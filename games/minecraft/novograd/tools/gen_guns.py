"""Объёмные модели оружия (кубоиды, как у блоков) + общая текстура-палитра, вспышка выстрела и звуки затвора.

Ствол смотрит на север (-z), верх +y, центр модели (8, 8, 8). Рукоять — около центра, чтобы рука держала её.
Запуск: python3 tools/gen_guns.py  (перезаписывает models/item/{pistol,smg,shotgun,rifle,sniper,knife,grenade,molotov}.json,
textures/item/gun_atlas.png, textures/misc/muzzle_flash.png, sounds/*_reload.ogg, sounds/gun_dry.ogg).
"""
import json, math, os, subprocess, wave
import numpy as np
from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'assets', 'novograd')
MODELS = os.path.join(ROOT, 'models', 'item')
TEX = os.path.join(ROOT, 'textures')
SND = os.path.join(ROOT, 'sounds')

# ------------------------------------------------------------------ палитра 64×64: 16 клеток 16×16
CELLS = ['metal', 'metal_hi', 'polymer', 'wood', 'wood_dark', 'olive', 'olive_dark', 'lens',
         'muzzle', 'brass', 'steel', 'grip', 'glass', 'rag', 'fire', 'red']
BASE = {'metal': '2b2e33', 'metal_hi': '50555c', 'polymer': '1d1e21', 'wood': '8c5226', 'wood_dark': '5f3517',
        'olive': '56653d', 'olive_dark': '3c4a2b', 'lens': '2a6fa8', 'muzzle': '0a0a0a', 'brass': 'c9a227',
        'steel': 'b9c0c9', 'grip': '26272b', 'glass': '3f8a42', 'rag': 'e3d9bd', 'fire': 'ff8a1c', 'red': 'c0262b'}


def rgb(h):
    return np.array([int(h[i:i + 2], 16) for i in (0, 2, 4)], float)


def atlas():
    rng = np.random.default_rng(7)
    img = np.zeros((64, 64, 4), np.uint8)
    yy, xx = np.mgrid[0:16, 0:16]
    for i, name in enumerate(CELLS):
        c = rgb(BASE[name])
        cell = np.repeat(c[None, None, :], 16, 0).repeat(16, 1)
        noise = rng.normal(0, 1, (16, 16))
        if name in ('wood', 'wood_dark'):
            grain = np.sin(yy * 1.7 + np.sin(xx * 0.35) * 2.2) * 9 + rng.normal(0, 1, (16, 1)) * 6
            cell += grain[..., None] * np.array([1, 0.75, 0.5])
            cell += noise[..., None] * 3
        elif name == 'grip':  # насечка рукояти
            cell += (((xx + yy) % 3 == 0) * 14 - 4)[..., None]
        elif name in ('metal', 'metal_hi', 'polymer', 'olive', 'olive_dark'):
            cell += noise[..., None] * (5 if name != 'polymer' else 3)
            cell += (np.sin(xx * 0.9) * 2)[..., None]  # лёгкая «шлифовка»
        elif name == 'steel':
            cell += (yy * -2.2 + 16)[..., None] + noise[..., None] * 4
        elif name == 'lens':
            d = np.hypot(xx - 6, yy - 6)
            cell += (np.clip(40 - d * 8, 0, 60))[..., None] * np.array([1.1, 1.2, 1.3])
            cell[(np.hypot(xx - 5, yy - 5) < 2)] = [220, 240, 255]
        elif name == 'muzzle':
            d = np.hypot(xx - 7.5, yy - 7.5)
            cell[:] = np.where((d > 5.2)[..., None], rgb('3a3d42'), rgb('050505'))
        elif name == 'glass':
            cell += noise[..., None] * 4
            cell[2:12, 3:5] = [150, 220, 150]
        elif name == 'fire':
            cell = np.stack([np.full((16, 16), 255.), 140 + yy * 7. + noise * 10, 30 + yy * 4.], -1)
        else:
            cell += noise[..., None] * 4
        r, cc = divmod(i, 4)
        img[r * 16:(r + 1) * 16, cc * 16:(cc + 1) * 16, :3] = np.clip(cell, 0, 255)
        img[r * 16:(r + 1) * 16, cc * 16:(cc + 1) * 16, 3] = 255
    os.makedirs(os.path.join(TEX, 'item'), exist_ok=True)
    Image.fromarray(img).save(os.path.join(TEX, 'item', 'gun_atlas.png'))


