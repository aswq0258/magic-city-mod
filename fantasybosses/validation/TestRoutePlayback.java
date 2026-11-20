import dev.fantasybosses.movement.RoutePlayback;
public class TestRoutePlayback {
 static double at(RoutePlayback.Sample s){return s.from()+(s.to()-s.from())*s.alpha();}
 public static void main(String[] args){
  RoutePlayback p=new RoutePlayback(); int latest=0; double prev=-1; double min=99,max=-99; int monotonic=0;
  for(int t=0;t<500;t++){
   // Packet bursts 3 ticks apart; also single-tick delays. Server frames are represented by their clock.
   if(t%3==0)latest=t;
   p.tick(1,latest,Math.max(0,latest-1),latest);
   for(int j=0;j<=4;j++){
    double v=at(p.sample(j/4f,latest-1,latest));
    if(v+1e-5<prev)throw new AssertionError("backwards "+v+" "+prev);
    prev=v;monotonic++;
   }
   if(t>30){double lag=t-prev;min=Math.min(min,lag);max=Math.max(max,lag);}
  }
  p.tick(2,0,800,800);if(at(p.sample(.5f,800,800))!=800)throw new AssertionError("reset");
  for(int t=0;t<100;t++) p.tick(2,0,800,800);
  if(at(p.sample(.5f,800,800))!=800)throw new AssertionError("hold");
  p.clear(); if(at(p.sample(.5f,10,11))!=10.5)throw new AssertionError("fallback");
  System.out.printf("PASS: %d monotonic sub-frame samples, simulated 3-tick packet bursts, lag %.2f..%.2f ticks; reset/hold/fallback PASS%n",monotonic,min,max);
 }
}
