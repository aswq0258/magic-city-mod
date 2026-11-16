package io.github.aswq0258.magiccity.world;

/**
 * 왕국 대륙의 길 10개 (마스터플랜 도로 CSV, 도면 좌표 → 월드 x2).
 * 길은 지형 생성 때 바로 깔림 (KingdomTerrain.column → KingdomChunkGenerator).
 * 곧은 선분이 어색하지 않게 좌표를 노이즈로 살짝 비틀어 길이 구불구불하게 보이도록 함.
 */
public final class KingdomRoads {
    private KingdomRoads() {}

    /** 1 = 왕도/교역로 (폭 7, 돌길), 2 = 숲길/산악길 (폭 5, 흙길), 3 = 봉쇄된 옛길 (폭 5, 부서진 돌길) */
    public record Road(String name, int type, int[][] pts, int minX, int minZ, int maxX, int maxZ, double length) {
        public double halfWidth() {
            return type == 1 ? 3.2 : 2.3;
        }
    }

    private static final Object[][] PLAN = {
            {"북부 왕도", 1, new int[][]{{260, 260}, {260, 0}, {260, -170}, {220, -400}, {240, -670}, {190, -970}, {140, -1250}, {150, -1590}, {80, -1920}, {100, -2150}}},
            {"서부 숲길", 2, new int[][]{{260, 260}, {0, 260}, {-270, 190}, {-440, 80}, {-660, -230}, {-920, -560}}},
            {"엘프 산악길", 2, new int[][]{{-920, -560}, {-1190, -810}, {-1500, -980}, {-1670, -1090}}},
            {"엘프-북부 고갯길", 2, new int[][]{{-920, -560}, {-600, -800}, {-350, -1040}, {70, -1270}, {140, -1250}}},
            {"동부 교역로", 1, new int[][]{{260, 260}, {520, 260}, {660, 260}, {930, 250}, {1140, 440}, {1310, 700}, {1490, 900}}},
            {"남부 왕도", 1, new int[][]{{260, 260}, {260, 520}, {270, 760}, {170, 980}, {0, 1240}, {-200, 1670}}},
            {"강변길", 1, new int[][]{{660, 260}, {760, -10}, {950, -290}, {1300, -450}, {1200, -820}}},
            {"남동 교역 연결로", 1, new int[][]{{0, 1240}, {320, 1350}, {700, 1500}, {1100, 1550}, {1450, 1000}, {1490, 900}}},
            {"남서 국경로", 1, new int[][]{{0, 1240}, {-20, 1260}, {-430, 1110}, {-760, 1020}, {-1160, 1110}}},
            {"마왕성 폐왕도", 3, new int[][]{{-1160, 1110}, {-1450, 1350}, {-1650, 1530}, {-1850, 1760}}},
    };

    public static final Road[] ROADS = new Road[PLAN.length];

    static {
        for (int i = 0; i < PLAN.length; i++) {
            int[][] plan = (int[][]) PLAN[i][2];
            int[][] w = new int[plan.length][];
            int x0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
            double len = 0;
            for (int k = 0; k < plan.length; k++) {
                w[k] = new int[]{KingdomTerrain.worldX(plan[k][0]), KingdomTerrain.worldZ(plan[k][1])};
                x0 = Math.min(x0, w[k][0]);
                z0 = Math.min(z0, w[k][1]);
                x1 = Math.max(x1, w[k][0]);
                z1 = Math.max(z1, w[k][1]);
                if (k > 0) len += Math.hypot(w[k][0] - w[k - 1][0], w[k][1] - w[k - 1][1]);
            }
            ROADS[i] = new Road((String) PLAN[i][0], (Integer) PLAN[i][1], w, x0 - 60, z0 - 60, x1 + 60, z1 + 60, len);
        }
    }

    /** 길을 구불구불하게 만드는 좌표 비틀기 */
    static double warpX(double x, double z) {
        return x + 60 * (KingdomTerrain.fbm(x / 320.0, z / 320.0, 2, 51) - 0.5);
    }

    static double warpZ(double x, double z) {
        return z + 60 * (KingdomTerrain.fbm(x / 320.0, z / 320.0, 2, 52) - 0.5);
    }

    /** 길 중심선까지의 거리 (비틀린 좌표 기준) */
    public static double dist(Road r, double x, double z) {
        if (x < r.minX || x > r.maxX || z < r.minZ || z > r.maxZ) return 1e9;
        return warpedDist(r, warpX(x, z), warpZ(x, z));
    }

    private static double warpedDist(Road r, double wx, double wz) {
        double best = 1e9;
        int[][] p = r.pts;
        for (int i = 0; i + 1 < p.length; i++) {
            double vx = p[i + 1][0] - p[i][0], vz = p[i + 1][1] - p[i][1];
            double t = Math.max(0, Math.min(1, ((wx - p[i][0]) * vx + (wz - p[i][1]) * vz) / (vx * vx + vz * vz)));
            best = Math.min(best, Math.hypot(wx - p[i][0] - vx * t, wz - p[i][1] - vz * t));
        }
        return best;
    }

    /** 길을 깔지 않는 곳: 왕성도시·신시가지 건설 범위 (도시가 직접 거리를 깖), 옛 왕도 성 안 */
    public static boolean excluded(double x, double z) {
        if (Math.abs(x - KingdomTerrain.CX) <= 622 && Math.abs(z - KingdomTerrain.CZ) <= 622) return true;   // 왕성도시 + 신시가지
        return Math.hypot(x - KingdomTerrain.OLD_X, z - KingdomTerrain.OLD_Z) < 172;
    }

    /** 가장 가까운 길 번호 (maxDist 안에 없으면 -1). out[0] 에 (거리 - 반폭) */
    public static int nearest(double x, double z, double maxDist, double[] out) {
        int best = -1;
        double bd = 1e9, wx = Double.NaN, wz = 0;
        for (int i = 0; i < ROADS.length; i++) {
            Road r = ROADS[i];
            if (x < r.minX || x > r.maxX || z < r.minZ || z > r.maxZ) continue;
            if (Double.isNaN(wx)) {
                wx = warpX(x, z);
                wz = warpZ(x, z);
            }
            double d = warpedDist(r, wx, wz) - r.halfWidth();
            if (d < bd) {
                bd = d;
                best = i;
            }
        }
        if (bd > maxDist) return -1;
        if (out != null) out[0] = bd;
        return best;
    }

    /** 비틀기 전 꺾은선 위 비율 위치의 점과 진행 방향 {x, z, dirX, dirZ} */
    public static double[] pointAt(Road r, double frac) {
        double target = r.length * Math.max(0, Math.min(1, frac)), acc = 0;
        int[][] p = r.pts;
        for (int i = 0; i + 1 < p.length; i++) {
            double seg = Math.hypot(p[i + 1][0] - p[i][0], p[i + 1][1] - p[i][1]);
            if (acc + seg >= target || i + 2 == p.length) {
                double t = seg == 0 ? 0 : Math.min(1, (target - acc) / seg);
                double dx = (p[i + 1][0] - p[i][0]) / seg, dz = (p[i + 1][1] - p[i][1]) / seg;
                return new double[]{p[i][0] + (p[i + 1][0] - p[i][0]) * t, p[i][1] + (p[i + 1][1] - p[i][1]) * t, dx, dz};
            }
            acc += seg;
        }
        return new double[]{p[0][0], p[0][1], 1, 0};
    }
}
