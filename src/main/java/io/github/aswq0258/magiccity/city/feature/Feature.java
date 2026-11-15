package io.github.aswq0258.magiccity.city.feature;

import io.github.aswq0258.magiccity.build.Placer;
import io.github.aswq0258.magiccity.city.CityLayout;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

/**
 * 도시의 건물 하나. build() 는 청크마다 호출되며, Placer 가 해당 청크 밖의 블록은 무시합니다.
 * 그래서 build() 안에서는 "건물 전체를 짓는 코드"를 그대로 쓰면 됩니다.
 * 무작위 요소는 생성자에서 미리 정해 두어야 청크 경계에서도 모양이 어긋나지 않습니다.
 */
public abstract class Feature {
    /** 이 건물이 블록을 놓는 전체 범위 */
    public final int minX, minZ, maxX, maxZ;

    protected Feature(int minX, int minZ, int maxX, int maxZ) {
        this.minX = minX;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxZ = maxZ;
    }

    public abstract void build(Placer p, CityLayout city);

    /** 마지막 패스에서 건물 중심 청크가 처리될 때 한 번 호출 (주민 소환 등) */
    public void spawn(ServerLevel level, CityLayout city) {}

    /** 가로등 격자가 이 칸을 피해야 하는지 */
    public boolean occupies(int x, int z) {
        return x >= minX - 1 && x <= maxX + 1 && z >= minZ - 1 && z <= maxZ + 1;
    }

    public long anchorChunk() {
        return ChunkPos.asLong(((minX + maxX) >> 1) >> 4, ((minZ + maxZ) >> 1) >> 4);
    }
}
