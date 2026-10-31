#!/usr/bin/env python3
"""Картинки WHAT THE LAKE KEEPS в godot/lake/assets/textures (папка assets в git не идёт):
рисунки Айрис (глава 4) — пять страниц «как комикс», ночь смерти отца: карандаш на листе в клетку, детская рука, дрожащие
линии в несколько проходов, штриховка, немного красного, без надписей; и фотографии Анны на стене у Агнес (глава 3) —
сепия, готовые снимки лежат в tools/lake/src/photos (сняты кадром из игры), их только копируем.
  python3 tools/lake/drawings.py → drawing_1..5.png, photo_*.png"""
import math, os, random, shutil
from PIL import Image, ImageDraw, ImageFilter

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '../../godot/lake/assets/textures')
W, H = 640, 480
PENCIL = (58, 62, 78)
RED = (150, 36, 30)
rnd = random.Random(4)


def paper():
    im = Image.new('RGB', (W, H), (236, 230, 214))
    d = ImageDraw.Draw(im)
    for x in range(0, W, 24):   # клетка
        d.line([(x, 0), (x, H)], fill=(214, 218, 226), width=1)
    for y in range(0, H, 24):
        d.line([(0, y), (W, y)], fill=(214, 218, 226), width=1)
    d.line([(W - 52, 0), (W - 52, H)], fill=(222, 170, 168), width=2)   # поля
    return im


def wob(d, pts, col=PENCIL, w=3, passes=2, j=2.2):
    """дрожащая линия по точкам, в несколько проходов"""
    for p in range(passes):
        seq = []
        for i in range(len(pts) - 1):
            (x0, y0), (x1, y1) = pts[i], pts[i + 1]
            n = max(2, int(math.hypot(x1 - x0, y1 - y0) / 9))
            for k in range(n):
                t = k / n
                seq.append((x0 + (x1 - x0) * t + rnd.uniform(-j, j), y0 + (y1 - y0) * t + rnd.uniform(-j, j)))
        seq.append((pts[-1][0] + rnd.uniform(-j, j), pts[-1][1] + rnd.uniform(-j, j)))
        d.line(seq, fill=col, width=w if p == 0 else max(1, w - 1), joint='curve')


def circle(d, cx, cy, r, col=PENCIL, w=3, fill=None):
    pts = [(cx + r * math.cos(a) * rnd.uniform(0.95, 1.05), cy + r * math.sin(a) * rnd.uniform(0.95, 1.05))
           for a in [i * math.tau / 24 for i in range(25)]]
    if fill:
        d.polygon(pts, fill=fill)
    wob(d, pts, col, w, 2, 1.2)


def hatch(d, box, col=PENCIL, step=7, w=2):
    """штриховка по диагонали внутри прямоугольника"""
    x0, y0, x1, y1 = box
    bw, bh = x1 - x0, y1 - y0
    c = step
    while c < bw + bh:
        a = (x0 + min(c, bw), y0 + max(0, c - bw))
        b = (x0 + max(0, c - bh), y0 + min(c, bh))
        wob(d, [a, b], col, w, 1, 1.0)
        c += step


def person(d, x, y, s, arms=(-30, -30), eyes='dots', col=PENCIL, w=3):
    """палочный человечек: голова, тело, руки (углы от горизонта), ноги; y — ступни, s — рост"""
    hr = s * 0.12
    hy = y - s + hr
    circle(d, x, hy, hr, col, w)
    if eyes == 'dots':
        d.ellipse([x - hr * 0.45 - 3, hy - 3, x - hr * 0.45 + 3, hy + 3], fill=col)
        d.ellipse([x + hr * 0.45 - 3, hy - 3, x + hr * 0.45 + 3, hy + 3], fill=col)
    elif eyes == 'holes':
        for sx in (-1, 1):
            r = hr * 0.32
            d.ellipse([x + sx * hr * 0.42 - r, hy - r, x + sx * hr * 0.42 + r, hy + r], fill=(18, 18, 22))
    neck = hy + hr
    hip = y - s * 0.42
    wob(d, [(x, neck), (x, hip)], col, w)
    sh = neck + s * 0.08
    for side, ang in ((-1, arms[0]), (1, arms[1])):
        a = math.radians(ang)
        wob(d, [(x, sh), (x + side * s * 0.32 * math.cos(a), sh - s * 0.32 * math.sin(a))], col, w)
    for side in (-1, 1):
        wob(d, [(x, hip), (x + side * s * 0.14, y)], col, w)
    return (x, sh)


