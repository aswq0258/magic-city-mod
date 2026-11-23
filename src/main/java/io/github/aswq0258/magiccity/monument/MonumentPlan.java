package io.github.aswq0258.magiccity.monument;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 웅장화 요소 하나의 설계도: 놓을 블록 + 받침이 있어야 하는 칸.
 * 월드를 읽거나 쓰지 않고 좌표·블록만 모음 → 검사(survey)와 적용(apply)이 같은 설계도를 씀.
 * 같은 칸을 두 번 쓰면 나중 것이 이김 (요소 안에서만).
 */
public final class MonumentPlan {
    /** 요소 이름 (보고용), 후보 번호 */
    public final String name;
    public final int variant;
    final Map<Long, BlockState> put = new LinkedHashMap<>();
    final Set<Long> support = new LinkedHashSet<>();
    int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
    int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

    public MonumentPlan(String name, int variant) {
        this.name = name;
        this.variant = variant;
    }

    public MonumentPlan set(int x, int y, int z, BlockState s) {
        put.put(BlockPos.asLong(x, y, z), s);
        grow(x, y, z);
        return this;
    }

    /** 이 칸에는 이미 단단한 블록이 있어야 함 (허공에 뜬 구조 방지) */
    public MonumentPlan support(int x, int y, int z) {
        support.add(BlockPos.asLong(x, y, z));
        grow(x, y, z);
        return this;
    }

    public MonumentPlan box(int x0, int y0, int z0, int x1, int y1, int z1, BlockState s) {
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++)
            for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++)
                for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) set(x, y, z, s);
        return this;
    }

    private void grow(int x, int y, int z) {
        minX = Math.min(minX, x);
        minY = Math.min(minY, y);
        minZ = Math.min(minZ, z);
        maxX = Math.max(maxX, x);
        maxY = Math.max(maxY, y);
        maxZ = Math.max(maxZ, z);
    }

    public int size() {
        return put.size();
    }

    public boolean isEmpty() {
        return put.isEmpty();
    }

    /** 위에서 아래 순서가 아니라 아래에서 위로 놓아야 받침 블록(난간·등불)이 바로 붙음 */
    List<Map.Entry<Long, BlockState>> ordered() {
        List<Map.Entry<Long, BlockState>> out = new ArrayList<>(put.entrySet());
        out.sort((a, b) -> Integer.compare(BlockPos.getY(a.getKey()), BlockPos.getY(b.getKey())));
        return out;
    }

    public String where() {
        return String.format("(%d~%d, %d~%d, %d~%d)", minX, maxX, minY, maxY, minZ, maxZ);
    }
}
