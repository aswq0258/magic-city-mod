package io.github.aswq0258.magiccity.site;

import io.github.aswq0258.magiccity.build.BuildLayout;
import io.github.aswq0258.magiccity.build.Placer;
import io.github.aswq0258.magiccity.city.CityLayout;
import io.github.aswq0258.magiccity.city.Palette;
import io.github.aswq0258.magiccity.city.Shapes;
import io.github.aswq0258.magiccity.city.feature.Feature;
import io.github.aswq0258.magiccity.city.feature.Frame;
import io.github.aswq0258.magiccity.world.KingdomRoads;
import io.github.aswq0258.magiccity.world.KingdomTerrain;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 동해안 항구도시 벨마르 (마스터플랜 P1) + 앞바다 등대와 해상신전 섬 (P2)
 *
 * 항구도시 (중심 = 동부 교역로·남동 교역 연결로·늪지 수로길이 모이는 곳)
 *  - 바다 쪽이 트인 반원 성벽(반지름 170), 길이 들어오는 곳마다 성문과 문탑
 *  - 가운데 분수 광장(해신상·노점), 광장에서 성문·항구로 뻗는 큰길, 고리 길 2개
 *  - 해안을 따라 돌 부두(안벽)와 항구 산책길, 바다로 뻗은 나무 잔교 4개, 정박한 범선 3척
 *  - 만 입구를 감싼 방파제 두 줄과 끝의 항구 등대 2기 (가운데 뱃길은 열림)
 *  - 항만청(시계탑), 해신 신전(돔), 상인 길드 세관, 갈매기 여관, 창고 거리, 어시장, 조선소(건조 중인 배)
 *  - 흰 회벽·사암·붉은 벽돌 집 + 청록·붉은·검은 지붕의 2~3층 집 수십 채
 * 등대 섬 (앞바다 약 1,100 블록)
 *  - 섬 동쪽 끝 거대 등대(높이 약 60, 흰·붉은 줄무늬, 꼭대기 등불실과 발코니), 등대지기 오두막
 *  - 섬 서쪽 해안의 해상신전(프리즈머린 열주 신전)과 그 앞바다 아래 잠긴 성소 (물로 찬 돔)
 *    성소: 보스 "심해의 수호자"(엘더 가디언), 가디언 스포너, 보물 상자 (바다의 심장)
 *  - 항구 쪽을 보는 섬 잔교
 */
public final class PortCity implements BuildLayout {
    public static final int PX = KingdomTerrain.worldX(1490), PZ = KingdomTerrain.worldZ(900);
    public static final int IX = KingdomTerrain.worldX(2070), IZ = KingdomTerrain.worldZ(950);
    public static final int B = 66, WATER_Y = KingdomTerrain.SEA - 1;
    public static final int R = 170, WALL_IN = 166, HARBOR_R = 238, MOLE_OUT = 244, ISLE_R = 230;
    private static final double MOUTH = Math.toRadians(18), MOUTH_HALF = Math.toRadians(9);
    private static final int DREDGE = 54;

    private final long seed;
    private final List<Feature> features = new ArrayList<>();
    private final Map<Long, List<Feature>> byChunk = new HashMap<>();
    private final List<int[]> taken = new ArrayList<>();
    private final List<int[]> lit = new ArrayList<>();          // 실내 조명 {x0, z0, x1, z1, y0, y1}
    private final List<double[]> gates = new ArrayList<>();     // 성문 각도 (라디안)
    private final List<ChunkPos> chunks = new ArrayList<>();
    private final Map<Long, Boolean> seaCache = new HashMap<>();
    private final double harborAngle;
    private final int sanctumFloor;

    public PortCity(long seed) {
        this.seed = seed;
        sanctumFloor = Math.min(KingdomTerrain.column(IX - 260, IZ).h() - 2, WATER_Y - Sanctum.RAD - 4);
        // 성문: 성벽 바로 바깥에서 대륙 길이 지나는 각도
        List<Double> hits = new ArrayList<>();
        for (int i = 0; i < 1440; i++) {
            double a = i * Math.PI * 2 / 1440;
            int x = PX + (int) Math.round(Math.cos(a) * (R + 8)), z = PZ + (int) Math.round(Math.sin(a) * (R + 8));
            if (KingdomRoads.nearest(x, z, 0.5, null) >= 0 && !sea(x, z)) hits.add(a);
        }
        List<double[]> groups = new ArrayList<>();
        for (double a : hits) {
            if (!groups.isEmpty() && a - groups.get(groups.size() - 1)[1] < 0.05) groups.get(groups.size() - 1)[1] = a;
            else groups.add(new double[]{a, a});
        }
        if (groups.size() > 1 && groups.get(0)[0] < 0.01 && groups.get(groups.size() - 1)[1] > Math.PI * 2 - 0.01) {
            double[] last = groups.remove(groups.size() - 1);
            groups.get(0)[0] = last[0] - Math.PI * 2;
        }
        for (double[] g : groups) gates.add(new double[]{(g[0] + g[1]) / 2});
        // 항구 큰길: 광장 → 만 안쪽(가장 가까운 바다)
        double best = 1e9, ha = 0;
        for (int i = 0; i < 360; i++) {
            double a = Math.toRadians(i);
            for (int r = 40; r <= R; r += 4) {
                if (sea(PX + (int) (Math.cos(a) * r), PZ + (int) (Math.sin(a) * r))) {
                    if (r < best) {
                        best = r;
                        ha = a;
                    }
                    break;
                }
            }
        }
        harborAngle = ha;

        Random rand = new Random(seed);
        take(PX - 30, PZ - 30, PX + 30, PZ + 30);                                        // 광장
        features.add(new Plaza());
        // ── 항구: 잔교·배
        int[] pierZ = {PZ - 60, PZ - 15, PZ + 30, PZ + 75};
        int ships = 0;
        for (int zp : pierZ) {
            int cx0 = coastX(zp);
            if (cx0 == Integer.MIN_VALUE) continue;
            int len = 0;
            while (len < 46 && Math.hypot(cx0 + len - PX, zp - PZ) < HARBOR_R - 8 && sea(cx0 + len, zp)) len++;
            if (len < 14) continue;
            features.add(new Pier(cx0 - 2, zp, len + 2));
            // 잔교 옆 정박 범선: 배가 놓일 줄의 해안선 바깥에서 시작
            int start = Integer.MIN_VALUE;
            for (int dz = 4; dz <= 16; dz += 4) {
                int c = coastX(zp + dz);
                if (c == Integer.MIN_VALUE) {
                    start = Integer.MIN_VALUE;
                    break;
                }
                start = Math.max(start, c + 4);
            }
            if (ships < 3 && start != Integer.MIN_VALUE) {
                int room = 0;
                while (room < 40 && Math.hypot(start + room - PX, zp + 10 - PZ) < HARBOR_R - 12 && sea(start + room, zp + 10)) room++;
                int L = Math.min(28, room - 8);
                if (L >= 18) {
                    features.add(new Ship(start + L / 2 + 1, zp + 10, L, rand.nextLong()));
                    ships++;
                }
            }
        }
        // ── 안벽 앞 건물 (항구 큰길 양옆)
        int[] cust = alongHarbor(24, 17);
        if (cust != null) addFixed(new Customs(cust[0], cust[1]));
        int[] fish = alongHarbor(14, -17);
        if (fish != null) addFixed(new FishMarket(fish[0], fish[1]));
        for (int k = 0; k < 4; k++) {
            int[] w = alongHarbor(17, -42 - 22 * k);
            if (w != null) addFixed(new Warehouse(w[0], w[1], rand.nextLong()));
        }
        int[] yard = alongHarbor(22, 60);
        if (yard != null) addFixed(new Shipyard(yard[0], yard[1]));
        // ── 큰 건물
        place(new Admiralty(), rand, Math.toRadians(-60), 95, 20);
        place(new SeaTemple(), rand, Math.toRadians(120), 95, 20);
        place(new Inn(), rand, gates.isEmpty() ? Math.PI : gates.get(0)[0], 125, 12);
        // ── 집
        Block[][] roofs = {{Blocks.DARK_PRISMARINE_STAIRS, Blocks.DARK_PRISMARINE_SLAB}, {Blocks.BRICK_STAIRS, Blocks.BRICK_SLAB},
                {Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILE_SLAB}, {Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_SLAB}};
        Block[] walls = {Blocks.CALCITE, Blocks.SMOOTH_SANDSTONE, Blocks.BRICKS, Blocks.WHITE_TERRACOTTA, Blocks.CALCITE};
        int houses = 0;
        for (int t = 0; t < 9000 && houses < 120; t++) {
            double a = rand.nextDouble() * Math.PI * 2, r = 36 + rand.nextDouble() * (WALL_IN - 46);
            int x = PX + (int) Math.round(Math.cos(a) * r), z = PZ + (int) Math.round(Math.sin(a) * r);
            int W = 7 + rand.nextInt(3) * 2, D = 7 + rand.nextInt(3) * 2;
            Direction front = facingStreet(x, z);
            boolean ns = front.getAxis() == Direction.Axis.Z;
            int sx = ns ? W : D, sz = ns ? D : W;
            int x0 = x - sx / 2, z0 = z - sz / 2, x1 = x0 + sx - 1, z1 = z0 + sz - 1;
            if (!free(x0 - 2, z0 - 2, x1 + 2, z1 + 2) || !landBox(x0 - 3, z0 - 3, x1 + 3, z1 + 3, 6)) continue;
            Block[] roof = roofs[rand.nextInt(roofs.length)];
            Block wall = walls[rand.nextInt(walls.length)];
            int floors = r < 90 ? 3 : 2 + (rand.nextInt(3) == 0 ? 1 : 0);
            addFixed(new House(x, z, front, W, D, floors, wall, roof[0], roof[1], rand.nextLong()));
            houses++;
        }
        // ── 등대 섬
        features.add(new Lighthouse(IX + 120, IZ - 10));
        lit.add(new int[]{IX + 113, IZ - 17, IX + 127, IZ - 3, KingdomTerrain.SEA, KingdomTerrain.SEA + 75});
        features.add(new KeeperCottage(IX + 96, IZ + 14));
        lit.add(new int[]{IX + 90, IZ + 8, IX + 102, IZ + 20, KingdomTerrain.SEA, KingdomTerrain.SEA + 20});
        features.add(new IslandTemple(IX - 120, IZ));
        features.add(new Sanctum(IX - 260, IZ));
        features.add(new IslandPier(IX, IZ));
        features.add(new GateTowers());

        for (Feature f : features) {
            if (f.groundY == 0) f.groundY = B;
            for (int x = (f.minX - 1) >> 4; x <= (f.maxX + 1) >> 4; x++) {
                for (int z = (f.minZ - 1) >> 4; z <= (f.maxZ + 1) >> 4; z++) {
                    byChunk.computeIfAbsent(ChunkPos.asLong(x, z), k -> new ArrayList<>()).add(f);
                }
            }
        }
        LinkedHashSet<Long> keys = new LinkedHashSet<>();
        for (int x = (PX - MOLE_OUT - 4) >> 4; x <= (PX + MOLE_OUT + 4) >> 4; x++)
            for (int z = (PZ - MOLE_OUT - 4) >> 4; z <= (PZ + MOLE_OUT + 4) >> 4; z++) keys.add(ChunkPos.asLong(x, z));
        for (int x = (IX - ISLE_R) >> 4; x <= (IX + ISLE_R) >> 4; x++)
            for (int z = (IZ - ISLE_R) >> 4; z <= (IZ + ISLE_R) >> 4; z++) keys.add(ChunkPos.asLong(x, z));
        keys.addAll(byChunk.keySet());
        for (long k : keys) chunks.add(new ChunkPos(k));
    }

