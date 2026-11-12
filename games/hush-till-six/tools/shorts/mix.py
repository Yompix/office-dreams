#!/usr/bin/env python3
"""Звук ролика: события из OUT.json (его пишет short.js) → дорожка.
  python3 tools/shorts/mix.py OUT.mp4 ПАПКА_ГОЛОСА            → OUT_final.mp4 (видео + звук, основной язык)
  python3 tools/shorts/mix.py OUT.mp4 ПАПКА_ГОЛОСА/ru ru      → OUT_ru.m4a (только звук: дорожка дубляжа для YouTube)
Звуки — из web/snd (свои, из игры); голос — wav из папки (событие vo:<ключ>). Монтаж сделан под английские реплики:
если реплика другого языка длиннее своего места, она чуть ускоряется (не больше чем в 1,4 раза), чтобы не наезжать на следующую."""
import json, os, subprocess, sys
mp4, vo = sys.argv[1], sys.argv[2]
lang = sys.argv[3] if len(sys.argv) > 3 else ''
SP = json.load(open(mp4[:-4] + '.json'))
SND = os.path.join(os.path.dirname(os.path.abspath(__file__)), '../../web/snd')
D = SP['dur']


def length(f):
    return float(subprocess.run(['ffprobe', '-v', '0', '-show_entries', 'format=duration', '-of', 'csv=p=0', f], capture_output=True, text=True).stdout.strip() or 0)


voices = sorted([e for e in SP['ev'] if e['f'].startswith('vo:')], key=lambda e: e['at'])
slot = {}
for i, e in enumerate(voices):
    end = voices[i + 1]['at'] - .12 if i + 1 < len(voices) else D - .5
    slot[e['f']] = max(.4, end - e['at'])

ins, flt, lab, fast = ([] if lang else ['-i', mp4]), [], [], []
n = 0 if lang else 1   # номер следующего входа ffmpeg
for e in SP['ev']:
    isvo = e['f'].startswith('vo:')
    f = os.path.join(vo, e['f'][3:] + '.wav') if isvo else os.path.join(SND, e['f'] + '.mp3')
    if not os.path.exists(f):
        print('нет звука', f)
        continue
    ins += (['-stream_loop', '-1'] if e.get('loop') else []) + ['-i', f]
    c = ['aformat=sample_rates=48000:channel_layouts=stereo']
    if isvo:
        k = min(1.4, max(1.0, length(f) / slot[e['f']]))
        if k > 1.01:
            c.append('atempo=%.3f' % k)
            fast.append('%s ×%.2f' % (e['f'][3:], k))
        c += ['highpass=f=90', 'acompressor=threshold=-20dB:ratio=3:attack=5:release=120:makeup=4', 'volume=2.1']
    else:
        c.append('volume=%.2f' % e['g'])
    if e.get('d'):
        fo = min(.5, e['d'] / 3)
        c += ['atrim=0:%.3f' % e['d'], 'afade=t=out:st=%.3f:d=%.3f' % (max(0, e['d'] - fo), fo)]
    at = int(max(0, e['at']) * 1000)
    c.append('adelay=%d|%d' % (at, at))
    flt.append('[%d:a]%s[a%d]' % (n, ','.join(c), n))
    lab.append('[a%d]' % n)
    n += 1
flt.append('%samix=inputs=%d:normalize=0:dropout_transition=0,atrim=0:%.3f,afade=t=out:st=%.3f:d=.8,alimiter=limit=0.9[out]' % (''.join(lab), len(lab), D, D - .8))
if lang:
    out = mp4[:-4] + '_' + lang + '.m4a'
    cmd = ['ffmpeg', '-loglevel', 'error', '-y'] + ins + ['-filter_complex', ';'.join(flt), '-map', '[out]', '-c:a', 'aac', '-b:a', '192k', out]
else:
    out = mp4[:-4] + '_final.mp4'
    cmd = ['ffmpeg', '-loglevel', 'error', '-y'] + ins + ['-filter_complex', ';'.join(flt), '-map', '0:v', '-map', '[out]', '-c:v', 'copy', '-c:a', 'aac', '-b:a', '192k', '-movflags', '+faststart', out]
subprocess.run(cmd, check=True)
print('готово', out, '%.1f с' % D, ('· ускорено: ' + ', '.join(fast)) if fast else '')
