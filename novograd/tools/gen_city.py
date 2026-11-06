#!/usr/bin/env python3
"""Текстуры, модели и названия городских блоков: панель лифта и пол кабины.
Запуск из папки novograd: python3 tools/gen_city.py
"""
import json, os, random
from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'assets', 'novograd')


def save(img, rel):
    p = os.path.join(ROOT, 'textures', rel)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    img.save(p)


def dump(obj, rel):
    p = os.path.join(ROOT, rel)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, 'w', encoding='utf-8') as f:
        json.dump(obj, f, ensure_ascii=False, indent=2)


def steel(size=32, base=(150, 156, 164), seed=1):
    """Шлифованная сталь: горизонтальные штрихи."""
    r = random.Random(seed)
    img = Image.new('RGBA', (size, size))
    px = img.load()
    for y in range(size):
        row = r.randint(-8, 8)
        for x in range(size):
            d = row + r.randint(-4, 4)
            px[x, y] = tuple(max(0, min(255, v + d)) for v in base) + (255,)
    return img


def panel_front():
    img = steel(32, (128, 134, 142), 3)
    d = ImageDraw.Draw(img)
    d.rectangle([1, 1, 30, 30], outline=(70, 74, 80, 255))
    # табло этажа
    d.rectangle([9, 3, 22, 8], fill=(12, 14, 18, 255))
    d.rectangle([12, 4, 14, 7], fill=(255, 150, 40, 255))
    d.rectangle([17, 4, 19, 7], fill=(255, 150, 40, 255))
    # кнопки двумя столбиками, пара горит
    lit = {(0, 1), (1, 3)}
    for row in range(5):
        for col in range(2):
            x = 10 + col * 8
            y = 11 + row * 4
            on = (col, row) in lit
            d.ellipse([x, y, x + 3, y + 3], fill=(255, 196, 60, 255) if on else (40, 44, 50, 255), outline=(200, 205, 212, 255))
    return img


