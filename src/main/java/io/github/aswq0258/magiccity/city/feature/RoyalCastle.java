package io.github.aswq0258.magiccity.city.feature;

import io.github.aswq0258.magiccity.build.Placer;
import io.github.aswq0258.magiccity.city.CityLayout;
import io.github.aswq0258.magiccity.city.Royal;
import io.github.aswq0258.magiccity.city.Shapes;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

import static io.github.aswq0258.magiccity.city.Palette.*;

/**
 * 중앙 왕성의 뼈대 (도면 좌표 X/Z 195~325, 북쪽 = 도면 Z 증가)
 *  - 왕성 성벽(높이 28, 금 띠, 총안) + 모서리 큰 탑 4개 + 성문 탑 8개 (흰 돔·금 첨탑)
 *  - 해자를 건너 성문까지 0 → +8 로 올라가는 경사 둑길 4개 (난간·가로등)
 *  - 2단 테라스(+18) 옹벽과 금 띠, 난간 / 남쪽 대계단, 동서 계단, 왕좌의 전당으로 가는 다리
 *  - C09 왕성 앞뜰: 산울타리 화단, 붉은·흰 꽃, 분수, 조각상, 가로등
 *  - 1단 테라스 가로등
 */
public class RoyalCastle extends Feature {
    public static final int L1 = 8, L2 = 18, WALL_H = 28;
    private final CityLayout c;

    public RoyalCastle(CityLayout city) {
        super(Math.min(city.wx(170), city.wx(351)), Math.min(city.wz(170), city.wz(351)),
                Math.max(city.wx(170), city.wx(351)), Math.max(city.wz(170), city.wz(351)));
        this.c = city;
    }

    @Override
    public boolean occupies(int x, int z) {
        int u = c.u(x), v = c.v(z);
        return u >= 172 && u <= 349 && v >= 172 && v <= 349;
    }

    private static boolean ringBand(int t) {
        return (t >= 195 && t <= 199) || (t >= 321 && t <= 325);
    }

    public static boolean inL2(int u, int v) {
        return u >= 232 && u <= 289 && v >= 240 && v <= 292;
    }

    @Override
    public void build(Placer p, CityLayout city) {
        final int y0 = groundY, y1 = y0 + L1, y2 = y0 + L2;

        p.forColumns(minX, minZ, maxX, maxZ, (x, z) -> {
            int u = c.u(x), v = c.v(z);
            wallColumn(p, x, z, u, v, y0, y1);
            causeway(p, x, z, u, v, y0);
            terraceEdge(p, x, z, u, v, y1, y2);
            stairs(p, x, z, u, v, y1);
            garden(p, x, z, u, v, y1);
        });

        // 모서리 탑과 성문 탑
        int[][] corners = {{195, 195}, {195, 325}, {325, 195}, {325, 325}};
        for (int[] k : corners) Palace.palaceTower(p, c.wx(k[0]), c.wz(k[1]), 8, y0, 48);
        int[][] gates = {{249, 197}, {272, 197}, {249, 323}, {272, 323}, {197, 249}, {197, 272}, {323, 249}, {323, 272}};
        for (int[] k : gates) Palace.palaceTower(p, c.wx(k[0]), c.wz(k[1]), 4, y0, 36);

        // 앞뜰 분수·조각상
        Shapes.fountain(p, c.wx(260), c.wz(217), y1);
        for (int[] s : new int[][]{{238, 210}, {283, 210}, {238, 226}, {283, 226}}) Royal.statue(p, c.wx(s[0]), c.wz(s[1]), y1, 4);

        // 1단 테라스 가로등
        int[][] avoid = {{202, 208, 232, 240}, {289, 208, 319, 240}, {202, 266, 232, 320}, {289, 266, 319, 320},
                {232, 292, 289, 321}, {230, 238, 291, 294}, {232, 204, 289, 240}, {220, 253, 233, 268}, {288, 253, 301, 268}};
        for (int u = 206; u <= 316; u += 11) {
            for (int v = 206; v <= 316; v += 11) {
                boolean blocked = false;
                for (int[] a : avoid) if (u >= a[0] && u <= a[2] && v >= a[1] && v <= a[3]) blocked = true;
                if (!blocked) Royal.lamp(p, c.wx(u), c.wz(v), y1);
            }
        }
        for (int v = 208; v <= 228; v += 5) {
            Royal.lamp(p, c.wx(255), c.wz(v), y1);
            Royal.lamp(p, c.wx(266), c.wz(v), y1);
        }
    }

