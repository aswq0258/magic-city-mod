package io.github.aswq0258.magiccity.site;

import io.github.aswq0258.magiccity.build.BuildLayout;
import io.github.aswq0258.magiccity.build.Placer;
import io.github.aswq0258.magiccity.city.CityLayout;
import io.github.aswq0258.magiccity.city.feature.Feature;
import io.github.aswq0258.magiccity.world.KingdomChunkGenerator;
import io.github.aswq0258.magiccity.world.KingdomRoads;
import io.github.aswq0258.magiccity.world.KingdomTerrain;
import io.github.aswq0258.magiccity.world.Regrow;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * /magiccity fix forests: 이미 생성된 대륙에 새 숲 배치(산맥 남쪽 기슭 숲, 산비탈 침엽수림, 눈 덮인 가문비 숲,
 * 들판·촌락 둘레의 작은 숲) 를 입힘.
 *  - 청크의 바이옴을 새 지형 계산대로 다시 칠함 (풀 색·몹이 숲에 맞게)
 *  - 산비탈 숲 자리의 맨 바위 지표를 풀·눈 덮인 풀로 바꿈 (가파른 곳은 바위 그대로)
 *  - 빈 땅에만 나무·풀을 심음 (이미 우거진 곳, 길가, 왕성도시, 건축물 자리는 그대로)
 */
public final class ForestFix implements BuildLayout {
    private final int cx, cz, r;

    public ForestFix(int cx, int cz, int r) {
        this.cx = cx;
        this.cz = cz;
        this.r = r;
    }

    private static boolean forest(KingdomTerrain.Kind k) {
        return k == KingdomTerrain.Kind.FOREST || k == KingdomTerrain.Kind.TAIGA || k == KingdomTerrain.Kind.GROVE;
    }

    @Override
    public void buildColumn(Placer p, int x, int z) {
        ServerLevel level = p.level();
        if (p.pass() == Placer.Pass.SOLID) {
            if ((x & 15) == 0 && (z & 15) == 0) rebiome(level, x >> 4, z >> 4);
            return;
        }
        if (KingdomRoads.excluded(x, z) || Protected.at(x, z)) return;
        KingdomTerrain.Column c = KingdomTerrain.column(x, z);
        if (!forest(c.kind()) || c.onRoad()) return;
        BlockPos g = new BlockPos(x, c.h(), z);
        BlockState now = level.getBlockState(g);
        BlockState want = KingdomChunkGenerator.blockAt(c, x, c.h(), z);
        if (want != null && want.is(Blocks.GRASS_BLOCK) && level.getBlockState(g.above()).isAir()
                && (now.is(Blocks.STONE) || now.is(Blocks.ANDESITE) || now.is(Blocks.TUFF) || now.is(Blocks.SNOW_BLOCK) || now.is(Blocks.GRASS_BLOCK))) {
            level.setBlock(g, want, 2);                                                       // 바위 비탈 → 풀 (나무가 자랄 땅)
            BlockState cover = KingdomChunkGenerator.blockAt(c, x, c.h() + 1, z);
            if (cover != null && cover.is(Blocks.SNOW)) level.setBlock(g.above(), cover, 2);
        }
        Regrow.column(level, x, z);
    }

    /** 청크 바이옴을 새 계산대로 다시 칠하고 접속한 플레이어에게 다시 보냄 (/fillbiome 과 같은 방식) */
    static void rebiome(ServerLevel level, int chx, int chz) {
        LevelChunk ch = level.getChunk(chx, chz);
        BiomeSource src = level.getChunkSource().getGenerator().getBiomeSource();
        Map<Long, Holder<Biome>> cache = new HashMap<>();
        ch.fillBiomesFromNoise((qx, qy, qz, s) -> cache.computeIfAbsent(((long) qx << 32) ^ (qz & 0xffffffffL),
                k -> src.getNoiseBiome(qx, qy, qz, s)), level.getChunkSource().randomState().sampler());
        ch.setUnsaved(true);
        level.getChunkSource().chunkMap.resendBiomesForChunks(List.<ChunkAccess>of(ch));
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
