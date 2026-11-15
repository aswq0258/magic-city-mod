package io.github.aswq0258.magiccity.city.feature;

import io.github.aswq0258.magiccity.build.Placer;
import io.github.aswq0258.magiccity.city.Chateau;
import io.github.aswq0258.magiccity.city.CityLayout;
import io.github.aswq0258.magiccity.city.Deco;
import io.github.aswq0258.magiccity.city.Fantasy;
import io.github.aswq0258.magiccity.city.Palette;
import io.github.aswq0258.magiccity.city.Royal;
import io.github.aswq0258.magiccity.city.Shapes;
import io.github.aswq0258.magiccity.city.Towers;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

import static io.github.aswq0258.magiccity.city.Palette.*;

/**
 * C01 중앙 주탑 — 2단 테라스(+18) 위에 웨딩케이크처럼 쌓아 올린 3단 궁전 + 첨탑.
 *
 *  1단 (46 x 40, 3층, +18 ~ +42): 1층 대현관홀 / 2층 왕실 서재 / 3층 연회장
 *  2단 (34 x 28, 2층, +42 ~ +60): 1층 국왕·왕비 침실 / 2층 집무실
 *  3단 (22 x 16, 2층, +60 ~ +76): 1층 기도실 / 2층 보물실
 *  첨탑 (반지름 6, +76 ~ +104): 금 띠 + 창, 꼭대기 관측실, 관측실 위 높이 30 원뿔 첨탑 (~+143)
 *  가운데를 관통하는 나선 대계단(빛기둥 + 유리 난간)으로 모든 층과 첨탑 꼭대기까지 연결.
 *  각 단의 지붕은 금 머리 조각상 난간이 둘린 테라스이고, 1·2단 지붕 모서리에 작은 금 돔 탑.
 */
public class Keep extends Feature {
    private static final int LIFT = 18;
    private static final int A_H = 24, B_H = 18, C_H = 16, SPIRE_H = 28, SPIRE_R = 6;
    private final int kx, kz;
    private final long seed;

    public Keep(int x0, int z0, int x1, int z1, long seed) {
        super(Math.min(x0, x1), Math.min(z0, z1), Math.max(x0, x1), Math.max(z0, z1));
        this.kx = (minX + maxX) >> 1;
        this.kz = (minZ + maxZ) >> 1;
        this.seed = seed;
    }

    @Override
    public boolean occupies(int x, int z) {
        return x >= minX - 2 && x <= maxX + 2 && z >= minZ - 2 && z <= maxZ + 2;
    }

    @Override
    public void build(Placer p, CityLayout city) {
        if (!p.intersects(minX - 8, minZ - 8, maxX + 8, maxZ + 8)) return;
        final int y = groundY + LIFT;
        final int yB = y + A_H, yC = yB + B_H, yS = yC + C_H;

        // 1단
        new RoyalBox(minX, minZ, maxX, maxZ, y, 3, 8).core(kx, kz, 2)
                .door(Direction.SOUTH, kx, 2, 1, 6).door(Direction.WEST, kz, 1, 1, 4).door(Direction.EAST, kz, 1, 1, 4).build(p);
        // 비대칭 단 배치: 2단은 남서쪽으로, 3단은 서쪽으로 치우침 (나선 계단 축은 그대로)
        final int bx0 = minX + 4, bz0 = minZ + 8, bx1 = maxX - 10, bz1 = maxZ - 4;
        final int cx0 = minX + 12, cz0 = minZ + 10, cx1 = maxX - 14, cz1 = maxZ - 14;
        // 2단
        RoyalBox b = new RoyalBox(bx0, bz0, bx1, bz1, yB, 2, 9).core(kx, kz, 2);
        for (Direction d : new Direction[]{Direction.NORTH, Direction.SOUTH}) b.door(d, kx, 1, 1, 3);
        for (Direction d : new Direction[]{Direction.WEST, Direction.EAST}) b.door(d, kz, 1, 1, 3);
        b.build(p);
        // 3단
        RoyalBox c = new RoyalBox(cx0, cz0, cx1, cz1, yC, 2, 8).core(kx, kz, 2).roofed();
        for (Direction d : new Direction[]{Direction.NORTH, Direction.SOUTH}) c.door(d, kx, 1, 1, 3);
        for (Direction d : new Direction[]{Direction.WEST, Direction.EAST}) c.door(d, kz, 1, 1, 3);
        c.build(p);
        // 위 단 바닥의 계단 구멍 (각 단의 바닥 = 아래 단의 지붕)
        for (int base : new int[]{yB, yC, yS}) {
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) p.set(kx + dx, base, kz + dz, AIR);
        }

