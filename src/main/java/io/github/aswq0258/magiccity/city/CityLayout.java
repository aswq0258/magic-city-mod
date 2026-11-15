package io.github.aswq0258.magiccity.city;

import io.github.aswq0258.magiccity.build.Placer;
import io.github.aswq0258.magiccity.city.feature.Cathedral;
import io.github.aswq0258.magiccity.city.feature.CityGate;
import io.github.aswq0258.magiccity.city.feature.Feature;
import io.github.aswq0258.magiccity.city.feature.FountainSquare;
import io.github.aswq0258.magiccity.city.feature.Garden;
import io.github.aswq0258.magiccity.city.feature.House;
import io.github.aswq0258.magiccity.city.feature.MarketPlaza;
import io.github.aswq0258.magiccity.city.feature.MarketSquare;
import io.github.aswq0258.magiccity.city.feature.Mausoleum;
import io.github.aswq0258.magiccity.city.feature.Palace;
import io.github.aswq0258.magiccity.city.feature.RoleBuilding;
import io.github.aswq0258.magiccity.city.feature.RoleBuilding.Role;
import io.github.aswq0258.magiccity.city.feature.TrainingGround;
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
 * 성곽 대도시 설계도 (기본 지름 2000)
 *
 *                       북문
 *          ┌──────────────┼──────────────┐
 *          │ 모험가 길드  │ 병영·훈련장 │ 군수창고 │ 귀족 저택 │
 *          │ ·여관 구역   │             │          │           │
 *     서문 ├ 공방 ────── [ 해자 + 중앙 왕성 (지름 500) ] ────── 귀족 저택 ┤ 동문
 *          │ 대장간       │                                  │ 대성당     │
 *          │ 창고·마구간  │ 시장 광장   │ 상점·여관 │ 주거지 │
 *          └──────────────┼──────────────┘
 *                       남문
 *  - 둥근 사각형 성벽(높이 30) + 해자, 동서남북 성문에서 왕성까지 폭 15 대로, 80칸 간격 골목길
 *  - 성벽 밖: 농지 띠, 북동쪽에 공동묘지(묘비·영묘·영혼 랜턴)
 */
public final class CityLayout {
    public static final double AVENUE_HALF = 7.5;
    public static final int WALL_HEIGHT = 30, CORNER_R = 220, STREET = 80, BELT = 100;
    private static final int MOAT_IN = 256, MOAT_OUT = 272, CRING_OUT = 288, DISTRICT_IN = 292;

    public enum District { BARRACKS, ARMORY, NOBLE, CATHEDRAL, SHOPS, RESIDENTIAL, MARKET, BLACKSMITH, WAREHOUSE, WORKSHOP, GUILD }

    public final int cx, cz, base, half, extent, radius;
    public final long seed;

    private final List<Feature> features = new ArrayList<>();
    private final Map<Long, List<Feature>> byChunk = new HashMap<>();
    private int villagerBudget = 300;

