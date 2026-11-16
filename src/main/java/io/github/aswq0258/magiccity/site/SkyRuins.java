package io.github.aswq0258.magiccity.site;

import io.github.aswq0258.magiccity.build.BuildLayout;
import io.github.aswq0258.magiccity.build.Placer;
import io.github.aswq0258.magiccity.city.CityLayout;
import io.github.aswq0258.magiccity.city.Shapes;
import io.github.aswq0258.magiccity.city.feature.Feature;
import io.github.aswq0258.magiccity.world.KingdomTerrain;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 부유섬 유적 "천공의 유적" (마스터플랜 A1) — 지류가 시작되는 샘 위 하늘에 떠 있는 고대 섬들
 *
 *  - 큰 섬(반지름 34, Y 182): 무너진 신전 터, 가운데 샘에서 흐른 물이 남쪽 끝에서 폭포가 되어 땅의 샘 연못으로 떨어짐 (지류의 발원)
 *  - 작은 섬 6개: 수정 첨탑, 무너진 서고, 공중 정원, 별 관측소, 폭풍의 결투장(보스), 가장 높은 보물고
 *  - 섬 사이 사슬 다리 (몇 군데는 끊겨서 뛰어 건너야 함), 보물고는 떠 있는 디딤돌로만 갈 수 있음
 *  - 땅: 섬을 묶은 사슬 오벨리스크 4개, 섬까지 오르는 "천공의 탑"(나선 계단), 탑 앞 학자 야영지(느린 낙하 물약)
 *  - 몬스터: 브리즈 스포너, 보스 "폭풍의 수호자"(boss.SkyBoss)
 */
public final class SkyRuins implements BuildLayout {
    public static final int CX = KingdomTerrain.worldX(1200), CZ = KingdomTerrain.worldZ(-820);
    /** 섬 {x, z, 반지름, 윗면 Y} */
    public static final int[][] ISLES = {
            {CX, CZ - 40, 34, 182},          // 0 큰 섬
            {CX - 55, CZ - 90, 14, 198},     // 1 수정 첨탑
            {CX + 60, CZ - 85, 15, 192},     // 2 무너진 서고
            {CX + 70, CZ - 15, 13, 172},     // 3 공중 정원
            {CX - 65, CZ - 15, 12, 206},     // 4 별 관측소
            {CX, CZ - 128, 20, 214},         // 5 폭풍의 결투장
            {CX, CZ - 180, 9, 236},          // 6 보물고
    };
    /** 다리 {섬 a, 섬 b, 끊김 여부} */
    private static final int[][] BRIDGES = {{0, 1, 0}, {0, 2, 1}, {0, 3, 0}, {0, 4, 1}, {1, 5, 0}, {2, 5, 0}, {4, 1, 0}};
    public static final int TOWER_X = CX + 41, TOWER_Z = CZ - 2, TOWER_R = 6;
    public static final int POOL_X = CX, POOL_Z = CZ - 3;
    public static final int ARENA_X = ISLES[5][0], ARENA_Z = ISLES[5][1], ARENA_Y = ISLES[5][3];

    private final long seed;
    private final List<Feature> features = new ArrayList<>();
    private final Map<Long, List<Feature>> byChunk = new HashMap<>();
    private final List<ChunkPos> chunks = new ArrayList<>();
    private final int towerBase;

    public SkyRuins(long seed) {
        this.seed = seed;
        this.towerBase = KingdomTerrain.column(TOWER_X, TOWER_Z).h();
        features.add(new Temple());
        features.add(new CrystalSpire());
        features.add(new Library());
        features.add(new Garden());
        features.add(new Observatory());
        features.add(new Arena());
        features.add(new Vault());
        features.add(new Tower());
        features.add(new Camp());
        for (int[] b : BRIDGES) features.add(new Bridge(ISLES[b[0]], ISLES[b[1]], b[2] == 1));    // 다리는 섬 유적 다음에 (난간 틈을 냄)
        features.add(new Stepping(ISLES[5], ISLES[6]));
        for (Feature f : features) {
            for (int x = (f.minX - 1) >> 4; x <= (f.maxX + 1) >> 4; x++)
                for (int z = (f.minZ - 1) >> 4; z <= (f.maxZ + 1) >> 4; z++)
                    byChunk.computeIfAbsent(ChunkPos.asLong(x, z), k -> new ArrayList<>()).add(f);
        }
        LinkedHashSet<Long> keys = new LinkedHashSet<>();
        for (int[] s : ISLES) for (int x = (s[0] - s[2] - 6) >> 4; x <= (s[0] + s[2] + 6) >> 4; x++)
            for (int z = (s[1] - s[2] - 6) >> 4; z <= (s[1] + s[2] + 6) >> 4; z++) keys.add(ChunkPos.asLong(x, z));
        for (int x = (CX - 50) >> 4; x <= (CX + 60) >> 4; x++) for (int z = (CZ - 80) >> 4; z <= (CZ + 30) >> 4; z++) keys.add(ChunkPos.asLong(x, z));
        keys.addAll(byChunk.keySet());
        for (long k : keys) chunks.add(new ChunkPos(k));
    }

    public static boolean near(double x, double z) {
        return x > CX - 110 && x < CX + 110 && z > CZ - 210 && z < CZ + 40;
    }

    public int featureCount() {
        return features.size();
    }

