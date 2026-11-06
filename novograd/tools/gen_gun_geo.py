#!/usr/bin/env python3
"""Оружие для GeckoLib: подробные модели с костями (затвор, магазин, спуск, помпа, затвор снайперки),
расписанные текстуры (металл с потёртостями, дерево, полимер), модели предметов, данные для анимаций.

Всё своё, без чужих ассетов. Запуск из папки novograd: python3 tools/gen_gun_geo.py

Пишет:
  geo/guns/<gun>.geo.json, animations/guns/<gun>.animation.json, textures/item/guns/<gun>.png,
  models/item/<gun>.json (parent builtin/entity), guns/meta.json (точки рук, дула, прицела, кости),
  textures/misc/muzzle_flash.png (4 варианта звезды), textures/misc/gun_bits.png (гильзы, магазин).

Координаты в коде — «пиксели модели предмета», как в JSON-моделях: центр (8, 8, 8), ствол смотрит на север (-z),
верх +y, правая сторона оружия +x. Повороты — в градусах по правилу правой руки вокруг осей.
"""
import json, math, os
import numpy as np
from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'assets', 'novograd')
D = 8  # текселей на пиксель модели

# ---------------------------------------------------------------- материалы
MAT = {
    'steel':      dict(c=(46, 50, 56), kind='metal'),
    'steel_dark': dict(c=(30, 32, 36), kind='metal'),
    'steel_hi':   dict(c=(98, 104, 112), kind='metal'),
    'bright':     dict(c=(150, 156, 164), kind='metal'),
    'polymer':    dict(c=(33, 34, 37), kind='polymer'),
    'olive':      dict(c=(84, 90, 62), kind='polymer'),
    'olive_dark': dict(c=(58, 63, 43), kind='polymer'),
    'tan':        dict(c=(150, 128, 92), kind='polymer'),
    'wood':       dict(c=(134, 74, 36), kind='wood'),
    'wood_dark':  dict(c=(88, 48, 24), kind='wood'),
    'rubber':     dict(c=(22, 22, 24), kind='rubber'),
    'brass':      dict(c=(196, 152, 58), kind='metal'),
    'red':        dict(c=(160, 28, 26), kind='polymer'),
    'glass':      dict(c=(30, 70, 110), kind='glass'),
    'bore':       dict(c=(12, 12, 13), kind='bore'),
    'bakelite':   dict(c=(120, 52, 30), kind='polymer'),
}


class Gun:
    def __init__(self, name):
        self.name = name
        self.bones = {}
        self.order = []
        self.meta = {}
        self.bone('root', None, (8, 8, 8))

    def bone(self, name, parent, pivot):
        self.bones[name] = {'name': name, 'parent': parent, 'pivot': pivot, 'cubes': []}
        self.order.append(name)
        return name

    def box(self, bone, a, b, mat, rot=None, decals=None, faces=None):
        """a, b — углы; rot = (ось, градусы, центр) или None; decals = {грань: [(тип, параметры)]}; faces — материал по граням."""
        a, b = [min(a[i], b[i]) for i in range(3)], [max(a[i], b[i]) for i in range(3)]
        self.bones[bone]['cubes'].append(dict(a=a, b=b, mat=mat, rot=rot, decals=decals or {}, faces=faces or {}))

    def octo(self, bone, c, z0, z1, r, mat, decals=None, faces=None, axis='z'):
        """Восьмигранный «цилиндр» вдоль оси: квадрат + квадрат, повёрнутый на 45°."""
        if axis == 'z':
            a, b = (c[0] - r, c[1] - r, z0), (c[0] + r, c[1] + r, z1)
            self.box(bone, a, b, mat, decals=decals, faces=faces)
            self.box(bone, a, b, mat, rot=('z', 45, (c[0], c[1], (z0 + z1) / 2)), faces=faces)
        elif axis == 'y':  # c = (x, z)
            a, b = (c[0] - r, z0, c[1] - r), (c[0] + r, z1, c[1] + r)
            self.box(bone, a, b, mat, decals=decals, faces=faces)
            self.box(bone, a, b, mat, rot=('y', 45, (c[0], (z0 + z1) / 2, c[1])), faces=faces)


# ---------------------------------------------------------------- геометрия
def pistol():
    g = Gun('pistol')
    g.bone('frame', 'root', (8, 8, 8))
    g.bone('slide', 'root', (8, 9.4, 6))
    g.bone('mag', 'root', (8, 7.3, 9.4))
    g.bone('trigger', 'root', (8, 7.3, 6.6))
    g.bone('hammer', 'root', (8, 9.5, 11.0))
    gr = ('x', -16, (8, 7.3, 9.4))
    # затвор-кожух с фасками и прорезями
    g.box('slide', (7.0, 8.4, 1.9), (9.0, 10.2, 11.0), 'steel', decals={
        'east': [('serr', 0.80, 0.97), ('port', 0.22, 0.5, 0.45, 1.0)], 'west': [('serr', 0.80, 0.97), ('text', 0.3, 0.6)]})
    g.box('slide', (7.2, 10.2, 2.1), (8.8, 10.45, 10.9), 'steel', decals={'up': [('stripe', 0.45, 0.55)]})
    g.box('slide', (7.1, 8.5, 1.75), (8.9, 10.1, 1.9), 'steel', decals={'north': [('bore', 0.5, 0.43, 0.28)]})
    g.box('slide', (7.55, 10.3, 3.9), (8.45, 10.48, 6.0), 'bright')  # ствол в окне
    g.box('slide', (7.82, 10.45, 2.2), (8.18, 10.92, 2.75), 'steel_dark', decals={'south': [('dot', 0.5, 0.3, (240, 240, 240))]})
    g.box('slide', (7.2, 10.45, 10.1), (7.72, 11.0, 10.75), 'steel_dark', decals={'north': [('dot', 0.7, 0.35, (240, 240, 240))]})
    g.box('slide', (8.28, 10.45, 10.1), (8.8, 11.0, 10.75), 'steel_dark', decals={'north': [('dot', 0.3, 0.35, (240, 240, 240))]})
    g.box('slide', (7.72, 10.45, 10.1), (8.28, 10.62, 10.75), 'steel_dark')
    # рамка
    g.box('frame', (7.1, 7.3, 2.1), (8.9, 8.45, 10.7), 'polymer')
    g.box('frame', (7.25, 6.85, 2.3), (8.75, 7.3, 5.3), 'polymer', decals={'east': [('rail', 6)], 'west': [('rail', 6)]})
    g.box('frame', (7.55, 5.85, 5.0), (8.45, 7.3, 5.45), 'polymer')
    g.box('frame', (7.55, 5.7, 5.0), (8.45, 6.1, 8.3), 'polymer')
    g.box('frame', (7.15, 2.6, 8.05), (8.85, 7.35, 10.85), 'polymer', rot=gr,
          decals={'east': [('checker',)], 'west': [('checker',)], 'south': [('checker',)]})
    g.box('frame', (7.25, 7.2, 10.4), (8.75, 7.95, 11.35), 'polymer')  # «бобровый хвост»
    g.box('frame', (6.95, 8.25, 5.6), (7.1, 8.55, 7.8), 'steel_dark')  # затворная задержка
    g.box('frame', (8.9, 7.55, 8.6), (9.02, 8.1, 9.2), 'steel_dark')  # кнопка магазина
    # магазин (виден пятка, весь — когда выпадает)
    g.box('mag', (7.32, 2.7, 8.35), (8.68, 7.25, 10.5), 'steel', rot=gr, decals={'east': [('holes', 4)], 'west': [('holes', 4)]})
    g.box('mag', (7.45, 7.0, 8.55), (8.55, 7.32, 9.9), 'brass', rot=gr)
    g.box('mag', (7.05, 2.15, 7.95), (8.95, 2.65, 11.05), 'polymer', rot=gr)
    g.box('trigger', (7.85, 6.25, 6.35), (8.15, 7.3, 6.75), 'steel_dark')
    g.box('hammer', (7.72, 9.4, 10.95), (8.28, 10.15, 11.45), 'steel_dark')
    g.meta = dict(grip=(8, 5.4, 9.9), support=(8.1, 4.5, 9.2), muzzle=(8, 9.25, 1.6), eject=(9.0, 10.1, 5.0),
                  sight=(8, 10.78, 10.4), hip=(0.0, -2.0), mag_dir=(0, -0.96, 0.27), mag_hand=(8, 3.0, 9.6),
                  slide_kick=1.5, length=11)
    return g


