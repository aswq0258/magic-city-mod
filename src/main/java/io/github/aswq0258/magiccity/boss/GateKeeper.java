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
    }

    // ───────────────────────── 틱 ─────────────────────────

    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        for (ServerPlayer sp : pendingReturn) returnToKingdom(server, sp);
        pendingReturn.clear();
        ServerLevel level = server.getLevel(ModWorldgen.KINGDOM);
        if (level == null) return;
        if (++tick % 100 != 0) return;
        BlockPos center = new BlockPos(OldCapital.OX, OldCapital.Y + 1, OldCapital.OZ);
        if (!level.isLoaded(center) || !state(level).defeated) return;
        boolean near = !level.getPlayers(p -> p.distanceToSqr(Vec3.atCenterOf(center)) < 64 * 64 && !p.isSpectator()).isEmpty();
        if (near && !level.getBlockState(new BlockPos(OldCapital.OX, OldCapital.PORTAL_Y0 + 1, OldCapital.OZ)).is(Blocks.NETHER_PORTAL)) {
            setPortal(level, true);
        }
    }

    // ───────────────────────── 처치 · 포탈 ─────────────────────────

    /**
     * 심연의 문지기를 쓰러뜨림 (2026-11-19부터 전장에서, ClassicBosses.Gate): 기록 → 지역의 마계문을 열고 알림.
     */
    public static void markDefeated(MinecraftServer server) {
        ServerLevel level = server.getLevel(ModWorldgen.KINGDOM);
        if (level == null) return;
        State st = state(level);
        st.defeated = true;
        st.setDirty();
        setPortal(level, true);
        BlockPos c = new BlockPos(OldCapital.OX, OldCapital.PORTAL_Y0 + 6, OldCapital.OZ);
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null) {
            bolt.moveTo(c.getX() + 0.5, c.getY(), c.getZ() + 0.5);
            bolt.setVisualOnly(true);
            level.addFreshEntity(bolt);
        }
        level.playSound(null, c, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 3F, 0.8F);
        for (ServerPlayer p : server.getPlayerList().getPlayers())
            p.sendSystemMessage(Component.literal("§6[옛 왕도] 심연의 문지기가 쓰러졌다. 봉인된 마계문이 열려, 네더로 가는 길이 활성화되었다!"));
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
