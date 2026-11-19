package io.github.aswq0258.magiccity.arena;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 전장 지형 (2026-11-19). 장별 테마 전장은 나중에 정하기로 했으므로(사용자 결정) 1차는 공용 기본 전장과
 * 6장(엘더 가디언)용 물로 찬 돔 두 가지. 들어갈 때마다 비우고 새로 깐다.
 */
public final class ArenaShapes {
    private ArenaShapes() {}

    private static final int FLAGS = Block.UPDATE_CLIENTS;
    private static final int CLEAR_R = 36, CLEAR_Y0 = ArenaRules.FLOOR_Y - 4, CLEAR_Y1 = ArenaRules.FLOOR_Y + 48;

    /** 전장 비우고 깔기. c = 바닥 중심 */
    public static void build(ServerLevel level, BlockPos c, String shape) {
        clear(level, c);
        if ("water".equals(shape)) water(level, c);
        else basic(level, c);
    }

    /** 전장 자리를 모두 공기로 */
    public static void clear(ServerLevel level, BlockPos c) {
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -CLEAR_R; dx <= CLEAR_R; dx++) for (int dz = -CLEAR_R; dz <= CLEAR_R; dz++) {
            if (dx * dx + dz * dz > CLEAR_R * CLEAR_R) continue;
            for (int y = CLEAR_Y0; y <= CLEAR_Y1; y++) {
                m.set(c.getX() + dx, y, c.getZ() + dz);
                if (!level.getBlockState(m).isAir()) level.setBlock(m, air, FLAGS);
            }
        }
    }

    /** 공용 기본 전장: 반지름 28 원형 바닥(두 겹), 높이 6 벽, 반지름 20 에 기둥 8개와 등불 */
    private static void basic(ServerLevel level, BlockPos c) {
        int r = ArenaRules.RADIUS, f = c.getY();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -r - 2; dx <= r + 2; dx++) for (int dz = -r - 2; dz <= r + 2; dz++) {
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d > r + 1.5) continue;
            int x = c.getX() + dx, z = c.getZ() + dz;
            set(level, m, x, f - 1, z, Blocks.DEEPSLATE_TILES.defaultBlockState());
            BlockState floor = d > r - 2 ? Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState()
                    : ((dx + dz) & 1) == 0 ? Blocks.STONE_BRICKS.defaultBlockState() : Blocks.POLISHED_ANDESITE.defaultBlockState();
            if (d < 3) floor = Blocks.CHISELED_STONE_BRICKS.defaultBlockState();
            set(level, m, x, f, z, floor);
            if (d > r - 0.5) for (int y = f + 1; y <= f + 6; y++)
                set(level, m, x, y, z, y == f + 6 ? Blocks.POLISHED_BLACKSTONE_BRICK_WALL.defaultBlockState() : Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
        }
        for (int k = 0; k < 8; k++) {
            double a = Math.PI * 2 * k / 8;
            int x = c.getX() + (int) Math.round(Math.cos(a) * 20), z = c.getZ() + (int) Math.round(Math.sin(a) * 20);
            for (int y = f + 1; y <= f + 7; y++) set(level, m, x, y, z, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
            set(level, m, x, f + 8, z, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false));
        }
    }

    /** 6장: 반지름 24 유리 원통 돔에 물을 채움, 바닥 프리즈머린과 바다 랜턴 */
    private static void water(ServerLevel level, BlockPos c) {
        int r = ArenaRules.WATER_RADIUS, f = c.getY(), top = f + 28;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        BlockState water = Blocks.WATER.defaultBlockState(), glass = Blocks.GLASS.defaultBlockState();
        for (int dx = -r - 1; dx <= r + 1; dx++) for (int dz = -r - 1; dz <= r + 1; dz++) {
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d > r + 1.0) continue;
            int x = c.getX() + dx, z = c.getZ() + dz;
            boolean wall = d > r - 0.5;
            set(level, m, x, f - 1, z, Blocks.DARK_PRISMARINE.defaultBlockState());
            set(level, m, x, f, z, wall ? Blocks.PRISMARINE_BRICKS.defaultBlockState()
                    : (Math.abs(dx) % 6 == 0 && Math.abs(dz) % 6 == 0) ? Blocks.SEA_LANTERN.defaultBlockState() : Blocks.PRISMARINE_BRICKS.defaultBlockState());
            for (int y = f + 1; y < top; y++) set(level, m, x, y, z, wall ? glass : water);
            set(level, m, x, top, z, glass);
        }
    }

    private static void set(ServerLevel level, BlockPos.MutableBlockPos m, int x, int y, int z, BlockState s) {
        m.set(x, y, z);
        level.setBlock(m, s, FLAGS);
    }
}
