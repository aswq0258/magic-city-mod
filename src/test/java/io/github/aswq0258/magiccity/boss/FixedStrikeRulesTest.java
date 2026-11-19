package io.github.aswq0258.magiccity.boss;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.aswq0258.magiccity.boss.FixedStrikeRules.Pattern;
import io.github.aswq0258.magiccity.boss.FixedStrikeRules.Shape;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 보스 고정 피해 패턴 규칙 단위 테스트 (Minecraft 없이 실행).
 * 기준: 프로젝트 문서 claude/보스고정피해_초안.md 4~6절, 밸런스 문서 6-1절.
 */
class FixedStrikeRulesTest {
    private static final Path DIR = Path.of("src/main/resources/data/magiccity/boss_patterns");

    private static Pattern p(Shape s, double r, double in, double out, double pct, double flat, int cd) {
        return new Pattern("magiccity_t", "시험", s, r, in, out, pct, flat, cd, "");
    }

    private static List<Pattern> shipped() throws Exception {
        List<Pattern> out = new ArrayList<>();
        try (Stream<Path> files = Files.list(DIR)) {
            for (Path f : files.sorted().toList()) {
                String id = f.getFileName().toString().replace(".json", "");
                JsonObject o = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
                out.add(FixedStrikeRules.parse(id, o));
            }
        }
        return out;
    }

    @Test
    @DisplayName("비율형은 등급과 무관하게 최대 체력 × 비율")
    void percentIgnoresGrade() {
        Pattern pat = p(Shape.AROUND_BOSS, 5, 0, 0, 0.25, 0, 500);
        assertEquals(5.0f, FixedStrikeRules.amount(pat, 20, 1), 1e-6);
        assertEquals(5.0f, FixedStrikeRules.amount(pat, 20, 6), 1e-6);
        assertEquals(8.0f, FixedStrikeRules.amount(pat, 32, 3), 1e-6); // 체력을 쌓아도 같은 비율
    }

    @Test
    @DisplayName("고정형은 6급 기준값 × 등급 계수 (1급 0.5 ~ 6급 1.0, 그 위는 1.0)")
    void flatScalesByGrade() {
        Pattern pat = p(Shape.AROUND_BOSS, 5, 0, 0, 0, 8, 500);
        assertEquals(4.0f, FixedStrikeRules.amount(pat, 20, 1), 1e-6);
        assertEquals(5.6f, FixedStrikeRules.amount(pat, 20, 3), 1e-5);
        assertEquals(8.0f, FixedStrikeRules.amount(pat, 20, 6), 1e-6);
        assertEquals(8.0f, FixedStrikeRules.amount(pat, 40, 6), 1e-6); // 최대 체력과 무관
        assertEquals(8.0f, FixedStrikeRules.amount(pat, 20, 9), 1e-6);
        assertEquals(4.0f, FixedStrikeRules.amount(pat, 20, 0), 1e-6);
    }

    @Test
    @DisplayName("예고 시간 1·2급 60틱, 3급 이상 40틱 / 광폭화 쿨타임 ×0.8")
    void telegraphAndCooldown() {
        assertEquals(60, FixedStrikeRules.telegraph(1));
        assertEquals(60, FixedStrikeRules.telegraph(2));
        assertEquals(40, FixedStrikeRules.telegraph(3));
        assertEquals(40, FixedStrikeRules.telegraph(6));
        Pattern pat = p(Shape.AROUND_BOSS, 5, 0, 0, 0.2, 0, 500);
        assertEquals(500, FixedStrikeRules.cooldown(pat, false));
        assertEquals(400, FixedStrikeRules.cooldown(pat, true));
    }

