#!/usr/bin/env python3
"""WHAT THE LAKE KEEPS, глава 2 — дополнения по мастер-промпту редактора (3 ноя):
  * прогулка по кафе «У трёх сосен» (`c3p`): после туалета (утверждённая сцена) Ной сам ходит по залу — дальнобойщики,
    официантка Нина, телевизор (там его фото), окно на стоянку, Айрис с Евой, Дэнни; выход — «Сесть за стол» (дальше
    чёрный седан, c2_s5) или «Выйти к Зое на крыльцо» (утверждённая сцена c2_s4_z). Утверждённый порядок событий
    кафе не меняется: Ева → Дэнни → туалет → (крыльцо) → седан;
  * новая сцена «Буран» (`c2_storm…`): на боковой дороге машина встаёт в белой мгле, мотор работает ради печки —
    трубу заметает, тянет выхлопом; Ной глушит мотор, выходит почти вслепую (прогулка `storm` вокруг машины)
    и откапывает трубу; в салоне тем временем разговор; потом — поймать волну со сводкой о дорогах (действие «tune»).
    Встаёт между «Машина ползёт по боковой дороге…» и «В зеркале… две фары» (конец главы).
Новые отметки — только свои (на доверие, «Тень», таблетки, улики и концовки не влияют):
  porch, cafe_tv, coffee_zoe, cafe_paid, storm_slip, storm_tool, frost, storm_radio.
Правит godot/lake/data/ch2.json на месте (повторный запуск ничего не дублирует):
    python3 tools/lake/story/ch2_walk.py
"""
import json, os, sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
DATA = os.path.join(ROOT, 'godot/lake/data/ch2.json')
F = 0.45   # пол кафе (DriveScene.CF)
NEW_FLAGS = ['porch', 'cafe_tv', 'coffee_zoe', 'cafe_paid', 'storm_slip', 'storm_tool', 'frost', 'storm_radio']


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


ZOE_IN = {'zoe': '>0', 'zoe_gone': '==0'}

