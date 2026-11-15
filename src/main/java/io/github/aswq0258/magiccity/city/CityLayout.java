package io.github.aswq0258.magiccity.city;

import io.github.aswq0258.magiccity.build.Placer;
import io.github.aswq0258.magiccity.city.feature.AcademyHall;
import io.github.aswq0258.magiccity.city.feature.BarrierTower;
import io.github.aswq0258.magiccity.city.feature.Cathedral;
import io.github.aswq0258.magiccity.city.feature.Citadel;
import io.github.aswq0258.magiccity.city.feature.Feature;
import io.github.aswq0258.magiccity.city.feature.FloatingIsland;
import io.github.aswq0258.magiccity.city.feature.FountainSquare;
import io.github.aswq0258.magiccity.city.feature.Garden;
import io.github.aswq0258.magiccity.city.feature.GuildYard;
import io.github.aswq0258.magiccity.city.feature.House;
import io.github.aswq0258.magiccity.city.feature.Keep;
import io.github.aswq0258.magiccity.city.feature.MageTower;
import io.github.aswq0258.magiccity.city.feature.MarketPlaza;
import io.github.aswq0258.magiccity.city.feature.MarketSquare;
import io.github.aswq0258.magiccity.city.feature.RingWallWalk;
import io.github.aswq0258.magiccity.city.feature.RoleBuilding;
import io.github.aswq0258.magiccity.city.feature.RoleBuilding.Role;
import io.github.aswq0258.magiccity.city.feature.RoyalHall;
import io.github.aswq0258.magiccity.city.feature.Sewer;
import io.github.aswq0258.magiccity.city.feature.Slum;
import io.github.aswq0258.magiccity.city.feature.Temple;
import io.github.aswq0258.magiccity.city.feature.TrainingGround;
import io.github.aswq0258.magiccity.city.feature.WallTower;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static io.github.aswq0258.magiccity.city.Palette.*;

/**
 * 방사형 이중 성곽 도시 (외성벽 지름 약 520)
 *
 *  중심에서 바깥으로:
 *    r <  72  왕립 성채 (Citadel: 3겹 원형 성벽과 계단식 테라스, 꼭대기 궁전)
 *    72 ~ 82  왕성 순환로 (폭 10)
 *    82 ~ 147 내성 구역: 행정청, 귀족 저택, 대성당, 별빛의 신전, 왕실 마구간, 정원 (넉넉하게)
 *   148 ~ 154 내성벽 (높이 16, 성문 4)
 *   154 ~ 163 내성 바깥 순환로
 *   163 ~ 207 중간 시가지: 북서 군사, 북 모험가 길드, 북동 마법 아카데미, 남동 시장, 남서 장인
 *   208 ~ 215 중간 순환로
 *   215 ~ 252 외곽 시가지: 불규칙한 주거·작은 가게, 동문 창고, 남서 슬럼가, 결계탑
 *   253 ~ 259 외성벽 (높이 24, 성문 4, 탑, 계단·초소)
 *   262 ~ 275 해자 (성문 다리)
 *
 *  도로 3단계: 성문→왕성 대로 4개(폭 13) / 순환로 3개(폭 7~10) + 대각 방사로 4개(폭 7) / 굽은 골목(폭 3~5)
 *  좌표: 도시 중심 기준 du(동 +), dn(북 +). 마인크래프트 월드 x = cx + du, z = cz - dn.
 *  도면 좌표 (u, v) = (260 + du, 260 + dn) 도 하수도·길드 마당에서 씁니다.
 */
public final class CityLayout {
    public static final int R_CASTLE = 72, R_CRING = 82, R_INNER = 147, R_IWALL_IN = 148, R_IWALL_OUT = 154, R_IRING = 163,
            R_MID = 207, R_MRING = 215, R_OUTER = 252, R_WALL_IN = 253, R_WALL_OUT = 259, R_MOAT_IN = 262, R_MOAT_OUT = 275,
            R_APRON = 285, SLOPE = 24, WALL_H = 24, IWALL_H = 16, AVE = 6;
    /** 자동 지면 높이 조사 범위 (반지름) */
    public static final int SURVEY_R = R_WALL_OUT;
    /** 하수도 입구 (도면 좌표) */
    public static final int SLUM_SHAFT_U = 127, SLUM_SHAFT_V = 71, CASTLE_SHAFT_U = 224, CASTLE_SHAFT_V = 245;
    private static final int[] LANES = {15, 75, 105, 165, 195, 255, 285, 345};

