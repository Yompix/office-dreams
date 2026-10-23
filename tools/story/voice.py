#!/usr/bin/env python3
"""Озвучка «Night Shift» пакетами (ElevenLabs eleven_v4) и нарезка пакетов на реплики по распознанной речи.

  python3 tools/story/voice.py plan ru|en            → tools/story/batches_<lang>.json: пакеты по голосам (текст для генерации, ключи)
  python3 tools/story/voice.py split ru|en N file.mp3 → режет пакет N на реплики: snd/<key>.mp3 (ru) или snd/en/<key>.mp3,
                                                        тайминг слов — в timing.json / timing_en.json (who и fx — из lines.json)
  python3 tools/story/voice.py check ru|en           → каких реплик ещё нет

Реплики — tools/story/lines.json (собирается tools/story/lines.py). Нужен faster-whisper (pip install faster-whisper).
Голоса — ниже, VOICES. Пакет = подряд идущие реплики одного голоса через пустую строку; нарезка не зависит от пауз:
распознаём слова с временем и сопоставляем с текстом реплик."""
import json, os, re, subprocess, sys, difflib
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
GAME = os.path.join(ROOT, 'public/games/nightshift')
LINES = json.load(open(os.path.join(ROOT, 'tools/story/lines.json')))
VOICES = {
  'ru': {'ИТАН': '9fof2VPjlqYRNgXCv1cT', 'СМЕНЩИК': '26NxJyRo8NCmbvY1KNWp', 'ГАС': 'FXmXRAbqIOvSE0Op0Jo0', 'ЛИЛИ': 'CamFZN2KIaqnyvcaFe3E',
         'ФРЭНК': '9AjtU6o19uipv7QL8dLL', '???': 'SkGOoi8yfK3ONMNKMKj0'},
  'en': {'ИТАН': 'hryj0YLzRd2iGghnwoOI', 'СМЕНЩИК': 'DeWSKTpgZpo6WQf8rQFY', 'ГАС': 'KgUSWQPFmuiZ5ycRbnty', 'ЛИЛИ': 'CamFZN2KIaqnyvcaFe3E',
         'ФРЭНК': 'wcATjh8zBDfepqUbl99V', '???': '2tTjAGX0n5ajDmazDcWk'},
}
SKIP = {'ch_katya', 'ch_monster'}   # плач и хрип — звуковые эффекты, не речь
SKIP_WHO = {'ГОЛОС'}                 # голос матери Фрэнка — отдельно (свой голос, сделанный в Voice Design)
MAXC = 1250                          # символов в пакете (≈ кредитов за генерацию)
def say_text(s):   # что читает голос: без тире реплики и без «(…)»
    return re.sub(r'^\s*—\s*', '', s).strip()
ONLY = os.environ.get('ONLY')        # ONLY='^n\d\d$' — озвучить только эти ключи (добавка к готовому)
BATCH = os.environ.get('BATCH', '')  # BATCH=n — свой файл пакетов batches_<lang>_n.json (не затирать основной)
def bfile(lang): return os.path.join(ROOT, f'tools/story/batches_{lang}{"_" + BATCH if BATCH else ""}.json')
def tfile(lang): return os.path.join(GAME, 'timing.json' if lang == 'ru' else 'timing_en.json')
def sdir(lang): return os.path.join(GAME, 'snd' if lang == 'ru' else 'snd/en')

