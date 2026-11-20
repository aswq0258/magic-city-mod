"""Original procedural score, 32 bars at 80 BPM. Requires numpy, scipy, ffmpeg.
Usage: python tools/generate_radicion_bgm.py [output.ogg]
No samples or third-party music are used. All sound is synthesized.
"""
from pathlib import Path
import sys, subprocess, tempfile
import numpy as np
from scipy.signal import butter, sosfilt
from scipy.io.wavfile import write
SR=44100; BEAT=.75; DUR=96; N=int(SR*DUR)
rng=np.random.default_rng(751)
L=np.zeros(N,np.float32); R=np.zeros(N,np.float32)
# Circular placement preserves reverb and notes across the loop boundary.
def add(s,start,pan=0,gain=1):
    s=np.asarray(s,dtype=np.float32)*gain; i=int(round(start*SR))%N
    a=np.sqrt((1-pan)/2); b=np.sqrt((1+pan)/2)
    n=min(len(s),N-i); L[i:i+n]+=s[:n]*a; R[i:i+n]+=s[:n]*b
    if n<len(s): L[:len(s)-n]+=s[n:]*a; R[:len(s)-n]+=s[n:]*b

def wet(s,start,pan,gain):
    add(s,start,pan,gain)
    for d,v,p in [(0.113,.24,-pan*.7),(.271,.17,.6),(.413,.12,-.6),(.737,.075,.35),(1.139,.04,-.3)]:add(s,start+d,p,gain*v)

def hz(n):return 440*2**((n-69)/12)
def time(d):return np.arange(round(d*SR),dtype=np.float64)/SR
def envelope(t,a=.2,r=.5):return np.minimum(1,t/a)*np.minimum(1,(t[-1]-t)/r)
def bowed(n,d,detune=0):
    t=time(d); f=hz(n)*2**(detune/1200); phase=2*np.pi*f*t+.028*np.sin(2*np.pi*4.8*t)
    s=np.sin(phase)+.29*np.sin(2*phase+.6)+.12*np.sin(3*phase)+.065*np.sin(5*phase)
    return s*envelope(t,1.9,2.2)*(.84+.16*np.sin(2*np.pi*.17*t+.8))
# Persistent subterranean D pedal, eight rotating dissonant chords.
chords=[(26,38,51,57),(26,39,50,56),(26,38,53,60),(26,36,51,57),
        (26,39,53,56),(24,36,51,58),(26,38,50,56),(26,39,51,57)]
for i,ch in enumerate(chords):
    start=i*12-1.4
    for j,n in enumerate(ch):
        wet(bowed(n,14.8,[-3,2,-5,4][j]),start,[-.15,.32,-.5,.5][j],[.15,.075,.038,.027][j])
# Dark bowed motif, sparse off-beat entries; original note sequence.
for bar in range(32):
    start=bar*3
    seq=([50,51,45],[48,51,46],[50,53,51],[45,46,38])[bar//4%4]
    if bar%2==0:
        for k,n in enumerate(seq):
            t=time(1.25); p=2*np.pi*hz(n)*t+.12*np.sin(2*np.pi*5.2*t)
            s=(np.sin(p)+.22*np.sin(2*p)+.08*np.sin(3*p))*envelope(t,.11,.65)*np.exp(-.5*t)
            wet(s,start+(.25+k*1.0)*BEAT,(-1)**k*.36,.055 if bar<16 else .073)
    # Thudding pulse, not constant loud percussion.
    for beat in ([0,2.5] if bar%4<2 else [0,1.5,3]):
        t=time(.65); ph=2*np.pi*(44*t+18*(1-np.exp(-t*18))/18)
        s=np.sin(ph)*np.exp(-t*8)*np.minimum(t/.015,1)
        wet(s,start+beat*BEAT,0,.16 if bar<16 else .2)
    # Wet metallic droplets with downward chirps and long echoes.
    for _ in range(2+(bar%3==0)):
        t=time(.53); f=rng.uniform(460,1400); ph=2*np.pi*(f*t+f*.65*(1-np.exp(-t*23))/23)
        s=(np.sin(ph)+.19*np.sin(1.64*ph))*np.exp(-t*12)*np.minimum(t/.006,1)
        wet(s,start+rng.uniform(.05,2.95),rng.uniform(-.85,.85),rng.uniform(.018,.043))
    if bar%4==3:
        t=time(2.7); noise=sosfilt(butter(2,[90,900],fs=SR,btype='bandpass',output='sos'),rng.standard_normal(len(t)))
        wet(noise*envelope(t,1.5,1.1),start+.1,.1,.08)
# Circular filtered water-like rumble, stereo difference, soft dynamics.
for channel in (L,R):
    n=rng.standard_normal(N).astype(np.float32); sos=butter(2,[95,500],fs=SR,btype='bandpass',output='sos')
    n=sosfilt(sos,np.r_[n[-SR:],n])[SR:]; channel+=n*.047
    # small 1 ms edge correction for exact wrap (all musical tails already wrap).
    w=220; drift=channel[0]-channel[-1]; channel[-w:]+=np.linspace(0,drift,w,dtype=np.float32)
a=np.stack([L,R],axis=1); a=np.tanh(a*1.8); a*=.77/max(np.max(np.abs(a)),1e-8)
out=Path(sys.argv[1] if len(sys.argv)>1 else 'src/main/resources/assets/fantasybosses/sounds/music/radicion_undercurrent.ogg').resolve(); out.parent.mkdir(parents=True,exist_ok=True)
with tempfile.TemporaryDirectory() as td:
    wav=Path(td)/'mix.wav'; write(wav,SR,(a*32767).astype(np.int16))
    subprocess.run(['ffmpeg','-v','error','-y','-i',str(wav),'-c:a','libvorbis','-q:a','5','-metadata','title=Radicion - Under the Roots','-metadata','artist=Original procedural score','-metadata','LOOPSTART=0','-metadata',f'LOOPLENGTH={N}',str(out)],check=True)
print(out)
