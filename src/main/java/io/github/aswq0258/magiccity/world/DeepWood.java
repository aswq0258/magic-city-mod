package io.github.aswq0258.magiccity.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * 엘프 대수림 심부 "태고의 숲" — 세계수 성역을 둘러싼 숲 한가운데를 바닐라 바이옴보다 깊고 신비롭게 꾸밈.
 *  - 고목: 지름 5~7 의 굵은 줄기(높이 26~42), 땅 위로 드러난 뿌리 버팀목, 사방으로 뻗은 가지와 참나무·진달래·꽃 진달래 잎 덩어리,
 *    수관 속 버섯불(은은한 빛), 늘어진 발광 열매 덩굴과 덩굴, 포자 꽃
 *  - 바닥: 이끼 융단, 고사리·큰 고사리, 진달래 덤불, 버섯, 은방울꽃, 포드졸·이끼·뿌리 흙
 *  - 쓰러진 이끼 통나무, 이끼 낀 선돌(영혼 등불·자수정), 요정의 버섯 고리(가운데 은은한 빛)
 * 지형 생성 때 청크마다 자동으로, 이미 생성된 땅은 /magiccity fix deepwood 로.
 * 세계수 성역(반지름 240) 과 길(18칸) 근처에는 고목을 두지 않음.
 */
public final class DeepWood {
    private DeepWood() {}

    /** 숲 중심 = 세계수 (도면 -1300, -300) */
    public static final int FX = KingdomTerrain.worldX(-1300), FZ = KingdomTerrain.worldZ(-300);
    private static final int SANCTUARY = 240;
    /** 이 반지름 안에는 고목이 없음 (세계수 수관·성역 시설 바깥) */
    private static final int INNER = 212;

    /** 태고의 숲 안인지 (지형 종류와 같은 판정) */
    public static boolean in(int x, int z) {
        return KingdomTerrain.column(x, z).kind() == KingdomTerrain.Kind.DEEPWOOD;
    }

    /** 도면 좌표 판정 (KingdomTerrain 에서 씀) */
    static boolean mask(double px, double pz, int x, int z) {
        double q = Math.sqrt(Math.pow((px + 1300) / 760.0, 2) + Math.pow((pz + 300) / 660.0, 2));
        return q + 0.14 * (KingdomTerrain.fbm(x / 150.0, z / 150.0, 2, 61) - 0.5) < 0.62;
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

    private static boolean nearRoad(int x, int z, double d) {
        return KingdomRoads.nearest(x, z, d, null) >= 0;
    }

    private static int ground(int x, int z) {
        return KingdomTerrain.column(x, z).h();
    }

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    private static BlockState log(Block b, Direction.Axis axis) {
        return b.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis);
    }

    private static BlockState leaf(Block b) {
        return b.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
    }

    private static boolean soft(BlockState s) {
        return s.isAir() || s.canBeReplaced() || s.is(net.minecraft.tags.BlockTags.LEAVES) || s.is(Blocks.VINE);
    }

    private static void put(TreeClearer.Access a, int x, int y, int z, BlockState s) {
        if (!a.ok(x, z)) return;
        a.set(new BlockPos(x, y, z), s);
    }

    private static void putSoft(TreeClearer.Access a, int x, int y, int z, BlockState s) {
        if (!a.ok(x, z)) return;
        BlockPos p = new BlockPos(x, y, z);
        if (soft(a.get(p))) a.set(p, s);
    }

