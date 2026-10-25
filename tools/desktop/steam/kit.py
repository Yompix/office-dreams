#!/usr/bin/env python3
"""Капсулы, фон библиотеки, логотип и иконки достижений для Steam — из кадров движка (kit.js frames) и шрифта игры (Oswald, OFL).
   python3 tools/desktop/steam/kit.py ПАПКА   (в ПАПКА/frames — кадры от node tools/desktop/steam/kit.js ПАПКА frames)
   → ПАПКА/store/      header_capsule 920×430, small_capsule 462×174, main_capsule 1232×706, vertical_capsule 748×896
     ПАПКА/library/    library_capsule 600×900, library_hero 3840×1240 (без надписей), library_logo.png (прозрачный)
     ПАПКА/achievements/ <API_NAME>.jpg и <API_NAME>_locked.jpg 256×256
     ПАПКА/icons/      community_icon.jpg 184×184, client_icon.ico (16, 32)
   На капсулах — только название игры (правила Steam: без слоганов, оценок и наград)."""
import os, sys
from PIL import Image, ImageDraw, ImageFont, ImageFilter, ImageEnhance, ImageOps
HERE = os.path.dirname(os.path.abspath(__file__)); GAME = os.path.join(HERE, '../../../public/games/nightshift'); FONTS = os.path.join(GAME, 'fonts')
out = sys.argv[1]; FR = os.path.join(out, 'frames')
BONE, GOLD = (236, 232, 220), (242, 194, 27)
LILY = (.52, .5)   # где Лили в горизонтальном кадре door_katya (доли)
for d in ('store', 'library', 'achievements', 'icons'): os.makedirs(os.path.join(out, d), exist_ok=True)

def frame(name): return Image.open(os.path.join(FR, name + '.png')).convert('RGB')
def cover(im, W, H, fx=.5, fy=.5, at=(.5, .5), zoom=1.0):   # кадр на холст W×H: точка (fx, fy) кадра встаёт в точку at холста
    k = max(W / im.width, H / im.height) * zoom; im = im.resize((round(im.width * k), round(im.height * k)), Image.LANCZOS)
    x0 = min(max(0, round(im.width * fx - W * at[0])), im.width - W); y0 = min(max(0, round(im.height * fy - H * at[1])), im.height - H)
    return im.crop((x0, y0, x0 + W, y0 + H))
