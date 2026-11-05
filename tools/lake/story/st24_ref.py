"""WHAT THE LAKE KEEPS, глава 1 — эталонная сцена заправки «24» по согласованному постановочному листу
(godot/lake/docs/staging/ST24_STAGING_SHEET.md, ответы редактора — docs/audit/EDITOR_DECISIONS_2026-11-04.md).
Правит godot/lake/data/ch1.json на месте; повторный запуск ничего не дублирует. Запускать ПОСЛЕДНИМ — после
ch1_walk.py, gestures.py и fixes_mp.py (они пишут прогулку st24 целиком):
    python3 tools/lake/story/st24_ref.py
Что делает (журнал — docs/staging/ST24_IMPLEMENTATION.md):
  * заправка — первое действие игрока (эпизод 1, шаг 7): пистолет в руку, «держать» до отсечки (табло бежит, отпустил —
    стоит, провала нет), пистолет обратно; подписи «Щелчок. Колонка встаёт…» и «Гул в голове не стихает» убраны;
  * ведро (эпизод 2): лёд — действие в два нажатия (трещины → пластины; 3 с без нажатия — Ной разбивает сам),
    подпись «Ной разбивает лёд…» убрана — её заменило действие.
"""
import json, os

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
DATA = os.path.join(ROOT, 'godot/lake/data/ch1.json')


def L(ru, en):
    return {'ru': ru, 'en': en}


REFUEL = [   # эпизод 1, шаг 7: Ной у горловины (лючок над левым задним колесом), камера от заднего угла машины —
    # рука с пистолетом в горловине, шланг от колонки, табло колонки за спиной Ноя
    {'ref': 'refuel', 'place': {'who': 'noah', 'at': [13.08, 0.0, -3.45], 'face': 90, 'clip': 'Idle'},
     'cam': [[13.15, 1.55, -1.15], [13.25, 1.0, -3.45], 40], 'fx': 'nozzle_take', 'dur': 1.0},
    {'ref': 'refuel', 'qte': 'hold', 't': 7.0, 'what': 'fuel',
     'hint': L('Заправить — держите [E]', 'Fill up — hold [E]')},
    {'ref': 'refuel', 'cam': [[13.05, 1.47, -4.62], [12.3, 1.42, -5.0], 28], 'dur': 1.4},   # отсечка: табло встало
    {'ref': 'refuel', 'fx': 'nozzle_back', 'dur': 1.0},
]
ICE = {'ref': 'ice', 'qte': 'ice', 'what': 'ice', 'hint': L('Разбить лёд — [E]', 'Break the ice — [E]')}
# крупно, низко спереди: открытое ведро, корка льда с трещинами, кулак Ноя, потом обе руки в воде (через плечо
# Ноя не видно — он наклоняется к воде корпусом и закрывает ведро спиной)
BUCKET_CAM = [[11.95, 0.95, -8.25], [12.5, 0.42, -8.56], 40]
# осмотр рук (лист «24», ведро, шаг 6): Ной отступил от ведра, поднял руки — ладонями вверх и обратно: ладони чистые,
# под ногтями и на манжетах бурое. Крупно спереди-сверху, видно кисти и манжеты
HANDS_CAM = [[12.4, 1.75, -8.75], [13.0, 1.24, -8.5], 30]
HANDS_AT = [13.12, 0.0, -8.5]   # на шаг от ведра: щётка в ведре не лезет в кисти


# ---------- эпизод 1, шаги 1–6 (сцена s5): постановка без управления, подписи стали планами ----------
ARRIVE_CAM = [[-2.6, 0.75, -6.0], [10.5, 1.4, 0.5], 56]          # шаг 1: общий с трассы, камера низко — подъезд к колонке
IN_BACK = [[14.62, 1.27, -3.8], [14.05, 0.92, -5.35], 54]        # шаг 2: салон с заднего сиденья (за Айрис) — ключ, приборы, ремень
# шаг 3: ступенька глазами Айрис — лёгкий наезд. Она оборачивается к заднему окну: из переднего ступеньку закрывают
# стойка B и колонка правого островка (проверено кадром 5 ноя)
IRIS_POV = [[14.85, 1.15, -4.25], [19.98, 0.9, -3.3], 36]
IRIS_POV2 = [[14.95, 1.14, -4.22], [19.98, 0.88, -3.3], 24]
DOOR_CAM = [[12.0, 1.4, -2.6], [13.4, 1.0, -4.9], 44]           # шаг 4: средний у водительской двери (сзади-слева)
BC_POV = [[12.9, 1.62, -3.6], [9.2, 1.0, -24.0], 32]            # шаг 6 (late): глазами Ноя на чёрную машину, наезд
BC_POV2 = [[12.85, 1.62, -3.95], [9.2, 1.0, -24.0], 22]
S5_CAPTIONS = ('Лесная заправка «24».', 'На ступеньке — мальчик', 'Других машин нет.', 'У края площадки, в темноте')


def intro_s5():
    I = {'ref': 'intro'}
    def B(**kw):
        d = dict(I)
        d.update(kw)
        return d
    return [
        B(place={'who': 'noah', 'car': 'driver'}),
        B(place={'who': 'iris', 'car': 'passenger'}),
        B(fx='car_arrive'),
        B(fx='fade_in', cam=ARRIVE_CAM, dur=2.0),
        B(fx='neon_flicker', dur=4.4),                                     # неон «4» мигает, машина встаёт у колонки
        B(cam=IN_BACK, fx='key_off', dur=1.6),                             # ключ — мотор глохнет, приборы гаснут
        B(fx='unbuckle', look={'who': 'iris', 'at': 'danny'}, dur=2.2),    # Айрис отстёгивается, смотрит на ступеньку
        B(cam=IRIS_POV, cam_to=IRIS_POV2, dur=2.6, fx='walkie_hide',
          gesture={'who': 'danny', 'do': 'warm_hands', 'dur': 2.5}),       # Дэнни дышит в ладони (рация — в коленях)
        B(look={'who': 'danny', 'at': 'noah'}, fx='walkie_show', dur=0.8), # поднимает глаза на машину…
        B(look={'who': 'danny', 'at': None}, dur=0.8),                     # …и сразу опускает
        B(cam=DOOR_CAM, fx='door_l_open', dur=1.1),                        # дверь примёрзла: рывок, хруст наледи
        # сидит на краю сиденья лицом наружу: ближе к стойке B — открытая на 44° дверь (до островка 0,76 м) впереди него
        B(place={'who': 'noah', 'at': [13.55, 0.06, -4.6], 'face': -90, 'clip': 'Sitting_Idle'}, dur=0.25),
        B(anim={'who': 'noah', 'clip': 'Sitting_Exit'}, fx='noah_step_out', dur=1.1),   # ботинки в снег — встаёт, выходит из проёма
        B(anim={'who': 'noah', 'clip': 'Idle'}, gesture={'who': 'noah', 'do': 'collar', 'dur': 1.4}, dur=1.4),
        B(walk={'who': 'noah', 'to': [13.08, 0.0, -3.45], 'then': 'Idle', 'face': 90}, fx='door_l_close', dur=1.3),   # на 18 см ближе: иначе рука не доставала до горловины
        # Айрис вышла (склейка): справа машина в 16 см от островка — правую дверь не открыть; идёт к ступеньке из-за машины
        B(place={'who': 'iris', 'at': [15.2, 0.0, -0.45], 'face': 60, 'clip': 'Idle'}, fx='snd:car_door:-14'),
    ]


