package io.github.aswq0258.magiccity.boss;

import io.github.aswq0258.magiccity.site.SkyRuins;
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
 * 부유섬 유적 폭풍의 결투장 보스 "폭풍의 수호자" — 결투장 가운데에 다가가면 나타남.
 *  - 거대한 브리즈(x3, 바람 돌격탄), 회오리(주변을 띄우고 밀쳐냄), 체력 절반 아래부터 번개
 *  - 체력 66%·33% 에서 바람의 정령(브리즈) 소환, 25% 아래 광폭화
 *  - 쓰러뜨리면 철퇴·무거운 핵·브리즈 막대·다이아몬드, 30분 뒤 다시 나타남
 */
public final class SkyBoss {
    private SkyBoss() {}

    private static final String TAG = "magiccity_skyboss", MINION = "magiccity_windkin";
    private static final String NAME = "폭풍의 수호자";
    private static final int TRIGGER_R = 12, LEASH_R = 15, ACTIVE_R = 40;
    private static final long RESPAWN_TICKS = 20L * 60 * 30;

    private static UUID id;
    private static ServerBossEvent bar;
    private static int empty, tick;
    private static boolean s66, s33, enraged;
    private static long deadUntil;

    private static Vec3 center() {
        return new Vec3(SkyRuins.ARENA_X + 0.5, SkyRuins.ARENA_Y + 2, SkyRuins.ARENA_Z + 0.5);
    }

    private static boolean inside(ServerPlayer p, Vec3 c, double r) {
        double dx = p.getX() - c.x, dz = p.getZ() - c.z;
        return dx * dx + dz * dz < r * r && p.getY() > SkyRuins.ARENA_Y - 8 && p.getY() < SkyRuins.ARENA_Y + 30;
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
        Mob m = EntityType.BREEZE.spawn(level, at, MobSpawnType.EVENT);
        if (m == null) return;
        m.addTag(TAG);
        m.setCustomName(Component.literal("§b§l" + NAME));
        m.setCustomNameVisible(true);
        m.setPersistenceRequired();
        attr(m, Attributes.SCALE, 3.0);
        attr(m, Attributes.MAX_HEALTH, 450);
        attr(m, Attributes.ATTACK_DAMAGE, 10);
        attr(m, Attributes.ARMOR, 15);
        attr(m, Attributes.KNOCKBACK_RESISTANCE, 1.0);
        attr(m, Attributes.FOLLOW_RANGE, 48);
        attr(m, Attributes.STEP_HEIGHT, 3.0);
        m.setHealth(m.getMaxHealth());
        m.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, -1, 0, false, false));
        id = m.getUUID();
        bar = new ServerBossEvent(Component.literal(NAME), BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.NOTCHED_20);
        s66 = s33 = enraged = false;
        empty = 0;
        level.playSound(null, at, SoundEvents.BREEZE_IDLE_GROUND, SoundSource.HOSTILE, 4F, 0.5F);
        say(level, at, "§b§l[천공의 유적] 바람이 휘몰아친다... " + NAME + "(이)가 모습을 드러냈다!");
    }

    private static void fight(ServerLevel level, Mob boss, List<ServerPlayer> near, Vec3 c) {
        if (bar == null) bar = new ServerBossEvent(Component.literal(NAME), BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.NOTCHED_20);
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
        // 회오리: 8초마다, 10칸 안을 띄우고 밀쳐냄 (결투장 난간 쪽으로)
        if (tick % 160 == 80) {
            level.playSound(null, boss.blockPosition(), SoundEvents.BREEZE_SHOOT, SoundSource.HOSTILE, 3F, 0.6F);
            for (ServerPlayer p : near) {
                if (p.isCreative() || p.distanceToSqr(boss) > 10 * 10) continue;
                p.hurt(level.damageSources().mobAttack(boss), 5F);
                Vec3 push = p.position().subtract(boss.position()).normalize().scale(1.1);
                p.push(push.x, 1.0, push.z);
                p.hurtMarked = true;
            }
        }
        // 번개: 체력 절반 아래부터 6초마다 플레이어 자리에 내리꽂힘 (1초 앞서 경고음)
        if (hp < 0.5F && tick % 120 == 0) {
            for (ServerPlayer p : near) {
                if (p.isCreative()) continue;
                net.minecraft.world.entity.LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
                if (bolt == null) continue;
                bolt.moveTo(p.getX() + level.random.nextInt(3) - 1, p.getY(), p.getZ() + level.random.nextInt(3) - 1);
                level.addFreshEntity(bolt);
            }
        }
        if (hp <= 0.66F && !s66) {
            s66 = true;
            say(level, boss.blockPosition(), "§b[천공의 유적] " + NAME + "(이)가 바람의 정령을 부른다!");
            minions(level, boss);
        }
        if (hp <= 0.33F && !s33) {
            s33 = true;
            minions(level, boss);
        }
        if (hp <= 0.25F && !enraged) {
            enraged = true;
            say(level, boss.blockPosition(), "§3[천공의 유적] " + NAME + "(이)가 폭풍이 되어 휘몰아친다!");
            boss.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20 * 600, 1));
            boss.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 20 * 600, 1));
        }
    }

    private static void minions(ServerLevel level, Mob boss) {
        if (level.getEntitiesOfClass(Mob.class, boss.getBoundingBox().inflate(40), m -> m.getTags().contains(MINION)).size() >= 6) return;
        for (int i = 0; i < 3; i++) {
            double a = Math.PI * 2 * i / 3;
            BlockPos pos = BlockPos.containing(SkyRuins.ARENA_X + Math.cos(a) * 8, SkyRuins.ARENA_Y + 1, SkyRuins.ARENA_Z + Math.sin(a) * 8);
            var e = EntityType.BREEZE.spawn(level, pos, MobSpawnType.MOB_SUMMONED);
            if (e != null) {
                e.addTag(MINION);
                e.setCustomName(Component.literal("바람의 정령"));
            }
        }
    }

    public static void onDeath(LivingDeathEvent event) {
        LivingEntity e = event.getEntity();
        if (!(e.level() instanceof ServerLevel level) || !e.getTags().contains(TAG)) return;
        clear();
        id = null;
        deadUntil = level.getGameTime() + RESPAWN_TICKS;
        say(level, e.blockPosition(), "§b§l[천공의 유적] " + NAME + "(을)를 쓰러뜨렸다! 바람이 잦아든다.");
        ItemStack[] loot = {new ItemStack(Items.MACE), new ItemStack(Items.HEAVY_CORE), new ItemStack(Items.BREEZE_ROD, 8),
                new ItemStack(Items.DIAMOND, 8), new ItemStack(Items.GOLDEN_APPLE, 3), new ItemStack(Items.EXPERIENCE_BOTTLE, 24),
                new ItemStack(Items.WIND_CHARGE, 32)};
        for (ItemStack s : loot) level.addFreshEntity(new ItemEntity(level, e.getX(), e.getY() + 1, e.getZ(), s));
        Story.credit(level, e.blockPosition(), 9);
    }

    private static void clear() {
        if (bar != null) bar.removeAllPlayers();
        bar = null;
    }

    private static void say(ServerLevel level, BlockPos at, String msg) {
        for (ServerPlayer p : level.players()) if (p.blockPosition().distSqr(at) < 120 * 120) p.sendSystemMessage(Component.literal(msg));
    }
}