# ---------------------------------------------------------------------------------------------------------- кафе
CAFE_SPOTS = [
    {'id': 'truckers', 'at': [30.12, F + 1.5, -8.4], 'where': [29.72, -6.9], 'r': 1.3, 'stand': [29.72, -6.9], 'face': 160,
     'label': L('Подсесть к дальнобойщикам', 'Join the truckers'),
     'cam': [[31.45, F + 1.6, -4.8], [30.0, F + 1.18, -8.8], 48],   # из-за стойки: лица дальнобойщиков, Ной сбоку
     'beats': [
         {'look': {'who': 'noah', 'at': 'trucker3'}},
         note('Трое у стойки — над тарелками, в тулупах нараспашку. Пахнет мокрой овчиной и луком.',
              'Three men at the counter, hunched over their plates, sheepskins hanging open. It smells of wet fleece and onions.', 3.4,
              look={'who': 'trucker3', 'at': 'noah'}),
     ],
     'ask': {'max': 2, 'opts': [
         {'ru': '«Что с трассой?»', 'en': '“What’s going on with the highway?”', 'beats': [
             say('noah', 'Что с трассой?', 'What’s going on with the highway?'),
             say('trucker', 'Фура на сорок шестом сложилась. Поперёк дороги. До утра там никто не проедет.',
                 'A rig jackknifed at kilometer forty-six. Right across the road. Nobody’s getting through till morning.', lips='trucker3'),
             say('trucker_b', 'А нам и не надо. Мы тут ночуем.', 'Suits us. We’re spending the night.', lips='trucker2'),
         ]},
         {'ru': '«А в объезд?»', 'en': '“Is there a way around?”', 'beats': [
             say('noah', 'А в объезд?', 'Is there a way around?'),
             say('trucker', 'Направо есть боковая, через деревни. Если не заметёт.',
                 'There’s a side road to the right, through the villages. If it doesn’t drift over.', lips='trucker3'),
             say('trucker', 'Я бы в буран не совался. Встанешь в поле — никто не найдёт.',
                 'I wouldn’t try it in a blizzard. Get stuck out in the fields and nobody’ll find you.', lips='trucker3'),
         ]},
         {'ru': '«Вы с севера?»', 'en': '“You coming from the north?”', 'beats': [
             say('noah', 'Вы с севера?', 'You coming from the north?'),
             say('trucker', 'С севера. Там сейчас хуже, чем тут.', 'From the north. It’s worse up there than here.', lips='trucker3'),
             say('trucker_b', 'Озёра в этом году встали рано. Лёд чёрный.', 'The lakes froze early this year. Black ice.', lips='trucker2'),
             say('trucker_b', 'Чёрный — красивый. Только не держит.', 'Black ice is pretty. It just won’t hold you.', lips='trucker2'),
             note('Ной не понимает, почему от этих слов у него немеет затылок.',
                  'Noah doesn’t understand why those words make the back of his neck go numb.', 3.0, fx='hum_spike',
                  **IF(pills2='==0')),
         ]},
         {'leave': True, 'ru': 'Отойти', 'en': 'Step away', 'beats': [{'dur': 0.4}]},
     ]},
     'after': [{'look': {'who': 'trucker3', 'at': None}}, {'look': {'who': 'noah', 'at': None}}]},

    {'id': 'nina', 'at': [31.75, F + 1.85, -6.6], 'where': [29.72, -5.4], 'r': 1.3, 'stand': [29.72, -5.4], 'face': 110,
     'label': L('Подойти к стойке', 'Go to the counter'),
     'cam': [[28.9, F + 1.65, -4.6], [31.7, F + 1.45, -6.6], 38],   # из-за плеча Ноя: Нина за стойкой
     'beats': [
         {'look': {'who': 'noah', 'at': 'waitress'}},
         say('waitress', 'Ещё чаю, милый?', 'More tea, love?', look={'who': 'waitress', 'at': 'noah'}),
     ],
     'ask': {'max': 2, 'opts': [
         {'ru': '«Два кофе»', 'en': '“Two coffees”', 'if': ZOE_IN, 'beats': [
             say('noah', 'Два кофе. Один — с собой, на крыльцо.', 'Two coffees. One to go — out on the porch.'),
             note('Нина наливает в толстые белые кружки. Себе Ной не берёт.',
                  'Nina pours into thick white mugs. Noah doesn’t take one for himself.', 2.8, set={'coffee_zoe': 1}),
             say('noah', 'Обещал.', 'I promised.'),
         ]},
         {'ru': '«Кофе»', 'en': '“Coffee”', 'if': {'zoe': '==0'}, 'beats': [
             say('noah', 'Кофе. Если можно.', 'Coffee. If that’s all right.'),
             note('Нина наливает в толстую белую кружку. Кофе горький, пережжённый. Ной пьёт до дна.',
                  'Nina pours it into a thick white mug. The coffee is bitter and burnt. Noah drinks it to the bottom.', 3.2),
         ]},
         {'ru': '«Сколько с нас?»', 'en': '“What do we owe you?”', 'beats': [
             say('noah', 'Сколько с нас?', 'What do we owe you?'),
             say('waitress', 'Сто сорок. Да не спеши, буран всё равно никого не выпустит.',
                 'A hundred and forty. No rush, though — the blizzard won’t let anybody out.'),
             note('Ной выкладывает на стойку последние деньги. Нина не пересчитывает.',
                  'Noah lays the last of his money on the counter. Nina doesn’t count it.', 2.8, set={'cafe_paid': 1}),
         ]},
         {'ru': '«Тут всегда так пусто?»', 'en': '“Is it always this empty?”', 'beats': [
             say('noah', 'Тут всегда так пусто?', 'Is it always this empty?'),
             say('waitress', 'Зимой — да. Летом рыбаки едут на озёра, не протолкнёшься.',
                 'In winter, yes. In summer the fishermen head up to the lakes — you can’t squeeze through.'),
             say('waitress', 'А зимой только эти. Да такие, как вы. Кому очень надо на север.',
                 'In winter it’s only this lot. And folks like you. The ones who really need to get north.',
                 look={'who': 'waitress', 'at': 'trucker2'}),
         ]},
         {'leave': True, 'ru': 'Отойти', 'en': 'Step away', 'beats': [{'dur': 0.4}]},
     ]},
     'after': [{'look': {'who': 'waitress', 'at': 'trucker3'}}, {'look': {'who': 'noah', 'at': None}}]},

    {'id': 'tv', 'at': [33.35, F + 2.3, 0.4], 'where': [31.2, -1.6], 'r': 1.4, 'stand': [31.2, -1.6], 'face': 47,
     'label': L('Посмотреть телевизор', 'Look at the TV'),
     'cam': [[30.3, F + 1.55, -2.6], [33.3, F + 2.55, 0.35], 36],   # из-за спины Ноя — экран под потолком
     'beats': [
         {'look': {'who': 'noah', 'at': [33.35, F + 2.3, 0.4]}},
         note('Телевизор под потолком, звук выключен. Новости.', 'A TV up by the ceiling, sound off. The news.', 2.4),
         note('Его школьная фотография. Красная плашка: «Разыскивается».',
              'His school photo. A red banner: “Wanted.”', 2.8, cam=[[32.4, F + 2.2, -0.55], [33.35, F + 2.6, 0.4], 28]),
         note('Ной оглядывается. На экран никто не смотрит. Пока.',
              'Noah looks around. Nobody’s watching the screen. Not yet.', 2.8,
              cam=[[33.0, F + 2.3, -0.2], [27.0, F + 1.0, -6.0], 52]),
     ],
     'ask': {'max': 1, 'opts': [
         {'ru': 'Выключить', 'en': 'Turn it off', 'beats': [
             note('Ной тянется и жмёт кнопку на корпусе. Экран гаснет.',
                  'Noah reaches up and presses the button on the set. The screen goes dark.', 2.6, fx='tv_off', set={'cafe_tv': 1}),
             say('trucker_b', 'Э, парень. Мы погоду смотрели.', 'Hey, kid. We were watching the weather.', lips='trucker2'),
             say('noah', 'Там одно и то же. Буран.', 'It’s all the same. Blizzard.'),
             say('trucker_b', '…Тоже верно.', '…Fair enough.', lips='trucker2'),
         ]},
         {'ru': 'Оставить', 'en': 'Leave it on', 'beats': [
             note('Фото держится на экране целую вечность. Потом — карта погоды: над всей областью белое пятно.',
                  'The photo stays on screen for an eternity. Then the weather map: a white blot over the whole region.', 3.6,
                  fx='tv_weather', cam=[[32.4, F + 2.2, -0.55], [33.35, F + 2.6, 0.4], 28]),
         ]},
     ]},
     'after': [{'look': {'who': 'noah', 'at': None}}]},

    {'id': 'window', 'at': [22.15, F + 1.6, -5.0], 'where': [22.9, -5.0], 'r': 1.2, 'stand': [22.9, -5.0], 'face': -90,
     'label': L('Посмотреть в окно', 'Look out the window'),
     'cam': [[22.6, F + 1.45, -4.3], [17.2, 0.8, -11.6], 46],   # глазами Ноя у стекла — стоянка в снегу (спина Ноя не в кадре)
     'beats': [
         {'anim': {'who': 'noah', 'clip': 'Rk_Watching'}},
         note('Снег идёт стеной. Фуры у входа — уже по колёса в снегу.',
              'The snow comes down like a wall. The rigs out front are already buried to their wheels.', 3.0),
         note('Их седан у крыльца. На капоте — ладонь снега.', 'Their sedan by the porch. A hand’s depth of snow on the hood.', 2.6),
         note('Въезд пустой. Только снег.', 'The entrance to the lot is empty. Just snow.', 2.4),
         note('В углу Ева тоже смотрит в окно — на свою машину в канаве.',
              'In the corner, Eva is looking out the window too — at her car in the ditch.', 2.8,
              cam=[[24.0, F + 1.45, -12.2], [23.0, F + 1.1, -14.0], 46], look={'who': 'eva', 'at': [19.0, 0.4, -16.6]}),
         {'anim': {'who': 'noah', 'clip': 'Idle'}, 'look': {'who': 'eva', 'at': 'iris'}},
     ]},

    {'id': 'iris_eva', 'at': [23.3, F + 1.35, -14.2], 'where': [24.35, -13.0], 'r': 1.2, 'stand': [24.35, -13.0], 'face': -137,
     'label': L('Подойти к Айрис и Еве', 'Go over to Iris and Eva'),
     'cam': [[24.0, F + 1.45, -12.2], [23.0, F + 1.0, -14.0], 46],
     'beats': [
         {'look': {'who': 'noah', 'at': 'iris'}},
         note('Айрис прикрывает блокнот локтем.', 'Iris covers her notebook with her elbow.', 2.2, look={'who': 'iris', 'at': 'noah'}),
     ],
     'ask': {'max': 1, 'opts': [
         {'ru': '«Покажи»', 'en': '“Show me”', 'beats': [
             say('noah', 'Покажи.', 'Show me.'),
             {'dur': 1.0},
             note('Айрис убирает локоть. Ева — в профиль, рука на животе, кольцо на цепочке.',
                  'Iris moves her elbow away. Eva, in profile — a hand on her belly, the ring on its chain.', 3.2),
             note('Рядом — вторая женщина. Без лица: только волосы и руки.',
                  'Next to her, a second woman. No face — just hair and hands.', 2.8),
             say('iris', 'Это не мама. Просто… женщина.', 'It’s not Mom. It’s just… a woman.'),
             say('eva', 'Хорошо рисует. Я в её годы только лошадей умела.',
                 'She draws well. At her age all I could do was horses.', look={'who': 'eva', 'at': 'noah'}),
         ]},
         {'ru': '«Не буду мешать»', 'en': '“I won’t bother you”', 'beats': [
             say('noah', 'Не буду мешать.', 'I won’t bother you.'),
             say('eva', 'Оставь её. Нам хорошо.', 'Leave her be. We’re fine.', look={'who': 'eva', 'at': 'noah'}),
             note('Айрис не поднимает головы. Карандаш снова шуршит.', 'Iris doesn’t look up. The pencil starts scratching again.', 2.6,
                  fx='snd:pencil_scribble:-16'),
         ]},
     ]},
     'after': [{'look': {'who': 'eva', 'at': 'iris'}}, {'look': {'who': 'iris', 'at': 'eva'}}, {'look': {'who': 'noah', 'at': None}}]},

    {'id': 'danny', 'if': {'danny': '>0'}, 'at': [23.42, F + 1.2, -1.82], 'where': [24.4, -1.6], 'r': 1.15, 'stand': [24.35, -1.75],
     'face': -90, 'label': L('Подсесть к Дэнни', 'Sit with Danny'),
     # Ной присаживается на корточки у края скамьи — голова вровень с Дэнни; камера через стол, с пустого места Ноя
     'cam': [[23.2, F + 1.1, -3.2], [23.75, F + 0.95, -1.75], 44],
     'beats': [
         {'anim': {'who': 'noah', 'clip': 'Crouch_Idle'}, 'look': {'who': 'noah', 'at': 'danny'}},
         note('Дэнни отодвигается к стенке.', 'Danny shifts away toward the wall.', 2.2, **IF(danny_fear='>0')),
         note('Дэнни двигает к Ною тарелку с картошкой.', 'Danny pushes his plate of fries toward Noah.', 2.4, **IF(promise='>0')),
         note('Дэнни рисует на салфетке.', 'Danny is drawing on a napkin.', 2.2, **IF(danny_fear='==0', promise='==0')),
     ],
     'ask': {'max': 2, 'opts': [
         {'ru': '«Что рисуешь?»', 'en': '“What are you drawing?”', 'beats': [
             say('noah', 'Что рисуешь?', 'What are you drawing?'),
             say('danny', 'Дом. Маму. И рацию — чтобы она услышала.', 'A house. Mom. And the walkie-talkie — so she can hear.',
                 look={'who': 'danny', 'at': 'noah'}),
         ]},
         {'ru': '«Мама тебя найдёт»', 'en': '“Your mom will find you”', 'beats': [
             say('noah', 'Мама тебя найдёт.', 'Your mom will find you.'),
             say('danny', 'А если мы уедем далеко?', 'What if we go really far?'),
             say('noah', 'Мы ей скажем, где ты.', 'We’ll tell her where you are.'),
             note('Дэнни кивает, будто это договор.', 'Danny nods, as if it’s a deal.', 2.2),
         ]},
         {'leave': True, 'ru': 'Отойти', 'en': 'Step away', 'beats': [{'dur': 0.4}]},
     ]},
     'after': [{'look': {'who': 'danny', 'at': 'iris'}}, {'anim': {'who': 'noah', 'clip': 'Idle'}, 'look': {'who': 'noah', 'at': None}}]},

    {'id': 'porch', 'if': ZOE_IN, 'exit': True, 'at': [22.1, F + 2.0, -7.0], 'where': [22.75, -7.0], 'r': 1.1, 'stand': [22.75, -7.0],
     'face': -90, 'label': L('Выйти к Зое на крыльцо', 'Go out to Zoe on the porch'),
     'beats': [{'set': {'porch': 1}, 'fx': 'snd:door_chime:-12', 'dur': 0.5}]},
]

