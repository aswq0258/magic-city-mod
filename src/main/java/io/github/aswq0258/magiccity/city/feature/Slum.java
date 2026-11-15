package io.github.aswq0258.magiccity.city.feature;

import io.github.aswq0258.magiccity.build.Placer;
import io.github.aswq0258.magiccity.city.CityLayout;
import io.github.aswq0258.magiccity.city.Deco;
import io.github.aswq0258.magiccity.city.Shapes;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

import java.util.ArrayList;
import java.util.List;

import static io.github.aswq0258.magiccity.city.Palette.*;

/**
 * 슬럼가: 흰 도시와 대비되는 낡은 판잣집 동네.
 *  - 진흙·자갈·거친 흙 골목, 크기와 재료가 제각각인 판잣집(가문비 판자·조약돌·이끼 낀 돌·진흙 벽돌)
 *  - 기울어진 판자 지붕에 천 조각 덧댐, 빨랫줄, 모닥불, 퇴비통, 통·건초
 *  - 하수도 입구: 무너진 돌 테두리 + 열린 뚜껑 + 아래로 내려가는 사다리 (하수도 던전과 연결)
 */
public class Slum extends Feature {
    private record Hut(int x0, int z0, int x1, int z1, int h, int mat, Direction door, int roof) {}

    private static final BlockState[] WALLS = {
            Blocks.SPRUCE_PLANKS.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState(),
            Blocks.MOSSY_COBBLESTONE.defaultBlockState(), Blocks.MUD_BRICKS.defaultBlockState(),
            Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState(), Blocks.OAK_PLANKS.defaultBlockState(),
            Blocks.PACKED_MUD.defaultBlockState(),
    };
    private static final BlockState[] ROOFS = {
            Blocks.SPRUCE_SLAB.defaultBlockState(), Blocks.DARK_OAK_SLAB.defaultBlockState(),
            Blocks.OAK_SLAB.defaultBlockState(), Blocks.MUD_BRICK_SLAB.defaultBlockState(),
    };
    private static final BlockState[] PATCH = {
            Blocks.BROWN_WOOL.defaultBlockState(), Blocks.GRAY_WOOL.defaultBlockState(),
            Blocks.LIGHT_GRAY_WOOL.defaultBlockState(), Blocks.GREEN_WOOL.defaultBlockState(),
    };
    private static final BlockState[] GROUND = {
            Blocks.MUD.defaultBlockState(), Blocks.COARSE_DIRT.defaultBlockState(), Blocks.GRAVEL.defaultBlockState(),
            Blocks.DIRT_PATH.defaultBlockState(), Blocks.PACKED_MUD.defaultBlockState(),
    };

    private final List<Hut> huts = new ArrayList<>();
    private final int shaftX, shaftZ;
    private final long seed;

    /** (shaftX, shaftZ) = 하수도 입구 (사다리 칸). 그 둘레 5x5 는 비워 둠 */
    public Slum(int x0, int z0, int x1, int z1, int shaftX, int shaftZ, long seed) {
        super(Math.min(x0, x1), Math.min(z0, z1), Math.max(x0, x1), Math.max(z0, z1));
        this.shaftX = shaftX;
        this.shaftZ = shaftZ;
        this.seed = seed;
        RandomSource r = RandomSource.create(seed);
        // 줄 단위로 판잣집을 채움: 줄마다 깊이가 다르고, 집 사이에 1~2칸 골목
        int z = minZ + 2;
        boolean faceNorth = true;
        while (z + 5 <= maxZ - 1) {
            int depth = 5 + r.nextInt(3);
            if (z + depth - 1 > maxZ - 1) break;
            int x = minX + 1 + r.nextInt(2);
            while (x + 4 <= maxX - 1) {
                int w = 4 + r.nextInt(4);
                int x1h = Math.min(x + w - 1, maxX - 1);
                if (x1h - x >= 3) {
                    boolean nearShaft = x <= shaftX + 3 && x1h >= shaftX - 3 && z <= shaftZ + 3 && z + depth - 1 >= shaftZ - 3;
                    if (!nearShaft && r.nextInt(9) != 0) {
                        int dz = r.nextInt(2);
                        huts.add(new Hut(x, z + dz, x1h, z + depth - 1, 3 + r.nextInt(2), r.nextInt(WALLS.length),
                                faceNorth ? Direction.NORTH : Direction.SOUTH, r.nextInt(ROOFS.length)));
                    }
                }
                x = x1h + 2 + r.nextInt(2);
            }
            z += depth + (faceNorth ? 1 : 3);   // 두 줄씩 등을 맞대고, 그 사이에 넓은 골목
            faceNorth = !faceNorth;
        }
    }