def flash():
    """Вспышка у дула: белое ядро, оранжевые лучи. Чёрный = прозрачно (рисуется сложением)."""
    n = 64
    yy, xx = (np.mgrid[0:n, 0:n] - (n - 1) / 2) / (n / 2)
    r = np.hypot(xx, yy)
    a = np.arctan2(yy, xx)
    rays = np.clip(np.cos(a * 4) ** 8 * 0.9 + np.cos(a * 4 + math.pi / 4) ** 16 * 0.5, 0, 1)
    shape = np.clip(1 - r / (0.35 + rays * 0.65), 0, 1) ** 1.6
    core = np.clip(1 - r / 0.32, 0, 1) ** 1.2
    col = np.stack([255 * np.clip(shape * 1.4 + core, 0, 1),
                    255 * np.clip(shape * 0.95 + core, 0, 1),
                    255 * np.clip(shape * 0.35 + core * 0.9, 0, 1)], -1)
    img = np.dstack([col, np.full((n, n), 255.)]).astype(np.uint8)
    os.makedirs(os.path.join(TEX, 'misc'), exist_ok=True)
    Image.fromarray(img).save(os.path.join(TEX, 'misc', 'muzzle_flash.png'))


# ------------------------------------------------------------------ модели
def uv(name):
    i = CELLS.index(name)
    r, c = divmod(i, 4)
    return [c * 4 + 0.25, r * 4 + 0.25, c * 4 + 3.75, r * 4 + 3.75]


def box(a, b, mat, faces=None, rot=None, shade=True):
    f = {s: {'uv': uv((faces or {}).get(s, mat)), 'texture': '#0'} for s in ('north', 'south', 'east', 'west', 'up', 'down')}
    e = {'from': [round(v, 3) for v in a], 'to': [round(v, 3) for v in b], 'faces': f}
    if rot:  # (ось, угол, центр) — угол только -45/-22.5/0/22.5/45
        e['rotation'] = {'axis': rot[0], 'angle': rot[1], 'origin': rot[2]}
    if not shade:
        e['shade'] = False
    return e


def _rx(a):
    a = math.radians(a); c, s = math.cos(a), math.sin(a)
    return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])


def _ry(a):
    a = math.radians(a); c, s = math.cos(a), math.sin(a)
    return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])


def _rz(a):
    a = math.radians(a); c, s = math.cos(a), math.sin(a)
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


def euler_xyz(R):
    """Углы для display.rotation (Minecraft: R = Rx·Ry·Rz)."""
    b = math.asin(max(-1, min(1, R[0][2])))
    a = math.atan2(-R[1][2], R[2][2])
    c = math.atan2(-R[0][1], R[0][0])
    return [round(math.degrees(v), 2) for v in (a, b, c)]


def corners(elements):
    pts = []
    for el in elements:
        f, t = el['from'], el['to']
        cs = [np.array([x, y, z], float) for x in (f[0], t[0]) for y in (f[1], t[1]) for z in (f[2], t[2])]
        if 'rotation' in el:
            r = el['rotation']; o = np.array(r['origin'], float)
            R = {'x': _rx, 'y': _ry, 'z': _rz}[r['axis']](r['angle'])
            cs = [R @ (p - o) + o for p in cs]
        pts += cs
    return np.array(pts) / 16 - 0.5


