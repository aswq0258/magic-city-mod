package io.github.aswq0258.magiccity.city;

import io.github.aswq0258.magiccity.build.Placer;
import io.github.aswq0258.magiccity.city.feature.AcademyHall;
import io.github.aswq0258.magiccity.city.feature.Castle;
import io.github.aswq0258.magiccity.city.feature.Feature;
import io.github.aswq0258.magiccity.city.feature.FountainSquare;
import io.github.aswq0258.magiccity.city.feature.Garden;
import io.github.aswq0258.magiccity.city.feature.Gatehouse;
import io.github.aswq0258.magiccity.city.feature.House;
import io.github.aswq0258.magiccity.city.feature.MageTower;
import io.github.aswq0258.magiccity.city.feature.MarketSquare;
import io.github.aswq0258.magiccity.city.feature.WallTower;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.github.aswq0258.magiccity.city.Palette.*;

/**
 * 계단식 성도시 설계도 (반지름 600 기준). 중심으로 갈수록 지면이 높아집니다.
 *
 *  [4단 +32] 성역 고원     0 ~ 140  중앙 대성(~112) · 성 광장 · 운하(114~122) · 산책로 · 옹벽 난간
 *  [3단 +20] 학원 구역   140 ~ 220  경사로 · 순환도로 · 마법탑 · 학원 강당 · 정원
 *  [2단 +10] 상업 구역   220 ~ 330  경사로 · 순환도로 · 주민 집 · 시장 · 분수
 *  [1단  0 ] 외곽 주거   330 ~ 554  경사로 · 순환도로 · 주민 집 · 시장 · 정원
 *            외성벽      562 ~ 570  높이 26, 성문 8개, 탑 8개
 *            농지 · 해자 · 잔디  570 ~ 600
 *  대로 8개 (폭 11)가 중심에서 성문까지 이어지고, 단 사이에는 계단 경사로로 올라갑니다.
 */
public final class CityLayout {
    public static final double AVENUE_HALF = 5.5;
    public static final int AVENUE_COUNT = 8;
    public static final int WALL_HEIGHT = 26;
    /** 단 높이: 0=외곽, 1=상업, 2=학원, 3=성역 */
    public static final int[] TIER_H = {0, 10, 20, 32};
    public static final double[] AV_COS = new double[AVENUE_COUNT];
    public static final double[] AV_SIN = new double[AVENUE_COUNT];

    static {
        for (int k = 0; k < AVENUE_COUNT; k++) {
            double a = k * Math.PI * 2 / AVENUE_COUNT;
            AV_COS[k] = Math.cos(a);
            AV_SIN[k] = Math.sin(a);
        }
    }

    public final int cx, cz, base, radius;
    public final long seed;

    public final double rCastle = 112, rCastleRim = 114, rCanalOut = 122, rCanalRim = 124;
    /** 단 경계: [0]=1단|2단, [1]=2단|3단, [2]=3단|4단 */
    public final double[] bd = new double[3];
    private static final int[] RAMP = {10, 10, 12};
    public final double rRing3In, rWallIn, rWallOut, rBeltOut, rMoatOut;

    private final List<Feature> features = new ArrayList<>();
    private final Map<Long, List<Feature>> byChunk = new HashMap<>();
    private int villagerBudget = 200;

