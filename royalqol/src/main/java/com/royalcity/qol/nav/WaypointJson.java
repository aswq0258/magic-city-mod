package com.royalcity.qol.nav;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

/**
 * config/royalqol/waypoints.json 형식. 내장 지역은 저장하지 않는다 (모드를 갱신하면 내장 지역도 갱신되게).
 *
 * <pre>{"version": 1, "hud": true, "waypoints": [{"name": "...", "dimension": "magiccity:kingdom", "x": 0, "y": 64, "z": 0, "radius": 16}]}</pre>
 */
public final class WaypointJson {
    private WaypointJson() {}

    public static final int VERSION = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public record Data(boolean hud, List<Waypoint> waypoints, List<String> problems) {}

    public static String write(boolean hud, List<Waypoint> user) {
        JsonObject root = new JsonObject();
        root.addProperty("version", VERSION);
        root.addProperty("hud", hud);
        JsonArray arr = new JsonArray();
        for (Waypoint w : user) {
            if (w.builtin()) continue;
            JsonObject o = new JsonObject();
            o.addProperty("name", w.name());
            o.addProperty("dimension", w.dimension());
            o.addProperty("x", w.x());
            o.addProperty("y", w.y());
            o.addProperty("z", w.z());
            o.addProperty("radius", w.radius());
            if (!w.note().isEmpty()) o.addProperty("note", w.note());
            arr.add(o);
        }
        root.add("waypoints", arr);
        return GSON.toJson(root);
    }

    /** 읽기. 잘못된 항목은 건너뛰고 problems 에 이유를 남긴다. 파일 전체가 깨졌으면 예외 */
    public static Data read(String text) {
        JsonObject root = JsonParser.parseString(text).getAsJsonObject();
        boolean hud = !root.has("hud") || root.get("hud").getAsBoolean();
        List<Waypoint> list = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        if (root.has("waypoints")) {
            int i = 0;
            for (JsonElement e : root.getAsJsonArray("waypoints")) {
                i++;
                try {
                    JsonObject o = e.getAsJsonObject();
                    list.add(new Waypoint(
                            o.get("name").getAsString(),
                            o.has("dimension") ? o.get("dimension").getAsString() : DefaultRegions.KINGDOM,
                            o.get("x").getAsInt(),
                            o.has("y") ? o.get("y").getAsInt() : 64,
                            o.get("z").getAsInt(),
                            o.has("radius") ? o.get("radius").getAsInt() : 16,
                            false,
                            o.has("note") ? o.get("note").getAsString() : ""));
                } catch (RuntimeException ex) {
                    problems.add(i + "번째 항목: " + (ex.getMessage() == null ? "이름·x·z 중 빠진 값이 있음" : ex.getMessage()));
                }
            }
        }
        return new Data(hud, list, problems);
    }
}
