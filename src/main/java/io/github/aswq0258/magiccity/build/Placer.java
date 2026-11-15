package io.github.aswq0258.magiccity.build;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * 한 청크 안에서만 블록을 놓는 도구.
 *
 * 도시는 청크 단위로 두 번 지어집니다.
 *  - SOLID 패스: 물을 제외한 모든 블록 (물이 들어갈 자리는 공기로)
 *  - WATER 패스: 물만 채움
 * 물을 마지막에 채우기 때문에, 아직 안 지어진 옆 청크로 물이 새어 나가지 않습니다.
 */
public final class Placer {
    public enum Pass { SOLID, WATER }

    @FunctionalInterface
    public interface ColumnOp {
        void apply(int x, int z);
    }

    private static final int FLAGS = Block.UPDATE_CLIENTS;
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState FOUNDATION = Blocks.STONE.defaultBlockState();

    private final ServerLevel level;
    private final Pass pass;
    private final int minX, minZ, maxX, maxZ, minY, maxY;
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    private boolean sawWater = false;

    public Placer(ServerLevel level, ChunkPos chunk, Pass pass) {
        this.level = level;
        this.pass = pass;
        this.minX = chunk.getMinBlockX();
        this.maxX = chunk.getMaxBlockX();
        this.minZ = chunk.getMinBlockZ();
        this.maxZ = chunk.getMaxBlockZ();
        this.minY = level.getMinBuildHeight();
        this.maxY = level.getMaxBuildHeight() - 1;
    }

    /** SOLID 패스 중 물 자리를 만났는지 (WATER 패스에서 다시 방문할 청크) */
    public boolean sawWater() {
        return sawWater;
    }

    public Pass pass() {
        return pass;
    }

    public ServerLevel level() {
        return level;
    }

    public int minX() { return minX; }
    public int maxX() { return maxX; }
    public int minZ() { return minZ; }
    public int maxZ() { return maxZ; }

    /** 사각 영역이 이 청크와 겹치는지 */
    public boolean intersects(int x0, int z0, int x1, int z1) {
        return x0 <= maxX && x1 >= minX && z0 <= maxZ && z1 >= minZ;
    }

    public boolean contains(int x, int z) {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    /** 사각 영역 중 이 청크와 겹치는 기둥(x,z)만 순회 */
    public void forColumns(int x0, int z0, int x1, int z1, ColumnOp op) {
        int ax0 = Math.max(x0, minX), ax1 = Math.min(x1, maxX);
        int az0 = Math.max(z0, minZ), az1 = Math.min(z1, maxZ);
        for (int x = ax0; x <= ax1; x++) {
            for (int z = az0; z <= az1; z++) {
                op.apply(x, z);
            }
        }
    }

    public BlockState get(int x, int y, int z) {
        return level.getBlockState(cursor.set(x, y, z));
    }

    public void set(int x, int y, int z, BlockState state) {
        if (!contains(x, z) || y < minY || y > maxY) return;
        boolean water = state.is(Blocks.WATER);
        if (pass == Pass.SOLID) {
            if (water) {
                state = AIR;
                sawWater = true;
            }
        } else if (!water) {
            return;
        }
        cursor.set(x, y, z);
        if (needsShapeUpdate(state)) {
            state = Block.updateFromNeighbourShapes(state, level, cursor);
        }
        if (level.getBlockState(cursor) == state) return;
        level.setBlock(cursor, state, FLAGS);
    }

    /** fromY 부터 지형 꼭대기까지 비우기 (나무, 언덕 제거) */
    public void clearAbove(int x, int z, int fromY) {
        if (pass != Pass.SOLID || !contains(x, z)) return;
        int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
        for (int y = fromY; y < top && y <= maxY; y++) {
            set(x, y, z, AIR);
        }
    }

    /** fromY 부터 아래로 단단한 블록을 만날 때까지 돌로 채움 (공중에 뜨지 않게) */
    public void foundation(int x, int z, int fromY, int maxDepth) {
        if (pass != Pass.SOLID || !contains(x, z)) return;
        for (int y = fromY; y > fromY - maxDepth && y >= minY; y--) {
            BlockState s = get(x, y, z);
            if (!s.isAir() && s.getFluidState().isEmpty() && !s.canBeReplaced()) return;
            set(x, y, z, FOUNDATION);
        }
    }

    private static boolean needsShapeUpdate(BlockState state) {
        Block b = state.getBlock();
        return b instanceof StairBlock || b instanceof IronBarsBlock || b instanceof FenceBlock || b instanceof WallBlock;
    }
}
