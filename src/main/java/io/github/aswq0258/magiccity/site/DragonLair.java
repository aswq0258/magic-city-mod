package io.github.aswq0258.magiccity.site;

import io.github.aswq0258.magiccity.build.BuildLayout;
import io.github.aswq0258.magiccity.build.Placer;
import io.github.aswq0258.magiccity.city.CityLayout;
import io.github.aswq0258.magiccity.city.Shapes;
import io.github.aswq0258.magiccity.city.feature.Feature;
import io.github.aswq0258.magiccity.world.KingdomTerrain;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DripstoneThickness;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;

/**
 * 고룡 봉우리 드래곤 레어 (마스터플랜 G1) — 봉우리 아래 거대한 지하 동굴 미궁
 *
 *  1. 용의 아가리: 큰 강이 시작되는 협곡 끝 절벽의 거대한 동굴 입구 (용의 길 끝). 송곳니 같은 종유석, 경고 표지
 *  2. 샘의 동굴: 강물이 솟는 샘 연못과 물길이 있는 큰 동굴 (Y 63)
 *  3. 용의 목구멍: 나선 계단으로 지하 깊이 내려감
 *  4. 상층 미궁 (Y 30): 11 x 11 칸 미로. 막다른 곳마다 방 — 보물, 몰락한 모험가 야영지, 납골당, 수정 동굴, 잠긴 동굴, 알 둥지
 *  5. 하층 미궁 (Y 6): 흑암·현무암·마그마의 불타는 미로, 용암 도랑과 좁은 다리. 용의 권속(블레이즈·마그마 큐브·위더 해골)
 *  6. 보물 동굴 (Y -40): 지름 70 의 거대한 돔. 황금 더미 위에 잠든 고룡 이그나르 (boss.DragonBoss), 옛 용의 해골, 용암 웅덩이
 * 각 층 출구는 그 층 입구에서 가장 먼 칸에 있음.
 */
public final class DragonLair implements BuildLayout {
    /** 미궁 중심 (봉우리 아래) */
    public static final int MX = 760, MZ = -2740;
    public static final int CELL = 12, N = 11, X0 = MX - CELL * N / 2, Z0 = MZ - CELL * N / 2;
    public static final int L1 = 30, L2 = 6, HOARD_Y = -40, HOARD_R = 34;
    /** 동굴 입구와 샘의 동굴 */
    public static final int MOUTH_X = 680, MOUTH_Z = -2598, GROTTO_Z = -2640, GROTTO_R = 14, CAVE_Y = 63;
    public static final int THROAT_X = 680, THROAT_Z = -2664;

    private final long seed;
    private final Maze up, down;
    private final List<ChunkPos> chunks = new ArrayList<>();
    /** 나선 계단 {x, z, yTop, yBottom} */
    private final List<int[]> spirals = new ArrayList<>();
    /** 굴 {x0, z0, x1, z1, y, halfWidth} — 바닥 높이 y 의 곧은 굴 */
    private final List<int[]> tunnels = new ArrayList<>();

    enum Room { TREASURE, CAMP, OSSUARY, CRYSTAL, FLOODED, NEST, LAVA, SHRINE }

    /** 한 층의 미로 */
    final class Maze {
        final int floor;
        final boolean fire;
        final boolean[][] east = new boolean[N][N], south = new boolean[N][N];
        final Room[][] room = new Room[N][N];
        final int ei, ej;               // 입구 칸
        int xi, xj;                     // 출구 칸 (입구에서 가장 먼 칸)