    public CityLayout(int cx, int cz, int base, int radius, long seed) {
        this.cx = cx;
        this.cz = cz;
        this.base = base;
        this.radius = radius;
        this.seed = seed;

        bd[2] = 140;
        bd[1] = 140 + (radius - 140) * 0.174;
        bd[0] = 140 + (radius - 140) * 0.413;
        this.rWallIn = radius - 38;
        this.rWallOut = radius - 30;
        this.rBeltOut = radius - 20;
        this.rMoatOut = radius - 6;
        this.rRing3In = rWallIn - 8;

        RandomSource rand = RandomSource.create(seed);
        features.add(new Castle(this));
        double rWallMid = (rWallIn + rWallOut) / 2.0;
        for (int k = 0; k < AVENUE_COUNT; k++) {
            features.add(new Gatehouse(this, k, rWallMid));
            features.add(new WallTower(this, (k + 0.5) * Math.PI * 2 / AVENUE_COUNT, rWallMid));
        }
        packZone(rand, ringRoadOut(2) + 2, bd[1] - 6, 23, 5, this::academyLot);
        packZone(rand, ringRoadOut(1) + 2, bd[0] - 6, 13, 3, this::residentialLot);
        packZone(rand, ringRoadOut(0) + 2, rRing3In - 2, 13, 3, this::residentialLot);

        for (Feature f : features) {
            double r = Math.hypot(((f.minX + f.maxX) >> 1) - cx, ((f.minZ + f.maxZ) >> 1) - cz);
            f.groundY = base + TIER_H[tierAt(r)];
            for (int x = (f.minX - 1) >> 4; x <= (f.maxX + 1) >> 4; x++) {
                for (int z = (f.minZ - 1) >> 4; z <= (f.maxZ + 1) >> 4; z++) {
                    byChunk.computeIfAbsent(ChunkPos.asLong(x, z), k -> new ArrayList<>()).add(f);
                }
            }
        }
    }

    public int featureCount() {
        return features.size();
    }

    public List<Feature> featuresIn(ChunkPos cp) {
        return byChunk.getOrDefault(cp.toLong(), Collections.emptyList());
    }

    /** 0=외곽 ~ 3=성역 */
    public int tierAt(double r) {
        if (r < bd[2]) return 3;
        if (r < bd[1]) return 2;
        if (r < bd[0]) return 1;
        return 0;
    }

    /** 경계 i 바깥쪽 순환도로가 끝나는 반지름 */
    private double ringRoadOut(int i) {
        return bd[i] + RAMP[i] + 8;
    }

    // ───────────────────────── 구획 배치 ─────────────────────────

    public record Lot(int centerX, int centerZ, int minX, int minZ, int maxX, int maxZ, Direction front) {
        public int size() {
            return maxX - minX + 1;
        }
    }

    @FunctionalInterface
    private interface LotFactory {
        Feature create(Lot lot, RandomSource rand);
    }

    private void packZone(RandomSource rand, double r0, double r1, int size, int gap, LotFactory factory) {
        int half = size / 2;
        double step = (size + gap) * 0.8;
        for (double r = r0 + half; r <= r1 - half; r += step) {
            int n = (int) Math.floor(2 * Math.PI * r / (size + gap));
            if (n <= 0) continue;
            double offset = rand.nextDouble() * 2 * Math.PI / n;
            for (int i = 0; i < n; i++) {
                double a = offset + i * 2 * Math.PI / n;
                int x = cx + (int) Math.round(Math.cos(a) * r);
                int z = cz + (int) Math.round(Math.sin(a) * r);
                int x0 = x - half, x1 = x + half, z0 = z - half, z1 = z + half;
                if (!cornersWithin(x0, z0, x1, z1, r0, r1)) continue;
                if (nearAvenue(x0, z0, x1, z1, AVENUE_HALF + 2)) continue;
                if (overlaps(x0 - gap, z0 - gap, x1 + gap, z1 + gap)) continue;
                Feature f = factory.create(new Lot(x, z, x0, z0, x1, z1, facingToCenter(x, z)), rand);
                if (f != null) features.add(f);
            }
        }
    }

    private Feature academyLot(Lot lot, RandomSource rand) {
        float roll = rand.nextFloat();
        if (roll < 0.40F) return new MageTower(this, lot, rand);
        if (roll < 0.80F) return new AcademyHall(this, lot, rand);
        return new Garden(this, lot, rand);
    }

