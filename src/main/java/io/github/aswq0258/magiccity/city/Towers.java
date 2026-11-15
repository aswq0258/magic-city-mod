package io.github.aswq0258.magiccity.city;

import io.github.aswq0258.magiccity.build.Placer;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import static io.github.aswq0258.magiccity.city.Palette.*;

/**
 * 원형 탑 내부: 가운데 빛기둥(유리 난간으로 둘러쌈) + 그 둘레를 도는 나선 계단 + 층마다 꾸민 방.
 *
 *   위에서 본 모습 (s = 2)
 *     . . . . . . .      S = 나선 계단 (5x5 테두리 16칸)
 *     . S S S S S .      G = 유리 난간
 *     . S G G G S .      C = 빛기둥 (석영 기둥 + 바다 랜턴)
 *     . S G C G S .      . = 방 (벽 쪽에는 가구, 안쪽은 카펫 통로)
 *     . S G G G S .
 *     . S S S S S .
 */
public final class Towers {
    private Towers() {}

    public enum Room { ENTRANCE, LIBRARY, ALCHEMY, STUDY }

    public static Room roomFor(int floor) {
        if (floor == 0) return Room.ENTRANCE;
        return switch ((floor - 1) % 3) {
            case 0 -> Room.LIBRARY;
            case 1 -> Room.ALCHEMY;
            default -> Room.STUDY;
        };
    }

    /** 반지름 s 정사각 테두리 칸들을 시계 방향 순서로 (연속한 칸은 항상 상하좌우로 맞닿음) */
    static int[][] ringCells(int s) {
        int[][] cells = new int[8 * s][];
        int i = 0;
        for (int z = -s + 1; z <= s; z++) cells[i++] = new int[]{s, z};
        for (int x = s - 1; x >= -s; x--) cells[i++] = new int[]{x, s};
        for (int z = s - 1; z >= -s; z--) cells[i++] = new int[]{-s, z};
        for (int x = -s + 1; x <= s; x++) cells[i++] = new int[]{x, -s};
        return cells;
    }

    /**
     * 탑 내부 골격: 층 바닥, 빛기둥, 유리 난간. (벽은 호출하는 쪽에서 따로 지음)
     * @param innerR 벽 안쪽 반지름 (이 거리 이하가 실내)
     * @param s      나선 계단 테두리 반지름 (2 또는 3)
     */
    public static void interior(Placer p, int cx, int cz, int b, double innerR, int s, int fh, int topLy,
                                BlockState floor, boolean quartzStairs) {
        int ext = (int) Math.ceil(innerR);
        p.forColumns(cx - ext, cz - ext, cx + ext, cz + ext, (x, z) -> {
            int dx = x - cx, dz = z - cz;
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d > innerR) return;
            int cheb = Math.max(Math.abs(dx), Math.abs(dz));
            p.set(x, b, z, cheb < s - 1 ? QUARTZ_PILLAR : floor);
            for (int ly = 1; ly <= topLy; ly++) {
                BlockState st;
                if (cheb < s - 1) {
                    st = (ly % fh == fh / 2) ? LIGHT : QUARTZ_PILLAR;
                } else if (cheb == s - 1) {
                    st = ly < topLy ? GLASS_PANE : SMOOTH_QUARTZ;
                } else if (cheb == s) {
                    st = AIR;
                } else {
                    st = (ly % fh == 0) ? floor : AIR;
                }
                p.set(x, b + ly, z, st);
            }
        });

