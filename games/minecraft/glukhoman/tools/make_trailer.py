#!/usr/bin/env python3
"""Монтаж трейлера «Глухомани»: клипы из игры (Autotest trailer) + титры + музыка, сгенерированная кодом.

  /tmp/claude-0/ttsenv/bin/python tools/make_trailer.py [h|v|en]

h — трейлер 16:9 1920×1080, v — Shorts 9:16 1080×1920, en — трейлер 16:9 с английскими титрами.
Клипы: /tmp/claude-0/glukhoman-trailer/clips/{h,v}_<сцена>.mp4. Звуки игры — из src/main/resources/assets/glukhoman/sounds
(только синтезированные кодом; голоса XTTS в трейлере не используются). Громкость — −14 LUFS.
"""
import os, subprocess, sys, tempfile
import numpy as np
from scipy.signal import butter, sosfilt, fftconvolve

HERE = os.path.dirname(os.path.abspath(__file__))
SND = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'glukhoman', 'sounds')
OUT = '/tmp/claude-0/glukhoman-trailer'
CLIPS = f'{OUT}/clips'
SR = 48000
FONT = '/usr/share/fonts/truetype/dejavu/DejaVuSerif-Bold.ttf'
FONT2 = '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'
rng = np.random.default_rng(13)
MODE = sys.argv[1] if len(sys.argv) > 1 else 'h'
VERT = MODE == 'v'
W, H = (1080, 1920) if VERT else (1920, 1080)
FPS = 30

T = {  # титры: ru / en
    'oct': ('Октябрь 1996', 'October 1996'),
    'silent': ('Посёлок Еловый не выходит на связь', 'The village of Yelovy has gone silent'),
    'quote': ('«Если вы это слышите — не отвечайте,\nкогда вас позовут по имени»', '"If you can hear this — don\'t answer\nwhen it calls your name"'),
    'learns': ('Оно учится.', 'It learns.'),
    'voices': ('Оно умеет голосами.', 'It knows our voices.'),
    'forest': ('Не смотри в лес.', 'Don\'t look into the forest.'),
    'tag': ('Хоррор с сюжетом · 5 глав · соло и кооператив', 'Story horror · 5 chapters · solo and co-op'),
    'mc': ('Minecraft Java 1.21.1 · мод от Yompix Games', 'Minecraft Java 1.21.1 · a mod by Yompix Games'),
    'nm': ('Не связано с Mojang и Microsoft', 'Not affiliated with Mojang or Microsoft'),
}


def tr(k):
    return T[k][1 if MODE == 'en' else 0]


# ---------------- монтажный лист ----------------
# ('card', [строки], длина, крупно?) | ('clip', сцена, с, длина, подпись) | ('black', длина) | ('title', длина)
if not VERT:
    EDL = [('card', [tr('oct')], 2.2, False), ('clip', 'village', 0.0, 10.0, ''), ('card', [tr('silent')], 2.6, False), ('clip', 'tr_radio', 0.0, 5.0, tr('quote')), ('card', [tr('learns')], 1.8, True),
           ('clip', 'knock', 0.0, 10.0, ''), ('card', [tr('voices')], 1.8, True), ('clip', 'tr_path', 0.0, 5.0, ''),
           ('card', [tr('forest')], 1.5, True), ('clip', 'corridor', 3.5, 3.2, ''),
           ('clip', 'tr_listener', 1.2, 3.4, ''), ('clip', 'anna', 4.0, 3.0, ''), ('clip', 'tr_doppel', 1.8, 3.2, ''), ('clip', 'bunker', 2.0, 4.0, ''),
           ('black', 1.6), ('clip', 'tr_scare', 0.0, 4.0, ''), ('title', 6.0), ('card', [tr('tag'), tr('mc'), tr('nm')], 5.0, False)]
