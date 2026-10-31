#!/usr/bin/env python3
"""Музыка WHAT THE LAKE KEEPS — своя, целиком кодом (инструменты — tools/music/compose.py: пианино, пэд,
музыкальная шкатулка, гул, реверберация). Никаких сэмплов и чужих записей.

  python3 tools/lake/music.py [имя …]   → godot/lake/snd/<имя>.ogg   (без имён — все)

lake_theme — меню (петля 38,4 с, ля минор, 3/4): мамина колыбельная на шкатулке, которая «плывёт», как воспоминание;
             под ней — низкое пианино и тёмный пэд. Тот же мотив Айрис напевает в машине.
lake_end   — карта главы (≈40 с): одно пианино, мотив медленно, в конце не разрешается — повисает на «ми».
radio_song — глава 2: песня по радио, под которую Зоя подпевает (петля 32 с, ре мажор, 120 ударов): барабаны, бас,
             электропиано, синтезатор-мелодия — и всё через «динамик магнитолы» (узкая полоса, перегруз, шипение)."""
import os, subprocess, sys
import numpy as np

sys.path.insert(0, os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'music'))
import compose as C   # noqa: E402

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '../../godot/lake/snd')
BEAT = 0.8            # 75 ударов в минуту, такт 3/4 = 2,4 с
BAR = 3 * BEAT
# колыбельная: (нота, доля от начала, длина в долях); 16 тактов
LULLABY = [('E5', 0, 2), ('D5', 2, 1), ('C5', 3, 2), ('B4', 5, 1), ('A4', 6, 3), ('E4', 10, 1), ('A4', 11, 1),
           ('C5', 12, 1), ('B4', 13, 1), ('A4', 14, 1), ('G4', 15, 2), ('A4', 17, 1), ('E4', 18, 3),
           ('A4', 24, 1), ('B4', 25, 1), ('C5', 26, 1), ('D5', 27, 2), ('C5', 29, 1), ('B4', 30, 2), ('G4', 32, 1), ('A4', 33, 3),
           ('E5', 36, 2), ('D5', 38, 1), ('C5', 39, 1), ('B4', 40, 1), ('G#4', 41, 1), ('A4', 42, 3)]
CHORDS = [(['A2', 'E3', 'C4'], 'A1'), (['F2', 'C3', 'A3'], 'F1'), (['C3', 'G3', 'E4'], 'C2'), (['E2', 'B2', 'G#3'], 'E1'),
          (['F2', 'C3', 'A3'], 'F1'), (['D3', 'A3', 'F4'], 'D2'), (['E2', 'B2', 'G#3'], 'E1'), (['A2', 'E3', 'C4'], 'A1')]


def lake_theme(tr, t0):
    tr.add(t0 - 2, C.drone(C.hz('A1'), 16 * BAR + 4, vel=.16))
    for i, (ch, bass) in enumerate(CHORDS):   # аккорд на два такта
        at = t0 + i * 2 * BAR
        tr.add(at, C.pad([C.hz(n) for n in ch], 2 * BAR + .3, vel=.2, bright=900, att=1.8, rel=3.0), pan=-.15)
        tr.add(at, C.piano(C.hz(bass), 2 * BAR - .2, vel=.3, bright=.6), pan=-.3)
        tr.add(at + BAR, C.piano(C.hz(ch[1]), BAR, vel=.14, bright=.6), pan=-.1)
    for n, b, d in LULLABY:   # шкатулка: мотив «плывёт» (wow), как заводная из детства
        tr.add(t0 + b * BEAT + C.rng.uniform(-.012, .012), C.musicbox(C.hz(n) * 2, d * BEAT + 1.2, vel=.24, wow=.004, t0=b), pan=.25)
    for n, b, d in LULLABY[13:]:   # во второй половине мотив тихо вторит пианино октавой ниже
        tr.add(t0 + b * BEAT + .03, C.piano(C.hz(n) / 2, d * BEAT, vel=.16, bright=.7), pan=.05)


