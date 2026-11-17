package com.royalcity.qol.nav;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaypointBookTest {
    private static final String K = DefaultRegions.KINGDOM;

    @Test
    @DisplayName("내장 지역: 33곳, 모두 왕국 차원, 이름 중복 없음, 도면→월드 변환 확인")
    void defaults() {
        List<Waypoint> all = DefaultRegions.all();
        assertEquals(33, all.size());
        Set<String> names = new HashSet<>();
        for (Waypoint w : all) {
            assertEquals(K, w.dimension());
            assertTrue(w.builtin());
            assertTrue(names.add(w.name()), "중복: " + w.name());
        }
        assertFalse(names.contains("빙해 항구"));
        assertEquals(-2860, DefaultRegions.worldOf(-1300));
        assertEquals(-860, DefaultRegions.worldOf(-300));
        Waypoint tree = new WaypointBook(all).exact("세계수 성역").orElseThrow();
        assertEquals(-2860, tree.x());
        assertEquals(-860, tree.z());
        Waypoint dwarf = new WaypointBook(all).exact("드워프 광산왕국").orElseThrow();
        assertEquals(-3640, dwarf.x());
        assertEquals(-2400, dwarf.z());
    }

    @Test
    @DisplayName("현재 지역: 반경이 작은 곳(촌락)이 큰 지역보다 우선, 다른 차원은 무시")
    void regionAt() {
        WaypointBook b = new WaypointBook(DefaultRegions.all());
        assertEquals("왕성도시", b.regionAt(K, 260, 260).orElseThrow().name());
        assertEquals("세계수 성역", b.regionAt(K, -2860, -860).orElseThrow().name());
        // 엘프 순례촌(-1720,-800)은 세계수 성역 반경 밖이지만 촌락 반경 안
        assertEquals("엘프 순례촌", b.regionAt(K, -1720, -790).orElseThrow().name());
        assertTrue(b.regionAt(K, 5000, 5000).isEmpty());
        assertTrue(b.regionAt("minecraft:overworld", 260, 260).isEmpty());
    }

    @Test
    @DisplayName("가까운 순 정렬, 같은 차원만")
    void nearest() {
        WaypointBook b = new WaypointBook(DefaultRegions.all());
        List<Waypoint> n = b.nearest(K, 300, -650, 3);
        assertEquals("북문 역참촌", n.get(0).name());
        assertEquals(3, n.size());
        assertTrue(b.nearest("minecraft:the_nether", 0, 0, 5).isEmpty());
    }

    @Test
    @DisplayName("이름 찾기: 공백·대소문자 무시, 앞부분 일치, 여러 후보")
    void find() {
        WaypointBook b = new WaypointBook(DefaultRegions.all());
        assertEquals("왕성도시", b.find("왕성 도시").chosen().orElseThrow().name());
        assertEquals("몰락한 옛 왕도", b.find("몰락").chosen().orElseThrow().name());
        WaypointBook.Match m = b.find("동부");
        assertTrue(m.chosen().isEmpty());
        assertTrue(m.candidates().size() >= 3);
        assertTrue(b.find("없는곳").candidates().isEmpty());
    }

    @Test
    @DisplayName("추가·삭제: 중복 거부, 내장 지역은 삭제 불가")
    void addRemove() {
        WaypointBook b = new WaypointBook(DefaultRegions.all());
        Waypoint home = new Waypoint("우리 집", K, 100, 70, 200, 16, false, "");
        assertEquals(WaypointBook.AddResult.ADDED, b.add(home));
        assertEquals(WaypointBook.AddResult.DUPLICATE, b.add(new Waypoint("우리집", K, 0, 0, 0, 5, false, "")));
        assertEquals(WaypointBook.AddResult.DUPLICATE, b.add(new Waypoint("왕성도시", K, 0, 0, 0, 5, false, "")));
        assertEquals(1, b.user().size());
        assertEquals(WaypointBook.RemoveResult.BUILTIN, b.remove("왕성도시"));
        assertEquals(WaypointBook.RemoveResult.NOT_FOUND, b.remove("없는곳"));
        assertEquals(WaypointBook.RemoveResult.REMOVED, b.remove("우리 집"));
        assertTrue(b.user().isEmpty());
        assertEquals(33, b.all().size());
    }

    @Test
    @DisplayName("JSON 저장·읽기: 사용자 웨이포인트만 저장, 잘못된 항목은 건너뜀, 내장 이름과 겹치면 건너뜀")
    void json() {
        WaypointBook b = new WaypointBook(DefaultRegions.all());
        b.add(new Waypoint("광산 입구", K, -10, 40, 20, 8, false, "메모"));
        b.add(new Waypoint("네더 기지", "minecraft:the_nether", 5, 70, 5, 16, false, ""));
        String text = WaypointJson.write(false, b.all());
        assertFalse(text.contains("왕성도시"));

        WaypointJson.Data d = WaypointJson.read(text);
        assertFalse(d.hud());
        assertEquals(2, d.waypoints().size());
        assertEquals("메모", d.waypoints().get(0).note());
        assertTrue(d.problems().isEmpty());

        WaypointBook b2 = new WaypointBook(DefaultRegions.all());
        assertEquals(0, b2.replaceUser(d.waypoints()));
        assertEquals(35, b2.all().size());

        WaypointJson.Data bad = WaypointJson.read("{\"waypoints\":[{\"name\":\"왕성도시\",\"x\":1,\"z\":2},{\"x\":1},{\"name\":\"ok\",\"x\":1,\"z\":2}]}");
        assertTrue(bad.hud());
        assertEquals(2, bad.waypoints().size());
        assertEquals(1, bad.problems().size());
        assertEquals(1, new WaypointBook(DefaultRegions.all()).replaceUser(bad.waypoints()));
    }
}
