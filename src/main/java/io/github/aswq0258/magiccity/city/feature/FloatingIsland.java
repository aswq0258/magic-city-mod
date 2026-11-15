package io.github.aswq0258.magiccity.city.feature;

import io.github.aswq0258.magiccity.build.Placer;
import io.github.aswq0258.magiccity.city.CityLayout;
import io.github.aswq0258.magiccity.city.Fantasy;

/** 도시 위 하늘에 떠 있는 작은 섬 */
public class FloatingIsland extends Feature {
    private final int ix, iz, lift, r;
    private final long seed;

    public FloatingIsland(int ix, int iz, int lift, int r, long seed) {
        super(ix - r - 3, iz - r - 3, ix + r + 3, iz + r + 3);
        this.ix = ix;
        this.iz = iz;
        this.lift = lift;
        this.r = r;
        this.seed = seed;
    }

    @Override
    public boolean occupies(int x, int z) {
        return false;
    }

    @Override
    public void build(Placer p, CityLayout city) {
        Fantasy.floatingIsland(p, ix, iz, groundY + lift, r, seed);
    }
}
