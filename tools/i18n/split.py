#!/usr/bin/env python3
"""Нарезка пакета озвучки (несколько реплик подряд с длинными паузами) на отдельные файлы + тайминги слов.
   python3 tools/i18n/split.py batch.mp3 key1,key2,... [out_dir] — пишет out_dir/<key>.mp3 и обновляет timing_en.json.
   Режет по N−1 самым длинным паузам; время слов — по длине слов с запасом на знаки препинания (для подсветки субтитров)."""
import json, re, subprocess, sys, os
src, keys = sys.argv[1], sys.argv[2].split(',')
out = sys.argv[3] if len(sys.argv) > 3 else 'public/games/nightshift/snd/en'
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
TXT = json.load(open(os.path.join(ROOT, 'tools/i18n/voice_en.json')))
TIMF = os.path.join(ROOT, 'public/games/nightshift/timing_en.json')
RU = json.load(open(os.path.join(ROOT, 'public/games/nightshift/timing.json')))
def silences(f, db=-38, d=.35):
    r = subprocess.run(['ffmpeg', '-hide_banner', '-i', f, '-af', f'silencedetect=noise={db}dB:d={d}', '-f', 'null', '-'], capture_output=True, text=True).stderr
    st = [float(x) for x in re.findall(r'silence_start: ([\d.]+)', r)]; en = [float(x) for x in re.findall(r'silence_end: ([\d.]+)', r)]
    dur = float(re.search(r'Duration: (\d+):(\d+):([\d.]+)', r).groups()[2]) + 60 * float(re.search(r'Duration: (\d+):(\d+)', r).groups()[1])
    return list(zip(st, en + [dur] * (len(st) - len(en)))), dur
sil, dur = silences(src)
inner = [(a, b) for a, b in sil if a > .05 and b < dur - .05]
n = len(keys)
if len(inner) < n - 1: sys.exit(f'мало пауз: {len(inner)} < {n - 1}')
top = sorted(inner, key=lambda s: s[1] - s[0], reverse=True)
cut, rest = top[:n - 1], top[n - 1:]
lo = min(b - a for a, b in cut); hi = max([b - a for a, b in rest] or [0])
print(f'паузы между репликами ≥ {lo:.2f} с, внутри реплик ≤ {hi:.2f} с')
if rest and lo < hi * 1.25: print('ВНИМАНИЕ: паузы плохо различимы — проверь нарезку')
cut = sorted(cut)
bounds = [0] + [(a + b) / 2 for a, b in cut] + [dur]
os.makedirs(out, exist_ok=True)
tim = json.load(open(TIMF)) if os.path.exists(TIMF) else {}
for i, k in enumerate(keys):
    a, b = bounds[i], bounds[i + 1]
    # обрезаем тишину по краям сегмента (оставляем 60 мс)
    seg_sil = [(x, y) for x, y in sil if y > a and x < b]
    s0, s1 = a, b
    for x, y in seg_sil:
        if x <= a + .01: s0 = max(s0, y - .06)
        if y >= b - .01: s1 = min(s1, x + .08)
    f = os.path.join(out, k + '.mp3')
    subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', src, '-ss', f'{s0:.3f}', '-to', f'{s1:.3f}', '-ac', '1', '-ar', '44100', '-b:a', '64k',
                    '-af', 'afade=t=in:d=0.02,areverse,afade=t=in:d=0.04,areverse', f], check=True)
    d = s1 - s0
    text = TXT[k]
    # слова и их вес: длина + пауза после точки/запятой
    parts = []
    for tok in text.split():
        sub = [p for p in re.sub(r'[.,:—?!…«»]', ' ', tok).split() if p]
        for j, p in enumerate(sub): parts.append([p, len(p) + 1 + (8 if j == len(sub) - 1 and re.search(r'[.?!…]["»]?$', tok) else 4 if j == len(sub) - 1 and re.search(r'[,:—]$', tok) else 0)])
    tot = sum(w for _, w in parts) or 1; t = .05; w = []
    for p, wt in parts: w.append([round(t, 2), p]); t += (d - .15) * wt / tot
    tim[k] = {'t': text, 'dur': round(d, 2), 'w': w}
    if RU.get(k, {}).get('who'): tim[k]['who'] = RU[k]['who']
    print(k, f'{d:.2f} с', text[:60])
json.dump(tim, open(TIMF, 'w'), ensure_ascii=False)
