package io.github.aswq0258.magiccity.boss;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.aswq0258.magiccity.boss.BossDataRules.Grades;
import io.github.aswq0258.magiccity.boss.BossDataRules.Link;
import io.github.aswq0258.magiccity.boss.BossDataRules.Stats;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 보스 수치·외부 보스 연결 데이터 (claude/외부보스연결_초안.md 2절·4절) */
class BossDataRulesTest {
    private static JsonObject j(String s) {
        return JsonParser.parseString(s).getAsJsonObject();
    }

    @Test
    @DisplayName("능력치: 적힌 필드만, null 은 덮어쓰지 않음, 탈것은 챕터 보스만")
    void stats() {
        Stats s = BossDataRules.parseStats(j("{\"scale\":1.7,\"health\":320,\"attack\":14,\"armor\":12,\"speed\":null,"
                + "\"mount\":{\"scale\":1.3,\"health\":160,\"attack\":12,\"armor\":8}}"), true);
        assertEquals(1.7, (double) s.scale());
        assertEquals(320.0, (double) s.health());
        assertNull(s.speed());
        assertNull(s.knockback());
        assertEquals(4, s.count());
        assertEquals(160.0, (double) s.mount().health());
        Stats part = BossDataRules.parseStats(j("{\"health\":999}"), true);
        assertEquals(1, part.count());
        assertNull(part.scale());
        assertNull(part.mount());
        // 필드보스에는 탈것 없음
        assertThrows(IllegalArgumentException.class, () -> BossDataRules.parseStats(j("{\"mount\":{}}"), false));
    }

    @Test
    @DisplayName("능력치: 범위 밖·모르는 항목은 파일 거부")
    void statsReject() {
        assertThrows(IllegalArgumentException.class, () -> BossDataRules.parseStats(j("{\"health\":0}"), true));
        assertThrows(IllegalArgumentException.class, () -> BossDataRules.parseStats(j("{\"health\":10001}"), true));
        assertThrows(IllegalArgumentException.class, () -> BossDataRules.parseStats(j("{\"attack\":201}"), true));
        assertThrows(IllegalArgumentException.class, () -> BossDataRules.parseStats(j("{\"armor\":31}"), true));
        assertThrows(IllegalArgumentException.class, () -> BossDataRules.parseStats(j("{\"knockback_resistance\":1.5}"), true));
        assertThrows(IllegalArgumentException.class, () -> BossDataRules.parseStats(j("{\"hp\":100}"), true));
        assertThrows(IllegalArgumentException.class, () -> BossDataRules.parseStats(j("{\"mount\":{\"health\":-1}}"), true));
        assertEquals(10000.0, (double) BossDataRules.parseStats(j("{\"health\":10000}"), true).health());
    }

    @Test
    @DisplayName("오르는 높이: 크기와 지금 값 중 큰 쪽, 최소 1")
    void step() {
        assertEquals(3.0, BossDataRules.stepHeight(3.0, 2.2));
        assertEquals(7.0, BossDataRules.stepHeight(0.6, 7));
        assertEquals(1.0, BossDataRules.stepHeight(0.6, 0.5));
    }

    @Test
    @DisplayName("등급표: 기본값 = 지금 규칙(8/11/14/15/17/18, 체력 1.0), 일부만 쓰면 나머지 기본, 6개 아니면 거부")
    void grades() {
        Grades d = Grades.DEFAULT;
        assertTrue(java.util.Arrays.equals(new double[]{8, 11, 14, 15, 17, 18}, d.basic()));
        assertEquals(ProgressionRules.damageScale(3, 9), d.damageScale(3, 9), 1e-9);
        assertEquals(1.0, d.damageScale(3, 0));
        assertEquals(18, d.basic(9));
        assertEquals(8, d.basic(0));
        Grades g = BossDataRules.parseGrades(j("{\"hp_scale\":[1,1.2,1.4,1.6,1.8,2]}"));
        assertTrue(java.util.Arrays.equals(d.basic(), g.basic()));
        assertEquals(1.4, g.hpScale(3));
        assertThrows(IllegalArgumentException.class, () -> BossDataRules.parseGrades(j("{\"basic\":[1,2,3]}")));
        assertThrows(IllegalArgumentException.class, () -> BossDataRules.parseGrades(j("{\"hp_scale\":[1,1,1,1,1,0]}")));
        assertThrows(IllegalArgumentException.class, () -> BossDataRules.parseGrades(j("{\"boss_basic\":[8,11,14,15,17,18]}")));
    }

