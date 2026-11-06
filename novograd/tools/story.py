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
    'rosa':     dict(name='Роза', skin='c22', voice='Sofia Hellen', color='ff8a65'),
    'vince':    dict(name='Винс', skin='c17', voice='Craig Gutsy', color='b0bec5'),
    'marco':    dict(name='Марко «Синий»', skin='c33', voice='Gilberto Mathias', color='4fc3f7'),
    'harris':   dict(name='Шеф Харрис', skin='police', voice='Baldur Sanjin', color='90a4ae'),
    'lena':     dict(name='Лена Вуд', skin='c28', voice='Tammie Ema', color='ffd54f'),
    'crow':     dict(name='Виктор Кроу', skin='c37', voice='Wulf Carlevaro', color='e53935'),
    'sofi':     dict(name='Софи', skin='c26', voice='Daisy Studious', color='f48fb1'),
    'ray':      dict(name='Рэй', skin='c19', voice='Eugenio Mataracı', color='4db6ac'),
    'shakal':   dict(name='Шакал', skin='c31', voice='Ferran Simen', color='ffb300'),
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
    step('escape', 'Уходи от полиции!', stars=3, why='ограбление инкассаторов', do=[['shock', 'CITY', -0.12, 'ограблен бронефургон с выручкой казино']]),
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
]})
chapters.append(ch3)


# =====================================================================================  0.2: КАТ-СЦЕНЫ С АКТЁРАМИ И НОВЫЕ МИССИИ
def S(a, b, sec, track=None, shake=None):
    """Кадр: камера летит из a в b; track — следить за героем."""
    d = {'from': a, 'to': b, 'sec': sec}
    if track: d['track'] = track
    if shake: d['shake'] = shake
    return d


def F(who, sec, off=(7, 3, 0), shake=None):
    """Кадр «за героем/машиной»: камера едет следом (off — назад, вверх, вбок)."""
    d = {'follow': who, 'sec': sec, 'off': list(off)}
    if shake: d['shake'] = shake
    return d


def scene(mission, shots, actors, *pairs, do=None):
    """Кат-сцена, где герои двигаются: shots — кадры, actors — [секунда, действие, …] (см. Director.java)."""
    c = {'type': 'cutscene', 'shots': shots, 'actors': actors, 'lines': lines(mission + 's', *pairs)}
    if do: c['do'] = do
    return c


def mission(mid, title, steps):
    return {'id': mid, 'title': title, 'steps': steps}


def insert_after(ch, after_id, *ms):
    i = next(k for k, x in enumerate(ch['missions']) if x['id'] == after_id)
    ch['missions'][i + 1:i + 1] = list(ms)


def find(ch, mid):
    return next(x for x in ch['missions'] if x['id'] == mid)


# ---------- вступление: машина гонщика пролетает по деловому центру, камера за ней
intro = ch1['missions'][0]['steps'][0]
intro['shots'].insert(1, F('racer', 7, (9, 2.5, 2.5)))
intro['shots'].insert(2, S('dt_2+0,30,3', 'dt_2+0,16,2.5', 4, track='racer'))
intro['actors'] = [[0, 'car', 'racer', 'dt_1', 2], [0.2, 'drive', 'racer', ['dt_2', 'dt_3'], 0.95]]

# ---------- a1m2: Сторм приезжает на старт, выходит и наезжает на Ники
m = 'a1m2'
find(ch1, m)['steps'].insert(0, scene(m,
    [S('race_start+22,-5,3', 'race_start+12,-4,2.5', 6, track='storm_car'), F('shtorm', 4, (3.5, 1.6, 1.5)), S('race_start+4,6,2', 'race_start+2,4,2', 5, track='nika')],
    [[0, 'spawn', 'nika', 'race_start+2,-3'], [0, 'act', 'nika', 'TALK'], [0, 'car', 'storm_car', 'race_start+60,0', 2, 90],
     [0.2, 'drive', 'storm_car', ['race_start+6,0'], 0.7], [5.5, 'spawn', 'shtorm', 'race_start+6,2'], [5.6, 'walk', 'shtorm', 'race_start+3,-2'],
     [8, 'face', 'nika', 'shtorm'], [9, 'act', 'shtorm', 'ARGUE', 'nika'], [11, 'act', 'nika', 'LAUGH', 'shtorm']],
    ('narrator', 'На трассу всегда приезжают двое: тот, кто хочет выиграть, и тот, кто не умеет проигрывать.'),
    ('shtorm', 'Ники, ты правда поставишь меня против новичка?'),
    ('nika', 'Я ставлю на тех, кто приезжает первым, Сторм. Сегодня посмотрим.')))

# ---------- новые миссии Акта I
m = 'a1m2b'
a1m2b = mission(m, 'Работа на колёсах', [
    dialog(m, ('gleb', 'Пока ты гоняешь, гараж простаивает. Клиент из пригорода ждёт свою машину.'),
              ('gleb', 'Отгони её на Тихую улицу. Аккуратно, это первая наша работа за месяц.'),
           do=[['car', 'garage_door', 1]]),
    step('goto', 'Отгони машину клиенту в пригород, на Тихую улицу', place='suburb_street', radius=9, vehicle=True, time=240),
    scene(m, [S('cam_suburb', 'suburb_street+10,8,3', 6, track='client'), S('suburb_street+3,4,2', 'suburb_street+2,3,2', 5, track='client')],
          [[0, 'spawn', 'client', 'suburb_house', 'citizen', 'Мистер Тейлор'], [0.5, 'walk', 'client', 'suburb_street+2,2'],
           [6, 'act', 'client', 'HANDSHAKE', 'player'], [9, 'act', 'client', 'LAUGH', 'player']],
          ('narrator', 'Мистер Тейлор обходит машину три раза. Потом довольно хлопает по капоту.'),
          ('narrator', 'Первые честные деньги в Новограде. Брат был бы горд.')),
    dialog(m, ('gleb', 'Клиент доволен! Половина — твоя. Видишь, и без гонок можно жить.'), do=[['money', 250]]),
])

m = 'a1m3b'
a1m3b = mission(m, 'Клуб «Неон»', [
    dialog(m, ('nika', 'Сегодня отмечаем твою победу в «Неоне». И познакомлю тебя с Розой — она знает в этом городе всех.')),
    step('goto', 'Езжай в клуб «Неон» в деловом центре', place='club_neon', radius=8, do=[['time', 16500]]),
    scene(m, [S('cam_club', 'club_neon+6,14,6', 7), S('club_neon+5,-3,2', 'club_neon+3,-2,2', 5, track='rosa'), F('rosa', 4, (3, 1.7, -1.2))],
          [[0, 'spawn', 'd1', 'club_neon+2,-3', 'citizen'], [0, 'spawn', 'd2', 'club_neon+-2,-4', 'citizen'], [0, 'spawn', 'd3', 'club_neon+0,-6', 'citizen'],
           [0, 'act', 'd1', 'DANCE'], [0, 'act', 'd2', 'DANCE'], [0, 'act', 'd3', 'DANCE'],
           [0, 'spawn', 'rosa', 'club_neon+4,-8'], [0, 'spawn', 'nika', 'club_neon+1,-1'], [0, 'act', 'nika', 'DANCE'],
           [7, 'walk', 'rosa', 'club_neon+1,0'], [12, 'act', 'rosa', 'TALK', 'player']],
          ('narrator', 'Клуб «Неон». Здесь не спрашивают, откуда деньги. Здесь спрашивают только — сколько.'),
          ('rosa', 'Так вот кто обогнал Сторма. Садись, гонщик, угощаю.')),
    talk(m, 'rosa', 'Поговори с Розой у барной стойки',
         ('rosa', 'Скажу тебе по секрету: Сторм не сам по себе. Его гонки оплачивает Марко — главарь «Синих».'),
         ('rosa', '«Синие» и «Ящеры» делят город. А твой брат должен Ящерам… Понимаешь, во что ты влез?'),
         choices=[('Спасибо, Роза. Я буду осторожен.', 'a1_rosa_ok', [('rosa', 'Осторожность — лучшее, что можно купить в Новограде.')]),
                  ('Мне плевать, кто чей. Я тут ради брата.', 'a1_rosa_hard', [('rosa', 'Так говорят все. А потом выбирают сторону.')])]),
])

