package dev.fantasybosses.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.fantasybosses.entity.RadicionEntity;
import dev.fantasybosses.arena.ArenaRules;
import dev.fantasybosses.combat.AttackPattern;
import dev.fantasybosses.combat.CombatPoseData;
import dev.fantasybosses.movement.RadicionRoute;
import dev.fantasybosses.movement.RoutePlayback;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Route geometry is flat, with centered cuboids. Use the captured quaternion directly. */
public final class RadicionRenderer extends GeoEntityRenderer<RadicionEntity> {
    private final Map<String,Integer> routeParts = new HashMap<>();
    private static final Set<String> GLOW_PARTS=Set.of(
            "rad_s0_eyem1_0",
            "rad_s0_eyem1_1",
            "rad_s0_eyem1_2",
            "rad_s0_eye1_0",
            "rad_s0_eye1_1",
            "rad_s0_eye1_2",
            "rad_s0_sucker_tip",
            "rad_s1_veinm1",
            "rad_s1_vein1",
            "rad_s2_veinm1",
            "rad_s2_vein1",
            "rad_s3_veinm1",
            "rad_s3_vein1",
            "rad_s4_veinm1",
            "rad_s4_vein1",
            "rad_s5_veinm1",
            "rad_s5_vein1",
            "rad_s6_veinm1",
            "rad_s6_vein1",
            "rad_s7_veinm1",
            "rad_s7_vein1",
            "rad_s7_reservoir",
            "rad_s8_veinm1",
            "rad_s8_vein1",
            "rad_s8_reservoir",
            "rad_s9_veinm1",
            "rad_s9_vein1",
            "rad_s9_reservoir",
            "rad_s10_tailm1_0tip",
            "rad_s10_tailm1_1tip",
            "rad_s10_tail1_0tip",
            "rad_s10_tail1_1tip");
    private final Quaternionf[] rotations = new Quaternionf[RadicionRoute.PARTS];
    private final float[][] transforms = new float[RadicionRoute.PARTS][6];
    private final boolean[] directedLeg = new boolean[RadicionRoute.PARTS];
    private final Quaternionf symmetry = new Quaternionf();
    private record Leg(int upper,int lower,int hook,int armor,float upperLength,float lowerLength) {}
    private static final Leg[] LEGS = {
            new Leg(38,39,40,31,0.604625000F,0.742593125F),
            new Leg(41,42,43,31,0.613593125F,0.532330625F),
            new Leg(52,53,54,45,0.604211875F,0.698671250F),
            new Leg(55,56,57,45,0.601546875F,0.496661875F),
            new Leg(66,67,68,59,0.621217500F,0.726301875F),
            new Leg(69,70,71,59,0.609012500F,0.758730625F),
            new Leg(80,81,82,73,0.623176250F,0.643410625F),
            new Leg(83,84,85,73,0.610252500F,0.584332500F),
            new Leg(94,95,96,87,0.606083125F,0.474005000F),
            new Leg(97,98,99,87,0.601702500F,0.824884375F),
            new Leg(108,109,110,101,0.630059375F,0.583796250F),
            new Leg(111,112,113,101,0.637302500F,0.311953750F),
            new Leg(126,127,128,115,0.664862500F,0.639745625F),
            new Leg(129,130,131,115,0.677525625F,0.899751875F),
            new Leg(144,145,146,133,0.686768125F,0.659673125F),
            new Leg(147,148,149,133,0.695795625F,0.762060625F),
            new Leg(162,163,164,151,0.711378750F,0.611041250F),
            new Leg(165,166,167,151,0.710626875F,0.705035000F)
    };
    // Reuse scratch objects across basic and emissive render passes; no per-leg allocations.
    private final Vector3f knee=new Vector3f(), normal=new Vector3f(), dir=new Vector3f(), originalDir=new Vector3f(),
            oldFoot=new Vector3f(), newFoot=new Vector3f(), hookOffset=new Vector3f(), tmp=new Vector3f();
    private final Quaternionf correction=new Quaternionf();
    private RoutePlayback.Sample cachedSample;
    private RadicionEntity cachedEntity;
    private int cachedAttack=-1;
    private float cachedPartial=-1;
    private final Quaternionf attackA=new Quaternionf(), attackB=new Quaternionf();
    private final Quaternionf rotation = new Quaternionf();
    private final Quaternionf target = new Quaternionf();
    private final Quaternionf candidate = new Quaternionf();
    public RadicionRenderer(EntityRendererProvider.Context context) {
        super(context, new RadicionModel());
        CombatPoseData.preload();
        this.shadowRadius = 1.0F;
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
        String[] names = RadicionRoute.DATA.names;
        for(int i=0;i<names.length;i++) {
            routeParts.put(names[i],i);
            rotations[i]=new Quaternionf();
            directedLeg[i]=names[i].contains("_leg") && (names[i].endsWith("_lower") || names[i].endsWith("_upper"));
        }
    }
    private void preparePose(RadicionEntity entity, RoutePlayback.Sample sample,float partialTick) {
        int code=entity.combatPose();
        if(cachedEntity==entity && sample.equals(cachedSample) && code==cachedAttack && (code==0||cachedPartial==partialTick)) return;
        cachedEntity=entity; cachedSample=sample; cachedAttack=code;cachedPartial=partialTick;
        float t=sample.alpha();
        float[][] from=RadicionRoute.DATA.frames[sample.from()].parts();
        float[][] to=RadicionRoute.DATA.frames[sample.to()].parts();
        for(int i=0;i<from.length;i++) {
            float[] a=from[i], b=to[i], out=transforms[i];
            rotation.set(a[3],a[4],a[5],a[6]); target.set(b[3],b[4],b[5],b[6]);
            if(directedLeg[i]) {
                // Only roll around the long axis is equivalent. Flipping that axis reverses knee/foot.
                symmetry.set(0,0,1,0);
                candidate.set(target).mul(symmetry);
                if(Math.abs(rotation.dot(candidate))>Math.abs(rotation.dot(target))) target.set(candidate);
            }
            rotations[i].set(rotation).slerp(target,t).normalize();
            for(int j=0;j<3;j++) {
                out[j]=Mth.lerp(t,a[j],b[j]);
                out[j+3]=Mth.lerp(t,a[j+7],b[j+7]);
            }
        }
        if(code!=0 && sample.from()<RadicionRoute.GROUND && sample.to()<RadicionRoute.GROUND){
            // Ground attacks freeze the route; nearest captured corrected baseline is used.
            int frame=sample.alpha()<.5F?sample.from():sample.to();
            var attack=AttackPattern.pose(code,partialTick);
            for(int i=0;i<transforms.length;i++){
                float[] a=CombatPoseData.get(attack.from(),frame,i),b=CombatPoseData.get(attack.to(),frame,i),p=transforms[i];
                float k=attack.alpha();
                for(int j=0;j<3;j++){p[j]+=Mth.lerp(k,a[j],b[j]);p[j+3]*=Mth.lerp(k,a[j+7],b[j+7]);}
                attackA.set(a[3],a[4],a[5],a[6]);attackB.set(b[3],b[4],b[5],b[6]);
                rotations[i].premul(attackA.slerp(attackB,k).normalize()).normalize();
            }
        }
        // Interpolating centers independently can separate the knee or turn the foot up.
        // Rebuild the shared knee and constrain the lower axis against each segment's own surface normal.
        for(Leg leg: LEGS) alignLeg(leg);
    }
    private void alignLeg(Leg l) {
        float[] upper=transforms[l.upper()], lower=transforms[l.lower()], hook=transforms[l.hook()];
        float halfUpper=l.upperLength()*upper[5]*0.5F, halfLower=l.lowerLength()*lower[5]*0.5F;
        rotations[l.upper()].transform(knee.set(0,0,halfUpper));
        knee.add(upper[0],upper[1],upper[2]);
        rotations[l.armor()].transform(normal.set(0,1,0)).normalize();
        rotations[l.lower()].transform(originalDir.set(0,0,1)).normalize();
        dir.set(originalDir);
        oldFoot.set(lower[0],lower[1],lower[2]).fma(halfLower,originalDir);
        float d=dir.dot(normal);
        if(d> -0.22F) {
            dir.fma(-d,normal);
            if(dir.lengthSquared()<1.0e-8F) rotations[l.armor()].transform(dir.set(1,0,0));
            dir.normalize().mul((float)Math.sqrt(1.0-0.22*0.22)).fma(-0.22F,normal).normalize();
        }
        correction.identity().rotationTo(originalDir,dir);
        rotations[l.lower()].premul(correction).normalize();
        newFoot.set(knee).fma(2*halfLower,dir);
        tmp.set(knee).fma(halfLower,dir);
        lower[0]=tmp.x;lower[1]=tmp.y;lower[2]=tmp.z;
        hookOffset.set(hook[0],hook[1],hook[2]).sub(oldFoot);
        correction.transform(hookOffset).add(newFoot);
        hook[0]=hookOffset.x;hook[1]=hookOffset.y;hook[2]=hookOffset.z;
        rotations[l.hook()].premul(correction).normalize();
    }
    // Vanilla entity position packets have their own interpolation. Render the body at the exact
    // interpolated captured head position instead of mixing that interpolation with the pose clock.
    @Override public Vec3 getRenderOffset(RadicionEntity entity,float partialTick) {
        if (!entity.inRoute()) return super.getRenderOffset(entity,partialTick);
        float t=Mth.clamp(partialTick,0,1);
        RoutePlayback.Sample sample=entity.renderPose(t);
        preparePose(entity,sample,partialTick);
        entity.positionParts(sample.from(),sample.to(),sample.alpha(),partialTick);
        Vec3 a=entity.head(sample.from());
        Vec3 b=entity.head(sample.to());
        Vec3 renderPosition=new Vec3(Mth.lerp((double)t,entity.xOld,entity.getX()),
                Mth.lerp((double)t,entity.yOld,entity.getY()),Mth.lerp((double)t,entity.zOld,entity.getZ()));
        return a.lerp(b,sample.alpha()).subtract(renderPosition);
    }
    // All route orientations are already in world axes. Do not turn the body a second time.
    @Override protected void applyRotations(RadicionEntity entity,PoseStack poseStack,float ageInTicks,
              float rotationYaw,float partialTick,float nativeScale) {
        if (!entity.inRoute()) super.applyRotations(entity,poseStack,ageInTicks,rotationYaw,partialTick,nativeScale);
    }
    @Override public void renderRecursively(PoseStack poseStack,RadicionEntity entity,GeoBone bone,
              RenderType renderType,MultiBufferSource bufferSource,VertexConsumer buffer,boolean isReRender,
              float partialTick,int packedLight,int packedOverlay,int colour) {
        if (!entity.inRoute()) {
            super.renderRecursively(poseStack,entity,bone,renderType,bufferSource,buffer,isReRender,partialTick,packedLight,packedOverlay,colour);
            return;
        }
        // Reset the route-only flat bones, including the root. Prevent old preview animation state
        // and GeckoLib's Euler transforms from accumulating on the captured world poses.
        bone.updatePosition(0,0,0);bone.updateRotation(0,0,0);bone.updateScale(1,1,1);bone.setHidden(false);
        Integer index=routeParts.get(bone.getName());
        if (index==null) {
            if(bone.getName().equals("root") && cachedEntity!=entity) preparePose(entity,entity.renderPose(partialTick),partialTick);
            super.renderRecursively(poseStack,entity,bone,renderType,bufferSource,buffer,isReRender,partialTick,packedLight,packedOverlay,colour);
            return;
        }
        // Only the existing AutoGlowingGeoLayer uses reRender in this renderer.
        // Non-emissive leaf cubes have no visible pixels in its mask.
        if(isReRender && !GLOW_PARTS.contains(bone.getName())) return;
        int visible=ArenaRules.segmentVisibleCount(entity.deathProgress());
        // Flat route leaves are sorted by segment. Skip both normal and emissive passes.
        int segment=index>=168?10:index>=150?9:index>=132?8:index>=114?7:index>=100?6:index>=86?5:index>=72?4:index>=58?3:index>=44?2:index>=30?1:0;
        if(segment>=visible)return;
        if(cachedEntity!=entity || cachedSample==null) preparePose(entity,entity.renderPose(partialTick),partialTick);
        float[] pose=transforms[index];
        poseStack.pushPose();
        try {
            poseStack.translate(pose[0],pose[1]-0.01F,pose[2]);
            poseStack.mulPose(rotations[index]);
            poseStack.scale(pose[3],pose[4],pose[5]);
            super.renderRecursively(poseStack,entity,bone,renderType,bufferSource,buffer,isReRender,partialTick,packedLight,packedOverlay,colour);
        } finally { poseStack.popPose(); }
    }
}
