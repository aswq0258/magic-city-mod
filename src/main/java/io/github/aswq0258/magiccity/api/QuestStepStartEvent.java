package io.github.aswq0258.magiccity.api;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

/**
 * 개인 퀘스트의 단계가 시작될 때, 그리고 플레이어가 접속할 때(진행 중인 단계마다 한 번) 서버 게임플레이 이벤트 버스에 발행된다.
 *
 * <p>신호({@code signal}) 단계라면 받는 모드는 이미 조건을 만족했는지 확인해 곧바로 {@link MagicCityApi#questSignal} 을 불러도 된다
 * (예: 명령으로 이미 직업을 고른 플레이어). 이벤트 안에서 신호를 보내도 안전하다.
 * 서버 쪽에서만 발행된다. 취소할 수 없다.
 */
public final class QuestStepStartEvent extends Event {
    private final ServerPlayer player;
    private final String quest, type, signal;
    private final int step;
    private final boolean login;

    public QuestStepStartEvent(ServerPlayer player, String quest, int step, String type, String signal, boolean login) {
        this.player = player;
        this.quest = quest;
        this.step = step;
        this.type = type;
        this.signal = signal;
        this.login = login;
    }

    public ServerPlayer getPlayer() { return player; }

    public String getQuest() { return quest; }

    /** 단계 번호 (0부터) */
    public int getStep() { return step; }

    /** talk_npc, signal, enter_area, chapter_clear */
    public String getType() { return type; }

    /** 신호 단계면 기다리는 신호 (예: fantasyrpg:class_chosen), 아니면 빈 문자열 */
    public String getSignal() { return signal; }

    /** 접속 때 다시 알리는 것이면 true (단계가 새로 시작된 것이 아님) */
    public boolean isLogin() { return login; }
}
