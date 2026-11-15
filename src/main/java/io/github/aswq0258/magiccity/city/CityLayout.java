package io.github.aswq0258.magiccity.city;

import io.github.aswq0258.magiccity.build.Placer;
import io.github.aswq0258.magiccity.city.feature.AcademyHall;
import io.github.aswq0258.magiccity.city.feature.Cathedral;
import io.github.aswq0258.magiccity.city.feature.Feature;
import io.github.aswq0258.magiccity.city.feature.FountainSquare;
import io.github.aswq0258.magiccity.city.feature.Garden;
import io.github.aswq0258.magiccity.city.feature.House;
import io.github.aswq0258.magiccity.city.feature.Keep;
import io.github.aswq0258.magiccity.city.feature.MageTower;
import io.github.aswq0258.magiccity.city.feature.MarketPlaza;
import io.github.aswq0258.magiccity.city.feature.MarketSquare;
import io.github.aswq0258.magiccity.city.feature.Mausoleum;
import io.github.aswq0258.magiccity.city.feature.RoleBuilding;
import io.github.aswq0258.magiccity.city.feature.RoleBuilding.Role;
import io.github.aswq0258.magiccity.city.feature.RoyalCastle;
import io.github.aswq0258.magiccity.city.feature.RoyalHall;
import io.github.aswq0258.magiccity.city.feature.TrainingGround;
import io.github.aswq0258.magiccity.city.feature.WallTower;
import io.github.aswq0258.magiccity.city.feature.FloatingIsland;
import io.github.aswq0258.magiccity.city.feature.GuildYard;
import io.github.aswq0258.magiccity.city.feature.Sewer;
import io.github.aswq0258.magiccity.city.feature.Slum;
import io.github.aswq0258.magiccity.city.feature.Temple;
import io.github.aswq0258.magiccity.city.feature.WallWalk;
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

import static io.github.aswq0258.magiccity.city.Palette.*;

/**
 * 판타지 성채 도시 — 사용자 520 도면에서 바깥 둘레 띠(P 구획)를 걷어내 약 395 x 395 로 줄인 버전
 *
 *  도면 좌표 (u, v): u = 도면 X (동쪽 +), v = 도면 Z (북쪽 +)
 *  월드 좌표: x = cx + (u - 260),  z = cz - (v - 260)   ← 마인크래프트의 북쪽은 -Z 이므로 뒤집음
 *
 *  - 외성벽(u/v 63~69, 451~457, 높이 24) + 모서리 큰 탑, 중간 탑, 성문 탑 / 동서남북 성문
 *  - 성벽 안 순환로(70~81, 439~450), 왕성 순환로(165~176, 345~356), 성문 대로 4개, 보조 도로 8개
 *  - 구획 N01~N12(북: 군사·마법), W01~W04(서: 장인), E01~E04(동: 귀족·종교), S01~S11(남: 시장·상업·모험가),
 *
 *  - 해자 + 다층 왕성 (RoyalCastle + RoyalHall + Keep)
 *  - 북문 밖 공동묘지, 성벽 바깥은 원래 지형까지 완만한 잔디 경사로
 */
public final class CityLayout {
    public static final int W0 = 63, W1 = 457, WALL_H = 24, APRON = 9, SLOPE = 24;
    /** 공동묘지 범위 (도면 좌표) */
    private static final int CEM_U0 = 292, CEM_U1 = 450, CEM_V0 = 466, CEM_V1 = 525;

    public enum Style { MILITARY, MAGIC, CRAFT, NOBLE, PUBLIC, COMMERCE, RESIDENT, LOGISTIC, SLUM }

    /** 도면 구획 (u0, v0) ~ (u1, v1) */
    public record Parcel(String id, int u0, int v0, int u1, int v1, Style style) {
        boolean contains(int u, int v) {
            return u >= u0 && u <= u1 && v >= v0 && v <= v1;
        }
    }

