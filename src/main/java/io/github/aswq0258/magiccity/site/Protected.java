package io.github.aswq0258.magiccity.site;

import io.github.aswq0258.magiccity.world.KingdomTerrain;

/** 지어 둔 건축물 자리: 길 고치기·나무 심기가 건드리지 않는 곳 */
public final class Protected {
    private Protected() {}

    public static boolean at(double x, double z) {
        if (Math.hypot(x - KingdomTerrain.OLD_X, z - KingdomTerrain.OLD_Z) < 260) return true;
        if (Math.hypot(x - WorldTree.OX, z - WorldTree.OZ) < WorldTree.R + 20) return true;
        if (DwarfHold.near(x, z)) return true;
        if (PortCity.near(x, z)) return true;
        if (DragonLair.near(x, z)) return true;
        if (SkyRuins.near(x, z)) return true;
        if (CursedLands.protectedAt(x, z)) return true;
        if (Hamlets.near(x, z)) return true;
        for (RoadSites.Site s : RoadSites.sites()) {
            int e = s.kind() == RoadSites.Kind.ARENA ? 24 : 18;
            if (Math.abs(x - s.x()) <= e && Math.abs(z - s.z()) <= e) return true;
        }
        return false;
    }
}
