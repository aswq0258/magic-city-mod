package io.github.aswq0258.magiccity.city.feature;

import io.github.aswq0258.magiccity.build.Placer;
import io.github.aswq0258.magiccity.city.CityLayout;
import io.github.aswq0258.magiccity.city.Deco;
import io.github.aswq0258.magiccity.city.Palette;
import io.github.aswq0258.magiccity.city.Shapes;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import static io.github.aswq0258.magiccity.city.Palette.*;

/**
 * 2층 주민 집
 *
 * 외관: 석영 기둥 모서리 + 방해석/매끄러운 석영 벽 + 민트 층띠, 쐐기돌 달린 창, 창 밑 화분과 창턱,
 *       기둥 두 개가 받치는 2층 발코니(현관 지붕 겸), 처마 밑 석영 내물림, 민트 모임지붕 + 꼭대기 장식, 굴뚝
 * 1층: 주방(가마솥·직업 블록·통), 식탁과 의자 4개, 소파와 러그, 책장, 계단 밑 수납
 * 2층: 침대와 협탁, 옷장과 상자, 책상, 러그, 계단 난간, 발코니 문
 *
 * 내부 좌표: u = 왼쪽→오른쪽(문 쪽에서 볼 때), v = 앞벽(0)→뒷벽(D-1), ly = 바닥(0)부터의 높이
 */
public class House extends Feature {
    private static final int FH = 5, TOP = FH * 2;

    private final Frame f;
    private final BlockState wallMat, job;
    private final boolean villager, spire;
    private final long seed;

    public House(CityLayout city, CityLayout.Lot lot, RandomSource rand, boolean villager) {
        this(new Frame(lot.centerX(), lot.centerZ(), lot.front(), rand.nextBoolean() ? 9 : 11, 9),
                rand.nextBoolean() ? SMOOTH_QUARTZ : CALCITE,
                JOB_BLOCKS[rand.nextInt(JOB_BLOCKS.length)],
                villager, rand.nextInt(3) == 0, rand.nextLong());
    }

    private House(Frame f, BlockState wallMat, BlockState job, boolean villager, boolean spire, long seed) {
        super(f.minX - 2, f.minZ - 2, f.maxX + 2, f.maxZ + 2);
        this.f = f;
        this.wallMat = wallMat;
        this.job = job;
        this.villager = villager;
        this.spire = spire;
        this.seed = seed;
    }

