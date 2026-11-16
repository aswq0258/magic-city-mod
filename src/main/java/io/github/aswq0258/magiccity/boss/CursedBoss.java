package io.github.aswq0258.magiccity.boss;

import io.github.aswq0258.magiccity.site.CursedLands;
import io.github.aswq0258.magiccity.world.KingdomTerrain;
import io.github.aswq0258.magiccity.world.ModWorldgen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 저주받은 숲 제단의 보스 "저주의 마녀왕" — 제단 가운데에 다가가면 나타남.
 *  - 거대한 소환사(x2.2, 송곳니 함정·벡스 소환), 저주의 파동(어둠·시듦), 체력 절반 아래부터 영혼 사슬(끌어당김)
 *  - 체력 66%·33% 에서 저주받은 망자 소환, 25% 아래 광폭화
 *  - 쓰러뜨리면 불사의 토템·에메랄드·다이아몬드·메아리 조각, 30분 뒤 다시 나타남
 */
public final class CursedBoss {
    private CursedBoss() {}

    private static final String TAG = "magiccity_cursedboss", MINION = "magiccity_cursedkin";
    private static final String NAME = "저주의 마녀왕";
    private static final int TRIGGER_R = 10, LEASH_R = 14, ACTIVE_R = 40;
    private static final long RESPAWN_TICKS = 20L * 60 * 30;

    private static UUID id;
    private static ServerBossEvent bar;
    private static int empty, tick;
    private static boolean s66, s33, enraged;
    private static long deadUntil;

    private static Vec3 center() {
        return new Vec3(CursedLands.ALTAR_X + 0.5, KingdomTerrain.column(CursedLands.ALTAR_X, CursedLands.ALTAR_Z).h() + 1, CursedLands.ALTAR_Z + 0.5);
    }

    private static boolean inside(ServerPlayer p, Vec3 c, double r) {
        double dx = p.getX() - c.x, dz = p.getZ() - c.z;
        return dx * dx + dz * dz < r * r && p.getY() > c.y - 12 && p.getY() < c.y + 25;
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        if (++tick % 5 != 0) return;
        ServerLevel level = event.getServer().getLevel(ModWorldgen.KINGDOM);
        if (level == null || level.players().isEmpty()) return;
        Vec3 c = center();
        List<ServerPlayer> near = level.getPlayers(p -> !p.isSpectator() && inside(p, c, ACTIVE_R));
        if (near.isEmpty() && id == null) return;
        Mob boss = find(level, c);
        if (boss == null) {
            clear();
            if (level.getGameTime() < deadUntil || level.getDifficulty() == Difficulty.PEACEFUL) return;
            if (near.stream().anyMatch(p -> !p.isCreative() && inside(p, c, TRIGGER_R))) spawn(level, c);
            return;
        }
        fight(level, boss, near, c);
    }

    private static Mob find(ServerLevel level, Vec3 c) {
        if (id != null && level.getEntity(id) instanceof Mob m && m.isAlive()) return m;
        id = null;
        List<Mob> found = level.getEntitiesOfClass(Mob.class, new AABB(c, c).inflate(ACTIVE_R), m -> m.getTags().contains(TAG) && m.isAlive());
        if (found.isEmpty()) return null;
        id = found.get(0).getUUID();
        return found.get(0);
    }

    private static void attr(Mob m, Holder<Attribute> a, double v) {
        AttributeInstance i = m.getAttribute(a);
        if (i != null) i.setBaseValue(v);
    }

