#!/usr/bin/env python3
"""Радиостанции Новограда: своя синтезированная музыка (без чужих сэмплов).
Каждая станция — трек ~100 с, зацикливается без шва. Выход: assets/novograd/sounds/radio/*.ogg
Запуск из папки novograd: python3 tools/gen_radio.py"""
import os, subprocess, wave
import numpy as np

SR = 24000
HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'novograd', 'sounds', 'radio')
os.makedirs(OUT, exist_ok=True)
rng = np.random.default_rng(88)


T = 0  # транспонирование трека (полутона)


def hz(n):  # MIDI → Гц
    return 440.0 * 2 ** ((n + T - 69) / 12)


def t_(sec):
    return np.arange(int(sec * SR)) / SR


def env(n, a=0.005, d=0.1, s=0.6, r=0.1):
    e = np.ones(n) * s
    A, D, R = int(a * SR), int(d * SR), int(r * SR)
    A = max(1, min(A, n)); e[:A] = np.linspace(0, 1, A)
    D2 = max(0, min(D, n - A)); e[A:A + D2] = np.linspace(1, s, D2)
    R = min(R, n); e[n - R:] *= np.linspace(1, 0, R)
    return e


def lowpass(x, cutoff):
    """FIR-фильтр (окно Хэмминга)."""
    taps = 63
    fc = cutoff / SR
    k = np.arange(taps) - (taps - 1) / 2
    h = np.sinc(2 * fc * k) * np.hamming(taps)
    h /= h.sum()
    return np.convolve(x, h, mode='same')


def saw(f, t, detune=0.0):
    out = 2 * ((f * t) % 1) - 1
    if detune:
        out = (out + 2 * ((f * (1 + detune) * t) % 1) - 1 + 2 * ((f * (1 - detune) * t) % 1) - 1) / 3
    return out


def sq(f, t, pw=0.5):
    return np.where((f * t) % 1 < pw, 1.0, -1.0)


def tri(f, t):
    return 2 * np.abs(2 * ((f * t) % 1) - 1) - 1


def kick(dur=0.35, punch=1.0):
    t = t_(dur)
    f = 50 + 120 * np.exp(-t * 35)
    ph = 2 * np.pi * np.cumsum(f) / SR
    return np.sin(ph) * np.exp(-t * 9) * punch + np.sin(ph * 2) * np.exp(-t * 40) * 0.2


def snare(dur=0.25, tone=190):
    t = t_(dur)
    n = rng.uniform(-1, 1, len(t))
    n = n - lowpass(n, 1500)
    return n * np.exp(-t * 18) * 0.7 + np.sin(2 * np.pi * tone * t) * np.exp(-t * 25) * 0.5


def clap(dur=0.25):
    t = t_(dur)
    n = rng.uniform(-1, 1, len(t)); n = n - lowpass(n, 1200)
    e = np.exp(-t * 22)
    for d in (0.01, 0.02):
        e += np.exp(-np.clip(t - d, 0, None) * 60) * (t > d) * 0.6
    return n * e * 0.5


def hat(dur=0.05, open_=False):
    t = t_(0.3 if open_ else dur)
    n = rng.uniform(-1, 1, len(t)); n = n - lowpass(n, 7000)
    return n * np.exp(-t * (8 if open_ else 70)) * 0.35


def mix(*xs):
    n = max(len(x) for x in xs)
    out = np.zeros(n)
    for x in xs: out[:len(x)] += x
    return out


def place(buf, snd, at, gain=1.0):
    i = int(at * SR)
    if i >= len(buf): return
    j = min(len(buf), i + len(snd))
    buf[i:j] += snd[:j - i] * gain


def pluck(f, dur, bright=4000):
    t = t_(dur)
    x = saw(f, t, 0.004) * np.exp(-t * 6)
    return lowpass(x, bright) * env(len(t), 0.002, 0.05, 0.8, 0.05)


def pad(freqs, dur, cutoff=2200):
    t = t_(dur)
    x = sum(saw(f, t, 0.006) for f in freqs) / len(freqs)
    return lowpass(x, cutoff) * env(len(t), 0.4, 0.3, 0.8, 0.5)


def epiano(f, dur):
    t = t_(dur)
    mod = np.sin(2 * np.pi * f * 2 * t) * np.exp(-t * 4) * 1.5
    return np.sin(2 * np.pi * f * t + mod) * np.exp(-t * 2.2) * env(len(t), 0.003, 0.1, 0.9, 0.2)


