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
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 남부 국경도시 "로젠하르트" (마스터플랜 V2) — 남부 왕도의 끝, 남서 국경 군용로가 시작되는 별 모양 요새 도시
 *
 *  - 별 모양 성벽(능보 6개, 높이 12, 위 걷는 길과 총안)과 바깥 물 해자, 길이 들어오는 곳마다 문루와 도개교
 *  - 능보 끝마다 망루와 대포 2문
 *  - 가운데 열병 광장(기사상·노점), 북쪽 성채(네 모서리 탑과 높은 본성), 병영 3동, 훈련장(과녁·허수아비·무기 걸이),
 *    마구간(말), 무기고와 대장간, 종탑 예배당, 곡물 창고, 여관 "국경의 등불", 병사 가족의 집들
 *  - 수비대장·병사·교관·대장장이·마부·사제, 수비 골렘
 */
public final class BorderCity implements BuildLayout {
    public static final int VX = KingdomTerrain.worldX(-200), VZ = KingdomTerrain.worldZ(1670), B = 67;
    private static final int CURTAIN = 92, POINT = 122, WALL_T = 5, MOAT = 10, WALL_H = 12;
    private static final double BASTION_HALF = Math.toRadians(19);

    private final long seed;
    private final List<Feature> features = new ArrayList<>();
    private final Map<Long, List<Feature>> byChunk = new HashMap<>();
    private final List<int[]> taken = new ArrayList<>();
    private final List<int[]> lit = new ArrayList<>();
    private final List<Double> gates = new ArrayList<>();
    private final List<ChunkPos> chunks = new ArrayList<>();
    private final double theta0;

    public BorderCity(long seed) {
        this.seed = seed;
        // 성문: 성벽 바깥(반지름 145)에서 대륙 길이 지나는 각도
        List<double[]> groups = new ArrayList<>();
        for (int i = 0; i < 1440; i++) {
            double a = i * Math.PI * 2 / 1440;
            int x = VX + (int) Math.round(Math.cos(a) * 145), z = VZ + (int) Math.round(Math.sin(a) * 145);
            if (KingdomRoads.nearest(x, z, 0.5, null) < 0) continue;
            if (!groups.isEmpty() && a - groups.get(groups.size() - 1)[1] < 0.06) groups.get(groups.size() - 1)[1] = a;
            else groups.add(new double[]{a, a});
        }
        for (double[] g : groups) gates.add((g[0] + g[1]) / 2);
        if (gates.isEmpty()) gates.add(-Math.PI / 2);
        // 성문은 능보 사이(성벽 가운데)에 오도록 별을 돌림
        theta0 = gates.get(0) + Math.PI / 6;
        // 성문 각도를 실제 길이 해자 바깥 둑에 닿는 지점으로 맞춤 (길이 도시 중심을 정확히 지나지 않아도 큰길·다리·길이 한 줄로 이어지게)
        for (int gi = 0; gi < gates.size(); gi++) {
            double g = gates.get(gi), best = 1e9, ba = g;
            for (KingdomRoads.Road road : KingdomRoads.ROADS) {
                for (double[] q : KingdomRoads.centerline(road, 1)) {
                    double qa = Math.atan2(q[1] - VZ, q[0] - VX);
                    if (Math.abs(Math.atan2(Math.sin(qa - g), Math.cos(qa - g))) > 0.35) continue;
                    double off = Math.hypot(q[0] - VX, q[1] - VZ) - wallR(qa) - (MOAT + 5);
                    if (Math.abs(off) < best) {
                        best = Math.abs(off);
                        ba = qa;
                    }
                }
            }
            if (best < 4) gates.set(gi, ba);
        }

        Random rand = new Random(seed);
        features.add(new Plaza());
        take(VX - 22, VZ - 22, VX + 22, VZ + 22);
        addFixed(new Citadel(VX, VZ - 52));
        int[][] barracks = {{VX - 52, VZ + 30}, {VX - 52, VZ + 48}, {VX - 30, VZ + 62}};
        for (int[] b : barracks) addFixed(new Building(b[0], b[1], Direction.NORTH, 23, 9, 2, Blocks.STONE_BRICKS, Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILE_SLAB, Kind.BARRACKS, rand.nextLong()));
        addFixed(new TrainingYard(VX + 42, VZ + 42));
        addFixed(new Building(VX - 60, VZ - 6, Direction.EAST, 15, 11, 1, Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_SLAB, Kind.STABLE, rand.nextLong()));
        addFixed(new Building(VX + 52, VZ - 4, Direction.WEST, 13, 11, 2, Blocks.COBBLESTONE, Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILE_SLAB, Kind.ARMORY, rand.nextLong()));
        addFixed(new Building(VX + 52, VZ + 14, Direction.WEST, 11, 9, 1, Blocks.STONE_BRICKS, Blocks.BRICK_STAIRS, Blocks.BRICK_SLAB, Kind.SMITHY, rand.nextLong()));
        addFixed(new Building(VX + 30, VZ - 40, Direction.SOUTH, 11, 17, 1, Blocks.CALCITE, Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILE_SLAB, Kind.CHAPEL, rand.nextLong()));
        addFixed(new Building(VX - 32, VZ - 40, Direction.SOUTH, 13, 11, 2, Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_SLAB, Kind.GRANARY, rand.nextLong()));
        // 여관: 첫 성문 안쪽
        double ga = gates.get(0);
        int ix = VX + (int) Math.round(Math.cos(ga) * 58 - Math.sin(ga) * 14), iz = VZ + (int) Math.round(Math.sin(ga) * 58 + Math.cos(ga) * 14);
        if (free(ix - 9, iz - 9, ix + 9, iz + 9)) addFixed(new Building(ix, iz, facingStreet(ix, iz), 15, 13, 3, Blocks.SPRUCE_PLANKS, Blocks.BRICK_STAIRS, Blocks.BRICK_SLAB, Kind.INN, rand.nextLong()));
        // 집
        Block[] walls = {Blocks.STONE_BRICKS, Blocks.SPRUCE_PLANKS, Blocks.CALCITE, Blocks.COBBLESTONE, Blocks.BRICKS};
        Block[][] roofs = {{Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILE_SLAB}, {Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_SLAB}, {Blocks.BRICK_STAIRS, Blocks.BRICK_SLAB}};
        int houses = 0;
        for (int t = 0; t < 6000 && houses < 60; t++) {
            double a = rand.nextDouble() * Math.PI * 2, r = 26 + rand.nextDouble() * 58;
            int x = VX + (int) Math.round(Math.cos(a) * r), z = VZ + (int) Math.round(Math.sin(a) * r);
            int W = 7 + rand.nextInt(2) * 2, D = 7 + rand.nextInt(2) * 2;
            Direction front = facingStreet(x, z);
            boolean ns = front.getAxis() == Direction.Axis.Z;
            int sx = ns ? W : D, sz = ns ? D : W;
            int x0 = x - sx / 2, z0 = z - sz / 2, x1 = x0 + sx - 1, z1 = z0 + sz - 1;
            if (!free(x0 - 2, z0 - 2, x1 + 2, z1 + 2) || inner(x0, z0) > -10 || inner(x1, z1) > -10 || inner(x0, z1) > -10 || inner(x1, z0) > -10) continue;
            Block[] roof = roofs[rand.nextInt(roofs.length)];
            addFixed(new Building(x, z, front, W, D, 2, walls[rand.nextInt(walls.length)], roof[0], roof[1], Kind.HOUSE, rand.nextLong()));
            houses++;
        }
        features.add(new Bastions());
        features.add(new Gatehouses());
        for (Feature f : features) {
            for (int x = (f.minX - 1) >> 4; x <= (f.maxX + 1) >> 4; x++)
                for (int z = (f.minZ - 1) >> 4; z <= (f.maxZ + 1) >> 4; z++)
                    byChunk.computeIfAbsent(ChunkPos.asLong(x, z), k -> new ArrayList<>()).add(f);
        }
        LinkedHashSet<Long> keys = new LinkedHashSet<>();
        int e = POINT + MOAT + 8;
        for (int x = (VX - e) >> 4; x <= (VX + e) >> 4; x++) for (int z = (VZ - e) >> 4; z <= (VZ + e) >> 4; z++) keys.add(ChunkPos.asLong(x, z));
        keys.addAll(byChunk.keySet());
        for (long k : keys) chunks.add(new ChunkPos(k));
    }

