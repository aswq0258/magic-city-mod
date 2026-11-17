package io.github.aswq0258.magiccity.site;

import io.github.aswq0258.magiccity.build.BuildLayout;
import io.github.aswq0258.magiccity.build.Placer;
import io.github.aswq0258.magiccity.city.CityLayout;
import io.github.aswq0258.magiccity.city.Palette;
import io.github.aswq0258.magiccity.city.Shapes;
import io.github.aswq0258.magiccity.city.feature.Feature;
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
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;

import java.util.ArrayList;
import java.util.List;

/**
 * 드워프 심층 광산 — 5장 "꺼지지 않는 용광로"의 던전. 지금 광산(지면 −16)의 동쪽 갱도에서 갈라져 내려감.
 *
 *  1. 봉쇄된 갱도 문 (광산 층, 동쪽 갱도 북쪽): 뜯겨 나간 쇠창살, 경고 해골 — "심층부가 열렸다"
 *  2. 내려가는 계단 갱도 20칸 → 심부 광산 (지면 −36)
 *       갱도 집결지(밝음) · 광부 피난처(침대·상자, 밝음, 중간 복귀 지점)
 *       노천 채굴장: 계단식으로 파 내려간 큰 구덩이(바닥 −8), 경사로, 광맥, 좀비 스포너
 *       북쪽 붕괴 갱도: 무너진 자갈 뒤에 갇힌 광부, 동굴거미 스포너
 *       서쪽 무너진 채굴조: 갇힌 광부, 스켈레톤 스포너
 *       채굴장 끝 언덕에 고립된 광부
 *       깨진 고대 봉인문 → 나선 계단 24칸
 *  3. 고대 용광로 홀 (지면 −60): 지름 약 54, 높이 약 28 의 돔.
 *       용암 해자로 둘러싼 가운데 섬, 다리 3개, 다리 끝마다 압력 밸브 장치,
 *       섬 한가운데 오염된 고대 용광로(우는 흑요석 띠), 북쪽에 불꽃의 봉인 제단,
 *       섬 남쪽이 "용광로의 거신"이 깨어나는 자리 (BOSS_X/Y/Z). 마그마 큐브·블레이즈 스포너
 *  4. 서쪽 보상 방 → 사다리 귀환 갱 → 북쪽 붕괴 갱도 → 집결지 (복귀 경로)
 * 깊은 곳은 어둡게 둠 (집결지·피난처·갱도 문만 밝음). 기존 광산·대장간은 건드리지 않음.
 */
public final class DeepMine implements BuildLayout {
    public static final int CX = DwarfHold.CX, CZ = DwarfHold.CZ;
    /** 왕국 바닥, 광산 층, 심부 광산 층, 고대 용광로 홀 바닥 */
    public static final int F = DwarfHold.floorAt(), M = F - 16, D = F - 36, A = F - 60;
    /** 고대 용광로 홀 중심 */
    public static final int HX = CX + 174, HZ = CZ - 272, HALL_R = 27;
    /** 보스(용광로의 거신)가 깨어나는 자리: 섬 남쪽 */
    public static final int BOSS_X = HX, BOSS_Y = A + 1, BOSS_Z = HZ + 10;
    /** 갱도 문 (goto 위치) */
    public static final int GATE_X = CX + 140, GATE_Z = CZ - 152;

    private static final int PX = CX + 174, PZ = CZ - 195, PRX = 22, PRZ = 19;   // 노천 채굴장
    private static final int SPX = CX + 174, SPZ = CZ - 234;                     // 봉인문 아래 나선

    private final long seed;
    private final Mine mine = new Mine();
    private final List<ChunkPos> chunks = new ArrayList<>();

    public DeepMine(long seed) {
        this.seed = seed;
        for (int x = (mine.minX - 1) >> 4; x <= (mine.maxX + 1) >> 4; x++)
            for (int z = (mine.minZ - 1) >> 4; z <= (mine.maxZ + 1) >> 4; z++) chunks.add(new ChunkPos(x, z));
    }

    @Override public int cx() { return CX + 140; }
    @Override public int cz() { return CZ - 220; }
    @Override public int base() { return F; }
    @Override public int radius() { return 0; }
    @Override public CityLayout city() { return null; }
    @Override public List<ChunkPos> chunkList() { return chunks; }
    @Override public List<Feature> featuresIn(ChunkPos cp) { return List.of(mine); }
    @Override public void buildColumn(Placer p, int x, int z) {}
    @Override public int shapeMinY() { return A - 6; }
    @Override public int shapeMaxY() { return M + 10; }

    /** 밝게 할 곳: 갱도 문, 집결지, 피난처 (나머지는 어두운 던전) */
    @Override
    public int[] lightRange(int x, int z) {
        if (x >= CX + 134 && x <= CX + 146 && z >= CZ - 160 && z <= CZ - 149) return new int[]{M, M + 6};
        if (x >= CX + 132 && x <= CX + 148 && z >= CZ - 196 && z <= CZ - 182) return new int[]{D, D + 7};
        if (x >= CX + 120 && x <= CX + 129 && z >= CZ - 194 && z <= CZ - 186) return new int[]{D, D + 5};
        return null;
    }

