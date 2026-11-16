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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SculkShriekerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 저주받은 숲 (W1) + 심연 균열 (W2) — 옛 왕도로 이어지는 어둠의 땅
 *
 * 저주받은 숲 (중심 = 남서 국경로와 마왕성 폐왕도가 갈리는 곳)
 *  - 숲 전체: 시들어 잎이 반쯤 떨어진 나무, 거미줄, 껍질 벗겨진 죽은 고목(맨가지·매달린 철창), 포드졸·거친 흙·영혼 흙 바닥,
 *    시든 덤불·위더 장미·버섯·뼈 무더기
 *  - 갈림길의 교수대, 검은 연못가 기둥 위 마녀의 오두막(가마솥·양조대·마녀·검은 고양이)
 *  - 저주받은 제단: 우는 흑요석 기둥 고리와 영혼 불, 가운데에 보스 "저주의 마녀왕"(boss.CursedBoss)
 * 심연 균열
 *  - 길이 260, 폭 최대 34 의 V 자 협곡이 Y -30 까지 쪼개짐. 벽은 층마다 계단처럼 꺾인 바위 턱
 *  - 바닥: 용암 강, 스컬크가 번진 고대 폐허(심층암 벽돌 기둥·영혼 등불), 고대 도시 보물 상자,
 *    "깨어날 수 있는" 스컬크 비명체 — 시끄러우면 심연의 감시자(워든)가 나타남
 *  - 협곡 위 사슬 다리 3개(하나는 끊김), 옆의 나선 계단 굴로 바닥까지 내려감 (15칸마다 협곡 쪽 창)
 */
public final class CursedLands implements BuildLayout {
    public static final int WX = KingdomTerrain.worldX(-1160), WZ = KingdomTerrain.worldZ(1110);
    public static final int RX = KingdomTerrain.worldX(-1540), RZ = KingdomTerrain.worldZ(1390);
    public static final int ALTAR_X = WX - 80, ALTAR_Z = WZ - 60, HUT_X = WX + 80, HUT_Z = WZ + 70;
    /** 균열 방향 (옛 왕도 쪽) */
    private static final double UX = -0.642, UZ = 0.766, PXV = -UZ, PZV = UX;
    private static final int HALF_LEN = 130, BOTTOM = -30;
    public static final int STAIR_X = RX + (int) Math.round(PXV * 34), STAIR_Z = RZ + (int) Math.round(PZV * 34);

    private final long seed;
    private final List<Feature> features = new ArrayList<>();
    private final Map<Long, List<Feature>> byChunk = new HashMap<>();
    private final List<ChunkPos> chunks = new ArrayList<>();
    private final int altarY, hutY;

    public CursedLands(long seed) {
        this.seed = seed;
        altarY = KingdomTerrain.column(ALTAR_X, ALTAR_Z).h();
        hutY = KingdomTerrain.column(HUT_X, HUT_Z).h();
        LinkedHashSet<Long> keys = new LinkedHashSet<>();
        for (int cx = (WX - 580) >> 4; cx <= (WX + 580) >> 4; cx++) {
            for (int cz = (WZ - 500) >> 4; cz <= (WZ + 500) >> 4; cz++) {
                int bx = (cx << 4) + 8, bz = (cz << 4) + 8;
                if (KingdomTerrain.column(bx, bz).kind() != KingdomTerrain.Kind.CURSED) continue;
                keys.add(ChunkPos.asLong(cx, cz));
                double r = rnd(cx, cz, 1);
                if (r < 0.45) {                                                                  // 죽은 고목
                    int tx = bx - 4 + (int) (rnd(cx, cz, 2) * 8), tz = bz - 4 + (int) (rnd(cx, cz, 3) * 8);
                    if (KingdomRoads.nearest(tx, tz, 10, null) < 0 && far(tx, tz)) features.add(new DeadTree(tx, tz, (long) cx * 73 + cz));
                }
            }
        }
        features.add(new Gallows());
        features.add(new WitchHut());
        features.add(new Altar());
        features.add(new RiftBridges());
        features.add(new RiftRuins());
        for (Feature f : features) {
            for (int x = (f.minX - 1) >> 4; x <= (f.maxX + 1) >> 4; x++)
                for (int z = (f.minZ - 1) >> 4; z <= (f.maxZ + 1) >> 4; z++)
                    byChunk.computeIfAbsent(ChunkPos.asLong(x, z), k -> new ArrayList<>()).add(f);
        }
        for (int x = (RX - 150) >> 4; x <= (RX + 150) >> 4; x++)
            for (int z = (RZ - 150) >> 4; z <= (RZ + 150) >> 4; z++) keys.add(ChunkPos.asLong(x, z));
        keys.addAll(byChunk.keySet());
        for (long k : keys) chunks.add(new ChunkPos(k));
    }