def plan(lang):
    by = {}
    for k, L in LINES.items():
        if k in SKIP or L['who'] in SKIP_WHO or (ONLY and not re.match(ONLY, k)): continue
        by.setdefault(L['who'], []).append(k)
    out = []
    for who, keys in by.items():
        cur, n = [], 0
        for k in keys:
            t = f"[{LINES[k]['tag']}] {say_text(LINES[k][lang])}"
            if cur and (n + len(t) > MAXC or len(cur) >= 16): out.append({'who': who, 'voice': VOICES[lang][who], 'keys': cur}); cur, n = [], 0
            cur.append(k); n += len(t) + 18
        if cur: out.append({'who': who, 'voice': VOICES[lang][who], 'keys': cur})
    for b in out:   # тег интонации — только когда он меняется (теги тоже стоят кредитов); между репликами — пустая строка
        parts, prev = [], None
        for k in b['keys']:
            tg = LINES[k]['tag']; parts.append((f'[{tg}] ' if tg != prev else '') + say_text(LINES[k][lang])); prev = tg
        b['prompt'] = '\n\n'.join(parts)
        b['chars'] = len(b['prompt'])
    json.dump(out, open(bfile(lang), 'w'), ensure_ascii=False, indent=1)
    for i, b in enumerate(out): print(i, b['who'], len(b['keys']), 'реплик', b['chars'], 'симв.')
    print('итого символов:', sum(b['chars'] for b in out))

# --- нарезка ---
NUM = {'ru': {'газ': 'гас', '13': 'тринадцать', '12': 'двенадцать', '707': 'семьсотседьмого', '3': 'три', '6': 'шесть', '40': 'сорок', '300': 'триста'},
       'en': {'13': 'thirteen', '12': 'twelve', '3': 'three', '6': 'six', '707': 'sevenohseven', '40': 'forty', '200': 'two', '540': 'fiveforty'}}
def norm(w, lang):
    w = w.lower().replace('ё', 'е').replace('’', "'")
    w = re.sub(r"[^a-zа-я0-9']", '', w)
    return NUM[lang].get(w, w)
def toks(text):
    return [p for p in re.sub(r'[.,:;—?!…«»"()\-]', ' ', text).split() if p]

