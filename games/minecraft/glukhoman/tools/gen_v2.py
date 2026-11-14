#!/usr/bin/env python3
"""«Порожняк» (0.4): ресурсы новой истории.
  - Обходчик (watcher): высокий, худой, в путейской тужурке; лицо закрыто мокрыми прядями, челюсть отвисла, глаза — две тусклые точки,
    в руках фонарь и камера без кассеты. Без кубической головы: голова наклонена, силуэт ломаный.
  - Порожняк (train): три теплушки и пассажирский вагон, без локомотива; в окнах тусклый свет и тёмные силуэты лиц (glowmask).
  - Жители: баба Поля, Захар Ильич, Вика, Петрович, Зина (скины 64×64 + моргание/речь).
  - Блоки и предметы: ящик для кассет, громкоговоритель, лампа табло, красный огонь переезда, босые следы, камера, кассета.
  - Звуки: переездный звонок, мотор камеры, реле табло, громкоговоритель, перемотка, мотовоз.
Запуск: /tmp/claude-0/ttsenv/bin/python tools/gen_v2.py   (Pillow, numpy, scipy, ffmpeg)
"""
import math, os, random, subprocess, tempfile, sys
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import gen_creatures as gc  # noqa: E402

A = gc.A
rnd = random.Random(1953)
anim, wob, merge, shade = gc.anim, gc.wob, gc.merge, gc.shade

# ======================= Обходчик =======================
gc.MATS.update({
    'coat': gc.material((30, 31, 36), crack=0.0, dirt=0.5, sheen=0.1, scale=0.4, edge=0.75),
    'coat_d': gc.material((22, 22, 26), dirt=0.5, sheen=0.08, scale=0.4, edge=0.75),
    'wskin': gc.material((66, 63, 62), vein=0.05, dirt=0.0, sheen=0.12, scale=0.45, edge=0.8),
    'hair': gc.material((16, 15, 15), dirt=0.0, sheen=0.14, scale=0.9, edge=1.0),
    'brass': gc.material((84, 70, 40), dirt=0.0, sheen=0.1, scale=0.6),
    'lglass': gc.material((120, 100, 52), dirt=0.0, edge=1.0),
    'cam': gc.material((26, 26, 28), dirt=0.0, sheen=0.05, scale=0.6),
    'wmouth': gc.material((14, 8, 9), dirt=0.0, edge=1.0),
})
gc.MATS['wjaw'] = gc.bloody(gc.MATS['wskin'], low=49.5, amount=0.55)


def front_watcher(px, gx, x0, y0, w_, h_):
    """Лицо почти целиком в тени; глаза — по одной тусклой точке, глубоко и слишком широко посаженные."""
    for y in range(h_):
        for x in range(w_):
            c = px[x0 + x, y0 + y]
            k = 0.55 + 0.25 * (y / max(1, h_ - 1))
            gc.put(px, x0 + x, y0 + y, shade(c[:3], k))
    ey = y0 + int(h_ * 0.34)
    for cx in (x0 + 1, x0 + w_ - 2):
        gc.put(px, cx, ey, (122, 112, 92, 255))
        gc.put(gx, cx, ey, (70, 62, 48, 255))


gc.FRONT['watcher'] = front_watcher


