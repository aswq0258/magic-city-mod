package io.github.aswq0258.magiccity.city.feature;

import io.github.aswq0258.magiccity.build.Placer;
import io.github.aswq0258.magiccity.city.CityLayout;
import io.github.aswq0258.magiccity.city.Deco;
import io.github.aswq0258.magiccity.city.Royal;
import io.github.aswq0258.magiccity.city.Shapes;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;

import static io.github.aswq0258.magiccity.city.Palette.*;

/**
 * 사람이 다니는 외성벽: 성벽 안쪽 면을 따라 올라가는 계단(면마다 2개), 계단 꼭대기 초소(지붕·랜턴·통·모닥불),
 * 초소 둘레에 쌓인 짐(건초·통·무기 손질대).
 * 성벽 위 닳은 바닥, 랜턴, 바깥 깃발은 CityLayout.wallColumn 이 그립니다.
 */
public class WallWalk extends Feature {
    private static final int LEN = CityLayout.WALL_H;     // 계단 길이 = 성벽 높이
    private static final int[] STARTS = {108, 388};
    private final CityLayout c;
    private final long seed;

    public WallWalk(CityLayout city, long seed) {
        super(Math.min(city.wx(CityLayout.W0), city.wx(CityLayout.W1)), Math.min(city.wz(CityLayout.W0), city.wz(CityLayout.W1)),
                Math.max(city.wx(CityLayout.W0), city.wx(CityLayout.W1)), Math.max(city.wz(CityLayout.W0), city.wz(CityLayout.W1)));
        this.c = city;
        this.seed = seed;
    }

    /** side 0=서 1=동 2=남 3=북. t = 바깥면에서 안쪽으로, lat = 벽을 따라 */
    private int u(int side, int t, int lat) {
        return side == 0 ? CityLayout.W0 + t : side == 1 ? CityLayout.W1 - t : lat;
    }

    private int v(int side, int t, int lat) {
        return side == 2 ? CityLayout.W0 + t : side == 3 ? CityLayout.W1 - t : lat;
    }

    private Direction latDir(int side) {
        return side <= 1 ? Direction.NORTH : Direction.EAST;      // lat 증가 = 도면 v 증가(북) 또는 u 증가(동)
    }

    private void put(Placer p, int side, int t, int lat, int ly, BlockState s) {
        p.set(c.wx(u(side, t, lat)), groundY + ly, c.wz(v(side, t, lat)), s);
    }

    @Override
    public boolean occupies(int x, int z) {
        int uu = c.u(x), vv = c.v(z);
        for (int side = 0; side < 4; side++) {
            for (int s : STARTS) {
                int t = side == 0 ? uu - CityLayout.W0 : side == 1 ? CityLayout.W1 - uu : side == 2 ? vv - CityLayout.W0 : CityLayout.W1 - vv;
                int lat = side <= 1 ? vv : uu;
                if (t >= 7 && t <= 9 && lat >= s - 1 && lat <= s + LEN) return true;
            }
        }
        return false;
    }

    @Override
    public void build(Placer p, CityLayout city) {
        // 성벽 바깥 민트 깃발 (탑 자리는 피함)
        Direction[] outward = {Direction.WEST, Direction.EAST, Direction.SOUTH, Direction.NORTH};
        int[] towers = {66, 160, 244, 277, 360, 454};
        for (int side = 0; side < 4; side++) {
            for (int lat = CityLayout.W0 + 12; lat <= CityLayout.W1 - 12; lat += 12) {
                boolean clear = true;
                for (int tw : towers) if (Math.abs(lat - tw) <= 12) clear = false;
                if (!clear) continue;
                put(p, side, -1, lat, LEN - 5, Royal.mintBanner(outward[side]));
            }
        }
        for (int side = 0; side < 4; side++) {
            for (int s : STARTS) {
                int ua = u(side, 0, s), ub = u(side, 9, s + LEN + 6), va = v(side, 0, s), vb = v(side, 9, s + LEN + 6);
                int xa = c.wx(ua), xb = c.wx(ub), za = c.wz(va), zb = c.wz(vb);
                if (!p.intersects(Math.min(xa, xb), Math.min(za, zb), Math.max(xa, xb), Math.max(za, zb))) continue;
                stair(p, side, s);
                booth(p, side, s + LEN + 2);
            }
        }
    }

    /** 성벽 안쪽 면에 붙은 폭 2 계단 (지면 → 성벽 위) */
    private void stair(Placer p, int side, int s) {
        Direction up = latDir(side);
        for (int k = 1; k <= LEN; k++) {
            int lat = s + k - 1;
            for (int t = 7; t <= 8; t++) {
                for (int ly = 1; ly < k; ly++) put(p, side, t, lat, ly, QUARTZ_BRICKS);
                put(p, side, t, lat, k, Royal.whiteStairs(up));
                for (int ly = k + 1; ly <= k + 3; ly++) put(p, side, t, lat, ly, AIR);
            }
            put(p, side, 9, lat, k + 1, k % 2 == 0 ? SMOOTH_QUARTZ_SLAB : AIR);   // 낮은 난간
            if (k % 8 == 4) put(p, side, 9, lat, k + 1, Blocks.LANTERN.defaultBlockState());
        }
        // 꼭대기: 성벽 위로 넘어가는 자리의 안쪽 난간을 터 줌
        for (int lat = s + LEN - 3; lat <= s + LEN + 1; lat++) {
            put(p, side, 6, lat, LEN + 1, AIR);
            for (int t = 7; t <= 8; t++) if (lat >= s + LEN) put(p, side, t, lat, LEN, SMOOTH_QUARTZ);
        }
        // 아래쪽 계단참: 닳은 흙길
        for (int lat = s - 3; lat < s; lat++) {
            for (int t = 7; t <= 9; t++) put(p, side, t, lat, 0, Shapes.hash(lat, t, side, seed) % 2 == 0 ? Blocks.DIRT_PATH.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState());
        }
    }

    /** 성벽 위 초소 (3x3, 기둥 + 민트 지붕) + 쌓인 짐 */
    private void booth(Placer p, int side, int lat0) {
        int H = LEN;
        for (int dl = 0; dl <= 2; dl++) {
            for (int t = 2; t <= 4; t++) {
                boolean post = (dl == 0 || dl == 2) && (t == 2 || t == 4);
                for (int ly = 1; ly <= 3; ly++) put(p, side, t, lat0 + dl, H + ly, post ? QUARTZ_PILLAR : AIR);
                put(p, side, t, lat0 + dl, H + 4, MINT_SLAB);
            }
        }
        put(p, side, 3, lat0 + 1, H + 3, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        put(p, side, 1, lat0, H + 1, Deco.BARREL);
        put(p, side, 1, lat0 + 2, H + 1, Blocks.HAY_BLOCK.defaultBlockState());
        put(p, side, 5, lat0 + 4, H + 1, Deco.CAMPFIRE);
        put(p, side, 5, lat0 - 2, H + 1, Blocks.GRINDSTONE.defaultBlockState());
        put(p, side, 1, lat0 + 4, H + 1, Deco.BARREL);
        put(p, side, 1, lat0 + 4, H + 2, Deco.BARREL);
        put(p, side, 3, lat0 + 5, H + 1, Blocks.HAY_BLOCK.defaultBlockState());
    }
}
