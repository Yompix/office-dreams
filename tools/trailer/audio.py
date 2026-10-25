#!/usr/bin/env python3
"""«Hush Till Six» — звук трейлера: музыка (кодом, инструменты tools/music/compose.py, мотив игры «ля-фа-ми-ре»),
голоса игры с теми же эффектами, что в engine.js (телефон, рация, за стеклом, «чужой» голос), звуки из snd/.
Музыка уходит под голос; в конце — громкость −14 LUFS (YouTube/TikTok).

  python3 tools/trailer/audio.py TIMELINE.json OUT.wav
"""
import json, os, sys, subprocess
import numpy as np
HERE = os.path.dirname(os.path.abspath(__file__)); ROOT = os.path.join(HERE, '../../public/games/nightshift')
sys.path.insert(0, os.path.join(HERE, '../music')); sys.dont_write_bytecode = True   # без __pycache__ в репозитории
import compose as C
from compose import piano, pad, drone, musicbox, lowpass, hz, SR

def load(rel, rate=1.0):
    raw = subprocess.run(['ffmpeg', '-v', 'error', '-i', os.path.join(ROOT, rel), '-f', 'f32le', '-ac', '2', '-ar', str(SR), '-'], capture_output=True, check=True).stdout
    x = np.frombuffer(raw, '<f4').reshape(-1, 2).astype(np.float64)
    if rate != 1.0:   # как playbackRate: медленнее и ниже
        n = int(len(x) / rate); src = np.arange(n) * rate
        x = np.stack([np.interp(src, np.arange(len(x)), x[:, c]) for c in range(2)], 1)
    return x

def spec_filter(x, hp=0, lp=0, order=2):   # фильтры как у биквадов игры (амплитудная характеристика), по всему клипу сразу
    N = len(x); f = np.fft.rfftfreq(N, 1 / SR); X = np.fft.rfft(x, axis=0); k = np.ones_like(f)
    if lp: k /= np.sqrt(1 + (f / lp) ** (2 * order))
    if hp: k /= np.sqrt(1 + (hp / np.maximum(f, 1e-3)) ** (2 * order))
    return np.fft.irfft(X * k[:, None], N, axis=0)

def hall_ir(sec=2.4, seed=3):   # эхо голосов в игре: шум с затуханием (1 - i/len)^3.2
    r = np.random.default_rng(seed); n = int(sec * SR); i = np.arange(n)
    ir = r.uniform(-1, 1, (n, 2)) * ((1 - i / n) ** 3.2)[:, None]; ir[:90] *= (np.arange(90) / 90)[:, None]
    return ir / np.sqrt(np.sum(ir ** 2) / 2)
IR = hall_ir()

def conv(x, ir):
    n = len(x) + len(ir) - 1; N = 1 << (n - 1).bit_length()
    return np.stack([np.fft.irfft(np.fft.rfft(x[:, c], N) * np.fft.rfft(ir[:, c], N), N)[:n] for c in range(2)], 1)

def voice(v):
    """Реплика с эффектом метки fx (engine.js voiceFx): возвращает (сухой, посыл в эхо)."""
    fx, who, aloud = v.get('fx', ''), v.get('who', ''), v.get('aloud', False)
    base = load(v['file'], v.get('rate', 1.0)) * v.get('g', 1.0)
    def ghost(g, dt):   # «низкий призрак» под чужим голосом: та же реплика вдвое медленнее, обрезана по длине
        y = load(v['file'], .5) * v.get('g', 1.0) * g; y = y[:len(base)]; pad_ = np.zeros((int(dt * SR), 2)); return np.concatenate([pad_, y])[:len(base) + int(dt * SR)]
    def mix(a, b):
        n = max(len(a), len(b)); out = np.zeros((n, 2)); out[:len(a)] += a; out[:len(b)] += b; return out
    if fx == 'radio':
        y = spec_filter(base * 1.35, hp=480); y = np.tanh(y * 2.6) / np.tanh(2.6); y = spec_filter(y, lp=2600); return y, y * .06
    if fx == 'phone':
        y = spec_filter(base * 1.25, hp=330, lp=3300); return y, y * 0
    if fx == 'phonefake':
        y = mix(base * 1.25, ghost(.14, .02)); y = spec_filter(y, hp=330, lp=3300); return y, y * 0
    if fx == 'glass':
        y = spec_filter(base * 1.4, lp=1100); return y, y * .2 / 1.4
    if fx == 'muffle':
        y = spec_filter(base * 1.5, lp=1100); return y, y * .35 / 1.5
    if fx == 'near': return base * 1.05, base * 0
    if fx == 'fake':
        y = mix(base, ghost(.1, .03)); return y, base * .05
    if who == 'ИТАН': return base, (base * .12 if aloud else base * 0)
    if who == '???':
        y = mix(base, ghost(.16, 0)); return y, y * .3
    return base, base * .2