m = 'a1m3c'
a1m3c = mission(m, 'Западная трасса', [
    dialog(m, ('nika', 'Новый вызов! Винс, механик Сторма, хочет реванша на западной трассе — вдоль пляжа.'),
              ('nika', 'Шоссе длинное и быстрое. Нитро береги на последнюю прямую.')),
    scene(m, [S('hw_west+16,6,3', 'hw_west+8,-4,2', 5, track='vince'), F('vince_car', 5, (8, 2.2, -2.5))],
          [[0, 'car', 'vince_car', 'hw_west+3,0', 5, 180], [0, 'spawn', 'vince', 'hw_west+5,-2'], [0.3, 'act', 'vince', 'ARGUE', 'player'],
           [4, 'ride', 'vince', 'vince_car'], [5, 'drive', 'vince_car', ['hw_west+0,-60'], 0.6]],
          ('vince', 'Ты сделал Сторма, но меня ты не сделаешь. Я эту трассу знаю наизусть.')),
    step('carrace', 'Гонка по западной трассе против Винса', start='hw_west', points=['hw_nw', 'race_start', 'race_1'],
         rival='Винс', rival_speed=74, rival_engine=2, variant=5),
    dialog(m, ('nika', 'Красавчик! Винс в бешенстве. Держи деньги.'),
              ('nika', 'Только… будь осторожен. Винс не прощает проигрышей.'), do=[['money', 1200], ['shock', 'NITR', 0.08, 'гонщик Нитро-Гаража выиграл западную трассу']]),
])

m = 'a1m3d'
a1m3d = mission(m, 'Сломанные тормоза', [
    scene(m, [S('garage_door+12,10,4', 'garage_door+8,6,3', 5, track='vince'), S('garage_door+3,3,1.6', 'garage_door+2,2,1.5', 4, track='vince'), F('vince', 4, (4, 2, 0))],
          [[0, 'spawn', 'vince', 'garage_door+14,8'], [0.2, 'walk', 'vince', 'garage_door+1,1'], [0, 'car', 'jake_car', 'garage_door', 1],
           [6, 'act', 'vince', 'AIM', 'jake_car'], [7, 'smoke', 'garage_door'], [9, 'run', 'vince', 'garage_door+30,-20']],
          ('narrator', 'Ночью к гаражу подходит человек в тёмной куртке. Пара минут с ключом — и тормоза уже не держат.'),
          ('narrator', 'Утром брат найдёт следы. А ты — того, кто их оставил.')),
    dialog(m, ('gleb', 'Кто-то копался в моей машине! Тормозные шланги перерезаны!'),
              ('gleb', 'Соседи видели Винса. Он ещё в городе — сидит у Сторма в машине. Догони его!')),
    step('chase', 'Догони машину Винса и разбей её', points=['race_finish', 'race_5', 'race_4', 'hw_east'], driver='Винс', variant=5),
    dialog(m, ('vince', 'Ладно, ладно! Это не я придумал! Сторм заплатил!'),
              ('vince', 'А Сторму платит Марко. «Синие» хотят, чтобы твой брат не вернул долг «Ящерам». Тогда Брик ослабнет.'),
           choices=[('Передай Марко: я не пешка.', 'a1_msg', [('vince', 'Передам… если доживу.')]),
                    ('Убирайся из города.', 'a1_out', [('vince', 'Уже уехал!')])]),
])

insert_after(ch1, 'a1m2', a1m2b)
insert_after(ch1, 'a1m3', a1m3b, a1m3c, a1m3d)

# ---------- a1m4: облава — полицейские машины влетают на старт, копы выходят с оружием
m = 'a1m4'
find(ch1, m)['steps'].insert(0, scene(m,
    [S('race_start+30,-5,8', 'race_start+18,-4,5', 5, track='cop_car1'), F('cop_car2', 4, (7, 2.5, 3), shake=0.6), S('race_start+8,10,2', 'race_start+5,6,2', 4, track='cop1')],
    [[0, 'car', 'cop_car1', 'race_start+90,4', 6, 90], [0, 'car', 'cop_car2', 'race_start+110,-4', 6, 90],
     [0.1, 'drive', 'cop_car1', ['race_start+10,3'], 0.9], [0.6, 'drive', 'cop_car2', ['race_start+14,-4'], 0.9],
     [8.5, 'spawn', 'cop1', 'race_start+8,4', 'police'], [8.5, 'spawn', 'cop2', 'race_start+12,-2', 'police'],
     [8.8, 'act', 'cop1', 'AIM', 'player'], [9, 'act', 'cop2', 'AIM', 'player']],
    ('narrator', 'Сирены. Сначала одна, потом сразу пять. Кто-то сдал трассу.')))

# ---------- Акт II: Брик держит Джейка во дворе
m = 'a2m1'
find(ch2, m)['steps'].insert(1, scene(m,
    [S('cam_ind_a', 'gang_yard+12,10,12', 6), S('gang_yard+4,10,15', 'gang_yard+2,7,13', 6, track='kirpich'), S('gang_yard+-4,6,11', 'gang_yard+-2,4,10', 4, track='kirpich')],
    [[0, 'spawn', 'gleb', 'gang_yard+0,2'], [0, 'act', 'gleb', 'UPSET'], [0, 'spawn', 'kirpich', 'gang_yard+8,-6'],
     [0, 'spawn', 'g1', 'gang_yard+-3,4', 'gang'], [0, 'spawn', 'g2', 'gang_yard+3,4', 'gang'], [0.5, 'act', 'g1', 'AIM', 'gleb'], [0.5, 'act', 'g2', 'AIM', 'gleb'],
     [3, 'walk', 'kirpich', 'gang_yard+1,0'], [8, 'act', 'kirpich', 'LAUGH', 'gleb'], [12, 'act', 'kirpich', 'ARGUE', 'gleb']],
    ('narrator', 'Промзона. Здесь заканчиваются дороги и начинаются правила «Чёрных Ящеров».'),
    ('kirpich', 'Не бойся, Джейк. Твой братишка уже едет. Посмотрим, сколько ты для него стоишь.'),
    do=[['despawn', 'gleb']]))