def bass808(f, dur, slide_from=None):
    t = t_(dur)
    fr = np.full(len(t), f) if slide_from is None else f + (slide_from - f) * np.exp(-t * 18)
    ph = 2 * np.pi * np.cumsum(fr) / SR
    x = np.sin(ph) + 0.25 * np.tanh(3 * np.sin(ph))
    return x * env(len(t), 0.004, 0.2, 0.7, 0.12)


def chord(root, kind='m'):
    third = 3 if kind == 'm' else 4
    return [root, root + third, root + 7]


def master(x, name, gain=0.85):
    x = x / (np.max(np.abs(x)) + 1e-9) * gain
    x = np.tanh(x * 1.2) / np.tanh(1.2)
    # стыкуем конец с началом: 30 мс кроссфейда
    n = int(0.03 * SR)
    x[:n] = x[:n] * np.linspace(0, 1, n) + x[-n:] * np.linspace(1, 0, n)
    x = x[:-n]
    tmp = os.path.join(OUT, name + '.wav')
    with wave.open(tmp, 'wb') as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
        w.writeframes((x * 32000).astype(np.int16).tobytes())
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', tmp, '-c:a', 'libvorbis', '-q:a', '2', os.path.join(OUT, name + '.ogg')], check=True)
    os.remove(tmp)
    print(name, round(len(x) / SR, 1), 'с')


