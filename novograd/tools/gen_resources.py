#!/usr/bin/env python3
"""Пишет json-ресурсы Novograd: модели и состояния блоков, модели предметов, переводы, звуки,
таблицы добычи, теги и тип мира «Новоград: Город». Запуск из папки novograd: python3 tools/gen_resources.py
"""
import copy, json, os, zipfile, glob

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources')
A = os.path.join(ROOT, 'assets', 'novograd')
D = os.path.join(ROOT, 'data')
NS = 'novograd'


def w(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as f:
        json.dump(obj, f, ensure_ascii=False, indent=2)


def t(name):
    return f'{NS}:block/{name}' if ':' not in name else name


def el(frm, to, tex, faces='all', extra=None):
    """Элемент модели. tex — текстура по умолчанию, extra — {сторона: текстура} для особых граней."""
    sides = ['north', 'south', 'east', 'west', 'up', 'down']
    f = {}
    for s in sides:
        f[s] = {'texture': '#' + (extra or {}).get(s, tex)}
    return {'from': frm, 'to': to, 'faces': f}


def model(name, textures, elements, parent='block/block', particle=None):
    m = {'parent': parent, 'textures': dict(textures), 'elements': elements}
    m['textures']['particle'] = m['textures'].get(particle or next(iter(textures)))
    w(f'{A}/models/block/{name}.json', m)


FACING_Y = {'north': 0, 'east': 90, 'south': 180, 'west': 270}


def facing_state(name, model_for=lambda extra: None, props=None):
    """Состояния для блока с facing (+ другие свойства)."""
    variants = {}
    props = props or {}
    keys = list(props.keys())

    def rec(i, cur):
        if i == len(keys):
            for f, y in FACING_Y.items():
                k = ','.join([f'facing={f}'] + [f'{kk}={cur[kk]}' for kk in keys])
                v = {'model': f'{NS}:block/' + (model_for(cur) or name)}
                if y:
                    v['y'] = y
                variants[k] = v
            return
        for val in props[keys[i]]:
            rec(i + 1, {**cur, keys[i]: val})

    rec(0, {})
    w(f'{A}/blockstates/{name}.json', {'variants': variants})


def simple_state(name):
    w(f'{A}/blockstates/{name}.json', {'variants': {'': {'model': f'{NS}:block/{name}'}}})


def block_item(name, model_name=None):
    w(f'{A}/models/item/{name}.json', {'parent': f'{NS}:block/{model_name or name}'})


def gen_item(name, tex=None):
    w(f'{A}/models/item/{name}.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{NS}:item/{tex or name}'}})


