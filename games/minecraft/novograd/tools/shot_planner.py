#!/usr/bin/env python3
"""
Постановщик кат-сцен Новограда (1.0 «Кино») — офлайн, «как в Blender», только своей математикой.

Blender (bpy) здесь не помещается на диск, поэтому то, что оператор делает в Blender руками, делает этот скрипт:
  1. берёт выгрузку места сцены из игры (автотест shotexport → scene_гл_мис_шаг.json: воксели вокруг, где стоят актёры,
     куда идут, реплики с длиной озвучки, исходные планы);
  2. сохраняет «биты» сцены (длительности исходных планов — к ним привязан таймлайн актёров), но внутри каждого бита
     с героем ставит камеру заново: общий план (24 мм), «восьмёрка» через плечо (50 мм), крупный план на эмоциональной
     реплике (85 мм), план двоих (35 мм), финальный отъезд; монтаж по репликам (смена говорящего — склейка, не короче
     1.6 с), правило 180° (камера по одну сторону линии взгляда), правило третей (глаза — на верхней трети, «воздух»
     в сторону взгляда), проверка видимости лица и свободного места для камеры по вокселям;
  3. движение камеры внутри плана — кривая Безье с плавным разгоном/торможением (наезд, отъезд, боковая тележка,
     подъём крана), ключи каждые 0.25 с: позиция, курс, наклон, FOV (объектив → вертикальный угол Minecraft), крен;
     «ручная камера» — тихое покачивание (hand) на диалогах;
  4. пишет src/main/resources/novograd_shots.json (игра подхватывает: story/Shots), листы раскадровки (быстрый рендер
     вокселей) и shot list в docs/SHOTS_1_0.md.

Объектив → FOV: кадр 16:9 полнокадровой камеры (36 × 20.25 мм), FOV_верт = 2·atan(10.125 / f).
  24 мм → 45.7°, 35 мм → 32.3°, 50 мм → 22.9°, 85 мм → 13.6° (в Minecraft «по умолчанию» 70° ≈ 14 мм).

Запуск:
  python3 tools/shot_planner.py --in /tmp/claude-0/novograd-shots [--preview папка] [--only 0:0:0,...]
"""
import argparse
import base64
import gzip
import json
import math
import os
import re

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
OUT_JSON = os.path.join(HERE, '..', 'src', 'main', 'resources', 'novograd_shots.json')
OUT_MD = os.path.join(HERE, '..', 'docs', 'SHOTS_1_0.md')

SENSOR_H = 20.25  # мм, 16:9 полный кадр
ASPECT = 16 / 9


def vfov(mm):
    return math.degrees(2 * math.atan(SENSOR_H / 2 / mm))


def hfov(mm):
    return math.degrees(2 * math.atan(SENSOR_H * ASPECT / 2 / mm))


# ------------------------------------------------------------------ мир