else:
    EDL = [('clip', 'tr_doppel', 1.6, 2.6, 'НЕ ОТВЕЧАЙ,\nЕСЛИ ПОЗОВУТ\nПО ИМЕНИ'), ('clip', 'knock', 0.0, 9.0, 'Она стучит\nв три часа ночи'),
           ('clip', 'village', 0.0, 6.0, 'Свет гаснет сам'), ('clip', 'corridor', 3.5, 3.2, 'Не отворачивайся'),
           ('clip', 'tr_listener', 1.2, 3.4, 'Оно слепое.\nНе шуми.'), ('clip', 'bunker', 2.0, 4.5, 'Хор'), ('black', 1.0),
           ('clip', 'tr_scare', 0.8, 2.6, ''), ('title', 5.0)]


def run(cmd):
    subprocess.run(cmd, check=True)


def esc(s):
    return s.replace('\\', '\\\\').replace(':', '\\:').replace("'", "’").replace('%', '\\%')


def text_filter(lines, size, y, color='white', font=FONT, box=False):
    fs = []
    total = len(lines) * size * 1.35
    for i, l in enumerate(lines):
        yy = f'{y}-{total / 2:.0f}+{i * size * 1.35:.0f}'
        f = f"drawtext=fontfile={font}:text='{esc(l)}':fontsize={size}:fontcolor={color}:x=(w-text_w)/2:y={yy}"
        if box:
            f += ':box=1:boxcolor=black@0.45:boxborderw=18'
        fs.append(f)
    return fs


def segment(i, seg, tmp):
    out = f'{tmp}/s{i:02d}.mp4'
    kind = seg[0]
    fade = []
    if kind in ('card', 'title', 'black'):
        dur = seg[2] if kind == 'card' else seg[1]
        vf = []
        if kind == 'card':
            lines = [l for x in seg[1] for l in x.split('\n')]
            big = seg[3]
            size = (110 if VERT else 84) if big else (62 if VERT else 60)
            vf += text_filter(lines, size, 'h/2', 'white@0.92', FONT if big else FONT2)
            vf += [f'fade=t=in:st=0:d=0.35', f'fade=t=out:st={dur - 0.35:.2f}:d=0.35']
        elif kind == 'title':
            vf += text_filter(['ГЛУХОМАНЬ' if MODE != 'en' else 'GLUKHOMAN'], 128 if VERT else 170, 'h/2-40', '0xE8E0D0', FONT)
            sub = 'хоррор-мод для Minecraft' if VERT else ('Не отвечай, если позовут по имени.' if MODE != 'en' else 'Don\'t answer when it calls your name.')
            vf += text_filter([sub], 56 if VERT else 46, 'h/2+130', '0xB0A8A0', FONT2)
            if VERT:
                vf += text_filter(['Yompix Games · не связано с Mojang'], 40, 'h-220', '0x808080', FONT2)
            vf += ['fade=t=in:st=0:d=0.6']
        run(['ffmpeg', '-y', '-loglevel', 'error', '-f', 'lavfi', '-i', f'color=c=black:s={W}x{H}:r={FPS}:d={dur}',
             '-vf', ','.join(vf) if vf else 'null', '-c:v', 'libx264', '-crf', '17', '-pix_fmt', 'yuv420p', out])
        return out, dur
    _, scene, start, dur, cap = seg
    src = f'{CLIPS}/{"v" if VERT else "h"}_{scene}.mp4'
    vf = [f'scale={W}:{H}:flags=lanczos', 'setsar=1']
    if not VERT:
        vf.append(f'drawbox=x=0:y=0:w=iw:h=110:color=black:t=fill,drawbox=x=0:y=ih-110:w=iw:h=110:color=black:t=fill')
    if cap:
        lines = cap.split('\n')
        if VERT:
            vf += text_filter(lines, 96, '360', 'white', FONT, box=True)
        else:
            vf += text_filter(lines, 44, 'h-230', 'white@0.95', FONT, box=False)
    vf += [f'fade=t=in:st=0:d=0.25', f'fade=t=out:st={dur - 0.25:.2f}:d=0.25']
    run(['ffmpeg', '-y', '-loglevel', 'error', '-ss', f'{start}', '-t', f'{dur}', '-i', src, '-vf', ','.join(vf), '-r', str(FPS),
         '-c:v', 'libx264', '-crf', '21', '-preset', 'slow', '-pix_fmt', 'yuv420p', '-an', out])
    return out, dur


