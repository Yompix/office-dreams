#!/usr/bin/env python3
"""Иконки Hush Till Six из одного кадра (страшная улыбка Лили, b_smile@3.4 в «Кино», tools/android/icon_master.jpg 1024×1024)
и графика для страницы Google Play.
  python3 tools/android/assets.py   →
    public/games/nightshift/icon*.png, apple-touch-icon.png, favicon-32.png, favicon.ico  (браузер, ярлык на телефоне, ПК-версия берёт icon-512.png)
    tools/android/res/mipmap-*/ic_launcher.png, ic_launcher_fg.png + mipmap-anydpi-v26/ic_launcher.xml  (адаптивная иконка Android)
    dist/play/icon-512.png, dist/play/feature-1024x500.png  (Google Play: значок и баннер страницы)
Раньше иконки были ещё с прежним названием «NIGHT SHIFT»; без текста лицо читается и на значке 48 px, название магазин пишет рядом."""
import os
from PIL import Image, ImageDraw, ImageFont, ImageFilter
HERE = os.path.dirname(os.path.abspath(__file__)); ROOT = os.path.dirname(os.path.dirname(HERE))
GAME = os.path.join(ROOT, 'public/games/nightshift'); RES = os.path.join(HERE, 'res'); PLAY = os.path.join(ROOT, 'dist/play')
FONT = os.path.join(ROOT, 'tools/fonts/Oswald-Bold.ttf')
CREAM, GOLD, RED, BG = (236, 232, 220), (242, 194, 27), (230, 50, 50), (5, 6, 7)
master = Image.open(os.path.join(HERE, 'icon_master.jpg')).convert('RGB')
sz = lambda n: master.resize((n, n), Image.LANCZOS)

# браузер и ПК
for name, n in [('icon.png', 512), ('icon-512.png', 512), ('icon-192.png', 192), ('icon-maskable-512.png', 512), ('icon-maskable-192.png', 192),
                ('apple-touch-icon.png', 180), ('favicon-32.png', 32)]:
    sz(n).save(os.path.join(GAME, name), optimize=True)
sz(256).save(os.path.join(GAME, 'favicon.ico'), sizes=[(16, 16), (32, 32), (48, 48)])

# Android: адаптивная иконка — фон цветом, лицо на весь холст 108 dp (видна середина 72 dp, лицо в безопасном круге 66 dp)
DPI = {'mdpi': 1, 'hdpi': 1.5, 'xhdpi': 2, 'xxhdpi': 3, 'xxxhdpi': 4}
for d, k in DPI.items():
    o = os.path.join(RES, 'mipmap-' + d); os.makedirs(o, exist_ok=True)
    sz(round(108 * k)).save(os.path.join(o, 'ic_launcher_fg.png'), optimize=True)
    # обычная иконка (старые лаунчеры): скруглённый квадрат 48 dp
    n = round(48 * k); im = sz(n).convert('RGBA'); m = Image.new('L', (n, n), 0); ImageDraw.Draw(m).rounded_rectangle((0, 0, n - 1, n - 1), n // 6, fill=255); im.putalpha(m)
    im.save(os.path.join(o, 'ic_launcher.png'), optimize=True)
os.makedirs(os.path.join(RES, 'mipmap-anydpi-v26'), exist_ok=True)
open(os.path.join(RES, 'mipmap-anydpi-v26', 'ic_launcher.xml'), 'w').write(
    '<?xml version="1.0" encoding="utf-8"?>\n<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
    '  <background android:drawable="@color/icon_bg"/>\n  <foreground android:drawable="@mipmap/ic_launcher_fg"/>\n</adaptive-icon>\n')

# Google Play: значок 512 (без прозрачности — маску накладывает магазин) и баннер 1024×500
os.makedirs(PLAY, exist_ok=True)
sz(512).save(os.path.join(PLAY, 'icon-512.png'), optimize=True)
fr = Image.open(os.path.join(HERE, 'feature_frame.jpg')).convert('RGB')   # титульный кадр door_katya@3 (Лили за стеклом)
W, H = 1024, 500; s = max(W / fr.width, H / fr.height) * 1.25; fr = fr.resize((round(fr.width * s), round(fr.height * s)), Image.LANCZOS)   # крупнее: Лили правее названия
x0 = 0; y0 = round((fr.height - H) * .45); im = fr.crop((x0, y0, x0 + W, y0 + H))
shade = Image.new('L', (W, H)); d = ImageDraw.Draw(shade)
for x in range(W): d.line([(x, 0), (x, H)], fill=int(215 * max(0, 1 - x / (W * .6)) ** 1.25))
im = Image.composite(Image.new('RGB', (W, H), (0, 0, 0)), im, shade); dr = ImageDraw.Draw(im)
def text(x, y, s, size, color, sp=2):
    f = ImageFont.truetype(FONT, size); sh = Image.new('L', im.size); ImageDraw.Draw(sh).text((x + 3, y + 5), s, font=f, fill=210)
    im.paste((0, 0, 0), mask=sh.filter(ImageFilter.GaussianBlur(5)))
    for ch in s: dr.text((x, y), ch, font=f, fill=color); x += f.getlength(ch) + sp
y = 48
for s_, size, c in [('HUSH', 168, CREAM), ('TILL SIX', 128, GOLD)]: text(56, y, s_, size, c); y += int(size * .98)
text(60, y + 16, "DON'T ANSWER · DON'T OPEN · WAIT TILL SIX", 26, CREAM, 1)
im.save(os.path.join(PLAY, 'feature-1024x500.png'), optimize=True)
print('иконки: браузер, Android (mipmap-*), dist/play/icon-512.png и feature-1024x500.png')
