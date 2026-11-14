#!/usr/bin/env python3
"""Существа Глухомани 0.3 — детальные модели в духе ручной работы в Blockbench.
Пофейсовая развёртка (2 текселя на пиксель модели, текстуры 256×256), продуманные силуэты: длинные тонкие кости, сгорбленные
спины, рёбра и позвонки из мелких кубов, длинные пальцы из суставов, челюсть, которая открывается слишком широко.
Текстуры рисуются по граням: светотень сверху вниз, грязь, трещины, бледная кожа; светящиеся глаза/пасть — *_glowmask.png.
Пишет geo/, animations/, textures/entity/ для pole, listener, choir. Запуск: /tmp/claude-0/ttsenv/bin/python tools/gen_creatures.py
"""
import json, math, os, random
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
A = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'glukhoman')
D = 2  # текселей на пиксель модели
rnd = random.Random(31)


def w(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    json.dump(obj, open(path, 'w'), separators=(',', ':'))


def vnoise(x, y, s=1):
    xi, yi = math.floor(x), math.floor(y)
    fx, fy = x - xi, y - yi

    def h(a, b):
        n = (a * 374761393 + b * 668265263 + s * 2147483647) & 0xFFFFFFFF
        n = ((n ^ (n >> 13)) * 1274126177) & 0xFFFFFFFF
        return (n & 0xFFFF) / 65535
    fx, fy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
    a, b, c, d = h(xi, yi), h(xi + 1, yi), h(xi, yi + 1), h(xi + 1, yi + 1)
    return a + (b - a) * fx + (c - a) * fy + (a - b - c + d) * fx * fy


class Model:
    def __init__(self, name, tw=256, th=256):
        self.name, self.tw, self.th = name, tw, th
        self.bones, self.faces = [], []
        self.x = self.y = self.rowh = 0
        self.byname = {}

    def bone(self, name, parent, pivot, rot=None):
        b = {'name': name, 'pivot': [round(v, 3) for v in pivot], 'cubes': []}
        if parent:
            b['parent'] = parent
        if rot:
            b['rotation'] = [round(v, 2) for v in rot]
        self.bones.append(b)
        self.byname[name] = b
        return name

    def alloc(self, fw, fh):
        fw, fh = max(1, math.ceil(fw)), max(1, math.ceil(fh))
        if self.x + fw > self.tw:
            self.x, self.y, self.rowh = 0, self.y + self.rowh, 0
        r = (self.x, self.y, fw, fh)
        self.x += fw
        self.rowh = max(self.rowh, fh)
        return r

    def cube(self, bone, origin, size, mat, front=None, rot=None, pivot=None, glow=None, inflate=0):
        wx, hy, dz = size
        uv = {}
        dims = {'north': (wx, hy), 'south': (wx, hy), 'east': (dz, hy), 'west': (dz, hy), 'up': (wx, dz), 'down': (wx, dz)}
        for f, (a, b) in dims.items():
            x, y, fw, fh = self.alloc(a * D, b * D)
            uv[f] = {'uv': [x, y], 'uv_size': [fw, fh]}
            self.faces.append((x, y, fw, fh, mat, f, front if f == 'north' else None, glow if f == 'north' else None, origin[1], origin[1] + hy))
        c = {'origin': [round(v, 3) for v in origin], 'size': [round(v, 3) for v in size], 'uv': uv}
        if inflate:
            c['inflate'] = inflate
        if rot:
            c['rotation'] = rot
            c['pivot'] = pivot or [origin[0] + wx / 2, origin[1] + hy / 2, origin[2] + dz / 2]
        self.byname[bone]['cubes'].append(c)

    def seg(self, name, parent, a, b, t, mat, glow=None):
        """Кость-отрезок от точки a к точке b толщиной t (куб вдоль −Y, повёрнутый на направление)."""
        dx, dy, dz = b[0] - a[0], b[1] - a[1], b[2] - a[2]
        L = math.sqrt(dx * dx + dy * dy + dz * dz)
        nx, ny, nz = dx / L, dy / L, dz / L
        az = math.degrees(math.asin(max(-1, min(1, nx))))
        ax = math.degrees(math.atan2(-nz, -ny))
        self.bone(name, parent, a, [ax, 0, az])
        self.cube(name, [a[0] - t / 2, a[1] - L, a[2] - t / 2], [t, L, t], mat)
        return name

    def save(self, height, anims):
        assert self.y + self.rowh <= self.th, (self.name, self.y + self.rowh)
        for b in self.bones:
            if not b['cubes']:
                del b['cubes']
        w(f'{A}/geo/{self.name}.geo.json', {'format_version': '1.12.0', 'minecraft:geometry': [{
            'description': {'identifier': f'geometry.glukhoman.{self.name}', 'texture_width': self.tw, 'texture_height': self.th,
                            'visible_bounds_width': 5, 'visible_bounds_height': height, 'visible_bounds_offset': [0, height / 2, 0]},
            'bones': self.bones}]})
        w(f'{A}/animations/{self.name}.animation.json', {'format_version': '1.8.0', 'animations': {f'animation.{self.name}.{k}': v for k, v in anims.items()}})
        img = Image.new('RGBA', (self.tw, self.th), (0, 0, 0, 0))
        glow = Image.new('RGBA', (self.tw, self.th), (0, 0, 0, 0))
        px, gx = img.load(), glow.load()
        for (x0, y0, fw, fh, mat, side, front, gl, ylo, yhi) in self.faces:
            for yy in range(fh):
                for xx in range(fw):
                    # высота точки модели (для «грязи снизу» и света сверху)
                    hgt = yhi - (yy + 0.5) / D if side not in ('up', 'down') else (yhi if side == 'up' else ylo)
                    px[x0 + xx, y0 + yy] = MATS[mat](x0 + xx, y0 + yy, xx, yy, fw, fh, side, hgt)
            if front:
                FRONT[front](px, gx, x0, y0, fw, fh)
            if gl:
                for yy in range(fh):
                    for xx in range(fw):
                        gx[x0 + xx, y0 + yy] = gl
        img.save(f'{A}/textures/entity/{self.name}.png')
        if glow.getbbox():
            glow.save(f'{A}/textures/entity/{self.name}_glowmask.png')


# ======================= материалы =======================
def shade(c, k, a=255):
    return tuple(max(0, min(255, int(v * k))) for v in c) + (a,)


def material(base, *, crack=0.0, dirt=0.4, vein=0.0, sheen=0.0, scale=0.35, edge=0.8, top=1.15, bottom=0.7):
    def f(gx_, gy_, x, y, fw, fh, side, h):
        n = vnoise(gx_ * scale, gy_ * scale, 3) * 0.6 + vnoise(gx_ * scale * 3, gy_ * scale * 3, 4) * 0.4
        k = 0.82 + n * 0.32
        if side == 'up':
            k *= top
        elif side == 'down':
            k *= bottom
        elif side in ('east', 'west'):
            k *= 0.88
        if fw > 3 and fh > 3 and (x == 0 or y == 0 or x == fw - 1 or y == fh - 1):
            k *= edge                         # тень на рёбрах кубов («окклюзия»)
        if crack and vnoise(gx_ * 0.9, gy_ * 0.25, 7) > 1 - crack:
            k *= 0.55                         # продольные трещины
        if vein and abs(vnoise(gx_ * 0.6, gy_ * 0.6, 9) - 0.5) < vein:
            c = (base[0] * 0.85, base[1] * 0.7, base[2] * 0.95)
            return shade(c, k)
        if sheen and vnoise(gx_ * 0.5, gy_ * 0.5, 11) > 1 - sheen:
            k *= 1.35                         # влажный блеск
        g = max(0.0, min(1.0, (8 - h) / 8)) * dirt   # грязь у земли
        c = tuple(v * (1 - g) + d * g for v, d in zip(base, (52, 40, 30)))
        return shade(c, k)
    return f


def bloody(base_mat, low=6.0, high=None, amount=0.75):
    """Засохшая кровь: гуще к кончикам (ниже low) или выше high (у пасти), с влажными бликами."""
    def f(gx_, gy_, x, y, fw, fh, side, h):
        c = base_mat(gx_, gy_, x, y, fw, fh, side, h)
        k = 0.0
        if high is None:
            k = max(0.0, min(1.0, (low - h) / 4.0))
        else:
            k = max(0.0, min(1.0, (h - high) / 2.5))
        k *= amount * (0.6 + 0.4 * vnoise(gx_ * 0.7, gy_ * 0.7, 21))
        if vnoise(gx_ * 0.4, gy_ * 1.3, 23) > 0.8:   # потёки вниз
            k = max(k, 0.55 * amount)
        if k <= 0.05:
            return c
        blood = (92, 14, 16) if vnoise(gx_ * 2, gy_ * 2, 25) > 0.15 else (150, 40, 38)   # иногда влажный блик
        return tuple(int(a * (1 - k) + b * k) for a, b in zip(c[:3], blood)) + (255,)
    return f


MATS = {
    'bark': material((62, 58, 54), crack=0.14, dirt=0.4, sheen=0.05, scale=0.3),
    'bark_d': material((44, 41, 39), crack=0.15, dirt=0.4, sheen=0.04, scale=0.3),
    'bone': material((84, 78, 72), dirt=0.2, sheen=0.07, scale=0.5, edge=0.8),
    'skull': material((78, 72, 68), dirt=0.0, sheen=0.1, vein=0.03, scale=0.45, edge=0.75),
    'twig': material((34, 31, 29), crack=0.2, dirt=0.0),
    'skin': material((82, 78, 77), vein=0.04, dirt=0.4, sheen=0.06, scale=0.4),
    'skin_d': material((58, 54, 54), vein=0.04, dirt=0.4, sheen=0.05, scale=0.4),
    'ear': material((70, 56, 56), vein=0.06, dirt=0.0, sheen=0.05, scale=0.5, edge=0.7),
    'mouth': material((40, 12, 14), dirt=0.0, edge=0.9),
    'teeth': material((96, 88, 70), dirt=0.0, edge=1.0),
    'tar': material((40, 33, 44), sheen=0.08, dirt=0.3, scale=0.35),
    'tar2': material((58, 44, 60), sheen=0.06, dirt=0.3, scale=0.35),
    'mask': material((86, 80, 78), dirt=0.0, sheen=0.08, vein=0.03, scale=0.5, edge=0.8),
    'nail': material((60, 56, 50), dirt=0.0, edge=1.0),
}
MATS['finger_b'] = bloody(MATS['skin_d'], low=14.0)              # пальцы Жерди — в крови к кончикам
MATS['nail_b'] = bloody(MATS['nail'], low=14.0, amount=0.9)
MATS['teeth_b'] = bloody(MATS['teeth'], low=60.0, amount=0.55)   # зубы в разводах
MATS['jaw_b'] = bloody(MATS['skull'], low=53.0, amount=0.8)      # подбородок Жерди
MATS['ljaw_b'] = bloody(MATS['skin_d'], low=11.5, amount=0.8)    # пасть Слухача
MATS['lteeth_b'] = bloody(MATS['teeth'], low=12.0, amount=0.5)
MATS['lfinger_b'] = bloody(MATS['skin_d'], low=1.5, amount=0.6)


def put(px, x, y, c):
    try:
        px[x, y] = c
    except IndexError:
        pass


def front_pole(px, gx, x0, y0, w_, h_):
    """Лицо Жерди: гладкая, вытянутая, мокрая кожа без рта — только две тусклые точки глаз в глубоких тенях."""
    for y in range(h_):
        for x in range(w_):
            c = px[x0 + x, y0 + y]
            k = 0.8 + 0.25 * (1 - abs(x - w_ / 2) / (w_ / 2))
            if int(h_ * 0.2) <= y <= int(h_ * 0.42) and (abs(x - w_ // 4) <= 1 or abs(x - (w_ - 1 - w_ // 4)) <= 1):
                k *= 0.45
            put(px, x0 + x, y0 + y, shade(c[:3], k))
    for cx in (w_ // 4, w_ - 1 - w_ // 4):
        put(px, x0 + cx, y0 + int(h_ * 0.32), (150, 132, 96, 255))
        put(gx, x0 + cx, y0 + int(h_ * 0.32), (120, 104, 70, 255))


def front_listener(px, gx, x0, y0, w_, h_):
    """Морда Слухача: глаз нет — зашитые складки кожи."""
    for cx in (w_ // 4, w_ - 1 - w_ // 4):
        for x in range(cx - 1, cx + 2):
            put(px, x0 + x, y0 + int(h_ * 0.35), (110, 92, 92, 255))
        for k in (-1, 1):
            put(px, x0 + cx + k, y0 + int(h_ * 0.35) + 1, (90, 70, 72, 255))


def front_mask(px, gx, x0, y0, w_, h_):
    """Лицо, вдавленное в массу Хора: тёмная мокрая кожа, впадины глаз, сжатый шов рта; у некоторых — тусклая точка."""
    lit = rnd.random() < 0.4
    for cx in (w_ // 3 - 1, w_ - w_ // 3):
        for y in range(int(h_ * 0.3), int(h_ * 0.42)):
            put(px, x0 + cx, y0 + y, (16, 12, 12, 255))
        if lit:
            put(px, x0 + cx, y0 + int(h_ * 0.36), (140, 110, 70, 255))
            put(gx, x0 + cx, y0 + int(h_ * 0.36), (110, 86, 50, 255))
    for x in range(w_ // 3, w_ - w_ // 3):
        put(px, x0 + x, y0 + int(h_ * 0.72), (22, 10, 12, 255))


FRONT = {'pole': front_pole, 'listener': front_listener, 'mask': front_mask}


# ======================= анимации =======================
# В GeckoLib +X наклоняет кость «назад»; в позах ниже «+» задуман как «вперёд/вниз» для корпуса — переворачиваем.
FLIP = {'spine', 'chest', 'neck1', 'neck2', 'head', 'pelvis', 'body', 'neck'}


def anim(fn, length, loop=True, fps=12, jerk=False):
    frames = {}
    raw = fn
    fn = lambda t: {b: {k: ([-v[0], v[1], v[2]] if (k == 'rotation' and b in FLIP) else v) for k, v in ch.items()} for b, ch in raw(t).items()}
    n = max(1, int(length * fps))
    prev = None
    for i in range(n + 1):
        t = min(length, i / fps)
        pose = fn(t)
        key = f'{t:.3f}'.rstrip('0').rstrip('.') or '0'
        hold = f'{max(0, t - 0.03):.3f}'.rstrip('0').rstrip('.')
        for b, ch in pose.items():
            for kind, val in ch.items():
                d = frames.setdefault(b, {}).setdefault(kind, {})
                if jerk and prev and i > 0 and b in prev and kind in prev[b]:
                    d[hold] = [round(v, 2) for v in prev[b][kind]]
                d[key] = [round(v, 2) for v in val]
        prev = pose
    return {'animation_length': length, 'loop': True if loop else 'hold_on_last_frame', 'bones': frames}


def wob(t, f, a, ph=0.0):
    return math.sin(t * f * 2 * math.pi + ph) * a


def merge(*ds):
    out = {}
    for d in ds:
        for b, ch in d.items():
            out.setdefault(b, {}).update(ch)
    return out


R = random.Random(7)


def twitchy(bones, t, amp, rate=8):
    return {b: {'rotation': [R.uniform(-amp, amp) * (int(t * rate + i) % 3 == 0), R.uniform(-amp, amp) * (int(t * rate * 0.7 + i) % 4 == 0),
                             R.uniform(-amp, amp) * (int(t * rate * 0.6 + i) % 3 == 0)]} for i, b in enumerate(bones)}


# ======================= Жердь =======================
def pole():
    m = Model('pole', 256, 256)
    m.bone('root', None, [0, 0, 0])
    m.bone('pelvis', 'root', [0, 30, 0]); m.cube('pelvis', [-2.5, 28, -1.4], [5, 3.5, 2.8], 'bark_d')
    m.bone('spine', 'pelvis', [0, 31.5, 0.4], [-8, 0, 0]); m.cube('spine', [-1.4, 31.5, -0.6], [2.8, 7, 2], 'bark')
    for i in range(4):
        m.cube('spine', [-0.55, 32 + i * 1.7, 1.2], [1.1, 1.0, 1.0], 'bone')
    m.bone('chest', 'spine', [0, 38.5, 0.6], [-18, 0, 0])
    m.cube('chest', [-2.6, 38.5, -0.6], [5.2, 8.5, 2.6], 'bark')
    m.cube('chest', [-0.5, 39.5, -1.3], [1.0, 6.5, 0.8], 'bone')                   # грудина
    for i in range(5):                                                             # рёбра
        y = 39.6 + i * 1.45
        wdt = 2.6 - abs(i - 1.5) * 0.25
        for s in (-1, 1):
            m.cube('chest', [0.7 if s > 0 else -0.7 - wdt, y, -1.4], [wdt, 0.55, 0.7], 'bone')
            m.cube('chest', [2.6 if s > 0 else -3.3, y, -1.2], [0.7, 0.55, 2.3], 'bone')
        m.cube('chest', [-0.5, y, 2.0], [1.0, 1.0, 0.9], 'bone')                   # позвонки сзади
    m.cube('chest', [-4.6, 46.2, -0.5], [9.2, 1.2, 1.6], 'bark_d')                # ключицы/плечи
    m.bone('neck1', 'chest', [0, 47, 0.3], [-30, 0, 0]); m.cube('neck1', [-0.8, 47, -0.5], [1.6, 5, 1.6], 'bark')
    m.bone('neck2', 'neck1', [0, 52, 0.3], [6, 0, 0]); m.cube('neck2', [-0.7, 52, -0.4], [1.4, 4, 1.4], 'bark')
    m.bone('head', 'neck2', [0, 56, 0.3], [24, 0, 0])
    m.cube('head', [-2.3, 57, -1.2], [4.6, 5, 4.8], 'skull')                       # череп
    m.cube('head', [-1.9, 54.2, -2.6], [3.8, 6.8, 1.5], 'skull', front='pole')    # вытянутое лицо
    m.cube('head', [-2.3, 59.4, -2.9], [4.6, 0.9, 1.0], 'skull')                   # надбровье
    for s in (-1, 1):
        m.cube('head', [1.4 if s > 0 else -2.4, 56.4, -2.4], [1.0, 1.6, 1.4], 'skull')   # скулы
    for (x, z, rz, rx, ln) in ((-1.8, 0.5, 25, -10, 6), (1.6, 0.0, -30, 5, 7), (0.2, 1.4, 5, -25, 5), (-0.8, -0.4, 40, 0, 4), (1.0, 1.0, -12, 15, 4.5)):
        m.cube('head', [x - 0.25, 61.6, z - 0.25], [0.5, ln, 0.5], 'twig', rot=[rx, 0, rz], pivot=[x, 61.6, z])  # сучья
    m.bone('jaw', 'head', [0, 55.4, -1.6])
    m.cube('jaw', [-1.6, 50.6, -2.5], [3.2, 4.8, 1.3], 'jaw_b')
    for s, n in ((1, 'left'), (-1, 'right')):
        sx = 4.4 * s
        m.seg(f'{n}_arm', 'chest', [sx, 46.6, 0.3], [sx + 0.9 * s, 33.0, -0.6], 1.5, 'bark')
        m.cube(f'{n}_arm', [sx - 0.95, 32.6, -0.45], [1.9, 1.3, 1.6], 'bone')       # локоть
        m.seg(f'{n}_forearm', f'{n}_arm', [sx, 33.0, 0.3], [sx, 19.5, -0.3], 1.3, 'bark')
        m.bone(f'{n}_hand', f'{n}_forearm', [sx, 19.6, 0.3])
        m.cube(f'{n}_hand', [sx - 1.0, 16.8, -0.4], [2.0, 2.8, 1.1], 'skin_d')
        for i, fx in enumerate((-0.75, -0.25, 0.25, 0.75)):
            ln = 4.6 - abs(fx) * 1.2
            m.bone(f'{n}_f{i}', f'{n}_hand', [sx + fx, 16.9, 0.15], [-6, 0, fx * 6 * s])
            m.cube(f'{n}_f{i}', [sx + fx - 0.2, 16.9 - ln, -0.05], [0.4, ln, 0.4], 'skin_d')
            m.bone(f'{n}_f{i}b', f'{n}_f{i}', [sx + fx, 16.9 - ln, 0.15], [-14, 0, 0])
            m.cube(f'{n}_f{i}b', [sx + fx - 0.18, 16.9 - ln - 4.0, -0.03], [0.36, 4.0, 0.36], 'finger_b')
            m.cube(f'{n}_f{i}b', [sx + fx - 0.2, 16.9 - ln - 4.6, -0.05], [0.4, 0.7, 0.4], 'nail_b')
        lx = 1.5 * s
        m.seg(f'{n}_leg', 'pelvis', [lx, 28.6, 0.2], [lx + 0.3 * s, 15.2, -1.2], 1.7, 'bark_d')
        m.cube(f'{n}_leg', [lx - 0.95, 14.6, -1.0], [1.9, 1.5, 1.9], 'bone')
        m.seg(f'{n}_shin', f'{n}_leg', [lx, 15.2, 0.2], [lx, 1.6, 1.0], 1.45, 'bark_d')
        m.bone(f'{n}_foot', f'{n}_shin', [lx, 1.6, 0.2])
        m.cube(f'{n}_foot', [lx - 0.85, 0, -3.2], [1.7, 1.0, 4.0], 'bark_d')
        for i in range(3):
            m.cube(f'{n}_foot', [lx - 0.75 + i * 0.6, 0, -4.9], [0.35, 0.55, 1.8], 'nail')
    A_ = {}

    def legs(t, f, a):
        return {'left_leg': {'rotation': [wob(t, f, a), 0, 0]}, 'right_leg': {'rotation': [-wob(t, f, a), 0, 0]},
                'left_shin': {'rotation': [max(0, -wob(t, f, a * 1.3, 1.3)), 0, 0]}, 'right_shin': {'rotation': [max(0, wob(t, f, a * 1.3, 1.3)), 0, 0]}}

    fingers = lambda t, a: {f'{s}_f{i}': {'rotation': [wob(t, 0.6, a, i), 0, 0]} for s in ('left', 'right') for i in range(4)}
    A_['idle'] = anim(lambda t: merge(fingers(t, 6), {'head': {'rotation': [0, wob(t, 0.08, 10), 14 + wob(t, 0.05, 8)]},
                                                     'chest': {'rotation': [wob(t, 0.2, 1.5), 0, wob(t, 0.12, 2)]}, 'jaw': {'rotation': [3 + wob(t, 0.3, 3), 0, 0]}}), 8)
    A_['still'] = anim(lambda t: {'head': {'rotation': [8, 0, 34]}, 'neck2': {'rotation': [0, 0, 10]}, 'jaw': {'rotation': [6, 0, 0]}}, 1)
    A_['walk'] = anim(lambda t: merge(legs(t, 0.55, 26), fingers(t, 10), {
        'left_arm': {'rotation': [-wob(t, 0.55, 12, 0.8), 0, 0]}, 'right_arm': {'rotation': [wob(t, 0.55, 12, 0.8), 0, 0]},
        'head': {'rotation': [4, wob(t, 0.27, 8), 18 + wob(t, 1.1, 5)]}, 'spine': {'rotation': [4, 0, wob(t, 0.55, 3)]},
        'pelvis': {'position': [0, abs(wob(t, 0.55, 0.6)), 0]}}), 1.818)
    A_['run'] = anim(lambda t: merge(legs(t, 2.0, 58), {
        'spine': {'rotation': [28, 0, wob(t, 2, 5)]}, 'chest': {'rotation': [12, 0, 0]}, 'neck1': {'rotation': [-20, 0, 0]},
        'left_arm': {'rotation': [50 + wob(t, 2, 18), 0, -10]}, 'right_arm': {'rotation': [50 - wob(t, 2, 18), 0, 10]},
        'left_forearm': {'rotation': [30, 0, 0]}, 'right_forearm': {'rotation': [30, 0, 0]},
        'head': {'rotation': [-30, wob(t, 4, 7), wob(t, 3, 12)]}, 'jaw': {'rotation': [24, 0, 0]}, 'pelvis': {'position': [0, abs(wob(t, 2, 1.5)), 0]}}), 0.5)
    A_['peek'] = anim(lambda t: merge(fingers(t, 4), {'spine': {'rotation': [0, 0, 28]}, 'chest': {'rotation': [0, 0, 14]},
                                                     'head': {'rotation': [4, -14, 58 + wob(t, 0.2, 4)]}, 'left_arm': {'rotation': [-72, 0, 26]},
                                                     'left_forearm': {'rotation': [-28, 0, 0]}}), 5)
    A_['tilt'] = anim(lambda t: {'head': {'rotation': [0, 0, min(100, t * 55) if t < 1.8 else 0]}, 'neck2': {'rotation': [0, 0, min(24, t * 14) if t < 1.8 else 0]},
                                 'jaw': {'rotation': [min(30, t * 20) if t < 1.8 else 0, 0, 0]}}, 2.2, jerk=True, fps=7)
    A_['turn180'] = anim(lambda t: {'head': {'rotation': [0, min(180, int(t * 6) * 36), 8]}}, 1.2, loop=False, jerk=True, fps=6)
    A_['twitch'] = anim(lambda t: twitchy(['head', 'left_arm', 'right_forearm', 'neck2', 'left_hand', 'right_f1', 'jaw'], t, 28), 2, jerk=True, fps=9)
    A_['attack'] = anim(lambda t: merge(fingers(t, 25), {'left_arm': {'rotation': [-150 + t * 250 if t < 0.6 else 0, 0, -18]},
                                                        'right_arm': {'rotation': [-150 + t * 250 if t < 0.6 else 0, 0, 18]},
                                                        'jaw': {'rotation': [55 if t < 0.5 else 8, 0, 0]}, 'spine': {'rotation': [22 * min(1, t * 3), 0, 0]},
                                                        'head': {'rotation': [-24, 0, 0]}}), 0.8, loop=False)
    A_['spawn'] = anim(lambda t: {'spine': {'rotation': [max(0, 95 - t * 70), 0, 0]}, 'left_leg': {'rotation': [-max(0, 80 - t * 60), 0, 0]},
                                  'right_leg': {'rotation': [-max(0, 80 - t * 60), 0, 0]}, 'left_shin': {'rotation': [max(0, 120 - t * 90), 0, 0]},
                                  'right_shin': {'rotation': [max(0, 120 - t * 90), 0, 0]}, 'root': {'position': [0, min(0, -26 + t * 20), 0]},
                                  'head': {'rotation': [0, 0, max(0, 90 - t * 70)]}}, 1.4, loop=False, jerk=True, fps=8)
    A_['vanish'] = anim(lambda t: {'spine': {'rotation': [min(100, t * 160), 0, 0]}, 'root': {'position': [0, -min(40, t * 60), 0]},
                                   'head': {'rotation': [0, 0, t * 200]}}, 0.75, loop=False, jerk=True, fps=10)
    A_['knock'] = anim(lambda t: {'right_arm': {'rotation': [-80 + (12 if (t * 3) % 1 < 0.25 else 0), 0, 6]}, 'right_forearm': {'rotation': [-40, 0, 0]},
                                  'head': {'rotation': [26, 0, 22]}, 'spine': {'rotation': [10, 0, 0]}}, 2)
    A_['jumpscare'] = anim(lambda t: merge(fingers(t * 3, 30), {
        'head': {'rotation': [-8 + wob(t, 9, 7), wob(t, 11, 10), wob(t, 7, 14)], 'position': [0, 0, -min(5, t * 14)]},
        'jaw': {'rotation': [min(80, t * 260), 0, 0]}, 'left_arm': {'rotation': [-10, 0, -6]}, 'right_arm': {'rotation': [-10, 0, 6]},
        'neck1': {'rotation': [-30, 0, 0], 'position': [0, 0, -min(3, t * 8)]}}), 1.2, loop=False, fps=20)
    m.save(5, A_)


# ======================= Слухач =======================
def listener():
    m = Model('listener', 256, 256)
    m.bone('root', None, [0, 0, 0])
    m.bone('body', 'root', [0, 12, 0])
    m.cube('body', [-3.2, 10.2, -8.5], [6.4, 4.6, 7.5], 'skin')                    # грудная клетка
    m.cube('body', [-2.4, 10.6, -1.2], [4.8, 3.6, 6.4], 'skin_d')                  # впалый живот
    for i in range(9):                                                             # хребет
        m.cube('body', [-0.55, 14.6 - (0.3 if i > 5 else 0), -8 + i * 1.5], [1.1, 1.0, 1.0], 'bone')
    for i in range(5):                                                             # рёбра по бокам
        for s in (-1, 1):
            m.cube('body', [3.2 if s > 0 else -3.75, 10.6, -7.8 + i * 1.4], [0.55, 3.8, 0.7], 'bone')
    m.bone('pelvis', 'body', [0, 12, 5]); m.cube('pelvis', [-2.8, 10.2, 5], [5.6, 3.8, 3.4], 'skin_d')
    m.bone('neck', 'body', [0, 13, -8.4], [-14, 0, 0]); m.cube('neck', [-1.1, 11.9, -12.2], [2.2, 2.2, 4.0], 'skin')
    for i in range(3):
        m.cube('neck', [-0.45, 14.0, -11.8 + i * 1.3], [0.9, 0.8, 0.8], 'bone')
    m.bone('head', 'neck', [0, 13, -12])
    m.cube('head', [-2.3, 11.2, -17.2], [4.6, 4.8, 5.4], 'skin')                    # череп
    m.cube('head', [-1.6, 11.0, -20.3], [3.2, 2.8, 3.2], 'skin', front='listener')  # морда
    for i in range(5):
        m.cube('head', [-1.4 + i * 0.6, 10.4, -20.1], [0.35, 0.8, 0.35], 'teeth')
    m.bone('jaw', 'head', [0, 11.4, -13.4])
    m.cube('jaw', [-1.5, 9.6, -20.6], [3.0, 1.4, 7.0], 'ljaw_b')
    m.cube('jaw', [-1.2, 10.9, -19.8], [2.4, 0.4, 6.0], 'mouth')
    for i in range(5):
        m.cube('jaw', [-1.3 + i * 0.6, 11.0, -20.3], [0.35, 0.7, 0.35], 'lteeth_b')
    for s, n in ((1, 'left'), (-1, 'right')):
        m.bone(f'{n}_ear', 'head', [2.2 * s, 15.6, -15], [0, 0, -38 * s])
        m.cube(f'{n}_ear', [2.2 * s - 0.15, 15.0, -19.5], [0.3, 8, 9], 'ear')     # перепонка
        for k in range(3):
            m.cube(f'{n}_ear', [2.2 * s - 0.3, 15.2, -18.5 + k * 3], [0.6, 7.6 - k * 1.5, 0.6], 'skin_d')   # спицы
        # передние лапы-руки: плечо вверх и в стороны, локоть высоко, длинные пальцы на земле
        sh = [3.0 * s, 12.5, -6.5]
        el = [7.5 * s, 14.5, -9.5]
        wr = [8.0 * s, 1.2, -12.0]
        m.seg(f'{n}_arm', 'body', sh, el, 1.5, 'skin')
        m.seg(f'{n}_forearm', f'{n}_arm', el, wr, 1.25, 'skin_d')
        m.bone(f'{n}_hand', f'{n}_forearm', wr)
        m.cube(f'{n}_hand', [wr[0] - 1.0, 0.2, wr[2] - 1.6], [2.0, 1.0, 2.2], 'skin_d')
        for i in range(4):
            fx = wr[0] + (-0.8 + i * 0.55) * s
            m.cube(f'{n}_hand', [fx - 0.2, 0.0, wr[2] - 5.5 + abs(i - 1.5) * 0.6], [0.4, 0.45, 4.0], 'lfinger_b')
            m.cube(f'{n}_hand', [fx - 0.2, 0.0, wr[2] - 6.1 + abs(i - 1.5) * 0.6], [0.4, 0.45, 0.6], 'nail')
        # задние лапы: колени торчат выше спины, как у паука
        hp = [2.6 * s, 11.5, 7.2]
        kn = [6.0 * s, 17.5, 9.5]
        an = [6.8 * s, 0.8, 11.5]
        m.seg(f'{n}_leg', 'pelvis', hp, kn, 1.6, 'skin')
        m.seg(f'{n}_shin', f'{n}_leg', kn, an, 1.3, 'skin_d')
        m.bone(f'{n}_foot', f'{n}_shin', an)
        m.cube(f'{n}_foot', [an[0] - 0.8, 0, an[2] - 3.0], [1.6, 0.8, 3.6], 'skin_d')
    A_ = {}

    def crawl(t, f, a):
        return {'left_arm': {'rotation': [wob(t, f, a), 0, 0]}, 'right_leg': {'rotation': [wob(t, f, a), 0, 0]},
                'right_arm': {'rotation': [-wob(t, f, a), 0, 0]}, 'left_leg': {'rotation': [-wob(t, f, a), 0, 0]},
                'left_forearm': {'rotation': [max(0, wob(t, f, a * 0.8, 1)), 0, 0]}, 'right_forearm': {'rotation': [max(0, -wob(t, f, a * 0.8, 1)), 0, 0]},
                'body': {'rotation': [0, wob(t, f, 6), wob(t, f, 5)], 'position': [0, abs(wob(t, f, 0.8)), 0]}}
    A_['idle'] = anim(lambda t: {'body': {'position': [0, wob(t, 0.35, 0.35), 0]}, 'left_ear': {'rotation': [0, 0, wob(t, 0.5, 6)]},
                                 'right_ear': {'rotation': [0, 0, -wob(t, 0.5, 6)]}, 'head': {'rotation': [wob(t, 0.2, 6), wob(t, 0.13, 14), 0]},
                                 'jaw': {'rotation': [4 + wob(t, 0.4, 3), 0, 0]}}, 6)
    A_['crawl'] = anim(lambda t: merge(crawl(t, 1.1, 24), {'head': {'rotation': [0, wob(t, 0.55, 10), wob(t, 1.1, 7)]}}), 0.909)
    A_['sprint'] = anim(lambda t: merge(crawl(t, 2.6, 42), {'head': {'rotation': [-12, 0, wob(t, 2.6, 10)]}, 'jaw': {'rotation': [30, 0, 0]},
                                                           'left_ear': {'rotation': [0, 0, -35]}, 'right_ear': {'rotation': [0, 0, 35]}}), 0.385)
    A_['listen'] = anim(lambda t: {'head': {'rotation': [-8, [0, 35, -40, 70, -10, 0][int(t * 3) % 6], [0, 25, -30, 45, 0, 15][int(t * 3) % 6]]},
                                   'left_ear': {'rotation': [0, 0, -30]}, 'right_ear': {'rotation': [0, 0, 30]}, 'body': {'rotation': [-6, 0, 0]},
                                   'jaw': {'rotation': [12, 0, 0]}}, 2, jerk=True, fps=6)
    A_['attack'] = anim(lambda t: {'body': {'rotation': [-28 if t < 0.4 else 8, 0, 0], 'position': [0, 3 if t < 0.4 else 0, -5 * min(1, t * 3)]},
                                   'left_arm': {'rotation': [-70 if t < 0.4 else 15, 0, 0]}, 'right_arm': {'rotation': [-70 if t < 0.4 else 15, 0, 0]},
                                   'jaw': {'rotation': [65, 0, 0]}}, 0.7, loop=False)
    A_['turn180'] = anim(lambda t: {'head': {'rotation': [0, min(180, int(t * 6) * 36), 0]}, 'jaw': {'rotation': [20, 0, 0]}}, 1.2, loop=False, jerk=True, fps=6)
    A_['twitch'] = anim(lambda t: twitchy(['head', 'left_forearm', 'right_leg', 'jaw', 'left_ear'], t, 32), 1.5, jerk=True, fps=10)
    A_['spawn'] = anim(lambda t: {'root': {'position': [0, min(0, -14 + t * 12), 0]}, 'head': {'rotation': [55 - t * 45, 0, 0]}}, 1.2, loop=False, jerk=True, fps=8)
    A_['vanish'] = anim(lambda t: {'root': {'position': [0, -t * 20, 0]}}, 0.75, loop=False)
    A_['jumpscare'] = anim(lambda t: {'body': {'rotation': [-14, 0, 0], 'position': [0, 3, -min(7, t * 18)]}, 'jaw': {'rotation': [min(85, t * 260), 0, 0]},
                                      'head': {'rotation': [-14 + wob(t, 10, 8), wob(t, 13, 12), wob(t, 8, 8)]},
                                      'left_ear': {'rotation': [0, 0, -45]}, 'right_ear': {'rotation': [0, 0, 45]},
                                      'left_arm': {'rotation': [-70, 0, -20]}, 'right_arm': {'rotation': [-70, 0, 20]}}, 1.2, loop=False, fps=20)
    m.save(3, A_)


# ======================= Хор =======================
FACES = [(-5.5, 35, -7.0), (2.5, 38, -6.6), (-1.0, 28, -7.6), (5.5, 29, -6.8), (-7.0, 22, -6.4), (2.8, 21, -7.2), (-2.0, 45.5, -4.6), (7.5, 41, -4.5)]


def choir():
    m = Model('choir', 256, 256)
    m.bone('root', None, [0, 0, 0])
    m.bone('mass', 'root', [0, 16, 0])
    for (x, y, z, sx, sy, sz, r) in ((-7, 0, -5, 7, 15, 10, 4), (0, 0, -6, 8, 17, 11, -3), (-3, 0, 0, 9, 13, 8, 6), (3, 0, 1, 6, 12, 7, -6)):
        m.cube('mass', [x, y, z], [sx, sy, sz], 'tar2', rot=[0, 0, r])
    m.bone('body', 'mass', [0, 16, 0], [8, 0, 0])
    m.cube('body', [-9, 16, -6.5], [18, 22, 13], 'tar')
    m.cube('body', [-6.5, 38, -5], [13, 7, 10], 'tar')
    m.cube('body', [-4, 45, -3.5], [8, 4, 7], 'tar2')
    for i in range(6):                                                             # рёбра-обручи на груди
        y = 24 + i * 2.4
        m.cube('body', [-8.2, y, -7.0], [16.4, 0.7, 0.8], 'bone')
    for i in range(7):                                                             # шипы-позвонки по спине
        m.cube('body', [-0.6 + (i % 2) * 0.4 - 0.2, 20 + i * 4, 6.2], [1.2, 1.6, 2.4 + (i % 3)], 'bone', rot=[-30, 0, 0])
    for i, (x, y, z) in enumerate(FACES):
        m.bone(f'face{i}', 'body', [x, y, z])
        m.cube(f'face{i}', [x - 2.4, y - 2.0, z - 1.6], [4.8, 5.4, 1.8], 'mask', front='mask')
        m.bone(f'mouth{i}', f'face{i}', [x, y - 1.0, z - 0.6])
        m.cube(f'mouth{i}', [x - 1.8, y - 3.6, z - 1.7], [3.6, 1.6, 1.5], 'mask')
        m.cube(f'mouth{i}', [x - 1.4, y - 2.2, z - 1.5], [2.8, 0.5, 1.0], 'mouth')
    for s, n in ((1, 'left'), (-1, 'right')):
        sh = [9.5 * s, 40, 0]
        el = [17 * s, 25, -3]
        wr = [16 * s, 8, -6]
        m.seg(f'{n}_arm', 'body', sh, el, 2.6, 'tar2')
        m.seg(f'{n}_forearm', f'{n}_arm', el, wr, 2.2, 'tar2')
        m.bone(f'{n}_hand', f'{n}_forearm', wr)
        m.cube(f'{n}_hand', [wr[0] - 1.6, wr[1] - 3, wr[2] - 1.6], [3.2, 3.2, 3.2], 'mask')
        for i in range(4):
            fx = wr[0] - 1.2 + i * 0.8
            m.cube(f'{n}_hand', [fx - 0.25, wr[1] - 9 + abs(i - 1.5), wr[2] - 0.6], [0.5, 6.5 - abs(i - 1.5), 0.5], 'mask')
        for k, (y, z) in enumerate(((30, 2), (22, -1))):                          # тонкие лишние руки
            a0 = [8.5 * s, y, z]
            a1 = [15 * s, y - 4, z - 4]
            a2 = [16 * s, y - 12, z - 7]
            m.seg(f'{n}_xa{k}', 'body', a0, a1, 0.9, 'mask')
            m.seg(f'{n}_xb{k}', f'{n}_xa{k}', a1, a2, 0.75, 'mask')
    for i in range(5):
        a = i * 2 * math.pi / 5 + 0.4
        tx, tz = math.cos(a) * 7, math.sin(a) * 5
        m.seg(f'tendril{i}', 'mass', [tx, 4, tz], [tx * 1.5, 0.3, tz * 1.5], 1.4, 'tar2')
    A_ = {}

    def faces(t, open_=0.0, speed=1.0):
        d = {}
        for i in range(len(FACES)):
            d[f'face{i}'] = {'rotation': [wob(t, 0.3 * speed, 9, i), wob(t, 0.2 * speed, 22, i * 2), wob(t, 0.25 * speed, 16, i)]}
            d[f'mouth{i}'] = {'rotation': [max(0, open_ * 55 + wob(t, 1.1 * speed, 14, i)), 0, 0]}
        return d

    def extra(t, a):
        return {f'{s}_x{p}{k}': {'rotation': [wob(t, 0.7, a, k + (s == 'left')), 0, wob(t, 0.5, a, k * 2)]} for s in ('left', 'right') for k in range(2) for p in 'ab'}
    A_['idle'] = anim(lambda t: merge(faces(t, 0.1), extra(t, 10), {'body': {'rotation': [wob(t, 0.25, 3), 0, wob(t, 0.18, 3)]},
                                                                    'left_arm': {'rotation': [wob(t, 0.2, 5), 0, 0]}, 'right_arm': {'rotation': [wob(t, 0.2, 5, 1), 0, 0]}}), 5)
    A_['walk'] = anim(lambda t: merge(faces(t, 0.2, 2), extra(t, 18), {'body': {'rotation': [6, wob(t, 0.6, 8), wob(t, 0.6, 6)]},
                                                                       'mass': {'position': [0, abs(wob(t, 0.6, 1.5)), 0]},
                                                                       'left_arm': {'rotation': [wob(t, 0.6, 24), 0, 0]}, 'right_arm': {'rotation': [-wob(t, 0.6, 24), 0, 0]},
                                                                       **{f'tendril{i}': {'rotation': [wob(t, 1.2, 22, i), 0, 0]} for i in range(5)}}), 1.667)
    A_['run'] = anim(lambda t: merge(faces(t, 0.6, 4), extra(t, 30), {'body': {'rotation': [22, wob(t, 1.5, 10), wob(t, 1.5, 8)]},
                                                                      'mass': {'position': [0, abs(wob(t, 1.5, 3)), 0]},
                                                                      'left_arm': {'rotation': [-55 + wob(t, 1.5, 40), 0, 0]}, 'right_arm': {'rotation': [-55 - wob(t, 1.5, 40), 0, 0]}}), 0.667)
    A_['attack'] = anim(lambda t: merge(faces(t, 1), {'left_arm': {'rotation': [-165 + t * 300 if t < 0.6 else 10, 0, 0]},
                                                      'right_arm': {'rotation': [-165 + t * 300 if t < 0.6 else 10, 0, 0]}, 'body': {'rotation': [-10 + t * 40, 0, 0]}}), 0.8, loop=False)
    A_['stunned'] = anim(lambda t: merge(faces(t, 1, 6), extra(t, 40), {'body': {'rotation': [-22 + wob(t, 6, 6), wob(t, 7, 6), 0]},
                                                                        'left_arm': {'rotation': [-100, 0, -30]}, 'right_arm': {'rotation': [-100, 0, 30]},
                                                                        'left_forearm': {'rotation': [-55, 0, 0]}, 'right_forearm': {'rotation': [-55, 0, 0]}}), 1, jerk=True, fps=10)
    A_['roar'] = anim(lambda t: merge(faces(t, 1, 3), extra(t, 35), {'body': {'rotation': [-18, 0, wob(t, 4, 4)]},
                                                                     'left_arm': {'rotation': [-40, 0, -45]}, 'right_arm': {'rotation': [-40, 0, 45]}}), 2)
    A_['spawn'] = anim(lambda t: merge(faces(t, 0.8, 3), {'root': {'position': [0, min(0, -50 + t * 25), 0]}, 'body': {'rotation': [max(0, 40 - t * 20), 0, 0]}}), 2, loop=False)
    A_['vanish'] = anim(lambda t: merge(faces(t, 1, 6), {'root': {'position': [0, -t * 30, 0]}, 'body': {'rotation': [t * 30, 0, wob(t, 5, 8)]}}), 1.5, loop=False)
    A_['jumpscare'] = anim(lambda t: merge(faces(t, 1, 8), extra(t, 45), {'body': {'rotation': [-24, 0, 0], 'position': [0, 0, -min(10, t * 25)]},
                                                                          'left_arm': {'rotation': [-125, 0, -60]}, 'right_arm': {'rotation': [-125, 0, 60]}}), 1.2, loop=False, fps=20)
    m.save(5, A_)


if __name__ == '__main__':
    import sys
    which = sys.argv[1:] or ['pole', 'listener', 'choir']
    for n in which:
        {'pole': pole, 'listener': listener, 'choir': choir}[n]()
        print('ok', n)
