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
 * 챕터 보스 공용 틀: 체력바 → 고유 기술 → 66%·33% 부하 소환 → 25% 광폭화 → 쓰러뜨리면 전리품·챕터 완료.
 * 보스마다 바탕 몹·능력치·기술·부하·전리품만 다르게 정의 (NewBosses, ClassicBosses).
 *
 * <p>2026-11-19 인스턴스 전장: 보스는 더 이상 지역 자리에서 깨어나지 않는다. 지역 자리({@link #center()})에는 봉인 제단이 놓이고,
 * 전투는 {@code arena.ArenaManager} 가 전용 차원에서 {@link #arenaSpawn}·{@link #arenaTick} 으로 진행한다.
 * 지역 쪽 틱은 습격 같은 사전 단계({@link #preFight})와 예전 판에서 남은 보스 정리만 한다.
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
    /** 이번 전투의 등급 (깨어날 때 정해지고 전투 중에는 바뀌지 않음) */
    protected int grade = ProgressionRules.MAX_GRADE;
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

    /**
     * 지역 자리에서 보스 전에 할 일(습격 등). 끝났으면 false — 그때 {@link #onPreFightCleared} 가 불리고 제단이 열린다.
     * 기본은 할 일 없음(false).
     */
    protected boolean preFight(ServerLevel level, List<ServerPlayer> near, Vec3 c) {
        return false;
    }

    /** 사전 단계가 있는 보스인지 (제단이 처음부터 열려 있지 않음) */
    public boolean hasPreFight() {
        return false;
    }

    private boolean preCleared;

    /** 사전 단계가 끝나 제단이 열렸는지 (사전 단계가 없으면 늘 true) */
    public boolean altarOpen() {
        return !hasPreFight() || preCleared;
    }

    /** 사전 단계를 막아 낸 순간 */
    protected void onPreFightCleared(ServerLevel level, Vec3 c) {
        say(level, BlockPos.containing(c), "§e[" + region + "] 봉인 제단이 깨어났다. 제단을 눌러 " + name + "(와)과의 결전에 나서라.");
    }

    /** 전장 승리 뒤 사전 단계를 처음으로 되돌림 */
    public void resetPreFight() {
        preCleared = false;
    }

    /** 전장 입장 조건 (기본: 챕터 입장 조건을 참가자 중 한 명이라도 만족). 막히면 이유, 열리면 null */
    public String entryBlocked(ServerLevel level, List<ServerPlayer> party) {
        if (!altarOpen()) return "아직 제단이 잠들어 있다 (" + region + "의 사전 단계를 먼저 끝내야 함)";
        for (ServerPlayer p : party) if (Story.requirementMet(p, chapter)) return null;
        return party.isEmpty() ? "참가자가 없음" : Story.requirementText(party.get(0), chapter);
    }

    public String key() {
        return tag.substring("magiccity_".length());
    }

    public String name() {
        return name;
    }

    public String region() {
        return region;
    }

    public int chapter() {
        return chapter;
    }

    /** 지역 쪽 제단 자리 (보스가 깨어나던 자리) */
    public Vec3 altarCenter() {
        return center();
    }

    /** 전장에서 보스가 설 자리 높이 (전장 바닥 + 1 기준 보정, 예: 6장 물속은 위로) */
    public double arenaYOffset() {
        return 0;
    }

    /** 이 보스가 쓰는 전장 모양 (6장 물로 찬 돔 등) */
    public String arenaShape() {
        return "basic";
    }

    protected void onEnrage(ServerLevel level, Mob boss) {}

    protected void onDefeat(ServerLevel level, LivingEntity boss) {}

    // ── 공용 진행

    private boolean inside(ServerPlayer p, Vec3 c, double r) {
        double dx = p.getX() - c.x, dz = p.getZ() - c.z;
        return dx * dx + dz * dz < r * r && inY(p.getY(), c);
    }

    /** 지역 쪽 틱 (5틱마다): 사전 단계만 진행하고, 예전 판에서 지역에 남아 있던 보스는 치움 */
    final void tick(ServerLevel level) {
        Vec3 c = center();
        List<ServerPlayer> near = level.getPlayers(p -> !p.isSpectator() && inside(p, c, activeR));
        if (hasPreFight() && !preCleared && level.getDifficulty() != Difficulty.PEACEFUL && !preFight(level, near, c)) {
            preCleared = true;
            onPreFightCleared(level, c);
        }
        if (near.isEmpty()) return;
        Mob stray = find(level, c);
        if (stray != null) {
            stray.discard();
            id = null;
            clearBar();
        }
    }

    // ── 전장 (arena.ArenaManager 가 부름)

    /** 전장에서 보스 소환. 등급은 참가자 중 최고 진행도 (지금 규칙 그대로) */
    public Mob arenaSpawn(ServerLevel level, Vec3 c, List<ServerPlayer> party) {
        clearBar();
        id = null;
        wake(level, c, party);
        return id == null ? null : level.getEntity(id) instanceof Mob m ? m : null;
    }

    /** 전장 전투 틱 (5틱마다). gameTick 은 기술 주기용 */
    public void arenaTick(ServerLevel level, Mob boss, List<ServerPlayer> near, Vec3 c, int gameTick) {
        this.tick = gameTick;
        fight(level, boss, near, c);
    }

    /** 전장이 끝날 때 상태 정리 */
    public void arenaEnd() {
        clearBar();
        id = null;
        s66 = s33 = enraged = false;
    }

    /** 전리품 (전장은 꾸러미로 줌) */
    public ItemStack[] lootStacks() {
        return loot();
    }

    /** 처치 연출·후처리 (전장 승리 때 ArenaManager 가 부름) */
    public void defeated(ServerLevel level, LivingEntity boss) {
        say(level, boss.blockPosition(), "§6§l[" + region + "] " + name + "(을)를 쓰러뜨렸다!");
        onDefeat(level, boss);
    }

    private Mob find(ServerLevel level, Vec3 c) {
        if (id != null && level.getEntity(id) instanceof Mob m && m.isAlive()) return m;
        id = null;
        List<Mob> found = level.getEntitiesOfClass(Mob.class, new AABB(c, c).inflate(activeR), m -> m.getTags().contains(tag) && m.isAlive());
        if (found.isEmpty()) return null;
        id = found.get(0).getUUID();
        return found.get(0);
    }

    private void wake(ServerLevel level, Vec3 c, List<ServerPlayer> party) {
        BlockPos at = BlockPos.containing(c.x, c.y, c.z);
        Mob m = create(level, at);
        if (m == null) return;
        m.addTag(tag);
        m.setCustomName(Component.literal("§l" + name));
        m.setCustomNameVisible(true);
        m.setPersistenceRequired();
        grade = Progression.apply(m, chapter, party);
        m.setHealth(m.getMaxHealth());
        id = m.getUUID();
        s66 = s33 = enraged = false;
        empty = 0;
        level.playSound(null, at, roar(), SoundSource.HOSTILE, 4F, 0.6F);
        say(level, at, "§4§l[" + region + "] " + wakeText() + " §7(" + grade + "급)");
    }

    private void fight(ServerLevel level, Mob boss, List<ServerPlayer> near, Vec3 c) {
        if (bar == null) {
            int g = Progression.gradeOf(boss);
            if (g > 0) grade = g;
            bar = new ServerBossEvent(Component.literal(name + (g > 0 ? " §7[" + g + "급]" : "")), color, BossEvent.BossBarOverlay.NOTCHED_20);
        }
        float hp = boss.getHealth() / boss.getMaxHealth();
        bar.setProgress(hp);
        for (ServerPlayer p : near) bar.addPlayer(p);
        for (ServerPlayer p : new ArrayList<>(bar.getPlayers())) if (!near.contains(p)) bar.removePlayer(p);
        if (near.isEmpty()) return;                                                          // 전장: 전멸·시간 초과는 ArenaManager 가 판정
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
            if (ProgressionRules.secondSummon(grade)) summon(level, boss);
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

    /** 이 보스의 엔티티인지 */
    public boolean isMine(LivingEntity e) {
        return e.getTags().contains(tag);
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

    /** 챕터 보스 12종 (장 순서) */
    public static final List<ManagedBoss> ALL = List.of(
            new NewBosses.Sewer(), new ClassicBosses.Cursed(), new NewBosses.Parasite(), new NewBosses.FrostGiant(), new NewBosses.Furnace(),
            new NewBosses.DeepGuardian(), new ClassicBosses.Tomb(), new NewBosses.Siege(), new ClassicBosses.Sky(), new NewBosses.AbyssWatcher(),
            new ClassicBosses.Dragon(), new ClassicBosses.Gate());

    /** 그 장의 보스 */
    public static ManagedBoss forChapter(int chapter) {
        for (ManagedBoss b : ALL) if (b.chapter == chapter) return b;
        return null;
    }

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

    /** 지역 쪽 보스 죽음은 더 이상 없음 (전장 승리는 arena.ArenaManager 가 처리) */
    public static void onDeath(LivingDeathEvent event) {
    }
}
