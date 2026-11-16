package io.github.aswq0258.magiccity.site;

import io.github.aswq0258.magiccity.build.BuildLayout;
import io.github.aswq0258.magiccity.build.Placer;
import io.github.aswq0258.magiccity.city.CityLayout;
import io.github.aswq0258.magiccity.city.feature.Feature;
import io.github.aswq0258.magiccity.world.KingdomChunkGenerator;
import io.github.aswq0258.magiccity.world.KingdomRoads;
import io.github.aswq0258.magiccity.world.KingdomTerrain;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 철거: 왕국 대륙에 지은 건축물 자리를 원래 지형으로 되돌림.
 * 지형은 계산식(KingdomTerrain) 이라, 각 기둥을 지형 생성기와 똑같이 다시 채우면 원래 땅(길 포함) 이 됨.
 * 되돌린 빈 땅에는 바이옴에 맞는 나무·풀을 다시 심음 (길 가까이는 빼고). 이름 붙은 주민·정령·골렘, 보스·몬스터 무리도 치움.
 */
public final class Restore implements BuildLayout {
    /** 되돌릴 영역 {x0, z0, x1, z1, y0, y1} */
    private final List<int[]> areas = new ArrayList<>();
    private final Map<Long, List<Feature>> byChunk = new HashMap<>();
    private final List<ChunkPos> chunks = new ArrayList<>();
    private final String label;
    /** true = 왕국 대륙 (계산식 지형), false = 다른 차원 (지형 생성기의 기본 지형 + 흙·풀 표면) */
    private boolean formula = true;

