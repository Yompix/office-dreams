#!/usr/bin/env python3
"""Сборка роликов этапов 3–8 из записей автотеста (без музыки — только звуки мода и записи BigSoundBank, как в трейлере):

  npc_proto.mp4, cutscene_proto.mp4  — из clips/proto_*.mp4 + журнала *.events.tsv (реплики → субтитры, звуки → дорожка)
  yompix_intro.mp4, ui_tour.mp4, settings.mp4, subtitles_test.mp4 — из raw_*.mp4 + raw_*.sounds.csv (щелчки реле и т. п.)

  /tmp/claude-0/ttsenv/bin/python tools/make_proto.py [proto|ui|all]
"""
import os, subprocess, sys, tempfile, textwrap
import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from make_trailer3 import S, place, loop, hp, SR, FONT, FONTB, esc, run  # noqa: E402

OUT = '/tmp/claude-0/glukhoman-trailer3'
CLIPS = f'{OUT}/clips'
COL = {'zakhar': '0xD7B072', 'vika': '0x9CC8EC', 'me': '0xA8DCC4'}


def dur(path):
    return float(subprocess.run(['ffprobe', '-v', 'error', '-show_entries', 'format=duration', '-of', 'csv=p=0', path], capture_output=True, text=True).stdout)


def subs(lines, W):
    """Субтитры как в игре: имя цветом и жирным, текст светлым, до двух строк, плашка; одна реплика за раз."""
    f = []
    for t0, d, key, name, text in lines:
        t1 = t0 + d
        rows = textwrap.wrap(text, 62)[:2]
        y0 = 1080 - 150 - 46 * (len(rows) - 1)
        en = f"between(t\\,{t0:.2f}\\,{t1:.2f})"
        f.append(f"drawtext=fontfile={FONTB}:text='{esc(name)}':fontsize=30:fontcolor={COL.get(key, '0xE6E6E6')}:x=(w-text_w)/2:y={y0 - 42}:"
                 f"borderw=2:bordercolor=black:enable='{en}'")
        for i, r in enumerate(rows):
            f.append(f"drawtext=fontfile={FONT}:text='{esc(r)}':fontsize=38:fontcolor=0xEDE6CF:x=(w-text_w)/2:y={y0 + 46 * i}:borderw=3:"
                     f"bordercolor=black:box=1:boxcolor=black@0.45:boxborderw=10:enable='{en}'")
    return f


def events(path):
    lines, snds = [], []
    for row in open(path, encoding='utf-8'):
        p = row.rstrip('\n').split('\t')
        t = float(p[0])
        if p[1] == 'LINE':
            lines.append((t, float(p[2]), p[3], p[4], p[5]))
        elif p[1] == 'SND':
            snds.append((t, p[2], float(p[3]), float(p[4])))
    return lines, snds


def master(a, path_raw):
    a = a / (np.abs(a).max() + 1e-9) * 0.7
    st = np.stack([a, np.roll(a, int(0.011 * SR)) * 0.95], 1)
    (np.clip(st, -1, 1) * 32767).astype(np.int16).tofile(path_raw)


AF = 'loudnorm=I=-16:TP=-2:LRA=11,aresample=192000,alimiter=limit=0.7:level=0,aresample=48000'


