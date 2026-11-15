package io.github.aswq0258.magiccity.city.feature;

import io.github.aswq0258.magiccity.build.Placer;
import io.github.aswq0258.magiccity.city.Deco;
import io.github.aswq0258.magiccity.city.Royal;
import io.github.aswq0258.magiccity.city.Shapes;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

import static io.github.aswq0258.magiccity.city.Palette.*;

/**
 * 르네상스식 궁전 건물 한 동 (3층, 층고 10).
 *
 * 외관: 1층은 석재를 쌓은 듯한 띠무늬(러스티케이션), 2~3층은 매끈한 벽에 3층을 관통하는 거대 기둥(필라스터)과
 *       금빛 기둥머리, 1층 아치창 + 금 쐐기돌, 2층(귀빈층) 높은 창 + 박공 장식, 3층 사각 창,
 *       층 사이 띠돌림, 꼭대기 금빛 띠와 처마 내물림, 지붕 위 난간과 금 머리 조각상, 모서리 귀돌
 * 내부 (ROOMS): 가운데 긴 복도(붉은 카펫 + 금색 테두리, 금 기둥머리 벽기둥, 샹들리에) 양옆으로 방
 *       — 침실(붉은 캐노피 침대), 서재, 식당, 살롱, 집무실, 보물고, 조각 전시실, 왕복 계단실
 * 내부 (BANQUET/BALLROOM): 3층 높이로 트인 대연회장 / 무도회장
 */
public final class PalaceBlock {
    public enum Mode { ROOMS, BANQUET, BALLROOM }

    private enum RoomType { BEDROOM, LIBRARY, DINING, SALON, STUDY, TREASURY, GALLERY, STAIRS }

    public static final int FH = 10, FLOORS = 3, H = FH * FLOORS, ROOM = 18;

    private final int x0, z0, x1, z1, py;
    private final boolean alongX;
    private final Mode mode;
    private final double ex, ez, er;
    private final long seed;
    private final int L, S, c, lastR;
    private final Direction dirL, dirS;
    private final List<int[]> doors = new ArrayList<>();

    public PalaceBlock(int x0, int z0, int x1, int z1, int py, Mode mode, double ex, double ez, double er, long seed) {
        this.x0 = x0;
        this.z0 = z0;
        this.x1 = x1;
        this.z1 = z1;
        this.py = py;
        this.mode = mode;
        this.ex = ex;
        this.ez = ez;
        this.er = er;
        this.seed = seed;
        this.alongX = (x1 - x0) >= (z1 - z0);
        this.L = alongX ? x1 - x0 + 1 : z1 - z0 + 1;
        this.S = alongX ? z1 - z0 + 1 : x1 - x0 + 1;
        this.c = S / 2;
        this.lastR = (L - 11) / ROOM;
        this.dirL = alongX ? Direction.EAST : Direction.SOUTH;
        this.dirS = alongX ? Direction.SOUTH : Direction.EAST;
    }

    /** 외벽에 출입구 (폭 2*hw+1, 높이 6, 아치 + 금 쐐기돌) */
    public PalaceBlock door(int x, int z, int hw) {
        doors.add(new int[]{x, z, hw});
        return this;
    }

    private int wx(int l, int s) {
        return alongX ? x0 + l : x0 + s;
    }

    private int wz(int l, int s) {
        return alongX ? z0 + s : z0 + l;
    }

    private boolean excluded(double x, double z) {
        return er > 0 && Math.hypot(x - ex, z - ez) <= er;
    }

    private int doorPart(int x, int z, int ly) {
        for (int[] d : doors) {
            int off = Math.abs(x - d[0]) + Math.abs(z - d[1]);
            if ((x == d[0] || z == d[1]) && off <= d[2]) {
                if (ly <= 6) return 1;
                if (ly == 7) return 2;
                if (ly == 8 && off == 0) return 3;
            }
        }
        return 0;
    }

    // ───────────────────────── 건축 ─────────────────────────

