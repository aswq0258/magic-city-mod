package io.github.aswq0258.magiccity.site;

import io.github.aswq0258.magiccity.city.Royal;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * 왕성 하수도 초대 왕의 납골당 물 빼기 (2026-11-20).
 * 도시 건설의 물 패스는 "나중에 다른 블록으로 덮인 물"까지 놓아서, 하수로 물길 끝(납골당 입구 계단 위·입구 벽)에
 * 물 근원이 생겨 납골당 바닥으로 흘러내리고 양탄자를 쓸어 갔다. 이미 지은 월드를 그 자리에서 고친다.
 * 좌표는 왕국 대륙 왕성도시(중심 260, 260) 기준: 납골당 도면 u 244~277, v 242~280 → 월드 x 244~277, z 240~278.
 */
public final class CryptFix {
    private CryptFix() {}

    private static final int X0 = 243, X1 = 278, Z0 = 239, Z1 = 279;     // 납골당 벽 포함
    private static final int CX = 260;                                     // 가운데 x

    /** 바뀐 블록 수 */
    public static int run(ServerLevel k) {
        int F = SewerWorks.F, floor = F - 2, n = 0;
        int fl = Block.UPDATE_CLIENTS;                                     // 이웃 갱신 없이 (물이 다시 흐르지 않게 한 번에)
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = X0; x <= X1; x++) for (int z = Z0; z <= Z1; z++) for (int y = floor - 1; y <= floor + 13; y++) {
            m.set(x, y, z);
            BlockState s = k.getBlockState(m);
            if (s.is(Blocks.WATER)) {
                // 입구 벽 줄(z 279)의 물길 홈은 벽돌로, 나머지는 비움
                k.setBlock(m, z == Z1 && y == F ? Blocks.STONE_BRICKS.defaultBlockState() : Blocks.AIR.defaultBlockState(), fl);
                n++;
            } else if (s.hasProperty(BlockStateProperties.WATERLOGGED) && s.getValue(BlockStateProperties.WATERLOGGED)) {
                k.setBlock(m, s.setValue(BlockStateProperties.WATERLOGGED, false), fl);
                n++;
            }
        }
        // 가운데 양탄자 길 다시 깔기 (도면 v 244~263 → 월드 z 257~276, x 259~261). 빈 칸에만
        for (int x = CX - 1; x <= CX + 1; x++) for (int z = 520 - 263; z <= 520 - 244; z++) {
            m.set(x, floor + 1, z);
            if (k.getBlockState(m).isAir() && k.getBlockState(m.below()).isSolid()) {
                k.setBlock(m, Royal.RED_CARPET, fl);
                n++;
            }
        }
        // 다 고친 뒤 이웃 갱신 (물길 쪽 물이 다시 넘어오지 않는지는 위에서 벽돌로 막음)
        for (int x = X0; x <= X1; x++) for (int z = Z0; z <= Z1; z++) {
            m.set(x, F, z);
            k.blockUpdated(m, k.getBlockState(m).getBlock());
        }
        return n;
    }
}