def fit(elements, tilt, fill=0.95, yaw=-112, pitch=22):
    """Иконка: стволом вправо-вверх на tilt градусов, вписать в слот."""
    R = _rz(tilt) @ _rx(pitch) @ _ry(yaw)
    rot = euler_xyz(R)
    R2 = _rx(rot[0]) @ _ry(rot[1]) @ _rz(rot[2])
    q = corners(elements) @ R2.T
    lo, hi = q.min(0), q.max(0)
    sc = fill / max(hi[0] - lo[0], hi[1] - lo[1])
    cx, cy = (lo[0] + hi[0]) / 2 * sc, (lo[1] + hi[1]) / 2 * sc
    return {'rotation': rot, 'translation': [round(-cx * 16, 2), round(-cy * 16, 2), 0], 'scale': [round(min(sc, 1.6), 3)] * 3}


GUI_TILT = {'pistol': 0, 'smg': 18, 'shotgun': 32, 'rifle': 32, 'sniper': 34, 'knife': 40, 'grenade': 0, 'molotov': 0}


def model(name, elements, display):
    display['gui'] = fit(elements, GUI_TILT[name], 0.7 if name in ('grenade', 'molotov') else 0.86 if name == 'pistol' else 0.95,
                         yaw=-135 if name in ('grenade', 'molotov') else -112)
    m = {'credit': 'Novograd: tools/gen_guns.py', 'texture_size': [64, 64],
         'textures': {'0': 'novograd:item/gun_atlas', 'particle': 'novograd:item/gun_atlas'},
         'elements': elements, 'display': display}
    with open(os.path.join(MODELS, name + '.json'), 'w') as f:
        json.dump(m, f, indent=1)


def gun_display(grip, length, fp=(0, 0, 0), fp_scale=0.85, tp_scale=0.85, gui_scale=None, gui_shift=(0, 0), fp_pitch=0):
    """grip — точка рукояти (x, y, z) в пикселях модели; length — длина ствола-приклада (для иконки)."""
    gx, gy, gz = (grip[0] - 8, grip[1] - 8, grip[2] - 8)
    gs = gui_scale or min(0.9, 15.0 / length)
    tp = [round(-gx * tp_scale, 2), round(-gy * tp_scale - 1.5, 2), round(-gz * tp_scale + 1.5, 2)]  # рукоять — в кулаке
    return {
        # от первого лица ствол уходит вперёд, чуть к центру экрана
        'firstperson_righthand': {'rotation': [fp_pitch, 4, 0], 'translation': [round(fp[0] - gx * fp_scale, 2), round(fp[1] - gy * fp_scale, 2), round(fp[2] - gz * fp_scale, 2)],
                                  'scale': [fp_scale] * 3},
        'firstperson_lefthand': {'rotation': [fp_pitch, 4, 0], 'translation': [round(fp[0] - gx * fp_scale, 2), round(fp[1] - gy * fp_scale, 2), round(fp[2] - gz * fp_scale, 2)],
                                 'scale': [fp_scale] * 3},
        # от третьего лица руки подняты (прицел) — рукоять в кулаке, ствол вперёд
        'thirdperson_righthand': {'rotation': [0, 0, 0], 'translation': tp, 'scale': [tp_scale] * 3},
        'thirdperson_lefthand': {'rotation': [0, 0, 0], 'translation': tp, 'scale': [tp_scale] * 3},
        # иконка: стволом вправо, чуть сверху и повёрнута к нам
        'gui': {'rotation': [24, -118, 0], 'translation': [gui_shift[0], gui_shift[1], 0], 'scale': [gs] * 3},
        'ground': {'rotation': [0, 90, 0], 'translation': [0, 2, 0], 'scale': [min(0.6, gs * 0.9)] * 3},
        'fixed': {'rotation': [0, 90, 0], 'translation': [0, 0, 0], 'scale': [gs * 1.1] * 3},
        'head': {'rotation': [0, 90, 0], 'translation': [0, 6, 0], 'scale': [0.8] * 3},
    }