    @Override public int cx() { return CX; }
    @Override public int cz() { return CZ; }
    @Override public int base() { return 90; }
    @Override public int radius() { return 0; }
    @Override public CityLayout city() { return null; }
    @Override public List<ChunkPos> chunkList() { return chunks; }
    @Override public List<Feature> featuresIn(ChunkPos cp) { return byChunk.getOrDefault(cp.toLong(), Collections.emptyList()); }
    @Override public int[] lightRange(int x, int z) { return null; }
    @Override public int shapeMinY() { return 50; }
    @Override public int shapeMaxY() { return 260; }

    private int h(int x, int y, int z) {
        return Shapes.hash(x, y, z, seed);
    }

    private static double wob(int x, int z, int salt) {
        return Math.sin(x * 0.21 + salt) * Math.cos(z * 0.17 - salt * 0.7) * 0.5 + Math.sin((x - z) * 0.07 + salt * 1.3) * 0.5;
    }

    /** 이 기둥에서 섬의 윗면 높이 (섬 밖이면 MIN) */
    public static int isleTop(int[] s, int x, int z) {
        double d = Math.hypot(x - s[0], z - s[1]);
        double r = s[2] * (1 + 0.12 * wob(x, z, s[2]));
        if (d > r) return Integer.MIN_VALUE;
        return s[3] + (int) Math.round(1.5 * wob(x / 2, z / 2, s[3]) * (d / r));
    }

    // ───────────────────────── 기둥: 섬 몸체, 땅의 샘 연못, 사슬 오벨리스크 ─────────────────────────

    @Override
    public void buildColumn(Placer p, int x, int z) {
        for (int i = 0; i < ISLES.length; i++) isle(p, x, z, ISLES[i], i);
        pool(p, x, z);
        anchors(p, x, z);
    }

    private void isle(Placer p, int x, int z, int[] s, int idx) {
        int top = isleTop(s, x, z);
        if (top == Integer.MIN_VALUE) return;
        double d = Math.hypot(x - s[0], z - s[1]);
        double r = s[2] * (1 + 0.12 * wob(x, z, s[2]));
        double t = 1 - d / r;
        int depth = 3 + (int) Math.round(s[2] * 1.3 * Math.pow(t, 1.4) + 2 * wob(x, z, idx + 5));
        int bottom = top - Math.max(2, depth);
        int k = h(x, top, z);
        for (int y = bottom; y <= top; y++) {
            BlockState b;
            if (y == top) b = k % 7 == 0 ? Blocks.MOSS_BLOCK.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState();
            else if (y > top - 3) b = Blocks.DIRT.defaultBlockState();
            else {
                int m = h(x, y, z) % 23;
                b = m == 0 ? Blocks.AMETHYST_BLOCK.defaultBlockState() : m < 4 ? Blocks.CALCITE.defaultBlockState()
                        : m < 9 ? Blocks.ANDESITE.defaultBlockState() : m < 11 ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : Blocks.STONE.defaultBlockState();
            }
            p.set(x, y, z, b);
        }
        for (int y = top + 1; y <= top + 8; y++) p.set(x, y, z, Blocks.AIR.defaultBlockState());
        // 아래로 늘어진 뿌리·발광 열매 덩굴
        if (k % 9 == 0) {
            int len = 2 + k % 5;
            for (int y = bottom - 1; y >= bottom - len; y--) {
                boolean last = y == bottom - len;
                p.set(x, y, z, k % 2 == 0 ? Blocks.HANGING_ROOTS.defaultBlockState()
                        : (last ? Blocks.CAVE_VINES : Blocks.CAVE_VINES_PLANT).defaultBlockState().setValue(CaveVines.BERRIES, (y & 1) == 0));
                if (k % 2 == 0) break;
            }
        }
        // 윗면 풀·꽃
        if (k % 11 == 0) p.set(x, top + 1, z, Blocks.SHORT_GRASS.defaultBlockState());
        else if (k % 37 == 0) p.set(x, top + 1, z, Blocks.ALLIUM.defaultBlockState());
        else if (k % 41 == 0) p.set(x, top + 1, z, Blocks.AZURE_BLUET.defaultBlockState());
    }

    /** 폭포가 떨어지는 땅의 샘 연못 (지류의 시작) */
    private void pool(Placer p, int x, int z) {
        double d = Math.hypot(x - POOL_X, z - POOL_Z);
        if (d > 9) return;
        int g = KingdomTerrain.column(x, z).h();
        if (d <= 6.5) {
            p.clearAbove(x, z, KingdomTerrain.SEA);
            p.set(x, KingdomTerrain.SEA - 5, z, Blocks.GRAVEL.defaultBlockState());
            for (int y = KingdomTerrain.SEA - 4; y <= KingdomTerrain.SEA - 1; y++) p.set(x, y, z, Blocks.WATER.defaultBlockState());
        } else if (g > KingdomTerrain.SEA) {
            for (int y = KingdomTerrain.SEA - 5; y <= Math.min(g, KingdomTerrain.SEA); y++) p.set(x, y, z, Blocks.MOSSY_STONE_BRICKS.defaultBlockState());
            p.clearAbove(x, z, KingdomTerrain.SEA + 1);
            if (h(x, 3, z) % 3 == 0) p.set(x, KingdomTerrain.SEA + 1, z, Blocks.MOSSY_STONE_BRICK_WALL.defaultBlockState());
        }
    }

