"""WHAT THE LAKE KEEPS, глава 4 — эпизоды по мастер-промпту редактора (3 ноя): буханка во дворе Агнес (свечи из дедова
ящика, аккумулятор их седана, бензин — потом «с толкача»), остановка у заброшенного павильона, пока остывает мотор
(после «закипает», до ремонта шланга; указатель на «Тихие сосны»). Подключается из tools/lake/story/ch4.py (apply(DATA)).
Новые отметки — только свои (c4_*): доверие, «Тень», таблетки, улики и концовки не трогают.

Координаты — мировые. Двор: буханка VAN_YARD (−10,5; −55), носом к улице (+x); водительская дверь — с юга (z −56),
пассажирская — с севера (z −54); сарай (−18,5; −52), дверь на восток (x −17). Остановка (после торможения): буханка в
начале координат носом к −z, павильон — справа у дороги (x 4,65…6,15; z −2,55…0,55), открыт от дороги (к +x)."""

def L(ru, en):
    return {'ru': ru, 'en': en}


def say(who, ru, en, **kw):
    b = {'say': who, 'ru': ru, 'en': en}
    b.update(kw)
    return b


def note(ru, en, dur=2.6, **kw):
    b = {'note': L(ru, en), 'dur': dur}
    b.update(kw)
    return b


def IF(**c):
    return {'if': c}


DANNY_IN = {'danny': '>0', 'danny_lost': '==0'}
ZOE_IN = {'zoe': '>0', 'zoe_gone': '==0'}

# ------------------------------------------------------------------------------------- буханка во дворе
VAN_SPOTS = [
    {'id': 'toolbox', 'at': [-16.55, 0.45, -51.2], 'where': [-15.85, -51.2], 'r': 0.7, 'stand': [-15.85, -51.2], 'face': -90,
     'label': L('Дедов ящик у сарая', 'Grandpa’s toolbox by the shed'),
     'cam': [[-14.4, 1.55, -50.0], [-16.5, 0.35, -51.3], 44],
     'beats': [
         {'anim': {'who': 'noah', 'clip': 'Crouch_Idle'}},
         note('Дедов ящик. Ключи в масле, в промасленной газете — свечи. Новые, в коробке.',
              'Grandpa’s toolbox. Wrenches slick with oil; wrapped in oily newspaper — spark plugs. New ones, still boxed.', 3.2),
         say('agnes', 'Он их берёг. Говорил: на чёрный день. Вот он и пришёл.', 'He was saving them. Said they were for a rainy day. Well, here it is.',
             look={'who': 'agnes', 'at': 'noah'}),
         {'set': {'c4_plugs': 1}, 'anim': {'who': 'noah', 'clip': 'Idle'}},
     ],
     'after': [{'look': {'who': 'agnes', 'at': None}}]},

    {'id': 'plugs', 'if': {'c4_plugs': '>0'}, 'at': [-8.25, 0.9, -55.0], 'where': [-7.45, -55.0], 'r': 0.6, 'stand': [-7.55, -55.0],
     'face': -90, 'label': L('Вкрутить свечи', 'Put in the spark plugs'),
     'cam': [[-6.95, 1.6, -53.6], [-8.4, 0.8, -55.0], 46],
     'beats': [
         {'fx': 'van_hood'},
         note('Старые свечи — в нагаре, чёрные, как угли. Ной выкручивает их по одной.',
              'The old plugs are fouled, black as coal. Noah unscrews them one by one.', 3.0),
         {'qte': 'press', 't': 6.0, 'n': 8, 'sfx': 'none', 'hint': L('Вкручивай свечи — жми E / пробел в ритм', 'Screw in the plugs — press E / Space in rhythm')},
         note('Новые встают туго, с хрустом резьбы. Четыре из четырёх.', 'The new ones go in tight, the threads crunching. Four out of four.', 2.8),
         note('Пока резьба схватывается, Ной притаскивает аккумулятор с их седана у забора. Руки отваливаются.',
              'While the threads bite, Noah hauls the battery over from their sedan by the fence. His arms are about to fall off.', 3.4),
         {'set': {'c4_plugs2': 1}, 'anim': {'who': 'noah', 'clip': 'Idle'}},
     ]},

    {'id': 'battery', 'if': {'c4_plugs2': '>0', 'c4_batt': '==0'}, 'once': False, 'at': [-9.3, 0.8, -53.95], 'where': [-9.3, -53.3],
     'r': 0.6, 'stand': [-9.3, -53.25], 'face': 180, 'label': L('Подключить аккумулятор', 'Hook up the battery'),
     'cam': [[-8.0, 1.6, -52.2], [-9.4, 0.8, -54.0], 44],
     'beats': [
         note('Аккумулятор — под пассажирским сиденьем. Два провода: красный и чёрный.',
              'The battery goes under the passenger seat. Two cables: red and black.', 2.8),
     ],
     'ask': {'max': 1, 'opts': [
         {'ru': 'Красный — на «плюс»', 'en': 'Red to the positive', 'beats': [
             note('Клеммы садятся плотно. На щитке вспыхивает лампочка.', 'The clamps bite down tight. A light blinks on the dash.', 2.6, set={'c4_batt': 1}),
         ]},
         {'ru': 'Чёрный — на «плюс»', 'en': 'Black to the positive', 'beats': [
             note('Искра — Ной отдёргивает руку. Не тот провод.', 'A spark — Noah jerks his hand back. Wrong cable.', 2.4, fx='snd:static_burst:-10'),
         ]},
     ]}},

    {'id': 'gas', 'at': [-11.1, 0.9, -56.05], 'where': [-11.1, -56.75], 'r': 0.6, 'stand': [-11.1, -56.8], 'face': 0,
     'label': L('Залить бензин', 'Pour in the gas'),
     'cam': [[-12.6, 1.5, -57.8], [-11.0, 0.85, -56.1], 44],
     'beats': [
         note('Дедова канистра из сарая. Бензин старый, пахнет лаком — но гореть будет.',
              'Grandpa’s gas can from the shed. The gas is old and smells like varnish — but it’ll burn.', 3.0),
         note('И остаток из генератора — на донышке.', 'And what’s left in the generator — the last few drops.', 2.2, **IF(light='>0')),
         note('Дэнни держит воронку обеими руками — очень серьёзно.', 'Danny holds the funnel with both hands — very seriously.', 2.6, **IF(**DANNY_IN)),
         note('Воронку держит Айрис. Молча.', 'Iris holds the funnel. Without a word.', 2.4, **IF(danny='==0')),
         note('Воронку держит Айрис. Молча.', 'Iris holds the funnel. Without a word.', 2.4, **IF(danny='>0', danny_lost='>0')),   # «или» — без повтора
         {'set': {'c4_gas': 1}},
     ]},

    {'id': 'agnes', 'at': [-7.6, 1.5, -57.8], 'where': [-8.3, -57.35], 'r': 0.6, 'stand': [-8.3, -57.3], 'face': 125,
     'label': L('Агнес', 'Agnes'),
     'cam': [[-9.4, 1.55, -58.9], [-7.6, 1.5, -57.8], 40],
     'beats': [{'look': {'who': 'agnes', 'at': 'noah'}}],
     'ask': {'max': 2, 'opts': [
         {'ru': '«Чья это буханка?»', 'en': '“Whose van is this?”', 'beats': [
             say('noah', 'Чья это буханка?', 'Whose van is this?'),
             say('agnes', 'Деда. Возил на ней Аню в школу, за двенадцать вёрст. Она в кузове песни орала — всю дорогу.',
                 'Grandpa’s. Drove Annie to school in it, twelve versts each way. She’d belt out songs in the back the whole ride.'),
         ]},
         {'ru': '«Почему вы её не продали?»', 'en': '“Why didn’t you sell it?”', 'beats': [
             say('noah', 'Почему вы её не продали?', 'Why didn’t you sell it?'),
             say('agnes', 'Кому она нужна. А мне — нужна. Сядешь — пахнет им. Махоркой и бензином.',
                 'Who’d want it? But I need it. You sit inside — it smells of him. Shag tobacco and gasoline.'),
         ]},
         {'ru': '«Куда мы поедем?»', 'en': '“Where are we going?”', 'beats': [
             say('noah', 'Куда мы поедем?', 'Where are we going?'),
             say('agnes', 'К озеру. Куда ж ещё. Вы ведь туда с самого начала и ехали.',
                 'To the lake. Where else? That’s where you were headed from the start.'),
         ]},
         {'leave': True, 'ru': 'Отойти', 'en': 'Step away', 'beats': [{'dur': 0.3}]},
     ]},
     'after': [{'look': {'who': 'agnes', 'at': None}}]},
]