    public static final Parcel[] PARCELS = {
            new Parcel("N01", 184, 356, 243, 388, Style.MILITARY), new Parcel("N02", 184, 403, 243, 436, Style.MILITARY),
            new Parcel("N03", 134, 356, 160, 387, Style.MILITARY), new Parcel("N04", 85, 356, 118, 387, Style.MILITARY),
            new Parcel("N05", 85, 403, 118, 436, Style.MILITARY), new Parcel("N06", 134, 403, 160, 436, Style.RESIDENT),
            new Parcel("N07", 278, 356, 339, 389, Style.MAGIC), new Parcel("N08", 278, 404, 336, 436, Style.MAGIC),
            new Parcel("N09", 359, 356, 387, 386, Style.MAGIC), new Parcel("N10", 405, 356, 435, 387, Style.RESIDENT),
            new Parcel("N11", 359, 406, 387, 436, Style.RESIDENT), new Parcel("N12", 405, 405, 435, 436, Style.PUBLIC),
            new Parcel("W01", 85, 282, 116, 336, Style.CRAFT), new Parcel("W02", 134, 282, 160, 325, Style.CRAFT),
            new Parcel("W03", 85, 185, 116, 235, Style.SLUM), new Parcel("W04", 134, 185, 160, 235, Style.RESIDENT),
            new Parcel("E01", 360, 275, 387, 336, Style.NOBLE), new Parcel("E02", 405, 290, 435, 336, Style.NOBLE),
            new Parcel("E03", 360, 185, 387, 236, Style.NOBLE), new Parcel("E04", 405, 185, 435, 236, Style.NOBLE),
            new Parcel("S01", 182, 85, 246, 160, Style.PUBLIC), new Parcel("S02", 135, 85, 160, 116, Style.COMMERCE),
            new Parcel("S03", 135, 135, 160, 160, Style.RESIDENT), new Parcel("S04", 85, 85, 116, 116, Style.COMMERCE),
            new Parcel("S05", 85, 135, 116, 160, Style.SLUM), new Parcel("S06", 276, 125, 341, 160, Style.COMMERCE),
            new Parcel("S07", 276, 85, 341, 115, Style.RESIDENT), new Parcel("S08", 360, 85, 387, 115, Style.COMMERCE),
            new Parcel("S09", 360, 135, 387, 160, Style.RESIDENT), new Parcel("S10", 405, 135, 435, 160, Style.RESIDENT),
            new Parcel("S11", 405, 85, 435, 115, Style.RESIDENT),
    };

    /** 도로 사각형 (u0, v0, u1, v1) */
    private static final int[][] ROADS = {
            // 성벽 안 순환로
            {70, 70, 450, 81}, {70, 439, 450, 450}, {70, 70, 81, 450}, {439, 70, 450, 450},
            // 왕성 순환로
            {165, 165, 356, 176}, {165, 345, 356, 356}, {165, 165, 176, 356}, {345, 165, 356, 356},
            // 성문 대로
            {254, 357, 267, 456}, {254, 64, 267, 164}, {64, 254, 164, 267}, {357, 254, 456, 267},
            // 보조 도로
            {121, 80, 130, 166}, {121, 355, 130, 440}, {391, 80, 400, 166}, {391, 355, 400, 440},
            {80, 121, 166, 130}, {355, 121, 440, 130}, {80, 391, 166, 400}, {355, 391, 440, 400},
            // 북문 밖 길과 묘지 가는 길
            {254, 457, 267, 478}, {268, 473, 291, 478},
    };

    public final int cx, cz, base, radius;
    public final long seed;

    private final List<Feature> features = new ArrayList<>();
    private final Map<Long, List<Feature>> byChunk = new HashMap<>();
    private int villagerBudget = 160;