    @Override
    public void build(Placer p, CityLayout city) {
        final int b = groundY;
        // 바닥: 진흙 골목
        p.forColumns(minX, minZ, maxX, maxZ, (x, z) -> {
            int h = Shapes.hash(x, 5, z, seed);
            p.set(x, b, z, GROUND[h % GROUND.length]);
            if (h % 41 == 0) p.set(x, b + 1, z, Blocks.COMPOSTER.defaultBlockState());
            else if (h % 37 == 0) p.set(x, b + 1, z, Deco.BARREL);
            else if (h % 53 == 0) p.set(x, b + 1, z, Blocks.HAY_BLOCK.defaultBlockState());
            else if (h % 97 == 0) p.set(x, b + 1, z, Blocks.COBWEB.defaultBlockState());
        });
        for (Hut hut : huts) hut(p, b, hut);
        // 빨랫줄 (집 지붕 높이에 울타리 + 천)
        for (int i = 0; i + 1 < huts.size(); i++) {
            Hut a = huts.get(i), c = huts.get(i + 1);
            if (a.z0() != c.z0() || Shapes.hash(a.x0(), 9, a.z0(), seed) % 2 != 0) continue;
            int y = b + Math.min(a.h(), c.h());
            int z = (a.z0() + a.z1()) / 2;
            for (int x = a.x1() + 1; x < c.x0(); x++) {
                p.set(x, y, z, Blocks.SPRUCE_FENCE.defaultBlockState());
                if ((x & 1) == 0) p.set(x, y - 1, z, PATCH[Shapes.hash(x, 3, z, seed) % PATCH.length]);
            }
        }
        // 골목 모닥불·가로등 대신 랜턴 기둥
        p.forColumns(minX + 3, minZ + 3, maxX - 3, maxZ - 3, (x, z) -> {
            if (Math.floorMod(x * 7 + z * 13, 61) != 0 || inHut(x, z, 1) || near(x, z, shaftX, shaftZ, 3)) return;
            if (Shapes.hash(x, 8, z, seed) % 2 == 0) {
                p.set(x, b + 1, z, Deco.CAMPFIRE);
            } else {
                p.set(x, b + 1, z, Blocks.SPRUCE_FENCE.defaultBlockState());
                p.set(x, b + 2, z, Blocks.SPRUCE_FENCE.defaultBlockState());
                p.set(x, b + 3, z, Blocks.LANTERN.defaultBlockState());
            }
        });
        shaft(p, b);
    }

    private boolean inHut(int x, int z, int pad) {
        for (Hut h : huts) if (x >= h.x0() - pad && x <= h.x1() + pad && z >= h.z0() - pad && z <= h.z1() + pad) return true;
        return false;
    }

    private static boolean near(int x, int z, int cx, int cz, int r) {
        return Math.abs(x - cx) <= r && Math.abs(z - cz) <= r;
    }