def pistol():
    e = [
        box([7, 8, 2], [9, 10.4, 11], 'metal', {'north': 'metal'}),                 # затвор
        box([7.2, 10.4, 2.4], [8.8, 10.6, 10.6], 'metal_hi'),                        # верхняя грань затвора
        box([7.5, 8.6, 1.6], [8.5, 9.6, 2], 'metal', {'north': 'muzzle'}),           # дуло
        box([7.15, 7, 3], [8.85, 8, 11], 'polymer'),                                 # рамка
        box([7.75, 10.6, 2.4], [8.25, 11, 2.9], 'metal_hi'),                         # мушка
        box([7.35, 10.6, 10.1], [8.65, 11, 10.7], 'metal_hi'),                       # целик
        box([6.95, 8.5, 8.2], [9.05, 10, 10.6], 'metal_hi'),                         # насечка затвора
        box([7.15, 3.2, 8.2], [8.85, 7.2, 10.8], 'grip', rot=('x', -22.5, [8, 7, 10])),  # рукоять
        box([7.05, 2.7, 8.1], [8.95, 3.3, 10.9], 'polymer', rot=('x', -22.5, [8, 7, 10])),  # пятка магазина
        box([7.6, 6.2, 5.6], [8.4, 6.6, 8.4], 'polymer'),                            # скоба
        box([7.6, 6.6, 5.6], [8.4, 7, 6.1], 'polymer'),
        box([7.8, 6.6, 7.2], [8.2, 7.1, 7.6], 'metal_hi'),                           # спуск
        box([7.7, 9.7, 10.9], [8.3, 10.3, 11.4], 'metal'),                           # курок
    ]
    model('pistol', e, gun_display((8, 6, 9.5), 11, fp=(-2, 2.2, -2.5), fp_scale=0.9, tp_scale=0.75, gui_scale=1.05, gui_shift=(0, 1)))


def smg():
    e = [
        box([7, 8, 3], [9, 10.6, 13], 'metal'),                                      # ствольная коробка
        box([7.3, 10.6, 4], [8.7, 11, 12.5], 'metal_hi'),                            # планка
        box([7.5, 8.5, -0.5], [8.5, 9.5, 3], 'metal', {'north': 'muzzle'}),          # ствол
        box([7.2, 8.3, 0.5], [8.8, 9.7, 1.3], 'metal_hi'),                           # дульный тормоз
        box([7.15, 7, 3.2], [8.85, 8, 7.5], 'polymer'),                              # цевьё
        box([7.35, 2.6, 6.2], [8.65, 7, 7.8], 'polymer', rot=('x', 22.5, [8, 7, 7])),  # магазин
        box([7.2, 4, 10], [8.8, 7, 12], 'grip', rot=('x', -22.5, [8, 7, 11])),       # рукоять
        box([7.6, 6.2, 8], [8.4, 6.6, 10.4], 'polymer'),                             # скоба
        box([7.8, 6.6, 9], [8.2, 7.1, 9.4], 'metal_hi'),
        box([7.7, 11, 4], [8.3, 11.8, 4.6], 'metal'),                                # мушка
        box([7.5, 11, 11.5], [8.5, 11.7, 12.3], 'metal'),                            # целик
        box([7.6, 9.4, 13], [8.4, 10, 18.5], 'metal'),                               # складной приклад
        box([7.6, 7.6, 13], [8.4, 8.2, 18.5], 'metal'),
        box([7.2, 7, 18.5], [8.8, 10.4, 19.3], 'polymer'),                           # затыльник
        box([9, 9.3, 7.5], [9.6, 9.8, 8.4], 'metal_hi'),                             # рукоять затвора
    ]
    model('smg', e, gun_display((8, 6, 11), 20, fp=(-2, 1.6, -3), fp_pitch=3, fp_scale=0.85, tp_scale=0.75, gui_shift=(0, 0)))


