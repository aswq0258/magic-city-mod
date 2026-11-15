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
 * 왕성 1단 테라스(+8) 위의 건물들.
 *  THRONE   C02 왕좌의 전당: 높이 22 대전, 3단 옥좌 + 붉은 천개, 열주, 뒤쪽 +18 회랑(2단 테라스와 연결), 금 돔
 *  COUNCIL  C03 왕실 의회실: 1층 회의실(긴 탁자·상석) / 2층 집무실
 *  CHAPEL   C04 왕실 예배당: 높이 18, 금빛 스테인드글라스, 신도석, 금 제단, 금 돔
 *  VAULT    C05 금고·서고: 1층 보물고 / 2층 문서고
 *  ARCANE   C06 왕궁 마법서고: 1층 마법부여실·연금술 / 2층 서고
 *  GUARD    C07 근위대 병영: 1층 식당·무기 손질 / 2층 침상
 *  ARMORY   C08 왕립 무기고: 1·2층 모루·숫돌·무기 보관
 */
public class RoyalHall extends Feature {
    public enum Kind { THRONE, COUNCIL, CHAPEL, VAULT, ARCANE, GUARD, ARMORY }

    private final Kind kind;
    private final Direction front;
    private final int lift, floors, fh;
    private final long seed;

    public RoyalHall(Kind kind, int x0, int z0, int x1, int z1, Direction front, int lift, long seed) {
        super(Math.min(x0, x1), Math.min(z0, z1), Math.max(x0, x1), Math.max(z0, z1));
        this.kind = kind;
        this.front = front;
        this.lift = lift;
        this.seed = seed;
        this.floors = (kind == Kind.THRONE || kind == Kind.CHAPEL) ? 1 : 2;
        this.fh = kind == Kind.THRONE ? 22 : kind == Kind.CHAPEL ? 18 : 9;
    }

    @Override
    public boolean occupies(int x, int z) {
        return x >= minX - 2 && x <= maxX + 2 && z >= minZ - 2 && z <= maxZ + 2;
    }

