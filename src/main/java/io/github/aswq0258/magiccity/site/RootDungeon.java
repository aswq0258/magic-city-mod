package io.github.aswq0258.magiccity.site;

import io.github.aswq0258.magiccity.build.BuildLayout;
import io.github.aswq0258.magiccity.build.Placer;
import io.github.aswq0258.magiccity.city.CityLayout;
import io.github.aswq0258.magiccity.city.Shapes;
import io.github.aswq0258.magiccity.city.feature.Feature;
import io.github.aswq0258.magiccity.world.KingdomTerrain;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.GlowLichenBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 세계수 뿌리 던전 — 세계수 기생충의 둥지 (3장 "썩어가는 뿌리").
 *
 *  1. 줄기 입구: 세계수 밑동 남쪽 면의 갈라진 틈 → 줄기 속 빈 방 → 나선 계단으로 내려감
 *  2. 뿌리 회랑 (지면 -26): 뿌리로 엮인 굴과 방 다섯 개
 *       수액 샘 / 감염된 둥지(좀벌레·동굴거미, 감염된 돌) / 고치 방(거미줄에 감긴 희생자) /
 *       엘프 탐사대의 마지막 야영지(일지·보급 상자) / 하강 동굴(더 아래로 내려가는 나선)
 *  3. 기생충의 둥지 (지면 -64): 지름 약 70, 높이 약 28 의 살아 있는 듯한 돔.
 *       천장에서 내려온 세계수 원뿌리를 기생충이 갉아먹고 있음. 네더 사마귀 덩어리·진홍 균사 벽,
 *       알주머니(개구리불), 늘어진 덩굴, 가운데 수액 웅덩이 = 보스가 나타나는 자리 (BOSS_X/Y/Z)
 *  4. 보스 방 북쪽의 보상 방과 회랑으로 돌아가는 나선
 * 몬스터는 스포너로만 나옴 (세계수 성역은 자연 스폰이 막혀 있음).
 */
public final class RootDungeon implements BuildLayout {
    public static final int OX = WorldTree.OX, OZ = WorldTree.OZ;
    /** 지면 높이, 회랑 바닥, 보스 방 바닥 */
    public static final int B = KingdomTerrain.column(OX, OZ).h(), C = B - 26, F = B - 64;
    public static final int ARENA_R = 34, ARENA_H = 28;
    /** 보스가 나타날 자리 (가운데 수액 웅덩이 위) */
    public static final int BOSS_X = OX, BOSS_Y = F + 1, BOSS_Z = OZ;
    /** 방 위치 (중심 기준) */
    static final int[] SAP = {-48, -12}, NEST = {42, -34}, COCOON = {-18, 50}, CAMP = {-56, 34}, DESCENT = {54, 26}, REWARD = {0, -46};

    private final long seed;
    private final List<ChunkPos> chunks = new ArrayList<>();
    /** 굴 {x0, z0, y0, x1, z1, y1, 반폭, 높이} (중심 기준, y 는 바닥) */
    private final List<int[]> tunnels = new ArrayList<>();
    /** 방 {x, z, 바닥, 반지름, 높이} */
    private final List<int[]> rooms = new ArrayList<>();
    /** 나선 {x, z, 위 바닥, 아래 바닥} */
    private final List<int[]> spirals = new ArrayList<>();