    public CityLayout(int cx, int cz, int base, long seed) {
        this.cx = cx;
        this.cz = cz;
        this.base = base;
        this.radius = CEM_V1 - 260 + SLOPE + 4;
        this.seed = seed;

        RandomSource rand = RandomSource.create(seed);
        for (Parcel pc : PARCELS) features.add(new ParcelDecor(pc));
        addCastle(rand);
        addWallTowers();
        addCemetery();
        fillParcels(rand);
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

    // ───────────────────────── 좌표 변환 ─────────────────────────

    public int wx(int u) {
        return cx + (u - 260);
    }

    public int wz(int v) {
        return cz - (v - 260);
    }

    public int u(int x) {
        return x - cx + 260;
    }

    public int v(int z) {
        return cz - z + 260;
    }

    /** 도면 사각형 → 월드 Lot */
    private Lot lot(int u0, int v0, int u1, int v1, Direction front) {
        int x0 = wx(u0), x1 = wx(u1), z0 = wz(v1), z1 = wz(v0);
        return new Lot((x0 + x1) >> 1, (z0 + z1) >> 1, x0, z0, x1, z1, front);
    }

    /** 도면 중심 + 크기 → 월드 Lot */
    private Lot lotAt(int uc, int vc, int L, Direction front) {
        return lot(uc - L / 2, vc - L / 2, uc - L / 2 + L - 1, vc - L / 2 + L - 1, front);
    }

    public Direction facingToCenter(int x, int z) {
        int dx = cx - x, dz = cz - z;
        if (Math.abs(dx) >= Math.abs(dz)) return dx > 0 ? Direction.EAST : Direction.WEST;
        return dz > 0 ? Direction.SOUTH : Direction.NORTH;
    }

    // ───────────────────────── 왕성 ─────────────────────────

    private void addCastle(RandomSource rand) {
        features.add(new RoyalCastle(this));
        Object[][] halls = {
                {RoyalHall.Kind.THRONE, 234, 294, 287, 319, Direction.NORTH},
                {RoyalHall.Kind.COUNCIL, 204, 268, 230, 290, Direction.EAST},
                {RoyalHall.Kind.CHAPEL, 291, 268, 317, 290, Direction.WEST},
                {RoyalHall.Kind.VAULT, 204, 294, 230, 318, Direction.EAST},
                {RoyalHall.Kind.ARCANE, 291, 294, 317, 318, Direction.WEST},
                {RoyalHall.Kind.GUARD, 204, 210, 230, 238, Direction.EAST},
                {RoyalHall.Kind.ARMORY, 291, 210, 317, 238, Direction.WEST},
        };
        for (Object[] h : halls) {
            int u0 = (Integer) h[1], v0 = (Integer) h[2], u1 = (Integer) h[3], v1 = (Integer) h[4];
            features.add(new RoyalHall((RoyalHall.Kind) h[0], wx(u0), wz(v1), wx(u1), wz(v0), (Direction) h[5], RoyalCastle.L1, rand.nextLong()));
        }
        features.add(new Keep(wx(238), wz(286), wx(283), wz(247), rand.nextLong()));
    }

    // ───────────────────────── 성벽 탑 · 묘지 ─────────────────────────

    private void addWallTowers() {
        int[] ends = {W0 + 3, W1 - 3};
        for (int a : ends) for (int b : ends) features.add(new WallTower(this, wx(a), wz(b), 10, 44));
        for (int t : ends) {
            for (int m : new int[]{160, 360}) {
                features.add(new WallTower(this, wx(t), wz(m), 7, 36));
                features.add(new WallTower(this, wx(m), wz(t), 7, 36));
            }
            for (int g : new int[]{244, 277}) {
                features.add(new WallTower(this, wx(t), wz(g), 8, 40));
                features.add(new WallTower(this, wx(g), wz(t), 8, 40));
            }
        }
    }

    private static boolean inCemetery(int u, int v) {
        return u >= CEM_U0 && u <= CEM_U1 && v >= CEM_V0 && v <= CEM_V1;
    }

    private void addCemetery() {
        for (int u = 310; u <= 430; u += 30) {
            for (int v = 485; v <= 515; v += 30) {
                features.add(new Mausoleum(wx(u), wz(v), Direction.SOUTH));
            }
        }
    }

    // ───────────────────────── 구획 채우기 ─────────────────────────

    private interface Maker {
        Feature make(Lot lot, int index, RandomSource rand);
    }

    private Parcel parcel(String id) {
        for (Parcel p : PARCELS) if (p.id().equals(id)) return p;
        throw new IllegalArgumentException(id);
    }

    /** 구획을 L x L 칸으로 나눠 채움. 각 칸의 정면은 가장 가까운 구획 가장자리(= 도로) 쪽 */
    private void pack(Parcel pc, int u0, int u1, int L, int gap, Maker maker, RandomSource rand) {
        int v0 = pc.v0(), v1 = pc.v1();
        int w = u1 - u0 + 1, d = v1 - v0 + 1;
        int nu = (w + gap) / (L + gap), nv = (d + gap) / (L + gap);
        if (nu <= 0 || nv <= 0) return;
        int ou = u0 + (w - (nu * L + (nu - 1) * gap)) / 2;
        int ov = v0 + (d - (nv * L + (nv - 1) * gap)) / 2;
        int idx = 0;
        for (int a = 0; a < nu; a++) {
            for (int c = 0; c < nv; c++) {
                int lu0 = ou + a * (L + gap), lv0 = ov + c * (L + gap);
                int uc = lu0 + L / 2, vc = lv0 + L / 2;
                int dW = uc - pc.u0(), dE = pc.u1() - uc, dS = vc - pc.v0(), dN = pc.v1() - vc;
                int min = Math.min(Math.min(dW, dE), Math.min(dS, dN));
                Direction front = min == dS ? Direction.SOUTH : min == dN ? Direction.NORTH : min == dW ? Direction.WEST : Direction.EAST;
                Feature f = maker.make(lot(lu0, lv0, lu0 + L - 1, lv0 + L - 1, front), idx++, rand);
                if (f != null) features.add(f);
            }
        }
    }

    private void pack(String id, int L, int gap, Maker maker, RandomSource rand) {
        Parcel pc = parcel(id);
        pack(pc, pc.u0(), pc.u1(), L, gap, maker, rand);
    }

    private boolean villager(float chance, RandomSource rand) {
        if (villagerBudget > 0 && rand.nextFloat() < chance) {
            villagerBudget--;
            return true;
        }
        return false;
    }

    private Feature role(Lot lot, Role r, RandomSource rand) {
        return new RoleBuilding(lot, r, rand, villager(0.25F, rand));
    }

    private Feature house(Lot lot, RandomSource rand) {
        return new House(this, lot, rand, villager(0.25F, rand));
    }

    private Feature whole(String id, java.util.function.Function<Lot, Feature> make) {
        Parcel pc = parcel(id);
        return make.apply(lot(pc.u0(), pc.v0(), pc.u1(), pc.v1(), Direction.SOUTH));
    }

    private void squareIn(String id, int L, java.util.function.Function<Lot, Feature> make) {
        Parcel pc = parcel(id);
        int uc = (pc.u0() + pc.u1()) / 2, vc = (pc.v0() + pc.v1()) / 2;
        features.add(make.apply(lotAt(uc, vc, L, Direction.SOUTH)));
    }

    /** 구획 가운데에 건물 하나 (정면은 왕성 쪽) */
    private void single(String id, int size, java.util.function.Function<Lot, Feature> make) {
        Parcel pc = parcel(id);
        int uc = (pc.u0() + pc.u1()) / 2, vc = (pc.v0() + pc.v1()) / 2;
        Direction front = facingToCenter(wx(uc), wz(vc));
        features.add(make.apply(lotAt(uc, vc, size, front)));
    }

    /**
     * 구획 배정: 주거(집·저택)를 뺀 건물은 모두 한 번씩만 짓습니다.
     * 빈 땅은 ParcelDecor 가 구역별 소품·나무·가로등으로 채웁니다.
     */
    private void fillParcels(RandomSource rand) {
        Maker homes = (l, i, r) -> house(l, r);
        // ── 북쪽: 군사 구역
        single("N01", 25, l -> role(l, Role.BARRACKS, rand));                                     // 기사단 병영
        features.add(whole("N02", l -> new TrainingGround(l.minX(), l.minZ(), l.maxX(), l.maxZ(), rand.nextLong())));
        single("N03", 23, l -> role(l, Role.ARMORY, rand));
        single("N04", 25, l -> role(l, Role.STABLE, rand));
        single("N05", 25, l -> role(l, Role.WAREHOUSE, rand));                                    // 보급 창고
        pack("N06", 15, 2, homes, rand);                                                           // 병사 가족 주거
        // ── 북동: 마법 구역
        single("N07", 25, l -> new AcademyHall(this, l, rand));
        single("N08", 23, l -> new MageTower(this, l, rand));
        single("N09", 15, l -> role(l, Role.SHOP, rand));                                         // 마법 상점
        pack("N10", 15, 2, homes, rand);                                                           // 마법사 주거
        pack("N11", 15, 2, homes, rand);
        squareIn("N12", 30, l -> new Garden(this, l, rand));
        // ── 서쪽: 장인 구역 + 슬럼가
        single("W01", 17, l -> role(l, Role.SMITHY, rand));
        single("W02", 17, l -> role(l, Role.WORKSHOP, rand));
        Parcel w03 = parcel("W03"), s05 = parcel("S05");
        features.add(new Slum(wx(w03.u0()), wz(w03.v1()), wx(w03.u1()), wz(w03.v0()), wx(SLUM_SHAFT_U), wz(SLUM_SHAFT_V), rand.nextLong()));
        features.add(new Slum(wx(s05.u0()), wz(s05.v1()), wx(s05.u1()), wz(s05.v0()), Integer.MIN_VALUE / 2, Integer.MIN_VALUE / 2, rand.nextLong()));
        pack("W04", 15, 2, homes, rand);
        // ── 동쪽: 귀족·종교 구역
        Parcel e01 = parcel("E01"), e02 = parcel("E02");
        features.add(new Cathedral(wx(e01.u0()), wz(e01.v1()), wx(e01.u1()), wz(e01.v0()), Direction.SOUTH, rand.nextLong()));
        features.add(new Temple(wx(e02.u0()), wz(e02.v1()), wx(e02.u1()), wz(e02.v0()), Direction.SOUTH, rand.nextLong()));
        Parcel e03 = parcel("E03"), e04 = parcel("E04");
        for (int vc : new int[]{e03.v0() + 12, e03.v0() + 38}) {
            int uc = e03.u1() - 7;                                // 저택 깊이 15, 서쪽 앞뜰 13
            features.add(new RoleBuilding(lot(uc - 7, vc - 12, uc + 7, vc + 12, Direction.WEST), Role.MANSION, rand, villager(0.5F, rand)));
        }
        int uc4 = e04.u1() - 7, vc4 = (e04.v0() + e04.v1()) / 2;
        features.add(new RoleBuilding(lot(uc4 - 7, vc4 - 12, uc4 + 7, vc4 + 12, Direction.WEST), Role.MANSION, rand, villager(0.5F, rand)));
        // ── 남쪽: 시장·상업·모험가 구역
        features.add(whole("S01", l -> new MarketPlaza(l.minX(), l.minZ(), l.maxX(), l.maxZ(), rand.nextLong())));
        single("S02", 19, l -> role(l, Role.INN, rand));
        pack("S03", 15, 2, homes, rand);
        squareIn("S04", 32, l -> new MarketSquare(this, l, rand, villager(0.6F, rand)));
        // S06: 모험가 길드 본관 + 길드 마당(망루·의뢰 게시판·훈련장)
        features.add(new RoleBuilding(lot(277, 138, 309, 160, Direction.NORTH), Role.GUILD, rand, true));
        features.add(new GuildYard(this, 311, 125, 341, 160, rand.nextLong()));
        pack("S07", 15, 2, homes, rand);
        squareIn("S08", 27, l -> new FountainSquare(this, l));
        pack("S09", 15, 2, homes, rand);
        pack("S10", 15, 2, homes, rand);
        pack("S11", 15, 2, homes, rand);
    }

    /** 하수도 입구 (도면 좌표) */
    public static final int SLUM_SHAFT_U = 100, SLUM_SHAFT_V = 207, CASTLE_SHAFT_U = 205, CASTLE_SHAFT_V = 250;

    private void addUnderworldAndSky(RandomSource rand) {
        features.add(new Sewer(this, SLUM_SHAFT_U, SLUM_SHAFT_V, CASTLE_SHAFT_U, CASTLE_SHAFT_V, rand.nextLong()));
        features.add(new WallWalk(this, rand.nextLong()));
        int[][] islands = {{222, 300, 78, 7}, {302, 222, 62, 5}, {335, 420, 50, 6}, {150, 300, 44, 4}, {412, 205, 48, 5}};
        for (int[] i : islands) features.add(new FloatingIsland(wx(i[0]), wz(i[1]), i[2], i[3], rand.nextLong()));
    }

    // ───────────────────────── 조회 ─────────────────────────

    /** 건물(구획 장식 제외)이 이 칸을 차지하는지 */
    public boolean isOccupied(int x, int z) {
        List<Feature> list = byChunk.get(ChunkPos.asLong(x >> 4, z >> 4));
        if (list == null) return false;
        for (Feature f : list) if (!(f instanceof ParcelDecor) && f.occupies(x, z)) return true;
        return false;
    }

    private boolean occupiedNear(int x, int z, int r) {
        return isOccupied(x - r, z - r) || isOccupied(x + r, z - r) || isOccupied(x - r, z + r) || isOccupied(x + r, z + r) || isOccupied(x, z);
    }

    private static boolean isRoad(int u, int v) {
        for (int[] r : ROADS) if (u >= r[0] && u <= r[2] && v >= r[1] && v <= r[3]) return true;
        return false;
    }

    private static Parcel parcelAt(int u, int v) {
        for (Parcel p : PARCELS) if (p.contains(u, v)) return p;
        return null;
    }

    /** 왕성 바깥 해자 영역까지의 거리 (0 이하 = 왕성 성벽 안) */
    private static int castleRing(int u, int v) {
        return Math.max(Math.max(195 - u, u - 325), Math.max(195 - v, v - 325));
    }

    // ───────────────────────── 기둥 단위 지형 ─────────────────────────

    public void buildColumn(Placer p, int x, int z) {
        final int u = u(x), v = v(z), b = base;
        if (inCemetery(u, v)) {
            p.clearAbove(x, z, b + 1);
            cemetery(p, x, z, u, v);
            p.foundation(x, z, b - 1, 32);
            return;
        }
        boolean road = isRoad(u, v);
        int e = flatDist(u, v);
        if (e > 0) {                                                       // 평평한 도시 바깥: 원래 지형까지 경사로
            slope(p, x, z, e);
            return;
        }

        p.clearAbove(x, z, b + 1);
        BlockState surface;
        int lowest = b;

        if (u < W0 || u > W1 || v < W0 || v > W1) {                       // 성벽 바깥 잔디 띠
            surface = road ? roadBlock(u, v) : isTrail(u, v) ? Blocks.DIRT_PATH.defaultBlockState() : GRASS;
            if (!road && (u == W0 - 5 || u == W1 + 5 || v == W0 - 5 || v == W1 + 5) && Math.floorMod(u + v, 24) == 0) Shapes.lampPost(p, x, z, b);
        } else if (u <= W0 + 6 || u >= W1 - 6 || v <= W0 + 6 || v >= W1 - 6) {          // 외성벽
            p.set(x, b, z, QUARTZ_BRICKS);
            wallColumn(p, x, z, u, v);
            p.foundation(x, z, b - 1, 48);
            return;
        } else if (castleRing(u, v) <= 18) {                               // 왕성 + 해자
            int r = castleRing(u, v);
            boolean lane = (u >= 254 && u <= 267) || (v >= 254 && v <= 267);
            if (r <= 0) {
                castleGround(p, x, z, u, v);
                p.foundation(x, z, b - 1, 48);
                return;
            } else if (lane) {
                surface = QUARTZ_BRICKS;
            } else if (r >= 4 && r <= 14) {                                // 해자
                boolean edge = r == 4 || r == 14;
                if (edge) {
                    surface = SMOOTH_QUARTZ;
                    for (int i = 1; i <= 5; i++) p.set(x, b - i, z, QUARTZ_BRICKS);
                    if (r == 14) p.set(x, b + 1, z, GLASS_PANE);
                } else {
                    surface = AIR;
                    for (int i = 1; i <= 4; i++) p.set(x, b - i, z, WATER);
                    p.set(x, b - 5, z, CALCITE);
                }
                lowest = b - 5;
            } else {
                surface = GRASS;
                if (r >= 15 && Shapes.hash(x, 3, z, seed) % 9 == 0) p.set(x, b + 1, z, Royal.ROYAL_FLOWERS[Shapes.hash(x, 4, z, seed) % 4]);
            }
        } else if (road) {
            surface = roadBlock(u, v);
            if (surface == MINT_CUT && Math.floorMod(u + v, 14) == 0 && !isOccupied(x, z)) lamp(p, x, z, b);
            if (isLane(u, v) && Shapes.hash(x, 12, z, seed) % 4 == 0) {        // 성벽 밑 사람들이 다녀 닳은 길
                surface = Shapes.hash(x, 13, z, seed) % 3 == 0 ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.DIRT_PATH.defaultBlockState();
            }
        } else {
            Parcel pc = parcelAt(u, v);
            if (pc != null) {
                surface = parcelGround(pc, u, v);
            } else {                                                       // 빈 광장 포장
                surface = (Math.floorMod(u, 9) == 0 || Math.floorMod(v, 9) == 0) ? SMOOTH_QUARTZ : WHITE_CONCRETE;
                if (Math.floorMod(u, 10) == 5 && Math.floorMod(v, 10) == 5 && !isOccupied(x, z)) lamp(p, x, z, b);
            }
        }
        p.set(x, b, z, surface);
        p.foundation(x, z, lowest - 1, 48);
    }

    /** 평평하게 지은 영역(성벽 + 바깥 잔디 띠, 묘지, 묘지 길) 밖으로 몇 칸 떨어졌는지. 0 이하 = 안쪽 */
    private static int flatDist(int u, int v) {
        int ds = Math.max(Math.max(W0 - APRON - u, u - W1 - APRON), Math.max(W0 - APRON - v, v - W1 - APRON));
        int dc = Math.max(Math.max(CEM_U0 - u, u - CEM_U1), Math.max(CEM_V0 - v, v - CEM_V1));
        int dr = Math.max(Math.max(254 - u, u - 291), Math.max(W1 - v, v - 478));
        return Math.min(ds, Math.min(dc, dr));
    }

    /** 도시 바닥 높이에서 원래 지형 높이로 이어지는 완만한 잔디 경사 (돌 절벽 대신) */
    private void slope(Placer p, int x, int z, int e) {
        int u = u(x), v = v(z);
        boolean trail = isTrail(u, v);
        if (e > SLOPE) {
            if (trail && e <= SLOPE + 16) {                                    // 원래 지형 위 흙길
                int top = p.naturalTop(x, z);
                p.clearAbove(x, z, top + 1);
                p.set(x, top, z, Shapes.hash(x, 15, z, seed) % 4 == 0 ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.DIRT_PATH.defaultBlockState());
            }
            return;
        }
        int top = p.naturalTop(x, z);
        double t = (double) e / (SLOPE + 1);
        t = t * t * (3 - 2 * t);
        int target = (int) Math.round(base + (top - base) * t);
        if (target == top) return;
        if (target < top) {
            p.clearAbove(x, z, target + 1);
        } else {
            for (int y = top + 1; y < target; y++) p.set(x, y, z, Blocks.DIRT.defaultBlockState());
        }
        if (trail) {
            p.set(x, target, z, Shapes.hash(x, 15, z, seed) % 4 == 0 ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.DIRT_PATH.defaultBlockState());
            return;
        }
        p.set(x, target, z, GRASS);
        if (Shapes.hash(x, 9, z, seed) % 17 == 0) p.set(x, target + 1, z, FLOWERS[Shapes.hash(x, 10, z, seed) % FLOWERS.length]);
    }

    /** 가로등: 흰 석영 가로등과 판타지 수정 가로등을 섞음 */
    private void lamp(Placer p, int x, int z, int b) {
        if (Shapes.hash(x, 14, z, seed) % 3 == 0) Fantasy.crystalLamp(p, x, z, b);
        else Shapes.lampPost(p, x, z, b);
    }

    /** 성벽 바로 안쪽 순환로 중 벽에 붙은 3칸 */
    private static boolean isLane(int u, int v) {
        int t = Math.min(Math.min(u - W0, W1 - u), Math.min(v - W0, W1 - v));
        return t >= 7 && t <= 9;
    }

    /** 성문 밖으로 이어지는 흙길 (대로 연장선) */
    private static boolean isTrail(int u, int v) {
        return (u >= 256 && u <= 265) || (v >= 256 && v <= 265);
    }

    private static BlockState roadBlock(int u, int v) {
        boolean edge = !isRoad(u + 1, v) || !isRoad(u - 1, v) || !isRoad(u, v + 1) || !isRoad(u, v - 1);
        return edge ? MINT_CUT : (Math.floorMod(u * 3 + v * 7, 11) == 0 ? CALCITE : SMOOTH_QUARTZ);
    }

    private BlockState parcelGround(Parcel pc, int u, int v) {
        boolean border = u == pc.u0() || u == pc.u1() || v == pc.v0() || v == pc.v1();
        if (border) return QUARTZ_BRICKS;
        int lu = u - pc.u0(), lv = v - pc.v0();
        return switch (pc.style()) {
            case MILITARY -> (lu % 7 == 0 || lv % 7 == 0) ? SMOOTH_QUARTZ : CALCITE;
            case MAGIC -> (lu % 8 == 0 || lv % 8 == 0) ? MINT_CUT : GRASS;
            case CRAFT -> ((lu + lv) & 1) == 0 ? CALCITE : WHITE_CONCRETE;
            case NOBLE, PUBLIC -> (lu % 10 == 5 || lv % 10 == 5) ? CALCITE : GRASS;
            case COMMERCE -> ((lu / 2 + lv / 2) & 1) == 0 ? SMOOTH_QUARTZ : CALCITE;
            case RESIDENT -> (lu % 9 == 4 || lv % 9 == 4) ? CALCITE : GRASS;
            case LOGISTIC -> (lu % 6 == 0) ? SMOOTH_QUARTZ : WHITE_CONCRETE;
            case SLUM -> Blocks.COARSE_DIRT.defaultBlockState();
        };
    }

    /** 왕성 성벽 안쪽의 단 채우기: 1단 +8, 2단 +18 (성벽·계단·건물은 RoyalCastle 등이 지음) */
    private void castleGround(Placer p, int x, int z, int u, int v) {
        int b = base;
        p.set(x, b, z, QUARTZ_BRICKS);
        if (u < 200 || u > 320 || v < 200 || v > 320) return;            // 왕성 성벽 띠
        int top = b + (RoyalCastle.inL2(u, v) ? RoyalCastle.L2 : RoyalCastle.L1);
        for (int y = b + 1; y < top; y++) p.set(x, y, z, QUARTZ_BRICKS);
        boolean inlay = Math.floorMod(u - 260, 10) == 0 || Math.floorMod(v - 260, 10) == 0;
        p.set(x, top, z, inlay ? CHISELED_QUARTZ : Royal.marble(x, z));
    }

    /** 외성벽: 높이 24, 민트 띠, 총안, 성문 아치 (가로 254~267) */
    private void wallColumn(Placer p, int x, int z, int u, int v) {
        int b = base, H = WALL_H;
        boolean uBand = u <= W0 + 6 || u >= W1 - 6, vBand = v <= W0 + 6 || v >= W1 - 6;
        int t = uBand ? (u <= W0 + 6 ? u - W0 : W1 - u) : (v <= W0 + 6 ? v - W0 : W1 - v);   // 0 = 바깥면
        int lat = uBand ? v : u;
        boolean gate = !(uBand && vBand) && lat >= 254 && lat <= 267;
        double open = gate ? 9 + 3 * Math.sqrt(Math.max(0, 1 - Math.pow((lat - 260.5) / 7.0, 2))) : -1;
        for (int ly = 1; ly <= H; ly++) {
            BlockState s = QUARTZ_BRICKS;
            if (ly == H) s = (t >= 1 && t <= 5) ? walkway(x, z) : SMOOTH_QUARTZ;
            else if (ly == H - 4) s = MINT_CUT;
            else if (t == 0 && ly >= 14 && ly <= 15 && Math.floorMod(lat, 8) == 0) s = GLASS;
            if (gate) {
                if (ly <= open) s = AIR;
                else if (ly <= open + 1) s = (lat == 260 || lat == 261) ? MINT_CHISELED : CHISELED_QUARTZ;
            }
            p.set(x, b + ly, z, s);
        }
        if (gate) p.set(x, b, z, (lat == 254 || lat == 267) ? MINT_CUT : SMOOTH_QUARTZ);
        if (t == 0 && Math.floorMod(lat, 4) < 2) p.set(x, b + H + 1, z, CALCITE);
        if (t == 6) p.set(x, b + H + 1, z, Math.floorMod(lat, 8) == 4 ? Blocks.LANTERN.defaultBlockState() : SMOOTH_QUARTZ_SLAB);
        // 순찰병이 두고 간 흔적: 가끔 통·건초
        if (t >= 2 && t <= 4 && !gate) {
            int h = Shapes.hash(x, 16, z, seed) % 211;
            if (h == 0) p.set(x, b + H + 1, z, Deco.BARREL);
            else if (h == 1) p.set(x, b + H + 1, z, Blocks.HAY_BLOCK.defaultBlockState());
        }
    }

    /** 닳은 성벽 위 보행로: 석영 사이사이 방해석·섬록암·금 간 석재 */
    private BlockState walkway(int x, int z) {
        int h = Shapes.hash(x, 17, z, seed) % 20;
        return h < 12 ? SMOOTH_QUARTZ : h < 16 ? CALCITE : h < 19 ? Blocks.POLISHED_DIORITE.defaultBlockState() : Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
    }

    /** 공동묘지: 방해석 길, 묘비(조각 석영 + 촛불), 꽃 핀 무덤, 영혼 랜턴 기둥, 낮은 담 */
    private void cemetery(Placer p, int x, int z, int u, int v) {
        int b = base;
        int a = Math.floorMod(u - CEM_U0, 14), c = Math.floorMod(v - CEM_V0, 14);
        boolean path = a <= 1 || c <= 1;
        boolean border = u == CEM_U0 || u == CEM_U1 || v == CEM_V0 || v == CEM_V1;
        boolean entrance = u == CEM_U0 && v >= 473 && v <= 478;
        int h = Shapes.hash(x, 21, z, seed);
        p.set(x, b, z, path || entrance ? CALCITE : GRASS);
        if (border) {
            if (!entrance) {
                p.set(x, b + 1, z, CALCITE);
                p.set(x, b + 2, z, SMOOTH_QUARTZ_SLAB);
            }
            return;
        }
        if (a == 0 && c == 0) {
            p.set(x, b + 1, z, QUARTZ_PILLAR);
            p.set(x, b + 2, z, QUARTZ_PILLAR);
            p.set(x, b + 3, z, Blocks.SOUL_LANTERN.defaultBlockState());
            return;
        }
        if (path || isOccupied(x, z)) return;
        if (a >= 3 && a <= 12 && a % 3 == 0) {
            if (c % 5 == 3) {
                p.set(x, b + 1, z, CHISELED_QUARTZ);
                if (h % 3 == 0) p.set(x, b + 2, z, Deco.candles(1 + h % 4));
            } else if (c % 5 == 4) {
                p.set(x, b, z, Blocks.COARSE_DIRT.defaultBlockState());
                if (h % 2 == 0) p.set(x, b + 1, z, (h >> 2) % 2 == 0 ? Blocks.LILY_OF_THE_VALLEY.defaultBlockState() : Blocks.WHITE_TULIP.defaultBlockState());
            }
        }
    }

    // ───────────────────────── 구획 장식 ─────────────────────────

    /**
     * 구획의 빈 땅을 채우는 소품: 자작나무, 가로등, 꽃, 구역별 소품(군사: 건초·과녁·통 / 장인: 통·장작·모루 /
     * 물류: 건초 더미·통·상자 / 상업: 노점 화분·통 / 마법: 자수정·책장·촛불)
     * 건물보다 먼저 지어지고, 건물이 차지한 칸은 피합니다.
     */
    private final class ParcelDecor extends Feature {
        private final Parcel pc;

        ParcelDecor(Parcel pc) {
            super(wx(pc.u0()), wz(pc.v1()), wx(pc.u1()), wz(pc.v0()));
            this.pc = pc;
        }

        @Override
        public boolean occupies(int x, int z) {
            return false;
        }

        @Override
        public void build(Placer p, CityLayout city) {
            final int b = groundY;
            final boolean green = pc.style() == Style.MAGIC || pc.style() == Style.NOBLE || pc.style() == Style.PUBLIC || pc.style() == Style.RESIDENT;
            p.forColumns(minX + 1, minZ + 1, maxX - 1, maxZ - 1, (x, z) -> {
                int lu = u(x) - pc.u0(), lv = v(z) - pc.v0();
                if (lu <= 1 || lv <= 1 || u(x) >= pc.u1() - 1 || v(z) >= pc.v1() - 1) return;
                int h = Shapes.hash(x, 13, z, seed) % 1000;
                if (green && lu % 11 == 6 && lv % 11 == 6) {
                    if (!occupiedNear(x, z, 3)) Shapes.birchTree(p, x, z, b, 5 + h % 3);
                    return;
                }
                if (lu % 12 == 3 && lv % 12 == 9) {
                    if (!occupiedNear(x, z, 1)) Shapes.lampPost(p, x, z, b);
                    return;
                }
                if (isOccupied(x, z) || occupiedNear(x, z, 1)) return;
                if (p.get(x, b, z) != GRASS && green) return;
                BlockState prop = prop(pc.style(), h, x, z);
                if (prop != null) p.set(x, b + 1, z, prop);
                if (prop == HAY && h % 3 == 0) p.set(x, b + 2, z, HAY);
            });
        }
    }

    private static final BlockState HAY = Blocks.HAY_BLOCK.defaultBlockState();

    private BlockState prop(Style s, int h, int x, int z) {
        return switch (s) {
            case MILITARY -> h < 6 ? HAY : h < 10 ? Deco.BARREL : h < 12 ? Blocks.TARGET.defaultBlockState() : null;
            case CRAFT -> h < 8 ? Deco.BARREL : h < 12 ? Blocks.STRIPPED_BIRCH_LOG.defaultBlockState() : h < 13 ? Blocks.ANVIL.defaultBlockState()
                    : h < 15 ? Blocks.GRINDSTONE.defaultBlockState() : null;
            case LOGISTIC -> h < 12 ? HAY : h < 22 ? Deco.BARREL : h < 26 ? Deco.chest(Direction.NORTH) : null;
            case COMMERCE -> h < 5 ? Deco.BARREL : h < 9 ? Deco.pot(h) : null;
            case MAGIC -> h < 30 ? FLOWERS[h % FLOWERS.length] : h < 32 ? Blocks.AMETHYST_CLUSTER.defaultBlockState() : null;
            case NOBLE -> h < 25 ? Royal.ROYAL_FLOWERS[h % 4] : h < 30 ? Royal.HEDGE : null;
            case PUBLIC, RESIDENT -> h < 40 ? FLOWERS[h % FLOWERS.length] : null;
            case SLUM -> null;
        };
    }
}
