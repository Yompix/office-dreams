#!/usr/bin/env python3
"""Пробный ролик 15–20 с: атмосфера → мелькнувшая тварь → Двойник-Анна → скример. Жёсткий грейд, органический звук.

  /tmp/claude-0/ttsenv/bin/python tools/make_proof.py

Клипы: /tmp/claude-0/glukhoman-trailer2/clips/pf_*.mp4 (Autotest trailer -Pproof).
Звуки можно заменить настоящими записями: положи файл в /tmp/claude-0/glukhoman-trailer2/sfx_real/<имя>.wav|.ogg|.mp3
(имена: wind, step, breath, creak, swish, tap, hit, click, drone, room). Иначе — синтез из шума (без тонов и «бипов»).
"""
import os, subprocess, tempfile
import numpy as np
from scipy.signal import butter, sosfilt, fftconvolve

OUT = '/tmp/claude-0/glukhoman-trailer2'
CLIPS = f'{OUT}/clips'
REAL = f'{OUT}/sfx_real'
SR = 48000
rng = np.random.default_rng(7)
# сцена, начало, длина
EDL = [('play', 0, 1.2), ('pf_path', 0.0, 5.3), ('tape', 0, 0.13), ('pf_glimpse', 0.0, 3.4), ('tape', 0, 0.1), ('pf_double', 0.0, 5.5),
       ('tape', 0, 0.16), ('pf_scare', 0.0, 4.77), ('black', 0, 1.3)]
FONT = '/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf'
# «камкордер»: REC с мигающей точкой, дата и бегущее время, SP — поверх всего, кроме карточки PLAY
OVERLAY = ("drawtext=fontfile=F:text='REC':fontsize=46:fontcolor=white@0.85:x=w-200:y=60:enable='gte(t,1.2)',"
           "drawtext=fontfile=F:text='●':fontsize=46:fontcolor=0xD02020:x=w-250:y=58:enable='gte(t,1.2)*lt(mod(t,1),0.5)',"
           "drawtext=fontfile=F:text='14.10.1996  03\\:47\\:%{eif\\:mod(t+12,60)\\:d\\:2}':fontsize=40:fontcolor=white@0.8:x=w-560:y=h-100:enable='gte(t,1.2)',"
           "drawtext=fontfile=F:text='SP':fontsize=40:fontcolor=white@0.7:x=80:y=h-100:enable='gte(t,1.2)'").replace('fontfile=F', 'fontfile=' + FONT)
# грейд: продавленные чёрные, холодная бирюза, мало цвета, зерно, виньетка, мерцание, лёгкая мягкость оптики
GRADE = ("eq=gamma=1.45:saturation=0.6:contrast=1.06,curves=all='0/0 0.04/0.025 0.5/0.52 1/0.95',"
         "colorbalance=rs=-0.04:gs=0.0:bs=0.05:rm=-0.03:bm=0.03,"
         "eq=brightness='-0.01*abs(sin(t*23))-0.015*lt(mod(t*7\\,3)\\,0.2)':eval=frame,"
         "gblur=sigma=0.5,noise=alls=8:allf=t,vignette=angle=PI/5")


def run(c):
    subprocess.run(c, check=True)


def t_(s):
    return np.arange(int(s * SR)) / SR


def bp(x, a, b, o=2):
    return sosfilt(butter(o, [a, b], 'band', fs=SR, output='sos'), x)


def lp(x, f, o=2):
    return sosfilt(butter(o, f, 'low', fs=SR, output='sos'), x)


def hp(x, f, o=2):
    return sosfilt(butter(o, f, 'high', fs=SR, output='sos'), x)


def env(n, a, r):
    e = np.ones(n)
    ai, ri = max(1, int(a * SR)), max(1, int(r * SR))
    e[:ai] = np.linspace(0, 1, ai)
    e[-ri:] *= np.linspace(1, 0, ri)
    return e


def room(x, size=0.6, mix=0.25):
    ir = rng.standard_normal(int(size * SR)) * np.exp(-t_(size) * 6 / size)
    y = fftconvolve(x, lp(ir, 4000))[:len(x)]
    return x * (1 - mix) + y / (np.abs(y).max() + 1e-9) * np.abs(x).max() * mix


def real(name):
    for ext in ('wav', 'ogg', 'mp3', 'flac'):
        p = f'{REAL}/{name}.{ext}'
        if os.path.exists(p):
            raw = subprocess.run(['ffmpeg', '-loglevel', 'error', '-i', p, '-f', 's16le', '-ac', '1', '-ar', str(SR), '-'], capture_output=True).stdout
            return np.frombuffer(raw, np.int16).astype(np.float64) / 32768
    return None


