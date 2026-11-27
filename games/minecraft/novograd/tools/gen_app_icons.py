#!/usr/bin/env python3
"""Иконки приложений телефона, которых не хватало (0.9.9–0.9.10 и старые «Империя», «Сеть», «Прогресс»), 64×64 RGBA,
в стиле остальных: тёмная плашка с оттенком приложения, светлая кромка, знак цветом приложения.
Запуск из папки novograd: python3 tools/gen_app_icons.py → src/main/resources/assets/novograd/textures/gui/ui/app/
"""
import math, os
from PIL import Image, ImageDraw

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src/main/resources/assets/novograd/textures/gui/ui/app')

# цвет — как у приложения в PhoneScreen.APPS
APPS = {
    'night': 0xAB47BC, 'dating': 0xE91E63, 'sport': 0xFF7043, 'social': 0xEC407A, 'cozy': 0xC08A64,
    'career': 0x7986CB, 'crime': 0xD84357, 'duty': 0x5C9CE6, 'biz': 0x4CAF50, 'voice': 0x26A69A,
    'empire': 0xE0B040, 'grid': 0x3FB8B0, 'progress': 0xFFC428,
}


def rgb(c, k=1.0):
    return tuple(min(255, int(((c >> s) & 255) * k)) for s in (16, 8, 0))