# ---------------- музыка и звук ----------------
def t_(s):
    return np.arange(int(s * SR)) / SR


def lp(x, f, o=2):
    return sosfilt(butter(o, f, 'low', fs=SR, output='sos'), x)


def bp(x, a, b, o=2):
    return sosfilt(butter(o, [a, b], 'band', fs=SR, output='sos'), x)


def env(n, a, r):
    e = np.ones(n)
    ai, ri = max(1, int(a * SR)), max(1, int(r * SR))
    e[:ai] = np.linspace(0, 1, ai)
    e[-ri:] *= np.linspace(1, 0, ri)
    return e


def reverb(x, size=2.5, mix=0.35):
    ir = rng.standard_normal(int(size * SR)) * np.exp(-t_(size) * 4 / size)
    y = fftconvolve(x, lp(ir, 6000))[:len(x)]
    y *= np.abs(x).max() / (np.abs(y).max() + 1e-9)
    return x * (1 - mix) + y * mix


def saw(f, t):
    return 2 * ((f * t) % 1) - 1


def braam(d=3.2, root=55.0):
    t = t_(d)
    x = sum(saw(root * m * (1 + rng.uniform(-0.004, 0.004)), t) for m in (1, 1.5, 2, 3)) / 3
    k = np.exp(-t * 2.5)
    y = lp(x, 2400) * k + lp(x, 350) * (1 - k)   # фильтр «закрывается»
    y = np.tanh(y * 2.2) * env(len(t), 0.02, 1.6)
    return reverb(y + np.sin(2 * np.pi * root / 2 * t) * np.exp(-t * 1.2) * 0.8, 3.0, 0.4)


def boom(d=5.0):
    t = t_(d)
    x = np.sin(2 * np.pi * 34 * t * (1 - 0.25 * t / d)) * np.exp(-t * 1.2) + lp(rng.standard_normal(len(t)), 150) * np.exp(-t * 2) * 0.6
    return reverb(x, 3.5, 0.45)


def riser(d):
    t = t_(d)
    n = rng.standard_normal(len(t))
    out = np.zeros(len(t))
    for k in range(0, len(t), 4800):
        f = 300 + 5000 * (k / len(t)) ** 2
        out[k:k + 4800] = bp(n[k:k + 4800], f, f * 1.6)
    tone = np.sin(2 * np.pi * np.cumsum(80 + 600 * (t / d) ** 2) / SR) * 0.3
    return (out * 0.6 + tone) * (t / d) ** 2


def drone(d, root=41.2):
    t = t_(d)
    x = np.sin(2 * np.pi * root * t) * 0.5 + np.sin(2 * np.pi * root * 1.498 * t) * 0.25 + lp(rng.standard_normal(len(t)), 90) * 0.5
    pad = sum(lp(saw(f * (1 + rng.uniform(-0.003, 0.003)), t), 700) for f in (110, 130.8, 164.8)) / 3
    return (x + pad * 0.35 * (0.6 + 0.4 * np.sin(2 * np.pi * t / 9))) * env(len(t), 2.0, 2.0)


def heart(d, bpm=70):
    x = np.zeros(int(d * SR))
    beat = 60 / bpm
    for k in np.arange(0, d - 0.5, beat):
        for off, f, g in ((0, 55, 1), (0.22, 48, 0.75)):
            t = t_(0.3)
            s = np.sin(2 * np.pi * f * t) * np.exp(-t * 16) * g
            i = int((k + off) * SR)
            x[i:i + len(s)] += s[:len(x) - i]
    return x


