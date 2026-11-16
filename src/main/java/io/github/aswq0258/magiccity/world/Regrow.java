package io.github.aswq0258.magiccity.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;

/**
 * 지형을 되돌린(철거·길 고치기) 빈 땅에 바이옴에 맞는 나무와 풀을 다시 심음.
 * 이미 숲이 우거진 곳(주변에 나뭇잎이 있는 곳)에는 심지 않고, 길 가까이에도 심지 않음.
 * 지형이 모두 정리된 뒤(두 번째 패스)에 불러야 나무가 잘리지 않음.
 */
public final class Regrow {
    private Regrow() {}

    private static ResourceKey<ConfiguredFeature<?, ?>> key(String n) {
        return ResourceKey.create(Registries.CONFIGURED_FEATURE, ResourceLocation.withDefaultNamespace(n));
    }

    private static final ResourceKey<ConfiguredFeature<?, ?>> OAK = key("oak"), BIRCH = key("birch"), FANCY = key("fancy_oak"),
            DARK = key("dark_oak"), SPRUCE = key("spruce"), PINE = key("pine"), SWAMP = key("swamp_oak"),
            SUPER_BIRCH = key("super_birch_bees_0002"), RED = key("huge_red_mushroom"), BROWN = key("huge_brown_mushroom");

    private static final BlockState[] FLOWERS = {Blocks.DANDELION.defaultBlockState(), Blocks.POPPY.defaultBlockState(),
            Blocks.AZURE_BLUET.defaultBlockState(), Blocks.OXEYE_DAISY.defaultBlockState(), Blocks.CORNFLOWER.defaultBlockState()};

    private static double rnd(int x, int z, long salt) {
        long h = x * 341873128712L + z * 132897987541L + salt * 0x9E3779B97F4A7C15L;
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        h *= 0xc4ceb9fe1a85ec53L;
        h ^= h >>> 33;
        return (h >>> 11) * (1.0 / (1L << 53));
    }

    /** 주변(사방 5칸)까지 머리 위에 나뭇잎이 없는 빈 땅인지 */
    private static boolean open(ServerLevel level, int x, int z) {
        int[][] d = {{0, 0}, {5, 0}, {-5, 0}, {0, 5}, {0, -5}};
        for (int[] o : d) {
            if (level.getHeight(Heightmap.Types.MOTION_BLOCKING, x + o[0], z + o[1])
                    != level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x + o[0], z + o[1])) return false;
        }
        return true;
    }

    public static void column(ServerLevel level, int x, int z) {
        int gy = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        BlockPos g = new BlockPos(x, gy, z);
        if (!level.getFluidState(g).isEmpty()) return;
        BlockState gs = level.getBlockState(g);
        boolean grass = gs.is(Blocks.GRASS_BLOCK);
        if (!(grass || gs.is(Blocks.PODZOL) || gs.is(Blocks.DIRT) || gs.is(Blocks.COARSE_DIRT) || gs.is(Blocks.MOSS_BLOCK))) return;
        BlockPos up = g.above();
        BlockState us = level.getBlockState(up);
        if (!us.isAir() && !us.is(Blocks.SNOW)) return;
        KingdomTerrain.Kind kind = KingdomTerrain.column(x, z).kind();
        double[] edge = new double[1];
        int ri = KingdomRoads.nearest(x, z, TreeClearer.WOOD + 3, edge);
        if (ri >= 0 && edge[0] <= 1) return;                                          // 길 위·길 바로 옆은 그대로
        double r = rnd(x, z, 9101), pick = rnd(x, z, 9203);

        ResourceKey<ConfiguredFeature<?, ?>> tree = null;
        double p = switch (kind) {
            case CURSED -> 1 / 20.0;
            case FOREST -> 1 / 26.0;
            case ELF_FOREST -> 1 / 28.0;
            case SWAMP -> 1 / 100.0;
            case SNOW -> 1 / 700.0;
            case MEADOW -> 1 / 900.0;
            case PLAINS -> 1 / 1500.0;
            case FARMLAND -> 1 / 3000.0;
            default -> 0;
        };
        if (ri < 0 && r < p) {
            tree = switch (kind) {
                case CURSED -> pick < 0.03 ? (pick < 0.015 ? RED : BROWN) : DARK;
                case FOREST -> pick < 0.6 ? OAK : pick < 0.8 ? BIRCH : FANCY;
                case ELF_FOREST -> pick < 0.8 ? SUPER_BIRCH : BIRCH;
                case SWAMP -> SWAMP;
                case SNOW -> pick < 0.5 ? SPRUCE : PINE;
                case MEADOW -> pick < 0.5 ? BIRCH : OAK;
                default -> pick < 0.85 ? OAK : FANCY;
            };
        }
        if (tree != null && open(level, x, z)) {
            var holder = level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE).getHolder(tree);
            if (holder.isPresent() && holder.get().value().place(level, level.getChunkSource().getGenerator(),
                    RandomSource.create((long) x * 31L + z * 17L + 5), up)) return;
        }
        // 풀·꽃
        if (!grass || !us.isAir()) return;
        double pg = switch (kind) {
            case PLAINS, FARMLAND, MEADOW -> 1 / 3.0;
            case FOREST, ELF_FOREST -> 1 / 6.0;
            case CURSED, SWAMP -> 1 / 8.0;
            default -> 0;
        };
        double q = rnd(x, z, 9307);
        if (q < pg) {
            boolean fern = (kind == KingdomTerrain.Kind.FOREST || kind == KingdomTerrain.Kind.ELF_FOREST) && pick < 0.25;
            level.setBlock(up, (fern ? Blocks.FERN : Blocks.SHORT_GRASS).defaultBlockState(), 2);
        } else if ((kind == KingdomTerrain.Kind.PLAINS || kind == KingdomTerrain.Kind.MEADOW) && q < pg + 1 / 60.0) {
            level.setBlock(up, FLOWERS[(int) (pick * FLOWERS.length)], 2);
        }
    }
}
