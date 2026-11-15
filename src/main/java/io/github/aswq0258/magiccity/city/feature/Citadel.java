package io.github.aswq0258.magiccity.city.feature;

import io.github.aswq0258.magiccity.build.Placer;
import io.github.aswq0258.magiccity.city.CityLayout;
import io.github.aswq0258.magiccity.city.Deco;
import io.github.aswq0258.magiccity.city.Shapes;
import io.github.aswq0258.magiccity.city.Towers;
import net.minecraft.world.level.block.state.BlockState;

import static io.github.aswq0258.magiccity.city.Palette.*;

/**
 * 중앙 성채
 *
 * 대성탑 (반지름 12.5, 높이 64, 8층)
 *  - 외관: 8개의 버팀벽과 꼭대기 첨탑 장식, 층마다 방해석·민트 띠, 높은 창, 네 방향 장미창,
 *          두 층의 원형 발코니(석영 내물림 + 유리 난간), 장식 테두리를 두른 네 방향 정문,
 *          민트 돔 + 첨탑 + 떠 있는 수정, 돔을 도는 공중 고리
 *  - 내부: 빛기둥을 둘러싼 석영 나선 대계단, 층마다 다른 방(현관홀/서재/연금술실/연구실), 샹들리에,
 *          돔 안 꼭대기층 관측실
 * 내성벽 (반지름 23~27, 높이 14): 아치 출입구 8개(조각 석영 테두리), 바깥 내물림 통로와 총안, 민트 유리 화살창
 * 안뜰: 잔디와 흰 꽃, 자작나무, 방해석 길, 가로등
 * 내성벽 위 원형 탑 8개, 광장 위 떠 있는 수정 4개
 */
public class Citadel extends Feature {
    private static final int H = 64, FH = 8;
    private static final double TOWER_R = 12.5, INNER = 10.5;
    private static final int KEEP_H = 14;
    private static final int HALO_R = 20;
    private static final int[] BALCONIES = {24, 48};
    private static final int ROSE_LY = 36;

    private final long seed;

    public Citadel(CityLayout city) {
        super(city.cx - 42, city.cz - 42, city.cx + 42, city.cz + 42);
        this.seed = city.seed ^ 0xC17ADE1L;
    }

    @Override
    public boolean occupies(int x, int z) {
        return false; // 가로등 배치는 광장 패턴(lampOk: r > 28)이 결정
    }

    @Override
    public void build(Placer p, CityLayout c) {
        int b = c.base, cx = c.cx, cz = c.cz;
        buildCourtyard(p, c, b, cx, cz);
        buildKeepWall(p, c, b, cx, cz);
        for (int k = 0; k < 8; k++) {
            double a = (k + 0.5) * Math.PI / 4;
            int tx = cx + (int) Math.round(Math.cos(a) * 24.5);
            int tz = cz + (int) Math.round(Math.sin(a) * 24.5);
            Shapes.roundTower(p, tx, tz, 4.5, b, 30, null);
        }
        buildGreatTower(p, b, cx, cz);
        buildHalo(p, b + H + 4, cx, cz);
        for (int k = 0; k < 4; k++) {
            double a = (k + 0.5) * Math.PI / 2;
            Shapes.crystal(p, cx + (int) Math.round(Math.cos(a) * 36), b + 36, cz + (int) Math.round(Math.sin(a) * 36), 4);
        }
    }

    // ───────────────────────── 안뜰 ─────────────────────────

