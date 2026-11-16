package io.github.aswq0258.magiccity.boss;

import io.github.aswq0258.magiccity.site.DragonLair;
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
 * 고룡 봉우리 보물 동굴의 보스 "고룡 이그나르" — 황금 더미에 다가가면 깨어남.
 *  - 거대한 몸집(파괴수 x3), 화염 숨결(불덩이 연사), 날갯짓 돌풍(밀쳐냄·피해), 체력 절반 아래부터 천장에서 불비
 *  - 체력 66%·33% 에서 용의 권속(블레이즈·마그마 큐브) 소환, 25% 아래 광폭화
 *  - 동굴 밖으로 멀어지면 제자리로, 모두 떠나면 1분 뒤 다시 잠듦
 *  - 쓰러뜨리면 용의 머리·겉날개·다이아몬드·네더라이트 조각·마법이 부여된 황금 사과, 30분 뒤 다시 나타남
 */
public final class DragonBoss {
    private DragonBoss() {}

    private static final String TAG = "magiccity_dragon", MINION = "magiccity_dragonkin";
    private static final String NAME = "고룡 이그나르";
    private static final int TRIGGER_R = 22, LEASH_R = 28, ACTIVE_R = 48;
    private static final long RESPAWN_TICKS = 20L * 60 * 30;

    private static UUID id;
    private static ServerBossEvent bar;
    private static int empty, tick;
    private static boolean s66, s33, enraged;
    private static long deadUntil;

    private static Vec3 center() {
        return new Vec3(DragonLair.MX + 0.5, DragonLair.HOARD_Y + 7, DragonLair.MZ + 0.5);
    }

