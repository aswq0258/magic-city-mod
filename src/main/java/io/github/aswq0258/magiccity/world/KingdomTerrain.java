package io.github.aswq0258.magiccity.world;

/**
 * 왕국 대륙 지형 — 사용자 마스터플랜(도면 좌표)을 왕성 중심 (260, 260) 기준으로 2배 넓혀 배치 (북쪽 = -Z)
 *
 * 도면 좌표 P 의 실제 위치 = 260 + (P - 260) x 2. 지역 사이의 "길" 만 길어지고, 각 지역(왕성도시 평지,
 * 옛 왕도, 강폭, 산 봉우리의 굵기 같은 세부) 의 크기는 그대로 유지.
 *
 *  - 중앙 왕성도시 (260, 260): 반지름 430 안은 Y=63 평지 → /magiccity build
 *  - 북방 엘프 산맥: 도면 Z -1500 ~ -850 의 두꺼운 띠. 북부 왕도·엘프-북부 고갯길·엘프 산악길을 따라 고갯길이 파임
 *    고룡 봉우리 G1 (500, -1210) 이 가장 높음, 북동 산괴(부유섬 유적 A1 주변), 북서 드워프 산괴 D1
 *  - 혹한 설원: 도면 Z 약 -1480 북쪽 + 북동쪽 빙원 (650, -1750), 빙해 (Z -2150 북쪽)
 *  - 서쪽 엘프 대수림 (세계수 성역 E1 포함), 서부 숲 전초마을 F1 주변 숲
 *  - 남서: 재의 들판과 몰락한 옛 왕도 B1, 심연 균열 W2, 저주받은 숲 W1
 *  - 남동 습지, 남쪽 농경평야
 *  - 동: 큰 강(고룡 봉우리 → 왕성 동쪽 → 남동 삼각주) + 부유섬 유적에서 내려오는 지류,
 *    동해안은 항구도시 P1 (1490, 900) 앞에서 만으로 들어오고, 앞바다에 등대 섬 P2
 */
public final class KingdomTerrain {
    private KingdomTerrain() {}

    public static final int CX = 260, CZ = 260, SEA = 63, CITY_Y = 63, FLAT_R = 430;
    /** 도면 → 월드 배율 (지역 사이 거리만 늘어남) */
    public static final double SCALE = 2.0;

    /** 도면 좌표 → 실제 월드 좌표 */
    public static int worldX(double planX) {
        return (int) Math.round(CX + (planX - CX) * SCALE);
    }

    public static int worldZ(double planZ) {
        return (int) Math.round(CZ + (planZ - CZ) * SCALE);
    }

    /** 몰락한 옛 왕도 (도면 B1 (-1850, 1760) → 월드 (-3960, 3260)), 봉쇄 요새는 폐왕도 위 330블록 앞 */
    public static final int OLD_X = -3960, OLD_Z = 3260, OLD_Y = 70, OLD_R = 190, FORT_X = -3744, FORT_Z = 3011;

    public enum Kind { OCEAN, DEEP_OCEAN, FROZEN_OCEAN, BEACH, RIVER, PLAINS, FARMLAND, FOREST, ELF_FOREST, CURSED, SNOW, MEADOW, PEAKS, DWARF, WASTELAND, SWAMP, ASH, CORRUPT }

    /**
     * 한 기둥: 지면 높이, 물 높이(없으면 Integer.MIN_VALUE), 종류, 가파름,
     * 가까운 길 번호(KingdomRoads.ROADS 인덱스 + 1, 근처에 길이 없으면 0) 와 길 가장자리까지 거리(0 이하 = 길 위)
     */
    public record Column(int h, int water, Kind kind, boolean steep, int road, float roadEdge) {
        public boolean onRoad() {
            return road > 0 && roadEdge <= 0;
        }
    }

    // ───────────────────────── 노이즈 ─────────────────────────

