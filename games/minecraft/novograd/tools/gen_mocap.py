#!/usr/bin/env python3
"""
Мокап для жителей Новограда: CMU Graphics Lab Motion Capture Database → анимации GeckoLib.

Источник: http://mocap.cs.cmu.edu (ASF/AMC — исходный формат базы; BVH-версия cgspeed сделана из тех же файлов).
Лицензия CMU: «This data is free for use in research projects... You may include this data in commercially-sold
products, but you may not resell this data directly, even in converted form.» Благодарность: «The data used in
this project was obtained from mocap.cs.cmu.edu. The database was created with funding from NSF EIA-0196217.»

Что делает:
  1. читает скелет (.asf) и движение (.amc, 120 кадров/с), считает прямую кинематику;
  2. переносит движение на наш скелет (root, waist, chest, head, руки/предплечья, ноги/голени):
     корпус и голова — полной ориентацией сегмента, конечности — по направлению кости и плоскости сгиба
     (у нас «коробки», поэтому кисти, стопы, ключицы, пальцы не нужны); убирает поворот всего тела
     (сглаженный курс таза) и перемещение — циклы на месте; ноги не проваливаются (root опускается,
     когда колени согнуты);
  3. 20 кадров/с, сшивает конец с началом для циклов, выкидывает почти-линейные ключи;
  4. пишет анимации с теми же именами, что уже знает код (animation.citizen.walk, run, idle, talk…),
     плюс новые варианты для кат-сцен (talk_1…talk_4 — жесты говорящего, listen_1…listen_3 — слушает).

Запуск (скачанные клипы лежат вне репозитория):
  python3 tools/gen_mocap.py --src /tmp/claude-0/mocap/cmu          — пересобрать анимации
  python3 tools/gen_mocap.py --src … --preview walk,talk_1 --out x.png  — контакт-лист для проверки
  python3 tools/gen_mocap.py --src … --scan 18_08                   — «энергия» рук/ног по секундам (выбор отрезков)
Результат: tools/mocap_anims.json (кэш, его подхватывает gen_geo.py) и src/main/resources/.../citizen.animation.json.
Скачать клипы: http://mocap.cs.cmu.edu/subjects/NN/NN.asf и …/NN/NN_MM.amc (список — CLIPS ниже).
"""
import argparse
import json
import math
import os
import re
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
ANIM_JSON = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'novograd', 'animations', 'citizen.animation.json')
CACHE = os.path.join(HERE, 'mocap_anims.json')
FPS_IN = 120
FPS = 20

# имя → (клип, начало c, конец c, длина цикла в игре c | None (как в записи), петля, режим)
# режим: 'cycle' — найти шаг/период в [начало, конец] и сшить; 'seg' — взять отрезок и сшить конец с началом
CLIPS = {
    # походка и бег: под них подогнан темп в CitizenEntity (walk 1.0 c на цикл, run 0.6 c)
    'walk': ('08_02', 0.5, 3.5, 1.0, True, 'cycle'),
    'run': ('09_01', 0.3, 1.5, 0.6, True, 'cycle'),
    'jog': ('16_35', 0.5, 2.5, 0.7, True, 'cycle'),
    # стоит и ждёт: переминается с ноги на ногу
    'idle': ('82_08', 0.5, 8.5, None, True, 'seg'),
    # разговор с жестами (CMU 18/19 — двое беседуют, объясняют руками)
    'talk': ('19_08', 2.5, 9.5, None, True, 'seg'),
    'talk_1': ('19_08', 2.5, 8.0, None, True, 'seg'),
    'talk_2': ('19_08', 6.0, 11.0, None, True, 'seg'),
    'talk_3': ('18_08', 10.0, 15.0, None, True, 'seg'),
    'talk_4': ('19_08', 12.0, 17.0, None, True, 'seg'),
    # слушает: тот же разговор, но отрезки, где собеседник молчит
    'listen_1': ('18_08', 3.5, 9.5, None, True, 'seg'),
    'listen_2': ('40_10', 0.5, 6.0, None, True, 'seg'),
    'listen_3': ('82_08', 3.0, 8.5, None, True, 'seg'),
    # ссора, курит (телефон у уха и смех в базе неудачные — для них остаются ручные анимации)
    'argue': ('80_48', 1.5, 6.0, None, True, 'seg'),
    'smoke': ('80_28', 1.0, 10.0, None, True, 'seg'),
}

