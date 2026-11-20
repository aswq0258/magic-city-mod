package io.github.aswq0258.magiccity.boss;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Map;
import java.util.Set;

/**
 * 보스 수치·외부 보스 연결 데이터 규칙 (순수 계산, 마인크래프트 비의존 — 단위 검증용).
 * 기준: 프로젝트 문서 claude/외부보스연결_초안.md 2절·4절 (2026-11-20 사용자 결정 A~D 추천안).
 *
 * <ul>
 *   <li>{@code data/magiccity/boss_stats/<보스 키>.json}: 챕터 보스 기본 능력치. 적힌 필드만 덮어쓰고 빠진 필드는 코드 기본값.</li>
 *   <li>{@code data/magiccity/boss_stats/_grades.json}: 등급 1~6 보스 기본 공격·체력 배율.</li>
 *   <li>{@code data/magiccity/field_boss_stats/<필드보스 키>.json}: 필드보스 능력치.</li>
 *   <li>{@code data/magiccity/boss_entity/<보스 키>.json}: 그 장 보스를 외부 모드 몹으로 바꿈.</li>
 * </ul>
 * 범위 밖 값이나 모르는 키가 있으면 그 파일 전체를 거부한다 (오류 기록 → 기본값).
 */
public final class BossDataRules {
    private BossDataRules() {}

    /** 능력치 (null = 덮어쓰지 않음). mount 는 챕터 보스 8장 탈것처럼 보스가 타는 몹 */
    public record Stats(Double scale, Double health, Double attack, Double armor, Double knockback, Double follow, Double speed, Stats mount) {
        public static final Stats NONE = new Stats(null, null, null, null, null, null, null, null);

        /** 덮어쓰는 필드 수 (탈것 제외) */
        public int count() {
            int n = 0;
            for (Double d : new Double[]{scale, health, attack, armor, knockback, follow, speed}) if (d != null) n++;
            return n;
        }
    }

    /** 필드 이름 → [최소, 최대] */
    static final Map<String, double[]> RANGES = Map.of(
            "scale", new double[]{0.0625, 16},
            "health", new double[]{1, 10000},
            "attack", new double[]{0, 200},
            "armor", new double[]{0, 30},
            "knockback_resistance", new double[]{0, 1},
            "follow_range", new double[]{1, 256},
            "speed", new double[]{0, 2});

    public static Stats parseStats(JsonObject o, boolean allowMount) {
        for (String k : o.keySet())
            if (!RANGES.containsKey(k) && !(allowMount && k.equals("mount")))
                throw new IllegalArgumentException("모르는 항목 '" + k + "'");
        Stats mount = null;
        if (allowMount && o.has("mount") && !o.get("mount").isJsonNull()) mount = parseStats(o.getAsJsonObject("mount"), false);
        return new Stats(num(o, "scale"), num(o, "health"), num(o, "attack"), num(o, "armor"),
                num(o, "knockback_resistance"), num(o, "follow_range"), num(o, "speed"), mount);
    }

    private static Double num(JsonObject o, String k) {
        if (!o.has(k) || o.get(k).isJsonNull()) return null;
        double v = o.get(k).getAsDouble();
        double[] r = RANGES.get(k);
        if (Double.isNaN(v) || v < r[0] || v > r[1]) throw new IllegalArgumentException(k + " = " + v + " (허용 " + r[0] + "~" + r[1] + ")");
        return v;
    }

    /** 오르는 높이: 지금 값과 크기 중 큰 쪽 (크기를 키우면 블록을 넘을 수 있게, 원래 더 높으면 그대로) */
    public static double stepHeight(double current, double scale) {
        return Math.max(Math.max(1, current), scale);
    }

    // ── 등급표

    /** 등급 1~6 의 보스 기본 공격 원본 피해와 최대 체력 배율 */
    public record Grades(double[] basic, double[] hpScale) {
        public static final Grades DEFAULT = new Grades(ProgressionRules.BOSS_BASIC.clone(), new double[]{1, 1, 1, 1, 1, 1});

        public double basic(int grade) {
            return basic[clamp(grade) - 1];
        }

        public double hpScale(int grade) {
            return hpScale[clamp(grade) - 1];
        }

        private static int clamp(int g) {
            return Math.max(1, Math.min(ProgressionRules.MAX_GRADE, g));
        }

        /** 이 등급표에서의 피해 배율 (보스 근접 설계값 → 그 등급의 기본 공격) */
        public double damageScale(int grade, double designBasic) {
            return designBasic > 0 ? basic(grade) / designBasic : 1.0;
        }
    }

    public static Grades parseGrades(JsonObject o) {
        for (String k : o.keySet())
            if (!k.equals("basic") && !k.equals("hp_scale")) throw new IllegalArgumentException("모르는 항목 '" + k + "'");
        double[] basic = o.has("basic") ? six(o.getAsJsonArray("basic"), "basic", 0, 200) : Grades.DEFAULT.basic().clone();
        double[] hp = o.has("hp_scale") ? six(o.getAsJsonArray("hp_scale"), "hp_scale", 0.1, 20) : Grades.DEFAULT.hpScale().clone();
        return new Grades(basic, hp);
    }