    private Feature residentialLot(Lot lot, RandomSource rand) {
        float roll = rand.nextFloat();
        if (roll < 0.66F) {
            boolean villager = villagerBudget > 0 && rand.nextFloat() < 0.25F;
            if (villager) villagerBudget--;
            return new House(this, lot, rand, villager);
        }
        if (roll < 0.80F) {
            boolean villagers = villagerBudget >= 2 && rand.nextFloat() < 0.5F;
            if (villagers) villagerBudget -= 2;
            return new MarketSquare(this, lot, rand, villagers);
        }
        if (roll < 0.92F) return new Garden(this, lot, rand);
        return new FountainSquare(this, lot);
    }

    private boolean cornersWithin(int x0, int z0, int x1, int z1, double r0, double r1) {
        int[] xs = {x0, x1}, zs = {z0, z1};
        for (int x : xs) {
            for (int z : zs) {
                double r = Math.hypot(x - cx, z - cz);
                if (r < r0 || r > r1) return false;
            }
        }
        return true;
    }

    private boolean nearAvenue(int x0, int z0, int x1, int z1, double margin) {
        double mx = (x0 + x1) / 2.0 - cx, mz = (z0 + z1) / 2.0 - cz;
        int[] xs = {x0, x1}, zs = {z0, z1};
        for (int k = 0; k < AVENUE_COUNT; k++) {
            if (mx * AV_COS[k] + mz * AV_SIN[k] <= 0) continue;
            double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
            for (int x : xs) {
                for (int z : zs) {
                    double d = (x - cx) * AV_SIN[k] - (z - cz) * AV_COS[k];
                    min = Math.min(min, d);
                    max = Math.max(max, d);
                }
            }
            double dist = (min < 0 && max > 0) ? 0 : Math.min(Math.abs(min), Math.abs(max));
            if (dist < margin) return true;
        }
        return false;
    }

    private boolean overlaps(int x0, int z0, int x1, int z1) {
        for (Feature f : features) {
            if (x0 <= f.maxX && x1 >= f.minX && z0 <= f.maxZ && z1 >= f.minZ) return true;
        }
        return false;
    }

    public Direction facingToCenter(int x, int z) {
        int dx = cx - x, dz = cz - z;
        if (Math.abs(dx) >= Math.abs(dz)) return dx > 0 ? Direction.EAST : Direction.WEST;
        return dz > 0 ? Direction.SOUTH : Direction.NORTH;
    }

    public double avenueDist(double dx, double dz) {
        double best = Double.MAX_VALUE;
        for (int k = 0; k < AVENUE_COUNT; k++) {
            if (dx * AV_COS[k] + dz * AV_SIN[k] <= 0) continue;
            best = Math.min(best, Math.abs(dx * AV_SIN[k] - dz * AV_COS[k]));
        }
        return best;
    }

    private boolean isOccupied(int x, int z) {
        List<Feature> list = byChunk.get(ChunkPos.asLong(x >> 4, z >> 4));
        if (list == null) return false;
        for (Feature f : list) {
            if (f.occupies(x, z)) return true;
        }
        return false;
    }

    // ───────────────────────── 기둥 단위 지형/도로/옹벽/성벽 ─────────────────────────

