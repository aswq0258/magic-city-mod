package dev.fantasybosses.arena;

/** Pure rules shared with offline regression tests. Durations are in server ticks. */
public final class ArenaRules {
    private ArenaRules() {}
    public static final int ROOTS=4, ROOT_HEALTH=24, EGG_HEALTH=8, HATCH=60, MAX_EGGS=3, MAX_BROOD=6;
    public static final int HEAL_INTERVAL=100, WAVE_INTERVAL=200, WAVE_WARNING=50, WAVE_RADIUS=5, WAVE_DAMAGE=8, DEATH_DURATION=48;
    public static int count(int mask){return Integer.bitCount(mask&15);}
    public static float floor(float maximum){return maximum*.6625F;}
    public static float clamp(float incoming,float maximum,int mask){return mask!=0?Math.max(incoming,floor(maximum)):incoming;}
    public static int segmentVisibleCount(int deathTicks){return deathTicks<=0?11:Math.max(0,11-(deathTicks-1)/4-1);}
    public static boolean canLay(int eggs,int larvae){return eggs<MAX_EGGS&&eggs+larvae<MAX_BROOD;}
    public static boolean inWave(double x,double y,double z,double cx,double cy,double cz){double dx=x-cx,dz=z-cz;return dx*dx+dz*dz<=WAVE_RADIUS*WAVE_RADIUS&&Math.abs(y-cy)<=3;}
}