VAN = {
    'who': 'noah',
    'start': {'at': [-7.3, 0.0, -51.6], 'face': -150},
    'intro': [
        note('Свечи, аккумулятор, бензин. И чтобы мотор, восемь лет простоявший в снегу, вспомнил, как это — заводиться.',
             'Spark plugs, a battery, gas. And an engine that’s sat in the snow for eight years has to remember how to start.', 3.6),
    ],
    'walk': [[-16.6, -58.4, -6.65, -49.72]],   # до сугроба у стены дома Агнес (z −49,7), не в него
    'block': [
        [-12.85, -56.1, -8.15, -53.9],      # буханка
        [-16.82, -51.38, -16.28, -51.02],   # дедов ящик с инструментом у сарая
        [-15.92, -55.52, -15.28, -54.88],   # колода для колки дров
        [-16.95, -54.4, -16.4, -52.2],      # поленница у сарая
        [-7.95, -58.15, -7.25, -57.45],     # Агнес
        [-15.1, -57.9, -14.5, -57.3],       # Айрис
        [-14.9, -50.5, -14.3, -49.9, ZOE_IN],    # Зоя
        [-12.3, -58.1, -11.7, -57.5, DANNY_IN],  # Дэнни
        [-15.3, -54.8, -14.7, -54.2, {'eva_alive': '>0'}],   # Ева с малышкой
    ],
    'cam_block': [[-14.4, -48.9, -7.6, -41.1, 99.0], [-20.1, -53.8, -16.9, -50.2, 99.0], [-12.85, -56.1, -8.15, -53.9, 2.3]],
    'nav': [[-7.3, -51.6], [-7.3, -53.0], [-7.2, -57.0], [-10.5, -57.4], [-13.6, -57.0], [-13.6, -53.2], [-13.4, -51.0], [-10.5, -52.8]],
    'spots': VAN_SPOTS,
    'nudge': {'after': 150, 'beats': [say('agnes', 'Ну что, механик? Солнце-то уже высоко.', 'Well, mechanic? The sun’s already high.')]},
    'exit': {'id': 'push', 'if': {'c4_plugs2': '>0', 'c4_batt': '>0', 'c4_gas': '>0'}, 'at': [-9.3, 1.0, -56.05], 'where': [-9.3, -56.75],
             'r': 0.6, 'stand': [-9.3, -56.75], 'face': 0, 'label': L('Завести «с толкача»', 'Push-start it'),
             'beats': [{'dur': 0.3}]},
}

# ---------------------------------------------------------------- остановка: пока остывает мотор (до ремонта шланга)
STOP_SPOTS = [
    {'id': 'timetable', 'at': [4.8, 1.6, -1.6], 'where': [5.75, -1.6], 'r': 0.55, 'stand': [5.8, -1.6], 'face': -90,
     'label': L('Расписание', 'The timetable'),
     'cam': [[6.9, 1.65, -0.4], [4.8, 1.5, -1.6], 42],
     'beats': [
         note('Расписание под мутным пластиком. Последний автобус — «19:40». Год на листке выцвел.',
              'A timetable under cloudy plastic. The last bus — “7:40 p.m.” The year on the sheet has faded away.', 3.0),
         note('Кто-то нацарапал ключом: «ЗДЕСЬ БЫЛ…» Дальше соскоблено.', 'Someone scratched with a key: “I WAS HERE…” The rest is scraped off.', 2.8),
     ]},

    {'id': 'sign', 'at': [7.6, 1.7, -5.5], 'where': [7.0, -4.6], 'r': 0.7, 'stand': [7.0, -4.55], 'face': 147,
     'label': L('Указатель', 'The road sign'),
     'cam': [[5.6, 1.5, -3.2], [7.6, 1.75, -5.5], 40],
     'beats': [
         note('Ржавый указатель: «Санаторий „Тихие сосны“ — 6 км». Стрелка смотрит на лесную дорогу.',
              'A rusty road sign: “‘Quiet Pines’ Sanatorium — 6 km.” The arrow points down a forest road.', 3.2),
         note('Зоя видит указатель — и отворачивается.', 'Zoe sees the sign — and turns away.', 2.4, **IF(**ZOE_IN)),
         say('agnes', '«Тихие сосны»… Туда я её и отправила. К нему, к доктору.', '“Quiet Pines”… That’s where I sent her. To him. To the doctor.',
             look={'who': 'agnes', 'at': 'noah'}),
         {'set': {'c4_sign': 1}},
     ],
     'after': [{'look': {'who': 'agnes', 'at': None}}]},

    {'id': 'danny', 'if': DANNY_IN, 'at': [3.0, 0.8, 2.6], 'where': [2.2, 1.9], 'r': 0.6, 'stand': [2.2, 1.85], 'face': 50,
     'label': L('Дэнни', 'Danny'),
     'cam': [[1.4, 1.2, 0.9], [3.2, 0.6, 2.7], 44],
     'beats': [note('Дэнни лепит у павильона снеговика — сосредоточенно, как взрослый.', 'By the shelter, Danny is building a snowman — focused, like a grown-up.', 2.8)],
     'ask': {'max': 1, 'opts': [
         {'ru': '«Кого лепишь?»', 'en': '“Who are you making?”', 'beats': [
             say('noah', 'Кого лепишь?', 'Who are you making?'),
             say('danny', 'Сторожа. Чтобы машину сторожил, пока нас нет.', 'A guard. So he can watch the van while we’re gone.', look={'who': 'danny', 'at': 'noah'}),
         ]},
         {'ru': '«Не отходи далеко»', 'en': '“Don’t go far”', 'beats': [
             say('noah', 'Не отходи далеко.', 'Don’t go far.'),
             say('danny', 'Я тут. Я буханку вижу.', 'I’m right here. I can see the van.', look={'who': 'danny', 'at': 'noah'}),
         ]},
     ]},
     'after': [{'look': {'who': 'danny', 'at': None}}]},
]

