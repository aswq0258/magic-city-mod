package io.github.aswq0258.magiccity.city.feature;

import io.github.aswq0258.magiccity.build.Placer;
import io.github.aswq0258.magiccity.city.CityLayout;
import io.github.aswq0258.magiccity.city.Deco;
import io.github.aswq0258.magiccity.city.Fantasy;
import io.github.aswq0258.magiccity.city.Royal;
import io.github.aswq0258.magiccity.city.Shapes;
import io.github.aswq0258.magiccity.city.Towers;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import static io.github.aswq0258.magiccity.city.Palette.*;

/**
 * 왕립 마법 아카데미의 상징: 대마법사의 수정탑 (마나 결정탑)
 *  - 이끼 낀 돌 언덕 위, 짙은 돌(심층암·응회암) 몸통이 위로 갈수록 가늘어지고
 *    밝은 응회암 띠가 두 갈래 나선으로 감아 오름, 둘레에 떠 있는 바위 조각들
 *  - 입구 위에 보라·분홍 수정 연꽃, 허리에 매달린 마나 구슬, 꼭대기에 사방으로 뻗은 거대한 수정 왕관
 *  - 내부: 가운데 마나 빛기둥, 벽을 따라 나선 계단, 층마다 서재·마법부여실·연금술실·천체 관측실
 * 도시 전체 결계탑을 관리하는 아카데미의 중심.
 */
public class ArchmageTower extends Feature {
    private static final int H = 62, MOUND = 4, FH = 10;
    private static final BlockState[] STONE = {
            Blocks.DEEPSLATE_TILES.defaultBlockState(), Blocks.POLISHED_DEEPSLATE.defaultBlockState(),
            Blocks.COBBLED_DEEPSLATE.defaultBlockState(), Blocks.TUFF_BRICKS.defaultBlockState(), Blocks.DEEPSLATE_BRICKS.defaultBlockState(),
    };
    private static final BlockState BAND = Blocks.POLISHED_TUFF.defaultBlockState();
    private static final BlockState BAND2 = Blocks.TUFF_BRICKS.defaultBlockState();
    private static final BlockState PURPLE_GLASS = Blocks.PURPLE_STAINED_GLASS.defaultBlockState();
    private static final BlockState MAGENTA_GLASS = Blocks.MAGENTA_STAINED_GLASS.defaultBlockState();
    private static final BlockState PINK_GLASS = Blocks.PINK_STAINED_GLASS.defaultBlockState();
    private static final BlockState WHITE_GLASS = Blocks.WHITE_STAINED_GLASS.defaultBlockState();
    private final int tx, tz;
    private final Direction front;
    private final long seed;

    public ArchmageTower(int tx, int tz, Direction front, long seed) {
        super(tx - 16, tz - 16, tx + 16, tz + 16);
        this.tx = tx;
        this.tz = tz;
        this.front = front;
        this.seed = seed;
    }

    private static double radius(int ly) {
        return 6.5 - ly * 1.6 / H;
    }

