#!/usr/bin/env python3
"""Музыка «Молчи до шести» — своя, целиком кодом (numpy): пианино, пэд, музыкальная шкатулка, гул, реверберация.
Никаких сэмплов и чужих записей. Ноты — ниже, в PIECES.

  python3 tools/music/compose.py [имя …]   → public/games/nightshift/snd/<имя>.mp3   (без имён — все)

theme — титул (петля 64 с, ре минор, 60 уд/мин): тихая колыбельная-мотив «ля-фа-ми-ре» над тёмными аккордами;
shift — фон часов смены (петля 96 с): гул ре + квинта и редкие далёкие ноты мотива — тихо, чтобы слышать камеры;
end_dawn / end_newentry / end_inside / end_sister — концовки (≈44 с, в конце тишина — петля незаметна).
Петли без шва: играем три круга и берём средний (хвосты реверберации переходят через стык как в игре)."""
import os, sys, subprocess
import numpy as np

SR = 44100
OUT = os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))), 'public/games/nightshift/snd')
rng = np.random.default_rng(7)
NOTE = {'C': 0, 'D': 2, 'E': 4, 'F': 5, 'G': 7, 'A': 9, 'B': 11}

def hz(n):   # 'A4', 'C#5', 'Bb2' → Гц
    k = NOTE[n[0]] + (1 if '#' in n else -1 if 'b' in n[1:2] else 0)
    return 440 * 2 ** ((k + 12 * (int(n[-1]) + 1) - 69) / 12)

def piano(f, dur, vel=.6, bright=1.0, det=0.0):
    """Фортепиано: обертоны с лёгкой негармоничностью, у верхних затухание быстрее, двухступенчатый спад, удар молоточка."""
    tail = min(9.0, dur + 2.8 * (220 / f) ** .3 + .4); n = int(tail * SR); t = np.arange(n) / SR
    x = np.zeros(n); tau0 = 2.6 * (220 / f) ** .35
    for h in range(1, 14):
        fh = h * f * np.sqrt(1 + .00035 * h * h) * (1 + det)
        if fh > 9000: break
        a = vel * (1 / h ** 1.15) * (1 if h == 1 else .85) * (bright ** (h - 1))
        tau = tau0 / (1 + .55 * (h - 1))
        env = .55 * np.exp(-t / (tau * .22)) + .45 * np.exp(-t / tau)
        x += a * env * np.sin(2 * np.pi * fh * t + rng.uniform(0, 6.28))
    off = int(dur * SR)   # отпустили клавишу — демпфер гасит за 0,25 с
    if off < n: x[off:] *= np.exp(-(t[off:] - t[off]) / .08)
    x[:int(.004 * SR)] *= np.linspace(0, 1, int(.004 * SR))
    hn = int(.03 * SR); ham = rng.normal(0, 1, hn) * np.exp(-np.arange(hn) / (.006 * SR)) * vel * .05
    x[:hn] += np.convolve(ham, np.ones(6) / 6, 'same')
    return x

def musicbox(f, dur=2.5, vel=.4, wow=0.0, t0=0.0):
    """Музыкальная шкатулка: короткий металлический язычок (основной тон + неровные обертоны), wow — «плывёт» как заводная."""
    n = int((dur + 1.5) * SR); t = np.arange(n) / SR
    drift = 1 + wow * np.sin(2 * np.pi * .7 * (t + t0)) - wow * .6 * t / max(1, dur)
    x = np.zeros(n)
    for r, a, tau in ((1, 1, 1.1), (2.0, .25, .5), (4.2, .18, .18), (6.9, .08, .08)):
        x += a * np.exp(-t / tau) * np.sin(2 * np.pi * f * r * np.cumsum(drift) / SR)
    x[:40] *= np.linspace(0, 1, 40)
    return vel * x

def pad(fs, dur, vel=.25, bright=1400, att=2.5, rel=3.5):
    """Пэд: три расстроенных «пилы» на каждую ноту (сумма гармоник, верх приглушён), медленная атака и спад."""
    n = int((dur + rel) * SR); t = np.arange(n) / SR; x = np.zeros(n)
    for f in fs:
        for cents in (-7, 0, 6):
            fd = f * 2 ** (cents / 1200)
            for h in range(1, 18):
                fh = h * fd
                if fh > 5000: break
                a = (1 / h) / (1 + (fh / bright) ** 2)
                x += a * np.sin(2 * np.pi * fh * t + rng.uniform(0, 6.28))
    env = np.minimum(1, t / att) * np.where(t < dur, 1, np.exp(-(t - dur) / (rel / 3)))
    lfo = 1 + .12 * np.sin(2 * np.pi * .11 * t + rng.uniform(0, 6))
    return vel * x * env * lfo / (len(fs) * 3)