def watcher():
    m = gc.Model('watcher', 256, 256)
    m.bone('root', None, [0, 0, 0])
    m.bone('pelvis', 'root', [0, 27, 0]); m.cube('pelvis', [-2.3, 25.5, -1.3], [4.6, 3.0, 2.6], 'coat_d')
    m.bone('spine', 'pelvis', [0, 28.5, 0.2], [-6, 0, 0]); m.cube('spine', [-2.0, 28.5, -1.1], [4.0, 7, 2.2], 'coat')
    m.bone('chest', 'spine', [0, 35.5, 0.3], [-14, 0, 0])
    m.cube('chest', [-2.6, 35.5, -1.2], [5.2, 8.0, 2.6], 'coat')
    m.cube('chest', [-0.25, 36, -1.45], [0.5, 7.0, 0.3], 'brass')                     # пуговицы
    m.cube('chest', [-3.9, 42.6, -0.9], [7.8, 1.2, 2.0], 'coat_d')                   # плечи
    m.cube('chest', [-1.8, 43.0, -1.5], [3.6, 1.0, 2.8], 'coat_d')                   # воротник
    # полы тужурки — рваные, до колен, отдельные полосы (ломают силуэт)
    for i, (x, ln, rz) in enumerate(((-2.2, 12, 4), (-1.0, 14, 1), (0.2, 11, -2), (1.3, 13.5, -4), (-2.3, 13, 6))):
        z = -1.4 if i < 4 else 1.0
        m.cube('pelvis', [x, 25.8 - ln, z], [1.2, ln, 0.4], 'coat_d', rot=[0, 0, rz], pivot=[x + 0.6, 25.8, z])
    for x in (-2.0, -0.6, 0.8):
        m.cube('pelvis', [x, 13.5, 1.0], [1.3, 12.3, 0.4], 'coat_d')
    m.bone('neck', 'chest', [0, 43.5, 0.2], [-24, 0, 0]); m.cube('neck', [-0.7, 43.5, -0.5], [1.4, 4.2, 1.4], 'wskin')
    m.bone('head', 'neck', [0, 47.5, 0.2], [18, 0, 22])
    m.cube('head', [-1.7, 48, -1.0], [3.4, 4.6, 3.6], 'hair')                         # затылок в волосах
    m.cube('head', [-1.4, 46.6, -1.9], [2.8, 5.0, 1.0], 'wskin', front='watcher')    # узкое лицо
    m.cube('head', [-1.1, 51.8, -1.6], [2.2, 1.2, 3.0], 'hair')
    # мокрые пряди поверх лица и по бокам: тонкие, разной длины
    for (x, z, ln, rz) in ((-1.5, -2.2, 6.5, 3), (-0.7, -2.15, 4.2, -2), (0.15, -2.2, 7.5, 1), (0.9, -2.15, 5.0, -3), (1.35, -2.1, 6.8, -6),
                           (-1.9, -0.8, 7.0, 4), (1.85, -0.6, 7.6, -5), (-1.7, 1.0, 8.0, 2), (1.5, 1.2, 8.4, -2), (0.0, 2.3, 9.0, 0)):
        m.cube('head', [x - 0.22, 52.4 - ln, z - 0.2], [0.44, ln, 0.4], 'hair', rot=[0, 0, rz], pivot=[x, 52.4, z])
    m.bone('jaw', 'head', [0, 47.2, -1.2], [32, 0, 0])                                 # отвисшая челюсть
    m.cube('jaw', [-1.1, 44.4, -1.9], [2.2, 2.8, 0.9], 'wjaw')
    m.cube('jaw', [-0.8, 45.0, -1.2], [1.6, 2.0, 0.3], 'wmouth')
    for s, n in ((1, 'left'), (-1, 'right')):
        sx = 3.4 * s
        m.seg(f'{n}_arm', 'chest', [sx, 42.6, 0.2], [sx + 0.6 * s, 31.0, -0.4], 1.5, 'coat')
        m.seg(f'{n}_forearm', f'{n}_arm', [sx + 0.6 * s, 31.0, 0.2], [sx + 0.5 * s, 19.0, -0.6], 1.3, 'coat')
        m.bone(f'{n}_hand', f'{n}_forearm', [sx + 0.5 * s, 19.0, 0.2])
        m.cube(f'{n}_hand', [sx + 0.5 * s - 0.8, 15.6, -0.6], [1.6, 3.4, 1.0], 'wskin')
        for i, fx in enumerate((-0.5, 0.0, 0.5)):
            m.cube(f'{n}_hand', [sx + 0.5 * s + fx - 0.17, 12.6, -0.4], [0.34, 3.2, 0.34], 'wskin')
        lx = 1.2 * s
        m.seg(f'{n}_leg', 'pelvis', [lx, 25.6, 0.0], [lx + 0.2 * s, 13.0, -0.6], 1.5, 'coat_d')
        m.seg(f'{n}_shin', f'{n}_leg', [lx + 0.2 * s, 13.0, 0.0], [lx + 0.2 * s, 1.4, 0.6], 1.35, 'coat_d')
        m.bone(f'{n}_foot', f'{n}_shin', [lx, 1.4, 0.0])
        m.cube(f'{n}_foot', [lx - 0.8, 0, -2.4], [1.6, 1.4, 3.4], 'coat_d')
    # фонарь в левой руке (тусклый), камера без кассеты в правой
    m.bone('lantern', 'left_hand', [3.9, 15.8, 0.2])
    m.cube('lantern', [3.2, 9.0, -0.5], [1.6, 0.4, 1.6], 'brass')
    m.cube('lantern', [3.35, 9.4, -0.35], [1.3, 2.4, 1.3], 'lglass', glow=(150, 120, 60, 255))
    m.cube('lantern', [3.2, 11.8, -0.5], [1.6, 0.5, 1.6], 'brass')
    m.cube('lantern', [3.9, 12.3, 0.1], [0.2, 3.3, 0.2], 'brass')
    m.bone('camera', 'right_hand', [-3.9, 15.8, 0.2])
    m.cube('camera', [-5.0, 13.2, -2.6], [1.8, 2.2, 4.4], 'cam')
    m.cube('camera', [-4.7, 13.7, -3.4], [1.2, 1.2, 0.8], 'cam')
    A_ = {}

    def legs(t, f, a):
        return {'left_leg': {'rotation': [wob(t, f, a), 0, 0]}, 'right_leg': {'rotation': [-wob(t, f, a), 0, 0]},
                'left_shin': {'rotation': [max(0, -wob(t, f, a * 1.2, 1.3)), 0, 0]}, 'right_shin': {'rotation': [max(0, wob(t, f, a * 1.2, 1.3)), 0, 0]}}
    hair = lambda t, a: {}
    A_['idle'] = anim(lambda t: {'head': {'rotation': [0, wob(t, 0.07, 6), 22 + wob(t, 0.05, 5)]}, 'chest': {'rotation': [wob(t, 0.15, 1.2), 0, wob(t, 0.1, 1.5)]},
                                 'jaw': {'rotation': [32 + wob(t, 0.2, 3), 0, 0]}, 'lantern': {'rotation': [wob(t, 0.3, 4), 0, wob(t, 0.23, 5)]}}, 8)
    A_['still'] = anim(lambda t: {'head': {'rotation': [4, 0, 30]}, 'jaw': {'rotation': [36, 0, 0]}}, 1)
    A_['walk'] = anim(lambda t: merge(legs(t, 0.45, 20), {
        'left_arm': {'rotation': [-wob(t, 0.45, 6), 0, 0]}, 'right_arm': {'rotation': [wob(t, 0.45, 6), 0, 0]},
        'head': {'rotation': [2, wob(t, 0.22, 5), 22 + wob(t, 0.9, 3)]}, 'pelvis': {'position': [0, abs(wob(t, 0.45, 0.5)), 0]},
        'lantern': {'rotation': [wob(t, 0.45, 12, 1), 0, 0]}}), 2.222)
    # слишком длинный шаг с рывками: замирает на полшага и снова идёт
    A_['walk'] = anim(lambda t: merge(legs(t, 0.36, 38), {
        'left_arm': {'rotation': [-wob(t, 0.36, 4), 0, 0]}, 'right_arm': {'rotation': [wob(t, 0.36, 4), 0, 0]},
        'spine': {'rotation': [6, 0, wob(t, 0.36, 4)]}, 'pelvis': {'position': [0, abs(wob(t, 0.36, 1.1)), 0]},
        'lantern': {'rotation': [wob(t, 0.36, 14, 1), 0, 0]}, 'jaw': {'rotation': [34, 0, 0]}}), 2.778, jerk=True, fps=5)
    A_['run'] = A_['walk']
    # согнулся почти пополам и идёт так — когда подходит близко
    A_['stalk'] = anim(lambda t: merge(legs(t, 0.3, 22), {
        'spine': {'rotation': [38, 0, wob(t, 0.3, 3)]}, 'chest': {'rotation': [26, 0, 0]}, 'neck': {'rotation': [-30, 0, 0]},
        'left_arm': {'rotation': [-28 + wob(t, 0.3, 6), 0, 0]}, 'right_arm': {'rotation': [-24 - wob(t, 0.3, 6), 0, 0]},
        'pelvis': {'position': [0, -2, 0]}, 'jaw': {'rotation': [38, 0, 0]}}), 3.333, jerk=True, fps=6)
    # наклон в объектив: медленно, потом рывок головы к камере (для «записанного» кадра)
    A_['lean'] = anim(lambda t: {'spine': {'rotation': [min(30, t * 12), 0, 0]}, 'chest': {'rotation': [min(26, t * 10), 0, 0]},
                                 'neck': {'rotation': [min(20, t * 9), 0, 0]}, 'head': {'rotation': [-10, 0, 22 - min(22, t * 14)]},
                                 'jaw': {'rotation': [32 + min(14, t * 6), 0, 0]}}, 3.0, loop=False)
    A_['spawn'] = A_['still']
    A_['vanish'] = A_['still']
    m.save(4, A_)