m = 'a2m2b'
a2m2b = mission(m, 'Налёт на разборку', [
    dialog(m, ('kirpich', '«Синие» налетели на мою разборку у южных складов. Забрали двоих моих и пару тачек.'),
              ('kirpich', 'Езжай туда и покажи, чья это земля.'), do=[['give', 'novograd:ammo', 60]]),
    step('goto', 'Езжай к разборке «Ящеров» в промзоне', place='chop_shop', radius=12),
    step('clear', 'Выбей «Синих» с разборки', place='chop_shop', count=6),
    scene(m, [S('cam_chop', 'chop_shop+10,10,4', 4, track='marco_car'), F('marco_car', 5, (9, 2.5, 3), shake=0.4)],
          [[0, 'car', 'marco_car', 'chop_shop+6,4', 4, 0], [0, 'spawn', 'marco', 'chop_shop+4,2'], [0.3, 'ride', 'marco', 'marco_car'],
           [0.5, 'drive', 'marco_car', ['chop_shop+6,60', 'hw_south'], 0.9]],
          ('narrator', 'Пока ты разбирался с его людьми, сам Марко «Синий» спокойно уехал. Он запомнил твоё лицо.')),
    dialog(m, ('kirpich', 'Ты выбил их! И даже видел Марко? Значит, скоро он сам тебя найдёт.'), do=[['money', 500]]),
])

m = 'a2m3b'
a2m3b = mission(m, 'Контейнер', [
    dialog(m, ('kirpich', 'В порту стоит мой фургон с контейнером. Таможня его опечатала. Забери и привези во двор.')),
    step('steal', 'Угони фургон у портового крана и пригони во двор «Ящеров»', place='port_crane', to='gang_yard', variant=4),
    step('escape', 'Портовая охрана вызвала полицию — оторвись!', stars=2, why='угон с таможни'),
    dialog(m, ('kirpich', 'Ни царапины! Ещё тысяча с долга брата.'), do=[['money', 400]]),
])

m = 'a2m3c'
a2m3c = mission(m, 'Предложение Марко', [
    dialog(m, ('narrator', 'Сообщение с незнакомого номера: «Пляж, парковка у бара. Один. — М.»')),
    step('goto', 'Езжай на пляжную парковку', place='beach_lot', radius=10, do=[['spawn', 'marco', 'beach_lot+4,0']]),
    scene(m, [S('cam_beach', 'beach_lot+12,6,4', 5, track='marco'), S('beach_lot+6,3,2', 'beach_lot+5,2,2', 6, track='marco')],
          [[0, 'spawn', 'marco', 'beach_lot+8,-6'], [0, 'spawn', 'b1', 'beach_lot+10,-8', 'gang', 'Синий'], [0, 'spawn', 'b2', 'beach_lot+12,-4', 'gang', 'Синий'],
           [0.5, 'walk', 'marco', 'beach_lot+3,0'], [6, 'act', 'marco', 'TALK', 'player']],
          ('marco', 'Ты храбрый. Это мне нравится. Брик тебя использует, ты же понимаешь?')),
    talk(m, 'marco', 'Поговори с Марко',
         ('marco', 'Работай на меня. Я закрою долг твоего брата в тот же день.'),
         ('marco', 'Брик продаст тебя, как только ты станешь не нужен. Я — нет.'),
         choices=[('Я подумаю.', 'a2_marco_maybe', [('marco', 'Думай быстро. В Новограде долго не думают.')]),
                  ('Нет. Я не предаю.', 'a2_marco_no', [('marco', 'Жаль. Тогда мы ещё встретимся — по другую сторону ствола.')])]),
    scene(m, [S('beach_lot+16,8,3', 'beach_lot+10,4,2', 5, track='l_car', shake=0.5), S('beach_lot+-4,6,2', 'beach_lot+-3,4,2', 6, track='b1', shake=0.8), F('marco_car', 4, (8, 2.5, 2))],
          [[0, 'spawn', 'b1', 'beach_lot+6,-4', 'gang', 'Синий'], [0, 'spawn', 'b2', 'beach_lot+8,2', 'gang', 'Синий'],
           [0, 'car', 'l_car', 'beach_lot+0,70', 3, 180], [0.1, 'drive', 'l_car', ['beach_lot+0,12'], 0.95],
           [4.5, 'spawn', 'l1', 'beach_lot+-2,10', 'gang', 'Ящер'], [4.5, 'spawn', 'l2', 'beach_lot+3,11', 'gang', 'Ящер'],
           [5, 'shoot', 'l1', 'b1', 5, 0], [5.4, 'shoot', 'l2', 'b2', 5, 0], [5.3, 'shoot', 'b1', 'l1', 3, 0],
           [7, 'die', 'b1'], [8, 'die', 'b2'], [0, 'car', 'marco_car', 'beach_lot+10,-8', 4, 0],
           [6, 'despawn', 'marco'], [11, 'drive', 'marco_car', ['beach_lot+10,-90'], 0.95]],
          ('narrator', 'Разговор прерывают визг шин и выстрелы. Брик проследил за тобой.'),
          ('kirpich', 'Говорил с Марко? Хорошо, что отказал. Иначе мои ребята стреляли бы по тебе.'),
          do=[['despawn', 'marco']]),
])

m = 'a2m3d'
a2m3d = mission(m, 'Казино «Корона»', [
    dialog(m, ('kirpich', 'Большое дело готовится в казино «Корона». Нужен план: когда выезжает фургон с выручкой.'),
              ('kirpich', 'Иди туда как обычный игрок и найди расписание в служебном коридоре.')),
    step('goto', 'Езжай к казино «Корона» в деловом центре', place='casino_dt', radius=10, do=[['spawn', 'lena', 'casino_dt+4,3']]),
    step('collect', 'Найди расписание инкассаторов (ПКМ по метке)', clues=[{'place': 'casino_dt+3,-4', 'name': 'Расписание фургона'}]),
    talk(m, 'lena', 'Кто-то следит за тобой — поговори с девушкой у входа',
         ('lena', 'Лена Вуд, «Вестник Новограда». Я видела, как ты выходил из служебного коридора.'),
         ('lena', 'Не бойся, я не к копам. Я пишу про Брика. И про того, кто его прикрывает в полиции.'),
         ('lena', 'Шеф Харрис. Слышал это имя? Запомни. Когда понадобится правда — найди меня в «Неоне».')),
    dialog(m, ('kirpich', 'Расписание у тебя? Отлично. Фургон пойдёт по северной трассе. Готовься.')),
])

insert_after(ch2, 'a2m2', a2m2b)
insert_after(ch2, 'a2m3', a2m3b, a2m3c, a2m3d)

# a2m4: фургон выезжает из казино — камера за ним
m = 'a2m4'
find(ch2, m)['steps'].insert(1, scene(m,
    [S('cam_casino', 'casino_dt+14,12,5', 4, track='van'), F('van', 6, (10, 3, -3))],
    [[0, 'car', 'van', 'casino_dt', 4, 0], [0.5, 'drive', 'van', ['casino_dt+0,60', 'dt_3'], 0.7]],
    ('narrator', 'Бронефургон «Короны» выезжает ровно по расписанию. В нём — недельная выручка казино.')))

# ---------- Акт III
m = 'a3m1'
find(ch3, m)['steps'].insert(1, scene(m,
    [S('police_station+8,8,3', 'police_station+5,5,2', 5, track='petrov'), F('petrov', 4, (3.2, 1.7, 1.4))],
    [[0, 'spawn', 'petrov', 'police_station+10,6'], [0.2, 'walk', 'petrov', 'police_station+1,1'],
     [0, 'spawn', 'harris', 'police_station+-4,2'], [0, 'act', 'harris', 'TALK', 'petrov'], [6, 'face', 'petrov', 'player']],
    ('narrator', 'Участок. Камера два на три метра и ночь, чтобы подумать о своих ошибках.'),
    ('harris', 'Питерсон, этот парень — пустое место. Не трать время.'),
    ('petrov', 'Это вы так думаете, шеф.')))