    private static boolean far(int x, int z) {
        return Math.hypot(x - ALTAR_X, z - ALTAR_Z) > 26 && Math.hypot(x - HUT_X, z - HUT_Z) > 26 && Math.hypot(x - WX, z - WZ) > 16;
    }

    /** 지어 둔 건물 자리 (길 고치기·나무 심기 제외) */
    public static boolean protectedAt(double x, double z) {
        return Math.hypot(x - ALTAR_X, z - ALTAR_Z) < 22 || Math.hypot(x - HUT_X, z - HUT_Z) < 20 || Math.hypot(x - RX, z - RZ) < 150;
    }

    public static boolean near(double x, double z) {
        return Math.hypot(x - WX, z - WZ) < 600 || Math.hypot(x - RX, z - RZ) < 170;
    }

    private static double rnd(long a, long b, long salt) {
        long h = a * 341873128712L + b * 132897987541L + salt * 0x9E3779B97F4A7C15L;
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        h *= 0xc4ceb9fe1a85ec53L;
        h ^= h >>> 33;
        return (h >>> 11) * (1.0 / (1L << 53));
    }

    private int h(int x, int y, int z) {
        return Shapes.hash(x, y, z, seed);
    }

    @Override public int cx() { return WX; }
    @Override public int cz() { return WZ; }
    @Override public int base() { return 68; }
    @Override public int radius() { return 0; }
    @Override public CityLayout city() { return null; }
    @Override public List<ChunkPos> chunkList() { return chunks; }
    @Override public List<Feature> featuresIn(ChunkPos cp) { return byChunk.getOrDefault(cp.toLong(), Collections.emptyList()); }
    @Override public int[] lightRange(int x, int z) { return null; }
    @Override public int shapeMinY() { return BOTTOM - 4; }
    @Override public int shapeMaxY() { return 110; }

    public int featureCount() {
        return features.size();
    }

    // ───────────────────────── 기둥 ─────────────────────────

    @Override
    public void buildColumn(Placer p, int x, int z) {
        rift(p, x, z);
        stair(p, x, z);
        KingdomTerrain.Column c = KingdomTerrain.column(x, z);
        if (c.kind() == KingdomTerrain.Kind.CURSED) wither(p, x, z, c);
    }

    /** 저주받은 숲 바닥과 시든 나무 */
    private void wither(Placer p, int x, int z, KingdomTerrain.Column c) {
        if (p.pass() != Placer.Pass.SOLID) return;
        int g = c.h();
        if (c.road() > 0 && c.roadEdge() <= 1) return;
        if (c.water() != Integer.MIN_VALUE && c.water() >= g) return;
        // 잎을 반쯤 떨어뜨리고 거미줄을 걺
        for (int y = g + 2; y <= g + 34; y++) {
            BlockState s = p.get(x, y, z);
            if (!s.is(net.minecraft.tags.BlockTags.LEAVES)) continue;
            int k = h(x, y, z) % 100;
            if (k < 55) p.set(x, y, z, Blocks.AIR.defaultBlockState());
            else if (k < 58) p.set(x, y, z, Blocks.COBWEB.defaultBlockState());
        }
        BlockState gs = p.get(x, g, z);
        if (!(gs.is(Blocks.GRASS_BLOCK) || gs.is(Blocks.PODZOL) || gs.is(Blocks.DIRT))) return;
        int k = h(x, g, z) % 1000;
        BlockState top = k < 220 ? Blocks.PODZOL.defaultBlockState() : k < 420 ? Blocks.COARSE_DIRT.defaultBlockState()
                : k < 470 ? Blocks.SOUL_SOIL.defaultBlockState() : k < 500 ? Blocks.ROOTED_DIRT.defaultBlockState() : null;
        if (top != null) p.set(x, g, z, top);
        BlockState up = p.get(x, g + 1, z);
        if (!up.isAir() && !up.canBeReplaced()) return;
        int q = h(x, g + 1, z) % 1000;
        BlockState plant = q < 40 ? Blocks.DEAD_BUSH.defaultBlockState() : q < 45 ? Blocks.WITHER_ROSE.defaultBlockState()
                : q < 65 ? Blocks.BROWN_MUSHROOM.defaultBlockState() : q < 75 ? Blocks.RED_MUSHROOM.defaultBlockState()
                : q < 100 ? Blocks.FERN.defaultBlockState() : q < 104 ? Blocks.COBWEB.defaultBlockState()
                : q < 107 ? Blocks.BONE_BLOCK.defaultBlockState() : q < 108 ? Blocks.SKELETON_SKULL.defaultBlockState() : Blocks.AIR.defaultBlockState();
        if ((top != null && top.is(Blocks.SOUL_SOIL)) && q < 300) plant = q < 20 ? Blocks.SOUL_FIRE.defaultBlockState() : plant;
        p.set(x, g + 1, z, plant);
    }