# ======================= живые люди: дополнительные анимации жителей =======================
def human_extra():
    import json
    path = f'{A}/animations/human.animation.json'
    data = json.load(open(path))
    an = data['animations']
    # лунатик: медленно, руки висят плетьми, голова вниз, ноги шаркают (мало сгибаются), корпус покачивается
    an['animation.human.sleepwalk'] = anim(lambda t: {
        'right_leg': {'rotation': [-wob(t, 0.42, 13), 0, 0]}, 'left_leg': {'rotation': [wob(t, 0.42, 13), 0, 0]},
        'right_shin': {'rotation': [max(0, wob(t, 0.42, 6, 1.4)), 0, 0]}, 'left_shin': {'rotation': [max(0, -wob(t, 0.42, 6, 1.4)), 0, 0]},
        'right_arm': {'rotation': [wob(t, 0.42, 3, 0.5), 0, 3]}, 'left_arm': {'rotation': [-wob(t, 0.42, 3, 0.5), 0, -3]},
        'right_forearm': {'rotation': [-4, 0, 0]}, 'left_forearm': {'rotation': [-4, 0, 0]},
        'head': {'rotation': [26 + wob(t, 0.21, 3), wob(t, 0.11, 4), wob(t, 0.42, 3)]}, 'neck': {'rotation': [8, 0, 0]},
        'chest': {'rotation': [6, wob(t, 0.42, 3), wob(t, 0.42, 2)]}, 'waist': {'rotation': [0, 0, wob(t, 0.42, 2.5)]},
        'root': {'position': [0, -abs(wob(t, 0.42, 0.35)), 0]}}, 2.381)
    # стоит и осматривается: голова медленно туда-сюда с паузами, дыхание
    def look(t):
        k = [(0, 0), (1.2, -38), (2.4, -38), (3.4, 26), (4.6, 26), (5.6, 0), (6, 0)]
        for (a, va), (b, vb) in zip(k, k[1:]):
            if a <= t <= b:
                u = (t - a) / (b - a)
                y = va + (vb - va) * (u * u * (3 - 2 * u))
                break
        return {'head': {'rotation': [wob(t, 0.3, 2), y, 0]}, 'chest': {'rotation': [wob(t, 0.3, 1.5), y * 0.15, 0]},
                'right_arm': {'rotation': [wob(t, 0.3, 2), 0, 2]}, 'left_arm': {'rotation': [-wob(t, 0.3, 2), 0, -2]}}
    an['animation.human.idle_look'] = anim(look, 6)
    # переминается с ноги на ногу: вес на одну, потом на другую
    an['animation.human.idle_shift'] = anim(lambda t: {
        'waist': {'rotation': [0, wob(t, 0.2, 3), wob(t, 0.2, 4)], 'position': [wob(t, 0.2, 0.4), 0, 0]},
        'right_leg': {'rotation': [0, 0, max(0, wob(t, 0.2, 4))]}, 'left_leg': {'rotation': [0, 0, min(0, wob(t, 0.2, 4))]},
        'chest': {'rotation': [wob(t, 0.3, 1.5), 0, -wob(t, 0.2, 3)]}, 'head': {'rotation': [wob(t, 0.25, 3), wob(t, 0.13, 8), wob(t, 0.2, 2)]},
        'right_arm': {'rotation': [wob(t, 0.2, 3), 0, 3]}, 'left_arm': {'rotation': [-wob(t, 0.2, 3), 0, -3]}}, 5)
    # чешет затылок
    def scratch(t):
        up = min(1, t / 0.6) if t < 2.6 else max(0, 1 - (t - 2.6) / 0.7)
        j = wob(t, 4.5, 6) * (1 if 0.7 < t < 2.5 else 0)
        return {'right_arm': {'rotation': [-150 * up, -20 * up, 10 * up]}, 'right_forearm': {'rotation': [-70 * up + j, 0, 0]},
                'head': {'rotation': [10 * up, -10 * up, 6 * up]}, 'chest': {'rotation': [wob(t, 0.3, 1.5), 0, 0]}}
    an['animation.human.idle_scratch'] = anim(scratch, 4)
    # колеблется на краю: шаг назад весом, голова опускается и поднимается
    an['animation.human.hesitate'] = anim(lambda t: {
        'waist': {'rotation': [-4 * min(1, t / 0.5), 0, 0]}, 'head': {'rotation': [18 * min(1, t / 0.6) - 22 * max(0, min(1, (t - 0.9) / 0.6)), 0, 0]},
        'right_arm': {'rotation': [wob(t, 0.5, 3), 0, 4]}, 'left_arm': {'rotation': [-wob(t, 0.5, 3), 0, -4]},
        'chest': {'rotation': [wob(t, 0.35, 2), 0, 0]}}, 1.6, loop=False)
    zakhar_clips(an)
    json.dump(data, open(path, 'w'), separators=(',', ':'))


