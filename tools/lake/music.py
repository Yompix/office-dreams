#!/usr/bin/env python3
"""Музыка WHAT THE LAKE KEEPS — своя, целиком кодом (инструменты — tools/music/compose.py: пианино, пэд,
музыкальная шкатулка, гул, реверберация). Никаких сэмплов и чужих записей.

  python3 tools/lake/music.py [имя …]   → godot/lake/snd/<имя>.ogg   (без имён — все)

lake_theme — меню (петля 38,4 с, ля минор, 3/4): мамина колыбельная на шкатулке, которая «плывёт», как воспоминание;
             под ней — низкое пианино и тёмный пэд. Тот же мотив Айрис напевает в машине.
lake_end   — карта главы (≈40 с): одно пианино, мотив медленно, в конце не разрешается — повисает на «ми»."""
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


PIECES = {'lake_theme': (lake_theme, 16 * BAR, True, .42, 4.0, -21), 'lake_end': (lake_end, 40, False, .45, 4.5, -22)}


def save(name, y):
    path = os.path.join(OUT, name + '.ogg')
    pcm = (np.clip(y, -1, 1) * 32767).astype('<i2').tobytes()
    subprocess.run(['ffmpeg', '-nostdin', '-v', 'error', '-y', '-f', 's16le', '-ar', str(C.SR), '-ac', '2', '-i', '-',
                    '-c:a', 'libvorbis', '-q:a', '5', path], input=pcm, check=True)
    print(name, f'{len(y) / C.SR:.1f} с', path)


if __name__ == '__main__':
    for name in (sys.argv[1:] or list(PIECES)):
        build, length, loop, wet, rt, target = PIECES[name]
        save(name, C.render(build, length, loop, wet, rt, target))
