#!/usr/bin/env python3
"""«Hush Till Six» — звук трейлера YouTube на других языках под английский монтаж (для мультиязычного звука YouTube:
к одному ролику — дорожки на разных языках, каждая ровно той же длины, что ролик).
Музыка, звуки и склейки — как в английском ролике; реплики игры на этом языке с теми же эффектами (audio.py).

  python3 tools/trailer/dub.py TIMELINE_EN.json ru|es|pt|de OUT.wav
  python3 tools/trailer/dub.py TIMELINE_EN.json en OUT.wav ORIGINAL.wav   # английская — звук самого ролика, обрезан ровно по кадрам

Реплика начинается там же, где английская. Длиннее своего места (до следующей реплики или удара) — сначала начинается раньше
(до 0,3 с, не раньше своего кадра, предыдущей реплики и звука перед ней в кадре — звонок лифта), потом ускоряется без смены высоты голоса (atempo, не больше ×1.25). Правила (dg08) режутся по словам языка: разрез — в самом тихом месте перед словом.
Длина дорожки — ровно кадры / 24 (английский YouTube: 2346 кадров = 97,75 с), 48 кГц, в конце — затухание 0,2 с.
"""
import copy, json, os, subprocess, sys, tempfile
sys.dont_write_bytecode = True   # без __pycache__ в репозитории
import numpy as np
HERE = os.path.dirname(os.path.abspath(__file__)); sys.path.insert(0, HERE)
import audio as A
ROOT, SR = A.ROOT, A.SR

# начало каждого правила в реплике сменщика dg08: (слово, какое по счёту совпадение в реплике)
SPLIT = {'ru': [('Сиди', 0), ('дверь', 0), ('на', 0)], 'en': [('Sit', 0), ("don't", 1), ("don't", 2)],
         'es': [('Quédate', 0), ('no', 1), ('no', 2)], 'pt': [('Fica', 0), ('não', 1), ('não', 2)], 'de': [('Bleib', 0), ('mach', 0), ('antworte', 0)]}
HARD_SFX = {'boom', 'jumpscare', 'scare', 'buzz', 'knock'}   # удары: реплика на них не заходит
HARD_MARKS = ('deaths', 'silence', 'title', 'stinger', 'end')
GAP, MAXT, EARLY = .12, 1.25, .3

def timing(L): return json.load(open(os.path.join(ROOT, 'timing.json' if L == 'ru' else f'timing_{L}.json')))
def vfile(L, k): return os.path.join(ROOT, ('snd/' if L == 'ru' else f'snd/{L}/') + k + '.mp3')

def decode(path, extra=(), sr=SR):
    raw = subprocess.run(['ffmpeg', '-v', 'error', '-i', path, *extra, '-f', 'f32le', '-ac', '2', '-ar', str(sr), '-'], capture_output=True, check=True).stdout
    return np.frombuffer(raw, '<f4').reshape(-1, 2).astype(np.float64)

def quiet_before(x, t, back=.5, fwd=.05):   # самое тихое место (окно 30 мс) незадолго до слова
    m = x.mean(1); w, hop = int(.03 * SR), int(.005 * SR)
    i0, i1 = max(0, int((t - back) * SR)), min(len(m) - w, int((t + fwd) * SR))
    best = min(range(i0, max(i0 + 1, i1), hop), key=lambda i: float(np.sum(m[i:i + w] ** 2)))
    return (best + w / 2) / SR

def rule_cuts(L, tim):   # секунды разрезов dg08 на этом языке (3 разреза → 4 куска: вступление и три правила)
    w, x, cuts = tim['dg08']['w'], decode(vfile(L, 'dg08')), []
    for word, n in SPLIT[L]:
        hits = [t for t, s in w if s.strip('.,!?…«»"“”').lower() == word.lower()]
        cuts.append(quiet_before(x, hits[n]))
    return cuts, len(x) / SR

def write_wav(y, path, sr=SR):
    pcm = (np.clip(y, -1, 1) * 32767).astype('<i2').tobytes()
    subprocess.run(['ffmpeg', '-v', 'error', '-y', '-f', 's16le', '-ar', str(sr), '-ac', '2', '-i', '-', path], input=pcm, check=True)

