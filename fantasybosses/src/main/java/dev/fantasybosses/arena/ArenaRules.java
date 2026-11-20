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

    // ── v0.8 전장 원점 변환 (2026-11-20, magiccity 보스 방 복사 전장 연결) ──
    /** 원본(왕국 대륙 둥지) 전장 상자. 녹화 좌표·기관·균열은 모두 이 상자 기준 절대 좌표 */
    public static final int MIN_X=-2895, MAX_X=-2825, MIN_Y=7, MAX_Y=40, MIN_Z=-895, MAX_Z=-825;
    /** 원본 바닥 높이 (공격 예고·판정 높이) */
    public static final double FLOOR_Y=9.15;
    public static final String KINGDOM="magiccity:kingdom";

    /** 이동량 (dx,dy,dz) 만큼 옮긴 상자 안인지 (경계 포함, 원본 isArena 와 같은 판정) */
    public static boolean inBox(double x,double y,double z,int dx,int dy,int dz){
        return x>=MIN_X+dx&&x<=MAX_X+dx&&z>=MIN_Z+dz&&z<=MAX_Z+dz&&y>=MIN_Y+dy&&y<=MAX_Y+dy;
    }
    /** 이 차원에서 전투할 수 있는지: 이동량 0 = 원본 왕국 대륙 둥지만, 이동량이 있으면 그 몹이 있는 차원(보스 전장) */
    public static boolean dimensionOk(String dimension,int dx,int dy,int dz){
        return (dx!=0||dy!=0||dz!=0)||KINGDOM.equals(dimension);
    }
    /** NBT 정수 배열 → 이동량. 길이가 3이 아니거나 너무 크면 0 (원본 그대로) */
    public static int[] offset(int[] raw){
        if(raw==null||raw.length!=3)return new int[3];
        for(int v:raw)if(Math.abs(v)>30_000_000)return new int[3];
        return raw.clone();
    }
}
