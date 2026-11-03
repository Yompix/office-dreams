"""WHAT THE LAKE KEEPS, глава 5 — эпизоды по мастер-промпту редактора (3 ноя): прогулка у дачи (сарай у воды — издалека,
качели, поленница; выход — крыльцо: ключ под третьей ступенькой берётся в основной последовательности, пропустить его
нельзя), прогулка в доме (рюкзак Ноя под кроватью, три чашки, мамина жестяная шкатулка — код из дневника, а если дневник
у Хейла, его вспоминает Айрис; в шкатулке — письмо Саймона про «субботу»), разговор с Айрис у окна — темы зависят от
прохождения (дневник, доверие, бабушка). Подключается из tools/lake/story/ch5.py (apply(DATA)).
Новые отметки — свои (c5_*), и одна улика: ev_letter — письмо Саймона идёт в доказательства, только если мамин дневник
сгорел (тот же факт — «Саймон обещал в субботу» — дважды не считается). Доверие, «Тень», таблетки и концовки не трогает.

Координаты — мировые: озеро Ch4.LAKE = (−420, 0, −60); дача — местные озера (−20, 0), пол y 0,6, фасад к озеру (+x),
крыльцо — x −17…−15,4, ступени — до x −14,5; сарай — (−10, 13)."""


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


def LK(x, z):   # местные озера → мир
    return [round(x - 420.0, 3), round(z - 60.0, 3)]


def LK3(x, y, z):
    return [round(x - 420.0, 3), y, round(z - 60.0, 3)]


def LBOX(x0, z0, x1, z1, *cond):
    return LK(x0, z0) + LK(x1, z1) + list(cond)


DX, DF = -20.0, 0.6


def DK(x, z):   # местные дачи → мир
    return LK(DX + x, z)


def DK3(x, y, z):
    return LK3(DX + x, DF + y, z)


def DBOX(x0, z0, x1, z1, *cond):
    return DK(x0, z0) + DK(x1, z1) + list(cond)


ZOE_HERE = {'zoe_here': '>0'}
DANNY_HERE = {'danny_here': '>0'}

# --------------------------------------------------------------------------------- у дачи: до крыльца
YARD_SPOTS = [
    {'id': 'shed', 'at': LK3(-10.0, 1.6, 12.6), 'where': LK(-12.0, 6.6), 'r': 0.8, 'stand': LK(-12.0, 6.6), 'face': 17,
     'label': L('Сарай у воды', 'The boathouse'),
     'cam': [LK3(-15.0, 2.0, 3.6), LK3(-10.4, 1.2, 12.4), 38],   # Ной у края кадра, сарай — в центре (раньше спина закрывала угол)
     'beats': [
         note('Лодочный сарай у самой воды. Доски почернели. В одной — щель, низко, на уровне глаз ребёнка.',
              'The boathouse right by the water. The boards have gone black. One has a gap — low, at a child’s eye level.', 3.6),
         {'fx': 'snd:frogs:-26', **note('Ной смотрит на щель слишком долго. В ушах — лягушки. Летние. Откуда они зимой?',
                                       'Noah stares at the gap too long. Frogs in his ears. Summer frogs. Where would they come from in winter?', 3.6)},
         say('iris', 'Туда мы ещё сходим. Вместе. Только не сейчас.', 'We’ll go down there. Together. Just not yet.'),
     ]},
    {'id': 'swing', 'at': LK3(-12.6, 1.0, -6.4), 'where': LK(-12.6, -5.2), 'r': 0.7, 'stand': LK(-12.6, -5.15), 'face': 180,
     'label': L('Качели', 'The swing'),
     'cam': [LK3(-9.2, 1.6, -3.4), LK3(-12.6, 0.9, -6.4), 46],   # издали: столбы, перекладина и доска в снегу — над субтитрами
     'beats': [
         note('Качели на двух столбах. Доска обледенела; одна верёвка оборвана и вмёрзла в снег.',
              'A swing between two posts. The seat is iced over; one rope has snapped and frozen into the snow.', 3.2),
         say('iris', 'Ты меня тут раскачивал. Выше, выше… А мама кричала с крыльца: «Тише вы!»',
             'You used to push me here. Higher, higher… And Mom would shout from the porch, “Easy, you two!”'),
         note('Ной берётся за верёвку. Пальцы сами находят узел — будто вчера завязывал.',
              'Noah takes hold of the rope. His fingers find the knot on their own — as if he tied it yesterday.', 3.2),
     ]},
    {'id': 'wood', 'at': LK3(-13.6, 0.8, -3.2), 'where': LK(-13.6, -2.3), 'r': 0.6, 'stand': LK(-13.6, -2.25), 'face': 180,
     'label': L('Поленница', 'The woodpile'),
     'cam': [LK3(-12.3, 1.4, -2.9), LK3(-13.7, 0.65, -3.25), 46],
     'beats': [
         note('Поленница под навесом. Дрова сухие, серые — их складывали ещё при маме.',
              'A woodpile under a little roof. The logs are dry and gray — stacked back when Mom was still here.', 3.0),
         say('agnes', 'Дед колол. Каждое лето — на две зимы вперёд.', 'Grandpa split them. Every summer — enough for two winters ahead.',
             **IF(agnes_dead='==0')),
         note('Хватит на печь. Хватит надолго.', 'Enough for the stove. Enough for a long time.', 2.4, **IF(agnes_dead='>0')),
     ]},
]

