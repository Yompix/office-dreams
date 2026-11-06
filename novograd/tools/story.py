#!/usr/bin/env python3
"""Сценарий сюжета Novograd. Отсюда собирается src/main/resources/novograd_story.json
(главы → миссии → шаги) и список реплик для озвучки (tools/voice_lines.json).
Запуск из папки novograd: python3 tools/story.py

Шаги (type):
  dialog   — разговор (lines, choices); показывается сразу
  talk     — подойти к персонажу и нажать на него, потом разговор
  goto     — дойти до места (place, radius); vehicle=true — только на машине; time — лимит, сек
  lesson   — сдать урок(и) (count, min — минимальная оценка)
  job      — выполнить задания работы (job, count)
  money    — накопить денег (amount)
  buy      — иметь предмет в инвентаре (item, count)
  sleep    — лечь спать в кровать
  collect  — найти улики (clues: [{place, name}])
  race     — забег по чекпоинтам против соперника (points, rival)
  drivetest— экзамен на права на машине (points, limit — макс. скорость в км/ч)
  party    — праздник в месте (place, music) — длится пока идёт разговор
  cutscene — облёт камеры (shots, lines)
  title    — заставка главы/«прошло N лет»
Действия (do) выполняются в начале шага: money, give, take, flag, age, edu, job, time, tp, spawn, despawn, car, msg.
"""
import json, os

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, '..', 'src', 'main', 'resources', 'novograd_story.json')
VOICES = os.path.join(HERE, 'voice_lines.json')

# Персонажи: имя, скин, голос XTTS, тон (полутона) для детских лет
CHARS = {
    'narrator': dict(name='Рассказчик', skin=None, voice='Damien Black', color='e0e0e0'),
    'gleb':     dict(name='Джейк', skin='c25', voice='Marcos Rudaski', color='ff7c6e'),
    'nika':     dict(name='Ники «Нитро»', skin='c20', voice='Asya Anara', color='ff4fd8'),
    'shtorm':   dict(name='Сторм', skin='c13', voice='Badr Odhiambo', color='7fd3ff'),
    'kirpich':  dict(name='Брик', skin='c39', voice='Torcull Diarmuid', color='9be564'),
    'petrov':   dict(name='Лейтенант Питерсон', skin='police', voice='Royston Min', color='6ea8ff'),
    'tema':     dict(name='Томми', skin='c15', voice='Viktor Menelaos', color='7cff8a'),
}

voice_lines = []
counter = {}


def L(who, text, mission):
    """Реплика с id для озвучки."""
    n = counter.get(mission, 0) + 1
    counter[mission] = n
    vid = f'{mission}_{n:02d}'
    voice_lines.append({'id': vid, 'who': who, 'text': text})
    return {'who': who, 'text': text, 'voice': vid}


def lines(mission, *pairs):
    return [L(w, t, mission) for w, t in pairs]


def dialog(mission, *pairs, choices=None, do=None, hint=None, coop=None):
    s = {'type': 'dialog', 'lines': lines(mission, *pairs)}
    if coop: s['coop'] = lines(mission + 'c', *coop)
    if choices:
        s['choices'] = [{'text': c[0], 'flag': c[1], 'reply': lines(mission, *c[2])} for c in choices]
    if do: s['do'] = do
    if hint: s['hint'] = hint
    return s


def talk(mission, npc, hint, *pairs, choices=None, do=None, coop=None):
    s = dialog(mission, *pairs, choices=choices, do=do, coop=coop)
    s['type'] = 'talk'
    s['npc'] = npc
    s['hint'] = hint
    return s


def cut(mission, shots, *pairs, do=None):
    """Кат-сцена: облёт камеры по точкам (from, to, секунды) + голос рассказчика. Свои id (…s_01), чтобы не сдвигать озвучку."""
    c = {'type': 'cutscene', 'shots': [{'from': a, 'to': b, 'sec': t} for a, b, t in shots], 'lines': lines(mission + 's', *pairs)}
    if do: c['do'] = do
    return c


