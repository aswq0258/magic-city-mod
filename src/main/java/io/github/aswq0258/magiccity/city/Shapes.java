package io.github.aswq0258.magiccity.city;

import io.github.aswq0258.magiccity.build.Placer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.neoforge.event.EventHooks;
import org.jetbrains.annotations.Nullable;

import static io.github.aswq0258.magiccity.city.Palette.*;

/** 여러 건물이 함께 쓰는 형태들 (원형 탑, 지붕, 나무, 분수, 가로등 ...) */
public final class Shapes {
    private Shapes() {}

    /** 0 ~ 2π 각도 */
    public static double angle(double dx, double dz) {
        double a = Math.atan2(dz, dx);
        return a < 0 ? a + Math.PI * 2 : a;
    }

    /** 좌표 기반 결정적 난수 (청크마다 따로 지어도 같은 결과) */
    public static int hash(int x, int y, int z, long seed) {
        long h = Mth.getSeed(x, y, z) ^ seed;
        h *= 0x9E3779B97F4A7C15L;
        h ^= (h >>> 29);
        return (int) (h >>> 32) & 0x7FFFFFFF;
    }

    public static void lampPost(Placer p, int x, int z, int b) {
        p.set(x, b + 1, z, QUARTZ_PILLAR);
        p.set(x, b + 2, z, QUARTZ_PILLAR);
        p.set(x, b + 3, z, QUARTZ_PILLAR);
        p.set(x, b + 4, z, LIGHT);
        p.set(x, b + 5, z, MINT_SLAB);
    }

    /**
     * 원형 탑: 석영 벽돌 벽 + 방해석 띠 + 하늘색 창 + 민트 구리 원뿔 지붕.
     * door 가 있으면 그 방향에 문을 내고, 반대편 벽에 꼭대기까지 사다리를 설치합니다.
     */
    public static void roundTower(Placer p, int tx, int tz, double rad, int b, int height, @Nullable Direction door) {
        int ext = (int) Math.ceil(rad) + 2;
        int coneH = (int) Math.round(rad * 2.2) + 3;
        int rr = (int) Math.round(rad);
        int ladderWallX = door == null ? Integer.MIN_VALUE : tx - door.getStepX() * rr;
        int ladderWallZ = door == null ? Integer.MIN_VALUE : tz - door.getStepZ() * rr;

        p.forColumns(tx - ext, tz - ext, tx + ext, tz + ext, (x, z) -> {
            int dx = x - tx, dz = z - tz;
            double d = Math.sqrt(dx * dx + dz * dz);
            int arc = (int) Math.floor(angle(dx, dz) * rad);
            if (d <= rad + 0.5) {
                boolean wall = d > rad - 0.5;
                boolean doorCol = door != null && wall && dx == door.getStepX() * rr && dz == door.getStepZ() * rr;
                boolean ladderWall = x == ladderWallX && z == ladderWallZ;
                p.set(x, b, z, SMOOTH_QUARTZ);
                for (int ly = 1; ly <= height; ly++) {
                    var s = AIR;
                    if (wall) {
                        if (ly == height) s = MINT_CUT;
                        else if (ly % 6 == 3 && arc % 4 == 0 && !ladderWall) s = GLASS_PANE;
                        else s = (ly % 6 == 0) ? CALCITE : QUARTZ_BRICKS;
                        if (doorCol && ly <= 2) s = Palette.door(door.getOpposite(), ly == 2);
                    } else if (ly % 6 == 0) {
                        s = d < 0.5 ? LIGHT : SMOOTH_QUARTZ;
                    }
                    p.set(x, b + ly, z, s);
                }
            }
            for (int k = 0; k < coneH; k++) {
                double cr = (rad + 1.2) * (1.0 - (double) k / coneH);
                if (d <= cr + 0.01 && d > cr - 1.6) {
                    p.set(x, b + height + 1 + k, z, (k % 4 == 3) ? MINT_CHISELED : MINT_CUT);
                }
            }
        });
        p.set(tx, b + height + 1 + coneH, tz, END_ROD);

        if (door != null && rr >= 2) {
            int lx = tx - door.getStepX() * (rr - 1);
            int lz = tz - door.getStepZ() * (rr - 1);
            for (int ly = 1; ly < height; ly++) {
                p.set(lx, b + ly, lz, ladder(door));
            }
        }
    }