    // ── 심연 균열
    /** 균열 좌표: {축 위치 t, 가로 거리 lat} */
    private static double[] riftCoords(int x, int z) {
        double dx = x - RX, dz = z - RZ;
        return new double[]{dx * UX + dz * UZ, dx * PXV + dz * PZV};
    }

    private static double topHalf(double t, int x, int z) {
        double f = Math.max(0, 1 - Math.pow(t / HALF_LEN, 2));
        return 17 * Math.sqrt(f) + 4 * (Math.sin(x * 0.13 + z * 0.07) * 0.5 + Math.cos(z * 0.11 - x * 0.05) * 0.5) * f;
    }

    private static int bottomAt(double t) {
        return BOTTOM + (int) Math.round(95 * Math.pow(Math.abs(t) / HALF_LEN, 4));
    }

    private void rift(Placer p, int x, int z) {
        double[] tc = riftCoords(x, z);
        double t = tc[0], lat = tc[1];
        if (Math.abs(t) > HALF_LEN) return;
        double w0 = topHalf(t, x, z);
        if (w0 < 1 || Math.abs(lat) > w0) return;
        int top = KingdomTerrain.column(x, z).h() + 2;
        int bot = bottomAt(t);
        if (bot >= top - 3) return;
        // 이 기둥이 비는 가장 깊은 높이: 가로 거리가 멀수록 얕음 (8칸마다 꺾인 턱)
        double frac = Math.abs(lat) / Math.max(1, w0);                                       // 0 = 가운데, 1 = 가장자리
        double depth = (top - bot) * Math.pow(1 - frac, 0.55);
        int floorY = top - (int) Math.round(depth);
        floorY = Math.max(bot, floorY - Math.floorMod(floorY - bot, 8) / 3);                    // 계단 모양 턱
        p.clearAbove(x, z, floorY + 1);
        for (int y = floorY + 1; y <= top + 6; y++) p.set(x, y, z, Blocks.AIR.defaultBlockState());
        int k = h(x, floorY, z) % 20;
        boolean deep = floorY < 0;
        BlockState f = deep ? (k < 6 ? Blocks.SCULK.defaultBlockState() : k < 12 ? Blocks.DEEPSLATE.defaultBlockState() : k < 16 ? Blocks.COBBLED_DEEPSLATE.defaultBlockState() : Blocks.TUFF.defaultBlockState())
                : (k < 5 ? Blocks.BASALT.defaultBlockState() : k < 9 ? Blocks.BLACKSTONE.defaultBlockState() : k < 13 ? Blocks.TUFF.defaultBlockState() : Blocks.STONE.defaultBlockState());
        p.set(x, floorY, z, f);
        // 바닥 가운데 용암 강
        if (floorY == bot && Math.abs(lat) <= 1.6 && Math.abs(t) < HALF_LEN - 30) {
            p.set(x, floorY - 1, z, Blocks.MAGMA_BLOCK.defaultBlockState());
            p.set(x, floorY, z, Blocks.LAVA.defaultBlockState());
        } else if (deep && k == 19) {
            p.set(x, floorY + 1, z, Blocks.SCULK_VEIN.defaultBlockState().setValue(net.minecraft.world.level.block.MultifaceBlock.getFaceProperty(Direction.DOWN), true));
        } else if (!deep && k == 18 && Math.abs(lat) > 3) {
            p.set(x, floorY + 1, z, Blocks.POINTED_DRIPSTONE.defaultBlockState());
        }
        // 벽 위쪽 가장자리 무너진 돌
        if (frac > 0.9 && h(x, 3, z) % 5 == 0) p.set(x, top - 1, z, Blocks.COBBLED_DEEPSLATE.defaultBlockState());
    }