        spire(p, yS);
        spiral(p, y, yS + SPIRE_H);

        // 방 꾸미기
        rooms(p, minX, minZ, maxX, maxZ, y, 8, new RoyalRooms.Kind[]{RoyalRooms.Kind.GRAND_HALL, RoyalRooms.Kind.LIBRARY, RoyalRooms.Kind.BANQUET});
        rooms(p, bx0, bz0, bx1, bz1, yB, 9, new RoyalRooms.Kind[]{RoyalRooms.Kind.BEDCHAMBER, RoyalRooms.Kind.STUDY});
        rooms(p, cx0, cz0, cx1, cz1, yC, 8, new RoyalRooms.Kind[]{RoyalRooms.Kind.CHAPEL, RoyalRooms.Kind.TREASURY});

        // ── 3단: 가파른 슬레이트 모임지붕 (첨탑 자리는 비움) + 지붕창
        Chateau.hipRoof(p, cx0, cz0, cx1, cz1, yS + 1, 12, SPIRE_R + 0.5, kx, kz);
        Chateau.dormer(p, kx - 7, cz0, yS + 1, Direction.NORTH);
        Chateau.dormer(p, kx + 6, cz0, yS + 1, Direction.NORTH);
        Chateau.dormer(p, kx - 8, cz1, yS + 1, Direction.SOUTH);
        Chateau.dormer(p, cx0, kz, yS + 1, Direction.WEST);
        Chateau.dormer(p, cx1, kz - 3, yS + 1, Direction.EAST);

        // ── 북동: 가장 높은 둥근 망루(동종탑). 서남쪽의 낮고 굵은 탑과 무게를 맞춤
        int dtx = maxX - 5, dtz = minZ + 5;
        Palace.palaceTower(p, dtx, dtz, 5, yB, 46);
        for (int ly = 1; ly <= 3; ly++) p.set(dtx - 5, yB + ly, dtz + 1, AIR);           // 테라스 쪽 출입구
        for (int ly = 1; ly < 46; ly++) p.set(dtx + 3, yB + ly, dtz, Palette.ladder(Direction.WEST));
        Shapes.crystal(p, dtx, yB + 46 + 31, dtz, 1);
        // ── 남서: 낮고 굵은 탑 (1단에 붙어 테라스까지)
        Palace.palaceTower(p, minX + 3, maxZ - 3, 4.5, y, 36);
        // ── 크기가 다른 매달린 작은 탑들 (모서리마다 다르게)
        Chateau.turret(p, minX, minZ, yB - 9, 15, 2.5);           // 1단 북서
        Chateau.turret(p, maxX, maxZ, yB - 7, 11, 2.0);           // 1단 남동
        Chateau.turret(p, bx0, bz0, yC - 8, 13, 2.0);             // 2단 북서
        Chateau.turret(p, bx1, bz1, yC - 10, 17, 2.5);            // 2단 남동