YARD = {
    'who': 'noah',
    'intro': [note('Дача молчит. Окна заколочены, крыльцо замело. Всё — как одиннадцать лет назад.',
                   'The lake house is silent. The windows are boarded up, the porch is snowed in. Everything is just as it was eleven years ago.', 3.2)],
    'walk': [LBOX(-16.4, -7.6, -9.2, 9.6)],
    'block': [
        LBOX(-17.0, -1.6, -14.5, 1.6),                     # крыльцо и ступени
        LBOX(-16.9, 1.8, -15.7, 3.4), LBOX(-16.9, -3.4, -15.7, -1.8),   # сугробы под окнами
        LBOX(-13.6, -6.6, -11.6, -6.2),                    # качели
        LBOX(-14.3, -3.55, -12.9, -2.85),                  # поленница
        LBOX(-12.5, 2.7, -11.9, 3.3),                      # Айрис
        LBOX(-11.5, 1.9, -10.9, 2.5, {'agnes_dead': '==0'}),
        LBOX(-11.3, 3.3, -10.7, 3.9, {'eva_alive': '>0'}),
        LBOX(-12.1, 3.9, -11.5, 4.5, DANNY_HERE),
        LBOX(-12.3, -0.7, -11.7, -0.1, ZOE_HERE),
    ],
    'cam_block': [LBOX(-23.6, -4.8, -16.5, 4.8, 99.0)],
    'nav': [LK(-12.6, 1.6), LK(-13.2, 3.6), LK(-12.9, 5.6), LK(-12.0, 6.6), LK(-13.4, -1.4), LK(-13.6, -2.25),
            LK(-12.6, -5.15), LK(-14.0, 0.4), LK(-11.0, -2.0)],
    'spots': YARD_SPOTS,
    'nudge': {'after': 120, 'beats': [note('Айрис дует на замёрзшие пальцы и смотрит на крыльцо.', 'Iris blows on her frozen fingers and looks at the porch.', 2.6)]},
    'exit': {'id': 'porch', 'at': LK3(-14.65, 0.45, 0.4), 'where': LK(-13.95, 0.4), 'r': 0.7, 'stand': LK(-14.1, 0.4), 'face': -90,
             'label': L('Крыльцо', 'The porch'),
             'beats': [{'dur': 0.3}]},
}

# --------------------------------------------------------------------------------- в доме: до печи
OPEN_BOX = [
    {'fx': 'box_open', **note('Замок щёлкает. В шкатулке — мамины серёжки, две метрики и письмо, сложенное вчетверо.',
                              'The lock clicks. Inside the box — Mom’s earrings, two birth certificates, and a letter folded in four.', 3.4)},
    note('Почерк с наклоном: «Аня, в субботу ночью. Ждите на мостках — я подъеду с дороги. Только дети и документы. Никому ни слова, даже матери. — С.»',
         'A slanted hand: “Anya, Saturday night. Wait on the dock — I’ll drive up from the road. Just the children and the papers. Not a word to anyone, not even your mother. — S.”', 5.2),
    say('iris', '«Даже матери»… Он и бабушке не дал знать.', '“Not even your mother”… He made sure Grandma didn’t know either.'),
    say('noah', 'Он знал, где мы будем. И когда.', 'He knew where we’d be. And when.'),
    say('agnes', 'Вот почему она мне ничего не сказала. Он велел.', 'That’s why she never told me anything. He told her not to.', **IF(agnes_dead='==0')),
    {'set': {'c5_box': 1, 'c5_letter': 1}},
    note('Айрис прячет письмо во внутренний карман.', 'Iris tucks the letter into her inside pocket.', 2.4),
]
WRONG = [{'fx': 'snd:ui_click:-14', **note('Колёсики встают на место. Замок не поддаётся.', 'The little wheels click into place. The lock doesn’t give.', 2.2)}]