    public void build(Placer p) {
        if (!p.intersects(x0 - 2, z0 - 2, x1 + 2, z1 + 2)) return;
        p.forColumns(x0 - 1, z0 - 1, x1 + 1, z1 + 1, (x, z) -> {
            if (excluded(x, z)) return;
            boolean out = x < x0 || x > x1 || z < z0 || z > z1;
            if (out) {
                Direction toWall = x < x0 ? Direction.EAST : x > x1 ? Direction.WEST : z < z0 ? Direction.SOUTH : Direction.NORTH;
                p.set(x, py + H, z, Deco.corbel(toWall));
                return;
            }
            boolean wall = x == x0 || x == x1 || z == z0 || z == z1;
            if (wall) {
                boolean corner = (x == x0 || x == x1) && (z == z0 || z == z1);
                int idx = (z == z0 || z == z1) ? x - x0 : z - z0;
                p.set(x, py, z, QUARTZ_BRICKS);
                for (int ly = 1; ly <= H; ly++) {
                    int dp = doorPart(x, z, ly);
                    BlockState s = dp == 1 ? AIR : dp == 2 ? CHISELED_QUARTZ : dp == 3 ? Royal.GOLD : exterior(idx, ly, corner);
                    p.set(x, py + ly, z, s);
                }
                // 지붕 난간 + 조각상
                if (idx % 12 == 6) {
                    p.set(x, py + H + 1, z, QUARTZ_PILLAR);
                    p.set(x, py + H + 2, z, QUARTZ_PILLAR);
                    p.set(x, py + H + 3, z, Royal.GOLD);
                } else {
                    p.set(x, py + H + 1, z, (idx % 3 == 0 || corner) ? CHISELED_QUARTZ : SMOOTH_QUARTZ_SLAB);
                }
                return;
            }
            int l = alongX ? x - x0 : z - z0, s = alongX ? z - z0 : x - x0;
            p.set(x, py, z, Royal.marble(x, z));
            for (int ly = 1; ly <= H; ly++) {
                BlockState st = AIR;
                if (ly == H) st = (l % 6 == 3 && s % 6 == 3) ? LIGHT : SMOOTH_QUARTZ;
                else if (mode == Mode.ROOMS) {
                    if (ly % FH == 0) st = stairHole(l, s, ly / FH) ? AIR : Royal.DARK_PLANKS;
                    else st = partition(l, s, ly % FH);
                }
                p.set(x, py + ly, z, st);
            }
        });
        if (mode == Mode.ROOMS) {
            corridor(p);
            rooms(p);
        } else {
            hall(p);
        }
    }

    private BlockState exterior(int idx, int ly, boolean corner) {
        int k = (ly - 1) / FH, local = ly - k * FH;
        if (ly == H) return SMOOTH_QUARTZ;
        if (ly == H - 1) return Royal.GOLD;
        if (corner) return ((ly / 2) % 2 == 0) ? CHISELED_QUARTZ : QUARTZ_BRICKS;
        if (idx % 6 == 0) {
            if (ly == H - 2) return Royal.GOLD;
            if (ly == H - 3) return CHISELED_QUARTZ;
            return k == 0 ? ((ly & 1) == 0 ? SMOOTH_QUARTZ : QUARTZ_BRICKS) : QUARTZ_PILLAR;
        }
        if (local == FH && k < FLOORS - 1) return CHISELED_QUARTZ;
        int m = idx % 6;
        boolean bay = m >= 2 && m <= 4;
        if (bay) {
            switch (k) {
                case 0 -> {
                    if (local >= 3 && local <= 6) return Royal.CLEAR_PANE;
                    if (local == 7) return m == 3 ? Royal.CLEAR_PANE : CHISELED_QUARTZ;
                    if (local == 8 && m == 3) return Royal.GOLD;
                }
                case 1 -> {
                    if (local >= 2 && local <= 7) return Royal.CLEAR_PANE;
                    if (local == 8) return m == 3 ? Royal.GOLD : CHISELED_QUARTZ;
                    if (local == 9 && m == 3) return CHISELED_QUARTZ;
                }
                default -> {
                    if (local >= 3 && local <= 6) return Royal.CLEAR_PANE;
                }
            }
        }
        if (k == 0) return (ly & 1) == 0 ? SMOOTH_QUARTZ : QUARTZ_BRICKS;
        return SMOOTH_QUARTZ;
    }