# ------------------------------------------------------------------ блоки
def blocks():
    for n in ('asphalt', 'sidewalk'):
        w(f'{A}/models/block/{n}.json', {'parent': 'minecraft:block/cube_all', 'textures': {'all': t(n)}})
        simple_state(n)
        block_item(n)

    for n in ('asphalt_line', 'asphalt_edge', 'crosswalk'):
        w(f'{A}/models/block/{n}.json', {'parent': 'minecraft:block/cube_bottom_top',
                                         'textures': {'top': t(n), 'bottom': t('asphalt'), 'side': t('asphalt')}})
        facing_state(n)
        block_item(n)

    model('chalkboard', {'front': t('chalkboard'), 'back': t('chalkboard_back')},
          [el([0, 0, 13], [16, 16, 16], 'back', extra={'north': 'front'})])
    facing_state('chalkboard')
    block_item('chalkboard')

    model('school_desk', {'top': t('desk_top'), 'leg': t('desk_leg')}, [
        el([1, 10, 0], [15, 12, 9], 'top'),
        el([2, 0, 1], [3, 10, 2], 'leg'), el([13, 0, 1], [14, 10, 2], 'leg'),
        el([2, 0, 7], [3, 10, 8], 'leg'), el([13, 0, 7], [14, 10, 8], 'leg'),
        el([3, 6, 10], [13, 7, 15], 'top'),
        el([3, 0, 10], [4, 6, 11], 'leg'), el([12, 0, 10], [13, 6, 11], 'leg'),
        el([3, 0, 14], [4, 6, 15], 'leg'), el([12, 0, 14], [13, 6, 15], 'leg'),
        el([3, 7, 15], [13, 13, 16], 'top')], particle='top')
    facing_state('school_desk')
    block_item('school_desk')

    channels = ['tv_off', 'tv_news', 'tv_cartoon', 'tv_nature', 'tv_music', 'tv_sport']
    for i, ch in enumerate(channels):
        model(f'tv_{i}', {'body': t('tv_body'), 'screen': t(ch)}, [
            el([5, 0, 6], [11, 1, 10], 'body'),
            el([7, 1, 7.5], [9, 3, 8.5], 'body'),
            el([0, 3, 7], [16, 14, 9], 'body'),
            {'from': [1, 4, 6.9], 'to': [15, 13, 7], 'faces': {'north': {'texture': '#screen', 'uv': [0, 0, 16, 16]}}}],
            particle='body')
    facing_state('tv', lambda c: f"tv_{c['channel']}", {'channel': list(range(6))})
    block_item('tv', 'tv_1')

    for i, st in enumerate(['red', 'yellow', 'green']):
        model(f'traffic_light_{st}', {'face': t(f'traffic_light_{st}'), 'side': t('traffic_light_side')}, [
            el([4, 0, 5], [12, 16, 11], 'side', extra={'north': 'face'}),
            el([4, 15, 3], [12, 16, 5], 'side'),
            el([4, 10, 3], [12, 10.5, 5], 'side'),
            el([4, 5, 3], [12, 5.5, 5], 'side')], particle='side')
    facing_state('traffic_light', lambda c: ['traffic_light_red', 'traffic_light_yellow', 'traffic_light_green'][c['light']], {'light': [0, 1, 2]})
    block_item('traffic_light', 'traffic_light_green')

    model('lamp_post', {'pole': t('lamp_pole')}, [el([6, 0, 6], [10, 16, 10], 'pole')])
    facing_state('lamp_post')
    block_item('lamp_post')
    model('street_lamp', {'pole': t('lamp_pole'), 'light': t('lamp_light')}, [
        el([6, 0, 6], [10, 16, 10], 'pole'),
        el([7, 13, 0], [9, 15, 6], 'pole'),
        el([4, 12, -6], [12, 14, 2], 'pole', extra={'down': 'light'})], particle='pole')
    facing_state('street_lamp')
    block_item('street_lamp')

    model('atm', {'front': t('atm_front'), 'side': t('atm_side')}, [el([1, 0, 2], [15, 16, 16], 'side', extra={'north': 'front'})])
    facing_state('atm')
    block_item('atm')

    model('job_board', {'board': t('job_board'), 'wood': 'minecraft:block/spruce_planks'}, [
        el([1, 0, 13], [3, 16, 15], 'wood'), el([13, 0, 13], [15, 16, 15], 'wood'),
        el([0, 3, 12], [16, 16, 13], 'wood', extra={'north': 'board'})], particle='wood')
    facing_state('job_board')
    block_item('job_board')

    model('mailbox', {'front': t('mailbox_front'), 'side': t('mailbox_side'), 'pole': t('lamp_pole'), 'flag': 'minecraft:block/red_concrete'}, [
        el([7, 0, 7], [9, 7, 9], 'pole'),
        el([4, 7, 3], [12, 15, 13], 'side', extra={'north': 'front'}),
        el([12, 11, 9], [13, 15, 10], 'flag')], particle='side')
    facing_state('mailbox')
    block_item('mailbox')

    w(f'{A}/models/block/shop_counter.json', {'parent': 'minecraft:block/orientable_with_bottom', 'textures': {
        'front': t('shop_counter'), 'side': t('shop_counter'), 'top': t('shop_counter_top'), 'bottom': t('shop_counter_top')}})
    facing_state('shop_counter')
    block_item('shop_counter')

    model('computer', {'body': t('computer_body'), 'screen': t('computer_screen')}, [
        el([2, 0, 9], [14, 1, 13], 'body'),
        el([7, 1, 10], [9, 4, 12], 'body'),
        el([1, 4, 9], [15, 14, 11], 'body'),
        {'from': [2, 5, 8.9], 'to': [14, 13, 9], 'faces': {'north': {'texture': '#screen', 'uv': [0, 0, 16, 16]}}},
        el([3, 0, 2], [13, 1, 7], 'body')], particle='body')
    facing_state('computer')
    block_item('computer')

    model('slot_machine', {'front': t('slot_machine_front'), 'side': t('slot_machine_side'), 'gold': 'minecraft:block/gold_block'}, [
        el([1, 0, 2], [15, 15, 15], 'side', extra={'north': 'front'}),
        el([0, 15, 1], [16, 16, 16], 'gold'),
        el([15, 7, 7], [16, 13, 8], 'gold')], particle='side')
    facing_state('slot_machine')
    block_item('slot_machine')

    colors = ['pink', 'purple', 'cyan', 'blue', 'yellow', 'green', 'red', 'orange']
    for c in colors + ['off']:
        model(f'neon_{c}', {'t': t(f'neon_{c}')}, [el([0, 5.5, 13.5], [16, 10.5, 16], 't')])
        model(f'neon_floor_{c}', {'t': t(f'neon_{c}')}, [el([0, 0, 5], [16, 1.2, 11], 't')])
    facing_state('neon', lambda st: f"neon_{st['color'] if st['lit'] == 'true' else 'off'}", {'color': colors, 'lit': ['true', 'false']})
    facing_state('neon_floor', lambda st: f"neon_floor_{st['color'] if st['lit'] == 'true' else 'off'}", {'color': colors, 'lit': ['true', 'false']})
    block_item('neon', 'neon_pink')
    block_item('neon_floor', 'neon_floor_cyan')

    model('clue', {'paper': 'minecraft:block/white_wool', 'mark': 'minecraft:block/gold_block'}, [
        el([3, 0, 3], [13, 1, 13], 'paper'), el([5, 1, 5], [11, 2.5, 11], 'mark')])
    simple_state('clue')
    block_item('clue')

    model('roulette', {'top': t('roulette_top'), 'side': t('roulette_side'), 'leg': 'minecraft:block/dark_oak_log'}, [
        {'from': [0, 10, 0], 'to': [16, 13, 16], 'faces': {
            'up': {'texture': '#top', 'uv': [0, 0, 16, 16]}, 'down': {'texture': '#side'},
            'north': {'texture': '#side'}, 'south': {'texture': '#side'}, 'east': {'texture': '#side'}, 'west': {'texture': '#side'}}},
        el([1, 0, 1], [3, 10, 3], 'leg'), el([13, 0, 1], [15, 10, 3], 'leg'),
        el([1, 0, 13], [3, 10, 15], 'leg'), el([13, 0, 13], [15, 10, 15], 'leg')], particle='side')
    facing_state('roulette')
    block_item('roulette')

    V = 'minecraft:block/'
    model('treadmill', {'belt': V + 'black_concrete', 'frame': V + 'iron_block', 'panel': V + 'gray_concrete', 'screen': V + 'light_blue_concrete'}, [
        el([2, 0, 1], [14, 2, 15], 'frame'), el([3, 2, 1], [13, 2.5, 15], 'belt'),
        el([2, 2, 2], [3, 13, 3], 'frame'), el([13, 2, 2], [14, 13, 3], 'frame'),
        el([2, 11, 1], [14, 14, 4], 'panel', extra={'up': 'screen'})], particle='frame')
    facing_state('treadmill')
    block_item('treadmill')
    model('barbell', {'bench': V + 'black_wool', 'metal': V + 'iron_block', 'plate': V + 'black_concrete'}, [
        el([5, 0, 6], [11, 5, 10], 'metal'), el([4, 5, 3], [12, 7, 13], 'bench'),
        el([2, 7, 11], [3, 13, 12], 'metal'), el([13, 7, 11], [14, 13, 12], 'metal'),
        el([0, 13, 11], [16, 14, 12], 'metal'), el([0, 11, 10], [2, 16, 13], 'plate'), el([14, 11, 10], [16, 16, 13], 'plate')], particle='metal')
    facing_state('barbell')
    block_item('barbell')
    model('scales', {'base': V + 'white_concrete', 'metal': V + 'iron_block', 'screen': V + 'lime_concrete'}, [
        el([3, 0, 3], [13, 1.5, 13], 'base'), el([7, 1.5, 3], [9, 13, 5], 'metal'),
        el([5, 13, 2.5], [11, 16, 5], 'metal', extra={'north': 'screen'})], particle='base')
    facing_state('scales')
    block_item('scales')
    model('dj_deck', {'body': V + 'black_concrete', 'deck': V + 'jukebox_top', 'neon': V + 'magenta_concrete', 'knob': V + 'light_blue_concrete'}, [
        el([0, 0, 3], [16, 11, 13], 'body', extra={'north': 'neon'}),
        el([1, 11, 4], [7, 12, 10], 'deck'), el([9, 11, 4], [15, 12, 10], 'deck'),
        el([7.5, 11, 9], [8.5, 13, 11], 'knob'), el([3, 11, 11], [13, 14, 12.5], 'body')], particle='body')
    facing_state('dj_deck')
    block_item('dj_deck')
    model('pizza_oven', {'brick': V + 'bricks', 'mouth': V + 'black_concrete', 'fire': V + 'magma', 'top': V + 'polished_andesite'}, [
        el([0, 0, 0], [16, 12, 16], 'brick'), el([3, 2, -0.1], [13, 9, 0.1], 'mouth', faces=['north']),
        el([4, 2.2, 0.2], [12, 4, 6], 'fire'), el([2, 12, 2], [14, 14, 14], 'brick'), el([6, 14, 6], [10, 16, 10], 'top')], particle='brick')
    facing_state('pizza_oven')
    block_item('pizza_oven')


