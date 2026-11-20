# FantasyBosses v0.7.5.1 — BGM 추가 및 Claude 인계
Minecraft 1.21.1 / NeoForge 21.1.267 / JDK 21 / GeckoLib 4.7.6

- v0.7.5 사용자 인게임 테스트: 25/25 정상. 이번 변경은 음악 관련 등록, 클라이언트 재생 코드, OGG 및 배포 버전만 추가/수정했다.
- 96초 오리지널 루프 `Radicion – Under the Roots` (뿌리 아래의 맥동). 실제 전장에 활성 라디키온이 있을 때 재생된다. Music 음량으로 조절. 사망 후 약 1초 페이드아웃, 퇴장/월드 전환 시 즉시 종료하도록 작성했다. 참고: 실제 게임 검증 필요.
- `build_windows.cmd` 실행 후 `build/libs/fantasybosses-0.7.5.1.jar` 생성. 이전 v0.7.5 JAR 제거, GeckoLib 유지. 월드 백업 후 교체. 기존 데이터팩은 동시 사용하지 않는다.
- `docs/HANDOFF_KO.md`: 전체 개발 명세 및 v0.8~v1.0 연동 지침. `docs/CLAUDE_PROMPT_KO.md`: Claude에게 붙여 넣을 요청문.
- `docs/BASELINE_v0.7.5.md`: 기존 테스트 명령어와 기능 설명. `docs/BASELINE_VALIDATION_v0.7.5.txt`: 기존 정적 검사 기록. `VALIDATION.txt`: 이번 작업 검사 결과.

## 음악만 확인하는 방법
```
/execute in magiccity:kingdom run tp @s -2860.5 9 -835.5
/kill @e[type=fantasybosses:radicion]
/summon fantasybosses:radicion -2861.179 9.518 -884.502
```
음악 볼륨을 0보다 크게 설정하고 보스가 지상 이동을 시작하면 BGM이 자동 재생되는지 확인한다. 음악 재생/반복과 죽음/퇴장 시 정리, 게임 재접속, 음소거 및 복구, 일반 배경음 복귀, 효과음 보존을 확인한다. 음악 없이 모드가 작동하도록 원본 전투 코드는 그대로 보존했다.

수동 음원 확인은 전투 밖에서 `/playsound fantasybosses:music.radicion.undercurrent music @s ~ ~ ~ 0.7 1 1` 후 `/stopsound @s music fantasybosses:music.radicion.undercurrent` (OP 필요). 수동 재생은 자동 반복/페이드 검증이 아니며 자동 BGM과 동시에 사용하지 않는다.

현재 실행 환경에서는 Maven DNS 연결이 불가능해 전체 JAR 빌드는 하지 못했다. 빌드/실게임 오디오 검증 후 Claude 통합 브랜치에 사용하거나 v0.7.5의 정상 기준을 우선 사용한다.
