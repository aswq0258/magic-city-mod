package io.github.aswq0258.magiccity.city.feature;

import io.github.aswq0258.magiccity.build.Placer;
import io.github.aswq0258.magiccity.city.CityLayout;
import io.github.aswq0258.magiccity.city.Deco;
import io.github.aswq0258.magiccity.city.Fantasy;
import io.github.aswq0258.magiccity.city.Palette;
import io.github.aswq0258.magiccity.city.Royal;
import io.github.aswq0258.magiccity.city.Shapes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootTable;

import static io.github.aswq0258.magiccity.city.Palette.*;

/**
 * 왕성 하수도 지하 던전 (도시 지면 16칸 아래)
 *
 *  슬럼가 맨홀 ─ 서쪽 하수로 ─┐
 *                    ┌── 왕성 아래 순환 하수로 ──┐── 동쪽 지류 ─ 지하 저수조(물웅덩이·좀비)
 *     왕성 비밀 사다리 ┤        │                  │
 *                    │   남쪽 지류 → 초대 왕의 납골당(주탑 바로 아래: 보물 상자·스켈레톤)
 *                    └── 북쪽 지류 ─ 지하 감옥(철창 감방·거미)
 *
 *  - 통로: 폭 7, 가운데 하수 물길, 양옆 보행로, 이끼·금 간 석재 벽돌, 영혼 랜턴, 거미줄, 뼈
 *  - 몬스터 스포너와 보물 상자(던전 전리품) 배치
 */
public class Sewer extends Feature {
    public static final int DEPTH = 16;
    private final CityLayout c;
    private final long seed;
    private final int slumU, slumV, castleU, castleV;

    /** 하수로 구간 {u0, v0, u1, v1} (가로 또는 세로 직선, 중심선) */
    private final int[][] SEGS;
    private static final int[] CRYPT = {244, 242, 277, 280};
    private static final int[] CISTERN = {373, 244, 404, 276};
    private static final int[] PRISON = {212, 289, 250, 309};

    private final int castleTop;

    public Sewer(CityLayout city, int slumU, int slumV, int castleU, int castleV, int castleTop, long seed) {
        super(Math.min(city.wx(Math.min(90, slumU - 6)), city.wx(410)), Math.min(city.wz(Math.min(190, slumV - 6)), city.wz(400)),
                Math.max(city.wx(Math.min(90, slumU - 6)), city.wx(410)), Math.max(city.wz(Math.min(190, slumV - 6)), city.wz(400)));
        this.c = city;
        this.slumU = slumU;
        this.slumV = slumV;
        this.castleU = castleU;
        this.castleV = castleV;
        this.seed = seed;
        this.castleTop = castleTop;
        this.SEGS = new int[][]{
                {slumU - 2, slumV - 2, slumU - 2, 205}, {slumU - 2, 205, 200, 205},                     // 슬럼 맨홀 → 서쪽
                {200, 205, 200, 315}, {200, 315, 320, 315}, {320, 315, 320, 205}, {200, 205, 320, 205},   // 왕성 아래 순환
                {260, 205, 260, 242},                                                         // 납골당
                {320, 260, 373, 260},                                                         // 저수조
                {231, 309, 231, 315},                                                         // 감옥 (왕성 북서쪽 아래)
        };
    }

    @Override
    public boolean occupies(int x, int z) {
        return false;   // 땅속이므로 지상 가로등·소품을 막지 않음
    }

    private int F() {
        return groundY - DEPTH;
    }

    private BlockState brick(int x, int y, int z) {
        int h = Shapes.hash(x, y, z, seed) % 10;
        return h < 3 ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState() : h < 5 ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState()
                : Blocks.STONE_BRICKS.defaultBlockState();
    }

    private void put(Placer p, int u, int y, int v, BlockState s) {
        p.set(c.wx(u), y, c.wz(v), s);
    }

    private boolean hits(Placer p, int u0, int v0, int u1, int v1) {
        int xa = c.wx(u0), xb = c.wx(u1), za = c.wz(v0), zb = c.wz(v1);
        return p.intersects(Math.min(xa, xb), Math.min(za, zb), Math.max(xa, xb), Math.max(za, zb));
    }

