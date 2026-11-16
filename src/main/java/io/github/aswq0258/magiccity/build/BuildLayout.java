package io.github.aswq0258.magiccity.build;

import io.github.aswq0258.magiccity.city.CityLayout;
import io.github.aswq0258.magiccity.city.feature.Feature;
import net.minecraft.world.level.ChunkPos;

import java.util.List;

/** 청크 단위로 지을 수 있는 설계도 (왕성도시, 옛 왕도 같은 지역) */
public interface BuildLayout {
    int cx();

    int cz();

    int base();

    /** 지을 범위 (정사각형 반지름) */
    int radius();

    void buildColumn(Placer p, int x, int z);

    List<Feature> featuresIn(ChunkPos cp);

    /** 실내 조명 범위, 없으면 null */
    int[] lightRange(int x, int z);

    /** 건물에 넘겨줄 왕성도시 설계도 (지역 설계도는 null) */
    CityLayout city();

    /** 지을 청크 목록 (null 이면 중심 기준 정사각형 radius 안 전부) — 대륙 곳곳에 흩어진 건물용 */
    default List<ChunkPos> chunkList() {
        return null;
    }

    /** true = 모든 청크에 두 번째(물·주민) 패스를 돌림 (나무 다시 심기 등) */
    default boolean waterPassAll() {
        return false;
    }

    /** 계단·울타리 연결을 맞출 높이 범위 */
    default int shapeMinY() {
        return base() - 26;
    }

    default int shapeMaxY() {
        return base() + 160;
    }
}
