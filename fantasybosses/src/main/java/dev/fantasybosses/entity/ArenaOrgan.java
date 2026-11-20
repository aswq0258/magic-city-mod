package dev.fantasybosses.entity;

import dev.fantasybosses.arena.ArenaRules;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import java.util.UUID;

/** Physical, attackable stationary encounter target. No loot, no independent AI. */
public final class ArenaOrgan extends PathfinderMob {
    private static final EntityDataAccessor<Integer> SLOT=SynchedEntityData.defineId(ArenaOrgan.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> AGE=SynchedEntityData.defineId(ArenaOrgan.class,EntityDataSerializers.INT);
    private UUID owner;
    private int missingOwnerTicks;
    public ArenaOrgan(EntityType<? extends ArenaOrgan> type,Level level){super(type,level);setNoAi(true);setNoGravity(true);setPersistenceRequired();xpReward=0;}
    public static AttributeSupplier.Builder attributes(){return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH,ArenaRules.ROOT_HEALTH).add(Attributes.MOVEMENT_SPEED,0).add(Attributes.KNOCKBACK_RESISTANCE,1);}
    @Override protected void registerGoals(){}
    @Override protected void defineSynchedData(SynchedEntityData.Builder b){super.defineSynchedData(b);b.define(SLOT,-1);b.define(AGE,0);}
    public void configure(RadicionEntity boss,int slot){owner=boss.getUUID();entityData.set(SLOT,slot);entityData.set(AGE,0);getAttribute(Attributes.MAX_HEALTH).setBaseValue(egg()?ArenaRules.EGG_HEALTH:ArenaRules.ROOT_HEALTH);setHealth(getMaxHealth());setCustomName(Component.literal(egg()?"기생충 알집":"마력 흡수 기관 "+(slot+1)));setCustomNameVisible(true);}
    public int slot(){return entityData.get(SLOT);}
    public boolean egg(){return slot()<0;}
    public int ageTicks(){return entityData.get(AGE);}
    public boolean belongsTo(RadicionEntity boss){return owner!=null&&owner.equals(boss.getUUID());}
    private RadicionEntity boss(ServerLevel world){Entity e=owner==null?null:world.getEntity(owner);return e instanceof RadicionEntity b?b:null;}
    @Override public boolean removeWhenFarAway(double distance){return false;}
    @Override public boolean isPushable(){return false;}
    @Override protected boolean shouldDropLoot(){return false;}
    @Override public void tick(){
        setDeltaMovement(0,0,0);super.tick();
        if(!(level() instanceof ServerLevel world)||!isAlive())return;
        RadicionEntity b=boss(world);
        if(b==null){if(++missingOwnerTicks>=200&&dev.fantasybosses.arena.RadicionArena.chunksReady(world))discard();return;}
        missingOwnerTicks=0;
        if(!b.isAlive()||!b.inRoute()||!b.arena().ownsSlot(this)){discard();return;}
        if(egg()&&!b.routePaused()){
            int age=ageTicks()+1;entityData.set(AGE,age);
            if(age%10==0)world.sendParticles(ParticleTypes.HAPPY_VILLAGER,getX(),getY()+.55,getZ(),6,.25,.3,.25,0);
            if(age>=ArenaRules.HATCH){b.combat().hatchEgg(this);discard();}
        }
    }
    @Override public void die(DamageSource source){
        if(level() instanceof ServerLevel world&&!isRemoved()){
            RadicionEntity b=boss(world);if(b!=null)b.arena().organDestroyed(this);
            world.sendParticles(ParticleTypes.POOF,getX(),getY()+.5,getZ(),24,.4,.5,.4,.04);
        }
        super.die(source);
        if(!level().isClientSide)discard();
    }
    @Override public void addAdditionalSaveData(CompoundTag tag){super.addAdditionalSaveData(tag);if(owner!=null)tag.putUUID("BossOwner",owner);tag.putInt("OrganSlot",slot());tag.putInt("OrganAge",ageTicks());}
    @Override public void readAdditionalSaveData(CompoundTag tag){super.readAdditionalSaveData(tag);owner=tag.hasUUID("BossOwner")?tag.getUUID("BossOwner"):null;entityData.set(SLOT,tag.getInt("OrganSlot"));entityData.set(AGE,Math.max(0,Math.min(ArenaRules.HATCH,tag.getInt("OrganAge"))));}
}
