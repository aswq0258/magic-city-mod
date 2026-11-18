package io.github.aswq0258.magiccity.quest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 개인 퀘스트 규칙·정의 파싱 단위 테스트 (Minecraft 없이 실행).
 * 기준: 프로젝트 문서 claude/서장NPC_직업선택_초안.md 2·5절.
 */
class QuestRulesTest {

    private static QuestDef kingsCall() throws Exception {
        Path p = Path.of("src/main/resources/data/magiccity/quests/kings_call.json");
        JsonObject o = JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8)).getAsJsonObject();
        return QuestDef.parse("kings_call", o);
    }

    @Test
    @DisplayName("배포 데이터 kings_call: 3단계 흐름(신호 → 하수도 → 1장 완료)으로 읽힘")
    void shippedQuestParses() throws Exception {
        QuestDef q = kingsCall();
        assertEquals("steward", q.giver());
        assertEquals(3, q.size());
        assertEquals(QuestDef.Type.SIGNAL, q.steps().get(0).type());
        assertEquals("fantasyrpg:class_chosen", q.steps().get(0).signal());
        assertEquals("choose_class", q.steps().get(0).actionId());
        assertEquals(QuestDef.Type.ENTER_AREA, q.steps().get(1).type());
        assertEquals(QuestDef.Type.CHAPTER_CLEAR, q.steps().get(2).type());
        assertEquals(1, q.steps().get(2).chapter());
    }

    @Test
    @DisplayName("받기: 주는 NPC·받지 않음·선행 퀘스트를 모두 만족할 때만")
    void offer() throws Exception {
        QuestDef q = kingsCall();
        assertTrue(QuestRules.offerable(q, QuestRules.NOT_STARTED, "steward", Set.of()));
        assertFalse(QuestRules.offerable(q, 0, "steward", Set.of()));
        assertFalse(QuestRules.offerable(q, QuestRules.NOT_STARTED, "guard", Set.of()));
        QuestDef needs = new QuestDef("b", "b", "steward", "", List.of("kings_call"), List.of(), "a", "d", q.steps(), List.of());
        assertFalse(QuestRules.offerable(needs, QuestRules.NOT_STARTED, "steward", Set.of()));
        assertTrue(QuestRules.offerable(needs, QuestRules.NOT_STARTED, "steward", Set.of("kings_call")));
    }

    @Test
    @DisplayName("행동 선택지: 지금 단계의 NPC·행동이 맞을 때만 (다른 단계·다른 NPC·다른 행동은 거부)")
    void action() throws Exception {
        QuestDef q = kingsCall();
        assertTrue(QuestRules.actionAllowed(q, 0, "steward", "choose_class"));
        assertFalse(QuestRules.actionAllowed(q, QuestRules.NOT_STARTED, "steward", "choose_class"));
        assertFalse(QuestRules.actionAllowed(q, 1, "steward", "choose_class"));
        assertFalse(QuestRules.actionAllowed(q, 0, "guard", "choose_class"));
        assertFalse(QuestRules.actionAllowed(q, 0, "steward", "give_gold"));
    }

    @Test
    @DisplayName("신호: 기다리는 신호가 지금 단계일 때만 넘김")
    void signal() throws Exception {
        QuestDef q = kingsCall();
        assertTrue(QuestRules.signalMatches(q, 0, "fantasyrpg:class_chosen"));
        assertFalse(QuestRules.signalMatches(q, 0, "fantasyrpg:other"));
        assertFalse(QuestRules.signalMatches(q, 1, "fantasyrpg:class_chosen"));
        assertFalse(QuestRules.signalMatches(q, QuestRules.NOT_STARTED, "fantasyrpg:class_chosen"));
    }

    @Test
    @DisplayName("구역: 같은 차원, 수평 반경 안, 높이 범위 안")
    void area() throws Exception {
        QuestDef q = kingsCall();
        String k = "magiccity:kingdom";
        assertTrue(QuestRules.areaCompletes(q, 1, k, 260, 47, 300));
        assertFalse(QuestRules.areaCompletes(q, 1, k, 260, 64, 300));          // 지상
        assertFalse(QuestRules.areaCompletes(q, 1, k, 400, 47, 260));          // 반경 밖
        assertFalse(QuestRules.areaCompletes(q, 1, "minecraft:overworld", 260, 47, 260));
        assertFalse(QuestRules.areaCompletes(q, 0, k, 260, 47, 260));          // 아직 그 단계 아님
    }

    @Test
    @DisplayName("챕터 완료·진행 값: 1장 완료로 끝나고 완료 뒤에는 더 넘어가지 않음")
    void chapterAndAdvance() throws Exception {
        QuestDef q = kingsCall();
        assertFalse(QuestRules.chapterCompletes(q, 2, Set.of(2)));
        assertTrue(QuestRules.chapterCompletes(q, 2, Set.of(1)));
        assertEquals(3, QuestRules.advance(q, 2));
        assertTrue(QuestRules.done(q, 3));
        assertEquals(3, QuestRules.advance(q, 3));
        assertEquals(QuestRules.NOT_STARTED, QuestRules.advance(q, QuestRules.NOT_STARTED));
        assertEquals(3, QuestRules.clamp(q, 99));
        assertEquals(-1, QuestRules.clamp(q, -5));
    }

    @Test
    @DisplayName("잘못된 정의는 읽기 거부 (giver 없음, 단계 종류 오류, 구역 반경 없음, 행동에 NPC 없음)")
    void invalid() {
        assertThrows(IllegalArgumentException.class, () -> QuestDef.parse("x", obj("{\"steps\":[{\"type\":\"signal\",\"signal\":\"a\",\"goal\":\"g\"}]}")));
        assertThrows(IllegalArgumentException.class, () -> QuestDef.parse("x", obj("{\"giver\":\"n\",\"steps\":[{\"type\":\"fly\",\"goal\":\"g\"}]}")));
        assertThrows(IllegalArgumentException.class, () -> QuestDef.parse("x", obj("{\"giver\":\"n\",\"steps\":[{\"type\":\"enter_area\",\"goal\":\"g\",\"x\":1,\"z\":1}]}")));
        assertThrows(IllegalArgumentException.class, () -> QuestDef.parse("x", obj("{\"giver\":\"n\",\"steps\":[{\"type\":\"signal\",\"signal\":\"a\",\"goal\":\"g\",\"action\":{\"id\":\"b\"}}]}")));
        assertThrows(IllegalArgumentException.class, () -> QuestDef.parse("x", obj("{\"giver\":\"n\",\"steps\":[]}")));
    }

    private static JsonObject obj(String s) {
        return JsonParser.parseString(s).getAsJsonObject();
    }
}