        Maze(int floor, boolean fire, int ei, int ej, long s) {
            this.floor = floor;
            this.fire = fire;
            this.ei = ei;
            this.ej = ej;
            Random r = new Random(s);
            boolean[][] seen = new boolean[N][N];
            ArrayDeque<int[]> stack = new ArrayDeque<>();
            stack.push(new int[]{ei, ej});
            seen[ei][ej] = true;
            int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            while (!stack.isEmpty()) {
                int[] c = stack.peek();
                List<int[]> opts = new ArrayList<>();
                for (int[] d : dirs) {
                    int ni = c[0] + d[0], nj = c[1] + d[1];
                    if (ni >= 0 && nj >= 0 && ni < N && nj < N && !seen[ni][nj]) opts.add(new int[]{ni, nj});
                }
                if (opts.isEmpty()) {
                    stack.pop();
                    continue;
                }
                int[] n = opts.get(r.nextInt(opts.size()));
                open(c[0], c[1], n[0], n[1]);
                seen[n[0]][n[1]] = true;
                stack.push(n);
            }
            // 고리 몇 개 (완전한 나무 구조보다 덜 답답하게)
            for (int k = 0; k < 10; k++) {
                int i = r.nextInt(N - 1), j = r.nextInt(N - 1);
                if (r.nextBoolean()) east[i][j] = true;
                else south[i][j] = true;
            }
            // 출구: 입구에서 가장 먼 칸
            int[][] dist = new int[N][N];
            for (int[] row : dist) java.util.Arrays.fill(row, -1);
            ArrayDeque<int[]> q = new ArrayDeque<>();
            q.add(new int[]{ei, ej});
            dist[ei][ej] = 0;
            int best = 0;
            xi = ei;
            xj = ej;
            while (!q.isEmpty()) {
                int[] c = q.poll();
                for (int[] d : dirs) {
                    int ni = c[0] + d[0], nj = c[1] + d[1];
                    if (ni < 0 || nj < 0 || ni >= N || nj >= N || dist[ni][nj] >= 0 || !connected(c[0], c[1], ni, nj)) continue;
                    dist[ni][nj] = dist[c[0]][c[1]] + 1;
                    if (dist[ni][nj] > best) {
                        best = dist[ni][nj];
                        xi = ni;
                        xj = nj;
                    }
                    q.add(new int[]{ni, nj});
                }
            }
            // 막다른 칸에 방
            Room[] kinds = fire ? new Room[]{Room.TREASURE, Room.LAVA, Room.OSSUARY, Room.NEST, Room.SHRINE, Room.LAVA}
                    : new Room[]{Room.TREASURE, Room.CAMP, Room.OSSUARY, Room.CRYSTAL, Room.FLOODED, Room.NEST};
            int k = 0;
            for (int i = 0; i < N; i++) for (int j = 0; j < N; j++) {
                if ((i == ei && j == ej) || (i == xi && j == xj)) continue;
                int links = 0;
                for (int[] d : dirs) {
                    int ni = i + d[0], nj = j + d[1];
                    if (ni >= 0 && nj >= 0 && ni < N && nj < N && connected(i, j, ni, nj)) links++;
                }
                if (links == 1 && r.nextInt(3) != 0) room[i][j] = kinds[k++ % kinds.length];
            }
        }

        private void open(int i, int j, int ni, int nj) {
            if (ni == i + 1) east[i][j] = true;
            else if (ni == i - 1) east[ni][j] = true;
            else if (nj == j + 1) south[i][j] = true;
            else south[i][nj] = true;
        }

        boolean connected(int i, int j, int ni, int nj) {
            if (ni == i + 1) return east[i][j];
            if (ni == i - 1) return east[ni][j];
            if (nj == j + 1) return south[i][j];
            if (nj == j - 1) return south[i][nj];
            return false;
        }

        int cx(int i) {
            return X0 + i * CELL + CELL / 2;
        }

        int cz(int j) {
            return Z0 + j * CELL + CELL / 2;
        }
    }

    public DragonLair(long seed) {
        this.seed = seed;
        up = new Maze(L1, false, 0, N - 1, seed ^ 0x11);
        down = new Maze(L2, true, up.xi, up.xj, seed ^ 0x22);
        // 길: 목구멍 바닥 → 상층 입구 / 상층 출구 ↓ 하층 / 하층 출구 ↓ 보물 동굴
        spirals.add(new int[]{THROAT_X, THROAT_Z, CAVE_Y, L1});
        tunnels.add(new int[]{THROAT_X, THROAT_Z, up.cx(up.ei), up.cz(up.ej), L1, 3});
        spirals.add(new int[]{up.cx(up.xi), up.cz(up.xj), L1, L2});
        int hx = down.cx(down.xi), hz = down.cz(down.xj);
        spirals.add(new int[]{hx, hz, L2, HOARD_Y});
        tunnels.add(new int[]{hx, hz, MX, MZ, HOARD_Y, 3});
        tunnels.add(new int[]{MOUTH_X, GROTTO_Z, THROAT_X, THROAT_Z, CAVE_Y, 3});

        LinkedHashSet<Long> keys = new LinkedHashSet<>();
        for (int x = (X0 - 8) >> 4; x <= (X0 + CELL * N + 8) >> 4; x++)
            for (int z = (Z0 - 8) >> 4; z <= (Z0 + CELL * N + 8) >> 4; z++) keys.add(ChunkPos.asLong(x, z));
        for (int x = (MOUTH_X - 40) >> 4; x <= (MOUTH_X + 40) >> 4; x++)
            for (int z = (THROAT_Z - 12) >> 4; z <= (MOUTH_Z + 22) >> 4; z++) keys.add(ChunkPos.asLong(x, z));
        for (int[] t : tunnels) for (int x = (Math.min(t[0], t[2]) - 8) >> 4; x <= (Math.max(t[0], t[2]) + 8) >> 4; x++)
            for (int z = (Math.min(t[1], t[3]) - 8) >> 4; z <= (Math.max(t[1], t[3]) + 8) >> 4; z++) keys.add(ChunkPos.asLong(x, z));
        for (long k : keys) chunks.add(new ChunkPos(k));
    }

