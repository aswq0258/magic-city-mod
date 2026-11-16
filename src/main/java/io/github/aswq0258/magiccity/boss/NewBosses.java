package io.github.aswq0258.magiccity.boss;

import io.github.aswq0258.magiccity.site.BorderCity;
import io.github.aswq0258.magiccity.site.CursedLands;
import io.github.aswq0258.magiccity.site.DwarfHold;
import io.github.aswq0258.magiccity.site.FrostArena;
import io.github.aswq0258.magiccity.site.PortCity;
import io.github.aswq0258.magiccity.site.RootDungeon;
import io.github.aswq0258.magiccity.world.KingdomTerrain;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 메인 스토리의 새 챕터 보스 (초안 — 외형은 바닐라 몹을 키운 임시 모습, 수치는 플레이하며 조정).
 *  1장 하수도의 역병 술사 / 3장 세계수 기생충 / 4장 서리 거인 요툰 / 5장 용광로의 거신 /
 *  6장 심해의 수호자 / 8장 약탈 군주 그림발드(습격 3단계 뒤) / 10장 심연의 감시자
 */
public final class NewBosses {
    private NewBosses() {}

    private static MobEffectInstance eff(net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> e, int ticks, int amp) {
        return new MobEffectInstance(e, ticks, amp);
    }

    // ───────────────────────── 1장 하수도의 역병 술사 ─────────────────────────

    /** 왕성 하수도 초대 왕의 납골당. 거대 좀비 주민, 독 구름, 좀비 부하 */
    public static final class Sewer extends ManagedBoss {
        public Sewer() {
            super("plague", "하수도의 역병 술사", "왕성 하수도", 1);
            triggerR = 12;
            leashR = 18;
            activeR = 30;
            color = BossEvent.BossBarColor.GREEN;
        }

        @Override protected Vec3 center() { return new Vec3(260.5, KingdomTerrain.CITY_Y - 17, 259.5); }

        @Override protected boolean inY(double y, Vec3 c) { return y > c.y - 4 && y < c.y + 13; }

        @Override
        protected Mob create(ServerLevel level, BlockPos at) {
            Mob m = EntityType.ZOMBIE_VILLAGER.spawn(level, at, MobSpawnType.EVENT);
            if (m != null) stats(m, 1.8, 150, 6, 4);
            return m;
        }

        @Override
        protected void abilities(ServerLevel level, Mob boss, ServerPlayer target, List<ServerPlayer> near, float hp) {
            if (tick % 80 == 0) {                                                            // 독 구름
                AreaEffectCloud c = new AreaEffectCloud(level, target.getX(), target.getY(), target.getZ());
                c.setOwner(boss);
                c.setRadius(3.5F);
                c.setDuration(120);
                c.setRadiusPerTick(-0.01F);
                c.addEffect(eff(MobEffects.POISON, 100, 0));
                level.addFreshEntity(c);
                level.playSound(null, boss.blockPosition(), SoundEvents.WITCH_THROW, SoundSource.HOSTILE, 2F, 0.6F);
            }
            if (tick % 200 == 100) blast(level, boss, near, 6, 3, 0, eff(MobEffects.HUNGER, 200, 1));   // 역병의 숨
        }

        @Override protected EntityType<?>[] minions() { return new EntityType<?>[]{EntityType.ZOMBIE, EntityType.ZOMBIE, EntityType.ZOMBIE_VILLAGER, EntityType.ZOMBIE}; }

        @Override
        protected ItemStack[] loot() {
            return new ItemStack[]{new ItemStack(Items.IRON_SWORD), new ItemStack(Items.IRON_CHESTPLATE), new ItemStack(Items.IRON_INGOT, 12),
                    new ItemStack(Items.GOLDEN_APPLE, 2), new ItemStack(Items.EXPERIENCE_BOTTLE, 8)};
        }

        @Override protected SoundEvent roar() { return SoundEvents.ZOMBIE_VILLAGER_AMBIENT; }

        @Override protected String wakeText() { return "납골당 깊은 곳에서 역병의 기운이 피어오른다... " + name + "(이)가 나타났다!"; }
    }

    // ───────────────────────── 3장 세계수 기생충 ─────────────────────────