# ---------- синтез из шума ----------
def wind(d):
    n = rng.standard_normal(int(d * SR))
    gust = lp(rng.standard_normal(len(n)), 0.5, 1)
    gust = 0.5 + 0.5 * gust / (np.abs(gust).max() + 1e-9)
    return (bp(n, 120, 700) * 0.7 + bp(n, 900, 2400) * 0.12 * gust) * gust


def step():
    """Шаг по мёрзлой хвое и снегу: короткий хруст из множества мелких щелчков."""
    d = 0.32
    x = np.zeros(int(d * SR))
    for _ in range(40):
        i = int(rng.uniform(0, 0.16) * SR)
        g = rng.standard_normal(int(0.004 * SR)) * rng.uniform(0.2, 1)
        x[i:i + len(g)] += g
    return bp(x, 500, 5000) * env(len(x), 0.005, 0.15) + lp(rng.standard_normal(len(x)), 180) * np.exp(-t_(d) * 25) * 0.8


def breath(d=1.6, g=1.0):
    t = t_(d)
    n = bp(rng.standard_normal(len(t)), 400, 2600)
    e = np.sin(np.pi * np.clip(t / (d * 0.45), 0, 1)) ** 2 * (t < d * 0.45) + 0.7 * np.sin(np.pi * np.clip((t - d * 0.55) / (d * 0.45), 0, 1)) ** 2 * (t > d * 0.55)
    return n * e * g


def creak(d=1.4):
    """Скрип дерева: узкая полоса шума, медленно плывущая по частоте (без тона)."""
    n = rng.standard_normal(int(d * SR))
    out = np.zeros_like(n)
    for k in range(0, len(n), 1200):
        f = 300 + 140 * np.sin(k / len(n) * np.pi * 2.3)
        out[k:k + 1200] = bp(n[k:k + 1200], f, f * 1.25, 4)
    return out * env(len(n), 0.2, 0.5) * 2.5


def swish():
    d = 0.35
    return bp(rng.standard_normal(int(d * SR)), 600, 4000) * np.sin(np.pi * t_(d) / d) ** 3


def tap():
    x = bp(rng.standard_normal(int(0.06 * SR)), 1800, 6000) * np.exp(-t_(0.06) * 80)
    return room(np.concatenate([x, np.zeros(int(0.3 * SR))]), 0.4, 0.3)


def click():
    return hp(rng.standard_normal(int(0.012 * SR)), 2000) * np.exp(-t_(0.012) * 400) * 0.6


def drone(d):
    n = rng.standard_normal(int(d * SR))
    return lp(n, 70, 4) * 3 + bp(n, 90, 160, 4) * 0.8


def hit():
    """Удар скримера: хлёсткий шумовой транзиент, низкий удар, рваный хвост — без тонов."""
    d = 1.4
    t = t_(d)
    n = rng.standard_normal(len(t))
    trans = hp(n, 800) * np.exp(-t * 18) * 1.5
    body = lp(n, 220, 4) * np.exp(-t * 4) * 4
    low = lp(rng.standard_normal(len(t)), 60, 4) * np.exp(-t * 3) * 6
    tear = bp(n, 1200, 6000) * np.exp(-t * 7) * (1 + 0.8 * (rng.random(len(t)) > 0.7))
    return np.tanh((trans + body + low + tear) * 1.5)


def sfx(name, fn):
    r = real(name)
    return r if r is not None else fn()


def place(buf, s, at, g=1.0):
    i = int(at * SR)
    if i >= len(buf):
        return
    n = min(len(s), len(buf) - i)
    buf[i:i + n] += s[:n] * g