    public static boolean near(double x, double z) {
        return x > X0 - 30 && x < X0 + CELL * N + 30 && z > Z0 - 30 && z < MOUTH_Z + 30;
    }

    @Override public int cx() { return MX; }
    @Override public int cz() { return MZ; }
    @Override public int base() { return CAVE_Y; }
    @Override public int radius() { return 0; }
    @Override public CityLayout city() { return null; }
    @Override public List<ChunkPos> chunkList() { return chunks; }
    @Override public List<Feature> featuresIn(ChunkPos cp) { return Collections.emptyList(); }
    @Override public int[] lightRange(int x, int z) { return null; }
    @Override public int shapeMinY() { return HOARD_Y - 4; }
    @Override public int shapeMaxY() { return CAVE_Y + 30; }

    private int h(int x, int y, int z) {
        return Shapes.hash(x, y, z, seed);
    }

    private static double noise(int x, int z, int salt) {
        double a = Math.sin(x * 0.37 + salt * 1.7) * Math.cos(z * 0.29 - salt * 0.9) + Math.sin((x + z) * 0.11 + salt);
        return 0.5 + 0.25 * a;                                                               // 대략 0 ~ 1
    }

    // ───────────────────────── 기둥 ─────────────────────────

    @Override
    public void buildColumn(Placer p, int x, int z) {
        mouth(p, x, z);
        for (int[] t : tunnels) tunnel(p, x, z, t);
        for (Maze m : new Maze[]{up, down}) maze(p, x, z, m);
        hoard(p, x, z);
        for (int[] s : spirals) spiral(p, x, z, s[0], s[1], s[2], s[3]);
    }

    /** 바닥(y)과 천장(y+hgt) 사이를 비우고 바닥을 깖 */
    private void carve(Placer p, int x, int z, int y, int hgt, BlockState floor) {
        p.set(x, y, z, floor);
        for (int k = 1; k <= hgt; k++) {
            BlockState s = p.get(x, y + k, z);
            if (s.is(Blocks.WATER) || s.is(Blocks.LAVA)) continue;
            p.set(x, y + k, z, Blocks.AIR.defaultBlockState());
        }
    }

    private BlockState floorBlock(int x, int y, int z, boolean fire) {
        int k = h(x, y, z) % 20;
        if (fire) return k == 0 ? Blocks.MAGMA_BLOCK.defaultBlockState() : k < 6 ? Blocks.BASALT.defaultBlockState()
                : k < 12 ? Blocks.BLACKSTONE.defaultBlockState() : Blocks.COBBLED_DEEPSLATE.defaultBlockState();
        return k < 4 ? Blocks.GRAVEL.defaultBlockState() : k < 8 ? Blocks.TUFF.defaultBlockState() : k < 10 ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.STONE.defaultBlockState();
    }

