package io.github.aswq0258.magiccity.city.feature;

import io.github.aswq0258.magiccity.build.Placer;
import io.github.aswq0258.magiccity.city.CityLayout;
import io.github.aswq0258.magiccity.city.Deco;
import io.github.aswq0258.magiccity.city.Fantasy;
import io.github.aswq0258.magiccity.city.Palette;
import io.github.aswq0258.magiccity.city.Shapes;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import static io.github.aswq0258.magiccity.city.Palette.*;

/**
 * 아카데미 천문대: 짙은 돌 원통 + 보라 유리 띠, 녹청 구리 돔에 관측 틈, 틈 밖으로 뻗은 구리 망원경,
 * 안에는 별자리 바닥(금·청금석), 지도 탁자, 독서대, 사다리.
 */
public class Observatory extends Feature {
    private static final int R = 6, H = 20;
    private static final BlockState DARK = Blocks.DEEPSLATE_BRICKS.defaultBlockState();
    private static final BlockState COPPER = Blocks.CUT_COPPER.defaultBlockState();
    private final int tx, tz;
    private final Direction front;

    public Observatory(int tx, int tz, Direction front) {
        super(tx - R - 6, tz - R - 6, tx + R + 6, tz + R + 6);
        this.tx = tx;
        this.tz = tz;
        this.front = front;
    }

    @Override
    public void build(Placer p, CityLayout city) {
        final int b = groundY;
        p.forColumns(tx - R - 1, tz - R - 1, tx + R + 1, tz + R + 1, (x, z) -> {
            int dx = x - tx, dz = z - tz;
            double d = Math.hypot(dx, dz);
            if (d > R + 0.5) return;
            boolean wall = d > R - 0.6;
            int arc = (int) Math.floor(Shapes.angle(dx, dz) * R);
            p.set(x, b, z, wall ? DARK : (Math.floorMod(dx * 3 + dz * 5, 7) == 0 ? Blocks.GOLD_BLOCK.defaultBlockState() : Blocks.LAPIS_BLOCK.defaultBlockState()));
            for (int ly = 1; ly <= H; ly++) {
                BlockState s = AIR;
                if (wall) s = ly % 7 == 0 ? Blocks.PURPLE_STAINED_GLASS.defaultBlockState() : (ly % 7 >= 3 && ly % 7 <= 5 && arc % 4 == 0) ? Blocks.PURPLE_STAINED_GLASS_PANE.defaultBlockState() : DARK;
                else if (ly == 10) s = d < 2 ? AIR : Blocks.DARK_OAK_PLANKS.defaultBlockState();
                p.set(x, b + ly, z, s);
            }
            // 녹청 구리 돔 + 관측 틈 (정면 반대쪽 하늘)
            for (int k = 1; k <= R; k++) {
                double d3 = Math.sqrt(d * d + k * k);
                if (d3 <= R + 0.4 && d3 > R - 0.9) {
                    boolean slit = Math.abs(dx * front.getStepZ() - dz * front.getStepX()) <= 0 && (dx * front.getStepX() + dz * front.getStepZ()) <= 0;
                    p.set(x, b + H + k, z, slit ? AIR : MINT_BLOCK);
                }
            }
        });
        // 망원경 (뒤쪽 위로 비스듬히)
        int bx = -front.getStepX(), bz = -front.getStepZ();
        for (int k = 0; k <= 7; k++) {
            int x = tx + bx * (k / 2), z = tz + bz * (k / 2), y = b + H + 1 + k;
            p.set(x, y, z, k == 7 ? Blocks.GLASS.defaultBlockState() : COPPER);
        }
        p.set(tx, b + H, tz, COPPER);
        p.set(tx, b + H - 1, tz, Blocks.CHAIN.defaultBlockState());
        // 입구 + 사다리 + 가구
        int fx = front.getStepX(), fz = front.getStepZ();
        for (int ly = 1; ly <= 3; ly++) p.set(tx + fx * R, b + ly, tz + fz * R, AIR);
        for (int ly = 1; ly <= H; ly++) p.set(tx - fx * (R - 1), b + ly, tz - fz * (R - 1), Palette.ladder(front));
        p.set(tx + 2, b + 1, tz, Blocks.CARTOGRAPHY_TABLE.defaultBlockState());
        p.set(tx - 2, b + 1, tz, Deco.lectern(Direction.EAST));
        p.set(tx, b + 1, tz + 2, ENCHANTING_TABLE);
        p.set(tx + 2, b + 11, tz + 2, Deco.lectern(Direction.WEST));
        Fantasy.crystalLamp(p, tx + fx * (R + 3) - fz * 3, tz + fz * (R + 3) + fx * 3, b);
        Fantasy.crystalLamp(p, tx + fx * (R + 3) + fz * 3, tz + fz * (R + 3) - fx * 3, b);
    }
}