    private static double rnd(int x, int z, long salt) {
        long h = x * 341873128712L + z * 132897987541L + salt * 0x9E3779B97F4A7C15L;
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        h *= 0xc4ceb9fe1a85ec53L;
        h ^= h >>> 33;
        return (h >>> 11) * (1.0 / (1L << 53));
    }

    private static double vnoise(double x, double z, long salt) {
        int x0 = (int) Math.floor(x), z0 = (int) Math.floor(z);
        double fx = x - x0, fz = z - z0, u = fx * fx * (3 - 2 * fx), v = fz * fz * (3 - 2 * fz);
        double a = rnd(x0, z0, salt), b = rnd(x0 + 1, z0, salt), c = rnd(x0, z0 + 1, salt), d = rnd(x0 + 1, z0 + 1, salt);
        return (a + (b - a) * u) * (1 - v) + (c + (d - c) * u) * v;
    }

    static double fbm(double x, double z, int oct, long salt) {
        double t = 0, amp = 1, n = 0;
        for (int i = 0; i < oct; i++) {
            t += amp * vnoise(x, z, salt + i * 17L);
            n += amp;
            amp *= 0.5;
            x *= 2.03;
            z *= 2.03;
        }
        return t / n;
    }

    static double ridged(double x, double z, int oct, long salt) {
        double t = 0, amp = 1, n = 0, w = 1;
        for (int i = 0; i < oct; i++) {
            double r = 1 - Math.abs(2 * vnoise(x, z, salt + i * 31L) - 1);
            r = r * r * w;
            w = Math.min(1, r * 1.6);
            t += amp * r;
            n += amp;
            amp *= 0.5;
            x *= 2.1;
            z *= 2.1;
        }
        return t / n;
    }

    static double smooth(double t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }

    /** 타원 안쪽 정도 (1 = 가운데, 0 = 경계 밖) — 도면 좌표, 경계는 노이즈로 들쭉날쭉 */
    private static double ellipse(double x, double z, double ex, double ez, double rx, double rz, long salt) {
        double dx = (x - ex) / rx, dz = (z - ez) / rz;
        double e = 1 - (dx * dx + dz * dz) + 0.35 * (fbm(x / 160.0, z / 160.0, 2, salt) - 0.5);
        return smooth(e * 2.2);
    }

    /** 점에서 꺾은선까지의 거리 */
    private static double polyDist(double x, double z, int[][] pts) {
        double best = 1e9;
        for (int i = 0; i + 1 < pts.length; i++) {
            int[] a = pts[i], b = pts[i + 1];
            double vx = b[0] - a[0], vz = b[1] - a[1], wx = x - a[0], wz = z - a[1];
            double t = Math.max(0, Math.min(1, (wx * vx + wz * vz) / (vx * vx + vz * vz)));
            best = Math.min(best, Math.hypot(x - a[0] - vx * t, z - a[1] - vz * t));
        }
        return best;
    }

    // ───────────────────────── 산길 (도면 좌표, 마스터플랜 도로 CSV) ─────────────────────────

    /** 산맥을 지나는 길: 이 선을 따라 고갯길이 파임 */
    private static final int[][][] MOUNTAIN_ROADS = {
            {{260, 0}, {260, -170}, {220, -400}, {240, -670}, {190, -970}, {140, -1250}, {150, -1590}, {80, -1920}, {100, -2150}},  // 북부 왕도
            {{-920, -560}, {-600, -800}, {-350, -1040}, {70, -1270}},                                                             // 엘프-북부 고갯길
            {{-920, -560}, {-1190, -810}, {-1500, -980}, {-1670, -1090}},                                                         // 엘프 산악길
    };

    private static double mountainRoadDist(double px, double pz) {
        double best = 1e9;
        for (int[][] r : MOUNTAIN_ROADS) best = Math.min(best, polyDist(px, pz, r));
        return best;
    }

    // ───────────────────────── 강 (월드 좌표) ─────────────────────────