    @Override
    public void build(Placer p, CityLayout city) {
        final int b = groundY;
        final int W = f.width, D = f.depth;
        final int doorU = W / 2, stairU = W - 2;

        // ── 1. 벽과 바닥/천장 ──
        for (int u = 0; u < W; u++) {
            for (int v = 0; v < D; v++) {
                if (!p.contains(f.x(u, v), f.z(u, v))) continue;
                boolean edge = u == 0 || u == W - 1 || v == 0 || v == D - 1;
                if (edge) {
                    f.set(p, b, u, 0, v, QUARTZ_BRICKS);
                    for (int ly = 1; ly <= TOP; ly++) f.set(p, b, u, ly, v, wallBlock(u, v, ly, W, D, doorU));
                } else {
                    f.set(p, b, u, 0, v, Deco.PLANKS);
                    for (int ly = 1; ly <= TOP; ly++) {
                        BlockState s = AIR;
                        if (ly == FH) s = (u == stairU && v >= 2 && v <= 5) ? AIR : Deco.PLANKS;
                        else if (ly == TOP) s = (u == doorU && v == D / 2) ? LIGHT : SMOOTH_QUARTZ;
                        f.set(p, b, u, ly, v, s);
                    }
                }
            }
        }

        // ── 2. 계단 (오른쪽 벽을 따라 앞→뒤로 오름) + 2층 난간 + 계단 밑 수납 ──
        for (int k = 1; k <= FH; k++) f.set(p, b, stairU, k, 1 + k, Deco.woodStairs(f.back));
        for (int v = 2; v <= 5; v++) f.set(p, b, stairU - 1, FH + 1, v, Deco.FENCE);
        f.set(p, b, stairU, FH + 1, 1, Deco.FENCE);
        f.set(p, b, stairU, 1, 5, Deco.chest(f.left));
        f.set(p, b, stairU, 1, 6, Deco.BARREL);
        f.set(p, b, stairU, 2, 6, Deco.BARREL);

        // ── 3. 1층 ──
        for (int v = 1; v <= 3; v++) f.set(p, b, doorU, 1, v, Deco.CARPET_MINT);          // 현관 러그
        f.set(p, b, 1, 1, 1, Deco.WATER_CAULDRON);                                         // 주방
        f.set(p, b, 1, 1, 2, job);
        f.set(p, b, 1, 1, 3, Deco.BARREL);
        f.set(p, b, 1, 2, 3, Deco.pot((int) seed));
        f.set(p, b, 2, 1, 1, Deco.CRAFTING);
        for (int u = 2; u <= 3; u++) {                                                     // 식탁
            Deco.table(p, f.x(u, 5), b + 1, f.z(u, 5));
            f.set(p, b, u, 1, 4, Deco.chair(f.front));
            f.set(p, b, u, 1, 6, Deco.chair(f.back));
        }
        f.set(p, b, 1, 1, 6, BOOKSHELF);                                                   // 거실
        f.set(p, b, 1, 2, 6, BOOKSHELF);
        f.set(p, b, 1, 3, 6, Deco.candles(3));
        f.set(p, b, 1, 1, 7, Deco.pot((int) (seed >> 8)));
        f.set(p, b, doorU, 1, 7, Deco.chair(f.back));                                      // 소파
        f.set(p, b, doorU + 1, 1, 7, Deco.chair(f.back));
        for (int u = doorU; u <= doorU + 1; u++) {
            for (int v = 5; v <= 6; v++) f.set(p, b, u, 1, v, Deco.CARPET_WHITE);
        }
        f.set(p, b, doorU, FH - 1, 2, Deco.hangingLantern());
        f.set(p, b, 3, FH - 1, 5, Deco.hangingLantern());

        // ── 4. 2층 ──
        int y2 = FH + 1;
        f.set(p, b, 1, y2, 6, Palette.bed(f.back, false));                                 // 침대
        f.set(p, b, 1, y2, 7, Palette.bed(f.back, true));
        f.set(p, b, 2, y2, 7, Deco.BARREL);                                                // 협탁
        f.set(p, b, 2, y2 + 1, 7, Deco.candles(2));
        f.set(p, b, 1, y2, 1, BOOKSHELF);                                                  // 옷장
        f.set(p, b, 1, y2 + 1, 1, BOOKSHELF);
        f.set(p, b, 1, y2, 2, Deco.chest(f.right));
        Deco.table(p, f.x(2, 1), b + y2, f.z(2, 1));                                       // 책상
        f.set(p, b, 2, y2, 2, Deco.chair(f.back));
        for (int u = 2; u <= 4; u++) {                                                     // 러그
            for (int v = 3; v <= 5; v++) f.set(p, b, u, y2, v, Deco.CARPET_MINT);
        }
        f.set(p, b, W - 4, y2, 7, BOOKSHELF);
        f.set(p, b, W - 4, y2 + 1, 7, BOOKSHELF);
        f.set(p, b, doorU, y2, 7, Deco.pot((int) (seed >> 16)));
        f.set(p, b, 3, TOP - 1, 4, Deco.hangingLantern());

        // ── 5. 현관 + 2층 발코니 ──
        for (int u = doorU - 2; u <= doorU + 2; u++) {
            for (int v = -2; v <= -1; v++) {
                f.set(p, b, u, 0, v, SMOOTH_QUARTZ);
                f.set(p, b, u, FH, v, SMOOTH_QUARTZ);
            }
            f.set(p, b, u, FH + 1, -2, GLASS_PANE);
        }
        f.set(p, b, doorU - 2, FH + 1, -1, GLASS_PANE);
        f.set(p, b, doorU + 2, FH + 1, -1, GLASS_PANE);
        for (int ly = 1; ly < FH; ly++) {
            BlockState pillar = ly == FH - 1 ? CHISELED_QUARTZ : QUARTZ_PILLAR;
            f.set(p, b, doorU - 2, ly, -2, pillar);
            f.set(p, b, doorU + 2, ly, -2, pillar);
        }
        f.set(p, b, doorU - 1, FH + 1, -1, Deco.pot((int) (seed >> 24)));
        f.set(p, b, doorU + 1, FH + 1, -1, Deco.pot((int) (seed >> 28)));
        f.set(p, b, doorU, FH - 1, -1, Deco.hangingLantern());

        // ── 6. 창 밑 화분(1층) + 창턱(2층) ──
        BlockState sill = Deco.topSlab(Blocks.SMOOTH_QUARTZ_SLAB);
        for (int u = 0; u < W; u++) {
            for (int v = 0; v < D; v++) {
                boolean edgeU = u == 0 || u == W - 1, edgeV = v == 0 || v == D - 1;
                if (!(edgeU ^ edgeV)) continue;
                int along = edgeV ? u : v, len = edgeV ? W : D;
                if (!isWindow(along, len)) continue;
                if (v == 0 && Math.abs(u - doorU) <= 2) continue;
                int ou = u == 0 ? -1 : u == W - 1 ? W : u;
                int ov = v == 0 ? -1 : v == D - 1 ? D : v;
                f.set(p, b, ou, 1, ov, Deco.pot(Shapes.hash(u, 7, v, seed)));
                f.set(p, b, ou, FH + 1, ov, sill);
            }
        }

        // ── 7. 처마 밑 내물림(뒤집힌 석영 계단) ──
        for (int u = -1; u <= W; u++) {
            for (int v = -1; v <= D; v++) {
                if (u >= 0 && u < W && v >= 0 && v < D) continue;
                Direction toWall = v == -1 ? f.back : v == D ? f.front : u == -1 ? f.right : f.left;
                f.set(p, b, u, TOP, v, Deco.corbel(toWall));
            }
        }

        // ── 8. 지붕 + 꼭대기 장식 ──
        int top = Shapes.hipRoof(p, f.minX - 1, f.minZ - 1, f.maxX + 1, f.maxZ + 1, b + TOP + 1);
        int cx = f.centerX(), cz = f.centerZ();
        if (spire) {
            for (int i = 1; i <= 3; i++) p.set(cx, top + i, cz, i == 3 ? MINT_CHISELED : MINT_CUT);
            p.set(cx, top + 4, cz, END_ROD);
        } else {
            p.set(cx, top + 1, cz, MINT_CHISELED);
            p.set(cx, top + 2, cz, END_ROD);
        }

        // ── 9. 굴뚝 (뒷벽 바깥) ──
        int chimneyTop = top - b + 2;
        for (int ly = 1; ly < chimneyTop; ly++) f.set(p, b, 1, ly, D, QUARTZ_BRICKS);
        f.set(p, b, 1, chimneyTop, D, Deco.CAMPFIRE);
    }

