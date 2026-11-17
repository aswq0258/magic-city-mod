package com.royalcity.qol.nav;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 내장 지역 웨이포인트 (차원 {@code magiccity:kingdom}, 월드 좌표).
 *
 * <p>좌표 출처: magiccity 소스의 각 site 중심 상수와 {@code Hamlets.spots()} (2026-11-17, 커밋 f30d2f2).
 * 도면 좌표로 정의된 곳은 {@link #worldOf(int)} 로 바꾼다 (월드 = 260 + (도면 − 260) × 2).
 * 반경은 지역 알림용 근사값이다 [초안].
 */
public final class DefaultRegions {
    private DefaultRegions() {}

    public static final String KINGDOM = "magiccity:kingdom";

    /** 도면 좌표 → 월드 좌표 (x, z 공통) */
    public static int worldOf(int plan) {
        return 260 + (plan - 260) * 2;
    }

    private static Waypoint r(String name, int x, int z, int radius, String note) {
        return new Waypoint(name, KINGDOM, x, 64, z, radius, true, note);
    }

    private static final List<Waypoint> ALL;

    static {
        List<Waypoint> l = new ArrayList<>();
        // 주요 지역 14곳
        l.add(r("왕성도시", 260, 260, 260, "수도. 하수도는 지하"));
        l.add(r("세계수 성역", worldOf(-1300), worldOf(-300), 360, "보랏빛 숲. 아래에 세계수 뿌리 던전(3장)"));
        l.add(r("드워프 광산왕국", worldOf(-1690), worldOf(-1070), 220, "아래에 드워프 심층 광산(5장)"));
        l.add(r("엘프 산맥 관문", worldOf(-350), worldOf(-1040), 80, ""));
        l.add(r("서리 결투장", -855, -2442, 50, "4장. 관문에서 길 따라 북동쪽 (위치 근사)"));
        l.add(r("고룡 봉우리 드래곤 레어", 760, -2740, 200, "11장. 입구 (680, -2598)"));
        l.add(r("부유섬 유적", worldOf(1200), worldOf(-820), 200, "9장"));
        l.add(r("동해안 항구도시", worldOf(1490), worldOf(900), 180, "6장 잠긴 성소"));
        l.add(r("등대 섬", worldOf(2070), worldOf(950), 80, ""));
        l.add(r("저주받은 숲", worldOf(-1160), worldOf(1110), 260, "2장"));
        l.add(r("심연 균열", worldOf(-1540), worldOf(1390), 120, "10장"));
        l.add(r("남부 국경도시 로젠하르트", worldOf(-200), worldOf(1670), 140, "8장. 남쪽 들판에서 습격"));
        l.add(r("고대 왕국 유적", worldOf(660), worldOf(1700), 180, "7장"));
        l.add(r("몰락한 옛 왕도", -3960, 3260, 190, "12장"));
        // 테마 촌락 19곳 (Hamlets.spots, 월드 좌표, 반경 = 촌락 반폭 + 10)
        l.add(r("북문 역참촌", 320, -700, 65, ""));
        l.add(r("남부 농경마을", 80, 1700, 100, ""));
        l.add(r("남서 제분촌", -660, 1280, 60, ""));
        l.add(r("남부 사거리촌", -380, 2440, 75, ""));
        l.add(r("동부 강변 수차촌", 830, 1110, 65, ""));
        l.add(r("서부 숲 전초마을", -1120, -80, 75, ""));
        l.add(r("엘프 순례촌", -1720, -800, 75, ""));
        l.add(r("서부 약초촌", -2060, 220, 55, ""));
        l.add(r("드워프 광석 운송촌", -2960, -1920, 70, ""));
        l.add(r("혹한의 북부 마을", 40, -3440, 80, ""));
        l.add(r("동부 교역소", 1600, 240, 90, ""));
        l.add(r("동부 상단 중계촌", 2100, 860, 80, ""));
        l.add(r("동해안 어촌", 3440, 880, 55, ""));
        l.add(r("남동 늪지 수상촌", 1840, 2600, 65, ""));
        l.add(r("남서 변경 경비촌", -1880, 2580, 85, ""));
        l.add(r("북부 보급초소", -60, -3960, 35, ""));
        l.add(r("동부 해안 등대초소", 3500, -560, 32, ""));
        l.add(r("고대 유적 발굴 야영지", 820, 2840, 40, ""));
        l.add(r("폐왕도 피난민 은신처", -2900, 2940, 45, ""));
        ALL = Collections.unmodifiableList(l);
    }

    public static List<Waypoint> all() {
        return ALL;
    }
}
