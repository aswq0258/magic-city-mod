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
 * 중앙 왕성 (지름 500) — 흰 석영 + 금, 르네상스 양식
 *
 * 성곽: 정사각 성벽(높이 32, 금 띠, 내물림 통로, 총안), 모서리 큰 원형 탑 4개 + 중간 탑 8개(흰 돔·금 갈비뼈·금 첨탑),
 *       동서남북 성문(남문이 정문)과 문루 탑
 * 궁전: ㄷ자 배치 — 본관(220 x 61, 3층) + 서쪽 날개(대연회장) + 동쪽 날개(무도회장), 3단 기단과 계단
 *       본관 중앙 원형 알현실: 바닥에서 지름 60 돔까지 트인 공간, 16개 금 기둥머리 열주, 2개 층 원형 회랑,
 *       계단식 단 위 금 옥좌와 붉은 천개(캐노피), 붉은 깃발, 남문에서 옥좌까지 붉은 카펫, 거대한 금 샹들리에
 *       돔: 기둥과 창이 둘린 드럼 위 흰 돔(금 갈비뼈 16줄) + 꼭대기 채광탑 + 금 돔과 첨탑
 *       남쪽 열주 현관: 2x2 거대 기둥 6쌍, 금 기둥머리, 금 띠 엔타블러처, 금 메달리온이 박힌 삼각 박공
 * 정원: 명예의 안뜰(금 별 무늬 + 조각상 + 분수), 남쪽 대로와 쌍둥이 반사 연못, 화단 정원(산울타리·붉은/흰 꽃·분수),
 *       양옆 숲 정원(자작나무 격자·정자), 북쪽 정원, 금장식 가로등
 */
public class Palace extends Feature {
    public static final int OUT = 250, IN = 240, WALL_H = 32;
    private static final int RZ = -120, RR = 30, DRUM = 48, DOME_R = 30;
    private static final int[][] RECTS = {{-110, -150, 110, -90}, {-110, -89, -70, 20}, {70, -89, 110, 20}, {-26, -89, 26, -79}};

    private final int cx, cz;
    private final long seed;

    public Palace(CityLayout city) {
        super(city.cx - 256, city.cz - 256, city.cx + 256, city.cz + 256);
        this.cx = city.cx;
        this.cz = city.cz;
        this.seed = city.seed ^ 0x9A1ACEL;
    }

    @Override
    public boolean occupies(int x, int z) {
        return Math.max(Math.abs(x - cx), Math.abs(z - cz)) <= 256;
    }

    @Override
    public void build(Placer p, CityLayout city) {
        final int b = groundY, py = b + 3;
        gardens(p, b);
        castleWall(p, b);
        podium(p, b);

        new PalaceBlock(cx - 110, cz - 150, cx + 110, cz - 90, py, PalaceBlock.Mode.ROOMS, cx, cz + RZ, RR + 0.5, seed)
                .door(cx - 90, cz - 90, 2).door(cx + 90, cz - 90, 2)
                .door(cx - 50, cz - 150, 2).door(cx + 50, cz - 150, 2)
                .door(cx - 110, cz - 120, 2).door(cx + 110, cz - 120, 2).build(p);
        new PalaceBlock(cx - 110, cz - 89, cx - 70, cz + 20, py, PalaceBlock.Mode.BANQUET, 0, 0, -1, seed + 1)
                .door(cx - 90, cz - 89, 2).door(cx - 70, cz - 35, 3).door(cx - 110, cz - 35, 2).door(cx - 90, cz + 20, 2).build(p);
        new PalaceBlock(cx + 70, cz - 89, cx + 110, cz + 20, py, PalaceBlock.Mode.BALLROOM, 0, 0, -1, seed + 2)
                .door(cx + 90, cz - 89, 2).door(cx + 70, cz - 35, 3).door(cx + 110, cz - 35, 2).door(cx + 90, cz + 20, 2).build(p);

        rotunda(p, py);
        portico(p, py);
        towers(p, b);
    }

    // ───────────────────────── 성곽 ─────────────────────────

