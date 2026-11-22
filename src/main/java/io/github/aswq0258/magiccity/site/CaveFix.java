package io.github.aswq0258.magiccity.site;

import io.github.aswq0258.magiccity.build.BuildLayout;
import io.github.aswq0258.magiccity.build.Placer;
import io.github.aswq0258.magiccity.city.CityLayout;
import io.github.aswq0258.magiccity.city.feature.Feature;
import io.github.aswq0258.magiccity.world.CaveRules;
import io.github.aswq0258.magiccity.world.KingdomTerrain;
import io.github.aswq0258.magiccity.world.UndergroundZones;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Collections;
import java.util.List;

/**
 * /magiccity fix caves (2026-11-22): 동굴 규칙이 생기기 전에 생성된 땅에 같은 모양의 동굴을 파냄.
 * Y 0 미만의 돌 종류(심층암·돌·응회암 등)만 파고 광석·다른 블록은 남김. 던전 영역 + 8칸은 건드리지 않음 (CaveRules).
 */
public final class CaveFix implements BuildLayout {
    private static final BlockState AIR = Blocks.AIR.defaultBlockState(), LAVA = Blocks.LAVA.defaultBlockState();
    private final int cx, cz, r;

    public CaveFix(int cx, int cz, int r) {
        this.cx = cx;
        this.cz = cz;
        this.r = r;
    }

    private static boolean rock(BlockState s) {
        return s.is(Blocks.DEEPSLATE) || s.is(Blocks.STONE) || s.is(Blocks.TUFF) || s.is(Blocks.GRANITE)
                || s.is(Blocks.DIORITE) || s.is(Blocks.ANDESITE) || s.is(Blocks.GRAVEL) || s.is(Blocks.DIRT);
    }

    @Override
    public void buildColumn(Placer p, int x, int z) {
        if (p.pass() != Placer.Pass.SOLID) return;
        for (int y = CaveRules.BOTTOM; y <= CaveRules.TOP; y++) {
            if (!UndergroundZones.carve(x, y, z)) continue;
            if (!rock(p.get(x, y, z))) continue;
            p.set(x, y, z, CaveRules.lava(y) ? LAVA : AIR);
        }
    }

    @Override public int cx() { return cx; }
    @Override public int cz() { return cz; }
    @Override public int base() { return KingdomTerrain.SEA; }
    @Override public int radius() { return r; }
    @Override public CityLayout city() { return null; }
    @Override public List<Feature> featuresIn(ChunkPos cp) { return Collections.emptyList(); }
    @Override public int[] lightRange(int x, int z) { return null; }
    @Override public int shapeMinY() { return CaveRules.BOTTOM; }
    @Override public int shapeMaxY() { return CaveRules.TOP; }
}
