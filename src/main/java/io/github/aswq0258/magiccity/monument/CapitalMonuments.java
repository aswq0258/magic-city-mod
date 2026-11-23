package io.github.aswq0258.magiccity.monument;

import io.github.aswq0258.magiccity.build.CityRecord;
import io.github.aswq0258.magiccity.city.CityLayout;
import io.github.aswq0258.magiccity.city.feature.AcademyHall;
import io.github.aswq0258.magiccity.city.feature.Feature;
import io.github.aswq0258.magiccity.city.feature.Keep;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

import java.util.ArrayList;
import java.util.List;

/**
 * 왕성도시 1차 웅장화 (2026-11-23, 1판). 모두 "덧붙이기"만 함 — 기존 블록을 바꾸지 않고 빈 공간에만 놓음.
 *
 *  1. 왕궁 왕관 첨탑 4개: 주탑 3단(+70~+86) 둘레, 2단 지붕 위에 높이가 다른 가는 첨탑(18·22·26·30) → 주탑을 겹겹이 받치는 궁전군 실루엣
 *  2. 왕궁 서쪽 플라잉 버트레스 3개: 꼭대기 대지(+28)의 기둥에서 주탑 1단 서쪽 벽(+46)으로 날아 붙는 아치 보강
 *  3. 성채 전망 발코니 4개: 1겹(+12)·2겹(+22) 성벽 바깥면 남서·남동에 내민 반원 발코니 (난간·등불·벤치·까치발)
 *  4. 외성문 관문 왕관 4개: 동·서·남·북 성문 탑 두 개 사이, 성벽 위에 걸친 열주 회랑 + 박공 + 금 문장
 *     → 옛 외성문을 "구시가지와 신시가지 사이의 내부 관문"으로 읽히게 함
 *  5. 마법 학원 대첨탑: 학원 강당 부지 모서리의 5×5 종탑(높이 약 44, 구리 지붕, 종) — 원거리에서 학원 구역을 알아보는 표지
 * 왕좌의 전당 확대·예배당의 대성당화처럼 기존 블록을 바꿔야 하는 개선은 다음 판(교체 모드)에서.
 */
public final class CapitalMonuments implements Monument {
    @Override public String id() { return "capital1"; }
    @Override public int version() { return 1; }
    @Override public String title() { return "왕성도시 1차 (왕궁·성문·학원)"; }

    private static BlockState b(Block k) {
        return k.defaultBlockState();
    }

    private static final BlockState QBRICK = b(Blocks.QUARTZ_BRICKS), SQUARTZ = b(Blocks.SMOOTH_QUARTZ), CQUARTZ = b(Blocks.CHISELED_QUARTZ_BLOCK),
            GOLD = b(Blocks.GOLD_BLOCK), CALCITE = b(Blocks.CALCITE), PANE = b(Blocks.WHITE_STAINED_GLASS_PANE), ROD = b(Blocks.END_ROD),
            QWALL = b(Blocks.DIORITE_WALL), LANTERN = b(Blocks.LANTERN);

    private static BlockState pillar() {
        return Blocks.QUARTZ_PILLAR.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
    }

    private static BlockState stairs(Direction facing, boolean top) {
        return Blocks.QUARTZ_STAIRS.defaultBlockState().setValue(StairBlock.FACING, facing).setValue(StairBlock.HALF, top ? Half.TOP : Half.BOTTOM);
    }

    private CityRecord record(ServerLevel level) {
        CityRecord rec = CityRecord.get(level.getServer());
        return rec.present() ? rec : null;
    }