HOUSE_SPOTS = [
    {'id': 'pack', 'at': DK3(-2.2, 0.35, 3.05), 'where': DK(-1.45, 2.95), 'r': 0.6, 'stand': DK(-1.45, 2.95), 'face': -90,
     'label': L('За кроватью', 'Behind the bed'),
     'cam': [DK3(-2.45, 1.95, 2.0), DK3(-1.7, 0.45, 2.95), 50],
     'beats': [
         {'anim': {'who': 'noah', 'clip': 'Crouch_Idle'}},
         {'fx': 'backpack', **note('Между кроватью и стеной — детский рюкзак. Застёгнут на все молнии.', 'Wedged between the bed and the wall — a child’s backpack. Every zipper done up.', 3.0)},
         note('Внутри — свитер, фонарик, машинка без колеса. Собран в дорогу. Одиннадцать лет назад.',
              'Inside — a sweater, a flashlight, a toy car missing a wheel. Packed for a trip. Eleven years ago.', 3.4),
         say('noah', 'Я его сам собирал. Чтобы мама не ругалась, что долго.', 'I packed it myself. So Mom wouldn’t get mad that I was slow.'),
         say('iris', 'Ты и мой собрал. Я помню зайца в кармане.', 'You packed mine too. I remember the bunny in the pocket.'),
         {'set': {'c5_pack': 1}, 'anim': {'who': 'noah', 'clip': 'Idle'}},
     ]},
    {'id': 'cups', 'at': DK3(1.3, 0.9, 1.5), 'where': DK(1.3, 0.5), 'r': 0.6, 'stand': DK(1.3, 0.45), 'face': 0,
     'label': L('Стол', 'The table'),
     'cam': [DK3(2.45, 1.55, -0.3), DK3(1.25, 0.82, 1.45), 42],
     'beats': [
         note('На столе — три чашки вверх дном, чтобы не пылились. Одна — детская, с зайцем.',
              'On the table — three cups turned upside down to keep the dust out. One is a child’s, with a bunny on it.', 3.2),
         say('iris', 'Это моя. Я из неё молоко пила. Тёплое, с пенкой — гадость.', 'That one’s mine. I drank milk out of it. Warm, with skin on top — gross.'),
         note('Вторая — с отбитой ручкой. Ной откуда-то знает: его.', 'The second one has a chipped handle. Somehow Noah knows: it’s his.', 2.8),
     ]},
    {'id': 'tinbox', 'once': False, 'at': DK3(-2.72, 1.72, 0.45), 'where': DK(-2.2, 0.15), 'r': 0.6, 'stand': DK(-2.25, 0.15), 'face': -90,
     'label': L('Мамина шкатулка', 'Mom’s tin box'),
     'cam': [DK3(-1.6, 1.85, 1.2), DK3(-2.7, 1.62, 0.45), 40],
     'beats': [
         note('На полке — мамина жестяная шкатулка из-под чая. На крышке — маленький кодовый замок, четыре колёсика.',
              'On the shelf — Mom’s old tea tin. On the lid — a little combination lock, four wheels.', 3.4, **IF(c5_box='==0')),
         note('Айрис открывает дневник. На обороте обложки — четыре цифры маминым почерком: 1907.',
              'Iris opens the diary. On the inside of the cover — four digits in Mom’s handwriting: 1907.', 3.4, **IF(diary_hale='==0', c5_box='==0')),
         say('iris', 'Я думала, это телефон.', 'I thought it was a phone number.', **IF(diary_hale='==0', c5_box='==0')),
         say('iris', 'Дневник у него… Подожди. На обложке изнутри были цифры. Я думала, это телефон.',
             'He has the diary… Wait. There were numbers inside the cover. I thought it was a phone number.', **IF(diary_hale='>0', c5_box='==0')),
         note('Айрис закрывает глаза и беззвучно шевелит губами: один… девять… ноль… семь.',
              'Iris closes her eyes, her lips moving silently: one… nine… zero… seven.', 3.2, **IF(diary_hale='>0', c5_box='==0')),
         note('Шкатулка открыта. Письмо — у Айрис.', 'The box is open. Iris has the letter.', 2.2, **IF(c5_box='>0')),
     ],
     'ask': {'max': 3, 'opts': [
         {'ru': '1 · 7 · 0 · 9', 'en': '1 · 7 · 0 · 9', 'if': {'c5_box': '==0'}, 'beats': WRONG},
         {'ru': '1 · 9 · 0 · 7', 'en': '1 · 9 · 0 · 7', 'if': {'c5_box': '==0'}, 'beats': OPEN_BOX},
         {'ru': '0 · 7 · 1 · 9', 'en': '0 · 7 · 1 · 9', 'if': {'c5_box': '==0'}, 'beats': WRONG},
         {'leave': True, 'ru': 'Не открывать', 'en': 'Leave it closed', 'beats': [{'dur': 0.3}]},
     ]}},
]