BLOCKS = {
    'asphalt': ('Асфальт', 'Asphalt', 'pickaxe'), 'asphalt_line': ('Асфальт с разметкой', 'Asphalt with line', 'pickaxe'),
    'asphalt_edge': ('Асфальт с краевой линией', 'Asphalt edge', 'pickaxe'), 'crosswalk': ('Пешеходный переход', 'Crosswalk', 'pickaxe'),
    'sidewalk': ('Тротуарная плитка', 'Sidewalk', 'pickaxe'), 'chalkboard': ('Школьная доска', 'Chalkboard', 'axe'),
    'school_desk': ('Парта', 'School desk', 'axe'), 'tv': ('Телевизор', 'TV', 'pickaxe'),
    'traffic_light': ('Светофор', 'Traffic light', 'pickaxe'), 'lamp_post': ('Столб', 'Lamp post', 'pickaxe'),
    'street_lamp': ('Уличный фонарь', 'Street lamp', 'pickaxe'), 'atm': ('Банкомат', 'ATM', 'pickaxe'),
    'job_board': ('Доска вакансий', 'Job board', 'axe'), 'mailbox': ('Почтовый ящик', 'Mailbox', 'pickaxe'),
    'shop_counter': ('Прилавок с кассой', 'Shop counter', 'axe'), 'computer': ('Компьютер', 'Computer', 'pickaxe'),
    'slot_machine': ('Игровой автомат', 'Slot machine', 'pickaxe'), 'roulette': ('Стол рулетки', 'Roulette table', 'axe'),
    'treadmill': ('Беговая дорожка', 'Treadmill', 'pickaxe'), 'barbell': ('Скамья со штангой', 'Barbell bench', 'pickaxe'),
    'scales': ('Весы', 'Scales', 'pickaxe'), 'dj_deck': ('Диджейский пульт', 'DJ deck', 'pickaxe'), 'pizza_oven': ('Печь для пиццы', 'Pizza oven', 'pickaxe'),
    'clue': ('Улика', 'Clue', 'pickaxe'), 'neon': ('Неоновая трубка', 'Neon tube', 'pickaxe'), 'neon_floor': ('Неоновая полоса', 'Neon strip', 'pickaxe'),
}
ITEMS = {
    'phone': ('Телефон', 'Phone'), 'banknote': ('Купюра $10', '$10 banknote'), 'banknote_50': ('Купюра $50', '$50 banknote'), 'banknote_100': ('Купюра $100', '$100 banknote'), 'bank_card': ('Банковская карта', 'Bank card'),
    'parcel': ('Посылка', 'Parcel'), 'scooter': ('Электроскутер', 'E-scooter'), 'suit': ('Костюм для выпускного', 'Prom suit'), 'diploma': ('Диплом', 'Diploma'), 'textbook': ('Учебник', 'Textbook'),
    'burger': ('Бургер', 'Burger'), 'cola': ('Кола', 'Cola'), 'pizza': ('Пицца', 'Pizza'), 'pistol': ('Пистолет', 'Pistol'), 'smg': ('Автомат', 'SMG'), 'ammo': ('Патроны', 'Ammo'), 'bat': ('Бита', 'Baseball bat'), 'car_police': ('Полицейская машина', 'Police car'), 'salad': ('Салат', 'Salad'),
    'car_red': ('Машина (красная)', 'Car (red)'), 'car_blue': ('Машина (синяя)', 'Car (blue)'), 'car_yellow': ('Машина (жёлтая)', 'Car (yellow)'),
    'car_black': ('Машина (чёрная)', 'Car (black)'), 'car_white': ('Машина (белая)', 'Car (white)'), 'car_taxi': ('Такси', 'Taxi'),
}
SOUNDS = {
    'school_bell': ('Школьный звонок', 'School bell'), 'phone_notify': ('Сообщение на телефон', 'Phone notification'),
    'phone_open': ('Телефон открыт', 'Phone opens'), 'phone_click': ('Нажатие', 'Click'), 'car_horn': ('Гудок', 'Car horn'),
    'car_engine': ('Мотор', 'Car engine'), 'car_start': ('Заводится мотор', 'Engine starts'), 'cash': ('Касса', 'Cash register'),
    'answer_right': ('Верный ответ', 'Right answer'), 'answer_wrong': ('Ошибка', 'Wrong answer'), 'tv_switch': ('Переключение канала', 'TV switch'),
    'level_up': ('Фанфары', 'Fanfare'), 'traffic_beep': ('Сигнал светофора', 'Traffic beep'),
}