def floor_top():
    img = steel(32, (140, 144, 150), 5)
    d = ImageDraw.Draw(img)
    # рифлёный лист: косые «зёрна»
    for y in range(2, 32, 6):
        for x in range(2 + (y // 6 % 2) * 3, 32, 6):
            d.line([x, y + 2, x + 2, y], fill=(190, 194, 200, 255))
            d.point((x + 1, y + 2), fill=(90, 94, 100, 255))
    d.rectangle([0, 0, 31, 31], outline=(200, 160, 60, 255))
    return img


def floor_bottom():
    img = steel(32, (225, 228, 232), 6)
    d = ImageDraw.Draw(img)
    d.rectangle([5, 5, 26, 26], fill=(255, 250, 225, 255), outline=(170, 172, 178, 255))  # плафон в потолке кабины
    return img


def main():
    save(panel_front(), 'block/elevator_panel.png')
    save(steel(32, (150, 156, 164), 4), 'block/elevator_steel.png')
    save(floor_top(), 'block/elevator_floor.png')
    save(floor_bottom(), 'block/elevator_ceiling.png')

    dump({'parent': 'minecraft:block/orientable', 'textures': {
        'front': 'novograd:block/elevator_panel', 'side': 'novograd:block/elevator_steel', 'top': 'novograd:block/elevator_steel'}},
        'models/block/elevator_panel.json')
    dump({'variants': {f'facing={f}': {'model': 'novograd:block/elevator_panel', **({'y': r} if r else {})}
                       for f, r in (('north', 0), ('east', 90), ('south', 180), ('west', 270))}},
         'blockstates/elevator_panel.json')
    dump({'parent': 'minecraft:block/cube_bottom_top', 'textures': {
        'top': 'novograd:block/elevator_floor', 'bottom': 'novograd:block/elevator_ceiling', 'side': 'novograd:block/elevator_steel'}},
        'models/block/elevator_floor.json')
    dump({'variants': {f'facing={f},roof={r}': {'model': 'novograd:block/elevator_floor'}
                       for f in ('north', 'east', 'south', 'west') for r in ('false', 'true')}},
         'blockstates/elevator_floor.json')
    for n in ('elevator_panel', 'elevator_floor'):
        dump({'parent': f'novograd:block/{n}'}, f'models/item/{n}.json')

    billboards()
    names = {
        'ru_ru': {'block.novograd.elevator_panel': 'Панель лифта', 'block.novograd.elevator_floor': 'Пол кабины лифта', 'block.novograd.billboard': 'Рекламный щит'},
        'uk_ua': {'block.novograd.elevator_panel': 'Панель ліфта', 'block.novograd.elevator_floor': 'Підлога кабіни ліфта', 'block.novograd.billboard': 'Рекламний щит'},
        'en_us': {'block.novograd.elevator_panel': 'Elevator Panel', 'block.novograd.elevator_floor': 'Elevator Cab Floor', 'block.novograd.billboard': 'Billboard'},
    }
    for lang, add in names.items():
        p = os.path.join(ROOT, 'lang', lang + '.json')
        with open(p, encoding='utf-8') as f:
            text = f.read()
        data = json.loads(text)
        lines = [f'  "{k}": {json.dumps(v, ensure_ascii=False)},' for k, v in add.items() if k not in data]
        if not lines:
            continue
        # вставляем сразу после первой строки «{», не трогая форматирование остального файла
        i = text.index('{') + 1
        text = text[:i] + '\n' + '\n'.join(lines) + text[i:]
        json.loads(text)
        with open(p, 'w', encoding='utf-8') as f:
            f.write(text)


# ---------------------------------------------------------------- рекламные щиты
FONT_B = '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'
FONT = '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'


def _font(path, size):
    from PIL import ImageFont
    return ImageFont.truetype(path, size)


def _text(d, xy, s, size, fill, bold=True, anchor='mm', stroke=0, stroke_fill=None, maxw=None):
    """Текст; если не влезает в maxw (по умолчанию — до края щита от центра), шрифт уменьшается."""
    if maxw is None and anchor == 'mm':
        maxw = 2 * min(xy[0], d.im.size[0] - xy[0]) - 8 * 4
    f = _font(FONT_B if bold else FONT, size)
    while maxw and size > 8 and d.textlength(s, font=f) + 2 * stroke > maxw:
        size -= 2
        f = _font(FONT_B if bold else FONT, size)
    d.text(xy, s, font=f, fill=fill, anchor=anchor, stroke_width=stroke, stroke_fill=stroke_fill)


def ad_image(i):
    """Реклама 6×3 блока (по 32 px), рисуем в 4 раза крупнее и уменьшаем — так буквы гладкие."""
    S = 4
    W, H = 192 * S, 96 * S
    img = Image.new('RGBA', (W, H))
    d = ImageDraw.Draw(img)
    if i == 0:  # кола «Бодрость»
        d.rectangle([0, 0, W, H], fill=(200, 20, 30, 255))
        for k in range(0, W, 6):
            y = int(H * 0.84 + 7 * S * __import__('math').sin(k / W * 6.28))
            d.line([k, y, k, y + 10 * S], fill=(255, 255, 255, 255), width=6)
        d.rounded_rectangle([16 * S, 12 * S, 34 * S, 84 * S], 8 * S, fill=(60, 10, 10, 255))
        d.rectangle([21 * S, 6 * S, 29 * S, 14 * S], fill=(60, 10, 10, 255))
        d.rectangle([18 * S, 40 * S, 32 * S, 52 * S], fill=(255, 255, 255, 255))
        _text(d, (112 * S, 34 * S), 'БОДРОСТЬ', 26 * S, (255, 255, 255, 255), stroke=2 * S, stroke_fill=(120, 0, 0, 255))
        _text(d, (112 * S, 58 * S), 'КОЛА · ЛЕДЯНАЯ', 11 * S, (255, 230, 120, 255))
    elif i == 1:  # «Новоград-Банк»
        d.rectangle([0, 0, W, H], fill=(14, 30, 72, 255))
        d.rectangle([0, H - 14 * S, W, H], fill=(200, 160, 50, 255))
        d.ellipse([10 * S, 18 * S, 50 * S, 58 * S], fill=(220, 180, 60, 255), outline=(255, 230, 140, 255), width=3 * S)
        _text(d, (30 * S, 38 * S), '₽', 24 * S, (14, 30, 72, 255))
        _text(d, (122 * S, 30 * S), 'НОВОГРАД', 22 * S, (240, 200, 90, 255))
        _text(d, (122 * S, 52 * S), 'Б А Н К', 14 * S, (255, 255, 255, 255))
        _text(d, (96 * S, 89 * S), 'кредит за 5 минут · без справок', 8 * S, (14, 30, 72, 255))
    elif i == 2:  # такси «Шашка»
        d.rectangle([0, 0, W, H], fill=(255, 200, 20, 255))
        for k in range(0, 192, 8):
            for r in range(2):
                if (k // 8 + r) % 2 == 0:
                    d.rectangle([k * S, r * 6 * S, (k + 8) * S, (r + 1) * 6 * S], fill=(20, 20, 20, 255))
                    d.rectangle([k * S, H - (r + 1) * 6 * S, (k + 8) * S, H - r * 6 * S], fill=(20, 20, 20, 255))
        _text(d, (96 * S, 40 * S), 'ТАКСИ «ШАШКА»', 22 * S, (20, 20, 20, 255))
        _text(d, (96 * S, 66 * S), '☎ 777-000 · по городу от $9', 11 * S, (20, 20, 20, 255), bold=False)
    elif i == 3:  # радио «Волна 104.2»
        d.rectangle([0, 0, W, H], fill=(24, 8, 40, 255))
        for k in range(4):
            d.arc([(2 + k * 6) * S, (30 - k * 6) * S, (30 + k * 6) * S, (66 + k * 6) * S], -50, 50, fill=(40, 230, 255, 255), width=2 * S)
        _text(d, (122 * S, 36 * S), 'ВОЛНА 104.2', 22 * S, (255, 60, 170, 255), stroke=1 * S, stroke_fill=(255, 200, 240, 255))
        _text(d, (122 * S, 62 * S), 'хиты Новограда — нон-стоп', 10 * S, (40, 230, 255, 255), bold=False)
    elif i == 4:  # бургерная «Жар-Птица»
        for y in range(H):
            k = y / H
            d.line([0, y, W, y], fill=(int(255 - 40 * k), int(120 - 60 * k), 20, 255))
        d.ellipse([12 * S, 22 * S, 52 * S, 46 * S], fill=(205, 130, 40, 255))
        d.rectangle([12 * S, 44 * S, 52 * S, 50 * S], fill=(90, 200, 60, 255))
        d.rectangle([12 * S, 50 * S, 52 * S, 58 * S], fill=(110, 50, 20, 255))
        d.rounded_rectangle([12 * S, 58 * S, 52 * S, 68 * S], 4 * S, fill=(205, 130, 40, 255))
        _text(d, (122 * S, 36 * S), 'ЖАР-ПТИЦА', 24 * S, (255, 236, 80, 255), stroke=2 * S, stroke_fill=(110, 30, 0, 255))
        _text(d, (122 * S, 62 * S), 'бургеры 24/7 · двойной за $5', 10 * S, (255, 255, 255, 255))
    else:  # АЗС «Нефтяник»
        d.rectangle([0, 0, W, H], fill=(245, 248, 245, 255))
        d.rectangle([0, 0, W, 30 * S], fill=(20, 140, 60, 255))
        _text(d, (96 * S, 15 * S), 'АЗС «НЕФТЯНИК»', 17 * S, (255, 255, 255, 255))
        d.polygon([(22 * S, 40 * S), (34 * S, 60 * S), (22 * S, 86 * S), (10 * S, 60 * S)], fill=(20, 140, 60, 255))
        d.ellipse([10 * S, 56 * S, 34 * S, 86 * S], fill=(20, 140, 60, 255))
        for n, (fuel, price) in enumerate((('АИ-92', '48,90'), ('АИ-95', '52,90'), ('ДТ', '61,40'))):
            y = (44 + n * 16) * S
            _text(d, (60 * S, y), fuel, 11 * S, (30, 30, 30, 255), anchor='lm')
            d.rectangle([118 * S, y - 7 * S, 178 * S, y + 7 * S], fill=(15, 15, 15, 255))
            _text(d, (148 * S, y), price, 11 * S, (255, 170, 30, 255))
    d.rectangle([0, 0, W - 1, H - 1], outline=(30, 30, 34, 255), width=2 * S)
    return img.resize((192, 96), Image.LANCZOS)


def billboards():
    frame = steel(32, (52, 56, 62), 9)
    dd = ImageDraw.Draw(frame)
    for x in (4, 27):
        for y in (4, 27):
            dd.point((x, y), fill=(120, 124, 130, 255))
    save(frame, 'block/billboard_frame.png')
    variants = {}
    for ad in range(6):
        img = ad_image(ad)
        for t in range(18):
            col, row = t % 6, t // 6
            save(img.crop((col * 32, row * 32, col * 32 + 32, row * 32 + 32)), f'block/billboard/a{ad}_{t}.png')
            dump({'parent': 'minecraft:block/orientable', 'textures': {'front': f'novograd:block/billboard/a{ad}_{t}',
                                                                       'side': 'novograd:block/billboard_frame', 'top': 'novograd:block/billboard_frame'}},
                 f'models/block/billboard/a{ad}_{t}.json')
            for f, r in (('north', 0), ('east', 90), ('south', 180), ('west', 270)):
                v = {'model': f'novograd:block/billboard/a{ad}_{t}'}
                if r:
                    v['y'] = r
                variants[f'ad={ad},facing={f},tile={t}'] = v
        img.save(os.path.join(os.path.dirname(os.path.abspath(__file__)), f'..', 'build', f'billboard_ad{ad}.png')) if os.path.isdir(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'build')) else None
    dump({'variants': variants}, 'blockstates/billboard.json')
    dump({'parent': 'novograd:block/billboard/a0_7'}, 'models/item/billboard.json')


if __name__ == '__main__':
    main()
