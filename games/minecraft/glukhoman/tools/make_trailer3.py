#!/usr/bin/env python3
"""Трейлер 0.4 «Порожняк»: 70 с VHS found footage (16:9) + три вертикальных ролика 9:16 для TikTok/Shorts (разные «крючки»),
обложка, превью, тексты. Музыки нет — только наши звуки: записи BigSoundBank (как CC0, см. docs/SOUND_CREDITS.md) и звуки мода.

  /tmp/claude-0/ttsenv/bin/python tools/make_trailer3.py [main|shorts|all]

Клипы: /tmp/claude-0/glukhoman-trailer3/clips/t3_*.mp4 (Autotest trailer). Выход: /tmp/claude-0/glukhoman-trailer3/.
Голосов нет (реплики — субтитрами на экране): нейросетевая озвучка XTTS не используется в роликах для монетизации.
"""
import os, subprocess, sys, tempfile
import numpy as np
from scipy.signal import butter, sosfilt

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = '/tmp/claude-0/glukhoman-trailer3'
CLIPS = f'{OUT}/clips'
REAL = '/tmp/claude-0/glukhoman-trailer2/sfx_real'
MOD = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'glukhoman', 'sounds')
SR = 48000
rng = np.random.default_rng(1996)
FONT = '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'
FONTB = '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'
MONO = '/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf'
GRADE = ("eq=gamma=1.6:saturation=0.62:contrast=1.04,curves=all='0/0 0.04/0.03 0.5/0.53 1/0.95',"
         "colorbalance=rs=-0.03:bs=0.04:rm=-0.02:bm=0.03,"
         "eq=brightness='-0.01*abs(sin(t*23))-0.015*lt(mod(t*7\\,3)\\,0.2)':eval=frame,"
         "gblur=sigma=0.45,noise=alls=4:allf=t,vignette=angle=PI/5")


def run(c):
    subprocess.run(c, check=True)


def esc(s):
    return s.replace('\\', '\\\\').replace(':', '\\:').replace("'", "’").replace(',', '\\,').replace('%', '\\%')


def snd(path):
    raw = subprocess.run(['ffmpeg', '-loglevel', 'error', '-i', path, '-f', 's16le', '-ac', '1', '-ar', str(SR), '-'], capture_output=True).stdout
    return np.frombuffer(raw, np.int16).astype(np.float64) / 32768


def S(name):
    for p in (f'{REAL}/{name}.wav', f'{MOD}/{name}.ogg', f'{MOD}/{name.replace(".", "/")}.ogg'):
        if os.path.exists(p):
            return snd(p)
    raise FileNotFoundError(name)


def hp(x, f):
    return sosfilt(butter(2, f, 'high', fs=SR, output='sos'), x)


def place(buf, s, at, g=1.0):
    i = int(at * SR)
    if i >= len(buf) or i < 0:
        return
    n = min(len(s), len(buf) - i)
    buf[i:i + n] += s[:n] * g


def loop(s, d):
    n = int(d * SR)
    reps = int(np.ceil(n / len(s))) + 1
    return np.tile(s, reps)[:n]


def tapefx(a, t):
    place(a, sosfilt(butter(2, [1500, 7000], 'band', fs=SR, output='sos'), rng.standard_normal(int(0.12 * SR))) * 0.25, t)
    place(a, S('tape_click'), t, 0.6)


def text(t0, t1, s, size, y, color='white', font=FONTB, box=False, x='(w-text_w)/2', fade=0.25):
    if t0 <= 0:   # с первого кадра — сразу полностью видно (крючок, обложка)
        alpha = f"if(gt(t\\,{t1 - fade})\\,({t1}-t)/{fade}\\,1)"
    else:
        alpha = f"if(lt(t\\,{t0 + fade})\\,(t-{t0})/{fade}\\,if(gt(t\\,{t1 - fade})\\,({t1}-t)/{fade}\\,1))"
    b = ':box=1:boxcolor=black@0.45:boxborderw=14' if box else ''
    return (f"drawtext=fontfile={font}:text='{esc(s)}':fontsize={size}:fontcolor={color}:x={x}:y={y}:borderw={max(2, size // 14)}:bordercolor=black"
            f"{b}:alpha='{alpha}':enable='between(t\\,{t0}\\,{t1})'")


