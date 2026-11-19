package io.github.aswq0258.magiccity.arena;

import io.github.aswq0258.magiccity.arena.ArenaRules.Outcome;
import io.github.aswq0258.magiccity.arena.ArenaRules.Vote;
import io.github.aswq0258.magiccity.arena.ArenaRules.VoteResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 인스턴스 전장 규칙 단위 테스트 (Minecraft 없이 실행).
 * 기준: 프로젝트 문서 claude/인스턴스전장_구현초안.md v3.
 */
class ArenaRulesTest {
    private static final UUID A = new UUID(0, 1), B = new UUID(0, 2), C = new UUID(0, 3), X = new UUID(0, 9);

    @Test
    @DisplayName("투표: 제안한 사람은 자동 동의, 전원 동의(만장일치)면 통과")
    void unanimous() {
        Vote v = new Vote(3, A, List.of(A, B, C), 1000);
        assertEquals(1, v.yesCount());
        assertEquals(VoteResult.PENDING, v.result(1000));
        assertTrue(v.accept(B));
        assertEquals(VoteResult.PENDING, v.result(1100));
        assertTrue(v.accept(C));
        assertEquals(VoteResult.PASSED, v.result(1200));
    }

    @Test
    @DisplayName("투표: 한 명이라도 거절하면 거절, 30초가 지나면 시간 초과, 대상이 아닌 사람은 무시")
    void rejectAndExpire() {
        Vote v = new Vote(3, A, List.of(A, B, C), 0);
        assertFalse(v.accept(X));
        assertFalse(v.decline(X));
        v.accept(B);
        assertTrue(v.decline(C));
        assertEquals(VoteResult.REJECTED, v.result(10));
        assertEquals(C, v.declinedBy());
        Vote w = new Vote(3, A, List.of(A, B), 0);
        assertEquals(VoteResult.PENDING, w.result(ArenaRules.VOTE_TICKS - 1));
        assertEquals(VoteResult.EXPIRED, w.result(ArenaRules.VOTE_TICKS));
        Vote solo = new Vote(5, A, List.of(A), 0);
        assertEquals(VoteResult.PASSED, solo.result(0));                        // 파티가 없으면 바로 통과
    }

    @Test
    @DisplayName("투표 시간 30초, 거절·시간 초과 뒤 10초가 지나야 다시 제안")
    void retry() {
        assertEquals(600, ArenaRules.VOTE_TICKS);
        long at = ArenaRules.retryAt(5000);
        assertEquals(5200, at);
        assertFalse(ArenaRules.mayPropose(5199, at));
        assertTrue(ArenaRules.mayPropose(5200, at));
    }

    @Test
    @DisplayName("전투 판정: 보스 처치 = 승리, 살아 있는 참가자 0 = 전멸, 30분 = 시간 초과")
    void outcome() {
        assertEquals(Outcome.ONGOING, ArenaRules.outcome(false, 2, 100));
        assertEquals(Outcome.VICTORY, ArenaRules.outcome(true, 0, 100));          // 마지막 한 방과 동시에 쓰러져도 승리
        assertEquals(Outcome.WIPE, ArenaRules.outcome(false, 0, 100));
        assertEquals(Outcome.ONGOING, ArenaRules.outcome(false, 1, 20L * 60 * 30 - 1));
        assertEquals(Outcome.TIMEOUT, ArenaRules.outcome(false, 1, 20L * 60 * 30));
    }

    @Test
    @DisplayName("전장 자리: 장 N 은 x = N × 1000, 바닥 64 / 경계는 반지름 + 4, 바닥 아래 6·위 45")
    void geometry() {
        assertArrayEquals(new int[]{3000, 64, 0}, ArenaRules.center(3));
        assertEquals(28, ArenaRules.radius("basic"));
        assertEquals(24, ArenaRules.radius("water"));
        assertFalse(ArenaRules.outOfBounds(31.9, 1, 0, 28));
        assertTrue(ArenaRules.outOfBounds(32.1, 1, 0, 28));
        assertTrue(ArenaRules.outOfBounds(0, -6.5, 0, 28));
        assertTrue(ArenaRules.outOfBounds(0, 45.5, 0, 28));
        assertFalse(ArenaRules.outOfBounds(20, 10, 20, 28));
    }

    @Test
    @DisplayName("카운트다운: 5초부터 1초마다 5·4·3·2·1")
    void countdown() {
        assertEquals(5, ArenaRules.countdownSecond(0));
        assertEquals(4, ArenaRules.countdownSecond(20));
        assertEquals(1, ArenaRules.countdownSecond(80));
        assertEquals(0, ArenaRules.countdownSecond(10));
        assertEquals(0, ArenaRules.countdownSecond(100));
    }
}
