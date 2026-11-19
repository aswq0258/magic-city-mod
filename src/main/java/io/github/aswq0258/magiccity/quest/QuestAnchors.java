package io.github.aswq0258.magiccity.quest;

import io.github.aswq0258.magiccity.site.AncientRuins;
import io.github.aswq0258.magiccity.site.BorderCity;
import io.github.aswq0258.magiccity.site.CursedLands;
import io.github.aswq0258.magiccity.site.DwarfHold;
import io.github.aswq0258.magiccity.site.ElfGate;
import io.github.aswq0258.magiccity.site.PortCity;
import io.github.aswq0258.magiccity.site.SkyRuins;
import io.github.aswq0258.magiccity.site.WorldTree;

/**
 * 퀘스트 NPC 를 세울 지역 거점 이름 → 왕국 대륙 좌표 (x, z). 지역 건설 코드의 상수에서 계산하므로 지역을 옮겨도 따라감.
 * 자리는 {@code /magiccity goto <지역>} 도착 지점 옆 3칸 (사람이 오가는 안전한 곳).
 */
public final class QuestAnchors {
    private QuestAnchors() {}

    /**
     * 모르는 이름이면 null. 지면 자리는 {x, z}, 정해진 높이에 세우는 자리(지하 NPC, {@link QuestMarks} 의 블록 자리)는 {x, z, y}.
     */
    public static int[] resolve(String name) {
        int[] surface = surface(name);
        if (surface != null) return surface;
        QuestMarks.Mark m = QuestMarks.resolve(name);
        return m != null && m.hasY() ? new int[]{m.x(), m.z(), m.y()} : null;
    }

    private static int[] surface(String name) {
        return switch (name) {
            case "cursed_crossroads" -> new int[]{CursedLands.WX - 3, CursedLands.WZ - 6};
            case "worldtree_plaza" -> new int[]{WorldTree.OX + 3, WorldTree.OZ + 46};
            case "elf_gate" -> new int[]{ElfGate.GX + 3, ElfGate.GZ};
            case "dwarf_gate" -> new int[]{DwarfHold.CX + 3, DwarfHold.CZ - 40};
            case "port_plaza" -> new int[]{PortCity.PX + 3, PortCity.PZ + 22};
            case "ruins_dig" -> new int[]{AncientRuins.CX - 59, AncientRuins.CZ - 62};
            case "border_square" -> new int[]{BorderCity.VX + 3, BorderCity.VZ + 8};
            case "sky_camp" -> new int[]{SkyRuins.TOWER_X + 3, SkyRuins.TOWER_Z + 12};
            case "rift_stairs" -> new int[]{CursedLands.STAIR_X + 10, CursedLands.STAIR_Z};
            default -> null;
        };
    }
}
