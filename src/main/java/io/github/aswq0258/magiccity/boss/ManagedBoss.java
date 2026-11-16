package io.github.aswq0258.magiccity.boss;

import io.github.aswq0258.magiccity.world.ModWorldgen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 챕터 보스 공용 틀 (초안): 자리에 다가가면 깨어남 → 체력바 → 고유 기술 → 66%·33% 부하 소환 → 25% 광폭화
 * → 쓰러뜨리면 전리품·챕터 완료, 30분 뒤 다시 나타남. 모두 떠나면 1분 뒤 다시 잠듦.
 * 보스마다 바탕 몹·능력치·기술·부하·전리품만 다르게 정의 (NewBosses).
 */
public abstract class ManagedBoss {
    protected final String tag, minionTag, name, region;
    protected final int chapter;
    protected int triggerR = 16, leashR = 26, activeR = 44;
    protected long respawnTicks = 20L * 60 * 30;
    protected BossEvent.BossBarColor color = BossEvent.BossBarColor.RED;

    private UUID id;
    private ServerBossEvent bar;
    private int empty;
    private boolean s66, s33, enraged;
    private long deadUntil;
    protected int tick;

    protected ManagedBoss(String key, String name, String region, int chapter) {
        this.tag = "magiccity_" + key;
        this.minionTag = "magiccity_" + key + "_minion";
        this.name = name;
        this.region = region;
        this.chapter = chapter;
    }

    // ── 보스마다 정할 것
    protected abstract Vec3 center();

    /** 보스 자리 안의 높이인지 */
    protected boolean inY(double y, Vec3 c) {
        return y > c.y - 12 && y < c.y + 30;
    }

    /** 바탕 몹을 만들고 능력치를 맞춤 */
    protected abstract Mob create(ServerLevel level, BlockPos at);

    /** 고유 기술 (5틱마다 불림) */
    protected abstract void abilities(ServerLevel level, Mob boss, ServerPlayer target, List<ServerPlayer> near, float hp);

    protected abstract EntityType<?>[] minions();

    protected abstract ItemStack[] loot();

    protected abstract SoundEvent roar();

    protected String wakeText() {
        return name + "(이)가 모습을 드러냈다!";
    }

    /** 깨어날 수 있는지 (특별한 조건 — 기본은 챕터 입장 조건) */
    protected boolean mayWake(ServerLevel level, List<ServerPlayer> trigger) {
        return Story.mayWake(level, trigger, chapter);
    }

    /** 보스가 없을 때도 매번 불림 (습격 이벤트처럼 보스 전에 할 일이 있으면) — true 를 돌려주면 그 틱은 깨우지 않음 */
    protected boolean preFight(ServerLevel level, List<ServerPlayer> near, Vec3 c) {
        return false;
    }

    protected void onEnrage(ServerLevel level, Mob boss) {}

    protected void onDefeat(ServerLevel level, LivingEntity boss) {}

    // ── 공용 진행

    private boolean inside(ServerPlayer p, Vec3 c, double r) {
        double dx = p.getX() - c.x, dz = p.getZ() - c.z;
        return dx * dx + dz * dz < r * r && inY(p.getY(), c);
    }

    final void tick(ServerLevel level) {
        Vec3 c = center();
        List<ServerPlayer> near = level.getPlayers(p -> !p.isSpectator() && inside(p, c, activeR));
        if (near.isEmpty() && id == null) return;
        Mob boss = find(level, c);
        if (boss == null) {
            clearBar();
            if (level.getGameTime() < deadUntil || level.getDifficulty() == Difficulty.PEACEFUL) return;
            if (preFight(level, near, c)) return;
            List<ServerPlayer> trig = near.stream().filter(p -> !p.isCreative() && inside(p, c, triggerR)).toList();
            if (!trig.isEmpty() && mayWake(level, trig)) wake(level, c);
            return;
        }
        fight(level, boss, near, c);
    }

    private Mob find(ServerLevel level, Vec3 c) {
        if (id != null && level.getEntity(id) instanceof Mob m && m.isAlive()) return m;
        id = null;
        List<Mob> found = level.getEntitiesOfClass(Mob.class, new AABB(c, c).inflate(activeR), m -> m.getTags().contains(tag) && m.isAlive());
        if (found.isEmpty()) return null;
        id = found.get(0).getUUID();
        return found.get(0);
    }

    private void wake(ServerLevel level, Vec3 c) {
        BlockPos at = BlockPos.containing(c.x, c.y, c.z);
        Mob m = create(level, at);
        if (m == null) return;
        m.addTag(tag);
        m.setCustomName(Component.literal("§l" + name));
        m.setCustomNameVisible(true);
        m.setPersistenceRequired();
        m.setHealth(m.getMaxHealth());
        id = m.getUUID();
        s66 = s33 = enraged = false;
        empty = 0;
        level.playSound(null, at, roar(), SoundSource.HOSTILE, 4F, 0.6F);
        say(level, at, "§4§l[" + region + "] " + wakeText());
    }