def shotgun():
    e = [
        box([7.4, 9, -7], [8.6, 10.2, 8], 'metal', {'north': 'muzzle'}),             # ствол
        box([7.5, 7.9, -5], [8.5, 9, 8], 'metal_hi'),                                # подствольный магазин
        box([7.1, 7.5, -3], [8.9, 9.4, 3], 'wood', {'north': 'wood_dark', 'south': 'wood_dark'}),  # цевьё-помпа
        box([7.75, 10.2, -6.6], [8.25, 10.6, -6.1], 'brass'),                         # мушка-бусина
        box([7, 7.4, 8], [9, 10.5, 14], 'metal'),                                    # коробка
        box([7.2, 10.5, 8.5], [8.8, 10.8, 13.5], 'metal_hi'),
        box([7.6, 6.4, 10.5], [8.4, 6.8, 13], 'metal'),                              # скоба
        box([7.6, 6.8, 10.5], [8.4, 7.4, 11], 'metal'),
        box([7.8, 6.8, 11.6], [8.2, 7.4, 12], 'metal_hi'),
        box([7.1, 5.4, 13.5], [8.9, 8.6, 16], 'wood', rot=('x', -22.5, [8, 8, 14])),  # шейка приклада
        box([7.1, 6.8, 15], [8.9, 10.2, 22], 'wood'),                                # приклад
        box([7.05, 6.2, 21.5], [8.95, 10.3, 22.6], 'polymer'),                       # затыльник
    ]
    model('shotgun', e, gun_display((8, 6.5, 14.5), 29, fp=(-2.5, 1.2, -4), fp_scale=0.8, fp_pitch=5, tp_scale=0.8, gui_shift=(0, 0)))


def rifle():
    e = [
        box([7.6, 9, -8], [8.4, 9.8, 2], 'metal'),                                   # ствол
        box([7.45, 8.85, -9.5], [8.55, 9.95, -8], 'metal', {'north': 'muzzle'}),     # дульный тормоз
        box([7.55, 9.8, -6], [8.45, 11.2, -5.3], 'metal'),                           # мушка
        box([7.55, 9.8, -2], [8.45, 10.6, 4.5], 'metal'),                            # газовая трубка
        box([7.1, 8, 0], [8.9, 9.9, 6], 'wood'),                                     # цевьё
        box([7.3, 9.9, 1.5], [8.7, 10.8, 5.5], 'wood'),                              # накладка
        box([7.1, 7.8, 6], [8.9, 10.4, 14], 'metal'),                                # коробка
        box([7.3, 10.4, 6.5], [8.7, 10.9, 14], 'metal_hi'),                          # крышка
        box([7.55, 10.6, 6.2], [8.45, 11.4, 7], 'metal'),                            # прицельная планка
        box([8.9, 8.9, 9], [9.3, 9.5, 11.5], 'metal_hi'),                            # переводчик
        box([7.35, 4.6, 7.6], [8.65, 7.9, 9.9], 'metal', rot=('x', 22.5, [8, 7.8, 9])),   # рожок (верх)
        box([7.35, 2.2, 6.8], [8.65, 5.2, 9.0], 'metal', rot=('x', 45, [8, 5, 8])),      # рожок (низ, загиб)
        box([7.6, 6.4, 10], [8.4, 6.8, 12.3], 'metal'),                              # скоба
        box([7.8, 6.8, 10.8], [8.2, 7.3, 11.2], 'metal_hi'),
        box([7.3, 4.2, 12.4], [8.7, 7.8, 14.2], 'wood_dark', rot=('x', -22.5, [8, 7.8, 13])),  # пистолетная рукоять
        box([7.2, 7.2, 14], [8.8, 10, 17], 'wood'),                                  # приклад (шейка)
        box([7.2, 6.4, 17], [8.8, 10, 22], 'wood'),                                  # приклад
        box([7.15, 6.2, 22], [8.85, 10.1, 22.6], 'polymer'),                         # затыльник
    ]
    model('rifle', e, gun_display((8, 6.5, 13), 32, fp=(-2.5, 1.2, -4), fp_pitch=5, fp_scale=0.78, tp_scale=0.8, gui_shift=(0, 0)))


