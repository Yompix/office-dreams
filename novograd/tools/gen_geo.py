#!/usr/bin/env python3
"""Модель жителя для GeckoLib (формат Bedrock geo) и все анимации.
Модель повторяет развёртку скина игрока 64×64, поэтому подходят все скины, но руки и ноги
разрезаны на две части (локти и колени гнутся), корпус — на таз и грудь.
Запуск из папки novograd: python3 tools/gen_geo.py
"""
import json, math, os

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'assets', 'novograd')


def faces(u, v, w, h, d, y0=0, y1=None, top=True, bottom=True):
    """UV граней для части бокса (u,v,w,h,d) — строки y0..y1 боковин (сверху вниз)."""
    if y1 is None:
        y1 = h
    sh = y1 - y0
    f = {
        'east': {'uv': [u, v + d + y0], 'uv_size': [d, sh]},
        'north': {'uv': [u + d, v + d + y0], 'uv_size': [w, sh]},
        'west': {'uv': [u + d + w, v + d + y0], 'uv_size': [d, sh]},
        'south': {'uv': [u + 2 * d + w, v + d + y0], 'uv_size': [w, sh]},
    }
    if top:
        f['up'] = {'uv': [u + d, v], 'uv_size': [w, d]}
    if bottom:
        f['down'] = {'uv': [u + d + w, v], 'uv_size': [w, d]}
    return f


def cube(origin, size, uv, inflate=0):
    c = {'origin': origin, 'size': size, 'uv': uv}
    if inflate:
        c['inflate'] = inflate
    return c


def model(slim):
    aw = 3 if slim else 4
    bones = []

    def bone(name, parent, pivot, cubes=None):
        b = {'name': name, 'pivot': pivot}
        if parent:
            b['parent'] = parent
        if cubes:
            b['cubes'] = cubes
        bones.append(b)

    bone('root', None, [0, 0, 0])
    bone('waist', 'root', [0, 12, 0], [
        cube([-4, 12, -2], [8, 6, 4], faces(16, 16, 8, 12, 4, 6, 12, top=False)),
        cube([-4, 12, -2], [8, 6, 4], faces(16, 32, 8, 12, 4, 6, 12, top=False), 0.25)])
    bone('chest', 'waist', [0, 18, 0], [
        cube([-4, 18, -2], [8, 6, 4], faces(16, 16, 8, 12, 4, 0, 6, bottom=False)),
        cube([-4, 18, -2], [8, 6, 4], faces(16, 32, 8, 12, 4, 0, 6, bottom=False), 0.25)])
    bone('neck', 'chest', [0, 24, 0])  # сюда — куда смотрит житель (код), анимации трогают только head
    bone('head', 'neck', [0, 24, 0], [
        cube([-4, 24, -4], [8, 8, 8], faces(0, 0, 8, 8, 8)),
        cube([-4, 24, -4], [8, 8, 8], faces(32, 0, 8, 8, 8), 0.5)])
    # руки: правая (+x, скин 40,16 / рукав 40,32), левая (−x, 32,48 / 48,48)
    for side, sx, (u, v), (su, sv) in (('right', 1, (40, 16), (40, 32)), ('left', -1, (32, 48), (48, 48))):
        x0 = 4 if sx > 0 else -4 - aw
        px = 5 if sx > 0 else -5
        bone(f'{side}_arm', 'chest', [px, 22, 0], [
            cube([x0, 18, -2], [aw, 6, 4], faces(u, v, aw, 12, 4, 0, 6, bottom=False)),
            cube([x0, 18, -2], [aw, 6, 4], faces(su, sv, aw, 12, 4, 0, 6, bottom=False), 0.25)])
        bone(f'{side}_forearm', f'{side}_arm', [px, 18, 0], [
            cube([x0, 12, -2], [aw, 6, 4], faces(u, v, aw, 12, 4, 6, 12, top=False)),
            cube([x0, 12, -2], [aw, 6, 4], faces(su, sv, aw, 12, 4, 6, 12, top=False), 0.25)])
        bone(f'{side}_hand', f'{side}_forearm', [x0 + aw / 2, 12, -1])
    # ноги: правая (+x, 0,16 / 0,32), левая (−x, 16,48 / 0,48)
    for side, sx, (u, v), (su, sv) in (('right', 1, (0, 16), (0, 32)), ('left', -1, (16, 48), (0, 48))):
        x0 = 0 if sx > 0 else -4
        px = 2 if sx > 0 else -2
        bone(f'{side}_leg', 'root', [px, 12, 0], [
            cube([x0, 6, -2], [4, 6, 4], faces(u, v, 4, 12, 4, 0, 6, bottom=False)),
            cube([x0, 6, -2], [4, 6, 4], faces(su, sv, 4, 12, 4, 0, 6, bottom=False), 0.25)])
        bone(f'{side}_shin', f'{side}_leg', [px, 6, 0], [
            cube([x0, 0, -2], [4, 6, 4], faces(u, v, 4, 12, 4, 6, 12, top=False)),
            cube([x0, 0, -2], [4, 6, 4], faces(su, sv, 4, 12, 4, 6, 12, top=False), 0.25)])
    return {'format_version': '1.12.0', 'minecraft:geometry': [{
        'description': {'identifier': 'geometry.novograd.citizen' + ('_slim' if slim else ''), 'texture_width': 64, 'texture_height': 64,
                        'visible_bounds_width': 3, 'visible_bounds_height': 3.5, 'visible_bounds_offset': [0, 1.25, 0]},
        'bones': bones}]}