m = 'a3m2b'
a3m2b = mission(m, 'Журналистка', [
    dialog(m, ('tema', 'Брик что-то говорил по телефону про «шефа». Тебе это имя что-нибудь говорит?'),
              ('tema', 'Найди ту журналистку, Лену. Похоже, она копает в ту же сторону.')),
    step('goto', 'Найди Лену в клубе «Неон»', place='club_neon', radius=8, do=[['spawn', 'lena', 'club_neon+2,-2'], ['time', 17000]]),
    talk(m, 'lena', 'Поговори с Леной',
         ('lena', 'Харрис встречается с людьми Брика каждую пятницу. В переулке за клубом. Сегодня пятница.'),
         ('lena', 'Нужны фото. Без них в редакции меня никто слушать не будет.')),
    step('goto', 'Тихо подойди к переулку за клубом', place='dt_alley', radius=14),
    scene(m, [S('dt_alley+14,6,3', 'dt_alley+12,4,2.5', 6, track='harris'), S('dt_alley+4,-5,2', 'dt_alley+3,-4,1.8', 5, track='courier')],
          [[0, 'spawn', 'harris', 'dt_alley+-6,-2'], [0, 'spawn', 'courier', 'dt_alley+-14,0', 'gang', 'Курьер Ящеров'],
           [0.3, 'walk', 'courier', 'dt_alley+-7,-1'], [4, 'act', 'courier', 'HANDSHAKE', 'harris'], [4, 'act', 'harris', 'HANDSHAKE', 'courier'],
           [8, 'act', 'harris', 'LAUGH', 'courier'], [10, 'walk', 'courier', 'dt_alley+-20,0']],
          ('narrator', 'Конверт переходит из рук в руки. Шеф полиции Новограда пересчитывает деньги прямо на месте.'),
          ('harris', 'Передай Брику: облав на этой неделе не будет. Но цена растёт.')),
    step('collect', 'Сфотографируй встречу (ПКМ по метке в переулке)', clues=[{'place': 'dt_alley+2,-3', 'name': 'Фото: Харрис и курьер'}]),
    step('escape', 'Тебя заметили! Уходи от людей Харриса', stars=2, why='слежка за шефом полиции'),
    dialog(m, ('lena', 'Фото у тебя? Это бомба. Но теперь Харрис знает, что за ним следят.'), do=[['despawn', 'harris']]),
])

m = 'a3m3b'
a3m3b = mission(m, 'Похищение', [
    scene(m, [S('cam_club', 'club_neon+8,10,4', 4, track='lena'), S('club_neon+4,4,2', 'club_neon+3,3,2', 5, track='lena', shake=0.4), F('kid_car', 5, (9, 2.5, 2.5))],
          [[0, 'spawn', 'lena', 'club_neon+0,2'], [0, 'act', 'lena', 'TALK'], [0, 'car', 'kid_car', 'club_neon+8,6', 3, 90],
           [1, 'spawn', 'k1', 'club_neon+10,4', 'gang', 'Ящер'], [1, 'spawn', 'k2', 'club_neon+10,8', 'gang', 'Ящер'],
           [1.2, 'run', 'k1', 'club_neon+1,2'], [1.4, 'run', 'k2', 'club_neon+0,3'], [4.5, 'act', 'lena', 'UPSET'],
           [6, 'fight', 'k1', 'lena', 40], [8, 'ride', 'lena', 'kid_car'], [8.3, 'despawn', 'k1'], [8.3, 'despawn', 'k2'],
           [9, 'drive', 'kid_car', ['dt_1', 'dt_2'], 0.95]],
          ('narrator', 'Лену хватают прямо у входа в «Неон». Харрис решил вопрос по-своему.'),
          ('lena', 'Пусти! Помогите!'),
          do=[['despawn', 'lena']]),
    step('chase', 'Догони машину похитителей и разбей её', points=['dt_1', 'dt_2', 'dt_3', 'dt_4'], driver='Похититель', variant=3),
    step('clear', 'Освободи Лену: разберись с похитителями', place='dt_4', count=3),
    dialog(m, ('lena', 'Ты… ты пришёл. Спасибо. Флешка с фото у меня — они её не нашли.'),
              ('lena', 'Отвези её Питерсону. Пусть Харрис ответит за всё.'), do=[['spawn', 'lena', 'dt_4+2,2']]),
])

m = 'a3m3c'
a3m3c = mission(m, 'Шеф Харрис', [
    step('goto', 'Отвези улики лейтенанту Питерсону в участок', place='police_station', radius=8,
         do=[['spawn', 'petrov', 'police_station+2,0'], ['despawn', 'lena']]),
    talk(m, 'petrov', 'Отдай улики Питерсону',
         ('petrov', 'Харрис… Я подозревал, но не думал, что настолько.'),
         ('petrov', 'С этим я пойду к прокурору. Сегодня же.')),
    scene(m, [S('police_station+10,6,3', 'police_station+7,4,2', 5, track='harris'), S('police_station+3,-3,2', 'police_station+2,-2,1.8', 6, track='harris')],
          [[0, 'spawn', 'harris', 'police_station+-6,4'], [0, 'spawn', 'petrov', 'police_station+3,2'],
           [0, 'spawn', 'c1', 'police_station+6,-3', 'police'], [0, 'spawn', 'c2', 'police_station+7,3', 'police'],
           [0.5, 'walk', 'petrov', 'police_station+-4,3'], [0.8, 'walk', 'c1', 'police_station+-5,1'], [1, 'walk', 'c2', 'police_station+-5,6'],
           [4, 'act', 'harris', 'UPSET'], [5, 'act', 'c1', 'AIM', 'harris'], [5, 'act', 'c2', 'AIM', 'harris'], [6, 'act', 'petrov', 'ARGUE', 'harris']],
          ('petrov', 'Шеф Харрис, вы арестованы за взятки и пособничество банде.'),
          ('harris', 'Ты пожалеешь, Питерсон. Брик всё равно уйдёт.'),
          ('petrov', 'Не в этот раз.'),
          do=[['despawn', 'harris']]),
])

insert_after(ch3, 'a3m2', a3m2b)
insert_after(ch3, 'a3m3', a3m3b, a3m3c)

# a3m3: Джейк выбегает к брату — камера за ним
# финал: Брика выводят из ангара и увозят на полицейской машине
fin = find(ch3, 'a3m4')['steps']
end = next(x for x in fin if x.get('id') == 'ending')
end['shots'] = [S('airfield_hangar+14,8,3', 'airfield_hangar+9,5,2', 6, track='kirpich'), F('cop_car', 6, (9, 3, 2.5)), S('cam_air_a', 'cam_island_far', 10), S('cam_dt_a', 'cam_dt_b', 8)]
end['actors'] = [[0, 'spawn', 'kirpich', 'airfield_hangar+2,4'], [0, 'act', 'kirpich', 'UPSET'],
                 [0, 'spawn', 'c1', 'airfield_hangar+4,3', 'police'], [0, 'spawn', 'c2', 'airfield_hangar+0,3', 'police'],
                 [0, 'car', 'cop_car', 'airfield_hangar+6,12', 6, 0], [0.5, 'walk', 'kirpich', 'airfield_hangar+5,11'],
                 [0.6, 'walk', 'c1', 'airfield_hangar+7,10'], [0.6, 'walk', 'c2', 'airfield_hangar+3,10'],
                 [5.5, 'ride', 'kirpich', 'cop_car'], [6, 'drive', 'cop_car', ['airfield_hangar+6,60', 'airfield'], 0.6]]


