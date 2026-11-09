"""GTA-обновление: текстуры оружия и звуки (выстрел, очередь, сирена)."""
import math, os, subprocess, wave
import numpy as np
from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'assets', 'novograd')
ITEM = os.path.join(ROOT, 'textures', 'item')
SND = os.path.join(ROOT, 'sounds')


def rgb(h):
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def item(name, draw, mirror=False):
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    draw(ImageDraw.Draw(img))
    if mirror:  # стволом вправо, как пистолет и автомат
        img = img.transpose(Image.FLIP_LEFT_RIGHT)
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
# выстрелы теперь синтезирует gen_gun_sfx.py (щелчок + тело + механика + эхо-хвост)
import sys as _sys
_sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_gun_sfx as _sfx
write('gunshot', _sfx.pistol_shot() * 0.95)
write('smg_shot', _sfx.smg_shot() * 0.95)
write('siren', siren())
print('ok')


# ---------------------------------------------------------------- арсенал
def shotgun(d):
    d.rectangle([1, 6, 15, 6], fill=rgb('2a2c30'))            # ствол
    d.rectangle([1, 7, 14, 7], fill=rgb('3c4046'))            # подствольный магазин
    d.line([(2, 6), (14, 6)], fill=rgb('5a5f66'))
    d.rectangle([6, 8, 9, 8], fill=rgb('8a5a32'))             # цевьё (помпа)
    d.rectangle([9, 5, 12, 8], fill=rgb('222327'))            # ствольная коробка
    d.polygon([(12, 7), (15, 7), (15, 11), (13, 11)], fill=rgb('7a4a28'))  # приклад
    d.point([(14, 9)], fill=rgb('9a6a3e'))
    d.rectangle([10, 9, 11, 10], outline=rgb('222327'))       # скоба


def rifle(d):
    d.line([(0, 6), (5, 6)], fill=rgb('1c1d20'))              # ствол
    d.point([(0, 5)], fill=rgb('1c1d20'))                      # мушка
    d.rectangle([4, 5, 7, 7], fill=rgb('8a4f22'))             # деревянное цевьё
    d.rectangle([7, 5, 11, 7], fill=rgb('2a2b2f'))            # коробка
    d.line([(7, 5), (11, 5)], fill=rgb('4a4d53'))
    d.polygon([(8, 8), (10, 8), (10, 10), (9, 12), (7, 12), (8, 10)], fill=rgb('2e2f33'))  # изогнутый рожок
    d.polygon([(11, 6), (15, 7), (15, 10), (13, 10), (11, 8)], fill=rgb('8a4f22'))  # приклад
    d.point([(14, 8), (12, 7)], fill=rgb('a8693a'))
    d.rectangle([11, 8, 11, 10], fill=rgb('3a2a1e'))          # рукоять


def sniper(d):
    d.line([(0, 8), (8, 8)], fill=rgb('2a2c30'))              # длинный ствол
    d.point([(0, 7)], fill=rgb('2a2c30'))
    d.rectangle([5, 4, 11, 5], fill=rgb('111214'))            # оптика
    d.point([(4, 4), (12, 4)], fill=rgb('3a8fd0'))            # линзы
    d.line([(7, 6), (9, 6)], fill=rgb('111214'))              # крепление
    d.rectangle([7, 7, 11, 9], fill=rgb('3b4a2c'))            # коробка (олива)
    d.polygon([(11, 8), (15, 8), (15, 12), (13, 12), (11, 10)], fill=rgb('4a5a36'))  # приклад
    d.point([(12, 10)], fill=rgb('2a2c30'))
    d.line([(3, 9), (2, 12)], fill=rgb('555a60'))             # сошки
    d.line([(4, 9), (5, 12)], fill=rgb('555a60'))


