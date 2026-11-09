#!/usr/bin/env python3
"""Рисует все текстуры и звуки Novograd (пиксель-арт и синтез), чтобы не зависеть от чужих файлов.
Запуск из папки novograd: python3 tools/gen_assets.py
"""
import json, math, os, random, subprocess, wave
import numpy as np
from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'assets', 'novograd')
TEX = os.path.join(ROOT, 'textures')
SND = os.path.join(ROOT, 'sounds')
rnd = random.Random(7)


def save(img, *path):
    p = os.path.join(TEX, *path)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    img.save(p)


def anim(frames, *path, frametime=4, interpolate=False):
    """Склеивает кадры в вертикальную ленту + .mcmeta, Minecraft сам анимирует."""
    w, h = frames[0].size
    strip = Image.new('RGBA', (w, h * len(frames)))
    for i, f in enumerate(frames):
        strip.paste(f, (0, i * h))
    save(strip, *path)
    with open(os.path.join(TEX, *path) + '.mcmeta', 'w') as f:
        json.dump({'animation': {'frametime': frametime, 'interpolate': interpolate}}, f)


def rgb(h):
    h = h.lstrip('#')
    if len(h) == 3:
        h = ''.join(c * 2 for c in h)
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


def shade(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c[:3]) + (c[3] if len(c) > 3 else 255,)


def noise(size, base, amp=10, seed=0):
    r = random.Random(seed)
    img = Image.new('RGBA', size)
    px = img.load()
    for y in range(size[1]):
        for x in range(size[0]):
            d = r.randint(-amp, amp)
            px[x, y] = tuple(max(0, min(255, v + d)) for v in base[:3]) + (255,)
    return img


# ---------- блоки ----------
def blocks():
    # дороги и тротуары в 32×32: щебень, трещины, затёртая разметка
    R = random.Random(11)

    def asphalt_tex(seed):
        img = noise((32, 32), rgb('3a3d42'), 7, seed)
        px = img.load()
        rr = random.Random(seed)
        for _ in range(70):
            x, y = rr.randrange(32), rr.randrange(32)
            px[x, y] = shade(rgb('5a5e66'), rr.uniform(0.8, 1.25))
        for _ in range(25):
            x, y = rr.randrange(32), rr.randrange(32)
            px[x, y] = rgb('26282c')
        d = ImageDraw.Draw(img)
        if rr.random() < 0.6:
            x, y = rr.randrange(32), rr.randrange(32)
            pts = [(x, y)]
            for _ in range(5):
                x += rr.randint(-3, 3)
                y += rr.randint(1, 4)
                pts.append((x, y))
            d.line(pts, fill=rgb('2a2c30'))
        return img

    def worn(d, box, col, rr):
        d.rectangle(box, fill=col)
        for _ in range(10):
            x, y = rr.randint(box[0], box[2]), rr.randint(box[1], box[3])
            d.point([(x, y)], fill=shade(col, 0.72))

    save(asphalt_tex(1), 'block', 'asphalt.png')
    line = asphalt_tex(2)
    worn(ImageDraw.Draw(line), [14, 0, 17, 19], rgb('f2c335'), R)
    save(line, 'block', 'asphalt_line.png')
    edge = asphalt_tex(3)
    worn(ImageDraw.Draw(edge), [0, 0, 3, 31], rgb('e8e8e8'), R)
    save(edge, 'block', 'asphalt_edge.png')
    cross = asphalt_tex(4)
    d = ImageDraw.Draw(cross)
    for x in (2, 12, 22):
        worn(d, [x, 0, x + 6, 31], rgb('f0f0f0'), R)
    save(cross, 'block', 'crosswalk.png')

    side = Image.new('RGBA', (32, 32))
    d = ImageDraw.Draw(side)
    for ty in range(2):
        for tx in range(2):
            c = shade(rgb('bdb8ad'), 1 + R.uniform(-.05, .05))
            x0, y0 = tx * 16, ty * 16
            d.rectangle([x0, y0, x0 + 15, y0 + 15], fill=c)
            for _ in range(30):
                d.point([(x0 + R.randrange(1, 15), y0 + R.randrange(1, 15))], fill=shade(c, R.uniform(0.9, 1.08)))
            d.line([x0, y0, x0 + 15, y0], fill=shade(c, 1.12))
            d.line([x0, y0, x0, y0 + 15], fill=shade(c, 1.08))
            d.line([x0, y0 + 15, x0 + 15, y0 + 15], fill=rgb('8a857c'))
            d.line([x0 + 15, y0, x0 + 15, y0 + 15], fill=rgb('8a857c'))
    save(side, 'block', 'sidewalk.png')

    # Школьная доска: зелёная, мел, рамка из дерева
    board = Image.new('RGBA', (16, 16), rgb('6b4a2b'))
    d = ImageDraw.Draw(board)
    d.rectangle([1, 1, 14, 12], fill=rgb('2e5e3e'))
    for (x, y, w) in ((3, 3, 6), (3, 5, 9), (3, 7, 4), (9, 7, 3), (3, 9, 7)):
        d.line([x, y, x + w, y], fill=rgb('dfe8df'))
    d.rectangle([1, 13, 14, 14], fill=rgb('8a6440'))
    d.point([(4, 13), (10, 13)], fill=rgb('ffffff'))
    save(board, 'block', 'chalkboard.png')
    save(noise((16, 16), rgb('6b4a2b'), 6, 3), 'block', 'chalkboard_back.png')

    # Парта
    top = noise((16, 16), rgb('b88a57'), 7, 4)
    d = ImageDraw.Draw(top)
    for y in (3, 8, 13):
        d.line([0, y, 15, y], fill=rgb('a07648'))
    save(top, 'block', 'desk_top.png')
    save(noise((16, 16), rgb('8b8f96'), 5, 5), 'block', 'desk_leg.png')

    # Телевизор: корпус + каналы (анимированные ленты 32x32)
    save(noise((16, 16), rgb('1c1d22'), 4, 6), 'block', 'tv_body.png')
    off = Image.new('RGBA', (32, 32), rgb('08090b'))
    ImageDraw.Draw(off).line([2, 2, 8, 2], fill=rgb('1d2128'))
    save(off, 'block', 'tv_off.png')
    tv_channels()

    # Светофор
    for state, lit in (('red', 0), ('yellow', 1), ('green', 2)):
        img = Image.new('RGBA', (16, 16), rgb('22252a'))
        d = ImageDraw.Draw(img)
        d.rectangle([4, 0, 11, 15], fill=rgb('2c3036'))
        cols = [rgb('ff3b30'), rgb('ffcc00'), rgb('34c759')]
        for i, c in enumerate(cols):
            y = 1 + i * 5
            col = c if i == lit else shade(c, .25)
            d.ellipse([5, y, 10, y + 4], fill=col)
            if i == lit:
                d.point([(6, y + 1)], fill=(255, 255, 255, 255))
        save(img, 'block', f'traffic_light_{state}.png')
    save(noise((16, 16), rgb('2c3036'), 3, 8), 'block', 'traffic_light_side.png')

    # Фонарь
    pole = noise((16, 16), rgb('4a4f57'), 4, 9)
    save(pole, 'block', 'lamp_pole.png')
    lamp = Image.new('RGBA', (16, 16), rgb('fff4c2'))
    d = ImageDraw.Draw(lamp)
    d.rectangle([3, 3, 12, 12], fill=rgb('fffbe6'))
    save(lamp, 'block', 'lamp_light.png')

    # Банкомат
    atm = noise((16, 16), rgb('2f6db5'), 5, 10)
    d = ImageDraw.Draw(atm)
    d.rectangle([3, 2, 12, 7], fill=rgb('0c1a2b'))
    d.line([4, 4, 9, 4], fill=rgb('5be37d'))
    d.line([4, 6, 7, 6], fill=rgb('5be37d'))
    for i in range(3):
        for j in range(3):
            d.point([(5 + j * 2, 9 + i * 2)], fill=rgb('dfe6ee'))
    d.rectangle([3, 14, 12, 14], fill=rgb('111111'))
    save(atm, 'block', 'atm_front.png')
    save(noise((16, 16), rgb('2a5f9e'), 4, 11), 'block', 'atm_side.png')

    # Доска вакансий
    jb = noise((16, 16), rgb('a8784a'), 6, 12)
    d = ImageDraw.Draw(jb)
    for (x, y, c) in ((2, 2, 'fff6d5'), (9, 3, 'd5f0ff'), (3, 9, 'ffd5e0'), (10, 9, 'e2ffd5')):
        d.rectangle([x, y, x + 4, y + 4], fill=rgb(c))
        d.point([(x + 2, y)], fill=rgb('d33'))
        d.line([x + 1, y + 2, x + 3, y + 2], fill=rgb('777777'))
    save(jb, 'block', 'job_board.png')

    # Почтовый ящик
    mb = noise((16, 16), rgb('2f63c8'), 5, 13)
    d = ImageDraw.Draw(mb)
    d.rectangle([3, 4, 12, 5], fill=rgb('0d1d3d'))
    d.rectangle([5, 8, 10, 11], fill=rgb('f4f4f4'))
    d.line([5, 8, 7, 10, 10, 8], fill=rgb('2f63c8'))
    save(mb, 'block', 'mailbox_front.png')
    save(noise((16, 16), rgb('2a59b4'), 4, 14), 'block', 'mailbox_side.png')

    # Касса / прилавок
    cnt = noise((16, 16), rgb('d9d4c7'), 4, 15)
    d = ImageDraw.Draw(cnt)
    d.rectangle([0, 0, 15, 2], fill=rgb('e05a47'))
    save(cnt, 'block', 'shop_counter.png')
    save(noise((16, 16), rgb('efeae0'), 3, 16), 'block', 'shop_counter_top.png')

    # Игровой автомат: красный корпус, три окошка, мигающие лампочки
    frames = []
    for f in range(6):
        img = noise((16, 16), rgb('b3122e'), 5, 30)
        d = ImageDraw.Draw(img)
        d.rectangle([1, 1, 14, 3], fill=rgb('2a0710'))
        d.text((2, -2), '777', fill=rgb('ffd54f'))
        for i in range(3):
            d.rectangle([2 + i * 4, 5, 4 + i * 4, 9], fill=rgb('f4f4f4'))
            d.point([(3 + i * 4, 7)], fill=[rgb('e3242b'), rgb('ffd400'), rgb('5ad1ff')][(i + f) % 3])
        d.rectangle([1, 11, 14, 12], fill=rgb('e0b040'))
        d.rectangle([5, 13, 10, 14], fill=rgb('1a1a1a'))
        for i in range(8):
            on = (i + f) % 3 == 0
            d.point([(i * 2, 0)], fill=rgb('fff59d') if on else rgb('7a5a20'))
            d.point([(i * 2 + 1, 15)], fill=rgb('fff59d') if not on else rgb('7a5a20'))
        frames.append(img)
    anim(frames, 'block', 'slot_machine_front.png', frametime=5)
    save(noise((16, 16), rgb('8a0f22'), 5, 31), 'block', 'slot_machine_side.png')

    # Стол рулетки: колесо на зелёном сукне
    rt = Image.new('RGBA', (32, 32), rgb('1b6b3a'))
    d = ImageDraw.Draw(rt)
    d.ellipse([2, 2, 29, 29], fill=rgb('6b3e1c'))
    for i in range(37):
        a0 = i * 360 / 37
        col = rgb('1b8e3a') if i == 0 else (rgb('c62828') if i % 2 else rgb('1a1a1a'))
        d.pieslice([4, 4, 27, 27], a0, a0 + 360 / 37, fill=col)
    d.ellipse([9, 9, 22, 22], fill=rgb('8b5a2b'))
    d.line([15, 10, 15, 21], fill=rgb('d4af37'))
    d.line([10, 15, 21, 15], fill=rgb('d4af37'))
    d.ellipse([14, 14, 17, 17], fill=rgb('f0d060'))
    save(rt, 'block', 'roulette_top.png')
    rs = noise((16, 16), rgb('4a2c14'), 5, 32)
    ImageDraw.Draw(rs).rectangle([0, 0, 15, 2], fill=rgb('1b6b3a'))
    save(rs, 'block', 'roulette_side.png')

    # Неон: светящаяся трубка 8 цветов (вкл/выкл)
    NEON = {'pink': 'ff2fb4', 'purple': 'a855f7', 'cyan': '22e5ff', 'blue': '3d7bff', 'yellow': 'ffe14d', 'green': '39ff88', 'red': 'ff3b3b', 'orange': 'ff9a1f'}
    for name, col in NEON.items():
        c = rgb(col)
        on = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
        d = ImageDraw.Draw(on)
        d.rectangle([0, 0, 15, 15], fill=c)
        d.rectangle([0, 3, 15, 12], fill=shade(c, 1.25))
        d.rectangle([0, 6, 15, 9], fill=(255, 255, 255, 255))
        for x in range(0, 16, 4):
            d.point([(x, 6), (x + 2, 9)], fill=shade(c, 1.3))
        save(on, 'block', f'neon_{name}.png')
    off = Image.new('RGBA', (16, 16), rgb('3a3d45'))
    ImageDraw.Draw(off).rectangle([0, 6, 15, 9], fill=rgb('5a5f6a'))
    save(off, 'block', 'neon_off.png')

    # Компьютер: монитор с бегущим кодом
    save(noise((16, 16), rgb('d7d9dc'), 3, 17), 'block', 'computer_body.png')
    frames = []
    lines = [[rnd.choice('5be37d,8ab4f8,f28b82,fdd663'.split(',')), rnd.randint(1, 4), rnd.randint(3, 11)] for _ in range(40)]
    for f in range(16):
        img = Image.new('RGBA', (32, 32), rgb('0f1419'))
        d = ImageDraw.Draw(img)
        for i in range(13):
            c, x, w = lines[(i + f) % len(lines)]
            d.line([x * 2, 2 + i * 2, x * 2 + w * 2, 2 + i * 2], fill=rgb(c))
        if f % 2 == 0:
            d.rectangle([4, 28, 5, 29], fill=rgb('ffffff'))
        frames.append(img)
    anim(frames, 'block', 'computer_screen.png', frametime=6)


