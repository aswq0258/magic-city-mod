package io.github.aswq0258.magiccity.quest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 장별 중간 단계 규칙 단위 테스트 (Minecraft 없이 실행).
 * 기준: 프로젝트 문서 claude/장별중간단계_초안.md 2~6절.
 */
class MidStepRulesTest {
    private static final Path QUESTS = Path.of("src/main/resources/data/magiccity/quests");

    private static JsonObject obj(String s) {
        return JsonParser.parseString(s).getAsJsonObject();
    }

    private static QuestDef shipped(String id) throws Exception {
        return QuestDef.parse(id, obj(Files.readString(QUESTS.resolve(id + ".json"), StandardCharsets.UTF_8)));
    }

    /** 장 5 모양: 대화 → 여러 NPC → 도착 → 블록 3개 → 처치 */
    private static QuestDef sample() {
        QuestDef q = QuestDef.parse("t", obj("{\"giver\":\"g\",\"chapter\":5,\"version\":2,\"steps\":["
                + "{\"type\":\"talk_npc\",\"npc\":\"d\",\"goal\":\"a\"},"
                + "{\"type\":\"talk_npcs\",\"npcs\":[\"m1\",\"m2\",\"m3\"],\"goal\":\"b\"},"
                + "{\"type\":\"enter_area\",\"at_chapter\":5,\"radius\":30,\"goal\":\"c\"},"
                + "{\"type\":\"use_blocks\",\"blocks\":[\"v1\",\"v2\",\"v3\"],\"goal\":\"d\"},"
                + "{\"type\":\"chapter_clear\",\"chapter\":5,\"goal\":\"e\"}]}"));
        List<QuestDef.Step> steps = new java.util.ArrayList<>(q.steps());
        steps.set(3, steps.get(3).withBlocks(List.of(new int[]{10, 20, 30}, new int[]{11, 20, 30}, new int[]{12, 20, 30})));
        return q.withSteps(steps);
    }

    @Test
    @DisplayName("여러 NPC 를 아무 순서로: 목록에 있는 NPC 만 표시, 셋 다 만나면 완료, 다른 단계에서는 무시")
    void talkNpcs() {
        QuestDef q = sample();
        assertEquals(-1, QuestRules.talkNpcsMark(q, 1, 0, "stranger"));
        assertEquals(-1, QuestRules.talkNpcsMark(q, 0, 0, "m1"));          // 아직 그 단계가 아님
        int m = QuestRules.talkNpcsMark(q, 1, 0, "m3");
        assertEquals(0b100, m);
        assertFalse(QuestRules.partsDone(q, 1, m));
        m = QuestRules.talkNpcsMark(q, 1, m, "m3");                          // 같은 NPC 다시: 그대로
        assertEquals(0b100, m);
        m = QuestRules.talkNpcsMark(q, 1, QuestRules.talkNpcsMark(q, 1, m, "m1"), "m2");
        assertTrue(QuestRules.partsDone(q, 1, m));
        assertEquals(3, QuestRules.partsCount(m));
        assertEquals(3, q.steps().get(1).parts());
    }

    @Test
    @DisplayName("블록 자리 여러 개: 정확한 좌표만, 아무 순서, 셋 다 쓰면 완료")
    void useBlocks() {
        QuestDef q = sample();
        assertEquals(-1, QuestRules.useBlocksMark(q, 3, 0, 10, 21, 30));     // 한 칸 위는 아님
        assertEquals(-1, QuestRules.useBlocksMark(q, 2, 0, 10, 20, 30));     // 아직 그 단계가 아님
        int m = QuestRules.useBlocksMark(q, 3, 0, 12, 20, 30);
        m = QuestRules.useBlocksMark(q, 3, m, 10, 20, 30);
        assertFalse(QuestRules.partsDone(q, 3, m));
        m = QuestRules.useBlocksMark(q, 3, m, 11, 20, 30);
        assertTrue(QuestRules.partsDone(q, 3, m));
        assertFalse(QuestRules.partsDone(q, 2, m));                          // 부분이 없는 단계는 늘 false
    }

    @Test
    @DisplayName("중간 단계 = 장 퀘스트에서 보스 자리 도착·처치가 아닌 단계 (장 퀘스트가 아니면 없음)")
    void midStep() throws Exception {
        QuestDef q = sample();
        assertTrue(QuestRules.midStep(q, 0));
        assertTrue(QuestRules.midStep(q, 1));
        assertFalse(QuestRules.midStep(q, 2));                              // 보스 자리 도착
        assertTrue(QuestRules.midStep(q, 3));                               // 밸브: 도착 뒤라도 중간 단계
        assertFalse(QuestRules.midStep(q, 4));                              // 처치
        assertFalse(QuestRules.midStep(q, 5));
        QuestDef kings = shipped("kings_call");                             // 1장 왕의 소명은 장 퀘스트 표시가 없음
        for (int i = 0; i < kings.size(); i++) assertFalse(QuestRules.midStep(kings, i));
    }

