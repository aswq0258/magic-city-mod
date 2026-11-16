package io.github.aswq0258.magiccity.city.feature;

import io.github.aswq0258.magiccity.build.Placer;
import io.github.aswq0258.magiccity.city.Chateau;
import io.github.aswq0258.magiccity.city.CityLayout;
import io.github.aswq0258.magiccity.city.Palette;
import io.github.aswq0258.magiccity.city.Royal;
import io.github.aswq0258.magiccity.city.Shapes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;

/**
 * 신시가지의 공공·상업·산업 건물 (한 종류당 한 채). 종류마다 크기·층수·벽·기둥·지붕 모양과 재료·
 * 상징 장식(종탑, 돔, 큐폴라, 거대 굴뚝, 수차, 온실, 차양)·실내 소품이 달라 서로 다른 건물로 보임.
 *
 * 내부 좌표: u = 왼쪽→오른쪽(정면에서 볼 때), v = 앞벽(0)→뒷벽(D-1), ly = 바닥(0)부터의 높이, 층 높이 5
 */
public class DistrictBuilding extends Feature {
    public enum Roof { GABLE, HIP, DOME, FLAT }

    public enum Extra { NONE, CUPOLA, TOWER, SMOKESTACK, CHIMNEY, GREENHOUSE, AWNINGS, WATERWHEEL }