def zakhar_clips(an):
    """Захар Ильич — базовые клипы новой системы анимации (жесты — слоями в коде, client/anim/Gestures.java).
    Старик-дежурный: сутулится, правая нога не гнётся (хромает), шаг медленный и неровный, голову держит поднятой."""
    P = 1.36                                   # период шага, с: на «плохую» правую ногу — коротко, на левую — долго
    def ph(t):
        u = (t % P) / P                        # 0…0.42 — опора на правую (больную), 0.42…1 — на левую
        return u
    def walk(t):
        u = ph(t)
        s = math.sin(2 * math.pi * u)
        bad = 1 if u < 0.42 else 0             # стоит на больной ноге — проседает и спешит снять с неё вес
        k = math.sin(math.pi * min(1, u / 0.42)) if bad else 0
        dip = -0.55 * k                        # «провал» корпуса на больной ноге
        return {
            'root': {'position': [0.25 * k, dip + 0.12 * abs(math.sin(2 * math.pi * u * 2)), 0]},
            'waist': {'rotation': [-8, 4 * s, 5 * k - 1.5]},   # waist не в FLIP: минус — наклон вперёд
            'chest': {'rotation': [12 + 2 * k, -5 * s, -3 * k]},
            'neck': {'rotation': [-6, 0, 0]},
            'head': {'rotation': [-8 - 2 * k, 3 * s, 2 * k]},
            # правая нога почти прямая (колено не гнётся), выносится дугой вбок; левая — обычный шаг
            'right_leg': {'rotation': [-22 * math.sin(2 * math.pi * u + 0.3), 0, -5 * (1 - bad) * math.sin(math.pi * max(0, (u - 0.42) / 0.58))]},
            'right_shin': {'rotation': [4 + 4 * max(0, math.sin(2 * math.pi * u)), 0, 0]},
            'left_leg': {'rotation': [24 * math.sin(2 * math.pi * u + 0.3), 0, 0]},
            'left_shin': {'rotation': [max(0, 26 * math.sin(2 * math.pi * u + 2.2)), 0, 0]},
            # левая рука машет, правая почти висит (держит равновесие, чуть отставлена)
            'left_arm': {'rotation': [-16 * math.sin(2 * math.pi * u + 0.3), 0, -4]},
            'left_forearm': {'rotation': [-12 - 6 * max(0, math.sin(2 * math.pi * u)), 0, 0]},
            'right_arm': {'rotation': [5 * math.sin(2 * math.pi * u + 0.3), 0, 9 + 4 * k]},
            'right_forearm': {'rotation': [-10, 0, 0]},
        }
    an['animation.human.z_walk'] = anim(walk, P * 2)
    # стоит: сутулый, вес на левой ноге, правое колено чуть согнуто; медленное тяжёлое дыхание, лёгкое покачивание
    def idle(t):
        b = math.sin(2 * math.pi * t / 4.0)
        sw = math.sin(2 * math.pi * t / 7.0)
        return {
            'root': {'position': [0.35, -0.15 - 0.05 * b, 0]},
            'waist': {'rotation': [-6, 2 * sw, -3]},
            'chest': {'rotation': [11 - 1.5 * b, 1.5 * sw, 1.5]},
            'neck': {'rotation': [-5, 0, 0]},
            'head': {'rotation': [-7 + b, 2 * sw, 1.5]},
            'right_leg': {'rotation': [-4, 0, -2]}, 'right_shin': {'rotation': [8, 0, 0]},
            'left_leg': {'rotation': [2, 0, 3]},
            'right_arm': {'rotation': [2 + b, 0, 6]}, 'right_forearm': {'rotation': [-8, 0, 0]},
            'left_arm': {'rotation': [3 - b, 0, -6]}, 'left_forearm': {'rotation': [-10, 0, 0]},
        }
    an['animation.human.z_idle'] = anim(idle, 28)


def zakhar_sounds():
    """Кашель старика: три толчка воздуха (шум через «горло» — полосовой фильтр с формантами), хрип на вдохе."""
    import numpy as np
    from scipy.signal import butter, sosfilt
    rng = np.random.default_rng(1952)
    n = int(2.2 * SR)
    x = np.zeros(n)
    bp = lambda y, a, b: sosfilt(butter(2, [a, b], 'band', fs=SR, output='sos'), y)
    for at, g in ((0.05, 1.0), (0.48, 0.8), (0.92, 0.65)):
        m = int(0.32 * SR)
        e = np.exp(-np.arange(m) / SR * 11) * np.minimum(1, np.arange(m) / (0.008 * SR))
        burst = bp(rng.standard_normal(m), 300, 2600) * 0.7 + bp(rng.standard_normal(m), 900, 1300) * 0.6
        i = int(at * SR)
        x[i:i + m] += burst * e * g
    m = int(0.6 * SR)                          # хриплый вдох после
    e = np.sin(np.linspace(0, np.pi, m)) * 0.18
    i = int(1.4 * SR)
    x[i:i + m] += bp(rng.standard_normal(m), 1800, 4200) * e
    save_ogg('cough', x)


def yompix_sound():
    """Звуковая подпись Yompix Games для «Глухомани»: щелчок реле, низкий гул плёнки, три «пиксельных» тона вверх
    (пиксели эмблемы встают на место), «тиньк» и шипение ленты на исчезновении. 4.6 с."""
    import numpy as np
    from scipy.signal import butter, sosfilt
    rng = np.random.default_rng(2024)
    n = int(4.6 * SR)
    t = np.arange(n) / SR
    lp = lambda y, f: sosfilt(butter(2, f, 'low', fs=SR, output='sos'), y)
    bp = lambda y, a, b: sosfilt(butter(2, [a, b], 'band', fs=SR, output='sos'), y)
    x = np.zeros(n)
    # гул: 55 Гц + обертоны, нарастает к 1.6 с, держится, уходит
    env = np.clip(t / 1.4, 0, 1) * np.clip((4.4 - t) / 1.0, 0, 1)
    x += (np.sin(2 * np.pi * 55 * t) * 0.5 + np.sin(2 * np.pi * 110 * t + 0.4) * 0.25 + lp(rng.standard_normal(n), 300) * 0.25) * env * 0.6
    # шипение плёнки в начале
    x += bp(rng.standard_normal(n), 3000, 8000) * np.clip(1 - t / 1.6, 0, 1) * 0.12
    # щелчок реле на 0.05 с
    m = int(0.01 * SR); i = int(0.05 * SR)
    x[i:i + m] += bp(rng.standard_normal(m), 1500, 6000) * np.exp(-np.arange(m) / m * 4) * 1.2
    # три тона (квадратные с мягким срезом) — ми, соль, си
    for at, f in ((0.9, 659.3), (1.15, 784.0), (1.4, 987.8)):
        m = int(0.22 * SR); i = int(at * SR)
        tt = np.arange(m) / SR
        tone = np.sign(np.sin(2 * np.pi * f * tt)) * 0.5 + np.sin(2 * np.pi * f * 2 * tt) * 0.2
        x[i:i + m] += lp(tone, 4000) * np.exp(-tt * 9) * 0.5
    # «тиньк» (колокольчик) на 1.9 с — эмблема собрана
    m = int(1.6 * SR); i = int(1.9 * SR)
    tt = np.arange(m) / SR
    x[i:i + m] += sum(a * np.sin(2 * np.pi * f * tt) for f, a in ((1318.5, 0.5), (2637, 0.2), (3951, 0.08))) * np.exp(-tt * 3.2) * 0.6
    # срыв строки на 3.1 с
    m = int(0.2 * SR); i = int(3.1 * SR)
    x[i:i + m] += bp(rng.standard_normal(m), 800, 5000) * np.sin(np.linspace(0, np.pi, m)) * 0.35
    save_ogg('yompix', x)