# ---------- эпизод 5 (магазин): у прилавка и три ветки выбора — подписи стали планами и действием ----------
NOAH_COUNTER = [26.4, 0.38, -7.62]
HANDS_COUNTER = [[26.2, 1.98, -9.0], [26.4, 1.42, -8.1], 40]      # руки Ноя на прилавке — сверху, глазами Романа
UNDER_COUNTER = [[25.3, 1.36, -9.25], [26.05, 1.33, -8.95], 46]  # низко, вдоль задней стенки прилавка: правая рука Романа к телефону
WINDOW_OUT = [[19.4, 1.75, -8.4], [26.4, 1.85, -9.4], 18]        # с улицы сквозь витрину: Роман звонит (длинный фокус — читается)
CORD_CAM = [[27.35, 1.85, -7.35], [26.2, 1.4, -8.7], 42]         # из-за правого плеча Ноя: перегнулся через прилавок
SHOP_CAPTIONS = ('Кассир Роман.', 'Роман смотрит на экран', 'На его руки', 'Медленно тянется под прилавок')


def shop(j):
    S = {'ref': 'shop'}
    def B(**kw):
        d = dict(S)
        d.update(kw)
        return d
    sc = j['scenes']
    bs = sc['s5']['beats']
    i0 = next(i for i, b in enumerate(bs) if b.get('explore') == 'st24') + 1
    i1 = next(i for i, b in enumerate(bs) if b.get('go') == 's5_calm')
    bs[i0:i1] = [
        B(fx='roman_stool'),                                                         # табурет, руки скрещены, телевизор
        B(place={'who': 'noah', 'at': NOAH_COUNTER, 'face': 180, 'clip': 'Idle'}),
        B(shot='st_counter', dur=3.0),                                               # 1: через плечо Ноя на Романа
        B(shot='st_tv', dur=3.0),                                                    # 2: вставка — телевизор
        B(fx='roman_rise', shot='st_roman', dur=2.8),                                # 3: узнал — руки расходятся
        B(fx='noah_hands_counter', look={'who': 'roman', 'at': 'noah_hands'}, cam=HANDS_COUNTER, dur=2.6),   # 4: руки
        B(fx='noah_pockets', dur=1.0),                                               # заметил взгляд — руки в карманы
        B(fx='roman_phone_reach', look={'who': 'roman', 'at': 'noah'}, cam=UNDER_COUNTER, dur=2.6),          # 5
    ]
    calm = sc['s5_calm']['beats']
    if not any(b.get('fx') == 'roman_pickup' for b in calm):   # выбор: на краю кадра рука Романа поднимает трубку
        calm.insert(0, B(fx='roman_pickup'))
    say = lambda sid, who: next(b for b in sc[sid]['beats'] if b.get('say') == who)
    pay_line = dict(say('s5_pay', 'noah'))
    pay_line['gesture'] = {'who': 'noah', 'do': 'palms_out', 'dur': 2.0}   # «Не надо…» — показывает пустые ладони
    sc['s5_pay']['beats'] = [
        pay_line,
        B(fx='noah_bills', cam=HANDS_COUNTER, dur=3.2),                    # купюры из внутреннего кармана — на прилавок
        B(fx='noah_backoff', shot='st_counter', dur=2.6),                  # пятится к двери, не поворачиваясь спиной
        B(fx='roman_dial', cam=WINDOW_OUT, dur=3.2),                       # (новое) с улицы: Роман звонит в полицию
        {'set': {'police': 1}},
        {'go': 's6'},
    ]
    sc['s5_silent']['beats'] = [   # молчание (решение редактора): без слов кладёт деньги и пятится
        B(fx='noah_bills', cam=HANDS_COUNTER, dur=3.2),
        B(fx='noah_backoff', shot='st_counter', dur=2.6),
        B(fx='roman_dial', cam=WINDOW_OUT, dur=3.2),
        {'set': {'police': 1}},
        {'go': 's6'},
    ]
    sc['s5_cord']['beats'] = [
        B(fx='cord_rip', cam=CORD_CAM, dur=1.6),                           # перегнулся, кулаком за шнур — рывок
        B(fx='roman_recoil', shot='st_roman', dur=2.4),                    # ладони вперёд, шаг назад — пачки падают
        B(fx='cord_drop', shot='st_counter', dur=1.4),                     # бросает шнур, уходит
        B(fx='snd:door_chime:-10'),
        {'set': {'shadow': 1, 'police_late': 1}},
        {'go': 's6'},
    ]
    h0 = say('s5_help', 'noah')
    h1 = say('s5_help', 'roman')
    sc['s5_help']['beats'] = [
        h0,
        B(shot='st_roman', look={'who': 'roman', 'at': 'iris'}, dur=3.0),  # долго молчит, смотрит сквозь витрину на детей
        B(fx='roman_hand_counter', cam=HANDS_COUNTER, dur=1.8),            # рука поднимается пустой и ложится на прилавок
        dict(h1, look={'who': 'roman', 'at': 'noah'}),
        B(gesture={'who': 'noah', 'do': 'nod', 'dur': 0.8}, shot='st_counter', dur=1.2),
        B(fx='snd:door_chime:-10'),
        {'set': {'roman_ally': 1}},
        {'go': 's6'},
    ]


