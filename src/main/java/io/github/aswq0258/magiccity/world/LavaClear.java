package io.github.aswq0258.magiccity.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** 지표 가까이(Y 50 위)의 용암 호수·용암 샘을 물로 바꿈: 용암 원천 → 물 원천, 흐르는 용암 → 공기 */
public final class LavaClear {
    private LavaClear() {}

    public static final int MIN_Y = 50;

    /** @return 바꾼 블록 수 */
    public static int column(TreeClearer.Access a, int x, int z, int top) {
        int n = 0;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int y = MIN_Y; y <= top; y++) {
            m.set(x, y, z);
            BlockState s = a.get(m);
            if (!s.is(Blocks.LAVA)) continue;
            a.set(m.immutable(), s.getFluidState().isSource() ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState());
            n++;
        }
        return n;
    }
}
