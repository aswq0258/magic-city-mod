package io.github.aswq0258.magiccity.city.feature;

import io.github.aswq0258.magiccity.build.Placer;
import io.github.aswq0258.magiccity.city.Chateau;
import io.github.aswq0258.magiccity.city.CityLayout;
import io.github.aswq0258.magiccity.city.Deco;
import io.github.aswq0258.magiccity.city.Palette;
import io.github.aswq0258.magiccity.city.Royal;
import io.github.aswq0258.magiccity.city.Shapes;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;

import static io.github.aswq0258.magiccity.city.Palette.*;

/**
 * 모험가 길드 본관 — 흰 도시 속에서 한눈에 보이는 진홍·금·짙은 나무
 *  - 1층 이끼 낀 돌, 2·3층 짙은 참나무 골조 + 붉은 테라코타 벽 + 노란 유리창
 *  - 진홍 벽돌 모임지붕 + 금 용마루, 앞 왼쪽 모서리에 높은 망루(진홍 지붕·길드 깃발)
 *  - 정문 위 거대한 길드 문장(붉은 방패 + 금 테두리 + 엇갈린 두 검), 양옆 화로
 *  - 1층 선술집·접수대·의뢰 게시판·벽난로 / 2층 숙소 / 3층 길드장 방
 * 정면은 남쪽(왕성 쪽) 고정.
 */