def overlay(rec_spans, play_spans, W, H, k=1.0):
    """REC ● (мигает), батарея, дата и бегущее время; PLAY ▶ на пересмотрах; уголки кадра."""
    f = []
    m = int(40 * k)
    L = int(48 * k)
    en_rec = '+'.join(f'between(t\\,{a}\\,{b})' for a, b in rec_spans) or '0'
    en_play = '+'.join(f'between(t\\,{a}\\,{b})' for a, b in play_spans) or '0'
    en_any = f'({en_rec}+{en_play})'
    for (x, y, w, h) in ((m, m, L, 3), (m, m, 3, L), (W - m - L, m, L, 3), (W - m - 3, m, 3, L), (m, H - m - 3, L, 3), (m, H - m - L, 3, L),
                         (W - m - L, H - m - 3, L, 3), (W - m - 3, H - m - L, 3, L)):
        f.append(f"drawbox=x={x}:y={y}:w={w}:h={h}:color=white@0.75:t=fill:enable='{en_any}'")
    fs = int(40 * k)
    f.append(f"drawtext=fontfile={MONO}:text='REC':fontsize={fs}:fontcolor=white@0.9:x=w-{int(170 * k)}:y={m + int(18 * k)}:enable='{en_rec}'")
    f.append(f"drawtext=fontfile={FONT}:text='●':fontsize={fs}:fontcolor=0xE02424:x=w-{int(215 * k)}:y={m + int(16 * k)}:enable='({en_rec})*lt(mod(t\\,1)\\,0.5)'")
    bx, by, bw, bh = m + int(26 * k), m + int(22 * k), int(64 * k), int(28 * k)
    for (x, y, w, h) in ((bx, by, bw, 3), (bx, by + bh - 3, bw, 3), (bx, by, 3, bh), (bx + bw - 3, by, 3, bh), (bx + bw, by + bh // 3, int(6 * k), bh // 3)):
        f.append(f"drawbox=x={x}:y={y}:w={w}:h={h}:color=white@0.85:t=fill:enable='{en_rec}'")
    for i in range(2):
        f.append(f"drawbox=x={bx + int(7 * k) + i * int(14 * k)}:y={by + int(7 * k)}:w={int(10 * k)}:h={bh - int(14 * k)}:color=white@0.85:t=fill:enable='{en_rec}'")
    f.append(f"drawtext=fontfile={MONO}:text='▶ PLAY':fontsize={fs}:fontcolor=white:x={m + int(26 * k)}:y={m + int(18 * k)}:enable='{en_play}'")
    f.append(f"drawtext=fontfile={MONO}:text='14.10.1996  03\\:1%{{eif\\:mod(t/60+4\\,10)\\:d}}\\:%{{eif\\:mod(t+7\\,60)\\:d\\:2}}':fontsize={int(36 * k)}:"
             f"fontcolor=white@0.85:x=w-{int(520 * k)}:y=h-{m + int(64 * k)}:enable='{en_any}'")
    f.append(f"drawtext=fontfile={MONO}:text='SP':fontsize={int(36 * k)}:fontcolor=white@0.7:x={m + int(26 * k)}:y=h-{m + int(64 * k)}:enable='{en_any}'")
    return ','.join(f)


def build(edl, texts, sound_fn, out, W=1920, H=1080, vertical=False, crf=26, play_ids=()):
    """edl: [(id, начало, длина)] — id клипа или 'play'/'black'/'tape'. Один проход ffmpeg, без промежуточных файлов."""
    with tempfile.TemporaryDirectory(dir='/tmp/claude-0') as t:
        ins, parts, times, tt = [], [], [], 0.0
        for i, (s_, a_, d) in enumerate(edl):
            if s_ == 'black':
                ins += ['-f', 'lavfi', '-t', str(d), '-i', f'color=c=black:s=1920x1080:r=30']
            elif s_ == 'play':
                ins += ['-f', 'lavfi', '-t', str(d), '-i', f'color=c=0x1a2690:s=1920x1080:r=30']
            elif s_ == 'tape':
                ins += ['-f', 'lavfi', '-t', str(d), '-i', 'nullsrc=s=1920x1080:r=30,geq=lum=random(1)*200:cb=128:cr=128']
            else:
                ins += ['-ss', str(a_), '-t', str(d), '-i', f'{CLIPS}/{s_}.mp4']
            crop = ',crop=608:1080:(iw-608)/2:0,scale=1080:1920:flags=bicubic' if vertical else ''
            parts.append(f'[{i}:v]fps=30,setsar=1,format=yuv420p,trim=duration={d}{crop}[v{i}]')
            times.append((s_, tt, tt + d))
            tt += d
        n = len(edl)
        a = sound_fn(times, tt)
        a /= np.abs(a).max() + 1e-9
        st = np.stack([a, np.roll(a, int(0.012 * SR)) * 0.94], 1)
        (np.clip(st, -1, 1) * 32767).astype(np.int16).tofile(f'{t}/a.raw')
        rec = [(x, y) for s_, x, y in times if s_ not in ('play', 'black', 'tape') and s_ not in play_ids]
        play = [(x, y) for s_, x, y in times if s_ in play_ids]
        k = 1.5 if vertical else 1.0
        ov = overlay(rec, play, W, H, k)
        txt = ','.join(texts(times)) if texts else ''
        fc = ';'.join(parts) + ';' + ''.join(f'[v{i}]' for i in range(n)) + f'concat=n={n}:v=1:a=0[c];[c]{GRADE},{ov}' + (',' + txt if txt else '') + '[v]'
        # громкость: два прохода loudnorm (-14 LUFS, пик -1 dBTP)
        meas = subprocess.run(['ffmpeg', '-hide_banner', '-f', 's16le', '-ar', str(SR), '-ac', '2', '-i', f'{t}/a.raw', '-af',
                               'loudnorm=I=-14:TP=-2:LRA=11:print_format=json', '-f', 'null', '-'], capture_output=True, text=True).stderr
        import json
        j = json.loads(meas[meas.rindex('{'):meas.rindex('}') + 1])
        ln = (f"loudnorm=I=-14:TP=-2:LRA=11:measured_I={j['input_i']}:measured_TP={j['input_tp']}:measured_LRA={j['input_lra']}:"
              f"measured_thresh={j['input_thresh']}:offset={j['target_offset']}:linear=true,aresample=192000,alimiter=limit=0.8:attack=0.3:release=40:level=0,aresample=48000")
        fc += f';[{n}:a]{ln}[a]'
        run(['ffmpeg', '-y', '-loglevel', 'error'] + ins + ['-f', 's16le', '-ar', str(SR), '-ac', '2', '-i', f'{t}/a.raw', '-filter_complex', fc,
             '-map', '[v]', '-map', '[a]', '-c:v', 'libx264', '-crf', str(crf), '-preset', 'slow', '-tune', 'grain', '-maxrate', '6M', '-bufsize', '12M',
             '-pix_fmt', 'yuv420p', '-r', '30', '-c:a', 'aac', '-b:a', '192k', '-ar', '48000', '-shortest', '-movflags', '+faststart', out])
        return times, tt


def lufs(path):
    r = subprocess.run(['ffmpeg', '-nostats', '-i', path, '-af', 'ebur128=peak=true', '-f', 'null', '-'], capture_output=True, text=True).stderr
    s = r[r.rindex('Summary'):]
    i = float(s.split('I:')[1].split('LUFS')[0])
    p = float(s.split('Peak:')[1].split('dBFS')[0])
    return i, p


# ======================= основной трейлер 70 с =======================
MAIN = [('play', 0, 4.0), ('t3_rails', 0.3, 6.0), ('tape', 0, 0.12), ('t3_cut', 0.2, 4.6), ('tape', 0, 0.1), ('t3_bell', 0.3, 5.5),
        ('t3_train', 0.0, 7.6), ('tape', 0, 0.12), ('t3_updown', 0.0, 5.5), ('tape', 0, 0.14), ('t3_house', 0.0, 4.8), ('tape', 0, 0.1),
        ('t3_board', 0.2, 4.6), ('tape', 0, 0.1), ('t3_rewind', 0.0, 6.0), ('tape', 0, 0.12), ('t3_rollcall', 0.2, 4.6), ('tape', 0, 0.1),
        ('t3_draisine', 0.2, 4.2), ('tape', 0, 0.1), ('t3_look', 0.0, 4.4), ('black', 0, 3.2), ('t3_stop', 0.0, 3.0)]


def main_texts(times):
    st = {s: (x, y) for s, x, y in times}
    o = []
    p0 = st['play'][0]
    o.append(f"drawtext=fontfile={MONO}:text='▶ PLAY':fontsize=64:fontcolor=white:x=100:y=90:enable='between(t\\,{p0 + 0.6}\\,{p0 + 4})'")
    o.append(f"drawtext=fontfile={MONO}:text='14.10.1996  02\\:51':fontsize=56:fontcolor=white:x=100:y=h-170:enable='between(t\\,{p0 + 0.6}\\,{p0 + 4})*lt(mod(t\\,0.8)\\,0.5)'")
    r0, r1 = st['t3_rails']
    o.append(text(r0 + 0.8, r1 - 0.2, 'ГЕНА (рация): Каждый метр снимай. Каждую ночь.', 40, 'h-210', font=FONT, box=True))
    b0, b1 = st['t3_bell']
    o.append(text(b0 + 1.0, b1, 'ВЕТКУ ЗАКРЫЛИ В 1991-М', 76, '(h-text_h)/2-40'))
    h0, h1 = st['t3_house']
    o.append(text(h0 + 0.3, h0 + 2.6, 'ЗИНА: Какая Поля? Там пять лет никто не живёт.', 40, 'h-210', font=FONT, box=True))
    o.append(text(h0 + 2.6, h1 + 0.1, 'ОН ЗАБИРАЕТ ЛЮДЕЙ. И ПАМЯТЬ О НИХ', 70, '(h-text_h)/2-40'))
    w0, w1 = st['t3_rewind']
    o.append(text(w1 - 2.0, w1, 'ПЕРЕСМОТРИ СВОИ ЗАПИСИ', 76, '(h-text_h)/2-40'))
    c0, c1 = st['t3_rollcall']
    for i, (a, s) in enumerate(((0.3, '— …семь.  — Я.'), (1.6, '— Восемь.  — Я.'), (2.9, '— Девять.  — Я.'))):
        o.append(text(c0 + a, c0 + a + 1.25, s, 44, 'h-210', font=FONT, box=True, fade=0.1))
    o.append(text(c1 - 1.6, c1 + 0.05, 'ИХ ВОСЕМЬ', 70, '(h-text_h)/2-60'))
    k0, k1 = st['black']
    o.append(text(k0 + 0.5, k1, 'ГЛУХОМАНЬ', 150, '(h-text_h)/2-60', color='0xE8E0D0'))
    o.append(text(k0 + 0.9, k1, 'ПОРОЖНЯК  ·  мод для Minecraft  ·  18+  ·  Yompix Games', 40, '(h-text_h)/2+90', color='0xB0A8A0', font=FONT))
    s0, s1 = st['t3_stop']
    o.append(f"drawtext=fontfile={MONO}:text='■ STOP':fontsize=60:fontcolor=white:x=100:y=90:enable='between(t\\,{s1 - 0.45}\\,{s1})'")
    return o


def main_sound(times, total):
    a = np.zeros(int(total * SR))
    st = {s: (x, y) for s, x, y in times}
    p0, p1 = st['play']
    place(a, S('tape_click'), p0 + 0.15, 0.9)
    place(a, hp(rng.standard_normal(int((p1 - p0) * SR)), 900) * 0.05, p0)
    bed0 = p1
    wind = S('wind')
    w0, w1 = bed0, st['t3_cut'][1]
    place(a, loop(wind, w1 - w0) * np.linspace(1, 0.15, int((w1 - w0) * SR)), w0, 0.55)   # ветер стихает к обрыву рельсов
    motor = S('cam_motor')
    for s_, x, y in times:
        if s_ in ('play', 'black', 'tape', 't3_updown'):
            continue
        place(a, loop(motor, y - x), x, 0.07)
    r0, r1 = st['t3_rails']
    rs = S('radio_static')
    place(a, rs[:int(0.6 * SR)], r0 + 0.6, 0.5)
    place(a, rs[:int(0.4 * SR)], r1 - 1.0, 0.4)
    c0, c1 = st['t3_cut']
    for k in np.arange(c0 + 0.3, c1 - 0.3, 0.62):
        place(a, S('step'), k, 0.35)
    bell = S('crossing_bell')
    b0 = st['t3_bell'][0]
    t1 = st['t3_train'][1]
    for k in np.arange(b0 + 0.3, t1, 4.0):
        place(a, bell, k, 0.55)
    u0, u1 = st['t3_updown']
    place(a, loop(bell, 1.8), u0, 0.4)
    place(a, loop(bell, u1 - u0 - 3.5), u0 + 3.5, 0.45)          # опустил — тишина; поднял — снова звонок
    place(a, S('stinger_soft'), u0 + 3.5, 0.35)
    h0, h1 = st['t3_house']
    place(a, loop(S('room'), h1 - h0), h0, 0.25)
    bd0 = st['t3_board'][0]
    for i in range(6):
        place(a, S('relay'), bd0 - 0.2 + 0.5 + i * 0.7, 0.9)
    w0, w1 = st['t3_rewind']
    place(a, S('rewind'), w0, 0.6)
    place(a, S('breath'), w0 + 2.0, 0.25)
    place(a, S('stinger_soft'), w0 + 4.3, 0.5)
    rc0, rc1 = st['t3_rollcall']
    place(a, loop(S('room'), rc1 - rc0), rc0, 0.2)
    d0, d1 = st['t3_draisine']
    place(a, loop(wind, d1 - d0), d0, 0.3)
    place(a, S('joints'), d0 + 0.6, 0.9)
    place(a, S('joints'), d0 + 2.6, 0.8)
    l0, l1 = st['t3_look']
    i0 = int((l1 - 0.6) * SR)
    k0, k1 = st['black']
    place(a, bell[:int(1.0 * SR)], k0 + 0.45, 0.9)                 # один удар звонка на титре
    place(a, S('hit') * 0.6, k0 + 0.45, 0.5)
    s0, s1 = st['t3_stop']
    place(a, S('breath'), s0 + 0.5, 0.25)
    place(a, S('tape_click'), s1 - 0.45, 1.0)
    for s_, x, y in times:
        if s_ == 'tape':
            tapefx(a, x)
    a[i0:int(k0 * SR)] *= 0.05                                      # мотор камеры глохнет
    a[int((s1 - 0.3) * SR):] *= np.linspace(1, 0, len(a) - int((s1 - 0.3) * SR))
    return a


# ======================= вертикальные ролики (TikTok / Shorts) =======================
HOOKS = {'A': 'Эту кассету нашли\nна станции в 1996', 'B': 'Поставь на паузу,\nкогда увидишь его', 'C': 'Не смотри на 0:18'}
SHORT = [('t3_bell', 1.0, 2.2), ('t3_updown', 0.0, 5.5), ('t3_vanish', 0.4, 3.6), ('t3_house', 1.8, 3.0), ('t3_rewind', 0.5, 5.4),
         ('t3_rollcall', 1.4, 2.6), ('black', 0, 0.8), ('t3_bell', 0.0, 1.0)]
# безопасная зона 1080×1920: не ниже 1440 (подпись/музыка), не правее 918 (кнопки), не выше 192
SZ_X = '(918-text_w)/2'


def short_texts(hook):
    def f(times):
        o = []
        b0, b1 = times[0][1], times[0][2]
        for i, line in enumerate(HOOKS[hook].split('\n')):
            o.append(text(0, b1 + 0.6, line, 74, str(330 + i * 92), x=SZ_X, fade=0.01))
        u0, u1 = times[1][1], times[1][2]
        o.append(text(u0 + 0.15, u0 + 1.8, 'в камере — поезд', 62, '1180', x=SZ_X, fade=0.08))
        o.append(text(u0 + 1.85, u0 + 3.5, 'опустил камеру — пусто', 62, '1180', x=SZ_X, fade=0.08))
        o.append(text(u0 + 3.55, u1, 'поднял — он здесь', 62, '1180', x=SZ_X, fade=0.08))
        v0, v1 = times[2][1], times[2][2]
        o.append(text(v0 + 0.2, v1, 'она исчезла между шагами', 58, '1180', x=SZ_X, fade=0.08))
        h0, h1 = times[3][1], times[3][2]
        o.append(text(h0 + 0.1, h1, 'наутро её никто не помнит', 58, '1110', x=SZ_X, fade=0.08))
        o.append(text(h0 + 1.0, h1, 'кроме кассеты', 58, '1190', x=SZ_X, fade=0.08))
        w0, w1 = times[4][1], times[4][2]
        o.append(text(w0 + 0.2, w0 + 3.4, 'пересмотри запись', 62, '1180', x=SZ_X, fade=0.08))
        c0, c1 = times[5][1], times[5][2]
        o.append(text(c0 + 0.1, c1, '— Девять. — Я.', 58, '1110', x=SZ_X, fade=0.05))
        o.append(text(c0 + 0.9, c1, 'вас было восемь', 66, '1200', x=SZ_X, fade=0.05))
        k0, k1 = times[6][1], times[6][2]
        o.append(text(k0, k1, 'ГЛУХОМАНЬ:', 96, '760', color='0xE8E0D0', x=SZ_X, fade=0.01))
        o.append(text(k0, k1, 'ПОРОЖНЯК', 96, '870', color='0xE04040', x=SZ_X, fade=0.01))
        return o
    return f


def short_sound(times, total):
    a = np.zeros(int(total * SR))
    bell, motor = S('crossing_bell'), S('cam_motor')
    # удар в первую секунду: звонок + металлический удар
    place(a, bell, 0.0, 0.7)
    place(a, S('hit') * 0.5, 0.0, 0.45)
    u0, u1 = times[1][1], times[1][2]
    place(a, loop(bell, 1.8), u0, 0.35)
    place(a, loop(bell, u1 - u0 - 3.5 + 0.01), u0 + 3.5, 0.4) if u1 - u0 > 3.5 else None
    place(a, S('stinger_soft'), u0 + 3.5, 0.35)
    v0, v1 = times[2][1], times[2][2]
    for k in np.arange(v0 + 0.1, v0 + 2.7, 0.55):
        place(a, S('step'), k, 0.35)
    h0, h1 = times[3][1], times[3][2]
    place(a, loop(S('room'), h1 - h0), h0, 0.3)
    w0, w1 = times[4][1], times[4][2]
    place(a, S('rewind'), w0, 0.5)
    place(a, S('breath'), w0 + 1.4, 0.3)
    i0 = int((w0 + 3.6) * SR)
    place(a, S('hit'), w0 + 3.8, 1.0)                                # скример ~72% хронометража
    a[i0:int((w0 + 3.8) * SR)] *= 0.1
    c0, c1 = times[5][1], times[5][2]
    place(a, loop(S('room'), c1 - c0), c0, 0.25)
    k0, k1 = times[6][1], times[6][2]
    place(a, S('tape_click'), k0, 0.8)
    # хвост: звонок переезда ведёт обратно в первый кадр (петля)
    e0, e1 = times[7][1], times[7][2]
    place(a, bell, e0, 0.6)
    for s_, x, y in times:
        if s_ not in ('black',):
            place(a, loop(motor, y - x), x, 0.05)
    return a


def stills(path, secs, prefix):
    for name, sec in secs:
        run(['ffmpeg', '-y', '-loglevel', 'error', '-ss', str(sec), '-i', path, '-frames:v', '1', '-q:v', '3', f'{OUT}/stills/{prefix}{name}.jpg'])


def make_main():
    times, tt = build(MAIN, main_texts, main_sound, f'{OUT}/trailer_70s.mp4', play_ids=('t3_rewind', 't3_stop', 't3_look'))
    i, p = lufs(f'{OUT}/trailer_70s.mp4')
    print(f'ok trailer_70s.mp4 {tt:.1f} с, {i} LUFS, пик {p} dBFS')
    st = {s: (x, y) for s, x, y in times}
    stills(f'{OUT}/trailer_70s.mp4', [('1_rails', st['t3_rails'][0] + 4.5), ('2_cut', st['t3_cut'][0] + 3.5), ('3_bell', st['t3_bell'][0] + 2.5),
                                       ('4_train', st['t3_train'][0] + 5.8), ('5_updown_eye', st['t3_updown'][0] + 2.6), ('6_house', st['t3_house'][0] + 3.5),
                                       ('7_board', st['t3_board'][0] + 3.6), ('8_rewind', st['t3_rewind'][0] + 3.5), ('9_rollcall', st['t3_rollcall'][0] + 2.5),
                                       ('10_look', st['t3_look'][0] + 3.6), ('11_title', st['black'][0] + 2.0)], 'main_')
    # превью 1280×720: поезд в видоискателе + название
    run(['ffmpeg', '-y', '-loglevel', 'error', '-ss', str(st['t3_train'][0] + 5.8), '-i', f'{OUT}/trailer_70s.mp4', '-frames:v', '1', '-vf',
         'scale=1280:720,' + text(0, 99, 'ГЛУХОМАНЬ', 120, '60', color='0xE8E0D0') + ',' + text(0, 99, 'ПОРОЖНЯК', 64, '200', color='0xE04040'),
         f'{OUT}/thumbnail.png'])


def make_shorts():
    for h in HOOKS:
        out = f'{OUT}/short_{h}.mp4'
        times, tt = build(SHORT, short_texts(h), short_sound, out, 1080, 1920, vertical=True, crf=25, play_ids=('t3_rewind',))
        i, p = lufs(out)
        print(f'ok short_{h}.mp4 {tt:.1f} с, {i} LUFS, пик {p} dBFS')
    w0 = sum(d for _, _, d in SHORT[:4])
    stills(f'{OUT}/short_A.mp4', [('0_first_frame', 0.0), ('1_spot_it', SHORT[0][2] + 2.6), ('2_scare', w0 + 3.9), ('3_title', w0 + 5.4 + 2.6 + 0.4), ('4_last_frame', sum(d for _, _, d in SHORT) - 0.04)], 'short_')
    # обложка 1080×1920: перемотка (силуэт за спиной) + название в безопасной зоне
    run(['ffmpeg', '-y', '-loglevel', 'error', '-ss', str(w0 + 3.0), '-i', f'{OUT}/short_A.mp4', '-frames:v', '1', '-vf',
         'drawbox=x=0:y=280:w=1080:h=600:color=black@0.35:t=fill,' + text(0, 99, 'ГЛУХОМАНЬ:', 110, '360', color='0xE8E0D0', x=SZ_X) + ','
         + text(0, 99, 'ПОРОЖНЯК', 110, '490', color='0xE04040', x=SZ_X) + ',' + text(0, 99, 'кассета 1996 года', 60, '650', x=SZ_X, font=FONT),
         f'{OUT}/short_cover.png'])


if __name__ == '__main__':
    os.makedirs(f'{OUT}/stills', exist_ok=True)
    what = sys.argv[1] if len(sys.argv) > 1 else 'all'
    if what in ('main', 'all'):
        make_main()
    if what in ('shorts', 'all'):
        make_shorts()