    public RootDungeon(long seed) {
        this.seed = seed;
        // 줄기 입구: 남쪽 광장 → 줄기 속 빈 방
        tunnels.add(new int[]{0, 34, B, 0, 4, B, 2, 6});
        rooms.add(new int[]{0, 0, B, 7, 11});
        spirals.add(new int[]{0, 0, B, C});
        // 회랑
        rooms.add(new int[]{0, 0, C, 9, 8});                                  // 나선 아래 갈림방
        room(SAP, 9, 8);
        room(NEST, 10, 9);
        room(COCOON, 9, 8);
        room(CAMP, 8, 7);
        room(DESCENT, 8, 9);
        bend(0, 0, SAP, 10, -14);
        bend(0, 0, NEST, -6, 12);
        bend(0, 0, COCOON, 12, 6);
        bend(COCOON[0], COCOON[1], CAMP, -4, -10);
        bend(SAP[0], SAP[1], CAMP, 12, 4);
        bend(NEST[0], NEST[1], DESCENT, 14, 4);
        // 아래로: 하강 동굴의 나선 → 보스 방 동쪽 입구
        spirals.add(new int[]{DESCENT[0], DESCENT[1], C, F});
        tunnels.add(new int[]{DESCENT[0], DESCENT[1], F, ARENA_R - 4, 6, F, 3, 7});
        // 보상 방과 돌아가는 나선 (회랑 갈림방 북쪽으로 이어짐)
        rooms.add(new int[]{REWARD[0], REWARD[1], F, 8, 7});
        tunnels.add(new int[]{0, -(ARENA_R - 4), F, REWARD[0], REWARD[1], F, 2, 5});
        spirals.add(new int[]{REWARD[0], REWARD[1] - 6, C, F});
        tunnels.add(new int[]{REWARD[0], REWARD[1] - 6, C, 0, -8, C, 2, 5});

        LinkedHashSet<Long> keys = new LinkedHashSet<>();
        for (int x = (OX - 80) >> 4; x <= (OX + 80) >> 4; x++)
            for (int z = (OZ - 80) >> 4; z <= (OZ + 80) >> 4; z++) keys.add(ChunkPos.asLong(x, z));
        for (long k : keys) chunks.add(new ChunkPos(k));
    }

    private void room(int[] at, int r, int hgt) {
        rooms.add(new int[]{at[0], at[1], C, r, hgt});
    }

    /** 두 점을 살짝 굽은 굴(꺾인 두 토막)로 이음 */
    private void bend(int x0, int z0, int[] to, int ox, int oz) {
        int mx = (x0 + to[0]) / 2 + ox, mz = (z0 + to[1]) / 2 + oz;
        tunnels.add(new int[]{x0, z0, C, mx, mz, C, 2, 6});
        tunnels.add(new int[]{mx, mz, C, to[0], to[1], C, 2, 6});
    }

    public static boolean near(double x, double z) {
        return Math.abs(x - OX) < 80 && Math.abs(z - OZ) < 80;
    }

    @Override public int cx() { return OX; }
    @Override public int cz() { return OZ; }
    @Override public int base() { return B; }
    @Override public int radius() { return 0; }
    @Override public CityLayout city() { return null; }
    @Override public List<ChunkPos> chunkList() { return chunks; }
    @Override public List<Feature> featuresIn(ChunkPos cp) { return Collections.emptyList(); }
    @Override public int[] lightRange(int x, int z) { return null; }
    @Override public int shapeMinY() { return F - 4; }
    @Override public int shapeMaxY() { return B + 14; }

    private int h(int x, int y, int z) {
        return Shapes.hash(x, y, z, seed);
    }

    private static double noise(int x, int z, int salt) {
        double a = Math.sin(x * 0.37 + salt * 1.7) * Math.cos(z * 0.29 - salt * 0.9) + Math.sin((x + z) * 0.11 + salt);
        return 0.5 + 0.25 * a;
    }

    // ───────────────────────── 모양 ─────────────────────────

