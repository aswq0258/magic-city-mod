package io.github.aswq0258.magiccity.monument;

import net.minecraft.server.level.ServerLevel;

import java.util.List;

/**
 * 지역 웅장화 묶음 하나 (예: 왕성도시 1차). 기존 지역 생성(site·build)과 따로 돌고, 명시적으로 고른 요소만 덧붙인다.
 *
 * 규칙 (MonumentRunner 가 강제):
 *  - 요소마다 후보 위치가 여럿일 수 있음. 검사를 통과하는 첫 후보만 씀. 하나도 안 되면 그 요소는 건너뜀 (부분만 짓지 않음)
 *  - 놓는 칸은 지금 공기(또는 풀·눈처럼 바로 바뀌는 칸)여야 함 = 기존 건축물·플레이어 블록을 덮어쓰지 않음
 *  - 보스 방(전장 복사 범위)·봉인 제단·퀘스트 NPC·이름 붙은 주민 근처, 블록 엔티티 칸은 건드리지 않음
 *  - 바뀌는 칸의 이전 블록을 모두 백업 → rollback 으로 되돌림. 같은 판(version)을 두 번 적용하지 않음
 */
public interface Monument {
    /** 명령어에 쓰는 이름 (영문 소문자) */
    String id();

    /** 설계를 바꾸면 올림. 적용 기록과 다르면 먼저 rollback 해야 함 */
    int version();

    String title();

    /**
     * 이 월드가 설계도와 맞는 월드인지 (기록·표본 블록 확인). 맞지 않으면 이유, 맞으면 null.
     * 생성기 버전이 다른 월드에 잘못된 좌표로 덧짓는 일을 막음.
     */
    String checkWorld(ServerLevel level);

    /** 요소 목록. 바깥 리스트 = 요소, 안쪽 리스트 = 그 요소의 후보 위치(앞 것 우선) */
    List<List<MonumentPlan>> elements(ServerLevel level);
}