    @Test
    @DisplayName("안내형: 장을 깨면 진행 중인 장 퀘스트는 남은 단계를 건너뛰고 완료, 받지 않았거나 끝났거나 다른 장이면 그대로")
    void skipOnChapter() {
        QuestDef q = sample();
        assertEquals(5, QuestRules.skipOnChapter(q, 1, Set.of(5)));
        assertEquals(5, QuestRules.skipOnChapter(q, 0, Set.of(1, 5)));
        assertEquals(1, QuestRules.skipOnChapter(q, 1, Set.of(4)));
        assertEquals(QuestRules.NOT_STARTED, QuestRules.skipOnChapter(q, QuestRules.NOT_STARTED, Set.of(5)));
        assertEquals(5, QuestRules.skipOnChapter(q, 5, Set.of(5)));
    }

    @Test
    @DisplayName("판 번호: 진행 중인데 저장된 판이 다르면 처음부터, 받지 않았거나 끝났으면 그대로")
    void version() {
        QuestDef q = sample();
        assertEquals(2, q.version());
        assertTrue(QuestRules.needsRestart(q, 1, 1));
        assertFalse(QuestRules.needsRestart(q, 1, 2));
        assertFalse(QuestRules.needsRestart(q, QuestRules.NOT_STARTED, 1));
        assertFalse(QuestRules.needsRestart(q, 5, 1));
        assertEquals(1, QuestDef.parse("x", obj("{\"giver\":\"g\",\"steps\":[{\"type\":\"signal\",\"signal\":\"s\",\"goal\":\"g\"}]}")).version());
    }

    @Test
    @DisplayName("잘못된 정의 거부: talk_npcs 1명·겹침, use_blocks 비었음·겹침, version 0, mark 만으로 구역 가능")
    void invalid() {
        assertThrows(IllegalArgumentException.class, () -> QuestDef.parse("x", obj("{\"giver\":\"g\",\"steps\":[{\"type\":\"talk_npcs\",\"npcs\":[\"a\"],\"goal\":\"g\"}]}")));
        assertThrows(IllegalArgumentException.class, () -> QuestDef.parse("x", obj("{\"giver\":\"g\",\"steps\":[{\"type\":\"talk_npcs\",\"npcs\":[\"a\",\"a\"],\"goal\":\"g\"}]}")));
        assertThrows(IllegalArgumentException.class, () -> QuestDef.parse("x", obj("{\"giver\":\"g\",\"steps\":[{\"type\":\"use_blocks\",\"goal\":\"g\"}]}")));
        assertThrows(IllegalArgumentException.class, () -> QuestDef.parse("x", obj("{\"giver\":\"g\",\"steps\":[{\"type\":\"use_blocks\",\"blocks\":[\"a\",\"a\"],\"goal\":\"g\"}]}")));
        assertThrows(IllegalArgumentException.class, () -> QuestDef.parse("x", obj("{\"giver\":\"g\",\"version\":0,\"steps\":[{\"type\":\"signal\",\"signal\":\"s\",\"goal\":\"g\"}]}")));
        QuestDef ok = QuestDef.parse("x", obj("{\"giver\":\"g\",\"steps\":[{\"type\":\"enter_area\",\"mark\":\"m\",\"radius\":5,\"goal\":\"g\"}]}"));
        assertTrue(ok.steps().get(0).hasPos());
        assertFalse(ok.steps().get(0).ySet());
    }

    @Test
    @DisplayName("mark 높이 채우기: 단계가 y_min·y_max 를 주지 않았으면 자리의 범위, 주었으면 단계 것")
    void markHeight() {
        QuestDef.Step s = QuestDef.parse("x", obj("{\"giver\":\"g\",\"steps\":[{\"type\":\"enter_area\",\"mark\":\"m\",\"radius\":5,\"goal\":\"g\"}]}")).steps().get(0);
        QuestDef.Step f = s.at(1, 2, -10, 20);
        assertEquals(-10, f.yMin());
        assertEquals(20, f.yMax());
        QuestDef q = QuestDef.parse("x", obj("{\"giver\":\"g\",\"steps\":[{\"type\":\"enter_area\",\"mark\":\"m\",\"radius\":5,\"y_min\":0,\"goal\":\"g\"}]}"));
        QuestDef.Step g = q.steps().get(0).at(1, 2, -10, 20);
        assertEquals(0, g.yMin());
        QuestDef area = q.withSteps(List.of(f));
        assertTrue(QuestRules.areaCompletes(area, 0, "magiccity:kingdom", 1.5, 5, 2.5));
        assertFalse(QuestRules.areaCompletes(area, 0, "magiccity:kingdom", 1.5, 25, 2.5));  // 하늘·지상에서 지나가면 안 됨
    }

