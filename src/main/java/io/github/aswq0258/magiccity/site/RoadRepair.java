package io.github.aswq0258.magiccity.site;

import io.github.aswq0258.magiccity.build.BuildLayout;
import io.github.aswq0258.magiccity.build.Placer;
import io.github.aswq0258.magiccity.city.CityLayout;
import io.github.aswq0258.magiccity.city.feature.Feature;
import io.github.aswq0258.magiccity.world.KingdomChunkGenerator;
import io.github.aswq0258.magiccity.world.KingdomRoads;
import io.github.aswq0258.magiccity.world.KingdomTerrain;
import io.github.aswq0258.magiccity.world.TreeClearer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 이미 생성된 왕국 대륙에서 길만 그 자리에서 고침 (대륙을 다시 생성하지 않아도 됨).
 *  - 왕성도시 근처(사방 1700 안): 길이 지나는 띠(양옆 45) 와 도시 남북 강 끝을 지형째 다시 깖 → 도시 대로와 어긋나던 길·강을 바로잡음
 *  - 그 바깥: 길은 그대로 두고 길 위·양옆 5블록 안의 나무만 걷어냄
 * 왕성도시 범위(사방 622 안) 는 건드리지 않음 (/magiccity build expansion 으로 따로).
 */
public final class RoadRepair implements BuildLayout {
    private static final int NEAR = 1700, BAND = 45, CITY = 622;
    /** 도시 남북 끝에서 강이 바뀐 곳 {x0, z0, x1, z1} */
    private static final int[][] RIVER_ZONES = {{500, -600, 780, -280}, {500, 800, 780, 1100}};
    private final List<ChunkPos> chunks = new ArrayList<>();

    public RoadRepair() {
        LinkedHashSet<Long> keys = new LinkedHashSet<>();
        for (KingdomRoads.Road r : KingdomRoads.ROADS) {
            int[][] p = r.pts();
            for (int i = 0; i + 1 < p.length; i++) {
                double len = Math.hypot(p[i + 1][0] - p[i][0], p[i + 1][1] - p[i][1]);
                for (double t = 0; t <= len; t += 8) {
                    double x = p[i][0] + (p[i + 1][0] - p[i][0]) * t / len, z = p[i][1] + (p[i + 1][1] - p[i][1]) * t / len;
                    int reach = near(x, z) ? BAND + 40 : 40;                         // 구불거림(최대 30) + 여유
                    for (int dx = -reach; dx <= reach; dx += 16) for (int dz = -reach; dz <= reach; dz += 16)
                        keys.add(ChunkPos.asLong(((int) Math.floor(x + dx)) >> 4, ((int) Math.floor(z + dz)) >> 4));
                }
            }
        }
        for (int[] q : RIVER_ZONES) for (int x = q[0] >> 4; x <= q[2] >> 4; x++) for (int z = q[1] >> 4; z <= q[3] >> 4; z++) keys.add(ChunkPos.asLong(x, z));
        for (long k : keys) {
            ChunkPos cp = new ChunkPos(k);
            int mx = Math.max(Math.abs(cp.getMinBlockX() - KingdomTerrain.CX), Math.abs(cp.getMaxBlockX() - KingdomTerrain.CX));
            int mz = Math.max(Math.abs(cp.getMinBlockZ() - KingdomTerrain.CZ), Math.abs(cp.getMaxBlockZ() - KingdomTerrain.CZ));
            if (mx <= CITY && mz <= CITY) continue;                                    // 도시 안쪽 청크
            chunks.add(cp);
        }
    }

    private static boolean near(double x, double z) {
        return Math.max(Math.abs(x - KingdomTerrain.CX), Math.abs(z - KingdomTerrain.CZ)) < NEAR;
    }

    private static boolean inRiverZone(int x, int z) {
        for (int[] q : RIVER_ZONES) if (x >= q[0] && x <= q[2] && z >= q[1] && z <= q[3]) return true;
        return false;
    }

    public int chunkCount() {
        return chunks.size();
    }

    @Override
    public void buildColumn(Placer p, int x, int z) {
        if (Math.abs(x - KingdomTerrain.CX) <= CITY && Math.abs(z - KingdomTerrain.CZ) <= CITY) return;
        boolean near = near(x, z);
        double[] edge = new double[1];
        double offset = near ? BAND - 3 : 0;
        int ri = KingdomRoads.nearest(x, z, TreeClearer.ORPHAN + offset, edge);
        boolean river = near && inRiverZone(x, z);
        if (ri < 0 && !river) return;
        KingdomTerrain.Column c = KingdomTerrain.column(x, z);
        int top = p.level().getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
        if (p.pass() == Placer.Pass.SOLID && ri >= 0) {                                // 길가 나무는 한 그루째 (잘린 나무·떠 있는 잎 정리 포함)
            net.minecraft.server.level.ServerLevel lv = p.level();
            TreeClearer.Access acc = new TreeClearer.Access() {
                @Override public BlockState get(net.minecraft.core.BlockPos q) { return lv.getBlockState(q); }
                @Override public void set(net.minecraft.core.BlockPos q, BlockState s) { lv.setBlock(q, s, 2 | 16); }
                @Override public boolean ok(int xx, int zz) { return true; }
            };
            int y0 = Math.max(c.h() + 1, c.water() == Integer.MIN_VALUE ? c.h() + 1 : c.water() + 3);
            TreeClearer.clearColumn(acc, x, z, y0, top, edge[0], offset);
            top = p.level().getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
        }
        if (near && (ri >= 0 && edge[0] <= BAND || river)) {                           // 띠 전체를 지형째 다시 깖
            int y1 = Math.max(c.h() + 8, Math.min(top, c.h() + 48));
            for (int y = c.h() - 8; y <= y1; y++) {
                BlockState s = KingdomChunkGenerator.blockAt(c, x, y, z);
                p.set(x, y, z, s == null ? Blocks.AIR.defaultBlockState() : s);
            }
            return;
        }
    }

    @Override public int cx() { return KingdomTerrain.CX; }
    @Override public int cz() { return KingdomTerrain.CZ; }
    @Override public int base() { return KingdomTerrain.SEA; }
    @Override public int radius() { return 0; }
    @Override public CityLayout city() { return null; }
    @Override public List<ChunkPos> chunkList() { return chunks; }
    @Override public int shapeMinY() { return 40; }
    @Override public int shapeMaxY() { return 200; }
    @Override public List<Feature> featuresIn(ChunkPos cp) { return Collections.emptyList(); }
    @Override public int[] lightRange(int x, int z) { return null; }
}
