package io.github.aswq0258.magiccity.boss;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 보스 고정 피해 패턴 규칙 (순수 계산, Minecraft 없이 테스트).
 * 기준: 프로젝트 문서 claude/보스고정피해_초안.md, 밸런스 문서 6-1절.
 *
 * <p>예고(바닥 범위) → 발동 순간 범위 안이면 모든 감소를 무시하는 피해, 범위 밖이면 0.
 */
public final class FixedStrikeRules {
    private FixedStrikeRules() {}

    /** 모양: 보스 원형(멀어지면 안전), 지점 원형(찍힌 자리에서 벗어나면 안전), 고리(보스에게 붙으면 안전) */
    public enum Shape { AROUND_BOSS, AT_PLAYERS, RING }

    /** 고정 수치형 등급 계수 [초안]: 1급 0.5 … 6급 1.0, 7급 이상은 1.0 */
    static final double[] GRADE_FACTOR = {0.5, 0.6, 0.7, 0.8, 0.9, 1.0};
    /** 예고 시간: 기본 40틱, 1·2급 60틱 [초안] */
    public static final int TELEGRAPH = 40, TELEGRAPH_LOW = 60;
    /** 깨어난 뒤 첫 사용까지 */
    public static final int FIRST_DELAY = 200;
    /** 광폭화 뒤 쿨타임 배율 */
    public static final double ENRAGE_COOLDOWN = 0.8;
    /** 판정 높이 차: 지점 원형 4, 보스 중심 모양 6 */
    public static final double VERT_POINT = 4, VERT_BOSS = 6;
    /** 데이터 검사 한도 [초안] */
    public static final double MAX_PERCENT = 0.5, MAX_FLAT = 14;
    /** 지점 원형이 한 번에 찍는 최대 지점 수 */
    public static final int MAX_POINTS = 6;

    public record Pattern(String boss, String name, Shape shape, double radius, double inner, double outer,
                          double percent, double flat, int cooldown, String warn) {}

    /** 고정 수치형 등급 계수 */
    public static double gradeFactor(int grade) {
        int g = Math.max(1, Math.min(GRADE_FACTOR.length, grade));
        return GRADE_FACTOR[g - 1];
    }

    /** 맞는 피해: 비율형은 최대 체력 × 비율(등급 무관), 고정형은 6급 기준값 × 등급 계수 */
    public static float amount(Pattern p, float maxHealth, int grade) {
        double v = maxHealth * p.percent() + p.flat() * gradeFactor(grade);
        return (float) Math.max(0, v);
    }

    public static int telegraph(int grade) {
        return grade <= 2 ? TELEGRAPH_LOW : TELEGRAPH;
    }

    public static int cooldown(Pattern p, boolean enraged) {
        return enraged ? (int) Math.round(p.cooldown() * ENRAGE_COOLDOWN) : p.cooldown();
    }

    /**
     * 발동 순간 범위 안인지. dx·dz·dy 는 판정 중심(보스 원형·고리는 예고 시작 때 보스 자리, 지점 원형은 찍힌 지점)에서 플레이어까지.
     */
    public static boolean hits(Pattern p, double dx, double dy, double dz) {
        double d2 = dx * dx + dz * dz;
        return switch (p.shape()) {
            case AROUND_BOSS -> Math.abs(dy) <= VERT_BOSS && d2 <= p.radius() * p.radius();
            case AT_PLAYERS -> Math.abs(dy) <= VERT_POINT && d2 <= p.radius() * p.radius();
            case RING -> Math.abs(dy) <= VERT_BOSS && d2 >= p.inner() * p.inner() && d2 <= p.outer() * p.outer();
        };
    }

    /** 데이터 검사. 빈 목록이면 정상 */
    public static List<String> validate(Pattern p) {
        List<String> e = new ArrayList<>();
        if (p.name() == null || p.name().isBlank()) e.add("name 없음");
        boolean pct = p.percent() > 0, flat = p.flat() > 0;
        if (pct == flat) e.add("percent·flat 중 정확히 하나만 0보다 커야 함");
        if (p.percent() > MAX_PERCENT) e.add("percent > " + MAX_PERCENT);
        if (p.flat() > MAX_FLAT) e.add("flat > " + MAX_FLAT);
        if (p.shape() == Shape.RING) {
            if (!(p.inner() > 0 && p.outer() > p.inner())) e.add("ring 은 0 < inner < outer");
        } else if (!(p.radius() > 0)) e.add("radius 는 0보다 커야 함");
        if (p.cooldown() < TELEGRAPH_LOW + 40) e.add("cooldown 이 너무 짧음 (예고 + 2초 이상)");
        return e;
    }

    /** 데이터 파일 읽기. 보스 태그 = "magiccity_" + 파일 이름 */
    public static Pattern parse(String file, JsonObject o) {
        String shapeName = o.has("shape") ? o.get("shape").getAsString().toUpperCase(Locale.ROOT) : "";
        Shape shape;
        try {
            shape = Shape.valueOf(shapeName);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("shape 은 around_boss·at_players·ring 중 하나: " + shapeName);
        }
        return new Pattern("magiccity_" + file,
                o.has("name") ? o.get("name").getAsString() : "",
                shape,
                num(o, "radius"), num(o, "inner"), num(o, "outer"),
                num(o, "percent"), num(o, "flat"),
                (int) Math.round(num(o, "cooldown_seconds") * 20),
                o.has("warn") ? o.get("warn").getAsString() : "");
    }

    private static double num(JsonObject o, String k) {
        return o.has(k) ? o.get(k).getAsDouble() : 0;
    }
}
