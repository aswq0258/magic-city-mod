package io.github.aswq0258.magiccity.city.feature;

import io.github.aswq0258.magiccity.build.Placer;
import io.github.aswq0258.magiccity.city.CityLayout;
import io.github.aswq0258.magiccity.city.Shapes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

import static io.github.aswq0258.magiccity.city.Palette.*;

/** 시장: 석영 기둥과 민트 지붕의 노점 4개 + 가운데 가로등. 노점마다 직업 블록. */
public class MarketSquare extends Feature {
    private final int mx, mz;
    private final BlockState[] goods = new BlockState[4];
    private final boolean villagers;

    public MarketSquare(CityLayout city, CityLayout.Lot lot, RandomSource rand, boolean villagers) {
        super(lot.minX(), lot.minZ(), lot.maxX(), lot.maxZ());
        this.mx = lot.centerX();
        this.mz = lot.centerZ();
        this.villagers = villagers;
        for (int i = 0; i < 4; i++) goods[i] = JOB_BLOCKS[rand.nextInt(JOB_BLOCKS.length)];
    }

    @Override
    public void build(Placer p, CityLayout city) {
        int b = city.base;
        p.forColumns(minX, minZ, maxX, maxZ, (x, z) -> p.set(x, b, z, SMOOTH_QUARTZ));

        int i = 0;
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                int scx = mx + sx * 4, scz = mz + sz * 4;
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        int x = scx + dx, z = scz + dz;
                        if (Math.abs(dx) == 1 && Math.abs(dz) == 1) {
                            for (int y = b + 1; y <= b + 3; y++) p.set(x, y, z, QUARTZ_PILLAR);
                        }
                        p.set(x, b + 4, z, (dx == 0 && dz == 0) ? MINT_CUT : MINT_SLAB);
                    }
                }
                p.set(scx, b + 1, scz, goods[i++]);
            }
        }
        Shapes.lampPost(p, mx, mz, b);
    }

    @Override
    public void spawn(ServerLevel level, CityLayout city) {
        if (!villagers) return;
        Shapes.spawnVillager(level, mx + 2.5, city.base + 1, mz + 0.5);
        Shapes.spawnVillager(level, mx - 1.5, city.base + 1, mz + 0.5);
    }
}