    @Override
    public String checkWorld(ServerLevel level) {
        CityRecord rec = record(level);
        if (rec == null) return "왕성도시 건설 기록(magiccity_city)이 없음 — /magiccity build 로 지은 월드가 아님";
        if (!rec.dim.equals(level.dimension().location().toString())) return "왕성도시는 " + rec.dim + " 에 있음 (지금 차원과 다름)";
        // 표본: 3겹 성벽(r 43~45, 높이 32) 가운데 높이의 블록이 대부분 있어야 같은 생성기로 지은 성채
        int hit = 0, n = 36;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            if (Math.abs(Math.sin(a) + 1) < 0.08) continue;                        // 남쪽 대계단 자리는 뺌
            int x = rec.cx + (int) Math.round(Math.cos(a) * 44), z = rec.cz - (int) Math.round(Math.sin(a) * 44);
            if (!level.getBlockState(m.set(x, rec.base + 12, z)).isAir()) hit++;
        }
        if (hit < n * 0.7) return String.format("성채 3겹 성벽 표본 %d/%d 만 있음 — 지은 뒤 크게 바뀌었거나 다른 판의 도시", hit, n);
        return null;
    }

    @Override
    public List<List<MonumentPlan>> elements(ServerLevel level) {
        CityRecord rec = record(level);
        List<List<MonumentPlan>> out = new ArrayList<>();
        if (rec == null) return out;
        CityLayout city = new CityLayout(rec.cx, rec.cz, rec.base, rec.seed);
        Keep keep = null;
        AcademyHall academy = null;
        for (Feature f : city.features()) {
            if (f instanceof Keep k && keep == null) keep = k;
            if (f instanceof AcademyHall a && academy == null) academy = a;
        }
        int B = rec.base, cx = rec.cx, cz = rec.cz;
        if (keep != null) {
            crownSpires(out, keep);
            buttresses(out, keep, cx, cz, B);
        }
        balconies(out, cx, cz, B);
        gateCrowns(out, cx, cz, B);
        if (academy != null) academySpire(out, academy);
        return out;
    }

    // ───────────────────────── 1. 왕관 첨탑 ─────────────────────────

    /** Keep: 1단 바닥 = groundY + 28, 2단 지붕(3단 바닥) = +70. 3단 발자국 = minX+12..maxX-14, minZ+10..maxZ-14 */
    private void crownSpires(List<List<MonumentPlan>> out, Keep k) {
        int y0 = k.groundY + 28 + 24 + 18;                                         // 2단 지붕 높이
        int x0 = k.minX + 12, x1 = k.maxX - 14, z0 = k.minZ + 10, z1 = k.maxZ - 14;
        int[][] at = {{x0 - 2, z0 + 2, 18}, {x1 + 2, z0 + 2, 26}, {x0 - 2, z1 - 2, 22}, {x1 + 2, z1 - 2, 30}};
        String[] names = {"왕관 첨탑 북서", "왕관 첨탑 북동", "왕관 첨탑 남서", "왕관 첨탑 남동"};
        for (int i = 0; i < at.length; i++) {
            List<MonumentPlan> cand = new ArrayList<>();
            for (int v = 0; v < 2; v++) {                                          // 후보 2: 한 칸 더 바깥
                int sx = at[i][0] + (v == 0 ? 0 : (at[i][0] < x0 ? -1 : 1)), sz = at[i][1];
                cand.add(slenderSpire(new MonumentPlan(names[i], v), sx, y0, sz, at[i][2]));
            }
            out.add(cand);
        }
    }

    /** 3 x 3 가는 첨탑: 모서리 석영 기둥, 면은 매끄러운 석영 + 세로 창, 6칸마다 금 띠, 꼭대기 금 고리 + 바늘 */
    private static MonumentPlan slenderSpire(MonumentPlan p, int x, int y0, int z, int h) {
        p.support(x, y0, z);
        for (int ly = 1; ly <= h; ly++) {
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                boolean corner = dx != 0 && dz != 0, center = dx == 0 && dz == 0;
                BlockState s;
                if (center) s = QBRICK;
                else if (corner) s = ly % 6 == 0 ? GOLD : pillar();
                else s = ly % 6 == 0 ? GOLD : (ly % 6 >= 2 && ly % 6 <= 4 && ly < h - 2) ? PANE : SQUARTZ;
                p.set(x + dx, y0 + ly, z + dz, s);
            }
        }
        int t = y0 + h;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) p.set(x + dx, t + 1, z + dz, (dx == 0 || dz == 0) ? CQUARTZ : SQUARTZ);
        p.set(x, t + 2, z, pillar());
        p.set(x, t + 3, z, pillar());
        p.set(x, t + 4, z, GOLD);
        p.set(x, t + 5, z, ROD);
        return p;
    }

    // ───────────────────────── 2. 플라잉 버트레스 ─────────────────────────

    private void buttresses(List<List<MonumentPlan>> out, Keep k, int cx, int cz, int B) {
        int ground = k.groundY + 28;                                               // 꼭대기 대지 높이
        int wallX = k.minX;                                                        // 1단 서쪽 벽
        int[] zs = {k.minZ + 11, k.minZ + 24, k.maxZ - 4};
        for (int i = 0; i < zs.length; i++) {
            List<MonumentPlan> cand = new ArrayList<>();
            for (int v = 0; v < 2; v++) {
                int z = zs[i] + (v == 0 ? 0 : 2);
                int px = wallX - 8;
                if (Math.hypot(px - cx, z - cz) > 41.5) px = wallX - 7;             // 3겹 성벽(r 43) 안쪽에 둠
                MonumentPlan p = new MonumentPlan("플라잉 버트레스 " + (i + 1), v);
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                    p.support(px + dx, ground, z + dz);
                    for (int y = ground + 1; y <= ground + 16; y++) {
                        boolean corner = dx != 0 && dz != 0;
                        BlockState s = (y - ground) % 5 == 0 ? CQUARTZ : corner ? pillar() : QBRICK;
                        p.set(px + dx, y, z + dz, s);
                    }
                    p.set(px + dx, ground + 17, z + dz, GOLD);
                }
                p.set(px, ground + 18, z, pillar());
                p.set(px, ground + 19, z, pillar());
                p.set(px, ground + 20, z, GOLD);
                p.set(px, ground + 21, z, ROD);
                // 날개 아치: 기둥 머리(+15)에서 벽(+19)까지 올라가는 두 칸 두께의 띠, 아랫면은 계단으로 다듬음
                int from = px + 2, to = wallX - 1;
                for (int x = from; x <= to; x++) {
                    double t = (x - from) / (double) Math.max(1, to - from);
                    int y = ground + 15 + (int) Math.round(4 * Math.sqrt(t));
                    p.set(x, y, z, QBRICK);
                    p.set(x, y + 1, z, x == to ? CQUARTZ : SQUARTZ);
                    if (x < to) p.set(x, y - 1, z, stairs(Direction.WEST, true));
                }
                p.support(wallX, ground + 19, z);
                cand.add(p);
            }
            out.add(cand);
        }
    }

    // ───────────────────────── 3. 전망 발코니 ─────────────────────────

    private void balconies(List<List<MonumentPlan>> out, int cx, int cz, int B) {
        int[][] rings = {{71, 12}, {57, 22}};                                       // {바깥 반지름, 성벽 높이}
        int[] angles = {240, 300};
        for (int[] ring : rings) for (int ang : angles) {
            List<MonumentPlan> cand = new ArrayList<>();
            int v = 0;
            for (int da : new int[]{0, -4, 4}) {
                MonumentPlan p = new MonumentPlan(String.format("전망 발코니 r%d %s", ring[0], ang < 270 ? "남서" : "남동"), v++);
                balcony(p, cx, cz, B + ring[1], ring[0], Math.toRadians(ang + da));
                cand.add(p);
            }
            out.add(cand);
        }
    }

    private static void balcony(MonumentPlan p, int cx, int cz, int y, int rOut, double a) {
        double ux = Math.cos(a), un = Math.sin(a);                                   // 바깥 방향 (du, dn)
        double ccu = ux * (rOut + 1), ccn = un * (rOut + 1);                         // 반원 중심 = 성벽 바깥면
        int wx = cx + (int) Math.round(ux * (rOut - 1)), wz = cz - (int) Math.round(un * (rOut - 1));
        p.support(wx, y, wz);
        for (int du = (int) ccu - 8; du <= (int) ccu + 8; du++) for (int dn = (int) ccn - 8; dn <= (int) ccn + 8; dn++) {
            double ex = du - ccu, en = dn - ccn;
            double d = Math.hypot(ex, en);
            double out = ex * ux + en * un;                                          // 바깥쪽으로 얼마나 나왔나
            if (d > 6.2 || out < 0.6 || Math.hypot(du, dn) <= rOut + 0.5) continue;
            int x = cx + du, z = cz - dn;
            boolean rim = d > 5.2;
            p.set(x, y, z, rim ? CQUARTZ : ((du + dn) & 1) == 0 ? SQUARTZ : CALCITE);
            if (rim) {
                p.set(x, y + 1, z, QWALL);
                p.set(x, y - 1, z, stairs(dirToward(ux, un), true));                // 까치발
            } else if (d > 3.5) p.set(x, y - 1, z, SQUARTZ);
            if (d < 1.0) for (int k = 1; k <= 4; k++) p.set(x, y - k, z, k == 4 ? CQUARTZ : pillar());   // 가운데 받침 기둥
        }
        // 등불 기둥 2개 (반원 양끝) + 벤치
        for (int s = -1; s <= 1; s += 2) {
            double la = a + s * 0.62;
            int lx = cx + (int) Math.round(Math.cos(la) * (rOut + 4.5)), lz = cz - (int) Math.round(Math.sin(la) * (rOut + 4.5));
            p.set(lx, y + 1, lz, QWALL);
            p.set(lx, y + 2, lz, QWALL);
            p.set(lx, y + 3, lz, LANTERN);
        }
        int bx = cx + (int) Math.round(ux * (rOut + 3)), bz = cz - (int) Math.round(un * (rOut + 3));
        Direction toWall = dirToward(-ux, -un);
        p.set(bx, y + 1, bz, Blocks.QUARTZ_STAIRS.defaultBlockState().setValue(StairBlock.FACING, toWall));
    }

    /** (du, dn) 방향에 가장 가까운 마인크래프트 방향 (dn + = 북) */
    private static Direction dirToward(double du, double dn) {
        if (Math.abs(du) >= Math.abs(dn)) return du > 0 ? Direction.EAST : Direction.WEST;
        return dn > 0 ? Direction.NORTH : Direction.SOUTH;
    }

    // ───────────────────────── 4. 외성문 관문 왕관 ─────────────────────────

    private void gateCrowns(List<List<MonumentPlan>> out, int cx, int cz, int B) {
        String[] names = {"동문", "북문", "서문", "남문"};
        for (int g = 0; g < 4; g++) {
            List<MonumentPlan> cand = new ArrayList<>();
            for (int v = 0; v < 2; v++) {
                MonumentPlan p = new MonumentPlan(names[g] + " 관문 왕관", v);
                gateCrown(p, cx, cz, B + CityLayout.WALL_H, B + CityLayout.WALL_H + 2 + v, g * 90);
                cand.add(p);
            }
            out.add(cand);
        }
    }

    /**
     * 성벽(r 253~259) 위, 성문 탑 두 개 사이(좌우 ±6)에 걸친 관문 왕관.
     * y0: 받침 띠 → 열주 회랑(기둥 좌우 -6·-2·2·6, 안팎 두 면) → 처마 띠(금) → 프리즈 → 박공(금 문장) → 꼭대기 금 + 바늘
     */
    private static void gateCrown(MonumentPlan p, int cx, int cz, int wallTop, int y0, int deg) {
        double a = Math.toRadians(deg);
        double ru = Math.cos(a), rn = Math.sin(a), tu = -Math.sin(a), tn = Math.cos(a);
        int rIn = CityLayout.R_WALL_IN, rOut = CityLayout.R_WALL_OUT;
        int sx = cx + (int) Math.round(ru * (rIn + rOut) / 2.0), sz = cz - (int) Math.round(rn * (rIn + rOut) / 2.0);
        p.support(sx, wallTop, sz);                                                // 성문 위 성벽이 있어야 함
        for (int r = rIn; r <= rOut; r++) for (int t = -6; t <= 6; t++) {
            int x = cx + (int) Math.round(ru * r + tu * t), z = cz - (int) Math.round(rn * r + tn * t);
            boolean face = r == rIn || r == rOut;
            boolean col = Math.abs(t) == 6 || Math.abs(t) == 2;
            p.set(x, y0, z, QBRICK);
            p.set(x, y0 + 1, z, face ? CQUARTZ : SQUARTZ);
            for (int ly = 2; ly <= 7; ly++) {
                if (face && col) p.set(x, y0 + ly, z, pillar());
                else if (face && ly >= 6) {                                        // 기둥 사이 아치 머리
                    int dc = Math.min(Math.abs(Math.abs(t) - 2), Math.abs(Math.abs(t) - 6));
                    if (Math.abs(t) < 2) dc = 2 - Math.abs(t);
                    if (ly == 7 || dc <= 0) p.set(x, y0 + ly, z, QBRICK);
                    else if (ly == 6 && dc == 1) p.set(x, y0 + ly, z, QBRICK);
                }
            }
            p.set(x, y0 + 8, z, face ? GOLD : QBRICK);
            p.set(x, y0 + 9, z, face ? CQUARTZ : QBRICK);
            p.set(x, y0 + 10, z, face && (t & 1) == 0 ? CQUARTZ : SQUARTZ);
            p.set(x, y0 + 11, z, face ? b(Blocks.SMOOTH_QUARTZ_SLAB) : SQUARTZ);
            // 박공 (삼각형): 바깥·안쪽 면만 채우고 속은 지붕으로
            for (int ly = 12; ly <= 17; ly++) {
                double half = 6 - (ly - 12) * 1.25;
                if (Math.abs(t) > half) continue;
                BlockState s = face ? QBRICK : SQUARTZ;
                if (face && t == 0 && (ly == 14 || ly == 15)) s = GOLD;               // 금 문장
                if (Math.abs(t) > half - 1 && !face) s = b(Blocks.SMOOTH_QUARTZ_STAIRS);
                p.set(x, y0 + ly, z, s);
            }
        }
        int tx = sx, tz = sz;
        p.set(tx, y0 + 18, tz, GOLD);
        p.set(tx, y0 + 19, tz, ROD);
        // 양끝 작은 첨탑 (박공 모서리)
        for (int s = -1; s <= 1; s += 2) for (int r : new int[]{rIn, rOut}) {
            int x = cx + (int) Math.round(ru * r + tu * 6 * s), z = cz - (int) Math.round(rn * r + tn * 6 * s);
            p.set(x, y0 + 12, z, QWALL);
            p.set(x, y0 + 13, z, LANTERN);
        }
    }

    // ───────────────────────── 5. 학원 대첨탑 ─────────────────────────

    private void academySpire(List<List<MonumentPlan>> out, AcademyHall a) {
        List<MonumentPlan> cand = new ArrayList<>();
        int g = a.groundY;
        int[][] corners = {{a.maxX - 2, a.minZ + 2}, {a.minX + 2, a.minZ + 2}, {a.maxX - 2, a.maxZ - 2}, {a.minX + 2, a.maxZ - 2}};
        for (int i = 0; i < corners.length; i++) {
            MonumentPlan p = new MonumentPlan("마법 학원 대첨탑", i);
            bellTower(p, corners[i][0], g, corners[i][1]);
            cand.add(p);
        }
        out.add(cand);
    }

    /** 5 x 5 종탑: 석영 벽 + 프리즈머린 띠, 세로 창, 사다리, +34 종루(사방 아치), 구리 뾰족 지붕, 바늘 */
    private static void bellTower(MonumentPlan p, int x, int g, int z) {
        final int H = 34, BELL = H + 1;
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) p.support(x + dx, g, z + dz);
        for (int ly = 1; ly <= H + 5; ly++) for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            boolean edge = Math.abs(dx) == 2 || Math.abs(dz) == 2, corner = Math.abs(dx) == 2 && Math.abs(dz) == 2;
            int y = g + ly;
            if (ly <= H) {
                if (!edge) {
                    p.set(x + dx, y, z + dz, ly == H ? SQUARTZ : b(Blocks.AIR));
                    continue;
                }
                boolean door = ly <= 2 && dz == 2 && dx == 0;
                boolean window = !corner && (dx == 0 || dz == 0) && ly % 8 >= 4 && ly % 8 <= 6 && ly > 4;
                BlockState s = door ? b(Blocks.AIR) : corner ? pillar() : ly % 8 == 0 ? b(Blocks.PRISMARINE_BRICKS) : window ? PANE : SQUARTZ;
                p.set(x + dx, y, z + dz, s);
            } else {                                                                // 종루: 모서리 기둥만, 사방이 트임
                if (corner) p.set(x + dx, y, z + dz, pillar());
                else if (ly == H + 5) p.set(x + dx, y, z + dz, CQUARTZ);
            }
        }
        // 사다리 (북쪽 안벽) — 종루까지
        for (int ly = 1; ly <= H; ly++) p.set(x, g + ly, z - 1, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH));
        p.set(x, g + H, z - 1, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH));
        p.set(x, g + H + 4, z, Blocks.BELL.defaultBlockState().setValue(BellBlock.ATTACHMENT, BellAttachType.CEILING));
        p.set(x, g + H + 5, z, CQUARTZ);
        // 구리 뾰족 지붕 (7층으로 줄어듦)
        for (int k = 0; k < 7; k++) {
            int r = 3 - (k + 1) / 2;
            int y = g + H + 6 + k;
            for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
                if (Math.abs(dx) != r && Math.abs(dz) != r && k < 6) continue;
                p.set(x + dx, y, z + dz, b(Blocks.WAXED_OXIDIZED_CUT_COPPER));
            }
        }
        p.set(x, g + H + 13, z, GOLD);
        p.set(x, g + H + 14, z, ROD);
        // 종루 처마 등불
        for (int[] o : new int[][]{{-3, 0}, {3, 0}, {0, -3}, {0, 3}})
            p.set(x + o[0], g + H + 5, z + o[1], Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
    }

    @SuppressWarnings("unused")
    private static BlockState slab() {
        return Blocks.SMOOTH_QUARTZ_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
    }
}
