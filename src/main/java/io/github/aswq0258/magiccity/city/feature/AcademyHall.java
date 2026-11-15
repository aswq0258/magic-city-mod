package io.github.aswq0258.magiccity.city.feature;

import io.github.aswq0258.magiccity.build.Placer;
import io.github.aswq0258.magiccity.city.CityLayout;
import io.github.aswq0258.magiccity.city.Deco;
import io.github.aswq0258.magiccity.city.Shapes;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

import static io.github.aswq0258.magiccity.city.Palette.*;

/**
 * 마법 학원 강당 (17 x 13, 2층)
 *
 * 외관: 4칸 간격 석영 기둥(필라스터) 사이에 3칸짜리 아치형 큰 창, 민트 층띠, 처마 내물림,
 *       정면 열주 현관(기둥 4개 + 민트 띠 + 삼각 박공), 아치형 정문, 네 모서리 원형 첨탑, 지붕 위 쿠폴라
 * 1층 강의실: 체크무늬 바닥, 가운데 민트 카펫 통로, 책상과 의자 3줄, 교탁(독서대)과 마법부여대, 벽 책장과 촛불, 샹들리에
 * 2층 도서관: 서가 두 줄, 긴 열람 테이블, 독서대, 화분, 샹들리에, 석영 대계단과 난간
 *
 * 내부 좌표: u = 0..16 (왼쪽→오른쪽), v = 0..12 (앞→뒤), ly = 높이
 */
public class AcademyHall extends Feature {
    private static final int W = 17, D = 13, FH = 6, TOP = 12;
    private final Frame f;
    private final long seed;

    public AcademyHall(CityLayout city, CityLayout.Lot lot, RandomSource rand) {
        this(new Frame(lot.centerX(), lot.centerZ(), lot.front(), W, D), rand.nextLong());
    }

    private AcademyHall(Frame f, long seed) {
        super(f.minX - 4, f.minZ - 4, f.maxX + 4, f.maxZ + 4);
        this.f = f;
        this.seed = seed;
    }

