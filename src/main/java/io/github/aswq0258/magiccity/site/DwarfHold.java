package io.github.aswq0258.magiccity.site;

import io.github.aswq0258.magiccity.build.BuildLayout;
import io.github.aswq0258.magiccity.build.Placer;
import io.github.aswq0258.magiccity.city.CityLayout;
import io.github.aswq0258.magiccity.city.Palette;
import io.github.aswq0258.magiccity.city.Royal;
import io.github.aswq0258.magiccity.city.Shapes;
import io.github.aswq0258.magiccity.city.feature.Feature;
import io.github.aswq0258.magiccity.world.KingdomTerrain;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;

import java.util.List;

/**
 * 드워프 광산왕국 (마스터플랜 D1) — 엘프 산악길 끝, 북서 산괴 속을 파고 들어간 지하 왕국.
 *
 *  바깥: 골짜기의 교역 마당(노점·모루 진열), 계단식 거대 파사드 "산의 문", 양옆의 드워프 왕 거상 2개, 강철 골렘 경비
 *  산속(지면 높이 F):
 *   - 대전당 61 x 59, 높이 24: 거대한 기둥 16개, 양옆 용암 수로, 사슬 등불, 가운데 드워프 왕 석상, 붉은 융단
 *   - 북쪽 옥좌실: 3단 단상의 금 옥좌, 보물 상자
 *   - 서쪽 대장간: 모루·용광로·숫돌·대장장이 작업대, 유리 너머로 흘러내리는 용암 폭포, 장인의 5×5 작업대 단
 *     (5장 보스 "용광로의 거신"은 이제 심층 광산 {@link DeepMine} 의 고대 용광로 홀에서 깨어남)
 *   - 동쪽 주거 회랑: 드워프 집 8칸
 *   - 회랑 끝 계단 갱도 → 16칸 아래 광산: 갱도 광장, 레일 깔린 갱도 3개, 벽마다 광맥,
 *     동쪽 끝 자수정 정동, 북쪽 끝 "잃어버린 광부의 갱도" (스포너 던전)
 *  드워프 주민(작은 키) 과 성 안 조명. 근처는 길 몬스터가 나오지 않는 안전 지대.
 */
public final class DwarfHold implements BuildLayout {
    public static final int CX = KingdomTerrain.worldX(-1690), CZ = KingdomTerrain.worldZ(-1070);
    /** 산속 단지의 범위 (조명·안전 지대) */
    private static final int X0 = CX - 80, X1 = CX + 186, Z0 = CZ - 222, Z1 = CZ - 20;

    private final int F;
    private final long seed;
    private final Hold hold;

    /** 왕국 바닥 높이 (대전당·대장간 바닥) */
    public static int floorAt() {
        return KingdomTerrain.column(CX, CZ - 50).h();
    }

    public DwarfHold(long seed) {
        this.seed = seed;
        this.F = floorAt();
        this.hold = new Hold();
    }

    public int floorY() {
        return F;
    }

    @Override public int cx() { return CX; }
    @Override public int cz() { return CZ - 100; }
    @Override public int base() { return F; }
    @Override public int radius() { return 0; }
    @Override public CityLayout city() { return null; }
    @Override public int shapeMinY() { return F - 30; }
    @Override public int shapeMaxY() { return F + 50; }

    @Override
    public List<ChunkPos> chunkList() {
        List<ChunkPos> out = new java.util.ArrayList<>();
        for (int x = (hold.minX - 1) >> 4; x <= (hold.maxX + 1) >> 4; x++)
            for (int z = (hold.minZ - 1) >> 4; z <= (hold.maxZ + 1) >> 4; z++) out.add(new ChunkPos(x, z));
        return out;
    }

    @Override
    public List<Feature> featuresIn(ChunkPos cp) {
        return List.of(hold);
    }

    @Override
    public void buildColumn(Placer p, int x, int z) {}

    /** 산속 전체 조명 (잃어버린 광부의 갱도 던전만 어둡게) */
    @Override
    public int[] lightRange(int x, int z) {
        if (x < X0 || x > X1 || z < Z0 || z > Z1) return null;
        if (x >= CX + 102 && x <= CX + 118 && z <= CZ - 204) return null;
        return new int[]{F - 24, F + 30};
    }

    public static boolean near(double x, double z) {
        return x > X0 - 60 && x < X1 + 20 && z > Z0 - 20 && z < CZ + 60;
    }

    private int h(int x, int y, int z) {
        return Shapes.hash(x, y, z, seed);
    }

    private static BlockState b(Block block) {
        return block.defaultBlockState();
    }

    /** `fantasyrpg`가 5×5 작업대를 등록했으면 그 블록, 없으면 바닐라 작업대. ID 는 확정되면 맨 앞에 둠 */
    static final String[] BENCH_IDS = {"fantasyrpg:master_workbench", "fantasyrpg:workbench_5x5", "fantasyrpg:crafting_table_5x5"};

    static BlockState benchBlock() {
        for (String id : BENCH_IDS) {
            net.minecraft.resources.ResourceLocation rl = net.minecraft.resources.ResourceLocation.tryParse(id);
            if (rl == null) continue;
            java.util.Optional<Block> blk = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getOptional(rl);
            if (blk.isPresent() && blk.get() != Blocks.AIR) return blk.get().defaultBlockState();
        }
        return Blocks.CRAFTING_TABLE.defaultBlockState();
    }