    /** 큰 강: 고룡 봉우리 아래 → 왕성 동쪽(도시 설계와 같은 곡선) → 남동 하구 */
    private static final int[][] RIVER_N = {{680, -2580}, {780, -2060}, {880, -1460}, {940, -860}, {660, -420}, {596, -300}};
    private static final int[][] RIVER_S = {{596, 820}, {700, 1000}, {1060, 1340}, {1260, 1740}, {1540, 2100}, {1940, 2320}, {2440, 2360}, {2940, 2280}, {3940, 2240}};
    /** 삼각주 갈래 */
    private static final int[][] DELTA_A = {{2440, 2360}, {2900, 2560}, {3500, 2720}, {3900, 2760}};
    private static final int[][] DELTA_B = {{2440, 2360}, {2850, 2110}, {3400, 1960}, {3900, 1920}};
    /** 부유섬 유적(A1) 에서 흘러오는 지류 */
    private static final int[][] TRIBUTARY = {{2140, -1900}, {1720, -1520}, {1300, -1120}, {940, -860}};

    /** 왕성 근처에서는 도시 설계(CityLayout.riverX)와 정확히 같은 곡선 */
    private static double cityRiverX(int z) {
        int dn = CZ - z;
        return CX + 332 + 8 * Math.sin(dn / 45.0);
    }

    /** 강가까지의 거리 (0 이하 = 물 속). 강폭은 하류·삼각주로 갈수록 넓어짐 */
    public static double riverEdge(int x, int z) {
        double wob = 6 * (fbm(x / 70.0, z / 70.0, 2, 77) - 0.5);
        double best = 1e9;
        if (z >= -300 && z <= 820) best = Math.abs(x - cityRiverX(z)) - 9;
        else {
            best = Math.min(best, polyDist(x, z, RIVER_N) + wob - 9);
            double wide = 9 + 9 * smooth((x - 1800) / 900.0);
            best = Math.min(best, polyDist(x, z, RIVER_S) + wob - wide);
        }
        best = Math.min(best, polyDist(x, z, DELTA_A) + wob - 8);
        best = Math.min(best, polyDist(x, z, DELTA_B) + wob - 8);
        best = Math.min(best, polyDist(x, z, TRIBUTARY) + wob - 6);
        return best;
    }

    // ───────────────────────── 해안 (도면 좌표) ─────────────────────────

    /** 바다까지의 거리 (도면 단위, 양수 = 육지 안쪽, 음수 = 바다) */
    private static double seaDist(double x, double z) {
        double noisy = 1950 + 700 * (fbm(z / 420.0, 3.3, 4, 11) - 0.5) - 650 * smooth((-1750 - z) / 450.0);
        double bay = Math.exp(-Math.pow((z - 900) / 260.0, 2));                       // 항구도시 P1 앞 만
        double coastX = noisy * (1 - bay) + (1560 + 30 * (fbm(z / 60.0, 9.1, 2, 14) - 0.5)) * bay;
        double dEast = coastX - x;
        double northZ = -2150 + 160 * (fbm(x / 380.0, 7.7, 3, 12) - 0.5);
        double dNorth = z - northZ;
        double dWest = x + 2250 + 80 * (fbm(z / 300.0, 1.1, 2, 13) - 0.5);
        double dSouth = 2350 - z;
        double d = Math.min(Math.min(dEast, dNorth), Math.min(dWest, dSouth));
        // 등대와 해상신전 섬 P2 (2070, 950)
        double island = 95 - Math.hypot(x - 2070, z - 950) + 25 * (fbm(x / 40.0, z / 40.0, 2, 15) - 0.5);
        return Math.max(d, island);
    }

    // ───────────────────────── 기둥 ─────────────────────────

