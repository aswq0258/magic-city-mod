package com.royalcity.qol.nav;

/**
 * 방위·화살표·시간 계산 (Minecraft 클래스 없음).
 *
 * <p>Minecraft 의 yaw: 0 = 남(+z), 90 = 서(−x), 180 = 북(−z), −90 = 동(+x). 값이 커질수록 오른쪽으로 돈다.
 */
public final class NavMath {
    private NavMath() {}

    public static final String[] COMPASS = {"북", "북동", "동", "남동", "남", "남서", "서", "북서"};
    /** 바라보는 방향 기준 화살표: 앞, 오른쪽 앞, 오른쪽, 오른쪽 뒤, 뒤, 왼쪽 뒤, 왼쪽, 왼쪽 앞 */
    public static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

    /** −180 이상 180 미만으로 정리 */
    public static double wrap(double deg) {
        double d = ((deg + 180.0) % 360.0 + 360.0) % 360.0;
        return d - 180.0;
    }

    /** 북쪽 0, 시계 방향 각도(0~360) — yaw 를 지도 방위로 */
    public static double bearingOfYaw(double yaw) {
        return ((yaw + 180.0) % 360.0 + 360.0) % 360.0;
    }

    /** (dx, dz) 쪽을 바라보는 yaw */
    public static double yawToward(double dx, double dz) {
        return Math.toDegrees(Math.atan2(-dx, dz));
    }

    /** 0~7 칸 (45°씩, 경계는 가운데로 반올림) */
    public static int octant(double deg) {
        return (int) Math.floorMod(Math.round(deg / 45.0), 8L);
    }

    /** 플레이어가 바라보는 8방위 */
    public static String facing(double yaw) {
        return COMPASS[octant(bearingOfYaw(yaw))];
    }

    /** 플레이어 위치에서 목표가 있는 8방위 (지도 기준) */
    public static String compassTo(double px, double pz, double tx, double tz) {
        return COMPASS[octant(bearingOfYaw(yawToward(tx - px, tz - pz)))];
    }

    /** 바라보는 방향 기준으로 목표를 가리키는 화살표 */
    public static String arrowTo(double yaw, double px, double pz, double tx, double tz) {
        double rel = wrap(yawToward(tx - px, tz - pz) - yaw);
        return ARROWS[octant(rel)];
    }

    /** 게임 날짜 (1일차부터). dayTime 은 Level.getDayTime() */
    public static long day(long dayTime) {
        return Math.floorDiv(dayTime, 24000L) + 1;
    }

    /** 게임 시각 "HH:MM". dayTime 0 = 06:00 */
    public static String clock(long dayTime) {
        long t = Math.floorMod(dayTime + 6000L, 24000L);
        long h = t / 1000L;
        long m = (t % 1000L) * 60L / 1000L;
        return String.format("%02d:%02d", h, m);
    }

    /** 목적지에 도착했다고 볼 거리: 지역 반경 안이되 최소 6, 최대 32 */
    public static double arrivalRange(Waypoint w) {
        return Math.max(6, Math.min(w.radius(), 32));
    }

    /** 사람이 읽기 쉬운 거리 */
    public static String distance(double d) {
        if (d < 1000) return Math.round(d) + "m";
        return String.format("%.1fkm", d / 1000.0);
    }
}
