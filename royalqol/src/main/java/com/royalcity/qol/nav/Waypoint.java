package com.royalcity.qol.nav;

/**
 * 지역 또는 사용자 웨이포인트. 좌표는 월드 좌표(블록), 반경은 "이 지역 안"으로 보는 수평 거리.
 *
 * @param name      표시 이름 (고유)
 * @param dimension 차원 ID (예: {@code magiccity:kingdom})
 * @param builtin   내장 지역이면 true (파일에 저장하지 않고 /rq remove 로 지울 수 없음)
 * @param note      짧은 설명 (없으면 빈 문자열)
 */
public record Waypoint(String name, String dimension, int x, int y, int z, int radius, boolean builtin, String note) {

    public Waypoint {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("이름이 비어 있음");
        if (dimension == null || dimension.isBlank()) throw new IllegalArgumentException("차원이 비어 있음");
        name = name.strip();
        radius = Math.max(1, radius);
        note = note == null ? "" : note;
    }

    /** 수평 거리 */
    public double distanceTo(double px, double pz) {
        return Math.hypot(x - px, z - pz);
    }

    public boolean contains(String dim, double px, double pz) {
        return dimension.equals(dim) && distanceTo(px, pz) <= radius;
    }
}
