package io.github.aswq0258.magiccity.site;

import io.github.aswq0258.magiccity.build.BuildLayout;
import io.github.aswq0258.magiccity.build.Placer;
import io.github.aswq0258.magiccity.city.CityLayout;
import io.github.aswq0258.magiccity.city.Shapes;
import io.github.aswq0258.magiccity.city.feature.Feature;
import io.github.aswq0258.magiccity.world.KingdomRoads;
import io.github.aswq0258.magiccity.world.KingdomTerrain;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 엘프 산맥 관문 "은빛 협문" (마스터플랜 M1) — 엘프-북부 고갯길이 산맥을 넘는 좁은 협곡을 가로막은 엘프 요새
 *
 *  - 협곡을 가로지르는 흰 성벽(양쪽 산비탈까지), 가운데 아치형 문루(내린 쇠살문 끝, 은빛 문장), 양옆 가는 원탑과 구리 첨탑
 *  - 엘프 숲 쪽(남서)의 안뜰: 둥근 엘프 막사 2동, 원형 마구간, 달의 제단(자수정과 엔드 막대), 우물, 자작나무
 *  - 북쪽 면: 눈 덮인 땅을 향한 깃발과 망루, 눈 골렘
 *  - 엘프 관문 대장·파수꾼, 숲의 정령, 흰 말
 */
public final class ElfGate implements BuildLayout {
    public static final int GX, GZ, G;
    /** 길 방향 (북동 = +u) 과 가로 방향 (+w) */
    private static final double DX, DZ, PX, PZ;

    static {
        int cx = KingdomTerrain.worldX(-350), cz = KingdomTerrain.worldZ(-1040);
        double[] best = {cx, cz}, prev = null, next = null;
        double bd = 1e9;
        for (KingdomRoads.Road r : KingdomRoads.ROADS) {
            List<double[]> cl = KingdomRoads.centerline(r, 2);
            for (int i = 1; i + 1 < cl.size(); i++) {
                double d = Math.hypot(cl.get(i)[0] - cx, cl.get(i)[1] - cz);
                if (d < bd) {
                    bd = d;
                    best = cl.get(i);
                    prev = cl.get(Math.max(0, i - 6));
                    next = cl.get(Math.min(cl.size() - 1, i + 6));
                }
            }
        }
        GX = (int) Math.round(best[0]);
        GZ = (int) Math.round(best[1]);
        double vx = 0.832, vz = -0.555;
        if (prev != null && next != null) {
            vx = next[0] - prev[0];
            vz = next[1] - prev[1];
            if (vz > 0) {                                                                       // 북동쪽이 +u 가 되게
                vx = -vx;
                vz = -vz;
            }
            double l = Math.hypot(vx, vz);
            vx /= l;
            vz /= l;
        }
        DX = vx;
        DZ = vz;
        PX = -vz;
        PZ = vx;
        G = KingdomTerrain.column(GX, GZ).h();
    }

    public static final int TOP = 16, WALL_HALF = 3, GATE_HALF_U = 8, GATE_HALF_W = 12, TOWER_W = 15, TOWER_R = 5;
    /** 둥근 건물 {u, w, 반지름, 종류: 0 막사 1 마구간 2 달의 제단 3 우물} */
    private static final int[][] ROUND = {{-32, 13, 6, 0}, {-32, -13, 6, 0}, {-20, -15, 6, 1}, {-46, 0, 5, 2}, {-18, 12, 2, 3}};

    private final long seed;
    private final List<ChunkPos> chunks = new ArrayList<>();
    private final List<Feature> people = new ArrayList<>();

    public ElfGate(long seed) {
        this.seed = seed;
        people.add(new Wardens());
        LinkedHashSet<Long> keys = new LinkedHashSet<>();
        for (int x = (GX - 90) >> 4; x <= (GX + 90) >> 4; x++) for (int z = (GZ - 90) >> 4; z <= (GZ + 90) >> 4; z++) keys.add(ChunkPos.asLong(x, z));
        for (long k : keys) chunks.add(new ChunkPos(k));
    }

    public static boolean near(double x, double z) {
        return Math.hypot(x - GX, z - GZ) < 80;
    }

