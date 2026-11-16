package io.github.aswq0258.magiccity.site;

import io.github.aswq0258.magiccity.build.BuildLayout;
import io.github.aswq0258.magiccity.build.Placer;
import io.github.aswq0258.magiccity.city.CityLayout;
import io.github.aswq0258.magiccity.city.Palette;
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
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 세계수 성역 (마스터플랜 E1, 반경 약 210) — 엘프 대수림 한가운데. 세계수 순례길(서부 숲길 → 엘프 순례촌) 끝.
 *
 *  - 세계수: 뿌리가 땅 위로 드러난 거대한 나무 (줄기 지름 약 30, 높이 약 140, 수관 지름 약 400), 큰 가지 12개(120~165칸)와
 *    가지를 받치는 기근(반얀나무처럼 땅까지 내려온 뿌리 기둥), 진달래 잎 수관,
 *    빛나는 열매 덩굴. 줄기를 감아 오르는 나선 계단과 3단 전망대, 꼭대기 "세계수의 심장" 제단
 *  - 정령호: 남서쪽의 맑은 호수, 바닥의 빛, 연잎, 가운데 섬의 정령 제단, 숲의 정령(알레이)
 *  - 엘프 마을: 자작나무 줄기 위 둥근 나무집 10채, 장로의 전당(둥근 홀), 엘프 주민
 *  - 세계수 광장과 마을을 잇는 등불 오솔길. 성역 안은 몬스터가 출몰하지 않는 안전 지대
 */
public final class WorldTree implements BuildLayout {
    public static final int OX = KingdomTerrain.worldX(-1300), OZ = KingdomTerrain.worldZ(-300), R = 210;
    private static final int PLAZA_R = 40, TRUNK_H = 100, STAIR_TOP = 92, STAIR_PITCH = 30;
    private static final int[] DECKS = {30, 60, STAIR_TOP};

    private final int base;
    private final long seed;
    private final List<Feature> features = new ArrayList<>();
    private final Map<Long, List<Feature>> byChunk = new HashMap<>();
    private final List<int[]> paths = new ArrayList<>();     // {x0, z0, x1, z1}
    private final List<int[]> taken = new ArrayList<>();

