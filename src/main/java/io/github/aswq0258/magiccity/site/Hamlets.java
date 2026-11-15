package io.github.aswq0258.magiccity.site;

import io.github.aswq0258.magiccity.build.BuildLayout;
import io.github.aswq0258.magiccity.build.Placer;
import io.github.aswq0258.magiccity.city.CityLayout;
import io.github.aswq0258.magiccity.city.Palette;
import io.github.aswq0258.magiccity.city.Royal;
import io.github.aswq0258.magiccity.city.Shapes;
import io.github.aswq0258.magiccity.city.feature.Feature;
import io.github.aswq0258.magiccity.city.feature.Frame;
import io.github.aswq0258.magiccity.world.KingdomMap;
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
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LeavesBlock;
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
 * 왕국 대륙 곳곳의 작은 촌락 (바닐라 마을 대신). 지역 성격에 따라 5가지 양식:
 *  - 왕국 농촌 (평원·농경지·초원): 흰 벽 + 민트 지붕, 밀밭, 민트 지붕 우물
 *  - 숲속 엘프 촌락 (숲·엘프 대수림): 자작나무 벽 + 잎 지붕, 꽃 진달래 사당
 *  - 설원 사냥꾼 마을 (설원·산): 가문비 통나무집 + 짙은 지붕, 가죽 건조대와 모닥불
 *  - 어부 촌락 (바닷가·습지): 맹그로브 기둥집, 그물 건조대, 통발
 *  - 피난민 정착촌 (재의 들판·황무지·저주받은 숲): 목책, 천막과 판잣집, 망루 (옛 왕도에서 피난 온 사람들)
 * 위치는 지형만으로 정해짐 (800블록 격자마다 최대 1곳). /magiccity site hamlets 로 한 번에 지음.
 */
public final class Hamlets implements BuildLayout {
    public enum Style {
        FARM("왕국 농촌", "농부"), ELF("숲속 엘프 촌락", "엘프 채집꾼"), FROST("설원 사냥꾼 마을", "사냥꾼"),
        FISHER("어부 촌락", "어부"), REFUGEE("피난민 정착촌", "피난민");
        public final String title, people;

        Style(String title, String people) {
            this.title = title;
            this.people = people;
        }
    }

    public record Spot(int x, int z, int y, Style style, String name) {}

    private static final int CELL = 800;
    private static final String[] PREFIX = {"버들", "은빛", "참나무", "바람", "이슬", "들꽃", "돌담", "샘물", "노을", "달빛", "솔숲", "물방아", "종달새", "황금", "푸른", "안개"};
    private static List<Spot> spots;

    /** 모든 촌락 위치 (처음 부를 때 계산) */
    public static synchronized List<Spot> spots() {
        if (spots != null) return spots;
        List<Spot> out = new ArrayList<>();
        int gx0 = Math.floorDiv(-4800, CELL), gx1 = Math.floorDiv(4000, CELL), gz0 = Math.floorDiv(-5100, CELL), gz1 = Math.floorDiv(4500, CELL);
        for (int gx = gx0; gx <= gx1; gx++) {
            for (int gz = gz0; gz <= gz1; gz++) {
                for (int attempt = 0; attempt < 6; attempt++) {
                    int hsh = Shapes.hash(gx, attempt, gz, 0x4A3L);
                    int x = gx * CELL + 120 + Math.floorMod(hsh, CELL - 240);
                    int z = gz * CELL + 120 + Math.floorMod(hsh >>> 11, CELL - 240);
                    Style st = check(x, z);
                    if (st == null) continue;
                    String name = PREFIX[Math.floorMod(hsh >>> 5, PREFIX.length)] + (st == Style.REFUGEE ? " 정착촌" : st == Style.FROST ? " 사냥터" : " 마을");
                    out.add(new Spot(x, z, KingdomTerrain.column(x, z).h(), st, name));
                    break;
                }
            }
        }
        spots = Collections.unmodifiableList(out);
        return spots;
    }