    private static void spawn(ServerLevel level, Vec3 c) {
        BlockPos at = BlockPos.containing(c.x, c.y + 1, c.z);
        Mob m = EntityType.EVOKER.spawn(level, at, MobSpawnType.EVENT);
        if (m == null) return;
        m.addTag(TAG);
        m.setCustomName(Component.literal("§5§l" + NAME));
        m.setCustomNameVisible(true);
        m.setPersistenceRequired();
        attr(m, Attributes.SCALE, 2.2);
        attr(m, Attributes.MAX_HEALTH, 400);
        attr(m, Attributes.ATTACK_DAMAGE, 10);
        attr(m, Attributes.ARMOR, 15);
        attr(m, Attributes.KNOCKBACK_RESISTANCE, 1.0);
        attr(m, Attributes.FOLLOW_RANGE, 48);
        attr(m, Attributes.STEP_HEIGHT, 3.0);
        m.setHealth(m.getMaxHealth());
        m.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, -1, 0, false, false));
        id = m.getUUID();
        bar = new ServerBossEvent(Component.literal(NAME), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_20);
        s66 = s33 = enraged = false;
        empty = 0;
        level.playSound(null, at, SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.HOSTILE, 4F, 0.5F);
        say(level, at, "§5§l[저주받은 숲] 제단의 영혼 불이 일렁인다... " + NAME + "(이)가 모습을 드러냈다!");
    }

    private static void fight(ServerLevel level, Mob boss, List<ServerPlayer> near, Vec3 c) {
        if (bar == null) bar = new ServerBossEvent(Component.literal(NAME), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_20);
        float hp = boss.getHealth() / boss.getMaxHealth();
        bar.setProgress(hp);
        for (ServerPlayer p : near) bar.addPlayer(p);
        for (ServerPlayer p : new ArrayList<>(bar.getPlayers())) if (!near.contains(p)) bar.removePlayer(p);
        if (near.isEmpty()) {
            if ((empty += 5) > 1200) {
                boss.discard();
                clear();
            }
            return;
        }
        empty = 0;
        if (boss.position().distanceToSqr(c) > LEASH_R * LEASH_R) boss.teleportTo(c.x, c.y + 1, c.z);
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
        if (target == null) return;
        if (boss.getTarget() == null || !boss.getTarget().isAlive()) boss.setTarget(target);
        // 저주의 파동: 10초마다 14칸 안에 어둠·시듦
        if (tick % 200 == 100) {
            level.playSound(null, boss.blockPosition(), SoundEvents.WITHER_AMBIENT, SoundSource.HOSTILE, 2F, 0.6F);
            for (ServerPlayer p : near) {
                if (p.isCreative() || p.distanceToSqr(boss) > 14 * 14) continue;
                p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 100, 0));
                p.addEffect(new MobEffectInstance(MobEffects.WITHER, 80, 0));
            }
        }
        // 영혼 사슬: 체력 절반 아래부터 6초마다 가까운 플레이어를 끌어당김
        if (hp < 0.5F && tick % 120 == 0) {
            for (ServerPlayer p : near) {
                if (p.isCreative() || p.distanceToSqr(boss) < 4 * 4) continue;
                Vec3 pull = boss.position().subtract(p.position()).normalize().scale(1.4);
                p.push(pull.x, 0.4, pull.z);
                p.hurtMarked = true;
                p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
            }
        }
        if (hp <= 0.66F && !s66) {
            s66 = true;
            say(level, boss.blockPosition(), "§5[저주받은 숲] " + NAME + "(이)가 저주받은 망자를 일으킨다!");
            minions(level, boss);
        }
        if (hp <= 0.33F && !s33) {
            s33 = true;
            minions(level, boss);
        }
        if (hp <= 0.25F && !enraged) {
            enraged = true;
            say(level, boss.blockPosition(), "§4[저주받은 숲] " + NAME + "(이)가 광기에 휩싸인다!");
            boss.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20 * 600, 1));
            boss.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 20 * 600, 1));
        }
    }

    private static void minions(ServerLevel level, Mob boss) {
        if (level.getEntitiesOfClass(Mob.class, boss.getBoundingBox().inflate(40), m -> m.getTags().contains(MINION)).size() >= 8) return;
        EntityType<?>[] kin = {EntityType.ZOMBIE, EntityType.SKELETON, EntityType.ZOMBIE, EntityType.WITCH};
        for (int i = 0; i < kin.length; i++) {
            double a = Math.PI * 2 * i / kin.length;
            BlockPos pos = BlockPos.containing(boss.getX() + Math.cos(a) * 7, boss.getY(), boss.getZ() + Math.sin(a) * 7);
            var e = kin[i].spawn(level, pos, MobSpawnType.MOB_SUMMONED);
            if (e instanceof Mob m) {
                m.addTag(MINION);
                m.setCustomName(Component.literal("저주받은 망자"));
                m.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, -1, 0, false, false));
            }
        }
    }

    public static void onDeath(LivingDeathEvent event) {
        LivingEntity e = event.getEntity();
        if (!(e.level() instanceof ServerLevel level) || !e.getTags().contains(TAG)) return;
        clear();
        id = null;
        deadUntil = level.getGameTime() + RESPAWN_TICKS;
        say(level, e.blockPosition(), "§5§l[저주받은 숲] " + NAME + "(을)를 쓰러뜨렸다! 숲의 저주가 잠시 옅어진다.");
        ItemStack[] loot = {new ItemStack(Items.TOTEM_OF_UNDYING), new ItemStack(Items.EMERALD, 24), new ItemStack(Items.DIAMOND, 6),
                new ItemStack(Items.GOLDEN_APPLE, 2), new ItemStack(Items.EXPERIENCE_BOTTLE, 24), new ItemStack(Items.ECHO_SHARD, 4)};
        for (ItemStack s : loot) level.addFreshEntity(new ItemEntity(level, e.getX(), e.getY() + 1, e.getZ(), s));
        Story.credit(level, e.blockPosition(), 2);
    }

    private static void clear() {
        if (bar != null) bar.removeAllPlayers();
        bar = null;
    }

    private static void say(ServerLevel level, BlockPos at, String msg) {
        for (ServerPlayer p : level.players()) if (p.blockPosition().distSqr(at) < 120 * 120) p.sendSystemMessage(Component.literal(msg));
    }
}
