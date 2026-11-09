"""WHAT THE LAKE KEEPS, глава 3 — прогулки по мастер-промпту редактора (3 ноя): «бензин у Петра» (Ной сам идёт через
деревню к магазину и обратно к генератору) и «дом Агнес ночью» (фотографии Анны, приёмник, сундук с её вещами, спящие,
лаз в подпол — потом разговор у печи). Подключается из tools/story/ch3.py (apply(DATA) перед записью ch3.json).
Новые отметки — только свои (c3_*): доверие, «Тень», таблетки, улики и концовки не трогают.

Координаты — мировые, как в scripts/world/village.gd: улица вдоль z по x = 0, дом Агнес — центр (−11, −45), фасад к +x,
пол в доме — y 0,5 (точка в доме: A(x, z) — местные координаты дома). Магазин — (11, −28), дверь (8,48; −30,2),
окно (8,48; −27,1), таксофон (8,28; −31,35); колодец (−5,5; −40); дом напротив Агнес h4 — (11, −50), окна на улицу."""

F = 0.5   # пол в доме Агнес


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


def A(x, z):   # местные координаты дома Агнес → мир (x, z)
    return [round(-11.0 + x, 3), round(-45.0 + z, 3)]


def A3(x, y, z):   # то же с высотой над полом дома
    return [round(-11.0 + x, 3), round(F + y, 3), round(-45.0 + z, 3)]


# ---------------------------------------------------------------------------------------- бензин: через деревню
VIL_SPOTS = [
    {'id': 'window', 'at': [8.0, 1.95, -50.0], 'where': [4.25, -49.2], 'r': 1.2, 'stand': [4.22, -49.25], 'face': 105,   # у вала снега, не в нём
     'label': L('Окно напротив', 'The window across the street'),
     'cam': [[2.6, 1.8, -47.2], [8.0, 1.85, -50.2], 40],
     'beats': [
         {'fx': 'window_candle', 'look': {'who': 'noah', 'at': [8.0, 1.9, -50.0]}},
         note('В доме напротив, за тёмным окном, дрожит огонёк свечи.', 'In the house across the street, behind a dark window, a candle flame trembles.', 2.8),
         note('Кто-то стоит за занавеской и смотрит на Ноя. Не прячется.', 'Someone is standing behind the curtain, watching Noah. Not hiding.', 2.8),
         note('Огонёк гаснет.', 'The flame goes out.', 2.2, fx='window_candle_off'),
     ],
     'after': [{'look': {'who': 'noah', 'at': None}}]},

    {'id': 'well', 'at': [-5.5, 1.1, -40.0], 'where': [-4.4, -40.7], 'r': 1.2, 'stand': [-4.35, -40.8], 'face': -54,
     'label': L('Колодец', 'The well'),
     'cam': [[-3.2, 1.6, -38.6], [-5.5, 0.9, -40.0], 42],   # сбоку: колодец в кадре, Ной у края
     'beats': [
         note('Колодец. Ворот обледенел, цепь вмёрзла в сруб.', 'The well. The windlass is iced over; the chain is frozen to the frame.', 2.8),
         note('Дом с синими ставнями, у колодца. Всё, как писала мама.', 'The house with blue shutters, by the well. Just like Mom wrote.', 2.8),
     ]},

    {'id': 'tracks', 'at': [-0.9, 0.2, -36.5], 'where': [-1.8, -33.6], 'r': 1.3, 'stand': [-1.8, -33.5], 'face': 165,
     'label': L('Колея на дороге', 'Tire tracks in the road'),
     'cam': [[-1.2, 0.95, -31.0], [-0.2, 0.1, -42.0], 46],
     'beats': [
         {'fx': 'car_tracks'},
         note('На дороге — свежая колея. Широкая, не от их машины.', 'Fresh tire tracks in the road. Wide ones — not from their car.', 2.8),
         note('Тянется мимо дома Агнес, в конец улицы, к лесу.', 'They run past Agnes’s house, down to the end of the street, toward the woods.', 2.8),
         note('Снег её ещё не засыпал. Кто-то проехал совсем недавно.', 'The snow hasn’t filled them in yet. Someone drove through not long ago.', 2.8),
     ]},

    {'id': 'payphone', 'at': [8.28, 1.6, -31.35], 'where': [7.45, -31.4], 'r': 1.0, 'stand': [7.5, -31.35], 'face': 90,
     'label': L('Таксофон', 'The payphone'),
     'cam': [[6.2, 1.7, -32.9], [8.2, 1.45, -31.3], 40],
     'beats': [
         {'anim': {'who': 'noah', 'clip': 'Interact'}},
         note('Таксофон под козырьком. Трубка ледяная — пальцы липнут к пластику.', 'A payphone under a little hood. The receiver is ice-cold — his fingers stick to the plastic.', 3.0,
              fx='snd:phone_rip:-14'),
         note('Гудка нет. Провода оборвало вместе со светом.', 'No dial tone. The lines went down with the power.', 2.6),
         {'anim': {'who': 'noah', 'clip': 'Idle'}},
     ]},

    {'id': 'shopwin', 'at': [8.48, 1.95, -27.1], 'where': [7.45, -27.2], 'r': 1.1, 'stand': [7.5, -27.2], 'face': 90,
     'label': L('Витрина магазина', 'The store window'),
     'cam': [[6.4, 1.75, -25.2], [8.5, 1.7, -27.6], 42],
     'beats': [
         note('За стеклом — полки, банки, мешок сахара. На двери табличка «Закрыто».', 'Behind the glass — shelves, tins, a sack of sugar. A sign on the door: “Closed.”', 2.8),
         note('Ниже, от руки: «Стучите громче. Пётр».', 'Underneath, by hand: “Knock louder. Pyotr.”', 2.4),
         note('В глубине, под дверью подсобки, — полоска света. Керосиновая лампа.', 'At the back, under the storeroom door — a strip of light. A kerosene lamp.', 2.8),
     ]},
]

