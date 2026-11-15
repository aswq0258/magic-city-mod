package io.github.aswq0258.magiccity.world;

import com.mojang.serialization.MapCodec;
import io.github.aswq0258.magiccity.MagicCityMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/** 왕국 대륙 차원 (magiccity:kingdom) 의 지형 생성기·바이옴 배치 등록 */
public final class ModWorldgen {
    private ModWorldgen() {}

    public static final ResourceKey<Level> KINGDOM = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(MagicCityMod.MODID, "kingdom"));

    private static final DeferredRegister<MapCodec<? extends ChunkGenerator>> GENERATORS =
            DeferredRegister.create(Registries.CHUNK_GENERATOR, MagicCityMod.MODID);
    private static final DeferredRegister<MapCodec<? extends BiomeSource>> BIOME_SOURCES =
            DeferredRegister.create(Registries.BIOME_SOURCE, MagicCityMod.MODID);

    static {
        GENERATORS.register("kingdom", () -> KingdomChunkGenerator.CODEC);
        BIOME_SOURCES.register("kingdom", () -> KingdomBiomeSource.CODEC);
    }

    public static void register(IEventBus modBus) {
        GENERATORS.register(modBus);
        BIOME_SOURCES.register(modBus);
    }
}