STOP = {
    'who': 'noah',
    'start': {'at': [1.6, 0.0, -2.9], 'face': 120},
    'intro': [
        note('Мотор кипит. Трогать нельзя — только ждать, пока остынет.', 'The engine is boiling. Can’t touch it — just wait for it to cool.', 2.8),
        say('agnes', 'Остынет — сделаешь. Дед всегда ждал по полчаса. Курил.', 'Let it cool, then fix it. Grandpa always waited half an hour. Smoked.',
            look={'who': 'agnes', 'at': 'noah'}),
    ],
    'walk': [[-3.5, -9.0, 8.6, 6.5]],   # до нижних лап елей на краю (x 8,7)
    'block': [
        [-1.1, -3.02, 1.1, 2.45],                    # буханка (с бампером и запаской спереди)
        [-1.28, -1.98, -1.1, -1.74], [1.1, -1.98, 1.28, -1.74],   # её зеркала
        [4.55, -2.65, 4.75, 0.65],                   # павильон: задняя стена
        [4.55, -2.65, 6.2, -2.45], [4.55, 0.45, 6.2, 0.65],   # бока
        [4.75, -2.35, 5.25, 0.35],                   # лавка
        [5.8, 1.2, 6.0, 1.4],                        # столб с буквой «А»
        [7.45, -5.65, 7.75, -5.35],                  # указатель
        [-1.9, 0.9, -1.3, 1.5],                      # Агнес с трубкой
        [-2.0, -1.5, -1.4, -0.9, {'eva_alive': '>0'}],   # Ева с малышкой
        [2.5, -4.5, 3.1, -3.9],                      # Айрис
        [6.5, 1.9, 7.1, 2.5, ZOE_IN],                # Зоя
        [2.7, 2.3, 3.3, 2.9, DANNY_IN],              # Дэнни на корточках
        [3.3, 2.6, 3.9, 3.2, DANNY_IN],              # его снеговик
    ],
    'floors': [[4.3, -3.1, 6.9, 1.5, 0.15]],
    'cam_block': [[4.55, -2.65, 6.2, 0.65, 2.45], [-1.1, -2.45, 1.1, 2.45, 2.2]],
    'nav': [[1.6, -2.9], [2.6, -3.3], [7.2, -3.0], [7.2, -1.6], [2.2, 1.0], [2.0, 3.6], [6.6, 1.8]],
    'spots': STOP_SPOTS,
    'nudge': {'after': 140, 'beats': [say('agnes', 'Ну, остыл уже. Не век же тут стоять.', 'Well, it’s cooled off by now. We can’t stand here forever.')]},
    'exit': {'id': 'hood', 'at': [0.0, 1.0, -2.45], 'where': [0.4, -3.3], 'r': 0.6, 'stand': [0.25, -3.35], 'face': 0,
             'label': L('Мотор остыл — к шлангу', 'Engine’s cool — check the hose'),
             'beats': [{'dur': 0.3}]},
}

# ------------------------------------------------------------------ избушка: спрятаться от фонаря (до плача малышки)
ROCK = L('Медленно… Жми E / пробел редко, в ритм — не торопись', 'Slowly… Press E / Space rarely, in rhythm — don’t rush')
HIDE_SCENES = {
    'c4_s6_hide': {'beats': [
        {'fx': 'snd:snow_steps:-14', 'shot': 'hut', **note('Шаги вокруг избушки. Снег скрипит у самой стены.', 'Footsteps circle the hut. The snow creaks right by the wall.', 3.0)},
        {'shot': 'hut', **note('Дверь приоткрыта — на ладонь. В щель тянет холодом. И светом.', 'The door is ajar — a hand’s width. Cold seeps through the gap. And light.', 3.0)},
        {'choice': {'t': 6, 'opts': [
            {'ru': 'Закрыть дверь — медленно', 'en': 'Close the door — slowly', 'go': 'c4_s6_door'},
            {'ru': 'Не трогать — замереть', 'en': 'Leave it — freeze', 'go': 'c4_s6_still'},
            {'silent': True, 'go': 'c4_s6_still'}]}},
    ]},
    'c4_s6_door': {'beats': [
        {'fx': 'hut_door', 'shot': 'hut', **note('Ной ползёт к двери. Пальцы — на холодной доске.', 'Noah crawls to the door. His fingers on the cold board.', 2.6)},
        {'qte': 'rock', 't': 8.0, 'n': 4, 'gap': 0.75, 'hint': ROCK, 'fail': 'c4_s6_creak'},
        {'shot': 'hut', **note('Дверь встаёт на место. Без звука.', 'The door settles into place. Without a sound.', 2.4)},
        {'go': 'c4_s6_wait'}]},
    'c4_s6_creak': {'beats': [
        {'fx': 'snd:knock:-16', 'shot': 'hut_gap', **note('Петля скрипит. Луч дёргается к двери…', 'A hinge creaks. The beam jerks toward the door…', 2.6)},
        {'shot': 'hut_gap', **note('…и, помедлив, уходит дальше по стене.', '…and, after a moment, moves on along the wall.', 2.6)},
        {'go': 'c4_s6_wait'}]},
    'c4_s6_still': {'beats': [
        {'shot': 'hut', **note('Ной не дышит. Полоса света ложится на пол — у самых его ботинок. И уходит.',
                               'Noah doesn’t breathe. A strip of light falls across the floor — right at the tips of his boots. And moves away.', 3.4)},
        {'go': 'c4_s6_wait'}]},
    'c4_s6_wait': {'beats': [
        {'shot': 'hut', **note('Дэнни трясётся. Ной кладёт ладонь ему на затылок — тише, тише.', 'Danny is shaking. Noah lays a palm on the back of his head — hush, hush.', 3.0, **IF(**DANNY_IN))},
        {'shot': 'hut', **note('Агнес закашливается в кулак. Айрис прижимает к её губам край шарфа.', 'Agnes coughs into her fist. Iris presses the end of her scarf to her lips.', 3.2, **IF(agnes_dead='==0'))},
        {'fx': 'beam_pass2', 'shot': 'hut_gap', **note('Луч проходит по щелям — раз, другой. Будто пересчитывает доски.', 'The beam runs over the gaps — once, twice. As if it’s counting the boards.', 3.2)},
        {'go': 'c4_s6_baby'}]},
}

