package io.github.aswq0258.magiccity.boss;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import io.github.aswq0258.magiccity.MagicCityMod;
import io.github.aswq0258.magiccity.arena.Party;
import io.github.aswq0258.magiccity.item.LootPouchItem;
import io.github.aswq0258.magiccity.item.Pouches;
import io.github.aswq0258.magiccity.world.ModWorldgen;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 필드보스 전리품 꾸러미 (2026-11-19 사용자 결정): 기여도 없이 처치한 사람과 그 파티원(결투장 64칸 안)에게 꾸러미 하나씩.
 * 플레이어별 첫 처치는 큰 보상, 그 뒤는 작은 보상. 인벤토리가 가득 차 있으면 결투장 64칸을 벗어날 때 발밑에.
 */
public final class FieldLoot {
    private FieldLoot() {}

    public static final double ROOM_R = 64;
    private static final Gson GSON = new Gson();
    private static volatile Map<String, FieldLootRules.Loot> loot = Map.of();

    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new SimpleJsonResourceReloadListener(GSON, "field_boss_loot") {
            @Override
            protected void apply(Map<ResourceLocation, JsonElement> map, ResourceManager rm, ProfilerFiller pf) {
                Map<String, FieldLootRules.Loot> out = new LinkedHashMap<>();
                int bad = 0;
                for (var e : map.entrySet()) {
                    String id = e.getKey().getPath();
                    if (id.startsWith("_")) continue;
                    try {
                        FieldLootRules.Loot l = FieldLootRules.parse(e.getValue().getAsJsonObject());
                        for (FieldLootRules.Entry en : l.first()) item(en);
                        for (FieldLootRules.Entry en : l.repeat()) item(en);
                        out.put(id, l);
                    } catch (RuntimeException ex) {
                        bad++;
                        MagicCityMod.LOGGER.error("[필드보스] 꾸러미 {} 읽기 실패: {}", e.getKey(), ex.getMessage());
                    }
                }
                loot = Collections.unmodifiableMap(out);
                MagicCityMod.LOGGER.info("[필드보스] 꾸러미 {}개 로드, 오류 {}건", out.size(), bad);
            }
        });
    }

    /** 아이템 찾기. 다른 모드(fantasyrpg 지역 소재 등) 아이템은 그 모드가 없으면 빈 것(건너뜀), 바닐라·magiccity 아이템이 없으면 오류 */
    static Item item(FieldLootRules.Entry e) {
        ResourceLocation id = ResourceLocation.parse(e.item());
        Item it = BuiltInRegistries.ITEM.get(id);
        if (it == Items.AIR && (id.getNamespace().equals("minecraft") || id.getNamespace().equals(MagicCityMod.MODID)))
            throw new IllegalArgumentException("모르는 아이템: " + e.item());
        return it;
    }

    /** 플레이어별 처치 기록 (길 번호 비트) */
    public static final class Kills extends SavedData {
        final Map<UUID, Integer> masks = new HashMap<>();

        static Kills load(CompoundTag tag, HolderLookup.Provider p) {
            Kills k = new Kills();
            for (String key : tag.getAllKeys()) {
                try {
                    k.masks.put(UUID.fromString(key), tag.getInt(key));
                } catch (IllegalArgumentException ignored) {
                }
            }
            return k;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider p) {
            masks.forEach((u, m) -> tag.putInt(u.toString(), m));
            return tag;
        }
    }

    private static Kills kills(ServerLevel any) {
        ServerLevel k = any.getServer().getLevel(ModWorldgen.KINGDOM);
        return (k != null ? k : any).getDataStorage().computeIfAbsent(new SavedData.Factory<>(Kills::new, Kills::load, null), "magiccity_field_kills");
    }

    /**
     * 처치 보상. killer 가 있으면 그와 그 파티원 중 결투장 64칸 안 사람, 없으면 결투장 64칸 안 모두.
     */
    public static void reward(ServerLevel level, int road, Vec3 room, ServerPlayer killer, String bossName) {
        FieldLootRules.Loot l = loot.get(FieldLootRules.key(road));
        if (l == null) return;
        List<ServerPlayer> to = new ArrayList<>();
        List<ServerPlayer> base = killer != null ? Party.onlineMembers(killer) : level.players();
        for (ServerPlayer p : base) if (p.level() == level && !p.isSpectator() && p.position().distanceToSqr(room) <= ROOM_R * ROOM_R) to.add(p);
        Kills k = kills(level);
        for (ServerPlayer p : to) {
            int mask = k.masks.getOrDefault(p.getUUID(), 0);
            boolean first = FieldLootRules.firstKill(mask, road);
            List<ItemStack> items = new ArrayList<>();
            for (FieldLootRules.Entry e : FieldLootRules.pick(l, first)) {
                Item it = item(e);
                if (it != Items.AIR) items.add(new ItemStack(it, e.count()));
            }
            Pouches.give(p, LootPouchItem.make(bossName + (first ? "의 첫 전리품 꾸러미" : "의 전리품 꾸러미"), items), level.dimension(), room, ROOM_R);
            if (first) {
                k.masks.put(p.getUUID(), FieldLootRules.markKill(mask, road));
                k.setDirty();
            }
        }
    }
}
