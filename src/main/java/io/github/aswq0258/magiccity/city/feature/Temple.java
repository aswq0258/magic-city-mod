package io.github.aswq0258.magiccity.city.feature;

import io.github.aswq0258.magiccity.build.Placer;
import io.github.aswq0258.magiccity.city.CityLayout;
import io.github.aswq0258.magiccity.city.Deco;
import io.github.aswq0258.magiccity.city.Fantasy;
import io.github.aswq0258.magiccity.city.Royal;
import io.github.aswq0258.magiccity.city.Shapes;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;

import static io.github.aswq0258.magiccity.city.Palette.*;

/**
 * 별빛의 신전 (대성당과 다른 판타지 종교 건물)
 *  - 3단 원형 기단 위에 기둥 12개가 둘린 열린 원형 신전(톨로스), 금 기둥머리, 기둥을 잇는 고리 보
 *  - 가운데 연꽃 연못 위에 거대한 수정이 떠 있고, 사방에서 빛기둥(엔드 막대)이 솟음
 *  - 앞쪽: 반사 연못 참배로, 양옆 조각상과 수정 가로등, 봉헌 촛대
 */
public class Temple extends Feature {
    private final int tx, tz;
    private final Direction front;
    private final long seed;
    private static final int R = 11;

    public Temple(int x0, int z0, int x1, int z1, Direction front, long seed) {
        super(Math.min(x0, x1), Math.min(z0, z1), Math.max(x0, x1), Math.max(z0, z1));
        this.front = front;
        this.seed = seed;
        int cx = (minX + maxX) >> 1, cz = (minZ + maxZ) >> 1;
        int depth = front.getAxis() == Direction.Axis.Z ? maxZ - minZ : maxX - minX;
        int back = Math.max(0, depth / 2 - R - 2);
        this.tx = cx - front.getStepX() * back;
        this.tz = cz - front.getStepZ() * back;
    }

    @Override
    public void build(Placer p, CityLayout city) {
        final int b = groundY;
        final int fx = front.getStepX(), fz = front.getStepZ();
        // 바닥 + 참배로 연못
        p.forColumns(minX, minZ, maxX, maxZ, (x, z) -> {
            int rx = x - tx, rz = z - tz;
            int along = rx * fx + rz * fz, lat = Math.abs(rx * fz - rz * fx);
            p.set(x, b, z, ((x + z) & 1) == 0 ? CALCITE : SMOOTH_QUARTZ);
            if (along > R + 3 && lat >= 3 && lat <= 5) {
                p.set(x, b, z, lat == 5 ? MINT_CUT : WATER);
                if (lat == 4 && Shapes.hash(x, 1, z, seed) % 5 == 0) p.set(x, b + 1, z, Blocks.LILY_PAD.defaultBlockState());
            }
        });
        // 3단 원형 기단 + 기둥 + 고리 보
        p.forColumns(tx - R - 3, tz - R - 3, tx + R + 3, tz + R + 3, (x, z) -> {
            double d = Math.hypot(x - tx, z - tz);
            for (int k = 0; k < 3; k++) {
                if (d <= R + 2.5 - k) p.set(x, b + 1 + k, z, d > R + 1.5 - k ? CHISELED_QUARTZ : Royal.marble(x, z));
            }
            if (d <= 4.5) {                                                // 연꽃 연못
                p.set(x, b + 3, z, d > 3.5 ? MINT_CUT : WATER);
                if (d <= 3.5 && Shapes.hash(x, 2, z, seed) % 4 == 0) p.set(x, b + 4, z, Blocks.LILY_PAD.defaultBlockState());
            }
            if (Math.abs(d - (R - 1)) <= 0.6) {                            // 고리 보
                p.set(x, b + 14, z, SMOOTH_QUARTZ);
                p.set(x, b + 15, z, Royal.GOLD);
                int arc = (int) Math.floor(Shapes.angle(x - tx, z - tz) * (R - 1));
                if (arc % 3 == 0) p.set(x, b + 16, z, END_ROD);
            }
        });
        for (int i = 0; i < 12; i++) {
            double a = Math.PI * 2 * i / 12 + Math.PI / 12;
            int px = tx + (int) Math.round(Math.cos(a) * (R - 1)), pz = tz + (int) Math.round(Math.sin(a) * (R - 1));
            for (int y = b + 4; y <= b + 13; y++) p.set(px, y, pz, y == b + 13 ? Royal.GOLD : y == b + 4 ? CHISELED_QUARTZ : QUARTZ_PILLAR);
        }
        // 정면 계단 (기단을 가르는 넓은 계단)
        for (int k = 0; k < 3; k++) {
            for (int lat = -3; lat <= 3; lat++) {
                int a = R + 2 - k;
                p.set(tx + fx * a - fz * lat, b + 1 + k, tz + fz * a + fx * lat, Royal.whiteStairs(front.getOpposite()));
            }
        }
        // 떠 있는 거대한 수정 + 수정 아래 빛 기둥
        Shapes.crystal(p, tx, b + 10, tz, 3);
        Fantasy.halo(p, tx, tz, b + 10, 5.5);
        for (int[] o : new int[][]{{6, 0}, {-6, 0}, {0, 6}, {0, -6}}) {
            for (int y = b + 4; y <= b + 6; y++) p.set(tx + o[0], y, tz + o[1], y == b + 6 ? Fantasy.CLUSTER_UP : Fantasy.AMETHYST);
        }
        // 참배로 장식: 조각상과 수정 가로등, 봉헌 촛대
        for (int a = R + 6; a <= R + 18; a += 6) {
            for (int s = -1; s <= 1; s += 2) {
                int x = tx + fx * a - fz * s * 7, z = tz + fz * a + fx * s * 7;
                if (x < minX + 1 || x > maxX - 1 || z < minZ + 1 || z > maxZ - 1) continue;
                if ((a / 6) % 2 == 0) Royal.statue(p, x, z, b, 3);
                else Fantasy.crystalLamp(p, x, z, b);
            }
        }
        for (int lat = -2; lat <= 2; lat += 4) {
            p.set(tx + fx * 7 - fz * lat, b + 4, tz + fz * 7 + fx * lat, Deco.candles(4));
        }
    }
}
