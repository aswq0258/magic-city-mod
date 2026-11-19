package io.github.aswq0258.magiccity.quest;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 개인 퀘스트 정의 ({@code data/<ns>/quests/<id>.json}). Minecraft 비의존 (Gson만 씀).
 * 기준: 프로젝트 문서 claude/서장NPC_직업선택_초안.md 5절, claude/혼합개발_연동설계_v1.md D-2.
 *
 * <pre>
 * {
 *   "title": "왕의 소명",
 *   "giver": "steward",                 // 퀘스트를 주는 NPC id (quest_npcs/&lt;id&gt;.json)
 *   "hint": "왕국에 처음 들어올 때 한 번 보내는 안내 (없으면 안내 없음)",
 *   "requires": ["다른_퀘스트"],         // 먼저 끝내야 하는 퀘스트 (없으면 생략)
 *   "offer": ["수락 전 대사", ...], "accept": "소명을 받든다", "decline": "나중에",
 *   "steps": [
 *     {"type": "signal", "signal": "fantasyrpg:class_chosen", "goal": "...", "npc": "steward",
 *      "lines": ["..."], "action": {"id": "choose_class", "label": "직업을 정한다"}},
 *     {"type": "talk_npc", "npc": "steward", "goal": "...", "lines": ["..."]},
 *     {"type": "enter_area", "goal": "...", "x": 260, "z": 260, "radius": 60, "y_min": 38, "y_max": 56},
 *     {"type": "chapter_clear", "chapter": 1, "goal": "...", "x": 260, "z": 259},
 *     {"type": "enter_area", "goal": "...", "mark": "deep_hall", "radius": 30},   // mark: 코드가 계산하는 자리 이름 (QuestMarks)
 *     {"type": "talk_npcs", "goal": "...", "npcs": ["miner_bruk", "miner_halda"]}, // 여러 NPC 를 아무 순서로 모두
 *     {"type": "use_blocks", "goal": "...", "blocks": ["deep_valve_1", "deep_valve_2"]} // 정해진 자리 블록을 아무 순서로 모두 오른쪽 클릭
 *   ],
 *   "version": 2,                       // 단계를 바꾸면 올림: 저장된 판과 다르면 진행 중인 퀘스트를 첫 단계부터 다시
 *   "complete": ["완료 대사"]
 * }
 * </pre>
 */
