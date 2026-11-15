package io.github.aswq0258.magiccity.site;

import io.github.aswq0258.magiccity.build.BuildLayout;
import io.github.aswq0258.magiccity.build.Placer;
import io.github.aswq0258.magiccity.city.CityLayout;
import io.github.aswq0258.magiccity.city.Deco;
import io.github.aswq0258.magiccity.city.Royal;
import io.github.aswq0258.magiccity.city.Shapes;
import io.github.aswq0258.magiccity.city.feature.Feature;
import io.github.aswq0258.magiccity.city.feature.Frame;
import io.github.aswq0258.magiccity.world.KingdomTerrain;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 몰락한 옛 왕도 — 옛 왕궁 터에서 네더 포탈이 열리며 멸망한 수도 (마스터플랜의 남서쪽 끝, 왕국 대륙 차원)
 *
 *  - 반쯤 무너진 원형 성벽(r 165~170)과 부러진 탑, 북동쪽 성문 (폐왕도가 들어오는 방향)
 *  - 재와 균열로 덮인 거리, 불탄 집 수십 채, 불탄 대성당, 쓰러진 거상, 용암 구덩이
 *  - 가운데 옛 왕궁 터: 무너진 아치 사이 광장에 거대한 봉인된 마계문(흑요석·우는 흑요석 틀, 9x11)
 *    중심으로 갈수록 네더랙·진홍 균사·마그마가 번진 오염
 *  - 네더 몹 스포너 (좀비 피글린·마그마 큐브·블레이즈·위더 해골)
 *  - 바깥 북동쪽 폐왕도 위: 연합군 봉쇄 요새 (안전 거점: 막사·침대·대장간·양조대·보급 상자)
 * 보스 "심연의 문지기" 와 포탈 활성화는 boss.GateKeeper 가 담당.
 */
public final class OldCapital implements BuildLayout {
    public static final int OX = KingdomTerrain.OLD_X, OZ = KingdomTerrain.OLD_Z, Y = KingdomTerrain.OLD_Y;
    public static final int WALL_IN = 165, WALL_OUT = 170;
    /** 포탈: 안쪽 폭 9(x) x 높이 11, 틀은 z = OZ 줄 */
    public static final int PORTAL_W = 9, PORTAL_H = 11;
    public static final double GATE_ANGLE = Math.toRadians(-45);   // 북동쪽 (도면 기준 x+, z-)

    private final int cx, cz, radius;
    private final long seed;
    private final List<Feature> features = new ArrayList<>();
    private final Map<Long, List<Feature>> byChunk = new HashMap<>();
    private final List<int[]> taken = new ArrayList<>();

    public OldCapital(long seed) {
        this.seed = seed;
        this.cx = (OX + KingdomTerrain.FORT_X) / 2;
        this.cz = (OZ + KingdomTerrain.FORT_Z) / 2;
        this.radius = (int) Math.max(Math.hypot(OX - cx, OZ - cz) + WALL_OUT + 6, Math.hypot(KingdomTerrain.FORT_X - cx, KingdomTerrain.FORT_Z - cz) + 40);

        RandomSource rand = RandomSource.create(seed);
        features.add(new PortalPlaza());
        take(OX - 32, OZ - 32, OX + 32, OZ + 32);
        features.add(new BurntCathedral(OX + 70, OZ + 40));
        take(OX + 46, OZ + 26, OX + 96, OZ + 56);
        features.add(new FallenColossus(OX - 60, OZ - 75));
        take(OX - 82, OZ - 85, OX - 42, OZ - 65);
        features.add(new BlockadeFort(KingdomTerrain.FORT_X, KingdomTerrain.FORT_Z, rand.nextLong()));
        for (int t = 0, n = 0; t < 4000 && n < 55; t++) {
            double a = rand.nextDouble() * Math.PI * 2, r = 45 + rand.nextDouble() * 112;
            int x = OX + (int) Math.round(Math.cos(a) * r), z = OZ + (int) Math.round(Math.sin(a) * r);
            int w = 7 + rand.nextInt(5), d = 7 + rand.nextInt(4);
            int x0 = x - w / 2, z0 = z - d / 2, x1 = x0 + w - 1, z1 = z0 + d - 1;
            if (!free(x0, z0, x1, z1)) continue;
            features.add(new RuinHouse(x0, z0, x1, z1, rand.nextLong()));
            take(x0, z0, x1, z1);
            n++;
        }
        for (Feature f : features) {
            f.groundY = Y;
            for (int x = (f.minX - 1) >> 4; x <= (f.maxX + 1) >> 4; x++) {
                for (int z = (f.minZ - 1) >> 4; z <= (f.maxZ + 1) >> 4; z++) {
                    byChunk.computeIfAbsent(ChunkPos.asLong(x, z), k -> new ArrayList<>()).add(f);
                }
            }
        }
    }

