package io.github.aswq0258.magiccity.world;

import io.github.aswq0258.magiccity.site.WorldTree;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Enemy;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;

/**
 * 몬스터가 저절로 생기지 않는 안전 지대.
 * 세계수 성역은 수관이 하늘을 덮어 낮에도 어두우므로, 성역 안(지상·지하 모두)에서는 적대 몹의 자연 스폰을 막음.
 * 스포너·소환·보스 등 자연 스폰이 아닌 것은 그대로 둠.
 */
public final class SafeZones {
    private SafeZones() {}

    public static boolean noMonsters(double x, double z) {
        return Math.hypot(x - WorldTree.OX, z - WorldTree.OZ) < Math.max(WorldTree.R + 20, ElfWood.RZ + 10);   // 보랏빛 숲 전체
    }

    public static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        MobSpawnType type = event.getSpawnType();
        if (type != MobSpawnType.NATURAL && type != MobSpawnType.CHUNK_GENERATION && type != MobSpawnType.PATROL) return;
        if (!(event.getEntity() instanceof Enemy)) return;
        if (!event.getLevel().getLevel().dimension().equals(ModWorldgen.KINGDOM)) return;
        if (noMonsters(event.getX(), event.getZ())) event.setSpawnCancelled(true);
    }
}
