package dev.fantasybosses.combat;
/** Balance defaults; integrate with RPG damage calculation only at handoff. */
public final class PartDamage {
    private PartDamage(){}
    public static float multiplier(int i){if(i<0||i>10)throw new IllegalArgumentException("segment");return i==0?1F:i<=3?.70F:i<=6?.85F:i<=9?1.50F:1.20F;}
    public static float groggy(boolean active){return active?1.25F:1F;}
}
