package io.github.aswq0258.magiccity.world;

import io.github.aswq0258.magiccity.site.BorderCity;
import io.github.aswq0258.magiccity.site.DwarfHold;
import io.github.aswq0258.magiccity.site.Hamlets;
import io.github.aswq0258.magiccity.site.PortCity;
import io.github.aswq0258.magiccity.site.RoadSites;
import io.github.aswq0258.magiccity.site.WorldTree;

/**
 * 마을(사람이 사는 안전한 곳): 왕성도시·신시가지, 드워프 요새, 항구도시, 국경도시, 작은 마을들, 세계수 성역, 길가 쉼터.
 * 폐왕도는 마을이 아님 (저주받은 땅). 2026-11-21: 다른 모드(fantasyrpg 마스터리 교체)가 쓰도록 길 습격의 안전 판정에서 분리.
 */
public final class Towns {
    private Towns() {}

    /** 왕국 차원 좌표가 마을 안인지 */
    public static boolean at(double x, double z) {
        if (Math.max(Math.abs(x - KingdomTerrain.CX), Math.abs(z - KingdomTerrain.CZ)) < 680) return true;   // 왕성도시 + 신시가지
        if (DwarfHold.near(x, z)) return true;
        if (Hamlets.near(x, z)) return true;
        if (PortCity.near(x, z)) return true;
        if (BorderCity.near(x, z)) return true;
        if (Math.hypot(x - WorldTree.OX, z - WorldTree.OZ) < WorldTree.R + 20) return true;
        for (RoadSites.Site s : RoadSites.sites()) {
            if (s.kind() == RoadSites.Kind.REST && Math.hypot(x - s.x(), z - s.z()) < 64) return true;
        }
        return false;
    }
}
