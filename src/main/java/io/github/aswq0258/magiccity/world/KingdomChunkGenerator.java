package io.github.aswq0258.magiccity.world;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowyDirtBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.blending.Blender;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 왕국 대륙 차원의 지형 생성기: KingdomTerrain 의 높이·종류대로 블록을 채움.
 * 나무·풀·광석 같은 장식은 바이옴(KingdomBiomeSource) 의 바닐라 장식이 자동으로 얹음.
 */
public class KingdomChunkGenerator extends ChunkGenerator {
    public static final MapCodec<KingdomChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(ChunkGenerator::getBiomeSource)
    ).apply(i, i.stable(KingdomChunkGenerator::new)));

    private static final int MIN_Y = -64, HEIGHT = 384;

    public KingdomChunkGenerator(BiomeSource biomeSource) {
        super(biomeSource);
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    // ───────────────────────── 블록 ─────────────────────────

    private static final BlockState BEDROCK = Blocks.BEDROCK.defaultBlockState();
    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final BlockState DEEPSLATE = Blocks.DEEPSLATE.defaultBlockState();
    private static final BlockState DIRT = Blocks.DIRT.defaultBlockState();
    private static final BlockState GRASS = Blocks.GRASS_BLOCK.defaultBlockState();
    private static final BlockState SNOWY_GRASS = Blocks.GRASS_BLOCK.defaultBlockState().setValue(SnowyDirtBlock.SNOWY, true);
    private static final BlockState SAND = Blocks.SAND.defaultBlockState();
    private static final BlockState SANDSTONE = Blocks.SANDSTONE.defaultBlockState();
    private static final BlockState GRAVEL = Blocks.GRAVEL.defaultBlockState();
    private static final BlockState WATER = Blocks.WATER.defaultBlockState();
    private static final BlockState ICE = Blocks.ICE.defaultBlockState();
    private static final BlockState SNOW_LAYER = Blocks.SNOW.defaultBlockState();
    private static final BlockState SNOW_BLOCK = Blocks.SNOW_BLOCK.defaultBlockState();
    private static final BlockState MUD = Blocks.MUD.defaultBlockState();
    private static final BlockState RED_SAND = Blocks.RED_SAND.defaultBlockState();
    private static final BlockState[] TERRACOTTA = {
            Blocks.TERRACOTTA.defaultBlockState(), Blocks.BROWN_TERRACOTTA.defaultBlockState(), Blocks.GRAY_TERRACOTTA.defaultBlockState(),
            Blocks.BLACK_TERRACOTTA.defaultBlockState(), Blocks.RED_TERRACOTTA.defaultBlockState(), Blocks.GRAY_TERRACOTTA.defaultBlockState(),
    };
    private static final BlockState ANDESITE = Blocks.ANDESITE.defaultBlockState();
    private static final BlockState TUFF = Blocks.TUFF.defaultBlockState();
    private static final BlockState PODZOL = Blocks.PODZOL.defaultBlockState();
    private static final BlockState BLACKSTONE = Blocks.BLACKSTONE.defaultBlockState();

    /** 기둥의 y 높이 블록 */
    public static BlockState blockAt(KingdomTerrain.Column c, int x, int y, int z) {
        if (y == MIN_Y) return BEDROCK;
        int h = c.h();
        if (c.road() > 0) {
            BlockState r = RoadBlocks.at(c, x, y, z);
            if (r != null) return r == RoadBlocks.KEEP_AIR ? null : r;
        }
        if (y > h) {
            if (c.water() != Integer.MIN_VALUE && y <= c.water()) {
                return (c.kind() == KingdomTerrain.Kind.FROZEN_OCEAN && y == c.water()) ? ICE : WATER;
            }
            if (y == h + 1 && (c.kind() == KingdomTerrain.Kind.SNOW || (c.kind() == KingdomTerrain.Kind.PEAKS && h > 135))
                    && (c.water() == Integer.MIN_VALUE || h >= c.water())) return SNOW_LAYER;
            return null;
        }
        int depth = h - y;
        if (depth > 4) return y < 0 ? DEEPSLATE : STONE;
        boolean top = depth == 0;
        boolean underwater = c.water() != Integer.MIN_VALUE && h < c.water();
        int hash = (int) ((x * 73856093L ^ z * 19349663L) & 0x7fffffff);
        switch (c.kind()) {
            case OCEAN, DEEP_OCEAN, FROZEN_OCEAN, RIVER -> {
                return hash % 4 == 0 ? GRAVEL : (depth < 3 ? SAND : SANDSTONE);
            }
            case BEACH -> {
                return depth < 3 ? SAND : SANDSTONE;
            }
            case SNOW -> {
                return top ? (underwater ? GRAVEL : SNOWY_GRASS) : DIRT;
            }
            case PEAKS, DWARF -> {
                if (h > 150 && top) return SNOW_BLOCK;
                if (c.steep() || h > 112) return depth == 0 && hash % 5 == 0 ? ANDESITE : (hash % 7 == 0 ? TUFF : STONE);
                return top ? GRASS : DIRT;
            }
            case MEADOW -> {
                if (c.steep() && top) return STONE;
                return top ? GRASS : DIRT;
            }
            case WASTELAND -> {
                if (top) return hash % 3 == 0 ? BLACKSTONE : RED_SAND;
                return TERRACOTTA[Math.floorMod(y, TERRACOTTA.length)];
            }
            case ASH -> {
                if (top) return hash % 5 == 0 ? Blocks.COARSE_DIRT.defaultBlockState() : hash % 3 == 0 ? GRAVEL : hash % 7 == 0 ? BLACKSTONE : Blocks.TUFF.defaultBlockState();
                return hash % 4 == 0 ? Blocks.TUFF.defaultBlockState() : STONE;
            }
            case CORRUPT -> {
                if (top) return hash % 4 == 0 ? Blocks.CRIMSON_NYLIUM.defaultBlockState() : hash % 3 == 0 ? BLACKSTONE : Blocks.NETHERRACK.defaultBlockState();
                return Blocks.NETHERRACK.defaultBlockState();
            }
            case CURSED -> {
                return top ? (hash % 3 == 0 ? PODZOL : GRASS) : DIRT;
            }
            case SWAMP -> {
                return top ? (underwater || hash % 4 == 0 ? MUD : GRASS) : DIRT;
            }
            case ELF_FOREST, FOREST -> {
                return top ? (hash % 9 == 0 ? PODZOL : GRASS) : DIRT;
            }
            default -> {
                return top ? (underwater ? SAND : GRASS) : DIRT;
            }
        }
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState randomState, StructureManager structureManager, ChunkAccess chunk) {
        Heightmap ocean = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        Heightmap surface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        ChunkPos cp = chunk.getPos();
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int x = cp.getMinBlockX() + lx, z = cp.getMinBlockZ() + lz;
                KingdomTerrain.Column c = KingdomTerrain.column(x, z);
                int top = Math.max(c.h() + 1, c.water() == Integer.MIN_VALUE ? c.h() + 1 : c.water());
                if (c.road() > 0) top += 4;                                        // 다리 난간·가로등
                for (int y = MIN_Y; y <= top; y++) {
                    BlockState s = blockAt(c, x, y, z);
                    if (s == null) continue;
                    chunk.setBlockState(pos.set(x, y, z), s, false);
                    ocean.update(lx, y, lz, s);
                    surface.update(lx, y, lz, s);
                }
            }
        }
        return CompletableFuture.completedFuture(chunk);
    }

    /** 왕국 대륙에는 바닐라 구조물(마을·전초기지·폐허 포탈 등)을 만들지 않음 — 대신 모드 촌락(site.Hamlets) */
    @Override
    public void createStructures(net.minecraft.core.RegistryAccess registryAccess, net.minecraft.world.level.chunk.ChunkGeneratorStructureState structureState,
                                 StructureManager structureManager, ChunkAccess chunk,
                                 net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager templateManager) {
    }

    @Override
    public void applyCarvers(WorldGenRegion level, long seed, RandomState randomState, BiomeManager biomeManager,
                             StructureManager structureManager, ChunkAccess chunk, GenerationStep.Carving step) {
    }

    @Override
    public void buildSurface(WorldGenRegion level, StructureManager structureManager, RandomState randomState, ChunkAccess chunk) {
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion level) {
        ChunkPos cp = level.getCenter();
        Holder<Biome> biome = level.getBiome(cp.getWorldPosition().atY(level.getMaxBuildHeight() - 1));
        WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(RandomSupport.generateUniqueSeed()));
        random.setDecorationSeed(level.getSeed(), cp.getMinBlockX(), cp.getMinBlockZ());
        NaturalSpawner.spawnMobsForChunkGeneration(level, biome, cp, random);
    }

    @Override
    public int getGenDepth() {
        return HEIGHT;
    }

    @Override
    public int getSeaLevel() {
        return KingdomTerrain.SEA;
    }

    @Override
    public int getMinY() {
        return MIN_Y;
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState randomState) {
        KingdomTerrain.Column c = KingdomTerrain.column(x, z);
        boolean floorOnly = type == Heightmap.Types.OCEAN_FLOOR || type == Heightmap.Types.OCEAN_FLOOR_WG;
        int top = c.h();
        if (!floorOnly && c.water() != Integer.MIN_VALUE) top = Math.max(top, c.water());
        return top + 1;
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState randomState) {
        KingdomTerrain.Column c = KingdomTerrain.column(x, z);
        BlockState[] states = new BlockState[HEIGHT];
        for (int i = 0; i < HEIGHT; i++) {
            BlockState s = blockAt(c, x, MIN_Y + i, z);
            states[i] = s == null ? Blocks.AIR.defaultBlockState() : s;
        }
        return new NoiseColumn(MIN_Y, states);
    }

    @Override
    public void addDebugScreenInfo(List<String> info, RandomState randomState, BlockPos pos) {
        KingdomTerrain.Column c = KingdomTerrain.column(pos.getX(), pos.getZ());
        info.add("Magic City Kingdom: " + c.kind() + " h=" + c.h());
    }
}
