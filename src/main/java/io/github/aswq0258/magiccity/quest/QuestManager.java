package io.github.aswq0258.magiccity.quest;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.aswq0258.magiccity.MagicCityMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 퀘스트·퀘스트 NPC 데이터 로더. {@code data/<ns>/quests/*.json}, {@code data/<ns>/quest_npcs/*.json}.
 * 퀘스트 id 는 파일 이름(네임스페이스 없이, 예: kings_call). 같은 이름이 둘이면 나중 것이 덮어씀. {@code _} 로 시작하는 파일은 무시.
 */
public final class QuestManager {
    private QuestManager() {}

    /** NPC 이름과 평소 대사 */
    public record NpcDef(String id, String name, List<String> idle) {}

    private static final Gson GSON = new Gson();
    private static volatile Map<String, QuestDef> quests = Map.of();
    private static volatile Map<String, NpcDef> npcs = Map.of();

    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new SimpleJsonResourceReloadListener(GSON, "quests") {
            @Override
            protected void apply(Map<ResourceLocation, JsonElement> map, ResourceManager rm, ProfilerFiller pf) {
                Map<String, QuestDef> out = new TreeMap<>();
                int bad = 0;
                for (var e : map.entrySet()) {
                    String id = e.getKey().getPath();
                    if (id.startsWith("_")) continue;
                    try {
                        out.put(id, QuestDef.parse(id, e.getValue().getAsJsonObject()));
                    } catch (RuntimeException ex) {
                        bad++;
                        MagicCityMod.LOGGER.error("[퀘스트] {} 읽기 실패: {}", e.getKey(), ex.getMessage());
                    }
                }
                quests = Collections.unmodifiableMap(out);
                MagicCityMod.LOGGER.info("[퀘스트] 퀘스트 {}개 로드, 오류 {}건", out.size(), bad);
            }
        });
        event.addListener(new SimpleJsonResourceReloadListener(GSON, "quest_npcs") {
            @Override
            protected void apply(Map<ResourceLocation, JsonElement> map, ResourceManager rm, ProfilerFiller pf) {
                Map<String, NpcDef> out = new LinkedHashMap<>();
                for (var e : map.entrySet()) {
                    String id = e.getKey().getPath();
                    if (id.startsWith("_")) continue;
                    try {
                        JsonObject o = e.getValue().getAsJsonObject();
                        List<String> idle = new ArrayList<>();
                        if (o.has("idle")) for (JsonElement l : o.getAsJsonArray("idle")) idle.add(l.getAsString());
                        out.put(id, new NpcDef(id, o.has("name") ? o.get("name").getAsString() : id, List.copyOf(idle)));
                    } catch (RuntimeException ex) {
                        MagicCityMod.LOGGER.error("[퀘스트] NPC {} 읽기 실패: {}", e.getKey(), ex.getMessage());
                    }
                }
                npcs = Collections.unmodifiableMap(out);
                MagicCityMod.LOGGER.info("[퀘스트] 퀘스트 NPC {}명 로드", out.size());
            }
        });
    }

    public static QuestDef quest(String id) {
        return quests.get(id);
    }

    public static Collection<QuestDef> quests() {
        return quests.values();
    }

    /** 데이터에 없으면 id 를 이름으로 쓰는 빈 정의 */
    public static NpcDef npc(String id) {
        NpcDef d = npcs.get(id);
        return d != null ? d : new NpcDef(id, id, List.of());
    }
}