def smg():
    g = Gun('smg')
    g.bone('body', 'root', (8, 8, 8))
    g.bone('bolt', 'root', (9, 9.4, 6.8))
    g.bone('handle', 'root', (6.7, 9.8, 1.8))
    g.bone('mag', 'root', (8, 7.8, 5.9))
    g.bone('trigger', 'root', (8, 7.8, 8.8))
    g.box('body', (7.0, 7.8, 3.0), (9.0, 10.4, 12.5), 'steel', decals={
        'east': [('port', 0.32, 0.55, 0.5, 0.95), ('text', 0.62, 0.45)], 'west': [('rivets', 3)]})
    g.box('body', (7.3, 10.4, 4.0), (8.7, 10.75, 12.0), 'steel_dark', decals={'up': [('railtop', 10)], 'east': [('rail', 10)], 'west': [('rail', 10)]})
    g.box('body', (7.05, 7.6, -0.6), (8.95, 10.0, 3.0), 'polymer', decals={'east': [('vents', 3)], 'west': [('vents', 3)]})
    g.box('body', (6.4, 9.6, -0.4), (7.05, 10.2, 2.9), 'steel')  # трубка взвода
    g.octo('body', (8, 8.9), -3.0, -0.6, 0.42, 'steel_dark')
    g.octo('body', (8, 8.9), -3.4, -2.2, 0.62, 'steel', decals={'north': [('bore', 0.5, 0.5, 0.3)]})
    g.box('body', (7.6, 10.0, 0.0), (8.4, 11.35, 0.7), 'steel', decals={'north': [('ring',)]})
    g.box('body', (7.92, 10.0, 0.1), (8.08, 11.12, 0.6), 'steel_dark')
    g.box('body', (7.35, 10.75, 11.0), (7.8, 11.42, 11.8), 'steel')
    g.box('body', (8.2, 10.75, 11.0), (8.65, 11.42, 11.8), 'steel')
    g.box('body', (7.8, 10.75, 11.0), (8.2, 11.05, 11.8), 'steel')
    g.box('body', (7.15, 3.6, 9.6), (8.85, 7.85, 11.6), 'polymer', rot=('x', -15, (8, 7.8, 10.6)),
          decals={'east': [('checker',)], 'west': [('checker',)]})
    g.box('body', (7.55, 6.4, 7.6), (8.45, 6.8, 10.0), 'polymer')
    g.box('body', (7.55, 6.8, 7.6), (8.45, 7.8, 8.0), 'polymer')
    g.box('body', (6.9, 8.6, 10.4), (7.05, 9.0, 11.3), 'steel_hi')  # переводчик
    g.box('body', (7.65, 9.4, 12.5), (8.35, 9.8, 18.0), 'steel')  # приклад-рамка
    g.box('body', (7.65, 7.9, 12.5), (8.35, 8.3, 18.0), 'steel')
    g.box('body', (7.1, 7.2, 18.0), (8.9, 10.5, 18.8), 'rubber', decals={'south': [('grooves', 5)]})
    g.box('body', (7.45, 7.75, 5.0), (8.55, 7.85, 6.8), 'steel_dark')  # горловина
    g.box('bolt', (8.92, 9.0, 6.0), (9.04, 9.8, 7.6), 'bright')
    g.box('handle', (6.2, 9.65, 1.6), (6.5, 10.15, 2.2), 'steel_dark')
    g.box('handle', (5.9, 9.75, 1.65), (6.2, 10.05, 2.15), 'polymer')
    # магазин с изгибом
    g.box('mag', (7.3, 5.0, 5.15), (8.7, 7.8, 6.65), 'steel', decals={'east': [('ribs', 3)], 'west': [('ribs', 3)]})
    g.box('mag', (7.3, 2.1, 5.0), (8.7, 5.15, 6.5), 'steel', rot=('x', 12, (8, 5.0, 5.9)), decals={'east': [('ribs', 3)], 'west': [('ribs', 3)]})
    g.box('mag', (7.2, 1.8, 4.85), (8.8, 2.25, 6.6), 'polymer', rot=('x', 12, (8, 5.0, 5.9)))
    g.box('mag', (7.45, 7.8, 5.35), (8.55, 7.95, 6.4), 'brass')
    g.box('trigger', (7.85, 6.85, 8.55), (8.15, 7.8, 8.95), 'steel_dark')
    g.meta = dict(grip=(8, 5.9, 10.9), support=(8, 7.3, 1.0), muzzle=(8, 8.9, -3.5), eject=(9.0, 9.4, 6.8),
                  sight=(8, 11.12, 11.4), hip=(0.0, -2.0), mag_dir=(0, -0.98, -0.2), mag_hand=(8, 4.2, 5.8),
                  slide_kick=1.3, length=22)
    return g