    /** @param diameter 성벽 바깥 지름 */
    public CityLayout(int cx, int cz, int base, int diameter, long seed) {
        this.cx = cx;
        this.cz = cz;
        this.base = base;
        this.half = diameter / 2;
        this.extent = half + BELT;
        this.radius = extent;
        this.seed = seed;

        RandomSource rand = RandomSource.create(seed);
        features.add(new Palace(this));
        for (int k = 0; k < 4; k++) features.add(new CityGate(this, k));
        addWallTowers();
        addCemetery();
        packCity(rand);

        for (Feature f : features) {
            f.groundY = base;
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

    public record Lot(int centerX, int centerZ, int minX, int minZ, int maxX, int maxZ, Direction front) {
        public int size() {
            return maxX - minX + 1;
        }
    }

    // ───────────────────────── 형태 ─────────────────────────

    /** 둥근 사각형까지의 부호 거리 (안쪽이 음수) */
    public static double roundedSD(double ax, double az, double h, double r) {
        double qx = ax - (h - r), qz = az - (h - r);
        double outside = Math.hypot(Math.max(qx, 0), Math.max(qz, 0));
        double inside = Math.min(Math.max(qx, qz), 0);
        return outside + inside - r;
    }

    /** 성벽 바깥면 기준 부호 거리 */
    private double wallSD(int dx, int dz) {
        return roundedSD(Math.abs(dx), Math.abs(dz), half, CORNER_R);
    }

    public District districtAt(int dx, int dz) {
        double a = Math.abs(dx), c = Math.abs(dz);
        boolean north = dz < 0, east = dx > 0;
        boolean vBand = a < c * 0.5, hBand = c < a * 0.5;
        if (north && !east) return vBand ? District.BARRACKS : hBand ? District.WORKSHOP : District.GUILD;
        if (north) return vBand ? District.ARMORY : District.NOBLE;
        if (east) return vBand ? District.SHOPS : hBand ? District.CATHEDRAL : District.RESIDENTIAL;
        return vBand ? District.MARKET : hBand ? District.BLACKSMITH : District.WAREHOUSE;
    }

    private boolean isStreet(int dx, int dz) {
        return (Math.floorMod(dx - 38, STREET) < 5 && Math.abs(dx) > 7) || (Math.floorMod(dz - 38, STREET) < 5 && Math.abs(dz) > 7);
    }

    private boolean inCemetery(int dx, int dz, double sd) {
        return sd > 18 && dx > 300 && dz < -300;
    }

    public Direction facingToCenter(int x, int z) {
        int dx = cx - x, dz = cz - z;
        if (Math.abs(dx) >= Math.abs(dz)) return dx > 0 ? Direction.EAST : Direction.WEST;
        return dz > 0 ? Direction.SOUTH : Direction.NORTH;
    }

    // ───────────────────────── 성벽 탑 · 묘지 ─────────────────────────

    private void addWallTowers() {
        int straight = half - CORNER_R, line = half - 5;
        for (int t = -straight; t <= straight; t += 140) {
            if (Math.abs(t) < 60) continue;
            features.add(new WallTower(this, cx + line, cz + t));
            features.add(new WallTower(this, cx - line, cz + t));
            features.add(new WallTower(this, cx + t, cz + line));
            features.add(new WallTower(this, cx + t, cz - line));
        }
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                for (double a : new double[]{Math.PI / 12, Math.PI / 4, Math.PI * 5 / 12}) {
                    int x = cx + sx * (straight + (int) Math.round(Math.cos(a) * (CORNER_R - 5)));
                    int z = cz + sz * (straight + (int) Math.round(Math.sin(a) * (CORNER_R - 5)));
                    features.add(new WallTower(this, x, z));
                }
            }
        }
    }

    private void addCemetery() {
        for (int dx = 7; dx < extent; dx += 14) {
            for (int dz = -7; dz > -extent; dz -= 14) {
                int i = dx / 14, j = -dz / 14;
                if ((i + 2 * j) % 5 != 0) continue;
                boolean ok = true;
                for (int ox = -5; ox <= 5 && ok; ox += 10) {
                    for (int oz = -5; oz <= 5 && ok; oz += 10) {
                        double sd = wallSD(dx + ox, dz + oz);
                        if (!inCemetery(dx + ox, dz + oz, sd) || sd > BELT - 3 || sd < 22) ok = false;
                    }
                }
                if (ok) features.add(new Mausoleum(cx + dx, cz + dz, Direction.SOUTH));
            }
        }
    }

    // ───────────────────────── 구획 배치 ─────────────────────────

    /** 도로 선: {중심 좌표, 반폭} (대로 0, 골목 ±40, ±120, ...) */
    private List<int[]> lines() {
        List<int[]> l = new ArrayList<>();
        for (int p = -half; p <= half; p++) {
            if (p == 0) l.add(new int[]{0, 7});
            else if (Math.floorMod(p - 40, STREET) == 0) l.add(new int[]{p, 2});
        }
        return l;
    }

