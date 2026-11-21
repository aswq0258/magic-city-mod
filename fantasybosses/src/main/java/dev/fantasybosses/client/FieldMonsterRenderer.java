package dev.fantasybosses.client;

import dev.fantasybosses.field.FieldMonster;
import dev.fantasybosses.field.FieldMonsters;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** Elite models get the GeckoLib 4 auto glow layer (<texture>_glowmask.png next to the texture). */
public final class FieldMonsterRenderer extends GeoEntityRenderer<FieldMonster> {
    public FieldMonsterRenderer(EntityRendererProvider.Context context, FieldMonsters.Spec spec) {
        super(context, new FieldMonsterModel(spec.id()));
        this.shadowRadius = Math.min(1.2F, spec.width() * 0.5F);
        if (spec.elite()) addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    /** The models have their own death clip; skip the vanilla sideways flop. */
    @Override
    protected float getDeathMaxRotation(FieldMonster animatable) {
        return 0F;
    }
}
