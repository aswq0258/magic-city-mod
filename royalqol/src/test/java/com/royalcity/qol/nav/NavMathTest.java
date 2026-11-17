package com.royalcity.qol.nav;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NavMathTest {

    @Test
    @DisplayName("바라보는 8방위: yaw 0 남, 90 서, 180 북, -90 동, 225 북동")
    void facing() {
        assertEquals("남", NavMath.facing(0));
        assertEquals("서", NavMath.facing(90));
        assertEquals("북", NavMath.facing(180));
        assertEquals("북", NavMath.facing(-180));
        assertEquals("동", NavMath.facing(-90));
        assertEquals("동", NavMath.facing(270));
        assertEquals("북동", NavMath.facing(225));
        assertEquals("남서", NavMath.facing(45));
        assertEquals("남", NavMath.facing(720));
    }

    @Test
    @DisplayName("목표 방위: 북쪽(-z)·동쪽(+x)·남서")
    void compassTo() {
        assertEquals("북", NavMath.compassTo(0, 0, 0, -100));
        assertEquals("동", NavMath.compassTo(0, 0, 100, 0));
        assertEquals("남서", NavMath.compassTo(0, 0, -100, 100));
        assertEquals("북동", NavMath.compassTo(260, 260, 360, 160));
    }

    @Test
    @DisplayName("화살표: 북쪽을 볼 때 북쪽 목표는 ↑, 동쪽 목표는 →, 뒤는 ↓, 서쪽은 ←")
    void arrow() {
        double north = 180;
        assertEquals("↑", NavMath.arrowTo(north, 0, 0, 0, -50));
        assertEquals("→", NavMath.arrowTo(north, 0, 0, 50, 0));
        assertEquals("↓", NavMath.arrowTo(north, 0, 0, 0, 50));
        assertEquals("←", NavMath.arrowTo(north, 0, 0, -50, 0));
        assertEquals("↗", NavMath.arrowTo(north, 0, 0, 50, -50));
        // 남쪽(yaw 0)을 볼 때 동쪽은 왼쪽
        assertEquals("←", NavMath.arrowTo(0, 0, 0, 50, 0));
    }

    @Test
    @DisplayName("날짜·시각: 0 = 1일차 06:00, 18000 = 00:00, 24000 = 2일차")
    void clock() {
        assertEquals(1, NavMath.day(0));
        assertEquals("06:00", NavMath.clock(0));
        assertEquals("12:00", NavMath.clock(6000));
        assertEquals("00:00", NavMath.clock(18000));
        assertEquals("18:30", NavMath.clock(12500));
        assertEquals(2, NavMath.day(24000));
        assertEquals("06:00", NavMath.clock(24000));
    }

    @Test
    @DisplayName("도착 거리는 6~32 사이, 거리 표시")
    void arrivalAndDistance() {
        assertEquals(6, NavMath.arrivalRange(new Waypoint("a", "d", 0, 0, 0, 2, false, "")));
        assertEquals(16, NavMath.arrivalRange(new Waypoint("a", "d", 0, 0, 0, 16, false, "")));
        assertEquals(32, NavMath.arrivalRange(new Waypoint("a", "d", 0, 0, 0, 360, false, "")));
        assertEquals("999m", NavMath.distance(999.4));
        assertEquals("3.1km", NavMath.distance(3120));
    }
}
