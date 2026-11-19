package io.github.aswq0258.magiccity.arena;

import io.github.aswq0258.magiccity.boss.ManagedBoss;
import io.github.aswq0258.magiccity.world.ModWorldgen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * 봉인 제단 (2026-11-19): 챕터 보스가 깨어나던 지역 자리에 놓이는 상징 구조물. 3×3 흑암 단 + 가운데 자석석 + 네 귀퉁이 영혼 등불.
 * 플레이어가 그 자리 32칸 안에 오면 코드가 놓고(기존 월드도 자동), 자석석을 오른쪽 클릭하면 전장 입장 제안.
 */
public final class SealAltar {
    private SealAltar() {}

    /** 장 → 자석석 자리 (지면을 찾은 뒤 기억) */
    private static final Map<Integer, BlockPos> CORE = new HashMap<>();

    /** 그 장 제단의 자석석 자리 (아직 모르면 null) */
    public static BlockPos core(int chapter) {
        return CORE.get(chapter);
    }

    /** 2초마다: 근처에 사람이 있는 제단 자리에 제단이 없으면 놓음 */
    public static void tick(MinecraftServer server) {
        ServerLevel k = server.getLevel(ModWorldgen.KINGDOM);
        if (k == null || k.players().isEmpty()) return;
        for (ManagedBoss b : ManagedBoss.ALL) {
            Vec3 c = b.altarCenter();
            boolean near = false;
            for (ServerPlayer p : k.players()) if (p.position().distanceToSqr(c) < 32 * 32) near = true;
            if (!near || !k.isLoaded(BlockPos.containing(c))) continue;
            BlockPos core = CORE.computeIfAbsent(b.chapter(), ch -> findCore(k, BlockPos.containing(c)));
            if (!k.getBlockState(core).is(Blocks.LODESTONE)) place(k, core);
        }
    }

    /** 보스 자리에서 아래로 내려가 첫 단단한 블록 위(이미 제단이 있으면 그 자석석)를 찾음 */
    private static BlockPos findCore(ServerLevel k, BlockPos from) {
        BlockPos.MutableBlockPos m = from.mutable();
        for (int i = 0; i < 16; i++) {
            BlockState s = k.getBlockState(m);
            if (s.is(Blocks.LODESTONE)) return m.immutable();
            if (!s.isAir() && !s.getFluidState().isSource() && s.isSolid()) return m.above().immutable();
            m.move(0, -1, 0);
        }
        return from;
    }

    private static void place(ServerLevel k, BlockPos core) {
        int fl = Block.UPDATE_ALL;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            BlockPos b = core.offset(dx, -1, dz);
            k.setBlock(b, dx == 0 && dz == 0 ? Blocks.GILDED_BLACKSTONE.defaultBlockState() : Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), fl);
            if (dx != 0 && dz != 0) k.setBlock(core.offset(dx, 0, dz), Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false), fl);
        }
        k.setBlock(core, Blocks.LODESTONE.defaultBlockState(), fl);
        for (int y = 1; y <= 2; y++) if (k.getBlockState(core.above(y)).isSolid()) k.setBlock(core.above(y), Blocks.AIR.defaultBlockState(), fl);   // 서서 누를 자리
    }

    /** 제단 자석석 오른쪽 클릭: 나침반 연결 대신 입장 제안 */
    public static void onUse(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p.level().dimension() != ModWorldgen.KINGDOM) return;
        if (!event.getLevel().getBlockState(event.getPos()).is(Blocks.LODESTONE)) return;
        for (var e : CORE.entrySet()) {
            if (!e.getValue().equals(event.getPos())) continue;
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            if (event.getHand() == InteractionHand.MAIN_HAND) ArenaManager.propose(p, e.getKey());
            return;
        }
    }

    /** /magiccity arena altars: 제단 자리를 모두 다시 찾게 함 */
    public static void forget() {
        CORE.clear();
    }
}