def onset(pcm, g, lo, sr=16000):
    """Начало речи: от догадки g назад до первой тишины ≥ 0,12 с (не раньше lo). Нужна, когда первое слово
    whisper не расслышал и его время угадано — иначе начало реплики обрезается."""
    import numpy as np
    f = int(.01 * sr); i = int(g * sr) // f; i0 = max(0, int(lo * sr) // f); quiet = 0
    rms = lambda k: float(np.sqrt(np.mean(pcm[k * f:(k + 1) * f] ** 2))) if (k + 1) * f <= len(pcm) else 0.
    thr = max(.006, .08 * max(rms(k) for k in range(i, min(i + 60, len(pcm) // f))) if i < len(pcm) // f else .006)
    while i > i0:
        if rms(i - 1) < thr:
            quiet += 1
            if quiet >= 12: return (i - 1 + quiet) * f / sr
        else: quiet = 0
        i -= 1
    return max(lo, (i + quiet) * f / sr)

def split(lang, idx, src, hints=()):
    import numpy as np
    from faster_whisper import WhisperModel
    B = json.load(open(bfile(lang)))[idx]; keys = B['keys']
    pcm = np.frombuffer(subprocess.run(['ffmpeg', '-v', 'error', '-i', src, '-f', 's16le', '-ac', '1', '-ar', '16000', '-'], capture_output=True, check=True).stdout, np.int16).astype(np.float32) / 32768
    model = WhisperModel('base.en' if lang == 'en' else 'small', device='cpu', compute_type='int8')
    segs, _ = model.transcribe(pcm, word_timestamps=True, language=lang)
    hw = [(norm(w.word, lang), w.start, w.end) for s in segs for w in s.words if norm(w.word, lang)]
    dur = len(pcm) / 16000
    ref, owner, words = [], [], []
    for i, k in enumerate(keys):
        for p in toks(LINES[k][lang]):
            if norm(p, lang): ref.append(norm(p, lang)); owner.append(i); words.append(p)
    sm = difflib.SequenceMatcher(None, ref, [h[0] for h in hw], autojunk=False)
    at = [None] * len(ref)
    for a, b, n in sm.get_matching_blocks():
        for j in range(n): at[a + j] = hw[b + j][1:]
    # подсказки вручную: ключ=начало-конец (секунды пакета) — для реплик, которых whisper не услышал
    for h in hints:
        k, r = h.split('='); a, b = (float(x) for x in r.split('-')); i = keys.index(k); js = [j for j in range(len(ref)) if owner[j] == i]
        for n, j in enumerate(js): at[j] = (a + (b - a) * n / len(js), a + (b - a) * (n + 1) / len(js))
    known = [i for i, x in enumerate(at) if x]
    print(f'совпало слов {len(known)}/{len(ref)}')
    for i in range(len(at)):   # пропущенные слова — между соседними известными
        if at[i]: continue
        L = max([j for j in known if j < i], default=None); R = min([j for j in known if j > i], default=None)
        t0 = at[L][1] if L is not None else 0; t1 = at[R][0] if R is not None else dur
        lo = L if L is not None else -1; hi = R if R is not None else len(at); f = (i - lo) / (hi - lo)
        at[i] = (t0 + (t1 - t0) * f, t0 + (t1 - t0) * f)
    first = [min(j for j in range(len(ref)) if owner[j] == i) for i in range(len(keys))]
    last = [max(j for j in range(len(ref)) if owner[j] == i) for i in range(len(keys))]
    bad = [keys[i] for i in range(len(keys)) if not any(first[i] <= j <= last[i] for j in known)]
    if bad: sys.exit(f'не распознаны реплики {bad} — проверь пакет (возможно, голос пропустил строку)')
    cuts = [0.0] + [(at[last[i]][1] + at[first[i + 1]][0]) / 2 if at[first[i + 1]][0] > at[last[i]][1] else at[last[i]][1] for i in range(len(keys) - 1)] + [dur]
    os.makedirs(sdir(lang), exist_ok=True)
    tf = tfile(lang); tim = json.load(open(tf)) if os.path.exists(tf) else {}
    heardK = {first[i] for i in range(len(keys)) if at[first[i]] and any(abs(h[1] - at[first[i]][0]) < .02 for h in hw)}
    for i, k in enumerate(keys):
        g = at[first[i]][0]
        if first[i] not in heardK and not any(h.startswith(k + '=') for h in hints):   # первое слово не расслышано — ищем начало по тишине
            g = onset(pcm, g + .05, at[last[i - 1]][1] + .05 if i else 0)
            cuts[i] = min(cuts[i], g - .04)
        s0 = max(cuts[i], g - .12); s1 = min(cuts[i + 1], at[last[i]][1] + .3)
        subprocess.run(['ffmpeg', '-v', 'error', '-y', '-ss', f'{s0:.3f}', '-to', f'{s1:.3f}', '-i', src, '-ac', '1', '-ar', '44100', '-b:a', '64k',   # отрезок до фильтров — иначе затухания ложатся на края всего пакета
                        '-af', 'afade=t=in:d=0.02,areverse,afade=t=in:d=0.06,areverse', os.path.join(sdir(lang), k + '.mp3')], check=True)
        L = LINES[k]
        tim[k] = {'t': L[lang], 'dur': round(s1 - s0, 2), 'w': [[round(max(0, at[j][0] - s0), 2), words[j]] for j in range(first[i], last[i] + 1)], 'who': L['who']}
        if L['fx']: tim[k]['fx'] = L['fx']
        heard = ' '.join(h[0] for h in hw if s0 <= h[1] < s1)
        print(f'{k:6s} {s1 - s0:5.2f} с | {heard[:80]}')
    json.dump(dict(sorted(tim.items())), open(tf, 'w'), ensure_ascii=False)

def check(lang):
    tf = tfile(lang); tim = json.load(open(tf)) if os.path.exists(tf) else {}
    miss = [k for k in LINES if k not in SKIP and (k not in tim or not os.path.exists(os.path.join(sdir(lang), k + '.mp3')))]
    print('нет:', miss if miss else 'всё на месте')

if __name__ == '__main__':
    cmd, lang = sys.argv[1], sys.argv[2]
    if cmd == 'plan': plan(lang)
    elif cmd == 'split': split(lang, int(sys.argv[3]), sys.argv[4], sys.argv[5:])   # … [ключ=начало-конец …]
    elif cmd == 'check': check(lang)
