package dev.fantasybosses.movement;

import java.io.*;
import java.util.zip.GZIPInputStream;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import dev.fantasybosses.arena.ArenaRules;

/** Captured world-space route: original ground/air/dive frames, ground sampled at 20 TPS. */
public final class RadicionRoute {
    public static final int GROUND = 204, AIR = 204, AIR_COUNT = 260, DIVE = 464, COUNT = 506;
    public static final int AIR_JOIN = AIR + 35, DIVE_JOIN = AIR + 190, DIVE_LAND = 32;
    public static final int PARTS = 182;
    public static final RadicionRoute DATA = load();
    public final String[] names = new String[PARTS];
    public final Frame[] frames = new Frame[COUNT];
    public record Frame(Vec3 head, Vec3[] anchors, float[][] parts) {}
    private RadicionRoute() {}

    private static RadicionRoute load() {
        RadicionRoute result = new RadicionRoute();
        try (InputStream raw = RadicionRoute.class.getResourceAsStream("/fantasybosses/radicion_route.bin.gz")) {
            if (raw == null) throw new IOException("missing route resource");
            try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(raw)))) {
                if (in.readInt() != 0x52445233 || in.readInt() != COUNT || in.readInt() != PARTS)
                    throw new IOException("invalid route header");
                for (int i=0; i<PARTS; i++) result.names[i] = in.readUTF();
                for (int i=0; i<COUNT; i++) {
                    Vec3 head = readVec(in);
                    Vec3[] anchors = new Vec3[11];
                    for (int j=0; j<11; j++) anchors[j] = readVec(in);
                    float[][] parts = new float[PARTS][10];
                    for (float[] part : parts) for (int j=0; j<10; j++) {
                        part[j] = in.readFloat();
                        if (!Float.isFinite(part[j])) throw new IOException("non-finite pose");
                    }
                    result.frames[i] = new Frame(head, anchors, parts);
                }
                if (in.read() != -1) throw new IOException("trailing route data");
            }
            return result;
        } catch (IOException e) { throw new IllegalStateException("Radicion route load failed", e); }
    }
    private static Vec3 readVec(DataInputStream in) throws IOException {
        return new Vec3(in.readDouble(), in.readDouble(), in.readDouble());
    }
    public static int remapLegacyFrame(int frame) {
        if (frame < 0 || frame >= 606) return -1;
        if (frame >= 304) return frame - 100;
        if (frame < 48) return Math.min(31,(int)Math.round(frame * 32.0 / 48.0));
        return Math.min(203,32+(int)Math.round((frame-48)*172.0/256.0));
    }
    public static boolean valid(int frame) { return frame >= 0 && frame < COUNT; }
    /** 원본 왕국 대륙 둥지 (이동량 0) */
    public static boolean isArena(Level level, Vec3 p) { return isArena(level, p, BlockPos.ZERO); }
    /** v0.8: 이동량 o 만큼 옮긴 전장. 이동량 0 이면 원본과 같은 판정 */
    public static boolean isArena(Level level, Vec3 p, BlockPos o) {
        return validDimension(level, o) && ArenaRules.inBox(p.x, p.y, p.z, o.getX(), o.getY(), o.getZ());
    }
    public static boolean validDimension(Level level, BlockPos o) {
        return ArenaRules.dimensionOk(level.dimension().location().toString(), o.getX(), o.getY(), o.getZ());
    }
    public static AABB bounds(BlockPos o) {
        return new AABB(ArenaRules.MIN_X, ArenaRules.MIN_Y, ArenaRules.MIN_Z, ArenaRules.MAX_X, ArenaRules.MAX_Y, ArenaRules.MAX_Z)
                .move(o.getX(), o.getY(), o.getZ());
    }
    public static boolean loaded(Level level, int frame) { return loaded(level, frame, BlockPos.ZERO); }
    public static boolean loaded(Level level, int frame, BlockPos o) {
        for (Vec3 p : DATA.frames[frame].anchors()) {
            if (!level.hasChunkAt(BlockPos.containing(p).offset(o))) return false;
        }
        return true;
    }
    public static String phase(int frame) {
        if (!valid(frame)) return "preview";
        if (frame < AIR) return "ground";
        return frame < DIVE ? "wall_ceiling" : "dive";
    }
}