def drone(f, dur, vel=.3):
    """Гул: чистый низ + квинта с медленными биениями и тёмный шум."""
    n = int(dur * SR); t = np.arange(n) / SR
    x = np.sin(2 * np.pi * f * t) + .5 * np.sin(2 * np.pi * f * 1.0013 * t + 1) + .3 * np.sin(2 * np.pi * f * 1.5 * t + 2) + .15 * np.sin(2 * np.pi * f * 2.003 * t)
    nz = lowpass(rng.normal(0, 1, n), 260) * 2.2
    sw = .75 + .25 * np.sin(2 * np.pi * t / 23.0)
    fade = np.minimum(1, t / 1.5) * np.minimum(1, (dur - t) / 3.0)   # мягко входит и уходит (иначе щелчок там, где гул кончается)
    return vel * (x * .5 + nz) * sw * fade

def lowpass(x, fc):
    X = np.fft.rfft(x); f = np.fft.rfftfreq(len(x), 1 / SR); X /= np.sqrt(1 + (f / fc) ** 4); return np.fft.irfft(X, len(x))

def reverb_ir(rt=3.6, pre=.025):
    """Импульс зала: шум с экспоненциальным спадом, хвост темнеет; два канала разные (ширина)."""
    n = int((rt + .3) * SR); t = np.arange(n) / SR; out = []
    for ch in range(2):
        e = rng.normal(0, 1, n); bright, dark = lowpass(e, 6000), lowpass(e, 1200)
        k = np.clip(t / 1.2, 0, 1); ir = (bright * (1 - k) + dark * k) * np.exp(-6.91 * t / rt)
        ir = np.concatenate([np.zeros(int(pre * SR)), ir]); ir[int((pre + .011 + .004 * ch) * SR)] += .6; ir[int((pre + .023 - .003 * ch) * SR)] += .4
        out.append(ir / np.sqrt(np.sum(ir ** 2)))
    return out

def convolve(x, ir):
    n = len(x) + len(ir) - 1; N = 1 << (n - 1).bit_length()
    return np.fft.irfft(np.fft.rfft(x, N) * np.fft.rfft(ir, N), N)[:n]

class Track:
    def __init__(self, length): self.L = length; self.n = int(length * SR); self.l = np.zeros(self.n + 12 * SR); self.r = np.zeros(self.n + 12 * SR)
    def add(self, at, x, pan=0.0, gain=1.0):
        i = int(at * SR)
        if i < 0: x = x[-i:]; i = 0
        m = min(len(x), len(self.l) - i)
        if m <= 0: return
        self.l[i:i + m] += x[:m] * gain * np.sqrt(.5 - pan / 2) * 1.414; self.r[i:i + m] += x[:m] * gain * np.sqrt(.5 + pan / 2) * 1.414

def render(build, length, loop, wet=.38, rt=3.6, target=-18.0):
    """build(tr, t0) кладёт ноты одного круга со сдвигом t0. Петля: три круга, берём средний."""
    global rng
    k = 3 if loop else 1; tr = Track(length * k)
    for c in range(k): rng = np.random.default_rng(11); build(tr, c * length)   # круги одинаковы до отсчёта — стык петли без щелчка
    irl, irr = reverb_ir(rt)
    L = tr.l * (1 - wet) + convolve(tr.l, irl)[:len(tr.l)] * wet * .9
    R = tr.r * (1 - wet) + convolve(tr.r, irr)[:len(tr.r)] * wet * .9
    a = int(length * SR) if loop else 0; b = a + int(length * SR)
    y = np.stack([L[a:b], R[a:b]], 1)
    if not loop:   # в конце — тишина: петля движка начнётся заново незаметно
        fo = int(3 * SR); y[-fo:] *= np.linspace(1, 0, fo)[:, None] ** 2
    rms = np.sqrt(np.mean(y ** 2)) + 1e-9; y *= 10 ** (target / 20) / rms
    pk = np.max(np.abs(y)); y *= min(1, .89 / pk)
    return y

def save(name, y):
    path = os.path.join(OUT, name + '.mp3')
    pcm = (np.clip(y, -1, 1) * 32767).astype('<i2').tobytes()
    subprocess.run(['ffmpeg', '-v', 'error', '-y', '-f', 's16le', '-ar', str(SR), '-ac', '2', '-i', '-', '-c:a', 'libmp3lame', '-b:a', '112k', path], input=pcm, check=True)
    print(name, f'{len(y) / SR:.1f} с', path)

