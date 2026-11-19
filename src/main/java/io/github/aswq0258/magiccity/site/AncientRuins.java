package io.github.aswq0258.magiccity.site;

import io.github.aswq0258.magiccity.build.BuildLayout;
import io.github.aswq0258.magiccity.build.Placer;
import io.github.aswq0258.magiccity.city.CityLayout;
import io.github.aswq0258.magiccity.city.Shapes;
import io.github.aswq0258.magiccity.city.feature.Feature;
import io.github.aswq0258.magiccity.world.KingdomTerrain;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 고대 왕국 유적 "아르케이아" (마스터플랜 R1) — 발굴이 한창인 잊혀진 왕국의 수도
 *
 *  - 지름 약 140, 깊이 12 의 계단식 발굴 구덩이 (북서쪽 경사로로 내려감, 곳곳에 사다리·비계)
 *  - 구덩이 바닥: 사암·테라코타 열주 대로, 무너진 대신전(기단·열주·제단), 쓰러진 거상(머리·몸통·부러진 팔),
 *    반쯤 묻힌 집터들, 붓으로 털어 볼 수 있는 수상한 모래·자갈(도자기 조각·고대 유물)
 *  - 구덩이 둘레: 오벨리스크 4개, 발굴 야영지(천막·기중기·비계·발굴 도구 상자)
 *  - 대신전 아래 왕릉 (Y 36): 석관이 늘어선 회랑(허스크 스포너), 보물실, 왕의 묘실 — 보스 "고대왕 아르케온"(boss.TombBoss)
 */
public final class AncientRuins implements BuildLayout {
    public static final int CX = KingdomTerrain.worldX(660), CZ = KingdomTerrain.worldZ(1700);
    public static final int G = 66, PIT_R = 70, FLOOR = G - 12, TOMB_Y = 36;
    public static final int TEMPLE_X = CX, TEMPLE_Z = CZ - 28;
    public static final int KING_X = CX, KING_Z = CZ - 92;
    private static final double RAMP_ANG = Math.toRadians(-135);                               // 북서쪽 (발굴지 지선 쪽)
    /** 발굴 야영지 가운데 (퀘스트 자리) */
    public static final int CAMP_X = CX + (int) Math.round(Math.cos(RAMP_ANG + 0.35) * (PIT_R + 12)),
            CAMP_Z = CZ + (int) Math.round(Math.sin(RAMP_ANG + 0.35) * (PIT_R + 12));

    private final long seed;
    private final List<Feature> features = new ArrayList<>();
    private final Map<Long, List<Feature>> byChunk = new HashMap<>();
    private final List<ChunkPos> chunks = new ArrayList<>();

    public AncientRuins(long seed) {
        this.seed = seed;
        Random r = new Random(seed);
        features.add(new Temple());
        features.add(new Colossus());
        features.add(new Tomb());
        features.add(new Camp());
        for (int i = 0; i < 4; i++) {
            double a = Math.PI / 4 + Math.PI / 2 * i;
            features.add(new Obelisk(CX + (int) Math.round(Math.cos(a) * (PIT_R + 14)), CZ + (int) Math.round(Math.sin(a) * (PIT_R + 14))));
        }
        int made = 0;
        for (int t = 0; t < 400 && made < 12; t++) {
            double a = r.nextDouble() * Math.PI * 2, rr = 20 + r.nextDouble() * 38;
            int x = CX + (int) Math.round(Math.cos(a) * rr), z = CZ + (int) Math.round(Math.sin(a) * rr);
            if (Math.abs(x - TEMPLE_X) < 22 && Math.abs(z - TEMPLE_Z) < 18) continue;
            if (Math.abs(x - CX) < 22 && z > CZ + 18 && z < CZ + 54) continue;                       // 거상 자리
            if (Math.abs(z - CZ) < 6) continue;                                                     // 열주 대로
            boolean clash = false;
            for (Feature f : features) if (f instanceof HouseRuin hr && Math.abs(hr.x - x) < 13 && Math.abs(hr.z - z) < 13) clash = true;
            if (clash) continue;
            features.add(new HouseRuin(x, z, r.nextLong()));
            made++;
        }
        for (Feature f : features) {
            for (int x = (f.minX - 1) >> 4; x <= (f.maxX + 1) >> 4; x++)
                for (int z = (f.minZ - 1) >> 4; z <= (f.maxZ + 1) >> 4; z++)
                    byChunk.computeIfAbsent(ChunkPos.asLong(x, z), k -> new ArrayList<>()).add(f);
        }
        LinkedHashSet<Long> keys = new LinkedHashSet<>();
        int e = PIT_R + 24;
        for (int x = (CX - e) >> 4; x <= (CX + e) >> 4; x++) for (int z = (CZ - e) >> 4; z <= (CZ + e) >> 4; z++) keys.add(ChunkPos.asLong(x, z));
        keys.addAll(byChunk.keySet());
        for (long k : keys) chunks.add(new ChunkPos(k));
    }