    private int h(int x, int y, int z) {
        return Shapes.hash(x, y, z, seed);
    }

    private static BlockState b(Block block) {
        return block.defaultBlockState();
    }

    private static DwarfHold.Mat of(BlockState s) {
        return (x, y, z) -> s;
    }

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    public final class Mine extends Feature {
        Mine() {
            super(CX + 76, CZ - 306, CX + 206, CZ - 140);
            groundY = D;
        }

        /** 광맥이 박힌 깊은 암반 (아래로 갈수록 금·다이아몬드가 많음) */
        private final DwarfHold.Mat rock = (x, y, z) -> {
            int k = h(x, y, z) % 400;
            boolean deep = y < D;
            if (k < 5) return b(Blocks.DEEPSLATE_IRON_ORE);
            if (k < 8) return b(Blocks.DEEPSLATE_COAL_ORE);
            if (k < (deep ? 12 : 10)) return b(Blocks.DEEPSLATE_GOLD_ORE);
            if (k < (deep ? 14 : 11)) return b(Blocks.DEEPSLATE_DIAMOND_ORE);
            if (k < 16) return b(Blocks.DEEPSLATE_REDSTONE_ORE);
            if (k < 17) return b(Blocks.DEEPSLATE_LAPIS_ORE);
            if (k < 90) return b(Blocks.TUFF);
            if (k < 110) return b(Blocks.COBBLED_DEEPSLATE);
            return b(Blocks.DEEPSLATE);
        };
        private final DwarfHold.Mat gravelFloor = (x, y, z) -> h(x, y, z) % 3 == 0 ? b(Blocks.GRAVEL) : b(Blocks.COBBLED_DEEPSLATE);
        private final DwarfHold.Mat brick = (x, y, z) -> h(x, y, z) % 7 == 0 ? b(Blocks.CRACKED_DEEPSLATE_BRICKS) : b(Blocks.DEEPSLATE_BRICKS);

        @Override
        public void build(Placer p, CityLayout city) {
            gate(p);
            stairs(p);
            plaza(p);
            refuge(p);
            westGallery(p);
            northGallery(p);
            pit(p);
            sealRoom(p);
            spiral(p);
            hall(p);
            reward(p);
            returnShaft(p);
            passages(p);
        }

        // ── 1. 봉쇄된 갱도 문 (광산 층) ──
        private void gate(Placer p) {
            DwarfHold.room(p, CX + 134, CZ - 160, CX + 146, CZ - 149, M, 6, brick, gravelFloor, brick);
            DwarfHold.carve(p, CX + 138, CZ - 148, CX + 142, CZ - 147, M + 1, M + 4);       // 동쪽 갱도에서 들어오는 문
            // 뜯겨 나간 쇠창살 (가운데가 벌어짐)
            for (int x = CX + 134; x <= CX + 146; x++) {
                if (Math.abs(x - (CX + 140)) <= 2) continue;
                for (int y = M + 1; y <= M + 5; y++) p.set(x, y, CZ - 156, b(Blocks.IRON_BARS));
            }
            for (int x = CX + 138; x <= CX + 142; x++) p.set(x, M + 6, CZ - 156, b(Blocks.IRON_BARS));
            p.set(CX + 137, M + 1, CZ - 155, b(Blocks.IRON_BARS));                          // 떨어진 창살
            p.set(CX + 143, M + 1, CZ - 154, b(Blocks.CHAIN));
            // 경고: 해골과 영혼 등불, 쓰러진 수레
            for (int s = -1; s <= 1; s += 2) {
                p.set(CX + 140 + s * 4, M + 1, CZ - 153, b(Blocks.POLISHED_BLACKSTONE_BRICK_WALL));
                p.set(CX + 140 + s * 4, M + 2, CZ - 153, b(Blocks.SOUL_LANTERN));
                p.set(CX + 140 + s * 5, M + 1, CZ - 157, b(Blocks.SKELETON_SKULL));
            }
            p.set(CX + 135, M + 1, CZ - 151, b(Blocks.BARREL));
            p.set(CX + 145, M + 1, CZ - 151, b(Blocks.BARREL));
            OldCapital.chest(p, CX + 145, M + 1, CZ - 150, Direction.WEST, seed ^ 0xA1, BuiltInLootTables.VILLAGE_TOOLSMITH);
        }

        // ── 2. 계단 갱도: 광산 층 → 심부 광산 (20칸) ──
        private void stairs(Placer p) {
            for (int i = 0; i < 20; i++) {
                int z = CZ - 161 - i, fy = M - 1 - i;
                for (int x = CX + 137; x <= CX + 143; x++) {
                    boolean border = x == CX + 137 || x == CX + 143;
                    for (int y = fy - 1; y <= fy + 7; y++) {
                        BlockState s;
                        if (border || y == fy + 7) s = brick.at(x, y, z);
                        else if (y == fy - 1) s = b(Blocks.COBBLED_DEEPSLATE);
                        else if (y == fy) s = i == 19 ? b(Blocks.POLISHED_DEEPSLATE) : Blocks.DEEPSLATE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH);
                        else s = AIR;
                        p.set(x, y, z, s);
                    }
                }
                if (i % 5 == 2) p.set(CX + 140, fy + 6, z, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
            }
            DwarfHold.carve(p, CX + 138, CZ - 161, CX + 142, CZ - 161, M, M + 5);
            DwarfHold.carve(p, CX + 138, CZ - 181, CX + 142, CZ - 181, D + 1, D + 6);
        }

        // ── 심부 광산: 갱도 집결지 ──
        private void plaza(Placer p) {
            DwarfHold.room(p, CX + 132, CZ - 196, CX + 148, CZ - 182, D, 7, rock, gravelFloor, rock);
            DwarfHold.carve(p, CX + 138, CZ - 181, CX + 142, CZ - 181, D + 1, D + 6);
            // 버팀목 기둥 넷, 수레 레일, 장비 통
            for (int[] q : new int[][]{{CX + 135, CZ - 193}, {CX + 145, CZ - 193}, {CX + 135, CZ - 185}, {CX + 145, CZ - 185}}) {
                for (int y = D + 1; y <= D + 7; y++) p.set(q[0], y, q[1], b(Blocks.STRIPPED_SPRUCE_LOG));
                p.set(q[0], D + 6, q[1] + 1, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false));
            }
            for (int x = CX + 133; x <= CX + 147; x++) p.set(x, D + 1, CZ - 189, Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE, RailShape.EAST_WEST));
            box(p, CX + 133, D + 1, CZ - 195, CX + 134, D + 1, CZ - 195, b(Blocks.BARREL));
            p.set(CX + 147, D + 1, CZ - 195, b(Blocks.CRAFTING_TABLE));
            p.set(CX + 140, D + 1, CZ - 195, b(Blocks.BELL));                               // 광부의 종
        }

        // ── 광부 피난처 (밝음, 침대·상자) ──
        private void refuge(Placer p) {
            DwarfHold.room(p, CX + 120, CZ - 194, CX + 129, CZ - 186, D, 5, brick, of(b(Blocks.SPRUCE_PLANKS)), of(b(Blocks.SPRUCE_PLANKS)));
            DwarfHold.carve(p, CX + 130, CZ - 191, CX + 131, CZ - 189, D + 1, D + 3);
            for (int i = 0; i < 3; i++) {
                int x = CX + 121 + i * 3;
                p.set(x, D + 1, CZ - 194, Palette.bed(Direction.NORTH, true));
                p.set(x, D + 1, CZ - 193, Palette.bed(Direction.NORTH, false));
            }
            p.set(CX + 125, D + 1, CZ - 189, b(Blocks.CAMPFIRE));
            p.set(CX + 120, D + 1, CZ - 186, b(Blocks.CRAFTING_TABLE));
            p.set(CX + 121, D + 1, CZ - 186, b(Blocks.FURNACE));
            OldCapital.chest(p, CX + 129, D + 1, CZ - 186, Direction.WEST, seed ^ 0xA2, BuiltInLootTables.VILLAGE_PLAINS_HOUSE);
            p.set(CX + 124, D + 5, CZ - 190, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        }

        // ── 서쪽 무너진 채굴조 ──
        private void westGallery(Placer p) {
            tunnel(p, CX + 92, CZ - 191, CX + 118, CZ - 189, false);
            DwarfHold.carve(p, CX + 119, CZ - 191, CX + 119, CZ - 189, D + 1, D + 3);
            DwarfHold.room(p, CX + 80, CZ - 197, CX + 90, CZ - 183, D, 5, rock, gravelFloor, rock);
            DwarfHold.carve(p, CX + 91, CZ - 191, CX + 91, CZ - 189, D + 1, D + 3);
            // 무너진 자갈 더미와 쓰러진 버팀목
            for (int x = CX + 80; x <= CX + 90; x++) for (int z = CZ - 197; z <= CZ - 183; z++) {
                int k = h(x, 5, z) % 11;
                if (k == 0) for (int y = D + 1; y <= D + 1 + h(x, 6, z) % 3; y++) p.set(x, y, z, b(Blocks.GRAVEL));
                else if (k == 1) p.set(x, D + 5, z, b(Blocks.COBWEB));
            }
            p.set(CX + 85, D + 1, CZ - 190, b(Blocks.STRIPPED_SPRUCE_LOG));
            OldCapital.spawner(p, CX + 84, D + 1, CZ - 194, EntityType.SKELETON);
            OldCapital.chest(p, CX + 81, D + 1, CZ - 196, Direction.SOUTH, seed ^ 0xA3, BuiltInLootTables.ABANDONED_MINESHAFT);
            pocket(p, CX + 81, CZ - 185);                                                  // 갇힌 광부 자리
        }

        // ── 북쪽 붕괴 갱도 ──
        private void northGallery(Placer p) {
            tunnel(p, CX + 139, CZ - 232, CX + 141, CZ - 197, true);
            DwarfHold.carve(p, CX + 139, CZ - 197, CX + 141, CZ - 197, D + 1, D + 3);
            DwarfHold.room(p, CX + 132, CZ - 244, CX + 148, CZ - 234, D, 6, rock, gravelFloor, rock);
            DwarfHold.carve(p, CX + 139, CZ - 233, CX + 141, CZ - 233, D + 1, D + 3);
            for (int x = CX + 132; x <= CX + 148; x++) for (int z = CZ - 244; z <= CZ - 234; z++) {
                int k = h(x, 7, z) % 9;
                if (k == 0) p.set(x, D + 1 + h(x, 8, z) % 4, z, b(Blocks.COBWEB));
                else if (k == 1) p.set(x, D + 1, z, b(Blocks.GRAVEL));
            }
            OldCapital.spawner(p, CX + 136, D + 1, CZ - 238, EntityType.CAVE_SPIDER);
            OldCapital.spawner(p, CX + 145, D + 1, CZ - 240, EntityType.CAVE_SPIDER);
            pocket(p, CX + 146, CZ - 243);
        }

        /** 무너진 자갈 벽 뒤의 작은 공간 (갇힌 광부가 있는 곳): 2 x 2, 높이 3, 앞을 자갈이 막음 */
        private void pocket(Placer p, int x, int z) {
            box(p, x - 1, D + 1, z - 1, x + 1, D + 3, z + 1, b(Blocks.GRAVEL));
            box(p, x, D + 1, z, x, D + 2, z, AIR);
            p.set(x, D, z, b(Blocks.COBBLED_DEEPSLATE));
            p.set(x, D + 3, z, b(Blocks.STRIPPED_SPRUCE_LOG));
        }

        /** 레일 깔린 갱도 (폭 3, 높이 4), 6칸마다 버팀목, 어두움 */
        private void tunnel(Placer p, int x0, int z0, int x1, int z1, boolean northSouth) {
            DwarfHold.room(p, x0, z0, x1, z1, D, 4, rock, gravelFloor, rock);
            int mid = northSouth ? (x0 + x1) / 2 : (z0 + z1) / 2;
            int a0 = northSouth ? z0 : x0, a1 = northSouth ? z1 : x1;
            for (int a = a0; a <= a1; a++) {
                int x = northSouth ? mid : a, z = northSouth ? a : mid;
                if (h(x, 3, z) % 7 != 0) p.set(x, D + 1, z, Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE, northSouth ? RailShape.NORTH_SOUTH : RailShape.EAST_WEST));
                if ((a - a0) % 6 == 3) {
                    for (int s = -1; s <= 1; s += 2) {
                        int px = northSouth ? mid + s : a, pz = northSouth ? a : mid + s;
                        for (int y = D + 1; y <= D + 3; y++) p.set(px, y, pz, b(Blocks.SPRUCE_FENCE));
                    }
                    for (int s = -1; s <= 1; s++) {
                        int px = northSouth ? mid + s : a, pz = northSouth ? a : mid + s;
                        p.set(px, D + 4, pz, h(px, 4, pz) % 4 == 0 ? b(Blocks.STRIPPED_SPRUCE_LOG) : b(Blocks.SPRUCE_PLANKS));
                    }
                }
            }
        }

        // ── 노천 채굴장 (계단식 구덩이) ──
        private void pit(Placer p) {
            p.forColumns(PX - PRX - 4, PZ - PRZ - 4, PX + PRX + 4, PZ + PRZ + 4, (x, z) -> {
                double ex = (x - PX) / (double) PRX, ez = (z - PZ) / (double) PRZ;
                double e = Math.sqrt(ex * ex + ez * ez) + 0.06 * Math.sin(x / 3.0) * Math.cos(z / 4.0);
                if (e > 1.14) return;
                int ceil = D + 10 + (int) Math.round(6 * Math.sqrt(Math.max(0, 1 - e * e)));
                if (e > 1.0) {                                                                // 벽
                    for (int y = D - 9; y <= ceil + 1; y++) p.set(x, y, z, rock.at(x, y, z));
                    return;
                }
                // 바닥: 안쪽부터 −8, −4, 0 의 계단식. 동쪽 경사로는 한 칸씩
                int floor = e < 0.45 ? D - 8 : e < 0.75 ? D - 4 : D;
                boolean ramp = Math.abs(z - PZ) <= 1 && x > PX;
                if (ramp) floor = D - 8 + (int) Math.max(0, Math.min(8, Math.floor((e - 0.3) / 0.6 * 9)));
                for (int y = D - 9; y < floor; y++) p.set(x, y, z, rock.at(x, y, z));
                p.set(x, floor, z, ramp ? b(Blocks.COBBLED_DEEPSLATE) : gravelFloor.at(x, floor, z));
                for (int y = floor + 1; y <= ceil; y++) p.set(x, y, z, AIR);
                p.set(x, ceil + 1, z, rock.at(x, ceil + 1, z));
                // 계단 턱 가장자리 울타리
                int k = h(x, 9, z);
                if (!ramp && (Math.abs(e - 0.75) < 0.03 || Math.abs(e - 0.45) < 0.03) && k % 3 == 0) p.set(x, floor + 1, z, b(Blocks.SPRUCE_FENCE));
                else if (floor == D - 8 && k % 37 == 0) p.set(x, floor + 1, z, b(Blocks.RAW_IRON_BLOCK));
                else if (floor == D - 8 && k % 41 == 0) p.set(x, floor + 1, z, b(Blocks.RAW_GOLD_BLOCK));
                if (k % 151 == 0 && ceil - floor > 8) {                                       // 천장의 사슬 등불 (드문드문)
                    for (int y = ceil - 2; y <= ceil; y++) p.set(x, y, z, b(Blocks.CHAIN));
                    p.set(x, ceil - 3, z, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
                }
            });
            // 바닥의 레일과 기중기 (울타리 + 사슬)
            for (int x = PX - 6; x <= PX + 6; x++) p.set(x, D - 7, PZ, Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE, RailShape.EAST_WEST));
            for (int y = D - 7; y <= D + 8; y++) p.set(PX - 3, y, PZ - 6, b(Blocks.STRIPPED_SPRUCE_LOG));
            for (int x = PX - 3; x <= PX + 2; x++) p.set(x, D + 9, PZ - 6, b(Blocks.SPRUCE_PLANKS));
            for (int y = D - 2; y <= D + 8; y++) p.set(PX + 2, y, PZ - 6, b(Blocks.CHAIN));
            DwarfHold.carve(p, CX + 149, CZ - 191, CX + 153, CZ - 189, D + 1, D + 4);       // 집결지에서 들어가는 굴
            OldCapital.spawner(p, PX - 4, D - 7, PZ + 4, EntityType.ZOMBIE);
            OldCapital.spawner(p, PX + 5, D - 7, PZ - 5, EntityType.ZOMBIE);
            // 채굴장 북동쪽 끝 언덕에 고립된 광부 (무너진 사다리)
            box(p, PX + 14, D + 1, PZ - 13, PX + 16, D + 3, PZ - 11, AIR);
            p.set(PX + 13, D + 1, PZ - 12, b(Blocks.SPRUCE_FENCE));
        }

        // ── 깨진 고대 봉인문 ──
        private void sealRoom(Placer p) {
            DwarfHold.room(p, CX + 168, CZ - 228, CX + 180, CZ - 217, D, 8, brick, of(b(Blocks.POLISHED_DEEPSLATE)), brick);
            DwarfHold.carve(p, CX + 172, CZ - 216, CX + 176, CZ - 214, D + 1, D + 5);
            // 북쪽 벽의 문틀: 조각 심층암 + 금 상감 흑암, 가운데가 깨져 열림
            for (int x = CX + 169; x <= CX + 179; x++) for (int y = D + 1; y <= D + 8; y++) {
                int ax = Math.abs(x - SPX);
                boolean open = ax <= 2 && y <= D + 6;
                BlockState s = open ? AIR : (ax == 3 || y == D + 7) ? b(Blocks.GILDED_BLACKSTONE)
                        : h(x, y, 3) % 3 == 0 ? b(Blocks.CRACKED_DEEPSLATE_BRICKS) : b(Blocks.CHISELED_DEEPSLATE);
                p.set(x, y, CZ - 228, s);
            }
            // 깨진 문짝 조각 (바닥에 흩어진 금 상감 돌)
            for (int[] q : new int[][]{{CX + 171, CZ - 225}, {CX + 177, CZ - 224}, {CX + 174, CZ - 222}}) p.set(q[0], D + 1, q[1], b(Blocks.GILDED_BLACKSTONE));
            for (int s = -1; s <= 1; s += 2) {
                p.set(SPX + s * 5, D + 1, CZ - 220, b(Blocks.POLISHED_BLACKSTONE_BRICK_WALL));
                p.set(SPX + s * 5, D + 2, CZ - 220, b(Blocks.SOUL_LANTERN));
            }
            // 봉인 문양 (바닥, 깨진 원)
            for (int x = SPX - 3; x <= SPX + 3; x++) for (int z = CZ - 226; z <= CZ - 220; z++) {
                double d = Math.hypot(x - SPX, z - (CZ - 223));
                if (d > 2.5 && d < 3.5 && h(x, 1, z) % 4 != 0) p.set(x, D, z, b(Blocks.CRYING_OBSIDIAN));
            }
        }

        // ── 나선 계단: 봉인문 → 고대 용광로 홀 (24칸) ──
        private void spiral(Placer p) {
            DwarfHold.carve(p, SPX - 2, CZ - 229, SPX + 2, CZ - 229, D + 1, D + 6);
            p.forColumns(SPX - 7, SPZ - 7, SPX + 7, SPZ + 7, (x, z) -> {
                double d = Math.hypot(x - SPX, z - SPZ);
                if (d > 6.6) return;
                if (d > 5.5) {
                    for (int y = A; y <= D + 7; y++) p.set(x, y, z, brick.at(x, y, z));
                    return;
                }
                for (int y = A + 1; y <= D + 6; y++) p.set(x, y, z, AIR);
                p.set(x, A, z, b(Blocks.POLISHED_DEEPSLATE));
                p.set(x, D + 7, z, brick.at(x, D + 7, z));
                if (d <= 1.5) {                                                                // 가운데 기둥
                    for (int y = A + 1; y <= D + 6; y++) p.set(x, y, z, b(Blocks.POLISHED_BASALT));
                    return;
                }
                double ang = Math.atan2(z - SPZ, x - SPX) - Math.PI / 2;                     // 문(남쪽)에서 시작
                while (ang < 0) ang += Math.PI * 2;
                for (int n = 0; ; n++) {                                                      // 반 칸씩, 12칸에 한 바퀴
                    double hy = D - (ang / (Math.PI * 2) + n) * 12;
                    if (hy < A) break;
                    int y = (int) Math.floor(hy);
                    boolean half = hy - y >= 0.5;
                    p.set(x, half ? y + 1 : y, z, half ? Blocks.DEEPSLATE_BRICK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM) : b(Blocks.DEEPSLATE_BRICKS));
                }
                if (d > 4.5 && h(x, 2, z) % 9 == 0) for (int y = A + 3; y < D; y += 8) p.set(x, y, z, b(Blocks.SOUL_LANTERN));
            });
        }

        // ── 3. 고대 용광로 홀 ──
        private void hall(Placer p) {
            final int moatIn = 17, moatOut = 20;
            final double[] bridges = {Math.PI / 2, Math.PI / 2 + Math.PI * 2 / 3, Math.PI / 2 + Math.PI * 4 / 3};
            p.forColumns(HX - HALL_R - 3, HZ - HALL_R - 3, HX + HALL_R + 3, HZ + HALL_R + 3, (x, z) -> {
                double d = Math.hypot(x - HX, z - HZ), ang = Math.atan2(z - HZ, x - HX);
                if (d > HALL_R + 2.5) return;
                int ceil = A + 4 + (int) Math.round(24 * Math.sqrt(Math.max(0, 1 - Math.pow(d / (HALL_R + 2), 2))));
                if (d > HALL_R) {
                    for (int y = A - 2; y <= ceil + 2; y++) p.set(x, y, z, rock.at(x, y, z));
                    return;
                }
                for (int y = A + 1; y <= ceil; y++) p.set(x, y, z, AIR);
                p.set(x, ceil + 1, z, rock.at(x, ceil + 1, z));
                boolean bridge = false;
                for (double a : bridges) {
                    double da = Math.abs(Math.atan2(Math.sin(ang - a), Math.cos(ang - a)));
                    if (da < Math.PI / 2 && Math.abs(Math.sin(ang - a)) * d <= 1.6) bridge = true;
                }
                int k = h(x, 11, z);
                if (d >= moatIn && d < moatOut && !bridge) {                                  // 용암 해자
                    p.set(x, A - 2, z, b(Blocks.MAGMA_BLOCK));
                    p.set(x, A - 1, z, b(Blocks.LAVA));
                    p.set(x, A, z, b(Blocks.LAVA));
                    return;
                }
                BlockState floor;
                if (d < moatIn) floor = d < 7 ? b(Blocks.POLISHED_BLACKSTONE) : k % 5 == 0 ? b(Blocks.BLACKSTONE) : b(Blocks.POLISHED_BASALT);
                else if (bridge && d < moatOut) floor = b(Blocks.POLISHED_BLACKSTONE_BRICKS);
                else floor = k % 6 == 0 ? b(Blocks.CRACKED_DEEPSLATE_TILES) : b(Blocks.DEEPSLATE_TILES);
                p.set(x, A - 1, z, b(Blocks.DEEPSLATE));
                p.set(x, A, z, floor);
                if (bridge && d >= moatIn - 0.5 && d < moatOut + 0.5 && Math.abs(Math.sin(ang - nearest(bridges, ang))) * d > 1.0) {
                    p.set(x, A + 1, z, b(Blocks.POLISHED_BLACKSTONE_BRICK_WALL));               // 다리 난간
                }
                if (d > HALL_R - 1.5 && k % 9 == 0) p.set(x, A + 1 + k % 3, z, b(Blocks.MAGMA_BLOCK));   // 벽의 달아오른 바위
            });
            furnace(p);
            sealAltar(p);
            for (double a : bridges) valve(p, a);
            OldCapital.spawner(p, HX - 23, A + 1, HZ - 6, EntityType.MAGMA_CUBE);
            OldCapital.spawner(p, HX + 22, A + 1, HZ - 9, EntityType.MAGMA_CUBE);
            OldCapital.spawner(p, HX + 4, A + 1, HZ - 23, EntityType.BLAZE);
        }

        private double nearest(double[] as, double ang) {
            double best = as[0], bd = 9;
            for (double a : as) {
                double da = Math.abs(Math.atan2(Math.sin(ang - a), Math.cos(ang - a)));
                if (da < bd) {
                    bd = da;
                    best = a;
                }
            }
            return best;
        }

        /** 섬 가운데 오염된 고대 용광로: 지름 11, 높이 16, 우는 흑요석 띠, 아래 용암 아궁이 */
        private void furnace(Placer p) {
            p.forColumns(HX - 6, HZ - 6, HX + 6, HZ + 6, (x, z) -> {
                double d = Math.hypot(x - HX, z - HZ), ang = Math.atan2(z - HZ, x - HX);
                if (d > 5.6) return;
                boolean mouth = Math.abs(Math.sin(ang - Math.PI / 2)) * d < 1.6 && z > HZ;        // 남쪽 아궁이
                for (int y = A + 1; y <= A + 16; y++) {
                    BlockState s;
                    if (d > 4.4) {
                        if (mouth && y <= A + 4) s = y == A + 1 ? b(Blocks.MAGMA_BLOCK) : AIR;
                        else if ((y - A) % 5 == 0) s = b(Blocks.CRYING_OBSIDIAN);
                        else if (y >= A + 15) s = b(Blocks.CHISELED_POLISHED_BLACKSTONE);
                        else s = h(x, y, z) % 5 == 0 ? b(Blocks.GILDED_BLACKSTONE) : b(Blocks.POLISHED_BLACKSTONE_BRICKS);
                    } else if (y <= A + 2) s = b(Blocks.MAGMA_BLOCK);
                    else if (y == A + 3 && d < 2.5) s = b(Blocks.LAVA);
                    else s = y >= A + 16 ? b(Blocks.POLISHED_BLACKSTONE_BRICKS) : AIR;
                    p.set(x, y, z, s);
                }
            });
            // 굴뚝: 천장까지 사슬과 흑암 기둥
            for (int y = A + 17; y <= A + 26; y++) p.set(HX, y, HZ, (y & 1) == 0 ? b(Blocks.POLISHED_BLACKSTONE) : b(Blocks.CRYING_OBSIDIAN));
            for (int s = -1; s <= 1; s += 2) {
                p.set(HX + s * 6, A + 1, HZ + 6, b(Blocks.ANVIL));
                p.set(HX + s * 7, A + 1, HZ, b(Blocks.BLAST_FURNACE));
            }
        }

        /** 불꽃의 봉인 제단 (섬 북쪽): 금 상감 단, 영혼 모닥불, 자수정 */
        private void sealAltar(Placer p) {
            int ax = HX, az = HZ - 11;
            box(p, ax - 2, A + 1, az - 2, ax + 2, A + 1, az + 2, b(Blocks.GILDED_BLACKSTONE));
            box(p, ax - 1, A + 2, az - 1, ax + 1, A + 2, az + 1, b(Blocks.CHISELED_POLISHED_BLACKSTONE));
            p.set(ax, A + 3, az, b(Blocks.SOUL_CAMPFIRE));
            for (int s = -1; s <= 1; s += 2) {
                p.set(ax + s * 2, A + 2, az + 2, b(Blocks.AMETHYST_CLUSTER));
                p.set(ax + s * 2, A + 2, az - 2, b(Blocks.AMETHYST_CLUSTER));
            }
        }

        /** 다리 바깥 끝의 압력 밸브 장치: 구리 관, 레버, 레드스톤 램프 */
        private void valve(Placer p, double ang) {
            int vx = HX + (int) Math.round(Math.cos(ang) * 24.5), vz = HZ + (int) Math.round(Math.sin(ang) * 24.5);
            Direction face = Math.abs(Math.cos(ang)) > Math.abs(Math.sin(ang)) ? (Math.cos(ang) > 0 ? Direction.WEST : Direction.EAST)
                    : (Math.sin(ang) > 0 ? Direction.NORTH : Direction.SOUTH);
            int sx = face.getAxis() == Direction.Axis.X ? 0 : 2, sz = face.getAxis() == Direction.Axis.Z ? 0 : 2;
            // 옆으로 비켜 둠 (다리 길을 막지 않게)
            vx += sx;
            vz += sz;
            p.set(vx, A + 1, vz, b(Blocks.OXIDIZED_COPPER));
            p.set(vx, A + 2, vz, b(Blocks.WAXED_OXIDIZED_CUT_COPPER));
            p.set(vx, A + 3, vz, Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.FLOOR).setValue(LeverBlock.FACING, face));
            p.set(vx - sx, A + 1, vz - sz, b(Blocks.REDSTONE_LAMP));
            for (int y = A + 1; y <= A + 8; y++) p.set(vx + sx, y, vz + sz, b(Blocks.WAXED_OXIDIZED_COPPER_GRATE));
        }

        // ── 4. 보상 방 (홀 서쪽) ──
        private void reward(Placer p) {
            DwarfHold.room(p, CX + 134, CZ - 278, CX + 144, CZ - 266, A, 6, brick, of(b(Blocks.POLISHED_BLACKSTONE)), brick);
            OldCapital.chest(p, CX + 139, A + 1, CZ - 278, Direction.SOUTH, seed ^ 0xB1, BuiltInLootTables.ANCIENT_CITY);
            OldCapital.chest(p, CX + 135, A + 1, CZ - 272, Direction.EAST, seed ^ 0xB2, BuiltInLootTables.BASTION_TREASURE);
            p.set(CX + 143, A + 1, CZ - 277, b(Blocks.GOLD_BLOCK));
            p.set(CX + 143, A + 1, CZ - 276, b(Blocks.RAW_GOLD_BLOCK));
            p.set(CX + 139, A + 6, CZ - 272, Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        }

        /** 사다리 귀환 갱: 보상 방 북쪽 → 심부 광산 층 → 북쪽 붕괴 갱도 */
        private void returnShaft(Placer p) {
            int x0 = CX + 138, x1 = CX + 140, z0 = CZ - 264, z1 = CZ - 262;
            p.forColumns(x0 - 1, z0 - 1, x1 + 1, z1 + 1, (x, z) -> {
                boolean wall = x < x0 || x > x1 || z < z0 || z > z1;
                for (int y = A + 1; y <= D + 4; y++) p.set(x, y, z, wall ? brick.at(x, y, z) : AIR);
                if (!wall) p.set(x, A, z, b(Blocks.POLISHED_BLACKSTONE));
            });
            tunnel(p, x0, CZ - 261, x1, CZ - 246, true);
            DwarfHold.carve(p, x0, z1, x1, z1, D + 1, D + 3);                                // 갱 → 갱도
            DwarfHold.carve(p, x0, CZ - 245, x1, CZ - 245, D + 1, D + 3);                    // 붕괴 갱도 방 남쪽 벽
            DwarfHold.carve(p, x0 + 1, CZ - 265, x1, CZ - 265, A + 1, A + 3);                // 보상 방 → 갱
            for (int y = A + 1; y <= D + 1; y++) p.set(x0, y, z0 + 1, Palette.ladder(Direction.EAST));
        }

        /** 다른 공간이 벽으로 덮은 이음목을 마지막에 다시 뚫음: 봉인문 → 나선, 나선 → 홀, 홀 → 보상 방 */
        private void passages(Placer p) {
            DwarfHold.carve(p, SPX - 2, CZ - 229, SPX + 2, CZ - 228, D + 1, D + 6);
            DwarfHold.carve(p, SPX - 2, HZ + HALL_R - 2, SPX + 2, SPZ - 5, A + 1, A + 5);
            box(p, SPX - 2, A, HZ + HALL_R - 2, SPX + 2, A, SPZ - 5, b(Blocks.POLISHED_DEEPSLATE));
            DwarfHold.carve(p, CX + 145, CZ - 273, HX - HALL_R + 2, CZ - 271, A + 1, A + 4);
            box(p, CX + 145, A, CZ - 273, HX - HALL_R + 2, A, CZ - 271, b(Blocks.POLISHED_DEEPSLATE));
        }

        @Override
        public boolean needsSpawn() {
            return true;
        }

        /** 갇힌 광부 셋 (작은 키), 갱도 문지기 */
        @Override
        public void spawn(ServerLevel level, CityLayout city) {
            String[] names = {"실종 광부 브루크", "실종 광부 할다", "실종 광부 퀸", "갱도 문지기 도른"};
            int[][] at = {{CX + 81, D + 1, CZ - 185}, {CX + 146, D + 1, CZ - 243}, {PX + 15, D + 1, PZ - 12}, {CX + 140, M + 1, CZ - 151}};
            for (int i = 0; i < names.length; i++) {
                Mob m = EntityType.VILLAGER.spawn(level, new BlockPos(at[i][0], at[i][1], at[i][2]), MobSpawnType.STRUCTURE);
                if (m == null) continue;
                m.setPersistenceRequired();
                m.setCustomName(Component.literal(names[i]));
                AttributeInstance sc = m.getAttribute(Attributes.SCALE);
                if (sc != null) sc.setBaseValue(0.72);
            }
        }

        @Override
        public long anchorChunk() {
            return ChunkPos.asLong((CX + 140) >> 4, (CZ - 189) >> 4);
        }
    }

    static void box(Placer p, int x0, int y0, int z0, int x1, int y1, int z1, BlockState s) {
        DwarfHold.box(p, x0, y0, z0, x1, y1, z1, s);
    }
}
