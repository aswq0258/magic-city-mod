package io.github.aswq0258.magiccity.api;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

/**
 * 플레이어가 메인 챕터를 <b>처음</b> 완료했을 때 서버의 게임플레이 이벤트 버스({@code NeoForge.EVENT_BUS})에 한 번 발행된다.
 *
 * <ul>
 *   <li>완료 기록이 저장 데이터에 반영된 뒤에 발행된다 (이벤트 안에서 {@link MagicCityApi#completed} 를 불러도 새 값이 나온다).</li>
 *   <li>이미 완료한 챕터를 다시 깨면 발행되지 않는다.</li>
 *   <li>서버 쪽에서만 발행된다. 취소할 수 없다.</li>
 *   <li>관리자 명령 {@code /magiccity story give <장>} 도 처음 완료처럼 이 이벤트를 발행한다.
 *       이벤트 없이 기록만 바꾸려면 {@code /magiccity story mark <장>} (소급 지급 시험용).</li>
 * </ul>
 *
 * 다른 모드(fantasyrpg)는 이 이벤트로 완료 보너스 등을 주되, 이벤트를 놓친 경우를 위해 접속할 때도
 * {@link MagicCityApi#completed} 로 다시 맞춰야 한다.
 */
public final class ChapterFirstClearEvent extends Event {
    private final ServerPlayer player;
    private final int chapter;
    private final int completedBefore, completed;
    private final int gradeBefore, grade;
    private final int levelCapBefore, levelCap;
    private final int craftGridBefore, craftGrid;

    public ChapterFirstClearEvent(ServerPlayer player, int chapter, int completedBefore, int completed,
                                  int gradeBefore, int grade, int levelCapBefore, int levelCap,
                                  int craftGridBefore, int craftGrid) {
        this.player = player;
        this.chapter = chapter;
        this.completedBefore = completedBefore;
        this.completed = completed;
        this.gradeBefore = gradeBefore;
        this.grade = grade;
        this.levelCapBefore = levelCapBefore;
        this.levelCap = levelCap;
        this.craftGridBefore = craftGridBefore;
        this.craftGrid = craftGrid;
    }

    /** 완료한 플레이어 (UUID 는 {@code getPlayer().getUUID()}) */
    public ServerPlayer getPlayer() { return player; }

    /** 완료한 챕터 번호 (1~12) */
    public int getChapter() { return chapter; }

    /** 이번 완료 전·후의 누적 최초 완료 챕터 수 */
    public int getCompletedBefore() { return completedBefore; }
    public int getCompleted() { return completed; }

    /** 진행 등급 (1~6) 전·후 */
    public int getGradeBefore() { return gradeBefore; }
    public int getGrade() { return grade; }

    /** 레벨 상한 (Lv.10~60) 전·후 */
    public int getLevelCapBefore() { return levelCapBefore; }
    public int getLevelCap() { return levelCap; }

    /** 쓸 수 있는 작업대 크기 (3·4·5) 전·후 */
    public int getCraftGridBefore() { return craftGridBefore; }
    public int getCraftGrid() { return craftGrid; }

    public boolean gradeChanged() { return grade != gradeBefore; }
    public boolean levelCapChanged() { return levelCap != levelCapBefore; }
    public boolean craftGridChanged() { return craftGrid != craftGridBefore; }
}
