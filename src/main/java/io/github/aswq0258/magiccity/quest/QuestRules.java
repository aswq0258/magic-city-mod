package io.github.aswq0258.magiccity.quest;

import java.util.Collection;
import java.util.Set;

/**
 * 개인 퀘스트 진행 규칙 (Minecraft 비의존, 단위 테스트 대상).
 *
 * <p>진행 값: {@link #NOT_STARTED}(-1) = 받지 않음, 0 ~ 단계 수 - 1 = 그 단계 진행 중, 단계 수 = 완료.
 */
public final class QuestRules {
    private QuestRules() {}

    public static final int NOT_STARTED = -1;

    public static boolean started(int state) {
        return state != NOT_STARTED;
    }

    public static boolean done(QuestDef q, int state) {
        return state >= q.size();
    }

    public static boolean inProgress(QuestDef q, int state) {
        return state >= 0 && state < q.size();
    }

    /** 진행 중이면 지금 단계, 아니면 null */
    public static QuestDef.Step current(QuestDef q, int state) {
        return inProgress(q, state) ? q.steps().get(state) : null;
    }

    /** NPC가 이 퀘스트를 건넬 수 있는지: 그 NPC가 주는 퀘스트이고, 받지 않았고, 먼저 끝낼 퀘스트를 모두 끝냄 */
    public static boolean offerable(QuestDef q, int state, String npc, Collection<String> doneQuests) {
        return q.giver().equals(npc) && !started(state) && doneQuests.containsAll(q.requires());
    }

    /** 다른 모드 신호가 지금 단계를 끝내는지 */
    public static boolean signalMatches(QuestDef q, int state, String signal) {
        QuestDef.Step s = current(q, state);
        return s != null && s.type() == QuestDef.Type.SIGNAL && s.signal().equals(signal);
    }

    /** 이 NPC와 대화하면 지금 단계가 끝나는지 */
    public static boolean talkCompletes(QuestDef q, int state, String npc) {
        QuestDef.Step s = current(q, state);
        return s != null && s.type() == QuestDef.Type.TALK_NPC && s.npc().equals(npc);
    }

    /** 이 NPC 선택지(행동)를 지금 단계에서 누를 수 있는지 */
    public static boolean actionAllowed(QuestDef q, int state, String npc, String actionId) {
        QuestDef.Step s = current(q, state);
        return s != null && s.hasAction() && s.npc().equals(npc) && s.actionId().equals(actionId);
    }

    /** 챕터 첫 완료(또는 이미 완료된 챕터 목록)가 지금 단계를 끝내는지 */
    public static boolean chapterCompletes(QuestDef q, int state, Set<Integer> clearedChapters) {
        QuestDef.Step s = current(q, state);
        return s != null && s.type() == QuestDef.Type.CHAPTER_CLEAR && clearedChapters.contains(s.chapter());
    }

    /** 플레이어 위치가 지금 단계의 구역 안인지 (수평 원 + 높이 범위, 같은 차원) */
    public static boolean areaCompletes(QuestDef q, int state, String dimension, double x, double y, double z) {
        QuestDef.Step s = current(q, state);
        if (s == null || s.type() != QuestDef.Type.ENTER_AREA || !s.dimension().equals(dimension)) return false;
        if (y < s.yMin() || y > s.yMax()) return false;
        double dx = x - (s.x() + 0.5), dz = z - (s.z() + 0.5);
        return dx * dx + dz * dz <= (double) s.radius() * s.radius();
    }

    /** 다음 진행 값 (완료 뒤에는 그대로) */
    public static int advance(QuestDef q, int state) {
        return inProgress(q, state) ? state + 1 : state;
    }

    /** 관리자 명령용: 단계 번호를 범위 안으로 (-1 ~ 단계 수) */
    public static int clamp(QuestDef q, int state) {
        return Math.max(NOT_STARTED, Math.min(q.size(), state));
    }
}
