#!/usr/bin/env python3
"""«За городом»: олень и кабан для GeckoLib (модели, анимации, текстуры), иконки предметов (шкуры, мясо, рыба, лицензия),
блок «подарок», значки приложений «Навыки» и «Природа», таблица улова для удочки.
Всё рисуется с нуля. Запуск из папки novograd: python3 tools/gen_outdoor.py
"""
import json, math, os, random, sys
from PIL import Image, ImageDraw, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, '..', 'src', 'main', 'resources')
A = os.path.join(RES, 'assets', 'novograd')
rnd = random.Random(7)


def save_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as f:
        json.dump(obj, f, ensure_ascii=False, indent=2)
        f.write('\n')


# ------------------------------------------------------------------ текстуры зверей: атлас 8×8 пятен по 8 пикселей
def atlas(colors, path, spots=None):
    img = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
    px = img.load()
    for i, c in enumerate(colors):
        if c is None:
            continue
        ox, oy = (i % 8) * 8, (i // 8) * 8
        for y in range(8):
            for x in range(8):
                n = rnd.uniform(-1, 1)
                k = 1 + n * 0.09 + (0.05 if (x + y * 3) % 5 == 0 else 0)
                px[ox + x, oy + y] = tuple(max(0, min(255, int(v * k))) for v in c) + (255,)
        if spots and i in spots:
            for _ in range(5):
                x, y = rnd.randrange(8), rnd.randrange(8)
                px[ox + x, oy + y] = spots[i] + (255,)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


def patch_uv(i):
    return [(i % 8) * 8, (i // 8) * 8]


def cube(origin, size, side, top=None, bottom=None, front=None, inflate=0):
    top = side if top is None else top
    bottom = side if bottom is None else bottom
    front = side if front is None else front
    uv = {}
    for face, p in (('north', front), ('south', side), ('east', side), ('west', side), ('up', top), ('down', bottom)):
        uv[face] = {'uv': patch_uv(p), 'uv_size': [8, 8]}
    c = {'origin': origin, 'size': size, 'uv': uv}
    if inflate:
        c['inflate'] = inflate
    return c


def geo(name, bones, h):
    return {'format_version': '1.12.0', 'minecraft:geometry': [{
        'description': {'identifier': 'geometry.' + name, 'texture_width': 64, 'texture_height': 64,
                        'visible_bounds_width': 3, 'visible_bounds_height': h, 'visible_bounds_offset': [0, h / 2, 0]},
        'bones': bones}]}


def bone(name, parent, pivot, cubes=None, rotation=None):
    b = {'name': name, 'pivot': pivot}
    if parent:
        b['parent'] = parent
    if rotation:
        b['rotation'] = rotation
    if cubes:
        b['cubes'] = cubes
    return b


# пятна: 0 шерсть, 1 брюхо, 2 ноги, 3 копыта, 4 рога/клыки, 5 нос, 6 глаз, 7 хвост/грива, 8 спина
DEER = [(150, 98, 58), (222, 196, 158), (118, 80, 50), (42, 32, 26), (226, 212, 182), (30, 24, 22), (12, 10, 10), (238, 232, 222), (126, 82, 48)]
BOAR = [(82, 66, 54), (118, 100, 86), (60, 50, 44), (32, 26, 22), (242, 236, 214), (156, 112, 102), (12, 10, 10), (46, 38, 32), (70, 56, 46)]


def deer():
    legs = []
    for nm, x, z in (('leg_fl', -2.5, -7), ('leg_fr', 2.5, -7), ('leg_bl', -2.5, 7), ('leg_br', 2.5, 7)):
        legs.append(bone(nm, 'root', [x, 14, z], [cube([x - 1, 1.5, z - 1], [2, 13, 2], 2, top=2, bottom=2),
                                                    cube([x - 1.1, 0, z - 1.1], [2.2, 1.5, 2.2], 3)]))
    bones = [bone('root', None, [0, 0, 0]),
             bone('body', 'root', [0, 17, 0], [cube([-4, 13, -9], [8, 8, 18], 0, top=8, bottom=1),
                                               cube([-3.5, 13.5, 8.5], [7, 6, 1], 7)]),
             bone('neck', 'body', [0, 20, -7], [cube([-2, 19, -12], [4, 9, 4], 0, front=1)], rotation=[-18, 0, 0]),
             bone('head', 'neck', [0, 27, -10], [cube([-2.5, 26, -16], [5, 5, 7], 0, front=0, bottom=1),
                                                 cube([-1.6, 26.2, -17], [3.2, 2.6, 1.2], 5),
                                                 cube([-2.6, 28.6, -14.6], [0.4, 1, 1], 6), cube([2.2, 28.6, -14.6], [0.4, 1, 1], 6),
                                                 cube([-4.5, 29.5, -10.5], [2.2, 3, 0.8], 0), cube([2.3, 29.5, -10.5], [2.2, 3, 0.8], 0)]),
             bone('antlers', 'head', [0, 31, -11], [
                 cube([-2.4, 31, -11.2], [1, 5, 1], 4), cube([1.4, 31, -11.2], [1, 5, 1], 4),
                 cube([-5.4, 34, -11.2], [3, 1, 1], 4), cube([2.4, 34, -11.2], [3, 1, 1], 4),
                 cube([-6.2, 34, -11.2], [1, 4, 1], 4), cube([5.2, 34, -11.2], [1, 4, 1], 4),
                 cube([-2.4, 33, -13.2], [1, 1, 2], 4), cube([1.4, 33, -13.2], [1, 1, 2], 4),
                 cube([-2.4, 36, -11.2], [1, 2, 1], 4), cube([1.4, 36, -11.2], [1, 2, 1], 4)]),
             bone('tail', 'body', [0, 20.5, 9], [cube([-1, 17.5, 9], [2, 3.5, 1.2], 7)])] + legs
    return geo('deer', bones, 3)


def boar():
    legs = []
    for nm, x, z in (('leg_fl', -2.4, -5.5), ('leg_fr', 2.4, -5.5), ('leg_bl', -2.4, 5.5), ('leg_br', 2.4, 5.5)):
        legs.append(bone(nm, 'root', [x, 7, z], [cube([x - 1.25, 1, z - 1.25], [2.5, 6.5, 2.5], 2),
                                                   cube([x - 1.3, 0, z - 1.3], [2.6, 1, 2.6], 3)]))
    bones = [bone('root', None, [0, 0, 0]),
             bone('body', 'root', [0, 11, 0], [cube([-4.5, 6, -8], [9, 9, 16], 0, top=8, bottom=1),
                                               cube([-1.5, 15, -8], [3, 2, 11], 7)]),
             bone('neck', 'body', [0, 11, -8]),
             bone('head', 'neck', [0, 12, -8], [cube([-3.5, 7, -14], [7, 7, 6], 0, front=0, bottom=1),
                                                cube([-2, 7.5, -16.5], [4, 3.5, 2.5], 5),
                                                cube([-2.9, 9, -16], [0.8, 3, 0.8], 4), cube([2.1, 9, -16], [0.8, 3, 0.8], 4),
                                                cube([-3.6, 11.2, -14.6], [0.4, 1, 1], 6), cube([3.2, 11.2, -14.6], [0.4, 1, 1], 6),
                                                cube([-3.6, 13.6, -10.5], [2, 2, 1], 0), cube([1.6, 13.6, -10.5], [2, 2, 1], 0)]),
             bone('tail', 'body', [0, 13, 8], [cube([-0.5, 10, 8], [1, 3.5, 1], 7)])] + legs
    return geo('boar', bones, 2)


def anim():
    def legs(freq, amp):
        e = 'math.sin(query.anim_time*%s)*%s' % (round(360 * freq, 2), amp)
        ne = '-math.sin(query.anim_time*%s)*%s' % (round(360 * freq, 2), amp)
        return {'leg_fl': {'rotation': [e, 0, 0]}, 'leg_br': {'rotation': [e, 0, 0]},
                'leg_fr': {'rotation': [ne, 0, 0]}, 'leg_bl': {'rotation': [ne, 0, 0]}}

    walk = legs(1.6, 26)
    walk['head'] = {'rotation': ['math.sin(query.anim_time*1152)*3', 0, 0]}
    walk['tail'] = {'rotation': ['math.sin(query.anim_time*576)*10', 0, 0]}
    run = legs(2.6, 46)
    run['body'] = {'rotation': ['math.sin(query.anim_time*936)*5', 0, 0], 'position': [0, 'math.abs(math.sin(query.anim_time*936))*1.5', 0]}
    run['neck'] = {'rotation': [-14, 0, 0]}
    run['tail'] = {'rotation': [30, 0, 0]}
    idle = {'head': {'rotation': ['math.sin(query.anim_time*40)*4', 'math.sin(query.anim_time*25)*18', 0]},
            'tail': {'rotation': ['math.max(0,math.sin(query.anim_time*180))*20', 0, 0]}}
    graze = {'neck': {'rotation': [52, 0, 0]}, 'head': {'rotation': ['18+math.sin(query.anim_time*400)*4', 0, 0]},
             'tail': {'rotation': ['math.max(0,math.sin(query.anim_time*150))*25', 0, 0]}}
    return {'format_version': '1.8.0', 'animations': {
        'animation.wild.walk': {'loop': True, 'animation_length': 1 / 1.6, 'bones': walk},
        'animation.wild.run': {'loop': True, 'animation_length': 1 / 2.6, 'bones': run},
        'animation.wild.idle': {'loop': True, 'animation_length': 14.4, 'bones': idle},
        'animation.wild.graze': {'loop': True, 'animation_length': 2.4, 'bones': graze}}}


# ------------------------------------------------------------------ иконки 16×16
def icon(name, draw):
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    draw(d, img)
    p = os.path.join(A, 'textures', 'item', name + '.png')
    os.makedirs(os.path.dirname(p), exist_ok=True)
    img.save(p)
    save_json(os.path.join(A, 'models', 'item', name + '.json'), {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'novograd:item/' + name}})


def shade(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c[:3]) + (255,)


def pelt(main, belly, tip=None):
    def f(d, img):
        d.polygon([(3, 3), (6, 4), (10, 4), (13, 3), (12, 6), (13, 9), (12, 12), (13, 14), (9, 12), (7, 12), (3, 14), (4, 11), (3, 8), (4, 6)], fill=main + (255,))
        d.polygon([(6, 6), (10, 6), (10, 10), (6, 10)], fill=belly + (255,))
        d.line([(3, 3), (6, 4), (10, 4), (13, 3)], fill=shade(main, 0.7))
        px = img.load()
        for _ in range(14):
            x, y = rnd.randrange(3, 13), rnd.randrange(4, 13)
            if px[x, y][3]:
                px[x, y] = shade(px[x, y], rnd.uniform(0.82, 1.12))
        if tip:
            d.rectangle([7, 12, 8, 15], fill=main + (255,))
            d.point([(7, 15), (8, 15)], fill=tip + (255,))
    return f


def meat(color, fat):
    def f(d, img):
        d.ellipse([2, 4, 13, 13], fill=color + (255,), outline=shade(color, 0.6))
        d.arc([4, 6, 11, 11], 200, 340, fill=fat + (255,))
        d.rectangle([11, 3, 14, 5], fill=(236, 228, 212, 255))
        d.point([(14, 2), (15, 3)], fill=(236, 228, 212, 255))
    return f


def fish(back, belly, fin, length=12, stripes=None, spots=None, whiskers=False):
    def f(d, img):
        x0 = (16 - length) // 2
        x1 = x0 + length - 3
        d.ellipse([x0, 5, x1, 11], fill=belly + (255,))
        d.chord([x0, 5, x1, 11], 180, 360, fill=back + (255,))
        d.polygon([(x1 - 1, 8), (15, 4), (15, 12)], fill=fin + (255,))
        d.polygon([(x0 + length // 2 - 2, 5), (x0 + length // 2 + 1, 2), (x0 + length // 2 + 2, 5)], fill=fin + (255,))
        d.point([(x0 + 2, 7)], fill=(10, 10, 10, 255))
        if stripes:
            for sx in range(x0 + 3, x1 - 1, 2):
                d.line([(sx, 5), (sx, 8)], fill=stripes + (255,))
        if spots:
            for _ in range(5):
                d.point([(rnd.randrange(x0 + 3, x1 - 1), rnd.randrange(6, 10))], fill=spots + (255,))
        if whiskers:
            d.line([(x0, 9), (x0 - 2, 12)], fill=(30, 30, 30, 255))
            d.line([(x0 + 1, 9), (x0, 12)], fill=(30, 30, 30, 255))
    return f


def items():
    def license_(d, img):
        d.rectangle([2, 3, 13, 12], fill=(236, 226, 196, 255), outline=(120, 100, 70, 255))
        d.rectangle([2, 3, 13, 5], fill=(46, 125, 50, 255))
        d.rectangle([4, 7, 7, 10], fill=(150, 98, 58, 255))
        d.line([(9, 8), (12, 8)], fill=(90, 80, 70, 255))
        d.line([(9, 10), (12, 10)], fill=(90, 80, 70, 255))
    icon('hunting_license', license_)
    icon('pelt_deer', pelt((150, 98, 58), (214, 186, 146)))
    icon('pelt_boar', pelt((80, 64, 52), (112, 96, 82)))
    icon('pelt_wolf', pelt((138, 138, 142), (206, 206, 210)))
    icon('pelt_fox', pelt((222, 116, 40), (240, 230, 220), (250, 250, 250)))
    icon('pelt_rabbit', pelt((168, 140, 110), (226, 214, 196)))

    def horn(d, img):
        pts = [(3, 13), (4, 9), (6, 6), (9, 4), (12, 3), (14, 4)]
        for i in range(len(pts) - 1):
            d.line([pts[i], pts[i + 1]], fill=(214, 200, 168, 255), width=3 - (i > 3))
        d.line([(3, 13), (4, 9), (6, 6)], fill=(160, 146, 118, 255))
        d.rectangle([2, 12, 5, 14], fill=(110, 82, 56, 255))
    icon('goat_trophy', horn)
    icon('venison', meat((168, 46, 40), (232, 210, 200)))
    icon('boar_meat', meat((186, 70, 66), (246, 230, 222)))
    icon('fish_perch', fish((90, 122, 52), (214, 200, 120), (220, 70, 40), 11, stripes=(40, 60, 30)))
    icon('fish_crucian', fish((150, 120, 50), (222, 190, 96), (170, 120, 50), 10))
    icon('fish_pike', fish((92, 112, 60), (200, 206, 160), (110, 120, 70), 14, spots=(214, 220, 150)))
    icon('fish_catfish', fish((54, 56, 58), (130, 130, 124), (60, 60, 60), 14, whiskers=True))
    icon('fish_salmon', fish((120, 132, 150), (240, 150, 140), (150, 120, 130), 12, spots=(40, 40, 50)))
    icon('fish_mackerel', fish((40, 110, 120), (220, 226, 230), (70, 90, 100), 11, stripes=(20, 50, 60)))
    icon('fish_tuna', fish((34, 52, 108), (200, 206, 214), (230, 196, 60), 13))
    icon('fish_gold', fish((255, 150, 20), (255, 210, 60), (255, 120, 20), 10))


# ------------------------------------------------------------------ подарок
def gift():
    tex = os.path.join(A, 'textures', 'block')
    os.makedirs(tex, exist_ok=True)
    for name, top in (('gift', False), ('gift_top', True)):
        img = Image.new('RGBA', (16, 16), (196, 30, 36, 255))
        px = img.load()
        for y in range(16):
            for x in range(16):
                k = 1 + rnd.uniform(-0.05, 0.05)
                px[x, y] = shade(px[x, y], k)
        d = ImageDraw.Draw(img)
        d.rectangle([7, 0, 8, 15], fill=(250, 206, 60, 255))
        if top:
            d.rectangle([0, 7, 15, 8], fill=(250, 206, 60, 255))
        img.save(os.path.join(tex, name + '.png'))
    el = [{'from': [3, 0, 3], 'to': [13, 10, 13], 'faces': {f: {'texture': '#top' if f in ('up', 'down') else '#side', 'uv': [3, 6, 13, 16] if f not in ('up', 'down') else [3, 3, 13, 13]}
                                                          for f in ('north', 'south', 'east', 'west', 'up', 'down')}},
          {'from': [5, 10, 7.5], 'to': [8, 12.5, 8.5], 'faces': {f: {'texture': '#bow'} for f in ('north', 'south', 'east', 'west', 'up', 'down')}},
          {'from': [8, 10, 7.5], 'to': [11, 12.5, 8.5], 'faces': {f: {'texture': '#bow'} for f in ('north', 'south', 'east', 'west', 'up', 'down')}}]
    save_json(os.path.join(A, 'models', 'block', 'gift.json'), {'parent': 'block/block', 'textures': {
        'side': 'novograd:block/gift', 'top': 'novograd:block/gift_top', 'bow': 'minecraft:block/gold_block', 'particle': 'novograd:block/gift'}, 'elements': el})
    save_json(os.path.join(A, 'blockstates', 'gift.json'), {'variants': {'': {'model': 'novograd:block/gift'}}})
    save_json(os.path.join(A, 'models', 'item', 'gift.json'), {'parent': 'novograd:block/gift'})


# ------------------------------------------------------------------ значки приложений
def app_icons():
    sys.path.insert(0, HERE)
    from gen_ui import vgrad
    out = os.path.join(A, 'textures', 'gui', 'ui', 'app')
    S = 128
    for app, col in (('skills', (40, 230, 255)), ('outdoor', (124, 179, 66))):
        tile = Image.new('RGBA', (S, S), (0, 0, 0, 0))
        m = Image.new('L', (S, S), 0)
        ImageDraw.Draw(m).rounded_rectangle([2, 2, S - 3, S - 3], radius=30, fill=255)
        bg = vgrad(S, S, [(0, tuple(int(v * 0.32 + 22) for v in col)), (1, (10, 7, 16))])
        tile.paste(bg, (0, 0), m)
        g = Image.new('RGBA', (S, S), (0, 0, 0, 0))
        d = ImageDraw.Draw(g)
        c = col + (255,)
        k = S / 64
        if app == 'skills':
            for i, h in enumerate((14, 24, 34)):
                x = 14 + i * 13
                d.rectangle([x * k, (50 - h) * k, (x + 9) * k, 50 * k], fill=c)
            d.line([(14 * k, 30 * k), (30 * k, 18 * k), (50 * k, 10 * k)], fill=(255, 255, 255, 255), width=int(4 * k))
            d.polygon([(52 * k, 6 * k), (52 * k, 16 * k), (43 * k, 9 * k)], fill=(255, 255, 255, 255))
        else:
            d.polygon([(22 * k, 8 * k), (8 * k, 34 * k), (36 * k, 34 * k)], fill=c)
            d.polygon([(22 * k, 18 * k), (5 * k, 48 * k), (39 * k, 48 * k)], fill=c)
            d.rectangle([19 * k, 48 * k, 25 * k, 56 * k], fill=(160, 110, 70, 255))
            d.ellipse([40 * k, 30 * k, 60 * k, 42 * k], fill=(120, 200, 255, 255))
            d.polygon([(58 * k, 36 * k), (64 * k, 30 * k), (64 * k, 42 * k)], fill=(120, 200, 255, 255))
            d.ellipse([44 * k, 8 * k, 56 * k, 20 * k], fill=(255, 196, 40, 255))
        g = g.resize((int(S * 0.72), int(S * 0.72)), Image.LANCZOS)
        gl = Image.new('RGBA', (S, S), (0, 0, 0, 0))
        gl.alpha_composite(g, (int(S * 0.14), int(S * 0.14)))
        tile.alpha_composite(gl.filter(ImageFilter.GaussianBlur(7)))
        tile.alpha_composite(gl)
        bd = ImageDraw.Draw(tile)
        bd.rounded_rectangle([2, 2, S - 3, S - 3], radius=30, outline=col + (150,), width=3)
        bd.arc([4, 4, 60, 60], 180, 270, fill=(255, 255, 255, 70), width=3)
        tile.resize((64, 64), Image.LANCZOS).save(os.path.join(out, app + '.png'))


# ------------------------------------------------------------------ что клюёт
def loot():
    lake = {'condition': 'minecraft:location_check', 'predicate': {'position': {'x': {'min': 1080, 'max': 1560}, 'z': {'min': 590, 'max': 930}}}}
    sea = {'condition': 'minecraft:inverted', 'term': lake}

    def e(name, w, cond):
        return {'type': 'minecraft:item', 'name': 'novograd:' + name, 'weight': w, 'conditions': [cond]}
    entries = [e('fish_crucian', 34, lake), e('fish_perch', 30, lake), e('fish_pike', 16, lake), e('fish_catfish', 5, lake), e('fish_salmon', 6, lake),
               e('fish_mackerel', 38, sea), e('fish_salmon', 26, sea), e('fish_tuna', 12, sea), e('fish_perch', 6, sea),
               {'type': 'minecraft:item', 'name': 'novograd:fish_gold', 'weight': 1}]
    save_json(os.path.join(RES, 'data', 'minecraft', 'loot_table', 'gameplay', 'fishing', 'fish.json'),
              {'type': 'minecraft:fishing', 'pools': [{'rolls': 1, 'entries': entries}]})


if __name__ == '__main__':
    atlas(DEER, os.path.join(A, 'textures', 'entity', 'wild', 'deer.png'), spots={8: (200, 170, 130)})
    atlas(BOAR, os.path.join(A, 'textures', 'entity', 'wild', 'boar.png'))
    save_json(os.path.join(A, 'geo', 'deer.geo.json'), deer())
    save_json(os.path.join(A, 'geo', 'boar.geo.json'), boar())
    save_json(os.path.join(A, 'animations', 'wild.animation.json'), anim())
    items()
    gift()
    app_icons()
    loot()
    print('ok')