def step(type_, hint, **kw):
    s = {'type': type_, 'hint': hint}
    s.update(kw)
    return s


chapters = []

# =====================================================================================  АКТ I
ch1 = {'id': 'a1', 'title': 'Акт I', 'subtitle': 'Уличные гонки', 'age': 22, 'missions': []}
m = 'a1m1'
ch1['missions'].append({'id': m, 'title': 'Добро пожаловать в Новоград', 'steps': [
    {'type': 'cutscene', 'id': 'intro', 'do': [['age', 22], ['edu', 16], ['time', 11600]],
     'shots': [{'from': 'cam_island_far', 'to': 'cam_dt_a', 'sec': 9}, {'from': 'cam_dt_a', 'to': 'cam_dt_b', 'sec': 7},
               {'from': 'cam_port_a', 'to': 'cam_port_b', 'sec': 7}, {'from': 'cam_ind_a', 'to': 'cam_ind_b', 'sec': 7}],
     'lines': lines(m,
        ('narrator', 'Новоград. Остров, где деньги делают ночью, а днём делают вид, что их нет.'),
        ('narrator', 'Небоскрёбы в центре. Порт, где пропадают контейнеры. Промзона, где правят «Чёрные Ящеры».'),
        ('narrator', 'Ты приехал сюда помочь брату. Ты ещё не знаешь, во что ввязался.'))},
    {'type': 'title', 'title': 'Акт I', 'subtitle': 'Уличные гонки', 'do': [['tp', 'garage_door'], ['spawn', 'gleb', 'garage_bay']]},
    talk(m, 'gleb', 'Найди брата Джейка в Нитро-Гараже',
         ('gleb', 'Братишка! Добрался-таки! Как же я рад тебя видеть.'),
         ('gleb', 'Слушай… я влип. Задолжал «Чёрным Ящерам» пять тысяч. Брик дал мне неделю.'),
         choices=[('Как ты умудрился?', 'a1_how', [('gleb', 'Взял у них на гараж. А потом клиенты кончились, а проценты — нет.')]),
                  ('Разберёмся. Что делать?', 'a1_help', [('gleb', 'Вот это я понимаю — родня!')])]),
    dialog(m, ('gleb', 'Быстро заработать можно только одним способом — ночные гонки. Трассу держит Ники «Нитро».'),
              ('gleb', 'Победитель забирает тысячу. Бери мою старушку у ворот — и на северную трассу!'),
           do=[['car', 'garage_door', 1], ['spawn', 'nika', 'race_start']]),
    step('goto', 'Езжай на старт гонок, на северную трассу (карта — M)', place='race_start', radius=10, vehicle=True),
]})

m = 'a1m2'
ch1['missions'].append({'id': m, 'title': 'Ники «Нитро»', 'steps': [
    talk(m, 'nika', 'Поговори с Ники Нитро',
         ('nika', 'Значит, это ты — брат Джейка? Он поручился за тебя. Это дорогого стоит.'),
         ('nika', 'Правило одно: приехал первым — забрал деньги. Твой соперник — Сторм. Он не любит проигрывать.'),
         do=[['spawn', 'shtorm', 'race_1']]),
    dialog(m, ('shtorm', 'Брат Джейка? Тот, что не может отдать долги? Я сделаю тебя на первом же повороте.'),
           choices=[('Посмотрим, кто кого.', 'a1_bold', [('shtorm', 'Смелый. Смелые обычно и разбиваются.')]),
                    ('Удачи, она тебе понадобится.', 'a1_cool', [('shtorm', 'Ха. Мне — нет.')])],
           do=[['despawn', 'shtorm']]),
    step('carrace', 'Гонка по трассе: проезжай отметки раньше Сторма', start='race_start',
         points=['race_1', 'race_2', 'race_3', 'race_4', 'race_5', 'race_finish'], rival='Сторм', rival_speed=62, variant=0),
    dialog(m, ('nika', 'Неплохо для первого раза! Держи свою тысячу.'),
              ('nika', 'Но в следующий раз Сторм приедет на нитро. Тебе тоже нужна закись азота. Загляни к брату.'),
           do=[['money', 1000], ['tp_npc', 'nika', 'race_finish']]),
]})