    /** 뿌리 던전 기생충의 둥지. 거대 좀벌레, 산성 분사, 땅굴 잠행, 새끼 기생충 */
    public static final class Parasite extends ManagedBoss {
        public Parasite() {
            super("parasite", "세계수 기생충", "세계수 뿌리 던전", 3);
            triggerR = 22;
            leashR = 32;
            activeR = 40;
            color = BossEvent.BossBarColor.PURPLE;
        }

        @Override protected Vec3 center() { return new Vec3(RootDungeon.BOSS_X + 0.5, RootDungeon.BOSS_Y, RootDungeon.BOSS_Z + 0.5); }

        @Override protected boolean inY(double y, Vec3 c) { return y > c.y - 4 && y < c.y + RootDungeon.ARENA_H; }

        @Override
        protected Mob create(ServerLevel level, BlockPos at) {
            Mob m = EntityType.SILVERFISH.spawn(level, at, MobSpawnType.EVENT);
            if (m != null) {
                stats(m, 7, 350, 9, 8);
                attr(m, net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED, 0.3);
            }
            return m;
        }

        @Override
        protected void abilities(ServerLevel level, Mob boss, ServerPlayer target, List<ServerPlayer> near, float hp) {
            if (tick % 100 == 0) {                                                           // 산성 분사
                level.sendParticles(ParticleTypes.ITEM_SLIME, boss.getX(), boss.getY() + 1.5, boss.getZ(), 60, 3, 1, 3, 0.1);
                level.playSound(null, boss.blockPosition(), SoundEvents.SLIME_SQUISH, SoundSource.HOSTILE, 3F, 0.5F);
                blast(level, boss, near, 8, 4, 0.6, eff(MobEffects.POISON, 120, 1));
            }
            if (tick % 240 == 120) {                                                         // 땅굴 잠행: 뿌리 사이로 파고들어 등 뒤에서 튀어나옴
                Vec3 behind = target.position().subtract(target.getLookAngle().multiply(4, 0, 4));
                level.sendParticles(ParticleTypes.POOF, boss.getX(), boss.getY(), boss.getZ(), 30, 1, 0.5, 1, 0.05);
                boss.teleportTo(behind.x, target.getY(), behind.z);
                level.playSound(null, boss.blockPosition(), SoundEvents.SILVERFISH_STEP, SoundSource.HOSTILE, 3F, 0.4F);
                blast(level, boss, near, 4, 6, 1.2, null);
            }
            if (hp < 0.5F && tick % 160 == 40) {                                             // 수액 흡수: 체력 회복
                boss.heal(10);
                level.sendParticles(ParticleTypes.FALLING_HONEY, boss.getX(), boss.getY() + 2, boss.getZ(), 20, 1.5, 1, 1.5, 0);
            }
        }

        @Override protected EntityType<?>[] minions() { return new EntityType<?>[]{EntityType.SILVERFISH, EntityType.SILVERFISH, EntityType.SILVERFISH, EntityType.ENDERMITE, EntityType.CAVE_SPIDER}; }

        @Override
        protected ItemStack[] loot() {
            return new ItemStack[]{new ItemStack(Items.TOTEM_OF_UNDYING), new ItemStack(Items.GOLDEN_APPLE, 4), new ItemStack(Items.EMERALD, 16),
                    new ItemStack(Items.DIAMOND, 4), new ItemStack(Items.EXPERIENCE_BOTTLE, 16)};
        }

        @Override protected SoundEvent roar() { return SoundEvents.RAVAGER_ROAR; }

        @Override protected String wakeText() { return "원뿌리를 갉던 것이 수액 웅덩이에서 솟아오른다... " + name + "!"; }
    }

    // ───────────────────────── 4장 서리 거인 요툰 ─────────────────────────

    /** 엘프 관문 북쪽 서리 결투장. 거대 스트레이, 땅 찍기, 눈보라 */
    public static final class FrostGiant extends ManagedBoss {
        public FrostGiant() {
            super("frost", "서리 거인 요툰", "서리 결투장", 4);
            triggerR = 18;
            leashR = 24;
            activeR = 40;
            color = BossEvent.BossBarColor.BLUE;
        }

