#!/usr/bin/env python3
"""WHAT THE LAKE KEEPS — глава 4 «Чёрная машина» в данные игры (godot/lake/data/ch4.json) по сценарию newgame/ch4.md.
Тексты RU+EN, выборы на время, действия (qte), флаги, карта главы. Постановка — godot/lake/scripts/chapter4.gd (fx и планы).
  python3 tools/lake/story/ch4.py"""
import json, os

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '../../../godot/lake/data/ch4.json')


def N(ru, en, dur=2.6, **kw):          # ремарка внизу кадра
    return dict(note={'ru': ru, 'en': en}, dur=dur, **kw)


def S(who, ru, en, **kw):              # реплика
    return dict(say=who, ru=ru, en=en, **kw)


def T(ru, en, dur=3.0, **kw):          # текст на чёрном
    return dict(text={'ru': ru, 'en': en}, dur=dur, note=True, **kw)


def O(ru, en, go, **kw):
    return dict(ru=ru, en=en, go=go, **kw)


def C(t, opts, shot=None, silent=None):
    c = {'t': t, 'opts': opts + ([{'silent': True, 'go': silent}] if silent else [])}
    if shot:
        c['shot'] = shot
    return {'choice': c}


def I(**cond):                         # условие beat-а: I(zoe='>0') → {'if': {...}}
    return {'if': cond}


ZOE = {'zoe': '>0', 'zoe_gone': '==0'}                     # Зоя едет с ними
DANNY = {'danny': '>0', 'danny_lost': '==0'}               # Дэнни с ними
DANNY_FEAR = {'danny': '>0', 'danny_lost': '==0', 'danny_fear': '>0'}
ROCK = {'ru': 'Укачай: жми E / пробел медленно, в ритм — не торопись', 'en': 'Rock her: press E / Space slowly, in rhythm — don’t rush'}

SC = {}

# ---------- «Ранее» ----------
SC['c4_recap'] = {'save': True, 'time': '07:40', 'beats': [
    {'fx': 'black'},
    T('Ранее…', 'Previously…', 1.6),
    T('Ева родила девочку. Её назвали Анной.', 'Eva gave birth to a girl. They named her Anna.', 2.6, **I(eva_alive='>0')),
    T('Ева умерла родами. Девочку назвали Анной.', 'Eva died giving birth. They named the girl Anna.', 2.8, **I(eva_dead='>0')),
    T('Зою забрал доктор Хейл.', 'Dr. Hale took Zoe.', 2.4, **I(zoe_taken='>0')),
    T('Ночью Ной стоял босиком в снегу с топором и говорил чужим голосом.', 'In the night Noah stood barefoot in the snow with an axe, speaking in someone else’s voice.', 3.2, **I(night_lapse='>0')),
    T('Ночью по рации пел женский голос. Та самая колыбельная.', 'In the night a woman sang over the radio. That same lullaby.', 3.0, **I(heard_song='>0')),
    T('Дэнни ушёл в буран.', 'Danny walked out into the blizzard.', 2.4, **I(danny_lost='>0')),
    T('Бабушка Агнес: «Сама её к нему и послала».', 'Grandma Agnes: “I sent her to him myself.”', 2.8),
    T('На рассвете в конце улицы зажглись фары чёрного седана.', 'At dawn, the black sedan’s headlights came on at the end of the street.', 3.0),
    {'go': 'c4_s1'}]}

# ---------- Сцена 1. «Буханка» ----------
SC['c4_s1'] = {'save': True, 'time': '08:30', 'beats': [
    {'fx': 'c4_start', 'shot': 'yard'},
    {'fx': 'fade_in', 'shot': 'yard', **N('Глава 4. «Чёрная машина». Утро. Буран кончился, снег искрится.', 'Chapter 4. “The Black Car.” Morning. The storm is over; the snow glitters.', 3.4)},
    N('Они возвращаются к дому Агнес за машиной.', 'They come back to Agnes’s house for the car.', 2.6, shot='yard', **I(hale_lost='>0')),
    {'fx': 'car_door', 'shot': 'car_seat', **N('На водительском сиденье — пузырёк таблеток. Новый. Под ним — записка.', 'On the driver’s seat — a bottle of pills. A new one. A note underneath.', 3.0)},
    {'fx': 'hale_note', 'shot': 'hale_note', **N('Ровный почерк: «Ной, тебе станет легче. Ты же знаешь, что бывает без них. — С. Х.»', 'Neat handwriting: “Noah, you’ll feel better. You know what happens without them. — S. H.”', 4.2)},
    S('iris', 'Он был здесь. Ночью. Прямо тут.', 'He was here. Last night. Right here.', shot='iris_yard'),
    N('В кармане у Ноя — ещё и старый пузырёк.', 'Noah still has the old bottle in his pocket, too.', 2.4, shot='noah_yard', **I(pills_burned='==0', pills_flushed='==0')),
    C(6, [O('Сунуть пузырёк в карман', 'Pocket the bottle', 'c4_s1_pocket'),
          O('Забросить в сугроб', 'Throw it into a snowdrift', 'c4_s1_snow'),
          O('Отдать Айрис: «Держи у себя. Решишь ты»', 'Give it to Iris: “You keep it. You decide”', 'c4_s1_iris')],
      shot='noah_yard', silent='c4_s1_leave')]}

SC['c4_s1_pocket'] = {'beats': [
    {'set': {'pills_pocket': 1}},
    {'fx': 'pills_pocket', 'shot': 'noah_yard', **N('Ной суёт пузырёк в карман.', 'Noah slips the bottle into his pocket.', 2.2)},
    N('Айрис смотрит на карман. Молчит.', 'Iris looks at the pocket. Says nothing.', 2.4, shot='iris_yard'),
    {'go': 'c4_s1_van'}]}

SC['c4_s1_snow'] = {'beats': [
    {'set': {'pills_snow': 1, 'trust': 1}, 'toast': True},
    {'fx': 'pills_throw', 'shot': 'yard_drift', **N('Пузырёк летит в сугроб и тонет в нём без звука.', 'The bottle sails into a drift and sinks without a sound.', 2.6)},
    N('Айрис выдыхает.', 'Iris breathes out.', 1.8, shot='iris_yard'),
    {'go': 'c4_s1_van'}]}

SC['c4_s1_iris'] = {'beats': [
    {'set': {'pills_iris': 1, 'trust': 2}, 'toast': True},
    S('noah', 'Держи у себя. Решишь ты.', 'You keep it. You decide.', shot='noah_yard'),
    {'fx': 'pills_give', 'shot': 'iris_yard', **N('Айрис сжимает пузырёк в кулаке.', 'Iris closes her fist around the bottle.', 2.4)},
    {'go': 'c4_s1_van'}]}

SC['c4_s1_leave'] = {'beats': [
    {'set': {'pills_left': 1}},
    N('Ной не трогает пузырёк. Тот так и остаётся на сиденье.', 'Noah doesn’t touch the bottle. It stays where it is, on the seat.', 2.8, shot='car_seat'),
    {'go': 'c4_s1_van'}]}