m = 'a1m3'
ch1['missions'].append({'id': m, 'title': 'Закись азота', 'steps': [
    step('tune', 'Поставь нитро в Нитро-Гараже: заезжай в гараж и нажми ПКМ по механику'),
    talk(m, 'gleb', 'Поговори с Джейком',
         ('gleb', 'Нажимаешь N — и держишь руль двумя руками. Только не разбей мне машину!'),
         ('gleb', 'Ники звонила: сегодня заезд через деловой центр. Улицы узкие, полиции полно. Осторожнее.')),
    step('carrace', 'Ночной заезд через деловой центр', start='dt_5', points=['dt_1', 'dt_2', 'dt_3', 'dt_4', 'dt_5'],
         rival='Сторм', rival_speed=78, rival_engine=2, variant=3, do=[['time', 15000]]),
    dialog(m, ('nika', 'Ты взял центр! Весь город об этом говорит.'),
              ('shtorm', 'Это ещё не конец, новенький. Совсем не конец.'),
           do=[['money', 1500]]),
]})

m = 'a1m4'
ch1['missions'].append({'id': m, 'title': 'Облава', 'steps': [
    dialog(m, ('petrov', 'Полиция Новограда! Всем оставаться на местах! Гонки окончены!'),
              ('nika', 'Копы! Сторм нас сдал! Разбегаемся!')),
    step('escape', 'Уйди от полиции: скройся из виду или перекрасься в Нитро-Гараже', stars=2, why='незаконные гонки'),
    dialog(m, ('gleb', 'Ушёл? Ну ты даёшь! Слушай… тут такое дело. За мной приехали Ящеры.'),
              ('gleb', 'Они везут меня в промзону, к Брику. Брат, выручай!')),
]})
chapters.append(ch1)

# =====================================================================================  АКТ II
ch2 = {'id': 'a2', 'title': 'Акт II', 'subtitle': 'Чёрные Ящеры', 'age': 22, 'missions': []}
m = 'a2m1'
ch2['missions'].append({'id': m, 'title': 'Брик', 'steps': [
    {'type': 'title', 'title': 'Акт II', 'subtitle': 'Чёрные Ящеры', 'do': [['spawn', 'kirpich', 'gang_yard'], ['despawn', 'gleb']]},
    step('goto', 'Езжай в промзону, в штаб «Чёрных Ящеров»', place='gang_hq', radius=8),
    talk(m, 'kirpich', 'Поговори с Бриком',
         ('kirpich', 'Так вот ты какой, гонщик. Весь город гудит про тебя.'),
         ('kirpich', 'Твой брат у меня. Живой, не переживай. Пока что.'),
         ('kirpich', 'Долг — пять тысяч. Можешь отработать. Мне как раз нужен хороший водитель.'),
         choices=[('Отпусти его — деньги будут.', 'a2_money', [('kirpich', 'Деньги — это хорошо. Но работа — лучше.')]),
                  ('Если с ним что-то случится…', 'a2_threat', [('kirpich', 'Угрожаешь Брику? Смело. Посмотрим, как ты работаешь.')])]),
]})

m = 'a2m2'
ch2['missions'].append({'id': m, 'title': 'Золотая тачка', 'steps': [
    dialog(m, ('kirpich', 'Возле башни банка стоит золотая машина. Хозяин мне должен. Пригони её сюда.'),
              ('kirpich', 'Без царапин. Каждая царапина — сотня к долгу брата.')),
    step('steal', 'Угони золотую машину у банка в деловом центре и пригони во двор Ящеров', place='bank_tower', to='gang_yard', variant=3),
    dialog(m, ('kirpich', 'Чистая работа. Минус тысяча из долга. Мне нравится, как ты водишь.'),
           do=[['flag', 'a2_car']]),
]})