    private int lotSize(District d) {
        return switch (d) {
            case BARRACKS, ARMORY, WAREHOUSE -> 27;
            case NOBLE -> 37;
            case GUILD, SHOPS -> 21;
            default -> 17;
        };
    }

    private boolean lotValid(int x0, int z0, int x1, int z1) {
        int[][] corners = {{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}};
        for (int[] c : corners) {
            int dx = c[0] - cx, dz = c[1] - cz;
            if (Math.max(Math.abs(dx), Math.abs(dz)) < DISTRICT_IN) return false;
            if (wallSD(dx, dz) > -27) return false;
        }
        return true;
    }

    private void packCity(RandomSource rand) {
        List<int[]> lines = lines();
        int[][] special = {{580, 160}, {-160, 560}, {-560, -560}};   // 대성당, 시장 광장, 모험가 길드
        for (int i = 0; i + 1 < lines.size(); i++) {
            int bx0 = lines.get(i)[0] + lines.get(i)[1] + 2, bx1 = lines.get(i + 1)[0] - lines.get(i + 1)[1] - 2;
            for (int j = 0; j + 1 < lines.size(); j++) {
                int bz0 = lines.get(j)[0] + lines.get(j)[1] + 2, bz1 = lines.get(j + 1)[0] - lines.get(j + 1)[1] - 2;
                if (bx1 - bx0 < 12 || bz1 - bz0 < 12) continue;
                int mx = (bx0 + bx1) / 2, mz = (bz0 + bz1) / 2;
                District d = districtAt(mx, mz);
                int X0 = cx + bx0, Z0 = cz + bz0, X1 = cx + bx1, Z1 = cz + bz1;
                boolean whole = lotValid(X0, Z0, X1, Z1);

                if (whole && bx1 - bx0 >= 60 && bz1 - bz0 >= 60) {
                    if (inside(special[0], bx0, bz0, bx1, bz1)) {
                        features.add(new Cathedral(X0, Z0, X1, Z1, Direction.NORTH, rand.nextLong()));
                        continue;
                    }
                    if (inside(special[1], bx0, bz0, bx1, bz1)) {
                        features.add(new MarketPlaza(X0, Z0, X1, Z1, rand.nextLong()));
                        continue;
                    }
                    if (inside(special[2], bx0, bz0, bx1, bz1)) {
                        int gx = cx + mx, gz = cz + mz;
                        features.add(new RoleBuilding(new Lot(gx, gz, gx - 18, gz - 18, gx + 18, gz + 18, Direction.SOUTH), Role.GUILD, rand, true));
                        continue;
                    }
                }
                if (whole && d == District.BARRACKS && Math.floorMod(i * 7 + j * 3, 4) == 0) {
                    features.add(new TrainingGround(X0, Z0, X1, Z1, rand.nextLong()));
                    continue;
                }

                int L = lotSize(d), gap = 3;
                int nx = (bx1 - bx0 + 1 + gap) / (L + gap), nz = (bz1 - bz0 + 1 + gap) / (L + gap);
                if (nx <= 0 || nz <= 0) continue;
                int ox = bx0 + (bx1 - bx0 + 1 - (nx * L + (nx - 1) * gap)) / 2;
                int oz = bz0 + (bz1 - bz0 + 1 - (nz * L + (nz - 1) * gap)) / 2;
                for (int a = 0; a < nx; a++) {
                    for (int c = 0; c < nz; c++) {
                        int lx0 = cx + ox + a * (L + gap), lz0 = cz + oz + c * (L + gap);
                        int lx1 = lx0 + L - 1, lz1 = lz0 + L - 1;
                        if (!lotValid(lx0, lz0, lx1, lz1)) continue;
                        int lcx = (lx0 + lx1) / 2, lcz = (lz0 + lz1) / 2;
                        int dW = lcx - X0, dE = X1 - lcx, dN = lcz - Z0, dS = Z1 - lcz;
                        int min = Math.min(Math.min(dW, dE), Math.min(dN, dS));
                        Direction front = min == dN ? Direction.NORTH : min == dS ? Direction.SOUTH : min == dW ? Direction.WEST : Direction.EAST;
                        Lot lot = new Lot(lcx, lcz, lx0, lz0, lx1, lz1, front);
                        Feature f = create(districtAt(lcx - cx, lcz - cz), lot, rand);
                        if (f != null) features.add(f);
                    }
                }
            }
        }
    }

