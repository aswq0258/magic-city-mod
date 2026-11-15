package io.github.aswq0258.magiccity.city.feature;

import io.github.aswq0258.magiccity.build.Placer;
import io.github.aswq0258.magiccity.city.CityLayout;
import io.github.aswq0258.magiccity.city.Deco;
import io.github.aswq0258.magiccity.city.Palette;
import io.github.aswq0258.magiccity.city.Royal;
import io.github.aswq0258.magiccity.city.Shapes;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.event.EventHooks;

import java.util.ArrayList;
import java.util.List;

import static io.github.aswq0258.magiccity.city.Palette.*;

/**
 * 역할이 있는 도시 건물. 외관은 도시 공통 양식(흰 석영 + 민트 지붕), 내부는 역할에 맞게 층마다 꾸밉니다.
 *
 *  SHOP      상점      1층 매장(카운터·진열장) / 2층 살림집
 *  INN       여관      1층 선술집(술통 바·테이블) / 2~3층 객실
 *  GUILD     모험가 길드 1층 접수·의뢰 게시판·테이블 / 2층 자료실 / 3층 숙소, 정면에 깃발
 *  BARRACKS  병영      1층 식당·무기 손질 / 2층 침상, 깃발
 *  ARMORY    군수창고  무기 손질대·상자·통
 *  WAREHOUSE 창고      통·상자·건초 더미
 *  STABLE    마구간    칸막이 마구간, 건초와 물통, 말
 *  SMITHY    대장간    용광로·화로(마그마)·모루·숫돌, 큰 굴뚝
 *  WORKSHOP  공방      작업대·베틀·석재절단기·지도제작대 / 2층 살림집
 *  MANSION   귀족 저택 붉은 카펫, 1층 연회 식당 / 2층 서재 / 3층 침실, 앞뜰 분수
 */
public class RoleBuilding extends Feature {
    public enum Role { SHOP, INN, GUILD, BARRACKS, ARMORY, WAREHOUSE, STABLE, SMITHY, WORKSHOP, MANSION }

    private enum Kind { TABLES, BEDS, SHELVES, BARRELS, COUNTER, FORGE, STALLS, DINING_LONG, NONE }

    private record Plan(BlockState[][] wall, Kind kind, BlockState carpet) {}

    private static final BlockState POT = Blocks.FLOWER_POT.defaultBlockState(); // 자리표시: 화분 종류는 위치마다 달라짐
    private static final BlockState CANDLE = Blocks.CANDLE.defaultBlockState();  // 자리표시: 불 켜진 흰 양초

    private final Role role;
    private final Frame f;
    private final int floors, fh;
    private final BlockState wallMat;
    private final boolean villager;
    private final long seed;

    public RoleBuilding(CityLayout.Lot lot, Role role, RandomSource rand, boolean villager) {
        this(lot, role, dims(role), rand.nextBoolean() ? SMOOTH_QUARTZ : CALCITE, villager, rand.nextLong());
    }

    private RoleBuilding(CityLayout.Lot lot, Role role, int[] dims, BlockState wallMat, boolean villager, long seed) {
        this(role, new Frame(lot.centerX(), lot.centerZ(), lot.front(), dims[0], dims[1]), dims[2], dims[3], wallMat, villager, seed);
    }

    private RoleBuilding(Role role, Frame f, int floors, int fh, BlockState wallMat, boolean villager, long seed) {
        super(f.minX - margin(role), f.minZ - margin(role), f.maxX + margin(role), f.maxZ + margin(role));
        this.role = role;
        this.f = f;
        this.floors = floors;
        this.fh = fh;
        this.wallMat = role == Role.MANSION ? SMOOTH_QUARTZ : wallMat;
        this.villager = villager;
        this.seed = seed;
    }

    /** {폭 W, 깊이 D, 층수, 층고} */
    private static int[] dims(Role r) {
        return switch (r) {
            case SHOP -> new int[]{11, 9, 2, 5};
            case INN -> new int[]{15, 13, 3, 5};
            case GUILD -> new int[]{31, 21, 3, 6};
            case BARRACKS -> new int[]{21, 13, 2, 5};
            case ARMORY -> new int[]{19, 13, 2, 5};
            case WAREHOUSE -> new int[]{21, 15, 1, 8};
            case STABLE -> new int[]{21, 11, 1, 6};
            case SMITHY -> new int[]{13, 11, 1, 7};
            case WORKSHOP -> new int[]{13, 11, 2, 5};
            case MANSION -> new int[]{25, 15, 3, 6};
        };
    }

