#!/usr/bin/env python3
"""Полицейская рация (gta/Dispatch): треск эфира и «чирп» тангенты.
Пишет sounds/radio_static.ogg и sounds/radio_chirp.ogg и добавляет их в sounds.json (не трогая остальное)."""
import json, os, subprocess, wave
import numpy as np

SR = 44100
ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'assets', 'novograd')
OUT = os.path.join(ROOT, 'sounds')
rng = np.random.default_rng(7)


def bandpass(x, lo, hi):
    f = np.fft.rfft(x)
    fr = np.fft.rfftfreq(len(x), 1 / SR)
    f[(fr < lo) | (fr > hi)] = 0
    return np.fft.irfft(f, len(x))


def write(name, sig):
    sig = sig / max(1e-6, np.max(np.abs(sig))) * 0.6
    tmp = f'/tmp/{name}.wav'
    with wave.open(tmp, 'wb') as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
        w.writeframes((sig * 32767).astype(np.int16).tobytes())
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', tmp, '-c:a', 'libvorbis', '-q:a', '4', os.path.join(OUT, name + '.ogg')], check=True)


# треск: шум в полосе рации (300–3400 Гц), «рваная» огибающая, щелчки
T = 0.9
n = int(SR * T)
t = np.arange(n) / SR
noise = bandpass(rng.standard_normal(n), 300, 3400)
flutter = 0.55 + 0.45 * np.abs(np.sin(2 * np.pi * 7.3 * t + 2 * np.sin(2 * np.pi * 1.7 * t)))
gate = (rng.random(n // 400 + 1) > 0.25).repeat(400)[:n] * 0.6 + 0.4
clicks = np.zeros(n)
for _ in range(14):
    i = rng.integers(0, n - 80)
    clicks[i:i + 40] += rng.standard_normal(40) * 3
env = np.minimum(1, np.minimum(t / 0.02, (T - t) / 0.25))
static = (noise * flutter * gate + bandpass(clicks, 500, 5000)) * env
write('radio_static', static)

# чирп: двойной короткий тон (как у тангенты), с крошкой шума
T2 = 0.22
n2 = int(SR * T2)
t2 = np.arange(n2) / SR
f = np.where(t2 < 0.09, 1850, 2350)
tone = np.sin(2 * np.pi * np.cumsum(f) / SR) * (np.abs(t2 - 0.1) > 0.012)
tone = np.sign(tone) * np.abs(tone) ** 0.6 * 0.7 + bandpass(rng.standard_normal(n2), 400, 3000) * 0.25
env2 = np.minimum(1, np.minimum(t2 / 0.005, (T2 - t2) / 0.03))
write('radio_chirp', bandpass(tone * env2, 300, 3800))

sj = os.path.join(ROOT, 'sounds.json')
with open(sj, encoding='utf-8') as fh:
    data = json.load(fh)
data['radio_static'] = {'sounds': [{'name': 'novograd:radio_static', 'volume': 0.6}], 'subtitle': 'Треск рации'}
data['radio_chirp'] = {'sounds': [{'name': 'novograd:radio_chirp', 'volume': 0.7}]}
with open(sj, 'w', encoding='utf-8') as fh:
    json.dump(data, fh, ensure_ascii=False, indent=2)
    fh.write('\n')
print('ok')