# ---------- эпизод 6 (провал памяти, s5_lapse): подписи — кадрами и звуком ----------
HANDS_PUMP = [[12.9, 1.3, -4.85], [13.22, 0.98, -4.3], 30]         # 3: крупно руки на пистолете, сбоку-спереди справа (было: камера внутри колонки — 2 с серого кадра)
IRIS_OTS = [[12.3, 1.7, -3.2], [19.3, 1.15, -3.5], 18]             # 7: Айрис у ступеньки — за кормой машины (было: через крышу машины, Айрис за ней не видно)
NOAH_FACE_PUMP = [[12.35, 1.62, -4.2], [13.0, 1.6, -4.1], 30]      # 8: назад из вспышки — Ной моргает
PUMP_MED = [[12.75, 1.5, -2.6], [12.8, 0.75, -5.0], 46]            # 9: пистолет падает и повисает у колонки, Ной уходит к двери (было: колонка закрывала и Ноя, и пистолет)
LAPSE_WINDOW = [[19.9, 2.0, -9.0], [25.9, 0.5, -10.0], 30]       # 5: сквозь витрину (край рамы в кадре) — Роман на полу между прилавком и полкой
LAPSE_CAPTIONS = ('Ной стоит у колонки', 'Стеклянная дверь магазина', 'Внутри на полу Роман', 'Мальчик на ступеньке')


# ---------- эпизод 3–4 (прогулка): багажник и разговор с Дэнни — подписи стали действием ----------
DANNY_IRIS = [[18.75, 1.3, -2.35], [20.0, 0.85, -3.65], 42]       # «Укройся»: видны оба — Айрис и Дэнни на ступеньке


IRIS_STEP_OTS = [[20.65, 1.3, -3.2], [13.6, 0.8, -8.1], 40]      # ведро, шаг 7: через плечо Айрис (и Дэнни) со ступеньки на Ноя


TRUNK_TOP = [[14.6, 2.05, -2.15], [14.3, 1.0, -3.0], 40]      # багажник сверху над правым плечом Ноя: чехол удочек, ящик, плед
VEND_COIN = [[19.75, 1.55, -10.2], [20.3, 1.25, -10.85], 44]    # автомат сбоку справа: рука у щели, батончики за стеклом
VEND_ROMAN = [[20.05, 1.62, -10.45], [26.2, 1.7, -9.35], 24]   # от автомата сквозь витрину: Роман поднимает голову
TRUNK_IRON = [[14.48, 1.72, -2.25], [14.33, 1.06, -2.97], 30]  # deer = 2: монтировка крупно, рука над ней


def talks(j):
    spots = {sp.get('id'): sp for sp in j['explore']['st24']['spots']}
    spots['trunk']['stand'] = [14.3, -2.55]   # у самого бампера: из прежней точки (метр от машины) рука не доставала в багажник
    spots['vending']['stand'] = [19.88, -11.0]   # плечи у модели Ноя ~22 см позади точки стояния — из 19,65 рука не доставала до щели
    for b in spots['trunk'].get('beats', []):   # багажник: подписи-описания → план сверху и действия (лист «24»)
        nt = (b.get('note') or {}).get('ru', '') if isinstance(b.get('note'), dict) else ''
        if nt.startswith('Отцовские удочки') or b.get('ref') == 'trunk_top':
            b.pop('note', None)
            b['ref'] = 'trunk_top'
            b['cam'] = TRUNK_TOP
            b['dur'] = 3.0
        elif nt.startswith('Монтировка брошена') or b.get('fx') == 'trunk_iron':
            b.pop('note', None)
            b['fx'] = 'trunk_iron'
            b['cam'] = TRUNK_IRON
            b['dur'] = 2.8
        elif nt.startswith('Ной не помнит'):   # мысль Ноя остаётся; на ней он касается чехла удочек
            b['fx'] = 'touch_rods'
            b['cam'] = TRUNK_TOP
    for b in spots['bucket'].get('after', []) + spots['bucket'].get('beats', []):   # ведро, шаг 7: подпись → взгляды и касание
        nt = (b.get('note') or {}).get('ru', '') if isinstance(b.get('note'), dict) else ''
        if nt.startswith('Айрис смотрит с крыльца') or b.get('fx') == 'iris_looks_away':
            b.pop('note', None)
            b.pop('look', None)
            b['fx'] = 'iris_looks_away'
            b['cam'] = IRIS_STEP_OTS
            b['dur'] = 3.4
    vb = spots['vending']['beats']   # автомат: подписи-описания → действие (лист «24»)
    for b in vb:
        nt = (b.get('note') or {}).get('ru', '') if isinstance(b.get('note'), dict) else ''
        if nt.startswith('Автомат: кофе') or b.get('fx') == 'vend_coin':
            b.pop('note', None)
            b['fx'] = 'vend_coin'
            b['cam'] = VEND_COIN
            b['dur'] = 4.4
    for o in spots['vending']['ask']['opts']:
        out = []
        for b in o.get('beats', []):
            nt = (b.get('note') or {}).get('ru', '') if isinstance(b.get('note'), dict) else ''
            an = (b.get('anim') or {}).get('clip', '')
            if an == 'Punch_Jab' or b.get('fx') == 'vend_slap':   # ладонью, а не кулаком
                out.append({'fx': 'vend_slap', 'cam': VEND_COIN, 'dur': 3.9})
            elif nt.startswith('Ной бьёт ладонью'):
                b.pop('note', None)
                b['dur'] = 0.1
                out.append(b)
            elif nt.startswith('В голове звенит'):
                b.pop('note', None)
                b['dur'] = 1.6
                out.append(b)
            elif nt.startswith('За витриной кассир') or (b.get('look') or {}).get('at') == 'noah' and b.get('cam') == VEND_ROMAN:
                b.pop('note', None)
                b['cam'] = VEND_ROMAN
                b['dur'] = 2.6
                out.append(b)
            elif nt.startswith('Ной отходит') or b.get('fx') == 'vend_tap':
                out.append({'fx': 'vend_tap', 'dur': 1.4})
            else:
                out.append(b)
        o['beats'] = out
    ib = spots['iris']['beats']   # разговор с Айрис: блокнот на коленях, «Нормально.» не поднимая глаз, прячет под худи
    if not any(b.get('fx') == 'iris_notebook' for b in ib):
        ib[0]['fx'] = 'iris_notebook'
    for b in ib:
        nt = (b.get('note') or {}).get('ru', '') if isinstance(b.get('note'), dict) else ''
        if b.get('say') == 'noah' and b.get('ru') == 'Ты как?':
            b.pop('look', None)   # не поднимает глаз
        if nt.startswith('Она прячет блокнот') or b.get('fx') == 'iris_notebook_hide':
            b.pop('note', None)
            b.pop('gesture', None)
            b['fx'] = 'iris_notebook_hide'
            b['dur'] = 2.0
        if b.get('say') == 'iris' and b.get('ru', '').startswith(('Иди плати', 'Плати и сразу')):
            b['look'] = {'who': 'iris', 'at': 'noah'}   # подняла глаза — уже без блокнота
    db = spots['danny']['beats']   # разговор с Дэнни: «Ной прячет руки в карманы» — жестом (подпись убрана)
    for b in db:
        nt = (b.get('note') or {}).get('ru', '') if isinstance(b.get('note'), dict) else ''
        if nt.startswith('Ной прячет руки'):
            b.pop('note', None)
    for o in spots['danny']['ask']['opts']:
        for b in o.get('beats', []):
            nt = (b.get('note') or {}).get('ru', '') if isinstance(b.get('note'), dict) else ''
            if nt.startswith('Он жмёт кнопку') or b.get('gesture', {}).get('do') == 'walkie_talk':
                b.pop('note', None)
                b['gesture'] = {'who': 'danny', 'do': 'walkie_talk', 'dur': 3.4}   # рацию к губам, кнопка — шипение
            elif nt.startswith('Он смотрит на пустую трассу'):
                b.pop('note', None)
    for o in spots['trunk']['ask']['opts']:   # «Взять одеяло»: из-под ящика, встряхнул — под мышку (носит, пока не отдаст)
        for b in o.get('beats', []):
            if b.get('fx') in ('trunk_blanket', 'blanket_carry'):
                b.pop('note', None)
                b['fx'] = 'blanket_carry'
                b['dur'] = 1.6
    for o in spots['danny']['ask']['opts']:
        for b in o.get('beats', []):
            st = b.get('set') or {}
            if 'choco_given' in st:   # «Держи»: обеими руками, во внутренний карман, ладонь прижата, кивок
                b.pop('note', None)
                b.pop('gesture', None)
                b['fx'] = 'choco_give'
                b['dur'] = 3.8
            if 'blanket_given' in st:   # «Укройся»: Айрис встряхивает плед и закутывает мальчика по плечи
                b.pop('note', None)
                b['fx'] = 'blanket_give'
                b['dur'] = 3.6
                b['cam'] = DANNY_IRIS