    private Restore(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public int areaCount() {
        return areas.size();
    }

    private Restore add(int x0, int z0, int x1, int z1, int y0, int y1) {
        areas.add(new int[]{x0, z0, x1, z1, y0, y1});
        return this;
    }

    private Restore done() {
        LinkedHashSet<Long> keys = new LinkedHashSet<>();
        for (int[] a : areas) {
            Feature f = new Cleaner(a);
            for (int x = a[0] >> 4; x <= a[2] >> 4; x++) {
                for (int z = a[1] >> 4; z <= a[3] >> 4; z++) {
                    long k = ChunkPos.asLong(x, z);
                    keys.add(k);
                    byChunk.computeIfAbsent(k, q -> new ArrayList<>()).add(f);
                }
            }
        }
        for (long k : keys) chunks.add(new ChunkPos(k));
        return this;
    }

    // ───────────────────────── 대상별 ─────────────────────────

    public static Restore hamlets() {
        Restore r = new Restore("촌락 전체");
        for (Hamlets.Spot s : Hamlets.spots()) r.add(s.x() - s.half() - 40, s.z() - s.half() - 40, s.x() + s.half() + 40, s.z() + s.half() + 40, s.y() - 14, s.y() + 40);
        return r.done();
    }

    public static Restore nearestHamlet(double px, double pz) {
        Hamlets.Spot best = null;
        for (Hamlets.Spot s : Hamlets.spots()) if (best == null || Math.hypot(s.x() - px, s.z() - pz) < Math.hypot(best.x() - px, best.z() - pz)) best = s;
        Restore r = new Restore(best == null ? "촌락" : best.name());
        if (best != null) r.add(best.x() - best.half() - 40, best.z() - best.half() - 40, best.x() + best.half() + 40, best.z() + best.half() + 40, best.y() - 14, best.y() + 40);
        return r.done();
    }

    public static Restore roads() {
        Restore r = new Restore("길 거점 (쉼터·둥지·결투장)");
        for (RoadSites.Site s : RoadSites.sites()) {
            int e = s.kind() == RoadSites.Kind.ARENA ? 17 : 11;
            r.add(s.x() - e, s.z() - e, s.x() + e, s.z() + e, s.y() - 10, s.y() + 16);
        }
        return r.done();
    }

    public static Restore worldTree() {
        int b = KingdomTerrain.column(WorldTree.OX, WorldTree.OZ).h();
        return new Restore("세계수 성역").add(WorldTree.OX - WorldTree.R, WorldTree.OZ - WorldTree.R, WorldTree.OX + WorldTree.R, WorldTree.OZ + WorldTree.R, b - 12, b + 172).done();
    }

    public static Restore dwarf() {
        int f = KingdomTerrain.column(DwarfHold.CX, DwarfHold.CZ - 50).h();
        return new Restore("드워프 광산왕국").add(DwarfHold.CX - 83, DwarfHold.CZ - 225, DwarfHold.CX + 189, DwarfHold.CZ - 17, f - 30, f + 50).done();
    }

    /**
     * 왕성도시 (원형 구시가지 + 신시가지 + 외곽 생활권). 높이 범위는 중심에 가까울수록 높게
     * (왕성 첨탑 지면+156, 대성당·떠다니는 섬 +110, 신시가지 +62, 하수도 지면-30).
     */
    public static Restore city(int cx, int cz, int base, boolean kingdom) {
        int e = io.github.aswq0258.magiccity.city.CityLayout.EXP + 2;
        Restore r = new Restore("왕성도시 (구시가지 + 신시가지)");
        r.formula = kingdom;
        r.add(cx - e, cz - e, cx + e, cz + e, base - 14, base + 62);
        r.add(cx - 292, cz - 292, cx + 292, cz + 292, base - 32, base + 112);
        r.add(cx - 112, cz - 112, cx + 112, cz + 112, base - 32, base + 172);
        return r.done();
    }

    /** 왕국 대륙에 지은 것 전부 (촌락·길 거점·세계수·드워프·옛 왕도·항구도시) — 왕성도시는 따로 */
    public static List<Restore> allSites() {
        return List.of(hamlets(), roads(), worldTree(), dwarf(), oldCapital(), port(), dragon(), sky(), cursed(), border(), ruins(), elfGate());
    }

    /** 엘프 산맥 관문 */
    public static Restore elfGate() {
        return new Restore("엘프 산맥 관문").add(ElfGate.GX - 90, ElfGate.GZ - 90, ElfGate.GX + 90, ElfGate.GZ + 90, ElfGate.G - 12, ElfGate.G + 52).done();
    }

    /** 고대 왕국 유적 (발굴 구덩이 + 왕릉) */
    public static Restore ruins() {
        int e = AncientRuins.PIT_R + 26;
        return new Restore("고대 왕국 유적")
                .add(AncientRuins.CX - e, AncientRuins.CZ - e, AncientRuins.CX + e, AncientRuins.CZ + e, AncientRuins.TOMB_Y - 3, AncientRuins.G + 22)
                .add(AncientRuins.KING_X - 16, AncientRuins.KING_Z - 16, AncientRuins.KING_X + 22, AncientRuins.CZ, AncientRuins.TOMB_Y - 3, AncientRuins.G + 6)
                .done();
    }

    /** 남부 국경도시 (별 모양 요새·해자 포함) */
    public static Restore border() {
        int e = 150;
        return new Restore("남부 국경도시").add(BorderCity.VX - e, BorderCity.VZ - e, BorderCity.VX + e, BorderCity.VZ + e, BorderCity.B - 14, BorderCity.B + 45).done();
    }

    /** 저주받은 숲 + 심연 균열 (숲 전체를 원래 지형으로 — 시든 나무 대신 새 나무가 심어짐) */
    public static Restore cursed() {
        return new Restore("저주받은 숲·심연 균열")
                .add(CursedLands.WX - 580, CursedLands.WZ - 500, CursedLands.WX + 580, CursedLands.WZ + 500, 60, 110)
                .add(CursedLands.RX - 150, CursedLands.RZ - 150, CursedLands.RX + 150, CursedLands.RZ + 150, -34, 90)
                .done();
    }

    /** 부유섬 유적 (하늘의 섬들·탑·땅의 샘·야영지) */
    public static Restore sky() {
        return new Restore("부유섬 유적").add(SkyRuins.CX - 110, SkyRuins.CZ - 210, SkyRuins.CX + 110, SkyRuins.CZ + 40, 55, 250).done();
    }

    /** 고룡 봉우리 드래곤 레어 (지하 미궁 전체 + 동굴 입구) */
    public static Restore dragon() {
        int x0 = DragonLair.X0, z0 = DragonLair.Z0, sz = DragonLair.CELL * DragonLair.N;
        return new Restore("고룡 봉우리 드래곤 레어")
                .add(x0 - 8, z0 - 8, x0 + sz + 8, z0 + sz + 8, DragonLair.HOARD_Y - 5, DragonLair.L1 + 12)
                .add(DragonLair.MOUTH_X - 26, DragonLair.THROAT_Z - 8, DragonLair.MOUTH_X + 26, DragonLair.MOUTH_Z + 14, DragonLair.CAVE_Y - 6, DragonLair.CAVE_Y + 22)
                .add(DragonLair.THROAT_X - 8, DragonLair.THROAT_Z - 22, DragonLair.THROAT_X + 30, DragonLair.THROAT_Z + 8, DragonLair.L1 - 2, DragonLair.CAVE_Y + 6)
                .done();
    }

    /** 동해안 항구도시 + 방파제 + 등대 섬과 잠긴 성소 */
    public static Restore port() {
        int px = PortCity.PX, pz = PortCity.PZ, e = PortCity.MOLE_OUT + 8;
        int ix = PortCity.IX, iz = PortCity.IZ;
        return new Restore("동해안 항구도시·등대 섬")
                .add(px - e, pz - e, px + e, pz + e, PortCity.B - 24, PortCity.B + 48)
                .add(ix - PortCity.ISLE_R, iz - PortCity.ISLE_R, ix + PortCity.ISLE_R, iz + PortCity.ISLE_R, 30, 145)
                .add(ix - 280, iz - 20, ix - 115, iz + 20, 28, 75)
                .done();
    }

    public static Restore oldCapital() {
        OldCapital oc = new OldCapital(0);
        int r = oc.radius() + 2;
        return new Restore("몰락한 옛 왕도").add(oc.cx() - r, oc.cz() - r, oc.cx() + r, oc.cz() + r, OldCapital.Y - 26, OldCapital.Y + 40).done();
    }

    // ───────────────────────── 되돌리기 ─────────────────────────

    @Override
    public void buildColumn(Placer p, int x, int z) {
        int y0 = Integer.MAX_VALUE, y1 = Integer.MIN_VALUE;
        for (int[] a : areas) {
            if (x < a[0] || x > a[2] || z < a[1] || z > a[3]) continue;
            y0 = Math.min(y0, a[4]);
            y1 = Math.max(y1, a[5]);
        }
        if (y0 == Integer.MAX_VALUE) return;
        if (!formula) {
            restoreGeneric(p, x, z, y0, y1);
            return;
        }
        KingdomTerrain.Column c = KingdomTerrain.column(x, z);
        y1 = Math.max(y1, c.h() + 6);
        y0 = Math.min(y0, c.h() - 4);
        // 지형 위에 남은 나무·풀까지 정리
        if (p.pass() == Placer.Pass.SOLID) {
            int top = p.level().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x, z);
            y1 = Math.max(y1, Math.min(top, y1 + 40));
        }
        for (int y = y0; y <= y1; y++) {
            BlockState s = KingdomChunkGenerator.blockAt(c, x, y, z);
            p.set(x, y, z, s == null ? Blocks.AIR.defaultBlockState() : s);
        }
        if (p.pass() == Placer.Pass.WATER && !KingdomRoads.excluded(x, z)) io.github.aswq0258.magiccity.world.Regrow.column(p.level(), x, z);   // 나무·풀 다시 심기
    }