class World:
    def __init__(self, boxes):
        self.boxes = []
        for b in boxes:
            sx, sy, sz = b['size']
            data = np.frombuffer(gzip.decompress(base64.b64decode(b['gz'])), dtype=np.uint8).reshape(sx, sz, sy)
            self.boxes.append((np.array(b['origin'], int), (sx, sy, sz), data))

    def box_for(self, p):
        for o, s, d in self.boxes:
            if o[0] <= p[0] < o[0] + s[0] and o[2] <= p[2] < o[2] + s[2]:
                return o, s, d
        return None

    def cells(self, pts):
        """Значения вокселей для массива точек N×3 (вне коробок — воздух, ниже коробки — камень)."""
        pts = np.asarray(pts, float)
        out = np.zeros(len(pts), np.uint8)
        fl = np.floor(pts).astype(int)
        for o, s, d in self.boxes:
            rel = fl - o
            m = (rel[:, 0] >= 0) & (rel[:, 0] < s[0]) & (rel[:, 2] >= 0) & (rel[:, 2] < s[2])
            if not m.any():
                continue
            r = rel[m]
            y = r[:, 1]
            v = np.zeros(len(r), np.uint8)
            inside = (y >= 0) & (y < s[1])
            v[inside] = d[r[inside, 0], r[inside, 2], y[inside]]
            v[y < 0] = 1
            out[m] = v
        return out

    def solid(self, pts):
        return self.cells(pts) != 0

    def ground(self, x, z, y_hint):
        """Ступни: первый воздух над твёрдым в столбце около y_hint."""
        for dy in range(0, 12):
            for y in (y_hint + dy, y_hint - dy):
                if self.solid([[x, y - 0.5, z]])[0] and not self.solid([[x, y + 0.5, z]])[0] and not self.solid([[x, y + 1.5, z]])[0]:
                    return math.floor(y)
        return y_hint

    def clear(self, p, r=0.35):
        """Камере есть место: точка и шесть соседей на r свободны."""
        p = np.asarray(p, float)
        offs = np.array([[0, 0, 0], [r, 0, 0], [-r, 0, 0], [0, r, 0], [0, -r, 0], [0, 0, r], [0, 0, -r]])
        return not self.solid(p + offs).any()

    def los(self, a, b, skip_end=0.6):
        """Луч a→b свободен (кроме последних skip_end блоков — сама голова героя)."""
        a, b = np.asarray(a, float), np.asarray(b, float)
        d = np.linalg.norm(b - a)
        if d < 0.1:
            return True
        n = max(2, int(d / 0.2))
        t = np.linspace(0, max(0.0, (d - skip_end) / d), n)[:, None]
        return not self.solid(a + (b - a) * t).any()


# ------------------------------------------------------------------ углы Minecraft

def yaw_to(c, p):
    """Курс Minecraft: 0 — на +Z, 90 — на −X."""
    return math.degrees(math.atan2(-(p[0] - c[0]), p[2] - c[2]))


def pitch_to(c, p):
    h = math.hypot(p[0] - c[0], p[2] - c[2])
    return -math.degrees(math.atan2(p[1] - c[1], h))


def fwd(yaw, pitch=0.0):
    y, p = math.radians(yaw), math.radians(pitch)
    return np.array([-math.sin(y) * math.cos(p), -math.sin(p), math.cos(y) * math.cos(p)])


def wrap(a):
    return (a + 180) % 360 - 180


def aim(cam, eye, sx, sy, mm):
    """Курс/наклон камеры, чтобы глаза героя оказались в точке кадра (sx, sy) ∈ [−1, 1] (sx>0 — правее, sy>0 — выше)."""
    hy, hp = yaw_to(cam, eye), pitch_to(cam, eye)
    oy = math.degrees(math.atan(sx * math.tan(math.radians(hfov(mm) / 2))))
    op = math.degrees(math.atan(sy * math.tan(math.radians(vfov(mm) / 2))))
    # герой правее центра → камера повёрнута левее героя (курс растёт по часовой)
    return hy - oy, hp + op


def project(cam, yaw, pitch, mm, p):
    """Точка мира → координаты кадра (sx, sy) или None (за камерой)."""
    f = fwd(yaw, pitch)
    r = np.array([-math.cos(math.radians(yaw)), 0, -math.sin(math.radians(yaw))])  # вправо по кадру
    u = np.cross(r, f)
    u = -u if u[1] < 0 else u
    v = np.asarray(p, float) - np.asarray(cam, float)
    z = v @ f
    if z <= 0.05:
        return None
    th, tv = math.tan(math.radians(hfov(mm) / 2)), math.tan(math.radians(vfov(mm) / 2))
    return (v @ r) / z / th, (v @ u) / z / tv


# ------------------------------------------------------------------ сцена

WALK, RUN = 1.6, 3.6  # блоков/с (оценка; в игре кадр доворачивается на настоящее место героя — aim/subject)
EMO = re.compile(r'[!]|\?!|убь|прост|люб|предал|мёртв|мертв|конец|прощ|брат|умр|ненавиж|обещ|клянус|верн', re.I)