    public void buildColumn(Placer p, int x, int z) {
        double dx = x - cx, dz = z - cz;
        double r = Math.sqrt(dx * dx + dz * dz);
        if (r > radius + 0.5) return;

        double ad = avenueDist(dx, dz);
        boolean avenue = ad <= AVENUE_HALF && r >= rCastle - 2;
        double theta = Shapes.angle(dx, dz);
        int tier = tierAt(r);
        int h = TIER_H[tier];

        // 경사로 (대로가 단 경계를 넘는 곳)
        int lowerH = h;
        boolean ramp = false;
        for (int i = 0; i < 3; i++) {
            if (avenue && r > bd[i] && r <= bd[i] + RAMP[i]) {
                int upper = TIER_H[i + 1];
                h = Math.max(lowerH, upper - (int) Math.ceil(r - bd[i]));
                ramp = h > lowerH;
            }
        }
        final int sy = base + h;
        p.clearAbove(x, z, sy + 1);

        BlockState surface = null;
        int lowest = sy;
        boolean lampOk = false;

        if (avenue) {
            boolean edge = ad > AVENUE_HALF - 1;
            surface = edge ? MINT_CUT : SMOOTH_QUARTZ;
            boolean overCanal = r >= rCastleRim && r < rCanalOut;
            boolean overMoat = r >= rBeltOut + 1 && r < rMoatOut - 1;
            if (overCanal || overMoat) {
                int depth = overCanal ? 2 : 4;
                for (int i = 1; i <= depth; i++) p.set(x, sy - i, z, WATER);
                p.set(x, sy - depth - 1, z, CALCITE);
                lowest = sy - depth - 1;
                surface = edge ? QUARTZ_BRICKS : SMOOTH_QUARTZ;
                if (edge) p.set(x, sy + 1, z, GLASS_PANE);
            }
            if (ramp) {
                surface = edge ? QUARTZ_BRICKS : Deco.quartzStairs(Deco.toward(-dx, -dz));
                for (int y = sy - 1; y >= base + lowerH; y--) p.set(x, y, z, QUARTZ_BRICKS);
                lowest = base + lowerH;
                if (edge) p.set(x, sy + 1, z, GLASS_PANE);
            }
        } else if (r < rCastle) {
            surface = castlePlaza(r, ad);
            lampOk = r > 84 && ad > 2;
        } else if (r < rCastleRim) {
            surface = CALCITE;
            for (int i = 1; i <= 3; i++) p.set(x, sy - i, z, QUARTZ_BRICKS);
            lowest = sy - 3;
            if (r >= rCastleRim - 1) p.set(x, sy + 1, z, GLASS_PANE);
        } else if (r < rCanalOut) {
            p.set(x, sy, z, AIR);
            p.set(x, sy - 1, z, WATER);
            p.set(x, sy - 2, z, WATER);
            p.set(x, sy - 3, z, CALCITE);
            lowest = sy - 3;
        } else if (r < rCanalRim) {
            surface = QUARTZ_BRICKS;
            for (int i = 1; i <= 3; i++) p.set(x, sy - i, z, QUARTZ_BRICKS);
            lowest = sy - 3;
            if (r < rCanalOut + 1) p.set(x, sy + 1, z, GLASS_PANE);
        } else if (tier > 0 && r >= bd[tier - 1] - 2) {
            // 옹벽 위 난간 + 옹벽 면
            surface = SMOOTH_QUARTZ;
            if (r >= bd[tier - 1] - 1) p.set(x, sy + 1, z, GLASS_PANE);
            int bottom = base + TIER_H[tier - 1];
            int band = (sy + bottom) / 2;
            for (int y = sy - 1; y >= bottom; y--) p.set(x, y, z, y == band ? MINT_CUT : QUARTZ_BRICKS);
            lowest = bottom;
        } else if (onRingRoad(r)) {
            surface = ringRoadEdge(r) ? MINT_CUT : SMOOTH_QUARTZ;
        } else if (tier > 0 || r < rRing3In) {
            surface = pavement(x, z);
            lampOk = true;
        } else if (r < rWallIn - 1) {
            double mid = (rRing3In + rWallIn - 1) / 2.0;
            surface = Math.abs(r - mid) > 2.5 ? MINT_CUT : SMOOTH_QUARTZ;
        } else if (r < rWallIn) {
            surface = pavement(x, z);
        } else if (r < rWallOut) {
            buildWallColumn(p, x, z, r, theta);
        } else if (r < rBeltOut) {
            double farmIn = rWallOut + 2, farmOut = rBeltOut - 1, channel = (farmIn + farmOut) / 2.0;
            int seg = (int) Math.floor(theta * channel / 12.0);
            if (r >= farmIn && r < farmOut && seg % 4 != 0) {
                if (Math.abs(r - channel) < 0.5) {
                    p.set(x, sy, z, WATER);
                    p.set(x, sy - 1, z, CALCITE);
                    lowest = sy - 1;
                } else {
                    surface = Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7);
                    CropBlock crop = (CropBlock) (seg % 3 == 0 ? Blocks.WHEAT : seg % 3 == 1 ? Blocks.CARROTS : Blocks.POTATOES);
                    p.set(x, sy + 1, z, crop.getStateForAge(Shapes.hash(x, 0, z, seed) % 8));
                }
            } else {
                surface = GRASS;
                lampOk = true;
            }
        } else if (r < rMoatOut) {
            if (r < rBeltOut + 1 || r >= rMoatOut - 1) {
                surface = SMOOTH_QUARTZ;
                for (int i = 1; i <= 5; i++) p.set(x, sy - i, z, QUARTZ_BRICKS);
            } else {
                p.set(x, sy, z, AIR);
                for (int i = 1; i <= 4; i++) p.set(x, sy - i, z, WATER);
                p.set(x, sy - 5, z, CALCITE);
            }
            lowest = sy - 5;
        } else {
            surface = GRASS;
            lampOk = true;
        }