def grenade(d):
    d.ellipse([4, 5, 12, 14], fill=rgb('4b5b2e'))
    for y in (8, 11):
        d.line([(5, y), (11, y)], fill=rgb('36431f'))
    d.line([(8, 6), (8, 13)], fill=rgb('36431f'))
    d.point([(6, 7)], fill=rgb('6f8546'))
    d.rectangle([7, 3, 9, 5], fill=rgb('8c9096'))             # запал
    d.line([(9, 3), (12, 6)], fill=rgb('b0b4ba'))             # рычаг
    d.ellipse([3, 1, 7, 5], outline=rgb('d4af37'))            # кольцо


def molotov(d):
    d.rectangle([6, 7, 10, 14], fill=rgb('3d7a3a'))           # бутылка
    d.rectangle([7, 4, 9, 6], fill=rgb('3d7a3a'))             # горлышко
    d.rectangle([6, 10, 10, 14], fill=rgb('c8822a'))          # бензин
    d.point([(7, 8), (7, 11)], fill=rgb('9fd39a'))            # блик
    d.rectangle([7, 2, 9, 3], fill=rgb('e8e0c8'))             # тряпка
    d.point([(8, 1), (9, 0), (7, 0)], fill=rgb('ff7a1a'))     # огонь
    d.point([(8, 0), (10, 1)], fill=rgb('ffd23a'))


def knife(d):
    d.polygon([(6, 9), (13, 2), (14, 3), (8, 10)], fill=rgb('c9ced6'))  # лезвие
    d.line([(7, 9), (13, 3)], fill=rgb('eef2f7'))             # кромка
    d.line([(5, 8), (8, 11)], fill=rgb('55595f'))             # гарда
    d.line([(2, 13), (6, 9)], fill=rgb('2a2420'), width=2)    # рукоять
    d.point([(3, 12), (5, 10)], fill=rgb('4a3e36'))


def body_armor(d):
    d.polygon([(3, 3), (6, 3), (8, 5), (10, 3), (13, 3), (13, 14), (3, 14)], fill=rgb('2e3328'))
    d.rectangle([4, 7, 12, 8], fill=rgb('1d201a'))            # ремень
    d.rectangle([4, 11, 12, 12], fill=rgb('1d201a'))
    d.rectangle([5, 9, 7, 10], fill=rgb('4a5240'))            # подсумки
    d.rectangle([9, 9, 11, 10], fill=rgb('4a5240'))
    d.line([(3, 3), (3, 14)], fill=rgb('454c3c'))
    d.point([(7, 13), (8, 13), (9, 13)], fill=rgb('35e0ff'))  # светоотражатель


def medkit(d):
    d.rectangle([2, 4, 13, 13], fill=rgb('f2f2f2'))
    d.rectangle([2, 4, 13, 5], fill=rgb('d8d8d8'))
    d.rectangle([6, 2, 9, 3], outline=rgb('9a9a9a'))          # ручка
    d.rectangle([7, 6, 8, 11], fill=rgb('e0262b'))            # крест
    d.rectangle([5, 8, 10, 9], fill=rgb('e0262b'))
    d.line([(2, 13), (13, 13)], fill=rgb('b0b0b0'))