class Scene:
    def __init__(self, data):
        self.d = data
        self.key = data['key']
        self.world = World(data['voxels'])
        self.places = data['places']
        self.actors = {}  # id → список (t, x, y, z, yaw)
        self.kind = {}    # id → 'person' | 'car'
        self.ride_at = {} # id → когда сел в машину
        self.lines = []
        t = 0.0
        for l in data['lines']:
            dur = max(1.2, l.get('len', 0) + 0.35)
            self.lines.append({'who': l['who'], 'text': l['text'], 't0': t, 't1': t + dur, 'voice': l.get('voice', '')})
            t += dur
        self._timeline()

    def place(self, name):
        p = self.places.get(name)
        return None if p is None else np.array(p['pos'], float)

    def _timeline(self):
        for c in sorted(self.d['actors'], key=lambda c: c[0]):
            t, kind = c[0], c[1]
            if kind in ('spawn', 'car') and len(c) > 3 and isinstance(c[3], str):
                p = self.place(c[3])
                if p is None:
                    continue
                g = self.world.ground(p[0], p[2], int(round(p[1])))
                yaw = self.places[c[3]].get('yaw', 0)
                self.actors.setdefault(c[2], []).append([t, p[0], g, p[2], yaw])
                self.kind[c[2]] = 'person' if kind == 'spawn' else 'car'
            elif kind in ('walk', 'run') and len(c) > 3 and c[2] in self.actors:
                p = self.place(c[3])
                if p is None:
                    continue
                last = self.at(c[2], t)
                g = self.world.ground(p[0], p[2], int(round(last[1])))
                dist = math.hypot(p[0] - last[0], p[2] - last[2])
                sp = RUN if kind == 'run' else WALK
                self.actors[c[2]].append([t, last[0], last[1], last[2], last[3]])
                self.actors[c[2]].append([t + dist / sp, p[0], g, p[2], yaw_to(last, p)])
            elif kind == 'ride' and c[2] in self.actors:
                self.ride_at[c[2]] = min(self.ride_at.get(c[2], 1e9), t)  # до посадки — пеший герой

    def at(self, aid, t):
        tr = self.actors.get(aid)
        if not tr:
            return None
        if t <= tr[0][0]:
            return np.array(tr[0][1:5], float)
        for a, b in zip(tr, tr[1:]):
            if a[0] <= t <= b[0]:
                k = (t - a[0]) / max(1e-6, b[0] - a[0])
                v = np.array(a[1:5], float) * (1 - k) + np.array(b[1:5], float) * k
                v[3] = b[4] if k > 0.5 else a[4]
                return v
        return np.array(tr[-1][1:5], float)

    def eye(self, aid, t):
        p = self.at(aid, t)
        return None if p is None else np.array([p[0], p[1] + 1.62, p[2]])

    def moving(self, aid, t0, t1):
        a, b = self.at(aid, t0), self.at(aid, t1)
        return a is not None and b is not None and math.hypot(a[0] - b[0], a[2] - b[2]) > 1.0

    def speaker(self, t):
        for l in self.lines:
            if l['t0'] <= t < l['t1']:
                return l
        return None


# ------------------------------------------------------------------ планы

TYPES = {
    #            объектив, дистанция, высота камеры над глазами, ручная, движение
    'WS':  dict(mm=24, dist=16.0, up=6.0, hand=0.0),
    'TWO': dict(mm=35, dist=7.5, up=0.6, hand=0.2),
    'MS':  dict(mm=35, dist=6.0, up=0.3, hand=0.2),
    'OTS': dict(mm=50, dist=1.6, up=0.15, hand=0.25),
    'MCU': dict(mm=50, dist=4.6, up=0.1, hand=0.25),
    'CU':  dict(mm=85, dist=5.2, up=0.05, hand=0.3),
    'END': dict(mm=35, dist=9.0, up=2.5, hand=0.0),
}
NAMES = {'WS': 'общий (establishing)', 'TWO': 'план двоих', 'MS': 'средний', 'OTS': 'через плечо',
         'MCU': 'средне-крупный', 'CU': 'крупный', 'END': 'финальный отъезд'}


def bezier(p0, p3, bend):
    """Кубическая Безье p0→p3 с изгибом bend (вектор) — дуга тележки/крана."""
    p1 = p0 + (p3 - p0) / 3 + bend
    p2 = p0 + (p3 - p0) * 2 / 3 + bend
    return lambda u: ((1 - u) ** 3) * p0 + 3 * ((1 - u) ** 2) * u * p1 + 3 * (1 - u) * u * u * p2 + u ** 3 * p3


