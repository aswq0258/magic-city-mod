package io.github.aswq0258.magiccity.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.EndRodBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.PinkPetalsBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 엘프의 성역 "보랏빛 숲" — 세계수를 중심으로 반지름 360 의 닫힌 숲.
 *  - 바이옴 magiccity:elf_sanctum: 보라색 안개·물·풀·잎(참나무·짙은 참나무 잎이 보랏빛), 공중에 떠도는 보랏빛 입자, 비 없음
 *  - 하늘을 덮는 잎 천장 (지면 위 약 64칸, 가운데 이끼층이 햇빛을 막음) → 숲 안은 발광체 빛만
 *    가장자리 30칸은 천장이 땅까지 내려와 장막을 이루고, 길이 지나는 곳만 아치로 열림
 *  - 천장에 매달린 보랏빛 구슬(펄빛 개구리불)과 청록 수정(바다 랜턴·엔드 막대), 길게 늘어진 발광 열매 덩굴
 *  - 땅: 빛나는 버섯(붉은 갓 아래 펄빛 주름), 자수정, 분홍 꽃잎, 보랏빛 작은 나무
 * 지형 생성 때 자동, 이미 생성된 땅은 /magiccity fix elfwood (잎·버섯불도 보랏빛 계열로 바꿈).
 */
public final class ElfWood {
    private ElfWood() {}

    public static final int FX = DeepWood.FX, FZ = DeepWood.FZ;
    /** 닫힌 숲 반지름, 가장자리 장막 폭 */
    public static final int RZ = 360, CURTAIN = 30;
    /** 천장 높이 (세계수 2층 전망대 위) */
    public static final int ROOF = KingdomTerrain.column(FX, FZ).h() + 66;

    public static boolean in(double x, double z) {
        return Math.hypot(x - FX, z - FZ) < RZ;
    }

    private static double rnd(long a, long b, long salt) {
        long h = a * 341873128712L + b * 132897987541L + salt * 0x9E3779B97F4A7C15L;
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        h *= 0xc4ceb9fe1a85ec53L;
        h ^= h >>> 33;
        return (h >>> 11) * (1.0 / (1L << 53));
    }

    private static final BlockState LEAF = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
    private static final BlockState CHERRY = Blocks.CHERRY_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
    private static final BlockState ORB = Blocks.PEARLESCENT_FROGLIGHT.defaultBlockState();

    /** 숲 잎 섞기: 보랏빛(바이옴 색) 참나무 잎 + 벚나무 잎 + 꽃 진달래 잎 */
    public static BlockState leaf(double k) {
        return k < 0.68 ? LEAF : k < 0.86 ? CHERRY : Blocks.FLOWERING_AZALEA_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
    }

    private static boolean soft(BlockState s) {
        return s.isAir() || s.canBeReplaced() || s.is(net.minecraft.tags.BlockTags.LEAVES) || s.is(Blocks.VINE);
    }

    /** 이 칸의 천장 아래 높이 (장막 구간은 땅 쪽으로 내려옴) */
    private static int roofY(double r, int x, int z) {
        int wob = (int) Math.round(2.5 * Math.sin(x / 23.0) * Math.cos(z / 19.0) + 1.5 * Math.sin((x + z) / 9.0));
        int top = ROOF + wob;
        if (r < RZ - CURTAIN) return top;
        double t = (r - (RZ - CURTAIN)) / CURTAIN;                       // 0 → 1
        int g = KingdomTerrain.column(x, z).h();
        return (int) Math.round(top + (g + 1 - top) * Math.pow(t, 0.7));
    }

    private static boolean nearRoad(int x, int z, double d) {
        return KingdomRoads.nearest(x, z, d, null) >= 0;
    }

    /**
     * 한 청크 꾸미기.
     * @param retop true = 이미 생성된 땅 (기존 진달래 잎 → 보랏빛 잎, 버섯불 → 펄빛 개구리불로도 바꿈)
     */
    public static void decorateChunk(TreeClearer.Access a, int chx, int chz, boolean retop) {
        int bx = chx << 4, bz = chz << 4;
        if (Math.hypot(bx + 8 - FX, bz + 8 - FZ) > RZ + 12) return;
        for (int x = bx; x < bx + 16; x++) {
            for (int z = bz; z < bz + 16; z++) {
                if (!a.ok(x, z)) continue;
                double r = Math.hypot(x - FX, z - FZ);
                if (r >= RZ) continue;
                column(a, x, z, r, retop);
            }
        }
    }