SC['c4_s1_van'] = {'beats': [
    {'fx': 'shed_open', 'shot': 'shed', **N('Всем в машину уже не влезть. В сарае у Агнес — старая буханка покойного деда.', 'They won’t all fit in the car anymore. In Agnes’s shed — her late husband’s old van.', 3.4)},
    S('agnes', 'Лет восемь не заводилась. Ты ж у нас механик?', 'Hasn’t started in eight years. You’re the mechanic, aren’t you?', shot='agnes_shed'),
    {'fx': 'van_hood', 'shot': 'van_engine', **N('Свечи — из дедова ящика. Аккумулятор — из их машины. Остаётся «с толкача».', 'Spark plugs from the old man’s toolbox. The battery from their car. All that’s left is a push start.', 3.4)},
    {'fx': 'van_push', 'shot': 'van_push'},
    {'qte': 'mash', 't': 6.0, 'n': 12, 'hint': {'ru': 'С толкача! Быстро жми E / пробел — сцепление, газ', 'en': 'Push start! Mash E / Space — clutch, gas'}, 'fail': 'c4_s1_van2'},
    {'go': 'c4_s1_vango'}]}

SC['c4_s1_van2'] = {'beats': [
    {'set': {'van_late': 1}},
    N('Мотор чихает и глохнет. Толкают ещё — всем миром. И ещё.', 'The engine sputters and dies. They push again — everyone together. And again.', 3.0, shot='van_push'),
    N('Время уходит.', 'Time is slipping away.', 1.8, shot='yard'),
    {'go': 'c4_s1_vango'}]}

SC['c4_s1_vango'] = {'beats': [
    {'fx': 'van_start', 'shot': 'van_smoke', **N('Буханка кашляет чёрным дымом — и заводится.', 'The van coughs out black smoke — and starts.', 2.8)},
    N('Зоя хлопает в ладоши.', 'Zoe claps her hands.', 2.0, fx='zoe_clap', shot='zoe_yard', **{'if': ZOE}),
    N('И главное: чёрная машина буханку не знает.', 'And best of all: the black car doesn’t know the van.', 2.8, shot='van_smoke'),
    {'go': 'c4_s2'}]}

# ---------- Сцена 2. «Санаторий» ----------
SC['c4_s2'] = {'time': '10:10', 'beats': [
    {'fx': 'van_drive', 'shot': 'van_road', **N('Лесная дорога. Буханку трясёт на каждой кочке.', 'A forest road. The van rattles over every bump.', 2.8)},
    {'if': ZOE, 'else': 'c4_s2_b'},
    {'go': 'c4_s2_zoe'}]}

SC['c4_s2_b'] = {'beats': [
    {'if': {'zoe_taken': '>0'}, 'else': 'c4_s3'},
    {'go': 'c4_s2_radio'}]}

SC['c4_s2_zoe'] = {'beats': [
    N('Зоя долго смотрит в окно. Потом начинает сама, ни на кого не глядя.', 'Zoe stares out the window for a long time. Then she starts talking, not looking at anyone.', 3.2, shot='zoe_van'),
    S('zoe', 'Ладно. Раз уж мы все тут… в одной буханке.', 'Okay. Since we’re all here… in one old van.', shot='zoe_van'),
    S('zoe', 'Санаторий «Тихие сосны». В лесу. Белый такой, красивый. Нас там было человек десять. Мне — девятнадцать.', 'Quiet Pines. A sanatorium in the woods. White, pretty. There were about ten of us. I was nineteen.', shot='zoe_van'),
    S('zoe', 'Знаешь, кого туда кладут? Тех, кто что-то видел.', 'You know who they put in there? People who saw something.', shot='zoe_van'),
    S('noah', 'Что видела ты?', 'What did you see?', shot='noah_van'),
    {'fx': 'zoe_laugh', **S('zoe', 'Не помню. В том-то и фишка. Мама заплатила — и я не помню.', 'I don’t remember. That’s the whole point. Mom paid, and I don’t remember.', shot='zoe_van')},
    S('zoe', 'Помню только, что отчим после этого стал очень добрый.', 'All I remember is my stepdad got really nice after that.', shot='zoe_van'),
    N('Тишина. Только мотор.', 'Silence. Just the engine.', 2.0, shot='van_inside'),
    S('iris', 'Она врёт. Она с ним заодно. Он её подослал.', 'She’s lying. She’s with him. He sent her.', shot='iris_van'),
    C(6, [O('«Я ей верю»', '“I believe her”', 'c4_s2_believe'),
          O('«Айрис… может, и так»', '“Iris… maybe you’re right”', 'c4_s2_doubt'),
          O('«Хватит. Обе»', '“Enough. Both of you”', 'c4_s2_enough')],
      shot='noah_van', silent='c4_s2_quiet')]}

SC['c4_s2_believe'] = {'beats': [
    {'set': {'zoe_believed': 1, 'zoe_trust': 1, 'jealous': 1}},
    S('noah', 'Я ей верю.', 'I believe her.', shot='noah_van'),
    {'fx': 'zoe_window', 'shot': 'zoe_van', **N('Зоя отворачивается к окну. Но — благодарно.', 'Zoe turns to the window. But she’s grateful.', 2.6)},
    N('Айрис утыкается в блокнот.', 'Iris buries her face in her notebook.', 2.2, shot='iris_van'),
    {'go': 'c4_s3'}]}

SC['c4_s2_doubt'] = {'beats': [
    {'set': {'zoe_doubted': 1, 'zoe_trust': -1}},
    S('noah', 'Айрис… может, и так.', 'Iris… maybe you’re right.', shot='noah_van'),
    S('zoe', 'Ну спасибо.', 'Gee, thanks.', shot='zoe_van'),
    {'fx': 'zoe_window', 'shot': 'zoe_van', **N('Зоя молчит до конца дороги.', 'Zoe doesn’t say another word for the rest of the drive.', 2.6)},
    {'go': 'c4_s3'}]}

SC['c4_s2_enough'] = {'beats': [
    S('noah', 'Хватит. Обе.', 'Enough. Both of you.', shot='noah_van'),
    N('Обе замолкают.', 'They both go quiet.', 2.0, shot='van_inside'),
    {'go': 'c4_s3'}]}

SC['c4_s2_quiet'] = {'beats': [
    {'fx': 'zoe_window', 'shot': 'zoe_van', **N('Ной молчит. Зоя хмыкает и отворачивается к окну.', 'Noah says nothing. Zoe snorts and turns to the window.', 2.6)},
    {'go': 'c4_s3'}]}

SC['c4_s2_radio'] = {'beats': [
    {'set': {'hale_radio': 1}},
    {'if': DANNY, 'else': 'c4_s2_phone'},
    {'fx': 'radio_hale', 'shot': 'danny_van', **N('У Дэнни в кармане трещит рация. Голос — мягкий, вежливый.', 'The walkie-talkie in Danny’s pocket crackles. A voice — soft, polite.', 3.0)},
    {'go': 'c4_s2_hale'}]}

SC['c4_s2_phone'] = {'beats': [
    {'fx': 'phone_hale', 'shot': 'agnes_van', **N('У Агнес звонит старый телефон. Номер не определён. Она включает громкую связь.', 'Agnes’s old phone rings. Unknown number. She puts it on speaker.', 3.2)},
    {'go': 'c4_s2_hale'}]}

