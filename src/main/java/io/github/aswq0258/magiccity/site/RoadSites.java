package io.github.aswq0258.magiccity.site;

import io.github.aswq0258.magiccity.build.BuildLayout;
import io.github.aswq0258.magiccity.build.Placer;
import io.github.aswq0258.magiccity.city.CityLayout;
import io.github.aswq0258.magiccity.city.Palette;
import io.github.aswq0258.magiccity.city.Shapes;
import io.github.aswq0258.magiccity.city.feature.Feature;
import io.github.aswq0258.magiccity.world.KingdomRoads;
import io.github.aswq0258.magiccity.world.KingdomTerrain;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 길 10개를 "지역" 으로: 길마다 쉼터(안전 거점), 몬스터 둥지 2곳, 필드보스 결투장.
 * 위치는 지형과 길만으로 정해지므로(시드와 무관) 보스·출몰 관리자도 같은 위치를 압니다.
 * /magiccity site roads 로 한 번에 지음 (흩어진 건물이 있는 청크만 처리).
 */
public final class RoadSites implements BuildLayout {
    public enum Kind { REST, CAMP, ARENA }

    /** 길 위 거점 하나: 길 번호, 종류, 중심, 지면 높이 */
    public record Site(int road, Kind kind, int x, int z, int y) {}

    private static List<Site> sites;

    /** 모든 길의 거점 (처음 부를 때 계산) */
    public static synchronized List<Site> sites() {
        if (sites != null) return sites;
        List<Site> out = new ArrayList<>();
        for (int i = 0; i < KingdomRoads.REGIONS; i++) {
            KingdomRoads.Road r = KingdomRoads.ROADS[i];
            boolean longRoad = r.length() > 3000;
            Object[][] plan = longRoad
                    ? new Object[][]{{Kind.REST, .22, 1}, {Kind.CAMP, .36, -1}, {Kind.ARENA, .52, 1}, {Kind.CAMP, .66, -1}, {Kind.REST, .80, 1}}
                    : new Object[][]{{Kind.REST, .28, 1}, {Kind.CAMP, .44, -1}, {Kind.ARENA, .60, 1}, {Kind.CAMP, .78, -1}};
            for (Object[] e : plan) {
                Site s = place(i, (Kind) e[0], (Double) e[1], (Integer) e[2], out);
                if (s != null) out.add(s);
            }
        }
        sites = Collections.unmodifiableList(out);
        return sites;
    }

    private static Site place(int ri, Kind kind, double frac, int side, List<Site> placed) {
        KingdomRoads.Road r = KingdomRoads.ROADS[ri];
        int off = kind == Kind.REST ? 13 : kind == Kind.CAMP ? 42 : 48;
        int half = kind == Kind.ARENA ? 16 : 10;
        for (int attempt = 0; attempt < 12; attempt++) {
            double f = frac + (attempt / 2) * 0.025 * ((attempt & 1) == 0 ? 1 : -1);
            int sd = attempt % 4 >= 2 ? -side : side;
            double[] pt = KingdomRoads.pointAt(r, f);
            double nx = -pt[3], nz = pt[2];
            // 비틀린 실제 길 중심 찾기
            double bestT = 0, bestD = 1e9;
            for (int t = -45; t <= 45; t += 3) {
                double d = KingdomRoads.dist(r, pt[0] + nx * t, pt[1] + nz * t);
                if (d < bestD) {
                    bestD = d;
                    bestT = t;
                }
            }
            int x = (int) Math.round(pt[0] + nx * (bestT + sd * off)), z = (int) Math.round(pt[1] + nz * (bestT + sd * off));
            if (!ok(x, z, half, placed)) continue;
            return new Site(ri, kind, x, z, KingdomTerrain.column(x, z).h());
        }
        return null;
    }

