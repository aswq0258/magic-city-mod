package io.github.aswq0258.magiccity.city.feature;

import io.github.aswq0258.magiccity.build.Placer;
import io.github.aswq0258.magiccity.city.CityLayout;
import io.github.aswq0258.magiccity.city.Shapes;
import net.minecraft.util.RandomSource;

import static io.github.aswq0258.magiccity.city.Palette.*;

/** 정원: 잔디 + 방해석 십자 산책로 + 흰 꽃 + 자작나무. 큰 정원(학원 구역)은 가운데에 분수. */
public class Garden extends Feature {
    private final int gx, gz, half;
    private final long seed;
    private final int treeMask;
    private final int treeH;

    public Garden(CityLayout city, CityLayout.Lot lot, RandomSource rand) {
        super(lot.minX(), lot.minZ(), lot.maxX(), lot.maxZ());
        this.gx = lot.centerX();
        this.gz = lot.centerZ();
        this.half = lot.size() / 2;
        this.seed = rand.nextLong();
        this.treeMask = rand.nextBoolean() ? 0b1001 : 0b0110 | (rand.nextBoolean() ? 0b1001 : 0);
        this.treeH = 5 + rand.nextInt(2);
    }

    @Override
    public void build(Placer p, CityLayout city) {
        int b = groundY;
        boolean big = half >= 9;
        int q = Math.max(3, half / 2 + 1);

        p.forColumns(minX, minZ, maxX, maxZ, (x, z) -> {
            boolean path = x == gx || z == gz;
            if (big) path = Math.abs(x - gx) <= 1 || Math.abs(z - gz) <= 1;
            boolean border = x == minX || x == maxX || z == minZ || z == maxZ;
            if (border) {
                p.set(x, b, z, SMOOTH_QUARTZ);
                p.set(x, b + 1, z, SMOOTH_QUARTZ_SLAB);
                return;
            }
            p.set(x, b, z, path ? CALCITE : GRASS);
            if (!path && Shapes.hash(x, 1, z, seed) % 4 == 0) {
                p.set(x, b + 1, z, FLOWERS[Shapes.hash(x, 2, z, seed) % FLOWERS.length]);
            }
        });
        // 입구 (산책로가 테두리와 만나는 곳)
        for (int[] e : new int[][]{{gx, minZ}, {gx, maxZ}, {minX, gz}, {maxX, gz}}) {
            p.set(e[0], b + 1, e[1], AIR);
        }

        int[][] spots = {{gx - q, gz - q}, {gx + q, gz - q}, {gx - q, gz + q}, {gx + q, gz + q}};
        for (int i = 0; i < 4; i++) {
            if ((treeMask & (1 << i)) != 0) Shapes.birchTree(p, spots[i][0], spots[i][1], b, treeH);
        }

        if (big) {
            Shapes.fountain(p, gx, gz, b);
            Shapes.lampPost(p, gx + 7, gz + 2, b);
            Shapes.lampPost(p, gx - 7, gz - 2, b);
            Shapes.lampPost(p, gx + 2, gz - 7, b);
            Shapes.lampPost(p, gx - 2, gz + 7, b);
        } else {
            Shapes.lampPost(p, gx, gz, b);
        }
    }
}