SC['c4_s2_hale'] = {'beats': [
    S('hale', 'Ной. Это доктор Хейл. Зоя у меня. С ней всё хорошо — пока она принимает лекарства.', 'Noah. This is Dr. Hale. Zoe is with me. She’s fine — as long as she takes her medication.', shot='noah_van'),
    S('hale', 'Давай поговорим как взрослые люди. Без топоров.', 'Let’s talk like adults. No axes.', shot='noah_van', **I(hale_brave='>0')),
    S('hale', 'Давай поговорим как взрослые люди.', 'Let’s talk like adults.', shot='noah_van', **I(hale_brave='==0')),
    {'fx': 'radio_off', 'shot': 'van_inside', **N('Шипение. Связь обрывается.', 'Static. The line goes dead.', 2.2)},
    S('iris', 'Не отвечай ему. Никогда.', 'Don’t answer him. Ever.', shot='iris_van'),
    {'go': 'c4_s3'}]}

# ---------- Сцена 3. «Рисунки» ----------
SC['c4_s3'] = {'save': True, 'time': '12:40', 'beats': [
    {'fx': 'bus_stop', 'shot': 'bus_stop', **N('Буханка закипает у заброшенной автобусной остановки. Из-под капота валит пар.', 'The van overheats by an abandoned bus stop. Steam pours from under the hood.', 3.2)},
    {'fx': 'hood_up', 'shot': 'hose', **N('Лопнул шланг. Изолента — в дедовом ящике.', 'A hose has split. There’s electrical tape in the old man’s toolbox.', 2.8)},
    {'qte': 'press', 't': 6.0, 'n': 10, 'hint': {'ru': 'Мотай изоленту: жми E / пробел в ритм', 'en': 'Wrap the tape: press E / Space in rhythm'}, 'fail': 'c4_s3_tape2'},
    {'go': 'c4_s3_iris'}]}

SC['c4_s3_tape2'] = {'beats': [
    {'set': {'van_late': 1}},
    N('Изолента рвётся. Ещё раз — медленнее. Держит. Но время ушло.', 'The tape tears. Again — slower. It holds. But time is gone.', 3.0, shot='hose'),
    {'go': 'c4_s3_iris'}]}

SC['c4_s3_iris'] = {'beats': [
    {'if': {'trust': '>=3'}, 'else': 'c4_s3_sleep'},
    {'fx': 'iris_bench', 'shot': 'iris_stop', **N('Айрис сидит на лавке остановки с блокнотом. Потом сама протягивает его брату.', 'Iris sits on the bus-stop bench with her notebook. Then she holds it out to her brother.', 3.2)},
    S('iris', 'Ты хотел знать.', 'You wanted to know.', shot='iris_stop'),
    {'set': {'drawings_shown': 1, 'memory': 1}},
    {'go': 'c4_s3_pages'}]}

SC['c4_s3_pages'] = {'beats': [
    {'fx': 'page_1', 'shot': 'drawing', **N('Рисунки, как комикс. Лестница. Отец с ремнём.', 'The drawings run like a comic. The stairs. Dad with his belt.', 2.8)},
    {'fx': 'page_2', 'shot': 'drawing', **N('Айрис за дверью. Ной встаёт между ними.', 'Iris behind a door. Noah steps between them.', 2.6)},
    {'fx': 'page_3', 'shot': 'drawing', **N('Лицо Ноя. Глаза пустые, как дырки.', 'Noah’s face. The eyes empty, like holes.', 2.6)},
    {'fx': 'page_4', 'shot': 'drawing', **N('Нож. Отец на полу.', 'A knife. Dad on the floor.', 2.4)},
    {'fx': 'page_5', 'shot': 'drawing', **N('Айрис держит брата за руку. А он её не слышит.', 'Iris holding her brother’s hand. And he can’t hear her.', 2.8)},
    {'if': {'drawings_peeked': '>0'}, 'else': 'c4_s3_talk'},
    {'go': 'c4_s3_woke'}]}

SC['c4_s3_talk'] = {'beats': [
    {'fx': 'notebook_close', 'shot': 'iris_stop'},
    S('iris', 'Он шёл ко мне. Ты встал. А потом… это был не совсем ты.', 'He was coming for me. You stood up. And then… it wasn’t quite you.', shot='iris_stop'),
    C(8, [O('«Прости меня»', '“I’m sorry”', 'c4_s3_sorry'),
          O('«Это был не я»', '“That wasn’t me”', 'c4_s3_notme'),
          O('Ничего не говорить — обнять её', 'Say nothing — hold her', 'c4_s3_hug')],
      shot='noah_stop', silent='c4_s3_quiet')]}

SC['c4_s3_sorry'] = {'beats': [
    {'set': {'trust': 1, 'memory': 1}, 'toast': True},
    S('noah', 'Прости меня.', 'I’m sorry.', shot='noah_stop'),
    {'fx': 'iris_shake', 'shot': 'iris_stop'},
    S('iris', 'Ты меня спас, дурак.', 'You saved me, stupid.', shot='iris_stop'),
    {'go': 'c4_s4'}]}

SC['c4_s3_notme'] = {'beats': [
    {'set': {'trust': -1}, 'toast': True},
    S('noah', 'Это был не я.', 'That wasn’t me.', shot='noah_stop'),
    {'fx': 'notebook_take', 'shot': 'iris_stop'},
    S('iris', 'Ну да. Конечно.', 'Right. Sure.', shot='iris_stop'),
    {'go': 'c4_s4'}]}

SC['c4_s3_hug'] = {'beats': [
    {'set': {'trust': 2}, 'toast': True},
    {'fx': 'hug', 'shot': 'hug', **N('Ной просто обнимает её. Она не отстраняется.', 'Noah just holds her. She doesn’t pull away.', 2.8)},
    {'fx': 'iris_cry', 'shot': 'hug', **N('И впервые за всю дорогу плачет вслух.', 'And for the first time on the whole trip, she cries out loud.', 3.0)},
    {'go': 'c4_s4'}]}

SC['c4_s3_quiet'] = {'beats': [
    {'fx': 'notebook_take', 'shot': 'iris_stop', **N('Ной молчит. Айрис закрывает блокнот.', 'Noah says nothing. Iris closes the notebook.', 2.6)},
    {'go': 'c4_s4'}]}

SC['c4_s3_sleep'] = {'beats': [
    {'fx': 'iris_sleep', 'shot': 'iris_asleep', **N('Айрис уснула в буханке. Блокнот — у неё на коленях.', 'Iris has fallen asleep in the van. The notebook is on her lap.', 3.0)},
    C(6, [O('Тихо посмотреть', 'Take a quiet look', 'c4_s3_peek'),
          O('Не трогать', 'Leave it', 'c4_s3_leave')], shot='noah_door', silent='c4_s3_leave')]}

SC['c4_s3_peek'] = {'beats': [
    {'set': {'drawings_peeked': 1, 'memory': 1}},
    {'fx': 'notebook_peek', 'shot': 'iris_asleep', **N('Ной осторожно вытягивает блокнот у неё из-под руки.', 'Noah carefully slides the notebook out from under her hand.', 2.6)},
    {'go': 'c4_s3_pages'}]}

