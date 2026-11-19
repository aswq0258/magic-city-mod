package io.github.aswq0258.magiccity.arena;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 챕터 보스 인스턴스 전장 규칙 (순수 계산, Minecraft 없이 테스트).
 * 기준: 프로젝트 문서 claude/인스턴스전장_구현초안.md v3 (2026-11-19 사용자 결정).
 */
public final class ArenaRules {
    private ArenaRules() {}

    /** 투표 유지 시간 30초, 거절·시간 초과 뒤 다시 제안까지 10초 */
    public static final int VOTE_TICKS = 600, RETRY_TICKS = 200;
    /** 입장 카운트다운 5초, 이동 뒤 보스 등장까지 3초, 제한 시간 30분, 승리 뒤 귀환까지 30초 */
    public static final int COUNTDOWN_TICKS = 100, SPAWN_DELAY_TICKS = 60, TIME_LIMIT_TICKS = 20 * 60 * 30, VICTORY_TICKS = 600;
    /** 전장: 장 N 의 중심 x = N × SPACING, 바닥 높이, 기본 반지름, 경계 여유 */
    public static final int SPACING = 1000, FLOOR_Y = 64, RADIUS = 28, WATER_RADIUS = 24, MARGIN = 4;

    /** 전장 중심 블록 {x, 바닥 y, z} */
    public static int[] center(int chapter) {
        return new int[]{chapter * SPACING, FLOOR_Y, 0};
    }

    public static int radius(String shape) {
        return "water".equals(shape) ? WATER_RADIUS : RADIUS;
    }

    /** 전장 밖인지 (바닥 중심에서의 차이): 수평 반지름 + 여유 밖, 바닥 6칸 아래, 바닥 45칸 위 */
    public static boolean outOfBounds(double dx, double dy, double dz, int radius) {
        double r = radius + MARGIN;
        return dx * dx + dz * dz > r * r || dy < -6 || dy > 45;
    }

    // ───────── 투표 ─────────

    public enum VoteResult { PENDING, PASSED, REJECTED, EXPIRED }

    /** 파티 투표: 제안한 사람은 자동 동의, 전원 동의면 통과, 한 명이라도 거절이면 거절, 30초가 지나면 시간 초과 */
    public static final class Vote {
        public final int chapter;
        public final UUID proposer;
        public final List<UUID> members;
        public final long expiresAt;
        private final Set<UUID> yes = new LinkedHashSet<>();
        private UUID declinedBy;

        public Vote(int chapter, UUID proposer, List<UUID> members, long now) {
            this.chapter = chapter;
            this.proposer = proposer;
            this.members = List.copyOf(members);
            this.expiresAt = now + VOTE_TICKS;
            yes.add(proposer);
        }

        /** 동의. 투표 대상이 아니면 false */
        public boolean accept(UUID who) {
            if (!members.contains(who)) return false;
            yes.add(who);
            return true;
        }

        /** 거절 (접속 끊김도 거절로 셈). 투표 대상이 아니면 false */
        public boolean decline(UUID who) {
            if (!members.contains(who)) return false;
            if (declinedBy == null) declinedBy = who;
            return true;
        }

        public UUID declinedBy() {
            return declinedBy;
        }

        public int yesCount() {
            return yes.size();
        }

        public boolean voted(UUID who) {
            return yes.contains(who) || who.equals(declinedBy);
        }

        public VoteResult result(long now) {
            if (declinedBy != null) return VoteResult.REJECTED;
            if (yes.containsAll(members)) return VoteResult.PASSED;
            if (now >= expiresAt) return VoteResult.EXPIRED;
            return VoteResult.PENDING;
        }
    }

    /** 다시 제안할 수 있는지 (거절·시간 초과 뒤 10초) */
    public static boolean mayPropose(long now, long cooldownUntil) {
        return now >= cooldownUntil;
    }

    /** 거절·시간 초과 뒤 다음 제안 가능 시각 */
    public static long retryAt(long now) {
        return now + RETRY_TICKS;
    }

    // ───────── 전투 결과 ─────────

    public enum Outcome { ONGOING, VICTORY, WIPE, TIMEOUT }

    /**
     * 전투 판정. 보스가 죽었으면 승리, 살아 있는 참가자(관전 아님)가 없으면 전멸, 30분이 지나면 시간 초과.
     * @param elapsed 보스가 나온 뒤 지난 틱
     */
    public static Outcome outcome(boolean bossDead, int aliveParticipants, long elapsed) {
        if (bossDead) return Outcome.VICTORY;
        if (aliveParticipants <= 0) return Outcome.WIPE;
        if (elapsed >= TIME_LIMIT_TICKS) return Outcome.TIMEOUT;
        return Outcome.ONGOING;
    }

    /** 카운트다운 중 채팅에 보일 남은 초 (20틱마다, 0 이면 안 보임) */
    public static int countdownSecond(long elapsed) {
        if (elapsed < 0 || elapsed >= COUNTDOWN_TICKS || elapsed % 20 != 0) return 0;
        return (int) ((COUNTDOWN_TICKS - elapsed) / 20);
    }
}
