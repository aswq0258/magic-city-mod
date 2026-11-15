package io.github.aswq0258.magiccity.city.feature;

import io.github.aswq0258.magiccity.build.Placer;
import io.github.aswq0258.magiccity.city.CityLayout;
import io.github.aswq0258.magiccity.city.Shapes;
import net.minecraft.core.Direction;

/** 외성벽 위의 원형 탑 */
public class WallTower extends Feature {
    private static final int RAD = 7, HEIGHT = 40;
    private final int tx, tz;
    private final Direction door;

    public WallTower(CityLayout city, int tx, int tz) {
        super(tx - RAD - 3, tz - RAD - 3, tx + RAD + 3, tz + RAD + 3);
        this.tx = tx;
        this.tz = tz;
        this.door = city.facingToCenter(tx, tz);
    }

    @Override
    public void build(Placer p, CityLayout city) {
        Shapes.roundTower(p, tx, tz, RAD, groundY, HEIGHT, door);
    }
}
