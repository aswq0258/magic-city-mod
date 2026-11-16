package io.github.aswq0258.magiccity.build;

import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayDeque;
import java.util.Deque;

/** 한 번에 하나씩 건설(철거) 합니다. 여러 작업은 차례로 대기열에서 이어서 진행. (서버가 꺼지면 모두 취소) */
public final class CityBuildManager {
    /** 한 틱에 건설에 쓸 최대 시간. 렉이 심하면 줄이고, 빨리 짓고 싶으면 늘리세요. */
    private static final long TICK_BUDGET_NANOS = 40_000_000L; // 40ms

    private static CityBuildJob active;
    private static final Deque<CityBuildJob> queue = new ArrayDeque<>();

    private CityBuildManager() {}

    public static boolean isRunning() {
        return active != null;
    }

    public static void start(CityBuildJob job) {
        active = job;
    }

    /** 대기열에 추가 (진행 중인 작업이 없으면 바로 시작) */
    public static void enqueue(CityBuildJob job) {
        if (active == null) active = job;
        else queue.add(job);
    }

    public static boolean cancel() {
        boolean was = active != null;
        active = null;
        queue.clear();
        return was;
    }

    public static String status() {
        if (active == null) return "§7[마법 도시] 진행 중인 건설이 없습니다.";
        return "§b[마법 도시] " + active.describe() + (queue.isEmpty() ? "" : " §7(대기 중인 작업 " + queue.size() + "개)");
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        CityBuildJob job = active;
        if (job == null) return;
        if (job.tick(TICK_BUDGET_NANOS)) {
            active = queue.poll();
        }
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        active = null;
        queue.clear();
    }
}
