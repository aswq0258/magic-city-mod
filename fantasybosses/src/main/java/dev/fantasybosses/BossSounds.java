package dev.fantasybosses;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Common registry: contains no client-only classes. */
public final class BossSounds {
    private BossSounds() {}
    public static final DeferredRegister<SoundEvent> TYPES = DeferredRegister.create(Registries.SOUND_EVENT, FantasyBosses.MOD_ID);
    public static final DeferredHolder<SoundEvent,SoundEvent> RADICION_BGM = TYPES.register("music.radicion.undercurrent",
        () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(FantasyBosses.MOD_ID,"music.radicion.undercurrent")));
}