def page1():   # лестница, наверху отец с ремнём
    im = paper(); d = ImageDraw.Draw(im)
    pts = []
    x, y = 60, 450
    for k in range(6):
        pts += [(x, y), (x, y - 40)]
        y -= 40
        pts.append((x + 50, y))
        x += 50
    wob(d, pts, PENCIL, 3)
    wob(d, [(60, 450), (x, y)], PENCIL, 2, 1)
    wob(d, [(x, y), (x + 200, y)], PENCIL, 3)          # площадка
    fx, fy, fs = x + 70, y, 190
    person(d, fx, fy, fs, arms=(-40, 25), col=PENCIL, w=4)
    sh = fy - fs + fs * 0.24 + fs * 0.08
    hx, hy = fx + fs * 0.32 * math.cos(math.radians(25)), sh - fs * 0.32 * math.sin(math.radians(25))
    wob(d, [(hx, hy), (hx + 26, hy - 34), (hx + 14, hy - 70), (hx + 36, hy - 100)], (40, 30, 26), 5)   # ремень
    hatch(d, (fx - 22, fy - fs + 4, fx + 22, fy - fs + 30), PENCIL, 5, 1)   # тень на лице
    return im


def page2():   # дверь, за ней Айрис; Ной встаёт между ней и отцом
    im = paper(); d = ImageDraw.Draw(im)
    wob(d, [(60, 440), (60, 120), (190, 120), (190, 440)], PENCIL, 4)
    circle(d, 172, 290, 7, PENCIL, 2)
    person(d, 135, 300, 80, arms=(-60, -60), col=PENCIL, w=2)   # маленькая Айрис выглядывает
    hatch(d, (62, 122, 120, 438), PENCIL, 9, 1)
    person(d, 300, 440, 190, arms=(10, 10), col=PENCIL, w=4)   # Ной: руки в стороны
    person(d, 500, 440, 260, arms=(-20, 35), col=PENCIL, w=5)  # отец — огромный
    wob(d, [(560, 230), (590, 200), (575, 160)], (40, 30, 26), 4)
    return im


def page3():   # лицо Ноя: глаза пустые, как дырки
    im = paper(); d = ImageDraw.Draw(im)
    cx, cy = W // 2 - 20, H // 2 + 10
    pts = [(cx + 150 * math.cos(a) * (1.0 if math.sin(a) < 0 else 1.12), cy + 185 * math.sin(a)) for a in [i * math.tau / 30 for i in range(31)]]
    wob(d, pts, PENCIL, 4, 3, 2.0)
    for sx in (-1, 1):
        ex, ey = cx + sx * 60, cy - 30
        d.ellipse([ex - 38, ey - 30, ex + 38, ey + 30], fill=(14, 14, 18))
        for k in range(14):   # нажим карандаша — штрихи вокруг дыр
            a = rnd.uniform(0, math.tau)
            wob(d, [(ex + 36 * math.cos(a), ey + 28 * math.sin(a)), (ex + 52 * math.cos(a), ey + 42 * math.sin(a))], PENCIL, 2, 1, 1.0)
    wob(d, [(cx - 50, cy + 95), (cx, cy + 92), (cx + 50, cy + 96)], PENCIL, 3)   # рот — прямой
    wob(d, [(cx, cy - 5), (cx - 10, cy + 45), (cx + 6, cy + 48)], PENCIL, 2)
    for k in range(40):   # волосы — штрихами
        x = cx - 140 + k * 7
        wob(d, [(x, cy - 160 + rnd.uniform(-8, 8)), (x + rnd.uniform(-14, 14), cy - 205)], PENCIL, 2, 1, 1.5)
    return im