    private void castleWall(Placer p, int b) {
        p.forColumns(cx - OUT - 1, cz - OUT - 1, cx + OUT + 1, cz + OUT + 1, (x, z) -> {
            int dx = x - cx, dz = z - cz;
            int m = Math.max(Math.abs(dx), Math.abs(dz));
            if (m <= IN || m > OUT + 1) return;
            boolean xSide = Math.abs(dx) >= Math.abs(dz);
            int lat = xSide ? dz : dx;
            if (m > OUT) {
                Direction in = xSide ? (dx > 0 ? Direction.WEST : Direction.EAST) : (dz > 0 ? Direction.NORTH : Direction.SOUTH);
                p.set(x, b + WALL_H - 1, z, Deco.corbel(in));
                p.set(x, b + WALL_H, z, SMOOTH_QUARTZ);
                if (Math.floorMod(lat, 4) < 2) p.set(x, b + WALL_H + 1, z, CALCITE);
                return;
            }
            boolean south = !xSide && dz > 0;
            boolean gate = Math.abs(lat) <= 7;
            double top = gate ? (south ? 24 : 20) + 4 * Math.sqrt(Math.max(0, 1 - (lat / 7.5) * (lat / 7.5))) : -1;
            for (int ly = 1; ly <= WALL_H; ly++) {
                BlockState s = QUARTZ_BRICKS;
                if (ly == WALL_H) s = SMOOTH_QUARTZ;
                else if (ly == WALL_H - 3) s = Royal.GOLD;
                else if (ly % 8 == 0) s = SMOOTH_QUARTZ;
                else if (m > OUT - 1 && ly >= 14 && ly <= 16 && Math.floorMod(lat, 8) == 0) s = Royal.CLEAR_GLASS;
                if (gate && ly <= top) s = AIR;
                else if (gate && ly <= top + 1) s = (lat == 0 && south) ? Royal.GOLD : CHISELED_QUARTZ;
                p.set(x, b + ly, z, s);
            }
            if (m <= IN + 1) p.set(x, b + WALL_H + 1, z, SMOOTH_QUARTZ_SLAB);
        });
    }