def ease(u):
    return u * u * (3 - 2 * u)


def candidates(sc, kind, subj, partner, t0, t1, side):
    """Кандидаты точки камеры (начало, конец) для плана kind: перебор азимута/дистанции/высоты вокруг идеала."""
    T = TYPES[kind]
    e = sc.eye(subj, t0)
    face = sc.at(subj, t0)[3]
    pe = sc.eye(partner, t0) if partner else None
    out = []
    if kind == 'OTS' and pe is not None:
        # за плечом партнёра: от партнёра назад (от героя), вбок на «свою» сторону линии
        d = (pe - e)
        d[1] = 0
        L = np.linalg.norm(d) or 1
        d /= L
        right = np.array([d[2], 0, -d[0]]) * side
        for back in (2.2, 2.7, 3.2, 1.8):
            for lat in (0.9, 1.1, 1.3, 0.7):
                for up in (0.1, 0.25, -0.05):
                    c = pe + d * back + right * lat + np.array([0, up, 0])
                    out.append((c, c + right * 0.35 - d * 0.1, abs(back - 2.7) + abs(lat - 1.1) * 2))
        return out
    # линия действия: герой → партнёр (или куда смотрит герой); камера — по одну сторону (side)
    if pe is not None:
        line = math.degrees(math.atan2(-(pe[0] - e[0]), pe[2] - e[2]))
    else:
        line = face
    base_az = line + 90 * side  # перпендикуляр к линии
    if kind in ('CU', 'MCU'):
        base_az = line + 40 * side  # почти «в лицо», но с той же стороны линии
    if kind in ('WS', 'END'):
        base_az = line + 120 * side
    for daz in (0, 15, -15, 30, -30, 45, -45, 60, -60, 80, -80):
        for dk in (1.0, 0.85, 1.2, 0.7, 1.45):
            for dup in (0, 1.0, -0.5, 2.5):
                az = math.radians(base_az + daz)
                # точка, откуда камера смотрит на героя под курсом az+180
                dirv = np.array([-math.sin(az), 0, math.cos(az)])
                ref = e if pe is None or kind in ('CU', 'MCU') else (e + pe) / 2
                c = ref + dirv * T['dist'] * dk + np.array([0, T['up'] + dup, 0])
                # движение: наезд/отъезд/тележка
                if kind == 'WS':
                    c1 = ref + dirv * T['dist'] * dk * 0.72 + np.array([0, T['up'] + dup - 1.5, 0])
                elif kind == 'END':
                    c1 = ref + dirv * T['dist'] * dk * 1.5 + np.array([0, T['up'] + dup + 3.0, 0])
                elif kind == 'CU':
                    c1 = c + (ref - c) * 0.08
                elif kind == 'MCU':
                    a2 = az + math.radians(8 * side)
                    c1 = ref + np.array([-math.sin(a2), 0, math.cos(a2)]) * T['dist'] * dk + np.array([0, T['up'] + dup, 0])
                else:  # TWO / MS — боковая тележка
                    c1 = c + np.array([math.cos(az), 0, math.sin(az)]) * 1.4 * side
                cost = abs(daz) / 30 + abs(dk - 1) * 2 + abs(dup) * 0.4
                out.append((c, c1, cost))
    return out


def frame_target(kind, sc, subj, partner, t, side):
    """Куда в кадре поставить глаза героя: третьи, «воздух» в сторону взгляда."""
    e = sc.eye(subj, t)
    pe = sc.eye(partner, t) if partner else None
    sy = 0.33  # глаза на верхней трети
    if kind in ('WS', 'END'):
        return 0.0, -0.1
    if kind == 'TWO':
        return 0.0, 0.2
    # герой смотрит на партнёра: партнёр по ту сторону — значит герой на противоположной трети
    sx = -0.33 * side
    if kind == 'OTS':
        sx = 0.33 * side
    return sx, sy


def seg_dist_axis(a, b, foot, h=1.9):
    """Мин. расстояние от отрезка a→b (камера → глаза) до вертикальной оси актёра (ступни → макушка)."""
    best = 1e9
    for u in np.linspace(0.05, 0.95, 19):
        p = a + (b - a) * u
        y = min(max(p[1], foot[1]), foot[1] + h)
        best = min(best, math.hypot(p[0] - foot[0], p[2] - foot[2]) + abs(p[1] - y))
    return best