    @FunctionalInterface
    interface Mat {
        BlockState at(int x, int y, int z);
    }

    private static Mat of(BlockState s) {
        return (x, y, z) -> s;
    }

    // ───────────────────────── 조립 도구 ─────────────────────────

    /** 벽·바닥·천장으로 둘러싼 방 (산이 얇아도 닫힌 공간). 안쪽 x0..x1, z0..z1, 바닥 y, 높이 hgt */
    static void room(Placer p, int x0, int z0, int x1, int z1, int y, int hgt, Mat wall, Mat floor, Mat ceil) {
        p.forColumns(x0 - 1, z0 - 1, x1 + 1, z1 + 1, (x, z) -> {
            boolean border = x == x0 - 1 || x == x1 + 1 || z == z0 - 1 || z == z1 + 1;
            if (border) {
                for (int yy = y; yy <= y + hgt + 1; yy++) p.set(x, yy, z, wall.at(x, yy, z));
            } else {
                p.set(x, y, z, floor.at(x, y, z));
                for (int yy = y + 1; yy <= y + hgt; yy++) p.set(x, yy, z, Blocks.AIR.defaultBlockState());
                p.set(x, y + hgt + 1, z, ceil.at(x, y + hgt + 1, z));
            }
        });
    }

    /** 벽 뚫기 (통로) */
    static void carve(Placer p, int x0, int z0, int x1, int z1, int y0, int y1) {
        p.forColumns(x0, z0, x1, z1, (x, z) -> {
            for (int y = y0; y <= y1; y++) p.set(x, y, z, Blocks.AIR.defaultBlockState());
        });
    }