    private static int margin(Role r) {
        return r == Role.MANSION ? 13 : 2;
    }

    // ───────────────────────── 층별 계획 ─────────────────────────

    private static BlockState[] s(BlockState... b) {
        return b;
    }

    private Plan plan(int k) {
        BlockState shelf = BOOKSHELF, barrel = Deco.BARREL, chest = Deco.chest(Direction.NORTH);
        return switch (role) {
            case SHOP -> k == 0
                    ? new Plan(new BlockState[][]{s(barrel, POT), s(shelf, shelf), s(chest), s(barrel, CANDLE), s(Deco.lectern(Direction.NORTH))}, Kind.COUNTER, Deco.CARPET_MINT)
                    : new Plan(new BlockState[][]{s(shelf, shelf), s(chest), s(POT)}, Kind.BEDS, Deco.CARPET_WHITE);
            case INN -> k == 0
                    ? new Plan(new BlockState[][]{s(barrel, barrel), s(Deco.BREWING), s(Deco.WATER_CAULDRON), s(barrel, CANDLE), s(POT), s(shelf, shelf)}, Kind.TABLES, Deco.CARPET_WHITE)
                    : new Plan(new BlockState[][]{s(chest), s(barrel, CANDLE), s(POT), s(shelf)}, Kind.BEDS, Deco.CARPET_MINT);
            case GUILD -> switch (k) {
                case 0 -> new Plan(new BlockState[][]{s(shelf, shelf, shelf), s(Deco.lectern(Direction.NORTH)), s(barrel, CANDLE), s(POT), s(shelf, shelf, CANDLE)}, Kind.TABLES, Deco.CARPET_MINT);
                case 1 -> new Plan(new BlockState[][]{s(shelf, shelf, shelf), s(Deco.lectern(Direction.NORTH)), s(POT)}, Kind.SHELVES, Deco.CARPET_WHITE);
                default -> new Plan(new BlockState[][]{s(chest), s(barrel), s(POT)}, Kind.BEDS, Deco.CARPET_MINT);
            };
            case BARRACKS -> k == 0
                    ? new Plan(new BlockState[][]{s(barrel), s(Blocks.GRINDSTONE.defaultBlockState()), s(Blocks.SMITHING_TABLE.defaultBlockState()), s(chest), s(barrel, barrel)}, Kind.TABLES, null)
                    : new Plan(new BlockState[][]{s(chest), s(barrel)}, Kind.BEDS, Deco.CARPET_WHITE);
            case ARMORY -> k == 0
                    ? new Plan(new BlockState[][]{s(Blocks.ANVIL.defaultBlockState()), s(Blocks.GRINDSTONE.defaultBlockState()), s(Blocks.SMITHING_TABLE.defaultBlockState()), s(chest), s(barrel, barrel)}, Kind.BARRELS, null)
                    : new Plan(new BlockState[][]{s(chest), s(barrel, barrel)}, Kind.BARRELS, null);
            case WAREHOUSE -> new Plan(new BlockState[][]{s(barrel, barrel, barrel), s(chest), s(Blocks.HAY_BLOCK.defaultBlockState(), Blocks.HAY_BLOCK.defaultBlockState())}, Kind.BARRELS, null);
            case STABLE -> new Plan(new BlockState[0][], Kind.STALLS, null);
            case SMITHY -> new Plan(new BlockState[][]{s(Blocks.FURNACE.defaultBlockState()), s(Blocks.BLAST_FURNACE.defaultBlockState()), s(Blocks.ANVIL.defaultBlockState()),
                    s(Blocks.GRINDSTONE.defaultBlockState()), s(Blocks.SMITHING_TABLE.defaultBlockState()), s(barrel), s(chest)}, Kind.FORGE, null);
            case WORKSHOP -> k == 0
                    ? new Plan(new BlockState[][]{s(Deco.CRAFTING), s(Blocks.LOOM.defaultBlockState()), s(Blocks.STONECUTTER.defaultBlockState()), s(Blocks.CARTOGRAPHY_TABLE.defaultBlockState()),
                    s(Blocks.FLETCHING_TABLE.defaultBlockState()), s(Blocks.SMITHING_TABLE.defaultBlockState()), s(barrel)}, Kind.TABLES, null)
                    : new Plan(new BlockState[][]{s(chest), s(barrel), s(shelf)}, Kind.BEDS, Deco.CARPET_WHITE);
            case MANSION -> switch (k) {
                case 0 -> new Plan(new BlockState[][]{s(shelf, shelf, CANDLE), s(POT), s(chest)}, Kind.DINING_LONG, Royal.RED_CARPET);
                case 1 -> new Plan(new BlockState[][]{s(shelf, shelf, shelf), s(Deco.lectern(Direction.NORTH)), s(POT)}, Kind.SHELVES, Royal.RED_CARPET);
                default -> new Plan(new BlockState[][]{s(chest), s(shelf, shelf), s(POT)}, Kind.BEDS, Royal.RED_CARPET);
            };
        };
    }

