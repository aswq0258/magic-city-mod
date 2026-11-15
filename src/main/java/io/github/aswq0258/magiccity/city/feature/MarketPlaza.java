package io.github.aswq0258.magiccity.city.feature;

import io.github.aswq0258.magiccity.build.Placer;
import io.github.aswq0258.magiccity.city.CityLayout;
import io.github.aswq0258.magiccity.city.Deco;
import io.github.aswq0258.magiccity.city.Shapes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import static io.github.aswq0258.magiccity.city.Palette.*;

/** 중앙 시장 광장: 큰 2단 분수, 둘레의 줄무늬 노점들, 자작나무, 가로등, 상인 주민 */
public class MarketPlaza extends Feature {
    private final int mx, mz, half;
    private final long seed;

    public MarketPlaza(int x0, int z0, int x1, int z1, long seed) {
        super(x0, z0, x1, z1);
        this.mx = (x0 + x1) >> 1;
        this.mz = (z0 + z1) >> 1;
        this.half = Math.min(x1 - x0, z1 - z0) / 2;
        this.seed = seed;
    }

    @Override
    public void build(Placer p, CityLayout city) {
        final int b = groundY;
        p.forColumns(minX, minZ, maxX, maxZ, (x, z) -> {
            int m = Math.max(Math.abs(x - mx), Math.abs(z - mz));
            BlockState s = m % 6 == 0 ? MINT_CUT : (((x + z) & 1) == 0 ? SMOOTH_QUARTZ : CALCITE);
            p.set(x, b, z, s);
            double d = Math.hypot(x - mx, z - mz);
            if (d <= 7) {
                p.set(x, b, z, CALCITE);
                if (d > 1.5) p.set(x, b + 1, z, WATER);
            } else if (d <= 8) {
                p.set(x, b + 1, z, QUARTZ_BRICKS);
                p.set(x, b + 2, z, SMOOTH_QUARTZ_SLAB);
            }
        });
        // 가운데 2단 분수 탑
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            for (int y = b + 1; y <= b + 3; y++) p.set(mx + dx, y, mz + dz, (dx == 0 && dz == 0) ? QUARTZ_PILLAR : QUARTZ_BRICKS);
            p.set(mx + dx, b + 4, mz + dz, (dx == 0 && dz == 0) ? QUARTZ_PILLAR : WATER);
        }
        p.set(mx, b + 5, mz, QUARTZ_PILLAR);
        p.set(mx, b + 6, mz, LIGHT);
        p.set(mx, b + 7, mz, MINT_CHISELED);
        p.set(mx, b + 8, mz, WATER);

        // 노점 둘레 (중심에서 18칸)
        int r = Math.min(18, half - 6);
        int i = 0;
        for (int t = -r; t <= r; t += 9) {
            int[][] spots = {{t, -r}, {t, r}, {-r, t}, {r, t}};
            for (int[] sp : spots) {
                if (Math.abs(sp[0]) == r && Math.abs(sp[1]) == r && sp[0] != t) continue;
                int sx = sp[0] >= 0 ? 1 : -1, sz = sp[1] >= 0 ? 1 : -1;
                MarketSquare.stall(p, b, mx + sp[0], mz + sp[1], sx, sz, JOB_BLOCKS[(i++ + (int) (seed & 7)) % JOB_BLOCKS.length], seed);
            }
        }
        // 모서리 나무와 가로등, 의자
        int c = half - 4;
        int[][] corners = {{c, c}, {-c, c}, {c, -c}, {-c, -c}};
        for (int[] k : corners) {
            Shapes.birchTree(p, mx + k[0], mz + k[1], b, 6);
            Shapes.lampPost(p, mx + k[0] / 2, mz + k[1] / 2, b);
        }
        for (int k = -12; k <= 12; k += 6) {
            Shapes.lampPost(p, mx + k, mz + 11, b);
            Shapes.lampPost(p, mx + k, mz - 11, b);
            p.set(mx + 11, b + 1, mz + k, Deco.chair(net.minecraft.core.Direction.EAST));
            p.set(mx - 11, b + 1, mz + k, Deco.chair(net.minecraft.core.Direction.WEST));
        }
    }

    @Override
    public void spawn(ServerLevel level, CityLayout city) {
        for (int k = 0; k < 4; k++) {
            double a = k * Math.PI / 2;
            Shapes.spawnVillager(level, mx + Math.cos(a) * 14 + 0.5, groundY + 1, mz + Math.sin(a) * 14 + 0.5);
        }
    }

    @Override
    public boolean needsSpawn() {
        return true;
    }
}