    private static boolean inside(int[] pt, int x0, int z0, int x1, int z1) {
        return pt[0] >= x0 && pt[0] <= x1 && pt[1] >= z0 && pt[1] <= z1;
    }

    private boolean villager(float chance, RandomSource rand) {
        if (villagerBudget > 0 && rand.nextFloat() < chance) {
            villagerBudget--;
            return true;
        }
        return false;
    }

    private Feature role(Lot lot, Role r, RandomSource rand) {
        return new RoleBuilding(lot, r, rand, villager(0.2F, rand));
    }

    private Feature create(District d, Lot lot, RandomSource rand) {
        float x = rand.nextFloat();
        return switch (d) {
            case BARRACKS -> x < 0.6F ? role(lot, Role.BARRACKS, rand) : x < 0.8F ? role(lot, Role.ARMORY, rand) : new House(this, lot, rand, villager(0.15F, rand));
            case ARMORY -> x < 0.5F ? role(lot, Role.ARMORY, rand) : x < 0.8F ? role(lot, Role.WAREHOUSE, rand) : role(lot, Role.BARRACKS, rand);
            case WAREHOUSE -> x < 0.45F ? role(lot, Role.WAREHOUSE, rand) : x < 0.85F ? role(lot, Role.STABLE, rand) : role(lot, Role.ARMORY, rand);
            case NOBLE -> x < 0.7F ? role(lot, Role.MANSION, rand) : x < 0.85F ? new Garden(this, lot, rand) : new FountainSquare(this, lot);
            case GUILD -> x < 0.5F ? role(lot, Role.INN, rand) : x < 0.7F ? role(lot, Role.SHOP, rand) : x < 0.9F ? new House(this, lot, rand, villager(0.15F, rand)) : new MarketSquare(this, lot, rand, false);
            case SHOPS -> x < 0.5F ? role(lot, Role.SHOP, rand) : x < 0.8F ? role(lot, Role.INN, rand) : x < 0.9F ? new MarketSquare(this, lot, rand, villager(0.3F, rand)) : new House(this, lot, rand, false);
            case MARKET -> x < 0.4F ? new MarketSquare(this, lot, rand, villager(0.3F, rand)) : x < 0.75F ? role(lot, Role.SHOP, rand) : new House(this, lot, rand, villager(0.15F, rand));
            case BLACKSMITH -> x < 0.55F ? role(lot, Role.SMITHY, rand) : x < 0.8F ? role(lot, Role.WORKSHOP, rand) : new House(this, lot, rand, villager(0.15F, rand));
            case WORKSHOP -> x < 0.6F ? role(lot, Role.WORKSHOP, rand) : x < 0.75F ? role(lot, Role.SMITHY, rand) : new House(this, lot, rand, villager(0.15F, rand));
            case CATHEDRAL -> x < 0.6F ? new House(this, lot, rand, villager(0.15F, rand)) : x < 0.85F ? new Garden(this, lot, rand) : new FountainSquare(this, lot);
            case RESIDENTIAL -> x < 0.72F ? new House(this, lot, rand, villager(0.15F, rand)) : x < 0.82F ? new Garden(this, lot, rand)
                    : x < 0.9F ? new FountainSquare(this, lot) : new MarketSquare(this, lot, rand, villager(0.3F, rand));
        };
    }

    private boolean isOccupied(int x, int z) {
        List<Feature> list = byChunk.get(ChunkPos.asLong(x >> 4, z >> 4));
        if (list == null) return false;
        for (Feature f : list) if (f.occupies(x, z)) return true;
        return false;
    }

    // ───────────────────────── 기둥 단위 지형 ─────────────────────────

