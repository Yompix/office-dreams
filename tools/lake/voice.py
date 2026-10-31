#!/usr/bin/env python3
"""Голоса WHAT THE LAKE KEEPS (ElevenLabs eleven_v4 на платном тарифе владельца): пакеты по героям и нарезка на реплики.

  python3 tools/lake/voice.py plan ru|en             → tools/lake/vo_<lang>.json: пакеты (текст для генерации, реплики)
  python3 tools/lake/voice.py split ru|en N file.mp3 → режет пакет N на реплики: godot/lake/snd/vo/<lang>/<id>.ogg,
                                                       слова со временем и громкость (для губ) — snd/vo/<lang>/timing.json
  python3 tools/lake/voice.py check ru|en            → каких реплик ещё нет

Реплики — шаги "say" в godot/lake/data/ch*.json (все главы); ключ реплики — "<сцена>_<номер реплики в сцене>" (s1_a_2).
Пакет = подряд реплики одного голоса через пустую строку, у каждой — тег интонации ([scared] …). Нарезка не зависит
от пауз: распознаём слова с временем (faster-whisper) и сопоставляем с текстом реплик (как tools/story/voice.py).
Радио после нарезки — «через динамик» (полоса 300–3400 Гц, лёгкий перегруз)."""
import difflib, glob, json, os, re, subprocess, sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
GAME = os.path.join(ROOT, 'godot/lake')
DATA = os.path.join(GAME, 'data/ch1.json')
VOICES = {   # голоса из библиотеки ElevenLabs (creative_list_voices)
    'ru': {'noah': 'RvcToHi9ENVDatbeYNJe',    # Mikhail — молодой, уставший
           'iris': 'CamFZN2KIaqnyvcaFe3E',    # Lisa — мягкий, эмоциональный
           'danny': 'XjGYkUkzth8BPs29fmcV',   # Teddy — мальчик
           'roman': '9AjtU6o19uipv7QL8dLL',   # Alex — 50–60 лет, баритон с хрипотцой
           'radio': 'LTkx8oHrUXaND7aaxdTA'},  # Alexander — диктор
    'en': {'noah': 'OwEAPhTFInN6VSRT9QXm',    # Jordan — warm & gritty, horror protagonist
           'iris': 'umKoJK6tP1ALjO0zo1EE',    # Teen Girl — Chicadi
           'danny': 'XjGYkUkzth8BPs29fmcV',
           'roman': 'wcATjh8zBDfepqUbl99V',   # Rick — raspy, tired
           'radio': '9ZbFvrGBxO3uSmsw6wdI'},  # Walter — serious news anchor
}
TAGS = {   # интонация (тег eleven_v4) по ключу реплики
    's1_1': 'confused, breathless', 's1_2': 'cold, flat',
    's1_a_1': 'quiet, anxious', 's1_a_2': 'hurt, quiet', 's1_a_3': 'quietly', 's1_a_4': 'tired, resigned',
    's1_b_1': 'shaky, determined', 's1_b_2': 'screaming', 's1_b_3': 'panicked, tearful',
    's2_1': 'news announcer, neutral', 's2_b_1': 'news announcer, neutral',
    's2_c_1': 'barely audible, horrified', 's2_c_2': 'cold, quiet',
    's3_hit_1': 'crying, desperate',
    's4_1': 'nervous, distracted', 's4_b_1': 'quiet, suspicious',
    's5_1': 'gentle, soft', 's5_2': 'scared, quiet', 's5_pay_1': 'nervous',
    's5_help_1': 'trembling, desperate', 's5_help_2': 'gruff, reluctant', 's5_lapse_1': 'wary, uneasy',
    's5_lapse_2': 'urgent whisper',
    's6_a_1': 'soft, tired', 's6_a_2': 'gentle, reassuring', 's6_b_1': 'tense, firm', 's6_b_2': 'angry, pleading',
    's6_a2_1': 'defeated, quiet', 's6_leave_1': 'cold, firm',
    's7_a_1': 'forced calm', 's7_b_1': 'broken, whispering',
}


