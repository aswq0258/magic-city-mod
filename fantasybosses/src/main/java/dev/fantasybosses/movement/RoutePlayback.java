package dev.fantasybosses.movement;

import java.util.Map;
import java.util.TreeMap;

/** Client-side jitter buffer. Authoritative server frames are never mutated. */
public final class RoutePlayback {
    public record Sample(int from, int to, float alpha) {}
    private final TreeMap<Integer,Integer> frames = new TreeMap<>();
    private int epoch, latest;
    private double previousCursor, cursor;
    private boolean ready;

    public void clear() { ready=false; frames.clear(); }
    public void tick(int revision, int clock, int previous, int current) {
        if (!ready || revision!=epoch || clock<latest || (long)clock-latest>30) {
            frames.clear(); epoch=revision; latest=clock;
            frames.put(clock-1,previous); frames.put(clock,current);
            previousCursor=cursor=clock-3.0; ready=true;
            return;
        }
        if (clock>latest) {
            // The previous authoritative pose fills the last missed sample, if any.
            frames.putIfAbsent(clock-1,previous);
            frames.put(clock,current); latest=clock;
        }
        previousCursor=cursor;
        // Run at one route tick/client tick, with gentle drift correction, not packet-clock resets.
        double buffer=latest-cursor;
        double advance=Math.max(0.85,Math.min(1.15,1.0+(buffer-3.0)*0.08));
        cursor=Math.min(latest,cursor+advance);
        while(frames.size()>1) {
            Integer first=frames.firstKey(), second=frames.higherKey(first);
            if(second==null || second>previousCursor-1) break;
            frames.pollFirstEntry();
        }
    }
    public Sample sample(float partialTick, int previous, int current) {
        float alpha=Math.max(0,Math.min(1,partialTick));
        if(!ready) return new Sample(previous,current,alpha);
        double time=previousCursor+(cursor-previousCursor)*alpha;
        Map.Entry<Integer,Integer> a=frames.floorEntry((int)Math.floor(time));
        Map.Entry<Integer,Integer> b=frames.ceilingEntry((int)Math.ceil(time));
        if(a==null) a=frames.firstEntry();
        if(b==null) b=frames.lastEntry();
        if(a.getKey().equals(b.getKey())) return new Sample(a.getValue(),a.getValue(),0);
        float fraction=(float)Math.max(0,Math.min(1,(time-a.getKey())/(b.getKey()-a.getKey())));
        return new Sample(a.getValue(),b.getValue(),fraction);
    }
}