    public void buildColumn(Placer p, int x, int z) {
        final int dx = x - cx, dz = z - cz, adx = Math.abs(dx), adz = Math.abs(dz);
        final double sd = wallSD(dx, dz);
        if (sd > BELT) return;
        final int m = Math.max(adx, adz);
        final int sy = base;
        boolean nsAve = adx <= 7, ewAve = adz <= 7;
        boolean avenue = (nsAve || ewAve) && m > Palace.OUT;

        p.clearAbove(x, z, sy + 1);
        BlockState surface = null;
        int lowest = sy;
        boolean lampOk = false;

        if (m <= Palace.OUT) {
            surface = GRASS;                                         // 왕성이 위에 지음
        } else if (avenue) {
            int lat = nsAve ? dx : dz, along = nsAve ? dz : dx, alat = Math.abs(lat);
            surface = alat == 7 ? MINT_CUT : lat == 0 ? CALCITE : SMOOTH_QUARTZ;
            boolean bridge = (m > MOAT_IN && m <= MOAT_OUT) || (sd > 2 && sd <= 16);
            if (bridge) {
                for (int i = 1; i <= 4; i++) p.set(x, sy - i, z, WATER);
                p.set(x, sy - 5, z, CALCITE);
                lowest = sy - 5;
                if (alat == 7) {
                    surface = QUARTZ_BRICKS;
                    p.set(x, sy + 1, z, GLASS_PANE);
                }
            } else if (alat == 6 && Math.floorMod(along, 16) == 8 && (sd < -12 || sd > 18)) {
                Shapes.lampPost(p, x, z, sy);
            }
        } else if (m <= MOAT_IN) {
            surface = GRASS;
        } else if (m <= MOAT_OUT) {                                 // 왕성 해자
            if (m == MOAT_IN + 1 || m == MOAT_OUT) {
                surface = SMOOTH_QUARTZ;
                for (int i = 1; i <= 5; i++) p.set(x, sy - i, z, QUARTZ_BRICKS);
                if (m == MOAT_OUT) p.set(x, sy + 1, z, GLASS_PANE);
            } else {
                p.set(x, sy, z, AIR);
                for (int i = 1; i <= 4; i++) p.set(x, sy - i, z, WATER);
                p.set(x, sy - 5, z, CALCITE);
            }
            lowest = sy - 5;
        } else if (m <= CRING_OUT) {                                // 왕성 순환도로
            surface = (m == MOAT_OUT + 1 || m == CRING_OUT) ? MINT_CUT : SMOOTH_QUARTZ;
        } else if (sd <= -24) {                                     // 구역
            if (isStreet(dx, dz)) {
                boolean xLine = Math.floorMod(dx - 38, STREET) < 5 && adx > 7;
                int off = Math.floorMod(xLine ? dx - 38 : dz - 38, STREET);
                surface = (off == 0 || off == 4) ? CALCITE : SMOOTH_QUARTZ;
            } else {
                surface = (Math.floorMod(dx, 9) == 0 || Math.floorMod(dz, 9) == 0) ? SMOOTH_QUARTZ : WHITE_CONCRETE;
                lampOk = true;
            }
        } else if (sd <= -10) {                                     // 성벽 안 순환도로
            surface = (sd > -11 || sd <= -23) ? MINT_CUT : SMOOTH_QUARTZ;
        } else if (sd <= 0) {                                       // 성벽
            wallColumn(p, x, z, dx, dz, sd);
        } else if (sd <= 2) {
            surface = GRASS;
        } else if (sd <= 16) {                                      // 바깥 해자
            if (sd <= 4 || sd > 14) {
                surface = SMOOTH_QUARTZ;
                for (int i = 1; i <= 5; i++) p.set(x, sy - i, z, QUARTZ_BRICKS);
            } else {
                p.set(x, sy, z, AIR);
                for (int i = 1; i <= 4; i++) p.set(x, sy - i, z, WATER);
                p.set(x, sy - 5, z, CALCITE);
            }
            lowest = sy - 5;
        } else if (inCemetery(dx, dz, sd)) {
            cemetery(p, x, z, dx, dz, sd);
        } else {                                                    // 농지
            int u = Math.floorMod(dx, 13), v = Math.floorMod(dz, 13);
            if (u == 12 || v == 12) {
                surface = GRASS;
                if (u == 12 && v == 12) Shapes.lampPost(p, x, z, sy);
            } else if (v == 6) {
                p.set(x, sy, z, WATER);
                p.set(x, sy - 1, z, CALCITE);
                lowest = sy - 1;
            } else {
                surface = Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7);
                int plot = Shapes.hash(Math.floorDiv(dx, 13), 0, Math.floorDiv(dz, 13), seed) % 3;
                CropBlock crop = (CropBlock) (plot == 0 ? Blocks.WHEAT : plot == 1 ? Blocks.CARROTS : Blocks.POTATOES);
                p.set(x, sy + 1, z, crop.getStateForAge(Shapes.hash(x, 0, z, seed) % 8));
            }
        }