def shotgun():
    g = Gun('shotgun')
    g.bone('body', 'root', (8, 8, 8))
    g.bone('pump', 'root', (8, 8.4, -0.5))
    g.bone('trigger', 'root', (8, 7.4, 7.3))
    g.bone('shell', 'root', (8, 7.1, 5.3))
    g.octo('body', (8, 9.6), -9.0, 3.0, 0.62, 'steel', decals={'north': [('bore', 0.5, 0.5, 0.36)]})
    g.box('body', (7.8, 10.2, -8.7), (8.2, 10.36, 3.0), 'steel_dark', decals={'up': [('notches', 18)]})
    g.box('body', (7.85, 10.3, -8.7), (8.15, 10.62, -8.3), 'brass')
    g.octo('body', (8, 8.35), -7.1, 3.0, 0.55, 'steel_dark')
    g.box('body', (7.35, 7.75, -7.5), (8.65, 8.95, -7.05), 'steel', decals={'north': [('dot', 0.5, 0.5, (90, 94, 100))]})
    g.box('body', (7.6, 8.9, -6.6), (8.4, 9.2, -6.0), 'steel')
    g.box('body', (7.0, 7.4, 2.6), (9.0, 10.4, 9.5), 'steel', decals={
        'east': [('port', 0.18, 0.45, 0.62, 0.9), ('text', 0.75, 0.4)], 'west': [('text', 0.5, 0.45)], 'down': [('slot', 0.25, 0.65)]})
    g.box('body', (7.2, 10.4, 2.8), (8.8, 10.6, 9.3), 'steel_dark', decals={'up': [('stripe', 0.42, 0.58)]})
    g.box('body', (7.55, 6.3, 6.4), (8.45, 6.7, 9.2), 'steel')
    g.box('body', (7.55, 6.7, 6.4), (8.45, 7.4, 6.8), 'steel')
    g.box('body', (7.1, 5.6, 9.3), (8.9, 9.1, 12.6), 'wood', rot=('x', -18, (8, 9.0, 10.0)))
    g.box('body', (7.1, 6.9, 12.0), (8.9, 10.2, 17.0), 'wood')
    g.box('body', (7.12, 6.3, 16.9), (8.88, 10.05, 21.4), 'wood')
    g.box('body', (7.05, 6.1, 21.4), (8.95, 10.15, 22.2), 'rubber', decals={'south': [('grooves', 6)]})
    g.box('body', (8.9, 8.2, 8.2), (9.05, 8.6, 9.0), 'steel_dark')  # предохранитель
    g.box('pump', (7.05, 7.35, -3.6), (8.95, 9.3, 2.4), 'polymer', decals={'east': [('grooves_v', 7)], 'west': [('grooves_v', 7)], 'down': [('grooves_h', 7)]})
    g.box('pump', (7.25, 7.2, -3.4), (8.75, 7.4, 2.2), 'polymer')
    g.box('trigger', (7.85, 6.75, 7.1), (8.15, 7.4, 7.5), 'steel_dark')
    g.box('shell', (7.62, 6.8, 4.2), (8.38, 7.56, 5.9), 'red', decals={'up': [('stripe_h', 0.1, 0.25)]})
    g.box('shell', (7.58, 6.76, 5.9), (8.42, 7.6, 6.35), 'brass')
    g.meta = dict(grip=(8, 6.7, 10.9), support=(8, 7.25, -0.6), muzzle=(8, 9.6, -9.2), eject=(9.0, 9.4, 5.2),
                  sight=(8, 10.5, 9.0), hip=(0.0, -2.0), mag_dir=(0, -1, 0), mag_hand=(8, 6.9, 5.2),
                  slide_kick=2.6, length=31)
    return g


def rifle():
    g = Gun('rifle')
    g.bone('body', 'root', (8, 8, 8))
    g.bone('bolt', 'root', (9.0, 9.7, 11.0))
    g.bone('mag', 'root', (8, 7.7, 5.0))
    g.bone('trigger', 'root', (8, 7.6, 9.4))
    g.octo('body', (8, 9.3), -8.0, 1.0, 0.4, 'steel_dark')
    g.box('body', (7.45, 8.75, -10.0), (8.55, 9.85, -8.0), 'steel', decals={'east': [('slots', 3)], 'west': [('slots', 3)], 'north': [('bore', 0.5, 0.5, 0.3)]})
    g.box('body', (7.5, 8.9, -7.6), (8.5, 10.3, -6.8), 'steel')
    g.box('body', (7.48, 10.3, -7.4), (7.64, 11.45, -7.0), 'steel')
    g.box('body', (8.36, 10.3, -7.4), (8.52, 11.45, -7.0), 'steel')
    g.box('body', (7.92, 10.3, -7.3), (8.08, 11.2, -7.1), 'steel_dark')
    g.box('body', (7.55, 9.55, -5.3), (8.45, 10.6, -4.4), 'steel')
    g.octo('body', (8, 10.15), -4.4, 1.4, 0.36, 'steel')
    g.box('body', (7.25, 9.85, -3.6), (8.75, 10.75, 1.0), 'wood', decals={'up': [('stripe', 0.47, 0.53)]})
    g.box('body', (7.05, 7.9, -3.8), (8.95, 9.9, 1.6), 'wood', decals={'east': [('fgroove',)], 'west': [('fgroove',)]})
    g.box('body', (7.05, 9.8, 0.9), (8.95, 10.3, 1.7), 'steel')
    g.box('body', (7.05, 7.7, 1.6), (8.95, 10.3, 13.6), 'steel', decals={'east': [('rivets', 4), ('port', 0.25, 0.55, 0.62, 0.98)], 'west': [('rivets', 4), ('text', 0.55, 0.35)]})
    g.box('body', (7.15, 10.3, 3.2), (8.85, 10.8, 13.5), 'steel_hi', decals={'up': [('ribs_z', 3)]})
    g.box('body', (7.5, 10.8, 3.4), (8.5, 10.95, 13.3), 'steel_hi')
    g.box('body', (7.4, 10.3, 1.8), (8.6, 11.0, 3.2), 'steel')
    g.box('body', (7.5, 11.0, 2.2), (7.8, 11.4, 3.6), 'steel_dark')
    g.box('body', (8.2, 11.0, 2.2), (8.5, 11.4, 3.6), 'steel_dark')
    g.box('body', (7.8, 11.0, 2.2), (8.2, 11.15, 3.6), 'steel_dark')
    g.box('body', (8.95, 8.9, 6.0), (9.1, 9.8, 10.8), 'steel_dark')  # переводчик
    g.box('body', (7.55, 6.3, 8.0), (8.45, 6.7, 11.4), 'steel')
    g.box('body', (7.55, 6.7, 8.0), (8.45, 7.7, 8.4), 'steel')
    g.box('body', (7.25, 3.6, 10.8), (8.75, 7.75, 12.8), 'wood_dark', rot=('x', -16, (8, 7.7, 11.8)), decals={'east': [('checker',)], 'west': [('checker',)]})
    g.box('body', (7.15, 7.4, 13.6), (8.85, 10.1, 16.6), 'wood')
    g.box('body', (7.17, 6.0, 14.0), (8.83, 9.95, 21.8), 'wood', rot=('x', 7, (8, 9.95, 14.0)))
    g.box('body', (7.1, 5.4, 21.6), (8.9, 9.95, 22.3), 'steel', rot=('x', 7, (8, 9.95, 14.0)))
    g.box('bolt', (8.95, 9.5, 9.0), (9.15, 9.9, 12.4), 'bright')
    g.box('bolt', (9.15, 9.42, 11.3), (9.95, 9.98, 11.9), 'steel_hi')
    # изогнутый магазин
    g.box('mag', (7.3, 5.5, 3.8), (8.7, 7.75, 6.2), 'bakelite', rot=('x', 8, (8, 7.7, 5.0)), decals={'east': [('ribs', 2)], 'west': [('ribs', 2)]})
    g.box('mag', (7.32, 3.5, 3.1), (8.68, 5.8, 5.55), 'bakelite', rot=('x', 20, (8, 5.6, 4.6)), decals={'east': [('ribs', 2)], 'west': [('ribs', 2)]})
    g.box('mag', (7.34, 1.6, 2.15), (8.66, 3.8, 4.55), 'bakelite', rot=('x', 32, (8, 3.7, 3.5)), decals={'east': [('ribs', 2)], 'west': [('ribs', 2)]})
    g.box('mag', (7.25, 1.3, 2.0), (8.75, 1.75, 4.7), 'steel', rot=('x', 32, (8, 3.7, 3.5)))
    g.box('mag', (7.45, 7.7, 4.1), (8.55, 7.9, 5.9), 'brass')
    g.box('trigger', (7.85, 6.8, 9.2), (8.15, 7.7, 9.6), 'steel_dark')
    g.meta = dict(grip=(8, 6.0, 12.4), support=(8, 7.75, -1.4), muzzle=(8, 9.3, -10.2), eject=(9.0, 10.0, 6.4),
                  sight=(8, 11.18, 3.0), hip=(0.0, -2.0), mag_dir=(0, -0.92, -0.38), mag_hand=(8, 3.6, 4.0),
                  slide_kick=2.4, length=33)
    return g