# ---- ноты ----
def theme(tr, t0):   # 16 тактов по 4 с; аккорды по 8 с
    chords = [['D3', 'F3', 'A3'], ['Bb2', 'D3', 'F3'], ['G2', 'Bb2', 'D3'], ['A2', 'C#3', 'E3'], ['D3', 'F3', 'A3'], ['F2', 'A2', 'C3'], ['G2', 'Bb2', 'D3', 'E3'], ['A2', 'C#3', 'E3', 'G3']]
    bass = ['D2', 'Bb1', 'G1', 'A1', 'D2', 'F1', 'G1', 'A1']
    for i, ch in enumerate(chords):
        tr.add(t0 + i * 8, pad([hz(n) for n in ch], 8.2, vel=.32, bright=1100), pan=-.1)
        tr.add(t0 + i * 8, piano(hz(bass[i]), 7.5, vel=.32, bright=.7), pan=-.25)
    mel = [('A4', 0, 1.5), ('F4', 1.5, .5), ('E4', 2, 1), ('D4', 3, 2.5),
           ('D5', 8, 1.5), ('C5', 9.5, .5), ('Bb4', 10, 1), ('A4', 11, 2.5),
           ('G4', 16, 1.5), ('A4', 17.5, .5), ('Bb4', 18, 1), ('D5', 19, 1), ('C#5', 20, 3),
           ('E5', 24, 1), ('C#5', 25, 1), ('A4', 26, 3),
           ('A4', 32, 1.5), ('F4', 33.5, .5), ('E4', 34, 1), ('D4', 35, 2.5),
           ('C5', 40, 1.5), ('A4', 41.5, .5), ('G4', 42, 1), ('F4', 43, 2.5),
           ('Bb4', 48, 1), ('A4', 49, 1), ('G4', 50, 1), ('D5', 51, 1), ('C#5', 52, 3),
           ('E4', 56, 1.5), ('G4', 57.5, .5), ('F4', 58, 1), ('E4', 59, 1), ('A3', 60, 3.5)]
    for n, at, d in mel: tr.add(t0 + at + rng.uniform(-.015, .015), piano(hz(n), d, vel=.42 + rng.uniform(-.04, .04), bright=.82), pan=.15)
    for n, at, d in mel[16:24]:   # во второй половине мотив тихо вторит шкатулка — как из детской
        f = hz(n) * 2; tr.add(t0 + at + .02, musicbox(f, 2, vel=.11), pan=.45)

def shift(tr, t0):   # 96 с: гул ре и квинта, раз в 12 с далёкая нота мотива
    tr.add(t0 - 3, drone(hz('D2'), 96 + 6, vel=.35))
    tr.add(t0, pad([hz('D3'), hz('A3')], 46, vel=.12, bright=700, att=8, rel=6), pan=-.2)
    tr.add(t0 + 48, pad([hz('Bb2'), hz('F3')], 46, vel=.11, bright=650, att=8, rel=6), pan=.2)
    for i, n in enumerate(['A4', 'F4', 'E4', 'D4', 'C#5', 'D4', 'A4', 'Bb4']):
        tr.add(t0 + 5 + i * 12 + rng.uniform(-.8, .8), piano(hz(n), 2.5, vel=.18, bright=.7), pan=rng.uniform(-.5, .5))

def end_dawn(tr, t0):   # ре мажор, светлее: ночь кончилась
    chords = [['D3', 'F#3', 'A3'], ['G2', 'B2', 'D3'], ['B2', 'D3', 'F#3'], ['A2', 'C#3', 'E3'], ['G2', 'B2', 'D3'], ['D3', 'F#3', 'A3']]
    for i, ch in enumerate(chords): tr.add(t0 + i * 6.5, pad([hz(n) for n in ch], 6.8, vel=.3, bright=1600))
    mel = [('F#4', 1, 1.5), ('A4', 2.5, .8), ('D5', 3.3, 2.5), ('B4', 7.5, 1.2), ('D5', 8.7, .8), ('G4', 9.5, 2.5), ('F#4', 14, 1.2), ('B4', 15.2, 1.6), ('A4', 16.8, 2.5),
           ('E4', 20.5, 1.2), ('C#5', 21.7, 1.2), ('A4', 22.9, 2.5), ('B4', 27, 1.2), ('D5', 28.2, 1.4), ('A4', 32.5, 1.2), ('F#4', 33.7, 1.2), ('D4', 35, 4.5)]
    for n, at, d in mel: tr.add(t0 + at, piano(hz(n), d, vel=.4, bright=.86), pan=.12)
    tr.add(t0 + 35, piano(hz('D3'), 5, vel=.3), pan=-.2); tr.add(t0 + 35, piano(hz('A3'), 5, vel=.22), pan=-.1)

