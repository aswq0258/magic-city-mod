package io.github.aswq0258.magiccity.quest;

import io.github.aswq0258.magiccity.world.ModWorldgen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 개인 퀘스트 저장 ({@code magiccity_quests}, Story 와 같은 차원 저장소).
 * <ul>
 *   <li>players: UUID → (퀘스트 id → 진행 값, {@link QuestRules})</li>
 *   <li>hinted: 시작 안내를 이미 받은 플레이어</li>
 *   <li>npcs: NPC id → 마지막으로 소환한 자리 (안내에 좌표를 붙이기 위함)</li>
 * </ul>
 */
public final class QuestData extends SavedData {
    final Map<UUID, Map<String, Integer>> players = new HashMap<>();
    final Set<UUID> hinted = new HashSet<>();
    final Map<String, BlockPos> npcs = new HashMap<>();

    static QuestData load(CompoundTag tag, HolderLookup.Provider p) {
        QuestData d = new QuestData();
        CompoundTag pl = tag.getCompound("players");
        for (String k : pl.getAllKeys()) {
            try {
                UUID u = UUID.fromString(k);
                CompoundTag q = pl.getCompound(k);
                Map<String, Integer> m = new HashMap<>();
                for (String id : q.getAllKeys()) m.put(id, q.getInt(id));
                d.players.put(u, m);
            } catch (IllegalArgumentException ignored) {
            }
        }
        for (Tag t : tag.getList("hinted", Tag.TAG_STRING)) {
            try {
                d.hinted.add(UUID.fromString(t.getAsString()));
            } catch (IllegalArgumentException ignored) {
            }
        }
        CompoundTag n = tag.getCompound("npcs");
        for (String k : n.getAllKeys()) d.npcs.put(k, BlockPos.of(n.getLong(k)));
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider p) {
        CompoundTag pl = new CompoundTag();
        for (var e : players.entrySet()) {
            CompoundTag q = new CompoundTag();
            e.getValue().forEach(q::putInt);
            pl.put(e.getKey().toString(), q);
        }
        tag.put("players", pl);
        net.minecraft.nbt.ListTag h = new net.minecraft.nbt.ListTag();
        for (UUID u : hinted) h.add(net.minecraft.nbt.StringTag.valueOf(u.toString()));
        tag.put("hinted", h);
        CompoundTag n = new CompoundTag();
        npcs.forEach((k, v) -> n.putLong(k, v.asLong()));
        tag.put("npcs", n);
        return tag;
    }

    static QuestData get(MinecraftServer server) {
        ServerLevel k = server.getLevel(ModWorldgen.KINGDOM);
        ServerLevel l = k != null ? k : server.overworld();
        return l.getDataStorage().computeIfAbsent(new SavedData.Factory<>(QuestData::new, QuestData::load, null), "magiccity_quests");
    }

    int state(UUID player, String quest) {
        Map<String, Integer> m = players.get(player);
        return m == null ? QuestRules.NOT_STARTED : m.getOrDefault(quest, QuestRules.NOT_STARTED);
    }

    void setState(UUID player, String quest, int state) {
        if (state == QuestRules.NOT_STARTED) {
            Map<String, Integer> m = players.get(player);
            if (m != null) m.remove(quest);
        } else {
            players.computeIfAbsent(player, u -> new HashMap<>()).put(quest, state);
        }
        setDirty();
    }
}
