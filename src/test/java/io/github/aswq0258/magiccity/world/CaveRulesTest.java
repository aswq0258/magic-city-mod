package io.github.aswq0258.magiccity.world;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 지하 동굴 규칙 (2026-11-22) */
class CaveRulesTest {
    @Test
    void onlyBelowZeroAndAboveBedrock() {
        for (int x = -200; x < 200; x += 3)
            for (int z = -200; z < 200; z += 3)
                for (int y = 0; y < 120; y += 2) assertFalse(CaveRules.open(x, y, z), "Y " + y + " 에 동굴");
        for (int x = -200; x < 200; x += 3)
            for (int z = -200; z < 200; z += 3)
                for (int y = -64; y < CaveRules.BOTTOM; y++) assertFalse(CaveRules.open(x, y, z), "기반암 근처 Y " + y);
    }

    @Test
    void deterministicAndReasonableDensity() {
        long open = 0, total = 0;
        for (int x = 0; x < 96; x++)
            for (int z = 0; z < 96; z++)
                for (int y = CaveRules.BOTTOM; y <= CaveRules.TOP; y++) {
                    boolean o = CaveRules.open(x + 3000, y, z - 1700);
                    assertEquals(o, CaveRules.open(x + 3000, y, z - 1700));
                    if (o) open++;
                    total++;
                }
        double f = (double) open / total;
        assertTrue(f > 0.03 && f < 0.2, "동굴 비율 " + f);
    }

    @Test
    void zoneMarginIsNeverCarved() {
        List<int[]> zones = List.of(new int[]{0, 0, 40, 40, -40, 70});   // 심연 균열처럼 Y −40 까지 내려온 던전
        int m = CaveRules.MARGIN;
        for (int x = -m; x <= 40 + m; x++)
            for (int z = -m; z <= 40 + m; z++)
                for (int y = CaveRules.BOTTOM; y <= CaveRules.TOP; y++)
                    if (y >= -40 - m) assertFalse(CaveRules.carve(x, y, z, zones), x + "," + y + "," + z);
        // 영역 바로 아래(여백 밖)는 다시 동굴이 될 수 있음
        boolean any = false;
        for (int x = 0; x <= 40 && !any; x++)
            for (int z = 0; z <= 40 && !any; z++)
                for (int y = CaveRules.BOTTOM; y < -40 - m && !any; y++) any = CaveRules.carve(x, y, z, zones);
        assertTrue(any, "여백 밖에도 동굴이 하나도 없음");
    }

    @Test
    void lavaOnlyAtBottom() {
        assertTrue(CaveRules.lava(CaveRules.LAVA_Y));
        assertFalse(CaveRules.lava(CaveRules.LAVA_Y + 1));
        assertTrue(CaveRules.LAVA_Y > CaveRules.BOTTOM && CaveRules.TOP < 0);
    }
}
