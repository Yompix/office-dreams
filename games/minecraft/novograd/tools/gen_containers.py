#!/usr/bin/env python3
"""Сундуки и бочки в стиле города: тёмная панель 176×222 с ячейками (своя картинка, не перекраска ванильной).

  python3 tools/gen_containers.py

Пишет assets/minecraft/textures/gui/container/generic_54.png. Раскладка как у ванильного экрана сундука:
верх — до 6 рядов ячеек с y=17, низ (с v=126) — рюкзак с y=13 и быстрые слоты с y=71. Подписи светлые —
цвет текста меняет client/mixin/ContainerTitleMixin.
"""
import os
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'minecraft', 'textures', 'gui', 'container', 'generic_54.png')

PANEL, EDGE, PINK = (18, 18, 22, 240), (44, 44, 52, 255), (212, 42, 58, 255)
SLOT_BG, SLOT_DARK, SLOT_LIGHT = (30, 30, 34, 255), (10, 10, 12, 255), (58, 58, 66, 255)


def slot(d, x, y):
    d.rectangle([x, y, x + 17, y + 17], fill=SLOT_BG)
    d.line([x, y, x + 16, y], fill=SLOT_DARK)
    d.line([x, y, x, y + 16], fill=SLOT_DARK)
    d.line([x + 1, y + 17, x + 17, y + 17], fill=SLOT_LIGHT)
    d.line([x + 17, y + 1, x + 17, y + 17], fill=SLOT_LIGHT)


def panel(d, y0, y1):
    d.rectangle([0, y0, 175, y1], fill=PANEL)
    d.rectangle([0, y0, 175, y1], outline=EDGE)


def main():
    im = Image.new('RGBA', (256, 256), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    # верх: 6 рядов (экран берёт столько, сколько рядов у сундука)
    panel(d, 0, 125)
    d.rectangle([0, 0, 175, 1], fill=PINK)
    for r in range(6):
        for c in range(9):
            slot(d, 7 + c * 18, 17 + r * 18)
    # низ: рюкзак и быстрые слоты
    panel(d, 126, 221)
    d.line([4, 126 + 10, 171, 126 + 10], fill=(255, 255, 255, 18))
    for r in range(3):
        for c in range(9):
            slot(d, 7 + c * 18, 126 + 13 + r * 18)
    for c in range(9):
        slot(d, 7 + c * 18, 126 + 71)
    d.line([0, 221, 175, 221], fill=PINK)
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    im.save(OUT)
    print('ok', OUT)


if __name__ == '__main__':
    main()