    /** 왕성 성벽: 지면에서 +28, 성문은 +8 높이에 아치 */
    private void wallColumn(Placer p, int x, int z, int u, int v, int y0, int y1) {
        boolean uBand = ringBand(u) && v >= 195 && v <= 325;
        boolean vBand = ringBand(v) && u >= 195 && u <= 325;
        if (!uBand && !vBand) return;
        int t = uBand ? u : v, lat = uBand ? v : u;
        boolean outer = t == 195 || t == 325, inner = t == 199 || t == 321;
        boolean gate = lat >= 254 && lat <= 267 && !(uBand && vBand);
        double open = gate ? 10 + 2 * Math.sqrt(Math.max(0, 1 - Math.pow((lat - 260.5) / 7.0, 2))) : -1;
        for (int ly = 1; ly <= WALL_H; ly++) {
            BlockState s = QUARTZ_BRICKS;
            if (ly == WALL_H) s = SMOOTH_QUARTZ;
            else if (ly == WALL_H - 3) s = Royal.GOLD;
            else if (outer && ly >= 18 && ly <= 20 && Math.floorMod(lat, 6) == 0) s = Royal.CLEAR_GLASS;
            if (gate) {
                int rel = ly - L1;
                if (rel == 0) s = SMOOTH_QUARTZ;
                else if (rel > 0 && rel <= open) s = AIR;
                else if (rel > 0 && rel <= open + 1) s = (lat == 260 || lat == 261) ? Royal.GOLD : CHISELED_QUARTZ;
            }
            p.set(x, y0 + ly, z, s);
        }
        if (outer && Math.floorMod(lat, 12) == 6 && !gate) Royal.pinnacle(p, x, z, y0 + WALL_H + 1, 3);
        else if (outer && Math.floorMod(lat, 4) < 2) p.set(x, y0 + WALL_H + 1, z, CALCITE);
        if (inner) p.set(x, y0 + WALL_H + 1, z, SMOOTH_QUARTZ_SLAB);
    }

    /** 해자를 건너는 경사 둑길 (바깥 0 → 성문 +8) */
    private void causeway(Placer p, int x, int z, int u, int v, int y0) {
        int t, lat;
        Direction in;
        if (u >= 254 && u <= 267 && v >= 175 && v <= 194) { t = v - 175; lat = u; in = Direction.NORTH; }
        else if (u >= 254 && u <= 267 && v >= 326 && v <= 346) { t = 346 - v; lat = u; in = Direction.SOUTH; }
        else if (v >= 254 && v <= 267 && u >= 175 && u <= 194) { t = u - 175; lat = v; in = Direction.EAST; }
        else if (v >= 254 && v <= 267 && u >= 326 && u <= 346) { t = 346 - u; lat = v; in = Direction.WEST; }
        else return;
        int h = Math.min(L1, (t + 1) * L1 / 20), hn = Math.min(L1, (t + 2) * L1 / 20);
        boolean edge = lat == 254 || lat == 267;
        for (int y = y0 + 1; y < y0 + h; y++) p.set(x, y, z, QUARTZ_BRICKS);
        BlockState surface = edge ? QUARTZ_BRICKS : (hn > h ? Royal.whiteStairs(in) : (lat == 260 || lat == 261) ? CALCITE : SMOOTH_QUARTZ);
        p.set(x, y0 + h, z, surface);
        if (edge) {
            if (t % 6 == 3) Royal.lamp(p, x, z, y0 + h);
            else p.set(x, y0 + h + 1, z, t % 2 == 0 ? CHISELED_QUARTZ : SMOOTH_QUARTZ_SLAB);
        }
    }

