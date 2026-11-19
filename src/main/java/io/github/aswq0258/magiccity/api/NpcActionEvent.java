package io.github.aswq0258.magiccity.api;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

/**
 * 플레이어가 퀘스트 NPC 대화에서 행동 선택지(예: {@code choose_class})를 눌렀을 때 서버 게임플레이 이벤트 버스에 발행된다.
 *
 * <ul>
 *   <li>magiccity 가 먼저 검증한 뒤에만 발행한다: NPC 표식, 같은 차원 6칸 안, 그 퀘스트의 지금 단계가 이 NPC·행동을 허용.</li>
 *   <li>행동의 결과(화면 열기 등)는 받는 모드가 처리하고, 단계를 끝낼 때는 {@link MagicCityApi#questSignal} 을 부른다.</li>
 *   <li>서버 쪽에서만 발행된다. 취소할 수 없다.</li>
 * </ul>
 */
public final class NpcActionEvent extends Event {
    private final ServerPlayer player;
    private final String npc, quest, action;

    public NpcActionEvent(ServerPlayer player, String npc, String quest, String action) {
        this.player = player;
        this.npc = npc;
        this.quest = quest;
        this.action = action;
    }

    public ServerPlayer getPlayer() { return player; }

    /** NPC id (예: steward) */
    public String getNpc() { return npc; }

    /** 퀘스트 id (예: kings_call). NPC 상시 선택지(quest_npcs 의 services)면 빈 문자열 */
    public String getQuest() { return quest; }

    /** 행동 id (예: choose_class) */
    public String getAction() { return action; }
}