CAFE = {
    'who': 'noah',
    'start': {'at': [28.85, F, -13.3], 'face': -60},   # из двери туалета — лицом в зал
    'intro': [
        note('Ной выходит из туалета. Руки ещё мокрые.', 'Noah comes out of the restroom. His hands are still wet.', 2.4, shot='cafe_wide'),
        note('За окном, на крыльце, — огонёк сигареты. Зоя.', 'Out the window, on the porch — the glow of a cigarette. Zoe.', 2.6,
             shot='cafe_window_porch', **{'if': ZOE_IN}),
    ],
    'hum': {'if': {'pills2': '==0'}, 'v': 0.22},
    'watch': ['iris', 'eva', 'danny', 'waitress'],
    'walk': [[22.4, -12.5, 30.5, 0.65], [22.4, -14.65, 29.25, -12.5], [30.5, -2.9, 33.6, 0.65]],
    'block': [
        [22.3, -14.55, 23.85, -9.95],      # кабинки у окна (Ева с Айрис, пустая)
        [22.3, -3.75, 23.85, 0.85],        # кабинки: наш стол (Дэнни, Зоя) и пустая
        [30.55, -12.6, 34.0, -2.95],       # стойка, за ней Нина и полки
        [29.7, -11.9, 30.55, -7.95],       # дальнобойщики на табуретах
        [29.95, -7.1, 30.3, -6.7], [29.95, -5.6, 30.3, -5.2], [29.95, -4.1, 30.3, -3.7],   # свободные табуреты
        [22.2, -9.3, 22.55, -8.0],         # вешалка у двери
        [29.3, -15.0, 34.0, -12.5],        # туалет
        [33.42, -3.28, 34.0, -1.52],       # полка раздачи у окна на кухню (на уровне груди; срез на высоте тела, 5 ноя)
    ],
    'floors': [[22.0, -15.0, 34.0, 1.0, F]],
    # камера не выходит из зала (стены) и не заходит в туалет; кабинки и стойка ниже камеры — она смотрит поверх
    'cam_block': [[10.0, -30.0, 22.15, 10.0, 99.0], [33.85, -30.0, 45.0, 10.0, 99.0], [10.0, -30.0, 45.0, -14.85, 99.0],
                  [10.0, 0.85, 45.0, 10.0, 99.0], [29.3, -15.0, 34.0, -12.5, 99.0],
                  [22.3, -14.55, 23.85, -9.95, 1.7], [22.3, -3.75, 23.85, 0.85, 1.7], [30.55, -12.6, 33.85, -2.95, 1.1]],
    'nav': [[24.3, -12.4], [24.3, -8.0], [24.3, -4.6], [24.3, -1.0], [27.0, -12.0], [27.0, -8.0], [27.0, -4.0], [27.0, 0.0],
            [29.3, -2.4], [31.2, -1.6], [29.3, -6.2]],
    'spots': CAFE_SPOTS,
    'nudge': {'after': 150, 'beats': [say('iris', 'Ной. Сядь уже. На тебя смотрят.', 'Noah. Sit down already. People are looking.')]},
    'exit': {'id': 'table', 'at': [23.0, F + 1.3, -3.4], 'where': [24.3, -3.9], 'r': 1.15, 'stand': [24.3, -3.9], 'face': -90,
             'label': L('Сесть за стол', 'Sit back down at the table'),
             'beats': [{'fx': 'noah_sit_cafe', 'shot': 'noah_cafe', 'dur': 1.2}]},
}