    // ───────────────────────── 건축 ─────────────────────────

    @Override
    public void build(Placer p, CityLayout city) {
        final int b = groundY;
        final int W = f.width, D = f.depth, top = floors * fh;
        final int doorU = W / 2, stairU = W - 2;
        final boolean mansion = role == Role.MANSION;

        if (mansion) frontGarden(p, b);

        // 1. 벽, 바닥, 천장
        for (int u = 0; u < W; u++) {
            for (int v = 0; v < D; v++) {
                if (!p.contains(f.x(u, v), f.z(u, v))) continue;
                boolean edge = u == 0 || u == W - 1 || v == 0 || v == D - 1;
                if (edge) {
                    f.set(p, b, u, 0, v, QUARTZ_BRICKS);
                    for (int ly = 1; ly <= top; ly++) f.set(p, b, u, ly, v, wall(u, v, ly, W, D, doorU));
                } else {
                    f.set(p, b, u, 0, v, (role == Role.STABLE || role == Role.SMITHY) ? CALCITE
                            : mansion ? Royal.marble(u, v) : Deco.PLANKS);
                    for (int ly = 1; ly <= top; ly++) {
                        BlockState st = AIR;
                        if (ly == top) st = (u == doorU && v == D / 2) ? LIGHT : SMOOTH_QUARTZ;
                        else if (ly % fh == 0) st = (floors > 1 && u == stairU && v >= 2 && v <= fh) ? AIR : (mansion ? Royal.DARK_PLANKS : Deco.PLANKS);
                        f.set(p, b, u, ly, v, st);
                    }
                }
            }
        }

        // 2. 계단 (오른쪽 벽을 따라, 층마다 같은 자리) + 난간
        for (int k = 0; k + 1 < floors; k++) {
            int base = k * fh;
            for (int j = 1; j <= fh; j++) f.set(p, b, stairU, base + j, 1 + j, Deco.woodStairs(f.back));
            for (int v = 2; v <= fh; v++) f.set(p, b, stairU - 1, base + fh + 1, v, Deco.FENCE);
        }

        // 3. 층별 실내
        for (int k = 0; k < floors; k++) furnish(p, b, k, W, D, doorU);

        // 4. 처마 내물림 + 지붕 + 꼭대기 장식
        for (int u = -1; u <= W; u++) {
            for (int v = -1; v <= D; v++) {
                if (u >= 0 && u < W && v >= 0 && v < D) continue;
                Direction toWall = v == -1 ? f.back : v == D ? f.front : u == -1 ? f.right : f.left;
                f.set(p, b, u, top, v, Deco.corbel(toWall));
            }
        }
        int roofTop = Shapes.hipRoof(p, f.minX - 1, f.minZ - 1, f.maxX + 1, f.maxZ + 1, b + top + 1);
        p.set(f.centerX(), roofTop + 1, f.centerZ(), mansion ? Royal.GOLD : MINT_CHISELED);
        p.set(f.centerX(), roofTop + 2, f.centerZ(), END_ROD);

        // 5. 역할별 외부 장식
        switch (role) {
            case GUILD, BARRACKS -> {
                for (int du : new int[]{-2, 2}) {
                    f.set(p, b, doorU + du, 4, -1, Royal.mintBanner(f.front));
                    f.set(p, b, doorU + du, 2, -1, Deco.hangingLantern());
                }
            }
            case INN -> {
                f.set(p, b, doorU + 2, 3, -1, Royal.mintBanner(f.front));
                f.set(p, b, doorU - 2, 3, -1, Deco.hangingLantern());
                chimney(p, b, roofTop, 1);
            }
            case SMITHY -> {
                f.set(p, b, doorU + 2, 1, -1, Blocks.ANVIL.defaultBlockState());
                f.set(p, b, doorU - 2, 1, -1, Deco.BARREL);
                chimney(p, b, roofTop, 2);
                chimney(p, b, roofTop, W - 3);
            }
            case STABLE -> {
                f.set(p, b, doorU + 2, 1, -1, Blocks.HAY_BLOCK.defaultBlockState());
                f.set(p, b, doorU - 2, 1, -1, Deco.WATER_CAULDRON);
            }
            case MANSION -> chimney(p, b, roofTop, 2);
            default -> { }
        }
    }