    @Override
    public void build(Placer p, CityLayout city) {
        if (!p.intersects(minX, minZ, maxX, maxZ)) return;
        final int b = groundY, y0 = b + MOUND;
        final double twist = Math.PI * 4 / H;              // 나선 두 바퀴
        p.forColumns(minX, minZ, maxX, maxZ, (x, z) -> {
            int dx = x - tx, dz = z - tz;
            double d = Math.hypot(dx, dz), ang = Math.atan2(dz, dx);
            int h = Shapes.hash(x, 41, z, seed);
            // 이끼 낀 돌 언덕 (3단)
            if (d <= 12.5) {
                int lv = d <= 8.5 ? MOUND : d <= 10.5 ? 3 : 1;
                for (int ly = 1; ly <= lv; ly++) p.set(x, b + ly, z, ly == lv ? (h % 4 == 0 ? Blocks.MOSS_BLOCK.defaultBlockState() : h % 4 == 1 ? GRASS : STONE[h % STONE.length]) : STONE[(h >> 3) % STONE.length]);
                if (lv < MOUND && h % 9 == 0) p.set(x, b + lv + 1, z, h % 2 == 0 ? Blocks.FERN.defaultBlockState() : Fantasy.CLUSTER_UP);
            }
            // 몸통
            for (int ly = 0; ly <= H; ly++) {
                double r = radius(ly);
                if (d > r + 2.6) continue;
                double band = Math.cos((ang - ly * twist) * 2);   // 두 갈래 나선 띠
                boolean shell = d > r - 1.6 && d <= r + 0.5;
                boolean ridge = d > r + 0.5 && d <= r + 1.5 + (band > 0.97 ? 1 : 0) && band > 0.82;
                int y = y0 + ly;
                if (shell) {
                    BlockState s = STONE[Math.floorMod((int) Math.floor(ang * 6) + (h & 1), STONE.length)];
                    if (band > 0.82) s = BAND;
                    if (ly % FH == 5 && Math.floorMod((int) Math.floor(ang * r), 5) == 0) s = PURPLE_GLASS;
                    p.set(x, y, z, s);
                } else if (ridge) {
                    p.set(x, y, z, band > 0.95 ? BAND2 : BAND);
                } else if (d <= r - 1.6) {
                    p.set(x, y, z, ly % FH == 0 ? (Math.max(Math.abs(dx), Math.abs(dz)) <= 2 ? AIR : Blocks.DARK_OAK_PLANKS.defaultBlockState()) : AIR);
                }
            }
        });
        // 가운데 마나 빛기둥 + 나선 계단
        int[][] ring = Towers.ringCells(2);
        for (int ly = 1; ly <= H; ly++) {
            p.set(tx, y0 + ly, tz, ly % 6 == 3 ? Fantasy.AMETHYST : LIGHT);
            for (int[] c : ring) p.set(tx + c[0], y0 + ly, tz + c[1], AIR);
        }
        for (int i = 0; i < H - 1; i++) {
            int[] c0 = ring[i % ring.length], c1 = ring[(i + 1) % ring.length];
            p.set(tx + c0[0], y0 + i + 1, tz + c0[1], Blocks.DARK_OAK_STAIRS.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.StairBlock.FACING, Deco.toward(c1[0] - c0[0], c1[1] - c0[1])));
        }
        floors(p, y0);
        door(p, b, y0);
        lotus(p, y0);
        floatingRocks(p, y0);
        // 허리에 매달린 마나 구슬
        double oa = Math.PI * 0.25;
        int ox = tx + (int) Math.round(Math.cos(oa) * 9), oz = tz + (int) Math.round(Math.sin(oa) * 9), oy = y0 + 30;
        orb(p, ox, oy, oz, 2);
        for (int y = oy + 3; y <= oy + 6; y++) p.set(ox, y, oz, Blocks.CHAIN.defaultBlockState());
        for (int k = 0; k <= 4; k++) p.set(ox - (int) Math.signum(ox - tx) * k / 2, oy + 6, oz - (int) Math.signum(oz - tz) * k / 2, BAND);
        crown(p, y0 + H);
    }

    /** 층마다 방: 서재 / 마법부여실 / 연금술실 / 천체 관측실 / 대마법사 집무실 */
    private void floors(Placer p, int y0) {
        for (int k = 0; k * FH < H - 4; k++) {
            int fy = y0 + k * FH + 1;
            double r = radius(k * FH) - 2;
            for (int i = 0; i < 12; i++) {
                double a = Math.PI * 2 * i / 12;
                int x = tx + (int) Math.round(Math.cos(a) * r), z = tz + (int) Math.round(Math.sin(a) * r);
                if (Math.max(Math.abs(x - tx), Math.abs(z - tz)) <= 2) continue;
                BlockState s = switch (k % 5) {
                    case 0 -> i % 3 == 0 ? Deco.lectern(Direction.NORTH) : BOOKSHELF;
                    case 1 -> i % 4 == 0 ? ENCHANTING_TABLE : BOOKSHELF;
                    case 2 -> i % 3 == 0 ? Deco.BREWING : i % 3 == 1 ? Blocks.CAULDRON.defaultBlockState() : Deco.BARREL;
                    case 3 -> i % 4 == 0 ? Blocks.CARTOGRAPHY_TABLE.defaultBlockState() : i % 4 == 2 ? BOOKSHELF : Deco.candles(3);
                    default -> i % 2 == 0 ? Royal.GOLD : Fantasy.CLUSTER_UP;
                };
                p.set(x, fy, z, s);
                if (s == BOOKSHELF) p.set(x, fy + 1, z, BOOKSHELF);
            }
        }
    }

    private void door(Placer p, int b, int y0) {
        double r = radius(0);
        int fx = front.getStepX(), fz = front.getStepZ();
        int lx = -fz, lz = fx;
        for (int k = 0; k <= (int) r + 6; k++) {
            for (int s = -1; s <= 1; s++) {
                int x = tx + fx * k + lx * s, z = tz + fz * k + lz * s;
                if (k <= (int) r + 1) {
                    if (k < 3) continue;
                    for (int ly = 1; ly <= 4; ly++) p.set(x, y0 + ly, z, AIR);
                    p.set(x, y0 + 5, z, s == 0 ? Fantasy.AMETHYST : BAND);
                } else {
                    int step = MOUND - (k - (int) r - 2);
                    for (int ly = 1; ly <= MOUND; ly++) p.set(x, b + ly, z, ly < step ? STONE[1] : ly == step ? Blocks.POLISHED_DEEPSLATE_STAIRS.defaultBlockState()
                            .setValue(net.minecraft.world.level.block.StairBlock.FACING, front.getOpposite()) : AIR);
                }
            }
        }
        int dx = tx + fx * ((int) r + 2), dz = tz + fz * ((int) r + 2);
        Fantasy.crystalLamp(p, dx + lx * 3, dz + lz * 3, y0);
        Fantasy.crystalLamp(p, dx - lx * 3, dz - lz * 3, y0);
    }

    /** 입구 위 수정 연꽃 (꽃잎 5장) */
    private void lotus(Placer p, int y0) {
        double r = radius(0);
        int fx = front.getStepX(), fz = front.getStepZ(), lx = -fz, lz = fx;
        int cx = tx + fx * ((int) r + 1), cz = tz + fz * ((int) r + 1);
        int[] offs = {-6, -3, 0, 3, 6};
        int[] hs = {4, 6, 8, 6, 4};
        for (int i = 0; i < offs.length; i++) {
            for (int k = 0; k <= hs[i]; k++) {
                int w = Math.max(0, (hs[i] - k) / 3);
                for (int s = -w; s <= w; s++) {
                    int x = cx + lx * (offs[i] + s), z = cz + lz * (offs[i] + s);
                    BlockState g = k == hs[i] ? WHITE_GLASS : Math.abs(s) == w ? MAGENTA_GLASS : (k < 2 ? Fantasy.AMETHYST : PURPLE_GLASS);
                    p.set(x, y0 + 5 + k, z, g);
                }
            }
        }
    }

    private void floatingRocks(Placer p, int y0) {
        for (int i = 0; i < 9; i++) {
            int h = Shapes.hash(i, 3, 7, seed);
            double a = Math.PI * 2 * i / 9 + (h % 10) / 10.0;
            int ly = 10 + i * 5 + h % 4;
            double r = radius(ly) + 4 + h % 3;
            int x = tx + (int) Math.round(Math.cos(a) * r), z = tz + (int) Math.round(Math.sin(a) * r);
            for (int dx = 0; dx <= 1; dx++) for (int dz = 0; dz <= (h % 2); dz++) for (int dy = 0; dy <= 1; dy++) {
                p.set(x + dx, y0 + ly + dy, z + dz, (dx + dy + dz) % 2 == 0 ? BAND : STONE[h % STONE.length]);
            }
        }
    }

    private void orb(Placer p, int ox, int oy, int oz, int r) {
        p.forColumns(ox - r, oz - r, ox + r, oz + r, (x, z) -> {
            for (int y = oy - r; y <= oy + r; y++) {
                double d = Math.sqrt((x - ox) * (x - ox) + (y - oy) * (y - oy) + (z - oz) * (z - oz));
                if (d <= r + 0.3) p.set(x, y, z, d < 1 ? LIGHT : ((x + y + z) & 1) == 0 ? MAGENTA_GLASS : Fantasy.AMETHYST);
            }
        });
    }

    /** 꼭대기 수정 왕관: 가운데 큰 수정 + 바깥으로 기울어 뻗은 수정 가시 8개 */
    private void crown(Placer p, int top) {
        for (int k = 0; k <= 22; k++) {
            int w = k < 8 ? 2 : k < 16 ? 1 : 0;
            for (int dx = -w; dx <= w; dx++) for (int dz = -w; dz <= w; dz++) {
                if (Math.abs(dx) + Math.abs(dz) > w + 1) continue;
                p.set(tx + dx, top + k, tz + dz, k > 19 ? WHITE_GLASS : k > 12 ? PINK_GLASS : (dx + dz + k) % 3 == 0 ? Fantasy.AMETHYST : PURPLE_GLASS);
            }
        }
        p.set(tx, top + 23, tz, END_ROD);
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * 2 * i / 8 + 0.2;
            double ca = Math.cos(a), sa = Math.sin(a);
            int len = 12 + Shapes.hash(i, 5, 5, seed) % 6;
            for (int k = 0; k <= len; k++) {
                double rr = 3.5 + k * 0.45;
                int x = tx + (int) Math.round(ca * rr), z = tz + (int) Math.round(sa * rr), y = top - 2 + k;
                BlockState g = k > len - 2 ? WHITE_GLASS : k > len / 2 ? (k % 2 == 0 ? PINK_GLASS : MAGENTA_GLASS) : (k % 3 == 0 ? Fantasy.AMETHYST : PURPLE_GLASS);
                p.set(x, y, z, g);
                if (k < len - 3) {
                    p.set(x + (int) Math.signum(Math.round(-sa)), y, z + (int) Math.signum(Math.round(ca)), k < len / 2 ? PURPLE_GLASS : MAGENTA_GLASS);
                    p.set(x, y - 1, z, k < 4 ? Fantasy.AMETHYST : PURPLE_GLASS);
                }
            }
            p.set(tx + (int) Math.round(ca * (3.5 + len * 0.45)), top - 1 + len, tz + (int) Math.round(sa * (3.5 + len * 0.45)), END_ROD);
        }
        Fantasy.halo(p, tx, tz, top + 8, 11.5);
    }
}
