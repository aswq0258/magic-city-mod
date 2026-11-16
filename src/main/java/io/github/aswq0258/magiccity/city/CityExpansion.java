package io.github.aswq0258.magiccity.city;

import io.github.aswq0258.magiccity.build.BuildLayout;
import io.github.aswq0258.magiccity.build.Placer;
import io.github.aswq0258.magiccity.city.feature.Feature;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.List;

/**
 * 이미 지어진 원형 도시는 건드리지 않고, 성곽 바깥(외곽 생활권·신시가지)만 짓는 설계도.
 * 예전 버전으로 도시를 지은 월드에서 /magiccity build expansion 으로 씀.
 */
public final class CityExpansion implements BuildLayout {
    private final CityLayout city;
    private final List<ChunkPos> chunks = new ArrayList<>();

    public CityExpansion(CityLayout city) {
        this.city = city;
        int r = CityLayout.EXP + 2;
        for (int x = (city.cx - r) >> 4; x <= (city.cx + r) >> 4; x++) {
            for (int z = (city.cz - r) >> 4; z <= (city.cz + r) >> 4; z++) {
                // 청크에서 중심으로부터 가장 먼 점이 성곽 바깥이면 포함
                int fx = Math.max(Math.abs((x << 4) - city.cx), Math.abs((x << 4) + 15 - city.cx));
                int fz = Math.max(Math.abs((z << 4) - city.cz), Math.abs((z << 4) + 15 - city.cz));
                if (Math.hypot(fx, fz) > CityLayout.R_APRON) chunks.add(new ChunkPos(x, z));
            }
        }
        chunks.sort(java.util.Comparator.comparingDouble(c -> Math.hypot((c.x << 4) + 8 - city.cx, (c.z << 4) + 8 - city.cz)));
    }

    private boolean outside(int x, int z) {
        return Math.hypot(x - city.cx, city.cz - z) > CityLayout.R_APRON;
    }

    @Override public int cx() { return city.cx; }
    @Override public int cz() { return city.cz; }
    @Override public int base() { return city.base; }
    @Override public int radius() { return city.radius; }
    @Override public CityLayout city() { return city; }
    @Override public List<ChunkPos> chunkList() { return chunks; }

    @Override
    public void buildColumn(Placer p, int x, int z) {
        if (outside(x, z)) city.buildColumn(p, x, z);
    }

    @Override
    public List<Feature> featuresIn(ChunkPos cp) {
        return city.outerFeaturesIn(cp);
    }

    @Override
    public int[] lightRange(int x, int z) {
        return outside(x, z) ? city.lightRange(x, z) : null;
    }
}
