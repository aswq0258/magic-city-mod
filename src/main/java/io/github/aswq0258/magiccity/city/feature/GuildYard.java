package io.github.aswq0258.magiccity.city.feature;

import io.github.aswq0258.magiccity.build.Placer;
import io.github.aswq0258.magiccity.city.CityLayout;
import io.github.aswq0258.magiccity.city.Deco;
import io.github.aswq0258.magiccity.city.Fantasy;
import io.github.aswq0258.magiccity.city.Royal;
import io.github.aswq0258.magiccity.city.Shapes;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CarvedPumpkinBlock;
import net.minecraft.world.level.block.state.BlockState;

import static io.github.aswq0258.magiccity.city.Palette.*;

/**
 * 모험가 길드 마당 (길드 본관 옆, 도면 S06 동쪽)
 *  - 망루(사다리로 올라감), 의뢰 게시판(깃발 의뢰서 + 랜턴), 훈련장(허수아비·과녁·무기 손질대),
 *    모닥불 쉼터(통나무 의자), 입구 깃대
 * 좌표는 도면 좌표(u, v)로 받아 CityLayout 으로 변환합니다.
 */
public class GuildYard extends Feature {
    private final CityLayout c;
    private final int u0, v0, u1, v1;
    private final long seed;

    public GuildYard(CityLayout city, int u0, int v0, int u1, int v1, long seed) {
        super(Math.min(city.wx(u0), city.wx(u1)), Math.min(city.wz(v0), city.wz(v1)),
                Math.max(city.wx(u0), city.wx(u1)), Math.max(city.wz(v0), city.wz(v1)));
        this.c = city;
        this.u0 = u0;
        this.v0 = v0;
        this.u1 = u1;
        this.v1 = v1;
        this.seed = seed;
    }

    private void at(Placer p, int u, int ly, int v, BlockState s) {
        p.set(c.wx(u), groundY + ly, c.wz(v), s);
    }

    @Override
    public void build(Placer p, CityLayout city) {
        final int b = groundY;
        // 바닥: 훈련장은 거친 흙, 나머지는 돌길
        p.forColumns(minX, minZ, maxX, maxZ, (x, z) -> {
            int u = c.u(x), v = c.v(z);
            boolean yard = u >= u0 + 2 && u <= u1 - 1 && v >= v0 + 1 && v <= v0 + 17;
            boolean fence = yard && (u == u0 + 2 || u == u1 - 1 || v == v0 + 1 || v == v0 + 17);
            int h = Shapes.hash(x, 31, z, seed);
            p.set(x, b, z, yard ? (h % 4 == 0 ? Blocks.GRAVEL.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState())
                    : (h % 5 == 0 ? Blocks.DIRT_PATH.defaultBlockState() : CALCITE));
            if (fence && !(v == v0 + 17 && Math.abs(u - (u0 + u1) / 2) <= 1)) p.set(x, b + 1, z, Blocks.SPRUCE_FENCE.defaultBlockState());
        });
        // 허수아비 3개 + 과녁 2개
        BlockState pumpkin = Blocks.CARVED_PUMPKIN.defaultBlockState().setValue(CarvedPumpkinBlock.FACING, Direction.SOUTH);
        for (int i = 0; i < 3; i++) {
            int u = u0 + 6 + i * 7, v = v0 + 6;
            at(p, u, 1, v, Blocks.SPRUCE_FENCE.defaultBlockState());
            at(p, u, 2, v, Blocks.HAY_BLOCK.defaultBlockState());
            at(p, u, 3, v, pumpkin);
        }
        for (int i = 0; i < 2; i++) {
            at(p, u0 + 9 + i * 10, 1, v0 + 3, Blocks.HAY_BLOCK.defaultBlockState());
            at(p, u0 + 9 + i * 10, 2, v0 + 3, Blocks.TARGET.defaultBlockState());
        }
        at(p, u1 - 3, 1, v0 + 12, Blocks.GRINDSTONE.defaultBlockState());
        at(p, u1 - 3, 1, v0 + 14, Blocks.ANVIL.defaultBlockState());
        at(p, u1 - 4, 1, v0 + 14, Deco.BARREL);
        // 모닥불 쉼터
        int fu = u0 + 8, fv = v0 + 13;
        at(p, fu, 1, fv, Deco.CAMPFIRE);
        at(p, fu - 2, 1, fv, Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState());
        at(p, fu + 2, 1, fv, Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState());
        at(p, fu, 1, fv - 2, Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState());
        // 의뢰 게시판 (짙은 참나무 판 + 깃발 의뢰서)
        int gu = u0 + 4, gv0 = v1 - 13;
        for (int v = gv0; v <= gv0 + 6; v++) {
            for (int ly = 1; ly <= 4; ly++) {
                boolean post = v == gv0 || v == gv0 + 6;
                at(p, gu, ly, v, post ? Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState() : ly == 1 ? AIR : Royal.DARK_PLANKS);
            }
            at(p, gu, 5, v, Blocks.DARK_OAK_SLAB.defaultBlockState());
            if (v > gv0 && v < gv0 + 6 && v % 2 == 0) {
                at(p, gu + 1, 3, v, (Shapes.hash(gu, v, 0, seed) & 1) == 0 ? Royal.redBanner(Direction.EAST) : Royal.mintBanner(Direction.EAST));
            }
        }
        at(p, gu, 6, gv0, Blocks.LANTERN.defaultBlockState());
        at(p, gu, 6, gv0 + 6, Blocks.LANTERN.defaultBlockState());
        // 망루
        Shapes.roundTower(p, c.wx(u1 - 6), c.wz(v1 - 6), 4, b, 28, Direction.WEST);
        p.set(c.wx(u1 - 6), b + 36, c.wz(v1 - 6), Royal.GOLD);
        // 입구 깃대 + 수정 가로등
        for (int du : new int[]{-3, 3}) {
            int u = (u0 + u1) / 2 + du, v = v0 + 19;
            for (int ly = 1; ly <= 7; ly++) at(p, u, ly, v, Blocks.SPRUCE_FENCE.defaultBlockState());
            at(p, u, 7, v + 1, du < 0 ? Royal.redBanner(Direction.NORTH) : Royal.mintBanner(Direction.NORTH));
        }
        Fantasy.crystalLamp(p, c.wx(u0 + 4), c.wz(v1 - 3), b);
    }
}
