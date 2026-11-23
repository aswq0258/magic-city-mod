package io.github.aswq0258.magiccity.monument;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * 어떤 웅장화 묶음을 어느 판(version)으로 적용했는지 (월드 저장 데이터 magiccity_monuments).
 * state: "applying" = 적용 중(중단되면 그대로 남음 → rollback 가능), "applied" = 끝남.
 */
public final class MonumentRecord extends SavedData {
    private CompoundTag data = new CompoundTag();

    static MonumentRecord load(CompoundTag t, HolderLookup.Provider p) {
        MonumentRecord r = new MonumentRecord();
        r.data = t.getCompound("monuments").copy();
        return r;
    }

    @Override
    public CompoundTag save(CompoundTag t, HolderLookup.Provider p) {
        t.put("monuments", data.copy());
        return t;
    }

    public static MonumentRecord get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(MonumentRecord::new, MonumentRecord::load, null), "magiccity_monuments");
    }

    public boolean has(String id) {
        return data.contains(id);
    }

    public int version(String id) {
        return data.getCompound(id).getInt("version");
    }

    public String state(String id) {
        return data.getCompound(id).getString("state");
    }

    public String summary(String id) {
        CompoundTag t = data.getCompound(id);
        return String.format("%d판, %s, 요소 %d개, 바꾼 칸 %d개", t.getInt("version"), t.getString("state"), t.getInt("elements"), t.getInt("blocks"));
    }

    public void mark(String id, int version, String state, int elements, int blocks) {
        CompoundTag t = new CompoundTag();
        t.putInt("version", version);
        t.putString("state", state);
        t.putInt("elements", elements);
        t.putInt("blocks", blocks);
        t.putLong("time", System.currentTimeMillis());
        data.put(id, t);
        setDirty();
    }

    public void remove(String id) {
        data.remove(id);
        setDirty();
    }
}
