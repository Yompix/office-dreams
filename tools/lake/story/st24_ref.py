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
        B(fx='door_r_open', place={'who': 'iris', 'at': [15.55, 0.0, -4.65], 'face': 90, 'clip': 'Idle'}),
    ]


# ---------- эпизод 5 (магазин): у прилавка и три ветки выбора — подписи стали планами и действием ----------
NOAH_COUNTER = [26.4, 0.38, -7.62]
HANDS_COUNTER = [[26.2, 1.98, -9.0], [26.4, 1.42, -8.1], 40]      # руки Ноя на прилавке — сверху, глазами Романа
UNDER_COUNTER = [[25.35, 1.0, -9.45], [25.95, 1.18, -8.93], 46]  # низко, под прилавком: рука Романа к телефону
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
    for b in bs:   # Айрис идёт к ступеньке от своей двери (дверь закрывается за ней); Ной в глубине кадра открывает лючок
        if (b.get('walk') or {}).get('who') == 'iris' and b.get('shot') == 'st_two':
            b['fx'] = 'door_r_close'
        if b.get('say') == 'iris' and b.get('ru', '').startswith('Ты чего тут один'):
            b['fx'] = 'fuel_flap_open'
    shop(j)
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
