package io.github.aswq0258.magiccity.api;

import io.github.aswq0258.magiccity.boss.Progression;
import io.github.aswq0258.magiccity.boss.ProgressionRules;
import io.github.aswq0258.magiccity.boss.Story;
import net.minecraft.server.level.ServerPlayer;

/**
 * 다른 모드가 쓰는 magiccity 공개 API (서버 쪽 전용, 읽기 전용).
 * 이 패키지(io.github.aswq0258.magiccity.api)의 클래스만 바깥에서 쓰도록 하고, 나머지 패키지는 내부 구현으로 본다.
 *
 * <p>API 버전: {@link #API_VERSION}. 메서드를 바꾸거나 지우면 올린다(추가만 하면 그대로).
 */
public final class MagicCityApi {
    private MagicCityApi() {}

    public static final int API_VERSION = 1;

    /** 처음 완료한 메인 챕터 수 (0~12) */
    public static int completed(ServerPlayer p) { return Progression.completed(p); }

    /** 진행 등급 (1~6) */
    public static int grade(ServerPlayer p) { return Progression.grade(p); }

    /** 레벨 상한 (10~60) */
    public static int levelCap(ServerPlayer p) { return Progression.levelCap(p); }

    /** 쓸 수 있는 작업대 크기 (3·4·5) */
    public static int craftGrid(ServerPlayer p) { return Progression.craftGrid(p); }

    /** 그 챕터를 완료했는지 (1~12) */
    public static boolean cleared(ServerPlayer p, int chapter) { return Story.cleared(p, chapter); }

    /** 완료 수 → 등급·레벨 상한·작업대 크기 (순수 계산, 플레이어 없이 쓸 때) */
    public static int gradeFor(int completed) { return ProgressionRules.grade(completed); }

    public static int levelCapFor(int completed) { return ProgressionRules.levelCap(completed); }

    public static int craftGridFor(int completed) { return ProgressionRules.craftGrid(completed); }

    // ───────── 개인 퀘스트 (API 버전 1 그대로: 추가만 함, 2026-11-19) ─────────

    /**
     * 다른 모드가 보내는 퀘스트 신호. 진행 중인 퀘스트 중 지금 단계가 이 신호를 기다리면 다음 단계로 넘긴다.
     * @return 넘긴 퀘스트가 하나라도 있으면 true
     */
    public static boolean questSignal(ServerPlayer p, String signal) { return io.github.aswq0258.magiccity.quest.QuestService.signal(p, signal); }

    /** 퀘스트 진행 값: -1 받지 않음, 0 ~ 단계 수 - 1 진행 중인 단계, 단계 수 이상 완료. 모르는 퀘스트도 -1 */
    public static int questState(ServerPlayer p, String quest) { return io.github.aswq0258.magiccity.quest.QuestService.state(p, quest); }

    /** 퀘스트를 끝냈는지 */
    public static boolean questDone(ServerPlayer p, String quest) { return io.github.aswq0258.magiccity.quest.QuestService.done(p, quest); }

    // ───────── 보스 고정 피해 (API 버전 1 그대로: 추가만 함, 2026-11-19) ─────────

    /** 보스 고정 피해 패턴의 피해 유형 id (magiccity:boss_fixed) */
    public static final net.minecraft.resources.ResourceLocation BOSS_FIXED_DAMAGE = io.github.aswq0258.magiccity.boss.BossPatterns.BOSS_FIXED.location();

    /**
     * 보스 고정 피해 패턴의 피해인지. 이 피해는 예고된 범위 안에서만 맞고 모든 감소를 무시하도록 설계됐으므로,
     * 다른 모드는 자기 피해 감소·무효 처리를 이 피해에 적용하지 않는다(흡수 체력은 바닐라가 먼저 깎음).
     */
    public static boolean isBossFixed(net.minecraft.world.damagesource.DamageSource source) { return io.github.aswq0258.magiccity.boss.BossPatterns.isFixed(source); }
}