class Bus:
    def __init__(s, sec): s.x = np.zeros((int(sec * SR) + SR * 12, 2))
    def add(s, at, y, gain=1.0):
        i = int(round(at * SR))
        if i < 0: y = y[-i:]; i = 0
        m = min(len(y), len(s.x) - i)
        if m > 0: s.x[i:i + m] += y[:m] * gain

def env(n, fi=0.0, fo=0.0):
    e = np.ones(n)
    if fi > 0: k = min(n, int(fi * SR)); e[:k] *= np.linspace(0, 1, k)
    if fo > 0: k = min(n, int(fo * SR)); e[n - k:] *= np.linspace(1, 0, k)
    return e

# ---------------- музыка ----------------
def mono(x): return np.stack([x, x], 1)
def at(tr, t, x, pan=0.0, gain=1.0):
    if x.ndim == 1: x = np.stack([x * np.sqrt(.5 - pan / 2) * 1.414, x * np.sqrt(.5 + pan / 2) * 1.414], 1)
    tr.add(t, x, gain)

def braam(f, dur, vel=.5, open_=.35):
    """«Браам»: низкий медный удар — расстроенные пилы, фильтр раскрывается и закрывается (смесь тёмной и яркой версии)."""
    n = int((dur + 1.5) * SR); t = np.arange(n) / SR; x = np.zeros(n)
    for fr in (f, f * 2, f * 1.5):
        for cents in (-11, -4, 0, 5, 12):
            fd = fr * 2 ** (cents / 1200); ph = C.rng.uniform(0, 6.28)
            for h in range(1, 40):
                if h * fd > 6000: break
                x += (1 / h) * np.sin(2 * np.pi * h * fd * t + ph * h) * (1 if fr == f else .5)
    dark, bright = lowpass(x, 220), lowpass(x, 1800)
    k = np.clip(t / open_, 0, 1) * np.exp(-np.maximum(0, t - open_) / (dur * .45))
    y = dark * (1 - k) + bright * k
    e = np.minimum(1, t / .03) * np.where(t < dur, 1, np.exp(-(t - dur) / .5)) * (1 + .3 * np.exp(-t / .2))
    y = y * e; return vel * y / (np.max(np.abs(y)) + 1e-9)

def pulse(f=hz('D1'), vel=.6):   # низкий удар-пульс (как сердце, только ниже и суше)
    n = int(.9 * SR); t = np.arange(n) / SR; fr = f * (1 + 1.5 * np.exp(-t / .03))
    x = np.sin(2 * np.pi * np.cumsum(fr) / SR) * np.exp(-t / .22) + .3 * lowpass(C.rng.normal(0, 1, n), 120) * np.exp(-t / .05)
    return vel * x

def riser(dur, vel=.4):   # шум вверх: полоса поднимается, громкость растёт, в конце — обрыв
    n = int(dur * SR); t = np.arange(n) / SR; nz = C.rng.normal(0, 1, n); y = np.zeros(n); k = t / dur
    for i, (a, b) in enumerate(((0, .35), (.3, .7), (.6, 1.01))):
        seg = (k >= a) & (k < b); band = lowpass(nz, 400 + 5200 * (a + b) / 2) - lowpass(nz, 120 + 2000 * (a + b) / 2); w = np.clip((k - a) / max(.05, b - a), 0, 1)
        y += band * np.where(seg, np.sin(np.pi * w), 0)
    tone = np.sin(2 * np.pi * np.cumsum(hz('D3') * (1 + 3 * k ** 2)) / SR) * .25
    return vel * (y * .6 + tone) * k ** 2