def sniper():
    g = Gun('sniper')
    g.bone('body', 'root', (8, 8, 8))
    g.bone('bolt', 'root', (8, 9.6, 11.0))
    g.bone('mag', 'root', (8, 7.4, 8.4))
    g.bone('trigger', 'root', (8, 7.2, 11.0))
    g.octo('body', (8, 9.3), -13.2, -2.0, 0.42, 'steel_dark')
    g.octo('body', (8, 9.3), -2.0, 4.0, 0.52, 'steel_dark')
    g.box('body', (7.4, 8.7, -15.0), (8.6, 9.9, -13.0), 'steel', decals={'east': [('slots', 3)], 'west': [('slots', 3)], 'north': [('bore', 0.5, 0.5, 0.3)]})
    g.box('body', (7.1, 7.5, -4.0), (8.9, 9.35, 6.0), 'olive', decals={'east': [('vents', 4)], 'west': [('vents', 4)]})
    g.box('body', (7.05, 7.1, 6.0), (8.95, 9.5, 11.6), 'olive')
    g.octo('body', (8, 9.6), 3.5, 12.0, 0.75, 'steel', decals={'east': [('port', 0.62, 0.3, 0.85, 0.85)]})
    g.box('body', (7.2, 4.0, 11.4), (8.8, 7.4, 13.4), 'olive', rot=('x', -10, (8, 7.3, 12.4)), decals={'east': [('checker',)], 'west': [('checker',)]})
    g.box('body', (7.15, 6.6, 11.6), (8.85, 9.2, 15.5), 'olive')
    g.box('body', (7.15, 9.0, 13.5), (8.85, 10.6, 20.5), 'olive_dark', decals={'up': [('stripe', 0.3, 0.7)]})
    g.box('body', (7.15, 5.2, 15.5), (8.85, 6.6, 21.6), 'olive')
    g.box('body', (7.15, 5.2, 20.5), (8.85, 10.6, 21.6), 'olive')
    g.box('body', (7.1, 5.0, 21.6), (8.9, 10.8, 22.4), 'rubber', decals={'south': [('grooves', 6)]})
    g.box('body', (7.55, 6.5, 10.4), (8.45, 6.9, 12.4), 'steel')
    g.box('body', (7.55, 6.9, 10.4), (8.45, 7.2, 10.8), 'steel')
    # прицел
    for z0 in (5.0, 9.6):
        g.box('body', (7.5, 10.2, z0), (8.5, 11.7, z0 + 0.8), 'steel_dark')
        g.box('body', (7.35, 10.2, z0 - 0.1), (8.65, 10.5, z0 + 0.9), 'steel_dark')
    g.octo('body', (8, 12.2), 2.0, 11.8, 0.62, 'steel_dark')
    g.octo('body', (8, 12.2), 1.0, 2.0, 0.82, 'steel_dark')
    g.octo('body', (8, 12.2), -0.6, 1.0, 1.0, 'steel_dark', faces={'north': 'glass'}, decals={'north': [('lens',)]})
    g.octo('body', (8, 12.2), 11.6, 13.7, 0.86, 'steel_dark', faces={'south': 'glass'}, decals={'south': [('lens',)]})
    g.box('body', (7.55, 12.75, 6.8), (8.45, 13.55, 7.8), 'steel', decals={'up': [('ring',)]})
    g.box('body', (8.55, 11.75, 6.8), (9.35, 12.65, 7.8), 'steel')
    # сошки сложены
    g.box('body', (7.25, 6.95, -9.0), (7.6, 7.3, -2.2), 'steel_hi')
    g.box('body', (8.4, 6.95, -9.0), (8.75, 7.3, -2.2), 'steel_hi')
    g.box('body', (7.2, 7.0, -2.6), (8.8, 7.55, -1.4), 'steel')
    g.octo('bolt', (8, 9.6), 9.0, 12.7, 0.5, 'bright')
    g.box('bolt', (8.5, 9.4, 11.0), (10.1, 9.8, 11.4), 'steel_hi')
    g.box('bolt', (9.9, 9.15, 10.8), (10.7, 10.05, 11.6), 'steel_dark')
    g.box('mag', (7.4, 6.3, 7.2), (8.6, 7.4, 9.6), 'steel', decals={'east': [('holes', 2)]})
    g.box('mag', (7.3, 6.1, 7.1), (8.7, 6.4, 9.7), 'polymer')
    g.box('mag', (7.55, 7.35, 7.5), (8.45, 7.55, 9.3), 'brass')
    g.box('trigger', (7.85, 6.5, 10.9), (8.15, 7.2, 11.25), 'steel_dark')
    g.meta = dict(grip=(8, 6.1, 12.9), support=(8, 7.2, 1.2), muzzle=(8, 9.3, -15.2), eject=(9.0, 10.0, 10.4),
                  sight=(8, 12.2, 13.7), hip=(0.0, -2.0), mag_dir=(0, -1, 0), mag_hand=(8, 6.3, 8.4),
                  bolt_hand=(10.3, 9.6, 11.2), slide_kick=2.4, length=38)
    return g


