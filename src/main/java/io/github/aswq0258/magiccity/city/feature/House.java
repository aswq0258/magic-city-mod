package io.github.aswq0258.magiccity.city.feature;

import io.github.aswq0258.magiccity.build.Placer;
import io.github.aswq0258.magiccity.city.CityLayout;
import io.github.aswq0258.magiccity.city.Palette;
import io.github.aswq0258.magiccity.city.Shapes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

import static io.github.aswq0258.magiccity.city.Palette.*;

/** 주민 집: 흰 벽 + 석영 기둥 모서리 + 하늘색 창 + 민트 구리 모임지붕. 침대와 직업 블록이 있어 마을로 인식됩니다. */
public class House extends Feature {
    protected final Frame f;
    protected final int floors, floorH;
    protected final BlockState wallMat;
    protected final BlockState job;
    private final boolean villager;

    public House(CityLayout city, CityLayout.Lot lot, RandomSource rand, boolean villager) {
        this(lot, new Frame(lot.centerX(), lot.centerZ(), lot.front(),
                        7 + 2 * rand.nextInt(2), 7 + 2 * rand.nextInt(3)),
                1 + rand.nextInt(2), 4,
                rand.nextBoolean() ? QUARTZ_BRICKS : CALCITE,
                JOB_BLOCKS[rand.nextInt(JOB_BLOCKS.length)], villager, 1);
    }

    protected House(CityLayout.Lot lot, Frame f, int floors, int floorH, BlockState wallMat, BlockState job,
                    boolean villager, int margin) {
        super(f.minX - margin, f.minZ - margin, f.maxX + margin, f.maxZ + margin);
        this.f = f;
        this.floors = floors;
        this.floorH = floorH;
        this.wallMat = wallMat;
        this.job = job;
        this.villager = villager;
    }

    @Override
    public void build(Placer p, CityLayout city) {
        int b = city.base;
        int W = f.width, D = f.depth;
        int topLy = floors * floorH;
        int doorU = W / 2;
        int ladderU = W - 2, ladderV = 2;

        for (int u = 0; u < W; u++) {
            for (int v = 0; v < D; v++) {
                int x = f.x(u, v), z = f.z(u, v);
                if (!p.contains(x, z)) continue;
                boolean edgeU = u == 0 || u == W - 1, edgeV = v == 0 || v == D - 1;
                boolean edge = edgeU || edgeV, corner = edgeU && edgeV;
                int along = edgeV ? u : v, len = edgeV ? W : D;

                p.set(x, b, z, SMOOTH_QUARTZ);
                for (int ly = 1; ly <= topLy; ly++) {
                    BlockState s;
                    if (edge) {
                        if (corner) s = QUARTZ_PILLAR;
                        else if (ly % floorH == 0) s = SMOOTH_QUARTZ;
                        else s = wallMat;
                        int inFloor = ly % floorH;
                        boolean windowRow = inFloor == 2 || (floorH >= 5 && inFloor == 3);
                        boolean ladderWall = u == W - 1 && v == ladderV;
                        if (!corner && windowRow && along % 2 == 1 && along < len - 1 && !ladderWall) s = GLASS_PANE;
                        if (v == 0 && u == doorU && ly <= 2) s = Palette.door(f.back, ly == 2);
                    } else {
                        s = AIR;
                        if (ly % floorH == 0) s = (u == W / 2 && v == D / 2) ? LIGHT : SMOOTH_QUARTZ;
                        if (floors > 1 && u == ladderU && v == ladderV && ly < topLy) s = ladder(f.left);
                    }
                    p.set(x, b + ly, z, s);
                }
            }
        }

        // 가구 (1층): 침대 + 직업 블록
        p.set(f.x(1, D - 2), b + 1, f.z(1, D - 2), bed(f.right, false));
        p.set(f.x(2, D - 2), b + 1, f.z(2, D - 2), bed(f.right, true));
        p.set(f.x(W - 2, D - 2), b + 1, f.z(W - 2, D - 2), job);

        // 문 위 차양
        p.set(f.x(doorU, -1), b + 3, f.z(doorU, -1), MINT_SLAB);

        Shapes.hipRoof(p, f.minX - 1, f.minZ - 1, f.maxX + 1, f.maxZ + 1, b + topLy + 1);
    }

    @Override
    public void spawn(ServerLevel level, CityLayout city) {
        if (!villager) return;
        int u = f.width / 2, v = f.depth / 2;
        Shapes.spawnVillager(level, f.x(u, v) + 0.5, city.base + 1, f.z(u, v) + 0.5);
    }
}
