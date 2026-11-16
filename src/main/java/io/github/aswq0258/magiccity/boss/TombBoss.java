package io.github.aswq0258.magiccity.boss;

import io.github.aswq0258.magiccity.site.AncientRuins;
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
 * 고대 왕국 유적 왕릉의 보스 "고대왕 아르케온" — 왕의 묘실 석관에 다가가면 깨어남.
 *  - 황금 갑옷의 거대한 위더 해골(x2), 모래 폭풍(실명·구속), 체력 절반 아래부터 왕의 심판(발밑 송곳니)
 *  - 체력 66%·33% 에서 왕릉의 병사(허스크·스트레이) 소환, 25% 아래 광폭화
 *  - 쓰러뜨리면 마법이 부여된 황금 사과·금 블록·다이아몬드·에메랄드·스니퍼 알, 30분 뒤 다시 나타남
 */
public final class TombBoss {
    private TombBoss() {}

    private static final String TAG = "magiccity_tombboss", MINION = "magiccity_tombkin";
    private static final String NAME = "고대왕 아르케온";
    private static final int TRIGGER_R = 10, LEASH_R = 14, ACTIVE_R = 40;
    private static final long RESPAWN_TICKS = 20L * 60 * 30;

    private static UUID id;
    private static ServerBossEvent bar;
    private static int empty, tick;
    private static boolean s66, s33, enraged;
    private static long deadUntil;

    private static Vec3 center() {
        return new Vec3(AncientRuins.KING_X + 0.5, AncientRuins.TOMB_Y + 3, AncientRuins.KING_Z + 3.5);
    }

    private static boolean inside(ServerPlayer p, Vec3 c, double r) {
        double dx = p.getX() - c.x, dz = p.getZ() - c.z;
        return dx * dx + dz * dz < r * r && p.getY() > c.y - 6 && p.getY() < c.y + 10;
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
        Mob m = EntityType.WITHER_SKELETON.spawn(level, at, MobSpawnType.EVENT);
        if (m == null) return;
        m.addTag(TAG);
        m.setCustomName(Component.literal("§6§l" + NAME));
        m.setCustomNameVisible(true);
        m.setPersistenceRequired();
        attr(m, Attributes.SCALE, 2.0);
        attr(m, Attributes.MAX_HEALTH, 380);
        attr(m, Attributes.ATTACK_DAMAGE, 10);
        attr(m, Attributes.ARMOR, 15);
        attr(m, Attributes.KNOCKBACK_RESISTANCE, 1.0);
        attr(m, Attributes.FOLLOW_RANGE, 48);
        attr(m, Attributes.STEP_HEIGHT, 3.0);
        m.setHealth(m.getMaxHealth());
        m.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, -1, 0, false, false));
        id = m.getUUID();
        bar = new ServerBossEvent(Component.literal(NAME), BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.NOTCHED_20);
        s66 = s33 = enraged = false;
        empty = 0;
        m.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.GOLDEN_HELMET));
        m.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new ItemStack(Items.GOLDEN_CHESTPLATE));
        m.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.GOLDEN_SWORD));
        for (net.minecraft.world.entity.EquipmentSlot sl : net.minecraft.world.entity.EquipmentSlot.values()) m.setDropChance(sl, 0F);
        level.playSound(null, at, SoundEvents.WITHER_SKELETON_AMBIENT, SoundSource.HOSTILE, 4F, 0.5F);
        say(level, at, "§6§l[고대 왕국 유적] 석관 뚜껑이 미끄러진다... " + NAME + "(이)가 잠에서 깨어났다!");
    }

    private static void fight(ServerLevel level, Mob boss, List<ServerPlayer> near, Vec3 c) {
        if (bar == null) bar = new ServerBossEvent(Component.literal(NAME), BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.NOTCHED_20);
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
        // 모래 폭풍: 10초마다 14칸 안에 실명·구속
        if (tick % 200 == 100) {
            level.playSound(null, boss.blockPosition(), SoundEvents.SAND_BREAK, SoundSource.HOSTILE, 3F, 0.5F);
            for (ServerPlayer p : near) {
                if (p.isCreative() || p.distanceToSqr(boss) > 14 * 14) continue;
                p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0));
                p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 1));
            }
        }
        // 왕의 심판: 체력 절반 아래부터 6초마다 플레이어 발밑에서 송곳니가 솟음
        if (hp < 0.5F && tick % 120 == 0) {
            for (ServerPlayer p : near) {
                if (p.isCreative()) continue;
                for (int i = -2; i <= 2; i++) for (int j = -2; j <= 2; j += 2) {
                    net.minecraft.world.entity.projectile.EvokerFangs fang = new net.minecraft.world.entity.projectile.EvokerFangs(level,
                            p.getX() + i, p.getY(), p.getZ() + j, 0F, 10 + Math.abs(i) * 2, boss);
                    level.addFreshEntity(fang);
                }
            }
        }
        if (hp <= 0.66F && !s66) {
            s66 = true;
            say(level, boss.blockPosition(), "§6[고대 왕국 유적] " + NAME + "(이)가 왕릉의 병사들을 깨운다!");
            minions(level, boss);
        }
        if (hp <= 0.33F && !s33) {
            s33 = true;
            minions(level, boss);
        }
        if (hp <= 0.25F && !enraged) {
            enraged = true;
            say(level, boss.blockPosition(), "§4[고대 왕국 유적] " + NAME + "(이)가 분노에 휩싸인다!");
            boss.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20 * 600, 1));
            boss.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 20 * 600, 1));
        }
    }

    private static void minions(ServerLevel level, Mob boss) {
        if (level.getEntitiesOfClass(Mob.class, boss.getBoundingBox().inflate(40), m -> m.getTags().contains(MINION)).size() >= 8) return;
        EntityType<?>[] kin = {EntityType.HUSK, EntityType.HUSK, EntityType.STRAY, EntityType.HUSK};
        for (int i = 0; i < kin.length; i++) {
            double a = Math.PI * 2 * i / kin.length;
            BlockPos pos = BlockPos.containing(boss.getX() + Math.cos(a) * 7, boss.getY(), boss.getZ() + Math.sin(a) * 7);
            var e = kin[i].spawn(level, pos, MobSpawnType.MOB_SUMMONED);
            if (e instanceof Mob m) {
                m.addTag(MINION);
                m.setCustomName(Component.literal("왕릉의 병사"));
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
        say(level, e.blockPosition(), "§6§l[고대 왕국 유적] " + NAME + "(을)를 쓰러뜨렸다! 왕의 보물이 그대의 것이다.");
        ItemStack[] loot = {new ItemStack(Items.ENCHANTED_GOLDEN_APPLE), new ItemStack(Items.GOLD_BLOCK, 8), new ItemStack(Items.DIAMOND, 6),
                new ItemStack(Items.EMERALD, 16), new ItemStack(Items.SNIFFER_EGG), new ItemStack(Items.EXPERIENCE_BOTTLE, 24)};
        for (ItemStack s : loot) level.addFreshEntity(new ItemEntity(level, e.getX(), e.getY() + 1, e.getZ(), s));
        Story.credit(level, e.blockPosition(), 7);
    }

    private static void clear() {
        if (bar != null) bar.removeAllPlayers();
        bar = null;
    }

    private static void say(ServerLevel level, BlockPos at, String msg) {
        for (ServerPlayer p : level.players()) if (p.blockPosition().distSqr(at) < 120 * 120) p.sendSystemMessage(Component.literal(msg));
    }
}