        int[][] ring = ringCells(s);
        for (int i = 0; i < topLy; i++) {
            int[] c = ring[i % ring.length];
            int[] n = ring[(i + 1) % ring.length];
            Direction up = Deco.toward(n[0] - c[0], n[1] - c[1]);
            p.set(cx + c[0], b + i + 1, cz + c[1], quartzStairs ? Deco.quartzStairs(up) : Deco.woodStairs(up));
        }
    }

    /**
     * 한 층 꾸미기. 벽에 붙은 칸에는 방 종류별 가구, 나머지 칸에는 카펫, 천장에는 샹들리에.
     * @param door      1층 출입문 방향 (그 앞은 비움). null 이면 없음
     * @param clearAxes true 면 네 방향 축(문/발코니 통로)을 모두 비움
     */
    public static void furnish(Placer p, int cx, int cz, int floorY, double innerR, int s, int fh, Room room,
                               long seed, @Nullable Direction door, boolean clearAxes, boolean ceiling) {
        BlockState carpet = (room == Room.LIBRARY || room == Room.STUDY) ? Deco.CARPET_WHITE : Deco.CARPET_MINT;
        int ext = (int) Math.ceil(innerR);
        int y = floorY + 1;

        p.forColumns(cx - ext, cz - ext, cx + ext, cz + ext, (x, z) -> {
            int dx = x - cx, dz = z - cz;
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d > innerR) return;
            int cheb = Math.max(Math.abs(dx), Math.abs(dz));
            if (cheb <= s) return;

            boolean onAxis = Math.min(Math.abs(dx), Math.abs(dz)) <= 1;
            boolean doorSide = door != null && onAxis
                    && (door.getStepX() != 0 ? Integer.signum(dx) == door.getStepX() && Math.abs(dz) <= 1
                                             : Integer.signum(dz) == door.getStepZ() && Math.abs(dx) <= 1);
            boolean keepClear = doorSide || (clearAxes && onAxis);
            boolean wallSide = d > innerR - 1.0 && cheb >= s + 2;

            if (!wallSide || keepClear) {
                p.set(x, y, z, carpet);
                return;
            }

            int h = Shapes.hash(x, floorY, z, seed);
            Direction in = Deco.toward(-dx, -dz);
            switch (room) {
                case ENTRANCE -> {
                    switch (h % 3) {
                        case 0 -> { p.set(x, y, z, BOOKSHELF); p.set(x, y + 1, z, BOOKSHELF); p.set(x, y + 2, z, Deco.candles(1 + h % 3)); }
                        case 1 -> p.set(x, y, z, Deco.pot(h));
                        default -> { p.set(x, y, z, Deco.BARREL); p.set(x, y + 1, z, Deco.pot(h >> 3)); }
                    }
                }
                case LIBRARY -> {
                    if (h % 6 == 0) {
                        p.set(x, y, z, Deco.lectern(in));
                    } else {
                        for (int i = 0; i < 3; i++) p.set(x, y + i, z, BOOKSHELF);
                    }
                }
                case ALCHEMY -> {
                    switch (h % 5) {
                        case 0 -> p.set(x, y, z, Deco.BREWING);
                        case 1 -> p.set(x, y, z, Deco.WATER_CAULDRON);
                        case 2 -> { p.set(x, y, z, Deco.BARREL); p.set(x, y + 1, z, Deco.candles(3)); }
                        case 3 -> { p.set(x, y, z, BOOKSHELF); p.set(x, y + 1, z, BOOKSHELF); }
                        default -> p.set(x, y, z, Deco.pot(h >> 3));
                    }
                }
                case STUDY -> {
                    switch (h % 5) {
                        case 0 -> p.set(x, y, z, ENCHANTING_TABLE);
                        case 1 -> { p.set(x, y, z, BOOKSHELF); p.set(x, y + 1, z, BOOKSHELF); p.set(x, y + 2, z, Deco.candles(2)); }
                        case 2 -> p.set(x, y, z, Deco.chest(in));
                        case 3 -> Deco.table(p, x, y, z);
                        default -> p.set(x, y, z, Deco.pot(h >> 3));
                    }
                }
            }
        });

        if (ceiling) {
            int chain = Math.max(0, fh - 5);
            int c = s + 1;
            int[][] spots = {{c, c}, {-c, c}, {c, -c}, {-c, -c}};
            for (int[] sp : spots) Deco.chandelier(p, cx + sp[0], floorY + fh, cz + sp[1], chain);
            if (innerR >= 9) {
                int r = (int) Math.floor(innerR) - 3;
                int[][] more = {{r, 0}, {-r, 0}, {0, r}, {0, -r}};
                for (int[] sp : more) Deco.chandelier(p, cx + sp[0], floorY + fh, cz + sp[1], chain);
            }
        }
    }
}