SC['c4_s3_woke'] = {'beats': [
    {'set': {'trust': -2}, 'toast': True},
    {'fx': 'iris_wake', 'shot': 'iris_awake'},
    S('iris', 'Ты рылся в моих вещах.', 'You went through my stuff.', shot='iris_awake'),
    {'fx': 'notebook_take', 'shot': 'iris_awake', **N('Она забирает блокнот и отворачивается к стене.', 'She takes the notebook back and turns to the wall.', 2.6)},
    {'go': 'c4_s4'}]}

SC['c4_s3_leave'] = {'beats': [
    N('Ной не трогает блокнот. Пусть спит.', 'Noah doesn’t touch the notebook. Let her sleep.', 2.4, shot='iris_asleep'),
    {'go': 'c4_s4'}]}

# ---------- Сцена 4. «Что видел Дэнни» ----------
SC['c4_s4'] = {'beats': [
    {'if': {'danny': '>0', 'danny_lost': '==0', 'danny_fear': '==0', 'lapse1': '>0'}, 'else': 'c4_s5'},
    {'fx': 'danny_sit', 'shot': 'danny_stop', **N('Дэнни садится рядом, пока Ной возится с мотором.', 'Danny sits down next to Noah while he works on the engine.', 2.8)},
    S('danny', 'На заправке… Ты не был злой. Ты был… будто тебя там нет. Глаза пустые.', 'At the gas station… You weren’t angry. You were… like you weren’t there. Empty eyes.', shot='danny_stop'),
    S('danny', 'А потом ты сказал дяде «спи». Тихо так. Как мама говорит.', 'And then you told the man, “sleep.” Real quiet. Like my mom says it.', shot='danny_stop', **I(roman_dead='==0')),
    S('danny', 'Он упал. Головой. Об эту… стойку. Ты потом плакал. Ты не хотел.', 'He fell. Hit his head. On the… counter. You cried after. You didn’t mean it.', shot='danny_stop', **I(roman_dead='>0')),
    C(6, [O('«Спасибо, что сказал»', '“Thanks for telling me”', 'c4_s4_thanks'),
          O('«Никому об этом не говори, ладно?»', '“Don’t tell anyone about this, okay?”', 'c4_s4_secret'),
          O('«Если спросят — говори правду. Как было»', '“If anyone asks — tell the truth. How it was”', 'c4_s4_truth')],
      shot='noah_engine', silent='c4_s4_quiet')]}

SC['c4_s4_thanks'] = {'beats': [
    {'set': {'danny_for': 1}},
    S('noah', 'Спасибо, что сказал.', 'Thanks for telling me.', shot='noah_engine'),
    N('Дэнни кивает.', 'Danny nods.', 1.8, shot='danny_stop'),
    {'go': 'c4_s5'}]}

SC['c4_s4_secret'] = {'beats': [
    {'set': {'danny_against': 1, 'shadow': 1}},
    S('noah', 'Никому об этом не говори, ладно?', 'Don’t tell anyone about this, okay?', shot='noah_engine'),
    N('Дэнни смотрит на него долго.', 'Danny looks at him for a long time.', 2.4, shot='danny_stop'),
    S('danny', 'Ладно.', 'Okay.', shot='danny_stop'),
    {'go': 'c4_s5'}]}

SC['c4_s4_truth'] = {'beats': [
    {'set': {'danny_for': 1, 'trust': 1}, 'toast': True},
    S('noah', 'Если спросят — говори правду. Как было.', 'If anyone asks — tell the truth. How it was.', shot='noah_engine'),
    N('Дэнни кивает. Айрис у буханки всё слышала.', 'Danny nods. By the van, Iris heard every word.', 2.6, shot='danny_stop'),
    {'go': 'c4_s5'}]}

SC['c4_s4_quiet'] = {'beats': [
    N('Ной молчит. Дэнни встаёт и уходит к буханке.', 'Noah says nothing. Danny gets up and walks back to the van.', 2.6, shot='danny_stop'),
    {'go': 'c4_s5'}]}

# ---------- Сцена 5. «Поваленная сосна» ----------
SC['c4_s5'] = {'save': True, 'time': '15:20', 'beats': [
    {'fx': 'pine', 'shot': 'pine_road', **N('Поперёк дороги лежит сосна.', 'A pine tree lies across the road.', 2.4)},
    N('Спил свежий, ровный. Её свалили.', 'The cut is fresh and clean. Someone felled it.', 2.6, shot='pine_cut'),
    {'fx': 'sedan_behind', 'shot': 'van_mirror', **N('В зеркале — чёрный седан.', 'In the mirror — the black sedan.', 2.4)},
    {'fx': 'orderly_out', 'shot': 'orderly', **N('Из-за руля выходит санитар: огромный, белый халат под пуховиком. Молчит.', 'The orderly gets out from behind the wheel: huge, a white coat under his down jacket. Silent.', 3.2)},
    {'fx': 'hale_out', 'shot': 'hale_wide', **N('С заднего сиденья — доктор Хейл. Очки, хорошее пальто, шарф. Впервые видно его лицо.', 'From the back seat — Dr. Hale. Glasses, a good coat, a scarf. For the first time, they see his face.', 3.6)},
    S('hale', 'Ной. Ну наконец-то.', 'Noah. At last.', shot='hale'),
    {'fx': 'hale_look_van', **S('hale', 'Агнесса… Сколько лет.', 'Agnessa… It’s been so long.', shot='hale')},
    S('agnes', 'Не смей. Не смей звать меня по имени.', 'Don’t you dare. Don’t you dare call me by my name.', shot='agnes_pine'),
    S('hale', 'Я понимаю, ты напуган. Ты болен, Ной, и ты сам это знаешь.', 'I understand, you’re frightened. You’re ill, Noah, and you know it.', shot='hale'),
    {'fx': 'hale_nod_iris', **S('hale', 'Ты убил отца. Следующей будет она.', 'You killed your father. She’ll be next.', shot='hale')},
    S('hale', 'Поехали со мной. Я помогу. Как тогда.', 'Come with me. I’ll help. Like before.', shot='hale'),
    S('iris', 'Как тогда — это когда ты стёр ему маму.', '“Like before” — when you erased his mom.', shot='iris_pine'),
    C(10, [O('«Что случилось с моей мамой?»', '“What happened to my mother?”', 'c4_s5_talk'),
           O('Отдать ему дневник', 'Give him the diary', 'c4_s5_diary'),
           O('Напасть — монтировка', 'Attack — the tire iron', 'c4_s5_attack'),
           O('Бежать в лес', 'Run into the woods', 'c4_s5_run')],
      shot='noah_pine', silent='c4_s5_freeze')]}

