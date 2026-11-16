package io.github.aswq0258.magiccity.site;

import io.github.aswq0258.magiccity.build.BuildLayout;
import io.github.aswq0258.magiccity.build.Placer;
import io.github.aswq0258.magiccity.city.CityLayout;
import io.github.aswq0258.magiccity.city.Palette;
import io.github.aswq0258.magiccity.city.Royal;
import io.github.aswq0258.magiccity.city.Shapes;
import io.github.aswq0258.magiccity.city.feature.Feature;
import io.github.aswq0258.magiccity.city.feature.Frame;
import io.github.aswq0258.magiccity.city.feature.Outskirts;
import io.github.aswq0258.magiccity.world.KingdomRoads;
import io.github.aswq0258.magiccity.world.KingdomTerrain;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 촌락 배치 마스터플랜: 왕국 대륙의 지정된 촌락 16곳 + 전초기지 4곳 (무작위 배치 대신).
 * 사람들이 정착한 이유(교역로·농업·광산·국경) 에 따라 유형이 다르고, 일부러 비워 둔 땅(세계수 아래, 고룡 봉우리,
 * 만년설, 부유섬, 저주받은 숲 중심, 옛 왕도) 에는 촌락이 없음.
 * 유형별 건물 수·대표 건물·길 포장·방어 시설이 다르고, 주택은 소형 약 70% · 중형 약 30% (+ 촌장 저택).
 * 여관은 역참촌·교역촌에만. /magiccity site hamlets 로 한 번에 지음.
 */
public final class Hamlets implements BuildLayout {

    // ───────────────────────── 유형 · 양식 ─────────────────────────

    public enum Role { HOME, MEDIUM, MANOR, INN, STABLE, WAREHOUSE, SMITHY, CHAPEL, GUILD, BARRACKS, ARMORY, LODGE, STORE, WATERMILL, HIDDEN, TREEHOUSE, WOODSTORE }

    public enum Extra { WINDMILL, WATCHTOWER, ALTAR, LIFT, LIGHTHOUSE, PIER, TENTS, CELLAR, DIGPIT, TREEHOUSES, WOODSTORE }

    public enum Type {
        STATION("역참촌", 9, new Role[]{Role.INN, Role.STABLE, Role.WAREHOUSE, Role.SMITHY}, new Extra[]{Extra.WATCHTOWER}, 3, true, false),
        FARM_BIG("큰 농촌", 20, new Role[]{Role.MANOR, Role.CHAPEL, Role.SMITHY, Role.WAREHOUSE}, new Extra[]{Extra.WINDMILL, Extra.WINDMILL}, 4, false, true),
        FARM_SMALL("소규모 농촌", 7, new Role[]{Role.WAREHOUSE}, new Extra[]{Extra.WINDMILL}, 2, false, true),
        WATERMILL_FARM("강변 수차촌", 7, new Role[]{Role.WATERMILL, Role.WAREHOUSE}, new Extra[]{}, 2, false, true),
        HERB_SMALL("약초촌", 6, new Role[]{Role.STORE}, new Extra[]{Extra.ALTAR}, 2, false, true),
        TRADE("교역촌", 14, new Role[]{Role.INN, Role.GUILD, Role.WAREHOUSE, Role.STABLE, Role.MANOR}, new Extra[]{Extra.WATCHTOWER}, 4, true, false),
        FOREST_OUTPOST("숲 전초마을", 9, new Role[]{Role.GUILD, Role.INN, Role.WAREHOUSE}, new Extra[]{Extra.WATCHTOWER, Extra.WOODSTORE}, 3, false, false),
        ELF_PILGRIM("엘프 순례촌", 2, new Role[]{Role.STORE}, new Extra[]{Extra.TREEHOUSES, Extra.ALTAR}, 3, false, false),
        DWARF_HAUL("광석 운송촌", 9, new Role[]{Role.WAREHOUSE, Role.SMITHY, Role.WAREHOUSE}, new Extra[]{Extra.LIFT, Extra.WATCHTOWER}, 3, false, false),
        FROST_TOWN("혹한 마을", 13, new Role[]{Role.LODGE, Role.SMITHY, Role.WAREHOUSE, Role.MANOR}, new Extra[]{Extra.WATCHTOWER, Extra.WOODSTORE}, 4, false, false),
        FROST_PORT("빙해 항구", 10, new Role[]{Role.WAREHOUSE, Role.LODGE, Role.INN}, new Extra[]{Extra.PIER, Extra.PIER}, 3, true, false),
        FISHING("어촌", 7, new Role[]{Role.STORE}, new Extra[]{Extra.PIER}, 2, false, false),
        SWAMP_STILT("늪지 수상촌", 9, new Role[]{Role.STORE, Role.LODGE}, new Extra[]{}, 3, false, false),
        MILITARY("변경 경비촌", 10, new Role[]{Role.BARRACKS, Role.ARMORY, Role.STABLE, Role.SMITHY, Role.BARRACKS}, new Extra[]{}, 4, false, false),
        SUPPLY("보급초소", 3, new Role[]{Role.LODGE}, new Extra[]{Extra.WOODSTORE, Extra.WATCHTOWER}, 1, false, false),
        LIGHTHOUSE("등대초소", 1, new Role[]{Role.STORE}, new Extra[]{Extra.LIGHTHOUSE}, 1, false, false),
        DIG_CAMP("발굴 야영지", 0, new Role[]{Role.STORE}, new Extra[]{Extra.TENTS, Extra.DIGPIT}, 1, false, false),
        HIDEOUT("피난민 은신처", 0, new Role[]{Role.HIDDEN, Role.HIDDEN, Role.HIDDEN, Role.HIDDEN, Role.HIDDEN, Role.HIDDEN}, new Extra[]{Extra.CELLAR}, 2, false, false);

        public final String title;
        final int houses, lanes;
        final Role[] specials;
        final Extra[] extras;
        final boolean paved, fields;

        Type(String title, int houses, Role[] specials, Extra[] extras, int lanes, boolean paved, boolean fields) {
            this.title = title;
            this.houses = houses;
            this.specials = specials;
            this.extras = extras;
            this.lanes = lanes;
            this.paved = paved;
            this.fields = fields;
        }
    }

    /** 건축 양식 (재료) */
    public enum Style {
        KINGDOM(Blocks.CALCITE, Blocks.OAK_LOG, Blocks.OAK_PLANKS, Blocks.WAXED_OXIDIZED_CUT_COPPER_STAIRS, Blocks.WAXED_OXIDIZED_CUT_COPPER, Blocks.OAK_FENCE, "주민"),
        FOREST(Blocks.SPRUCE_PLANKS, Blocks.DARK_OAK_LOG, Blocks.SPRUCE_PLANKS, Blocks.DARK_OAK_STAIRS, Blocks.DARK_OAK_PLANKS, Blocks.SPRUCE_FENCE, "숲지기"),
        ELF(Blocks.BIRCH_PLANKS, Blocks.BIRCH_LOG, Blocks.MOSS_BLOCK, Blocks.BIRCH_STAIRS, Blocks.FLOWERING_AZALEA_LEAVES, Blocks.BIRCH_FENCE, "엘프 순례자"),
        DWARF(Blocks.DEEPSLATE_BRICKS, Blocks.POLISHED_DEEPSLATE, Blocks.POLISHED_DEEPSLATE, Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILES, Blocks.SPRUCE_FENCE, "드워프 운송꾼"),
        FROST(Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_LOG, Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_FENCE, "설원 주민"),
        FISHER(Blocks.MANGROVE_PLANKS, Blocks.MANGROVE_LOG, Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_PLANKS, Blocks.MANGROVE_FENCE, "어부"),
        SWAMP(Blocks.MANGROVE_PLANKS, Blocks.MANGROVE_LOG, Blocks.MANGROVE_PLANKS, Blocks.DARK_OAK_STAIRS, Blocks.MOSS_BLOCK, Blocks.MANGROVE_FENCE, "늪지 주민"),
        FRONTIER(Blocks.COBBLESTONE, Blocks.DARK_OAK_LOG, Blocks.SPRUCE_PLANKS, Blocks.DARK_OAK_STAIRS, Blocks.DARK_OAK_PLANKS, Blocks.DARK_OAK_FENCE, "경비병"),
        REFUGEE(Blocks.MOSSY_COBBLESTONE, Blocks.DARK_OAK_LOG, Blocks.COARSE_DIRT, Blocks.MOSSY_COBBLESTONE_STAIRS, Blocks.MOSS_BLOCK, Blocks.DARK_OAK_FENCE, "피난민");

        final BlockState wall, post, floor, ridge, fence;
        final Block roofStairs;
        public final String people;

        Style(Block wall, Block post, Block floor, Block roofStairs, Block ridge, Block fence, String people) {
            this.wall = wall.defaultBlockState();
            this.post = post.defaultBlockState();
            this.floor = floor.defaultBlockState();
            this.roofStairs = roofStairs;
            BlockState r = ridge.defaultBlockState();
            if (r.hasProperty(LeavesBlock.PERSISTENT)) r = r.setValue(LeavesBlock.PERSISTENT, true);
            this.ridge = r;
            this.fence = fence.defaultBlockState();
            this.people = people;
        }
    }

    /** 촌락 하나: 이름, 중심, 지면, 유형, 양식, 반폭, 방어 등급(D0~D3) */
    public record Spot(String name, int x, int z, int y, Type type, Style style, int half, int defense) {
        public String title() {
            return type.title;
        }
    }

    private static List<Spot> spots;

    private static Spot spot(String name, int x, int z, Type t, Style s, int half, int def) {
        return new Spot(name, x, z, KingdomTerrain.column(x, z).h(), t, s, half, def);
    }

