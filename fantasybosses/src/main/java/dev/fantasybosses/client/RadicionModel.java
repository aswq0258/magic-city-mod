package dev.fantasybosses.client;

import dev.fantasybosses.FantasyBosses;
import dev.fantasybosses.entity.RadicionEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public final class RadicionModel extends GeoModel<RadicionEntity> {
    private static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath(FantasyBosses.MOD_ID, "geo/radicion.geo.json");
    private static final ResourceLocation ROUTE_MODEL = ResourceLocation.fromNamespaceAndPath(FantasyBosses.MOD_ID, "geo/radicion_route.geo.json");
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(FantasyBosses.MOD_ID, "textures/entity/radicion.png");
    private static final ResourceLocation ANIMATION = ResourceLocation.fromNamespaceAndPath(FantasyBosses.MOD_ID, "animations/radicion.animation.json");
    @Override public ResourceLocation getModelResource(RadicionEntity entity) { return entity.inRoute() ? ROUTE_MODEL : MODEL; }
    @Override public ResourceLocation getTextureResource(RadicionEntity entity) { return TEXTURE; }
    @Override public ResourceLocation getAnimationResource(RadicionEntity entity) { return ANIMATION; }
}
