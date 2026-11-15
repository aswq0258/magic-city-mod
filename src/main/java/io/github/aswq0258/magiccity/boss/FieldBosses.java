package io.github.aswq0258.magiccity.boss;

import io.github.aswq0258.magiccity.site.RoadSites;
import io.github.aswq0258.magiccity.site.RoadTheme;
import io.github.aswq0258.magiccity.world.KingdomRoads;
import io.github.aswq0258.magiccity.world.ModWorldgen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.Holder;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 길마다 하나씩 있는 필드보스. 결투장(RoadSites.Arena) 가운데에 플레이어가 다가가면 등장.
 *  - 체력·크기를 키운 바닐라 몹 + 보스 체력바
 *  - 내려찍기(주변 넉백), 체력 50%·25% 에서 길의 몬스터 소환, 30% 이하 광폭화(신속·힘)
 *  - 결투장에서 멀어지면 제자리로, 모두 떠나면 1분 뒤 사라짐
 *  - 쓰러뜨리면 보상, 15분 뒤 다시 나타남
 */
public final class FieldBosses {
    private FieldBosses() {}

    private static final String TAG = "magiccity_fieldboss", MINION = "magiccity_minion";
    private static final int TRIGGER_R = 14, LEASH_R = 30, ACTIVE_R = 64;
    private static final long RESPAWN_TICKS = 20L * 60 * 15;

    private static final class Fight {
        UUID id;
        ServerBossEvent bar;
        int empty;
        boolean summon50, summon25, enraged;
        long deadUntil;
    }

    private static final Map<Integer, Fight> FIGHTS = new HashMap<>();
    private static int tick;

    private static List<RoadSites.Site> arenas() {
        List<RoadSites.Site> out = new ArrayList<>();
        for (RoadSites.Site s : RoadSites.sites()) if (s.kind() == RoadSites.Kind.ARENA) out.add(s);
        return out;
    }

    private static List<RoadSites.Site> arenas;

    public static void onServerTick(ServerTickEvent.Post event) {
        if (++tick % 5 != 0) return;
        ServerLevel level = event.getServer().getLevel(ModWorldgen.KINGDOM);
        if (level == null || level.players().isEmpty()) return;
        if (arenas == null) arenas = arenas();
        for (RoadSites.Site s : arenas) {
            Vec3 c = new Vec3(s.x() + 0.5, s.y() + 1, s.z() + 0.5);
            List<ServerPlayer> near = level.getPlayers(p -> !p.isSpectator() && p.distanceToSqr(c) < ACTIVE_R * ACTIVE_R);
            Fight f = FIGHTS.get(s.road());
            if (near.isEmpty() && (f == null || f.id == null)) continue;
            if (f == null) FIGHTS.put(s.road(), f = new Fight());
            Mob boss = find(level, f, s, c);
            if (boss == null) {
                clear(f);
                if (level.getGameTime() < f.deadUntil || level.getDifficulty() == Difficulty.PEACEFUL) continue;
                boolean trigger = near.stream().anyMatch(p -> !p.isCreative() && p.distanceToSqr(c) < TRIGGER_R * TRIGGER_R);
                if (trigger) spawn(level, f, s);
                continue;
            }
            fight(level, f, s, boss, near, c);
        }
    }

    private static Mob find(ServerLevel level, Fight f, RoadSites.Site s, Vec3 c) {
        if (f.id != null && level.getEntity(f.id) instanceof Mob m && m.isAlive()) return m;
        f.id = null;
        String tag = TAG + "_" + s.road();
        List<Mob> found = level.getEntitiesOfClass(Mob.class, new AABB(c, c).inflate(ACTIVE_R), m -> m.getTags().contains(tag) && m.isAlive());
        if (found.isEmpty()) return null;
        Mob m = found.get(0);
        f.id = m.getUUID();
        return m;
    }

    private static void attr(Mob m, Holder<Attribute> a, double v) {
        AttributeInstance i = m.getAttribute(a);
        if (i != null) i.setBaseValue(v);
    }