    /** 마스터플랜의 촌락 20곳 (도면 좌표 x2 = 월드 좌표, 해안 마을은 실제 해안선에, 북문 역참촌은 동선대로) */
    public static synchronized List<Spot> spots() {
        if (spots != null) return spots;
        List<Spot> l = new ArrayList<>();
        // 수도 주변·남부 농경권
        l.add(spot("북문 역참촌", 320, -700, Type.STATION, Style.KINGDOM, 55, 1));
        l.add(spot("남부 농경마을", 80, 1700, Type.FARM_BIG, Style.KINGDOM, 90, 0));
        l.add(spot("남서 제분촌", -660, 1280, Type.FARM_SMALL, Style.KINGDOM, 50, 0));
        l.add(spot("남부 사거리촌", -380, 2440, Type.TRADE, Style.KINGDOM, 65, 1));
        l.add(spot("동부 강변 수차촌", 830, 1110, Type.WATERMILL_FARM, Style.KINGDOM, 55, 0));
        // 서부 숲·엘프·드워프권
        l.add(spot("서부 숲 전초마을", -1120, -80, Type.FOREST_OUTPOST, Style.FOREST, 65, 2));
        l.add(spot("엘프 순례촌", -1720, -800, Type.ELF_PILGRIM, Style.ELF, 65, 1));
        l.add(spot("서부 약초촌", -2060, 220, Type.HERB_SMALL, Style.FOREST, 45, 1));
        l.add(spot("드워프 광석 운송촌", -2960, -1920, Type.DWARF_HAUL, Style.DWARF, 60, 2));
        // 북부 혹한권
        l.add(spot("혹한의 북부 마을", 40, -3440, Type.FROST_TOWN, Style.FROST, 70, 2));
        l.add(spot("빙해 항구", -60, -4490, Type.FROST_PORT, Style.FROST, 65, 2));
        // 동부 강·해안·습지권
        l.add(spot("동부 교역소", 1600, 240, Type.TRADE, Style.KINGDOM, 80, 1));
        l.add(spot("동부 상단 중계촌", 2100, 860, Type.TRADE, Style.KINGDOM, 70, 1));
        l.add(spot("동해안 어촌", 3440, 880, Type.FISHING, Style.FISHER, 45, 0));
        l.add(spot("남동 늪지 수상촌", 1840, 2600, Type.SWAMP_STILT, Style.SWAMP, 55, 1));
        // 남서부 변경권
        l.add(spot("남서 변경 경비촌", -1880, 2580, Type.MILITARY, Style.FRONTIER, 75, 3));
        // 전초기지
        l.add(spot("북부 보급초소", -60, -3960, Type.SUPPLY, Style.FROST, 25, 1));
        l.add(spot("동부 해안 등대초소", 3500, -560, Type.LIGHTHOUSE, Style.FISHER, 22, 0));
        l.add(spot("고대 유적 발굴 야영지", 820, 2840, Type.DIG_CAMP, Style.KINGDOM, 30, 0));
        l.add(spot("폐왕도 피난민 은신처", -2900, 2940, Type.HIDEOUT, Style.REFUGEE, 35, 0));
        spots = Collections.unmodifiableList(l);
        return spots;
    }

    public static boolean near(double x, double z) {
        for (Spot s : spots()) if (Math.abs(x - s.x()) < s.half() + 20 && Math.abs(z - s.z()) < s.half() + 20) return true;
        return false;
    }

