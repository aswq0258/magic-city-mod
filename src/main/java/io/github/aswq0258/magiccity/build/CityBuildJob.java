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
 * 도시 건설 작업. 매 서버 틱마다 정해진 시간(기본 25ms)만큼만 청크를 처리해서 렉 없이 짓습니다.
 * 중심에서 바깥쪽 순서로 SOLID 패스 → WATER 패스(+주민 소환)를 진행합니다.
 */
public final class CityBuildJob {
    private final ServerLevel level;
    private final CityLayout layout;
    private final List<ChunkPos> chunks = new ArrayList<>();
    private Placer.Pass pass = Placer.Pass.SOLID;
    private int index = 0;
    private int lastReportedPercent = -1;
    private final long startMillis = System.currentTimeMillis();

    public CityBuildJob(ServerLevel level, CityLayout layout) {
        this.level = level;
        this.layout = layout;
        int r = layout.radius + 2;
        int c0x = (layout.cx - r) >> 4, c1x = (layout.cx + r) >> 4;
        int c0z = (layout.cz - r) >> 4, c1z = (layout.cz + r) >> 4;
        for (int x = c0x; x <= c1x; x++) {
            for (int z = c0z; z <= c1z; z++) {
                ChunkPos cp = new ChunkPos(x, z);
                if (distanceToChunk(cp) <= r) chunks.add(cp);
            }
        }
        chunks.sort(Comparator.comparingDouble(this::distanceToChunk));
    }

    private double distanceToChunk(ChunkPos cp) {
        double nx = Math.max(cp.getMinBlockX(), Math.min(layout.cx, cp.getMaxBlockX()));
        double nz = Math.max(cp.getMinBlockZ(), Math.min(layout.cz, cp.getMaxBlockZ()));
        double dx = nx - layout.cx, dz = nz - layout.cz;
        return Math.sqrt(dx * dx + dz * dz);
    }

    public int chunkCount() {
        return chunks.size();
    }

    public int percent() {
        int total = chunks.size() * 2;
        int done = (pass == Placer.Pass.SOLID ? 0 : chunks.size()) + index;
        return total == 0 ? 100 : (int) (100L * done / total);
    }

    public String describe() {
        long sec = (System.currentTimeMillis() - startMillis) / 1000;
        return String.format("%s 패스, %d%% (%d/%d 청크), 경과 %d초",
                pass == Placer.Pass.SOLID ? "건축" : "물·주민", percent(), index, chunks.size(), sec);
    }

    /** @return 모두 끝났으면 true */
    public boolean tick(long budgetNanos) {
        long start = System.nanoTime();
        do {
            if (index >= chunks.size()) {
                if (pass == Placer.Pass.SOLID) {
                    pass = Placer.Pass.WATER;
                    index = 0;
                    broadcast("§b[마법 도시] 건축 완료, 이제 운하·해자에 물을 채우고 주민을 불러옵니다.");
                } else {
                    long sec = (System.currentTimeMillis() - startMillis) / 1000;
                    broadcast(String.format("§a[마법 도시] 완성! (%d분 %d초) 중심: %d, %d, %d",
                            sec / 60, sec % 60, layout.cx, layout.base + 1, layout.cz));
                    return true;
                }
            }
            buildChunk(chunks.get(index++));
        } while (System.nanoTime() - start < budgetNanos);

        int pct = percent();
        if (pct / 10 != lastReportedPercent / 10) {
            lastReportedPercent = pct;
            broadcast("§7[마법 도시] " + describe());
        }
        return false;
    }

    private void buildChunk(ChunkPos cp) {
        level.getChunk(cp.x, cp.z); // 로드 또는 생성
        Placer p = new Placer(level, cp, pass);
        try {
            for (int x = cp.getMinBlockX(); x <= cp.getMaxBlockX(); x++) {
                for (int z = cp.getMinBlockZ(); z <= cp.getMaxBlockZ(); z++) {
                    layout.buildColumn(p, x, z);
                }
            }
            long key = cp.toLong();
            for (Feature f : layout.featuresIn(cp)) {
                f.build(p, layout);
                if (pass == Placer.Pass.WATER && f.anchorChunk() == key) {
                    f.spawn(level, layout);
                }
            }
        } catch (RuntimeException e) {
            MagicCityMod.LOGGER.error("Magic City: failed to build chunk {}", cp, e);
        }
    }

    private void broadcast(String msg) {
        level.getServer().getPlayerList().broadcastSystemMessage(Component.literal(msg), false);
    }
}