def score(TL):
    """Музыка по отметкам монтажа: завязка (гул, редкие ноты мотива) → правила (пульс) → нарастание (пульс чаще, браамы, остинато)
    → смерти (кластер) → тишина → название (браам + шкатулка) → звонок (тишина) → финальная карточка (шкатулка, пэд)."""
    M, D = TL['marks'], TL['dur']; tr = Bus(D)
    rules = next(s['start'] for s in TL['segs'] if s['id'] == 'call'); build, deaths, silence = M['build'], M['deaths'], M['silence']
    scare, end = M['scare'], M['end']; title = M.get('title'); stinger = M.get('stinger')
    yt = TL['fmt'] == 'yt'
    # завязка: гул ре, пэд ре-ля, редкие ноты мотива
    at(tr, 0, drone(hz('D2'), deaths + 2, vel=.26))
    at(tr, 1.0 if yt else 0, pad([hz('D3'), hz('A3')], max(4, rules - 1), vel=.10, bright=700, att=4, rel=3), pan=-.15)
    if yt:
        for i, n in enumerate(['A4', 'F4', 'E4', 'D4']): at(tr, 2.2 + i * 1.6 + (i == 3) * .6, piano(hz(n), 2.6, vel=.16, bright=.7), pan=.2)
        for i, n in enumerate(['D5', 'C5', 'Bb4', 'A4']): at(tr, 11 + i * 1.6 + (i == 3) * .6, piano(hz(n), 2.6, vel=.13, bright=.7), pan=-.2)
    # правила: пульс 60 уд/мин и пэд си-бемоль
    t = rules; per = 1.0
    while t < build: at(tr, t, pulse(vel=.45)); t += per
    at(tr, rules, pad([hz('Bb2'), hz('F3'), hz('D4')], build - rules + 1, vel=.12, bright=800, att=2, rel=2), pan=.1)
    # нарастание: пульс чаще, остинато ре, пэд ярче, браамы на ключевых кадрах
    span = deaths - build; t = build
    while t < deaths - .2:
        k = (t - build) / span; at(tr, t, pulse(vel=.5 + .3 * k)); t += 1.0 - .62 * k
    t = build; step = .5
    while t < deaths - .1:
        k = (t - build) / span; n = ['D3', 'A3', 'D4', 'A3'][int(round((t - build) / step)) % 4]
        at(tr, t, piano(hz(n), .3, vel=.06 + .1 * k, bright=.55), pan=.3 * np.sin(t)); t += step * (1 - .4 * k)
    at(tr, build, pad([hz('D3'), hz('F3'), hz('A3'), hz('C4')], span, vel=.08, bright=900, att=span * .8, rel=1), pan=-.1)
    at(tr, build + span * .45, pad([hz('Eb4'), hz('D5')], span * .55, vel=.05, bright=1600, att=span * .5, rel=.5), pan=.25)   # высокая секунда — тревога
    for s in TL['segs']:
        if s['id'] in ('lift', 'lily', 'face', 'frank') and build <= s['start'] < deaths: at(tr, s['start'], braam(hz('D1'), 2.2, vel=.24))
    last = max([v['at'] + v['dur'] for v in TL['vo'] if v['at'] < deaths] or [deaths - 2.6]); r0 = max(deaths - 2.6, last + .05)   # рост — после последней реплики
    if deaths - r0 > .4: at(tr, r0, riser(deaths - r0, vel=.34))
    # смерти: браам и низкий кластер; на «14 способов» — ещё один
    at(tr, deaths, braam(hz('D1'), 3.2, vel=.5)); at(tr, deaths, pad([hz('D2'), hz('Eb2'), hz('A2')], silence - deaths, vel=.2, bright=500, att=.3, rel=.3))
    ways = next((s['start'] for s in TL['segs'] if s['id'] == 'ways'), None)
    if ways: at(tr, ways, braam(hz('A0') * 2, 2.6, vel=.55))
    # тишина до броска: тонкий высокий тон
    at(tr, silence + .3, (np.sin(2 * np.pi * hz('C#7') * np.arange(int((scare - silence) * SR)) / SR) * .012 * env(int((scare - silence) * SR), 2, .5)))
    # название: браам + шкатулка с мотивом
    if title:
        at(tr, title + .1, braam(hz('D1'), 3.4, vel=.55))
        for i, n in enumerate(['A5', 'F5', 'E5', 'D5']): at(tr, title + 1.4 + i * .55, musicbox(hz(n), 2.2, vel=.16, wow=.004 + i * .002, t0=i), pan=.25)
    # финальная карточка: пэд ре минор и мотив шкатулкой, последний звук повисает
    e0 = end
    at(tr, e0, pad([hz('D3'), hz('F3'), hz('A3')], D - e0 + .5, vel=.13, bright=900, att=1.2, rel=2))
    mel = [('A5', 0), ('F5', .55), ('E5', 1.1), ('D5', 1.65), ('D6', 2.6), ('C6', 3.15), ('Bb5', 3.7), ('A5', 4.25)] if yt else [('A5', .2), ('F5', .75), ('E5', 1.3), ('D5', 1.85), ('A5', 3.0)]
    for n, dt in mel:
        if e0 + dt < D - .4: at(tr, e0 + .4 + dt, musicbox(hz(n), 2.0, vel=.15, wow=.003), pan=.2)
    y = tr.x[:int(D * SR) + SR * 3]
    irl, irr = C.reverb_ir(3.8)
    wet = np.stack([C.convolve(y[:, 0], irl)[:len(y)], C.convolve(y[:, 1], irr)[:len(y)]], 1)
    y = y * .7 + wet * .4
    # провалы музыки: тишина перед броском и звонок
    g = np.ones(len(y)); tt = np.arange(len(y)) / SR
    def dip(a, b, fo=.25, fi=.4, lvl=0.0):
        k = np.clip((tt - a) / fo, 0, 1) * np.clip((b - tt) / fi, 0, 1); g[:] = np.minimum(g, 1 - (1 - lvl) * k)
    dip(silence, (title or end) - .05, .35, .05, .04)
    if stinger: dip(stinger, end, .3, .3, 0)
    return y * g[:, None]

