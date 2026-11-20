import dev.fantasybosses.combat.AttackPattern;
public final class TestAttackPattern {
    public static void main(String[] args){
        int samples=0;
        for(var p:AttackPattern.values()){
            if(p.windup>p.end||p.recovery<=0||p.minPhase<1||p.minPhase>3)throw new AssertionError(p);
            if(!p.ground())continue;
            for(int i=0;i<=p.end;i++)for(int j=0;j<=4;j++){
                var pose=AttackPattern.pose(p.poseCode(i),j/4f);
                if(pose.from()<0||pose.from()>6||pose.to()<0||pose.to()>6||pose.alpha()<0||pose.alpha()>1)throw new AssertionError(pose);
                samples++;
            }
            var initial=AttackPattern.pose(p.poseCode(0),0);var end=AttackPattern.pose(p.poseCode(p.end),0);
            if(initial.from()!=0||initial.alpha()!=0||end.to()!=0||end.alpha()!=1)throw new AssertionError("not restored "+p);
            var hit=AttackPattern.pose(p.poseCode(p.windup),0);
            if(p==AttackPattern.BITE&&hit.from()!=2||p==AttackPattern.DRAIN&&hit.from()!=3||p==AttackPattern.TAIL&&hit.from()!=5)throw new AssertionError("wrong hit pose "+p);
        }
        if(AttackPattern.phase(240,240)!=1||AttackPattern.phase(158,240)!=2||AttackPattern.phase(79,240)!=3)throw new AssertionError("phase");
        if(AttackPattern.DIVE.damage(3)!=9||AttackPattern.DIVE.radius(3)!=6)throw new AssertionError("dive phase");
        System.out.println("PASS: "+samples+" ground attack pose samples, hit pose, end pose, phase and dive scaling");
    }
}