# ---------- эпизод 3: доска, таксофон, витрина, трасса, чёрная машина — подписи-описания → кадры и действия ----------
BOARD_MED = [[24.65, 1.62, 2.55], [25.2, 1.48, 1.02], 36]        # средний на доску: надписи читаются, Ной справа от доски
POSTER_A = [[24.95, 1.6, 2.05], [25.47, 1.5, 1.05], 30]          # плакат целиком, палец Ноя на загнутом углу
POSTER_B = [[25.38, 1.74, 1.78], [25.47, 1.74, 1.05], 26]        # наезд — на название санатория (место главы 4), целиком
CORNER_CU = [[25.18, 1.32, 1.55], [25.74, 1.2, 1.05], 30]        # убрал палец — угол снова загибается (слева: рука Ноя
                                                                 # справа за углом, не закрывает его рукавом)
PHONE_MED = [[24.15, 1.62, 2.15], [23.2, 1.5, 1.3], 40]          # таксофон в ¾: трубка у уха, стальной шнур
PHONE_KEYS = [[23.27, 1.24, 1.44], [23.19, 1.43, 1.15], 36]      # крупно палец на металлических кнопках: снизу спереди —
                                                                 # цифры читаются (сбоку кнопки шли рейками), локоть руки
                                                                 # с трубкой — в 30 см (было 6 см: кадр изнутри рукава)
PHONE_FACE = [[22.74, 1.63, 1.27], [23.2, 1.64, 1.58], 28]       # лицо Ноя на длинных гудках: спереди слева, мимо стенки козырька
WINDOW_GLASS = [[20.25, 2.1, -5.8], [26.0, 1.0, -8.6], 40]       # сверху из-за плеча сквозь стекло: Роман у телевизора, ниже —
                                                                 # отражение лица Ноя поверх магазина (рука-козырёк его не заслоняет)
ROAD_MED = [[3.5, 1.35, -1.6], [6.2, 1.45, -3.0], 40]            # Ной у края площадки лицом к трассе, за ним огни заправки
ROAD_POV_L = [[6.05, 1.74, -3.0], [2.5, 0.4, 14.0], 50]          # его глазами: влево — их следы с трассы, позёмок…
ROAD_POV_R = [[6.05, 1.74, -3.0], [0.0, 1.0, -16.0], 50]         # …и вправо — пусто в обе стороны
TRACK_A = [[6.22, 1.74, -3.12], [5.3, 0.0, -7.5], 46]            # late: вторые следы — свежие, у самых ног…
TRACK_B = [[6.22, 1.74, -3.12], [9.1, 0.8, -23.6], 28]           # …камера ведёт по ним до чёрной машины
BC_OTS_A = [[10.98, 1.74, -14.5], [9.5, 1.15, -23.15], 30]       # через плечо Ноя на машину…
BC_OTS_B = [[10.92, 1.72, -14.75], [9.45, 1.2, -23.3], 16]       # …медленный наезд на лобовое
BC_REVERSE = [[12.6, 1.62, -15.45], [10.6, 1.6, -15.25], 26]     # профиль Ноя (лицо освещено навесом): взгляд не отрывается от
                                                                 # машины за кадром, отступает на шаг (запись 5 ноя: план от машины
                                                                 # на Ноя давал тёмный силуэт — лица не видно)
WARN_CAM = [[19.2, 1.5, -10.8], [19.4, 1.15, -4.6], 26]          # Айрис встаёт со ступеньки: «Ной! Не ходи туда.»
WARN_ZONE = [4.8, -13.4, 20.0, -12.0]                            # край площадки перед чёрной машиной (до точки «Присмотреться»)


def _nt(b):
    return (b.get('note') or {}).get('ru', '') if isinstance(b.get('note'), dict) else ''


