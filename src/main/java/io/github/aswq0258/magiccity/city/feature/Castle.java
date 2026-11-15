package io.github.aswq0258.magiccity.city.feature;

import io.github.aswq0258.magiccity.build.Placer;
import io.github.aswq0258.magiccity.city.CityLayout;
import io.github.aswq0258.magiccity.city.Deco;
import io.github.aswq0258.magiccity.city.Shapes;
import io.github.aswq0258.magiccity.city.Towers;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import static io.github.aswq0258.magiccity.city.CityLayout.AVENUE_HALF;
import static io.github.aswq0258.magiccity.city.Palette.*;

/**
 * 중앙 대성 (성역 고원 위, 지름 약 190칸)
 *
 * ┌ 대성전 (원형, 반지름 22, 벽 높이 96)
 * │   외관: 벽 띠, 16개 버팀벽과 첨탑 장식, 높은 스테인드글라스 창, 대각선 4방향 거대 장미창,
 * │         두 층의 원형 외부 발코니, 민트 돔(방해석 갈비뼈) + 꼭대기 유리 천창, 열린 왕관 장식과 유리 수정
 * │   내부: 바닥에서 돔까지 뚫린 성소(지름 24) — 원형 제단과 신호기(beacon): 빛줄기가 천창을 지나 하늘로,
 * │         떠 있는 빛의 고리 2개와 수정 8개, 16개 열주, 5층 원형 회랑(서고·독서대·촛불), 나선 대계단 2개
 * ├ 십자 회랑 4개 (각 길이 27, 폭 17, 높이 28): 아치 열주, 신도석, 민트 카펫, 스테인드글라스 창,
 * │   정면 아치 대문 + 장미창 + 박공, 양옆 탑, 민트 맞배지붕
 * ├ 대각선 첨탑 4개 (높이 76)
 * ├ 안뜰 정원: 잔디·흰 꽃, 자작나무, 분수 연못 8개, 방해석 길
 * ├ 성벽 (반지름 70~74, 높이 24): 아치 성문 8개, 성문 탑 16개, 큰 탑 8개, 내물림 통로와 총안
 * └ 돔을 도는 공중 고리, 광장 위 떠 있는 수정 8개
 */
public class Castle extends Feature {
    // 대성전
    private static final int RK = 22, WALL_IN = 19, ATRIUM = 12, HK = 96;
    private static final int[] GALLERIES = {16, 32, 48, 64, 80};
    private static final int[] OUT_BALCONY = {48, 80};
    private static final int[][] SPIRALS = {{11, 11}, {-11, -11}};
    // 십자 회랑
    private static final int W_START = 19, W_END = 46, W_HALF = 8, W_H = 28;
    // 성벽
    private static final double CW_IN = 70, CW_OUT = 74;
    private static final int CW_H = 24;

    private final int cx, cz;
    private final long seed;
    private final long[] pillars = new long[16];

    public Castle(CityLayout city) {
        super(city.cx - 96, city.cz - 96, city.cx + 96, city.cz + 96);
        this.cx = city.cx;
        this.cz = city.cz;
        this.seed = city.seed ^ 0x5A17EDL;
        for (int k = 0; k < 16; k++) {
            double a = (k + 0.5) * Math.PI / 8;
            pillars[k] = key((int) Math.round(Math.cos(a) * 12.5), (int) Math.round(Math.sin(a) * 12.5));
        }
    }

    private static long key(int dx, int dz) {
        return ((long) dx << 32) ^ (dz & 0xFFFFFFFFL);
    }

    private boolean isPillar(int dx, int dz) {
        long k = key(dx, dz);
        for (long p : pillars) if (p == k) return true;
        return false;
    }

    private static boolean inSpiral(int dx, int dz, int reach) {
        for (int[] s : SPIRALS) {
            if (Math.max(Math.abs(dx - s[0]), Math.abs(dz - s[1])) <= reach) return true;
        }
        return false;
    }

    private static boolean isGallery(int ly) {
        for (int g : GALLERIES) if (g == ly) return true;
        return false;
    }

    /** 흰색·하늘색이 섞인 스테인드글라스 */
    private static BlockState stained(int x, int y, int z) {
        return Math.floorMod(x + z + y, 3) == 0 ? GLASS_WHITE : GLASS;
    }

