# FantasyBosses v0.7.1 — 정식 엔티티 렌더링 프로토타입

**이 ZIP은 소스 프로젝트이며 mods에 넣는 JAR가 아닙니다.** 작성 환경에서 외부 Maven/Gradle 서버 DNS 조회가 실패하여 실제 Gradle 컴파일 및 게임 실행을 하지 못했습니다. 아래 방식으로 첫 빌드부터 검증해야 합니다.

## 빌드
- Minecraft 1.21.1 / NeoForge 21.1.x / JDK 21 / GeckoLib 4.7.6 (NeoForge용).
- 기본 빌드 버전은 NeoForge 공식 1.21.1 MDK의 21.1.267, ModDevGradle 2.0.148, Gradle 9.2.1입니다. 실제 테스트팩의 NeoForge 버전은 확인하지 못했으므로 필요시 `gradle.properties`의 `neo_version`을 실제 버전으로 변경하세요.
- JDK 21을 설치하고 JAVA_HOME을 설정한 후 ZIP을 풀고 `build_windows.cmd`를 실행하세요. 공식 Gradle 배포판을 다운로드하고 SHA-256을 확인한 뒤 빌드합니다. 첫 빌드에는 인터넷 필요. 성공 결과: `build/libs/fantasybosses-0.7.1.jar`; 실패 시 `build-output.log` 전달.
- 또는 JDK 21 및 Gradle 9.2.1을 설치한 터미널에서 `gradle --no-daemon clean build` 실행. GitHub Actions용 workflow도 포함했으나 원격 저장소를 만들거나 갱신하지 않았습니다.

## 설치 및 테스트 (빌드 성공 후)
1. PrismLauncher에서 Minecraft 1.21.1 NeoForge 별도 테스트 인스턴스를 준비합니다. 기존 월드는 백업하고 v0.5.2 데이터팩과 동시에 전투를 실행하지 마세요. 새 평지 테스트 월드 권장.
2. `mods`에 방금 빌드한 JAR와 NeoForge용 `geckolib-neoforge-1.21.1-4.7.6.jar`를 넣습니다. 다른 GeckoLib JAR가 이미 있으면 중복 설치하지 마세요. 기존 모드팩의 GeckoLib 버전과 충돌 시 별도 인스턴스 사용.
3. 치트 허용, 크리에이티브 모드, 적어도 50×50×40 블록의 빈 공간에서 `/summon fantasybosses:radicion ~ ~ ~` 실행.
4. 모델, 방향, 크기, 텍스처, 발광, 대기 촉수와 240 HP 보스바를 검사. F3+B로 임시 머리 피격박스 확인. `/data get entity @e[type=fantasybosses:radicion,limit=1,sort=nearest] Health`.
5. 시각 동작만 확인하려면 `/fantasybosses preview @e[type=fantasybosses:radicion,limit=1,sort=nearest] bite` 실행. 명령어 끝을 drain / tail_sweep / ground_crawl / wall_ceiling_route / ceiling_dive / death 로 교체 가능. 프리뷰는 1회 재생, 실제 엔티티 위치·공격 판정·피해는 연동하지 않습니다. 이동/낙하 클립은 머리 기준으로 저장한 리소스의 포즈를 재생하는 것이며 실제 천장 충돌이나 지면 착지는 구현하지 않습니다. death 프리뷰는 실제 사망이 아닙니다.
6. 실제 피격 시 보스바가 갱신되는지 확인. 제거: `/kill @e[type=fantasybosses:radicion]`. 이 버전의 실제 사망은 아직 바닐라 처리.

## 범위와 한계
- v0.6 리소스의 geometry/animation/texture/glowmask 네 파일은 바이트 단위로 그대로 유지, 네임스페이스만 fantasybosses로 이동.
- 1개 엔티티, 240 HP, 임시 머리 AABB, 서버측 보스바, 클라이언트 GeckoLib 렌더러 및 발광 레이어를 연결하는 코드 작성. 공격 AI, 분절 경로, 부위별 판정, 전장 기믹, RPG 연동은 미구현. 자연 스폰 없음.
- 전체 리소스 8개 클립은 보존. 실제 엔티티 소환/렌더링/발광/애니메이션/전용 서버 로딩은 미검증. 모델 좌표 변환 및 포즈 정합성은 첫 인게임 테스트에서 확인해야 함.
- 원본 세계수 레지온, 기존 데이터팩, 기존 모드팩, GitHub 저장소는 수정하지 않음.
- `migration/`의 454프레임 원본 변환 행렬, 참고 함수 ZIP과 `blockbench/`는 다음 이식 단계용이며 런타임 JAR에 포함하지 않음.

## 이후 순서
v0.7.2 지상/벽/천장 서버 경로 및 분절 동기화 → v0.7.3 전투 → v0.7.4 부위별 피격 → v0.7.5 전장 → 독립 테스트 후 Claude 통합.

공식 참고: https://docs.neoforged.net/docs/1.21.1/gettingstarted/ , https://github.com/NeoForgeMDKs/MDK-1.21.1-ModDevGradle , https://github.com/bernie-g/geckolib/wiki/Custom-GeckoLib-Entity , https://modrinth.com/mod/geckolib/version/DTo3uxPN
