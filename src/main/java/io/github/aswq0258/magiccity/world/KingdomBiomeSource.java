package io.github.aswq0258.magiccity.world;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;

import java.util.stream.Stream;

/** 대륙 지형(KingdomTerrain)의 지역 종류에 맞춰 바닐라 바이옴을 배정 */
public class KingdomBiomeSource extends BiomeSource {
    public static final MapCodec<KingdomBiomeSource> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            RegistryOps.retrieveElement(Biomes.OCEAN), RegistryOps.retrieveElement(Biomes.DEEP_OCEAN),
            RegistryOps.retrieveElement(Biomes.FROZEN_OCEAN), RegistryOps.retrieveElement(Biomes.BEACH),
            RegistryOps.retrieveElement(Biomes.RIVER), RegistryOps.retrieveElement(Biomes.PLAINS),
            RegistryOps.retrieveElement(Biomes.SUNFLOWER_PLAINS), RegistryOps.retrieveElement(Biomes.FOREST),
            RegistryOps.retrieveElement(Biomes.OLD_GROWTH_BIRCH_FOREST), RegistryOps.retrieveElement(Biomes.DARK_FOREST),
            RegistryOps.retrieveElement(Biomes.SNOWY_PLAINS), RegistryOps.retrieveElement(Biomes.MEADOW),
            RegistryOps.retrieveElement(Biomes.JAGGED_PEAKS), RegistryOps.retrieveElement(Biomes.STONY_PEAKS),
            RegistryOps.retrieveElement(Biomes.BADLANDS), RegistryOps.retrieveElement(Biomes.SWAMP)
    ).apply(i, i.stable(KingdomBiomeSource::new)));

    private final Holder<Biome>[] byKind;

    @SuppressWarnings("unchecked")
    public KingdomBiomeSource(Holder<Biome> ocean, Holder<Biome> deepOcean, Holder<Biome> frozenOcean, Holder<Biome> beach,
                              Holder<Biome> river, Holder<Biome> plains, Holder<Biome> farmland, Holder<Biome> forest,
                              Holder<Biome> elfForest, Holder<Biome> cursed, Holder<Biome> snow, Holder<Biome> meadow,
                              Holder<Biome> peaks, Holder<Biome> dwarf, Holder<Biome> wasteland, Holder<Biome> swamp) {
        this.byKind = new Holder[KingdomTerrain.Kind.values().length];
        byKind[KingdomTerrain.Kind.OCEAN.ordinal()] = ocean;
        byKind[KingdomTerrain.Kind.DEEP_OCEAN.ordinal()] = deepOcean;
        byKind[KingdomTerrain.Kind.FROZEN_OCEAN.ordinal()] = frozenOcean;
        byKind[KingdomTerrain.Kind.BEACH.ordinal()] = beach;
        byKind[KingdomTerrain.Kind.RIVER.ordinal()] = river;
        byKind[KingdomTerrain.Kind.PLAINS.ordinal()] = plains;
        byKind[KingdomTerrain.Kind.FARMLAND.ordinal()] = farmland;
        byKind[KingdomTerrain.Kind.FOREST.ordinal()] = forest;
        byKind[KingdomTerrain.Kind.ELF_FOREST.ordinal()] = elfForest;
        byKind[KingdomTerrain.Kind.CURSED.ordinal()] = cursed;
        byKind[KingdomTerrain.Kind.SNOW.ordinal()] = snow;
        byKind[KingdomTerrain.Kind.MEADOW.ordinal()] = meadow;
        byKind[KingdomTerrain.Kind.PEAKS.ordinal()] = peaks;
        byKind[KingdomTerrain.Kind.DWARF.ordinal()] = dwarf;
        byKind[KingdomTerrain.Kind.WASTELAND.ordinal()] = wasteland;
        byKind[KingdomTerrain.Kind.SWAMP.ordinal()] = swamp;
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return Stream.of(byKind);
    }

    @Override
    public Holder<Biome> getNoiseBiome(int qx, int qy, int qz, Climate.Sampler sampler) {
        return byKind[KingdomTerrain.column(qx << 2, qz << 2).kind().ordinal()];
    }

    public Holder<Biome> biomeOf(KingdomTerrain.Kind kind) {
        return byKind[kind.ordinal()];
    }
}