    private static final BlockState BARK = Blocks.OAK_WOOD.defaultBlockState();
    private static final BlockState BARK2 = Blocks.STRIPPED_OAK_WOOD.defaultBlockState();
    private static final BlockState MOSS = Blocks.MOSS_BLOCK.defaultBlockState();
    private static final BlockState LEAF = Blocks.AZALEA_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
    private static final BlockState LEAF_FLOWER = Blocks.FLOWERING_AZALEA_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
    private static final BlockState BIRCH_LEAF = Blocks.BIRCH_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);

    public WorldTree(long seed) {
        this.seed = seed;
        this.base = KingdomTerrain.column(OX, OZ).h();
        Tree tree = new Tree();
        add(tree);
        take(OX - 50, OZ - 50, OX + 50, OZ + 50);
        Lake lake = new Lake(OX - 70, OZ + 85);
        add(lake);
        take(lake.minX, lake.minZ, lake.maxX, lake.maxZ);
        ElderHall hall = new ElderHall(OX + 4, OZ - 82);
        add(hall);
        take(hall.minX - 3, hall.minZ - 3, hall.maxX + 3, hall.maxZ + 3);
        paths.add(new int[]{OX, OZ - PLAZA_R, hall.cx, hall.cz + 12});
        paths.add(new int[]{OX - 28, OZ + 28, lake.cx, lake.cz - Lake.RZ - 3});
        // 세계수 순례길 끝 → 광장 동쪽 입구로 이어지는 참배로 (그 띠에는 나무집을 짓지 않음)
        int[] end = pilgrimRoadEnd();
        take(Math.min(OX + PLAZA_R, end[0]) - 2, Math.min(OZ, end[1]) - 7, Math.max(OX + PLAZA_R, end[0]) + 2, Math.max(OZ, end[1]) + 7);
        paths.add(new int[]{OX + PLAZA_R + 1, OZ, end[0], end[1]});
        java.util.Random rand = new java.util.Random(seed);
        int made = 0;
        for (int t = 0; t < 400 && made < 10; t++) {
            double a = rand.nextDouble() * Math.PI * 2, r = 75 + rand.nextDouble() * 80;
            int x = OX + (int) Math.round(Math.cos(a) * r), z = OZ + (int) Math.round(Math.sin(a) * r);
            if (!free(x - 9, z - 9, x + 9, z + 9) || !clearOfPillars(x, z, 10)) continue;
            boolean road = false;
            for (int dx = -10; dx <= 10 && !road; dx += 5) for (int dz = -10; dz <= 10 && !road; dz += 5) road = KingdomTerrain.column(x + dx, z + dz).road() > 0;
            if (road) continue;
            Treehouse h = new Treehouse(x, z, rand.nextLong());
            add(h);
            take(x - 9, z - 9, x + 9, z + 9);
            int rx = OX + (int) Math.round(Math.cos(a) * (PLAZA_R + 1)), rz = OZ + (int) Math.round(Math.sin(a) * (PLAZA_R + 1));
            paths.add(new int[]{rx, rz, x, z});
            made++;
        }
        for (Feature f : features) {
            for (int x = (f.minX - 1) >> 4; x <= (f.maxX + 1) >> 4; x++) {
                for (int z = (f.minZ - 1) >> 4; z <= (f.maxZ + 1) >> 4; z++) {
                    byChunk.computeIfAbsent(ChunkPos.asLong(x, z), k -> new ArrayList<>()).add(f);
                }
            }
        }
    }

    /** 세계수 순례길이 성역 동쪽 끝에 닿는 지점 (길 비틀림을 반영해 실제 길 위 칸을 찾음) */
    private static int[] pilgrimRoadEnd() {
        int ex = KingdomTerrain.worldX(-1195), ez = KingdomTerrain.worldZ(-300);
        int[] best = {ex, ez};
        double bd = 1e9;
        for (int dx = -50; dx <= 50; dx += 2) {
            for (int dz = -50; dz <= 50; dz += 2) {
                int x = ex + dx, z = ez + dz;
                if (!KingdomTerrain.column(x, z).onRoad()) continue;
                double d = Math.hypot(x - OX, z - OZ);
                if (d < bd) {
                    bd = d;
                    best = new int[]{x, z};
                }
            }
        }
        return best;
    }

    private void add(Feature f) {
        features.add(f);
    }

    private void take(int x0, int z0, int x1, int z1) {
        taken.add(new int[]{x0, z0, x1, z1});
    }

    private boolean free(int x0, int z0, int x1, int z1) {
        for (int[] q : taken) if (x0 - 4 <= q[2] && x1 + 4 >= q[0] && z0 - 4 <= q[3] && z1 + 4 >= q[1]) return false;
        return true;
    }

    public int featureCount() {
        return features.size();
    }

    @Override public int cx() { return OX; }
    @Override public int cz() { return OZ; }
    @Override public int base() { return base; }
    @Override public int radius() { return R; }
    @Override public CityLayout city() { return null; }
    @Override public int shapeMinY() { return base - 30; }
    @Override public int shapeMaxY() { return base + 140; }

    @Override
    public List<Feature> featuresIn(ChunkPos cp) {
        return byChunk.getOrDefault(cp.toLong(), Collections.emptyList());
    }

    /** 나무집·장로의 전당 실내 조명 */
    @Override
    public int[] lightRange(int x, int z) {
        for (Feature f : featuresIn(new ChunkPos(x >> 4, z >> 4))) {
            if (x < f.minX || x > f.maxX || z < f.minZ || z > f.maxZ) continue;
            if (f instanceof Treehouse) return new int[]{f.groundY + 12, f.groundY + 18};
            if (f instanceof ElderHall) return new int[]{f.groundY, f.groundY + 10};
        }
        return null;
    }

    private int h(int x, int z, int salt) {
        return Shapes.hash(x, salt, z, seed);
    }

    // ───────────────────────── 오솔길 ─────────────────────────

    private static double segDist(double x, double z, int[] s) {
        double vx = s[2] - s[0], vz = s[3] - s[1];
        double t = Math.max(0, Math.min(1, ((x - s[0]) * vx + (z - s[1]) * vz) / (vx * vx + vz * vz)));
        return Math.hypot(x - s[0] - vx * t, z - s[1] - vz * t);
    }

    @Override
    public void buildColumn(Placer p, int x, int z) {
        if (p.pass() != Placer.Pass.SOLID) return;
        double r = Math.hypot(x - OX, z - OZ);
        if (r > R) return;
        boolean ring = r >= PLAZA_R - 1 && r <= PLAZA_R + 2;
        boolean path = ring;
        double best = 99;
        for (int[] s : paths) best = Math.min(best, segDist(x + 0.5 * Math.sin(z / 7.0), z, s));
        if (best <= 1.2) path = true;
        if (!path) {
            // 길가 등불 (오솔길 바로 옆, 띄엄띄엄)
            if ((best > 1.2 && best <= 2.4 || r > PLAZA_R + 2 && r <= PLAZA_R + 3.4) && h(x, z, 31) % 23 == 0) {
                int top = p.naturalTop(x, z);
                p.set(x, top + 1, z, Blocks.BIRCH_FENCE.defaultBlockState());
                p.set(x, top + 2, z, Blocks.LANTERN.defaultBlockState());
            }
            return;
        }
        int top = p.naturalTop(x, z);
        BlockState below = p.get(x, top, z);
        if (!below.getFluidState().isEmpty()) return;
        p.clearBetween(x, z, top + 1, top + 5);          // 머리 위 공간만 (수관·가지는 남김)
        int k = h(x, z, 32);
        p.set(x, top, z, ring ? (k % 4 == 0 ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState())
                : k % 5 == 0 ? Blocks.MOSS_BLOCK.defaultBlockState() : Blocks.DIRT_PATH.defaultBlockState());
    }

    /** 평평한 터 다지기 — 위로는 건물 높이(24칸)까지만 비워서 그 위의 세계수 수관이 뚫리지 않게 함 */
    static void pad(Placer p, int x, int z, int b, BlockState top) {
        if (p.pass() != Placer.Pass.SOLID || !p.contains(x, z)) return;
        p.clearBetween(x, z, b + 1, b + 24);
        p.set(x, b, z, top);
        for (int y = b - 1; y > b - 30; y--) {
            BlockState s = p.get(x, y, z);
            if (!s.isAir() && s.getFluidState().isEmpty() && !s.canBeReplaced()) break;
            p.set(x, y, z, b - y < 3 ? Blocks.DIRT.defaultBlockState() : Blocks.STONE.defaultBlockState());
        }
    }

    // ───────────────────────── 세계수 ─────────────────────────

    /** 높이 y 에서 줄기 반지름 */
    private static double trunkR(double y) {
        double t = Math.max(0, Math.min(1, y / TRUNK_H));
        return 7 + 11 * Math.pow(1 - t, 1.3) + (y < 16 ? (16 - y) * 0.5 : 0);
    }

    /** 큰 가지에서 땅으로 내려온 기근(받침 뿌리) {x, z, 반지름, 위쪽 y} */
    private final List<double[]> pillars = new ArrayList<>();

    /** 큰 가지 끝·중간·곁가지 끝 (잎 덩어리 위치) — branches 보다 먼저 만들어져야 함 */
    private final List<double[]> tips = new ArrayList<>();
    /** 가지·뿌리 선분 {x0,y0,z0,x1,y1,z1,r0,r1} (중심 기준 상대 좌표) */
    private final double[][] branches = makeBranches();
    private final double[][] roots = makeRoots();
    /** 잎 덩어리 {x,y,z,rx,ry} */
    private final double[][] crowns = makeCrowns();

    /** 기근을 세우면 안 되는 곳 (광장·정령호·장로의 전당·순례길 참배로) */
    private static boolean keepClear(double x, double z) {
        if (Math.hypot(x, z) < PLAZA_R + 8) return true;
        if (Math.hypot((x + 70) / 50.0, (z - 85) / 38.0) < 1) return true;
        if (Math.abs(x - 4) < 22 && Math.abs(z + 82) < 22) return true;
        return x > PLAZA_R - 4 && Math.abs(z) < 12;
    }

    /**
     * 큰 가지 12개: 줄기에서 비스듬히 솟았다가(굵기 7) 거의 수평으로 아주 멀리 뻗고(120~165칸) 끝이 다시 하늘로 휨.
     * 큰 가지마다 곁가지 3개, 중간·바깥쪽에서 땅까지 내려온 기근(반얀나무처럼 가지를 받침).
     */
    private double[][] makeBranches() {
        java.util.Random r = new java.util.Random(seed ^ 0xB4A9);
        List<double[]> out = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            double a = Math.PI * 2 * i / 12 + r.nextDouble() * 0.3;
            double y0 = 50 + r.nextInt(34), L = 120 + r.nextInt(46);
            double sx = Math.cos(a) * trunkR(y0) * 0.5, sz = Math.sin(a) * trunkR(y0) * 0.5;
            double p1x = Math.cos(a) * L * 0.3, p1z = Math.sin(a) * L * 0.3, p1y = y0 + 12 + r.nextInt(6);
            double p2x = Math.cos(a + 0.1) * L * 0.62, p2z = Math.sin(a + 0.1) * L * 0.62, p2y = p1y + 2 + r.nextInt(5);
            double p3x = Math.cos(a + 0.17) * L, p3z = Math.sin(a + 0.17) * L, p3y = p2y + 6 + r.nextInt(9);
            out.add(new double[]{sx, y0, sz, p1x, p1y, p1z, 7.0, 4.8});
            out.add(new double[]{p1x, p1y, p1z, p2x, p2y, p2z, 4.8, 3.2});
            out.add(new double[]{p2x, p2y, p2z, p3x, p3y, p3z, 3.2, 1.6});
            double s1x = p1x + Math.cos(a + 0.8) * 42, s1z = p1z + Math.sin(a + 0.8) * 42, s1y = p1y + 12;
            double s2x = p2x + Math.cos(a - 0.7) * 40, s2z = p2z + Math.sin(a - 0.7) * 40, s2y = p2y + 9;
            double m3x = (p2x + p3x) / 2, m3z = (p2z + p3z) / 2, m3y = (p2y + p3y) / 2;
            double s3x = m3x + Math.cos(a + 0.9) * 30, s3z = m3z + Math.sin(a + 0.9) * 30, s3y = m3y + 8;
            out.add(new double[]{p1x, p1y, p1z, s1x, s1y, s1z, 3.0, 1.2});
            out.add(new double[]{p2x, p2y, p2z, s2x, s2y, s2z, 2.6, 1.1});
            out.add(new double[]{m3x, m3y, m3z, s3x, s3y, s3z, 2.2, 1.0});
            tips.add(new double[]{p3x, p3y + 5, p3z, 26 + r.nextInt(6), 11});
            tips.add(new double[]{p2x, p2y + 7, p2z, 22, 10});
            tips.add(new double[]{m3x, m3y + 7, m3z, 20, 9});
            tips.add(new double[]{s1x, s1y + 4, s1z, 18, 8});
            tips.add(new double[]{s2x, s2y + 4, s2z, 17, 8});
            tips.add(new double[]{s3x, s3y + 4, s3z, 15, 7});
            // 기근: 가지 중간과 바깥쪽에서 땅으로
            for (double f : new double[]{0.62, 0.85}) {
                double px = Math.cos(a + 0.1) * L * f, pz = Math.sin(a + 0.1) * L * f;
                if (keepClear(px, pz)) continue;
                pillars.add(new double[]{px, pz, f < 0.7 ? 2.6 : 1.9, f < 0.7 ? p2y : (p2y + p3y) / 2});
            }
        }
        // 줄기 꼭대기에서 가운데 수관 속으로 솟는 가지 5개 (줄기가 뚝 끊겨 보이지 않게)
        for (int i = 0; i < 5; i++) {
            double a = Math.PI * 2 * i / 5 + r.nextDouble() * 0.5;
            double ex = Math.cos(a) * (22 + r.nextInt(12)), ez = Math.sin(a) * (22 + r.nextInt(12));
            out.add(new double[]{Math.cos(a) * 3, TRUNK_H - 6, Math.sin(a) * 3, ex, TRUNK_H + 26 + r.nextInt(10), ez, 4.0, 1.6});
        }
        for (double[] pl : pillars) out.add(new double[]{pl[0], -2, pl[1], pl[0] * 0.98, pl[3], pl[1] * 0.98, pl[2] + 1.6, pl[2]});
        return out.toArray(new double[0][]);
    }

    /** 나무집이 기근과 겹치지 않는지 */
    private boolean clearOfPillars(int x, int z, int pad) {
        for (double[] pl : pillars) if (Math.hypot(OX + pl[0] - x, OZ + pl[1] - z) < pad + pl[2] + 2) return false;
        return true;
    }

    private double[][] makeRoots() {
        java.util.Random r = new java.util.Random(seed ^ 0x2007);
        double[][] out = new double[18][];
        for (int i = 0; i < 9; i++) {
            double a = Math.PI * 2 * i / 9 + r.nextDouble() * 0.4, len = 32 + r.nextInt(14);
            double mx = Math.cos(a) * len * 0.5, mz = Math.sin(a) * len * 0.5;
            double ex = Math.cos(a - 0.3) * len, ez = Math.sin(a - 0.3) * len;
            out[i * 2] = new double[]{Math.cos(a) * 14, 11, Math.sin(a) * 14, mx * 1.5, 4, mz * 1.5, 6.0, 4.0};
            out[i * 2 + 1] = new double[]{mx * 1.5, 4, mz * 1.5, ex * 1.6, -1.5, ez * 1.6, 4.0, 1.6};
        }
        return out;
    }

    private double[][] makeCrowns() {
        List<double[]> c = new ArrayList<>();
        c.add(new double[]{0, 124, 0, 46, 18});
        c.addAll(tips);
        return c.toArray(new double[0][]);
    }

    private static double segDist3(double x, double y, double z, double[] s, double[] tOut) {
        double vx = s[3] - s[0], vy = s[4] - s[1], vz = s[5] - s[2];
        double t = Math.max(0, Math.min(1, ((x - s[0]) * vx + (y - s[1]) * vy + (z - s[2]) * vz) / (vx * vx + vy * vy + vz * vz)));
        tOut[0] = t;
        double dx = x - s[0] - vx * t, dy = y - s[1] - vy * t, dz = z - s[2] - vz * t;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public final class Tree extends Feature {
        Tree() {
            super(OX - 205, OZ - 205, OX + 205, OZ + 205);
            groundY = base;
        }

        @Override
        public void build(Placer p, CityLayout city) {
            final int b = groundY;
            double[] t = new double[1];
            p.forColumns(minX, minZ, maxX, maxZ, (x, z) -> {
                double dx = x - OX, dz = z - OZ, d = Math.hypot(dx, dz), ang = Math.atan2(dz, dx);
                int k = h(x, z, 1);
                // 광장
                if (d <= PLAZA_R - 1.5) {
                    pad(p, x, z, b, k % 7 == 0 ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState() : k % 3 == 0 ? MOSS : Blocks.GRASS_BLOCK.defaultBlockState());
                    if (d > 22 && d < PLAZA_R - 3 && k % 9 == 0) p.set(x, b + 1, z, k % 2 == 0 ? Blocks.LILY_OF_THE_VALLEY.defaultBlockState() : Blocks.AZURE_BLUET.defaultBlockState());
                    else if (d > 22 && d < PLAZA_R - 3 && k % 13 == 0) p.set(x, b + 1, z, Blocks.MOSS_CARPET.defaultBlockState());
                }
                int lowestLeaf = Integer.MAX_VALUE;
                List<double[]> bs = new ArrayList<>(), cs = new ArrayList<>();
                for (double[] seg : branches) {
                    if (dx < Math.min(seg[0], seg[3]) - seg[6] - 1 || dx > Math.max(seg[0], seg[3]) + seg[6] + 1
                            || dz < Math.min(seg[2], seg[5]) - seg[6] - 1 || dz > Math.max(seg[2], seg[5]) + seg[6] + 1) continue;
                    bs.add(seg);
                }
                for (double[] c : crowns) if (Math.abs(dx - c[0]) <= c[3] * 1.2 && Math.abs(dz - c[2]) <= c[3] * 1.2) cs.add(c);
                boolean nearTrunk = d <= trunkR(-2) + 2 || d < 80;
                if (bs.isEmpty() && cs.isEmpty() && !nearTrunk) {
                    stairsAndDecks(p, b, x, z, dx, dz, d, ang);
                    return;
                }
                for (int y = -3; y <= 150; y++) {
                    BlockState s = null;
                    // 줄기
                    if (y >= -2 && y <= TRUNK_H + 34) {
                        double rr = trunkR(y) * (1 + 0.1 * Math.sin(ang * 5 + y / 9.0) + 0.05 * Math.sin(ang * 11 - y / 5.0));
                        if (y > TRUNK_H) rr -= (y - TRUNK_H) * 0.17;                    // 수관 속으로 가늘어지며 이어짐
                        if (d <= rr) s = (Math.abs(Math.sin(ang * 7 + y / 14.0)) < 0.12 && d > rr - 1.5) ? BARK2 : BARK;
                    }
                    // 뿌리
                    if (s == null && y <= 9) for (double[] seg : roots) {
                        double dd = segDist3(dx, y, dz, seg, t);
                        if (dd <= seg[6] + (seg[7] - seg[6]) * t[0]) {
                            s = y >= 0 && h(x, z + y, 2) % 6 == 0 ? MOSS : BARK;
                            break;
                        }
                    }
                    // 가지
                    if (s == null) for (double[] seg : bs) {
                        if (y < Math.min(seg[1], seg[4]) - seg[6] - 1 || y > Math.max(seg[1], seg[4]) + seg[6] + 1) continue;
                        double dd = segDist3(dx, y, dz, seg, t);
                        if (dd <= seg[6] + (seg[7] - seg[6]) * t[0]) {
                            s = BARK;
                            break;
                        }
                    }
                    // 수관
                    if (s == null && y >= 50) for (double[] c : cs) {
                        if (Math.abs(y - c[1]) > c[4] * 1.2) continue;
                        double ex = (dx - c[0]) / c[3], ey = (y - c[1]) / c[4], ez = (dz - c[2]) / c[3];
                        double e = ex * ex + ey * ey + ez * ez;
                        double n = 0.18 * Math.sin(dx / 3.0 + y / 4.0) * Math.cos(dz / 3.5 - y / 5.0);
                        if (e < 1 + n) {
                            int kk = h(x, z + y * 7, 3) % 100;
                            if (e < 0.5 && kk < 3) s = Blocks.SHROOMLIGHT.defaultBlockState();
                            else if (e > 0.75 && kk < 2) s = null;                   // 군데군데 빈틈
                            else s = kk < 28 ? LEAF_FLOWER : LEAF;
                            if (s != null) lowestLeaf = Math.min(lowestLeaf, y);
                            break;
                        }
                    }
                    if (s != null) p.set(x, b + y, z, s);
                }
                // 빛나는 열매 덩굴 (수관 밑에 늘어짐)
                if (lowestLeaf != Integer.MAX_VALUE && d > trunkR(lowestLeaf) + 2 && h(x, z, 4) % 7 == 0) {
                    int len = 2 + h(x, z, 5) % 8;
                    for (int i = 1; i <= len; i++) {
                        int y = lowestLeaf - i;
                        if (!p.get(x, b + y, z).isAir()) break;
                        p.set(x, b + y, z, (i == len ? Blocks.CAVE_VINES : Blocks.CAVE_VINES_PLANT).defaultBlockState().setValue(CaveVines.BERRIES, h(x, z, 6 + i) % 3 == 0));
                    }
                }
                stairsAndDecks(p, b, x, z, dx, dz, d, ang);
            });
            // 꼭대기 제단: 세계수의 심장
            int top = b + STAIR_TOP + 1;
            for (int s = 0; s < 4; s++) {
                double a = Math.PI / 2 * s + 0.4;
                int ax = OX + (int) Math.round(Math.cos(a) * (trunkR(STAIR_TOP) + 4)), az = OZ + (int) Math.round(Math.sin(a) * (trunkR(STAIR_TOP) + 4));
                p.set(ax, top, az, Blocks.AMETHYST_BLOCK.defaultBlockState());
                p.set(ax, top + 1, az, Blocks.AMETHYST_CLUSTER.defaultBlockState());
            }
            int hx = OX + (int) Math.round(trunkR(STAIR_TOP) + 3), hz = OZ;
            p.set(hx, top, hz, Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState());
            p.set(hx, top + 1, hz, Blocks.ENCHANTING_TABLE.defaultBlockState());
            OldCapital.chest(p, hx, top, hz + 2, Direction.WEST, seed ^ 0x77, BuiltInLootTables.ANCIENT_CITY);
        }

        /** 3단 전망대 + 줄기를 감아 오르는 나선 계단 (반 블록씩 오름, 전망대를 뚫고 지나감) */
        private void stairsAndDecks(Placer p, int b, int x, int z, double dx, double dz, double d, double ang) {
            decks(p, b, x, z, d);
            double frac = (ang + Math.PI) / (Math.PI * 2);
            for (int k = 0; k <= STAIR_TOP / STAIR_PITCH + 1; k++) {
                double yf = 1 + (frac + k) * STAIR_PITCH;
                if (yf > STAIR_TOP) break;
                double rin = trunkR(yf) + 0.4, rout = rin + 3.2;
                if (d < rin || d > rout) continue;
                int y = (int) Math.floor(yf);
                boolean half = yf - y < 0.5;
                p.set(x, b + y, z, half ? Blocks.SPRUCE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM) : Blocks.SPRUCE_PLANKS.defaultBlockState());
                for (int c = 1; c <= 3; c++) p.set(x, b + y + c, z, Blocks.AIR.defaultBlockState());
                if (d > rout - 1) {
                    p.set(x, b + y + 1, z, Blocks.SPRUCE_FENCE.defaultBlockState());
                    if (h(x, z, 8) % 9 == 0) p.set(x, b + y + 2, z, Blocks.LANTERN.defaultBlockState());
                }
            }
        }

        private void decks(Placer p, int b, int x, int z, double d) {
            for (int deck : DECKS) {
                double rin = trunkR(deck) + 0.4, rout = rin + (deck == STAIR_TOP ? 9 : 7);
                if (d < rin || d > rout) continue;
                p.set(x, b + deck, z, h(x, z, 9) % 6 == 0 ? Blocks.STRIPPED_SPRUCE_WOOD.defaultBlockState() : Blocks.SPRUCE_PLANKS.defaultBlockState());
                for (int c = 1; c <= 3; c++) p.set(x, b + deck + c, z, Blocks.AIR.defaultBlockState());
                if (d > rout - 1) {
                    p.set(x, b + deck + 1, z, Blocks.SPRUCE_FENCE.defaultBlockState());
                    if (h(x, z, 10) % 5 == 0) p.set(x, b + deck + 2, z, Blocks.LANTERN.defaultBlockState());
                } else if (h(x, z, 11) % 37 == 0) {
                    p.set(x, b + deck + 1, z, Blocks.LANTERN.defaultBlockState());
                }
            }
        }
    }

    // ───────────────────────── 정령호 ─────────────────────────

    public final class Lake extends Feature {
        final int cx, cz, level;
        static final int RX = 40, RZ = 28;

        Lake(int cx, int cz) {
            super(cx - RX - 8, cz - RZ - 8, cx + RX + 8, cz + RZ + 8);
            this.cx = cx;
            this.cz = cz;
            this.level = KingdomTerrain.column(cx, cz).h() - 1;
            groundY = level;
        }

        private double e(int x, int z) {
            double ex = (x - cx) / (double) RX, ez = (z - cz) / (double) RZ;
            return Math.sqrt(ex * ex + ez * ez) + 0.08 * Math.sin(x / 5.0) * Math.cos(z / 6.0);
        }

        @Override
        public void build(Placer p, CityLayout city) {
            final int L = level;
            p.forColumns(minX, minZ, maxX, maxZ, (x, z) -> {
                double e = e(x, z), di = Math.hypot(x - cx, z - cz);
                int k = h(x, z, 20);
                if (di <= 6) {                                                          // 가운데 섬
                    pad(p, x, z, L + 1, k % 3 == 0 ? MOSS : Blocks.GRASS_BLOCK.defaultBlockState());
                    for (int y = L; y > L - 8; y--) p.set(x, y, z, Blocks.DIRT.defaultBlockState());
                    return;
                }
                if (e < 1) {
                    int depth = 1 + (int) Math.round(5 * (1 - e));
                    if (p.pass() == Placer.Pass.SOLID) p.clearBetween(x, z, L + 1, L + 24);
                    p.set(x, L - depth, z, k % 19 == 0 ? Blocks.SEA_LANTERN.defaultBlockState() : k % 3 == 0 ? Blocks.CLAY.defaultBlockState() : Blocks.SAND.defaultBlockState());
                    for (int y = L - depth + 1; y <= L; y++) p.set(x, y, z, Blocks.WATER.defaultBlockState());
                    if (depth > 2 && k % 11 == 0) p.set(x, L - depth + 1, z, Blocks.SEAGRASS.defaultBlockState());
                    else if (k % 29 == 0) p.set(x, L + 1, z, Blocks.LILY_PAD.defaultBlockState());
                } else if (e < 1.22) {                                                  // 물가
                    pad(p, x, z, L, k % 4 == 0 ? Blocks.MOSS_BLOCK.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState());
                    if (k % 8 == 0) p.set(x, L + 1, z, Blocks.FERN.defaultBlockState());
                    else if (k % 11 == 0) p.set(x, L + 1, z, Blocks.BLUE_ORCHID.defaultBlockState());
                }
            });
            // 북쪽 물가에서 섬까지 나무다리
            for (int z = cz - RZ - 1; z <= cz - 6; z++) for (int dx = -1; dx <= 1; dx++) {
                p.set(cx + dx, L + 1, z, Blocks.BIRCH_PLANKS.defaultBlockState());
                if (dx != 0 && (z & 3) == 0) {
                    p.set(cx + dx, L + 2, z, Blocks.BIRCH_FENCE.defaultBlockState());
                    if ((z & 7) == 0) p.set(cx + dx, L + 3, z, Blocks.LANTERN.defaultBlockState());
                }
            }
            // 섬의 정령 제단: 방해석 기둥 + 자수정 + 엔드 막대 고리
            for (int y = 2; y <= 7; y++) p.set(cx, L + y, cz, y == 7 ? Blocks.AMETHYST_BLOCK.defaultBlockState() : Blocks.CALCITE.defaultBlockState());
            p.set(cx, L + 8, cz, Blocks.AMETHYST_CLUSTER.defaultBlockState());
            for (int i = 0; i < 8; i++) {
                double a = Math.PI * 2 * i / 8;
                int ax = cx + (int) Math.round(Math.cos(a) * 4), az = cz + (int) Math.round(Math.sin(a) * 4);
                p.set(ax, L + 2, az, i % 2 == 0 ? Blocks.END_ROD.defaultBlockState() : Blocks.SMALL_AMETHYST_BUD.defaultBlockState());
            }
        }

        @Override
        public boolean needsSpawn() {
            return true;
        }

        @Override
        public void spawn(ServerLevel level, CityLayout city) {
            for (int i = 0; i < 6; i++) {
                double a = Math.PI * 2 * i / 6;
                Mob m = EntityType.ALLAY.spawn(level, new BlockPos(cx + (int) (Math.cos(a) * 5), this.level + 4, cz + (int) (Math.sin(a) * 5)), MobSpawnType.STRUCTURE);
                if (m != null) {
                    m.setPersistenceRequired();
                    m.setCustomName(Component.literal("숲의 정령"));
                }
            }
        }
    }

    // ───────────────────────── 엘프 나무집 ─────────────────────────

    /** 자작나무 줄기 위 둥근 나무집: 지상 12칸 높이 원형 마루, 흰 벽, 잎 지붕, 사다리 */
    public final class Treehouse extends Feature {
        final int tx, tz;
        final long hseed;

        Treehouse(int tx, int tz, long hseed) {
            super(tx - 8, tz - 8, tx + 9, tz + 9);
            this.tx = tx;
            this.tz = tz;
            this.hseed = hseed;
            groundY = KingdomTerrain.column(tx, tz).h();
        }

        @Override
        public void build(Placer p, CityLayout city) {
            final int g = groundY, floor = g + 12;
            final double ccx = tx + 0.5, ccz = tz + 0.5;
            p.forColumns(minX, minZ, maxX, maxZ, (x, z) -> {
                double d = Math.hypot(x + 0.5 - (ccx + 0.5), z + 0.5 - (ccz + 0.5));
                boolean trunk = (x == tx || x == tx + 1) && (z == tz || z == tz + 1);
                if (d <= 3) pad(p, x, z, g, Blocks.GRASS_BLOCK.defaultBlockState());
                if (trunk) {
                    for (int y = g + 1; y <= g + 22; y++) p.set(x, y, z, Blocks.BIRCH_LOG.defaultBlockState());
                    for (int y = -2; y <= 0; y++) p.set(x, g + y, z, Blocks.BIRCH_LOG.defaultBlockState());
                    return;
                }
                if (d <= 7.5) p.set(x, floor, z, d > 6.5 ? Blocks.STRIPPED_BIRCH_WOOD.defaultBlockState() : Blocks.BIRCH_PLANKS.defaultBlockState());
                if (d > 6.5 && d <= 7.5) p.set(x, floor + 1, z, Blocks.BIRCH_FENCE.defaultBlockState());
                if (d > 4.0 && d <= 5.0) {                                              // 둥근 벽
                    boolean door = z > tz + 3 && Math.abs(x - tx) <= 0;
                    for (int y = floor + 1; y <= floor + 4; y++) {
                        BlockState s = door && y <= floor + 2 ? Blocks.AIR.defaultBlockState()
                                : y == floor + 2 && (x + z) % 3 == 0 ? Blocks.GLASS_PANE.defaultBlockState()
                                : y == floor + 4 ? Blocks.STRIPPED_BIRCH_WOOD.defaultBlockState() : Blocks.CALCITE.defaultBlockState();
                        p.set(x, y, z, s);
                    }
                }
                // 원뿔 잎 지붕
                for (int k = 0; k <= 6; k++) {
                    double rr = 6 - k;
                    if (d <= rr && d > rr - 1.4) p.set(x, floor + 5 + k, z, k % 3 == 2 ? LEAF_FLOWER : BIRCH_LEAF);
                }
                if (d <= 4) p.set(x, floor + 5, z, Blocks.BIRCH_PLANKS.defaultBlockState());   // 천장
                // 줄기 꼭대기 잎
                double dc = Math.hypot(x - tx - 0.5, z - tz - 0.5);
                for (int y = g + 20; y <= g + 25; y++) if (Math.hypot(dc, (y - (g + 22)) * 1.3) < 3.6 && !trunk) p.set(x, y, z, BIRCH_LEAF);
            });
            // 사다리 (줄기 동쪽 면) + 마루 구멍
            for (int y = g + 1; y <= floor + 1; y++) p.set(tx + 2, y, tz, Palette.ladder(Direction.EAST));
            // 가구
            p.set(tx - 3, floor + 1, tz, Palette.bed(Direction.SOUTH, false));
            p.set(tx - 3, floor + 1, tz + 1, Palette.bed(Direction.SOUTH, true));
            p.set(tx + 3, floor + 1, tz - 2, Blocks.CRAFTING_TABLE.defaultBlockState());
            p.set(tx, floor + 1, tz - 3, Blocks.BOOKSHELF.defaultBlockState());
            p.set(tx + 1, floor + 1, tz - 3, Blocks.FLOWER_POT.defaultBlockState());
            OldCapital.chest(p, tx - 2, floor + 1, tz - 2, Direction.SOUTH, hseed, BuiltInLootTables.VILLAGE_PLAINS_HOUSE);
            p.set(tx + 3, floor + 4, tz + 2, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
            p.set(tx - 2, floor + 4, tz - 2, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
            for (int s = -1; s <= 1; s += 2) p.set(tx + s * 6, floor + 1, tz + s * 2, Blocks.LANTERN.defaultBlockState());
            p.set(tx + 2, g + 1, tz + 3, Blocks.LANTERN.defaultBlockState());
        }
    }

    // ───────────────────────── 장로의 전당 ─────────────────────────

    /** 둥근 홀: 방해석·자작나무 벽, 잎 돔 지붕, 가운데 정령 나무 화단, 책장과 독서대 */
    public final class ElderHall extends Feature {
        final int cx, cz;
        static final int HR = 11;

        ElderHall(int cx, int cz) {
            super(cx - HR - 2, cz - HR - 2, cx + HR + 2, cz + HR + 2);
            this.cx = cx;
            this.cz = cz;
            groundY = KingdomTerrain.column(cx, cz).h();
        }

        @Override
        public void build(Placer p, CityLayout city) {
            final int b = groundY;
            p.forColumns(minX, minZ, maxX, maxZ, (x, z) -> {
                double d = Math.hypot(x - cx, z - cz);
                if (d > HR + 1.5) return;
                int k = h(x, z, 40);
                pad(p, x, z, b, d > HR ? MOSS : k % 5 == 0 ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState() : Blocks.POLISHED_DIORITE.defaultBlockState());
                if (d > HR - 1 && d <= HR) {
                    boolean door = z > cz && Math.abs(x - cx) <= 1;
                    double ang = Math.atan2(z - cz, x - cx);
                    boolean pillar = Math.abs(Math.sin(ang * 6)) < 0.18;
                    for (int y = 1; y <= 8; y++) {
                        BlockState s = door && y <= 4 ? Blocks.AIR.defaultBlockState()
                                : pillar ? Blocks.BIRCH_LOG.defaultBlockState()
                                : y >= 3 && y <= 6 ? Blocks.WHITE_STAINED_GLASS_PANE.defaultBlockState()
                                : Blocks.CALCITE.defaultBlockState();
                        p.set(x, b + y, z, s);
                    }
                }
                // 잎 돔
                for (int y = 9; y <= 9 + HR; y++) {
                    double rr = Math.sqrt(Math.max(0, HR * HR - (y - 9) * (y - 9) * 1.4));
                    if (d <= rr && d > rr - 1.3) p.set(x, b + y, z, (k + y) % 4 == 0 ? LEAF_FLOWER : LEAF);
                }
                if (d <= HR - 1) p.set(x, b + 9, z, d < 2 ? Blocks.GLASS.defaultBlockState() : Blocks.BIRCH_PLANKS.defaultBlockState());   // 천장
                // 안쪽: 둘레 책장, 가운데 화단
                if (d > HR - 2 && d <= HR - 1 && (Math.abs(x - cx) > 1 || z < cz)) {
                    p.set(x, b + 1, z, Blocks.BOOKSHELF.defaultBlockState());
                    p.set(x, b + 2, z, Blocks.BOOKSHELF.defaultBlockState());
                }
                if (d <= 2.5) p.set(x, b, z, MOSS);
            });
            p.set(cx, b + 1, cz, Blocks.FLOWERING_AZALEA.defaultBlockState());
            for (int s = -1; s <= 1; s += 2) {
                p.set(cx + s * 4, b + 1, cz - 3, Blocks.LECTERN.defaultBlockState());
                p.set(cx + s * 3, b + 8, cz + s * 3, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
                p.set(cx + s * 2, b + 1, cz + HR + 1, Blocks.BIRCH_FENCE.defaultBlockState());
                p.set(cx + s * 2, b + 2, cz + HR + 1, Blocks.LANTERN.defaultBlockState());
            }
            p.set(cx, b + 8, cz - 5, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
            for (int x = cx - 2; x <= cx + 2; x++) p.set(x, b + 1, cz - 7, Blocks.BIRCH_STAIRS.defaultBlockState().setValue(net.minecraft.world.level.block.StairBlock.FACING, Direction.NORTH));
        }

        @Override
        public boolean needsSpawn() {
            return true;
        }

        @Override
        public void spawn(ServerLevel level, CityLayout city) {
            String[] names = {"엘프 장로", "엘프 사서", "엘프 순찰대", "엘프 약초사", "엘프 궁수", "엘프 음유시인"};
            for (int i = 0; i < names.length; i++) {
                Mob m = EntityType.VILLAGER.spawn(level, new BlockPos(cx - 3 + i, groundY + 1, cz + 3), MobSpawnType.STRUCTURE);
                if (m != null) {
                    m.setPersistenceRequired();
                    m.setCustomName(Component.literal(names[i]));
                }
            }
        }
    }
}
