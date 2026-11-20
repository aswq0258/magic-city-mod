package dev.fantasybosses.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.fantasybosses.entity.ArenaOrgan;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Compact voxel organ model, no temporary placed blocks or display entities. */
public final class ArenaOrganRenderer extends EntityRenderer<ArenaOrgan> {
    private final BlockRenderDispatcher blocks;
    public ArenaOrganRenderer(EntityRendererProvider.Context ctx){super(ctx);blocks=ctx.getBlockRenderDispatcher();shadowRadius=.25F;}
    @Override public ResourceLocation getTextureLocation(ArenaOrgan entity){return TextureAtlas.LOCATION_BLOCKS;}
    private void box(PoseStack p,MultiBufferSource b,int light,Block block,float x,float y,float z,float w,float h,float d){p.pushPose();p.translate(x,y,z);p.scale(w,h,d);blocks.renderSingleBlock(block.defaultBlockState(),p,b,light,OverlayTexture.NO_OVERLAY);p.popPose();}
    @Override public void render(ArenaOrgan e,float yaw,float partial,PoseStack p,MultiBufferSource b,int light){
        if(e.egg()){
            float pulse=1F+.10F*(float)Math.sin((e.ageTicks()+partial)*.5);
            box(p,b,light,Blocks.MOSS_BLOCK,-.40F,0,-.40F,.8F,.38F,.8F);
            box(p,b,15728880,Blocks.VERDANT_FROGLIGHT,-.26F*pulse,.16F,-.26F*pulse,.52F*pulse,.65F,.52F*pulse);
            for(int x:new int[]{-1,1})for(int z:new int[]{-1,1})box(p,b,light,Blocks.BLACKSTONE,x*.30F-.09F,.15F,z*.30F-.09F,.18F,.5F,.18F);
        }else{
            box(p,b,light,Blocks.DARK_OAK_WOOD,-.75F,0,-.75F,1.5F,.32F,1.5F);
            box(p,b,light,Blocks.BLACKSTONE,-.57F,.25F,-.57F,1.14F,.33F,1.14F);
            for(float x:new float[]{-.57F,.35F})for(float z:new float[]{-.57F,.35F})box(p,b,light,Blocks.BLACKSTONE,x,.38F,z,.22F,1.22F,.22F);
            box(p,b,15728880,Blocks.EMERALD_BLOCK,-.31F,.56F,-.31F,.62F,1.05F,.62F);
            box(p,b,15728880,Blocks.VERDANT_FROGLIGHT,-.21F,1.55F,-.21F,.42F,.42F,.42F);
        }
        super.render(e,yaw,partial,p,b,light);
    }
}
