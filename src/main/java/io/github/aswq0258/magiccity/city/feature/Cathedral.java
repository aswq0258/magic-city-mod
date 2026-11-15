package io.github.aswq0258.magiccity.city.feature;

import io.github.aswq0258.magiccity.build.Placer;
import io.github.aswq0258.magiccity.city.CityLayout;
import io.github.aswq0258.magiccity.city.Deco;
import io.github.aswq0258.magiccity.city.Royal;
import io.github.aswq0258.magiccity.city.Shapes;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

import static io.github.aswq0258.magiccity.city.Palette.*;

/**
 * 대성당 (길이 66, 폭 31, 벽 높이 24)
 * 외관: 정면 아치 대문 + 장미창 + 박공, 정면 쌍탑(높이 54), 버팀벽, 뾰족 아치 스테인드글라스, 교차부 돔, 반원 후진(apse), 민트 맞배지붕
 * 내부: 아치 열주, 신도석, 민트 카펫 통로, 계단식 제단(금 장식·촛불·독서대), 샹들리에
 */
public class Cathedral extends Feature {
    private static final int LEN = 50, HALF = 15, H = 24, APSE_R = 15, DOME_A = 34, DOME_R = 9;
    private final int ox, oz;           // 정면 중앙
    private final int fx, fz, lx, lz;   // 앞 방향, 옆 방향
    private final Direction front;
    private final long seed;

    public Cathedral(int x0, int z0, int x1, int z1, Direction front, long seed) {
        super(x0, z0, x1, z1);
        int cx = (x0 + x1) >> 1, cz = (z0 + z1) >> 1;
        this.front = front;
        this.fx = front.getStepX();
        this.fz = front.getStepZ();
        this.lx = -fz;
        this.lz = fx;
        this.ox = cx + fx * 32;
        this.oz = cz + fz * 32;
        this.seed = seed;
    }

    /** (along: 정면에서 뒤로, lat: 옆) → 월드 좌표 */
    private int wx(int a, int lat) {
        return ox - fx * a + lx * lat;
    }

    private int wz(int a, int lat) {
        return oz - fz * a + lz * lat;
    }

    private static BlockState stained(int x, int y, int z) {
        return Math.floorMod(x + y + z, 3) == 0 ? GLASS_WHITE : GLASS;
    }