def kevlar_layer():
    """Надетый бронежилет: только корпус (64x32, развёртка как у брони)."""
    img = Image.new('RGBA', (64, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rectangle([16, 16, 39, 31], fill=rgb('2e3328') + (255,))   # корпус со всех сторон
    d.rectangle([20, 16, 27, 19], fill=rgb('2e3328') + (255,))
    for y in (22, 27):
        d.line([(16, y), (39, y)], fill=rgb('1d201a') + (255,))
    d.rectangle([21, 23, 22, 25], fill=rgb('4a5240') + (255,))   # подсумки спереди
    d.rectangle([25, 23, 26, 25], fill=rgb('4a5240') + (255,))
    d.line([(20, 31), (27, 31)], fill=rgb('35e0ff') + (255,))
    out = os.path.join(ROOT, 'textures', 'models', 'armor')
    os.makedirs(out, exist_ok=True)
    img.save(os.path.join(out, 'kevlar_layer_1.png'))


def pump(t, at, sr=44100):
    """Металлический щелчок в момент at."""
    k = np.clip(t - at, 0, None)
    on = (t >= at).astype(float)
    return on * (np.sin(2 * np.pi * 2300 * k) * 0.5 + np.sin(2 * np.pi * 3700 * k) * 0.3) * np.exp(-k * 90)


def shotgun_sound():
    sr = 44100
    base = shot(1.1, 8, 60, 3)
    t = np.arange(len(base)) / sr
    rng = np.random.default_rng(13)
    n = rng.standard_normal(len(t))
    rack = pump(t, 0.55) + pump(t, 0.68) * 0.8 + n * ((t > 0.55) & (t < 0.6)) * 0.15 + n * ((t > 0.68) & (t < 0.72)) * 0.12
    return np.clip(base * 1.05 + rack * 0.6, -1, 1)


def sniper_sound():
    sr = 44100
    base = shot(1.8, 6, 160, 5)
    t = np.arange(len(base)) / sr
    rng = np.random.default_rng(15)
    n = np.convolve(rng.standard_normal(len(t)), np.ones(120) / 120, mode='same')
    echo = np.zeros_like(base)
    for dly, g in ((0.22, 0.35), (0.47, 0.2), (0.8, 0.1)):
        k = int(dly * sr)
        echo[k:] += base[:-k] * g
    return np.clip(base + echo + n * np.exp(-t * 2.5) * 3 * 0.3, -1, 1) * 0.95


def reload_sound():
    sr = 44100
    t = np.arange(int(sr * 0.75)) / sr
    rng = np.random.default_rng(21)
    n = rng.standard_normal(len(t))
    s = pump(t, 0.02) * 0.7 + pump(t, 0.36) + pump(t, 0.40) * 0.6 + pump(t, 0.6) * 0.9
    s += n * np.exp(-np.abs(t - 0.05) * 60) * 0.12 + n * np.exp(-np.abs(t - 0.38) * 80) * 0.15
    return np.clip(s * 0.8, -1, 1)


def explosion_sound():
    sr = 44100
    T = 2.6
    t = np.arange(int(sr * T)) / sr
    rng = np.random.default_rng(31)
    n = rng.standard_normal(len(t))
    low = np.convolve(n, np.ones(300) / 300, mode='same') * 18
    mid = np.convolve(n, np.ones(25) / 25, mode='same') * 3
    thump = np.sin(2 * np.pi * (40 + 120 * np.exp(-t * 18)) * t) * np.exp(-t * 4)
    crack = n * np.exp(-t * 40)
    debris = n * (rng.random(len(t)) > 0.9985) * np.exp(-t * 1.5) * 2
    sig = thump * 1.2 + low * np.exp(-t * 1.6) + mid * np.exp(-t * 5) + crack * 0.8 + debris * 0.4
    sig *= np.minimum(1, t / 0.004)
    return np.tanh(sig * 1.3) * 0.95


for _name, _fn in (('shotgun', shotgun), ('rifle', rifle), ('sniper', sniper), ('grenade', grenade), ('molotov', molotov),
                   ('knife', knife), ('body_armor', body_armor), ('medkit', medkit)):
    item(_name, _fn, _name in ('shotgun', 'rifle', 'sniper'))
kevlar_layer()
write('shotgun_shot', _sfx.shotgun_shot() / (np.max(np.abs(_sfx.shotgun_shot())) + 1e-9) * 0.95)
write('rifle_shot', _sfx.rifle_shot() * 0.95)
write('sniper_shot', _sfx.sniper_shot() / (np.max(np.abs(_sfx.sniper_shot())) + 1e-9) * 0.95)
write('reload', reload_sound())
write('explosion', explosion_sound())
print('arsenal ok')
