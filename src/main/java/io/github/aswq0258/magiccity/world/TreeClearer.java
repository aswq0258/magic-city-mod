package io.github.aswq0258.magiccity.world;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 길가 나무를 "한 그루째" 자연스럽게 없앰 (조각조각 잘라 내지 않음).
 *  - 길에서 가까운 줄기 → 이어진 줄기·가지(통나무, 큰 버섯 대·갓) 를 통째로 지우고, 주인 없는 잎·덩굴도 지움
 *  - 길 위로 늘어진 잎 → 그 잎의 주인 나무를 통째로 지움
 *  - 근처에 줄기가 없이 떠 있는 잎(예전에 잘린 흔적) → 지움
 */
public final class TreeClearer {
    private TreeClearer() {}

    /** 블록 읽기·쓰기 (지형 생성 영역 또는 실제 월드) */
    public interface Access {
        BlockState get(BlockPos p);

        void set(BlockPos p, BlockState s);

        boolean ok(int x, int z);
    }

    /** 줄기 판정 거리 (길 가장자리에서) */
    public static final double WOOD = 9, OVERHANG = 5, ORPHAN = 12;

    public static boolean wood(BlockState s) {
        return s.is(BlockTags.LOGS) || s.is(Blocks.MUSHROOM_STEM) || s.is(Blocks.BROWN_MUSHROOM_BLOCK) || s.is(Blocks.RED_MUSHROOM_BLOCK);
    }

    public static boolean foliage(BlockState s) {
        return s.is(BlockTags.LEAVES) || s.is(Blocks.VINE) || s.is(Blocks.COCOA) || s.is(Blocks.BEE_NEST);
    }

    /**
     * 기둥 (x, z) 의 y0~y1 을 살펴 길가 나무를 정리. edge = 길 가장자리까지 거리, offset = 판정 거리에 더할 값
     */
    public static void clearColumn(Access a, int x, int z, int y0, int y1, double edge, double offset) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int y = y0; y <= y1; y++) {
            BlockState s = a.get(m.set(x, y, z));
            if (s.isAir()) continue;
            if (wood(s)) {
                if (edge <= WOOD + offset) removeTree(a, m.immutable());
            } else if (foliage(s)) {
                BlockPos log = nearestWood(a, m, 5);
                if (log == null) {
                    if (edge <= ORPHAN + offset) a.set(m.immutable(), Blocks.AIR.defaultBlockState());
                } else if (edge <= OVERHANG + offset) {
                    removeTree(a, log);
                }
            }
        }
    }

    public static BlockPos nearestWood(Access a, BlockPos c, int r) {
        BlockPos best = null;
        int bd = Integer.MAX_VALUE;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
            if (!a.ok(c.getX() + dx, c.getZ() + dz)) continue;
            for (int dy = -r; dy <= r; dy++) {
                int d = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
                if (d >= bd) continue;
                if (wood(a.get(m.set(c.getX() + dx, c.getY() + dy, c.getZ() + dz)))) {
                    bd = d;
                    best = m.immutable();
                }
            }
        }
        return best;
    }

    /** 이어진 줄기·가지를 통째로 지우고, 남은 줄기에 닿지 않는 잎·덩굴을 지움 */
    public static void removeTree(Access a, BlockPos seed) {
        Set<BlockPos> woodSet = new HashSet<>();
        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        q.add(seed);
        woodSet.add(seed);
        while (!q.isEmpty() && woodSet.size() < 1500) {
            BlockPos p = q.poll();
            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                BlockPos n = p.offset(dx, dy, dz);
                if (woodSet.contains(n) || !a.ok(n.getX(), n.getZ())) continue;
                if (wood(a.get(n))) {
                    woodSet.add(n);
                    q.add(n);
                }
            }
        }
        for (BlockPos p : woodSet) a.set(p, Blocks.AIR.defaultBlockState());
        // 주인 잃은 잎: 지운 줄기 둘레 6칸 안의 잎 중, 4칸 안에 다른 줄기가 없는 것
        Set<BlockPos> leaves = new HashSet<>();
        for (BlockPos p : woodSet) {
            for (int dx = -5; dx <= 5; dx++) for (int dy = -5; dy <= 5; dy++) for (int dz = -5; dz <= 5; dz++) {
                if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) > 7) continue;
                BlockPos n = p.offset(dx, dy, dz);
                if (leaves.contains(n) || !a.ok(n.getX(), n.getZ())) continue;
                if (foliage(a.get(n))) leaves.add(n);
            }
        }
        List<BlockPos> drop = new ArrayList<>();
        for (BlockPos l : leaves) if (nearestWood(a, l, 4) == null) drop.add(l);
        for (BlockPos l : drop) a.set(l, Blocks.AIR.defaultBlockState());
    }
}