    /**
     * 한 청크 꾸미기.
     * @param retop true = 이미 생성된 땅 (풀 지표를 포드졸·이끼로 바꾸고 바닥 식물도 심음)
     */
    public static void decorateChunk(TreeClearer.Access a, int chx, int chz, boolean retop) {
        int bx = chx << 4, bz = chz << 4;
        if (!in(bx + 8, bz + 8) && !in(bx, bz) && !in(bx + 15, bz + 15)) return;
        // 바닥
        for (int x = bx; x < bx + 16; x++) {
            for (int z = bz; z < bz + 16; z++) {
                if (!a.ok(x, z)) continue;
                KingdomTerrain.Column c = KingdomTerrain.column(x, z);
                if (c.kind() != KingdomTerrain.Kind.DEEPWOOD || c.road() > 0 && c.roadEdge() <= 2) continue;
                if (c.water() != Integer.MIN_VALUE && c.water() >= c.h()) continue;
                if (Math.hypot(x - FX, z - FZ) < SANCTUARY - 30) continue;
                floor(a, x, c.h(), z, retop);
            }
        }
        if (Math.hypot(bx + 8 - FX, bz + 8 - FZ) < INNER - 24) return;
        // 고목 (청크마다 0~1 그루). 성역 쪽으로 갈수록 드물고 작아져서, 고목 숲이 벽처럼 뚝 끊기지 않고 서서히 엷어짐
        {
            int tx = bx + 4 + (int) (rnd(chx, chz, 2) * 8), tz = bz + 4 + (int) (rnd(chx, chz, 3) * 8);
            boolean outer = Math.hypot(bx + 8 - FX, bz + 8 - FZ) >= SANCTUARY;               // 원래 고목 숲 (예전과 똑같이)
            double ramp = outer ? 1 : Math.max(0, Math.min(1, (Math.hypot(tx - FX, tz - FZ) - INNER) / 40.0));
            if (ramp > 0 && rnd(chx, chz, 1) < (outer ? 0.5 : 0.5 * (0.35 + 0.65 * ramp))
                    && in(tx, tz) && !nearRoad(tx, tz, 18) && KingdomTerrain.column(tx, tz).water() == Integer.MIN_VALUE)
                giant(a, tx, ground(tx, tz) + 1, tz, (long) chx * 31 + chz, outer ? 1 : 0.5 + 0.4 * ramp);
        }
        if (Math.hypot(bx + 8 - FX, bz + 8 - FZ) < SANCTUARY) return;
        if (rnd(chx, chz, 4) < 0.3) {
            int lx = bx + 3 + (int) (rnd(chx, chz, 5) * 10), lz = bz + 3 + (int) (rnd(chx, chz, 6) * 10);
            if (in(lx, lz) && !nearRoad(lx, lz, 8)) fallenLog(a, lx, lz, rnd(chx, chz, 7));
        }
        if (rnd(chx, chz, 8) < 0.1) {
            int sx = bx + 5 + (int) (rnd(chx, chz, 9) * 6), sz = bz + 5 + (int) (rnd(chx, chz, 10) * 6);
            if (in(sx, sz) && !nearRoad(sx, sz, 8)) spiritStone(a, sx, sz, rnd(chx, chz, 11));
        }
        if (rnd(chx, chz, 12) < 0.06) {
            int fx = bx + 6 + (int) (rnd(chx, chz, 13) * 4), fz = bz + 6 + (int) (rnd(chx, chz, 14) * 4);
            if (in(fx, fz) && !nearRoad(fx, fz, 8)) fairyRing(a, fx, fz);
        }
    }

    // ───────────────────────── 바닥 ─────────────────────────

