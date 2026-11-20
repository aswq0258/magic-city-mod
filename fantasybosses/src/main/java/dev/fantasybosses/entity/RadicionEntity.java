package dev.fantasybosses.entity;

import dev.fantasybosses.movement.RadicionRoute;
import dev.fantasybosses.arena.RadicionArena;
import dev.fantasybosses.arena.ArenaRules;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.AABB;
import dev.fantasybosses.combat.RadicionCombat;
import dev.fantasybosses.combat.AttackPattern;
import dev.fantasybosses.combat.CombatPoseData;
import dev.fantasybosses.combat.PartDamage;
import net.neoforged.neoforge.entity.PartEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.damagesource.DamageSource;
import dev.fantasybosses.movement.RoutePlayback;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.Animation;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

/** Server-authoritative captured route. Server-authoritative combat and multipart hit zones. */
public final class RadicionEntity extends PathfinderMob implements GeoEntity {
    public static final double BASE_HEALTH = 240.0;
    private static final String CONTROLLER = "body";
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.radicion.idle");
    private static final EntityDataAccessor<Integer> ROUTE_FRAME = SynchedEntityData.defineId(RadicionEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> PREV_FRAME = SynchedEntityData.defineId(RadicionEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ROUTE_CLOCK = SynchedEntityData.defineId(RadicionEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ROUTE_EPOCH = SynchedEntityData.defineId(RadicionEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> COMBAT_POSE = SynchedEntityData.defineId(RadicionEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DEATH_PROGRESS = SynchedEntityData.defineId(RadicionEntity.class,EntityDataSerializers.INT);
    /** v0.8 전장 원점 변환: 원본 녹화 좌표에 더할 이동량 (0 = 원본 왕국 대륙 둥지). 소환 NBT RadicionOffset = [I; dx, dy, dz] */
    private static final EntityDataAccessor<BlockPos> OFFSET = SynchedEntityData.defineId(RadicionEntity.class,EntityDataSerializers.BLOCK_POS);
    public static final String OFFSET_NBT = "RadicionOffset";
    /** 자동 경로 시작을 다시 시도하는 시간 (청크가 늦게 불러질 때) */
    private static final int AUTO_START_TICKS = 600;
    private final RadicionArena arena=new RadicionArena(this);
    private boolean bypassArenaClamp;
    private final RadicionPart[] parts = new RadicionPart[11];
    private static final int[] ARMOR_PART={0,31,45,59,73,87,101,115,133,151,168};
    private final RadicionCombat combat = new RadicionCombat(this);
    private final RoutePlayback playback = new RoutePlayback();
    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    private final ServerBossEvent bossBar = new ServerBossEvent(Component.translatable("entity.fantasybosses.radicion"), BossEvent.BossBarColor.GREEN, BossEvent.BossBarOverlay.PROGRESS);
    private boolean paused, autoAttempted;
    public static final int GROGGY_DURATION = 100; // five seconds at 20 TPS
    private int request, hold, groggyTicks;
    private boolean waitingForChunks;

    public RadicionEntity(EntityType<? extends RadicionEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        noCulling = true;
        xpReward = 0;
        for(int i=0;i<parts.length;i++)parts[i]=new RadicionPart(this,i);
        setId(ENTITY_COUNTER.getAndAdd(parts.length+1)+1);
    }
    @Override public void setId(int id){super.setId(id);if(parts!=null)for(int i=0;i<parts.length;i++)if(parts[i]!=null)parts[i].setId(id+i+1);}
    @Override public void recreateFromPacket(ClientboundAddEntityPacket packet){super.recreateFromPacket(packet);setId(packet.getId());}
    @Override public boolean isMultipartEntity(){return true;}
    @Override public PartEntity<?>[] getParts(){return parts;}
    @Override public boolean isPickable(){return !inRoute()&&super.isPickable();}
    public RadicionArena arena(){return arena;}
    @Override public void setHealth(float health){super.setHealth(arena!=null&&!bypassArenaClamp&&!level().isClientSide?arena.clampHealth(health):health);}
    @Override public boolean hurt(DamageSource source,float amount){
        boolean kill=source.is(DamageTypes.GENERIC_KILL);
        if(!kill&&arena.shield())return false;
        boolean old=bypassArenaClamp;bypassArenaClamp=kill;
        try{return super.hurt(source,amount*PartDamage.groggy(isGroggy())*(arena.roots()>0&&!kill?.6F:1F));}
        finally{bypassArenaClamp=old;}
    }
    public boolean hurtSegment(int segment, DamageSource source,float amount){
        if(!isAlive()||!inRoute())return false;
        return hurt(source,amount*PartDamage.multiplier(segment));
    }
    @Override public void remove(Entity.RemovalReason reason){
        if(reason.shouldDestroy()&&level() instanceof ServerLevel){if(combat!=null)combat.cleanupLarvae();if(arena!=null)arena.cleanup();}
        if(!level().isClientSide){if(bossBar!=null)bossBar.removeAllPlayers();if(arena!=null)arena.detachBar();}
        super.remove(reason);
    }
    // Use the same captured armor-center translations applied by the renderer, including attack poses.
    private void updateParts(){
        if(!inRoute()){for(RadicionPart part:parts)part.place(position());return;}
        if(level().isClientSide){var sample=renderPose(1F);positionParts(sample.from(),sample.to(),sample.alpha(),0F);}
        else positionParts(routeFrame(),routeFrame(),0F,0F);
    }
    public void positionParts(int from,int to,float alpha,float partial){
        if(!RadicionRoute.valid(from)||!RadicionRoute.valid(to))return;
        var a=RadicionRoute.DATA.frames[from];var b=RadicionRoute.DATA.frames[to];
        Vec3 h=a.head().lerp(b.head(),alpha).add(shift());
        int code=combatPose(),frame=alpha<.5F?from:to;
        var pose=AttackPattern.pose(code,partial);
        for(int i=0;i<parts.length;i++){
            int k=ARMOR_PART[i];float[] x=a.parts()[k],y=b.parts()[k];
            double dx=x[0]+(y[0]-x[0])*alpha,dy=x[1]+(y[1]-x[1])*alpha,dz=x[2]+(y[2]-x[2])*alpha;
            if(code!=0&&from<RadicionRoute.GROUND&&to<RadicionRoute.GROUND){
                float[] l=CombatPoseData.get(pose.from(),frame,k),r=CombatPoseData.get(pose.to(),frame,k);float t=pose.alpha();
                dx+=l[0]+(r[0]-l[0])*t;dy+=l[1]+(r[1]-l[1])*t;dz+=l[2]+(r[2]-l[2])*t;
            }
            parts[i].place(h.add(dx,dy-.01,dz));
        }
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ROUTE_FRAME, -1);
        builder.define(PREV_FRAME, -1);
        builder.define(ROUTE_CLOCK, 0);
        builder.define(ROUTE_EPOCH, 0);
        builder.define(COMBAT_POSE, 0);
        builder.define(DEATH_PROGRESS,0);
        builder.define(OFFSET,BlockPos.ZERO);
    }
    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, BASE_HEALTH)
                .add(Attributes.MOVEMENT_SPEED, 0.0).add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }
    @Override protected void registerGoals() {}
    @Override public boolean removeWhenFarAway(double distance) { return false; }
    public int routeFrame() { return entityData.get(ROUTE_FRAME); }
    public int previousRouteFrame() { return entityData.get(PREV_FRAME); }
    public boolean inRoute() { return RadicionRoute.valid(routeFrame()); }
    // ── v0.8 전장 원점 변환: 원본 녹화 좌표(절대) + 이동량
    public BlockPos offset() { return entityData.get(OFFSET); }
    public Vec3 shift() { BlockPos o=offset(); return new Vec3(o.getX(),o.getY(),o.getZ()); }
    public Vec3 head(int frame) { return RadicionRoute.DATA.frames[frame].head().add(shift()); }
    public Vec3 anchor(int frame,int segment) { return RadicionRoute.DATA.frames[frame].anchors()[segment].add(shift()); }
    public Vec3[] anchors(int frame) { Vec3[] a=RadicionRoute.DATA.frames[frame].anchors(),out=new Vec3[a.length];Vec3 s=shift();for(int i=0;i<a.length;i++)out[i]=a[i].add(s);return out; }
    /** 원본 좌표 하나를 이 전장으로 옮김 (기관·균열 등) */
    public Vec3 shifted(Vec3 original) { return original.add(shift()); }
    public AABB bounds() { return RadicionRoute.bounds(offset()); }
    /** 이 보스 전장 안인지 (같은 차원 + 옮긴 상자) */
    public boolean inArena(Level level, Vec3 p) { return level.dimension()==level().dimension() && RadicionRoute.isArena(level,p,offset()); }
    public double floorY() { return dev.fantasybosses.arena.ArenaRules.FLOOR_Y+offset().getY(); }

    public RoutePlayback.Sample renderPose(float partialTick) {
        int current=routeFrame(), previous=previousRouteFrame();
        if(!RadicionRoute.valid(previous)) previous=current;
        return playback.sample(partialTick,previous,current);
    }

    @Override public void tick() {
        if (inRoute()) {
            setNoGravity(true);
            noPhysics = true;
            setDeltaMovement(Vec3.ZERO);
        }
        super.tick();
        if (level().isClientSide) {
            if(inRoute()) {
                int current=routeFrame(), previous=previousRouteFrame();
                playback.tick(entityData.get(ROUTE_EPOCH),entityData.get(ROUTE_CLOCK),RadicionRoute.valid(previous)?previous:current,current);
            } else playback.clear();
        } else {
            if (isAlive()) {
                if (!autoAttempted) {
                    // v0.8: 청크가 늦게 불러지는 전장에서도 시작하도록 30초 동안 다시 시도
                    if (inRoute() || !inArena(level(), position()) || tickCount > AUTO_START_TICKS) autoAttempted = true;
                    else if (tickCount % 10 == 1) startRoute();
                } else if (inRoute()) {
                    if (arena.tick() || combat.tick()) applyRouteFrame(routeFrame(),routeFrame());
                    else stepRoute();
                }
            }
            bossBar.setProgress(Math.max(0F, Math.min(1F, getHealth() / getMaxHealth())));
            bossBar.setColor(isGroggy() ? BossEvent.BossBarColor.YELLOW : arena.shield()?BossEvent.BossBarColor.BLUE:BossEvent.BossBarColor.GREEN);
            bossBar.setName(getDisplayName().copy().append(Component.literal(" [P"+combat.phase()+"]"+(isGroggy()?" [그로기 "+((groggyTicks+19)/20)+"초]":arena.shield()?" [보호막]":arena.waveActive()?" [마력 균열]":combat.busy()?" ["+combat.label()+"]":""))));
            bossBar.setVisible(isAlive());
        }
        updateParts();
    }
    public boolean startRoute() {
        if (level().isClientSide || !isAlive() || !inArena(level(), position()) || !RadicionRoute.loaded(level(),0,offset())) return false;
        if(level() instanceof ServerLevel world && !world.getEntitiesOfClass(RadicionEntity.class,bounds(),e->e!=this&&e.isAlive()&&e.inRoute()).isEmpty())return false;
        combat.reset(true);
        autoAttempted = true; paused = false; request = 0; hold = 0; groggyTicks=0; waitingForChunks = false;
        setNoGravity(true); noPhysics = true; setDeltaMovement(Vec3.ZERO);
        entityData.set(PREV_FRAME, 0); entityData.set(ROUTE_FRAME, 0);
        entityData.set(ROUTE_EPOCH,entityData.get(ROUTE_EPOCH)+1);
        entityData.set(ROUTE_CLOCK,0);
        Vec3 p = head(0);
        setPos(p.x,p.y,p.z);
        arena.start();
        return true;
    }
    public boolean requestAir(boolean dive) {
        if (!isAlive() || !inRoute() || routeFrame() >= RadicionRoute.AIR || paused || request != 0 || groggyTicks>0 || hold>0 || combat.busy() || arena.waveActive()) return false;
        request = dive ? 2 : 1;
        return true;
    }
    public boolean pauseRoute(boolean value) {
        if (!inRoute() || !isAlive()) return false;
        paused = value;
        return true;
    }
    public boolean isGroggy() { return isAlive() && inRoute() && groggyTicks>0; }
    public RadicionCombat combat(){return combat;}
    public boolean routePaused(){return paused;}
    public int routeHold(){return hold;}
    public void setRouteHold(int ticks){hold=Math.max(0,ticks);}
    public boolean availableGround(){return !level().isClientSide && isAlive() && inRoute() && routeFrame()<RadicionRoute.AIR && !paused && request==0 && hold==0 && groggyTicks==0 && !arena.waveActive();}
    public int combatPose(){return entityData.get(COMBAT_POSE);}
    public void setCombatPose(int code){if(!level().isClientSide)entityData.set(COMBAT_POSE,code);}
    public boolean beginGroggy(){return beginGroggy(GROGGY_DURATION);}
    public boolean beginGroggy(int ticks){
        if(!availableGround()||combat.busy())return false;
        groggyTicks=Math.max(1,Math.min(GROGGY_DURATION,ticks));return true;
    }
    @Override public void die(DamageSource source){
        if(!level().isClientSide){combat.reset(true);arena.cleanup();}
        super.die(source);
    }
    public int deathProgress(){return entityData.get(DEATH_PROGRESS);}
    @Override protected void tickDeath(){
        if(!inRoute()){super.tickDeath();return;}
        // Keep captured route fixed. No new attacks; synchronize segment removal to every client.
        deathTime++;
        if(level() instanceof ServerLevel world){
            entityData.set(DEATH_PROGRESS,deathTime);
            if(deathTime<=41&&(deathTime-1)%4==0){int segment=10-(deathTime-1)/4;Vec3 p=anchor(routeFrame(),segment);
                world.sendParticles(ParticleTypes.HAPPY_VILLAGER,p.x,p.y,p.z,24,.55,.4,.55,.01);
                world.sendParticles(ParticleTypes.POOF,p.x,p.y,p.z,18,.45,.35,.45,.04);
            }
            if(deathTime>=ArenaRules.DEATH_DURATION){arena.announce("세계수 잠식자 라디키온 격파!");remove(Entity.RemovalReason.KILLED);}
        }
    }
    public String routeStatus() {
        return "route="+RadicionRoute.phase(routeFrame())+" frame="+routeFrame()+" paused="+paused+" queued="+request+" hold="+hold+" groggyTicks="+groggyTicks+" waitingChunks="+waitingForChunks;
    }
    private void stepRoute() {
        int current = routeFrame();
        if (!RadicionRoute.validDimension(level(),offset())) {
            entityData.set(ROUTE_FRAME,-1); entityData.set(PREV_FRAME,-1);
            setNoGravity(false); noPhysics=false; request=0; hold=0; groggyTicks=0; combat.reset(true); arena.cleanup(); return;
        }
        if (groggyTicks>0) {
            if (!paused) groggyTicks--;
            applyRouteFrame(current,current);
            return;
        }
        if (paused || hold > 0) {
            if (!paused) hold--;
            applyRouteFrame(current,current);
            return;
        }
        int next;
        boolean beginDiveHold=false, landed=false;
        if (current < RadicionRoute.AIR) {
            // Both connections share the exact eleven segment anchors.
            next = (current + 1) % RadicionRoute.GROUND;
            if (request != 0 && (current == 0 || next == 0)) next=RadicionRoute.AIR_JOIN;
        } else if (current < RadicionRoute.DIVE) {
            if (request == 2 && current == RadicionRoute.DIVE_JOIN) next=RadicionRoute.DIVE;
            else {
                int advance=combat.fastDescent(current)?Math.min(2,RadicionRoute.DIVE-current):1;
                next = RadicionRoute.AIR+(current-RadicionRoute.AIR+advance)%RadicionRoute.AIR_COUNT;
                if (next == RadicionRoute.DIVE_JOIN && request == 2) beginDiveHold=true;
                if (next == RadicionRoute.AIR_JOIN) { next=0; landed=true; }
            }
        } else {
            next=current+1;
            if (next >= RadicionRoute.COUNT) { next=RadicionRoute.DIVE_LAND; landed=true; }
        }
        if (!RadicionRoute.loaded(level(),next,offset())) { waitingForChunks=true; applyRouteFrame(current,current); return; }
        waitingForChunks=false;
        applyRouteFrame(current,next);
        if (beginDiveHold) hold=combat.busy()?AttackPattern.DIVE.windup:30;
        if (landed) { request=0; hold=0; combat.onLanded(); beginGroggy(); }
        else combat.onRouteFrame(next);
    }
    private void applyRouteFrame(int previous,int next) {
        Vec3 p=head(next);
        setDeltaMovement(Vec3.ZERO);
        setPos(p.x,p.y,p.z);
        entityData.set(PREV_FRAME,previous); entityData.set(ROUTE_FRAME,next);
        entityData.set(ROUTE_CLOCK,entityData.get(ROUTE_CLOCK)+1);
    }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        BlockPos o=offset();tag.putIntArray(OFFSET_NBT,new int[]{o.getX(),o.getY(),o.getZ()});
        tag.putInt("RadicionRouteLayout",3); tag.putInt("RadicionRouteFrame",routeFrame()); tag.putBoolean("RadicionPaused",paused);
        tag.putInt("RadicionGroggy",groggyTicks); tag.putInt("RadicionRequest",request); tag.putInt("RadicionHold",hold); tag.putBoolean("RadicionAutoAttempted",autoAttempted);
        combat.save(tag);
        arena.save(tag);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        int[] o=dev.fantasybosses.arena.ArenaRules.offset(tag.contains(OFFSET_NBT)?tag.getIntArray(OFFSET_NBT):null);
        entityData.set(OFFSET,new BlockPos(o[0],o[1],o[2]));
        int frame=tag.contains("RadicionRouteFrame") ? tag.getInt("RadicionRouteFrame") : -1;
        if (tag.getInt("RadicionRouteLayout")!=3) frame=RadicionRoute.remapLegacyFrame(frame);
        if (!RadicionRoute.valid(frame)) frame=-1;
        if (!RadicionRoute.validDimension(level(),offset())) frame=-1;
        entityData.set(ROUTE_FRAME,frame); entityData.set(PREV_FRAME,frame);
        paused=tag.getBoolean("RadicionPaused"); request=Math.max(0,Math.min(2,tag.getInt("RadicionRequest")));
        hold=Math.max(0,Math.min(40,tag.getInt("RadicionHold"))); autoAttempted=tag.getBoolean("RadicionAutoAttempted") || frame>=0;
        groggyTicks=frame>=0 && frame<RadicionRoute.AIR ? Math.max(0,Math.min(GROGGY_DURATION,tag.getInt("RadicionGroggy"))) : 0;
        if (frame>=0) { setNoGravity(true); noPhysics=true; }
        combat.load(tag);
        arena.load(tag);
    }
    @Override public void startSeenByPlayer(ServerPlayer player) { super.startSeenByPlayer(player); bossBar.addPlayer(player); }
    @Override public void stopSeenByPlayer(ServerPlayer player) { super.stopSeenByPlayer(player); bossBar.removePlayer(player); }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        AnimationController<RadicionEntity> controller=new AnimationController<>(this,CONTROLLER,2,
                state -> inRoute() ? PlayState.STOP : state.setAndContinue(IDLE));
        for (PreviewClip clip:PreviewClip.values()) controller.triggerableAnim(clip.key(),RawAnimation.begin().then(clip.animationId(),Animation.LoopType.PLAY_ONCE));
        controllers.add(controller);
    }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return animationCache; }
    public void preview(PreviewClip clip) {
        if (!level().isClientSide && isAlive() && !inRoute()) triggerAnim(CONTROLLER,clip.key());
    }
}