    private static int wx(double u, double w) {
        return (int) Math.round(GX + DX * u + PX * w);
    }

    private static int wz(double u, double w) {
        return (int) Math.round(GZ + DZ * u + PZ * w);
    }

    @Override public int cx() { return GX; }
    @Override public int cz() { return GZ; }
    @Override public int base() { return G; }
    @Override public int radius() { return 0; }
    @Override public CityLayout city() { return null; }
    @Override public List<ChunkPos> chunkList() { return chunks; }
    @Override public int shapeMinY() { return G - 10; }
    @Override public int shapeMaxY() { return G + 50; }

    @Override
    public List<Feature> featuresIn(ChunkPos cp) {
        return cp.toLong() == ChunkPos.asLong(GX >> 4, GZ >> 4) ? people : Collections.emptyList();
    }

    @Override
    public int[] lightRange(int x, int z) {
        double[] uw = uw(x, z);
        if (Math.abs(uw[0]) <= GATE_HALF_U + 1 && Math.abs(uw[1]) <= TOWER_W + TOWER_R + 1) return new int[]{G, G + TOP + 24};
        for (int[] r : ROUND) if (Math.hypot(uw[0] - r[0], uw[1] - r[1]) <= r[2] + 1) return new int[]{G, G + 10};
        return null;
    }

    private static double[] uw(int x, int z) {
        double dx = x - GX, dz = z - GZ;
        return new double[]{dx * DX + dz * DZ, dx * PX + dz * PZ};
    }

    private int h(int x, int y, int z) {
        return Shapes.hash(x, y, z, seed);
    }

    private static final BlockState WHITE = Blocks.CALCITE.defaultBlockState();
    private static final BlockState QUARTZ = Blocks.QUARTZ_BRICKS.defaultBlockState();
    private static final BlockState COPPER = Blocks.WAXED_OXIDIZED_CUT_COPPER.defaultBlockState();

    // ───────────────────────── 기둥 ─────────────────────────

    @Override
    public void buildColumn(Placer p, int x, int z) {
        double[] t = uw(x, z);
        double u = t[0], w = t[1];
        int terrain = KingdomTerrain.column(x, z).h();
        int hh = h(x, 1, z);
        // 안뜰 (엘프 숲 쪽): 땅을 관문 높이로 깎고 고름
        boolean court = u < -GATE_HALF_U && u > -56 && Math.abs(w) <= 22 + (u + 56) * 0.0;
        if (court) {
            p.clearAbove(x, z, G + 1);
            for (int y = G - 3; y < G; y++) p.set(x, y, z, Blocks.DIRT.defaultBlockState());
            BlockState top = Math.abs(w) <= 3 ? (hh % 3 == 0 ? Blocks.POLISHED_DIORITE.defaultBlockState() : Blocks.SMOOTH_QUARTZ.defaultBlockState())
                    : hh % 9 == 0 ? Blocks.MOSS_BLOCK.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState();
            p.set(x, G, z, top);
            if (Math.abs(w) > 4 && Math.abs(w) <= 21 && top.is(Blocks.GRASS_BLOCK)) {
                if (hh % 17 == 0) p.set(x, G + 1, z, Blocks.LILY_OF_THE_VALLEY.defaultBlockState());
                else if (hh % 11 == 0) p.set(x, G + 1, z, Blocks.SHORT_GRASS.defaultBlockState());
            }
            if (Math.abs(w) == 4 && Math.floorMod((int) Math.round(u), 8) == 0) {             // 길가 등불 기둥
                for (int y = 1; y <= 3; y++) p.set(x, G + y, z, Blocks.BIRCH_FENCE.defaultBlockState());
                p.set(x, G + 4, z, Blocks.END_ROD.defaultBlockState());
            }
            if (Math.abs(w) > 21.2 - 1 && terrain > G) {                                        // 깎은 비탈 옹벽
                for (int y = G + 1; y <= Math.min(terrain, G + 6); y++) p.set(x, y, z, Blocks.MOSSY_STONE_BRICKS.defaultBlockState());
            }
            round(p, x, z, u, w);
        }
        // 성벽
        if (Math.abs(u) <= WALL_HALF && Math.abs(w) > GATE_HALF_W && Math.abs(w) < 90 && terrain < G + TOP + 3) {
            p.foundation(x, z, Math.min(terrain, G) - 1, 20);
            for (int y = Math.min(terrain, G) - 2; y <= G + TOP; y++) {
                BlockState s = (y - G) % 6 == 0 ? QUARTZ : hh % 9 == 0 ? Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState() : WHITE;
                p.set(x, y, z, s);
            }
            if (Math.abs(u) > WALL_HALF - 0.8 && ((int) Math.round(w)) % 2 == 0) p.set(x, G + TOP + 1, z, QUARTZ);
            else if (Math.abs(u) <= WALL_HALF - 0.8) for (int y = G + TOP + 1; y <= G + TOP + 3; y++) p.set(x, y, z, Blocks.AIR.defaultBlockState());
            if (u > WALL_HALF - 0.8 && ((int) Math.round(w)) % 12 == 0) {                      // 북쪽 면 깃발
                p.set(x + (int) Math.round(DX), G + TOP - 2, z + (int) Math.round(DZ), Blocks.WHITE_BANNER.defaultBlockState());
            }
        }
        // 문루
        if (Math.abs(u) <= GATE_HALF_U && Math.abs(w) <= GATE_HALF_W) gatehouse(p, x, z, u, w, terrain);
        // 탑 두 개
        for (int s = -1; s <= 1; s += 2) {
            double d = Math.hypot(u, w - s * TOWER_W);
            if (d <= TOWER_R + 0.5) tower(p, x, z, d, terrain);
        }
    }

