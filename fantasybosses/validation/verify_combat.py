import gzip,struct,zipfile,math,pathlib,sys
root=pathlib.Path(__file__).resolve().parents[1]; old=zipfile.ZipFile(sys.argv[1]); prefix=old.namelist()[0].split('/')[0]+'/'
files=['src/main/resources/fantasybosses/radicion_route.bin.gz','src/main/resources/fantasybosses/radicion_combat_poses.bin.gz','src/main/resources/assets/fantasybosses/animations/radicion.animation.json','src/main/resources/assets/fantasybosses/geo/radicion.geo.json','src/main/resources/assets/fantasybosses/geo/radicion_route.geo.json','src/main/resources/assets/fantasybosses/textures/entity/radicion.png','src/main/resources/assets/fantasybosses/textures/entity/radicion_glowmask.png','src/main/java/dev/fantasybosses/movement/RoutePlayback.java','src/main/java/dev/fantasybosses/movement/RadicionRoute.java']
for name in files:
 assert old.read(prefix+name)==(root/name).read_bytes(), name
 print('EXACT MATCH:',name)
with gzip.open(root/'src/main/resources/fantasybosses/radicion_combat_poses.bin.gz','rb') as f:
 assert struct.unpack('>3i',f.read(12))==(0x52435031,204,182)
 nonidentity=[]; total=0
 for stage in range(6):
  count=0
  for _ in range(204*182):
   p=struct.unpack('>10f',f.read(40)); assert all(map(math.isfinite,p)); assert abs(sum(v*v for v in p[3:7])-1)<1e-5; assert min(p[7:])>0
   if max(abs(a-b) for a,b in zip(p,(0,0,0,0,0,0,1,1,1,1)))>1e-5:count+=1
   total+=1
  nonidentity.append(count)
 assert not f.read(1)
print('PASS:',total,'pose records, finite values, unit quaternions and positive scales; changed poses:',nonidentity)
# Verify the special route transitions for every possible starting ground frame.
G=204; AIR=204; COUNT=506; AIR_COUNT=260; DIVE=464; JOIN=239; TOP=394; LAND=32; HIT=487; WALL=429
for mode in ('dive','brood','descent'):
 for start in range(G):
  current=start;hold=0;warned=False;hits=0;landed=False
  for ticks in range(1000):
   if hold:
    if hold==1 and mode=='brood':hits+=1
    hold-=1;continue
   if current<AIR:
    nxt=(current+1)%G
    if current==0 or nxt==0:nxt=JOIN
   elif current<DIVE:
    if mode=='dive' and current==TOP:nxt=DIVE
    else:
     advance=min(2,DIVE-current) if mode=='descent' and warned and hits==0 and WALL<=current<DIVE else 1
     nxt=AIR+(current-AIR+advance)%AIR_COUNT
     if nxt==TOP and mode=='dive':hold=22
     if nxt==JOIN:nxt=0;landed=True
   else:
    nxt=current+1
    if nxt>=COUNT:nxt=LAND;landed=True
   current=nxt
   if landed:break
   if mode=='dive' and nxt==TOP:warned=True
   if mode=='dive' and nxt==HIT:assert warned;hits+=1
   if mode=='brood' and nxt==TOP:warned=True;hold=26
   if mode=='descent' and nxt==WALL:warned=True;hold=22
   if mode=='descent' and nxt==AIR:assert warned;hits+=1
  assert landed and hits==1 and current==(LAND if mode=='dive' else 0),(mode,start,ticks,hits,current)
print('PASS: 612 modeled route sequences, single impact/spawn and ground recovery (Python model, not execution of Minecraft classes)')