    @Test
    @DisplayName("배포 데이터: 2·3·5·6·7·9·10·11장에 중간 단계, 4·8·12장은 그대로, 5장은 문지기 → 광부 3 → 홀 → 밸브 3 → 처치, 바뀐 장은 판 2")
    void shippedMidSteps() throws Exception {
        record Expect(String id, int mids, int version) {}
        for (Expect e : List.of(new Expect("ch02_withering_forest", 1, 2), new Expect("ch03_rotting_roots", 2, 2),
                new Expect("ch04_frost_march", 0, 1), new Expect("ch05_eternal_furnace", 3, 2), new Expect("ch06_sunken_sanctum", 2, 2),
                new Expect("ch07_forgotten_king", 2, 2), new Expect("ch08_border_night", 0, 1), new Expect("ch09_sky_trial", 2, 2),
                new Expect("ch10_abyss_whisper", 1, 2), new Expect("ch11_dragon_flame", 3, 2), new Expect("ch12_demon_gate", 0, 1))) {
            QuestDef q = shipped(e.id());
            int mids = 0;
            for (int i = 0; i < q.size(); i++) if (QuestRules.midStep(q, i)) mids++;
            assertEquals(e.mids(), mids, e.id());
            assertEquals(e.version(), q.version(), e.id());
        }
        QuestDef q5 = shipped("ch05_eternal_furnace");
        assertEquals(List.of(QuestDef.Type.TALK_NPC, QuestDef.Type.TALK_NPCS, QuestDef.Type.ENTER_AREA, QuestDef.Type.USE_BLOCKS, QuestDef.Type.CHAPTER_CLEAR),
                q5.steps().stream().map(QuestDef.Step::type).toList());
        assertEquals("gate_doren", q5.steps().get(0).npc());
        assertEquals(List.of("miner_bruk", "miner_halda", "miner_quin"), q5.steps().get(1).npcs());
        assertEquals("deep_hall", q5.steps().get(2).mark());
        assertEquals(3, q5.steps().get(3).parts());
    }

    @Test
    @DisplayName("배포 데이터가 쓰는 자리 이름·NPC 가 모두 있음 (QuestMarks 자리, quest_npcs 파일)")
    void namesExist() throws Exception {
        String src = Files.readString(Path.of("src/main/java/io/github/aswq0258/magiccity/quest/QuestMarks.java"), StandardCharsets.UTF_8);
        Set<String> marks = new HashSet<>();
        Matcher m = Pattern.compile("case \"([a-z0-9_]+)\"").matcher(src);
        while (m.find()) marks.add(m.group(1));
        Set<String> npcs = new HashSet<>();
        try (var files = Files.list(Path.of("src/main/resources/data/magiccity/quest_npcs"))) {
            for (Path f : files.toList()) npcs.add(f.getFileName().toString().replace(".json", ""));
        }
        try (var files = Files.list(QUESTS)) {
            for (Path f : files.toList()) {
                QuestDef q = QuestDef.parse(f.getFileName().toString().replace(".json", ""), obj(Files.readString(f, StandardCharsets.UTF_8)));
                for (QuestDef.Step s : q.steps()) {
                    if (!s.mark().isEmpty()) assertTrue(marks.contains(s.mark()), q.id() + " 자리 " + s.mark());
                    for (String b : s.blocks()) assertTrue(marks.contains(b), q.id() + " 블록 자리 " + b);
                    for (String n : s.npcs()) assertTrue(npcs.contains(n), q.id() + " NPC " + n);
                    if (!s.npc().isEmpty()) assertTrue(npcs.contains(s.npc()), q.id() + " NPC " + s.npc());
                }
            }
        }
        for (String n : List.of("gate_doren", "miner_bruk", "miner_halda", "miner_quin")) {
            JsonObject o = obj(Files.readString(Path.of("src/main/resources/data/magiccity/quest_npcs/" + n + ".json"), StandardCharsets.UTF_8));
            assertTrue(marks.contains(o.getAsJsonObject("spawn").get("anchor").getAsString()), n);
        }
    }
}
