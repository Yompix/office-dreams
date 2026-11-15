#!/usr/bin/env python3
"""Текстуры «Глухомани» рисуются кодом: блоки (рация, щит, генератор…), предметы, существа (Жердь, Слухач, Хор),
скин Анны, виньетка и иконка. Запуск: /tmp/claude-0/ttsenv/bin/python tools/gen_textures.py (нужен Pillow)."""
import os
import random
from PIL import Image, ImageDraw, ImageFilter

R = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'assets', 'glukhoman')
rnd = random.Random(17)


def save(img, *path):
    p = os.path.join(R, *path)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    img.save(p)


def noisy(size, base, var=12, alpha=255):
    w, h = size
    img = Image.new('RGBA', size)
    px = img.load()
    for y in range(h):
        for x in range(w):
            d = rnd.randint(-var, var)
            px[x, y] = tuple(max(0, min(255, c + d)) for c in base) + (alpha,)
    return img


def metal(base=(92, 98, 96)):
    img = noisy((16, 16), base, 8)
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, 15, 15], outline=tuple(max(0, c - 35) for c in base) + (255,))
    for _ in range(5):  # ржавчина
        x, y = rnd.randint(1, 14), rnd.randint(1, 14)
        d.point((x, y), fill=(120, 72, 40, 255))
    return img