    public static boolean near(double x, double z) {
        return Math.hypot(x - VX, z - VZ) < POINT + MOAT + 24;
    }

    public int featureCount() {
        return features.size();
    }

    // ───────────────────────── 모양 ─────────────────────────

    /** 이 각도에서 성벽 바깥면까지의 반지름 (능보 6개의 별) */
    private double wallR(double ang) {
        double rel = Math.floorMod((long) Math.floor(Math.toDegrees(ang - theta0) * 1000), 60000) / 1000.0;
        double d = Math.toRadians(Math.min(rel, 60 - rel));
        return CURTAIN + (POINT - CURTAIN) * Math.max(0, 1 - d / BASTION_HALF);
    }

    /** 성벽 안쪽면까지 남은 거리 (음수 = 성 안) */
    private double inner(int x, int z) {
        double ang = Math.atan2(z - VZ, x - VX);
        return Math.hypot(x - VX, z - VZ) - (wallR(ang) - WALL_T);
    }

    private static boolean radial(int dx, int dz, double a, double half) {
        double along = dx * Math.cos(a) + dz * Math.sin(a), lat = -dx * Math.sin(a) + dz * Math.cos(a);
        return along > 0 && Math.abs(lat) <= half;
    }

    private boolean gateAt(int x, int z, double half) {
        for (double g : gates) if (radial(x - VX, z - VZ, g, half)) return true;
        return false;
    }

    /** 0 = 거리 아님, 1 = 큰길, 2 = 고리 길, 3 = 광장 */
    private int street(int x, int z) {
        int dx = x - VX, dz = z - VZ;
        double r = Math.hypot(dx, dz);
        if (r <= 20) return 3;
        if (inner(x, z) > -1) return 0;
        if (gateAt(x, z, 3.5)) return 1;
        if (radial(dx, dz, -Math.PI / 2, 3.5) && r < 42) return 1;                              // 광장 → 성채
        if (Math.abs(r - 34) <= 2 || Math.abs(r - 74) <= 2) return 2;
        for (int k = 0; k < 6; k++) if (radial(dx, dz, theta0 + Math.PI / 3 * k, 2) && r > 20) return 2;
        return 0;
    }