# ------------------------------------------------------------------ анимации
def sine(t, period, amp, phase=0.0, offset=0.0):
    return offset + amp * math.sin(2 * math.pi * (t / period + phase))


def track(fn, length, steps=16):
    """Ключи по синусоиде/функции: время → [x, y, z]."""
    out = {}
    for i in range(steps + 1):
        t = round(length * i / steps, 4)
        out[f'{t}'] = [round(v, 3) for v in fn(t)]
    return out


def anim(length, bones, loop=True):
    return {'loop': loop, 'animation_length': length, 'bones': bones}


def walk(length, amp_leg, amp_arm, knee, elbow, lean, bob, twist):
    P = length
    return anim(P, {
        'right_leg': {'rotation': track(lambda t: [sine(t, P, amp_leg), 0, 0], P)},
        'left_leg': {'rotation': track(lambda t: [sine(t, P, amp_leg, 0.5), 0, 0], P)},
        # колено сгибается, когда нога уходит назад
        'right_shin': {'rotation': track(lambda t: [-max(0, sine(t, P, knee, 0.25)) - 4, 0, 0], P)},
        'left_shin': {'rotation': track(lambda t: [-max(0, sine(t, P, knee, 0.75)) - 4, 0, 0], P)},
        'right_arm': {'rotation': track(lambda t: [sine(t, P, amp_arm, 0.5), 0, 4], P)},
        'left_arm': {'rotation': track(lambda t: [sine(t, P, amp_arm), 0, -4], P)},
        'right_forearm': {'rotation': track(lambda t: [elbow + max(0, sine(t, P, elbow * 0.6, 0.5)), 0, 0], P)},
        'left_forearm': {'rotation': track(lambda t: [elbow + max(0, sine(t, P, elbow * 0.6)), 0, 0], P)},
        'chest': {'rotation': track(lambda t: [lean, sine(t, P, twist), 0], P)},
        'waist': {'rotation': track(lambda t: [lean * 0.4, sine(t, P, -twist * 0.5), 0], P)},
        'root': {'position': track(lambda t: [0, abs(math.sin(2 * math.pi * t / P)) * bob, 0], P)},
    })


def poses(keys, length, fps=10):
    """Плавный переход между позами: keys — [(время, [x,y,z]), ...], между ними — мягкий косинус."""
    out = {}
    n = int(length * fps)
    for i in range(n + 1):
        t = length * i / n
        for j in range(len(keys) - 1):
            (t0, a), (t1, b) = keys[j], keys[j + 1]
            if t0 <= t <= t1:
                k = (1 - math.cos(math.pi * (t - t0) / max(1e-6, t1 - t0))) / 2
                out[f'{round(t, 3)}'] = [round(a[c] + (b[c] - a[c]) * k, 3) for c in range(3)]
                break
    return out