def lake_end(tr, t0):
    slow = BEAT * 1.35
    tr.add(t0, C.pad([C.hz('A2'), C.hz('E3')], 30, vel=.1, bright=600, att=6, rel=6), pan=-.2)
    for i, (n, b, d) in enumerate(LULLABY[:13]):
        tr.add(t0 + 1.0 + b * slow, C.piano(C.hz(n), d * slow, vel=.36 - .01 * i, bright=.75), pan=.1)
    end = 1.0 + 21 * slow
    for k, n in enumerate(['A2', 'C3', 'E3']):
        tr.add(t0 + end + k * .12, C.piano(C.hz(n), 6, vel=.2, bright=.6), pan=-.15)
    tr.add(t0 + end + 3.0, C.piano(C.hz('E4'), 5, vel=.22, bright=.7), pan=.2)   # повисает — не разрешается


Q = 0.5   # радиопесня: 120 ударов в минуту, такт 4/4 = 2 с
SONG = [('A4', 0, 1), ('F#4', 1, .5), ('A4', 1.5, .5), ('B4', 2, 1), ('A4', 3, 1),
        ('E4', 4, 1), ('E4', 5, .5), ('F#4', 5.5, .5), ('A4', 6, 1.5), ('G4', 7.5, .5),
        ('F#4', 8, 1), ('D4', 9, 1), ('F#4', 10, .5), ('G4', 10.5, .5), ('A4', 11, 1),
        ('B4', 12, 1.5), ('A4', 13.5, .5), ('G4', 14, 1), ('F#4', 15, 1),
        ('A4', 16, 1), ('F#4', 17, .5), ('A4', 17.5, .5), ('D5', 18, 1), ('B4', 19, 1),
        ('C#5', 20, 1), ('B4', 21, .5), ('A4', 21.5, .5), ('E4', 22, 2),
        ('F#4', 24, 1), ('A4', 25, 1), ('B4', 26, .5), ('A4', 26.5, .5), ('F#4', 27, 1),
        ('G4', 28, 1), ('F#4', 29, 1), ('E4', 30, 1), ('D4', 31, 1),
        ('D5', 32, 1.5), ('C#5', 33.5, .5), ('B4', 34, 1), ('A4', 35, 1),
        ('C#5', 36, 1.5), ('B4', 37.5, .5), ('A4', 38, 2),
        ('B4', 40, 1), ('A4', 41, 1), ('F#4', 42, 1), ('D5', 43, 1),
        ('D5', 44, 1), ('C#5', 45, .5), ('B4', 45.5, .5), ('A4', 46, 2),
        ('D5', 48, 1.5), ('C#5', 49.5, .5), ('B4', 50, 1), ('A4', 51, 1),
        ('C#5', 52, 1.5), ('E5', 53.5, .5), ('D5', 54, 2),
        ('B4', 56, 1), ('D5', 57, 1), ('C#5', 58, 1), ('B4', 59, 1),
        ('A4', 60, 2), ('F#4', 62, 2)]
SONG_CHORDS = [(['D3', 'F#3', 'A3'], 'D2'), (['C#3', 'E3', 'A3'], 'A1'), (['D3', 'F#3', 'B3'], 'B1'), (['D3', 'G3', 'B3'], 'G1')]


def _kick():
    n = int(.35 * C.SR); t = np.arange(n) / C.SR
    f = 45 + 80 * np.exp(-t / .03)
    return (np.sin(2 * np.pi * np.cumsum(f) / C.SR) * np.exp(-t / .16) + .3 * np.exp(-t / .004) * C.rng.normal(0, 1, n)) * .9


def _snare():
    n = int(.3 * C.SR); t = np.arange(n) / C.SR
    nz = C.rng.normal(0, 1, n)
    body = C.lowpass(nz, 6000) - C.lowpass(nz, 900)
    return body * np.exp(-t / .09) * .55 + np.sin(2 * np.pi * 190 * t) * np.exp(-t / .04) * .4


def _hat(open_=False):
    n = int((.25 if open_ else .06) * C.SR); t = np.arange(n) / C.SR
    nz = C.rng.normal(0, 1, n)
    return (nz - C.lowpass(nz, 7000)) * np.exp(-t / (.08 if open_ else .018)) * .22


