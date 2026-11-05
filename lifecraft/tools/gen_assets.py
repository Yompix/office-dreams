#!/usr/bin/env python3
"""Рисует все текстуры и звуки LifeCraft (пиксель-арт и синтез), чтобы не зависеть от чужих файлов.
Запуск из папки lifecraft: python3 tools/gen_assets.py
"""
import json, math, os, random, subprocess, wave
import numpy as np
from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'assets', 'lifecraft')
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
    asphalt = noise((16, 16), rgb('3a3d42'), 9, 1)
    px = asphalt.load()
    for _ in range(10):
        x, y = rnd.randrange(16), rnd.randrange(16)
        px[x, y] = rgb('55595f')
    save(asphalt, 'block', 'asphalt.png')

    line = asphalt.copy()
    d = ImageDraw.Draw(line)
    d.rectangle([7, 0, 8, 9], fill=rgb('f2c335'))
    save(line, 'block', 'asphalt_line.png')

    edge = asphalt.copy()
    ImageDraw.Draw(edge).rectangle([0, 0, 1, 15], fill=rgb('e8e8e8'))
    save(edge, 'block', 'asphalt_edge.png')

    cross = asphalt.copy()
    d = ImageDraw.Draw(cross)
    for x in (1, 6, 11):
        d.rectangle([x, 0, x + 2, 15], fill=rgb('f0f0f0'))
    save(cross, 'block', 'crosswalk.png')

    side = Image.new('RGBA', (16, 16))
    d = ImageDraw.Draw(side)
    for ty in range(2):
        for tx in range(2):
            c = shade(rgb('b9b4aa'), 1 + rnd.uniform(-.05, .05))
            d.rectangle([tx * 8, ty * 8, tx * 8 + 7, ty * 8 + 7], fill=c)
            d.line([tx * 8, ty * 8 + 7, tx * 8 + 7, ty * 8 + 7], fill=rgb('8f8a82'))
            d.line([tx * 8 + 7, ty * 8, tx * 8 + 7, ty * 8 + 7], fill=rgb('8f8a82'))
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

    def burger(d, img):
        d.chord([2, 3, 13, 11], 180, 360, fill=rgb('e0a24a'))
        d.point([(5, 5), (8, 4), (10, 6)], fill=rgb('fff3c4'))
        d.rectangle([2, 7, 13, 8], fill=rgb('5bbd3a'))
        d.rectangle([2, 9, 13, 10], fill=rgb('6b3a1f'))
        d.rectangle([2, 11, 13, 11], fill=rgb('f2c84b'))
        d.rectangle([3, 12, 12, 13], fill=rgb('d8913c'))
    item('burger', burger)

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

    variants = {
        'teacher':    dict(skin='e9c1a0', hair='5a3a22', shirt='7b4fa1', pants='2b2b3a', shoes='3a2416', long=True, glasses=True),
        'shopkeeper': dict(skin='d9a47c', hair='1f1f1f', shirt='e05a47', pants='3c4a6b', shoes='222222', apron=True),
        'student':    dict(skin='f0c8a6', hair='a0662e', shirt='2c6ecb', pants='3a3a46', shoes='eeeeee', bag=True),
        'student2':   dict(skin='c99067', hair='2a1a10', shirt='e8e8e8', pants='5a2a3a', shoes='222222', long=True, bag=True),
        'worker':     dict(skin='e0b48f', hair='7a5230', shirt='f28c28', pants='2f3b55', shoes='4a3020', helmet=True),
        'office':     dict(skin='efc9a8', hair='3a2a1a', shirt='f4f4f4', pants='2a2d36', shoes='111111', tie=True),
        'police':     dict(skin='d8a982', hair='222222', shirt='1d3f8a', pants='1b2440', shoes='111111', cap=True),
        'grandma':    dict(skin='edc7ab', hair='d9d9d9', shirt='9c5a7a', pants='5a3a4a', shoes='3a2a2a', long=True),
    }
    for name, v in variants.items():
        save(skin(v), 'entity', 'citizen', name + '.png')