def items():
    for n in ITEMS:
        gen_item(n)


def lang():
    ru, en = {}, {}
    for n, (r, e, _) in BLOCKS.items():
        ru[f'block.{NS}.{n}'] = r
        en[f'block.{NS}.{n}'] = e
    for n, (r, e) in ITEMS.items():
        ru[f'item.{NS}.{n}'] = r
        en[f'item.{NS}.{n}'] = e
    for n, (r, e) in SOUNDS.items():
        ru[f'subtitles.{NS}.{n}'] = r
        en[f'subtitles.{NS}.{n}'] = e
    extra = {
        f'entity.{NS}.car': ('Машина', 'Car'), f'entity.{NS}.scooter': ('Электроскутер', 'E-scooter'), f'entity.{NS}.ball': ('Мяч', 'Ball'), f'entity.{NS}.citizen': ('Житель', 'Citizen'), f'entity.{NS}.seat': ('Сиденье', 'Seat'),
        f'itemGroup.{NS}': ('Новоград — тёмные улицы', 'Novograd: Dark Streets'), 'key.novograd.phone': ('Телефон', 'Phone'), 'key.novograd.horn': ('Гудок', 'Car horn'), 'key.novograd.lights': ('Фары вкл/выкл', 'Headlights'), 'key.novograd.nitro': ('Нитро', 'Nitro'), 'key.novograd.left': ('Левый поворотник', 'Left turn signal'), 'key.novograd.right': ('Правый поворотник', 'Right turn signal'), 'key.novograd.hazard': ('Аварийка', 'Hazard lights'), 'key.novograd.map': ('Карта и задания', 'Map and quests'), 'key.novograd.radio': ('Радио в машине', 'Car radio'), 'key.novograd.grade': ('Графика: красивая / лёгкая / выкл', 'Graphics: pretty / light / off'),
        'category.novograd': ('Новоград', 'Novograd'), 'generator.novograd.city': ('Новоград: Город', 'Novograd: City'),
    }
    for k, (r, e) in extra.items():
        ru[k] = r
        en[k] = e
    w(f'{A}/lang/ru_ru.json', ru)
    w(f'{A}/lang/uk_ua.json', ru)
    w(f'{A}/lang/en_us.json', en)