def exact(src, out, dur, sr=48000):   # ровно dur секунд (обрезать или добить тишиной), 48 кГц как в ролике, затухание 0,2 с в конце
    y = decode(src, sr=sr); n = int(round(dur * sr))
    y = np.concatenate([y, np.zeros((max(0, n - len(y)), 2))])[:n]; f = int(.2 * sr); y[-f:] *= np.linspace(1, 0, f)[:, None]
    write_wav(y, out, sr)

def dub(TL, L, out):
    tim, ru = timing(L), timing('ru')
    cuts, dg_len = rule_cuts(L, tim); pieces = [0.0] + cuts + [dg_len]
    hard = sorted([s['at'] for s in TL['sfx'] if s['n'] in HARD_SFX] + [TL['marks'][m] for m in HARD_MARKS if m in TL['marks']]
                  + [TL['marks']['scare'] + next((s['scareAt'] for s in TL['segs'] if s.get('scareAt')), .75)])
    vos = sorted(TL['vo'], key=lambda v: v['at']); tmp = tempfile.mkdtemp(prefix='dub_'); new, dg_i, prev_end = [], 0, 0.0
    seg0 = {s['id']: s['start'] for s in TL['segs']}
    for i, v in enumerate(vos):
        k, rate = v['k'], v.get('rate', 1.0)
        if k == 'dg08': a, b = pieces[dg_i], pieces[dg_i + 1]; dg_i += 1
        else: a, b = 0.0, None
        src = decode(vfile(L, k)); b = len(src) / SR if b is None else b
        d = (b - a) / rate
        nxt = vos[i + 1]['at'] - GAP if i + 1 < len(vos) else TL['dur']
        nh = next((h for h in hard if h > v['at'] + .05), TL['dur'])
        end = min(nxt, nh - .1); at = v['at']
        cue = max([s['at'] + .15 for s in TL['sfx'] if seg0[v['seg']] <= s['at'] < at] or [0])   # звук кадра перед репликой (звонок лифта) — не накрывать
        if d > end - at: at = max(at - EARLY, seg0[v['seg']] + .05, prev_end + GAP, cue, at - (d - (end - at)))   # длинная — начать чуть раньше
        avail = end - at
        tempo = 1.0 if d <= avail else min(d / avail, MAXT)
        y = src[int(round(a * SR)):int(round(b * SR))].copy()
        if tempo > 1.0:   # быстрее без смены высоты: atempo через ffmpeg
            p0 = os.path.join(tmp, f'{i}_in.wav'); write_wav(y, p0); y = decode(p0, ['-af', f'atempo={tempo:.4f}'])
        f = min(len(y) // 4, int(.015 * SR)); y[:f] *= np.linspace(0, 1, f)[:, None]; y[-f:] *= np.linspace(1, 0, f)[:, None]
        p = os.path.join(tmp, f'{i}_{k}.wav'); write_wav(y, p)
        m = tim.get(k) or {}; who = (ru.get(k) or {}).get('who') or 'ИТАН'
        nv = dict(v, at=round(at, 4), file=p, to=None, dur=len(y) / SR / rate, g=m.get('g', 1), aloud=who != 'ИТАН' or (m.get('t') or '').strip().startswith('—'))
        nv.pop('from', None)   # кусок уже вырезан — audio.py режет только при from/to
        spill = nv['dur'] - avail
        print(f'  {k:5s} {v["at"]:6.2f} с  {L} {d:5.2f} с  место {avail:5.2f} с' + (f'  раньше на {v["at"] - at:.2f} с' if at < v['at'] - .005 else '') + (f'  ×{tempo:.2f}' if tempo > 1 else '') + (f'  ← заходит на {spill:.2f} с' if spill > .01 else ''))
        new.append(nv); prev_end = at + nv['dur']
    T2 = copy.deepcopy(TL); T2['vo'] = new; T2['lang'] = L
    raw = out + '.full.wav'; A.build_mix(T2, raw)
    exact(raw, out, TL['frames'] / TL['fps']); os.remove(raw)
    for fn in os.listdir(tmp): os.remove(os.path.join(tmp, fn))
    os.rmdir(tmp)

if __name__ == '__main__':
    TL = json.load(open(sys.argv[1])); L, out = sys.argv[2], sys.argv[3]
    if L == 'en': exact(sys.argv[4], out, TL['frames'] / TL['fps'])
    else: dub(TL, L, out)
    y = decode(out, sr=48000); print(out, f'{len(y) / 48000:.3f} с')
