package dev.fantasybosses.combat;

import dev.fantasybosses.entity.RadicionEntity;
import dev.fantasybosses.entity.ArenaOrgan;
import dev.fantasybosses.BossEntities;
import dev.fantasybosses.arena.ArenaRules;
import net.minecraft.world.entity.Mob;
import dev.fantasybosses.movement.RadicionRoute;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import java.util.UUID;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Endermite;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Single authoritative attack per boss. Warnings are fixed in world space; damage is server-only. */
public final class RadicionCombat {
    public static final int WALL_WARN=RadicionRoute.AIR+225;
    public static final int DIVE_HIT=RadicionRoute.DIVE+23;
    private final RadicionEntity boss;
    private AttackPattern active;
    private final List<Vec3> zones=new ArrayList<>();
    private int elapsed, phaseAtStart=1, reachedPhase=1, cooldown=30, groundTicks, groundSequence, airSequence;
    private boolean automatic=true, impacted, warned;
    public RadicionCombat(RadicionEntity boss){this.boss=boss;}
    public boolean busy(){return active!=null;}
    public boolean auto(){return automatic;}
    public void setAuto(boolean value){automatic=value;}
    public int phase(){if(boss.arena().roots()>0)return 1;return Math.max(reachedPhase,AttackPattern.phase(boss.getHealth(),boss.getMaxHealth()));}
    public void updatePhase(){int p=phase();if(p>reachedPhase){reachedPhase=p;announce(p==2?"기생체 각성 — 추가 공격 해금!":"마력 폭주 — 낙하 범위 증가!");}}
    public String status(){return "phase="+phase()+" attack="+(active==null?"none":active.key)+" elapsed="+elapsed+" auto="+automatic+" groundTicks="+groundTicks+" cooldown="+cooldown+" larvae="+larvaCount()+" lastSpawned="+lastSpawned+" "+boss.arena().status();}
    public String label(){return active==null?"":active.label;}
    private ServerLevel world(){return (ServerLevel)boss.level();}
    private boolean eligible(ServerPlayer p){return p.isAlive()&&!p.isSpectator()&&!p.isCreative()&&boss.inArena(p.level(),p.position());}
    private List<ServerPlayer> participants(){return world().players().stream().filter(this::eligible).toList();}
    private void announce(String text){for(ServerPlayer p:world().players())if(!p.isSpectator()&&boss.inArena(p.level(),p.position()))p.displayClientMessage(Component.literal(text),true);}
    private Vec3 floor(Vec3 p){return new Vec3(p.x,boss.floorY(),p.z);}
    private Vec3[] anchors(){return boss.anchors(boss.routeFrame());}
    private Vec3 front(double distance){Vec3[] a=anchors();Vec3 v=a[0].subtract(a[1]);return floor(a[0].add(new Vec3(v.x,0,v.z).normalize().scale(distance)));}
    private boolean near(List<ServerPlayer> players,Vec3 point,double radius){return players.stream().anyMatch(p->horizontalDistance(p.position(),point)<=radius*radius&&Math.abs(p.getY()-point.y)<3);}
    private static double horizontalDistance(Vec3 a,Vec3 b){double x=a.x-b.x,z=a.z-b.z;return x*x+z*z;}
    /** Returns true while a ground attack owns the movement clock. */
    public boolean tick(){
        if(!boss.inRoute()||!boss.isAlive())return false;
        updatePhase();
        if(boss.tickCount%20==0)for(Endermite e:larvae())if(!boss.inArena(e.level(),e.position()))e.discard();
        if(boss.routePaused())return busy()&&active.ground();
        if(active!=null){
            if(active.ground()){
                elapsed++;
                boss.setCombatPose(active.poseCode(elapsed));
                if(!impacted&&elapsed%5==0)warning();
                if(elapsed==active.windup)impact();
                if(elapsed>=active.end){int ticks=active.recovery;finish();boss.beginGroggy(ticks);}
                return true;
            }
            // Air sequence is synchronized to actual route frames, not wall-clock time.
            if(warned&&!impacted&&boss.tickCount%5==0)warning();
            if(active==AttackPattern.BROOD&&warned&&!impacted&&boss.routeHold()==1)impact();
            return false;
        }
        if(!automatic||!boss.availableGround())return false;
        List<ServerPlayer> players=participants();if(players.isEmpty())return false;
        groundTicks++;if(cooldown>0)cooldown--;
        int phase=phase();
        if(groundTicks>=AttackPattern.airInterval(phase)){
            AttackPattern p=phase==1?AttackPattern.DIVE:switch(airSequence%3){case 0->AttackPattern.DIVE;case 1->AttackPattern.BROOD;default->AttackPattern.DESCENT;};
            if(start(p)){airSequence++;return false;}
        }
        if(cooldown>0)return false;
        Vec3[] a=anchors();
        if(phase>=2 && near(players,floor(a[9]),6.0) && (!near(players,front(1.5),5.5)||groundSequence%3==2)){
            if(start(AttackPattern.TAIL)){groundSequence++;return true;}
        }
        if(near(players,front(1.5),5.5)){
            AttackPattern p=groundSequence%2==0?AttackPattern.BITE:AttackPattern.DRAIN;
            if(start(p)){groundSequence++;return true;}
        }
        return false;
    }
    public boolean start(AttackPattern p){
        if(p==null||busy()||!boss.availableGround()||phase()<p.minPhase)return false;
        if(!p.ground()&&!boss.requestAir(p==AttackPattern.DIVE))return false;
        active=p;elapsed=0;phaseAtStart=phase();impacted=false;warned=false;zones.clear();
        if(p.ground()){
            if(p==AttackPattern.TAIL){for(int i:new int[]{7,9,10})zones.add(floor(anchors()[i]));}
            else zones.add(front(p==AttackPattern.BITE?1.5:1.0));
            boss.setCombatPose(p.poseCode(0));prepareWarning();
        }else{groundTicks=0;announce("라디키온: "+p.label+" 준비");}
        return true;
    }
    public void onRouteFrame(int next){
        if(active==null||active.ground())return;
        if(active==AttackPattern.DIVE && next==RadicionRoute.DIVE_JOIN&&!warned){zones.add(floor(boss.head(DIVE_HIT)));prepareWarning();}
        if(active==AttackPattern.DIVE && next==DIVE_HIT&&!impacted)impact();
        if(active==AttackPattern.BROOD && next==RadicionRoute.DIVE_JOIN&&!warned){
            List<ServerPlayer> players=new ArrayList<>(participants());Collections.shuffle(players);
            for(ServerPlayer p:players){if(zones.size()>= (phaseAtStart==3?3:2))break;zones.add(floor(p.position()));}
            // Creative operators can see the telegraph/visual preview without taking damage.
            if(zones.isEmpty())for(ServerPlayer p:world().players())if(!p.isSpectator()&&boss.inArena(p.level(),p.position())){zones.add(floor(p.position()));break;}
            boss.setRouteHold(active.windup);prepareWarning();
        }
        if(active==AttackPattern.DESCENT&&next==WALL_WARN&&!warned){zones.add(floor(boss.head(RadicionRoute.AIR)));boss.setRouteHold(active.windup);prepareWarning();}
        if(active==AttackPattern.DESCENT&&next==RadicionRoute.AIR&&!impacted)impact();
    }
    public boolean fastDescent(int current){return active==AttackPattern.DESCENT && warned && !impacted && current>=WALL_WARN && current<RadicionRoute.DIVE;}
    public void onLanded(){if(active!=null&&!active.ground())finish();}
    private void prepareWarning(){warned=true;announce(active.label+" — 경고 범위에서 벗어나세요!");boss.playSound(SoundEvents.RAVAGER_ROAR,2.0F,.8F);warning();}
    private void warning(){
        int r=active.radius(phaseAtStart);
        for(Vec3 p:zones)for(int j=0;j<Math.max(24,r*8);j++){
            double a=j*2*Math.PI/Math.max(24,r*8);
            world().sendParticles(ParticleTypes.FLAME,p.x+r*Math.cos(a),p.y+.05,p.z+r*Math.sin(a),1,0,0,0,0);
        }
    }
    private void impact(){
        if(impacted||active==null)return;impacted=true;
        if(active==AttackPattern.BROOD){spawnEggs();zones.clear();return;}
        boolean heavy=active==AttackPattern.DIVE||active==AttackPattern.DESCENT;
        int radius=active.radius(phaseAtStart),hits=0;
        for(ServerPlayer p:participants()){
            boolean hit=false;for(Vec3 center:zones)if(horizontalDistance(p.position(),center)<=radius*radius&&Math.abs(p.getY()-center.y)<=3){hit=true;break;}
            if(!hit)continue;
            var source=active==AttackPattern.BITE||active==AttackPattern.TAIL?boss.damageSources().mobAttack(boss):boss.damageSources().indirectMagic(boss,boss);
            if(p.hurt(source,active.damage(phaseAtStart))){hits++;if(active==AttackPattern.DRAIN)p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,40,0));}
        }
        if(active==AttackPattern.DRAIN&&hits>0){boss.heal(Math.min(12,6*hits));world().sendParticles(ParticleTypes.HAPPY_VILLAGER,boss.getX(),boss.getY()+1,boss.getZ(),24,.8,.5,.8,0);announce("생명력 흡수 성공!");}
        for(Vec3 p:zones){
            if(heavy){world().sendParticles(ParticleTypes.EXPLOSION_EMITTER,p.x,p.y+.6,p.z,1,0,0,0,0);world().sendParticles(ParticleTypes.CLOUD,p.x,p.y+.4,p.z,40,1.8,.35,1.8,.10);world().sendParticles(new BlockParticleOption(ParticleTypes.BLOCK,Blocks.ROOTED_DIRT.defaultBlockState()),p.x,p.y+.3,p.z,50,1.8,.35,1.8,.12);world().playSound(null,p.x,p.y,p.z,SoundEvents.DEEPSLATE_BREAK,SoundSource.HOSTILE,3F,.6F);world().playSound(null,p.x,p.y,p.z,SoundEvents.GENERIC_EXPLODE.value(),SoundSource.HOSTILE,3.0F,.65F);}
            else world().sendParticles(active==AttackPattern.DRAIN?ParticleTypes.HAPPY_VILLAGER:ParticleTypes.SWEEP_ATTACK,p.x,p.y+.8,p.z,8,.7,.15,.7,0);
        }
        boss.playSound(heavy?SoundEvents.RAVAGER_ROAR:SoundEvents.RAVAGER_ATTACK,2.5F,.8F);zones.clear();
    }
    private static final String OWNER_PREFIX="fantasybosses.radicion.";
    private String ownerTag(){return OWNER_PREFIX+boss.getUUID();}
    private List<Endermite> larvae(){return world().getEntitiesOfClass(Endermite.class,boss.bounds().inflate(24),e->e.getTags().contains(ownerTag())&&e.isAlive());}
    private int lastSpawned;
    public int lastSpawned(){return lastSpawned;}
    public int larvaCount(){return boss.level() instanceof ServerLevel?larvae().size():0;}
    /** Search collision-supported positions around each warning, without editing arena blocks. */
    private boolean placeLarva(Mob mite,Vec3 p){
        int[] dy={0,1,-1,2,-2,3,-3};
        List<Endermite> existing=larvae();
        List<ArenaOrgan> organs=boss.arena().organs();
        for(int ring=0;ring<=2;ring++)for(int dx=-ring;dx<=ring;dx++)for(int dz=-ring;dz<=ring;dz++){
            if(Math.max(Math.abs(dx),Math.abs(dz))!=ring)continue;
            for(int y:dy)for(double frac:new double[]{.01,.26,.51,.76}){
                Vec3 v=new Vec3(Math.floor(p.x)+dx+.5,Math.floor(p.y)+y+frac,Math.floor(p.z)+dz+.5);
                if(!boss.inArena(world(),v)||!world().hasChunkAt(BlockPos.containing(v)))continue;
                mite.setPos(v.x,v.y,v.z);
                AABB box=mite.getBoundingBox();
                if(!world().noCollision(mite,box)||!world().getFluidState(BlockPos.containing(v)).isEmpty())continue;
                if(!world().getBlockCollisions(mite,box.move(0,-.18,0)).iterator().hasNext())continue;
                if(existing.stream().anyMatch(e->e.distanceToSqr(v)<.64))continue;
                if(organs.stream().anyMatch(e->e.distanceToSqr(v)<.81))continue;
                return true;
            }
        }return false;
    }
    private void spawnEggs(){
        lastSpawned=0;int eggs=boss.arena().eggs(),larvae=larvaCount();
        if(world().getDifficulty()==Difficulty.PEACEFUL){announce("산란 불가: 평화로움 난이도");return;}
        for(Vec3 p:zones)for(int n=0;n<(phaseAtStart==3?3:2);n++){
            if(!ArenaRules.canLay(eggs,larvae))break;
            ArenaOrgan egg=BossEntities.BROOD_EGG.get().create(world());if(egg==null)continue;
            egg.configure(boss,-1);if(!placeLarva(egg,p))continue;
            
            if(world().addFreshEntity(egg)){eggs++;lastSpawned++;world().sendParticles(ParticleTypes.HAPPY_VILLAGER,egg.getX(),egg.getY()+.5,egg.getZ(),14,.3,.3,.3,0);}
        }
        announce(lastSpawned>0?"기생충 알집 "+lastSpawned+"개 생성! 3초 이내 파괴!":"알집 생성 실패/제한: "+eggs+"개, 유충 "+larvae+"마리");
    }
    public void hatchEgg(ArenaOrgan egg){
        if(!boss.isAlive()||!boss.inRoute()||!boss.arena().ownsSlot(egg)||world().getDifficulty()==Difficulty.PEACEFUL||larvaCount()>=ArenaRules.MAX_BROOD)return;
        Endermite mite=EntityType.ENDERMITE.create(world());if(mite==null)return;
        Vec3 p=egg.position();mite.setPos(p.x,p.y,p.z);
        // The egg occupies the reserved hatch point. Discard it before collision search.
        egg.discard();
        if(!placeLarva(mite,p)){announce("유충 부화 실패: 안전한 바닥 없음");return;}
        mite.addTag(ownerTag());mite.setCustomName(Component.literal("라디키온의 기생 유충"));mite.setCustomNameVisible(true);
        if(world().addFreshEntity(mite)){participants().stream().min(java.util.Comparator.comparingDouble(a->a.distanceToSqr(mite))).ifPresent(mite::setTarget);world().sendParticles(ParticleTypes.POOF,mite.getX(),mite.getY()+.4,mite.getZ(),20,.4,.3,.4,.03);world().playSound(null,mite.getX(),mite.getY(),mite.getZ(),SoundEvents.ENDERMITE_AMBIENT,SoundSource.HOSTILE,1F,.8F);announce("기생 유충 부화! ("+larvaCount()+"/6)");}
    }
    /** Loaded or reloaded orphan larvae are removed within 40 ticking ticks. */
    public static void tickLarva(EntityTickEvent.Post event){
        if(!(event.getEntity() instanceof Endermite mite)||!(mite.level() instanceof ServerLevel level)||mite.tickCount%40!=0)return;
        for(String tag:mite.getTags())if(tag.startsWith(OWNER_PREFIX)){
            try{Entity e=level.getEntity(UUID.fromString(tag.substring(OWNER_PREFIX.length())));
                if(!(e instanceof RadicionEntity boss)||!boss.isAlive()||!boss.inRoute())mite.discard();
            }catch(IllegalArgumentException ex){mite.discard();}return;
        }
    }
    public void cleanupLarvae(){
        if(!(boss.level() instanceof ServerLevel level))return;
        List<Endermite> owned=new ArrayList<>();
        for(Entity e:level.getAllEntities())if(e instanceof Endermite mite&&mite.getTags().contains(ownerTag()))owned.add(mite);
        for(Endermite mite:owned)mite.discard();
        lastSpawned=0;
    }
    private void finish(){active=null;elapsed=0;zones.clear();warned=false;impacted=false;cooldown=AttackPattern.cooldown(phase());boss.setCombatPose(0);}
    public void reset(boolean removeLarvae){finish();groundTicks=0;groundSequence=0;airSequence=0;cooldown=30;reachedPhase=AttackPattern.phase(boss.getHealth(),boss.getMaxHealth());if(removeLarvae)cleanupLarvae();}
    public void save(CompoundTag tag){tag.putBoolean("RadicionCombatAuto",automatic);tag.putInt("RadicionCombatPhase",reachedPhase);tag.putInt("RadicionCombatGround",groundTicks);tag.putInt("RadicionCombatGroundSeq",groundSequence);tag.putInt("RadicionCombatAirSeq",airSequence);}
    public void load(CompoundTag tag){finish();reachedPhase=Math.max(1,Math.min(3,tag.getInt("RadicionCombatPhase")));automatic=!tag.contains("RadicionCombatAuto")||tag.getBoolean("RadicionCombatAuto");groundTicks=Math.max(0,Math.min(440,tag.getInt("RadicionCombatGround")));groundSequence=Math.max(0,tag.getInt("RadicionCombatGroundSeq"));airSequence=Math.max(0,tag.getInt("RadicionCombatAirSeq"));}
}