def sniper():
    e = [
        box([7.6, 9, -13], [8.4, 9.8, 4], 'metal', {'north': 'muzzle'}),             # длинный ствол
        box([7.45, 8.85, -14.5], [8.55, 9.95, -13], 'metal', {'north': 'muzzle'}),   # тормоз
        box([7.1, 8, 4], [8.9, 10.2, 12], 'metal'),                                  # коробка
        box([8.9, 9.4, 10], [10.2, 9.9, 10.5], 'metal_hi'),                          # затвор
        box([10.2, 9.2, 9.8], [10.8, 10.1, 10.7], 'metal'),                          # шарик затвора
        box([7.35, 11.3, 1.5], [8.65, 12.6, 11.5], 'metal', {'north': 'lens', 'south': 'lens'}),  # труба прицела
        box([7.05, 11, -0.6], [8.95, 12.9, 2], 'metal', {'north': 'lens'}),           # объектив
        box([7.15, 11.1, 11.2], [8.85, 12.8, 13], 'metal', {'south': 'lens'}),        # окуляр
        box([7.7, 12.6, 5.8], [8.3, 13.4, 6.8], 'metal_hi'),                          # барабанчик
        box([8.65, 11.65, 5.8], [9.3, 12.25, 6.8], 'metal_hi'),
        box([7.6, 10.2, 3.5], [8.4, 11.3, 4.5], 'metal'),                             # кольца
        box([7.6, 10.2, 9.5], [8.4, 11.3, 10.5], 'metal'),
        box([7.15, 7.6, -2], [8.85, 9, 4], 'olive_dark'),                             # цевьё
        box([7.3, 7.2, -5], [7.7, 7.8, 3], 'metal_hi'),                               # сошки сложены
        box([8.3, 7.2, -5], [8.7, 7.8, 3], 'metal_hi'),
        box([7.5, 6.6, 7], [8.5, 8, 9.5], 'metal'),                                   # магазин
        box([7.6, 6.6, 10], [8.4, 7, 12], 'metal'),                                   # скоба
        box([7.8, 7, 10.8], [8.2, 7.6, 11.2], 'metal_hi'),
        box([7.25, 4.6, 11.8], [8.75, 8, 13.6], 'olive', rot=('x', -22.5, [8, 8, 12.5])),  # рукоять
        box([7.15, 7.2, 12], [8.85, 10, 16], 'olive'),                               # приклад
        box([7.15, 5.8, 16], [8.85, 10.4, 21.5], 'olive'),
        box([7.35, 10.4, 15], [8.65, 11, 20], 'olive_dark'),                          # щека
        box([7.1, 5.6, 21.5], [8.9, 10.6, 22.3], 'polymer'),                          # затыльник
    ]
    model('sniper', e, gun_display((8, 6.8, 12.5), 37, fp=(-2.5, 1.0, -4.5), fp_pitch=5, fp_scale=0.75, tp_scale=0.8, gui_shift=(0, 0)))