    private void hut(Placer p, int b, Hut h) {
        if (!p.intersects(h.x0() - 1, h.z0() - 1, h.x1() + 1, h.z1() + 1)) return;
        BlockState wall = WALLS[h.mat()];
        BlockState roof = ROOFS[h.roof()];
        int doorX = (h.x0() + h.x1()) / 2 + (Shapes.hash(h.x0(), 2, h.z0(), seed) % 2);
        int doorZ = h.door() == Direction.NORTH ? h.z0() : h.z1();
        int winZ = h.door() == Direction.NORTH ? h.z1() : h.z0();
        for (int x = h.x0(); x <= h.x1(); x++) {
            for (int z = h.z0(); z <= h.z1(); z++) {
                boolean edge = x == h.x0() || x == h.x1() || z == h.z0() || z == h.z1();
                int hh = Shapes.hash(x, 11, z, seed);
                p.set(x, b, z, edge ? wall : Blocks.SPRUCE_PLANKS.defaultBlockState());
                for (int ly = 1; ly <= h.h(); ly++) {
                    BlockState s = AIR;
                    if (edge) {
                        s = (hh >> ly) % 6 == 0 ? Blocks.COBBLESTONE.defaultBlockState() : wall;     // 덧댄 자국
                        if (x == doorX && z == doorZ && ly <= 2) s = AIR;
                        if (z == winZ && x == (h.x0() + h.x1()) / 2 && ly == 2) s = Blocks.IRON_BARS.defaultBlockState();
                    }
                    p.set(x, b + ly, z, s);
                }
                // 한쪽으로 기운 지붕: 뒤쪽이 1칸 낮음
                boolean low = h.door() == Direction.NORTH ? z > (h.z0() + h.z1()) / 2 : z < (h.z0() + h.z1()) / 2;
                int ry = b + h.h() + (low ? 0 : 1);
                BlockState rs = (hh % 9 == 0) ? PATCH[hh % PATCH.length] : roof.setValue(SlabBlock.TYPE, low ? SlabType.TOP : SlabType.BOTTOM);
                p.set(x, low ? ry : ry - 1, z, rs);
            }
        }
        // 처마 끝 1칸 밖
        for (int x = h.x0() - 1; x <= h.x1() + 1; x++) {
            int z = h.door() == Direction.NORTH ? h.z0() - 1 : h.z1() + 1;
            p.set(x, b + h.h(), z, roof);
        }
        // 살림: 건초 침대, 통, 작업대·가마솥, 촛불
        int ix = h.x0() + 1, iz = h.door() == Direction.NORTH ? h.z1() - 1 : h.z0() + 1;
        p.set(ix, b + 1, iz, Blocks.HAY_BLOCK.defaultBlockState());
        p.set(ix, b + 2, iz, Blocks.BROWN_CARPET.defaultBlockState());
        p.set(h.x1() - 1, b + 1, iz, Deco.BARREL);
        int k = Shapes.hash(h.x1(), 4, h.z1(), seed) % 3;
        if (h.x1() - h.x0() >= 4) {
            p.set(h.x1() - 2, b + 1, iz, k == 0 ? Deco.CRAFTING : k == 1 ? Blocks.CAULDRON.defaultBlockState() : Blocks.LOOM.defaultBlockState());
        }
        p.set(h.x1() - 1, b + 2, iz, Deco.candles(1 + k));
    }

    /** 하수도 입구: 무너진 조약돌 테두리, 열린 철 뚜껑, 사다리 */
    private void shaft(Placer p, int b) {
        if (shaftX == Integer.MIN_VALUE / 2) return;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                int m = Math.max(Math.abs(dx), Math.abs(dz));
                int x = shaftX + dx, z = shaftZ + dz;
                if (m == 2) {
                    p.set(x, b, z, Blocks.COBBLESTONE.defaultBlockState());
                    if (Shapes.hash(x, 6, z, seed) % 3 != 0) p.set(x, b + 1, z, Blocks.MOSSY_COBBLESTONE_WALL.defaultBlockState());
                } else {
                    p.set(x, b, z, Blocks.STONE_BRICKS.defaultBlockState());
                    p.set(x, b + 1, z, AIR);
                }
            }
        }
        p.set(shaftX, b, shaftZ, AIR);
        p.set(shaftX, b + 1, shaftZ, Blocks.IRON_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.OPEN, true)
                .setValue(TrapDoorBlock.FACING, Direction.SOUTH));
        p.set(shaftX - 1, b + 1, shaftZ - 1, Blocks.LANTERN.defaultBlockState());
        // 아래로 내려가는 사다리와 통로는 Sewer 가 이어 줌
    }

    @Override
    public boolean occupies(int x, int z) {
        return x >= minX - 1 && x <= maxX + 1 && z >= minZ - 1 && z <= maxZ + 1;
    }
}
