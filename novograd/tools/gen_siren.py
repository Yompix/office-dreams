#!/usr/bin/env python3
"""Мягкая сирена полиции: плавный «вой» синусом без квадратной волны, с фильтром и лёгким эхом улицы.
Пишет sounds/siren_soft.ogg (gen_resources направляет звук siren сюда)."""
import os, subprocess, wave
import numpy as np

SR = 44100
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'assets', 'novograd', 'sounds')
T = 2.4
t = np.arange(int(SR * T)) / SR
# медленный подъём и спад тона (как у настоящей «вой»-сирены), 560–980 Гц
f = 560 + 420 * (0.5 - 0.5 * np.cos(2 * np.pi * t / T))
f *= 1 + 0.004 * np.sin(2 * np.pi * 5 * t)              # чуть живое вибрато
ph = 2 * np.pi * np.cumsum(f) / SR
sig = np.sin(ph) * 0.8 + np.sin(2 * ph) * 0.12 + np.sin(3 * ph) * 0.04
# мягкий фильтр верхов (скользящее среднее) и эхо от домов
k = 6
sig = np.convolve(sig, np.ones(k) / k, mode='same')
echo = np.zeros_like(sig)
d = int(0.11 * SR)
echo[d:] = sig[:-d] * 0.25
sig = sig + echo
fade = np.minimum(1, np.minimum(t / 0.15, (T - t) / 0.15))
sig = sig / np.max(np.abs(sig)) * 0.55 * fade
tmp = '/tmp/siren_soft.wav'
with wave.open(tmp, 'wb') as w:
    w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
    w.writeframes((sig * 32767).astype(np.int16).tobytes())
subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', tmp, '-c:a', 'libvorbis', '-q:a', '5', os.path.join(OUT, 'siren_soft.ogg')], check=True)
print('ok')
