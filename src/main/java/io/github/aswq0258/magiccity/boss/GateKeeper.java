package io.github.aswq0258.magiccity.boss;

import io.github.aswq0258.magiccity.site.OldCapital;
import io.github.aswq0258.magiccity.world.ModWorldgen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.WitherSkeleton;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 필드보스 "심연의 문지기" — 몰락한 옛 왕도의 봉인된 마계문을 지키는 거대한 위더 해골.
 *
 *  - 포탈 광장(반지름 24) 에 플레이어가 들어오면 등장 (쓰러뜨리기 전까지)
 *  - 체력 600, 3배 크기, 네더라이트 검·투구, 상단 보스 체력바
 *  - 1페이즈: 내려찍기 (주변 넉백·피해)
 *  - 2페이즈(66%↓): 블레이즈·위더 해골 소환, 화염구 연사
 *  - 3페이즈(33%↓): 화염구 고리, 신속·힘, 번개
 *  - 광장에서 멀어지면 제자리로 순간이동, 모두 떠나면 1분 뒤 사라짐(재도전 가능)
 *  - 쓰러뜨리면 마계문(네더 포탈) 이 열리고 저장됨. 이후엔 다시 나오지 않음.
 *  - 왕국 대륙에서 네더로 간 플레이어가 네더 포탈로 돌아오면 오버월드 대신 옛 왕도 포탈 앞으로 돌아옴.
 */
public final class GateKeeper {
    private GateKeeper() {}

    private static final String TAG = "magiccity_gatekeeper", TRAVELER = "magiccity_kingdom_traveler";
    private static final int ARENA_R = 24, LEASH_R = 34;
    private static UUID bossId;
    private static ServerBossEvent bar;
    private static int tick, emptyTicks, lastSummon;
    private static boolean phase2Announced, phase3Announced;
    private static final List<ServerPlayer> pendingReturn = new ArrayList<>();

    // ───────────────────────── 저장 ─────────────────────────

    public static final class State extends SavedData {
        boolean defeated;

        static State load(CompoundTag tag, HolderLookup.Provider p) {
            State s = new State();
            s.defeated = tag.getBoolean("defeated");
            return s;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider p) {
            tag.putBoolean("defeated", defeated);
            return tag;
        }
    }

