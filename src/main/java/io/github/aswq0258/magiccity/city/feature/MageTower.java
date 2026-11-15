package io.github.aswq0258.magiccity.city.feature;

import io.github.aswq0258.magiccity.build.Placer;
import io.github.aswq0258.magiccity.city.CityLayout;
import io.github.aswq0258.magiccity.city.Deco;
import io.github.aswq0258.magiccity.city.Palette;
import io.github.aswq0258.magiccity.city.Shapes;
import io.github.aswq0258.magiccity.city.Towers;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

import static io.github.aswq0258.magiccity.city.Palette.*;

/**
 * 마법탑 (4~6층)
 *
 * 외관: 받침단, 8개의 버팀 기둥, 쐐기돌 달린 아치창, 층마다 방해석 띠, 중간층 원형 발코니(석영 내물림 + 유리 난간),
 *       기둥과 차양이 있는 현관, 민트 원뿔 지붕 + 꼭대기 떠 있는 수정, 탑을 도는 공중 고리
 * 내부: 빛기둥을 감싼 나선 계단, 층마다 다른 방 (현관홀 → 서재 → 연금술실 → 연구실 → ...), 샹들리에
 */
public class MageTower extends Feature {
    private static final int FH = 6;
    private final int tx, tz, rad, floors, height;
    private final Direction door;
    private final long seed;

    public MageTower(CityLayout city, CityLayout.Lot lot, RandomSource rand) {
        this(lot, 5 + rand.nextInt(2), 4 + rand.nextInt(3), rand.nextLong());
    }

    private MageTower(CityLayout.Lot lot, int rad, int floors, long seed) {
        super(lot.centerX() - rad - 5, lot.centerZ() - rad - 5, lot.centerX() + rad + 5, lot.centerZ() + rad + 5);
        this.tx = lot.centerX();
        this.tz = lot.centerZ();
        this.rad = rad;
        this.floors = floors;
        this.height = floors * FH;
        this.door = lot.front();
        this.seed = seed;
    }