def pulses(d, bpm=132):
    x = np.zeros(int(d * SR))
    beat = 60 / bpm
    for i, k in enumerate(np.arange(0, d, beat)):
        t = t_(0.35)
        s = np.sin(2 * np.pi * 50 * t) * np.exp(-t * 12)
        if i % 2:
            s = s + bp(rng.standard_normal(len(t)), 3000, 8000) * np.exp(-t * 60) * 0.3
        j = int(k * SR)
        x[j:j + len(s)] += s[:len(x) - j] * (0.5 + 0.5 * k / d)
    return x


def sfx(name, gain=1.0, rate=1.0):
    p = os.path.join(SND, name.replace('.', '/') + '.ogg')
    raw = subprocess.run(['ffmpeg', '-loglevel', 'error', '-i', p, '-f', 's16le', '-ac', '1', '-ar', str(int(SR / rate)), '-'], capture_output=True).stdout
    return np.frombuffer(raw, np.int16).astype(np.float64) / 32768 * gain


def place(buf, s, at, g=1.0):
    i = int(at * SR)
    if i >= len(buf):
        return
    n = min(len(s), len(buf) - i)
    buf[i:i + n] += s[:n] * g


def soundtrack(times, total):
    mus = np.zeros(int(total * SR) + SR)
    fx = np.zeros_like(mus)
    st = {s[1] if s[0] == 'clip' else s[0] + str(i): (a, b) for i, (s, a, b) in enumerate(times)}
    for i, (s, a, b) in enumerate(times):
        if s[0] == 'card' and s[3]:
            place(mus, braam(), a, 0.9)
        if s[0] == 'title':
            place(mus, boom(), a, 1.0)
            place(fx, sfx('title_hit'), a, 0.8)
        if s[0] == 'black':
            pass
    if VERT:
        blk = next(a for s_, a, b_ in times if s_[0] == 'black')
        place(mus, braam(3.0, 55.0), 0.05, 0.8)
        place(fx, sfx('slowmo'), 0.3, 0.7)
        place(mus, drone(blk), 0.0, 0.45)
        k = st['knock']
        for o in (0.6, 2.0, 3.4):
            place(fx, sfx('knock'), k[0] + o, 0.9)
        place(mus, heart(9, 64), k[0], 0.45)
        place(fx, sfx('stinger_soft'), k[0] + 8.3, 0.6)
        v = st['village']
        for o in np.arange(0.8, 6, 0.9):
            place(fx, sfx('lamp_buzz'), v[0] + o, 0.25)
        c = st['corridor']
        place(fx, sfx('listener_scream', rate=0.6), c[0] + 1.0, 0.6)
        place(mus, pulses(blk - c[0]), c[0], 0.6)
        place(mus, riser(blk - c[0]), c[0], 0.5)
        place(fx, sfx('listener_scream'), st['tr_listener'][0] + 0.2, 0.7)
        place(fx, sfx('choir_roar'), st['bunker'][0] + 0.5, 0.9)
        place(fx, sfx('breath'), blk + 0.1, 0.35)
        place(fx, sfx('jumpscare'), st['tr_scare'][0] + 1.55, 1.0)
        return mus, fx
    # 16:9
    blk = next(a for s, a, b in times if s[0] == 'black')
    place(mus, drone(blk - 0.0), 0.0, 0.5)
    place(fx, sfx('amb.forest'), 0.0, 0.5)
    place(fx, sfx('wind_gust'), 1.0, 0.6)
    v = st['village']
    for o in np.arange(1.0, 10, 1.1):
        place(fx, sfx('lamp_buzz'), v[0] + o, 0.22)
    r = st['tr_radio']
    place(fx, sfx('radio_static'), r[0] + 0.2, 0.7)
    place(fx, sfx('radio_static'), r[0] + 2.6, 0.5)
    k = st['knock']
    for o in (0.6, 2.0, 3.4):
        place(fx, sfx('knock'), k[0] + o, 1.0)
    place(mus, heart(10, 64), k[0], 0.45)
    place(fx, sfx('slowmo'), k[0] + 5.5, 0.8)
    place(fx, sfx('stinger_soft'), k[0] + 8.3, 0.7)
    p = st['tr_path']
    place(fx, sfx('branch_snap'), p[0] + 2.2, 0.8)
    place(fx, sfx('whisper'), p[0] + 3.4, 0.6)
    c = st['corridor']
    place(fx, sfx('pole_appear'), c[0] + 0.1, 0.7)
    place(fx, sfx('listener_scream', rate=0.6), c[0] + 1.0, 0.6)
    mont0, mont1 = c[0], blk
    place(mus, pulses(mont1 - mont0), mont0, 0.7)
    place(mus, riser(mont1 - mont0), mont0, 0.6)
    place(fx, sfx('listener_scream'), st['tr_listener'][0] + 0.3, 0.7)
    place(fx, sfx('choir_roar'), st['bunker'][0] + 0.5, 0.9)
    place(fx, sfx('breath'), blk + 0.2, 0.35)                       # тишина… дыхание
    sc = st['tr_scare']
    place(fx, sfx('lamp_buzz'), sc[0] + 0.4, 0.4)
    place(fx, sfx('jumpscare'), sc[0] + 2.35, 1.0)
    return mus, fx


