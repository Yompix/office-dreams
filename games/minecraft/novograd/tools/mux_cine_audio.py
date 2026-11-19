#!/usr/bin/env python3
"""Звук к записанной кат-сцене (автотест cine с -Dnovograd.rec): реплики и музыка по журналу .events.tsv.

  python3 tools/mux_cine_audio.py raw.mp4 out.mp4

Журнал пишет client/story/CineRec: «время_видео  VOICE  id» и «время_видео  MUSIC  id  громкость».
Файлы берутся из assets/novograd (sounds.json → путь .ogg). Музыка звучит тихо под репликами и гаснет к концу.
"""
import json, os, subprocess, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'novograd')


def sound_file(sid):
    sid = sid.split(':')[-1]
    js = json.load(open(os.path.join(ASSETS, 'sounds.json'), encoding='utf-8'))
    e = js.get(sid)
    if not e:
        return None
    s = e['sounds'][0]
    name = s['name'] if isinstance(s, dict) else s
    path = os.path.join(ASSETS, 'sounds', name.split(':')[-1] + '.ogg')
    return path if os.path.exists(path) else None


def main():
    raw, out = sys.argv[1], sys.argv[2]
    ev = raw.replace('.mp4', '.events.tsv')
    dur = float(subprocess.run(['ffprobe', '-v', 'error', '-show_entries', 'format=duration', '-of', 'csv=p=0', raw],
                               capture_output=True, text=True).stdout.strip() or 0)
    inputs, filters, labels = [], [], []
    music_on = None
    for line in open(ev, encoding='utf-8') if os.path.exists(ev) else []:
        p = line.rstrip('\n').split('\t')
        if len(p) < 3:
            continue
        t, kind = float(p[0]), p[1]
        if kind == 'VOICE':
            f = sound_file('voice.' + p[2])
            if f:
                inputs.append(f)
                i = len(inputs)
                filters.append(f'[{i}:a]adelay={int(t * 1000)}|{int(t * 1000)},volume=1.0[a{i}]')
                labels.append(f'[a{i}]')
        elif kind == 'MUSIC' and p[2] not in ('stop', ''):
            f = sound_file(p[2])
            if f:
                vol = float(p[3]) if len(p) > 3 else 0.35
                inputs.append(f)
                i = len(inputs)
                fade = max(0.0, dur - t - 1.5)
                filters.append(f'[{i}:a]atrim=0:{max(0.1, dur - t):.2f},afade=t=in:d=1.2,afade=t=out:st={fade:.2f}:d=1.5,'
                               f'adelay={int(t * 1000)}|{int(t * 1000)},volume={vol * 0.9:.2f}[a{i}]')
                labels.append(f'[a{i}]')
    if not labels:
        subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', raw, '-c', 'copy', out], check=True)
        return
    cmd = ['ffmpeg', '-y', '-loglevel', 'error', '-i', raw]
    for f in inputs:
        cmd += ['-i', f]
    fc = ';'.join(filters) + ';' + ''.join(labels) + f'amix=inputs={len(labels)}:normalize=0,apad,atrim=0:{dur:.2f}[aout]'
    cmd += ['-filter_complex', fc, '-map', '0:v', '-map', '[aout]', '-c:v', 'copy', '-c:a', 'aac', '-b:a', '160k', '-t', f'{dur:.2f}', out]
    subprocess.run(cmd, check=True)
    print('ok', out, f'{dur:.1f}s', len(labels), 'audio tracks')


if __name__ == '__main__':
    main()