    /** 섬을 땅에 묶어 둔 사슬 오벨리스크 4개 */
    private static final int[][] ANCHORS = {{-24, -22}, {24, -24}, {-20, 18}, {18, 22}};

    private void anchors(Placer p, int x, int z) {
        int[] s = ISLES[0];
        for (int[] a : ANCHORS) {
            int ax = s[0] + a[0], az = s[1] + a[1];
            if (Math.abs(x - ax) > 2 || Math.abs(z - az) > 2) continue;
            int g = KingdomTerrain.column(ax, az).h();
            boolean core = x == ax && z == az, edge = Math.abs(x - ax) == 2 || Math.abs(z - az) == 2;
            int hgt = edge ? 3 : 14;
            if (edge && (Math.abs(x - ax) == 2 && Math.abs(z - az) == 2)) hgt = 5;
            for (int y = g; y <= g + hgt; y++) p.set(x, y, z, (y - g) % 5 == 4 ? Blocks.CHISELED_STONE_BRICKS.defaultBlockState() : Blocks.MOSSY_STONE_BRICKS.defaultBlockState());
            if (core) {
                p.set(x, g + 15, z, Blocks.AMETHYST_BLOCK.defaultBlockState());
                int bottom = s[3] - 3 - (int) Math.round(s[2] * 1.3 * Math.pow(1 - Math.hypot(a[0], a[1]) / s[2], 1.4));
                for (int y = g + 16; y < bottom; y++) p.set(x, y, z, Blocks.CHAIN.defaultBlockState());
            }
        }
    }

    // ───────────────────────── 공용 ─────────────────────────