SC['c4_s5_talk'] = {'beats': [
    {'set': {'pine_talk': 1}},
    S('noah', 'Что случилось с моей мамой?', 'What happened to my mother?', shot='noah_pine'),
    S('hale', 'Несчастный случай, Ной. Ты спал. Ты не мог этого видеть.', 'An accident, Noah. You were asleep. You couldn’t have seen it.', shot='hale'),
    {'if': {'memory': '>=3'}, 'else': 'c4_s5_doubt'},
    {'set': {'hale_knows_mem': 1, 'memory': 1}},
    {'fx': 'flash_shore', 'shot': 'flash_shore', **N('Вспышка. Ночь. Фары на берегу озера. Этот самый седан.', 'A flash. Night. Headlights on the lakeshore. This very sedan.', 2.8)},
    {'fx': 'flash_end', 'shot': 'noah_pine'},
    S('noah', 'Вы там были. Той ночью. Я видел вашу машину.', 'You were there. That night. I saw your car.', shot='noah_pine'),
    {'fx': 'hale_cold', 'shot': 'hale', **N('Хейл впервые теряет спокойствие.', 'For the first time, Hale loses his composure.', 2.4)},
    S('hale', 'Взять его.', 'Take him.', shot='hale'),
    {'go': 'c4_s5_chase'}]}

SC['c4_s5_doubt'] = {'beats': [
    N('Ной сомневается. Хейл протягивает руку.', 'Noah wavers. Hale holds out his hand.', 2.6, fx='hale_hand', shot='hale_wide'),
    S('hale', 'Поехали, Ной.', 'Come on, Noah.', shot='hale'),
    {'fx': 'iris_pull', 'shot': 'iris_pine', **N('Айрис тянет брата за рукав назад.', 'Iris pulls her brother back by the sleeve.', 2.4)},
    S('iris', 'Ной. Бежим.', 'Noah. Run.', shot='iris_pine'),
    {'go': 'c4_s5_chase'}]}

SC['c4_s5_diary'] = {'beats': [
    {'set': {'pine_diary': 1}},
    S('noah', 'Айрис. Отдай ему дневник.', 'Iris. Give him the diary.', shot='noah_pine'),
    {'if': {'trust': '>=4'}, 'else': 'c4_s5_diary_no'},
    {'fx': 'diary_give', 'shot': 'iris_pine'},
    S('iris', 'Делай как знаешь.', 'Do what you think is right.', shot='iris_pine'),
    {'go': 'c4_s5_diary_hale'}]}

SC['c4_s5_diary_no'] = {'beats': [
    {'fx': 'diary_hold', 'shot': 'iris_pine', **N('Айрис прижимает дневник к груди.', 'Iris presses the diary to her chest.', 2.2)},
    S('iris', 'Нет.', 'No.', shot='iris_pine'),
    C(5, [O('Отобрать силой', 'Take it by force', 'c4_s5_diary_force'),
          O('Отступить', 'Back off', 'c4_s5_diary_keep')], shot='noah_pine', silent='c4_s5_diary_keep')]}

SC['c4_s5_diary_force'] = {'beats': [
    {'set': {'trust': -3}, 'toast': True},
    {'fx': 'diary_force', 'shot': 'iris_pine', **N('Ной вырывает дневник у сестры.', 'Noah wrenches the diary out of his sister’s hands.', 2.4)},
    {'go': 'c4_s5_diary_hale'}]}

SC['c4_s5_diary_keep'] = {'beats': [
    {'set': {'diary_kept': 1}},
    S('hale', 'Ну что ж.', 'Well then.', shot='hale'),
    {'fx': 'hale_nod', **S('hale', 'Взять его.', 'Take him.', shot='hale')},
    {'go': 'c4_s5_chase'}]}

SC['c4_s5_diary_hale'] = {'beats': [
    {'set': {'diary_hale': 1}},
    {'fx': 'hale_diary', 'shot': 'hale', **N('Хейл листает дневник. Прячет во внутренний карман.', 'Hale leafs through the diary. Tucks it into his inside pocket.', 2.8)},
    S('hale', 'Спасибо. А теперь — ты.', 'Thank you. And now — you.', shot='hale'),
    {'fx': 'orderly_step', 'shot': 'orderly', **N('Санитар делает шаг вперёд.', 'The orderly steps forward.', 2.0)},
    {'go': 'c4_s5_chase'}]}

SC['c4_s5_attack'] = {'beats': [
    {'set': {'pine_attack': 1}},
    {'fx': 'crowbar', 'shot': 'noah_pine', **N('Ной выхватывает из буханки монтировку.', 'Noah grabs the tire iron out of the van.', 2.2)},
    {'fx': 'orderly_grab', 'shot': 'fight', **N('Санитар перехватывает его руку.', 'The orderly catches his arm.', 2.2)},
    {'if': {'pills3': '==0'}, 'else': 'c4_s5_weak'},
    {'go': 'c4_s5_lapse'}]}

SC['c4_s5_lapse'] = {'beats': [
    {'set': {'orderly_dead': 1, 'shadow': 2, 'hurt4': 1}},
    {'fx': 'lapse', 'dur': 1.4},
    {'fx': 'orderly_down'},
    {'fx': 'wake', 'shot': 'fight_after', **N('Ной приходит в себя. Санитар лежит в снегу и больше не встаёт.', 'Noah comes to. The orderly lies in the snow and doesn’t get up again.', 3.2)},
    N('Костяшки Ноя в крови. Монтировка — в снегу.', 'Noah’s knuckles are bloody. The tire iron lies in the snow.', 2.6, shot='knuckles4'),
    {'fx': 'hale_back', 'shot': 'hale_wide', **N('Хейл пятится к машине.', 'Hale backs away toward his car.', 2.4)},
    {'go': 'c4_s5_chase'}]}

SC['c4_s5_weak'] = {'beats': [
    {'set': {'iris_fought': 1}},
    {'fx': 'noah_down', 'shot': 'fight', **N('Таблетка ещё в крови. Ной слабый и медленный — санитар валит его в снег.', 'The pill is still in his blood. Noah is weak and slow — the orderly drives him down into the snow.', 3.2)},
    {'fx': 'syringe', 'shot': 'hale', **N('Хейл достаёт шприц.', 'Hale takes out a syringe.', 2.2)},
    {'fx': 'iris_hit', 'shot': 'hit_two', **N('Айрис бьёт Хейла по руке. Шприц — в снегу.', 'Iris strikes Hale’s hand. The syringe drops into the snow.', 2.6)},
    S('iris', 'Беги!', 'Run!', shot='hit_two'),
    {'go': 'c4_s5_chase'}]}

SC['c4_s5_run'] = {'beats': [
    {'set': {'pine_run': 1}},
    S('noah', 'В лес! Все — в лес!', 'Into the woods! Everyone — go!', shot='noah_pine'),
    {'go': 'c4_s5_chase'}]}

SC['c4_s5_freeze'] = {'beats': [
    {'set': {'pine_freeze': 1}},
    {'fx': 'hale_elbow', 'shot': 'hale_wide', **N('Ной молчит. Хейл подходит и мягко берёт его под локоть.', 'Noah says nothing. Hale walks up and gently takes his elbow.', 3.0)},
    S('hale', 'Вот и умница.', 'Good boy.', shot='hale'),
    {'fx': 'iris_pull', 'shot': 'iris_pine'},
    S('iris', 'Ной!', 'Noah!', shot='iris_pine'),
    N('Айрис вырывает брата у него из рук — и тянет в лес.', 'Iris tears her brother out of his grip — and pulls him toward the trees.', 2.8, shot='iris_pine'),
    {'go': 'c4_s5_chase'}]}