        if (surface != null) p.set(x, sy, z, surface);
        p.foundation(x, z, lowest - 1, 48);

        if (lampOk && Math.floorMod(dx, 10) == 5 && Math.floorMod(dz, 10) == 5 && !isOccupied(x, z)) {
            Shapes.lampPost(p, x, z, sy);
        }
    }

    private void wallColumn(Placer p, int x, int z, int dx, int dz, double sd) {
        int b = base, H = WALL_HEIGHT;
        boolean outer = sd > -1;
        int idx = Math.floorMod(dx + dz, 12);
        for (int y = b; y <= b + H; y++) {
            int ly = y - b;
            BlockState s = QUARTZ_BRICKS;
            if (ly == H) s = SMOOTH_QUARTZ;
            else if (ly == H - 4) s = MINT_CUT;
            else if (outer && ly == 16 && idx == 0) s = GLASS;
            p.set(x, y, z, s);
        }
        if (outer) {
            if (Math.floorMod(dx + dz, 4) < 2) p.set(x, b + H + 1, z, CALCITE);
        } else if (sd <= -9) {
            p.set(x, b + H + 1, z, SMOOTH_QUARTZ_SLAB);
        }
    }

    /** 공동묘지: 방해석 길, 묘비(조각 석영 + 촛불), 꽃 핀 무덤, 영혼 랜턴 기둥, 낮은 담 */
    private void cemetery(Placer p, int x, int z, int dx, int dz, double sd) {
        int b = base;
        int u = Math.floorMod(dx, 14), v = Math.floorMod(dz, 14);
        boolean path = u <= 1 || v <= 1;
        boolean border = sd <= 19.5 || sd > BELT - 1 || dx <= 301 || dz >= -301;
        int h = Shapes.hash(x, 21, z, seed);
        p.set(x, b, z, path ? CALCITE : GRASS);
        if (border) {
            if (!path) {
                p.set(x, b + 1, z, CALCITE);
                p.set(x, b + 2, z, SMOOTH_QUARTZ_SLAB);
            }
            return;
        }
        if (u == 0 && v == 0) {
            p.set(x, b + 1, z, QUARTZ_PILLAR);
            p.set(x, b + 2, z, QUARTZ_PILLAR);
            p.set(x, b + 3, z, Blocks.SOUL_LANTERN.defaultBlockState());
            return;
        }
        if (path) return;
        if (u >= 3 && u <= 12 && u % 3 == 0) {
            if (v % 5 == 3) {
                p.set(x, b + 1, z, CHISELED_QUARTZ);
                if (h % 3 == 0) p.set(x, b + 2, z, Deco.candles(1 + h % 4));
            } else if (v % 5 == 4) {
                p.set(x, b, z, Blocks.COARSE_DIRT.defaultBlockState());
                if (h % 2 == 0) p.set(x, b + 1, z, (h >> 2) % 2 == 0 ? Blocks.LILY_OF_THE_VALLEY.defaultBlockState() : Blocks.WHITE_TULIP.defaultBlockState());
            }
        }
    }
}