    /** 복도 벽(금 기둥머리 벽기둥 + 아치 출입구)과 방 칸막이 */
    private BlockState partition(int l, int s, int local) {
        int ds = Math.abs(s - c);
        if (ds == 3) {
            int r = l % ROOM;
            boolean doorway = r >= 8 && r <= 10;
            if (doorway && local <= 5) return AIR;
            if (doorway && local == 6) return r == 9 ? Royal.GOLD : CHISELED_QUARTZ;
            if (l % 6 == 0) return local == 9 ? Royal.GOLD : QUARTZ_PILLAR;
            return local == 9 ? CHISELED_QUARTZ : SMOOTH_QUARTZ;
        }
        if (ds > 3 && l % ROOM == 0) return local == 9 ? CHISELED_QUARTZ : SMOOTH_QUARTZ;
        return AIR;
    }

    // ───────────────────────── 계단실 ─────────────────────────

    /** 계단실: 0번 방(왼쪽 줄)과 마지막 방(오른쪽 줄). 반환: {l0, s0} */
    private int[][] stairRooms() {
        return new int[][]{{1, 1}, {lastR * ROOM + 1, c + 4}};
    }

    private boolean stairHole(int l, int s, int floorK) {
        int flight = floorK - 1;
        boolean laneA = (flight % 2) == 0;
        for (int[] r : stairRooms()) {
            int laneS0 = r[1] + (laneA ? 1 : 5);
            if (s < laneS0 || s > laneS0 + 2) continue;
            for (int j = 1; j <= FH - 1; j++) {
                int lj = laneA ? r[0] + 1 + j : r[0] + 12 - j;
                if (l == lj) return true;
            }
        }
        return false;
    }

    private void stairs(Placer p, int l0, int s0) {
        for (int k = 0; k + 1 < FLOORS; k++) {
            boolean laneA = (k % 2) == 0;
            int laneS0 = s0 + (laneA ? 1 : 5);
            Direction up = laneA ? dirL : dirL.getOpposite();
            int base = py + k * FH;
            for (int j = 1; j <= FH; j++) {
                int l = laneA ? l0 + 1 + j : l0 + 12 - j;
                for (int ds = 0; ds < 3; ds++) p.set(wx(l, laneS0 + ds), base + j, wz(l, laneS0 + ds), Royal.whiteStairs(up));
            }
            // 위층 난간 (두 계단 사이)
            for (int l = l0 + 2; l <= l0 + 10; l++) p.set(wx(l, s0 + 4), base + FH + 1, wz(l, s0 + 4), Royal.DARK_FENCE);
        }
        for (int k = 0; k < FLOORS; k++) {
            int y = py + k * FH;
            Royal.chandelier(p, wx(l0 + 14, s0 + 4), y + FH, wz(l0 + 14, s0 + 4), 3);
        }
    }

    // ───────────────────────── 복도 ─────────────────────────

    private void corridor(Placer p) {
        for (int k = 0; k < FLOORS; k++) {
            int fy = py + k * FH + 1;
            for (int l = 1; l <= L - 2; l++) {
                for (int s = c - 2; s <= c + 2; s++) {
                    int x = wx(l, s), z = wz(l, s);
                    if (!p.contains(x, z) || excluded(x, z)) continue;
                    p.set(x, fy, z, Math.abs(s - c) == 2 ? Royal.GOLD_CARPET : Royal.RED_CARPET);
                }
                if (l % 9 == 4 && !excluded(wx(l, c), wz(l, c))) Royal.chandelier(p, wx(l, c), fy + FH - 1, wz(l, c), 2);
            }
        }
    }

    // ───────────────────────── 방 ─────────────────────────

