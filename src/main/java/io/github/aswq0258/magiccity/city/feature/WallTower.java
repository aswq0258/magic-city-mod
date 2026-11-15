package io.github.aswq0258.magiccity.city.feature;

import io.github.aswq0258.magiccity.build.Placer;
import io.github.aswq0258.magiccity.city.CityLayout;
import io.github.aswq0258.magiccity.city.Shapes;
import net.minecraft.core.Direction;

/** 성문과 성문 사이 외성벽 위의 큰 원형 탑 */
public class WallTower extends Feature {
    private static final int RAD = 6, HEIGHT = 30;
    private final int tx, tz;
    private final Direction door;

    public WallTower(CityLayout city, double angle, double r) {
        this(city, city.cx + (int) Math.round(Math.cos(angle) * r), city.cz + (int) Math.round(Math.sin(angle) * r));
    }

    private WallTower(CityLayout city, int tx, int tz) {
        super(tx - RAD - 3, tz - RAD - 3, tx + RAD + 3, tz + RAD + 3);
        this.tx = tx;
        this.tz = tz;
        this.door = city.facingToCenter(tx, tz);
    }

    @Override
    public void build(Placer p, CityLayout city) {
        Shapes.roundTower(p, tx, tz, RAD, city.base, HEIGHT, door);
    }
}
