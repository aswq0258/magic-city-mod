package dev.fantasybosses;

import dev.fantasybosses.command.PreviewCommands;
import dev.fantasybosses.combat.RadicionCombat;
import dev.fantasybosses.entity.RadicionEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

@Mod(FantasyBosses.MOD_ID)
public final class FantasyBosses {
    public static final String MOD_ID = "fantasybosses";

    public FantasyBosses(IEventBus modBus) {
        dev.fantasybosses.field.FieldMonsters.register(BossEntities.TYPES);
        BossEntities.TYPES.register(modBus);
        BossSounds.TYPES.register(modBus);
        modBus.addListener(this::registerAttributes);
        NeoForge.EVENT_BUS.addListener(PreviewCommands::register);
        NeoForge.EVENT_BUS.addListener(dev.fantasybosses.command.FieldCommands::register);
        NeoForge.EVENT_BUS.addListener(RadicionCombat::tickLarva);
    }

    private void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(BossEntities.RADICION.get(), RadicionEntity.createAttributes().build());
        event.put(BossEntities.ROOT_ORGAN.get(),dev.fantasybosses.entity.ArenaOrgan.attributes().build());
        event.put(BossEntities.BROOD_EGG.get(),dev.fantasybosses.entity.ArenaOrgan.attributes().build());
        for (var spec : dev.fantasybosses.field.FieldMonsters.SPECS)
            event.put(dev.fantasybosses.field.FieldMonsters.TYPES.get(spec.id()).get(),
                    dev.fantasybosses.field.FieldMonster.attributes(spec.elite()).build());
    }
}
