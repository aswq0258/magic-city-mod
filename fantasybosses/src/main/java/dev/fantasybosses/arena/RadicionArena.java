package dev.fantasybosses.arena;

import dev.fantasybosses.BossEntities;
import dev.fantasybosses.entity.ArenaOrgan;
import dev.fantasybosses.entity.RadicionEntity;
import dev.fantasybosses.movement.RadicionRoute;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/** Arena mechanics are owned by one boss UUID. No terrain writes, no global scoreboard. */
public final class RadicionArena {
    private static final Vec3[] ROOT={new Vec3(-2877.5,9,-859.5),new Vec3(-2841.5,9,-859.5),new Vec3(-2859.5,9,-872.5),new Vec3(-2859.5,9,-843.5)};
    private static final Vec3[][] WAVES={{new Vec3(-2871.5,9,-868.5),new Vec3(-2847.5,9,-851.5)},{new Vec3(-2847.5,9,-868.5),new Vec3(-2871.5,9,-849.5)}};
    private final RadicionEntity boss;
    private final ServerBossEvent rootBar=new ServerBossEvent(Component.literal("마력 흡수 기관"),BossEvent.BossBarColor.GREEN,BossEvent.BossBarOverlay.NOTCHED_10);
    private boolean enabled, lockAnnounced;
    private int mask, healClock, waveClock, waveTicks, waveIndex, reconcileClock;
    public RadicionArena(RadicionEntity boss){this.boss=boss;}
    private ServerLevel world(){return (ServerLevel)boss.level();}
    public static boolean chunksReady(ServerLevel level){return chunksReady(level,BlockPos.ZERO);}
    public static boolean chunksReady(ServerLevel level,BlockPos o){for(int x=-2895;x<=-2816;x+=16)for(int z=-895;z<=-816;z+=16)if(!level.hasChunkAt(new BlockPos(x,9,z).offset(o)))return false;return level.hasChunkAt(new BlockPos(-2825,9,-825).offset(o));}
    public int roots(){return enabled?ArenaRules.count(mask):0;}
    public boolean shield(){return roots()>0&&boss.getHealth()<=ArenaRules.floor(boss.getMaxHealth())+.01F;}
    public boolean waveActive(){return waveTicks>0;}
    public float clampHealth(float health){return enabled?ArenaRules.clamp(health,boss.getMaxHealth(),mask):health;}
    public boolean ownsSlot(ArenaOrgan organ){return enabled&&organ.belongsTo(boss)&&(organ.egg()||(organ.slot()>=0&&organ.slot()<4&&(mask&(1<<organ.slot()))!=0));}
    public List<ArenaOrgan> organs(){return world().getEntitiesOfClass(ArenaOrgan.class,boss.bounds().inflate(4),e->e.belongsTo(boss)&&e.isAlive());}
    public int eggs(){return enabled&&boss.level() instanceof ServerLevel?(int)organs().stream().filter(ArenaOrgan::egg).count():0;}
    public String status(){return "roots="+roots()+"/4 shield="+shield()+" eggs="+eggs()+" fissureTicks="+waveTicks;}
    private List<ServerPlayer> players(){return world().players().stream().filter(p->p.isAlive()&&!p.isSpectator()&&boss.inArena(p.level(),p.position())).toList();}
    private boolean hasCombatPlayers(){return players().stream().anyMatch(p->!p.isCreative());}
    public void announce(String msg){for(ServerPlayer p:players())p.displayClientMessage(Component.literal(msg),true);}
    public void start(){cleanup();enabled=true;mask=boss.getHealth()>boss.getMaxHealth()*.66F?15:0;healClock=waveClock=waveTicks=waveIndex=reconcileClock=0;waveIndex=1;lockAnnounced=false;ensureRoots();updateBar();if(mask!=0)announce("세계수 마력 흡수 기관 4개를 파괴하세요!");}
    private void ensureRoots(){
        if(!enabled||mask==0)return;
        List<ArenaOrgan> current=organs();
        for(int i=0;i<4;i++){
            final int slot=i;if((mask&(1<<i))==0||current.stream().anyMatch(e->e.slot()==slot))continue;
            Vec3 p=boss.shifted(ROOT[i]);if(!world().hasChunkAt(BlockPos.containing(p)))continue;
            ArenaOrgan organ=BossEntities.ROOT_ORGAN.get().create(world());if(organ==null)continue;
            organ.configure(boss,i);organ.setPos(p.x,p.y,p.z);
            // Original verified coordinates, but avoid spawning inside a modified world.
            if(world().getBlockCollisions(organ,organ.getBoundingBox()).iterator().hasNext()||!world().getFluidState(BlockPos.containing(p)).isEmpty()){
                mask&=~(1<<i);announce("흡수 기관 "+(i+1)+" 생성 불가: 지형이 변경되어 해당 기관을 제외합니다.");continue;
            }
            world().addFreshEntity(organ);
        }
    }
    private void updateBar(){
        rootBar.setProgress(roots()/4F);rootBar.setName(Component.literal("세계수 마력 흡수 기관 ["+roots()+"/4]"+(shield()?" — 보호막":"")));rootBar.setVisible(roots()>0);
        List<ServerPlayer> nearby=players();for(ServerPlayer p:new ArrayList<>(rootBar.getPlayers()))if(!nearby.contains(p)||roots()==0)rootBar.removePlayer(p);
        if(roots()>0)for(ServerPlayer p:nearby)rootBar.addPlayer(p);
    }
    public void organDestroyed(ArenaOrgan organ){
        if(!ownsSlot(organ)||!boss.isAlive())return;
        if(organ.egg()){announce("기생충 알집 파괴!");return;}
        boolean wasShield=shield();mask&=~(1<<organ.slot());
        announce("마력 흡수 기관 파괴 — "+roots()+"/4 남음");
        if(mask==0){if(wasShield)boss.setHealth(Math.min(boss.getHealth(),boss.getMaxHealth()*.659F));announce("세계수 보호막 해제!");}
        updateBar();
    }
    public boolean clearRoots(){if(!enabled)return false;boolean wasShield=shield();mask=0;for(ArenaOrgan e:organs())if(!e.egg())e.discard();if(wasShield)boss.setHealth(Math.min(boss.getHealth(),boss.getMaxHealth()*.659F));updateBar();return true;}
    private void beams(){
        Vec3 tail=boss.anchor(boss.routeFrame(),10);
        for(ArenaOrgan organ:organs())if(!organ.egg()&&ownsSlot(organ)){
            Vec3 a=organ.position().add(0,1.4,0);Vec3 v=tail.subtract(a);int points=Math.max(1,Math.min(24,(int)Math.ceil(v.length())));double offset=(boss.tickCount%16)/16.0;
            for(int j=0;j<points;j++){Vec3 p=a.add(v.scale((j+offset)/points));world().sendParticles(ParticleTypes.HAPPY_VILLAGER,p.x,p.y,p.z,1,0,0,0,0);}
        }
    }
    /** True while the arena wave owns the movement/attack clock. */
    public boolean tick(){
        if(!enabled||!boss.inRoute()||!boss.isAlive())return false;
        if(++reconcileClock>=20){reconcileClock=0;ensureRoots();updateBar();}
        if(boss.routePaused())return waveActive();
        if(roots()>0){
            if(shield()&&!lockAnnounced){lockAnnounced=true;announce("뿌리 보호막! 남은 흡수 기관을 파괴하세요.");}
            if(boss.tickCount%8==0)beams();
            if(!shield()&&++healClock>=ArenaRules.HEAL_INTERVAL){healClock=0;boss.heal(2F*roots());}
        }
        if(waveTicks>0){
            if(waveTicks%5==0)warnWave();
            if(--waveTicks==0){impactWave();boss.beginGroggy(60);}
            return true;
        }
        if(boss.combat().phase()==3&&boss.tickCount%20==0){
            for(Vec3[] pair:WAVES)for(Vec3 o:pair){Vec3 p=boss.shifted(o);
                world().sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR,p.x,p.y+.1,p.z,8,.65,.05,.65,0);
                world().sendParticles(ParticleTypes.HAPPY_VILLAGER,p.x,p.y+.1,p.z,3,.25,.04,.25,0);
            }
        }
        if(boss.combat().phase()==3&&boss.combat().auto()&&hasCombatPlayers()){
            if(waveClock<ArenaRules.WAVE_INTERVAL)waveClock++;
            if(waveClock>=ArenaRules.WAVE_INTERVAL&&startWave())return true;
        }
        return false;
    }
    public boolean startWave(){
        if(!enabled||waveActive()||boss.combat().phase()!=3||boss.combat().busy()||!boss.availableGround())return false;
        waveIndex=1-waveIndex;waveTicks=ArenaRules.WAVE_WARNING;waveClock=0;announce("세계수 마력 균열 — 위험 구역 밖으로 이동하세요!");warnWave();return true;
    }
    private void warnWave(){for(Vec3 o:WAVES[waveIndex]){Vec3 p=boss.shifted(o);for(int j=0;j<40;j++){double a=j*Math.PI/20;world().sendParticles(ParticleTypes.SOUL_FIRE_FLAME,p.x+5*Math.cos(a),p.y+.12,p.z+5*Math.sin(a),1,0,0,0,0);}world().sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR,p.x,p.y+.25,p.z,12,1.4,.2,1.4,0);}}
    private void impactWave(){
        for(ServerPlayer p:players())if(!p.isCreative())for(Vec3 o:WAVES[waveIndex]){Vec3 c=boss.shifted(o);if(ArenaRules.inWave(p.getX(),p.getY(),p.getZ(),c.x,c.y,c.z)){p.hurt(boss.damageSources().indirectMagic(boss,boss),ArenaRules.WAVE_DAMAGE);break;}}
        for(Vec3 o:WAVES[waveIndex]){Vec3 c=boss.shifted(o);world().sendParticles(ParticleTypes.EXPLOSION,c.x,c.y+.7,c.z,8,1.6,.5,1.6,0);world().playSound(null,c.x,c.y,c.z,SoundEvents.GENERIC_EXPLODE.value(),SoundSource.HOSTILE,2F,.7F);}
        announce("마력 균열 폭발 종료 — 반격 기회!");
    }
    public void detachBar(){rootBar.removeAllPlayers();}
    public void cleanup(){
        enabled=false;mask=waveTicks=waveClock=healClock=0;lockAnnounced=false;rootBar.setVisible(false);rootBar.removeAllPlayers();
        if(!(boss.level() instanceof ServerLevel level))return;
        List<ArenaOrgan> owned=new ArrayList<>();for(Entity e:level.getAllEntities())if(e instanceof ArenaOrgan organ&&organ.belongsTo(boss))owned.add(organ);for(ArenaOrgan e:owned)e.discard();
    }
    public void save(CompoundTag tag){tag.putBoolean("ArenaEnabled",enabled);tag.putInt("ArenaRoots",mask);tag.putInt("ArenaHeal",healClock);tag.putInt("ArenaWaveClock",waveClock);tag.putInt("ArenaWaveTicks",waveTicks);tag.putInt("ArenaWaveIndex",waveIndex);}
    public void load(CompoundTag tag){enabled=tag.contains("ArenaEnabled")?tag.getBoolean("ArenaEnabled"):boss.inRoute()&&boss.isAlive();mask=tag.contains("ArenaRoots")?tag.getInt("ArenaRoots")&15:enabled&&boss.getHealth()>boss.getMaxHealth()*.66F?15:0;healClock=Math.max(0,Math.min(ArenaRules.HEAL_INTERVAL,tag.getInt("ArenaHeal")));waveClock=Math.max(0,Math.min(ArenaRules.WAVE_INTERVAL,tag.getInt("ArenaWaveClock")));waveTicks=Math.max(0,Math.min(ArenaRules.WAVE_WARNING,tag.getInt("ArenaWaveTicks")));waveIndex=tag.getInt("ArenaWaveIndex")&1;reconcileClock=19;lockAnnounced=false;}
}
