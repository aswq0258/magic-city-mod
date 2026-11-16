package io.github.aswq0258.magiccity.site;

import io.github.aswq0258.magiccity.build.BuildLayout;
import io.github.aswq0258.magiccity.build.Placer;
import io.github.aswq0258.magiccity.city.CityLayout;
import io.github.aswq0258.magiccity.city.feature.Feature;
import io.github.aswq0258.magiccity.world.KingdomRoads;
import io.github.aswq0258.magiccity.world.KingdomTerrain;
import io.github.aswq0258.magiccity.world.Regrow;
import net.minecraft.world.level.ChunkPos;

import java.util.Collections;
import java.util.List;

/**
 * /magiccity fix nature: 철거·길 고치기로 나무가 사라진 빈 땅(숲 속 네모난 빈터 등)에 바이옴에 맞는 나무·풀을 다시 심음.
 * 이미 나무가 우거진 곳, 길 가까이, 왕성도시와 지어 둔 건축물 자리는 건드리지 않음.
 */
public final class NatureFix implements BuildLayout {
    private final int cx, cz, r;

    public NatureFix(int cx, int cz, int r) {
        this.cx = cx;
        this.cz = cz;
        this.r = r;
    }

    @Override
    public void buildColumn(Placer p, int x, int z) {
        if (p.pass() != Placer.Pass.WATER) return;
        if (KingdomRoads.excluded(x, z) || Protected.at(x, z)) return;
        Regrow.column(p.level(), x, z);
    }

    @Override public int cx() { return cx; }
    @Override public int cz() { return cz; }
    @Override public int base() { return KingdomTerrain.SEA; }
    @Override public int radius() { return r; }
    @Override public CityLayout city() { return null; }
    @Override public List<Feature> featuresIn(ChunkPos cp) { return Collections.emptyList(); }
    @Override public int[] lightRange(int x, int z) { return null; }
    @Override public boolean waterPassAll() { return true; }
    @Override public int shapeMinY() { return 0; }
    @Override public int shapeMaxY() { return 0; }
}
