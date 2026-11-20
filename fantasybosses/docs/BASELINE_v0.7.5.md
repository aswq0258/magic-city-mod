# FantasyBosses v0.7.5 — 라디키온 전장 기믹
Minecraft 1.21.1 / NeoForge 21.1.267 / Java 21 / GeckoLib 4.7.6

## 빌드/교체
1. 이전 v0.7.4 보스와 유충을 정리한 뒤 게임 종료, 월드 백업.
2. ZIP 압축 해제, `build_windows.cmd` 실행. 기존 `.build-tools` 폴더 복사 가능.
3. `build/libs/fantasybosses-0.7.5.jar`를 mods에 설치. 이전 FantasyBosses JAR 제거, GeckoLib 유지. 데이터팩은 사용하지 않음.
4. 세계수 전장에서 새로 소환. 자동 순찰 시작. 같은 전장에서는 한 마리만 전투 가능한 설계.

```
/execute in magiccity:kingdom run tp @s -2860.5 9 -835.5
/kill @e[type=fantasybosses:radicion]
/summon fantasybosses:radicion -2861.179 9.518 -884.502
/gamemode survival
```
평화로움에서는 유충 부화 불가. 명령어는 OP 권한 필요.

## 1페이즈: 흡수 기관
4개 기관(24 HP씩), 기존 데이터팩 좌표 사용. 기관이 남으면 받는 피해 ×0.6, 5초마다 기관당 2 HP 회복. 159/240 HP (66.25%) 도달 시 보호막이 켜지고 회복은 정지. 기관 전부 파괴 전까지 피해에 의한 페이즈 진입 차단(일반 피해 및 그로기/약점 배율 포함). 마지막 기관 파괴 시 보호막 해제 및 필요할 경우 2페이즈 진입. 관리자 /kill은 별도 우회. 기관 수 별도 체력바. 녹색 입자 연결선 표시, 파괴된 기관은 다음 갱신부터 공급 중단.

## 2페이즈: 산란
기존 산란 경고/공중 이동/그로기 타이밍 유지. 유충 대신 8 HP 알집 생성, 60틱 후 유충 한 마리 부화. 부화 전 파괴 가능. 동시에 알집 3개, 알집+유충 6개 제한(로드된 개체 기준). 안전한 바닥 검색. 실제 생성 수 안내. 유충은 여전히 엔더마이트 임시 모델. 부화 실패 시 안내.

## 3페이즈: 마력 균열
기존 데이터팩의 4개 위치, 10초 누적 대기 후 사용 가능한 지상 순찰 시 대각선 2개씩 교대로 경고. 50틱(2.5초) 예고, 5블록 반경/수직 ±3블록, 8 HP 마법 피해, 각 플레이어 1회. 경고 중 보스 정지 및 다른 공격 시작 방지. 이후 3초 그로기. 바닥은 파티클로 표현하여 원본 블록 수정 없음. 기존 공격 6종 설정 유지.

## 사망/초기화
보스 사망 시 공격/기관/알집/유충/균열 즉시 정리, 이후 48틱 동안 머리 방향으로 11마디 순차 소멸. 독립 UI 정리. 각 개체는 소유 보스 UUID로 식별. 로드되지 않은 개체는 로드된 후 소유자 검사로 정리하며 청크 강제 로드는 하지 않음. 기관 마스크/부화 타이머/균열 타이머는 NBT 저장. 기존 버전의 진행 중 전투 상태는 새 보스를 소환해 테스트하는 것을 권장. 기존 클래스의 공격 진행 중 저장 복원 제한은 유지.

## 명령어
모든 개별 테스트는 `auto_off`로 자동 공격을 중지하고, 보스가 지상 순찰 중이고 경직/다른 공격이 없을 때 진행.
```
/fantasybosses combat @e[type=fantasybosses:radicion,limit=1,sort=nearest] auto_off
/fantasybosses combat @e[type=fantasybosses:radicion,limit=1,sort=nearest] status
/fantasybosses combat @e[type=fantasybosses:radicion,limit=1,sort=nearest] roots
/fantasybosses combat @e[type=fantasybosses:radicion,limit=1,sort=nearest] phase2
/fantasybosses combat @e[type=fantasybosses:radicion,limit=1,sort=nearest] brood
/fantasybosses combat @e[type=fantasybosses:radicion,limit=1,sort=nearest] phase3
/fantasybosses combat @e[type=fantasybosses:radicion,limit=1,sort=nearest] fissure
/fantasybosses combat @e[type=fantasybosses:radicion,limit=1,sort=nearest] auto_on
```
`roots`는 모든 기관 강제 제거. `phase2`, `phase3`는 기관을 제거한 후 강제 진행하는 관리자 전용 우회이므로 보호막 자체는 명령어 없이 먼저 검증. `brood`는 특수 경로 연결점까지 대기 후 실행. `fissure`는 3페이즈 지상 순찰에만 허용. status에 roots/shield/eggs/larvae/lastSpawned/fissureTicks 표시. `lastSpawned`는 이번 버전에서 마지막 산란 알집 생성 수.

## 검증 수준 및 인계
기존 이동/공격 포즈, 텍스처, 피격 배율/공격 타이밍 소스 보존. 독립 Java 테스트 및 Java 문법 파싱, 원본 레지온 좌표 검사, ZIP 검사 수행. 전체 NeoForge/GeckoLib 컴파일 및 인게임 검증은 미수행(Maven DNS 접속 불가). 실제 충돌/피격, 보스바 렌더링, 알집 생성, 파티클 성능, 저장 복원 검증 필요. 특히 북쪽 기관은 경로와 인접해 모델 일부가 겹칠 수 있음. 변형한 월드에서 기관 공간이 막혔을 때 그 기관을 제외할 수 있음. 여러 보스/파티별 전장, MagicCity/FantasyRPG 연동 및 스케일링, 보상/챕터 완료 처리는 v0.8 이후. 정식 인계는 이 버전 인게임 테스트 통과 후 권장. 이 ZIP은 JAR가 아니라 소스 프로젝트.