    @Override
    public boolean occupies(int x, int z) {
        return false; // 성 광장의 가로등은 광장 패턴(r > 84)이 결정
    }

    @Override
    public void build(Placer p, CityLayout c) {
        int y = groundY;
        buildCourtyard(p, c, y);
        buildCurtainWall(p, c, y);
        for (int k = 0; k < 4; k++) buildWing(p, y, k);
        buildRotunda(p, y);
        buildSpirals(p, y);
        furnishGalleries(p, y);
        buildSanctum(p, y);
        buildCrown(p, y);

        for (int k = 0; k < 4; k++) {   // 대각선 첨탑
            double a = Math.PI / 4 + k * Math.PI / 2;
            int sx = cx + (int) Math.round(Math.cos(a) * 33), sz = cz + (int) Math.round(Math.sin(a) * 33);
            Shapes.roundTower(p, sx, sz, 5.0, y, 76, Deco.toward(cx - sx, cz - sz));
        }
        for (int k = 0; k < 8; k++) {   // 성벽 탑
            double a = k * Math.PI / 4;
            double tx = -Math.sin(a), tz = Math.cos(a);
            double gx = cx + Math.cos(a) * 72, gz = cz + Math.sin(a) * 72;
            Shapes.roundTower(p, (int) Math.round(gx + tx * 12), (int) Math.round(gz + tz * 12), 5.0, y, 40, null);
            Shapes.roundTower(p, (int) Math.round(gx - tx * 12), (int) Math.round(gz - tz * 12), 5.0, y, 40, null);
            double b = a + Math.PI / 8;
            Shapes.roundTower(p, cx + (int) Math.round(Math.cos(b) * 72), cz + (int) Math.round(Math.sin(b) * 72), 7.0, y, 50, null);
        }
        for (int k = 0; k < 8; k++) {   // 광장 위 수정
            double a = (k + 0.5) * Math.PI / 4;
            Shapes.crystal(p, cx + (int) Math.round(Math.cos(a) * 92), y + 46, cz + (int) Math.round(Math.sin(a) * 92), 4);
        }
        floatingRing(p, y + HK + 10, 34, true);
    }

    // ───────────────────────── 안뜰 ─────────────────────────

