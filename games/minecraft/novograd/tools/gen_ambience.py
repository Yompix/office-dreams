#!/usr/bin/env python3
"""Фон города: днём — гул машин, гудки, голоса вдалеке, птицы; ночью — тише, сверчки, редкая сирена вдали.
Петли по ~40 с, без шва. Выход: sounds/ambience/city_day.ogg, city_night.ogg, beach.ogg"""
import os, subprocess, wave
import numpy as np

SR = 22050
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'assets', 'novograd', 'sounds', 'ambience')
os.makedirs(OUT, exist_ok=True)
rng = np.random.default_rng(5)
L = 40


def t_(s):
    return np.arange(int(s * SR)) / SR


def lowpass(x, k):
    return np.convolve(x, np.ones(k) / k, mode='same')


def place(buf, snd, at, g=1.0):
    i = int(at * SR) % len(buf)
    n = min(len(snd), len(buf) - i)
    buf[i:i + n] += snd[:n] * g
    if n < len(snd): buf[:len(snd) - n] += snd[n:] * g  # заворачиваем в начало — петля без шва


def hum(level):
    n = rng.standard_normal(L * SR)
    x = lowpass(lowpass(n, 60), 30) * 6
    swell = 0.7 + 0.3 * np.sin(2 * np.pi * t_(L) / L * 3 + 1)
    return x * swell * level


def car_pass(dur=4.0):
    t = t_(dur)
    f = 80 + 40 * np.sin(np.pi * t / dur)
    ph = 2 * np.pi * np.cumsum(f) / SR
    eng = np.sin(ph) * 0.4 + np.sign(np.sin(ph * 2)) * 0.1
    tyre = lowpass(rng.standard_normal(len(t)), 8) * 0.5
    env = np.sin(np.pi * t / dur) ** 2
    return lowpass(eng + tyre, 6) * env


def horn():
    t = t_(0.35 + rng.random() * 0.4)
    f = rng.choice([350, 420, 480])
    x = np.sign(np.sin(2 * np.pi * f * t)) * 0.3 + np.sin(2 * np.pi * f * 1.25 * t) * 0.3
    return lowpass(x, 10) * np.minimum(1, np.minimum(t / 0.02, (t[-1] - t) / 0.05))


def bird():
    t = t_(0.18)
    f = 2800 + 1200 * np.sin(2 * np.pi * 9 * t) + rng.random() * 800
    ph = 2 * np.pi * np.cumsum(f) / SR
    return np.sin(ph) * np.sin(np.pi * t / t[-1]) * 0.3


def cricket():
    t = t_(0.6)
    gate = (np.sin(2 * np.pi * 28 * t) > 0.2).astype(float)
    return np.sin(2 * np.pi * 4300 * t) * gate * np.sin(np.pi * t / t[-1]) * 0.12


def voices():
    # «гул голосов»: полосовой шум с формантной модуляцией
    t = t_(3)
    n = rng.standard_normal(len(t))
    x = lowpass(n, 4) - lowpass(n, 20)
    mod = 0.5 + 0.5 * np.sin(2 * np.pi * (3 + rng.random() * 3) * t)
    return x * mod * np.sin(np.pi * t / t[-1]) * 0.25


def siren_far():
    t = t_(5)
    f = 650 + 300 * (0.5 - 0.5 * np.cos(2 * np.pi * t / 2.5))
    ph = 2 * np.pi * np.cumsum(f) / SR
    return lowpass(np.sin(ph), 12) * np.sin(np.pi * t / t[-1]) * 0.08


def waves():
    n = rng.standard_normal(L * SR)
    x = lowpass(n, 25) * 3
    sw = 0.55 + 0.45 * np.sin(2 * np.pi * t_(L) / 5.0) ** 2
    return x * sw


def write(name, x):
    x = x / (np.max(np.abs(x)) + 1e-9) * 0.6
    tmp = os.path.join(OUT, name + '.wav')
    with wave.open(tmp, 'wb') as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
        w.writeframes((x * 32000).astype(np.int16).tobytes())
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', tmp, '-c:a', 'libvorbis', '-q:a', '1', os.path.join(OUT, name + '.ogg')], check=True)
    os.remove(tmp)


day = hum(0.5)
for i in range(14): place(day, car_pass(3 + rng.random() * 3), rng.random() * L, 0.5 + rng.random() * 0.5)
for i in range(4): place(day, horn(), rng.random() * L, 0.35)
for i in range(10): place(day, voices(), rng.random() * L, 0.6)
for i in range(18): place(day, bird(), rng.random() * L, 0.25)
write('city_day', day)
night = hum(0.3)
for i in range(6): place(night, car_pass(4 + rng.random() * 3), rng.random() * L, 0.4)
for i in range(60): place(night, cricket(), rng.random() * L, 0.5)
place(night, siren_far(), 17, 1)
write('city_night', night)
beach = waves()
for i in range(10): place(beach, bird(), rng.random() * L, 0.15)
write('beach', beach)
print('ok')