    @Override
    public void build(Placer p, CityLayout city) {
        final int b = groundY;
        final Direction toFront = front, toBack = front.getOpposite();
        final Direction latPos = Deco.toward(lx, lz), latNeg = latPos.getOpposite();

        // 앞마당
        p.forColumns(minX, minZ, maxX, maxZ, (x, z) -> {
            int rx = x - ox, rz = z - oz;
            int a = -(rx * fx + rz * fz), lat = rx * lx + rz * lz;
            if (a < 0) p.set(x, b, z, Math.abs(lat) <= 3 ? SMOOTH_QUARTZ : CALCITE);
        });

        p.forColumns(minX, minZ, maxX, maxZ, (x, z) -> {
            int rx = x - ox, rz = z - oz;
            int a = -(rx * fx + rz * fz), lat = rx * lx + rz * lz, al = Math.abs(lat);
            if (a < -1 || a > LEN + APSE_R + 1 || al > HALF + 2) return;

            // 반원 후진
            if (a > LEN) {
                double d = Math.hypot(a - LEN, lat);
                if (d > APSE_R + 0.5) return;
                p.set(x, b, z, Royal.marble(x, z));
                boolean wall = d > APSE_R - 0.5;
                for (int ly = 1; ly <= H; ly++) {
                    BlockState s = AIR;
                    if (wall) s = (ly % 6 >= 2 && ly % 6 <= 4 && ly > 4 && ((int) Math.floor(Math.atan2(lat, a - LEN) * 8) & 1) == 0)
                            ? stained(x, ly, z) : (ly == 12 ? MINT_CUT : QUARTZ_BRICKS);
                    else if (ly == H) s = SMOOTH_QUARTZ;
                    p.set(x, b + ly, z, s);
                }
                for (int k = 1; k <= APSE_R; k++) { // 반구 지붕
                    double d3 = Math.sqrt(d * d + k * k);
                    if (d3 <= APSE_R + 0.5 && d3 > APSE_R - 0.9) p.set(x, b + H + k, z, MINT_BLOCK);
                }
                return;
            }

            // 맞배지붕
            if (a <= LEN) {
                for (int t = 0; t <= HALF + 1; t++) {
                    int half = HALF + 1 - t, ly = H + 1 + t;
                    if (al == half) p.set(x, b + ly, z, half == 0 ? MINT_CHISELED : mintStairs(lat > 0 ? latNeg : latPos));
                    else if (al < half && a == 0) p.set(x, b + ly, z, QUARTZ_BRICKS);
                }
            }
            if (a < 0) return;

            // 버팀벽
            if (al > HALF) {
                if (al == HALF + 1 && a % 6 == 3) {
                    for (int ly = 1; ly < H; ly++) p.set(x, b + ly, z, ly == H - 1 ? CHISELED_QUARTZ : QUARTZ_PILLAR);
                    p.set(x, b + H, z, MINT_CHISELED);
                    p.set(x, b + H + 1, z, END_ROD);
                }
                return;
            }

            p.set(x, b, z, QUARTZ_BRICKS);
            if (a == 0) { // 정면
                for (int ly = 1; ly <= H; ly++) {
                    BlockState s = ly == 12 ? MINT_CUT : QUARTZ_BRICKS;
                    if (al == HALF) s = QUARTZ_PILLAR;
                    int r2 = al * al + (ly - 17) * (ly - 17);
                    if (r2 <= 16) s = (lat == 0 || ly == 17) ? CALCITE : stained(x, ly, z);
                    else if (r2 <= 25) s = MINT_CHISELED;
                    if (al <= 3) {
                        double top = 8 + 3 * Math.sqrt(Math.max(0, 1 - (al / 3.5) * (al / 3.5)));
                        if (ly <= top) s = AIR;
                        else if (ly <= top + 1) s = CHISELED_QUARTZ;
                    }
                    if (al == 4 && ly <= 10) s = CHISELED_QUARTZ;
                    p.set(x, b + ly, z, s);
                }
                return;
            }
            if (al == HALF) { // 옆벽
                int m = a % 6;
                for (int ly = 1; ly <= H; ly++) {
                    BlockState s;
                    if (m == 3) s = (ly == 12 || ly == H) ? CHISELED_QUARTZ : QUARTZ_PILLAR;
                    else if (ly <= 2) s = QUARTZ_BRICKS;
                    else if (ly == H) s = SMOOTH_QUARTZ;
                    else if ((m == 0) && ly >= 4 && ly <= 19) s = stained(x, ly, z);
                    else if ((m == 5 || m == 1) && ly >= 6 && ly <= 17) s = stained(x, ly, z);
                    else if (m == 0 && ly == 20) s = CHISELED_QUARTZ;
                    else s = QUARTZ_BRICKS;
                    p.set(x, b + ly, z, s);
                }
                return;
            }

            // 내부
            p.set(x, b, z, Royal.marble(x, z));
            for (int ly = 1; ly <= H; ly++) {
                BlockState s = AIR;
                if (ly == H) s = (a % 6 == 0 && al % 6 == 0) ? LIGHT : SMOOTH_QUARTZ;
                else if (al == 8 && a % 6 == 3) s = (ly == 1 || ly == 12 || ly == H - 1) ? CHISELED_QUARTZ : QUARTZ_PILLAR;
                else if (al == 8 && ly >= 18) s = QUARTZ_BRICKS;
                p.set(x, b + ly, z, s);
            }
            if (al <= 1 && a >= 1 && a <= 44) p.set(x, b + 1, z, Deco.CARPET_MINT);
            else if (al >= 2 && al <= 6 && a % 2 == 0 && a >= 6 && a <= 40) p.set(x, b + 1, z, Deco.chair(toFront));
            if (lat == 0 && a % 6 == 3 && a >= 6) Deco.chandelier(p, x, b + H, z, 6);
            if (al == 12 && a % 12 == 6) Deco.chandelier(p, x, b + H, z, 2);
        });

        // 제단 (후진 쪽)
        int altarA = LEN + 4;
        for (int a = LEN - 4; a <= LEN + 10; a++) {
            for (int lat = -7; lat <= 7; lat++) {
                int step = (a >= LEN - 2 && Math.abs(lat) <= 6) ? 1 : 0;
                if (a >= LEN && Math.abs(lat) <= 5) step = 2;
                if (step >= 1) p.set(wx(a, lat), b + 1, wz(a, lat), step == 1 && (a == LEN - 2 || Math.abs(lat) == 6)
                        ? Deco.quartzStairs(toBack) : SMOOTH_QUARTZ);
                if (step == 2) p.set(wx(a, lat), b + 2, wz(a, lat), (a == LEN || Math.abs(lat) == 5) ? Deco.quartzStairs(toBack) : SMOOTH_QUARTZ);
            }
        }
        for (int lat = -2; lat <= 2; lat++) {
            p.set(wx(altarA, lat), b + 3, wz(altarA, lat), lat == 0 ? Royal.GOLD : CHISELED_QUARTZ);
            p.set(wx(altarA, lat), b + 4, wz(altarA, lat), Deco.candles(4));
        }
        p.set(wx(altarA - 2, 0), b + 3, wz(altarA - 2, 0), Deco.lectern(toFront));
        for (int y = b + 3; y <= b + 14; y++) p.set(wx(LEN + 12, 0), y, wz(LEN + 12, 0), y == b + 14 ? Royal.GOLD : QUARTZ_PILLAR);
        p.set(wx(LEN + 12, 0), b + 15, wz(LEN + 12, 0), END_ROD);

        // 교차부 돔 (드럼 + 반구 + 꼭대기 랜턴)
        int dcx = wx(DOME_A, 0), dcz = wz(DOME_A, 0);
        int roofTop = b + H + HALF + 2;
        p.forColumns(dcx - DOME_R - 1, dcz - DOME_R - 1, dcx + DOME_R + 1, dcz + DOME_R + 1, (x, z) -> {
            double d = Math.hypot(x - dcx, z - dcz);
            if (d > DOME_R + 0.5) return;
            boolean wall = d > DOME_R - 0.5;
            for (int y = b + H + 1; y <= roofTop; y++) {
                if (wall) p.set(x, y, z, (y - b) % 4 == 2 && ((x + z) & 1) == 0 ? GLASS : QUARTZ_BRICKS);
            }
            for (int k = 1; k <= DOME_R; k++) {
                double d3 = Math.sqrt(d * d + k * k);
                if (d3 <= DOME_R + 0.5 && d3 > DOME_R - 0.9) p.set(x, roofTop + k, z, (Math.floorMod(x + z, 4) == 0) ? CALCITE : MINT_BLOCK);
            }
        });
        for (int k = 1; k <= 4; k++) p.set(dcx, roofTop + DOME_R + k, dcz, k == 4 ? Royal.GOLD : QUARTZ_PILLAR);
        p.set(dcx, roofTop + DOME_R + 5, dcz, END_ROD);

        // 정면 쌍탑
        for (int s : new int[]{-1, 1}) {
            int tx = wx(3, s * (HALF + 5)), tz = wz(3, s * (HALF + 5));
            Shapes.roundTower(p, tx, tz, 5.0, b, 54, front);
        }
    }
}