def tv_channels():
    W = 32
    # 1. Новости: студия, ведущий, бегущая строка
    frames = []
    for f in range(24):
        img = Image.new('RGBA', (W, W), rgb('1b3a6b'))
        d = ImageDraw.Draw(img)
        d.rectangle([0, 0, W, 4], fill=rgb('c62828'))
        d.text((1, -2), 'NEWS', fill=rgb('ffffff'))
        d.rectangle([20, 7, 30, 18], fill=rgb('25528f'))
        d.rectangle([22, 9, 28, 13], fill=rgb('8ec9ff'))
        # ведущий
        d.ellipse([7, 9, 14, 16], fill=rgb('e0b089'))
        d.rectangle([8, 9, 13, 10], fill=rgb('3b2a1e'))
        mouth = f % 4 < 2
        d.line([9, 14, 12, 14], fill=rgb('7a3b2e') if mouth else rgb('c48b6c'))
        d.point([(9, 12), (12, 12)], fill=rgb('222222'))
        d.rectangle([5, 17, 16, 25], fill=rgb('2b2f3a'))
        d.polygon([(10, 17), (11, 17), (11, 21)], fill=rgb('d32f2f'))
        d.rectangle([0, 22, W, 26], fill=rgb('e8e8e8'))
        d.rectangle([0, 26, W, 31], fill=rgb('0d1b2e'))
        for i in range(8):
            x = (i * 9 - f * 2) % 72 - 8
            d.line([x, 28, x + 5, 28], fill=rgb('ffd54f'))
        frames.append(img)
    anim(frames, 'block', 'tv_news.png', frametime=3)

    # 2. Мультик: прыгающий колобок на поляне
    frames = []
    for f in range(20):
        img = Image.new('RGBA', (W, W), rgb('86d0ff'))
        d = ImageDraw.Draw(img)
        d.ellipse([22, 2, 29, 9], fill=rgb('ffe14d'))
        cx = (f * 3) % 44 - 6
        d.ellipse([cx, 4, cx + 9, 8], fill=rgb('ffffff'))
        d.rectangle([0, 22, W, W], fill=rgb('5cc85c'))
        y = 22 - int(abs(math.sin(f / 20 * math.pi * 2)) * 10) - 7
        d.ellipse([11, y, 19, y + 8], fill=rgb('ff9f1a'))
        d.point([(13, y + 3), (17, y + 3)], fill=rgb('000000'))
        d.arc([13, y + 3, 17, y + 7], 0, 180, fill=rgb('7a2b00'))
        frames.append(img)
    anim(frames, 'block', 'tv_cartoon.png', frametime=2)

    # 3. Природа: закат над морем, волны, чайка
    frames = []
    for f in range(16):
        img = Image.new('RGBA', (W, W))
        d = ImageDraw.Draw(img)
        for y in range(18):
            t = y / 18
            d.line([0, y, W, y], fill=(int(255 - 60 * t), int(140 + 40 * t), int(90 + 120 * t), 255))
        d.ellipse([11, 11, 21, 21], fill=rgb('ffcf5c'))
        d.rectangle([0, 18, W, W], fill=rgb('1f5e8c'))
        for i in range(6):
            x = (i * 7 + f) % 36 - 3
            yy = 20 + (i * 3) % 10
            d.line([x, yy, x + 3, yy], fill=rgb('9fd3f5'))
        gx = (f * 2) % 40 - 4
        d.line([gx, 7, gx + 2, 8 + (f % 2), gx + 4, 7], fill=rgb('222222'))
        frames.append(img)
    anim(frames, 'block', 'tv_nature.png', frametime=4)

    # 4. Музыка: эквалайзер
    frames = []
    for f in range(16):
        img = Image.new('RGBA', (W, W), rgb('170b2e'))
        d = ImageDraw.Draw(img)
        for i in range(7):
            h = int(4 + 18 * abs(math.sin(f * .7 + i * 1.3)))
            c = [rgb('ff4fa3'), rgb('a855f7'), rgb('22d3ee')][i % 3]
            d.rectangle([2 + i * 4, 28 - h, 4 + i * 4, 28], fill=c)
        d.text((4, -1), 'MTV', fill=rgb('ffffff'))
        frames.append(img)
    anim(frames, 'block', 'tv_music.png', frametime=2)

    # 5. Спорт: футбольное поле, мяч летает
    frames = []
    for f in range(24):
        img = Image.new('RGBA', (W, W), rgb('2f8f3a'))
        d = ImageDraw.Draw(img)
        for x in range(0, W, 8):
            d.rectangle([x, 0, x + 3, W], fill=rgb('348f40'))
        d.line([16, 0, 16, W], fill=rgb('e8ffe8'))
        d.ellipse([11, 11, 21, 21], outline=rgb('e8ffe8'))
        d.rectangle([0, 11, 2, 21], outline=rgb('e8ffe8'))
        d.rectangle([29, 11, 31, 21], outline=rgb('e8ffe8'))
        t = f / 24
        bx = int(4 + 24 * abs(math.sin(t * math.pi)))
        by = int(16 + 9 * math.sin(t * math.pi * 4))
        d.ellipse([bx, by, bx + 2, by + 2], fill=rgb('ffffff'))
        for i, (px_, py_, c) in enumerate(((8, 8, 'e53935'), (24, 22, '1e88e5'), (12, 24, 'e53935'), (22, 6, '1e88e5'))):
            jx = px_ + int(2 * math.sin(f / 3 + i))
            d.rectangle([jx, py_, jx + 1, py_ + 2], fill=rgb(c))
        d.rectangle([0, 0, 13, 4], fill=rgb('111111'))
        d.text((1, -2), '1:0', fill=rgb('ffffff'))
        frames.append(img)
    anim(frames, 'block', 'tv_sport.png', frametime=2)


