package io.github.aswq0258.magiccity.arena;

import com.google.gson.JsonParser;
import io.github.aswq0258.magiccity.arena.BossRoomRules.Spec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 보스 방 복사 전장 규칙 (claude/보스방복사전장_초안.md) */
class BossRoomRulesTest {
    private static Spec spec(String json) {
        return BossRoomRules.parse(JsonParser.parseString(json).getAsJsonObject());
    }

    @Test
    @DisplayName("범위 읽기: 기본값(지하 아님·켜짐), 크기 계산, 잘못된 값 거부")
    void parse() {
        Spec s = spec("{\"radius\":24,\"down\":4,\"up\":13,\"underground\":true}");
        assertTrue(s.underground());
        assertTrue(s.enabled());
        assertEquals(49, s.sizeX());
        assertEquals(18, s.sizeY());
        assertEquals(49L * 18 * 49, s.volume());
        Spec o = spec("{\"radius\":20,\"down\":6,\"up\":24,\"enabled\":false}");
        assertFalse(o.underground());
        assertFalse(o.enabled());
        assertThrows(IllegalArgumentException.class, () -> spec("{\"radius\":60,\"down\":4,\"up\":13}"));
        assertThrows(IllegalArgumentException.class, () -> spec("{\"radius\":20,\"down\":-1,\"up\":13}"));
        assertThrows(RuntimeException.class, () -> spec("{\"radius\":20}"));
    }

    @Test
    @DisplayName("뜨는 자리와 까는 자리: 보스 자리가 전장 (장 중심, 바닥 높이) 에 옴")
    void origins() {
        Spec s = spec("{\"radius\":24,\"down\":4,\"up\":13}");
        int[] cap = BossRoomRules.captureOrigin(260, 46, 259, s);
        assertArrayEquals(new int[]{236, 42, 235}, cap);
        int[] put = BossRoomRules.placeOrigin(1000, 64, 0, s);
        assertArrayEquals(new int[]{976, 60, -24}, put);
        // 보스 자리의 상대 좌표는 양쪽이 같음
        assertEquals(260 - cap[0], 1000 - put[0]);
        assertEquals(46 - cap[1], 64 - put[1]);
        assertEquals(259 - cap[2], 0 - put[2]);
    }

    @Test
    @DisplayName("경계: 가로 r + 여유 2 밖, 바닥 아래 여유 2 밖이면 범위 밖")
    void bounds() {
        Spec s = spec("{\"radius\":20,\"down\":6,\"up\":24}");
        assertFalse(BossRoomRules.outOfBox(0, 0, 0, s));
        assertFalse(BossRoomRules.outOfBox(22, 0, -22, s));
        assertTrue(BossRoomRules.outOfBox(23, 0, 0, s));
        assertTrue(BossRoomRules.outOfBox(0, 0, -23, s));
        assertFalse(BossRoomRules.outOfBox(0, -8, 0, s));
        assertTrue(BossRoomRules.outOfBox(0, -8.5, 0, s));
        assertFalse(BossRoomRules.outOfBox(0, 34, 0, s));
        assertTrue(BossRoomRules.outOfBox(0, 35, 0, s));
    }

    @Test
    @DisplayName("경계 칸 종류: 윗면 > 옆면 > 바닥, 안쪽은 0")
    void faces() {
        Spec s = spec("{\"radius\":4,\"down\":2,\"up\":5}");
        assertEquals(0, BossRoomRules.face(4, 2, 4, s));
        assertEquals(1, BossRoomRules.face(0, 3, 4, s));
        assertEquals(1, BossRoomRules.face(4, 0, 8, s));
        assertEquals(2, BossRoomRules.face(4, 0, 4, s));
        assertEquals(3, BossRoomRules.face(0, 7, 0, s));
        assertTrue(BossRoomRules.insideRel(8, 7, 8, s));
        assertFalse(BossRoomRules.insideRel(9, 0, 0, s));
    }
}
