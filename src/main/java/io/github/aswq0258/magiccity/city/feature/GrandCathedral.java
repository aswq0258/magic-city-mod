package io.github.aswq0258.magiccity.city.feature;

import io.github.aswq0258.magiccity.build.Placer;
import io.github.aswq0258.magiccity.city.CityLayout;
import io.github.aswq0258.magiccity.city.Deco;
import io.github.aswq0258.magiccity.city.Palette;
import io.github.aswq0258.magiccity.city.Royal;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;

import static io.github.aswq0258.magiccity.city.Palette.*;

/**
 * 왕국 대성당 — 쾰른 대성당식 고딕
 *  - 짙은 심층암 몸체 + 흰 방해석 세로 갈비뼈·트레이서리·피너클 + 형형색색 스테인드글라스 + 녹청 구리 지붕
 *  - 서쪽 정면: 높이 60 쌍탑 + 투각 첨탑(꼭대기 약 +100), 가운데 뾰족 아치 정문과 박공(빔페르크), 거대한 서쪽 창
 *  - 신랑(높이 40) + 양옆 측랑(20) + 플라잉 버트레스, 십자 교차부 녹청 첨탑, 장미창 달린 익랑, 반원 후진
 *  - 내부: 아케이드 기둥, 신도석, 붉은 카펫, 금 제단, 영혼 랜턴 샹들리에
 *  - 정문 앞 성수 분수
 * 정면은 서쪽 고정. (x0, zc) = 정면 서쪽 끝 x, 가운데 z.
 */
public class GrandCathedral extends Feature {
    public static final int LEN = 61, HALF = 17;
    private static final int TH = 60, NH = 40, AH = 20, SPIRE = 34;
    private static final BlockState DARK = Blocks.DEEPSLATE_BRICKS.defaultBlockState();
    private static final BlockState DARK2 = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
    private static final BlockState DARK_TILE = Blocks.DEEPSLATE_TILES.defaultBlockState();
    private static final BlockState WHITE = CALCITE;
    private static final BlockState[] GLASS_COLORS = {
            Blocks.BLUE_STAINED_GLASS.defaultBlockState(), Blocks.RED_STAINED_GLASS.defaultBlockState(),
            Blocks.PURPLE_STAINED_GLASS.defaultBlockState(), Blocks.YELLOW_STAINED_GLASS.defaultBlockState(),
            Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState(), Blocks.MAGENTA_STAINED_GLASS.defaultBlockState(),
            Blocks.BLUE_STAINED_GLASS.defaultBlockState(), Blocks.ORANGE_STAINED_GLASS.defaultBlockState(),
    };
    private final int x0, zc;
    private final long seed;

    public GrandCathedral(int x0, int zc, long seed) {
        super(x0 - 16, zc - HALF - 2, x0 + LEN + 1, zc + HALF + 2);
        this.x0 = x0;
        this.zc = zc;
        this.seed = seed;
    }

    private int X(int a) {
        return x0 + a;
    }

    private int Z(int lat) {
        return zc - lat;
    }

    private void set(Placer p, int a, int ly, int lat, BlockState s) {
        p.set(X(a), groundY + ly, Z(lat), s);
    }

    private static BlockState glass(int a, int ly, int lat) {
        int i = Math.floorMod(a * 3 + ly * 5 + lat * 7 + (ly / 3) * 11, GLASS_COLORS.length);
        return GLASS_COLORS[i];
    }

    @Override
    public void build(Placer p, CityLayout city) {
        if (!p.intersects(minX, minZ, maxX, maxZ)) return;
        final int b = groundY;
        p.forColumns(X(0), Z(HALF + 1), X(LEN), Z(-HALF - 1), (x, z) -> {
            int a = x - x0, lat = zc - z, al = Math.abs(lat);
            column(p, a, lat, al, b);
        });
        // 쌍탑 투각 첨탑 + 피너클
        for (int s = -1; s <= 1; s += 2) {
            int tcLat = s * 12, tcA = 5;
            Royal.spire(p, X(tcA), Z(tcLat), b + TH + 1, 5.5, SPIRE, DARK_TILE, WHITE);
            for (int[] o : new int[][]{{0, -5}, {10, -5}, {0, 5}, {10, 5}}) {
                Royal.spire(p, X(o[0]), Z(tcLat + o[1]), b + TH + 1, 1.0, 9, DARK_TILE, WHITE);
            }
            Royal.spire(p, X(tcA), Z(tcLat), b + TH + SPIRE + 6, 0.6, 3, Royal.GOLD, Royal.GOLD);
        }
        // 교차부 녹청 첨탑
        Royal.spire(p, X(46), Z(0), b + NH + 9, 3.0, 24, MINT_BLOCK, Royal.GOLD);
        // 반원 후진 둘레 피너클
        for (int i = 0; i <= 6; i++) {
            double ang = -Math.PI / 2 + Math.PI * i / 6;
            int a = 52 + (int) Math.round(Math.cos(ang) * 9), lat = (int) Math.round(Math.sin(ang) * 9);
            for (int ly = 1; ly <= 30; ly++) set(p, a, ly, lat, ly > 26 ? WHITE : DARK);
            Royal.spire(p, X(a), Z(lat), b + 31, 0.8, 6, DARK_TILE, WHITE);
        }
        interior(p, b);
        holyFountain(p, b);
    }