    @Override
    public void build(Placer p, CityLayout city) {
        final int b = city.base;

        // ── 1. 벽, 바닥, 천장 ──
        for (int u = 0; u < W; u++) {
            for (int v = 0; v < D; v++) {
                if (!p.contains(f.x(u, v), f.z(u, v))) continue;
                boolean edge = u == 0 || u == W - 1 || v == 0 || v == D - 1;
                if (edge) {
                    f.set(p, b, u, 0, v, QUARTZ_BRICKS);
                    for (int ly = 1; ly <= TOP; ly++) f.set(p, b, u, ly, v, wallBlock(u, v, ly));
                } else {
                    f.set(p, b, u, 0, v, ((u + v) & 1) == 0 ? SMOOTH_QUARTZ : CALCITE);
                    for (int ly = 1; ly <= TOP; ly++) {
                        BlockState s = AIR;
                        if (ly == FH) s = (v == 11 && u >= 10 && u <= 14) ? AIR : Deco.PLANKS;
                        else if (ly == TOP) s = (u % 4 == 0 && v % 4 == 2) ? LIGHT : SMOOTH_QUARTZ;
                        f.set(p, b, u, ly, v, s);
                    }
                }
            }
        }

        // ── 2. 석영 대계단 (뒷벽을 따라) + 2층 난간 ──
        for (int k = 1; k <= FH; k++) f.set(p, b, 15 - k, k, 11, Deco.quartzStairs(f.left));
        for (int u = 10; u <= 14; u++) f.set(p, b, u, FH + 1, 10, Deco.FENCE);

        // ── 3. 1층 강의실 ──
        for (int v = 1; v <= 8; v++) {
            for (int u = 7; u <= 9; u++) f.set(p, b, u, 1, v, Deco.CARPET_MINT);
        }
        for (int v = 3; v <= 7; v += 2) {
            for (int u : new int[]{3, 4, 12, 13}) {
                Deco.table(p, f.x(u, v), b + 1, f.z(u, v));
                f.set(p, b, u, 1, v - 1, Deco.chair(f.front));
            }
        }
        f.set(p, b, 8, 1, 9, Deco.lectern(f.front));
        f.set(p, b, 6, 1, 9, ENCHANTING_TABLE);
        f.set(p, b, 10, 1, 9, ENCHANTING_TABLE);
        for (int u : new int[]{5, 6, 10, 11}) {
            f.set(p, b, u, 1, 10, BOOKSHELF);
            f.set(p, b, u, 2, 10, BOOKSHELF);
        }
        f.set(p, b, 5, 3, 10, Deco.candles(3));
        f.set(p, b, 11, 3, 10, Deco.candles(3));
        for (int u : new int[]{1, 15}) {
            for (int v = 2; v <= 10; v += 2) {
                if (v % 4 == 0) {
                    for (int ly = 1; ly <= 3; ly++) f.set(p, b, u, ly, v, BOOKSHELF);
                    f.set(p, b, u, 4, v, Deco.candles(2 + (v & 1)));
                } else {
                    f.set(p, b, u, 1, v, Deco.pot(Shapes.hash(u, 1, v, seed)));
                }
            }
        }
        int[][] lamps1 = {{4, 4}, {12, 4}, {4, 8}, {12, 8}, {8, 3}, {8, 7}};
        for (int[] l : lamps1) Deco.chandelier(p, f.x(l[0], l[1]), b + FH, f.z(l[0], l[1]), 1);

        // ── 4. 2층 도서관 ──
        int y2 = FH + 1;
        for (int u = 6; u <= 10; u++) {
            for (int v = 2; v <= 8; v++) f.set(p, b, u, y2, v, Deco.CARPET_WHITE);
        }
        for (int u : new int[]{3, 13}) {
            for (int v = 2; v <= 8; v++) {
                if (v == 5) continue;
                for (int ly = y2; ly < y2 + 3; ly++) f.set(p, b, u, ly, v, BOOKSHELF);
            }
            f.set(p, b, u, y2 + 3, 2, Deco.candles(4));
            f.set(p, b, u, y2 + 3, 8, Deco.candles(4));
        }
        for (int u = 7; u <= 9; u++) {
            Deco.table(p, f.x(u, 5), b + y2, f.z(u, 5));
            f.set(p, b, u, y2, 4, Deco.chair(f.front));
            f.set(p, b, u, y2, 6, Deco.chair(f.back));
        }
        f.set(p, b, 8, y2, 2, Deco.lectern(f.back));
        f.set(p, b, 1, y2, 1, Deco.pot((int) seed));
        f.set(p, b, 15, y2, 1, Deco.pot((int) (seed >> 8)));
        f.set(p, b, 1, y2, 11, Deco.pot((int) (seed >> 16)));
        int[][] lamps2 = {{5, 5}, {11, 5}, {8, 9}};
        for (int[] l : lamps2) Deco.chandelier(p, f.x(l[0], l[1]), b + TOP, f.z(l[0], l[1]), 1);

        // ── 5. 처마 내물림 ──
        for (int u = -1; u <= W; u++) {
            for (int v = -1; v <= D; v++) {
                if (u >= 0 && u < W && v >= 0 && v < D) continue;
                Direction toWall = v == -1 ? f.back : v == D ? f.front : u == -1 ? f.right : f.left;
                f.set(p, b, u, TOP, v, Deco.corbel(toWall));
            }
        }

        // ── 6. 열주 현관 + 삼각 박공 ──
        for (int u = 3; u <= 13; u++) {
            for (int v = -3; v <= -1; v++) {
                f.set(p, b, u, 0, v, SMOOTH_QUARTZ);
                f.set(p, b, u, 7, v, v == -3 ? MINT_CUT : SMOOTH_QUARTZ);
            }
        }
        for (int u : new int[]{4, 6, 10, 12}) {
            for (int ly = 1; ly <= 6; ly++) {
                f.set(p, b, u, ly, -3, (ly == 1 || ly == 6) ? CHISELED_QUARTZ : QUARTZ_PILLAR);
            }
        }
        for (int t = 0; ; t++) {
            int ly = 8 + t, uL = 3 + t, uR = 13 - t;
            if (uL > uR) break;
            for (int v = -3; v <= -1; v++) {
                boolean face = v == -3;
                if (uL == uR) {
                    f.set(p, b, uL, ly, v, face ? CHISELED_QUARTZ : MINT_SLAB);
                    continue;
                }
                f.set(p, b, uL, ly, v, face ? Deco.quartzStairs(f.right) : mintStairs(f.right));
                f.set(p, b, uR, ly, v, face ? Deco.quartzStairs(f.left) : mintStairs(f.left));
                if (face) {
                    for (int u = uL + 1; u < uR; u++) {
                        f.set(p, b, u, ly, v, (t == 2 && u == 8) ? MINT_CHISELED : QUARTZ_BRICKS);
                    }
                }
            }
        }
        Deco.chandelier(p, f.x(5, -2), b + 7, f.z(5, -2), 0);
        Deco.chandelier(p, f.x(11, -2), b + 7, f.z(11, -2), 0);

        // ── 7. 지붕 + 쿠폴라 + 모서리 첨탑 ──
        int top = Shapes.hipRoof(p, f.minX - 1, f.minZ - 1, f.maxX + 1, f.maxZ + 1, b + TOP + 1);
        Shapes.roundTower(p, f.centerX(), f.centerZ(), 2.0, top, 4, null);
        int[][] corners = {{f.minX - 1, f.minZ - 1}, {f.maxX + 1, f.minZ - 1}, {f.minX - 1, f.maxZ + 1}, {f.maxX + 1, f.maxZ + 1}};
        for (int[] c : corners) Shapes.roundTower(p, c[0], c[1], 2.0, b, TOP + 3, null);
    }

    private BlockState wallBlock(int u, int v, int ly) {
        boolean edgeV = v == 0 || v == D - 1;
        int along = edgeV ? u : v;
        int m = along % 4;

        if (v == 0 && u >= 7 && u <= 9) {                           // 아치형 정문
            if (ly <= 4) return AIR;
            if (ly == 5) return u == 8 ? AIR : CHISELED_QUARTZ;
        }
        if (m == 0) return (ly == FH || ly == TOP) ? CHISELED_QUARTZ : QUARTZ_PILLAR;
        if (ly == 1) return QUARTZ_BRICKS;
        if (ly == FH) return MINT_CUT;
        if (ly == TOP) return SMOOTH_QUARTZ;
        if ((ly >= 2 && ly <= 4) || (ly >= 8 && ly <= 10)) return GLASS_PANE;
        if (ly == 5 || ly == 11) return m == 2 ? GLASS_PANE : CHISELED_QUARTZ;
        return SMOOTH_QUARTZ;
    }
}