# ======================= Порожняк =======================
def car_mat(base, kind):
    """Доски вагона; на боковых гранях — окна с тусклым светом и тёмными головами пассажиров."""
    planks = gc.material(base, crack=0.12, dirt=0.6, sheen=0.04, scale=0.5, edge=0.85)

    def f(gx_, gy_, x, y, fw, fh, side, h):
        c = planks(gx_, gy_, x, y, fw, fh, side, h)
        if side in ('north', 'south', 'up', 'down'):
            return c
        if x % 6 == 0:
            c = shade(c[:3], 0.7)                    # щели между досками
        win = window(x, y, fw, fh, kind)
        if win:
            return win
        return c
    return f


def window(x, y, fw, fh, kind):
    """None или цвет окна. Окна: теплушка — маленькие под крышей, пассажирский — ряд."""
    if kind == 'box':
        wy0, wy1, period, ww = 6, 13, 30, 10
    else:
        wy0, wy1, period, ww = 12, 24, 16, 9
    if not (wy0 <= y < wy1):
        return None
    k = (x - 4) % period
    if x < 4 or x > fw - 5 or k >= ww:
        return None
    # голова в окне: тёмный овал по центру, иногда пустое окно
    idx = (x - 4) // period
    empty = (idx * 7 + (3 if kind == 'box' else 1)) % 5 == 0
    cx, cy = ww / 2 - 0.5, (wy0 + wy1) / 2 + 1.5
    dx, dy = (k - cx) / (ww * 0.28), (y - cy) / ((wy1 - wy0) * 0.42)
    if not empty and dx * dx + dy * dy < 1:
        return (20, 18, 16, 255)
    if not empty and y >= wy1 - 2 and abs(k - cx) < ww * 0.42:
        return (24, 20, 18, 255)                 # плечи
    return (128, 108, 64, 255)


class TrainModel(gc.Model):
    def save_train(self):
        assert self.y + self.rowh <= self.th, ('train', self.y + self.rowh)
        for b in self.bones:
            if not b['cubes']:
                del b['cubes']
        gc.w(f'{A}/geo/{self.name}.geo.json', {'format_version': '1.12.0', 'minecraft:geometry': [{
            'description': {'identifier': f'geometry.glukhoman.{self.name}', 'texture_width': self.tw, 'texture_height': self.th,
                            'visible_bounds_width': 40, 'visible_bounds_height': 6, 'visible_bounds_offset': [0, 3, 0]},
            'bones': self.bones}]})
        gc.w(f'{A}/animations/{self.name}.animation.json', {'format_version': '1.8.0', 'animations': {
            'animation.train.idle': {'animation_length': 2, 'loop': True, 'bones': {
                'body': {'position': {'0': [0, 0, 0], '1': [0, 0.08, 0], '2': [0, 0, 0]}}}}}})
        img = Image.new('RGBA', (self.tw, self.th), (0, 0, 0, 0))
        glow = Image.new('RGBA', (self.tw, self.th), (0, 0, 0, 0))
        px, gx = img.load(), glow.load()
        for (x0, y0, fw, fh, mat, side, front, gl, ylo, yhi) in self.faces:
            for yy in range(fh):
                for xx in range(fw):
                    hgt = yhi - (yy + 0.5) if side not in ('up', 'down') else (yhi if side == 'up' else ylo)
                    c = gc.MATS[mat](x0 + xx, y0 + yy, xx, yy, fw, fh, side, hgt)
                    px[x0 + xx, y0 + yy] = c
                    if c == (128, 108, 64, 255):
                        gx[x0 + xx, y0 + yy] = (110, 92, 52, 255)
        img.save(f'{A}/textures/entity/{self.name}.png')
        glow.save(f'{A}/textures/entity/{self.name}_glowmask.png')


def train():
    gc.D = 1
    gc.MATS['car_box'] = car_mat((58, 36, 30), 'box')
    gc.MATS['car_box2'] = car_mat((50, 38, 32), 'box')
    gc.MATS['car_pass'] = car_mat((40, 46, 40), 'pass')
    gc.MATS['roof'] = gc.material((36, 34, 34), crack=0.0, dirt=0.0, sheen=0.05, scale=0.5)
    gc.MATS['iron'] = gc.material((26, 24, 23), dirt=0.2, sheen=0.06, scale=0.6)
    gc.MATS['door'] = gc.material((42, 28, 24), crack=0.2, dirt=0.5, scale=0.5)
    gc.MATS['wheel'] = gc.material((70, 58, 48), dirt=0.0, sheen=0.25, scale=0.8, edge=0.6)
    m = TrainModel('train', 1024, 1024)
    m.bone('root', None, [0, 0, 0])
    m.bone('body', 'root', [0, 0, 0])
    L, Wd, H, gap = 110, 36, 40, 6
    z = -((L + gap) * 3) / 2
    for i, mat in enumerate(('car_box', 'car_box2', 'car_pass')):
        b = m.bone(f'car{i}', 'body', [0, 0, z + L / 2])
        m.cube(b, [-Wd / 2, 10, z], [Wd, H, L], mat)
        m.cube(b, [-Wd / 2 - 1, 10 + H, z - 1], [Wd + 2, 2, L + 2], 'roof')
        m.cube(b, [-Wd / 2 + 4, 10 + H + 2, z + 4], [Wd - 8, 2, L - 8], 'roof')
        m.cube(b, [-Wd / 2 + 2, 7, z + 4], [Wd - 4, 3, L - 8], 'iron')                # рама
        # тележки узкой колеи: колёса точно на рельсах (нити рельса — ±4.5 px от оси), обод касается головки рельса
        for bc in (z + 20, z + L - 20):
            m.cube(b, [-8, 3, bc - 13], [16, 4, 26], 'iron')                            # рама тележки
            for ax in (bc - 7, bc + 7):
                m.cube(b, [-6.5, 3.5, ax - 0.75], [13, 1.5, 1.5], 'iron')              # ось
                for wx in (-6, 3):
                    m.cube(b, [wx, 0, ax - 4], [3, 8, 8], 'wheel')                      # колесо
            m.cube(b, [-2, 7, bc - 2], [4, 3, 4], 'iron')                               # шкворень
        if i < 2:
            m.cube(b, [-Wd / 2 - 0.5, 10, z + L / 2 - 14], [0.5, H - 4, 28], 'door')   # сдвижная дверь теплушки
            m.cube(b, [Wd / 2, 10, z + L / 2 - 14], [0.5, H - 4, 28], 'door')
        m.cube(b, [-3, 12, z - gap / 2], [6, 6, gap / 2], 'iron')                    # сцепка
        z += L + gap
    m.save_train()
    gc.D = 2


