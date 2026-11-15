package io.github.aswq0258.magiccity.city;

import io.github.aswq0258.magiccity.build.Placer;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;

import static io.github.aswq0258.magiccity.city.Palette.*;

/**
 * 프랑스 고딕·르네상스 성(루아르 계곡 샤토) 요소
 *  - 가파른 모임지붕(경사 2:1, 슬레이트 = 심층암 타일) + 금 용마루 장식
 *  - 돌 지붕창(뤼카른): 박공 + 피너클
 *  - 장식 굴뚝 (꼭대기 모닥불 연기)
 *  - 벽에 매달린 작은 탑(에쇼게트): 코벨 받침 + 원통 + 원뿔 지붕
 * 지붕 색을 바꾸려면 ROOF / ROOF_STAIRS 만 바꾸면 됩니다 (원뿔 첨탑도 같은 색을 씀).
 */
public final class Chateau {
    private Chateau() {}

    public static final BlockState ROOF = Blocks.DEEPSLATE_TILES.defaultBlockState();

    public static BlockState roofStairs(Direction ascend) {
        return Blocks.DEEPSLATE_TILE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, ascend);
    }

    /**
     * 가파른 모임지붕. 벽 (x0,z0)~(x1,z1) 위, y 높이에서 시작하고 1칸 처마가 나옵니다.
     * holeR > 0 이면 (hx, hz) 둘레 원 안은 비웁니다 (지붕을 뚫고 올라가는 탑 자리).
     * @return 용마루 꼭대기 y
     */
    public static int hipRoof(Placer p, int x0, int z0, int x1, int z1, int y, int maxRise, double holeR, int hx, int hz) {
        return hipRoof(p, x0, z0, x1, z1, y, maxRise, holeR, hx, hz, ROOF, Chateau::roofStairs, Royal.GOLD);
    }

    /** 재료를 지정하는 모임지붕 (판, 계단, 용마루 장식) */
    public static int hipRoof(Placer p, int x0, int z0, int x1, int z1, int y, int maxRise, double holeR, int hx, int hz,
                              BlockState roof, java.util.function.Function<Direction, BlockState> stairs, BlockState crest) {
        final int ox0 = Math.min(x0, x1) - 1, oz0 = Math.min(z0, z1) - 1, ox1 = Math.max(x0, x1) + 1, oz1 = Math.max(z0, z1) + 1;
        final int ridgeD = Math.min((Math.min(ox1 - ox0, oz1 - oz0)) / 2, Math.max(1, maxRise / 2));
        if (!p.intersects(ox0, oz0, ox1, oz1)) return y + 2 * ridgeD + 2;
        p.forColumns(ox0, oz0, ox1, oz1, (x, z) -> {
            if (holeR > 0 && Math.hypot(x - hx, z - hz) <= holeR + 0.5) return;
            int dW = x - ox0, dE = ox1 - x, dN = z - oz0, dS = oz1 - z;
            int d = Math.min(Math.min(dW, dE), Math.min(dN, dS));
            Direction out = d == dW ? Direction.WEST : d == dE ? Direction.EAST : d == dN ? Direction.NORTH : Direction.SOUTH;
            int ly = 2 * Math.min(d, ridgeD);
            if (d >= ridgeD) {
                p.set(x, y + ly, z, roof);
                p.set(x, y + ly + 1, z, roof);
                if (d == ridgeD && Math.floorMod(x + z, 2) == 0) p.set(x, y + ly + 2, z, crest);
            } else {
                p.set(x, y + ly, z, roof);
                p.set(x, y + ly + 1, z, stairs.apply(out.getOpposite()));
            }
        });
        return y + 2 * ridgeD + 2;
    }

    /** 지붕 위에 지붕창들과 굴뚝 2개를 얹음. chimneyTop < 0 이면 굴뚝 없음 */
    public static void dressRoof(Placer p, int x0, int z0, int x1, int z1, int y, int chimneyTop) {
        int ax0 = Math.min(x0, x1), az0 = Math.min(z0, z1), ax1 = Math.max(x0, x1), az1 = Math.max(z0, z1);
        boolean longX = ax1 - ax0 >= az1 - az0;
        int len = longX ? ax1 - ax0 : az1 - az0;
        int n = Math.max(1, (len - 6) / 9);
        for (int i = 0; i < n; i++) {
            int along = (longX ? ax0 : az0) + (len * (2 * i + 1)) / (2 * n);
            if (longX) {
                dormer(p, along, az0, y, Direction.NORTH);
                dormer(p, along, az1, y, Direction.SOUTH);
            } else {
                dormer(p, ax0, along, y, Direction.WEST);
                dormer(p, ax1, along, y, Direction.EAST);
            }
        }
        if (chimneyTop > 0) {
            int top = chimneyTop;
            int cx = (ax0 + ax1) >> 1, cz = (az0 + az1) >> 1;
            int off = Math.max(3, len / 3);
            if (longX) {
                chimney(p, cx - off, cz, y, top, Direction.EAST);
                chimney(p, cx + off, cz, y, top, Direction.EAST);
            } else {
                chimney(p, cx, cz - off, y, top, Direction.SOUTH);
                chimney(p, cx, cz + off, y, top, Direction.SOUTH);
            }
        }
    }

    /**
     * 돌 지붕창(뤼카른). (x, z) = 벽면 위 가운데, out = 바깥쪽 방향. y = 지붕(처마) 시작 높이.
     * 폭 3, 깊이 3, 높이 6 + 금 피너클
     */
    public static void dormer(Placer p, int x, int z, int y, Direction out) {
        Direction in = out.getOpposite(), side = out.getClockWise();
        for (int lat = -1; lat <= 1; lat++) {
            for (int dep = 0; dep <= 2; dep++) {
                int bx = x + side.getStepX() * lat + in.getStepX() * dep;
                int bz = z + side.getStepZ() * lat + in.getStepZ() * dep;
                for (int ly = 0; ly <= 3; ly++) {
                    BlockState s = QUARTZ_BRICKS;
                    if (dep == 0) {
                        if (lat != 0) s = (ly == 3) ? CHISELED_QUARTZ : QUARTZ_PILLAR;
                        else s = ly == 0 ? SMOOTH_QUARTZ : ly == 3 ? Royal.GOLD : Royal.CLEAR_PANE;
                    }
                    p.set(bx, y + ly, bz, s);
                }
                p.set(bx, y + 4, bz, lat == 0 ? CHISELED_QUARTZ : Royal.whiteStairs(lat < 0 ? side : side.getOpposite()));
                if (lat == 0) p.set(bx, y + 5, bz, dep == 0 ? CHISELED_QUARTZ : SMOOTH_QUARTZ_SLAB);
            }
        }
        Royal.pinnacle(p, x, z, y + 6, 1);
    }

    /** 장식 굴뚝 (2칸 폭, 띠 장식, 꼭대기 모닥불 연기). axis = 2칸이 늘어서는 방향 */
    public static void chimney(Placer p, int x, int z, int y0, int top, Direction axis) {
        for (int k = 0; k <= 1; k++) {
            int cx = x + axis.getStepX() * k, cz = z + axis.getStepZ() * k;
            for (int y = y0; y <= top; y++) p.set(cx, y, cz, (top - y) % 5 == 0 ? CHISELED_QUARTZ : QUARTZ_BRICKS);
            p.set(cx, top + 1, cz, Royal.GOLD);
            p.set(cx, top + 2, cz, Deco.CAMPFIRE);
        }
    }

    /**
     * 벽에 매달린 작은 탑(에쇼게트): y0 아래로 좁아지는 코벨 받침 3단, y0~y0+h 원통(창·금 띠), 원뿔 지붕.
     */
    public static void turret(Placer p, int tx, int tz, int y0, int h, double r) {
        int ext = (int) Math.ceil(r) + 1;
        if (!p.intersects(tx - ext - 2, tz - ext - 2, tx + ext + 2, tz + ext + 2)) return;
        p.forColumns(tx - ext, tz - ext, tx + ext, tz + ext, (x, z) -> {
            int dx = x - tx, dz = z - tz;
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d > r + 0.5) return;
            for (int k = 1; k <= 3; k++) {                                   // 코벨 받침 (아래로 좁아짐)
                if (d <= r + 0.5 - k) p.set(x, y0 - k, z, k == 3 ? Royal.GOLD : CHISELED_QUARTZ);
            }
            boolean wall = d > r - 0.6;
            int arc = (int) Math.floor(Shapes.angle(dx, dz) * r);
            p.set(x, y0, z, wall ? CHISELED_QUARTZ : Royal.marble(x, z));
            for (int ly = 1; ly <= h; ly++) {
                BlockState s = AIR;
                if (wall) {
                    if (ly == h) s = Royal.GOLD;
                    else if (ly % 6 >= 2 && ly % 6 <= 4 && arc % 3 == 0) s = Royal.CLEAR_PANE;
                    else s = ly % 6 == 0 ? CHISELED_QUARTZ : SMOOTH_QUARTZ;
                } else if (ly == h) {
                    s = SMOOTH_QUARTZ;
                }
                p.set(x, y0 + ly, z, s);
            }
        });
        Royal.spire(p, tx, tz, y0 + h + 1, r + 0.5, (int) Math.round(r * 4) + 4);
    }
}