def walk_spots(j):
    w = j['explore']['st24']
    spots = {sp.get('id'): sp for sp in w['spots']}
    # доска: средний план — надписи читаются; палец расправляет угол плаката, наезд на название; угол снова загибается
    sp = spots['board']
    sp['stand'] = [26.0, 1.42]   # справа от доски: левой рукой до угла плаката, сам не заслоняет доску
    sp['face'] = 190
    sp['where'] = [25.3, 1.8]
    sp['cam'] = BOARD_MED
    sp['beats'] = [
        {'ref': 'board', 'fx': 'board_look', 'cam': BOARD_MED, 'look': {'who': 'noah', 'at': [24.83, 1.4, 1.05]}, 'dur': 3.6},
        {'ref': 'board', 'fx': 'poster_corner', 'look': {'who': 'noah', 'at': [25.6, 1.4, 1.05]}, 'cam': POSTER_A, 'cam_to': POSTER_B, 'dur': 4.0},
        {'ref': 'board', 'cam': CORNER_CU, 'dur': 1.4},
        {'look': {'who': 'noah', 'at': None}},
    ]
    # таксофон: трубка ледяная — вздрогнул; набор — пальцем по кнопкам крупно; длинные гудки — лицо; мысль Ноя остаётся
    sp = spots['phone']
    sp['stand'] = [23.2, 1.5]   # ближе: плечо Ноя — за точкой стояния, из 1,8 палец не доставал до кнопок
    sp['cam'] = PHONE_MED
    sp['beats'] = [
        {'ref': 'phone', 'anim': {'who': 'noah', 'clip': 'Idle_TalkingPhone'}, 'fx': 'phone_pick', 'cam': PHONE_MED, 'dur': 0.9},
        {'ref': 'phone', 'fx': 'phone_cold', 'dur': 2.2},
    ]
    for o in sp['ask']['opts']:
        if o.get('ru') == 'Набрать домашний':
            thought = next((b for b in o['beats'] if _nt(b).startswith('Дома некому')), None)
            if thought is None:
                thought = next(b for b in o['beats'] if b.get('ref') == 'phone_home')
            thought['fx'] = 'phone_lower'   # на седьмом гудке медленно опускает трубку
            thought['ref'] = 'phone_home'
            thought.pop('cam', None)
            o['beats'] = [
                {'ref': 'phone', 'fx': 'phone_dial_home', 'cam': PHONE_KEYS, 'dur': 2.9},
                {'ref': 'phone', 'fx': 'phone_ringback', 'cam': PHONE_FACE, 'look': {'who': 'noah', 'at': [23.2, 1.5, 0.6]}, 'dur': 4.6},
                thought,
            ]
    # витрина: вплотную к стеклу, ладонь козырьком, отражение поверх магазина, пятно дыхания, тишина
    sp = spots['window']
    sp['stand'] = [20.85, -6.45]
    sp['face'] = 105
    sp['cam'] = WINDOW_GLASS
    sp['beats'] = [
        {'ref': 'window', 'anim': {'who': 'noah', 'clip': 'Idle'}, 'fx': 'window_look', 'look': {'who': 'noah', 'at': 'roman'}, 'cam': WINDOW_GLASS, 'dur': 4.6},
        {'look': {'who': 'noah', 'at': None}},
    ]
    # трасса: огляделся; его глазами — пусто в обе стороны, позёмок заметает их следы; late — вторые следы до чёрной машины
    sp = spots['road']
    sp['stand'] = [6.2, -3.0]
    sp['cam'] = ROAD_MED
    sp['beats'] = [
        {'ref': 'road', 'anim': {'who': 'noah', 'clip': 'Rk_LookAround'}, 'fx': 'road_look', 'cam': ROAD_MED, 'dur': 2.2},
        {'ref': 'road', 'cam': ROAD_POV_L, 'cam_to': ROAD_POV_R, 'dur': 4.6},
        {'ref': 'road', 'if': {'late': '>0'}, 'fx': 'road_late', 'cam': TRACK_A, 'cam_to': TRACK_B, 'dur': 4.8,
         'place': {'who': 'noah', 'at': [6.2, 0.0, -3.0], 'face': 172, 'clip': 'Idle'}},   # повернулся к следам (кадр — его глазами)
        {'anim': {'who': 'noah', 'clip': 'Idle'}},
    ]
    # чёрная машина: через плечо — наезд на лобовое (стёкла тёмные, в них красный неон); мысль Ноя — отступает на шаг
    sp = spots['blackcar']
    keep = next((b for b in sp['beats'] if _nt(b).startswith('Водителя не видно')), None)
    if keep is None:
        keep = next(b for b in sp['beats'] if b.get('ref') == 'bc_thought')
    keep.update({'ref': 'bc_thought', 'fx': 'step_back', 'cam': BC_REVERSE, 'dur': 3.4})
    sp['beats'] = [
        {'ref': 'blackcar', 'fx': 'blackcar_look', 'look': {'who': 'noah', 'at': [9.4, 1.2, -23.2]}, 'cam': BC_OTS_A, 'cam_to': BC_OTS_B, 'dur': 4.8},
        keep,
        {'look': {'who': 'noah', 'at': None}},
    ]
    # окрик Айрис — у края площадки, раньше точки «Присмотреться»: встаёт со ступеньки, шаг за ним; Ной останавливается
    for z in w.get('zones', []):
        if any(b.get('say') == 'iris' and b.get('ru') == 'Ной! Не ходи туда.' for b in z.get('beats', [])):
            say = next(b for b in z['beats'] if b.get('say') == 'iris')
            say['look'] = {'who': 'iris', 'at': 'noah'}
            z['rect'] = WARN_ZONE
            z['beats'] = [
                {'fx': 'iris_warn', 'cam': WARN_CAM, 'dur': 0.8},
                say,
                {'look': {'who': 'noah', 'at': 'iris'}, 'dur': 1.0},
                {'look': {'who': 'noah', 'at': None}},
            ]


