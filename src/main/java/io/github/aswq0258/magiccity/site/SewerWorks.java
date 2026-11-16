package io.github.aswq0258.magiccity.site;

import io.github.aswq0258.magiccity.build.BuildLayout;
import io.github.aswq0258.magiccity.build.Placer;
import io.github.aswq0258.magiccity.city.CityLayout;
import io.github.aswq0258.magiccity.city.Shapes;
import io.github.aswq0258.magiccity.city.feature.Feature;
import io.github.aswq0258.magiccity.city.feature.Sewer;
import io.github.aswq0258.magiccity.world.KingdomTerrain;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 왕성 하수도 확장 — "크지만 들어갈 수 있는 곳은 적은" 하수도.
 * 왕궁 아래 순환 하수로(실제로 다니는 곳)에서 동서남북으로 거대한 간선 하수도 4개가 도시 끝까지 뻗어 나가지만,
 * 모두 입구가 쇠창살 격자로 막혀 있어 들여다보기만 할 수 있음.
 *  - 간선: 폭 9 의 아치형 굴, 가운데 하수 물길과 양옆 보행로, 멀어질수록 어두워지고 무너진 잔해, 끝은 완전히 무너짐
 *  - 간선 옆으로 격자로 막힌 지선 입구들, 북쪽 간선 중간의 거대한 집수 홀 (기둥·저수조) 이 창살 너머 멀리 보임
 * 왕국 대륙 왕성도시(260, 260) 기준. 실제 동선(순환 하수로·납골당·저수조·감옥)은 그대로.
 */
public final class SewerWorks implements BuildLayout {
    public static final int F = KingdomTerrain.CITY_Y - Sewer.DEPTH;

    /** 간선 {X 방향이면 1, 고정 좌표, 시작(창살 쪽), 끝, 창살 두 줄 위치 a, b} */
    private static final int[][] TRUNKS = {
            {1, 250, 195, 40, 196, 197},     // 서쪽
            {1, 232, 325, 470, 323, 324},    // 동쪽
            {0, 290, 200, 60, 201, 202},     // 북쪽
            {0, 232, 320, 460, 318, 319},    // 남쪽
    };
    /** 집수 홀 {x0, z0, x1, z1} (북쪽 간선 중간) */
    private static final int[] HALL = {280, 118, 300, 140};

    private final long seed;
    private final List<ChunkPos> chunks = new ArrayList<>();

    public SewerWorks(long seed) {
        this.seed = seed;
        LinkedHashSet<Long> keys = new LinkedHashSet<>();
        for (int[] t : TRUNKS) {
            int a0 = Math.min(t[2], t[3]) - 2, a1 = Math.max(t[2], t[3]) + 4, c0 = t[1] - 16, c1 = t[1] + 16;
            int x0 = t[0] == 1 ? a0 : c0, x1 = t[0] == 1 ? a1 : c1, z0 = t[0] == 1 ? c0 : a0, z1 = t[0] == 1 ? c1 : a1;
            for (int x = x0 >> 4; x <= x1 >> 4; x++) for (int z = z0 >> 4; z <= z1 >> 4; z++) keys.add(ChunkPos.asLong(x, z));
        }
        for (long k : keys) chunks.add(new ChunkPos(k));
    }

    /** 철거용 영역들 {x0, z0, x1, z1} */
    public static List<int[]> boxes() {
        List<int[]> out = new ArrayList<>();
        for (int[] t : TRUNKS) {
            int a0 = Math.min(t[2], t[3]), a1 = Math.max(t[2], t[3]), c0 = t[1] - 12, c1 = t[1] + 12;
            out.add(t[0] == 1 ? new int[]{a0, c0, a1, c1} : new int[]{c0, a0, c1, a1});
        }
        return out;
    }