def blocks():
    B = ('textures', 'block')
    # рация: зелёный корпус, шкала, ручки
    side = metal((58, 74, 58)); save(side, *B, 'radio.png'); save(side, *B, 'radio_top.png')
    f = side.copy(); d = ImageDraw.Draw(f)
    d.rectangle([2, 3, 9, 7], fill=(190, 170, 90, 255)); d.line([3, 5, 8, 5], fill=(60, 40, 20, 255)); d.point((6, 4), fill=(200, 30, 30, 255))
    d.ellipse([10, 3, 13, 6], fill=(30, 30, 30, 255)); d.ellipse([10, 9, 13, 12], fill=(30, 30, 30, 255))
    for x in range(2, 9, 2): d.line([x, 9, x, 13], fill=(25, 25, 25, 255))
    save(f, *B, 'radio_front.png')
    # магнитофон
    side = metal((70, 66, 62)); save(side, *B, 'tape_player.png')
    t = side.copy(); d = ImageDraw.Draw(t); d.rectangle([2, 4, 13, 11], fill=(30, 30, 32, 255)); d.ellipse([4, 6, 7, 9], fill=(150, 150, 150, 255)); d.ellipse([9, 6, 12, 9], fill=(150, 150, 150, 255))
    save(t, *B, 'tape_player_top.png')
    f = side.copy(); d = ImageDraw.Draw(f)
    for i, c in enumerate([(160, 40, 40), (140, 140, 140), (140, 140, 140), (140, 140, 140)]): d.rectangle([2 + i * 3, 8, 3 + i * 3, 11], fill=c + (255,))
    save(f, *B, 'tape_player_front.png')
    # щит
    side = metal((120, 124, 126)); save(side, *B, 'fuse_box.png'); save(side, *B, 'fuse_box_top.png')
    f = side.copy(); d = ImageDraw.Draw(f); d.polygon([(8, 2), (5, 8), (8, 8), (6, 13), (11, 6), (8, 6), (10, 2)], fill=(230, 200, 40, 255))
    d.rectangle([2, 13, 13, 14], fill=(40, 40, 40, 255)); save(f, *B, 'fuse_box_front.png')
    # генератор
    side = metal((150, 90, 40)); save(side, *B, 'generator.png')
    t = metal((70, 70, 70)); save(t, *B, 'generator_top.png')
    f = side.copy(); d = ImageDraw.Draw(f); d.ellipse([3, 3, 12, 12], fill=(40, 40, 40, 255)); d.ellipse([6, 6, 9, 9], fill=(110, 110, 110, 255)); save(f, *B, 'generator_front.png')
    # усилитель
    side = metal((44, 46, 54)); save(side, *B, 'amplifier.png'); save(side, *B, 'amplifier_top.png')
    f = side.copy(); d = ImageDraw.Draw(f)
    d.rectangle([2, 2, 13, 6], fill=(20, 40, 30, 255)); d.line([3, 5, 6, 3, 9, 5, 12, 3], fill=(80, 220, 120, 255))
    for x in (3, 7, 11): d.ellipse([x - 1, 9, x + 1, 11], fill=(180, 180, 180, 255))
    d.point((13, 13), fill=(220, 40, 40, 255)); save(f, *B, 'amplifier_front.png')
    # передатчик
    side = metal((60, 62, 70)); d = ImageDraw.Draw(side)
    for y in range(3, 14, 3): d.line([1, y, 14, y], fill=(30, 30, 36, 255))
    save(side, *B, 'transmitter.png')
    t = metal((50, 50, 58)); d = ImageDraw.Draw(t); d.ellipse([5, 5, 10, 10], outline=(200, 170, 80, 255)); save(t, *B, 'transmitter_top.png')
    f = metal((60, 62, 70)); d = ImageDraw.Draw(f)
    d.rectangle([2, 2, 13, 7], fill=(15, 20, 15, 255)); d.text((3, 1), '417', fill=(220, 160, 40, 255))
    d.rectangle([5, 10, 10, 13], fill=(140, 30, 30, 255)); save(f, *B, 'transmitter_front.png')
    # записка
    n = Image.new('RGBA', (16, 16), (0, 0, 0, 0)); d = ImageDraw.Draw(n)
    d.rectangle([3, 3, 12, 12], fill=(222, 214, 190, 255))
    for y in range(5, 12, 2): d.line([4, y, 4 + rnd.randint(4, 7), y], fill=(70, 60, 90, 255))
    save(n, *B, 'note.png'); save(n, *B, 'note_top.png'); save(n, *B, 'note_front.png')
    # метка мелом: стрелка и крест
    m = Image.new('RGBA', (16, 16), (0, 0, 0, 0)); d = ImageDraw.Draw(m)
    d.line([2, 8, 12, 8], fill=(235, 235, 225, 230)); d.line([12, 8, 9, 5], fill=(235, 235, 225, 230)); d.line([12, 8, 9, 11], fill=(235, 235, 225, 230))
    d.text((3, 10), 'A', fill=(235, 235, 225, 200))
    for nm in ('chalk_mark', 'chalk_mark_front', 'chalk_mark_top'): save(m, *B, nm + '.png')
    # замок
    side = metal((70, 66, 60)); save(side, *B, 'lock.png'); save(side, *B, 'lock_top.png')
    f = side.copy(); d = ImageDraw.Draw(f); d.rectangle([5, 5, 10, 12], fill=(40, 38, 36, 255)); d.point((7, 8), fill=(5, 5, 5, 255)); d.line([7, 9, 7, 10], fill=(5, 5, 5, 255))
    save(f, *B, 'lock_front.png')
    # ящик
    c = noisy((16, 16), (112, 82, 50), 10); d = ImageDraw.Draw(c)
    d.rectangle([0, 0, 15, 15], outline=(62, 44, 26, 255)); d.line([0, 0, 15, 15], fill=(80, 58, 34, 255)); d.line([0, 15, 15, 0], fill=(80, 58, 34, 255))
    for nm in ('crate', 'crate_front'): save(c, *B, nm + '.png')
    t = noisy((16, 16), (100, 74, 46), 10); d = ImageDraw.Draw(t)
    for y in (0, 5, 10, 15): d.line([0, y, 15, y], fill=(62, 44, 26, 255))
    save(t, *B, 'crate_top.png')
    # лампы
    save(metal((40, 40, 40)), *B, 'lamp_shade.png')
    on = Image.new('RGBA', (16, 16), (255, 236, 170, 255)); save(on, *B, 'bulb_on.png')
    off = Image.new('RGBA', (16, 16), (90, 88, 80, 255)); save(off, *B, 'bulb_off.png')