    /** 이 기둥에서 비어 있는 높이 구간들 (grow > 0 이면 벽 두께만큼 넓힘) — {아래, 위} 목록 */
    private List<int[]> spans(int dx, int dz, int x, int z, double grow) {
        List<int[]> out = new ArrayList<>();
        int g = grow > 0 ? 1 : 0;
        double n = noise(x, z, 3);
        for (int[] t : tunnels) {
            double vx = t[3] - t[0], vz = t[4] - t[1], len2 = vx * vx + vz * vz;
            double u = len2 == 0 ? 0 : Math.max(0, Math.min(1, ((dx - t[0]) * vx + (dz - t[1]) * vz) / len2));
            double d = Math.hypot(dx - t[0] - vx * u, dz - t[1] - vz * u);
            double w = t[6] + n * 1.2 + grow;
            if (d > w) continue;
            int floor = (int) Math.round(t[2] + (t[5] - t[2]) * u);
            int top = floor + (int) Math.round(t[7] * Math.sqrt(Math.max(0, 1 - Math.pow(d / (w + 1), 2)))) + 1;
            out.add(new int[]{floor + 1 - g, top + g});
        }
        for (int[] r : rooms) {
            double d = Math.hypot(dx - r[0], dz - r[1]);
            double rr = r[3] + n * 2 + grow;
            if (d > rr) continue;
            int top = r[2] + (int) Math.round(r[4] * Math.sqrt(Math.max(0, 1 - Math.pow(d / (rr + 1.5), 2)))) + 2;
            out.add(new int[]{r[2] + 1 - g, top + g});
        }
        for (int[] s : spirals) {
            double d = Math.hypot(dx - s[0], dz - s[1]);
            if (d > 5.2 + grow) continue;
            out.add(new int[]{s[3] + 1 - g, s[2] + 4 + g});
        }
        // 보스 방: 가장자리가 울퉁불퉁한 돔
        double da = Math.hypot(dx, dz), ra = ARENA_R + noise(x, z, 11) * 4 - 2 + grow;
        if (da <= ra) {
            int top = F + (int) Math.round(ARENA_H * Math.sqrt(Math.max(0, 1 - Math.pow(da / (ra + 2), 2)))) + 2;
            out.add(new int[]{F + 1 - g, top + g});
        }
        return out;
    }

    private static boolean in(List<int[]> spans, int y) {
        for (int[] s : spans) if (y >= s[0] && y <= s[1]) return true;
        return false;
    }

    @Override
    public void buildColumn(Placer p, int x, int z) {
        int dx = x - OX, dz = z - OZ;
        if (Math.abs(dx) > 78 || Math.abs(dz) > 78) return;
        List<int[]> air = spans(dx, dz, x, z, 0), wall = spans(dx, dz, x, z, 1.6);
        if (wall.isEmpty()) return;
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        for (int[] s : wall) {
            lo = Math.min(lo, s[0]);
            hi = Math.max(hi, s[1]);
        }
        double da = Math.hypot(dx, dz);
        boolean arena = da <= ARENA_R + 4;
        for (int y = lo; y <= hi; y++) {
            if (in(air, y)) {
                p.set(x, y, z, Blocks.AIR.defaultBlockState());
            } else if (in(wall, y)) {
                if (y >= B - 1) continue;                                                // 지면 위(줄기 속)는 줄기 나무결 그대로
                p.set(x, y, z, in(air, y + 1) ? floor(x, y, z, arena) : wall(x, y, z, arena || y < C - 8));
            }
        }
        // 굴·방 장식 (바닥 위 첫 칸, 천장 아래 첫 칸)
        for (int[] s : air) {
            int fy = s[0], cy = s[1];
            if (fy - 1 < B - 1 && !in(air, fy - 1)) decorateFloor(p, x, fy, z, arena);
            if (!in(air, cy + 1) && cy < B) decorateCeiling(p, x, cy, z, arena, cy - fy);
        }
        rooms(p, x, z, dx, dz);
        if (arena) arena(p, x, z, dx, dz, da);
        for (int[] s : spirals) spiral(p, x, z, OX + s[0], OZ + s[1], s[2], s[3]);
        entrance(p, x, z, dx, dz);
    }

    private BlockState floor(int x, int y, int z, boolean arena) {
        int k = h(x, y, z) % 20;
        if (arena) return k < 5 ? Blocks.NETHER_WART_BLOCK.defaultBlockState() : k < 9 ? Blocks.ROOTED_DIRT.defaultBlockState()
                : k < 13 ? Blocks.MOSS_BLOCK.defaultBlockState() : k < 16 ? Blocks.MUD.defaultBlockState() : Blocks.PACKED_MUD.defaultBlockState();
        return k < 6 ? Blocks.ROOTED_DIRT.defaultBlockState() : k < 10 ? Blocks.MOSS_BLOCK.defaultBlockState()
                : k < 13 ? Blocks.COARSE_DIRT.defaultBlockState() : k < 16 ? Blocks.MUD.defaultBlockState() : Blocks.PACKED_MUD.defaultBlockState();
    }