# ======================= жители =======================
def skin(name, *, hair, coat, legs, boots, face=(196, 160, 138), scarf=None, cap=None, beard=None, old=False, girl=False, gown=None):
    def paint(blink=False, talk=False, look=0, half=False):
        rnd = random.Random(name)        # один шум для всех вариантов лица — при смене кадра ничего не «мерцает»
        img = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
        px = img.load()

        def region(x0, y0, w_, h_, col, var=0.06, grad=0.18):
            for y in range(h_):
                for x in range(w_):
                    k = 1 + rnd.uniform(-var, var) - grad * (y / max(1, h_ - 1) - 0.4)
                    px[x0 + x, y0 + y] = shade(col, k)

        def box(u, v, w_, h_, d_, col, var=0.06):
            region(u + d_, v, w_, d_, col, var, 0)
            region(u + d_ + w_, v, w_, d_, shade(col, 0.7)[:3], var, 0)
            region(u, v + d_, 2 * d_ + 2 * w_, h_, col, var)
        box(0, 0, 8, 8, 8, scarf or cap or hair, 0.1)
        region(8, 8, 8, 8, face, 0.04, 0.12)
        top = scarf or cap or hair
        for x in range(8, 16):
            px[x, 8] = shade(top, 1.0)
            if scarf or cap:
                px[x, 9] = shade(top, 0.9)
        if scarf:                               # платок закрывает виски
            for y in range(10, 15):
                px[8, y] = shade(scarf, 0.85)
                px[15, y] = shade(scarf, 0.85)
        brow = shade(hair, 0.9 if not old else 1.4)
        for x in (9, 10, 13, 14):
            px[x, 10] = brow
        eye = (58, 46, 40) if not girl else (70, 56, 44)
        white = (168, 162, 156)
        pupils = {0: (10, 13), -1: (9, 13), 1: (10, 14)}[look]   # взгляд: прямо, влево, вправо (по картинке)
        for x in (9, 10, 13, 14):
            c = eye if x in pupils else white
            px[x, 11] = (c + (255,)) if not blink else shade(face, 0.8)
        for x in (9, 10, 13, 14):
            px[x, 12] = shade(face, 0.84 if old else 0.9)
        if old:
            for x in (9, 14):
                px[x, 13] = shade(face, 0.82)   # морщины
        px[11, 13] = shade(face, 0.9)
        px[12, 13] = shade(face, 0.9)
        if beard:
            for x in range(9, 15):
                for y in (13, 14, 15):
                    if not (y == 13 and x in (11, 12)):
                        px[x, y] = shade(beard, 1 + rnd.uniform(-0.08, 0.08))
        for x in (11, 12):
            px[x, 14] = (126, 86, 82, 255) if not talk else (50, 24, 24, 255)
        if half:                                 # рот приоткрыт (между слогами)
            px[11, 14] = (70, 34, 32, 255)
        if talk and not beard:
            px[11, 15] = shade(face, 0.7)
            px[12, 15] = shade(face, 0.7)
        box(16, 16, 8, 12, 4, coat, 0.07)
        if gown:                                # ночная рубашка ниже куртки/кофты
            region(16, 26, 24, 6, gown, 0.05)
        box(40, 16, 3 if girl else 4, 12, 4, coat, 0.07)
        box(32, 48, 3 if girl else 4, 12, 4, coat, 0.07)
        for (u, v) in ((44, 29), (36, 61)):
            for x in range(u, u + (3 if girl else 4)):
                for y in range(v, v + 3):
                    px[x, y] = shade(face, 0.95)
        box(0, 16, 4, 12, 4, legs, 0.08)
        box(16, 48, 4, 12, 4, legs, 0.08)
        for (u, v) in ((0, 29), (16, 61)):
            for x in range(u, u + 16):
                for y in range(v, v + 3):
                    px[x, y] = shade(boots, 1 + rnd.uniform(-0.05, 0.05))
        if cap:                                 # козырёк фуражки и кокарда
            for x in range(8, 16):
                px[x, 9] = shade(cap, 0.6)
            px[11, 8] = (150, 128, 60, 255)
            px[12, 8] = (150, 128, 60, 255)
        return img
    paint().save(f'{A}/textures/entity/res_{name}.png')
    paint(blink=True).save(f'{A}/textures/entity/res_{name}_blink.png')
    paint(talk=True).save(f'{A}/textures/entity/res_{name}_talk.png')
    paint(half=True).save(f'{A}/textures/entity/res_{name}_half.png')
    # зрачки к правому/левому краю картинки; на лице это правая/левая сторона самого персонажа (картинка — как в зеркале)
    paint(look=-1).save(f'{A}/textures/entity/res_{name}_eyes_r.png')
    paint(look=1).save(f'{A}/textures/entity/res_{name}_eyes_l.png')


def residents():
    skin('polya', hair=(150, 146, 140), coat=(98, 84, 78), legs=(200, 196, 186), boots=(50, 44, 40), face=(196, 168, 150),
         scarf=(92, 70, 74), old=True, gown=(196, 192, 182))
    skin('zakhar', hair=(170, 168, 164), coat=(30, 36, 52), legs=(28, 32, 44), boots=(22, 20, 20), face=(188, 156, 136),
         cap=(30, 36, 52), beard=(160, 158, 154), old=True)
    skin('vika', hair=(96, 66, 44), coat=(112, 52, 56), legs=(48, 50, 66), boots=(40, 34, 32), face=(208, 176, 156), girl=True)
    skin('petrovich', hair=(70, 64, 60), coat=(58, 62, 52), legs=(40, 42, 44), boots=(30, 28, 26), face=(186, 136, 118),
         beard=(86, 78, 72))
    skin('zina', hair=(120, 92, 62), coat=(90, 76, 92), legs=(60, 54, 58), boots=(40, 34, 32), face=(200, 166, 146),
         scarf=(70, 74, 92))
    # остальные жители Елового (перекличка)
    skin('nyura', hair=(160, 156, 150), coat=(70, 60, 54), legs=(50, 46, 44), boots=(36, 30, 28), face=(194, 164, 146), scarf=(110, 46, 50), old=True)
    skin('kolya', hair=(52, 44, 38), coat=(46, 52, 60), legs=(36, 38, 42), boots=(28, 26, 24), face=(190, 148, 128), beard=(60, 50, 44))
    skin('lyuba', hair=(150, 110, 70), coat=(84, 62, 52), legs=(52, 48, 52), boots=(40, 34, 32), face=(204, 170, 150))
    skin('mitya', hair=(120, 96, 66), coat=(60, 84, 70), legs=(44, 46, 60), boots=(36, 32, 30), face=(210, 178, 158), girl=True)


