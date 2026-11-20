package dev.fantasybosses;

import dev.fantasybosses.entity.RadicionEntity;
import dev.fantasybosses.entity.ArenaOrgan;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class BossEntities {
    private BossEntities() {}
    public static final DeferredRegister<EntityType<?>> TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, FantasyBosses.MOD_ID);
    public static final DeferredHolder<EntityType<?>, EntityType<RadicionEntity>> RADICION =
            TYPES.register("radicion", () -> EntityType.Builder.<RadicionEntity>of(
                    RadicionEntity::new, MobCategory.MISC)
                    .sized(2.0F, 2.3F)
                    .clientTrackingRange(12)
                    .updateInterval(1)
                    .build(FantasyBosses.MOD_ID + ":radicion"));
    public static final DeferredHolder<EntityType<?>,EntityType<ArenaOrgan>> ROOT_ORGAN = organ("root_organ",1.5F,1.98F);
    public static final DeferredHolder<EntityType<?>,EntityType<ArenaOrgan>> BROOD_EGG = organ("brood_egg",.8F,.84F);
    private static DeferredHolder<EntityType<?>,EntityType<ArenaOrgan>> organ(String id,float w,float h){
        return TYPES.register(id,()->EntityType.Builder.<ArenaOrgan>of(ArenaOrgan::new,MobCategory.MISC).sized(w,h).clientTrackingRange(10).updateInterval(2).build(FantasyBosses.MOD_ID+":"+id));
    }
}