    /** 벽: 위쪽은 흙과 뿌리, 아래로 갈수록 기생충에 감염된 살덩이 */
    private BlockState wall(int x, int y, int z, boolean infected) {
        int k = h(x, y, z) % 24;
        double root = noise(x + y * 3, z - y * 2, 5);
        if (root > 0.82) return Blocks.OAK_WOOD.defaultBlockState();                     // 벽을 가로지르는 뿌리
        if (infected) return k < 6 ? Blocks.NETHER_WART_BLOCK.defaultBlockState() : k < 10 ? Blocks.CRIMSON_HYPHAE.defaultBlockState()
                : k < 13 ? Blocks.MANGROVE_ROOTS.defaultBlockState() : k < 15 ? Blocks.PACKED_MUD.defaultBlockState()
                : k < 16 ? Blocks.SHROOMLIGHT.defaultBlockState() : Blocks.MUD.defaultBlockState();
        return k < 7 ? Blocks.ROOTED_DIRT.defaultBlockState() : k < 11 ? Blocks.MUD.defaultBlockState()
                : k < 14 ? Blocks.MANGROVE_ROOTS.defaultBlockState() : k < 16 ? Blocks.MOSS_BLOCK.defaultBlockState()
                : k < 18 ? Blocks.TUFF.defaultBlockState() : Blocks.DIRT.defaultBlockState();
    }

    private void decorateFloor(Placer p, int x, int y, int z, boolean arena) {
        int k = h(x, y, z + 7) % 100;
        if (arena) {
            if (k < 3) p.set(x, y, z, Blocks.CRIMSON_ROOTS.defaultBlockState());
            else if (k < 5) p.set(x, y, z, Blocks.BONE_BLOCK.defaultBlockState());
            return;
        }
        if (k < 4) p.set(x, y, z, Blocks.MOSS_CARPET.defaultBlockState());
        else if (k < 6) p.set(x, y, z, Blocks.BROWN_MUSHROOM.defaultBlockState());
        else if (k < 7) p.set(x, y, z, Blocks.COBWEB.defaultBlockState());
    }

    private void decorateCeiling(Placer p, int x, int y, int z, boolean arena, int room) {
        int k = h(x, y, z + 13) % 100;
        if (arena) {
            if (k < 10) {
                int len = 1 + h(x, y, z) % Math.max(1, Math.min(9, room - 4));
                for (int i = 0; i < len; i++) p.set(x, y - i, z, (i == len - 1 ? Blocks.WEEPING_VINES : Blocks.WEEPING_VINES_PLANT).defaultBlockState());
            }
            return;
        }
        if (k < 14) p.set(x, y, z, Blocks.HANGING_ROOTS.defaultBlockState());
        else if (k < 17 && room > 3) {                                                    // 빛나는 열매 덩굴 (어두운 굴의 유일한 빛)
            int len = 1 + h(x, y, z) % 3;
            for (int i = 0; i < len; i++) p.set(x, y - i, z, (i == len - 1 ? Blocks.CAVE_VINES : Blocks.CAVE_VINES_PLANT).defaultBlockState()
                    .setValue(CaveVines.BERRIES, i == len - 1));
        } else if (k < 19) p.set(x, y, z, Blocks.GLOW_LICHEN.defaultBlockState().setValue(GlowLichenBlock.getFaceProperty(Direction.UP), true));
        else if (k == 19) p.set(x, y, z, Blocks.SPORE_BLOSSOM.defaultBlockState());
    }

    // ───────────────────────── 회랑의 방 ─────────────────────────

