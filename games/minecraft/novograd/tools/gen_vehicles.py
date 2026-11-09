"""Транспорт: иконки предметов (мотоцикл, катер, вертолёт, самолёт) и звуки моторов (бесшовные петли по 2 с)."""
import os, subprocess, tempfile, wave
import numpy as np
from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'assets', 'novograd')
ITEM = os.path.join(ROOT, 'textures', 'item')
SND = os.path.join(ROOT, 'sounds')
SR = 44100
T = 2.0
N = int(SR * T)
t = np.arange(N) / SR


def rgb(h):
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


def icon(name, draw):
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    draw(ImageDraw.Draw(img))
    img.save(os.path.join(ITEM, name + '.png'))


def bike(d):
    d.ellipse([0, 9, 5, 14], outline=rgb('151515'), width=2)
    d.ellipse([10, 9, 15, 14], outline=rgb('151515'), width=2)
    d.polygon([(4, 8), (9, 7), (12, 9), (8, 11), (5, 11)], fill=rgb('d7262c'))
    d.rectangle([3, 7, 7, 8], fill=rgb('1a1a1c'))
    d.line([(11, 5), (13, 11)], fill=rgb('8a8f96'))
    d.line([(10, 5), (13, 5)], fill=rgb('2a2a2a'))
    d.point((13, 7), fill=rgb('fff6c8'))
    d.line([(1, 11), (5, 11)], fill=rgb('c9cdd3'))


def boat(d):
    d.polygon([(1, 9), (13, 9), (15, 7), (14, 11), (3, 12)], fill=rgb('f2f4f7'))
    d.line([(2, 10), (14, 9)], fill=rgb('1e5aa8'))
    d.polygon([(7, 9), (9, 6), (10, 6), (10, 9)], fill=rgb('8fb6d6'))
    d.rectangle([0, 6, 2, 10], fill=rgb('15171a'))
    d.line([(1, 11), (1, 13)], fill=rgb('2f3237'))
    d.line([(4, 13), (13, 13)], fill=rgb('5aa0e0'))


def heli(d):
    d.line([(0, 2), (15, 2)], fill=rgb('1c1d20'))
    d.line([(7, 2), (7, 4)], fill=rgb('5a5f66'))
    d.ellipse([4, 4, 12, 11], fill=rgb('1f2a44'))
    d.rectangle([9, 5, 12, 8], fill=rgb('8fb6d6'))
    d.line([(0, 7), (5, 7)], fill=rgb('1f2a44'), width=2)
    d.line([(0, 4), (0, 8)], fill=rgb('1f2a44'))
    d.line([(3, 13), (13, 13)], fill=rgb('3a3d42'))
    d.line([(6, 11), (6, 13)], fill=rgb('3a3d42'))
    d.line([(10, 11), (10, 13)], fill=rgb('3a3d42'))


def plane(d):
    d.polygon([(1, 7), (13, 6), (15, 8), (13, 10), (1, 9)], fill=rgb('f2f4f7'))
    d.line([(1, 8), (13, 8)], fill=rgb('d7262c'))
    d.polygon([(6, 2), (9, 2), (9, 14), (6, 14)], fill=rgb('e0e2e6'))
    d.line([(1, 4), (1, 7)], fill=rgb('d7262c'), width=2)
    d.line([(15, 5), (15, 11)], fill=rgb('1c1d20'))
    d.rectangle([10, 6, 12, 7], fill=rgb('8fb6d6'))


def write(name, sig):
    sig = sig / max(1e-6, np.max(np.abs(sig))) * 0.85
    fd, tmp = tempfile.mkstemp(suffix='.wav')
    os.close(fd)
    with wave.open(tmp, 'wb') as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
        w.writeframes((sig * 32767).astype(np.int16).tobytes())
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', tmp, '-c:a', 'libvorbis', '-q:a', '5', os.path.join(SND, name + '.ogg')], check=True)
    os.remove(tmp)


def noise(lo, hi, seed):
    """Периодический (бесшовный) шум в полосе lo..hi Гц."""
    rng = np.random.default_rng(seed)
    f = np.fft.rfftfreq(N, 1 / SR)
    spec = (rng.standard_normal(len(f)) + 1j * rng.standard_normal(len(f))) * ((f >= lo) & (f <= hi))
    x = np.fft.irfft(spec, N)
    return x / np.max(np.abs(x))


def tone(f, harmonics):
    return sum(a * np.sin(2 * np.pi * f * k * t + k) for k, a in harmonics)


def bike_engine():
    # четырёхцилиндровый спортбайк: 55 Гц такт, рваные гармоники, лёгкий рокот
    f = 55
    body = tone(f, [(1, 1), (2, 0.7), (3, 0.5), (4, 0.45), (6, 0.25), (8, 0.15)])
    pulse = (np.sin(2 * np.pi * f / 2 * t) > 0.3) * 0.25
    return np.tanh((body + pulse + noise(200, 3000, 11) * 0.35) * 1.4)


def boat_engine():
    # подвесной мотор: жужжащий 2-тактник + плеск
    f = 45
    buzz = np.sign(np.sin(2 * np.pi * f * t)) * 0.4 + tone(f, [(1, 0.6), (2, 0.5), (3, 0.4), (5, 0.2)])
    water = noise(150, 1800, 12) * (0.6 + 0.4 * np.sin(2 * np.pi * 1.5 * t))
    return np.tanh(buzz * 0.9 + water * 0.5)


def heli_rotor():
    # «вуп-вуп» лопастей 5 раз в секунду + свист турбины
    chops = 5
    ph = (t * chops) % 1.0
    env = np.exp(-ph * 9)
    thump = noise(30, 260, 13) * env * 1.6 + np.sin(2 * np.pi * 60 * t) * env * 0.8
    whine = np.sin(2 * np.pi * 1500 * t) * 0.06 + np.sin(2 * np.pi * 3000 * t) * 0.03
    air = noise(300, 4000, 14) * (0.15 + env * 0.25)
    return np.tanh((thump + whine + air) * 1.2)


def plane_engine():
    # поршневой мотор с винтом: гул 80 Гц и биение лопастей 40 Гц
    f = 80
    drone = tone(f, [(1, 1), (2, 0.6), (3, 0.35), (4, 0.25), (5, 0.15)])
    prop = 1 + 0.35 * np.sin(2 * np.pi * 40 * t)
    return np.tanh((drone * prop + noise(150, 2500, 15) * 0.3) * 1.2)


if __name__ == '__main__':
    os.makedirs(ITEM, exist_ok=True)
    icon('bike', bike)
    icon('speedboat', boat)
    icon('helicopter', heli)
    icon('plane', plane)
    write('bike_engine', bike_engine())
    write('boat_engine', boat_engine())
    write('heli_rotor', heli_rotor())
    write('plane_engine', plane_engine())
    print('ok')