    private void take(int x0, int z0, int x1, int z1) {
        taken.add(new int[]{x0, z0, x1, z1});
    }

    private boolean free(int x0, int z0, int x1, int z1) {
        for (int[] q : taken) if (x0 - 2 <= q[2] && x1 + 2 >= q[0] && z0 - 2 <= q[3] && z1 + 2 >= q[1]) return false;
        for (int x = x0; x <= x1; x += 2) for (int z = z0; z <= z1; z += 2) if (street(x, z)) return false;
        return !street(x1, z1);
    }

    public int featureCount() {
        return features.size();
    }

    @Override public int cx() { return cx; }
    @Override public int cz() { return cz; }
    @Override public int base() { return Y; }
    @Override public int radius() { return radius; }
    @Override public CityLayout city() { return null; }

    @Override
    public List<Feature> featuresIn(ChunkPos cp) {
        return byChunk.getOrDefault(cp.toLong(), Collections.emptyList());
    }

    /** 옛 왕도 폐허는 일부러 어둡게 (몬스터 소굴), 봉쇄 요새만 실내 조명 */
    @Override
    public int[] lightRange(int x, int z) {
        if (Math.abs(x - KingdomTerrain.FORT_X) <= 26 && Math.abs(z - KingdomTerrain.FORT_Z) <= 22) return new int[]{Y - 1, Y + 20};
        return null;
    }

    // ───────────────────────── 거리 · 성벽 ─────────────────────────

    /** 방사형 큰길 8개 + 고리 길 2개 (폐허 사이로 남은 옛 거리) */
    private boolean street(int x, int z) {
        int dx = x - OX, dz = z - OZ;
        double r = Math.hypot(dx, dz);
        if (r < 30 || r > WALL_IN) return false;
        if (Math.abs(r - 70) <= 3 || Math.abs(r - 120) <= 3) return true;
        for (int k = 0; k < 8; k++) {
            double a = GATE_ANGLE + k * Math.PI / 4;
            double lat = -dx * Math.sin(a) + dz * Math.cos(a), along = dx * Math.cos(a) + dz * Math.sin(a);
            if (along > 0 && Math.abs(lat) <= (k == 0 ? 4 : 2.5)) return true;
        }
        return false;
    }

    private int h(int x, int z, int salt) {
        return Shapes.hash(x, salt, z, seed);
    }