def main():
    os.makedirs(OUT, exist_ok=True)
    with tempfile.TemporaryDirectory(dir='/tmp/claude-0') as tmp:
        files, times, t = [], [], 0.0
        for i, s in enumerate(EDL):
            f, d = segment(i, s, tmp)
            files.append(f)
            times.append((s, t, t + d))
            t += d
        lst = f'{tmp}/list.txt'
        open(lst, 'w').write(''.join(f"file '{f}'\n" for f in files))
        run(['ffmpeg', '-y', '-loglevel', 'error', '-f', 'concat', '-safe', '0', '-i', lst, '-c', 'copy', f'{tmp}/video.mp4'])
        mus, fx = soundtrack(times, t)
        mix = mus * 0.55 + fx * 0.8
        # тишина перед финальным скримером и после титула — по-настоящему тихо
        for s, a, b in times:
            if s[0] == 'black':
                i0, i1 = int(a * SR), int(b * SR)
                mus[i0:i1] = 0
                mix[i0:i1] = fx[i0:i1] * 0.8
        mix = mix[:int(t * SR)]
        mix /= np.abs(mix).max() + 1e-9
        stereo = np.stack([mix, np.roll(mix, int(0.012 * SR)) * 0.96], 1)   # лёгкая ширина
        raw = f'{tmp}/a.raw'
        (np.clip(stereo, -1, 1) * 32767).astype(np.int16).tofile(raw)
        name = {'h': 'trailer_16x9.mp4', 'v': 'shorts_9x16.mp4', 'en': 'trailer_16x9_en.mp4'}[MODE]
        # громкость для YouTube: два прохода loudnorm (замер, потом точная подгонка к −14 LUFS, пик ≤ −1.5 dBTP)
        import json as _j
        meas = subprocess.run(['ffmpeg', '-hide_banner', '-f', 's16le', '-ar', str(SR), '-ac', '2', '-i', raw, '-af',
                               'loudnorm=I=-14:TP=-1.5:LRA=11:print_format=json', '-f', 'null', '-'], capture_output=True, text=True).stderr
        m = _j.loads(meas[meas.rindex('{'):meas.rindex('}') + 1])
        ln = (f"loudnorm=I=-14:TP=-2:LRA=11:measured_I={m['input_i']}:measured_TP={m['input_tp']}:measured_LRA={m['input_lra']}"
              f":measured_thresh={m['input_thresh']}:offset={m['target_offset']}:linear=true")
        run(['ffmpeg', '-y', '-loglevel', 'error', '-i', f'{tmp}/video.mp4', '-f', 's16le', '-ar', str(SR), '-ac', '2', '-i', raw,
             '-af', ln, '-c:v', 'copy', '-c:a', 'aac', '-b:a', '192k', '-ar', '48000',
             '-shortest', '-movflags', '+faststart', f'{OUT}/{name}'])
        print('ok', name, round(t, 1), 's')


if __name__ == '__main__':
    main()