    // ── 1·2. 동굴 입구와 샘의 동굴
    private void mouth(Placer p, int x, int z) {
        int dx = x - MOUTH_X;
        // 샘의 동굴: 둥근 큰 동굴 + 가운데 샘 연못 + 남쪽으로 흐르는 물길
        double dg = Math.hypot(dx, z - GROTTO_Z);
        boolean channel = Math.abs(dx) <= 2 && z > GROTTO_Z && z <= MOUTH_Z + 14;
        if (dg <= GROTTO_R + noise(x, z, 3) * 2) {
            int hgt = (int) Math.round(18 * Math.sqrt(Math.max(0, 1 - Math.pow(dg / (GROTTO_R + 2), 2)))) + 2;
            carve(p, x, z, CAVE_Y, hgt, h(x, 1, z) % 4 == 0 ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : Blocks.STONE.defaultBlockState());
            if (dg <= 5.5) {                                                                // 샘 연못
                p.set(x, CAVE_Y - 4, z, Blocks.GRAVEL.defaultBlockState());
                for (int y = CAVE_Y - 3; y <= CAVE_Y - 1; y++) p.set(x, y, z, Blocks.WATER.defaultBlockState());
                p.set(x, CAVE_Y, z, Blocks.AIR.defaultBlockState());
                if (dg < 1) p.set(x, CAVE_Y - 4, z, Blocks.MAGMA_BLOCK.defaultBlockState());     // 거품
            } else if (h(x, 2, z) % 23 == 0) {
                p.set(x, CAVE_Y + 1, z, Blocks.GLOW_LICHEN.defaultBlockState().setValue(net.minecraft.world.level.block.GlowLichenBlock.getFaceProperty(Direction.DOWN), true));
            }
            if (h(x, 3, z) % 17 == 0 && dg > 6) stalactite(p, x, CAVE_Y + hgt, z);
        }
        if (channel) {
            for (int y = CAVE_Y - 4; y <= CAVE_Y + 3; y++) {
                BlockState s = y == CAVE_Y - 4 ? Blocks.GRAVEL.defaultBlockState() : y < CAVE_Y ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState();
                if (y >= CAVE_Y && z > MOUTH_Z + 2) continue;                                       // 바깥쪽은 지형 그대로
                p.set(x, y, z, s);
            }
        }
        // 용의 아가리: 높이 14 의 아치형 입구 (밖으로 열린 굴)
        if (z <= MOUTH_Z + 4 && z >= GROTTO_Z + GROTTO_R - 2 && Math.abs(dx) <= 7) {
            double arch = 14 * Math.sqrt(Math.max(0, 1 - Math.pow(dx / 7.5, 2)));
            if (!channel) carve(p, x, z, CAVE_Y, (int) Math.round(arch), Blocks.STONE.defaultBlockState());
            else for (int y = CAVE_Y; y <= CAVE_Y + arch; y++) p.set(x, y, z, Blocks.AIR.defaultBlockState());
            if (z >= MOUTH_Z - 2 && Math.abs(dx) <= 7) {                                        // 송곳니 (위아래 뾰족 돌)
                int top = CAVE_Y + (int) Math.round(arch) + 1;
                if (Math.floorMod(dx, 3) == 0) stalactite(p, x, top, z);
                if (Math.floorMod(dx, 4) == 1 && Math.abs(dx) >= 3 && !channel) stalagmite(p, x, CAVE_Y + 1, z);
            }
        }
        // 입구 앞마당 (동쪽 강둑, 용의 길이 닿는 곳)
        if (z > MOUTH_Z + 4 && z <= MOUTH_Z + 10 && dx >= 4 && dx <= 20) {
            p.clearAbove(x, z, CAVE_Y + 1);
            p.set(x, CAVE_Y, z, h(x, 4, z) % 3 == 0 ? Blocks.GRAVEL.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState());
            p.foundation(x, z, CAVE_Y - 1, 12);
            int k = h(x, 5, z) % 90;
            if (k == 0) p.set(x, CAVE_Y + 1, z, Blocks.SKELETON_SKULL.defaultBlockState());
            else if (k < 3) p.set(x, CAVE_Y + 1, z, Blocks.BONE_BLOCK.defaultBlockState());
        }
        if (x == MOUTH_X + 12 && z == MOUTH_Z + 7) {                                         // 경고 표지 기둥과 꺼진 모닥불
            for (int y = 1; y <= 3; y++) p.set(x, CAVE_Y + y, z, Blocks.DARK_OAK_FENCE.defaultBlockState());
            p.set(x, CAVE_Y + 4, z, Blocks.SKELETON_SKULL.defaultBlockState());
            p.set(x + 3, CAVE_Y + 1, z + 2, Blocks.CAMPFIRE.defaultBlockState().setValue(net.minecraft.world.level.block.CampfireBlock.LIT, false));
            p.set(x + 1, CAVE_Y + 1, z, Blocks.RED_BANNER.defaultBlockState());
        }
    }

    private void stalactite(Placer p, int x, int y, int z) {
        p.set(x, y - 1, z, Blocks.POINTED_DRIPSTONE.defaultBlockState().setValue(PointedDripstoneBlock.TIP_DIRECTION, Direction.DOWN)
                .setValue(PointedDripstoneBlock.THICKNESS, DripstoneThickness.FRUSTUM));
        p.set(x, y - 2, z, Blocks.POINTED_DRIPSTONE.defaultBlockState().setValue(PointedDripstoneBlock.TIP_DIRECTION, Direction.DOWN)
                .setValue(PointedDripstoneBlock.THICKNESS, DripstoneThickness.TIP));
    }