    private void fight(ServerLevel level, Mob boss, List<ServerPlayer> near, Vec3 c) {
        if (bar == null) bar = new ServerBossEvent(Component.literal(name), color, BossEvent.BossBarOverlay.NOTCHED_20);
        float hp = boss.getHealth() / boss.getMaxHealth();
        bar.setProgress(hp);
        for (ServerPlayer p : near) bar.addPlayer(p);
        for (ServerPlayer p : new ArrayList<>(bar.getPlayers())) if (!near.contains(p)) bar.removePlayer(p);
        if (near.isEmpty()) {
            if ((empty += 5) > 1200) {
                boss.discard();
                clearBar();
            }
            return;
        }
        empty = 0;
        if (boss.position().distanceToSqr(c) > leashR * leashR) boss.teleportTo(c.x, c.y, c.z);
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
        abilities(level, boss, target, near, hp);
        if (hp <= 0.66F && !s66) {
            s66 = true;
            say(level, boss.blockPosition(), "§c[" + region + "] " + name + "(이)가 부하를 부른다!");
            summon(level, boss);
        }
        if (hp <= 0.33F && !s33) {
            s33 = true;
            summon(level, boss);
        }
        if (hp <= 0.25F && !enraged) {
            enraged = true;
            level.playSound(null, boss.blockPosition(), roar(), SoundSource.HOSTILE, 4F, 0.4F);
            say(level, boss.blockPosition(), "§4[" + region + "] " + name + "(이)가 광폭해진다!");
            boss.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20 * 600, 1));
            boss.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 20 * 600, 1));
            onEnrage(level, boss);
        }
    }

    protected void summon(ServerLevel level, Mob boss) {
        if (level.getEntitiesOfClass(Mob.class, boss.getBoundingBox().inflate(40), m -> m.getTags().contains(minionTag)).size() >= 10) return;
        EntityType<?>[] kin = minions();
        for (int i = 0; i < kin.length; i++) {
            double a = Math.PI * 2 * i / kin.length;
            BlockPos pos = BlockPos.containing(boss.getX() + Math.cos(a) * 6, boss.getY() + 1, boss.getZ() + Math.sin(a) * 6);
            var e = kin[i].spawn(level, pos, MobSpawnType.MOB_SUMMONED);
            if (e instanceof Mob m) m.addTag(minionTag);
        }
    }

    final boolean died(LivingEntity e) {
        if (!(e.level() instanceof ServerLevel level) || !e.getTags().contains(tag)) return false;
        clearBar();
        id = null;
        deadUntil = level.getGameTime() + respawnTicks;
        say(level, e.blockPosition(), "§6§l[" + region + "] " + name + "(을)를 쓰러뜨렸다!");
        for (ItemStack s : loot()) level.addFreshEntity(new ItemEntity(level, e.getX(), e.getY() + 1, e.getZ(), s));
        onDefeat(level, e);
        Story.credit(level, e.blockPosition(), chapter);
        return true;
    }

    private void clearBar() {
        if (bar != null) bar.removeAllPlayers();
        bar = null;
    }

    // ── 도우미

    protected static void attr(Mob m, Holder<Attribute> a, double v) {
        AttributeInstance i = m.getAttribute(a);
        if (i != null) i.setBaseValue(v);
    }

    protected static void stats(Mob m, double scale, double hp, double dmg, double armor) {
        attr(m, Attributes.SCALE, scale);
        attr(m, Attributes.MAX_HEALTH, hp);
        attr(m, Attributes.ATTACK_DAMAGE, dmg);
        attr(m, Attributes.ARMOR, armor);
        attr(m, Attributes.KNOCKBACK_RESISTANCE, 1.0);
        attr(m, Attributes.FOLLOW_RANGE, 48);
        attr(m, Attributes.STEP_HEIGHT, Math.max(1, scale));
    }

    /** 반지름 r 안 플레이어에게 피해·밀치기·효과 */
    protected static void blast(ServerLevel level, Mob boss, List<ServerPlayer> near, double r, float dmg, double push, MobEffectInstance eff) {
        for (ServerPlayer p : near) {
            if (p.isCreative() || p.distanceToSqr(boss) > r * r) continue;
            if (dmg > 0) p.hurt(level.damageSources().mobAttack(boss), dmg);
            if (push != 0) {
                Vec3 v = p.position().subtract(boss.position()).normalize().scale(push);
                p.push(v.x, Math.abs(push) * 0.35, v.z);
                p.hurtMarked = true;
            }
            if (eff != null) p.addEffect(new MobEffectInstance(eff));
        }
    }

    protected static void say(ServerLevel level, BlockPos at, String msg) {
        for (ServerPlayer p : level.players()) if (p.blockPosition().distSqr(at) < 120 * 120) p.sendSystemMessage(Component.literal(msg));
    }

    // ───────────────────────── 등록 ─────────────────────────

    public static final List<ManagedBoss> ALL = List.of(
            new NewBosses.Sewer(), new NewBosses.Parasite(), new NewBosses.FrostGiant(), new NewBosses.Furnace(),
            new NewBosses.DeepGuardian(), new NewBosses.Siege(), new NewBosses.AbyssWatcher());

    private static int ticks;

    public static void onServerTick(ServerTickEvent.Post event) {
        if (++ticks % 5 != 0) return;
        ServerLevel level = event.getServer().getLevel(ModWorldgen.KINGDOM);
        if (level == null || level.players().isEmpty()) return;
        for (ManagedBoss b : ALL) {
            b.tick = ticks;
            b.tick(level);
        }
    }

    public static void onDeath(LivingDeathEvent event) {
        for (ManagedBoss b : ALL) if (b.died(event.getEntity())) return;
    }
}