    @Override
    public void buildColumn(Placer p, int x, int z) {
        int dx = x - OX, dz = z - OZ;
        double r = Math.hypot(dx, dz);
        boolean fort = Math.abs(x - KingdomTerrain.FORT_X) <= 30 && Math.abs(z - KingdomTerrain.FORT_Z) <= 30;
        if (r > WALL_OUT + 4 && !fort) {
            if (r <= WALL_OUT + 60 && h(x, z, 1) % 60 == 0) p.set(x, Y + 1, z, Blocks.DEAD_BUSH.defaultBlockState());
            return;
        }
        if (fort) return;                                                  // 요새는 BlockadeFort 가 바닥까지 지음
        p.clearAbove(x, z, Y + 1);
        int hh = h(x, z, 2);
        // 오염도: 포탈에 가까울수록 네더 블록
        double corrupt = Math.max(0, 1 - (r - 25) / 110.0);
        boolean nether = (hh % 100) < corrupt * 85;
        BlockState s;
        if (street(x, z)) {
            s = hh % 6 == 0 ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState() : hh % 5 == 0 ? Blocks.GRAVEL.defaultBlockState()
                    : hh % 4 == 0 ? Blocks.COBBLED_DEEPSLATE.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState();
            if (nether && hh % 3 == 0) s = Blocks.NETHERRACK.defaultBlockState();
        } else if (nether) {
            s = hh % 7 == 0 ? Blocks.MAGMA_BLOCK.defaultBlockState() : hh % 3 == 0 ? Blocks.CRIMSON_NYLIUM.defaultBlockState() : Blocks.NETHERRACK.defaultBlockState();
        } else {
            s = hh % 4 == 0 ? Blocks.COARSE_DIRT.defaultBlockState() : hh % 3 == 0 ? Blocks.GRAVEL.defaultBlockState() : hh % 5 == 0 ? Blocks.SOUL_SOIL.defaultBlockState() : Blocks.TUFF.defaultBlockState();
        }
        p.set(x, Y, z, s);
        p.foundation(x, z, Y - 1, 24);
        // 용암 구덩이 (균열)
        double crack = Math.abs(Math.sin(dx / 23.0 + dz / 31.0) * Math.cos(dx / 17.0 - dz / 29.0));
        if (r > 35 && r < 150 && crack < 0.025 && !street(x, z)) {
            p.set(x, Y, z, Blocks.LAVA.defaultBlockState());
            p.set(x, Y - 1, z, Blocks.MAGMA_BLOCK.defaultBlockState());
        } else if (!street(x, z) && r < WALL_IN) {
            int k = hh % 211;
            if (k == 0) p.set(x, Y + 1, z, Blocks.SOUL_CAMPFIRE.defaultBlockState());
            else if (k < 3) p.set(x, Y + 1, z, Blocks.SKELETON_SKULL.defaultBlockState());
            else if (k < 6) p.set(x, Y + 1, z, Blocks.BONE_BLOCK.defaultBlockState());
            else if (k < 10 && nether) p.set(x, Y + 1, z, Blocks.CRIMSON_ROOTS.defaultBlockState());
            else if (k < 14) p.set(x, Y + 1, z, Blocks.COBWEB.defaultBlockState());
            else if (k < 22) p.set(x, Y + 1, z, Blocks.DEAD_BUSH.defaultBlockState());
        }
        // 반쯤 무너진 성벽
        if (r >= WALL_IN && r <= WALL_OUT) {
            double ang = Math.atan2(dz, dx);
            boolean gate = Math.abs(Math.atan2(Math.sin(ang - GATE_ANGLE), Math.cos(ang - GATE_ANGLE))) * r < 6;
            double ruin = 0.5 + 0.5 * Math.sin(ang * 13 + 1.7) * Math.cos(ang * 7);
            int top = gate ? 0 : (int) Math.round(3 + ruin * 15) - (hh % 3);
            if (ruin < 0.18) top = hh % 3;                                  // 무너진 틈
            for (int y = 1; y <= top; y++) {
                int k = h(x, z + y, 3) % 9;
                p.set(x, Y + y, z, k < 3 ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState() : k < 5 ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState()
                        : k < 6 ? Blocks.BLACKSTONE.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState());
            }
            if (top > 3 && r > WALL_OUT - 0.6 && (int) (ang * r) % 3 == 0) p.set(x, Y + top + 1, z, Blocks.STONE_BRICK_WALL.defaultBlockState());
        } else if (r > WALL_OUT && r <= WALL_OUT + 4 && hh % 4 == 0) {          // 무너진 돌 더미
            p.set(x, Y + 1, z, Blocks.COBBLESTONE.defaultBlockState());
            if (hh % 12 == 0) p.set(x, Y + 2, z, Blocks.MOSSY_COBBLESTONE.defaultBlockState());
        }
    }

    // ───────────────────────── 공용 ─────────────────────────

    static void spawner(Placer p, int x, int y, int z, EntityType<?> type) {
        if (p.pass() != Placer.Pass.SOLID || !p.contains(x, z)) return;
        p.set(x, y, z, Blocks.SPAWNER.defaultBlockState());
        BlockEntity be = p.level().getBlockEntity(new BlockPos(x, y, z));
        if (be instanceof SpawnerBlockEntity s) {
            s.setEntityId(type, p.level().getRandom());
            s.setChanged();
        }
    }