def lapse(j):
    bs = j['scenes']['s5_lapse']['beats']
    if any(b.get('ref') == 'lapse' for b in bs):
        for b in bs:   # уже переставлено: только правки планов поверх
            if b.get('ref') == 'lapse' and (b.get('shot') == 'st_window' or (b.get('cam') and b['cam'][1] == [26.0, 0.5, -10.0])):
                b.pop('shot', None)
                b['cam'] = LAPSE_WINDOW
            # 5 ноя, сверка провала по видео веток: три плана стояли сквозь колонку или за крышей машины
            if b.get('ref') == 'lapse' and b.get('fx') == 'wake':
                b['cam'] = HANDS_PUMP
            if b.get('ref') == 'lapse' and b.get('fx') == 'nozzle_drop':
                b['cam'] = PUMP_MED
            if b.get('say') == 'iris':
                b.pop('shot', None)
                b['cam'] = IRIS_OTS
        return
    L_ = {'ref': 'lapse'}
    def B(**kw):
        d = dict(L_)
        d.update(kw)
        return d
    out = []
    for b in bs:
        nt = (b.get('note') or {}).get('ru', '') if isinstance(b.get('note'), dict) else ''
        tx = (b.get('text') or {}).get('ru', '')
        if b.get('say') == 'roman':
            out.append(b)
            out.append(B(fx='lapse_tv', shot='st_tv', dur=1.0))          # 1: фокус — на фото Ноя в телевизоре
        elif tx.startswith('Бьётся стекло'):
            b['mute'] = True                                             # 2: со звуком подпись не нужна
            out.append(b)
        elif b.get('fx') == 'wake':
            out.append(B(fx='wake', cam=HANDS_PUMP, dur=0.1))            # 3: резкая склейка — руки на пистолете
            out.append(B(fx='wake_drop', dur=3.5))
        elif nt.startswith('Стеклянная дверь'):
            out.append(B(shot='st_crack', fx='bell_swing', dur=2.5))     # 4: трещина звездой, колокольчик качается
        elif nt.startswith('Внутри на полу') and b['if'].get('shadow') == '==0':
            out.append(B(**{'if': b['if'], 'cam': LAPSE_WINDOW, 'fx': 'roman_groan', 'dur': 3.2}))   # 5: жив — стонет
        elif nt.startswith('Внутри на полу'):
            out.append(B(**{'if': b['if'], 'cam': LAPSE_WINDOW, 'dur': 3.4}))                          # 5: не шевелится
        elif nt.startswith('Мальчик на ступеньке'):
            out.append(B(shot='st_danny_stare', dur=3.0))                # 6: Дэнни не дышит, Айрис заслоняет
        elif b.get('say') == 'iris':
            b2 = dict(b)
            b2.pop('shot', None)
            b2['cam'] = IRIS_OTS                                         # 7: через плечо Ноя на Айрис
            out.append(b2)
        elif b.get('fx') == 'flash_dock':
            out.append(B(**{'if': b.get('if', {}), 'fx': 'memory_dock', 'dur': 2.0}))   # 8: рука на мостках, синий свет
            out.append(B(**{'if': b.get('if', {}), 'fx': 'memory_back', 'cam': NOAH_FACE_PUMP, 'dur': 0.8}))
            out.append(B(fx='nozzle_drop', cam=PUMP_MED, dur=1.4))     # 9: пистолет падает и повисает на шланге
            out.append(B(walk={'who': 'noah', 'to': [13.15, 0.0, -5.6], 'then': 'Idle', 'face': 180}, dur=1.8))
        else:
            out.append(b)
    j['scenes']['s5_lapse']['beats'] = out


# ---------- эпизод 7 (s6…): дети у машины, решение про Дэнни, отъезд с последствиями в витрине ----------
KIDS_CAM = [[12.3, 1.6, -0.7], [13.0, 1.15, -4.2], 40]           # средний у задней двери (сзади-слева машины)
NOAH_OTS = [[12.85, 1.78, -6.0], [12.95, 1.25, -3.6], 36]        # через плечо Ноя на Айрис и Дэнни
DOOR_HOLD = [[12.35, 1.45, -6.4], [13.0, 1.05, -4.0], 38]        # крупно: Ной у открытой задней двери
HANDS_LET = [[12.2, 1.05, -3.0], [12.85, 0.8, -3.55], 32]        # крупно руки сбоку: правая Айрис отпускает левую руку Дэнни (сзади камеру закрывала Айрис)
IN_DASH = [[14.62, 1.27, -3.8], [14.05, 0.92, -5.35], 54]         # салон: приборы загораются
PASSBY = [[12.2, 1.55, -7.2], [26.3, 1.55, -9.3], 8]              # мимо витрины: длинный фокус на Романа за стеклом — крупно, по пояс (что у него в руках, видно с улицы); столб навеса — вне кадра; машина — шторкой
PASSBY_LOW = [[20.4, 2.6, -8.4], [26.0, 0.55, -10.0], 36]         # после провала: сверху за прилавок (Роман на полу)
ROAD_BACK = [[-1.8, 1.0, -30.0], [13.0, 2.0, -6.0], 46]           # с трассы назад: машина уезжает, «4» подмигивает
EP7_CAPTIONS = ('Мальчик в ужасе пятится', 'Дэнни садится назад', 'Мальчик забивается в угол', 'Ной молчит. Айрис решает сама',
                'Она сажает мальчика назад', 'Айрис отпускает его руку')