    private void column(Placer p, int a, int lat, int al, int b) {
        // ── 서쪽 쌍탑
        if (a <= 10 && al >= 7) {
            int tl = al - 7;                           // 0..10
            boolean edge = a == 0 || a == 10 || tl == 0 || tl == 10;
            boolean corner = (a == 0 || a == 10) && (tl == 0 || tl == 10);
            int face = (a == 0 || a == 10) ? tl : a;   // 면 위의 위치
            set(p, a, 0, lat, DARK2);
            for (int ly = 1; ly <= TH; ly++) {
                BlockState s = AIR;
                if (edge) {
                    s = DARK;
                    if (corner) s = ly % 6 == 0 ? WHITE : DARK2;
                    else if (face % 3 == 0) s = WHITE;                                     // 흰 세로 갈비뼈
                    else if (ly >= 12 && ly <= 26 && (face == 4 || face == 5 || face == 7)) s = glass(a, ly, lat);
                    else if (ly >= 44 && ly <= 56 && (face == 4 || face == 5 || face == 7 || face == 8 || face == 2)) s = ly % 4 == 0 ? WHITE : Blocks.IRON_BARS.defaultBlockState();
                    if (ly == 30 || ly == 42 || ly == TH) s = WHITE;
                } else if (ly % 15 == 0) s = DARK_TILE;
                set(p, a, ly, lat, s);
            }
            if (edge && (a + lat) % 2 == 0) set(p, a, TH + 1, lat, WHITE);
            return;
        }
        // ── 정면 가운데 (정문 + 서쪽 창 + 박공)
        if (a == 0 && al <= 6) {
            set(p, a, 0, lat, DARK2);
            for (int ly = 1; ly <= 50; ly++) {
                BlockState s = DARK;
                if (al == 6 || al == 0 && ly > 40) s = WHITE;
                double arch = 10 + 4 * Math.sqrt(Math.max(0, 1 - Math.pow(al / 3.6, 2)));
                if (al <= 3 && ly <= arch) s = AIR;                                          // 뾰족 아치 정문
                else if (al <= 4 && ly <= arch + 1) s = WHITE;
                else if (ly >= 15 && ly <= 22 && ly - 15 >= al * 1.2) s = (ly + al) % 2 == 0 ? WHITE : DARK;   // 빔페르크
                else if (ly >= 24 && ly <= 38 && al <= 4) s = (al == 0 || ly == 31) ? WHITE : glass(a, ly, lat);   // 서쪽 창
                if (ly > 42 && ly - 42 > (6 - al) * 1.4) s = AIR;                            // 박공 지붕선
                set(p, a, ly, lat, s);
            }
            return;
        }
        if (a <= 10 && al <= 6) {                      // 현관(두 탑 사이)
            set(p, a, 0, lat, Royal.marble(X(a), Z(lat)));
            for (int ly = 1; ly <= NH; ly++) set(p, a, ly, lat, ly == NH ? DARK_TILE : AIR);
            return;
        }
        // ── 반원 후진 (a > 52)
        if (a > 52) {
            double d = Math.hypot(a - 52, lat);
            if (d > 8.5) {
                if (d <= 10.5 && al <= 12) { set(p, a, 0, lat, DARK2); }
                return;
            }
            set(p, a, 0, lat, Royal.marble(X(a), Z(lat)));
            boolean wall = d > 7.5;
            for (int ly = 1; ly <= NH; ly++) {
                BlockState s = AIR;
                if (wall) s = (ly >= 6 && ly <= 34 && ly % 12 != 0) ? glass(a, ly, lat) : (ly % 12 == 0 ? WHITE : DARK);
                else if (ly == NH) s = DARK_TILE;
                set(p, a, ly, lat, s);
            }
            // 후진 지붕 (반원뿔)
            for (int k = 1; k <= 8; k++) {
                double rk = 8.5 - k;
                if (d <= rk + 0.5 && d > rk - 0.6) set(p, a, NH + k, lat, MINT_BLOCK);
            }
            return;
        }
        boolean transept = a >= 41 && a <= 51;
        // ── 플라잉 버트레스 피어 (|lat| 15~16)
        if (!transept && al >= 15 && al <= 16) {
            if (a % 6 == 0 && a >= 12) {
                for (int ly = 0; ly <= 26; ly++) set(p, a, ly, lat, ly > 22 ? WHITE : DARK);
                if (al == 15) Royal.spire(p, X(a), Z(lat), groundY + 27, 0.7, 6, DARK_TILE, WHITE);
            }
            return;
        }
        if (!transept && al >= 13 && al <= 14) {
            if (a % 6 == 0 && a >= 12) {                   // 버트레스 아치 (피어 → 신랑 벽)
                for (int ly = 0; ly <= 2; ly++) set(p, a, 27 + (14 - al) + ly, lat, DARK);
            }
            set(p, a, 0, lat, DARK2);
            return;
        }
        // ── 익랑 (a 41~51, |lat| ≤ 17)
        if (transept) {
            if (al > 17) return;
            set(p, a, 0, lat, Royal.marble(X(a), Z(lat)));
            boolean endWall = al == 17, sideWall = (a == 41 || a == 51) && al >= 7;
            for (int ly = 1; ly <= NH; ly++) {
                BlockState s = AIR;
                if (endWall) {
                    int da = a - 46;
                    double rr = Math.hypot(da, ly - 26);
                    if (rr <= 5.5) s = rr < 1 ? WHITE : (Math.round(Math.atan2(ly - 26, da) * 4) % 2 == 0 ? WHITE : glass(a, ly, lat));   // 장미창
                    else if (Math.abs(da) <= 2 && ly <= 8) s = ly <= 7 && Math.abs(da) <= 1 ? AIR : WHITE;                                // 익랑 문
                    else s = (a == 41 || a == 51 || da % 3 == 0) ? WHITE : DARK;
                } else if (sideWall) {
                    s = (ly >= 22 && ly <= 34 && al % 3 != 0) ? glass(a, ly, lat) : (al % 3 == 0 ? WHITE : DARK);
                } else if (ly == NH) s = DARK_TILE;
                set(p, a, ly, lat, s);
            }
            // 익랑 지붕 (용마루가 남북 방향)
            int da = Math.abs(a - 46);
            for (int k = 0; k <= 6 - da; k++) set(p, a, NH + 1 + k, lat, k == 6 - da ? MINT_BLOCK
                    : (da == 6 - k ? Palette.mintStairs(a < 46 ? Direction.EAST : Direction.WEST) : MINT_BLOCK));
            if (endWall) for (int k = 1; k <= 6 - da; k++) set(p, a, NH + k, lat, (a + k) % 2 == 0 ? WHITE : DARK);   // 박공
            return;
        }
        // ── 신랑 + 측랑 (a 11~52, |lat| ≤ 12)
        if (al > 12) return;
        set(p, a, 0, lat, al <= 1 ? Royal.marble(X(a), Z(lat)) : ((a + lat) & 1) == 0 ? CALCITE : DARK2);
        boolean outerWall = al == 12 || (a == 52 && al >= 7), arcade = al == 6 && a < 52;
        int segment = Math.floorMod(a - 12, 6);
        for (int ly = 1; ly <= NH; ly++) {
            BlockState s = AIR;
            if (outerWall) {
                if (ly <= AH) s = (segment == 0) ? WHITE : (ly >= 4 && ly <= 16 && segment >= 2 && segment <= 4) ? glass(a, ly, lat) : DARK;
                else s = null;
            } else if (arcade) {
                if (ly <= AH) {
                    double archTop = AH - 3 + 2 * Math.sqrt(Math.max(0, 1 - Math.pow((segment - 3) / 3.0, 2)));
                    s = segment == 0 ? (ly % 5 == 0 ? WHITE : DARK2) : (ly > archTop ? DARK : AIR);
                } else {
                    s = (ly >= 23 && ly <= 36 && segment >= 1 && segment <= 5) ? (segment == 3 ? WHITE : glass(a, ly, lat))
                            : (segment == 0 ? WHITE : DARK);
                }
            } else if (al > 6) {
                if (ly == AH) s = DARK_TILE;                                          // 측랑 천장
                else if (ly > AH) s = null;
            } else if (ly == NH) s = (a % 6 == 3 && lat == 0) ? LIGHT : DARK_TILE;     // 신랑 볼트
            if (s != null) set(p, a, ly, lat, s);
        }
        // 측랑 외쪽 경사 지붕
        if (al > 6) {
            int k = 12 - al;                                                          // 0 (바깥) .. 5 (안)
            set(p, a, AH + 1 + k, lat, Palette.mintStairs(lat > 0 ? Direction.SOUTH : Direction.NORTH));
            if (outerWall && segment == 0) {
                set(p, a, AH + 1, lat, WHITE);
                set(p, a, AH + 2, lat, END_ROD);
            }
        }
        // 플라잉 버트레스 아치가 측랑 지붕 위를 넘어 신랑 벽에 닿음
        if (al >= 7 && a % 6 == 0 && a >= 12 && a < 52) {
            for (int ly = 0; ly <= 2; ly++) set(p, a, 27 + (14 - al) + ly, lat, ly == 2 ? WHITE : DARK);
        }
        // 신랑 맞배지붕
        if (al <= 7) {
            int k = 7 - al;
            set(p, a, NH + 1 + k, lat, al == 0 ? MINT_BLOCK : Palette.mintStairs(lat > 0 ? Direction.SOUTH : Direction.NORTH));
            if (al == 0 && a % 4 == 0) set(p, a, NH + 9, lat, Royal.GOLD);
        }
    }

