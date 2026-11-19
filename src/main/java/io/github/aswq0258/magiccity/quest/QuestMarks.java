package io.github.aswq0258.magiccity.quest;

import io.github.aswq0258.magiccity.site.AncientRuins;
import io.github.aswq0258.magiccity.site.CursedLands;
import io.github.aswq0258.magiccity.site.DeepMine;
import io.github.aswq0258.magiccity.site.DragonLair;
import io.github.aswq0258.magiccity.site.PortCity;
import io.github.aswq0258.magiccity.site.RootDungeon;
import io.github.aswq0258.magiccity.site.SkyRuins;

/**
 * 퀘스트 단계가 쓰는 자리 이름 → 좌표와 높이 범위 (장별 중간 단계, 2026-11-19).
 * 지역 건설 코드의 상수에서 계산하므로 지역을 옮겨도 따라감. 기준: 프로젝트 문서 claude/장별중간단계_초안.md 3절.
 * <ul>
 *   <li>{@code y} 가 있으면 블록 자리(use_blocks)·지하 NPC 자리로도 쓸 수 있음</li>
 *   <li>{@code yMin·yMax} 는 enter_area 의 높이 범위 (단계가 y_min·y_max 를 따로 주면 그쪽이 우선). 지면 자리는 제한 없음</li>
 * </ul>
 */
public final class QuestMarks {
    private QuestMarks() {}

    public static final int NO_Y = Integer.MIN_VALUE;

    public record Mark(int x, int y, int z, int yMin, int yMax) {
        public boolean hasY() {
            return y != NO_Y;
        }
    }

    /** 지면 자리 (높이 제한 없음) */
    private static Mark surface(int x, int z) {
        return new Mark(x, NO_Y, z, Integer.MIN_VALUE, Integer.MAX_VALUE);
    }

    /** 높이 범위가 있는 자리 */
    private static Mark range(int x, int z, int yMin, int yMax) {
        return new Mark(x, NO_Y, z, yMin, yMax);
    }

    /** 정확한 블록 자리 (높이 범위는 그 높이 −3 ~ +6) */
    private static Mark block(int[] p) {
        return new Mark(p[0], p[1], p[2], p[1] - 3, p[1] + 6);
    }

    /** 모르는 이름이면 null */
    public static Mark resolve(String name) {
        return switch (name) {
            // 2장 저주받은 숲
            case "cursed_hut" -> surface(CursedLands.HUT_X, CursedLands.HUT_Z);
            // 3장 세계수 뿌리 던전: 회랑 층(지면 −26), 탐사대 야영지, 기생충의 둥지(지면 −64)
            case "root_corridor" -> range(RootDungeon.OX, RootDungeon.OZ, RootDungeon.C - 3, RootDungeon.C + 8);
            case "root_camp" -> range(RootDungeon.OX + RootDungeon.CAMP[0], RootDungeon.OZ + RootDungeon.CAMP[1], RootDungeon.C - 3, RootDungeon.C + 8);
            case "root_nest" -> range(RootDungeon.BOSS_X, RootDungeon.BOSS_Z, RootDungeon.F - 3, RootDungeon.F + RootDungeon.ARENA_H);
            // 5장 드워프 심층 광산: NPC 자리, 고대 용광로 홀, 압력 밸브
            case "deep_doren" -> block(DeepMine.DOREN);
            case "deep_bruk" -> block(DeepMine.BRUK);
            case "deep_halda" -> block(DeepMine.HALDA);
            case "deep_quin" -> block(DeepMine.QUIN);
            case "deep_hall" -> range(DeepMine.HX, DeepMine.HZ, DeepMine.A - 3, DeepMine.A + 30);
            case "deep_valve_1" -> block(DeepMine.valve(0));
            case "deep_valve_2" -> block(DeepMine.valve(1));
            case "deep_valve_3" -> block(DeepMine.valve(2));
            // 6장 등대 섬
            case "port_isle" -> surface(PortCity.IX, PortCity.IZ);
            case "port_temple" -> surface(PortCity.IX - 120, PortCity.IZ);
            // 7장 고대 왕국 유적: 발굴 야영지, 구덩이 바닥 대신전, 왕릉
            case "ruins_camp" -> surface(AncientRuins.CAMP_X, AncientRuins.CAMP_Z);
            case "ruins_temple" -> range(AncientRuins.TEMPLE_X, AncientRuins.TEMPLE_Z, AncientRuins.FLOOR - 3, AncientRuins.FLOOR + 12);
            case "ruins_tomb" -> range(AncientRuins.KING_X, AncientRuins.KING_Z, AncientRuins.TOMB_Y - 4, AncientRuins.TOMB_Y + 12);
            // 9장 천공의 유적: 큰 섬, 별 관측소, 폭풍의 결투장
            case "sky_isle" -> isle(0);
            case "sky_observatory" -> isle(4);
            case "sky_arena" -> isle(5);
            // 10장 심연 균열: 나선 계단 중턱(높이 30 아래), 바닥
            case "rift_midway" -> range(CursedLands.STAIR_X, CursedLands.STAIR_Z, CursedLands.BOTTOM, 30);
            case "rift_floor" -> range(CursedLands.RX, CursedLands.RZ, CursedLands.BOTTOM - 5, CursedLands.BOTTOM + 15);
            // 11장 드래곤 레어: 용의 아가리 동굴, 상층·하층 미궁, 보물 동굴
            case "dragon_mouth" -> range(DragonLair.MOUTH_X, DragonLair.MOUTH_Z, DragonLair.CAVE_Y - 6, DragonLair.CAVE_Y + 20);
            case "dragon_upper" -> range(DragonLair.MX, DragonLair.MZ, DragonLair.L1 - 3, DragonLair.L1 + 8);
            case "dragon_lower" -> range(DragonLair.MX, DragonLair.MZ, DragonLair.L2 - 3, DragonLair.L2 + 8);
            case "dragon_hoard" -> range(DragonLair.MX, DragonLair.MZ, DragonLair.HOARD_Y - 5, DragonLair.HOARD_Y + 30);
            default -> null;
        };
    }

    /** 떠 있는 섬 (섬 바닥 높이 −8 ~ +40) */
    private static Mark isle(int i) {
        int[] s = SkyRuins.ISLES[i];
        return range(s[0], s[1], s[3] - 8, s[3] + 40);
    }
}
