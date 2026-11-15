package io.github.aswq0258.magiccity.world;

/**
 * "북쪽의 빙해에서 남서쪽의 마왕성까지" 대륙 지형 (사용자 마스터플랜 좌표 그대로, 북쪽 = -Z)
 *
 *  - 중앙 왕성도시 (250, 250): 반지름 430 안은 Y=63 평지 → 여기서 /magiccity build 로 왕국을 지음
 *  - 북쪽: 혹한 설원(Z -1350 북쪽), 빙해(Z 약 -2150 북쪽)
 *  - 북방 엘프 산맥: Z 약 -1180 을 따라 동서로 길게 (북쪽 왕도 고갯길 X≈310 은 낮게)
 *  - 북서: 드워프 광산왕국 산괴 (-1700, -1000)
 *  - 서: 고대 엘프 대수림 언덕 (-1300, -300)
 *  - 남서: 검은 황무지 메사 (-1450, 1500), 저주받은 숲 (-1200, 1000)
 *  - 남동: 남동 습지 (1000, 1500)
 *  - 남: 왕국 농경평야 (250, 850)
 *  - 동: 큰 강 (북쪽 산맥 → 왕성 동쪽 → 남동쪽 하구) 과 동쪽 외해 해안선 (X 약 1950)
 */
public final class KingdomTerrain {
    private KingdomTerrain() {}

    public static final int CX = 250, CZ = 250, SEA = 63, CITY_Y = 63, FLAT_R = 430;

    public enum Kind { OCEAN, DEEP_OCEAN, FROZEN_OCEAN, BEACH, RIVER, PLAINS, FARMLAND, FOREST, ELF_FOREST, CURSED, SNOW, MEADOW, PEAKS, DWARF, WASTELAND, SWAMP }

    /** 한 기둥: 지면 높이, 물 높이(없으면 Integer.MIN_VALUE), 종류 */
    public record Column(int h, int water, Kind kind, boolean steep) {}

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

    /** 타원 안쪽 정도 (1 = 가운데, 0 = 경계 밖) — 경계는 노이즈로 들쭉날쭉 */
    private static double ellipse(int x, int z, double ex, double ez, double rx, double rz, long salt) {
        double dx = (x - ex) / rx, dz = (z - ez) / rz;
        double e = 1 - (dx * dx + dz * dz) + 0.35 * (fbm(x / 160.0, z / 160.0, 2, salt) - 0.5);
        return smooth(e * 2.2);
    }

    // ───────────────────────── 강 ─────────────────────────

    /** 큰 강 중심선 (북쪽 산맥 → 왕성 동쪽 → 남동 하구 → 외해) */
    private static final int[][] RIVER = {
            {470, -1160}, {520, -900}, {570, -600}, {600, -300}, {590, -140},
            {590, 640}, {660, 800}, {760, 1000}, {900, 1180}, {1100, 1290}, {1350, 1310}, {1600, 1270}, {2100, 1250},
    };

    /** 왕성 근처에서는 도시 설계(CityLayout.riverX)와 정확히 같은 곡선 */
    private static double cityRiverX(int z) {
        int dn = CZ - z;
        return CX + 332 + 8 * Math.sin(dn / 45.0);
    }

    /** 강 중심선까지의 거리 */
    public static double riverDist(int x, int z) {
        if (z >= -140 && z <= 640) return Math.abs(x - cityRiverX(z));
        double best = 1e9;
        for (int i = 0; i + 1 < RIVER.length; i++) {
            int[] a = RIVER[i], b = RIVER[i + 1];
            if (a[1] == -140 && b[1] == 640) continue;
            double vx = b[0] - a[0], vz = b[1] - a[1], wx = x - a[0], wz = z - a[1];
            double t = Math.max(0, Math.min(1, (wx * vx + wz * vz) / (vx * vx + vz * vz)));
            double px = a[0] + vx * t, pz = a[1] + vz * t;
            double wob = 6 * (fbm(x / 70.0, z / 70.0, 2, 77) - 0.5);
            best = Math.min(best, Math.hypot(x - px, z - pz) + wob);
        }
        return best;
    }

    // ───────────────────────── 해안 ─────────────────────────

    /** 바다까지의 거리 (양수 = 육지 안쪽, 음수 = 바다) */
    private static double seaDist(int x, int z) {
        double coastX = 1950 + 140 * (fbm(z / 420.0, 3.3, 3, 11) - 0.5) - 650 * smooth((-1750 - z) / 450.0);
        double dEast = coastX - x;
        double northZ = -2150 + 160 * (fbm(x / 380.0, 7.7, 3, 12) - 0.5);
        double dNorth = z - northZ;
        double dWest = x + 2250 + 80 * (fbm(z / 300.0, 1.1, 2, 13) - 0.5);
        double dSouth = 2350 - z;
        return Math.min(Math.min(dEast, dNorth), Math.min(dWest, dSouth));
    }

    // ───────────────────────── 기둥 ─────────────────────────

