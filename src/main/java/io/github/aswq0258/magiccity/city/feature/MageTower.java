package io.github.aswq0258.magiccity.city.feature;

import io.github.aswq0258.magiccity.build.Placer;
import io.github.aswq0258.magiccity.city.CityLayout;
import io.github.aswq0258.magiccity.city.Shapes;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;

import static io.github.aswq0258.magiccity.city.Palette.*;

/** 마법탑: 높은 원형 탑 + 꼭대기 근처에 떠 있는 고리 */
public class MageTower extends Feature {
    private final int tx, tz, height;
    private final double rad;
    private final Direction door;

    public MageTower(CityLayout city, CityLayout.Lot lot, RandomSource rand) {
        this(lot, 4 + rand.nextInt(3), 22 + rand.nextInt(13));
    }

    private MageTower(CityLayout.Lot lot, int rad, int height) {
        super(lot.centerX() - rad - 4, lot.centerZ() - rad - 4, lot.centerX() + rad + 4, lot.centerZ() + rad + 4);
        this.tx = lot.centerX();
        this.tz = lot.centerZ();
        this.rad = rad;
        this.height = height;
        this.door = lot.front();
    }

    @Override
    public void build(Placer p, CityLayout city) {
        int b = city.base;
        // 탑 주변 작은 광장
        p.forColumns(minX + 1, minZ + 1, maxX - 1, maxZ - 1, (x, z) -> {
            double d = Math.hypot(x - tx, z - tz);
            if (d <= rad + 3.5) p.set(x, b, z, (Math.abs(d - (rad + 2.5)) < 0.5) ? MINT_CUT : SMOOTH_QUARTZ);
        });
        Shapes.roundTower(p, tx, tz, rad, b, height, door);
        p.set(tx, b + 1, tz, ENCHANTING_TABLE);

        // 떠 있는 고리
        double ringR = rad + 3;
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