# ------------------------------------------------------------------ лёд: вдоль берега к мосткам (на рассвете)
def LK(x, z):   # местные координаты озера → мир (Ch4.LAKE = (−420, 0, −60))
    return [round(x - 420.0, 3), round(z - 60.0, 3)]


def LK3(x, y, z):
    return [round(x - 420.0, 3), round(y, 3), round(z - 60.0, 3)]


ICE_Y = -0.4
# тёмный (молодой) лёд: [x0, z0, x1, z1] местные; куда отступить — [x, z, лицом]
PATCHES = [
    ([-3.0, 58.0, 3.0, 64.0], [-6.2, 61.0, 180]),
    ([-7.6, 44.0, -1.0, 50.0], [4.2, 47.0, 180]),
    ([2.0, 30.0, 9.0, 36.0], [-3.2, 33.0, 180]),
    ([-7.6, 22.0, -2.0, 27.0], [2.8, 24.0, 180]),
]


def _grow(r, k):
    return [r[0] - k, r[1] - k, r[2] + k, r[3] + k]


def _ice_zones():
    zs = []
    for r, to in PATCHES:
        safe = LK(to[0], to[1]) + [to[2]]
        core = _grow(r, -0.6)
        zs.append({'rect': LK(core[0], core[1]) + LK(core[2], core[3]), 'manual': True, 'if': {'ice_wet': '==0'}, 'to': safe, 'beats': [
            {'fx': 'ice_fall'},
            note('Лёд проваливается. Вода — по пояс, ледяная. Ноги сразу немеют.', 'The ice gives way. The water is waist-deep and freezing. His legs go numb at once.', 3.0),
            note('Айрис хватает его за капюшон, Зоя — за руку. Рывок — и он на белом льду.',
                 'Iris grabs his hood, Zoe his hand. One heave — and he’s back on the white ice.', 3.2, **IF(**ZOE_IN)),
            note('Айрис хватает его за капюшон и тянет — откуда только силы. Он выползает на белый лёд.',
                 'Iris grabs his hood and pulls — who knows where she finds the strength. He crawls out onto the white ice.', 3.4, **IF(zoe='==0')),
            note('Айрис хватает его за капюшон и тянет — откуда только силы. Он выползает на белый лёд.',
                 'Iris grabs his hood and pulls — who knows where she finds the strength. He crawls out onto the white ice.', 3.4, **IF(zoe='>0', zoe_gone='>0')),   # была с ними и пропала (не взяли в кафе — уже zoe == 0)
            {'set': {'ice_wet': 1}, 'fx': 'ice_out'},
            note('Мокрый до пояса. На морозе штаны сразу встают колом.', 'Soaked to the waist. In the frost his trousers stiffen at once.', 2.8),
        ]})
    for r, to in PATCHES:   # внешнее кольцо — предупреждение (после него — ещё можно повернуть)
        safe = LK(to[0], to[1]) + [to[2]]
        warn = _grow(r, 1.2)
        zs.append({'rect': LK(warn[0], warn[1]) + LK(warn[2], warn[3]), 'manual': True, 'to': safe, 'beats': [
            {'fx': 'ice_crack'},
            note('Под ногой — тёмный лёд. Треск, как выстрел. Назад, по белому!', 'Dark ice underfoot. A crack like a gunshot. Back — onto the white!', 2.6),
        ]})
    return zs


ICE_SPOTS = [
    {'id': 'hole', 'at': LK3(-5.2, ICE_Y + 0.2, 66.5), 'where': LK(-5.6, 67.6), 'r': 0.8, 'stand': LK(-5.6, 67.7), 'face': 160,
     'label': L('Старая лунка', 'An old ice hole'),
     'cam': [LK3(-3.4, 1.0, 68.8), LK3(-5.2, -0.3, 66.4), 44],
     'beats': [
         {'anim': {'who': 'noah', 'clip': 'Crouch_Idle'}},
         note('Старая лунка, затянутая льдом. Рядом — сломанная удочка, вмёрзшая в снег.', 'An old ice-fishing hole, frozen over. Beside it — a broken rod, frozen into the snow.', 3.0),
         note('Значит, здесь лёд держал. Когда-то.', 'So the ice held here. Once.', 2.2),
         {'anim': {'who': 'noah', 'clip': 'Idle'}},
     ]},
    {'id': 'back', 'at': LK3(6.5, ICE_Y + 1.6, 52.5), 'where': LK(5.8, 52.4), 'r': 0.9, 'stand': LK(5.8, 52.4), 'face': 20,
     'label': L('Оглянуться', 'Look back'),
     'cam': [LK3(5.2, 1.3, 50.6), LK3(-14.0, 2.5, 76.0), 50],
     'beats': [
         note('Позади, у кромки леса, — пусто. Ни фонаря, ни голосов.', 'Behind them, at the edge of the forest — nothing. No flashlight, no voices.', 2.8),
         note('Только их следы — цепочкой, по белому. Как по нитке.', 'Only their footprints — a single line across the white. Like following a thread.', 2.8),
     ]},
    {'id': 'boat', 'at': LK3(-6.5, 0.6, 17.6), 'where': LK(-4.0, 19.0), 'r': 0.8, 'stand': LK(-4.0, 19.1), 'face': -110,
     'label': L('Лодка у берега', 'The boat by the shore'),
     'cam': [LK3(-2.4, 1.3, 20.6), LK3(-6.5, 0.4, 17.6), 44],
     'beats': [
         note('Лодка вверх дном, вмёрзла у берега. На борту — облезлая краска, буквы не разобрать. Только первую: «А».',
              'A boat, upside down, frozen in by the shore. Peeling paint on the side — the letters are illegible. Only the first: “A.”', 3.6),
     ]},
]