    @Override
    public void build(Placer p, CityLayout city) {
        if (!p.intersects(minX - 12, minZ - 12, maxX + 12, maxZ + 12)) return;
        final int y = groundY + lift;
        boolean ns = front == Direction.NORTH || front == Direction.SOUTH;
        int W = ns ? maxX - minX - 1 : maxZ - minZ - 1;
        int D = ns ? maxZ - minZ - 1 : maxX - minX - 1;
        Frame f = new Frame((minX + 1 + maxX - 1) >> 1, (minZ + 1 + maxZ - 1) >> 1, front, W, D);
        int doorAlong = ns ? f.x(W / 2, 0) : f.z(W / 2, 0);

        RoyalBox box = new RoyalBox(minX, minZ, maxX, maxZ, y, floors, fh)
                .door(front, doorAlong, kind == Kind.THRONE ? 2 : 1, 1, kind == Kind.THRONE ? 8 : 4);
        if (kind == Kind.THRONE) box.door(front.getOpposite(), doorAlong, 1, 11, 3);
        if (kind == Kind.CHAPEL) box.window(Royal.GOLD_PANE);
        if (floors > 1) box.hole(f.x(0, 1), f.z(0, 1), f.x(1, fh - 1), f.z(1, fh - 1), 1);
        box.build(p);

        // 계단 (왼쪽 벽을 따라)
        if (floors > 1) {
            for (int j = 1; j <= fh; j++) {
                for (int u = 0; u <= 1; u++) f.set(p, y, u, j, j, Royal.whiteStairs(f.back));
            }
            for (int v = 1; v < fh; v++) f.set(p, y, 2, fh + 1, v, Royal.DARK_FENCE);
        }

        final int cu = W / 2;
        RoyalRooms.Blocked stairs = (u, v) -> floors > 1 && u <= 2 && v <= fh + 1;
        switch (kind) {
            case THRONE -> throne(p, f, y, W, D);
            case COUNCIL -> {
                RoyalRooms.furnish(p, RoyalRooms.Kind.COUNCIL, f, y, fh, seed, (u, v) -> stairs.at(u, v) || (Math.abs(u - cu) <= 1 && v <= 1));
                RoyalRooms.furnish(p, RoyalRooms.Kind.STUDY, f, y + fh, fh, seed + 1, stairs);
            }
            case CHAPEL -> RoyalRooms.furnish(p, RoyalRooms.Kind.CHAPEL, f, y, fh, seed, (u, v) -> Math.abs(u - cu) <= 1 && v <= 1);
            case VAULT -> {
                RoyalRooms.furnish(p, RoyalRooms.Kind.TREASURY, f, y, fh, seed, (u, v) -> stairs.at(u, v) || (Math.abs(u - cu) <= 1 && v <= 1));
                RoyalRooms.furnish(p, RoyalRooms.Kind.ARCHIVE, f, y + fh, fh, seed + 1, stairs);
            }
            case ARCANE -> {
                RoyalRooms.furnish(p, RoyalRooms.Kind.ARCANE, f, y, fh, seed, (u, v) -> stairs.at(u, v) || (Math.abs(u - cu) <= 1 && v <= 1));
                RoyalRooms.furnish(p, RoyalRooms.Kind.LIBRARY, f, y + fh, fh, seed + 1, stairs);
            }
            case GUARD -> {
                RoyalRooms.furnish(p, RoyalRooms.Kind.MESS, f, y, fh, seed, (u, v) -> stairs.at(u, v) || (Math.abs(u - cu) <= 1 && v <= 1));
                RoyalRooms.furnish(p, RoyalRooms.Kind.DORM, f, y + fh, fh, seed + 1, stairs);
            }
            case ARMORY -> {
                RoyalRooms.furnish(p, RoyalRooms.Kind.ARMORY, f, y, fh, seed, (u, v) -> stairs.at(u, v) || (Math.abs(u - cu) <= 1 && v <= 1));
                RoyalRooms.furnish(p, RoyalRooms.Kind.ARMORY, f, y + fh, fh, seed + 1, stairs);
            }
        }

        // 문 앞 장식: 양옆 왕성 가로등 + 붉은 깃발
        int ox = f.x(cu, -1) + front.getStepX(), oz = f.z(cu, -1) + front.getStepZ();
        int sx = -front.getStepZ(), sz = front.getStepX();
        int off = kind == Kind.THRONE ? 5 : 3;
        Royal.lamp(p, ox + sx * off, oz + sz * off, y);
        Royal.lamp(p, ox - sx * off, oz - sz * off, y);
        p.set(f.x(cu - off + 1, -1), y + 6, f.z(cu - off + 1, -1), Royal.redBanner(front));
        p.set(f.x(cu + off - 1, -1), y + 6, f.z(cu + off - 1, -1), Royal.redBanner(front));

        if (kind == Kind.CHAPEL || kind == Kind.THRONE) {
            dome(p, (minX + maxX) >> 1, (minZ + maxZ) >> 1, y + floors * fh + 1, kind == Kind.THRONE ? 8 : 6);
        }
    }

