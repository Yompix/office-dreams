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
    {'ref': 'refuel', 'place': {'who': 'noah', 'at': [12.9, 0.0, -3.45], 'face': 90, 'clip': 'Idle'},
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
        B(walk={'who': 'noah', 'to': [12.9, 0.0, -3.45], 'then': 'Idle', 'face': 90}, fx='door_l_close', dur=1.3),
        # Айрис вышла (склейка): справа машина в 16 см от островка — правую дверь не открыть; идёт к ступеньке из-за машины
        B(place={'who': 'iris', 'at': [15.2, 0.0, -0.45], 'face': 60, 'clip': 'Idle'}, fx='snd:car_door:-14'),
    ]


# ---------- эпизод 5 (магазин): у прилавка и три ветки выбора — подписи стали планами и действием ----------
NOAH_COUNTER = [26.4, 0.38, -7.62]
HANDS_COUNTER = [[26.2, 1.98, -9.0], [26.4, 1.42, -8.1], 40]      # руки Ноя на прилавке — сверху, глазами Романа
UNDER_COUNTER = [[25.3, 1.36, -9.25], [26.05, 1.33, -8.95], 46]  # низко, вдоль задней стенки прилавка: правая рука Романа к телефону
WINDOW_OUT = [[19.4, 1.75, -8.4], [26.4, 1.75, -9.35], 30]       # с улицы сквозь витрину: Роман звонит
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
HANDS_PUMP = [[12.25, 1.38, -4.55], [12.92, 1.08, -4.12], 36]      # 3: крупно руки на пистолете
IRIS_OTS = [[12.55, 1.72, -4.45], [19.25, 1.45, -3.45], 32]        # 7: через плечо Ноя на Айрис у ступеньки
NOAH_FACE_PUMP = [[12.35, 1.62, -4.2], [13.0, 1.6, -4.1], 30]      # 8: назад из вспышки — Ной моргает
PUMP_MED = [[11.6, 1.45, -6.2], [12.9, 0.95, -4.3], 44]            # 9: средний у колонки — пистолет падает
LAPSE_WINDOW = [[19.9, 2.0, -9.0], [25.9, 0.5, -10.0], 30]       # 5: сквозь витрину (край рамы в кадре) — Роман на полу между прилавком и полкой
LAPSE_CAPTIONS = ('Ной стоит у колонки', 'Стеклянная дверь магазина', 'Внутри на полу Роман', 'Мальчик на ступеньке')


# ---------- эпизод 3–4 (прогулка): багажник и разговор с Дэнни — подписи стали действием ----------
DANNY_IRIS = [[18.75, 1.3, -2.35], [20.0, 0.85, -3.65], 42]       # «Укройся»: видны оба — Айрис и Дэнни на ступеньке


def talks(j):
    spots = {sp.get('id'): sp for sp in j['explore']['st24']['spots']}
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


def lapse(j):
    bs = j['scenes']['s5_lapse']['beats']
    if any(b.get('ref') == 'lapse' for b in bs):
        for b in bs:   # уже переставлено: только правки планов поверх
            if b.get('ref') == 'lapse' and (b.get('shot') == 'st_window' or (b.get('cam') and b['cam'][1] == [26.0, 0.5, -10.0])):
                b.pop('shot', None)
                b['cam'] = LAPSE_WINDOW
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
HANDS_LET = [[12.5, 1.35, -2.6], [12.98, 0.95, -3.55], 30]       # крупно руки: Айрис отпускает руку Дэнни
IN_DASH = [[14.62, 1.27, -3.8], [14.05, 0.92, -5.35], 54]         # салон: приборы загораются
PASSBY = [[12.2, 1.55, -9.0], [26.4, 1.7, -9.0], 30]              # мимо витрины: машина проходит в кадре, за стеклом — Роман
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
    lapse(j)
    episode7(j)
    # Роман на табурете у телевизора — с самого начала прогулки (видно сквозь витрину), а не только у прилавка
    w['setup'] = [b for b in w.get('setup', []) if (b.get('place') or {}).get('who') != 'roman' and b.get('fx') != 'roman_stool']
    w['setup'].insert(0, {'fx': 'roman_stool', 'ref': 'shop'})
    # эпизод 5: дверь магазина — рукой за ручку, за спиной закрывается доводчиком (лист «24», магазин, дверь 1–3)
    for dr in w.get('doors', []):
        if dr.get('id') == 'shop_door':
            dr['closer'] = True
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