def talk_anim():
    """Живой разговор: жест правой — раскрытая ладонь — обе руки «объясняют» — пауза; кивки, наклоны корпусом."""
    L = 6.0
    return anim(L, {
        'right_arm': {'rotation': poses([(0, [-12, 0, 6]), (0.5, [-48, -18, 10]), (1.1, [-38, -8, 14]), (1.5, [-55, -22, 8]),
                                          (2.2, [-30, 5, 22]), (3.0, [-45, -12, 12]), (3.6, [-52, -25, 8]), (4.3, [-20, 0, 8]),
                                          (5.2, [-14, 0, 6]), (6.0, [-12, 0, 6])], L)},
        'right_forearm': {'rotation': poses([(0, [10, 0, 0]), (0.5, [55, 0, 0]), (1.1, [35, 0, 0]), (1.5, [62, 0, 0]),
                                              (2.2, [40, 0, 0]), (3.0, [58, 0, 0]), (3.6, [48, 0, 0]), (4.3, [20, 0, 0]),
                                              (5.2, [12, 0, 0]), (6.0, [10, 0, 0])], L)},
        'left_arm': {'rotation': poses([(0, [-8, 0, -5]), (1.0, [-10, 0, -5]), (1.8, [-28, 12, -14]), (2.4, [-42, 18, -10]),
                                         (3.0, [-35, 10, -18]), (3.6, [-46, 20, -8]), (4.4, [-15, 4, -6]), (6.0, [-8, 0, -5])], L)},
        'left_forearm': {'rotation': poses([(0, [8, 0, 0]), (1.0, [10, 0, 0]), (1.8, [42, 0, 0]), (2.4, [60, 0, 0]),
                                             (3.0, [45, 0, 0]), (3.6, [58, 0, 0]), (4.4, [15, 0, 0]), (6.0, [8, 0, 0])], L)},
        'chest': {'rotation': poses([(0, [1, 0, 0]), (0.6, [4, -7, 0]), (1.5, [3, -4, 1.5]), (2.4, [5, 6, 0]), (3.3, [2, 3, -1.5]),
                                      (4.2, [3, -5, 0]), (5.2, [0, 0, 0.8]), (6.0, [1, 0, 0])], L)},
        'waist': {'rotation': poses([(0, [0, 0, 0]), (1.5, [0, -3, 0.8]), (3.0, [0, 3, -0.8]), (4.5, [0, -1, 0]), (6.0, [0, 0, 0])], L)},
        'head': {'rotation': poses([(0, [0, 0, 0]), (0.4, [5, 0, 2]), (0.7, [-2, 0, 2]), (1.0, [4, 0, 0]), (1.6, [0, 4, -3]),
                                     (2.5, [-3, -3, 2]), (3.1, [6, 0, 0]), (3.4, [-1, 0, 0]), (3.7, [5, 0, 0]), (4.6, [0, 5, 3]),
                                     (5.4, [2, 0, 0]), (6.0, [0, 0, 0])], L)},
    })


