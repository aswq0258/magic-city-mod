package io.github.aswq0258.magiccity.city.feature;

import io.github.aswq0258.magiccity.build.Placer;
import io.github.aswq0258.magiccity.city.Deco;
import io.github.aswq0258.magiccity.city.Royal;
import io.github.aswq0258.magiccity.city.Shapes;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import static io.github.aswq0258.magiccity.city.Palette.*;

/**
 * 왕성 실내 꾸미기. 방 하나(직사각형 실내)를 종류에 맞게 채웁니다.
 * 좌표는 Frame 기준: u = 왼쪽→오른쪽, v = 문(앞)→안쪽(뒤). 붉은 카펫 + 금색 테두리, 짙은 참나무 가구, 금 샹들리에.
 */
public final class RoyalRooms {
    private RoyalRooms() {}

    public enum Kind { GRAND_HALL, LIBRARY, BANQUET, BEDCHAMBER, STUDY, CHAPEL, TREASURY, COUNCIL, MESS, DORM, ARMORY, ARCANE, ARCHIVE, OBSERVATORY }

    @FunctionalInterface
    public interface Blocked {
        boolean at(int u, int v);
    }

    /**
     * @param f       실내 범위 Frame (벽 안쪽)
     * @param floorY  바닥 블록의 y (가구는 floorY+1 부터)
     * @param fh      층고 (천장 = floorY + fh)
     */
    public static void furnish(Placer p, Kind kind, Frame f, int floorY, int fh, long seed, Blocked blocked) {
        final int W = f.width, D = f.depth, y = floorY + 1, ceil = floorY + fh;
        final int cu = W / 2;
        Put put = (u, v, dy, s) -> {
            if (u < 0 || v < 0 || u >= W || v >= D || blocked.at(u, v)) return;
            f.set(p, floorY, u, 1 + dy, v, s);
        };

        // 1) 벽을 따라 놓는 가구
        BlockState[][] wall = wallItems(kind);
        if (wall.length > 0) {
            int i = (int) Math.floorMod(seed, (long) wall.length);
            for (int u = 0; u < W; u++) {
                for (int v = 0; v < D; v++) {
                    boolean edge = u == 0 || u == W - 1 || v == D - 1 || (v == 0 && Math.abs(u - cu) > 3);
                    if (!edge || blocked.at(u, v)) continue;
                    if ((u + v) % 2 == 1) continue;
                    BlockState[] stack = wall[i++ % wall.length];
                    Direction in = Deco.toward(f.x(cu, D / 2) - f.x(u, v), f.z(cu, D / 2) - f.z(u, v));
                    for (int k = 0; k < stack.length && k < fh - 3; k++) put.at(u, v, k, face(stack[k], in, u, v, seed));
                }
            }
        }

        // 2) 방 종류별 가운데 배치
        switch (kind) {
            case GRAND_HALL -> {
                runner(put, cu, 1, D - 2);
                for (int v = 3; v < D - 2; v += 5) {
                    for (int u : new int[]{cu - 5, cu + 5}) {
                        if (blocked.at(u, v)) continue;
                        for (int dy = 0; dy < fh - 1; dy++) put.at(u, v, dy, dy == 0 ? CHISELED_QUARTZ : dy == fh - 2 ? Royal.GOLD : QUARTZ_PILLAR);
                    }
                }
            }
            case LIBRARY, ARCHIVE -> {
                for (int v = 3; v < D - 2; v += 4) {
                    for (int u = 2; u < W - 2; u++) {
                        if (Math.abs(u - cu) <= 1) continue;
                        for (int dy = 0; dy < Math.min(3, fh - 3); dy++) put.at(u, v, dy, BOOKSHELF);
                    }
                    if (kind == Kind.LIBRARY) {
                        put.at(2, v + 1, 0, Deco.lectern(f.right));
                        put.at(W - 3, v + 1, 0, Deco.lectern(f.left));
                    }
                }
                runner(put, cu, 1, D - 2);
            }
            case BANQUET -> {
                Royal.rug(p, f.x(cu - 3, 2), f.z(cu - 3, 2), f.x(cu + 3, D - 3), f.z(cu + 3, D - 3), y);
                BlockState top = Deco.topSlab(Blocks.DARK_OAK_SLAB);
                for (int v = 3; v <= D - 4; v++) {
                    put.at(cu, v, 0, top);
                    if (v % 3 == 0) put.at(cu, v, 1, Deco.candles(4));
                    put.at(cu - 1, v, 0, Royal.chair(f.left));
                    put.at(cu + 1, v, 0, Royal.chair(f.right));
                }
                put.at(cu, D - 3, 0, Royal.GOLD);
            }
            case BEDCHAMBER -> {
                for (int side = -1; side <= 1; side += 2) {
                    int bu = cu + side * Math.max(4, W / 4);
                    Royal.rug(p, f.x(bu - 3, D - 9), f.z(bu - 3, D - 9), f.x(bu + 3, D - 3), f.z(bu + 3, D - 3), y);
                    for (int du = 0; du <= 1; du++) {
                        put.at(bu + du, D - 2, 0, Royal.redBed(f.back, true));
                        put.at(bu + du, D - 3, 0, Royal.redBed(f.back, false));
                    }
                    int[][] posts = {{bu - 1, D - 2}, {bu + 2, D - 2}, {bu - 1, D - 4}, {bu + 2, D - 4}};
                    for (int[] q : posts) for (int dy = 0; dy <= 2; dy++) put.at(q[0], q[1], dy, Royal.DARK_FENCE);
                    for (int u = bu - 1; u <= bu + 2; u++) {
                        for (int v = D - 4; v <= D - 2; v++) {
                            boolean edge = u == bu - 1 || u == bu + 2 || v == D - 4 || v == D - 2;
                            put.at(u, v, 3, edge ? Royal.GOLD : Royal.RED_WOOL);
                        }
                    }
                    put.at(bu - 2, D - 2, 0, Deco.BARREL);
                    put.at(bu - 2, D - 2, 1, Deco.candles(3));
                    put.at(bu + 3, D - 2, 0, Deco.BARREL);
                    put.at(bu + 3, D - 2, 1, Deco.candles(3));
                }
                Royal.table(p, f.x(cu, 4), y, f.z(cu, 4));
                put.at(cu - 1, 4, 0, Royal.chair(f.left));
                put.at(cu + 1, 4, 0, Royal.chair(f.right));
                put.at(cu, 5, 0, Royal.chair(f.back));
            }
            case STUDY, OBSERVATORY -> {
                Royal.rug(p, f.x(cu - 3, 2), f.z(cu - 3, 2), f.x(cu + 3, D - 3), f.z(cu + 3, D - 3), y);
                for (int du = -1; du <= 1; du++) Royal.table(p, f.x(cu + du, D - 4), y, f.z(cu + du, D - 4));
                put.at(cu, D - 5, 0, Royal.chair(f.front));
                put.at(cu - 3, 3, 0, kind == Kind.OBSERVATORY ? ENCHANTING_TABLE : Blocks.CARTOGRAPHY_TABLE.defaultBlockState());
                put.at(cu + 3, 3, 0, Deco.lectern(f.front));
                put.at(cu, 3, 0, ENCHANTING_TABLE);
            }
            case CHAPEL -> {
                runner(put, cu, 1, D - 4);
                for (int v = 3; v <= D - 7; v += 2) {
                    for (int u = 2; u < W - 2; u++) {
                        if (Math.abs(u - cu) <= 2) continue;
                        put.at(u, v, 0, Royal.chair(f.front));
                    }
                }
                for (int du = -3; du <= 3; du++) {
                    put.at(cu + du, D - 3, 0, du == 0 ? Royal.GOLD : CHISELED_QUARTZ);
                    put.at(cu + du, D - 3, 1, Deco.candles(4));
                }
                put.at(cu, D - 5, 0, Deco.lectern(f.front));
                for (int dy = 0; dy <= Math.min(8, fh - 3); dy++) put.at(cu, D - 2, dy, dy == Math.min(8, fh - 3) ? Royal.GOLD : QUARTZ_PILLAR);
            }
            case TREASURY -> {
                for (int u = 2; u < W - 2; u += 3) {
                    for (int v = 2; v < D - 2; v += 3) {
                        if (Math.abs(u - cu) <= 1) continue;
                        put.at(u, v, 0, CHISELED_QUARTZ);
                        put.at(u, v, 1, Royal.TREASURES[Shapes.hash(u, 31, v, seed) % Royal.TREASURES.length]);
                    }
                }
                runner(put, cu, 1, D - 2);
            }
            case COUNCIL -> {
                Royal.rug(p, f.x(cu - 4, 2), f.z(cu - 4, 2), f.x(cu + 4, D - 3), f.z(cu + 4, D - 3), y);
                BlockState top = Deco.topSlab(Blocks.DARK_OAK_SLAB);
                for (int v = 4; v <= D - 5; v++) {
                    for (int du = 0; du <= 1; du++) put.at(cu - 1 + du * 2, v, 0, top);
                    put.at(cu, v, 0, top);
                    if (v % 2 == 0) {
                        put.at(cu - 2, v, 0, Royal.chair(f.left));
                        put.at(cu + 2, v, 0, Royal.chair(f.right));
                    }
                    if (v % 4 == 0) put.at(cu, v, 1, Deco.candles(3));
                }
                put.at(cu, D - 4, 0, Royal.chair(f.back));
                put.at(cu - 1, D - 4, 0, Royal.GOLD);
                put.at(cu + 1, D - 4, 0, Royal.GOLD);
                put.at(cu, 3, 0, Blocks.CARTOGRAPHY_TABLE.defaultBlockState());
            }
            case MESS -> {
                for (int v = 3; v + 1 <= D - 3; v += 4) {
                    for (int u = 2; u + 1 < W - 2; u += 5) {
                        if (Math.abs(u - cu) <= 1 || Math.abs(u + 1 - cu) <= 1) continue;
                        for (int du = 0; du <= 1; du++) {
                            Royal.table(p, f.x(u + du, v), y, f.z(u + du, v));
                            put.at(u + du, v - 1, 0, Royal.chair(f.front));
                            put.at(u + du, v + 1, 0, Royal.chair(f.back));
                        }
                    }
                }
            }
            case DORM -> {
                for (int u = 2; u < W - 2; u += 3) {
                    if (Math.abs(u - cu) <= 1) continue;
                    for (int v = 2; v + 1 < D - 1; v += 4) {
                        put.at(u, v + 1, 0, Royal.redBed(f.back, true));
                        put.at(u, v, 0, Royal.redBed(f.back, false));
                        put.at(u + 1, v + 1, 0, Deco.chest(f.front));
                    }
                }
            }
            case ARMORY -> {
                BlockState[] tools = {Blocks.ANVIL.defaultBlockState(), Blocks.GRINDSTONE.defaultBlockState(), Blocks.SMITHING_TABLE.defaultBlockState(), Deco.BARREL};
                for (int v = 3; v < D - 2; v += 3) {
                    for (int u = 2; u < W - 2; u += 2) {
                        if (Math.abs(u - cu) <= 1) continue;
                        put.at(u, v, 0, tools[Shapes.hash(u, 7, v, seed) % tools.length]);
                    }
                }
            }
            case ARCANE -> {
                for (int[] a : new int[][]{{cu - 5, 4}, {cu + 5, 4}, {cu - 5, D - 6}, {cu + 5, D - 6}}) {
                    put.at(a[0], a[1], 0, ENCHANTING_TABLE);
                    for (int du = -2; du <= 2; du++) {
                        for (int dv = -2; dv <= 2; dv++) {
                            if (Math.max(Math.abs(du), Math.abs(dv)) != 2 || (du == 0 && dv == -2)) continue;
                            put.at(a[0] + du, a[1] + dv, 0, BOOKSHELF);
                            put.at(a[0] + du, a[1] + dv, 1, BOOKSHELF);
                        }
                    }
                }
                for (int v = 3; v < D - 2; v += 3) {
                    put.at(cu - 1, v, 0, Deco.BREWING);
                    put.at(cu + 1, v, 0, Blocks.AMETHYST_CLUSTER.defaultBlockState());
                }
                runner(put, cu, 1, D - 2);
            }
        }

        // 3) 샹들리에
        int chain = Math.max(0, fh - 7);
        for (int u = 3; u < W - 3; u += 7) {
            for (int v = 3; v < D - 3; v += 7) {
                if (blocked.at(u, v)) continue;
                Royal.chandelier(p, f.x(u, v), ceil, f.z(u, v), chain);
            }
        }
    }