ICE = {
    'who': 'noah',
    'start': {'at': LK3(-6.5, ICE_Y, 74.0), 'face': 180},
    'intro': [
        note('Берегом не пройти — бурелом, обрыв. Только по льду, вдоль берега. До мостков — метров семьдесят.',
             'There’s no way along the shore — windfall, a steep drop. Only across the ice, along the bank. About seventy meters to the dock.', 3.6),
        note('Белый лёд — старый, под снегом, держит. Чёрный — молодой, тонкий. По чёрному не ступать.',
             'White ice is old, under snow — it holds. Black ice is young and thin. Don’t step on the black.', 3.4),
        say('agnes', 'По белому идите. Где чёрный — не ступать. Слышите?', 'Walk on the white. Where it’s black — don’t step. You hear me?', **IF(agnes_dead='==0')),
        note('Ной идёт первым. Остальные — след в след.', 'Noah goes first. The others step in his footprints.', 2.4),
    ],
    'walk': [LK(-7.7, 8.2) + LK(16.0, 78.0)],
    'floors': [LK(-8.0, 0.0) + LK(20.0, 80.0) + [ICE_Y]],
    'block': [LK(-8.3, 16.9) + LK(-4.7, 18.3)] + [LK(*_grow(r, -0.6)[:2]) + LK(*_grow(r, -0.6)[2:]) + [{'ice_wet': '>0'}] for r, _ in PATCHES],
    'cam_block': [LK(-30.0, -10.0) + LK(-8.6, 90.0) + [0.5]],
    'nav': [LK(-6.5, 74.0), LK(-6.2, 66.0), LK(-6.0, 55.0), LK(4.2, 53.0), LK(4.2, 42.0), LK(-3.2, 38.0), LK(-3.2, 29.0), LK(2.8, 26.0),
            LK(2.8, 19.0), LK(-3.6, 19.4), LK(2.0, 10.0)],
    'zones': _ice_zones(),
    'spots': ICE_SPOTS,
    'nudge': {'after': 150, 'beats': [note('Солнце поднимается. Лёд начинает потрескивать от тепла.', 'The sun is climbing. The ice starts to tick and crackle in the warmth.', 2.8)]},
    'exit': {'id': 'pier', 'at': LK3(2.0, 0.6, 7.0), 'where': LK(2.0, 8.7), 'r': 0.8, 'stand': LK(2.0, 8.75), 'face': 180,
             'label': L('К мосткам', 'To the dock'),
             'beats': [{'dur': 0.3}]},
}

# ------------------------------------------------------- санаторий «Тихие сосны» (необязательный заезд, до сосны)
# Развилка после остановки: «Ехать дальше» (первый вариант — по умолчанию в прогонах) или свернуть в лес. Во дворе —
# доска (главврач «д-р С. Х…» — строчка оторвана: кто он, сцена у сосны скажет сама), фонтан, крыльцо, окно в коридор;
# через окно пристройки — архив: стеллажи, картотека (карточка Ноя: «8 лет… после несчастного случая на озере», свежими
# чернилами — «Отец умер. Возобновить»), папка Анны на столе (боится мужа, хочет уйти с детьми; следующий лист вырван —
# что Хейл сделал дальше, скажет Агнес в главе 5), папка Зои. Забрать папки — одна улика (ev_archive) в финале.
def S2(x, z):   # местные координаты санатория → мир (Ch4.SANA = (480, 0, −60))
    return [round(x + 480.0, 3), round(z - 60.0, 3)]


def S3(x, y, z):
    return [round(x + 480.0, 3), y, round(z - 60.0, 3)]


def SBOX(x0, z0, x1, z1, *cond):
    return S2(x0, z0) + S2(x1, z1) + list(cond)


FORK = {'c4_s4_road': {'beats': [
    {'fx': 'sana_fork', 'shot': 'van_road', **note('Снова в пути. Мотор тянет ровно — шланг держит.', 'Back on the road. The engine pulls steadily — the hose is holding.', 2.6)},
    {'ask': {'id': 'road4', 'max': 2, 'opts': [
        {'ru': '«Бабушка, какой была мама?»', 'en': '“Grandma, what was Mom like?”', 'beats': [
            say('noah', 'Бабушка… а какой она была? Мама.', 'Grandma… what was she like? Mom.', shot='noah_van'),
            say('agnes', 'Упрямой. В восемь лет ушла пешком в город — за котёнком. Двенадцать вёрст. И принесла.',
                'Stubborn. At eight she walked to town on her own — for a kitten. Twelve versts. And she brought it home.', shot='agnes_van'),
            say('agnes', 'Ты в неё. Тоже упёртый.', 'You take after her. Just as pigheaded.', shot='agnes_van'),
        ]},
        {'ru': '«Дэнни, ты как?»', 'en': '“Danny, you okay?”', 'if': DANNY_IN, 'beats': [
            say('noah', 'Дэнни, ты как там?', 'Danny, you doing okay back there?', shot='noah_van'),
            say('danny', 'Трясёт, как на тракторе. Мне нравится.', 'It shakes like a tractor. I like it.', shot='danny_van'),
        ]},
        {'ru': '«Зоя, ты в порядке?»', 'en': '“Zoe, are you all right?”', 'if': ZOE_IN, 'beats': [
            say('noah', 'Зоя, ты в порядке?', 'Zoe, are you all right?', shot='noah_van'),
            say('zoe', 'Нет. Но спасибо, что спросил. Это редкость.', 'No. But thanks for asking. That’s rare.', shot='zoe_van'),
        ]},
        {'ru': '«Айрис, поспи»', 'en': '“Iris, get some sleep”', 'beats': [
            say('noah', 'Айрис, поспи. Я разбужу.', 'Iris, get some sleep. I’ll wake you.', shot='noah_van'),
            say('iris', 'Не разбудишь. Ты сам за рулём «выпадаешь» — я видела.', 'You won’t. You blank out at the wheel yourself — I’ve seen it.', shot='iris_van'),
        ]},
        {'leave': True, 'ru': 'Смотреть на дорогу', 'en': 'Watch the road', 'beats': [
            note('Ели, снег, ели. Буханку покачивает на колеях.', 'Firs, snow, firs. The van rocks in the ruts.', 2.4, shot='van_road'),
        ]},
    ]}},
    {'fx': 'sana_stop', 'shot': 'sana_turn', **note('Ной притормаживает. Тот самый указатель — у поворота в лес. «Тихие сосны», два километра.',
                                 'Noah slows down. That same sign — by a turnoff into the woods. “Quiet Pines,” two kilometers.', 3.2, **IF(c4_sign='>0'))},
    {'fx': 'sana_stop', 'shot': 'sana_turn', **note('У поворота в лес — ржавый указатель: «Санаторий „Тихие сосны“». Агнес просит притормозить.',
                                 'By a turnoff into the woods — a rusty sign: “Quiet Pines Sanatorium.” Agnes asks him to slow down.', 3.4, **IF(c4_sign='==0'))},
    say('agnes', 'Он ничего не выбрасывает. Если где и лежит правда про Аню — то там.', 'He never throws anything away. If the truth about Annie is anywhere — it’s there.',
        shot='agnes_van'),
    say('zoe', 'Только без меня. Я туда не вернусь. Ни за что.', 'Count me out. I’m not going back in there. No way.', shot='zoe_van', **IF(**ZOE_IN)),
    {'choice': {'t': 10, 'shot': 'noah_van', 'opts': [
        {'ru': 'Ехать дальше — к озеру', 'en': 'Keep driving — to the lake', 'go': 'c4_s5'},
        {'ru': 'Свернуть к санаторию', 'en': 'Turn off to the sanatorium', 'go': 'c4_sana'},
        {'silent': True, 'go': 'c4_s5'}]}},
]}}