    private void take(int x0, int z0, int x1, int z1) {
        taken.add(new int[]{x0, z0, x1, z1});
    }

    private boolean free(int x0, int z0, int x1, int z1) {
        for (int[] q : taken) if (x0 <= q[2] && x1 >= q[0] && z0 <= q[3] && z1 >= q[1]) return false;
        for (int x = x0; x <= x1; x += 2) for (int z = z0; z <= z1; z += 2) if (street(x, z) > 0) return false;
        return street(x1, z1) == 0;
    }

    private void addFixed(Feature f) {
        features.add(f);
        take(f.minX, f.minZ, f.maxX, f.maxZ);
        lit.add(new int[]{f.minX, f.minZ, f.maxX, f.maxZ, B, B + 32});
    }

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

    private int h(int x, int y, int z) {
        return Shapes.hash(x, y, z, seed);
    }

    @Override public int cx() { return VX; }
    @Override public int cz() { return VZ; }
    @Override public int base() { return B; }
    @Override public int radius() { return 0; }
    @Override public CityLayout city() { return null; }
    @Override public List<ChunkPos> chunkList() { return chunks; }
    @Override public List<Feature> featuresIn(ChunkPos cp) { return byChunk.getOrDefault(cp.toLong(), Collections.emptyList()); }
    @Override public int shapeMinY() { return B - 12; }
    @Override public int shapeMaxY() { return B + 50; }

    @Override
    public int[] lightRange(int x, int z) {
        for (int[] q : lit) if (x >= q[0] && x <= q[2] && z >= q[1] && z <= q[3]) return new int[]{q[4], q[5]};
        return null;
    }

    // ───────────────────────── 기둥: 땅·거리·성벽·해자 ─────────────────────────

    @Override
    public void buildColumn(Placer p, int x, int z) {
        double ang = Math.atan2(z - VZ, x - VX);
        double rho = Math.hypot(x - VX, z - VZ), wr = wallR(ang);
        int hh = h(x, 1, z);
        if (rho > wr + MOAT + 6) return;
        boolean gate = gateAt(x, z, 3.5);
        if (rho > wr + MOAT) {                                                                // 해자 바깥 둑
            p.clearAbove(x, z, B + 1);
            p.set(x, B, z, gate ? Blocks.STONE_BRICKS.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState());
            p.foundation(x, z, B - 1, 12);
            return;
        }
        if (rho > wr) {                                                                       // 물 해자 (성문 앞은 도개교)
            p.clearAbove(x, z, B + 1);
            if (gate) {
                p.set(x, B, z, Blocks.SPRUCE_PLANKS.defaultBlockState());
                for (int y = B - 5; y < B; y++) p.set(x, y, z, Blocks.WATER.defaultBlockState());
                if (gateAt(x, z, 3.5) && !gateAt(x, z, 2.5)) p.set(x, B + 1, z, Blocks.CHAIN.defaultBlockState());
            } else {
                p.set(x, B, z, Blocks.AIR.defaultBlockState());
                for (int y = B - 5; y < B; y++) p.set(x, y, z, Blocks.WATER.defaultBlockState());
            }
            p.set(x, B - 6, z, Blocks.MUD.defaultBlockState());
            if (rho > wr + MOAT - 1 || rho < wr + 1) for (int y = B - 6; y <= B; y++) if (!gate) p.set(x, y, z, Blocks.STONE_BRICKS.defaultBlockState());
            if (!gate && rho > wr + MOAT - 1) p.set(x, B, z, Blocks.STONE_BRICKS.defaultBlockState());
            return;
        }
        if (rho > wr - WALL_T) {                                                              // 성벽
            p.clearAbove(x, z, B + 1);
            p.foundation(x, z, B - 1, 16);
            for (int y = B - 6; y <= B + WALL_H; y++) {
                BlockState s = (y - B) % 6 == 0 ? Blocks.POLISHED_ANDESITE.defaultBlockState()
                        : h(x, y, z) % 7 == 0 ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState();
                if (gate && y > B && y <= B + 6) s = Blocks.AIR.defaultBlockState();
                if (gate && y == B + 7 && !gateAt(x, z, 2.5)) s = Blocks.CHISELED_STONE_BRICKS.defaultBlockState();
                p.set(x, y, z, s);
            }
            if (rho > wr - 1) {                                                               // 바깥 총안
                if (((int) Math.round(ang * rho)) % 2 == 0) p.set(x, B + WALL_H + 1, z, Blocks.STONE_BRICKS.defaultBlockState());
            } else if (rho < wr - WALL_T + 1 && hh % 31 == 0) {
                p.set(x, B + WALL_H + 1, z, Blocks.DARK_OAK_FENCE.defaultBlockState());
                p.set(x, B + WALL_H + 2, z, Blocks.LANTERN.defaultBlockState());
            }
            return;
        }
        // 성 안
        p.clearAbove(x, z, B + 1);
        p.foundation(x, z, B - 1, 14);
        int st = street(x, z);
        BlockState s;
        if (st == 3) {
            int ring = (int) rho;
            s = ring % 5 == 0 ? Blocks.POLISHED_ANDESITE.defaultBlockState() : ring % 2 == 0 ? Blocks.STONE_BRICKS.defaultBlockState() : Blocks.ANDESITE.defaultBlockState();
        } else if (st == 1) {
            s = hh % 5 == 0 ? Blocks.COBBLESTONE.defaultBlockState() : hh % 3 == 0 ? Blocks.POLISHED_ANDESITE.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState();
        } else if (st == 2) {
            s = hh % 3 == 0 ? Blocks.GRAVEL.defaultBlockState() : hh % 4 == 0 ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState();
        } else if (inner(x, z) > -5) {
            s = Blocks.DIRT_PATH.defaultBlockState();                                         // 성벽 안쪽 순찰로
        } else {
            s = hh % 11 == 0 ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState();
        }
        p.set(x, B, z, s);
        if (st > 0 && st < 3 && hh % 41 == 0 && street(x + 2, z) == 0) {
            for (int y = 1; y <= 3; y++) p.set(x, B + y, z, Blocks.DARK_OAK_FENCE.defaultBlockState());
            p.set(x, B + 4, z, Blocks.LANTERN.defaultBlockState());
        } else if (st == 0 && s.is(Blocks.GRASS_BLOCK) && hh % 23 == 0) {
            p.set(x, B + 1, z, Blocks.SHORT_GRASS.defaultBlockState());
        }
        // 성벽 안쪽 계단 (능보마다 하나, 걷는 길로)
        double rel = Math.floorMod((long) Math.floor(Math.toDegrees(ang - theta0) * 1000), 60000) / 1000.0;
        double inn = inner(x, z);
        if (Math.abs(rel - 30) > 20 && inn > -WALL_H - 1 && inn <= 0) {
            int step = (int) Math.round(WALL_H + inn);
            if (step >= 1 && Math.abs(rel - (rel < 30 ? 6 : 54)) < 1.3) for (int y = B + 1; y <= B + step; y++) p.set(x, y, z, Blocks.STONE_BRICKS.defaultBlockState());
        }
    }