    @Override
    public void build(Placer p, CityLayout city) {
        final int F = F();
        for (int[] s : SEGS) tunnel(p, s, F);
        crypt(p, F);
        cistern(p, F);
        prison(p, F);
        // 슬럼가 맨홀 사다리 (지면 → 통로)
        if (hits(p, slumU - 1, slumV - 1, slumU + 1, slumV + 1)) {
            for (int y = F + 1; y <= groundY; y++) {
                if (y < groundY) put(p, slumU + 1, y, slumV, brick(slumU + 1, y, slumV));
                put(p, slumU, y, slumV, Palette.ladder(Direction.WEST));
            }
        }
        // 왕성 비밀 사다리 (1단 테라스 → 통로) + 옆 통로
        if (hits(p, 200, castleV - 2, castleU + 1, castleV + 2)) {
            int top = groundY + castleTop;
            for (int y = F + 1; y <= top; y++) {
                put(p, castleU, y, castleV + 1, brick(castleU, y, castleV + 1));
                put(p, castleU, y, castleV, Palette.ladder(Direction.SOUTH));
            }
            put(p, castleU, top + 1, castleV, Blocks.SPRUCE_TRAPDOOR.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.TrapDoorBlock.OPEN, true).setValue(net.minecraft.world.level.block.TrapDoorBlock.FACING, Direction.SOUTH));
            for (int u = 203; u < castleU; u++) {          // 왕성 아래 순환 하수로(서쪽)까지 비밀 통로
                for (int dv = -1; dv <= 1; dv++) {
                    put(p, u, F, castleV + dv, brick(u, F, castleV + dv));
                    for (int ly = 1; ly <= 3; ly++) put(p, u, F + ly, castleV + dv, AIR);
                    put(p, u, F + 4, castleV + dv, brick(u, F + 4, castleV + dv));
                }
            }
            put(p, castleU - 1, F + 3, castleV - 1, Blocks.SOUL_WALL_TORCH.defaultBlockState());
        }
    }

    /** 폭 7 통로 한 구간 */
    private void tunnel(Placer p, int[] s, int F) {
        boolean alongU = s[1] == s[3];
        int a0 = alongU ? Math.min(s[0], s[2]) - 3 : Math.min(s[1], s[3]) - 3;
        int a1 = alongU ? Math.max(s[0], s[2]) + 3 : Math.max(s[1], s[3]) + 3;
        int center = alongU ? s[1] : s[0];
        int u0 = alongU ? a0 : center - 4, u1 = alongU ? a1 : center + 4;
        int v0 = alongU ? center - 4 : a0, v1 = alongU ? center + 4 : a1;
        if (!hits(p, u0, v0, u1, v1)) return;
        for (int u = u0; u <= u1; u++) {
            for (int v = v0; v <= v1; v++) {
                int lat = alongU ? v - center : u - center, along = alongU ? u : v;
                int al = Math.abs(lat);
                int x = c.wx(u), z = c.wz(v);
                if (!p.contains(x, z)) continue;
                boolean end = along == a0 || along == a1;
                if (al == 4 || (end && !openEnd(u, v))) {                 // 바깥 껍질
                    for (int y = F - 1; y <= F + 7; y++) if (!isOpen(u, v, y, F)) p.set(x, y, z, brick(x, y, z));
                    continue;
                }
                p.set(x, F - 1, z, brick(x, F - 1, z));
                if (al == 3) {
                    for (int y = F; y <= F + 6; y++) if (!isOpen(u, v, y, F)) p.set(x, y, z, brick(x, y, z));
                    continue;
                }
                p.set(x, F, z, lat == 0 ? WATER : brick(x, F, z));
                int top = al == 2 ? 4 : 5;
                for (int ly = 1; ly <= top; ly++) p.set(x, F + ly, z, AIR);
                for (int ly = top + 1; ly <= 6; ly++) p.set(x, F + ly, z, brick(x, F + ly, z));
                if (al == 2 && top == 4) p.set(x, F + 5, z, Blocks.STONE_BRICK_STAIRS.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.StairBlock.FACING, (alongU ? Deco.toward(0, -lat) : Deco.toward(lat, 0)))
                        .setValue(net.minecraft.world.level.block.StairBlock.HALF, net.minecraft.world.level.block.state.properties.Half.TOP));
                int h = Shapes.hash(x, F, z, seed);
                if (lat == 0 && Math.floorMod(along, 11) == 5) {
                    p.set(x, F + 5, z, Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
                } else if (al == 2 && h % 29 == 0) {
                    p.set(x, F + 4, z, Blocks.COBWEB.defaultBlockState());
                } else if (al == 2 && h % 31 == 0) {
                    p.set(x, F + 1, z, Blocks.BONE_BLOCK.defaultBlockState());
                } else if (al == 1 && h % 47 == 0) {
                    p.set(x, F + 1, z, Blocks.SKELETON_SKULL.defaultBlockState());
                } else if (al == 2 && h % 53 == 0) {
                    p.set(x, F + 1, z, Deco.BARREL);
                }
                // 쥐굴 스포너 (구간 가운데쯤 하나)
                if (lat == 2 && along == (a0 + a1) / 2 && (a1 - a0) > 40) spawner(p, x, F + 1, z, EntityType.ZOMBIE);
            }
        }
    }

    /** 통로가 다른 통로·방과 이어지는 칸이면 벽을 세우지 않음 */
    private boolean isOpen(int u, int v, int y, int F) {
        if (y <= F - 1 || y >= F + 6) return false;
        for (int[] s : SEGS) {
            boolean alongU = s[1] == s[3];
            int lo = Math.min(alongU ? s[0] : s[1], alongU ? s[2] : s[3]) - 2;
            int hi = Math.max(alongU ? s[0] : s[1], alongU ? s[2] : s[3]) + 2;
            int along = alongU ? u : v, lat = alongU ? v - s[1] : u - s[0];
            if (along >= lo && along <= hi && Math.abs(lat) <= 2) return true;
        }
        return inRoom(u, v, CRYPT, 0) || inRoom(u, v, CISTERN, 0) || inRoom(u, v, PRISON, 0);
    }

    private boolean openEnd(int u, int v) {
        return inRoom(u, v, CRYPT, 1) || inRoom(u, v, CISTERN, 1) || inRoom(u, v, PRISON, 1);
    }

    private static boolean inRoom(int u, int v, int[] r, int pad) {
        return u >= r[0] - pad && u <= r[2] + pad && v >= r[1] - pad && v <= r[3] + pad;
    }

    /** 방 껍질 + 바닥 + 천장 (h = 바닥 위 높이) */
    private void room(Placer p, int[] r, int floor, int h) {
        for (int u = r[0] - 1; u <= r[2] + 1; u++) {
            for (int v = r[1] - 1; v <= r[3] + 1; v++) {
                int x = c.wx(u), z = c.wz(v);
                if (!p.contains(x, z)) continue;
                boolean wall = u == r[0] - 1 || u == r[2] + 1 || v == r[1] - 1 || v == r[3] + 1;
                p.set(x, floor - 1, z, brick(x, floor - 1, z));
                for (int y = floor; y <= floor + h + 1; y++) {
                    if (wall) {
                        if (!(y > F() && y < F() + 6 && isOpenCorridor(u, v))) p.set(x, y, z, brick(x, y, z));
                    } else {
                        p.set(x, y, z, y == floor ? brick(x, y, z) : y == floor + h + 1 ? brick(x, y, z) : AIR);
                    }
                }
            }
        }
    }

    private boolean isOpenCorridor(int u, int v) {
        for (int[] s : SEGS) {
            boolean alongU = s[1] == s[3];
            int lo = Math.min(alongU ? s[0] : s[1], alongU ? s[2] : s[3]);
            int hi = Math.max(alongU ? s[0] : s[1], alongU ? s[2] : s[3]);
            int along = alongU ? u : v, lat = alongU ? v - s[1] : u - s[0];
            if (along >= lo - 3 && along <= hi + 3 && Math.abs(lat) <= 2) return true;
        }
        return false;
    }

    /** 초대 왕의 납골당 (주탑 바로 아래) */
    private void crypt(Placer p, int F) {
        int[] r = CRYPT;
        if (!hits(p, r[0] - 1, r[1] - 1, r[2] + 1, r[3] + 1)) return;
        int floor = F - 2;
        room(p, r, floor, 12);
        int cu = (r[0] + r[2]) / 2, cv = (r[1] + r[3]) / 2;
        // 입구 계단 (통로 바닥 F → 방 바닥 F-2)
        for (int u = cu - 2; u <= cu + 2; u++) {
            put(p, u, F - 1, r[1], Blocks.STONE_BRICK_STAIRS.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.StairBlock.FACING, Direction.SOUTH));
        }
        for (int u = r[0]; u <= r[2]; u++) {
            for (int v = r[1]; v <= r[3]; v++) {
                int x = c.wx(u), z = c.wz(v);
                boolean pillar = Math.floorMod(u - r[0], 7) == 3 && Math.floorMod(v - r[1], 7) == 3 && Math.abs(u - cu) > 4;
                if (pillar) {
                    for (int y = floor + 1; y <= floor + 12; y++) p.set(x, y, z, y == floor + 1 || y == floor + 12 ? CHISELED_QUARTZ : Blocks.POLISHED_DEEPSLATE.defaultBlockState());
                    p.set(x, floor + 8, z, Blocks.SOUL_LANTERN.defaultBlockState());
                }
                if (Math.abs(u - cu) <= 1 && v > r[1] + 1 && v < cv + 4) p.set(x, floor + 1, z, Royal.RED_CARPET);
                if (Math.abs(u - cu) == 5 && Math.floorMod(v, 5) == 0 && v < cv) {   // 석관
                    p.set(x, floor + 1, z, Blocks.POLISHED_DEEPSLATE.defaultBlockState());
                    p.set(x, floor + 2, z, Blocks.DEEPSLATE_TILE_SLAB.defaultBlockState());
                }
            }
        }
        // 제단 + 보물 + 스켈레톤
        for (int du = -3; du <= 3; du++) {
            for (int dv = 4; dv <= 8; dv++) {
                put(p, cu + du, floor + 1, cv + dv, Math.abs(du) == 3 || dv == 4 ? Blocks.POLISHED_DEEPSLATE.defaultBlockState() : Royal.GOLD);
            }
        }
        chest(p, c.wx(cu - 1), floor + 2, c.wz(cv + 6), Direction.SOUTH, BuiltInLootTables.ANCIENT_CITY);
        chest(p, c.wx(cu + 1), floor + 2, c.wz(cv + 6), Direction.SOUTH, BuiltInLootTables.SIMPLE_DUNGEON);
        put(p, cu, floor + 2, cv + 7, Blocks.SKELETON_SKULL.defaultBlockState());
        Shapes.crystal(p, c.wx(cu), floor + 9, c.wz(cv + 6), 2);
        spawner(p, c.wx(cu - 8), floor + 1, c.wz(cv), EntityType.SKELETON);
        spawner(p, c.wx(cu + 8), floor + 1, c.wz(cv), EntityType.SKELETON);
    }

    /** 지하 저수조: 기둥 사이 깊은 물, 둘레 보행로 */
    private void cistern(Placer p, int F) {
        int[] r = CISTERN;
        if (!hits(p, r[0] - 1, r[1] - 1, r[2] + 1, r[3] + 1)) return;
        room(p, r, F, 9);
        for (int u = r[0]; u <= r[2]; u++) {
            for (int v = r[1]; v <= r[3]; v++) {
                int x = c.wx(u), z = c.wz(v);
                boolean rim = u <= r[0] + 1 || u >= r[2] - 1 || v <= r[1] + 1 || v >= r[3] - 1;
                if (!rim) {
                    p.set(x, F, z, WATER);
                    p.set(x, F - 1, z, WATER);
                    p.set(x, F - 2, z, brick(x, F - 2, z));
                }
                if (Math.floorMod(u - r[0], 6) == 3 && Math.floorMod(v - r[1], 6) == 3) {
                    for (int y = F - 1; y <= F + 9; y++) p.set(x, y, z, brick(x, y, z));
                    p.set(x, F + 6, z, Blocks.SOUL_LANTERN.defaultBlockState());
                }
            }
        }
        spawner(p, c.wx(r[2] - 1), F + 1, c.wz(r[3] - 1), EntityType.ZOMBIE);
        chest(p, c.wx(r[2] - 1), F + 1, c.wz(r[1] + 1), Direction.WEST, BuiltInLootTables.SIMPLE_DUNGEON);
        p.set(c.wx((r[0] + r[2]) / 2), F + 7, c.wz((r[1] + r[3]) / 2), Fantasy.CLUSTER_DOWN);
    }

    /** 지하 감옥: 가운데 복도, 양옆 철창 감방 */
    private void prison(Placer p, int F) {
        int[] r = PRISON;
        if (!hits(p, r[0] - 1, r[1] - 1, r[2] + 1, r[3] + 1)) return;
        room(p, r, F, 5);
        int midV = (r[1] + r[3]) / 2;
        for (int u = r[0]; u <= r[2]; u++) {
            for (int v = r[1]; v <= r[3]; v++) {
                int x = c.wx(u), z = c.wz(v);
                int dv = v - midV;
                if (Math.abs(u - 230) <= 2 && dv < 0) continue;          // 입구 통로는 비워 둠
                boolean front = Math.abs(dv) == 3;
                boolean divider = Math.abs(dv) > 3 && Math.floorMod(u - r[0], 6) == 0;
                for (int ly = 1; ly <= 5; ly++) {
                    if (divider) p.set(x, F + ly, z, brick(x, F + ly, z));
                    else if (front && ly <= 4) {
                        boolean door = Math.floorMod(u - r[0], 6) == 3 && ly <= 2;
                        p.set(x, F + ly, z, door ? AIR : Blocks.IRON_BARS.defaultBlockState());
                    }
                }
                int cell = Math.floorMod(u - r[0], 6);
                if (Math.abs(dv) == 6 && cell == 2) p.set(x, F + 4, z, Blocks.CHAIN.defaultBlockState());
                if (Math.abs(dv) == 6 && cell == 4 && Shapes.hash(x, 3, z, seed) % 2 == 0) p.set(x, F + 1, z, Blocks.BONE_BLOCK.defaultBlockState());
                if (Math.abs(dv) == 5 && cell == 1) p.set(x, F + 1, z, Blocks.HAY_BLOCK.defaultBlockState());
                if (dv == 0 && Math.floorMod(u, 8) == 0) p.set(x, F + 5, z, Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
            }
        }
        spawner(p, c.wx(r[0] + 15), F + 1, c.wz(midV + 6), EntityType.SPIDER);
        spawner(p, c.wx(r[2] - 9), F + 1, c.wz(midV - 6), EntityType.ZOMBIE);
        chest(p, c.wx(r[2] - 1), F + 1, c.wz(midV), Direction.WEST, BuiltInLootTables.SIMPLE_DUNGEON);
    }

    private void spawner(Placer p, int x, int y, int z, EntityType<?> type) {
        if (p.pass() != Placer.Pass.SOLID || !p.contains(x, z)) return;
        p.set(x, y, z, Blocks.SPAWNER.defaultBlockState());
        BlockEntity be = p.level().getBlockEntity(new BlockPos(x, y, z));
        if (be instanceof SpawnerBlockEntity s) {
            s.setEntityId(type, p.level().getRandom());
            s.setChanged();
        }
    }

    private void chest(Placer p, int x, int y, int z, Direction facing, ResourceKey<LootTable> loot) {
        if (p.pass() != Placer.Pass.SOLID || !p.contains(x, z)) return;
        p.set(x, y, z, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing));
        BlockEntity be = p.level().getBlockEntity(new BlockPos(x, y, z));
        if (be instanceof RandomizableContainerBlockEntity rc) rc.setLootTable(loot, seed ^ ((long) x * 31 + z));
    }
}