    private void towers(Placer p, int b) {
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                palaceTower(p, cx + sx * 234, cz + sz * 234, 16, b, 56);
                palaceTower(p, cx + sx * 234, cz + sz * 120, 9, b, 44);
                palaceTower(p, cx + sx * 120, cz + sz * 234, 9, b, 44);
            }
        }
        int[][] axes = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        for (int[] a : axes) {
            int gx = cx + a[0] * 245, gz = cz + a[1] * 245;
            int lx = -a[1], lz = a[0];
            palaceTower(p, gx + lx * 20, gz + lz * 20, 9, b, 48);
            palaceTower(p, gx - lx * 20, gz - lz * 20, 9, b, 48);
            p.set(gx + a[0] * 6 + lx * 4, b + 30, gz + a[1] * 6 + lz * 4, Royal.redBanner(Deco.toward(a[0], a[1])));
            p.set(gx + a[0] * 6 - lx * 4, b + 30, gz + a[1] * 6 - lz * 4, Royal.redBanner(Deco.toward(a[0], a[1])));
        }
    }

    /** 흰 탑 + 금 띠 + 흰 돔(금 갈비뼈) + 금 첨탑 */
    public static void palaceTower(Placer p, int tx, int tz, double rad, int b, int h) {
        int ext = (int) Math.ceil(rad) + 2;
        if (!p.intersects(tx - ext, tz - ext, tx + ext, tz + ext)) return;
        p.forColumns(tx - ext, tz - ext, tx + ext, tz + ext, (x, z) -> {
            int dx = x - tx, dz = z - tz;
            double d = Math.sqrt(dx * dx + dz * dz);
            double ang = Shapes.angle(dx, dz);
            int arc = (int) Math.floor(ang * rad);
            if (d <= rad + 0.5) {
                boolean wall = d > rad - 1.5;
                p.set(x, b, z, SMOOTH_QUARTZ);
                for (int ly = 1; ly <= h; ly++) {
                    BlockState s;
                    if (wall) {
                        if (ly == h - 2) s = Royal.GOLD;
                        else if (ly % 8 == 0 || ly == h) s = SMOOTH_QUARTZ;
                        else if (ly > 6 && (ly % 8 == 4 || ly % 8 == 5) && arc % 5 == 0) s = Royal.CLEAR_PANE;
                        else s = QUARTZ_BRICKS;
                    } else {
                        s = ly % 8 == 0 ? (d < 0.5 ? LIGHT : SMOOTH_QUARTZ) : AIR;
                    }
                    p.set(x, b + ly, z, s);
                }
            } else if (d <= rad + 1.5) {
                p.set(x, b + h - 1, z, Deco.corbel(Deco.toward(-dx, -dz)));
                p.set(x, b + h, z, SMOOTH_QUARTZ);
                if (arc % 2 == 0) p.set(x, b + h + 1, z, CALCITE);
            }
            double frac = (ang * 8 / (Math.PI * 2)) % 1.0;
            for (int k = 1; k <= (int) Math.ceil(rad); k++) {
                double d3 = Math.sqrt(d * d + k * k);
                if (d3 <= rad + 0.3 && d3 > rad - 1.1) p.set(x, b + h + k, z, (frac < 0.12 && d > 1) ? Royal.GOLD : SMOOTH_QUARTZ);
            }
        });
        int top = b + h + (int) Math.ceil(rad) + 1;
        for (int y = top; y < top + 3; y++) p.set(tx, y, tz, Royal.GOLD);
        p.set(tx, top + 3, tz, END_ROD);
    }

    // ───────────────────────── 기단 ─────────────────────────

    private void podium(Placer p, int b) {
        p.forColumns(cx - 114, cz - 154, cx + 114, cz + 24, (x, z) -> {
            int best = Integer.MAX_VALUE;
            int nx = 0, nz = 0;
            for (int[] r : RECTS) {
                int rx0 = cx + r[0], rz0 = cz + r[1], rx1 = cx + r[2], rz1 = cz + r[3];
                int qx = Math.max(rx0, Math.min(x, rx1)), qz = Math.max(rz0, Math.min(z, rz1));
                int dist = Math.max(Math.abs(x - qx), Math.abs(z - qz));
                if (dist < best) {
                    best = dist;
                    nx = qx;
                    nz = qz;
                }
            }
            if (best > 3) return;
            Direction up = Deco.toward(nx - x, nz - z);
            switch (best) {
                case 0 -> {
                    p.set(x, b + 1, z, QUARTZ_BRICKS);
                    p.set(x, b + 2, z, QUARTZ_BRICKS);
                    if (z - cz >= -89 && z - cz <= -79 && Math.abs(x - cx) <= 26) p.set(x, b + 3, z, Royal.marble(x, z));
                }
                case 1 -> {
                    p.set(x, b + 1, z, QUARTZ_BRICKS);
                    p.set(x, b + 2, z, QUARTZ_BRICKS);
                    p.set(x, b + 3, z, SMOOTH_QUARTZ);
                }
                case 2 -> {
                    p.set(x, b + 1, z, QUARTZ_BRICKS);
                    p.set(x, b + 2, z, Royal.whiteStairs(up));
                }
                default -> p.set(x, b + 1, z, Royal.whiteStairs(up));
            }
        });
    }

    // ───────────────────────── 원형 알현실 + 돔 ─────────────────────────

    private void rotunda(Placer p, int py) {
        final int rx = cx, rz = cz + RZ;
        if (!p.intersects(rx - RR - 12, rz - RR - 12, rx + RR + 12, rz + RR + 12)) return;
        p.forColumns(rx - RR - 1, rz - RR - 1, rx + RR + 1, rz + RR + 1, (x, z) -> {
            int dx = x - rx, dz = z - rz;
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d > RR + 0.5) return;
            double ang = Shapes.angle(dx, dz);
            int adx = Math.abs(dx), adz = Math.abs(dz);
            int latC = adx >= adz ? adz : adx;

            if (d > RR - 3) { // 벽 (두께 3) + 드럼
                boolean southAxis = dz > 0 && adx <= 4;
                boolean ewAxis = adz <= 2 && adx > 20;
                p.set(x, py, z, QUARTZ_BRICKS);
                for (int ly = 1; ly <= DRUM; ly++) {
                    BlockState s;
                    if (ly > PalaceBlock.H) {
                        int bay = Math.floorMod((int) Math.floor(ang * RR), 10);
                        if (ly == PalaceBlock.H + 1) s = CHISELED_QUARTZ;
                        else if (ly == DRUM - 1) s = Royal.GOLD;
                        else if (bay == 0) s = ly == DRUM - 2 ? Royal.GOLD : QUARTZ_PILLAR;
                        else if (bay >= 4 && bay <= 6 && ly >= 34 && ly <= 42) s = Royal.CLEAR_PANE;
                        else if (bay == 5 && ly == 43) s = Royal.GOLD;
                        else s = SMOOTH_QUARTZ;
                    } else {
                        s = ly % 10 == 0 ? CHISELED_QUARTZ : SMOOTH_QUARTZ;
                    }
                    if (southAxis && ly <= 18) {
                        double top = 14 + 2 * Math.sqrt(Math.max(0, 1 - (adx / 4.5) * (adx / 4.5)));
                        if (ly <= top) s = AIR;
                        else if (ly <= top + 1) s = adx == 0 ? Royal.GOLD : CHISELED_QUARTZ;
                    }
                    if (ewAxis && ly <= PalaceBlock.H) {
                        int loc = ly % 10;
                        if (loc >= 1 && loc <= 6) s = AIR;
                    }
                    p.set(x, py + ly, z, s);
                }
            } else { // 내부
                p.set(x, py, z, rotundaFloor(d, latC, adx, adz, x, z));
                for (int ly = 1; ly <= DRUM; ly++) {
                    BlockState s = AIR;
                    boolean skip = (adx <= 10 && dz < 0) || (adx <= 5 && dz > 0);
                    if ((ly == 10 || ly == 20) && d > 24.5 && !(ly == 10 && skip)) s = SMOOTH_QUARTZ;
                    if ((ly == 11 || ly == 21) && d > 24.5 && d <= 25.5 && !(ly == 11 && skip)) s = Royal.CLEAR_PANE;
                    p.set(x, py + ly, z, s);
                }
            }
            // 돔
            double frac = (ang * 16 / (Math.PI * 2)) % 1.0;
            for (int k = 1; k <= DOME_R; k++) {
                double d3 = Math.sqrt(d * d + k * k);
                if (d3 <= DOME_R && d3 > DOME_R - 1.4) p.set(x, py + DRUM + k, z, (frac < 0.08 && d > 3) ? Royal.GOLD : SMOOTH_QUARTZ);
                else if (d3 <= DOME_R - 1.4) p.set(x, py + DRUM + k, z, AIR);
            }
        });

        // 열주 16개
        for (int k = 0; k < 16; k++) {
            double a = (k + 0.5) * Math.PI / 8;
            int dx = (int) Math.round(Math.cos(a) * 24), dz = (int) Math.round(Math.sin(a) * 24);
            if ((Math.abs(dx) <= 6 && dz > 0) || (Math.abs(dx) <= 11 && dz < -10)) continue;
            for (int ly = 1; ly <= 33; ly++) {
                BlockState s = ly == 1 || ly == 33 ? CHISELED_QUARTZ : ly == 32 ? Royal.GOLD : QUARTZ_PILLAR;
                p.set(rx + dx, py + ly, rz + dz, s);
            }
        }

        // 남문에서 옥좌까지 붉은 카펫
        for (int dz = -15; dz <= RR - 3; dz++) {
            for (int dx = -2; dx <= 2; dx++) p.set(rx + dx, py + 1, rz + dz, Math.abs(dx) == 2 ? Royal.GOLD_CARPET : Royal.RED_CARPET);
        }

        // 옥좌 단 (3단)
        int[][] tiers = {{9, -15}, {7, -17}, {5, -19}};
        for (int t = 0; t < 3; t++) {
            int hw = tiers[t][0], front = tiers[t][1];
            for (int dx = -hw; dx <= hw; dx++) {
                for (int dz = -26; dz <= front; dz++) {
                    boolean edgeFront = dz == front, edgeSide = Math.abs(dx) == hw;
                    BlockState s = edgeFront ? Royal.whiteStairs(Direction.NORTH)
                            : edgeSide ? Royal.whiteStairs(dx > 0 ? Direction.WEST : Direction.EAST) : SMOOTH_QUARTZ;
                    p.set(rx + dx, py + 1 + t, rz + dz, s);
                }
            }
        }
        Royal.rug(p, rx - 4, rz - 25, rx + 4, rz - 20, py + 4);
        // 옥좌
        int ty = py + 4, tz = rz - 23;
        p.set(rx, ty, tz, Royal.chair(Direction.NORTH));
        p.set(rx - 1, ty, tz, Royal.GOLD);
        p.set(rx + 1, ty, tz, Royal.GOLD);
        for (int dx = -1; dx <= 1; dx++) for (int y = ty; y <= ty + (dx == 0 ? 3 : 2); y++) p.set(rx + dx, y, tz - 1, Royal.GOLD);
        p.set(rx, ty + 4, tz - 1, END_ROD);
        // 천개 (캐노피)
        int[][] posts = {{-4, -20}, {4, -20}, {-4, -26}, {4, -26}};
        for (int[] q : posts) for (int y = ty; y <= ty + 7; y++) p.set(rx + q[0], y, rz + q[1], QUARTZ_PILLAR);
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -26; dz <= -20; dz++) {
                boolean edge = Math.abs(dx) == 4 || dz == -26 || dz == -20;
                p.set(rx + dx, ty + 8, rz + dz, edge ? Royal.GOLD : Royal.RED_WOOL);
            }
        }
        p.set(rx, ty + 9, rz - 23, Royal.GOLD);
        p.set(rx, ty + 10, rz - 23, END_ROD);
        // 붉은 깃발 (북쪽 벽)
        for (int dx : new int[]{-12, -8, 8, 12}) {
            int dz = -(int) Math.floor(Math.sqrt((RR - 3.0) * (RR - 3.0) - dx * dx));
            p.set(rx + dx, py + 9, rz + dz, Royal.redBanner(Direction.SOUTH));
        }
        // 샹들리에: 가운데 거대 1개 + 둘레 8개
        Royal.chandelier(p, rx, py + DRUM + DOME_R - 1, rz, 30);
        for (int k = 0; k < 8; k++) {
            double a = (k + 0.5) * Math.PI / 4;
            int dx = (int) Math.round(Math.cos(a) * 14), dz = (int) Math.round(Math.sin(a) * 14);
            int ceil = py + DRUM + (int) Math.ceil(Math.sqrt((DOME_R - 1.4) * (DOME_R - 1.4) - 196));
            Royal.chandelier(p, rx + dx, ceil, rz + dz, 22);
        }
        // 꼭대기 채광탑 + 금 돔 + 첨탑
        int top = py + DRUM + DOME_R;
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4;
            int dx = (int) Math.round(Math.cos(a) * 4), dz = (int) Math.round(Math.sin(a) * 4);
            for (int y = top - 1; y <= top + 6; y++) p.set(rx + dx, y, rz + dz, QUARTZ_PILLAR);
        }
        p.forColumns(rx - 6, rz - 6, rx + 6, rz + 6, (x, z) -> {
            double d = Math.hypot(x - rx, z - rz);
            if (d <= 5.5) p.set(x, top + 7, z, d > 4.5 ? CHISELED_QUARTZ : SMOOTH_QUARTZ);
            for (int k = 1; k <= 5; k++) {
                double d3 = Math.sqrt(d * d + k * k);
                if (d3 <= 5.3 && d3 > 4.2) p.set(x, top + 7 + k, z, Royal.GOLD);
            }
        });
        for (int y = top + 12; y <= top + 15; y++) p.set(rx, y, rz, Royal.GOLD);
        p.set(rx, top + 16, rz, END_ROD);
    }

    private BlockState rotundaFloor(double d, int latC, int adx, int adz, int x, int z) {
        if (d <= 2.5) return Royal.GOLD;
        if (Math.abs(d - 24) < 0.5) return Royal.GOLD;
        if (Math.abs(d - 12) < 0.5 || Math.abs(d - 18) < 0.5) return CHISELED_QUARTZ;
        if (d > 6 && d < 23 && (latC == 0 || adx == adz)) return CHISELED_QUARTZ;
        return Royal.marble(x, z);
    }

    // ───────────────────────── 남쪽 열주 현관 ─────────────────────────

    private void portico(Placer p, int py) {
        if (!p.intersects(cx - 30, cz - 92, cx + 30, cz - 74)) return;
        int zf = cz - 79, zb = cz - 89;
        int[] colX = {-24, -16, -8, 7, 15, 23};
        for (int c0 : colX) {
            for (int dx = 0; dx <= 1; dx++) {
                for (int z = zf - 1; z <= zf; z++) {
                    for (int ly = 1; ly <= 26; ly++) {
                        BlockState s = ly == 1 || ly == 25 ? CHISELED_QUARTZ : ly == 26 ? Royal.GOLD : QUARTZ_PILLAR;
                        p.set(cx + c0 + dx, py + ly, z, s);
                    }
                }
            }
        }
        for (int dx = -26; dx <= 26; dx++) {
            for (int z = zb; z <= zf; z++) {
                boolean face = z == zf;
                p.set(cx + dx, py + 27, z, face ? (Math.floorMod(dx, 4) == 0 ? Royal.GOLD : CHISELED_QUARTZ) : SMOOTH_QUARTZ);
                p.set(cx + dx, py + 28, z, SMOOTH_QUARTZ);
            }
            p.set(cx + dx, py + 28, zf + 1, Deco.corbel(Direction.NORTH));
            if (Math.floorMod(dx, 4) == 2) p.set(cx + dx, py + 27, zf + 1, Deco.hangingLantern());
        }
        for (int t = 0; t <= 13; t++) {
            int ly = 29 + t, uL = -26 + 2 * t, uR = 26 - 2 * t;
            if (uL > uR) break;
            for (int z = zb; z <= zf; z++) {
                boolean face = z == zf;
                for (int u = uL; u <= uR; u++) {
                    boolean edgeL = u <= uL + 1, edgeR = u >= uR - 1;
                    BlockState s;
                    if (edgeL && !edgeR) s = Royal.whiteStairs(Direction.EAST);
                    else if (edgeR && !edgeL) s = Royal.whiteStairs(Direction.WEST);
                    else if (edgeL) s = SMOOTH_QUARTZ_SLAB;
                    else if (face) {
                        int r2 = u * u + (ly - 33) * (ly - 33);
                        s = r2 <= 9 ? Royal.GOLD : r2 <= 16 ? CHISELED_QUARTZ : QUARTZ_BRICKS;
                    } else continue;
                    p.set(cx + u, py + ly, z, s);
                }
            }
        }
        // 앞 계단 3단
        for (int dx = -26; dx <= 26; dx++) {
            p.set(cx + dx, py, zf + 1, Royal.whiteStairs(Direction.NORTH));
            p.set(cx + dx, py - 1, zf + 2, Royal.whiteStairs(Direction.NORTH));
            p.set(cx + dx, py - 2, zf + 3, Royal.whiteStairs(Direction.NORTH));
            p.set(cx + dx, py - 1, zf + 1, QUARTZ_BRICKS);
            p.set(cx + dx, py - 2, zf + 1, QUARTZ_BRICKS);
            p.set(cx + dx, py - 2, zf + 2, QUARTZ_BRICKS);
        }
    }

    // ───────────────────────── 정원 ─────────────────────────

    private void gardens(Placer p, int b) {
        p.forColumns(cx - IN, cz - IN, cx + IN, cz + IN, (x, z) -> {
            int dx = x - cx, dz = z - cz, adx = Math.abs(dx);
            if (Math.max(adx, Math.abs(dz)) > IN) return;
            int h = Shapes.hash(x, 11, z, seed);

            if (adx <= 7 && dz >= 21) {                                        // 남쪽 대로
                p.set(x, b, z, adx == 7 ? CHISELED_QUARTZ : dx == 0 ? CALCITE : SMOOTH_QUARTZ);
            } else if ((Math.abs(dz) <= 4 && adx >= 112) || (adx <= 4 && dz <= -153)) { // 동서 길, 북쪽 길
                p.set(x, b, z, SMOOTH_QUARTZ);
            } else if (adx <= 66 && dz >= -75 && dz <= 20) {                     // 명예의 안뜰
                double rr = Math.hypot(dx, dz + 30);
                int ax = Math.abs(dx), az = Math.abs(dz + 30);
                BlockState s = Royal.marble(x, z);
                if (rr <= 20 && (ax == 0 || az == 0 || ax == az)) s = Royal.GOLD;
                else if (Math.abs(rr - 22) < 0.5) s = CHISELED_QUARTZ;
                p.set(x, b, z, s);
            } else if (adx >= 11 && adx <= 21 && dz >= 39 && dz <= 221) {        // 쌍둥이 반사 연못
                boolean rim = adx == 11 || adx == 21 || dz == 39 || dz == 221;
                if (rim) {
                    p.set(x, b, z, SMOOTH_QUARTZ);
                    p.set(x, b + 1, z, SMOOTH_QUARTZ_SLAB);
                } else {
                    p.set(x, b, z, WATER);
                    p.set(x, b - 1, z, WATER);
                    p.set(x, b - 2, z, CALCITE);
                }
            } else if (adx >= 24 && adx <= 116 && dz >= 30 && dz <= 215) {      // 화단 정원
                int u = Math.floorMod(adx - 24, 31), v = Math.floorMod(dz - 30, 31);
                if (u == 30 || v == 30) {
                    p.set(x, b, z, CALCITE);
                } else {
                    p.set(x, b, z, GRASS);
                    boolean gap = u == 14 || u == 15 || v == 14 || v == 15;
                    if ((u == 0 || u == 29 || v == 0 || v == 29) && !gap) {
                        p.set(x, b + 1, z, Royal.HEDGE);
                    } else if (!gap) {
                        int dd = (int) (Math.abs(u - 14.5) + Math.abs(v - 14.5));
                        if (dd <= 13 && dd >= 7) {
                            if (dd % 3 == 0) p.set(x, b + 1, z, Royal.HEDGE_FLOWER);
                            else p.set(x, b + 1, z, Royal.ROYAL_FLOWERS[((dd / 3) % 2 == 0 ? 0 : 1) + ((h >> 3) & 1) * 2]);
                        }
                    } else {
                        p.set(x, b, z, CALCITE);
                    }
                }
            } else if (adx >= 120) {                                           // 숲 정원
                boolean path = Math.floorMod(dz, 40) <= 2 || Math.floorMod(adx - 120, 40) <= 2;
                p.set(x, b, z, path ? CALCITE : GRASS);
                if (!path && h % 9 == 0) p.set(x, b + 1, z, FLOWERS[(h >> 4) % FLOWERS.length]);
            } else if (dz <= -158) {                                           // 북쪽 정원
                p.set(x, b, z, GRASS);
                if (h % 7 == 0) p.set(x, b + 1, z, Royal.ROYAL_FLOWERS[(h >> 4) % 4]);
            } else {
                p.set(x, b, z, GRASS);
            }
        });

        // 안뜰 조각상과 분수
        Royal.statue(p, cx, cz - 30, b, 7);
        Shapes.fountain(p, cx - 42, cz - 30, b);
        Shapes.fountain(p, cx + 42, cz - 30, b);
        for (int z = -70; z <= 15; z += 15) {
            Royal.lamp(p, cx - 62, cz + z, b);
            Royal.lamp(p, cx + 62, cz + z, b);
        }
        // 대로 가로등과 정문 조각상
        for (int z = 30; z <= 230; z += 12) {
            Royal.lamp(p, cx - 9, cz + z, b);
            Royal.lamp(p, cx + 9, cz + z, b);
        }
        Royal.statue(p, cx - 13, cz + 232, b, 5);
        Royal.statue(p, cx + 13, cz + 232, b, 5);
        // 화단 정원 분수
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 6; j++) {
                int fz = cz + 30 + 31 * j + 15;
                for (int s = -1; s <= 1; s += 2) {
                    int fx = cx + s * (24 + 31 * i + 15);
                    if (p.intersects(fx - 6, fz - 6, fx + 6, fz + 6)) Shapes.fountain(p, fx, fz, b);
                }
            }
        }
        // 숲 정원: 자작나무 격자, 가로등, 정자
        int[][] gazebos = {{180, 60}, {180, 140}, {180, -100}, {180, -180}};
        int sx0 = Math.max(p.minX() - 3, cx - IN), sx1 = Math.min(p.maxX() + 3, cx + IN);
        int sz0 = Math.max(p.minZ() - 3, cz - IN), sz1 = Math.min(p.maxZ() + 3, cz + IN);
        for (int x = sx0; x <= sx1; x++) {
            for (int z = sz0; z <= sz1; z++) {
                int dx = x - cx, dz = z - cz, adx = Math.abs(dx);
                if (adx >= 124 && adx <= 236 && Math.abs(dz) <= 236 && Math.floorMod(adx - 120, 10) == 6 && Math.floorMod(dz, 10) == 6) {
                    boolean nearGazebo = false;
                    for (int[] g : gazebos) if (Math.abs(adx - g[0]) <= 9 && Math.abs(dz - g[1]) <= 9) nearGazebo = true;
                    if (!nearGazebo && Math.abs(dz) > 8) Shapes.birchTree(p, x, z, b, 6 + Shapes.hash(x, 2, z, seed) % 2);
                }
                if (adx >= 120 && Math.floorMod(adx - 120, 40) == 4 && Math.floorMod(dz, 40) == 4) Royal.lamp(p, x, z, b);
                if (dz <= -166 && dz >= -232 && adx <= 112 && adx >= 10 && Math.floorMod(adx, 12) == 6 && Math.floorMod(dz, 20) == 10) {
                    Shapes.birchTree(p, x, z, b, 6);
                }
            }
        }
        for (int[] g : gazebos) {
            gazebo(p, cx + g[0], cz + g[1], b);
            gazebo(p, cx - g[0], cz + g[1], b);
        }
        Shapes.fountain(p, cx, cz - 195, b);
        Royal.statue(p, cx - 30, cz - 195, b, 5);
        Royal.statue(p, cx + 30, cz - 195, b, 5);
    }

    /** 정자: 기둥 8개 + 흰 돔(금 갈비뼈) + 가운데 탁자와 의자 */
    private void gazebo(Placer p, int gx, int gz, int b) {
        if (!p.intersects(gx - 7, gz - 7, gx + 7, gz + 7)) return;
        p.forColumns(gx - 6, gz - 6, gx + 6, gz + 6, (x, z) -> {
            double d = Math.hypot(x - gx, z - gz);
            if (d <= 5.5) p.set(x, b, z, d > 4.5 ? CHISELED_QUARTZ : Royal.marble(x, z));
            if (d > 3.5 && d <= 5.5) p.set(x, b + 6, z, ((x + z) & 1) == 0 ? Royal.GOLD : CHISELED_QUARTZ);
            double frac = (Shapes.angle(x - gx, z - gz) * 8 / (Math.PI * 2)) % 1.0;
            for (int k = 1; k <= 5; k++) {
                double d3 = Math.sqrt(d * d + k * k);
                if (d3 <= 5.3 && d3 > 4.3) p.set(x, b + 6 + k, z, frac < 0.15 ? Royal.GOLD : SMOOTH_QUARTZ);
            }
        });
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4;
            int dx = (int) Math.round(Math.cos(a) * 4.5), dz = (int) Math.round(Math.sin(a) * 4.5);
            for (int y = b + 1; y <= b + 5; y++) p.set(gx + dx, y, gz + dz, QUARTZ_PILLAR);
        }
        Royal.table(p, gx, b + 1, gz);
        p.set(gx + 1, b + 1, gz, Royal.chair(Direction.EAST));
        p.set(gx - 1, b + 1, gz, Royal.chair(Direction.WEST));
        p.set(gx, b + 1, gz + 1, Royal.chair(Direction.SOUTH));
        p.set(gx, b + 1, gz - 1, Royal.chair(Direction.NORTH));
        Royal.chandelier(p, gx, b + 6, gz, 0);
        p.set(gx, b + 12, gz, Royal.GOLD);
        p.set(gx, b + 13, gz, END_ROD);
    }
}