# ======================= блоки и предметы =======================
B = os.path.join(A, 'textures', 'block')
I = os.path.join(A, 'textures', 'item')


def noisy(size, base, var=10):
    img = Image.new('RGBA', size)
    p = img.load()
    for y in range(size[1]):
        for x in range(size[0]):
            d = rnd.randint(-var, var)
            p[x, y] = tuple(max(0, min(255, c + d)) for c in base) + (255,)
    return img


def blocks():
    os.makedirs(B, exist_ok=True)
    os.makedirs(I, exist_ok=True)
    # ящик для кассет: старый почтовый ящик лагпункта, облезлая зелёная краска, щель
    s = noisy((16, 16), (44, 58, 46), 8); d = ImageDraw.Draw(s)
    for _ in range(14):
        x, y = rnd.randint(0, 15), rnd.randint(0, 15)
        d.point((x, y), fill=(92, 60, 40, 255))
    d.rectangle([0, 0, 15, 15], outline=(26, 34, 28, 255)); s.save(f'{B}/box.png'); s.save(f'{B}/box_top.png')
    f = s.copy(); d = ImageDraw.Draw(f)
    d.rectangle([3, 4, 12, 5], fill=(8, 8, 8, 255)); d.rectangle([5, 8, 10, 12], outline=(120, 110, 80, 255)); d.point((7, 10), fill=(140, 120, 70, 255))
    f.save(f'{B}/box_front.png')
    # громкоговоритель: серый рупор
    s = noisy((16, 16), (82, 86, 84), 7); s.save(f'{B}/loudspeaker.png'); s.save(f'{B}/loudspeaker_top.png')
    f = Image.new('RGBA', (16, 16), (0, 0, 0, 255)); d = ImageDraw.Draw(f)
    for r, c in ((7, (70, 74, 72)), (5, (44, 46, 46)), (3, (24, 24, 24)), (1, (10, 10, 10))):
        d.ellipse([8 - r, 8 - r, 7 + r, 7 + r], fill=c + (255,))
    f.save(f'{B}/loudspeaker_front.png')
    # табло: лампа в чёрной панели
    panel = noisy((16, 16), (20, 20, 22), 4); panel.save(f'{B}/board_panel.png')
    for name, col in (('board_on', (238, 176, 70)), ('board_off', (46, 38, 30))):   # лампа в чёрной панели табло
        im = panel.copy(); d = ImageDraw.Draw(im)
        d.rectangle([0, 0, 15, 15], outline=(8, 8, 9, 255))
        d.ellipse([4, 4, 11, 11], fill=(30, 30, 30, 255)); d.ellipse([5, 5, 10, 10], fill=col + (255,))
        if name == 'board_on':
            d.point((6, 6), fill=(255, 236, 190, 255))
        d.line([3, 13, 12, 13], fill=(70, 70, 66, 255))
        im.save(f'{B}/{name}.png')
    for name, col in (('signal_on', (236, 46, 34)), ('signal_off', (58, 20, 18))):   # красный огонь на чёрном щитке
        im = noisy((16, 16), (14, 14, 15), 3); d = ImageDraw.Draw(im)
        d.ellipse([3, 3, 12, 12], fill=(30, 26, 26, 255)); d.ellipse([4, 4, 11, 11], fill=col + (255,))
        if name == 'signal_on':
            d.ellipse([5, 5, 7, 7], fill=(255, 160, 140, 255))
        im.save(f'{B}/{name}.png')
    noisy((16, 16), (14, 14, 15), 3).save(f'{B}/signal_back.png')
    # босые следы на шпалах (тёмные, мокрые)
    for v in range(3):
        im = Image.new('RGBA', (16, 16), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
        for (x, y, flip) in ((4 + v, 2, 0), (9 - v, 9, 1)):
            d.ellipse([x, y, x + 2, y + 4], fill=(30, 26, 24, 200))
            d.ellipse([x, y + 4, x + 2, y + 6], fill=(30, 26, 24, 190))
            for t in range(4):
                d.point((x - 1 + t + flip, y - 1), fill=(30, 26, 24, 170))
        im.save(f'{B}/footprints_{v}.png')
    Image.new('RGBA', (16, 16), (0, 0, 0, 0)).save(f'{B}/footprints_side.png')
    Image.new('RGBA', (16, 16), (0, 0, 0, 0)).save(f'{B}/footprints_top.png')
    # предметы: камера «Панасоник», кассета E-180
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
    d.rectangle([2, 5, 12, 11], fill=(34, 34, 36, 255)); d.rectangle([12, 6, 15, 10], fill=(22, 22, 24, 255)); d.ellipse([13, 7, 15, 9], fill=(70, 80, 96, 255))
    d.rectangle([3, 3, 7, 5], fill=(46, 46, 48, 255)); d.point((4, 7), fill=(200, 30, 30, 255)); d.line([3, 10, 10, 10], fill=(60, 60, 64, 255))
    im.save(f'{I}/camcorder.png')
    # 3D-модель камеры в руке: корпус, объектив, видоискатель
    body = noisy((16, 16), (32, 32, 34), 4); d = ImageDraw.Draw(body)
    d.line([0, 11, 15, 11], fill=(50, 50, 54, 255)); d.rectangle([2, 3, 5, 5], fill=(60, 60, 66, 255)); d.point((12, 4), fill=(200, 30, 30, 255))
    d.text((7, 6), 'P', fill=(120, 120, 126, 255)); body.save(f'{I}/camcorder_body.png')
    lens = noisy((16, 16), (20, 20, 22), 3); d = ImageDraw.Draw(lens)
    d.ellipse([2, 2, 13, 13], fill=(14, 14, 16, 255)); d.ellipse([4, 4, 11, 11], fill=(40, 52, 70, 255)); d.point((6, 6), fill=(150, 170, 200, 255))
    lens.save(f'{I}/camcorder_lens.png')
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
    d.rectangle([1, 4, 14, 12], fill=(24, 24, 26, 255)); d.rectangle([3, 5, 12, 8], fill=(214, 206, 180, 255))
    d.line([4, 6, 10, 6], fill=(60, 50, 80, 255)); d.ellipse([4, 9, 6, 11], fill=(90, 90, 90, 255)); d.ellipse([9, 9, 11, 11], fill=(90, 90, 90, 255))
    im.save(f'{I}/cassette.png')


# ======================= звуки =======================
SR = 22050
SND = os.path.join(A, 'sounds')


def save_ogg(name, x):
    import numpy as np
    x = x / max(1e-6, float(np.max(np.abs(x)))) * 0.9
    with tempfile.NamedTemporaryFile(suffix='.raw', delete=False) as f:
        f.write((x * 32767).astype('<i2').tobytes())
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-f', 's16le', '-ar', str(SR), '-ac', '1', '-i', f.name,
                    '-c:a', 'libvorbis', '-q:a', '3', os.path.join(SND, name + '.ogg')], check=True)
    os.unlink(f.name)