    public enum Kind {
        SCHOLARS_COLLEGE("북부 학술원", 23, 17, 3, Blocks.CALCITE, Blocks.QUARTZ_PILLAR, Blocks.POLISHED_DIORITE, Roof.HIP,
                Blocks.DEEPSLATE_TILES, Blocks.DEEPSLATE_TILE_STAIRS, Blocks.GOLD_BLOCK, Extra.CUPOLA, Blocks.WHITE_STAINED_GLASS_PANE,
                new Block[]{Blocks.BOOKSHELF, Blocks.LECTERN, Blocks.CARTOGRAPHY_TABLE, Blocks.BOOKSHELF, Blocks.ENCHANTING_TABLE}, true),
        ROYAL_LIBRARY("왕립 도서관", 19, 15, 2, Blocks.DARK_OAK_PLANKS, Blocks.STRIPPED_DARK_OAK_LOG, Blocks.STONE_BRICKS, Roof.DOME,
                Blocks.WAXED_OXIDIZED_COPPER, Blocks.WAXED_OXIDIZED_CUT_COPPER_STAIRS, Blocks.GOLD_BLOCK, Extra.NONE, Blocks.YELLOW_STAINED_GLASS_PANE,
                new Block[]{Blocks.BOOKSHELF, Blocks.CHISELED_BOOKSHELF, Blocks.BOOKSHELF, Blocks.LECTERN, Blocks.BOOKSHELF}, false),
        KNIGHT_LODGE("북부 기사단 숙소", 17, 13, 2, Blocks.STONE_BRICKS, Blocks.DEEPSLATE_BRICKS, Blocks.COBBLESTONE, Roof.GABLE,
                Blocks.DARK_OAK_PLANKS, Blocks.DARK_OAK_STAIRS, Blocks.DARK_OAK_PLANKS, Extra.TOWER, Blocks.IRON_BARS,
                new Block[]{Blocks.SMITHING_TABLE, Blocks.GRINDSTONE, Blocks.BARREL, Blocks.BARREL, Blocks.FLETCHING_TABLE}, true),
        HERBALIST("약초 연구소", 15, 11, 1, Blocks.BIRCH_PLANKS, Blocks.BIRCH_LOG, Blocks.MOSSY_COBBLESTONE, Roof.GABLE,
                Blocks.MOSS_BLOCK, Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_PLANKS, Extra.GREENHOUSE, Blocks.GLASS_PANE,
                new Block[]{Blocks.BREWING_STAND, Blocks.CAULDRON, Blocks.COMPOSTER, Blocks.FLOWER_POT, Blocks.BARREL}, false),
        FOUNDRY("주물 공장", 21, 15, 2, Blocks.BRICKS, Blocks.POLISHED_BLACKSTONE, Blocks.STONE_BRICKS, Roof.GABLE,
                Blocks.DEEPSLATE_TILES, Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILES, Extra.SMOKESTACK, Blocks.IRON_BARS,
                new Block[]{Blocks.BLAST_FURNACE, Blocks.ANVIL, Blocks.BLAST_FURNACE, Blocks.SMITHING_TABLE, Blocks.BLAST_FURNACE}, false),
        GLASSWORKS("유리 공방", 15, 11, 1, Blocks.SMOOTH_SANDSTONE, Blocks.SPRUCE_LOG, Blocks.SANDSTONE, Roof.HIP,
                Blocks.POLISHED_GRANITE, Blocks.POLISHED_GRANITE_STAIRS, Blocks.GLASS, Extra.CUPOLA, Blocks.LIGHT_BLUE_STAINED_GLASS_PANE,
                new Block[]{Blocks.FURNACE, Blocks.FURNACE, Blocks.GLASS, Blocks.BARREL, Blocks.TINTED_GLASS}, false),
        DYEWORKS("염색 공방", 15, 11, 2, Blocks.WHITE_TERRACOTTA, Blocks.DARK_OAK_LOG, Blocks.COBBLESTONE, Roof.GABLE,
                Blocks.DARK_OAK_PLANKS, Blocks.DARK_OAK_STAIRS, Blocks.DARK_OAK_PLANKS, Extra.AWNINGS, Blocks.GLASS_PANE,
                new Block[]{Blocks.LOOM, Blocks.CAULDRON, Blocks.LOOM, Blocks.BARREL, Blocks.CAULDRON}, false),
        MATERIALS_DEPOT("자재 창고", 19, 13, 1, Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_LOG, Blocks.STONE, Roof.GABLE,
                Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_PLANKS, Extra.NONE, Blocks.SPRUCE_FENCE,
                new Block[]{Blocks.BARREL, Blocks.BARREL, Blocks.HAY_BLOCK, Blocks.BARREL, Blocks.CRAFTING_TABLE}, false),
        MERCHANT_GUILD("상인 길드 회관", 23, 17, 3, Blocks.YELLOW_TERRACOTTA, Blocks.DARK_OAK_LOG, Blocks.POLISHED_ANDESITE, Roof.HIP,
                Blocks.RED_NETHER_BRICKS, Blocks.RED_NETHER_BRICK_STAIRS, Blocks.GOLD_BLOCK, Extra.TOWER, Blocks.GLASS_PANE,
                new Block[]{Blocks.LECTERN, Blocks.CARTOGRAPHY_TABLE, Blocks.BARREL, Blocks.BELL, Blocks.BARREL}, true),
        COACH_INN("마차 역참 여관", 19, 15, 3, Blocks.OAK_PLANKS, Blocks.STRIPPED_OAK_LOG, Blocks.COBBLESTONE, Roof.GABLE,
                Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_PLANKS, Extra.CHIMNEY, Blocks.GLASS_PANE,
                new Block[]{Blocks.BARREL, Blocks.SMOKER, Blocks.BARREL, Blocks.CAULDRON, Blocks.CRAFTING_TABLE}, true),
        THEATER("왕도 극장", 21, 17, 2, Blocks.RED_TERRACOTTA, Blocks.QUARTZ_PILLAR, Blocks.POLISHED_DIORITE, Roof.HIP,
                Blocks.PURPUR_BLOCK, Blocks.PURPUR_STAIRS, Blocks.GOLD_BLOCK, Extra.AWNINGS, Blocks.MAGENTA_STAINED_GLASS_PANE,
                new Block[]{Blocks.NOTE_BLOCK, Blocks.JUKEBOX, Blocks.NOTE_BLOCK, Blocks.NOTE_BLOCK, Blocks.NOTE_BLOCK}, true),
        SPICE_BAZAAR("향신료 바자르", 21, 15, 1, Blocks.SMOOTH_RED_SANDSTONE, Blocks.ACACIA_LOG, Blocks.RED_SANDSTONE, Roof.DOME,
                Blocks.WAXED_CUT_COPPER, Blocks.WAXED_CUT_COPPER_STAIRS, Blocks.GOLD_BLOCK, Extra.AWNINGS, Blocks.ORANGE_STAINED_GLASS_PANE,
                new Block[]{Blocks.BARREL, Blocks.COMPOSTER, Blocks.SMOKER, Blocks.BARREL, Blocks.FLOWER_POT}, false),
        TRADE_EXCHANGE("동부 무역소", 21, 17, 2, Blocks.POLISHED_DIORITE, Blocks.DARK_PRISMARINE, Blocks.STONE_BRICKS, Roof.HIP,
                Blocks.DARK_PRISMARINE, Blocks.DARK_PRISMARINE_STAIRS, Blocks.GOLD_BLOCK, Extra.CUPOLA, Blocks.CYAN_STAINED_GLASS_PANE,
                new Block[]{Blocks.CARTOGRAPHY_TABLE, Blocks.LECTERN, Blocks.BARREL, Blocks.BARREL, Blocks.BELL}, true),
        MONEY_CHANGER("환전소", 13, 11, 2, Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.GILDED_BLACKSTONE, Blocks.POLISHED_BLACKSTONE, Roof.GABLE,
                Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS, Blocks.GOLD_BLOCK, Extra.NONE, Blocks.YELLOW_STAINED_GLASS_PANE,
                new Block[]{Blocks.LECTERN, Blocks.BARREL, Blocks.ANVIL, Blocks.BARREL, Blocks.LECTERN}, true),
        WATERMILL("수차 방앗간", 11, 9, 2, Blocks.COBBLESTONE, Blocks.STRIPPED_SPRUCE_LOG, Blocks.MOSSY_COBBLESTONE, Roof.GABLE,
                Blocks.DARK_OAK_PLANKS, Blocks.DARK_OAK_STAIRS, Blocks.DARK_OAK_PLANKS, Extra.WATERWHEEL, Blocks.GLASS_PANE,
                new Block[]{Blocks.GRINDSTONE, Blocks.HAY_BLOCK, Blocks.COMPOSTER, Blocks.BARREL, Blocks.HAY_BLOCK}, false),
        RIVER_WAREHOUSE("강변 물류창고", 13, 19, 1, Blocks.MUD_BRICKS, Blocks.OAK_LOG, Blocks.STONE_BRICKS, Roof.GABLE,
                Blocks.MANGROVE_PLANKS, Blocks.MANGROVE_STAIRS, Blocks.MANGROVE_PLANKS, Extra.NONE, Blocks.OAK_FENCE,
                new Block[]{Blocks.BARREL, Blocks.BARREL, Blocks.BARREL, Blocks.HAY_BLOCK, Blocks.BARREL}, false);