    // ───────────────────────── 배치 도우미 ─────────────────────────

    /** 바다 (원래 지형에서 물 아래 바닥) */
    public boolean sea(int x, int z) {
        long k = ((long) x << 32) ^ (z & 0xffffffffL);
        Boolean v = seaCache.get(k);
        if (v != null) return v;
        KingdomTerrain.Column c = KingdomTerrain.column(x, z);
        boolean s = c.water() != Integer.MIN_VALUE && c.h() < c.water() && c.kind() != KingdomTerrain.Kind.RIVER;
        if (seaCache.size() > 400000) seaCache.clear();
        seaCache.put(k, s);
        return s;
    }

    /** 이 z 줄에서 도시 중심 동쪽으로 처음 바다가 나오는 x (없으면 MIN) */
    private int coastX(int z) {
        for (int x = PX; x <= PX + HARBOR_R; x++) if (sea(x, z)) return x;
        return Integer.MIN_VALUE;
    }

    /** 바다까지의 거리 (최대 lim) */
    private int seaDist(int x, int z, int lim) {
        for (int d = 1; d <= lim; d++) {
            for (int k = -d; k <= d; k++) {
                if (sea(x + k, z - d) || sea(x + k, z + d) || sea(x - d, z + k) || sea(x + d, z + k)) return d;
            }
        }
        return lim + 1;
    }

    private boolean landBox(int x0, int z0, int x1, int z1, int coastGap) {
        for (int x = x0; x <= x1; x += 3) for (int z = z0; z <= z1; z += 3) {
            if (Math.hypot(x - PX, z - PZ) > WALL_IN - 4) return false;
            if (seaDist(x, z, coastGap) <= coastGap) return false;
        }
        return seaDist(x1, z1, coastGap) > coastGap;
    }

    /** 안벽을 따라 (항구 큰길 기준 옆으로 lateral 만큼) 해안에서 inland 만큼 안쪽 자리 */
    private int[] alongHarbor(int inland, int lateral) {
        double a = harborAngle, lx = -Math.sin(a), lz = Math.cos(a);
        double bx = PX + lx * lateral, bz = PZ + lz * lateral;
        for (int r = 0; r <= R + 20; r++) {
            int x = (int) Math.round(bx + Math.cos(a) * r), z = (int) Math.round(bz + Math.sin(a) * r);
            if (sea(x, z)) {
                int cx = (int) Math.round(bx + Math.cos(a) * (r - inland)), cz = (int) Math.round(bz + Math.sin(a) * (r - inland));
                if (Math.hypot(cx - PX, cz - PZ) > WALL_IN - 12) return null;
                return new int[]{cx, cz};
            }
        }
        return null;
    }

    private void place(Feature f, Random rand, double a0, int r0, int tries) {
        // 큰 건물: 원하는 방향·거리 근처에서 땅이 넉넉한 자리를 찾아 옮겨 지음
        int w = f.maxX - f.minX, d = f.maxZ - f.minZ;
        for (int t = 0; t < tries * 20; t++) {
            double a = a0 + (rand.nextDouble() - 0.5) * 0.9 * Math.min(1, t / 40.0 + 0.1);
            int r = r0 + (int) ((rand.nextDouble() - 0.5) * 40 * Math.min(1, t / 40.0 + 0.1));
            int cx = PX + (int) (Math.cos(a) * r), cz = PZ + (int) (Math.sin(a) * r);
            int x0 = cx - w / 2, z0 = cz - d / 2;
            if (!free(x0 - 3, z0 - 3, x0 + w + 3, z0 + d + 3) || !landBox(x0 - 2, z0 - 2, x0 + w + 2, z0 + d + 2, 5)) continue;
            Feature moved = relocate(f, cx, cz);
            if (moved != null) {
                addFixed(moved);
                return;
            }
        }
    }

    private Feature relocate(Feature f, int cx, int cz) {
        if (f instanceof Admiralty) return new Admiralty(cx, cz);
        if (f instanceof SeaTemple) return new SeaTemple(cx, cz);
        if (f instanceof Inn) return new Inn(cx, cz);
        return null;
    }

    private void addFixed(Feature f) {
        features.add(f);
        take(f.minX, f.minZ, f.maxX, f.maxZ);
        lit.add(new int[]{f.minX, f.minZ, f.maxX, f.maxZ, B, B + 26});
    }

    private void take(int x0, int z0, int x1, int z1) {
        taken.add(new int[]{x0, z0, x1, z1});
    }

    private boolean free(int x0, int z0, int x1, int z1) {
        for (int[] q : taken) if (x0 <= q[2] && x1 >= q[0] && z0 <= q[3] && z1 >= q[1]) return false;
        for (int x = x0; x <= x1; x += 2) for (int z = z0; z <= z1; z += 2) if (street(x, z) > 0) return false;
        return street(x1, z1) == 0;
    }

