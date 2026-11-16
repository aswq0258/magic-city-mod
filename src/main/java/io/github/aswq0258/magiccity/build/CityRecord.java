package io.github.aswq0258.magiccity.build;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** 마지막으로 지은 왕성도시의 중심·지면·시드·차원 (신시가지만 덧지을 때 똑같은 설계도를 다시 만들기 위함) */
public final class CityRecord extends SavedData {
    public int cx, cz, base;
    public long seed;
    public String dim = "";

    static CityRecord load(CompoundTag t, HolderLookup.Provider p) {
        CityRecord r = new CityRecord();
        r.cx = t.getInt("cx");
        r.cz = t.getInt("cz");
        r.base = t.getInt("base");
        r.seed = t.getLong("seed");
        r.dim = t.getString("dim");
        return r;
    }

    @Override
    public CompoundTag save(CompoundTag t, HolderLookup.Provider p) {
        t.putInt("cx", cx);
        t.putInt("cz", cz);
        t.putInt("base", base);
        t.putLong("seed", seed);
        t.putString("dim", dim);
        return t;
    }

    public static CityRecord get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(CityRecord::new, CityRecord::load, null), "magiccity_city");
    }

    public boolean present() {
        return !dim.isEmpty();
    }
}
