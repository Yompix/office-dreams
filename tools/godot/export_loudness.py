#!/usr/bin/env python3
"""Громкость каждой реплики по времени — чтобы рот героев в Godot открывался так же, как в старой игре.

В старой игре (engine.js voiceLevel) анализатор на шине голоса: последние 512 отсчётов, RMS × 6, не больше 1;
движок сглаживает её каждый кадр. Здесь то же самое считается заранее для каждого файла реплики (60 значений
в секунду, байт 0…255), детерминированно и одинаково на ПК и на сайте.

Запуск: python3 tools/godot/export_loudness.py  →  godot/nightshift/assets/loud_<голос>.json  (ru, en, es, pt, de)
"""
import base64, json, os, subprocess, sys
import numpy as np

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SRC = os.path.join(ROOT, 'public/games/nightshift')
OUT = os.path.join(ROOT, 'godot/nightshift/assets')
SR, WIN, HZ = 48000, 512, 60
VOICES = ['ru', 'en', 'es', 'pt', 'de']


def level(path):
    pcm = subprocess.run(['ffmpeg', '-v', 'error', '-i', path, '-ac', '1', '-ar', str(SR), '-f', 'f32le', '-'],
                         capture_output=True, check=True).stdout
    x = np.frombuffer(pcm, dtype=np.float32)
    n = int(len(x) / SR * HZ) + 1
    out = np.zeros(n, dtype=np.uint8)
    for i in range(n):
        e = int(i / HZ * SR)
        w = x[max(0, e - WIN):e]
        if len(w):
            out[i] = int(round(min(1.0, float(np.sqrt(np.mean(w * w))) * 6) * 255))
    return out


def main():
    os.makedirs(OUT, exist_ok=True)
    for v in VOICES:
        tim_f = os.path.join(SRC, 'timing.json' if v == 'ru' else f'timing_{v}.json')
        keys = set(json.load(open(tim_f, encoding='utf-8')).keys()) | {'ch_katya', 'ch_whisper', 'ch_monster'}
        d = os.path.join(SRC, 'snd' if v == 'ru' else f'snd/{v}')
        res = {}
        for k in sorted(keys):
            p = os.path.join(d, k + '.mp3')
            if os.path.exists(p):
                res[k] = base64.b64encode(level(p).tobytes()).decode()
        json.dump({'hz': HZ, 'lines': res}, open(os.path.join(OUT, f'loud_{v}.json'), 'w'), separators=(',', ':'))
        print(v, len(res), 'реплик')


if __name__ == '__main__':
    sys.exit(main())