    private BlockState wallBlock(int u, int v, int ly, int W, int D, int doorU) {
        boolean edgeU = u == 0 || u == W - 1, edgeV = v == 0 || v == D - 1;
        if (edgeU && edgeV) return (ly == FH || ly == TOP) ? CHISELED_QUARTZ : QUARTZ_PILLAR;
        if (v == 0 && u == doorU) {
            switch (ly) {
                case 1, 2 -> { return Palette.door(f.back, ly == 2); }
                case 3, 8 -> { return GLASS_PANE; }
                case 4, 9 -> { return CHISELED_QUARTZ; }
                case 6, 7 -> { return Palette.door(f.back, ly == 7); }
                default -> { }
            }
        }
        if (ly == 1) return QUARTZ_BRICKS;
        if (ly == FH) return MINT_CUT;
        if (ly == TOP) return SMOOTH_QUARTZ;
        int along = edgeV ? u : v, len = edgeV ? W : D;
        if (isWindow(along, len)) {
            int local = ly % FH;
            if (local == 2 || local == 3) return GLASS_PANE;
            if (local == 4) return CHISELED_QUARTZ;
        }
        return wallMat;
    }

    private static boolean isWindow(int along, int len) {
        return along % 2 == 0 && along >= 2 && along <= len - 3;
    }

    @Override
    public void spawn(ServerLevel level, CityLayout city) {
        if (!villager) return;
        int u = f.width / 2;
        Shapes.spawnVillager(level, f.x(u, 3) + 0.5, groundY + 1, f.z(u, 3) + 0.5);
    }
}
