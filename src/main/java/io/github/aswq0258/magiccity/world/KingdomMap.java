package io.github.aswq0258.magiccity.world;

import io.github.aswq0258.magiccity.site.RoadSites;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/**
 * 가 보지 않은 땅까지 그려진 왕국 대륙 지도. 지형은 계산식(KingdomTerrain) 이라 청크를 생성하지 않고 바로 그림.
 *  - 지역 지도: 중심 주변 2048 x 2048 블록 (1픽셀 = 16블록), 내 위치 화살표 + 거점 표시
 *  - 대륙 전도: 대륙 전체 (1픽셀 = 76블록), 주요 지역은 색 점으로 표시
 */
public final class KingdomMap {
    private KingdomMap() {}

    /** 마스터플랜 지역 (도면 좌표) */
    public static final Object[][] PLAN_SITES = {
            {"C0", "중앙 왕성도시", 260, 260}, {"F1", "서부 숲 전초마을", -430, 90}, {"E1", "세계수 성역", -950, -570},
            {"D1", "드워프 광산왕국", -1690, -1070}, {"M1", "엘프 산맥 관문", -350, -1040}, {"G1", "고룡 봉우리", 500, -1210},
            {"N1", "북부 관문요새", 220, -400}, {"I1", "혹한의 북부 마을", 150, -1590}, {"I2", "빙해 항구", 100, -2150},
            {"A1", "부유섬 유적", 1200, -820}, {"T1", "동부 교역소", 930, 250}, {"P1", "동해안 항구도시", 1490, 900},
            {"P2", "등대와 해상신전", 2070, 950}, {"V1", "남부 농경마을", 170, 980}, {"V2", "남부 국경도시", -200, 1670},
            {"R1", "고대 왕국 유적", 660, 1700}, {"W1", "저주받은 숲", -1160, 1110}, {"W2", "심연 균열", -1540, 1390},
            {"B1", "몰락한 옛 왕도", -1850, 1760},
    };

    private static MapColor color(KingdomTerrain.Column c) {
        return switch (c.kind()) {
            case OCEAN, DEEP_OCEAN, RIVER -> MapColor.WATER;
            case FROZEN_OCEAN -> MapColor.ICE;
            case BEACH -> MapColor.SAND;
            case FARMLAND -> MapColor.TERRACOTTA_YELLOW;
            case FOREST, TAIGA, GROVE -> MapColor.PLANT;
            case ELF_FOREST -> MapColor.COLOR_GREEN;
            case CURSED -> MapColor.TERRACOTTA_PURPLE;
            case SNOW -> MapColor.SNOW;
            case PEAKS, DWARF -> c.h() > 150 ? MapColor.SNOW : c.h() > 112 || c.steep() ? MapColor.STONE : MapColor.GRASS;
            case WASTELAND -> MapColor.TERRACOTTA_ORANGE;
            case SWAMP -> MapColor.TERRACOTTA_GREEN;
            case ASH -> MapColor.COLOR_GRAY;
            case CORRUPT -> MapColor.CRIMSON_NYLIUM;
            default -> MapColor.GRASS;
        };
    }

    /** 128 x 128 픽셀을 그림. step = 1픽셀당 블록 수, (x0, z0) = 왼쪽 위 모서리 */
    private static void paint(MapItemSavedData data, int x0, int z0, int step) {
        int[] prevH = new int[128];
        for (int px = 0; px < 128; px++) prevH[px] = KingdomTerrain.column(x0 + px * step + step / 2, z0 - step / 2).h();
        double[] edge = new double[1];
        for (int pz = 0; pz < 128; pz++) {
            for (int px = 0; px < 128; px++) {
                int x = x0 + px * step + step / 2, z = z0 + pz * step + step / 2;
                KingdomTerrain.Column c = KingdomTerrain.column(x, z);
                MapColor col = color(c);
                MapColor.Brightness br;
                boolean wet = c.water() != Integer.MIN_VALUE && c.water() >= c.h();
                if (wet) {
                    int depth = c.water() - c.h();
                    br = depth > 12 ? MapColor.Brightness.LOWEST : depth > 5 ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
                } else {
                    int d = c.h() - prevH[px];
                    int th = Math.max(1, step / 8);
                    br = d > th ? MapColor.Brightness.HIGH : d < -th ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
                }
                // 길: 픽셀 크기의 절반 안에 길이 지나면 길 색
                if (!KingdomRoads.excluded(x, z)) {
                    int ri = KingdomRoads.nearest(x, z, Math.max(1, step / 2.0), edge);
                    if (ri >= 0) {
                        int type = KingdomRoads.ROADS[ri].type();
                        col = wet ? MapColor.WOOD : type == 1 ? MapColor.STONE : type == 3 ? MapColor.COLOR_BLACK : MapColor.DIRT;
                        br = MapColor.Brightness.HIGH;
                    }
                }
                prevH[px] = c.h();
                data.setColor(px, pz, col.getPackedId(br));
            }
        }
    }