    // ───────────────────────── 시설 ─────────────────────────

    /** 열병 광장: 기사상, 깃대, 노점 */
    final class Plaza extends Feature {
        Plaza() {
            super(VX - 20, VZ - 20, VX + 20, VZ + 20);
        }

        @Override
        public void build(Placer p, CityLayout city) {
            // 기사상 (받침대 + 갑옷 입은 기사 모양 돌 조각)
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                p.set(VX + dx, B + 1, VZ + dz, Blocks.POLISHED_ANDESITE.defaultBlockState());
                if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) p.set(VX + dx, B + 2, VZ + dz, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
            }
            p.set(VX, B + 3, VZ, Blocks.IRON_BLOCK.defaultBlockState());
            p.set(VX, B + 4, VZ, Blocks.IRON_BLOCK.defaultBlockState());
            p.set(VX - 1, B + 4, VZ, Blocks.IRON_BARS.defaultBlockState());
            p.set(VX + 1, B + 4, VZ, Blocks.IRON_BARS.defaultBlockState());
            p.set(VX, B + 5, VZ, Blocks.CARVED_PUMPKIN.defaultBlockState());
            for (int y = B + 3; y <= B + 7; y++) p.set(VX + 1, y, VZ - 1, Blocks.LIGHTNING_ROD.defaultBlockState());   // 창
            for (int s : new int[]{-12, 12}) {
                for (int y = 1; y <= 10; y++) p.set(VX + s, B + y, VZ - 12, Blocks.DARK_OAK_FENCE.defaultBlockState());
                p.set(VX + s, B + 11, VZ - 12, Blocks.RED_BANNER.defaultBlockState());
            }
            BlockState[] cloth = {Blocks.RED_WOOL.defaultBlockState(), Blocks.WHITE_WOOL.defaultBlockState()};
            for (int i = 0; i < 6; i++) {
                double a = Math.PI * 2 * i / 6 + Math.PI / 6;
                int cx = VX + (int) Math.round(Math.cos(a) * 15), cz = VZ + (int) Math.round(Math.sin(a) * 15);
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                    if (Math.abs(dx) == 1 && Math.abs(dz) == 1) for (int y = 1; y <= 2; y++) p.set(cx + dx, B + y, cz + dz, Blocks.SPRUCE_FENCE.defaultBlockState());
                    p.set(cx + dx, B + 3, cz + dz, cloth[i & 1]);
                }
                p.set(cx, B + 1, cz, (i % 3 == 0 ? Blocks.BARREL : i % 3 == 1 ? Blocks.HAY_BLOCK : Blocks.PUMPKIN).defaultBlockState());
            }
        }

        @Override public boolean needsSpawn() { return true; }

        @Override
        public void spawn(ServerLevel level, CityLayout city) {
            String[] names = {"병사", "병사", "보급 상인", "말 상인", "전령", "순찰병"};
            for (int i = 0; i < names.length; i++) {
                double a = Math.PI * 2 * i / names.length;
                PortCity.villager(level, VX + (int) (Math.cos(a) * 10), B + 1, VZ + (int) (Math.sin(a) * 10), names[i]);
            }
            for (int i = 0; i < 6; i++) {
                double a = Math.PI * 2 * i / 6;
                Mob g = EntityType.IRON_GOLEM.spawn(level, new BlockPos(VX + (int) (Math.cos(a) * 60), B + 1, VZ + (int) (Math.sin(a) * 60)), MobSpawnType.STRUCTURE);
                if (g != null) {
                    g.setPersistenceRequired();
                    g.setCustomName(Component.literal("국경 수비대"));
                }
            }
        }
    }

    /** 성채: 모서리 탑 네 개를 거느린 사각 성곽과 가운데 높은 본성 */
    final class Citadel extends Feature {
        final int cx, cz;

        Citadel(int cx, int cz) {
            super(cx - 15, cz - 15, cx + 15, cz + 15);
            this.cx = cx;
            this.cz = cz;
        }

        @Override
        public void build(Placer p, CityLayout city) {
            p.forColumns(cx - 14, cz - 14, cx + 14, cz + 14, (x, z) -> {
                int dx = x - cx, dz = z - cz, m = Math.max(Math.abs(dx), Math.abs(dz));
                p.clearAbove(x, z, B + 1);
                p.set(x, B, z, m <= 6 ? Blocks.SPRUCE_PLANKS.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState());
                p.foundation(x, z, B - 1, 14);
                if (m == 14) {                                                                // 바깥 성곽
                    for (int y = 1; y <= 8; y++) p.set(x, B + y, z, Blocks.STONE_BRICKS.defaultBlockState());
                    if (((x + z) & 1) == 0) p.set(x, B + 9, z, Blocks.STONE_BRICKS.defaultBlockState());
                    if (dz == 14 && Math.abs(dx) <= 2) for (int y = 1; y <= 5; y++) p.set(x, B + y, z, Blocks.AIR.defaultBlockState());
                }
                if (m <= 6) {                                                                 // 본성 (높이 26)
                    boolean wall = m == 6;
                    for (int y = 1; y <= 26; y++) {
                        BlockState s = wall ? (y % 7 == 0 ? Blocks.POLISHED_ANDESITE.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState())
                                : (y % 7 == 0 ? Blocks.SPRUCE_PLANKS.defaultBlockState() : Blocks.AIR.defaultBlockState());
                        if (wall && y % 7 == 3 && (dx == 0 || dz == 0)) s = Blocks.GLASS_PANE.defaultBlockState();
                        if (wall && dz == 6 && Math.abs(dx) <= 1 && y <= 3) s = Blocks.AIR.defaultBlockState();
                        if (!wall && dx == -5 && dz == -5 && y < 26) s = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH);
                        p.set(x, B + y, z, s);
                    }
                    if (wall && ((x + z) & 1) == 0) p.set(x, B + 27, z, Blocks.STONE_BRICKS.defaultBlockState());
                    if (!wall) p.set(x, B + 26, z, Blocks.SPRUCE_PLANKS.defaultBlockState());
                }
            });
            p.set(cx - 5, B + 26, cz - 5, Blocks.AIR.defaultBlockState());
            for (int[] c : new int[][]{{-14, -14}, {14, -14}, {-14, 14}, {14, 14}}) tower(p, cx + c[0], cz + c[1], 3.5, 14);
            for (int y = 27; y <= 34; y++) p.set(cx, B + y, cz, Blocks.DARK_OAK_FENCE.defaultBlockState());
            p.set(cx, B + 35, cz, Blocks.RED_BANNER.defaultBlockState());
            p.set(cx, B + 4, cz, PortCity.HANG);
            p.set(cx, B + 11, cz, PortCity.HANG);
            OldCapital.chest(p, cx + 4, B + 1, cz - 4, Direction.SOUTH, seed ^ 0x81, BuiltInLootTables.PILLAGER_OUTPOST);
            for (int u = -3; u <= 3; u++) p.set(cx + u, B + 1, cz - 3, Blocks.SPRUCE_SLAB.defaultBlockState());
            p.set(cx, B + 8, cz - 4, Blocks.CARTOGRAPHY_TABLE.defaultBlockState());
        }

        @Override public boolean needsSpawn() { return true; }

        @Override
        public void spawn(ServerLevel level, CityLayout city) {
            PortCity.villager(level, cx, B + 1, cz, "국경 수비대장");
            PortCity.villager(level, cx + 2, B + 1, cz + 2, "참모");
        }
    }

    /** 둥근 돌탑 (총안 꼭대기, 안쪽 사다리) */
    private void tower(Placer p, int tx, int tz, double rad, int height) {
        p.forColumns(tx - 5, tz - 5, tx + 5, tz + 5, (x, z) -> {
            double d = Math.hypot(x - tx, z - tz);
            if (d > rad + 0.5) return;
            p.foundation(x, z, B - 1, 16);
            boolean wall = d > rad - 0.7;
            for (int y = 0; y <= height; y++) {
                BlockState s = wall ? ((y % 6 == 3 && ((x + z) & 1) == 0) ? Blocks.IRON_BARS.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState())
                        : (y == 0 || y == height ? Blocks.SPRUCE_PLANKS.defaultBlockState() : Blocks.AIR.defaultBlockState());
                p.set(x, B + y, z, s);
            }
            if (wall && ((x + z) & 1) == 0) p.set(x, B + height + 1, z, Blocks.STONE_BRICKS.defaultBlockState());
        });
        for (int y = 1; y < height; y++) p.set(tx, B + y, tz, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH));
        p.set(tx, B + height, tz, Blocks.AIR.defaultBlockState());
        p.set(tx, B + 1, tz + 1, Blocks.AIR.defaultBlockState());
        p.set(tx + 1, B + height - 1, tz + 1, Blocks.LANTERN.defaultBlockState());
    }

    enum Kind { HOUSE, BARRACKS, STABLE, ARMORY, SMITHY, CHAPEL, GRANARY, INN }

    /** 건물 (용도별 실내) */
    final class Building extends Feature {
        final Frame f;
        final int W, D, floors;
        final BlockState wall;
        final Block roofStairs, roofSlab;
        final Kind kind;
        final long bs;

        Building(int cx, int cz, Direction front, int W, int D, int floors, Block wall, Block roofStairs, Block roofSlab, Kind kind, long bs) {
            this(new Frame(cx, cz, front, W, D), W, D, floors, wall, roofStairs, roofSlab, kind, bs);
        }

        private Building(Frame f, int W, int D, int floors, Block wall, Block roofStairs, Block roofSlab, Kind kind, long bs) {
            super(f.minX - 2, f.minZ - 2, f.maxX + 2, f.maxZ + 2);
            this.f = f;
            this.W = W;
            this.D = D;
            this.floors = floors;
            this.wall = wall.defaultBlockState();
            this.roofStairs = roofStairs;
            this.roofSlab = roofSlab;
            this.kind = kind;
            this.bs = bs;
        }

        @Override
        public void build(Placer p, CityLayout city) {
            BlockState post = wall.is(Blocks.SPRUCE_PLANKS) ? Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState()
                    : wall.is(Blocks.CALCITE) ? Blocks.POLISHED_ANDESITE.defaultBlockState() : Blocks.DARK_OAK_LOG.defaultBlockState();
            PortCity.body(p, f, B, W, D, floors, wall, post, roofStairs, roofSlab, kind == Kind.STABLE || kind == Kind.SMITHY);
            switch (kind) {
                case HOUSE -> {
                    f.set(p, B, W - 2, 1, D - 2, Palette.bed(f.front, true));
                    f.set(p, B, W - 2, 1, D - 3, Palette.bed(f.front, false));
                    f.set(p, B, 1, 1, 1, Blocks.BARREL.defaultBlockState());
                    f.set(p, B, W - 2, 6, D - 2, Palette.bed(f.front, true));
                    f.set(p, B, W - 2, 6, D - 3, Palette.bed(f.front, false));
                }
                case BARRACKS -> {
                    for (int fl = 0; fl < floors; fl++) for (int u = 2; u < W - 2; u += 2) {
                        f.set(p, B, u, 1 + fl * 5, D - 2, Palette.bed(f.front, true));
                        f.set(p, B, u, 1 + fl * 5, D - 3, Palette.bed(f.front, false));
                    }
                    for (int u = 2; u < W - 2; u += 4) f.set(p, B, u, 1, 1, Blocks.BARREL.defaultBlockState());
                }
                case STABLE -> {
                    for (int u = 1; u < W - 1; u++) {
                        f.set(p, B, u, 1, 4, u % 3 == 0 ? Blocks.SPRUCE_FENCE_GATE.defaultBlockState() : Blocks.SPRUCE_FENCE.defaultBlockState());
                        if (u % 3 == 1) f.set(p, B, u, 1, D - 2, Blocks.HAY_BLOCK.defaultBlockState());
                    }
                }
                case ARMORY -> {
                    for (int u = 1; u < W - 1; u += 2) f.set(p, B, u, 1, D - 2, Blocks.BARREL.defaultBlockState());
                    for (int v = 2; v < D - 2; v += 2) f.set(p, B, 1, 2, v, Blocks.IRON_BARS.defaultBlockState());
                    OldCapital.chest(p, f.x(W / 2, D - 2), B + 1, f.z(W / 2, D - 2), f.front, bs, BuiltInLootTables.VILLAGE_WEAPONSMITH);
                    OldCapital.chest(p, f.x(W / 2 + 2, D - 2), B + 1, f.z(W / 2 + 2, D - 2), f.front, bs + 1, BuiltInLootTables.VILLAGE_ARMORER);
                }
                case SMITHY -> {
                    f.set(p, B, 2, 1, D - 2, Blocks.BLAST_FURNACE.defaultBlockState());
                    f.set(p, B, 3, 1, D - 2, Blocks.LAVA_CAULDRON.defaultBlockState());
                    f.set(p, B, 5, 1, D - 3, Blocks.ANVIL.defaultBlockState());
                    f.set(p, B, 7, 1, D - 2, Blocks.GRINDSTONE.defaultBlockState());
                    f.set(p, B, 8, 1, D - 2, Blocks.SMITHING_TABLE.defaultBlockState());
                    OldCapital.chest(p, f.x(1, D - 2), B + 1, f.z(1, D - 2), f.front, bs, BuiltInLootTables.VILLAGE_TOOLSMITH);
                }
                case CHAPEL -> {
                    for (int v = 3; v < D - 3; v += 2) for (int u = 2; u < W - 2; u++) if (u != W / 2) f.set(p, B, u, 1, v, Blocks.SPRUCE_STAIRS.defaultBlockState()
                            .setValue(net.minecraft.world.level.block.StairBlock.FACING, f.front));
                    f.set(p, B, W / 2, 1, D - 2, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
                    f.set(p, B, W / 2, 2, D - 2, Blocks.CANDLE.defaultBlockState());
                    for (int y = 1; y <= 12; y++) for (int u = W / 2 - 1; u <= W / 2 + 1; u++) for (int v = -3; v <= -1; v++) {
                        boolean e = u != W / 2 || v != -2;
                        f.set(p, B, u, 5 + y, v + 1 + 0, e ? wall : Blocks.AIR.defaultBlockState());
                    }
                    f.set(p, B, W / 2, 16, -1, Blocks.BELL.defaultBlockState());
                }
                case GRANARY -> {
                    for (int u = 1; u < W - 1; u++) for (int v = 1; v < D - 1; v++) if ((u + v) % 3 == 0) f.set(p, B, u, 1, v, Blocks.HAY_BLOCK.defaultBlockState());
                }
                case INN -> {
                    for (int u = 2; u <= W - 3; u += 2) f.set(p, B, u, 1, D - 2, Blocks.BARREL.defaultBlockState());
                    for (int u = 3; u <= W - 4; u++) f.set(p, B, u, 1, D - 4, PortCity.slabTop(Blocks.SPRUCE_SLAB));
                    for (int u = 2; u <= W - 3; u += 3) for (int fl = 1; fl < floors; fl++) {
                        f.set(p, B, u, 1 + fl * 5, 2, Palette.bed(f.front, true));
                        f.set(p, B, u, 1 + fl * 5, 3, Palette.bed(f.front, false));
                    }
                }
            }
        }

        @Override public boolean needsSpawn() { return kind != Kind.GRANARY && (kind != Kind.HOUSE || (bs & 1) == 0); }

        @Override
        public void spawn(ServerLevel level, CityLayout city) {
            int x = f.x(W / 2, 2), z = f.z(W / 2, 2);
            switch (kind) {
                case BARRACKS -> {
                    PortCity.villager(level, x, B + 1, z, "병사");
                    PortCity.villager(level, x + 1, B + 1, z, "병사");
                }
                case STABLE -> {
                    PortCity.villager(level, x, B + 1, z, "마부");
                    for (int i = 0; i < 3; i++) {
                        Mob hz = EntityType.HORSE.spawn(level, new BlockPos(f.x(2 + i * 3, D - 3), B + 1, f.z(2 + i * 3, D - 3)), MobSpawnType.STRUCTURE);
                        if (hz != null) hz.setPersistenceRequired();
                    }
                }
                case ARMORY -> PortCity.villager(level, x, B + 1, z, "무기고 관리관");
                case SMITHY -> PortCity.villager(level, x, B + 1, z, "대장장이");
                case CHAPEL -> PortCity.villager(level, x, B + 1, z, "군종 사제");
                case INN -> PortCity.villager(level, x, B + 1, z, "국경의 등불 여관 주인");
                default -> PortCity.villager(level, x, B + 1, z, null);
            }
        }
    }

    /** 훈련장: 과녁·허수아비·무기 걸이, 울타리 */
    final class TrainingYard extends Feature {
        final int cx, cz;

        TrainingYard(int cx, int cz) {
            super(cx - 14, cz - 12, cx + 14, cz + 12);
            this.cx = cx;
            this.cz = cz;
        }

        @Override
        public void build(Placer p, CityLayout city) {
            p.forColumns(cx - 14, cz - 12, cx + 14, cz + 12, (x, z) -> {
                int dx = x - cx, dz = z - cz;
                p.set(x, B, z, h(x, 2, z) % 3 == 0 ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.DIRT_PATH.defaultBlockState());
                if ((Math.abs(dx) == 14 || Math.abs(dz) == 12) && !(dz == -12 && Math.abs(dx) <= 2)) p.set(x, B + 1, z, Blocks.SPRUCE_FENCE.defaultBlockState());
            });
            for (int i = 0; i < 4; i++) {                                                     // 과녁
                int x = cx - 9 + i * 6, z = cz + 9;
                p.set(x, B + 1, z, Blocks.HAY_BLOCK.defaultBlockState());
                p.set(x, B + 2, z, Blocks.TARGET.defaultBlockState());
            }
            for (int i = 0; i < 4; i++) {                                                     // 허수아비
                int x = cx - 9 + i * 6, z = cz - 2;
                p.set(x, B + 1, z, Blocks.SPRUCE_FENCE.defaultBlockState());
                p.set(x, B + 2, z, Blocks.HAY_BLOCK.defaultBlockState());
                p.set(x, B + 3, z, Blocks.CARVED_PUMPKIN.defaultBlockState());
            }
            for (int x = cx - 10; x <= cx + 10; x += 4) {                                    // 무기 걸이
                p.set(x, B + 1, cz - 9, Blocks.SPRUCE_FENCE.defaultBlockState());
                p.set(x + 1, B + 1, cz - 9, Blocks.BARREL.defaultBlockState());
            }
        }

        @Override public boolean needsSpawn() { return true; }

        @Override
        public void spawn(ServerLevel level, CityLayout city) {
            PortCity.villager(level, cx, B + 1, cz + 4, "교관");
            PortCity.villager(level, cx + 3, B + 1, cz + 4, "신병");
            PortCity.villager(level, cx - 3, B + 1, cz + 4, "신병");
        }
    }

    /** 능보 끝 망루와 대포 */
    final class Bastions extends Feature {
        Bastions() {
            super(VX - POINT - 2, VZ - POINT - 2, VX + POINT + 2, VZ + POINT + 2);
        }

        @Override
        public void build(Placer p, CityLayout city) {
            for (int k = 0; k < 6; k++) {
                double a = theta0 + Math.PI / 3 * k;
                int tx = VX + (int) Math.round(Math.cos(a) * (POINT - 14)), tz = VZ + (int) Math.round(Math.sin(a) * (POINT - 14));
                if (!p.intersects(tx - 12, tz - 12, tx + 12, tz + 12)) continue;
                // 망루 (성벽 위 높이에서 시작)
                p.forColumns(tx - 3, tz - 3, tx + 3, tz + 3, (x, z) -> {
                    double d = Math.hypot(x - tx, z - tz);
                    if (d > 3.3) return;
                    boolean wall = d > 2.4;
                    for (int y = 1; y <= WALL_H + 8; y++) {
                        BlockState s = wall ? Blocks.STONE_BRICKS.defaultBlockState() : (y == WALL_H || y == WALL_H + 8 ? Blocks.SPRUCE_PLANKS.defaultBlockState() : Blocks.AIR.defaultBlockState());
                        if (wall && y > WALL_H && y < WALL_H + 4 && ((x + z) & 1) == 0) s = Blocks.AIR.defaultBlockState();
                        p.set(x, B + y, z, s);
                    }
                    if (wall && ((x + z) & 1) == 0) p.set(x, B + WALL_H + 9, z, Blocks.STONE_BRICKS.defaultBlockState());
                });
                for (int y = 1; y < WALL_H + 8; y++) p.set(tx, B + y, tz, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH));
                p.set(tx, B + WALL_H + 8, tz, Blocks.AIR.defaultBlockState());
                p.set(tx, B + WALL_H, tz, Blocks.AIR.defaultBlockState());
                p.set(tx, B + 1, tz + 1, Blocks.AIR.defaultBlockState());
                p.set(tx, B + 2, tz + 1, Blocks.AIR.defaultBlockState());
                p.set(tx + 1, B + WALL_H + 9, tz, Blocks.RED_BANNER.defaultBlockState());
                // 대포 2문 (능보 양 옆면, 바깥을 향함)
                for (int s = -1; s <= 1; s += 2) {
                    double ca = a + s * Math.toRadians(9);
                    int cx = VX + (int) Math.round(Math.cos(ca) * (POINT - 22)), cz = VZ + (int) Math.round(Math.sin(ca) * (POINT - 22));
                    int ox = (int) Math.round(Math.cos(a)), oz = (int) Math.round(Math.sin(a));
                    p.set(cx, B + WALL_H + 1, cz, Blocks.SPRUCE_PLANKS.defaultBlockState());
                    p.set(cx + ox, B + WALL_H + 1, cz + oz, Blocks.SPRUCE_PLANKS.defaultBlockState());
                    p.set(cx, B + WALL_H + 2, cz, Blocks.POLISHED_BLACKSTONE.defaultBlockState());
                    p.set(cx + ox, B + WALL_H + 2, cz + oz, Blocks.POLISHED_BLACKSTONE.defaultBlockState());
                    p.set(cx + ox * 2, B + WALL_H + 2, cz + oz * 2, Blocks.POLISHED_BLACKSTONE_WALL.defaultBlockState());
                    p.set(cx - ox, B + WALL_H + 1, cz - oz, Blocks.BARREL.defaultBlockState());
                }
            }
        }
    }

    /** 문루: 성문 양옆 네모 탑 + 위 깃발, 안쪽 경비 초소 */
    final class Gatehouses extends Feature {
        Gatehouses() {
            super(VX - POINT - 14, VZ - POINT - 14, VX + POINT + 14, VZ + POINT + 14);
        }

        @Override
        public void build(Placer p, CityLayout city) {
            for (double g : gates) {
                double wr = wallR(g);
                for (int s = -1; s <= 1; s += 2) {
                    double lat = s * 6.5;
                    int tx = VX + (int) Math.round(Math.cos(g) * (wr - 2) - Math.sin(g) * lat), tz = VZ + (int) Math.round(Math.sin(g) * (wr - 2) + Math.cos(g) * lat);
                    if (!p.intersects(tx - 4, tz - 4, tx + 4, tz + 4)) continue;
                    for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                        boolean e = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                        p.foundation(tx + dx, tz + dz, B - 1, 16);
                        for (int y = 0; y <= WALL_H + 5; y++) p.set(tx + dx, B + y, tz + dz, e || y == 0 || y == WALL_H + 5 ? Blocks.STONE_BRICKS.defaultBlockState() : Blocks.AIR.defaultBlockState());
                        if (e && ((dx + dz) & 1) == 0) p.set(tx + dx, B + WALL_H + 6, tz + dz, Blocks.STONE_BRICKS.defaultBlockState());
                    }
                    p.set(tx, B + WALL_H + 6, tz, Blocks.DARK_OAK_FENCE.defaultBlockState());
                    p.set(tx, B + WALL_H + 7, tz, Blocks.RED_BANNER.defaultBlockState());
                    p.set(tx, B + 3, tz, Blocks.LANTERN.defaultBlockState());
                }
            }
        }
    }
}