    private void stalagmite(Placer p, int x, int y, int z) {
        p.set(x, y, z, Blocks.POINTED_DRIPSTONE.defaultBlockState().setValue(PointedDripstoneBlock.TIP_DIRECTION, Direction.UP)
                .setValue(PointedDripstoneBlock.THICKNESS, DripstoneThickness.FRUSTUM));
        p.set(x, y + 1, z, Blocks.POINTED_DRIPSTONE.defaultBlockState().setValue(PointedDripstoneBlock.TIP_DIRECTION, Direction.UP)
                .setValue(PointedDripstoneBlock.THICKNESS, DripstoneThickness.TIP));
    }

    // ── 굴 (곧은 연결 통로)
    private void tunnel(Placer p, int x, int z, int[] t) {
        double vx = t[2] - t[0], vz = t[3] - t[1], len2 = vx * vx + vz * vz;
        if (len2 == 0) return;
        double u = Math.max(0, Math.min(1, ((x - t[0]) * vx + (z - t[1]) * vz) / len2));
        double d = Math.hypot(x - t[0] - vx * u, z - t[1] - vz * u);
        if (d > t[5] + noise(x, z, 7)) return;
        boolean fire = t[4] < L1 - 5;
        carve(p, x, z, t[4], 5 + (int) Math.round(noise(x, z, 8) * 2), floorBlock(x, t[4], z, fire));
        if (h(x, 6, z) % 61 == 0) p.set(x, t[4] + 1, z, Blocks.LANTERN.defaultBlockState());
    }