def items():
    I = ('textures', 'item')

    def canvas():
        return Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    im = canvas(); d = ImageDraw.Draw(im)  # фонарик
    d.polygon([(3, 12), (9, 6), (11, 8), (5, 14)], fill=(60, 60, 64, 255)); d.polygon([(9, 6), (11, 4), (14, 7), (11, 8)], fill=(110, 110, 116, 255))
    d.line([12, 5, 13, 6], fill=(255, 240, 160, 255)); save(im, *I, 'flashlight.png')
    im = canvas(); d = ImageDraw.Draw(im)  # батарейка
    d.rectangle([5, 4, 10, 13], fill=(40, 40, 44, 255)); d.rectangle([5, 4, 10, 7], fill=(200, 120, 30, 255)); d.rectangle([7, 2, 8, 3], fill=(180, 180, 180, 255))
    save(im, *I, 'battery.png')
    im = canvas(); d = ImageDraw.Draw(im)  # керосинка
    d.rectangle([5, 12, 10, 14], fill=(120, 40, 30, 255)); d.rectangle([6, 5, 9, 11], fill=(230, 200, 120, 200)); d.point((7, 8), fill=(255, 150, 40, 255))
    d.line([5, 4, 10, 4], fill=(60, 60, 60, 255)); d.arc([5, 0, 10, 6], 180, 360, fill=(60, 60, 60, 255)); save(im, *I, 'oil_lamp.png')
    im = canvas(); d = ImageDraw.Draw(im)  # бутылка керосина
    d.rectangle([5, 6, 10, 14], fill=(150, 120, 60, 220)); d.rectangle([7, 3, 8, 5], fill=(90, 70, 40, 255)); d.rectangle([5, 9, 10, 11], fill=(220, 210, 180, 255))
    save(im, *I, 'oil.png')
    im = canvas(); d = ImageDraw.Draw(im); d.ellipse([4, 6, 11, 12], fill=(120, 120, 116, 255)); d.point((6, 8), fill=(160, 160, 156, 255)); save(im, *I, 'pebble.png')
    im = canvas(); d = ImageDraw.Draw(im)  # предохранитель
    d.rectangle([6, 2, 9, 13], fill=(230, 225, 210, 255)); d.rectangle([6, 2, 9, 4], fill=(170, 170, 170, 255)); d.rectangle([6, 11, 9, 13], fill=(170, 170, 170, 255))
    d.line([7, 5, 8, 10], fill=(200, 60, 40, 255)); save(im, *I, 'fuse.png')
    for name, col in (('gate_key', (150, 120, 60)), ('mine_key', (140, 140, 150))):
        im = canvas(); d = ImageDraw.Draw(im)
        d.ellipse([2, 2, 7, 7], outline=col + (255,), width=2); d.line([6, 6, 13, 13], fill=col + (255,), width=2); d.line([11, 11, 13, 9], fill=col + (255,), width=1)
        d.line([9, 9, 11, 7], fill=col + (255,), width=1); save(im, *I, name + '.png')
    im = canvas(); d = ImageDraw.Draw(im)  # канистра
    d.rectangle([3, 4, 12, 14], fill=(170, 40, 30, 255)); d.rectangle([9, 2, 11, 4], fill=(60, 60, 60, 255)); d.line([4, 6, 11, 13], fill=(130, 30, 22, 255))
    save(im, *I, 'canister.png')
    im = canvas(); d = ImageDraw.Draw(im)  # камертон
    d.line([7, 9, 7, 15], fill=(200, 200, 210, 255), width=1); d.line([5, 2, 5, 9], fill=(220, 220, 230, 255)); d.line([9, 2, 9, 9], fill=(220, 220, 230, 255))
    d.line([5, 9, 9, 9], fill=(220, 220, 230, 255)); save(im, *I, 'tuning_fork.png')