    private static boolean ok(int x, int z, int half, List<Site> placed) {
        if (KingdomRoads.excluded(x, z) || Math.hypot(x - KingdomTerrain.OLD_X, z - KingdomTerrain.OLD_Z) < 260) return false;
        int h0 = Integer.MAX_VALUE, h1 = Integer.MIN_VALUE;
        for (int dx = -half; dx <= half; dx += half) {
            for (int dz = -half; dz <= half; dz += half) {
                KingdomTerrain.Column c = KingdomTerrain.column(x + dx, z + dz);
                if (c.water() != Integer.MIN_VALUE && c.water() >= c.h()) return false;
                if (c.onRoad()) return false;
                h0 = Math.min(h0, c.h());
                h1 = Math.max(h1, c.h());
            }
        }
        if (h1 - h0 > 14) return false;
        for (Site s : placed) if (Math.abs(s.x - x) < 40 && Math.abs(s.z - z) < 40) return false;
        for (Hamlets.Spot v : Hamlets.spots()) if (Math.abs(v.x() - x) < v.half() + 30 && Math.abs(v.z() - z) < v.half() + 30) return false;
        return true;
    }

    // ───────────────────────── 건설 설계도 ─────────────────────────

    private final List<Feature> features = new ArrayList<>();
    private final Map<Long, List<Feature>> byChunk = new HashMap<>();
    private final List<ChunkPos> chunks;

    public RoadSites() {
        for (Site s : sites()) {
            Feature f = switch (s.kind()) {
                case REST -> new RestStop(s);
                case CAMP -> new Camp(s);
                case ARENA -> new Arena(s);
            };
            f.groundY = s.y();
            features.add(f);
        }
        LinkedHashSet<Long> keys = new LinkedHashSet<>();
        for (Feature f : features) {
            for (int x = (f.minX - 1) >> 4; x <= (f.maxX + 1) >> 4; x++) {
                for (int z = (f.minZ - 1) >> 4; z <= (f.maxZ + 1) >> 4; z++) {
                    long k = ChunkPos.asLong(x, z);
                    keys.add(k);
                    byChunk.computeIfAbsent(k, q -> new ArrayList<>()).add(f);
                }
            }
        }
        chunks = new ArrayList<>();
        for (long k : keys) chunks.add(new ChunkPos(k));
    }

    public int featureCount() {
        return features.size();
    }

    @Override public int cx() { return KingdomTerrain.CX; }
    @Override public int cz() { return KingdomTerrain.CZ; }
    @Override public int base() { return KingdomTerrain.SEA; }
    @Override public int radius() { return 0; }
    @Override public CityLayout city() { return null; }
    @Override public List<ChunkPos> chunkList() { return chunks; }
    @Override public int shapeMinY() { return 40; }
    @Override public int shapeMaxY() { return 300; }

    @Override
    public void buildColumn(Placer p, int x, int z) {}

    @Override
    public List<Feature> featuresIn(ChunkPos cp) {
        return byChunk.getOrDefault(cp.toLong(), Collections.emptyList());
    }

    /** 쉼터 실내만 조명 */
    @Override
    public int[] lightRange(int x, int z) {
        for (Feature f : featuresIn(new ChunkPos(x >> 4, z >> 4))) {
            if (f instanceof RestStop && x >= f.minX && x <= f.maxX && z >= f.minZ && z <= f.maxZ) return new int[]{f.groundY, f.groundY + 6};
        }
        return null;
    }

    // ───────────────────────── 공용 ─────────────────────────

    /** 평평한 터 다지기: 위는 비우고, 아래는 흙·돌로 메움 */
    static void pad(Placer p, int x, int z, int b, BlockState top) {
        if (p.pass() != Placer.Pass.SOLID || !p.contains(x, z)) return;
        p.clearAbove(x, z, b + 1);
        p.set(x, b, z, top);
        for (int y = b - 1; y > b - 30; y--) {
            BlockState s = p.get(x, y, z);
            if (!s.isAir() && s.getFluidState().isEmpty() && !s.canBeReplaced()) break;
            p.set(x, y, z, b - y < 3 ? Blocks.DIRT.defaultBlockState() : Blocks.STONE.defaultBlockState());
        }
    }