    public static Column column(int x, int z) {
        double px = CX + (x - CX) / SCALE, pz = CZ + (z - CZ) / SCALE;                // 도면 좌표
        double sea = seaDist(px, pz) * SCALE;                                          // 월드 블록 단위
        double base = 66 + 9 * (fbm(x / 180.0, z / 180.0, 4, 1) - 0.5);
        double h = base;

        // 북방 엘프 산맥: 도면 Z -1500 ~ -850 의 두꺼운 띠 + 드워프 산괴 + 북동 산괴 + 고룡 봉우리
        double bandZ = -1175 + 50 * Math.sin(px / 350.0) + 40 * (fbm(px / 200.0, 0.3, 2, 21) - 0.5);
        double band = (1 - smooth((Math.abs(pz - bandZ) - 190) / 150.0)) * smooth((1500 - px) / 160.0) * smooth((px + 2150) / 160.0);
        double dwarf = ellipse(px, pz, -1690, -1070, 340, 310, 22);
        double ne = ellipse(px, pz, 1150, -900, 300, 250, 37);
        double mtn = Math.max(band, Math.max(dwarf * 0.95, ne * 0.9));
        double roadD = mountainRoadDist(px, pz);
        double pass = 1 - 0.8 * Math.exp(-Math.pow(roadD / 38.0, 2));                  // 길을 따라 파인 고갯길
        double gate = Math.exp(-Math.pow(Math.hypot(px + 350, pz + 1040) / 55.0, 2));   // 엘프 산맥 관문 M1 터
        pass = Math.min(pass, 1 - 0.85 * gate);
        double dragon = Math.exp(-Math.pow(Math.hypot(px - 500, pz + 1210) / 170.0, 2)); // 고룡 봉우리 G1
        if (mtn > 0.01) {
            double peaks = 25 + 115 * ridged(x / 240.0, z / 240.0, 5, 23) * (0.5 + 0.8 * fbm(px / 400.0, pz / 400.0, 2, 24));
            peaks += 70 * dragon;
            h += mtn * peaks * pass;
        }
        // 혹한 설원: 북쪽 띠 + 북동 빙원
        double snowLine = -1480 + 400 * (fbm(px / 260.0, 5.5, 3, 35) - 0.5);
        double snow = Math.max(smooth((snowLine - pz) / 120.0), ellipse(px, pz, 650, -1750, 520, 360, 38));
        h += snow * 6 * (fbm(x / 180.0, z / 180.0, 3, 25) - 0.5);
        // 서쪽 엘프 대수림: 둥근 언덕
        double elf = ellipse(px, pz, -1300, -300, 760, 660, 26);
        h += elf * 14 * fbm(x / 220.0, z / 220.0, 3, 27);
        // 남서 재의 들판: 계단식 메사
        double waste = ellipse(px, pz, -1450, 1500, 660, 540, 28);
        if (waste > 0) {
            double mesa = fbm(x / 280.0, z / 280.0, 3, 29);
            double step = Math.floor(mesa * 6) / 6.0;
            h += waste * (mesa > 0.55 ? 28 * step : 3);
        }
        // 심연 균열 W2 (-1540, 1390): 갈라진 협곡
        double rift = ellipse(px, pz, -1540, 1390, 150, 105, 39);
        if (rift > 0) {
            double crack = Math.abs(fbm(x / 90.0, z / 90.0, 3, 40) - 0.5);
            if (crack < 0.06) h -= rift * 18 * (1 - crack / 0.06);
        }
        // 남동 습지: 해수면 가까이
        double swamp = ellipse(px, pz, 1000, 1500, 480, 340, 30);
        h = h * (1 - swamp) + (SEA + 0.3 + 1.5 * (fbm(x / 40.0, z / 40.0, 2, 31) - 0.5)) * swamp;
        // 남쪽 농경평야: 평평하게
        double farm = ellipse(px, pz, 250, 870, 640, 360, 32);
        h = h * (1 - farm * 0.8) + 66 * farm * 0.8;
        // 몰락한 옛 왕도 터 + 봉쇄 요새 터: 평평 (Y=70)
        double dOld = Math.hypot(x - OLD_X, z - OLD_Z);
        double oldFlat = 1 - smooth((dOld - OLD_R) / 70.0);
        double dFort = Math.max(Math.abs(x - FORT_X), Math.abs(z - FORT_Z));
        double fortFlat = 1 - smooth((dFort - 30) / 30.0);
        double siteFlat = Math.max(oldFlat, fortFlat);
        h = h * (1 - siteFlat) + OLD_Y * siteFlat;
        // 왕성도시 터: 평평 (Y=63)
        double dc = Math.hypot(x - CX, z - CZ);
        double city = 1 - smooth((dc - FLAT_R) / 170.0);
        h = h * (1 - city) + CITY_Y * city;
        // 강: 계곡과 물길
        double re = riverEdge(x, z);
        if (re <= 0) h = SEA - 5;
        else {
            double valley = smooth(re / 45.0);
            h = h * valley + SEA * (1 - valley);
        }
        // 해안: 바다로 갈수록 깊어짐
        if (sea < 40) {
            double t = smooth(sea / 40.0);
            double depth = SEA - 2 - Math.min(32, Math.max(0, -sea) * 0.2);
            h = h * t + depth * (1 - t);
        }
        h = Math.min(h, 300);

        int hi = (int) Math.round(h);
        Kind kind;
        int water = Integer.MIN_VALUE;
        boolean frozen = pz < -1950;
        if (sea < 0) {
            kind = frozen ? Kind.FROZEN_OCEAN : sea < -120 ? Kind.DEEP_OCEAN : Kind.OCEAN;
            water = SEA - 1;
        } else if (re <= 0 && dc > 300) {
            kind = Kind.RIVER;
            hi = Math.min(hi, SEA - 5);
            water = SEA - 1;
        } else if (sea < 22 && hi <= SEA + 2) {
            kind = Kind.BEACH;
        } else if (mtn > 0.35 && hi > 100) {
            kind = dwarf > band && dwarf > ne ? Kind.DWARF : Kind.PEAKS;
        } else if (mtn > 0.2 || (snow > 0.5 && hi > 80)) {
            kind = snow > 0.5 ? Kind.SNOW : Kind.MEADOW;
        } else if (snow > 0.5) {
            kind = Kind.SNOW;
        } else if (dOld < 130) {
            kind = Kind.CORRUPT;                                                     // 열린 네더 포탈이 퍼뜨린 오염
        } else if (dOld < OLD_R + 220 || rift > 0.3 || (waste > 0.4 && dOld < 1100)) {
            kind = Kind.ASH;                                                         // 재로 덮인 옛 왕도 들판, 심연 균열
        } else if (waste > 0.4) {
            kind = ellipse(px, pz, -1160, 1110, 260, 220, 33) > 0.4 ? Kind.CURSED : Kind.WASTELAND;
        } else if (ellipse(px, pz, -1160, 1110, 260, 220, 33) > 0.4) {
            kind = Kind.CURSED;
        } else if (swamp > 0.5) {
            kind = Kind.SWAMP;
            if (hi < SEA) water = SEA - 1;
        } else if (elf > 0.45) {
            kind = Kind.ELF_FOREST;
        } else if (elf > 0.15 || ellipse(px, pz, -500, 200, 360, 420, 36) > 0.35) {
            kind = Kind.FOREST;
        } else if (farm > 0.4) {
            kind = Kind.FARMLAND;
        } else {
            kind = Kind.PLAINS;
        }
        if (water == Integer.MIN_VALUE && hi < SEA - 1 && kind != Kind.RIVER) water = SEA - 1;   // 낮은 땅의 웅덩이
        boolean steep = mtn > 0.2 && Math.abs(ridged(x / 60.0, z / 60.0, 2, 34) - 0.5) > 0.32;
        int road = 0;
        float roadEdge = 99;
        if (!KingdomRoads.excluded(x, z)) {
            double[] out = new double[1];
            int ri = KingdomRoads.nearest(x, z, 4, out);
            if (ri >= 0) {
                road = ri + 1;
                roadEdge = (float) out[0];
                if (roadEdge <= 0) steep = false;
            }
        }
        return new Column(hi, water, kind, steep, road, roadEdge);
    }
}