    private static BlockState slabTop(net.minecraft.world.level.block.Block b) {
        return b.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP);
    }

    private static int topAt(int[] s, int x, int z) {
        int t = isleTop(s, x, z);
        return t == Integer.MIN_VALUE ? s[3] : t;
    }

    /** 무너진 원형 기둥 고리 */
    private void ring(Placer p, int cx, int cz, int y, double r, int hMax, BlockState mat) {
        p.forColumns(cx - (int) r - 1, cz - (int) r - 1, cx + (int) r + 1, cz + (int) r + 1, (x, z) -> {
            double d = Math.hypot(x - cx, z - cz);
            if (Math.abs(d - r) > 0.6) return;
            int a = (int) Math.round(Math.toDegrees(Math.atan2(z - cz, x - cx)));
            if (Math.floorMod(a, 30) > 6) return;
            int hh = 2 + h(x, 1, z) % hMax;
            for (int k = 1; k <= hh; k++) p.set(x, y + k, z, mat);
        });
    }

    // ───────────────────────── 다리 ─────────────────────────

    /** 사슬 다리: 폭 3 판자, 양옆 사슬 난간, 가운데가 처짐. broken 이면 가운데 4칸이 끊김 */
    final class Bridge extends Feature {
        final int[] a, b;
        final boolean broken;
        final double ax, az, bx, bz;
        final int ay, by;

        Bridge(int[] a, int[] b, boolean broken) {
            super(Math.min(a[0], b[0]) - 4, Math.min(a[1], b[1]) - 4, Math.max(a[0], b[0]) + 4, Math.max(a[1], b[1]) + 4);
            this.a = a;
            this.b = b;
            this.broken = broken;
            double dx = b[0] - a[0], dz = b[1] - a[1], len = Math.hypot(dx, dz);
            double ux = dx / len, uz = dz / len;
            ax = a[0] + ux * (a[2] - 3);
            az = a[1] + uz * (a[2] - 3);
            bx = b[0] - ux * (b[2] - 3);
            bz = b[1] - uz * (b[2] - 3);
            ay = topAt(a, (int) Math.round(ax), (int) Math.round(az));
            by = topAt(b, (int) Math.round(bx), (int) Math.round(bz));
        }

        @Override
        public void build(Placer p, CityLayout city) {
            double dx = bx - ax, dz = bz - az, len = Math.hypot(dx, dz);
            double ux = dx / len, uz = dz / len, px = -uz, pz = ux;
            Direction.Axis axis = Math.abs(dx) > Math.abs(dz) ? Direction.Axis.X : Direction.Axis.Z;
            for (double t = 0; t <= len; t += 0.4) {
                double f = t / len;
                if (broken && Math.abs(f - 0.5) * len < 2.2) continue;
                int y = (int) Math.round(ay + (by - ay) * f - 3 * Math.sin(Math.PI * f));
                for (int w = -1; w <= 1; w++) {
                    int x = (int) Math.round(ax + ux * t + px * w), z = (int) Math.round(az + uz * t + pz * w);
                    p.set(x, y, z, w == 0 ? Blocks.SPRUCE_PLANKS.defaultBlockState() : slabTop(Blocks.SPRUCE_SLAB));
                    p.set(x, y + 1, z, Blocks.AIR.defaultBlockState());
                    p.set(x, y + 2, z, Blocks.AIR.defaultBlockState());
                }
                for (int side = -2; side <= 2; side += 4) {
                    int x = (int) Math.round(ax + ux * t + px * side), z = (int) Math.round(az + uz * t + pz * side);
                    p.set(x, y + 1, z, Blocks.CHAIN.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis));
                }
            }
            // 양 끝 기둥과 등불
            for (double[] e : new double[][]{{ax, az, ay}, {bx, bz, by}}) {
                for (int side = -2; side <= 2; side += 4) {
                    int x = (int) Math.round(e[0] + px * side), z = (int) Math.round(e[1] + pz * side);
                    for (int y = 1; y <= 3; y++) p.set(x, (int) e[2] + y, z, Blocks.MOSSY_STONE_BRICK_WALL.defaultBlockState());
                    p.set(x, (int) e[2] + 4, z, Blocks.LANTERN.defaultBlockState());
                }
            }
        }
    }

    /** 결투장 → 보물고: 떠 있는 디딤돌 (1~2칸 간격, 조금씩 높아짐) */
    final class Stepping extends Feature {
        final int[] a, b;

        Stepping(int[] a, int[] b) {
            super(Math.min(a[0], b[0]) - 4, Math.min(a[1], b[1]) - 4, Math.max(a[0], b[0]) + 4, Math.max(a[1], b[1]) + 4);
            this.a = a;
            this.b = b;
        }

        @Override
        public void build(Placer p, CityLayout city) {
            double dx = b[0] - a[0], dz = b[1] - a[1], len = Math.hypot(dx, dz);
            double ux = dx / len, uz = dz / len;
            double t0 = a[2] - 1, t1 = len - b[2] + 1;
            int n = (int) ((t1 - t0) / 3.2);
            for (int i = 1; i <= n; i++) {
                double t = t0 + (t1 - t0) * i / (n + 1);
                int y = (int) Math.round(a[3] + (b[3] - a[3]) * (double) i / (n + 1));
                int x = (int) Math.round(a[0] + ux * t + 1.5 * Math.sin(i * 1.3)), z = (int) Math.round(a[1] + uz * t + 1.5 * Math.cos(i * 1.1));
                for (int ox = 0; ox <= 1; ox++) for (int oz = 0; oz <= 1; oz++) {
                    p.set(x + ox, y, z + oz, i % 3 == 0 ? Blocks.AMETHYST_BLOCK.defaultBlockState() : Blocks.MOSSY_STONE_BRICKS.defaultBlockState());
                    p.set(x + ox, y - 1, z + oz, Blocks.CALCITE.defaultBlockState());
                }
            }
        }
    }

    // ───────────────────────── 섬 위 유적 ─────────────────────────

    /** 큰 섬: 무너진 신전(열주와 반쯤 남은 지붕), 가운데 샘과 남쪽 폭포로 흐르는 물길 */
    final class Temple extends Feature {
        final int[] s = ISLES[0];

        Temple() {
            super(ISLES[0][0] - 36, ISLES[0][1] - 36, ISLES[0][0] + 36, ISLES[0][1] + 36);
        }

        @Override
        public void build(Placer p, CityLayout city) {
            int cx = s[0], cz = s[1], y = s[3];
            p.forColumns(cx - 13, cz - 13, cx + 13, cz + 13, (x, z) -> {
                int dx = x - cx, dz = z - cz, m = Math.max(Math.abs(dx), Math.abs(dz));
                if (m > 12) return;
                p.set(x, y, z, ((dx + dz) & 1) == 0 ? Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState() : Blocks.QUARTZ_BRICKS.defaultBlockState());
                if (h(x, 4, z) % 5 == 0) p.set(x, y, z, Blocks.MOSSY_STONE_BRICKS.defaultBlockState());
                for (int k = 1; k <= 10; k++) p.set(x, y + k, z, Blocks.AIR.defaultBlockState());
                if (m == 11 && Math.floorMod(dx, 4) == 0 && Math.floorMod(dz, 4) == 0) {           // 열주 (일부 부러짐)
                    int hh = h(x, 5, z) % 3 == 0 ? 3 + h(x, 6, z) % 4 : 9;
                    for (int k = 1; k <= hh; k++) p.set(x, y + k, z, Blocks.QUARTZ_PILLAR.defaultBlockState());
                    if (hh == 9) p.set(x, y + 10, z, Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState());
                }
                if (m <= 11 && dz < -2 && h(x, 7, z) % 4 != 0) p.set(x, y + 10, z, Blocks.SMOOTH_QUARTZ.defaultBlockState());   // 반쯤 남은 지붕
                if (m == 12 && h(x, 8, z) % 3 == 0) p.set(x, y + 1, z, Blocks.QUARTZ_SLAB.defaultBlockState());
            });
            // 가운데 샘 + 남쪽 물길 → 섬 끝에서 폭포
            p.forColumns(cx - 3, cz - 3, cx + 3, cz + 3, (x, z) -> {
                if (Math.hypot(x - cx, z - cz) > 3.2) return;
                p.set(x, y - 1, z, Blocks.PRISMARINE_BRICKS.defaultBlockState());
                p.set(x, y, z, Blocks.WATER.defaultBlockState());
            });
            p.set(cx, y - 1, cz, Blocks.SEA_LANTERN.defaultBlockState());
            for (int z = cz + 4; z <= cz + s[2] + 6; z++) {
                int top = isleTop(s, cx, z);
                for (int x = cx - 1; x <= cx + 1; x++) {
                    if (top == Integer.MIN_VALUE) continue;
                    p.set(x, y - 1, z, Blocks.PRISMARINE_BRICKS.defaultBlockState());
                    p.set(x, y, z, x == cx ? Blocks.WATER.defaultBlockState() : Blocks.MOSSY_STONE_BRICKS.defaultBlockState());
                    for (int k = 1; k <= 3; k++) p.set(x, y + k, z, Blocks.AIR.defaultBlockState());
                }
            }
            // 제단과 학자의 기록
            p.set(cx, y + 1, cz - 8, Blocks.LECTERN.defaultBlockState());
            p.set(cx - 2, y + 1, cz - 8, Blocks.END_ROD.defaultBlockState());
            p.set(cx + 2, y + 1, cz - 8, Blocks.END_ROD.defaultBlockState());
            OldCapital.chest(p, cx, y + 1, cz - 10, Direction.SOUTH, seed ^ 0x51, BuiltInLootTables.ANCIENT_CITY);
            ring(p, cx, cz, y, 24, 6, Blocks.MOSSY_STONE_BRICKS.defaultBlockState());
        }
    }

    /** 수정 첨탑: 자수정·방해석 탑과 꼭대기 떠 있는 수정 */
    final class CrystalSpire extends Feature {
        final int[] s = ISLES[1];

        CrystalSpire() {
            super(ISLES[1][0] - 8, ISLES[1][1] - 8, ISLES[1][0] + 8, ISLES[1][1] + 8);
        }

        @Override
        public void build(Placer p, CityLayout city) {
            int cx = s[0], cz = s[1], y = s[3];
            p.forColumns(cx - 4, cz - 4, cx + 4, cz + 4, (x, z) -> {
                double d = Math.hypot(x - cx, z - cz);
                for (int k = 0; k <= 26; k++) {
                    double rr = 4 - k * 0.14;
                    if (d > rr) continue;
                    p.set(x, y + k, z, d > rr - 1 ? ((k / 4) % 2 == 0 ? Blocks.CALCITE.defaultBlockState() : Blocks.AMETHYST_BLOCK.defaultBlockState())
                            : (k == 0 ? Blocks.CALCITE.defaultBlockState() : Blocks.AIR.defaultBlockState()));
                }
            });
            Shapes.crystal(p, cx, y + 33, cz, 3);
            for (Direction d : Direction.Plane.HORIZONTAL) p.set(cx + d.getStepX() * 4, y + 1, cz + d.getStepZ() * 4, Blocks.AIR.defaultBlockState());
            for (Direction d : Direction.Plane.HORIZONTAL) p.set(cx + d.getStepX() * 4, y + 2, cz + d.getStepZ() * 4, Blocks.AIR.defaultBlockState());
            p.set(cx, y + 1, cz, Blocks.ENCHANTING_TABLE.defaultBlockState());
            OldCapital.spawner(p, cx + 2, y + 1, cz, EntityType.BREEZE);
            for (int k = 0; k < 8; k++) p.set(cx + (k % 3) - 1, y + 1, cz - 6 - k % 2, Blocks.AMETHYST_CLUSTER.defaultBlockState());
        }
    }

    /** 무너진 서고: 반쯤 남은 벽과 책장, 흩어진 책, 브리즈 스포너 */
    final class Library extends Feature {
        final int[] s = ISLES[2];

        Library() {
            super(ISLES[2][0] - 9, ISLES[2][1] - 9, ISLES[2][0] + 9, ISLES[2][1] + 9);
        }

        @Override
        public void build(Placer p, CityLayout city) {
            int cx = s[0], cz = s[1], y = s[3];
            p.forColumns(cx - 8, cz - 8, cx + 8, cz + 8, (x, z) -> {
                int dx = x - cx, dz = z - cz, m = Math.max(Math.abs(dx), Math.abs(dz));
                if (m > 7) return;
                p.set(x, y, z, Blocks.DARK_OAK_PLANKS.defaultBlockState());
                int wallH = m == 7 ? Math.max(0, 7 - Math.abs(dx + dz) / 2 - h(x, 2, z) % 3) : 0;
                for (int k = 1; k <= wallH; k++) p.set(x, y + k, z, k % 3 == 0 ? Blocks.CHISELED_STONE_BRICKS.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState());
                if (m == 6 && h(x, 3, z) % 3 != 0) for (int k = 1; k <= 1 + h(x, 4, z) % 3; k++) p.set(x, y + k, z, Blocks.BOOKSHELF.defaultBlockState());
                if (m < 5 && h(x, 5, z) % 13 == 0) p.set(x, y + 1, z, Blocks.CHISELED_BOOKSHELF.defaultBlockState());
            });
            p.set(cx, y + 1, cz, Blocks.LECTERN.defaultBlockState());
            OldCapital.chest(p, cx - 3, y + 1, cz + 3, Direction.NORTH, seed ^ 0x52, BuiltInLootTables.STRONGHOLD_LIBRARY);
            OldCapital.spawner(p, cx + 3, y + 1, cz - 3, EntityType.BREEZE);
            p.set(cx, y + 6, cz, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false));
        }
    }

    /** 공중 정원: 꽃나무(진달래), 벌집, 작은 연못 */
    final class Garden extends Feature {
        final int[] s = ISLES[3];

        Garden() {
            super(ISLES[3][0] - 8, ISLES[3][1] - 8, ISLES[3][0] + 8, ISLES[3][1] + 8);
        }

        @Override
        public void build(Placer p, CityLayout city) {
            int cx = s[0], cz = s[1], y = s[3];
            int[][] trees = {{-4, -3}, {4, 2}, {-1, 5}};
            for (int[] t : trees) {
                int tx = cx + t[0], tz = cz + t[1], ty = topAt(s, tx, tz);
                for (int k = 1; k <= 5; k++) p.set(tx, ty + k, tz, Blocks.CHERRY_LOG.defaultBlockState());
                p.forColumns(tx - 3, tz - 3, tx + 3, tz + 3, (x, z) -> {
                    for (int k = 4; k <= 7; k++) {
                        double rr = k == 7 ? 1.6 : 3.2;
                        if (Math.hypot(x - tx, z - tz) <= rr && !(x == tx && z == tz && k <= 5))
                            p.set(x, ty + k, z, Blocks.CHERRY_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true));
                    }
                });
            }
            p.set(cx + 4, topAt(s, cx + 4, cz + 2) + 4, cz + 3, Blocks.BEE_NEST.defaultBlockState());
            p.forColumns(cx - 2, cz - 2, cx + 2, cz + 2, (x, z) -> {
                if (Math.hypot(x - cx, z - cz) <= 1.6) p.set(x, topAt(s, x, z), z, Blocks.WATER.defaultBlockState());
                else if (Math.hypot(x - cx, z - cz) <= 2.5) p.set(x, topAt(s, x, z) + 1, z, Blocks.PINK_PETALS.defaultBlockState());
            });
        }

        @Override public boolean needsSpawn() { return true; }

        @Override
        public void spawn(ServerLevel level, CityLayout city) {
            for (int i = 0; i < 3; i++) {
                Mob m = EntityType.ALLAY.spawn(level, new BlockPos(s[0] + i - 1, s[3] + 3, s[1]), MobSpawnType.STRUCTURE);
                if (m != null) {
                    m.setPersistenceRequired();
                    m.setCustomName(Component.literal("하늘 정령"));
                }
            }
        }
    }

    /** 별 관측소: 유리 돔 아래 망원경(구리 기둥), 별자리 바닥 */
    final class Observatory extends Feature {
        final int[] s = ISLES[4];

        Observatory() {
            super(ISLES[4][0] - 7, ISLES[4][1] - 7, ISLES[4][0] + 7, ISLES[4][1] + 7);
        }

        @Override
        public void build(Placer p, CityLayout city) {
            int cx = s[0], cz = s[1], y = s[3];
            p.forColumns(cx - 6, cz - 6, cx + 6, cz + 6, (x, z) -> {
                double d = Math.hypot(x - cx, z - cz);
                if (d > 6.3) return;
                p.set(x, y, z, h(x, 1, z) % 9 == 0 ? Blocks.SEA_LANTERN.defaultBlockState() : Blocks.DEEPSLATE_TILES.defaultBlockState());
                for (int k = 1; k <= 6; k++) {
                    double rr = 6 * Math.sqrt(Math.max(0, 1 - Math.pow((k - 1) / 6.0, 2)));
                    if (d <= rr && d > rr - 1.2) p.set(x, y + k, z, k <= 2 ? Blocks.POLISHED_DEEPSLATE.defaultBlockState() : Blocks.TINTED_GLASS.defaultBlockState());
                    else if (d <= rr) p.set(x, y + k, z, Blocks.AIR.defaultBlockState());
                }
            });
            for (int k = 1; k <= 2; k++) p.set(cx + 6, y + k, cz, Blocks.AIR.defaultBlockState());
            for (int k = 1; k <= 3; k++) p.set(cx, y + k, cz, Blocks.WAXED_COPPER_BLOCK.defaultBlockState());
            p.set(cx, y + 4, cz, Blocks.LIGHTNING_ROD.defaultBlockState());
            p.set(cx + 1, y + 1, cz + 1, Blocks.CARTOGRAPHY_TABLE.defaultBlockState());
            OldCapital.chest(p, cx - 2, y + 1, cz, Direction.EAST, seed ^ 0x53, BuiltInLootTables.SHIPWRECK_MAP);
        }
    }

    /** 폭풍의 결투장: 둥근 돌 바닥, 낮은 난간, 바람 문양, 가운데 보스 제단 */
    final class Arena extends Feature {
        final int[] s = ISLES[5];

        Arena() {
            super(ISLES[5][0] - 18, ISLES[5][1] - 18, ISLES[5][0] + 18, ISLES[5][1] + 18);
        }

        @Override
        public void build(Placer p, CityLayout city) {
            int cx = s[0], cz = s[1], y = s[3];
            p.forColumns(cx - 17, cz - 17, cx + 17, cz + 17, (x, z) -> {
                double d = Math.hypot(x - cx, z - cz);
                if (d > 16.5) return;
                int ring = (int) d;
                p.set(x, y, z, ring % 4 == 0 ? Blocks.CHISELED_TUFF.defaultBlockState() : ring % 2 == 0 ? Blocks.POLISHED_TUFF.defaultBlockState() : Blocks.TUFF_BRICKS.defaultBlockState());
                for (int k = 1; k <= 12; k++) p.set(x, y + k, z, Blocks.AIR.defaultBlockState());
                if (d > 15.5) {
                    p.set(x, y + 1, z, Blocks.TUFF_BRICK_WALL.defaultBlockState());
                    int a = (int) Math.round(Math.toDegrees(Math.atan2(z - cz, x - cx)));
                    if (Math.floorMod(a, 45) < 4) {
                        for (int k = 1; k <= 6; k++) p.set(x, y + k, z, Blocks.CHISELED_TUFF_BRICKS.defaultBlockState());
                        p.set(x, y + 7, z, Blocks.COPPER_BULB.defaultBlockState().setValue(net.minecraft.world.level.block.CopperBulbBlock.LIT, true));
                    }
                }
            });
            // 입구 (다리가 닿는 남쪽·남동·남서) 난간 틈
            for (int dx = -2; dx <= 2; dx++) p.set(cx + dx, y + 1, cz + 16, Blocks.AIR.defaultBlockState());
            p.set(cx, y + 1, cz, Blocks.CHISELED_TUFF.defaultBlockState());
            p.set(cx, y + 2, cz, Blocks.HEAVY_CORE.defaultBlockState());
        }
    }

    /** 가장 높은 보물고: 작은 돔 금고 */
    final class Vault extends Feature {
        final int[] s = ISLES[6];

        Vault() {
            super(ISLES[6][0] - 6, ISLES[6][1] - 6, ISLES[6][0] + 6, ISLES[6][1] + 6);
        }

        @Override
        public void build(Placer p, CityLayout city) {
            int cx = s[0], cz = s[1], y = s[3];
            p.forColumns(cx - 5, cz - 5, cx + 5, cz + 5, (x, z) -> {
                double d = Math.hypot(x - cx, z - cz);
                if (d > 5.3) return;
                p.set(x, y, z, Blocks.GOLD_BLOCK.defaultBlockState());
                for (int k = 1; k <= 5; k++) {
                    double rr = 5 * Math.sqrt(Math.max(0, 1 - Math.pow((k - 1) / 5.0, 2)));
                    if (d <= rr && d > rr - 1.2) p.set(x, y + k, z, Blocks.QUARTZ_BRICKS.defaultBlockState());
                    else if (d <= rr) p.set(x, y + k, z, Blocks.AIR.defaultBlockState());
                }
            });
            for (int k = 1; k <= 2; k++) p.set(cx, y + k, cz + 5, Blocks.AIR.defaultBlockState());
            OldCapital.chest(p, cx, y + 1, cz - 2, Direction.SOUTH, seed ^ 0x54, BuiltInLootTables.END_CITY_TREASURE);
            OldCapital.chest(p, cx + 2, y + 1, cz, Direction.WEST, seed ^ 0x55, BuiltInLootTables.TRIAL_CHAMBERS_REWARD_RARE);
            p.set(cx, y + 4, cz, Blocks.END_ROD.defaultBlockState());
        }
    }

    // ───────────────────────── 땅 ─────────────────────────

    /** 천공의 탑: 땅에서 큰 섬 높이까지 오르는 무너진 원탑 (나선 계단, 30칸마다 쉼터 창, 꼭대기에서 섬으로 다리) */
    final class Tower extends Feature {
        Tower() {
            super(TOWER_X - TOWER_R - 2, TOWER_Z - TOWER_R - 2, TOWER_X + TOWER_R + 2, TOWER_Z + TOWER_R + 2);
        }

        @Override
        public void build(Placer p, CityLayout city) {
            final int base = towerBase, topY = ISLES[0][3];
            p.forColumns(TOWER_X - TOWER_R - 1, TOWER_Z - TOWER_R - 1, TOWER_X + TOWER_R + 1, TOWER_Z + TOWER_R + 1, (x, z) -> {
                int dx = x - TOWER_X, dz = z - TOWER_Z;
                double d = Math.hypot(dx, dz);
                if (d > TOWER_R + 0.5) return;
                p.foundation(x, z, base - 1, 20);
                p.set(x, base, z, Blocks.STONE_BRICKS.defaultBlockState());
                boolean wall = d > TOWER_R - 0.7;
                for (int y = base + 1; y <= topY + 3; y++) {
                    BlockState s;
                    if (wall) {
                        int k = h(x, y, z) % 10;
                        s = k < 3 ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState() : k < 4 ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState();
                        if ((y - base) % 10 == 5 && (Math.abs(dx) <= 0 || Math.abs(dz) <= 0)) s = Blocks.AIR.defaultBlockState();   // 창
                        if (y > topY && (int) Math.round(Math.toDegrees(Math.atan2(dz, dx))) % 40 > 20) s = Blocks.AIR.defaultBlockState();   // 총안
                        if (y <= base + 3 && dz >= TOWER_R - 1 && Math.abs(dx) <= 1) s = Blocks.AIR.defaultBlockState();                     // 남쪽 문
                    } else if (d <= 1.5) {
                        s = y <= topY ? Blocks.STONE_BRICKS.defaultBlockState() : Blocks.AIR.defaultBlockState();
                    } else {
                        double ang0 = Math.atan2(dz, dx);
                        if (ang0 < 0) ang0 += Math.PI * 2;
                        double last = base + (ang0 / (Math.PI * 2) + Math.floor((topY - 1 - base) / 10.0 - ang0 / (Math.PI * 2))) * 10;
                        boolean stairwell = last > topY - 5;                                            // 마지막 계단 위는 뚫어 둠
                        s = y == topY && !stairwell ? Blocks.STONE_BRICK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP) : Blocks.AIR.defaultBlockState();
                    }
                    p.set(x, y, z, s);
                }
                if (!wall && d > 1.5) {                                                          // 나선 디딤돌 (10칸에 한 바퀴)
                    double ang = Math.atan2(dz, dx);
                    if (ang < 0) ang += Math.PI * 2;
                    for (int n = 0; ; n++) {
                        double hy = base + (ang / (Math.PI * 2) + n) * 10;
                        if (hy > topY - 1) break;
                        int y = (int) Math.floor(hy);
                        boolean half = hy - y >= 0.5;
                        p.set(x, half ? y + 1 : y, z, half ? Blocks.STONE_BRICK_SLAB.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState());
                    }
                    if (d > 4.3 && h(x, 11, z) % 8 == 0) for (int y = base + 6; y < topY; y += 9) p.set(x, y, z, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
                }
            });
            // 꼭대기 → 큰 섬 동쪽 끝 판자길
            int[] s = ISLES[0];
            for (int x = s[0] + s[2] - 4; x <= TOWER_X - TOWER_R + 1; x++) for (int dz = -1; dz <= 1; dz++) {
                p.set(x, topY, TOWER_Z + dz, Blocks.SPRUCE_PLANKS.defaultBlockState());
                p.set(x, topY + 1, TOWER_Z + dz, Blocks.AIR.defaultBlockState());
                p.set(x, topY + 2, TOWER_Z + dz, Blocks.AIR.defaultBlockState());
                if (Math.abs(dz) == 1) p.set(x, topY + 1, TOWER_Z + dz, Blocks.SPRUCE_FENCE.defaultBlockState());
            }
            for (int y = 1; y <= 3; y++) p.set(TOWER_X - TOWER_R, topY + y, TOWER_Z, Blocks.AIR.defaultBlockState());
            p.set(TOWER_X - TOWER_R, topY + 1, TOWER_Z, Blocks.AIR.defaultBlockState());
        }
    }

    /** 탑 앞 학자 야영지: 천막, 연구 탁자, 느린 낙하 물약 상자 */
    final class Camp extends Feature {
        final int cx = TOWER_X, cz = TOWER_Z + TOWER_R + 9, base;

        Camp() {
            super(TOWER_X - 8, TOWER_Z + TOWER_R + 2, TOWER_X + 8, TOWER_Z + TOWER_R + 16);
            base = KingdomTerrain.column(TOWER_X, TOWER_Z + TOWER_R + 9).h();
        }

        @Override
        public void build(Placer p, CityLayout city) {
            p.forColumns(cx - 7, cz - 6, cx + 7, cz + 6, (x, z) -> {
                RoadSites.pad(p, x, z, base, h(x, 1, z) % 3 == 0 ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState());
            });
            for (int tx : new int[]{cx - 4, cx + 4}) {                                           // 천막 2개
                for (int dz = -2; dz <= 2; dz++) for (int k = 0; k <= 2; k++) {
                    p.set(tx - 2 + k, base + 1 + k, cz + dz, Blocks.WHITE_WOOL.defaultBlockState());
                    p.set(tx + 2 - k, base + 1 + k, cz + dz, Blocks.WHITE_WOOL.defaultBlockState());
                }
                p.set(tx, base + 1, cz, Blocks.RED_CARPET.defaultBlockState());
            }
            p.set(cx, base + 1, cz - 3, Blocks.CAMPFIRE.defaultBlockState());
            p.set(cx + 2, base + 1, cz - 4, Blocks.CARTOGRAPHY_TABLE.defaultBlockState());
            p.set(cx - 2, base + 1, cz - 4, Blocks.BREWING_STAND.defaultBlockState());
            int chx = cx, chz = cz + 4;
            if (p.pass() == Placer.Pass.SOLID && p.contains(chx, chz)) {
                p.set(chx, base + 1, chz, Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.FACING, Direction.NORTH));
                BlockEntity be = p.level().getBlockEntity(new BlockPos(chx, base + 1, chz));
                if (be instanceof ChestBlockEntity c) {
                    for (int i = 0; i < 6; i++) c.setItem(i, PotionContents.createItemStack(Items.POTION, Potions.SLOW_FALLING));
                    c.setItem(9, new ItemStack(Items.ENDER_PEARL, 4));
                    c.setItem(10, new ItemStack(Items.BREAD, 12));
                    c.setChanged();
                }
            }
            for (int y = 1; y <= 3; y++) p.set(cx + 7, base + y, cz + 4, Blocks.SPRUCE_FENCE.defaultBlockState());
            p.set(cx + 7, base + 4, cz + 4, Blocks.LIGHT_BLUE_BANNER.defaultBlockState());
        }

        @Override public boolean needsSpawn() { return true; }

        @Override
        public void spawn(ServerLevel level, CityLayout city) {
            String[] names = {"유적 학자", "하늘 지도 제작자"};
            for (int i = 0; i < names.length; i++) {
                Mob m = EntityType.VILLAGER.spawn(level, new BlockPos(cx - 1 + i * 2, base + 1, cz - 1), MobSpawnType.STRUCTURE);
                if (m != null) {
                    m.setPersistenceRequired();
                    m.setCustomName(Component.literal(names[i]));
                }
            }
        }
    }
}
