"""Звуки WHAT THE LAKE KEEPS, которые сделаны кодом (numpy), а не в ElevenLabs:
  underwater — пролог: глухой подводный гул и пузыри (10 с, петля);
  ui_click   — щелчок выбора и кнопок меню (ElevenLabs дал почти тишину).
  python3 tools/lake/synth.py   → godot/lake/snd/<имя>.ogg"""
import os, subprocess, wave
import numpy as np

SR = 44100
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '../../godot/lake/snd')
rng = np.random.default_rng(3)


def lowpass(x, fc, passes=2):
    a = np.exp(-2 * np.pi * fc / SR)
    for _ in range(passes):
        out = np.empty_like(x)
        acc = 0.0
        for i in range(len(x)):
            acc = a * acc + (1 - a) * x[i]
            out[i] = acc
        x = out
    return x


def save(name, x, peak=0.8):
    x = x / max(1e-9, np.max(np.abs(x))) * peak
    wav = '/tmp/lake_%s.wav' % name
    with wave.open(wav, 'wb') as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
        w.writeframes((x * 32767).astype('<i2').tobytes())
    subprocess.run(['ffmpeg', '-nostdin', '-v', 'error', '-y', '-i', wav, '-c:a', 'libvorbis', '-q:a', '4',
                    os.path.join(OUT, name + '.ogg')], check=True)
    print(name)


def underwater():
    T = 10.0
    n = int(SR * T)
    t = np.arange(n) / SR
    rumble = lowpass(rng.normal(0, 1, n), 90.0) * 6.0 * (0.7 + 0.3 * np.sin(2 * np.pi * 0.1 * t))
    rumble += 0.25 * np.sin(2 * np.pi * 38 * t) * (0.5 + 0.5 * np.sin(2 * np.pi * 0.2 * t))
    bub = np.zeros(n)
    for _ in range(70):                    # пузыри: короткие «блупы» с растущим тоном
        s = rng.uniform(0, T - 0.2); d = rng.uniform(0.03, 0.09); f0 = rng.uniform(300, 900)
        i0 = int(s * SR); m = int(d * SR); tt = np.arange(m) / SR
        bub[i0:i0 + m] += rng.uniform(0.05, 0.18) * np.sin(2 * np.pi * (f0 * tt + 2500 * tt * tt)) * np.exp(-tt / (d * 0.4))
    x = rumble + lowpass(bub, 1400.0) * 2.5
    k = int(0.5 * SR)                      # стык петли: конец плавно переходит в начало
    x[:k] = x[:k] * np.linspace(0, 1, k) + x[-k:] * np.linspace(1, 0, k)
    save('underwater', x[:-k])


def ui_click():
    n = int(0.12 * SR)
    t = np.arange(n) / SR
    x = 0.6 * np.sin(2 * np.pi * 1450 * t) * np.exp(-t / 0.007)
    x += 0.35 * np.sin(2 * np.pi * 420 * t) * np.exp(-t / 0.018)
    x += 0.25 * lowpass(rng.normal(0, 1, n), 3000.0, 1) * np.exp(-t / 0.002)
    x[:20] *= np.linspace(0, 1, 20)
    save('ui_click', x, 0.7)


if __name__ == '__main__':
    underwater()
    ui_click()