    private static double[] six(JsonArray a, String k, double min, double max) {
        if (a.size() != 6) throw new IllegalArgumentException(k + " 는 등급 1~6 의 값 6개여야 함 (지금 " + a.size() + "개)");
        double[] out = new double[6];
        for (int i = 0; i < 6; i++) {
            out[i] = a.get(i).getAsDouble();
            if (Double.isNaN(out[i]) || out[i] < min || out[i] > max)
                throw new IllegalArgumentException(k + "[" + (i + 1) + "급] = " + out[i] + " (허용 " + min + "~" + max + ")");
        }
        return out;
    }

    // ── 외부 보스 연결

    /**
     * 그 장 보스를 외부 모드 몹으로 바꾸는 연결.
     *
     * @param entity      몹 id (예: fantasybosses:radicion). 그 모드가 없으면 기본(바닐라 바탕) 보스로 대신함
     * @param nbt         소환할 때 넣는 NBT (SNBT 문자열 또는 JSON 객체, 없으면 null)
     * @param ourSkills   우리 기술·부하 소환·광폭화·대상 지정·묶어 두기를 쓸지
     * @param ourBossbar  우리 체력바를 띄울지 (외부 모드가 자체 체력바를 띄우면 false)
     * @param gradeScaling 진행 등급 배율(공격·체력)을 곱할지
     * @param anchorNbt   전장 안 보스 자리(블록 좌표 x,y,z 정수 배열)를 넣어 줄 NBT 이름 (없으면 null)
     * @param name        머리 위 이름표 (없으면 외부 모드 이름 그대로)
     * @param offsetNbt   (v0.8) 이동량 = 전장 보스 자리 − 왕국 대륙 보스 자리 (정수 배열 dx,dy,dz)를 넣어 줄 NBT 이름. 보스 방 사본이 있을 때만
     * @param designAttack (v0.8) 등급 배율 기준이 될 그 보스의 기본 공격 (없으면 공격력 속성). 배율 = 등급 기본 공격 ÷ 이 값
     * @param ourPatterns (v0.8) 우리 고정 피해 패턴을 쓸지 (기본 true)
     */
    public record Link(boolean enabled, String entity, JsonElement nbt, boolean ourSkills, boolean ourBossbar, boolean gradeScaling,
                       String anchorNbt, String name, String offsetNbt, Double designAttack, boolean ourPatterns) {}

    static final Set<String> LINK_KEYS = Set.of("enabled", "entity", "nbt", "our_skills", "our_bossbar", "grade_scaling", "anchor_nbt", "name",
            "offset_nbt", "design_attack", "our_patterns");

    public static Link parseLink(JsonObject o) {
        for (String k : o.keySet()) if (!LINK_KEYS.contains(k)) throw new IllegalArgumentException("모르는 항목 '" + k + "'");
        boolean enabled = bool(o, "enabled", true);
        if (!o.has("entity")) throw new IllegalArgumentException("entity 가 없음");
        String entity = o.get("entity").getAsString();
        if (!validId(entity)) throw new IllegalArgumentException("entity '" + entity + "' 는 '모드:이름' 형식이어야 함");
        JsonElement nbt = o.has("nbt") && !o.get("nbt").isJsonNull() ? o.get("nbt") : null;
        if (nbt != null && !nbt.isJsonObject() && !(nbt.isJsonPrimitive() && nbt.getAsJsonPrimitive().isString()))
            throw new IllegalArgumentException("nbt 는 SNBT 문자열이나 JSON 객체여야 함");
        String anchor = nbtName(o, "anchor_nbt");
        String offset = nbtName(o, "offset_nbt");
        String name = o.has("name") && !o.get("name").isJsonNull() ? o.get("name").getAsString() : null;
        Double design = null;
        if (o.has("design_attack") && !o.get("design_attack").isJsonNull()) {
            design = o.get("design_attack").getAsDouble();
            if (!(design > 0 && design <= 200)) throw new IllegalArgumentException("design_attack = " + design + " (허용 0 초과~200)");
        }
        return new Link(enabled, entity, nbt, bool(o, "our_skills", false), bool(o, "our_bossbar", false), bool(o, "grade_scaling", true), anchor, name,
                offset, design, bool(o, "our_patterns", true));
    }

    private static String nbtName(JsonObject o, String k) {
        String v = o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : null;
        if (v != null && !v.matches("[A-Za-z0-9_]{1,64}")) throw new IllegalArgumentException(k + " '" + v + "' 는 영문·숫자·_ 만");
        return v;
    }

    /** 이동량: 전장 보스 자리 블록 − 왕국 대륙 보스 자리 블록 (보스 방 복사는 상대 위치를 그대로 옮기므로 녹화 좌표 + 이 값 = 전장 좌표) */
    public static int[] offset(int ax, int ay, int az, int sx, int sy, int sz) {
        return new int[]{ax - sx, ay - sy, az - sz};
    }

    /** 등급 배율의 기준값: 연결에 design_attack 이 있으면 그것, 없으면 공격력 속성 */
    public static double designBasic(Link l, double attackAttribute) {
        return l != null && l.designAttack() != null ? l.designAttack() : attackAttribute;
    }

    private static boolean bool(JsonObject o, String k, boolean def) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsBoolean() : def;
    }

    /** 리소스 id 형식 (네임스페이스 필수) */
    public static boolean validId(String id) {
        return id != null && id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+");
    }

    /** 외부 몹으로 바꿀지: 켜져 있고 그 몹이 게임에 있을 때만 */
    public static boolean useExternal(Link l, boolean entityPresent) {
        return l != null && l.enabled() && entityPresent;
    }
}