        public final String title;
        final int W, D, floors;
        final BlockState wall, trim, plinth, roofBlock, crest, window;
        final Block roofStairs;
        final Roof roof;
        final Extra extra;
        final Block[] props;
        final boolean villager;

        Kind(String title, int W, int D, int floors, Block wall, Block trim, Block plinth, Roof roof, Block roofBlock, Block roofStairs, Block crest,
             Extra extra, Block window, Block[] props, boolean villager) {
            this.title = title;
            this.W = W;
            this.D = D;
            this.floors = floors;
            this.wall = wall.defaultBlockState();
            this.trim = trim.defaultBlockState();
            this.plinth = plinth.defaultBlockState();
            this.roof = roof;
            this.roofBlock = roofBlock.defaultBlockState();
            this.roofStairs = roofStairs;
            this.crest = crest.defaultBlockState();
            this.extra = extra;
            this.window = window.defaultBlockState();
            this.props = props;
            this.villager = villager;
        }

        /** 이 건물이 차지하는 (du, dn) 반폭: 장식까지 포함 */
        public int halfW() {
            return Math.max(W, D) / 2 + 3 + (extra == Extra.GREENHOUSE ? 7 : 0) + (extra == Extra.WATERWHEEL ? 6 : 0);
        }
    }

    private static final int FH = 5;
    private final Kind k;
    private final Frame f;
    private final long seed;

    public DistrictBuilding(Kind k, int cx, int cz, Direction front, long seed) {
        this(k, new Frame(cx, cz, front, k.W, k.D), seed);
    }

    private DistrictBuilding(Kind k, Frame f, long seed) {
        super(f.minX - 3 - pad(k), f.minZ - 3 - pad(k), f.maxX + 3 + pad(k), f.maxZ + 3 + pad(k));
        this.k = k;
        this.f = f;
        this.seed = seed;
    }

    private static int pad(Kind k) {
        return k.extra == Extra.GREENHOUSE ? 7 : k.extra == Extra.WATERWHEEL ? 6 : k.extra == Extra.AWNINGS ? 1 : 0;
    }

    private BlockState stairs(Direction ascend) {
        return k.roofStairs.defaultBlockState().setValue(StairBlock.FACING, ascend);
    }