SC['c4_s5_chase'] = {'beats': [
    {'fx': 'run_woods', 'shot': 'woods_run', **N('Все бросаются в лес. Снег по колено.', 'They all bolt into the woods. Knee-deep snow.', 2.6)},
    N('За ними — санитар с фонарём.', 'Behind them — the orderly with a flashlight.', 2.4, shot='woods_back', **I(orderly_dead='==0')),
    N('За ними — Хейл. Один. Не торопится.', 'Behind them — Hale. Alone. In no hurry.', 2.6, shot='woods_back', **I(orderly_dead='>0')),
    {'go': 'c4_s6'}]}

# ---------- Сцена 6. «Лес» ----------
SC['c4_s6'] = {'save': True, 'time': '17:10', 'beats': [
    {'fx': 'woods_dusk', 'shot': 'woods', **N('Темнеет. Снег по колено. Впереди — охотничья избушка.', 'It’s getting dark. Knee-deep snow. Ahead — a hunter’s cabin.', 3.0)},
    {'fx': 'hut_in', 'shot': 'hut', **N('Спрятаться. Сквозь щели в стенах пляшет луч фонаря.', 'Hide. Through the gaps in the walls, a flashlight beam dances.', 3.0)},
    S('hale', 'Ной… Я не сержусь. Тут холодно. Выходи — поговорим.', 'Noah… I’m not angry. It’s cold out here. Come out — let’s talk.', shot='hut_gap', **I(orderly_dead='>0')),
    {'if': {'baby': '>0'}, 'else': 'c4_s6_zoe'},
    {'fx': 'baby_whimper', 'shot': 'baby_hut', **N('Маленькая Анна начинает хныкать.', 'Little Anna starts to whimper.', 2.4)},
    {'if': {'eva_alive': '>0'}, 'else': 'c4_s6_rock_iris'},
    {'fx': 'eva_rock', 'shot': 'baby_hut', **N('Ева прижимает её к себе и укачивает. Медленно. В ритм.', 'Eva holds her close and rocks her. Slowly. In rhythm.', 2.8)},
    {'qte': 'rock', 't': 9.0, 'n': 5, 'gap': 0.7, 'hint': ROCK, 'fail': 'c4_s6_cry'},
    {'go': 'c4_s6_quiet'}]}

SC['c4_s6_rock_iris'] = {'beats': [
    {'fx': 'iris_rock', 'shot': 'baby_hut', **N('Девочка у Айрис на руках. Укачать — медленно, в ритм, не торопясь.', 'The baby is in Iris’s arms. Rock her — slowly, in rhythm, no rushing.', 3.0)},
    {'qte': 'rock', 't': 9.0, 'n': 7, 'gap': 0.8, 'hint': ROCK, 'fail': 'c4_s6_cry', 'if': {'van_late': '==0'}},
    {'qte': 'rock', 't': 9.0, 'n': 8, 'gap': 0.85, 'hint': ROCK, 'fail': 'c4_s6_cry', 'if': {'van_late': '>0'}},
    {'go': 'c4_s6_quiet'}]}

SC['c4_s6_quiet'] = {'beats': [
    {'fx': 'beam_pass', 'shot': 'hut_gap', **N('Тишина. Луч скользит по стене — и уходит мимо.', 'Silence. The beam slides along the wall — and moves on.', 3.0)},
    {'go': 'c4_s6_zoe'}]}

SC['c4_s6_cry'] = {'beats': [
    {'set': {'baby_cried': 1}},
    {'fx': 'baby_cry', 'shot': 'baby_hut', **N('Плач.', 'Crying.', 1.6)},
    {'fx': 'beam_turn', 'shot': 'hut_gap', **N('Луч останавливается. Поворачивается к избушке.', 'The beam stops. Turns toward the cabin.', 2.6)},
    {'fx': 'hut_run', 'shot': 'woods_run', **N('Бежать дальше — в темноту.', 'Run on — into the dark.', 2.2)},
    {'go': 'c4_s6_zoe'}]}

SC['c4_s6_zoe'] = {'beats': [
    {'if': ZOE, 'else': 'c4_s6_agnes'},
    {'fx': 'zoe_grab', 'shot': 'zoe_trail', **N('На тропе санитар хватает Зою.', 'On the trail, the orderly grabs Zoe.', 2.4, **I(orderly_dead='==0'))},
    {'fx': 'zoe_grab', 'shot': 'zoe_trail', **N('На тропе из темноты — Хейл. Шприц — Зое в шею.', 'On the trail, out of the dark — Hale. A syringe to Zoe’s neck.', 2.8, **I(orderly_dead='>0'))},
    S('zoe', 'Беги! Бегите, ну!', 'Run! Go, all of you, run!', shot='zoe_trail', **I(romance='<=0')),
    S('zoe', 'Ной!', 'Noah!', shot='zoe_trail', **I(romance='>0')),
    C(6, [O('Броситься на санитара', 'Throw yourself at the orderly', 'c4_s6_zoe_save', **I(orderly_dead='==0')),
          O('Броситься на Хейла', 'Throw yourself at Hale', 'c4_s6_zoe_save', **I(orderly_dead='>0')),
          O('Бежать с детьми', 'Run with the kids', 'c4_s6_zoe_lost')], shot='noah_woods', silent='c4_s6_zoe_lost')]}

SC['c4_s6_zoe_save'] = {'beats': [
    {'set': {'zoe_saved': 1, 'zoe_trust': 1}},
    {'if': {'pills3': '==0'}, 'else': 'c4_s6_zoe_pull'},
    {'set': {'shadow': 1, 'hurt4': 1}},
    {'fx': 'lapse', 'dur': 1.2},
    {'fx': 'zoe_rough'},
    {'fx': 'wake', 'shot': 'zoe_after', **N('Ной приходит в себя. Зоя рядом. У Ноя разбиты руки.', 'Noah comes to. Zoe is beside him. Noah’s hands are split open.', 3.0)},
    N('Санитар стонет в снегу. «Второй он» вытащил её — жёстко.', 'The orderly groans in the snow. “The other him” got her out — the hard way.', 3.0, shot='zoe_after', **I(orderly_dead='==0')),
    N('Хейл отползает в темноту. «Второй он» вытащил её — жёстко.', 'Hale crawls off into the dark. “The other him” got her out — the hard way.', 3.0, shot='zoe_after', **I(orderly_dead='>0')),
    {'go': 'c4_s6_agnes'}]}

SC['c4_s6_zoe_pull'] = {'beats': [
    {'fx': 'zoe_pull', 'shot': 'zoe_trail', **N('Ной сбивает санитара с ног. Зоя вырывается.', 'Noah knocks the orderly off his feet. Zoe tears free.', 2.6, **I(orderly_dead='==0'))},
    {'fx': 'zoe_pull', 'shot': 'zoe_trail', **N('Ной сбивает Хейла с ног. Шприц летит в снег. Зоя вырывается.', 'Noah knocks Hale off his feet. The syringe flies into the snow. Zoe tears free.', 2.8, **I(orderly_dead='>0'))},
    {'go': 'c4_s6_agnes'}]}