    static void box(Placer p, int x0, int y0, int z0, int x1, int y1, int z1, BlockState s) {
        p.forColumns(Math.min(x0, x1), Math.min(z0, z1), Math.max(x0, x1), Math.max(z0, z1), (x, z) -> {
            for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) p.set(x, y, z, s);
        });
    }

    /**
     * 드워프 왕 석상 (남쪽을 바라봄). s = 크기 배율 (1 = 높이 약 14)
     * 단위 좌표 u(동서) v(높이) w(남북, + = 앞) 의 상자들을 s 배로 키워 쌓음
     */
    static void statue(Placer p, int x, int y, int z, double s) {
        BlockState armor = b(Blocks.POLISHED_BLACKSTONE_BRICKS), tile = b(Blocks.DEEPSLATE_TILES), gold = b(Blocks.GOLD_BLOCK),
                face = b(Blocks.SMOOTH_STONE), beard = b(Blocks.CALCITE), iron = b(Blocks.IRON_BLOCK), haft = b(Blocks.DARK_OAK_LOG);
        Object[][] parts = {
                {-3, -1, 0, 3, -1, 1, tile}, {0, 2, 0, 3, -1, 1, tile},          // 다리
                {-4, 3, 4, 8, -2, 2, armor}, {-4, 3, 4, 4, -2, 2, gold},          // 몸통, 금 허리띠
                {-6, -5, 4, 8, -1, 1, armor}, {4, 5, 4, 8, -1, 1, armor},          // 팔
                {-6, -5, 3, 3, -1, 1, gold}, {4, 5, 3, 3, -1, 1, gold},            // 손
                {-2, 1, 9, 11, -2, 1, face},                                       // 머리
                {-2, 1, 4, 9, 2, 2, beard}, {-1, 0, 3, 5, 3, 3, beard},            // 수염
                {-3, 2, 12, 12, -3, 2, gold}, {-3, -3, 13, 14, 0, 0, gold}, {2, 2, 13, 14, 0, 0, gold}, {0, -1, 13, 15, -1, -1, gold}, // 투구·뿔·볏
                {6, 6, 0, 12, 0, 0, haft}, {6, 8, 10, 12, -2, 2, iron},            // 도끼
        };
        for (Object[] q : parts) {
            int u0 = (Integer) q[0], u1 = (Integer) q[1], v0 = (Integer) q[2], v1 = (Integer) q[3], w0 = (Integer) q[4], w1 = (Integer) q[5];
            box(p, x + (int) Math.floor(u0 * s), y + (int) Math.floor(v0 * s), z + (int) Math.floor(w0 * s),
                    x + (int) Math.floor((u1 + 1) * s) - 1, y + (int) Math.floor((v1 + 1) * s) - 1, z + (int) Math.floor((w1 + 1) * s) - 1, (BlockState) q[6]);
        }
    }

    // ───────────────────────── 왕국 ─────────────────────────

    public final class Hold extends Feature {
        Hold() {
            super(CX - 82, CZ - 224, CX + 188, CZ - 18);
            groundY = F;
        }

        @Override
        public void build(Placer p, CityLayout city) {
            final Mat deepWall = (x, y, z) -> h(x, y, z) % 9 == 0 ? b(Blocks.CRACKED_DEEPSLATE_BRICKS) : b(Blocks.DEEPSLATE_BRICKS);
            final Mat hallFloor = (x, y, z) -> ((x + z) & 1) == 0 ? b(Blocks.POLISHED_DEEPSLATE) : b(Blocks.DEEPSLATE_TILES);
            final Mat tiles = of(b(Blocks.DEEPSLATE_TILES));
            courtyard(p);
            facade(p);
            // 입구 통로 → 대전당
            room(p, CX - 5, CZ - 114, CX + 5, CZ - 105, F, 13, deepWall, hallFloor, tiles);
            carve(p, CX - 4, CZ - 104, CX + 4, CZ - 95, F + 1, F + 13);
            greatHall(p, deepWall, hallFloor, tiles);
            carve(p, CX - 4, CZ - 116, CX + 4, CZ - 115, F + 1, F + 13);
            throneRoom(p);
            forge(p, deepWall);
            homes(p, deepWall);
            mines(p);
        }

        // ── 바깥 교역 마당 ──
        private void courtyard(Placer p) {
            p.forColumns(CX - 36, CZ - 94, CX + 36, CZ - 22, (x, z) -> {
                int k = h(x, 1, z);
                boolean edge = x == CX - 36 || x == CX + 36;
                RoadSites.pad(p, x, z, F, Math.abs(x - CX) <= 3 ? b(Blocks.POLISHED_BLACKSTONE_BRICKS)
                        : k % 5 == 0 ? b(Blocks.ANDESITE) : k % 3 == 0 ? b(Blocks.STONE_BRICKS) : b(Blocks.POLISHED_ANDESITE));
                if (edge && (z & 1) == 0) p.set(x, F + 1, z, b(Blocks.STONE_BRICK_WALL));
            });
            // 노점 4곳
            int[][] stalls = {{CX - 20, CZ - 60}, {CX + 20, CZ - 60}, {CX - 20, CZ - 38}, {CX + 20, CZ - 38}};
            for (int i = 0; i < stalls.length; i++) {
                int sx = stalls[i][0], sz = stalls[i][1];
                BlockState cloth = i % 2 == 0 ? b(Blocks.RED_WOOL) : b(Blocks.YELLOW_WOOL);
                for (int dx = -3; dx <= 3; dx++) for (int dz = -2; dz <= 2; dz++) {
                    if (Math.abs(dx) == 3 && Math.abs(dz) == 2) for (int y = 1; y <= 3; y++) p.set(sx + dx, F + y, sz + dz, b(Blocks.SPRUCE_FENCE));
                    p.set(sx + dx, F + 4, sz + dz, ((dx + 10) % 2 == 0) ? cloth : b(Blocks.WHITE_WOOL));
                    if (dz == 1 && Math.abs(dx) <= 2) p.set(sx + dx, F + 1, sz + dz, dx == 0 ? b(Blocks.ANVIL) : b(Blocks.BARREL));
                }
                p.set(sx, F + 3, sz, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
            }
            for (int[] q : new int[][]{{CX - 8, CZ - 30}, {CX + 8, CZ - 30}, {CX - 8, CZ - 70}, {CX + 8, CZ - 70}}) {
                p.set(q[0], F + 1, q[1], b(Blocks.POLISHED_BLACKSTONE_BRICK_WALL));
                p.set(q[0], F + 2, q[1], b(Blocks.POLISHED_BLACKSTONE_BRICK_WALL));
                p.set(q[0], F + 3, q[1], b(Blocks.LANTERN));
            }
            p.set(CX, F + 1, CZ - 48, b(Blocks.CAMPFIRE));
        }

        // ── 산의 문: 계단식 파사드 + 거상 ──
        private void facade(Placer p) {
            p.forColumns(CX - 38, CZ - 104, CX + 38, CZ - 95, (x, z) -> {
                int ax = Math.abs(x - CX);
                int top = ax <= 12 ? 46 : ax <= 22 ? 38 : ax <= 30 ? 30 : 22;
                boolean front = z == CZ - 95;
                for (int y = F - 2; y <= F + top; y++) {
                    BlockState s;
                    if (y == F + top) s = b(Blocks.CHISELED_DEEPSLATE);
                    else if (front) {
                        boolean strip = ax == 9 || ax == 17 || ax == 26;
                        boolean band = (y - F) % 12 == 0;
                        s = strip ? ((y & 1) == 0 ? b(Blocks.GOLD_BLOCK) : b(Blocks.SHROOMLIGHT))
                                : band ? b(Blocks.POLISHED_BLACKSTONE_BRICKS)
                                : h(x, y, z) % 11 == 0 ? b(Blocks.CRACKED_DEEPSLATE_BRICKS) : b(Blocks.DEEPSLATE_BRICKS);
                    } else s = b(Blocks.POLISHED_DEEPSLATE);
                    p.set(x, y, z, s);
                }
                // 계단식 턱마다 가장자리 장식
                if (front && (ax == 12 || ax == 22 || ax == 30)) p.set(x, F + top + 1, z, b(Blocks.POLISHED_BLACKSTONE_BRICK_WALL));
            });
            // 문 테두리 (금) + 망치 문장
            for (int y = F + 1; y <= F + 15; y++) for (int s = -1; s <= 1; s += 2) p.set(CX + s * 5, y, CZ - 95, b(Blocks.GOLD_BLOCK));
            for (int x = CX - 5; x <= CX + 5; x++) p.set(x, F + 15, CZ - 95, b(Blocks.GOLD_BLOCK));
            for (int y = F + 22; y <= F + 34; y++) p.set(CX, y, CZ - 95, b(Blocks.GOLD_BLOCK));
            box(p, CX - 4, F + 31, CZ - 95, CX + 4, F + 35, CZ - 95, b(Blocks.GOLD_BLOCK));
            box(p, CX - 3, F + 32, CZ - 95, CX + 3, F + 34, CZ - 95, b(Blocks.RAW_GOLD_BLOCK));
            for (int s = -1; s <= 1; s += 2) {
                // 거상 받침대 + 거상
                box(p, CX + s * 24 - 5, F + 1, CZ - 93, CX + s * 24 + 5, F + 2, CZ - 87, b(Blocks.POLISHED_BLACKSTONE_BRICKS));
                statue(p, CX + s * 24, F + 3, CZ - 90, 1.7);
                p.set(CX + s * 7, F + 1, CZ - 93, b(Blocks.POLISHED_BLACKSTONE_BRICK_WALL));
                p.set(CX + s * 7, F + 2, CZ - 93, b(Blocks.LANTERN));
                p.set(CX + s * 4, F + 10, CZ - 94, Royal.redBanner(Direction.SOUTH));
            }
        }

        // ── 대전당 ──
        private void greatHall(Placer p, Mat wall, Mat floor, Mat ceil) {
            int hx0 = CX - 30, hx1 = CX + 30, hz0 = CZ - 175, hz1 = CZ - 117, H = 24;
            room(p, hx0, hz0, hx1, hz1, F, H, wall, floor, ceil);
            // 용암 수로 (양옆)
            for (int s = -1; s <= 1; s += 2) {
                for (int z = hz0 + 3; z <= hz1 - 3; z++) {
                    for (int c = 25; c <= 26; c++) {
                        p.set(CX + s * c, F - 1, z, b(Blocks.MAGMA_BLOCK));
                        p.set(CX + s * c, F, z, b(Blocks.LAVA));
                    }
                    p.set(CX + s * 24, F, z, b(Blocks.POLISHED_BLACKSTONE));
                    p.set(CX + s * 27, F, z, b(Blocks.POLISHED_BLACKSTONE));
                    p.set(CX + s * 24, F + 1, z, b(Blocks.POLISHED_BLACKSTONE_BRICK_WALL));
                }
                // 벽 깃발
                for (int z = hz0 + 6; z <= hz1 - 6; z += 10) p.set(CX + s * 30, F + 14, z, Royal.redBanner(s > 0 ? Direction.WEST : Direction.EAST));
            }
            // 기둥 16개
            for (int i = 0; i < 4; i++) for (int j = 0; j < 4; j++) {
                int px = CX - 18 + i * 12, pz = hz0 + 10 + j * 13;
                for (int y = F + 1; y <= F + H; y++) {
                    BlockState s = y == F + 1 || y == F + H - 1 ? b(Blocks.GOLD_BLOCK)
                            : y == F + 2 || y == F + H - 2 ? b(Blocks.CHISELED_DEEPSLATE) : b(Blocks.POLISHED_DEEPSLATE);
                    box(p, px - 1, y, pz - 1, px + 1, y, pz + 1, s);
                }
                for (int d = -2; d <= 2; d += 4) {                               // 기둥 머리 받침
                    p.set(px + d, F + H, pz, b(Blocks.POLISHED_DEEPSLATE));
                    p.set(px, F + H, pz + d, b(Blocks.POLISHED_DEEPSLATE));
                }
            }
            // 사슬 등불
            for (int x = CX - 12; x <= CX + 12; x += 12) for (int z = hz0 + 4; z <= hz1 - 4; z += 13) {
                if (x == CX && Math.abs(z - (CZ - 146)) < 5) continue;
                for (int y = F + 19; y <= F + H; y++) p.set(x, y, z, b(Blocks.CHAIN));
                p.set(x, F + 18, z, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
            }
            // 붉은 융단 (입구 → 옥좌실), 가운데 왕 석상
            for (int z = hz0; z <= hz1; z++) for (int x = CX - 1; x <= CX + 1; x++) {
                if (Math.abs(z - (CZ - 146)) <= 4) continue;
                p.set(x, F + 1, z, Math.abs(x - CX) == 1 ? b(Blocks.YELLOW_CARPET) : b(Blocks.RED_CARPET));
            }
            box(p, CX - 4, F + 1, CZ - 149, CX + 4, F + 2, CZ - 143, b(Blocks.POLISHED_BLACKSTONE_BRICKS));
            statue(p, CX, F + 3, CZ - 146, 1.0);
        }

        // ── 옥좌실 ──
        private void throneRoom(Placer p) {
            Mat wall = (x, y, z) -> (y - F) % 5 == 0 ? b(Blocks.GILDED_BLACKSTONE) : b(Blocks.POLISHED_BLACKSTONE_BRICKS);
            room(p, CX - 12, CZ - 206, CX + 12, CZ - 178, F, 16, wall, of(b(Blocks.POLISHED_BLACKSTONE)), of(b(Blocks.POLISHED_BLACKSTONE_BRICKS)));
            carve(p, CX - 2, CZ - 177, CX + 2, CZ - 176, F + 1, F + 6);
            for (int z = CZ - 196; z <= CZ - 178; z++) p.set(CX, F + 1, z, b(Blocks.RED_CARPET));
            for (int k = 0; k < 3; k++) box(p, CX - 6 + k, F + 1 + k, CZ - 205, CX + 6 - k, F + 1 + k, CZ - 199 + k, b(Blocks.POLISHED_DEEPSLATE));
            // 옥좌
            p.set(CX, F + 4, CZ - 203, Blocks.POLISHED_BLACKSTONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH));
            box(p, CX - 1, F + 4, CZ - 204, CX + 1, F + 9, CZ - 204, b(Blocks.GOLD_BLOCK));
            p.set(CX - 1, F + 4, CZ - 203, b(Blocks.GOLD_BLOCK));
            p.set(CX + 1, F + 4, CZ - 203, b(Blocks.GOLD_BLOCK));
            p.set(CX, F + 10, CZ - 204, b(Blocks.RAW_GOLD_BLOCK));
            for (int s = -1; s <= 1; s += 2) {
                p.set(CX + s * 2, F + 10, CZ - 204, b(Blocks.GOLD_BLOCK));
                for (int z = CZ - 196; z <= CZ - 180; z += 4) {
                    p.set(CX + s * 12, F + 8, z, Royal.redBanner(s > 0 ? Direction.WEST : Direction.EAST));
                    p.set(CX + s * 6, F + 1, z, b(Blocks.POLISHED_BLACKSTONE_BRICK_WALL));
                    p.set(CX + s * 6, F + 2, z, b(Blocks.LANTERN));
                }
                OldCapital.chest(p, CX + s * 4, F + 4, CZ - 204, Direction.SOUTH, seed ^ (s + 5), BuiltInLootTables.BURIED_TREASURE);
            }
            for (int x = CX - 8; x <= CX + 8; x += 8) for (int y = F + 13; y <= F + 16; y++) p.set(x, y, CZ - 192, b(Blocks.CHAIN));
            for (int x = CX - 8; x <= CX + 8; x += 8) p.set(x, F + 12, CZ - 192, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        }

        // ── 대장간 ──
        private void forge(Placer p, Mat wall) {
            int x0 = CX - 72, x1 = CX - 33, z0 = CZ - 165, z1 = CZ - 125;
            room(p, x0, z0, x1, z1, F, 12, wall, (x, y, z) -> h(x, 2, z) % 4 == 0 ? b(Blocks.COBBLED_DEEPSLATE) : b(Blocks.POLISHED_DEEPSLATE), of(b(Blocks.DEEPSLATE_TILES)));
            carve(p, CX - 32, CZ - 148, CX - 31, CZ - 144, F + 1, F + 6);
            // 용암 폭포 (북쪽 벽 홈, 앞은 유리)
            for (int x = x0 + 6; x <= x1 - 4; x += 12) {
                for (int y = F + 1; y <= F + 12; y++) {
                    p.set(x, y, z0 - 2, b(Blocks.DEEPSLATE_BRICKS));
                    p.set(x - 1, y, z0 - 1, b(Blocks.DEEPSLATE_BRICKS));
                    p.set(x + 1, y, z0 - 1, b(Blocks.DEEPSLATE_BRICKS));
                    p.set(x, y, z0 - 1, b(Blocks.LAVA));
                    p.set(x, y, z0, b(Blocks.ORANGE_STAINED_GLASS));
                }
                p.set(x, F + 13, z0 - 1, b(Blocks.DEEPSLATE_BRICKS));
                p.set(x, F, z0 - 1, b(Blocks.MAGMA_BLOCK));
            }
            // 작업대 줄
            Block[] row = {Blocks.ANVIL, Blocks.SMITHING_TABLE, Blocks.GRINDSTONE, Blocks.BLAST_FURNACE, Blocks.ANVIL, Blocks.BARREL, Blocks.BLAST_FURNACE, Blocks.CHIPPED_ANVIL};
            for (int i = 0; i < row.length; i++) {
                for (int z : new int[]{z0 + 8, z1 - 8}) p.set(x0 + 4 + i * 4, F + 1, z, b(row[i]));
            }
            // 가운데 용광로 탑 (5 x 5)
            int fx = (x0 + x1) / 2, fz = (z0 + z1) / 2;
            for (int y = F + 1; y <= F + 12; y++) for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                boolean core = Math.abs(dx) <= 1 && Math.abs(dz) <= 1;
                boolean face = (dx == 0 || dz == 0) && !core && y <= F + 2;
                p.set(fx + dx, y, fz + dz, core ? (y <= F + 3 ? b(Blocks.MAGMA_BLOCK) : b(Blocks.BRICKS)) : face ? b(Blocks.BLAST_FURNACE) : b(Blocks.BRICKS));
            }
            for (int s = -1; s <= 1; s += 2) {
                p.set(fx + s * 3, F + 1, fz, b(Blocks.CAULDRON));
                p.set(fx, F + 1, fz + s * 3, b(Blocks.SMOKER));
            }
            masterBench(p, CX - 44, CZ - 152);
            OldCapital.chest(p, x0 + 1, F + 1, z1 - 1, Direction.EAST, seed ^ 31, BuiltInLootTables.VILLAGE_TOOLSMITH);
            OldCapital.chest(p, x0 + 1, F + 1, z1 - 3, Direction.EAST, seed ^ 32, BuiltInLootTables.VILLAGE_WEAPONSMITH);
            for (int x = x0 + 6; x <= x1 - 4; x += 10) for (int z = z0 + 10; z <= z1 - 10; z += 10) {
                p.set(x, F + 12, z, b(Blocks.CHAIN));
                p.set(x, F + 11, z, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
            }
        }

        /**
         * 장인의 5×5 작업대: 대장간 북쪽의 5 x 5 단. 가운데에 `fantasyrpg` 5×5 작업대 블록을 ID 로 찾아 놓고,
         * 없으면 바닐라 작업대를 놓음 (의존성 없이). 둘레는 금 모서리·모루·대장장이 작업대, 위에는 사슬 등불 지붕.
         */
        private void masterBench(Placer p, int cx, int cz) {
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                boolean corner = Math.abs(dx) == 2 && Math.abs(dz) == 2;
                boolean edge = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                p.set(cx + dx, F, cz + dz, corner ? b(Blocks.GOLD_BLOCK) : edge ? b(Blocks.POLISHED_BLACKSTONE_BRICKS) : b(Blocks.CHISELED_POLISHED_BLACKSTONE));
                p.set(cx + dx, F + 1, cz + dz, Blocks.AIR.defaultBlockState());
                if (corner) for (int y = F + 1; y <= F + 5; y++) p.set(cx + dx, y, cz + dz, b(Blocks.POLISHED_BLACKSTONE_BRICK_WALL));
                p.set(cx + dx, F + 6, cz + dz, edge ? b(Blocks.POLISHED_BLACKSTONE_BRICK_SLAB) : b(Blocks.DEEPSLATE_TILE_SLAB));
            }
            p.set(cx, F + 1, cz, benchBlock());
            p.set(cx - 1, F + 1, cz, b(Blocks.SMITHING_TABLE));
            p.set(cx + 1, F + 1, cz, b(Blocks.ANVIL));
            p.set(cx, F + 1, cz - 1, b(Blocks.STONECUTTER));
            for (int y = F + 7; y <= F + 12; y++) p.set(cx, y, cz, b(Blocks.CHAIN));
            p.set(cx, F + 5, cz, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
            for (int s = -1; s <= 1; s += 2) p.set(cx + s * 2, F + 6, cz + 3, Royal.redBanner(Direction.SOUTH));
        }

        // ── 주거 회랑 ──
        private void homes(Placer p, Mat wall) {
            Mat stoneFloor = of(b(Blocks.POLISHED_ANDESITE));
            room(p, CX + 33, CZ - 147, CX + 84, CZ - 143, F, 6, wall, (x, y, z) -> (x & 3) == 0 ? b(Blocks.DEEPSLATE_TILES) : b(Blocks.POLISHED_DEEPSLATE), of(b(Blocks.DEEPSLATE_TILES)));
            carve(p, CX + 31, CZ - 147, CX + 32, CZ - 143, F + 1, F + 5);
            int[] xs = {CX + 36, CX + 47, CX + 58, CX + 69};
            for (int i = 0; i < xs.length; i++) {
                int rx = xs[i];
                for (int side = 0; side < 2; side++) {
                    int z0 = side == 0 ? CZ - 157 : CZ - 140, z1 = z0 + 7;
                    room(p, rx, z0, rx + 8, z1, F, 4, of(b(Blocks.STONE_BRICKS)), stoneFloor, of(b(Blocks.SPRUCE_PLANKS)));
                    int doorZ0 = side == 0 ? CZ - 149 : CZ - 142, doorZ1 = doorZ0 + 1;
                    carve(p, rx + 4, doorZ0, rx + 4, doorZ1, F + 1, F + 2);
                    int back = side == 0 ? z0 : z1, front = side == 0 ? z1 : z0;
                    Direction toBack = side == 0 ? Direction.NORTH : Direction.SOUTH;
                    // 침대 (뒤쪽 벽으로 머리), 화로, 통, 상자, 작업대, 양탄자
                    int bedHead = back, bedFoot = back + (side == 0 ? 1 : -1);
                    p.set(rx + 1, F + 1, bedHead, Palette.bed(toBack, true));
                    p.set(rx + 1, F + 1, bedFoot, Palette.bed(toBack, false));
                    p.set(rx + 7, F + 1, back, b(Blocks.FURNACE));
                    p.set(rx + 6, F + 1, back, b(Blocks.BARREL));
                    p.set(rx + 8, F + 1, back, b(Blocks.CRAFTING_TABLE));
                    OldCapital.chest(p, rx + 8, F + 1, front, toBack, seed + i * 7L + side, BuiltInLootTables.VILLAGE_PLAINS_HOUSE);
                    for (int x = rx + 3; x <= rx + 5; x++) for (int z = Math.min(back, front) + 2; z <= Math.max(back, front) - 2; z++)
                        p.set(x, F + 1, z, (i + side) % 2 == 0 ? b(Blocks.RED_CARPET) : b(Blocks.BROWN_CARPET));
                    p.set(rx + 4, F + 4, (back + front) / 2, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
                }
            }
            for (int x = CX + 38; x <= CX + 82; x += 8) p.set(x, F + 6, CZ - 145, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        }

        // ── 광산 ──
        private void mines(Placer p) {
            carve(p, CX + 85, CZ - 147, CX + 85, CZ - 143, F + 1, F + 5);
            // 계단 갱도: 16칸 내려감
            for (int i = 0; i <= 16; i++) {
                int x = CX + 86 + i, fy = F - i;
                for (int z = CZ - 148; z <= CZ - 142; z++) {
                    boolean border = z == CZ - 148 || z == CZ - 142;
                    if (p.contains(x, z)) {
                        for (int y = fy - 1; y <= fy + 7; y++) {
                            BlockState s;
                            if (border || y == fy + 7) s = b(Blocks.DEEPSLATE_BRICKS);
                            else if (y == fy - 1) s = b(Blocks.COBBLED_DEEPSLATE);
                            else if (y == fy) s = i == 16 ? b(Blocks.POLISHED_DEEPSLATE) : Blocks.DEEPSLATE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.WEST);
                            else s = Blocks.AIR.defaultBlockState();
                            p.set(x, y, z, s);
                        }
                    }
                }
                if (i % 4 == 2) p.set(x, F - i + 5, CZ - 145, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
            }
            int M = F - 16;
            Mat rock = (x, y, z) -> {
                int k = h(x, y, z) % 400;
                return k < 6 ? b(Blocks.IRON_ORE) : k < 10 ? b(Blocks.COAL_ORE) : k < 13 ? b(Blocks.COPPER_ORE) : k < 15 ? b(Blocks.GOLD_ORE)
                        : k < 16 ? b(Blocks.DIAMOND_ORE) : k < 18 ? b(Blocks.REDSTONE_ORE) : k < 19 ? b(Blocks.LAPIS_ORE)
                        : k < 90 ? b(Blocks.TUFF) : k < 150 ? b(Blocks.ANDESITE) : b(Blocks.STONE);
            };
            Mat gravelFloor = (x, y, z) -> h(x, y, z) % 3 == 0 ? b(Blocks.GRAVEL) : b(Blocks.COBBLESTONE);
            // 갱도 광장
            room(p, CX + 104, CZ - 152, CX + 116, CZ - 138, M, 6, rock, gravelFloor, rock);
            carve(p, CX + 103, CZ - 147, CX + 103, CZ - 143, M + 1, M + 5);
            box(p, CX + 104, M + 1, CZ - 151, CX + 104, M + 1, CZ - 149, b(Blocks.BARREL));
            p.set(CX + 116, M + 1, CZ - 139, b(Blocks.CRAFTING_TABLE));
            for (int[] q : new int[][]{{CX + 106, CZ - 140}, {CX + 114, CZ - 150}}) {
                p.set(q[0], M + 1, q[1], b(Blocks.SPRUCE_FENCE));
                p.set(q[0], M + 2, q[1], b(Blocks.LANTERN));
            }
            // 갱도 3개 (북·동·남), 레일과 버팀목
            tunnel(p, CX + 109, CZ - 205, CX + 111, CZ - 154, M, rock, true);
            tunnel(p, CX + 118, CZ - 146, CX + 168, CZ - 144, M, rock, false);
            tunnel(p, CX + 109, CZ - 136, CX + 111, CZ - 112, M, rock, true);
            carve(p, CX + 109, CZ - 153, CX + 111, CZ - 153, M + 1, M + 4);
            carve(p, CX + 117, CZ - 146, CX + 117, CZ - 144, M + 1, M + 4);
            carve(p, CX + 109, CZ - 137, CX + 111, CZ - 137, M + 1, M + 4);
            geode(p, CX + 176, M + 4, CZ - 145);
            lostMine(p, M);
        }

        /** 레일 깔린 갱도 (폭 3, 높이 4), 6칸마다 버팀목 + 등불 */
        private void tunnel(Placer p, int x0, int z0, int x1, int z1, int M, Mat rock, boolean northSouth) {
            room(p, x0, z0, x1, z1, M, 4, rock, (x, y, z) -> h(x, y, z) % 3 == 0 ? b(Blocks.GRAVEL) : b(Blocks.COBBLESTONE), rock);
            int mid = northSouth ? (x0 + x1) / 2 : (z0 + z1) / 2;
            int a0 = northSouth ? z0 : x0, a1 = northSouth ? z1 : x1;
            for (int a = a0; a <= a1; a++) {
                int x = northSouth ? mid : a, z = northSouth ? a : mid;
                p.set(x, M + 1, z, Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE, northSouth ? RailShape.NORTH_SOUTH : RailShape.EAST_WEST));
                if ((a - a0) % 6 == 3) {
                    for (int s = -1; s <= 1; s += 2) {
                        int px = northSouth ? mid + s : a, pz = northSouth ? a : mid + s;
                        for (int y = M + 1; y <= M + 3; y++) p.set(px, y, pz, b(Blocks.SPRUCE_FENCE));
                    }
                    for (int s = -1; s <= 1; s++) {
                        int px = northSouth ? mid + s : a, pz = northSouth ? a : mid + s;
                        p.set(px, M + 4, pz, b(Blocks.SPRUCE_PLANKS));
                    }
                    int lx = northSouth ? mid + 1 : a, lz = northSouth ? a : mid + 1;
                    p.set(lx, M + 3, lz, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
                }
            }
        }

        /** 자수정 정동 (동쪽 갱도 끝) */
        private void geode(Placer p, int gx, int gy, int gz) {
            p.forColumns(gx - 9, gz - 9, gx + 9, gz + 9, (x, z) -> {
                for (int y = gy - 9; y <= gy + 9; y++) {
                    double d = Math.sqrt((x - gx) * (x - gx) + (y - gy) * (y - gy) * 1.2 + (z - gz) * (z - gz));
                    if (d > 8.5) continue;
                    BlockState s = d > 7.5 ? b(Blocks.SMOOTH_BASALT) : d > 6.5 ? b(Blocks.CALCITE)
                            : d > 5.5 ? (h(x, y, z) % 7 == 0 ? b(Blocks.BUDDING_AMETHYST) : b(Blocks.AMETHYST_BLOCK)) : Blocks.AIR.defaultBlockState();
                    p.set(x, y, z, s);
                }
            });
            // 바닥과 벽의 자수정 결정
            for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) {
                if (h(gx + dx, 9, gz + dz) % 3 != 0) continue;
                for (int y = gy - 6; y <= gy; y++) {
                    double d = Math.sqrt(dx * dx + (y - gy) * (y - gy) * 1.2 + dz * dz);
                    if (d <= 5.5 && d > 4.5) {
                        p.set(gx + dx, y + 1, gz + dz, b(Blocks.AMETHYST_CLUSTER));
                        break;
                    }
                }
            }
            carve(p, CX + 169, CZ - 146, CX + 170, CZ - 144, gy - 3, gy - 1);
            box(p, CX + 169, gy - 4, CZ - 146, CX + 170, gy - 4, CZ - 144, b(Blocks.COBBLESTONE));
        }

        /** 잃어버린 광부의 갱도: 무너진 버팀목, 거미줄, 좀비·스켈레톤 스포너, 보물 상자 (어둡게 남겨 둠) */
        private void lostMine(Placer p, int M) {
            Mat wall = (x, y, z) -> h(x, y, z) % 3 == 0 ? b(Blocks.MOSSY_COBBLESTONE) : b(Blocks.COBBLESTONE);
            room(p, CX + 104, CZ - 218, CX + 116, CZ - 207, M, 5, wall, wall, wall);
            carve(p, CX + 109, CZ - 206, CX + 111, CZ - 206, M + 1, M + 4);
            OldCapital.spawner(p, CX + 107, M + 1, CZ - 213, EntityType.ZOMBIE);
            OldCapital.spawner(p, CX + 113, M + 1, CZ - 213, EntityType.SKELETON);
            OldCapital.chest(p, CX + 110, M + 1, CZ - 217, Direction.SOUTH, seed ^ 0xD3, BuiltInLootTables.ABANDONED_MINESHAFT);
            OldCapital.chest(p, CX + 104, M + 1, CZ - 218, Direction.EAST, seed ^ 0xD4, BuiltInLootTables.SIMPLE_DUNGEON);
            for (int x = CX + 104; x <= CX + 116; x++) for (int z = CZ - 218; z <= CZ - 207; z++) {
                int k = h(x, 77, z) % 13;
                if (k == 0) p.set(x, M + 5, z, b(Blocks.COBWEB));
                else if (k == 1) p.set(x, M + 1, z, b(Blocks.COBWEB));
                else if (k == 2) p.set(x, M + 1, z, b(Blocks.GRAVEL));
            }
            p.set(CX + 110, M + 4, CZ - 210, b(Blocks.SPRUCE_PLANKS));
            p.set(CX + 109, M + 3, CZ - 210, b(Blocks.SPRUCE_FENCE));
        }

        @Override
        public boolean needsSpawn() {
            return true;
        }

        @Override
        public void spawn(ServerLevel level, CityLayout city) {
            String[] names = {"드워프 왕 브롬하르", "대장장이 구른", "광부 도린", "보석세공사 니무", "맥주 양조사 볼그", "상인 탈린", "경비대장 오르크", "룬 각인사 에드"};
            int[][] at = {{CX, CZ - 195}, {CX - 44, CZ - 148}, {CX + 110, CZ - 145}, {CX + 60, CZ - 145}, {CX + 45, CZ - 145}, {CX - 18, CZ - 50}, {CX, CZ - 120}, {CX - 10, CZ - 160}};
            for (int i = 0; i < names.length; i++) {
                int y = i == 0 ? F + 1 : i == 2 ? F - 15 : F + 1;
                Mob m = EntityType.VILLAGER.spawn(level, new BlockPos(at[i][0], y, at[i][1]), MobSpawnType.STRUCTURE);
                if (m == null) continue;
                m.setPersistenceRequired();
                m.setCustomName(Component.literal(names[i]));
                AttributeInstance sc = m.getAttribute(Attributes.SCALE);
                if (sc != null) sc.setBaseValue(0.72);                       // 작은 키
            }
            for (int s = -1; s <= 1; s += 2) {
                Mob g = EntityType.IRON_GOLEM.spawn(level, new BlockPos(CX + s * 7, F + 1, CZ - 88), MobSpawnType.STRUCTURE);
                if (g != null) {
                    g.setPersistenceRequired();
                    g.setCustomName(Component.literal("드워프 강철 골렘"));
                }
            }
        }

        @Override
        public long anchorChunk() {
            return ChunkPos.asLong(CX >> 4, (CZ - 146) >> 4);
        }
    }
}