# ---------- предметы ----------
def items():
    def item(name, draw):
        img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
        draw(ImageDraw.Draw(img), img)
        save(img, 'item', name + '.png')

    def phone(d, img):
        d.rounded_rectangle([4, 1, 11, 14], 1, fill=rgb('1b1c20'))
        d.rectangle([5, 3, 10, 12], fill=rgb('4aa3ff'))
        d.rectangle([5, 3, 10, 5], fill=rgb('7cc0ff'))
        d.point([(6, 7), (9, 7), (6, 10), (9, 10)], fill=rgb('ffffff'))
        d.line([7, 13, 8, 13], fill=rgb('555555'))
    item('phone', phone)

    def parcel(d, img):
        d.rectangle([2, 4, 13, 13], fill=rgb('c8945a'))
        d.rectangle([2, 4, 13, 6], fill=rgb('dcaa70'))
        d.line([7, 4, 7, 13], fill=rgb('8a5f33'))
        d.line([8, 4, 8, 13], fill=rgb('e8d5a8'))
        d.rectangle([9, 9, 12, 11], fill=rgb('ffffff'))
    item('parcel', parcel)

    def diploma(d, img):
        d.rectangle([2, 3, 13, 12], fill=rgb('f5ecd2'))
        d.rectangle([2, 3, 13, 3], fill=rgb('c9b37a'))
        for y in (5, 7, 9):
            d.line([4, y, 11, y], fill=rgb('8a7a5a'))
        d.ellipse([9, 9, 13, 13], fill=rgb('d33'))
        d.line([10, 13, 9, 15], fill=rgb('d33'))
        d.line([12, 13, 13, 15], fill=rgb('d33'))
    item('diploma', diploma)

    def banknote(d, img):
        d.rectangle([1, 4, 14, 11], fill=rgb('5fb86b'))
        d.rectangle([2, 5, 13, 10], outline=rgb('3d8a48'))
        d.ellipse([6, 6, 9, 9], fill=rgb('e7f6e0'))
        d.point([(7, 7)], fill=rgb('3d8a48'))
    item('banknote', banknote)

    def note(paper, dark, stripe):
        def draw(d, img):
            d.rectangle([1, 4, 14, 11], fill=rgb(paper))
            d.rectangle([2, 5, 13, 10], outline=rgb(dark))
            d.rectangle([3, 6, 4, 9], fill=rgb(stripe))
            d.ellipse([7, 6, 10, 9], fill=rgb('f6f0e0'))
            d.point([(8, 7), (9, 8)], fill=rgb(dark))
            d.line([(11, 6), (12, 9)], fill=rgb(dark))
        return draw
    item('banknote_50', note('6f8fe0', '3a55a8', 'c7d3ff'))
    item('banknote_100', note('e8944a', 'a8521e', 'ffe0b8'))

    def burger(d, img):
        d.chord([2, 3, 13, 11], 180, 360, fill=rgb('e0a24a'))
        d.point([(5, 5), (8, 4), (10, 6)], fill=rgb('fff3c4'))
        d.rectangle([2, 7, 13, 8], fill=rgb('5bbd3a'))
        d.rectangle([2, 9, 13, 10], fill=rgb('6b3a1f'))
        d.rectangle([2, 11, 13, 11], fill=rgb('f2c84b'))
        d.rectangle([3, 12, 12, 13], fill=rgb('d8913c'))
    item('burger', burger)

    def pizza(d, img):
        d.pieslice([1, 1, 14, 14], 0, 360, fill=rgb('e8b25a'))
        d.pieslice([2, 2, 13, 13], 0, 360, fill=rgb('d8452b'))
        for (x, y) in [(5, 5), (9, 4), (10, 9), (5, 10), (7, 7)]:
            d.ellipse([x - 1, y - 1, x + 1, y + 1], fill=rgb('f6e3a1'))
        d.point([(6, 8), (11, 6), (8, 11)], fill=rgb('3f8f2f'))
        d.line([(7, 1), (7, 14)], fill=rgb('c99a48'))
    item('pizza', pizza)

    def salad(d, img):
        d.chord([1, 6, 14, 15], 0, 180, fill=rgb('f0f0f0'))
        d.ellipse([2, 4, 13, 10], fill=rgb('4caf50'))
        d.point([(4, 6), (9, 5), (11, 7)], fill=rgb('e53935'))
        d.point([(6, 7), (8, 6)], fill=rgb('ffeb3b'))
        d.point([(5, 5), (10, 8)], fill=rgb('81c784'))
    item('salad', salad)

    def cola(d, img):
        d.rectangle([5, 2, 10, 14], fill=rgb('d7262c'))
        d.rectangle([5, 2, 10, 3], fill=rgb('c0c4c8'))
        d.rectangle([5, 13, 10, 14], fill=rgb('c0c4c8'))
        d.line([6, 7, 9, 8], fill=rgb('ffffff'))
    item('cola', cola)

    def book(d, img):
        d.rectangle([3, 2, 12, 14], fill=rgb('2c6ecb'))
        d.rectangle([3, 2, 4, 14], fill=rgb('1d4f99'))
        d.rectangle([6, 5, 10, 7], fill=rgb('ffffff'))
        d.rectangle([12, 3, 12, 13], fill=rgb('f0ead8'))
    item('textbook', book)

    def money_card(d, img):
        d.rounded_rectangle([1, 4, 14, 12], 1, fill=rgb('2b2f77'))
        d.rectangle([1, 6, 14, 7], fill=rgb('111111'))
        d.rectangle([3, 9, 5, 10], fill=rgb('f2c84b'))
        d.line([8, 10, 12, 10], fill=rgb('aab'))
    item('bank_card', money_card)

    for name, col in (('red', 'd7262c'), ('blue', '2c6ecb'), ('yellow', 'f2c032'), ('black', '2a2c31'), ('white', 'e8e8ea'), ('taxi', 'f7c600')):
        def car(d, img, col=col, name=name):
            body = rgb(col)
            d.rectangle([1, 7, 14, 11], fill=body)
            d.polygon([(4, 7), (5, 4), (11, 4), (12, 7)], fill=shade(body, .9))
            d.polygon([(5, 7), (6, 5), (8, 5), (8, 7)], fill=rgb('9fd6ff'))
            d.polygon([(9, 7), (9, 5), (10, 5), (11, 7)], fill=rgb('9fd6ff'))
            d.ellipse([2, 10, 5, 13], fill=rgb('1a1a1a'))
            d.ellipse([10, 10, 13, 13], fill=rgb('1a1a1a'))
            d.point([(3, 11), (11, 11)], fill=rgb('aaaaaa'))
            d.point([(14, 8)], fill=rgb('fff3a0'))
            d.point([(1, 8)], fill=rgb('ff4040'))
            if name == 'taxi':
                d.rectangle([7, 2, 8, 3], fill=rgb('222222'))
        item('car_' + name, car)

    def suit(d, img):
        d.polygon([(4, 2), (11, 2), (13, 5), (13, 14), (2, 14), (2, 5)], fill=rgb('1c1f2a'))
        d.polygon([(6, 2), (9, 2), (8, 9), (7, 9)], fill=rgb('f4f4f4'))
        d.line([7, 3, 7, 8], fill=rgb('c62828'))
        d.line([8, 3, 8, 8], fill=rgb('a51f1f'))
        d.line([5, 3, 7, 9], fill=rgb('2c3142'))
        d.line([10, 3, 8, 9], fill=rgb('2c3142'))
        d.point([(9, 11), (9, 13)], fill=rgb('d4af37'))
    item('suit', suit)

    def scooter(d, img):
        d.rectangle([3, 11, 12, 12], fill=rgb('1e88e5'))
        d.line([11, 3, 11, 11], fill=rgb('b0bec5'))
        d.line([9, 3, 13, 3], fill=rgb('263238'))
        d.rectangle([4, 8, 7, 9], fill=rgb('212121'))
        d.line([5, 9, 5, 11], fill=rgb('90a4ae'))
        d.ellipse([2, 11, 5, 14], fill=rgb('151515'))
        d.ellipse([10, 11, 13, 14], fill=rgb('151515'))
        d.point([(12, 5)], fill=rgb('fff6c8'))
    item('scooter', scooter)

    def key(d, img):
        d.ellipse([2, 2, 7, 7], fill=rgb('2a2c31'))
        d.ellipse([4, 4, 5, 5], fill=rgb('888888'))
        d.line([7, 7, 13, 13], fill=rgb('c0c4c8'), width=2)
        d.point([(11, 13), (13, 11)], fill=rgb('c0c4c8'))
    item('car_key', key)


