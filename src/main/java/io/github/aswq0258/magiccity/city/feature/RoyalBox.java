package io.github.aswq0258.magiccity.city.feature;

import io.github.aswq0258.magiccity.build.Placer;
import io.github.aswq0258.magiccity.city.Deco;
import io.github.aswq0258.magiccity.city.Royal;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

import static io.github.aswq0258.magiccity.city.Palette.*;

/**
 * 왕성 건물의 껍데기 (르네상스 외관 + 층 바닥 + 지붕 난간).
 *  - 6칸 간격 벽기둥(금 기둥머리), 1층 띠무늬 석재, 층마다 아치창 + 금 쐐기돌, 꼭대기 금 띠, 처마 내물림
 *  - 지붕 난간: 기둥·난간·금 머리 조각상
 *  - 층 바닥은 대리석, 가운데 나선 계단 자리(core)는 비움
 */
public final class RoyalBox {
    public final int x0, z0, x1, z1, y, floors, fh, H;
    private final List<int[]> doors = new ArrayList<>(); // {side, center, halfWidth, ly0, height}
    private int coreX = Integer.MIN_VALUE, coreZ, coreR;
    private int holeX0 = Integer.MIN_VALUE, holeZ0, holeX1, holeZ1, holeFloor;
    private BlockState window = Royal.CLEAR_PANE;

    public RoyalBox(int x0, int z0, int x1, int z1, int y, int floors, int fh) {
        this.x0 = Math.min(x0, x1);
        this.z0 = Math.min(z0, z1);
        this.x1 = Math.max(x0, x1);
        this.z1 = Math.max(z0, z1);
        this.y = y;
        this.floors = floors;
        this.fh = fh;
        this.H = floors * fh;
    }

    /** side 쪽 외벽에 출입구. center: 그 벽을 따라가는 월드 좌표(x 또는 z) */
    public RoyalBox door(Direction side, int center, int halfWidth, int ly0, int height) {
        int s = switch (side) { case NORTH -> 0; case SOUTH -> 1; case WEST -> 2; default -> 3; };
        doors.add(new int[]{s, center, halfWidth, ly0, height});
        return this;
    }

    public RoyalBox core(int cx, int cz, int r) {
        this.coreX = cx;
        this.coreZ = cz;
        this.coreR = r;
        return this;
    }

    /** floorK 층 바닥에 계단 구멍 */
    public RoyalBox hole(int hx0, int hz0, int hx1, int hz1, int floorK) {
        this.holeX0 = Math.min(hx0, hx1);
        this.holeZ0 = Math.min(hz0, hz1);
        this.holeX1 = Math.max(hx0, hx1);
        this.holeZ1 = Math.max(hz0, hz1);
        this.holeFloor = floorK;
        return this;
    }

    public RoyalBox window(BlockState w) {
        this.window = w;
        return this;
    }

    private boolean inCore(int x, int z) {
        return coreX != Integer.MIN_VALUE && Math.max(Math.abs(x - coreX), Math.abs(z - coreZ)) <= coreR;
    }

    private int doorPart(int x, int z, int ly) {
        for (int[] d : doors) {
            boolean onSide = switch (d[0]) {
                case 0 -> z == z0;
                case 1 -> z == z1;
                case 2 -> x == x0;
                default -> x == x1;
            };
            if (!onSide) continue;
            int along = (d[0] <= 1) ? x : z;
            int off = Math.abs(along - d[1]);
            if (off > d[2]) continue;
            if (ly >= d[3] && ly < d[3] + d[4]) return 1;
            if (ly == d[3] + d[4]) return 2;
            if (ly == d[3] + d[4] + 1 && off == 0) return 3;
        }
        return 0;
    }

    public void build(Placer p) {
        if (!p.intersects(x0 - 2, z0 - 2, x1 + 2, z1 + 2)) return;
        p.forColumns(x0 - 1, z0 - 1, x1 + 1, z1 + 1, (x, z) -> {
            boolean out = x < x0 || x > x1 || z < z0 || z > z1;
            if (out) {
                Direction toWall = x < x0 ? Direction.EAST : x > x1 ? Direction.WEST : z < z0 ? Direction.SOUTH : Direction.NORTH;
                p.set(x, y + H, z, Deco.corbel(toWall));
                return;
            }
            boolean wall = x == x0 || x == x1 || z == z0 || z == z1;
            if (wall) {
                boolean corner = (x == x0 || x == x1) && (z == z0 || z == z1);
                int idx = (z == z0 || z == z1) ? x - x0 : z - z0;
                p.set(x, y, z, QUARTZ_BRICKS);
                for (int ly = 1; ly <= H; ly++) {
                    int dp = doorPart(x, z, ly);
                    p.set(x, y + ly, z, dp == 1 ? AIR : dp == 2 ? CHISELED_QUARTZ : dp == 3 ? Royal.GOLD : exterior(idx, ly, corner));
                }
                if (corner) {
                    Royal.pinnacle(p, x, z, y + H + 1, 5);              // 모서리: 높은 침탑
                } else if (idx % 10 == 5) {
                    Royal.pinnacle(p, x, z, y + H + 1, 2);              // 난간 기둥: 짧은 침탑
                } else if (idx % 10 == 0 && floors * fh >= 16) {
                    p.set(x, y + H + 1, z, CHISELED_QUARTZ);
                    p.set(x, y + H + 2, z, END_ROD);
                } else {
                    p.set(x, y + H + 1, z, idx % 2 == 0 ? CHISELED_QUARTZ : SMOOTH_QUARTZ_SLAB);
                }
                return;
            }
            p.set(x, y, z, Royal.marble(x, z));
            boolean core = inCore(x, z);
            boolean hole = holeX0 != Integer.MIN_VALUE && x >= holeX0 && x <= holeX1 && z >= holeZ0 && z <= holeZ1;
            for (int ly = 1; ly <= H; ly++) {
                BlockState s = AIR;
                if (ly == H) s = core ? AIR : (Math.floorMod(x, 6) == 3 && Math.floorMod(z, 6) == 3) ? LIGHT : SMOOTH_QUARTZ;
                else if (ly % fh == 0) s = (core || (hole && ly / fh == holeFloor)) ? AIR : Royal.marble(x, z);
                p.set(x, y + ly, z, s);
            }
        });
    }

    private BlockState exterior(int idx, int ly, boolean corner) {
        int k = (ly - 1) / fh, local = ly - k * fh;
        if (ly == H) return SMOOTH_QUARTZ;
        if (ly == H - 1) return Royal.GOLD;
        if (corner) return ((ly / 2) % 2 == 0) ? CHISELED_QUARTZ : QUARTZ_BRICKS;
        if (idx % 6 == 0) {
            if (ly == H - 2) return Royal.GOLD;
            if (ly == H - 3) return CHISELED_QUARTZ;
            return k == 0 ? ((ly & 1) == 0 ? SMOOTH_QUARTZ : QUARTZ_BRICKS) : QUARTZ_PILLAR;
        }
        if (local == fh && k < floors - 1) return CHISELED_QUARTZ;
        int m = idx % 6;
        if (m >= 2 && m <= 4) {
            int top = Math.max(3, fh - 3);
            if (local >= 2 && local <= top) return window;
            if (local == top + 1) return m == 3 ? window : CHISELED_QUARTZ;
            if (local == top + 2 && m == 3 && local < fh) return Royal.GOLD;
        }
        if (k == 0) return (ly & 1) == 0 ? SMOOTH_QUARTZ : QUARTZ_BRICKS;
        return SMOOTH_QUARTZ;
    }
}