        if (surface != null) p.set(x, sy, z, surface);
        p.foundation(x, z, lowest - 1, 80);

        if (lampOk && Math.floorMod(x - cx, 10) == 0 && Math.floorMod(z - cz, 10) == 0 && !isOccupied(x, z)) {
            Shapes.lampPost(p, x, z, sy);
        }
    }

    private boolean onRingRoad(double r) {
        for (int i = 0; i < 3; i++) {
            double r0 = bd[i] + RAMP[i] + 1;
            if (r >= r0 && r < r0 + 7) return true;
        }
        return false;
    }

    private boolean ringRoadEdge(double r) {
        for (int i = 0; i < 3; i++) {
            double r0 = bd[i] + RAMP[i] + 1;
            if (r >= r0 && r < r0 + 7) return r < r0 + 1 || r >= r0 + 6;
        }
        return false;
    }

    private BlockState castlePlaza(double r, double ad) {
        for (int ring = 80; ring <= 110; ring += 10) {
            if (Math.abs(r - ring) < 0.5) return MINT_CUT;
            if (Math.abs(r - (ring + 5)) < 0.5) return CALCITE;
        }
        if (ad < 0.6 && r > 75) return MINT_CUT;
        return SMOOTH_QUARTZ;
    }

    private BlockState pavement(int x, int z) {
        if (Math.floorMod(x - cx, 9) == 0 || Math.floorMod(z - cz, 9) == 0) return SMOOTH_QUARTZ;
        return WHITE_CONCRETE;
    }

    /** 외성벽 한 기둥: 석영 벽돌 + 민트 띠 + 하늘색 창 + 총안 */
    private void buildWallColumn(Placer p, int x, int z, double r, double theta) {
        int b = base, H = WALL_HEIGHT;
        boolean outer = r >= rWallOut - 1;
        int arc = (int) Math.floor(theta * rWallOut);
        for (int y = b; y <= b + H; y++) {
            int ly = y - b;
            BlockState s = QUARTZ_BRICKS;
            if (ly == H) s = SMOOTH_QUARTZ;
            else if (ly == H - 4) s = MINT_CUT;
            else if (outer && ly == 14 && arc % 6 == 0) s = GLASS;
            p.set(x, y, z, s);
        }
        if (outer) {
            if ((arc / 2) % 2 == 0) p.set(x, b + H + 1, z, CALCITE);
        } else if (r < rWallIn + 1) {
            p.set(x, b + H + 1, z, SMOOTH_QUARTZ_SLAB);
        }
    }
}