    // ───────────────────────── 쉼터 ─────────────────────────

    /** 길가 쉼터: 지붕 덮인 휴게소(침대·모닥불·작업대·보급 상자), 말 울타리, 우물, 이정표 깃발. 몬스터가 출몰하지 않는 안전 지대 */
    public static final class RestStop extends Feature {
        private final Site s;

        RestStop(Site s) {
            super(s.x() - 9, s.z() - 9, s.x() + 9, s.z() + 9);
            this.s = s;
        }

        @Override
        public void build(Placer p, CityLayout city) {
            final int b = groundY, cx = s.x(), cz = s.z();
            p.forColumns(minX, minZ, maxX, maxZ, (x, z) -> {
                int k = Shapes.hash(x, 3, z, 99);
                boolean house = Math.abs(x - cx) <= 4 && Math.abs(z - cz) <= 3;
                pad(p, x, z, b, house ? Blocks.SPRUCE_PLANKS.defaultBlockState() : k % 3 == 0 ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState());
            });
            // 휴게소 (9 x 7, 남쪽이 트인 반개방 오두막)
            for (int x = cx - 4; x <= cx + 4; x++) for (int z = cz - 3; z <= cz + 3; z++) {
                boolean post = (x == cx - 4 || x == cx + 4) && (z == cz - 3 || z == cz + 3);
                boolean wall = (z == cz - 3 || x == cx - 4 || x == cx + 4) && !post;
                for (int y = 1; y <= 3; y++) {
                    if (post) p.set(x, b + y, z, Blocks.SPRUCE_LOG.defaultBlockState());
                    else if (wall) p.set(x, b + y, z, y == 2 && (x == cx || z == cz) ? Blocks.GLASS_PANE.defaultBlockState()
                            : y == 1 ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.SPRUCE_PLANKS.defaultBlockState());
                }
            }
            // 박공 지붕 (동서 방향 용마루)
            for (int k = 0; k <= 4; k++) {
                for (int x = cx - 5; x <= cx + 5; x++) {
                    int zn = cz - 4 + k, zs = cz + 4 - k;
                    if (zn > zs) continue;
                    if (zn == zs) p.set(x, b + 4 + k, zn, Blocks.SPRUCE_SLAB.defaultBlockState());
                    else {
                        p.set(x, b + 4 + k, zn, Palette.stairs(Blocks.SPRUCE_STAIRS, Direction.SOUTH));
                        p.set(x, b + 4 + k, zs, Palette.stairs(Blocks.SPRUCE_STAIRS, Direction.NORTH));
                    }
                }
                if (k < 4) for (int z = cz - 3 + k; z <= cz + 3 - k; z++) {
                    p.set(cx - 4, b + 4 + k, z, Blocks.SPRUCE_PLANKS.defaultBlockState());
                    p.set(cx + 4, b + 4 + k, z, Blocks.SPRUCE_PLANKS.defaultBlockState());
                }
            }
            p.set(cx - 3, b + 1, cz - 2, Palette.bed(Direction.NORTH, true));
            p.set(cx - 3, b + 1, cz - 1, Palette.bed(Direction.NORTH, false));
            p.set(cx - 1, b + 1, cz - 2, Palette.bed(Direction.NORTH, true));
            p.set(cx - 1, b + 1, cz - 1, Palette.bed(Direction.NORTH, false));
            p.set(cx + 1, b + 1, cz - 2, Blocks.CRAFTING_TABLE.defaultBlockState());
            p.set(cx + 2, b + 1, cz - 2, Blocks.FURNACE.defaultBlockState());
            OldCapital.chest(p, cx + 3, b + 1, cz - 2, Direction.SOUTH, s.x() * 31L + s.z(), BuiltInLootTables.VILLAGE_PLAINS_HOUSE);
            p.set(cx, b + 3, cz, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
            p.set(cx + 3, b + 1, cz + 2, Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.WEST));
            // 앞마당 모닥불과 통나무 의자
            p.set(cx, b + 1, cz + 6, Blocks.CAMPFIRE.defaultBlockState());
            for (int sgn = -1; sgn <= 1; sgn += 2) p.set(cx + sgn * 2, b + 1, cz + 6, Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, sgn > 0 ? Direction.EAST : Direction.WEST));
            // 우물 (물 가마솥) 과 말 울타리
            p.set(cx - 7, b + 1, cz + 6, Blocks.WATER_CAULDRON.defaultBlockState().setValue(net.minecraft.world.level.block.LayeredCauldronBlock.LEVEL, 3));
            for (int x = cx + 5; x <= cx + 8; x++) for (int z = cz - 8; z <= cz - 4; z++) {
                boolean e = x == cx + 5 || x == cx + 8 || z == cz - 8 || z == cz - 4;
                if (e && !(x == cx + 5 && z == cz - 6)) p.set(x, b + 1, z, Blocks.SPRUCE_FENCE.defaultBlockState());
            }
            p.set(cx + 7, b + 1, cz - 7, Blocks.HAY_BLOCK.defaultBlockState());
            // 이정표 기둥 (왕국 민트 깃발 + 랜턴): 멀리서도 보이게
            for (int y = 1; y <= 5; y++) p.set(cx - 7, b + y, cz - 7, Blocks.SPRUCE_FENCE.defaultBlockState());
            p.set(cx - 7, b + 6, cz - 7, Blocks.LANTERN.defaultBlockState());
            p.set(cx - 6, b + 5, cz - 7, io.github.aswq0258.magiccity.city.Royal.mintBanner(Direction.EAST));
            for (int[] q : new int[][]{{cx - 8, cz + 8}, {cx + 8, cz + 8}, {cx - 8, cz - 2}}) {
                p.set(q[0], b + 1, q[1], Blocks.SPRUCE_FENCE.defaultBlockState());
                p.set(q[0], b + 2, q[1], Blocks.LANTERN.defaultBlockState());
            }
        }
    }

    // ───────────────────────── 몬스터 둥지 ─────────────────────────

    /** 길마다 재료가 다른 몬스터 둥지: 부서진 목책, 천막, 뼈 더미, 가운데 스포너 + 전리품 상자 */
    public static final class Camp extends Feature {
        private final Site s;
        private final RoadTheme t;

        Camp(Site s) {
            super(s.x() - 10, s.z() - 10, s.x() + 10, s.z() + 10);
            this.s = s;
            this.t = RoadTheme.THEMES[s.road()];
        }

        @Override
        public void build(Placer p, CityLayout city) {
            final int b = groundY, cx = s.x(), cz = s.z();
            long seed = cx * 341873128712L + cz;
            p.forColumns(minX, minZ, maxX, maxZ, (x, z) -> {
                double r = Math.hypot(x - cx, z - cz);
                if (r > 10.5) return;
                int k = Shapes.hash(x, 4, z, seed);
                pad(p, x, z, b, k % 3 == 0 ? t.ground() : k % 3 == 1 ? Blocks.COARSE_DIRT.defaultBlockState() : t.ground());
                if (r > 8.5 && r <= 9.5) {                                           // 반쯤 무너진 목책
                    int top = 1 + k % 4;
                    if (k % 5 == 0) top = 0;
                    for (int y = 1; y <= top; y++) p.set(x, b + y, z, t.wall());
                } else if (r < 8 && r > 3 && k % 17 == 0) {
                    p.set(x, b + 1, z, t.accent());
                } else if (r < 8 && r > 3 && k % 23 == 0) {
                    p.set(x, b + 1, z, Blocks.BONE_BLOCK.defaultBlockState());
                }
            });
            // 천막 2개
            for (int sgn = -1; sgn <= 1; sgn += 2) {
                int tx = cx + sgn * 5, tz = cz - 3;
                for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                    int ad = Math.abs(dx);
                    for (int y = 1; y <= 3 - ad; y++) if (y == 3 - ad || Math.abs(dz) == 2 && dz < 0)
                        p.set(tx + dx, b + y, tz + dz, sgn < 0 ? Blocks.BROWN_WOOL.defaultBlockState() : Blocks.GRAY_WOOL.defaultBlockState());
                }
            }
            // 가운데: 스포너 받침 + 횃불 기둥
            p.set(cx, b, cz, t.wall());
            OldCapital.spawner(p, cx, b + 1, cz, t.spawner());
            for (int[] q : new int[][]{{cx - 3, cz + 3}, {cx + 3, cz + 3}}) {
                p.set(q[0], b + 1, q[1], t.wall());
                p.set(q[0], b + 2, q[1], t.light().is(Blocks.SEA_LANTERN) ? t.light() : t.light().setValue(LanternBlock.HANGING, false));
            }
            OldCapital.chest(p, cx, b + 1, cz - 5, Direction.SOUTH, seed, s.road() >= 8 ? BuiltInLootTables.SIMPLE_DUNGEON : BuiltInLootTables.PILLAGER_OUTPOST);
        }
    }

    // ───────────────────────── 필드보스 결투장 ─────────────────────────

    /** 원형 결투장: 부서진 돌기둥 8개, 화톳불 4개, 가운데 제단. 플레이어가 다가가면 필드보스 등장 (boss.FieldBosses) */
    public static final class Arena extends Feature {
        private final Site s;
        private final RoadTheme t;

        Arena(Site s) {
            super(s.x() - 16, s.z() - 16, s.x() + 16, s.z() + 16);
            this.s = s;
            this.t = RoadTheme.THEMES[s.road()];
        }

        @Override
        public void build(Placer p, CityLayout city) {
            final int b = groundY, cx = s.x(), cz = s.z();
            long seed = cx * 7919L + cz * 31L;
            p.forColumns(minX, minZ, maxX, maxZ, (x, z) -> {
                double r = Math.hypot(x - cx, z - cz);
                if (r > 15.5) return;
                int k = Shapes.hash(x, 5, z, seed);
                BlockState floor = r > 14.5 ? Blocks.STONE_BRICKS.defaultBlockState()
                        : k % 5 == 0 ? t.ground() : k % 4 == 0 ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState()
                        : k % 7 == 0 ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState();
                pad(p, x, z, b, floor);
                if (r > 14.5 && r <= 15.5 && k % 3 != 0) p.set(x, b + 1, z, Blocks.STONE_BRICK_WALL.defaultBlockState());
            });
            for (int i = 0; i < 8; i++) {
                double a = Math.PI * 2 * i / 8 + 0.2;
                int px = cx + (int) Math.round(Math.cos(a) * 12), pz = cz + (int) Math.round(Math.sin(a) * 12);
                int top = 3 + Shapes.hash(px, 6, pz, seed) % 6;
                for (int y = 1; y <= top; y++) p.set(px, b + y, pz, y == top ? Blocks.CHISELED_STONE_BRICKS.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState());
                if (i % 2 == 0) {
                    int fx = cx + (int) Math.round(Math.cos(a) * 9), fz = cz + (int) Math.round(Math.sin(a) * 9);
                    p.set(fx, b + 1, fz, t.wall());
                    p.set(fx, b + 2, fz, s.road() >= 7 ? Blocks.SOUL_CAMPFIRE.defaultBlockState() : Blocks.CAMPFIRE.defaultBlockState());
                }
            }
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) p.set(cx + dx, b, cz + dz, t.wall());
            p.set(cx, b + 1, cz, t.accent().is(Blocks.RAIL) || t.accent().is(Blocks.COBWEB) ? Blocks.CHISELED_STONE_BRICKS.defaultBlockState() : t.accent());
        }
    }
}