ANIMS = {
    'idle': anim(4.0, {
        'chest': {'rotation': track(lambda t: [sine(t, 4, 1.2), 0, sine(t, 8, 0.8)], 4.0), 'scale': track(lambda t: [1, 1 + sine(t, 4, 0.012), 1], 4.0)},
        'right_arm': {'rotation': track(lambda t: [sine(t, 4, 2), 0, 3 + sine(t, 4, 1.5)], 4.0)},
        'left_arm': {'rotation': track(lambda t: [sine(t, 4, 2, 0.3), 0, -3 - sine(t, 4, 1.5)], 4.0)},
        'right_forearm': {'rotation': track(lambda t: [6, 0, 0], 4.0, 2)},
        'left_forearm': {'rotation': track(lambda t: [6, 0, 0], 4.0, 2)},
        'waist': {'rotation': track(lambda t: [0, 0, sine(t, 8, 1.2)], 4.0)},
    }),
    'walk': walk(1.0, 28, 24, 45, 14, 3, 0.5, 6),
    'run': walk(0.6, 45, 50, 75, 55, 12, 1.1, 10),
    'talk': talk_anim(),
    'wave': anim(1.0, {
        'right_arm': {'rotation': track(lambda t: [-160, 0, 15 + sine(t, 0.5, 22)], 1.0)},
        'right_forearm': {'rotation': track(lambda t: [25 + sine(t, 0.5, 15), 0, 0], 1.0)},
        'chest': {'rotation': track(lambda t: [0, 0, sine(t, 1.0, 3)], 1.0)},
    }),
    'phone': anim(3.0, {
        'right_arm': {'rotation': track(lambda t: [-25, -20, 0], 3.0, 2)},
        'right_forearm': {'rotation': track(lambda t: [75 + sine(t, 0.6, 3), 0, 0], 3.0)},
        'left_arm': {'rotation': track(lambda t: [-20, 25, 0], 3.0, 2)},
        'left_forearm': {'rotation': track(lambda t: [70, 0, 0], 3.0, 2)},
        'head': {'rotation': track(lambda t: [22 + sine(t, 3, 3), 0, 0], 3.0)},
        'chest': {'rotation': track(lambda t: [5, 0, 0], 3.0, 2)},
    }),
    'think': anim(4.0, {
        'right_arm': {'rotation': track(lambda t: [-55, -20, 0], 4.0, 2)},
        'right_forearm': {'rotation': track(lambda t: [110 + sine(t, 4, 4), 0, 0], 4.0)},
        'left_arm': {'rotation': track(lambda t: [-35, 35, 0], 4.0, 2)},
        'left_forearm': {'rotation': track(lambda t: [80, 0, 0], 4.0, 2)},
        'head': {'rotation': track(lambda t: [-8, 0, 8 + sine(t, 4, 3)], 4.0)},
    }),
    'sit': anim(4.0, {
        'right_leg': {'rotation': track(lambda t: [-85, 6, 0], 4.0, 2)},
        'left_leg': {'rotation': track(lambda t: [-85, -6, 0], 4.0, 2)},
        'right_shin': {'rotation': track(lambda t: [80, 0, 0], 4.0, 2)},
        'left_shin': {'rotation': track(lambda t: [80, 0, 0], 4.0, 2)},
        'root': {'position': track(lambda t: [0, -6, 0], 4.0, 2)},
        'right_arm': {'rotation': track(lambda t: [-20, 0, 5], 4.0, 2)},
        'left_arm': {'rotation': track(lambda t: [-20, 0, -5], 4.0, 2)},
        'right_forearm': {'rotation': track(lambda t: [30, 0, 0], 4.0, 2)},
        'left_forearm': {'rotation': track(lambda t: [30, 0, 0], 4.0, 2)},
        'chest': {'rotation': track(lambda t: [sine(t, 4, 1.5), 0, 0], 4.0)},
    }),
    'ride': anim(4.0, {
        'right_leg': {'rotation': track(lambda t: [-80, 4, 0], 4.0, 2)},
        'left_leg': {'rotation': track(lambda t: [-80, -4, 0], 4.0, 2)},
        'right_shin': {'rotation': track(lambda t: [60, 0, 0], 4.0, 2)},
        'left_shin': {'rotation': track(lambda t: [60, 0, 0], 4.0, 2)},
        'right_arm': {'rotation': track(lambda t: [-45 + sine(t, 4, 2), -8, 0], 4.0)},
        'left_arm': {'rotation': track(lambda t: [-45 + sine(t, 4, 2, 0.5), 8, 0], 4.0)},
        'right_forearm': {'rotation': track(lambda t: [-20, 0, 0], 4.0, 2)},
        'left_forearm': {'rotation': track(lambda t: [-20, 0, 0], 4.0, 2)},
        'chest': {'rotation': track(lambda t: [-4 + sine(t, 4, 1), 0, 0], 4.0)},
    }),
    'dance': anim(1.2, {
        'root': {'position': track(lambda t: [sine(t, 1.2, 0.8), abs(math.sin(2 * math.pi * t / 0.6)) * 1.4, 0], 1.2)},
        'waist': {'rotation': track(lambda t: [0, sine(t, 1.2, 25), sine(t, 0.6, 6)], 1.2)},
        'chest': {'rotation': track(lambda t: [0, sine(t, 1.2, -15), sine(t, 0.6, -8)], 1.2)},
        'right_arm': {'rotation': track(lambda t: [-120 + sine(t, 0.6, 35), 0, 25], 1.2)},
        'left_arm': {'rotation': track(lambda t: [-120 + sine(t, 0.6, 35, 0.5), 0, -25], 1.2)},
        'right_forearm': {'rotation': track(lambda t: [40 + sine(t, 0.6, 30), 0, 0], 1.2)},
        'left_forearm': {'rotation': track(lambda t: [40 + sine(t, 0.6, 30, 0.5), 0, 0], 1.2)},
        'right_leg': {'rotation': track(lambda t: [sine(t, 0.6, 20), 0, 0], 1.2)},
        'left_leg': {'rotation': track(lambda t: [sine(t, 0.6, 20, 0.5), 0, 0], 1.2)},
        'right_shin': {'rotation': track(lambda t: [-max(0, sine(t, 0.6, 35)), 0, 0], 1.2)},
        'left_shin': {'rotation': track(lambda t: [-max(0, sine(t, 0.6, 35, 0.5)), 0, 0], 1.2)},
        'head': {'rotation': track(lambda t: [sine(t, 0.6, 10), 0, sine(t, 1.2, 8)], 1.2)},
    }),
    'cheer': anim(0.8, {
        'root': {'position': track(lambda t: [0, max(0, math.sin(2 * math.pi * t / 0.8)) * 3, 0], 0.8)},
        'right_arm': {'rotation': track(lambda t: [-170, 0, 25 + sine(t, 0.4, 10)], 0.8)},
        'left_arm': {'rotation': track(lambda t: [-170, 0, -25 - sine(t, 0.4, 10)], 0.8)},
        'right_forearm': {'rotation': track(lambda t: [15, 0, 0], 0.8, 2)},
        'left_forearm': {'rotation': track(lambda t: [15, 0, 0], 0.8, 2)},
        'right_shin': {'rotation': track(lambda t: [-max(0, -math.sin(2 * math.pi * t / 0.8)) * 30, 0, 0], 0.8)},
        'left_shin': {'rotation': track(lambda t: [-max(0, -math.sin(2 * math.pi * t / 0.8)) * 30, 0, 0], 0.8)},
    }),
    'kick': anim(0.5, {
        'right_leg': {'rotation': track(lambda t: [[-10, 35, -55, -20, 0][min(4, int(t / 0.1))], 0, 0], 0.5, 5)},
        'right_shin': {'rotation': track(lambda t: [[-10, -60, -5, -10, -4][min(4, int(t / 0.1))], 0, 0], 0.5, 5)},
        'left_arm': {'rotation': track(lambda t: [-30 * math.sin(math.pi * t / 0.5), 0, -15], 0.5, 5)},
        'chest': {'rotation': track(lambda t: [8 * math.sin(math.pi * t / 0.5), 0, 0], 0.5, 5)},
    }, loop=False),
}

