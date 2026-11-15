package io.github.aswq0258.magiccity.build;

import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** 동시에 하나의 도시만 건설합니다. (서버가 꺼지면 진행 중인 건설은 취소됩니다) */
public final class CityBuildManager {
    /** 한 틱에 건설에 쓸 최대 시간. 렉이 심하면 줄이고, 빨리 짓고 싶으면 늘리세요. */
    private static final long TICK_BUDGET_NANOS = 25_000_000L; // 25ms

    private static CityBuildJob active;

    private CityBuildManager() {}

    public static boolean isRunning() {
        return active != null;
    }

    public static void start(CityBuildJob job) {
        active = job;
    }

    public static boolean cancel() {
        boolean was = active != null;
        active = null;
        return was;
    }

    public static String status() {
        return active == null ? "§7[마법 도시] 진행 중인 건설이 없습니다." : "§b[마법 도시] " + active.describe();
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        CityBuildJob job = active;
        if (job == null) return;
        if (job.tick(TICK_BUDGET_NANOS)) {
            active = null;
        }
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        active = null;
    }
}
