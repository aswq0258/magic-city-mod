package io.github.aswq0258.magiccity.city.feature;

import io.github.aswq0258.magiccity.build.Placer;
import io.github.aswq0258.magiccity.city.CityLayout;
import io.github.aswq0258.magiccity.city.Fantasy;
import io.github.aswq0258.magiccity.city.Royal;
import io.github.aswq0258.magiccity.city.Shapes;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

import static io.github.aswq0258.magiccity.city.Palette.*;

/**
 * 왕립 성채: 동화 속 성처럼 둥근 성벽이 세 겹으로 계단식으로 솟은 독립 요새 (도시 중심 반지름 72 안)
 *
 *   1겹 성벽 r67~71 (높이 12) ─ 1단 정원 테라스 r58~66 (+8)
 *   2겹 성벽 r55~57 (높이 22) ─ 2단 꽃 테라스 r46~54 (+18)
 *   3겹 성벽 r43~45 (높이 32) ─ 꼭대기 궁전 대지 r<43 (+28): 궁전(Keep)·왕좌의 전당·보물고·근위대·왕실 정원
 *
 *  - 남쪽 대계단: 도시 순환로에서 세 겹 성문을 뚫고 꼭대기까지 곧게 오름 (단마다 넓은 계단참)
 *  - 성벽 탑은 크기·높이·간격을 일부러 제각각으로 (비대칭이지만 균형)
 *  - 왕실 정원 한가운데: 도시 결계의 중심 수정(떠 있는 수정 + 금빛 고리 + 빛기둥)
 * 단의 흙 채우기는 CityLayout.buildColumn 이 groundTop() 으로 합니다.
 */
public class Citadel extends Feature {
    public static final int R = 72;
    /** {안쪽 r, 바깥 r, 높이} */
    private static final int[][] RINGS = {{67, 71, 12}, {55, 57, 22}, {43, 45, 32}};
    /** 탑 {각도, 반지름(×10), 높이, 받침 높이} — 각도 0 = 동, 90 = 북 */
    private static final int[][] TOWERS = {
            {262, 40, 24, 0}, {278, 40, 24, 0}, {10, 50, 28, 0}, {55, 40, 22, 0}, {100, 60, 32, 0},
            {140, 40, 20, 0}, {185, 50, 26, 0}, {225, 40, 22, 0}, {315, 50, 30, 0}, {350, 35, 20, 0},
            {262, 30, 22, 8}, {278, 30, 22, 8}, {30, 40, 26, 8}, {115, 30, 20, 8}, {160, 50, 30, 8},
            {205, 30, 22, 8}, {335, 40, 24, 8},
            {261, 30, 20, 18}, {279, 30, 20, 18}, {70, 40, 24, 18}, {145, 30, 18, 18}, {195, 40, 26, 18}, {300, 30, 22, 18},
    };
    private static final int[] RING_R = {69, 56, 44};

    private final CityLayout c;
    private final long seed;

    public Citadel(CityLayout city, long seed) {
        super(city.cx - 90, city.cz - 90, city.cx + 90, city.cz + 90);
        this.c = city;
        this.seed = seed;
    }

    /** 이 반지름의 지면(단) 높이. 성벽 자리는 -1 */
    public static int groundTop(double r) {
        if (r <= 42.5) return 28;
        if (r <= 45.5) return -1;
        if (r <= 54.5) return 18;
        if (r <= 57.5) return -1;
        if (r <= 66.5) return 8;
        if (r <= 71.5) return -1;
        return 0;
    }

    /** 남쪽 대계단의 높이 (r 이 작아질수록 올라감) */
    public static int stairH(double r) {
        if (r >= 82) return 0;
        if (r >= 74) return (int) Math.round(82 - r);          // 0 → 8
        if (r >= 64) return 8;                                  // 1겹 성문 계단참
        if (r >= 54) return 8 + (int) Math.round(64 - r);       // 8 → 18
        if (r >= 50) return 18;                                 // 2단 계단참
        if (r >= 40) return 18 + (int) Math.round(50 - r);      // 18 → 28
        return 28;
    }