    private static void spawn(ServerLevel level, Fight f, RoadSites.Site s) {
        RoadTheme t = RoadTheme.THEMES[s.road()];
        RoadTheme.Boss d = t.boss();
        BlockPos at = new BlockPos(s.x(), s.y() + 2, s.z());
        Mob m = d.type().spawn(level, at, MobSpawnType.EVENT);
        if (m == null) return;
        m.addTag(TAG);
        m.addTag(TAG + "_" + s.road());
        m.setCustomName(Component.literal("§l" + d.name()));
        m.setCustomNameVisible(true);
        m.setPersistenceRequired();
        attr(m, Attributes.SCALE, d.scale());
        attr(m, Attributes.MAX_HEALTH, d.hp());
        attr(m, Attributes.ATTACK_DAMAGE, d.damage());
        attr(m, Attributes.ARMOR, 10);
        attr(m, Attributes.KNOCKBACK_RESISTANCE, 0.9);
        attr(m, Attributes.FOLLOW_RANGE, 40);
        m.setHealth(m.getMaxHealth());
        if (d.weapon() != null) m.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(d.weapon()));
        for (EquipmentSlot slot : EquipmentSlot.values()) m.setDropChance(slot, 0F);
        m.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, -1, 0, false, false));       // 햇빛에 타지 않게
        f.id = m.getUUID();
        f.bar = new ServerBossEvent(Component.literal(d.name()), d.color(), BossEvent.BossBarOverlay.NOTCHED_10);
        f.summon50 = f.summon25 = f.enraged = false;
        f.empty = 0;
        level.playSound(null, at, SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 2F, 0.7F);
        say(level, at, "§c[" + t.region() + "] " + d.name() + "(이)가 모습을 드러냈다!");
    }

    private static void fight(ServerLevel level, Fight f, RoadSites.Site s, Mob boss, List<ServerPlayer> near, Vec3 c) {
        RoadTheme t = RoadTheme.THEMES[s.road()];
        if (f.bar == null) f.bar = new ServerBossEvent(Component.literal(t.boss().name()), t.boss().color(), BossEvent.BossBarOverlay.NOTCHED_10);
        float hp = boss.getHealth() / boss.getMaxHealth();
        f.bar.setProgress(hp);
        for (ServerPlayer p : near) f.bar.addPlayer(p);
        for (ServerPlayer p : new ArrayList<>(f.bar.getPlayers())) if (!near.contains(p)) f.bar.removePlayer(p);
        if (near.isEmpty()) {
            if ((f.empty += 5) > 1200) {
                boss.discard();
                clear(f);
            }
            return;
        }
        f.empty = 0;
        if (boss.distanceToSqr(c) > LEASH_R * LEASH_R) boss.teleportTo(c.x, c.y + 1, c.z);
        ServerPlayer target = null;
        double best = 1e9;
        for (ServerPlayer p : near) {
            if (p.isCreative()) continue;
            double d = p.distanceToSqr(boss);
            if (d < best) {
                best = d;
                target = p;
            }
        }
        if (target != null && (boss.getTarget() == null || !boss.getTarget().isAlive())) boss.setTarget(target);
        // 내려찍기 (8초마다, 6칸 안)
        if (tick % 160 == 0) {
            level.playSound(null, boss.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1F, 0.6F);
            for (ServerPlayer p : near) {
                if (p.isCreative() || p.distanceToSqr(boss) > 6.5 * 6.5) continue;
                p.hurt(level.damageSources().mobAttack(boss), 6F);
                Vec3 push = p.position().subtract(boss.position()).normalize().scale(1.2);
                p.push(push.x, 0.6, push.z);
                p.hurtMarked = true;
            }
        }
        if (hp <= 0.5F && !f.summon50) {
            f.summon50 = true;
            say(level, boss.blockPosition(), "§c[" + t.region() + "] " + t.boss().name() + "(이)가 부하를 부른다!");
            minions(level, boss, t);
        }
        if (hp <= 0.25F && !f.summon25) {
            f.summon25 = true;
            minions(level, boss, t);
        }
        if (hp <= 0.3F && !f.enraged) {
            f.enraged = true;
            say(level, boss.blockPosition(), "§4[" + t.region() + "] " + t.boss().name() + "(이)가 광폭해졌다!");
            boss.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20 * 600, 1));
            boss.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 20 * 600, 0));
        }
    }

    private static void minions(ServerLevel level, Mob boss, RoadTheme t) {
        if (level.getEntitiesOfClass(Mob.class, boss.getBoundingBox().inflate(30), m -> m.getTags().contains(MINION)).size() >= 8) return;
        for (int i = 0; i < 3; i++) {
            int x = boss.getBlockX() + level.random.nextInt(9) - 4, z = boss.getBlockZ() + level.random.nextInt(9) - 4;
            BlockPos pos = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            Mob m = t.mobs()[i % t.mobs().length].spawn(level, pos, MobSpawnType.MOB_SUMMONED);
            if (m != null) {
                m.addTag(MINION);
                RoadEncounters.dress(m);
            }
        }
    }

    public static void onDeath(LivingDeathEvent event) {
        LivingEntity e = event.getEntity();
        if (!(e.level() instanceof ServerLevel level) || !e.getTags().contains(TAG)) return;
        for (Map.Entry<Integer, Fight> en : FIGHTS.entrySet()) {
            Fight f = en.getValue();
            if (!e.getUUID().equals(f.id)) continue;
            clear(f);
            f.id = null;
            f.deadUntil = level.getGameTime() + RESPAWN_TICKS;
            RoadTheme t = RoadTheme.THEMES[en.getKey()];
            say(level, e.blockPosition(), "§6[" + t.region() + "] " + t.boss().name() + "(을)를 쓰러뜨렸다! " + KingdomRoads.ROADS[en.getKey()].name() + "이(가) 잠시 평온해진다.");
        }
        ItemStack[] loot = {new ItemStack(Items.DIAMOND, 3), new ItemStack(Items.EMERALD, 12), new ItemStack(Items.GOLDEN_APPLE, 2),
                new ItemStack(Items.EXPERIENCE_BOTTLE, 16), new ItemStack(Items.IRON_INGOT, 10)};
        for (ItemStack s : loot) level.addFreshEntity(new ItemEntity(level, e.getX(), e.getY() + 1, e.getZ(), s));
    }

    private static void clear(Fight f) {
        if (f.bar != null) f.bar.removeAllPlayers();
        f.bar = null;
    }

    private static void say(ServerLevel level, BlockPos at, String msg) {
        for (ServerPlayer p : level.players()) if (p.blockPosition().distSqr(at) < 160 * 160) p.sendSystemMessage(Component.literal(msg));
    }

    /** 위치 안내용 */
    public static String describe() {
        StringBuilder sb = new StringBuilder();
        for (RoadSites.Site s : RoadSites.sites()) {
            if (s.kind() != RoadSites.Kind.ARENA) continue;
            sb.append("\n§7- ").append(KingdomRoads.ROADS[s.road()].name()).append(": ").append(RoadTheme.THEMES[s.road()].boss().name())
                    .append(" (").append(s.x()).append(", ").append(s.y() + 1).append(", ").append(s.z()).append(")");
        }
        return sb.toString();
    }
}