# ---------------------------------------------------------------- текстуры
def face_dims(cube, face):
    a, b = cube['a'], cube['b']
    sx, sy, sz = b[0] - a[0], b[1] - a[1], b[2] - a[2]
    return {'north': (sx, sy), 'south': (sx, sy), 'east': (sz, sy), 'west': (sz, sy), 'up': (sx, sz), 'down': (sx, sz)}[face]


def blur(a, k):
    if k <= 1:
        return a
    h, w = a.shape
    p = np.pad(a, ((k, k), (k, k)), mode='wrap' if min(h, w) > k else 'edge')
    c = np.cumsum(np.cumsum(p, 0), 1)
    c = np.pad(c, ((1, 0), (1, 0)))
    r = k // 2
    y0, x0 = k - r, k - r
    s = c[y0 + k:y0 + k + h, x0 + k:x0 + k + w] - c[y0:y0 + h, x0 + k:x0 + k + w] - c[y0 + k:y0 + k + h, x0:x0 + w] + c[y0:y0 + h, x0:x0 + w]
    return s / (k * k)


def paint(mat, face, w, h, rng, decals):
    """Текстура одной грани w×h текселей. u — слева направо, v — сверху вниз."""
    m = MAT[mat]
    base = np.array(m['c'], float)
    kind = m['kind']
    img = np.ones((h, w, 3)) * base
    yy, xx = np.mgrid[0:h, 0:w]
    along_u = face in ('east', 'west')     # длина оружия вдоль u
    along_v = face in ('up', 'down')       # длина оружия вдоль v
    fine = rng.normal(0, 1, (h, w))
    mott = blur(rng.normal(0, 1, (h, w)), 5) * 2.2
    if kind == 'metal':
        if along_u:
            streak = np.repeat(blur(rng.normal(0, 1, (h, 1)), 1), w, 1)
        elif along_v:
            streak = np.repeat(rng.normal(0, 1, (1, w)), h, 0)
        else:
            streak = np.zeros((h, w))
        img += (fine * 2.5 + streak * 4 + mott * 3)[..., None]
        if face == 'up':
            img *= 1.10
        if face == 'down':
            img *= 0.85
        # потёртые светлые рёбра
        edge = np.minimum(np.minimum(xx, w - 1 - xx), np.minimum(yy, h - 1 - yy))
        wear = (edge == 0) * 1.0 + (edge == 1) * 0.45
        wear *= np.clip(0.55 + rng.random((h, w)) * 0.9, 0, 1)
        img += wear[..., None] * (np.array([70, 74, 80]) if mat not in ('brass',) else np.array([60, 50, 20]))
        # царапины
        for _ in range(int(w * h / 900) + 1):
            if rng.random() < 0.7:
                x0, y0 = rng.integers(0, w), rng.integers(0, h)
                L = rng.integers(3, 10)
                ang = rng.uniform(-0.5, 0.5) + (0 if along_u else math.pi / 2 if along_v else rng.uniform(0, 3))
                for t in range(L):
                    x, y = int(x0 + math.cos(ang) * t), int(y0 + math.sin(ang) * t)
                    if 0 <= x < w and 0 <= y < h:
                        img[y, x] += 26
    elif kind == 'polymer':
        stip = (rng.random((h, w)) > 0.82) * 6 - (rng.random((h, w)) > 0.85) * 5
        img += (fine * 1.6 + mott * 2.4 + stip)[..., None]
        if face == 'up':
            img *= 1.08
        if face == 'down':
            img *= 0.86
        edge = np.minimum(np.minimum(xx, w - 1 - xx), np.minimum(yy, h - 1 - yy))
        img += ((edge == 0) * 22 * (0.4 + rng.random((h, w)) * 0.6))[..., None]
    elif kind == 'wood':
        L = w if along_u else h if along_v else max(w, h)
        if along_u:
            pos, cross = xx, yy
        elif along_v:
            pos, cross = yy, xx
        else:
            pos, cross = xx, yy
        warp = blur(rng.normal(0, 1, (h, w)), 7) * 6
        grain = np.sin((cross + warp + np.sin(pos * 0.07) * 3) * 1.15)
        rings = (grain > 0.75) * -22 + grain * 7
        img += rings[..., None] * np.array([1.0, 0.7, 0.45])
        img += (fine * 3 + mott * 4)[..., None]
        if not (along_u or along_v):  # торец: кольца
            r = np.hypot(xx - w * 0.3, yy - h * 1.2)
            img += (np.sin(r * 1.3) * 8)[..., None] * np.array([1, 0.7, 0.4])
        # лак: блик по верху, тёмные рёбра
        if face == 'up':
            img *= 1.12
        if face == 'down':
            img *= 0.82
        edge = np.minimum(np.minimum(xx, w - 1 - xx), np.minimum(yy, h - 1 - yy))
        img -= ((edge == 0) * 22)[..., None]
        if face in ('east', 'west'):
            img += (np.exp(-((yy - h * 0.22) / (h * 0.08 + 0.5)) ** 2) * 18)[..., None]
    elif kind == 'rubber':
        img += (fine * 1.5)[..., None]
    elif kind == 'glass':
        d = np.hypot(xx - w * 0.38, yy - h * 0.38) / max(w, h)
        img = np.ones((h, w, 3)) * np.array([14, 26, 40]) + (np.clip(0.45 - d, 0, 1) * 220)[..., None] * np.array([0.5, 0.75, 1.0])
    elif kind == 'bore':
        img += (fine * 2)[..., None]
    for d in decals:
        decal(img, d, w, h, rng, face, base)
    return np.clip(img, 0, 255)


