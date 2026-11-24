#!/usr/bin/env python3
"""Нарезка пакета озвучки для языков без пробелов (японский; так же подойдёт китайский): сопоставление по символам.
  python3 tools/story/voice_cjk.py ja N file.mp3   → web/snd/ja/<ключ>.mp3 и web/timing_ja.json (t, dur, w — куски текста с временем, who, fx)
Пакеты — tools/story/batches_<язык>.json (voice.py plan ja). Потом: python3 tools/story/voice.py loud ja (громкость).
Куски w режутся так же, как tools/i18n/build.js cjkWords (менять вместе), — по ним движок показывает субтитры и двигает губы."""
import json, os, re, subprocess, sys, difflib
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import voice as V

CJK_O = '「『（(《〈【'; CJK_P = '、。，．！？…」』）)》〉】：；!?,.'
def cjk_words(t):   # = build.js cjkWords
    s = re.sub(r'\s+', '', t); out = []; cur = ''
    for ch in s:
        if ch in CJK_O and cur and cur[-1] not in CJK_O: out.append(cur); cur = ''
        cur += ch
        if ch in CJK_P: out.append(cur); cur = ''
    if cur: out.append(cur)
    merged = []
    for w in out:
        if merged and all(c in CJK_P for c in w): merged[-1] += w
        else: merged.append(w)
    res = []
    for w in merged:
        a = list(w); n = -(-len(a) // 9); k = -(-len(a) // n)
        res += [''.join(a[i:i + k]) for i in range(0, len(a), k)]
    return res

KEEP = re.compile(r'[\w぀-ヿ㐀-鿿]')
def chars(t): return [c for c in t if KEEP.match(c) and c not in '_']

def main(lang, idx, src):
    import numpy as np
    from faster_whisper import WhisperModel
    B = json.load(open(V.bfile(lang)))[idx]; keys = B['keys']
    pcm = np.frombuffer(subprocess.run(['ffmpeg', '-v', 'error', '-i', src, '-f', 's16le', '-ac', '1', '-ar', '16000', '-'], capture_output=True, check=True).stdout, np.int16).astype(np.float32) / 32768
    dur = len(pcm) / 16000
    segs, _ = WhisperModel('small', device='cpu', compute_type='int8').transcribe(pcm, word_timestamps=True, language=lang)
    hyp = []   # (символ, время)
    for s in segs:
        for w in s.words:
            cs = chars(w.word)
            for i, c in enumerate(cs): hyp.append((c, w.start + (w.end - w.start) * i / max(1, len(cs)), w.start + (w.end - w.start) * (i + 1) / max(1, len(cs))))
    texts = [re.sub(r'^\s*—\s*', '', V.LINES[k][lang]).strip() for k in keys]
    ref, owner = [], []
    for i, t in enumerate(texts):
        for c in chars(t): ref.append(c); owner.append(i)
    sm = difflib.SequenceMatcher(None, ref, [h[0] for h in hyp], autojunk=False)
    at = [None] * len(ref)
    for a, b, n in sm.get_matching_blocks():
        for j in range(n): at[a + j] = hyp[b + j][1:]
    known = [i for i, x in enumerate(at) if x]
    print(f'совпало символов {len(known)}/{len(ref)}')
    for i in range(len(at)):
        if at[i]: continue
        L = max([j for j in known if j < i], default=None); R = min([j for j in known if j > i], default=None)
        t0 = at[L][1] if L is not None else 0; t1 = at[R][0] if R is not None else dur
        lo = L if L is not None else -1; hi = R if R is not None else len(at); f = (i - lo) / (hi - lo)
        at[i] = (t0 + (t1 - t0) * f, t0 + (t1 - t0) * f)
    first = [owner.index(i) for i in range(len(keys))]; last = [len(owner) - 1 - owner[::-1].index(i) for i in range(len(keys))]
    weak = [keys[i] for i in range(len(keys)) if sum(1 for j in range(first[i], last[i] + 1) if j in known) < max(2, .25 * (last[i] - first[i] + 1))]
    if weak: print('мало совпало (проверь на слух):', weak)
    cuts = [0.0] + [(at[last[i]][1] + at[first[i + 1]][0]) / 2 if at[first[i + 1]][0] > at[last[i]][1] else at[last[i]][1] for i in range(len(keys) - 1)] + [dur]
    sd = V.sdir(lang); os.makedirs(sd, exist_ok=True)
    tf = V.tfile(lang); tim = json.load(open(tf)) if os.path.exists(tf) else {}
    for i, k in enumerate(keys):
        g = V.onset(pcm, at[first[i]][0] + .05, at[last[i - 1]][1] + .05 if i else 0)
        s0 = max(cuts[i], min(g, at[first[i]][0]) - .12); s1 = min(cuts[i + 1], at[last[i]][1] + .3)
        subprocess.run(['ffmpeg', '-v', 'error', '-y', '-ss', f'{s0:.3f}', '-to', f'{s1:.3f}', '-i', src, '-ac', '1', '-ar', '44100', '-b:a', '64k',
                        '-af', 'afade=t=in:d=0.02,areverse,afade=t=in:d=0.06,areverse', os.path.join(sd, k + '.mp3')], check=True)
        L = V.LINES[k]; ws = cjk_words(texts[i]); w = []; pos = first[i]
        for chunk in ws:   # время куска = время его первого «настоящего» символа
            n = len(chars(chunk)); w.append([round(max(0, at[min(pos, last[i])][0] - s0), 2), chunk]); pos += n
        tim[k] = {'t': L[lang], 'dur': round(s1 - s0, 2), 'w': w, 'who': L['who']}
        if L['fx']: tim[k]['fx'] = L['fx']
        heard = ''.join(h[0] for h in hyp if s0 <= h[1] < s1)
        print(f'{k:6s} {s1 - s0:5.2f} с | {heard[:40]}')
    for a, (k0, who, fx) in V.ALIAS.items():
        if k0 in keys:
            subprocess.run(['cp', os.path.join(sd, k0 + '.mp3'), os.path.join(sd, a + '.mp3')], check=True); tim[a] = dict(tim[k0], who=who, fx=fx)
    for k in ('ch_katya', 'ch_monster'):   # плач и хрип — без слов: английские записи
        f = os.path.join(sd, k + '.mp3')
        if not os.path.exists(f) and os.path.exists(os.path.join(V.sdir('en'), k + '.mp3')): subprocess.run(['cp', os.path.join(V.sdir('en'), k + '.mp3'), f], check=True)
    json.dump(dict(sorted(tim.items())), open(tf, 'w'), ensure_ascii=False)

if __name__ == '__main__':
    main(sys.argv[1], int(sys.argv[2]), sys.argv[3])