    @Override public int cx() { return 260; }
    @Override public int cz() { return 260; }
    @Override public int base() { return F; }
    @Override public int radius() { return 0; }
    @Override public CityLayout city() { return null; }
    @Override public List<ChunkPos> chunkList() { return chunks; }
    @Override public List<Feature> featuresIn(ChunkPos cp) { return Collections.emptyList(); }
    @Override public int[] lightRange(int x, int z) { return null; }
    @Override public int shapeMinY() { return F - 4; }
    @Override public int shapeMaxY() { return F + 14; }

    private int h(int x, int y, int z) {
        return Shapes.hash(x, y, z, seed);
    }

    private BlockState brick(int x, int y, int z) {
        int k = h(x, y, z) % 10;
        return k < 3 ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState() : k < 5 ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState();
    }

    private BlockState rubble(int x, int y, int z) {
        int k = h(x, y, z + 3) % 6;
        return k < 2 ? Blocks.COBBLESTONE.defaultBlockState() : k < 4 ? Blocks.GRAVEL.defaultBlockState() : k < 5 ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : Blocks.DIRT.defaultBlockState();
    }

    @Override
    public void buildColumn(Placer p, int x, int z) {
        if (p.pass() != Placer.Pass.SOLID) return;
        if (x >= HALL[0] - 1 && x <= HALL[2] + 1 && z >= HALL[1] - 1 && z <= HALL[3] + 1) {
            hall(p, x, z);
            return;
        }
        for (int[] t : TRUNKS) trunk(p, x, z, t);
    }