# ---------------------------------------------------------------- Неон 88: синтвейв
def neon(name='neon88', v=0):
    bpm = [100, 108, 94][v]; beat = 60 / bpm; bars = 36
    L = bars * 4 * beat
    buf = np.zeros(int(L * SR) + SR)
    prog = [[(57, 'm'), (53, 'M'), (48, 'M'), (55, 'M')], [(57, 'm'), (55, 'M'), (53, 'M'), (52, 'M')], [(50, 'm'), (57, 'm'), (53, 'M'), (55, 'M')]][v]
    lead = [int(x) for x in rng.choice([64, 65, 67, 69, 71, 72, 74, 76], 16)]
    for bar in range(bars):
        root, kind = prog[bar % 4]
        t0 = bar * 4 * beat
        part = (bar // 8) % 5  # 0 вступление, 1 куплет, 2 припев, 3 куплет, 4 припев
        place(buf, pad([hz(n) for n in chord(root + 12, 'm' if kind == 'm' else 'M')], 4 * beat), t0, 0.35)
        for e in range(8):  # бас восьмыми, октавой
            n = root - 12 + (12 if e % 2 else 0)
            place(buf, lowpass(saw(hz(n), t_(beat / 2 * 0.9)), 900) * env(int(beat / 2 * 0.9 * SR), 0.003, 0.08, 0.6, 0.03), t0 + e * beat / 2, 0.45)
        if part > 0:
            for b in range(4):
                place(buf, kick(), t0 + b * beat, 0.9)
                if b % 2: place(buf, mix(snare(0.4), clap() * 0.6), t0 + b * beat, 0.6)
                place(buf, hat(), t0 + b * beat + beat / 2, 0.5)
            for s in range(16):  # арпеджио
                notes = chord(root + 24, 'm' if kind == 'm' else 'M') + [root + 36]
                place(buf, pluck(hz(notes[s % 4]), beat / 4, 3000), t0 + s * beat / 4, 0.18)
        if part in (2, 4):
            for i in range(4):
                n = lead[(bar % 4) * 4 + i]
                t = t_(beat * 0.95)
                x = lowpass(sq(hz(n), t, 0.3) * 0.5 + saw(hz(n), t, 0.005) * 0.5, 3500) * env(len(t), 0.01, 0.1, 0.7, 0.1)
                vib = 1  # лёгкое эхо
                place(buf, x, t0 + i * beat, 0.25)
                place(buf, x, t0 + i * beat + beat * 0.75, 0.08)
    master(buf[:int(L * SR)], name)


# ---------------------------------------------------------------- Бит Улиц: хип-хоп / трэп
def beat_ulic(name='beat_ulic', v=0):
    bpm = [86, 92, 78][v]; beat = 60 / bpm; bars = 32
    L = bars * 4 * beat
    buf = np.zeros(int(L * SR) + SR)
    prog = [[50, 50, 46, 45], [48, 51, 53, 51], [45, 41, 43, 40]][v]
    mel = [int(x) for x in rng.choice([69, 70, 72, 74, 76, 77], 8)]
    for bar in range(bars):
        r = prog[bar % 4]
        t0 = bar * 4 * beat
        for i, n in enumerate(chord(r + 12, 'm' if bar % 4 != 3 else 'M')):
            place(buf, epiano(hz(n), 4 * beat), t0 + i * 0.012, 0.18)
        if bar >= 2:
            kicks = [0, 1.5, 2.75] if bar % 2 == 0 else [0, 0.75, 2.5, 3.25]
            for k in kicks: place(buf, kick(0.5, 1.2), t0 + k * beat, 0.9)
            for b in (1, 3): place(buf, mix(snare(0.3, 220), clap() * 0.8), t0 + b * beat, 0.55)
            for s in range(16):
                if bar % 4 == 3 and s >= 12:  # трэп-дробь хэтов
                    for q in range(2): place(buf, hat(0.03), t0 + (s + q / 2) * beat / 4, 0.4)
                else:
                    place(buf, hat(), t0 + s * beat / 4, 0.35 if s % 2 else 0.5)
            place(buf, bass808(hz(r - 12), 2 * beat, hz(r - 5) if bar % 2 else None), t0, 0.55)
            place(buf, bass808(hz(r - 12), 1.5 * beat), t0 + 2.5 * beat, 0.5)
        if bar >= 10 and (bar // 4) % 2 == 0:
            for i in range(8):
                n = mel[(i + bar) % 8]
                t = t_(beat / 2)
                x = (np.sin(2 * np.pi * hz(n) * t) + 0.3 * np.sin(4 * np.pi * hz(n) * t)) * np.exp(-t * 5)
                place(buf, x, t0 + i * beat / 2, 0.16)
    master(buf[:int(L * SR)], name)


# ---------------------------------------------------------------- Ночной Порт: лоу-фай
def lofi(name='nochnoy_port', v=0):
    bpm = [74, 70, 80][v]; beat = 60 / bpm; bars = 28
    L = bars * 4 * beat
    buf = np.zeros(int(L * SR) + SR)
    prog = [[[62, 65, 69, 72], [60, 64, 67, 71], [57, 60, 64, 67], [59, 62, 65, 69]],
            [[60, 64, 67, 71], [57, 60, 64, 67], [62, 65, 69, 72], [55, 59, 62, 65]],
            [[65, 69, 72, 76], [64, 67, 71, 74], [62, 65, 69, 72], [60, 64, 67, 71]]][v]
    for bar in range(bars):
        t0 = bar * 4 * beat
        ch = prog[bar % 4]
        for k, swing in ((0, 0), (2.5, 0.05)):
            for i, n in enumerate(ch):
                place(buf, epiano(hz(n), 2 * beat), t0 + (k + swing) * beat + i * 0.02, 0.14)
        place(buf, lowpass(tri(hz(ch[0] - 24), t_(3.5 * beat)), 500) * env(int(3.5 * beat * SR), 0.01, 0.3, 0.6, 0.3), t0, 0.5)
        if bar >= 1:
            for k in (0, 2.6): place(buf, lowpass(kick(0.4, 1), 2500), t0 + k * beat, 0.7)
            for b in (1, 3): place(buf, lowpass(snare(0.3, 180), 3500), t0 + b * beat + 0.02, 0.4)
            for s in range(8): place(buf, lowpass(hat(0.04), 6000), t0 + s * beat / 2 + (0.04 if s % 2 else 0), 0.3)
    # винил: шорох и щелчки
    crackle = rng.uniform(-1, 1, len(buf)) * 0.006
    pops = rng.random(len(buf)) < 0.00004
    crackle[pops] += rng.uniform(-0.25, 0.25, pops.sum())
    buf += lowpass(crackle, 5000)
    master(lowpass(buf[:int(L * SR)], 6500), name, 0.8)


# ---------------------------------------------------------------- Трасса 128: электро-хаус
def trassa(name='trassa128', v=0):
    bpm = [128, 124, 132][v]; beat = 60 / bpm; bars = 48
    L = bars * 4 * beat
    buf = np.zeros(int(L * SR) + SR)
    prog = [[(52, 'm'), (48, 'M'), (55, 'M'), (50, 'M')], [(57, 'm'), (53, 'M'), (48, 'M'), (55, 'M')], [(50, 'm'), (46, 'M'), (53, 'M'), (48, 'M')]][v]
    hook = [int(x) for x in rng.choice([71, 74, 76, 79, 81], 8)]
    for bar in range(bars):
        root, kind = prog[bar % 4]
        t0 = bar * 4 * beat
        sec = (bar // 8) % 6  # 0 интро, 1 нарастание, 2 дроп, 3 брейк, 4 нарастание, 5 дроп
        drums = sec in (0, 1, 2, 4, 5)
        if drums:
            for b in range(4): place(buf, kick(0.3, 1.3), t0 + b * beat, 1.0)
            for b in range(4): place(buf, hat(open_=True), t0 + b * beat + beat / 2, 0.25)
            if sec != 0:
                for b in (1, 3): place(buf, clap(), t0 + b * beat, 0.7)
        if sec in (1, 4) and bar % 8 == 7:  # подъём перед дропом
            for s in range(16): place(buf, snare(0.12), t0 + s * beat / 4, 0.15 + s * 0.03)
        # бас со «вдохом» (sidechain)
        for e in range(8):
            t = t_(beat / 2)
            x = lowpass(saw(hz(root - 12), t, 0.01), 700 if sec != 2 and sec != 5 else 1400)
            duck = np.minimum(1, t / 0.12)
            place(buf, x * duck * env(len(t), 0.003, 0.05, 0.9, 0.02), t0 + e * beat / 2, 0.5)
        if sec in (2, 5):
            for i in range(8):
                n = hook[(i + bar * 2) % 8]
                t = t_(beat / 2 * 0.9)
                x = lowpass(saw(hz(n), t, 0.012) + saw(hz(n + 12), t, 0.008) * 0.5, 5000) * env(len(t), 0.005, 0.1, 0.7, 0.05)
                place(buf, x, t0 + i * beat / 2, 0.22)
        if sec == 3:
            place(buf, pad([hz(n) for n in chord(root + 12, 'm' if kind == 'm' else 'M')], 4 * beat, 1800), t0, 0.4)
    master(buf[:int(L * SR)], name)


# ---------------------------------------------------------------- РОК-ВОЛНА: гитарный рок
def guitar(notes, dur, drive=4.0):
    """Перегруженная гитара: несколько пил + жёсткое клиппирование + фильтр «кабинета»."""
    t = t_(dur)
    x = sum(saw(hz(n), t, 0.004) for n in notes) / len(notes)
    x = np.tanh(x * drive)
    return lowpass(x, 3200) * env(len(t), 0.004, 0.1, 0.8, 0.05)


def rock(name, v):
    bpm = [138, 126, 146][v]; beat = 60 / bpm; bars = 40
    L = bars * 4 * beat
    buf = np.zeros(int(L * SR) + SR)
    prog = [[40, 43, 45, 38], [45, 41, 43, 40], [38, 41, 36, 43]][v]  # пауэр-аккорды (корни)
    riff = [int(x) for x in rng.choice([0, 0, 3, 5, 7, 10, 12], 8)]
    for bar in range(bars):
        t0 = bar * 4 * beat
        r = prog[(bar // 2) % 4]
        sec = (bar // 8) % 4
        if sec == 0:  # куплет: глушёные восьмые
            for e in range(8): place(buf, guitar([r + 12, r + 19], beat / 2 * 0.6, 3), t0 + e * beat / 2, 0.35)
        elif sec == 3:  # соло
            for e in range(8):
                n = r + 24 + riff[(e + bar) % 8]
                tt = t_(beat / 2)
                lead = np.tanh(saw(hz(n) * (1 + 0.006 * np.sin(2 * np.pi * 6 * tt)), tt) * 5)
                place(buf, lowpass(lead, 4000) * env(len(tt), 0.01, 0.1, 0.8, 0.05), t0 + e * beat / 2, 0.22)
            place(buf, guitar([r + 12, r + 19, r + 24], 4 * beat, 5), t0, 0.25)
        else:  # припев: открытые аккорды
            for k in (0, 1.5, 2, 3.5): place(buf, guitar([r + 12, r + 19, r + 24], beat * (1.5 if k in (0, 2) else 0.5), 5), t0 + k * beat, 0.38)
        for b in range(4):
            place(buf, kick(0.3, 1.1), t0 + b * beat if b % 2 == 0 else t0 + b * beat + beat / 2, 0.8)
            if b % 2: place(buf, snare(0.3, 200), t0 + b * beat, 0.7)
            place(buf, hat(open_=sec != 0), t0 + b * beat + beat / 2, 0.25)
            place(buf, hat(), t0 + b * beat, 0.25)
        for e in range(8): place(buf, lowpass(saw(hz(r), t_(beat / 2 * 0.9)), 600) * env(int(beat / 2 * 0.9 * SR), 0.003, 0.05, 0.8, 0.03), t0 + e * beat / 2, 0.4)
        if bar % 8 == 7: place(buf, mix(snare(0.8), hat(open_=True)), t0 + 3.5 * beat, 0.6)  # тарелка
    master(buf[:int(L * SR)], name)


# ---------------------------------------------------------------- ДЖАЗ «ПОЛНОЧЬ»: свинг
def jazz(name, v):
    bpm = [132, 118, 150][v]; beat = 60 / bpm; bars = 32
    L = bars * 4 * beat
    buf = np.zeros(int(L * SR) + SR)
    keys = [50, 53, 48][v]
    # ii–V–I–VI в мажоре, септаккорды
    prog = [[2, 5, 9, 12], [7, 11, 14, 17], [0, 4, 7, 11], [9, 13, 16, 19]]
    swing = beat * 0.66
    for bar in range(bars):
        t0 = bar * 4 * beat
        ch = [keys + 12 + n for n in prog[bar % 4]]
        # компинг пианино на слабые доли
        for k in (1.5, 3.66) if bar % 2 else (0.66, 2.5):
            for i, n in enumerate(ch): place(buf, epiano(hz(n), beat), t0 + k * beat + i * 0.01, 0.1)
        # шагающий бас
        walk = [ch[0] - 24, ch[1] - 24, ch[2] - 24, ch[0] - 23 + (1 if bar % 2 else -1)]
        for b, n in enumerate(walk):
            tt = t_(beat * 0.9)
            place(buf, np.sin(2 * np.pi * hz(n) * tt) * np.exp(-tt * 3) * env(len(tt), 0.01, 0.1, 0.8, 0.1), t0 + b * beat, 0.5)
        # райд со свингом и щётки
        for b in range(4):
            place(buf, hat(0.15), t0 + b * beat, 0.25)
            if b % 2: place(buf, hat(0.08), t0 + b * beat + swing, 0.2)
            if b % 2: place(buf, lowpass(snare(0.2, 180), 2500), t0 + b * beat, 0.18)
        # «саксофон»: пила с вибрато через мягкий фильтр
        if (bar // 4) % 2 == 1:
            for e in range(4):
                n = int(rng.choice(ch)) + 12 + int(rng.choice([0, 2, -1]))
                tt = t_(beat * 0.95)
                f = hz(n) * (1 + 0.008 * np.sin(2 * np.pi * 5.5 * tt) * np.minimum(1, tt * 3))
                ph = 2 * np.pi * np.cumsum(f) / SR
                sax = lowpass(np.tanh(np.sin(ph) * 2 + 0.4 * np.sin(2 * ph)), 2400)
                place(buf, sax * env(len(tt), 0.05, 0.1, 0.8, 0.15), t0 + e * beat + (0.08 if e % 2 else 0), 0.16)
    master(buf[:int(L * SR)], name, 0.8)


# ---------------------------------------------------------------- ПАЛЬМА FM: регги
def reggae(name, v):
    bpm = [76, 82, 72][v]; beat = 60 / bpm; bars = 32
    L = bars * 4 * beat
    buf = np.zeros(int(L * SR) + SR)
    prog = [[(55, 'M'), (60, 'M'), (62, 'M'), (60, 'M')], [(57, 'm'), (62, 'm'), (55, 'M'), (57, 'm')], [(53, 'M'), (55, 'M'), (57, 'm'), (55, 'M')]][v]
    for bar in range(bars):
        t0 = bar * 4 * beat
        r, kind = prog[bar % 4]
        for b in range(4):  # «скэнк» на слабую долю
            for i, n in enumerate(chord(r + 12, 'm' if kind == 'm' else 'M')):
                tt = t_(0.12)
                place(buf, lowpass(sq(hz(n), tt, 0.4), 2500) * np.exp(-tt * 25), t0 + b * beat + beat / 2 + i * 0.004, 0.1)
        # one drop: бочка с малым на 3
        place(buf, kick(0.4, 1.0), t0 + 2 * beat, 0.8)
        place(buf, snare(0.2, 240), t0 + 2 * beat, 0.4)
        for e in range(8): place(buf, hat(0.04), t0 + e * beat / 2 + (0.06 if e % 2 else 0), 0.2)
        # плотный бас
        line = [r - 12, r - 12, r - 5, r - 8]
        for b, n in enumerate(line):
            tt = t_(beat * 0.7)
            place(buf, lowpass(np.sin(2 * np.pi * hz(n) * tt) + 0.3 * tri(hz(n), tt), 400) * env(len(tt), 0.01, 0.1, 0.8, 0.1), t0 + b * beat + (0.5 * beat if b == 1 else 0), 0.6)
        if bar % 8 >= 4:  # мелодика
            for e in range(4):
                n = r + 24 + int(rng.choice([0, 4, 7, 9, 12])) - (3 if kind == 'm' and e % 2 else 0)
                tt = t_(beat * 0.8)
                place(buf, (sq(hz(n), tt, 0.3) * 0.5 + np.sin(2 * np.pi * hz(n) * tt)) * env(len(tt), 0.02, 0.1, 0.7, 0.1) * 0.6, t0 + e * beat, 0.12)
    master(buf[:int(L * SR)], name, 0.8)


# ---------------------------------------------------------------- КЛАССИКА: струнные и рояль
def strings(freqs, dur):
    t = t_(dur)
    x = sum(saw(f * (1 + 0.003 * np.sin(2 * np.pi * 5 * t + i)), t, 0.004) for i, f in enumerate(freqs)) / len(freqs)
    return lowpass(x, 1800) * env(len(t), 0.6, 0.4, 0.85, 0.8)


def piano(f, dur):
    t = t_(dur)
    x = np.sin(2 * np.pi * f * t) + 0.4 * np.sin(4 * np.pi * f * t) * np.exp(-t * 3) + 0.15 * np.sin(6 * np.pi * f * t) * np.exp(-t * 5)
    return x * np.exp(-t * 1.6) * env(len(t), 0.002, 0.1, 1, 0.2)


def classic(name, v):
    bpm = [66, 72, 60][v]; beat = 60 / bpm; bars = 24
    L = bars * 4 * beat
    buf = np.zeros(int(L * SR) + SR)
    prog = [[(57, 'm'), (53, 'M'), (60, 'M'), (55, 'M')], [(50, 'm'), (55, 'm'), (57, 'M'), (50, 'm')], [(48, 'M'), (57, 'm'), (53, 'M'), (55, 'M')]][v]
    for bar in range(bars):
        t0 = bar * 4 * beat
        r, kind = prog[bar % 4]
        ch = chord(r, 'm' if kind == 'm' else 'M')
        place(buf, strings([hz(n) for n in [ch[0] - 12] + ch + [ch[0] + 12]], 4 * beat), t0, 0.3)
        arp = ch + [ch[0] + 12, ch[1] + 12, ch[2] + 12, ch[1] + 12, ch[0] + 12]
        for e in range(8): place(buf, piano(hz(arp[e] + 12), beat * 1.2), t0 + e * beat / 2, 0.16)
        if bar >= 8 and bar % 2 == 0:  # мелодия скрипки
            for e in range(2):
                n = int(rng.choice(ch)) + 24
                tt = t_(beat * 2)
                f = hz(n) * (1 + 0.01 * np.sin(2 * np.pi * 6 * tt) * np.minimum(1, tt))
                ph = 2 * np.pi * np.cumsum(f) / SR
                place(buf, lowpass((ph / np.pi % 2 - 1), 3000) * env(len(tt), 0.2, 0.2, 0.8, 0.4), t0 + e * 2 * beat, 0.12)
    master(buf[:int(L * SR)], name, 0.75)


# ---------------------------------------------------------------- КАНТРИ-РОУД: банджо и «бум-чик»
def country(name, v):
    bpm = [112, 104, 120][v]; beat = 60 / bpm; bars = 32
    L = bars * 4 * beat
    buf = np.zeros(int(L * SR) + SR)
    prog = [[55, 60, 62, 55], [57, 62, 64, 57], [52, 57, 59, 52]][v]
    for bar in range(bars):
        t0 = bar * 4 * beat
        r = prog[bar % 4]
        ch = chord(r + 12, 'M')
        roll = [ch[0], ch[1], ch[2] + 12, ch[1], ch[0] + 12, ch[2], ch[1] + 12, ch[2]]
        for e in range(16):  # банджо-ролл шестнадцатыми
            tt = t_(beat / 4)
            n = roll[e % 8] + 12
            place(buf, lowpass(saw(hz(n), tt) * np.exp(-tt * 18) + np.sin(2 * np.pi * hz(n) * tt) * np.exp(-tt * 9), 5000), t0 + e * beat / 4, 0.07)
        for b in range(4):  # бум-чик
            if b % 2 == 0:
                tt = t_(beat * 0.8)
                place(buf, np.sin(2 * np.pi * hz(r - 12 + (7 if b == 2 else 0)) * tt) * np.exp(-tt * 4), t0 + b * beat, 0.45)
                place(buf, kick(0.25, 0.8), t0 + b * beat, 0.6)
            else:
                place(buf, lowpass(snare(0.15, 210), 4000), t0 + b * beat, 0.35)
                for i, n in enumerate(ch): place(buf, lowpass(saw(hz(n), t_(0.1)), 2500) * np.exp(-t_(0.1) * 20), t0 + b * beat + i * 0.008, 0.06)
        if bar % 8 >= 4:  # «скрипка»
            n = int(rng.choice(ch)) + 12
            tt = t_(beat * 3.5)
            f = hz(n) * (1 + 0.012 * np.sin(2 * np.pi * 6 * tt))
            ph = 2 * np.pi * np.cumsum(f) / SR
            place(buf, lowpass((ph / np.pi % 2 - 1), 3500) * env(len(tt), 0.15, 0.2, 0.8, 0.3), t0, 0.1)
    master(buf[:int(L * SR)], name, 0.8)


# ---------------------------------------------------------------- ТЕХНО «ПОДВАЛ»: кислотная линия
def acid(n, dur, cutoff):
    t = t_(dur)
    x = saw(hz(n), t)
    return np.tanh(lowpass(x, cutoff) * 3) * env(len(t), 0.003, 0.08, 0.6, 0.03)


def techno(name, v):
    bpm = [130, 136, 126][v]; beat = 60 / bpm; bars = 48
    L = bars * 4 * beat
    buf = np.zeros(int(L * SR) + SR)
    root = [45, 43, 48][v]
    pattern = [int(x) for x in rng.choice([0, 0, 12, 3, 7, 10, 15], 16)]
    accents = rng.random(16) < 0.35
    for bar in range(bars):
        t0 = bar * 4 * beat
        sec = (bar // 8) % 6
        for b in range(4):
            if sec != 3: place(buf, kick(0.28, 1.4), t0 + b * beat, 1.0)
            place(buf, hat(0.04), t0 + b * beat + beat / 2, 0.35)
            if sec >= 2 and b % 2: place(buf, clap(), t0 + b * beat, 0.4)
        if sec >= 1:
            sweep = 600 + 1800 * (0.5 + 0.5 * np.sin(bar / 4))
            for s_ in range(16):
                place(buf, acid(root + pattern[s_], beat / 4 * 0.9, sweep * (1.6 if accents[s_] else 1)), t0 + s_ * beat / 4, 0.22)
        if sec == 3:
            place(buf, pad([hz(root + 12), hz(root + 15), hz(root + 19)], 4 * beat, 1400), t0, 0.35)
        if sec in (4, 5):
            for s_ in range(16): place(buf, hat(0.02), t0 + s_ * beat / 4, 0.12)
    master(buf[:int(L * SR)], name)


STATIONS = [('neon88', neon), ('beat_ulic', beat_ulic), ('nochnoy_port', lofi), ('trassa128', trassa),
            ('rock_volna', rock), ('jazz_polnoch', jazz), ('palma_fm', reggae), ('klassika', classic), ('country_road', country), ('tekhno_podval', techno)]

if __name__ == '__main__':
    import sys
    only = sys.argv[1:]
    for base, fn in STATIONS:
        if only and base not in only:
            continue
        for v in range(3):
            T = [0, 3, -2][v]
            rng = np.random.default_rng(1000 + v * 17 + len(base))
            fn(f'{base}_{v}', v)
    # старые одиночные файлы больше не нужны
    for old in ('neon88', 'beat_ulic', 'nochnoy_port', 'trassa128'):
        f = os.path.join(OUT, old + '.ogg')
        if os.path.exists(f): os.remove(f)