def lines():
    """Реплики всех глав (data/ch1.json, ch2.json, …): ключ — как в игре, <сцена>_<номер реплики в сцене>."""
    out = {}
    for f in sorted(glob.glob(os.path.join(GAME, 'data/ch*.json'))):
        d = json.load(open(f))
        for sid, sc in d['scenes'].items():
            n = 0
            for b in sc['beats']:
                if 'say' in b:
                    n += 1
                    k = f'{sid}_{n}'
                    out[k] = {'who': b['say'], 'ru': b.get('ru', ''), 'en': b.get('en', ''), 'tag': TAGS.get(k, 'natural')}
    return out


LINES = lines()


def say_text(s):   # что читает голос: КАПС → обычные буквы (иначе читает по буквам)
    return re.sub(r'\b([A-ZА-ЯЁ]{2,})\b', lambda m: m.group(1).capitalize(), s.strip())


BATCH = os.environ.get('BATCH', '')   # BATCH=mm — пакеты MiniMax (plan2), свой файл vo_<lang>_mm.json
def bfile(lang): return os.path.join(ROOT, f'tools/lake/vo_{lang}{"_" + BATCH if BATCH else ""}.json')
def sdir(lang): return os.path.join(GAME, f'snd/vo/{lang}')
def tfile(lang): return os.path.join(sdir(lang), 'timing.json')


def plan(lang):
    by = {}
    for k, L in LINES.items():
        by.setdefault(L['who'], []).append(k)
    out = []
    for who, keys in by.items():
        parts = [f"[{LINES[k]['tag']}] {say_text(LINES[k][lang])}" for k in keys]
        out.append({'who': who, 'voice': VOICES[lang][who], 'keys': keys, 'prompt': '\n\n'.join(parts)})
    for b in out:
        b['chars'] = len(b['prompt'])
    json.dump(out, open(bfile(lang), 'w'), ensure_ascii=False, indent=1)
    for i, b in enumerate(out):
        print(i, b['who'], len(b['keys']), 'реплик', b['chars'], 'симв.')
    print('итого символов:', sum(b['chars'] for b in out))


# Запасной движок — MiniMax Speech 2.8 HD через Figma Weave (когда кредиты ElevenLabs кончились): голос — готовый
# пресет, эмоция — на весь пакет, поэтому пакеты по (герой, эмоция); между репликами — пауза <#1.2#>.
MM_VOICES = {'noah': 'Casual_Guy', 'iris': 'Lovely_Girl', 'radio': 'Elegant_Man', 'roman': 'Deep_Voice_Man', 'danny': 'Decent_Boy'}
EMO = {'s1_1': 'fearful', 's1_a_1': 'fearful', 's1_b_1': 'fearful', 's2_c_1': 'fearful', 's5_pay_1': 'fearful', 's5_help_1': 'fearful',
       's1_a_3': 'sad', 's6_a_1': 'sad', 's6_a2_1': 'sad', 's7_b_1': 'sad', 's6_b_1': 'angry', 's6_leave_1': 'angry', 's7_a_1': 'neutral',
       's1_2': 'neutral', 's2_c_2': 'neutral', 's4_1': 'neutral', 's4_b_1': 'neutral', 's5_1': 'neutral',
       's1_a_2': 'sad', 's1_a_4': 'sad', 's6_a_2': 'sad', 's1_b_2': 'angry', 's6_b_2': 'angry',
       's1_b_3': 'fearful', 's3_hit_1': 'fearful', 's5_lapse_2': 'fearful',
       's2_1': 'neutral', 's2_b_1': 'neutral', 's5_help_2': 'neutral', 's5_lapse_1': 'neutral', 's5_2': 'fearful'}
INTERJ = {'s1_1': '(gasps) ', 's3_hit_1': '(sniffs) ', 's7_b_1': '(sighs) '}


def plan2(lang):
    """Пакеты для MiniMax: реплики, которых ещё нет в timing.json, по (герой, эмоция)."""
    tim = json.load(open(tfile(lang))) if os.path.exists(tfile(lang)) else {}
    by = {}
    for k, L in LINES.items():
        if k in tim:
            continue
        by.setdefault((L['who'], EMO.get(k, 'neutral')), []).append(k)
    out = []
    for (who, emo), keys in by.items():
        prompt = ' <#1.2#> '.join(INTERJ.get(k, '') + say_text(LINES[k][lang]) for k in keys)
        out.append({'who': who, 'voice': MM_VOICES[who], 'emotion': emo, 'keys': keys, 'prompt': prompt, 'chars': len(prompt)})
    json.dump(out, open(os.path.join(ROOT, f'tools/lake/vo_{lang}_mm.json'), 'w'), ensure_ascii=False, indent=1)
    for i, b in enumerate(out):
        print(i, b['who'], b['emotion'], b['voice'], len(b['keys']), 'реплик |', b['prompt'][:90])


