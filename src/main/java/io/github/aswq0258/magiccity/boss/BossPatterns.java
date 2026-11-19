package io.github.aswq0258.magiccity.boss;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import io.github.aswq0258.magiccity.MagicCityMod;
import io.github.aswq0258.magiccity.boss.FixedStrikeRules.Pattern;
import io.github.aswq0258.magiccity.boss.FixedStrikeRules.Shape;
import io.github.aswq0258.magiccity.world.ModWorldgen;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * 보스 고정 피해 패턴 실행 (2026-11-19). 보스 클래스와 따로 돈다: 플레이어 근처의 패턴 있는 보스(태그)를 찾아
 * 쿨타임마다 예고 → 발동한다. 패턴은 data/magiccity/boss_patterns/&lt;보스 키&gt;.json (보스 태그 = magiccity_&lt;키&gt;).
 * 피해 유형 magiccity:boss_fixed 는 방어·인챈트·저항 효과·방패·무적 시간을 건너뛰고 흡수 체력부터 깎는다(태그 데이터).
 */
public final class BossPatterns {
    private BossPatterns() {}

    public static final ResourceKey<DamageType> BOSS_FIXED =
            ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(MagicCityMod.MODID, "boss_fixed"));

    private static final Gson GSON = new Gson();
    private static final double SCAN_R = 48, ACTIVE_R = 40;
    private static final DustParticleOptions MARK = new DustParticleOptions(new Vector3f(1.0F, 0.15F, 0.1F), 1.6F);
    private static final DustParticleOptions SAFE = new DustParticleOptions(new Vector3f(0.95F, 0.95F, 0.95F), 1.2F);

    private static volatile Map<String, Pattern> patterns = Map.of();
    private static final Map<UUID, State> STATES = new HashMap<>();
    private static int ticks;

    private static final class State {
        long nextAt = -1, fireAt = -1;
        boolean warned;
        Vec3 center;
        final List<Vec3> points = new ArrayList<>();
    }

    public static Map<String, Pattern> patterns() {
        return patterns;
    }

    public static boolean isFixed(DamageSource s) {
        return s != null && s.is(BOSS_FIXED);
    }

    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new SimpleJsonResourceReloadListener(GSON, "boss_patterns") {
            @Override
            protected void apply(Map<ResourceLocation, JsonElement> map, ResourceManager rm, ProfilerFiller pf) {
                Map<String, Pattern> out = new TreeMap<>();
                int bad = 0;
                for (var e : map.entrySet()) {
                    String id = e.getKey().getPath();
                    if (id.startsWith("_")) continue;
                    try {
                        Pattern p = FixedStrikeRules.parse(id, e.getValue().getAsJsonObject());
                        List<String> err = FixedStrikeRules.validate(p);
                        if (!err.isEmpty()) throw new IllegalArgumentException(String.join(", ", err));
                        out.put(p.boss(), p);
                    } catch (RuntimeException ex) {
                        bad++;
                        MagicCityMod.LOGGER.error("[보스 패턴] {} 읽기 실패: {}", e.getKey(), ex.getMessage());
                    }
                }
                patterns = Collections.unmodifiableMap(new LinkedHashMap<>(out));
                MagicCityMod.LOGGER.info("[보스 패턴] 고정 피해 패턴 {}개 로드, 오류 {}건", out.size(), bad);
            }
        });
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        if (++ticks % 5 != 0) return;
        ServerLevel level = event.getServer().getLevel(ModWorldgen.KINGDOM);
        if (level == null) return;
        if (ticks % 200 == 0) STATES.keySet().removeIf(id -> !(level.getEntity(id) instanceof Mob m) || !m.isAlive());
        if (level.players().isEmpty() || patterns.isEmpty()) return;
        for (Mob boss : bosses(level)) step(level, boss, patterns.get(patternTag(boss)));
    }

    /** 플레이어 근처의 패턴 있는 보스 */
    private static List<Mob> bosses(ServerLevel level) {
        Map<UUID, Mob> found = new LinkedHashMap<>();
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator()) continue;
            for (Mob m : level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(SCAN_R), m -> m.isAlive() && patternTag(m) != null))
                found.putIfAbsent(m.getUUID(), m);
        }
        return new ArrayList<>(found.values());
    }

    private static String patternTag(Mob m) {
        for (String t : m.getTags()) if (patterns.containsKey(t)) return t;
        return null;
    }

    private static int gradeOf(Mob boss) {
        int g = Progression.gradeOf(boss);
        return g > 0 ? g : ProgressionRules.MAX_GRADE;
    }

    private static List<ServerPlayer> targets(ServerLevel level, Mob boss) {
        return level.getPlayers(p -> !p.isSpectator() && !p.isCreative() && p.isAlive() && p.distanceToSqr(boss) < ACTIVE_R * ACTIVE_R);
    }

    private static void step(ServerLevel level, Mob boss, Pattern pat) {
        if (pat == null) return;
        long now = level.getGameTime();
        State st = STATES.computeIfAbsent(boss.getUUID(), k -> new State());
        if (st.nextAt < 0) st.nextAt = now + FixedStrikeRules.FIRST_DELAY;
        List<ServerPlayer> near = targets(level, boss);
        if (st.fireAt >= 0) {
            telegraph(level, pat, st);
            if (!st.warned && now >= st.fireAt - 10) {
                st.warned = true;
                sound(level, st, pat, 1.6F);
            }
            if (now >= st.fireAt) resolve(level, boss, pat, st, near, now);
            return;
        }
        if (near.isEmpty() || now < st.nextAt) return;
        start(level, boss, pat, st, near, now);
    }

    /** 예고 시작 (시험 명령으로도 부름) */
    static void start(ServerLevel level, Mob boss, Pattern pat, State st, List<ServerPlayer> near, long now) {
        int grade = gradeOf(boss);
        st.center = boss.position();
        st.points.clear();
        if (pat.shape() == Shape.AT_PLAYERS)
            for (ServerPlayer p : near) if (st.points.size() < FixedStrikeRules.MAX_POINTS) st.points.add(p.position());
        st.fireAt = now + FixedStrikeRules.telegraph(grade);
        st.warned = false;
        sound(level, st, pat, 0.6F);
        Component chat = Component.literal("§c§l[" + bossName(boss) + "] " + pat.name() + "§r§c — " + pat.warn());
        for (ServerPlayer p : near) {
            p.sendSystemMessage(chat);
            p.displayClientMessage(Component.literal("⚠ " + pat.warn()).withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
        }
        telegraph(level, pat, st);
    }

    private static void resolve(ServerLevel level, Mob boss, Pattern pat, State st, List<ServerPlayer> near, long now) {
        int grade = gradeOf(boss);
        DamageSource src = new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(BOSS_FIXED), boss);
        for (ServerPlayer p : near) {
            if (!inside(pat, st, p)) continue;
            p.hurt(src, FixedStrikeRules.amount(pat, p.getMaxHealth(), grade));
        }
        for (Vec3 c : centers(pat, st)) {
            level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y + 0.5, c.z, 3, pat.shape() == Shape.AT_PLAYERS ? pat.radius() / 2 : 2, 0.3, pat.shape() == Shape.AT_PLAYERS ? pat.radius() / 2 : 2, 0);
        }
        level.playSound(null, st.center.x, st.center.y, st.center.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 2.5F, 0.8F);
        boolean enraged = boss.getHealth() <= boss.getMaxHealth() * 0.25F;
        st.fireAt = -1;
        st.points.clear();
        st.nextAt = now + FixedStrikeRules.cooldown(pat, enraged);
    }

    private static boolean inside(Pattern pat, State st, ServerPlayer p) {
        for (Vec3 c : centers(pat, st))
            if (FixedStrikeRules.hits(pat, p.getX() - c.x, p.getY() - c.y, p.getZ() - c.z)) return true;
        return false;
    }

    private static List<Vec3> centers(Pattern pat, State st) {
        return pat.shape() == Shape.AT_PLAYERS ? st.points : List.of(st.center);
    }

    /** 바닥 범위 표시: 빨간 테두리(맞는 곳), 고리는 안쪽 안전 원을 흰색으로 */
    private static void telegraph(ServerLevel level, Pattern pat, State st) {
        for (Vec3 c : centers(pat, st)) {
            switch (pat.shape()) {
                case AROUND_BOSS, AT_PLAYERS -> {
                    circle(level, c, pat.radius(), MARK);
                    level.sendParticles(MARK, c.x, c.y + 0.2, c.z, (int) Math.min(30, pat.radius() * pat.radius()), pat.radius() / 2, 0.05, pat.radius() / 2, 0);
                }
                case RING -> {
                    circle(level, c, pat.outer(), MARK);
                    circle(level, c, pat.inner(), SAFE);
                    circle(level, c, (pat.inner() + pat.outer()) / 2, MARK);
                }
            }
        }
    }

    private static void circle(ServerLevel level, Vec3 c, double r, DustParticleOptions dust) {
        int n = (int) Math.max(12, Math.min(72, Math.PI * 2 * r / 1.2));
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            level.sendParticles(dust, c.x + Math.cos(a) * r, c.y + 0.2, c.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
        }
    }

    private static void sound(ServerLevel level, State st, Pattern pat, float pitch) {
        level.playSound(null, st.center.x, st.center.y, st.center.z, SoundEvents.EVOKER_PREPARE_ATTACK, SoundSource.HOSTILE, 3F, pitch);
    }

    private static String bossName(Mob boss) {
        return boss.hasCustomName() ? boss.getCustomName().getString().replaceAll("§.", "") : boss.getName().getString();
    }

    /** 시험 명령: 플레이어에게 가장 가까운 패턴 보스가 지금 바로 예고를 시작 */
    public static String forceNearest(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) return "§c서버 월드가 아닙니다.";
        Mob best = null;
        double bd = SCAN_R * SCAN_R;
        for (Mob m : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(SCAN_R), m -> m.isAlive() && patternTag(m) != null)) {
            double d = m.distanceToSqr(player);
            if (d < bd) {
                bd = d;
                best = m;
            }
        }
        if (best == null) return "§c48칸 안에 고정 피해 패턴이 있는 보스가 없습니다.";
        Pattern pat = patterns.get(patternTag(best));
        State st = STATES.computeIfAbsent(best.getUUID(), k -> new State());
        List<ServerPlayer> near = targets(level, best);
        if (near.isEmpty()) return "§c보스 40칸 안에 대상 플레이어(생존·창조 아님)가 없습니다.";
        start(level, best, pat, st, near, level.getGameTime());
        int g = gradeOf(best);
        return "§e[보스 패턴] " + bossName(best) + " — " + pat.name() + " 예고 시작 (" + g + "급, 예고 " + FixedStrikeRules.telegraph(g) + "틱, 피해 "
                + String.format("%.1f", FixedStrikeRules.amount(pat, player.getMaxHealth(), g)) + ")";
    }

    /** 시험 명령: 불러온 패턴 목록 */
    public static String list() {
        StringBuilder b = new StringBuilder("§e[보스 패턴] " + patterns.size() + "개");
        for (Iterator<Pattern> it = patterns.values().iterator(); it.hasNext(); ) {
            Pattern p = it.next();
            b.append("\n§7").append(p.boss().substring("magiccity_".length())).append(" §f").append(p.name()).append(" §7")
                    .append(p.shape().name().toLowerCase()).append(' ')
                    .append(p.percent() > 0 ? Math.round(p.percent() * 100) + "%" : "고정 " + p.flat())
                    .append(", ").append(p.cooldown() / 20).append("초");
        }
        return b.toString();
    }
}
