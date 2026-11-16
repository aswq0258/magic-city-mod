package io.github.aswq0258.magiccity.site;

import io.github.aswq0258.magiccity.build.BuildLayout;
import io.github.aswq0258.magiccity.build.Placer;
import io.github.aswq0258.magiccity.city.CityLayout;
import io.github.aswq0258.magiccity.city.feature.Feature;
import io.github.aswq0258.magiccity.world.DeepWood;
import io.github.aswq0258.magiccity.world.KingdomRoads;
import io.github.aswq0258.magiccity.world.KingdomTerrain;
import io.github.aswq0258.magiccity.world.Regrow;
import io.github.aswq0258.magiccity.world.TreeClearer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * /magiccity fix seams: 철거(remove)한 네모난 영역의 가장자리에서 나무가 칼로 자른 듯 반쪽만 남은 곳을 고침.
 *  - 태고의 숲: 고목은 위치가 정해진 계산식이라, 가장자리 근처 청크의 고목·쓰러진 통나무를 다시 그려 잘린 반쪽을 되살림
 *  - 그 밖의 숲: 가장자리 ±10칸에 걸친 나무는 통째로 치우고, 주인 잃은 잎을 지운 뒤 바이옴에 맞는 나무를 다시 심음
 * 지어 둔 건축물 자리(Protected)의 나무는 건드리지 않음.
 */
public final class SeamFix implements BuildLayout {
    private static final int BAND = 10;
    private final List<int[]> boxes = new ArrayList<>();
    private final List<ChunkPos> chunks = new ArrayList<>();

    public SeamFix() {
        for (Restore r : Restore.allSites()) {
            if (!r.formula()) continue;
            for (int[] b : r.areas()) {
                if (b[5] < KingdomTerrain.column((b[0] + b[2]) / 2, (b[1] + b[3]) / 2).h()) continue;      // 땅속만 되돌리는 영역(던전)은 지표에 자국이 없음
                boxes.add(b);
            }
        }
        LinkedHashSet<Long> keys = new LinkedHashSet<>();
        for (int[] b : boxes) {
            int m = BAND + 32;
            for (int cx = (b[0] - m) >> 4; cx <= (b[2] + m) >> 4; cx++) {
                for (int cz = (b[1] - m) >> 4; cz <= (b[3] + m) >> 4; cz++) {
                    if (edgeDist((cx << 4) + 8, (cz << 4) + 8) <= BAND + 40) keys.add(ChunkPos.asLong(cx, cz));
                }
            }
        }
        for (long k : keys) chunks.add(new ChunkPos(k));
    }

    public int chunkCount() {
        return chunks.size();
    }

    /** 가장 가까운 철거 영역 테두리까지 거리 */
    private double edgeDist(int x, int z) {
        double best = 1e9;
        for (int[] b : boxes) {
            boolean in = x >= b[0] && x <= b[2] && z >= b[1] && z <= b[3];
            double d;
            if (in) d = Math.min(Math.min(x - b[0], b[2] - x), Math.min(z - b[1], b[3] - z));
            else {
                double dx = Math.max(Math.max(b[0] - x, 0), x - b[2]), dz = Math.max(Math.max(b[1] - z, 0), z - b[3]);
                d = Math.hypot(dx, dz);
            }
            best = Math.min(best, d);
        }
        return best;
    }

    @Override
    public void buildColumn(Placer p, int x, int z) {
        if (p.pass() != Placer.Pass.WATER) return;
        ServerLevel level = p.level();
        TreeClearer.Access acc = new TreeClearer.Access() {
            @Override public BlockState get(BlockPos q) { return level.getBlockState(q); }
            @Override public void set(BlockPos q, BlockState s) { level.setBlock(q, s, 2); }
            @Override public boolean ok(int xx, int zz) { return !Protected.at(xx, zz) || DeepWood.in(xx, zz); }
        };
        // 태고의 숲 청크: 고목을 다시 그림 (잘린 반쪽이 되살아남)
        if ((x & 15) == 0 && (z & 15) == 0 && (DeepWood.in(x + 8, z + 8) || DeepWood.in(x, z) || DeepWood.in(x + 15, z + 15))) {
            TreeClearer.Access all = new TreeClearer.Access() {
                @Override public BlockState get(BlockPos q) { return level.getBlockState(q); }
                @Override public void set(BlockPos q, BlockState s) { level.setBlock(q, s, 2); }
                @Override public boolean ok(int xx, int zz) { return true; }
            };
            DeepWood.decorateChunk(all, x >> 4, z >> 4, false);
        }
        if (DeepWood.in(x, z) || Protected.at(x, z) || KingdomRoads.excluded(x, z)) return;
        if (edgeDist(x, z) > BAND) return;
        KingdomTerrain.Column c = KingdomTerrain.column(x, z);
        int g = c.h();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        boolean touched = false;
        for (int y = g + 1; y <= g + 45; y++) {
            BlockState s = level.getBlockState(m.set(x, y, z));
            if (s.isAir()) continue;
            if (TreeClearer.wood(s)) {
                TreeClearer.removeTree(acc, m.immutable());
                touched = true;
            } else if (TreeClearer.foliage(s) && TreeClearer.nearestWood(acc, m, 4) == null) {
                level.setBlock(m.immutable(), Blocks.AIR.defaultBlockState(), 2);
                touched = true;
            }
        }
        if (touched || level.getBlockState(new BlockPos(x, g + 1, z)).isAir()) Regrow.column(level, x, z);
    }

    @Override public int cx() { return 0; }
    @Override public int cz() { return 0; }
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
