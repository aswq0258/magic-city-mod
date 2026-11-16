package io.github.aswq0258.magiccity.site;

import io.github.aswq0258.magiccity.build.BuildLayout;
import io.github.aswq0258.magiccity.build.Placer;
import io.github.aswq0258.magiccity.city.CityLayout;
import io.github.aswq0258.magiccity.city.Shapes;
import io.github.aswq0258.magiccity.city.feature.Feature;
import io.github.aswq0258.magiccity.world.KingdomRoads;
import io.github.aswq0258.magiccity.world.KingdomTerrain;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 서리 결투장 (4장 "서리의 행군", 보스 서리 거인 요툰) — 엘프 산맥 관문에서 길을 따라 북동쪽으로 약 120칸,
 * 길 북쪽 산자락을 깎아 만든 지름 40 의 얼음 원형 마당. 둘레에 얼음 기둥, 가운데 푸른 얼음 문양, 길에서 들어오는 오솔길.
 */
public final class FrostArena implements BuildLayout {
    public static final int R = 20;
    /** 결투장 중심과 바닥 높이, 길 위 입구 */
    public static final int AX, AZ, AY, RX, RZ;

    static {
        double[] gate = {ElfGate.GX, ElfGate.GZ};
        List<double[]> best = null;
        int bi = 0;
        double bd = 1e9;
        for (KingdomRoads.Road r : KingdomRoads.ROADS) {
            List<double[]> cl = KingdomRoads.centerline(r, 2);
            for (int i = 0; i < cl.size(); i++) {
                double d = Math.hypot(cl.get(i)[0] - gate[0], cl.get(i)[1] - gate[1]);
                if (d < bd) {
                    bd = d;
                    best = cl;
                    bi = i;
                }
            }
        }
        double[] road = gate, dir = {0.877, -0.48};
        if (best != null) {
            double[] fwd = best.get(Math.min(best.size() - 1, bi + 60)), back = best.get(Math.max(0, bi - 60));
            road = fwd[1] <= back[1] ? fwd : back;                                              // 북쪽으로 120칸 쯤
            dir = new double[]{road[0] - gate[0], road[1] - gate[1]};
            double l = Math.max(1, Math.hypot(dir[0], dir[1]));
            dir[0] /= l;
            dir[1] /= l;
        }
        double px = -dir[1], pz = dir[0];
        if (pz > 0) {                                                                            // 길의 북쪽으로
            px = -px;
            pz = -pz;
        }
        RX = (int) Math.round(road[0]);
        RZ = (int) Math.round(road[1]);
        AX = (int) Math.round(road[0] + px * 44);
        AZ = (int) Math.round(road[1] + pz * 44);
        AY = KingdomTerrain.column(AX, AZ).h();
    }

    private final long seed;
    private final List<ChunkPos> chunks = new ArrayList<>();

    public FrostArena(long seed) {
        this.seed = seed;
        LinkedHashSet<Long> keys = new LinkedHashSet<>();
        int x0 = Math.min(AX - R - 6, RX - 6), x1 = Math.max(AX + R + 6, RX + 6), z0 = Math.min(AZ - R - 6, RZ - 6), z1 = Math.max(AZ + R + 6, RZ + 6);
        for (int x = x0 >> 4; x <= x1 >> 4; x++) for (int z = z0 >> 4; z <= z1 >> 4; z++) keys.add(ChunkPos.asLong(x, z));
        for (long k : keys) chunks.add(new ChunkPos(k));
    }

    public static boolean near(double x, double z) {
        return Math.hypot(x - AX, z - AZ) < R + 30;
    }

    @Override public int cx() { return AX; }
    @Override public int cz() { return AZ; }
    @Override public int base() { return AY; }
    @Override public int radius() { return 0; }
    @Override public CityLayout city() { return null; }
    @Override public List<ChunkPos> chunkList() { return chunks; }
    @Override public List<Feature> featuresIn(ChunkPos cp) { return Collections.emptyList(); }
    @Override public int[] lightRange(int x, int z) { return null; }
    @Override public int shapeMinY() { return AY - 10; }
    @Override public int shapeMaxY() { return AY + 20; }

    private int h(int x, int y, int z) {
        return Shapes.hash(x, y, z, seed);
    }

    /** y 아래로 단단한 땅까지 채움 */
    private void fillDown(Placer p, int x, int y, int z) {
        for (int k = y; k > y - 40; k--) {
            BlockState s = p.get(x, k, z);
            if (!s.isAir() && s.getFluidState().isEmpty() && !s.canBeReplaced()) break;
            p.set(x, k, z, k > y - 3 ? Blocks.PACKED_ICE.defaultBlockState() : Blocks.STONE.defaultBlockState());
        }
    }

    @Override
    public void buildColumn(Placer p, int x, int z) {
        if (p.pass() != Placer.Pass.SOLID) return;
        double d = Math.hypot(x - AX, z - AZ);
        int k = h(x, AY, z);
        if (d <= R + 0.5) {
            p.clearAbove(x, z, AY + 1);
            double ang = Math.atan2(z - AZ, x - AX);
            boolean sigil = d < 5 && (d > 3.6 || Math.abs(Math.sin(ang * 3)) < 0.15);
            p.set(x, AY, z, sigil ? Blocks.BLUE_ICE.defaultBlockState() : k % 5 == 0 ? Blocks.SNOW_BLOCK.defaultBlockState()
                    : k % 3 == 0 ? Blocks.ICE.defaultBlockState() : Blocks.PACKED_ICE.defaultBlockState());
            fillDown(p, x, AY - 1, z);
            // 둘레 얼음 기둥 12개
            double seg = (ang + Math.PI) / (Math.PI * 2) * 12;
            if (d > R - 3.2 && d < R - 0.8 && Math.abs(seg - Math.round(seg)) < 0.09) {
                int hgt = 6 + Math.floorMod((int) Math.round(seg) * 7, 5);
                for (int y = 1; y <= hgt; y++) p.set(x, AY + y, z, y == hgt ? Blocks.BLUE_ICE.defaultBlockState() : Blocks.PACKED_ICE.defaultBlockState());
                p.set(x, AY + hgt + 1, z, Blocks.SOUL_LANTERN.defaultBlockState());
            } else if (d > 6 && k % 41 == 0) {
                p.set(x, AY + 1, z, Blocks.SNOW.defaultBlockState());
            } else if (d > 8 && k % 97 == 0) {
                p.set(x, AY + 1, z, Blocks.SKELETON_SKULL.defaultBlockState());
            }
            return;
        }
        // 길에서 들어오는 오솔길 (폭 5)
        double vx = AX - RX, vz = AZ - RZ, len2 = vx * vx + vz * vz;
        double t = Math.max(0, Math.min(1, ((x - RX) * vx + (z - RZ) * vz) / len2));
        double dd = Math.hypot(x - RX - vx * t, z - RZ - vz * t);
        if (dd > 2.5 || t <= 0.02) return;
        int gy = (int) Math.round(KingdomTerrain.column(RX, RZ).h() + (AY - KingdomTerrain.column(RX, RZ).h()) * t);
        p.clearBetween(x, z, gy + 1, gy + 7);
        p.set(x, gy, z, k % 4 == 0 ? Blocks.SNOW_BLOCK.defaultBlockState() : Blocks.PACKED_ICE.defaultBlockState());
        fillDown(p, x, gy - 1, z);
        if (dd > 1.8 && k % 11 == 0) {
            p.set(x, gy + 1, z, Blocks.SPRUCE_FENCE.defaultBlockState());
            p.set(x, gy + 2, z, Blocks.SOUL_LANTERN.defaultBlockState());
        }
    }
}