    public static boolean onStair(int du, int dn) {
        return Math.abs(du) <= 3 && dn < 0 && Math.hypot(du, dn) <= 84 && Math.hypot(du, dn) >= 38;
    }

    @Override
    public boolean occupies(int x, int z) {
        return Math.hypot(x - c.cx, z - c.cz) <= 86;
    }

    @Override
    public void build(Placer p, CityLayout city) {
        final int b = groundY;
        p.forColumns(minX, minZ, maxX, maxZ, (x, z) -> {
            int du = x - c.cx, dn = c.cz - z;
            double r = Math.hypot(du, dn);
            if (r > 85) return;
            boolean stair = onStair(du, dn), stairSide = Math.abs(du) == 4 && dn < 0 && r <= 84 && r >= 38;
            int arc = (int) Math.floor(Shapes.angle(du, dn) * r);
            for (int k = 0; k < RINGS.length; k++) {
                int[] g = RINGS[k];
                if (r < g[0] - 0.5 || r > g[1] + 0.5) continue;
                boolean outer = r > g[1] - 0.5, inner = r < g[0] + 0.5;
                for (int ly = 1; ly <= g[2]; ly++) {
                    BlockState s = QUARTZ_BRICKS;
                    if (ly == g[2]) s = SMOOTH_QUARTZ;
                    else if (ly == g[2] - 3) s = k == 0 ? CHISELED_QUARTZ : Royal.GOLD;
                    else if (outer && ly % 6 == 3 && arc % 7 == 0) s = Royal.CLEAR_GLASS;
                    p.set(x, b + ly, z, s);
                }
                if (outer && Math.floorMod(arc, 3) != 1) p.set(x, b + g[2] + 1, z, CALCITE);
                if (inner) p.set(x, b + g[2] + 1, z, SMOOTH_QUARTZ_SLAB);
            }
            // 테라스 장식
            int top = groundTop(r);
            int h = Shapes.hash(x, 21, z, seed);
            if (!stair && !stairSide && top == 8) {
                if (Math.abs(r - 62) < 0.6) p.set(x, b + 8, z, CALCITE);                     // 산책로
                else if (Math.abs(r - 58.5) < 0.6) p.set(x, b + 9, z, Royal.HEDGE);           // 산울타리
                else if (h % 9 == 0) p.set(x, b + 9, z, Royal.ROYAL_FLOWERS[h % 4]);
            } else if (!stair && !stairSide && top == 18) {
                if (Math.abs(r - 50) < 0.6) p.set(x, b + 18, z, CALCITE);
                else if (h % 5 == 0) p.set(x, b + 19, z, FLOWERS[h % FLOWERS.length]);
            }
            // 대계단
            if (stair) stairColumn(p, x, z, r, b);
            else if (stairSide) {
                int sh = stairH(r);
                int t = Math.max(sh, 0);
                for (int y = b + 1; y <= b + t; y++) p.set(x, y, z, QUARTZ_BRICKS);
                if (sh > Math.max(groundTop(r), 0) || groundTop(r) < 0) p.set(x, b + t + 1, z, (Math.round(r) % 3 == 0) ? CHISELED_QUARTZ : SMOOTH_QUARTZ_SLAB);
                if (Math.round(r) % 8 == 0) Royal.lamp(p, x, z, b + t);
            }
        });
        // 성벽 탑 (제각각)
        for (int[] t : TOWERS) {
            int ring = t[3] == 0 ? 0 : t[3] == 8 ? 1 : 2;
            double a = Math.toRadians(t[0]);
            int tx = c.cx + (int) Math.round(Math.cos(a) * RING_R[ring]), tz = c.cz - (int) Math.round(Math.sin(a) * RING_R[ring]);
            Palace.palaceTower(p, tx, tz, t[1] / 10.0, b + t[3], t[2]);
        }
        // 성문 양옆 붉은 깃발
        for (int[] g : RINGS) {
            int gz = c.cz + g[1] + 1;
            p.set(c.cx - 5, b + g[2] - 2, gz, Royal.redBanner(Direction.SOUTH));
            p.set(c.cx + 5, b + g[2] - 2, gz, Royal.redBanner(Direction.SOUTH));
        }
        royalGarden(p, b + 28);
    }