    /** 2단 테라스 옹벽 + 난간 */
    private void terraceEdge(Placer p, int x, int z, int u, int v, int y1, int y2) {
        if (!inL2(u, v)) {
            if (u >= 258 && u <= 263 && v == 293) {       // 왕좌의 전당 회랑으로 가는 다리
                p.set(x, y2, z, SMOOTH_QUARTZ);
                if (u == 258 || u == 263) p.set(x, y2 + 1, z, SMOOTH_QUARTZ_SLAB);
            }
            return;
        }
        boolean edge = u == 232 || u == 289 || v == 240 || v == 292;
        if (!edge) return;
        boolean opening = (v == 240 && u >= 252 && u <= 269) || ((u == 232 || u == 289) && v >= 255 && v <= 266) || (v == 292 && u >= 258 && u <= 263);
        if (opening) return;
        for (int y = y1 + 1; y < y2; y++) p.set(x, y, z, y == y1 + 5 ? Royal.GOLD : QUARTZ_BRICKS);
        p.set(x, y2, z, CHISELED_QUARTZ);
        p.set(x, y2 + 1, z, Math.floorMod(u + v, 3) == 0 ? CHISELED_QUARTZ : SMOOTH_QUARTZ_SLAB);
    }

    /** 남쪽 대계단(폭 18)과 동서 계단: +8 → +18 */
    private void stairs(Placer p, int x, int z, int u, int v, int y1) {
        int k, lat, lo, hi;
        Direction up;
        if (u >= 252 && u <= 269 && v >= 230 && v <= 239) { k = v - 229; lat = u; lo = 252; hi = 269; up = Direction.NORTH; }
        else if (v >= 255 && v <= 266 && u >= 222 && u <= 231) { k = u - 221; lat = v; lo = 255; hi = 266; up = Direction.EAST; }
        else if (v >= 255 && v <= 266 && u >= 290 && u <= 299) { k = 300 - u; lat = v; lo = 255; hi = 266; up = Direction.WEST; }
        else return;
        boolean side = lat == lo || lat == hi;
        for (int y = y1 + 1; y < y1 + k; y++) p.set(x, y, z, QUARTZ_BRICKS);
        p.set(x, y1 + k, z, side ? QUARTZ_BRICKS : Royal.whiteStairs(up));
        if (side) p.set(x, y1 + k + 1, z, k % 3 == 0 ? CHISELED_QUARTZ : SMOOTH_QUARTZ_SLAB);
    }

    /** C09 왕성 앞뜰 */
    private void garden(Placer p, int x, int z, int u, int v, int y1) {
        if (u < 234 || u > 287 || v < 206 || v > 229) return;
        boolean path = (u >= 257 && u <= 264) || (v >= 216 && v <= 219);
        boolean border = u == 234 || u == 287 || v == 206 || v == 229;
        int h = Shapes.hash(x, 41, z, c.seed);
        if (path) {
            p.set(x, y1, z, ((u + v) & 1) == 0 ? CALCITE : SMOOTH_QUARTZ);
            return;
        }
        p.set(x, y1, z, GRASS);
        if (border) {
            p.set(x, y1 + 1, z, Royal.HEDGE);
            return;
        }
        int du = Math.min(Math.abs(u - 245), Math.abs(u - 276)), dv = Math.min(Math.abs(v - 211), Math.abs(v - 224));
        int dd = du + dv;
        if (dd % 4 == 0) p.set(x, y1 + 1, z, Royal.HEDGE_FLOWER);
        else if (dd <= 9) p.set(x, y1 + 1, z, Royal.ROYAL_FLOWERS[(dd / 4) % 2 == 0 ? (h & 1) * 2 : 1 + (h & 1) * 2]);
    }
}