# ---------------------------------------------------------------------------------------------------------- буран
# машина стоит на боковой дороге в начале координат, нос — к −z (как на всех дорогах); задний бампер — z ≈ 2,4,
# выхлоп — сзади справа
STORM_SPOTS = [
    {'id': 'trunk', 'at': [-0.45, 1.05, 2.45], 'where': [-0.75, 3.25], 'r': 1.0, 'stand': [-0.75, 3.25], 'face': 180,
     'label': L('Открыть багажник', 'Open the trunk'),
     'cam': [[-1.9, 1.7, 4.6], [-0.45, 0.8, 2.3], 50],
     'beats': [
         {'anim': {'who': 'noah', 'clip': 'Chest_Open'}, 'dur': 0.8},
         note('Замок примёрз. Ной бьёт по нему кулаком, пока крышка не поддаётся.',
              'The lock is frozen. Noah pounds it with his fist until the lid gives.', 3.0, fx='snd:trunk:-6'),
         note('Монтировка. Ей можно долбить наст.', 'The tire iron. He can break up the crust with it.', 2.6, set={'storm_tool': 1}),
         note('На ней бурое и шерсть. Ной старается не смотреть.', 'There are brown stains on it, and fur. Noah tries not to look.', 2.8,
              **IF(deer='==2')),
     ],
     'after': [{'anim': {'who': 'noah', 'clip': 'Idle'}, 'fx': 'snd:trunk:-9', 'dur': 0.5}]},

    {'id': 'lights', 'at': [0.0, 0.75, -2.95], 'where': [0.0, -3.9], 'r': 1.1, 'stand': [0.0, -3.9], 'face': 0,
     'label': L('Протереть фары', 'Wipe the headlights'),
     'cam': [[1.6, 1.5, -5.6], [0.0, 0.7, -3.0], 46],
     'beats': [
         {'anim': {'who': 'noah', 'clip': 'Crouch_Idle'}},
         note('Фары залеплены снегом. Ной сгребает его рукавом.', 'The headlights are plastered with snow. Noah scrapes it off with his sleeve.', 2.8,
              fx='storm_lights'),
         note('Свет становится белой стеной — в двух шагах. Дальше нет ничего.',
              'The light turns into a white wall two steps ahead. Beyond it, nothing.', 3.0),
         {'anim': {'who': 'noah', 'clip': 'Idle'}},
     ]},

    {'id': 'look_in', 'at': [0.9, 1.25, 0.25], 'where': [1.6, 0.4], 'r': 1.0, 'stand': [1.6, 0.4], 'face': -90,
     'label': L('Заглянуть в салон', 'Look inside'),
     'cam': [[2.6, 1.55, 1.6], [0.6, 1.15, 0.2], 40],
     'beats': [
         {'look': {'who': 'noah', 'at': 'iris'}, 'fx': 'iris_glass'},
         note('Сквозь заиндевевшее стекло — Айрис. Она водит пальцем по стеклу изнутри.',
              'Through the frosted glass — Iris. She’s tracing something on the inside of the window.', 3.0),
         note('Буквы задом наперёд: «ЙОН».', 'The letters are backwards: “HAON.”', 2.6, fx='storm_name'),
         {'look': {'who': 'noah', 'at': None}, 'fx': 'iris_glass_off'},
     ]},
]

