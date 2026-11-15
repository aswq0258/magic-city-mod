package io.github.aswq0258.magiccity.city.feature;

import io.github.aswq0258.magiccity.build.Placer;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 건물 내부 좌표계. 문이 있는 앞벽을 v=0 으로 두고 u 는 오른쪽, v 는 안쪽(뒤)으로 증가합니다.
 * 덕분에 건물이 어느 방향을 보든 같은 코드로 지을 수 있습니다.
 */
public final class Frame {
    public final Direction front, back, right, left;
    public final int width, depth;              // u 방향 W, v 방향 D
    public final int minX, minZ, maxX, maxZ;    // 월드 기준 범위
    private final int ox, oz;

    public Frame(int centerX, int centerZ, Direction front, int width, int depth) {
        this.front = front;
        this.back = front.getOpposite();
        this.right = back.getClockWise();
        this.left = right.getOpposite();
        this.width = width;
        this.depth = depth;
        boolean rightIsX = right.getAxis() == Direction.Axis.X;
        int sizeX = rightIsX ? width : depth;
        int sizeZ = rightIsX ? depth : width;
        this.minX = centerX - (sizeX - 1) / 2;
        this.minZ = centerZ - (sizeZ - 1) / 2;
        this.maxX = minX + sizeX - 1;
        this.maxZ = minZ + sizeZ - 1;
        this.ox = (right.getStepX() + back.getStepX()) > 0 ? minX : maxX;
        this.oz = (right.getStepZ() + back.getStepZ()) > 0 ? minZ : maxZ;
    }

    public int x(int u, int v) {
        return ox + right.getStepX() * u + back.getStepX() * v;
    }

    public int z(int u, int v) {
        return oz + right.getStepZ() * u + back.getStepZ() * v;
    }

    /** 내부 좌표 (u, 높이 ly, v) 에 블록 놓기 */
    public void set(Placer p, int base, int u, int ly, int v, BlockState state) {
        p.set(x(u, v), base + ly, z(u, v), state);
    }

    public int centerX() {
        return (minX + maxX) >> 1;
    }

    public int centerZ() {
        return (minZ + maxZ) >> 1;
    }
}