    private void rooms(Placer p) {
        int[][] stairs = stairRooms();
        for (int r = 0; r <= lastR; r++) {
            int l0 = r * ROOM + 1, l1 = Math.min(r * ROOM + ROOM - 1, L - 2);
            if (l1 - l0 < 8) continue;
            for (int side = 0; side < 2; side++) {
                int s0 = side == 0 ? 1 : c + 4, s1 = side == 0 ? c - 4 : S - 2;
                int cxr = wx((l0 + l1) / 2, (s0 + s1) / 2), czr = wz((l0 + l1) / 2, (s0 + s1) / 2);
                if (er > 0 && Math.hypot(cxr - ex, czr - ez) < er + 6) continue;
                if (excluded(wx(l0, s0), wz(l0, s0)) || excluded(wx(l1, s1), wz(l1, s1))
                        || excluded(wx(l0, s1), wz(l0, s1)) || excluded(wx(l1, s0), wz(l1, s0))) continue;
                if (!p.intersects(Math.min(wx(l0, s0), wx(l1, s1)) - 3, Math.min(wz(l0, s0), wz(l1, s1)) - 3,
                        Math.max(wx(l0, s0), wx(l1, s1)) + 3, Math.max(wz(l0, s0), wz(l1, s1)) + 3)) continue;
                boolean isStair = (stairs[0][0] == l0 && stairs[0][1] == s0) || (stairs[1][0] == l0 && stairs[1][1] == s0);
                if (isStair) {
                    stairs(p, l0, s0);
                    continue;
                }
                for (int k = 0; k < FLOORS; k++) {
                    RoomType t = pick(r, side, k);
                    furnish(p, t, l0, l1, s0, s1, side, k);
                }
            }
        }
    }

    private RoomType pick(int r, int side, int k) {
        int h = Shapes.hash(r, k, side, seed);
        if (k == FLOORS - 1) return (h % 3 == 0) ? RoomType.STUDY : RoomType.BEDROOM;
        RoomType[] pool = k == 0
                ? new RoomType[]{RoomType.DINING, RoomType.SALON, RoomType.GALLERY, RoomType.LIBRARY, RoomType.SALON, RoomType.TREASURY}
                : new RoomType[]{RoomType.LIBRARY, RoomType.SALON, RoomType.STUDY, RoomType.BEDROOM, RoomType.GALLERY, RoomType.DINING};
        return pool[h % pool.length];
    }