    @Test
    @DisplayName("범위 판정: 원형은 안이면 맞고, 고리는 안쪽·바깥이 안전, 높이 차를 넘으면 안전")
    void hitShapes() {
        Pattern around = p(Shape.AROUND_BOSS, 7, 0, 0, 0.2, 0, 500);
        assertTrue(FixedStrikeRules.hits(around, 6.9, 0, 0));
        assertTrue(FixedStrikeRules.hits(around, 7, 0, 0)); // 경계는 맞음
        assertFalse(FixedStrikeRules.hits(around, 5.1, 0, 5.1));
        assertFalse(FixedStrikeRules.hits(around, 1, 6.5, 0));
        assertTrue(FixedStrikeRules.hits(around, 1, -6, 0));

        Pattern point = p(Shape.AT_PLAYERS, 3, 0, 0, 0.2, 0, 500);
        assertTrue(FixedStrikeRules.hits(point, 0, 0, 0)); // 제자리에 있으면 맞음
        assertFalse(FixedStrikeRules.hits(point, 3.1, 0, 0));
        assertFalse(FixedStrikeRules.hits(point, 0, 4.5, 0)); // 지점 원형 높이 한도 4

        Pattern ring = p(Shape.RING, 0, 5, 16, 0.25, 0, 500);
        assertFalse(FixedStrikeRules.hits(ring, 4.9, 0, 0)); // 보스에게 붙으면 안전
        assertTrue(FixedStrikeRules.hits(ring, 5, 0, 0));
        assertTrue(FixedStrikeRules.hits(ring, 10, 0, 10));
        assertFalse(FixedStrikeRules.hits(ring, 16.1, 0, 0));
    }

    @Test
    @DisplayName("데이터 검사: 비율 ≤ 50%, 고정 ≤ 14, 둘 중 하나만, 모양별 크기, 쿨타임 하한")
    void validation() {
        assertTrue(FixedStrikeRules.validate(p(Shape.AROUND_BOSS, 5, 0, 0, 0.5, 0, 100)).isEmpty());
        assertFalse(FixedStrikeRules.validate(p(Shape.AROUND_BOSS, 5, 0, 0, 0.51, 0, 500)).isEmpty());
        assertFalse(FixedStrikeRules.validate(p(Shape.AROUND_BOSS, 5, 0, 0, 0, 14.5, 500)).isEmpty());
        assertFalse(FixedStrikeRules.validate(p(Shape.AROUND_BOSS, 5, 0, 0, 0.2, 5, 500)).isEmpty());
        assertFalse(FixedStrikeRules.validate(p(Shape.AROUND_BOSS, 5, 0, 0, 0, 0, 500)).isEmpty());
        assertFalse(FixedStrikeRules.validate(p(Shape.AT_PLAYERS, 0, 0, 0, 0.2, 0, 500)).isEmpty());
        assertFalse(FixedStrikeRules.validate(p(Shape.RING, 0, 8, 8, 0.2, 0, 500)).isEmpty());
        assertFalse(FixedStrikeRules.validate(p(Shape.AROUND_BOSS, 5, 0, 0, 0.2, 0, 99)).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> FixedStrikeRules.parse("x", JsonParser.parseString("{\"shape\":\"square\"}").getAsJsonObject()));
    }

    @Test
    @DisplayName("배포 데이터: 12장 보스 12개 모두 검사 통과, 비율형 7·고정형 5")
    void shippedData() throws Exception {
        List<Pattern> all = shipped();
        Set<String> bosses = new TreeSet<>();
        int pct = 0, flat = 0;
        for (Pattern pat : all) {
            assertTrue(FixedStrikeRules.validate(pat).isEmpty(), pat.boss() + " " + FixedStrikeRules.validate(pat));
            assertFalse(pat.warn().isBlank(), pat.boss() + " 경고 문구 없음");
            bosses.add(pat.boss());
            if (pat.percent() > 0) pct++;
            else flat++;
        }
        assertEquals(Set.of("magiccity_plague", "magiccity_cursedboss", "magiccity_parasite", "magiccity_frost", "magiccity_furnace",
                "magiccity_deep", "magiccity_tombboss", "magiccity_grimbald", "magiccity_skyboss", "magiccity_watcher",
                "magiccity_dragon", "magiccity_gatekeeper"), bosses);
        assertEquals(7, pct);
        assertEquals(5, flat);
    }
}