# =====================================================================================  АКТ IV (0.3)
ch4 = {'id': 'a4', 'title': 'Акт IV', 'subtitle': 'Город казино', 'age': 22, 'missions': []}
m = 'a4m1'
ch4['missions'].append(mission(m, 'Ривьера зовёт', [
    {'type': 'title', 'title': 'Акт IV', 'subtitle': 'Город казино', 'do': [['time', 9000]]},
    scene(m, [S('cam_bridge', 'bridge_n+30,0,12', 7), S('cam_riv_far', 'cam_riv_strip', 9)],
          [],
          ('narrator', 'Прошло полгода. Новоград затих. А за проливом, на острове Ривьера, жизнь только начиналась.'),
          ('narrator', 'Город казино, отелей и людей, которые никогда не спят. И у каждого здесь — своя ставка.')),
    dialog(m, ('nika', 'Слушай, гонщик. На Ривьере большой заезд — «Кубок Стрипа». Приз — сто тысяч.'),
              ('nika', 'Его проводит Виктор Кроу. Хозяин всех казино острова. Едем вместе?'),
           choices=[('Сто тысяч? Едем.', 'a4_money', [('nika', 'Вот это настрой! Встречаемся на мосту.')]),
                    ('Кроу? Звучит как неприятности.', 'a4_wary', [('nika', 'На Ривьере всё — неприятности. Зато какие красивые.')])],
           do=[['car', 'garage_door', 0]]),
    step('goto', 'Езжай на Ривьеру по северному мосту (карта — M)', place='bridge_n', radius=14, vehicle=True),
    scene(m, [F('nika_car', 6, (9, 3, 3)), S('bridge_n+-60,10,4', 'bridge_n+-90,8,3', 5, track='nika_car')],
          [[0, 'car', 'nika_car', 'bridge_n+-6,2', 2, 90], [0.1, 'drive', 'nika_car', ['bridge_n+-200,2', 'riv_ring_e+0,-444'], 0.9]],
          ('nika', 'Догоняй! Ривьера не ждёт!')),
    step('goto', 'Доберись до Стрипа — главной улицы Ривьеры', place='riv_strip', radius=12, vehicle=True),
]))

m = 'a4m2'
ch4['missions'].append(mission(m, 'Софи', [
    scene(m, [S('riv_casino+12,10,4', 'riv_casino+6,6,2.5', 6, track='crow'), F('crow', 4, (3, 1.8, 1.4)), S('riv_casino+3,4,2', 'riv_casino+2,3,1.8', 5, track='sofi')],
          [[0, 'spawn', 'crow', 'riv_casino+8,-6'], [0, 'spawn', 'g1', 'riv_casino+10,-7', 'gang', 'Охрана Кроу'], [0, 'spawn', 'g2', 'riv_casino+6,-8', 'gang', 'Охрана Кроу'],
           [0.3, 'walk', 'crow', 'riv_casino+1,1'], [0.5, 'walk', 'g1', 'riv_casino+3,0'], [0.5, 'walk', 'g2', 'riv_casino+-1,0'],
           [0, 'spawn', 'sofi', 'riv_casino+0,3'], [6, 'act', 'crow', 'TALK', 'sofi'], [8, 'act', 'sofi', 'UPSET'], [10, 'walk', 'crow', 'riv_casino+20,-10']],
          ('narrator', 'Казино «Золотая корона». Сюда приходят с деньгами, а уходят с надеждой.'),
          ('crow', 'Софи, дорогая. Опять проигрыши за твоим столом? Ещё один такой вечер — и ты уволена.'),
          ('sofi', 'Да, мистер Кроу.')),
    talk(m, 'sofi', 'Поговори с крупье Софи у казино',
         ('sofi', 'Ты не местный. Видно по глазам — ещё не проиграл.'),
         ('sofi', 'Хочешь совет? Кубок Стрипа подстроен. Побеждает только Шакал — водитель Кроу.'),
         ('sofi', 'Кто обгоняет Шакала, тот потом пропадает. Двое ребят в прошлом году. Их машины нашли в заливе.'),
         choices=[('Тогда я буду первым, кто вернётся.', 'a4_brave', [('sofi', 'Хотела бы я в это верить.')]),
                  ('Почему ты мне это говоришь?', 'a4_why', [('sofi', 'Потому что Кроу держит и меня. Долгом моего отца.')])],
         do=[['spawn', 'sofi', 'riv_casino+0,3']]),
]))

m = 'a4m3'
ch4['missions'].append(mission(m, 'Кубок Стрипа', [
    scene(m, [S('riv_strip+16,0,4', 'riv_strip+8,0,3', 5, track='shakal'), F('shakal', 4, (3, 1.7, 1.2))],
          [[0, 'spawn', 'shakal', 'riv_strip+10,6'], [0, 'car', 'sh_car', 'riv_strip+4,3', 3, 270], [0.2, 'walk', 'shakal', 'riv_strip+5,5'], [5, 'act', 'shakal', 'LAUGH', 'player']],
          ('shakal', 'Новенький из Новограда? Смешно. Здесь асфальт горячий, а повороты — острые.')),
    step('carrace', 'Кубок Стрипа: проезжай отметки раньше Шакала', start='riv_strip',
         points=['riv_strip+-240,0', 'riv_strip+-240,-300', 'riv_strip+240,-300', 'riv_strip+240,300', 'riv_strip+0,300', 'riv_strip'],
         rival='Шакал', rival_speed=82, rival_engine=2, variant=3, do=[['time', 15500], ['despawn', 'shakal']]),
    dialog(m, ('nika', 'Ты сделал Шакала! На его же улицах!'),
              ('nika', 'Только… Кроу не заплатит. Он сказал, что гонка «не засчитана». И забрал мою машину в счёт ставки.'),
           do=[['money', 3000]]),
]))

m = 'a4m4'
ch4['missions'].append(mission(m, 'Машина Ники', [
    dialog(m, ('nika', 'Мою «Молнию» держат в гараже у отеля «Лазурь». Охрана, камеры… но для тебя это не проблема, да?')),
    step('steal', 'Угони машину Ники из гаража отеля «Лазурь» и пригони к докам у пролива', place='riv_hotel', to='riv_docks', variant=2),
    step('escape', 'Охрана отеля вызвала полицию Ривьеры — оторвись!', stars=2, why='угон у отеля'),
    dialog(m, ('nika', 'Моя девочка! Ни царапины! Я тебе должна, по-настоящему.'), do=[['money', 1500]]),
]))