    /** 균열 옆 나선 계단 굴: 땅에서 바닥까지, 15칸마다 협곡 쪽으로 창 */
    private void stair(Placer p, int x, int z) {
        int dx = x - STAIR_X, dz = z - STAIR_Z;
        double d = Math.hypot(dx, dz);
        int top = KingdomTerrain.column(STAIR_X, STAIR_Z).h();
        // 창 굴 (계단 → 협곡, 가로 방향)
        double along = -(dx * PXV + dz * PZV), side = Math.abs(dx * UX + dz * UZ);
        if (along > 0 && along < 34 && side <= 1.5) {
            for (int y = BOTTOM; y < top; y++) {
                if (Math.floorMod(y - BOTTOM, 15) == 0 && y + 3 < top) {
                    p.set(x, y, z, Blocks.POLISHED_DEEPSLATE.defaultBlockState());
                    for (int k = 1; k <= 3; k++) {
                        BlockState s = p.get(x, y + k, z);
                        if (!s.is(Blocks.LAVA)) p.set(x, y + k, z, Blocks.AIR.defaultBlockState());
                    }
                }
            }
        }
        if (d > 5.2) return;
        int bot = BOTTOM;
        for (int y = bot + 1; y <= top + 3; y++) p.set(x, y, z, d <= 1.5 ? Blocks.DEEPSLATE_BRICKS.defaultBlockState() : Blocks.AIR.defaultBlockState());
        if (d > 4.4 && d <= 5.2) {                                                            // 지상 입구 둘레 낮은 담
            p.set(x, top + 1, z, (int) Math.round(Math.toDegrees(Math.atan2(dz, dx))) % 60 < 15 ? Blocks.AIR.defaultBlockState() : Blocks.DEEPSLATE_BRICK_WALL.defaultBlockState());
        }
        p.set(x, bot, z, Blocks.DEEPSLATE_TILES.defaultBlockState());
        if (d <= 1.5) {
            for (int y = bot + 4; y < top; y += 9) p.set(x, y, z, Blocks.SOUL_LANTERN.defaultBlockState());
            p.set(x, top + 4, z, Blocks.SOUL_LANTERN.defaultBlockState());
            return;
        }
        double ang = Math.atan2(dz, dx);
        if (ang < 0) ang += Math.PI * 2;
        for (int n = 0; ; n++) {
            double hy = top - (ang / (Math.PI * 2) + n) * 10;
            if (hy < bot) break;
            int y = (int) Math.floor(hy);
            boolean half = hy - y >= 0.5;
            p.set(x, half ? y + 1 : y, z, half ? Blocks.COBBLED_DEEPSLATE_SLAB.defaultBlockState() : Blocks.COBBLED_DEEPSLATE.defaultBlockState());
        }
    }

    // ───────────────────────── 저주받은 숲 건물 ─────────────────────────

    /** 껍질 벗겨진 죽은 고목: 잎 없는 비틀린 가지, 거미줄, 가끔 매달린 철창(해골) */
    final class DeadTree extends Feature {
        final int tx, tz;
        final long ts;

        DeadTree(int tx, int tz, long ts) {
            super(tx - 9, tz - 9, tx + 9, tz + 9);
            this.tx = tx;
            this.tz = tz;
            this.ts = ts;
        }