# ---- 0.4: сон и общение. Записаны сразу в верном знаке: предплечье вперёд = −X, голень назад = +X
NEW = {
    'sleep': anim(4.0, {
        'root': {'rotation': track(lambda t: [-90, 0, 0], 4.0, 2), 'position': track(lambda t: [0, 2.2, 0], 4.0, 2)},
        'chest': {'scale': track(lambda t: [1, 1, 1 + sine(t, 4, 0.03)], 4.0)},
        'head': {'rotation': track(lambda t: [-8, 0, 12 + sine(t, 4, 2)], 4.0)},
        'right_arm': {'rotation': track(lambda t: [-6, 0, 6], 4.0, 2)},
        'left_arm': {'rotation': track(lambda t: [-30, 25, -4], 4.0, 2)},
        'left_forearm': {'rotation': track(lambda t: [-55, 0, 0], 4.0, 2)},
        'right_leg': {'rotation': track(lambda t: [0, 0, 3], 4.0, 2)},
        'left_leg': {'rotation': track(lambda t: [-12, 0, -2], 4.0, 2)},
        'left_shin': {'rotation': track(lambda t: [18, 0, 0], 4.0, 2)},
    }),
    'argue': anim(1.6, {
        'right_arm': {'rotation': track(lambda t: [-70 + sine(t, 0.4, 14), -10, 6], 1.6)},
        'right_forearm': {'rotation': track(lambda t: [-15 - max(0, sine(t, 0.4, 20)), 0, 0], 1.6)},
        'left_arm': {'rotation': track(lambda t: [-8, 0, -38], 1.6, 2)},
        'left_forearm': {'rotation': track(lambda t: [-75, 30, 0], 1.6, 2)},
        'chest': {'rotation': track(lambda t: [7 + sine(t, 0.8, 3), sine(t, 1.6, 8), 0], 1.6)},
        'head': {'rotation': track(lambda t: [sine(t, 0.4, 7), sine(t, 0.8, 10), 0], 1.6)},
    }),
    'laugh': anim(1.0, {
        'chest': {'rotation': track(lambda t: [-10 + sine(t, 0.25, 4), 0, sine(t, 1.0, 3)], 1.0)},
        'head': {'rotation': track(lambda t: [-16 + sine(t, 0.25, 6), 0, 0], 1.0)},
        'right_arm': {'rotation': track(lambda t: [-22, -20, 4], 1.0, 2)},
        'right_forearm': {'rotation': track(lambda t: [-70 + sine(t, 0.25, 5), 0, 0], 1.0)},
        'left_arm': {'rotation': track(lambda t: [-5 + sine(t, 0.5, 6), 0, -8], 1.0)},
        'root': {'position': track(lambda t: [0, abs(math.sin(2 * math.pi * t / 0.5)) * 0.4, 0], 1.0)},
    }),
    'fight': anim(1.2, {
        # стойка боксёра, левый джеб, правый прямой
        'right_arm': {'rotation': track(lambda t: [-95 if 0.65 < t < 0.85 else -55, 15 if 0.65 < t < 0.85 else 25, 0], 1.2, 24)},
        'right_forearm': {'rotation': track(lambda t: [-10 if 0.65 < t < 0.85 else -105, 0, 0], 1.2, 24)},
        'left_arm': {'rotation': track(lambda t: [-95 if 0.15 < t < 0.35 else -60, -15 if 0.15 < t < 0.35 else -25, 0], 1.2, 24)},
        'left_forearm': {'rotation': track(lambda t: [-10 if 0.15 < t < 0.35 else -100, 0, 0], 1.2, 24)},
        'chest': {'rotation': track(lambda t: [10, 18 if 0.65 < t < 0.85 else -14 if 0.15 < t < 0.35 else 0, 0], 1.2, 24)},
        'head': {'rotation': track(lambda t: [8, 0, 0], 1.2, 2)},
        'right_leg': {'rotation': track(lambda t: [14, 0, 4], 1.2, 2)},
        'left_leg': {'rotation': track(lambda t: [-16, 0, -4], 1.2, 2)},
        'right_shin': {'rotation': track(lambda t: [14, 0, 0], 1.2, 2)},
        'left_shin': {'rotation': track(lambda t: [10, 0, 0], 1.2, 2)},
        'root': {'position': track(lambda t: [0, -0.6 + abs(math.sin(2 * math.pi * t / 0.6)) * 0.5, 0], 1.2)},
    }),
    'hug': anim(2.0, {
        'right_arm': {'rotation': track(lambda t: [-80, -38, 0], 2.0, 2)},
        'left_arm': {'rotation': track(lambda t: [-80, 38, 0], 2.0, 2)},
        'right_forearm': {'rotation': track(lambda t: [-40, -20, 0], 2.0, 2)},
        'left_forearm': {'rotation': track(lambda t: [-40, 20, 0], 2.0, 2)},
        'chest': {'rotation': track(lambda t: [8, 0, sine(t, 2.0, 4)], 2.0)},
        'head': {'rotation': track(lambda t: [6, 25, 6], 2.0, 2)},
    }),
    'handshake': anim(1.0, {
        'right_arm': {'rotation': track(lambda t: [-48 + sine(t, 0.33, 7), -12, 0], 1.0)},
        'right_forearm': {'rotation': track(lambda t: [-28, 0, 0], 1.0, 2)},
        'chest': {'rotation': track(lambda t: [5, -6, 0], 1.0, 2)},
        'head': {'rotation': track(lambda t: [4 + sine(t, 0.5, 3), 0, 0], 1.0)},
    }),
    'upset': anim(3.0, {
        'head': {'rotation': track(lambda t: [28, 0, sine(t, 3, 4)], 3.0)},
        'chest': {'rotation': track(lambda t: [14 + sine(t, 0.75, 2), 0, 0], 3.0)},
        'right_arm': {'rotation': track(lambda t: [-38, -22, 0], 3.0, 2)},
        'left_arm': {'rotation': track(lambda t: [-38, 22, 0], 3.0, 2)},
        'right_forearm': {'rotation': track(lambda t: [-95, 0, 0], 3.0, 2)},
        'left_forearm': {'rotation': track(lambda t: [-95, 0, 0], 3.0, 2)},
    }),
    'aim': anim(1.0, {
        'right_arm': {'rotation': track(lambda t: [-88 + sine(t, 1.0, 1.5), -8, 0], 1.0)},
        'right_forearm': {'rotation': track(lambda t: [-4, 0, 0], 1.0, 2)},
        'left_arm': {'rotation': track(lambda t: [-80, 32, 0], 1.0, 2)},
        'left_forearm': {'rotation': track(lambda t: [-22, 0, 0], 1.0, 2)},
        'chest': {'rotation': track(lambda t: [4, -10, 0], 1.0, 2)},
        'head': {'rotation': track(lambda t: [2, -8, 0], 1.0, 2)},
        'right_leg': {'rotation': track(lambda t: [-6, 0, 3], 1.0, 2)},
        'left_leg': {'rotation': track(lambda t: [8, 0, -3], 1.0, 2)},
    }),
    'hit': anim(0.6, {
        'chest': {'rotation': track(lambda t: [-28 * math.sin(math.pi * t / 0.6), 0, 10 * math.sin(math.pi * t / 0.6)], 0.6, 6)},
        'head': {'rotation': track(lambda t: [-22 * math.sin(math.pi * t / 0.6), 0, 0], 0.6, 6)},
        'right_arm': {'rotation': track(lambda t: [0, 0, 40 * math.sin(math.pi * t / 0.6)], 0.6, 6)},
        'left_arm': {'rotation': track(lambda t: [0, 0, -40 * math.sin(math.pi * t / 0.6)], 0.6, 6)},
        'root': {'position': track(lambda t: [0, 0, 2.5 * math.sin(math.pi * t / 0.6)], 0.6, 6)},
    }, loop=False),
    'highfive': anim(0.8, {
        'right_arm': {'rotation': track(lambda t: [-150 + (25 if t > 0.45 else 0), -10, 10], 0.8, 16)},
        'right_forearm': {'rotation': track(lambda t: [-15, 0, 0], 0.8, 2)},
        'chest': {'rotation': track(lambda t: [-4, 0, 0], 0.8, 2)},
    }, loop=False),
}