        // ── 1단 지붕 테라스: 바깥 난간 위 지붕창, 2단 벽에 붙은 굴뚝, 가로등
        for (int x : new int[]{minX + 9, minX + 18, minX + 27}) Chateau.dormer(p, x, minZ, yB + 1, Direction.NORTH);
        for (int x : new int[]{minX + 14, minX + 24, maxX - 8}) Chateau.dormer(p, x, maxZ, yB + 1, Direction.SOUTH);
        for (int z : new int[]{minZ + 12, minZ + 22}) Chateau.dormer(p, minX, z, yB + 1, Direction.WEST);
        for (int z : new int[]{kz, maxZ - 10}) Chateau.dormer(p, maxX, z, yB + 1, Direction.EAST);
        Chateau.chimney(p, bx0 + 6, bz0 - 1, yB + 1, yC + 5, Direction.EAST);
        Chateau.chimney(p, bx0 + 20, bz0 - 1, yB + 1, yC + 7, Direction.EAST);
        Chateau.chimney(p, bx1 + 1, kz + 6, yB + 1, yC + 4, Direction.SOUTH);
        Chateau.chimney(p, kx + 5, bz1 + 1, yB + 1, yC + 3, Direction.EAST);
        for (int x = minX + 6; x <= maxX - 13; x += 10) Royal.lamp(p, x, minZ + 4, yB);
        for (int z = minZ + 14; z <= maxZ - 6; z += 10) Royal.lamp(p, maxX - 4, z, yB);

        // ── 2단 지붕 테라스: 남쪽의 넓은 정원 테라스, 지붕창, 3단 벽에 붙은 굴뚝
        for (int x : new int[]{bx0 + 6, kx, bx1 - 6}) Chateau.dormer(p, x, bz1, yC + 1, Direction.SOUTH);
        for (int z : new int[]{kz - 6, kz + 6}) Chateau.dormer(p, bx0, z, yC + 1, Direction.WEST);
        Chateau.chimney(p, cx0 + 4, cz1 + 1, yC + 1, yS + 8, Direction.EAST);
        Chateau.chimney(p, cx1 - 5, cz1 + 1, yC + 1, yS + 10, Direction.EAST);
        Chateau.chimney(p, cx0 - 1, kz - 4, yC + 1, yS + 6, Direction.SOUTH);
        for (int x = cx0; x <= cx1; x += 4) {                    // 화분 정원
            if (Math.abs(x - kx) <= 2) continue;
            p.set(x, yC + 1, cz1 + 5, Royal.HEDGE);
            p.set(x + 1, yC + 1, cz1 + 5, Royal.ROYAL_FLOWERS[Math.floorMod(x, 4)]);
        }
        Royal.lamp(p, cx0 - 4, cz1 + 7, yC);
        Royal.lamp(p, cx1 + 2, cz1 + 7, yC);