def proto(name, out, grade, night):
    clip = f'{CLIPS}/{name}.mp4'
    D = dur(clip)
    lines, snds = events(f'{CLIPS}/{name}.events.tsv')
    n = int(D * SR)
    a = np.zeros(n)
    wind = S('wind')
    a += loop(hp(wind, 120), D) * (0.16 if night else 0.08)
    if night:
        a += loop(S('room'), D) * 0.05
    for t, s_, vol, dist in snds:
        try:
            x = S(s_)
        except FileNotFoundError:
            continue
        g = vol / (1 + dist / 7)
        place(a, x, t, g * (1.4 if s_ == 'knock' else 1.0))
    # шаги актёров — тихо, под ходьбу (по журналу ходьбы нет — только под реплики не кладём)
    with tempfile.TemporaryDirectory(dir='/tmp/claude-0') as tdir:
        master(a, f'{tdir}/a.raw')
        vf = ','.join([grade] + subs(lines, 1920) + [
            f"drawtext=fontfile={FONT}:text='Прототип · новая система анимации и кат-сцен':fontsize=22:fontcolor=white@0.45:x=40:y=36",
            f"drawtext=fontfile={FONT}:text='Не связано с Mojang':fontsize=20:fontcolor=white@0.35:x=w-text_w-40:y=h-50"])
        run(['ffmpeg', '-y', '-loglevel', 'error', '-i', clip, '-f', 's16le', '-ar', str(SR), '-ac', '2', '-i', f'{tdir}/a.raw',
             '-vf', vf, '-af', AF, '-c:v', 'libx264', '-crf', '24', '-preset', 'slow', '-pix_fmt', 'yuv420p', '-c:a', 'aac', '-b:a', '160k',
             '-shortest', '-movflags', '+faststart', f'{OUT}/{out}'])
    print(out, round(D, 1), 's')


def ui(parts, out, bed=0.04, extra=None):
    """parts — raw_*.mp4 по порядку (склейка), звук — по журналам щелчков."""
    ins, durs = [], []
    for p in parts:
        if os.path.exists(f'{OUT}/raw_{p}.mp4'):
            ins.append(p)
            durs.append(dur(f'{OUT}/raw_{p}.mp4'))
    if not ins:
        print('нет записей для', out)
        return
    D = sum(durs)
    a = np.zeros(int(D * SR) + SR)
    a += loop(hp(S('wind'), 150), len(a) / SR) * bed
    off = 0
    for p, d in zip(ins, durs):
        log = f'{OUT}/raw_{p}.sounds.csv'
        if os.path.exists(log):
            for row in open(log):
                t, sid, vol, pitch = row.strip().split(',')
                try:
                    x = S(sid)
                except FileNotFoundError:
                    continue
                if float(pitch) != 1.0:
                    idx = np.arange(0, len(x), float(pitch))
                    x = np.interp(idx, np.arange(len(x)), x)
                place(a, x, off + float(t), float(vol))
        off += d
    with tempfile.TemporaryDirectory(dir='/tmp/claude-0') as tdir:
        master(a, f'{tdir}/a.raw')
        cmd = ['ffmpeg', '-y', '-loglevel', 'error']
        for p in ins:
            cmd += ['-i', f'{OUT}/raw_{p}.mp4']
        cmd += ['-f', 's16le', '-ar', str(SR), '-ac', '2', '-i', f'{tdir}/a.raw']
        fc = ''.join(f'[{i}:v]fps=30,setsar=1,format=yuv420p[v{i}];' for i in range(len(ins)))
        fc += ''.join(f'[v{i}]' for i in range(len(ins))) + f'concat=n={len(ins)}:v=1:a=0[v];[{len(ins)}:a]{AF}[a]'
        cmd += ['-filter_complex', fc, '-map', '[v]', '-map', '[a]', '-c:v', 'libx264', '-crf', '23', '-preset', 'slow', '-pix_fmt', 'yuv420p',
                '-c:a', 'aac', '-b:a', '160k', '-shortest', '-movflags', '+faststart', f'{OUT}/{out}']
        run(cmd)
    print(out, round(D, 1), 's')


if __name__ == '__main__':
    what = sys.argv[1] if len(sys.argv) > 1 else 'all'
    if what in ('proto', 'all'):
        proto('proto_npc', 'npc_proto.mp4', 'eq=gamma=1.18:saturation=0.9:contrast=1.03', False)
        proto('proto_cut', 'cutscene_proto.mp4', 'eq=gamma=1.45:saturation=0.8:contrast=1.04,vignette=angle=PI/5', True)
    if what in ('ui', 'all'):
        ui(['yompix_intro'], 'yompix_intro.mp4', bed=0.0)
        ui(['ui_tour', 'ui_loading', 'ui_tour2'], 'ui_tour.mp4')
        ui(['settings'], 'settings.mp4')
        ui(['subtitles_test'], 'subtitles_test.mp4', bed=0.03)
