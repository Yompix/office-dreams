#!/usr/bin/env python3
"""Json-ресурсы «Глухомани»: модели и состояния блоков, модели предметов, переводы (ru/en/uk), sounds.json,
тип мира «Глухомань» и тёмный таёжный биом (папка data). Запуск: python3 tools/gen_resources.py
Нужны ванильные данные 1.21.1 (MC_DATA, по умолчанию /tmp/claude-0/mcdata) — для копии биома и пресета.
"""
import json, os

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, '..', 'src', 'main', 'resources')
A = os.path.join(ROOT, 'assets', 'glukhoman')
D = os.path.join(ROOT, 'data')
NS = 'glukhoman'


def w(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as f:
        json.dump(obj, f, ensure_ascii=False, indent=1)


def el(frm, to, tex='side', front=None, up=None):
    faces = {}
    for s in ('north', 'south', 'east', 'west', 'up', 'down'):
        t = tex
        if s == 'south' and front:
            t = front
        if s == 'up' and up:
            t = up
        faces[s] = {'texture': '#' + t}
    return {'from': frm, 'to': to, 'faces': faces}


# имя: элементы модели (лицом на юг)
PROPS = {
    'radio': [el([2, 0, 4], [14, 9, 12], 'side', 'front'), el([12, 9, 8], [13, 16, 9])],
    'tape_player': [el([2, 0, 3], [14, 6, 13], 'side', 'front', 'top')],
    'fuse_box': [el([2, 1, 0], [14, 15, 4], 'side', 'front')],
    'generator': [el([0, 0, 2], [16, 13, 14], 'side', 'front', 'top'), el([12, 13, 4], [14, 17, 6])],
    'amplifier': [el([1, 0, 1], [15, 16, 15], 'side', 'front')],
    'transmitter': [el([0, 0, 0], [16, 16, 16], 'side', 'front', 'top')],
    'note': [el([3, 0, 3], [13, 0.5, 13], 'top', None, 'top')],
    'chalk_mark': [el([0, 0, 0], [16, 16, 0.5], 'front', 'front')],
    'lock': [el([5, 4, 0], [11, 12, 4], 'side', 'front')],
    'crate': [el([1, 0, 1], [15, 12, 15], 'side', 'side', 'top')],
}
DECOR = {  # имя: (элементы, текстура зависит от варианта)
    'poster': ([el([1, 2, 0], [15, 15, 0.5], 'front', 'front')], True),
    'scratches': ([el([0, 0, 0], [16, 16, 0.3], 'front', 'front')], False),
    'doll': ([el([6, 0, 7], [10, 5, 9.5], 'side', 'front'), el([6.5, 5, 7], [9.5, 8.5, 9.5], 'top', 'front'), el([4.5, 3, 7.5], [6, 5, 8.5], 'side'), el([10, 3, 7.5], [11.5, 5, 8.5], 'side')], False),
    'photo': ([el([3, 4, 0], [13, 13, 1], 'side', 'front')], True),
}
ITEMS = ['flashlight', 'battery', 'oil_lamp', 'oil', 'pebble', 'fuse', 'gate_key', 'mine_key', 'canister', 'tuning_fork']

NAMES = {
    'radio': ('Рация', 'Radio', 'Рація'), 'tape_player': ('Магнитофон', 'Tape player', 'Магнітофон'),
    'fuse_box': ('Электрощит', 'Fuse box', 'Електрощит'), 'generator': ('Генератор', 'Generator', 'Генератор'),
    'amplifier': ('Усилитель «Тишина-3»', 'Amplifier', 'Підсилювач'), 'transmitter': ('Передатчик', 'Transmitter', 'Передавач'),
    'note': ('Записка', 'Note', 'Записка'), 'chalk_mark': ('Метка мелом', 'Chalk mark', 'Позначка крейдою'), 'lock': ('Замок', 'Lock', 'Замок'),
    'crate': ('Ящик', 'Crate', 'Скриня'),
    'poster': ('Плакат «Пропал человек»', 'Missing poster', 'Плакат «Зник чоловік»'), 'scratches': ('Царапины', 'Scratches', 'Подряпини'),
    'doll': ('Кукла', 'Doll', 'Лялька'), 'photo': ('Семейное фото', 'Family photo', 'Родинне фото'),
    'doppel': ('Двойник', 'Doppelganger', 'Двійник'), 'lamp': ('Лампочка', 'Light bulb', 'Лампочка'), 'street_lamp': ('Фонарь', 'Street lamp', 'Ліхтар'),
    'flashlight': ('Фонарик', 'Flashlight', 'Ліхтарик'), 'battery': ('Батарейка', 'Battery', 'Батарейка'),
    'oil_lamp': ('Керосиновая лампа', 'Oil lamp', 'Гасова лампа'), 'oil': ('Керосин', 'Lamp oil', 'Гас'),
    'pebble': ('Камешек', 'Pebble', 'Камінчик'), 'fuse': ('Предохранитель', 'Fuse', 'Запобіжник'),
    'gate_key': ('Ключ от ворот просеки', 'Gate key', 'Ключ від воріт просіки'), 'mine_key': ('Ключ от шахты', 'Mine key', 'Ключ від шахти'),
    'canister': ('Канистра', 'Fuel canister', 'Каністра'), 'tuning_fork': ('Камертон Шумова', "Shumov's tuning fork", 'Камертон Шумова'),
}
ENT = {'doppel': ('Двойник', 'Doppelganger', 'Двійник'), 'pole': ('Жердь', 'The Pole', 'Жердина'), 'listener': ('Слухач', 'Listener', 'Слухач'), 'choir': ('Хор', 'The Choir', 'Хор'),
       'anna': ('Анна Ветрова', 'Anna Vetrova', 'Анна Вєтрова'), 'pebble': ('Камешек', 'Pebble', 'Камінчик')}
SUBS = {'knock': ('Стук в дверь', 'Knocking', 'Стукіт у двері'), 'steps_far': ('Шаги в лесу', 'Footsteps in the woods', 'Кроки в лісі'),
        'steps_near': ('Шаги за спиной', 'Footsteps behind', 'Кроки позаду'), 'branch_snap': ('Хруст ветки', 'Branch snaps', 'Хрускіт гілки'),
        'listener_click': ('Слухач щёлкает', 'Listener clicks', 'Слухач клацає'), 'listener_scream': ('Слухач кричит', 'Listener shrieks', 'Слухач кричить'),
        'choir_roar': ('Хор ревёт', 'The Choir roars', 'Хор реве'), 'choir_hum': ('Хор гудит', 'The Choir hums', 'Хор гуде'),
        'pole_step': ('Скрип дерева', 'Wood creaks', 'Скрип дерева'), 'door_creak': ('Скрип двери', 'Door creaks', 'Рипіння дверей'),
        'distant_scream': ('Крик вдалеке', 'Distant scream', 'Крик вдалині'), 'heartbeat': ('Сердцебиение', 'Heartbeat', 'Серцебиття'),
        'whisper': ('Шёпот', 'Whisper', 'Шепіт'), 'radio_static': ('Треск эфира', 'Radio static', 'Тріск ефіру')}
SOUNDS = ["amb.forest", "amb.village", "amb.mine", "amb.station", "amb.bunker", "amb.dread", "amb.relief", "amb.chase",
          "heartbeat", "breath", "knock", "steps_far", "steps_near", "whisper", "radio_static", "radio_click", "stinger", "stinger_soft",
          "pole_appear", "pole_vanish", "pole_step", "listener_click", "listener_scream", "listener_hurt", "choir_roar", "choir_hum", "choir_hurt",
          "flashlight_click", "battery_dead", "fuse", "generator", "power_on", "lamp_buzz", "door_creak", "lift", "tape_click", "title_hit",
          "pebble", "branch_snap", "distant_scream", "wind_gust", "jumpscare", "breath_close", "scratch", "musicbox", "crash_buzz", "slowmo"]


def blocks():
    for n, els in PROPS.items():
        tex = {'side': f'{NS}:block/{n}', 'front': f'{NS}:block/{n}_front', 'top': f'{NS}:block/{n}_top', 'particle': f'{NS}:block/{n}'}
        w(f'{A}/models/block/{n}.json', {'textures': tex, 'elements': els})
        w(f'{A}/blockstates/{n}.json', {'variants': {f'facing={f}': {'model': f'{NS}:block/{n}', 'y': y} | ({'y': y} if y else {})
                                                     for f, y in (('south', 0), ('west', 90), ('north', 180), ('east', 270))}})
        w(f'{A}/models/item/{n}.json', {'parent': f'{NS}:block/{n}'})
    for n, els in (('lamp', [el([7.5, 12, 7.5], [8.5, 16, 8.5], 'side'), el([5, 10, 5], [11, 12, 11], 'side'), el([6, 7, 6], [10, 10, 10], 'bulb')]),
                   ('street_lamp', [el([3, 10, 3], [13, 14, 13], 'side'), el([5, 8, 5], [11, 10, 11], 'bulb'), el([7, 14, 7], [9, 16, 9], 'side')])):
        for lit in (True, False):
            m = f'{n}_on' if lit else n
            w(f'{A}/models/block/{m}.json', {'textures': {'side': f'{NS}:block/lamp_shade', 'bulb': f'{NS}:block/bulb_{"on" if lit else "off"}',
                                                           'particle': f'{NS}:block/lamp_shade'}, 'elements': els})
        w(f'{A}/blockstates/{n}.json', {'variants': {'lit=true': {'model': f'{NS}:block/{n}_on'}, 'lit=false': {'model': f'{NS}:block/{n}'}}})
        w(f'{A}/models/item/{n}.json', {'parent': f'{NS}:block/{n}_on'})
    for n, (els, per_variant) in DECOR.items():
        variants = {}
        for v in range(3):
            m = f'{n}_{v}' if per_variant else n
            tex = {'side': f'{NS}:block/{n}_side', 'top': f'{NS}:block/{n}_top', 'front': f'{NS}:block/{m}', 'particle': f'{NS}:block/{n}_side'}
            w(f'{A}/models/block/{m}.json', {'textures': tex, 'elements': els})
            for f, y in (('south', 0), ('west', 90), ('north', 180), ('east', 270)):
                variants[f'facing={f},variant={v}'] = {'model': f'{NS}:block/{m}', 'y': y}
        w(f'{A}/blockstates/{n}.json', {'variants': variants})
        w(f'{A}/models/item/{n}.json', {'parent': f'{NS}:block/{n}_0' if per_variant else f'{NS}:block/{n}'})
    for it in ITEMS:
        if it in ('flashlight', 'oil_lamp'):
            w(f'{A}/models/item/{it}.json', {'parent': 'minecraft:item/handheld', 'textures': {'layer0': f'{NS}:item/{it}'},
                'display': {'firstperson_righthand': {'rotation': [0, -90, 35], 'translation': [1.5, 3.5, -2], 'scale': [0.9, 0.9, 0.9]},
                            'firstperson_lefthand': {'rotation': [0, 90, -35], 'translation': [1.5, 3.5, -2], 'scale': [0.9, 0.9, 0.9]}}})
            continue
        w(f'{A}/models/item/{it}.json', {'parent': 'minecraft:item/handheld' if it in ('flashlight', 'oil_lamp', 'tuning_fork', 'gate_key', 'mine_key') else 'minecraft:item/generated',
                                          'textures': {'layer0': f'{NS}:item/{it}'}})


def lang():
    for i, code in enumerate(('ru_ru', 'en_us', 'uk_ua')):
        L = {'itemGroup.glukhoman': ('Глухомань', 'Glukhoman', 'Глухомань')[i],
             'key.categories.glukhoman': ('Глухомань', 'Glukhoman', 'Глухомань')[i],
             'key.glukhoman.flashlight': ('Фонарик вкл/выкл', 'Flashlight on/off', 'Ліхтарик увімк/вимк')[i],
             'key.glukhoman.hint': ('Подсказка: куда идти', 'Hint: where to go', 'Підказка: куди йти')[i],
             'generator.glukhoman.taiga': ('Глухомань', 'Glukhoman (story)', 'Глухомань')[i]}
        for n, v in NAMES.items():
            L[('item' if n in ITEMS else 'block') + f'.{NS}.{n}'] = v[i]
        for n, v in ENT.items():
            L[f'entity.{NS}.{n}'] = v[i]
        for n, v in SUBS.items():
            L[f'subtitles.{NS}.{n}'] = v[i]
        w(f'{A}/lang/{code}.json', L)


def sounds():
    story = json.load(open(os.path.join(ROOT, 'glukhoman_story.json'), encoding='utf-8'))
    S = {}
    for n in SOUNDS:
        path = n.replace('.', '/') if n.startswith('amb.') else n
        e = {'sounds': [{'name': f'{NS}:{path}', 'stream': n.startswith('amb.')}]}
        if n in SUBS:
            e['subtitle'] = f'subtitles.{NS}.{n}'
        S[n] = e
    for lid in story['lines']:
        S[f'voice.{lid}'] = {'sounds': [{'name': f'{NS}:voice/{lid}', 'stream': True}]}
    w(f'{A}/sounds.json', S)


def data():
    mc = os.environ.get('MC_DATA', '/tmp/claude-0/mcdata')
    biome = json.load(open(f'{mc}/data/minecraft/worldgen/biome/old_growth_spruce_taiga.json'))
    biome['features'] = [[] for _ in biome['features']]
    biome['spawners'] = {k: [] for k in biome['spawners']}
    biome['spawn_costs'] = {}
    biome['carvers'] = {}
    biome['temperature'] = 0.2
    biome['effects'] = {
        'fog_color': 0x2B2F35, 'sky_color': 0x3A4047, 'water_color': 0x2E3D42, 'water_fog_color': 0x0E1519,
        'grass_color': 0x4F5F3E, 'foliage_color': 0x3B4A33,
        'mood_sound': {'block_search_extent': 8, 'offset': 2.0, 'sound': 'minecraft:ambient.cave', 'tick_delay': 6000},
        'particle': {'options': {'type': 'minecraft:white_ash'}, 'probability': 0.004},
    }
    w(f'{D}/{NS}/worldgen/biome/taiga.json', biome)
    preset = json.load(open(f'{mc}/data/minecraft/worldgen/world_preset/flat.json'))
    preset['dimensions']['minecraft:overworld']['generator'] = {'type': f'{NS}:taiga', 'biome_source': {'type': 'minecraft:fixed', 'biome': f'{NS}:taiga'}}
    w(f'{D}/{NS}/worldgen/world_preset/taiga.json', preset)
    w(f'{D}/minecraft/tags/worldgen/world_preset/normal.json', {'replace': False, 'values': [f'{NS}:taiga']})


if __name__ == '__main__':
    blocks(); lang(); sounds(); data()
    print('ok')
