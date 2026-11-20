import dev.fantasybosses.arena.ArenaRules;
public final class TestArenaRules {
    public static void main(String[] args){
        int cases=0;
        for(int mask=0;mask<16;mask++)for(int max:new int[]{240,480,10000})for(int hit=0;hit<=max+1;hit+=17){
            float health=max-hit;float next=ArenaRules.clamp(health,max,mask);
            if(mask==0 ? next!=health : next<ArenaRules.floor(max)||next!=Math.max(health,ArenaRules.floor(max)))throw new AssertionError("shield");cases++;
        }
        if(ArenaRules.floor(240)!=159F||ArenaRules.count(15)!=4||ArenaRules.count(0)!=0)throw new AssertionError("root");
        for(int e=0;e<=6;e++)for(int l=0;l<=6;l++){
            if(ArenaRules.canLay(e,l)!=(e<3&&e+l<6))throw new AssertionError("brood cap");
            if(ArenaRules.canLay(e,l)&&e+1+l>6)throw new AssertionError("overflow");cases++;
        }
        int prev=11;for(int t=0;t<=48;t++){int count=ArenaRules.segmentVisibleCount(t);if(count<0||count>prev||prev-count>1)throw new AssertionError("death sequence");prev=count;cases++;}
        if(prev!=0||ArenaRules.segmentVisibleCount(0)!=11||ArenaRules.segmentVisibleCount(41)!=0)throw new AssertionError("death count");
        if(!ArenaRules.inWave(5,9,0,0,9,0)||ArenaRules.inWave(5.01,9,0,0,9,0)||ArenaRules.inWave(0,12.01,0,0,9,0)||ArenaRules.inWave(5,9,5,0,9,0))throw new AssertionError("wave radius");
        if(ArenaRules.HATCH!=60||ArenaRules.HEAL_INTERVAL!=100||ArenaRules.WAVE_WARNING!=50||ArenaRules.WAVE_INTERVAL!=200)throw new AssertionError("timing");
        System.out.println("PASS: "+cases+" pure arena rule cases plus wave bounds and durations");
    }
}