    private void rooms(Placer p, int x, int z, int dx, int dz) {
        int k = h(x, C, z);
        // 수액 샘: 가운데 꿀 웅덩이와 물
        double ds = Math.hypot(dx - SAP[0], dz - SAP[1]);
        if (ds < 4.5) {
            p.set(x, C, z, ds < 2.5 ? Blocks.HONEY_BLOCK.defaultBlockState() : Blocks.WATER.defaultBlockState());
            p.set(x, C - 1, z, Blocks.CLAY.defaultBlockState());
        }
        if (dx == SAP[0] && dz == SAP[1] - 6) OldCapital.chest(p, x, C + 1, z, Direction.SOUTH, seed ^ 0x51, BuiltInLootTables.SIMPLE_DUNGEON);
        // 감염된 둥지: 거미줄, 감염된 돌, 좀벌레·동굴거미 스포너
        double dn = Math.hypot(dx - NEST[0], dz - NEST[1]);
        if (dn < 9) {
            if (k % 5 == 0) p.set(x, C + 1 + k % 3, z, Blocks.COBWEB.defaultBlockState());
            if (k % 3 == 0) p.set(x, C, z, Blocks.INFESTED_COBBLESTONE.defaultBlockState());
        }
        if (dx == NEST[0] && dz == NEST[1]) OldCapital.spawner(p, x, C + 1, z, EntityType.SILVERFISH);
        if (dx == NEST[0] + 4 && dz == NEST[1] - 3) OldCapital.spawner(p, x, C + 1, z, EntityType.CAVE_SPIDER);
        // 고치 방: 거미줄에 감긴 희생자(흰 양털 고치), 좀비 스포너
        double dc = Math.hypot(dx - COCOON[0], dz - COCOON[1]);
        if (dc > 4 && dc < 8 && k % 9 == 0) for (int y = 1; y <= 3; y++) p.set(x, C + y, z, y == 3 ? Blocks.COBWEB.defaultBlockState() : Blocks.WHITE_WOOL.defaultBlockState());
        if (dx == COCOON[0] && dz == COCOON[1]) OldCapital.spawner(p, x, C + 1, z, EntityType.ZOMBIE);
        if (dx == COCOON[0] + 3 && dz == COCOON[1] + 3) OldCapital.chest(p, x, C + 1, z, Direction.NORTH, seed ^ 0x52, BuiltInLootTables.ABANDONED_MINESHAFT);
        // 엘프 탐사대의 마지막 야영지: 침낭, 꺼진 모닥불, 일지(독서대), 보급 상자, 등불
        int cx = OX + CAMP[0], cz = OZ + CAMP[1];
        if (x == cx && z == cz) p.set(x, C + 1, z, Blocks.CAMPFIRE.defaultBlockState().setValue(net.minecraft.world.level.block.CampfireBlock.LIT, false));
        if (x == cx - 3 && z == cz) {
            p.set(x, C + 1, z, Blocks.GREEN_BED.defaultBlockState().setValue(net.minecraft.world.level.block.BedBlock.FACING, Direction.SOUTH)
                    .setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD));
            p.set(x, C + 1, z - 1, Blocks.GREEN_BED.defaultBlockState().setValue(net.minecraft.world.level.block.BedBlock.FACING, Direction.SOUTH)
                    .setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.FOOT));
        }
        if (x == cx + 3 && z == cz - 2) p.set(x, C + 1, z, Blocks.LECTERN.defaultBlockState());
        if (x == cx + 3 && z == cz + 2) OldCapital.chest(p, x, C + 1, z, Direction.WEST, seed ^ 0x53, BuiltInLootTables.VILLAGE_PLAINS_HOUSE);
        if (x == cx && z == cz - 4) {
            for (int y = 1; y <= 2; y++) p.set(x, C + y, z, Blocks.BIRCH_FENCE.defaultBlockState());
            p.set(x, C + 3, z, Blocks.LANTERN.defaultBlockState());
        }
        // 하강 동굴 입구 표지: 뼈와 경고 등불
        if (x == OX + DESCENT[0] - 6 && z == OZ + DESCENT[1]) {
            p.set(x, C + 1, z, Blocks.SKELETON_SKULL.defaultBlockState());
            p.set(x, C + 1, z + 1, Blocks.SOUL_LANTERN.defaultBlockState());
        }
        // 보상 방: 보물 상자 셋
        if (dx == REWARD[0] && dz == REWARD[1] + 3) OldCapital.chest(p, x, F + 1, z, Direction.SOUTH, seed ^ 0x61, BuiltInLootTables.ANCIENT_CITY);
        if (dx == REWARD[0] - 4 && dz == REWARD[1]) OldCapital.chest(p, x, F + 1, z, Direction.EAST, seed ^ 0x62, BuiltInLootTables.JUNGLE_TEMPLE);
        if (dx == REWARD[0] + 4 && dz == REWARD[1]) OldCapital.chest(p, x, F + 1, z, Direction.WEST, seed ^ 0x63, BuiltInLootTables.WOODLAND_MANSION);
        if (Math.hypot(dx - REWARD[0], dz - REWARD[1]) < 2.2) p.set(x, F, z, Blocks.MOSSY_STONE_BRICKS.defaultBlockState());
        if (dx == REWARD[0] && dz == REWARD[1]) p.set(x, F + 1, z, Blocks.FLOWERING_AZALEA.defaultBlockState());
    }

    // ───────────────────────── 기생충의 둥지 ─────────────────────────

    private void arena(Placer p, int x, int z, int dx, int dz, double da) {
        int k = h(x, F, z);
        int ceil = F + (int) Math.round(ARENA_H * Math.sqrt(Math.max(0, 1 - Math.pow(da / (ARENA_R + 2), 2)))) + 2;
        // 가운데 수액 웅덩이 (보스가 나타나는 자리) + 둘레의 감염 고리
        if (da < 5) {
            p.set(x, F, z, Blocks.HONEY_BLOCK.defaultBlockState());
            p.set(x, F - 1, z, Blocks.MUD.defaultBlockState());
        } else if (da < 7) {
            p.set(x, F, z, Blocks.CRIMSON_NYLIUM.defaultBlockState());
            p.set(x, F - 1, z, Blocks.NETHERRACK.defaultBlockState());
        }
        // 세계수 원뿌리: 천장 한가운데서 내려와 웅덩이 위에서 끝남 (기생충이 갉아 놓은 홈)
        int tipY = F + 13;
        for (int y = tipY; y <= ceil + 1; y++) {
            double rr = 2 + 5 * (y - tipY) / (double) Math.max(1, ceil - tipY);
            double wob = 0.6 * Math.sin(Math.atan2(dz, dx) * 4 + y / 3.0);
            if (da > rr + wob) continue;
            boolean gnawed = da > rr + wob - 1.2 && h(x, y, z) % 4 == 0;
            p.set(x, y, z, gnawed ? Blocks.NETHER_WART_BLOCK.defaultBlockState() : da > rr - 1.2 ? Blocks.OAK_WOOD.defaultBlockState() : Blocks.STRIPPED_OAK_WOOD.defaultBlockState());
        }
        if (da < 2.2 && h(x, 1, z) % 2 == 0) {                                            // 원뿌리 끝에서 늘어진 덩굴
            for (int i = 1; i <= 5; i++) p.set(x, tipY - i, z, (i == 5 ? Blocks.WEEPING_VINES : Blocks.WEEPING_VINES_PLANT).defaultBlockState());
        }
        // 천장에서 바닥까지 내리꽂힌 뿌리 여덟 개 (엄폐물)
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * 2 * i / 8 + 0.3;
            double rx = Math.cos(a) * 22, rz = Math.sin(a) * 22;
            double ex = Math.cos(a + 0.25) * 15, ez = Math.sin(a + 0.25) * 15;
            for (int y = F + 1; y <= ceil; y++) {
                double t = (y - F) / (double) (ARENA_H + 2);                            // 아래 0 → 위 1
                double px = rx + (ex - rx) * t, pz = rz + (ez - rz) * t, r = 1.6 + t * 1.6;
                if (Math.hypot(dx - px, dz - pz) <= r) p.set(x, y, z, h(x, y, z) % 7 == 0 ? Blocks.MANGROVE_ROOTS.defaultBlockState() : Blocks.OAK_WOOD.defaultBlockState());
            }
        }
        // 벽의 알주머니 (개구리불 무리) 와 뼈
        if (da > ARENA_R - 6 && da < ARENA_R + 2 && noise(x, z, 17) > 0.74) {
            for (int y = F + 1; y <= F + 4; y++) {
                if (!p.get(x, y, z).isAir()) continue;
                int c = h(x, y, z) % 3;
                p.set(x, y, z, c == 0 ? Blocks.OCHRE_FROGLIGHT.defaultBlockState() : c == 1 ? Blocks.VERDANT_FROGLIGHT.defaultBlockState() : Blocks.PEARLESCENT_FROGLIGHT.defaultBlockState());
                if (h(x, y + 1, z) % 2 == 0) break;
            }
        }
        if (da > 9 && da < ARENA_R - 4 && k % 61 == 0) p.set(x, F + 1, z, Blocks.SKELETON_SKULL.defaultBlockState());
        // 입구 두 곳 표지 (동쪽 굴 = 들어오는 길, 북쪽 굴 = 보상 방)
        if (dz == 6 && dx == ARENA_R - 6) {
            p.set(x, F + 1, z, Blocks.SOUL_LANTERN.defaultBlockState());
        }
        if (dx == 0 && dz == -(ARENA_R - 5)) p.set(x, F + 1, z, Blocks.SOUL_LANTERN.defaultBlockState());
    }

    // ───────────────────────── 줄기 입구·나선 ─────────────────────────

    private void entrance(Placer p, int x, int z, int dx, int dz) {
        // 갈라진 틈 양옆: 이끼와 버섯불
        if (Math.abs(dx) == 3 && dz >= 22 && dz <= 34 && h(x, B, z) % 3 == 0) p.set(x, B + 1 + h(x, 2, z) % 4, z, Blocks.SHROOMLIGHT.defaultBlockState());
        // 줄기 속 빈 방: 바닥 이끼, 벽 등불
        double d = Math.hypot(dx, dz);
        if (d > 5.5 && d < 7 && h(x, B, z) % 11 == 0) p.set(x, B + 3, z, Blocks.LANTERN.defaultBlockState());
        if (d <= 7 && d > 5.3) p.set(x, B, z, Blocks.MOSS_BLOCK.defaultBlockState());
        if (dx == 0 && dz == 36) {                                                      // 입구 앞 표지석
            p.set(x, B + 1, z, Blocks.MOSSY_STONE_BRICK_WALL.defaultBlockState());
            p.set(x, B + 2, z, Blocks.SOUL_LANTERN.defaultBlockState());
        }
    }

    /** 나선 계단 (가운데 기둥 + 반 칸씩 내려가는 디딤판, 10칸에 한 바퀴) */
    private void spiral(Placer p, int x, int z, int sx, int sz, int yTop, int yBot) {
        int dx = x - sx, dz = z - sz;
        double d = Math.hypot(dx, dz);
        if (d > 5.2) return;
        boolean deep = yBot < C - 5;
        if (d <= 1.5) {
            for (int y = yBot + 1; y <= yTop + 3; y++) p.set(x, y, z, deep ? Blocks.CRIMSON_STEM.defaultBlockState() : Blocks.OAK_LOG.defaultBlockState());
            if (h(x, 9, z) % 2 == 0) for (int y = yBot + 3; y < yTop; y += 7) p.set(x, y, z, Blocks.SHROOMLIGHT.defaultBlockState());
            return;
        }
        double ang = Math.atan2(dz, dx);
        if (ang < 0) ang += Math.PI * 2;
        for (int n = 0; ; n++) {
            double hy = yTop - (ang / (Math.PI * 2) + n) * 10;
            if (hy < yBot) break;
            int y = (int) Math.floor(hy);
            boolean half = hy - y >= 0.5;
            BlockState step = deep ? Blocks.MANGROVE_PLANKS.defaultBlockState() : Blocks.SPRUCE_PLANKS.defaultBlockState();
            BlockState slab = (deep ? Blocks.MANGROVE_SLAB : Blocks.SPRUCE_SLAB).defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
            p.set(x, half ? y + 1 : y, z, half ? slab : step);
        }
        if (d > 4.3 && h(x, 10, z) % 9 == 0) for (int y = yBot + 3; y < yTop; y += 7) p.set(x, y, z, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false));
    }
}