def check(sc, c, eyes, t=None, skip=()):
    if not sc.world.clear(c):
        return False
    for e in eyes:
        if e is not None and not sc.world.los(c, e):
            return False
    if t is not None:  # лицо не загорожено другим актёром (плечо в «восьмёрке» — да, голова — нет)
        for aid, k in sc.kind.items():
            if aid in skip or k == 'car':
                continue
            f = sc.at(aid, t)
            if f is None:
                continue
            for e in eyes:
                if e is not None and np.linalg.norm(f[:3] + [0, 1.6, 0] - e) > 0.3 and seg_dist_axis(np.asarray(c), e, f[:3]) < 0.42:
                    return False
    return True


def plan_sub(sc, kind, subj, partner, t0, t1, side, prev_dir):
    """Подобрать камеру плана: лучшая по цене точка, у которой весь путь свободен и лицо видно в начале, середине, конце."""
    T = TYPES[kind]
    best = None
    for c0, c1, cost in candidates(sc, kind, subj, partner, t0, t1, side):
        path = bezier(c0, c1, np.array([0, 0.25 if kind in ('WS', 'END') else 0.0, 0]))
        ok = True
        for u in np.linspace(0.0, 1.0, 13):
            tt = t0 + (t1 - t0) * u
            c = path(ease(u))
            eyes = [sc.eye(subj, tt)]
            if kind in ('TWO', 'OTS') and partner:
                eyes.append(sc.eye(partner, tt) if kind == 'TWO' else None)
            if not check(sc, c, eyes, tt, skip=(subj,) if kind != 'TWO' else (subj, partner)):
                ok = False
                break
        if not ok:
            continue
        # монтаж: соседние планы не с одной точки (иначе «прыжок» склейки) — разница направления ≥ 25°
        d = sc.eye(subj, t0) - c0
        ang = math.degrees(math.atan2(-d[0], d[2]))
        if prev_dir is not None and abs(wrap(ang - prev_dir)) < 25 and kind != 'OTS':
            cost += 3
        if best is None or cost < best[2]:
            best = (c0, c1, cost, ang)
    return best


def keys_for(sc, kind, subj, partner, t0, t1, side, c0, c1):
    T = TYPES[kind]
    mm = T['mm']
    path = bezier(c0, c1, np.array([0, 0.25 if kind in ('WS', 'END') else 0.0, 0]))
    keys = []
    n = max(2, int(round((t1 - t0) / 0.25)))
    for i in range(n + 1):
        u = i / n
        tt = t0 + (t1 - t0) * u
        c = path(ease(u))
        e = sc.eye(subj, tt)
        if kind == 'TWO' and partner:
            e = (e + sc.eye(partner, tt)) / 2
        sx, sy = frame_target(kind, sc, subj, partner, tt, side)
        yaw, pitch = aim(c, e, sx, sy, mm)
        keys.append([round(tt - t0, 3), round(float(c[0]), 3), round(float(c[1]), 3), round(float(c[2]), 3),
                     round(yaw, 2), round(pitch, 2), round(vfov(mm), 2), 0])
    return keys, mm


