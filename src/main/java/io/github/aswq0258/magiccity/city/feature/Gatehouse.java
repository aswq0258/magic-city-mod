package io.github.aswq0258.magiccity.city.feature;

import io.github.aswq0258.magiccity.build.Placer;
import io.github.aswq0258.magiccity.city.CityLayout;
import io.github.aswq0258.magiccity.city.Shapes;
import net.minecraft.world.level.block.state.BlockState;

import static io.github.aswq0258.magiccity.city.CityLayout.AV_COS;
import static io.github.aswq0258.magiccity.city.CityLayout.AV_SIN;
import static io.github.aswq0258.magiccity.city.CityLayout.AVENUE_HALF;
import static io.github.aswq0258.magiccity.city.CityLayout.WALL_HEIGHT;
import static io.github.aswq0258.magiccity.city.Palette.*;

/** 성문: 대로가 성벽을 지나는 곳에 아치를 만들고, 양옆에 큰 탑 두 개 */
public class Gatehouse extends Feature {
    private static final int TOWER_RAD = 5, TOWER_HEIGHT = 34, EXTENT = 20;
    private final int k;
    private final int t1x, t1z, t2x, t2z;

    public Gatehouse(CityLayout city, int k, double rWallMid) {
        this(city, k, city.cx + AV_COS[k] * rWallMid, city.cz + AV_SIN[k] * rWallMid);
    }

    private Gatehouse(CityLayout city, int k, double gx, double gz) {
        super((int) Math.floor(gx) - EXTENT, (int) Math.floor(gz) - EXTENT,
                (int) Math.ceil(gx) + EXTENT, (int) Math.ceil(gz) + EXTENT);
        this.k = k;
        double off = AVENUE_HALF + 1 + TOWER_RAD + 0.5;
        double tanX = -AV_SIN[k], tanZ = AV_COS[k];
        this.t1x = (int) Math.round(gx + tanX * off);
        this.t1z = (int) Math.round(gz + tanZ * off);
        this.t2x = (int) Math.round(gx - tanX * off);
        this.t2z = (int) Math.round(gz - tanZ * off);
    }

    @Override
    public void build(Placer p, CityLayout c) {
        int b = groundY, H = WALL_HEIGHT;
        p.forColumns(minX, minZ, maxX, maxZ, (x, z) -> {
            double dx = x - c.cx, dz = z - c.cz;
            double r = Math.sqrt(dx * dx + dz * dz);
            if (r < c.rWallIn || r >= c.rWallOut) return;
            double proj = dx * AV_COS[k] + dz * AV_SIN[k];
            double perp = Math.abs(dx * AV_SIN[k] - dz * AV_COS[k]);
            if (proj <= 0 || perp > AVENUE_HALF) return;
            double t = perp / AVENUE_HALF;
            int open = (int) Math.floor(9 + 3 * Math.sqrt(Math.max(0, 1 - t * t)));
            for (int ly = open + 1; ly <= H; ly++) {
                BlockState s = QUARTZ_BRICKS;
                if (ly == H) s = SMOOTH_QUARTZ;
                else if (ly == H - 4) s = MINT_CUT;
                else if (ly == open + 1) s = CHISELED_QUARTZ;
                p.set(x, b + ly, z, s);
            }
            if (r >= c.rWallOut - 1) p.set(x, b + H + 1, z, MINT_CHISELED);
        });
        Shapes.roundTower(p, t1x, t1z, TOWER_RAD, b, TOWER_HEIGHT, c.facingToCenter(t1x, t1z));
        Shapes.roundTower(p, t2x, t2z, TOWER_RAD, b, TOWER_HEIGHT, c.facingToCenter(t2x, t2z));
    }
}