    public enum Style { NOBLE, MILITARY, MAGIC, CRAFT, COMMERCE, RESIDENT }

    public final int cx, cz, base, radius;
    public final long seed;

    private final List<Feature> features = new ArrayList<>();
    private final Map<Long, List<Feature>> byChunk = new HashMap<>();
    private final List<int[]> taken = new ArrayList<>();      // 놓인 건물 범위 {du0, dn0, du1, dn1}
    private int villagerBudget = 160;

    public CityLayout(int cx, int cz, int base, long seed) {
        this.cx = cx;
        this.cz = cz;
        this.base = base;
        this.radius = R_APRON + SLOPE + 18;
        this.seed = seed;

        RandomSource rand = RandomSource.create(seed);
        features.add(new ZoneDecor());
        addCitadel(rand);
        addWalls(rand);
        addFacilities(rand);
        addHouses(rand);
        addUnderworldAndSky(rand);

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

    // ───────────────────────── 좌표 ─────────────────────────

    /** 도면 u → 월드 x */
    public int wx(int u) {
        return cx + (u - 260);
    }

    /** 도면 v → 월드 z */
    public int wz(int v) {
        return cz - (v - 260);
    }

    public int u(int x) {
        return x - cx + 260;
    }

    public int v(int z) {
        return cz - z + 260;
    }

    /** 중심 기준 (du, dn) 사각형 → 월드 Lot */
    private Lot lotRel(int du0, int dn0, int du1, int dn1, Direction front) {
        int x0 = cx + Math.min(du0, du1), x1 = cx + Math.max(du0, du1);
        int z0 = cz - Math.max(dn0, dn1), z1 = cz - Math.min(dn0, dn1);
        return new Lot((x0 + x1) >> 1, (z0 + z1) >> 1, x0, z0, x1, z1, front);
    }

    private Lot lotAt(int du, int dn, int L, Direction front) {
        return lotRel(du - L / 2, dn - L / 2, du - L / 2 + L - 1, dn - L / 2 + L - 1, front);
    }

    public Direction facingToCenter(int x, int z) {
        int dx = cx - x, dz = cz - z;
        if (Math.abs(dx) >= Math.abs(dz)) return dx > 0 ? Direction.EAST : Direction.WEST;
        return dz > 0 ? Direction.SOUTH : Direction.NORTH;
    }

    private static int[] polar(double deg, double r) {
        double a = Math.toRadians(deg);
        return new int[]{(int) Math.round(Math.cos(a) * r), (int) Math.round(Math.sin(a) * r)};
    }

    private void take(int du0, int dn0, int du1, int dn1) {
        taken.add(new int[]{Math.min(du0, du1), Math.min(dn0, dn1), Math.max(du0, du1), Math.max(dn0, dn1)});
    }

    // ───────────────────────── 왕성 ─────────────────────────

    private void addCitadel(RandomSource rand) {
        features.add(new Citadel(this, rand.nextLong()));
        int L = 28;
        // 꼭대기 대지 (r < 43, +28): 북서로 치우친 궁전, 서쪽 왕좌의 전당, 동쪽 보물고·근위대, 남동 왕실 정원 (Citadel)
        features.add(new Keep(cx - 28, cz - 31, cx + 17, cz + 8, rand.nextLong(), L));
        features.add(new RoyalHall(RoyalHall.Kind.THRONE, cx - 32, cz + 10, cx - 6, cz + 26, Direction.SOUTH, L, rand.nextLong()).noBackDoor());
        features.add(new RoyalHall(RoyalHall.Kind.VAULT, cx + 21, cz - 10, cx + 35, cz + 6, Direction.WEST, L, rand.nextLong()));
        features.add(new RoyalHall(RoyalHall.Kind.GUARD, cx + 20, cz - 26, cx + 33, cz - 13, Direction.SOUTH, L, rand.nextLong()));
    }

    // ───────────────────────── 성벽 ─────────────────────────

    private void addWalls(RandomSource rand) {
        features.add(new RingWallWalk(this, R_WALL_IN, R_WALL_OUT, WALL_H, rand.nextLong()));
        // 외성벽 탑: 성문 양옆 큰 탑 + 사이사이 탑 (간격을 조금씩 다르게)
        for (int g = 0; g < 4; g++) {
            for (int s = -1; s <= 1; s += 2) {
                int[] q = polar(g * 90 + s * 3.4, 256);
                features.add(new WallTower(this, cx + q[0], cz - q[1], 8, 40));
            }
        }
        double[] mids = {22, 38, 58, 68, 112, 130, 148, 160, 202, 218, 236, 250, 290, 310, 326, 340};
        for (int i = 0; i < mids.length; i++) {
            int[] q = polar(mids[i], 256);
            features.add(new WallTower(this, cx + q[0], cz - q[1], i % 3 == 0 ? 7 : 5.5, i % 3 == 0 ? 36 : 30));
        }
        // 내성벽 탑
        for (int k = 0; k < 8; k++) {
            int[] q = polar(22.5 + k * 45, 151);
            features.add(new WallTower(this, cx + q[0], cz - q[1], 5, 26));
        }
        for (int g = 0; g < 4; g++) {
            for (int s = -1; s <= 1; s += 2) {
                int[] q = polar(g * 90 + s * 4.5, 151);
                features.add(new WallTower(this, cx + q[0], cz - q[1], 4.5, 24));
            }
        }
    }

    // ───────────────────────── 주요 시설 (모두 한 번씩) ─────────────────────────

    private boolean villager(float chance, RandomSource rand) {
        if (villagerBudget > 0 && rand.nextFloat() < chance) {
            villagerBudget--;
            return true;
        }
        return false;
    }

    private void place(int du, int dn, int L, Function<Lot, Feature> make) {
        Lot lot = lotAt(du, dn, L, facingToCenter(cx + du, cz - dn));
        features.add(make.apply(lot));
        take(du - L / 2, dn - L / 2, du - L / 2 + L - 1, dn - L / 2 + L - 1);
    }

    private void addFacilities(RandomSource rand) {
        // ── 내성 구역 (귀족·행정·종교)
        features.add(new RoyalHall(RoyalHall.Kind.COUNCIL, cx - 34, cz - 120, cx - 8, cz - 100, Direction.SOUTH, 0, rand.nextLong()));
        take(-40, 94, -2, 126);                                                                    // 행정청
        place(86, 72, 25, l -> new RoleBuilding(l, Role.STABLE, rand, false));                     // 왕실 마구간
        features.add(new Temple(cx + 97, cz - 54, cx + 127, cz - 8, Direction.SOUTH, rand.nextLong()));
        take(97, 8, 127, 54);                                                                      // 별빛의 신전
        features.add(new Cathedral(cx + 54, cz + 64, cx + 82, cz + 120, Direction.NORTH, rand.nextLong()));
        take(54, -120, 82, -64);                                                                   // 대성당 (2단계에서 새 디자인)
        mansion(-100, 57, Direction.EAST, rand);
        mansion(-101, -47, Direction.EAST, rand);
        mansion(-66, -94, Direction.NORTH, rand);
        mansion(-125, 20, Direction.EAST, rand);
        mansion(25, -125, Direction.NORTH, rand);
        features.add(new Garden(this, lotRel(-45, -127, -16, -98, Direction.NORTH), rand));        // 귀족 정원
        take(-45, -127, -16, -98);
        features.add(new FountainSquare(this, lotRel(33, 86, 59, 112, Direction.SOUTH)));         // 내성 분수 광장
        take(33, 86, 59, 112);

        // ── 중간 시가지
        place(-79, 169, 25, l -> new RoleBuilding(l, Role.BARRACKS, rand, false));                 // 기사단 병영
        features.add(new TrainingGround(cx - 182, cz - 89, cx - 153, cz - 66, rand.nextLong()));
        take(-182, 66, -153, 89);
        place(-32, 183, 23, l -> new RoleBuilding(l, Role.ARMORY, rand, false));                   // 무기고
        features.add(new RoleBuilding(lotRel(13, 173, 43, 193, Direction.SOUTH), Role.GUILD, rand, true));   // 모험가 길드
        take(11, 171, 45, 195);
        features.add(new GuildYard(this, 315, 420, 340, 446, rand.nextLong()));
        take(55, 160, 80, 186);
        place(161, 93, 25, l -> new AcademyHall(this, l, rand));                                   // 마법 아카데미 (2단계에서 새 디자인)
        place(99, 158, 23, l -> new MageTower(this, l, rand));
        features.add(new MarketPlaza(cx + 77, cz + 145, cx + 109, cz + 177, rand.nextLong()));      // 중앙 시장
        take(77, -177, 109, -145);
        place(161, -93, 15, l -> new RoleBuilding(l, Role.SHOP, rand, true));                      // 상인 길드 상점
        place(180, -48, 19, l -> new RoleBuilding(l, Role.INN, rand, true));                       // 동문 여관
        place(-161, -93, 17, l -> new RoleBuilding(l, Role.SMITHY, rand, true));                   // 대장간
        place(-93, -161, 17, l -> new RoleBuilding(l, Role.WORKSHOP, rand, true));                 // 공방
        features.add(new MarketSquare(this, lotRel(11, -199, 37, -173, Direction.NORTH), rand, villager(0.6F, rand)));   // 남문 광장
        take(11, -199, 37, -173);

        // ── 외곽 시가지
        place(229, -40, 25, l -> new RoleBuilding(l, Role.WAREHOUSE, rand, false));                // 동문 교역 창고
        features.add(new Slum(cx - 146, cz + 178, cx - 120, cz + 200, wx(SLUM_SHAFT_U), wz(SLUM_SHAFT_V), rand.nextLong()));
        take(-147, -201, -119, -177);
        for (int deg : new int[]{30, 95, 150, 210, 300, 335}) {                                    // 결계탑
            int[] q = polar(deg, 232);
            features.add(new BarrierTower(cx + q[0], cz - q[1], rand.nextLong()));
            take(q[0] - 6, q[1] - 6, q[0] + 6, q[1] + 6);
        }
    }

    private void mansion(int du, int dn, Direction front, RandomSource rand) {
        boolean ew = front.getAxis() == Direction.Axis.X;
        int hw = ew ? 7 : 12, hd = ew ? 12 : 7;
        features.add(new RoleBuilding(lotRel(du - hw, dn - hd, du + hw, dn + hd, front), Role.MANSION, rand, villager(0.5F, rand)));
        int g = 14;
        take(du - hw - (front == Direction.WEST ? g : 1), dn - hd - (front == Direction.SOUTH ? g : 1),
                du + hw + (front == Direction.EAST ? g : 1), dn + hd + (front == Direction.NORTH ? g : 1));
    }

    // ───────────────────────── 불규칙한 주거 ─────────────────────────

    private void addHouses(RandomSource rand) {
        scatter(rand, R_CRING + 4, R_INNER - 2, 14, 3000, 3);        // 내성: 넉넉하게
        scatter(rand, R_IRING + 1, R_MID - 1, 70, 12000, 2);         // 중간 시가지
        scatter(rand, R_MRING + 1, R_OUTER - 1, 150, 20000, 1);      // 외곽: 촘촘하게
    }

    /** 고리 영역 [r0, r1] 에 집을 흩뿌림: 도로·다른 건물과 겹치지 않고, 정면은 가장 가까운 길 쪽 */
    private void scatter(RandomSource rand, int r0, int r1, int count, int attempts, int gap) {
        int placed = 0;
        for (int t = 0; t < attempts && placed < count; t++) {
            double a = rand.nextDouble() * Math.PI * 2, r = r0 + rand.nextDouble() * (r1 - r0);
            int du = (int) Math.round(Math.cos(a) * r), dn = (int) Math.round(Math.sin(a) * r);
            int w = 11 + rand.nextInt(3), d = 11;
            int du0 = du - w / 2, dn0 = dn - d / 2, du1 = du0 + w - 1, dn1 = dn0 + d - 1;
            if (!rectFree(du0, dn0, du1, dn1, r0, r1, gap)) continue;
            Direction front = nearestRoad(du, dn);
            features.add(new House(this, lotRel(du0, dn0, du1, dn1, front), rand, villager(0.25F, rand)));
            take(du0, dn0, du1, dn1);
            placed++;
        }
    }

    private boolean rectFree(int du0, int dn0, int du1, int dn1, int r0, int r1, int gap) {
        for (int[] q : taken) {
            if (du0 - gap <= q[2] && du1 + gap >= q[0] && dn0 - gap <= q[3] && dn1 + gap >= q[1]) return false;
        }
        for (int u = du0; u <= du1; u += 2) {
            for (int n = dn0; n <= dn1; n += 2) {
                double r = Math.hypot(u, n);
                if (r < r0 || r > r1 || isRoad(u, n)) return false;
            }
        }
        return !isRoad(du1, dn1) && Math.hypot(du1, dn1) <= r1 && Math.hypot(du1, dn1) >= r0;
    }

    private static Direction nearestRoad(int du, int dn) {
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        Direction[] names = {Direction.EAST, Direction.WEST, Direction.NORTH, Direction.SOUTH};
        int best = 99;
        Direction pick = Math.abs(du) >= Math.abs(dn) ? (du > 0 ? Direction.WEST : Direction.EAST) : (dn > 0 ? Direction.SOUTH : Direction.NORTH);
        for (int i = 0; i < 4; i++) {
            for (int k = 7; k <= 18; k++) {
                if (isRoad(du + dirs[i][0] * k, dn + dirs[i][1] * k)) {
                    if (k < best) {
                        best = k;
                        pick = names[i];
                    }
                    break;
                }
            }
        }
        return pick;
    }

    // ───────────────────────── 하수도 · 하늘 ─────────────────────────

    private void addUnderworldAndSky(RandomSource rand) {
        features.add(new Sewer(this, SLUM_SHAFT_U, SLUM_SHAFT_V, CASTLE_SHAFT_U, CASTLE_SHAFT_V, 28, rand.nextLong()));
        int[][] islands = {{-38, 60, 92, 7}, {52, -50, 76, 5}, {120, 180, 58, 6}, {-190, 40, 50, 5}, {170, -150, 54, 5}};
        for (int[] i : islands) features.add(new FloatingIsland(cx + i[0], cz - i[1], i[2], i[3], rand.nextLong()));
    }

    // ───────────────────────── 조회 ─────────────────────────

    /** 건물(장식 제외)이 이 칸을 차지하는지 */
    public boolean isOccupied(int x, int z) {
        List<Feature> list = byChunk.get(ChunkPos.asLong(x >> 4, z >> 4));
        if (list == null) return false;
        for (Feature f : list) if (!(f instanceof ZoneDecor) && f.occupies(x, z)) return true;
        return false;
    }

    private boolean occupiedNear(int x, int z, int r) {
        return isOccupied(x - r, z - r) || isOccupied(x + r, z - r) || isOccupied(x - r, z + r) || isOccupied(x + r, z + r) || isOccupied(x, z);
    }

    private static double angleDeg(int du, int dn) {
        double a = Math.toDegrees(Math.atan2(dn, du));
        return a < 0 ? a + 360 : a;
    }

    /** 성문→왕성 대로 (동서남북) */
    private static boolean isAvenue(int du, int dn) {
        double r = Math.hypot(du, dn);
        return r >= R_CASTLE && r <= R_APRON && (Math.abs(du) <= AVE || Math.abs(dn) <= AVE);
    }

    public static boolean isRoad(int du, int dn) {
        double r = Math.hypot(du, dn);
        if (r < R_CASTLE || r > R_APRON) return false;
        if (Math.abs(du) <= AVE || Math.abs(dn) <= AVE) return true;                     // 대로
        if (r > R_OUTER) return false;
        if (r <= R_CRING) return true;                                                     // 왕성 순환로
        if (r >= R_IWALL_OUT && r <= R_IRING) return true;                                 // 내성 바깥 순환로
        if (r > R_MID && r <= R_MRING) return true;                                        // 중간 순환로
        if (r >= R_IWALL_OUT) {
            for (int k = 0; k < 4; k++) {                                                  // 대각 방사로
                double a = Math.toRadians(45 + 90 * k);
                double lat = -du * Math.sin(a) + dn * Math.cos(a), along = du * Math.cos(a) + dn * Math.sin(a);
                if (along > 0 && Math.abs(lat) <= 3) return true;
            }
        }
        double th = Math.atan2(dn, du);
        if (r > R_IRING && r <= R_MID) {                                                   // 굽은 골목
            if (Math.abs(r - (186 + 3 * Math.sin(5 * th))) <= 2) return true;
        } else if (r > R_MRING) {
            if (Math.abs(r - (233 + 4 * Math.sin(7 * th + 1))) <= 2) return true;
        }
        if (r > R_IRING) {
            for (int deg : LANES) {                                                        // 굽은 방사 골목
                double a = Math.toRadians(deg);
                double along = du * Math.cos(a) + dn * Math.sin(a);
                if (along <= 0) continue;
                double lat = -du * Math.sin(a) + dn * Math.cos(a) + 3 * Math.sin(r / 11.0);
                if (Math.abs(lat) <= 1.5) return true;
            }
        }
        return false;
    }

    private static Style styleAt(int du, int dn) {
        double r = Math.hypot(du, dn);
        if (r <= R_INNER) return Style.NOBLE;
        if (r > R_MRING) return Style.RESIDENT;
        double a = angleDeg(du, dn);
        if (a < 90) return Style.MAGIC;
        if (a < 180) return Style.MILITARY;
        if (a < 270) return Style.CRAFT;
        return Style.COMMERCE;
    }

    // ───────────────────────── 기둥 단위 지형 ─────────────────────────

    public void buildColumn(Placer p, int x, int z) {
        final int du = x - cx, dn = cz - z, b = base;
        final double r = Math.hypot(du, dn);
        if (r > R_APRON) {
            slope(p, x, z, du, dn, (int) Math.ceil(r - R_APRON));
            return;
        }
        p.clearAbove(x, z, b + 1);
        BlockState surface;
        int lowest = b;
        boolean road = isRoad(du, dn);

        if (r < R_CASTLE) {
            castleGround(p, x, z, r);
            p.foundation(x, z, b - 1, 48);
            return;
        } else if (r >= R_IWALL_IN && r < R_IWALL_OUT) {
            wallColumn(p, x, z, du, dn, r, R_IWALL_IN, R_IWALL_OUT, IWALL_H);
            p.foundation(x, z, b - 1, 48);
            return;
        } else if (r >= R_WALL_IN - 0.5 && r <= R_WALL_OUT + 0.5) {
            wallColumn(p, x, z, du, dn, r, R_WALL_IN, R_WALL_OUT, WALL_H);
            p.foundation(x, z, b - 1, 48);
            return;
        } else if (r >= R_MOAT_IN && r <= R_MOAT_OUT) {
            if (isAvenue(du, dn)) {                                                         // 성문 다리
                surface = roadBlock(du, dn);
                if (Math.abs(du) == AVE || Math.abs(dn) == AVE) p.set(x, b + 1, z, SMOOTH_QUARTZ_SLAB);
                for (int i = 1; i <= 4; i++) p.set(x, b - i, z, i == 4 ? CALCITE : WATER);
                lowest = b - 5;
            } else if (r < R_MOAT_IN + 1 || r > R_MOAT_OUT - 1) {
                surface = SMOOTH_QUARTZ;
                for (int i = 1; i <= 5; i++) p.set(x, b - i, z, QUARTZ_BRICKS);
                if (r > R_MOAT_OUT - 1) p.set(x, b + 1, z, GLASS_PANE);
                lowest = b - 5;
            } else {
                surface = AIR;
                for (int i = 1; i <= 4; i++) p.set(x, b - i, z, WATER);
                p.set(x, b - 5, z, CALCITE);
                lowest = b - 5;
            }
        } else if (road) {
            surface = roadBlock(du, dn);
            if (surface == MINT_CUT && Shapes.hash(x, 19, z, seed) % 9 == 0 && !isOccupied(x, z)) lamp(p, x, z, b);
        } else if (r > R_WALL_OUT) {
            surface = (Math.abs(du) <= 5 || Math.abs(dn) <= 5) ? Blocks.DIRT_PATH.defaultBlockState() : GRASS;
        } else if (r > R_OUTER) {
            surface = GRASS;
        } else {
            surface = openGround(x, z, du, dn, r);
        }
        p.set(x, b, z, surface);
        p.foundation(x, z, lowest - 1, 48);
    }

    /** 길이 아닌 빈 땅: 내성은 잔디 정원, 중간은 광장 포장, 외곽은 잔디와 닳은 흙길 */
    private BlockState openGround(int x, int z, int du, int dn, double r) {
        int h = Shapes.hash(x, 18, z, seed);
        if (r <= R_INNER) return Math.abs(r - 115) < 1.0 ? CALCITE : GRASS;
        if (r <= R_MID + 1) return (Math.floorMod(du, 9) == 0 || Math.floorMod(dn, 9) == 0) ? SMOOTH_QUARTZ : (h % 7 == 0 ? CALCITE : WHITE_CONCRETE);
        return h % 5 == 0 ? Blocks.DIRT_PATH.defaultBlockState() : h % 11 == 0 ? Blocks.COARSE_DIRT.defaultBlockState() : GRASS;
    }

    private BlockState roadBlock(int du, int dn) {
        boolean edge = !isRoad(du + 1, dn) || !isRoad(du - 1, dn) || !isRoad(du, dn + 1) || !isRoad(du, dn - 1);
        if (edge) return MINT_CUT;
        if (isAvenue(du, dn)) return (du == 0 || dn == 0) ? CALCITE : SMOOTH_QUARTZ;
        double r = Math.hypot(du, dn);
        int h = Shapes.hash(du, 20, dn, seed) % 13;
        if (r > R_MRING && h == 0) return Blocks.DIRT_PATH.defaultBlockState();            // 외곽 골목은 닳은 흙
        return h < 3 ? CALCITE : h < 5 ? Blocks.POLISHED_DIORITE.defaultBlockState() : SMOOTH_QUARTZ;
    }

    /** 가로등: 흰 석영 가로등과 판타지 수정 가로등을 섞음 */
    private void lamp(Placer p, int x, int z, int b) {
        if (Shapes.hash(x, 14, z, seed) % 3 == 0) Fantasy.crystalLamp(p, x, z, b);
        else Shapes.lampPost(p, x, z, b);
    }

    /** 왕성 단 채우기 (성벽·계단·건물은 Citadel 이 지음) */
    private void castleGround(Placer p, int x, int z, double r) {
        int b = base;
        int top = Citadel.groundTop(r);
        p.set(x, b, z, QUARTZ_BRICKS);
        if (top <= 0) return;
        for (int y = b + 1; y < b + top; y++) p.set(x, y, z, QUARTZ_BRICKS);
        if (top == 28) {
            boolean inlay = Math.abs(r - 20) < 0.6 || Math.abs(r - 36) < 0.6;
            p.set(x, b + top, z, inlay ? Royal.GOLD : Royal.marble(x, z));
        } else {
            p.set(x, b + top, z, GRASS);
        }
    }

    /** 원형 성벽 한 기둥: 닳은 보행로, 바깥 총안, 안쪽 난간·랜턴, 대로 자리는 아치 성문 */
    private void wallColumn(Placer p, int x, int z, int du, int dn, double r, int rIn, int rOut, int H) {
        int b = base;
        int t = (int) Math.round(rOut - r);                                                  // 0 = 바깥면
        boolean outer = t <= 0, inner = r < rIn + 0.5;
        int lat = Math.min(Math.abs(du), Math.abs(dn));
        boolean gate = lat <= AVE;
        double open = gate ? (H - 12) * 0.5 + 8 + 3 * Math.sqrt(Math.max(0, 1 - Math.pow(lat / (AVE + 1.0), 2))) : -1;
        int arc = (int) Math.floor(Shapes.angle(du, dn) * r);
        p.set(x, b, z, gate ? (lat == AVE ? MINT_CUT : SMOOTH_QUARTZ) : QUARTZ_BRICKS);
        for (int ly = 1; ly <= H; ly++) {
            BlockState s = QUARTZ_BRICKS;
            if (ly == H) s = (!outer && !inner) ? walkway(x, z) : SMOOTH_QUARTZ;
            else if (ly == H - 4) s = MINT_CUT;
            else if (outer && ly >= H - 10 && ly <= H - 9 && Math.floorMod(arc, 8) == 0) s = GLASS;
            if (gate) {
                if (ly <= open) s = AIR;
                else if (ly <= open + 1) s = lat <= 1 ? MINT_CHISELED : CHISELED_QUARTZ;
            }
            p.set(x, b + ly, z, s);
        }
        if (outer && Math.floorMod(arc, 4) < 2) p.set(x, b + H + 1, z, CALCITE);
        if (inner) p.set(x, b + H + 1, z, Math.floorMod(arc, 8) == 4 ? Blocks.LANTERN.defaultBlockState() : SMOOTH_QUARTZ_SLAB);
        if (!outer && !inner && !gate) {                                                       // 순찰병이 두고 간 흔적
            int h = Shapes.hash(x, 16, z, seed) % 251;
            if (h == 0) p.set(x, b + H + 1, z, Deco.BARREL);
            else if (h == 1) p.set(x, b + H + 1, z, Blocks.HAY_BLOCK.defaultBlockState());
        }
    }

    /** 닳은 성벽 위 보행로 */
    private BlockState walkway(int x, int z) {
        int h = Shapes.hash(x, 17, z, seed) % 20;
        return h < 12 ? SMOOTH_QUARTZ : h < 16 ? CALCITE : h < 19 ? Blocks.POLISHED_DIORITE.defaultBlockState() : Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
    }

    /** 도시 바깥: 원래 지형까지 완만한 잔디 경사로, 대로 연장선은 흙길 */
    private void slope(Placer p, int x, int z, int du, int dn, int e) {
        boolean trail = Math.abs(du) <= 5 || Math.abs(dn) <= 5;
        BlockState path = Shapes.hash(x, 15, z, seed) % 4 == 0 ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.DIRT_PATH.defaultBlockState();
        if (e > SLOPE) {
            if (trail && e <= SLOPE + 16) {
                int top = p.naturalTop(x, z);
                p.clearAbove(x, z, top + 1);
                p.set(x, top, z, path);
            }
            return;
        }
        int top = p.naturalTop(x, z);
        double t = (double) e / (SLOPE + 1);
        t = t * t * (3 - 2 * t);
        int target = (int) Math.round(base + (top - base) * t);
        if (target < top) p.clearAbove(x, z, target + 1);
        else for (int y = top + 1; y < target; y++) p.set(x, y, z, Blocks.DIRT.defaultBlockState());
        if (trail) {
            p.set(x, target, z, path);
            return;
        }
        p.set(x, target, z, GRASS);
        if (Shapes.hash(x, 9, z, seed) % 17 == 0) p.set(x, target + 1, z, FLOWERS[Shapes.hash(x, 10, z, seed) % FLOWERS.length]);
    }

    // ───────────────────────── 빈 땅 장식 ─────────────────────────

    /**
     * 길·건물이 아닌 빈 땅(도시의 약 15~20%)을 채우는 장식: 구역별 나무·꽃·가로등·소품.
     * 다른 건물보다 먼저 지어지고, 건물이 차지한 칸은 피합니다.
     */
    private final class ZoneDecor extends Feature {
        private static final BlockState HAY = Blocks.HAY_BLOCK.defaultBlockState();

        ZoneDecor() {
            super(cx - R_OUTER, cz - R_OUTER, cx + R_OUTER, cz + R_OUTER);
        }

        @Override
        public boolean occupies(int x, int z) {
            return false;
        }

        private boolean open(int x, int z) {
            int du = x - cx, dn = cz - z;
            double r = Math.hypot(du, dn);
            if (r < R_CRING + 2 || r > R_OUTER - 1 || (r >= R_IWALL_IN - 2 && r <= R_IRING + 1)) return false;
            return !isRoad(du, dn) && !isOccupied(x, z);
        }

        private int grid(Style st) {
            return st == Style.NOBLE ? 10 : st == Style.RESIDENT ? 13 : 19;
        }

        @Override
        public void build(Placer p, CityLayout city) {
            final int b = groundY;
            // 나무: 청크 가장자리에 걸친 나무도 온전히 그리도록 주변 2칸까지 훑음
            for (int x = p.minX() - 2; x <= p.maxX() + 2; x++) {
                for (int z = p.minZ() - 2; z <= p.maxZ() + 2; z++) {
                    int du = x - cx, dn = cz - z;
                    Style st = styleAt(du, dn);
                    int g = grid(st);
                    if (Math.floorMod(du, g) != g / 2 || Math.floorMod(dn, g) != g / 2) continue;
                    if (st == Style.COMMERCE || st == Style.CRAFT) continue;
                    if (!open(x, z) || occupiedNear(x, z, 3) || isRoad(du + 2, dn) || isRoad(du - 2, dn) || isRoad(du, dn + 2) || isRoad(du, dn - 2)) continue;
                    Shapes.birchTree(p, x, z, b, 5 + Shapes.hash(x, 7, z, seed) % 3);
                }
            }
            p.forColumns(p.minX(), p.minZ(), p.maxX(), p.maxZ(), (x, z) -> {
                int du = x - cx, dn = cz - z;
                Style st = styleAt(du, dn);
                int g = grid(st);
                if (Math.floorMod(du, g) == g / 2 && Math.floorMod(dn, g) == g / 2) return;   // 나무 자리
                if (!open(x, z) || occupiedNear(x, z, 1)) return;
                int h = Shapes.hash(x, 13, z, seed) % 1000;
                if (Math.floorMod(du, 14) == 7 && Math.floorMod(dn, 14) == 0) {
                    lamp(p, x, z, b);
                    return;
                }
                BlockState prop = switch (st) {
                    case NOBLE -> h < 25 ? Royal.ROYAL_FLOWERS[h % 4] : h < 32 ? Royal.HEDGE : null;
                    case MILITARY -> h < 6 ? HAY : h < 10 ? Deco.BARREL : h < 12 ? Blocks.TARGET.defaultBlockState() : null;
                    case MAGIC -> h < 8 ? FLOWERS[h % FLOWERS.length] : h < 10 ? Fantasy.CLUSTER_UP : null;
                    case CRAFT -> h < 6 ? Deco.BARREL : h < 9 ? Blocks.STRIPPED_BIRCH_LOG.defaultBlockState() : h < 10 ? Blocks.GRINDSTONE.defaultBlockState() : null;
                    case COMMERCE -> h < 5 ? Deco.BARREL : h < 8 ? Deco.pot(h) : null;
                    case RESIDENT -> h < 30 ? FLOWERS[h % FLOWERS.length] : h < 33 ? Deco.BARREL : h < 35 ? HAY : null;
                };
                if (prop != null) p.set(x, b + 1, z, prop);
            });
        }
    }
}