public class GuildHall extends Feature {
    private static final int W = 31, D = 21, FH = 6, FLOORS = 3;
    private static final BlockState[] STONE = {Blocks.STONE_BRICKS.defaultBlockState(), Blocks.MOSSY_STONE_BRICKS.defaultBlockState(),
            Blocks.CRACKED_STONE_BRICKS.defaultBlockState(), Blocks.STONE_BRICKS.defaultBlockState()};
    private static final BlockState POST = Blocks.DARK_OAK_LOG.defaultBlockState();
    private static final BlockState BEAM = Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState()
            .setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS, Direction.Axis.X);
    private static final BlockState INFILL = Blocks.RED_TERRACOTTA.defaultBlockState();
    private static final BlockState WINDOW = Blocks.YELLOW_STAINED_GLASS_PANE.defaultBlockState();
    private static final BlockState ROOF = Blocks.RED_NETHER_BRICKS.defaultBlockState();
    private static final BlockState RED = Blocks.RED_WOOL.defaultBlockState();
    private final Frame f;
    private final long seed;

    public GuildHall(int x0, int z0, int x1, int z1, long seed) {
        super(Math.min(x0, x1) - 4, Math.min(z0, z1) - 4, Math.max(x0, x1) + 4, Math.max(z0, z1) + 4);
        this.f = new Frame((x0 + x1) >> 1, (z0 + z1) >> 1, Direction.SOUTH, W, D);
        this.seed = seed;
    }

    private static BlockState redStairs(Direction d) {
        return Blocks.RED_NETHER_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, d);
    }

    @Override
    public void build(Placer p, CityLayout city) {
        final int b = groundY, top = FH * FLOORS;
        final int door = W / 2;
        for (int u = 0; u < W; u++) {
            for (int v = 0; v < D; v++) {
                boolean edgeU = u == 0 || u == W - 1, edgeV = v == 0 || v == D - 1, edge = edgeU || edgeV;
                int along = edgeV ? u : v;
                f.set(p, b, u, 0, v, edge ? STONE[0] : Royal.DARK_PLANKS);
                for (int ly = 1; ly <= top; ly++) {
                    BlockState s = AIR;
                    if (edge) {
                        int local = ly % FH;
                        boolean post = (edgeU && edgeV) || along % 5 == 0;
                        if (ly <= FH) {
                            s = STONE[Shapes.hash(f.x(u, v), ly, f.z(u, v), seed) % STONE.length];
                            if (!post && local >= 2 && local <= 4 && along % 5 == 2) s = WINDOW;
                        } else if (local == 0) s = BEAM;
                        else if (post) s = POST;
                        else if (local >= 2 && local <= 4 && (along % 5 == 2 || along % 5 == 3)) s = WINDOW;
                        else s = INFILL;
                        if (v == 0 && Math.abs(u - door) <= 1 && ly <= 4) s = AIR;                 // 정문
                    } else if (ly % FH == 0) {
                        s = (u >= W - 4 && v >= 2 && v <= FH + 1) ? AIR : Royal.DARK_PLANKS;        // 층 바닥 (계단 구멍)
                    }
                    f.set(p, b, u, ly, v, s);
                }
            }
        }
        // 계단 (오른쪽 벽을 따라)
        for (int k = 0; k + 1 < FLOORS; k++) {
            for (int j = 1; j <= FH; j++) {
                for (int u = W - 4; u <= W - 3; u++) f.set(p, b + k * FH, u, j, 1 + j, Deco.woodStairs(f.back));
            }
        }
        // 진홍 지붕
        int ridge = Chateau.hipRoof(p, f.minX, f.minZ, f.maxX, f.maxZ, b + top + 1, 12, 0, 0, 0, ROOF, GuildHall::redStairs, Royal.GOLD);
        Chateau.chimney(p, f.x(W - 6, D - 4), f.z(W - 6, D - 4), b + top + 1, ridge + 2, Direction.EAST);
        emblem(p, b);
        tower(p, b);
        // 정문 화로·계단·랜턴
        for (int s = -1; s <= 1; s += 2) {
            int u = door + s * 3;
            f.set(p, b, u, 1, -1, Blocks.COBBLESTONE_WALL.defaultBlockState());
            f.set(p, b, u, 2, -1, Deco.CAMPFIRE);
            f.set(p, b, door + s * 2, 5, -1, Blocks.LANTERN.defaultBlockState());
            f.set(p, b, door + s * 9, 11, -1, Royal.redBanner(Direction.SOUTH));
        }
        for (int u = door - 2; u <= door + 2; u++) f.set(p, b, u, 0, -1, Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH));
        interior(p, b);
    }

    /** 정문 위 길드 문장: 붉은 방패, 금 테두리, 엇갈린 두 검 */
    private void emblem(Placer p, int b) {
        int cu = W / 2;
        for (int ly = 7; ly <= 17; ly++) {
            int k = ly - 7;
            int half = k < 7 ? 5 : 5 - (k - 6);
            for (int du = -half; du <= half; du++) {
                boolean border = Math.abs(du) == half || ly == 17;
                BlockState s = border ? Royal.GOLD : RED;
                int sx = du, sy = ly - 12;
                if (Math.abs(sx - sy) <= 0 || Math.abs(sx + sy) <= 0) s = Math.abs(sy) >= 3 ? Royal.GOLD : Blocks.IRON_BLOCK.defaultBlockState();   // 두 검
                f.set(p, b, cu + du, ly, -1, s);
            }
        }
        f.set(p, b, cu, 18, -1, Royal.GOLD);
    }

    /** 앞 왼쪽 모서리 망루: 돌 사각탑 + 진홍 모임지붕 + 깃대 */
    private void tower(Placer p, int b) {
        int H = 30;
        for (int u = -2; u <= 4; u++) {
            for (int v = -2; v <= 4; v++) {
                boolean edge = u == -2 || u == 4 || v == -2 || v == 4;
                for (int ly = 0; ly <= H; ly++) {
                    BlockState s = edge ? (ly % 8 == 4 && (u == 1 || v == 1) ? WINDOW : STONE[Shapes.hash(u, ly, v, seed) % STONE.length]) : (ly % 8 == 0 ? Royal.DARK_PLANKS : AIR);
                    if (edge && ly == H - 1) s = Royal.GOLD;
                    f.set(p, b, u, ly, v, s);
                }
            }
        }
        for (int ly = 1; ly < H; ly++) f.set(p, b, 3, ly, 1, Palette.ladder(Direction.WEST));
        int x0 = f.x(-2, -2), z0 = f.z(-2, -2), x1 = f.x(4, 4), z1 = f.z(4, 4);
        int top = Chateau.hipRoof(p, x0, z0, x1, z1, b + H + 1, 14, 0, 0, 0, ROOF, GuildHall::redStairs, Royal.GOLD);
        int px = f.x(1, 1), pz = f.z(1, 1);
        for (int y = top; y <= top + 6; y++) p.set(px, y, pz, Royal.DARK_FENCE);
        p.set(px + 1, top + 5, pz, Royal.redBanner(Direction.EAST));
        p.set(px - 1, top + 5, pz, Royal.redBanner(Direction.WEST));
    }

    private void interior(Placer p, int b) {
        // 1층: 선술집 탁자, 접수대, 벽난로, 의뢰 게시판
        for (int u = 3; u <= 13; u += 5) {
            for (int v = 4; v <= 14; v += 5) {
                Deco.table(p, f.x(u, v), b + 1, f.z(u, v));
                f.set(p, b, u - 1, 1, v, Deco.chair(f.right));
                f.set(p, b, u + 1, 1, v, Deco.chair(f.left));
            }
        }
        for (int u = 17; u <= 25; u++) f.set(p, b, u, 1, 12, u == 21 ? AIR : Royal.DARK_PLANKS);       // 접수대
        f.set(p, b, 18, 2, 12, Deco.candles(3));
        f.set(p, b, 24, 2, 12, Deco.lectern(f.front));
        for (int u = 17; u <= 25; u += 2) f.set(p, b, u, 1, 18, Deco.BARREL);
        for (int u = 1; u <= 7; u++) for (int ly = 2; ly <= 4; ly++) {                           // 의뢰 게시판 (뒷벽)
            f.set(p, b, u, ly, D - 2, (u + ly) % 2 == 0 ? Royal.DARK_PLANKS : Blocks.WHITE_WALL_BANNER.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.WallBannerBlock.FACING, f.front));
        }
        for (int ly = 1; ly <= 3; ly++) f.set(p, b, 12, ly, D - 2, ly == 1 ? Deco.CAMPFIRE : Blocks.BRICKS.defaultBlockState());   // 벽난로
        f.set(p, b, 12, 4, D - 2, Blocks.BRICKS.defaultBlockState());
        // 2층: 숙소 (붉은 침대)
        for (int u = 2; u <= W - 6; u += 3) {
            f.set(p, b, u, FH + 1, D - 2, Royal.redBed(f.front, true));
            f.set(p, b, u, FH + 1, D - 3, Royal.redBed(f.front, false));
            f.set(p, b, u + 1, FH + 1, D - 2, Deco.chest(f.front));
        }
        // 3층: 길드장 방
        Royal.rug(p, f.x(8, 6), f.z(8, 6), f.x(20, 14), f.z(20, 14), b + FH * 2);
        f.set(p, b, 14, FH * 2 + 1, 15, Blocks.CARTOGRAPHY_TABLE.defaultBlockState());
        f.set(p, b, 14, FH * 2 + 1, 16, Royal.chair(f.back));
        for (int u = 2; u <= 6; u++) f.set(p, b, u, FH * 2 + 1, D - 2, BOOKSHELF);
        f.set(p, b, 9, FH * 2 + 1, D - 2, Royal.GOLD);
        Royal.chandelier(p, f.x(W / 2, D / 2), b + FH, f.z(W / 2, D / 2), 2);
    }
}