    private void interior(Placer p, int b) {
        // 신도석·카펫·샹들리에
        for (int a = 12; a <= 39; a++) {
            for (int lat = -5; lat <= 5; lat++) {
                int al = Math.abs(lat);
                if (al <= 1) set(p, a, 1, lat, Royal.RED_CARPET);
                else if (a % 2 == 0 && a <= 37) set(p, a, 1, lat, Royal.chair(Direction.WEST));
            }
            if (a % 6 == 3) {
                Royal.chandelier(p, X(a), b + NH, Z(0), 10);
                for (int s = -1; s <= 1; s += 2) {
                    set(p, a, AH - 1, s * 9, Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
                    set(p, a, 1, s * 10, Deco.candles(3));
                }
            }
        }
        // 제단 (교차부 → 후진)
        for (int a = 48; a <= 58; a++) {
            for (int lat = -6; lat <= 6; lat++) {
                if (Math.hypot(Math.max(0, a - 52), lat) > 7.5) continue;
                int al = Math.abs(lat);
                int step = a >= 50 ? 2 : a >= 48 ? 1 : 0;
                for (int ly = 1; ly <= step; ly++) set(p, a, ly, lat, ly == step && (a == 48 || a == 50) ?
                        Blocks.POLISHED_DEEPSLATE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST) : DARK2);
                if (step == 2 && al <= 1 && a <= 52) set(p, a, 3, lat, Royal.RED_CARPET);
            }
        }
        for (int lat = -3; lat <= 3; lat++) {
            set(p, 54, 3, lat, Math.abs(lat) == 3 ? WHITE : Royal.GOLD);
            set(p, 54, 4, lat, Deco.candles(4));
        }
        set(p, 51, 3, 0, Deco.lectern(Direction.WEST));
        for (int ly = 3; ly <= 14; ly++) set(p, 57, ly, 0, ly == 14 ? Royal.GOLD : WHITE);   // 금 십자가 기둥
        for (int lat = -2; lat <= 2; lat++) set(p, 57, 11, lat, Royal.GOLD);
        set(p, 57, 15, 0, END_ROD);
        Royal.chandelier(p, X(46), b + NH, Z(0), 14);
        // 종 (양 탑 안)
        for (int s = -1; s <= 1; s += 2) {
            set(p, 5, 50, s * 12, Blocks.BELL.defaultBlockState().setValue(net.minecraft.world.level.block.BellBlock.ATTACHMENT,
                    net.minecraft.world.level.block.state.properties.BellAttachType.CEILING));
            set(p, 5, 51, s * 12, DARK);
        }
    }