    @Override
    public void build(Placer p, CityLayout city) {
        final int b = groundY;
        final int balLy = FH * (floors / 2);

        // ── 탑 주변 광장 ──
        p.forColumns(minX + 1, minZ + 1, maxX - 1, maxZ - 1, (x, z) -> {
            double d = Math.hypot(x - tx, z - tz);
            if (d <= rad + 4.5) p.set(x, b, z, Math.abs(d - (rad + 3.5)) < 0.5 ? MINT_CUT : SMOOTH_QUARTZ);
        });

        // ── 벽, 받침단, 발코니 ──
        p.forColumns(tx - rad - 3, tz - rad - 3, tx + rad + 3, tz + rad + 3, (x, z) -> {
            int dx = x - tx, dz = z - tz;
            double d = Math.sqrt(dx * dx + dz * dz);
            int arc = (int) Math.floor(Shapes.angle(dx, dz) * rad);

            if (d > rad + 0.5 && d <= rad + 1.5) {          // 받침단
                p.set(x, b + 1, z, QUARTZ_BRICKS);
                p.set(x, b + 2, z, SMOOTH_QUARTZ_SLAB);
            }
            if (d > rad + 0.5 && d <= rad + 2.5) {          // 발코니
                p.set(x, b + balLy, z, SMOOTH_QUARTZ);
                if (d > rad + 1.5) {
                    p.set(x, b + balLy + 1, z, GLASS_PANE);
                    p.set(x, b + balLy - 1, z, Deco.corbel(Deco.toward(-dx, -dz)));
                }
            }
            if (d > rad - 0.5 && d <= rad + 0.5) {          // 벽
                boolean doorCol = dx == door.getStepX() * rad && dz == door.getStepZ() * rad;
                p.set(x, b, z, QUARTZ_BRICKS);
                for (int ly = 1; ly <= height; ly++) {
                    int local = ly % FH;
                    BlockState s;
                    if (local == 0) s = ly == height ? MINT_CUT : CALCITE;
                    else if (ly == 1) s = QUARTZ_BRICKS;
                    else if (arc % 4 == 1 && (local == 2 || local == 3)) s = GLASS_PANE;
                    else if (arc % 4 == 1 && local == 4) s = CHISELED_QUARTZ;
                    else s = QUARTZ_BRICKS;
                    if (doorCol) {
                        int rel = ly <= FH ? ly : ly - balLy;
                        boolean doorFloor = ly <= FH || (ly > balLy && ly <= balLy + FH);
                        if (doorFloor && (rel == 1 || rel == 2)) s = Palette.door(door.getOpposite(), rel == 2);
                        else if (doorFloor && rel == 3) s = GLASS_PANE;
                        else if (doorFloor && rel == 4) s = CHISELED_QUARTZ;
                    }
                    p.set(x, b + ly, z, s);
                }
            }
        });

        // ── 버팀 기둥 8개 ──
        for (int i = 0; i < 8; i++) {
            double a = (i + 0.5) * Math.PI / 4;
            int rx = tx + (int) Math.round(Math.cos(a) * (rad + 1));
            int rz = tz + (int) Math.round(Math.sin(a) * (rad + 1));
            for (int ly = 1; ly <= height; ly++) p.set(rx, b + ly, rz, ly % FH == 0 ? CHISELED_QUARTZ : QUARTZ_PILLAR);
        }

        // ── 현관: 양옆 기둥 + 민트 차양 ──
        int sideX = -door.getStepZ(), sideZ = door.getStepX();
        int ox = tx + door.getStepX() * (rad + 1), oz = tz + door.getStepZ() * (rad + 1);
        p.set(ox, b + 1, oz, AIR);
        p.set(ox, b + 2, oz, AIR);
        for (int s = -1; s <= 1; s++) {
            int x = ox + sideX * s, z = oz + sideZ * s;
            if (s != 0) for (int ly = 1; ly <= 3; ly++) p.set(x, b + ly, z, QUARTZ_PILLAR);
            p.set(x, b + 4, z, MINT_SLAB);
        }
        p.set(ox, b + 3, oz, Deco.hangingLantern());

        // ── 내부: 나선 계단 + 층별 방 ──
        Towers.interior(p, tx, tz, b, rad - 0.5, 2, FH, height, Deco.PLANKS, false);
        for (int k = 0; k < floors; k++) {
            boolean doorFloor = k == 0 || k * FH == balLy;
            Towers.furnish(p, tx, tz, b + k * FH, rad - 0.5, 2, FH, Towers.roomFor(k), seed,
                    doorFloor ? door : null, false, true);
        }

        // ── 원뿔 지붕 + 꼭대기 수정 ──
        int coneH = rad * 2 + 5;
        double coneR = rad + 1.5;
        p.forColumns(tx - rad - 2, tz - rad - 2, tx + rad + 2, tz + rad + 2, (x, z) -> {
            double d = Math.hypot(x - tx, z - tz);
            for (int k = 0; k < coneH; k++) {
                double cr = coneR * (1.0 - (double) k / coneH);
                if (d <= cr + 0.01 && d > cr - 1.6) {
                    p.set(x, b + height + 1 + k, z, k % 4 == 3 ? MINT_CHISELED : MINT_CUT);
                }
            }
        });
        int tip = b + height + 1 + coneH;
        p.set(tx, tip, tz, END_ROD);
        Shapes.crystal(p, tx, tip + 4, tz, 2);

        // ── 탑을 도는 공중 고리 ──
        double ringR = rad + 4;
        int ringY = b + height - 3;
        p.forColumns(minX, minZ, maxX, maxZ, (x, z) -> {
            int dx = x - tx, dz = z - tz;
            double d = Math.sqrt(dx * dx + dz * dz);
            if (Math.abs(d - ringR) <= 0.5) {
                int arc = (int) Math.floor(Shapes.angle(dx, dz) * ringR);
                p.set(x, ringY, z, arc % 3 == 0 ? MINT_CHISELED : CALCITE);
                if (arc % 6 == 3) p.set(x, ringY - 1, z, END_ROD_DOWN);
            }
        });
    }
}