BEDS = {'rain', 'drone_dread', 'sub_presence', 'static', 'tinnitus', 'room_tone', 'drone', 'hum_stop', 'whispers'}   # подложки: по средней громкости, остальное — удары, по пику

def rms_db(x):
    a = np.abs(x).max(1) if x.ndim > 1 else np.abs(x); act = x[a > max(1e-5, np.percentile(a, 60))]
    return 20 * np.log10(np.sqrt(np.mean(act ** 2)) + 1e-12) if len(act) else -120

def build_mix(TL, out):
    D = TL['dur']; vb, rb, sb = Bus(D), Bus(D), Bus(D)
    for v in TL['vo']:   # каждая реплика — к одной громкости речи (−19 дБ, по всей реплике), эффекты не меняют разборчивость
        dry, rev = voice(v); k = 10 ** ((-22 - rms_db(spec_filter(dry, hp=300, lp=4000))) / 20) * v.get('gain', 1.0)   # по речевой полосе: низкий голос рассказчика не тонет
        if v.get('from') or v.get('to') is not None:   # кусок реплики (правила): режем после эффектов, края — 15 мс
            r = v.get('rate', 1.0); i0 = int(round((v.get('from') or 0) / r * SR)); i1 = int(round(v['to'] / r * SR)) if v.get('to') is not None else len(dry)
            dry, rev = dry[i0:i1].copy(), rev[i0:].copy(); f = min(len(dry), int(.015 * SR)); dry[:f] *= np.linspace(0, 1, f)[:, None]; dry[-f:] *= np.linspace(1, 0, f)[:, None]
            rev = rev[:len(dry)] * env(len(dry), .015, .015)[:, None]
        vb.add(v['at'], dry * k); rb.add(v['at'], rev * k)
    for s in TL['sfx']:
        y = load('snd/%s.mp3' % s['n'], s.get('rate', 1.0))
        if s.get('cut'): y = y[:int(s['cut'] * SR)]
        if s.get('lp'): y = spec_filter(y, lp=s['lp'])
        if s['n'] in BEDS: continue   # подложки — отдельно (Bd)
        y = y * 10 ** (-3 / 20) / (np.max(np.abs(y)) + 1e-9)
        y = y * s.get('gain', 1.0) * env(len(y), s.get('fadeIn', 0), .12 if s.get('cut') else min(.05, len(y) / SR / 4))[:, None]
        sb.add(s['at'], y)
    V = vb.x + conv(rb.x, IR)[:len(vb.x)] * .55
    S = sb.x + conv(sb.x * .2, IR)[:len(sb.x)] * .4
    Bd = np.zeros_like(S)   # подложки (дождь, гул) тоже чуть уходят под голос
    for s in TL['sfx']:
        if s['n'] in BEDS:
            y = load('snd/%s.mp3' % s['n'], s.get('rate', 1.0))
            if s.get('cut'): y = y[:int(s['cut'] * SR)]
            y = y * 10 ** ((-26 - rms_db(y)) / 20) * s.get('gain', 1.0) * env(len(y), s.get('fadeIn', 0), .12 if s.get('cut') else min(.05, len(y) / SR / 4))[:, None]
            i = int(s['at'] * SR); m = min(len(y), len(Bd) - i); Bd[i:i + m] += y[:m]
    mu = score(TL); Mx = np.zeros_like(V); n = min(len(mu), len(Mx)); Mx[:n] += mu[:n]
    w = int(5 * SR); loud = max(np.sqrt(np.mean(Mx[i:i + w] ** 2)) for i in range(0, max(1, len(Mx) - w), SR)); Mx *= 10 ** (-21 / 20) / (loud + 1e-12)   # громкие места музыки — −21 дБ
    # музыка уходит под голос: огибающая речи (атака 40 мс, спад 400 мс) → до −9 дБ
    e = np.abs(V).max(1)[::32]; fd = np.zeros_like(e); s_ = 0.0; a, r = np.exp(-32 / (.04 * SR)), np.exp(-32 / (.4 * SR))
    for i, x in enumerate(e): s_ = a * s_ + (1 - a) * x if x > s_ else r * s_ + (1 - r) * x; fd[i] = s_
    fol = np.interp(np.arange(len(V)), np.arange(len(fd)) * 32, fd); ref = 10 ** (-19 / 20) * 1.4
    k = np.clip(fol / ref, 0, 1); Mx *= (1 - .8 * k)[:, None]; Bd *= (1 - .6 * k)[:, None]
    S = S + Bd
    y = (V + Mx + S)[:int((D + .3) * SR)]
    fo = int(.25 * SR); y[-fo:] *= np.linspace(1, 0, fo)[:, None]
    # отчёт: речь против фона на каждой реплике (нужно ≥ 8–10 дБ)
    band = lambda x: spec_filter(x, hp=300, lp=4000)   # разборчивость — по речевой полосе 300–4000 Гц (низ гула и браамов речь не маскирует)
    for v in TL['vo']:
        i0, i1 = int(v['at'] * SR), int((v['at'] + v['dur']) * SR)
        sv, bg = rms_db(band(V[i0:i1])), rms_db(band((Mx + S)[i0:i1])); print('  %-6s %6.2f с  речь %6.1f  фон %6.1f  → %+5.1f дБ%s' % (v['k'], v['at'], sv, bg, sv - bg, '  ← тихо' if sv - bg < 8 else ''))
    tmp = out + '.raw.wav'; pk = np.max(np.abs(y)); y = y / max(1.0, pk / .98)
    pcm = (np.clip(y, -1, 1) * 32767).astype('<i2').tobytes()
    subprocess.run(['ffmpeg', '-v', 'error', '-y', '-f', 's16le', '-ar', str(SR), '-ac', '2', '-i', '-', tmp], input=pcm, check=True)
    # громкость −14 LUFS, пик −1.5 dBTP: ограничитель пиков, потом loudnorm в два прохода
    pre = 'alimiter=limit=0.7:attack=2:release=60:level=disabled'
    st = subprocess.run(['ffmpeg', '-hide_banner', '-i', tmp, '-af', pre + ',loudnorm=I=-14:TP=-1.5:LRA=14:print_format=json', '-f', 'null', '-'], capture_output=True, text=True).stderr
    j = json.loads(st[st.rindex('{'):st.rindex('}') + 1])
    af = pre + f",loudnorm=I=-14:TP=-1.5:LRA=14:measured_I={j['input_i']}:measured_TP={j['input_tp']}:measured_LRA={j['input_lra']}:measured_thresh={j['input_thresh']}:offset={j['target_offset']}:linear=true,aresample=48000"
    subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', tmp, '-af', af, '-ar', '48000', out], check=True); os.remove(tmp)
    st2 = subprocess.run(['ffmpeg', '-hide_banner', '-i', out, '-af', 'ebur128=peak=true', '-f', 'null', '-'], capture_output=True, text=True).stderr
    summ = st2[st2.rindex('Summary:'):].split()
    print(out, f'{D:.1f} с', 'итог:', ' '.join(summ[1:14]))

if __name__ == '__main__':
    TL = json.load(open(sys.argv[1])); build_mix(TL, sys.argv[2])