    /** 사각형 위에 민트 구리 계단으로 모임지붕(사방 경사)을 얹음 */
    public static void hipRoof(Placer p, int x0, int z0, int x1, int z1, int y) {
        for (int k = 0; ; k++) {
            int ax0 = x0 + k, az0 = z0 + k, ax1 = x1 - k, az1 = z1 - k;
            int w = ax1 - ax0 + 1, d = az1 - az0 + 1;
            if (w <= 0 || d <= 0) return;
            int yy = y + k;
            if (w <= 2 || d <= 2) {
                for (int x = ax0; x <= ax1; x++) {
                    for (int z = az0; z <= az1; z++) {
                        p.set(x, yy, z, (w == 1 || d == 1) ? MINT_SLAB : MINT_CUT);
                    }
                }
                return;
            }
            for (int x = ax0; x <= ax1; x++) {
                p.set(x, yy, az0, mintStairs(Direction.SOUTH));
                p.set(x, yy, az1, mintStairs(Direction.NORTH));
            }
            for (int z = az0 + 1; z < az1; z++) {
                p.set(ax0, yy, z, mintStairs(Direction.EAST));
                p.set(ax1, yy, z, mintStairs(Direction.WEST));
            }
        }
    }

    /** 공중에 떠 있는 마법 수정 (하늘색 유리 껍질 + 바다 랜턴 심) */
    public static void crystal(Placer p, int x0, int y0, int z0, int n) {
        p.forColumns(x0 - n, z0 - n, x0 + n, z0 + n, (x, z) -> {
            int h = Math.abs(x - x0) + Math.abs(z - z0);
            for (int dy = -n; dy <= n; dy++) {
                int s = h + Math.abs(dy);
                if (s > n) continue;
                p.set(x, y0 + dy, z, s >= n - 1 ? GLASS : LIGHT);
            }
        });
    }

    /** 흰 자작나무 (잎은 사라지지 않게 persistent) */
    public static void birchTree(Placer p, int tx, int tz, int b, int h) {
        p.forColumns(tx - 2, tz - 2, tx + 2, tz + 2, (x, z) -> {
            int dx = Math.abs(x - tx), dz = Math.abs(z - tz);
            for (int y = b + h - 2; y <= b + h + 1; y++) {
                boolean lower = y < b + h;
                boolean inShape = lower ? !(dx == 2 && dz == 2) : (dx + dz <= 1);
                if (!inShape) continue;
                if (dx == 0 && dz == 0 && y <= b + h) continue;
                p.set(x, y, z, BIRCH_LEAVES);
            }
        });
        for (int y = b + 1; y <= b + h; y++) p.set(tx, y, tz, BIRCH_LOG);
    }

    /** 원형 분수 (지름 11) */
    public static void fountain(Placer p, int fx, int fz, int b) {
        p.forColumns(fx - 5, fz - 5, fx + 5, fz + 5, (x, z) -> {
            int dx = x - fx, dz = z - fz;
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d <= 3.5) {
                p.set(x, b, z, CALCITE);
                if (dx != 0 || dz != 0) p.set(x, b + 1, z, WATER);
            } else if (d <= 4.5) {
                p.set(x, b, z, SMOOTH_QUARTZ);
                p.set(x, b + 1, z, QUARTZ_BRICKS);
            } else if (d <= 5.5) {
                p.set(x, b, z, MINT_CUT);
            }
        });
        for (int y = b + 1; y <= b + 3; y++) p.set(fx, y, fz, QUARTZ_PILLAR);
        p.set(fx, b + 4, fz, LIGHT);
        p.set(fx, b + 5, fz, MINT_CHISELED);
        p.set(fx, b + 6, fz, WATER); // 꼭대기에서 흘러내리는 물
    }

    public static void spawnVillager(ServerLevel level, double x, double y, double z) {
        Villager v = EntityType.VILLAGER.create(level);
        if (v == null) return;
        BlockPos pos = BlockPos.containing(x, y, z);
        v.moveTo(x, y, z, level.random.nextFloat() * 360.0F, 0.0F);
        EventHooks.finalizeMobSpawn(v, level, level.getCurrentDifficultyAt(pos), MobSpawnType.STRUCTURE, null);
        level.addFreshEntity(v);
    }
}