    /** 촌락을 지을 만한 곳인지, 그렇다면 어떤 양식인지 */
    private static Style check(int x, int z) {
        if (Math.hypot(x - KingdomTerrain.CX, z - KingdomTerrain.CZ) < 520) return null;
        if (Math.hypot(x - KingdomTerrain.OLD_X, z - KingdomTerrain.OLD_Z) < 420) return null;
        for (Object[] p : KingdomMap.PLAN_SITES) {
            int sx = KingdomTerrain.worldX((Integer) p[2]), sz = KingdomTerrain.worldZ((Integer) p[3]);
            if (Math.hypot(x - sx, z - sz) < 300) return null;
        }
        for (RoadSites.Site s : RoadSites.sites()) if (Math.hypot(x - s.x(), z - s.z()) < 90) return null;
        if (KingdomRoads.nearest(x, z, 45, null) >= 0) return null;
        int h0 = Integer.MAX_VALUE, h1 = Integer.MIN_VALUE;
        boolean nearWater = false;
        KingdomTerrain.Kind kind = KingdomTerrain.column(x, z).kind();
        for (int dx = -26; dx <= 26; dx += 13) {
            for (int dz = -26; dz <= 26; dz += 13) {
                KingdomTerrain.Column c = KingdomTerrain.column(x + dx, z + dz);
                if (c.water() != Integer.MIN_VALUE && c.water() >= c.h()) return null;
                if (c.onRoad()) return null;
                h0 = Math.min(h0, c.h());
                h1 = Math.max(h1, c.h());
            }
        }
        if (h1 - h0 > 7 || h1 > 125) return null;
        for (int dx = -60; dx <= 60; dx += 30) for (int dz = -60; dz <= 60; dz += 30) {
            KingdomTerrain.Column c = KingdomTerrain.column(x + dx, z + dz);
            if (c.water() != Integer.MIN_VALUE && c.water() >= c.h()) nearWater = true;
        }
        return switch (kind) {
            case PLAINS, FARMLAND, MEADOW -> nearWater ? Style.FISHER : Style.FARM;
            case FOREST, ELF_FOREST -> Style.ELF;
            case SNOW, PEAKS, DWARF -> Style.FROST;
            case BEACH, SWAMP -> Style.FISHER;
            case ASH, WASTELAND, CURSED -> Style.REFUGEE;
            default -> null;
        };
    }

    public static boolean near(double x, double z) {
        for (Spot s : spots()) if (Math.abs(x - s.x()) < 45 && Math.abs(z - s.z()) < 45) return true;
        return false;
    }

    // ───────────────────────── 건설 설계도 ─────────────────────────

    private final List<Hamlet> hamlets = new ArrayList<>();
    private final Map<Long, List<Feature>> byChunk = new HashMap<>();
    private final List<ChunkPos> chunks = new ArrayList<>();

    public Hamlets() {
        LinkedHashSet<Long> keys = new LinkedHashSet<>();
        for (Spot s : spots()) {
            Hamlet h = new Hamlet(s);
            hamlets.add(h);
            for (int x = (h.minX - 1) >> 4; x <= (h.maxX + 1) >> 4; x++) {
                for (int z = (h.minZ - 1) >> 4; z <= (h.maxZ + 1) >> 4; z++) {
                    long k = ChunkPos.asLong(x, z);
                    keys.add(k);
                    byChunk.computeIfAbsent(k, q -> new ArrayList<>()).add(h);
                }
            }
        }
        for (long k : keys) chunks.add(new ChunkPos(k));
    }

    public int count() {
        return hamlets.size();
    }

    @Override public int cx() { return KingdomTerrain.CX; }
    @Override public int cz() { return KingdomTerrain.CZ; }
    @Override public int base() { return KingdomTerrain.SEA; }
    @Override public int radius() { return 0; }
    @Override public CityLayout city() { return null; }
    @Override public List<ChunkPos> chunkList() { return chunks; }
    @Override public int shapeMinY() { return 40; }
    @Override public int shapeMaxY() { return 160; }

    @Override
    public void buildColumn(Placer p, int x, int z) {}

    @Override
    public List<Feature> featuresIn(ChunkPos cp) {
        return byChunk.getOrDefault(cp.toLong(), Collections.emptyList());
    }

    @Override
    public int[] lightRange(int x, int z) {
        for (Feature f : featuresIn(new ChunkPos(x >> 4, z >> 4))) {
            Hamlet h = (Hamlet) f;
            for (int[] q : h.houses) if (Math.abs(x - q[0]) <= 5 && Math.abs(z - q[1]) <= 5) return new int[]{q[3], q[3] + 6};
        }
        return null;
    }

    // ───────────────────────── 촌락 ─────────────────────────