SANA_SC = {'c4_sana': {'beats': [
    {'fx': 'sana_arrive', 'shot': 'sana_gate', **note('Лесная дорога упирается в ворота. За ними — белый корпус в два этажа, все окна заколочены.',
                                                      'The forest road ends at a gate. Beyond it — a white two-story building, every window boarded up.', 3.4)},
    note('На воротах цепь. Снег во дворе нетронутый — сюда не приезжали с осени.', 'There’s a chain on the gate. The snow in the yard is untouched — no one’s been here since fall.', 3.0, shot='sana_gate'),
    say('iris', 'Я с тобой.', 'I’m coming with you.', shot='iris_van'),
    say('noah', 'Стой у ворот. Если кто поедет — кричи.', 'Wait by the gate. If anyone comes — shout.', shot='noah_van'),
    {'fx': 'sana_gate_out', 'shot': 'sana_gap', **note('Одна створка провисла на петлях. Ной протискивается боком.', 'One side of the gate sags on its hinges. Noah squeezes through sideways.', 2.8)},
    {'explore': 'c4s'},
    {'fx': 'sana_board', 'shot': 'sana_window', **note('Окно пристройки. Доска держится на одном гвозде.', 'A window in the annex. The board is hanging by a single nail.', 2.6)},
    {'qte': 'mash', 't': 6.0, 'n': 12, 'sfx': 'none', 'hint': L('Отдирай доску — быстро жми E / пробел', 'Pry the board off — mash E / Space')},
    {'fx': 'sana_in', 'shot': 'sana_inside', **note('Доска поддаётся. Ной перелезает через подоконник.', 'The board gives way. Noah climbs over the sill.', 2.6)},
    note('Архив. Пахнет бумагой и мышами. Свет — только из окна.', 'The archive. It smells of paper and mice. The only light comes from the window.', 3.0, shot='sana_inside'),
    {'explore': 'c4a'},
    {'fx': 'sana_back', 'shot': 'sana_back', **note('Айрис так и простояла всё это время у ворот.', 'Iris has been standing by the gate the whole time.', 2.6)},
    say('iris', 'Ну что там?', 'Well? What’s in there?', shot='sana_back'),
    say('noah', 'Мама. Потом расскажу. Поехали.', 'Mom. I’ll tell you later. Let’s go.', shot='sana_back', **IF(ev_archive='>0')),
    say('noah', 'Ничего. Поехали.', 'Nothing. Let’s go.', shot='sana_back', **IF(ev_archive='==0')),
    note('Ной прячет папки под сиденье буханки.', 'Noah tucks the folders under the van’s seat.', 2.4, shot='sana_back', **IF(ev_archive='>0')),
    {'fx': 'fade_out', 'dur': 1.3},
    {'fx': 'sana_out'},
    {'fx': 'fade_in'},
    {'go': 'c4_s5'},
]}}

YARD_SPOTS = [
    {'id': 'board', 'at': S3(-4.0, 1.5, 4.05), 'where': S2(-3.2, 5.0), 'r': 0.6, 'stand': S2(-3.15, 5.0), 'face': -138,
     'label': L('Доска объявлений', 'The notice board'),
     'cam': [S3(-5.2, 1.6, 6.0), S3(-4.0, 1.45, 4.0), 34],
     'beats': [
         note('Под мутным стеклом — выгоревший лист: «Санаторий закрыт на зимний период. Администрация».',
              'Under the cloudy glass — a faded sheet: “The sanatorium is closed for the winter. The Management.”', 3.2),
         note('Ниже — список врачей. Верхнюю строчку кто-то оторвал. Осталось: «Главный врач — д-р С. Х…»',
              'Below — a list of doctors. Someone tore off the top line. What’s left: “Head physician — Dr. S. H…”', 3.4),
         note('С. Х. Как подпись на записке в машине.', 'S. H. Like the signature on the note in the car.', 2.6),
     ]},
    {'id': 'fountain', 'at': S3(0.0, 0.9, 2.5), 'where': S2(-0.9, 4.65), 'r': 0.7, 'stand': S2(-0.9, 4.7), 'face': 170,
     'label': L('Фонтан', 'The fountain'),
     'cam': [S3(1.9, 1.35, 6.2), S3(0.3, 0.45, 4.2), 36],
     'beats': [
         note('Фонтан. В чаше — снег вровень с краями.', 'A fountain. The basin is filled with snow to the brim.', 2.4),
         note('На бортике, под коркой льда, кто-то выцарапал: «Я НЕ СУМАСШЕДШАЯ».', 'On the rim, under a crust of ice, someone has scratched: “I AM NOT CRAZY.”', 3.2),
     ]},
    {'id': 'porch', 'at': S3(0.0, 1.6, -5.9), 'where': S2(-1.3, -2.8), 'r': 0.7, 'stand': S2(-1.3, -2.75), 'face': 165,
     'label': L('Главный вход', 'The main entrance'),
     'cam': [S3(0.9, 1.55, -1.0), S3(-0.05, 2.25, -5.95), 50],
     'beats': [
         note('Главный вход заколочен крест-накрест. Над дверью: «Лечебный корпус».', 'The main entrance is boarded up crosswise. Above the door: “Treatment Building.”', 3.0),
         note('Из щели под дверью тянет холодом и хлоркой. Даже сейчас.', 'From the gap under the door comes a draft of cold and bleach. Even now.', 2.8),
     ]},
    {'id': 'ward', 'at': S3(-8.25, 1.7, -5.95), 'where': S2(-8.25, -4.95), 'r': 0.6, 'stand': S2(-8.25, -4.9), 'face': 180,
     'label': L('Окно без досок', 'The unboarded window'),
     'cam': [S3(-6.7, 1.75, -3.3), S3(-8.3, 1.65, -6.0), 40],
     'beats': [
         note('С этого окна доски сорваны. Внутри — длинный коридор и ряд одинаковых дверей.', 'The boards on this window have been torn off. Inside — a long corridor and a row of identical doors.', 3.2),
         note('На каждой двери — глазок. Снаружи.', 'Every door has a peephole. On the outside.', 2.6),
     ]},
]