    // ── 4·5. 미로
    private void maze(Placer p, int x, int z, Maze m) {
        if (x < X0 - 4 || z < Z0 - 4 || x >= X0 + CELL * N + 4 || z >= Z0 + CELL * N + 4) return;
        int i = Math.floorDiv(x - X0, CELL), j = Math.floorDiv(z - Z0, CELL);
        double best = 1e9;
        int bi = -1, bj = -1;
        for (int a = i - 1; a <= i + 1; a++) for (int b = j - 1; b <= j + 1; b++) {
            if (a < 0 || b < 0 || a >= N || b >= N) continue;
            double dc = Math.hypot(x - m.cx(a), z - m.cz(b));
            if (dc < best) {
                best = dc;
                bi = a;
                bj = b;
            }
            if (a + 1 < N && m.east[a][b]) best = Math.min(best, seg(x, z, m.cx(a), m.cz(b), m.cx(a + 1), m.cz(b)));
            if (b + 1 < N && m.south[a][b]) best = Math.min(best, seg(x, z, m.cx(a), m.cz(b), m.cx(a), m.cz(b + 1)));
        }
        double w = 2.0 + noise(x, z, m.fire ? 11 : 12) * 1.4;
        // 방 (막다른 칸)
        Room room = null;
        double dr = 1e9;
        int ri = Math.floorDiv(x - X0, CELL), rj = Math.floorDiv(z - Z0, CELL);
        if (ri >= 0 && rj >= 0 && ri < N && rj < N && m.room[ri][rj] != null) {
            room = m.room[ri][rj];
            dr = Math.hypot(x - m.cx(ri), z - m.cz(rj));
        }
        boolean inRoom = room != null && dr <= 5.6 + noise(x, z, 13);
        if (best > w && !inRoom) return;
        int hgt = inRoom ? 7 + (int) Math.round(3 * (1 - dr / 6.5)) : 4 + (int) Math.round(noise(x, z, 14) * 2);
        int F = m.floor;
        carve(p, x, z, F, hgt, floorBlock(x, F, z, m.fire));
        int k = h(x, F, z);
        // 장식
        if (!m.fire && k % 37 == 0) p.set(x, F + hgt, z, Blocks.COBWEB.defaultBlockState());
        else if (k % 53 == 0) stalactite(p, x, F + hgt + 1, z);
        else if (k % 97 == 0) p.set(x, F + 1, z, Blocks.BONE_BLOCK.defaultBlockState());
        else if (k % 211 == 0) p.set(x, F + 1, z, m.fire ? Blocks.WITHER_SKELETON_SKULL.defaultBlockState() : Blocks.SKELETON_SKULL.defaultBlockState());
        else if (!m.fire && k % 151 == 0) p.set(x, F + hgt, z, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        else if (m.fire && k % 89 == 0) p.set(x, F + 1, z, Blocks.FIRE.defaultBlockState());
        // 하층: 칸 사이 통로를 가로지르는 용암 도랑 + 외줄 다리
        if (m.fire && !inRoom && best <= w) {
            int ci = Math.floorDiv(x - X0, CELL), cj = Math.floorDiv(z - Z0, CELL);
            int lx = Math.floorMod(x - X0, CELL), lz = Math.floorMod(z - Z0, CELL);
            boolean trenchX = (lx == 0 || lx == 1) && ((ci + cj) & 1) == 0;                 // 칸 경계
            boolean trenchZ = (lz == 0 || lz == 1) && ((ci + cj) & 1) == 1;
            if (trenchX || trenchZ) {
                int along = trenchX ? z : x;
                int center = trenchX ? m.cz(cj) : m.cx(ci);
                if (Math.abs(along - center) == 0) {
                    p.set(x, F, z, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());     // 외줄 다리
                } else {
                    p.set(x, F - 2, z, Blocks.MAGMA_BLOCK.defaultBlockState());
                    p.set(x, F - 1, z, Blocks.LAVA.defaultBlockState());
                    p.set(x, F, z, Blocks.AIR.defaultBlockState());
                }
            }
        }
        if (inRoom) room(p, x, z, m, ri, rj, room, dr, F, hgt);
    }

    private static double seg(int x, int z, int x0, int z0, int x1, int z1) {
        double vx = x1 - x0, vz = z1 - z0;
        double t = Math.max(0, Math.min(1, ((x - x0) * vx + (z - z0) * vz) / (vx * vx + vz * vz)));
        return Math.hypot(x - x0 - vx * t, z - z0 - vz * t);
    }

    private void room(Placer p, int x, int z, Maze m, int i, int j, Room room, double dr, int F, int hgt) {
        int cx = m.cx(i), cz = m.cz(j), k = h(x, F + 7, z);
        boolean center = x == cx && z == cz;
        long ls = seed ^ ((long) i * 7919 + j * 104729L + F);
        switch (room) {
            case TREASURE -> {
                if (center) {
                    OldCapital.chest(p, cx, F + 1, cz, Direction.SOUTH, ls, m.fire ? BuiltInLootTables.BASTION_TREASURE : BuiltInLootTables.SIMPLE_DUNGEON);
                    OldCapital.spawner(p, cx + 3, F + 1, cz, m.fire ? EntityType.BLAZE : EntityType.SKELETON);
                }
                if (dr < 2.2 && !center) p.set(x, F, z, Blocks.GOLD_BLOCK.defaultBlockState());
            }
            case CAMP -> {                                                                       // 몰락한 모험가 야영지
                if (center) {
                    p.set(cx, F + 1, cz, Blocks.CAMPFIRE.defaultBlockState().setValue(net.minecraft.world.level.block.CampfireBlock.LIT, false));
                    OldCapital.chest(p, cx + 2, F + 1, cz + 2, Direction.WEST, ls, BuiltInLootTables.ABANDONED_MINESHAFT);
                    p.set(cx - 2, F + 1, cz, Blocks.RED_BED.defaultBlockState().setValue(net.minecraft.world.level.block.BedBlock.FACING, Direction.NORTH)
                            .setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD));
                    p.set(cx - 2, F + 1, cz + 1, Blocks.RED_BED.defaultBlockState().setValue(net.minecraft.world.level.block.BedBlock.FACING, Direction.NORTH)
                            .setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.FOOT));
                    p.set(cx + 2, F + 1, cz - 2, Blocks.SKELETON_SKULL.defaultBlockState());
                    for (int y = 1; y <= 2; y++) p.set(cx, F + y, cz - 3, Blocks.SPRUCE_FENCE.defaultBlockState());
                    p.set(cx, F + 3, cz - 3, Blocks.WHITE_BANNER.defaultBlockState());
                }
            }
            case OSSUARY -> {                                                                    // 납골당
                if (dr > 4 && k % 2 == 0) for (int y = 1; y <= 3; y++) p.set(x, F + y, z, y == 2 && k % 4 == 0 ? Blocks.SKELETON_SKULL.defaultBlockState() : Blocks.BONE_BLOCK.defaultBlockState());
                if (center) OldCapital.spawner(p, cx, F + 1, cz, m.fire ? EntityType.WITHER_SKELETON : EntityType.SKELETON);
            }
            case CRYSTAL -> {                                                                    // 자수정 동굴
                if (dr > 4) {
                    p.set(x, F, z, Blocks.AMETHYST_BLOCK.defaultBlockState());
                    if (k % 3 == 0) p.set(x, F + 1, z, Blocks.AMETHYST_CLUSTER.defaultBlockState());
                    p.set(x, F + hgt + 1, z, Blocks.AMETHYST_BLOCK.defaultBlockState());
                } else if (k % 7 == 0) p.set(x, F + 1, z, Blocks.LARGE_AMETHYST_BUD.defaultBlockState());
                if (center) OldCapital.chest(p, cx, F + 1, cz, Direction.NORTH, ls, BuiltInLootTables.ANCIENT_CITY);
            }
            case FLOODED -> {                                                                    // 잠긴 동굴
                if (dr < 4.5) {
                    p.set(x, F - 2, z, Blocks.CLAY.defaultBlockState());
                    p.set(x, F - 1, z, Blocks.WATER.defaultBlockState());
                    p.set(x, F, z, Blocks.WATER.defaultBlockState());
                    if (k % 5 == 0) p.set(x, F + hgt, z, Blocks.GLOW_LICHEN.defaultBlockState()
                            .setValue(net.minecraft.world.level.block.GlowLichenBlock.getFaceProperty(Direction.UP), true));
                }
                if (center) OldCapital.chest(p, cx, F - 2, cz, Direction.NORTH, ls, BuiltInLootTables.UNDERWATER_RUIN_SMALL);
            }
            case NEST -> {                                                                       // 알 둥지 (새끼 용의 알)
                if (dr < 3.2) {
                    p.set(x, F, z, Blocks.HAY_BLOCK.defaultBlockState());
                    if (k % 3 == 0) p.set(x, F + 1, z, Blocks.SNIFFER_EGG.defaultBlockState());
                }
                if (center) OldCapital.spawner(p, cx + 4, F + 1, cz, m.fire ? EntityType.MAGMA_CUBE : EntityType.CAVE_SPIDER);
            }
            case LAVA -> {                                                                       // 용암 웅덩이와 가운데 섬
                if (dr < 4.5 && dr > 1.6) {
                    p.set(x, F - 1, z, Blocks.MAGMA_BLOCK.defaultBlockState());
                    p.set(x, F, z, Blocks.LAVA.defaultBlockState());
                }
                if (center) OldCapital.chest(p, cx, F + 1, cz, Direction.SOUTH, ls, BuiltInLootTables.RUINED_PORTAL);
            }
            case SHRINE -> {                                                                     // 용 숭배 제단
                if (dr < 3) p.set(x, F, z, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
                if (center) {
                    p.set(cx, F + 1, cz, Blocks.CRYING_OBSIDIAN.defaultBlockState());
                    p.set(cx, F + 2, cz, Blocks.DRAGON_HEAD.defaultBlockState());
                    for (Direction d : Direction.Plane.HORIZONTAL) p.set(cx + d.getStepX() * 2, F + 1, cz + d.getStepZ() * 2, Blocks.SOUL_LANTERN.defaultBlockState());
                    OldCapital.spawner(p, cx - 4, F + 1, cz, EntityType.WITHER_SKELETON);
                }
            }
        }
    }