NUM = {'ru': {}, 'en': {}}


def norm(w, lang):
    w = w.lower().replace('ё', 'е').replace('’', "'")
    w = re.sub(r"[^a-zа-я0-9']", '', w)
    return NUM[lang].get(w, w)


def toks(text):
    return [p for p in re.sub(r'[.,:;—?!…«»"()\-]', ' ', text).split() if p]


def envelope(src, s0, s1):
    """Громкость реплики по 40 мс (0…9) — губы открываются по голосу."""
    import numpy as np
    pcm = np.frombuffer(subprocess.run(['ffmpeg', '-nostdin', '-v', 'error', '-ss', f'{s0:.3f}', '-to', f'{s1:.3f}', '-i', src,
                                        '-f', 's16le', '-ac', '1', '-ar', '16000', '-'], capture_output=True, check=True).stdout,
                        np.int16).astype(np.float32) / 32768
    f = 640
    rms = [float(np.sqrt(np.mean(pcm[i:i + f] ** 2))) for i in range(0, max(0, len(pcm) - f + 1), f)]
    if not rms:
        return ''
    top = max(1e-4, sorted(rms)[int(len(rms) * 0.95)])
    return ''.join(str(min(9, int(9.99 * min(1.0, r / top) ** 0.7))) for r in rms)


def split(lang, idx, src):
    import numpy as np
    from faster_whisper import WhisperModel
    B = json.load(open(bfile(lang)))[idx]
    keys = B['keys']
    pcm = np.frombuffer(subprocess.run(['ffmpeg', '-nostdin', '-v', 'error', '-i', src, '-f', 's16le', '-ac', '1', '-ar', '16000', '-'],
                                       capture_output=True, check=True).stdout, np.int16).astype(np.float32) / 32768
    model = WhisperModel('base.en' if lang == 'en' else 'small', device='cpu', compute_type='int8')
    segs, _ = model.transcribe(pcm, word_timestamps=True, language=lang)
    hw = [(norm(w.word, lang), w.start, w.end) for s in segs for w in s.words if norm(w.word, lang)]
    dur = len(pcm) / 16000
    ref, owner, words = [], [], []
    for i, k in enumerate(keys):
        for p in toks(LINES[k][lang]):
            if norm(p, lang):
                ref.append(norm(p, lang)); owner.append(i); words.append(p)
    sm = difflib.SequenceMatcher(None, ref, [h[0] for h in hw], autojunk=False)
    at = [None] * len(ref)
    for a, b, n in sm.get_matching_blocks():
        for j in range(n):
            at[a + j] = hw[b + j][1:]
    known = [i for i, x in enumerate(at) if x]
    print(f'совпало слов {len(known)}/{len(ref)}; whisper: {" ".join(h[0] for h in hw)[:300]}')
    for i in range(len(at)):   # пропущенные слова — между соседними известными
        if at[i]:
            continue
        L = max([j for j in known if j < i], default=None); R = min([j for j in known if j > i], default=None)
        t0 = at[L][1] if L is not None else 0; t1 = at[R][0] if R is not None else dur
        lo = L if L is not None else -1; hi = R if R is not None else len(at); f = (i - lo) / (hi - lo)
        at[i] = (t0 + (t1 - t0) * f, t0 + (t1 - t0) * f)
    first = [min(j for j in range(len(ref)) if owner[j] == i) for i in range(len(keys))]
    last = [max(j for j in range(len(ref)) if owner[j] == i) for i in range(len(keys))]
    bad = [keys[i] for i in range(len(keys)) if not any(first[i] <= j <= last[i] for j in known)]
    if bad:
        sys.exit(f'не распознаны реплики {bad} — проверь пакет (возможно, голос пропустил строку)')
    cuts = [0.0] + [(at[last[i]][1] + at[first[i + 1]][0]) / 2 if at[first[i + 1]][0] > at[last[i]][1] else at[last[i]][1]
                    for i in range(len(keys) - 1)] + [dur]
    os.makedirs(sdir(lang), exist_ok=True)
    tf = tfile(lang)
    tim = json.load(open(tf)) if os.path.exists(tf) else {}
    radio = B['who'] == 'radio'
    for i, k in enumerate(keys):
        g = at[first[i]][0]
        s0 = max(cuts[i], g - .15)
        s1 = min(cuts[i + 1], at[last[i]][1] + .35)
        af = 'afade=t=in:d=0.02,areverse,afade=t=in:d=0.08,areverse'
        if radio:   # голос из автомагнитолы
            af += ',highpass=f=300,lowpass=f=3400,acompressor=threshold=0.1:ratio=4,volume=1.6,alimiter=limit=0.9'
        subprocess.run(['ffmpeg', '-nostdin', '-v', 'error', '-y', '-ss', f'{s0:.3f}', '-to', f'{s1:.3f}', '-i', src, '-ac', '1',
                        '-ar', '44100', '-af', af, '-c:a', 'libvorbis', '-q:a', '5', os.path.join(sdir(lang), k + '.ogg')], check=True)
        tim[k] = {'who': B['who'], 'dur': round(s1 - s0, 2),
                  'w': [[round(max(0, at[j][0] - s0), 2), words[j]] for j in range(first[i], last[i] + 1)],
                  'e': envelope(src, s0, s1)}
        heard = ' '.join(h[0] for h in hw if s0 <= h[1] < s1)
        print(f'{k:12s} {s1 - s0:5.2f} с | {heard[:80]}')
    json.dump(dict(sorted(tim.items())), open(tf, 'w'), ensure_ascii=False)
    loud(lang)