    public static String describe(double px, double pz, int max) {
        List<Spot> sorted = new ArrayList<>(spots());
        sorted.sort((a, b) -> Double.compare(Math.hypot(a.x() - px, a.z() - pz), Math.hypot(b.x() - px, b.z() - pz)));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(max, sorted.size()); i++) {
            Spot s = sorted.get(i);
            sb.append("\n§7- §f").append(s.name()).append(" §7(").append(s.title()).append(") ")
                    .append(s.x()).append(", ").append(s.y() + 1).append(", ").append(s.z())
                    .append(" §8[").append(Math.round(Math.hypot(s.x() - px, s.z() - pz))).append("블록]");
        }
        return sb.toString();
    }

    private static boolean solid(BlockState s) {
        return !s.isAir() && s.getFluidState().isEmpty();
    }

    // ───────────────────────── 건설 설계도 ─────────────────────────

    private final List<Feature> features = new ArrayList<>();
    private final Map<Long, List<Feature>> byChunk = new HashMap<>();
    private final List<ChunkPos> chunks = new ArrayList<>();
    private final List<int[]> lit = new ArrayList<>();          // 실내 조명 {x0, z0, x1, z1, y0, y1}

    public Hamlets() {
        for (Spot s : spots()) {
            Village v = new Village(s);
            features.add(v);
            features.addAll(v.extraFeatures);
            lit.addAll(v.lit);
        }
        LinkedHashSet<Long> keys = new LinkedHashSet<>();
        for (Feature f : features) {
            for (int x = (f.minX - 1) >> 4; x <= (f.maxX + 1) >> 4; x++) {
                for (int z = (f.minZ - 1) >> 4; z <= (f.maxZ + 1) >> 4; z++) {
                    long k = ChunkPos.asLong(x, z);
                    keys.add(k);
                    byChunk.computeIfAbsent(k, q -> new ArrayList<>()).add(f);
                }
            }
        }
        for (long k : keys) chunks.add(new ChunkPos(k));
    }

    public int count() {
        return spots().size();
    }

    @Override public int cx() { return KingdomTerrain.CX; }
    @Override public int cz() { return KingdomTerrain.CZ; }
    @Override public int base() { return KingdomTerrain.SEA; }
    @Override public int radius() { return 0; }
    @Override public CityLayout city() { return null; }
    @Override public List<ChunkPos> chunkList() { return chunks; }
    @Override public int shapeMinY() { return 40; }
    @Override public int shapeMaxY() { return 200; }

    @Override
    public void buildColumn(Placer p, int x, int z) {}

    @Override
    public List<Feature> featuresIn(ChunkPos cp) {
        return byChunk.getOrDefault(cp.toLong(), Collections.emptyList());
    }

    @Override
    public int[] lightRange(int x, int z) {
        for (int[] q : lit) if (x >= q[0] && x <= q[2] && z >= q[1] && z <= q[3]) return new int[]{q[4], q[5]};
        return null;
    }

    // ───────────────────────── 마을 하나 ─────────────────────────

    /** 지을 건물: 중심, 앞 방향, 너비·깊이, 층수, 역할, 지면, 기둥 위(물가) 여부 */
    record Lot(int x, int z, Direction front, int W, int D, int floors, Role role, int y, boolean stilt) {}

    public static final class Village extends Feature {
        final Spot s;
        final Style st;
        final long seed;
        final List<double[]> streets = new ArrayList<>();      // {x0, z0, x1, z1, halfWidth}
        final List<Lot> lots = new ArrayList<>();
        final List<int[]> taken = new ArrayList<>();
        final List<int[]> fields = new ArrayList<>();          // {x0, z0, x1, z1, y}
        final List<int[]> piers = new ArrayList<>();           // {x, z, dirX, dirZ, len, waterY}
        final List<Feature> extraFeatures = new ArrayList<>();
        final List<int[]> lit = new ArrayList<>();
        int[] lift, lighthouse, cellar, tentsAt, altar, digpit;

        Village(Spot s) {
            super(s.x() - s.half() - 40, s.z() - s.half() - 40, s.x() + s.half() + 40, s.z() + s.half() + 40);
            this.s = s;
            this.st = s.style();
            this.seed = s.x() * 341873128712L + s.z() * 132897987541L + 77;
            this.groundY = s.y();
            java.util.Random r = new java.util.Random(seed);
            take(s.x() - 8, s.z() - 8, s.x() + 8, s.z() + 8);                    // 광장
            planStreets(r);
            for (Role role : s.type().specials) placeLot(r, role, true);
            for (int i = 0; i < s.type().houses; i++) placeLot(r, r.nextDouble() < 0.7 ? Role.HOME : Role.MEDIUM, false);
            for (Extra e : s.type().extras) placeExtra(r, e);
            if (s.type().fields) for (int i = 0; i < (s.type() == Type.FARM_BIG ? 10 : 4); i++) placeField(r);
            for (Lot l : lots) {
                int hw = Math.max(l.W(), l.D()) / 2 + 2;
                lit.add(new int[]{l.x() - hw, l.z() - hw, l.x() + hw, l.z() + hw, l.y() - 3, l.y() + l.floors() * 5 + 12});
            }
        }

        private int h(int x, int y, int z) {
            return Shapes.hash(x, y, z, seed);
        }

        private void take(int x0, int z0, int x1, int z1) {
            taken.add(new int[]{x0, z0, x1, z1});
        }

        private boolean free(int x0, int z0, int x1, int z1, int gap) {
            for (int[] q : taken) if (x0 - gap <= q[2] && x1 + gap >= q[0] && z0 - gap <= q[3] && z1 + gap >= q[1]) return false;
            return true;
        }

        private boolean stilts() {
            return st == Style.SWAMP || s.type() == Type.FISHING || s.type() == Type.FROST_PORT;
        }

        /** 거리: 가장 가까운 대륙 길 쪽으로 한 갈래 (길까지 이어짐) + 나머지는 고르게 */
        private void planStreets(java.util.Random r) {
            double best = 1e9, a0 = 0;
            double[] out = new double[1];
            for (int i = 0; i < 24; i++) {
                double a = Math.PI * 2 * i / 24;
                int x = s.x() + (int) (Math.cos(a) * (s.half() + 30)), z = s.z() + (int) (Math.sin(a) * (s.half() + 30));
                int ri = KingdomRoads.nearest(x, z, 400, out);
                if (ri >= 0 && out[0] < best) {
                    best = out[0];
                    a0 = a;
                }
            }
            int n = Math.max(1, s.type().lanes);
            double w = s.type().paved ? 2.5 : 1.5;
            for (int i = 0; i < n; i++) {
                double a = a0 + Math.PI * 2 * i / n + (i == 0 ? 0 : (r.nextDouble() - 0.5) * 0.4);
                double len = s.half() - 4;
                if (i == 0) {                                                            // 큰길: 대륙 길까지
                    for (int t = 10; t < s.half() + 90; t += 2) {
                        int x = s.x() + (int) (Math.cos(a) * t), z = s.z() + (int) (Math.sin(a) * t);
                        if (KingdomRoads.nearest(x, z, 0, null) >= 0) {
                            len = t;
                            break;
                        }
                    }
                }
                streets.add(new double[]{s.x(), s.z(), s.x() + Math.cos(a) * len, s.z() + Math.sin(a) * len, i == 0 ? w + 0.5 : w});
            }
        }

        private double streetDist(double x, double z) {
            double best = 1e9;
            for (double[] q : streets) {
                double vx = q[2] - q[0], vz = q[3] - q[1];
                double t = Math.max(0, Math.min(1, ((x - q[0]) * vx + (z - q[1]) * vz) / (vx * vx + vz * vz)));
                best = Math.min(best, Math.hypot(x - q[0] - vx * t, z - q[1] - vz * t) - q[4]);
            }
            return best;
        }

        private static int[] size(Role role, java.util.Random r) {
            return switch (role) {
                case HOME -> new int[]{7 + 2 * r.nextInt(2), 7 + r.nextInt(3), 1};
                case MEDIUM -> new int[]{11, 11 + r.nextInt(3), 2};
                case MANOR -> new int[]{17, 15, 2};
                case INN -> new int[]{13, 15, 2};
                case STABLE -> new int[]{15, 9, 1};
                case WAREHOUSE -> new int[]{13, 11, 1};
                case SMITHY -> new int[]{11, 9, 1};
                case CHAPEL -> new int[]{9, 15, 1};
                case GUILD -> new int[]{13, 13, 2};
                case BARRACKS -> new int[]{17, 11, 2};
                case ARMORY, LODGE, WATERMILL -> new int[]{11, 11, role == Role.WATERMILL ? 2 : 1};
                case STORE -> new int[]{9, 9, 1};
                case HIDDEN -> new int[]{7, 7, 1};
                case TREEHOUSE -> new int[]{13, 13, 1};
                case WOODSTORE -> new int[]{9, 7, 1};
            };
        }

        /** 거리 양옆 빈자리에 건물 하나 (특수 건물은 광장 가까운 곳부터) */
        private void placeLot(java.util.Random r, Role role, boolean special) {
            int[] sz = size(role, r);
            int W = sz[0], D = sz[1];
            List<double[]> cands = new ArrayList<>();
            if (role == Role.HIDDEN || role == Role.TREEHOUSE) {                          // 숲 사이 아무 데나
                for (int i = 0; i < 80; i++) {
                    double a = r.nextDouble() * Math.PI * 2, rr = 12 + r.nextDouble() * (s.half() - 14);
                    cands.add(new double[]{s.x() + Math.cos(a) * rr, s.z() + Math.sin(a) * rr, -Math.cos(a), -Math.sin(a), rr});
                }
            } else {
                for (double[] q : streets) {
                    double len = Math.hypot(q[2] - q[0], q[3] - q[1]);
                    double ux = (q[2] - q[0]) / len, uz = (q[3] - q[1]) / len;
                    for (double t = 11; t < Math.min(len, s.half()) - 3; t += 3) {
                        for (int side = -1; side <= 1; side += 2) {
                            double off = q[4] + 2 + D / 2.0;
                            double cx = q[0] + ux * t - uz * side * off, cz = q[1] + uz * t + ux * side * off;
                            cands.add(new double[]{cx, cz, uz * side, -ux * side, t});
                        }
                    }
                }
                if (role == Role.WATERMILL) cands.sort((a, b) -> Double.compare(KingdomTerrain.riverEdge((int) a[0], (int) a[1]), KingdomTerrain.riverEdge((int) b[0], (int) b[1])));
                else if (special) cands.sort((a, b) -> Double.compare(a[4], b[4]));
                else Collections.shuffle(cands, r);
            }
            for (double[] c : cands) {
                double fx = c[2], fz = c[3];
                Direction front = Math.abs(fx) > Math.abs(fz) ? (fx > 0 ? Direction.EAST : Direction.WEST) : (fz > 0 ? Direction.SOUTH : Direction.NORTH);
                Frame f = new Frame((int) Math.round(c[0]), (int) Math.round(c[1]), front, W, D);
                if (Math.max(Math.abs(f.minX - s.x()), Math.abs(f.maxX - s.x())) > s.half() || Math.max(Math.abs(f.minZ - s.z()), Math.abs(f.maxZ - s.z())) > s.half()) continue;
                if (!free(f.minX, f.minZ, f.maxX, f.maxZ, 2)) continue;
                if (!siteOk(f.minX - 1, f.minZ - 1, f.maxX + 1, f.maxZ + 1, role)) continue;
                boolean wet = wet(f.centerX(), f.centerZ());
                lots.add(new Lot(f.centerX(), f.centerZ(), front, W, D, sz[2], role, wet ? waterLevel(f.centerX(), f.centerZ()) : KingdomTerrain.column(f.centerX(), f.centerZ()).h(), wet));
                take(f.minX, f.minZ, f.maxX, f.maxZ);
                return;
            }
        }

        private static boolean wet(int x, int z) {
            KingdomTerrain.Column c = KingdomTerrain.column(x, z);
            return c.water() != Integer.MIN_VALUE && c.water() >= c.h();
        }

        private static int waterLevel(int x, int z) {
            KingdomTerrain.Column c = KingdomTerrain.column(x, z);
            return c.water() != Integer.MIN_VALUE ? c.water() : c.h();
        }

        /** 거리·대륙 길·경사·물 검사 */
        private boolean siteOk(int x0, int z0, int x1, int z1, Role role) {
            int h0 = Integer.MAX_VALUE, h1 = Integer.MIN_VALUE;
            int sx = Math.max(1, (x1 - x0) / 3), sz = Math.max(1, (z1 - z0) / 3);
            for (int x = x0; x <= x1; x += sx) {
                for (int z = z0; z <= z1; z += sz) {
                    if (streetDist(x, z) < 1) return false;
                    KingdomTerrain.Column c = KingdomTerrain.column(x, z);
                    if (c.road() > 0 && c.roadEdge() < 2) return false;
                    boolean w = c.water() != Integer.MIN_VALUE && c.water() >= c.h();
                    if (w && !stilts() && role != Role.WATERMILL) return false;
                    if (!w) {
                        h0 = Math.min(h0, c.h());
                        h1 = Math.max(h1, c.h());
                    }
                }
            }
            return h1 == Integer.MIN_VALUE || h1 - h0 <= 6;
        }

        private void placeExtra(java.util.Random r, Extra e) {
            switch (e) {
                case WINDMILL, WATCHTOWER -> {
                    for (int t = 0; t < 80; t++) {
                        double a = r.nextDouble() * Math.PI * 2, rr = s.half() * (0.55 + 0.4 * r.nextDouble());
                        int x = s.x() + (int) (Math.cos(a) * rr), z = s.z() + (int) (Math.sin(a) * rr);
                        int hw = e == Extra.WINDMILL ? 12 : 9;
                        if (!free(x - hw, z - hw, x + hw, z + hw, 2) || !siteOk(x - hw, z - hw, x + hw, z + hw, Role.STORE)) continue;
                        Feature f = e == Extra.WINDMILL ? new Outskirts.Windmill(x, z, r.nextLong()) : new Outskirts.Watchpost(x, z);
                        f.groundY = KingdomTerrain.column(x, z).h();
                        extraFeatures.add(f);
                        take(x - hw, z - hw, x + hw, z + hw);
                        return;
                    }
                }
                case LIGHTHOUSE -> {
                    lighthouse = new int[]{s.x() + 10, s.z()};
                    take(s.x() + 5, s.z() - 5, s.x() + 15, s.z() + 5);
                }
                case LIFT -> lift = spotFor(r, 9);
                case ALTAR -> altar = spotFor(r, 7);
                case CELLAR -> cellar = new int[]{s.x(), s.z()};
                case TENTS -> tentsAt = new int[]{s.x(), s.z()};
                case DIGPIT -> digpit = spotFor(r, 8);
                case WOODSTORE -> placeLot(r, Role.WOODSTORE, false);
                case TREEHOUSES -> {
                    for (int i = 0; i < 9; i++) placeLot(r, Role.TREEHOUSE, false);
                }
                case PIER -> {
                    for (int i = 0; i < 16; i++) {
                        double a = Math.PI * 2 * i / 16 + piers.size() * 0.2;
                        for (int t = 10; t < s.half() + 40; t += 2) {
                            int x = s.x() + (int) (Math.cos(a) * t), z = s.z() + (int) (Math.sin(a) * t);
                            if (wet(x, z) && KingdomTerrain.column(x, z).h() < KingdomTerrain.SEA - 2) {
                                boolean dup = false;
                                for (int[] q : piers) if (Math.hypot(q[0] - x, q[1] - z) < 14) dup = true;
                                if (dup) break;
                                int dx = (int) Math.round(Math.cos(a)), dz = (int) Math.round(Math.sin(a));
                                if (dx != 0 && dz != 0) dz = 0;
                                piers.add(new int[]{x - dx * 4, z - dz * 4, dx, dz, 24, waterLevel(x, z)});
                                return;
                            }
                        }
                    }
                }
            }
        }

        private int[] spotFor(java.util.Random r, int hw) {
            for (int t = 0; t < 120; t++) {
                double a = r.nextDouble() * Math.PI * 2, rr = 12 + r.nextDouble() * Math.max(1, s.half() - hw - 12);
                int x = s.x() + (int) (Math.cos(a) * rr), z = s.z() + (int) (Math.sin(a) * rr);
                if (!free(x - hw, z - hw, x + hw, z + hw, 2) || !siteOk(x - hw, z - hw, x + hw, z + hw, Role.STORE)) continue;
                take(x - hw, z - hw, x + hw, z + hw);
                return new int[]{x, z};
            }
            return null;
        }

        private void placeField(java.util.Random r) {
            for (int t = 0; t < 60; t++) {
                double a = r.nextDouble() * Math.PI * 2, rr = s.half() * (0.5 + 0.5 * r.nextDouble());
                int x = s.x() + (int) (Math.cos(a) * rr), z = s.z() + (int) (Math.sin(a) * rr);
                int w = 6, d = 4;
                if (!free(x - w, z - d, x + w, z + d, 2) || !siteOk(x - w, z - d, x + w, z + d, Role.STORE)) continue;
                fields.add(new int[]{x - w, z - d, x + w, z + d, KingdomTerrain.column(x, z).h()});
                take(x - w, z - d, x + w, z + d);
                return;
            }
        }

        // ───────────── 짓기 ─────────────

        @Override
        public void build(Placer p, CityLayout city) {
            final int b = groundY;
            // 거리 (지면을 따라, 물 위는 널빤지 다리), 길가 등불
            p.forColumns(minX, minZ, maxX, maxZ, (x, z) -> {
                double sd = streetDist(x, z);
                if (sd > 1.6) return;
                int top = p.naturalTop(x, z);
                BlockState t = p.get(x, top, z);
                boolean water = !t.getFluidState().isEmpty();
                int k = h(x, 1, z);
                if (water || st == Style.SWAMP) {
                    int wy = water ? top + 1 : top;
                    if (sd <= 0) {
                        p.set(x, wy, z, Blocks.SPRUCE_PLANKS.defaultBlockState());
                        p.clearAbove(x, z, wy + 1);
                        if (water && k % 9 == 0) for (int y = wy - 5; y < wy; y++) if (!solid(p.get(x, y, z))) p.set(x, y, z, st.post);
                    }
                    return;
                }
                if (sd > 0) {
                    if (k % 37 == 0) {
                        p.set(x, top + 1, z, st.fence);
                        p.set(x, top + 2, z, st.fence);
                        p.set(x, top + 3, z, Blocks.LANTERN.defaultBlockState());
                    }
                    return;
                }
                p.clearAbove(x, z, top + 1);
                p.set(x, top, z, streetBlock(k));
            });
            plaza(p, b);
            for (Lot l : lots) building(p, l);
            for (int[] q : fields) field(p, q);
            for (int[] q : piers) pier(p, q);
            if (s.defense() >= 2) wall(p);
            if (lift != null) cargoLift(p, lift[0], lift[1]);
            if (altar != null) altarAt(p, altar[0], altar[1]);
            if (lighthouse != null) lighthouseAt(p, lighthouse[0], lighthouse[1]);
            if (tentsAt != null) tents(p);
            if (digpit != null) digPit(p, digpit[0], digpit[1]);
            if (cellar != null) cellarAt(p, cellar[0], cellar[1]);
        }

        private BlockState streetBlock(int k) {
            if (s.type().paved) return k % 5 == 0 ? Blocks.ANDESITE.defaultBlockState() : k % 2 == 0 ? Blocks.STONE_BRICKS.defaultBlockState() : Blocks.POLISHED_ANDESITE.defaultBlockState();
            return switch (st) {
                case FROST -> k % 3 == 0 ? Blocks.PACKED_ICE.defaultBlockState() : k % 3 == 1 ? Blocks.GRAVEL.defaultBlockState() : Blocks.SNOW_BLOCK.defaultBlockState();
                case DWARF -> k % 2 == 0 ? Blocks.COBBLED_DEEPSLATE.defaultBlockState() : Blocks.GRAVEL.defaultBlockState();
                case ELF -> k % 3 == 0 ? Blocks.MOSS_BLOCK.defaultBlockState() : Blocks.DIRT_PATH.defaultBlockState();
                case FRONTIER -> k % 3 == 0 ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.GRAVEL.defaultBlockState();
                case REFUGEE -> k % 2 == 0 ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.MOSS_BLOCK.defaultBlockState();
                default -> k % 4 == 0 ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.DIRT_PATH.defaultBlockState();
            };
        }

        private void plaza(Placer p, int b) {
            final int cx = s.x(), cz = s.z();
            if (st == Style.SWAMP && wet(cx, cz)) {                                     // 수상촌: 물 위 광장
                final int wy = waterLevel(cx, cz) + 1;
                p.forColumns(cx - 7, cz - 7, cx + 7, cz + 7, (x, z) -> {
                    if (Math.hypot(x - cx, z - cz) > 7.3) return;
                    p.set(x, wy, z, Blocks.SPRUCE_PLANKS.defaultBlockState());
                    if (Math.floorMod(x + z, 5) == 0) for (int y = wy - 5; y < wy; y++) if (!solid(p.get(x, y, z))) p.set(x, y, z, st.post);
                });
                p.set(cx, wy + 1, cz, Blocks.CAMPFIRE.defaultBlockState());
                return;
            }
            p.forColumns(cx - 8, cz - 8, cx + 8, cz + 8, (x, z) -> {
                if (Math.hypot(x - cx, z - cz) > 8.3) return;
                RoadSites.pad(p, x, z, b, streetBlock(h(x, 2, z)));
            });
            switch (s.type()) {
                case STATION, FARM_BIG, FARM_SMALL, WATERMILL_FARM, HERB_SMALL -> well(p, cx, cz, b);
                case TRADE -> {
                    well(p, cx, cz, b);
                    stalls(p, cx, cz, b);
                }
                case ELF_PILGRIM -> {
                    p.set(cx, b + 1, cz, Blocks.FLOWERING_AZALEA.defaultBlockState());
                    for (int i = 0; i < 6; i++) {
                        double a = Math.PI * 2 * i / 6;
                        int x = cx + (int) Math.round(Math.cos(a) * 5), z = cz + (int) Math.round(Math.sin(a) * 5);
                        p.set(x, b + 1, z, Blocks.BIRCH_FENCE.defaultBlockState());
                        p.set(x, b + 2, z, Blocks.LANTERN.defaultBlockState());
                    }
                }
                case MILITARY -> {                                                       // 깃대 + 훈련용 허수아비·과녁
                    for (int y = 1; y <= 9; y++) p.set(cx, b + y, cz, Blocks.DARK_OAK_FENCE.defaultBlockState());
                    p.set(cx + 1, b + 8, cz, Royal.redBanner(Direction.EAST));
                    for (int sx = -1; sx <= 1; sx += 2) {
                        p.set(cx + sx * 5, b + 1, cz + 3, Blocks.HAY_BLOCK.defaultBlockState());
                        p.set(cx + sx * 5, b + 2, cz + 3, Blocks.CARVED_PUMPKIN.defaultBlockState());
                        p.set(cx + sx * 5, b + 1, cz - 3, Blocks.TARGET.defaultBlockState());
                    }
                }
                default -> {
                    p.set(cx, b + 1, cz, Blocks.CAMPFIRE.defaultBlockState());
                    for (int sx = -1; sx <= 1; sx += 2) {
                        p.set(cx + sx * 2, b + 1, cz, Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, sx > 0 ? Direction.EAST : Direction.WEST));
                        p.set(cx, b + 1, cz + sx * 2, Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, sx > 0 ? Direction.SOUTH : Direction.NORTH));
                    }
                }
            }
            p.set(cx + 6, b + 1, cz + 6, st.post);                                      // 마을 이정표 기둥 + 왕국 깃발
            p.set(cx + 6, b + 2, cz + 6, st.post);
            p.set(cx + 6, b + 3, cz + 6, Blocks.LANTERN.defaultBlockState());
            p.set(cx + 7, b + 2, cz + 6, Royal.mintBanner(Direction.EAST));
        }

        private void well(Placer p, int cx, int cz, int b) {
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                boolean rim = Math.abs(dx) == 1 || Math.abs(dz) == 1;
                p.set(cx + dx, b, cz + dz, rim ? Blocks.STONE_BRICKS.defaultBlockState() : Blocks.WATER.defaultBlockState());
                if (rim) p.set(cx + dx, b + 1, cz + dz, Blocks.STONE_BRICK_WALL.defaultBlockState());
            }
            for (int y = b - 3; y < b; y++) p.set(cx, y, cz, Blocks.WATER.defaultBlockState());
            for (int sx = -1; sx <= 1; sx += 2) for (int sz = -1; sz <= 1; sz += 2) for (int y = 2; y <= 3; y++) p.set(cx + sx, b + y, cz + sz, st.fence);
            BlockState cap = st.ridge.getBlock() instanceof LeavesBlock || st.ridge.is(Blocks.MOSS_BLOCK) ? Blocks.SPRUCE_PLANKS.defaultBlockState() : st.ridge;
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) p.set(cx + dx, b + 4, cz + dz, cap);
            p.set(cx, b + 3, cz, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        }

        private void stalls(Placer p, int cx, int cz, int b) {
            BlockState[] cloth = {Blocks.RED_WOOL.defaultBlockState(), Blocks.YELLOW_WOOL.defaultBlockState(), Blocks.LIGHT_BLUE_WOOL.defaultBlockState(), Blocks.LIME_WOOL.defaultBlockState()};
            Block[] goods = {Blocks.BARREL, Blocks.PUMPKIN, Blocks.HAY_BLOCK, Blocks.MELON, Blocks.COMPOSTER};
            for (int i = 0; i < 4; i++) {
                double a = Math.PI / 4 + Math.PI / 2 * i;
                int x = cx + (int) Math.round(Math.cos(a) * 5.5), z = cz + (int) Math.round(Math.sin(a) * 5.5);
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                    if (Math.abs(dx) == 1 && Math.abs(dz) == 1) for (int y = 1; y <= 2; y++) p.set(x + dx, b + y, z + dz, st.fence);
                    p.set(x + dx, b + 3, z + dz, cloth[i]);
                }
                p.set(x, b + 1, z, goods[Math.floorMod(i + h(x, 4, z), goods.length)].defaultBlockState());
            }
        }

        private void field(Placer p, int[] q) {
            int g = q[4];
            Block[] crops = {Blocks.WHEAT, Blocks.CARROTS, Blocks.POTATOES, Blocks.BEETROOTS};
            for (int x = q[0]; x <= q[2]; x++) for (int z = q[1]; z <= q[3]; z++) {
                if (!p.contains(x, z)) continue;
                boolean edge = x == q[0] || x == q[2] || z == q[1] || z == q[3];
                RoadSites.pad(p, x, z, g, edge ? Blocks.GRASS_BLOCK.defaultBlockState()
                        : Blocks.FARMLAND.defaultBlockState().setValue(net.minecraft.world.level.block.FarmBlock.MOISTURE, 7));
                if (edge) {
                    if (Math.floorMod(x + z, 2) == 0) p.set(x, g + 1, z, st.fence);
                } else if (z == (q[1] + q[3]) / 2) {
                    p.set(x, g, z, Blocks.WATER.defaultBlockState());
                } else if (s.type() == Type.HERB_SMALL) {
                    p.set(x, g, z, Blocks.GRASS_BLOCK.defaultBlockState());
                    p.set(x, g + 1, z, h(x, 6, z) % 2 == 0 ? Blocks.SWEET_BERRY_BUSH.defaultBlockState() : Blocks.FERN.defaultBlockState());
                } else {
                    CropBlock crop = (CropBlock) crops[Math.floorMod(q[0] + q[1], 4)];
                    p.set(x, g + 1, z, crop.getStateForAge(Math.min(crop.getMaxAge(), 2 + h(x, 7, z) % 6)));
                }
            }
        }

        /** 집·특수 건물 (지면 높이·역할·양식에 맞춤) */
        private void building(Placer p, Lot l) {
            if (l.role() == Role.TREEHOUSE) {
                treehouse(p, l);
                return;
            }
            if (l.role() == Role.WOODSTORE) {
                woodstore(p, l);
                return;
            }
            Frame f = new Frame(l.x(), l.z(), l.front(), l.W(), l.D());
            final int W = l.W(), D = l.D(), floors = l.floors(), FH = 5, top = floors * FH;
            final int fl = l.stilt() ? 3 : 0;
            final int bb = l.role() == Role.HIDDEN ? l.y() - 2 : l.y();                      // 은신 주택은 반쯤 땅에 묻힘
            for (int u = -1; u <= W; u++) for (int v = -2; v <= D; v++) {
                int x = f.x(u, v), z = f.z(u, v);
                if (!p.contains(x, z) || l.stilt()) continue;
                RoadSites.pad(p, x, z, l.y(), u >= 0 && u < W && v >= 0 && v < D ? st.floor : Blocks.GRASS_BLOCK.defaultBlockState());
            }
            boolean openFront = l.role() == Role.SMITHY || l.role() == Role.STABLE;
            for (int u = 0; u < W; u++) for (int v = 0; v < D; v++) {
                boolean eu = u == 0 || u == W - 1, ev = v == 0 || v == D - 1, corner = eu && ev;
                int x = f.x(u, v), z = f.z(u, v);
                if (l.stilt()) {
                    f.set(p, bb, u, fl, v, st.floor);
                    if ((corner || (eu && v == D / 2) || (ev && u == W / 2)) && p.contains(x, z))
                        for (int y = -6; y < fl; y++) if (!solid(p.get(x, bb + y, z))) f.set(p, bb, u, y, v, st.post);
                }
                if (l.role() == Role.HIDDEN) f.set(p, bb, u, 0, v, st.floor);
                for (int ly = fl + 1; ly <= fl + top; ly++) {
                    BlockState sv;
                    if (eu || ev) {
                        boolean post = corner || (ev && u % 4 == 0) || (eu && v % 4 == 0);
                        int inFloor = (ly - fl - 1) % FH + 1;
                        boolean band = (ly - fl) % FH == 0;
                        sv = post || band ? st.post : st.wall;
                        if (!post && !band && (inFloor == 2 || inFloor == 3) && ((ev && u % 3 == 1) || (eu && v % 3 == 1))) sv = Blocks.GLASS_PANE.defaultBlockState();
                        if (v == 0 && openFront && !post && ly - fl <= 3) sv = Blocks.AIR.defaultBlockState();
                        if (v == 0 && u == W / 2 && ly - fl <= 2 && !openFront) sv = Palette.door(f.back, ly - fl == 2);
                        if (l.role() == Role.WAREHOUSE && v == 0 && Math.abs(u - W / 2) == 1 && ly - fl <= 3) sv = Blocks.AIR.defaultBlockState();
                    } else {
                        sv = (ly - fl) % FH == 0 ? (u == 1 && v == D - 2 && ly < fl + top ? Blocks.AIR.defaultBlockState() : Blocks.SPRUCE_PLANKS.defaultBlockState())
                                : Blocks.AIR.defaultBlockState();
                    }
                    f.set(p, bb, u, ly, v, sv);
                }
            }
            for (int ly = fl + 1; ly < fl + top - 1 && floors > 1; ly++) f.set(p, bb, 1, ly, D - 2, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, f.front));
            int half = (W + 1) / 2;                                                      // 박공 지붕 (용마루 앞뒤)
            for (int k = 0; k <= half; k++) {
                int y = fl + top + k;
                for (int v = -1; v <= D; v++) {
                    int uL = k - 1, uR = W - k;
                    if (uL >= uR - 1) {
                        f.set(p, bb, uL, y, v, st.ridge);
                        if (uL != uR) f.set(p, bb, uR, y, v, st.ridge);
                        continue;
                    }
                    f.set(p, bb, uL, y, v, st.roofStairs.defaultBlockState().setValue(StairBlock.FACING, f.right));
                    f.set(p, bb, uR, y, v, st.roofStairs.defaultBlockState().setValue(StairBlock.FACING, f.left));
                    if (k > 0 && (v == 0 || v == D - 1)) for (int u = uL + 1; u < uR; u++) f.set(p, bb, u, y, v, st.wall);
                }
            }
            interior(p, f, l, bb, fl, W, D);
            if (!l.stilt()) {                                                            // 앞 등불
                f.set(p, l.y(), W / 2 - 2, 1, -1, st.fence);
                f.set(p, l.y(), W / 2 - 2, 2, -1, Blocks.LANTERN.defaultBlockState());
            } else {
                for (int y = 1; y <= fl; y++) f.set(p, bb, W / 2, y, -1, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, f.front));
            }
        }

        private void interior(Placer p, Frame f, Lot l, int b, int fl, int W, int D) {
            int y1 = fl + 1;
            BlockState lantern = Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
            f.set(p, b, W / 2, fl + 4, D / 2, lantern);
            switch (l.role()) {
                case INN -> {
                    for (int u = 2; u <= W - 3; u += 2) f.set(p, b, u, y1, D - 2, Blocks.BARREL.defaultBlockState());
                    for (int u = 3; u <= W - 4; u++) f.set(p, b, u, y1, D - 4, Blocks.SPRUCE_SLAB.defaultBlockState()
                            .setValue(net.minecraft.world.level.block.SlabBlock.TYPE, net.minecraft.world.level.block.state.properties.SlabType.TOP));
                    f.set(p, b, W - 2, y1, 1, Blocks.SMOKER.defaultBlockState());
                    for (int u = 2; u <= W - 3; u += 3) {                                 // 2층 객실
                        f.set(p, b, u, y1 + 5, 1, Palette.bed(f.front, true));
                        f.set(p, b, u, y1 + 5, 2, Palette.bed(f.front, false));
                    }
                    f.set(p, b, W / 2 + 1, fl + 9, D / 2, lantern);
                    f.set(p, l.y(), W / 2 + 2, 3, -1, Royal.redBanner(f.front));
                }
                case SMITHY -> {
                    f.set(p, b, 2, y1, D - 2, Blocks.ANVIL.defaultBlockState());
                    f.set(p, b, 4, y1, D - 2, Blocks.BLAST_FURNACE.defaultBlockState());
                    f.set(p, b, 5, y1, D - 2, Blocks.SMITHING_TABLE.defaultBlockState());
                    f.set(p, b, 6, y1, D - 2, Blocks.GRINDSTONE.defaultBlockState());
                    f.set(p, b, W - 3, y1, D - 2, Blocks.LAVA_CAULDRON.defaultBlockState());
                    for (int ly = y1; ly <= fl + 9; ly++) f.set(p, b, W - 2, ly, D - 1, Blocks.BRICKS.defaultBlockState());
                    f.set(p, b, W - 2, fl + 10, D - 1, Blocks.CAMPFIRE.defaultBlockState());
                }
                case STABLE -> {
                    for (int u = 1; u < W - 1; u += 3) f.set(p, b, u, y1, D / 2, st.fence);
                    for (int u = 2; u < W - 1; u += 3) f.set(p, b, u, y1, D - 2, Blocks.HAY_BLOCK.defaultBlockState());
                }
                case WAREHOUSE, STORE -> {
                    for (int u = 1; u < W - 1; u++) for (int v = D - 3; v <= D - 2; v++) if (h(f.x(u, v), 9, f.z(u, v)) % 3 != 0)
                        f.set(p, b, u, y1, v, h(f.x(u, v), 10, f.z(u, v)) % 4 == 0 ? Blocks.HAY_BLOCK.defaultBlockState() : Blocks.BARREL.defaultBlockState());
                    OldCapital.chest(p, f.x(1, 1), b + y1, f.z(1, 1), f.right, seed ^ l.x(), s.type() == Type.DIG_CAMP ? BuiltInLootTables.DESERT_PYRAMID
                            : s.type() == Type.HIDEOUT ? BuiltInLootTables.SIMPLE_DUNGEON : BuiltInLootTables.VILLAGE_PLAINS_HOUSE);
                }
                case CHAPEL -> {
                    for (int v = 2; v <= D - 4; v += 2) for (int u = 1; u < W - 1; u++) if (u != W / 2)
                        f.set(p, b, u, y1, v, Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, f.front));
                    f.set(p, b, W / 2, y1, D - 2, Blocks.LECTERN.defaultBlockState());
                    int tt = fl + 5 + (W + 1) / 2;                                       // 앞쪽 종탑
                    for (int ly = tt - 2; ly <= tt + 6; ly++) for (int du = -1; du <= 1; du++) for (int dv = 0; dv <= 2; dv++) {
                        boolean edge = Math.abs(du) == 1 || dv == 0 || dv == 2;
                        BlockState sv = ly >= tt + 5 ? st.ridge : edge ? (ly >= tt + 2 && (du == 0 || dv == 1) ? Blocks.AIR.defaultBlockState() : st.wall) : Blocks.AIR.defaultBlockState();
                        f.set(p, b, W / 2 + du, ly, dv, sv);
                    }
                    f.set(p, b, W / 2, tt + 3, 1, Blocks.BELL.defaultBlockState());
                    Royal.pinnacle(p, f.x(W / 2, 1), f.z(W / 2, 1), b + tt + 7, 3);
                }
                case GUILD, LODGE -> {
                    f.set(p, b, 2, y1, D - 2, Blocks.LECTERN.defaultBlockState());
                    f.set(p, b, 3, y1, D - 2, Blocks.CARTOGRAPHY_TABLE.defaultBlockState());
                    f.set(p, b, 4, y1, D - 2, l.role() == Role.LODGE ? Blocks.LOOM.defaultBlockState() : Blocks.BARREL.defaultBlockState());
                    for (int u = 6; u < W - 1; u += 2) {
                        f.set(p, b, u, y1, D - 2, Palette.bed(f.back, true));
                        f.set(p, b, u, y1, D - 3, Palette.bed(f.back, false));
                    }
                    f.set(p, l.y(), W / 2 + 2, 3, -1, Royal.mintBanner(f.front));
                }
                case BARRACKS -> {
                    for (int u = 2; u < W - 1; u += 2) {
                        f.set(p, b, u, y1, D - 2, Palette.bed(f.back, true));
                        f.set(p, b, u, y1, D - 3, Palette.bed(f.back, false));
                        if (l.floors() > 1) {
                            f.set(p, b, u, y1 + 5, D - 2, Palette.bed(f.back, true));
                            f.set(p, b, u, y1 + 5, D - 3, Palette.bed(f.back, false));
                        }
                    }
                    f.set(p, l.y(), W / 2 + 2, 3, -1, Royal.redBanner(f.front));
                }
                case ARMORY -> {
                    f.set(p, b, 2, y1, D - 2, Blocks.SMITHING_TABLE.defaultBlockState());
                    f.set(p, b, 3, y1, D - 2, Blocks.GRINDSTONE.defaultBlockState());
                    f.set(p, b, 4, y1, D - 2, Blocks.FLETCHING_TABLE.defaultBlockState());
                    OldCapital.chest(p, f.x(W - 2, D - 2), b + y1, f.z(W - 2, D - 2), f.front, seed ^ 99, BuiltInLootTables.VILLAGE_ARMORER);
                    OldCapital.chest(p, f.x(W - 3, D - 2), b + y1, f.z(W - 3, D - 2), f.front, seed ^ 98, BuiltInLootTables.VILLAGE_WEAPONSMITH);
                }
                case WATERMILL -> {
                    f.set(p, b, 2, y1, D - 2, Blocks.GRINDSTONE.defaultBlockState());
                    f.set(p, b, 3, y1, D - 2, Blocks.HAY_BLOCK.defaultBlockState());
                    f.set(p, b, 4, y1, D - 2, Blocks.COMPOSTER.defaultBlockState());
                    int wu = W + 1, cv = D / 2;                                         // 오른쪽 벽 바깥 물레바퀴
                    BlockState axle = Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, f.right.getAxis());
                    for (int v = cv - 4; v <= cv + 4; v++) for (int ly = -3; ly <= 6; ly++) {
                        double d = Math.hypot(v - cv, ly - 1);
                        if (d > 2.6 && d <= 3.6) f.set(p, b, wu, ly, v, axle);
                        else if (d <= 2.6 && (v == cv || ly == 1)) f.set(p, b, wu, ly, v, Blocks.SPRUCE_PLANKS.defaultBlockState());
                    }
                    f.set(p, b, W, 1, cv, axle);
                }
                case HIDDEN -> {
                    f.set(p, b, 1, y1, D - 2, Palette.bed(f.back, true));
                    f.set(p, b, 1, y1, D - 3, Palette.bed(f.back, false));
                    f.set(p, b, W - 2, y1, D - 2, Blocks.BARREL.defaultBlockState());
                    f.set(p, b, W - 2, y1, 1, Blocks.CRAFTING_TABLE.defaultBlockState());
                }
                case MANOR -> {
                    f.set(p, b, 2, y1, D - 2, Royal.redBed(f.back, true));
                    f.set(p, b, 2, y1, D - 3, Royal.redBed(f.back, false));
                    f.set(p, b, 4, y1, D - 2, Blocks.BOOKSHELF.defaultBlockState());
                    f.set(p, b, 5, y1, D - 2, Blocks.BOOKSHELF.defaultBlockState());
                    f.set(p, b, W - 3, y1, D - 2, Blocks.FURNACE.defaultBlockState());
                    OldCapital.chest(p, f.x(W - 2, D - 2), b + y1, f.z(W - 2, D - 2), f.front, seed ^ 77, BuiltInLootTables.VILLAGE_PLAINS_HOUSE);
                    for (int u = 3; u <= W - 4; u++) for (int v = 2; v <= 4; v++) f.set(p, b, u, y1, v, Blocks.RED_CARPET.defaultBlockState());
                    for (int u = -2; u <= W + 1; u++) if (Math.abs(u - W / 2) > 1) f.set(p, l.y(), u, 1, -3, st.fence);   // 앞마당 담장
                }
                default -> {                                                             // 일반 주택
                    f.set(p, b, 1, y1, D - 2, Palette.bed(f.back, true));
                    f.set(p, b, 1, y1, D - 3, Palette.bed(f.back, false));
                    f.set(p, b, W - 2, y1, D - 2, Blocks.CRAFTING_TABLE.defaultBlockState());
                    f.set(p, b, W - 3, y1, D - 2, st == Style.FROST ? Blocks.SMOKER.defaultBlockState() : Blocks.FURNACE.defaultBlockState());
                    if (l.role() == Role.MEDIUM) {
                        f.set(p, b, W - 2, y1, 2, Blocks.BARREL.defaultBlockState());
                        f.set(p, b, W - 2, y1 + 5, D - 2, Palette.bed(f.back, true));
                        f.set(p, b, W - 2, y1 + 5, D - 3, Palette.bed(f.back, false));
                        f.set(p, b, W / 2, fl + 9, D / 2, lantern);
                    }
                }
            }
        }

        /** 엘프 나무집: 자작나무 줄기 위 둥근 마루, 방해석 벽, 꽃잎 지붕, 사다리 */
        private void treehouse(Placer p, Lot l) {
            int tx = l.x(), tz = l.z(), g = l.y(), floor = g + 9;
            BlockState leaf = Blocks.BIRCH_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
            p.forColumns(tx - 6, tz - 6, tx + 7, tz + 7, (x, z) -> {
                double d = Math.hypot(x - tx - 0.5, z - tz - 0.5);
                boolean trunk = (x == tx || x == tx + 1) && (z == tz || z == tz + 1);
                if (trunk) {
                    for (int y = g - 1; y <= g + 17; y++) p.set(x, y, z, Blocks.BIRCH_LOG.defaultBlockState());
                    return;
                }
                if (d <= 5.5) p.set(x, floor, z, d > 4.6 ? Blocks.STRIPPED_BIRCH_WOOD.defaultBlockState() : Blocks.BIRCH_PLANKS.defaultBlockState());
                if (d > 4.6 && d <= 5.5) p.set(x, floor + 1, z, Blocks.BIRCH_FENCE.defaultBlockState());
                if (d > 2.6 && d <= 3.5) for (int y = floor + 1; y <= floor + 3; y++) {
                    boolean door = z > tz + 2 && x == tx && y <= floor + 2;
                    p.set(x, y, z, door ? Blocks.AIR.defaultBlockState() : y == floor + 2 && Math.floorMod(x + z, 3) == 0 ? Blocks.GLASS_PANE.defaultBlockState() : Blocks.CALCITE.defaultBlockState());
                }
                for (int k = 0; k <= 4; k++) if (d <= 4.5 - k && d > 3.2 - k) p.set(x, floor + 4 + k, z, k % 2 == 0 ? leaf : Royal.HEDGE_FLOWER);
                if (d <= 3.4) p.set(x, floor + 4, z, Blocks.BIRCH_PLANKS.defaultBlockState());
                for (int y = g + 15; y <= g + 19; y++) if (Math.hypot(d, (y - (g + 17)) * 1.3) < 3.2) p.set(x, y, z, leaf);
            });
            for (int y = g + 1; y <= floor + 1; y++) p.set(tx + 2, y, tz, Palette.ladder(Direction.EAST));
            p.set(tx - 2, floor + 1, tz, Palette.bed(Direction.SOUTH, false));
            p.set(tx - 2, floor + 1, tz + 1, Palette.bed(Direction.SOUTH, true));
            p.set(tx + 2, floor + 3, tz + 2, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
            p.set(tx, floor + 1, tz - 2, Blocks.FLOWER_POT.defaultBlockState());
        }

        /** 장작 창고: 기둥 4개 위 판자 지붕, 그 아래 쌓은 통나무 */
        private void woodstore(Placer p, Lot l) {
            Frame f = new Frame(l.x(), l.z(), l.front(), l.W(), l.D());
            BlockState log = Blocks.SPRUCE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, f.right.getAxis());
            for (int u = 0; u < l.W(); u++) for (int v = 0; v < l.D(); v++) {
                int x = f.x(u, v), z = f.z(u, v);
                if (!p.contains(x, z)) continue;
                RoadSites.pad(p, x, z, l.y(), Blocks.COARSE_DIRT.defaultBlockState());
                boolean corner = (u == 0 || u == l.W() - 1) && (v == 0 || v == l.D() - 1);
                if (corner) for (int y = 1; y <= 4; y++) f.set(p, l.y(), u, y, v, st.post);
                f.set(p, l.y(), u, 5, v, Blocks.SPRUCE_SLAB.defaultBlockState());
                if (!corner && u > 0 && u < l.W() - 1 && v > 0 && v < l.D() - 1) for (int y = 1; y <= 2 + (u + v) % 2; y++) f.set(p, l.y(), u, y, v, log);
            }
        }

        private void pier(Placer p, int[] q) {
            int wy = q[5] + 1;
            for (int t = 0; t <= q[4]; t++) {
                for (int side = -1; side <= 1; side++) {
                    int x = q[0] + q[2] * t + (q[2] == 0 ? side : 0), z = q[1] + q[3] * t + (q[3] == 0 ? side : 0);
                    if (!p.contains(x, z)) continue;
                    p.set(x, wy, z, Blocks.SPRUCE_PLANKS.defaultBlockState());
                    p.clearAbove(x, z, wy + 1);
                    if (side != 0 && t % 4 == 0) {
                        for (int y = wy - 8; y < wy; y++) if (!solid(p.get(x, y, z))) p.set(x, y, z, Blocks.SPRUCE_LOG.defaultBlockState());
                        p.set(x, wy + 1, z, Blocks.SPRUCE_FENCE.defaultBlockState());
                        if (t % 8 == 0) p.set(x, wy + 2, z, Blocks.LANTERN.defaultBlockState());
                    }
                }
            }
            p.set(q[0] + q[2] * q[4], wy + 1, q[1] + q[3] * q[4], Blocks.BARREL.defaultBlockState());
        }

        /** 방어: D2 = 통나무 목책, D3 = 돌 성벽 + 모서리 탑. 거리·대륙 길이 지나는 곳은 문 */
        private void wall(Placer p) {
            final int cx = s.x(), cz = s.z(), r = s.half() - 2;
            final boolean stone = s.defense() >= 3;
            p.forColumns(cx - r - 1, cz - r - 1, cx + r + 1, cz + r + 1, (x, z) -> {
                double d = Math.hypot(x - cx, z - cz);
                if (d < r - 0.5 || d > r + 0.5) return;
                if (streetDist(x, z) < 2.5) return;
                KingdomTerrain.Column c = KingdomTerrain.column(x, z);
                if (c.road() > 0 && c.roadEdge() < 2) return;
                int top = p.naturalTop(x, z);
                if (!p.get(x, top, z).getFluidState().isEmpty()) return;
                int hgt = stone ? 6 : 4;
                p.clearAbove(x, z, top + 1);
                for (int y = 1; y <= hgt; y++) p.set(x, top + y, z, stone ? (y == hgt && Math.floorMod(x + z, 2) == 0 ? Blocks.AIR.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState())
                        : Blocks.SPRUCE_LOG.defaultBlockState());
                if (!stone && h(x, 11, z) % 2 == 0) p.set(x, top + hgt + 1, z, Blocks.SPRUCE_FENCE.defaultBlockState());
            });
            if (stone) for (int i = 0; i < 4; i++) {                                     // 모서리 탑
                double a = Math.PI / 4 + Math.PI / 2 * i;
                int tx = cx + (int) Math.round(Math.cos(a) * r), tz = cz + (int) Math.round(Math.sin(a) * r);
                if (!p.intersects(tx - 4, tz - 4, tx + 4, tz + 4)) continue;
                int g = KingdomTerrain.column(tx, tz).h();
                for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                    boolean e = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                    for (int y = 0; y <= 11; y++) p.set(tx + dx, g + y, tz + dz, e || y == 0 || y == 9 ? Blocks.STONE_BRICKS.defaultBlockState() : Blocks.AIR.defaultBlockState());
                    if (e && Math.floorMod(dx + dz, 2) == 0) p.set(tx + dx, g + 12, tz + dz, Blocks.STONE_BRICK_WALL.defaultBlockState());
                }
                for (int y = 1; y < 9; y++) p.set(tx, g + y, tz + 1, Palette.ladder(Direction.NORTH));
                p.set(tx, g + 9, tz + 1, Blocks.AIR.defaultBlockState());
                p.set(tx, g + 10, tz, Blocks.LANTERN.defaultBlockState());
                p.set(tx + 3, g + 8, tz, Royal.redBanner(Direction.EAST));
            }
        }

        /** 드워프 화물 승강기: 통나무 탑(높이 18), 비계, 사슬, 기중기 팔, 광석 */
        private void cargoLift(Placer p, int x0, int z0) {
            int g = KingdomTerrain.column(x0, z0).h();
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                RoadSites.pad(p, x0 + dx, z0 + dz, g, Blocks.COBBLED_DEEPSLATE.defaultBlockState());
                boolean corner = Math.abs(dx) == 2 && Math.abs(dz) == 2;
                for (int y = 1; y <= 18; y++) {
                    if (corner) p.set(x0 + dx, g + y, z0 + dz, Blocks.SPRUCE_LOG.defaultBlockState());
                    else if ((Math.abs(dx) == 2 || Math.abs(dz) == 2) && y % 6 == 0) p.set(x0 + dx, g + y, z0 + dz, Blocks.SPRUCE_PLANKS.defaultBlockState());
                }
                p.set(x0 + dx, g + 18, z0 + dz, Blocks.SPRUCE_PLANKS.defaultBlockState());
            }
            for (int k = 1; k <= 6; k++) p.set(x0 + 2 + k, g + 18, z0, Blocks.SPRUCE_PLANKS.defaultBlockState());
            for (int y = g + 8; y < g + 18; y++) p.set(x0 + 8, y, z0, Blocks.CHAIN.defaultBlockState());
            p.set(x0 + 8, g + 7, z0, Blocks.IRON_BLOCK.defaultBlockState());
            for (int y = g + 1; y < g + 18; y++) p.set(x0, y, z0 - 1, Blocks.CHAIN.defaultBlockState());
            p.set(x0, g + 1, z0, Blocks.IRON_ORE.defaultBlockState());
            p.set(x0 + 1, g + 1, z0, Blocks.COPPER_ORE.defaultBlockState());
            for (int y = g + 1; y < g + 18; y++) p.set(x0 - 1, y, z0 + 1, Palette.ladder(Direction.NORTH));
        }

        private void altarAt(Placer p, int x0, int z0) {
            int g = KingdomTerrain.column(x0, z0).h();
            p.forColumns(x0 - 6, z0 - 6, x0 + 6, z0 + 6, (x, z) -> {
                double d = Math.hypot(x - x0, z - z0);
                if (d > 6.3) return;
                RoadSites.pad(p, x, z, g, d < 2 ? Blocks.CALCITE.defaultBlockState() : Blocks.MOSS_BLOCK.defaultBlockState());
                if (d > 4.5 && h(x, 12, z) % 2 == 0) p.set(x, g + 1, z, Royal.HEDGE_FLOWER);
            });
            for (int y = 1; y <= 3; y++) p.set(x0, g + y, z0, y == 3 ? Blocks.AMETHYST_BLOCK.defaultBlockState() : Blocks.CALCITE.defaultBlockState());
            p.set(x0, g + 4, z0, Blocks.AMETHYST_CLUSTER.defaultBlockState());
            for (int i = 0; i < 4; i++) {
                double a = Math.PI / 2 * i;
                p.set(x0 + (int) Math.round(Math.cos(a) * 3), g + 1, z0 + (int) Math.round(Math.sin(a) * 3), Blocks.END_ROD.defaultBlockState());
            }
        }

        /** 해안 등대: 흰·빨강 줄무늬 원통(높이 26), 꼭대기 등불실 */
        private void lighthouseAt(Placer p, int x0, int z0) {
            int g = KingdomTerrain.column(x0, z0).h();
            p.forColumns(x0 - 4, z0 - 4, x0 + 4, z0 + 4, (x, z) -> {
                double d = Math.hypot(x - x0, z - z0);
                if (d > 4.2) return;
                RoadSites.pad(p, x, z, g, Blocks.STONE_BRICKS.defaultBlockState());
                for (int y = 1; y <= 26; y++) {
                    BlockState sv = d > 3.0 ? ((y / 4) % 2 == 0 ? Blocks.WHITE_CONCRETE.defaultBlockState() : Blocks.RED_CONCRETE.defaultBlockState()) : Blocks.AIR.defaultBlockState();
                    if (d > 3.0 && y % 8 == 3 && (x == x0 || z == z0)) sv = Blocks.GLASS_PANE.defaultBlockState();
                    if (d > 3.0 && y <= 2 && x == x0 && z > z0) sv = Blocks.AIR.defaultBlockState();
                    p.set(x, g + y, z, sv);
                }
                p.set(x, g + 27, z, Blocks.SMOOTH_STONE.defaultBlockState());
                if (d > 2.5 && d <= 3.5) for (int y = 28; y <= 30; y++) p.set(x, g + y, z, y == 30 ? Blocks.RED_CONCRETE.defaultBlockState() : Blocks.GLASS.defaultBlockState());
                if (d <= 2.5) p.set(x, g + 31, z, Blocks.RED_CONCRETE.defaultBlockState());
            });
            p.set(x0, g + 28, z0, Blocks.SEA_LANTERN.defaultBlockState());
            p.set(x0, g + 29, z0, Blocks.GLOWSTONE.defaultBlockState());
            for (int y = 1; y <= 26; y++) p.set(x0, g + y, z0 - 2, Palette.ladder(Direction.SOUTH));
        }

        /** 발굴 야영지 천막 7개 (가운데 큰 초록 천막 = 조사본부) */
        private void tents(Placer p) {
            for (int i = 0; i < 7; i++) {
                double a = Math.PI * 2 * i / 7;
                int tx = s.x() + (int) Math.round(Math.cos(a) * 15), tz = s.z() + (int) Math.round(Math.sin(a) * 15);
                int g = KingdomTerrain.column(tx, tz).h();
                boolean hq = i == 0;
                int hw = hq ? 4 : 3;
                for (int dx = -hw; dx <= hw; dx++) for (int dz = -2; dz <= 2; dz++) {
                    int ad = Math.abs(dz);
                    RoadSites.pad(p, tx + dx, tz + dz, g, Blocks.COARSE_DIRT.defaultBlockState());
                    for (int y = 1; y <= 3 - ad; y++) if (y == 3 - ad || (Math.abs(dx) == hw && !(dx == -hw && ad == 0)))
                        p.set(tx + dx, g + y, tz + dz, hq ? Blocks.GREEN_WOOL.defaultBlockState() : Blocks.WHITE_WOOL.defaultBlockState());
                }
                p.set(tx, g + 1, tz, hq ? Blocks.CARTOGRAPHY_TABLE.defaultBlockState() : Blocks.BARREL.defaultBlockState());
                if (hq) p.set(tx + 1, g + 1, tz, Blocks.LECTERN.defaultBlockState());
                else {
                    p.set(tx + 1, g + 1, tz, Palette.bed(Direction.EAST, true));
                    p.set(tx, g + 1, tz + 1, Blocks.LANTERN.defaultBlockState());
                }
            }
        }

        /** 발굴 구덩이: 계단식으로 판 사각 구덩이, 의심스러운 모래·자갈, 깨진 석재 */
        private void digPit(Placer p, int x0, int z0) {
            int g = KingdomTerrain.column(x0, z0).h();
            p.forColumns(x0 - 7, z0 - 7, x0 + 7, z0 + 7, (x, z) -> {
                int d = Math.max(Math.abs(x - x0), Math.abs(z - z0));
                int depth = Math.max(0, 7 - d);
                p.clearAbove(x, z, g + 1);
                for (int y = g - depth + 1; y <= g; y++) p.set(x, y, z, Blocks.AIR.defaultBlockState());
                int k = h(x, 13, z) % 9;
                p.set(x, g - depth, z, k == 0 ? Blocks.SUSPICIOUS_GRAVEL.defaultBlockState() : k == 1 ? Blocks.SUSPICIOUS_SAND.defaultBlockState()
                        : k == 2 ? Blocks.CHISELED_STONE_BRICKS.defaultBlockState() : k < 5 ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState() : Blocks.GRAVEL.defaultBlockState());
                if (d == 7 && Math.floorMod(x + z, 4) == 0) {
                    p.set(x, g + 1, z, Blocks.SPRUCE_FENCE.defaultBlockState());
                    if (Math.floorMod(x + z, 8) == 0) p.set(x, g + 2, z, Blocks.LANTERN.defaultBlockState());
                }
            });
        }

        /** 피난민 은신처의 지하 식량고와 비밀 통로 (광장 아래, 뚜껑 문 입구 → 서쪽 덤불로 나가는 통로) */
        private void cellarAt(Placer p, int x0, int z0) {
            int g = KingdomTerrain.column(x0, z0).h(), fy = g - 8;
            p.forColumns(x0 - 6, z0 - 5, x0 + 6, z0 + 5, (x, z) -> {
                boolean edge = Math.abs(x - x0) == 6 || Math.abs(z - z0) == 5;
                for (int y = fy; y <= fy + 4; y++) p.set(x, y, z, edge || y == fy || y == fy + 4 ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState() : Blocks.AIR.defaultBlockState());
                if (!edge && Math.abs(z - z0) >= 3 && h(x, 14, z) % 2 == 0) p.set(x, fy + 1, z, h(x, 15, z) % 3 == 0 ? Blocks.HAY_BLOCK.defaultBlockState() : Blocks.BARREL.defaultBlockState());
            });
            for (int y = fy + 1; y <= g; y++) p.set(x0 + 4, y, z0, Palette.ladder(Direction.WEST));
            for (int y = fy + 5; y <= g; y++) p.set(x0 + 5, y, z0, Blocks.MOSSY_STONE_BRICKS.defaultBlockState());
            p.set(x0 + 4, g + 1, z0, Blocks.SPRUCE_TRAPDOOR.defaultBlockState());
            p.set(x0, fy + 3, z0, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
            OldCapital.chest(p, x0 - 5, fy + 1, z0, Direction.EAST, seed ^ 0xCE, BuiltInLootTables.SIMPLE_DUNGEON);
            for (int t = 7; t <= 34; t++) for (int dz = -1; dz <= 1; dz++) for (int y = fy; y <= fy + 3; y++) {
                boolean shell = dz != 0 || y == fy || y == fy + 3;
                p.set(x0 - t, y, z0 + dz, shell ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
            }
            int ex = x0 - 35, eg = KingdomTerrain.column(ex, z0).h();
            for (int y = fy + 1; y <= eg; y++) {
                p.set(ex, y, z0, Blocks.AIR.defaultBlockState());
                p.set(ex, y, z0 + 1, Blocks.COBBLESTONE.defaultBlockState());
                p.set(ex, y, z0 - 1, Blocks.COBBLESTONE.defaultBlockState());
                p.set(ex - 1, y, z0, Blocks.COBBLESTONE.defaultBlockState());
            }
            for (int y = fy + 1; y <= eg; y++) p.set(ex + 0, y, z0, Palette.ladder(Direction.EAST));
            p.set(ex + 1, eg + 1, z0 + 1, Blocks.AZALEA.defaultBlockState());
        }

        @Override
        public boolean needsSpawn() {
            return true;
        }

        @Override
        public void spawn(ServerLevel level, CityLayout city) {
            int n = Math.max(2, Math.min(10, lots.size() / 2 + 1));
            for (int i = 0; i < n; i++) {
                Mob m = EntityType.VILLAGER.spawn(level, new BlockPos(s.x() + (i % 5) - 2, groundY + 1, s.z() + 3 + i / 5), MobSpawnType.STRUCTURE);
                if (m == null) continue;
                m.setPersistenceRequired();
                m.setCustomName(Component.literal(s.name() + " " + st.people));
            }
            for (Lot l : lots) {
                if (l.role() != Role.STABLE) continue;
                for (int i = 0; i < 2; i++) {
                    Mob hrs = EntityType.HORSE.spawn(level, new BlockPos(l.x() + i, l.y() + 1, l.z()), MobSpawnType.STRUCTURE);
                    if (hrs != null) hrs.setPersistenceRequired();
                }
            }
            if (s.type() == Type.FARM_BIG || s.type() == Type.FARM_SMALL) {
                for (int i = 0; i < 4; i++) EntityType.SHEEP.spawn(level, new BlockPos(s.x() - 10 + i, groundY + 1, s.z() - 10), MobSpawnType.STRUCTURE);
            }
            if (s.defense() >= 3) {
                Mob g = EntityType.IRON_GOLEM.spawn(level, new BlockPos(s.x() + 3, groundY + 1, s.z() + 3), MobSpawnType.STRUCTURE);
                if (g != null) {
                    g.setPersistenceRequired();
                    g.setCustomName(Component.literal(s.name() + " 수비 골렘"));
                }
            }
        }

        @Override
        public long anchorChunk() {
            return ChunkPos.asLong(s.x() >> 4, s.z() >> 4);
        }
    }
}