        @Override
        public void build(Placer p, CityLayout city) {
            int g = KingdomTerrain.column(tx, tz).h();
            int H = 11 + (int) (rnd(ts, 1, 9) * 9);
            Block wood = rnd(ts, 2, 9) < 0.6 ? Blocks.STRIPPED_DARK_OAK_LOG : Blocks.DARK_OAK_LOG;
            for (int y = 1; y <= H; y++) {
                p.set(tx, g + y, tz, wood.defaultBlockState());
                if (y < 4) for (Direction d : Direction.Plane.HORIZONTAL) if (rnd(ts, y * 4 + d.ordinal(), 9) < 0.5) p.set(tx + d.getStepX(), g + y, tz + d.getStepZ(), wood.defaultBlockState());
            }
            int branches = 3 + (int) (rnd(ts, 3, 9) * 3);
            for (int i = 0; i < branches; i++) {
                double ang = rnd(ts, 10 + i, 9) * Math.PI * 2;
                int by = g + H / 2 + (int) (rnd(ts, 20 + i, 9) * (H / 2));
                int len = 3 + (int) (rnd(ts, 30 + i, 9) * 5);
                Direction.Axis ax = Math.abs(Math.cos(ang)) > Math.abs(Math.sin(ang)) ? Direction.Axis.X : Direction.Axis.Z;
                int ex = tx, ez = tz, ey = by;
                for (int t = 1; t <= len; t++) {
                    ex = tx + (int) Math.round(Math.cos(ang) * t);
                    ez = tz + (int) Math.round(Math.sin(ang) * t);
                    ey = by + t / 2 + (int) Math.round(Math.sin(t * 1.3 + i));
                    p.set(ex, ey, ez, wood.defaultBlockState().setValue(RotatedPillarBlock.AXIS, ax));
                }
                if (rnd(ts, 40 + i, 9) < 0.3) p.set(ex, ey + 1, ez, Blocks.COBWEB.defaultBlockState());
                if (i == 0 && rnd(ts, 50, 9) < 0.25) {                                          // 매달린 철창
                    for (int k = 1; k <= 3; k++) p.set(ex, ey - k, ez, Blocks.CHAIN.defaultBlockState());
                    p.set(ex, ey - 4, ez, Blocks.IRON_BARS.defaultBlockState());
                    p.set(ex, ey - 5, ez, Blocks.SKELETON_SKULL.defaultBlockState());
                    p.set(ex, ey - 6, ez, Blocks.IRON_BARS.defaultBlockState());
                }
            }
        }
    }

    /** 갈림길의 교수대: 두 길이 갈리는 곳, 검은 깃발 */
    final class Gallows extends Feature {
        Gallows() {
            super(WX - 14, WZ - 14, WX + 14, WZ + 14);
        }

        @Override
        public void build(Placer p, CityLayout city) {
            int x0 = WX + 8, z0 = WZ + 10;
            int g = KingdomTerrain.column(x0 + 2, z0).h();
            for (int y = 1; y <= 6; y++) {
                p.set(x0, g + y, z0, Blocks.DARK_OAK_LOG.defaultBlockState());
                p.set(x0 + 4, g + y, z0, Blocks.DARK_OAK_LOG.defaultBlockState());
            }
            for (int x = x0; x <= x0 + 4; x++) p.set(x, g + 7, z0, Blocks.DARK_OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
            for (int k = 1; k <= 2; k++) p.set(x0 + 2, g + 7 - k, z0, Blocks.CHAIN.defaultBlockState());
            p.set(x0 + 2, g + 4, z0, Blocks.SKELETON_SKULL.defaultBlockState());
            for (int x = x0; x <= x0 + 4; x++) p.set(x, g + 1, z0 + 1, Blocks.DARK_OAK_SLAB.defaultBlockState());
            int bg = KingdomTerrain.column(WX - 10, WZ - 10).h();
            for (int y = 1; y <= 3; y++) p.set(WX - 10, bg + y, WZ - 10, Blocks.DARK_OAK_FENCE.defaultBlockState());
            p.set(WX - 10, bg + 4, WZ - 10, Blocks.BLACK_BANNER.defaultBlockState());
        }
    }

    /** 검은 연못가 기둥 위 마녀의 오두막 */
    final class WitchHut extends Feature {
        WitchHut() {
            super(HUT_X - 14, HUT_Z - 14, HUT_X + 14, HUT_Z + 14);
        }