def plan_scene(sc):
    shots = sc.d['shots']
    persons = [a for a, k in sc.kind.items() if k == 'person']
    out, log = [], []
    t = 0.0
    prev_dir = None
    prev_setup = None
    side = None
    replanned = 0
    last_subj = ''
    for i, sh in enumerate(shots):
        sec = float(sh['sec'])
        t0, t1 = t, t + sec
        t = t1
        subj = sh.get('track', '') or sh.get('follow', '')
        # финальный «пролёт» без героя (старый план «издалека к месту», 200+ блоков за секунды: камера обгоняет
        # прогрузку участков и режет дома) — снимаем финальным отъездом от последнего героя на ногах
        if not subj and i == len(shots) - 1 and 'from' in sh and last_subj:
            subj = last_subj
        on_foot = subj in persons and sc.ride_at.get(subj, 1e9) > t1 + 0.5
        if on_foot:
            last_subj = subj
        if not on_foot or ('follow' in sh and sc.moving(subj, t0, t1)):
            out.append(sh)
            what = 'за машиной/героем (оставлен, движение)' if 'follow' in sh else 'пролёт (оставлен)'
            log.append((t0, sec, what, sh.get('follow', sh.get('track', '')), '', ''))
            prev_dir = None
            prev_setup = None
            continue
        # партнёр: тот, кто говорит в этом бите (с героем или о нём), иначе ближайший другой человек в 7 блоках.
        # (раньше — всегда ближайший: в 3:1:0 «план двоих» на реплике Кроу снял Софи со спины и случайного гостя)
        e = sc.eye(subj, t0)
        partner, bd = None, 7.0
        for l in sc.lines:
            a = l['who']
            if l['t1'] > t0 and l['t0'] < t1 and a in persons and a != subj and sc.ride_at.get(a, 1e9) >= t1:
                pe = sc.eye(a, t0)
                if pe is not None and np.linalg.norm(pe - e) < 12.0:
                    partner, bd = a, 0.0
                    break
        for a in persons:
            if partner is not None:
                break
            if a == subj or sc.ride_at.get(a, 1e9) < t1:
                continue
            pe = sc.eye(a, t0)
            if pe is not None and np.linalg.norm(pe - e) < bd:
                partner, bd = a, np.linalg.norm(pe - e)
        # нарезка бита по репликам: смена говорящего — новый план, не короче 1.6 с
        cuts = [t0]
        for l in sc.lines:
            if t0 + 1.6 <= l['t0'] <= t1 - 1.6 and l['t0'] - cuts[-1] >= 1.6:
                cuts.append(l['t0'])
        cuts.append(t1)
        moving = sc.moving(subj, t0, t1)
        first_beat = i == 0
        last_beat = i == len(shots) - 1
        subs = []
        for a, b in zip(cuts, cuts[1:]):
            ln = sc.speaker((a + b) / 2)
            who = ln['who'] if ln else ''
            emo = bool(ln and EMO.search(ln['text']))
            if first_beat and a == t0 and (not ln or who == 'narrator'):
                kind, s, p = 'WS', subj, partner
            elif last_beat and b == t1 and (b - a) >= 2.5 and (not ln or who == 'narrator'):
                kind, s, p = 'END', subj, partner
            elif who in persons and who in (subj, partner):
                s = who
                p = partner if who == subj else subj
                kind = 'CU' if emo else ('OTS' if p and not moving else 'MCU')
                # партнёр вплотную (заложница в руках, 3:9:0): «через плечо» — полкадра чужого затылка; берём средне-крупный
                if kind == 'OTS' and np.linalg.norm(sc.eye(s, a) - sc.eye(p, a)) < 2.0:
                    kind = 'MCU'
            elif partner and not moving:
                kind, s, p = 'TWO', subj, partner
            else:
                kind, s, p = 'MS', subj, partner
            subs.append([kind, s, p, a, b, ln])
        planned = []
        okall = True
        for kind, s, p, a, b, ln in subs:
            if side is None:
                side = 1
                # сторона линии 180° — та, где у общего плана свободнее
                tries = {sd: plan_sub(sc, kind, s, p, a, b, sd, None) for sd in (1, -1)}
                good = {sd: v for sd, v in tries.items() if v}
                if good:
                    side = min(good, key=lambda sd: good[sd][2])
            if prev_setup == (kind, s):  # тот же план того же героя подряд — «прыжок»; берём соседний по крупности
                kind = {'OTS': 'CU', 'CU': 'MCU', 'MCU': 'CU', 'TWO': 'MCU', 'MS': 'MCU', 'WS': 'MS', 'END': 'MS'}[kind]
            best = plan_sub(sc, kind, s, p, a, b, side, prev_dir)
            if best is None and kind in ('OTS', 'CU'):
                kind = 'MCU'
                best = plan_sub(sc, kind, s, p, a, b, side, prev_dir)
            if best is None:
                kind = 'MS'
                best = plan_sub(sc, kind, s, p, a, b, side, prev_dir) or plan_sub(sc, kind, s, p, a, b, -side, prev_dir)
            if best is None:
                okall = False
                break
            c0, c1, cost, ang = best
            keys, mm = keys_for(sc, kind, s, p, a, b, side, c0, c1)
            e_aim = sc.eye(s, a)
            shot = {'keys': keys, 'sec': round(b - a, 3), 'subject': s, 'aim': [round(float(x), 2) for x in e_aim],
                    'name': f'{kind} {mm}мм'}
            if TYPES[kind]['hand'] > 0:
                shot['hand'] = TYPES[kind]['hand'] * (1.6 if ln and EMO.search(ln['text']) else 1.0)
            planned.append((shot, kind, mm, s, ln))
            prev_dir = ang
            prev_setup = (kind, s)
        if not okall or not planned:
            out.append(sh)
            log.append((t0, sec, 'исходный (нет свободной точки)', subj, '', ''))
            continue
        replanned += 1
        for shot, kind, mm, s, ln in planned:
            out.append(shot)
            log.append((t0, shot['sec'], f'{NAMES[kind]}, {mm} мм', s, (ln['text'][:60] + '…') if ln and len(ln['text']) > 60 else (ln['text'] if ln else ''),
                        'ручная' if shot.get('hand') else ''))
            t0 += shot['sec']
    return out, log, replanned


