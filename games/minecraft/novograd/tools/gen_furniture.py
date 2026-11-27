#!/usr/bin/env python3
"""0.9.10: мебель для квартиры — 20 объёмных предметов (блоки novograd:f_*), поворот по сторонам света.
Модели из ванильных текстур (без новых картинок — архив не растёт): дерево, шерсть, кварц, железо, стекло.
Пишет models/block, models/item, blockstates и названия в lang. Список (id, название, цена, сидеть, коробки) —
тот же, что в world/Furniture.java (FURN).

Запуск из папки novograd: python3 tools/gen_furniture.py
"""
import json, os

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'assets', 'novograd')
T = {'oak': 'minecraft:block/oak_planks', 'dark': 'minecraft:block/dark_oak_planks', 'spruce': 'minecraft:block/spruce_planks',
     'wool': 'minecraft:block/light_gray_wool', 'wool2': 'minecraft:block/gray_wool', 'red': 'minecraft:block/red_wool', 'white': 'minecraft:block/white_wool',
     'blue': 'minecraft:block/blue_wool', 'quartz': 'minecraft:block/quartz_block_side', 'iron': 'minecraft:block/iron_block', 'black': 'minecraft:block/black_concrete',
     'glass': 'minecraft:block/light_blue_stained_glass', 'water': 'minecraft:block/water_still', 'leaves': 'minecraft:block/azalea_leaves',
     'pot': 'minecraft:block/terracotta', 'lamp': 'minecraft:block/shroomlight', 'book': 'minecraft:block/bookshelf', 'smooth': 'minecraft:block/smooth_stone',
     'gold': 'minecraft:block/gold_block', 'brick': 'minecraft:block/bricks'}

# коробки: (from, to, текстура); лицо — север (к игроку, который ставил)
FURN = {
    'sofa': ('Диван', [([0, 0, 2], [16, 3, 14], 'dark'), ([0, 3, 2], [16, 7, 13], 'wool'), ([0, 7, 10], [16, 15, 14], 'wool'), ([0, 3, 2], [2, 10, 14], 'wool2'), ([14, 3, 2], [16, 10, 14], 'wool2')]),
    'armchair': ('Кресло', [([2, 0, 2], [14, 3, 14], 'dark'), ([2, 3, 2], [14, 7, 13], 'red'), ([2, 7, 10], [14, 15, 14], 'red'), ([1, 3, 2], [3, 11, 14], 'red'), ([13, 3, 2], [15, 11, 14], 'red')]),
    'chair': ('Стул', [([3, 0, 3], [4, 7, 4], 'oak'), ([12, 0, 3], [13, 7, 4], 'oak'), ([3, 0, 12], [4, 7, 13], 'oak'), ([12, 0, 12], [13, 7, 13], 'oak'),
                       ([3, 7, 3], [13, 8, 13], 'oak'), ([3, 8, 12], [13, 18, 13], 'oak')]),
    'coffee_table': ('Журнальный столик', [([1, 0, 3], [2, 5, 4], 'dark'), ([14, 0, 3], [15, 5, 4], 'dark'), ([1, 0, 12], [2, 5, 13], 'dark'), ([14, 0, 12], [15, 5, 13], 'dark'),
                                           ([0, 5, 2], [16, 6, 14], 'glass')]),
    'dining_table': ('Обеденный стол', [([1, 0, 1], [3, 14, 3], 'spruce'), ([13, 0, 1], [15, 14, 3], 'spruce'), ([1, 0, 13], [3, 14, 15], 'spruce'), ([13, 0, 13], [15, 14, 15], 'spruce'),
                                        ([0, 14, 0], [16, 16, 16], 'spruce')]),
    'bookshelf': ('Книжный шкаф', [([0, 0, 8], [16, 16, 16], 'book'), ([0, 16, 8], [16, 24, 16], 'book'), ([0, 24, 8], [16, 25, 16], 'dark')]),
    'floor_lamp': ('Торшер', [([5, 0, 5], [11, 1, 11], 'iron'), ([7, 1, 7], [9, 22, 9], 'iron'), ([3, 22, 3], [13, 28, 13], 'lamp')]),
    'table_lamp': ('Настольная лампа', [([6, 0, 6], [10, 1, 10], 'gold'), ([7, 1, 7], [9, 6, 9], 'gold'), ([4, 6, 4], [12, 11, 12], 'lamp')]),
    'plant': ('Большое растение', [([4, 0, 4], [12, 8, 12], 'pot'), ([2, 8, 2], [14, 22, 14], 'leaves')]),
    'rug': ('Ковёр', [([-4, 0, -4], [20, 0.5, 20], 'red')]),
    'aquarium': ('Аквариум', [([0, 0, 4], [16, 8, 12], 'dark'), ([0, 8, 4], [16, 18, 12], 'glass'), ([1, 9, 5], [15, 16, 11], 'water'), ([0, 18, 4], [16, 19, 12], 'black')]),
    'wardrobe': ('Шкаф', [([0, 0, 6], [16, 30, 16], 'spruce'), ([7.5, 8, 5.9], [8.5, 22, 6], 'iron')]),
    'bed': ('Кровать', [([0, 0, -8], [16, 3, 16], 'dark'), ([0, 3, -8], [16, 7, 16], 'white'), ([0, 7, 10], [16, 9, 16], 'white'), ([0, 7, -8], [16, 7.5, 8], 'blue'),
                        ([0, 0, 14], [16, 14, 16], 'dark')]),
    'kitchen': ('Кухонная тумба', [([0, 0, 2], [16, 14, 16], 'white'), ([0, 14, 0], [16, 16, 16], 'smooth'), ([3, 7, 1.9], [13, 8, 2], 'iron')]),
    'fridge': ('Холодильник', [([1, 0, 2], [15, 30, 16], 'white'), ([12, 12, 1.9], [13, 26, 2], 'iron'), ([1, 18, 1.95], [15, 18.5, 2], 'iron')]),
    'desk': ('Письменный стол', [([0, 0, 4], [2, 14, 16], 'oak'), ([14, 0, 4], [16, 14, 16], 'oak'), ([0, 14, 4], [16, 16, 16], 'oak'), ([2, 8, 15], [14, 14, 16], 'oak')]),
    'nightstand': ('Тумбочка', [([3, 0, 4], [13, 10, 14], 'dark'), ([7, 6, 3.9], [9, 7, 4], 'gold')]),
    'speaker': ('Колонка', [([3, 0, 4], [13, 24, 13], 'black'), ([5, 14, 3.9], [11, 20, 4], 'iron'), ([6, 4, 3.9], [10, 9, 4], 'iron')]),
    'tv_stand': ('Тумба под ТВ', [([0, 0, 4], [16, 7, 14], 'dark'), ([2, 7, 8], [14, 16, 10], 'black'), ([1, 16, 8], [15, 17, 10], 'black')]),
    'fireplace': ('Камин', [([0, 0, 8], [16, 16, 16], 'brick'), ([3, 0, 7], [13, 9, 8], 'black'), ([-1, 16, 7], [17, 17, 16], 'smooth')]),
}


