package io.github.aswq0258.magiccity.city;

import io.github.aswq0258.magiccity.build.Placer;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * 실내 가구와 외장 장식.
 * 실내는 따뜻한 자작나무 톤, 외장은 흰 석영 + 민트.
 */
public final class Deco {
    private Deco() {}

    // ── 실내 (따뜻한 나무) ──
    public static final BlockState PLANKS = Blocks.BIRCH_PLANKS.defaultBlockState();
    public static final BlockState FENCE = Blocks.BIRCH_FENCE.defaultBlockState();
    public static final BlockState PLATE = Blocks.BIRCH_PRESSURE_PLATE.defaultBlockState();
    public static final BlockState CHAIN = Blocks.CHAIN.defaultBlockState();
    public static final BlockState BARREL = Blocks.BARREL.defaultBlockState();
    public static final BlockState BREWING = Blocks.BREWING_STAND.defaultBlockState();
    public static final BlockState CRAFTING = Blocks.CRAFTING_TABLE.defaultBlockState();
    public static final BlockState CARPET_WHITE = Blocks.WHITE_CARPET.defaultBlockState();
    public static final BlockState CARPET_MINT = Blocks.LIGHT_BLUE_CARPET.defaultBlockState();
    public static final BlockState WOOL_WHITE = Blocks.WHITE_WOOL.defaultBlockState();
    public static final BlockState WOOL_MINT = Blocks.LIGHT_BLUE_WOOL.defaultBlockState();
    public static final BlockState CAMPFIRE = Blocks.CAMPFIRE.defaultBlockState();
    public static final BlockState WATER_CAULDRON = Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3);

    public static final BlockState[] POTS = {
            Blocks.POTTED_FLOWERING_AZALEA.defaultBlockState(),
            Blocks.POTTED_AZALEA.defaultBlockState(),
            Blocks.POTTED_WHITE_TULIP.defaultBlockState(),
            Blocks.POTTED_LILY_OF_THE_VALLEY.defaultBlockState(),
            Blocks.POTTED_FERN.defaultBlockState(),
    };

    public static BlockState pot(int i) {
        return POTS[Math.floorMod(i, POTS.length)];
    }

    public static BlockState hangingLantern() {
        return Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
    }

    public static BlockState candles(int n) {
        return Blocks.WHITE_CANDLE.defaultBlockState()
                .setValue(CandleBlock.CANDLES, Math.max(1, Math.min(4, n)))
                .setValue(CandleBlock.LIT, true);
    }

    public static BlockState chest(Direction facing) {
        return Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing);
    }

    public static BlockState lectern(Direction facing) {
        return Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, facing);
    }

    public static BlockState smoker(Direction facing) {
        return Blocks.SMOKER.defaultBlockState().setValue(AbstractFurnaceBlock.FACING, facing);
    }

    /** 의자 = 계단. backrest 는 등받이가 있는 쪽 방향 */
    public static BlockState chair(Direction backrest) {
        return Blocks.BIRCH_STAIRS.defaultBlockState().setValue(StairBlock.FACING, backrest);
    }

    public static BlockState woodStairs(Direction ascend) {
        return Blocks.BIRCH_STAIRS.defaultBlockState().setValue(StairBlock.FACING, ascend);
    }

    public static BlockState quartzStairs(Direction ascend) {
        return Blocks.SMOOTH_QUARTZ_STAIRS.defaultBlockState().setValue(StairBlock.FACING, ascend);
    }

    /** 뒤집힌 석영 계단 (처마 장식, 내물림) */
    public static BlockState corbel(Direction towardWall) {
        return Blocks.QUARTZ_STAIRS.defaultBlockState()
                .setValue(StairBlock.FACING, towardWall)
                .setValue(StairBlock.HALF, Half.TOP);
    }

    public static BlockState topSlab(Block slab) {
        return slab.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP);
    }

    // ── 가구 배치 ──

    public static void table(Placer p, int x, int y, int z) {
        p.set(x, y, z, FENCE);
        p.set(x, y + 1, z, PLATE);
    }

    /** ceilingY: 천장 블록 높이. 그 아래로 사슬 + 매달린 랜턴 */
    public static void chandelier(Placer p, int x, int ceilingY, int z, int chainLength) {
        for (int i = 1; i <= chainLength; i++) p.set(x, ceilingY - i, z, CHAIN);
        p.set(x, ceilingY - chainLength - 1, z, hangingLantern());
    }

    /** 가장 가까운 4방향 중 (dx,dz) 쪽 방향 */
    public static Direction toward(double dx, double dz) {
        if (Math.abs(dx) >= Math.abs(dz)) return dx >= 0 ? Direction.EAST : Direction.WEST;
        return dz >= 0 ? Direction.SOUTH : Direction.NORTH;
    }
}