# ------------------------------------------------------------------ предпросмотр (быстрый рендер вокселей)

def palette(v):
    v = v.astype(np.int32)
    r = (v * 97) % 160 + 60
    g = (v * 57) % 140 + 70
    b = (v * 31) % 120 + 60
    col = np.stack([r, g, b], -1).astype(np.float32)
    col[v == 255] = [150, 190, 200]
    return col


def render(sc, cam, yaw, pitch, mm, t, W=192, H=108):
    f = fwd(yaw, pitch)
    r = np.array([-math.cos(math.radians(yaw)), 0, -math.sin(math.radians(yaw))])
    u = np.cross(r, f)
    u = -u if u[1] < 0 else u
    th, tv = math.tan(math.radians(hfov(mm) / 2)), math.tan(math.radians(vfov(mm) / 2))
    xs = (np.arange(W) + 0.5) / W * 2 - 1
    ys = 1 - (np.arange(H) + 0.5) / H * 2
    X, Y = np.meshgrid(xs, ys)
    dirs = f[None, None, :] + X[..., None] * th * r[None, None, :] + Y[..., None] * tv * u[None, None, :]
    dirs /= np.linalg.norm(dirs, axis=-1, keepdims=True)
    dirs = dirs.reshape(-1, 3)
    img = np.tile(np.array([150, 180, 220], np.float32), (len(dirs), 1))
    depth = np.full(len(dirs), 1e9)
    alive = np.ones(len(dirs), bool)
    cam = np.asarray(cam, float)
    for k in range(1, 480):
        d = k * 0.25
        idx = np.nonzero(alive)[0]
        if len(idx) == 0:
            break
        pts = cam + dirs[idx] * d
        v = sc.world.cells(pts)
        hit = v != 0
        if hit.any():
            h = idx[hit]
            img[h] = palette(v[hit]) * (0.55 + 0.45 * math.exp(-d / 60)) + np.array([150, 180, 220]) * (1 - math.exp(-d / 90)) * 0.3
            depth[h] = d
            alive[h] = False
    img = img.reshape(H, W, 3)
    depth = depth.reshape(H, W)
    # актёры — столбики (ступни → макушка), с проверкой глубины
    for aid, k in sc.kind.items():
        p = sc.at(aid, t)
        if p is None:
            continue
        hgt, wid, col = (1.8, 0.3, (220, 60, 50)) if k != 'car' else (1.5, 1.1, (60, 60, 70))
        a = project(cam, yaw, pitch, mm, [p[0], p[1], p[2]])
        b = project(cam, yaw, pitch, mm, [p[0], p[1] + hgt, p[2]])
        if a is None or b is None:
            continue
        dist = np.linalg.norm(np.array([p[0], p[1] + 1, p[2]]) - cam)
        px = int((a[0] + 1) / 2 * W)
        y0, y1 = int((1 - b[1]) / 2 * H), int((1 - a[1]) / 2 * H)
        half = max(1, int(wid / (dist * th) * W / 2))
        for yy in range(max(0, y0), min(H, y1)):
            for xx in range(max(0, px - half), min(W, px + half + 1)):
                if depth[yy, xx] > dist:
                    img[yy, xx] = col
        hy = int((1 - project(cam, yaw, pitch, mm, [p[0], p[1] + 1.62, p[2]])[1]) / 2 * H)
        if 0 <= hy < H and k != 'car':
            for xx in range(max(0, px - half), min(W, px + half + 1)):
                img[hy, xx] = (255, 230, 120)
    # трети
    for g in (W // 3, 2 * W // 3):
        img[:, g] = img[:, g] * 0.6 + 100
    for g in (H // 3, 2 * H // 3):
        img[g, :] = img[g, :] * 0.6 + 100
    return np.clip(img, 0, 255).astype(np.uint8)


def sheet(sc, shots, path):
    from PIL import Image, ImageDraw
    rows = []
    t = 0.0
    for sh in shots:
        sec = float(sh['sec'])
        if 'keys' in sh:
            ks = sh['keys']
            frames = [ks[0], ks[len(ks) // 2], ks[-1]]
            ims = [render(sc, k[1:4], k[4], k[5], [m for m in (24, 35, 50, 85) if abs(vfov(m) - k[6]) < 0.5][0] if any(abs(vfov(m) - k[6]) < 0.5 for m in (24, 35, 50, 85)) else 35, t + k[0]) for k in frames]
            rows.append((sh.get('name', ''), ims))
        t += sec
    if not rows:
        return
    W, H = 192, 108
    im = Image.new('RGB', (W * 3 + 150, (H + 4) * len(rows)), (20, 20, 20))
    dr = ImageDraw.Draw(im)
    for i, (name, ims) in enumerate(rows):
        dr.text((4, i * (H + 4) + 4), name, fill=(240, 240, 240))
        for j, a in enumerate(ims):
            im.paste(Image.fromarray(a), (150 + j * W, i * (H + 4)))
    im.save(path)


# ------------------------------------------------------------------ main

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--in', dest='src', default='/tmp/claude-0/novograd-shots')
    ap.add_argument('--preview', default='')
    ap.add_argument('--only', default='')
    ap.add_argument('--titles', default='')
    a = ap.parse_args()
    out = {'scenes': {}}
    if os.path.exists(OUT_JSON):
        out = json.load(open(OUT_JSON))
    md = []
    files = sorted(f for f in os.listdir(a.src) if f.startswith('scene_') and f.endswith('.json'))
    titles = json.load(open(a.titles)) if a.titles else {}
    for f in files:
        data = json.load(open(os.path.join(a.src, f)))
        if a.only and data['key'] not in a.only.split(','):
            continue
        sc = Scene(data)
        shots, log, n = plan_scene(sc)
        prev = out['scenes'].get(data['key'], {})
        entry = {'shots': shots}
        if 'cues' in prev:
            entry['cues'] = prev['cues']
        out['scenes'][data['key']] = entry
        total = sum(float(s['sec']) for s in shots)
        orig = sum(float(s['sec']) for s in data['shots'])
        print(f"{data['key']}: битов {len(data['shots'])}, переснято {n}, планов {len(shots)}, длина {total:.1f} c (было {orig:.1f})")
        md.append(f"\n### {data['key']} {titles.get(data['key'], '')}\n\n| с | длина | план | герой | реплика | камера |\n|---|---|---|---|---|---|")
        for t0, sec, what, who, line, hand in log:
            md.append(f"| {t0:.1f} | {float(sec):.1f} | {what} | {who} | {line} | {hand} |")
        if a.preview:
            os.makedirs(a.preview, exist_ok=True)
            sheet(sc, shots, os.path.join(a.preview, f"board_{data['key'].replace(':', '_')}.png"))
    with open(OUT_JSON, 'w') as fh:
        json.dump(out, fh, ensure_ascii=False, separators=(',', ':'))
    with open(OUT_MD + '.part', 'w') as fh:
        fh.write('\n'.join(md) + '\n')
    print('записано', os.path.normpath(OUT_JSON), 'и', os.path.normpath(OUT_MD) + '.part')


if __name__ == '__main__':
    main()
