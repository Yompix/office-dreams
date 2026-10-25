#!/usr/bin/env python3
"""Картинки установщика Hush Till Six (NSIS, Modern UI): слева на первой и последней странице 164×314 и в шапке 150×57.
   Только шрифт игры (Oswald, OFL) и код — без чужих картинок.  python3 tools/desktop/installer_art.py ПАПКА_ШРИФТОВ ПАПКА_ВЫХОДА"""
import os, sys, random
from PIL import Image, ImageDraw, ImageFont, ImageFilter
fonts, out = sys.argv[1], sys.argv[2]
BONE, GOLD, RED = (236, 232, 220), (242, 194, 27), (224, 48, 58)
font = lambda f, s: ImageFont.truetype(os.path.join(fonts, f), s)

def night(W, H, seed):   # ночь за стеклом: тёмный градиент, красноватый низ, дождь, зерно
    rnd = random.Random(seed); im = Image.new('RGB', (W, H)); px = im.load()
    for y in range(H):
        t = y / (H - 1)
        for x in range(W):
            n = rnd.randint(-5, 5); px[x, y] = (max(0, int(6 + 26 * t ** 2.2) + n), max(0, int(7 + 4 * t) + n), max(0, int(9 + 6 * t) + n))
    rain = Image.new('L', (W, H)); d = ImageDraw.Draw(rain)
    for _ in range(W * H // 900):
        x, y, l = rnd.uniform(0, W), rnd.uniform(-20, H), rnd.uniform(6, 18); d.line([(x, y), (x - l * .12, y + l)], fill=rnd.randint(30, 70))
    im.paste((150, 165, 180), mask=rain.filter(ImageFilter.GaussianBlur(.4)))
    return im

def logo(im, x, y, s1, s2, shadow=True):   # HUSH / TILL SIX, как на титуле игры
    d = ImageDraw.Draw(im)
    for txt, f, col, dy in (('HUSH', font('ns-logo.woff', s1), BONE, 0), ('TILL SIX', font('ns-logo.woff', s2), GOLD, int(s1 * .86))):
        if shadow:
            sh = Image.new('L', im.size); ImageDraw.Draw(sh).text((x + 2, y + dy + 3), txt, font=f, fill=200, anchor='mt')
            im.paste((0, 0, 0), mask=sh.filter(ImageFilter.GaussianBlur(3)))
        d.text((x, y + dy), txt, font=f, fill=col, anchor='mt')

side = night(164, 314, 6)
logo(side, 82, 34, 56, 40)
d = ImageDraw.Draw(side)
d.line([(46, 132), (118, 132)], fill=RED, width=2)
for i, s in enumerate(("DON'T ANSWER", "DON'T OPEN", 'WAIT TILL SIX')):
    d.text((82, 146 + i * 17), s, font=font('ns-ui.woff', 12), fill=(200, 196, 186), anchor='mt')
d.text((82, 286), '18+', font=font('ns-ui.woff', 14), fill=RED, anchor='mt')
side.save(os.path.join(out, 'side.bmp'))

head = night(150, 57, 7)
logo(head, 75, 7, 26, 19, shadow=False)
head.save(os.path.join(out, 'head.bmp'))
print('картинки установщика:', out)
