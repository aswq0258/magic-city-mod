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
}
