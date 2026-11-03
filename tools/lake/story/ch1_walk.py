"""WHAT THE LAKE KEEPS, глава 1 — дополнения к 2–3 часам (текст — документ для редактора
«WHAT THE LAKE KEEPS — дополнения сюжета (на редактуру)», правки редактора переносить сюда):
  * прогулка по заправке «24» (сцена 5, после «Мама сказала ждать…»): игрок сам ведёт Ноя, 10 точек осмотра;
  * разговор в дороге после заправки (s7_talk: после ответа Айрис в сцене 7, до указателя «Север — 640»);
  * условные строки в утверждённых сценах: «На его руки» (отмыл руки), имя Дэнни (Ной спросил сам).
Правит godot/lake/data/ch1.json на месте (повторный запуск ничего не дублирует):
    python3 tools/lake/story/ch1_walk.py
"""
import json, os, sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
DATA = os.path.join(ROOT, 'godot/lake/data/ch1.json')
F = 0.38   # пол магазина и крыльцо


def N(ru, en):
    return {'ru': ru, 'en': en}


def L(ru, en):
    return {'ru': ru, 'en': en}


def say(who, ru, en, **kw):
    b = {'say': who, 'ru': ru, 'en': en}
    b.update(kw)
    return b


def note(ru, en, dur=2.6, **kw):
    b = {'note': N(ru, en), 'dur': dur}
    b.update(kw)
    return b


