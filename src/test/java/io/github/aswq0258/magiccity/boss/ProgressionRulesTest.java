package io.github.aswq0258.magiccity.boss;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 진행도 규칙 단위 테스트 (Minecraft 없이 실행).
 * 기준: 프로젝트 통합문서 5절 2026-11-17 결정(자유 진행과 등급, 챕터 진행 규칙, 레벨 상한), 11절 구현 목록.
 */
class ProgressionRulesTest {

    private static int mask(int... chapters) {
        int m = 0;
        for (int c : chapters) m |= 1 << c;
        return m;
    }

    @Test
    @DisplayName("완료 수 → 등급: 0~1개 1급, 2~3개 2급 … 10개 이상 6급, 음수는 0으로")
    void grade() {
        int[] expected = {1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 6};
        for (int n = 0; n <= 12; n++) assertEquals(expected[n], ProgressionRules.grade(n), "완료 " + n);
        assertEquals(6, ProgressionRules.grade(100));
        assertEquals(1, ProgressionRules.grade(-3));
    }

    @Test
    @DisplayName("레벨 상한 = 등급 × 10 (Lv.10 ~ Lv.60)")
    void levelCap() {
        assertEquals(10, ProgressionRules.levelCap(0));
        assertEquals(10, ProgressionRules.levelCap(1));
        assertEquals(20, ProgressionRules.levelCap(2));
        assertEquals(50, ProgressionRules.levelCap(9));
        assertEquals(60, ProgressionRules.levelCap(10));
        assertEquals(60, ProgressionRules.levelCap(12));
    }

    @Test
    @DisplayName("작업대: 2개 완료 4×4, 6개 완료 5×5")
    void craftGrid() {
        assertEquals(3, ProgressionRules.craftGrid(0));
        assertEquals(3, ProgressionRules.craftGrid(1));
        assertEquals(4, ProgressionRules.craftGrid(2));
        assertEquals(4, ProgressionRules.craftGrid(5));
        assertEquals(5, ProgressionRules.craftGrid(6));
        assertEquals(5, ProgressionRules.craftGrid(12));
    }

    @Test
    @DisplayName("파티 등급 차 경고: 보스 등급 − 파티원 등급 ≥ 기준이면 경고, 기준 0이면 끔")
    void gradeGapWarn() {
        assertTrue(ProgressionRules.gradeGapWarn(4, 2, 2));
        assertFalse(ProgressionRules.gradeGapWarn(4, 3, 2));
        assertFalse(ProgressionRules.gradeGapWarn(6, 1, 0));
        assertTrue(ProgressionRules.gradeGapWarn(6, 5, 1));
    }

    @Test
    @DisplayName("보스 등급: 1~10장은 파티 최고 진행도, 11·12장은 6급 고정")
    void bossGrade() {
        assertEquals(1, ProgressionRules.bossGrade(1, 0));
        assertEquals(3, ProgressionRules.bossGrade(1, 4));
        assertEquals(6, ProgressionRules.bossGrade(10, 11));
        assertEquals(6, ProgressionRules.bossGrade(11, 0));
        assertEquals(6, ProgressionRules.bossGrade(12, 3));
    }

    @Test
    @DisplayName("보스 피해 배율: 근접 설계값이 등급별 기본 공격(8/11/14/15/17/18)이 되게, 범위 밖 등급은 양 끝, 설계값 0 이하는 1.0")
    void damageScale() {
        assertEquals(8.0 / 4.0, ProgressionRules.damageScale(1, 4.0), 1e-12);
        assertEquals(18.0 / 6.0, ProgressionRules.damageScale(6, 6.0), 1e-12);
        assertEquals(8.0 / 4.0, ProgressionRules.damageScale(0, 4.0), 1e-12);
        assertEquals(18.0 / 6.0, ProgressionRules.damageScale(9, 6.0), 1e-12);
        assertEquals(1.0, ProgressionRules.damageScale(3, 0.0), 1e-12);
        double[] basic = {8, 11, 14, 15, 17, 18};
        for (int g = 1; g <= 6; g++) assertEquals(basic[g - 1], ProgressionRules.damageScale(g, 10.0) * 10.0, 1e-9);
    }

    @Test
    @DisplayName("33% 두 번째 부하 소환: 1·2급 없음, 3급부터, 등급 기록 없는 보스(0)는 기존대로")
    void secondSummon() {
        assertFalse(ProgressionRules.secondSummon(1));
        assertFalse(ProgressionRules.secondSummon(2));
        assertTrue(ProgressionRules.secondSummon(3));
        assertTrue(ProgressionRules.secondSummon(6));
        assertTrue(ProgressionRules.secondSummon(0));
    }

    @Test
    @DisplayName("11장: 1~10장 모두 완료 + 봉인 5개")
    void chapter11() {
        int all10 = mask(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        assertTrue(ProgressionRules.chapter11Open(all10, 5));
        assertFalse(ProgressionRules.chapter11Open(all10, 4));
        assertFalse(ProgressionRules.chapter11Open(mask(1, 2, 3, 4, 5, 6, 7, 8, 9), 5));
        assertFalse(ProgressionRules.chapter11Open(mask(2, 3, 4, 5, 6, 7, 8, 9, 10), 5));
    }

    @Test
    @DisplayName("2~10장: 서장 완료 필요, 서장 규칙 이전에 다른 장을 깬 저장 데이터는 막지 않음")
    void freeChapters() {
        assertFalse(ProgressionRules.freeChapterOpen(0));
        assertTrue(ProgressionRules.freeChapterOpen(mask(1)));
        assertTrue(ProgressionRules.freeChapterOpen(mask(4)));   // 이전 저장 데이터 호환
        assertFalse(ProgressionRules.freeChapterOpen(1));        // 비트 0은 챕터가 아님
    }

    @Test
    @DisplayName("완료 수: 1~12장 비트만 센다")
    void completed() {
        assertEquals(0, ProgressionRules.completed(0));
        assertEquals(3, ProgressionRules.completed(mask(1, 5, 12)));
        assertEquals(0, ProgressionRules.completed(1 | (1 << 13)));
        assertEquals(12, ProgressionRules.completed(mask(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12)));
    }

    @org.junit.jupiter.api.Test
    @org.junit.jupiter.api.DisplayName("외부 보스의 방어구 무시 공격만 등급 배율을 건너뜀 (라디키온 낙하·돌진·균열)")
    void skipScalingRule() {
        org.junit.jupiter.api.Assertions.assertTrue(ProgressionRules.skipScaling(true, true));
        org.junit.jupiter.api.Assertions.assertFalse(ProgressionRules.skipScaling(true, false));
        org.junit.jupiter.api.Assertions.assertFalse(ProgressionRules.skipScaling(false, true));
    }
}