SC['c4_s6_zoe_lost'] = {'beats': [
    {'set': {'zoe_taken4': 1, 'zoe_taken': 1, 'zoe_gone': 1}},
    {'fx': 'zoe_dragged', 'shot': 'woods_back', **N('Зою уводят к чёрной машине. Её крик — всё тише.', 'They drag Zoe toward the black car. Her screams grow fainter.', 3.0)},
    {'go': 'c4_s6_agnes'}]}

SC['c4_s6_agnes'] = {'beats': [
    {'fx': 'agnes_sit', 'shot': 'agnes_snow', **N('Бабушка отстаёт. Задыхается. Садится в снег.', 'Grandma falls behind. She’s gasping. She sits down in the snow.', 2.8)},
    S('agnes', 'Идите. Идите, кому говорю. Я старая. Я его задержу.', 'Go. Go on, I said. I’m old. I’ll hold him off.', shot='agnes_snow'),
    {'fx': 'hale_dark', **S('hale', 'Агнесса, не надо. Ты же знаешь, я врач.', 'Agnessa, don’t. You know I’m a doctor.', shot='woods_dark')},
    S('agnes', 'Это ты ему сказал. Аню. Ты её отдал.', 'It was you who told him. About Annie. You gave her away.', shot='agnes_snow', **I(agnes_knows_hale='>0')),
    N('Хейл молчит.', 'Hale says nothing.', 2.0, shot='woods_dark', **I(agnes_knows_hale='>0')),
    C(6, [O('Вернуться за ней', 'Go back for her', 'c4_s6_agnes_save'),
          O('Уйти', 'Keep going', 'c4_s6_agnes_lost')], shot='noah_woods', silent='c4_s6_agnes_lost')]}

SC['c4_s6_agnes_save'] = {'beats': [
    {'set': {'agnes_limp': 1}},
    {'fx': 'carry_agnes', 'shot': 'woods_carry', **N('Ной возвращается и тащит бабушку на себе. Она подвернула ногу.', 'Noah goes back and hauls Grandma along on his back. She’s twisted her ankle.', 3.0)},
    N('Погоня ближе. Но она жива.', 'The chase is closer. But she’s alive.', 2.4, shot='woods_back'),
    {'go': 'c4_s6_danny'}]}

SC['c4_s6_agnes_lost'] = {'beats': [
    {'set': {'agnes_dead': 1}},
    {'fx': 'agnes_left', 'shot': 'woods_back', **N('Они уходят. Позади — голос бабушки, ровный.', 'They walk away. Behind them — Grandma’s voice, steady.', 2.6)},
    {'fx': 'agnes_song', 'shot': 'agnes_snow', **N('Колыбельная. Та самая.', 'The lullaby. That same one.', 3.4)},
    {'fx': 'song_stop', 'shot': 'woods_dark', **N('Потом — тишина.', 'Then — silence.', 2.6)},
    {'go': 'c4_s6_danny'}]}

SC['c4_s6_danny'] = {'beats': [
    {'if': DANNY_FEAR, 'else': 'c4_s7'},
    {'fx': 'hale_call', **S('hale', 'Дэнни. Дэнни, иди сюда. Мама в машине. Она тебя ищет.', 'Danny. Danny, come here. Your mom’s in the car. She’s looking for you.', shot='woods_dark')},
    {'fx': 'danny_freeze', 'shot': 'danny_woods', **N('Дэнни замирает.', 'Danny freezes.', 2.0)},
    C(6, [O('«Айрис, позови его»', '“Iris, call him”', 'c4_s6_danny_iris'),
          O('Бежать за ним', 'Run after him', 'c4_s6_danny_run'),
          O('Отпустить', 'Let him go', 'c4_s6_danny_go')], shot='noah_woods', silent='c4_s6_danny_go')]}

SC['c4_s6_danny_iris'] = {'beats': [
    S('iris', 'Дэнни. Иди ко мне.', 'Danny. Come here, to me.', shot='iris_woods'),
    {'if': {'hurt4': '==0'}, 'else': 'c4_s6_danny_no'},
    {'set': {'danny_stayed': 1}},
    {'fx': 'danny_to_iris', 'shot': 'danny_woods', **N('Дэнни долго стоит. Потом идёт к Айрис.', 'Danny stands there for a long time. Then he walks to Iris.', 2.8)},
    {'go': 'c4_s7'}]}

SC['c4_s6_danny_no'] = {'beats': [
    N('Дэнни смотрит на разбитые руки Ноя. И уходит на голос.', 'Danny looks at Noah’s battered hands. And walks toward the voice.', 3.0, shot='danny_woods'),
    {'go': 'c4_s6_danny_hale'}]}

SC['c4_s6_danny_run'] = {'beats': [
    {'fx': 'danny_bite', 'shot': 'danny_woods', **N('Ной догоняет его — Дэнни кусает его за руку и убегает к машине.', 'Noah catches up with him — Danny bites his hand and runs for the car.', 3.0)},
    {'go': 'c4_s6_danny_hale'}]}

SC['c4_s6_danny_go'] = {'beats': [
    N('Дэнни уходит на голос.', 'Danny walks toward the voice.', 2.2, shot='danny_woods'),
    {'go': 'c4_s6_danny_hale'}]}

SC['c4_s6_danny_hale'] = {'beats': [
    {'set': {'danny_hale': 1}},
    {'fx': 'danny_gone', 'shot': 'woods_dark', **N('Фонарь гаснет. Где-то далеко хлопает дверь машины.', 'The flashlight goes out. Somewhere far off, a car door slams.', 3.0)},
    {'go': 'c4_s7'}]}

# ---------- Сцена 7. «Озеро» ----------
SC['c4_s7'] = {'save': True, 'time': '07:10', 'beats': [
    {'fx': 'lake_dawn'},
    {'fx': 'fade_in', 'shot': 'lake_wide', **N('На рассвете лес кончается. Белая гладь замёрзшего озера.', 'At dawn the forest ends. The white expanse of a frozen lake.', 3.2)},
    N('На берегу — старая дача, лодочный сарай, мостки под снегом. То самое место.', 'On the shore — an old lake house, a boathouse, a dock under the snow. That same place.', 3.4, shot='dacha'),
    S('iris', 'Мы приехали.', 'We’re here.', shot='iris_lake'),
    {'fx': 'boathouse_gap', 'shot': 'boathouse', **N('Ной смотрит на сарай. Щель в досках.', 'Noah looks at the boathouse. A gap between the boards.', 2.6)},
    {'fx': 'kid_eyes', 'shot': 'gap_eyes', **N('На секунду — глаза восьмилетнего мальчика с той стороны.', 'For a second — the eyes of an eight-year-old boy on the other side.', 2.8)},
    {'fx': 'far_sedan', 'shot': 'lake_far', **N('На другом берегу медленно загораются две фары. Чёрный седан объезжает озеро.', 'On the far shore, two headlights slowly come on. The black sedan is circling the lake.', 3.6)},
    {'go': 'c4_end'}]}

