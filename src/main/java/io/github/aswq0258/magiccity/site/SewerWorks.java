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
    private final Den den = new Den();

    /** 원래 하수로 물길 가운데 줄 {X 방향이면 1, 고정 좌표, 시작, 끝} (왕국 대륙 왕성도시 기준 월드 좌표) */
    private static final int[][] CHANNELS = {
            {0, 125, 318, 448}, {1, 315, 128, 197},
            {0, 200, 208, 312}, {0, 320, 208, 312}, {1, 205, 203, 317}, {1, 315, 203, 317},
            {0, 260, 281, 312}, {1, 260, 323, 370}};
    /** 뒷골목 은신처 (안쪽) */
    static final int DX0 = 102, DX1 = 118, DZ0 = 425, DZ1 = 439;

    public SewerWorks(long seed) {
        this.seed = seed;
        LinkedHashSet<Long> keys = new LinkedHashSet<>();
        for (int[] t : TRUNKS) {
            int a0 = Math.min(t[2], t[3]) - 2, a1 = Math.max(t[2], t[3]) + 4, c0 = t[1] - 16, c1 = t[1] + 16;
            int x0 = t[0] == 1 ? a0 : c0, x1 = t[0] == 1 ? a1 : c1, z0 = t[0] == 1 ? c0 : a0, z1 = t[0] == 1 ? c1 : a1;
            for (int x = x0 >> 4; x <= x1 >> 4; x++) for (int z = z0 >> 4; z <= z1 >> 4; z++) keys.add(ChunkPos.asLong(x, z));
        }
        for (int x = 96 >> 4; x <= 380 >> 4; x++) for (int z = 196 >> 4; z <= 460 >> 4; z++) keys.add(ChunkPos.asLong(x, z));   // 원래 하수로·은신처
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
    @Override
    public List<Feature> featuresIn(ChunkPos cp) {
        return cp.toLong() == den.anchorChunk() ? List.of(den) : Collections.emptyList();
    }
    @Override public int[] lightRange(int x, int z) { return null; }
    @Override public boolean waterPassAll() { return true; }
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
        // 두 패스 모두 돎: 첫 패스(SOLID)는 물을 건너뛰고, 둘째 패스(WATER)에서 물만 놓임
        prisonRework(p, x, z);
        falls(p, x, z);
        if (x >= DX0 - 1 && x <= 127 && z >= DZ0 - 1 && z <= 451) hideout(p, x, z);
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
        p.set(x, F, z, al <= 1 ? (Math.floorMod(along, 8) == 0 ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState()) : brick(x, F, z));
        for (int y = F + 1; y <= top; y++) p.set(x, y, z, Blocks.AIR.defaultBlockState());
        boolean fall = lat == 0 && frac < 0.85 && Math.floorMod(along, 16) == 0;            // 천장 배수구에서 떨어지는 물
        p.set(x, top + 1, z, fall ? Blocks.WATER.defaultBlockState() : brick(x, top + 1, z));
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

    // ───────────────────────── 원래 하수로의 물 ─────────────────────────

    /**
     * 원래 하수로 물길을 흐르는 물로: 8칸마다 물 근원(그 위 16칸마다 천장 배수구에서 물이 쏟아짐), 그 사이는 비워 두어
     * 물이 근원에서 양쪽으로 흘러 퍼지게 함 (물길 홈이 1칸이라 보행로로 넘치지 않음).
     */
    private void falls(Placer p, int x, int z) {
        for (int[] c : CHANNELS) {
            boolean alongX = c[0] == 1;
            int along = alongX ? x : z, lat = alongX ? z - c[1] : x - c[1];
            if (lat != 0 || along < c[2] || along > c[3]) continue;
            if (!p.get(x, F - 1, z).isSolid()) continue;
            boolean source = Math.floorMod(along, 8) == 0;
            p.set(x, F, z, source ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState());
            boolean fall = Math.floorMod(along, 16) == 0 && p.get(x, F + 5, z).isAir();
            if (fall) p.set(x, F + 6, z, Blocks.WATER.defaultBlockState());
            else if (p.get(x, F + 6, z).is(Blocks.WATER)) p.set(x, F + 6, z, brick(x, F + 6, z));    // 예전 배수구 자리
        }
    }

    // ───────────────────────── 왕궁·지하감옥·하수도 연결 ─────────────────────────

    /**
     * - 순환 하수로 서쪽 벽에서 왕궁 사다리로 가던 통로를 메움
     * - 왕궁 비밀 사다리(224, 275) 아래에서 북쪽으로 지하감옥 남쪽 감방까지 새 통로
     * - 하수로 북쪽과 지하감옥을 대놓고 잇던 굴을 막고, 감방 벽의 덩굴 뒤 좁은 틈으로만 몰래 이어지게
     */
    private void prisonRework(Placer p, int x, int z) {
        // 옛 왕궁 통로 메우기 (하수로 벽 복구)
        if (x >= 203 && x <= 223 && z >= 274 && z <= 276) {
            for (int y = F - 1; y <= F + 7; y++) p.set(x, y, z, x <= 204 ? brick(x, y, z) : Blocks.STONE.defaultBlockState());
            if (x == 223 && z == 276) p.set(x, F + 3, z, Blocks.STONE.defaultBlockState());
        }
        // 왕궁 사다리 → 지하감옥 통로 (x 221~223, z 233~276)
        if (x >= 220 && x <= 224 && z >= 232 && z <= 277) {
            if (x == 224 && z >= 274) return;                                                    // 사다리와 그 받침은 그대로
            boolean inside = x >= 221 && x <= 223 && z >= 233 && z <= 276;
            boolean door = x >= 221 && x <= 223 && z == 232;
            for (int y = F - 1; y <= F + 5; y++) {
                BlockState s;
                if (inside || door) s = y == F - 1 || y == F + 5 ? brick(x, y, z) : y == F ? brick(x, y, z) : y <= F + 3 ? Blocks.AIR.defaultBlockState() : brick(x, y, z);
                else s = brick(x, y, z);
                if (door && y == F + 4) s = brick(x, y, z);
                p.set(x, y, z, s);
            }
            if (inside && x == 222 && Math.floorMod(z, 9) == 0) p.set(x, F + 3, z, Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
            if (x == 223 && z == 275) p.set(x, F + 1, z, Blocks.AIR.defaultBlockState());      // 사다리 발치에서 옆으로 나옴
            return;
        }
        // 하수로 ↔ 지하감옥 굴 막기 (x 227~235, z 208~210), 덩굴 장막 뒤 좁은 틈 (x 231)
        if (x >= 227 && x <= 235 && z >= 208 && z <= 210) {
            for (int y = F - 1; y <= F + 7; y++) p.set(x, y, z, brick(x, y, z));
            if (x == 231) {
                for (int y = F + 1; y <= F + 2; y++) {
                    p.set(x, y, z, z == 209 ? Blocks.AIR.defaultBlockState()
                            : Blocks.VINE.defaultBlockState().setValue(net.minecraft.world.level.block.VineBlock.WEST, true));
                }
            }
        }
    }

    // ───────────────────────── 뒷골목 은신처 ─────────────────────────

    /**
     * 슬럼가 맨홀 하수로 서쪽 땅속의 암시장 은신처. 두 갈래 비밀 입구:
     *  - 하수로 서쪽 벽의 덩굴 장막 뒤 (z 431~432)
     *  - 슬럼가 맨홀 사다리 중간(지하 8칸)의 덩굴 장막 → 좁은 굴 → 은신처 천장의 사다리
     */
    private void hideout(Placer p, int x, int z) {
        int k = h(x, F + 1, z);
        boolean inX = x >= DX0 && x <= DX1, inZ = z >= DZ0 && z <= DZ1;
        // 방 껍질
        if (x >= DX0 - 1 && x <= DX1 + 1 && z >= DZ0 - 1 && z <= DZ1 + 1) {
            boolean wall = !inX || !inZ;
            boolean doorA = x == DX1 + 1 && (z == 431 || z == 432);
            p.set(x, F, z, wall ? brick(x, F, z) : k % 5 == 0 ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.SPRUCE_PLANKS.defaultBlockState());
            for (int y = F + 1; y <= F + 6; y++) p.set(x, y, z, wall && !(doorA && y <= F + 3) ? brick(x, y, z) : Blocks.AIR.defaultBlockState());
            boolean ladderHole = x == 114 && z == 439;
            p.set(x, F + 7, z, ladderHole ? Blocks.AIR.defaultBlockState() : wall ? brick(x, F + 7, z) : Blocks.SPRUCE_PLANKS.defaultBlockState());
            if (ladderHole) for (int y = F + 1; y <= F + 7; y++) p.set(x, y, z, Blocks.LADDER.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.LadderBlock.FACING, net.minecraft.core.Direction.SOUTH));
            if (z == DZ1 + 1 && (x == 113 || x == 114)) {                                    // 입구 B 굴 끝이 사다리 위로
                for (int y = F + 8; y <= F + 9; y++) p.set(x, y, z, Blocks.AIR.defaultBlockState());
                p.set(x, F + 10, z, brick(x, F + 10, z));
            }
            if (!wall) denFurniture(p, x, z, k);
            return;
        }
        // 입구 A: 은신처 동쪽 벽 → 하수로 서쪽 벽 (덩굴 장막)
        if ((z == 431 || z == 432) && x >= DX1 + 2 && x <= 122) {
            p.set(x, F, z, brick(x, F, z));
            for (int y = F + 1; y <= F + 3; y++) {
                if (x == 122) p.set(x, y, z, Blocks.VINE.defaultBlockState().setValue(z == 431 ? net.minecraft.world.level.block.VineBlock.NORTH : net.minecraft.world.level.block.VineBlock.SOUTH, true));
                else p.set(x, y, z, Blocks.AIR.defaultBlockState());
            }
            p.set(x, F + 4, z, brick(x, F + 4, z));
            return;
        }
        if ((z == 430 || z == 433) && x >= DX1 + 2 && x <= 121) {
            for (int y = F; y <= F + 4; y++) p.set(x, y, z, brick(x, y, z));
            return;
        }
        // 입구 B: 슬럼가 맨홀 사다리 중간 → 서쪽 굴 (z 448~449, 바닥 F+7) → 남쪽으로 꺾여 은신처 사다리 위 (x 114)
        boolean westRun = (z == 448 || z == 449) && x >= 113 && x <= 126;
        boolean southRun = (x == 113 || x == 114) && z >= 440 && z <= 449;
        if (westRun || southRun) {
            p.set(x, F + 7, z, x == 114 && z == 440 ? Blocks.AIR.defaultBlockState() : brick(x, F + 7, z));
            for (int y = F + 8; y <= F + 9; y++) {
                if (x == 126) p.set(x, y, z, Blocks.VINE.defaultBlockState().setValue(z == 448 ? net.minecraft.world.level.block.VineBlock.NORTH : net.minecraft.world.level.block.VineBlock.SOUTH, true));
                else p.set(x, y, z, Blocks.AIR.defaultBlockState());
            }
            p.set(x, F + 10, z, brick(x, F + 10, z));
            if (x == 113 && z == 446) p.set(x, F + 9, z, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
            return;
        }
        boolean wallB = (z == 447 || z == 450) && x >= 112 && x <= 126 || (x == 112 || x == 115) && z >= 440 && z <= 450;
        if (wallB && !(x == 114 && z == 447) && !(x == 113 && z == 447)) {
            for (int y = F + 7; y <= F + 10; y++) p.set(x, y, z, brick(x, y, z));
        }
    }

    private void denFurniture(Placer p, int x, int z, int k) {
        int y = F + 1;
        // 북쪽: 이층 침상, 남쪽: 노름판, 서쪽: 장물 창고, 동쪽 입구 옆: 암시장 진열대
        if (z == DZ1 && x >= 104 && x <= 110 && x % 3 == 2) {
            p.set(x, y, z - 1, net.minecraft.world.level.block.Blocks.BROWN_BED.defaultBlockState().setValue(net.minecraft.world.level.block.BedBlock.FACING, net.minecraft.core.Direction.SOUTH)
                    .setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.FOOT));
            p.set(x, y, z, net.minecraft.world.level.block.Blocks.BROWN_BED.defaultBlockState().setValue(net.minecraft.world.level.block.BedBlock.FACING, net.minecraft.core.Direction.SOUTH)
                    .setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD));
        }
        if (x >= 108 && x <= 111 && z >= 427 && z <= 429) {                                     // 노름판
            p.set(x, y, z, Blocks.DARK_OAK_PLANKS.defaultBlockState());
            if ((x + z) % 3 == 0) p.set(x, y + 1, z, Blocks.CANDLE.defaultBlockState().setValue(net.minecraft.world.level.block.CandleBlock.LIT, true));
            else if ((x + z) % 3 == 1) p.set(x, y + 1, z, Blocks.RED_CARPET.defaultBlockState());
        }
        if (x == DX0 && z >= 427 && z <= 437) {                                                  // 장물 창고
            if (z % 3 == 0) OldCapital.chest(p, x, y, z, net.minecraft.core.Direction.EAST, seed ^ z, net.minecraft.world.level.storage.loot.BuiltInLootTables.SIMPLE_DUNGEON);
            else {
                p.set(x, y, z, Blocks.BARREL.defaultBlockState());
                p.set(x, y + 1, z, k % 2 == 0 ? Blocks.BARREL.defaultBlockState() : Blocks.AIR.defaultBlockState());
            }
        }
        if (x == 116 && z >= 426 && z <= 429) {                                                 // 진열대
            p.set(x, y, z, Blocks.SPRUCE_SLAB.defaultBlockState().setValue(net.minecraft.world.level.block.SlabBlock.TYPE, net.minecraft.world.level.block.state.properties.SlabType.TOP));
            if (z == 427) OldCapital.chest(p, x, y + 1, z, net.minecraft.core.Direction.WEST, seed ^ 0xB1AC, net.minecraft.world.level.storage.loot.BuiltInLootTables.PILLAGER_OUTPOST);
        }
        if (x == 105 && z == 433) p.set(x, y, z, Blocks.LECTERN.defaultBlockState());          // 의뢰 장부
        if (x == 110 && z == 432) p.set(x, y, z, Blocks.CARTOGRAPHY_TABLE.defaultBlockState());
        if ((x == 104 || x == 116) && (z == 428 || z == 436)) p.set(x, F + 6, z, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        if (x == 110 && z == 434) p.set(x, F + 6, z, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        if (k % 13 == 0 && z > 429 && z < 437 && x > 103 && x < 115) p.set(x, y, z, Blocks.RED_CARPET.defaultBlockState());
    }

    /** 은신처 주민: 암시장 상인·정보상·그림자 길드 중개인 */
    final class Den extends Feature {
        Den() {
            super(DX0, DZ0, DX1, DZ1);
            groundY = F;
        }

        @Override public void build(Placer p, CityLayout city) {}

        @Override public boolean needsSpawn() { return true; }

        @Override
        public void spawn(net.minecraft.server.level.ServerLevel level, CityLayout city) {
            String[] names = {"정보상 쥐눈", "위조 장인", "그림자 길드 중개인"};
            for (int i = 0; i < names.length; i++) {
                var m = net.minecraft.world.entity.EntityType.VILLAGER.spawn(level, new net.minecraft.core.BlockPos(106 + i * 3, F + 1, 431), net.minecraft.world.entity.MobSpawnType.STRUCTURE);
                if (m != null) {
                    m.setPersistenceRequired();
                    m.setCustomName(net.minecraft.network.chat.Component.literal(names[i]));
                }
            }
            var t = net.minecraft.world.entity.EntityType.WANDERING_TRADER.spawn(level, new net.minecraft.core.BlockPos(114, F + 1, 428), net.minecraft.world.entity.MobSpawnType.STRUCTURE);
            if (t != null) {
                t.setPersistenceRequired();
                t.setDespawnDelay(0);
                t.setCustomName(net.minecraft.network.chat.Component.literal("암시장 상인"));
            }
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
