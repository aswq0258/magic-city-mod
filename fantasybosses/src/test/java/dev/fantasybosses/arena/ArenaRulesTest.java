package dev.fantasybosses.arena;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** v0.8 전장 원점 변환 규칙 (2026-11-20, magiccity 보스 방 복사 전장 연결) */
class ArenaRulesTest {
    @Test
    @DisplayName("이동량 0 = 원본 둥지 상자 그대로 (경계 포함)")
    void originalBox() {
        assertTrue(ArenaRules.inBox(-2860, 9, -860, 0, 0, 0));
        assertTrue(ArenaRules.inBox(-2895, 7, -895, 0, 0, 0));
        assertTrue(ArenaRules.inBox(-2825, 40, -825, 0, 0, 0));
        assertFalse(ArenaRules.inBox(-2896, 9, -860, 0, 0, 0));
        assertFalse(ArenaRules.inBox(-2860, 41, -860, 0, 0, 0));
    }

    @Test
    @DisplayName("옮긴 전장: 원본 좌표 + 이동량이 안, 원본 자리는 밖")
    void shiftedBox() {
        int dx = 5860, dy = 55, dz = 860; // 예: 둥지 (-2860, 9, -860) → 전장 (3000, 64, 0)
        assertTrue(ArenaRules.inBox(-2860 + dx, 9 + dy, -860 + dz, dx, dy, dz));
        assertTrue(ArenaRules.inBox(3000, 64, 0, dx, dy, dz));
        assertFalse(ArenaRules.inBox(-2860, 9, -860, dx, dy, dz));
        assertFalse(ArenaRules.inBox(3000, 64 + 40, 0, dx, dy, dz));
    }

    @Test
    @DisplayName("차원: 이동량 0 이면 왕국 대륙만, 이동량이 있으면 그 몹의 차원")
    void dimension() {
        assertTrue(ArenaRules.dimensionOk("magiccity:kingdom", 0, 0, 0));
        assertFalse(ArenaRules.dimensionOk("magiccity:boss_arena", 0, 0, 0));
        assertFalse(ArenaRules.dimensionOk("minecraft:overworld", 0, 0, 0));
        assertTrue(ArenaRules.dimensionOk("magiccity:boss_arena", 5860, 55, 860));
    }

    @Test
    @DisplayName("NBT 이동량: 길이 3 이 아니거나 너무 크면 0")
    void offsetNbt() {
        assertArrayEquals(new int[]{1, 2, 3}, ArenaRules.offset(new int[]{1, 2, 3}));
        assertArrayEquals(new int[3], ArenaRules.offset(null));
        assertArrayEquals(new int[3], ArenaRules.offset(new int[]{1, 2}));
        assertArrayEquals(new int[3], ArenaRules.offset(new int[]{40_000_000, 0, 0}));
    }

    @Test
    @DisplayName("기존 규칙 그대로: 체력 하한·균열 판정·알집 제한")
    void existing() {
        assertTrue(ArenaRules.clamp(100, 240, 15) >= ArenaRules.floor(240));
        assertTrue(ArenaRules.inWave(3, 0, 4, 0, 0, 0));
        assertFalse(ArenaRules.inWave(4, 0, 4, 0, 0, 0));
        assertTrue(ArenaRules.canLay(2, 3));
        assertFalse(ArenaRules.canLay(3, 0));
    }
}
