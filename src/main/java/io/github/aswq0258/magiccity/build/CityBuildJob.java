package io.github.aswq0258.magiccity.build;

import io.github.aswq0258.magiccity.MagicCityMod;
import io.github.aswq0258.magiccity.city.CityLayout;
import io.github.aswq0258.magiccity.city.feature.Feature;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 도시 건설 작업. 매 서버 틱마다 정해진 시간만큼만 청크를 처리해서 렉을 줄입니다.
 * 중심에서 바깥쪽 순서로 SOLID 패스(건축) → WATER 패스(물·주민)를 진행합니다.
 * WATER 패스는 물이나 주민이 있는 청크만 다시 방문합니다.
 */
public final class CityBuildJob {
    private final ServerLevel level;
    private final BuildLayout layout;
    private final List<ChunkPos> chunks = new ArrayList<>();
    private final List<ChunkPos> waterChunks = new ArrayList<>();
    private Placer.Pass pass = Placer.Pass.SOLID;
    private int index = 0;
    private int lastReportedPercent = -1;
    private final long startMillis = System.currentTimeMillis();

    public CityBuildJob(ServerLevel level, BuildLayout layout) {
        this.level = level;
        this.layout = layout;
        List<ChunkPos> listed = layout.chunkList();
        if (listed != null) {
            chunks.addAll(listed);
            return;
        }
        int r = layout.radius() + 2;
        int c0x = (layout.cx() - r) >> 4, c1x = (layout.cx() + r) >> 4;
        int c0z = (layout.cz() - r) >> 4, c1z = (layout.cz() + r) >> 4;
        for (int x = c0x; x <= c1x; x++) {
            for (int z = c0z; z <= c1z; z++) {
                chunks.add(new ChunkPos(x, z));
            }
        }
        chunks.sort(Comparator.comparingDouble(this::distanceToChunk));
    }

    private double distanceToChunk(ChunkPos cp) {
        double nx = Math.max(cp.getMinBlockX(), Math.min(layout.cx(), cp.getMaxBlockX()));
        double nz = Math.max(cp.getMinBlockZ(), Math.min(layout.cz(), cp.getMaxBlockZ()));
        double dx = nx - layout.cx(), dz = nz - layout.cz();
        return Math.sqrt(dx * dx + dz * dz);
    }

    public int chunkCount() {
        return chunks.size();
    }

    private List<ChunkPos> current() {
        return pass == Placer.Pass.SOLID ? chunks : waterChunks;
    }

    public int percent() {
        if (pass == Placer.Pass.SOLID) return chunks.isEmpty() ? 90 : (int) (90L * index / chunks.size());
        return 90 + (waterChunks.isEmpty() ? 10 : (int) (10L * index / waterChunks.size()));
    }

    public String describe() {
        long sec = (System.currentTimeMillis() - startMillis) / 1000;
        return String.format("%s 패스, 전체 %d%% (%d/%d 청크), 경과 %d분 %d초",
                pass == Placer.Pass.SOLID ? "건축" : "물·주민", percent(), index, current().size(), sec / 60, sec % 60);
    }

    /** @return 모두 끝났으면 true */
    public boolean tick(long budgetNanos) {
        long start = System.nanoTime();
        do {
            if (index >= current().size()) {
                if (pass == Placer.Pass.SOLID) {
                    pass = Placer.Pass.WATER;
                    index = 0;
                    broadcast("§b[마법 도시] 건축 완료! 이제 해자·연못에 물을 채우고 주민을 불러옵니다. (" + waterChunks.size() + " 청크)");
                    continue;
                }
                long sec = (System.currentTimeMillis() - startMillis) / 1000;
                broadcast(String.format("§a[마법 도시] 완성! (%d분 %d초) 중심: %d, %d, %d",
                        sec / 60, sec % 60, layout.cx(), layout.base() + 1, layout.cz()));
                return true;
            }
            buildChunk(current().get(index++));
        } while (System.nanoTime() - start < budgetNanos);

        int pct = percent();
        if (pct / 5 != lastReportedPercent / 5) {
            lastReportedPercent = pct;
            broadcast("§7[마법 도시] " + describe());
        }
        return false;
    }

    private void buildChunk(ChunkPos cp) {
        level.getChunk(cp.x, cp.z); // 로드 또는 생성
        Placer p = new Placer(level, cp, pass);
        boolean spawns = false;
        try {
            for (int x = cp.getMinBlockX(); x <= cp.getMaxBlockX(); x++) {
                for (int z = cp.getMinBlockZ(); z <= cp.getMaxBlockZ(); z++) {
                    layout.buildColumn(p, x, z);
                }
            }
            long key = cp.toLong();
            for (Feature f : layout.featuresIn(cp)) {
                f.build(p, layout.city());
                if (f.anchorChunk() == key && f.needsSpawn()) {
                    spawns = true;
                    if (pass == Placer.Pass.WATER) f.spawn(level, layout.city());
                }
            }
            if (pass == Placer.Pass.SOLID) {
                p.finishShapes(layout.shapeMinY(), layout.shapeMaxY());
                p.lightInteriors(layout::lightRange);
            }
        } catch (RuntimeException e) {
            MagicCityMod.LOGGER.error("Magic City: failed to build chunk {}", cp, e);
        }
        if (pass == Placer.Pass.SOLID && (p.sawWater() || spawns)) waterChunks.add(cp);
    }

    private void broadcast(String msg) {
        level.getServer().getPlayerList().broadcastSystemMessage(Component.literal(msg), false);
    }
}