    private void gatehouse(Placer p, int x, int z, double u, double w, int terrain) {
        p.clearAbove(x, z, G + 1);
        p.foundation(x, z, G - 1, 20);
        double arch = 9 * Math.sqrt(Math.max(0, 1 - Math.pow(w / 4.0, 2)));
        int top = G + TOP + 8;
        for (int y = G; y <= top; y++) {
            BlockState s = (y - G) % 8 == 0 ? QUARTZ : WHITE;
            if (y == G) s = Math.abs(w) <= 3.5 ? Blocks.SMOOTH_QUARTZ.defaultBlockState() : QUARTZ;
            boolean passage = Math.abs(w) < 4 && y > G && y <= G + arch;
            if (passage) s = Blocks.AIR.defaultBlockState();
            if (passage && Math.abs(u - 6) < 0.6 && y > G + arch - 3) s = Blocks.IRON_BARS.defaultBlockState();   // 쇠살문 끝
            // 북쪽 정면 은빛 문장과 창
            if (!passage && Math.abs(u) > GATE_HALF_U - 0.8) {
                if (y == G + 13 && Math.abs(w) <= 1) s = Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState();
                if (y == G + 14 && Math.abs(w) < 0.6) s = Blocks.SEA_LANTERN.defaultBlockState();
                if ((y == G + 11 || y == G + 12) && Math.abs(Math.abs(w) - 7) < 0.6) s = Blocks.GLASS_PANE.defaultBlockState();
            }
            p.set(x, y, z, s);
        }
        if (Math.abs(u) > GATE_HALF_U - 0.8 || Math.abs(w) > GATE_HALF_W - 0.8) {
            if (((int) Math.round(u + w)) % 2 == 0) p.set(x, top + 1, z, QUARTZ);
        } else if (Math.abs(u) < 2 && Math.abs(w) < 2) {
            p.set(x, top + 1, z, Blocks.BIRCH_FENCE.defaultBlockState());
        }
        if (Math.abs(w) < 4 && Math.abs(u) < 0.6 && h(x, 3, z) % 2 == 0) p.set(x, G + 7, z, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
    }

    private void tower(Placer p, int x, int z, double d, int terrain) {
        int base = Math.min(terrain, G);
        p.foundation(x, z, base - 1, 20);
        int hgt = TOP + 20;
        boolean wall = d > TOWER_R - 0.8;
        for (int y = base; y <= G + hgt; y++) {
            BlockState s = wall ? ((y - G) % 7 == 0 ? QUARTZ : WHITE) : (y == G || (y - G) % 9 == 0 ? Blocks.BIRCH_PLANKS.defaultBlockState() : Blocks.AIR.defaultBlockState());
            if (wall && (y - G) % 7 == 4 && h(x, y, z) % 3 == 0) s = Blocks.GLASS_PANE.defaultBlockState();
            if (wall && y > G && y <= G + 2 && uw(x, z)[0] < -TOWER_R + 1.5 && Math.abs(Math.abs(uw(x, z)[1]) - TOWER_W) < 1.2) s = Blocks.AIR.defaultBlockState();   // 안뜰 쪽 문
            p.set(x, y, z, s);
        }
        for (int k = 0; k <= 12; k++) {                                                         // 구리 첨탑
            double cr = (TOWER_R + 1.2) * (1 - k / 12.0);
            if (d <= cr + 0.01 && d > cr - 1.4) p.set(x, G + hgt + 1 + k, z, k % 4 == 3 ? Blocks.WAXED_OXIDIZED_CHISELED_COPPER.defaultBlockState() : COPPER);
        }
        if (d < 0.6) {
            p.set(x, G + hgt + 13, z, Blocks.END_ROD.defaultBlockState());
            for (int y = G + 1; y < G + hgt; y++) p.set(x, y, z, Blocks.LADDER.defaultBlockState().setValue(net.minecraft.world.level.block.LadderBlock.FACING, Direction.NORTH));
        }
        if (d >= 0.6 && d < 1.6) for (int y = G + 1; y < G + hgt; y++) {
            if (p.get(x, y, z).is(Blocks.BIRCH_PLANKS)) p.set(x, y, z, Blocks.AIR.defaultBlockState());          // 사다리 구멍
        }
    }

    /** 안뜰의 둥근 엘프 건물 */
    private void round(Placer p, int x, int z, double u, double w) {
        for (int[] r : ROUND) {
            double d = Math.hypot(u - r[0], w - r[1]);
            if (d > r[2] + 0.5) continue;
            switch (r[3]) {
                case 0 -> {                                                                     // 막사: 흰 원형 벽, 자작 기둥, 구리 원뿔 지붕
                    boolean wall = d > r[2] - 0.8;
                    boolean door = wall && Math.abs(w - r[1]) < 1.2 && (r[1] > 0 ? w < r[1] : w > r[1]);
                    for (int y = 1; y <= 5; y++) {
                        BlockState s = wall ? (y == 3 && h(x, y, z) % 3 == 0 ? Blocks.GLASS_PANE.defaultBlockState()
                                : (h(x, 0, z) % 4 == 0 ? Blocks.STRIPPED_BIRCH_LOG.defaultBlockState() : WHITE)) : Blocks.AIR.defaultBlockState();
                        if (door && y <= 2) s = Blocks.AIR.defaultBlockState();
                        p.set(x, G + y, z, s);
                    }
                    p.set(x, G, z, Blocks.BIRCH_PLANKS.defaultBlockState());
                    for (int k = 0; k <= r[2]; k++) {
                        double cr = (r[2] + 1) * (1 - (double) k / (r[2] + 1));
                        if (d <= cr + 0.01 && d > cr - 1.4) p.set(x, G + 6 + k, z, COPPER);
                    }
                    if (!wall && d > 2 && d < 3.2 && h(x, 4, z) % 3 == 0) {
                        p.set(x, G + 1, z, h(x, 5, z) % 2 == 0 ? Blocks.WHITE_BED.defaultBlockState() : Blocks.BARREL.defaultBlockState());
                    }
                    if (d < 0.6) p.set(x, G + 5, z, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
                }
                case 1 -> {                                                                     // 원형 마구간
                    p.set(x, G, z, d < r[2] - 1 ? Blocks.HAY_BLOCK.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState());
                    if (d > r[2] - 0.8) p.set(x, G + 1, z, Blocks.BIRCH_FENCE.defaultBlockState());
                    if (d < 0.6) {
                        for (int y = 1; y <= 4; y++) p.set(x, G + y, z, Blocks.BIRCH_LOG.defaultBlockState());
                        p.set(x, G + 5, z, Blocks.BIRCH_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true));
                    }
                }
                case 2 -> {                                                                     // 달의 제단
                    p.set(x, G, z, d > r[2] - 1 ? Blocks.POLISHED_DIORITE.defaultBlockState() : Blocks.AMETHYST_BLOCK.defaultBlockState());
                    if (d > r[2] - 0.8 && h(x, 6, z) % 2 == 0) {
                        p.set(x, G + 1, z, Blocks.QUARTZ_PILLAR.defaultBlockState());
                        p.set(x, G + 2, z, Blocks.QUARTZ_PILLAR.defaultBlockState());
                        p.set(x, G + 3, z, Blocks.END_ROD.defaultBlockState());
                    }
                    if (d < 0.6) {
                        p.set(x, G + 1, z, Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState());
                        p.set(x, G + 2, z, Blocks.AMETHYST_CLUSTER.defaultBlockState());
                        Shapes.crystal(p, x, G + 6, z, 2);
                    }
                }
                case 3 -> {                                                                     // 우물
                    p.set(x, G - 3, z, Blocks.STONE.defaultBlockState());
                    if (d < 1.4) for (int y = G - 2; y <= G; y++) p.set(x, y, z, Blocks.WATER.defaultBlockState());
                    else {
                        p.set(x, G + 1, z, Blocks.CALCITE.defaultBlockState());
                        if (h(x, 7, z) % 2 == 0) {
                            p.set(x, G + 2, z, Blocks.BIRCH_FENCE.defaultBlockState());
                            p.set(x, G + 3, z, Blocks.BIRCH_SLAB.defaultBlockState());
                        }
                    }
                }
                default -> {
                }
            }
        }
    }

    /** 엘프 관문 대장·파수꾼, 숲의 정령, 흰 말, 북쪽 눈 골렘 */
    final class Wardens extends Feature {
        Wardens() {
            super(GX, GZ, GX, GZ);
        }

        @Override
        public void build(Placer p, CityLayout city) {
            if (p.contains(wx(-12, 6), wz(-12, 6))) OldCapital.chest(p, wx(-12, 6), G + 1, wz(-12, 6), Direction.NORTH, seed ^ 0xE1F, BuiltInLootTables.VILLAGE_FLETCHER);
        }

        @Override public boolean needsSpawn() { return true; }

        @Override
        public void spawn(ServerLevel level, CityLayout city) {
            String[] names = {"엘프 관문 대장", "엘프 파수꾼", "엘프 파수꾼", "엘프 궁수", "엘프 치유사"};
            for (int i = 0; i < names.length; i++) {
                Mob m = EntityType.VILLAGER.spawn(level, new BlockPos(wx(-14 - i * 4, -6), G + 1, wz(-14 - i * 4, -6)), MobSpawnType.STRUCTURE);
                if (m != null) {
                    m.setPersistenceRequired();
                    m.setCustomName(Component.literal(names[i]));
                }
            }
            for (int i = 0; i < 3; i++) {
                Mob m = EntityType.ALLAY.spawn(level, new BlockPos(wx(-46, i - 1), G + 3, wz(-46, i - 1)), MobSpawnType.STRUCTURE);
                if (m != null) {
                    m.setPersistenceRequired();
                    m.setCustomName(Component.literal("숲의 정령"));
                }
            }
            for (int i = 0; i < 2; i++) {
                Mob hz = EntityType.HORSE.spawn(level, new BlockPos(wx(-20 + i * 2, -15), G + 1, wz(-20 + i * 2, -15)), MobSpawnType.STRUCTURE);
                if (hz instanceof net.minecraft.world.entity.animal.horse.Horse horse) {
                    horse.setPersistenceRequired();
                    horse.setVariant(net.minecraft.world.entity.animal.horse.Variant.WHITE);
                }
            }
            for (int s = -1; s <= 1; s += 2) {
                Mob g = EntityType.SNOW_GOLEM.spawn(level, new BlockPos(wx(14, s * 8), G + 1, wz(14, s * 8)), MobSpawnType.STRUCTURE);
                if (g != null) {
                    g.setPersistenceRequired();
                    g.setCustomName(Component.literal("북쪽 파수병"));
                }
            }
        }
    }
}
