package io.github.aswq0258.magiccity.world;

import java.util.List;

/**
 * 왕국 대륙 지하 동굴 (2026-11-22, 순수 계산 — 마인크래프트 클래스 없음).
 * <ul>
 *   <li>Y {@value #TOP} 이하(심층암 층)에만 생김. 지표·하수도·지하 던전 대부분과 멀리 떨어짐</li>
 *   <li>굴: 3차원 노이즈 둘이 동시에 0 근처인 곳(두 곡면이 만나는 선) → 구불구불 이어지는 굴</li>
 *   <li>방: 느린 노이즈가 높은 곳 → 가끔 넓은 방</li>
 *   <li>Y {@value #LAVA_Y} 이하의 빈칸은 용암 (바닐라 용암 높이와 비슷), 물(지하수)은 두지 않음</li>
 *   <li>지어 둔 건축물·던전 영역(철거 영역 상자) + {@value #MARGIN}칸 안에는 만들지 않음 → 동굴·용암이 던전을 침범하지 않음</li>
 * </ul>
 * 같은 좌표는 항상 같은 결과 (월드 씨앗과 무관) — 새 청크 생성과 기존 월드용 {@code /magiccity fix caves} 가 같은 모양.
 */
public final class CaveRules {
    private CaveRules() {}

    /** 동굴이 생기는 가장 높은 Y (이 높이 포함, 0 미만) */
    public static final int TOP = -1;
    /** 동굴이 생기는 가장 낮은 Y (기반암 −64 위 여유) */
    public static final int BOTTOM = -60;
    /** 이 높이 이하의 동굴 빈칸은 용암 */
    public static final int LAVA_Y = -55;
    /** 던전 영역 둘레에 남길 돌 두께 */
    public static final int MARGIN = 8;

    private static final double TUNNEL_XZ = 1 / 52.0, TUNNEL_Y = 1 / 26.0, TUNNEL_W = 0.085;
    private static final double ROOM_XZ = 1 / 70.0, ROOM_Y = 1 / 30.0, ROOM_T = 0.47;

    /** 이 칸이 동굴(빈칸 또는 용암)인지 — 던전 영역 검사는 {@link #carve} */
    public static boolean open(int x, int y, int z) {
        if (y > TOP || y < BOTTOM) return false;
        // 위·아래 끝으로 갈수록 좁게 (천장·바닥이 갑자기 끊기지 않게)
        double edge = Math.min(TOP - y, y - BOTTOM);
        double taper = edge >= 6 ? 1 : (edge + 1) / 7.0;
        double a = noise(x * TUNNEL_XZ, y * TUNNEL_Y, z * TUNNEL_XZ, 11);
        double b = noise(x * TUNNEL_XZ, y * TUNNEL_Y, z * TUNNEL_XZ, 23);
        double w = TUNNEL_W * taper;
        if (Math.abs(a) < w && Math.abs(b) < w) return true;
        double r = noise(x * ROOM_XZ, y * ROOM_Y, z * ROOM_XZ, 37);
        return r > ROOM_T + (1 - taper) * 0.3;
    }

    /** 영역 상자 {x0, z0, x1, z1, y0, y1} 들 + MARGIN 안인지 */
    public static boolean nearZone(int x, int y, int z, List<int[]> zones) {
        for (int[] a : zones) {
            if (x >= a[0] - MARGIN && x <= a[2] + MARGIN && z >= a[1] - MARGIN && z <= a[3] + MARGIN
                    && y >= a[4] - MARGIN && y <= a[5] + MARGIN) return true;
        }
        return false;
    }

    /** 실제로 파낼지: 동굴이고 던전 영역 근처가 아님 */
    public static boolean carve(int x, int y, int z, List<int[]> zones) {
        return open(x, y, z) && !nearZone(x, y, z, zones);
    }

    /** 파낸 칸에 놓을 것: true = 용암, false = 빈칸 */
    public static boolean lava(int y) {
        return y <= LAVA_Y;
    }

    // ───────── 3차원 그래디언트 노이즈 (펄린 방식, 결과 약 −1 ~ 1) ─────────

    static double noise(double x, double y, double z, long salt) {
        int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y), z0 = (int) Math.floor(z);
        double fx = x - x0, fy = y - y0, fz = z - z0;
        double u = fade(fx), v = fade(fy), w = fade(fz);
        double n000 = grad(x0, y0, z0, salt, fx, fy, fz), n100 = grad(x0 + 1, y0, z0, salt, fx - 1, fy, fz);
        double n010 = grad(x0, y0 + 1, z0, salt, fx, fy - 1, fz), n110 = grad(x0 + 1, y0 + 1, z0, salt, fx - 1, fy - 1, fz);
        double n001 = grad(x0, y0, z0 + 1, salt, fx, fy, fz - 1), n101 = grad(x0 + 1, y0, z0 + 1, salt, fx - 1, fy, fz - 1);
        double n011 = grad(x0, y0 + 1, z0 + 1, salt, fx, fy - 1, fz - 1), n111 = grad(x0 + 1, y0 + 1, z0 + 1, salt, fx - 1, fy - 1, fz - 1);
        double x00 = lerp(u, n000, n100), x10 = lerp(u, n010, n110), x01 = lerp(u, n001, n101), x11 = lerp(u, n011, n111);
        return lerp(w, lerp(v, x00, x10), lerp(v, x01, x11));
    }

    private static double fade(double t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    private static double lerp(double t, double a, double b) {
        return a + t * (b - a);
    }

    /** 격자점 해시로 고른 12방향 기울기 중 하나와 거리 벡터의 내적 */
    private static double grad(int ix, int iy, int iz, long salt, double dx, double dy, double dz) {
        long h = ix * 0x9E3779B97F4A7C15L ^ iy * 0xC2B2AE3D27D4EB4FL ^ iz * 0x165667B19E3779F9L ^ salt * 0x27D4EB2F165667C5L;
        h ^= h >>> 31;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 29;
        switch ((int) Math.floorMod(h, 12L)) {
            case 0: return dx + dy;
            case 1: return -dx + dy;
            case 2: return dx - dy;
            case 3: return -dx - dy;
            case 4: return dx + dz;
            case 5: return -dx + dz;
            case 6: return dx - dz;
            case 7: return -dx - dz;
            case 8: return dy + dz;
            case 9: return -dy + dz;
            case 10: return dy - dz;
            default: return -dy - dz;
        }
    }
}