SPOTS = [
    {'id': 'bucket', 'at': [12.45, 0.75, -8.55], 'where': [13.0, -8.5], 'r': 1.25, 'stand': [13.0, -8.5], 'face': -90,
     'label': L('Отмыть руки', 'Wash your hands'),
     'cam': [[13.15, 1.8, -11.1], [12.7, 0.85, -8.5], 46],   # сбоку и чуть сверху: Ной в профиль, ведро со щёткой перед ним
     'beats': [
         note('Ведро со щёткой для стёкол. Синяя вода, сверху тонкий лёд.',
              'A bucket and squeegee for windshields. Blue water under a thin skin of ice.', 2.8),
         note('Ной разбивает лёд и опускает руки. Холод — до локтей.',
              'Noah breaks the ice and plunges his hands in. The cold reaches his elbows.', 3.0,
              anim={'who': 'noah', 'clip': 'Crouch_Idle'}, fx='snd:splash:-14'),
         note('Вода розовеет.', 'The water turns pink.', 2.2, fx='wash_hands'),
         note('Ладони чистые. Под ногтями и на рукаве — бурое.',
              'His palms come clean. Under his nails and on his sleeve — rust-brown.', 2.8,
              anim={'who': 'noah', 'clip': 'Idle'}, look={'who': 'noah', 'at': 'noah_hands'}),
         note('Айрис смотрит с крыльца — и отводит глаза.', 'Iris watches from the step — and looks away.', 2.6,
              look={'who': 'iris', 'at': 'noah'}, cam=[[17.3, 1.35, -5.6], [20.2, 0.95, -4.05], 32]),
         {'look': {'who': 'iris', 'at': 'danny'}, 'set': {'washed': 1}},
         {'look': {'who': 'noah', 'at': None}},
     ]},
    {'id': 'trunk', 'at': [14.3, 1.05, -2.45], 'where': [14.3, -1.75], 'r': 1.3, 'stand': [14.3, -1.75], 'face': 180,
     'label': L('Открыть багажник', 'Open the trunk'),
     'cam': [[15.05, 1.6, -0.4], [14.3, 0.85, -2.6], 44],
     'beats': [
         {'anim': {'who': 'noah', 'clip': 'Chest_Open'}, 'fx': 'trunk_open', 'dur': 1.2},
         note('Отцовские удочки. Ящик с инструментами. Свёрнутое старое одеяло.',
              'Their father’s fishing rods. A toolbox. An old folded blanket.', 3.0),
         note('Монтировка брошена сверху. На ней бурое и шерсть.', 'The tire iron lies on top. Dark stains, and fur.', 2.8,
              **{'if': {'deer': '==2'}}),
         note('Ной не помнит, чтобы отец хоть раз взял его на рыбалку.',
              'Noah can’t remember his father ever taking him fishing.', 3.0),
     ],
     'ask': {'max': 1, 'opts': [
         {'ru': 'Взять одеяло', 'en': 'Take the blanket', 'beats': [
             note('Ной берёт одеяло под мышку.', 'Noah tucks the blanket under his arm.', 2.0, set={'blanket': 1}, fx='trunk_blanket')]},
         {'ru': 'Закрыть', 'en': 'Close it', 'beats': [{'dur': 0.3}]},
     ]},
     'after': [{'anim': {'who': 'noah', 'clip': 'Idle'}, 'fx': 'trunk_close', 'dur': 0.6}]},
    {'id': 'vending', 'at': [20.3, 1.35, -11.0], 'where': [19.65, -11.0], 'r': 1.3, 'stand': [19.65, -11.0], 'face': 90,
     'label': L('Купить шоколадку', 'Buy a candy bar'),
     'cam': [[18.35, 1.5, -10.3], [20.5, 1.15, -11.0], 42],
     'beats': [
         note('Автомат: кофе, шоколад. Ной бросает монету. Автомат её глотает — и ничего.',
              'A vending machine: coffee, chocolate. Noah drops in a coin. The machine swallows it. Nothing.', 3.4),
     ],
     'ask': {'max': 1, 'opts': [
         {'ru': 'Ударить по стеклу', 'en': 'Hit the glass', 'beats': [
             {'anim': {'who': 'noah', 'clip': 'Punch_Jab'}, 'fx': 'snd:knock:-2', 'dur': 0.5},
             note('Ной бьёт ладонью по стеклу. Батончик падает.', 'Noah slams his palm against the glass. A candy bar drops.', 2.6,
                  set={'choco': 1}),
             note('В голове звенит — громче удара.', 'His head rings — louder than the blow.', 2.4, fx='hum_spike',
                  **{'if': {'pills': '==0'}}),
             note('За витриной кассир поднимает голову.', 'Behind the window, the cashier looks up.', 2.4,
                  look={'who': 'roman', 'at': 'noah'}, cam=[[25.2, 1.95, -11.05], [26.3, 1.7, -9.3], 42]),   # от задней полки: лицо Романа, за ним витрина
             {'look': {'who': 'roman', 'at': 'tv'}, 'anim': {'who': 'noah', 'clip': 'Idle'}},
         ]},
         {'ru': 'Отойти', 'en': 'Walk away', 'beats': [note('Ной отходит.', 'Noah walks away.', 1.4)]},
     ]}},
    {'id': 'board', 'at': [25.25, 1.95, 1.12], 'where': [24.55, 1.75], 'r': 1.4, 'stand': [24.55, 1.75], 'face': 165,
     'label': L('Осмотреть доску', 'Look at the notice board'),
     'cam': [[25.75, 1.62, 2.75], [25.25, 1.48, 1.0], 34],
     'beats': [
         note('«Продам дрова». «Пропала собака, рыжая, зовут Лиса».', '“Firewood for sale.” “Lost dog, ginger, answers to Foxy.”', 3.4,
              look={'who': 'noah', 'at': [25.0, 1.5, 1.0]}),
         note('Выцветший плакат: санаторий «Тихие сосны». Сон, тишина, сосновый воздух.',
              'A faded poster: Quiet Pines Sanatorium. Sleep, silence, pine air.', 3.6,
              cam=[[25.47, 1.52, 2.3], [25.47, 1.5, 1.0], 32]),
         {'look': {'who': 'noah', 'at': None}},
     ]},
    {'id': 'phone', 'at': [23.2, 2.25, 1.18], 'where': [23.2, 1.8], 'r': 1.35, 'stand': [23.2, 1.8], 'face': 180,
     'label': L('Позвонить', 'Use the payphone'),
     'cam': [[23.95, 1.6, 3.1], [23.15, 1.4, 1.0], 40],
     'beats': [
         note('Таксофон. Трубка ледяная. Гудок есть.', 'A payphone. The receiver is ice-cold. There’s a dial tone.', 3.0,
              anim={'who': 'noah', 'clip': 'Idle_TalkingPhone'}, fx='phone_pick'),
     ],
     'ask': {'max': 1, 'opts': [
         {'ru': 'Набрать домашний', 'en': 'Dial home', 'beats': [
             note('Пальцы сами набирают домашний номер.', 'His fingers dial home on their own.', 2.6),
             note('Длинные гудки. Пять. Шесть.', 'Long rings. Five. Six.', 3.2),
             note('Дома некому снять трубку.', 'There’s no one at home to pick up.', 2.8, set={'phoned': 1}),
         ]},
         {'ru': 'Повесить трубку', 'en': 'Hang up', 'beats': [{'dur': 0.6}]},
     ]},
     'after': [{'anim': {'who': 'noah', 'clip': 'Idle'}, 'fx': 'phone_hang'}]},
    {'id': 'window', 'at': [21.0, 1.75, -6.6], 'where': [20.45, -6.45], 'r': 1.2, 'stand': [20.45, -6.45], 'face': 108,
     'label': L('Заглянуть в витрину', 'Look through the window'),
     'cam': [[19.55, 2.05, -5.6], [26.4, 1.55, -9.4], 26],   # узко: кассир крупнее за стеклом
     'beats': [
         note('За стеклом — кассир. Он смотрит телевизор. Звука не слышно.', 'Behind the glass, the cashier. He’s watching TV. No sound.', 3.4,
              anim={'who': 'noah', 'clip': 'Rk_Watching'}, look={'who': 'noah', 'at': 'roman'}),
         {'anim': {'who': 'noah', 'clip': 'Idle'}, 'look': {'who': 'noah', 'at': None}},
     ]},
    {'id': 'road', 'at': [4.7, 1.2, -3.0], 'where': [5.4, -3.0], 'r': 1.6, 'stand': [5.4, -3.0], 'face': -90,
     'label': L('Посмотреть на трассу', 'Look at the road'),
     'cam': [[7.7, 2.1, -1.3], [0.5, 0.9, -6.0], 55],
     'beats': [
         note('Трасса пустая в обе стороны. Снег заметает их следы.', 'The highway is empty both ways. Snow is filling in their tracks.', 3.4,
              anim={'who': 'noah', 'clip': 'Rk_LookAround'}),
         note('Ещё одни следы шин сворачивают к краю площадки — к чёрной машине.',
              'Another set of tire tracks turns off to the edge of the lot — to the black car.', 3.4,
              cam=[[8.7, 2.2, -6.2], [9.2, 0.7, -24.0], 30], **{'if': {'late': '>0'}}),   # правее сугроба у дороги
         {'anim': {'who': 'noah', 'clip': 'Idle'}},
     ]},
    {'id': 'blackcar', 'if': {'late': '>0'}, 'at': [10.4, 1.5, -16.6], 'where': [10.6, -15.2], 'r': 1.8, 'stand': [10.6, -15.2],
     'face': 188, 'label': L('Присмотреться', 'Take a closer look'),
     'cam': [[11.55, 1.65, -13.3], [9.2, 1.0, -24.0], 28],
     'beats': [
         note('Чёрный седан у края площадки. Мотор заглушен, стёкла тёмные.', 'A black sedan at the edge of the lot. Engine off, dark windows.', 3.2,
              look={'who': 'noah', 'at': [9.2, 1.2, -24.0]}),
         note('Водителя не видно. Но Ной уверен: на него смотрят.', 'He can’t see the driver. But Noah is sure he’s being watched.', 3.2),
         {'look': {'who': 'noah', 'at': None}},
     ]},
    {'id': 'iris', 'at': [20.3, 1.3, -4.1], 'where': [19.0, -4.7], 'r': 1.25, 'stand': [19.0, -4.7], 'face': 70,
     'label': L('Поговорить с Айрис', 'Talk to Iris'),
     'cam': [[18.95, 1.5, -5.3], [20.3, 1.15, -4.1], 38],   # сбоку от Ноя, на уровне его головы: Ной за краем кадра, Айрис смотрит на него — почти в камеру (раньше спина Ноя занимала полкадра)
     'beats': [
         {'look': {'who': 'noah', 'at': 'iris'}},
         say('noah', 'Ты как?', 'You okay?', look={'who': 'iris', 'at': 'noah'}),
         say('iris', 'Нормально.', 'Fine.'),
         note('Она прячет блокнот под куртку.', 'She tucks the notebook under her jacket.', 2.4),
         say('iris', 'Иди плати. Я с ним посижу.', 'Go pay. I’ll sit with him.', **{'if': {'trust': '>=2'}}),
         say('iris', 'Плати и сразу назад. Ни с кем не говори.', 'Pay and come straight back. Don’t talk to anyone.', **{'if': {'trust': '<2'}}),
         say('iris', 'Опять голова?', 'Your head again?', **{'if': {'pills': '==0'}}),
         say('noah', 'Пройдёт.', 'It’ll pass.', **{'if': {'pills': '==0'}}),
         {'look': {'who': 'iris', 'at': 'danny'}},
         {'look': {'who': 'noah', 'at': None}},
     ]},
    {'id': 'danny', 'once': False, 'at': [19.98, 1.25, -3.3], 'where': [18.95, -3.25], 'r': 1.3, 'stand': [18.95, -3.25], 'face': 90,
     'label': L('Поговорить с Дэнни', 'Talk to Danny'),
     'cam': [[19.25, 1.2, -1.55], [19.7, 0.95, -3.35], 40],   # сбоку: Ной присел перед Дэнни, оба в профиль, Айрис за ними
     'beats': [
         {'anim': {'who': 'noah', 'clip': 'Crouch_Idle'}, 'look': {'who': 'danny', 'at': 'noah'}},
         {'look': {'who': 'noah', 'at': 'danny'}},   # Ной смотрит на мальчика, а не в камеру (кадр 3 ноя)
         say('danny', 'У тебя руки грязные.', 'Your hands are dirty.', set={'danny_hands': 1},
             **{'if': {'washed': '==0', 'danny_hands': '==0'}}),
         note('Ной прячет руки в карманы.', 'Noah shoves his hands into his pockets.', 2.2, set={'danny_hands': 1},
              **{'if': {'washed': '==0', 'danny_hands': '==1'}}),
     ],
     'ask': {'opts': [
         {'ru': '«Как тебя зовут?»', 'en': '“What’s your name?”', 'beats': [
             say('noah', 'Как тебя зовут?', 'What’s your name?'),
             say('danny', 'Дэнни. А ты?', 'Danny. You?'),
             say('noah', 'Ной.', 'Noah.'),
             say('danny', 'Как в книжке. Про корабль и зверей.', 'Like in the book. With the boat and the animals.', set={'danny_name': 1}),
         ]},
         {'ru': '«Что за рация?»', 'en': '“What’s the walkie-talkie for?”', 'beats': [
             say('noah', 'Что за рация?', 'What’s the walkie-talkie for?'),
             say('danny', 'Вторая у мамы. Она сказала: если что — нажми и говори.', 'Mom has the other one. She said: if anything happens, push and talk.'),
             note('Он жмёт кнопку. Шипение.', 'He presses the button. Static.', 2.2, fx='snd:static_burst:-14'),
             say('danny', 'Мама. Приём…', 'Mom. Over…'),
             {'dur': 1.2},
             say('danny', 'Она всегда сначала не отвечает.', 'She never answers the first time.'),
         ]},
         {'ru': '«Где твоя мама?»', 'en': '“Where’s your mom?”', 'beats': [
             say('noah', 'Где твоя мама?', 'Where’s your mom?'),
             say('danny', 'Сказала: жди тут, я быстро.', 'She said: wait here, I’ll be quick.'),
             note('Он смотрит на пустую трассу.', 'He looks at the empty road.', 2.4, look={'who': 'danny', 'at': [0.0, 1.0, -8.0]}),
             say('danny', 'Тогда ещё было светло.', 'It was still light out.', look={'who': 'danny', 'at': 'noah'}),
         ]},
         {'if': {'choco': '>0'}, 'ru': '«Держи»', 'en': '“Here”', 'beats': [
             say('noah', 'Держи.', 'Here.'),
             note('Дэнни берёт батончик обеими руками. Не ест — прячет в карман. На потом.',
                  'Danny takes the bar in both hands. He doesn’t eat it — he tucks it in his pocket. For later.', 3.6, set={'choco_given': 1}),
         ]},
         {'if': {'blanket': '>0'}, 'ru': '«Укройся»', 'en': '“Wrap up”', 'beats': [
             say('noah', 'Укройся.', 'Wrap up.'),
             note('Айрис закутывает его в отцовское одеяло. Дэнни перестаёт дрожать.', 'Iris wraps him in their father’s blanket. Danny stops shivering.',
                  3.4, set={'blanket_given': 1}),
         ]},
         {'leave': True, 'ru': 'Отойти', 'en': 'Step away', 'beats': [
             say('danny', 'Вы уедете?', 'Are you leaving?', **{'if': {'danny_bye': '==0'}}),
             say('noah', 'Сначала заплачу.', 'I have to pay first.', set={'danny_bye': 1}, **{'if': {'danny_bye': '==0'}}),
         ]},
     ]},
     'after': [{'anim': {'who': 'noah', 'clip': 'Idle'}, 'look': {'who': 'danny', 'at': 'iris'}}, {'look': {'who': 'noah', 'at': None}}]},
]

