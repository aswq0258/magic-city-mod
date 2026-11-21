package dev.fantasybosses.client;

import dev.fantasybosses.FantasyBosses;
import dev.fantasybosses.field.FieldMonster;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/** Paths follow the model pack: geo/field, textures/entity/field, animations/field. */
public final class FieldMonsterModel extends GeoModel<FieldMonster> {
    private final ResourceLocation model;
    private final ResourceLocation texture;
    private final ResourceLocation animation;

    public FieldMonsterModel(String id) {
        model = ResourceLocation.fromNamespaceAndPath(FantasyBosses.MOD_ID, "geo/field/" + id + ".geo.json");
        texture = ResourceLocation.fromNamespaceAndPath(FantasyBosses.MOD_ID, "textures/entity/field/" + id + ".png");
        animation = ResourceLocation.fromNamespaceAndPath(FantasyBosses.MOD_ID, "animations/field/" + id + ".animation.json");
    }

    @Override public ResourceLocation getModelResource(FieldMonster entity) { return model; }
    @Override public ResourceLocation getTextureResource(FieldMonster entity) { return texture; }
    @Override public ResourceLocation getAnimationResource(FieldMonster entity) { return animation; }
}