def sounds():
    import numpy as np
    from scipy.signal import butter, sosfilt, fftconvolve
    rng = np.random.default_rng(41)
    t_ = lambda s: np.arange(int(s * SR)) / SR
    lp = lambda x, f: sosfilt(butter(2, f, 'low', fs=SR, output='sos'), x)
    hp = lambda x, f: sosfilt(butter(2, f, 'high', fs=SR, output='sos'), x)
    bp = lambda x, a, b: sosfilt(butter(2, [a, b], 'band', fs=SR, output='sos'), x)

    def verb(x, size=1.2, mix=0.3):
        n = int(size * SR)
        ir = rng.standard_normal(n) * np.exp(-np.arange(n) / SR * 4.5)
        y = fftconvolve(x, lp(ir, 3500))[:len(x)]
        return x * (1 - mix) + y / max(1e-6, np.max(np.abs(y))) * np.max(np.abs(x)) * mix

    # переездный звонок: электрический звонок — молоточек ~16 ударов/с по чашке, металлические обертоны; далеко в тайге
    t = t_(4.0)
    bell = sum(a * np.sin(2 * np.pi * f * t) for f, a in ((1180, 1), (2710, 0.6), (3960, 0.35), (5240, 0.2)))
    hammer = (np.sin(2 * np.pi * 16 * t) > 0.2).astype(float)
    hammer = lp(hammer, 60) * 2
    x = bell * hammer * np.exp(-((t % (1 / 16)) * 30))
    x = verb(bp(x, 600, 6000), 1.6, 0.4)
    x[-int(0.05 * SR):] *= np.linspace(1, 0, int(0.05 * SR))
    save_ogg('crossing_bell', x)
    # мотор камеры: тихий гул лентопротяжки с биением
    t = t_(3.0)
    x = (np.sin(2 * np.pi * 120 * t) * 0.5 + np.sin(2 * np.pi * 240 * t + np.sin(2 * np.pi * 3 * t)) * 0.3) * (1 + 0.15 * np.sin(2 * np.pi * 7 * t))
    x += bp(rng.standard_normal(len(t)), 2000, 6000) * 0.08
    xf = int(0.3 * SR); x[:xf] *= np.linspace(0, 1, xf); x[-xf:] *= np.linspace(1, 0, xf)
    save_ogg('cam_motor', x)
    # реле табло: сухой щелчок + короткий дребезг
    n = int(0.25 * SR); x = np.zeros(n)
    for k, a in ((0, 1.0), (0.012, 0.5), (0.03, 0.25)):
        i = int(k * SR); m_ = int(0.006 * SR)
        x[i:i + m_] += hp(rng.standard_normal(m_), 1500) * a * np.exp(-np.arange(m_) / m_ * 4)
    x += np.sin(2 * np.pi * 50 * np.arange(n) / SR) * 0.05 * np.exp(-np.arange(n) / SR * 20)
    save_ogg('relay', x)
    # громкоговоритель: щелчок, гул сети, шипение (под объявление)
    t = t_(2.5)
    x = np.sin(2 * np.pi * 50 * t) * 0.25 + np.sin(2 * np.pi * 150 * t) * 0.1 + bp(rng.standard_normal(len(t)), 1500, 4000) * 0.15
    x[:int(0.02 * SR)] += hp(rng.standard_normal(int(0.02 * SR)), 800) * 1.5
    x = verb(x, 2.0, 0.5)
    x[-int(0.4 * SR):] *= np.linspace(1, 0, int(0.4 * SR))
    save_ogg('loudspeaker', x)
    # перемотка: визг ленты, растущий тон
    t = t_(1.6)
    f = 900 + 2200 * (t / 1.6) ** 1.5
    x = np.sin(2 * np.pi * np.cumsum(f) / SR) * 0.4 + bp(rng.standard_normal(len(t)), 3000, 8000) * 0.3
    x *= np.minimum(1, t * 8) * np.minimum(1, (1.6 - t) * 10)
    save_ogg('rewind', x)
    # мотовоз вдали: дизель ~9 Гц такт
    t = t_(5.0)
    x = lp(rng.standard_normal(len(t)), 180) * (1 + 0.8 * (np.sin(2 * np.pi * 9 * t) > 0.6)) + np.sin(2 * np.pi * 36 * t) * 0.3
    x = verb(x, 2.5, 0.5) * np.minimum(1, t / 1.5) * np.minimum(1, (5 - t) / 1.5)
    save_ogg('motor', x)
    # стук на стыках без рельсов (тук-тук, далеко)
    n = int(3.0 * SR); x = np.zeros(n)
    for k in (0.2, 0.42, 1.4, 1.62, 2.6, 2.82):
        i = int(k * SR); m_ = int(0.08 * SR)
        x[i:i + m_] += lp(rng.standard_normal(m_), 400) * np.exp(-np.arange(m_) / m_ * 6)
    save_ogg('joints', verb(x, 2.0, 0.5))
    # далёкий гудок: аккорд паровозного свистка (три трубы), атака воздухом, долгий хвост в тумане
    t = t_(3.2)
    env = np.minimum(1, t / 0.18) * np.minimum(1, np.maximum(0, (2.6 - t) / 0.9))
    vib = 1 + 0.004 * np.sin(2 * np.pi * 5.2 * t)
    tone = sum(a * np.sin(2 * np.pi * f * vib * t + rng.uniform(0, 6)) for f, a in ((349, 1), (440, 0.8), (523, 0.6), (698, 0.25), (880, 0.15)))
    air = bp(rng.standard_normal(len(t)), 900, 3500) * 0.35
    x = lp((tone + air) * env, 2600)
    save_ogg('whistle', verb(x, 3.0, 0.55))


if __name__ == '__main__':
    which = sys.argv[1:] or ['watcher', 'train', 'residents', 'human_extra', 'blocks', 'sounds', 'zakhar_sounds', 'yompix_sound']
    for n in which:
        globals()[n]()
        print('ok', n)
