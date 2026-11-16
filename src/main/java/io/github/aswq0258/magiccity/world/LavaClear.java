package io.github.aswq0258.magiccity.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 땅 위로 드러난 용암만 물로 바꿈 (지하 용암 웅덩이는 그대로).
 *  - 용암 호수: 하늘에서 내려다봐서 바위에 막히기 전까지의 용암 → 물 호수
 *  - 비탈 용암 샘: 옆이 트인(이웃 기둥의 지표보다 높은) 용암 → 물 샘
 * 용암 원천 → 물 원천, 흐르는 용암 → 공기.
 */
public final class LavaClear {
    private LavaClear() {}

    /** 호수 깊이 한계 (지표에서 이만큼 아래까지만) */
    private static final int LAKE_DEPTH = 10;

    /**
     * @param top   이 기둥의 지표 높이 (액체 포함, 맨 위 블록의 y)
     * @param nbMin 동서남북 이웃 기둥 지표 높이 중 가장 낮은 값
     * @return 바꾼 블록 수
     */
    public static int column(TreeClearer.Access a, int x, int z, int top, int nbMin) {
        int n = 0;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        // 1) 위에서 내려가며: 지표에 드러난 용암 호수
        for (int y = top + 1; y >= top - LAKE_DEPTH; y--) {
            m.set(x, y, z);
            BlockState s = a.get(m);
            if (s.is(Blocks.LAVA)) {
                n += swap(a, m, s);
            } else if (!s.isAir() && !s.canBeReplaced() && !s.is(Blocks.WATER)) {
                break;                                                                       // 바위·흙에 막힘 → 그 아래는 지하
            }
        }
        // 2) 비탈에 옆으로 뚫린 용암 샘 (이웃 기둥의 지표보다 높은 곳)
        for (int y = Math.max(nbMin - 1, top - 64); y <= top; y++) {
            m.set(x, y, z);
            BlockState s = a.get(m);
            if (!s.is(Blocks.LAVA)) continue;
            for (Direction d : Direction.Plane.HORIZONTAL) {
                if (!a.ok(x + d.getStepX(), z + d.getStepZ())) continue;
                BlockState o = a.get(m.relative(d));
                if (o.isAir() || o.is(Blocks.LAVA) && !o.getFluidState().isSource()) {
                    n += swap(a, m, s);
                    break;
                }
            }
        }
        return n;
    }

    private static int swap(TreeClearer.Access a, BlockPos p, BlockState s) {
        a.set(p.immutable(), s.getFluidState().isSource() ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState());
        return 1;
    }
}