def knife():
    e = [
        box([7.4, 7.2, 9], [8.6, 8.8, 14], 'grip'),                                  # рукоять
        box([7.3, 7.1, 14], [8.7, 8.9, 14.6], 'metal'),                              # навершие
        box([7, 6.6, 8.4], [9, 9.4, 9], 'metal'),                                    # гарда
        box([7.8, 7.4, 2], [8.2, 9.2, 8.4], 'steel'),                                # клинок
        box([7.8, 7.9, 0.6], [8.2, 9.2, 2], 'steel'),
        box([7.8, 8.5, -0.4], [8.2, 9.2, 0.6], 'steel'),                             # остриё
        box([7.75, 9.2, 4], [8.25, 9.5, 8.4], 'metal_hi'),                           # обух
    ]
    d = {
        'firstperson_righthand': {'rotation': [50, 12, 0], 'translation': [-1, 4.5, -1], 'scale': [0.75] * 3},
        'firstperson_lefthand': {'rotation': [50, 12, 0], 'translation': [-1, 4.5, -1], 'scale': [0.75] * 3},
        'thirdperson_righthand': {'rotation': [125, 0, 0], 'translation': [0, 3, -1], 'scale': [0.85] * 3},
        'thirdperson_lefthand': {'rotation': [125, 0, 0], 'translation': [0, 3, -1], 'scale': [0.85] * 3},
        'gui': {'rotation': [24, -118, 0], 'translation': [0, 0, 0], 'scale': [0.95] * 3},
        'ground': {'rotation': [0, 90, 0], 'translation': [0, 2, 0], 'scale': [0.5] * 3},
        'fixed': {'rotation': [0, 90, 0], 'translation': [0, 0, 0], 'scale': [1] * 3},
    }
    model('knife', e, d)


def throwable_display():
    return {
        'firstperson_righthand': {'rotation': [10, -25, 0], 'translation': [-1, 3, -2], 'scale': [0.6] * 3},
        'firstperson_lefthand': {'rotation': [10, -25, 0], 'translation': [-1, 3, -2], 'scale': [0.6] * 3},
        'thirdperson_righthand': {'rotation': [0, 0, 0], 'translation': [0, 1, 1], 'scale': [0.6] * 3},
        'thirdperson_lefthand': {'rotation': [0, 0, 0], 'translation': [0, 1, 1], 'scale': [0.6] * 3},
        'gui': {'rotation': [24, -135, 0], 'translation': [0, 0, 0], 'scale': [1.2] * 3},
        'ground': {'rotation': [0, 0, 0], 'translation': [0, 2, 0], 'scale': [0.6] * 3},
        'fixed': {'rotation': [0, 0, 0], 'translation': [0, 0, 0], 'scale': [1.2] * 3},
    }


def grenade():
    e = [
        box([6, 4, 6.5], [10, 9, 9.5], 'olive'),
        box([6.5, 4, 6], [9.5, 9, 10], 'olive'),
        box([6.3, 3.6, 6.3], [9.7, 9.4, 9.7], 'olive_dark'),
        box([7.3, 9.4, 7.3], [8.7, 10.6, 8.7], 'metal_hi'),                          # запал
        box([8.7, 8, 7.6], [9.2, 10.6, 8.4], 'metal_hi'),                            # рычаг
        box([6.2, 10, 7.9], [7.3, 11.2, 8.1], 'brass'),                              # кольцо
    ]
    model('grenade', e, throwable_display())


def molotov():
    e = [
        box([6.5, 2, 6.5], [9.5, 8.5, 9.5], 'glass'),                                # бутылка
        box([6.45, 2.1, 6.45], [9.55, 5.5, 9.55], 'fire'),                           # бензин
        box([7.2, 8.5, 7.2], [8.8, 11.5, 8.8], 'glass'),                             # горлышко
        box([7, 11.5, 7], [9, 13, 9], 'rag'),                                        # тряпка
        box([7.3, 13, 7.3], [8.7, 14.4, 8.7], 'fire', shade=False),                  # огонёк
    ]
    model('molotov', e, throwable_display())


# ------------------------------------------------------------------ звуки затвора
def write(name, sig, sr=44100):
    sig = np.clip(sig, -1, 1)
    tmp = f'/tmp/{name}.wav'
    with wave.open(tmp, 'wb') as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(sr)
        w.writeframes((sig * 32767).astype(np.int16).tobytes())
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', tmp, '-c:a', 'libvorbis', '-q:a', '6', os.path.join(SND, name + '.ogg')], check=True)