    private static void column(TreeClearer.Access a, int x, int z, double r, boolean retop) {
        KingdomTerrain.Column c = KingdomTerrain.column(x, z);
        int g = c.h();
        int ry = roofY(r, x, z);
        boolean curtain = r >= RZ - CURTAIN;
        boolean road = c.road() > 0 || curtain && nearRoad(x, z, 5);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        // 이미 생성된 땅: 잎·빛 바꾸기 (천장 아래 전체)
        if (retop) {
            for (int y = g + 1; y < Math.min(ry, ROOF + 8); y++) {
                BlockState s = a.get(m.set(x, y, z));
                if (s.is(Blocks.AZALEA_LEAVES)) a.set(m.immutable(), leaf(rnd(x, z, y)));
                else if (s.is(Blocks.SHROOMLIGHT)) a.set(m.immutable(), ORB);
            }
        }
        // 천장: 아래 2칸 잎, 가운데 이끼(햇빛 차단), 위 잎. 장막 구간은 내려온 높이부터 천장까지 채움
        int lo = ry, hi = ry + 5;
        if (road) lo = Math.max(lo, g + 9);                                         // 길이 지나는 곳은 아치
        for (int y = lo; y <= hi; y++) {
            BlockState s = a.get(m.set(x, y, z));
            if (!s.isAir() && !s.canBeReplaced()) continue;                            // 나무(세계수 수관 포함)는 그대로
            int fromTop = hi - y;
            BlockState put = fromTop == 2 || fromTop == 3 ? Blocks.MOSS_BLOCK.defaultBlockState() : leaf(rnd(x, z, y + 7));
            if (y - lo <= 1 && rnd(x, z, y + 3) < 0.03) put = ORB;                         // 천장 아래의 반짝임
            a.set(m.immutable(), put);
        }
        if (curtain) {
            // 장막 안쪽 면에 늘어진 덩굴
            if (rnd(x, z, 11) < 0.08) hang(a, x, lo - 1, z, 3 + (int) (rnd(x, z, 12) * 10), g);
            return;
        }
        double k = rnd(x, z, 1);
        // 천장에 매달린 것들
        if (k < 0.006) {                                                                     // 보랏빛 구슬: 사슬 + 개구리불 2칸
            int len = 4 + (int) (rnd(x, z, 2) * 14);
            int y = ry - 1;
            for (int i = 0; i < len && y > g + 6; i++, y--) if (a.get(m.set(x, y, z)).isAir()) a.set(m.immutable(), Blocks.CHAIN.defaultBlockState());
            if (y > g + 4) {
                a.set(new BlockPos(x, y, z), ORB);
                a.set(new BlockPos(x, y - 1, z), ORB);
            }
        } else if (k < 0.010) {                                                              // 청록 수정
            int y = ry - 1;
            if (a.get(m.set(x, y, z)).isAir() && a.get(m.set(x, y - 2, z)).isAir()) {
                a.set(new BlockPos(x, y, z), Blocks.SEA_LANTERN.defaultBlockState());
                a.set(new BlockPos(x, y - 1, z), Blocks.SEA_LANTERN.defaultBlockState());
                a.set(new BlockPos(x, y - 2, z), Blocks.END_ROD.defaultBlockState().setValue(EndRodBlock.FACING, Direction.DOWN));
            }
        } else if (k < 0.045) {                                                              // 길게 늘어진 발광 열매 덩굴
            hang(a, x, ry - 1, z, 6 + (int) (rnd(x, z, 3) * 26), g);
        }
        // 땅
        if (road || c.water() != Integer.MIN_VALUE && c.water() >= g) return;
        BlockPos gp = new BlockPos(x, g, z), up = gp.above();
        BlockState gs = a.get(gp);
        boolean soil = gs.is(Blocks.GRASS_BLOCK) || gs.is(Blocks.MOSS_BLOCK) || gs.is(Blocks.PODZOL) || gs.is(Blocks.DIRT) || gs.is(Blocks.ROOTED_DIRT);
        if (!soil || !a.get(up).isAir()) return;
        double q = rnd(x, z, 4);
        if (r > 50 && q < 0.0035) mushroom(a, x, g + 1, z, rnd(x, z, 5));
        else if (r > 50 && q < 0.0055) glowTree(a, x, g + 1, z, rnd(x, z, 6));
        else if (q < 0.03) a.set(up, Blocks.PINK_PETALS.defaultBlockState().setValue(PinkPetalsBlock.AMOUNT, 1 + (int) (rnd(x, z, 7) * 4)));
        else if (q < 0.04) a.set(up, Blocks.AMETHYST_CLUSTER.defaultBlockState());
        else if (q < 0.045) {
            a.set(gp, Blocks.AMETHYST_BLOCK.defaultBlockState());
            a.set(up, Blocks.LARGE_AMETHYST_BUD.defaultBlockState());
        }
    }

