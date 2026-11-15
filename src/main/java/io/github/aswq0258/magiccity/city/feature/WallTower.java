package io.github.aswq0258.magiccity.city.feature;

import io.github.aswq0258.magiccity.build.Placer;
import io.github.aswq0258.magiccity.city.CityLayout;
import io.github.aswq0258.magiccity.city.Shapes;
import net.minecraft.core.Direction;

/** 외성벽 위의 원형 탑 (모서리 큰 탑 / 중간 탑 / 성문 탑) */
public class WallTower extends Feature {
    private final int tx, tz, height;
    private final double rad;
    private final Direction door;

    public WallTower(CityLayout city, int tx, int tz) {
        this(city, tx, tz, 7, 40);
    }

    public WallTower(CityLayout city, int tx, int tz, double rad, int height) {
        super(tx - (int) Math.ceil(rad) - 3, tz - (int) Math.ceil(rad) - 3, tx + (int) Math.ceil(rad) + 3, tz + (int) Math.ceil(rad) + 3);
        this.tx = tx;
        this.tz = tz;
        this.rad = rad;
        this.height = height;
        this.door = city.facingToCenter(tx, tz);
    }

    @Override
    public void build(Placer p, CityLayout city) {
        Shapes.roundTower(p, tx, tz, rad, groundY, height, door);
    }
}