    public static boolean near(double x, double z) {
        return Math.hypot(x - CX, z - CZ) < PIT_R + 40 || Math.abs(x - KING_X) < 20 && z > KING_Z - 20 && z < CZ;
    }

    public int featureCount() {
        return features.size();
    }

    @Override public int cx() { return CX; }
    @Override public int cz() { return CZ; }
    @Override public int base() { return G; }
    @Override public int radius() { return 0; }
    @Override public CityLayout city() { return null; }
    @Override public List<ChunkPos> chunkList() { return chunks; }
    @Override public List<Feature> featuresIn(ChunkPos cp) { return byChunk.getOrDefault(cp.toLong(), Collections.emptyList()); }
    @Override public int[] lightRange(int x, int z) { return null; }
    @Override public int shapeMinY() { return TOMB_Y - 4; }
    @Override public int shapeMaxY() { return G + 30; }

    private int h(int x, int y, int z) {
        return Shapes.hash(x, y, z, seed);
    }

    /** 붓으로 털 수 있는 수상한 모래/자갈 (발굴 유물) */
    static void suspicious(Placer p, int x, int y, int z, boolean gravel, long seed) {
        if (p.pass() != Placer.Pass.SOLID || !p.contains(x, z)) return;
        p.set(x, y, z, (gravel ? Blocks.SUSPICIOUS_GRAVEL : Blocks.SUSPICIOUS_SAND).defaultBlockState());
        BlockEntity be = p.level().getBlockEntity(new BlockPos(x, y, z));
        if (be instanceof BrushableBlockEntity b) b.setLootTable(gravel ? BuiltInLootTables.TRAIL_RUINS_ARCHAEOLOGY_RARE : BuiltInLootTables.DESERT_PYRAMID_ARCHAEOLOGY, seed);
    }

    // ───────────────────────── 기둥: 발굴 구덩이 ─────────────────────────

    /** 반지름 r 에서의 바닥 높이 (바깥은 3칸씩 계단식 단) */
    private static int terrace(double r) {
        if (r <= PIT_R - 15) return FLOOR;
        int step = (int) Math.ceil((r - (PIT_R - 15)) / 5.0);                                 // 1 ~ 3
        return Math.min(G, FLOOR + step * 3);
    }