        // ── 판타지: 첨탑을 둘러 떠 있는 금빛 고리와 꼭대기 위 수정
        int spireBase = yS + SPIRE_H + 5;
        Fantasy.halo(p, kx, kz, spireBase + 12, 9.5);
        Shapes.crystal(p, kx, spireBase + 39, kz, 2);
    }

    private void rooms(Placer p, int x0, int z0, int x1, int z1, int y, int fh, RoyalRooms.Kind[] kinds) {
        int W = x1 - x0 - 1, D = z1 - z0 - 1;
        Frame f = new Frame((x0 + 1 + x1 - 1) >> 1, (z0 + 1 + z1 - 1) >> 1, Direction.SOUTH, W, D);
        for (int k = 0; k < kinds.length; k++) {
            final int floor = k;
            RoyalRooms.furnish(p, kinds[k], f, y + k * fh, fh, seed + k * 31L, (u, v) -> {
                int wx = f.x(u, v), wz = f.z(u, v);
                if (Math.max(Math.abs(wx - kx), Math.abs(wz - kz)) <= 3) return true;           // 나선 계단
                if (floor == 0 && Math.abs(u - W / 2) <= 2 && v <= 2) return true;               // 정문
                return floor == 0 && (u <= 1 || u >= W - 2) && Math.abs(v - D / 2) <= 2;          // 옆문
            });
        }
    }

    /** 가운데 나선 대계단 (빛기둥 + 유리 난간) */
    private void spiral(Placer p, int y, int top) {
        int[][] ring = Towers.ringCells(2);
        int topLy = top - y;
        for (int ly = 1; ly <= topLy; ly++) {
            p.set(kx, y + ly, kz, ly % 8 == 4 ? LIGHT : QUARTZ_PILLAR);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx != 0 || dz != 0) p.set(kx + dx, y + ly, kz + dz, ly < topLy ? Royal.CLEAR_PANE : SMOOTH_QUARTZ);
                }
            }
            for (int[] c : ring) p.set(kx + c[0], y + ly, kz + c[1], AIR);
        }
        for (int i = 0; i < topLy; i++) {
            int[] c0 = ring[i % ring.length], c1 = ring[(i + 1) % ring.length];
            p.set(kx + c0[0], y + i + 1, kz + c0[1], Royal.whiteStairs(Deco.toward(c1[0] - c0[0], c1[1] - c0[1])));
        }
    }

    /** 첨탑: 흰 원통 + 금 띠 + 창, 꼭대기 관측실, 금 갈비뼈 돔, 금 첨탑 */
    private void spire(Placer p, int ys) {
        int r = SPIRE_R;
        p.forColumns(kx - r - 2, kz - r - 2, kx + r + 2, kz + r + 2, (x, z) -> {
            int dx = x - kx, dz = z - kz;
            double d = Math.sqrt(dx * dx + dz * dz);
            int arc = (int) Math.floor(Shapes.angle(dx, dz) * r);
            if (d <= r + 0.5) {
                boolean wall = d > r - 1.0;
                for (int ly = 0; ly <= SPIRE_H; ly++) {
                    BlockState s = AIR;
                    if (wall) {
                        if (ly % 7 == 0) s = Royal.GOLD;
                        else if (ly % 7 >= 2 && ly % 7 <= 5 && arc % 3 == 0) s = Royal.CLEAR_PANE;
                        else s = SMOOTH_QUARTZ;
                    } else if (ly == SPIRE_H && Math.max(Math.abs(dx), Math.abs(dz)) > 2) {
                        s = Royal.marble(x, z);
                    } else if (ly == 0) {
                        continue;
                    }
                    p.set(x, ys + ly, z, s);
                }
            } else if (d <= r + 1.5) {
                p.set(x, ys + SPIRE_H, z, SMOOTH_QUARTZ);
                if (arc % 2 == 0) p.set(x, ys + SPIRE_H + 1, z, CHISELED_QUARTZ);
            }
        });
        // 꼭대기 관측실 벽 (유리창 4칸 높이)
        p.forColumns(kx - r - 1, kz - r - 1, kx + r + 1, kz + r + 1, (x, z) -> {
            double d = Math.hypot(x - kx, z - kz);
            if (d > r + 0.5 || d <= r - 1.0) return;
            int arc = (int) Math.floor(Shapes.angle(x - kx, z - kz) * r);
            for (int k = 1; k <= 4; k++) p.set(x, ys + SPIRE_H + k, z, k == 4 ? Royal.GOLD : arc % 2 == 0 ? QUARTZ_PILLAR : Royal.CLEAR_PANE);
        });
        // 바늘처럼 높은 원뿔 첨탑 + 둘레의 작은 첨탑 4개 + 피너클 4개 (발코니 위)
        Royal.spire(p, kx, kz, ys + SPIRE_H + 5, r, 30);
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * 2 * i / 8 + Math.PI / 8;
            int px = kx + (int) Math.round(Math.cos(a) * (r + 1.5)), pz = kz + (int) Math.round(Math.sin(a) * (r + 1.5));
            if (i % 2 == 0) Royal.spire(p, px, pz, ys + SPIRE_H + 1, 1.5, 10);
            else Royal.pinnacle(p, px, pz, ys + SPIRE_H + 1, 4);
        }
        // 꼭대기 관측실 가구
        int fy = ys + SPIRE_H + 1;
        p.set(kx + 3, fy, kz, ENCHANTING_TABLE);
        p.set(kx - 3, fy, kz, Deco.lectern(Direction.EAST));
        p.set(kx, fy, kz + 3, Royal.chair(Direction.SOUTH));
        p.set(kx, fy, kz - 3, Deco.pot((int) seed));
    }
}
