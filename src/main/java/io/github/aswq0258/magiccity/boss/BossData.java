package io.github.aswq0258.magiccity.boss;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import io.github.aswq0258.magiccity.MagicCityMod;
import io.github.aswq0258.magiccity.boss.BossDataRules.Grades;
import io.github.aswq0258.magiccity.boss.BossDataRules.Link;
import io.github.aswq0258.magiccity.boss.BossDataRules.Stats;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.EventHooks;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 보스 수치와 외부 보스 연결을 데이터에서 읽음 (2026-11-20, claude/외부보스연결_초안.md). 규칙은 {@link BossDataRules}.
 * 같은 경로·같은 파일 이름의 데이터팩을 위에 두면 그 값이 쓰인다. {@code /reload} 뒤 새로 등장하는 보스부터 적용.
 */
public final class BossData {
    private BossData() {}

    /** 외부 보스 몹에 붙이는 표식 (우리 기술·체력바를 끌지 판단) */
    public static final String EXTERNAL_TAG = "magiccity_external_boss";

    /** 읽은 값 + 그 파일을 준 데이터팩 이름 */
    public record Src<T>(T value, String pack) {}

    private static volatile Map<String, Src<Stats>> bossStats = Map.of();
    private static volatile Map<String, Src<Stats>> fieldStats = Map.of();
    private static volatile Src<Grades> grades = new Src<>(Grades.DEFAULT, "(코드 기본값)");
    private static volatile Map<String, Src<Link>> links = Map.of();

    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new SimpleJsonResourceReloadListener(new Gson(), "boss_stats") {
            @Override
            protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager rm, ProfilerFiller pf) {
                Map<String, Src<Stats>> out = new LinkedHashMap<>();
                Src<Grades> g = new Src<>(Grades.DEFAULT, "(코드 기본값)");
                int bad = 0;
                boolean gradeFile = false;
                for (var e : files.entrySet()) {
                    String key = e.getKey().getPath();
                    try {
                        if (key.equals("_grades")) {
                            g = new Src<>(BossDataRules.parseGrades(e.getValue().getAsJsonObject()), pack(rm, e.getKey(), "boss_stats"));
                            gradeFile = true;
                        } else if (!key.startsWith("_")) {
                            out.put(key, new Src<>(BossDataRules.parseStats(e.getValue().getAsJsonObject(), true), pack(rm, e.getKey(), "boss_stats")));
                        }
                    } catch (RuntimeException ex) {
                        bad++;
                        MagicCityMod.LOGGER.error("[보스] 수치 {} 읽기 실패 — 그 파일은 기본값으로: {}", e.getKey(), ex.getMessage());
                    }
                }
                bossStats = Collections.unmodifiableMap(out);
                grades = g;
                MagicCityMod.LOGGER.info("[보스] 챕터 보스 수치 {}개·등급표 {} 로드, 오류 {}건", out.size(), gradeFile ? "1개" : "없음(기본값)", bad);
            }
        });
        event.addListener(new SimpleJsonResourceReloadListener(new Gson(), "field_boss_stats") {
            @Override
            protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager rm, ProfilerFiller pf) {
                Map<String, Src<Stats>> out = new LinkedHashMap<>();
                int bad = 0;
                for (var e : files.entrySet()) {
                    if (e.getKey().getPath().startsWith("_")) continue;
                    try {
                        out.put(e.getKey().getPath(), new Src<>(BossDataRules.parseStats(e.getValue().getAsJsonObject(), false), pack(rm, e.getKey(), "field_boss_stats")));
                    } catch (RuntimeException ex) {
                        bad++;
                        MagicCityMod.LOGGER.error("[보스] 필드보스 수치 {} 읽기 실패 — 그 파일은 기본값으로: {}", e.getKey(), ex.getMessage());
                    }
                }
                fieldStats = Collections.unmodifiableMap(out);
                MagicCityMod.LOGGER.info("[보스] 필드보스 수치 {}개 로드, 오류 {}건", out.size(), bad);
            }
        });
        event.addListener(new SimpleJsonResourceReloadListener(new Gson(), "boss_entity") {
            @Override
            protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager rm, ProfilerFiller pf) {
                Map<String, Src<Link>> out = new LinkedHashMap<>();
                int bad = 0, present = 0, missing = 0;
                for (var e : files.entrySet()) {
                    if (e.getKey().getPath().startsWith("_")) continue;
                    try {
                        Link l = BossDataRules.parseLink(e.getValue().getAsJsonObject());
                        if (l.nbt() != null) nbt(l);                                       // NBT 문법을 미리 검사
                        out.put(e.getKey().getPath(), new Src<>(l, pack(rm, e.getKey(), "boss_entity")));
                        if (!l.enabled()) continue;
                        if (type(l) != null) present++;
                        else {
                            missing++;
                            MagicCityMod.LOGGER.warn("[보스] 외부 보스 {} 의 몹 {} 이 게임에 없음 — 그 장은 기본 보스로 싸움", e.getKey().getPath(), l.entity());
                        }
                    } catch (Exception ex) {
                        bad++;
                        MagicCityMod.LOGGER.error("[보스] 외부 보스 연결 {} 읽기 실패 — 그 장은 기본 보스로: {}", e.getKey(), ex.getMessage());
                    }
                }
                links = Collections.unmodifiableMap(out);
                MagicCityMod.LOGGER.info("[보스] 외부 보스 연결 {}개 로드 (몹 있음 {}, 몹 없음 → 기본 보스 {}), 오류 {}건", out.size(), present, missing, bad);
            }
        });
    }

    private static String pack(ResourceManager rm, ResourceLocation key, String dir) {
        ResourceLocation file = ResourceLocation.fromNamespaceAndPath(key.getNamespace(), dir + "/" + key.getPath() + ".json");
        return rm.getResource(file).map(Resource::sourcePackId).orElse("?");
    }

    // ── 조회

    public static Grades grades() {
        return grades.value();
    }

    public static Src<Grades> gradesSrc() {
        return grades;
    }

    public static Src<Stats> bossStats(String key) {
        return bossStats.get(key);
    }

    public static Src<Stats> fieldStats(String key) {
        return key == null ? null : fieldStats.get(key);
    }

    public static Src<Link> linkSrc(String key) {
        return links.get(key);
    }

    /** 지금 쓸 외부 보스 연결 (꺼져 있거나 몹이 게임에 없으면 null) */
    public static Link activeLink(String key) {
        Src<Link> s = links.get(key);
        if (s == null) return null;
        return BossDataRules.useExternal(s.value(), type(s.value()) != null) ? s.value() : null;
    }

    private static EntityType<?> type(Link l) {
        ResourceLocation id = ResourceLocation.tryParse(l.entity());
        if (id == null) return null;
        return BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
    }

    private static CompoundTag nbt(Link l) throws Exception {
        JsonElement n = l.nbt();
        if (n == null) return new CompoundTag();
        if (n.isJsonPrimitive()) return TagParser.parseTag(n.getAsString());
        Tag t = JsonOps.INSTANCE.convertTo(NbtOps.INSTANCE, n);
        if (t instanceof CompoundTag c) return c;
        throw new IllegalArgumentException("nbt 가 객체가 아님");
    }

    // ── 적용

    /** 외부 보스 몹을 전장 보스 자리에 부름. 실패하면 null (부르는 쪽이 기본 보스로 대신) */
    public static Mob spawnExternal(ServerLevel level, Link l, Vec3 c) {
        return spawnExternal(level, l, c, null);
    }

    /**
     * source = 그 보스 방의 왕국 대륙 보스 자리 (보스 방 사본이 깔렸을 때만, 없으면 null).
     * offset_nbt 가 있으면 이동량(전장 − 왕국 대륙, 블록 단위)을 넣어 줌 → 외부 보스가 녹화 좌표를 전장으로 옮김.
     */
    public static Mob spawnExternal(ServerLevel level, Link l, Vec3 c, Vec3 source) {
        try {
            CompoundTag tag = nbt(l).copy();
            tag.putString("id", l.entity());
            BlockPos at = BlockPos.containing(c);
            if (l.anchorNbt() != null) tag.put(l.anchorNbt(), NbtUtils.writeBlockPos(at));
            if (l.offsetNbt() != null && source != null) {
                BlockPos s = BlockPos.containing(source);
                tag.putIntArray(l.offsetNbt(), BossDataRules.offset(at.getX(), at.getY(), at.getZ(), s.getX(), s.getY(), s.getZ()));
            }
            Entity e = EntityType.loadEntityRecursive(tag, level, en -> {
                en.moveTo(c.x, c.y, c.z, en.getYRot(), en.getXRot());
                return en;
            });
            if (!(e instanceof Mob m)) {
                MagicCityMod.LOGGER.error("[보스] 외부 보스 {} 를 만들지 못함 (Mob 이 아님: {})", l.entity(), e);
                return null;
            }
            boolean onlyId = l.nbt() == null || nbt(l).isEmpty();
            if (onlyId) EventHooks.finalizeMobSpawn(m, level, level.getCurrentDifficultyAt(at), MobSpawnType.EVENT, null);
            if (!level.tryAddFreshEntityWithPassengers(m)) {
                MagicCityMod.LOGGER.error("[보스] 외부 보스 {} 를 월드에 넣지 못함 (같은 UUID?)", l.entity());
                return null;
            }
            m.addTag(EXTERNAL_TAG);
            m.setPersistenceRequired();
            if (l.name() != null) {
                m.setCustomName(Component.literal("§l" + l.name()));
                m.setCustomNameVisible(true);
            }
            return m;
        } catch (Exception ex) {
            MagicCityMod.LOGGER.error("[보스] 외부 보스 {} 소환 실패: {}", l.entity(), ex.toString());
            return null;
        }
    }

    /** 우리 고정 피해 패턴을 쓸지 (외부 보스 연결이 our_patterns=false 면 끔) */
    public static boolean ourPatterns(Entity e, String bossKey) {
        if (!isExternal(e)) return true;
        Src<Link> s = links.get(bossKey);
        return s == null || s.value().ourPatterns();
    }

    /** 이 몹이 외부 보스로 불린 것인지 */
    public static boolean isExternal(Entity e) {
        return e != null && e.getTags().contains(EXTERNAL_TAG);
    }

    /** 챕터 보스 능력치 덮어쓰기 (그 장 데이터가 없으면 아무것도 안 함). 탈것도 */
    public static void applyBoss(String key, Mob m) {
        Src<Stats> s = bossStats.get(key);
        if (s == null) return;
        apply(m, s.value());
        if (s.value().mount() != null && m.getVehicle() instanceof Mob mount) apply(mount, s.value().mount());
    }

    /** 필드보스 능력치 덮어쓰기 */
    public static void applyField(String key, Mob m) {
        Src<Stats> s = fieldStats(key);
        if (s != null) apply(m, s.value());
    }

    private static void apply(Mob m, Stats s) {
        if (s.scale() != null) {
            set(m, Attributes.SCALE, s.scale());
            AttributeInstance step = m.getAttribute(Attributes.STEP_HEIGHT);
            if (step != null) step.setBaseValue(BossDataRules.stepHeight(step.getBaseValue(), s.scale()));
        }
        set(m, Attributes.MAX_HEALTH, s.health());
        set(m, Attributes.ATTACK_DAMAGE, s.attack());
        set(m, Attributes.ARMOR, s.armor());
        set(m, Attributes.KNOCKBACK_RESISTANCE, s.knockback());
        set(m, Attributes.FOLLOW_RANGE, s.follow());
        set(m, Attributes.MOVEMENT_SPEED, s.speed());
        m.setHealth(m.getMaxHealth());
    }

    private static void set(Mob m, Holder<Attribute> a, Double v) {
        if (v == null) return;
        AttributeInstance i = m.getAttribute(a);
        if (i != null) i.setBaseValue(v);
    }

    // ── 보기 (/magiccity boss stats)

    public static String describe(String key) {
        StringBuilder b = new StringBuilder();
        Src<Stats> s = bossStats.get(key);
        if (s == null) s = fieldStats.get(key);
        if (s != null) b.append("§7수치 ").append(text(s.value())).append(" §8← ").append(s.pack());
        else b.append("§7수치: 데이터 없음 (코드 기본값)");
        Src<Link> l = links.get(key);
        if (l != null) {
            Link v = l.value();
            boolean have = type(v) != null;
            b.append("\n§7외부 보스 ").append(v.entity()).append(!v.enabled() ? " §8(꺼짐)" : have ? " §a(사용 중)" : " §c(몹 없음 → 기본 보스)")
                    .append(" §7우리 기술 ").append(v.ourSkills() ? "켬" : "끔").append(" · 우리 체력바 ").append(v.ourBossbar() ? "켬" : "끔")
                    .append(" · 등급 배율 ").append(v.gradeScaling() ? "켬" : "끔")
                    .append(" · 우리 고정 피해 ").append(v.ourPatterns() ? "켬" : "끔")
                    .append(v.designAttack() != null ? " · 배율 기준 공격 " + v.designAttack() : "")
                    .append(v.offsetNbt() != null ? " · 이동량 NBT " + v.offsetNbt() : "")
                    .append(v.anchorNbt() != null ? " · 기준점 NBT " + v.anchorNbt() : "").append(" §8← ").append(l.pack());
        }
        return b.toString();
    }

    public static String describeGrades() {
        Grades g = grades.value();
        return "§7등급 1~6 보스 기본 공격 " + java.util.Arrays.toString(g.basic()) + " · 체력 배율 " + java.util.Arrays.toString(g.hpScale())
                + " §8← " + grades.pack();
    }

    private static String text(Stats s) {
        StringBuilder b = new StringBuilder();
        add(b, "크기", s.scale());
        add(b, "체력", s.health());
        add(b, "공격", s.attack());
        add(b, "방어", s.armor());
        add(b, "밀치기 저항", s.knockback());
        add(b, "추적 거리", s.follow());
        add(b, "속도", s.speed());
        if (s.mount() != null) b.append(" / 탈것: ").append(text(s.mount()));
        return b.toString().trim();
    }

    private static void add(StringBuilder b, String n, Double v) {
        if (v != null) b.append(' ').append(n).append(' ').append(v % 1 == 0 ? String.valueOf(v.longValue()) : String.valueOf(v));
    }

    /** 모든 키 (챕터 보스 + 필드보스 + 외부 연결) */
    public static java.util.Set<String> keys() {
        java.util.Set<String> k = new java.util.TreeSet<>(bossStats.keySet());
        k.addAll(fieldStats.keySet());
        k.addAll(links.keySet());
        return k;
    }
}
