import dev.fantasybosses.combat.PartDamage;
import dev.fantasybosses.combat.AttackPattern;
public class TestPartDamage {
    public static void main(String[] args){
        float[] expected={1,.7F,.7F,.7F,.85F,.85F,.85F,1.5F,1.5F,1.5F,1.2F};
        for(int i=0;i<11;i++)if(PartDamage.multiplier(i)!=expected[i])throw new AssertionError(i);
        if(PartDamage.groggy(true)!=1.25F||PartDamage.groggy(false)!=1F)throw new AssertionError("groggy");
        int[] radius={4,5,6}, cd={24,18,12}, interval={440,360,300};
        for(int p=1;p<=3;p++)if(AttackPattern.DIVE.radius(p)!=radius[p-1]||AttackPattern.cooldown(p)!=cd[p-1]||AttackPattern.airInterval(p)!=interval[p-1])throw new AssertionError(p);
        if(AttackPattern.BITE.recovery!=30||AttackPattern.DRAIN.recovery!=60||AttackPattern.TAIL.recovery!=40||AttackPattern.DIVE.recovery!=100)throw new AssertionError("recovery");
        System.out.println("PASS: 11 segment multipliers, groggy modifier, phase timing, radius and preserved recovery");
    }
}