def fix_joints():
    """Локти и колени гнутся только в свою сторону: предплечье вперёд (−X), голень назад (+X).
    Ключи выше записаны «наоборот» — переворачиваем (сидячие позы уже записаны верно)."""
    for name, a in ANIMS.items():
        for bone, ch in a['bones'].items():
            flip = (bone.endswith('_forearm') and name != 'ride') or (bone.endswith('_shin') and name not in ('sit', 'ride'))
            flip = flip and name not in NEW
            if flip and 'rotation' in ch:
                for k, v in ch['rotation'].items():
                    ch['rotation'][k] = [-v[0], v[1], v[2]]


def main():
    fix_joints()
    ANIMS.update(NEW)
    os.makedirs(f'{ROOT}/geo', exist_ok=True)
    os.makedirs(f'{ROOT}/animations', exist_ok=True)
    for slim in (False, True):
        name = 'citizen_slim' if slim else 'citizen'
        with open(f'{ROOT}/geo/{name}.geo.json', 'w') as f:
            json.dump(model(slim), f)
    with open(f'{ROOT}/animations/citizen.animation.json', 'w') as f:
        json.dump({'format_version': '1.8.0', 'animations': {f'animation.citizen.{k}': v for k, v in ANIMS.items()}}, f)
    print('ok', len(ANIMS), 'анимаций')


if __name__ == '__main__':
    main()
