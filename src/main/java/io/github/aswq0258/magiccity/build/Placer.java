package io.github.aswq0258.magiccity.build;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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

    /**
     * 클라이언트에만 알리고, 이웃 블록의 모양 갱신은 막음(UPDATE_KNOWN_SHAPE) + 부서질 때 아이템도 안 떨어뜨림.
     * 이웃 갱신을 허용하면, 옆 청크를 지을 때 받침 블록이 잠깐 공기가 되는 순간 이미 놓인
     * 벽걸이 깃발·랜턴·양탄자·사다리 등이 "받침 없음"으로 부서져 버립니다.
     * 계단·울타리·판유리 연결은 청크를 다 지은 뒤 finishShapes() 가 한 번에 맞춥니다.
     */
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
    private static final BlockState LIGHT_BLOCK = Blocks.LIGHT.defaultBlockState().setValue(net.minecraft.world.level.block.LightBlock.LEVEL, 15);
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState FOUNDATION = Blocks.STONE.defaultBlockState();
    private static final BlockState FOUNDATION_TOP = Blocks.QUARTZ_BRICKS.defaultBlockState();

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

    /** fromY..toY 만 비움 (그 위의 나무 수관 같은 것은 남김) */
    public void clearBetween(int x, int z, int fromY, int toY) {
        if (pass != Pass.SOLID || !contains(x, z)) return;
        int top = Math.min(toY, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z));
        for (int y = fromY; y <= top && y <= maxY; y++) set(x, y, z, AIR);
    }

    /** fromY 부터 아래로 단단한 블록을 만날 때까지 채움 (위 6칸은 석영 벽돌, 그 아래는 돌) */
    public void foundation(int x, int z, int fromY, int maxDepth) {
        if (pass != Pass.SOLID || !contains(x, z)) return;
        for (int y = fromY; y > fromY - maxDepth && y >= minY; y--) {
            BlockState s = get(x, y, z);
            if (!s.isAir() && s.getFluidState().isEmpty() && !s.canBeReplaced()) return;
            set(x, y, z, fromY - y < 6 ? FOUNDATION_TOP : FOUNDATION);
        }
    }

    private static boolean needsShapeUpdate(BlockState state) {
        Block b = state.getBlock();
        return b instanceof StairBlock || b instanceof IronBarsBlock || b instanceof FenceBlock || b instanceof WallBlock;
    }

    /** 이 기둥의 원래 지면 높이 (나뭇잎·물 제외, 꼭대기 블록의 y) */
    public int naturalTop(int x, int z) {
        int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR, x, z) - 1;
        // 통나무·나뭇잎은 지면이 아님
        while (y > minY && (level.getBlockState(cursor.set(x, y, z)).is(net.minecraft.tags.BlockTags.LOGS) || level.getBlockState(cursor).is(net.minecraft.tags.BlockTags.LEAVES))) y--;
        return y;
    }

    /**
     * 월드 생성기가 처음 만든 지면 높이 (지금 월드에 무엇이 지어져 있든 같은 값).
     * 같은 건설을 여러 번 다시 돌려도 가장자리 경사가 매번 깎여 절벽이 되지 않게 함.
     */
    public int baseTop(int x, int z) {
        var src = level.getChunkSource();
        return src.getGenerator().getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, src.randomState()) - 1;
    }

    /** 청크를 다 지은 뒤: 계단·울타리·판유리·담장의 연결 모양을 이웃에 맞춤 (청크 둘레 1칸 포함) */
    public void finishShapes(int y0, int y1) {
        if (pass != Pass.SOLID) return;
        y0 = Math.max(y0, minY);
        y1 = Math.min(y1, maxY);
        for (int x = minX - 1; x <= maxX + 1; x++) {
            for (int z = minZ - 1; z <= maxZ + 1; z++) {
                for (int y = y0; y <= y1; y++) {
                    cursor.set(x, y, z);
                    BlockState s = level.getBlockState(cursor);
                    if (s.isAir() || !needsShapeUpdate(s)) continue;
                    BlockState n = Block.updateFromNeighbourShapes(s, level, cursor);
                    if (n != s) level.setBlock(cursor, n, FLAGS);
                }
            }
        }
    }

    public interface LightRange {
        /** 이 기둥에서 실내 조명을 깔 높이 범위 {y0, y1}, 없으면 null */
        int[] range(int x, int z);
    }

    /**
     * 실내 몬스터 방지 조명: 지붕 아래의 "몬스터가 설 수 있는 바닥 칸"마다,
     * 같은 방(벽으로 막히지 않은) 3칸 안에 조명이 없으면 보이지 않는 빛 블록(밝기 15)을 놓음.
     * 빛 블록은 투명하고 통과할 수 있어 보이지 않으며, 장식용 랜턴·촛불과 함께 방 전체를 밝힙니다.
     */
    public int lightInteriors(LightRange ranges) {
        if (pass != Pass.SOLID) return 0;
        java.util.List<int[]> lights = new java.util.ArrayList<>();
        BlockPos.MutableBlockPos below = new BlockPos.MutableBlockPos();
        int placed = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                int[] r = ranges.range(x, z);
                if (r == null) continue;
                int y0 = Math.max(r[0], minY + 1), y1 = Math.min(r[1], maxY - 2);
                for (int y = y0; y <= y1; y++) {
                    cursor.set(x, y, z);
                    if (!level.getBlockState(cursor).isAir()) continue;
                    below.set(x, y - 1, z);
                    BlockState floor = level.getBlockState(below);
                    if (!floor.isFaceSturdy(level, below, Direction.UP) || !floor.getFluidState().isEmpty()) continue;
                    if (!passable(x, y + 1, z)) continue;
                    if (!hasRoof(x, y + 2, z, 48)) continue;
                    if (covered(lights, x, y, z)) continue;
                    level.setBlock(cursor.set(x, y, z), LIGHT_BLOCK, FLAGS);
                    lights.add(new int[]{x, y, z});
                    placed++;
                }
            }
        }
        return placed;
    }

    private boolean passable(int x, int y, int z) {
        BlockState s = level.getBlockState(cursor.set(x, y, z));
        return s.isAir() || (s.getCollisionShape(level, cursor).isEmpty() && s.getFluidState().isEmpty());
    }

    private boolean solidAt(int x, int y, int z) {
        BlockState s = level.getBlockState(cursor.set(x, y, z));
        return !s.isAir() && !s.getCollisionShape(level, cursor).isEmpty() && !s.is(Blocks.LIGHT);
    }

    /** 위쪽에 건물 지붕이 있는지 (나뭇잎·통나무·흙·돌·잔디 같은 자연 블록은 지붕으로 치지 않음) */
    private boolean hasRoof(int x, int y, int z, int max) {
        for (int k = 0; k < max && y + k <= maxY; k++) {
            BlockState s = level.getBlockState(cursor.set(x, y + k, z));
            if (s.isAir() || s.is(Blocks.LIGHT)) continue;
            if (s.is(net.minecraft.tags.BlockTags.LEAVES) || s.is(net.minecraft.tags.BlockTags.LOGS)
                    || s.is(Blocks.STONE) || s.is(Blocks.DIRT) || s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.MOSSY_COBBLESTONE)
                    || s.is(Blocks.COBBLESTONE) || s.is(Blocks.AMETHYST_CLUSTER) || s.is(Blocks.END_ROD) || s.is(Blocks.SNOW_BLOCK)) return false;
            return true;
        }
        return false;
    }

    /** 3칸 안에, 벽에 막히지 않은 조명이 있는지 (x 먼저, 그다음 z 로 가는 길이 트였는지 확인) */
    private boolean covered(java.util.List<int[]> lights, int x, int y, int z) {
        for (int i = lights.size() - 1; i >= 0; i--) {
            int[] l = lights.get(i);
            if (Math.abs(l[1] - y) > 1 || Math.abs(l[0] - x) > 3 || Math.abs(l[2] - z) > 3) continue;
            if (clearPath(l[0], l[2], x, z, y) || clearPath(l[0], l[2], x, z, y + 1)) return true;
        }
        return false;
    }

    private boolean clearPath(int x0, int z0, int x1, int z1, int y) {
        int sx = Integer.signum(x1 - x0), sz = Integer.signum(z1 - z0);
        int x = x0, z = z0;
        while (x != x1) {
            x += sx;
            if (solidAt(x, y, z)) return false;
        }
        while (z != z1) {
            z += sz;
            if (solidAt(x, y, z)) return false;
        }
        return true;
    }
}