    private void chimney(Placer p, int b, int roofTop, int u) {
        int D = f.depth;
        int h = roofTop - b + 2;
        for (int ly = 1; ly < h; ly++) f.set(p, b, u, ly, D, QUARTZ_BRICKS);
        f.set(p, b, u, h, D, Deco.CAMPFIRE);
    }

    private BlockState wall(int u, int v, int ly, int W, int D, int doorU) {
        boolean edgeU = u == 0 || u == W - 1, edgeV = v == 0 || v == D - 1;
        int local = ((ly - 1) % fh) + 1;
        if (edgeU && edgeV) return (local == fh) ? (role == Role.MANSION ? Royal.GOLD : CHISELED_QUARTZ) : QUARTZ_PILLAR;
        if (v == 0 && u == doorU && ly <= 4) {
            if (ly <= 2) return Palette.door(f.back, ly == 2);
            return ly == 3 ? GLASS_PANE : CHISELED_QUARTZ;
        }
        int along = edgeV ? u : v, len = edgeV ? W : D;
        if (role == Role.STABLE && v == 0 && along % 4 == 2 && ly <= 3) return AIR;                 // 마구간 출입구
        if (ly == 1) return QUARTZ_BRICKS;
        if (local == fh) return ly == floors * fh ? SMOOTH_QUARTZ : MINT_CUT;
        boolean window = along % 2 == 0 && along >= 2 && along <= len - 3;
        if (window) {
            int winTop = fh >= 6 ? 4 : 3;
            if (local >= 2 && local <= winTop) return role == Role.MANSION ? Royal.CLEAR_PANE : GLASS_PANE;
            if (local == winTop + 1) return role == Role.MANSION ? Royal.GOLD : CHISELED_QUARTZ;
        }
        return wallMat;
    }

    private boolean reserved(int k, int u, int v, int W, int doorU) {
        if (floors > 1 && u >= W - 3 && v <= fh + 1) return true;
        return k == 0 && Math.abs(u - doorU) <= 1 && v <= 2;
    }