    private void stairColumn(Placer p, int x, int z, double r, int b) {
        int h = stairH(r), hin = stairH(r - 1);
        for (int y = b + 1; y < b + h; y++) p.set(x, y, z, QUARTZ_BRICKS);
        BlockState s = hin > h ? Royal.whiteStairs(Direction.NORTH) : (Math.abs(x - c.cx) == 0 ? CALCITE : SMOOTH_QUARTZ);
        if (h > 0) p.set(x, b + h, z, s);
        else if (hin > 0) p.set(x, b + 1, z, Royal.whiteStairs(Direction.NORTH));
        // 위는 비움 (성벽은 아치로, 대지는 계단 홈으로)
        int wallTop = 0;
        for (int[] g : RINGS) if (r >= g[0] - 0.5 && r <= g[1] + 0.5) wallTop = g[2] + 1;
        int clearTo = wallTop > 0 ? Math.min(h + 5, wallTop) : Math.max(groundTop(r), h);
        for (int y = b + h + 1; y <= b + clearTo; y++) p.set(x, y, z, AIR);
        if (wallTop > 0 && h + 6 <= wallTop - 1) p.set(x, b + h + 6, z, Math.abs(x - c.cx) == 0 ? Royal.GOLD : CHISELED_QUARTZ);
        if (Math.abs(x - c.cx) <= 1 && h > 0 && hin == h) p.set(x, b + h + 1, z, Royal.RED_CARPET);
    }

    /** 왕실 정원 (꼭대기 대지 남동쪽): 산울타리 화단, 분수, 결계의 중심 수정 */
    private void royalGarden(Placer p, int y) {
        int gx0 = c.cx + 8, gx1 = c.cx + 28, gz0 = c.cz + 12, gz1 = c.cz + 30;   // 도면 u 8~28, n -30~-12
        int fx = (gx0 + gx1) / 2, fz = (gz0 + gz1) / 2;
        p.forColumns(gx0, gz0, gx1, gz1, (x, z) -> {
            boolean border = x == gx0 || x == gx1 || z == gz0 || z == gz1;
            boolean path = Math.abs(x - fx) <= 1 || Math.abs(z - fz) <= 1;
            int h = Shapes.hash(x, 23, z, seed);
            if (path) {
                p.set(x, y, z, CALCITE);
                return;
            }
            p.set(x, y, z, GRASS);
            if (border && ((x + z) & 3) != 0) p.set(x, y + 1, z, Royal.HEDGE);
            else if (!border) p.set(x, y + 1, z, h % 3 == 0 ? Royal.HEDGE_FLOWER : Royal.ROYAL_FLOWERS[h % 4]);
        });
        Shapes.fountain(p, fx, fz, y);
        // 결계의 중심: 떠 있는 수정 + 금빛 고리 + 둘레 빛기둥
        Shapes.crystal(p, fx, y + 14, fz, 3);
        Fantasy.halo(p, fx, fz, y + 14, 6.5);
        Fantasy.halo(p, fx, fz, y + 10, 4.5);
        for (int[] o : new int[][]{{-7, -7}, {7, -7}, {-7, 7}, {7, 7}}) {
            p.set(fx + o[0], y + 1, fz + o[1], Fantasy.AMETHYST);
            p.set(fx + o[0], y + 2, fz + o[1], END_ROD);
        }
        for (int dx : new int[]{-9, 9}) Royal.statue(p, fx + dx, fz, y, 3);
    }
}
