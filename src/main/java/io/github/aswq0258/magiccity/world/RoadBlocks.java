package io.github.aswq0258.magiccity.world;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 길과 길가 장식 블록 (지형 생성 때 바로 놓임 — 블록 엔티티가 있는 블록은 쓰지 않음).
 *  - 왕도/교역로: 석재 벽돌·안산암 포장, 자갈 갓길, 가로등
 *  - 숲길/산악길: 흙길, 거친 흙, 높은 산에서는 자갈
 *  - 봉쇄된 옛길: 금 간 석재·이끼 낀 조약돌 사이로 풀이 자란 길, 쓰러진 가로등
 *  - 물을 건너면 다리(왕도 = 석재, 그 외 = 가문비나무) 와 난간
 *  - 길마다 다른 길가 장식 (설산 = 눈더미, 거미 숲 = 거미줄·버섯, 광산길 = 돌무더기 ...)
 */
final class RoadBlocks {
    private RoadBlocks() {}

    /** "여긴 일부러 비워 둠" 표시 (길 위의 눈 같은 것) */
    static final BlockState KEEP_AIR = Blocks.STRUCTURE_VOID.defaultBlockState();

    private static BlockState b(net.minecraft.world.level.block.Block block) {
        return block.defaultBlockState();
    }

    private static int hash(int x, int z, int salt) {
        long h = x * 73856093L ^ z * 19349663L ^ salt * 83492791L;
        h ^= h >>> 13;
        h *= 0x5bd1e995L;
        h ^= h >>> 15;
        return (int) (h & 0x7fffffff);
    }