WALK = {
    'who': 'noah',
    'start': {'at': [13.0, 0.0, -4.0], 'face': 74},   # от колонки — лицом к магазину (камера за спиной, не в машине)
    'setup': [
        {'place': {'who': 'roman', 'at': [26.4, F, -9.4], 'face': 165, 'clip': 'Idle'}, 'look': {'who': 'roman', 'at': 'tv'}},
    ],
    'intro': [
        note('Щелчок. Колонка встаёт: полный бак.', 'A click. The pump shuts off: the tank is full.', 2.6, fx='snd:nozzle:-8'),
        note('Гул в голове не стихает.', 'The hum in his head won’t let up.', 2.2, **{'if': {'pills': '==0'}}),
        note('Заплатить — в магазине.', 'Pay inside.', 2.0),
    ],
    'hum': {'if': {'pills': '==0'}, 'v': 0.28},
    'watch': ['danny', 'iris'],
    'walk': [[4.8, -17.8, 31.0, 5.6]],
    'block': [
        [20.98, -12.15, 31.4, 1.1],        # магазин
        [11.35, -5.6, 12.65, -4.4],        # колонка Ноя со шлангами (островок — низкий бордюр, по нему ходят)
        [15.35, -5.6, 16.65, -4.4],        # вторая колонка
        [11.82, -9.35, 12.18, -8.52],      # столб навеса и столбик у края островка
        [11.82, -1.48, 12.18, -0.65],
        [15.82, -9.35, 16.18, -8.52],
        [15.74, -1.48, 16.26, -0.09],      # столб, столбик и урна
        [12.28, -8.72, 12.62, -8.38],      # ведро для стёкол
        [13.38, -7.9, 15.22, -2.5],        # машина
        [20.2, -0.75, 20.92, 0.75],        # ледник
        [20.25, 1.95, 20.75, 2.45],        # автомат воздуха
        [20.3, 2.5, 21.2, 3.3],            # канистры
        [19.5, 3.0, 20.3, 3.8],            # шины
        [19.35, -4.6, 20.98, -2.85],       # Айрис и Дэнни на крыльце
        [20.32, -11.46, 20.98, -10.54],    # автомат с шоколадом
        [5.0, -16.2, 8.2, -7.8],           # сугроб у дороги
        [22.85, 1.1, 23.55, 1.4],          # таксофон
        [24.55, 1.1, 25.95, 1.2],          # доска объявлений
    ],
    'floors': [[19.85, -10.05, 20.98, 0.05, F], [11.35, -9.1, 12.65, -0.9, 0.18], [15.35, -9.1, 16.65, -0.9, 0.18]],
    # камера не заходит в магазин и машину (последнее число — до какой высоты препятствие); тонкие столбы и колонки
    # камера не обходит — иначе она прыгала бы вплотную к Ною у каждого столба (кадр у ведра, 3 ноя)
    'cam_block': [[20.9, -12.2, 31.5, 1.15, 4.4], [13.38, -7.9, 15.22, -2.5, 1.7]],
    'nav': [[13.0, -0.6], [17.0, -0.2], [18.6, -1.6], [18.6, -6.0], [17.0, -9.9], [13.0, -9.9], [10.6, -5.0], [18.8, -11.0],
            [19.3, 0.9], [21.5, 1.7], [26.0, 2.4], [10.6, -12.0], [18.5, -9.6]],
    'spots': SPOTS,
    'zones': [{'rect': [4.8, -17.8, 20.0, -16.3], 'if': {'late': '>0'}, 'beats': [
        say('iris', 'Ной! Не ходи туда.', 'Noah! Don’t go over there.')]}],
    'nudge': {'after': 180, 'beats': [say('iris', 'Ной. Нам надо ехать.', 'Noah. We need to go.')]},
    'exit': {'id': 'door', 'at': [20.98, 1.45, -2.0], 'where': [19.7, -1.85], 'r': 1.25, 'stand': [19.7, -1.85], 'face': 90,
             'label': L('Войти и заплатить', 'Go in and pay'),
             'beats': [{'walk': {'who': 'noah', 'to': [20.55, F, -2.0], 'then': 'Idle'}, 'shot': 'st_door', 'dur': 2.6}]},
}

