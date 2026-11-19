package io.github.aswq0258.magiccity.boss;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 필드보스 전리품 꾸러미 규칙 (순수 계산, 2026-11-19 사용자 결정).
 * 15분 자동 재등장은 그대로, 드롭만: 플레이어별 첫 처치는 큰 보상, 그 뒤는 작은 보상, 다이아·철은 반복에도 소량.
 * 데이터 {@code data/magiccity/field_boss_loot/<보스 키>.json}: {"name": "...", "first": [{"item": "minecraft:emerald", "count": 12}, ...], "repeat": [...]}
 */
public final class FieldLootRules {
    private FieldLootRules() {}

    /** 길 번호(RoadTheme.THEMES 순서) → 데이터 파일 이름 */
    public static final String[] KEYS = {"frost_bear", "queen_spider", "mountain_ravager", "pass_wyvern", "raider_evoker",
            "plague_giant", "river_lord", "swamp_witch", "cursed_archer", "rift_watcher"};

    public record Entry(String item, int count) {}

    public record Loot(String name, List<Entry> first, List<Entry> repeat) {}

    /** 길 번호의 키 (모르면 null) */
    public static String key(int road) {
        return road >= 0 && road < KEYS.length ? KEYS[road] : null;
    }

    /** 이 플레이어가 이 보스를 처음 잡는지 (처치 기록 비트) */
    public static boolean firstKill(int mask, int road) {
        return (mask & (1 << road)) == 0;
    }

    public static int markKill(int mask, int road) {
        return mask | (1 << road);
    }

    /** 첫 처치면 first, 아니면 repeat */
    public static List<Entry> pick(Loot l, boolean first) {
        return first ? l.first() : l.repeat();
    }

    public static Loot parse(JsonObject o) {
        String name = o.has("name") ? o.get("name").getAsString() : "";
        List<Entry> first = entries(o, "first"), repeat = entries(o, "repeat");
        if (first.isEmpty()) throw new IllegalArgumentException("first 가 비었음");
        if (repeat.isEmpty()) throw new IllegalArgumentException("repeat 가 비었음");
        return new Loot(name, List.copyOf(first), List.copyOf(repeat));
    }

    private static List<Entry> entries(JsonObject o, String k) {
        List<Entry> out = new ArrayList<>();
        if (!o.has(k)) return out;
        JsonArray a = o.getAsJsonArray(k);
        for (JsonElement e : a) {
            JsonObject x = e.getAsJsonObject();
            String item = x.get("item").getAsString();
            int count = x.has("count") ? x.get("count").getAsInt() : 1;
            if (!item.contains(":")) throw new IllegalArgumentException("아이템 id 는 네임스페이스 포함: " + item);
            if (count < 1 || count > 64) throw new IllegalArgumentException(item + " 수량은 1~64");
            out.add(new Entry(item, count));
        }
        return out;
    }

    /** 수량 합 (테스트·검사용) */
    public static int count(List<Entry> l, String item) {
        int n = 0;
        for (Entry e : l) if (e.item().equals(item)) n += e.count();
        return n;
    }
}