YARD = {
    'who': 'noah',
    'start': {'at': S3(0.8, 0.0, 8.0), 'face': 180},
    'intro': [note('Двор. Так тихо, что слышно, как с сосен осыпается снег.', 'The yard. So quiet you can hear snow sliding off the pines.', 2.8)],
    'walk': [SBOX(-12.5, -5.5, 11.7, 9.6)],
    'block': [
        SBOX(-2.65, -6.0, 2.65, -3.0),          # крыльцо с колоннами
        SBOX(-1.75, 0.75, 1.75, 4.25),          # фонтан
        SBOX(-4.75, 3.9, -3.25, 4.1),           # доска объявлений
        SBOX(-3.35, 6.35, -3.05, 6.65), SBOX(3.05, 6.35, 3.35, 6.65),   # фонари
        SBOX(-6.8, 0.2, -6.2, 1.8), SBOX(6.2, 0.2, 6.8, 1.8),           # скамейки
        SBOX(0.3, 8.7, 1.5, 9.6), SBOX(1.5, 9.2, 2.6, 9.6),             # провисшая створка ворот
    ],
    'cam_block': [SBOX(-13.1, -18.1, 13.1, -5.95, 99.0), SBOX(11.95, -6.05, 20.05, 1.55, 3.7)],
    'nav': [S2(0.8, 8.0), S2(3.0, 6.0), S2(-3.0, 6.0), S2(-3.15, 5.0), S2(-0.9, 4.7), S2(-3.0, -0.6), S2(3.0, -0.6),
            S2(-1.3, -2.75), S2(-5.5, -3.4), S2(-8.25, -4.9), S2(5.5, -3.0), S2(9.0, -2.0), S2(11.1, -1.0)],
    'spots': YARD_SPOTS,
    'nudge': {'after': 150, 'beats': [note('От ворот свистит Айрис: долго ещё?', 'Iris whistles from the gate: how much longer?', 2.4)]},
    'exit': {'id': 'window', 'at': S3(12.0, 1.5, -1.0), 'where': S2(11.15, -1.0), 'r': 0.6, 'stand': S2(11.1, -1.0), 'face': 90,
             'label': L('Окно архива', 'The archive window'),
             'beats': [{'dur': 0.3}]},
}

ARCH_SPOTS = [
    {'id': 'rows', 'at': S3(16.5, 1.6, -5.3), 'where': S2(16.5, -4.6), 'r': 0.7, 'stand': S2(16.5, -4.55), 'face': 180,
     'label': L('Стеллажи', 'The shelves'),
     'cam': [S3(17.7, 1.75, -2.5), S3(16.4, 1.3, -5.4), 46],
     'beats': [
         note('Стеллажи от пола до потолка. На корешках — только фамилии и годы.', 'Shelves from floor to ceiling. On the spines — only surnames and years.', 3.0),
         note('Сотни людей, которые что-то видели. И забыли.', 'Hundreds of people who saw something. And forgot.', 2.8),
     ]},
    {'id': 'cards', 'at': S3(19.35, 1.0, -4.15), 'where': S2(18.65, -3.5), 'r': 0.6, 'stand': S2(18.65, -3.5), 'face': 133,
     'label': L('Картотека', 'The card index'),
     'cam': [S3(19.5, 1.5, -2.35), S3(19.1, 0.75, -4.2), 42],
     'beats': [
         {'anim': {'who': 'noah', 'clip': 'Interact'}},
         note('Картотека. Один ящик выдвинут — его открывали недавно.', 'A card index. One drawer is pulled out — someone opened it recently.', 2.8),
         note('Сверху — карточка: «Ной, 8 лет. Поступил после несчастного случая на озере. Кричит по ночам. Зовёт маму».',
              'On top — a card: “Noah, age 8. Admitted after an accident at the lake. Screams at night. Calls for his mother.”', 3.8),
         note('Ниже — назначения, год за годом, одним почерком. Последняя строка — свежими чернилами: «Отец умер. Возобновить».',
              'Below — prescriptions, year after year, in the same handwriting. The last line, in fresh ink: “Father dead. Resume.”', 3.8),
         {'set': {'c4_card': 1}, 'anim': {'who': 'noah', 'clip': 'Idle'}},
     ]},
    {'id': 'anna', 'at': S3(15.0, 0.95, -3.42), 'where': S2(15.85, -3.0), 'r': 0.6, 'stand': S2(15.85, -3.0), 'face': -110,
     'label': L('Папка на столе', 'The folder on the desk'),
     'cam': [S3(13.4, 1.85, -2.0), S3(15.1, 0.83, -3.45), 42],
     'beats': [
         note('На столе — одна папка. Не на полке: её доставали недавно. Пыли нет.', 'On the desk — a single folder. Not on a shelf: someone took it out recently. No dust.', 3.0),
         note('На обложке — мамино имя.', 'On the cover — Mom’s name.', 2.2),
         say('noah', 'Мама…', 'Mom…'),
         note('Одна запись — за неделю до озера: «Жалобы: бессонница, страх. Боится мужа. Хочет уйти от него вместе с детьми».',
              'A single entry — a week before the lake: “Complaints: insomnia, fear. Afraid of her husband. Wants to leave him and take the children.”', 4.0),
         note('Следующий лист вырван. Остался только корешок.', 'The next page has been torn out. Only the stub is left.', 2.6),
         {'set': {'c4_anna': 1}},
     ],
     'ask': {'max': 1, 'opts': [
         {'ru': 'Забрать папку с собой', 'en': 'Take the folder', 'beats': [
             note('Ной прячет папку под куртку.', 'Noah tucks the folder under his jacket.', 2.2),
             note('И свою карточку — тоже.', 'His own card too.', 2.0, **IF(c4_card='>0')),
             {'set': {'ev_archive': 1}},
         ]},
         {'ru': 'Положить, как лежала', 'en': 'Put it back the way it was', 'beats': [
             note('Ной кладёт папку на место. Ровно так, как лежала. Пусть думает, что сюда никто не заходил.',
                  'Noah puts the folder back. Exactly the way it was. Let him think no one’s been here.', 3.2),
         ]},
     ]}},
    {'id': 'zoe', 'if': {'zoe': '>0'}, 'at': S3(17.4, 1.2, 0.75), 'where': S2(17.4, 0.0), 'r': 0.6, 'stand': S2(17.4, 0.05), 'face': 0,
     'label': L('Знакомое имя', 'A familiar name'),
     'cam': [S3(15.7, 1.7, -1.9), S3(17.4, 1.1, 0.8), 42],
     'beats': [
         note('Среди папок — одна с именем, которое Ной знает: «Зоя».', 'Among the folders — one with a name Noah knows: “Zoe.”', 2.6),
         note('«Поступила по просьбе матери. Видит то, чего не было». На выписке — два слова: «Не помнит».',
              '“Admitted at her mother’s request. Sees things that weren’t there.” At discharge — two words: “Doesn’t remember.”', 3.6),
         note('Зоя ждёт в буханке. Ной не знает, захочет ли она это видеть.', 'Zoe is waiting in the van. Noah doesn’t know if she’d want to see this.', 2.8, **IF(**ZOE_IN)),
         note('Зоя сейчас у него. Снова.', 'Zoe is with him now. Again.', 2.4, **IF(zoe_taken='>0')),
         {'set': {'c4_zoef': 1}},
     ]},
]