    private void buildCourtyard(Placer p, CityLayout c, int b, int cx, int cz) {
        p.forColumns(cx - 23, cz - 23, cx + 23, cz + 23, (x, z) -> {
            double dx = x - cx, dz = z - cz;
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d <= 13.5 || d > 22.5) return;
            if (c.avenueDist(dx, dz) <= 2) {
                p.set(x, b, z, CALCITE);
            } else {
                p.set(x, b, z, GRASS);
                int h = Shapes.hash(x, 5, z, seed);
                if (h % 4 == 0) p.set(x, b + 1, z, FLOWERS[(h >> 4) % FLOWERS.length]);
            }
        });
        for (int k = 0; k < 8; k++) {
            double a = (k + 0.5) * Math.PI / 4;
            Shapes.birchTree(p, cx + (int) Math.round(Math.cos(a) * 17.5), cz + (int) Math.round(Math.sin(a) * 17.5), b, 6);
            double a2 = k * Math.PI / 4;
            int lx = cx + (int) Math.round(Math.cos(a2) * 19 - Math.sin(a2) * 3);
            int lz = cz + (int) Math.round(Math.sin(a2) * 19 + Math.cos(a2) * 3);
            Shapes.lampPost(p, lx, lz, b);
        }
    }

    // ───────────────────────── 내성벽 ─────────────────────────

    private void buildKeepWall(Placer p, CityLayout c, int b, int cx, int cz) {
        p.forColumns(cx - 28, cz - 28, cx + 28, cz + 28, (x, z) -> {
            double dx = x - cx, dz = z - cz;
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d <= 23 || d > 27) return;
            int arc = (int) Math.floor(Shapes.angle(dx, dz) * 26);

            if (d > 26) { // 바깥 내물림 통로 + 총안
                p.set(x, b + KEEP_H - 1, z, Deco.corbel(Deco.toward(-dx, -dz)));
                p.set(x, b + KEEP_H, z, SMOOTH_QUARTZ);
                if ((arc / 2) % 2 == 0) p.set(x, b + KEEP_H + 1, z, CALCITE);
                return;
            }

            double ad = c.avenueDist(dx, dz);
            double archTop = ad <= 3.5 ? 7 + 2 * Math.sqrt(Math.max(0, 1 - (ad / 3.5) * (ad / 3.5))) : -1;
            for (int ly = 1; ly <= KEEP_H; ly++) {
                BlockState s;
                if (ly == KEEP_H) s = SMOOTH_QUARTZ;
                else if (ly == 10) s = MINT_CUT;
                else s = QUARTZ_BRICKS;
                if (d > 25 && ad > 4.5 && arc % 5 == 0 && ly >= 5 && ly <= 7) s = GLASS;   // 화살창
                if (ly <= archTop) s = AIR;
                else if (archTop > 0 && ly <= archTop + 1) s = CHISELED_QUARTZ;            // 아치 테두리
                p.set(x, b + ly, z, s);
            }
            if (d <= 24) p.set(x, b + KEEP_H + 1, z, SMOOTH_QUARTZ_SLAB);
        });
    }

    // ───────────────────────── 대성탑 ─────────────────────────

    private void buildGreatTower(Placer p, int b, int cx, int cz) {
        p.forColumns(cx - 15, cz - 15, cx + 15, cz + 15, (x, z) -> {
            int dx = x - cx, dz = z - cz;
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d > 14.5) return;
            int arc = (int) Math.floor(Shapes.angle(dx, dz) * 12);
            int lat = Math.abs(Math.abs(dx) >= Math.abs(dz) ? dz : dx); // 가장 가까운 축에서 옆으로 떨어진 거리

            // 발코니 (바닥 + 내물림 + 유리 난간)
            if (d > TOWER_R) {
                for (int bl : BALCONIES) {
                    p.set(x, b + bl, z, SMOOTH_QUARTZ);
                    if (d > 13.5) {
                        p.set(x, b + bl + 1, z, GLASS_PANE);
                        p.set(x, b + bl - 1, z, Deco.corbel(Deco.toward(-dx, -dz)));
                    }
                }
                if (d <= 13.5) { // 꼭대기 난간
                    p.set(x, b + H, z, SMOOTH_QUARTZ);
                    if (arc % 2 == 0) p.set(x, b + H + 1, z, CALCITE);
                }
                return;
            }

            // 벽 (두께 2)
            if (d > INNER) {
                boolean outer = d > 11.5;
                for (int ly = 1; ly <= H; ly++) {
                    BlockState s;
                    if (ly % 16 == 0) s = MINT_CUT;
                    else if (ly % 8 == 0) s = CALCITE;
                    else s = QUARTZ_BRICKS;
                    if (arc % 6 == 0 && ly > 8 && ly % 8 >= 3 && ly % 8 <= 6) s = GLASS;             // 높은 창
                    if (lat <= 4) {                                                                   // 장미창
                        int r2 = lat * lat + (ly - ROSE_LY) * (ly - ROSE_LY);
                        if (r2 <= 6) s = GLASS;
                        else if (r2 <= 12 && outer) s = MINT_CHISELED;
                    }
                    if (outer && ly <= 7 && lat <= 2) {                                               // 정문 테두리
                        if (lat == 2 && ly <= 5) s = CHISELED_QUARTZ;
                        if (ly == 6) s = CHISELED_QUARTZ;
                        if (ly == 7 && lat == 0) s = MINT_CHISELED;
                    }
                    if (lat <= 1 && ly <= 5) s = AIR;                                                 // 정문
                    for (int bl : BALCONIES) {
                        if (lat == 0 && (ly == bl + 1 || ly == bl + 2)) s = AIR;                       // 발코니 출입구
                    }
                    p.set(x, b + ly, z, s);
                }
                p.set(x, b, z, QUARTZ_BRICKS);
            }

            // 돔 (꼭대기 층 위)
            for (int ly = 1; ly <= 13; ly++) {
                double d3 = Math.sqrt(d * d + ly * ly);
                if (d3 <= TOWER_R && d3 > TOWER_R - 1.3) p.set(x, b + H + ly, z, MINT_BLOCK);
                else if (d3 <= TOWER_R - 1.3) p.set(x, b + H + ly, z, AIR);
            }
        });

        // 버팀벽 8개 + 꼭대기 첨탑 장식
        for (int k = 0; k < 8; k++) {
            double a = (k + 0.5) * Math.PI / 4;
            int rx = cx + (int) Math.round(Math.cos(a) * 13);
            int rz = cz + (int) Math.round(Math.sin(a) * 13);
            for (int ly = 1; ly < H; ly++) p.set(rx, b + ly, rz, ly % FH == 0 ? CHISELED_QUARTZ : QUARTZ_PILLAR);
            for (int ly = H + 1; ly <= H + 3; ly++) p.set(rx, b + ly, rz, QUARTZ_PILLAR);
            p.set(rx, b + H + 4, rz, MINT_CHISELED);
            p.set(rx, b + H + 5, rz, END_ROD);
        }

        // 내부: 석영 나선 대계단 + 8개 층 + 돔 안 관측실
        Towers.interior(p, cx, cz, b, INNER, 3, FH, H, Deco.PLANKS, true);
        int floors = H / FH;
        for (int k = 0; k < floors; k++) {
            Towers.furnish(p, cx, cz, b + k * FH, INNER, 3, FH, Towers.roomFor(k), seed, null, true, true);
        }
        Towers.furnish(p, cx, cz, b + H, INNER, 3, FH, Towers.Room.STUDY, seed + 1, null, true, false);

        // 첨탑 + 떠 있는 수정
        int yS = b + H + 12;
        p.forColumns(cx - 4, cz - 4, cx + 4, cz + 4, (x, z) -> {
            double d = Math.hypot(x - cx, z - cz);
            for (int k = 0; k < 22; k++) {
                double cr = 3.2 * (1.0 - k / 22.0);
                if (d <= cr) p.set(x, yS + k, z, k % 5 == 4 ? MINT_CHISELED : MINT_CUT);
            }
        });
        p.set(cx, yS + 22, cz, END_ROD);
        Shapes.crystal(p, cx, yS + 28, cz, 3);
    }

    private void buildHalo(Placer p, int y, int cx, int cz) {
        p.forColumns(cx - HALO_R - 1, cz - HALO_R - 1, cx + HALO_R + 1, cz + HALO_R + 1, (x, z) -> {
            int dx = x - cx, dz = z - cz;
            double d = Math.sqrt(dx * dx + dz * dz);
            if (Math.abs(d - HALO_R) > 0.5) return;
            int arc = (int) Math.floor(Shapes.angle(dx, dz) * HALO_R);
            p.set(x, y, z, arc % 8 == 0 ? MINT_CHISELED : CALCITE);
            if (arc % 4 == 2) p.set(x, y - 1, z, END_ROD_DOWN);
        });
    }
}