    private void furnish(Placer p, int b, int k, int W, int D, int doorU) {
        Plan plan = plan(k);
        int ly = k * fh + 1;
        int ceiling = (k + 1) * fh;

        // 카펫
        if (plan.carpet() != null) {
            for (int u = 1; u <= W - 2; u++) {
                for (int v = 1; v <= D - 2; v++) if (!reserved(k, u, v, W, doorU)) f.set(p, b, u, ly, v, plan.carpet());
            }
        }

        // 벽을 따라 놓는 가구
        if (plan.wall().length > 0) {
            List<int[]> ring = new ArrayList<>();
            for (int u = 1; u <= W - 2; u++) ring.add(new int[]{u, 1});
            for (int v = 2; v <= D - 2; v++) ring.add(new int[]{W - 2, v});
            for (int u = W - 3; u >= 1; u--) ring.add(new int[]{u, D - 2});
            for (int v = D - 3; v >= 2; v--) ring.add(new int[]{1, v});
            int idx = (int) Math.floorMod(seed + k, (long) plan.wall().length);
            for (int[] c : ring) {
                int u = c[0], v = c[1];
                if (reserved(k, u, v, W, doorU)) continue;
                if (k == 0 && v == 1 && Math.abs(u - doorU) <= 2) continue;
                BlockState[] stack = plan.wall()[idx++ % plan.wall().length];
                Direction in = Deco.toward(f.x(W / 2, D / 2) - f.x(u, v), f.z(W / 2, D / 2) - f.z(u, v));
                for (int i = 0; i < stack.length && i < fh - 2; i++) {
                    f.set(p, b, u, ly + i, v, resolve(stack[i], in, u, v, k));
                }
            }
        }

        // 가운데 배치
        switch (plan.kind()) {
            case TABLES -> {
                for (int v = 3; v + 1 <= D - 4; v += 3) {
                    for (int u = 3; u + 1 <= W - 4; u += 4) {
                        if (reserved(k, u, v - 1, W, doorU) || reserved(k, u + 1, v + 1, W, doorU)) continue;
                        for (int du = 0; du <= 1; du++) {
                            Deco.table(p, f.x(u + du, v), b + ly, f.z(u + du, v));
                            f.set(p, b, u + du, ly, v - 1, Deco.chair(f.front));
                            f.set(p, b, u + du, ly, v + 1, Deco.chair(f.back));
                        }
                    }
                }
            }
            case BEDS -> {
                boolean red = role == Role.MANSION;
                for (int u = 3; u <= W - 4; u += 3) {
                    for (int v = 3; v + 1 <= D - 4; v += 4) {
                        if (reserved(k, u, v, W, doorU) || reserved(k, u, v + 1, W, doorU)) continue;
                        f.set(p, b, u, ly, v, red ? Royal.redBed(f.front, true) : Palette.bed(f.front, true));
                        f.set(p, b, u, ly, v + 1, red ? Royal.redBed(f.front, false) : Palette.bed(f.front, false));
                        if (u + 1 <= W - 4 && !reserved(k, u + 1, v, W, doorU)) {
                            f.set(p, b, u + 1, ly, v, Deco.BARREL);
                            f.set(p, b, u + 1, ly + 1, v, Deco.candles(2));
                        }
                    }
                }
            }
            case SHELVES -> {
                for (int v = 3; v <= D - 4; v += 3) {
                    for (int u = 3; u <= W - 4; u++) {
                        if ((u - 3) % 6 == 5 || reserved(k, u, v, W, doorU)) continue;
                        for (int i = 0; i < Math.min(3, fh - 2); i++) f.set(p, b, u, ly + i, v, BOOKSHELF);
                    }
                }
            }
            case BARRELS -> {
                for (int v = 3; v <= D - 4; v += 3) {
                    for (int u = 3; u <= W - 4; u++) {
                        if (u % 3 == 0 || reserved(k, u, v, W, doorU)) continue;
                        int h = 1 + Shapes.hash(u, k, v, seed) % 3;
                        for (int i = 0; i < h && i < fh - 2; i++) {
                            f.set(p, b, u, ly + i, v, (i == 0 && Shapes.hash(u, 9, v, seed) % 5 == 0) ? Deco.chest(f.front) : Deco.BARREL);
                        }
                    }
                }
            }
            case COUNTER -> {
                int v = D - 4;
                BlockState top = Deco.topSlab(Blocks.BIRCH_SLAB);
                for (int u = 3; u <= W - 4; u++) {
                    if (reserved(k, u, v, W, doorU)) continue;
                    f.set(p, b, u, ly, v, (u % 3 == 0) ? Deco.BARREL : top);
                    if (u % 3 == 1) f.set(p, b, u, ly + 1, v, Deco.pot(Shapes.hash(u, 3, v, seed)));
                }
            }
            case FORGE -> {
                int cu = doorU, cv = D / 2;
                for (int du = -1; du <= 1; du++) f.set(p, b, cu + du, ly - 1, cv + 1, Blocks.MAGMA_BLOCK.defaultBlockState());
                f.set(p, b, cu, ly, cv + 2, Blocks.BLAST_FURNACE.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, f.front));
                f.set(p, b, cu - 2, ly, cv, Blocks.ANVIL.defaultBlockState());
                f.set(p, b, cu + 2, ly, cv, Blocks.ANVIL.defaultBlockState());
                f.set(p, b, cu, ly, cv - 1, Deco.WATER_CAULDRON);
            }
            case STALLS -> {
                for (int u = 1; u <= W - 2; u++) {
                    for (int v : new int[]{1, 2, 3, D - 4, D - 3, D - 2}) {
                        if (u % 4 == 0) {
                            f.set(p, b, u, ly, v, Deco.FENCE);
                        } else if (v == D - 2 && u % 4 == 2) {
                            f.set(p, b, u, ly, v, Blocks.HAY_BLOCK.defaultBlockState());
                        } else if (v == D - 2 && u % 4 == 1) {
                            f.set(p, b, u, ly, v, Deco.WATER_CAULDRON);
                        }
                    }
                }
            }
            case DINING_LONG -> {
                int v = D / 2;
                for (int u = 4; u <= W - 5; u++) {
                    Royal.table(p, f.x(u, v), b + ly, f.z(u, v));
                    f.set(p, b, u, ly, v - 1, Royal.chair(f.front));
                    f.set(p, b, u, ly, v + 1, Royal.chair(f.back));
                    if (u % 3 == 0) f.set(p, b, u, ly + 2, v, Deco.candles(3));
                }
            }
            case NONE -> { }
        }