public record QuestDef(String id, String title, String giver, String hint, List<String> requires,
                       List<String> offer, String accept, String decline, List<Step> steps, List<String> complete,
                       int chapter, List<Integer> requiresChapters, int version) {

    /** 예전 형식(챕터 정보 없음) */
    public QuestDef(String id, String title, String giver, String hint, List<String> requires,
                    List<String> offer, String accept, String decline, List<Step> steps, List<String> complete) {
        this(id, title, giver, hint, requires, offer, accept, decline, steps, complete, 0, List.of(), 1);
    }

    public QuestDef(String id, String title, String giver, String hint, List<String> requires,
                    List<String> offer, String accept, String decline, List<Step> steps, List<String> complete,
                    int chapter, List<Integer> requiresChapters) {
        this(id, title, giver, hint, requires, offer, accept, decline, steps, complete, chapter, requiresChapters, 1);
    }

    /** 단계 목록만 바꾼 사본 (좌표 채우기용) */
    public QuestDef withSteps(List<Step> newSteps) {
        return new QuestDef(id, title, giver, hint, requires, offer, accept, decline, List.copyOf(newSteps), complete, chapter, requiresChapters, version);
    }

    public enum Type {
        TALK_NPC, SIGNAL, ENTER_AREA, CHAPTER_CLEAR, TALK_NPCS, USE_BLOCKS;

        static Type parse(String s) {
            try {
                return valueOf(s.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("모르는 단계 종류: " + s + " (talk_npc, signal, enter_area, chapter_clear, talk_npcs, use_blocks)");
            }
        }
    }

    /**
     * 단계 하나. 쓰지 않는 칸은 빈 문자열·0.
     * @param hasPos 목표 좌표(x, z)가 있는지 (안내에 좌표를 붙일지)
     * @param atChapter 0 이 아니면 좌표를 그 챕터 보스 자리로 채움 (QuestManager 가 읽을 때)
     * @param mark 빈 문자열이 아니면 좌표(와 y_min·y_max 를 따로 주지 않았으면 높이 범위)를 그 자리 이름으로 채움 (QuestMarks)
     * @param npcs talk_npcs 의 NPC id 들
     * @param blocks use_blocks 의 자리 이름들, {@code blockPos} 는 읽을 때 채운 좌표 {x, y, z}
     */
    public record Step(Type type, String goal, String npc, List<String> lines, String actionId, String actionLabel,
                       String signal, String dimension, boolean hasPos, int x, int z, int radius, int yMin, int yMax, int chapter,
                       int atChapter, String mark, boolean ySet, List<String> npcs, List<String> blocks, List<int[]> blockPos) {
        public Step(Type type, String goal, String npc, List<String> lines, String actionId, String actionLabel,
                    String signal, String dimension, boolean hasPos, int x, int z, int radius, int yMin, int yMax, int chapter,
                    int atChapter) {
            this(type, goal, npc, lines, actionId, actionLabel, signal, dimension, hasPos, x, z, radius, yMin, yMax, chapter, atChapter,
                    "", yMin != Integer.MIN_VALUE || yMax != Integer.MAX_VALUE, List.of(), List.of(), List.of());
        }

        public boolean hasAction() {
            return !actionId.isEmpty();
        }

        /** 좌표 채우기 */
        public Step at(int nx, int nz) {
            return new Step(type, goal, npc, lines, actionId, actionLabel, signal, dimension, true, nx, nz, radius, yMin, yMax, chapter, atChapter,
                    mark, ySet, npcs, blocks, blockPos);
        }

        /** 좌표와 (따로 정하지 않았으면) 높이 범위 채우기 */
        public Step at(int nx, int nz, int nyMin, int nyMax) {
            return new Step(type, goal, npc, lines, actionId, actionLabel, signal, dimension, true, nx, nz, radius,
                    ySet ? yMin : nyMin, ySet ? yMax : nyMax, chapter, atChapter, mark, ySet, npcs, blocks, blockPos);
        }

        /** use_blocks 자리 좌표 채우기 */
        public Step withBlocks(List<int[]> pos) {
            return new Step(type, goal, npc, lines, actionId, actionLabel, signal, dimension, hasPos, x, z, radius, yMin, yMax, chapter, atChapter,
                    mark, ySet, npcs, blocks, List.copyOf(pos));
        }

        /** 여러 개 중 몇 개를 해야 하는지 (talk_npcs·use_blocks, 그 밖은 0) */
        public int parts() {
            return type == Type.TALK_NPCS ? npcs.size() : type == Type.USE_BLOCKS ? blocks.size() : 0;
        }
    }

    public int size() {
        return steps.size();
    }

    public static QuestDef parse(String id, JsonObject o) {
        String title = str(o, "title", id);
        String giver = str(o, "giver", "");
        if (giver.isEmpty()) throw new IllegalArgumentException("giver 가 없음");
        JsonArray arr = o.has("steps") ? o.getAsJsonArray("steps") : new JsonArray();
        if (arr.isEmpty()) throw new IllegalArgumentException("steps 가 비었음");
        List<Step> steps = new ArrayList<>();
        for (int i = 0; i < arr.size(); i++) {
            try {
                steps.add(step(arr.get(i).getAsJsonObject()));
            } catch (RuntimeException e) {
                throw new IllegalArgumentException("steps[" + i + "]: " + e.getMessage(), e);
            }
        }
        List<Integer> reqCh = new ArrayList<>();
        if (o.has("requires_chapters")) for (JsonElement e : o.getAsJsonArray("requires_chapters")) {
            int c = e.getAsInt();
            if (c < 1 || c > 12) throw new IllegalArgumentException("requires_chapters 는 1~12");
            reqCh.add(c);
        }
        int chapter = o.has("chapter") ? o.get("chapter").getAsInt() : 0;
        if (chapter < 0 || chapter > 12) throw new IllegalArgumentException("chapter 는 0~12");
        int version = o.has("version") ? o.get("version").getAsInt() : 1;
        if (version < 1) throw new IllegalArgumentException("version 은 1 이상");
        return new QuestDef(id, title, giver, str(o, "hint", ""), list(o, "requires"), list(o, "offer"),
                str(o, "accept", "수락한다"), str(o, "decline", "나중에"), List.copyOf(steps), list(o, "complete"),
                chapter, List.copyOf(reqCh), version);
    }

    private static Step step(JsonObject s) {
        Type t = Type.parse(str(s, "type", ""));
        String goal = str(s, "goal", "");
        if (goal.isEmpty()) throw new IllegalArgumentException("goal 이 없음");
        String npc = str(s, "npc", "");
        String actionId = "", actionLabel = "";
        if (s.has("action")) {
            JsonObject a = s.getAsJsonObject("action");
            actionId = str(a, "id", "");
            actionLabel = str(a, "label", actionId);
            if (actionId.isEmpty()) throw new IllegalArgumentException("action.id 가 없음");
            if (npc.isEmpty()) throw new IllegalArgumentException("action 은 npc 가 있어야 함");
        }
        String signal = str(s, "signal", "");
        int chapter = s.has("chapter") ? s.get("chapter").getAsInt() : 0;
        int atChapter = s.has("at_chapter") ? s.get("at_chapter").getAsInt() : 0;
        if (atChapter < 0 || atChapter > 12) throw new IllegalArgumentException("at_chapter 는 1~12");
        String mark = str(s, "mark", "");
        boolean hasPos = (s.has("x") && s.has("z")) || atChapter > 0 || !mark.isEmpty();
        int x = s.has("x") ? s.get("x").getAsInt() : 0, z = s.has("z") ? s.get("z").getAsInt() : 0;
        int radius = s.has("radius") ? s.get("radius").getAsInt() : 0;
        int yMin = s.has("y_min") ? s.get("y_min").getAsInt() : Integer.MIN_VALUE;
        int yMax = s.has("y_max") ? s.get("y_max").getAsInt() : Integer.MAX_VALUE;
        boolean ySet = s.has("y_min") || s.has("y_max");
        List<String> npcs = list(s, "npcs"), blocks = list(s, "blocks");
        switch (t) {
            case TALK_NPC -> { if (npc.isEmpty()) throw new IllegalArgumentException("talk_npc 는 npc 가 있어야 함"); }
            case SIGNAL -> { if (signal.isEmpty()) throw new IllegalArgumentException("signal 이 없음"); }
            case ENTER_AREA -> {
                if (!hasPos || radius <= 0) throw new IllegalArgumentException("enter_area 는 x, z(또는 at_chapter·mark), radius(>0) 가 있어야 함");
                if (yMin > yMax) throw new IllegalArgumentException("y_min > y_max");
            }
            case CHAPTER_CLEAR -> { if (chapter < 1 || chapter > 12) throw new IllegalArgumentException("chapter 는 1~12"); }
            case TALK_NPCS -> {
                if (npcs.size() < 2 || npcs.size() > 30) throw new IllegalArgumentException("talk_npcs 는 npcs 가 2~30명");
                if (npcs.stream().distinct().count() != npcs.size()) throw new IllegalArgumentException("npcs 가 겹침");
            }
            case USE_BLOCKS -> {
                if (blocks.isEmpty() || blocks.size() > 30) throw new IllegalArgumentException("use_blocks 는 blocks 가 1~30개");
                if (blocks.stream().distinct().count() != blocks.size()) throw new IllegalArgumentException("blocks 가 겹침");
            }
        }
        return new Step(t, goal, npc, list(s, "lines"), actionId, actionLabel, signal,
                str(s, "dimension", "magiccity:kingdom"), hasPos, x, z, radius, yMin, yMax, chapter, atChapter,
                mark, ySet, npcs, blocks, List.of());
    }

    private static String str(JsonObject o, String k, String def) {
        return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : def;
    }

    private static List<String> list(JsonObject o, String k) {
        if (!o.has(k)) return List.of();
        List<String> out = new ArrayList<>();
        for (JsonElement e : o.getAsJsonArray(k)) out.add(e.getAsString());
        return List.copyOf(out);
    }
}
