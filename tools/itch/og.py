#!/usr/bin/env python3
"""Картинка-превью ссылки и обложка для itch.io: кадр игры + название «HUSH TILL SIX» (шрифт Oswald 700, OFL).
   python3 tools/itch/og.py КАДР.png ШРИФТ_Oswald700.ttf
     → public/games/nightshift/og.jpg и og.webp (1200×630), dist/itch/cover.png (630×500)
   Кадр — титульный (Лили за стеклом): GFX=cine LAND=1 SIZE=1200x630 node tools/nightshift-shot.js ПАПКА "door_katya@3" """
import os, sys
from PIL import Image, ImageDraw, ImageFont, ImageFilter
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
GAME = os.path.join(ROOT, 'public/games/nightshift')
frame, font = sys.argv[1], sys.argv[2]
CREAM, GOLD, RED = (236, 232, 220), (242, 194, 27), (230, 50, 50)

def compose(W, H, big, lines):
    im = Image.open(frame).convert('RGB')
    s = max(W / im.width, H / im.height); im = im.resize((round(im.width * s), round(im.height * s)), Image.LANCZOS)
    x0 = (im.width - W) // 2; im = im.crop((x0, 0, x0 + W, H))
    # слева темнее — там название
    shade = Image.new('L', (W, H)); d = ImageDraw.Draw(shade)
    for x in range(W): d.line([(x, 0), (x, H)], fill=int(205 * max(0, 1 - x / (W * .62)) ** 1.3))
    im = Image.composite(Image.new('RGB', (W, H), (0, 0, 0)), im, shade)
    d = ImageDraw.Draw(im)
    def text(xy, s, size, color, spacing=0):
        f = ImageFont.truetype(font, size); x, y = xy
        sh = Image.new('L', im.size); ImageDraw.Draw(sh).text((x + 3, y + 5), s, font=f, fill=200)
        im.paste((0, 0, 0), mask=sh.filter(ImageFilter.GaussianBlur(5)))
        for ch in s:   # разрядка
            d.text((x, y), ch, font=f, fill=color); x += f.getlength(ch) + spacing
        return f.getbbox(s)[3]
    y = big[0]
    for s, size, color in big[1]: text((big[2], y), s, size, color, 2); y += int(size * .98)
    for s, size, color, dy in lines: y += dy; text((big[2], y), s, size, color, 2); y += int(size * 1.15)
    return im

og = compose(1200, 630, (70, [('HUSH', 196, CREAM), ('TILL SIX', 150, GOLD)], 74),
             [("DON'T ANSWER · DON'T OPEN · WAIT TILL SIX", 30, CREAM, 18), ('FREE HORROR GAME IN YOUR BROWSER', 24, (200, 196, 186), 62), ('18+  VIOLENCE AND BLOOD', 24, RED, 0)])
og.save(os.path.join(GAME, 'og.jpg'), quality=88); og.save(os.path.join(GAME, 'og.webp'), quality=82)
os.makedirs(os.path.join(ROOT, 'dist/itch'), exist_ok=True)
cover = compose(630, 500, (60, [('HUSH', 150, CREAM), ('TILL SIX', 112, GOLD)], 44), [("DON'T ANSWER · DON'T OPEN", 24, CREAM, 18), ('18+  HORROR', 22, RED, 30)])
cover.save(os.path.join(ROOT, 'dist/itch/cover.png'))
print('готово: og.jpg, og.webp, dist/itch/cover.png')