    /** 가장 가까운 거리 쪽을 보는 방향 */
    private Direction facingStreet(int x, int z) {
        Direction best = Direction.SOUTH;
        int bd = 99;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            for (int k = 4; k < 30; k++) {
                if (street(x + d.getStepX() * k, z + d.getStepZ() * k) > 0) {
                    if (k < bd) {
                        bd = k;
                        best = d;
                    }
                    break;
                }
            }
        }
        return best;
    }

    // ───────────────────────── 거리 ─────────────────────────

    /** 0 = 거리 아님, 1 = 큰길, 2 = 고리 길·골목, 3 = 광장 */
    public int street(int x, int z) {
        int dx = x - PX, dz = z - PZ;
        double r = Math.hypot(dx, dz);
        if (r <= 28) return 3;
        if (r > WALL_IN + 1) return 0;
        if (Math.abs(r - 92) <= 2 || Math.abs(r - 140) <= 2) return 2;
        if (radial(dx, dz, harborAngle, 3.5)) return 1;
        for (double[] g : gates) if (radial(dx, dz, g[0], 3.5)) return 1;
        for (int k = 0; k < 8; k++) if (radial(dx, dz, harborAngle + Math.PI / 4 * k + Math.PI / 8, 1.5) && r > 40) return 2;
        return 0;
    }

    private static boolean radial(int dx, int dz, double a, double half) {
        double along = dx * Math.cos(a) + dz * Math.sin(a), lat = -dx * Math.sin(a) + dz * Math.cos(a);
        return along > 0 && Math.abs(lat) <= half;
    }

    private boolean gateAt(int x, int z) {
        int dx = x - PX, dz = z - PZ;
        for (double[] g : gates) if (radial(dx, dz, g[0], 4.5)) return true;
        return false;
    }

    private int h(int x, int z, int salt) {
        return Shapes.hash(x, salt, z, seed);
    }

    // ───────────────────────── BuildLayout ─────────────────────────

    @Override public int cx() { return PX; }
    @Override public int cz() { return PZ; }
    @Override public int base() { return B; }
    @Override public int radius() { return MOLE_OUT; }
    @Override public CityLayout city() { return null; }
    @Override public List<ChunkPos> chunkList() { return chunks; }
    @Override public int shapeMinY() { return 30; }
    @Override public int shapeMaxY() { return B + 80; }

    public int featureCount() {
        return features.size();
    }

    @Override
    public List<Feature> featuresIn(ChunkPos cp) {
        return byChunk.getOrDefault(cp.toLong(), Collections.emptyList());
    }

    @Override
    public int[] lightRange(int x, int z) {
        for (int[] q : lit) if (x >= q[0] && x <= q[2] && z >= q[1] && z <= q[3]) return new int[]{q[4], q[5]};
        return null;
    }

    /** WATER 패스에서는 비어 있는 칸에만 물 (배·잔교 기둥을 지키려고) */
    private static void water(Placer p, int x, int y, int z) {
        if (p.pass() == Placer.Pass.WATER && !p.get(x, y, z).isAir() && !p.get(x, y, z).is(Blocks.WATER)) return;
        p.set(x, y, z, Blocks.WATER.defaultBlockState());
    }

    @Override
    public void buildColumn(Placer p, int x, int z) {
        double r = Math.hypot(x - PX, z - PZ);
        if (r <= MOLE_OUT + 1) {
            harborColumn(p, x, z, r);
            return;
        }
        if (Math.hypot(x - IX, z - IZ) <= ISLE_R) islandColumn(p, x, z);
    }

    private void harborColumn(Placer p, int x, int z, double r) {
        boolean sea = sea(x, z);
        int hh = h(x, z, 1);
        if (sea) {
            if (r > MOLE_OUT) return;
            double ang = Math.atan2(z - PZ, x - PX);
            boolean mouth = Math.abs(Math.atan2(Math.sin(ang - MOUTH), Math.cos(ang - MOUTH))) < MOUTH_HALF;
            if (r >= HARBOR_R + 1 && !mouth) {                                               // 방파제
                p.clearAbove(x, z, B + 1);
                for (int y = DREDGE - 6; y <= B - 1; y++) p.set(x, y, z, hh % 5 == 0 ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState());
                p.set(x, B, z, hh % 3 == 0 ? Blocks.POLISHED_ANDESITE.defaultBlockState() : Blocks.SMOOTH_STONE.defaultBlockState());
                if (r >= MOLE_OUT - 0.8) p.set(x, B + 1, z, Blocks.STONE_BRICK_WALL.defaultBlockState());
                else if (hh % 41 == 0) {
                    p.set(x, B + 1, z, Blocks.SPRUCE_FENCE.defaultBlockState());
                    p.set(x, B + 2, z, Blocks.LANTERN.defaultBlockState());
                }
                return;
            }
            if (r > HARBOR_R + 1 && mouth) return;                                          // 뱃길: 원래 바다 그대로
            // 항구 안: 배가 드나들게 바닥을 파고 물을 채움
            if (p.pass() == Placer.Pass.SOLID) p.clearAbove(x, z, WATER_Y + 1);
            p.set(x, DREDGE - 1, z, hh % 3 == 0 ? Blocks.GRAVEL.defaultBlockState() : Blocks.SAND.defaultBlockState());
            for (int y = DREDGE; y <= WATER_Y; y++) water(p, x, y, z);
            if (hh % 7 == 0) p.set(x, DREDGE, z, Blocks.SEAGRASS.defaultBlockState());
            return;
        }
        // 땅
        if (r > R + 1) return;
        if (r >= WALL_IN) {
            wall(p, x, z, r);
            return;
        }
        p.clearAbove(x, z, B + 1);
        p.foundation(x, z, B - 1, 20);
        int sd = seaDist(x, z, 9);
        int st = street(x, z);
        BlockState s;
        if (sd <= 2) {                                                                       // 안벽 (돌 부두 끝)
            for (int y = DREDGE - 2; y < B; y++) p.set(x, y, z, Blocks.STONE_BRICKS.defaultBlockState());
            s = Blocks.SMOOTH_STONE.defaultBlockState();
            p.set(x, B, z, s);
            if (sd == 1 && hh % 9 == 0) p.set(x, B + 1, z, Blocks.SPRUCE_FENCE.defaultBlockState());     // 계류 말뚝
            else if (sd == 1 && hh % 23 == 0) {
                p.set(x, B + 1, z, Blocks.SPRUCE_FENCE.defaultBlockState());
                p.set(x, B + 2, z, Blocks.SPRUCE_FENCE.defaultBlockState());
                p.set(x, B + 3, z, Blocks.LANTERN.defaultBlockState());
            }
            return;
        }
        if (sd <= 9 || st == 1) {                                                            // 항구 산책길·큰길
            s = hh % 7 == 0 ? Blocks.ANDESITE.defaultBlockState() : hh % 3 == 0 ? Blocks.POLISHED_ANDESITE.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState();
            if (sd <= 9 && hh % 97 == 0) {
                p.set(x, B, z, s);
                p.set(x, B + 1, z, hh % 2 == 0 ? Blocks.BARREL.defaultBlockState() : Blocks.SPRUCE_PLANKS.defaultBlockState());
                return;
            }
        } else if (st == 3) {
            int ring = (int) Math.hypot(x - PX, z - PZ);
            s = ring % 6 == 0 ? Blocks.CYAN_TERRACOTTA.defaultBlockState() : ring % 3 == 0 ? Blocks.CALCITE.defaultBlockState() : Blocks.SMOOTH_SANDSTONE.defaultBlockState();
        } else if (st == 2) {
            s = hh % 4 == 0 ? Blocks.COBBLESTONE.defaultBlockState() : hh % 5 == 0 ? Blocks.GRAVEL.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState();
        } else {
            s = hh % 9 == 0 ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState();
            if (hh % 53 == 0) {
                p.set(x, B, z, Blocks.GRASS_BLOCK.defaultBlockState());
                p.set(x, B + 1, z, Blocks.POPPY.defaultBlockState());
                return;
            }
            if (hh % 31 == 0) {
                p.set(x, B, z, Blocks.GRASS_BLOCK.defaultBlockState());
                p.set(x, B + 1, z, Blocks.SHORT_GRASS.defaultBlockState());
                return;
            }
        }
        p.set(x, B, z, s);
        // 거리 등불
        if (st > 0 && st < 3 && hh % 47 == 0 && street(x + 2, z) == 0 || st > 0 && st < 3 && hh % 47 == 1 && street(x, z + 2) == 0) {
            p.set(x, B + 1, z, Blocks.DARK_OAK_FENCE.defaultBlockState());
            p.set(x, B + 2, z, Blocks.DARK_OAK_FENCE.defaultBlockState());
            p.set(x, B + 3, z, Blocks.DARK_OAK_FENCE.defaultBlockState());
            p.set(x, B + 4, z, Blocks.LANTERN.defaultBlockState());
        }
    }

    /** 성벽 (높이 10, 위 걷는 길과 총안), 길이 지나는 곳은 아치 성문 */
    private void wall(Placer p, int x, int z, double r) {
        p.clearAbove(x, z, B + 1);
        p.foundation(x, z, B - 1, 20);
        boolean gate = gateAt(x, z);
        int top = B + 10;
        for (int y = B; y <= top; y++) {
            BlockState s = (y - B) % 5 == 0 ? Blocks.POLISHED_ANDESITE.defaultBlockState() : h(x, z + y, 2) % 6 == 0 ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState();
            if (gate && y > B && y <= B + 6) s = Blocks.AIR.defaultBlockState();
            if (gate && y == B) s = Blocks.STONE_BRICKS.defaultBlockState();
            p.set(x, y, z, s);
        }
        if (r >= R - 0.7) {
            double ang = Math.atan2(z - PZ, x - PX);
            if (((int) Math.round(ang * r)) % 2 == 0) p.set(x, top + 1, z, Blocks.STONE_BRICKS.defaultBlockState());
        } else if (r <= WALL_IN + 0.7 && h(x, z, 3) % 3 == 0) {
            p.set(x, top + 1, z, Blocks.STONE_BRICK_WALL.defaultBlockState());
        }
        if (gate && r > WALL_IN + 1 && r < R - 1) {
            p.set(x, B + 6, z, Blocks.IRON_BARS.defaultBlockState());                         // 내려진 쇠창살 끝
        }
    }

    private void islandColumn(Placer p, int x, int z) {
        // 섬은 원래 지형 그대로, 바닷가 일부에만 바위·해초 장식
        if (sea(x, z)) return;
        int hh = h(x, z, 5);
        KingdomTerrain.Column c = KingdomTerrain.column(x, z);
        if (c.kind() == KingdomTerrain.Kind.BEACH && hh % 61 == 0) p.set(x, c.h() + 1, z, Blocks.COBBLESTONE.defaultBlockState());
    }

    // ───────────────────────── 공통 도우미 ─────────────────────────

    static BlockState door(Direction facing, boolean upper) {
        return Blocks.SPRUCE_DOOR.defaultBlockState().setValue(DoorBlock.FACING, facing)
                .setValue(DoorBlock.HALF, upper ? DoubleBlockHalf.UPPER : DoubleBlockHalf.LOWER);
    }

    static BlockState stairs(Block b, Direction facing) {
        return b.defaultBlockState().setValue(StairBlock.FACING, facing);
    }

    static BlockState slabTop(Block b) {
        return b.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP);
    }

    static final BlockState HANG = Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);

    static void villager(ServerLevel level, int x, int y, int z, String name) {
        Mob m = EntityType.VILLAGER.spawn(level, new BlockPos(x, y, z), MobSpawnType.STRUCTURE);
        if (m != null) {
            m.setPersistenceRequired();
            if (name != null) m.setCustomName(Component.literal(name));
        }
    }

    /** 박공 지붕 집 몸체 (Frame 기준: 앞벽 v=0, 문은 가운데) */
    static void body(Placer p, Frame f, int b, int W, int D, int floors, BlockState wall, BlockState post,
                             Block roofStairs, Block roofSlab, boolean shopFront) {
        final int FH = 5, top = floors * FH;
        for (int u = -1; u <= W; u++) for (int v = -1; v <= D; v++) {
            int x = f.x(u, v), z = f.z(u, v);
            if (!p.contains(x, z)) continue;
            p.clearAbove(x, z, b + 1);
            p.set(x, b, z, u >= 0 && u < W && v >= 0 && v < D ? Blocks.SPRUCE_PLANKS.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState());
            p.foundation(x, z, b - 1, 16);
        }
        for (int u = 0; u < W; u++) for (int v = 0; v < D; v++) {
            boolean eu = u == 0 || u == W - 1, ev = v == 0 || v == D - 1, corner = eu && ev;
            for (int ly = 1; ly <= top; ly++) {
                BlockState s;
                if (eu || ev) {
                    int inFloor = (ly - 1) % FH + 1;
                    boolean band = ly % FH == 0;
                    s = corner || band ? post : wall;
                    if (!corner && !band && (inFloor == 2 || inFloor == 3) && ((ev && u % 3 == 1) || (eu && v % 3 == 1)))
                        s = Blocks.GLASS_PANE.defaultBlockState();
                    if (v == 0 && u == W / 2 && ly <= 2) s = door(f.back, ly == 2);
                    if (shopFront && v == 0 && ly <= 3 && !corner && Math.abs(u - W / 2) >= 2 && ly >= 2) s = Blocks.AIR.defaultBlockState();
                } else {
                    s = ly % FH == 0 ? (u == 1 && v == D - 2 && ly < top ? Blocks.AIR.defaultBlockState() : Blocks.SPRUCE_PLANKS.defaultBlockState())
                            : Blocks.AIR.defaultBlockState();
                }
                f.set(p, b, u, ly, v, s);
            }
        }
        for (int ly = 1; ly < top - 1 && floors > 1; ly++) f.set(p, b, 1, ly, D - 2, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, f.front));
        int half = (W + 1) / 2;
        for (int k = 0; k <= half; k++) {
            int y = top + k;
            for (int v = -1; v <= D; v++) {
                int uL = k - 1, uR = W - k;
                if (uL >= uR - 1) {
                    f.set(p, b, uL, y, v, roofSlab.defaultBlockState());
                    if (uL != uR) f.set(p, b, uR, y, v, roofSlab.defaultBlockState());
                    continue;
                }
                f.set(p, b, uL, y, v, stairs(roofStairs, f.right));
                f.set(p, b, uR, y, v, stairs(roofStairs, f.left));
                if (k > 0 && (v == 0 || v == D - 1)) for (int u = uL + 1; u < uR; u++) f.set(p, b, u, y, v, wall);
            }
        }
        f.set(p, b, W / 2, 4, D / 2, HANG);
        for (int fl = 1; fl < floors; fl++) f.set(p, b, W / 2, fl * FH + 4, D / 2, HANG);
    }

    // ───────────────────────── 광장 ─────────────────────────

    /** 분수 광장: 해신상(프리즈머린 기둥과 바다 랜턴), 둥근 물그릇, 사방 노점 */
    final class Plaza extends Feature {
        Plaza() {
            super(PX - 28, PZ - 28, PX + 28, PZ + 28);
        }

        @Override
        public void build(Placer p, CityLayout city) {
            p.forColumns(PX - 9, PZ - 9, PX + 9, PZ + 9, (x, z) -> {
                double d = Math.hypot(x - PX, z - PZ);
                if (d <= 7.5) {
                    p.set(x, B, z, Blocks.PRISMARINE_BRICKS.defaultBlockState());
                    if (d > 2.5) water(p, x, B + 1, z);
                } else if (d <= 8.5) {
                    p.set(x, B + 1, z, Blocks.DARK_PRISMARINE.defaultBlockState());
                }
            });
            for (int y = B + 1; y <= B + 7; y++) p.set(PX, y, PZ, y == B + 7 ? Blocks.SEA_LANTERN.defaultBlockState() : Blocks.PRISMARINE_BRICKS.defaultBlockState());
            for (Direction d : Direction.Plane.HORIZONTAL) {
                p.set(PX + d.getStepX(), B + 1, PZ + d.getStepZ(), Blocks.DARK_PRISMARINE.defaultBlockState());
                p.set(PX + d.getStepX(), B + 6, PZ + d.getStepZ(), stairs(Blocks.PRISMARINE_BRICK_STAIRS, d.getOpposite()));
                p.set(PX + d.getStepX() * 2, B + 2, PZ + d.getStepZ() * 2, Blocks.WATER.defaultBlockState());
            }
            p.set(PX, B + 8, PZ, Blocks.LIGHTNING_ROD.defaultBlockState());
            BlockState[] cloth = {Blocks.CYAN_WOOL.defaultBlockState(), Blocks.WHITE_WOOL.defaultBlockState(), Blocks.BLUE_WOOL.defaultBlockState(), Blocks.YELLOW_WOOL.defaultBlockState()};
            Block[] goods = {Blocks.BARREL, Blocks.MELON, Blocks.PUMPKIN, Blocks.HAY_BLOCK, Blocks.DRIED_KELP_BLOCK};
            for (int i = 0; i < 8; i++) {
                double a = Math.PI * 2 * i / 8 + Math.PI / 8;
                int cx = PX + (int) Math.round(Math.cos(a) * 19), cz = PZ + (int) Math.round(Math.sin(a) * 19);
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                    if (Math.abs(dx) == 1 && Math.abs(dz) == 1) for (int y = 1; y <= 2; y++) p.set(cx + dx, B + y, cz + dz, Blocks.SPRUCE_FENCE.defaultBlockState());
                    p.set(cx + dx, B + 3, cz + dz, cloth[i % cloth.length]);
                }
                p.set(cx, B + 1, cz, goods[i % goods.length].defaultBlockState());
            }
            for (int i = 0; i < 4; i++) {                                                     // 광장 등불
                double a = Math.PI / 2 * i;
                int cx = PX + (int) Math.round(Math.cos(a) * 12), cz = PZ + (int) Math.round(Math.sin(a) * 12);
                for (int y = 1; y <= 3; y++) p.set(cx, B + y, cz, Blocks.DARK_OAK_FENCE.defaultBlockState());
                p.set(cx, B + 4, cz, Blocks.LANTERN.defaultBlockState());
            }
        }

        @Override public boolean needsSpawn() { return true; }

        @Override
        public void spawn(ServerLevel level, CityLayout city) {
            String[] names = {"생선 장수", "향신료 상인", "직물 상인", "과일 장수", "항구 경비병", "떠돌이 음유시인"};
            for (int i = 0; i < names.length; i++) {
                double a = Math.PI * 2 * i / names.length;
                villager(level, PX + (int) (Math.cos(a) * 15), B + 1, PZ + (int) (Math.sin(a) * 15), names[i]);
            }
            for (int i = 0; i < 4; i++) {
                double a = Math.PI / 2 * i + 0.4;
                Mob g = EntityType.IRON_GOLEM.spawn(level, new BlockPos(PX + (int) (Math.cos(a) * 24), B + 1, PZ + (int) (Math.sin(a) * 24)), MobSpawnType.STRUCTURE);
                if (g != null) {
                    g.setPersistenceRequired();
                    g.setCustomName(Component.literal("항구 수비대"));
                }
            }
            for (int i = 0; i < 5; i++) {
                Mob c = EntityType.CAT.spawn(level, new BlockPos(PX - 10 + i * 5, B + 1, PZ + 22), MobSpawnType.STRUCTURE);
                if (c != null) c.setPersistenceRequired();
            }
        }
    }

    // ───────────────────────── 집 ─────────────────────────

    final class House extends Feature {
        final Frame f;
        final int W, D, floors;
        final BlockState wall;
        final Block roofStairs, roofSlab;
        final long hseed;

        House(int cx, int cz, Direction front, int W, int D, int floors, Block wall, Block roofStairs, Block roofSlab, long hseed) {
            this(new Frame(cx, cz, front, W, D), W, D, floors, wall, roofStairs, roofSlab, hseed);
        }

        private House(Frame f, int W, int D, int floors, Block wall, Block roofStairs, Block roofSlab, long hseed) {
            super(f.minX - 2, f.minZ - 2, f.maxX + 2, f.maxZ + 2);
            this.f = f;
            this.W = W;
            this.D = D;
            this.floors = floors;
            this.wall = wall.defaultBlockState();
            this.roofStairs = roofStairs;
            this.roofSlab = roofSlab;
            this.hseed = hseed;
        }

        @Override
        public void build(Placer p, CityLayout city) {
            BlockState post = wall.is(Blocks.BRICKS) ? Blocks.STONE_BRICKS.defaultBlockState() : Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState();
            boolean shop = (hseed & 3) == 0;
            body(p, f, B, W, D, floors, wall, post, roofStairs, roofSlab, shop);
            // 살림: 침대·식탁·통·화분
            f.set(p, B, W - 2, 1, D - 2, Palette.bed(f.front, true));
            f.set(p, B, W - 2, 1, D - 3, Palette.bed(f.front, false));
            f.set(p, B, 1, 1, 1, Blocks.BARREL.defaultBlockState());
            f.set(p, B, W - 2, 1, 1, shop ? Blocks.BARREL.defaultBlockState() : Blocks.CRAFTING_TABLE.defaultBlockState());
            if (floors > 1) {
                f.set(p, B, W - 2, 6, D - 2, Palette.bed(f.front, true));
                f.set(p, B, W - 2, 6, D - 3, Palette.bed(f.front, false));
                f.set(p, B, 2, 6, 1, Blocks.BOOKSHELF.defaultBlockState());
            }
            if (shop) {                                                                       // 가게 차양
                for (int u = -1; u <= W; u++) f.set(p, B, u, 4, -1, (u & 1) == 0 ? Blocks.CYAN_WOOL.defaultBlockState() : Blocks.WHITE_WOOL.defaultBlockState());
            }
            for (int u = 1; u < W - 1; u += 3) f.set(p, B, u, 2, -1, Blocks.POTTED_RED_TULIP.defaultBlockState());
            f.set(p, B, W / 2 + 2, 3, -1, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false));
            f.set(p, B, W / 2 + 2, 2, -1, Blocks.DARK_OAK_FENCE.defaultBlockState());
            f.set(p, B, W / 2 + 2, 1, -1, Blocks.DARK_OAK_FENCE.defaultBlockState());
        }

        @Override public boolean needsSpawn() { return (hseed & 1) == 0; }

        @Override
        public void spawn(ServerLevel level, CityLayout city) {
            villager(level, f.x(W / 2, 2), B + 1, f.z(W / 2, 2), null);
        }
    }

    // ───────────────────────── 큰 건물 ─────────────────────────

    /** 항만청: 3층 석조 청사 + 가운데 시계탑(구리 지붕), 앞 계단과 깃발 */
    final class Admiralty extends Feature {
        final int cx, cz;

        Admiralty() {
            this(PX, PZ);
        }

        Admiralty(int cx, int cz) {
            super(cx - 14, cz - 11, cx + 14, cz + 11);
            this.cx = cx;
            this.cz = cz;
        }

        @Override
        public void build(Placer p, CityLayout city) {
            Direction front = PZ > cz ? Direction.SOUTH : Direction.NORTH;
            Frame f = new Frame(cx, cz, front, 25, 19);
            body(p, f, B, 25, 19, 3, Blocks.SMOOTH_SANDSTONE.defaultBlockState(), Blocks.CHISELED_SANDSTONE.defaultBlockState(),
                    Blocks.DARK_PRISMARINE_STAIRS, Blocks.DARK_PRISMARINE_SLAB, false);
            // 시계탑
            int tx = f.x(12, 9), tz = f.z(12, 9);
            for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
                boolean edge = Math.abs(dx) == 3 || Math.abs(dz) == 3;
                for (int y = 16; y <= 34; y++) {
                    BlockState s = edge ? (y % 6 == 0 ? Blocks.CHISELED_SANDSTONE.defaultBlockState() : Blocks.SMOOTH_SANDSTONE.defaultBlockState()) : Blocks.AIR.defaultBlockState();
                    if (edge && (y >= 28 && y <= 31) && (dx == 0 || dz == 0)) s = Blocks.GLASS_PANE.defaultBlockState();
                    p.set(tx + dx, B + y, tz + dz, s);
                }
            }
            for (int k = 0; k <= 4; k++) for (int dx = -4 + k; dx <= 4 - k; dx++) for (int dz = -4 + k; dz <= 4 - k; dz++) {
                if (Math.abs(dx) == 4 - k || Math.abs(dz) == 4 - k) p.set(tx + dx, B + 35 + k, tz + dz, Blocks.WAXED_OXIDIZED_CUT_COPPER.defaultBlockState());
            }
            p.set(tx, B + 40, tz, Blocks.LIGHTNING_ROD.defaultBlockState());
            for (Direction d : Direction.Plane.HORIZONTAL) p.set(tx + d.getStepX() * 3, B + 30, tz + d.getStepZ() * 3, Blocks.TARGET.defaultBlockState());
            p.set(tx, B + 29, tz, Blocks.BELL.defaultBlockState());
            // 앞 깃대
            for (int s : new int[]{-6, 6}) {
                int x = f.x(12 + s, -3), z = f.z(12 + s, -3);
                for (int y = 1; y <= 9; y++) p.set(x, B + y, z, Blocks.DARK_OAK_FENCE.defaultBlockState());
                p.set(x, B + 10, z, Blocks.CYAN_BANNER.defaultBlockState());
            }
            OldCapital.chest(p, f.x(3, 14), B + 1, f.z(3, 14), f.front, cx * 31L + cz, BuiltInLootTables.SHIPWRECK_MAP);
        }

        @Override public boolean needsSpawn() { return true; }

        @Override
        public void spawn(ServerLevel level, CityLayout city) {
            villager(level, cx, B + 1, cz, "항만청장");
            villager(level, cx + 3, B + 1, cz, "해도 제작자");
            villager(level, cx - 3, B + 1, cz, "선원 모집관");
        }
    }

    /** 해신 신전: 프리즈머린 열주 회랑과 돔, 안에 해신상과 제단 */
    final class SeaTemple extends Feature {
        final int cx, cz;

        SeaTemple() {
            this(PX, PZ);
        }

        SeaTemple(int cx, int cz) {
            super(cx - 13, cz - 13, cx + 13, cz + 13);
            this.cx = cx;
            this.cz = cz;
        }

        @Override
        public void build(Placer p, CityLayout city) {
            p.forColumns(cx - 13, cz - 13, cx + 13, cz + 13, (x, z) -> {
                int dx = x - cx, dz = z - cz, m = Math.max(Math.abs(dx), Math.abs(dz));
                p.clearAbove(x, z, B + 1);
                p.foundation(x, z, B - 1, 16);
                p.set(x, B, z, m >= 12 ? Blocks.QUARTZ_BRICKS.defaultBlockState() : (dx + dz) % 2 == 0 ? Blocks.PRISMARINE_BRICKS.defaultBlockState() : Blocks.CALCITE.defaultBlockState());
                if (m == 11 && (Math.floorMod(dx, 3) == 0 && Math.floorMod(dz, 3) == 0)) {          // 열주
                    for (int y = 1; y <= 8; y++) p.set(x, B + y, z, Blocks.QUARTZ_PILLAR.defaultBlockState());
                }
                if (m <= 11) p.set(x, B + 9, z, m == 11 ? Blocks.QUARTZ_BRICKS.defaultBlockState() : Blocks.PRISMARINE_BRICKS.defaultBlockState());
                double d = Math.hypot(dx, dz);                                               // 돔
                for (int y = 10; y <= 20; y++) {
                    double rr = 10 * Math.sqrt(Math.max(0, 1 - Math.pow((y - 10) / 10.0, 2)));
                    if (d <= rr && d > rr - 1.3) p.set(x, B + y, z, y % 4 == 0 ? Blocks.SEA_LANTERN.defaultBlockState() : Blocks.DARK_PRISMARINE.defaultBlockState());
                }
                if (m == 6 && (Math.abs(dx) <= 1 || Math.abs(dz) <= 1)) for (int y = 1; y <= 8; y++) p.set(x, B + y, z, Blocks.PRISMARINE_WALL.defaultBlockState());
            });
            p.set(cx, B + 21, cz, Blocks.CONDUIT.defaultBlockState());
            for (int y = 1; y <= 5; y++) p.set(cx, B + y, cz, y == 5 ? Blocks.SEA_LANTERN.defaultBlockState() : Blocks.DARK_PRISMARINE.defaultBlockState());
            for (Direction d : Direction.Plane.HORIZONTAL) {
                p.set(cx + d.getStepX() * 3, B + 1, cz + d.getStepZ() * 3, Blocks.CANDLE.defaultBlockState());
                p.set(cx + d.getStepX() * 2, B + 1, cz + d.getStepZ() * 2, Blocks.WATER_CAULDRON.defaultBlockState());
            }
        }

        @Override public boolean needsSpawn() { return true; }

        @Override
        public void spawn(ServerLevel level, CityLayout city) {
            villager(level, cx + 4, B + 1, cz, "해신의 사제");
            villager(level, cx - 4, B + 1, cz, "순례자");
        }
    }

    /** 세관과 상인 길드: 항구 큰길 끝, 안벽 바로 앞의 붉은 벽돌 3층 건물 */
    final class Customs extends Feature {
        final int cx, cz;

        Customs(int cx, int cz) {
            super(cx - 9, cz - 9, cx + 9, cz + 9);
            this.cx = cx;
            this.cz = cz;
        }

        @Override
        public void build(Placer p, CityLayout city) {
            Direction front = Math.abs(Math.cos(harborAngle)) > Math.abs(Math.sin(harborAngle))
                    ? (Math.cos(harborAngle) > 0 ? Direction.EAST : Direction.WEST) : (Math.sin(harborAngle) > 0 ? Direction.SOUTH : Direction.NORTH);
            Frame f = new Frame(cx, cz, front, 15, 13);
            body(p, f, B, 15, 13, 3, Blocks.BRICKS.defaultBlockState(), Blocks.STONE_BRICKS.defaultBlockState(), Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILE_SLAB, false);
            for (int u = 2; u <= 12; u += 2) f.set(p, B, u, 1, 9, Blocks.BARREL.defaultBlockState());
            OldCapital.chest(p, f.x(3, 10), B + 1, f.z(3, 10), f.front, cx * 17L + cz, BuiltInLootTables.SHIPWRECK_TREASURE);
            for (int u = 4; u <= 10; u++) f.set(p, B, u, 1, 5, slabTop(Blocks.SPRUCE_SLAB));
        }

        @Override public boolean needsSpawn() { return true; }

        @Override
        public void spawn(ServerLevel level, CityLayout city) {
            villager(level, cx, B + 1, cz, "상인 길드장");
            villager(level, cx + 2, B + 1, cz + 1, "세관 관리");
        }
    }

    /** 갈매기 여관: 서쪽 성문 안, 3층 목조 여관 (객실·바·마구간) */
    final class Inn extends Feature {
        final int cx, cz;

        Inn() {
            this(PX, PZ);
        }

        Inn(int cx, int cz) {
            super(cx - 9, cz - 9, cx + 9, cz + 9);
            this.cx = cx;
            this.cz = cz;
        }

        @Override
        public void build(Placer p, CityLayout city) {
            Direction front = facingStreet(cx, cz);
            Frame f = new Frame(cx, cz, front, 15, 13);
            body(p, f, B, 15, 13, 3, Blocks.SPRUCE_PLANKS.defaultBlockState(), Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState(), Blocks.BRICK_STAIRS, Blocks.BRICK_SLAB, false);
            for (int u = 2; u <= 12; u += 2) f.set(p, B, u, 1, 11, Blocks.BARREL.defaultBlockState());
            for (int u = 3; u <= 11; u++) f.set(p, B, u, 1, 8, slabTop(Blocks.SPRUCE_SLAB));
            for (int u = 2; u <= 12; u += 3) {
                f.set(p, B, u, 6, 2, Palette.bed(f.front, true));
                f.set(p, B, u, 6, 3, Palette.bed(f.front, false));
                f.set(p, B, u, 11, 2, Palette.bed(f.front, true));
                f.set(p, B, u, 11, 3, Palette.bed(f.front, false));
            }
            f.set(p, B, 13, 1, 1, Blocks.SMOKER.defaultBlockState());
            int sx = f.x(7, -2), sz = f.z(7, -2);
            p.set(sx, B + 4, sz, Blocks.SPRUCE_FENCE.defaultBlockState());
            p.set(sx, B + 3, sz, Blocks.WHITE_BANNER.defaultBlockState());
        }

        @Override public boolean needsSpawn() { return true; }

        @Override
        public void spawn(ServerLevel level, CityLayout city) {
            villager(level, cx, B + 1, cz, "갈매기 여관 주인");
            villager(level, cx + 2, B + 1, cz + 2, "술 취한 선원");
            villager(level, cx - 2, B + 1, cz - 2, "떠돌이 모험가");
        }
    }

    /** 창고: 안벽 앞 길쭉한 돌·나무 창고, 큰 문, 안에 통·상자·짐 */
    final class Warehouse extends Feature {
        final int cx, cz;
        final long ws;

        Warehouse(int cx, int cz, long ws) {
            super(cx - 9, cz - 9, cx + 9, cz + 9);
            this.cx = cx;
            this.cz = cz;
            this.ws = ws;
        }

        @Override
        public void build(Placer p, CityLayout city) {
            Direction front = Math.abs(Math.cos(harborAngle)) > Math.abs(Math.sin(harborAngle))
                    ? (Math.cos(harborAngle) > 0 ? Direction.EAST : Direction.WEST) : (Math.sin(harborAngle) > 0 ? Direction.SOUTH : Direction.NORTH);
            Frame f = new Frame(cx, cz, front, 11, 15);
            body(p, f, B, 11, 15, 2, Blocks.COBBLESTONE.defaultBlockState(), Blocks.DARK_OAK_LOG.defaultBlockState(), Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_SLAB, true);
            Random r = new Random(ws);
            Block[] cargo = {Blocks.BARREL, Blocks.HAY_BLOCK, Blocks.DRIED_KELP_BLOCK, Blocks.CHEST, Blocks.SPRUCE_PLANKS, Blocks.WHITE_WOOL};
            for (int u = 1; u < 10; u++) for (int v = 3; v < 14; v++) {
                if (u == 5 || r.nextInt(3) != 0) continue;
                int hgt = 1 + r.nextInt(3);
                for (int y = 1; y <= hgt; y++) f.set(p, B, u, y, v, cargo[r.nextInt(cargo.length)].defaultBlockState());
            }
            for (int y = 6; y <= 9; y++) f.set(p, B, 5, y, -1, Blocks.CHAIN.defaultBlockState());                         // 짐 올리는 도르래
            f.set(p, B, 5, 10, -1, Blocks.DARK_OAK_PLANKS.defaultBlockState());
        }
    }

    /** 어시장: 안벽 위 지붕만 있는 장터 (생선 통·얼음·켈프) */
    final class FishMarket extends Feature {
        final int cx, cz;

        FishMarket(int cx, int cz) {
            super(cx - 8, cz - 6, cx + 8, cz + 6);
            this.cx = cx;
            this.cz = cz;
        }

        @Override
        public void build(Placer p, CityLayout city) {
            p.forColumns(cx - 8, cz - 6, cx + 8, cz + 6, (x, z) -> {
                int dx = x - cx, dz = z - cz;
                p.clearAbove(x, z, B + 1);
                p.set(x, B, z, Blocks.SMOOTH_STONE.defaultBlockState());
                boolean postCol = Math.abs(dx) == 8 && Math.abs(dz) % 3 == 0 || Math.abs(dz) == 6 && Math.abs(dx) % 4 == 0;
                if (postCol) for (int y = 1; y <= 4; y++) p.set(x, B + y, z, Blocks.SPRUCE_LOG.defaultBlockState());
                p.set(x, B + 5, z, (dx + 20) % 2 == 0 ? Blocks.BLUE_WOOL.defaultBlockState() : Blocks.WHITE_WOOL.defaultBlockState());
                if (Math.abs(dz) == 2 && Math.abs(dx) < 7) {
                    int k = h(x, z, 9) % 4;
                    p.set(x, B + 1, z, k == 0 ? Blocks.BLUE_ICE.defaultBlockState() : k == 1 ? Blocks.BARREL.defaultBlockState()
                            : k == 2 ? Blocks.DRIED_KELP_BLOCK.defaultBlockState() : Blocks.PACKED_ICE.defaultBlockState());
                }
            });
            p.set(cx, B + 4, cz, HANG);
        }

        @Override public boolean needsSpawn() { return true; }

        @Override
        public void spawn(ServerLevel level, CityLayout city) {
            villager(level, cx - 3, B + 1, cz, "어시장 경매인");
            villager(level, cx + 3, B + 1, cz, "어부");
        }
    }

    /** 조선소: 비탈 진 선대 위에 짓다 만 배 (용골·늑골), 비계와 목재 더미 */
    final class Shipyard extends Feature {
        final int cx, cz;

        Shipyard(int cx, int cz) {
            super(cx - 16, cz - 8, cx + 16, cz + 8);
            this.cx = cx;
            this.cz = cz;
        }

        @Override
        public void build(Placer p, CityLayout city) {
            p.forColumns(cx - 16, cz - 8, cx + 16, cz + 8, (x, z) -> {
                p.clearAbove(x, z, B + 1);
                p.set(x, B, z, Math.abs(z - cz) <= 3 ? Blocks.SPRUCE_PLANKS.defaultBlockState() : Blocks.GRAVEL.defaultBlockState());
            });
            for (int x = cx - 12; x <= cx + 12; x++) {
                p.set(x, B + 1, cz, Blocks.DARK_OAK_LOG.defaultBlockState().setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS, Direction.Axis.X));
                if ((x - cx) % 3 == 0 && Math.abs(x - cx) < 11) {                                 // 늑골
                    int hb = (int) Math.round(4 * Math.sqrt(1 - Math.pow((x - cx) / 12.0, 2)));
                    for (int dz = -hb; dz <= hb; dz++) {
                        int y = B + 1 + (int) Math.round(Math.pow(Math.abs(dz) / (double) Math.max(1, hb), 2) * 5);
                        p.set(x, y, cz + dz, Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState());
                    }
                }
            }
            for (int x = cx - 12; x <= cx + 12; x += 6) for (int s : new int[]{-6, 6}) {          // 비계
                for (int y = 1; y <= 8; y++) p.set(x, B + y, cz + s, Blocks.SCAFFOLDING.defaultBlockState());
            }
            for (int y = 1; y <= 3; y++) for (int x = cx + 13; x <= cx + 15; x++) p.set(x, B + y, cz - 6, Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState());
        }

        @Override public boolean needsSpawn() { return true; }

        @Override
        public void spawn(ServerLevel level, CityLayout city) {
            villager(level, cx, B + 1, cz + 5, "조선공 우두머리");
            villager(level, cx + 4, B + 1, cz - 5, "목수");
        }
    }

    /** 나무 잔교: 안벽에서 바다로 뻗는 폭 5 판자길, 4칸마다 기둥, 끝에 등불 */
    final class Pier extends Feature {
        final int x0, zc, len;

        Pier(int x0, int zc, int len) {
            super(x0, zc - 3, x0 + len, zc + 3);
            this.x0 = x0;
            this.zc = zc;
            this.len = len;
        }

        @Override
        public void build(Placer p, CityLayout city) {
            for (int x = x0; x <= x0 + len; x++) for (int dz = -2; dz <= 2; dz++) {
                int z = zc + dz;
                if (!p.contains(x, z)) continue;
                p.set(x, B, z, Math.abs(dz) == 2 ? Blocks.SPRUCE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP) : Blocks.SPRUCE_PLANKS.defaultBlockState());
                for (int y = B + 1; y <= B + 3; y++) p.set(x, y, z, Blocks.AIR.defaultBlockState());
                if (Math.abs(dz) == 2 && (x - x0) % 4 == 0) {
                    for (int y = DREDGE - 1; y < B; y++) p.set(x, y, z, Blocks.SPRUCE_LOG.defaultBlockState());
                    p.set(x, B + 1, z, Blocks.SPRUCE_FENCE.defaultBlockState());
                    if ((x - x0) % 12 == 0) p.set(x, B + 2, z, Blocks.LANTERN.defaultBlockState());
                }
            }
            for (int dz : new int[]{-2, 2}) {
                for (int y = 1; y <= 3; y++) p.set(x0 + len, B + y, zc + dz, Blocks.SPRUCE_FENCE.defaultBlockState());
                p.set(x0 + len, B + 4, zc + dz, Blocks.LANTERN.defaultBlockState());
            }
        }

        @Override public boolean needsSpawn() { return true; }

        @Override
        public void spawn(ServerLevel level, CityLayout city) {
            Boat b = new Boat(level, x0 + len - 3 + 0.5, WATER_Y + 1, zc - 4 + 0.5);
            b.setVariant(Boat.Type.OAK);
            level.addFreshEntity(b);
            Boat b2 = new Boat(level, x0 + len - 7 + 0.5, WATER_Y + 1, zc - 4 + 0.5);
            b2.setVariant(Boat.Type.SPRUCE);
            level.addFreshEntity(b2);
        }
    }

    /** 정박한 범선: 짙은 참나무 선체, 가문비 갑판, 돛대 2개와 흰 돛, 선미 선실, 뱃머리 기움돛대 */
    final class Ship extends Feature {
        final int sx, sz, L;
        final long ss;

        Ship(int sx, int sz, int L, long ss) {
            super(sx - L / 2 - 4, sz - 6, sx + L / 2 + 4, sz + 6);
            this.sx = sx;
            this.sz = sz;
            this.L = L;
            this.ss = ss;
        }

        private double halfBeam(int t) {
            double u = (double) t / (L / 2.0);
            if (u > 0) return 4.0 * Math.sqrt(Math.max(0, 1 - Math.pow(u, 3)));                      // 뱃머리 쪽 뾰족
            return 4.0 * Math.sqrt(Math.max(0, 1 - Math.pow(-u, 6)));                               // 선미는 뭉툭
        }

        @Override
        public void build(Placer p, CityLayout city) {
            int deck = WATER_Y + 2;
            for (int t = -L / 2; t <= L / 2; t++) {
                double hb = halfBeam(t);
                for (int dz = -5; dz <= 5; dz++) {
                    int x = sx + t, z = sz + dz;
                    if (!p.contains(x, z)) continue;
                    for (int y = deck - 5; y <= deck; y++) {
                        double w = hb - (deck - y) * 0.55;
                        if (Math.abs(dz) > w + 0.3) continue;
                        boolean shell = Math.abs(dz) > w - 1;
                        BlockState s = y == deck ? Blocks.SPRUCE_PLANKS.defaultBlockState()
                                : shell ? (y == deck - 1 ? Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState() : Blocks.DARK_OAK_PLANKS.defaultBlockState())
                                : Blocks.SPRUCE_PLANKS.defaultBlockState();
                        p.set(x, y, z, s);
                    }
                    if (Math.abs(dz) <= hb + 0.3 && Math.abs(dz) > hb - 1) p.set(x, deck + 1, z, Blocks.DARK_OAK_FENCE.defaultBlockState());
                    else if (Math.abs(dz) <= hb) for (int y = deck + 1; y <= deck + 3; y++) p.set(x, y, z, Blocks.AIR.defaultBlockState());
                }
            }
            // 선미 선실
            for (int t = -L / 2 + 1; t <= -L / 2 + 6; t++) for (int dz = -3; dz <= 3; dz++) {
                boolean edge = t == -L / 2 + 1 || t == -L / 2 + 6 || Math.abs(dz) == 3;
                for (int y = 1; y <= 3; y++) {
                    BlockState s = edge ? (y == 2 && Math.abs(dz) == 1 ? Blocks.GLASS_PANE.defaultBlockState() : Blocks.DARK_OAK_PLANKS.defaultBlockState()) : Blocks.AIR.defaultBlockState();
                    if (t == -L / 2 + 6 && dz == 0 && y <= 2) s = door(Direction.EAST, y == 2);
                    p.set(sx + t, deck + y, sz + dz, s);
                }
                p.set(sx + t, deck + 4, sz + dz, Blocks.DARK_OAK_SLAB.defaultBlockState());
            }
            OldCapital.chest(p, sx - L / 2 + 3, deck + 1, sz + 2, Direction.NORTH, ss, BuiltInLootTables.SHIPWRECK_SUPPLY);
            p.set(sx - L / 2 + 3, deck + 3, sz, HANG);
            // 돛대와 돛
            int[] masts = {sx - 1, sx + L / 4};
            for (int mi = 0; mi < masts.length; mi++) {
                int mx = masts[mi], mh = mi == 0 ? 22 : 17;
                for (int y = 1; y <= mh; y++) p.set(mx, deck + y, sz, Blocks.SPRUCE_LOG.defaultBlockState());
                for (int sail = 0; sail < 2; sail++) {
                    int y0 = deck + 5 + sail * 8, w = sail == 0 ? 5 : 4;
                    for (int dz = -w; dz <= w; dz++) {
                        p.set(mx, y0 + 6, sz + dz, Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState().setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS, Direction.Axis.Z));
                        for (int y = y0; y < y0 + 6; y++) {
                            double bulge = 1 - Math.pow(dz / (double) (w + 1), 2);
                            p.set(mx + (bulge > 0.6 && y > y0 && y < y0 + 5 ? 1 : 0), y, sz + dz, Blocks.WHITE_WOOL.defaultBlockState());
                        }
                    }
                }
                p.set(mx, deck + mh + 1, sz, mi == 0 ? Blocks.CYAN_BANNER.defaultBlockState() : Blocks.WHITE_BANNER.defaultBlockState());
            }
            for (int k = 1; k <= 6; k++) p.set(sx + L / 2 + k, deck + 1 + k / 2, sz, Blocks.SPRUCE_FENCE.defaultBlockState());       // 기움돛대
        }

        @Override public boolean needsSpawn() { return true; }

        @Override
        public void spawn(ServerLevel level, CityLayout city) {
            villager(level, sx - L / 2 + 4, WATER_Y + 4, sz, "선장");
            villager(level, sx + 2, WATER_Y + 3, sz + 1, "갑판장");
        }
    }

    /** 성문탑: 각 성문 양옆의 둥근 탑 + 방파제 끝의 항구 등대 2기 */
    final class GateTowers extends Feature {
        final List<int[]> towers = new ArrayList<>();
        final List<int[]> lights = new ArrayList<>();

        GateTowers() {
            super(PX - MOLE_OUT - 6, PZ - MOLE_OUT - 6, PX + MOLE_OUT + 6, PZ + MOLE_OUT + 6);
            for (double[] g : gates) {
                for (int s : new int[]{-1, 1}) {
                    double a = g[0] + s * 8.0 / R;
                    int x = PX + (int) Math.round(Math.cos(a) * (R - 2)), z = PZ + (int) Math.round(Math.sin(a) * (R - 2));
                    if (!sea(x, z)) towers.add(new int[]{x, z});
                }
            }
            for (int s : new int[]{-1, 1}) {
                double a = MOUTH + s * (MOUTH_HALF + 0.012);
                int x = PX + (int) Math.round(Math.cos(a) * (HARBOR_R + 3)), z = PZ + (int) Math.round(Math.sin(a) * (HARBOR_R + 3));
                lights.add(new int[]{x, z});
            }
        }

        @Override
        public void build(Placer p, CityLayout city) {
            for (int[] t : towers) {
                if (!p.intersects(t[0] - 5, t[1] - 5, t[0] + 5, t[1] + 5)) continue;
                p.forColumns(t[0] - 4, t[1] - 4, t[0] + 4, t[1] + 4, (x, z) -> {
                    double d = Math.hypot(x - t[0], z - t[1]);
                    if (d > 4.3) return;
                    p.foundation(x, z, B - 1, 20);
                    for (int y = B; y <= B + 15; y++) {
                        BlockState s = d > 3.2 ? ((y - B) % 5 == 0 ? Blocks.POLISHED_ANDESITE.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState())
                                : (y == B || y == B + 10 ? Blocks.SPRUCE_PLANKS.defaultBlockState() : Blocks.AIR.defaultBlockState());
                        if (d > 3.2 && (y - B) % 5 == 3 && ((x + z) & 1) == 0) s = Blocks.IRON_BARS.defaultBlockState();
                        p.set(x, y, z, s);
                    }
                    if (d > 3.2 && ((x + z) & 1) == 0) p.set(x, B + 16, z, Blocks.STONE_BRICKS.defaultBlockState());
                });
                p.set(t[0], B + 11, t[1], Blocks.LANTERN.defaultBlockState());
                p.set(t[0], B + 1, t[1], Blocks.LANTERN.defaultBlockState());
            }
            for (int[] t : lights) {
                if (!p.intersects(t[0] - 4, t[1] - 4, t[0] + 4, t[1] + 4)) continue;
                p.forColumns(t[0] - 3, t[1] - 3, t[0] + 3, t[1] + 3, (x, z) -> {
                    double d = Math.hypot(x - t[0], z - t[1]);
                    if (d > 3.3) return;
                    for (int y = DREDGE - 6; y < B; y++) p.set(x, y, z, Blocks.STONE_BRICKS.defaultBlockState());
                    for (int y = B; y <= B + 14; y++) {
                        BlockState s = d > 2.2 ? ((y - B) / 3 % 2 == 0 ? Blocks.WHITE_CONCRETE.defaultBlockState() : Blocks.RED_CONCRETE.defaultBlockState()) : Blocks.AIR.defaultBlockState();
                        p.set(x, y, z, s);
                    }
                    p.set(x, B + 15, z, d > 2.2 ? Blocks.GLASS.defaultBlockState() : Blocks.SEA_LANTERN.defaultBlockState());
                    p.set(x, B + 16, z, Blocks.WAXED_OXIDIZED_CUT_COPPER.defaultBlockState());
                });
                for (int y = B + 1; y < B + 15; y++) p.set(t[0], y, t[1], Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH));
                p.set(t[0], B + 17, t[1], Blocks.LIGHTNING_ROD.defaultBlockState());
            }
        }
    }

    // ───────────────────────── 등대 섬 ─────────────────────────

    private static int ground(int x, int z) {
        return KingdomTerrain.column(x, z).h();
    }

    /** 거대 등대: 높이 60, 흰·붉은 줄무늬 원탑, 위로 갈수록 가늘어짐, 등불실과 발코니, 안쪽 사다리 */
    final class Lighthouse extends Feature {
        final int lx, lz, base;

        Lighthouse(int lx, int lz) {
            super(lx - 10, lz - 10, lx + 10, lz + 10);
            this.lx = lx;
            this.lz = lz;
            this.base = Math.max(ground(lx, lz), KingdomTerrain.SEA + 1);
            groundY = base;
        }

        @Override
        public void build(Placer p, CityLayout city) {
            final int H = 58;
            p.forColumns(lx - 10, lz - 10, lx + 10, lz + 10, (x, z) -> {
                double d = Math.hypot(x - lx, z - lz);
                if (d > 10.3) return;
                p.clearAbove(x, z, base + 1);
                p.set(x, base, z, d > 8.5 ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState());
                p.foundation(x, z, base - 1, 30);
                for (int y = 1; y <= H; y++) {
                    double rr = 7.0 - 3.0 * y / H;
                    BlockState s;
                    if (d <= rr + 0.5 && d > rr - 0.7) {
                        s = (y / 7) % 2 == 0 ? Blocks.WHITE_CONCRETE.defaultBlockState() : Blocks.RED_CONCRETE.defaultBlockState();
                        if (y % 7 == 4 && ((x + z) & 3) == 0) s = Blocks.GLASS_PANE.defaultBlockState();
                        if (y <= 2 && x == lx - (int) Math.round(rr) && Math.abs(z - lz) == 0) s = door(Direction.EAST, y == 2);
                    } else if (d <= rr - 0.7) {
                        s = y % 14 == 0 ? Blocks.SPRUCE_PLANKS.defaultBlockState() : Blocks.AIR.defaultBlockState();
                        if (x == lx && z == lz) s = Blocks.AIR.defaultBlockState();
                    } else continue;
                    p.set(x, base + y, z, s);
                }
                // 발코니 + 등불실 + 지붕
                if (d <= 6.3) p.set(x, base + H + 1, z, Blocks.POLISHED_ANDESITE.defaultBlockState());
                if (d > 5.3 && d <= 6.3) p.set(x, base + H + 2, z, Blocks.IRON_BARS.defaultBlockState());
                for (int y = H + 2; y <= H + 6; y++) {
                    boolean exit = y <= H + 3 && z == lz && x < lx;                                    // 발코니로 나가는 틈
                    if (d <= 3.5 && d > 2.5) p.set(x, base + y, z, exit ? Blocks.AIR.defaultBlockState() : Blocks.GLASS.defaultBlockState());
                    else if (d <= 2.5) p.set(x, base + y, z, d < 0.5 && y >= H + 4 ? Blocks.SEA_LANTERN.defaultBlockState() : Blocks.AIR.defaultBlockState());
                }
                for (int k = 0; k <= 4; k++) if (d <= 4.2 - k && d > 3.0 - k) p.set(x, base + H + 7 + k, z, Blocks.WAXED_OXIDIZED_CUT_COPPER.defaultBlockState());
            });
            p.set(lx, base + H + 6, lz, Blocks.GLOWSTONE.defaultBlockState());
            p.set(lx, base + H + 12, lz, Blocks.LIGHTNING_ROD.defaultBlockState());
            for (int y = 1; y <= H; y++) p.set(lx, base + y, lz, Blocks.STONE_BRICKS.defaultBlockState());      // 사다리 기둥
            for (int y = 1; y <= H + 1; y++) p.set(lx + 1, base + y, lz, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.EAST));
        }
    }

    /** 등대지기 오두막 */
    final class KeeperCottage extends Feature {
        final int cx, cz, base;

        KeeperCottage(int cx, int cz) {
            super(cx - 6, cz - 6, cx + 6, cz + 6);
            this.cx = cx;
            this.cz = cz;
            this.base = Math.max(ground(cx, cz), KingdomTerrain.SEA + 1);
            groundY = base;
        }

        @Override
        public void build(Placer p, CityLayout city) {
            Frame f = new Frame(cx, cz, Direction.EAST, 9, 7);
            body(p, f, base, 9, 7, 1, Blocks.CALCITE.defaultBlockState(), Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState(), Blocks.DARK_PRISMARINE_STAIRS, Blocks.DARK_PRISMARINE_SLAB, false);
            f.set(p, base, 7, 1, 5, Palette.bed(f.front, true));
            f.set(p, base, 7, 1, 4, Palette.bed(f.front, false));
            f.set(p, base, 1, 1, 5, Blocks.FURNACE.defaultBlockState());
            OldCapital.chest(p, f.x(1, 1), base + 1, f.z(1, 1), f.back, cx * 13L + cz, BuiltInLootTables.VILLAGE_FISHER);
        }

        @Override public boolean needsSpawn() { return true; }

        @Override
        public void spawn(ServerLevel level, CityLayout city) {
            villager(level, cx, base + 1, cz, "등대지기");
        }
    }

    /** 섬의 해상신전: 바닷가 언덕 위 프리즈머린 열주 신전, 가운데 계단이 앞바다 아래 성소로 이어짐 */
    final class IslandTemple extends Feature {
        final int cx, cz, base;

        IslandTemple(int cx, int cz) {
            super(cx - 15, cz - 15, cx + 15, cz + 15);
            this.cx = cx;
            this.cz = cz;
            this.base = Math.max(ground(cx, cz), KingdomTerrain.SEA + 2);
            groundY = base;
        }

        @Override
        public void build(Placer p, CityLayout city) {
            p.forColumns(cx - 15, cz - 15, cx + 15, cz + 15, (x, z) -> {
                int dx = x - cx, dz = z - cz, m = Math.max(Math.abs(dx), Math.abs(dz));
                if (m > 14) return;
                p.clearAbove(x, z, base + 1);
                p.set(x, base, z, m >= 13 ? Blocks.PRISMARINE.defaultBlockState() : ((dx + dz) & 1) == 0 ? Blocks.PRISMARINE_BRICKS.defaultBlockState() : Blocks.DARK_PRISMARINE.defaultBlockState());
                for (int y = base - 1; y > base - 30; y--) {
                    BlockState s = p.get(x, y, z);
                    if (!s.isAir() && s.getFluidState().isEmpty() && !s.canBeReplaced()) break;
                    p.set(x, y, z, Blocks.PRISMARINE_BRICKS.defaultBlockState());
                }
                if (m == 12 && Math.floorMod(dx, 4) == 0 && Math.floorMod(dz, 4) == 0) {
                    for (int y = 1; y <= 9; y++) p.set(x, base + y, z, y == 9 ? Blocks.SEA_LANTERN.defaultBlockState() : Blocks.PRISMARINE_WALL.defaultBlockState());
                }
                if (m <= 12) p.set(x, base + 10, z, m == 12 ? Blocks.DARK_PRISMARINE.defaultBlockState() : m % 3 == 0 ? Blocks.SEA_LANTERN.defaultBlockState() : Blocks.PRISMARINE_BRICKS.defaultBlockState());
                if (m == 12 && dz == 0 && dx < 0) for (int y = 1; y <= 4; y++) p.set(x, base + y, z, Blocks.AIR.defaultBlockState());
            });
            // 성소로 내려가는 수직 통로 (서쪽, 물로 참)
            for (int y = base; y >= sanctumFloor + 2; y--) {
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                    int x = cx - 6 + dx, z = cz + dz;
                    boolean rim = Math.abs(dx) == 1 || Math.abs(dz) == 1;
                    p.set(x, y, z, rim ? Blocks.DARK_PRISMARINE.defaultBlockState() : Blocks.WATER.defaultBlockState());
                }
            }
            for (int y = base; y >= sanctumFloor + 2; y -= 4) p.set(cx - 6, y, cz - 1, Blocks.SEA_LANTERN.defaultBlockState());
            // 해신상
            for (int y = 1; y <= 6; y++) p.set(cx + 4, base + y, cz, y == 6 ? Blocks.SEA_LANTERN.defaultBlockState() : Blocks.PRISMARINE_BRICKS.defaultBlockState());
            p.set(cx + 4, base + 7, cz, Blocks.CONDUIT.defaultBlockState());
        }
    }

    /** 잠긴 성소: 섬 서쪽 앞바다 바닥의 돔 (안은 물), 보스 심해의 수호자·가디언 스포너·보물 */
    final class Sanctum extends Feature {
        static final int RAD = 14;
        final int cx, cz, FLOOR;

        Sanctum(int cx, int cz) {
            super(cx - RAD - 1, cz - RAD - 1, IX - 120, cz + RAD + 1);
            this.cx = cx;
            this.cz = cz;
            this.FLOOR = sanctumFloor;
        }

        @Override
        public void build(Placer p, CityLayout city) {
            p.forColumns(cx - RAD - 1, cz - RAD - 1, cx + RAD + 1, cz + RAD + 1, (x, z) -> {
                double d = Math.hypot(x - cx, z - cz);
                if (d > RAD + 0.5) return;
                for (int y = FLOOR - 3; y <= FLOOR; y++) p.set(x, y, z, Blocks.DARK_PRISMARINE.defaultBlockState());
                if (d < 4) p.set(x, FLOOR, z, Blocks.SEA_LANTERN.defaultBlockState());
                for (int y = 1; y <= RAD; y++) {
                    double rr = RAD * Math.sqrt(Math.max(0, 1 - Math.pow(y / (double) RAD, 2)));
                    if (d <= rr && d > rr - 1.4) {
                        BlockState s = (y % 5 == 0) ? Blocks.SEA_LANTERN.defaultBlockState() : ((x + y + z) % 3 == 0 ? Blocks.PRISMARINE.defaultBlockState() : Blocks.DARK_PRISMARINE.defaultBlockState());
                        boolean door = y >= 6 && y <= 9 && Math.abs(z - cz) <= 1 && x < cx;        // 서쪽 아치 입구 (바닥 모래 위)
                        p.set(x, FLOOR + y, z, door ? Blocks.WATER.defaultBlockState() : s);
                    } else if (d <= rr - 1.4) {
                        p.set(x, FLOOR + y, z, Blocks.WATER.defaultBlockState());
                    }
                }
            });
            // 섬 신전 통로와 잇는 바닥 굴 (동쪽)
            int tx = IX - 126;
            for (int x = cx + RAD - 2; x <= tx + 1; x++) for (int dz = -2; dz <= 2; dz++) for (int y = FLOOR - 1; y <= FLOOR + 4; y++) {
                boolean shell = Math.abs(dz) == 2 || y == FLOOR - 1 || y == FLOOR + 4;
                if (x <= cx + RAD - 2 + 1 && !shell) continue;
                if (Math.abs(x - tx) <= 1 && Math.abs(dz) <= 1 && y >= FLOOR) {                    // 신전 통로 바닥과 이어지는 구멍
                    p.set(x, y, cz + dz, Blocks.WATER.defaultBlockState());
                    continue;
                }
                p.set(x, y, cz + dz, shell ? ((x & 7) == 0 && y == FLOOR + 4 ? Blocks.SEA_LANTERN.defaultBlockState() : Blocks.DARK_PRISMARINE.defaultBlockState()) : Blocks.WATER.defaultBlockState());
            }
            // 보물·스포너
            OldCapital.chest(p, cx, FLOOR + 1, cz - 3, Direction.SOUTH, cx * 7L + cz, BuiltInLootTables.BURIED_TREASURE);
            OldCapital.chest(p, cx, FLOOR + 1, cz + 3, Direction.NORTH, cx * 11L + cz, BuiltInLootTables.UNDERWATER_RUIN_BIG);
            OldCapital.spawner(p, cx - 7, FLOOR + 1, cz + 6, EntityType.GUARDIAN);
            OldCapital.spawner(p, cx - 7, FLOOR + 1, cz - 6, EntityType.GUARDIAN);
            OldCapital.spawner(p, cx + 6, FLOOR + 1, cz, EntityType.DROWNED);
            p.set(cx, FLOOR + 1, cz, Blocks.GOLD_BLOCK.defaultBlockState());
            p.set(cx, FLOOR + 2, cz, Blocks.CONDUIT.defaultBlockState());
        }

        /** 보스(심해의 수호자)는 이제 boss.NewBosses.DeepGuardian 이 다가가면 깨움 */
        @Override public boolean needsSpawn() { return false; }

        @Override
        public long anchorChunk() {
            return ChunkPos.asLong(cx >> 4, cz >> 4);
        }

        @Override
        public void spawn(ServerLevel level, CityLayout city) {
            Mob boss = EntityType.ELDER_GUARDIAN.spawn(level, new BlockPos(cx, FLOOR + 6, cz), MobSpawnType.STRUCTURE);
            if (boss != null) {
                boss.setPersistenceRequired();
                boss.setCustomName(Component.literal("심해의 수호자"));
                boss.setCustomNameVisible(true);
            }
        }
    }

    /** 섬 잔교: 섬 서쪽에서 항구 쪽으로 뻗은 나무 잔교와 보트 */
    final class IslandPier extends Feature {
        final int x1, zc, x0;

        IslandPier(int ix, int iz) {
            super(ix - ISLE_R, iz + 26, ix, iz + 34);
            int zc0 = iz + 30, start = ix;
            while (start > ix - ISLE_R && !sea(start, zc0)) start--;
            this.zc = zc0;
            this.x1 = start + 2;
            this.x0 = Math.max(ix - ISLE_R + 2, start - 30);
        }

        @Override
        public void build(Placer p, CityLayout city) {
            for (int x = x0; x <= x1; x++) for (int dz = -2; dz <= 2; dz++) {
                int z = zc + dz;
                if (!p.contains(x, z)) continue;
                p.set(x, WATER_Y + 3, z, Blocks.SPRUCE_PLANKS.defaultBlockState());
                if (Math.abs(dz) == 2 && (x - x0) % 4 == 0) {
                    for (int y = WATER_Y - 8; y < WATER_Y + 3; y++) {
                        BlockState s = p.get(x, y, z);
                        if (s.isAir() || s.is(Blocks.WATER) || s.canBeReplaced()) p.set(x, y, z, Blocks.SPRUCE_LOG.defaultBlockState());
                    }
                    p.set(x, WATER_Y + 4, z, Blocks.SPRUCE_FENCE.defaultBlockState());
                    if ((x - x0) % 12 == 0) p.set(x, WATER_Y + 5, z, Blocks.LANTERN.defaultBlockState());
                }
            }
        }

        @Override public boolean needsSpawn() { return true; }

        @Override
        public long anchorChunk() {
            return ChunkPos.asLong(x0 >> 4, zc >> 4);
        }

        @Override
        public void spawn(ServerLevel level, CityLayout city) {
            Boat b = new Boat(level, x0 + 3.5, WATER_Y + 1, zc - 4 + 0.5);
            b.setVariant(Boat.Type.OAK);
            level.addFreshEntity(b);
        }
    }

    // ───────────────────────── 외부에서 쓰는 위치 정보 ─────────────────────────

    /** 항구도시 성벽 안 (길을 깔지 않는 곳) */
    public static boolean inCity(double x, double z) {
        return Math.hypot(x - PX, z - PZ) < R - 1;
    }

    /** 항구도시·방파제·등대 섬 근처 (몬스터 출몰 없음·길 고치기 제외) */
    public static boolean near(double x, double z) {
        return Math.hypot(x - PX, z - PZ) < MOLE_OUT + 20 || Math.hypot(x - IX, z - IZ) < ISLE_R;
    }
}