m = 'a2m3'
ch2['missions'].append({'id': m, 'title': 'Засада в порту', 'steps': [
    dialog(m, ('kirpich', 'Забери мой груз в порту, на складе у причала. И держи ствол — там крутятся чужие.'),
           do=[['give', 'novograd:pistol', 1], ['give', 'novograd:ammo', 60]]),
    step('goto', 'Езжай в порт, к складу у причала', place='port_warehouse', radius=10),
    step('clear', 'Засада! Отбейся от банды «Синих»', place='port_warehouse', count=5),
    dialog(m, ('kirpich', 'Живой? Значит, ты мне подходишь. Груз у тебя? Отлично. Минус ещё две тысячи.'),
           do=[['money', 300]]),
]})

m = 'a2m4'
ch2['missions'].append({'id': m, 'title': 'Большое дело', 'steps': [
    talk(m, 'kirpich', 'Вернись к Брику в промзону',
         ('kirpich', 'Последнее дело — и брат свободен. Бронефургон с выручкой казино пойдёт по северной трассе.'),
         ('kirpich', 'Разбей его — деньги наши. Возьми у оружейника автомат, он тебе пригодится.'),
         do=[['give', 'novograd:smg', 1], ['give', 'novograd:ammo', 120]]),
    step('chase', 'Догони бронефургон и разбей его (стреляй по машине или тарань)', points=['hw_north', 'race_1', 'race_2', 'race_3'], driver='Инкассатор', variant=4),
    step('escape', 'Уходи от полиции!', stars=3, why='ограбление инкассаторов'),
    talk(m, 'kirpich', 'Привези деньги Брику',
         ('kirpich', 'Ха-ха! Вот это я понимаю — работа!'),
         ('kirpich', 'А теперь — маленький сюрприз. Полиция уже знает, кто грабил фургон. Ты.'),
         ('kirpich', 'Кто-то должен сесть. И это буду не я. Прощай, гонщик.')),
    dialog(m, ('petrov', 'Полиция! Руки за голову! Вы задержаны за ограбление!'),
           do=[['busted']]),
]})
chapters.append(ch2)

# =====================================================================================  АКТ III
ch3 = {'id': 'a3', 'title': 'Акт III', 'subtitle': 'Под прикрытием', 'age': 22, 'missions': []}
m = 'a3m1'
ch3['missions'].append({'id': m, 'title': 'Сделка', 'steps': [
    {'type': 'title', 'title': 'Акт III', 'subtitle': 'Под прикрытием', 'do': [['spawn', 'petrov', 'police_station']]},
    talk(m, 'petrov', 'Поговори с лейтенантом Питерсоным в участке',
         ('petrov', 'Лейтенант Питерсон. Тебя подставили — и мы оба это знаем.'),
         ('petrov', 'Брика мы ловим три года. Он скользкий, как угорь. Но тебе он доверяет.'),
         ('petrov', 'Предложение: работаешь на нас под прикрытием — и обвинения снимут. Нет — десять лет за решёткой.'),
         choices=[('Я в деле.', 'a3_yes', [('petrov', 'Правильный выбор. Мой техник Томми свяжется с тобой.')]),
                  ('Выбора у меня нет, да?', 'a3_forced', [('petrov', 'Выбор есть всегда. Просто этот — лучший.')])]),
]})

m = 'a3m2'
ch3['missions'].append({'id': m, 'title': 'Жучок', 'steps': [
    dialog(m, ('tema', 'Привет, я Томми, техник Питерсона. Не бойся, я на твоей стороне.'),
              ('tema', 'Нужно поставить жучок в штабе Ящеров. Брик думает, что ты сидишь, так что будь тихим.')),
    step('collect', 'Поставь жучок в штабе «Чёрных Ящеров» (ПКМ по метке)', clues=[{'place': 'gang_hq', 'name': 'Жучок у стола Брика'}]),
    dialog(m, ('tema', 'Есть сигнал! Слышу Брика… Он собирается улетать с острова с аэродрома.'),
              ('tema', 'Ой… Он говорит про тебя. Тебя раскрыли! Уходи оттуда!')),
]})

