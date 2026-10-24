#!/usr/bin/env python3
"""Обложки к трейлерам: YouTube 1280×720 и вертикальная 1080×1920 (TikTok / Shorts) из кадра движка + логотип игры.
  python3 tools/trailer/thumb.py КАДР.jpg yt|tt ru|en OUT.jpg"""
import sys, os
from PIL import Image, ImageDraw, ImageFont, ImageFilter, ImageEnhance
HERE = os.path.dirname(os.path.abspath(__file__)); FONTS = os.path.join(HERE, '../../public/games/nightshift/fonts')
src, fmt, L, out = sys.argv[1:5]
W, H = (1280, 720) if fmt == 'yt' else (1080, 1920)
BONE, GOLD = (236, 232, 220), (242, 194, 27)
im = Image.open(src).convert('RGB')
k = max(W / im.width, H / im.height) * (1.12 if fmt == 'yt' else 1.0); im = im.resize((int(im.width * k + .5), int(im.height * k + .5)), Image.LANCZOS)
x0 = min(im.width - W, (im.width - W) // 2 + (int(W * .06) if fmt == 'yt' else 0)); y0 = (im.height - H) // 2   # кадр чуть крупнее: сдвиг вправо без чёрной полосы, рамка кадра срезана
im = im.crop((x0, y0, x0 + W, y0 + H))
im = ImageEnhance.Contrast(im).enhance(1.12)
# затемнение под текст: справа (YouTube) или снизу (вертикаль), плюс виньетка
sh = Image.new('L', (W, H), 0); d = ImageDraw.Draw(sh)
for i in range(W if fmt == 'yt' else H):
    t = i / (W if fmt == 'yt' else H); a = int(255 * max(0, (t - .42) / .58) ** 1.3 * .92) if fmt == 'yt' else int(255 * .88 * max(max(0, (.3 - t) / .3), max(0, (t - .78) / .22)) ** .8)   # вертикаль: темнее сверху (логотип) и снизу (подпись)
    (d.line([(i, 0), (i, H)], fill=a) if fmt == 'yt' else d.line([(0, i), (W, i)], fill=a))
im = Image.composite(Image.new('RGB', (W, H), (0, 0, 0)), im, sh)
g = ImageDraw.Draw(im)
def text(xy, s, size, fill, font='ns-ui.woff', anchor='mm', spacing=0, stroke=0):
    f = ImageFont.truetype(os.path.join(FONTS, font), size)
    if spacing:   # разрядка: по буквам
        w = sum(f.getlength(c) for c in s) + spacing * (len(s) - 1); x = xy[0] - w / 2 if anchor[0] == 'm' else xy[0]
        for c in s: g.text((x, xy[1]), c, font=f, fill=fill, anchor='l' + anchor[1], stroke_width=stroke, stroke_fill=(0, 0, 0)); x += f.getlength(c) + spacing
    else: g.text(xy, s, font=f, fill=fill, anchor=anchor, stroke_width=stroke, stroke_fill=(0, 0, 0))
if fmt == 'yt':
    cx = int(W * .76)
    text((cx, 250), 'HUSH', 190, BONE, 'ns-logo.woff', spacing=6)
    text((cx, 392), 'TILL SIX', 138, GOLD, 'ns-logo.woff', spacing=3)
    text((cx, 520), "DON'T ANSWER." if L == 'en' else 'НЕ ОТЗЫВАЙСЯ.', 64, BONE, stroke=3)
    text((cx, 590), 'HORROR · 18+' if L == 'en' else 'ХОРРОР · 18+', 34, GOLD, spacing=6)
else:
    text((W // 2, 330), 'HUSH', 250, BONE, 'ns-logo.woff', spacing=8)
    text((W // 2, 515), 'TILL SIX', 182, GOLD, 'ns-logo.woff', spacing=4)
    text((W // 2, 1560), "IF IT CALLS YOUR NAME —" if L == 'en' else 'ЕСЛИ ЗОВУТ ПО ИМЕНИ —', 62, BONE, stroke=3)
    text((W // 2, 1640), "DON'T ANSWER." if L == 'en' else 'НЕ ОТЗЫВАЙСЯ.', 78, GOLD, stroke=3)
im.save(out, quality=92); print(out, im.size)
