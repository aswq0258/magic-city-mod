package io.github.aswq0258.magiccity.city.feature;

import io.github.aswq0258.magiccity.build.Placer;
import io.github.aswq0258.magiccity.city.CityLayout;
import io.github.aswq0258.magiccity.city.Deco;
import io.github.aswq0258.magiccity.city.Shapes;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import static io.github.aswq0258.magiccity.city.Palette.*;

/** 병영 훈련장: 흙 바닥, 울타리, 활쏘기 과녁, 허수아비, 무기 손질대, 관람 의자, 가로등 */
public class TrainingGround extends Feature {
    private final int mx, mz;
    private final long seed;

    public TrainingGround(int x0, int z0, int x1, int z1, long seed) {
        super(x0, z0, x1, z1);
        this.mx = (x0 + x1) >> 1;
        this.mz = (z0 + z1) >> 1;
        this.seed = seed;
    }

    @Override
    public void build(Placer p, CityLayout city) {
        final int b = groundY;
        p.forColumns(minX, minZ, maxX, maxZ, (x, z) -> {
            boolean border = x == minX || x == maxX || z == minZ || z == maxZ;
            boolean cross = Math.abs(x - mx) <= 1 || Math.abs(z - mz) <= 1;
            if (border) {
                p.set(x, b, z, SMOOTH_QUARTZ);
                if (!cross) p.set(x, b + 1, z, Deco.FENCE);
            } else {
                p.set(x, b, z, cross ? CALCITE : Blocks.COARSE_DIRT.defaultBlockState());
            }
        });

        BlockState hay = Blocks.HAY_BLOCK.defaultBlockState();
        // 활쏘기 과녁 (북쪽 줄)
        for (int x = minX + 3; x <= maxX - 3; x += 5) {
            if (Math.abs(x - mx) <= 2) continue;
            p.set(x, b + 1, minZ + 2, hay);
            p.set(x, b + 2, minZ + 2, Blocks.TARGET.defaultBlockState());
        }
        // 허수아비 (남쪽 절반)
        BlockState pumpkin = Blocks.CARVED_PUMPKIN.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
        for (int x = minX + 5; x <= maxX - 5; x += 6) {
            for (int z = mz + 5; z <= maxZ - 4; z += 6) {
                if (Math.abs(x - mx) <= 2) continue;
                p.set(x, b + 1, z, Deco.FENCE);
                p.set(x, b + 2, z, hay);
                p.set(x, b + 3, z, pumpkin);
            }
        }
        // 무기 손질대 (동쪽) + 관람 의자 (서쪽)
        BlockState[] rack = {Blocks.GRINDSTONE.defaultBlockState(), Blocks.SMITHING_TABLE.defaultBlockState(), Deco.BARREL, Deco.chest(Direction.WEST)};
        int i = 0;
        for (int z = minZ + 4; z <= maxZ - 4; z += 3) {
            if (Math.abs(z - mz) <= 2) continue;
            p.set(maxX - 2, b + 1, z, rack[i++ % rack.length]);
            p.set(minX + 2, b + 1, z, Deco.chair(Direction.WEST));
        }
        // 가로등
        int[][] lamps = {{minX + 2, minZ + 2}, {maxX - 2, minZ + 2}, {minX + 2, maxZ - 2}, {maxX - 2, maxZ - 2},
                {mx + 3, mz + 3}, {mx - 3, mz - 3}, {mx + 3, minZ + 8}, {mx - 3, maxZ - 8}};
        for (int[] l : lamps) Shapes.lampPost(p, l[0], l[1], b);
    }
}
