# 오버월드 필드 몬스터 30종 — 모델 v1.2 인게임 확인표

대상: fantasybosses 브랜치 `fantasybosses-field-monsters-v1.2` (모델 패키지 v1.2: 정예 10종 256×256·면별 UV·개별 발광, 일반 20종 v1.0)

## 이번에 들어간 것
- 30종 엔티티 `fantasybosses:<id>` (MobCategory.MONSTER). **자연 스폰 없음** — `/summon` 또는 아래 명령으로만 나옴
- GeckoLib 렌더러(경로 `geo/field`, `textures/entity/field`, `animations/field`), 정예 10종 `AutoGlowingGeoLayer`
- 히트박스 = 몸통 기준(장식·무기·망토·깃발·왕관·모자·꼬리·날개 제외). 아래 표
- 기본 AI(근접만): 배회, 플레이어 추적, 근접 공격 시 attack_1/attack_2 번갈아, 피격 시 hurt, 사망 시 death(시체 24틱 유지, 바닐라 옆으로 눕기 끔)
- 수치 [실험]: 일반 체력 20·공격 4·이동 0.25 / 정예 체력 60·공격 8·방어 4·이동 0.27·넉백 저항 0.5
- 아직 없음: 원거리(석궁·궁수·비룡 사냥꾼·저주술사), 비행(비룡·망령), 스킬, 드롭, 자연 스폰, 등급 배율

## 명령 (권한 2)
- `/fantasybosses field gallery [elite|normal|all]` — 내 앞(남쪽 4칸)에 일렬로 세움. AI 꺼짐, 사라지지 않음. **F3+B** 로 히트박스 보기
- `/fantasybosses field anim <대상> <alert|attack_1|attack_2|skill|hurt|death>` — 동작 하나 재생
- `/summon fantasybosses:<id>` — AI 켜진 상태로 전투 확인

## 히트박스 (블록, 폭 × 높이) [초안]
| id | 폭 | 높이 | 비고 |
|---|---|---|---|
| snow_wanderer | 0.70 | 2.10 | |
| blizzard_wraith | 0.80 | 2.10 | 대기 = float |
| **frost_executioner** | 1.20 | 2.80 | 모델 외곽 3.89(왕관·고드름 제외) |
| venom_forest_spider | 1.60 | 1.00 | 달리기 = skitter |
| web_hunter_spider | 1.70 | 1.00 | 달리기 = skitter |
| **spider_nest_guardian** | 2.60 | 1.75 | 몸 3.0×3.4 → 회전 안 하는 상자라 절충, 알집 제외 |
| haunted_miner | 0.70 | 2.05 | |
| wraith_digger | 0.75 | 2.05 | |
| **cursed_mine_overseer** | 1.05 | 2.50 | 드릴·용광로·등불 제외 |
| young_wyvern | 0.90 | 1.05 | 날개·꼬리 제외 |
| cliff_wyvern | 1.40 | 1.45 | 날개·꼬리 제외 |
| **wyvern_hunter** | 0.95 | 2.55 | 석궁·화살통·망토 제외 |
| corrupted_swordsman | 0.75 | 2.10 | |
| corrupted_crossbowman | 0.75 | 2.10 | |
| **corrupted_captain** | 1.20 | 2.55 | 왕관·깃발 제외 |
| plague_infected | 0.70 | 2.05 | |
| plague_rat | 0.70 | 0.50 | |
| **plague_devourer** | 1.15 | 2.70 | 등 돌기 제외 |
| drowned_variant | 0.70 | 2.05 | |
| river_wraith | 0.80 | 2.05 | 대기 = float |
| **drowned_commander** | 1.05 | 2.60 | 산호관·코트 자락 제외 |
| bog_walker | 0.80 | 2.20 | |
| swamp_leech | 0.80 | 0.45 | |
| **swamp_hexer** | 1.00 | 2.60 | 균류 모자 제외(외곽 4.03) |
| border_swordsman | 0.75 | 2.10 | |
| border_archer | 0.75 | 2.10 | |
| **border_captain** | 1.20 | 2.55 | 깃털·군기 제외 |
| rift_beast | 1.30 | 1.30 | |
| lesser_demon | 0.70 | 2.35 | |
| **rift_devourer** | 2.20 | 2.10 | 몸 1.6×3.75(외곽 6.1) → 절충, 꼬리·결정 제외. 부위 판정은 테스트 뒤 결정 |

## 확인 항목 (몬스터마다 표시)
| # | 항목 | 확인 방법 | 기록할 것 |
|---|---|---|---|
| FM1 | 모델 크기 | gallery all, 플레이어 옆에 서서 비교 | 너무 큼/작음 → 배율 제안 |
| FM2 | 텍스처 | 정예 256 / 일반 128, 늘어짐·보라검정(텍스처 없음)·면 뒤집힘 | 깨진 부위(본 이름) |
| FM3 | 발광 강도 | 밤·동굴에서 정예 10종, 셰이더 끈 상태와 켠 상태 | 너무 밝음/안 보임 부위 |
| FM4 | 공격 모션 | `/summon` 후 맞아 보기, `field anim` 으로 attack_1·2·skill | 모션과 피해 시점 어긋남, 본 튐 |
| FM5 | 히트박스 | F3+B, 칼·화살로 머리·장식 쪽 때려 보기 | 모델 밖 빈 곳이 맞음 / 몸이 안 맞음 |
| FM6 | 이동·끼임 | 1칸 통로·문·계단, 거미 둥지 수호자·균열 포식자 | 끼임·벽 통과 |
| FM7 | 사망 | 처치 | death 끝까지 재생, 옆으로 눕지 않음 |
| FM8 | 서버 기동 | 전용 서버에서 오류 없이 켜짐 | 로그 오류 |