def decal(img, d, w, h, rng, face, base):
    t = d[0]
    yy, xx = np.mgrid[0:h, 0:w]
    dark = np.array([10, 10, 12], float)
    if t == 'serr':  # насечка затвора: вертикальные канавки на участке u0..u1
        u0, u1 = int(d[1] * w), int(d[2] * w)
        for x in range(u0, u1):
            if (x - u0) % 3 == 0:
                img[1:h - 1, x] = img[1:h - 1, x] * 0.35
            elif (x - u0) % 3 == 1:
                img[1:h - 1, x] = img[1:h - 1, x] * 1.25 + 8
    elif t == 'port':  # окно выброса: u0, v0, u1, v1
        u0, v0, u1, v1 = int(d[1] * w), int(d[2] * h), int(d[3] * w), int(d[4] * h)
        img[v0:v1, u0:u1] = dark
        img[v0:v1, u0:u0 + 1] += 40
        img[v0:v0 + 1, u0:u1] += 25
        if v1 - v0 > 4:
            img[v0 + 2:v1 - 1, u0 + 2:u1 - 2] = np.array([150, 156, 164]) * 0.8  # затвор внутри
    elif t == 'text':  # «маркировка»: строчка светлых штрихов
        u0, v0 = int(d[1] * w), int(d[2] * h)
        x = u0
        for _ in range(7):
            ln = rng.integers(1, 3)
            if x + ln >= w - 1:
                break
            img[v0:v0 + 2, x:x + ln] += 32
            x += ln + 1
    elif t == 'stripe':
        u0, u1 = int(d[1] * w), int(d[2] * w)
        img[:, u0:u1] = img[:, u0:u1] * 0.6
    elif t == 'stripe_h':
        v0, v1 = int(d[1] * h), int(d[2] * h)
        img[v0:v1, :] = img[v0:v1, :] * 0.75
    elif t == 'bore':
        cx, cy, r = d[1] * w, d[2] * h, d[3] * min(w, h)
        dd = np.hypot(xx - cx + 0.5, yy - cy + 0.5)
        img[dd < r + 1.2] = img[dd < r + 1.2] * 0.5 + 40
        img[dd < r] = dark
    elif t == 'dot':
        cx, cy = int(d[1] * w), int(d[2] * h)
        img[max(0, cy - 1):cy + 1, max(0, cx - 1):cx + 1] = np.array(d[3], float)
    elif t in ('rail', 'railtop'):
        n = d[1] * 2
        for i in range(n):
            if i % 2:
                x0, x1 = int(i * w / n), int((i + 1) * w / n)
                img[:, x0:x1] *= 0.45
    elif t == 'checker':
        m = ((xx + yy) % 4 == 0) | ((xx - yy) % 4 == 0)
        inner = (xx > 1) & (xx < w - 2) & (yy > 1) & (yy < h - 2)
        img[m & inner] *= 0.55
        img[~m & inner] += 6
    elif t == 'holes':
        n = d[1]
        for i in range(n):
            cy = int((i + 0.7) * h / (n + 0.6))
            cx = w // 2
            img[cy:cy + 2, cx - 1:cx + 1] = dark
    elif t == 'vents':
        n = d[1]
        for i in range(n):
            x0 = int((i + 0.3) * w / n)
            x1 = int((i + 0.75) * w / n)
            img[int(h * 0.35):int(h * 0.62), x0:x1] = dark
            img[int(h * 0.35):int(h * 0.35) + 1, x0:x1] += 30
    elif t == 'rivets':
        n = d[1]
        for i in range(n):
            cx, cy = int((i + 0.5) * w / n), int(h * 0.3)
            img[cy:cy + 2, cx:cx + 2] += 45
            img[cy + 2, cx:cx + 2] *= 0.5
    elif t == 'ribs':
        n = d[1]
        for i in range(n):
            x = int((i + 1) * w / (n + 1))
            img[2:h - 2, x] *= 0.55
            img[2:h - 2, min(w - 1, x + 1)] += 18
    elif t == 'ribs_z':
        n = d[1]
        for i in range(n):
            x = int((i + 1) * w / (n + 1))
            img[:, x] *= 0.6
    elif t == 'grooves':
        n = d[1]
        for i in range(n):
            y = int((i + 0.5) * h / n)
            img[y, 1:w - 1] *= 0.4
    elif t in ('grooves_v', 'grooves_h'):
        n = d[1]
        for i in range(n):
            x = int((i + 0.5) * w / n)
            img[1:h - 1, x:x + 2] *= 0.35
            img[1:h - 1, x + 2:x + 3] += 14
    elif t == 'fgroove':
        img[int(h * 0.42):int(h * 0.58), int(w * 0.1):int(w * 0.9)] *= 0.72
    elif t == 'slots':
        n = d[1]
        for i in range(n):
            x0 = int((i + 0.25) * w / n)
            img[int(h * 0.2):int(h * 0.8), x0:x0 + max(1, int(w / n * 0.45))] = dark
    elif t == 'notches':
        n = d[1]
        for i in range(n):
            y = int((i + 0.5) * h / n)
            img[y, :] *= 0.4
    elif t == 'ring':
        cx, cy = w / 2, h / 2
        dd = np.hypot(xx - cx + 0.5, yy - cy + 0.5)
        img[(dd < min(w, h) * 0.45) & (dd > min(w, h) * 0.3)] *= 0.5
    elif t == 'lens':
        pass
    elif t == 'slot':
        v0, v1 = int(d[1] * h), int(d[2] * h)
        img[v0:v1, int(w * 0.25):int(w * 0.75)] = dark


def pack(rects):
    """Простая укладка полками. rects: [(w, h, key)] → {key: (x, y)}, размер."""
    rects = sorted(rects, key=lambda r: -r[1])
    for size in (128, 256, 512, 1024):
        pos, x, y, row = {}, 0, 0, 0
        ok = True
        for w, h, k in rects:
            if w + 2 > size:
                ok = False
                break
            if x + w + 2 > size:
                x, y, row = 0, y + row, 0
            if y + h + 2 > size:
                ok = False
                break
            pos[k] = (x + 1, y + 1)
            x += w + 2
            row = max(row, h + 2)
        if ok:
            return pos, size
    raise RuntimeError('не влезло')


FACES = ('north', 'south', 'east', 'west', 'up', 'down')