    /** 정문 앞 성수 분수 (방해석 수반 + 금 + 빛) */
    private void holyFountain(Placer p, int b) {
        int fx = X(-8), fz = Z(0);
        p.forColumns(fx - 7, fz - 7, fx + 7, fz + 7, (x, z) -> {
            double d = Math.hypot(x - fx, z - fz);
            if (d > 7.5) return;
            p.set(x, b, z, d > 6.5 ? DARK2 : ((x + z) & 1) == 0 ? CALCITE : SMOOTH_QUARTZ);
            if (d <= 4.5 && d > 3.5) {
                p.set(x, b + 1, z, WHITE);
                p.set(x, b + 2, z, SMOOTH_QUARTZ_SLAB);
            } else if (d <= 3.5) {
                p.set(x, b, z, CALCITE);
                p.set(x, b + 1, z, d < 1 ? WHITE : WATER);
            }
        });
        for (int y = b + 2; y <= b + 4; y++) p.set(fx, y, fz, y == b + 4 ? Royal.GOLD : WHITE);
        p.set(fx, b + 5, fz, LIGHT);
        p.set(fx, b + 6, fz, WATER);
        for (int[] o : new int[][]{{-6, -6}, {6, -6}, {-6, 6}, {6, 6}}) Royal.lamp(p, fx + o[0], fz + o[1], b);
    }
}