def base(c):
    S = 4  # рисуем крупно и уменьшаем — ровные края без мыла
    im = Image.new('RGBA', (64 * S, 64 * S), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    top, bot = rgb(c, 0.32), (18, 18, 20)
    for y in range(64 * S):
        t = y / (64 * S)
        col = tuple(int(top[i] * (1 - t) + bot[i] * t) for i in range(3)) + (255,)
        d.line([(0, y), (64 * S, y)], fill=col)
    mask = Image.new('L', im.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle([2 * S, 2 * S, 62 * S - 1, 62 * S - 1], 12 * S, fill=255)
    out = Image.new('RGBA', im.size, (0, 0, 0, 0))
    out.paste(im, (0, 0), mask)
    ImageDraw.Draw(out).rounded_rectangle([2 * S, 2 * S, 62 * S - 1, 62 * S - 1], 12 * S, outline=rgb(c, 0.75) + (255,), width=2 * S)
    return out, ImageDraw.Draw(out), S


def glyph(name, c):
    im, d, S = base(c)
    f = rgb(c) + (255,)
    dk = (24, 22, 26, 255)
    s = lambda *v: [x * S for x in v]
    if name == 'night':  # месяц и звёзды
        m = Image.new('L', im.size, 0)
        md = ImageDraw.Draw(m)
        md.ellipse(s(14, 14, 44, 44), fill=255)
        md.ellipse(s(23, 8, 51, 36), fill=0)
        im.paste(Image.new('RGBA', im.size, f), (0, 0), m)
        for x, y, r in ((44, 42, 3), (36, 50, 2), (49, 24, 2)):
            d.ellipse(s(x - r, y - r, x + r, y + r), fill=(255, 236, 180, 255))
    elif name == 'dating':  # сердце
        d.ellipse(s(14, 16, 33, 35), fill=f)
        d.ellipse(s(31, 16, 50, 35), fill=f)
        d.polygon(s(15, 29, 49, 29, 32, 50), fill=f)
        d.ellipse(s(20, 20, 26, 26), fill=(255, 255, 255, 140))
    elif name == 'sport':  # кубок
        d.rectangle(s(22, 14, 42, 18), fill=f)
        d.pieslice(s(20, 4, 44, 36), 0, 180, fill=f)
        d.arc(s(12, 16, 26, 30), 90, 270, fill=f, width=3 * S)
        d.arc(s(38, 16, 52, 30), 270, 90, fill=f, width=3 * S)
        d.rectangle(s(29, 36, 35, 44), fill=f)
        d.rectangle(s(22, 44, 42, 50), fill=f)
    elif name == 'social':  # камера с сердечком
        d.rounded_rectangle(s(12, 20, 52, 48), 6 * S, fill=f)
        d.rectangle(s(24, 15, 36, 21), fill=f)
        d.ellipse(s(23, 24, 41, 42), fill=dk)
        d.ellipse(s(27, 28, 37, 38), fill=f)
        d.ellipse(s(44, 23, 48, 27), fill=(255, 255, 255, 200))
    elif name == 'cozy':  # кресло
        d.rounded_rectangle(s(18, 14, 46, 36), 5 * S, fill=f)
        d.rounded_rectangle(s(12, 26, 22, 46), 4 * S, fill=rgb(c, 0.8) + (255,))
        d.rounded_rectangle(s(42, 26, 52, 46), 4 * S, fill=rgb(c, 0.8) + (255,))
        d.rounded_rectangle(s(20, 32, 44, 44), 3 * S, fill=rgb(c, 1.15) + (255,))
        d.rectangle(s(15, 46, 18, 51), fill=f)
        d.rectangle(s(46, 46, 49, 51), fill=f)
    elif name == 'career':  # портфель и стрелка вверх
        d.rounded_rectangle(s(12, 24, 44, 50), 4 * S, fill=f)
        d.rectangle(s(22, 19, 34, 24), outline=f, width=3 * S)
        d.rectangle(s(12, 34, 44, 37), fill=dk)
        d.polygon(s(46, 10, 56, 22, 50, 22, 50, 34, 42, 34, 42, 22, 36, 22), fill=(255, 214, 102, 255))
    elif name == 'crime':  # маска
        d.rounded_rectangle(s(10, 22, 54, 40), 9 * S, fill=f)
        d.ellipse(s(17, 26, 29, 36), fill=dk)
        d.ellipse(s(35, 26, 47, 36), fill=dk)
        d.polygon(s(28, 40, 36, 40, 32, 46), fill=f)
    elif name == 'duty':  # значок-щит со звездой
        d.polygon(s(32, 10, 52, 18, 50, 36, 32, 52, 14, 36, 12, 18), fill=f)
        pts = []
        for i in range(10):
            a = -math.pi / 2 + i * math.pi / 5
            r = 10 if i % 2 == 0 else 4.2
            pts += [32 + r * math.cos(a), 30 + r * math.sin(a)]
        d.polygon(s(*pts), fill=dk)
    elif name == 'biz':  # витрина с навесом
        d.rectangle(s(14, 28, 50, 50), fill=f)
        for i in range(5):
            col = f if i % 2 == 0 else (240, 240, 232, 255)
            d.polygon(s(12 + i * 8, 16, 20 + i * 8, 16, 20 + i * 8, 26, 12 + i * 8, 26), fill=col)
        d.rectangle(s(20, 34, 32, 50), fill=dk)
        d.rectangle(s(36, 34, 46, 42), fill=(200, 236, 255, 255))
    elif name == 'voice':  # рация
        d.rectangle(s(36, 6, 40, 20), fill=f)
        d.rounded_rectangle(s(20, 18, 44, 54), 5 * S, fill=f)
        d.rounded_rectangle(s(24, 22, 40, 32), 2 * S, fill=dk)
        for y in (37, 42, 47):
            d.line(s(25, y, 39, y), fill=dk, width=2 * S)
        d.arc(s(44, 12, 56, 30), 300, 60, fill=f, width=2 * S)
    elif name == 'empire':  # корона
        d.polygon(s(12, 22, 22, 32, 32, 14, 42, 32, 52, 22, 48, 46, 16, 46), fill=f)
        d.rectangle(s(16, 46, 48, 51), fill=f)
        for x in (12, 32, 52):
            d.ellipse(s(x - 3, (14 if x == 32 else 22) - 3, x + 3, (14 if x == 32 else 22) + 3), fill=(255, 245, 200, 255))
    elif name == 'grid':  # сеть: узлы и связи
        nodes = [(18, 18), (46, 16), (32, 32), (16, 46), (48, 46)]
        for a, b in ((0, 2), (1, 2), (2, 3), (2, 4), (0, 1), (3, 4)):
            d.line(s(*nodes[a], *nodes[b]), fill=rgb(c, 0.7) + (255,), width=2 * S)
        for x, y in nodes:
            d.ellipse(s(x - 5, y - 5, x + 5, y + 5), fill=f)
    elif name == 'progress':  # столбики роста
        for i, h in enumerate((12, 20, 28, 36)):
            d.rectangle(s(13 + i * 10, 50 - h, 20 + i * 10, 50), fill=f)
        d.line(s(12, 34, 24, 26, 34, 28, 50, 12), fill=(255, 255, 255, 220), width=2 * S)
    out = im.resize((64, 64), Image.LANCZOS)
    return out


def main():
    os.makedirs(OUT, exist_ok=True)
    for n, c in APPS.items():
        p = os.path.join(OUT, n + '.png')
        glyph(n, c).save(p)
    print('иконок:', len(APPS))


if __name__ == '__main__':
    main()