STORM = {
    'who': 'noah',
    'start': {'at': [-1.55, 0.0, 0.35], 'face': -120},   # у водительской двери, лицом в метель
    'intro': [
        note('Ветер рвёт дверь из рук. Отойдёшь на пять шагов — машины уже не видно.',
             'The wind tears the door out of his hands. Five steps away and the car would be gone.', 3.0, shot='storm_side'),
        note('Ной ведёт ладонью по крыше, чтобы не потерять её.', 'Noah runs his palm along the roof so he won’t lose it.', 2.6),
    ],
    'walk': [[-3.0, -5.0, 3.0, 4.6]],
    'block': [[-0.95, -2.95, 0.95, 2.45]],          # машина
    'cam_block': [[-0.95, -2.95, 0.95, 2.45, 1.5]],
    'nav': [[-1.6, -3.6], [1.6, -3.6], [1.7, 3.4], [-1.7, 3.4], [-1.6, 0.4], [1.6, 0.4]],
    'spots': STORM_SPOTS,
    'zones': [
        {'rect': [-3.0, -5.0, -2.3, 4.6], 'beats': [note('Шаг от машины — и её нет. Только белое и вой. Ной на ощупь находит бок машины.',
                                                         'One step from the car and it’s gone. Only white, and the howl. Noah gropes his way back to its side.', 3.4)]},
        {'rect': [2.3, -5.0, 3.0, 4.6], 'beats': [note('Справа — пустота. Ни обочины, ни столбов.',
                                                       'To the right — emptiness. No shoulder, no posts.', 2.6)]},
    ],
    'nudge': {'after': 120, 'beats': [note('Холод добирается до костей. Надо откапывать трубу.',
                                            'The cold is getting into his bones. He has to dig out the pipe.', 2.8)]},
    'exit': {'id': 'exhaust', 'at': [0.55, 0.45, 2.45], 'where': [0.6, 3.2], 'r': 1.0, 'stand': [0.6, 3.2], 'face': 180,
             'label': L('Откопать выхлоп', 'Dig out the exhaust'),
             'beats': [{'fx': 'storm_dig', 'dur': 0.4}]},
}