def page4():   # нож; отец на полу
    im = paper(); d = ImageDraw.Draw(im)
    wob(d, [(70, 120), (250, 70), (262, 84), (82, 134), (70, 120)], PENCIL, 3)   # лезвие
    wob(d, [(262, 84), (330, 66), (338, 82), (270, 100)], (40, 30, 26), 5)          # рукоять
    for k in range(5):
        wob(d, [(150 + k * 18, 135), (148 + k * 18 + rnd.uniform(-4, 4), 160 + rnd.uniform(0, 30))], RED, 3, 1, 1.0)   # капли
    # отец лежит: человечек на боку
    x0, y0 = 150, 380
    circle(d, x0, y0, 26, PENCIL, 4)
    wob(d, [(x0 + 26, y0), (x0 + 230, y0 + 8)], PENCIL, 4)
    wob(d, [(x0 + 80, y0 + 2), (x0 + 120, y0 + 60)], PENCIL, 4)
    wob(d, [(x0 + 80, y0 + 2), (x0 + 60, y0 - 50)], PENCIL, 4)
    wob(d, [(x0 + 230, y0 + 8), (x0 + 300, y0 + 50)], PENCIL, 4)
    wob(d, [(x0 + 230, y0 + 8), (x0 + 310, y0 - 10)], PENCIL, 4)
    for k in range(60):   # лужа — красный штрих
        a = rnd.uniform(0, math.tau)
        r = rnd.uniform(0, 1) ** 0.5
        px, py = x0 + 140 + 110 * r * math.cos(a), y0 + 40 + 26 * r * math.sin(a)
        wob(d, [(px, py), (px + rnd.uniform(-12, 12), py + rnd.uniform(-4, 4))], RED, 2, 1, 0.8)
    wob(d, [(40, 440), (600, 440)], PENCIL, 2, 1)
    return im


def page5():   # Айрис держит брата за руку — он не слышит
    im = paper(); d = ImageDraw.Draw(im)
    person(d, 380, 440, 260, arms=(-70, -70), eyes='holes', col=PENCIL, w=4)   # Ной — глаза-дыры, руки висят
    person(d, 230, 440, 150, arms=(-10, 40), col=PENCIL, w=3)                 # Айрис тянется к нему
    for k in range(3):   # её «крик» — дуги к его голове
        r = 40 + k * 22
        pts = [(285 + r * math.cos(a), 250 + r * math.sin(a)) for a in [math.radians(t) for t in range(-60, 61, 10)]]
        wob(d, pts, PENCIL, 2, 1, 1.2)
    wob(d, [(310, 330), (325, 334)], PENCIL, 4)   # руки сцеплены
    circle(d, 318, 332, 10, RED, 2)
    hatch(d, (330, 160, 430, 200), PENCIL, 7, 1)
    return im


SUN = (214, 160, 40)
BLUE = (58, 96, 160)
BLACK = (24, 24, 28)


def water(d, y0, y1, col=BLUE):
    for y in range(int(y0), int(y1), 16):
        pts = [(x, y + 5 * math.sin(x / 22 + y)) for x in range(10, W - 60, 12)]
        wob(d, pts, col, 2, 1, 1.0)


def dock(d, x0, x1, y):
    wob(d, [(x0, y), (x1, y)], (96, 70, 44), 5)
    for x in range(int(x0), int(x1), 26):
        wob(d, [(x, y), (x, y + 70)], (96, 70, 44), 3, 1)


def page6(car=False):   # они вдвоём на мостках, лето («Я нарисовала, как будет»)
    im = paper(); d = ImageDraw.Draw(im)
    circle(d, 520, 80, 38, SUN, 4)
    for k in range(10):
        a = k * math.tau / 10
        wob(d, [(520 + 48 * math.cos(a), 80 + 48 * math.sin(a)), (520 + 70 * math.cos(a), 80 + 70 * math.sin(a))], SUN, 3, 1)
    water(d, 300, 470)
    dock(d, 40, 380, 300)
    person(d, 230, 300, 150, arms=(-20, -60), col=PENCIL, w=3)    # Ной — сидит на краю
    person(d, 300, 300, 100, arms=(-60, -20), col=PENCIL, w=3)    # Айрис рядом
    wob(d, [(254, 285), (276, 285)], RED, 3)                        # держатся за руки
    if car:   # чёрная машина на берегу
        d.rectangle([60, 190, 190, 240], fill=BLACK)
        d.polygon([(85, 190), (110, 160), (160, 160), (178, 190)], fill=BLACK)
        for cx in (90, 160):
            d.ellipse([cx - 16, 228, cx + 16, 260], fill=(10, 10, 12))
        circle(d, 52, 212, 9, SUN, 2)
    return im