    @Test
    @DisplayName("외부 보스 연결: 기본은 우리 기술·체력바 끔, 등급 배율 켬. 모드가 없거나 꺼 두면 기본 보스")
    void link() {
        Link l = BossDataRules.parseLink(j("{\"entity\":\"fantasybosses:radicion\",\"anchor_nbt\":\"RadicionAnchor\"}"));
        assertTrue(l.enabled());
        assertFalse(l.ourSkills());
        assertFalse(l.ourBossbar());
        assertTrue(l.gradeScaling());
        assertEquals("RadicionAnchor", l.anchorNbt());
        assertNull(l.nbt());
        assertTrue(BossDataRules.useExternal(l, true));
        assertFalse(BossDataRules.useExternal(l, false));
        assertFalse(BossDataRules.useExternal(null, true));
        Link off = BossDataRules.parseLink(j("{\"enabled\":false,\"entity\":\"fantasybosses:radicion\",\"nbt\":\"{Foo:1b}\"}"));
        assertFalse(BossDataRules.useExternal(off, true));
        assertEquals("{Foo:1b}", off.nbt().getAsString());
        assertTrue(BossDataRules.parseLink(j("{\"entity\":\"a:b\",\"nbt\":{\"X\":1}}")).nbt().isJsonObject());
    }

    @Test
    @DisplayName("v0.8 연결: 이동량 NBT·등급 배율 기준·우리 고정 피해 끄기, 이동량 = 전장 − 왕국 대륙")
    void linkV08() {
        Link l = BossDataRules.parseLink(j("{\"entity\":\"fantasybosses:radicion\",\"offset_nbt\":\"RadicionOffset\",\"design_attack\":6,\"our_patterns\":false}"));
        assertEquals("RadicionOffset", l.offsetNbt());
        assertEquals(6.0, (double) l.designAttack());
        assertFalse(l.ourPatterns());
        assertTrue(BossDataRules.parseLink(j("{\"entity\":\"a:b\"}")).ourPatterns());
        assertNull(BossDataRules.parseLink(j("{\"entity\":\"a:b\"}")).designAttack());
        assertEquals(6.0, BossDataRules.designBasic(l, 0));
        assertEquals(9.0, BossDataRules.designBasic(null, 9));
        // 라디키온 1페이즈 턱 강타 6 기준: 1급 8/6, 6급 18/6 = 3배
        assertEquals(3.0, Grades.DEFAULT.damageScale(6, BossDataRules.designBasic(l, 0)), 1e-9);
        assertEquals(8.0 / 6, Grades.DEFAULT.damageScale(1, BossDataRules.designBasic(l, 0)), 1e-9);
        int[] o = BossDataRules.offset(3000, 64, 0, -2860, 9, -860);
        assertEquals(5860, o[0]);
        assertEquals(55, o[1]);
        assertEquals(860, o[2]);
        assertThrows(IllegalArgumentException.class, () -> BossDataRules.parseLink(j("{\"entity\":\"a:b\",\"design_attack\":0}")));
        assertThrows(IllegalArgumentException.class, () -> BossDataRules.parseLink(j("{\"entity\":\"a:b\",\"offset_nbt\":\"a-b\"}")));
    }

    @Test
    @DisplayName("외부 보스 연결: 잘못된 id·NBT·기준점 이름·모르는 항목 거부")
    void linkReject() {
        assertThrows(IllegalArgumentException.class, () -> BossDataRules.parseLink(j("{}")));
        assertThrows(IllegalArgumentException.class, () -> BossDataRules.parseLink(j("{\"entity\":\"radicion\"}")));
        assertThrows(IllegalArgumentException.class, () -> BossDataRules.parseLink(j("{\"entity\":\"Fantasy:Radicion\"}")));
        assertThrows(IllegalArgumentException.class, () -> BossDataRules.parseLink(j("{\"entity\":\"a:b\",\"nbt\":5}")));
        assertThrows(IllegalArgumentException.class, () -> BossDataRules.parseLink(j("{\"entity\":\"a:b\",\"anchor_nbt\":\"a b\"}")));
        assertThrows(IllegalArgumentException.class, () -> BossDataRules.parseLink(j("{\"entity\":\"a:b\",\"skills\":true}")));
    }
}
