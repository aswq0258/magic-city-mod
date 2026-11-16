package io.github.aswq0258.magiccity.site;

import io.github.aswq0258.magiccity.build.BuildLayout;
import io.github.aswq0258.magiccity.build.Placer;
import io.github.aswq0258.magiccity.city.CityLayout;
import io.github.aswq0258.magiccity.city.feature.Feature;
import io.github.aswq0258.magiccity.world.KingdomRoads;
import io.github.aswq0258.magiccity.world.KingdomTerrain;
import io.github.aswq0258.magiccity.world.LavaClear;
import io.github.aswq0258.magiccity.world.TreeClearer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Collections;
import java.util.List;

/**
 * /magiccity fix lava: 이미 생성된 대륙에서 지표 가까이(Y 50 위)의 용암 호수·용암 샘을 물로 바꿈.
 * 왕성도시와 지어 둔 건축물(드워프 대장간·옛 왕도 등 일부러 둔 용암) 자리는 건드리지 않음.
 */
public final class LavaFix implements BuildLayout {
    private final int cx, cz, r;
    private int changed;

    public LavaFix(int cx, int cz, int r) {
        this.cx = cx;
        this.cz = cz;
        this.r = r;
    }

    @Override
    public void buildColumn(Placer p, int x, int z) {
        if (p.pass() != Placer.Pass.SOLID) return;
        if (KingdomRoads.excluded(x, z) || Protected.at(x, z)) return;
        ServerLevel lv = p.level();
        TreeClearer.Access acc = new TreeClearer.Access() {
            @Override public BlockState get(BlockPos q) { return lv.getBlockState(q); }
            @Override public void set(BlockPos q, BlockState s) { lv.setBlock(q, s, 3); }
            @Override public boolean ok(int xx, int zz) { return true; }
        };
        changed += LavaClear.column(acc, x, z, lv.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) + 2);
    }

    @Override public int cx() { return cx; }
    @Override public int cz() { return cz; }
    @Override public int base() { return KingdomTerrain.SEA; }
    @Override public int radius() { return r; }
    @Override public CityLayout city() { return null; }
    @Override public List<Feature> featuresIn(ChunkPos cp) { return Collections.emptyList(); }
    @Override public int[] lightRange(int x, int z) { return null; }
    @Override public int shapeMinY() { return 0; }
    @Override public int shapeMaxY() { return 0; }
}