    @Override
    public void build(Placer p, CityLayout city) {
        final int b = groundY, W = k.W, D = k.D, top = k.floors * FH;
        final int door = W / 2, ladderU = 1, ladderV = D - 2;
        // ── 벽·바닥·층 ──
        for (int u = 0; u < W; u++) {
            for (int v = 0; v < D; v++) {
                int x = f.x(u, v), z = f.z(u, v);
                if (!p.contains(x, z)) continue;
                p.clearAbove(x, z, b + 1);
                p.foundation(x, z, b - 1, 12);
                boolean eu = u == 0 || u == W - 1, ev = v == 0 || v == D - 1;
                f.set(p, b, u, 0, v, eu || ev ? k.plinth : Blocks.POLISHED_ANDESITE.defaultBlockState());
                for (int ly = 1; ly <= top; ly++) {
                    BlockState s;
                    if (eu || ev) {
                        boolean post = (eu && ev) || (ev && u % 4 == 0) || (eu && v % 4 == 0);
                        boolean band = ly % FH == 0;
                        int inFloor = (ly - 1) % FH + 1;
                        boolean win = !post && !band && (inFloor == 2 || inFloor == 3) && ((ev && u % 4 == 2) || (eu && v % 4 == 2));
                        s = post || band ? k.trim : win ? k.window : k.wall;
                        if (ev && v == 0 && Math.abs(u - door) <= (W >= 19 ? 1 : 0) && ly <= 3) s = Blocks.AIR.defaultBlockState();
                    } else {
                        s = ly % FH == 0 ? (u == ladderU && v == ladderV && ly < top ? Blocks.AIR.defaultBlockState() : Blocks.SPRUCE_PLANKS.defaultBlockState())
                                : Blocks.AIR.defaultBlockState();
                    }
                    f.set(p, b, u, ly, v, s);
                }
            }
        }
        if (W < 19) {
            f.set(p, b, door, 1, 0, Palette.door(f.back, false));
            f.set(p, b, door, 2, 0, Palette.door(f.back, true));
        }
        // 사다리 (층 사이)
        for (int ly = 1; ly < top - 1 && k.floors > 1; ly++) f.set(p, b, ladderU, ly, ladderV, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, f.front));
        // 현관 계단·등불·깃발
        for (int du = -1; du <= 1; du++) f.set(p, b, door + du, 0, -1, k.plinth);
        for (int s = -1; s <= 1; s += 2) {
            f.set(p, b, door + s * 3, 1, -1, Blocks.STONE_BRICK_WALL.defaultBlockState());
            f.set(p, b, door + s * 3, 2, -1, Blocks.LANTERN.defaultBlockState());
        }
        f.set(p, b, door, 4, -1, Shapes.hash(f.x(0, 0), 3, f.z(0, 0), seed) % 2 == 0 ? Royal.mintBanner(f.front) : Royal.redBanner(f.front));
        interior(p, b, W, D);
        // ── 지붕 ──
        int roofTop = roof(p, b, W, D, top);
        extra(p, b, W, D, top, roofTop);
    }

    private void interior(Placer p, int b, int W, int D) {
        for (int fl = 0; fl < k.floors; fl++) {
            int y0 = fl * FH;
            for (int i = 0; i < k.props.length; i++) {
                int u = 3 + i * Math.max(2, (W - 5) / k.props.length);
                if (u >= W - 1) break;
                BlockState s = k.props[i].defaultBlockState();
                if (s.hasProperty(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING)) s = s.setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, f.front);
                f.set(p, b, u, y0 + 1, D - 2, s);
            }
            // 가운데 탁자·의자, 매단 등불
            int cu = W / 2, cv = D / 2;
            f.set(p, b, cu, y0 + 1, cv, Blocks.SPRUCE_FENCE.defaultBlockState());
            f.set(p, b, cu, y0 + 2, cv, Blocks.SPRUCE_PRESSURE_PLATE.defaultBlockState());
            f.set(p, b, cu - 1, y0 + 1, cv, Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, f.left));
            f.set(p, b, cu + 1, y0 + 1, cv, Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, f.right));
            f.set(p, b, cu, y0 + FH - 1, cv - 2, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
            f.set(p, b, 2, y0 + FH - 1, 2, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
            f.set(p, b, W - 3, y0 + FH - 1, D - 3, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
            for (int u = cu - 2; u <= cu + 2; u++) for (int v = 2; v <= D - 4; v++) {
                if (u == cu && v == cv) continue;
                if (Math.abs(u - cu) == 1 && v == cv) continue;
                f.set(p, b, u, y0 + 1, v, k == Kind.THEATER ? Blocks.RED_CARPET.defaultBlockState() : Blocks.BROWN_CARPET.defaultBlockState());
            }
            // 여관: 위층은 객실 (침대)
            if (k == Kind.COACH_INN && fl > 0) {
                for (int u = 2; u <= W - 3; u += 3) {
                    f.set(p, b, u, y0 + 1, 1, Palette.bed(f.front, true));
                    f.set(p, b, u, y0 + 1, 2, Palette.bed(f.front, false));
                }
            }
        }
        // 극장: 무대와 객석
        if (k == Kind.THEATER) {
            for (int u = 2; u <= W - 3; u++) for (int v = D - 5; v <= D - 2; v++) f.set(p, b, u, 1, v, Blocks.DARK_OAK_PLANKS.defaultBlockState());
            for (int u = 2; u <= W - 3; u++) f.set(p, b, u, 2, D - 2, Blocks.RED_WOOL.defaultBlockState());
            for (int v = 3; v <= D - 7; v += 2) for (int u = 2; u <= W - 3; u++) if (Math.abs(u - W / 2) > 1)
                f.set(p, b, u, 1, v, Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, f.front));
        }
        int cx = f.x(W - 2, 1), cz = f.z(W - 2, 1);
        if (p.contains(cx, cz) && p.pass() == Placer.Pass.SOLID) {
            p.set(cx, b + 1, cz, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, f.left));
            BlockEntity be = p.level().getBlockEntity(new BlockPos(cx, b + 1, cz));
            if (be instanceof RandomizableContainerBlockEntity rc) rc.setLootTable(BuiltInLootTables.VILLAGE_PLAINS_HOUSE, seed);
        }
    }

    /** @return 지붕 꼭대기 y */
    private int roof(Placer p, int b, int W, int D, int top) {
        switch (k.roof) {
            case HIP -> {
                return Chateau.hipRoof(p, f.minX, f.minZ, f.maxX, f.maxZ, b + top + 1, Math.min(W, D) - 2, 0, 0, 0, k.roofBlock, this::stairs, k.crest);
            }
            case DOME -> {
                for (int u = 0; u < W; u++) for (int v = 0; v < D; v++) f.set(p, b, u, top + 1, v, k.trim);
                double r = Math.min(W, D) / 2.0 - 1;
                int cu = W / 2, cv = D / 2;
                for (int u = 0; u < W; u++) for (int v = 0; v < D; v++) {
                    double d = Math.hypot(u - cu, v - cv);
                    for (int y = 0; y <= r; y++) {
                        double dd = Math.sqrt(d * d + y * y * 1.1);
                        if (dd <= r && dd > r - 1.3) f.set(p, b, u, top + 2 + y, v, k.roofBlock);
                    }
                }
                int tip = top + 2 + (int) r;
                f.set(p, b, cu, tip + 1, cv, k.crest);
                f.set(p, b, cu, tip + 2, cv, Blocks.LIGHTNING_ROD.defaultBlockState());
                return b + tip + 2;
            }
            case FLAT -> {
                for (int u = 0; u < W; u++) for (int v = 0; v < D; v++) {
                    boolean e = u == 0 || u == W - 1 || v == 0 || v == D - 1;
                    f.set(p, b, u, top + 1, v, k.roofBlock);
                    if (e && (u + v) % 2 == 0) f.set(p, b, u, top + 2, v, k.trim);
                }
                return b + top + 2;
            }
            default -> {                                                                // 박공 (용마루는 앞뒤 방향)
                int half = (W + 1) / 2;
                for (int kk = 0; kk <= half; kk++) {
                    int y = top + kk;
                    for (int v = -1; v <= D; v++) {
                        int uL = kk - 1, uR = W - kk;
                        if (uL >= uR - 1) {
                            f.set(p, b, uL, y, v, k.roofBlock);
                            if (uL != uR) f.set(p, b, uR, y, v, k.roofBlock);
                            continue;
                        }
                        f.set(p, b, uL, y, v, stairs(f.right));
                        f.set(p, b, uR, y, v, stairs(f.left));
                        if (kk > 0 && (v == 0 || v == D - 1)) for (int u = uL + 1; u < uR; u++) f.set(p, b, u, y, v, k.wall);
                    }
                }
                return b + top + half;
            }
        }
    }

    private void extra(Placer p, int b, int W, int D, int top, int roofTop) {
        int cu = W / 2, cv = D / 2;
        switch (k.extra) {
            case CUPOLA -> {
                int y0 = roofTop - b - 1;
                for (int du = -1; du <= 1; du++) for (int dv = -1; dv <= 1; dv++) {
                    boolean corner = du != 0 && dv != 0;
                    for (int ly = y0; ly <= y0 + 3; ly++) f.set(p, b, cu + du, ly, cv + dv, ly == y0 ? k.trim : corner ? k.trim : Blocks.GLASS_PANE.defaultBlockState());
                    f.set(p, b, cu + du, y0 + 4, cv + dv, k.roofBlock);
                }
                f.set(p, b, cu, y0 + 1, cv, Blocks.LANTERN.defaultBlockState());
                Royal.pinnacle(p, f.x(cu, cv), f.z(cu, cv), b + y0 + 5, 3);
            }
            case TOWER -> {                                                              // 앞쪽 왼편 모서리 종탑
                int h = roofTop - b + 8;
                for (int u = -1; u <= 3; u++) for (int v = -1; v <= 3; v++) {
                    boolean e = u == -1 || u == 3 || v == -1 || v == 3;
                    for (int ly = 0; ly <= h; ly++) {
                        BlockState s = e ? ((ly % 6 == 0 || ((u == -1 || u == 3) && (v == -1 || v == 3))) ? k.trim
                                : (ly > h - 5 && ly < h - 1 && (u == 1 || v == 1)) ? Blocks.AIR.defaultBlockState() : k.wall)
                                : ly == 0 ? k.plinth : ly == h - 4 ? Blocks.SPRUCE_PLANKS.defaultBlockState() : Blocks.AIR.defaultBlockState();
                        f.set(p, b, u, ly, v, s);
                    }
                }
                f.set(p, b, 1, h - 3, 1, Blocks.BELL.defaultBlockState());
                for (int ly = 1; ly <= 2; ly++) f.set(p, b, 3, ly, 1, Blocks.AIR.defaultBlockState());   // 건물 안에서 종탑으로
                for (int ly = 1; ly < h - 4; ly++) f.set(p, b, 2, ly, 2, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, f.left));
                for (int kk = 0; kk <= 3; kk++) for (int u = -1 + kk; u <= 3 - kk; u++) for (int v = -1 + kk; v <= 3 - kk; v++)
                    f.set(p, b, u, h + 1 + kk, v, kk == 3 ? k.crest : k.roofBlock);
            }
            case SMOKESTACK -> {
                int h = roofTop - b + 14;
                for (int u = W - 4; u <= W - 2; u++) for (int v = D - 4; v <= D - 2; v++) for (int ly = 1; ly <= h; ly++) {
                    boolean core = u == W - 3 && v == D - 3;
                    f.set(p, b, u, ly, v, core && ly > 1 ? Blocks.AIR.defaultBlockState() : (h - ly) % 6 == 0 ? Blocks.POLISHED_BLACKSTONE.defaultBlockState() : Blocks.BRICKS.defaultBlockState());
                }
                f.set(p, b, W - 3, 1, D - 3, Blocks.MAGMA_BLOCK.defaultBlockState());
                f.set(p, b, W - 3, h, D - 3, Blocks.CAMPFIRE.defaultBlockState());
            }
            case CHIMNEY -> {
                for (int ly = 1; ly <= roofTop - b + 3; ly++) f.set(p, b, W - 2, ly, D - 1, Blocks.BRICKS.defaultBlockState());
                f.set(p, b, W - 2, roofTop - b + 4, D - 1, Blocks.CAMPFIRE.defaultBlockState());
                f.set(p, b, W - 2, 1, D - 2, Blocks.FURNACE.defaultBlockState());
            }
            case GREENHOUSE -> {                                                         // 뒤편 유리 온실
                for (int u = 1; u <= W - 2; u++) for (int v = D; v <= D + 6; v++) {
                    boolean e = u == 1 || u == W - 2 || v == D + 6;
                    f.set(p, b, u, 0, v, e ? k.plinth : Blocks.MOSS_BLOCK.defaultBlockState());
                    for (int ly = 1; ly <= 4; ly++) f.set(p, b, u, ly, v, e ? Blocks.GLASS.defaultBlockState() : Blocks.AIR.defaultBlockState());
                    f.set(p, b, u, 5, v, Blocks.GLASS.defaultBlockState());
                    if (!e) {
                        int hh = Shapes.hash(f.x(u, v), 4, f.z(u, v), seed) % 5;
                        f.set(p, b, u, 1, v, hh == 0 ? Blocks.FLOWERING_AZALEA.defaultBlockState() : hh == 1 ? Blocks.FERN.defaultBlockState()
                                : hh == 2 ? Blocks.ALLIUM.defaultBlockState() : hh == 3 ? Blocks.BLUE_ORCHID.defaultBlockState() : Blocks.AIR.defaultBlockState());
                    }
                }
                for (int ly = 1; ly <= 2; ly++) f.set(p, b, cu, ly, D - 1, Blocks.AIR.defaultBlockState());
                f.set(p, b, cu, 4, D + 3, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
            }
            case AWNINGS -> {                                                            // 정면 줄무늬 차양
                for (int u = 0; u < W; u++) {
                    BlockState wool = (u / 2) % 2 == 0 ? Blocks.RED_WOOL.defaultBlockState() : Blocks.WHITE_WOOL.defaultBlockState();
                    if (k == Kind.SPICE_BAZAAR) wool = (u / 2) % 2 == 0 ? Blocks.ORANGE_WOOL.defaultBlockState() : Blocks.YELLOW_WOOL.defaultBlockState();
                    if (k == Kind.DYEWORKS) wool = new BlockState[]{Blocks.BLUE_WOOL.defaultBlockState(), Blocks.MAGENTA_WOOL.defaultBlockState(),
                            Blocks.LIME_WOOL.defaultBlockState(), Blocks.ORANGE_WOOL.defaultBlockState()}[u % 4];
                    f.set(p, b, u, 4, -1, wool);
                    if (u % 4 == 0) for (int ly = 1; ly <= 3; ly++) f.set(p, b, u, ly, -2, Blocks.SPRUCE_FENCE.defaultBlockState());
                    if (u % 4 == 0) f.set(p, b, u, 4, -2, wool);
                }
                if (k == Kind.DYEWORKS) for (int u = 1; u < W - 1; u += 2) {                // 옆마당 염색 천 건조대
                    f.set(p, b, u, 1, D + 1, Blocks.SPRUCE_FENCE.defaultBlockState());
                    f.set(p, b, u, 2, D + 1, Blocks.SPRUCE_FENCE.defaultBlockState());
                    f.set(p, b, u, 3, D + 1, new BlockState[]{Blocks.RED_WOOL.defaultBlockState(), Blocks.YELLOW_WOOL.defaultBlockState(),
                            Blocks.CYAN_WOOL.defaultBlockState(), Blocks.PURPLE_WOOL.defaultBlockState()}[(u / 2) % 4]);
                }
            }
            case WATERWHEEL -> {                                                         // 오른쪽 벽 바깥 물레바퀴
                int wu = W + 2;
                for (int v = cv - 5; v <= cv + 5; v++) for (int ly = -3; ly <= 7; ly++) {
                    double d = Math.hypot(v - cv, ly - 2);
                    BlockState s = null;
                    if (d > 3.6 && d <= 4.6) s = Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, f.right.getAxis());
                    else if (d <= 3.6 && (v == cv || ly == 2)) s = Blocks.SPRUCE_PLANKS.defaultBlockState();
                    if (s != null) f.set(p, b, wu, ly, v, s);
                }
                for (int u = W; u <= wu; u++) f.set(p, b, u, 2, cv, Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, f.right.getAxis()));
            }
            default -> {}
        }
    }

    @Override
    public boolean needsSpawn() {
        return k.villager;
    }

    @Override
    public void spawn(ServerLevel level, CityLayout city) {
        Mob m = EntityType.VILLAGER.spawn(level, new BlockPos(f.x(k.W / 2, k.D / 2 + 1), groundY + 1, f.z(k.W / 2, k.D / 2 + 1)), MobSpawnType.STRUCTURE);
        if (m != null) {
            m.setPersistenceRequired();
            m.setCustomName(Component.literal(k.title + " 관리인"));
        }
    }

    // ───────────────────────── 작은 시설 ─────────────────────────

    /** 신시장 광장: 줄지어 선 천막 노점, 가운데 작은 분수 */
    public static class MarketRow extends Feature {
        private final long seed;

        public MarketRow(int x0, int z0, int x1, int z1, long seed) {
            super(x0, z0, x1, z1);
            this.seed = seed;
        }

        @Override
        public void build(Placer p, CityLayout city) {
            final int b = groundY;
            p.forColumns(minX, minZ, maxX, maxZ, (x, z) -> {
                p.clearAbove(x, z, b + 1);
                int hh = Shapes.hash(x, 2, z, seed) % 6;
                p.set(x, b, z, hh == 0 ? Blocks.STONE_BRICKS.defaultBlockState() : hh == 1 ? Blocks.ANDESITE.defaultBlockState() : Blocks.POLISHED_ANDESITE.defaultBlockState());
                p.foundation(x, z, b - 1, 8);
            });
            BlockState[] cloth = {Blocks.RED_WOOL.defaultBlockState(), Blocks.YELLOW_WOOL.defaultBlockState(), Blocks.LIGHT_BLUE_WOOL.defaultBlockState(),
                    Blocks.LIME_WOOL.defaultBlockState(), Blocks.ORANGE_WOOL.defaultBlockState(), Blocks.PINK_WOOL.defaultBlockState()};
            Block[] goods = {Blocks.BARREL, Blocks.COMPOSTER, Blocks.HAY_BLOCK, Blocks.PUMPKIN, Blocks.MELON, Blocks.CRAFTING_TABLE, Blocks.SMOKER, Blocks.LOOM};
            int i = 0;
            for (int x = minX + 3; x <= maxX - 3; x += 7) {
                for (int z = minZ + 3; z <= maxZ - 3; z += 8) {
                    if (Math.abs(x - (minX + maxX) / 2) < 5 && Math.abs(z - (minZ + maxZ) / 2) < 5) continue;
                    BlockState c = cloth[i % cloth.length];
                    for (int dx = -2; dx <= 2; dx++) for (int dz = -1; dz <= 1; dz++) {
                        if (Math.abs(dx) == 2 && Math.abs(dz) == 1) for (int y = 1; y <= 3; y++) p.set(x + dx, b + y, z + dz, Blocks.OAK_FENCE.defaultBlockState());
                        p.set(x + dx, b + 4, z + dz, (dx + 2) % 2 == 0 ? c : Blocks.WHITE_WOOL.defaultBlockState());
                    }
                    for (int dx = -1; dx <= 1; dx++) p.set(x + dx, b + 1, z + 1, goods[(i + dx + 1) % goods.length].defaultBlockState());
                    p.set(x, b + 3, z, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
                    i++;
                }
            }
            Shapes.fountain(p, (minX + maxX) / 2, (minZ + maxZ) / 2, b);
        }

        @Override
        public boolean needsSpawn() {
            return true;
        }

        @Override
        public void spawn(ServerLevel level, CityLayout city) {
            for (int j = 0; j < 4; j++) {
                Mob m = EntityType.VILLAGER.spawn(level, new BlockPos(minX + 4 + j * 5, groundY + 1, (minZ + maxZ) / 2 + 3), MobSpawnType.STRUCTURE);
                if (m != null) {
                    m.setPersistenceRequired();
                    m.setCustomName(Component.literal("신시장 상인"));
                }
            }
        }
    }

    /** 정령 정원 (수림 경계지구): 이끼 정원, 가운데 연못, 꽃 진달래 고리, 방해석 결계 돌과 엔드 막대, 숲의 정령 */
    public static class SpiritGarden extends Feature {
        private final int gx, gz;

        public SpiritGarden(int gx, int gz) {
            super(gx - 14, gz - 14, gx + 14, gz + 14);
            this.gx = gx;
            this.gz = gz;
        }

        @Override
        public void build(Placer p, CityLayout city) {
            final int b = groundY;
            p.forColumns(minX, minZ, maxX, maxZ, (x, z) -> {
                double d = Math.hypot(x - gx, z - gz);
                if (d > 13.5) return;
                p.clearAbove(x, z, b + 1);
                int hh = Shapes.hash(x, 7, z, 77) % 9;
                if (d <= 4) {
                    p.set(x, b, z, Blocks.WATER.defaultBlockState());
                    p.set(x, b - 1, z, d < 2 ? Blocks.SEA_LANTERN.defaultBlockState() : Blocks.CLAY.defaultBlockState());
                    if (hh == 0) p.set(x, b + 1, z, Blocks.LILY_PAD.defaultBlockState());
                    return;
                }
                p.set(x, b, z, hh < 3 ? Blocks.MOSS_BLOCK.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState());
                p.foundation(x, z, b - 1, 8);
                if (d > 8.5 && d <= 9.5 && hh % 2 == 0) p.set(x, b + 1, z, Royal.HEDGE_FLOWER);
                else if (d > 5 && d < 8 && hh == 1) p.set(x, b + 1, z, Blocks.LILY_OF_THE_VALLEY.defaultBlockState());
                else if (d > 5 && d < 8 && hh == 2) p.set(x, b + 1, z, Blocks.MOSS_CARPET.defaultBlockState());
            });
            for (int i = 0; i < 6; i++) {
                double a = Math.PI * 2 * i / 6;
                int x = gx + (int) Math.round(Math.cos(a) * 11), z = gz + (int) Math.round(Math.sin(a) * 11);
                for (int y = 1; y <= 3; y++) p.set(x, b + y, z, Blocks.CALCITE.defaultBlockState());
                p.set(x, b + 4, z, Blocks.END_ROD.defaultBlockState());
            }
        }

        @Override
        public boolean needsSpawn() {
            return true;
        }

        @Override
        public void spawn(ServerLevel level, CityLayout city) {
            for (int j = 0; j < 3; j++) {
                Mob m = EntityType.ALLAY.spawn(level, new BlockPos(gx + j - 1, groundY + 3, gz + 6), MobSpawnType.STRUCTURE);
                if (m != null) {
                    m.setPersistenceRequired();
                    m.setCustomName(Component.literal("정원의 정령"));
                }
            }
        }
    }

    /** 외곽 결계석: 마수를 막는 하얀 방첨탑 (자수정 머리, 엔드 막대, 영혼 등불) */
    public static class BarrierStone extends Feature {
        private final int sx, sz;

        public BarrierStone(int sx, int sz) {
            super(sx - 3, sz - 3, sx + 3, sz + 3);
            this.sx = sx;
            this.sz = sz;
        }

        @Override
        public void build(Placer p, CityLayout city) {
            final int b = groundY;
            p.forColumns(minX, minZ, maxX, maxZ, (x, z) -> {
                p.clearAbove(x, z, b + 1);
                p.set(x, b, z, Math.abs(x - sx) <= 1 && Math.abs(z - sz) <= 1 ? Blocks.POLISHED_DIORITE.defaultBlockState() : Blocks.MOSS_BLOCK.defaultBlockState());
                p.foundation(x, z, b - 1, 8);
            });
            for (int y = 1; y <= 8; y++) p.set(sx, b + y, sz, y == 1 ? Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState() : y == 8 ? Blocks.AMETHYST_BLOCK.defaultBlockState() : Blocks.QUARTZ_PILLAR.defaultBlockState());
            p.set(sx, b + 9, sz, Blocks.END_ROD.defaultBlockState());
            for (int[] d : new int[][]{{2, 0}, {-2, 0}, {0, 2}, {0, -2}}) p.set(sx + d[0], b + 1, sz + d[1], Blocks.SOUL_LANTERN.defaultBlockState());
        }
    }
}