        // 샹들리에
        int chain = Math.max(0, fh - 5);
        for (int u = 3; u <= W - 4; u += 6) {
            int v = D / 2;
            if (reserved(k, u, v, W, doorU)) continue;
            if (role == Role.MANSION) Royal.chandelier(p, f.x(u, v), b + ceiling, f.z(u, v), 0);
            else Deco.chandelier(p, f.x(u, v), b + ceiling, f.z(u, v), chain);
        }
    }

    private BlockState resolve(BlockState s, Direction in, int u, int v, int k) {
        if (s == POT) return Deco.pot(Shapes.hash(u, k, v, seed));
        if (s == CANDLE) return Deco.candles(2 + Shapes.hash(u, k + 7, v, seed) % 3);
        if (s.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) return s.setValue(BlockStateProperties.HORIZONTAL_FACING, in);
        return s;
    }

    /** 귀족 저택 앞뜰: 산울타리, 화단, 분수, 정문 길 */
    private void frontGarden(Placer p, int b) {
        int doorU = f.width / 2;
        for (int u = -2; u <= f.width + 1; u++) {
            for (int v = -12; v <= -1; v++) {
                boolean path = Math.abs(u - doorU) <= 1;
                boolean hedge = (v == -12 || u == -2 || u == f.width + 1) && !path;
                f.set(p, b, u, 0, v, path ? SMOOTH_QUARTZ : GRASS);
                if (hedge) f.set(p, b, u, 1, v, Royal.HEDGE);
                else if (!path && Shapes.hash(u, 4, v, seed) % 3 == 0) f.set(p, b, u, 1, v, Royal.ROYAL_FLOWERS[Shapes.hash(u, 5, v, seed) % 4]);
            }
        }
        Shapes.fountain(p, f.x(doorU, -7), f.z(doorU, -7), b);
        Royal.lamp(p, f.x(doorU - 3, -2), f.z(doorU - 3, -2), b);
        Royal.lamp(p, f.x(doorU + 3, -2), f.z(doorU + 3, -2), b);
    }

    @Override
    public void spawn(ServerLevel level, CityLayout city) {
        if (role == Role.STABLE) {
            for (int i = 0; i < 2; i++) {
                Horse h = EntityType.HORSE.create(level);
                if (h == null) continue;
                int u = 2 + i * 8, v = f.depth - 3;
                BlockPos pos = new BlockPos(f.x(u, v), groundY + 1, f.z(u, v));
                h.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
                EventHooks.finalizeMobSpawn(h, level, level.getCurrentDifficultyAt(pos), MobSpawnType.STRUCTURE, null);
                h.setPersistenceRequired();
                level.addFreshEntity(h);
            }
        }
        if (villager) {
            Shapes.spawnVillager(level, f.x(f.width / 2, 3) + 0.5, groundY + 1, f.z(f.width / 2, 3) + 0.5);
        }
    }

    @Override
    public boolean needsSpawn() {
        return villager || role == Role.STABLE;
    }
}
