package io.github.aswq0258.magiccity.city.feature;

import io.github.aswq0258.magiccity.build.Placer;
import io.github.aswq0258.magiccity.city.CityLayout;
import io.github.aswq0258.magiccity.city.Fantasy;
import io.github.aswq0258.magiccity.city.Shapes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import static io.github.aswq0258.magiccity.city.Palette.*;

/**
 * 결계탑: 마법 아카데미가 관리하는 도시 방어 결계의 기둥.
 * 짙은 돌 받침(계단식) + 가늘어지는 오벨리스크 + 자수정 띠, 꼭대기에 떠 있는 수정과 고리.
 */
public class BarrierTower extends Feature {
    private final int tx, tz;
    private final long seed;
    private static final BlockState DARK = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
    private static final BlockState DARK_BRICK = Blocks.DEEPSLATE_BRICKS.defaultBlockState();

    public BarrierTower(int tx, int tz, long seed) {
        super(tx - 6, tz - 6, tx + 6, tz + 6);
        this.tx = tx;
        this.tz = tz;
        this.seed = seed;
    }

    @Override
    public void build(Placer p, CityLayout city) {
        final int b = groundY;
        p.forColumns(tx - 4, tz - 4, tx + 4, tz + 4, (x, z) -> {
            int m = Math.max(Math.abs(x - tx), Math.abs(z - tz));
            if (m == 4) p.set(x, b + 1, z, DARK_BRICK);
            else if (m == 3) { p.set(x, b + 1, z, DARK_BRICK); p.set(x, b + 2, z, DARK); }
            else {
                for (int ly = 1; ly <= 3; ly++) p.set(x, b + ly, z, DARK_BRICK);
            }
        });
        for (int ly = 4; ly <= 18; ly++) {
            int half = ly < 10 ? 1 : 0;
            for (int dx = -half; dx <= half; dx++) {
                for (int dz = -half; dz <= half; dz++) {
                    BlockState s = ly % 5 == 0 ? Fantasy.AMETHYST : (dx == 0 && dz == 0 && ly % 5 == 2) ? LIGHT : DARK_BRICK;
                    p.set(tx + dx, b + ly, tz + dz, s);
                }
            }
        }
        p.set(tx, b + 19, tz, Fantasy.AMETHYST);
        p.set(tx, b + 20, tz, Fantasy.CLUSTER_UP);
        Shapes.crystal(p, tx, b + 25, tz, 2);
        Fantasy.halo(p, tx, tz, b + 25, 4.5);
        for (int[] o : new int[][]{{-3, -3}, {3, -3}, {-3, 3}, {3, 3}}) {
            p.set(tx + o[0], b + 3, tz + o[1], Fantasy.AMETHYST);
            p.set(tx + o[0], b + 4, tz + o[1], Fantasy.CLUSTER_UP);
        }
    }
}
