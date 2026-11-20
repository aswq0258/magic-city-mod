package dev.fantasybosses.combat;

import dev.fantasybosses.movement.RadicionRoute;
import java.io.*;
import java.util.zip.GZIPInputStream;
/** Six captured attack deltas relative to the pre-correction ground pose. Shared by client rendering and server hit zones. */
public final class CombatPoseData {
    private static final float[] IDENTITY={0,0,0,0,0,0,1,1,1,1};
    private static final float[][][][] DATA=load();
    private CombatPoseData(){}
    public static void preload() {} // Initialize before the first attack render.
    public static float[] get(int stage,int frame,int part){return stage==0?IDENTITY:DATA[stage-1][frame][part];}
    private static float[][][][] load(){
        float[][][][] d=new float[6][RadicionRoute.GROUND][RadicionRoute.PARTS][10];
        try(InputStream raw=CombatPoseData.class.getResourceAsStream("/fantasybosses/radicion_combat_poses.bin.gz")){
            if(raw==null)throw new IOException("missing combat poses");
            try(DataInputStream in=new DataInputStream(new BufferedInputStream(new GZIPInputStream(raw)))){
                if(in.readInt()!=0x52435031||in.readInt()!=RadicionRoute.GROUND||in.readInt()!=RadicionRoute.PARTS)throw new IOException("bad combat pose header");
                for(var stage:d)for(var frame:stage)for(var part:frame)for(int i=0;i<10;i++){
                    part[i]=in.readFloat();if(!Float.isFinite(part[i]))throw new IOException("nonfinite combat pose");
                }
                if(in.read()!=-1)throw new IOException("trailing pose data");
            }
            return d;
        }catch(IOException ex){throw new IllegalStateException("failed to load combat poses",ex);}
    }
}
