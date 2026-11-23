package io.github.aswq0258.magiccity.monument;

import io.github.aswq0258.magiccity.arena.BossRoomRules;
import io.github.aswq0258.magiccity.arena.BossRooms;
import io.github.aswq0258.magiccity.arena.SealAltar;
import io.github.aswq0258.magiccity.boss.ManagedBoss;
import io.github.aswq0258.magiccity.quest.QuestNpcs;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * 덮어쓰면 안 되는 곳. 검사(survey)할 때 한 번 만들어 씀.
 *  - 챕터 보스 방: 전장이 복사하는 범위 그대로 (boss_rooms 데이터, 없으면 보스 자리 둘레 32칸·위아래 16칸) + 여유 4칸
 *  - 봉인 제단 핵심 블록 둘레 4칸
 *  - 요소 범위 안의 퀘스트 NPC·이름 붙은 주민 둘레 3칸
 * 블록 엔티티(상자·스포너·독서대…) 칸은 MonumentRunner 가 "공기가 아님"으로 이미 막음.
 */
public final class MonumentGuard {
    /** {x0, y0, z0, x1, y1, z1, 장} */
    private final List<int[]> boxes = new ArrayList<>();

    public MonumentGuard() {
        for (ManagedBoss b : ManagedBoss.ALL) {
            Vec3 c = b.altarCenter();
            BlockPos bc = BlockPos.containing(c);
            BossRoomRules.Spec s = BossRooms.spec(b);
            if (s != null) {
                int[] o = BossRoomRules.captureOrigin(bc.getX(), bc.getY(), bc.getZ(), s);
                boxes.add(new int[]{o[0] - 4, o[1] - 4, o[2] - 4, o[0] + s.sizeX() + 3, o[1] + s.sizeY() + 3, o[2] + s.sizeZ() + 3, b.chapter()});
            } else {
                boxes.add(new int[]{bc.getX() - 36, bc.getY() - 20, bc.getZ() - 36, bc.getX() + 36, bc.getY() + 20, bc.getZ() + 36, b.chapter()});
            }
            BlockPos core = SealAltar.core(b.chapter());
            if (core != null) boxes.add(new int[]{core.getX() - 4, core.getY() - 4, core.getZ() - 4, core.getX() + 4, core.getY() + 4, core.getZ() + 4, b.chapter()});
        }
    }

    /** 요소 범위 안 NPC 들을 보호 상자로 추가 (요소마다 부름) */
    public List<int[]> npcBoxes(ServerLevel level, MonumentPlan plan) {
        List<int[]> out = new ArrayList<>();
        AABB area = new AABB(plan.minX - 3, plan.minY - 3, plan.minZ - 3, plan.maxX + 4, plan.maxY + 4, plan.maxZ + 4);
        for (Entity e : level.getEntities((Entity) null, area, e -> e instanceof Villager)) {
            boolean quest = QuestNpcs.npcId(e) != null;
            if (!quest && !e.hasCustomName()) continue;
            BlockPos p = e.blockPosition();
            out.add(new int[]{p.getX() - 3, p.getY() - 2, p.getZ() - 3, p.getX() + 3, p.getY() + 4, p.getZ() + 3, 0});
        }
        return out;
    }

    /** 이 칸이 보호 구역이면 그 이유, 아니면 null */
    public String why(int x, int y, int z, List<int[]> extra) {
        for (int[] b : boxes) if (inside(b, x, y, z)) return b[6] + "장 보스 방·제단";
        for (int[] b : extra) if (inside(b, x, y, z)) return "NPC 둘레";
        return null;
    }

    private static boolean inside(int[] b, int x, int y, int z) {
        return x >= b[0] && x <= b[3] && y >= b[1] && y <= b[4] && z >= b[2] && z <= b[5];
    }

    /** 놓아도 되는 기존 칸: 공기, 또는 바로 바뀌는 풀·꽃·눈 (블록 엔티티·물·용암 제외) */
    public static boolean free(BlockState s) {
        if (s.isAir()) return true;
        if (s.hasBlockEntity() || !s.getFluidState().isEmpty()) return false;
        return s.canBeReplaced();
    }

    /** 받침으로 쓸 수 있는 칸 */
    public static boolean solid(BlockState s) {
        return !s.isAir() && s.getFluidState().isEmpty() && !s.canBeReplaced();
    }
}
