package io.github.aswq0258.magiccity.boss;

/**
 * 진행도 규칙 (순수 계산, 마인크래프트 비의존 — 단위 검증용).
 * 기준: 프로젝트 문서 "방어구_전투밸런스_기준_v1.0" 2절·6절, 통합문서 5절 (2026-11-17 진행도 결정).
 *
 *  - 등급은 장 번호가 아니라 "처음 완료한 메인 챕터 수"로 정한다.
 *  - 0~1개 1급, 2~3개 2급, 4~5개 3급, 6~7개 4급, 8~9개 5급, 10개 이상 6급. 11·12장 보스는 6급 고정.
 *  - 레벨 상한 = 등급 × 10 (Lv.10 ~ Lv.60).
 *  - 작업대: 2개 완료 → 4×4, 6개 완료 → 5×5.
 */
public final class ProgressionRules {
    private ProgressionRules() {}

    public static final int MAX_GRADE = 6;

    /** 등급별 보스 기본 공격 원본 피해 [초안] (인챈트 없음·보통 난이도 기준) */
    public static final double[] BOSS_BASIC = {8, 11, 14, 15, 17, 18};
    /** 등급별 보스 강공 원본 피해 [초안] — 3·4급 20/21은 [확정] */
    public static final double[] BOSS_STRONG = {12, 15, 20, 21, 24, 25};

    /** 완료한 메인 챕터 수 → 등급 (1~6) */
    public static int grade(int completed) {
        if (completed < 0) completed = 0;
        return Math.min(MAX_GRADE, completed / 2 + 1);
    }

    /** 챕터 보스의 등급: 11·12장은 6급 고정, 나머지는 파티 최고 진행도 */
    public static int bossGrade(int chapter, int partyMaxCompleted) {
        if (chapter >= 11) return MAX_GRADE;
        return grade(partyMaxCompleted);
    }

    /** 레벨 상한 (Lv.10 ~ Lv.60) */
    public static int levelCap(int completed) {
        return grade(completed) * 10;
    }

    /** 쓸 수 있는 작업대 크기 (3, 4, 5) */
    public static int craftGrid(int completed) {
        if (completed >= 6) return 5;
        if (completed >= 2) return 4;
        return 3;
    }

    /**
     * 보스가 주는 모든 피해에 곱할 배율. 보스의 근접 공격(설계값 designBasic)이 그 등급의 "보스 기본 공격"이 되게 하고,
     * 고유 기술 피해는 근접 공격과의 비율을 그대로 유지한다.
     */
    public static double damageScale(int grade, double designBasic) {
        if (designBasic <= 0) return 1.0;
        int g = Math.max(1, Math.min(MAX_GRADE, grade));
        return BOSS_BASIC[g - 1] / designBasic;
    }

    /** 패턴 복잡도: 33% 부하 소환(두 번째 소환)은 3급부터. 등급 기록이 없는 보스(0)는 기존대로 소환 */
    public static boolean secondSummon(int grade) {
        return grade == 0 || grade >= 3;
    }

    /** 11장 입장 조건: 1~10장 모두 완료 + 봉인 5개 */
    public static boolean chapter11Open(int clearedMask, int seals) {
        for (int c = 1; c <= 10; c++) if ((clearedMask & (1 << c)) == 0) return false;
        return seals >= 5;
    }

    /**
     * 2~10장 입장 조건: 1장(서장) 완료.
     * 이전 저장 데이터 호환: 서장 규칙 전에 이미 다른 챕터를 완료한 플레이어는 막지 않는다.
     */
    public static boolean freeChapterOpen(int clearedMask) {
        if ((clearedMask & (1 << 1)) != 0) return true;
        for (int c = 2; c <= 12; c++) if ((clearedMask & (1 << c)) != 0) return true;
        return false;
    }

    /** 완료한 메인 챕터 수 (1~12장) */
    public static int completed(int clearedMask) {
        int n = 0;
        for (int c = 1; c <= 12; c++) if ((clearedMask & (1 << c)) != 0) n++;
        return n;
    }
}