    static void chest(Placer p, int x, int y, int z, Direction facing, long seed,
                      net.minecraft.resources.ResourceKey<net.minecraft.world.level.storage.loot.LootTable> loot) {
        if (p.pass() != Placer.Pass.SOLID || !p.contains(x, z)) return;
        p.set(x, y, z, Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.FACING, facing));
        BlockEntity be = p.level().getBlockEntity(new BlockPos(x, y, z));
        if (be instanceof RandomizableContainerBlockEntity rc) rc.setLootTable(loot, seed);
    }

    /** 불탄 재료 (그을린 돌·흑암·짙은 나무) */
    static BlockState burnt(int k) {
        return switch (Math.floorMod(k, 8)) {
            case 0, 1 -> Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
            case 2 -> Blocks.BLACKSTONE.defaultBlockState();
            case 3 -> Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
            case 4 -> Blocks.DARK_OAK_PLANKS.defaultBlockState();
            case 5 -> Blocks.COBBLESTONE.defaultBlockState();
            case 6 -> Blocks.STONE_BRICKS.defaultBlockState();
            default -> Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
        };
    }

    // ───────────────────────── 포탈 광장 (옛 왕궁 터) ─────────────────────────

    /** 포탈 틀 바닥 (3단 제단 꼭대기) */
    public static final int PORTAL_Y0 = Y + 3;

    /** 포탈 틀 안쪽 칸인지 (x: OX-4..OX+4, y: Y+4..Y+14, z = OZ) */
    public static boolean portalInterior(int x, int y, int z) {
        return z == OZ && Math.abs(x - OX) <= PORTAL_W / 2 && y > PORTAL_Y0 && y <= PORTAL_Y0 + PORTAL_H;
    }

    public final class PortalPlaza extends Feature {
        PortalPlaza() {
            super(OX - 34, OZ - 34, OX + 34, OZ + 34);
        }

        @Override
        public void build(Placer p, CityLayout city) {
            final int b = groundY;
            // 광장 바닥 (흑암 벽돌 + 균열 + 네더랙)
            p.forColumns(OX - 30, OZ - 30, OX + 30, OZ + 30, (x, z) -> {
                double r = Math.hypot(x - OX, z - OZ);
                if (r > 30.5) return;
                int k = h(x, z, 5);
                BlockState s = r > 29.5 ? Blocks.POLISHED_BLACKSTONE.defaultBlockState()
                        : k % 6 == 0 ? Blocks.NETHERRACK.defaultBlockState() : k % 5 == 0 ? Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState()
                        : k % 7 == 0 ? Blocks.MAGMA_BLOCK.defaultBlockState() : Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
                p.set(x, b, z, s);
                for (int y = b + 1; y <= b + 30; y++) p.set(x, y, z, Blocks.AIR.defaultBlockState());
            });
            // 무너진 왕궁: 광장 둘레의 부러진 아치 기둥 12개
            for (int i = 0; i < 12; i++) {
                double a = Math.PI * 2 * i / 12 + 0.13;
                if (Math.abs(Math.atan2(Math.sin(a - GATE_ANGLE), Math.cos(a - GATE_ANGLE))) < 0.4) continue;   // 정면은 비움
                int px = OX + (int) Math.round(Math.cos(a) * 27), pz = OZ + (int) Math.round(Math.sin(a) * 27);
                int top = 6 + h(px, pz, 6) % 14;
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                    int tt = top - (Math.abs(dx) + Math.abs(dz)) * (h(px + dx, pz + dz, 7) % 4);
                    for (int y = 1; y <= tt; y++) p.set(px + dx, b + y, pz + dz, burnt(h(px + dx, y, pz + dz)));
                }
                if (top > 14) for (int k = 1; k <= 4; k++) {                    // 아치 조각
                    p.set(px + (int) Math.round(Math.cos(a + Math.PI / 2) * k), b + top - k / 2, pz + (int) Math.round(Math.sin(a + Math.PI / 2) * k), Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
                }
                p.set(px, b + top + 1, pz, Blocks.SOUL_LANTERN.defaultBlockState());
            }
            // 계단식 제단 (3단) + 포탈 틀
            for (int k = 0; k < 3; k++) {
                int hw = 10 - k * 2, hd = 5 - k;
                for (int x = OX - hw; x <= OX + hw; x++) for (int z = OZ - hd; z <= OZ + hd; z++) {
                    boolean edge = Math.abs(x - OX) == hw || Math.abs(z - OZ) == hd;
                    p.set(x, b + 1 + k, z, edge ? Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS.defaultBlockState()
                            .setValue(StairBlock.FACING, z > OZ + hd - 1 ? Direction.NORTH : z < OZ - hd + 1 ? Direction.SOUTH : x > OX ? Direction.WEST : Direction.EAST)
                            : Blocks.POLISHED_BLACKSTONE.defaultBlockState());
                }
            }
            int fy0 = PORTAL_Y0, fy1 = PORTAL_Y0 + PORTAL_H + 1;
            for (int x = OX - PORTAL_W / 2 - 1; x <= OX + PORTAL_W / 2 + 1; x++) {
                for (int y = fy0; y <= fy1; y++) {
                    boolean frame = Math.abs(x - OX) == PORTAL_W / 2 + 1 || y == fy0 || y == fy1;
                    BlockState s = frame ? ((x + y) % 3 == 0 ? Blocks.CRYING_OBSIDIAN.defaultBlockState() : Blocks.OBSIDIAN.defaultBlockState())
                            : Blocks.AIR.defaultBlockState();
                    p.set(x, y, OZ, s);
                }
            }
            // 틀 위 뿔 장식과 사슬, 흑요석 가시
            for (int s = -1; s <= 1; s += 2) {
                for (int k = 1; k <= 5; k++) p.set(OX + s * (PORTAL_W / 2 + 1 + k / 2), fy1 + k, OZ, Blocks.OBSIDIAN.defaultBlockState());
                for (int y = fy0 + 1; y <= fy1 - 2; y += 3) p.set(OX + s * (PORTAL_W / 2 + 2), y, OZ, Blocks.CHAIN.defaultBlockState());
            }
            for (int i = 0; i < 9; i++) {
                double a = Math.PI * 2 * i / 9;
                int sx = OX + (int) Math.round(Math.cos(a) * 18), sz = OZ + (int) Math.round(Math.sin(a) * 18);
                int sh = 4 + h(sx, sz, 8) % 7;
                for (int y = 1; y <= sh; y++) p.set(sx, b + y, sz, y == sh ? Blocks.CRYING_OBSIDIAN.defaultBlockState() : Blocks.OBSIDIAN.defaultBlockState());
                p.set(sx + 1, b + 1, sz, Blocks.SOUL_CAMPFIRE.defaultBlockState());
            }
            // 네더 몹 스포너 (광장 가장자리)
            spawner(p, OX - 22, b + 1, OZ + 12, EntityType.BLAZE);
            spawner(p, OX + 22, b + 1, OZ - 12, EntityType.BLAZE);
            spawner(p, OX + 14, b + 1, OZ + 22, EntityType.WITHER_SKELETON);
            spawner(p, OX - 14, b + 1, OZ - 22, EntityType.MAGMA_CUBE);
            chest(p, OX + 3, b + 4, OZ - 3, Direction.SOUTH, seed ^ 7, BuiltInLootTables.RUINED_PORTAL);
        }
    }

    // ───────────────────────── 불탄 집 ─────────────────────────

    public final class RuinHouse extends Feature {
        private final long hseed;

        RuinHouse(int x0, int z0, int x1, int z1, long hseed) {
            super(x0, z0, x1, z1);
            this.hseed = hseed;
        }

        @Override
        public void build(Placer p, CityLayout city) {
            final int b = groundY;
            int full = 4 + (int) Math.floorMod(hseed, 3L);
            int doorX = (minX + maxX) / 2;
            p.forColumns(minX, minZ, maxX, maxZ, (x, z) -> {
                boolean edge = x == minX || x == maxX || z == minZ || z == maxZ;
                int k = Shapes.hash(x, 9, z, hseed);
                p.set(x, b, z, k % 3 == 0 ? Blocks.DARK_OAK_PLANKS.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState());
                if (edge) {
                    int top = Math.max(0, full - (k % 5) - (Math.abs(x - doorX) <= 0 && z == maxZ ? full : 0));
                    if ((x == minX || x == maxX) && (z == minZ || z == maxZ)) top = full + 1;
                    for (int y = 1; y <= top; y++) {
                        BlockState s = burnt(Shapes.hash(x, y, z, hseed));
                        if (y == 2 && k % 4 == 1 && top > 2) s = Blocks.IRON_BARS.defaultBlockState();
                        p.set(x, b + y, z, s);
                    }
                } else {
                    if (k % 23 == 0) p.set(x, b + full, z, Blocks.DARK_OAK_SLAB.defaultBlockState());     // 남은 지붕 조각
                    int j = k % 41;
                    if (j == 1) p.set(x, b + 1, z, Blocks.COBWEB.defaultBlockState());
                    else if (j == 2) p.set(x, b + 1, z, Deco.BARREL);
                    else if (j == 3) p.set(x, b + 1, z, Blocks.SKELETON_SKULL.defaultBlockState());
                    else if (j == 4) p.set(x, b + 1, z, Blocks.SOUL_CAMPFIRE.defaultBlockState());
                    else if (j == 5) p.set(x, b + 1, z, Blocks.DARK_OAK_STAIRS.defaultBlockState());
                }
            });
            if (Math.floorMod(hseed, 7L) == 0) chest(p, minX + 1, b + 1, minZ + 1, Direction.SOUTH, hseed, BuiltInLootTables.VILLAGE_WEAPONSMITH);
            if (Math.floorMod(hseed, 9L) == 0) spawner(p, maxX - 1, b + 1, maxZ - 1, EntityType.ZOMBIFIED_PIGLIN);
        }
    }

    // ───────────────────────── 불탄 대성당 ─────────────────────────

    public final class BurntCathedral extends Feature {
        private final int ax, az;

        BurntCathedral(int ax, int az) {
            super(ax - 26, az - 16, ax + 26, az + 16);
            this.ax = ax;
            this.az = az;
        }

        @Override
        public void build(Placer p, CityLayout city) {
            final int b = groundY;
            p.forColumns(ax - 24, az - 13, ax + 24, az + 13, (x, z) -> {
                int dx = x - ax, dz = z - az, k = h(x, z, 11);
                p.set(x, b, z, k % 4 == 0 ? Blocks.GRAVEL.defaultBlockState() : Blocks.POLISHED_DEEPSLATE.defaultBlockState());
                boolean wall = Math.abs(dz) == 12 || Math.abs(dx) == 24;
                boolean pier = Math.abs(dz) == 6 && Math.floorMod(dx, 6) == 0;
                double ruin = 0.5 + 0.5 * Math.sin(dx / 4.0 + 1.3) * Math.cos(dz / 5.0);
                if (wall) {
                    int top = dx == -24 ? 34 - Math.abs(dz) : (int) Math.round(6 + ruin * 18) - k % 4;   // 서쪽 정면 박공만 높이 남음
                    for (int y = 1; y <= top; y++) {
                        BlockState s = Blocks.DEEPSLATE_BRICKS.defaultBlockState();
                        if (Math.floorMod(dx, 6) == 3 && y > 4 && y < 18 && Math.abs(dz) == 12) s = h(x, y, 12) % 3 == 0 ? Blocks.BLACK_STAINED_GLASS_PANE.defaultBlockState() : Blocks.AIR.defaultBlockState();
                        else if (h(x, y, 13) % 5 == 0) s = Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState();
                        if (dx == -24 && Math.abs(dz) <= 2 && y <= 9) s = Blocks.AIR.defaultBlockState();
                        if (dx == -24 && Math.hypot(dz, y - 20) < 4) s = Math.hypot(dz, y - 20) < 3 ? Blocks.AIR.defaultBlockState() : Blocks.CALCITE.defaultBlockState();
                        p.set(x, b + y, z, s);
                    }
                } else if (pier) {
                    int top = (int) Math.round(4 + ruin * 20);
                    for (int y = 1; y <= top; y++) p.set(x, b + y, z, y % 6 == 0 ? Blocks.CALCITE.defaultBlockState() : Blocks.POLISHED_DEEPSLATE.defaultBlockState());
                } else if (Math.abs(dz) <= 4 && Math.floorMod(dx, 2) == 0 && k % 3 != 0 && Math.abs(dz) >= 1) {
                    p.set(x, b + 1, z, Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.WEST));   // 불탄 신도석
                } else if (k % 17 == 0) {
                    p.set(x, b + 1, z, Blocks.COBBLED_DEEPSLATE.defaultBlockState());
                }
            });
            // 무너진 종탑 밑동 + 떨어진 종
            for (int y = 1; y <= 14; y++) for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++)
                if (Math.abs(dx) == 2 || Math.abs(dz) == 2) if (y < 14 - Math.abs(dx + dz) * 2) p.set(ax + 26 + dx - 4, b + y, az + 15 + dz - 4, Blocks.DEEPSLATE_BRICKS.defaultBlockState());
            p.set(ax + 18, b + 1, az + 8, Blocks.BELL.defaultBlockState());
            for (int lat = -2; lat <= 2; lat++) p.set(ax + 21, b + 1, az + lat, Blocks.POLISHED_BLACKSTONE.defaultBlockState());
            p.set(ax + 21, b + 2, az, Blocks.SOUL_LANTERN.defaultBlockState());
            spawner(p, ax + 15, b + 1, az - 8, EntityType.WITHER_SKELETON);
            chest(p, ax + 21, b + 2, az + 2, Direction.WEST, seed ^ 11, BuiltInLootTables.SIMPLE_DUNGEON);
        }
    }

    // ───────────────────────── 쓰러진 거상 ─────────────────────────

    /** 옛 왕의 거상이 쓰러져 거리를 가로막음: 몸통 기둥, 떨어진 머리와 금관, 부러진 검 */
    public final class FallenColossus extends Feature {
        private final int ax, az;

        FallenColossus(int ax, int az) {
            super(ax - 22, az - 12, ax + 22, az + 12);
            this.ax = ax;
            this.az = az;
        }

        @Override
        public void build(Placer p, CityLayout city) {
            final int b = groundY;
            p.forColumns(minX, minZ, maxX, maxZ, (x, z) -> {
                int dx = x - ax, dz = z - az;
                if (dx >= -16 && dx <= 10) {                                   // 누운 몸통 (반쯤 땅에 묻힘)
                    double rr = 4.5 - Math.max(0, dx - 4) * 0.3;
                    for (int y = -1; y <= 6; y++) {
                        if (Math.hypot(dz, y - 1.5) <= rr) p.set(x, b + y, z, h(x, y, 14) % 6 == 0 ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState() : Blocks.SMOOTH_STONE.defaultBlockState());
                    }
                }
                double dh = Math.sqrt((dx - 16) * (dx - 16) + dz * dz);
                for (int y = 0; y <= 10; y++) {                                 // 떨어진 머리
                    if (Math.sqrt(dh * dh + (y - 4) * (y - 4)) <= 5.2) p.set(x, b + y, z, Blocks.SMOOTH_STONE.defaultBlockState());
                }
                if (Math.abs(dx - 16) <= 3 && Math.abs(dz) == 5) p.set(x, b + 9, z, Royal.GOLD);   // 금관 조각
            });
            for (int k = 0; k <= 14; k++) p.set(ax - 18 - k, b + 1, az + 6 + k / 3, Blocks.IRON_BLOCK.defaultBlockState());   // 부러진 검
            p.set(ax - 17, b + 1, az + 6, Blocks.GOLD_BLOCK.defaultBlockState());
            p.set(ax - 17, b + 2, az + 6, Blocks.GOLD_BLOCK.defaultBlockState());
            p.set(ax + 16, b + 4, az - 5, Blocks.SOUL_LANTERN.defaultBlockState());
        }
    }

    // ───────────────────────── 연합군 봉쇄 요새 (안전 거점) ─────────────────────────

    /** 폐왕도 위 봉쇄 요새: 돌벽·통나무 망루, 천막 막사, 침대, 대장간, 양조대(치료), 보급 상자, 왕국·연합 깃발 */
    public final class BlockadeFort extends Feature {
        private final int fx, fz;
        private final long fseed;

        BlockadeFort(int fx, int fz, long fseed) {
            super(fx - 30, fz - 30, fx + 30, fz + 30);
            this.fx = fx;
            this.fz = fz;
            this.fseed = fseed;
        }

        @Override
        public void build(Placer p, CityLayout city) {
            final int b = groundY, hw = 24, hd = 20;
            p.forColumns(fx - 28, fz - 28, fx + 28, fz + 28, (x, z) -> {
                p.clearAbove(x, z, b + 1);
                int dx = x - fx, dz = z - fz, k = Shapes.hash(x, 15, z, fseed);
                boolean inside = Math.abs(dx) <= hw && Math.abs(dz) <= hd;
                p.set(x, b, z, inside ? (k % 3 == 0 ? Blocks.GRAVEL.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState()) : Blocks.COARSE_DIRT.defaultBlockState());
                p.foundation(x, z, b - 1, 24);
                boolean edge = (Math.abs(dx) == hw && Math.abs(dz) <= hd) || (Math.abs(dz) == hd && Math.abs(dx) <= hw);
                boolean gateNE = (dz == -hd && Math.abs(dx - 8) <= 2) || (dx == -hw && Math.abs(dz - 6) <= 2);   // 북쪽(왕국 쪽) · 서쪽(옛 왕도 쪽) 문
                if (edge && !gateNE) {
                    for (int y = 1; y <= 7; y++) p.set(x, b + y, z, y <= 4 ? Blocks.STONE_BRICKS.defaultBlockState() : Blocks.SPRUCE_LOG.defaultBlockState());
                    if (((dx + dz) & 1) == 0) p.set(x, b + 8, z, Blocks.SPRUCE_FENCE.defaultBlockState());
                }
            });
            // 모서리 통나무 망루
            for (int sx = -1; sx <= 1; sx += 2) for (int sz = -1; sz <= 1; sz += 2) {
                int tx = fx + sx * hw, tz = fz + sz * hd;
                for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                    boolean e = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                    for (int y = 1; y <= 13; y++) p.set(tx + dx, b + y, tz + dz, e ? (y <= 5 ? Blocks.STONE_BRICKS.defaultBlockState() : Blocks.SPRUCE_LOG.defaultBlockState()) : (y == 12 ? Blocks.SPRUCE_PLANKS.defaultBlockState() : Blocks.AIR.defaultBlockState()));
                    if (e && ((dx + dz) & 1) == 0) p.set(tx + dx, b + 14, tz + dz, Blocks.SPRUCE_FENCE.defaultBlockState());
                }
                for (int y = 1; y < 12; y++) p.set(tx, b + y, tz - sz, io.github.aswq0258.magiccity.city.Palette.ladder(sz > 0 ? Direction.NORTH : Direction.SOUTH));
                p.set(tx, b + 15, tz, Blocks.SPRUCE_FENCE.defaultBlockState());
                p.set(tx, b + 16, tz, Blocks.SPRUCE_FENCE.defaultBlockState());
                p.set(tx + 1, b + 16, tz, sx > 0 ? Royal.mintBanner(Direction.EAST) : Royal.redBanner(Direction.EAST));
            }
            // 천막 막사 3동 (흰·민트 천)
            for (int i = 0; i < 3; i++) tent(p, b, fx - 14 + i * 10, fz + 8);
            // 대장간·치료소·보급
            Frame f = new Frame(fx + 12, fz - 9, Direction.SOUTH, 9, 7);
            for (int u = 0; u < 9; u++) for (int v = 0; v < 7; v++) {
                boolean e = u == 0 || u == 8 || v == 0 || v == 6;
                for (int y = 1; y <= 4; y++) f.set(p, b, u, y, v, e && !(v == 0 && u == 4 && y <= 2) ? (y == 4 ? Blocks.SPRUCE_PLANKS.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState()) : (y == 4 ? Blocks.SPRUCE_PLANKS.defaultBlockState() : Blocks.AIR.defaultBlockState()));
            }
            f.set(p, b, 1, 1, 5, Blocks.ANVIL.defaultBlockState());
            f.set(p, b, 2, 1, 5, Blocks.SMITHING_TABLE.defaultBlockState());
            f.set(p, b, 3, 1, 5, Blocks.GRINDSTONE.defaultBlockState());
            f.set(p, b, 5, 1, 5, Deco.BREWING);
            f.set(p, b, 6, 1, 5, Deco.WATER_CAULDRON);
            f.set(p, b, 7, 1, 5, Blocks.BLAST_FURNACE.defaultBlockState());
            f.set(p, b, 4, 3, 3, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
            chest(p, fx - 18, b + 1, fz - 14, Direction.SOUTH, fseed, BuiltInLootTables.VILLAGE_ARMORER);
            chest(p, fx - 16, b + 1, fz - 14, Direction.SOUTH, fseed + 1, BuiltInLootTables.VILLAGE_TEMPLE);
            p.set(fx - 17, b + 1, fz - 14, Deco.CRAFTING);
            p.set(fx, b + 1, fz, Deco.CAMPFIRE);
            for (int s = -1; s <= 1; s += 2) {
                p.set(fx + s * 2, b + 1, fz, Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, s > 0 ? Direction.EAST : Direction.WEST));
                p.set(fx, b + 1, fz + s * 2, Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, s > 0 ? Direction.SOUTH : Direction.NORTH));
            }
            p.set(fx + 4, b + 1, fz + 3, Blocks.TARGET.defaultBlockState());
            p.set(fx + 5, b + 1, fz + 3, Blocks.HAY_BLOCK.defaultBlockState());
            // 경고 표지: 옛 왕도 쪽 문 앞 해골·영혼 랜턴
            for (int s = -1; s <= 1; s += 2) {
                p.set(fx - hw - 2, b + 1, fz + 6 + s * 3, Blocks.SPRUCE_FENCE.defaultBlockState());
                p.set(fx - hw - 2, b + 2, fz + 6 + s * 3, Blocks.SKELETON_SKULL.defaultBlockState());
                p.set(fx - hw - 2, b + 3, fz + 6 + s * 3, Blocks.SOUL_LANTERN.defaultBlockState());
            }
        }

        private void tent(Placer p, int b, int tx, int tz) {
            for (int dx = -3; dx <= 3; dx++) for (int dz = -2; dz <= 2; dz++) {
                int ad = Math.abs(dz);
                for (int y = 1; y <= 4 - ad; y++) {
                    boolean shell = y == 4 - ad || Math.abs(dx) == 3;
                    if (shell && !(dx == -3 && ad <= 0 && y <= 2)) p.set(tx + dx, b + y, tz + dz, (dx + y) % 2 == 0 ? Blocks.WHITE_WOOL.defaultBlockState() : Blocks.LIGHT_BLUE_WOOL.defaultBlockState());
                }
            }
            p.set(tx + 1, b + 1, tz - 1, io.github.aswq0258.magiccity.city.Palette.bed(Direction.EAST, true));
            p.set(tx, b + 1, tz - 1, io.github.aswq0258.magiccity.city.Palette.bed(Direction.EAST, false));
            p.set(tx + 1, b + 1, tz + 1, io.github.aswq0258.magiccity.city.Palette.bed(Direction.EAST, true));
            p.set(tx, b + 1, tz + 1, io.github.aswq0258.magiccity.city.Palette.bed(Direction.EAST, false));
        }
    }
}