def sounds():
    out = {}
    for n in SOUNDS:
        entry = {'sounds': [{'name': f'{NS}:{n}', 'stream': n == 'car_engine'}], 'subtitle': f'subtitles.{NS}.{n}'}
        out[n] = entry
    # озвучка сюжета: voice.<id> → sounds/voice/<id>.ogg (файлы делает tools/voice.py)
    vl = json.load(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), 'voice_lines.json'), encoding='utf-8'))
    for line in vl + [{'id': 'yompix'}]:
        out['voice.' + line['id']] = {'sounds': [{'name': f"{NS}:voice/{line['id']}", 'stream': True}]}
    out['yompix_jingle'] = {'sounds': [{'name': f'{NS}:yompix_jingle', 'stream': True}]}
    out['yompix_intro'] = {'sounds': [{'name': f'{NS}:yompix_intro', 'stream': True}]}
    for snd in ('gunshot', 'smg_shot', 'siren'):
        out[snd] = {'sounds': [{'name': f'{NS}:{snd}'}]}
    w(f'{A}/sounds.json', out)


def data():
    for n, (_, _, tool) in BLOCKS.items():
        w(f'{D}/{NS}/loot_table/blocks/{n}.json', {'type': 'minecraft:block', 'pools': [{
            'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': f'{NS}:{n}'}], 'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
    for tool in ('pickaxe', 'axe'):
        w(f'{D}/minecraft/tags/block/mineable/{tool}.json', {'replace': False, 'values': [f'{NS}:{n}' for n, v in BLOCKS.items() if v[2] == tool]})

    # биом «Город»: как равнина, но без монстров и без случайных построек
    mc = os.environ.get('MC_DATA', '/tmp/claude-0/mcdata')
    with open(f'{mc}/data/minecraft/worldgen/biome/plains.json') as f:
        biome = json.load(f)
    biome['features'] = [[] for _ in biome['features']]
    biome['spawners'] = {k: [] for k in biome['spawners']}
    biome['spawn_costs'] = {}
    biome['carvers'] = {}
    w(f'{D}/{NS}/worldgen/biome/city.json', biome)
    with open(f'{mc}/data/minecraft/worldgen/world_preset/flat.json') as f:
        preset = json.load(f)
    gen = preset['dimensions']['minecraft:overworld']['generator']['settings']
    gen['biome'] = f'{NS}:city'
    gen['layers'] = [{'block': 'minecraft:bedrock', 'height': 1}, {'block': 'minecraft:stone', 'height': 3},
                     {'block': 'minecraft:dirt', 'height': 3}, {'block': 'minecraft:grass_block', 'height': 1}]
    gen['structure_overrides'] = []
    gen['features'] = False
    gen['lakes'] = False
    w(f'{D}/{NS}/worldgen/world_preset/city.json', preset)
    w(f'{D}/minecraft/tags/worldgen/world_preset/normal.json', {'replace': False, 'values': [f'{NS}:city']})


if __name__ == '__main__':
    blocks()
    items()
    lang()
    sounds()
    data()
    print('ok')