        @Override protected Vec3 center() { return new Vec3(FrostArena.AX + 0.5, FrostArena.AY + 1, FrostArena.AZ + 0.5); }

        @Override
        protected Mob create(ServerLevel level, BlockPos at) {
            Mob m = EntityType.STRAY.spawn(level, at, MobSpawnType.EVENT);
            if (m != null) {
                stats(m, 4, 450, 12, 10);
                m.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
            }
            return m;
        }

        @Override
        protected void abilities(ServerLevel level, Mob boss, ServerPlayer target, List<ServerPlayer> near, float hp) {
            if (tick % 160 == 0) {                                                           // 땅 찍기: 구속
                level.playSound(null, boss.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 2F, 0.6F);
                level.sendParticles(ParticleTypes.SNOWFLAKE, boss.getX(), boss.getY() + 0.5, boss.getZ(), 120, 5, 0.3, 5, 0.1);
                blast(level, boss, near, 9, 8, 1.6, eff(MobEffects.MOVEMENT_SLOWDOWN, 80, 3));
            }
            if (tick % 300 == 150) {                                                         // 눈보라: 시야 가림·얼어붙음
                say(level, boss.blockPosition(), "§b[서리 결투장] 눈보라가 몰아친다!");
                for (ServerPlayer p : near) {
                    if (p.isCreative()) continue;
                    p.addEffect(eff(MobEffects.BLINDNESS, 60, 0));
                    p.setTicksFrozen(Math.min(p.getTicksRequiredToFreeze() + 100, p.getTicksFrozen() + 160));
                }
            }
        }

        @Override protected EntityType<?>[] minions() { return new EntityType<?>[]{EntityType.STRAY, EntityType.STRAY, EntityType.STRAY, EntityType.SKELETON}; }

        @Override
        protected ItemStack[] loot() {
            return new ItemStack[]{new ItemStack(Items.BOW), new ItemStack(Items.SPECTRAL_ARROW, 32), new ItemStack(Items.BLUE_ICE, 16),
                    new ItemStack(Items.DIAMOND, 6), new ItemStack(Items.EXPERIENCE_BOTTLE, 16)};
        }

        @Override protected SoundEvent roar() { return SoundEvents.POLAR_BEAR_WARNING; }

        @Override protected String wakeText() { return "협곡이 얼어붙는다... 서리 군단의 우두머리 " + name + "(이)가 내려왔다!"; }
    }

    // ───────────────────────── 5장 용광로의 거신 ─────────────────────────

    /** 드워프 대장간. 달아오른 거대 철 골렘, 내려찍기, 불길, 블레이즈 부하 */
    public static final class Furnace extends ManagedBoss {
        public static final int FX = DwarfHold.CX - 52, FZ = DwarfHold.CZ - 145;

        public Furnace() {
            super("furnace", "용광로의 거신", "드워프 대장간", 5);
            triggerR = 14;
            leashR = 20;
            activeR = 32;
            color = BossEvent.BossBarColor.RED;
        }

        private static int floor() {
            return KingdomTerrain.column(DwarfHold.CX, DwarfHold.CZ - 50).h();
        }

        @Override protected Vec3 center() { return new Vec3(FX + 0.5, floor() + 1, FZ + 0.5); }

        @Override protected boolean inY(double y, Vec3 c) { return y > c.y - 3 && y < c.y + 13; }

