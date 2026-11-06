#!/usr/bin/env python3
"""Мелочи арсенала «Калибра»: мачете (кубоиды, атлас gun_atlas), выстрел к гранатомёту (иконка),
иконка приложения «Охрана» для телефона. Стволы — tools/gen_gun_geo.py, звуки — tools/gen_gun_sfx.py new.

Запуск из папки novograd: python3 tools/gen_armory.py
"""
import json, os, sys
import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_guns as gg  # noqa: E402

ROOT = gg.ROOT


def machete():
    e = [
        gg.box([7.4, 7.2, 10], [8.6, 8.8, 15], 'wood_dark'),
        gg.box([7.3, 7.1, 15], [8.7, 8.9, 15.5], 'metal'),
        gg.box([7.2, 6.9, 9.4], [8.8, 9.1, 10], 'metal'),
        gg.box([7.85, 7.3, 1.0], [8.15, 9.6, 9.4], 'steel'),
        gg.box([7.85, 7.6, -2.0], [8.15, 9.9, 1.0], 'steel'),
        gg.box([7.85, 8.4, -3.2], [8.15, 9.9, -2.0], 'steel'),
        gg.box([7.8, 9.6, 2.0], [8.2, 9.85, 9.4], 'metal_hi'),
    ]
    d = {
        'firstperson_righthand': {'rotation': [50, 12, 0], 'translation': [-1, 4.5, -1], 'scale': [0.75] * 3},
        'firstperson_lefthand': {'rotation': [50, 12, 0], 'translation': [-1, 4.5, -1], 'scale': [0.75] * 3},
        'thirdperson_righthand': {'rotation': [125, 0, 0], 'translation': [0, 3, -1], 'scale': [0.85] * 3},
        'thirdperson_lefthand': {'rotation': [125, 0, 0], 'translation': [0, 3, -1], 'scale': [0.85] * 3},
        'gui': gg.fit(e, 40, 0.95),
        'ground': {'rotation': [0, 90, 0], 'translation': [0, 2, 0], 'scale': [0.5] * 3},
        'fixed': {'rotation': [0, 90, 0], 'translation': [0, 0, 0], 'scale': [1] * 3},
    }
    m = {'credit': 'Novograd: tools/gen_armory.py', 'texture_size': [64, 64],
         'textures': {'0': 'novograd:item/gun_atlas', 'particle': 'novograd:item/gun_atlas'}, 'elements': e, 'display': d}
    with open(os.path.join(ROOT, 'models', 'item', 'machete.json'), 'w') as f:
        json.dump(m, f, indent=1)


def rocket():
    """Иконка 16×16: выстрел ПГ — оливковая боевая часть, стальной наконечник, хвост."""
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    # по диагонали снизу-слева вверх-вправо
    for i in range(12):
        x, y = 2 + i, 13 - i
        if i < 4:
            c = (70, 74, 52, 255); r = 1
        elif i < 9:
            c = (96, 104, 66, 255); r = 2
        else:
            c = (150, 156, 164, 255); r = 1 if i < 11 else 0
        d.ellipse([x - r, y - r, x + r, y + r], fill=c)
    d.point([(1, 14), (2, 15), (0, 13)], fill=(190, 150, 60, 255))
    img.save(os.path.join(ROOT, 'textures', 'item', 'rocket.png'))
    with open(os.path.join(ROOT, 'models', 'item', 'rocket.json'), 'w') as f:
        json.dump({'parent': 'minecraft:item/generated', 'textures': {'layer0': 'novograd:item/rocket'}}, f, indent=1)


def guard_icon():
    """Иконка телефона «Охрана»: тёмный скруглённый квадрат, бирюзовый щит со звездой."""
    s = 64
    img = Image.new('RGBA', (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([2, 2, s - 3, s - 3], radius=12, fill=(18, 16, 24, 255), outline=(40, 230, 255, 255), width=2)
    shield = [(32, 12), (48, 18), (46, 36), (32, 52), (18, 36), (16, 18)]
    d.polygon(shield, fill=(40, 230, 255, 255))
    inner = [(32, 17), (43, 21), (41, 35), (32, 46), (23, 35), (21, 21)]
    d.polygon(inner, fill=(18, 60, 80, 255))
    import math
    pts = []
    for k in range(10):
        r = 8 if k % 2 == 0 else 3.4
        a = -math.pi / 2 + k * math.pi / 5
        pts.append((32 + r * math.cos(a), 31 + r * math.sin(a)))
    d.polygon(pts, fill=(255, 196, 40, 255))
    img.save(os.path.join(ROOT, 'textures', 'gui', 'ui', 'app', 'guard.png'))


if __name__ == '__main__':
    machete()
    rocket()
    guard_icon()
    print('armory ok')
