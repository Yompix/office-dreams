"""GTA-обновление: текстуры оружия и звуки (выстрел, очередь, сирена)."""
import math, os, subprocess, wave
import numpy as np
from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'assets', 'novograd')
ITEM = os.path.join(ROOT, 'textures', 'item')
SND = os.path.join(ROOT, 'sounds')


def rgb(h):
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def item(name, draw):
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    draw(ImageDraw.Draw(img))
    img.save(os.path.join(ITEM, name + '.png'))


def pistol(d):
    d.rectangle([3, 5, 13, 7], fill=rgb('2b2d31'))          # затвор
    d.line([(4, 5), (12, 5)], fill=rgb('55595f'))
    d.rectangle([12, 6, 13, 6], fill=rgb('111111'))          # дуло
    d.polygon([(4, 8), (7, 8), (6, 13), (3, 13)], fill=rgb('3a2a1e'))  # рукоять
    d.rectangle([7, 8, 9, 9], outline=rgb('2b2d31'))          # скоба
    d.point([(5, 10), (4, 12)], fill=rgb('5a4430'))


def smg(d):
    d.rectangle([2, 5, 13, 7], fill=rgb('23252a'))
    d.line([(2, 5), (13, 5)], fill=rgb('4a4f57'))
    d.rectangle([13, 6, 15, 6], fill=rgb('111111'))
    d.rectangle([6, 8, 7, 13], fill=rgb('1a1b1e'))            # магазин
    d.polygon([(2, 8), (4, 8), (3, 11), (1, 11)], fill=rgb('23252a'))
    d.rectangle([9, 8, 10, 10], fill=rgb('23252a'))


def ammo(d):
    for x in (3, 7, 11):
        d.rectangle([x, 6, x + 2, 12], fill=rgb('c9a227'))
        d.rectangle([x, 4, x + 2, 6], fill=rgb('a5682a'))
        d.point([(x, 7), (x, 9)], fill=rgb('f2d36b'))
    d.rectangle([2, 12, 14, 13], fill=rgb('5a5f66'))


def bat(d):
    d.line([(3, 13), (12, 4)], fill=rgb('b8864b'), width=2)
    d.line([(10, 6), (13, 3)], fill=rgb('d4a466'), width=3)
    d.line([(2, 14), (4, 12)], fill=rgb('3a2a1e'), width=2)


def write(name, sig, sr=44100):
    sig = np.clip(sig, -1, 1)
    tmp = f'/tmp/{name}.wav'
    with wave.open(tmp, 'wb') as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(sr)
        w.writeframes((sig * 32767).astype(np.int16).tobytes())
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', tmp, '-c:a', 'libvorbis', '-q:a', '6', os.path.join(SND, name + '.ogg')], check=True)


def shot(length, body, crack, seed):
    sr = 44100
    t = np.arange(int(sr * length)) / sr
    rng = np.random.default_rng(seed)
    n = rng.standard_normal(len(t))
    # щелчок (высокий шум, очень быстро гаснет) + «бум» (низкий, дольше) + хвост эха
    snap = n * np.exp(-t * crack)
    lp = np.convolve(n, np.ones(40) / 40, mode='same')
    boom = lp * np.exp(-t * body) * 4 + np.sin(2 * np.pi * (90 + 200 * np.exp(-t * 40)) * t) * np.exp(-t * body * 1.2) * 0.8
    tail = np.convolve(n, np.ones(200) / 200, mode='same') * np.exp(-t * 6) * 6
    return np.tanh((snap * 0.9 + boom + tail * 0.4) * 1.6) * 0.9


def siren():
    sr = 44100
    T = 2.0
    t = np.arange(int(sr * T)) / sr
    f = 700 + 450 * (0.5 - 0.5 * np.cos(2 * np.pi * t / T))      # плавный вой вверх-вниз
    ph = 2 * np.pi * np.cumsum(f) / sr
    sig = np.sign(np.sin(ph)) * 0.25 + np.sin(ph) * 0.35 + np.sin(2 * ph) * 0.1
    return sig * np.minimum(1, np.minimum(t / 0.05, (T - t) / 0.05)) * 0.8


item('pistol', pistol)
item('smg', smg)
item('ammo', ammo)
item('bat', bat)
write('gunshot', shot(0.9, 14, 90, 1))
write('smg_shot', shot(0.35, 30, 140, 2))
write('siren', siren())
print('ok')
