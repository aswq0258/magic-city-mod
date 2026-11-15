package io.github.aswq0258.magiccity.city.feature;

import io.github.aswq0258.magiccity.build.Placer;
import io.github.aswq0258.magiccity.city.CityLayout;
import io.github.aswq0258.magiccity.city.Shapes;

import static io.github.aswq0258.magiccity.city.Palette.*;

/** 주거 구역의 작은 분수 광장 */
public class FountainSquare extends Feature {
    private final int fx, fz;

    public FountainSquare(CityLayout city, CityLayout.Lot lot) {
        super(lot.minX(), lot.minZ(), lot.maxX(), lot.maxZ());
        this.fx = lot.centerX();
        this.fz = lot.centerZ();
    }

    @Override
    public void build(Placer p, CityLayout city) {
        int b = city.base;
        p.forColumns(minX, minZ, maxX, maxZ, (x, z) ->
                p.set(x, b, z, ((x + z) & 1) == 0 ? SMOOTH_QUARTZ : CALCITE));
        Shapes.fountain(p, fx, fz, b);
        for (int[] c : new int[][]{{minX, minZ}, {maxX, minZ}, {minX, maxZ}, {maxX, maxZ}}) {
            Shapes.lampPost(p, c[0], c[1], b);
        }
    }
}
