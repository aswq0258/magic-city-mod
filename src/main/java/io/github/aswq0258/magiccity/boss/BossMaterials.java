package io.github.aswq0258.magiccity.boss;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import io.github.aswq0258.magiccity.MagicCityMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 챕터 보스 전리품 꾸러미에 더하는 지역 소재 (2026-11-20, claude/작업대_방어구제작_초안.md 4절).
 * 데이터 {@code data/magiccity/boss_materials/<보스 키>.json}: first(플레이어별 그 장 첫 완료) / repeat.
 * 아이템은 fantasyrpg 것을 id 로 찾고, 없으면 건너뜀.
 */
public final class BossMaterials {
    private BossMaterials() {}

    private static volatile Map<String, FieldLootRules.Loot> map = Map.of();

    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new SimpleJsonResourceReloadListener(new Gson(), "boss_materials") {
            @Override
            protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager rm, ProfilerFiller pf) {
                Map<String, FieldLootRules.Loot> out = new LinkedHashMap<>();
                int bad = 0;
                for (var e : files.entrySet()) {
                    if (e.getKey().getPath().startsWith("_")) continue;
                    try {
                        FieldLootRules.Loot l = FieldLootRules.parse(e.getValue().getAsJsonObject());
                        l.first().forEach(FieldLoot::item);
                        l.repeat().forEach(FieldLoot::item);
                        out.put(e.getKey().getPath(), l);
                    } catch (RuntimeException ex) {
                        bad++;
                        MagicCityMod.LOGGER.error("[보스 전장] 보스 소재 {} 읽기 실패: {}", e.getKey(), ex.getMessage());
                    }
                }
                map = Collections.unmodifiableMap(out);
                MagicCityMod.LOGGER.info("[보스 전장] 챕터 보스 소재 {}개 로드, 오류 {}건", out.size(), bad);
            }
        });
    }

    /** 이 보스의 소재 (first = 이 플레이어가 그 장을 처음 완료) */
    public static List<ItemStack> stacks(String bossKey, boolean first) {
        FieldLootRules.Loot l = map.get(bossKey);
        List<ItemStack> out = new ArrayList<>();
        if (l == null) return out;
        for (FieldLootRules.Entry e : FieldLootRules.pick(l, first)) {
            ItemStack st = FieldLoot.stack(e);                                                   // 룬석 등급 등 부가 정보 포함
            if (!st.isEmpty()) out.add(st);
        }
        return out;
    }
}