# ---------------------------------------------------------------- ASF / AMC


def rot_x(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])


def rot_y(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])


def rot_z(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


def euler_xyz(deg):
    """ASF/AMC: углы по осям X, Y, Z (статические) → Rz·Ry·Rx."""
    x, y, z = (math.radians(v) for v in deg)
    return rot_z(z) @ rot_y(y) @ rot_x(x)


class Bone:
    def __init__(self, name):
        self.name = name
        self.direction = np.zeros(3)
        self.length = 0.0
        self.axis = np.zeros(3)
        self.dof = []
        self.children = []
        self.parent = None


def read_asf(path):
    bones = {'root': Bone('root')}
    lines = [l.strip() for l in open(path, errors='ignore').read().replace('\r', '').split('\n')]
    i = 0
    section = ''
    while i < len(lines):
        l = lines[i]
        if l.startswith(':'):
            section = l.split()[0]
        if l == 'begin' and section == ':bonedata':
            b = None
            i += 1
            while lines[i] != 'end':
                t = lines[i].split()
                if t[0] == 'name':
                    b = Bone(t[1])
                    bones[t[1]] = b
                elif t[0] == 'direction':
                    b.direction = np.array([float(v) for v in t[1:4]])
                elif t[0] == 'length':
                    b.length = float(t[1])
                elif t[0] == 'axis':
                    b.axis = np.array([float(v) for v in t[1:4]])
                elif t[0] == 'dof':
                    b.dof = t[1:]
                i += 1
        if l.startswith(":hierarchy"):
            i += 2
            while lines[i] != 'end':
                t = lines[i].split()
                for c in t[1:]:
                    bones[t[0]].children.append(bones[c])
                    bones[c].parent = bones[t[0]]
                i += 1
        i += 1
    return bones


def read_amc(path):
    frames = []
    cur = None
    for l in open(path, errors='ignore').read().replace('\r', '').split('\n'):
        l = l.strip()
        if not l or l[0] in '#:':
            continue
        if re.fullmatch(r'\d+', l):
            cur = {}
            frames.append(cur)
            continue
        t = l.split()
        cur[t[0]] = [float(v) for v in t[1:]]
    return frames


def fk(bones, frame):
    """Глобальные матрицы костей и положения концов (в системе CMU: Y вверх, лицом +Z, левая сторона +X)."""
    G, P0, P1 = {}, {}, {}
    r = frame['root']
    G['root'] = euler_xyz(r[3:6])
    P1['root'] = np.array(r[0:3])

    def walk(b):
        for c in b.children:
            C = euler_xyz(c.axis)
            vals = frame.get(c.name, [])
            ang = [0.0, 0.0, 0.0]
            for k, d in enumerate(c.dof):
                if k < len(vals):
                    ang['xyz'.index(d[1])] = vals[k]
            L = euler_xyz(ang)
            G[c.name] = G[b.name] @ C @ L @ C.T
            P0[c.name] = P1[b.name]
            P1[c.name] = P1[b.name] + c.length * (G[c.name] @ c.direction)
            walk(c)
    walk(bones['root'])
    return G, P0, P1


# ---------------------------------------------------------------- перенос на наш скелет

# наш скелет в «пространстве отрисовки» GeckoLib: X геометрии отражён, лицом −Z, вверх +Y.
# Кости right_* стоят на −X (у них в руке right_hand держит предмет), поэтому правую сторону CMU (−X)
# ведём в −X, а «вперёд» CMU (+Z) — в −Z: B = diag(1, 1, −1) (зеркало, движение просто отражено).
B = np.diag([1.0, 1.0, -1.0])
THIGH, SHIN = 6.0, 6.0  # пиксели


def norm(v):
    n = np.linalg.norm(v)
    return v / n if n > 1e-9 else v


def limb_frame(u, f, hint, sign):
    """Матрица кости-коробки: её −Y по u (кость висит вниз), плоскость сгиба — по f (локтя/колена).
    sign=−1: сустав сгибается вперёд (локоть: предплечье уходит в −Z), +1 — назад (колено)."""
    y = -norm(u)
    p = f - np.dot(f, u) * u / max(1e-9, np.dot(u, u))
    h = hint - np.dot(hint, -y) * (-y)
    p = p + h * 0.35
    if np.linalg.norm(p) < 1e-6:
        p = np.array([0, 0, -sign * 1.0])
    z = norm(p) * (1 if sign > 0 else -1)
    x = norm(np.cross(y, z))
    z = np.cross(x, y)
    return np.column_stack([x, y, z])


def angle(a, b):
    return math.acos(max(-1.0, min(1.0, float(np.dot(norm(a), norm(b))))))


def to_euler(R):
    """R = Rz·Ry·Rx (порядок GeckoLib) → (rx, ry, rz), радианы."""
    sy = -R[2, 0]
    sy = max(-1.0, min(1.0, sy))
    ry = math.asin(sy)
    if abs(sy) < 0.9999:
        rx = math.atan2(R[2, 1], R[2, 2])
        rz = math.atan2(R[1, 0], R[0, 0])
    else:
        rx = math.atan2(-R[1, 2], R[1, 1])
        rz = 0.0
    return rx, ry, rz


def json_rot(R):
    rx, ry, rz = to_euler(R)
    # GeckoLib при чтении JSON меняет знак X и Y
    return [-math.degrees(rx), -math.degrees(ry), math.degrees(rz)]


def heading(G):
    """Курс таза: куда смотрит тело (в пространстве отрисовки), угол вокруг Y."""
    fwd = B @ G['root'] @ np.array([0, 0, 1.0])
    return math.atan2(fwd[0], -fwd[2])  # 0 — смотрит в −Z


def smooth(a, win):
    if win <= 1:
        return a
    k = np.ones(win) / win
    pad = win // 2
    ap = np.concatenate([np.repeat(a[:1], pad, 0), a, np.repeat(a[-1:], win - 1 - pad, 0)])
    return np.array([np.convolve(ap[:, j], k, 'valid') for j in range(a.shape[1])]).T if a.ndim == 2 else np.convolve(ap, k, 'valid')


BONES = ['waist', 'chest', 'head', 'right_arm', 'right_forearm', 'left_arm', 'left_forearm',
         'right_leg', 'right_shin', 'left_leg', 'left_shin']


def retarget(bones, frames, head_win=FPS_IN, travel=False):
    """Кадры CMU (120 к/с) → список поз: {кость: матрица 3×3 локально}, root_y (пиксели).
    travel=True (циклы ходьбы/бега): «вперёд» — направление перемещения, а не разворот таза."""
    data = [fk(bones, fr) for fr in frames]
    hd = np.unwrap(np.array([heading(G) for G, _, _ in data]))
    hd = smooth(hd, head_win)
    if travel:
        mv = B @ (data[-1][2]['root'] - data[0][2]['root'])
        hd = np.full(len(data), math.atan2(mv[0], -mv[2]))
    out = []
    prev = None
    for (G, P0, P1), h in zip(data, hd):
        H = rot_y(h)  # повернуть так, чтобы смотрел в −Z (поворот вокруг Y на +h возвращает курс к нулю)
        # проверка знака: H @ fwd должен смотреть в −Z
        def gm(n):
            return H @ B @ G[n] @ B

        def gp(p):
            return H @ (B @ p)
        W = gm('lowerback')
        C = gm('thorax')
        Hd = gm('head')
        pose = {'waist': W, 'chest': W.T @ C, 'head': C.T @ Hd}
        for side, s in (('right', 'r'), ('left', 'l')):
            sh, el, wr = gp(P0[s + 'humerus']), gp(P1[s + 'humerus']), gp(P1[s + 'radius'])
            u, f = C.T @ (el - sh), C.T @ (wr - el)
            # подсказка для прямой руки: локоть смотрит назад, ладонь к бедру
            A = limb_frame(u, f, np.array([0, 0, -1.0]), -1)
            pose[side + '_arm'] = A
            pose[side + '_forearm'] = rot_x(angle(u, f))
            hip, kn, an, toe = gp(P0[s + 'femur']), gp(P1[s + 'femur']), gp(P1[s + 'tibia']), gp(P1[s + 'foot'])
            u, f = kn - hip, an - kn
            Lg = limb_frame(u, f, -(toe - an), +1)
            pose[side + '_leg'] = Lg
            pose[side + '_shin'] = rot_x(-angle(u, f))
        # ноги на земле: самая длинная по вертикали нога достаёт до земли
        hs = []
        for side in ('right', 'left'):
            Lg = pose[side + '_leg']
            d1 = Lg @ np.array([0, -1.0, 0])
            d2 = Lg @ pose[side + '_shin'] @ np.array([0, -1.0, 0])
            hs.append(-(d1[1] * THIGH + d2[1] * SHIN))
        pose['root_y'] = max(hs) - (THIGH + SHIN)
        out.append(pose)
        prev = pose
    return out


def to_channels(poses):
    """Позы → массив углов JSON (кадры × кости × 3), с развёрткой углов во времени."""
    arr = np.array([[json_rot(p[b]) for b in BONES] for p in poses])
    arr = np.degrees(np.unwrap(np.radians(arr), axis=0))
    ry = np.array([p['root_y'] for p in poses])
    return arr, ry


def resample(arr, ry, src_fps, dst_fps):
    n = arr.shape[0]
    t_src = np.arange(n) / src_fps
    t_dst = np.arange(0, t_src[-1] + 1e-9, 1.0 / dst_fps)
    a2 = np.empty((len(t_dst),) + arr.shape[1:])
    for b in range(arr.shape[1]):
        for c in range(3):
            a2[:, b, c] = np.interp(t_dst, t_src, arr[:, b, c])
    return a2, np.interp(t_dst, t_src, ry)


def find_cycle(arr, lo, hi):
    """Период цикла: сдвиг с минимальной разницей позы (ноги + руки)."""
    best, bi, bj = 1e18, 0, 0
    n = arr.shape[0]
    for i in range(0, n // 3):
        for j in range(i + lo, min(n, i + hi + 1)):
            d = float(np.sum((arr[i] - arr[j]) ** 2)) + float(np.sum((arr[min(i + 3, n - 1)] - arr[min(j + 3, n - 1)]) ** 2))
            if d < best:
                best, bi, bj = d, i, j
    return bi, bj


def loop_blend(arr, ry, frac=0.25):
    """Сшить конец с началом: разницу последнего и первого кадров плавно распределяем по концу клипа."""
    n = arr.shape[0]
    m = max(2, int(n * frac))
    arr = arr.copy()
    ry = ry.copy()
    # углы: выбрать ближайший эквивалент (±360)
    d = arr[0] - arr[-1]
    d = (d + 180) % 360 - 180
    dr = ry[0] - ry[-1]
    for k in range(m):
        w = (k + 1) / m
        w = w * w * (3 - 2 * w)
        arr[n - m + k] += d * w
        ry[n - m + k] += dr * w
    arr[-1] = arr[0] + 0  # точное совпадение (с поправкой на развёртку)
    arr[-1] = arr[-1] - ((arr[-1] - (arr[-2] + (arr[-1] - arr[-2]))) * 0)
    ry[-1] = ry[0]
    return arr, ry


def reduce_keys(vals, tol):
    """Ramer–Douglas–Peucker по времени: оставить ключи, без которых линейная интерполяция ошибается > tol."""
    n = len(vals)
    keep = {0, n - 1}

    def rec(a, b):
        if b - a < 2:
            return
        best, bi = 0.0, -1
        for i in range(a + 1, b):
            t = (i - a) / (b - a)
            e = float(np.max(np.abs(vals[i] - (vals[a] + (vals[b] - vals[a]) * t))))
            if e > best:
                best, bi = e, i
        if best > tol:
            keep.add(bi)
            rec(a, bi)
            rec(bi, b)
    rec(0, n - 1)
    return sorted(keep)


def build(name, src, cfg, cache_clips):
    clip, t0, t1, length, loop, mode = cfg
    subj = clip.split('_')[0]
    key = clip
    if key not in cache_clips:
        bones = read_asf(os.path.join(src, subj + '.asf'))
        frames = read_amc(os.path.join(src, clip + '.amc'))
        cache_clips[key] = (bones, frames)
    bones, frames = cache_clips[key]
    a = int(t0 * FPS_IN)
    b = min(len(frames), int(t1 * FPS_IN))
    poses = retarget(bones, frames[a:b], head_win=int(FPS_IN * 1.5), travel=mode == 'cycle')
    arr, ry = to_channels(poses)
    # мягкий фильтр против дрожи маркеров (≈ 1/12 c), потом 20 к/с
    arr = np.stack([smooth(arr[:, i, :], 9) for i in range(arr.shape[1])], 1)
    ry = smooth(ry, 9)
    if mode == 'cycle':
        lo, hi = int(0.55 * FPS_IN), int(1.6 * FPS_IN)
        if name in ('run', 'jog'):
            lo, hi = int(0.45 * FPS_IN), int(1.0 * FPS_IN)
        i, j = find_cycle(arr[::2], lo // 2, hi // 2)
        i, j = i * 2, j * 2
        arr, ry = arr[i:j + 1], ry[i:j + 1]
    src_len = (arr.shape[0] - 1) / FPS_IN
    if length is None:
        length = src_len
    arr, ry = resample(arr, ry, FPS_IN * src_len / length, FPS)
    if loop:
        arr, ry = loop_blend(arr, ry, 0.3 if mode == 'seg' else 0.15)
    n = arr.shape[0]
    dur = (n - 1) / FPS
    # поза ровнее: у CMU корпус бывает наклонён/повёрнут — убираем средний поворот поясницы
    # (в циклах наклон вперёд оставляем наполовину — при беге корпус и должен идти вперёд)
    wi = BONES.index('waist')
    mean = np.mean(arr[:, wi, :], 0)
    arr[:, wi, :] -= mean * (np.array([1.0, 1.0, 1.0]) if mode == 'seg' else np.array([0.6, 1.0, 1.0]))
    if mode == 'seg':
        # в записи двое стоят под углом друг к другу: грудь и голову разворачиваем «к собеседнику» (вперёд),
        # а размах головы уменьшаем — поворот головы к собеседнику добавит игра (neck)
        ci, hi = BONES.index('chest'), BONES.index('head')
        arr[:, ci, 1] -= np.mean(arr[:, ci, 1])
        arr[:, ci, 2] -= np.mean(arr[:, ci, 2])
        arr[:, hi, :] = (arr[:, hi, :] - np.mean(arr[:, hi, :], 0)) * 0.6
        # корпус спокойнее: в кадре крупным планом сильные наклоны выглядят как поклоны
        arr[:, wi, :] *= 0.7
        arr[:, ci, :] = np.mean(arr[:, ci, :], 0) + (arr[:, ci, :] - np.mean(arr[:, ci, :], 0)) * 0.75
    out = {'loop': bool(loop), 'animation_length': round(dur, 4), 'bones': {}}
    for bi, bname in enumerate(BONES):
        v = arr[:, bi, :]
        keys = reduce_keys(v, 1.2 if 'arm' in bname or 'leg' in bname or 'shin' in bname else 0.8)
        out['bones'][bname] = {'rotation': {f'{k / FPS:.4g}': [round(float(x), 2) for x in v[k]] for k in keys}}
    keys = reduce_keys(ry[:, None], 0.08)
    if np.max(np.abs(ry)) > 0.05:
        out['bones']['root'] = {'position': {f'{k / FPS:.4g}': [0, round(float(ry[k]), 3), 0] for k in keys}}
    return out


# ---------------------------------------------------------------- предпросмотр (без игры)

GEO = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'novograd', 'geo', 'citizen.geo.json')


def sample(track, t, length):
    ks = sorted((float(k), v) for k, v in track.items())
    if t <= ks[0][0]:
        return np.array(ks[0][1], float)
    for (ta, va), (tb, vb) in zip(ks, ks[1:]):
        if ta <= t <= tb:
            w = (t - ta) / max(1e-9, tb - ta)
            return np.array(va, float) * (1 - w) + np.array(vb, float) * w
    return np.array(ks[-1][1], float)


def preview(anims, names, out, frames=8):
    """Контакт-лист: на каждую анимацию два ряда — три четверти спереди и сбоку; нос — тёмный квадрат на лице."""
    from PIL import Image, ImageDraw
    geo = json.load(open(GEO))['minecraft:geometry'][0]['bones']
    rows = []
    for nm in names:
        an = anims['animation.citizen.' + nm]
        L = an['animation_length']
        for view, vyaw in (('3/4', 35), ('бок', 90)):
            row = Image.new('RGB', (frames * 130, 190), (235, 235, 240))
            d = ImageDraw.Draw(row)
            d.text((4, 4), f'{nm} {view}', fill=(0, 0, 0))
            for fi in range(frames):
                t = L * fi / frames
                W = {}
                polys = []
                for b in geo:
                    n = b['name']
                    piv = np.array([-b['pivot'][0], b['pivot'][1], b['pivot'][2]], float)
                    rot = np.zeros(3)
                    pos = np.zeros(3)
                    tr = an['bones'].get(n, {})
                    if 'rotation' in tr:
                        rot = sample(tr['rotation'], t, L)
                    if 'position' in tr:
                        pos = sample(tr['position'], t, L)
                    R = rot_z(math.radians(rot[2])) @ rot_y(math.radians(-rot[1])) @ rot_x(math.radians(-rot[0]))
                    M = np.eye(4)
                    M[:3, :3] = R
                    Tp = np.eye(4)
                    Tp[:3, 3] = piv + np.array([-pos[0], pos[1], pos[2]])
                    Tm = np.eye(4)
                    Tm[:3, 3] = -piv
                    P = W[b['parent']] if b.get('parent') else np.eye(4)
                    W[n] = P @ Tp @ M @ Tm
                    cubes = [(c['origin'], c['size']) for c in b.get('cubes', [])[:1]]
                    if n == 'head':
                        cubes.append(([-1, 27, -5], [2, 2, 1]))  # нос (лицо смотрит в −Z)
                    for ci, (o, s) in enumerate(cubes):
                        x0 = -(o[0] + s[0])
                        corners = [np.array([x0 + dx * s[0], o[1] + dy * s[1], o[2] + dz * s[2], 1.0]) for dx in (0, 1) for dy in (0, 1) for dz in (0, 1)]
                        pts = [(W[n] @ p)[:3] for p in corners]
                        faces = [(0, 1, 3, 2), (4, 5, 7, 6), (0, 1, 5, 4), (2, 3, 7, 6), (0, 2, 6, 4), (1, 3, 7, 5)]
                        col = (40, 20, 20) if ci else {'head': (230, 190, 150), 'waist': (60, 60, 140), 'chest': (80, 120, 200)}.get(
                            n, (210, 110, 70) if 'right' in n else (110, 200, 90))
                        if 'forearm' in n or 'shin' in n:
                            col = tuple(int(c * 0.75) for c in col)
                        for fc in faces:
                            polys.append(([pts[k] for k in fc], col))
                Rv = rot_x(math.radians(-10)) @ rot_y(math.radians(vyaw))

                def proj(p):
                    q = Rv @ p
                    return (fi * 130 + 65 + q[0] * 4.2, 170 - q[1] * 4.2, q[2])
                ps = []
                for pts, col in polys:
                    pp = [proj(p) for p in pts]
                    ps.append((sum(p[2] for p in pp) / 4, pp, col))
                ps.sort(key=lambda x: x[0])
                for z, pp, col in ps:
                    d.polygon([(p[0], p[1]) for p in pp], fill=col, outline=(30, 30, 30))
                d.line([(fi * 130 + 10, 171), (fi * 130 + 120, 171)], fill=(120, 120, 120))
                d.text((fi * 130 + 4, 176), f'{t:.2f}', fill=(0, 0, 0))
            rows.append(row)
    img = Image.new('RGB', (frames * 130, 190 * len(rows)))
    for i, r in enumerate(rows):
        img.paste(r, (0, i * 190))
    img.save(out)


def scan(src, clip):
    subj = clip.split('_')[0]
    bones = read_asf(os.path.join(src, subj + '.asf'))
    frames = read_amc(os.path.join(src, clip + '.amc'))
    print(clip, len(frames) / FPS_IN, 'c')
    data = [fk(bones, fr) for fr in frames[::12]]
    prev = None
    for i, (G, P0, P1) in enumerate(data):
        if i % 10 == 0 and prev is not None:
            pass
        hands = np.concatenate([P1['rradius'] - P1['thorax'], P1['lradius'] - P1['thorax']])
        root = P1['root']
        if prev is not None and i % 5 == 0:
            ph, pr = prev
            print(f'{i / 10:5.1f}c  руки {np.linalg.norm(hands - ph):5.2f}  корпус {np.linalg.norm((root - pr)[[0, 2]]):5.2f}  кисти выше пояса {int(P1["rradius"][1] > P1["root"][1] + 2)}{int(P1["lradius"][1] > P1["root"][1] + 2)}')
        if i % 5 == 0:
            prev = (hands, root)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--src', default='/tmp/claude-0/mocap/cmu')
    ap.add_argument('--only', default='')
    ap.add_argument('--preview', default='')
    ap.add_argument('--out', default='mocap_preview.png')
    ap.add_argument('--scan', default='')
    ap.add_argument('--no-write', action='store_true')
    ap.add_argument('--cached', action='store_true', help='не пересчитывать, взять tools/mocap_anims.json')
    a = ap.parse_args()
    if a.scan:
        scan(a.src, a.scan)
        return
    cache = json.load(open(CACHE)) if os.path.exists(CACHE) else {}
    if os.path.isdir(a.src) and not a.cached:
        clips = {}
        for name, cfg in CLIPS.items():
            if a.only and name not in a.only.split(','):
                continue
            cache['animation.citizen.' + name] = build(name, a.src, cfg, clips)
            print('ok', name, len(json.dumps(cache['animation.citizen.' + name])), 'байт')
        if not a.no_write:
            with open(CACHE, 'w') as f:
                json.dump(cache, f, separators=(',', ':'))
    if a.preview:
        preview(cache, a.preview.split(','), a.out)
        print('preview', a.out)
        return
    if a.no_write:
        return
    full = json.load(open(ANIM_JSON))
    full['animations'].update(cache)
    with open(ANIM_JSON, 'w') as f:
        json.dump(full, f)
    print('записано', len(cache), 'мокап-анимаций в', os.path.normpath(ANIM_JSON))


if __name__ == '__main__':
    main()