VILLAGE = {
    'who': 'noah',
    'start': {'at': [-4.2, 0.0, -45.0], 'face': 90},   # с крыльца Агнес — за калитку, на улицу
    'intro': [
        note('Магазин Петра — через улицу, у таксофона. Ветер толкает в грудь.', 'Pyotr’s store is across the street, by the payphone. The wind shoves at his chest.', 2.8),
    ],
    'hum': {'if': {'pills': '==0'}, 'v': 0.3},
    'walk': [[-6.68, -47.3, -5.9, -42.7], [-6.0, -52.2, 6.0, -22.0], [5.9, -30.3, 7.0, -25.7], [6.6, -32.4, 7.95, -24.6]],   # калитка — до крыльца Агнес
    'block': [
        [-6.15, -40.65, -4.85, -39.35],                       # колодец
        # валы снега вдоль улицы (village.gd _snow_banks): проходы расчищены к калитке Агнес, к колодцу и к магазину
        [-5.9, -52.4, -4.5, -46.3], [-5.9, -43.7, -4.5, -41.6], [-5.9, -38.4, -4.5, -21.8],
        [4.5, -52.4, 5.9, -30.3], [4.5, -25.7, 5.9, -21.8],
        [1.2, -39.6, 3.2, -34.4, {'walked': '==0'}],          # их машина у дома Агнес (доехали)
    ],
    'cam_block': [[-14.4, -48.9, -7.6, -41.1, 99.0], [8.4, -31.2, 13.6, -24.8, 99.0], [7.9, -53.6, 14.1, -46.4, 99.0],
                  [-6.2, -40.8, -4.8, -39.2, 2.15]],
    'nav': [[-4.2, -45.0], [-3.4, -42.0], [-3.4, -38.0], [0.2, -41.0], [4.1, -46.5], [-1.6, -32.5], [3.8, -29.0],
            [6.3, -28.0], [7.3, -28.0], [7.3, -31.0]],
    'zones': [
        {'rect': [-3.5, -38.5, 3.5, -33.0], 'if': {'pills': '==0'}, 'beats': [
            note('Гул в голове — как вода в ушах. Ной трясёт головой, но он не уходит.', 'The hum in his head is like water in his ears. Noah shakes his head, but it won’t go away.', 3.0,
                 fx='snd:hum_ring:-12'),
        ]},
    ],
    'spots': VIL_SPOTS,
    'nudge': {'after': 120, 'beats': [note('Из дома Агнес, сквозь ветер, — крик Евы.', 'From Agnes’s house, through the wind — Eva screams.', 2.6)]},
    'exit': {'id': 'door', 'at': [8.48, 1.4, -30.2], 'where': [7.5, -30.2], 'r': 1.0, 'stand': [7.6, -30.2], 'face': 90,
             'label': L('Войти в магазин', 'Go into the store'),
             'beats': [{'fx': 'snd:knock:-10', 'dur': 0.5}]},
}