TALK = {'loc': 'car', 'beats': [
    note('Полчаса пустой дороги. Дворники, снег, гул мотора.', 'Half an hour of empty road. Wipers, snow, the drone of the engine.', 2.8,
         shot='road'),
    {'ask': {'max': 2, 'shot': 'two', 'opts': [
        {'ru': 'Про маму', 'en': 'About Mom', 'beats': [
            say('noah', 'Ты маму помнишь?', 'Do you remember Mom?', shot='noah'),
            say('iris', 'Немножко. Руки. …А ты?', 'A little. Her hands. …You?', shot='iris'),
            say('noah', 'Воду. Почему-то всегда воду.', 'Water. For some reason, always water.', shot='noah'),
            note('Айрис долго смотрит на него. И ничего не говорит.', 'Iris looks at him for a long time. She says nothing.', 2.6,
                 shot='iris', look='noah'),
        ]},
        {'ru': 'Куда мы едем', 'en': 'Where we’re going', 'beats': [
            say('noah', 'Что там, на севере?', 'What’s up north?', shot='noah'),
            say('iris', 'Там нас никто не ищет.', 'No one’s looking for us there.', shot='iris'),
            say('noah', 'Это не ответ.', 'That’s not an answer.', shot='noah'),
            say('iris', 'Другого пока нет.', 'It’s the only one I’ve got.', shot='iris'),
        ]},
        {'ru': 'Что было дома', 'en': 'What happened at home', 'beats': [
            say('noah', 'Айрис… что было дома?', 'Iris… what happened at home?', shot='noah'),
            say('iris', 'Ты уже спрашивал.', 'You already asked.', shot='iris', **{'if': {'asked': '>0'}}),
            say('iris', 'Не сейчас. Пожалуйста.', 'Not now. Please.', shot='iris'),
            note('Она сжимает блокнот так, что белеют пальцы.', 'She grips the notebook until her knuckles go white.', 2.4, shot='notebook'),
            {'if': {'asked': '==0'}, 'set': {'asked': 1}},
        ]},
        {'if': {'danny': '>0'}, 'ru': 'Про Дэнни', 'en': 'About Danny', 'beats': [
            say('noah', 'Что мы будем с ним делать?', 'What are we going to do with him?', shot='noah'),
            say('iris', 'Не знаю. Но не бросим.', 'I don’t know. But we’re not leaving him.', shot='iris'),
            say('danny', 'Я не сплю. Я всё слышу.', 'I’m not asleep. I can hear you.', shot='back', **{'if': {'lapse1': '==0'}}),
            say('danny', 'Можно я теперь съем шоколадку?', 'Can I eat the chocolate now?', shot='back',
                **{'if': {'lapse1': '==0', 'choco_given': '>0'}}),
            say('iris', 'Можно.', 'You can.', shot='iris', expr={'who': 'iris', 'set': 'smile'},
                **{'if': {'lapse1': '==0', 'choco_given': '>0'}}),
            note('С заднего сиденья — ни звука. Дэнни не спит: смотрит Ною в затылок.',
                 'Not a sound from the back seat. Danny isn’t asleep: he’s staring at the back of Noah’s head.', 3.0, shot='back',
                 **{'if': {'lapse1': '>0'}}),
            {'expr': {'who': 'iris', 'set': 'none'}},
        ]},
        {'ru': 'Про таблетки', 'en': 'About the pills', 'beats': [
            say('noah', 'В голове тихо.', 'My head’s quiet.', shot='noah', **{'if': {'pills': '>0'}}),
            say('iris', 'Тихо — это ещё не хорошо.', 'Quiet isn’t the same as okay.', shot='iris', **{'if': {'pills': '>0'}}),
            say('noah', 'Зря я их выбросил?', 'Was I wrong to throw them out?', shot='noah', **{'if': {'pills': '==0', 'threw': '>0'}}),
            say('noah', 'Может, надо было выпить?', 'Maybe I should have taken one.', shot='noah', **{'if': {'pills': '==0', 'threw': '==0'}}),
            say('iris', 'На них ты спишь с открытыми глазами.', 'On them you sleep with your eyes open.', shot='iris',
                **{'if': {'pills': '==0', 'lapse1': '==0'}}),
            say('iris', 'Я не знаю, Ной. Я правда не знаю.', 'I don’t know, Noah. I really don’t.', shot='iris',
                **{'if': {'pills': '==0', 'lapse1': '>0'}}),
        ]},
        {'leave': True, 'ru': 'Молчать', 'en': 'Stay quiet', 'beats': []},
    ], 'end': [note('Дальше едут молча.', 'They drive on in silence.', 2.2, shot='two')]}},
    {'go': 's8'},
]}