    private void buildCourtyard(Placer p, CityLayout c, int y) {
        p.forColumns(cx - 70, cz - 70, cx + 70, cz + 70, (x, z) -> {
            double dx = x - cx, dz = z - cz;
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d <= RK || d > CW_IN) return;
            double ad = c.avenueDist(dx, dz);
            if (ad <= AVENUE_HALF) {
                p.set(x, y, z, ad > AVENUE_HALF - 1 ? MINT_CUT : CALCITE);
            } else {
                p.set(x, y, z, GRASS);
                int h = Shapes.hash(x, 9, z, seed);
                if (h % 4 == 0) p.set(x, y + 1, z, FLOWERS[(h >> 4) % FLOWERS.length]);
            }
        });
        for (int k = 0; k < 8; k++) {
            double a = (k + 0.5) * Math.PI / 4;
            Shapes.fountain(p, cx + (int) Math.round(Math.cos(a) * 56), cz + (int) Math.round(Math.sin(a) * 56), y);
            Shapes.birchTree(p, cx + (int) Math.round(Math.cos(a) * 42), cz + (int) Math.round(Math.sin(a) * 42), y, 6);
            for (double off : new double[]{-0.12, 0.12}) {
                Shapes.birchTree(p, cx + (int) Math.round(Math.cos(a + off) * 62), cz + (int) Math.round(Math.sin(a + off) * 62), y, 6);
            }
            Shapes.lampPost(p, cx + (int) Math.round(Math.cos(a) * 30), cz + (int) Math.round(Math.sin(a) * 30), y);
            double a2 = k * Math.PI / 4;
            for (double off : new double[]{-0.16, 0.16}) {
                Shapes.lampPost(p, cx + (int) Math.round(Math.cos(a2 + off) * 60), cz + (int) Math.round(Math.sin(a2 + off) * 60), y);
            }
        }
    }

    // ───────────────────────── 성벽 ─────────────────────────

    private void buildCurtainWall(Placer p, CityLayout c, int y) {
        p.forColumns(cx - 76, cz - 76, cx + 76, cz + 76, (x, z) -> {
            double dx = x - cx, dz = z - cz;
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d <= CW_IN || d > CW_OUT + 1) return;
            int arc = (int) Math.floor(Shapes.angle(dx, dz) * CW_OUT);
            if (d > CW_OUT) {
                p.set(x, y + CW_H - 1, z, Deco.corbel(Deco.toward(-dx, -dz)));
                p.set(x, y + CW_H, z, SMOOTH_QUARTZ);
                if ((arc / 2) % 2 == 0) p.set(x, y + CW_H + 1, z, CALCITE);
                return;
            }
            double ad = c.avenueDist(dx, dz);
            double top = ad <= AVENUE_HALF ? 14 + 4 * Math.sqrt(Math.max(0, 1 - (ad / AVENUE_HALF) * (ad / AVENUE_HALF))) : -1;
            for (int ly = 1; ly <= CW_H; ly++) {
                BlockState s = QUARTZ_BRICKS;
                if (ly == CW_H) s = SMOOTH_QUARTZ;
                else if (ly == 20) s = MINT_CUT;
                else if (d > CW_OUT - 1 && arc % 6 == 0 && ly >= 9 && ly <= 12 && ad > 7) s = stained(x, ly, z);
                if (ly <= top) s = AIR;
                else if (top > 0 && ly <= top + 1) s = CHISELED_QUARTZ;
                p.set(x, y + ly, z, s);
            }
            if (d <= CW_IN + 1) p.set(x, y + CW_H + 1, z, SMOOTH_QUARTZ_SLAB);
        });
    }

    // ───────────────────────── 십자 회랑 ─────────────────────────

    private void buildWing(Placer p, int y, int k) {
        final int ax = new int[]{1, 0, -1, 0}[k];
        final int az = new int[]{0, 1, 0, -1}[k];
        final Direction out = Deco.toward(ax, az);
        final Direction latPos = Deco.toward(-az, ax);
        final Direction latNeg = latPos.getOpposite();
        int ext = W_END + 2;

        p.forColumns(cx - ext, cz - ext, cx + ext, cz + ext, (x, z) -> {
            int dx = x - cx, dz = z - cz;
            int along = dx * ax + dz * az;
            int lat = -dx * az + dz * ax;
            int alat = Math.abs(lat);
            if (along < W_START || along > W_END + 1 || alat > 10) return;
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d <= RK + 0.5) return;

            // 맞배지붕 (용마루 방향 = 회랑 방향)
            for (int t = 0; t <= 9; t++) {
                int ly = W_H + 1 + t, half = 9 - t;
                if (alat == half) {
                    BlockState s = half == 0 ? MINT_CHISELED : mintStairs(lat > 0 ? latNeg : latPos);
                    p.set(x, y + ly, z, s);
                    if (half == 0 && along % 6 == 0) p.set(x, y + ly + 1, z, END_ROD);
                } else if (alat < half) {
                    p.set(x, y + ly, z, (along == W_END) ? QUARTZ_BRICKS : AIR); // 정면 박공벽
                }
            }
            if (along == W_END + 1) return;

            if (alat >= 9) { // 버팀벽
                if (alat == 9 && along % 4 == 2) {
                    for (int ly = 1; ly < W_H; ly++) p.set(x, y + ly, z, ly == W_H - 1 ? CHISELED_QUARTZ : QUARTZ_PILLAR);
                    p.set(x, y + W_H, z, MINT_CHISELED);
                }
                return;
            }

            p.set(x, y, z, QUARTZ_BRICKS);
            if (alat == W_HALF || along == W_END) {
                for (int ly = 1; ly <= W_H; ly++) p.set(x, y + ly, z, wingWall(x, z, along, alat, ly));
                return;
            }

            // 내부: 아치 열주, 격자 천장
            p.set(x, y, z, ((along + lat) & 1) == 0 ? SMOOTH_QUARTZ : CALCITE);
            for (int ly = 1; ly <= W_H; ly++) {
                BlockState s = AIR;
                if (ly == W_H) s = (along % 4 == 0 && alat % 4 == 0) ? LIGHT : SMOOTH_QUARTZ;
                else if (alat == 5 && along % 4 == 2) s = (ly == 1 || ly == 14 || ly == W_H - 1) ? CHISELED_QUARTZ : QUARTZ_PILLAR;
                else if (alat == 5 && ly >= 24) s = QUARTZ_BRICKS;
                p.set(x, y + ly, z, s);
            }
            // 가구: 민트 카펫 통로, 신도석, 측랑의 서가와 촛불
            if (alat <= 1 && along >= 21) {
                p.set(x, y + 1, z, Deco.CARPET_MINT);
            } else if (alat >= 2 && alat <= 4 && along % 2 == 0 && along >= 26 && along <= 42) {
                p.set(x, y + 1, z, Deco.chair(out));
            } else if (alat == 7 && along % 4 == 0) {
                p.set(x, y + 1, z, BOOKSHELF);
                p.set(x, y + 2, z, BOOKSHELF);
                p.set(x, y + 3, z, Deco.candles(4));
            } else if (alat == 7 && along % 4 == 2) {
                p.set(x, y + 1, z, Deco.pot(Shapes.hash(x, 1, z, seed)));
            }
            if (alat == 0 && along % 6 == 3 && along >= 27) Deco.chandelier(p, x, y + W_H, z, 8);
            if (alat == 6 && along % 8 == 4) Deco.chandelier(p, x, y + W_H, z, 3);
        });

        // 정면 양옆 탑
        int ex = cx + ax * W_END, ez = cz + az * W_END;
        Shapes.roundTower(p, ex - az * 12, ez + ax * 12, 3.5, y, 44, null);
        Shapes.roundTower(p, ex + az * 12, ez - ax * 12, 3.5, y, 44, null);
    }

    private BlockState wingWall(int x, int z, int along, int alat, int ly) {
        if (along == W_END) { // 정면: 아치 대문 + 장미창
            if (alat == W_HALF) return QUARTZ_PILLAR;
            BlockState s = ly == W_H ? SMOOTH_QUARTZ : ly == 14 ? MINT_CUT : QUARTZ_BRICKS;
            int r2 = alat * alat + (ly - 20) * (ly - 20);
            if (r2 <= 16) s = (alat == 0 || ly == 20) ? CALCITE : stained(x, ly, z);
            else if (r2 <= 25) s = MINT_CHISELED;
            if (alat <= 3) {
                double top = 10 + 4 * Math.sqrt(Math.max(0, 1 - (alat / 3.5) * (alat / 3.5)));
                if (ly <= top) s = AIR;
                else if (ly <= top + 1) s = CHISELED_QUARTZ;
            }
            if (alat == 4 && ly <= 12) s = CHISELED_QUARTZ;
            return s;
        }
        int m = along % 4; // 옆벽: 기둥 사이 뾰족 아치창
        if (m == 2) return (ly == 14 || ly == W_H) ? CHISELED_QUARTZ : QUARTZ_PILLAR;
        if (ly <= 2) return QUARTZ_BRICKS;
        if (ly == 14) return MINT_CUT;
        if (ly == W_H) return SMOOTH_QUARTZ;
        if (m == 0) {
            if ((ly >= 4 && ly <= 12) || (ly >= 16 && ly <= 25)) return stained(x, ly, z);
            if (ly == 13 || ly == 26) return CHISELED_QUARTZ;
        } else if ((ly >= 6 && ly <= 11) || (ly >= 18 && ly <= 24)) {
            return stained(x, ly, z);
        }
        return QUARTZ_BRICKS;
    }

    // ───────────────────────── 대성전 ─────────────────────────

    private void buildRotunda(Placer p, int y) {
        p.forColumns(cx - 26, cz - 26, cx + 26, cz + 26, (x, z) -> {
            int dx = x - cx, dz = z - cz;
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d > 25.5) return;
            int latC = Math.abs(dx) >= Math.abs(dz) ? Math.abs(dz) : Math.abs(dx);
            double latD = Math.min(Math.abs(dx - dz), Math.abs(dx + dz)) / Math.sqrt(2);
            int arc = (int) Math.floor(Shapes.angle(dx, dz) * RK);

            if (d > RK) { // 바깥: 받침단, 발코니, 꼭대기 난간
                if (d <= RK + 1.5 && latC > 9) {
                    for (int ly = 1; ly <= 3; ly++) p.set(x, y + ly, z, QUARTZ_BRICKS);
                    p.set(x, y + 4, z, SMOOTH_QUARTZ_SLAB);
                }
                for (int bl : OUT_BALCONY) {
                    p.set(x, y + bl, z, SMOOTH_QUARTZ);
                    if (d > RK + 2) {
                        p.set(x, y + bl + 1, z, GLASS_PANE);
                        p.set(x, y + bl - 1, z, Deco.corbel(Deco.toward(-dx, -dz)));
                    }
                }
                if (d <= RK + 1.5) {
                    p.set(x, y + HK, z, SMOOTH_QUARTZ);
                    if (arc % 2 == 0) p.set(x, y + HK + 1, z, CALCITE);
                }
                return;
            }

            if (d > WALL_IN) { // 벽 (두께 3)
                boolean outer = d > RK - 1;
                p.set(x, y, z, QUARTZ_BRICKS);
                for (int ly = 1; ly <= HK; ly++) {
                    int lm = ly % 16;
                    BlockState s = lm == 0 ? MINT_CUT : (ly % 8 == 0 ? CALCITE : QUARTZ_BRICKS);
                    if (arc % 6 == 3 && ly > 40 && lm >= 4 && lm <= 12) s = stained(x, ly, z);
                    if (arc % 6 == 3 && ly > 40 && lm == 13 && outer) s = CHISELED_QUARTZ;
                    if (latD <= 7) { // 대각선 장미창
                        double r2 = latD * latD + (ly - 60) * (ly - 60);
                        if (r2 <= 30) s = (latD < 0.75 || ly == 60) ? CALCITE : stained(x, ly, z);
                        else if (r2 <= 49 && outer) s = MINT_CHISELED;
                    }
                    if (latC <= 4) { // 회랑으로 통하는 뾰족 아치
                        double top = 14 + 4 * Math.sqrt(Math.max(0, 1 - (latC / 4.5) * (latC / 4.5)));
                        if (ly <= top) s = AIR;
                        else if (ly <= top + 1) s = CHISELED_QUARTZ;
                    }
                    if (latC <= 1 && ((ly > 48 && ly <= 51) || (ly > 80 && ly <= 83))) s = AIR; // 발코니 문
                    p.set(x, y + ly, z, s);
                }
            } else { // 내부
                boolean annulus = d > ATRIUM;
                p.set(x, y, z, annulus ? (((dx + dz) & 1) == 0 ? SMOOTH_QUARTZ : CALCITE) : atriumFloor(d, latC, latD));
                boolean spiral = inSpiral(dx, dz, 2);
                for (int ly = 1; ly <= HK; ly++) {
                    BlockState s = AIR;
                    if (annulus && !spiral && isGallery(ly)) s = SMOOTH_QUARTZ;
                    p.set(x, y + ly, z, s);
                }
                if (annulus && d <= ATRIUM + 1 && !spiral) {
                    for (int g : GALLERIES) p.set(x, y + g + 1, z, GLASS_PANE);
                }
            }

            // 돔 + 천창
            for (int ly = 1; ly <= RK; ly++) {
                double d3 = Math.sqrt(d * d + ly * ly);
                if (d3 <= RK && d3 > RK - 1.4) {
                    BlockState s = (arc % 11 == 0) ? CALCITE : MINT_BLOCK;
                    if (d <= 2.5) s = GLASS;
                    p.set(x, y + HK + ly, z, s);
                } else if (d3 <= RK - 1.4) {
                    p.set(x, y + HK + ly, z, AIR);
                }
            }
        });

        // 열주 16개
        for (int k = 0; k < 16; k++) {
            int dx = (int) (pillars[k] >> 32), dz = (int) pillars[k];
            for (int ly = 1; ly <= GALLERIES[GALLERIES.length - 1]; ly++) {
                p.set(cx + dx, y + ly, cz + dz, isGallery(ly) ? CHISELED_QUARTZ : QUARTZ_PILLAR);
            }
        }
        // 버팀벽 16개 (회랑 지붕 위부터) + 첨탑 장식
        for (int k = 0; k < 16; k++) {
            double a = (k + 0.5) * Math.PI / 8;
            int rx = cx + (int) Math.round(Math.cos(a) * (RK + 1)), rz = cz + (int) Math.round(Math.sin(a) * (RK + 1));
            int latC = Math.min(Math.abs(rx - cx), Math.abs(rz - cz));
            int from = latC <= 10 ? 40 : 1;
            for (int ly = from; ly < HK; ly++) p.set(rx, y + ly, rz, ly % 16 == 0 ? CHISELED_QUARTZ : QUARTZ_PILLAR);
            for (int ly = HK + 1; ly <= HK + 4; ly++) p.set(rx, y + ly, rz, QUARTZ_PILLAR);
            p.set(rx, y + HK + 5, rz, MINT_CHISELED);
            p.set(rx, y + HK + 6, rz, END_ROD);
        }
    }

    private BlockState atriumFloor(double d, int latC, double latD) {
        for (int ring = 8; ring <= 12; ring += 2) if (Math.abs(d - ring) < 0.5) return MINT_CUT;
        for (int ring = 9; ring <= 11; ring += 2) if (Math.abs(d - ring) < 0.5) return CALCITE;
        if (d > 6.5 && (latC == 0 || latD < 0.5)) return MINT_CUT;
        return SMOOTH_QUARTZ;
    }

    /** 회랑을 잇는 나선 대계단 2개 (한 바퀴 = 한 층) */
    private void buildSpirals(Placer p, int y) {
        int top = GALLERIES[GALLERIES.length - 1];
        int[][] ring = Towers.ringCells(2);
        for (int[] s : SPIRALS) {
            int sx = cx + s[0], sz = cz + s[1];
            for (int ly = 1; ly <= top; ly++) {
                p.set(sx, y + ly, sz, ly % 16 == 8 ? LIGHT : QUARTZ_PILLAR);
                for (int ddx = -1; ddx <= 1; ddx++) {
                    for (int ddz = -1; ddz <= 1; ddz++) {
                        if (ddx == 0 && ddz == 0) continue;
                        p.set(sx + ddx, y + ly, sz + ddz, ly < top ? GLASS_PANE : SMOOTH_QUARTZ);
                    }
                }
            }
            for (int i = 0; i < top; i++) {
                int[] c0 = ring[i % ring.length], c1 = ring[(i + 1) % ring.length];
                p.set(sx + c0[0], y + i + 1, sz + c0[1], Deco.quartzStairs(Deco.toward(c1[0] - c0[0], c1[1] - c0[1])));
            }
        }
    }

    /** 1층 회랑(예배실)과 2~6층 원형 서고 꾸미기 */
    private void furnishGalleries(Placer p, int y) {
        int[] levels = {0, 16, 32, 48, 64, 80};
        for (int L : levels) {
            final int fy = y + L + 1;
            p.forColumns(cx - WALL_IN, cz - WALL_IN, cx + WALL_IN, cz + WALL_IN, (x, z) -> {
                int dx = x - cx, dz = z - cz;
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d <= ATRIUM || d > WALL_IN || inSpiral(dx, dz, 3) || isPillar(dx, dz)) return;
                if (L > 0 && d <= ATRIUM + 1) return; // 난간 자리
                int latC = Math.abs(dx) >= Math.abs(dz) ? Math.abs(dz) : Math.abs(dx);
                boolean keepClear = (L == 0 && latC <= 4) || ((L == 48 || L == 80) && latC <= 1);
                if (d <= 17.5 || keepClear) {
                    p.set(x, fy, z, L == 0 ? Deco.CARPET_MINT : Deco.CARPET_WHITE);
                    return;
                }
                int h = Shapes.hash(x, L, z, seed);
                Direction in = Deco.toward(-dx, -dz);
                if (L == 0) {
                    switch (h % 4) {
                        case 0 -> { p.set(x, fy, z, BOOKSHELF); p.set(x, fy + 1, z, BOOKSHELF); p.set(x, fy + 2, z, Deco.candles(4)); }
                        case 1 -> p.set(x, fy, z, Deco.lectern(in));
                        default -> p.set(x, fy, z, Deco.pot(h >> 4));
                    }
                } else if (h % 5 == 0) {
                    p.set(x, fy, z, Deco.lectern(in));
                } else if (h % 5 == 1) {
                    p.set(x, fy, z, Deco.pot(h >> 4));
                } else {
                    for (int i = 0; i < 3; i++) p.set(x, fy + i, z, BOOKSHELF);
                    if (h % 7 == 0) p.set(x, fy + 3, z, Deco.candles(3));
                }
            });
            if (L < 80) { // 다음 회랑 바닥에 매단 랜턴
                for (int k = 0; k < 8; k++) {
                    double a = (k + 0.5) * Math.PI / 4;
                    int lx = cx + (int) Math.round(Math.cos(a) * 15.5), lz = cz + (int) Math.round(Math.sin(a) * 15.5);
                    Deco.chandelier(p, lx, y + L + 16, lz, 9);
                }
            }
        }
    }

    /** 성소: 원형 제단, 다이아몬드 받침 위 신호기, 떠 있는 빛의 고리와 수정 */
    private void buildSanctum(Placer p, int y) {
        p.forColumns(cx - 7, cz - 7, cx + 7, cz + 7, (x, z) -> {
            int dx = x - cx, dz = z - cz;
            double d = Math.sqrt(dx * dx + dz * dz);
            Direction up = Deco.toward(-dx, -dz);
            if (d <= 6) p.set(x, y + 1, z, d > 5 ? Deco.quartzStairs(up) : SMOOTH_QUARTZ);
            if (d <= 4) p.set(x, y + 2, z, d > 3 ? Deco.quartzStairs(up) : CALCITE);
        });
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                p.set(cx + dx, y + 2, cz + dz, Blocks.DIAMOND_BLOCK.defaultBlockState());
                if (dx != 0 && dz != 0) p.set(cx + dx, y + 3, cz + dz, Deco.candles(4));
            }
        }
        p.set(cx, y + 3, cz, Blocks.BEACON.defaultBlockState());
        int[][] rods = {{2, 0}, {-2, 0}, {0, 2}, {0, -2}};
        for (int[] r : rods) p.set(cx + r[0], y + 3, cz + r[1], END_ROD);

        floatingRing(p, y + 40, 8.5, false);
        floatingRing(p, y + 72, 6.5, false);
        for (int k = 0; k < 8; k++) {
            double a = (k + 0.5) * Math.PI / 4;
            Shapes.crystal(p, cx + (int) Math.round(Math.cos(a) * 9), y + 24 + (k % 2) * 8, cz + (int) Math.round(Math.sin(a) * 9), 2);
        }
    }

    /** 공중에 떠 있는 빛의 고리 (가운데는 비워 신호기 빛줄기가 지나가게) */
    private void floatingRing(Placer p, int ry, double r, boolean rods) {
        int ext = (int) Math.ceil(r) + 1;
        p.forColumns(cx - ext, cz - ext, cx + ext, cz + ext, (x, z) -> {
            int dx = x - cx, dz = z - cz;
            double d = Math.sqrt(dx * dx + dz * dz);
            if (Math.abs(d - r) > 0.5) return;
            int arc = (int) Math.floor(Shapes.angle(dx, dz) * r);
            p.set(x, ry, z, arc % 4 == 0 ? MINT_CHISELED : CALCITE);
            if (arc % 4 == 2) p.set(x, ry - 1, z, rods ? END_ROD_DOWN : Deco.hangingLantern());
        });
    }

    /** 돔 꼭대기: 열린 왕관(기둥 8개 + 고리)과 유리 수정 — 빛줄기가 그대로 통과 */
    private void buildCrown(Placer p, int y) {
        int top = y + HK + RK;
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4;
            int px = cx + (int) Math.round(Math.cos(a) * 3.5), pz = cz + (int) Math.round(Math.sin(a) * 3.5);
            for (int ly = 0; ly < 8; ly++) p.set(px, top + ly, pz, QUARTZ_PILLAR);
        }
        p.forColumns(cx - 5, cz - 5, cx + 5, cz + 5, (x, z) -> {
            double d = Math.hypot(x - cx, z - cz);
            if (d > 2.5 && d <= 4.5) {
                p.set(x, top + 8, z, ((x + z) & 1) == 0 ? MINT_CHISELED : CALCITE);
                if (d > 3.5) p.set(x, top + 9, z, END_ROD);
            }
        });
        int n = 3, cyy = top + 16;
        p.forColumns(cx - n, cz - n, cx + n, cz + n, (x, z) -> {
            int h = Math.abs(x - cx) + Math.abs(z - cz);
            for (int dy = -n; dy <= n; dy++) {
                if (h + Math.abs(dy) <= n) p.set(x, cyy + dy, z, ((h + dy) & 1) == 0 ? GLASS : GLASS_WHITE);
            }
        });
    }
}