def build(g):
    rng = np.random.default_rng(sum(map(ord, g.name)) * 7919)
    rects, info = [], {}
    for bn in g.order:
        for ci, c in enumerate(g.bones[bn]['cubes']):
            for f in FACES:
                fw, fh = face_dims(c, f)
                w, h = max(2, int(round(fw * D))), max(2, int(round(fh * D)))
                rects.append((w, h, (bn, ci, f)))
                info[(bn, ci, f)] = (w, h)
    pos, size = pack(rects)
    tex = np.zeros((size, size, 4), np.uint8)
    for key, (x, y) in pos.items():
        bn, ci, f = key
        c = g.bones[bn]['cubes'][ci]
        w, h = info[key]
        mat = c['faces'].get(f, c['mat'])
        im = paint(mat, f, w, h, rng, c['decals'].get(f, []))
        tile = np.concatenate([im, np.full((h, w, 1), 255.)], -1).astype(np.uint8)
        # запас по краям (чтобы не было швов)
        pad = np.pad(tile, ((1, 1), (1, 1), (0, 0)), mode='edge')
        tex[y - 1:y + h + 1, x - 1:x + w + 1] = pad
    out = os.path.join(ROOT, 'textures', 'item', 'guns')
    os.makedirs(out, exist_ok=True)
    Image.fromarray(tex).save(os.path.join(out, g.name + '.png'))

    # geo: пиксели предмета → Bedrock (x зеркальный, начало — центр предмета)
    def bx(x):
        return 8 - x

    bones = []
    for bn in g.order:
        b = g.bones[bn]
        px, py, pz = b['pivot']
        jb = {'name': bn, 'pivot': [r3(bx(px)), r3(py - 8), r3(pz - 8)]}
        if b['parent']:
            jb['parent'] = b['parent']
        cubes = []
        for ci, c in enumerate(b['cubes']):
            a, e = c['a'], c['b']
            cube = {'origin': [r3(8 - e[0]), r3(a[1] - 8), r3(a[2] - 8)], 'size': [r3(e[0] - a[0]), r3(e[1] - a[1]), r3(e[2] - a[2])]}
            uv = {}
            for f in FACES:
                x, y = pos[(bn, ci, f)]
                w, h = info[(bn, ci, f)]
                uv[f] = {'uv': [x, y], 'uv_size': [w, h]} if f != 'down' else {'uv': [x, y + h], 'uv_size': [w, -h]}
            cube['uv'] = uv
            if c['rot']:
                ax, ang, o = c['rot']
                rot = {'x': [-ang, 0, 0], 'y': [0, -ang, 0], 'z': [0, 0, ang]}[ax]
                cube['rotation'] = rot
                cube['pivot'] = [r3(bx(o[0])), r3(o[1] - 8), r3(o[2] - 8)]
            cubes.append(cube)
        if cubes:
            jb['cubes'] = cubes
        bones.append(jb)
    geo = {'format_version': '1.12.0', 'minecraft:geometry': [{
        'description': {'identifier': 'geometry.novograd.' + g.name, 'texture_width': size, 'texture_height': size,
                        'visible_bounds_width': 4, 'visible_bounds_height': 3, 'visible_bounds_offset': [0, 0.5, 0]},
        'bones': bones}]}
    os.makedirs(os.path.join(ROOT, 'geo', 'guns'), exist_ok=True)
    with open(os.path.join(ROOT, 'geo', 'guns', g.name + '.geo.json'), 'w') as f:
        json.dump(geo, f, separators=(',', ':'))
    # анимации ведёт код (GunAnim.java) по таймингам сервера; файл — для GeckoLib и для правки в Blockbench
    anim = {'format_version': '1.8.0', 'animations': {f'animation.{g.name}.idle': {'loop': True, 'animation_length': 1.0, 'bones': {}}}}
    os.makedirs(os.path.join(ROOT, 'animations', 'guns'), exist_ok=True)
    with open(os.path.join(ROOT, 'animations', 'guns', g.name + '.animation.json'), 'w') as f:
        json.dump(anim, f)
    return size


def r3(v):
    return round(v, 4)


# ---------------------------------------------------------------- модель предмета (display) и иконка
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
    b = math.asin(max(-1, min(1, R[0][2])))
    a = math.atan2(-R[1][2], R[2][2])
    c = math.atan2(-R[0][1], R[0][0])
    return [round(math.degrees(v), 2) for v in (a, b, c)]


def corners(g):
    pts = []
    for bn in g.order:
        if bn == 'shell':
            continue
        for c in g.bones[bn]['cubes']:
            f, t = c['a'], c['b']
            cs = [np.array([x, y, z], float) for x in (f[0], t[0]) for y in (f[1], t[1]) for z in (f[2], t[2])]
            if c['rot']:
                ax, ang, o = c['rot']
                R = {'x': _rx, 'y': _ry, 'z': _rz}[ax](ang)
                cs = [R @ (p - np.array(o, float)) + np.array(o, float) for p in cs]
            pts += cs
    return np.array(pts) / 16 - 0.5


def fit(g, tilt, fill=0.95, yaw=-112, pitch=22):
    R = _rz(tilt) @ _rx(pitch) @ _ry(yaw)
    rot = euler_xyz(R)
    R2 = _rx(rot[0]) @ _ry(rot[1]) @ _rz(rot[2])
    q = corners(g) @ R2.T
    lo, hi = q.min(0), q.max(0)
    sc = fill / max(hi[0] - lo[0], hi[1] - lo[1])
    cx, cy = (lo[0] + hi[0]) / 2 * sc, (lo[1] + hi[1]) / 2 * sc
    return {'rotation': rot, 'translation': [round(-cx * 16, 2), round(-cy * 16, 2), 0], 'scale': [round(min(sc, 1.6), 3)] * 3}


FP_SCALE = {'pistol': 0.9, 'smg': 0.82, 'shotgun': 0.78, 'rifle': 0.76, 'sniper': 0.72}
TP_SCALE = {'pistol': 0.75, 'smg': 0.75, 'shotgun': 0.8, 'rifle': 0.8, 'sniper': 0.8}
GUI_TILT = {'pistol': 0, 'smg': 18, 'shotgun': 32, 'rifle': 32, 'sniper': 34}


