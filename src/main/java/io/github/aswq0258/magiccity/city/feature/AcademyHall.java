package io.github.aswq0258.magiccity.city.feature;

import io.github.aswq0258.magiccity.build.Placer;
import io.github.aswq0258.magiccity.city.CityLayout;
import io.github.aswq0258.magiccity.city.Shapes;
import net.minecraft.util.RandomSource;

import static io.github.aswq0258.magiccity.city.Palette.*;

/** 마법 학원 건물: 큰 2~3층 건물 + 네 모서리에 민트 지붕의 작은 원형 첨탑 */
public class AcademyHall extends House {
    public AcademyHall(CityLayout city, CityLayout.Lot lot, RandomSource rand) {
        super(lot, new Frame(lot.centerX(), lot.centerZ(), lot.front(),
                        15 + 2 * rand.nextInt(2), 13 + 2 * rand.nextInt(2)),
                2 + rand.nextInt(2), 5, QUARTZ_BRICKS, LECTERN_STATE, false, 4);
    }

    private static final net.minecraft.world.level.block.state.BlockState LECTERN_STATE =
            net.minecraft.world.level.block.Blocks.LECTERN.defaultBlockState();

    @Override
    public void build(Placer p, CityLayout city) {
        super.build(p, city);
        int b = city.base;
        int turretH = floors * floorH + 3;
        int[][] corners = {{f.minX, f.minZ}, {f.maxX, f.minZ}, {f.minX, f.maxZ}, {f.maxX, f.maxZ}};
        for (int[] c : corners) {
            Shapes.roundTower(p, c[0], c[1], 2.0, b, turretH, null);
        }
        // 1층 서가
        for (int u = 4; u < f.width - 2; u += 2) {
            int v = f.depth - 2;
            p.set(f.x(u, v), b + 1, f.z(u, v), BOOKSHELF);
            p.set(f.x(u, v), b + 2, f.z(u, v), BOOKSHELF);
        }
    }

    @Override
    public boolean occupies(int x, int z) {
        return x >= minX - 1 && x <= maxX + 1 && z >= minZ - 1 && z <= maxZ + 1;
    }
}