    /** 왕좌의 전당 내부 */
    private void throne(Placer p, Frame f, int y, int W, int D) {
        final int cu = W / 2;
        // 붉은 카펫
        for (int v = 0; v <= D - 9; v++) {
            for (int du = -3; du <= 3; du++) f.set(p, y, cu + du, 1, v, Math.abs(du) == 3 ? Royal.GOLD_CARPET : Royal.RED_CARPET);
        }
        // 열주
        for (int u : new int[]{5, W - 6}) {
            for (int v = 2; v <= D - 9; v += 4) {
                for (int ly = 1; ly < fh; ly++) f.set(p, y, u, ly, v, ly == 1 ? CHISELED_QUARTZ : ly == fh - 2 ? Royal.GOLD : QUARTZ_PILLAR);
            }
        }
        // 3단 옥좌 단
        for (int k = 0; k < 3; k++) {
            int hw = 8 - 2 * k;
            for (int u = cu - hw; u <= cu + hw; u++) {
                for (int v = D - 8 + k; v <= D - 1; v++) {
                    boolean edgeFront = v == D - 8 + k, edgeSide = Math.abs(u - cu) == hw;
                    BlockState s = edgeFront ? Royal.whiteStairs(f.back) : edgeSide ? Royal.whiteStairs(u < cu ? f.right : f.left) : SMOOTH_QUARTZ;
                    f.set(p, y, u, 1 + k, v, s);
                }
            }
        }
        Royal.rug(p, f.x(cu - 4, D - 5), f.z(cu - 4, D - 5), f.x(cu + 4, D - 2), f.z(cu + 4, D - 2), y + 4);
        // 옥좌
        f.set(p, y, cu, 4, D - 3, Royal.chair(f.back));
        f.set(p, y, cu - 1, 4, D - 3, Royal.GOLD);
        f.set(p, y, cu + 1, 4, D - 3, Royal.GOLD);
        for (int du = -1; du <= 1; du++) for (int ly = 4; ly <= (du == 0 ? 7 : 6); ly++) f.set(p, y, cu + du, ly, D - 2, Royal.GOLD);
        f.set(p, y, cu, 8, D - 2, END_ROD);
        // 천개 (기둥 4개 + 붉은 천)
        int[][] posts = {{cu - 4, D - 7}, {cu + 4, D - 7}, {cu - 4, D - 1}, {cu + 4, D - 1}};
        for (int[] q : posts) for (int ly = 4; ly <= 8; ly++) f.set(p, y, q[0], ly, q[1], QUARTZ_PILLAR);
        for (int u = cu - 4; u <= cu + 4; u++) {
            for (int v = D - 7; v <= D - 1; v++) {
                boolean edge = Math.abs(u - cu) == 4 || v == D - 7 || v == D - 1;
                f.set(p, y, u, 9, v, edge ? Royal.GOLD : Royal.RED_WOOL);
            }
        }
        // +10 회랑 (뒷벽을 따라, 2단 테라스로 나가는 문과 연결) + 내려가는 계단
        for (int u = 0; u < W; u++) {
            for (int v = D - 4; v <= D - 1; v++) f.set(p, y, u, 10, v, SMOOTH_QUARTZ);
            boolean stairLane = u <= 2 || u >= W - 3;
            if (!stairLane) f.set(p, y, u, 11, D - 5, Royal.DARK_FENCE);
        }
        for (int k = 1; k <= 10; k++) {
            int v = D - 15 + k;
            for (int u : new int[]{1, 2, W - 3, W - 2}) f.set(p, y, u, k, v, Royal.whiteStairs(f.back));
        }
        // 붉은 깃발 (양 옆벽)
        for (int v = 3; v <= D - 6; v += 5) {
            f.set(p, y, 0, 13, v, Royal.redBanner(f.right));
            f.set(p, y, W - 1, 13, v, Royal.redBanner(f.left));
        }
        // 샹들리에
        for (int v = 3; v <= D - 9; v += 6) {
            for (int u = cu - 12; u <= cu + 12; u += 12) Royal.chandelier(p, f.x(u, v), y + fh, f.z(u, v), 8);
        }
    }

    /** 지붕 위 흰 돔 (금 갈비뼈) + 금 채광탑 */
    static void dome(Placer p, int dcx, int dcz, int by, int r) {
        p.forColumns(dcx - r - 1, dcz - r - 1, dcx + r + 1, dcz + r + 1, (x, z) -> {
            double d = Math.hypot(x - dcx, z - dcz);
            if (d <= r + 0.5) p.set(x, by, z, d > r - 0.5 ? CHISELED_QUARTZ : SMOOTH_QUARTZ);
            double frac = (Shapes.angle(x - dcx, z - dcz) * 8 / (Math.PI * 2)) % 1.0;
            for (int k = 1; k <= r; k++) {
                double d3 = Math.sqrt(d * d + k * k);
                if (d3 <= r + 0.3 && d3 > r - 1.0) p.set(x, by + k, z, (frac < 0.12 && d > 1) ? Royal.GOLD : SMOOTH_QUARTZ);
            }
        });
        for (int k = 1; k <= 3; k++) p.set(dcx, by + r + k, dcz, Royal.GOLD);
        p.set(dcx, by + r + 4, dcz, END_ROD);
    }
}