def item_model(g):
    gx, gy, gz = (v - 8 for v in g.meta['grip'])
    s = FP_SCALE[g.name]
    fp = {'rotation': [0, 0, 0], 'translation': [round(-gx * s, 3), round(-gy * s, 3), round(-gz * s, 3)], 'scale': [s] * 3}
    ts = TP_SCALE[g.name]
    tp = {'rotation': [0, 0, 0], 'translation': [round(-gx * ts, 2), round(-gy * ts - 1.5, 2), round(-gz * ts + 1.5, 2)], 'scale': [ts] * 3}
    gs = min(0.9, 15.0 / g.meta['length'])
    disp = {
        'firstperson_righthand': fp, 'firstperson_lefthand': fp,
        'thirdperson_righthand': tp, 'thirdperson_lefthand': tp,
        'gui': fit(g, GUI_TILT[g.name], 0.86 if g.name == 'pistol' else 0.95),
        'ground': {'rotation': [0, 90, 0], 'translation': [0, 2, 0], 'scale': [round(min(0.6, gs * 0.9), 3)] * 3},
        'fixed': {'rotation': [0, 90, 0], 'translation': [0, 0, 0], 'scale': [round(gs * 1.1, 3)] * 3},
        'head': {'rotation': [0, 90, 0], 'translation': [0, 6, 0], 'scale': [0.8] * 3},
    }
    m = {'credit': 'Novograd: tools/gen_gun_geo.py (GeckoLib)', 'parent': 'builtin/entity', 'gui_light': 'front',
         'textures': {'particle': 'novograd:item/gun_atlas'}, 'display': disp}
    with open(os.path.join(ROOT, 'models', 'item', g.name + '.json'), 'w') as f:
        json.dump(m, f, indent=1)
    return {'fp_scale': s}


# ---------------------------------------------------------------- спрайты эффектов
def flashes():
    """4 разные звёзды вспышки 128×128 в атласе 256×256 (чёрный = прозрачно, рисуется сложением)."""
    n = 128
    out = np.zeros((256, 256, 4), np.uint8)
    rng = np.random.default_rng(5)
    for i in range(4):
        yy, xx = (np.mgrid[0:n, 0:n] - (n - 1) / 2) / (n / 2)
        r = np.hypot(xx, yy)
        a = np.arctan2(yy, xx)
        k = rng.integers(4, 8)
        ph = rng.uniform(0, math.pi)
        lens = rng.uniform(0.55, 1.0, k)
        rays = np.zeros_like(r)
        for j in range(k):
            ang = ph + j * 2 * math.pi / k + rng.uniform(-0.25, 0.25)
            dang = np.angle(np.exp(1j * (a - ang)))
            width = rng.uniform(0.08, 0.18)
            rays = np.maximum(rays, np.exp(-(dang / width) ** 2) * np.clip(1 - r / lens[j], 0, 1) ** 0.8)
        noise = blur(rng.random((n, n)), 3)
        core = np.clip(1 - r / 0.3, 0, 1) ** 1.1
        glow = np.clip(1 - r / 0.62, 0, 1) ** 2 * 0.55
        v = np.clip(rays * (0.75 + noise * 0.5) + glow, 0, 1)
        col = np.stack([255 * np.clip(v * 1.5 + core, 0, 1), 255 * np.clip(v * 0.95 + core, 0, 1), 255 * np.clip(v * 0.35 + core * 0.95, 0, 1)], -1)
        ox, oy = (i % 2) * n, (i // 2) * n
        out[oy:oy + n, ox:ox + n, :3] = col.astype(np.uint8)
        out[oy:oy + n, ox:ox + n, 3] = 255
    Image.fromarray(out).save(os.path.join(ROOT, 'textures', 'misc', 'muzzle_flash.png'))
    # боковой «язык» пламени (вытянутый, вдоль ствола)
    n = 64
    yy, xx = (np.mgrid[0:n * 2, 0:n] - [[[(2 * n - 1) / 2]], [[(n - 1) / 2]]])
    yy, xx = yy / n, xx / (n / 2)
    v = np.clip(1 - np.abs(xx) / (0.15 + 0.85 * np.clip(1 - np.abs(yy), 0, 1) ** 1.5), 0, 1) * np.clip(1 - np.abs(yy), 0, 1)
    v = np.clip(v * (0.7 + blur(np.random.default_rng(3).random((2 * n, n)), 3) * 0.6), 0, 1)
    col = np.stack([255 * np.clip(v * 1.6, 0, 1), 255 * np.clip(v * 1.0, 0, 1), 255 * np.clip(v * 0.35, 0, 1), np.full_like(v, 255)], -1).astype(np.uint8)
    Image.fromarray(col).save(os.path.join(ROOT, 'textures', 'misc', 'muzzle_side.png'))


def bits():
    """gun_bits.png 32×32: латунь (0..15, 0..7), красная гильза (16..31, 0..7), тёмная сталь (0..15, 8..15),
    бакелит (16..31, 8..15), дым (0..31, 16..31)."""
    rng = np.random.default_rng(11)
    img = np.zeros((32, 32, 4), np.uint8)

    def fill(x0, y0, w, h, c, var=10):
        n = rng.normal(0, var, (h, w, 1))
        grad = np.linspace(1.15, 0.8, h)[:, None, None]
        img[y0:y0 + h, x0:x0 + w, :3] = np.clip(np.array(c) * grad + n, 0, 255)
        img[y0:y0 + h, x0:x0 + w, 3] = 255
    fill(0, 0, 16, 8, (210, 165, 60))
    fill(16, 0, 16, 8, (170, 30, 28))
    img[0:8, 28:32, :3] = np.clip(np.array([210, 165, 60]) + rng.normal(0, 8, (8, 4, 3)), 0, 255)
    fill(0, 8, 16, 8, (44, 47, 52))
    fill(16, 8, 16, 8, (120, 52, 30))
    yy, xx = np.mgrid[0:16, 0:32]
    puff = np.clip(1 - np.hypot((xx - 15.5) / 15, (yy - 7.5) / 7.5), 0, 1) * (0.6 + blur(rng.random((16, 32)), 3) * 0.6)
    img[16:32, :, :3] = 220
    img[16:32, :, 3] = np.clip(puff * 200, 0, 255).astype(np.uint8)
    Image.fromarray(img).save(os.path.join(ROOT, 'textures', 'misc', 'gun_bits.png'))


def main():
    meta = {}
    for fn in (pistol, smg, shotgun, rifle, sniper):
        g = fn()
        size = build(g)
        extra = item_model(g)
        piv = {bn: g.bones[bn]['pivot'] for bn in g.order}
        meta[g.name] = dict(g.meta, **extra, pivots=piv, texture=size)
        print(g.name, 'texture', size, 'cubes', sum(len(g.bones[b]['cubes']) for b in g.order))
    os.makedirs(os.path.join(ROOT, 'guns'), exist_ok=True)
    with open(os.path.join(ROOT, 'guns', 'meta.json'), 'w') as f:
        json.dump(meta, f, indent=1)
    flashes()
    bits()
    print('gun geo ok')


if __name__ == '__main__':
    main()