m = 'a3m3'
ch3['missions'].append({'id': m, 'title': 'Крыса', 'steps': [
    step('clear', 'Ящеры раскусили тебя! Отбейся', place='industry_street', count=6, do=[['flag', 'gang_enemy']]),
    dialog(m, ('gleb', 'Брат! Я сбежал, пока они гонялись за тобой!'),
              ('gleb', 'Брик уже едет на аэродром. Возьми мою машину — я поставил ей всё, что было!'),
           do=[['spawn', 'gleb', 'industry_street'], ['car', 'industry_street', 0]]),
]})

m = 'a3m4'
ch3['missions'].append({'id': m, 'title': 'Взлётная полоса', 'steps': [
    dialog(m, ('petrov', 'Брик рвётся к аэродрому. Если он взлетит — мы его больше не увидим. Не дай ему уйти!')),
    step('chase', 'Погоня за Бриком! Разбей его машину', points=['industry_street', 'race_4', 'race_3', 'race_2', 'airfield'], driver='Брик', variant=3),
    step('clear', 'Финальная перестрелка в ангаре: возьми Брика', place='airfield_hangar', count=5, boss='Брик'),
    {'type': 'cutscene', 'id': 'ending', 'do': [['wanted_clear'], ['time', 23500]],
     'shots': [{'from': 'cam_air_a', 'to': 'cam_island_far', 'sec': 12}, {'from': 'cam_dt_a', 'to': 'cam_dt_b', 'sec': 8}],
     'lines': lines(m,
        ('narrator', 'Брика взяли на рассвете, у самого трапа самолёта.'),
        ('narrator', 'Джейк снова чинит машины. Ники опять держит трассу — теперь честную.'),
        ('narrator', 'А город… город живёт дальше. И у него на тебя ещё много планов.'))},
    dialog(m, ('petrov', 'Обвинения сняты. Ты свободен. Но знаешь… из тебя вышел бы отличный детектив.'),
              ('gleb', 'Или лучший гонщик острова! Ники уже спрашивала, когда следующий заезд.'),
           choices=[('Остаюсь с полицией.', 'end_cop', [('petrov', 'Добро пожаловать в команду, напарник.')]),
                    ('Моё место — на трассе.', 'end_racer', [('gleb', 'Вот это я понимаю! Гараж всегда твой.')])]),
    {'type': 'credits'},
]})
chapters.append(ch3)

# =====================================================================================  ПОБОЧНЫЕ МИССИИ (100)
import random as _r
RND = _r.Random(2026)
side = {}


def T(who, text):
    """Реплика без озвучки (побочные задания — текстом)."""
    return {'who': who, 'text': text, 'voice': ''}


def say(*pairs):
    return {'type': 'dialog', 'lines': [T(w, t) for w, t in pairs]}


DISTRICTS = {
    'центр': (180, 690, -690, -180), 'промзона': (180, 690, 180, 690),
    'пригород': (-540, -150, -540, 540), 'юг': (-130, 130, 180, 540), 'север': (-130, 130, -540, -180),
}


def snap(v):
    return round((v - 24) / 48) * 48 + 24


def walk(district, n):
    """Маршрут по перекрёсткам района: каждая следующая точка — соседний перекрёсток (по улице)."""
    x1, x2, z1, z2 = DISTRICTS[district]
    x, z = snap(RND.randint(x1 + 48, x2 - 48)), snap(RND.randint(z1 + 48, z2 - 48))
    pts, d = [(x, z)], RND.randint(0, 3)
    while len(pts) < n:
        if RND.random() < 0.35:
            d = (d + RND.choice([1, 3])) % 4
        dx, dz = [(48, 0), (0, 48), (-48, 0), (0, -48)][d]
        nx, nz = x + dx, z + dz
        if not (x1 <= nx <= x2 and z1 <= nz <= z2):
            d = (d + 2) % 4
            continue
        x, z = nx, nz
        if (x, z) not in pts:
            pts.append((x, z))
    return [f'@{x},{z}' for x, z in pts]


def spot(district):
    """Точка на тротуаре района."""
    x1, x2, z1, z2 = DISTRICTS[district]
    return f'@{snap(RND.randint(x1, x2)) + 5},{snap(RND.randint(z1, z2)) + 20}'