    private void furnish(Placer p, RoomType t, int l0, int l1, int s0, int s1, int side, int k) {
        final int fy = py + k * FH + 1, ceil = py + (k + 1) * FH;
        final int sf = side == 0 ? s0 : s1;          // 바깥벽 쪽
        final int sd = side == 0 ? 1 : -1;           // 복도 쪽으로 한 칸
        final int sn = side == 0 ? s1 : s0;          // 복도 쪽 끝
        final int lm = (l0 + l1) / 2, sm = (s0 + s1) / 2;
        final Direction towardFar = side == 0 ? dirS.getOpposite() : dirS;
        final Direction towardNear = towardFar.getOpposite();
        Put put = (l, s, dy, st) -> p.set(wx(l, s), fy + dy, wz(l, s), st);

        switch (t) {
            case BEDROOM -> {
                rug(p, lm - 4, sf + sd * 3, lm + 5, sf + sd * 8, fy);
                for (int i = 0; i <= 1; i++) {
                    put.at(lm + i, sf, 0, Royal.redBed(towardFar, true));
                    put.at(lm + i, sf + sd, 0, Royal.redBed(towardFar, false));
                }
                int[][] posts = {{lm - 1, sf}, {lm + 2, sf}, {lm - 1, sf + 2 * sd}, {lm + 2, sf + 2 * sd}};
                for (int[] q : posts) for (int dy = 0; dy <= 2; dy++) put.at(q[0], q[1], dy, Royal.DARK_FENCE);
                for (int l = lm - 1; l <= lm + 2; l++) {
                    for (int s = sf; s != sf + 3 * sd; s += sd) {
                        boolean edge = l == lm - 1 || l == lm + 2 || s == sf || s == sf + 2 * sd;
                        put.at(l, s, 3, edge ? Royal.GOLD : Royal.RED_WOOL);
                    }
                }
                for (int l : new int[]{lm - 2, lm + 3}) {
                    put.at(l, sf, 0, Deco.BARREL);
                    put.at(l, sf, 1, Deco.candles(3));
                }
                for (int s = sf + sd; s != sf + 5 * sd; s += sd) put.at(l0, s, 0, Deco.chest(dirL));
                put.at(l0, sf, 0, BOOKSHELF);
                put.at(l0, sf, 1, BOOKSHELF);
                Royal.table(p, wx(l1, sf + 2 * sd), fy, wz(l1, sf + 2 * sd));
                put.at(l1 - 1, sf + 2 * sd, 0, Royal.chair(dirL.getOpposite()));
                put.at(l1, sf + 4 * sd, 0, Deco.pot(Shapes.hash(l1, k, sf, seed)));
                put.at(l0, sn, 0, Deco.pot(Shapes.hash(l0, k, sn, seed)));
                Royal.chandelier(p, wx(lm, sm), ceil, wz(lm, sm), 3);
            }
            case LIBRARY -> {
                rug(p, l0 + 1, sm - 1, l1 - 1, sm + 1, fy);
                for (int l = l0; l <= l1; l++) for (int dy = 0; dy <= 3; dy++) put.at(l, sf, dy, BOOKSHELF);
                for (int s = sf; s != sn; s += sd) {
                    for (int dy = 0; dy <= 3; dy++) {
                        put.at(l0, s, dy, BOOKSHELF);
                        put.at(l1, s, dy, BOOKSHELF);
                    }
                }
                for (int s = sf + 3 * sd; Math.abs(s - sn) >= 4; s += 4 * sd) {
                    if (Math.abs(s - sm) <= 1) continue;
                    for (int l = l0 + 3; l <= l1 - 3; l++) {
                        if (l == lm) continue;
                        for (int dy = 0; dy <= 2; dy++) put.at(l, s, dy, BOOKSHELF);
                    }
                    put.at(l0 + 2, s, 0, Deco.lectern(dirL));
                }
                for (int l = lm - 1; l <= lm + 1; l++) {
                    Royal.table(p, wx(l, sm), fy, wz(l, sm));
                    put.at(l, sm - 1, 0, Royal.chair(dirS.getOpposite()));
                    put.at(l, sm + 1, 0, Royal.chair(dirS));
                }
                Royal.chandelier(p, wx(lm, sm), ceil, wz(lm, sm), 3);
            }
            case DINING -> {
                rug(p, l0 + 2, sm - 3, l1 - 2, sm + 3, fy);
                BlockState top = Deco.topSlab(Blocks.DARK_OAK_SLAB);
                for (int l = l0 + 4; l <= l1 - 4; l++) {
                    put.at(l, sm, 0, top);
                    if (l % 3 == 0) put.at(l, sm, 1, Deco.candles(3));
                    put.at(l, sm - 1, 0, Royal.chair(dirS.getOpposite()));
                    put.at(l, sm + 1, 0, Royal.chair(dirS));
                }
                put.at(l0 + 3, sm, 0, Royal.chair(dirL.getOpposite()));
                put.at(l1 - 3, sm, 0, Royal.chair(dirL));
                for (int l = l0 + 1; l <= l1 - 1; l += 3) {
                    put.at(l, sf, 0, Deco.BARREL);
                    put.at(l, sf, 1, Deco.candles(4));
                }
                Royal.chandelier(p, wx(lm - 4, sm), ceil, wz(lm - 4, sm), 3);
                Royal.chandelier(p, wx(lm + 4, sm), ceil, wz(lm + 4, sm), 3);
            }
            case SALON -> {
                for (int g = -1; g <= 1; g += 2) {
                    int gl = lm + g * 4;
                    rug(p, gl - 2, sm - 2, gl + 2, sm + 2, fy);
                    Royal.table(p, wx(gl, sm), fy, wz(gl, sm));
                    put.at(gl - 1, sm, 0, Royal.chair(dirL.getOpposite()));
                    put.at(gl + 1, sm, 0, Royal.chair(dirL));
                    put.at(gl, sm - 1, 0, Royal.chair(dirS.getOpposite()));
                    put.at(gl, sm + 1, 0, Royal.chair(dirS));
                }
                for (int l = l0; l <= l1; l += 2) {
                    put.at(l, sf, 0, BOOKSHELF);
                    put.at(l, sf, 1, BOOKSHELF);
                    put.at(l, sf, 2, Deco.candles(2));
                    if (l + 1 <= l1) put.at(l + 1, sf, 0, Deco.pot(Shapes.hash(l, k, sf, seed)));
                }
                Royal.chandelier(p, wx(lm, sm), ceil, wz(lm, sm), 3);
            }
            case STUDY -> {
                rug(p, lm - 3, sf + sd * 2, lm + 3, sf + sd * 7, fy);
                for (int l = lm - 1; l <= lm + 1; l++) Royal.table(p, wx(l, sf + 2 * sd), fy, wz(l, sf + 2 * sd));
                put.at(lm, sf + 3 * sd, 0, Royal.chair(towardNear));
                for (int l = l0; l <= l1; l++) {
                    if (Math.abs(l - lm) <= 2) continue;
                    for (int dy = 0; dy <= 2; dy++) put.at(l, sf, dy, BOOKSHELF);
                }
                put.at(lm, sf + 6 * sd, 0, ENCHANTING_TABLE);
                put.at(l0 + 1, sm, 0, Blocks.CARTOGRAPHY_TABLE.defaultBlockState());
                put.at(l1 - 1, sm, 0, Deco.lectern(dirL));
                put.at(l1 - 1, sf + sd, 0, Deco.chest(dirL.getOpposite()));
                Royal.chandelier(p, wx(lm, sm), ceil, wz(lm, sm), 3);
            }
            case TREASURY -> {
                for (int l = l0 + 2; l <= l1 - 2; l += 3) {
                    for (int s = sf + 2 * sd; Math.abs(s - sn) >= 2; s += 3 * sd) {
                        put.at(l, s, 0, CHISELED_QUARTZ);
                        put.at(l, s, 1, Royal.TREASURES[Shapes.hash(l, k, s, seed) % Royal.TREASURES.length]);
                    }
                }
                for (int s = sf; s != sn; s += sd) {
                    put.at(l0, s, 0, Deco.chest(dirL));
                    put.at(l1, s, 0, Deco.chest(dirL.getOpposite()));
                }
                for (int l : new int[]{l0, l1}) {
                    put.at(l, sf, 0, Royal.GOLD);
                    put.at(l, sf, 1, Royal.GOLD);
                }
                Royal.chandelier(p, wx(lm, sm), ceil, wz(lm, sm), 3);
            }
            case GALLERY -> {
                rug(p, l0 + 1, sm - 1, l1 - 1, sm + 1, fy);
                for (int l = l0 + 1; l <= l1 - 1; l += 3) {
                    for (int s : new int[]{sf + sd, sn - sd}) {
                        put.at(l, s, 0, CHISELED_QUARTZ);
                        put.at(l, s, 1, QUARTZ_PILLAR);
                        put.at(l, s, 2, Royal.TREASURES[Shapes.hash(l, k + 3, s, seed) % Royal.TREASURES.length]);
                    }
                }
                Royal.chandelier(p, wx(lm - 4, sm), ceil, wz(lm - 4, sm), 3);
                Royal.chandelier(p, wx(lm + 4, sm), ceil, wz(lm + 4, sm), 3);
            }
            case STAIRS -> { }
        }
    }

