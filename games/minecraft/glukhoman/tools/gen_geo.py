#!/usr/bin/env python3
"""Анимированные модели существ Глухомани для GeckoLib: Жердь, Слухач, Хор, Двойник (+ Анна на скелете людей Новограда).
Пишет geo/*.geo.json, animations/*.animation.json, textures/entity/*.png и *_glowmask.png (светящиеся глаза).
Все формы, текстуры и движения — свои. Запуск: /tmp/claude-0/ttsenv/bin/python tools/gen_geo.py
"""
import json, math, os, random
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
A = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'glukhoman')
NOVO = os.path.join(HERE, '..', '..', 'novograd', 'src', 'main', 'resources', 'assets', 'novograd')
rnd = random.Random(1996)


def w(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    json.dump(obj, open(path, 'w'), separators=(',', ':'))


# ======================= модели =======================
class Model:
    def __init__(self, name, tw=128, th=128):
        self.name, self.tw, self.th = name, tw, th
        self.bones, self.cubes = [], []   # cubes: (bone, origin, size, mat, face)
        self.u = self.v = 0
        self.rowh = 0

    def bone(self, name, parent, pivot, rot=None):
        b = {'name': name, 'pivot': pivot, 'cubes': []}
        if parent:
            b['parent'] = parent
        if rot:
            b['rotation'] = rot
        self.bones.append(b)
        return name

    def cube(self, bone, origin, size, mat='skin', face=None, mirror=False):
        w_, h_, d_ = [max(1, math.ceil(s)) for s in size]
        fw, fh = 2 * d_ + 2 * w_, d_ + h_
        if self.u + fw > self.tw:
            self.u, self.v, self.rowh = 0, self.v + self.rowh, 0
        uv = [self.u, self.v]
        self.u += fw
        self.rowh = max(self.rowh, fh)
        b = next(x for x in self.bones if x['name'] == bone)
        c = {'origin': origin, 'size': size, 'uv': uv}
        if mirror:
            c['mirror'] = True
        b['cubes'].append(c)
        self.cubes.append((uv, (w_, h_, d_), mat, face))

    def save(self, height):
        assert self.v + self.rowh <= self.th, (self.name, self.v + self.rowh)
        geo = {'format_version': '1.12.0', 'minecraft:geometry': [{
            'description': {'identifier': f'geometry.glukhoman.{self.name}', 'texture_width': self.tw, 'texture_height': self.th,
                            'visible_bounds_width': 4, 'visible_bounds_height': height, 'visible_bounds_offset': [0, height / 2, 0]},
            'bones': self.bones}]}
        w(f'{A}/geo/{self.name}.geo.json', geo)
        self.paint()

    # --------- текстура: каждая грань куба красится материалом, лицевая (north) — с деталями ---------
    def paint(self):
        img = Image.new('RGBA', (self.tw, self.th), (0, 0, 0, 0))
        glow = Image.new('RGBA', (self.tw, self.th), (0, 0, 0, 0))
        px, gx = img.load(), glow.load()
        for (u, v), (w_, h_, d_), mat, face in self.cubes:
            rects = {'up': (u + d_, v, w_, d_), 'down': (u + d_ + w_, v, w_, d_), 'east': (u, v + d_, d_, h_),
                     'north': (u + d_, v + d_, w_, h_), 'west': (u + d_ + w_, v + d_, d_, h_), 'south': (u + 2 * d_ + w_, v + d_, w_, h_)}
            for side, (x0, y0, rw, rh) in rects.items():
                for y in range(rh):
                    for x in range(rw):
                        px[x0 + x, y0 + y] = MATS[mat](x, y, rw, rh, side)
                if side == 'north' and face:
                    FACES[face](px, gx, x0, y0, rw, rh)
        img.save(f'{A}/textures/entity/{self.name}.png')
        if glow.getbbox():   # пустую маску GeckoLib не принимает
            glow.save(f'{A}/textures/entity/{self.name}_glowmask.png')


def shade(c, k):
    return tuple(max(0, min(255, int(x * k))) for x in c[:3]) + (255,)


def noise_mat(base, var, streak=None, dark_edge=0.75):
    def f(x, y, w, h, side):
        k = 1 + rnd.uniform(-var, var)
        if streak and (x * 7 + y // 3) % streak == 0:
            k *= 0.7
        if side == 'down':
            k *= 0.7
        if x == 0 or y == 0 or x == w - 1 or y == h - 1:
            k *= dark_edge if w > 2 and h > 2 else 1
        return shade(base, k)
    return f


MATS = {
    'bark': noise_mat((132, 124, 108), 0.12, 5),
    'bark_dark': noise_mat((88, 80, 70), 0.12, 4),
    'pale': noise_mat((196, 188, 172), 0.06),
    'skin': noise_mat((160, 150, 144), 0.08),
    'skin_dark': noise_mat((104, 92, 92), 0.08),
    'ear': noise_mat((176, 132, 128), 0.1, 3),
    'mass': noise_mat((38, 32, 42), 0.15),
    'mass2': noise_mat((58, 44, 60), 0.15),
    'mouth': noise_mat((26, 8, 10), 0.1),
    'bone': noise_mat((214, 206, 188), 0.05),
}


def face_pole(px, gx, x0, y0, w, h):
    # пустые глазницы с крошечными огоньками, длинная щель рта
    for ex in (1, w - 3):
        for yy in range(2, 5):
            for xx in range(ex, ex + 2):
                px[x0 + xx, y0 + yy] = (6, 6, 6, 255)
        px[x0 + ex + (1 if ex < w / 2 else 0), y0 + 3] = (255, 236, 200, 255)
        gx[x0 + ex + (1 if ex < w / 2 else 0), y0 + 3] = (255, 236, 200, 255)
    for xx in range(1, w - 1):
        px[x0 + xx, y0 + h - 2] = (20, 8, 8, 255)


def face_listener(px, gx, x0, y0, w, h):
    # лица нет: гладкая кожа, сшитые веки
    for ex in (1, w - 3):
        for xx in range(ex, ex + 2):
            px[x0 + xx, y0 + 2] = (90, 70, 70, 255)
    for xx in range(1, w - 1):
        px[x0 + xx, y0 + h - 1] = (40, 10, 12, 255)


def face_choir(px, gx, x0, y0, w, h):
    # бледное лицо: закрытые глаза, открытый рот; у части — светящиеся зрачки
    for y in range(h):
        for x in range(w):
            px[x0 + x, y0 + y] = shade((206, 196, 186), 1 + rnd.uniform(-0.05, 0.05))
    lit = rnd.random() < 0.5
    for ex in (1, w - 2):
        px[x0 + ex, y0 + 1] = (230, 210, 120, 255) if lit else (20, 14, 14, 255)
        if lit:
            gx[x0 + ex, y0 + 1] = (230, 210, 120, 255)
    for xx in range(1, w - 1):
        px[x0 + xx, y0 + h - 2] = (30, 10, 10, 255)


FACES = {'pole': face_pole, 'listener': face_listener, 'choir': face_choir}


# ======================= анимации =======================
def anim(bones_fn, length, loop=True, fps=12, jerk=False):
    """bones_fn(t) -> {bone: {'rotation': [x,y,z], 'position': [x,y,z]}}. jerk — рывками (держим позу, потом скачок)."""
    frames = {}
    n = max(1, int(length * fps))
    for i in range(n + 1):
        t = min(length, i / fps)
        pose = bones_fn(t)
        for b, ch in pose.items():
            for kind, val in ch.items():
                d = frames.setdefault(b, {}).setdefault(kind, {})
                key = f'{t:.3f}'.rstrip('0').rstrip('.') or '0'
                if jerk and i > 0:
                    hold = f'{max(0, t - 0.02):.3f}'.rstrip('0').rstrip('.')
                    prev = bones_fn(max(0, (i - 1) / fps)).get(b, {}).get(kind)
                    if prev is not None:
                        d[hold] = [round(x, 2) for x in prev]
                d[key] = [round(x, 2) for x in val]
    out = {'animation_length': length, 'bones': frames}
    if loop:
        out['loop'] = True
    else:
        out['loop'] = 'hold_on_last_frame'
    return out


S = math.sin
C = math.cos


def wob(t, f, a, ph=0):
    return S(t * f * 2 * math.pi + ph) * a


# ---------------------- Жердь ----------------------
def pole():
    m = Model('pole', 128, 128)
    m.bone('root', None, [0, 0, 0])
    m.bone('hips', 'root', [0, 30, 0]); m.cube('hips', [-3, 28, -1.5], [6, 4, 3], 'bark_dark')
    m.bone('spine', 'hips', [0, 32, 0]); m.cube('spine', [-2.5, 32, -1.5], [5, 8, 3], 'bark')
    m.bone('chest', 'spine', [0, 40, 0]); m.cube('chest', [-3.5, 40, -2], [7, 8, 4], 'bark')
    m.cube('chest', [-3, 41, -2.4], [6, 1, 1], 'bone'); m.cube('chest', [-3, 43, -2.4], [6, 1, 1], 'bone'); m.cube('chest', [-3, 45, -2.4], [6, 1, 1], 'bone')
    m.bone('neck', 'chest', [0, 48, 0]); m.cube('neck', [-1, 48, -1], [2, 7, 2], 'bark')
    m.bone('head', 'neck', [0, 55, 0]); m.cube('head', [-3, 55, -3.5], [6, 7, 6], 'pale', 'pole')
    m.cube('head', [-4, 61, 0], [1, 6, 1], 'bark_dark'); m.cube('head', [3, 60, -1], [1, 8, 1], 'bark_dark'); m.cube('head', [-1, 62, 1], [1, 5, 1], 'bark_dark')
    m.bone('jaw', 'head', [0, 55, -1]); m.cube('jaw', [-2.5, 53, -3.7], [5, 2, 4], 'pale')
    for s, n in ((1, 'left'), (-1, 'right')):
        x = 4 * s
        m.bone(f'{n}_arm', 'chest', [x, 47, 0])
        m.cube(f'{n}_arm', [x - 1 if s > 0 else x - 1, 35, -1], [2, 12, 2], 'bark', mirror=s < 0)
        m.bone(f'{n}_forearm', f'{n}_arm', [x, 35, 0]); m.cube(f'{n}_forearm', [x - 0.9, 21, -0.9], [1.8, 14, 1.8], 'bark')
        m.bone(f'{n}_hand', f'{n}_forearm', [x, 21, 0]); m.cube(f'{n}_hand', [x - 1, 18, -1], [2, 3, 2], 'pale')
        for i, fx in enumerate((-0.8, 0, 0.8)):
            m.cube(f'{n}_hand', [x + fx - 0.3, 9 + i, -0.3], [0.6, 9 - i, 0.6], 'pale')
        lx = 1.8 * s
        m.bone(f'{n}_leg', 'hips', [lx, 29, 0]); m.cube(f'{n}_leg', [lx - 1, 15, -1], [2, 14, 2], 'bark_dark')
        m.bone(f'{n}_shin', f'{n}_leg', [lx, 15, 0]); m.cube(f'{n}_shin', [lx - 0.9, 1, -0.9], [1.8, 14, 1.8], 'bark_dark')
        m.cube(f'{n}_shin', [lx - 1.2, 0, -2.6], [2.4, 1, 3.6], 'bark_dark')
    m.save(5)
    A_ = {}

    def legs(t, f, a):
        return {'left_leg': {'rotation': [wob(t, f, a), 0, 0]}, 'right_leg': {'rotation': [-wob(t, f, a), 0, 0]},
                'left_shin': {'rotation': [max(0, wob(t, f, a * 1.2, 1.2)), 0, 0]}, 'right_shin': {'rotation': [max(0, -wob(t, f, a * 1.2, 1.2)), 0, 0]}}
    A_['idle'] = anim(lambda t: {'head': {'rotation': [4, wob(t, 0.1, 8), 18 + wob(t, 0.07, 10)]}, 'chest': {'rotation': [wob(t, 0.2, 2), 0, wob(t, 0.13, 2)]},
                                 'left_hand': {'rotation': [0, 0, wob(t, 1.7, 6) if (t % 3) > 2.6 else 0]}, 'jaw': {'rotation': [2 + wob(t, 0.3, 2), 0, 0]}}, 6)
    A_['still'] = anim(lambda t: {'head': {'rotation': [12, 0, 38]}, 'neck': {'rotation': [6, 0, 8]}, 'left_arm': {'rotation': [0, 0, -3]}, 'right_arm': {'rotation': [0, 0, 3]}}, 1)
    A_['walk'] = anim(lambda t: {**legs(t, 0.6, 28), 'left_arm': {'rotation': [-wob(t, 0.6, 14, 0.6), 0, -4]}, 'right_arm': {'rotation': [wob(t, 0.6, 14, 0.6), 0, 4]},
                                 'head': {'rotation': [8, 0, 22 + wob(t, 1.2, 6)]}, 'spine': {'rotation': [6, 0, wob(t, 0.6, 3)]}}, 1.667)
    A_['run'] = anim(lambda t: {**legs(t, 2.2, 55), 'spine': {'rotation': [32, 0, wob(t, 2.2, 6)]}, 'chest': {'rotation': [14, 0, 0]},
                                'left_arm': {'rotation': [55 + wob(t, 2.2, 20), 0, -18]}, 'right_arm': {'rotation': [55 - wob(t, 2.2, 20), 0, 18]},
                                'head': {'rotation': [-40, wob(t, 4.4, 6), wob(t, 3.3, 10)]}, 'jaw': {'rotation': [20, 0, 0]}}, 0.455)
    A_['peek'] = anim(lambda t: {'spine': {'rotation': [0, 0, 32]}, 'chest': {'rotation': [0, 0, 14]}, 'head': {'rotation': [6, -12, 62 + wob(t, 0.2, 3)]},
                                 'left_arm': {'rotation': [-70, 0, 30]}, 'left_forearm': {'rotation': [-30, 0, 0]}, 'right_leg': {'rotation': [0, 0, 8]}}, 5)
    A_['tilt'] = anim(lambda t: {'head': {'rotation': [0, 0, min(95, t * 60) if t < 1.6 else 0]}, 'neck': {'rotation': [0, 0, min(20, t * 15) if t < 1.6 else 0]}}, 2.2, jerk=True, fps=8)
    A_['turn180'] = anim(lambda t: {'head': {'rotation': [0, min(180, int(t * 6) * 36), 10]}}, 1.2, loop=False, jerk=True, fps=6)
    A_['twitch'] = anim(lambda t: {b: {'rotation': [rnd.uniform(-25, 25) * (int(t * 9 + i) % 3 == 0), rnd.uniform(-20, 20) * (int(t * 7 + i) % 4 == 0), rnd.uniform(-30, 30) * (int(t * 5 + i) % 3 == 0)]}
                                 for i, b in enumerate(('head', 'left_arm', 'right_forearm', 'neck', 'left_hand'))}, 2, jerk=True, fps=9)
    A_['attack'] = anim(lambda t: {'left_arm': {'rotation': [-160 + t * 260 if t < 0.6 else -4, 0, -20]}, 'right_arm': {'rotation': [-160 + t * 260 if t < 0.6 else -4, 0, 20]},
                                   'jaw': {'rotation': [45 if t < 0.5 else 5, 0, 0]}, 'spine': {'rotation': [25 * min(1, t * 3), 0, 0]}, 'head': {'rotation': [-30, 0, 0]}}, 0.8, loop=False)
    A_['spawn'] = anim(lambda t: {'spine': {'rotation': [max(0, 95 - t * 70), 0, 0]}, 'left_leg': {'rotation': [max(0, -80 + t * 60) * -1, 0, 0]}, 'right_leg': {'rotation': [max(0, -80 + t * 60) * -1, 0, 0]},
                                  'left_shin': {'rotation': [max(0, 120 - t * 90), 0, 0]}, 'right_shin': {'rotation': [max(0, 120 - t * 90), 0, 0]},
                                  'root': {'position': [0, min(0, -26 + t * 20), 0]}, 'head': {'rotation': [0, 0, max(0, 90 - t * 70)]}}, 1.4, loop=False, jerk=True, fps=8)
    A_['vanish'] = anim(lambda t: {'spine': {'rotation': [min(100, t * 160), 0, 0]}, 'root': {'position': [0, -min(40, t * 60), 0]}, 'head': {'rotation': [0, 0, t * 200]}}, 0.75, loop=False, jerk=True, fps=10)
    A_['knock'] = anim(lambda t: {'right_arm': {'rotation': [-75 + (12 if (t * 3) % 1 < 0.25 else 0), 0, 8]}, 'right_forearm': {'rotation': [-40, 0, 0]},
                                  'head': {'rotation': [30, 0, 25]}, 'spine': {'rotation': [12, 0, 0]}}, 2)
    A_['jumpscare'] = anim(lambda t: {'head': {'rotation': [-20 + wob(t, 9, 8), wob(t, 11, 12), wob(t, 7, 15)], 'position': [0, 0, -min(6, t * 14)]},
                                      'jaw': {'rotation': [min(70, t * 200), 0, 0]}, 'left_arm': {'rotation': [-110, 0, -60]}, 'right_arm': {'rotation': [-110, 0, 60]},
                                      'neck': {'rotation': [-25, 0, 0], 'position': [0, 0, -min(3, t * 8)]}}, 1.2, loop=False, fps=20)
    return A_


# ---------------------- Слухач ----------------------
def listener():
    m = Model('listener', 128, 64)
    m.bone('root', None, [0, 0, 0])
    m.bone('body', 'root', [0, 10, 0]); m.cube('body', [-4, 8, -6], [8, 5, 12], 'skin')
    for i in range(4):
        m.cube('body', [-0.5, 13, -5 + i * 3], [1, 1.5, 1.5], 'bone')
    m.bone('pelvis', 'body', [0, 10, 5]); m.cube('pelvis', [-3.5, 7.5, 5], [7, 4, 4], 'skin_dark')
    m.bone('neck', 'body', [0, 11, -6]); m.cube('neck', [-1.5, 9.5, -10], [3, 3, 4], 'skin')
    m.bone('head', 'neck', [0, 11, -10]); m.cube('head', [-3, 8, -16], [6, 6, 6], 'skin', 'listener')
    m.bone('jaw', 'head', [0, 9, -11]); m.cube('jaw', [-2.5, 7, -16.2], [5, 2, 5], 'mouth')
    for s, n in ((1, 'left'), (-1, 'right')):
        m.bone(f'{n}_ear', 'head', [3 * s, 12, -13]); m.cube(f'{n}_ear', [3 * s - (0 if s > 0 else 1), 10, -16], [1, 10, 7], 'ear')
        for fb, z in (('front', -4), ('back', 6)):
            b = f'{fb}_{n}'
            m.bone(b, 'body' if fb == 'front' else 'pelvis', [4 * s, 10, z], [0, 0, -40 * s])
            m.cube(b, [4 * s - 1, 2, z - 1], [2, 8, 2], 'skin')
            m.bone(b + '_low', b, [4 * s, 2, z], [0, 0, 70 * s])
            m.cube(b + '_low', [4 * s - 0.8, -7, z - 0.8], [1.6, 9, 1.6], 'skin_dark')
    m.save(2)
    A_ = {}

    def crawl(t, f, a):
        return {'front_left': {'rotation': [wob(t, f, a), 0, -40]}, 'back_right': {'rotation': [wob(t, f, a), 0, 40]},
                'front_right': {'rotation': [-wob(t, f, a), 0, 40]}, 'back_left': {'rotation': [-wob(t, f, a), 0, -40]},
                'body': {'rotation': [0, wob(t, f, 5), wob(t, f, 4)], 'position': [0, abs(wob(t, f, 1)), 0]}}
    A_['idle'] = anim(lambda t: {'body': {'position': [0, wob(t, 0.4, 0.4), 0]}, 'left_ear': {'rotation': [0, 0, 10 + wob(t, 0.5, 6)]},
                                 'right_ear': {'rotation': [0, 0, -10 - wob(t, 0.5, 6)]}, 'head': {'rotation': [wob(t, 0.2, 6), wob(t, 0.13, 15), 0]}}, 5)
    A_['crawl'] = anim(lambda t: {**crawl(t, 1.2, 28), 'head': {'rotation': [0, wob(t, 0.6, 12), wob(t, 1.2, 8)]}}, 0.833)
    A_['sprint'] = anim(lambda t: {**crawl(t, 3, 50), 'head': {'rotation': [-15, 0, wob(t, 3, 12)]}, 'jaw': {'rotation': [25, 0, 0]},
                                   'left_ear': {'rotation': [0, 0, 40]}, 'right_ear': {'rotation': [0, 0, -40]}}, 0.333)
    A_['listen'] = anim(lambda t: {'head': {'rotation': [-10, [0, 35, -40, 70, -10, 0][int(t * 3) % 6], [0, 25, -30, 45, 0, 15][int(t * 3) % 6]]},
                                   'left_ear': {'rotation': [0, 0, 45]}, 'right_ear': {'rotation': [0, 0, -45]}, 'body': {'rotation': [-8, 0, 0]}}, 2, jerk=True, fps=6)
    A_['attack'] = anim(lambda t: {'body': {'rotation': [-35 if t < 0.4 else 10, 0, 0], 'position': [0, 4 if t < 0.4 else 0, -6 * min(1, t * 3)]},
                                   'front_left': {'rotation': [-90 if t < 0.4 else 20, 0, -30]}, 'front_right': {'rotation': [-90 if t < 0.4 else 20, 0, 30]},
                                   'jaw': {'rotation': [55, 0, 0]}}, 0.7, loop=False)
    A_['turn180'] = anim(lambda t: {'head': {'rotation': [0, min(180, int(t * 6) * 36), 0]}}, 1.2, loop=False, jerk=True, fps=6)
    A_['twitch'] = anim(lambda t: {b: {'rotation': [rnd.uniform(-30, 30) * (int(t * 8 + i) % 3 == 0), 0, rnd.uniform(-35, 35) * (int(t * 6 + i) % 2 == 0)]}
                                   for i, b in enumerate(('head', 'front_left_low', 'back_right_low', 'jaw'))}, 1.5, jerk=True, fps=10)
    A_['spawn'] = anim(lambda t: {'root': {'position': [0, min(0, -12 + t * 10), 0]}, 'head': {'rotation': [60 - t * 50, 0, 0]}}, 1.2, loop=False, jerk=True, fps=8)
    A_['vanish'] = anim(lambda t: {'root': {'position': [0, -t * 20, 0]}}, 0.75, loop=False)
    A_['jumpscare'] = anim(lambda t: {'body': {'rotation': [-12, 0, 0], 'position': [0, 4, -min(8, t * 20)]}, 'jaw': {'rotation': [min(75, t * 220), 0, 0]},
                                      'head': {'rotation': [-20 + wob(t, 10, 10), wob(t, 13, 14), 0]}, 'left_ear': {'rotation': [0, 0, 60]}, 'right_ear': {'rotation': [0, 0, -60]},
                                      'front_left': {'rotation': [-120, 0, -50]}, 'front_right': {'rotation': [-120, 0, 50]}}, 1.2, loop=False, fps=20)
    return A_


# ---------------------- Хор ----------------------
FACE_POS = [(-6, 34, -7.5), (2, 37, -7.5), (-1, 27, -7.5), (6, 28, -7.5), (-8, 22, -7.5), (3, 20, -7.5), (-2, 44, -5.5)]


def choir():
    m = Model('choir', 256, 128)
    m.bone('root', None, [0, 0, 0])
    m.bone('mass', 'root', [0, 18, 0]); m.cube('mass', [-8, 0, -6], [16, 18, 12], 'mass2')
    m.bone('body', 'mass', [0, 18, 0]); m.cube('body', [-10, 18, -7], [20, 22, 14], 'mass')
    m.cube('body', [-6, 40, -5], [12, 8, 10], 'mass')
    for i, (x, y, z) in enumerate(FACE_POS):
        m.bone(f'face{i}', 'body', [x, y, z]); m.cube(f'face{i}', [x - 2.5, y - 2, z - 2], [5, 5, 3], 'pale', 'choir')
        m.bone(f'mouth{i}', f'face{i}', [x, y - 1, z - 1]); m.cube(f'mouth{i}', [x - 2, y - 3.5, z - 2.2], [4, 1.5, 2.5], 'pale')
    for s, n in ((1, 'left'), (-1, 'right')):
        x = 10 * s
        m.bone(f'{n}_arm', 'body', [x, 38, 0]); m.cube(f'{n}_arm', [x - 1.5, 22, -1.5], [3, 16, 3], 'mass2')
        m.bone(f'{n}_forearm', f'{n}_arm', [x, 22, 0]); m.cube(f'{n}_forearm', [x - 1.3, 6, -1.3], [2.6, 16, 2.6], 'mass2')
        m.bone(f'{n}_hand', f'{n}_forearm', [x, 6, 0]); m.cube(f'{n}_hand', [x - 2, 0, -2], [4, 6, 4], 'pale')
    for i in range(4):
        a = i * math.pi / 2 + 0.6
        tx, tz = math.cos(a) * 7, math.sin(a) * 5
        m.bone(f'tendril{i}', 'mass', [tx, 4, tz]); m.cube(f'tendril{i}', [tx - 1, 0, tz - 1], [2, 4, 2], 'mass')
    m.save(5)
    A_ = {}

    def faces(t, open_=0.0, speed=1.0):
        d = {}
        for i in range(len(FACE_POS)):
            d[f'face{i}'] = {'rotation': [wob(t, 0.3 * speed, 8, i), wob(t, 0.2 * speed, 20, i * 2), wob(t, 0.25 * speed, 15, i)]}
            d[f'mouth{i}'] = {'rotation': [max(0, open_ * 50 + wob(t, 1.1 * speed, 12, i)), 0, 0]}
        return d
    A_['idle'] = anim(lambda t: {**faces(t, 0.1), 'body': {'rotation': [wob(t, 0.25, 3), 0, wob(t, 0.18, 3)]}, 'left_arm': {'rotation': [wob(t, 0.2, 5), 0, -6]},
                                 'right_arm': {'rotation': [wob(t, 0.2, 5, 1), 0, 6]}}, 5)
    A_['walk'] = anim(lambda t: {**faces(t, 0.2, 2), 'body': {'rotation': [8, wob(t, 0.6, 8), wob(t, 0.6, 6)]}, 'mass': {'position': [0, abs(wob(t, 0.6, 1.5)), 0]},
                                 'left_arm': {'rotation': [wob(t, 0.6, 25), 0, -8]}, 'right_arm': {'rotation': [-wob(t, 0.6, 25), 0, 8]},
                                 **{f'tendril{i}': {'rotation': [wob(t, 1.2, 25, i), 0, 0]} for i in range(4)}}, 1.667)
    A_['run'] = anim(lambda t: {**faces(t, 0.6, 4), 'body': {'rotation': [25, wob(t, 1.5, 10), wob(t, 1.5, 8)]}, 'mass': {'position': [0, abs(wob(t, 1.5, 3)), 0]},
                                'left_arm': {'rotation': [-60 + wob(t, 1.5, 40), 0, -15]}, 'right_arm': {'rotation': [-60 - wob(t, 1.5, 40), 0, 15]}}, 0.667)
    A_['attack'] = anim(lambda t: {**faces(t, 1), 'left_arm': {'rotation': [-170 + t * 300 if t < 0.6 else 10, 0, -10]}, 'right_arm': {'rotation': [-170 + t * 300 if t < 0.6 else 10, 0, 10]},
                                   'body': {'rotation': [-10 + t * 40, 0, 0]}}, 0.8, loop=False)
    A_['stunned'] = anim(lambda t: {**faces(t, 1, 6), 'body': {'rotation': [-25 + wob(t, 6, 6), wob(t, 7, 6), 0]}, 'left_arm': {'rotation': [-100, 0, -40]},
                                    'right_arm': {'rotation': [-100, 0, 40]}, 'left_forearm': {'rotation': [-60, 0, 0]}, 'right_forearm': {'rotation': [-60, 0, 0]}}, 1, jerk=True, fps=10)
    A_['roar'] = anim(lambda t: {**faces(t, 1, 3), 'body': {'rotation': [-20, 0, wob(t, 4, 4)]}, 'left_arm': {'rotation': [-40, 0, -50]}, 'right_arm': {'rotation': [-40, 0, 50]}}, 2)
    A_['spawn'] = anim(lambda t: {'root': {'position': [0, min(0, -50 + t * 25), 0]}, **faces(t, 0.8, 3), 'body': {'rotation': [max(0, 40 - t * 20), 0, 0]}}, 2, loop=False)
    A_['vanish'] = anim(lambda t: {'root': {'position': [0, -t * 30, 0]}, **faces(t, 1, 6), 'body': {'rotation': [t * 30, 0, wob(t, 5, 8)]}}, 1.5, loop=False)
    A_['jumpscare'] = anim(lambda t: {**faces(t, 1, 8), 'body': {'rotation': [-30, 0, 0], 'position': [0, 0, -min(10, t * 25)]},
                                      'left_arm': {'rotation': [-130, 0, -70]}, 'right_arm': {'rotation': [-130, 0, 70]}}, 1.2, loop=False, fps=20)
    return A_


# ---------------------- люди: Анна и Двойник на скелете Новограда ----------------------
HUMAN_KEEP = ['idle', 'walk', 'run', 'talk', 'think', 'upset', 'point', 'type', 'rummage', 'listen_1', 'down', 'getup', 'wave', 'sit', 'sleep']


def humans():
    for g in ('citizen.geo.json', 'citizen_slim.geo.json'):
        geo = json.load(open(f'{NOVO}/geo/{g}'))
        name = 'human' if g == 'citizen.geo.json' else 'human_slim'
        geo['minecraft:geometry'][0]['description']['identifier'] = f'geometry.glukhoman.{name}'
        w(f'{A}/geo/{name}.geo.json', geo)
    src = json.load(open(f'{NOVO}/animations/citizen.animation.json'))['animations']
    out = {}
    for k in HUMAN_KEEP:
        out[f'animation.human.{k}'] = src[f'animation.citizen.{k}']
    # жуткие позы Двойника — свои
    out['animation.human.still'] = anim(lambda t: {'head': {'rotation': [8, 0, 28]}, 'right_arm': {'rotation': [0, 0, 4]}, 'left_arm': {'rotation': [0, 0, -4]}}, 1)
    out['animation.human.slowwave'] = anim(lambda t: {'right_arm': {'rotation': [-160, 0, 10 + wob(t, 0.25, 25)]}, 'head': {'rotation': [5, 0, 25]}}, 4)
    out['animation.human.turn180'] = anim(lambda t: {'head': {'rotation': [0, min(180, int(t * 6) * 36), 0]}}, 1.2, loop=False, jerk=True, fps=6)
    out['animation.human.twitch'] = anim(lambda t: {b: {'rotation': [rnd.uniform(-30, 30) * (int(t * 8 + i) % 3 == 0), rnd.uniform(-40, 40) * (int(t * 6 + i) % 4 == 0), rnd.uniform(-35, 35) * (int(t * 5 + i) % 3 == 0)]}
                                             for i, b in enumerate(('head', 'right_arm', 'left_forearm', 'chest'))}, 1.6, jerk=True, fps=9)
    out['animation.human.knock'] = anim(lambda t: {'right_arm': {'rotation': [-80 + (14 if (t * 3) % 1 < 0.25 else 0), 0, 6]}, 'right_forearm': {'rotation': [-50, 0, 0]}, 'head': {'rotation': [20, 0, 0]}}, 2)
    out['animation.human.jumpscare'] = anim(lambda t: {'head': {'rotation': [-15 + wob(t, 9, 10), wob(t, 12, 14), wob(t, 8, 18)], 'position': [0, 0, -min(5, t * 12)]},
                                                       'right_arm': {'rotation': [-120, 0, 40]}, 'left_arm': {'rotation': [-120, 0, -40]}}, 1.2, loop=False, fps=20)
    out['animation.human.write'] = out['animation.human.type']
    w(f'{A}/animations/human.animation.json', {'format_version': '1.8.0', 'animations': out})


def human_skin(name, coat, pants, hair, face=(226, 190, 160), eyes=(40, 60, 80), glow=False):
    """Скин 64×64 в раскладке игрока."""
    img = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    g = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
    gd = ImageDraw.Draw(g)

    def box(u, v, w_, h_, dd, col, var=10):
        for y in range(v, v + dd + h_):
            for x in range(u, u + 2 * dd + 2 * w_):
                if y < v + dd and not (u + dd <= x < u + dd + 2 * w_):
                    continue
                img.putpixel((x, y), shade(col, 1 + rnd.uniform(-var, var) / 100))
    box(0, 0, 8, 8, 8, hair)
    for y in range(8, 16):
        for x in range(8, 16):
            img.putpixel((x, y), shade(face, 1 + rnd.uniform(-0.04, 0.04)))
    d.rectangle([8, 8, 15, 9], fill=hair + (255,))
    for ex in (9, 13):
        d.rectangle([ex, 11, ex + 1, 11], fill=(255, 255, 255, 255) if not glow else (10, 10, 10, 255))
        img.putpixel((ex + (1 if ex == 9 else 0), 11), eyes + (255,))
        if glow:
            g.putpixel((ex + (1 if ex == 9 else 0), 11), eyes + (255,))
    d.line([11, 13, 12, 13], fill=(150, 90, 90, 255))
    box(16, 16, 8, 12, 4, coat)                        # туловище
    box(40, 16, 4, 12, 4, coat); box(32, 48, 4, 12, 4, coat)   # руки
    box(0, 16, 4, 12, 4, pants); box(16, 48, 4, 12, 4, pants)  # ноги
    for (u, v) in ((44, 28), (36, 60)):
        d.rectangle([u, v, u + 7, v + 3], fill=face + (255,))
    for (u, v) in ((0, 28), (16, 60)):
        d.rectangle([u, v, u + 15, v + 3], fill=(40, 30, 26, 255))
    img.save(f'{A}/textures/entity/{name}.png')
    if g.getbbox():
        g.save(f'{A}/textures/entity/{name}_glowmask.png')


def main():
    # существа 0.3 — tools/gen_creatures.py; здесь только люди (Анна, Двойник)
    humans()
    human_skin('anna', (214, 110, 40), (52, 62, 96), (58, 40, 30))
    # лица Анны: моргание, «говорит» (рот открыт), испуг (широкие глаза, открытый рот)
    base = Image.open(f'{A}/textures/entity/anna.png')
    for name, fn in (('anna_blink', lambda d: [d.line([9, 11, 10, 11], fill=(150, 110, 90, 255)), d.line([13, 11, 14, 11], fill=(150, 110, 90, 255))]),
                     ('anna_talk', lambda d: d.rectangle([11, 13, 12, 14], fill=(60, 20, 20, 255))),
                     ('anna_fear', lambda d: [d.rectangle([9, 10, 10, 11], fill=(255, 255, 255, 255)), d.rectangle([13, 10, 14, 11], fill=(255, 255, 255, 255)),
                                              d.point((10, 11), fill=(20, 30, 40, 255)), d.point((13, 11), fill=(20, 30, 40, 255)), d.rectangle([11, 13, 12, 14], fill=(50, 15, 15, 255))])):
        im = base.copy()
        fn(ImageDraw.Draw(im))
        im.save(f'{A}/textures/entity/{name}.png')
    # Двойник: ватник, шапка, слишком бледное лицо, тёмные глаза с огоньком
    human_skin('doppel', (70, 74, 70), (40, 42, 46), (30, 30, 34), face=(214, 210, 204), eyes=(240, 230, 190), glow=True)
    # кровь на ватнике и рукавах Двойника (засохшая, с бликами)
    im = Image.open(f'{A}/textures/entity/doppel.png')
    for (u, v, w_, h_) in ((20, 22, 6, 6), (17, 26, 3, 4), (44, 26, 4, 6), (36, 56, 4, 6), (4, 24, 3, 4)):
        for y in range(v, v + h_):
            for x in range(u, u + w_):
                if rnd.random() < 0.75:
                    im.putpixel((x, y), (88 + rnd.randint(0, 40), 12, 14, 255))
    im.save(f'{A}/textures/entity/doppel.png')
    print('ok')


if __name__ == '__main__':
    main()