    // ── 3. 나선 계단 (가운데 기둥 + 반 칸씩 내려가는 디딤돌, 10칸에 한 바퀴)
    private void spiral(Placer p, int x, int z, int sx, int sz, int yTop, int yBot) {
        int dx = x - sx, dz = z - sz;
        double d = Math.hypot(dx, dz);
        if (d > 5.2) return;
        boolean fire = yBot < L1 - 5;
        for (int y = yBot + 1; y <= yTop + 4; y++) {
            BlockState s = p.get(x, y, z);
            if (s.is(Blocks.WATER)) continue;
            p.set(x, y, z, d <= 1.5 ? (fire ? Blocks.POLISHED_BASALT.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState()) : Blocks.AIR.defaultBlockState());
        }
        p.set(x, yBot, z, floorBlock(x, yBot, z, fire));
        if (d <= 1.5) {
            if (h(x, 9, z) % 2 == 0) for (int y = yBot + 2; y < yTop; y += 8) p.set(x, y, z, fire ? Blocks.SHROOMLIGHT.defaultBlockState() : Blocks.GLOWSTONE.defaultBlockState());
            return;
        }
        double ang = Math.atan2(dz, dx);
        if (ang < 0) ang += Math.PI * 2;
        final double pitch = 10;                                                             // 한 바퀴에 10칸
        for (int n = 0; ; n++) {
            double hy = yTop - (ang / (Math.PI * 2) + n) * pitch;                              // 이 칸의 디딤 높이
            if (hy < yBot) break;
            int y = (int) Math.floor(hy);
            boolean half = hy - y >= 0.5;
            BlockState step = fire ? Blocks.BLACKSTONE.defaultBlockState() : Blocks.COBBLED_DEEPSLATE.defaultBlockState();
            BlockState slab = (fire ? Blocks.BLACKSTONE_SLAB : Blocks.COBBLED_DEEPSLATE_SLAB).defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
            p.set(x, half ? y + 1 : y, z, half ? slab : step);
        }
        if (d > 4.3 && h(x, 10, z) % 9 == 0) {
            for (int y = yBot + 3; y < yTop; y += 7) p.set(x, y, z, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false));
        }
    }

    // ── 6. 보물 동굴
    private void hoard(Placer p, int x, int z) {
        int dx = x - MX, dz = z - MZ;
        double d = Math.hypot(dx, dz);
        if (d > HOARD_R + 2) return;
        double edge = HOARD_R + noise(x, z, 21) * 3 - 1.5;
        if (d > edge) return;
        int ceil = (int) Math.round(38 * Math.sqrt(Math.max(0, 1 - Math.pow(d / (HOARD_R + 2), 2)))) + 3;
        int F = HOARD_Y;
        int k = h(x, F, z);
        BlockState floor = k % 9 == 0 ? Blocks.MAGMA_BLOCK.defaultBlockState() : k % 3 == 0 ? Blocks.BASALT.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState();
        carve(p, x, z, F, ceil, floor);
        for (int y = F - 3; y < F; y++) p.set(x, y, z, Blocks.DEEPSLATE.defaultBlockState());
        // 황금 더미 (가운데가 높은 언덕)
        if (d < 13) {
            int pile = (int) Math.round(5 * (1 - d / 13.0) + noise(x, z, 22) * 1.5);
            for (int y = 1; y <= pile; y++) {
                int r = h(x, F + y, z) % 10;
                p.set(x, F + y, z, r < 5 ? Blocks.GOLD_BLOCK.defaultBlockState() : r < 8 ? Blocks.RAW_GOLD_BLOCK.defaultBlockState()
                        : r < 9 ? Blocks.EMERALD_BLOCK.defaultBlockState() : Blocks.GILDED_BLACKSTONE.defaultBlockState());
            }
            if (k % 31 == 0 && pile > 0) p.set(x, F + pile + 1, z, Blocks.CANDLE.defaultBlockState());
        }
        // 용암 웅덩이 (가장자리)
        if (d > 22 && d < edge - 3 && noise(x, z, 23) > 0.72) {
            p.set(x, F - 1, z, Blocks.MAGMA_BLOCK.defaultBlockState());
            p.set(x, F, z, Blocks.LAVA.defaultBlockState());
        }
        // 옛 용의 해골 (서쪽 갈비뼈 아치)
        if (dx < -14 && dx > -30 && Math.abs(dz) <= 9 && Math.floorMod(dx, 4) == 0) {
            int ribH = (int) Math.round(Math.sqrt(Math.max(0, 81 - dz * dz)) * 1.1);
            p.set(x, F + 1 + ribH, z, Blocks.BONE_BLOCK.defaultBlockState());
            if (Math.abs(dz) == 9) for (int y = 1; y <= ribH; y++) p.set(x, F + y, z, Blocks.BONE_BLOCK.defaultBlockState());
        }
        if (dz == 0 && dx <= -12 && dx >= -32) p.set(x, F + 1, z, Blocks.BONE_BLOCK.defaultBlockState()
                .setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS, Direction.Axis.X));      // 등뼈
        // 돌기둥·종유석
        if (d > 16 && k % 29 == 0) for (int y = 1; y <= ceil; y++) p.set(x, F + y, z, Blocks.BASALT.defaultBlockState());
        else if (k % 13 == 0) stalactite(p, x, F + ceil + 1, z);
        else if (d > 14 && k % 19 == 0) stalagmite(p, x, F + 1, z);
        // 보물 상자 세 개
        if (dx == 0 && dz == 15) OldCapital.chest(p, x, F + 1, z, Direction.NORTH, seed ^ 1, BuiltInLootTables.BURIED_TREASURE);
        if (dx == 12 && dz == -9) OldCapital.chest(p, x, F + 1, z, Direction.WEST, seed ^ 2, BuiltInLootTables.BASTION_TREASURE);
        if (dx == -12 && dz == -9) OldCapital.chest(p, x, F + 1, z, Direction.EAST, seed ^ 3, BuiltInLootTables.END_CITY_TREASURE);
    }
}