def mix(times, total):
    a = np.zeros(int(total * SR))
    st = {s: (x, y) for s, x, y in times}
    p0, p1 = st['pf_path']
    g0, g1 = st['pf_glimpse']
    d0, d1 = st['pf_double']
    s0, s1 = st['pf_scare']
    place(a, sfx('wind', lambda: wind(g1)) * 0.9, 0)
    for k in np.arange(0.3, p1 - 0.2, 0.56):
        place(a, sfx('step', step), k + rng.uniform(-0.03, 0.03), 0.55)
    place(a, sfx('breath', lambda: breath(1.7)), 1.2, 0.35)
    place(a, sfx('creak', creak), 3.4, 0.35)
    place(a, sfx('drone', lambda: drone(g1 - g0 + 1)) * np.linspace(0, 1, int((g1 - g0 + 1) * SR)), g0, 0.5)
    place(a, sfx('swish', swish), g0 + 2.2, 0.6)
    place(a, sfx('room', lambda: drone(d1 - d0)) * 0.3, d0, 0.5)               # тишина избы
    place(a, sfx('breath', lambda: breath(1.5, 0.7)), d0 + 0.6, 0.3)
    place(a, sfx('tap', tap), d0 + 4.3, 0.6)                                       # стук по стеклу
    place(a, sfx('wind', lambda: wind(2.5)) * 0.35, s0, 1.0)
    place(a, sfx('breath', lambda: breath(1.8, 0.8)), s0 + 0.8, 0.4)
    for k in np.arange(s0 + 3.0, s0 + 3.8, 0.14):
        place(a, sfx('click', click), k, 0.5)
    i0, i1 = int((s0 + 3.85) * SR), int((s0 + 4.6) * SR)
    a[i0:i1] *= 0.05                                                                 # полная тишина в темноте
    place(a, sfx('hit', hit), s0 + 4.6, 1.0)
    for (s_, x, y) in times:
        if s_ == 'tape':
            place(a, hp(rng.standard_normal(int((y - x) * SR)), 1500) * 0.5, x)
            place(a, click() * 2, x)
    b0 = st['black'][0]
    a[int((b0 + 0.25) * SR):] *= np.linspace(1, 0, len(a) - int((b0 + 0.25) * SR)) ** 2
    return a


def main():
    os.makedirs(f'{OUT}/stills', exist_ok=True)
    with tempfile.TemporaryDirectory(dir='/tmp/claude-0') as t:
        # один проход: обрезка клипов, склейка, грейд, звук — без промежуточных файлов (зерно раздувает их до сотен МБ)
        ins, parts, times, tt = [], [], [], 0.0
        for i, (s_, a_, d) in enumerate(EDL):
            if s_ == 'black':
                ins += ['-f', 'lavfi', '-t', str(d), '-i', 'color=c=black:s=1920x1080:r=30']
            elif s_ == 'play':
                ins += ['-f', 'lavfi', '-t', str(d), '-i', 'color=c=0x05060a:s=1920x1080:r=30']
            elif s_ == 'tape':   # склейка плёнки: серый «снег»
                ins += ['-f', 'lavfi', '-t', str(d), '-i', 'nullsrc=s=1920x1080:r=30,geq=lum=random(1)*200:cb=128:cr=128']
            else:
                ins += ['-ss', str(a_), '-t', str(d), '-i', f'{CLIPS}/{s_}.mp4']
            extra = (f",drawtext=fontfile={FONT}:text='▶ PLAY':fontsize=64:fontcolor=white:x=90:y=80" if s_ == 'play' else '')
            parts.append(f'[{i}:v]fps=30,setsar=1,format=yuv420p{extra}[v{i}]')
            times.append((s_, tt, tt + d))
            tt += d
        n = len(EDL)
        a = mix(times, tt)
        a /= np.abs(a).max() + 1e-9
        st = np.stack([a, np.roll(a, int(0.01 * SR)) * 0.95], 1)
        (np.clip(st, -1, 1) * 32767).astype(np.int16).tofile(f'{t}/a.raw')
        fc = ';'.join(parts) + ';' + ''.join(f'[v{i}]' for i in range(n)) + f'concat=n={n}:v=1:a=0[c];[c]{GRADE},' + OVERLAY + f'[v];[{n}:a]loudnorm=I=-16:TP=-2:LRA=15,alimiter=limit=0.79:attack=1:release=60:level=0[a]'
        run(['ffmpeg', '-y', '-loglevel', 'error'] + ins + ['-f', 's16le', '-ar', str(SR), '-ac', '2', '-i', f'{t}/a.raw', '-filter_complex', fc,
             '-map', '[v]', '-map', '[a]', '-c:v', 'libx264', '-crf', '26', '-preset', 'slow', '-tune', 'grain', '-maxrate', '14M', '-bufsize', '28M', '-pix_fmt', 'yuv420p', '-c:a', 'aac', '-b:a', '192k',
             '-shortest', '-movflags', '+faststart', f'{OUT}/proof.mp4'])
        # 6 кадров для просмотра
        off = {s_: x for s_, x, y in times}
        for k, (name, sec) in enumerate((('path', off['pf_path'] + 2.6), ('glimpse', off['pf_glimpse'] + 1.2), ('double_silhouette', off['pf_double'] + 1.5),
                                            ('double_closeup', off['pf_double'] + 5.0), ('scare_dark', off['pf_scare'] + 4.2), ('scare_hit', off['pf_scare'] + 4.68))):
            run(['ffmpeg', '-y', '-loglevel', 'error', '-ss', str(sec), '-i', f'{OUT}/proof.mp4', '-frames:v', '1', f'{OUT}/stills/{k + 1}_{name}.png'])
        print('ok proof.mp4', round(tt, 1), 's')


if __name__ == '__main__':
    main()