LOUD_T, LOUD_KEEP = -19.0, .3


def loud(lang):
    """Громкость реплик → g в тайминге (игра умножает на него голос): каждый голос — к −19 LUFS,
    внутри голоса остаётся треть живой разницы (крик громче шёпота), пик — не выше −1 дБ."""
    tf = tfile(lang)
    tim = json.load(open(tf))
    M = {}
    for k in tim:
        f = os.path.join(sdir(lang), k + '.ogg')
        if not os.path.exists(f):
            continue
        r = subprocess.run(['ffmpeg', '-nostdin', '-hide_banner', '-nostats', '-i', f, '-af', 'ebur128=peak=true', '-f', 'null', '-'],
                           capture_output=True, text=True).stderr
        I, P = re.findall(r'I:\s+(-?[\d.]+) LUFS', r), re.findall(r'Peak:\s+(-?[\d.]+) dBFS', r)
        if I and P and float(I[-1]) > -70:
            M[k] = (float(I[-1]), float(P[-1]))
    grp = {}
    for k, (I, P) in M.items():
        grp.setdefault(tim[k]['who'], []).append(I)
    med = {g: sorted(v)[len(v) // 2] for g, v in grp.items()}
    for k in tim:
        tim[k].pop('g', None)
        if k not in M:
            continue
        I, P = M[k]
        want = LOUD_T + LOUD_KEEP * (I - med[tim[k]['who']])
        db = max(-12.0, min(18.0, want - I, -1.0 - P))
        if abs(db) >= .3:
            tim[k]['g'] = round(10 ** (db / 20), 2)
    json.dump(dict(sorted(tim.items())), open(tf, 'w'), ensure_ascii=False)
    print(f'громкость {lang}: {len(M)} реплик')


def check(lang):
    tim = json.load(open(tfile(lang))) if os.path.exists(tfile(lang)) else {}
    miss = [k for k in LINES if k not in tim or not os.path.exists(os.path.join(sdir(lang), k + '.ogg'))]
    print('нет:', miss if miss else 'всё на месте')


if __name__ == '__main__':
    cmd, lang = sys.argv[1], sys.argv[2]
    if cmd == 'plan':
        plan(lang)
    elif cmd == 'plan2':
        plan2(lang)
    elif cmd == 'split':
        split(lang, int(sys.argv[3]), sys.argv[4])
    elif cmd == 'check':
        check(lang)
    elif cmd == 'loud':
        loud(lang)