def span(a, b):
    """Отрезок модели -> отрезок текстуры 0..16 (длинные детали повторяют рисунок, не растягивают)."""
    n = min(16.0, b - a)
    s0 = a % 16
    if s0 + n > 16:
        s0 = 16 - n
    return round(s0, 3), round(s0 + n, 3)


def model(name, boxes):
    els = []
    for fr, to, tex in boxes:
        f = {}
        x0, y0, z0 = fr
        x1, y1, z1 = to
        for side in ('north', 'south', 'east', 'west', 'up', 'down'):
            if side in ('north', 'south'):
                u, v = span(x0, x1), span(16 - y1, 16 - y0)
            elif side in ('east', 'west'):
                u, v = span(z0, z1), span(16 - y1, 16 - y0)
            else:
                u, v = span(x0, x1), span(z0, z1)
            f[side] = {'texture': '#' + tex, 'uv': [u[0], v[0], u[1], v[1]]}
        els.append({'from': fr, 'to': to, 'faces': f})
    used = sorted({b[2] for b in boxes})
    textures = {k: T[k] for k in used}
    textures['particle'] = T[used[0]]
    return {'parent': 'block/block', 'textures': textures, 'elements': els,
            'display': {'gui': {'rotation': [30, 225, 0], 'scale': [0.5, 0.5, 0.5]}, 'fixed': {'scale': [0.5, 0.5, 0.5]}, 'ground': {'scale': [0.25, 0.25, 0.25]}}}


def main():
    langs = {}
    for k, (title, boxes) in FURN.items():
        bid = 'f_' + k
        with open(os.path.join(ROOT, 'models', 'block', bid + '.json'), 'w') as f:
            json.dump(model(k, boxes), f, indent=1)
        with open(os.path.join(ROOT, 'models', 'item', bid + '.json'), 'w') as f:
            json.dump({'parent': 'novograd:block/' + bid}, f)
        v = {}
        for face, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270)):
            d = {'model': 'novograd:block/' + bid}
            if y:
                d['y'] = y
            v['facing=' + face] = d
        with open(os.path.join(ROOT, 'blockstates', bid + '.json'), 'w') as f:
            json.dump({'variants': v}, f, indent=1)
        langs['block.novograd.' + bid] = title
    for lang in ('ru_ru', 'uk_ua', 'en_us'):
        p = os.path.join(ROOT, 'lang', lang + '.json')
        d = json.load(open(p, encoding='utf-8'))
        for k, v in langs.items():
            d.setdefault(k, v)  # переводы en/uk правятся руками и не затираются
        open(p, 'w', encoding='utf-8').write(json.dumps(d, ensure_ascii=False, indent=2) + '\n')
    print(f'мебель: {len(FURN)} предметов')


if __name__ == '__main__':
    main()
