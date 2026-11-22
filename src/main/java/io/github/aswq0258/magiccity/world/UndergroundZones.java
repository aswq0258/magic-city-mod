package io.github.aswq0258.magiccity.world;

import io.github.aswq0258.magiccity.site.Restore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 지어 둔 건축물·던전이 차지하는 영역 상자 {x0, z0, x1, z1, y0, y1} (2026-11-22).
 * 철거 명령(Restore)이 쓰는 영역을 그대로 씀 — 왕성도시(하수도 포함)·촌락·길 거점·세계수(뿌리 던전)·드워프(심층 광산)·
 * 옛 왕도·항구·드래곤 레어·부유섬·저주받은 숲(심연 균열)·국경도시·유적·엘프 관문·서리 결투장.
 * 지형 생성기가 처음 쓸 때 한 번 만듦 (여러 스레드에서 불러도 안전한 지연 초기화).
 */
public final class UndergroundZones {
    private UndergroundZones() {}

    private static final class Holder {
        static final List<int[]> ALL;
        static final List<int[]> DEEP;

        static {
            List<int[]> all = new ArrayList<>();
            for (Restore r : Restore.allSites()) all.addAll(r.areas());
            all.addAll(Restore.city(KingdomTerrain.CX, KingdomTerrain.CZ, KingdomTerrain.CITY_Y, true).areas());
            List<int[]> deep = new ArrayList<>();
            for (int[] a : all) if (a[4] - CaveRules.MARGIN <= CaveRules.TOP) deep.add(a);   // 동굴 높이(Y 0 미만)까지 내려오는 영역
            ALL = Collections.unmodifiableList(all);
            DEEP = Collections.unmodifiableList(deep);
        }
    }

    /** 모든 영역 */
    public static List<int[]> all() {
        return Holder.ALL;
    }

    /** 동굴 높이까지 내려오는 영역 (심연 균열·드래곤 레어 보물 동굴 등) — 동굴 판정에는 이것만 보면 됨 */
    public static List<int[]> deep() {
        return Holder.DEEP;
    }

    /** 이 칸을 동굴로 파낼지 */
    public static boolean carve(int x, int y, int z) {
        return CaveRules.carve(x, y, z, Holder.DEEP);
    }
}