def episode7(j):
    sc = j['scenes']
    E = {'ref': 'ep7'}
    def B(**kw):
        d = dict(E)
        d.update(kw)
        return d
    def says(sid):
        return [b for b in sc[sid]['beats'] if b.get('say')]
    def choice(sid):
        return next(b for b in sc[sid]['beats'] if b.get('choice'))
    # s6: дети подходят к машине; «Его Дэнни зовут» (если имя не спрашивали); выбор — через плечо Ноя
    s6 = sc['s6']['beats']
    if not any(b.get('ref') == 'ep7' for b in s6):
        name = next(b for b in s6 if b.get('say') == 'iris')
        name.pop('shot', None)
        name['cam'] = NOAH_OTS
        ch = choice('s6')
        ch['choice'].pop('shot', None)
        ch['choice']['cam'] = NOAH_OTS
        sc['s6']['beats'] = [B(fx='kids_to_car', cam=KIDS_CAM, dur=4.2), name, ch]
    # s6_a: «Поехали с нами» — тянется к двери, открывает и держит; после провала мальчик пятится, Айрис на корточках
    if not any(b.get('ref') == 'ep7' for b in sc['s6_a']['beats']):
        l0, rest = says('s6_a')[0], says('s6_a')[1:]
        l0 = dict(l0, fx='rear_door_open')
        l0.pop('shot', None)
        l0['cam'] = DOOR_HOLD
        out = [l0, B(**{'if': {'lapse1': '>0'}, 'fx': 'danny_back_away', 'cam': KIDS_CAM, 'dur': 1.6})]
        for r in rest:   # «Я с тобой. Он тебя не тронет.» — присев перед ним
            out.append(B(**{'if': r.get('if', {}), 'fx': 'iris_kneel', 'dur': 0.5}))
            r = dict(r)
            r.pop('shot', None)
            r['cam'] = KIDS_CAM
            out.append(r)
        out += [B(fx='danny_climb_in', cam=KIDS_CAM, dur=2.8), {'set': {'danny': 1}}, {'go': 's6_end'}]
        sc['s6_a']['beats'] = out
    # s6_a2: «Ладно. Садись назад» — открывает дверь; Дэнни садится (после провала — в дальний угол)
    if not any(b.get('ref') == 'ep7' for b in sc['s6_a2']['beats']):
        l0 = dict(says('s6_a2')[0], fx='rear_door_open')
        l0.pop('shot', None)
        l0['cam'] = DOOR_HOLD
        sc['s6_a2']['beats'] = [l0, B(fx='danny_climb_in', cam=KIDS_CAM, dur=2.8), {'set': {'danny': 1}}, {'go': 's6_end'}]
    # s6_b: Дэнни опускает глаза на рацию; второй выбор — крупно Ной у двери
    if not any(b.get('ref') == 'ep7' for b in sc['s6_b']['beats']):
        bb = sc['s6_b']['beats']
        for b in bb:
            if b.get('say'):
                b.pop('shot', None)
                b['cam'] = NOAH_OTS
        ch = choice('s6_b')
        ch['choice'].pop('shot', None)
        ch['choice']['cam'] = DOOR_HOLD
        i = bb.index(ch)
        bb.insert(i, B(look={'who': 'danny', 'at': 'danny_walkie'}, dur=1.2))
    # s6_c: молчание — Ной смотрит на трассу; Айрис сама сажает мальчика и пристёгивает
    if not any(b.get('ref') == 'ep7' for b in sc['s6_c']['beats']):
        sc['s6_c']['beats'] = [
            B(look={'who': 'noah', 'at': [0.0, 1.2, -30.0]}, cam=NOAH_OTS, dur=2.0),
            B(fx='iris_buckle', cam=KIDS_CAM, dur=3.4),
            {'set': {'danny': 1}}, {'go': 's6_end'}]
    # s6_leave: «Нет. Он остаётся» — крупно руки: Айрис отпускает руку; мальчик остаётся под навесом
    if not any(b.get('ref') == 'ep7' for b in sc['s6_leave']['beats']):
        l0 = dict(says('s6_leave')[0])
        l0.pop('shot', None)
        l0['cam'] = DOOR_HOLD
        rest = [b for b in sc['s6_leave']['beats'] if b.get('set') or b.get('toast')]
        sc['s6_leave']['beats'] = [l0, B(fx='iris_release', cam=HANDS_LET, dur=2.2), B(fx='danny_stays', dur=0.2)] + rest + [{'go': 's6_end'}]
    for b in sc['s6_leave']['beats']:   # правка поверх уже переставленного: план рук — сбоку (сзади закрывала Айрис)
        if b.get('fx') == 'iris_release':
            b['cam'] = HANDS_LET
    # отъезд: Ной садится за руль, мотор, фары; мимо витрины (что с Романом — по ветке); с трассы назад; «4» мигает
    sc['s6_end']['beats'] = [
        B(fx='noah_to_car', cam=DOOR_HOLD, dur=2.4),
        B(fx='engine_on', cam=IN_DASH, dur=1.6),
        B(fx='roman_window'),
        B(**{'if': {'lapse1': '==0'}, 'fx': 'car_depart', 'cam': PASSBY, 'dur': 3.6}),
        B(**{'if': {'lapse1': '>0'}, 'fx': 'car_depart', 'cam': PASSBY_LOW, 'dur': 3.6}),
        B(fx='neon_flicker', cam=ROAD_BACK, dur=3.4),
        B(**{'if': {'danny_left': '>0'}, 'fx': 'danny_alone', 'dur': 1.4}),
        {'go': 's7'},
    ]