# обратно: генератор у сарая (без прогулки — бегом, сценой)
GEN = [
    {'fx': 'gas_run', 'shot': 'gas_run', **note('Обратно — бегом, с канистрой. Ветер теперь толкает в спину.', 'Back at a run, with the gas can. Now the wind is at his back.', 2.6)},
    {'fx': 'shed_gen', 'shot': 'shed_gen', **note('Сарай. Генератор под брезентом. Ной льёт бензин — руки трясутся, половина мимо.', 'The shed. The generator under a tarp. Noah pours the gas — his hands are shaking, half of it misses.', 3.2)},
    {'qte': 'mash', 't': 6.0, 'n': 8, 'sfx': 'none', 'hint': L('Дёргай шнур стартера — жми E / пробел', 'Yank the starter cord — press E / Space')},
]

# --------------------------------------------------------------------------------------- дом Агнес ночью
HOUSE_SPOTS = [
    {'id': 'photos', 'at': A3(-3.0, 1.8, 0.55), 'where': A(-2.25, 0.45), 'r': 0.55, 'stand': A(-2.25, 0.45), 'face': -90,
     'label': L('Фотографии Анны', 'Anna’s photos'),
     'cam': [A3(-2.0, 1.75, 0.95), A3(-3.06, 1.78, 0.55), 40],   # вся стена: три фото и приёмник
     'beats': [
         {'look': {'who': 'noah', 'at': A3(-3.06, 1.75, 0.55)}},
         note('Анна — девочкой, с двумя косами. Анна с удочкой на мостках. Анна в школьном фартуке, с букетом выше головы.',
              'Anna as a girl, with two braids. Anna with a fishing rod on a dock. Anna in her school pinafore, holding flowers taller than her head.', 3.8),
         note('На свадебном снимке край отрезан ножницами. Рядом с невестой — только чужое плечо в пиджаке.',
              'On the wedding photo, one edge has been cut off with scissors. Next to the bride — only a stranger’s shoulder in a suit.', 3.4),
         say('agnes', 'Я его отрезала. В тот год, когда он запретил ей ко мне ездить.', 'I cut him out. The year he forbade her to visit me.',
             look={'who': 'agnes', 'at': 'noah'}),
     ],
     'ask': {'max': 1, 'opts': [
         {'ru': '«Она была счастливой?»', 'en': '“Was she happy?”', 'beats': [
             say('noah', 'Она была счастливой?', 'Was she happy?'),
             say('agnes', 'До него — да. Пела всё время. Потом перестала.', 'Before him — yes. She sang all the time. Then she stopped.'),
         ]},
         {'ru': 'Промолчать', 'en': 'Say nothing', 'beats': [
             note('Ной долго смотрит на отрезанный край. Ножницы шли ровно — не сгоряча.', 'Noah looks at the cut edge for a long time. The scissors went straight — not in a rage.', 3.0),
         ]},
     ]},
     'after': [{'look': {'who': 'noah', 'at': None}}, {'look': {'who': 'agnes', 'at': None}}]},

    {'id': 'radio', 'at': A3(-2.98, 1.76, 0.1), 'where': A(-2.3, -0.5), 'r': 0.5, 'stand': A(-2.3, -0.45), 'face': -90,
     'label': L('Приёмник на полке', 'The radio on the shelf'),
     'cam': [A3(-2.0, 1.85, 0.95), A3(-2.98, 1.74, 0.1), 34],
     'beats': [
         note('Старый приёмник. Ручка настройки стёрта до блеска — его крутили годами.', 'An old radio. The tuning knob is worn shiny — someone has turned it for years.', 2.8),
     ],
     'ask': {'max': 1, 'opts': [
         {'ru': 'Включить тихо', 'en': 'Turn it on, quietly', 'beats': [
             note('Треск. Сквозь треск — сводка: трасса на север закрыта, метель до утра.', 'Static. Through the static — a bulletin: the northbound highway is closed, the blizzard will last till morning.', 3.4,
                  fx='radio_static', set={'c3_radio': 1}),
             note('Ной выключает. Агнес у печи даже не обернулась.', 'Noah switches it off. Agnes at the stove doesn’t even turn around.', 2.6),
         ]},
         {'ru': 'Не трогать', 'en': 'Leave it', 'beats': [
             note('Ной не трогает. В этом доме всё ждёт кого-то, кто уже не придёт.', 'Noah doesn’t touch it. Everything in this house is waiting for someone who isn’t coming back.', 3.0),
         ]},
     ]}},

    {'id': 'eva', 'if': {'eva_alive': '>0'}, 'at': A3(-2.0, 0.9, 1.3), 'where': A(-0.85, 1.3), 'r': 0.6, 'stand': A(-0.85, 1.3),
     'face': -90, 'label': L('Ева и малышка', 'Eva and the baby'),
     'cam': [A3(-0.6, 1.6, 0.4), A3(-2.0, 0.8, 1.35), 44],
     'beats': [
         note('Ева спит. Малышка у неё на груди — крошечный кулак у самого рта.', 'Eva is asleep. The baby on her chest — a tiny fist right by her mouth.', 3.0),
         note('Обе дышат в такт. Ной стоит, пока не убеждается в этом.', 'They breathe in time. Noah stands there until he’s sure of it.', 2.8),
     ]},

    {'id': 'eva_dead', 'if': {'eva_dead': '>0'}, 'at': A3(-2.0, 0.9, 1.3), 'where': A(-0.85, 1.3), 'r': 0.6, 'stand': A(-0.85, 1.3),
     'face': -90, 'label': L('Кровать за печью', 'The bed behind the stove'),
     'cam': [A3(-0.6, 1.6, 0.4), A3(-2.0, 0.8, 1.35), 44],
     'beats': [
         note('Ева лежит, будто спит. Агнес закрыла ей глаза и укрыла до подбородка.', 'Eva lies as if asleep. Agnes closed her eyes and drew the blanket up to her chin.', 3.2),
         note('Ной стоит рядом. Малышка у Айрис — та даже во сне её не отпускает.', 'Noah stands beside her. The baby is with Iris — even asleep, she won’t let go.', 3.0),
     ]},

    {'id': 'iris', 'if': {'eva_dead': '==0'}, 'at': A3(2.9, 1.0, 1.6), 'where': A(2.35, 0.45), 'r': 0.55, 'stand': A(2.35, 0.45),
     'face': 26, 'label': L('Айрис', 'Iris'),
     'cam': [A3(1.75, 1.3, 1.0), A3(2.85, 1.0, 1.6), 38],   # через стол — лицо Айрис
     'beats': [
         note('Айрис уснула сидя, прижав к себе мамин дневник.', 'Iris has fallen asleep sitting up, holding Mom’s diary to her chest.', 2.8),
     ],
     'ask': {'max': 1, 'opts': [
         {'ru': 'Забрать дневник, чтобы не упал', 'en': 'Take the diary so it won’t fall', 'beats': [
             note('Ной тянет тетрадь — Айрис во сне сжимает её крепче. Не отдаёт.', 'Noah tugs at the notebook — in her sleep, Iris grips it tighter. She won’t let go.', 3.0),
         ]},
         {'ru': 'Не будить', 'en': 'Let her sleep', 'beats': [
             note('Пусть спит. Здесь она впервые за много дней не вздрагивает во сне.', 'Let her sleep. Here, for the first time in days, she isn’t flinching in her sleep.', 3.0),
         ]},
     ]}},

    {'id': 'iris_baby', 'if': {'eva_dead': '>0'}, 'at': A3(2.9, 1.0, 1.6), 'where': A(2.35, 0.45), 'r': 0.55, 'stand': A(2.35, 0.45),
     'face': 26, 'label': L('Айрис', 'Iris'),
     'cam': [A3(1.75, 1.3, 1.0), A3(2.85, 1.0, 1.6), 38],
     'beats': [
         note('Айрис уснула сидя, с малышкой на руках. Держит крепко — как держала Еву за руку.', 'Iris has fallen asleep sitting up, the baby in her arms. Holding on tight — the way she held Eva’s hand.', 3.2),
         note('Ной не будит её.', 'Noah doesn’t wake her.', 2.0),
     ]},

    {'id': 'chest', 'at': A3(0.6, 0.6, 3.4), 'where': A(0.6, 2.6), 'r': 0.55, 'stand': A(0.6, 2.6), 'face': 0,
     'label': L('Сундук', 'The chest'),
     'cam': [A3(-0.4, 1.5, 2.2), A3(0.6, 0.6, 3.4), 44],
     'beats': [
         note('Сундук. Сверху — простыни. Под ними — свёрток в газете.', 'A chest. Sheets on top. Underneath — a bundle wrapped in newspaper.', 2.8),
     ],
     'ask': {'max': 1, 'opts': [
         {'ru': 'Развернуть', 'en': 'Unwrap it', 'beats': [
             note('Детское платье в горошек. Красная лента для кос. Школьная тетрадь: «Аня, 3 „Б“».',
                  'A little polka-dot dress. A red ribbon for braids. A school notebook: “Anya, Grade 3.”', 3.4, set={'c3_chest': 1}),
             note('Сочинение «Кем я буду». Детским почерком: «Я буду смелой».', 'An essay: “What I Will Be.” In a child’s handwriting: “I will be brave.”', 3.0),
             say('agnes', 'Положи, где взял, милок. Это я сама перебираю.', 'Put it back where you found it, dear. I go through those myself.',
                 look={'who': 'agnes', 'at': 'noah'}),
         ]},
         {'ru': 'Не трогать', 'en': 'Leave it', 'beats': [
             note('Ной закрывает крышку. Это не его.', 'Noah closes the lid. It isn’t his.', 2.2),
         ]},
     ]},
     'after': [{'look': {'who': 'agnes', 'at': None}}]},

    {'id': 'danny', 'if': {'danny': '>0', 'danny_lost': '==0'}, 'at': A3(-1.55, 2.1, -2.35), 'where': A(-0.5, -2.65), 'r': 0.5,
     'stand': A(-0.55, -2.65), 'face': -90, 'label': L('Дэнни на печи', 'Danny on the stove'),
     'cam': [A3(0.1, 2.25, -2.0), A3(-1.55, 2.05, -2.45), 40],
     'beats': [
         note('Дэнни спит на печи, под старым тулупом. Рация зажата в кулаке.', 'Danny is asleep on top of the stove, under an old sheepskin. His walkie-talkie is clenched in his fist.', 3.0),
         say('danny', 'Мам… я тут…', 'Mom… I’m here…'),
     ]},

    {'id': 'window', 'at': A3(3.2, 1.5, -2.2), 'where': A(2.55, -2.2), 'r': 0.55, 'stand': A(2.55, -2.2), 'face': 90,
     'label': L('Окно', 'The window'),
     'cam': [A3(2.45, 1.72, -1.7), A3(8.0, 1.2, -4.2), 46],
     'beats': [
         note('За окном — улица. Снег валит так, что дома напротив не видно.', 'Outside the window — the street. The snow is so thick you can’t see the house across the way.', 2.8),
         note('Колею на дороге уже замело — будто никто и не проезжал.', 'The tire tracks in the road are already snowed over — as if no one had ever driven through.', 2.8,
              **IF(went_noah='>0')),
         note('Где-то там, на краю деревни, — чёрная машина с выключенными фарами. Отсюда её не видно.',
              'Somewhere out there, at the edge of the village, is a black car with its lights off. You can’t see it from here.', 3.2, **IF(hale_here='>0')),
         note('Ни огонька. Деревня будто вымерла.', 'Not a single light. The village might as well be dead.', 2.4, **IF(went_noah='==0', hale_here='==0')),
     ]},

    {'id': 'hatch', 'at': A3(-0.2, 0.1, -1.0), 'where': A(0.55, -0.75), 'r': 0.45, 'stand': A(0.55, -0.75), 'face': -108,
     'label': L('Лаз в подпол', 'The cellar hatch'),
     'cam': [A3(-0.1, 2.2, -0.15), A3(-0.2, 0.0, -1.05), 50],   # сверху: лаз, кольцо, валенки Агнес у края
     'beats': [
         note('Лаз в подпол. Кольцо примёрзло к доскам — из щелей тянет холодом.', 'A hatch to the cellar. The ring is frozen to the boards — cold seeps up through the cracks.', 2.8),
         say('agnes', 'Там картошка да соленья. И сухо. Запомни — мало ли.', 'Potatoes and pickles down there. And it’s dry. Remember it — just in case.',
             look={'who': 'agnes', 'at': 'noah'}),
     ],
     'after': [{'look': {'who': 'agnes', 'at': None}}]},
]