    /** 양식별 재료: 벽, 기둥, 바닥, 지붕 계단 블록, 지붕 마루 */
    private record Mats(BlockState wall, BlockState post, BlockState floor, Block roofStairs, BlockState ridge, BlockState fence) {}

    private static Mats mats(Style s) {
        return switch (s) {
            case FARM -> new Mats(Blocks.CALCITE.defaultBlockState(), Blocks.OAK_LOG.defaultBlockState(), Blocks.OAK_PLANKS.defaultBlockState(),
                    Blocks.WAXED_OXIDIZED_CUT_COPPER_STAIRS, Blocks.WAXED_OXIDIZED_CUT_COPPER.defaultBlockState(), Blocks.OAK_FENCE.defaultBlockState());
            case ELF -> new Mats(Blocks.BIRCH_PLANKS.defaultBlockState(), Blocks.BIRCH_LOG.defaultBlockState(), Blocks.MOSS_BLOCK.defaultBlockState(),
                    Blocks.BIRCH_STAIRS, Blocks.FLOWERING_AZALEA_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true), Blocks.BIRCH_FENCE.defaultBlockState());
            case FROST -> new Mats(Blocks.SPRUCE_PLANKS.defaultBlockState(), Blocks.SPRUCE_LOG.defaultBlockState(), Blocks.SPRUCE_PLANKS.defaultBlockState(),
                    Blocks.DARK_OAK_STAIRS, Blocks.DARK_OAK_PLANKS.defaultBlockState(), Blocks.SPRUCE_FENCE.defaultBlockState());
            case FISHER -> new Mats(Blocks.MANGROVE_PLANKS.defaultBlockState(), Blocks.MANGROVE_LOG.defaultBlockState(), Blocks.SPRUCE_PLANKS.defaultBlockState(),
                    Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_PLANKS.defaultBlockState(), Blocks.MANGROVE_FENCE.defaultBlockState());
            case REFUGEE -> new Mats(Blocks.COBBLESTONE.defaultBlockState(), Blocks.DARK_OAK_LOG.defaultBlockState(), Blocks.COARSE_DIRT.defaultBlockState(),
                    Blocks.DARK_OAK_STAIRS, Blocks.DARK_OAK_PLANKS.defaultBlockState(), Blocks.DARK_OAK_FENCE.defaultBlockState());
        };
    }

    public static final class Hamlet extends Feature {
        final Spot s;
        final Mats m;
        /** 집: {중심 x, 중심 z, 앞 방향 ordinal, 지면 y, 너비, 깊이} */
        final List<int[]> houses = new ArrayList<>();
        final long seed;

        Hamlet(Spot s) {
            super(s.x() - 34, s.z() - 34, s.x() + 34, s.z() + 34);
            this.s = s;
            this.m = mats(s.style());
            this.seed = s.x() * 341873128712L + s.z() * 132897987541L;
            groundY = s.y();
            java.util.Random r = new java.util.Random(seed);
            int n = 4 + r.nextInt(4);
            double a0 = r.nextDouble() * Math.PI * 2;
            for (int i = 0; i < n; i++) {
                double a = a0 + Math.PI * 2 * i / n + (r.nextDouble() - 0.5) * 0.3;
                double rad = 15 + r.nextInt(9);
                int hx = s.x() + (int) Math.round(Math.cos(a) * rad), hz = s.z() + (int) Math.round(Math.sin(a) * rad);
                // 마을 중심을 바라보는 방향
                double vx = s.x() - hx, vz = s.z() - hz;
                Direction front = Math.abs(vx) > Math.abs(vz) ? (vx > 0 ? Direction.EAST : Direction.WEST) : (vz > 0 ? Direction.SOUTH : Direction.NORTH);
                int w = 7 + 2 * r.nextInt(2), d = 6 + r.nextInt(3);
                houses.add(new int[]{hx, hz, front.ordinal(), KingdomTerrain.column(hx, hz).h(), w, d});
            }
        }

        private int h(int x, int y, int z) {
            return Shapes.hash(x, y, z, seed);
        }

        @Override
        public void build(Placer p, CityLayout city) {
            final int b = groundY, cx = s.x(), cz = s.z();
            // 마을 공터
            p.forColumns(cx - 7, cz - 7, cx + 7, cz + 7, (x, z) -> {
                if (Math.hypot(x - cx, z - cz) > 7.3) return;
                int k = h(x, 1, z);
                BlockState g = switch (s.style()) {
                    case FARM -> k % 3 == 0 ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.GRAVEL.defaultBlockState();
                    case ELF -> k % 2 == 0 ? Blocks.MOSS_BLOCK.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState();
                    case FROST -> k % 3 == 0 ? Blocks.PACKED_ICE.defaultBlockState() : Blocks.SNOW_BLOCK.defaultBlockState();
                    case FISHER -> k % 3 == 0 ? Blocks.GRAVEL.defaultBlockState() : Blocks.SAND.defaultBlockState();
                    case REFUGEE -> k % 3 == 0 ? Blocks.GRAVEL.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState();
                };
                RoadSites.pad(p, x, z, b, g);
            });
            centerpiece(p, b, cx, cz);
            for (int[] q : houses) {
                path(p, cx, cz, q[0], q[1]);
                house(p, q);
            }
            extras(p, b, cx, cz);
        }

        /** 공터에서 집 앞까지 흙길 */
        private void path(Placer p, int x0, int z0, int x1, int z1) {
            int n = (int) Math.ceil(Math.hypot(x1 - x0, z1 - z0));
            for (int i = 0; i <= n; i++) {
                int x = x0 + (int) Math.round((x1 - x0) * i / (double) n), z = z0 + (int) Math.round((z1 - z0) * i / (double) n);
                for (int o = 0; o <= 1; o++) {
                    int px = x + (Math.abs(z1 - z0) > Math.abs(x1 - x0) ? o : 0), pz = z + (Math.abs(z1 - z0) > Math.abs(x1 - x0) ? 0 : o);
                    if (!p.contains(px, pz) || Math.hypot(px - x0, pz - z0) < 7.5) continue;
                    int top = p.naturalTop(px, pz);
                    BlockState t = p.get(px, top, pz);
                    if (!t.getFluidState().isEmpty()) continue;
                    p.clearAbove(px, pz, top + 1);
                    p.set(px, top, pz, s.style() == Style.FROST && h(px, 3, pz) % 2 == 0 ? Blocks.SNOW_BLOCK.defaultBlockState() : Blocks.DIRT_PATH.defaultBlockState());
                }
            }
        }

        private void house(Placer p, int[] q) {
            Direction front = Direction.values()[q[2]];
            int b = q[3], W = q[4], D = q[5];
            Frame f = new Frame(q[0], q[1], front, W, D);
            boolean stilt = s.style() == Style.FISHER;
            int fl = stilt ? 2 : 0;                                          // 어부 집은 기둥 위
            // 터
            for (int u = -1; u <= W; u++) for (int v = -2; v <= D; v++) {
                int x = f.x(u, v), z = f.z(u, v);
                if (p.contains(x, z)) RoadSites.pad(p, x, z, b, u >= 0 && u < W && v >= 0 && v < D && !stilt ? m.floor() : Blocks.GRASS_BLOCK.defaultBlockState());
            }
            // 벽·기둥·창
            for (int u = 0; u < W; u++) for (int v = 0; v < D; v++) {
                boolean edgeU = u == 0 || u == W - 1, edgeV = v == 0 || v == D - 1;
                boolean corner = edgeU && edgeV;
                if (stilt) {
                    f.set(p, b, u, fl, v, m.floor());
                    if (corner || (edgeU && v == D / 2)) for (int y = 1; y < fl; y++) f.set(p, b, u, y, v, m.post());
                }
                if (!edgeU && !edgeV) continue;
                for (int y = fl + 1; y <= fl + 4; y++) {
                    BlockState st = corner || y == fl + 4 ? m.post() : m.wall();
                    boolean window = !corner && y == fl + 2 && ((edgeU && v % 3 == 1) || (edgeV && u % 3 == 1 && !(v == 0 && Math.abs(u - W / 2) <= 1)));
                    if (window) st = Blocks.GLASS_PANE.defaultBlockState();
                    boolean door = v == 0 && u == W / 2 && y <= fl + 2;
                    if (door) st = Palette.door(f.back, y == fl + 2);
                    f.set(p, b, u, y, v, st);
                }
            }
            // 박공 지붕 (용마루가 앞뒤 방향)
            int half = (W + 1) / 2;
            for (int k = 0; k <= half; k++) {
                int y = fl + 4 + k;
                for (int v = -1; v <= D; v++) {
                    int uL = k - 1, uR = W - k;
                    if (uL >= uR) {
                        f.set(p, b, uL, y, v, m.ridge());
                        continue;
                    }
                    if (uL == uR - 1) {
                        f.set(p, b, uL, y, v, m.ridge());
                        f.set(p, b, uR, y, v, m.ridge());
                        continue;
                    }
                    f.set(p, b, uL, y, v, m.roofStairs().defaultBlockState().setValue(StairBlock.FACING, f.right));
                    f.set(p, b, uR, y, v, m.roofStairs().defaultBlockState().setValue(StairBlock.FACING, f.left));
                    if (k > 0 && (v == 0 || v == D - 1)) for (int u = uL + 1; u < uR; u++) f.set(p, b, u, y, v, m.wall());   // 박공벽
                }
            }
            // 가구
            f.set(p, b, 1, fl + 1, D - 2, Palette.bed(f.back, true));
            f.set(p, b, 1, fl + 1, D - 3, Palette.bed(f.back, false));
            f.set(p, b, W - 2, fl + 1, D - 2, Blocks.CRAFTING_TABLE.defaultBlockState());
            f.set(p, b, W - 3, fl + 1, D - 2, s.style() == Style.FROST ? Blocks.SMOKER.defaultBlockState() : Blocks.FURNACE.defaultBlockState());
            OldCapital.chest(p, f.x(W - 2, 1), b + fl + 1, f.z(W - 2, 1), f.left, seed ^ q[0], BuiltInLootTables.VILLAGE_PLAINS_HOUSE);
            f.set(p, b, 1, fl + 1, 1, Blocks.LANTERN.defaultBlockState());
            f.set(p, b, W / 2 - 2, 1, -1, m.fence());
            f.set(p, b, W / 2 - 2, 2, -1, Blocks.LANTERN.defaultBlockState());
            if (stilt) for (int y = 1; y <= fl; y++) {
                if (y < fl) f.set(p, b, W / 2, y, 0, m.post());
                f.set(p, b, W / 2, y, -1, Blocks.LADDER.defaultBlockState().setValue(net.minecraft.world.level.block.LadderBlock.FACING, f.front));
            }
        }

        private void centerpiece(Placer p, int b, int cx, int cz) {
            switch (s.style()) {
                case FARM -> {                                                   // 민트 지붕 우물
                    for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                        boolean rim = Math.abs(dx) == 1 || Math.abs(dz) == 1;
                        p.set(cx + dx, b, cz + dz, rim ? Blocks.STONE_BRICKS.defaultBlockState() : Blocks.WATER.defaultBlockState());
                        if (rim) p.set(cx + dx, b + 1, cz + dz, Blocks.STONE_BRICK_WALL.defaultBlockState());
                        if (!rim) for (int y = b - 3; y < b; y++) p.set(cx, y, cz, Blocks.WATER.defaultBlockState());
                    }
                    for (int sx = -1; sx <= 1; sx += 2) for (int sz = -1; sz <= 1; sz += 2) for (int y = 2; y <= 3; y++) p.set(cx + sx, b + y, cz + sz, Blocks.OAK_FENCE.defaultBlockState());
                    for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
                        p.set(cx + dx, b + 4, cz + dz, dx == 0 && dz == 0 ? Blocks.WAXED_OXIDIZED_CUT_COPPER.defaultBlockState() : Blocks.WAXED_OXIDIZED_CUT_COPPER_SLAB.defaultBlockState());
                    p.set(cx, b + 3, cz, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
                }
                case ELF -> {                                                    // 꽃 진달래 사당
                    p.set(cx, b, cz, Blocks.MOSS_BLOCK.defaultBlockState());
                    p.set(cx, b + 1, cz, Blocks.FLOWERING_AZALEA.defaultBlockState());
                    for (int i = 0; i < 6; i++) {
                        double a = Math.PI * 2 * i / 6;
                        int x = cx + (int) Math.round(Math.cos(a) * 4), z = cz + (int) Math.round(Math.sin(a) * 4);
                        p.set(x, b + 1, z, Blocks.BIRCH_FENCE.defaultBlockState());
                        p.set(x, b + 2, z, Blocks.LANTERN.defaultBlockState());
                        p.set(cx + (int) Math.round(Math.cos(a + 0.5) * 2), b + 1, cz + (int) Math.round(Math.sin(a + 0.5) * 2), Blocks.LILY_OF_THE_VALLEY.defaultBlockState());
                    }
                }
                case FROST -> {                                                  // 모닥불 + 가죽 건조대
                    p.set(cx, b + 1, cz, Blocks.CAMPFIRE.defaultBlockState());
                    for (int s2 = -1; s2 <= 1; s2 += 2) {
                        for (int y = 1; y <= 3; y++) {
                            p.set(cx + s2 * 4, b + y, cz - 2, Blocks.SPRUCE_FENCE.defaultBlockState());
                            p.set(cx + s2 * 4, b + y, cz + 2, Blocks.SPRUCE_FENCE.defaultBlockState());
                        }
                        for (int dz = -2; dz <= 2; dz++) p.set(cx + s2 * 4, b + 4, cz + dz, Blocks.SPRUCE_SLAB.defaultBlockState());
                        for (int dz = -1; dz <= 1; dz++) p.set(cx + s2 * 4, b + 3, cz + dz, Blocks.BROWN_WOOL.defaultBlockState());
                        p.set(cx + s2 * 2, b + 1, cz, Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, s2 > 0 ? Direction.EAST : Direction.WEST));
                    }
                }
                case FISHER -> {                                                 // 그물 건조대 + 통발 + 모닥불
                    p.set(cx, b + 1, cz, Blocks.CAMPFIRE.defaultBlockState());
                    for (int dx = -3; dx <= 3; dx++) {
                        if (Math.abs(dx) == 3) for (int y = 1; y <= 3; y++) p.set(cx + dx, b + y, cz - 4, Blocks.MANGROVE_FENCE.defaultBlockState());
                        else p.set(cx + dx, b + 3, cz - 4, Blocks.COBWEB.defaultBlockState());
                    }
                    p.set(cx + 3, b + 1, cz + 3, Blocks.BARREL.defaultBlockState());
                    p.set(cx + 4, b + 1, cz + 3, Blocks.BARREL.defaultBlockState());
                    p.set(cx - 3, b + 1, cz + 3, Blocks.COMPOSTER.defaultBlockState());
                }
                case REFUGEE -> {                                                // 모닥불 + 망루
                    p.set(cx, b + 1, cz, Blocks.CAMPFIRE.defaultBlockState());
                    int tx = cx + 5, tz = cz + 5;
                    for (int y = 1; y <= 7; y++) for (int sx = 0; sx <= 1; sx++) for (int sz = 0; sz <= 1; sz++)
                        p.set(tx + sx * 3, b + y, tz + sz * 3, Blocks.DARK_OAK_LOG.defaultBlockState());
                    for (int dx = 0; dx <= 3; dx++) for (int dz = 0; dz <= 3; dz++) {
                        p.set(tx + dx, b + 8, tz + dz, Blocks.DARK_OAK_PLANKS.defaultBlockState());
                        if (dx == 0 || dx == 3 || dz == 0 || dz == 3) p.set(tx + dx, b + 9, tz + dz, Blocks.DARK_OAK_FENCE.defaultBlockState());
                    }
                    for (int y = 1; y <= 8; y++) p.set(tx + 1, b + y, tz - 1, Blocks.LADDER.defaultBlockState().setValue(net.minecraft.world.level.block.LadderBlock.FACING, Direction.NORTH));
                    p.set(tx + 1, b + 8, tz, Blocks.AIR.defaultBlockState());
                    p.set(tx + 2, b + 10, tz + 2, Blocks.LANTERN.defaultBlockState());
                    p.set(tx + 3, b + 9, tz + 1, Royal.mintBanner(Direction.EAST));
                }
            }
        }

        /** 양식별 마을 바깥 장식: 밭, 꽃밭, 장작더미, 천막, 목책 */
        private void extras(Placer p, int b, int cx, int cz) {
            java.util.Random r = new java.util.Random(seed ^ 0xF1E1D);
            double a = r.nextDouble() * Math.PI * 2;
            int fx = cx + (int) Math.round(Math.cos(a) * 28), fz = cz + (int) Math.round(Math.sin(a) * 28);
            switch (s.style()) {
                case FARM -> {                                                   // 밀밭 9 x 9 (가운데 물길)
                    int g = KingdomTerrain.column(fx, fz).h();
                    for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) {
                        int x = fx + dx, z = fz + dz;
                        if (!p.contains(x, z)) continue;
                        RoadSites.pad(p, x, z, g, Blocks.FARMLAND.defaultBlockState());
                        if (dx == 0) p.set(x, g, z, Blocks.WATER.defaultBlockState());
                        else p.set(x, g + 1, z, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, h(x, 5, z) % 8));
                    }
                }
                case ELF -> {
                    for (int i = 0; i < 40; i++) {
                        int x = fx + r.nextInt(13) - 6, z = fz + r.nextInt(13) - 6;
                        if (!p.contains(x, z)) continue;
                        int top = p.naturalTop(x, z);
                        if (p.get(x, top, z).is(Blocks.GRASS_BLOCK)) p.set(x, top + 1, z, i % 3 == 0 ? Blocks.ALLIUM.defaultBlockState() : i % 3 == 1 ? Blocks.BLUE_ORCHID.defaultBlockState() : Blocks.OXEYE_DAISY.defaultBlockState());
                    }
                }
                case FROST, REFUGEE -> {                                         // 장작더미 / 천막
                    int g = KingdomTerrain.column(fx, fz).h();
                    for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                        int x = fx + dx, z = fz + dz;
                        if (!p.contains(x, z)) continue;
                        RoadSites.pad(p, x, z, g, Blocks.COARSE_DIRT.defaultBlockState());
                        if (s.style() == Style.FROST) {
                            if (Math.abs(dz) <= 1 && Math.abs(dx) <= 2) for (int y = 1; y <= 2 - Math.abs(dz); y++)
                                p.set(x, g + y, z, Blocks.SPRUCE_LOG.defaultBlockState().setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS, Direction.Axis.X));
                        } else {
                            int ad = Math.abs(dx);
                            for (int y = 1; y <= 3 - ad; y++) if (y == 3 - ad || Math.abs(dz) == 2) p.set(x, g + y, z, (dz + y) % 2 == 0 ? Blocks.BROWN_WOOL.defaultBlockState() : Blocks.WHITE_WOOL.defaultBlockState());
                        }
                    }
                }
                case FISHER -> {
                    for (int i = 0; i < 4; i++) p.set(fx + i, KingdomTerrain.column(fx + i, fz).h() + 1, fz, Blocks.BARREL.defaultBlockState());
                }
            }
            if (s.style() == Style.REFUGEE) {                                    // 목책 (드문드문)
                for (int i = 0; i < 90; i++) {
                    double aa = Math.PI * 2 * i / 90;
                    if (h(i, 9, 9) % 4 == 0) continue;
                    int x = cx + (int) Math.round(Math.cos(aa) * 31), z = cz + (int) Math.round(Math.sin(aa) * 31);
                    if (!p.contains(x, z)) continue;
                    int top = p.naturalTop(x, z);
                    for (int y = 1; y <= 3; y++) p.set(x, top + y, z, Blocks.DARK_OAK_LOG.defaultBlockState());
                }
            }
        }

        @Override
        public boolean needsSpawn() {
            return true;
        }

        @Override
        public void spawn(ServerLevel level, CityLayout city) {
            int n = 3 + Math.floorMod((int) seed, 3);
            for (int i = 0; i < n; i++) {
                Mob m = EntityType.VILLAGER.spawn(level, new BlockPos(s.x() + i - 1, groundY + 1, s.z() + 2), MobSpawnType.STRUCTURE);
                if (m == null) continue;
                m.setPersistenceRequired();
                m.setCustomName(Component.literal(s.name() + " " + s.style().people));
            }
            if (s.style() == Style.FARM) {
                for (int i = 0; i < 3; i++) EntityType.SHEEP.spawn(level, new BlockPos(s.x() - 9 + i, groundY + 1, s.z() - 9), MobSpawnType.STRUCTURE);
            }
        }

        @Override
        public long anchorChunk() {
            return ChunkPos.asLong(s.x() >> 4, s.z() >> 4);
        }
    }

    /** 위치 안내 (가까운 순) */
    public static String describe(double px, double pz, int max) {
        List<Spot> sorted = new ArrayList<>(spots());
        sorted.sort((a, b) -> Double.compare(Math.hypot(a.x() - px, a.z() - pz), Math.hypot(b.x() - px, b.z() - pz)));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(max, sorted.size()); i++) {
            Spot s = sorted.get(i);
            sb.append("\n§7- §f").append(s.name()).append(" §7(").append(s.style().title).append(") ")
                    .append(s.x()).append(", ").append(s.y() + 1).append(", ").append(s.z())
                    .append(" §8[").append(Math.round(Math.hypot(s.x() - px, s.z() - pz))).append("블록]");
        }
        return sb.toString();
    }
}
