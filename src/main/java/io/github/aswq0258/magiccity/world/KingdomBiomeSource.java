package io.github.aswq0258.magiccity.world;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;

import java.util.stream.Stream;

/** 대륙 지형(KingdomTerrain)의 지역 종류에 맞춰 바닐라 바이옴을 배정 */
public class KingdomBiomeSource extends BiomeSource {
    public static final MapCodec<KingdomBiomeSource> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            RegistryOps.retrieveGetter(Registries.BIOME)
    ).apply(i, i.stable(KingdomBiomeSource::new)));

    @SuppressWarnings("unchecked")
    private static final ResourceKey<Biome>[] KEYS = new ResourceKey[]{
            Biomes.OCEAN, Biomes.DEEP_OCEAN, Biomes.FROZEN_OCEAN, Biomes.BEACH, Biomes.RIVER, Biomes.PLAINS,
            Biomes.SUNFLOWER_PLAINS, Biomes.FOREST, Biomes.OLD_GROWTH_BIRCH_FOREST, Biomes.DARK_FOREST, Biomes.SNOWY_PLAINS,
            Biomes.MEADOW, Biomes.JAGGED_PEAKS, Biomes.STONY_PEAKS, Biomes.BADLANDS, Biomes.SWAMP,
            Biomes.ERODED_BADLANDS,      // ASH: 재로 덮인 옛 왕도 들판
            Biomes.CRIMSON_FOREST,       // CORRUPT: 네더 포탈이 퍼뜨린 오염 (붉은 안개, 네더 몹)
            Biomes.TAIGA,                // TAIGA: 산기슭·산비탈 침엽수림
            Biomes.GROVE,                // GROVE: 눈 덮인 산비탈의 가문비 숲
            Biomes.DARK_FOREST,          // DEEPWOOD: 세계수를 둘러싼 태고의 숲 (고목은 DeepWood 가 따로 심음)
    };

    private final HolderGetter<Biome> biomes;
    private final Holder<Biome>[] byKind;

    @SuppressWarnings("unchecked")
    public KingdomBiomeSource(HolderGetter<Biome> biomes) {
        this.biomes = biomes;
        this.byKind = new Holder[KingdomTerrain.Kind.values().length];
        for (KingdomTerrain.Kind k : KingdomTerrain.Kind.values()) byKind[k.ordinal()] = biomes.getOrThrow(KEYS[k.ordinal()]);
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
}
