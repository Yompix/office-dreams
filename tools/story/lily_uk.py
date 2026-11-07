# Lily (HTS) Ukrainian voice: trim/compress pauses, encode, word timings, loudness vs English
import json, re, subprocess, wave, numpy as np, os, sys
S = '/tmp/claude-0/-home-user/0fa0b1a0-e4f5-529b-a67e-693eb4ab78d1/scratchpad/iris_uk'
G = '/home/user/office-dreams/public/games/nightshift'
MAP = {1:'ly04',2:'ly06',3:'ly08',4:'ly10',5:'b08',6:'b10',7:'x06',8:'x08',9:'g05',10:'g07',11:'g11',12:'g13',
       13:'b04',14:'h04',15:'k5a',16:'k9a',17:'n20',18:'n39',19:'n42',20:'ch_whisper'}
SR = 44100; FR = 441  # 10 ms
def rd(p, sr):
    raw = subprocess.run(['ffmpeg','-v','error','-i',p,'-ac','1','-ar',str(sr),'-f','s16le','-'],capture_output=True).stdout
    return np.frombuffer(raw, np.int16).astype(np.float32)/32768
def wr(p, a, sr):
    with wave.open(p,'wb') as w: w.setnchannels(1); w.setsampwidth(2); w.setframerate(sr); w.writeframes((np.clip(a,-1,1)*32767).astype(np.int16).tobytes())
def lufs(p):
    r = subprocess.run(['ffmpeg','-hide_banner','-nostats','-i',p,'-af','ebur128=peak=true','-f','null','-'],capture_output=True,text=True).stderr
    return float(re.findall(r'I:\s+(-?[\d.]+) LUFS', r)[-1])
os.makedirs(G+'/snd/uk', exist_ok=True)
tim = json.load(open(G+'/timing_uk.json'))
from faster_whisper import WhisperModel
m = WhisperModel('small', device='cpu', compute_type='int8')
rep = []
for i, k in MAP.items():
    a = rd(f'{S}/{i}.mp3', SR)
    n = len(a)//FR; e = 20*np.log10(np.sqrt((a[:n*FR].reshape(n,FR)**2).mean(1))+1e-9)
    on = e > max(e.max()-38, -50)
    idx = np.where(on)[0]; s0, s1 = max(idx[0]-8,0), min(idx[-1]+15,n)   # 80 ms in, 150 ms out
    keep = []; gap = 0
    for f in range(s0, s1):
        if on[f]: gap = 0; keep.append(f)
        else:
            gap += 1
            if gap <= 45: keep.append(f)   # pauses capped at 0.45 s
    out = np.concatenate([a[f*FR:(f+1)*FR] for f in keep])
    fade = int(.01*SR); out[:fade] *= np.linspace(0,1,fade); out[-fade:] *= np.linspace(1,0,fade)
    wr(f'{S}/{k}.wav', out, SR)
    subprocess.run(['ffmpeg','-v','error','-y','-i',f'{S}/{k}.wav','-ac','1','-ar','44100','-b:a','64k',f'{G}/snd/uk/{k}.mp3'],check=True)
    dur = round(len(out)/SR, 2)
    a16 = rd(f'{S}/{k}.wav', 16000)
    seg, _ = m.transcribe(a16, language='uk', word_timestamps=True)
    ww = [w for sg in seg for w in sg.words]
    t0, t1 = (ww[0].start, ww[-1].end) if ww else (.08, dur-.15)
    words = re.findall(r"[\w’'-]+", tim[k]['t']); L = sum(len(x)+1 for x in words); c = 0; w = []
    for x in words: w.append([round(t0 + (t1-t0)*c/L, 2), x]); c += len(x)+1
    en = f'{G}/snd/en/{k}.mp3'
    g_en = tim[k].get('g', 1); I_en = lufs(en) + 20*np.log10(g_en); I_uk = lufs(f'{G}/snd/uk/{k}.mp3')
    db = max(-12, min(12, I_en - I_uk)); g = round(10**(db/20), 2)
    old = tim[k]['dur']
    tim[k].update(dur=dur, w=w, vl='uk'); tim[k].pop('ew', None); tim[k].pop('g', None)
    if abs(db) >= .3: tim[k]['g'] = g
    rep.append(f'{i:2} {k:10} {old:5} -> {dur:5}  g={tim[k].get("g",1)}  heard: {" ".join(x.word for x in ww)}')
json.dump(tim, open(G+'/timing_uk.json','w'), ensure_ascii=False, separators=(',',':'))
print('\n'.join(rep))
