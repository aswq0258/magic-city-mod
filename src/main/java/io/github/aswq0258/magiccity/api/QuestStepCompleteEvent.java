package io.github.aswq0258.magiccity.api;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

/**
 * 개인 퀘스트의 단계 하나를 플레이어가 직접 끝냈을 때 서버 게임플레이 이벤트 버스에 발행된다 (다음 단계로 넘어가기 직전, 2026-11-19).
 * 장을 깨서 남은 단계를 건너뛸 때(안내형 중간 단계)와 관리자 명령으로 단계를 바꿀 때는 발행하지 않는다.
 *
 * <p>{@link #isMidStep()} 은 장 퀘스트의 중간 단계(보스 자리 도착·보스 처치가 아닌 단계)인지 — 받는 모드가 중간 단계 보상을 줄 때 쓴다.
 * 서버 쪽에서만 발행된다. 취소할 수 없다.
 */
public final class QuestStepCompleteEvent extends Event {
    private final ServerPlayer player;
    private final String quest, type;
    private final int step, chapter;
    private final boolean midStep;

    public QuestStepCompleteEvent(ServerPlayer player, String quest, int step, String type, int chapter, boolean midStep) {
        this.player = player;
        this.quest = quest;
        this.step = step;
        this.type = type;
        this.chapter = chapter;
        this.midStep = midStep;
    }

    public ServerPlayer getPlayer() { return player; }

    public String getQuest() { return quest; }

    /** 끝낸 단계 번호 (0부터) */
    public int getStep() { return step; }

    /** talk_npc, signal, enter_area, chapter_clear, talk_npcs, use_blocks */
    public String getType() { return type; }

    /** 퀘스트의 장 (장 퀘스트가 아니면 0) */
    public int getChapter() { return chapter; }

    /** 장 퀘스트의 중간 단계인지 */
    public boolean isMidStep() { return midStep; }
}