        @Override
        public void build(Placer p, CityLayout city) {
            int g = hutY;
            // 검은 연못 (오두막 남쪽)
            p.forColumns(HUT_X - 12, HUT_Z - 2, HUT_X + 12, HUT_Z + 13, (x, z) -> {
                double d = Math.hypot((x - HUT_X) / 1.3, z - (HUT_Z + 6));
                if (d > 7.5) return;
                p.clearAbove(x, z, g + 1);
                if (d > 6.5) {
                    p.set(x, g, z, Blocks.MUD.defaultBlockState());
                    return;
                }
                p.set(x, g - 3, z, Blocks.MUD.defaultBlockState());
                p.set(x, g - 2, z, Blocks.WATER.defaultBlockState());
                p.set(x, g - 1, z, Blocks.WATER.defaultBlockState());
                p.set(x, g, z, Blocks.WATER.defaultBlockState());
                if (h(x, 2, z) % 9 == 0) p.set(x, g + 1, z, Blocks.LILY_PAD.defaultBlockState());
            });
            // 기둥 위 오두막 (7x9, 바닥 g+4)
            int fy = g + 4, x0 = HUT_X - 3, z0 = HUT_Z - 4;
            for (int x = x0; x <= x0 + 6; x++) for (int z = z0; z <= z0 + 8; z++) {
                boolean ex = x == x0 || x == x0 + 6, ez = z == z0 || z == z0 + 8;
                if (ex && ez) for (int y = g - 2; y < fy; y++) p.set(x, y, z, Blocks.DARK_OAK_LOG.defaultBlockState());
                p.set(x, fy, z, Blocks.SPRUCE_PLANKS.defaultBlockState());
                for (int y = fy + 1; y <= fy + 4; y++) {
                    BlockState s = (ex || ez) ? ((ex && ez) ? Blocks.DARK_OAK_LOG.defaultBlockState() : Blocks.SPRUCE_PLANKS.defaultBlockState()) : Blocks.AIR.defaultBlockState();
                    if ((ex || ez) && !(ex && ez) && y == fy + 2 && ((x + z) & 3) == 0) s = Blocks.BROWN_STAINED_GLASS_PANE.defaultBlockState();
                    if (z == z0 + 8 && x == x0 + 3 && y <= fy + 2) s = Blocks.AIR.defaultBlockState();
                    p.set(x, y, z, s);
                }
                p.set(x, fy + 5, z, Blocks.DARK_OAK_SLAB.defaultBlockState());
            }
            for (int x = x0 - 1; x <= x0 + 7; x++) for (int z = z0 - 1; z <= z0 + 9; z++)
                if (x == x0 - 1 || x == x0 + 7 || z == z0 - 1 || z == z0 + 9) p.set(x, fy + 5, z, Blocks.SPRUCE_STAIRS.defaultBlockState());
            for (int y = g - 2; y < fy; y++) p.set(x0 + 3, y, z0 + 8, Blocks.DARK_OAK_LOG.defaultBlockState());
            for (int y = g + 1; y <= fy; y++) p.set(x0 + 3, y, z0 + 9, Blocks.LADDER.defaultBlockState().setValue(net.minecraft.world.level.block.LadderBlock.FACING, Direction.SOUTH));
            p.set(x0 + 1, fy + 1, z0 + 1, Blocks.CAULDRON.defaultBlockState());
            p.set(x0 + 2, fy + 1, z0 + 1, Blocks.BREWING_STAND.defaultBlockState());
            p.set(x0 + 5, fy + 1, z0 + 1, Blocks.CRAFTING_TABLE.defaultBlockState());
            p.set(x0 + 5, fy + 1, z0 + 3, Blocks.FLOWER_POT.defaultBlockState());
            p.set(x0 + 1, fy + 1, z0 + 4, Blocks.POTTED_RED_MUSHROOM.defaultBlockState());
            OldCapital.chest(p, x0 + 5, fy + 1, z0 + 6, Direction.WEST, seed ^ 0x77, BuiltInLootTables.SIMPLE_DUNGEON);
            p.set(x0 + 3, fy + 4, z0 + 4, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
            for (int k = 0; k < 4; k++) p.set(x0 - 1 + k * 3, fy + 4, z0 - 2, Blocks.SKELETON_SKULL.defaultBlockState());
        }

        @Override public boolean needsSpawn() { return true; }

        @Override
        public void spawn(ServerLevel level, CityLayout city) {
            Mob w = EntityType.WITCH.spawn(level, new BlockPos(HUT_X, hutY + 5, HUT_Z), MobSpawnType.STRUCTURE);
            if (w != null) {
                w.setPersistenceRequired();
                w.setCustomName(Component.literal("늪의 마녀"));
            }
            Mob c = EntityType.CAT.spawn(level, new BlockPos(HUT_X + 1, hutY + 5, HUT_Z + 1), MobSpawnType.STRUCTURE);
            if (c instanceof net.minecraft.world.entity.animal.Cat cat) {
                cat.setPersistenceRequired();
                level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.CAT_VARIANT)
                        .getHolder(net.minecraft.world.entity.animal.CatVariant.ALL_BLACK).ifPresent(cat::setVariant);
            }
        }
    }