# сцены бурана
STORM_SCENES = {
    'c2_storm': {'beats': [
        note('Белая мгла. Фары упираются в снег в двух шагах. Где кончается дорога — не видно.',
             'A white haze. The headlights hit snow two steps ahead. There’s no telling where the road ends.', 3.4,
             fx='storm_white', shot='road'),
        {'qte': 'steer', 't': 4.0, 'hint': L('Удержи руль — A / D или стрелки', 'Hold the wheel — A / D or arrow keys'),
         'fail': 'c2_storm_slip'},
        note('Ной останавливается. Колёса ещё на дороге — кажется.', 'Noah stops. The wheels are still on the road — he thinks.', 2.8,
             fx='storm_stop', shot='two'),
        {'go': 'c2_storm_wait'},
    ]},
    'c2_storm_slip': {'beats': [
        note('Порыв сносит машину к обочине. Ной тормозит — задом её разворачивает в сугроб.',
             'A gust shoves the car toward the shoulder. Noah brakes — the back end swings round into a drift.', 3.4,
             fx='storm_slip', shot='two'),
        {'set': {'storm_slip': 1}},
        {'go': 'c2_storm_wait'},
    ]},
    'c2_storm_wait': {'beats': [
        say('iris', 'Не едь. Подождём, пока стихнет.', 'Don’t drive. Let’s wait till it dies down.', shot='iris'),
        say('noah', 'Мотор не глушу. Иначе замёрзнем.', 'I’m keeping the engine running. Or we’ll freeze.', shot='noah'),
        note('Печка гудит. Стёкла снаружи заметает, изнутри они запотевают.',
             'The heater hums. Snow piles up on the windows outside; inside, they fog over.', 3.0, shot='two', fx='storm_wait'),
        {'ask': {'max': 2, 'shot': 'two', 'opts': [
            {'ru': 'Про бабушку', 'en': 'About Grandma', 'beats': [
                say('noah', 'Расскажи про бабушку.', 'Tell me about Grandma.', shot='noah'),
                say('iris', 'Агнес. Мамина мама. Папа запрещал о ней даже говорить.',
                    'Agnes. Mom’s mom. Dad wouldn’t even let us talk about her.', shot='iris'),
                say('noah', 'Почему?', 'Why?', shot='noah'),
                say('iris', 'Не знаю. В дневнике мама пишет о ней так, будто просит прощения.',
                    'I don’t know. In the diary, Mom writes about her like she’s asking forgiveness.', shot='iris'),
                say('iris', 'Я её ни разу не видела.', 'I’ve never even seen her.', shot='iris', look={'who': 'iris', 'at': [1.6, 1.2, -0.6]}),
                {'dur': 1.2, 'look': {'who': 'iris', 'at': None}},   # посмотрела в окно — и снова к разговору (раньше так и смотрела до конца бурана)
            ]},
            {'ru': '«Ева, вы как?»', 'en': '“Eva, how are you doing?”', 'if': {'eva': '>0'}, 'beats': [
                say('noah', 'Ева, вы как?', 'Eva, how are you doing?', shot='noah'),
                say('eva', 'Живот каменеет. Это ничего, так бывает.', 'My belly keeps going hard. It’s nothing, it happens.', shot='back'),
                say('noah', 'Через месяц, вы говорили?', 'A month, you said?', shot='noah'),
                say('eva', '…Да. Через месяц.', '…Yes. A month.', shot='back'),
                note('Ева отворачивается к окну и долго дышит — медленно, будто считает.',
                     'Eva turns to the window and breathes for a long time — slowly, as if counting.', 3.2, shot='back'),
            ]},
            {'ru': '«Зоя, что значит — стирает?»', 'en': '“Zoe, what do you mean, erases?”', 'if': ZOE_IN, 'beats': [
                say('noah', 'Ты сказала — он стирает людей. Как это?', 'You said he erases people. What does that mean?', shot='noah'),
                say('zoe', 'Приходишь к нему с бедой. Уходишь без беды.', 'You go to him with something wrong. You leave without it.', shot='back'),
                say('zoe', 'И без кусков себя. Только не помнишь — каких.', 'And without pieces of yourself. You just don’t remember which ones.',
                    shot='back'),
                note('Айрис смотрит на брата и ничего не говорит.', 'Iris looks at her brother and says nothing.', 2.6, shot='iris'),
            ]},
            {'ru': '«Дэнни, не замёрз?»', 'en': '“Danny, you cold?”', 'if': {'danny': '>0'}, 'beats': [
                say('noah', 'Дэнни, не замёрз?', 'Danny, you cold?', shot='noah'),
                say('danny', 'Нет. А бабушка добрая?', 'No. Is your grandma nice?', shot='back'),
                say('iris', 'Не знаю.', 'I don’t know.', shot='iris'),
                say('danny', 'Если что, я её не боюсь.', 'Just so you know, I’m not scared of her.', shot='back'),
            ]},
            {'leave': True, 'ru': 'Помолчать', 'en': 'Stay quiet', 'beats': [
                note('Они молчат. Снег шуршит по крыше, печка гудит.', 'They sit in silence. Snow whispers on the roof, the heater hums.', 3.0),
            ]},
        ]}},
        note('Тянет выхлопом — сладковато, тошно. Сзади машина зарылась в сугроб.',
             'A smell of exhaust — sweetish, sickening. The back of the car is buried in the drift.', 3.4, shot='two', **IF(storm_slip='>0')),
        note('Проходит полчаса. Тянет выхлопом — сладковато, тошно.', 'Half an hour goes by. A smell of exhaust — sweetish, sickening.', 3.2,
             shot='two', **IF(storm_slip='==0')),
        note('Дэнни спит, привалившись к двери. Слишком крепко.', 'Danny is asleep against the door. Too deeply.', 2.8, shot='back',
             fx='storm_drowsy', **IF(danny='>0')),
        note('У Айрис слипаются глаза.', 'Iris’s eyes keep closing.', 2.4, shot='iris', fx='storm_drowsy', **IF(danny='==0')),
        say('noah', 'Трубу замело.', 'The exhaust’s buried.', shot='noah'),
        note('Ной глушит мотор. Сразу — тишина и вой ветра.', 'Noah kills the engine. At once — silence, and the howl of the wind.', 2.8,
             fx='engine_off', shot='two'),
        say('noah', 'Окна — на щель. Не спать.', 'Crack the windows. Nobody sleeps.', fx='snd:window_down:-8', shot='noah'),
        say('iris', 'Ты куда?', 'Where are you going?', shot='iris'),
        say('noah', 'Откопаю. С забитой трубой заводить нельзя.', 'I’ll dig it out. You can’t start it with the pipe blocked.', shot='noah'),
        say('iris', 'Ты там ничего не увидишь.', 'You won’t see a thing out there.', shot='iris'),
        say('noah', 'Машину увижу. На ощупь.', 'I’ll find the car. By feel.', shot='noah'),
        {'fx': 'storm_out'},
        {'explore': 'storm'},
        {'go': 'c2_storm_dig'},
    ]},
    'c2_storm_dig': {'beats': [
        note('Сзади — сугроб по бампер. Трубы не видно.', 'At the back — a drift up to the bumper. No sign of the pipe.', 2.6, shot='storm_rear'),
        note('Ной долбит наст монтировкой.', 'Noah hacks at the crust with the tire iron.', 2.2, **IF(storm_tool='>0')),
        {'qte': 'mash', 't': 6.0, 'n': 10, 'hint': L('Разгребай снег — быстро жми E / пробел', 'Dig the snow away — mash E / Space'),
         'fail': 'c2_storm_dig_slow', **IF(storm_tool='>0')},
        note('Ной разгребает снег голыми руками.', 'Noah digs at the snow with his bare hands.', 2.2, **IF(storm_tool='==0')),
        {'qte': 'mash', 't': 6.0, 'n': 16, 'hint': L('Разгребай снег — быстро жми E / пробел', 'Dig the snow away — mash E / Space'),
         'fail': 'c2_storm_dig_slow', **IF(storm_tool='==0')},
        note('Под снегом — чёрный кружок трубы. Из неё сыплется ледяная крошка.',
             'Under the snow — the black ring of the pipe. Ice crumbs trickle out of it.', 3.0, fx='storm_dug', shot='storm_rear'),
        {'go': 'c2_storm_inside'},
    ]},
    'c2_storm_dig_slow': {'beats': [
        note('Пальцы перестают слушаться. Ной гребёт ладонями, как лопатой, — пока не нащупывает трубу.',
             'His fingers stop obeying him. Noah scoops with his palms like a shovel — until he feels the pipe.', 3.6, shot='storm_rear'),
        {'set': {'frost': 1}, 'fx': 'storm_dug'},
        {'go': 'c2_storm_inside'},
    ]},
    'c2_storm_inside': {'beats': [   # «в салоне тем временем»: Ной снаружи, его этого разговора не слышит
        note('В машине без мотора холод приходит быстро.', 'Without the engine running, the cold creeps into the car fast.', 2.6,
             fx='storm_inside', shot='two'),
        say('eva', 'Он всегда так — всё сам?', 'Is he always like this — doing everything himself?', shot='back', **IF(eva='>0')),
        say('iris', 'Всегда.', 'Always.', shot='iris', **IF(eva='>0')),
        say('eva', 'Мой тоже всё сам решал. За меня.', 'Mine decided everything himself too. For me.', shot='back', **IF(eva='>0')),
        say('iris', 'И ты уехала?', 'And you left?', shot='iris', **IF(eva='>0')),
        say('eva', 'Уехала. А кольцо выбросить не могу.', 'I left. But I can’t throw the ring away.', shot='back',
            fx='eva_ring', **IF(eva='>0')),
        say('zoe', 'Слушай. Он правда не помнит, что было дома?', 'Listen. Does he really not remember what happened at home?',
            shot='back', **IF(**ZOE_IN)),
        say('iris', 'Не помнит.', 'He doesn’t.', shot='iris', **IF(**ZOE_IN)),
        say('zoe', 'А ты?', 'And you?', shot='back', **IF(**ZOE_IN)),
        say('iris', '…Я помню, что надо ехать.', '…I remember we have to keep going.', shot='iris', **IF(**ZOE_IN)),
        say('danny', 'Где Ной?', 'Where’s Noah?', shot='back', **IF(danny='>0')),
        say('iris', 'Откапывает машину.', 'Digging the car out.', shot='iris', **IF(danny='>0')),
        say('danny', 'Он не потеряется?', 'He won’t get lost?', shot='back', **IF(danny='>0')),
        say('iris', 'Он всегда возвращается.', 'He always comes back.', shot='iris', **IF(danny='>0')),
        note('Айрис одна. Считает вслух до ста. Потом снова.', 'Iris is alone. She counts out loud to a hundred. Then starts again.', 3.0,
             shot='iris', **IF(eva='==0', zoe='==0', danny='==0')),
        note('Айрис одна. Считает вслух до ста. Потом снова.', 'Iris is alone. She counts out loud to a hundred. Then starts again.', 3.0,
             shot='iris', **IF(eva='==0', zoe='>0', zoe_gone='>0', danny='==0')),   # Зою забрали ночью у кафе
        note('Дверь распахивается. Ной падает на сиденье — весь в снегу.', 'The door flies open. Noah drops into the seat, caked in snow.', 2.8,
             fx='storm_in', shot='two'),
        {'go': 'c2_storm_back'},
    ]},
    'c2_storm_back': {'beats': [
        note('Пальцы белые и не гнутся. Айрис молча растирает их в ладонях.',
             'His fingers are white and won’t bend. Iris rubs them between her palms without a word.', 3.2, shot='two',
             fx='iris_rubs', **IF(frost='>0')),
        note('Ключ. Мотор схватывает со второго раза.', 'The key. The engine catches on the second try.', 2.4, fx='engine_on', shot='noah'),
        say('noah', 'Окно не закрывай.', 'Keep the window open a crack.', shot='noah'),
        {'fx': 'radio_tune', 'shot': 'radio', 'dur': 0.6},
        {'qte': 'tune', 't': 10.0, 'hint': L('Поймай волну — A / D или стрелки', 'Find the station — A / D or arrow keys'),
         'fail': 'c2_storm_static'},
        {'set': {'storm_radio': 1}, 'fx': 'radio_report'},
        say('radio', '…на трассе на север — затор: сложилась фура. Движение закрыто до утра.',
            '…northbound highway: a jackknifed truck. The road is closed until morning.', shot='radio'),
        say('radio', 'На боковых дорогах заносы. Если машина встала — держите выхлопную трубу свободной от снега и приоткройте окно.',
            'Heavy drifts on side roads. If your car gets stuck, keep the exhaust pipe clear of snow and leave a window open.', shot='radio'),
        say('radio', 'Полиция просит сообщать о тёмном седане с одной фарой…', 'Police are asking anyone who sees a dark sedan with one headlight…',
            shot='radio', **IF(police='>0', lamp='>0')),
        say('radio', 'Полиция просит сообщать о тёмном седане, номер…', 'Police are asking anyone who sees a dark sedan, license plate…',
            shot='radio', **IF(police='>0', lamp='==0')),
        say('iris', 'Выключи.', 'Turn it off.', shot='iris', fx='radio_off_storm'),
        {'go': 'c2_storm_go'},
    ]},
    'c2_storm_static': {'beats': [
        note('Только шипение — то громче, то тише. Айрис выключает радио.', 'Nothing but hiss, rising and fading. Iris switches the radio off.', 3.0,
             fx='radio_off_storm', shot='radio'),
        {'go': 'c2_storm_go'},
    ]},
    'c2_storm_go': {'beats': [
        note('Ветер стихает — не весь, но дорогу снова видно на пару столбов вперёд.',
             'The wind eases — not all the way, but now the road shows a couple of posts ahead.', 3.2, fx='storm_go', shot='road'),
        {'go': 'c2_storm_end'},
    ]},
}


