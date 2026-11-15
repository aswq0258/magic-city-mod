package io.github.aswq0258.magiccity.city.feature;

import io.github.aswq0258.magiccity.build.Placer;
import io.github.aswq0258.magiccity.city.CityLayout;
import io.github.aswq0258.magiccity.city.Deco;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import static io.github.aswq0258.magiccity.city.Palette.*;

/** 공동묘지의 영묘: 7x7 석실, 모서리 기둥, 민트 돔, 영혼 랜턴, 안에 석관(상자)과 촛불 */
public class Mausoleum extends Feature {
    private final int mx, mz;
    private final Direction door;

    public Mausoleum(int mx, int mz, Direction door) {
        super(mx - 4, mz - 4, mx + 4, mz + 4);
        this.mx = mx;
        this.mz = mz;
        this.door = door;
    }

    @Override
    public void build(Placer p, CityLayout city) {
        final int b = groundY;
        BlockState soul = Blocks.SOUL_LANTERN.defaultBlockState();
        p.forColumns(mx - 4, mz - 4, mx + 4, mz + 4, (x, z) -> {
            int dx = x - mx, dz = z - mz, m = Math.max(Math.abs(dx), Math.abs(dz));
            p.set(x, b, z, m <= 3 ? CALCITE : SMOOTH_QUARTZ);
            if (m == 3) {
                boolean corner = Math.abs(dx) == 3 && Math.abs(dz) == 3;
                boolean doorway = dx == door.getStepX() * 3 && dz == door.getStepZ() * 3
                        || (door.getStepX() != 0 ? dx == door.getStepX() * 3 && Math.abs(dz) <= 1 : dz == door.getStepZ() * 3 && Math.abs(dx) <= 1);
                for (int ly = 1; ly <= 5; ly++) {
                    BlockState s = corner ? QUARTZ_PILLAR : (ly == 5 ? MINT_CUT : SMOOTH_QUARTZ);
                    if (doorway && !corner && ly <= 3) s = AIR;
                    p.set(x, b + ly, z, s);
                }
            } else if (m < 3) {
                for (int ly = 1; ly <= 4; ly++) p.set(x, b + ly, z, AIR);
                p.set(x, b + 5, z, SMOOTH_QUARTZ);
            }
            double d = Math.hypot(dx, dz);
            for (int k = 1; k <= 4; k++) {
                double d3 = Math.sqrt(d * d + k * k);
                if (d3 <= 3.9 && d3 > 2.9) p.set(x, b + 5 + k, z, MINT_BLOCK);
            }
        });
        p.set(mx, b + 10, mz, END_ROD);
        p.set(mx, b + 1, mz, Deco.chest(door));
        p.set(mx - door.getStepZ(), b + 1, mz + door.getStepX(), Deco.candles(3));
        p.set(mx + door.getStepZ(), b + 1, mz - door.getStepX(), Deco.candles(2));
        p.set(mx, b + 4, mz, soul.setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true));
        int ox = mx + door.getStepX() * 4, oz = mz + door.getStepZ() * 4;
        p.set(ox - door.getStepZ() * 2, b + 1, oz + door.getStepX() * 2, soul);
        p.set(ox + door.getStepZ() * 2, b + 1, oz - door.getStepX() * 2, soul);
    }
}