def page_family():   # детский рисунок фломастером на стене дачи: трое человечков и озеро (мама, Ной, Айрис)
    im = Image.new('RGB', (W, H), (238, 232, 214)); d = ImageDraw.Draw(im)
    water(d, 330, 470, (40, 110, 200))
    circle(d, 110, 90, 40, SUN, 6)
    person(d, 300, 330, 210, arms=(-10, -20), col=(200, 60, 120), w=6)     # мама — розовым
    person(d, 400, 330, 130, arms=(-20, 10), col=(40, 140, 60), w=5)       # Ной — зелёным
    person(d, 200, 330, 95, arms=(10, -30), col=(230, 120, 30), w=5)       # Айрис — оранжевым
    return im


def page8():   # Ной и мама держатся за руки под водой («Лёд»)
    im = paper(); d = ImageDraw.Draw(im)
    d.rectangle([0, 0, W - 54, H], fill=(196, 210, 228))
    water(d, 40, 460, (90, 120, 170))
    person(d, 250, 400, 220, arms=(30, -10), col=PENCIL, w=3)       # мама
    for k in range(18):   # волосы плывут вверх
        wob(d, [(230 + k * 3, 190), (215 + k * 4, 140 - (k % 4) * 8)], PENCIL, 2, 1, 1.2)
    person(d, 390, 400, 200, arms=(-10, 30), col=PENCIL, w=3)       # Ной
    wob(d, [(318, 262), (338, 262)], RED, 3)
    for k in range(12):
        circle(d, rnd.uniform(150, 480), rnd.uniform(40, 160), rnd.uniform(4, 10), (70, 100, 160), 2)
    return im


def page9(text):   # «Я ПОМНЮ ЗА ТЕБЯ» — крупно, маркером
    from PIL import ImageFont
    im = Image.new('RGB', (W, H), (240, 236, 224)); d = ImageDraw.Draw(im)
    lines = text.split('\n')
    size = 80
    while True:   # самая длинная строка — с полями по краям листа
        f = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', size)
        if max(d.textlength(ln, font=f) for ln in lines) <= W - 110 or size <= 30:
            break
        size -= 4
    y = H / 2 - len(lines) * 50
    for ln in lines:
        w = d.textlength(ln, font=f)
        for k in range(3):   # маркер в несколько проходов, рука дрожит
            d.text((W / 2 - w / 2 + rnd.uniform(-2, 2), y + rnd.uniform(-2, 2)), ln, font=f, fill=(30, 40, 90))
        y += 100
    return im


def page10():   # мостки и фонарь — рисует взрослый Ной в белой комнате
    im = Image.new('RGB', (W, H), (246, 246, 244)); d = ImageDraw.Draw(im)
    water(d, 280, 460, (120, 130, 140))
    dock(d, 120, 470, 280)
    wob(d, [(440, 280), (440, 150)], PENCIL, 3)
    wob(d, [(440, 150), (470, 150)], PENCIL, 3)
    wob(d, [(462, 150), (462, 175), (480, 175), (480, 205), (444, 205), (444, 175), (462, 175)], PENCIL, 2)
    for k in range(8):
        a = k * math.tau / 8
        wob(d, [(462 + 26 * math.cos(a), 190 + 26 * math.sin(a)), (462 + 40 * math.cos(a), 190 + 40 * math.sin(a))], (150, 140, 90), 2, 1)
    return im


if __name__ == '__main__':
    os.makedirs(OUT, exist_ok=True)
    for i, f in enumerate((page1, page2, page3, page4, page5), 1):
        im = f().filter(ImageFilter.GaussianBlur(0.6))
        im.save(os.path.join(OUT, f'drawing_{i}.png'))
        print('drawing', i)
    for name, im in (('drawing_family', page_family()), ('drawing_dock', page6()), ('drawing_car', page6(True)), ('drawing_under', page8()),
                     ('drawing_sign_ru', page9('Я ПОМНЮ\nЗА ТЕБЯ')), ('drawing_sign_en', page9("I'LL REMEMBER\nFOR YOU")), ('drawing_lantern', page10())):
        im.filter(ImageFilter.GaussianBlur(0.6)).save(os.path.join(OUT, name + '.png'))
        print('drawing', name)
    src = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'src/photos')
    for n in sorted(os.listdir(src)):
        shutil.copy(os.path.join(src, n), os.path.join(OUT, n))
        print('photo', n)
