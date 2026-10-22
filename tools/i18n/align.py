#!/usr/bin/env python3
"""Нарезка пакета озвучки по распознанной речи (надёжнее, чем по паузам).
   python3 tools/i18n/align.py batch.mp3 key1,key2,... [out_dir]
   Нужен faster-whisper (pip install faster-whisper). Распознаёт слова с временем, сопоставляет с текстами
   из voice_en.json, режет между последним словом реплики и первым словом следующей, пишет <key>.mp3
   и настоящие тайминги слов в timing_en.json."""
import json, re, subprocess, sys, os, difflib
from faster_whisper import WhisperModel
src, keys = sys.argv[1], sys.argv[2].split(',')
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
out = sys.argv[3] if len(sys.argv) > 3 else os.path.join(ROOT, 'public/games/nightshift/snd/en')
TXT = json.load(open(os.path.join(ROOT, 'tools/i18n/voice_en.json')))
TIMF = os.path.join(ROOT, 'public/games/nightshift/timing_en.json')
RU = json.load(open(os.path.join(ROOT, 'public/games/nightshift/timing.json')))
NUM = {'13': 'thirteen', '3': 'three', '6': 'six', '7': 'seven'}
def norm(w):
    w = re.sub(r"[^a-z0-9']", '', w.lower().replace('’', "'"))
    return NUM.get(w, w)
def toks(text):  # слова текста реплики (для субтитров) + нормализованные
    r = []
    for tok in text.split():
        for p in re.sub(r'[.,:—?!…«»"]', ' ', tok).split():
            if norm(p): r.append(p)
    return r
import numpy as np  # декодируем ffmpeg'ом сами (av в некоторых сборках несовместим)
pcm = np.frombuffer(subprocess.run(['ffmpeg', '-v', 'error', '-i', src, '-f', 's16le', '-ac', '1', '-ar', '16000', '-'], capture_output=True, check=True).stdout, np.int16).astype(np.float32) / 32768
segs, info = WhisperModel('base.en', device='cpu', compute_type='int8').transcribe(pcm, word_timestamps=True, language='en')
hw = [(norm(w.word), w.start, w.end) for s in segs for w in s.words if norm(w.word)]
dur = len(pcm) / 16000
ref, owner = [], []
for i, k in enumerate(keys):
    for p in toks(TXT[k]): ref.append(norm(p)); owner.append(i)
sm = difflib.SequenceMatcher(None, ref, [h[0] for h in hw], autojunk=False)
at = [None] * len(ref)  # ref-индекс → (start, end)
for a, b, n in sm.get_matching_blocks():
    for j in range(n): at[a + j] = hw[b + j][1:]
miss = sum(x is None for x in at)
print(f'совпало слов {len(ref) - miss}/{len(ref)}')
# интерполяция пропущенных слов
known = [i for i, x in enumerate(at) if x]
for i in range(len(at)):
    if at[i]: continue
    L = max([j for j in known if j < i], default=None); R = min([j for j in known if j > i], default=None)
    t0 = at[L][1] if L is not None else 0; t1 = at[R][0] if R is not None else dur
    lo = L if L is not None else -1; hi = R if R is not None else len(at)
    f0 = (i - lo) / (hi - lo)
    at[i] = (t0 + (t1 - t0) * f0, t0 + (t1 - t0) * f0)
first = [min(j for j in range(len(ref)) if owner[j] == i) for i in range(len(keys))]
last = [max(j for j in range(len(ref)) if owner[j] == i) for i in range(len(keys))]
for i in range(len(keys)):  # проверка: в каждой реплике должно совпасть хоть что-то
    ok = sum(1 for j in range(first[i], last[i] + 1) if j in known)
    if ok == 0: sys.exit(f'{keys[i]}: ни одно слово не распознано — проверь пакет')
cuts = [0.0]
for i in range(len(keys) - 1):
    e, s = at[last[i]][1], at[first[i + 1]][0]
    cuts.append((e + s) / 2 if s > e else e)
cuts.append(dur)
os.makedirs(out, exist_ok=True)
tim = json.load(open(TIMF)) if os.path.exists(TIMF) else {}
for i, k in enumerate(keys):
    s0 = max(cuts[i], at[first[i]][0] - .12); s1 = min(cuts[i + 1], at[last[i]][1] + .25)
    f = os.path.join(out, k + '.mp3')
    subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', src, '-ss', f'{s0:.3f}', '-to', f'{s1:.3f}', '-ac', '1', '-ar', '44100', '-b:a', '64k',
                    '-af', 'afade=t=in:d=0.02,areverse,afade=t=in:d=0.05,areverse', f], check=True)
    words = toks(TXT[k])
    w = [[round(max(0, at[j][0] - s0), 2), words[j - first[i]]] for j in range(first[i], last[i] + 1)]
    tim[k] = {'t': TXT[k], 'dur': round(s1 - s0, 2), 'w': w}
    if RU.get(k, {}).get('who'): tim[k]['who'] = RU[k]['who']
    got = ' '.join(h[0] for h in hw if s0 <= h[1] < s1)
    print(f'{k:12s} {s1 - s0:5.2f} с | {got[:70]}')
json.dump(tim, open(TIMF, 'w'), ensure_ascii=False)