def add(mid, title, cat, giver, reward, unlock, steps):
    side[mid] = {'title': title, 'category': cat, 'giver': CHARS[giver]['name'], 'reward': reward, 'unlock': unlock, 'steps': steps}


names = ['Сторм', 'Ракета', 'Бес', 'Тень', 'Молния', 'Кобра', 'Дизель', 'Призрак', 'Барракуда', 'Пуля', 'Гроза', 'Стрела', 'Ураган', 'Комета', 'Буран']
dists = list(DISTRICTS)
for i in range(15):
    dd = dists[i % len(dists)]
    route = walk(dd, 7 + i % 4)
    add(f'race{i+1:02d}', f'Гонка #{i+1}: {dd}', 'Гонки Ники', 'nika', 300 + i * 120, 0 if i < 5 else 1 if i < 10 else 2, [
        say(('nika', f'Новый заезд: {dd}. Против тебя — {names[i]}. Не подведи!')),
        step('carrace', f'Гонка против «{names[i]}»: проезжай отметки первым', start=route[0], points=route[1:], rival=names[i],
             rival_speed=58 + i * 2, rival_engine=min(2, i // 5), variant=i % 6)])

cars = [('чёрный седан', 3), ('белый джип', 4), ('красный купе', 0), ('синий хэтчбек', 1), ('жёлтый спорткар', 2)]
for i in range(15):
    dd = dists[i % len(dists)]
    car, var = cars[i % len(cars)]
    to = 'garage_door' if i % 2 == 0 else 'port_dock'
    steps_ = [say(('kirpich' if i % 2 else 'gleb', f'Есть заказ: {car}, район «{dd}». Машина помечена золотом. Пригони её — {"в гараж" if to == "garage_door" else "в порт, к причалу"}.')),
              step('steal', f'Угони {car} ({dd}) и пригони {"в Нитро-Гараж" if to == "garage_door" else "в порт"}', place=walk(dd, 1)[0], to=to, variant=var)]
    if i % 3 == 2:
        steps_.insert(2, step('escape', 'Хозяин вызвал полицию — оторвись!', stars=1 + i % 3, why='угон'))
    add(f'steal{i+1:02d}', f'Угон: {car} ({dd})', 'Угоны на заказ', 'kirpich' if i % 2 else 'gleb', 400 + i * 40, 0 if i < 6 else 1, steps_)

crimes = ['грабитель ювелирного', 'угонщик', 'наркокурьер', 'вор с сумкой', 'похититель картин']
for i in range(15):
    dd = dists[i % len(dists)]
    if i % 2 == 0:
        route = walk(dd, 7)
        st = [say(('petrov', f'Срочно! {crimes[i % len(crimes)].capitalize()} уходит на машине, район «{dd}». Останови его!')),
              step('chase', 'Догони и разбей машину беглеца', points=route, driver=crimes[i % len(crimes)].capitalize(), variant=i % 5)]
    else:
        place = spot(dd)
        st = [say(('petrov', f'Вооружённые грабители в районе «{dd}». Нужна помощь — полиция не успевает!')),
              step('goto', f'Езжай к месту ограбления ({dd})', place=place, radius=14),
              step('clear', 'Обезвредь грабителей', place=place, count=3 + i % 4)]
    add(f'cop{i+1:02d}', f'Дело №{i+1}: {crimes[i % len(crimes)]}', 'Дела Питерсона', 'petrov', 500 + i * 50, 1, st)

turf = ['промзона', 'промзона', 'пригород', 'юг', 'центр']
for i in range(15):
    dd = turf[i % len(turf)]
    place = spot(dd)
    boss = f'Главарь «{["Синих", "Волков", "Акул"][i % 3]}»' if i % 5 == 4 else ''
    add(f'turf{i+1:02d}', f'Война за район #{i+1} ({dd})', 'Войны за районы', 'gleb', 600 + i * 70, 1 if i < 8 else 2, [
        say(('gleb', f'Чужая банда засела в районе «{dd}». Если их не выбить — они придут к нам в гараж.')),
        step('goto', f'Езжай в район «{dd}»', place=place, radius=16),
        step('clear', 'Выбей банду из района' + (' и разберись с главарём' if boss else ''), place=place, count=4 + i % 5, **({'boss': boss} if boss else {}))])

for i in range(12):
    a_, b_ = RND.sample(dists, 2)
    pa, pb = spot(a_), spot(b_)
    st = [say(('tema', f'Нужно передать жёсткий диск. Забери его в районе «{a_}» и отвези в «{b_}». Быстро — за тобой могут следить.')),
          step('goto', f'Забери диск ({a_})', place=pa, radius=6, time=120 + i * 5),
          step('goto', f'Доставь диск ({b_})', place=pb, radius=6, time=150 + i * 5)]
    if i % 4 == 3:
        st.append(step('escape', 'Хвост! Оторвись от полиции', stars=2, why='подозрительная доставка'))
    add(f'tema{i+1:02d}', f'Курьер Томми #{i+1}: {a_} → {b_}', 'Задания Томми', 'tema', 250 + i * 40, 2 if i >= 6 else 1, st)

rings = [['race_1', 'race_2', 'race_3', 'race_4'], ['hw_north', 'race_1', 'race_2'], ['race_4', 'race_5', 'race_finish'],
         ['hw_west', '@-552,-600', '@-300,-744', 'hw_north'], ['@744,300', '@744,600', '@400,744', '@100,744']]
for i in range(10):
    r_ = rings[i % len(rings)]
    add(f'test{i+1:02d}', f'Тест-драйв #{i+1}', 'Тест-драйвы Джейка', 'gleb', 300 + i * 50, 0, [
        say(('gleb', 'Я собрал новый мотор. Обгони мою контрольную машину — и мотор оправдал себя.')),
        step('carrace', 'Обгони контрольную машину Джейка', start=r_[0], points=r_[1:], rival='Контрольная', rival_speed=65 + i * 2, rival_engine=1, variant=4)])

for i in range(10):
    dd = dists[i % len(dists)]
    add(f'stash{i+1:02d}', f'Тайники #{i+1} ({dd})', 'Тайники', 'tema', 500, 0, [
        say(('tema', f'В районе «{dd}» спрятаны три тайника старой банды. Найди их — поделим находку.')),
        step('collect', f'Найди 3 тайника в районе «{dd}»', clues=[{'place': spot(dd), 'name': f'Тайник {k+1}'} for k in range(3)])])

for i in range(8):
    add(f'heat{i+1:02d}', f'Испытание: {2 + i // 2}★', 'Розыск', 'nika', 300 + i * 150, 1, [
        say(('nika', f'Спорим, ты не уйдёшь от {2 + i // 2} звёзд? Ставлю {300 + i * 150} долларов.')),
        step('escape', f'Уйди от розыска {2 + i // 2}★', stars=2 + i // 2, why='спор с Ники')])

print('побочных миссий:', len(side))

story = {'characters': CHARS, 'chapters': chapters, 'side': side}
with open(OUT, 'w', encoding='utf-8') as f:
    json.dump(story, f, ensure_ascii=False, indent=1)

# фразы жителей «на улице»: ключ, текст, когда уместно. Теги: morning/day/evening/night — время суток,
# to_kid/to_adult — кому говорят, rain — в дождь, greet — приветствие при первой встрече, again — подошёл снова.
AMBIENT = {
    'kid': [
        ('hi', 'Привет!', 'greet'),
        ('morning', 'Доброе утро!', 'greet morning'),
        ('dusk', 'Привет! Уже темнеет…', 'greet evening night'),
        ('football', 'Пошли играть в футбол!', 'day evening'),
        ('test', 'У меня завтра контрольная…', 'day evening'),
        ('console', 'Ты видел новую приставку?', 'day evening'),
        ('lunch', 'Мама зовёт обедать!', 'day'),
        ('dog', 'А у тебя есть собака?', 'morning day evening'),
        ('race', 'Спорим, я быстрее добегу до школы?', 'morning day'),
        ('holidays', 'Каникулы скорее бы!', 'morning day'),
        ('late', 'Мне уже домой пора, а то влетит!', 'evening night'),
        ('puddles', 'Ура, лужи! Кто больше набрызгает?', 'rain'),
        ('again', 'Мы же уже виделись сегодня!', 'again'),
    ],
    'adult': [
        ('morning', 'Доброе утро!', 'greet morning'),
        ('day', 'Добрый день!', 'greet day'),
        ('evening', 'Добрый вечер!', 'greet evening'),
        ('night_kid', 'Поздно уже. Беги домой, родители волнуются.', 'greet night to_kid'),
        ('night', 'Тоже не спится?', 'greet night to_adult'),
        ('weather', 'Хорошая погода сегодня.', 'morning day'),
        ('rain', 'Ну и дождь сегодня… Зонт забыл.', 'rain'),
        ('traffic', 'Опять пробки на Центральной…', 'morning evening to_adult'),
        ('bank', 'Не подскажете, где банк?', 'day to_adult'),
        ('luck', 'Удачи!', 'morning day evening'),
        ('club', 'Говорят, в клубе «Неон» сегодня вечеринка.', 'evening night to_adult'),
        ('coffee', 'Кофе на углу — лучший в городе.', 'morning'),
        ('work', 'Работа, работа… Когда же отпуск?', 'day evening to_adult'),
        ('school', 'А ты почему не в школе?', 'morning day to_kid'),
        ('study', 'Учись хорошо — в жизни пригодится.', 'day evening to_kid'),
        ('tired', 'Ох, ну и денёк был. Домой бы поскорее.', 'evening'),
        ('again', 'Мы же только что говорили.', 'again'),
    ],
    'old': [
        ('hi_kid', 'Здравствуй, внучек!', 'greet morning day evening to_kid'),
        ('hi', 'Здравствуй, милок.', 'greet morning day evening to_adult'),
        ('hat', 'Шапку надень, простудишься!', 'rain to_kid'),
        ('bread', 'Раньше хлеб был вкуснее.', 'morning day'),
        ('youth', 'Ох, молодёжь…', 'day evening'),
        ('walk', 'В моё время мы пешком ходили!', 'day evening'),
        ('pies', 'Заходи на пирожки!', 'day evening'),
        ('night', 'Ночь на дворе, а ты гуляешь! Иди спать.', 'greet night'),
        ('again', 'Я же тебе уже говорила… Или не тебе?', 'again'),
    ],
}
AMB_SPEAKERS = {
    'kid': {'f': ['Gracie Wise', 'Lilya Stainthorpe'], 'm': ['Andrew Chipper', 'Kumar Dahl']},
    'adult': {'f': ['Tammie Ema', 'Nova Hogarth'], 'm': ['Abrahan Mack', 'Baldur Sanjin']},
    'old': {'f': ['Brenda Stern', 'Uta Obando'], 'm': ['Wulf Carlevaro', 'Filip Traverse']},
}
for group, phrases in AMBIENT.items():
    for sex, speakers in AMB_SPEAKERS[group].items():
        for k, sp in enumerate(speakers):
            for key, ph, _ in phrases:
                if group == 'old' and key == 'again' and sex == 'm':
                    ph = 'Я же тебе уже говорил… Или не тебе?'
                pass  # прохожие без озвучки — только текст
# сервер берёт фразы отсюда (тот же текст, что озвучен)
with open(os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'novograd', 'ambient.json'), 'w', encoding='utf-8') as f:
    json.dump({g: [{'key': k, 'text': t, 'tags': tags.split()} for k, t, tags in ps] for g, ps in AMBIENT.items()}, f, ensure_ascii=False, indent=1)

with open(VOICES, 'w', encoding='utf-8') as f:
    json.dump(voice_lines, f, ensure_ascii=False, indent=1)
print('глав:', len(chapters), 'миссий:', sum(len(c['missions']) for c in chapters), 'реплик:', len(voice_lines))
