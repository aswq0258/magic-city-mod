package io.github.aswq0258.magiccity.city.feature;

import io.github.aswq0258.magiccity.build.Placer;
import io.github.aswq0258.magiccity.city.CityLayout;
import io.github.aswq0258.magiccity.city.Deco;
import io.github.aswq0258.magiccity.city.Royal;
import io.github.aswq0258.magiccity.city.Shapes;
import net.minecraft.world.level.block.state.BlockState;

import static io.github.aswq0258.magiccity.city.Palette.*;

/** 도시 성문 (k: 0=북, 1=동, 2=남, 3=서): 대로 위 아치형 문루 + 양옆 큰 원형 탑 + 깃발과 랜턴 */
public class CityGate extends Feature {
    private static final int[][] DIRS = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
    private static final int TOWER_RAD = 9, TOWER_H = 48, EXT = 34;
    private final int ax, az, lx, lz, gx, gz, half;

    public CityGate(CityLayout city, int k) {
        this(city.cx + DIRS[k][0] * (city.half - 5), city.cz + DIRS[k][1] * (city.half - 5), DIRS[k][0], DIRS[k][1], city.half);
    }

    private CityGate(int gx, int gz, int ax, int az, int half) {
        super(gx - EXT, gz - EXT, gx + EXT, gz + EXT);
        this.gx = gx;
        this.gz = gz;
        this.ax = ax;
        this.az = az;
        this.lx = -az;
        this.lz = ax;
        this.half = half;
    }

    @Override
    public void build(Placer p, CityLayout c) {
        final int b = groundY, H = CityLayout.WALL_HEIGHT;
        p.forColumns(minX, minZ, maxX, maxZ, (x, z) -> {
            int along = (x - c.cx) * ax + (z - c.cz) * az;
            int lat = (x - c.cx) * lx + (z - c.cz) * lz;
            if (along <= half - 10 || along > half || Math.abs(lat) > 7) return;
            double t = Math.abs(lat) / 7.5;
            int open = (int) Math.floor(18 + 4 * Math.sqrt(Math.max(0, 1 - t * t)));
            for (int ly = open + 1; ly <= H; ly++) {
                BlockState s = QUARTZ_BRICKS;
                if (ly == H) s = SMOOTH_QUARTZ;
                else if (ly == H - 4) s = MINT_CUT;
                else if (ly == open + 1) s = CHISELED_QUARTZ;
                p.set(x, b + ly, z, s);
            }
            if (along == half) p.set(x, b + H + 1, z, MINT_CHISELED);
        });
        p.set(gx, b + 22, gz, Deco.hangingLantern());
        for (int s = -1; s <= 1; s += 2) {
            p.set(gx + ax * 6 + lx * 4 * s, b + 24, gz + az * 6 + lz * 4 * s, Royal.mintBanner(Deco.toward(ax, az)));
            int tx = gx + lx * 20 * s, tz = gz + lz * 20 * s;
            Shapes.roundTower(p, tx, tz, TOWER_RAD, b, TOWER_H, Deco.toward(-ax, -az));
        }
    }
}
