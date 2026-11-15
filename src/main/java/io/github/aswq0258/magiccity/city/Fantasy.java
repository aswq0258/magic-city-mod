package io.github.aswq0258.magiccity.city;

import io.github.aswq0258.magiccity.build.Placer;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.Direction;

import static io.github.aswq0258.magiccity.city.Palette.*;

/** 판타지 장식: 수정 가로등, 떠 있는 수정 고리(헤일로), 떠다니는 섬 */
public final class Fantasy {
    private Fantasy() {}

    public static final BlockState AMETHYST = Blocks.AMETHYST_BLOCK.defaultBlockState();
    public static final BlockState CLUSTER_UP = Blocks.AMETHYST_CLUSTER.defaultBlockState().setValue(AmethystClusterBlock.FACING, Direction.UP);
    public static final BlockState CLUSTER_DOWN = Blocks.AMETHYST_CLUSTER.defaultBlockState().setValue(AmethystClusterBlock.FACING, Direction.DOWN);

    /** 수정 가로등: 민트 받침 + 석영 기둥 + 빛 + 자수정, 위에 작은 수정이 떠 있음 */
    public static void crystalLamp(Placer p, int x, int z, int b) {
        p.set(x, b + 1, z, MINT_CHISELED);
        p.set(x, b + 2, z, QUARTZ_PILLAR);
        p.set(x, b + 3, z, QUARTZ_PILLAR);
        p.set(x, b + 4, z, LIGHT);
        p.set(x, b + 5, z, AMETHYST);
        p.set(x, b + 6, z, CLUSTER_UP);
        p.set(x, b + 8, z, END_ROD);
    }

    /** 공중에 뜬 고리: 반지름 r, 금 블록과 엔드 막대가 번갈아 */
    public static void halo(Placer p, int cx, int cz, int y, double r) {
        int ext = (int) Math.ceil(r) + 1;
        p.forColumns(cx - ext, cz - ext, cx + ext, cz + ext, (x, z) -> {
            double d = Math.hypot(x - cx, z - cz);
            if (Math.abs(d - r) > 0.5) return;
            int arc = (int) Math.floor(Shapes.angle(x - cx, z - cz) * r);
            p.set(x, y, z, arc % 3 == 0 ? END_ROD : Royal.GOLD);
        });
    }

    /**
     * 떠다니는 섬: 아래로 뾰족한 흙·돌 덩어리, 위는 잔디와 꽃, 자작나무, 아래에 매달린 자수정과 빛.
     */
    public static void floatingIsland(Placer p, int cx, int cz, int y, int r, long seed) {
        int ext = r + 3;
        if (!p.intersects(cx - ext, cz - ext, cx + ext, cz + ext)) return;
        int depth = r + 3;
        p.forColumns(cx - r - 1, cz - r - 1, cx + r + 1, cz + r + 1, (x, z) -> {
            int h = Shapes.hash(x, 77, z, seed);
            double d = Math.hypot(x - cx, z - cz) + (h % 7) / 10.0;
            if (d > r + 0.5) return;
            int under = (int) Math.round((1.0 - d / (r + 0.5)) * depth) + (h % 2);
            for (int k = 0; k <= under; k++) {
                BlockState s = k == 0 ? GRASS : k <= 2 ? Blocks.DIRT.defaultBlockState()
                        : (h >> k) % 5 == 0 ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : Blocks.STONE.defaultBlockState();
                p.set(x, y - k, z, s);
            }
            if (h % 23 == 0) p.set(x, y - under - 1, z, CLUSTER_DOWN);
            else if (h % 31 == 0) p.set(x, y - under - 1, z, END_ROD_DOWN);
            if (d < r - 1 && h % 6 == 0) p.set(x, y + 1, z, FLOWERS[h % FLOWERS.length]);
        });
        p.set(cx, y - depth - 1, cz, LIGHT);
        p.set(cx, y - depth - 2, cz, CLUSTER_DOWN);
        Shapes.birchTree(p, cx + 1, cz - 1, y, 5 + (int) (seed & 1));
    }
}