HOUSE = {
    'who': 'noah',
    'start': {'at': [A(1.25, -0.3)[0], F, A(1.25, -0.3)[1]], 'face': -110},
    'intro': [
        note('Дом затих. Только печь потрескивает. Агнес курит трубку у огня.', 'The house has gone quiet. Only the stove crackles. Agnes smokes her pipe by the fire.', 2.8),
    ],
    'hum': {'if': {'pills': '==0'}, 'v': 0.16},
    'watch': ['agnes'],
    'walk': [[-14.0, -48.5, -8.0, -41.5]],
    'block': [
        [-13.85, -48.35, -11.8, -46.35],                       # печь и припечек (на печи — Дэнни)
        [-14.2, -44.2, -12.2, -43.2],                          # кровать за печью
        [-9.35, -44.2, -8.25, -42.6],                          # стол
        [-8.25, -44.35, -7.85, -42.45],                        # лавка у окна (Айрис)
        [-9.85, -42.3, -8.15, -41.9],                          # лавка у стены (Зоя)
        [-10.9, -41.9, -9.9, -41.3],                           # сундук
        [-12.05, -46.45, -11.3, -45.65],                       # Агнес у печи
    ],
    'floors': [[-14.1, -48.6, -7.9, -41.4, F]],
    'cam_block': [[-30.0, -60.0, -14.05, -30.0, 99.0], [-7.95, -60.0, 10.0, -30.0, 99.0], [-30.0, -60.0, 10.0, -48.55, 99.0],
                  [-30.0, -41.45, 10.0, -30.0, 99.0], [-13.85, -48.35, -11.8, -46.35, 99.0]],
    'nav': [A(1.0, -0.3), A(0.6, 1.2), A(-0.6, 0.3), A(-0.9, 2.2), A(1.2, 0.2), A(2.0, -1.6), A(0.1, -2.3), A(-0.4, -1.9)],
    'spots': HOUSE_SPOTS,
    'nudge': {'after': 150, 'beats': [say('agnes', 'Сядь, милок. Ноги-то не казённые.', 'Sit down, dear. Those legs aren’t made of iron.')]},
    'exit': {'id': 'stove', 'at': A3(-0.7, 1.0, -1.05), 'where': A(0.75, -1.75), 'r': 0.5, 'stand': A(0.75, -1.75), 'face': -100,
             'label': L('Сесть к Агнес у печи', 'Sit with Agnes by the stove'),
             'beats': [{'dur': 0.3}]},
}