def main():
    j = json.load(open(DATA, encoding='utf-8'))
    w = j['explore']['st24']
    # --- заправка — первое действие игрока; подписи-описания убраны ---
    intro = [b for b in w['intro'] if b.get('ref') != 'refuel'
             and (b.get('note') or {}).get('ru') not in ('Щелчок. Колонка встаёт: полный бак.', 'Гул в голове не стихает.')]
    for b in REFUEL:
        b['pre'] = True   # до управления: Ной у колонки, ходить нельзя (передача управления — после отсечки)
    w['intro'] = REFUEL + [b for b in intro if (b.get('note') or {}).get('ru') != 'Заплатить — в магазине.']
    w['goal'] = L('Заплатить — в магазине', 'Pay inside')   # шаг 8: подсказка интерфейса вместо подписи
    w['glide'] = True
    # --- ведро: лёд — действием ---
    sp = next(s for s in w['spots'] if s['id'] == 'bucket')
    sp['stand'] = [12.85, -8.52]   # ближе к ведру: из приседа рука достаёт до воды (было 13,0 — кисть зависала над краем)
    bt = sp['beats']
    if not any(b.get('qte') == 'ice' for b in bt):   # уже сделано — не трогать
        for i, b in enumerate(bt):
            if (b.get('note') or {}).get('ru', '').startswith('Ной разбивает лёд'):
                crouch = {'anim': b.get('anim', {'who': 'noah', 'clip': 'Crouch_Idle'}), 'dur': 0.7, 'ref': 'ice'}
                plunge = {'fx': b.get('fx', 'snd:splash:-14'), 'dur': 1.2, 'ref': 'ice'}   # руки в воду
                bt[i:i + 1] = [crouch, ICE, plunge]
                break
        else:
            raise SystemExit('st24/bucket: не нашёл шаг «Ной разбивает лёд»')
    # постановка у ведра: крупно ведро (синяя вода, тонкий лёд — видно, подпись не нужна), кулак бьёт по корке на каждом
    # нажатии, обе руки — в воду, вода розовеет (подпись не нужна); осмотр рук — крупным планом, без подписи
    for b in bt:
        nt = (b.get('note') or {}).get('ru', '')
        if nt.startswith('Ведро со щёткой для стёкол'):
            b.pop('note')
            b.update({'ref': 'ice', 'cam': BUCKET_CAM, 'dur': 1.6})
        elif b.get('ref') == 'ice' and b.get('anim'):
            b['fx'] = 'ice_ready'
        elif b.get('qte') == 'ice':
            b['cam'] = BUCKET_CAM
        elif b.get('ref') == 'ice' and b.get('fx') in ('snd:splash:-14', 'hands_in_water'):
            b.update({'fx': 'hands_in_water', 'cam': BUCKET_CAM, 'dur': 1.4})
        elif nt == 'Вода розовеет.':
            b.pop('note')
            b.update({'ref': 'ice', 'cam': BUCKET_CAM, 'dur': 2.2})
        elif nt.startswith('Ладони чистые') or b.get('fx') == 'hands_out':
            # крупный план рук есть — подпись-описание убрана: ладони вверх и обратно (жест check_hands)
            b.pop('note', None)
            b['fx'] = 'hands_out'
            b['cam'] = HANDS_CAM
            b['place'] = {'who': 'noah', 'at': HANDS_AT, 'face': -90, 'clip': 'Idle'}
            b['gesture'] = {'who': 'noah', 'do': 'check_hands', 'dur': 2.8}
            b['dur'] = 2.8
        if b.get('ref') == 'ice' and 'cam' in b:   # все планы у ведра — один крупный (повторный запуск обновляет)
            b['cam'] = BUCKET_CAM
    # --- вступление s5 (эпизод 1, шаги 1–6) ---
    bs = j['scenes']['s5']['beats']
    if any(b.get('ref') == 'intro' for b in bs):   # повторный запуск: шаги 1–5 — заново из intro_s5()
        first = next(i for i, b in enumerate(bs) if b.get('ref') == 'intro')
        n = sum(1 for b in bs[first:] if b.get('ref') == 'intro' and not b.get('if'))
        bs[first:first + n] = intro_s5()
    else:
        st = next(i for i, b in enumerate(bs) if b.get('station'))
        late = None
        rest = []
        for b in bs[st + 1:]:
            nt = (b.get('note') or {}).get('ru', '')
            if nt.startswith(S5_CAPTIONS):
                if nt.startswith('У края площадки'):
                    late = b
                continue
            if b.get('explore') and late is not None:   # шаг 6 — после разговора на ступеньке, перед заправкой
                late.pop('note', None)
                late.pop('shot', None)
                late.update({'cam': BC_POV, 'cam_to': BC_POV2, 'dur': 4.5, 'ref': 'intro'})
                rest.append(late)
                late = None
            rest.append(b)
        bs[st + 1:] = intro_s5() + rest
    for b in bs:   # Ной в глубине кадра открывает лючок, пока Айрис говорит с Дэнни
        if (b.get('walk') or {}).get('who') == 'iris' and b.get('shot') == 'st_two':
            b.pop('fx', None)
        if b.get('say') == 'iris' and b.get('ru', '').startswith('Ты чего тут один'):
            b['fx'] = 'fuel_flap_open'
    shop(j)
    talks(j)
    walk_spots(j)
    lapse(j)
    episode7(j)
    # Роман на табурете у телевизора — с самого начала прогулки (видно сквозь витрину), а не только у прилавка
    w['setup'] = [b for b in w.get('setup', []) if (b.get('place') or {}).get('who') != 'roman' and b.get('fx') != 'roman_stool']
    w['setup'].insert(0, {'fx': 'roman_stool', 'ref': 'shop'})
    # камера прогулки проходила сквозь верх колонок и столбы навеса (запись 5 ноя, переход к автомату: кадр красной
    # «шапки» колонки во весь экран) — для камеры они тоже препятствия: колонки до 2,15 м, столбы — до навеса
    for cb in ([11.6, -5.55, 12.4, -4.45, 2.15], [15.6, -5.55, 16.4, -4.45, 2.15],
               [11.82, -9.35, 12.18, -8.52, 4.7], [11.82, -1.48, 12.18, -0.65, 4.7],
               [15.82, -9.35, 16.18, -8.52, 4.7], [15.82, -1.48, 16.18, -0.65, 4.7],
               [22.85, 1.0, 23.55, 1.4, 2.15], [24.55, 1.0, 25.95, 1.12, 2.0]):   # козырёк таксофона, доска объявлений
        if cb not in w.setdefault('cam_block', []):
            w['cam_block'].append(cb)
    # эпизод 5: дверь магазина — рукой за ручку, за спиной закрывается доводчиком (лист «24», магазин, дверь 1–3)
    for dr in w.get('doors', []):
        if dr.get('id') == 'shop_door':
            dr['closer'] = True
            # открытая створка была неподвижной полосой-препятствием, закрытая — не препятствием вовсе: в неё можно было
            # зайти наполовину (владелец, 5 ноя). Теперь створка — препятствие там, где она сейчас (explore._leaf)
            dr['block'] = [b for b in dr['block'] if b[:4] != [21.1, -2.66, 22.28, -2.48]]
            # план сверху (проверка помещений, 5 ноя): без препятствия были табличка «Мокрый пол» у двери и коробки у
            # задней стены (реквизит drive_scene.park_at_station) — в них можно было зайти
            for b in ([21.7, -0.8, 22.1, -0.4], [28.95, -11.4, 30.15, -10.55]):
                if b not in dr['block']:
                    dr['block'].append(b)
            # прилавок шире препятствия на 7 см с каждого конца (срез на высоте тела) — препятствие по модели
            dr['block'] = [[24.78, -11.75, 28.02, -7.97] if b[:4] == [24.93, -11.75, 27.87, -7.97] else b for b in dr['block']]
            # камера в проходе у окна садилась над полкой и смотрела поверх товара (проверка двери 5 ноя): стеллажи и
            # холодильник — препятствие и для камеры (выше их на 0,7 м), она остаётся в проходе за спиной Ноя
            for cb in ([22.93, -5.72, 23.87, -1.48, 2.9], [25.13, -5.72, 26.07, -1.48, 2.9], [29.95, -6.62, 30.75, -2.38, 2.9]):
                if cb not in dr.setdefault('cam_block', []):
                    dr['cam_block'].append(cb)
    # Айрис на крыльце (s5): таз в 8 см от края, как у Дэнни (game.gd DANNY_SEAT) — раньше сидела в 30 см от края:
    # колени над бетоном, голени и стопы в ступеньке (владелец, кадр 4 ноя: «ноги в текстурах застревают»)
    for b in j['scenes']['s5']['beats']:
        if (b.get('sit') or {}).get('who') == 'iris':
            b['place']['at'] = [20.08, 0.38, -4.05]
            b['sit']['at'] = [19.98, 0.48, -4.05]
    open(DATA, 'w', encoding='utf-8').write(json.dumps(j, ensure_ascii=False, indent=1) + '\n')
    print('ch1.json: st24 — заправка удержанием, лёд в ведре действием')


if __name__ == '__main__':
    main()
