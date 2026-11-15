package io.github.aswq0258.magiccity.city;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.EndRodBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * 도시 색상 팔레트.
 *  흰색 메인 : 석영 계열, 방해석, 흰색 콘크리트
 *  민트 포인트: 밀랍칠한 산화 구리 (시간이 지나도 색이 변하지 않음), 하늘색 유리
 *  조명       : 바다 랜턴, 엔드 막대
 * 색을 바꾸고 싶으면 이 파일만 고치면 됩니다.
 */
public final class Palette {
    private Palette() {}

    // ── 흰색 메인 ──
    public static final BlockState QUARTZ_BRICKS = Blocks.QUARTZ_BRICKS.defaultBlockState();
    public static final BlockState SMOOTH_QUARTZ = Blocks.SMOOTH_QUARTZ.defaultBlockState();
    public static final BlockState QUARTZ_BLOCK = Blocks.QUARTZ_BLOCK.defaultBlockState();
    public static final BlockState CHISELED_QUARTZ = Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState();
    public static final BlockState QUARTZ_PILLAR = Blocks.QUARTZ_PILLAR.defaultBlockState();
    public static final BlockState SMOOTH_QUARTZ_SLAB = Blocks.SMOOTH_QUARTZ_SLAB.defaultBlockState();
    public static final BlockState CALCITE = Blocks.CALCITE.defaultBlockState();
    public static final BlockState WHITE_CONCRETE = Blocks.WHITE_CONCRETE.defaultBlockState();

    // ── 민트 포인트 ──
    public static final BlockState MINT_BLOCK = Blocks.WAXED_OXIDIZED_COPPER.defaultBlockState();
    public static final BlockState MINT_CUT = Blocks.WAXED_OXIDIZED_CUT_COPPER.defaultBlockState();
    public static final BlockState MINT_SLAB = Blocks.WAXED_OXIDIZED_CUT_COPPER_SLAB.defaultBlockState();
    public static final BlockState MINT_CHISELED = Blocks.WAXED_OXIDIZED_CHISELED_COPPER.defaultBlockState();
    public static final BlockState GLASS = Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState();
    public static final BlockState GLASS_PANE = Blocks.LIGHT_BLUE_STAINED_GLASS_PANE.defaultBlockState();
    public static final BlockState GLASS_WHITE = Blocks.WHITE_STAINED_GLASS.defaultBlockState();

    // ── 조명 ──
    public static final BlockState LIGHT = Blocks.SEA_LANTERN.defaultBlockState();
    public static final BlockState END_ROD = Blocks.END_ROD.defaultBlockState();
    public static final BlockState END_ROD_DOWN = Blocks.END_ROD.defaultBlockState().setValue(EndRodBlock.FACING, Direction.DOWN);

    // ── 자연/기타 ──
    public static final BlockState AIR = Blocks.AIR.defaultBlockState();
    public static final BlockState WATER = Blocks.WATER.defaultBlockState();
    public static final BlockState GRASS = Blocks.GRASS_BLOCK.defaultBlockState();
    public static final BlockState BIRCH_LOG = Blocks.BIRCH_LOG.defaultBlockState();
    public static final BlockState BIRCH_LEAVES = Blocks.BIRCH_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
    public static final BlockState BOOKSHELF = Blocks.BOOKSHELF.defaultBlockState();
    public static final BlockState ENCHANTING_TABLE = Blocks.ENCHANTING_TABLE.defaultBlockState();

    public static final BlockState[] FLOWERS = {
            Blocks.LILY_OF_THE_VALLEY.defaultBlockState(),
            Blocks.WHITE_TULIP.defaultBlockState(),
            Blocks.OXEYE_DAISY.defaultBlockState(),
            Blocks.AZURE_BLUET.defaultBlockState(),
    };

    /** 주민 직업 블록 */
    public static final BlockState[] JOB_BLOCKS = {
            Blocks.CARTOGRAPHY_TABLE.defaultBlockState(),
            Blocks.FLETCHING_TABLE.defaultBlockState(),
            Blocks.SMITHING_TABLE.defaultBlockState(),
            Blocks.LOOM.defaultBlockState(),
            Blocks.BARREL.defaultBlockState(),
            Blocks.COMPOSTER.defaultBlockState(),
            Blocks.STONECUTTER.defaultBlockState(),
            Blocks.SMOKER.defaultBlockState(),
            Blocks.BLAST_FURNACE.defaultBlockState(),
            Blocks.LECTERN.defaultBlockState(),
    };

    public static BlockState mintStairs(Direction facing) {
        return stairs(Blocks.WAXED_OXIDIZED_CUT_COPPER_STAIRS, facing);
    }

    public static BlockState stairs(Block block, Direction facing) {
        return block.defaultBlockState().setValue(StairBlock.FACING, facing).setValue(StairBlock.HALF, Half.BOTTOM);
    }

    public static BlockState door(Direction facing, boolean upper) {
        return Blocks.BIRCH_DOOR.defaultBlockState()
                .setValue(DoorBlock.FACING, facing)
                .setValue(DoorBlock.HALF, upper ? DoubleBlockHalf.UPPER : DoubleBlockHalf.LOWER);
    }

    public static BlockState bed(Direction facing, boolean head) {
        return Blocks.WHITE_BED.defaultBlockState()
                .setValue(BedBlock.FACING, facing)
                .setValue(BedBlock.PART, head ? BedPart.HEAD : BedPart.FOOT);
    }

    public static BlockState ladder(Direction facing) {
        return Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, facing);
    }
}