# ---------------------------------------------- после поста: час до кафе (разговор с темами; мастер-промпт, §17)
ZOE_CAR = {'zoe': '>0', 'zoe_gone': '==0'}
DANNY_CAR = {'danny': '>0', 'danny_lost': '==0'}
ROAD_TALK = {'c2_s3_road': {'beats': [
    note('До кафе — час пути. Печка гудит, дворники скрипят. Можно поговорить — или помолчать.',
         'An hour to the diner. The heater hums, the wipers creak. You can talk — or keep quiet.', 3.0, shot='road'),
    {'ask': {'id': 'road2', 'max': 3, 'opts': [
        {'ru': '«Айрис, ты как?»', 'en': '“Iris, how are you holding up?”', 'beats': [
            say('noah', 'Айрис… ты как?', 'Iris… how are you holding up?', shot='noah'),
            say('iris', 'Ты удирал от полиции с детьми в машине. Как я, по-твоему?', 'You ran from the police with kids in the car. How do you think I am?',
                shot='iris', **IF(fled='>0')),
            say('iris', 'Нормально. Не спрашивай, ладно? Просто довези нас.', 'Fine. Don’t ask, okay? Just get us there.', shot='iris', **IF(fled='==0')),
        ]},
        {'ru': '«Дэнни, расскажи про маму»', 'en': '“Danny, tell me about your mom”', 'if': DANNY_CAR, 'beats': [
            say('noah', 'Дэнни, а какая она — твоя мама?', 'Danny, what’s she like — your mom?', shot='noah'),
            say('danny', 'Она поёт, когда добрая. А когда пьёт — спит. Я ей вторую рацию оставил.',
                'She sings when she’s nice. When she drinks, she sleeps. I left her the other walkie-talkie.', shot='back'),
            say('danny', 'Если включит — найдёт. Она всегда находит.', 'If she turns it on, she’ll find me. She always finds me.', shot='back'),
        ]},
        {'ru': '«Зоя, откуда ты?»', 'en': '“Zoe, where are you from?”', 'if': ZOE_CAR, 'beats': [
            say('noah', 'Зоя, а ты вообще откуда?', 'Zoe, where are you even from?', shot='noah'),
            say('zoe', 'Отовсюду понемногу. Сейчас — с обочины.', 'A little bit of everywhere. Right now — from the side of the road.', shot='zoe'),
            say('zoe', 'Не спрашивай дальше, ладно? Я начну врать, а ты заметишь. Ты такой.',
                'Don’t ask any more, okay? I’ll start lying and you’ll notice. You’re that kind.', shot='zoe'),
        ]},
        {'ru': '«Куда мы едем?»', 'en': '“Where are we going?”', 'beats': [
            say('noah', 'Айрис, куда мы вообще едем?', 'Iris, where are we actually going?', shot='noah'),
            say('iris', 'На север. Дальше скажу. Просто рули.', 'North. I’ll tell you the rest later. Just drive.', shot='iris'),
            note('Она отворачивается к окну. В отражении видно: губы шевелятся — будто считает километры.',
                 'She turns to the window. In the reflection her lips are moving — as if she’s counting kilometers.', 3.0, shot='iris'),
        ]},
        {'leave': True, 'ru': 'Помолчать', 'en': 'Say nothing', 'beats': [
            note('Ной молчит. Радио шипит между станциями.', 'Noah says nothing. The radio hisses between stations.', 2.4, shot='road'),
        ]},
    ]}},
    {'go': 'c2_s4'},
]}}