def skin(v):
    """Скин 64x64 в формате игрока."""
    img = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    S, H, SH, P, SO = (rgb(v[k]) for k in ('skin', 'hair', 'shirt', 'pants', 'shoes'))

    def box(u, vv, w, h, dd, col, sides=None):
        # стандартная развёртка куба: top, bottom, right, front, left, back
        sides = sides or {}
        regions = {
            'top': (u + dd, vv, w, dd), 'bottom': (u + dd + w, vv, w, dd),
            'right': (u, vv + dd, dd, h), 'front': (u + dd, vv + dd, w, h),
            'left': (u + dd + w, vv + dd, dd, h), 'back': (u + dd + w + dd, vv + dd, w, h)}
        for k, (x, y, ww, hh) in regions.items():
            c = sides.get(k, col)
            for yy in range(hh):
                for xx in range(ww):
                    j = random.Random(x * 131 + y * 7 + xx * 17 + yy).randint(-6, 6)
                    img.putpixel((x + xx, y + yy), shade(c, 1 + j / 255))
        return regions

    # голова
    r = box(0, 0, 8, 8, 8, S)
    fx, fy, _, _ = r['front']
    d.rectangle([fx, fy, fx + 7, fy + 1], fill=H)
    for k in ('right', 'left', 'back'):
        x, y, w, h = r[k]
        d.rectangle([x, y, x + w - 1, y + (5 if v.get('long') else 2)], fill=H)
    x, y, w, h = r['top']
    d.rectangle([x, y, x + w - 1, y + h - 1], fill=H)
    d.point([(fx + 2, fy + 4), (fx + 5, fy + 4)], fill=rgb('ffffff'))
    d.point([(fx + 2, fy + 4 + 0)], fill=rgb('ffffff'))
    d.point([(fx + 3, fy + 4), (fx + 4, fy + 4)], fill=shade(S, .9))
    d.point([(fx + 2, fy + 4)], fill=rgb('ffffff'))
    d.point([(fx + 3, fy + 4)], fill=rgb('2a3a6a'))
    d.point([(fx + 5, fy + 4)], fill=rgb('2a3a6a'))
    d.point([(fx + 6, fy + 4)], fill=rgb('ffffff'))
    d.line([fx + 3, fy + 6, fx + 4, fy + 6], fill=rgb('a0524a'))
    if v.get('long'):
        d.point([(fx, fy + 2), (fx, fy + 3), (fx + 7, fy + 2), (fx + 7, fy + 3)], fill=H)
    if v.get('glasses'):
        d.rectangle([fx + 1, fy + 3, fx + 3, fy + 5], outline=rgb('222222'))
        d.rectangle([fx + 5, fy + 3, fx + 7, fy + 5], outline=rgb('222222'))
    # слой шляпы (32,0)
    if v.get('helmet') or v.get('cap'):
        col = rgb('f2c032') if v.get('helmet') else rgb('1b2440')
        hx, hy = 32, 0
        for k, (x, y, w, h) in {'top': (hx + 8, hy, 8, 8), 'front': (hx + 8, hy + 8, 8, 3), 'right': (hx, hy + 8, 8, 3),
                                'left': (hx + 16, hy + 8, 8, 3), 'back': (hx + 24, hy + 8, 8, 3)}.items():
            d.rectangle([x, y, x + w - 1, y + h - 1], fill=col)
        if v.get('cap'):
            d.point([(hx + 11, hy + 9), (hx + 12, hy + 9)], fill=rgb('f2c032'))
    # тело
    r = box(16, 16, 8, 12, 4, SH)
    fx, fy, _, _ = r['front']
    if v.get('tie'):
        d.line([fx + 3, fy, fx + 3, fy + 8], fill=rgb('c62828'))
        d.line([fx + 4, fy, fx + 4, fy + 8], fill=rgb('a51f1f'))
    if v.get('apron'):
        d.rectangle([fx + 1, fy + 3, fx + 6, fy + 11], fill=rgb('f4f4f4'))
    if v.get('helmet'):
        d.rectangle([fx, fy + 6, fx + 7, fy + 7], fill=rgb('dfe6ee'))
    if v.get('cap'):
        d.point([(fx + 5, fy + 2)], fill=rgb('f2c032'))
    if v.get('bag'):
        bx, by, _, _ = r['back']
        d.rectangle([bx + 1, by + 1, bx + 6, by + 9], fill=rgb('e53935'))
        d.line([fx + 1, fy, fx + 1, fy + 5], fill=rgb('b71c1c'))
        d.line([fx + 6, fy, fx + 6, fy + 5], fill=rgb('b71c1c'))
    d.rectangle([fx, fy + 10, fx + 7, fy + 11], fill=shade(P, .8))
    # руки (правая 40,16; левая 32,48)
    for (u, vv) in ((40, 16), (32, 48)):
        r = box(u, vv, 4, 12, 4, SH)
        for k in ('right', 'front', 'left', 'back'):
            x, y, w, h = r[k]
            d.rectangle([x, y + 4 if v['shirt'] != v['skin'] else y, x + w - 1, y + h - 1], fill=S)
        x, y, w, h = r['bottom']
        d.rectangle([x, y, x + w - 1, y + h - 1], fill=S)
    # ноги (правая 0,16; левая 16,48)
    for (u, vv) in ((0, 16), (16, 48)):
        r = box(u, vv, 4, 12, 4, P)
        for k in ('right', 'front', 'left', 'back'):
            x, y, w, h = r[k]
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
