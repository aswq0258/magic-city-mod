package dev.fantasybosses.client;

import dev.fantasybosses.BossEntities;
import dev.fantasybosses.FantasyBosses;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = FantasyBosses.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ClientEvents {
    private ClientEvents() {}
    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(BossEntities.RADICION.get(), RadicionRenderer::new);
        event.registerEntityRenderer(BossEntities.ROOT_ORGAN.get(),ArenaOrganRenderer::new);
        event.registerEntityRenderer(BossEntities.BROOD_EGG.get(),ArenaOrganRenderer::new);
        for (var spec : dev.fantasybosses.field.FieldMonsters.SPECS)
            event.registerEntityRenderer(dev.fantasybosses.field.FieldMonsters.TYPES.get(spec.id()).get(),
                    ctx -> new FieldMonsterRenderer(ctx, spec));
    }
}