m = 'a4m5'
ch4['missions'].append(mission(m, 'Рэй', [
    dialog(m, ('sofi', 'Мне нужна твоя помощь. Есть человек — Рэй. Он возит грузы для Кроу на катерах.'),
              ('sofi', 'Рэй хочет уйти от Кроу, но люди Кроу пришли за ним. Он на пристани на западе. Поспеши!')),
    step('goto', 'Езжай на западную пристань Ривьеры', place='riv_marina', radius=14, do=[['spawn', 'ray', 'riv_marina+4,0']]),
    step('clear', 'Отбей Рэя у людей Кроу', place='riv_marina', count=6),
    talk(m, 'ray', 'Поговори с Рэем',
         ('ray', 'Спасибо, друг. Ещё минута — и меня бы отправили кормить рыб.'),
         ('ray', 'Кроу возит через мои катера не только деньги. Оружие. Для обеих банд Новограда.'),
         ('ray', 'Это он стравливал Ящеров и Синих. Пока они воевали, он скупал город.')),
]))

m = 'a4m6'
ch4['missions'].append(mission(m, 'Вилла Кроу', [
    dialog(m, ('lena', 'Привет, герой. Я на Ривьере, пишу про Кроу. Рэй передал мне, что ты в деле.'),
              ('lena', 'На вилле Кроу есть сейф с бухгалтерией. Если достанешь — я напечатаю всё на первой полосе.')),
    step('goto', 'Тихо подберись к вилле Кроу на севере острова', place='riv_villa', radius=16),
    step('collect', 'Найди сейф с бухгалтерией (ПКМ по метке)', clues=[{'place': 'riv_villa+4,-3', 'name': 'Сейф Кроу'}]),
    step('clear', 'Охрана заметила! Прорывайся', place='riv_villa', count=5),
    step('escape', 'Уходи от полиции, которую вызвала охрана', stars=3, why='налёт на виллу'),
    dialog(m, ('lena', 'Это… это всё. Взятки, имена, суммы. Шеф Харрис был мелочью. Кроу платит половине полиции острова.')),
]))

m = 'a4m7'
ch4['missions'].append(mission(m, 'Разведка', [
    dialog(m, ('sofi', 'Бумаги — это хорошо. Но Кроу откупится от любого суда. Его сила — в деньгах.'),
              ('sofi', 'Каждую пятницу в хранилище «Короны» лежит вся выручка острова. Если её не станет — Кроу конец.'),
              ('sofi', 'Я знаю, где схема хранилища. И у кого расписание охраны.')),
    step('collect', 'Найди схему хранилища и расписание охраны у казино', clues=[{'place': 'riv_casino+6,-4', 'name': 'Схема хранилища'}, {'place': 'riv_casino+-6,-2', 'name': 'Расписание охраны'}]),
    dialog(m, ('ray', 'Уходить будем морем. Мой катер будет ждать у доков на проливе. Главное — довези деньги до воды.')),
]))

m = 'a4m8'
ch4['missions'].append(mission(m, 'Подготовка', [
    dialog(m, ('nika', 'Я в деле. Моя машина — ваша. Но её надо подготовить: нитро и броня.'),
              ('ray', 'А тебе — бронежилет. Купи в оружейном «Калибр» в Новограде.')),
    step('buy', 'Купи бронежилет в оружейном «Калибр» (Новоград)', item='novograd:body_armor', count=1),
    step('tune', 'Поставь нитро в Нитро-Гараже: заезжай в гараж и нажми ПКМ по механику'),
    dialog(m, ('sofi', 'Пятница. Сегодня. Я открою служебную дверь в полночь.'), do=[['time', 18000]]),
]))

m = 'a4m9'
ch4['missions'].append(mission(m, 'Ограбление «Золотой короны»', [
    step('goto', 'Езжай к казино «Золотая корона» — Софи ждёт у служебного входа', place='riv_casino', radius=10),
    scene(m, [S('riv_casino+10,8,3', 'riv_casino+5,5,2', 5, track='sofi'), F('sofi', 4, (3, 1.7, -1.3))],
          [[0, 'spawn', 'sofi', 'riv_casino+3,2'], [0.5, 'walk', 'sofi', 'riv_casino+0,-2'], [3, 'act', 'sofi', 'WAVE', 'player'], [6, 'smoke', 'riv_casino+0,-4']],
          ('sofi', 'Дверь открыта. У тебя три минуты, пока охрана меняется. Удачи.')),
    step('clear', 'Хранилище! Разберись с охраной Кроу', place='riv_casino', count=8),
    step('collect', 'Забери деньги из хранилища', clues=[{'place': 'riv_casino+2,-6', 'name': 'Мешки с деньгами'}]),
    step('escape', 'Вся полиция Ривьеры на хвосте! Уходи', stars=4, why='ограбление казино'),
    step('goto', 'Доставь деньги к докам у пролива — там ждёт катер Рэя', place='riv_docks', radius=10),
    dialog(m, ('ray', 'Есть! Всё на борту! Кроу сегодня проснётся нищим!'), do=[['money', 20000], ['shock', 'KRON', -0.4, 'хранилище «Золотой короны» ограблено']]),
]))

m = 'a4m10'
ch4['missions'].append(mission(m, 'Заложница', [
    scene(m, [S('riv_docks+14,8,3', 'riv_docks+8,5,2', 5, track='crow'), S('riv_docks+3,3,2', 'riv_docks+2,2,1.8', 5, track='sofi', shake=0.4), F('crow_car', 5, (9, 2.5, 2.5))],
          [[0, 'spawn', 'crow', 'riv_docks+10,-6'], [0, 'spawn', 'sofi', 'riv_docks+9,-5'], [0, 'act', 'sofi', 'UPSET'],
           [0, 'spawn', 'g1', 'riv_docks+12,-3', 'gang', 'Охрана Кроу'], [0.3, 'act', 'g1', 'AIM', 'player'],
           [0, 'car', 'crow_car', 'riv_docks+14,-8', 3, 0], [2, 'act', 'crow', 'ARGUE', 'player'],
           [8, 'ride', 'sofi', 'crow_car'], [8.3, 'ride', 'crow', 'crow_car'], [9, 'drive', 'crow_car', ['riv_ring_e+0,-200', 'bridge_n'], 0.95]],
          ('crow', 'Красиво сработано. Правда. Но ты кое-что забыл: у меня осталась Софи.'),
          ('crow', 'Верни деньги — получишь её живой. Не вернёшь… ну, ты понял.'),
          ('sofi', 'Не слушай его! Беги!')),
    step('chase', 'Догони машину Кроу и разбей её — спаси Софи', points=['riv_docks', 'riv_ring_e', 'bridge_n', 'race_start'], driver='Виктор Кроу', variant=3),
    dialog(m, ('sofi', 'Ты… ты пришёл за мной. Он сбежал, но я цела.'),
              ('petrov', 'Это Питерсон. Слышал, у вас там жарко. Кроу видели на горной дороге — он рвётся на север, в Седые горы.')),
]))
chapters.append(ch4)

# =====================================================================================  АКТ V (0.3)
ch5 = {'id': 'a5', 'title': 'Акт V', 'subtitle': 'Седые горы', 'age': 22, 'missions': []}
m = 'a5m1'
ch5['missions'].append(mission(m, 'Беглец', [
    {'type': 'title', 'title': 'Акт V', 'subtitle': 'Седые горы', 'do': [['time', 7000]]},
    scene(m, [S('cam_mtn', 'mtn_road+20,0,30', 9)], [],
          ('narrator', 'Седые горы. Сюда не доходят ни деньги Ривьеры, ни законы Новограда.'),
          ('narrator', 'Здесь Кроу надеялся переждать. Он ошибся.')),
    dialog(m, ('petrov', 'Мои люди перекрыли перевал. Кроу где-то на горной дороге. Ищи его машину.')),
    step('goto', 'Езжай на горную дорогу на севере острова', place='mtn_road', radius=16, vehicle=True),
]))