    @FunctionalInterface
    private interface Put {
        void at(int u, int v, int dy, BlockState s);
    }

    /** 문에서 안쪽까지 붉은 카펫 (폭 5, 금색 테두리) */
    private static void runner(Put put, int cu, int v0, int v1) {
        for (int v = v0; v <= v1; v++) {
            for (int du = -2; du <= 2; du++) put.at(cu + du, v, 0, Math.abs(du) == 2 ? Royal.GOLD_CARPET : Royal.RED_CARPET);
        }
    }

    private static final BlockState POT = Blocks.FLOWER_POT.defaultBlockState();
    private static final BlockState CANDLE = Blocks.CANDLE.defaultBlockState();
    private static final BlockState BANNER = Blocks.RED_BANNER.defaultBlockState();

    private static BlockState[] s(BlockState... b) {
        return b;
    }

    private static BlockState[][] wallItems(Kind k) {
        BlockState shelf = BOOKSHELF, chest = Deco.chest(Direction.NORTH), barrel = Deco.BARREL;
        return switch (k) {
            case GRAND_HALL -> new BlockState[][]{s(CHISELED_QUARTZ, Royal.GOLD), s(POT), s(CHISELED_QUARTZ, CANDLE)};
            case LIBRARY, ARCHIVE -> new BlockState[][]{s(shelf, shelf, shelf, CANDLE), s(shelf, shelf, shelf)};
            case BANQUET -> new BlockState[][]{s(barrel, CANDLE), s(Deco.WATER_CAULDRON), s(POT)};
            case BEDCHAMBER -> new BlockState[][]{s(chest), s(shelf, shelf), s(POT)};
            case STUDY, OBSERVATORY -> new BlockState[][]{s(shelf, shelf, shelf), s(POT), s(chest)};
            case CHAPEL -> new BlockState[][]{s(CHISELED_QUARTZ, CANDLE), s(POT)};
            case TREASURY -> new BlockState[][]{s(chest), s(Royal.GOLD, Royal.GOLD), s(chest)};
            case COUNCIL -> new BlockState[][]{s(shelf, shelf, CANDLE), s(POT), s(Deco.lectern(Direction.NORTH))};
            case MESS -> new BlockState[][]{s(barrel, barrel), s(Blocks.GRINDSTONE.defaultBlockState()), s(Blocks.SMITHING_TABLE.defaultBlockState()), s(chest)};
            case DORM -> new BlockState[][]{s(chest), s(barrel)};
            case ARMORY -> new BlockState[][]{s(chest), s(barrel, barrel), s(Blocks.ANVIL.defaultBlockState())};
            case ARCANE -> new BlockState[][]{s(shelf, shelf, shelf, shelf), s(Deco.BREWING), s(Blocks.AMETHYST_CLUSTER.defaultBlockState())};
        };
    }

    private static BlockState face(BlockState s, Direction in, int u, int v, long seed) {
        if (s == POT) return Deco.pot(Shapes.hash(u, 17, v, seed));
        if (s == CANDLE) return Deco.candles(2 + Shapes.hash(u, 18, v, seed) % 3);
        if (s == BANNER) return Royal.redBanner(in);
        if (s.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING)) {
            return s.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, in);
        }
        return s;
    }
}
