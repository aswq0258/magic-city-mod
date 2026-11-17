package io.github.aswq0258.magiccity.site;

import io.github.aswq0258.magiccity.build.BuildLayout;
import io.github.aswq0258.magiccity.build.Placer;
import io.github.aswq0258.magiccity.city.CityLayout;
import io.github.aswq0258.magiccity.city.feature.Feature;
import io.github.aswq0258.magiccity.world.ElfWood;
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
 * /magiccity fix elfwood: 이미 생성된 세계수 주변(반지름 360)을 "보랏빛 숲"으로 바꿈.
 * 바이옴을 elf_sanctum 으로 다시 칠하고(잎·풀·물·안개가 보랏빛), 잎 천장·장막, 매달린 빛 구슬·수정·덩굴, 빛나는 버섯·작은 나무를 깔고,
 * 진달래 잎 → 보랏빛 잎, 버섯불 → 펄빛 개구리불로 바꿈.
 */
public final class ElfWoodFix implements BuildLayout {
    private final List<ChunkPos> chunks = new ArrayList<>();

    public ElfWoodFix() {
        int r = ElfWood.RZ + 16;
        for (int x = (ElfWood.FX - r) >> 4; x <= (ElfWood.FX + r) >> 4; x++)
            for (int z = (ElfWood.FZ - r) >> 4; z <= (ElfWood.FZ + r) >> 4; z++)
                if (Math.hypot((x << 4) + 8 - ElfWood.FX, (z << 4) + 8 - ElfWood.FZ) <= r + 12) chunks.add(new ChunkPos(x, z));
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
        int cx = x >> 4, cz = z >> 4;
        TreeClearer.Access acc = new TreeClearer.Access() {
            @Override public BlockState get(BlockPos q) { return level.getBlockState(q); }
            @Override public void set(BlockPos q, BlockState s) { level.setBlock(q, s, 2); }
            @Override public boolean ok(int xx, int zz) { return (xx >> 4) == cx && (zz >> 4) == cz || Math.abs((xx >> 4) - cx) <= 1 && Math.abs((zz >> 4) - cz) <= 1; }
        };
        ElfWood.decorateChunk(acc, cx, cz, true);
    }

    @Override public int cx() { return ElfWood.FX; }
    @Override public int cz() { return ElfWood.FZ; }
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