def _bass(f, dur):
    n = int((dur + .05) * C.SR); t = np.arange(n) / C.SR
    x = sum(np.sin(2 * np.pi * h * f * t) / h for h in range(1, 8))
    x = C.lowpass(x, 700) * np.minimum(1, t / .005) * np.where(t < dur, 1, np.exp(-(t - dur) / .02))
    return x * .5


def _lead(f, dur):
    n = int((dur + .12) * C.SR); t = np.arange(n) / C.SR
    vib = 1 + .006 * np.sin(2 * np.pi * 5.5 * t) * np.minimum(1, t / .25)
    ph = 2 * np.pi * f * np.cumsum(vib) / C.SR
    x = sum(np.sin((2 * k - 1) * ph) / (2 * k - 1) for k in range(1, 7))   # «квадрат»: нечётные гармоники
    x = C.lowpass(x, 2600) * np.minimum(1, t / .012) * np.where(t < dur, 1, np.exp(-(t - dur) / .05))
    return x * .3


def radio_song(tr, t0):
    for bar in range(16):
        at = t0 + bar * 4 * Q
        ch, bass = SONG_CHORDS[bar % 4]
        tr.add(at, C.pad([C.hz(nn) for nn in ch], 4 * Q, vel=.12, bright=1600, att=.05, rel=.4), pan=-.1)
        for k in (0, 1.5, 2.5, 3.5):   # электропиано: «раз», и синкопы
            for nn in ch:
                tr.add(at + k * Q, C.piano(C.hz(nn) * 2, .4 * Q if k else .9 * Q, vel=.12, bright=.85), pan=.15)
        for e in range(8):   # бас восьмыми
            tr.add(at + e * Q / 2, _bass(C.hz(bass) * (2 if e % 4 == 3 else 1), Q / 2 * .85), pan=0)
        for b in (0, 2, 2.5):
            tr.add(at + b * Q, _kick())
        for b in (1, 3):
            tr.add(at + b * Q, _snare(), pan=.05)
        for e in range(8):
            tr.add(at + e * Q / 2, _hat(e == 7), pan=.3)
    for nn, b, d in SONG:
        tr.add(t0 + b * Q, _lead(C.hz(nn), d * Q * .92), pan=-.05)


def radio_fx(y):
    """Через динамик магнитолы: моно, полоса 300–3500 Гц, лёгкий перегруз, тихое шипение эфира."""
    m = y.mean(1)
    X = np.fft.rfft(m); f = np.fft.rfftfreq(len(m), 1 / C.SR)
    X *= 1 / np.sqrt(1 + (300 / np.maximum(f, 1)) ** 4) / np.sqrt(1 + (f / 3500) ** 4)
    m = np.fft.irfft(X, len(m))
    m = np.tanh(m / (np.max(np.abs(m)) + 1e-9) * 1.4) / np.tanh(1.4)
    hiss = C.rng.normal(0, 1, len(m)); hiss = (hiss - C.lowpass(hiss, 2500)) * .025
    m = (m + hiss) * .85
    return np.stack([m, m], 1)


PIECES = {'lake_theme': (lake_theme, 16 * BAR, True, .42, 4.0, -21), 'lake_end': (lake_end, 40, False, .45, 4.5, -22),
          'radio_song': (radio_song, 64 * Q, True, .12, 1.1, -18)}


def save(name, y):
    path = os.path.join(OUT, name + '.ogg')
    pcm = (np.clip(y, -1, 1) * 32767).astype('<i2').tobytes()
    subprocess.run(['ffmpeg', '-nostdin', '-v', 'error', '-y', '-f', 's16le', '-ar', str(C.SR), '-ac', '2', '-i', '-',
                    '-c:a', 'libvorbis', '-q:a', '5', path], input=pcm, check=True)
    print(name, f'{len(y) / C.SR:.1f} с', path)


if __name__ == '__main__':
    for name in (sys.argv[1:] or list(PIECES)):
        build, length, loop, wet, rt, target = PIECES[name]
        y = C.render(build, length, loop, wet, rt, target)
        save(name, radio_fx(y) if name == 'radio_song' else y)
