package io.github.aswq0258.magiccity.city.feature;

import io.github.aswq0258.magiccity.build.Placer;
import io.github.aswq0258.magiccity.city.CityLayout;
import io.github.aswq0258.magiccity.city.Shapes;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

import static io.github.aswq0258.magiccity.city.Palette.*;

/**
 * 중앙 성채
 *  - 대성탑: 반지름 12.5, 높이 64, 민트 구리 돔 + 첨탑 + 꼭대기의 떠 있는 수정
 *  - 내성벽: 반지름 23~26, 높이 14, 대로 방향으로 아치형 출입구 8개
 *  - 내성벽 위 원형 탑 8개
 *  - 돔 주위를 도는 공중 고리, 광장 위에 떠 있는 수정 4개
 */
public class Citadel extends Feature {
    private static final int TOWER_H = 64;
    private static final double TOWER_R = 12.5, TOWER_INNER = 10.5;
    private static final int KEEP_H = 14;
    private static final int HALO_R = 20;

    public Citadel(CityLayout city) {
        super(city.cx - 42, city.cz - 42, city.cx + 42, city.cz + 42);
    }

    @Override
    public boolean occupies(int x, int z) {
        return false; // 가로등 배치는 광장 패턴(lampOk: r > 28)이 결정
    }

    @Override
    public void build(Placer p, CityLayout c) {
        int b = c.base, cx = c.cx, cz = c.cz;
        buildKeepWall(p, c, b, cx, cz);
        for (int k = 0; k < 8; k++) {
            double a = (k + 0.5) * Math.PI / 4;
            int tx = cx + (int) Math.round(Math.cos(a) * 24.5);
            int tz = cz + (int) Math.round(Math.sin(a) * 24.5);
            Shapes.roundTower(p, tx, tz, 4.5, b, 30, null);
        }
        buildGreatTower(p, b, cx, cz);
        buildHalo(p, b + TOWER_H + 4, cx, cz);
        for (int k = 0; k < 4; k++) {
            double a = (k + 0.5) * Math.PI / 2;
            Shapes.crystal(p, cx + (int) Math.round(Math.cos(a) * 36), b + 36, cz + (int) Math.round(Math.sin(a) * 36), 4);
        }
    }

    private void buildKeepWall(Placer p, CityLayout c, int b, int cx, int cz) {
        p.forColumns(cx - 27, cz - 27, cx + 27, cz + 27, (x, z) -> {
            double dx = x - cx, dz = z - cz;
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d <= 23 || d > 26) return;
            double ad = c.avenueDist(dx, dz);
            int arc = (int) Math.floor(Shapes.angle(dx, dz) * 26);
            double archTop = ad <= 3.5 ? 7 + 2 * Math.sqrt(Math.max(0, 1 - (ad / 3.5) * (ad / 3.5))) : -1;
            for (int ly = 1; ly <= KEEP_H; ly++) {
                BlockState s;
                if (ly == KEEP_H) s = SMOOTH_QUARTZ;
                else if (ly == 10) s = MINT_CUT;
                else s = QUARTZ_BRICKS;
                if (ly <= archTop) s = AIR;
                p.set(x, b + ly, z, s);
            }
            if (d > 25 && (arc / 2) % 2 == 0) p.set(x, b + KEEP_H + 1, z, CALCITE);
        });
    }

    private void buildGreatTower(Placer p, int b, int cx, int cz) {
        final int ladderX = cx + 7, ladderZ = cz - 7;
        p.forColumns(cx - 14, cz - 14, cx + 14, cz + 14, (x, z) -> {
            int dx = x - cx, dz = z - cz;
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d > 13.5) return;
            int arc = (int) Math.floor(Shapes.angle(dx, dz) * 12);

            if (d > TOWER_R) { // 꼭대기 바깥 난간
                p.set(x, b + TOWER_H, z, SMOOTH_QUARTZ);
                if (arc % 2 == 0) p.set(x, b + TOWER_H + 1, z, CALCITE);
                return;
            }

            boolean wall = d > TOWER_INNER;
            boolean doorway = Math.min(Math.abs(dx), Math.abs(dz)) <= 1;
            boolean ladderCol = x == ladderX && z == ladderZ;
            for (int ly = 1; ly <= TOWER_H; ly++) {
                BlockState s;
                if (wall) {
                    if (ly % 16 == 0) s = MINT_CUT;
                    else if (arc % 6 == 0 && ly > 8 && ly % 8 >= 3 && ly % 8 <= 6) s = GLASS;
                    else s = QUARTZ_BRICKS;
                    if (doorway && ly <= 5) s = AIR;
                } else {
                    s = (ly % 8 == 0) ? (d < 1.5 ? LIGHT : SMOOTH_QUARTZ) : AIR;
                    if (ladderCol) s = ladder(Direction.SOUTH);
                }
                p.set(x, b + ly, z, s);
            }

            if (!wall) { // 1층: 마법부여대와 서가
                if (d < 0.5) {
                    p.set(x, b + 1, z, ENCHANTING_TABLE);
                } else if (d > 2.5 && d <= 3.5 && Math.min(Math.abs(dx), Math.abs(dz)) > 1) {
                    p.set(x, b + 1, z, BOOKSHELF);
                    p.set(x, b + 2, z, BOOKSHELF);
                }
            }

            // 돔
            for (int ly = 1; ly <= 13; ly++) {
                double d3 = Math.sqrt(d * d + ly * ly);
                if (d3 <= TOWER_R && d3 > TOWER_R - 1.3) p.set(x, b + TOWER_H + ly, z, MINT_BLOCK);
                else if (d3 <= TOWER_R - 1.3) p.set(x, b + TOWER_H + ly, z, AIR);
            }
        });

        // 첨탑
        int yS = b + TOWER_H + 12;
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
