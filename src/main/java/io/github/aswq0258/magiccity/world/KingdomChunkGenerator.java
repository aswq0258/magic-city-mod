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
    private static final BlockState LAVA = Blocks.LAVA.defaultBlockState();
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
            if (y == h + 1 && (c.kind() == KingdomTerrain.Kind.SNOW || c.kind() == KingdomTerrain.Kind.GROVE || (c.kind() == KingdomTerrain.Kind.PEAKS && h > 135))
                    && (c.water() == Integer.MIN_VALUE || h >= c.water())) return SNOW_LAYER;
            return null;
        }
        int depth = h - y;
        if (depth > 4) {
            // 지하 동굴 (2026-11-22): Y 0 미만에만, 지어 둔 던전 영역 + 8칸 안은 빼고 (CaveRules)
            if (y <= CaveRules.TOP && UndergroundZones.carve(x, y, z)) return CaveRules.lava(y) ? LAVA : null;
            return y < 0 ? DEEPSLATE : STONE;
        }
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
            case TAIGA -> {
                if (h > 150 && top) return SNOW_BLOCK;
                if (c.steep() && top) return hash % 5 == 0 ? ANDESITE : STONE;
                return top ? (hash % 5 == 0 ? PODZOL : hash % 11 == 0 ? Blocks.COARSE_DIRT.defaultBlockState() : GRASS) : DIRT;
            }
            case GROVE -> {
                if (c.steep() && top) return STONE;
                return top ? (underwater ? GRAVEL : SNOWY_GRASS) : DIRT;
            }
            case DEEPWOOD -> {
                if (!top) return hash % 5 == 0 ? Blocks.ROOTED_DIRT.defaultBlockState() : DIRT;
                return hash % 5 == 0 ? PODZOL : hash % 7 == 0 ? Blocks.MOSS_BLOCK.defaultBlockState() : hash % 11 == 0 ? Blocks.ROOTED_DIRT.defaultBlockState() : GRASS;
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

    /**
     * 바이옴 장식(나무·풀·광석) 을 얹은 뒤 길가 나무를 한 그루째 정리 (TreeClearer):
     * 길에서 9블록 안에 줄기가 있거나 길 위로 가지를 늘어뜨린 나무·큰 버섯은 통째로 없애고, 떠 있는 잎도 치움.
     * 이 청크에서 심긴 나무는 줄기가 이 청크(±8) 에 있으므로 여기서 다 살핌. 수정은 3x3 청크 안에서만.
     */
    @Override
    public void applyBiomeDecoration(net.minecraft.world.level.WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager) {
        super.applyBiomeDecoration(level, chunk, structureManager);
        ChunkPos cp = chunk.getPos();
        final int rx0 = cp.getMinBlockX() - 16, rx1 = cp.getMaxBlockX() + 16, rz0 = cp.getMinBlockZ() - 16, rz1 = cp.getMaxBlockZ() + 16;
        TreeClearer.Access acc = new TreeClearer.Access() {
            @Override public BlockState get(BlockPos p) { return level.getBlockState(p); }
            @Override public void set(BlockPos p, BlockState s) { level.setBlock(p, s, 2); }
            @Override public boolean ok(int x, int z) { return x >= rx0 && x <= rx1 && z >= rz0 && z <= rz1 && level.hasChunk(x >> 4, z >> 4); }
        };
        // 세계수를 둘러싼 태고의 숲: 고목·쓰러진 통나무·선돌·버섯 고리·바닥 식물
        DeepWood.decorateChunk(acc, cp.x, cp.z, false);
        // 세계수·엘프의 성역: 잎 천장으로 닫힌 보랏빛 숲
        ElfWood.decorateChunk(acc, cp.x, cp.z, false);
        // 땅 위로 드러난 용암 호수·용암 샘 → 물 (지하 용암은 그대로)
        for (int x = rx0; x <= rx1; x++) {
            for (int z = rz0; z <= rz1; z++) {
                if (!acc.ok(x, z)) continue;
                int nb = Integer.MAX_VALUE;
                for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}})
                    if (acc.ok(x + d[0], z + d[1])) nb = Math.min(nb, level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x + d[0], z + d[1]) - 1);
                int top = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
                LavaClear.column(acc, x, z, top, nb == Integer.MAX_VALUE ? top : nb);
            }
        }
        // 던전 영역 + 8칸 안의 땅속 액체(바닐라 지하 용암 호수·물/용암 샘)를 돌로 (2026-11-22) — 나중에 지을 던전에 액체가 새지 않게
        sealZones(acc, rx0, rz0, rx1, rz1);
        double[] edge = new double[1];
        for (int x = cp.getMinBlockX() - 8; x <= cp.getMaxBlockX() + 8; x++) {
            for (int z = cp.getMinBlockZ() - 8; z <= cp.getMaxBlockZ() + 8; z++) {
                if (!acc.ok(x, z) || KingdomRoads.excluded(x, z)) continue;
                if (KingdomRoads.nearest(x, z, TreeClearer.ORPHAN, edge) < 0) continue;
                KingdomTerrain.Column c = KingdomTerrain.column(x, z);
                int y0 = Math.max(c.h() + 1, c.water() == Integer.MIN_VALUE ? c.h() + 1 : c.water() + 3);   // 다리 기둥·난간은 그대로
                TreeClearer.clearColumn(acc, x, z, y0, Math.min(c.h() + 40, getMinY() + HEIGHT - 1), edge[0], 0);
            }
        }
    }

    /** 영역 상자 + 여백 안, 지표 5칸 아래부터의 액체를 돌·심층암으로 바꿈. 강·바다·호수(지표 위)는 건드리지 않음 */
    private static void sealZones(TreeClearer.Access acc, int rx0, int rz0, int rx1, int rz1) {
        int m = CaveRules.MARGIN;
        BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
        for (int[] a : UndergroundZones.all()) {
            int x0 = Math.max(rx0, a[0] - m), x1 = Math.min(rx1, a[2] + m), z0 = Math.max(rz0, a[1] - m), z1 = Math.min(rz1, a[3] + m);
            if (x0 > x1 || z0 > z1) continue;
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    if (!acc.ok(x, z)) continue;
                    int yTop = Math.min(a[5] + m, KingdomTerrain.column(x, z).h() - 5);
                    for (int y = Math.max(MIN_Y + 1, a[4] - m); y <= yTop; y++) {
                        BlockState s = acc.get(q.set(x, y, z));
                        if (!s.getFluidState().isEmpty()) acc.set(q.immutable(), y < 0 ? DEEPSLATE : STONE);
                    }
                }
            }
        }
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