ARCH = {
    'who': 'noah',
    'start': {'at': S3(12.8, 0.15, -1.0), 'face': 90},
    'walk': [SBOX(12.45, -5.55, 19.55, 1.05)],
    'block': [
        SBOX(13.7, -5.8, 19.3, -5.15),          # стеллажи у северной стены
        SBOX(13.7, 0.68, 19.3, 1.3),            # у южной
        SBOX(18.75, -4.85, 19.85, -3.9),        # картотека (с выдвинутым ящиком)
        SBOX(13.85, -3.8, 15.35, -3.0),         # стол
        SBOX(14.3, -3.0, 14.9, -2.4),           # стул
    ],
    'floors': [SBOX(12.2, -5.8, 19.8, 1.3, 0.15)],
    'cam_block': [[-999.0, -999.0, S2(12.15, 0)[0], 999.0, 99.0], [S2(19.85, 0)[0], -999.0, 999.0, 999.0, 99.0],
                  [-999.0, -999.0, 999.0, S2(0, -5.85)[1], 99.0], [-999.0, S2(0, 1.35)[1], 999.0, 999.0, 99.0]],
    'nav': [S2(12.9, -1.0), S2(16.5, -1.6), S2(16.5, -4.55), S2(19.25, -3.45), S2(15.85, -2.2), S2(15.85, -3.0), S2(17.4, 0.05)],
    'spots': ARCH_SPOTS,
    'nudge': {'after': 90, 'beats': [note('Где-то на трассе гудит мотор. Ной замирает. Звук уходит дальше — не сюда. Пора.',
                                         'Somewhere out on the highway, an engine drones. Noah freezes. The sound moves on — not this way. Time to go.', 3.4)]},
    'exit': {'id': 'out', 'at': S3(12.1, 1.5, -1.0), 'where': S2(12.75, -1.0), 'r': 0.6, 'stand': S2(12.8, -1.0), 'face': -90,
             'label': L('Вылезти в окно', 'Climb out the window'),
             'beats': [{'dur': 0.3}]},
}

NEW_FLAGS = ['c4_plugs', 'c4_plugs2', 'c4_batt', 'c4_gas', 'c4_sign', 'ice_wet', 'c4_card', 'c4_anna', 'c4_zoef', 'ev_archive']


def apply(DATA):
    SC = DATA['scenes']
    ex = DATA.setdefault('explore', {})
    ex['c4v'] = VAN
    ex['c4b'] = STOP
    for f in NEW_FLAGS:
        DATA['flags'].setdefault(f, 0)
    # буханка: после «Ты ж у нас механик?» — сам: ящик, свечи, аккумулятор, бензин; потом «с толкача»
    vb = SC['c4_s1_van']['beats']
    i = next(i for i, b in enumerate(vb) if b.get('fx') == 'van_hood')
    vb[i] = {'fx': 'van_walk'}
    vb.insert(i + 1, {'explore': 'c4v'})
    vb.insert(i + 2, note('Всё на месте. Осталось только «с толкача».', 'Everything’s in place. All that’s left is a push start.', 2.6,
                          shot='van_engine'))
    # остановка: «закипает» → ждать, пока остынет (прогулка) → «Лопнул шланг…»
    sb = SC['c4_s3']['beats']
    j = next(j for j, b in enumerate(sb) if b.get('fx') == 'bus_stop')
    sb.insert(j + 1, {'fx': 'stop_walk'})
    sb.insert(j + 2, {'explore': 'c4b'})
    # избушка: прятаться (шаги, приоткрытая дверь, луч по щелям) — потом малышка (было сразу после «Спрятаться…»)
    hb = SC['c4_s6']['beats']
    k = next(k for k, b in enumerate(hb) if b.get('if') == {'baby': '>0'})
    SC['c4_s6']['beats'] = hb[:k] + [{'go': 'c4_s6_hide'}]
    SC['c4_s6_baby'] = {'beats': hb[k:]}
    for kk, v in HIDE_SCENES.items():
        SC[kk] = v
    # лёд: «Белая гладь…» → вдоль берега по льду к мосткам (прогулка) → все у мостков → «На берегу — старая дача…»
    lb = SC['c4_s7']['beats']
    m = next(m for m, b in enumerate(lb) if b.get('fx') == 'fade_in')
    lb.insert(m + 1, {'fx': 'ice_walk'})
    lb.insert(m + 2, {'explore': 'c4i'})
    lb.insert(m + 3, {'fx': 'ice_done'})
    lb.insert(m + 4, note('Ной стучит зубами. Штаны — ледяная корка.', 'Noah’s teeth are chattering. His trousers are a crust of ice.', 2.6, **IF(ice_wet='>0')))
    ex['c4i'] = ICE
    # санаторий: после разговора у мотора (или сразу после ремонта) — развилка; свернуть — двор и архив, потом к сосне
    for sid in ['c4_s4', 'c4_s4_thanks', 'c4_s4_secret', 'c4_s4_truth', 'c4_s4_quiet']:
        for b in SC[sid]['beats']:
            if b.get('go') == 'c4_s5':
                b['go'] = 'c4_s4_road'
            if b.get('else') == 'c4_s5':
                b['else'] = 'c4_s4_road'
    SC.update(FORK)
    SC.update(SANA_SC)
    ex['c4s'] = YARD
    ex['c4a'] = ARCH
    return DATA