    private static State state(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(State::new, State::load, null), "magiccity_gatekeeper");
    }

    public static boolean defeated(MinecraftServer server) {
        ServerLevel k = server.getLevel(ModWorldgen.KINGDOM);
        return k != null && state(k).defeated;
    }

    /** /magiccity boss reset 용: 다시 싸울 수 있게 (포탈도 닫힘) */
    public static void reset(MinecraftServer server) {
        ServerLevel k = server.getLevel(ModWorldgen.KINGDOM);
        if (k == null) return;
        state(k).defeated = false;
        state(k).setDirty();
        setPortal(k, false);
        Entity e = bossId == null ? null : k.getEntity(bossId);
        if (e != null) e.discard();
        clearBar();
    }

    // ───────────────────────── 틱 ─────────────────────────

    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        for (ServerPlayer sp : pendingReturn) returnToKingdom(server, sp);
        pendingReturn.clear();
        ServerLevel level = server.getLevel(ModWorldgen.KINGDOM);
        if (level == null) return;
        if (++tick % 5 != 0) return;
        BlockPos center = new BlockPos(OldCapital.OX, OldCapital.Y + 1, OldCapital.OZ);
        if (!level.isLoaded(center)) return;
        List<ServerPlayer> near = level.getPlayers(p -> p.distanceToSqr(Vec3.atCenterOf(center)) < 64 * 64 && !p.isSpectator());
        State st = state(level);
        if (st.defeated) {
            if (tick % 100 == 0 && !near.isEmpty() && !level.getBlockState(new BlockPos(OldCapital.OX, OldCapital.PORTAL_Y0 + 1, OldCapital.OZ)).is(Blocks.NETHER_PORTAL)) {
                setPortal(level, true);
            }
            return;
        }
        WitherSkeleton boss = bossId == null ? null : (level.getEntity(bossId) instanceof WitherSkeleton w && w.isAlive() ? w : null);
        if (boss == null) {                                                   // 서버를 다시 켠 경우: 남아 있던 보스를 다시 찾음
            List<WitherSkeleton> found = level.getEntitiesOfClass(WitherSkeleton.class, new AABB(center).inflate(64), w -> w.getTags().contains(TAG) && w.isAlive());
            if (!found.isEmpty()) {
                boss = found.get(0);
                bossId = boss.getUUID();
                if (bar == null) bar = new ServerBossEvent(Component.literal("심연의 문지기"), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
            }
        }
        if (boss == null) {
            clearBar();
            bossId = null;
            boolean inArena = near.stream().anyMatch(p -> p.distanceToSqr(Vec3.atCenterOf(center)) < ARENA_R * ARENA_R && !p.isCreative());
            if (inArena) spawnBoss(level, center);
            return;
        }
        // 체력바
        bar.setProgress(boss.getHealth() / boss.getMaxHealth());
        for (ServerPlayer p : near) bar.addPlayer(p);
        for (ServerPlayer p : new ArrayList<>(bar.getPlayers())) if (!near.contains(p)) bar.removePlayer(p);
        // 아무도 없으면 1분 뒤 사라짐
        if (near.isEmpty()) {
            if ((emptyTicks += 5) > 1200) {
                boss.discard();
                clearBar();
            }
            return;
        }
        emptyTicks = 0;
        // 광장 밖으로 끌려가면 제자리로
        if (boss.distanceToSqr(Vec3.atCenterOf(center)) > LEASH_R * LEASH_R) {
            boss.teleportTo(center.getX() + 0.5, center.getY() + 1, center.getZ() - 8.5);
        }
        abilities(level, boss, near);
    }

    private static void spawnBoss(ServerLevel level, BlockPos center) {
        WitherSkeleton w = EntityType.WITHER_SKELETON.spawn(level, center.offset(0, 1, -8), MobSpawnType.EVENT);
        if (w == null) return;
        w.addTag(TAG);
        w.setCustomName(Component.literal("§4§l심연의 문지기"));
        w.setCustomNameVisible(true);
        w.setPersistenceRequired();
        w.getAttribute(Attributes.SCALE).setBaseValue(3.0);
        w.getAttribute(Attributes.MAX_HEALTH).setBaseValue(600);
        w.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(16);
        w.getAttribute(Attributes.ARMOR).setBaseValue(14);
        w.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
        w.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.3);
        w.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(48);
        w.setHealth(w.getMaxHealth());
        w.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_SWORD));
        w.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.NETHERITE_HELMET));
        for (EquipmentSlot s : EquipmentSlot.values()) w.setDropChance(s, 0F);
        bossId = w.getUUID();
        bar = new ServerBossEvent(Component.literal("심연의 문지기"), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
        bar.setDarkenScreen(true);
        phase2Announced = phase3Announced = false;
        lastSummon = tick;
        level.playSound(null, center, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 2F, 0.7F);
        broadcast(level, "§4[옛 왕도] 봉인된 마계문 앞에 심연의 문지기가 깨어났다!");
    }

    private static void abilities(ServerLevel level, WitherSkeleton boss, List<ServerPlayer> near) {
        float hp = boss.getHealth() / boss.getMaxHealth();
        if (hp <= 0.66F && !phase2Announced) {
            phase2Announced = true;
            broadcast(level, "§c[옛 왕도] 문지기가 마계의 졸개를 부른다!");
            summon(level, boss);
        }
        if (hp <= 0.33F && !phase3Announced) {
            phase3Announced = true;
            broadcast(level, "§4[옛 왕도] 마계문의 불길이 문지기를 감싼다!");
            boss.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20 * 600, 1));
            boss.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 20 * 600, 0));
            boss.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 20 * 600, 0));
        }
        // 1페이즈부터: 6초마다 내려찍기
        if (tick % 120 == 0) {
            level.playSound(null, boss.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.2F, 0.6F);
            for (ServerPlayer p : near) {
                if (p.distanceToSqr(boss) < 7 * 7) {
                    p.hurt(level.damageSources().mobAttack(boss), 8F);
                    Vec3 push = p.position().subtract(boss.position()).normalize().scale(1.4);
                    p.push(push.x, 0.7, push.z);
                    p.hurtMarked = true;
                }
            }
        }
        // 2페이즈: 화염구 연사, 30초마다 졸개
        if (hp <= 0.66F) {
            if (tick % 40 == 0) {
                for (ServerPlayer p : near) {
                    if (p.distanceToSqr(boss) > 40 * 40 || p.isCreative()) continue;
                    Vec3 from = boss.position().add(0, boss.getBbHeight() * 0.8, 0);
                    Vec3 dir = p.position().add(0, 1, 0).subtract(from).normalize();
                    SmallFireball fb = new SmallFireball(level, boss, dir);
                    fb.setPos(from.x, from.y, from.z);
                    level.addFreshEntity(fb);
                }
            }
            if (tick - lastSummon > 600) {
                lastSummon = tick;
                summon(level, boss);
            }
        }
        // 3페이즈: 화염구 고리 + 번개
        if (hp <= 0.33F && tick % 100 == 0) {
            Vec3 from = boss.position().add(0, boss.getBbHeight() * 0.6, 0);
            for (int i = 0; i < 12; i++) {
                double a = Math.PI * 2 * i / 12;
                SmallFireball fb = new SmallFireball(level, boss, new Vec3(Math.cos(a), -0.05, Math.sin(a)));
                fb.setPos(from.x, from.y, from.z);
                level.addFreshEntity(fb);
            }
            ServerPlayer t = near.get(level.random.nextInt(near.size()));
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
            if (bolt != null) {
                bolt.moveTo(t.getX(), t.getY(), t.getZ());
                bolt.setVisualOnly(false);
                level.addFreshEntity(bolt);
            }
        }
    }

    private static void summon(ServerLevel level, WitherSkeleton boss) {
        BlockPos at = boss.blockPosition();
        long count = level.getEntitiesOfClass(Monster.class, new AABB(at).inflate(30), m -> m.getTags().contains("magiccity_minion")).size();
        if (count >= 8) return;
        for (int i = 0; i < 4; i++) {
            EntityType<? extends Monster> type = i % 2 == 0 ? EntityType.BLAZE : EntityType.WITHER_SKELETON;
            BlockPos pos = at.offset(level.random.nextInt(11) - 5, 1, level.random.nextInt(11) - 5);
            Monster m = type.spawn(level, pos, MobSpawnType.MOB_SUMMONED);
            if (m != null) m.addTag("magiccity_minion");
        }
    }

    // ───────────────────────── 처치 · 포탈 ─────────────────────────

    public static void onDeath(LivingDeathEvent event) {
        LivingEntity e = event.getEntity();
        if (!(e.level() instanceof ServerLevel level) || !e.getTags().contains(TAG)) return;
        State st = state(level);
        st.defeated = true;
        st.setDirty();
        clearBar();
        bossId = null;
        setPortal(level, true);
        BlockPos c = new BlockPos(OldCapital.OX, OldCapital.PORTAL_Y0 + 6, OldCapital.OZ);
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null) {
            bolt.moveTo(c.getX() + 0.5, c.getY(), c.getZ() + 0.5);
            bolt.setVisualOnly(true);
            level.addFreshEntity(bolt);
        }
        level.playSound(null, c, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 3F, 0.8F);
        ItemStack[] loot = {new ItemStack(Items.NETHERITE_INGOT, 2), new ItemStack(Items.DIAMOND, 8),
                new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 1), new ItemStack(Items.WITHER_SKELETON_SKULL, 1), new ItemStack(Items.EXPERIENCE_BOTTLE, 32)};
        for (ItemStack s : loot) level.addFreshEntity(new ItemEntity(level, e.getX(), e.getY() + 1, e.getZ(), s));
        broadcast(level, "§6[옛 왕도] 심연의 문지기가 쓰러졌다. 봉인된 마계문이 열려, 네더로 가는 길이 활성화되었다!");
    }

    /** 포탈 틀 안을 네더 포탈로 채우거나 비움 */
    static void setPortal(ServerLevel level, boolean open) {
        var portal = Blocks.NETHER_PORTAL.defaultBlockState().setValue(NetherPortalBlock.AXIS, Direction.Axis.X);
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
        for (int x = OldCapital.OX - OldCapital.PORTAL_W / 2; x <= OldCapital.OX + OldCapital.PORTAL_W / 2; x++) {
            for (int y = OldCapital.PORTAL_Y0 + 1; y <= OldCapital.PORTAL_Y0 + OldCapital.PORTAL_H; y++) {
                level.setBlock(new BlockPos(x, y, OldCapital.OZ), open ? portal : Blocks.AIR.defaultBlockState(), flags);
            }
        }
    }

    // ───────────────────────── 왕국 ↔ 네더 왕복 ─────────────────────────

    public static void onTravel(EntityTravelToDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        var from = sp.level().dimension();
        if (from == ModWorldgen.KINGDOM && event.getDimension() == Level.NETHER) {
            sp.addTag(TRAVELER);
        } else if (from == Level.NETHER && event.getDimension() == Level.OVERWORLD && sp.getTags().contains(TRAVELER)) {
            event.setCanceled(true);
            if (!pendingReturn.contains(sp)) pendingReturn.add(sp);
        }
    }

    private static void returnToKingdom(MinecraftServer server, ServerPlayer sp) {
        ServerLevel k = server.getLevel(ModWorldgen.KINGDOM);
        if (k == null) return;
        sp.removeTag(TRAVELER);
        sp.teleportTo(k, OldCapital.OX + 0.5, OldCapital.Y + 4, OldCapital.OZ - 6.5, 180F, 0F);
    }

    private static void clearBar() {
        if (bar != null) bar.removeAllPlayers();
        bar = null;
    }

    private static void broadcast(ServerLevel level, String msg) {
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(OldCapital.OX, OldCapital.Y, OldCapital.OZ) < 200 * 200) p.sendSystemMessage(Component.literal(msg));
        }
    }
}
