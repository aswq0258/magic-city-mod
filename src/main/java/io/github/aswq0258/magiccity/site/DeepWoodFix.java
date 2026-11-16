package io.github.aswq0258.magiccity.site;

import io.github.aswq0258.magiccity.build.BuildLayout;
import io.github.aswq0258.magiccity.build.Placer;
import io.github.aswq0258.magiccity.city.CityLayout;
import io.github.aswq0258.magiccity.city.feature.Feature;
import io.github.aswq0258.magiccity.world.DeepWood;
import io.github.aswq0258.magiccity.world.KingdomTerrain;
import io.github.aswq0258.magiccity.world.TreeClearer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * /magiccity fix deepwood: 이미 생성된 대륙의 엘프 대수림 심부에 "태고의 숲" 을 입힘
 * (바이옴을 짙은 숲으로 다시 칠하고, 고목·쓰러진 통나무·선돌·버섯 고리·이끼 바닥을 깖).
 * 세계수 성역·길·촌락 자리는 건드리지 않음.
 */
public final class DeepWoodFix implements BuildLayout {
    private final List<ChunkPos> chunks = new ArrayList<>();

    public DeepWoodFix() {
        for (int x = (DeepWood.FX - 980) >> 4; x <= (DeepWood.FX + 980) >> 4; x++) {
            for (int z = (DeepWood.FZ - 860) >> 4; z <= (DeepWood.FZ + 860) >> 4; z++) {
                int bx = (x << 4) + 8, bz = (z << 4) + 8;
                if (DeepWood.in(bx, bz) || DeepWood.in(bx - 8, bz - 8) || DeepWood.in(bx + 7, bz + 7)) chunks.add(new ChunkPos(x, z));
            }
        }
    }

    public int chunkCount() {
        return chunks.size();
    }

    @Override
    public void buildColumn(Placer p, int x, int z) {
        if ((x & 15) != 0 || (z & 15) != 0) return;
        ServerLevel level = p.level();
        if (p.pass() == Placer.Pass.SOLID) {
            ForestFix.rebiome(level, x >> 4, z >> 4);
            return;
        }
        if (Protected.at(x + 8, z + 8) && !(Math.hypot(x + 8 - DeepWood.FX, z + 8 - DeepWood.FZ) < WorldTree.R + 20)) return;
        TreeClearer.Access acc = new TreeClearer.Access() {
            @Override public BlockState get(BlockPos q) { return level.getBlockState(q); }
            @Override public void set(BlockPos q, BlockState s) { level.setBlock(q, s, 2); }
            @Override public boolean ok(int xx, int zz) { return true; }
        };
        DeepWood.decorateChunk(acc, x >> 4, z >> 4, true);
    }

    @Override public int cx() { return DeepWood.FX; }
    @Override public int cz() { return DeepWood.FZ; }
    @Override public int base() { return KingdomTerrain.SEA; }
    @Override public int radius() { return 0; }
    @Override public CityLayout city() { return null; }
    @Override public List<ChunkPos> chunkList() { return chunks; }
    @Override public List<Feature> featuresIn(ChunkPos cp) { return Collections.emptyList(); }
    @Override public int[] lightRange(int x, int z) { return null; }
    @Override public boolean waterPassAll() { return true; }
    @Override public int shapeMinY() { return 0; }
    @Override public int shapeMaxY() { return 0; }
}