HOUSE = {
    'who': 'noah',
    'start': {'at': DK3(1.4, 0.0, 0.3), 'face': -110},
    'walk': [DBOX(-2.58, -3.08, 2.58, 3.08)],
    'block': [
        DBOX(-2.55, -2.85, -1.25, -1.55),        # печь
        DBOX(-2.7, 0.78, -1.7, 2.82),            # кровать
        DBOX(0.83, 0.88, 1.77, 2.32),            # стол
        DBOX(2.2, 0.78, 2.6, 2.42),              # лавка
        DBOX(2.55, -2.95, 2.85, -2.25),          # вешалка с плащом
        DBOX(1.95, -2.5, 2.55, -1.9),            # Айрис у плаща
        DBOX(-0.8, -1.6, -0.2, -1.0, {'agnes_dead': '==0'}),
        DBOX(-1.2, 1.1, -0.6, 1.7, {'eva_alive': '>0'}),
        DBOX(0.6, 1.3, 1.2, 1.9, DANNY_HERE),
        DBOX(1.9, 0.9, 2.5, 1.5, ZOE_HERE),
    ],
    'floors': [DBOX(-2.8, -3.3, 2.8, 3.3, DF)],
    'cam_block': [[-999.0, -999.0, DK(-2.78, 0)[0], 999.0, 99.0], [DK(2.78, 0)[0], -999.0, 999.0, 999.0, 99.0],
                  [-999.0, -999.0, 999.0, DK(0, -3.28)[1], 99.0], [-999.0, DK(0, 3.28)[1], 999.0, 999.0, 99.0]],
    'nav': [DK(1.4, 0.3), DK(0.2, 0.4), DK(-0.9, 2.6), DK(-1.45, 2.95), DK(1.3, 0.45), DK(-2.25, 0.15), DK(-0.6, -0.4)],
    'spots': HOUSE_SPOTS,
    'nudge': {'after': 150, 'beats': [note('От печи тянет теплом. Остальные ждут.', 'Warmth drifts over from the stove. The others are waiting.', 2.4)]},
    'exit': {'id': 'warm', 'at': DK3(-1.3, 1.0, -1.45), 'where': DK(-0.6, -0.4), 'r': 0.6, 'stand': DK(-0.6, -0.4), 'face': -130,
             'label': L('К печи — к остальным', 'To the stove — to the others'),
             'beats': [{'dur': 0.3}]},
}