    private static ItemStack register(ServerLevel level, MapItemSavedData fresh, String name) {
        MapItemSavedData data = fresh.locked();                 // 실제 지형으로 덮어쓰지 않게 잠금
        MapId id = level.getFreeMapId();
        level.setMapData(id, data);
        ItemStack stack = new ItemStack(Items.FILLED_MAP);
        stack.set(DataComponents.MAP_ID, id);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return stack;
    }

    /** 지역 지도 (2048 x 2048, 내 위치 화살표 + 쉼터·둥지·보스 결투장·마스터플랜 지역 표시) */
    public static ItemStack region(ServerLevel kingdom, int cx, int cz) {
        MapItemSavedData fresh = MapItemSavedData.createFresh(cx, cz, (byte) 4, true, true, ModWorldgen.KINGDOM);
        int step = 16, x0 = fresh.centerX - 64 * step, z0 = fresh.centerZ - 64 * step;
        ItemStack stack = register(kingdom, fresh, "§b왕국 대륙 지도 (" + fresh.centerX + ", " + fresh.centerZ + ")");
        MapItemSavedData data = kingdom.getMapData(stack.get(DataComponents.MAP_ID));
        paint(data, x0, z0, step);
        int n = 0;
        for (RoadSites.Site s : RoadSites.sites()) {
            if (Math.abs(s.x() - fresh.centerX) > 1000 || Math.abs(s.z() - fresh.centerZ) > 1000) continue;
            Holder<MapDecorationType> type = switch (s.kind()) {
                case REST -> MapDecorationTypes.BLUE_MARKER;
                case CAMP -> MapDecorationTypes.RED_MARKER;
                case ARENA -> MapDecorationTypes.RED_X;
            };
            MapItemSavedData.addTargetDecoration(stack, new BlockPos(s.x(), 0, s.z()), "site" + n++, type);
        }
        for (io.github.aswq0258.magiccity.site.Hamlets.Spot h : io.github.aswq0258.magiccity.site.Hamlets.spots()) {
            if (Math.abs(h.x() - fresh.centerX) > 1000 || Math.abs(h.z() - fresh.centerZ) > 1000) continue;
            MapItemSavedData.addTargetDecoration(stack, new BlockPos(h.x(), 0, h.z()), "hamlet" + n++, MapDecorationTypes.PLAINS_VILLAGE);
        }
        for (Object[] p : PLAN_SITES) {
            int x = KingdomTerrain.worldX((Integer) p[2]), z = KingdomTerrain.worldZ((Integer) p[3]);
            if (Math.abs(x - fresh.centerX) > 1000 || Math.abs(z - fresh.centerZ) > 1000) continue;
            MapItemSavedData.addTargetDecoration(stack, new BlockPos(x, 0, z), "plan" + p[0], MapDecorationTypes.TARGET_POINT);
        }
        data.setDirty();
        return stack;
    }

    /** 대륙 전도 (전체를 한 장에, 주요 지역 = 흰 점, 필드보스 = 빨간 점, 옛 왕도 = 검붉은 점) */
    public static ItemStack world(ServerLevel kingdom) {
        int step = 76, x0 = -400 - 64 * step, z0 = -300 - 64 * step;
        MapItemSavedData fresh = MapItemSavedData.createFresh(-400, -300, (byte) 4, false, false, ModWorldgen.KINGDOM);
        ItemStack stack = register(kingdom, fresh, "§6왕국 대륙 전도");
        MapItemSavedData data = kingdom.getMapData(stack.get(DataComponents.MAP_ID));
        paint(data, x0, z0, step);
        for (Object[] p : PLAN_SITES) {
            int x = KingdomTerrain.worldX((Integer) p[2]), z = KingdomTerrain.worldZ((Integer) p[3]);
            dot(data, (x - x0) / step, (z - z0) / step, "B1".equals(p[0]) ? MapColor.COLOR_RED.getPackedId(MapColor.Brightness.LOW)
                    : MapColor.SNOW.getPackedId(MapColor.Brightness.HIGH), 1);
        }
        for (io.github.aswq0258.magiccity.site.Hamlets.Spot h : io.github.aswq0258.magiccity.site.Hamlets.spots()) {
            dot(data, (h.x() - x0) / step, (h.z() - z0) / step, MapColor.GOLD.getPackedId(MapColor.Brightness.HIGH), 0);
        }
        for (RoadSites.Site s : RoadSites.sites()) {
            if (s.kind() == RoadSites.Kind.ARENA) dot(data, (s.x() - x0) / step, (s.z() - z0) / step, MapColor.COLOR_RED.getPackedId(MapColor.Brightness.HIGH), 0);
        }
        data.setDirty();
        return stack;
    }

    private static void dot(MapItemSavedData data, int px, int pz, byte color, int r) {
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
            int x = px + dx, z = pz + dz;
            if (x >= 0 && x < 128 && z >= 0 && z < 128) data.setColor(x, z, color);
        }
    }
}
