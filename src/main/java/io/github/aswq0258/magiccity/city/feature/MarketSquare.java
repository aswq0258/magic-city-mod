package io.github.aswq0258.magiccity.city.feature;

import io.github.aswq0258.magiccity.build.Placer;
import io.github.aswq0258.magiccity.city.CityLayout;
import io.github.aswq0258.magiccity.city.Deco;
import io.github.aswq0258.magiccity.city.Shapes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

import static io.github.aswq0258.magiccity.city.Palette.*;

/**
 * 시장 광장: 흰·민트 줄무늬 차양을 친 노점 4개 + 가운데 장식 가로등과 화분.
 * 노점마다 매대(직업 블록), 통과 화분, 차양 밑 랜턴.
 */
public class MarketSquare extends Feature {
    private final int mx, mz;
    private final BlockState[] goods = new BlockState[4];
    private final boolean villagers;
    private final long seed;

    public MarketSquare(CityLayout city, CityLayout.Lot lot, RandomSource rand, boolean villagers) {
        super(lot.minX(), lot.minZ(), lot.maxX(), lot.maxZ());
        this.mx = lot.centerX();
        this.mz = lot.centerZ();
        this.villagers = villagers;
        for (int i = 0; i < 4; i++) goods[i] = JOB_BLOCKS[rand.nextInt(JOB_BLOCKS.length)];
        this.seed = rand.nextLong();
    }

    @Override
    public void build(Placer p, CityLayout city) {
        final int b = groundY;
        p.forColumns(minX, minZ, maxX, maxZ, (x, z) -> {
            boolean border = x == minX || x == maxX || z == minZ || z == maxZ;
            p.set(x, b, z, border ? QUARTZ_BRICKS : (((x + z) & 1) == 0 ? SMOOTH_QUARTZ : CALCITE));
        });

        int i = 0;
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                buildStall(p, b, mx + sx * 4, mz + sz * 4, sx, sz, goods[i++]);
            }
        }

        // 가운데 장식 가로등 + 화분
        for (int y = b + 1; y <= b + 3; y++) p.set(mx, y, mz, QUARTZ_PILLAR);
        p.set(mx, b + 4, mz, CHISELED_QUARTZ);
        p.set(mx, b + 5, mz, LIGHT);
        p.set(mx, b + 6, mz, MINT_CHISELED);
        p.set(mx, b + 7, mz, END_ROD);
        int[][] around = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int k = 0; k < 4; k++) {
            p.set(mx + around[k][0], b + 1, mz + around[k][1], Deco.pot((int) (seed >> (k * 4))));
            p.set(mx + around[k][0] * 4, b + 1, mz + around[k][1] * 4, Deco.pot((int) (seed >> (k * 4 + 16))));
        }
    }

    private void buildStall(Placer p, int b, int scx, int scz, int sx, int sz, BlockState job) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int x = scx + dx, z = scz + dz;
                boolean corner = Math.abs(dx) == 1 && Math.abs(dz) == 1;
                if (corner) {
                    for (int y = b + 1; y <= b + 3; y++) p.set(x, y, z, QUARTZ_PILLAR);
                    p.set(x, b + 4, z, CHISELED_QUARTZ);
                } else {
                    p.set(x, b + 4, z, ((x & 1) == 0) ? Deco.WOOL_WHITE : Deco.WOOL_MINT);
                }
                if (dz == 0) p.set(x, b + 5, z, ((x & 1) == 0) ? Deco.WOOL_WHITE : Deco.WOOL_MINT);
            }
        }
        p.set(scx - sx, b + 1, scz, job);                       // 매대 (광장 중심 쪽)
        p.set(scx, b + 1, scz - sz, Deco.BARREL);
        p.set(scx, b + 2, scz - sz, Deco.pot(Shapes.hash(scx, 3, scz, seed)));
        p.set(scx + sx, b + 1, scz, Deco.BARREL);
        p.set(scx + sx, b + 2, scz, Deco.candles(3));
        p.set(scx, b + 3, scz, Deco.hangingLantern());
    }

    @Override
    public void spawn(ServerLevel level, CityLayout city) {
        if (!villagers) return;
        Shapes.spawnVillager(level, mx + 4.5, groundY + 1, mz + 4.5);
        Shapes.spawnVillager(level, mx - 3.5, groundY + 1, mz - 3.5);
    }
}
