#!/usr/bin/env python3
"""
Губы в такт озвучке (1.0 «Кино»): огибающая громкости каждой реплики → assets/novograd/lipsync.json.

Для каждого voice/<id>.ogg: ffmpeg → моно 8 кГц, громкость (RMS) окнами 50 мс (20 значений в секунду, как тики),
сглаживание, нормировка по самой реплике (тихий и громкий голос открывают рот одинаково), квантование 0…9 → строка
цифр. Клиент (client/story/Lipsync) по времени звучания берёт цифру: 0–2 — рот закрыт, 3–6 — приоткрыт, 7–9 — открыт;
пики (ударные слоги) дают ещё и кивок головой.
  python3 tools/gen_lipsync.py
"""
import json
import os
import subprocess
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
SND = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'novograd', 'sounds', 'voice')
OUT = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'novograd', 'lipsync.json')
RATE, WIN = 8000, 400  # 50 мс


def envelope(path):
    raw = subprocess.run(['ffmpeg', '-v', 'quiet', '-i', path, '-ac', '1', '-ar', str(RATE), '-f', 's16le', '-'],
                         capture_output=True, check=True).stdout
    x = np.frombuffer(raw, dtype=np.int16).astype(np.float32) / 32768.0
    n = len(x) // WIN
    if n == 0:
        return ''
    rms = np.sqrt(np.mean(x[:n * WIN].reshape(n, WIN) ** 2, axis=1))
    rms = np.convolve(rms, [0.25, 0.5, 0.25], mode='same')
    ref = np.percentile(rms, 95) or 1.0
    gate = np.percentile(rms, 20) * 1.5
    v = np.clip((rms - gate) / max(1e-6, ref - gate), 0, 1)
    return ''.join(str(int(round(a * 9))) for a in v)


def main():
    out = {}
    files = sorted(f for f in os.listdir(SND) if f.endswith('.ogg'))
    for f in files:
        try:
            out[f[:-4]] = envelope(os.path.join(SND, f))
        except subprocess.CalledProcessError:
            print('пропуск', f, file=sys.stderr)
    with open(OUT, 'w') as fh:
        json.dump(out, fh, separators=(',', ':'), ensure_ascii=False)
    print('реплик', len(out), '→', os.path.normpath(OUT), os.path.getsize(OUT), 'байт')


if __name__ == '__main__':
    main()