def main():
    j = json.load(open(DATA, encoding='utf-8'))
    sc = j['scenes']
    ex = j.setdefault('explore', {})
    ex['c3p'] = CAFE
    ex['storm'] = STORM
    for f in NEW_FLAGS:
        j['flags'].setdefault(f, 0)
    j.setdefault('names', {}).update({'trucker_b': L('ДАЛЬНОБОЙЩИК', 'TRUCKER'), 'waitress': L('НИНА', 'NINA')})
    # крыльцо: вместо выбора «выйти / остаться» — прогулка по кафе с двумя выходами
    if not any(b.get('explore') == 'c3p' for b in sc['c2_s4_porch']['beats']):
        sc['c2_s4_porch'] = {'beats': [
            {'fx': 'cafe_walk'},
            {'explore': 'c3p'},
            {'if': {'porch': '>0'}, 'go': 'c2_s4_z'},
            {'go': 'c2_s5'},
        ]}
    # крыльцо (утверждённая сцена): если принёс кофе — кружка в руках у Зои
    z = sc['c2_s4_z']['beats']
    if not any(b.get('if', {}).get('coffee_zoe') for b in z):
        z.insert(0, {'fx': 'noah_porch', **IF(coffee_zoe='>0')})
        z.insert(1, note('Ной протягивает ей кружку. Зоя греет об неё руки и делает вид, что так и надо.',
                         'Noah hands her the mug. Zoe warms her hands on it and acts like it’s no big deal.', 3.0,
                         shot='porch', fx='zoe_coffee', **IF(coffee_zoe='>0')))
    # «Айрис видит их в окно»: она сидит у Евы (fx iris_eva в c2_s4_eva), а план смотрел на её место за столом —
    # в кадре был Дэнни. Теперь — из-за её плеча, на крыльцо
    for b in sc['c2_s4_zend']['beats']:
        if b.get('shot') == 'iris_window':
            b['shot'] = 'iris_window_eva'
            b['look'] = {'who': 'iris', 'at': 'noah'}
    # после поста — час до кафе: разговор с темами (все исходы поста → c2_s3_road → кафе)
    for k in ['c2_s3_pass', 'c2_s3_zoe_ok', 'c2_s3_flee', 'c2_s3_out', 'c2_s3_turn']:
        for b in sc[k]['beats']:
            if b.get('go') == 'c2_s4':
                b['go'] = 'c2_s3_road'
    if 'c2_s3_road' not in sc:
        order = list(sc)
        pos = order.index('c2_s3_turn') + 1
        items = [(k, sc[k]) for k in order[:pos]] + list(ROAD_TALK.items()) + [(k, sc[k]) for k in order[pos:]]
        j['scenes'] = sc = dict(items)
    else:
        sc.update(ROAD_TALK)
    # конец главы: «Машина ползёт по боковой дороге…» → буран → «В зеркале… две фары»
    road = sc['c2_s6_road']['beats']
    if 'c2_storm' not in sc:
        i = next(i for i, b in enumerate(road) if b.get('fx') == 'side_road')
        tail = road[i + 1:]
        sc['c2_s6_road']['beats'] = road[:i + 1] + [{'go': 'c2_storm'}]
        order = list(sc)
        pos = order.index('c2_s6_road') + 1
        new = dict(STORM_SCENES)
        new['c2_storm_end'] = {'beats': tail}
        items = [(k, sc[k]) for k in order[:pos]] + list(new.items()) + [(k, sc[k]) for k in order[pos:]]
        j['scenes'] = sc = dict(items)
    else:
        for k, v in STORM_SCENES.items():
            sc[k] = v
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    import vokeys   # свой ключ голоса у реплик внутри прогулок и разговоров
    vokeys.assign(j)
    s = json.dumps(j, ensure_ascii=False, indent=1) + '\n'
    open(DATA, 'w', encoding='utf-8').write(s)
    print('ch2.json: прогулка c3p (%d точек), буран storm (%d точек), сцен бурана %d, флаги %s' %
          (len(CAFE_SPOTS), len(STORM_SPOTS), len(STORM_SCENES) + 1, ', '.join(NEW_FLAGS)))


if __name__ == '__main__':
    main()