    private void trunk(Placer p, int x, int z, int[] t) {
        boolean alongX = t[0] == 1;
        int along = alongX ? x : z, lat = alongX ? z - t[1] : x - t[1];
        int al = Math.abs(lat);
        // 창살: 순환 하수로 벽 두 줄을 쇠창살 창으로 (폭 5, 높이 5)
        if (along == t[4] || along == t[5]) {
            if (al <= 2) for (int y = F + 1; y <= F + 5; y++) p.set(x, y, z, Blocks.IRON_BARS.defaultBlockState());
            return;
        }
        int lo = Math.min(t[2], t[3]), hi = Math.max(t[2], t[3]);
        if (along < lo || along > hi) return;
        double frac = Math.abs(along - t[2]) / (double) Math.abs(t[3] - t[2]);   // 0 = 창살 쪽, 1 = 끝
        int sideStub = stub(along, t);
        if (al > 6) {
            if (sideStub != 0 && Integer.signum(lat) == sideStub && al <= 12) stubColumn(p, x, z, al, frac);
            return;
        }
        // 바깥 껍질과 벽
        if (al >= 5) {
            boolean window = sideStub != 0 && Integer.signum(lat) == sideStub;
            for (int y = F - 1; y <= F + 9; y++) {
                if (window && y >= F + 1 && y <= F + 4) p.set(x, y, z, al == 5 ? Blocks.IRON_BARS.defaultBlockState() : Blocks.AIR.defaultBlockState());
                else p.set(x, y, z, brick(x, y, z));
            }
            return;
        }
        // 안쪽: 아치 천장, 가운데 물길, 양옆 보행로
        int top = F + 8 - (lat * lat) / 5;
        p.set(x, F - 1, z, brick(x, F - 1, z));
        p.set(x, F, z, al <= 1 ? Blocks.WATER.defaultBlockState() : brick(x, F, z));
        for (int y = F + 1; y <= top; y++) p.set(x, y, z, Blocks.AIR.defaultBlockState());
        p.set(x, top + 1, z, brick(x, top + 1, z));
        int k = h(x, F, z);
        // 끝: 완전히 무너짐
        if (frac > 0.96) {
            int pile = (int) Math.round((frac - 0.96) / 0.04 * (top - F));
            for (int y = F + 1; y <= F + Math.max(1, pile); y++) p.set(x, y, z, rubble(x, y, z));
            return;
        }
        // 멀어질수록 잔해 더미
        if (frac > 0.5 && al >= 2 && k % Math.max(3, (int) (14 - frac * 10)) == 0) {
            int hp = 1 + k % 3;
            for (int y = F + 1; y <= F + hp; y++) p.set(x, y, z, rubble(x, y, z));
        }
        if (frac > 0.78 && frac < 0.82) {                                                    // 중간에 무너진 둔덕
            int pile = 3 - al / 2;
            for (int y = F + 1; y <= F + pile; y++) p.set(x, y, z, rubble(x, y, z));
        }
        // 등불: 창살 가까이만 (멀리는 어둠)
        if (lat == 0 && frac < 0.55 && Math.floorMod(along, 12) == 6) p.set(x, top, z, Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        else if (al == 4 && k % 23 == 0) p.set(x, F + 1, z, k % 2 == 0 ? Blocks.BONE_BLOCK.defaultBlockState() : Blocks.SKELETON_SKULL.defaultBlockState());
        else if (al >= 3 && k % 31 == 0) p.set(x, F + 5, z, Blocks.COBWEB.defaultBlockState());
        else if (al == 4 && k % 37 == 0) p.set(x, F + 1, z, Blocks.BARREL.defaultBlockState());
    }

    /** 이 칸에 지선 입구가 있으면 그 방향(+1/-1), 없으면 0 — 26칸마다 번갈아 */
    private static int stub(int along, int[] t) {
        int d = Math.abs(along - t[2]);
        if (d < 14) return 0;
        int m = Math.floorMod(d, 26);
        if (m < 11 || m > 13) return 0;
        return (d / 26) % 2 == 0 ? 1 : -1;
    }

    /** 지선: 폭 3 의 짧은 어두운 굴, 6칸 들어가서 무너짐 */
    private void stubColumn(Placer p, int x, int z, int al, double frac) {
        int k = h(x, F, z);
        for (int y = F - 1; y <= F + 6; y++) p.set(x, y, z, brick(x, y, z));
        if (al >= 7 && al <= 11) {
            p.set(x, F, z, brick(x, F, z));
            for (int y = F + 1; y <= F + 4; y++) p.set(x, y, z, al >= 10 ? rubble(x, y, z) : Blocks.AIR.defaultBlockState());
            if (al == 9 && k % 3 == 0) p.set(x, F + 1, z, rubble(x, F + 1, z));
        }
    }

    // ───────────────────────── 집수 홀 ─────────────────────────

    private void hall(Placer p, int x, int z) {
        boolean wall = x == HALL[0] - 1 || x == HALL[2] + 1 || z == HALL[1] - 1 || z == HALL[3] + 1;
        int trunkLat = Math.abs(x - 290);
        if (wall) {
            for (int y = F - 2; y <= F + 14; y++) {
                boolean door = trunkLat <= 4 && (z == HALL[1] - 1 || z == HALL[3] + 1) && y >= F && y <= F + 7;
                if (!door) p.set(x, y, z, brick(x, y, z));
            }
            return;
        }
        int lx = x - HALL[0], lz = z - HALL[1];
        boolean pillar = Math.floorMod(lx - 2, 6) == 0 && Math.floorMod(lz - 2, 6) == 0 && trunkLat > 4;
        p.set(x, F - 2, z, brick(x, F - 2, z));
        boolean pool = trunkLat > 5 && Math.abs(z - (HALL[1] + HALL[3]) / 2) <= 6;
        p.set(x, F - 1, z, pool ? Blocks.WATER.defaultBlockState() : brick(x, F - 1, z));
        p.set(x, F, z, pool ? Blocks.WATER.defaultBlockState() : trunkLat <= 1 ? Blocks.WATER.defaultBlockState() : brick(x, F, z));
        for (int y = F + 1; y <= F + 13; y++) p.set(x, y, z, pillar ? (y == F + 9 ? Blocks.SOUL_LANTERN.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState()) : Blocks.AIR.defaultBlockState());
        p.set(x, F + 14, z, brick(x, F + 14, z));
        if (!pillar && h(x, F, z) % 41 == 0) p.set(x, F + 13, z, Blocks.CHAIN.defaultBlockState());
    }
}