    /** 저주받은 제단: 흑암 원형 마당, 우는 흑요석 기둥 8개와 영혼 불, 가운데 해골 제단 (보스 자리) */
    final class Altar extends Feature {
        Altar() {
            super(ALTAR_X - 16, ALTAR_Z - 16, ALTAR_X + 16, ALTAR_Z + 16);
        }

        @Override
        public void build(Placer p, CityLayout city) {
            int g = altarY;
            p.forColumns(ALTAR_X - 15, ALTAR_Z - 15, ALTAR_X + 15, ALTAR_Z + 15, (x, z) -> {
                double d = Math.hypot(x - ALTAR_X, z - ALTAR_Z);
                if (d > 15) return;
                RoadSites.pad(p, x, z, g, d < 3 ? Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState()
                        : (int) d % 4 == 0 ? Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState()
                        : h(x, 1, z) % 3 == 0 ? Blocks.SOUL_SOIL.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState());
                for (int y = g + 1; y <= g + 14; y++) p.set(x, y, z, Blocks.AIR.defaultBlockState());
            });
            for (int i = 0; i < 8; i++) {
                double a = Math.PI * 2 * i / 8;
                int x = ALTAR_X + (int) Math.round(Math.cos(a) * 12), z = ALTAR_Z + (int) Math.round(Math.sin(a) * 12);
                for (int y = 1; y <= 6; y++) p.set(x, g + y, z, y % 3 == 0 ? Blocks.OBSIDIAN.defaultBlockState() : Blocks.CRYING_OBSIDIAN.defaultBlockState());
                p.set(x, g + 7, z, Blocks.SOUL_CAMPFIRE.defaultBlockState());
            }
            p.set(ALTAR_X, g + 1, ALTAR_Z, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
            p.set(ALTAR_X, g + 2, ALTAR_Z, Blocks.WITHER_SKELETON_SKULL.defaultBlockState());
            for (Direction d : Direction.Plane.HORIZONTAL) {
                p.set(ALTAR_X + d.getStepX() * 2, g + 1, ALTAR_Z + d.getStepZ() * 2, Blocks.CANDLE.defaultBlockState());
                p.set(ALTAR_X + d.getStepX() * 5, g + 1, ALTAR_Z + d.getStepZ() * 5, Blocks.WITHER_ROSE.defaultBlockState());
            }
            OldCapital.chest(p, ALTAR_X + 3, g + 1, ALTAR_Z + 3, Direction.NORTH, seed ^ 0x78, BuiltInLootTables.WOODLAND_MANSION);
        }
    }

    // ───────────────────────── 심연 균열 건물 ─────────────────────────

    /** 협곡 위 사슬 다리 3개 (가운데는 끊김) */
    final class RiftBridges extends Feature {
        RiftBridges() {
            super(RX - 140, RZ - 140, RX + 140, RZ + 140);
        }