        @Override
        protected Mob create(ServerLevel level, BlockPos at) {
            Mob m = EntityType.IRON_GOLEM.spawn(level, at, MobSpawnType.EVENT);
            if (m != null) {
                stats(m, 2.2, 500, 18, 14);
                m.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, -1, 0, false, false));
            }
            return m;
        }

        @Override
        protected void abilities(ServerLevel level, Mob boss, ServerPlayer target, List<ServerPlayer> near, float hp) {
            boss.setTarget(target);                                                          // 골렘이 몬스터(부하)를 쫓지 않게
            if (tick % 120 == 0) {                                                           // 내려찍기
                level.playSound(null, boss.blockPosition(), SoundEvents.IRON_GOLEM_ATTACK, SoundSource.HOSTILE, 3F, 0.5F);
                level.sendParticles(ParticleTypes.LAVA, boss.getX(), boss.getY() + 0.5, boss.getZ(), 40, 3, 0.2, 3, 0.1);
                blast(level, boss, near, 7, 10, 1.5, null);
            }
            if (tick % 180 == 90) {                                                          // 쇳물 튀기기: 발밑에 불
                for (ServerPlayer p : near) {
                    if (p.isCreative()) continue;
                    p.igniteForSeconds(4);
                    BlockPos b = p.blockPosition();
                    for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                        BlockPos q = b.offset(dx, 0, dz);
                        if (level.getBlockState(q).isAir() && level.getBlockState(q.below()).isSolid()) level.setBlockAndUpdate(q, Blocks.FIRE.defaultBlockState());
                    }
                }
            }
        }

        @Override protected EntityType<?>[] minions() { return new EntityType<?>[]{EntityType.BLAZE, EntityType.BLAZE, EntityType.MAGMA_CUBE}; }

        @Override
        protected ItemStack[] loot() {
            return new ItemStack[]{new ItemStack(Items.NETHERITE_SCRAP, 2), new ItemStack(Items.IRON_BLOCK, 8), new ItemStack(Items.DIAMOND_PICKAXE),
                    new ItemStack(Items.ANVIL), new ItemStack(Items.EXPERIENCE_BOTTLE, 16)};
        }

        @Override protected SoundEvent roar() { return SoundEvents.IRON_GOLEM_HURT; }

        @Override protected String wakeText() { return "꺼지지 않는 용광로가 폭주한다... 쇳물로 빚은 " + name + "(이)가 일어섰다!"; }
    }

    // ───────────────────────── 6장 심해의 수호자 ─────────────────────────

    /** 등대 섬 앞바다 잠긴 성소 (예전의 한 번만 소환되던 엘더 가디언을 관리형으로) */
    public static final class DeepGuardian extends ManagedBoss {
        public static final int SX = PortCity.IX - 260, SZ = PortCity.IZ;

        public DeepGuardian() {
            super("deep", "심해의 수호자", "잠긴 성소", 6);
            triggerR = 13;
            leashR = 14;
            activeR = 30;
            color = BossEvent.BossBarColor.BLUE;
        }

        private static int floor() {
            return Math.min(KingdomTerrain.column(SX, SZ).h() - 2, PortCity.WATER_Y - 14 - 4);
        }

        @Override protected Vec3 center() { return new Vec3(SX + 0.5, floor() + 6, SZ + 0.5); }

        @Override protected boolean inY(double y, Vec3 c) { return y > c.y - 8 && y < c.y + 12; }

        @Override
        protected Mob create(ServerLevel level, BlockPos at) {
            Mob m = EntityType.ELDER_GUARDIAN.spawn(level, at, MobSpawnType.EVENT);
            if (m != null) stats(m, 1.4, 400, 10, 8);
            return m;
        }

        @Override
        protected void abilities(ServerLevel level, Mob boss, ServerPlayer target, List<ServerPlayer> near, float hp) {
            if (tick % 200 == 0) {                                                           // 소용돌이: 끌어당김
                level.playSound(null, boss.blockPosition(), SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.HOSTILE, 3F, 0.7F);
                level.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, boss.getX(), boss.getY(), boss.getZ(), 120, 6, 4, 6, 0.2);
                blast(level, boss, near, 14, 3, -1.4, eff(MobEffects.DIG_SLOWDOWN, 200, 2));
            }
            if (tick % 120 == 60) blast(level, boss, near, 6, 7, 1.2, null);                // 해일 파동
        }

        @Override protected EntityType<?>[] minions() { return new EntityType<?>[]{EntityType.GUARDIAN, EntityType.GUARDIAN, EntityType.GUARDIAN, EntityType.DROWNED}; }

        @Override
        protected ItemStack[] loot() {
            return new ItemStack[]{new ItemStack(Items.HEART_OF_THE_SEA), new ItemStack(Items.TRIDENT), new ItemStack(Items.PRISMARINE_SHARD, 24),
                    new ItemStack(Items.SPONGE, 4), new ItemStack(Items.EXPERIENCE_BOTTLE, 16)};
        }

        @Override protected SoundEvent roar() { return SoundEvents.ELDER_GUARDIAN_AMBIENT; }

        @Override protected String wakeText() { return "성소의 물이 소용돌이친다... " + name + "(이)가 눈을 떴다!"; }
    }

    // ───────────────────────── 8장 약탈 군주 그림발드 ─────────────────────────

    /** 국경도시 남쪽 들판. 다가가면 약탈단 습격 3단계 → 모두 막으면 파괴수를 탄 그림발드 등장 */
    public static final class Siege extends ManagedBoss {
        public static final int FX = BorderCity.VX, FZ = BorderCity.VZ + 175;
        private int wave;
        private final List<UUID> raiders = new ArrayList<>();

        public Siege() {
            super("grimbald", "약탈 군주 그림발드", "국경의 밤", 8);
            triggerR = 40;
            leashR = 40;
            activeR = 70;
            color = BossEvent.BossBarColor.RED;
        }

        @Override protected Vec3 center() { return new Vec3(FX + 0.5, KingdomTerrain.column(FX, FZ).h() + 1, FZ + 0.5); }

        @Override protected boolean inY(double y, Vec3 c) { return y > c.y - 15 && y < c.y + 40; }

        @Override
        protected boolean preFight(ServerLevel level, List<ServerPlayer> near, Vec3 c) {
            raiders.removeIf(u -> !(level.getEntity(u) instanceof LivingEntity e) || !e.isAlive());
            if (near.isEmpty()) {                                                            // 모두 떠나면 습격이 흩어짐
                if (wave > 0) {
                    for (UUID u : raiders) if (level.getEntity(u) instanceof Entity e) e.discard();
                    raiders.clear();
                    wave = 0;
                }
                return true;
            }
            if (wave == 0) {
                boolean trig = near.stream().anyMatch(p -> !p.isCreative() && p.position().distanceToSqr(c) < triggerR * triggerR);
                if (!trig) return true;
                wave = 1;
                spawnWave(level, c);
                return true;
            }
            if (!raiders.isEmpty()) return true;
            if (wave < 3) {
                wave++;
                spawnWave(level, c);
                return true;
            }
            return false;                                                                   // 3단계를 다 막음 → 그림발드 등장
        }

        private void spawnWave(ServerLevel level, Vec3 c) {
            EntityType<?>[] mobs = switch (wave) {
                case 1 -> new EntityType<?>[]{EntityType.PILLAGER, EntityType.PILLAGER, EntityType.PILLAGER, EntityType.PILLAGER, EntityType.VINDICATOR, EntityType.VINDICATOR};
                case 2 -> new EntityType<?>[]{EntityType.PILLAGER, EntityType.PILLAGER, EntityType.PILLAGER, EntityType.VINDICATOR, EntityType.VINDICATOR, EntityType.VINDICATOR, EntityType.RAVAGER};
                default -> new EntityType<?>[]{EntityType.PILLAGER, EntityType.PILLAGER, EntityType.VINDICATOR, EntityType.VINDICATOR, EntityType.EVOKER, EntityType.EVOKER, EntityType.RAVAGER, EntityType.WITCH};
            };
            say(level, BlockPos.containing(c), "§c§l[국경의 밤] 약탈단 습격 " + wave + "/3 단계! 남쪽 들판에서 몰려온다!");
            level.playSound(null, BlockPos.containing(c), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 64F, 1F);
            for (int i = 0; i < mobs.length; i++) {
                double a = Math.PI * 0.25 + Math.PI * 0.5 * i / Math.max(1, mobs.length - 1);         // 남쪽 반원에서
                int x = (int) Math.round(c.x + Math.cos(a) * 30), z = (int) Math.round(c.z + Math.sin(a) * 30);
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                var e = mobs[i].spawn(level, new BlockPos(x, y, z), MobSpawnType.EVENT);
                if (e instanceof Mob m) {
                    m.setPersistenceRequired();
                    m.addTag(minionTag);
                    raiders.add(m.getUUID());
                    ServerPlayer t = level.getNearestPlayer(m, 80) instanceof ServerPlayer sp ? sp : null;
                    if (t != null) m.setTarget(t);
                }
            }
        }

        @Override
        protected Mob create(ServerLevel level, BlockPos at) {
            BlockPos s = at.offset(0, 0, 24);
            Mob mount = EntityType.RAVAGER.spawn(level, s, MobSpawnType.EVENT);
            Mob m = EntityType.VINDICATOR.spawn(level, s, MobSpawnType.EVENT);
            if (m == null) return null;
            stats(m, 1.7, 320, 14, 12);
            m.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_AXE));
            if (mount != null) {
                stats(mount, 1.3, 160, 12, 8);
                mount.addTag(minionTag);
                mount.setPersistenceRequired();
                m.startRiding(mount, true);
            }
            return m;
        }

        @Override
        protected void abilities(ServerLevel level, Mob boss, ServerPlayer target, List<ServerPlayer> near, float hp) {
            if (tick % 160 == 0) {                                                           // 돌격 함성: 밀쳐냄
                level.playSound(null, boss.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 3F, 0.8F);
                blast(level, boss, near, 7, 6, 2.0, eff(MobEffects.WEAKNESS, 100, 0));
            }
        }

        @Override protected EntityType<?>[] minions() { return new EntityType<?>[]{EntityType.PILLAGER, EntityType.PILLAGER, EntityType.VINDICATOR, EntityType.EVOKER}; }

        @Override
        protected ItemStack[] loot() {
            return new ItemStack[]{new ItemStack(Items.TOTEM_OF_UNDYING), new ItemStack(Items.CROSSBOW), new ItemStack(Items.SADDLE),
                    new ItemStack(Items.EMERALD, 24), new ItemStack(Items.EXPERIENCE_BOTTLE, 16)};
        }

        @Override protected SoundEvent roar() { return SoundEvents.EVOKER_PREPARE_WOLOLO; }

        @Override protected boolean mayWake(ServerLevel level, List<ServerPlayer> trigger) { return true; }

        @Override protected String wakeText() { return "습격을 모두 막아냈다... 그러자 약탈단의 군주 " + name + "(이)가 직접 나섰다!"; }

        @Override
        protected void onDefeat(ServerLevel level, LivingEntity boss) {
            wave = 0;
            say(level, boss.blockPosition(), "§a[국경의 밤] 국경도시가 지켜졌다. 수비대가 환호한다!");
        }
    }

    // ───────────────────────── 10장 심연의 감시자 ─────────────────────────

    /** 심연 균열 바닥. 이름 붙은 워든 (스컬크 비명체 대신 다가가면 깨어남) */
    public static final class AbyssWatcher extends ManagedBoss {
        public AbyssWatcher() {
            super("watcher", "심연의 감시자", "심연 균열", 10);
            triggerR = 20;
            leashR = 34;
            activeR = 50;
            color = BossEvent.BossBarColor.BLUE;
        }

        @Override protected Vec3 center() { return new Vec3(CursedLands.RX + 0.5, -29, CursedLands.RZ + 0.5); }

        @Override protected boolean inY(double y, Vec3 c) { return y > c.y - 5 && y < c.y + 22; }

        @Override
        protected Mob create(ServerLevel level, BlockPos at) {
            return EntityType.WARDEN.spawn(level, at, MobSpawnType.TRIGGERED);
        }

        @Override
        protected void abilities(ServerLevel level, Mob boss, ServerPlayer target, List<ServerPlayer> near, float hp) {
            if (boss instanceof Warden w) w.increaseAngerAt(target, 80, false);
            if (tick % 200 == 0) for (ServerPlayer p : near) if (!p.isCreative()) p.addEffect(eff(MobEffects.DARKNESS, 160, 0));
        }

        @Override protected EntityType<?>[] minions() { return new EntityType<?>[0]; }

        @Override
        protected ItemStack[] loot() {
            return new ItemStack[]{new ItemStack(Items.ECHO_SHARD, 8), new ItemStack(Items.SCULK_CATALYST), new ItemStack(Items.MUSIC_DISC_5),
                    new ItemStack(Items.RECOVERY_COMPASS), new ItemStack(Items.EXPERIENCE_BOTTLE, 24)};
        }

        @Override protected SoundEvent roar() { return SoundEvents.WARDEN_EMERGE; }

        @Override protected String wakeText() { return "균열 바닥이 갈라진다... 마계문을 지키던 " + name + "(이)가 깨어났다!"; }
    }
}