    private static void floor(TreeClearer.Access a, int x, int g, int z, boolean retop) {
        BlockPos gp = new BlockPos(x, g, z), up = gp.above();
        BlockState gs = a.get(gp);
        if (!(gs.is(Blocks.GRASS_BLOCK) || gs.is(Blocks.PODZOL) || gs.is(Blocks.MOSS_BLOCK) || gs.is(Blocks.DIRT) || gs.is(Blocks.ROOTED_DIRT) || gs.is(Blocks.COARSE_DIRT))) return;
        double k = rnd(x, z, 21);
        if (retop && gs.is(Blocks.GRASS_BLOCK)) {
            BlockState top = k < 0.18 ? Blocks.PODZOL.defaultBlockState() : k < 0.3 ? Blocks.MOSS_BLOCK.defaultBlockState()
                    : k < 0.36 ? Blocks.ROOTED_DIRT.defaultBlockState() : null;
            if (top != null) a.set(gp, top);
        }
        if (!a.get(up).isAir()) return;
        double q = rnd(x, z, 22);
        BlockState plant = null;
        if (q < 0.12) plant = Blocks.FERN.defaultBlockState();
        else if (q < 0.16) {
            if (a.get(up.above()).isAir()) {
                a.set(up, Blocks.LARGE_FERN.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER));
                a.set(up.above(), Blocks.LARGE_FERN.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER));
            }
            return;
        } else if (q < 0.30) plant = Blocks.MOSS_CARPET.defaultBlockState();
        else if (q < 0.34) plant = Blocks.SHORT_GRASS.defaultBlockState();
        else if (q < 0.345) plant = Blocks.RED_MUSHROOM.defaultBlockState();
        else if (q < 0.355) plant = Blocks.BROWN_MUSHROOM.defaultBlockState();
        else if (q < 0.362) plant = Blocks.AZALEA.defaultBlockState();
        else if (q < 0.366) plant = Blocks.FLOWERING_AZALEA.defaultBlockState();
        else if (q < 0.372) plant = Blocks.LILY_OF_THE_VALLEY.defaultBlockState();
        else if (q < 0.375) plant = Blocks.BLUE_ORCHID.defaultBlockState();
        if (plant != null) a.set(up, plant);
    }

    // ───────────────────────── 고목 ─────────────────────────

    static void giant(TreeClearer.Access a, int tx, int y0, int tz, long seed) {
        giant(a, tx, y0, tz, seed, 1);
    }

    /** sc = 크기 배율 (성역 가장자리의 어린 고목은 0.5 정도) */
    static void giant(TreeClearer.Access a, int tx, int y0, int tz, long seed, double sc) {
        double r = rnd(seed, 1, 31);
        int H = (int) ((26 + r * 16) * sc);
        double R0 = (2.2 + rnd(seed, 2, 31) * 1.1) * (0.6 + 0.4 * sc);
        Block wood = rnd(seed, 3, 31) < 0.7 ? Blocks.DARK_OAK_LOG : Blocks.OAK_LOG;
        BlockState trunk = log(wood, Direction.Axis.Y);
        // 줄기 (아래는 뿌리 쪽으로 퍼짐)
        int ext = (int) Math.ceil(R0 + 2);
        for (int dy = -3; dy <= H; dy++) {
            double flare = 1.8 * Math.pow(Math.max(0, 1 - Math.max(0, dy) / 5.0), 2);
            double rr = R0 * (1 - 0.45 * Math.max(0, dy) / H) + flare;
            for (int dx = -ext; dx <= ext; dx++) for (int dz = -ext; dz <= ext; dz++) {
                double d = Math.hypot(dx, dz);
                if (d > rr) continue;
                put(a, tx + dx, y0 + dy, tz + dz, trunk);
                // 바깥 면에 덩굴·발광 이끼
                if (d > rr - 1 && dy > 3 && rnd(tx + dx, tz + dz, dy * 7L) < 0.05) {
                    Direction out = Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
                    int vx = tx + dx + out.getStepX(), vz = tz + dz + out.getStepZ();
                    BlockState v = rnd(vx, vz, dy) < 0.5
                            ? Blocks.VINE.defaultBlockState().setValue(VineBlock.getPropertyForFace(out.getOpposite()), true)
                            : Blocks.GLOW_LICHEN.defaultBlockState().setValue(net.minecraft.world.level.block.GlowLichenBlock.getFaceProperty(out.getOpposite()), true);
                    putSoft(a, vx, y0 + dy, vz, v);
                }
            }
        }
        // 뿌리 버팀목
        int roots = 5 + (int) (rnd(seed, 4, 31) * 3);
        for (int i = 0; i < roots; i++) {
            double ang = Math.PI * 2 * i / roots + rnd(seed, 10 + i, 31) * 0.5;
            double len = (6 + rnd(seed, 20 + i, 31) * 5) * sc;
            Direction.Axis ax = Math.abs(Math.cos(ang)) > Math.abs(Math.sin(ang)) ? Direction.Axis.X : Direction.Axis.Z;
            for (double t = R0; t <= R0 + len; t += 0.5) {
                double f = (t - R0) / len;
                int x = tx + (int) Math.round(Math.cos(ang) * t), z = tz + (int) Math.round(Math.sin(ang) * t);
                int y = ground(x, z) + (int) Math.round(3.2 * Math.pow(1 - f, 1.6));
                put(a, x, y, z, log(wood, ax));
                if (f < 0.5) put(a, x, y - 1, z, log(wood, ax));
            }
        }
        // 가지와 잎 덩어리
        int branches = 5 + (int) (rnd(seed, 5, 31) * 3);
        for (int i = 0; i < branches; i++) {
            double ang = Math.PI * 2 * i / branches + rnd(seed, 40 + i, 31);
            int by = y0 + (int) (H * (0.55 + 0.4 * rnd(seed, 50 + i, 31)));
            double len = (7 + rnd(seed, 60 + i, 31) * 6) * sc, rise = (3 + rnd(seed, 70 + i, 31) * 4) * sc;
            Direction.Axis ax = Math.abs(Math.cos(ang)) > Math.abs(Math.sin(ang)) ? Direction.Axis.X : Direction.Axis.Z;
            int ex = tx, ey = by, ez = tz;
            for (double t = 0; t <= len; t += 0.5) {
                ex = tx + (int) Math.round(Math.cos(ang) * t);
                ez = tz + (int) Math.round(Math.sin(ang) * t);
                ey = by + (int) Math.round(rise * t / len);
                put(a, ex, ey, ez, log(wood, t < len * 0.3 ? Direction.Axis.Y : ax));
            }
            cluster(a, ex, ey + 1, ez, (4 + rnd(seed, 80 + i, 31) * 1.5) * (0.6 + 0.4 * sc), seed + i);
        }
        cluster(a, tx, y0 + H + 2, tz, 6.5 * (0.6 + 0.4 * sc), seed + 99);
    }

    private static void cluster(TreeClearer.Access a, int cx, int cy, int cz, double r, long seed) {
        int ir = (int) Math.ceil(r);
        for (int dx = -ir; dx <= ir; dx++) for (int dz = -ir; dz <= ir; dz++) {
            for (int dy = -ir / 2 - 1; dy <= ir / 2 + 1; dy++) {
                double d = Math.sqrt(dx * dx + dz * dz + dy * dy * 2.2);
                double edge = r * (0.85 + 0.3 * rnd(cx + dx, cz + dz, seed + dy));
                if (d > edge) continue;
                double k = rnd(cx + dx, cz + dz, seed * 13 + dy);
                boolean elf = ElfWood.in(cx, cz);
                BlockState s = k < 0.025 ? (elf ? Blocks.PEARLESCENT_FROGLIGHT.defaultBlockState() : Blocks.SHROOMLIGHT.defaultBlockState())
                        : elf ? ElfWood.leaf((k - 0.025) / 0.975)
                        : k < 0.6 ? leaf(Blocks.OAK_LEAVES) : k < 0.85 ? leaf(Blocks.AZALEA_LEAVES) : leaf(Blocks.FLOWERING_AZALEA_LEAVES);
                putSoft(a, cx + dx, cy + dy, cz + dz, s);
            }
            // 아래로 늘어진 발광 열매 덩굴·포자 꽃
            double hang = rnd(cx + dx, cz + dz, seed + 500);
            if (Math.hypot(dx, dz) <= r * 0.9 && hang < 0.1) {
                int bottom = cy - (int) Math.round(Math.sqrt(Math.max(0, r * r - dx * dx - dz * dz)) / 1.48) - 1;
                if (hang < 0.015) {
                    putSoft(a, cx + dx, bottom, cz + dz, Blocks.SPORE_BLOSSOM.defaultBlockState());
                } else {
                    int len = 2 + (int) (hang * 60);
                    for (int k = 0; k < len; k++) {
                        boolean last = k == len - 1;
                        putSoft(a, cx + dx, bottom - k, cz + dz, (last ? Blocks.CAVE_VINES : Blocks.CAVE_VINES_PLANT).defaultBlockState()
                                .setValue(CaveVines.BERRIES, rnd(dx, k, seed) < 0.35));
                    }
                }
            }
        }
    }

    // ───────────────────────── 쓰러진 통나무·선돌·버섯 고리 ─────────────────────────

    private static void fallenLog(TreeClearer.Access a, int x, int z, double r) {
        boolean alongX = r < 0.5;
        int len = 6 + (int) (r * 10) % 6;
        Block wood = r < 0.3 ? Blocks.OAK_LOG : Blocks.DARK_OAK_LOG;
        for (int i = 0; i < len; i++) {
            int lx = x + (alongX ? i : 0), lz = z + (alongX ? 0 : i);
            int y = ground(lx, lz) + 1;
            if (i == len / 2 && r > 0.6) continue;                                            // 썩어 끊긴 곳
            put(a, lx, y, lz, log(wood, alongX ? Direction.Axis.X : Direction.Axis.Z));
            double k = rnd(lx, lz, 77);
            putSoft(a, lx, y + 1, lz, k < 0.5 ? Blocks.MOSS_CARPET.defaultBlockState() : k < 0.65 ? Blocks.BROWN_MUSHROOM.defaultBlockState() : AIR);
        }
    }

    private static void spiritStone(TreeClearer.Access a, int x, int z, double r) {
        int g = ground(x, z);
        int hgt = 4 + (int) (r * 3);
        for (int k = 1; k <= hgt; k++) put(a, x, g + k, z, k == hgt ? Blocks.CHISELED_STONE_BRICKS.defaultBlockState()
                : rnd(x, k, 5) < 0.5 ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState() : Blocks.MOSSY_COBBLESTONE.defaultBlockState());
        put(a, x, g + hgt + 1, z, r < 0.5 ? Blocks.SOUL_LANTERN.defaultBlockState() : Blocks.AMETHYST_CLUSTER.defaultBlockState());
        for (int i = 0; i < 6; i++) {                                                         // 둘레 작은 돌
            double ang = Math.PI * 2 * i / 6;
            int sx = x + (int) Math.round(Math.cos(ang) * 3), sz = z + (int) Math.round(Math.sin(ang) * 3);
            put(a, sx, ground(sx, sz) + 1, sz, Blocks.MOSSY_COBBLESTONE.defaultBlockState());
            putSoft(a, sx, ground(sx, sz) + 2, sz, Blocks.MOSS_CARPET.defaultBlockState());
        }
    }

    private static void fairyRing(TreeClearer.Access a, int x, int z) {
        int g = ground(x, z);
        put(a, x, g, z, Blocks.SHROOMLIGHT.defaultBlockState());
        putSoft(a, x, g + 1, z, Blocks.MOSS_CARPET.defaultBlockState());
        for (int i = 0; i < 14; i++) {
            double ang = Math.PI * 2 * i / 14;
            int sx = x + (int) Math.round(Math.cos(ang) * 3.5), sz = z + (int) Math.round(Math.sin(ang) * 3.5);
            putSoft(a, sx, ground(sx, sz) + 1, sz, (i & 1) == 0 ? Blocks.RED_MUSHROOM.defaultBlockState() : Blocks.BROWN_MUSHROOM.defaultBlockState());
        }
    }
}