    /** 발광 열매 덩굴 (y 에서 아래로 len 칸) */
    private static void hang(TreeClearer.Access a, int x, int y, int z, int len, int g) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int i = 0; i < len; i++) {
            int yy = y - i;
            if (yy <= g + 3 || !a.get(m.set(x, yy, z)).isAir()) break;
            boolean last = i == len - 1 || yy - 1 <= g + 3 || !a.get(new BlockPos(x, yy - 1, z)).isAir();
            a.set(new BlockPos(x, yy, z), (last ? Blocks.CAVE_VINES : Blocks.CAVE_VINES_PLANT).defaultBlockState().setValue(CaveVines.BERRIES, rnd(x, yy, z) < 0.4));
            if (last) break;
        }
    }

    /** 빛나는 버섯: 버섯 줄기, 붉은 갓, 갓 아래 펄빛 주름 */
    private static void mushroom(TreeClearer.Access a, int x, int y, int z, double s) {
        int h = 4 + (int) (s * 5), cr = h >= 7 ? 3 : 2;
        for (int dy = 0; dy < h; dy++) if (!soft(a.get(new BlockPos(x, y + dy, z)))) return;
        for (int dy = 0; dy < h; dy++) a.set(new BlockPos(x, y + dy, z), Blocks.MUSHROOM_STEM.defaultBlockState());
        for (int dx = -cr; dx <= cr; dx++) for (int dz = -cr; dz <= cr; dz++) {
            double d = Math.hypot(dx, dz);
            if (d > cr + 0.4) continue;
            if (!a.ok(x + dx, z + dz)) continue;
            BlockPos cap = new BlockPos(x + dx, y + h + (d < cr - 0.8 ? 1 : 0), z + dz);
            if (soft(a.get(cap))) a.set(cap, Blocks.RED_MUSHROOM_BLOCK.defaultBlockState());
            BlockPos gill = new BlockPos(x + dx, y + h - 1, z + dz);
            if ((dx != 0 || dz != 0) && d <= cr - 0.5 && soft(a.get(gill))) a.set(gill, ORB);
        }
    }

    /** 보랏빛 작은 나무: 짙은 참나무 줄기, 둥근 잎 덩어리, 매달린 빛 열매 */
    private static void glowTree(TreeClearer.Access a, int x, int y, int z, double s) {
        int h = 5 + (int) (s * 4);
        for (int dy = 0; dy < h + 3; dy++) if (!soft(a.get(new BlockPos(x, y + dy, z)))) return;
        for (int dy = 0; dy < h; dy++) a.set(new BlockPos(x, y + dy, z), Blocks.DARK_OAK_LOG.defaultBlockState());
        int cy = y + h;
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) for (int dy = -2; dy <= 2; dy++) {
            double d = Math.sqrt(dx * dx + dz * dz + dy * dy * 1.6);
            if (d > 3.2 || !a.ok(x + dx, z + dz)) continue;
            BlockPos p = new BlockPos(x + dx, cy + dy, z + dz);
            if (!soft(a.get(p))) continue;
            double k = rnd(x + dx, z + dz, cy + dy);
            a.set(p, k < 0.06 ? ORB : leaf(k));
        }
    }
}
