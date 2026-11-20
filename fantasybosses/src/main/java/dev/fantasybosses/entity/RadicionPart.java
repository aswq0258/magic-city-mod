package dev.fantasybosses.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.PartEntity;

/** One physical segment; no independent entity registration, saving or rendering. */
public final class RadicionPart extends PartEntity<RadicionEntity> {
    private final EntityDimensions size;
    private final int segment;
    public RadicionPart(RadicionEntity parent,int index){
        super(parent);segment=index;
        size=EntityDimensions.fixed(index==0?1.9F:index==10?1.35F:1.6F,index==0?1.5F:1.2F);
        refreshDimensions();noPhysics=true;
    }
    public int segment(){return segment;}
    @Override public EntityDimensions getDimensions(Pose pose){return size==null?super.getDimensions(pose):size;}
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder){}
    @Override protected void readAdditionalSaveData(CompoundTag tag){}
    @Override protected void addAdditionalSaveData(CompoundTag tag){}
    @Override public boolean shouldBeSaved(){return false;}
    @Override public boolean isPickable(){return getParent().isAlive()&&getParent().inRoute();}
    @Override public boolean is(Entity other){return other==this||other==getParent();}
    @Override public boolean hurt(DamageSource source,float amount){return isPickable()&&!isInvulnerableTo(source)&&getParent().hurtSegment(segment,source,amount);}
    public void place(Vec3 center){
        setPos(center.x,center.y-getBbHeight()*.5,center.z);
        xo=xOld=getX();yo=yOld=getY();zo=zOld=getZ();
    }
}
