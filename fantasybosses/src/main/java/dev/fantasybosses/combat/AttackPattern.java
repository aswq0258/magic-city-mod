package dev.fantasybosses.combat;

/** Timing and base HP damage, independent of Minecraft for regression tests. */
public enum AttackPattern {
    BITE("bite", "칼날 턱 강타", 1, 18, 34, 30, 4, 6, 8),
    DRAIN("drain", "생명력 흡수", 1, 24, 32, 60, 5, 5, 5),
    TAIL("tail", "꼬리 촉수 난타", 2, 22, 38, 40, 4, 7, 7),
    DIVE("dive", "천장 직접 낙하", 1, 22, 42, 100, 4, 6, 9),
    BROOD("brood", "기생 유충 산란", 2, 26, 26, 100, 3, 0, 0),
    DESCENT("descent", "벽면 돌진", 2, 22, 22, 100, 5, 10, 10);
    public final String key, label;
    public final int minPhase, windup, end, recovery, radius, damage, damage3;
    AttackPattern(String key,String label,int minPhase,int windup,int end,int recovery,int radius,int damage,int damage3) {
        this.key=key;this.label=label;this.minPhase=minPhase;this.windup=windup;this.end=end;this.recovery=recovery;
        this.radius=radius;this.damage=damage;this.damage3=damage3;
    }
    public boolean ground(){return ordinal()<3;}
    public int radius(int phase){return this==DIVE ? radius+phase-1:radius;}
    public int damage(int phase){return phase==3?damage3:damage;}
    public static int phase(float health,float max){return health<=max*.33F?3:health<=max*.66F?2:1;}
    public static int airInterval(int phase){return phase==1?440:phase==2?360:300;}
    public static int cooldown(int phase){return phase==1?24:phase==2?18:12;}
    public static AttackPattern byKey(String key){for(var p:values())if(p.key.equals(key))return p;return null;}
    public int poseCode(int tick){return ground()?(ordinal()+1)*1000+tick:0;}
    public record Pose(int from,int to,float alpha) {}
    /** Pose 0 is the corrected walking baseline; 1..6 are captured attack deltas. */
    public static Pose pose(int code,float partial){
        int id=code/1000;float t=code%1000+Math.max(0,Math.min(1,partial));
        // Retiming the verified pose sequence keeps the contact pose aligned with damage.
        AttackPattern p=id>=1&&id<=3?values()[id-1]:null;
        if(p!=null){int oldHit=id==1?24:id==2?32:30, oldEnd=id==1?42:id==2?40:48;
            t=t<=p.windup?t*oldHit/p.windup:oldHit+(t-p.windup)*(oldEnd-oldHit)/(p.end-p.windup);
        }
        return switch(id){
            case 1 -> t<12?blend(0,1,t,0,12):t<20?new Pose(1,1,0):t<24?blend(1,2,t,20,24):t<29?new Pose(2,2,0):blend(2,0,t,29,42);
            case 2 -> t<14?blend(0,3,t,0,14):t<33?new Pose(3,3,0):blend(3,0,t,33,40);
            case 3 -> t<12?blend(0,4,t,0,12):t<26?new Pose(4,4,0):t<30?blend(4,5,t,26,30):t<36?blend(5,6,t,30,36):blend(6,0,t,36,48);
            default -> new Pose(0,0,0);
        };
    }
    private static Pose blend(int a,int b,float t,int start,int end){float k=Math.max(0,Math.min(1,(t-start)/(end-start)));return new Pose(a,b,k*k*(3-2*k));}
}