m = 'a5m2'
ch5['missions'].append(mission(m, 'Серпантин', [
    scene(m, [F('crow_car', 6, (10, 3, 3), shake=0.3)],
          [[0, 'car', 'crow_car', 'mtn_road+0,-40', 3, 180], [0.1, 'drive', 'crow_car', ['outer_nw+600,0', 'outer_nw'], 0.95]],
          ('narrator', 'Чёрный седан Кроу выскакивает из-за поворота и уходит на запад.')),
    step('chase', 'Погоня по горной дороге! Разбей машину Кроу', points=['mtn_road', 'outer_nw+600,0', 'outer_nw', 'riv_ring_e+0,-1000'], driver='Виктор Кроу', variant=3),
    dialog(m, ('petrov', 'Машина разбита, но Кроу ушёл пешком в лес. Его люди забрали его на вертолёте. Чёрт!'),
              ('lena', 'У меня есть зацепка. Кроу владеет землёй в пустоши на юге. Там старые шахты.')),
]))

m = 'a5m3'
ch5['missions'].append(mission(m, 'Пустошь', [
    scene(m, [S('cam_desert', 'desert_mesa+20,10,10', 8)], [],
          ('narrator', 'Пустошь. Красная земля, кактусы и столовые горы. Идеальное место, чтобы спрятать то, что не должно найтись.')),
    step('goto', 'Езжай в пустошь на юге, к столовой горе', place='desert_mesa', radius=18, vehicle=True),
    step('clear', 'Наёмники Кроу охраняют склад оружия — зачисти', place='desert_mesa', count=7),
    dialog(m, ('ray', 'Это склад, через который Кроу снабжал банды. Тонны оружия. Взорвём?'),
           choices=[('Взрывай. Пусть знает.', 'a5_boom', [('ray', 'С удовольствием!')]),
                    ('Пусть полиция заберёт как улику.', 'a5_evidence', [('petrov', 'Правильно. Это потопит его в суде.')])],
           do=[['wanted_clear']]),
]))

m = 'a5m4'
ch5['missions'].append(mission(m, 'Сосновка', [
    dialog(m, ('lena', 'Беда. Люди Кроу едут в деревню Сосновка на востоке. Там прячется свидетель — бывший бухгалтер Кроу.')),
    step('goto', 'Спеши в деревню Сосновка на востоке острова', place='village', radius=18, vehicle=True),
    step('clear', 'Защити деревню от людей Кроу', place='village', count=6),
    talk(m, 'lena', 'Поговори с Леной',
         ('lena', 'Свидетель жив. Он даст показания. С этим Кроу не отвертится.'),
         ('lena', 'Но Кроу знает, что проиграл. Такие люди в конце опаснее всего.'),
         do=[['spawn', 'lena', 'village+4,2']]),
]))

m = 'a5m5'
ch5['missions'].append(mission(m, 'Тайник у озера', [
    dialog(m, ('ray', 'Свидетель сказал, что у Кроу есть тайник на озере Ясное: паспорта, деньги, план побега.')),
    step('goto', 'Езжай к пристани на озере Ясное', place='lake_dock', radius=14),
    step('collect', 'Найди тайник Кроу у пристани', clues=[{'place': 'lake_dock+3,-2', 'name': 'Тайник Кроу'}]),
    dialog(m, ('ray', 'Билет на самолёт… сегодня ночью, с аэродрома Новограда. Он хочет улететь!'),
              ('petrov', 'Аэродром мой человек не удержит. Нужна скорость. Нужны все мы.')),
]))

m = 'a5m6'
ch5['missions'].append(mission(m, 'Большое кольцо', [
    dialog(m, ('nika', 'Шакал прикрывает отход Кроу. Он ждёт на большом кольце. Последняя гонка, гонщик. Сделай его!')),
    step('carrace', 'Большое кольцо острова: обгони Шакала', start='outer_east',
         points=['outer_ne', 'mtn_road+0,-200', 'outer_nw', 'outer_nw+0,1000'], rival='Шакал', rival_speed=88, rival_engine=2, variant=0),
    dialog(m, ('shakal', 'Ладно… ладно! Ты лучший. Кроу на аэродроме. Ангар номер два. Я больше в этом не участвую.'),
              ('nika', 'Вот теперь ты — король трассы.'), do=[['money', 5000]]),
]))

m = 'a5m7'
ch5['missions'].append(mission(m, 'Последний рейс', [
    step('goto', 'Езжай на аэродром — Кроу в ангаре', place='airfield_hangar', radius=16, do=[['time', 19000]]),
    scene(m, [S('airfield_hangar+20,10,4', 'airfield_hangar+12,6,3', 5, track='crow'), F('crow', 4, (3, 1.8, 1.5))],
          [[0, 'spawn', 'crow', 'airfield_hangar+2,4'], [0, 'spawn', 'm1', 'airfield_hangar+5,3', 'gang', 'Наёмник'], [0, 'spawn', 'm2', 'airfield_hangar+-2,3', 'gang', 'Наёмник'],
           [0.5, 'act', 'm1', 'AIM', 'player'], [0.5, 'act', 'm2', 'AIM', 'player'], [2, 'act', 'crow', 'ARGUE', 'player']],
          ('crow', 'Ты отнял у меня казино, деньги, людей. Но самолёт ты не отнимешь.'),
          ('crow', 'Убейте его. Медленно.')),
    step('clear', 'Финальная битва в ангаре: возьми Кроу', place='airfield_hangar', count=8, boss='Виктор Кроу'),
    scene(m, [S('airfield_hangar+14,8,3', 'airfield_hangar+9,5,2', 6, track='crow'), F('cop_car', 6, (9, 3, 2.5)), S('cam_air_a', 'cam_island_far', 10), S('cam_riv_far', 'cam_riv_strip', 9)],
          [[0, 'spawn', 'crow', 'airfield_hangar+2,4'], [0, 'act', 'crow', 'UPSET'], [0, 'spawn', 'petrov', 'airfield_hangar+5,6'],
           [0, 'spawn', 'c1', 'airfield_hangar+4,3', 'police'], [0, 'car', 'cop_car', 'airfield_hangar+6,12', 6, 0],
           [0.5, 'walk', 'crow', 'airfield_hangar+5,11'], [0.6, 'walk', 'c1', 'airfield_hangar+7,10'],
           [5.5, 'ride', 'crow', 'cop_car'], [6, 'drive', 'cop_car', ['airfield_hangar+6,60', 'airfield'], 0.6]],
          ('narrator', 'Виктора Кроу взяли у трапа его же самолёта. На этот раз — без адвокатов и без взяток.'),
          ('narrator', 'Лена напечатала всё. Софи открыла свой бар на Стрипе. Рэй возит туристов на катере.'),
          ('narrator', 'А Новоград и Ривьера… они всё ещё не спят. И, кажется, всё ещё ждут тебя.'),
          do=[['wanted_clear'], ['shock', 'KRON', 0.25, 'Кроу арестован, у казино новый честный владелец'], ['shock', 'RIVH', 0.12, 'туристы возвращаются на Ривьеру']]),
    dialog(m, ('petrov', 'Ну что, напарник. Два города спасены. Что дальше?'),
              ('nika', 'Дальше — гонки! Честные. На Ривьере открывается новая трасса.'),
              ('sofi', 'А в моём баре для тебя всегда свободный столик.'),
           choices=[('Остаюсь в Новограде.', 'end2_novograd', [('gleb', 'Гараж ждёт, брат!')]),
                    ('Переезжаю на Ривьеру.', 'end2_riviera', [('sofi', 'Тогда добро пожаловать домой.')])]),
    {'type': 'credits'},
]))
chapters.append(ch5)

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