# ---------- машина и жители ----------
def entities():
    # Машина: общая серо-белая текстура с лёгким шумом, цвет даёт рендер (tint).
    img = noise((64, 64), (236, 236, 236), 7, 20)
    save(img, 'entity', 'car', 'base.png')
    glow = Image.new('RGBA', (64, 64), (255, 255, 255, 255))
    save(glow, 'entity', 'car', 'glow.png')

    # мяч: белые и чёрные пятна на развёртке куба 7×7
    ball = Image.new('RGBA', (28, 14), rgb('f4f4f4'))
    d = ImageDraw.Draw(ball)
    for (x, y) in ((9, 2), (16, 9), (2, 9), (23, 9), (9, 9), (16, 2)):
        d.rectangle([x, y, x + 2, y + 2], fill=rgb('1a1a1a'))
    save(ball, 'entity', 'ball.png')

    variants = {
        'teacher':    dict(skin='e9c1a0', hair='5a3a22', top='sweater', shirt='7b4fa1', pants='2b2b3a', shoes='3a2416', style='bob', glasses=True, slim=True, dress=True, girl=True),
        'shopkeeper': dict(skin='d9a47c', hair='1f1f1f', top='tshirt', shirt='e05a47', pants='3c4a6b', shoes='222222', style='short', apron=True),
        'student':    dict(skin='f0c8a6', hair='a0662e', top='tshirt', shirt='2c6ecb', pants='3a3a46', shoes='eeeeee', style='short', bag=True),
        'student2':   dict(skin='c99067', hair='2a1a10', top='tshirt', shirt='e8e8e8', pants='5a2a3a', shoes='222222', style='ponytail', bag=True, slim=True, dress=True, girl=True),
        'worker':     dict(skin='e0b48f', hair='7a5230', top='jacket', shirt='f28c28', inner='dfe6ee', pants='2f3b55', shoes='4a3020', style='short', helmet=True, beard='7a5230'),
        'office':     dict(skin='efc9a8', hair='3a2a1a', top='suit', shirt='2a2d36', inner='f4f4f4', pants='2a2d36', shoes='111111', style='short', tie='c62828'),
        'police':     dict(skin='d8a982', hair='222222', top='jacket', shirt='1d3f8a', inner='9fb8e8', pants='1b2440', shoes='111111', style='buzz', cap='1b2440', badge=True),
        'grandma':    dict(skin='edc7ab', hair='d9d9d9', top='sweater', shirt='9c5a7a', pants='5a3a4a', shoes='3a2a2a', style='bob', glasses=True, slim=True, dress=True, girl=True),
        # герои сюжета
        'mom':        dict(skin='efc6a4', hair='6b3d1f', top='sweater', shirt='e91e63', pants='37474f', shoes='5d4037', style='long', slim=True, blush=True, girl=True),
        'dad':        dict(skin='e2b48e', hair='3e2723', top='jacket', shirt='1565c0', inner='eceff1', pants='263238', shoes='3e2723', style='short', beard='3e2723'),
        'tema':       dict(skin='f3cbb0', hair='d2691e', top='hoodie', shirt='2e7d32', pants='37474f', shoes='fafafa', style='curly', freckles=True),
        'lisa':       dict(skin='f6d2b8', hair='f2d16b', top='tshirt', shirt='ffca28', pants='ad1457', shoes='fafafa', style='ponytail', slim=True, dress=True, blush=True, girl=True, bow='ff4f9a'),
        'gleb':       dict(skin='dcae8a', hair='212121', top='jacket', shirt='b71c1c', inner='212121', pants='1c1c1c', shoes='212121', style='buzz', scar=True),
        'instructor': dict(skin='e0b08a', hair='9e9e9e', top='jacket', shirt='ef6c00', inner='fff3e0', pants='455a64', shoes='3e2723', style='bald', mustache='757575'),
        'baron':      dict(skin='e8c0a0', hair='0d0d0d', top='suit', shirt='111111', inner='ffd54f', pants='111111', shoes='111111', style='slick', tie='ffc107', glasses='gold', mustache='0d0d0d'),
        'guard':      dict(skin='c8956c', hair='111111', top='suit', shirt='151515', inner='eeeeee', pants='151515', shoes='050505', style='buzz', tie='151515', glasses='shades'),
    }
    looks = []
    for name, v in variants.items():
        save(skin(v), 'entity', 'citizen', name + '.png')
        looks.append(look(v))
    # 40 случайных горожан: чётные — девушки (тонкие руки), разные лица, причёски и одежда
    r = random.Random(2024)
    skins = ['f6d2b8', 'efc6a4', 'e2b48e', 'd8a982', 'c99067', 'a8714a', '8d5a3b', '6b4429']
    hairs = ['1a1a1a', '3e2723', '6b3d1f', 'a0662e', 'd2691e', 'f2d16b', 'e0e0e0', '8e24aa', 'e91e63', '1e88e5']
    clothes = ['e53935', '1e88e5', '43a047', 'fdd835', '8e24aa', 'fb8c00', '00897b', '546e7a', 'f06292', '3949ab', 'ffffff', '212121', '6d4c41', '26c6da']
    pants = ['1a237e', '263238', '37474f', '4e342e', '212121', '5d4037', '1565c0', '6a1b9a']
    for i in range(40):
        girl = i % 2 == 0
        hair = r.choice(hairs[:7]) if r.random() < 0.85 else r.choice(hairs[7:])
        v = dict(skin=r.choice(skins), hair=hair, shirt=r.choice(clothes), pants=r.choice(pants), shoes=r.choice(['fafafa', '212121', '5d4037', 'e53935', '1e88e5']),
                 top=r.choice(['tshirt', 'tshirt', 'hoodie', 'jacket', 'sweater'] + (['dress'] if girl else ['suit'])), inner=r.choice(clothes),
                 style=r.choice(['long', 'long', 'ponytail', 'ponytail', 'bob', 'curly'] if girl else ['short', 'buzz', 'curly', 'slick', 'bald', 'short']), slim=girl)
        if girl:
            v['blush'] = True
            v['girl'] = True
            if r.random() < 0.45: v['bow'] = r.choice(['ff4f9a', 'e53935', 'ffffff', '7e57c2', '29b6f6'])
            if v['top'] in ('suit', 'jacket') and r.random() < 0.5: v['top'] = 'dress'
        if r.random() < 0.25: v['glasses'] = r.choice([True, 'shades'])
        if not girl and r.random() < 0.25: v['beard'] = v['hair']
        if r.random() < 0.2: v['cap'] = r.choice(clothes)
        elif r.random() < 0.12: v['beanie'] = r.choice(clothes)
        if r.random() < 0.15: v['freckles'] = True
        if r.random() < 0.2: v['bag'] = True
        if v['top'] == 'dress':
            v['dress'] = True
            v['top'] = 'tshirt'
        if girl and r.random() < 0.35: v['dress'] = True
        save(skin(v), 'entity', 'citizen', f'c{i:02d}.png')
        looks.append(look(v))
    with open(os.path.join(ROOT, 'citizen_looks.json'), 'w') as f:
        json.dump(looks, f)
    # текстура волос: пряди
    hair = Image.new('RGBA', (64, 64))
    hp = hair.load()
    rr = random.Random(5)
    for x in range(64):
        base = 200 + rr.randint(-25, 25)
        for y in range(64):
            v_ = max(120, min(255, base + rr.randint(-12, 12) - (18 if (x + y // 3) % 5 == 0 else 0)))
            hp[x, y] = (v_, v_, v_, 255)
    save(hair, 'entity', 'citizen', 'hair.png')


def look(v):
    """Что рисовать объёмом поверх головы: причёска, борода, усы, головной убор, очки."""
    out = {'style': v.get('style', 'short'), 'hair': v['hair'], 'skin': v['skin']}
    if v.get('girl'): out['girl'] = True
    if v.get('bow'): out['bow'] = v['bow']
    if v.get('dress'): out['skirt'] = v['pants']
    if v.get('beard'): out['beard'] = v['beard']
    if v.get('mustache'): out['mustache'] = v['mustache']
    if v.get('helmet'): out['helmet'] = 'f2c032'
    elif v.get('cap'): out['cap'] = v['cap'] if isinstance(v['cap'], str) else '1b2440'
    if v.get('beanie'): out['beanie'] = v['beanie']
    if v.get('glasses'): out['glasses'] = v['glasses'] if isinstance(v['glasses'], str) else 'frame'
    return out


def skin(v):
    """Скин 64x64 в формате игрока (классические или тонкие руки), с причёской, одеждой и аксессуарами."""
    img = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    S, H, SH, P, SO = (rgb(v[k]) for k in ('skin', 'hair', 'shirt', 'pants', 'shoes'))
    IN = rgb(v.get('inner', v['shirt']))
    top = v.get('top', 'tshirt')
    style = v.get('style', 'short')
    aw = 3 if v.get('slim') else 4

    def box(u, vv, w, h, dd, col):
        regions = {
            'top': (u + dd, vv, w, dd), 'bottom': (u + dd + w, vv, w, dd),
            'right': (u, vv + dd, dd, h), 'front': (u + dd, vv + dd, w, h),
            'left': (u + dd + w, vv + dd, dd, h), 'back': (u + dd + w + dd, vv + dd, w, h)}
        for k, (x, y, ww, hh) in regions.items():
            for yy in range(hh):
                for xx in range(ww):
                    j = random.Random(x * 131 + y * 7 + xx * 17 + yy).randint(-6, 6)
                    img.putpixel((x + xx, y + yy), shade(col, 1 + j / 255))
        return regions

    def rect(r, x0, y0, x1, y1, col):
        x, y, w, h = r
        d.rectangle([x + x0, y + y0, x + min(x1, w - 1), y + min(y1, h - 1)], fill=col)

    # ---- голова
    r = box(0, 0, 8, 8, 8, S)
    F = r['front']
    fx, fy = F[0], F[1]
    if style != 'bald':
        rect(r['top'], 0, 0, 7, 7, H)
        fringe = {'short': 1, 'buzz': 0, 'curly': 2, 'slick': 1, 'long': 1, 'ponytail': 1, 'bob': 2}.get(style, 1)
        rect(F, 0, 0, 7, fringe, H)
        side = {'short': 2, 'buzz': 1, 'curly': 3, 'slick': 2, 'long': 7, 'ponytail': 3, 'bob': 5}.get(style, 2)
        for k in ('right', 'left'):
            rect(r[k], 0, 0, 7, side, H)
        rect(r['back'], 0, 0, 7, 7 if style in ('long', 'bob', 'ponytail') else side + 1, H)
        if style in ('long', 'bob'):
            d.point([(fx, fy + 2), (fx, fy + 3), (fx + 7, fy + 2), (fx + 7, fy + 3)], fill=H)
            if style == 'long':
                d.point([(fx, fy + 4), (fx + 7, fy + 4), (fx, fy + 5), (fx + 7, fy + 5)], fill=H)
        if style == 'curly':
            for xx in range(0, 8, 2):
                d.point([(fx + xx, fy + 2)], fill=shade(H, 0.8))
        if style == 'slick':
            d.line([fx + 2, fy, fx + 7, fy + 1], fill=shade(H, 1.4))
    else:
        rect(r['top'], 2, 2, 5, 5, shade(S, 1.05))
    # глаза, брови, рот
    eye = rgb(random.Random(v['skin'] + v['hair']).choice(['2a3a6a', '3e6b2a', '4e342e', '1a1a1a', '2e7d9a']))
    # глаза симметричные: белок снаружи, зрачок ближе к носу
    d.point([(fx + 1, fy + 4), (fx + 6, fy + 4)], fill=rgb('ffffff'))
    d.point([(fx + 2, fy + 4), (fx + 5, fy + 4)], fill=eye)
    d.line([fx + 1, fy + 3, fx + 2, fy + 3], fill=shade(H if style != 'bald' else S, 0.7))
    d.line([fx + 5, fy + 3, fx + 6, fy + 3], fill=shade(H if style != 'bald' else S, 0.7))
    d.point([(fx + 3, fy + 5), (fx + 4, fy + 5)], fill=shade(S, 0.88))
    d.line([fx + 3, fy + 6, fx + 4, fy + 6], fill=rgb('a0524a'))
    d.point([(fx + 2, fy + 6)], fill=shade(S, 0.8)) if not v.get('slim') else None
    if v.get('girl'):
        lash = rgb('1a1010')
        d.point([(fx, fy + 3), (fx + 7, fy + 3), (fx + 1, fy + 3), (fx + 6, fy + 3)], fill=lash)
        d.line([fx + 3, fy + 6, fx + 4, fy + 6], fill=rgb('d6455a'))
        d.point([(fx + 3, fy + 7), (fx + 4, fy + 7)], fill=shade(S, 0.93))
    if v.get('blush'):
        d.point([(fx + 1, fy + 5), (fx + 6, fy + 5)], fill=rgb('f48fb1'))
    if v.get('freckles'):
        d.point([(fx + 1, fy + 5), (fx + 2, fy + 5), (fx + 5, fy + 5), (fx + 6, fy + 5)], fill=shade(S, 0.75))
    if v.get('scar'):
        d.line([fx + 6, fy + 5, fx + 7, fy + 6], fill=rgb('b0705a'))
    if v.get('beard'):
        B = rgb(v['beard'])
        d.rectangle([fx, fy + 6, fx + 7, fy + 7], fill=B)
        d.point([(fx, fy + 5), (fx + 7, fy + 5)], fill=B)
        # рот в бороде: тёмные губы и светлая кожа под ними, чтобы было видно, где рот
        d.line([fx + 2, fy + 6, fx + 5, fy + 6], fill=rgb('5a2a22'))
        d.line([fx + 3, fy + 6, fx + 4, fy + 6], fill=rgb('8e3f36'))
        d.point([(fx + 3, fy + 5), (fx + 4, fy + 5)], fill=shade(B, 0.85))
        for k in ('right', 'left'):
            rect(r[k], 0, 6, 7, 7, B)
    if v.get('mustache'):
        d.line([fx + 2, fy + 5, fx + 5, fy + 5], fill=rgb(v['mustache']))
    g = v.get('glasses')
    if g:
        col = rgb('d4af37') if g == 'gold' else rgb('111111')
        if g == 'shades':
            d.rectangle([fx + 1, fy + 3, fx + 6, fy + 4], fill=rgb('111111'))
        else:
            d.line([fx, fy + 3, fx + 7, fy + 3], fill=col)
            d.point([(fx + 1, fy + 4), (fx + 5, fy + 4)], fill=rgb('cfe8ff'))
            d.point([(fx, fy + 4), (fx + 3, fy + 4), (fx + 4, fy + 4), (fx + 7, fy + 4)], fill=col)
    # слой поверх головы (32,0): хвост, кепка, шапка, каска
    hx, hy = 32, 0
    hat = {'top': (hx + 8, hy, 8, 8), 'front': (hx + 8, hy + 8, 8, 8), 'right': (hx, hy + 8, 8, 8), 'left': (hx + 16, hy + 8, 8, 8), 'back': (hx + 24, hy + 8, 8, 8)}
    if style == 'ponytail':
        x, y, w, h = hat['back']
        d.rectangle([x + 3, y + 1, x + 4, y + 7], fill=shade(H, 0.9))
        d.rectangle([x + 3, y + 1, x + 4, y + 1], fill=rgb(v['shirt']))
    if style == 'curly':
        for k in ('top', 'right', 'left', 'back'):
            x, y, w, h = hat[k]
            for xx in range(0, w, 2):
                d.point([(x + xx, y + (0 if k == 'top' else 0))], fill=shade(H, 1.1))
    cap = v.get('cap') or (v.get('helmet') and 'f2c032')
    if cap:
        C = rgb(cap if isinstance(cap, str) else 'f2c032')
        for k in ('top',):
            x, y, w, h = hat[k]
            d.rectangle([x, y, x + w - 1, y + h - 1], fill=C)
        for k in ('front', 'right', 'left', 'back'):
            x, y, w, h = hat[k]
            d.rectangle([x, y, x + w - 1, y + 2], fill=C)
        if v.get('badge') or v.get('helmet'):
            x, y, w, h = hat['front']
            d.point([(x + 3, y + 1), (x + 4, y + 1)], fill=rgb('ffd54f'))
    if v.get('beanie'):
        C = rgb(v['beanie'])
        x, y, w, h = hat['top']
        d.rectangle([x, y, x + w - 1, y + h - 1], fill=C)
        for k in ('front', 'right', 'left', 'back'):
            x, y, w, h = hat[k]
            d.rectangle([x, y, x + w - 1, y + 3], fill=C)
            d.line([x, y + 3, x + w - 1, y + 3], fill=shade(C, 0.8))
    # ---- тело
    r = box(16, 16, 8, 12, 4, SH)
    F = r['front']
    fx, fy = F[0], F[1]
    if top == 'jacket' or top == 'suit':
        d.rectangle([fx + 3, fy, fx + 4, fy + 11], fill=IN)
        d.point([(fx + 2, fy), (fx + 5, fy)], fill=IN)
        if top == 'jacket':
            d.line([fx + 3, fy + 3, fx + 3, fy + 11], fill=shade(SH, 0.7))
        if v.get('badge'):
            d.point([(fx + 1, fy + 3)], fill=rgb('ffd54f'))
    if top == 'hoodie':
        d.rectangle([fx + 1, fy + 7, fx + 6, fy + 9], fill=shade(SH, 0.85))
        d.point([(fx + 3, fy + 1), (fx + 4, fy + 1)], fill=rgb('eeeeee'))
        d.line([fx + 3, fy + 1, fx + 3, fy + 4], fill=rgb('eeeeee'))
        d.line([fx + 4, fy + 1, fx + 4, fy + 4], fill=rgb('eeeeee'))
        x, y, w, h = r['back']
        d.rectangle([x + 1, y, x + 6, y + 2], fill=shade(SH, 0.85))
    if top == 'sweater':
        for yy in (2, 6):
            d.line([fx, fy + yy, fx + 7, fy + yy], fill=shade(SH, 1.25))
    if top == 'tshirt':
        d.rectangle([fx + 2, fy, fx + 5, fy], fill=shade(S, 0.95))
    tie = v.get('tie')
    if tie:
        d.line([fx + 3, fy + 1, fx + 3, fy + 8], fill=rgb(tie))
        d.line([fx + 4, fy + 1, fx + 4, fy + 8], fill=shade(rgb(tie), 0.85))
    if v.get('apron'):
        d.rectangle([fx + 1, fy + 3, fx + 6, fy + 11], fill=rgb('f4f4f4'))
    if v.get('bag'):
        x, y, w, h = r['back']
        d.rectangle([x + 1, y + 1, x + 6, y + 9], fill=rgb('e53935'))
        d.line([fx + 1, fy, fx + 1, fy + 5], fill=rgb('b71c1c'))
        d.line([fx + 6, fy, fx + 6, fy + 5], fill=rgb('b71c1c'))
    if not v.get('dress'):
        d.rectangle([fx, fy + 10, fx + 7, fy + 11], fill=shade(P, .8))
        d.point([(fx + 3, fy + 10)], fill=rgb('c0c0c0'))
    # ---- руки: правая (40,16), левая (32,48); тонкие — ширина 3
    long_sleeve = top in ('hoodie', 'jacket', 'suit', 'sweater')
    for (u, vv) in ((40, 16), (32, 48)):
        r = box(u, vv, aw, 12, 4, SH)
        for k in ('right', 'front', 'left', 'back'):
            x, y, w, h = r[k]
            if long_sleeve:
                d.rectangle([x, y + 10, x + w - 1, y + h - 1], fill=S)
            else:
                d.rectangle([x, y + 4, x + w - 1, y + h - 1], fill=S)
        x, y, w, h = r['bottom']
        d.rectangle([x, y, x + w - 1, y + h - 1], fill=S)
    # ---- ноги: правая (0,16), левая (16,48); у платья верх ног — цвет платья
    for (u, vv) in ((0, 16), (16, 48)):
        r = box(u, vv, 4, 12, 4, P if not v.get('dress') else S)
        for k in ('right', 'front', 'left', 'back'):
            x, y, w, h = r[k]
            if v.get('dress'):
                d.rectangle([x, y, x + w - 1, y + 4], fill=rgb(v['pants']))
                d.rectangle([x, y + 5, x + w - 1, y + 9], fill=shade(S, 0.97))
            d.rectangle([x, y + 10, x + w - 1, y + h - 1], fill=SO)
        x, y, w, h = r['bottom']
        d.rectangle([x, y, x + w - 1, y + h - 1], fill=SO)
    return img


# ---------- интерфейс телефона ----------
def gui():
    def icon(name, bg, draw):
        img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
        d = ImageDraw.Draw(img)
        d.rounded_rectangle([0, 0, 15, 15], 4, fill=rgb(bg))
        d.rounded_rectangle([0, 0, 15, 7], 4, fill=shade(rgb(bg), 1.12))
        d.rectangle([0, 4, 15, 7], fill=shade(rgb(bg), 1.06))
        draw(d)
        save(img, 'gui', 'app', name + '.png')

    icon('bank', '2fa84f', lambda d: (d.rectangle([3, 6, 12, 11], fill=rgb('ffffff')), d.ellipse([6, 7, 9, 10], fill=rgb('2fa84f')), d.line([3, 4, 12, 4], fill=rgb('ffffff'))))
    icon('school', 'f29b2b', lambda d: (d.polygon([(2, 7), (8, 4), (14, 7), (8, 10)], fill=rgb('ffffff')), d.rectangle([5, 9, 11, 12], fill=rgb('ffffff')), d.line([13, 7, 13, 11], fill=rgb('ffffff'))))
    icon('jobs', '5a6acf', lambda d: (d.rectangle([3, 6, 12, 12], fill=rgb('ffffff')), d.rectangle([6, 4, 9, 6], outline=rgb('ffffff')), d.line([3, 9, 12, 9], fill=rgb('5a6acf'))))
    icon('messages', '34c759', lambda d: (d.rounded_rectangle([2, 3, 13, 10], 3, fill=rgb('ffffff')), d.polygon([(4, 9), (4, 13), (8, 10)], fill=rgb('ffffff'))))
    icon('map', '3aa0e6', lambda d: (d.rectangle([2, 3, 13, 12], fill=rgb('e7f3d7')), d.line([2, 8, 13, 6], fill=rgb('f2c032'), width=2), d.ellipse([8, 3, 11, 6], fill=rgb('e53935')), d.point([(9, 7)], fill=rgb('e53935'))))
    icon('music', 'ff375f', lambda d: (d.line([10, 3, 10, 10], fill=rgb('ffffff')), d.line([6, 4, 10, 3], fill=rgb('ffffff')), d.line([6, 4, 6, 11], fill=rgb('ffffff')), d.ellipse([3, 10, 6, 13], fill=rgb('ffffff')), d.ellipse([7, 9, 10, 12], fill=rgb('ffffff'))))
    icon('camera', '4b4f58', lambda d: (d.rounded_rectangle([2, 5, 13, 12], 2, fill=rgb('dfe2e8')), d.ellipse([5, 6, 10, 11], fill=rgb('2a2c31')), d.ellipse([6, 7, 9, 10], fill=rgb('5b8def')), d.rectangle([5, 3, 8, 4], fill=rgb('dfe2e8'))))
    icon('shop', 'e05a47', lambda d: (d.polygon([(3, 6), (12, 6), (11, 13), (4, 13)], fill=rgb('ffffff')), d.arc([5, 2, 10, 9], 180, 360, fill=rgb('ffffff'))))
    icon('car', 'ff9f0a', lambda d: (d.rectangle([2, 7, 13, 10], fill=rgb('ffffff')), d.polygon([(4, 7), (5, 4), (10, 4), (11, 7)], fill=rgb('ffffff')), d.ellipse([3, 9, 6, 12], fill=rgb('2a2c31')), d.ellipse([9, 9, 12, 12], fill=rgb('2a2c31'))))
    icon('weather', '64b5f6', lambda d: (d.ellipse([3, 3, 9, 9], fill=rgb('ffd54f')), d.ellipse([5, 7, 13, 12], fill=rgb('ffffff')), d.ellipse([8, 5, 13, 10], fill=rgb('ffffff'))))
    icon('home', 'a0785a', lambda d: (d.polygon([(2, 8), (8, 3), (14, 8)], fill=rgb('ffffff')), d.rectangle([4, 8, 12, 13], fill=rgb('ffffff')), d.rectangle([7, 10, 9, 13], fill=rgb('a0785a'))))
    icon('settings', '8e8e93', lambda d: (d.ellipse([3, 3, 12, 12], outline=rgb('ffffff'), width=2), d.ellipse([6, 6, 9, 9], fill=rgb('ffffff'))))

    icon('story', 'ffb300', lambda d: (d.rectangle([3, 3, 12, 13], fill=rgb('ffffff')), d.rectangle([3, 3, 4, 13], fill=rgb('b07d00')), d.polygon([(8, 5), (9, 7), (11, 7), (9, 9), (10, 11), (8, 10), (6, 11), (7, 9), (5, 7), (7, 7)], fill=rgb('ffb300'))))
    icon('online', '6a1b9a', lambda d: (d.ellipse([3, 3, 12, 12], outline=rgb('ffffff')), d.line([7, 3, 7, 12], fill=rgb('ffffff')), d.line([3, 7, 12, 7], fill=rgb('ffffff')), d.ellipse([9, 9, 14, 14], fill=rgb('ffd54f'))))
    icon('trophy', 'c99a2e', lambda d: (d.rectangle([5, 3, 10, 8], fill=rgb('ffe082')), d.line([3, 4, 5, 6], fill=rgb('ffe082')), d.line([12, 4, 10, 6], fill=rgb('ffe082')), d.rectangle([7, 9, 8, 11], fill=rgb('ffe082')), d.rectangle([5, 12, 10, 13], fill=rgb('ffe082'))))
    icon('taxi', 'f7c600', lambda d: (d.rectangle([2, 7, 13, 11], fill=rgb('222222')), d.polygon([(4, 7), (5, 4), (10, 4), (11, 7)], fill=rgb('222222')), d.rectangle([6, 2, 9, 3], fill=rgb('222222')), d.ellipse([3, 10, 6, 13], fill=rgb('ffffff')), d.ellipse([9, 10, 12, 13], fill=rgb('ffffff'))))
    icon('friends', '7e57c2', lambda d: (d.ellipse([3, 3, 7, 7], fill=rgb('ffffff')), d.ellipse([9, 3, 13, 7], fill=rgb('e1d5f5')), d.rectangle([2, 8, 8, 13], fill=rgb('ffffff')), d.rectangle([8, 8, 14, 13], fill=rgb('e1d5f5'))))
    icon('casino', '8e1b3a', lambda d: (d.ellipse([3, 7, 7, 11], fill=rgb('ff3b4f')), d.ellipse([8, 8, 12, 12], fill=rgb('ff3b4f')), d.line([5, 7, 8, 3, 10, 8], fill=rgb('5bd16a')), d.point([(4, 8), (9, 9)], fill=rgb('ffffff'))))

    # символы игрового автомата 16x16
    def sym(name, draw):
        img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
        draw(ImageDraw.Draw(img))
        save(img, 'gui', 'slot', name + '.png')
    sym('cherry', lambda d: (d.line([5, 9, 8, 2, 11, 10], fill=rgb('3fae4a')), d.ellipse([2, 8, 8, 14], fill=rgb('e3242b')), d.ellipse([8, 9, 14, 15], fill=rgb('c81d25')), d.point([(4, 10), (10, 11)], fill=rgb('ffb3b3')), d.polygon([(8, 2), (12, 1), (11, 4)], fill=rgb('5bd16a'))))
    sym('lemon', lambda d: (d.ellipse([2, 4, 14, 12], fill=rgb('ffe14d')), d.ellipse([3, 5, 13, 11], outline=rgb('f2c400')), d.point([(5, 6), (6, 6)], fill=rgb('fff8c2')), d.polygon([(13, 7), (15, 8), (13, 9)], fill=rgb('f2c400'))))
    sym('bell', lambda d: (d.chord([3, 2, 13, 16], 180, 360, fill=rgb('ffc93c')), d.rectangle([3, 9, 13, 11], fill=rgb('ffc93c')), d.rectangle([2, 11, 14, 12], fill=rgb('e0a800')), d.ellipse([7, 12, 9, 14], fill=rgb('b07d00')), d.point([(6, 5), (6, 6)], fill=rgb('fff2b3'))))
    sym('star', lambda d: (d.polygon([(8, 1), (10, 6), (15, 6), (11, 9), (13, 15), (8, 11), (3, 15), (5, 9), (1, 6), (6, 6)], fill=rgb('ffd400'), outline=rgb('f29b00')), d.point([(7, 5), (8, 4)], fill=rgb('fff6b0'))))
    sym('seven', lambda d: (d.polygon([(3, 2), (13, 2), (13, 4), (8, 14), (5, 14), (10, 5), (3, 5)], fill=rgb('e3242b'), outline=rgb('8a0f14'))))
    sym('diamond', lambda d: (d.polygon([(4, 3), (12, 3), (15, 7), (8, 15), (1, 7)], fill=rgb('5ad1ff'), outline=rgb('1e88c8')), d.line([1, 7, 15, 7], fill=rgb('9fe6ff')), d.line([6, 3, 5, 7, 8, 15, 11, 7, 10, 3], fill=rgb('2aa8e0'))))

    # обои телефона (градиент + звёзды/город)
    W, H = 96, 176
    img = Image.new('RGBA', (W, H))
    d = ImageDraw.Draw(img)
    for y in range(H):
        t = y / H
        d.line([0, y, W, y], fill=(int(40 + 140 * t), int(30 + 60 * t), int(110 + 60 * (1 - t)), 255))
    r = random.Random(3)
    for _ in range(30):
        d.point([(r.randrange(W), r.randrange(H // 2))], fill=(255, 255, 255, r.randint(120, 255)))
    x = 0
    while x < W:
        w = r.randint(8, 16)
        h = r.randint(20, 60)
        d.rectangle([x, H - h, x + w - 1, H], fill=(25, 20, 45, 255))
        for wy in range(H - h + 3, H - 3, 5):
            for wx in range(x + 2, x + w - 2, 4):
                if r.random() < .45:
                    d.rectangle([wx, wy, wx + 1, wy + 1], fill=(255, 214, 120, 255))
        x += w + 1
    save(img, 'gui', 'wallpaper.png')

    icon_img = Image.new('RGBA', (128, 128))
    d = ImageDraw.Draw(icon_img)
    d.rounded_rectangle([0, 0, 127, 127], 24, fill=rgb('2c6ecb'))
    d.rounded_rectangle([0, 0, 127, 60], 24, fill=rgb('4a8ee6'))
    d.rectangle([0, 40, 127, 60], fill=rgb('3c7ed8'))
    d.rectangle([14, 64, 113, 108], fill=rgb('f29b2b'))
    d.polygon([(10, 66), (64, 30), (118, 66)], fill=rgb('d7262c'))
    for i in range(4):
        d.rectangle([22 + i * 24, 74, 36 + i * 24, 88], fill=rgb('9fd6ff'))
    d.rectangle([56, 92, 72, 108], fill=rgb('6b4a2b'))
    d.rectangle([0, 108, 127, 127], fill=rgb('3a3d42'))
    for x in range(8, 128, 24):
        d.rectangle([x, 116, x + 12, 118], fill=rgb('f2c335'))
    img2 = icon_img
    img2.save(os.path.join(ROOT, 'icon.png'))


# ---------- звуки ----------
SR = 44100


def write_ogg(name, sig):
    sig = np.clip(sig, -1, 1)
    os.makedirs(SND, exist_ok=True)
    wav = os.path.join(SND, name + '.wav')
    with wave.open(wav, 'w') as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes((sig * 32000).astype('<i2').tobytes())
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', wav, '-c:a', 'libvorbis', '-q:a', '4', os.path.join(SND, name + '.ogg')], check=True)
    os.remove(wav)


def t_(sec):
    return np.arange(int(SR * sec)) / SR


def env(n, a=.005, r=.2):
    t = np.arange(n) / SR
    e = np.minimum(1, t / a) * np.exp(-t / r)
    return e


def sounds():
    # школьный звонок: металлический звон с дребезжанием
    t = t_(2.6)
    ring = sum(np.sin(2 * np.pi * f * t) * a for f, a in ((1320, 1), (2640, .4), (3960, .25), (1980, .3)))
    trem = (np.sign(np.sin(2 * np.pi * 22 * t)) + 1) / 2
    e = np.minimum(1, t / .02) * np.where(t < 2.2, 1, np.exp(-(t - 2.2) / .12))
    write_ogg('school_bell', ring * trem * e * .28)

    # уведомление телефона: две ноты
    out = np.zeros(int(SR * .5))
    for i, f in enumerate((988, 1319)):
        s = int(SR * .12 * i)
        tt = t_(.3)
        out[s:s + len(tt)] += np.sin(2 * np.pi * f * tt) * env(len(tt), .003, .09) * .5
    write_ogg('phone_notify', out)

    # открытие телефона: мягкий щелчок + свуш
    tt = t_(.18)
    sw = np.random.default_rng(1).normal(0, 1, len(tt))
    sw = np.convolve(sw, np.ones(30) / 30, 'same') * np.sin(np.pi * tt / .18) * .5
    write_ogg('phone_open', sw + np.sin(2 * np.pi * 1800 * tt) * env(len(tt), .001, .01) * .4)

    # клик кнопки
    tt = t_(.06)
    write_ogg('phone_click', np.sin(2 * np.pi * 2400 * tt) * env(len(tt), .001, .008) * .5)

    # гудок машины
    tt = t_(.55)
    h = (np.sign(np.sin(2 * np.pi * 415 * tt)) + np.sign(np.sin(2 * np.pi * 523 * tt))) * .5
    h = np.convolve(h, np.ones(8) / 8, 'same')
    write_ogg('car_horn', h * np.minimum(1, tt / .01) * np.minimum(1, (.55 - tt) / .03) * .35)

    # двигатель (зацикливается): низкое урчание с гармониками
    tt = t_(1.0)
    f0 = 46
    eng = sum(np.sin(2 * np.pi * f0 * k * tt + k) / k for k in range(1, 9))
    puff = 1 + .35 * np.sin(2 * np.pi * f0 / 2 * tt)
    noise_ = np.convolve(np.random.default_rng(2).normal(0, 1, len(tt)), np.ones(40) / 40, 'same')
    write_ogg('car_engine', (eng * puff * .22 + noise_ * .3) * .8)

    # запуск двигателя
    tt = t_(1.1)
    f = 20 + 60 * np.minimum(1, tt / .9)
    ph = 2 * np.pi * np.cumsum(f) / SR
    st = sum(np.sin(ph * k) / k for k in range(1, 7)) * (1 + .5 * np.sign(np.sin(2 * np.pi * 9 * tt)) * (tt < .5))
    write_ogg('car_start', st * np.minimum(1, tt / .05) * .3)

    # касса
    out = np.zeros(int(SR * .7))
    tt = t_(.05)
    out[:len(tt)] += np.random.default_rng(3).normal(0, .4, len(tt)) * env(len(tt), .001, .01)
    for f, s in ((2093, .06), (2637, .06), (3136, .06)):
        tt = t_(.6)
        i = int(SR * s)
        out[i:i + len(tt)] += np.sin(2 * np.pi * f * tt) * env(len(tt), .002, .18) * .25
    write_ogg('cash', out)

    # правильный ответ / ошибка
    out = np.zeros(int(SR * .45))
    for i, f in enumerate((784, 988, 1175)):
        tt = t_(.25)
        s = int(SR * .08 * i)
        out[s:s + len(tt)] += np.sin(2 * np.pi * f * tt) * env(len(tt), .003, .1) * .35
    write_ogg('answer_right', out)
    tt = t_(.4)
    w = np.sign(np.sin(2 * np.pi * 140 * tt)) * .2 + np.sign(np.sin(2 * np.pi * 147 * tt)) * .2
    write_ogg('answer_wrong', w * np.minimum(1, tt / .01) * np.exp(-tt / .25))

    # щелчок переключения ТВ
    tt = t_(.25)
    st = np.random.default_rng(4).normal(0, 1, len(tt)) * np.exp(-tt / .06) * .4
    write_ogg('tv_switch', st + np.sin(2 * np.pi * 15000 * tt) * np.exp(-tt / .1) * .05)

    # джингл Yompix Games: восходящее арпеджио с переливом и мягким «бумом»
    out = np.zeros(int(SR * 3.2))
    notes = [523.25, 659.25, 783.99, 987.77, 1174.66, 1567.98]
    for i, f in enumerate(notes):
        tt = t_(2.2 - i * 0.12)
        st = int(SR * (0.1 + i * 0.11))
        tone = sum(np.sin(2 * np.pi * f * k * tt) / (k * k) for k in (1, 2, 3, 4)) * (1 + 0.3 * np.sin(2 * np.pi * 5 * tt))
        out[st:st + len(tt)] += tone * env(len(tt), .005, .6) * .16
    tt = t_(1.6)
    st = int(SR * 0.85)
    pad = sum(np.sin(2 * np.pi * f * tt) for f in (130.81, 196.0, 261.63, 329.63)) * np.minimum(1, tt / .2) * np.exp(-tt / .9)
    out[st:st + len(tt)] += pad * .1
    boom = np.sin(2 * np.pi * (60 * np.exp(-t_(0.8) * 3)) * t_(0.8)) * np.exp(-t_(0.8) / .25)
    out[st:st + len(boom)] += boom * .5
    shimmer = np.random.default_rng(9).normal(0, 1, int(SR * 1.5))
    shimmer = np.convolve(shimmer, [1, -1], 'same') * np.exp(-np.arange(len(shimmer)) / SR / .5) * .04
    out[int(SR * .9):int(SR * .9) + len(shimmer)] += shimmer
    write_ogg('yompix_jingle', out)

    # повышение класса / диплом: фанфары
    out = np.zeros(int(SR * 1.6))
    for i, (f, d) in enumerate(((523, .15), (659, .15), (784, .15), (1047, .7))):
        tt = t_(d + .3)
        s = int(SR * .15 * i)
        tone = sum(np.sin(2 * np.pi * f * k * tt) / k for k in (1, 2, 3))
        out[s:s + len(tt)] += tone * env(len(tt), .01, .35) * .22
    write_ogg('level_up', out)

    # сигнал светофора для пешеходов
    tt = t_(.12)
    write_ogg('traffic_beep', np.sin(2 * np.pi * 2000 * tt) * env(len(tt), .002, .05) * .3)


if __name__ == '__main__':
    blocks()
    items()
    entities()
    gui()
    sounds()
    print('ok')