        @Override
        public void build(Placer p, CityLayout city) {
            for (int b = -1; b <= 1; b++) {
                double t = b * 60;
                double w = 17 * Math.sqrt(Math.max(0, 1 - Math.pow(t / HALF_LEN, 2))) + 5;
                double cx = RX + UX * t, cz = RZ + UZ * t;
                int ya = KingdomTerrain.column((int) (cx - PXV * w), (int) (cz - PZV * w)).h();
                int yb = KingdomTerrain.column((int) (cx + PXV * w), (int) (cz + PZV * w)).h();
                int y = Math.max(ya, yb);
                Direction.Axis axis = Math.abs(PXV) > Math.abs(PZV) ? Direction.Axis.X : Direction.Axis.Z;
                for (double s = -w; s <= w; s += 0.4) {
                    if (b == 0 && Math.abs(s) < 3) continue;
                    for (int k = -1; k <= 1; k++) {
                        int x = (int) Math.round(cx + PXV * s + UX * k), z = (int) Math.round(cz + PZV * s + UZ * k);
                        p.set(x, y, z, k == 0 ? Blocks.DARK_OAK_PLANKS.defaultBlockState() : Blocks.DARK_OAK_SLAB.defaultBlockState()
                                .setValue(net.minecraft.world.level.block.SlabBlock.TYPE, net.minecraft.world.level.block.state.properties.SlabType.TOP));
                    }
                    for (int k = -2; k <= 2; k += 4) {
                        int x = (int) Math.round(cx + PXV * s + UX * k), z = (int) Math.round(cz + PZV * s + UZ * k);
                        p.set(x, y + 1, z, Blocks.CHAIN.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis));
                    }
                }
            }
        }
    }

    /** 균열 바닥의 고대 폐허: 심층암 벽돌 기둥, 영혼 등불, 보물 상자, 깨어날 수 있는 스컬크 비명체 */
    final class RiftRuins extends Feature {
        RiftRuins() {
            super(RX - 30, RZ - 30, RX + 30, RZ + 30);
        }

        @Override
        public void build(Placer p, CityLayout city) {
            int y = BOTTOM;
            for (int i = -2; i <= 2; i++) {
                double t = i * 9;
                for (int side = -1; side <= 1; side += 2) {
                    int x = (int) Math.round(RX + UX * t + PXV * side * 3), z = (int) Math.round(RZ + UZ * t + PZV * side * 3);
                    int hgt = 4 + Math.floorMod(i * 3 + side, 5);
                    for (int k = 1; k <= hgt; k++) p.set(x, y + k, z, k == hgt ? Blocks.CHISELED_DEEPSLATE.defaultBlockState() : Blocks.DEEPSLATE_BRICKS.defaultBlockState());
                    if (hgt >= 6) p.set(x, y + hgt + 1, z, Blocks.SOUL_LANTERN.defaultBlockState());
                }
            }
            int[][] shriek = {{-20, 2}, {0, -2}, {20, 2}};
            for (int[] s : shriek) {
                int x = (int) Math.round(RX + UX * s[0] + PXV * s[1]), z = (int) Math.round(RZ + UZ * s[0] + PZV * s[1]);
                p.set(x, y, z, Blocks.SCULK.defaultBlockState());
                p.set(x, y + 1, z, Blocks.SCULK_SHRIEKER.defaultBlockState().setValue(SculkShriekerBlock.CAN_SUMMON, true));
            }
            int[][] sensors = {{-12, -2}, {10, 2}, {28, -2}, {-28, 2}};
            for (int[] s : sensors) {
                int x = (int) Math.round(RX + UX * s[0] + PXV * s[1]), z = (int) Math.round(RZ + UZ * s[0] + PZV * s[1]);
                p.set(x, y, z, Blocks.SCULK.defaultBlockState());
                p.set(x, y + 1, z, Blocks.SCULK_SENSOR.defaultBlockState());
            }
            int cx1 = (int) Math.round(RX + UX * 5 + PXV * 2.5), cz1 = (int) Math.round(RZ + UZ * 5 + PZV * 2.5);
            int cx2 = (int) Math.round(RX - UX * 5 - PXV * 2.5), cz2 = (int) Math.round(RZ - UZ * 5 - PZV * 2.5);
            OldCapital.chest(p, cx1, y + 1, cz1, Direction.NORTH, seed ^ 0x79, BuiltInLootTables.ANCIENT_CITY);
            OldCapital.chest(p, cx2, y + 1, cz2, Direction.SOUTH, seed ^ 0x7A, BuiltInLootTables.ANCIENT_CITY_ICE_BOX);
        }
    }
}
