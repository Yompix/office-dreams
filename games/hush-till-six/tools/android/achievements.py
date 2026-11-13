#!/usr/bin/env python3
"""Иконки достижений из кадров игры (без ИИ): кадры рендерит tools/desktop/steam/kit.js ПАПКА frames (ONLY=ach_…).
  python3 tools/android/achievements.py ПАПКА_С_frames [ПАПКА_ДЛЯ_PLAY]
  → web/ach/<ID>.jpg 160×160 — в игре (плашка «Достижение получено» и экран «Достижения»)
  → ПАПКА_ДЛЯ_PLAY/<ID>.png 512×512 — для Google Play Console (Play Игры → Достижения → «Значок»: 512×512, PNG/JPEG, до 1 МБ)
Стиль: кадр в квадрат, лёгкое затемнение к краям, тонкая красная рамка внутри (цвет интерфейса игры), уголки скруглены."""
import os, sys
from PIL import Image, ImageDraw, ImageEnhance, ImageFilter

src = os.path.join(sys.argv[1], 'frames') if os.path.isdir(os.path.join(sys.argv[1], 'frames')) else sys.argv[1]
play = sys.argv[2] if len(sys.argv) > 2 else None
WEB = os.path.join(os.path.dirname(os.path.abspath(__file__)), '../../web/ach')
os.makedirs(WEB, exist_ok=True)
if play: os.makedirs(play, exist_ok=True)
RED = (255, 59, 71)


def icon(im, S):
    w, h = im.size; s = min(w, h)
    im = im.crop(((w - s) // 2, (h - s) // 2, (w + s) // 2, (h + s) // 2)).resize((S, S), Image.LANCZOS)
    im = ImageEnhance.Contrast(im).enhance(1.12)
    from PIL import ImageStat
    lum = ImageStat.Stat(im.convert('L')).mean[0]
    if lum < 70: im = ImageEnhance.Brightness(im).enhance(min(2.4, 70 / max(lum, 12)))   # тёмный кадр (смерть в темноте) — светлее, иначе на значке ничего не видно
    vig = Image.new('L', (S, S), 0); d = ImageDraw.Draw(vig)
    for i in range(40):   # затемнение к краям
        k = i / 40; d.ellipse((-S * .35 + k * S * .3, -S * .35 + k * S * .3, S * 1.35 - k * S * .3, S * 1.35 - k * S * .3), fill=int(255 * k))
    vig = vig.filter(ImageFilter.GaussianBlur(S / 14))
    im = Image.composite(im, Image.new('RGB', (S, S), (6, 6, 8)), vig.point(lambda v: 90 + v * 165 // 255))
    d = ImageDraw.Draw(im); m = round(S * .045); r = round(S * .09)
    d.rounded_rectangle((m, m, S - 1 - m, S - 1 - m), radius=r, outline=RED, width=max(2, round(S * .012)))
    d.rounded_rectangle((m + round(S * .02), m + round(S * .02), S - 1 - m - round(S * .02), S - 1 - m - round(S * .02)), radius=r, outline=(255, 255, 255, 60), width=1)
    mask = Image.new('L', (S, S), 0); ImageDraw.Draw(mask).rounded_rectangle((0, 0, S - 1, S - 1), radius=round(S * .12), fill=255)
    bg = Image.new('RGB', (S, S), (0, 0, 0)); bg.paste(im, (0, 0), mask); return bg


n = 0
for f in sorted(os.listdir(src)):
    if not (f.startswith('ach_') and f.endswith('.png')): continue
    aid = f[4:-4]; im = Image.open(os.path.join(src, f)).convert('RGB')
    icon(im, 160).save(os.path.join(WEB, aid + '.jpg'), quality=86)
    if play: icon(im, 512).save(os.path.join(play, aid + '.png'), optimize=True)
    n += 1
print('иконок:', n, '→', os.path.normpath(WEB), ('и ' + play) if play else '')
