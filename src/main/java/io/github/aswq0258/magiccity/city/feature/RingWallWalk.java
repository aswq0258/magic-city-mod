package io.github.aswq0258.magiccity.city.feature;

import io.github.aswq0258.magiccity.build.Placer;
import io.github.aswq0258.magiccity.city.CityLayout;
import io.github.aswq0258.magiccity.city.Deco;
import io.github.aswq0258.magiccity.city.Royal;
import io.github.aswq0258.magiccity.city.Shapes;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;

import static io.github.aswq0258.magiccity.city.Palette.*;

/**
 * 사람이 다니는 원형 외성벽: 안쪽에서 성벽 위로 곧게 올라가는 계단 8개, 계단 꼭대기 초소,
 * 바깥 민트 깃발. (보행로의 닳은 바닥·랜턴은 CityLayout.wallColumn)
 */
public class RingWallWalk extends Feature {
    public static final int[] STAIR_ANGLES = {15, 75, 105, 165, 195, 255, 285, 345};
    private final CityLayout c;
    private final int rIn, rOut, H;
    private final long seed;

    public RingWallWalk(CityLayout city, int rIn, int rOut, int height, long seed) {
        super(city.cx - rOut - 2, city.cz - rOut - 2, city.cx + rOut + 2, city.cz + rOut + 2);
        this.c = city;
        this.rIn = rIn;
        this.rOut = rOut;
        this.H = height;
        this.seed = seed;
    }

    private int wx(double along, double lat, double a) {
        return c.cx + (int) Math.round(along * Math.cos(a) - lat * Math.sin(a));
    }

    private int wz(double along, double lat, double a) {
        return c.cz - (int) Math.round(along * Math.sin(a) + lat * Math.cos(a));
    }

    @Override
    public boolean occupies(int x, int z) {
        int du = x - c.cx, dn = c.cz - z;
        for (int deg : STAIR_ANGLES) {
            double a = Math.toRadians(deg);
            double along = du * Math.cos(a) + dn * Math.sin(a), lat = -du * Math.sin(a) + dn * Math.cos(a);
            if (Math.abs(lat) <= 3 && along >= rIn - H - 3 && along <= rIn) return true;
        }
        return false;
    }

    @Override
    public boolean needsSpawn() {
        return false;
    }

    @Override
    public void build(Placer p, CityLayout city) {
        final int b = groundY;
        for (int deg : STAIR_ANGLES) {
            double a = Math.toRadians(deg);
            int x0 = wx(rIn - H - 3, -4, a), x1 = wx(rOut, 8, a), z0 = wz(rIn - H - 3, -4, a), z1 = wz(rOut, 8, a);
            if (!p.intersects(Math.min(x0, x1) - 8, Math.min(z0, z1) - 8, Math.max(x0, x1) + 8, Math.max(z0, z1) + 8)) continue;
            Direction out = Deco.toward(Math.cos(a), -Math.sin(a));
            p.forColumns(Math.min(x0, x1) - 8, Math.min(z0, z1) - 8, Math.max(x0, x1) + 8, Math.max(z0, z1) + 8, (x, z) -> {
                int du = x - c.cx, dn = c.cz - z;
                double along = du * Math.cos(a) + dn * Math.sin(a), lat = -du * Math.sin(a) + dn * Math.cos(a);
                if (along < rIn - H - 3 || along > rIn + 0.5) return;
                double al = Math.abs(lat);
                if (al <= 1.5) {
                    int h = (int) Math.round(along - (rIn - H - 1));
                    if (h < 1) {
                        p.set(x, b, z, Shapes.hash(x, 3, z, seed) % 2 == 0 ? Blocks.DIRT_PATH.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState());
                        return;
                    }
                    h = Math.min(h, H);
                    for (int y = b + 1; y < b + h; y++) p.set(x, y, z, QUARTZ_BRICKS);
                    p.set(x, b + h, z, h >= H ? SMOOTH_QUARTZ : Royal.whiteStairs(out));
                    for (int y = b + h + 1; y <= b + h + 3; y++) p.set(x, y, z, AIR);
                } else if (al <= 2.5) {
                    int h = (int) Math.round(along - (rIn - H - 1));
                    if (h < 1) return;
                    h = Math.min(h, H);
                    for (int y = b + 1; y <= b + h; y++) p.set(x, y, z, QUARTZ_BRICKS);
                    p.set(x, b + h + 1, z, (h % 6 == 3) ? Blocks.LANTERN.defaultBlockState() : SMOOTH_QUARTZ_SLAB);
                }
            });
            // 성벽 위로 넘어가는 자리: 안쪽 난간을 터 줌
            for (int lat = -2; lat <= 2; lat++) p.set(wx(rIn, lat, a), b + H + 1, wz(rIn, lat, a), AIR);
            // 초소 (계단 꼭대기 옆, 성벽 보행로 위)
            double mid = (rIn + rOut) / 2.0;
            for (int dl = 4; dl <= 6; dl++) {
                for (int dr = -1; dr <= 1; dr++) {
                    boolean post = (dl == 4 || dl == 6) && dr != 0;
                    int x = wx(mid + dr, dl, a), z = wz(mid + dr, dl, a);
                    for (int ly = 1; ly <= 3; ly++) p.set(x, b + H + ly, z, post ? QUARTZ_PILLAR : AIR);
                    p.set(x, b + H + 4, z, MINT_SLAB);
                }
            }
            p.set(wx(mid, 5, a), b + H + 3, wz(mid, 5, a), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
            p.set(wx(mid, 8, a), b + H + 1, wz(mid, 8, a), Deco.BARREL);
            p.set(wx(mid + 1, 9, a), b + H + 1, wz(mid + 1, 9, a), Blocks.HAY_BLOCK.defaultBlockState());
            p.set(wx(mid - 1, 10, a), b + H + 1, wz(mid - 1, 10, a), Deco.CAMPFIRE);
            p.set(wx(mid, -4, a), b + H + 1, wz(mid, -4, a), Blocks.GRINDSTONE.defaultBlockState());
        }
        // 바깥 민트 깃발 (6도 간격, 성문·계단 자리 제외)
        for (int deg = 3; deg < 360; deg += 6) {
            if (Math.abs(((deg + 45) % 90) - 45) < 5) continue;          // 성문(동서남북) 근처
            double a = Math.toRadians(deg);
            int x = wx(rOut + 1, 0, a), z = wz(rOut + 1, 0, a);
            if (!p.contains(x, z)) continue;
            Direction out = Deco.toward(Math.cos(a), -Math.sin(a));
            p.set(x, b + H - 5, z, Royal.mintBanner(out));
        }
    }
}
