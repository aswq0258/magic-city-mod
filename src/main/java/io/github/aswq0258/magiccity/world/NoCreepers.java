package io.github.aswq0258.magiccity.world;

import net.minecraft.world.entity.monster.Creeper;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * 크리퍼 없음 (2026-11-20 사용자 결정): 모든 차원에서 크리퍼가 생기지 않고, 이미 있던 크리퍼는 그 청크가 불러와질 때 사라진다.
 * 자연 생성 목록에서도 빼고(biome_modifier/no_creepers.json), 그 밖의 길(스폰 알·스포너·명령·번개 등)은 여기서 막는다.
 */
public final class NoCreepers {
    private NoCreepers() {}

    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof Creeper) event.setCanceled(true);
    }
}