# --------------------------------------------------------------------------------- у окна с Айрис: темы
IRIS_TURN = {'fx': 'iris_turn5', 'dur': 0.6}   # отвернулась от стекла — к Ною (план «в лицо», а не затылок у окна)
IRIS_TALK = {'ask': {'id': 'iris5', 'max': 2, 'opts': [
    {'ru': '«Зачем ты везла меня сюда?»', 'en': '“Why did you bring me here?”', 'beats': [
        say('noah', 'Зачем ты везла меня сюда? Только честно.', 'Why did you bring me here? Honestly.', shot='noah_room'),
        say('iris', 'Чтобы ты вспомнил. Ты один видел, что было той ночью. Без тебя ему ничего не будет.',
            'So you’d remember. You’re the only one who saw what happened that night. Without you, nothing happens to him.', shot='iris_room'),
        say('iris', 'И чтобы ты больше не пил его таблетки. Я хочу брата. Настоящего.', 'And so you’d stop taking his pills. I want my brother back. The real one.',
            shot='iris_room', **IF(trust='>=3')),
    ]},
    {'ru': '«Та ночь, дома…»', 'en': '“That night, at home…”', 'beats': [
        say('noah', 'Та ночь, дома… Это правда был я?', 'That night, at home… Was it really me?', shot='noah_room'),
        say('iris', 'Ты встал между нами. Если бы не ты — меня бы уже не было.', 'You stood between us. If it weren’t for you, I wouldn’t be here.',
            shot='iris_room', **IF(trust='>=2')),
        say('iris', 'Это был не совсем ты. Но и не он. Я не знаю, Ной. Правда не знаю.', 'It wasn’t quite you. But it wasn’t him either. I don’t know, Noah. I really don’t.',
            shot='iris_room', **IF(trust='<2')),
    ]},
    {'ru': '«Прости. За дневник»', 'en': '“I’m sorry. About the diary”', 'if': {'diary_hale': '>0'}, 'beats': [
        say('noah', 'Прости. За дневник.', 'I’m sorry. About the diary.', shot='noah_room'),
        say('iris', 'Ты отдал ему маму. Второй раз.', 'You gave Mom to him. A second time.', shot='iris_room', **IF(trust='<2')),
        say('iris', 'Ты хотел, чтобы он нас отпустил. Я знаю. Всё равно больно.', 'You wanted him to let us go. I know. It still hurts.',
            shot='iris_room', **IF(trust='>=2')),
    ]},
    {'ru': '«Что будет потом?»', 'en': '“What happens after?”', 'beats': [
        say('noah', 'А потом? Когда всё кончится?', 'And after? When it’s all over?', shot='noah_room'),
        say('iris', 'Меня отправят в детдом. А тебя — обратно к нему. Или в тюрьму.', 'They’ll send me to a children’s home. And you — back to him. Or to prison.',
            shot='iris_room'),
        say('iris', 'Бабушка говорит — к ней. Если разрешат.', 'Grandma says I can live with her. If they let me.', shot='iris_room', **IF(agnes_dead='==0')),
        say('noah', 'Я никому тебя не отдам.', 'I won’t let anyone take you.', shot='noah_room'),
    ]},
    {'leave': True, 'ru': 'Просто посидеть рядом', 'en': 'Just sit with her', 'beats': [
        note('Они сидят рядом и молчат. Печь потрескивает. Этого хватает.', 'They sit side by side in silence. The stove crackles. It’s enough.', 2.8, shot='iris_room'),
    ]},
]}}

NEW_FLAGS = ['c5_pack', 'c5_box', 'c5_letter', 'ev_letter']


def apply(DATA, EVIDENCE=None):
    SC = DATA['scenes']
    ex = DATA.setdefault('explore', {})
    ex['c5d'] = YARD
    ex['c5h'] = HOUSE
    for f in NEW_FLAGS:
        DATA['flags'].setdefault(f, 0)
    b = SC['c5_s1']['beats']
    # у дачи: «Дача по окна в снегу…» → прогулка (сарай, качели, поленница) → крыльцо: ключ под третьей ступенькой
    i = next(i for i, x in enumerate(b) if x.get('fx') == 'key')
    b.insert(i, {'fx': 'yard5'})
    b.insert(i + 1, {'explore': 'c5d'})
    # после ключа Ной встаёт и поворачивается к Айрис («Откуда ты знал?» — «…Не знаю» было спиной к камере, на корточках)
    k2 = next(k2 for k2, x in enumerate(b) if x.get('fx') == 'key')
    b.insert(k2 + 1, {'fx': 'key_up'})
    # в доме: после плаща и «Она мне тут косы заплетала» — прогулка (рюкзак, чашки, шкатулка), потом разговоры у печи
    j = next(j for j, x in enumerate(b) if x.get('go') == 'c5_s1_hub')
    b.insert(j, {'fx': 'house5'})
    b.insert(j + 1, {'explore': 'c5h'})
    # у окна с Айрис: темы по прохождению (после «…второго человечка»)
    t = SC['c5_t_iris']['beats']
    k = next(k for k, x in enumerate(t) if x.get('go') == 'c5_s1_hub')
    t.insert(k, IRIS_TURN)
    t.insert(k + 1, IRIS_TALK)
    return DATA
