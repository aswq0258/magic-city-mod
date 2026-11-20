package dev.fantasybosses.client;

import dev.fantasybosses.BossSounds;
import dev.fantasybosses.FantasyBosses;
import dev.fantasybosses.entity.RadicionEntity;
import dev.fantasybosses.movement.RadicionRoute;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.SelectMusicEvent;

/** Per client, one non-positional music loop. Does not run on the dedicated server. */
@EventBusSubscriber(modid=FantasyBosses.MOD_ID,value=Dist.CLIENT,bus=EventBusSubscriber.Bus.GAME)
public final class RadicionMusic {
    private RadicionMusic() {}
    /** 보스를 찾는 범위 (플레이어 둘레). v0.8: 보스마다 옮긴 전장 상자로 판정 */
    private static final double SEARCH=96;
    private static ClientLevel lastLevel;
    private static RadicionEntity boss;
    private static Loop track;
    private static int retry;
    private static int age;

    // v0.8: 고정 좌표 대신 그 보스의 전장(이동량을 더한 상자, 같은 차원) 안에 있을 때만. magiccity 보스 전장은 장마다 한 팀이라 참가자에게만 들림
    private static boolean eligible(Minecraft mc){
        if(mc.level==null || mc.player==null || !mc.player.isAlive() || mc.player.isSpectator()) return false;
        if(live(boss)) return boss.inArena(mc.level,mc.player.position());
        boss=find(mc);
        return boss!=null;
    }
    private static RadicionEntity find(Minecraft mc){
        return mc.level.getEntitiesOfClass(RadicionEntity.class,mc.player.getBoundingBox().inflate(SEARCH),
                e->live(e)&&e.inArena(mc.level,mc.player.position())).stream().findFirst().orElse(null);
    }
    private static boolean live(RadicionEntity entity){return entity!=null&&!entity.isRemoved()&&entity.isAlive()&&entity.inRoute();}
    private static void clear(Minecraft mc){
        if(track!=null)mc.getSoundManager().stop(track);
        track=null;boss=null;retry=0;age=0;
    }
    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event){
        Minecraft mc=Minecraft.getInstance();
        if(lastLevel!=mc.level){clear(mc);lastLevel=mc.level;}
        if(!eligible(mc)||mc.options.getSoundSourceVolume(SoundSource.MUSIC)<=0F||mc.options.getSoundSourceVolume(SoundSource.MASTER)<=0F){clear(mc);return;}
        if(mc.isPaused())return;
        if(!live(boss))boss=find(mc);
        boolean wanted=live(boss);
        if(retry>0)retry--;
        if(track!=null){
            age++;
            track.wanted=wanted;
            // Sound may be lost on resource reload or device change; allow async startup time.
            if(track.isStopped()||(age>40&&!mc.getSoundManager().isActive(track))){
                mc.getSoundManager().stop(track);track=null;age=0;retry=20;
            }
        }
        if(wanted&&track==null&&retry==0){
            if(mc.getSoundManager().getSoundEvent(BossSounds.RADICION_BGM.get().getLocation())==null){retry=100;return;}
            track=new Loop();age=0;
            mc.getMusicManager().stopPlaying();
            mc.getSoundManager().play(track);
        }
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void selectMusic(SelectMusicEvent event){
        // Suppress only the vanilla situational music manager, not sound effects or other categories.
        if(track!=null&&!track.isStopped()&&eligible(Minecraft.getInstance()))event.overrideMusic(null);
    }
    private static final class Loop extends AbstractTickableSoundInstance {
        private boolean wanted=true;
        Loop(){
            super(BossSounds.RADICION_BGM.get(),SoundSource.MUSIC,RandomSource.create());
            looping=true;delay=0;relative=true;attenuation=SoundInstance.Attenuation.NONE;
            volume=.06F;pitch=1F;x=y=z=0;
        }
        @Override public void tick(){
            if(wanted)volume=Math.min(.68F,volume+.031F);
            else {volume=Math.max(0F,volume-.034F);if(volume<=0F)stop();}
        }
    }
}