def bark(w, h, base=(118, 110, 96)):
    img = noisy((w, h), base, 16)
    d = ImageDraw.Draw(img)
    for _ in range(w * h // 40):
        x, y = rnd.randrange(w), rnd.randrange(h)
        d.line([x, y, x, min(h - 1, y + rnd.randint(2, 6))], fill=(70, 64, 56, 255))
    return img


def entities():
    E = ('textures', 'entity')
    # Жердь 64×64: светло-серая кора, лицо — пустые глазницы и щель рта
    p = bark(64, 64, (128, 122, 108))
    d = ImageDraw.Draw(p)
    # голова uv(0,40) 6×8×6: лицо на (6,46) 6×8
    d.rectangle([6, 46, 11, 53], fill=(196, 190, 176, 255))
    d.rectangle([7, 48, 8, 50], fill=(5, 5, 5, 255)); d.rectangle([10, 48, 10, 50], fill=(5, 5, 5, 255))
    d.line([7, 52, 10, 52], fill=(20, 10, 10, 255))
    d.rectangle([0, 46, 5, 53], fill=(150, 144, 130, 255)); d.rectangle([12, 46, 23, 53], fill=(150, 144, 130, 255))
    save(p, *E, 'pole.png')
    # Слухач 64×64: бледно-серая кожа, тёмная пасть, уши с прожилками
    l = noisy((64, 64), (150, 146, 140), 14)
    d = ImageDraw.Draw(l)
    d.rectangle([0, 40, 12, 44], fill=(40, 10, 12, 255))          # пасть
    for y in range(24, 39, 3): d.line([28, y, 41, y + 1], fill=(170, 110, 110, 255))  # уши
    d.rectangle([7, 31, 12, 36], fill=(170, 166, 160, 255))        # гладкое «лицо» без глаз
    for _ in range(40):
        x, y = rnd.randrange(64), rnd.randrange(24)
        d.point((x, y), fill=(110, 100, 100, 255))
    save(l, *E, 'listener.png')
    # Хор 128×128: тёмная масса и бледные лица
    c = noisy((128, 128), (34, 30, 38), 10)
    d = ImageDraw.Draw(c)
    for _ in range(260):
        x, y = rnd.randrange(64), rnd.randrange(80)
        d.point((x, y), fill=(70, 50, 70, 255))
    # лицо-голова uv(64,0) 5×5×5: фронт на (69,5) 5×5
    d.rectangle([64, 0, 83, 9], fill=(170, 160, 150, 255))
    d.rectangle([69, 5, 73, 9], fill=(205, 196, 186, 255))
    d.point((70, 6), fill=(10, 10, 10, 255)); d.point((72, 6), fill=(10, 10, 10, 255)); d.line([70, 8, 72, 8], fill=(40, 10, 10, 255))
    save(c, *E, 'choir.png')
    # Анна (скин 64×64, тонкие руки): тёмные волосы, оранжевая штормовка, джинсы
    a = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
    d = ImageDraw.Draw(a)
    d.rectangle([0, 0, 31, 15], fill=(58, 40, 30, 255))                 # голова (волосы)
    d.rectangle([8, 8, 15, 15], fill=(226, 190, 160, 255))              # лицо
    d.rectangle([8, 8, 15, 9], fill=(58, 40, 30, 255))
    d.point((10, 11), fill=(40, 60, 80, 255)); d.point((13, 11), fill=(40, 60, 80, 255)); d.line([11, 13, 12, 13], fill=(170, 100, 90, 255))
    d.rectangle([9, 11, 9, 11], fill=(255, 255, 255, 255)); d.rectangle([14, 11, 14, 11], fill=(255, 255, 255, 255))
    d.rectangle([16, 16, 39, 31], fill=(214, 110, 40, 255))             # куртка
    d.rectangle([40, 16, 53, 31], fill=(200, 100, 36, 255))             # правая рука
    d.rectangle([32, 48, 45, 63], fill=(200, 100, 36, 255))             # левая рука
    d.rectangle([44, 28, 53, 31], fill=(226, 190, 160, 255)); d.rectangle([36, 60, 45, 63], fill=(226, 190, 160, 255))
    d.rectangle([0, 16, 15, 31], fill=(52, 62, 96, 255))                 # ноги
    d.rectangle([16, 48, 31, 63], fill=(52, 62, 96, 255))
    d.rectangle([0, 28, 15, 31], fill=(40, 30, 26, 255)); d.rectangle([16, 60, 31, 63], fill=(40, 30, 26, 255))
    d.rectangle([20, 20, 27, 21], fill=(240, 240, 230, 255))            # полоска на куртке
    save(a, *E, 'anna.png')


def gui():
    s = 256
    v = Image.new('L', (s, s), 0)
    px = v.load()
    for y in range(s):
        for x in range(s):
            dx, dy = (x - s / 2) / (s / 2), (y - s / 2) / (s / 2)
            r = (dx * dx * 0.9 + dy * dy * 1.1) ** 0.5
            px[x, y] = int(max(0, min(1, (r - 0.45) / 0.65)) ** 1.6 * 255)
    img = Image.new('RGBA', (s, s), (0, 0, 0, 0))
    img.putalpha(v.filter(ImageFilter.GaussianBlur(4)))
    save(img, 'textures', 'gui', 'vignette.png')
    # иконка мода: тёмный лес, луна, фигура
    ic = Image.new('RGBA', (128, 128), (14, 16, 20, 255))
    d = ImageDraw.Draw(ic)
    d.ellipse([84, 14, 108, 38], fill=(200, 200, 190, 255))
    for i in range(9):
        x = i * 16 - 6 + rnd.randint(-3, 3); h = rnd.randint(60, 90)
        d.polygon([(x, 128), (x + 9, 128 - h), (x + 18, 128)], fill=(20 + i % 2 * 6, 30, 26, 255))
    d.rectangle([60, 56, 63, 112], fill=(150, 146, 136, 255)); d.rectangle([58, 46, 65, 56], fill=(196, 190, 176, 255))
    d.point((60, 50), fill=(0, 0, 0, 255)); d.point((63, 50), fill=(0, 0, 0, 255))
    save(ic, 'icon.png')


def decor():
    """Плакаты «Пропал человек» (3), царапины, кукла, семейное фото — три состояния (обычное, лица зацарапаны, лишний человек)."""
    B = ('textures', 'block')
    names = ['САВЕЛЬЕВЫ', 'КУЗЬМИН', 'А. ВЕТРОВА']
    for v in range(3):
        p = Image.new('RGBA', (16, 16), (0, 0, 0, 0)); d = ImageDraw.Draw(p)
        d.rectangle([1, 2, 14, 14], fill=(214, 206, 178, 255))
        d.rectangle([2, 3, 13, 4], fill=(150, 30, 30, 255))             # «ПРОПАЛ» красной полосой
        d.rectangle([5, 6, 10, 11], fill=(120, 116, 108, 255))          # фото
        d.ellipse([6, 6, 9, 9], fill=(60, 56, 52, 255)); d.rectangle([6, 9, 9, 11], fill=(60, 56, 52, 255))
        if v == 2: d.point((7, 7), fill=(240, 240, 240, 255)); d.point((8, 7), fill=(240, 240, 240, 255))
        for y in (12, 13): d.line([3, y, 3 + rnd.randint(6, 9), y], fill=(70, 60, 60, 255))
        d.point((rnd.randint(2, 13), 14), fill=(120, 110, 90, 255))
        save(p, *B, f'poster_{v}.png')
    sc = Image.new('RGBA', (16, 16), (0, 0, 0, 0)); d = ImageDraw.Draw(sc)
    for i in range(4):
        x = 3 + i * 3
        d.line([x, 2, x - 2 + rnd.randint(0, 2), 14], fill=(60, 50, 46, 210))
    d.line([2, 6, 14, 9], fill=(70, 60, 56, 160))
    save(sc, *B, 'scratches.png')
    for nm, col in (('poster_side', (180, 172, 150)), ('poster_top', (180, 172, 150)), ('scratches_side', (90, 80, 70)), ('scratches_top', (90, 80, 70)), ('doll_side', (170, 120, 120)), ('doll_top', (226, 210, 196)), ('photo_side', (70, 46, 30))):
        save(noisy((16, 16), col, 8), *B, nm + '.png')
    dl = noisy((16, 16), (226, 210, 196), 6); d = ImageDraw.Draw(dl)
    d.point((6, 5), fill=(10, 10, 10, 255)); d.point((9, 5), fill=(10, 10, 10, 255)); d.line([6, 9, 9, 9], fill=(130, 60, 60, 255))
    d.rectangle([0, 0, 15, 2], fill=(150, 100, 40, 255)); save(dl, *B, 'doll.png')
    # кровь на полу: 0 — брызги, 1 — лужа, 2 — следы волочения (к лесу)
    for v in range(3):
        bl = Image.new('RGBA', (16, 16), (0, 0, 0, 0)); d = ImageDraw.Draw(bl)
        if v == 0:
            for _ in range(14):
                x, y = rnd.randint(1, 14), rnd.randint(1, 14); r = rnd.choice([0, 0, 1])
                d.ellipse([x - r, y - r, x + r, y + r], fill=(96, 12, 14, 230))
        elif v == 1:
            d.ellipse([3, 4, 12, 12], fill=(80, 8, 10, 235)); d.ellipse([9, 2, 14, 7], fill=(80, 8, 10, 235))
            d.point((6, 6), fill=(160, 50, 50, 255)); d.point((7, 6), fill=(150, 40, 40, 255))
        else:
            for x0 in (5, 10):
                for y in range(16):
                    if rnd.random() < 0.8: d.line([x0 + rnd.randint(-1, 1), y, x0 + 1, y], fill=(86, 14, 14, 200))
        save(bl, *B, f'blood_{v}.png')
    # 18+: останки и куски
    for nm, col, var in (('remains_cloth', (64, 74, 58), 14), ('remains_skin', (196, 168, 150), 8), ('remains_bone', (222, 214, 196), 6)):
        im = noisy((16, 16), col, var); d = ImageDraw.Draw(im)
        for _ in range(10 if nm != 'remains_bone' else 3):
            x, y = rnd.randint(0, 15), rnd.randint(0, 15); d.point((x, y), fill=(110, 14, 16, 255))
        save(im, *B, nm + '.png')
    fl = noisy((16, 16), (138, 30, 34), 18); d = ImageDraw.Draw(fl)
    for _ in range(6):
        y = rnd.randint(0, 15); d.line([0, y, 15, y + rnd.randint(-2, 2)], fill=(176, 80, 86, 255))   # кишечные складки
    for _ in range(8): d.point((rnd.randint(0, 15), rnd.randint(0, 15)), fill=(210, 120, 120, 255))       # влажный блеск
    save(fl, *B, 'remains_flesh.png')
    gc = Image.new('RGBA', (16, 16), (0, 0, 0, 0)); d = ImageDraw.Draw(gc)
    d.rectangle([5, 5, 10, 10], fill=(150, 34, 38, 255)); d.rectangle([7, 6, 9, 8], fill=(214, 200, 180, 255)); d.point((6, 9), fill=(90, 10, 12, 255))
    save(gc, 'textures', 'item', 'gore_chunk.png')
    bd = Image.new('RGBA', (16, 16), (0, 0, 0, 0)); d = ImageDraw.Draw(bd); d.ellipse([5, 6, 10, 11], fill=(110, 12, 14, 255)); d.polygon([(7, 2), (6, 7), (9, 7)], fill=(110, 12, 14, 255))
    save(bd, 'textures', 'item', 'blood_drop.png')
    for nm in ('blood_side', 'blood_top'):
        save(Image.new('RGBA', (16, 16), (86, 14, 14, 200)), *B, nm + '.png')
    for v in range(3):
        ph = Image.new('RGBA', (16, 16), (70, 46, 30, 255)); d = ImageDraw.Draw(ph)
        d.rectangle([1, 1, 14, 14], fill=(176, 166, 140, 255))
        d.rectangle([1, 10, 14, 14], fill=(110, 120, 100, 255))
        for i, x in enumerate((3, 7, 11)):                               # семья из трёх
            d.ellipse([x - 1, 4, x + 1, 6], fill=(214, 190, 160, 255)); d.rectangle([x - 1, 7, x + 1, 11], fill=(60, 70, 90, 255))
            if v >= 1: d.line([x - 1, 4, x + 1, 6], fill=(20, 20, 20, 255)); d.line([x + 1, 4, x - 1, 6], fill=(20, 20, 20, 255))
        if v == 2:                                                       # четвёртый — высокий, сзади
            d.rectangle([13, 2, 13, 11], fill=(30, 28, 26, 255)); d.point((13, 3), fill=(240, 236, 210, 255))
        save(ph, *B, f'photo_{v}.png')


if __name__ == '__main__':
    blocks(); items(); gui(); decor()  # существа и люди — tools/gen_geo.py
    print('ok')