def end_newentry(tr, t0):   # одно пианино: мотив медленно, в конце не разрешается — повисает на «ля»
    for i, (b, ch) in enumerate([('D2', ['D3', 'A3']), ('Bb1', ['F3', 'D4']), ('G1', ['D3', 'Bb3']), ('A1', ['E3', 'C#4']), ('D2', ['F3', 'A3']), ('A1', ['E3', 'A3'])]):
        tr.add(t0 + i * 6.5, piano(hz(b), 6, vel=.3, bright=.65), pan=-.2)
        for j, n in enumerate(ch): tr.add(t0 + i * 6.5 + .9 + j * .6, piano(hz(n), 4.5, vel=.2, bright=.7), pan=-.05)
    mel = [('A4', .5, 1.8), ('F4', 2.3, .7), ('E4', 3, 1.5), ('D4', 4.5, 2.5), ('D5', 7, 1.8), ('C5', 8.8, .7), ('Bb4', 9.5, 1.5), ('A4', 11, 2.5),
           ('G4', 13.5, 1.8), ('Bb4', 15.3, .7), ('D5', 16, 1.5), ('C#5', 17.5, 3), ('E4', 20.5, 1.8), ('C#5', 22.3, .7), ('A4', 23, 3),
           ('A4', 26.5, 1.8), ('F4', 28.3, .7), ('E4', 29, 1.5), ('D4', 30.5, 3), ('E4', 34, 2), ('A3', 36, 5)]
    for n, at, d in mel: tr.add(t0 + at, piano(hz(n), d, vel=.38, bright=.8), pan=.1)

def end_inside(tr, t0):   # шкатулка играет мотив в мажоре и «заводится» вниз: плывёт и замедляется; под ней — гул с секундой
    tr.add(t0, drone(hz('D2'), 42, vel=.22)); tr.add(t0 + 8, pad([hz('D3'), hz('C#4')], 30, vel=.07, bright=600, att=10), pan=.3)
    mel = ['A5', 'F#5', 'E5', 'D5', 'D6', 'C#6', 'B5', 'A5', 'G5', 'A5', 'B5', 'D6', 'C#6', 'A5', 'F#5', 'E5', 'D5']
    at, step = 1.0, .62
    for i, n in enumerate(mel + mel[:8]):
        w = min(.03, .002 + i * .0012)
        tr.add(t0 + at, musicbox(hz(n), 2.2, vel=.32, wow=w, t0=at), pan=.2)
        at += step; step *= 1.045   # завод кончается
        if at > 38: break

def end_sister(tr, t0):   # страх нарастает: низкий кластер, мотив в басу, обрыв в тишину
    tr.add(t0, drone(hz('D2'), 37.5, vel=.35)); tr.add(t0 + 2, pad([hz('D2'), hz('Eb2'), hz('A2')], 30, vel=.22, bright=500, att=14, rel=2), pan=-.1)
    for i, (n, at, d) in enumerate([('A2', 3, 2), ('F2', 5.5, 1), ('E2', 7, 1.5), ('D2', 9, 4), ('A2', 15, 2), ('F2', 17.5, 1), ('E2', 19, 1.5), ('Eb2', 21, 5), ('D2', 27, 6)]):
        tr.add(t0 + at, piano(hz(n), d, vel=.45, bright=.6), pan=-.15)
    for at in (24, 26.5, 28.5, 30, 31.2, 32.2):   # высокие «уколы»
        tr.add(t0 + at, piano(hz('C#6'), .4, vel=.12, bright=.9), pan=.5)

PIECES = {'theme': (theme, 64, True, .38, 3.6, -19), 'shift': (shift, 96, True, .5, 4.5, -24),
          'end_dawn': (end_dawn, 44, False, .36, 3.4, -18), 'end_newentry': (end_newentry, 44, False, .36, 3.6, -19),
          'end_inside': (end_inside, 42, False, .42, 4.0, -20), 'end_sister': (end_sister, 38, False, .4, 4.2, -18)}

if __name__ == '__main__':
    for name in (sys.argv[1:] or PIECES):
        fn, length, loop, wet, rt, target = PIECES[name]
        save(name, render(fn, length, loop, wet, rt, target))