    @Override
    public void buildColumn(Placer p, int x, int z) {
        int dx = x - CX, dz = z - CZ;
        double r = Math.hypot(dx, dz) + 3 * (Math.sin(dx * 0.11) + Math.cos(dz * 0.09)) * 0.5;
        if (r > PIT_R + 2) return;
        int fy = terrace(r);
        // 북서쪽 경사로: 바닥에서 땅까지 2칸에 1칸씩
        double along = dx * Math.cos(RAMP_ANG) + dz * Math.sin(RAMP_ANG), lat = -dx * Math.sin(RAMP_ANG) + dz * Math.cos(RAMP_ANG);
        boolean ramp = Math.abs(lat) <= 2.5 && along > PIT_R - 42 && along < PIT_R + 4;
        if (ramp) fy = Math.max(fy, Math.min(G, FLOOR + (int) Math.floor((along - (PIT_R - 42)) / 3.0)));
        p.clearAbove(x, z, fy + 1);
        int hh = h(x, fy, z);
        BlockState s;
        if (ramp) {
            s = Blocks.SMOOTH_SANDSTONE.defaultBlockState();
            int next = Math.min(G, FLOOR + (int) Math.floor((along + 1 - (PIT_R - 42)) / 3.0));
            if (next > fy && fy < G) {                                                         // 계단 블록으로 오르기 쉽게
                Direction up = Math.abs(Math.cos(RAMP_ANG)) > Math.abs(Math.sin(RAMP_ANG))
                        ? (Math.cos(RAMP_ANG) > 0 ? Direction.EAST : Direction.WEST) : (Math.sin(RAMP_ANG) > 0 ? Direction.SOUTH : Direction.NORTH);
                p.set(x, fy, z, s);
                p.set(x, fy + 1, z, Blocks.SANDSTONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, up));
                return;
            }
        } else if (fy == FLOOR) {
            boolean avenue = Math.abs(dz) <= 3 && Math.abs(dx) < PIT_R - 16;
            s = avenue ? (hh % 4 == 0 ? Blocks.CHISELED_SANDSTONE.defaultBlockState() : hh % 3 == 0 ? Blocks.CUT_SANDSTONE.defaultBlockState() : Blocks.SMOOTH_SANDSTONE.defaultBlockState())
                    : hh % 7 == 0 ? Blocks.GRAVEL.defaultBlockState() : hh % 5 == 0 ? Blocks.TERRACOTTA.defaultBlockState()
                    : hh % 3 == 0 ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.SAND.defaultBlockState();
            if (!avenue && hh % 70 == 0) {
                p.set(x, fy - 1, z, Blocks.SANDSTONE.defaultBlockState());
                suspicious(p, x, fy, z, hh % 140 == 0, seed ^ ((long) x * 31 + z));
                return;
            }
            if (avenue && Math.abs(dz) == 3 && Math.floorMod(dx, 8) == 0) {                     // 열주
                int ph = h(x, 2, z) % 3 == 0 ? 2 + h(x, 3, z) % 3 : 8;
                p.set(x, fy, z, s);
                for (int y = 1; y <= ph; y++) p.set(x, fy + y, z, y == 8 ? Blocks.CHISELED_SANDSTONE.defaultBlockState() : Blocks.CUT_SANDSTONE.defaultBlockState());
                if (ph < 8) p.set(x + (dx > 0 ? 1 : -1), fy + 1, z + (dz > 0 ? 2 : -2), Blocks.CUT_SANDSTONE.defaultBlockState());   // 쓰러진 기둥 조각
                return;
            }
        } else {
            s = hh % 3 == 0 ? Blocks.COARSE_DIRT.defaultBlockState() : hh % 5 == 0 ? Blocks.GRAVEL.defaultBlockState() : Blocks.DIRT.defaultBlockState();
            if (hh % 90 == 0) {
                suspicious(p, x, fy, z, true, seed ^ ((long) x * 17 + z));
                return;
            }
        }
        p.set(x, fy, z, s);
        // 단 가장자리 사다리
        int outer = terrace(r + 1.2);
        if (!ramp && outer > fy && hh % 29 == 0) {
            Direction face = Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? Direction.WEST : Direction.EAST) : (dz > 0 ? Direction.NORTH : Direction.SOUTH);
            for (int y = fy + 1; y <= outer; y++) p.set(x, y, z, Blocks.LADDER.defaultBlockState().setValue(net.minecraft.world.level.block.LadderBlock.FACING, face));
        }
    }

    // ───────────────────────── 구덩이 바닥 유적 ─────────────────────────

    /** 무너진 대신전: 기단·앞 계단·열주(일부 부러짐)·반쯤 남은 벽·제단, 바닥의 왕릉 입구 */
    final class Temple extends Feature {
        Temple() {
            super(TEMPLE_X - 16, TEMPLE_Z - 12, TEMPLE_X + 16, TEMPLE_Z + 14);
        }

        @Override
        public void build(Placer p, CityLayout city) {
            int b = FLOOR + 2;
            p.forColumns(TEMPLE_X - 15, TEMPLE_Z - 11, TEMPLE_X + 15, TEMPLE_Z + 13, (x, z) -> {
                int dx = x - TEMPLE_X, dz = z - TEMPLE_Z;
                if (dz > 11) {                                                                  // 앞 계단 (남쪽)
                    if (Math.abs(dx) <= 6) {
                        p.set(x, FLOOR + 1, z, dz == 12 ? Blocks.SANDSTONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH) : Blocks.AIR.defaultBlockState());
                        if (dz == 12) p.set(x, FLOOR + 2, z, Blocks.AIR.defaultBlockState());
                    }
                    if (dz == 12 && Math.abs(dx) <= 6) p.set(x, FLOOR + 1, z, Blocks.CUT_SANDSTONE.defaultBlockState());
                    if (dz == 13 && Math.abs(dx) <= 6) p.set(x, FLOOR + 1, z, Blocks.SANDSTONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH));
                    return;
                }
                for (int y = FLOOR + 1; y <= b; y++) p.set(x, y, z, y == b ? ((dx + dz) % 2 == 0 ? Blocks.CHISELED_SANDSTONE.defaultBlockState() : Blocks.SMOOTH_SANDSTONE.defaultBlockState()) : Blocks.CUT_SANDSTONE.defaultBlockState());
                int m = Math.max(Math.abs(dx), Math.abs(dz) * 15 / 11);
                boolean edge = Math.abs(dx) == 14 || Math.abs(dz) == 10;
                if (edge && Math.floorMod(dx, 4) == 2 - 2 && (Math.abs(dz) == 10 || Math.floorMod(dz, 4) == 0)) {   // 열주
                    int ph = h(x, 4, z) % 3 == 0 ? 2 + h(x, 5, z) % 4 : 10;
                    for (int y = 1; y <= ph; y++) p.set(x, b + y, z, Blocks.CUT_SANDSTONE.defaultBlockState());
                    if (ph == 10) p.set(x, b + 11, z, Blocks.CHISELED_SANDSTONE.defaultBlockState());
                }
                if (Math.abs(dx) <= 8 && Math.abs(dz) <= 6 && (Math.abs(dx) == 8 || dz == -6)) {        // 안쪽 성소 벽 (반쯤 무너짐)
                    int wh = Math.max(0, 7 - Math.abs(dx + dz) / 3 - h(x, 6, z) % 3);
                    for (int y = 1; y <= wh; y++) p.set(x, b + y, z, y == 4 ? Blocks.ORANGE_TERRACOTTA.defaultBlockState() : Blocks.SANDSTONE.defaultBlockState());
                }
                if (m < 12 && h(x, 7, z) % 23 == 0) p.set(x, b + 1, z, Blocks.SAND.defaultBlockState());
            });
            // 제단과 왕릉 입구 (제단 앞 바닥 계단)
            for (int dx = -1; dx <= 1; dx++) p.set(TEMPLE_X + dx, b + 1, TEMPLE_Z + 4, Blocks.CHISELED_SANDSTONE.defaultBlockState());
            p.set(TEMPLE_X, b + 2, TEMPLE_Z + 4, Blocks.GOLD_BLOCK.defaultBlockState());
            p.set(TEMPLE_X - 2, b + 1, TEMPLE_Z + 4, Blocks.DECORATED_POT.defaultBlockState());
            p.set(TEMPLE_X + 2, b + 1, TEMPLE_Z + 4, Blocks.DECORATED_POT.defaultBlockState());
            for (int k = 0; b - k > TOMB_Y; k++) {                                                    // 북쪽으로 내려가는 계단
                int z = TEMPLE_Z - 1 - k;
                for (int dx = -1; dx <= 1; dx++) {
                    int y = b - k;
                    p.set(TEMPLE_X + dx, y, z, Blocks.SANDSTONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH));
                    for (int u = 1; u <= 4; u++) p.set(TEMPLE_X + dx, y + u, z, Blocks.AIR.defaultBlockState());
                    p.set(TEMPLE_X + dx, y - 1, z, Blocks.SANDSTONE.defaultBlockState());
                }
                p.set(TEMPLE_X - 2, b - k + 2, z, Blocks.CHISELED_SANDSTONE.defaultBlockState());
                p.set(TEMPLE_X + 2, b - k + 2, z, Blocks.CHISELED_SANDSTONE.defaultBlockState());
            }
        }
    }

    /** 쓰러진 거상: 동서로 누운 머리·몸통·다리, 부러진 팔, 금 눈 */
    final class Colossus extends Feature {
        final int x0 = CX - 16, z0 = CZ + 36;

        Colossus() {
            super(CX - 22, CZ + 26, CX + 22, CZ + 48);
        }

        @Override
        public void build(Placer p, CityLayout city) {
            int b = FLOOR;
            // 머리 (구, 반지름 5) 동쪽
            int hx = CX + 14, hz = z0;
            p.forColumns(hx - 6, hz - 6, hx + 6, hz + 6, (x, z) -> {
                for (int y = 0; y <= 9; y++) {
                    double d = Math.sqrt(Math.pow(x - hx, 2) + Math.pow(z - hz, 2) + Math.pow(y - 4.5, 2));
                    if (d <= 5.2) p.set(x, b + y, z, d > 4.2 ? (h(x, y, z) % 5 == 0 ? Blocks.CHISELED_SANDSTONE.defaultBlockState() : Blocks.SMOOTH_SANDSTONE.defaultBlockState()) : Blocks.SANDSTONE.defaultBlockState());
                }
            });
            p.set(hx + 2, b + 6, hz - 5, Blocks.GOLD_BLOCK.defaultBlockState());
            p.set(hx - 2, b + 6, hz - 5, Blocks.GOLD_BLOCK.defaultBlockState());
            // 몸통 (눕힌 상자) 서쪽으로, 반쯤 묻힘
            for (int x = x0; x <= hx - 6; x++) for (int dz = -5; dz <= 5; dz++) for (int y = 0; y <= 6; y++) {
                if (Math.abs(dz) == 5 && y > 4) continue;
                p.set(x, b + y, hz + dz, y == 6 || Math.abs(dz) == 5 ? Blocks.CUT_SANDSTONE.defaultBlockState() : Blocks.SANDSTONE.defaultBlockState());
            }
            for (int x = x0; x <= x0 + 10; x++) for (int dz = -2; dz <= 2; dz++) if (h(x, 8, dz) % 3 != 0) p.set(x, b + 7, hz + dz, Blocks.SAND.defaultBlockState());
            // 부러진 팔 (북쪽에 따로 떨어짐)
            for (int k = 0; k < 9; k++) for (int y = 0; y <= 2; y++) p.set(CX - 4 + k, b + y, hz - 10 + (k > 5 ? 1 : 0), Blocks.SMOOTH_SANDSTONE.defaultBlockState());
            suspicious(p, CX + 6, b, hz + 8, false, seed ^ 0xC0105L);
        }
    }

    /** 반쯤 묻힌 집터 */
    final class HouseRuin extends Feature {
        final int x, z;
        final long hs;

        HouseRuin(int x, int z, long hs) {
            super(x - 6, z - 6, x + 6, z + 6);
            this.x = x;
            this.z = z;
            this.hs = hs;
        }

        @Override
        public void build(Placer p, CityLayout city) {
            Random r = new Random(hs);
            int w = 4 + r.nextInt(3), d = 4 + r.nextInt(3);
            Block wall = r.nextBoolean() ? Blocks.SANDSTONE : Blocks.TERRACOTTA;
            for (int dx = -w; dx <= w; dx++) for (int dz = -d; dz <= d; dz++) {
                boolean e = Math.abs(dx) == w || Math.abs(dz) == d;
                int fy = FLOOR;
                if (!e) {
                    if (r.nextInt(9) == 0) suspicious(p, x + dx, fy, z + dz, false, hs + dx * 7L + dz);
                    else p.set(x + dx, fy, z + dz, r.nextInt(3) == 0 ? Blocks.SAND.defaultBlockState() : Blocks.SMOOTH_SANDSTONE.defaultBlockState());
                    continue;
                }
                int hgt = Math.max(0, 4 - Math.abs(dx + dz) / 3 - r.nextInt(3));
                for (int y = 1; y <= hgt; y++) p.set(x + dx, fy + y, z + dz, wall.defaultBlockState());
            }
            if (r.nextInt(3) == 0) p.set(x, FLOOR + 1, z, Blocks.DECORATED_POT.defaultBlockState());
        }
    }

    /** 오벨리스크: 구덩이 둘레, 끝이 금으로 덮인 사암 기둥 */
    final class Obelisk extends Feature {
        final int x, z;

        Obelisk(int x, int z) {
            super(x - 2, z - 2, x + 2, z + 2);
            this.x = x;
            this.z = z;
        }

        @Override
        public void build(Placer p, CityLayout city) {
            int g = KingdomTerrain.column(x, z).h();
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                p.foundation(x + dx, z + dz, g, 6);
                p.set(x + dx, g + 1, z + dz, Blocks.CHISELED_SANDSTONE.defaultBlockState());
            }
            for (int y = 2; y <= 16; y++) p.set(x, g + y, z, y % 5 == 0 ? Blocks.CHISELED_SANDSTONE.defaultBlockState() : Blocks.CUT_SANDSTONE.defaultBlockState());
            p.set(x, g + 17, z, Blocks.GOLD_BLOCK.defaultBlockState());
        }
    }

    /** 발굴 야영지: 구덩이 북서쪽 가장자리 (천막·기중기·비계·발굴 도구 상자) */
    final class Camp extends Feature {
        final int cx, cz;

        Camp() {
            this(CAMP_X, CAMP_Z);
        }

        private Camp(int cx, int cz) {
            super(cx - 8, cz - 8, cx + 8, cz + 8);
            this.cx = cx;
            this.cz = cz;
        }

        @Override
        public void build(Placer p, CityLayout city) {
            int g = KingdomTerrain.column(cx, cz).h();
            for (int dz = -2; dz <= 2; dz++) for (int k = 0; k <= 2; k++) {                   // 천막
                p.set(cx - 2 + k, g + 1 + k, cz + dz, Blocks.WHITE_WOOL.defaultBlockState());
                p.set(cx + 2 - k, g + 1 + k, cz + dz, Blocks.WHITE_WOOL.defaultBlockState());
            }
            for (int y = 1; y <= 9; y++) p.set(cx + 6, g + y, cz, Blocks.SPRUCE_FENCE.defaultBlockState());      // 기중기
            for (int k = 1; k <= 5; k++) p.set(cx + 6 + k, g + 9, cz, Blocks.SPRUCE_FENCE.defaultBlockState());
            for (int y = 4; y <= 8; y++) p.set(cx + 11, g + y, cz, Blocks.CHAIN.defaultBlockState());
            p.set(cx + 11, g + 3, cz, Blocks.BARREL.defaultBlockState());
            for (int y = 1; y <= 6; y++) p.set(cx - 5, g + y, cz + 4, Blocks.SCAFFOLDING.defaultBlockState());
            p.set(cx, g + 1, cz - 4, Blocks.CAMPFIRE.defaultBlockState());
            if (p.pass() == Placer.Pass.SOLID && p.contains(cx + 3, cz + 4)) {
                p.set(cx + 3, g + 1, cz + 4, Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.FACING, Direction.NORTH));
                BlockEntity be = p.level().getBlockEntity(new BlockPos(cx + 3, g + 1, cz + 4));
                if (be instanceof ChestBlockEntity c) {
                    for (int i = 0; i < 4; i++) c.setItem(i, new ItemStack(Items.BRUSH));
                    c.setItem(4, new ItemStack(Items.IRON_SHOVEL));
                    c.setItem(5, new ItemStack(Items.TORCH, 32));
                    c.setItem(6, new ItemStack(Items.BREAD, 8));
                    c.setChanged();
                }
            }
        }
    }

    // ───────────────────────── 왕릉 ─────────────────────────

    /** 대신전 아래: 석관 회랑 → 보물실 → 왕의 묘실 */
    final class Tomb extends Feature {
        Tomb() {
            super(KING_X - 14, KING_Z - 14, TEMPLE_X + 20, TEMPLE_Z);
        }

        private void room(Placer p, int x0, int z0, int x1, int z1, int y, int hgt) {
            for (int x = x0 - 1; x <= x1 + 1; x++) for (int z = z0 - 1; z <= z1 + 1; z++) {
                boolean e = x < x0 || x > x1 || z < z0 || z > z1;
                for (int k = -1; k <= hgt + 1; k++) {
                    BlockState s = e || k == -1 || k == hgt + 1 ? ((x + z + k) % 5 == 0 ? Blocks.CHISELED_SANDSTONE.defaultBlockState() : Blocks.CUT_SANDSTONE.defaultBlockState())
                            : k == 0 ? Blocks.SMOOTH_SANDSTONE.defaultBlockState() : Blocks.AIR.defaultBlockState();
                    if (!e && k == 0) { p.set(x, y, z, s); continue; }
                    if (e && p.get(x, y + k, z).isAir()) continue;                              // 이미 뚫린 곳은 막지 않음
                    p.set(x, y + k, z, s);
                }
            }
        }

        @Override
        public void build(Placer p, CityLayout city) {
            int y = TOMB_Y;
            int stairEnd = TEMPLE_Z - 1 - (FLOOR + 2 - TOMB_Y);
            // 석관 회랑 (계단 끝 → 북쪽)
            int hz1 = stairEnd, hz0 = KING_Z + 12;
            room(p, TEMPLE_X - 2, hz0, TEMPLE_X + 2, hz1, y, 5);
            for (int z = hz0 + 3; z < hz1 - 2; z += 6) for (int s = -1; s <= 1; s += 2) {          // 양옆 석관 감실
                int ax = TEMPLE_X + s * 5;
                room(p, Math.min(ax, ax + s * 2), z - 1, Math.max(ax, ax + s * 2), z + 1, y, 3);
                p.set(ax, y + 1, z, Blocks.SANDSTONE.defaultBlockState());
                p.set(ax + s, y + 1, z, Blocks.SANDSTONE.defaultBlockState());
                p.set(ax, y + 2, z, Blocks.SMOOTH_SANDSTONE_SLAB.defaultBlockState());
                p.set(ax + s, y + 2, z, Blocks.SKELETON_SKULL.defaultBlockState());
                for (int k = 1; k <= 3; k++) p.set(TEMPLE_X + s * 3, y + k, z, Blocks.AIR.defaultBlockState());
            }
            OldCapital.spawner(p, TEMPLE_X, y + 1, (hz0 + hz1) / 2, EntityType.HUSK);
            OldCapital.spawner(p, TEMPLE_X, y + 1, hz0 + 4, EntityType.STRAY);
            for (int z = hz0; z <= hz1; z += 8) p.set(TEMPLE_X, y + 5, z, Blocks.SOUL_LANTERN.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true));
            // 보물실 (회랑 동쪽)
            int tx0 = TEMPLE_X + 8, tz = (hz0 + hz1) / 2;
            room(p, tx0, tz - 4, tx0 + 8, tz + 4, y, 5);
            for (int k = 1; k <= 4; k++) for (int dz = -1; dz <= 1; dz++) p.set(TEMPLE_X + 2 + k, y + 1 + (dz == 0 ? 0 : 0), tz + dz, Blocks.AIR.defaultBlockState());
            for (int k = 1; k <= 4; k++) for (int dz = -1; dz <= 1; dz++) p.set(TEMPLE_X + 2 + k, y + 2, tz + dz, Blocks.AIR.defaultBlockState());
            for (int k = 1; k <= 4; k++) for (int dz = -1; dz <= 1; dz++) p.set(TEMPLE_X + 2 + k, y + 3, tz + dz, Blocks.AIR.defaultBlockState());
            for (int dx = 1; dx <= 7; dx++) for (int dz = -3; dz <= 3; dz++) if ((dx + dz) % 3 == 0) p.set(tx0 + dx, y + 1, tz + dz, Blocks.GOLD_BLOCK.defaultBlockState());
            OldCapital.chest(p, tx0 + 7, y + 1, tz - 3, Direction.WEST, seed ^ 0x91, BuiltInLootTables.DESERT_PYRAMID);
            OldCapital.chest(p, tx0 + 7, y + 1, tz + 3, Direction.WEST, seed ^ 0x92, BuiltInLootTables.DESERT_PYRAMID);
            suspicious(p, tx0 + 4, y, tz, false, seed ^ 0x93);
            // 왕의 묘실 (21 x 21, 기둥 8개, 가운데 높은 단 위 왕의 석관)
            room(p, KING_X - 10, KING_Z - 10, KING_X + 10, KING_Z + 10, y, 9);
            for (int dx = -1; dx <= 1; dx++) for (int k = 1; k <= 4; k++) p.set(KING_X + dx, y + k, KING_Z + 11, Blocks.AIR.defaultBlockState());
            for (int[] c : new int[][]{{-6, -6}, {6, -6}, {-6, 6}, {6, 6}, {-6, 0}, {6, 0}, {0, -8}, {-3, -8}}) {
                if (c[0] == -3) continue;
                for (int k = 1; k <= 9; k++) p.set(KING_X + c[0], y + k, KING_Z + c[1], k % 4 == 0 ? Blocks.CHISELED_SANDSTONE.defaultBlockState() : Blocks.CUT_SANDSTONE.defaultBlockState());
                p.set(KING_X + c[0], y + 6, KING_Z + c[1] + 1, Blocks.SOUL_LANTERN.defaultBlockState());
            }
            for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) p.set(KING_X + dx, y + 1, KING_Z + dz, Blocks.CHISELED_SANDSTONE.defaultBlockState());
            for (int dx = -1; dx <= 1; dx++) for (int dz = -2; dz <= 2; dz++) p.set(KING_X + dx, y + 2, KING_Z + dz, dx == 0 ? Blocks.GOLD_BLOCK.defaultBlockState() : Blocks.SMOOTH_SANDSTONE.defaultBlockState());
            p.set(KING_X, y + 3, KING_Z - 2, Blocks.SKELETON_SKULL.defaultBlockState());
            OldCapital.chest(p, KING_X, y + 2, KING_Z - 7, Direction.SOUTH, seed ^ 0x94, BuiltInLootTables.ANCIENT_CITY);
        }
    }
}