    public static Column column(int x, int z) {
        double sea = seaDist(x, z);
        double base = 66 + 9 * (fbm(x / 180.0, z / 180.0, 4, 1) - 0.5);
        double h = base;

        // 북방 엘프 산맥 (동서 띠) + 드워프 산괴
        double bandZ = -1180 + 60 * Math.sin(x / 350.0) + 40 * (fbm(x / 200.0, 0.3, 2, 21) - 0.5);
        double band = Math.exp(-Math.pow((z - bandZ) / 170.0, 2)) * smooth((1450 - x) / 160.0) * smooth((x + 2150) / 160.0);
        double dwarf = ellipse(x, z, -1700, -1000, 340, 310, 22);
        double mtn = Math.max(band, dwarf * 0.95);
        double pass = 1 - 0.78 * Math.exp(-Math.pow((x - 310) / 48.0, 2));            // 북쪽 왕도 고갯길
        if (mtn > 0.01) {
            double peaks = 25 + 115 * ridged(x / 120.0, z / 120.0, 5, 23) * (0.5 + 0.8 * fbm(x / 400.0, z / 400.0, 2, 24));
            h += mtn * peaks * (dwarf > band ? 1 : pass);
        }
        // 북쪽 설원: 완만한 기복
        double snow = smooth((-1330 - z) / 120.0);
        h += snow * 6 * (fbm(x / 90.0, z / 90.0, 3, 25) - 0.5);
        // 서쪽 엘프 대수림: 둥근 언덕
        double elf = ellipse(x, z, -1300, -300, 760, 660, 26);
        h += elf * 14 * fbm(x / 110.0, z / 110.0, 3, 27);
        // 남서 검은 황무지: 계단식 메사
        double waste = ellipse(x, z, -1450, 1500, 660, 540, 28);
        if (waste > 0) {
            double mesa = fbm(x / 140.0, z / 140.0, 3, 29);
            double step = Math.floor(mesa * 6) / 6.0;
            h += waste * (mesa > 0.55 ? 28 * step : 3);
        }
        // 남동 습지: 해수면 가까이
        double swamp = ellipse(x, z, 1000, 1500, 480, 340, 30);
        h = h * (1 - swamp) + (SEA + 0.3 + 1.5 * (fbm(x / 40.0, z / 40.0, 2, 31) - 0.5)) * swamp;
        // 남쪽 농경평야: 평평하게
        double farm = ellipse(x, z, 250, 870, 640, 360, 32);
        h = h * (1 - farm * 0.8) + 66 * farm * 0.8;
        // 왕성도시 터: 평평 (Y=63)
        double dc = Math.hypot(x - CX, z - CZ);
        double city = 1 - smooth((dc - FLAT_R) / 170.0);
        h = h * (1 - city) + CITY_Y * city;
        // 강: 계곡과 물길
        double rd = riverDist(x, z);
        if (rd <= 9) h = SEA - 5;
        else {
            double valley = smooth((rd - 9) / 45.0);
            h = h * valley + SEA * (1 - valley);
        }
        // 해안: 바다로 갈수록 깊어짐
        if (sea < 40) {
            double t = smooth(sea / 40.0);
            double depth = SEA - 2 - Math.min(32, Math.max(0, -sea) * 0.35);
            h = h * t + depth * (1 - t);
        }

        int hi = (int) Math.round(h);
        Kind kind;
        int water = Integer.MIN_VALUE;
        boolean frozen = z < -1950;
        if (sea < 0) {
            kind = frozen ? Kind.FROZEN_OCEAN : sea < -60 ? Kind.DEEP_OCEAN : Kind.OCEAN;
            water = SEA - 1;
        } else if (rd <= 9 && dc > 300) {
            kind = Kind.RIVER;
            hi = Math.min(hi, SEA - 5);
            water = SEA - 1;
        } else if (sea < 22 && hi <= SEA + 2) {
            kind = Kind.BEACH;
        } else if (mtn > 0.35 && hi > 100) {
            kind = dwarf > band ? Kind.DWARF : Kind.PEAKS;
        } else if (mtn > 0.2 || (snow > 0.5 && hi > 80)) {
            kind = snow > 0.5 ? Kind.SNOW : Kind.MEADOW;
        } else if (snow > 0.5) {
            kind = Kind.SNOW;
        } else if (waste > 0.4) {
            kind = ellipse(x, z, -1200, 1000, 260, 220, 33) > 0.4 ? Kind.CURSED : Kind.WASTELAND;
        } else if (ellipse(x, z, -1200, 1000, 260, 220, 33) > 0.4) {
            kind = Kind.CURSED;
        } else if (swamp > 0.5) {
            kind = Kind.SWAMP;
            if (hi < SEA) water = SEA - 1;
        } else if (elf > 0.45) {
            kind = Kind.ELF_FOREST;
        } else if (elf > 0.15 || (dc > 520 && x < CX - 250 && Math.abs(z - CZ) < 500)) {
            kind = Kind.FOREST;
        } else if (farm > 0.4) {
            kind = Kind.FARMLAND;
        } else {
            kind = Kind.PLAINS;
        }
        if (water == Integer.MIN_VALUE && hi < SEA - 1 && kind != Kind.RIVER) water = SEA - 1;   // 낮은 땅의 웅덩이
        boolean steep = mtn > 0.2 && Math.abs(ridged(x / 30.0, z / 30.0, 2, 34) - 0.5) > 0.32;
        return new Column(hi, water, kind, steep);
    }
}