    @FunctionalInterface
    private interface Put {
        void at(int l, int s, int dy, BlockState st);
    }

    private void rug(Placer p, int la, int sa, int lb, int sb, int y) {
        Royal.rug(p, wx(la, sa), wz(la, sa), wx(lb, sb), wz(lb, sb), y);
    }

    // ───────────────────────── 대연회장 / 무도회장 ─────────────────────────

    private void hall(Placer p) {
        final int fy = py + 1, ceil = py + H;
        for (int l = 1; l <= L - 2; l++) {
            for (int s = 1; s <= S - 2; s++) {
                int x = wx(l, s), z = wz(l, s);
                if (!p.contains(x, z)) continue;
                if (mode == Mode.BALLROOM && (l % 8 == 4 && s % 8 == 4)) p.set(x, py, z, Royal.GOLD);
                if ((s == 3 || s == S - 4) && l % 6 == 3) {
                    for (int ly = 1; ly < H; ly++) {
                        p.set(x, py + ly, z, ly == 1 ? CHISELED_QUARTZ : ly == H - 1 ? Royal.GOLD : ly == H - 2 ? CHISELED_QUARTZ : QUARTZ_PILLAR);
                    }
                    continue;
                }
                if (Math.abs(s - c) <= 2 && l >= 2 && l <= L - 3) {
                    p.set(x, fy, z, Math.abs(s - c) == 2 ? Royal.GOLD_CARPET : Royal.RED_CARPET);
                }
            }
        }
        for (int l = 4; l <= L - 5; l += 8) Royal.chandelier(p, wx(l, c), ceil, wz(l, c), 10);
        for (int l = 8; l <= L - 9; l += 12) {
            Royal.chandelier(p, wx(l, 7), ceil, wz(l, 7), 6);
            Royal.chandelier(p, wx(l, S - 8), ceil, wz(l, S - 8), 6);
        }

        if (mode == Mode.BANQUET) {
            BlockState top = Deco.topSlab(Blocks.DARK_OAK_SLAB);
            for (int lane : new int[]{c - 6, c, c + 6}) {
                for (int l = 6; l <= L - 7; l++) {
                    p.set(wx(l, lane), fy, wz(l, lane), top);
                    if (l % 3 == 0) p.set(wx(l, lane), fy + 1, wz(l, lane), Deco.candles(4));
                    p.set(wx(l, lane - 1), fy, wz(l, lane - 1), Royal.chair(dirS.getOpposite()));
                    p.set(wx(l, lane + 1), fy, wz(l, lane + 1), Royal.chair(dirS));
                }
            }
            for (int s = c - 1; s <= c + 1; s++) {               // 상석
                p.set(wx(4, s), fy, wz(4, s), s == c ? Royal.GOLD : Royal.chair(dirL.getOpposite()));
            }
            for (int l = 3; l <= L - 4; l += 4) {                 // 양쪽 벽 찬장
                for (int s : new int[]{1, S - 2}) {
                    p.set(wx(l, s), fy, wz(l, s), Deco.BARREL);
                    p.set(wx(l, s), fy + 1, wz(l, s), Deco.candles(4));
                    p.set(wx(l + 1, s), fy, wz(l + 1, s), Deco.WATER_CAULDRON);
                }
            }
        } else {
            for (int l = L - 7; l <= L - 2; l++) {                // 악단 무대
                for (int s = c - 6; s <= c + 6; s++) {
                    p.set(wx(l, s), fy, wz(l, s), SMOOTH_QUARTZ_SLAB);
                    if (l == L - 3 && (s - c) % 3 == 0) {
                        p.set(wx(l, s), fy + 1, wz(l, s), Blocks.NOTE_BLOCK.defaultBlockState());
                    }
                }
            }
            p.set(wx(L - 2, c), fy + 1, wz(L - 2, c), Blocks.JUKEBOX.defaultBlockState());
            for (int l = 2; l <= L - 9; l += 2) {                 // 벽을 따라 놓인 의자와 화분
                for (int s : new int[]{1, S - 2}) {
                    Direction back = s == 1 ? dirS.getOpposite() : dirS;
                    p.set(wx(l, s), fy, wz(l, s), (l % 4 == 0) ? Deco.pot(Shapes.hash(l, 1, s, seed)) : Royal.chair(back));
                }
            }
        }
    }
}
