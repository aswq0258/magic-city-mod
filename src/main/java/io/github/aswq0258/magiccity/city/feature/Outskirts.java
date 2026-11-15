package io.github.aswq0258.magiccity.city.feature;

import io.github.aswq0258.magiccity.build.Placer;
import io.github.aswq0258.magiccity.city.Chateau;
import io.github.aswq0258.magiccity.city.CityLayout;
import io.github.aswq0258.magiccity.city.Deco;
import io.github.aswq0258.magiccity.city.Palette;
import io.github.aswq0258.magiccity.city.Royal;
import io.github.aswq0258.magiccity.city.Shapes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;

import static io.github.aswq0258.magiccity.city.Palette.*;

/**
 * 성벽 바깥 세계의 건물들 (모두 Feature): 국경 요새, 광산 입구, 풍차, 붉은 헛간, 목장, 벌목장, 항구, 초소, 묘지.
 * 각 건물의 groundY 는 CityLayout 이 평평하게 다진 지형 높이로 정해 줍니다.
 */
public final class Outskirts {
    private Outskirts() {}

    static final BlockState SPRUCE = Blocks.SPRUCE_PLANKS.defaultBlockState();
    static final BlockState SPRUCE_LOG = Blocks.SPRUCE_LOG.defaultBlockState();
    static final BlockState SPRUCE_FENCE = Blocks.SPRUCE_FENCE.defaultBlockState();
    static final BlockState STONE_BRICK = Blocks.STONE_BRICKS.defaultBlockState();
    static final BlockState COBBLE = Blocks.COBBLESTONE.defaultBlockState();
    static final BlockState OAK_LOG = Blocks.OAK_LOG.defaultBlockState();
    static final BlockState OAK_LEAVES = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
    static final BlockState SPRUCE_LEAVES = Blocks.SPRUCE_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);

    static BlockState log(Direction.Axis axis) {
        return SPRUCE_LOG.setValue(RotatedPillarBlock.AXIS, axis);
    }

    // ───────────────────────── 나무 ─────────────────────────

    public static void oakTree(Placer p, int x, int z, int b, int h) {
        p.forColumns(x - 2, z - 2, x + 2, z + 2, (xx, zz) -> {
            int dx = Math.abs(xx - x), dz = Math.abs(zz - z);
            for (int y = b + h - 2; y <= b + h + 1; y++) {
                boolean top = y >= b + h;
                if (top ? dx + dz <= 1 + (y == b + h ? 1 : 0) : !(dx == 2 && dz == 2)) {
                    if (dx == 0 && dz == 0 && y <= b + h) continue;
                    p.set(xx, y, zz, OAK_LEAVES);
                }
            }
        });
        for (int y = b + 1; y <= b + h; y++) p.set(x, y, z, OAK_LOG);
    }

    public static void spruceTree(Placer p, int x, int z, int b, int h) {
        p.forColumns(x - 3, z - 3, x + 3, z + 3, (xx, zz) -> {
            int dx = Math.abs(xx - x), dz = Math.abs(zz - z);
            for (int k = 2; k <= h + 1; k++) {
                int rad = Math.max(0, (h + 1 - k) / 3 + ((k & 1) == 0 ? 1 : 0));
                if (rad > 3) rad = 3;
                if (dx + dz <= rad && !(dx == 0 && dz == 0 && k <= h)) p.set(xx, b + k, zz, SPRUCE_LEAVES);
            }
        });
        for (int y = b + 1; y <= b + h; y++) p.set(x, y, z, SPRUCE_LOG);
        p.set(x, b + h + 2, z, SPRUCE_LEAVES);
    }

    /** 통나무 오두막 (가문비 나무, 돌 기초, 경사 지붕) — 벌목장·항구·요새 막사에 공용 */
    static void cabin(Placer p, Frame f, int b, BlockState roofSlab, int h) {
        for (int u = 0; u < f.width; u++) {
            for (int v = 0; v < f.depth; v++) {
                boolean edge = u == 0 || v == 0 || u == f.width - 1 || v == f.depth - 1;
                boolean corner = (u == 0 || u == f.width - 1) && (v == 0 || v == f.depth - 1);
                f.set(p, b, u, 0, v, edge ? COBBLE : SPRUCE);
                for (int ly = 1; ly <= h; ly++) {
                    BlockState s = AIR;
                    if (edge) {
                        s = corner ? SPRUCE_LOG : SPRUCE;
                        if (v == 0 && u == f.width / 2 && ly <= 2) s = AIR;
                        else if (!corner && ly == 2 && (u % 3 == 1 || v % 3 == 1)) s = Blocks.GLASS_PANE.defaultBlockState();
                    }
                    f.set(p, b, u, ly, v, s);
                }
            }
        }
        Chateau.hipRoof(p, f.minX, f.minZ, f.maxX, f.maxZ, b + h + 1, 6, 0, 0, 0, Blocks.SPRUCE_PLANKS.defaultBlockState(),
                d -> Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, d), roofSlab);
        f.set(p, b, f.width / 2, h - 1, 1, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
    }

    // ───────────────────────── 북쪽: 국경 요새 ─────────────────────────

    /** 북쪽 길을 막아선 돌 요새: 성벽·총안, 길 위 성문, 모서리 탑 2개, 막사, 모닥불, 깃발 */
    public static class BorderFort extends Feature {
        private final int cx, cz, hw, hd;

        public BorderFort(int cx, int cz, int hw, int hd) {
            super(cx - hw - 4, cz - hd - 4, cx + hw + 4, cz + hd + 4);
            this.cx = cx;
            this.cz = cz;
            this.hw = hw;
            this.hd = hd;
        }

        @Override
        public void build(Placer p, CityLayout city) {
            final int b = groundY;
            p.forColumns(cx - hw, cz - hd, cx + hw, cz + hd, (x, z) -> {
                int dx = x - cx, dz = z - cz;
                boolean edge = Math.abs(dx) == hw || Math.abs(dz) == hd;
                boolean gate = Math.abs(dx) <= 3 && Math.abs(dz) == hd;
                p.set(x, b, z, Math.abs(dx) <= 3 ? Blocks.GRAVEL.defaultBlockState() : COBBLE);
                if (!edge) return;
                for (int ly = 1; ly <= 7; ly++) {
                    BlockState s = (ly == 7) ? Blocks.STONE_BRICK_SLAB.defaultBlockState() : Shapes.hash(x, ly, z, 5) % 4 == 0 ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState() : STONE_BRICK;
                    if (gate && ly <= 5) s = AIR;
                    if (gate && ly == 6) s = log(Direction.Axis.X);
                    p.set(x, b + ly, z, s);
                }
                if (((dx + dz) & 1) == 0 && !gate) p.set(x, b + 8, z, STONE_BRICK);
            });
            for (int sx = -1; sx <= 1; sx += 2) {
                for (int sz = -1; sz <= 1; sz += 2) {
                    if (sx == sz) continue;
                    int tx = cx + sx * hw, tz = cz + sz * hd;
                    for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                        boolean e = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                        for (int ly = 0; ly <= 14; ly++) p.set(tx + dx, b + ly, tz + dz, e || ly == 14 ? STONE_BRICK : AIR);
                        if (e && ((dx + dz) & 1) == 0) p.set(tx + dx, b + 15, tz + dz, STONE_BRICK);
                    }
                    for (int ly = 1; ly < 14; ly++) p.set(tx, b + ly, tz + 1, Palette.ladder(Direction.NORTH));
                    p.set(tx, b + 15, tz, Royal.DARK_FENCE);
                    p.set(tx, b + 16, tz, Royal.DARK_FENCE);
                    p.set(tx, b + 17, tz, Royal.DARK_FENCE);
                    p.set(tx + 1, b + 16, tz, Royal.redBanner(Direction.EAST));
                }
            }
            cabin(p, new Frame(cx - hw + 8, cz, Direction.EAST, 9, 7), b, Blocks.STONE_BRICKS.defaultBlockState(), 4);
            cabin(p, new Frame(cx + hw - 8, cz - 3, Direction.WEST, 9, 7), b, Blocks.STONE_BRICKS.defaultBlockState(), 4);
            p.set(cx + 8, b + 1, cz + hd - 4, Deco.CAMPFIRE);
            p.set(cx - 8, b + 1, cz + hd - 4, Blocks.HAY_BLOCK.defaultBlockState());
            p.set(cx - 9, b + 1, cz + hd - 4, Blocks.TARGET.defaultBlockState());
            p.set(cx + 10, b + 1, cz - hd + 3, Blocks.GRINDSTONE.defaultBlockState());
            p.set(cx + 11, b + 1, cz - hd + 3, Blocks.ANVIL.defaultBlockState());
        }
    }

    // ───────────────────────── 북쪽: 광산 ─────────────────────────

    /** 산 속으로 뚫린 광산: 나무 버팀목 갱도(북쪽으로), 레일, 광석 박힌 벽, 랜턴, 앞마당의 광석 더미와 창고 */
    public static class Mine extends Feature {
        private final int px, pz, len;

        /** (px, pz) = 갱도 입구 가운데, 북쪽(-z)으로 len 만큼 */
        public Mine(int px, int pz, int len) {
            super(px - 14, pz - len - 2, px + 14, pz + 16);
            this.px = px;
            this.pz = pz;
            this.len = len;
        }

        @Override
        public void build(Placer p, CityLayout city) {
            final int b = groundY;
            BlockState[] ores = {Blocks.IRON_ORE.defaultBlockState(), Blocks.COAL_ORE.defaultBlockState(), Blocks.COPPER_ORE.defaultBlockState(),
                    Blocks.GOLD_ORE.defaultBlockState(), Blocks.LAPIS_ORE.defaultBlockState(), Blocks.REDSTONE_ORE.defaultBlockState()};
            for (int k = 0; k <= len; k++) {
                int z = pz - k;
                for (int dx = -3; dx <= 3; dx++) {
                    int x = px + dx;
                    int h = Shapes.hash(x, k, z, 9);
                    boolean wall = Math.abs(dx) == 3;
                    p.set(x, b, z, wall ? Blocks.STONE.defaultBlockState() : Blocks.GRAVEL.defaultBlockState());
                    for (int ly = 1; ly <= 6; ly++) {
                        BlockState s = AIR;
                        if (wall || ly == 6) s = h % 7 == 0 && ly < 6 ? ores[(h >> 3) % ores.length] : Blocks.STONE.defaultBlockState();
                        if (k % 5 == 0) {                                                        // 버팀목
                            if (Math.abs(dx) == 2 && ly <= 4) s = SPRUCE_LOG;
                            if (Math.abs(dx) <= 2 && ly == 5) s = log(Direction.Axis.X);
                        }
                        p.set(x, b + ly, z, s);
                    }
                }
                p.set(px, b + 1, z, Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE, RailShape.NORTH_SOUTH));
                if (k % 5 == 0) p.set(px + 1, b + 4, z, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
            }
            // 입구 틀
            for (int ly = 1; ly <= 6; ly++) {
                p.set(px - 4, b + ly, pz, SPRUCE_LOG);
                p.set(px + 4, b + ly, pz, SPRUCE_LOG);
            }
            for (int dx = -4; dx <= 4; dx++) p.set(px + dx, b + 7, pz, log(Direction.Axis.X));
            p.set(px, b + 8, pz, Blocks.LANTERN.defaultBlockState());
            p.set(px, b + 1, pz - len, Deco.chest(Direction.SOUTH));
            // 앞마당: 광석 더미, 레일 연장, 창고
            for (int k = 1; k <= 10; k++) p.set(px, b + 1, pz + k, Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE, RailShape.NORTH_SOUTH));
            for (int i = 0; i < 9; i++) {
                int x = px - 8 + (i % 3), z = pz + 4 + i / 3, hh = 1 + (i % 2);
                for (int ly = 1; ly <= hh; ly++) p.set(x, b + ly, z, i % 4 == 0 ? Blocks.RAW_IRON_BLOCK.defaultBlockState() : Blocks.GRAVEL.defaultBlockState());
            }
            cabin(p, new Frame(px + 9, pz + 8, Direction.WEST, 7, 7), b, Blocks.SPRUCE_PLANKS.defaultBlockState(), 4);
            p.set(px + 4, b + 1, pz + 3, Deco.BARREL);
            p.set(px + 4, b + 2, pz + 3, Deco.BARREL);
        }
    }

    // ───────────────────────── 남쪽: 풍차·헛간·목장 ─────────────────────────

    /** 돌 풍차: 가늘어지는 둥근 탑, 가문비 원뿔 지붕, 북쪽(도시 쪽)을 보는 흰 돛 4장 */
    public static class Windmill extends Feature {
        private final int tx, tz;
        private final long seed;

        public Windmill(int tx, int tz, long seed) {
            super(tx - 12, tz - 12, tx + 12, tz + 12);
            this.tx = tx;
            this.tz = tz;
            this.seed = seed;
        }

        @Override
        public void build(Placer p, CityLayout city) {
            final int b = groundY, H = 15;
            p.forColumns(tx - 5, tz - 5, tx + 5, tz + 5, (x, z) -> {
                double d = Math.hypot(x - tx, z - tz);
                for (int ly = 0; ly <= H; ly++) {
                    double r = 4.2 - ly * 1.4 / H;
                    if (d > r + 0.5) continue;
                    boolean wall = d > r - 0.7;
                    BlockState s = ly == 0 ? COBBLE : wall ? (ly % 5 == 0 ? SPRUCE : Shapes.hash(x, ly, z, seed) % 3 == 0 ? COBBLE : STONE_BRICK) : (ly % 5 == 0 ? SPRUCE : AIR);
                    if (wall && ly % 5 == 3 && Math.abs(x - tx) + Math.abs(z - tz) == (int) r) s = Blocks.GLASS_PANE.defaultBlockState();
                    p.set(x, b + ly, z, s);
                }
            });
            for (int ly = 1; ly <= 2; ly++) p.set(tx, b + ly, tz + 4, AIR);                         // 문 (남쪽)
            Royal.spire(p, tx, tz, b + H + 1, 3.2, 7, Blocks.SPRUCE_PLANKS.defaultBlockState(), Blocks.DARK_OAK_PLANKS.defaultBlockState());
            // 돛: 바퀴통에서 대각선 4방향
            int hz = tz - 4, hy = b + H - 2;
            p.set(tx, hy, tz - 3, log(Direction.Axis.Z));
            p.set(tx, hy, hz, Blocks.DARK_OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z));
            int[][] dirs = {{1, 1}, {-1, 1}, {1, -1}, {-1, -1}};
            for (int[] d : dirs) {
                for (int k = 1; k <= 9; k++) {
                    int x = tx + d[0] * k, y = hy + d[1] * k;
                    p.set(x, y, hz, Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState());
                    if (k >= 3) {
                        p.set(x + d[0], y, hz, Blocks.WHITE_WOOL.defaultBlockState());
                        p.set(x, y - d[1], hz, k % 3 == 0 ? SPRUCE_FENCE : Blocks.WHITE_WOOL.defaultBlockState());
                    }
                }
            }
            p.set(tx + 3, b + 1, tz + 5, Blocks.HAY_BLOCK.defaultBlockState());
            p.set(tx - 3, b + 1, tz + 5, Deco.BARREL);
        }
    }

    /** 붉은 헛간: 붉은 테라코타 벽 + 짙은 참나무 골조 + 흰 테두리, 큰 문, 건초 다락 */
    public static class Barn extends Feature {
        private final Frame f;

        public Barn(int cx, int cz, Direction front, int w, int d) {
            this(new Frame(cx, cz, front, w, d));
        }

        private Barn(Frame f) {
            super(f.minX - 2, f.minZ - 2, f.maxX + 2, f.maxZ + 2);
            this.f = f;
        }

        @Override
        public void build(Placer p, CityLayout city) {
            final int b = groundY, H = 6;
            for (int u = 0; u < f.width; u++) {
                for (int v = 0; v < f.depth; v++) {
                    boolean edge = u == 0 || v == 0 || u == f.width - 1 || v == f.depth - 1;
                    boolean corner = (u == 0 || u == f.width - 1) && (v == 0 || v == f.depth - 1);
                    f.set(p, b, u, 0, v, edge ? COBBLE : Blocks.PACKED_MUD.defaultBlockState());
                    for (int ly = 1; ly <= H; ly++) {
                        BlockState s = AIR;
                        if (edge) {
                            s = corner || ly == H ? Blocks.DARK_OAK_LOG.defaultBlockState() : Blocks.RED_TERRACOTTA.defaultBlockState();
                            boolean bigDoor = v == 0 && Math.abs(u - f.width / 2) <= 2 && ly <= 4;
                            if (bigDoor) s = AIR;
                            if (v == 0 && Math.abs(u - f.width / 2) == 3 && ly <= 5) s = Blocks.WHITE_TERRACOTTA.defaultBlockState();
                        } else if (ly == 4 && v > f.depth / 2) s = Royal.DARK_PLANKS;            // 다락
                        f.set(p, b, u, ly, v, s);
                    }
                }
            }
            Chateau.hipRoof(p, f.minX, f.minZ, f.maxX, f.maxZ, b + H + 1, 8, 0, 0, 0, Royal.DARK_PLANKS,
                    d -> Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, d), Blocks.WHITE_TERRACOTTA.defaultBlockState());
            for (int u = 1; u < f.width - 1; u += 2) f.set(p, b, u, 5, f.depth - 2, Blocks.HAY_BLOCK.defaultBlockState());
            f.set(p, b, 1, 1, f.depth - 2, Blocks.HAY_BLOCK.defaultBlockState());
            f.set(p, b, 2, 1, f.depth - 2, Deco.WATER_CAULDRON);
        }
    }

    /** 울타리 목장: 소·양·말, 물통, 건초 */
    public static class Ranch extends Feature {
        private final long seed;

        public Ranch(int x0, int z0, int x1, int z1, long seed) {
            super(Math.min(x0, x1), Math.min(z0, z1), Math.max(x0, x1), Math.max(z0, z1));
            this.seed = seed;
        }

        @Override
        public boolean needsSpawn() {
            return true;
        }

        @Override
        public void build(Placer p, CityLayout city) {
            final int b = groundY;
            int gx = (minX + maxX) / 2;
            p.forColumns(minX, minZ, maxX, maxZ, (x, z) -> {
                boolean edge = x == minX || x == maxX || z == minZ || z == maxZ;
                p.set(x, b, z, Shapes.hash(x, 2, z, seed) % 6 == 0 ? Blocks.COARSE_DIRT.defaultBlockState() : GRASS);
                if (edge && !(z == minZ && Math.abs(x - gx) <= 1)) p.set(x, b + 1, z, Blocks.OAK_FENCE.defaultBlockState());
                else if (!edge && Shapes.hash(x, 3, z, seed) % 40 == 0) p.set(x, b + 1, z, Blocks.SHORT_GRASS.defaultBlockState());
            });
            for (int k = 0; k < 5; k++) p.set(minX + 4 + k, b + 1, minZ + 3, k == 2 ? Deco.WATER_CAULDRON : Blocks.HAY_BLOCK.defaultBlockState());
        }

        @Override
        public void spawn(ServerLevel level, CityLayout city) {
            EntityType<?>[] kinds = {EntityType.COW, EntityType.SHEEP, EntityType.HORSE, EntityType.COW, EntityType.SHEEP, EntityType.PIG};
            for (int i = 0; i < 12; i++) {
                int x = minX + 3 + Math.floorMod(Shapes.hash(i, 1, 2, seed), Math.max(1, maxX - minX - 6));
                int z = minZ + 3 + Math.floorMod(Shapes.hash(i, 3, 4, seed), Math.max(1, maxZ - minZ - 6));
                kinds[i % kinds.length].spawn(level, new BlockPos(x, groundY + 1, z), MobSpawnType.STRUCTURE);
            }
        }
    }

    // ───────────────────────── 서쪽: 벌목장 ─────────────────────────

    /** 벌목장: 통나무 더미, 그루터기, 제재소 오두막(석재 절단기), 도끼질 받침, 손수레 자리 */
    public static class LumberCamp extends Feature {
        private final long seed;

        public LumberCamp(int x0, int z0, int x1, int z1, long seed) {
            super(Math.min(x0, x1), Math.min(z0, z1), Math.max(x0, x1), Math.max(z0, z1));
            this.seed = seed;
        }

        @Override
        public void build(Placer p, CityLayout city) {
            final int b = groundY;
            p.forColumns(minX, minZ, maxX, maxZ, (x, z) -> {
                int h = Shapes.hash(x, 6, z, seed);
                p.set(x, b, z, h % 3 == 0 ? Blocks.COARSE_DIRT.defaultBlockState() : h % 3 == 1 ? Blocks.PODZOL.defaultBlockState() : GRASS);
                if (h % 37 == 0) p.set(x, b + 1, z, Blocks.OAK_LOG.defaultBlockState());          // 그루터기
            });
            for (int pile = 0; pile < 4; pile++) {                                               // 통나무 더미
                int x0 = minX + 3 + pile * 7, z0 = minZ + 3;
                for (int ly = 1; ly <= 3; ly++) for (int dx = 0; dx < 5 - ly; dx++) for (int dz = 0; dz < 4; dz++)
                    p.set(x0 + dx + ly / 2, b + ly, z0 + dz, Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z));
            }
            Frame f = new Frame(maxX - 6, maxZ - 6, Direction.WEST, 9, 7);
            cabin(p, f, b, Blocks.SPRUCE_PLANKS.defaultBlockState(), 4);
            f.set(p, b, 2, 1, 2, Blocks.STONECUTTER.defaultBlockState());
            f.set(p, b, 4, 1, 4, Deco.CRAFTING);
            p.set(minX + 5, b + 1, maxZ - 4, Blocks.STRIPPED_OAK_LOG.defaultBlockState());
            p.set(minX + 5, b + 2, maxZ - 4, Blocks.OAK_PRESSURE_PLATE.defaultBlockState());
            p.set(minX + 7, b + 1, maxZ - 3, Deco.CAMPFIRE);
        }
    }

    // ───────────────────────── 동쪽: 항구 ─────────────────────────

    /** 강변 항구: 강 쪽으로 뻗은 나무 부두 4개(말뚝·랜턴), 부두 창고, 짐 상자, 기중기, 교역 깃발 */
    public static class Port extends Feature {
        private final int bankX, riverX;
        private final long seed;

        public Port(int x0, int z0, int x1, int z1, int riverX, long seed) {
            super(Math.min(x0, x1), Math.min(z0, z1), Math.max(x0, x1) + 10, Math.max(z0, z1));
            this.bankX = Math.max(x0, x1);
            this.riverX = riverX;
            this.seed = seed;
        }

        @Override
        public void build(Placer p, CityLayout city) {
            final int b = groundY;
            p.forColumns(minX, minZ, bankX, maxZ, (x, z) -> p.set(x, b, z, (x + z) % 3 == 0 ? Blocks.SPRUCE_PLANKS.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState()));
            int cz = (minZ + maxZ) / 2;
            for (int k = -2; k <= 2; k++) {
                if (k == 0) continue;
                int pz = cz + k * 16;
                for (int x = bankX - 2; x <= riverX - 1; x++) {
                    for (int dz = -1; dz <= 1; dz++) p.set(x, b, pz + dz, SPRUCE);
                    if ((x - bankX) % 4 == 0) {
                        for (int dz = -2; dz <= 2; dz += 4) {
                            for (int y = b - 4; y <= b + 1; y++) p.set(x, y, pz + dz, SPRUCE_LOG);
                            if ((x - bankX) % 8 == 0) p.set(x, b + 2, pz + dz, Blocks.LANTERN.defaultBlockState());
                        }
                    }
                }
                p.set(riverX - 1, b + 1, pz, Deco.BARREL);
            }
            // 부두 창고 + 짐
            cabin(p, new Frame(minX + 7, cz - 6, Direction.EAST, 11, 9), b, Blocks.SPRUCE_PLANKS.defaultBlockState(), 5);
            for (int i = 0; i < 12; i++) {
                int x = minX + 3 + (i % 4) * 2, z = cz + 6 + (i / 4) * 2;
                p.set(x, b + 1, z, i % 3 == 0 ? Blocks.HAY_BLOCK.defaultBlockState() : Deco.BARREL);
                if (i % 2 == 0) p.set(x, b + 2, z, Deco.BARREL);
            }
            // 기중기
            int kx = bankX - 1, kz = cz + 10;
            for (int y = b + 1; y <= b + 9; y++) p.set(kx, y, kz, SPRUCE_LOG);
            for (int x = kx; x <= kx + 5; x++) p.set(x, b + 9, kz, log(Direction.Axis.X));
            for (int y = b + 4; y <= b + 8; y++) p.set(kx + 5, y, kz, Blocks.CHAIN.defaultBlockState());
            p.set(kx + 5, b + 3, kz, Deco.BARREL);
            // 교역 깃발
            for (int k = -1; k <= 1; k += 2) {
                int fz = cz + k * 4;
                for (int y = b + 1; y <= b + 7; y++) p.set(bankX - 3, y, fz, SPRUCE_FENCE);
                p.set(bankX - 2, b + 6, fz, k < 0 ? Royal.mintBanner(Direction.EAST) : Royal.redBanner(Direction.EAST));
            }
        }
    }

    // ───────────────────────── 초소 · 묘지 ─────────────────────────

    /** 군사 초소: 통나무 울타리 원 + 나무 망루 + 모닥불 */
    public static class Watchpost extends Feature {
        private final int tx, tz;

        public Watchpost(int tx, int tz) {
            super(tx - 9, tz - 9, tx + 9, tz + 9);
            this.tx = tx;
            this.tz = tz;
        }

        @Override
        public void build(Placer p, CityLayout city) {
            final int b = groundY;
            p.forColumns(tx - 8, tz - 8, tx + 8, tz + 8, (x, z) -> {
                double d = Math.hypot(x - tx, z - tz);
                if (d > 8.5) return;
                p.set(x, b, z, Blocks.COARSE_DIRT.defaultBlockState());
                if (d > 7.5 && !(Math.abs(x - tx) <= 1 && z > tz)) {
                    for (int ly = 1; ly <= 4; ly++) p.set(x, b + ly, z, SPRUCE_LOG);
                }
            });
            for (int dx = -2; dx <= 2; dx += 4) for (int dz = -2; dz <= 2; dz += 4)
                for (int ly = 1; ly <= 12; ly++) p.set(tx + dx, b + ly, tz + dz, SPRUCE_LOG);
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                p.set(tx + dx, b + 12, tz + dz, SPRUCE);
                if (Math.abs(dx) == 2 || Math.abs(dz) == 2) p.set(tx + dx, b + 13, tz + dz, SPRUCE_FENCE);
                p.set(tx + dx, b + 16, tz + dz, Blocks.SPRUCE_SLAB.defaultBlockState());
            }
            for (int ly = 1; ly < 12; ly++) p.set(tx - 1, b + ly, tz - 2 + 1, Palette.ladder(Direction.SOUTH));
            p.set(tx - 1, b + 12, tz - 1, AIR);
            for (int ly = 13; ly <= 15; ly++) p.set(tx - 2, b + ly, tz - 2, SPRUCE_FENCE);
            p.set(tx, b + 15, tz, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
            p.set(tx + 4, b + 1, tz + 3, Deco.CAMPFIRE);
            p.set(tx - 4, b + 1, tz + 3, Blocks.HAY_BLOCK.defaultBlockState());
            p.set(tx + 3, b + 1, tz - 4, Deco.BARREL);
        }
    }

    /** 묘지: 낮은 돌담, 방해석 길, 묘비(촛불), 꽃 핀 무덤, 영혼 랜턴 기둥, 가운데 작은 영묘 */
    public static class Graveyard extends Feature {
        private final long seed;

        public Graveyard(int x0, int z0, int x1, int z1, long seed) {
            super(Math.min(x0, x1), Math.min(z0, z1), Math.max(x0, x1), Math.max(z0, z1));
            this.seed = seed;
        }

        @Override
        public void build(Placer p, CityLayout city) {
            final int b = groundY;
            int gx = (minX + maxX) / 2, gz = (minZ + maxZ) / 2;
            p.forColumns(minX, minZ, maxX, maxZ, (x, z) -> {
                int a = Math.floorMod(x - minX, 8), c = Math.floorMod(z - minZ, 7);
                boolean border = x == minX || x == maxX || z == minZ || z == maxZ;
                boolean gate = z == maxZ && Math.abs(x - gx) <= 1;
                boolean path = a <= 1 || Math.abs(z - gz) <= 1;
                int h = Shapes.hash(x, 21, z, seed);
                p.set(x, b, z, path ? CALCITE : GRASS);
                if (border) {
                    if (!gate) {
                        p.set(x, b + 1, z, Blocks.STONE_BRICK_WALL.defaultBlockState());
                        if (Math.floorMod(x + z, 6) == 0) p.set(x, b + 2, z, Blocks.SOUL_LANTERN.defaultBlockState());
                    }
                    return;
                }
                if (path || Math.max(Math.abs(x - gx), Math.abs(z - gz)) <= 4) return;
                if (a == 4 && c == 2) {
                    p.set(x, b + 1, z, h % 3 == 0 ? Blocks.MOSSY_STONE_BRICK_WALL.defaultBlockState() : CHISELED_QUARTZ);
                    if (h % 4 == 0) p.set(x, b + 2, z, Deco.candles(1 + h % 3));
                } else if (a >= 3 && a <= 5 && c >= 3 && c <= 4) {
                    p.set(x, b, z, Blocks.COARSE_DIRT.defaultBlockState());
                    if (h % 3 == 0) p.set(x, b + 1, z, h % 2 == 0 ? Blocks.LILY_OF_THE_VALLEY.defaultBlockState() : Blocks.WHITE_TULIP.defaultBlockState());
                }
            });
            Mausoleum m = new Mausoleum(gx, gz, Direction.SOUTH);
            m.groundY = b;
            m.build(p, city);
        }
    }
}
