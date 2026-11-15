package io.github.aswq0258.magiccity.city;

import io.github.aswq0258.magiccity.build.Placer;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * 왕성 전용 팔레트와 가구.
 * 외관: 흰 석영 + 금 포인트 / 실내: 대리석(석영·방해석) 바닥, 붉은 카펫과 금색 테두리, 짙은 참나무 가구, 금 샹들리에
 */
public final class Royal {
    private Royal() {}

    public static final BlockState GOLD = Blocks.GOLD_BLOCK.defaultBlockState();
    public static final BlockState RED_CARPET = Blocks.RED_CARPET.defaultBlockState();
    public static final BlockState GOLD_CARPET = Blocks.YELLOW_CARPET.defaultBlockState();
    public static final BlockState RED_WOOL = Blocks.RED_WOOL.defaultBlockState();
    public static final BlockState CLEAR_PANE = Blocks.GLASS_PANE.defaultBlockState();
    public static final BlockState CLEAR_GLASS = Blocks.GLASS.defaultBlockState();
    public static final BlockState GOLD_PANE = Blocks.YELLOW_STAINED_GLASS_PANE.defaultBlockState();
    public static final BlockState WHITE_PANE = Blocks.WHITE_STAINED_GLASS_PANE.defaultBlockState();
    public static final BlockState DARK_PLANKS = Blocks.DARK_OAK_PLANKS.defaultBlockState();
    public static final BlockState DARK_FENCE = Blocks.DARK_OAK_FENCE.defaultBlockState();
    public static final BlockState DARK_PLATE = Blocks.DARK_OAK_PRESSURE_PLATE.defaultBlockState();
    public static final BlockState HEDGE = Blocks.AZALEA_LEAVES.defaultBlockState()
            .setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true);
    public static final BlockState HEDGE_FLOWER = Blocks.FLOWERING_AZALEA_LEAVES.defaultBlockState()
            .setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true);
    public static final BlockState[] ROYAL_FLOWERS = {
            Blocks.RED_TULIP.defaultBlockState(),
            Blocks.WHITE_TULIP.defaultBlockState(),
            Blocks.POPPY.defaultBlockState(),
            Blocks.LILY_OF_THE_VALLEY.defaultBlockState(),
    };
    public static final BlockState[] TREASURES = {
            Blocks.GOLD_BLOCK.defaultBlockState(),
            Blocks.DIAMOND_BLOCK.defaultBlockState(),
            Blocks.EMERALD_BLOCK.defaultBlockState(),
            Blocks.AMETHYST_CLUSTER.defaultBlockState(),
            Blocks.LAPIS_BLOCK.defaultBlockState(),
    };

    /** 짙은 참나무 의자. backrest: 등받이 쪽 방향 */
    public static BlockState chair(Direction backrest) {
        return Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, backrest);
    }

    public static BlockState whiteStairs(Direction ascend) {
        return Blocks.SMOOTH_QUARTZ_STAIRS.defaultBlockState().setValue(StairBlock.FACING, ascend);
    }

    public static BlockState whiteStairsTop(Direction toward) {
        return Blocks.QUARTZ_STAIRS.defaultBlockState().setValue(StairBlock.FACING, toward).setValue(StairBlock.HALF, Half.TOP);
    }

    public static BlockState redBed(Direction facing, boolean head) {
        return Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, facing)
                .setValue(BedBlock.PART, head ? BedPart.HEAD : BedPart.FOOT);
    }

    public static BlockState redBanner(Direction facing) {
        return Blocks.RED_WALL_BANNER.defaultBlockState().setValue(WallBannerBlock.FACING, facing);
    }

    public static BlockState mintBanner(Direction facing) {
        return Blocks.LIGHT_BLUE_WALL_BANNER.defaultBlockState().setValue(WallBannerBlock.FACING, facing);
    }

    public static void table(Placer p, int x, int y, int z) {
        p.set(x, y, z, DARK_FENCE);
        p.set(x, y + 1, z, DARK_PLATE);
    }

    /** 대리석 바닥 (석영·방해석 체크무늬) */
    public static BlockState marble(int x, int z) {
        return ((x + z) & 1) == 0 ? Palette.SMOOTH_QUARTZ : Palette.CALCITE;
    }

    /**
     * 금 샹들리에: 천장에서 사슬을 내리고, 금 블록 중심에서 사방으로 사슬 팔을 뻗어 랜턴 5개를 매답니다.
     * @param ceilingY 천장 블록의 y
     */
    public static void chandelier(Placer p, int x, int ceilingY, int z, int chain) {
        for (int i = 1; i <= chain; i++) p.set(x, ceilingY - i, z, Deco.CHAIN);
        int hub = ceilingY - chain - 1;
        p.set(x, hub, z, GOLD);
        p.set(x, hub - 1, z, Deco.hangingLantern());
        BlockState armX = Blocks.CHAIN.defaultBlockState().setValue(ChainBlock.AXIS, Direction.Axis.X);
        BlockState armZ = Blocks.CHAIN.defaultBlockState().setValue(ChainBlock.AXIS, Direction.Axis.Z);
        p.set(x + 1, hub, z, armX);
        p.set(x - 1, hub, z, armX);
        p.set(x, hub, z + 1, armZ);
        p.set(x, hub, z - 1, armZ);
        int[][] ends = {{2, 0}, {-2, 0}, {0, 2}, {0, -2}};
        for (int[] e : ends) {
            p.set(x + e[0], hub, z + e[1], GOLD);
            p.set(x + e[0], hub - 1, z + e[1], Deco.hangingLantern());
            if (chain > 0) p.set(x + e[0], hub + 1, z + e[1], Deco.candles(3));
        }
    }

    /** 붉은 카펫 + 금색 테두리 직사각형 */
    public static void rug(Placer p, int x0, int z0, int x1, int z1, int y) {
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
            for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
                boolean border = x == Math.min(x0, x1) || x == Math.max(x0, x1) || z == Math.min(z0, z1) || z == Math.max(z0, z1);
                p.set(x, y, z, border ? GOLD_CARPET : RED_CARPET);
            }
        }
    }

    /** 왕성식 가로등: 석영 기둥 + 바다 랜턴 + 금 장식 */
    public static void lamp(Placer p, int x, int z, int b) {
        for (int y = b + 1; y <= b + 3; y++) p.set(x, y, z, Palette.QUARTZ_PILLAR);
        p.set(x, b + 4, z, Palette.CHISELED_QUARTZ);
        p.set(x, b + 5, z, Palette.LIGHT);
        p.set(x, b + 6, z, GOLD);
    }

    /** 조각상: 받침 + 기둥 + 금 머리 */
    public static void statue(Placer p, int x, int z, int b, int h) {
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) p.set(x + dx, b + 1, z + dz, Palette.CHISELED_QUARTZ);
        for (int y = b + 2; y < b + 2 + h; y++) p.set(x, y, z, Palette.QUARTZ_PILLAR);
        p.set(x, b + 2 + h, z, GOLD);
        p.set(x, b + 3 + h, z, Palette.END_ROD);
    }

    /**
     * 뾰족한 원뿔 첨탑: 슬레이트(Chateau.ROOF) + 금 갈비뼈 8줄 + 금 띠, 꼭대기 금 침과 엔드 막대.
     * baseY 층이 원뿔 밑면(반지름 r), 높이 h 만큼 좁아집니다.
     */
    public static void spire(Placer p, int cx, int cz, int baseY, double r, int h) {
        spire(p, cx, cz, baseY, r, h, Chateau.ROOF, GOLD);
    }

    /** 재료를 지정하는 원뿔 첨탑 (몸체, 갈비뼈·띠) */
    public static void spire(Placer p, int cx, int cz, int baseY, double r, int h, BlockState body, BlockState rib) {
        int ext = (int) Math.ceil(r) + 1;
        if (!p.intersects(cx - ext, cz - ext, cx + ext, cz + ext)) return;
        p.forColumns(cx - ext, cz - ext, cx + ext, cz + ext, (x, z) -> {
            int dx = x - cx, dz = z - cz;
            double d = Math.sqrt(dx * dx + dz * dz);
            double frac = (Shapes.angle(dx, dz) * 8 / (Math.PI * 2)) % 1.0;
            boolean isRib = frac < 0.13 || frac > 0.95;
            for (int k = 0; k < h; k++) {
                double rk = (r + 0.5) * (1.0 - (double) k / h);
                if (d > rk) break;
                boolean shell = d > rk - 1.25 || k == 0;
                if (!shell) continue;
                BlockState st = (k > 0 && k % 7 == 3) ? rib : (isRib && d > 0.8) ? rib : body;
                if (k == 0) st = d > r - 0.5 ? Palette.CHISELED_QUARTZ : Palette.SMOOTH_QUARTZ;
                p.set(x, baseY + k, z, st);
            }
        });
        for (int k = 0; k < 4; k++) p.set(cx, baseY + h + k, cz, rib);
        p.set(cx, baseY + h + 4, cz, Palette.END_ROD);
    }

    /** 가는 침탑(피너클): 석영 기둥 + 조각 석영 + 금 + 엔드 막대 */
    public static void pinnacle(Placer p, int x, int z, int b, int h) {
        for (int y = b; y < b + h; y++) p.set(x, y, z, Palette.QUARTZ_PILLAR);
        p.set(x, b + h, z, Palette.CHISELED_QUARTZ);
        p.set(x, b + h + 1, z, GOLD);
        p.set(x, b + h + 2, z, Palette.END_ROD);
    }
}