def click(t, at, f1, f2, decay, rng, amp=1.0):
    """Механический щелчок: два металлических тона + короткий шум."""
    k = np.clip(t - at, 0, None)
    on = (t >= at).astype(float)
    n = rng.standard_normal(len(t))
    hp = n - np.convolve(n, np.ones(6) / 6, mode='same')
    return on * amp * (np.sin(2 * np.pi * f1 * k) * 0.45 + np.sin(2 * np.pi * f2 * k) * 0.3 + hp * 0.5) * np.exp(-k * decay)


def slide(t, a, b, rng, amp=0.15):
    """Шорох металла по металлу между a и b."""
    n = rng.standard_normal(len(t))
    hp = n - np.convolve(n, np.ones(3) / 3, mode='same')
    env = ((t > a) & (t < b)) * np.sin(np.clip((t - a) / (b - a), 0, 1) * np.pi)
    return hp * env * amp


def reload_sounds():
    sr = 44100
    rng = np.random.default_rng(99)
    # (имя, длина, [(время, f1, f2, затухание, громкость)], [(шорох от, до)])
    kinds = {
        'pistol_reload': (1.4, [(0.10, 1800, 3100, 70, 0.7), (0.55, 1500, 2600, 60, 0.9), (1.05, 2600, 4200, 90, 1.0), (1.13, 2100, 3600, 80, 0.8)], [(0.12, 0.5), (0.95, 1.05)]),
        'smg_reload': (1.9, [(0.15, 1600, 2900, 60, 0.8), (0.75, 1400, 2500, 55, 1.0), (1.45, 2400, 4000, 85, 1.0), (1.55, 2000, 3400, 75, 0.9)], [(0.2, 0.7), (1.3, 1.45)]),
        'rifle_reload': (2.2, [(0.2, 1200, 2300, 50, 0.9), (0.95, 1100, 2100, 45, 1.0), (1.0, 1700, 2900, 60, 0.7), (1.75, 2200, 3600, 80, 1.0), (1.85, 1800, 3100, 70, 0.9)], [(0.25, 0.9), (1.55, 1.75)]),
        'shotgun_reload': (2.6, [(0.2 + i * 0.42, 1300, 2600, 60, 0.85) for i in range(4)] + [(2.15, 900, 1900, 40, 1.0), (2.32, 1100, 2200, 45, 1.0)], [(0.1 + i * 0.42, 0.18 + i * 0.42) for i in range(4)] + [(2.15, 2.32)]),
        'sniper_reload': (2.8, [(0.25, 1500, 2800, 60, 0.9), (0.45, 1200, 2300, 50, 0.8), (1.2, 1300, 2500, 55, 1.0), (2.0, 1700, 3000, 70, 1.0), (2.3, 2000, 3400, 75, 1.0)], [(0.3, 0.45), (1.95, 2.3)]),
    }
    for name, (T, clicks, slides) in kinds.items():
        t = np.arange(int(sr * T)) / sr
        s = np.zeros_like(t)
        for at, f1, f2, dec, amp in clicks:
            s += click(t, at, f1 * rng.uniform(0.95, 1.05), f2, dec, rng, amp)
        for a, b in slides:
            s += slide(t, a, b, rng)
        write(name, np.tanh(s * 1.2) * 0.8)
    t = np.arange(int(sr * 0.25)) / sr
    write('gun_dry', np.tanh(click(t, 0.01, 3000, 5200, 140, rng, 1.0) * 1.5) * 0.7)


if __name__ == '__main__':
    atlas()
    # пистолет, ПП, дробовик, «АК», снайперка и вспышка теперь — tools/gen_gun_geo.py (GeckoLib, кости, анимации)
    knife(); grenade(); molotov()
    reload_sounds()
    print('guns ok')