NEW_FLAGS = ['c3_radio', 'c3_chest']


def apply(DATA):
    """Подставить прогулки в главу 3 (словарь DATA из ch3.py; сцены — DATA['scenes'])."""
    SC = DATA['scenes']
    DATA.setdefault('explore', {})['c3v'] = VILLAGE
    DATA['explore']['c3h'] = HOUSE
    for f in NEW_FLAGS:
        DATA['flags'].setdefault(f, 0)
    # бензин: Ной сам идёт к магазину (прогулка), потом бегом обратно к генератору
    nb = SC['c3_s3_noah']['beats']
    i = next(i for i, b in enumerate(nb) if b.get('fx') == 'noah_storm')
    nb[i] = dict(nb[i], fx='noah_out', shot='noah_out')
    nb.insert(i + 1, {'explore': 'c3v'})
    for sid in ('c3_s3_noah_ok', 'c3_s3_lapse'):
        bb = SC[sid]['beats']
        j = next(j for j, b in enumerate(bb) if b.get('fx') == 'generator')
        SC[sid]['beats'] = bb[:j] + [dict(x) for x in GEN] + bb[j:]
    # дом ночью: сначала прогулка, потом разговор у печи (заметка — без «Дом затих», он уже в начале прогулки; Айрис —
    # на лавке у окна, где её и усадили)
    b4 = SC['c3_s4']['beats']
    k = next(k for k, b in enumerate(b4) if b.get('fx') == 'night_stove')
    b4[k] = dict(b4[k], note=L('Айрис клюёт носом на лавке — перед этим она долго шепталась с бабушкой. Агнес курит трубку. Ной садится рядом.',
                               'Iris is nodding off on the bench — she spent a long time whispering with Grandma first. Agnes smokes her pipe. Noah sits down beside her.'))
    b4.insert(k, {'explore': 'c3h'})
    b4.insert(k, {'fx': 'house_walk'})
    return DATA