    private static boolean inside(ServerPlayer p, Vec3 c, double r) {
        double dx = p.getX() - c.x, dz = p.getZ() - c.z;
        return dx * dx + dz * dz < r * r && p.getY() > DragonLair.HOARD_Y - 6 && p.getY() < DragonLair.HOARD_Y + 45;
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
            List<ServerPlayer> trig = near.stream().filter(p -> !p.isCreative() && inside(p, c, TRIGGER_R)).toList();
            if (!trig.isEmpty() && Story.mayWake(level, trig, 11)) spawn(level, c, trig);
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

    private static void spawn(ServerLevel level, Vec3 c, List<ServerPlayer> party) {
        BlockPos at = BlockPos.containing(c.x, c.y + 1, c.z);
        Mob m = EntityType.RAVAGER.spawn(level, at, MobSpawnType.EVENT);
        if (m == null) return;
        m.addTag(TAG);
        m.setCustomName(Component.literal("§6§l" + NAME));
        m.setCustomNameVisible(true);
        m.setPersistenceRequired();
        attr(m, Attributes.SCALE, 3.0);
        attr(m, Attributes.MAX_HEALTH, 700);
        attr(m, Attributes.ATTACK_DAMAGE, 18);
        attr(m, Attributes.ARMOR, 15);
        attr(m, Attributes.KNOCKBACK_RESISTANCE, 1.0);
        attr(m, Attributes.FOLLOW_RANGE, 48);
        attr(m, Attributes.STEP_HEIGHT, 3.0);
        int grade = Progression.apply(m, 11, party);
        m.setHealth(m.getMaxHealth());
        m.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, -1, 0, false, false));
        id = m.getUUID();
        bar = new ServerBossEvent(Component.literal(NAME + " §7[" + grade + "급]"), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_20);
        s66 = s33 = enraged = false;
        empty = 0;
        level.playSound(null, at, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 4F, 0.6F);
        say(level, at, "§4§l[고룡 봉우리] 황금 더미가 꿈틀거린다... " + NAME + "(이)가 깨어났다!");
    }

    private static void fight(ServerLevel level, Mob boss, List<ServerPlayer> near, Vec3 c) {
        if (bar == null) bar = new ServerBossEvent(Component.literal(NAME), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_20);
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
        Vec3 head = boss.position().add(0, boss.getBbHeight() * 0.8, 0);
        // 화염 숨결: 3초마다 불덩이 6발
        if (tick % 60 == 0) {
            level.playSound(null, boss.blockPosition(), SoundEvents.ENDER_DRAGON_SHOOT, SoundSource.HOSTILE, 3F, 0.7F);
            Vec3 aim = target.position().add(0, 1, 0).subtract(head).normalize();
            for (int i = 0; i < 6; i++) {
                Vec3 v = aim.add((level.random.nextDouble() - 0.5) * 0.25, (level.random.nextDouble() - 0.5) * 0.15, (level.random.nextDouble() - 0.5) * 0.25);
                SmallFireball f = new SmallFireball(level, boss, v.normalize());
                f.setPos(head.x + aim.x * 2, head.y, head.z + aim.z * 2);
                level.addFreshEntity(f);
            }
        }
        // 날갯짓 돌풍: 10초마다, 12칸 안을 밀쳐냄
        if (tick % 200 == 100) {
            level.playSound(null, boss.blockPosition(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.HOSTILE, 4F, 0.5F);
            for (ServerPlayer p : near) {
                if (p.isCreative() || p.distanceToSqr(boss) > 12 * 12) continue;
                p.hurt(level.damageSources().mobAttack(boss), 8F);
                Vec3 push = p.position().subtract(boss.position()).normalize().scale(2.2);
                p.push(push.x, 0.8, push.z);
                p.hurtMarked = true;
                p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
            }
        }
        // 불비: 체력 절반 아래부터 7초마다, 천장에서 불덩이가 떨어짐
        if (hp < 0.5F && tick % 140 == 70) {
            for (ServerPlayer p : near) {
                if (p.isCreative()) continue;
                for (int i = 0; i < 4; i++) {
                    SmallFireball f = new SmallFireball(level, boss, new Vec3(0, -1, 0));
                    f.setPos(p.getX() + level.random.nextInt(7) - 3, p.getY() + 12, p.getZ() + level.random.nextInt(7) - 3);
                    level.addFreshEntity(f);
                }
            }
        }
        if (hp <= 0.66F && !s66) {
            s66 = true;
            say(level, boss.blockPosition(), "§c[고룡 봉우리] " + NAME + "의 울부짖음에 용의 권속이 몰려든다!");
            minions(level, boss);
        }
        if (hp <= 0.33F && !s33) {
            s33 = true;
            minions(level, boss);
        }
        if (hp <= 0.25F && !enraged) {
            enraged = true;
            level.playSound(null, boss.blockPosition(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 4F, 0.4F);
            say(level, boss.blockPosition(), "§4[고룡 봉우리] " + NAME + "(이)가 분노로 불타오른다!");
            boss.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20 * 600, 1));
            boss.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 20 * 600, 1));
        }
    }

    private static void minions(ServerLevel level, Mob boss) {
        if (level.getEntitiesOfClass(Mob.class, boss.getBoundingBox().inflate(40), m -> m.getTags().contains(MINION)).size() >= 10) return;
        EntityType<?>[] kin = {EntityType.BLAZE, EntityType.BLAZE, EntityType.BLAZE, EntityType.MAGMA_CUBE, EntityType.MAGMA_CUBE};
        for (int i = 0; i < kin.length; i++) {
            double a = Math.PI * 2 * i / kin.length;
            BlockPos pos = BlockPos.containing(boss.getX() + Math.cos(a) * 9, DragonLair.HOARD_Y + 2, boss.getZ() + Math.sin(a) * 9);
            var e = kin[i].spawn(level, pos, MobSpawnType.MOB_SUMMONED);
            if (e instanceof Mob m) {
                m.addTag(MINION);
                m.setCustomName(Component.literal("용의 권속"));
            }
        }
    }

    public static void onDeath(LivingDeathEvent event) {
        LivingEntity e = event.getEntity();
        if (!(e.level() instanceof ServerLevel level) || !e.getTags().contains(TAG)) return;
        clear();
        id = null;
        deadUntil = level.getGameTime() + RESPAWN_TICKS;
        level.playSound(null, e.blockPosition(), SoundEvents.ENDER_DRAGON_DEATH, SoundSource.HOSTILE, 3F, 0.8F);
        say(level, e.blockPosition(), "§6§l[고룡 봉우리] " + NAME + "(을)를 쓰러뜨렸다! 황금 더미가 그대의 것이다.");
        ItemStack[] loot = {new ItemStack(Items.DRAGON_HEAD), new ItemStack(Items.ELYTRA), new ItemStack(Items.DIAMOND, 12),
                new ItemStack(Items.NETHERITE_SCRAP, 4), new ItemStack(Items.ENCHANTED_GOLDEN_APPLE), new ItemStack(Items.EXPERIENCE_BOTTLE, 32),
                new ItemStack(Items.GOLD_INGOT, 32)};
        for (ItemStack s : loot) level.addFreshEntity(new ItemEntity(level, e.getX(), e.getY() + 1, e.getZ(), s));
        Story.credit(level, e.blockPosition(), 11);
    }

    private static void clear() {
        if (bar != null) bar.removeAllPlayers();
        bar = null;
    }

    private static void say(ServerLevel level, BlockPos at, String msg) {
        for (ServerPlayer p : level.players()) if (p.blockPosition().distSqr(at) < 120 * 120) p.sendSystemMessage(Component.literal(msg));
    }
}