NEW_FLAGS = ['washed', 'danny_name', 'danny_hands', 'danny_bye', 'choco', 'choco_given', 'blanket', 'blanket_given', 'phoned']


def main():
    j = json.load(open(DATA, encoding='utf-8'))
    sc = j['scenes']
    # прогулка
    j.setdefault('explore', {})['st24'] = WALK
    for f in NEW_FLAGS:
        j['flags'].setdefault(f, 0)
    # сцена 5: вместо «Ной сам идёт к двери» — прогулка
    s5 = sc['s5']['beats']
    for i, b in enumerate(s5):
        if b.get('explore') == 'st24':
            break
        w = b.get('walk')
        if w and w.get('who') == 'noah' and b.get('shot') == 'st_door':
            s5[i] = {'explore': 'st24'}
            break
    else:
        sys.exit('s5: не нашёл шаг «Ной идёт к двери»')
    # «На его руки.» — отмыл руки: другая строка
    for i, b in enumerate(s5):
        nt = b.get('note')
        if isinstance(nt, dict) and nt.get('ru') == 'На его руки.':
            if 'if' not in b:
                b['if'] = {'washed': '==0'}
                alt = dict(b)
                alt['if'] = {'washed': '>0'}
                alt['note'] = N('На его руки. Ладони чистые — под ногтями тёмное.', 'At his hands. The palms are clean — dark under the nails.')
                alt['dur'] = 2.2
                s5.insert(i + 1, alt)
            break
    # сцена 6: имя Дэнни — Ной мог спросить сам
    for i, b in enumerate(sc['s6']['beats']):
        nt = b.get('note')
        if isinstance(nt, dict) and nt.get('ru', '').startswith('Айрис держит мальчика за руку. Его зовут'):
            if 'if' not in b:
                b['if'] = {'danny_name': '==0'}
                alt = dict(b)
                alt['if'] = {'danny_name': '>0'}
                alt['note'] = N('Айрис держит мальчика за руку.', 'Iris is holding the boy’s hand.')
                alt['dur'] = 2.0
                sc['s6']['beats'].insert(i + 1, alt)
            break
    # сцена 7 → разговор в дороге → сцена 8 (в файле — перед s8, чтобы сценарий читался по порядку)
    order = [k for k in sc if k != 's7_talk']
    pos = order.index('s8')
    order.insert(pos, 's7_talk')
    sc['s7_talk'] = TALK
    j['scenes'] = sc = {k: sc[k] for k in order}
    for k in ('s7_a', 's7_b', 's7_c'):
        for b in sc[k]['beats']:
            if b.get('go') == 's8':
                b['go'] = 's7_talk'
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    import vokeys   # свой ключ голоса у реплик внутри прогулок и разговоров
    vokeys.assign(j)
    s = json.dumps(j, ensure_ascii=False, indent=1) + '\n'
    open(DATA, 'w', encoding='utf-8').write(s)
    print('ch1.json: прогулка st24 (%d точек), разговор s7_talk, флаги %s' % (len(SPOTS), ', '.join(NEW_FLAGS)))


if __name__ == '__main__':
    main()
