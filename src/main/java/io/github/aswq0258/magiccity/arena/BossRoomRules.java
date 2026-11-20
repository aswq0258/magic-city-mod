package io.github.aswq0258.magiccity.arena;

import com.google.gson.JsonObject;

/**
 * 장별 전장 = 실제 보스 방 복사 (순수 계산). 기준: 프로젝트 문서 claude/보스방복사전장_초안.md (2026-11-20 사용자 결정: 추천안 전부).
 * 뜰 범위 = 보스 자리 기준 가로 ±r, 세로 아래 down ~ 위 up. 전장에는 보스 자리가 (장 중심 x, FLOOR_Y, 0) 에 오게 깐다.
 */
public final class BossRoomRules {
    private BossRoomRules() {}

    /** 경계 여유 (범위 밖 이만큼까지는 봐줌) */
    public static final int MARGIN = 2;

    /** 뜰 범위. underground = 지하 방(천장이 뚫린 칸을 돌로 막고 바닥도 막음), enabled = false 면 공용 전장 */
    public record Spec(int r, int down, int up, boolean underground, boolean enabled) {
        public int sizeX() { return 2 * r + 1; }
        public int sizeY() { return down + up + 1; }
        public int sizeZ() { return 2 * r + 1; }
        public long volume() { return (long) sizeX() * sizeY() * sizeZ(); }
    }

    public static Spec parse(JsonObject o) {
        int r = o.get("radius").getAsInt(), down = o.get("down").getAsInt(), up = o.get("up").getAsInt();
        if (r < 4 || r > 48) throw new IllegalArgumentException("radius 는 4~48: " + r);
        if (down < 0 || down > 40) throw new IllegalArgumentException("down 은 0~40: " + down);
        if (up < 4 || up > 80) throw new IllegalArgumentException("up 은 4~80: " + up);
        boolean under = o.has("underground") && o.get("underground").getAsBoolean();
        boolean on = !o.has("enabled") || o.get("enabled").getAsBoolean();
        return new Spec(r, down, up, under, on);
    }

    /** 뜰 상자의 가장 작은 모서리 (보스 자리 블록 기준) */
    public static int[] captureOrigin(int bx, int by, int bz, Spec s) {
        return new int[]{bx - s.r(), by - s.down(), bz - s.r()};
    }

    /** 전장에 깔 상자의 가장 작은 모서리: 보스 자리가 (cx, floorY, cz) 에 오도록 */
    public static int[] placeOrigin(int cx, int floorY, int cz, Spec s) {
        return new int[]{cx - s.r(), floorY - s.down(), cz - s.r()};
    }

    /** 범위 밖인지. dx·dy·dz = 보스 자리 기준 차이. 아래는 바닥 밑으로 떨어진 것, 위는 범위 위로 많이 벗어난 것 */
    public static boolean outOfBox(double dx, double dy, double dz, Spec s) {
        double h = s.r() + MARGIN + 0.5;
        return Math.abs(dx) > h || Math.abs(dz) > h || dy < -s.down() - MARGIN || dy > s.up() + MARGIN + 8;
    }

    /** 상자 안 상대 좌표 (0 ~ size-1) 인지 */
    public static boolean insideRel(int x, int y, int z, Spec s) {
        return x >= 0 && y >= 0 && z >= 0 && x < s.sizeX() && y < s.sizeY() && z < s.sizeZ();
    }

    /** 경계 칸 종류: 0 = 안쪽, 1 = 옆면, 2 = 바닥, 3 = 윗면 (상대 좌표) */
    public static int face(int x, int y, int z, Spec s) {
        if (y == s.sizeY() - 1) return 3;
        if (x == 0 || z == 0 || x == s.sizeX() - 1 || z == s.sizeZ() - 1) return 1;
        if (y == 0) return 2;
        return 0;
    }
}