SC['c4_end'] = {'beats': [
    {'fx': 'fade_out', 'dur': 1.4},
    T('Агнес так и осталась в лесу. Её найдут потом — в снегу, будто уснула.', 'Agnes stayed behind in the forest. They’ll find her later — in the snow, as if asleep.', 3.6, **I(agnes_dead='>0')),
    {'end': 4}]}

DATA = {
    'chapter': 4, 'start': 'c4_recap', 'ready': True,    # True — когда постановка главы готова (chapter4.gd)
    'title': {'ru': 'ГЛАВА 4 · ЧЁРНАЯ МАШИНА', 'en': 'CHAPTER 4 · THE BLACK CAR'},
    'names': {'hale': {'ru': 'ХЕЙЛ', 'en': 'HALE'}},
    'flags': {k: 0 for k in ['pills_pocket', 'pills_snow', 'pills_iris', 'pills_left', 'van_late', 'zoe_believed', 'zoe_doubted',
                             'hale_radio', 'drawings_shown', 'drawings_peeked', 'danny_for', 'danny_against', 'pine_talk', 'pine_diary',
                             'pine_attack', 'pine_run', 'pine_freeze', 'hale_knows_mem', 'diary_hale', 'diary_kept', 'orderly_dead',
                             'iris_fought', 'baby_cried', 'zoe_saved', 'zoe_taken4', 'agnes_limp', 'agnes_dead', 'danny_hale',
                             'danny_stayed', 'hurt4']},
    'map': [
        {'key': 'pills', 'ru': 'Таблетки №4', 'en': 'Pills #4', 'opts': [
            {'id': 'pocket', 'ru': 'в кармане', 'en': 'in his pocket', 'if': {'pills_pocket': '>0'}},
            {'id': 'snow', 'ru': 'в сугробе', 'en': 'in the snow', 'if': {'pills_snow': '>0'}},
            {'id': 'iris', 'ru': 'у Айрис', 'en': 'with Iris', 'if': {'pills_iris': '>0'}},
            {'id': 'left', 'ru': 'на сиденье', 'en': 'left on the seat', 'if': {}}]},
        {'key': 'van', 'ru': 'Буханка', 'en': 'The old van', 'opts': [
            {'id': 'late', 'ru': 'потеряли время', 'en': 'lost time', 'if': {'van_late': '>0'}},
            {'id': 'fast', 'ru': 'завелась сразу', 'en': 'started right up', 'if': {}}]},
        {'key': 'zoe_story', 'ru': '«Тихие сосны»', 'en': 'Quiet Pines', 'opts': [
            {'id': 'believed', 'ru': 'поверил Зое', 'en': 'believed Zoe', 'if': {'zoe_believed': '>0'}},
            {'id': 'doubted', 'ru': 'поверил Айрис', 'en': 'sided with Iris', 'if': {'zoe_doubted': '>0'}},
            {'id': 'radio', 'ru': 'голос Хейла', 'en': 'Hale’s voice', 'if': {'hale_radio': '>0'}},
            {'id': 'quiet', 'ru': 'промолчал', 'en': 'kept quiet', 'if': {'zoe': '>0', 'zoe_taken': '==0'}},
            {'id': 'none', 'ru': 'Зои нет', 'en': 'no Zoe', 'if': {}}]},
        {'key': 'drawings', 'ru': 'Рисунки', 'en': 'The drawings', 'opts': [
            {'id': 'shown', 'ru': 'показала сама', 'en': 'she showed him', 'if': {'drawings_shown': '>0'}},
            {'id': 'peeked', 'ru': 'подсмотрел', 'en': 'he peeked', 'if': {'drawings_peeked': '>0'}},
            {'id': 'none', 'ru': 'не видел', 'en': 'never saw them', 'if': {}}]},
        {'key': 'danny', 'ru': 'Дэнни', 'en': 'Danny', 'opts': [
            {'id': 'hale', 'ru': 'ушёл на голос', 'en': 'went to the voice', 'if': {'danny_hale': '>0'}},
            {'id': 'for', 'ru': 'свидетель за Ноя', 'en': 'a witness for Noah', 'if': {'danny_for': '>0'}},
            {'id': 'against', 'ru': 'обещал молчать', 'en': 'promised silence', 'if': {'danny_against': '>0'}},
            {'id': 'with', 'ru': 'с ними', 'en': 'with them', 'if': {'danny': '>0', 'danny_lost': '==0'}},
            {'id': 'none', 'ru': 'не с ними', 'en': 'not with them', 'if': {}}]},
        {'key': 'pine', 'ru': 'Сосна', 'en': 'The pine', 'opts': [
            {'id': 'talk', 'ru': 'спросил про маму', 'en': 'asked about Mom', 'if': {'pine_talk': '>0'}},
            {'id': 'diary', 'ru': 'отдал дневник', 'en': 'gave up the diary', 'if': {'diary_hale': '>0'}},
            {'id': 'kept', 'ru': 'дневник у Айрис', 'en': 'Iris kept the diary', 'if': {'diary_kept': '>0'}},
            {'id': 'killed', 'ru': 'санитар убит', 'en': 'the orderly killed', 'if': {'orderly_dead': '>0'}},
            {'id': 'attack', 'ru': 'напал', 'en': 'attacked', 'if': {'pine_attack': '>0'}},
            {'id': 'run', 'ru': 'бежал в лес', 'en': 'ran for the woods', 'if': {'pine_run': '>0'}},
            {'id': 'freeze', 'ru': 'промолчал', 'en': 'froze', 'if': {}}]},
        {'key': 'baby', 'ru': 'Избушка', 'en': 'The cabin', 'opts': [
            {'id': 'cried', 'ru': 'плач выдал', 'en': 'the crying gave them away', 'if': {'baby_cried': '>0'}},
            {'id': 'quiet', 'ru': 'укачали', 'en': 'rocked to sleep', 'if': {'baby': '>0'}},
            {'id': 'none', 'ru': 'без младенца', 'en': 'no baby', 'if': {}}]},
        {'key': 'zoe', 'ru': 'Зоя в лесу', 'en': 'Zoe in the woods', 'opts': [
            {'id': 'saved', 'ru': 'спас', 'en': 'saved', 'if': {'zoe_saved': '>0'}},
            {'id': 'taken', 'ru': 'забрали', 'en': 'taken', 'if': {'zoe_taken4': '>0'}},
            {'id': 'hale', 'ru': 'у Хейла с кафе', 'en': 'with Hale since the diner', 'if': {'zoe_taken': '>0'}},
            {'id': 'none', 'ru': 'Зои нет', 'en': 'no Zoe', 'if': {}}]},
        {'key': 'agnes', 'ru': 'Агнес', 'en': 'Agnes', 'opts': [
            {'id': 'dead', 'ru': 'осталась в лесу', 'en': 'left in the woods', 'if': {'agnes_dead': '>0'}},
            {'id': 'saved', 'ru': 'вернулся за ней', 'en': 'went back for her', 'if': {}}]},
    ],
    'scenes': SC,
}

if __name__ == '__main__':
    json.dump(DATA, open(OUT, 'w'), ensure_ascii=False, indent=1)
    open(OUT, 'a').write('\n')
    says = sum(1 for s in SC.values() for b in s['beats'] if 'say' in b)
    print('ch4.json:', len(SC), 'сцен,', says, 'реплик')