    @Override
    public boolean waterPassAll() {
        return formula;
    }

    /**
     * 왕국 대륙이 아닌 곳: 지형 생성기의 기본 지형(돌·물·공기)을 다시 채우고, 맨 위를 흙·풀(물가는 모래)로 덮음.
     * 원래 바이옴 표면(눈·모래사막 등)과 나무는 되살아나지 않음.
     */
    private void restoreGeneric(Placer p, int x, int z, int y0, int y1) {
        ServerLevel level = p.level();
        var gen = level.getChunkSource().getGenerator();
        var rs = level.getChunkSource().randomState();
        net.minecraft.world.level.NoiseColumn col = gen.getBaseColumn(x, z, level, rs);
        if (p.pass() == Placer.Pass.SOLID) {
            int top = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x, z);
            y1 = Math.max(y1, Math.min(top, y1 + 40));
        }
        int surface = Integer.MIN_VALUE;
        for (int y = Math.min(y1 + 40, level.getMaxBuildHeight() - 1); y >= y0; y--) {
            BlockState s = col.getBlock(y);
            if (!s.isAir() && s.getFluidState().isEmpty()) {
                surface = y;
                break;
            }
        }
        int sea = gen.getSeaLevel();
        for (int y = y0; y <= y1; y++) {
            BlockState s = col.getBlock(y);
            if (surface != Integer.MIN_VALUE && !s.isAir() && s.getFluidState().isEmpty() && y > surface - 4) {
                boolean wet = surface < sea;
                s = y == surface ? (wet || surface <= sea + 1 ? Blocks.SAND.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState())
                        : (wet ? Blocks.SAND.defaultBlockState() : Blocks.DIRT.defaultBlockState());
            }
            p.set(x, y, z, s);
        }
    }

    /** 영역의 이름 붙은 주민·정령·골렘, 보스·몬스터 무리를 치움 (물 패스 때 한 번) */
    private static final class Cleaner extends Feature {
        private final int[] a;

        Cleaner(int[] a) {
            super(a[0], a[1], a[2], a[3]);
            this.a = a;
        }

        @Override
        public void build(Placer p, CityLayout city) {}

        @Override
        public boolean needsSpawn() {
            return true;
        }

        @Override
        public void spawn(ServerLevel level, CityLayout city) {
            AABB box = new AABB(a[0], a[4] - 4, a[1], a[2] + 1, a[5] + 40, a[3] + 1);
            for (Entity e : level.getEntitiesOfClass(Mob.class, box, m -> m.hasCustomName()
                    || m.getTags().stream().anyMatch(t -> t.startsWith("magiccity")))) {
                e.discard();
            }
        }
    }

    @Override public int cx() { return KingdomTerrain.CX; }
    @Override public int cz() { return KingdomTerrain.CZ; }
    @Override public int base() { return KingdomTerrain.SEA; }
    @Override public int radius() { return 0; }
    @Override public CityLayout city() { return null; }
    @Override public List<ChunkPos> chunkList() { return chunks; }
    @Override public int shapeMinY() { return 40; }
    @Override public int shapeMaxY() { return 220; }

    @Override
    public List<Feature> featuresIn(ChunkPos cp) {
        return byChunk.getOrDefault(cp.toLong(), Collections.emptyList());
    }

    @Override
    public int[] lightRange(int x, int z) {
        return null;
    }
}