    static BlockState at(KingdomTerrain.Column c, int x, int y, int z) {
        int h = c.h(), idx = c.road() - 1;
        KingdomRoads.Road road = KingdomRoads.ROADS[idx];
        boolean wet = c.water() != Integer.MIN_VALUE && c.water() >= h;
        int k = hash(x, z, 1);
        if (c.roadEdge() <= 0) {
            if (wet) {                                                              // 다리
                int deck = c.water() + 1;
                boolean edge = c.roadEdge() > -0.9;
                if (road.type() == 3 && k % 3 == 0) return y == deck + 1 ? KEEP_AIR : null;   // 무너진 다리 (군데군데 빠진 판자)
                if (y == deck) return road.type() == 1 ? (edge ? b(Blocks.STONE_BRICKS) : b(Blocks.POLISHED_ANDESITE)) : b(Blocks.SPRUCE_PLANKS);
                if (y == deck + 1) return edge ? (road.type() == 1 ? b(Blocks.STONE_BRICK_WALL)
                        : Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y)) : KEEP_AIR;
                if (y == deck + 2 && edge && road.type() == 1 && k % 23 == 0) return Blocks.LANTERN.defaultBlockState();
                if (y < deck && y > h && edge && k % 7 == 0) return b(Blocks.SPRUCE_LOG);   // 다리 기둥
                return null;
            }
            if (y == h) return surface(road.type(), c, k);
            if (y == h + 1) return KEEP_AIR;
            if (y < h && y >= h - 2 && road.type() == 1) return b(Blocks.GRAVEL);      // 길 밑 다짐
            return null;
        }
        if (wet || c.roadEdge() <= 1.0 || c.roadEdge() > 2.4 || y <= h || y > h + 3) return null;
        return roadside(idx, road.type(), c, x, y - h, z);
    }

    private static BlockState surface(int type, KingdomTerrain.Column c, int k) {
        boolean shoulder = c.roadEdge() > -0.9;
        KingdomTerrain.Kind kind = c.kind();
        boolean dark = kind == KingdomTerrain.Kind.ASH || kind == KingdomTerrain.Kind.CORRUPT;
        if (type == 1) {
            if (shoulder) return k % 3 == 0 ? b(Blocks.GRAVEL) : b(Blocks.COBBLESTONE);
            int m = k % 12;
            return m < 5 ? b(Blocks.STONE_BRICKS) : m < 8 ? b(Blocks.POLISHED_ANDESITE) : m < 10 ? b(Blocks.ANDESITE)
                    : m < 11 ? b(Blocks.CRACKED_STONE_BRICKS) : b(Blocks.MOSSY_STONE_BRICKS);
        }
        if (type == 3) {
            int m = k % 10;
            if (dark) return m < 4 ? b(Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS) : m < 7 ? b(Blocks.BLACKSTONE) : m < 9 ? b(Blocks.GRAVEL) : b(Blocks.COARSE_DIRT);
            return m < 3 ? b(Blocks.CRACKED_STONE_BRICKS) : m < 5 ? b(Blocks.MOSSY_COBBLESTONE) : m < 7 ? b(Blocks.COARSE_DIRT)
                    : m < 8 ? b(Blocks.GRAVEL) : shoulder ? b(Blocks.GRASS_BLOCK) : b(Blocks.MOSSY_STONE_BRICKS);
        }
        if (c.h() > 112) return k % 3 == 0 ? b(Blocks.COBBLESTONE) : b(Blocks.GRAVEL);             // 높은 산길
        if (shoulder) return k % 2 == 0 ? b(Blocks.COARSE_DIRT) : b(Blocks.DIRT_PATH);
        return k % 6 == 0 ? b(Blocks.COARSE_DIRT) : k % 13 == 0 ? b(Blocks.GRAVEL) : b(Blocks.DIRT_PATH);
    }

    /** 길가 장식 (dy = 지면 위 높이 1~3) */
    private static BlockState roadside(int idx, int type, KingdomTerrain.Column c, int x, int dy, int z) {
        int k = hash(x, z, 7 + idx);
        if (k % 173 == 0) {                                                         // 가로등 (왕도·교역로), 옛길은 쓰러진 기둥
            if (type == 1) return dy < 3 ? b(Blocks.SPRUCE_FENCE) : Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false);
            if (type == 3) return dy == 1 ? b(Blocks.SPRUCE_FENCE) : null;
        }
        if (dy != 1 || k % 41 != 0) return null;
        int m = (k / 41) % 4;
        if (idx >= KingdomRoads.REGIONS) idx = type == 3 ? 9 : type == 1 ? 8 : 5;      // 지선·군용로: 비슷한 길의 장식
        return switch (idx) {
            case 0 -> m == 0 ? b(Blocks.COBBLESTONE) : m == 1 ? b(Blocks.PACKED_ICE) : b(Blocks.SNOW_BLOCK);                    // 설산 원정길
            case 1 -> m == 0 ? b(Blocks.COBWEB) : m == 1 ? b(Blocks.RED_MUSHROOM) : m == 2 ? b(Blocks.BROWN_MUSHROOM) : b(Blocks.MOSS_CARPET); // 거미 숲길
            case 2 -> m < 2 ? b(Blocks.COBBLESTONE) : m == 2 ? b(Blocks.MOSSY_COBBLESTONE) : b(Blocks.GRAVEL);                  // 광산 무역로 돌무더기
            case 3 -> m == 0 ? b(Blocks.BONE_BLOCK) : m == 1 ? b(Blocks.COBWEB) : b(Blocks.COBBLESTONE);                       // 비룡 고갯길
            case 4 -> m == 0 ? b(Blocks.HAY_BLOCK) : m == 1 ? b(Blocks.COMPOSTER) : b(Blocks.COARSE_DIRT);                     // 상단 교역로
            case 5 -> m == 0 ? b(Blocks.HAY_BLOCK) : m == 1 ? b(Blocks.PUMPKIN) : m == 2 ? b(Blocks.POPPY) : b(Blocks.DANDELION); // 농경 왕도
            case 6 -> m == 0 ? b(Blocks.MUD) : m == 1 ? b(Blocks.MOSSY_COBBLESTONE) : b(Blocks.FERN);                         // 강변길
            case 7 -> m == 0 ? b(Blocks.MUD) : m == 1 ? b(Blocks.BROWN_MUSHROOM) : b(Blocks.MUDDY_MANGROVE_ROOTS);            // 늪 교역로
            case 8 -> m == 0 ? b(Blocks.COBBLESTONE_WALL) : m == 1 ? b(Blocks.COBWEB) : b(Blocks.DEAD_BUSH);                  // 저주받은 국경로 (묘비)
            default -> m == 0 ? b(Blocks.BONE_BLOCK) : m == 1 ? b(Blocks.BLACKSTONE) : b(Blocks.DEAD_BUSH);                   // 폐왕도
        };
    }
}