def shade(im, f):   # затемнение: f(x, y) в долях → 0…1
    W, H = im.size; m = Image.new('L', (W, H)); px = m.load(); sw, shh = max(1, W // 160), max(1, H // 160)
    small = Image.new('L', (W // sw + 1, H // shh + 1)); sp = small.load()
    for y in range(small.height):
        for x in range(small.width): sp[x, y] = int(255 * max(0, min(1, f(x / small.width, y / small.height))))
    m = small.resize((W, H), Image.BILINEAR)
    return Image.composite(Image.new('RGB', (W, H), (0, 0, 0)), im, m)
def logo(im, cx, top, s1, glow=True, anchor='m'):   # HUSH / TILL SIX, как на титуле: кость и золото, мягкая тень
    s2 = int(s1 * .73); f1, f2 = ImageFont.truetype(os.path.join(FONTS, 'ns-logo.woff'), s1), ImageFont.truetype(os.path.join(FONTS, 'ns-logo.woff'), s2)
    lines = (('HUSH', f1, BONE, top, s1 * .045), ('TILL SIX', f2, GOLD, top + int(s1 * .86), s2 * .02))
    def put(dr, fill=None):
        for t, f, c, y, sp in lines:
            w = sum(f.getlength(ch) for ch in t) + sp * (len(t) - 1); x = cx - w / 2 if anchor == 'm' else cx
            for ch in t: dr.text((x, y), ch, font=f, fill=fill or c); x += f.getlength(ch) + sp
    if glow:
        sh = Image.new('L', im.size); put(ImageDraw.Draw(sh), 220)
        im.paste((0, 0, 0), mask=sh.filter(ImageFilter.GaussianBlur(max(2, s1 // 18))))
    put(ImageDraw.Draw(im)); return im
def grade(im): return ImageEnhance.Contrast(im).enhance(1.08)

key, port = frame('key_land'), frame('key_port')
def wide(W, H, s1, lx, at_x=.7, zoom=1.0, dark=.62):   # горизонтальная капсула: кадр крупнее (zoom), Лили справа (at_x), название слева на тёмном
    im = grade(cover(key, W, H, LILY[0], .48, (at_x, .5), zoom))
    im = shade(im, lambda x, y: (max(0, 1 - x / dark)) ** 1.4 * .9 + max(0, y - .82) * .8)
    return logo(im, int(W * lx), int(H * .5 - s1 * .86), s1)
wide(920, 430, 150, .26, .73, 1.45).save(os.path.join(out, 'store/header_capsule.jpg'), quality=94)
wide(1232, 706, 210, .26, .73, 1.45).save(os.path.join(out, 'store/main_capsule.jpg'), quality=94)
sm = grade(cover(key, 462, 174, LILY[0], .47, (.84, .5), 1.8));   # кадр крупнее: Лили справа, название слева не наезжает sm = shade(sm, lambda x, y: (max(0, 1 - x / .78)) ** 1.1 * .93)
logo(sm, 150, 174 // 2 - int(78 * .86), 78).save(os.path.join(out, 'store/small_capsule.jpg'), quality=94)
def tall(W, H, s1):   # вертикальная: название сверху, Лили ниже за стеклом
    im = grade(cover(port, W, H, .5, .55, (.5, .62), 1.0))
    im = shade(im, lambda x, y: max(0, 1 - y / .45) ** 1.3 * .92 + max(0, y - .85) * 1.5)
    return logo(im, W // 2, int(H * .07), s1)
tall(748, 896, 190).save(os.path.join(out, 'store/vertical_capsule.jpg'), quality=94)
tall(600, 900, 160).save(os.path.join(out, 'library/library_capsule.jpg'), quality=94)
hero = frame('hero'); hy = round(hero.height * .5 - 620)
grade(hero.crop((0, hy, hero.width, hy + 1240)).resize((3840, 1240), Image.LANCZOS)).save(os.path.join(out, 'library/library_hero.jpg'), quality=93)
lg = Image.new('RGBA', (1280, 720), (0, 0, 0, 0)); logo(lg, 640, 70, 330)   # логотип библиотеки: прозрачный фон, Steam кладёт его поверх фона
bb = lg.getbbox(); lg.crop((max(0, bb[0] - 24), max(0, bb[1] - 24), min(1280, bb[2] + 24), min(720, bb[3] + 24))).save(os.path.join(out, 'library/library_logo.png'))
# иконки достижений: квадрат из кадра смерти/концовки; закрытая — серая и темнее
for f in sorted(os.listdir(FR)):
    if not f.startswith('ach_'): continue
    name = f[4:-4]; im = frame(f[:-4]); s = min(im.size); x0 = (im.width - s) // 2
    ic = grade(im.crop((x0, 0, x0 + s, s)).resize((256, 256), Image.LANCZOS))
    ic = shade(ic, lambda x, y: max(0, ((x - .5) ** 2 + (y - .5) ** 2) ** .5 - .32) * 1.6)   # виньетка
    d = ImageDraw.Draw(ic); d.rectangle((0, 0, 255, 255), outline=(242, 194, 27) if name.startswith(('END_', 'ALL_', 'NIGHT')) else (170, 24, 32), width=4)
    ic.save(os.path.join(out, 'achievements', name + '.jpg'), quality=94)
    lk = ImageEnhance.Brightness(ImageOps.grayscale(ic).convert('RGB')).enhance(.45)
    lk.save(os.path.join(out, 'achievements', name + '_locked.jpg'), quality=94)
ico = Image.open(os.path.join(GAME, 'icon-512.png')).convert('RGB')
ico.resize((184, 184), Image.LANCZOS).save(os.path.join(out, 'icons/community_icon.jpg'), quality=94)
ico.convert('RGBA').save(os.path.join(out, 'icons/client_icon.ico'), sizes=[(16, 16), (32, 32)])
print('готово:', out)