# ---------- 0.3: Ривьера и загород (после основных — чтобы старые миссии не сдвинулись)
DISTRICTS['ривьера'] = (-1550, -1090, -410, 410)
DISTRICTS['ривьера-север'] = (-1780, -950, -890, -440)
riv = ['ривьера', 'ривьера-север']
for i in range(8):
    dd = riv[i % 2]
    route = walk(dd, 7 + i % 3)
    add(f'rivrace{i+1:02d}', f'Ночная гонка Ривьеры #{i+1}', 'Гонки Ривьеры', 'shakal', 800 + i * 150, 3, [
        say(('shakal', f'Хочешь реванша, новичок? Улицы Ривьеры, {7 + i % 3} отметок. Проигравший платит.')),
        step('carrace', 'Гонка по Ривьере: проезжай отметки первым', start=route[0], points=route[1:], rival='Шакал',
             rival_speed=70 + i * 2, rival_engine=2, variant=i % 6)])
for i in range(8):
    dd = riv[i % 2]
    place = spot(dd)
    add(f'rivjob{i+1:02d}', f'Заказ Рэя #{i+1}', 'Контрабанда Рэя', 'ray', 700 + i * 100, 3, [
        say(('ray', 'Люди Кроу держат мой груз. Забери его — плачу щедро.')),
        step('goto', 'Езжай к складу людей Кроу', place=place, radius=14),
        step('clear', 'Разберись с охраной', place=place, count=4 + i % 3),
        step('goto', 'Отвези груз на пристань Ривьеры', place='riv_marina', radius=12)])
far = [('Седые горы', 'mtn_road', ['mtn_road', 'outer_nw+600,0', 'outer_nw']), ('Пустошь', 'desert_road', ['desert_road', 'desert_road+600,300', 'outer_se']),
       ('Большое кольцо', 'outer_east', ['outer_east', 'outer_ne', 'mtn_road+0,-200'])]
for i in range(6):
    name, start, pts = far[i % 3]
    add(f'farrace{i+1:02d}', f'Заезд: {name} #{i + 1}', 'Загородные гонки', 'nika', 1000 + i * 200, 3, [
        say(('nika', f'Загородный заезд: {name}. Длинные прямые, крутые повороты — как ты любишь.')),
        step('carrace', f'Заезд «{name}»: приди первым', start=start, points=pts[1:] + [pts[0]], rival='Ракета', rival_speed=78 + i * 2, rival_engine=2, variant=i % 6)])

print('побочных миссий:', len(side))

# ведущие радио: заставка станции при переключении
for vid, sp, text in [
    ('radio_neon88', 'Zacharie Aimilios', 'Неон восемьдесят восемь эф эм. Синтвейв для тех, кто живёт ночью.'),
    ('radio_beat_ulic', 'Abrahan Mack', 'Бит Улиц! Самый жёсткий хип-хоп Новограда. Громче!'),
    ('radio_nochnoy_port', 'Alma María', 'Ночной Порт. Расслабься, дорога длинная.'),
    ('radio_trassa128', 'Ludvig Milivoj', 'Трасса сто двадцать восемь! Жми на газ — и не оглядывайся!'),
    ('radio_rock_volna', 'Craig Gutsy', 'Рок-Волна! Громкие гитары для громких улиц. Не убавляй!'),
    ('radio_jazz_polnoch', 'Royston Min', 'Джаз-клуб «Полночь». Налейте себе чего-нибудь и не спешите.'),
    ('radio_palma_fm', 'Viktor Eka', 'Пальма эф эм, Ривьера! Солнце, море и регги до утра.'),
    ('radio_klassika', 'Gracie Wise', 'Радио «Классика». Музыка, которая старше этого города.'),
    ('radio_country_road', 'Dionisio Schuyler', 'Кантри-Роуд! Для тех, кто едет за город и не собирается возвращаться.'),
    ('radio_tekhno_podval', 'Kazuhiko Atallah', 'Техно-Подвал. Бочка, бас и ничего лишнего.'),
]:
    voice_lines.append({'id': vid, 'who': 'narrator', 'text': text, 'speaker': sp})

# ---------- точка старта каждой миссии (свободная игра: миссия ждёт игрока на жёлтой метке, как в GTA SA)
ACT_HOME = {'a1': 'garage_door', 'a2': 'gang_yard', 'a3': 'police_station', 'a4': 'riv_strip', 'a5': 'mtn_road'}
npc_at = {}
for ch in chapters:
    for mi in ch['missions']:
        start = None
        for st in mi['steps']:
            for a_ in st.get('do', []):
                if a_[0] in ('spawn', 'tp_npc'): npc_at[a_[1]] = a_[2]
            for cue in st.get('actors', []):
                if cue[1] == 'spawn' and cue[2] in CHARS: npc_at[cue[2]] = cue[3]
            if start: continue
            t_ = st.get('type')
            if t_ == 'talk' and st.get('npc') in npc_at: start = npc_at[st['npc']]
            elif t_ in ('goto', 'steal', 'clear') and st.get('place'): start = st['place']
            elif t_ == 'carrace': start = st.get('start')
            elif t_ == 'chase': start = st['points'][0]
            elif t_ == 'collect': start = st['clues'][0]['place']
        start = {'a1m3d': 'garage_door', 'a1m4': 'race_start', 'a3m3b': 'club_neon'}.get(mi['id'], start)
        mi.setdefault('start', start or ACT_HOME.get(ch['id'], 'garage_door'))

# ---------- операторская работа: цветокор по настроению и титр на первой кат-сцене миссии
DARK = ('Крыса', 'Предател', 'Похорон', 'Последн', 'Ловушк', 'Засада')


def grade_for(mi, st, last):
    names = ' '.join(str(sh.get(k, '')) for sh in st.get('shots', []) for k in ('from', 'to', 'follow', 'track'))
    cues = ' '.join(str(a[1]) if len(a) > 1 else '' for a in st.get('actors', []))
    if any(w in mi.get('title', '') for w in DARK) or 'die' in cues: return 'film_noir'
    if any(w in names for w in ('club', 'casino', 'neon', 'strip')): return 'film_neon'
    if last or any(w in names for w in ('desert', 'mtn', 'beach', 'riv_', 'sunset')): return 'film_sunset'
    if any(sh.get('shake') for sh in st.get('shots', [])) or any(sh.get('follow') for sh in st.get('shots', [])): return 'film_blockbuster'
    return 'film_cine'


for ci, ch in enumerate(chapters):
    for mj, mi in enumerate(ch['missions']):
        first = True
        last = ci == len(chapters) - 1 and mj == len(ch['missions']) - 1
        for st in mi['steps']:
            if st.get('type') != 'cutscene': continue
            st.setdefault('grade', grade_for(mi, st, last))
            if first: st['card'] = True
            first = False
for sm in side.values():
    for st in sm['steps']:
        if st.get('type') == 'cutscene': st.setdefault('grade', grade_for(sm, st, False))

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
