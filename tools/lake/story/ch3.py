#!/usr/bin/env python3
"""WHAT THE LAKE KEEPS — глава 3 «Деревня» в данные игры (godot/lake/data/ch3.json) по сценарию newgame/ch3.md.
Тексты RU+EN, выборы на время, действия (qte), флаги, карта главы. Постановка — godot/lake/scripts/chapter3.gd (fx и планы).
  python3 tools/lake/story/ch3.py"""
import json, os

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '../../../godot/lake/data/ch3.json')


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


SC = {}

# ---------- «Ранее» ----------
SC['c3_recap'] = {'save': True, 'time': '18:10', 'beats': [
    {'fx': 'black'},
    T('Ранее…', 'Previously…', 1.6),
    T('Зоя едет с ними.', 'Zoe is riding with them.', 2.4, **{'if': {'zoe': '>0', 'zoe_gone': '==0'}}),
    T('Зоя пропала ночью у кафе. На снегу — её больничный браслет.', 'Zoe vanished in the night at the diner. Her hospital bracelet lay in the snow.', 3.0, **{'if': {'zoe_taken': '>0'}}),
    T('Ной бежал с поста полиции.', 'Noah ran the police checkpoint.', 2.4, **{'if': {'fled': '>0'}}),
    T('Второй пузырёк таблеток — смыт в унитаз.', 'The second bottle of pills — flushed down the toilet.', 2.6, **{'if': {'pills_flushed': '>0'}}),
    T('В туалете кафе в зеркале был мокрый мальчик.', 'In the diner bathroom mirror — a soaking wet boy.', 2.6, **{'if': {'pills2': '==0', 'pills_flushed': '==0', 'memory': '>=2'}}),
    T('Голос из чёрного седана: «Как голова, Ной? Таблетки ещё есть?»', 'A voice from the black sedan: “How’s your head, Noah? Still have your pills?”', 3.2, **{'if': {'heard_hale': '>0'}}),
    T('Беременная Ева едет с ними на север.', 'Eva, pregnant, is riding north with them.', 2.6, **{'if': {'eva': '>0'}}),
    T('Ева осталась у кафе одна.', 'Eva stayed behind at the diner, alone.', 2.6, **{'if': {'eva_alone': '>0'}}),
    T('Буран. Поперёк трассы — фура. Боковая дорога. Позади — две фары.', 'A blizzard. A jackknifed truck across the highway. A side road. Two headlights behind.', 3.2),
    {'go': 'c3_s1'}]}

# ---------- Сцена 1. «Сугроб» ----------
SC['c3_s1'] = {'save': True, 'time': '18:40', 'beats': [
    {'fx': 'c3_start', 'shot': 'road'},
    {'fx': 'fade_in', 'shot': 'road', **N('Глава 3. «Деревня». Колея под снегом. Дворники не справляются.', 'Chapter 3. “The Village.” Ruts under the snow. The wipers can’t keep up.', 3.4)},
    N('В фарах — тёмные силуэты домов. Один огонёк.', 'Dark shapes of houses in the headlights. One light.', 2.6, shot='village_far'),
    {'fx': 'stuck', 'shot': 'wheel', **N('Колёса буксуют. Машина садится в сугроб — в двухстах метрах от деревни.', 'The wheels spin. The car sinks into a drift — two hundred meters from the village.', 3.0)},
    {'if': {'eva_alone': '>0'}, 'else': 'c3_s1_dig'},
    {'go': 'c3_s1_evacar'}]}

SC['c3_s1_evacar'] = {'beats': [
    {'fx': 'eva_ditch', 'shot': 'eva_ditch', **N('В фарах — её машина в кювете, полузанесённая снегом.', 'In the headlights — her car in the ditch, half buried in snow.', 2.8)},
    {'fx': 'eva_in_ditch', 'shot': 'eva_ditch_in', **N('Внутри — Ева. Схватки. Губы синие. Она поехала сама и застряла.', 'Eva is inside. Contractions. Her lips are blue. She drove on alone and got stuck.', 3.2)},
    S('eva', 'Я соврала… Не через месяц. Сейчас.', 'I lied… It’s not next month. It’s now.', shot='eva_ditch_in'),
    {'set': {'eva_found': 1}},
    C(6, [O('Нести её на руках к огоньку', 'Carry her to the light', 'c3_s1_carry'),
          O('Бежать за помощью одному', 'Run for help alone', 'c3_s1_run')], shot='noah_out', silent='c3_s1_carry')]}

SC['c3_s1_carry'] = {'beats': [
    {'set': {'eva_cold': 1, 'walked': 1}},
    {'fx': 'carry_eva', 'shot': 'walk_snow', **N('Ной несёт её на руках. Снег по колено. Айрис с Дэнни — следом.', 'Noah carries her in his arms. Knee-deep snow. Iris and Danny follow.', 3.2, **{'if': {'danny': '>0'}})},
    {'fx': 'carry_eva', 'shot': 'walk_snow', **N('Ной несёт её на руках. Снег по колено. Айрис — следом.', 'Noah carries her in his arms. Knee-deep snow. Iris follows.', 3.2, **{'if': {'danny': '==0'}})},
    {'go': 'c3_s2'}]}

SC['c3_s1_run'] = {'beats': [
    {'set': {'iris_alone': 1, 'trust': 1}},
    {'fx': 'noah_run', 'shot': 'walk_snow', **N('Ной бежит к огоньку один. Айрис остаётся с Евой в машине — греет её.', 'Noah runs for the light alone. Iris stays with Eva in the car, keeping her warm.', 3.2)},
    N('Он возвращается со стариковскими санками. Быстро. Айрис запомнит.', 'He comes back with an old man’s sled. Fast. Iris will remember that.', 3.0, shot='iris'),
    {'go': 'c3_s2'}]}

SC['c3_s1_dig'] = {'beats': [
    {'fx': 'water_break', 'shot': 'back', **N('У Евы отходят воды.', 'Eva’s water breaks.', 2.4, **{'if': {'eva': '>0'}})},
    S('eva', 'Я соврала… Не через месяц.', 'I lied… It’s not next month.', shot='eva', **{'if': {'eva': '>0'}}),
    N('Зоя пересаживается к ней назад и держит её за руку.', 'Zoe climbs into the back beside her and holds her hand.', 2.8, shot='back', **{'if': {'eva': '>0', 'zoe': '>0', 'zoe_gone': '==0'}}),
    {'fx': 'noah_dig', 'shot': 'wheel_out', **N('Ной — механик. Спустить колёса и подложить коврики.', 'Noah is a mechanic. Let air out of the tires and wedge the floor mats under them.', 2.8)},
    {'qte': 'mash', 't': 6.0, 'n': 12, 'hint': {'ru': 'Быстро жми E / пробел — подкопать, спустить, подложить', 'en': 'Mash E / Space — dig, deflate, wedge'}, 'fail': 'c3_s1_walk'},
    {'fx': 'unstuck', 'shot': 'wheel', **N('Колёса цепляются за коврики. Машина выползает из сугроба.', 'The tires bite on the mats. The car crawls out of the drift.', 3.0)},
    {'go': 'c3_s2'}]}

SC['c3_s1_walk'] = {'beats': [
    {'set': {'walked': 1, 'eva_cold': 1}},
    {'fx': 'walk_snow', 'shot': 'walk_snow', **N('Не вышло. Пешком: снег по колено, Айрис держит Дэнни за руку.', 'No luck. On foot: knee-deep snow, Iris holding Danny’s hand.', 3.2, **{'if': {'danny': '>0'}})},
    {'fx': 'walk_snow', 'shot': 'walk_snow', **N('Не вышло. Пешком: снег по колено.', 'No luck. On foot: knee-deep snow.', 3.0, **{'if': {'danny': '==0'}})},
    N('Ной ведёт Еву под руку. Она останавливается на каждой схватке.', 'Noah holds Eva up. She stops at every contraction.', 3.0, shot='walk_snow', **{'if': {'eva': '>0'}}),
    {'go': 'c3_s2'}]}

# ---------- Сцена 2. «Синие ставни» ----------
SC['c3_s2'] = {'time': '19:20', 'beats': [
    {'fx': 'village', 'shot': 'village_street', **N('Деревня у леса. Несколько домов под снегом. Магазин с телефоном — тёмный.', 'A village at the edge of the forest. A few houses under the snow. The store with the phone — dark.', 3.2)},
    S('iris', 'Дом с синими ставнями. У колодца. Мама писала.', 'The house with blue shutters. By the well. Mom wrote about it.', shot='iris_out'),
    {'fx': 'agnes_door', 'shot': 'agnes_door', **N('Дверь открывает старуха: седая коса, валенки, трубка в зубах. Она долго смотрит на Ноя.', 'An old woman opens the door: a gray braid, felt boots, a pipe in her teeth. She looks at Noah for a long time.', 3.4)},
    S('agnes', '…Анечка.', '…Annie.', shot='agnes'),
    N('Она видит в его лице мать. Потом смотрит на Айрис — и отступает, пропуская всех в тепло.', 'She sees his mother in his face. Then she looks at Iris — and steps aside, letting them all into the warmth.', 3.4, shot='agnes_door'),
    {'fx': 'house_in', 'shot': 'house_wide', **N('Пахнет травами. В углу — печь. На стене — фотографии Анны девочкой.', 'It smells of herbs. A stove in the corner. On the wall — photos of Anna as a girl.', 3.2)},
    N('Анна на фотографии улыбается. Ной её не помнит. Помнит воду.', 'Anna is smiling in the photo. Noah doesn’t remember her. He remembers water.', 3.0, shot='photos'),
    C(6, [O('«Ты — наша бабушка?»', '“Are you our grandmother?”', 'c3_s2_gran'),
          O('«Тут женщина рожает. Помогите»', '“There’s a woman in labor. Help us”', 'c3_s2_help', **{'if': {'eva': '>0'}}),
          O('«Тут женщина рожает. Помогите»', '“There’s a woman in labor. Help us”', 'c3_s2_help', **{'if': {'eva_found': '>0'}})],
      shot='noah_house', silent='c3_s2_quiet')]}

SC['c3_s2_gran'] = {'beats': [
    {'set': {'agnes_warm': 1}},
    S('noah', 'Ты — наша бабушка?', 'Are you our grandmother?', shot='noah_house'),
    S('agnes', 'Была. Пока ваш отец не запретил.', 'I was. Until your father forbade it.', shot='agnes'),
    {'go': 'c3_s3'}]}

SC['c3_s2_help'] = {'beats': [
    {'set': {'birth_time': 1}},
    S('noah', 'Тут женщина рожает. Помогите.', 'There’s a woman in labor. Help us.', shot='noah_house'),
    {'fx': 'sleeves', 'shot': 'agnes'},
    S('agnes', 'Воду на печь. Чистые простыни — в сундуке.', 'Water on the stove. Clean sheets are in the chest.', shot='agnes'),
    {'go': 'c3_s3'}]}

SC['c3_s2_quiet'] = {'beats': [
    {'set': {'agnes_knows': 1}},
    S('iris', 'Бабушка, это Ной. Он не помнит.', 'Grandma, this is Noah. He doesn’t remember.', shot='iris_house'),
    {'fx': 'agnes_nod', 'shot': 'agnes'},
    S('agnes', 'Знаю. Это ему сделали.', 'I know. Somebody did that to him.', shot='agnes'),
    {'go': 'c3_s3'}]}

# ---------- Сцена 3. «Роды» ----------
SC['c3_s3'] = {'save': True, 'time': '21:30', 'beats': [
    {'if': {'eva': '>0'}, 'else': 'c3_s3_eva2'},
    {'go': 'c3_s3_dark'}]}
SC['c3_s3_eva2'] = {'beats': [
    {'if': {'eva_found': '>0'}, 'else': 'c3_s4'},
    {'go': 'c3_s3_dark'}]}

SC['c3_s3_dark'] = {'beats': [
    {'fx': 'birth_bed', 'shot': 'bed', **N('Еву кладут на кровать за печью. Схватки всё чаще.', 'They lay Eva on the bed behind the stove. The contractions come faster.', 3.0)},
    {'fx': 'lights_out', 'shot': 'house_wide', **N('Свет мигает — и гаснет. Провода оборвало.', 'The light flickers — and goes out. The lines are down.', 2.8)},
    S('agnes', 'Без света не справлюсь. Генератор-то в сарае есть, а бензину нет. Бензин — в магазине, у Петра.', 'I can’t do this without light. There’s a generator in the shed, but no gas. Gas is at the store — Pyotr’s place.', shot='agnes_candle'),
    C(8, [O('Пойду я', 'I’ll go', 'c3_s3_noah'),
          O('Пусть сходит Зоя', 'Let Zoe go', 'c3_s3_zoe', **{'if': {'zoe': '>0', 'zoe_gone': '==0'}}),
          O('Никто — свечи и печь', 'No one — candles and the stove', 'c3_s3_candles')],
      shot='noah_house', silent='c3_s3_candles')]}

SC['c3_s3_noah'] = {'beats': [
    {'set': {'light': 1, 'went_noah': 1}},
    {'fx': 'noah_storm', 'shot': 'storm_noah', **N('Ной один в буране. Гул в голове нарастает.', 'Noah alone in the blizzard. The hum in his head swells.', 3.0)},
    {'if': {'pills': '==0'}, 'else': 'c3_s3_noah_ok'},
    {'go': 'c3_s3_lapse'}]}

SC['c3_s3_noah_ok'] = {'beats': [
    {'fx': 'shop', 'shot': 'shop', **N('Магазин. Дверь не заперта — Пётр спит в подсобке. Канистра у стены.', 'The store. The door isn’t locked — Pyotr is asleep in the back. A gas can by the wall.', 3.0)},
    {'fx': 'generator', 'shot': 'house_wide', **N('Генератор кашляет и заводится. Свет.', 'The generator coughs and starts. Light.', 2.6)},
    {'go': 'c3_s3_birth'}]}

SC['c3_s3_lapse'] = {'beats': [
    {'set': {'lapse_shop': 1}},
    {'fx': 'lapse', 'dur': 1.4},
    {'set': {'shadow': 1}, 'if': {'shadow': '>=1'}},
    {'fx': 'shop_broken'},
    {'fx': 'wake', 'shot': 'shop', **N('Ной приходит в себя у магазина. В руке — канистра. Окно магазина разбито.', 'Noah comes to outside the store. A gas can in his hand. The store window is smashed.', 3.2)},
    N('Он не помнит, как сюда дошёл.', 'He doesn’t remember getting here.', 2.4, shot='storm_noah'),
    {'fx': 'generator', 'shot': 'house_wide', **N('Генератор кашляет и заводится. Свет.', 'The generator coughs and starts. Light.', 2.6)},
    {'go': 'c3_s3_birth'}]}

SC['c3_s3_zoe'] = {'beats': [
    {'set': {'light': 1, 'zoe_trust': 1, 'hale_here': 1}},
    {'fx': 'zoe_storm', 'shot': 'house_door', **N('Зоя уходит в метель. Долго. Потом — шаги на крыльце.', 'Zoe walks out into the storm. A long time. Then — footsteps on the porch.', 3.0)},
    S('zoe', 'На краю деревни стоит машина. Чёрная. С выключенными фарами.', 'There’s a car at the edge of the village. Black. Headlights off.', shot='zoe_house'),
    {'fx': 'generator', 'shot': 'house_wide', **N('Генератор кашляет и заводится. Свет. Все знают, кто здесь.', 'The generator coughs and starts. Light. Everyone knows who’s here.', 3.0)},
    {'go': 'c3_s3_birth'}]}

SC['c3_s3_candles'] = {'beats': [
    {'fx': 'candles', 'shot': 'agnes_candle', **N('Свечи и отсвет печи. Агнес работает на ощупь.', 'Candles and the glow of the stove. Agnes works by touch.', 3.0)},
    {'go': 'c3_s3_birth'}]}

SC['c3_s3_birth'] = {'beats': [
    {'fx': 'birth', 'shot': 'bed', **N('Крик Евы. Агнес спокойная и жёсткая. Айрис держит Еву за руку.', 'Eva screams. Agnes is calm and hard. Iris holds Eva’s hand.', 3.2)},
    N('Дэнни сидит под столом, зажав уши.', 'Danny sits under the table with his hands over his ears.', 2.6, shot='danny_table', **{'if': {'danny': '>0'}}),
    S('eva', 'Ты смелая, слышишь? Если меня не будет — ты справишься.', 'You’re brave, you hear me? If I’m gone — you’ll manage.', shot='eva_bed', **{'if': {'trust': '>=2'}}),
    {'fx': 'baby_cry', 'shot': 'bed', **N('Плач. Девочка.', 'A cry. A girl.', 2.4)},
    {'fx': 'bleed', 'shot': 'bed', **N('Но кровь не останавливается.', 'But the bleeding won’t stop.', 2.4)},
    S('agnes', 'Держи здесь! Сильнее!', 'Press here! Harder!', shot='agnes_bed'),
    N('Держит Айрис — Ной стоит у стены, его трясёт.', 'Iris holds on — Noah stands by the wall, shaking.', 2.6, shot='iris_bed', **{'if': {'lapse_shop': '>0'}}),
    {'fx': 'press', 'shot': 'press'},
    # сложность: Ева из кювета (замёрзла), нет света, провал у Ноя — каждое сверху тяжелее
    {'set': {'hard': 1}, 'if': {'eva_cold': '>0'}},
    {'set': {'hard': 1}, 'if': {'light': '==0'}},
    {'set': {'hard': 1}, 'if': {'lapse_shop': '>0'}},
    {'set': {'hard': -1}, 'if': {'birth_time': '>0'}},
    {'qte': 'press', 't': 7.0, 'n': 14, 'hint': {'ru': 'Держи! Жми E / пробел — в ритм, не отпускай', 'en': 'Hold on! Press E / Space — keep the rhythm, don’t let go'}, 'fail': 'c3_s3_dead', 'if': {'hard': '<=0'}},
    {'qte': 'press', 't': 7.0, 'n': 19, 'hint': {'ru': 'Держи! Жми E / пробел — в ритм, не отпускай', 'en': 'Hold on! Press E / Space — keep the rhythm, don’t let go'}, 'fail': 'c3_s3_dead', 'if': {'hard': '==1'}},
    {'qte': 'press', 't': 7.0, 'n': 24, 'hint': {'ru': 'Держи! Жми E / пробел — в ритм, не отпускай', 'en': 'Hold on! Press E / Space — keep the rhythm, don’t let go'}, 'fail': 'c3_s3_dead', 'if': {'hard': '>=2'}},
    {'go': 'c3_s3_alive'}]}

SC['c3_s3_alive'] = {'beats': [
    {'set': {'eva_alive': 1, 'baby': 1}},
    {'fx': 'eva_baby', 'shot': 'eva_bed', **N('За полночь. Ева, белая и мокрая, держит девочку.', 'Past midnight. Eva, pale and soaked, holds the little girl.', 3.0)},
    S('eva', 'Как её назвать?', 'What should I call her?', shot='eva_bed'),
    S('iris', 'Анна.', 'Anna.', shot='iris_bed'),
    N('Ева кивает.', 'Eva nods.', 2.0, shot='eva_bed'),
    {'go': 'c3_s4'}]}

SC['c3_s3_dead'] = {'beats': [
    {'set': {'eva_dead': 1, 'baby': 1}},
    {'fx': 'eva_fading', 'shot': 'eva_bed', **N('Кровь не останавливается.', 'The bleeding won’t stop.', 2.4)},
    S('eva', 'Назови её…', 'Name her…', shot='eva_bed'),
    {'fx': 'eva_dies', 'shot': 'eva_bed', **N('Она не договаривает.', 'She doesn’t finish.', 2.6)},
    S('iris', 'Анна. Её зовут Анна.', 'Anna. Her name is Anna.', shot='iris_bed'),
    N('Младенец кричит. Агнес закрывает Еве глаза.', 'The baby screams. Agnes closes Eva’s eyes.', 3.0, shot='agnes_bed'),
    {'go': 'c3_s4'}]}

# ---------- Сцена 4. «Озеро не отпускает» ----------
SC['c3_s4'] = {'save': True, 'time': '01:10', 'beats': [
    {'fx': 'night_stove', 'shot': 'stove_two', **N('Дом затих. Агнес курит трубку у печи. Ной садится рядом.', 'The house is quiet. Agnes smokes her pipe by the stove. Noah sits down beside her.', 3.2)},
    S('agnes', 'Озеро своих не отпускает, милок. Кто утоп — тот при нём и остаётся. А злость его — в том, кто видел.', 'The lake doesn’t let its own go, dear. Whoever drowns stays with it. And its anger lives in whoever saw.', shot='agnes_stove'),
    S('agnes', 'Мать твоя в тебе сидит. Она Айрис и уберегла.', 'Your mother lives in you. She’s the one who kept Iris safe.', shot='agnes_stove'),
    S('noah', 'Врач говорил — это болезнь.', 'The doctor said it’s an illness.', shot='noah_stove'),
    S('agnes', 'Врач… Она ж ко мне приезжала. За неделю до того. Говорит: мам, уйду я от него. А я ей: сходи к доктору, он человек грамотный, поможет.', 'The doctor… She came to me, you know. A week before. Said: Mom, I’m leaving him. And I told her: go see the doctor, he’s an educated man, he’ll help.', shot='agnes_stove'),
    {'fx': 'pipe', 'shot': 'agnes_stove'},
    S('agnes', 'Сама её к нему и послала.', 'I sent her to him myself.', shot='agnes_stove'),
    {'if': {'trust': '>=3'}, 'else': 'c3_s4_belief'},
    {'go': 'c3_s4_diary'}]}

SC['c3_s4_diary'] = {'beats': [
    {'set': {'agnes_knows_hale': 1}},
    {'fx': 'diary_agnes', 'shot': 'iris_house', **N('Айрис достаёт мамин дневник и показывает бабушке последнюю страницу.', 'Iris takes out Mom’s diary and shows Grandma the last page.', 3.0)},
    S('iris', '«Саймон обещал. В субботу ночью он нас увезёт. Ной уже собрал рюкзак».', '“Simon promised. Saturday night he’ll take us away. Noah has already packed his backpack.”', shot='diary_page'),
    N('Агнес читает долго. Про ту ночь знал только доктор.', 'Agnes reads for a long time. Only the doctor knew about that night.', 3.0, shot='agnes_stove'),
    {'go': 'c3_s4_belief'}]}

SC['c3_s4_belief'] = {'beats': [
    C(8, [O('«Это болезнь. Мне надо лечиться»', '“It’s an illness. I need treatment”', 'c3_s4_doctor'),
          O('«Значит… мама со мной?»', '“So… Mom is with me?”', 'c3_s4_healer'),
          O('«Я не знаю»', '“I don’t know”', 'c3_s4_unsure')], shot='noah_stove', silent='c3_s4_unsure')]}

SC['c3_s4_doctor'] = {'beats': [
    {'set': {'belief_doctor': 1}},
    S('noah', 'Это болезнь. Мне надо лечиться.', 'It’s an illness. I need treatment.', shot='noah_stove'),
    N('Агнес сжимает губы.', 'Agnes presses her lips together.', 2.2, shot='agnes_stove'),
    {'go': 'c3_s4_pills'}]}

SC['c3_s4_healer'] = {'beats': [
    {'set': {'belief_healer': 1}},
    S('noah', 'Значит… мама со мной?', 'So… Mom is with me?', shot='noah_stove'),
    {'fx': 'agnes_hand', 'shot': 'stove_two', **N('Агнес берёт его руку в свои.', 'Agnes takes his hand in both of hers.', 2.6)},
    {'go': 'c3_s4_pills'}]}

SC['c3_s4_unsure'] = {'beats': [
    {'set': {'belief_none': 1}},
    S('noah', 'Я не знаю.', 'I don’t know.', shot='noah_stove'),
    S('agnes', 'Узнаешь. У озера все узнают.', 'You’ll find out. Everyone does, at the lake.', shot='agnes_stove'),
    {'go': 'c3_s4_pills'}]}

SC['c3_s4_pills'] = {'beats': [
    {'if': {'pills_flushed': '==0'}, 'else': 'c3_s4_brew'},
    S('agnes', 'Дай мне его отраву.', 'Give me his poison.', shot='agnes_stove'),
    {'fx': 'pills3', 'shot': 'pill_label', **N('Пузырёк Хейла. «Ной. Д-р С. Хейл. 1 таб. при головной боли».', 'Hale’s bottle. “Noah. Dr. S. Hale. 1 tab. for headaches.”', 2.8)},
    C(6, [O('Отдать', 'Give them to her', 'c3_s4_give'),
          O('Не отдавать и принять таблетку на ночь', 'Keep them and take a pill for the night', 'c3_s4_take'),
          O('Не отдавать и не пить ничего', 'Keep them and take nothing', 'c3_s4_keep')], shot='noah_stove', silent='c3_s4_keep')]}

SC['c3_s4_give'] = {'beats': [
    {'set': {'pills_burned': 1, 'brew': 1, 'night_lapse': 1}},
    {'fx': 'burn_pills', 'shot': 'stove_fire', **N('Агнес бросает пузырёк в печь. Пластик съёживается в огне.', 'Agnes throws the bottle into the stove. The plastic shrivels in the fire.', 3.0)},
    S('agnes', 'Пей. Спать будешь крепко.', 'Drink. You’ll sleep soundly.', shot='agnes_stove'),
    {'fx': 'drink_brew', 'shot': 'noah_stove', **N('Отвар горький, пахнет землёй.', 'The brew is bitter. It smells of earth.', 2.4)},
    {'go': 'c3_s5'}]}

SC['c3_s4_take'] = {'beats': [
    {'set': {'pills': 1, 'pills3': 1}},
    {'fx': 'take_pill', 'shot': 'noah_stove', **N('Ной глотает таблетку. Гул в голове стихает.', 'Noah swallows a pill. The hum in his head fades.', 2.6)},
    {'fx': 'agnes_away', 'shot': 'agnes_stove'},
    S('agnes', 'Ты так похож на него сейчас. На отца.', 'You look so much like him right now. Like your father.', shot='agnes_stove'),
    {'go': 'c3_s5'}]}

SC['c3_s4_keep'] = {'beats': [
    {'set': {'pills_kept': 1}},
    S('agnes', 'Упрямый. Как она.', 'Stubborn. Like her.', shot='agnes_stove'),
    {'set': {'night_lapse': 1}, 'if': {'shadow': '>=1'}},
    {'go': 'c3_s5'}]}

SC['c3_s4_brew'] = {'beats': [
    {'fx': 'brew_cup', 'shot': 'stove_two', **N('Таблеток нет. Агнес просто ставит перед ним кружку отвара.', 'There are no pills. Agnes just sets a mug of her brew in front of him.', 3.0)},
    C(6, [O('Выпить', 'Drink it', 'c3_s4_brew_yes'),
          O('Не пить', 'Don’t drink', 'c3_s4_brew_no')], shot='noah_stove', silent='c3_s4_brew_no')]}

SC['c3_s4_brew_yes'] = {'beats': [
    {'set': {'brew': 1, 'night_lapse': 1}},
    {'fx': 'drink_brew', 'shot': 'noah_stove', **N('Отвар горький, пахнет землёй.', 'The brew is bitter. It smells of earth.', 2.4)},
    S('agnes', 'Пей. Спать будешь крепко.', 'Drink. You’ll sleep soundly.', shot='agnes_stove'),
    {'go': 'c3_s5'}]}

SC['c3_s4_brew_no'] = {'beats': [
    S('agnes', 'Упрямый. Как она.', 'Stubborn. Like her.', shot='agnes_stove'),
    {'set': {'night_lapse': 1}, 'if': {'shadow': '>=1'}},
    {'go': 'c3_s5'}]}

# ---------- Сцена 5. «Второй Ной» ----------
SC['c3_s5'] = {'time': '03:40', 'beats': [
    {'fx': 'fade_out', 'dur': 1.2},
    {'if': {'night_lapse': '>0'}, 'else': 'c3_s5_song'},
    {'go': 'c3_s5_lapse'}]}

SC['c3_s5_lapse'] = {'beats': [
    {'fx': 'second_noah'},
    {'fx': 'fade_in', 'shot': 'axe_back', **N('Ной открывает глаза. Он стоит босиком в снегу у последнего дома — лицом к лесу, к озеру. В руке — топор из поленницы.', 'Noah opens his eyes. He is standing barefoot in the snow by the last house — facing the forest, the lake. In his hand — an axe from the woodpile.', 4.0)},
    {'fx': 'iris_behind', 'shot': 'axe_iris', **N('Сзади хрустит снег. Айрис — в одной кофте.', 'Snow crunches behind him. Iris — in nothing but a sweater.', 2.8)},
    S('iris', 'Ной… Это я. Это Айрис.', 'Noah… It’s me. It’s Iris.', shot='axe_iris'),
    {'fx': 'noah_turn', 'shot': 'axe_face'},
    S('anna', 'Не ходи к озеру одна.', 'Don’t go to the lake alone.', shot='axe_face', lips='noah'),
    {'fx': 'axe_drop', 'shot': 'axe_snow', **N('Он приходит в себя. Топор падает в снег.', 'He comes to. The axe drops into the snow.', 2.8)},
    S('agnes', 'Это была она.', 'That was her.', shot='agnes', **{'if': {'belief_doctor': '==0'}}),
    {'if': {'danny': '>0'}, 'else': 'c3_s6'},
    {'fx': 'danny_at_window', 'shot': 'danny_window', **N('Дэнни видел всё в окно.', 'Danny saw it all through the window.', 2.4)},
    {'if': {'danny_fear': '>0'}, 'else': 'c3_s5_shadow2'},
    {'go': 'c3_s5_danny'}]}

SC['c3_s5_shadow2'] = {'beats': [
    {'if': {'shadow': '>=2'}, 'else': 'c3_s6'},
    {'go': 'c3_s5_danny'}]}

SC['c3_s5_danny'] = {'beats': [
    {'fx': 'danny_door', 'shot': 'danny_door', **N('Перед рассветом у двери — Дэнни. В валенках, с рацией.', 'Before dawn, Danny is at the door. In felt boots, with his walkie-talkie.', 3.0)},
    C(6, [O('«Подожди. Я тебе обещал — и нарушил. Прости»', '“Wait. I made you a promise — and broke it. I’m sorry”', 'c3_s5_sorry'),
          O('«Иди. Я тебя не держу»', '“Go. I won’t stop you”', 'c3_s5_go'),
          O('Перегородить дверь', 'Block the door', 'c3_s5_block')], shot='noah_house', silent='c3_s5_go')]}

SC['c3_s5_sorry'] = {'beats': [
    S('noah', 'Подожди. Я тебе обещал — и нарушил. Прости.', 'Wait. I made you a promise — and broke it. I’m sorry.', shot='noah_house'),
    {'if': {'danny_fear': '==0'}, 'else': 'c3_s5_go_lie'},
    {'fx': 'danny_stays', 'shot': 'danny_door', **N('Дэнни долго смотрит на него. Потом снимает валенки.', 'Danny looks at him for a long time. Then he takes off the felt boots.', 3.0)},
    {'go': 'c3_s6'}]}

SC['c3_s5_go_lie'] = {'beats': [
    N('«Ты и тогда так говорил». Дэнни выходит в буран.', '“You said that last time too.” Danny walks out into the storm.', 3.0, shot='danny_door'),
    {'go': 'c3_s5_lost'}]}

SC['c3_s5_go'] = {'beats': [
    S('noah', 'Иди. Я тебя не держу.', 'Go. I won’t stop you.', shot='noah_house'),
    N('Дэнни выходит в буран.', 'Danny walks out into the storm.', 2.4, shot='danny_door'),
    {'go': 'c3_s5_lost'}]}

SC['c3_s5_block'] = {'beats': [
    {'fx': 'block_door', 'shot': 'danny_door', **N('Ной встаёт в дверях. Дэнни пятится — и через окно, в снег.', 'Noah stands in the doorway. Danny backs away — and out the window, into the snow.', 3.0)},
    {'go': 'c3_s5_lost'}]}

SC['c3_s5_lost'] = {'beats': [
    {'set': {'danny_lost': 1}},
    {'fx': 'danny_tracks', 'shot': 'tracks', **N('Следы в сторону трассы заметает буран.', 'The blizzard fills in the tracks heading toward the highway.', 3.0)},
    {'go': 'c3_s6'}]}

SC['c3_s5_song'] = {'beats': [
    {'fx': 'baby_night'},
    {'fx': 'fade_in', 'shot': 'house_night', **N('Ной просыпается от плача младенца.', 'Noah wakes to the baby crying.', 2.6, **{'if': {'baby': '>0'}})},
    {'fx': 'fade_in', 'shot': 'house_night', **N('Ной просыпается среди ночи. Тишина. Печь потрескивает.', 'Noah wakes in the night. Silence. The stove crackles.', 2.8, **{'if': {'baby': '==0'}})},
    {'if': {'danny': '>0'}, 'else': 'c3_s5_song2'},
    {'fx': 'radio_song', 'shot': 'danny_window', **N('Дэнни сидит у окна с рацией. Рация трещит — и сквозь помехи тихо поёт женский голос. Та самая колыбельная.', 'Danny sits by the window with his walkie-talkie. It crackles — and through the static a woman softly sings. That same lullaby.', 4.0)},
    N('Дэнни смотрит на Ноя огромными глазами.', 'Danny stares at Noah with enormous eyes.', 2.4, shot='danny_window'),
    {'go': 'c3_s5_song3'}]}

SC['c3_s5_song2'] = {'beats': [
    {'fx': 'radio_song', 'shot': 'house_night', **N('Из старого приёмника на полке, сквозь помехи, тихо поёт женский голос. Та самая колыбельная.', 'From the old radio on the shelf, through the static, a woman softly sings. That same lullaby.', 4.0)},
    {'go': 'c3_s5_song3'}]}

SC['c3_s5_song3'] = {'beats': [
    {'set': {'heard_song': 1, 'memory': 1}},   # колыбельная — кусок памяти (DESIGN 7.8)
    S('agnes', 'Это Аня поёт.', 'That’s Annie singing.', shot='agnes_sleep'),
    {'go': 'c3_s6'}]}

# ---------- Сцена 6. «Свет в конце улицы» ----------
SC['c3_s6'] = {'save': True, 'time': '06:20', 'beats': [
    {'fx': 'dawn_street'},
    {'fx': 'fade_in', 'shot': 'street_end', **N('Под утро буран стихает. В конце улицы загораются фары — чёрный седан. Гаснут.', 'Toward morning the storm dies down. At the end of the street, headlights come on — the black sedan. They go out.', 3.6)},
    S('agnes', 'Это он. Он и тогда приезжал. После Ани.', 'It’s him. He came back then too. After Annie.', shot='agnes_window'),
    S('iris', 'До дачи двадцать километров. Лесом.', 'It’s twenty kilometers to the cabin. Through the woods.', shot='iris_house'),
    C(8, [O('Выйти к машине — с топором', 'Go out to the car — with the axe', 'c3_s6_axe'),
          O('Спрятать всех в подпол и ждать', 'Hide everyone in the cellar and wait', 'c3_s6_cellar'),
          O('Уйти задами, огородами — к соседке', 'Slip out the back, through the gardens — to the neighbor’s', 'c3_s6_neighbor')],
      shot='noah_house', silent='c3_s6_cellar')]}

SC['c3_s6_axe'] = {'beats': [
    {'set': {'shadow': 1, 'hale_brave': 1}},
    {'fx': 'axe_walk', 'shot': 'street_walk', **N('Ной идёт по пустой улице к седану. Топор в руке.', 'Noah walks down the empty street toward the sedan. The axe in his hand.', 3.0)},
    {'fx': 'sedan_reverse', 'shot': 'street_end', **N('Седан уезжает задом, не включая фар.', 'The sedan backs away without turning on its lights.', 3.0)},
    {'go': 'c3_end'}]}

SC['c3_s6_cellar'] = {'beats': [
    {'set': {'hale_knows': 1}},
    {'fx': 'cellar', 'shot': 'cellar', **N('Подпол. Темнота и запах картошки. Тишина — час.', 'The cellar. Darkness and the smell of potatoes. Silence — for an hour.', 3.0)},
    {'fx': 'knock', 'shot': 'cellar', **N('Потом стук в дверь — три раза, вежливо. Никто не открывает. Шаги уходят.', 'Then a knock at the door — three times, politely. No one answers. Footsteps walk away.', 3.6)},
    {'go': 'c3_end'}]}

SC['c3_s6_neighbor'] = {'beats': [
    {'set': {'hale_lost': 1}},
    {'fx': 'neighbor', 'shot': 'gardens', **N('Агнес собирает узел трав и ведёт всех задами, огородами — к соседке на краю деревни.', 'Agnes bundles up her herbs and leads everyone out the back, through the gardens — to the neighbor’s at the edge of the village.', 3.6)},
    N('До света у Хейла нет их следа. Но лесом ночью, с младенцем, до дачи не дойти.', 'Until daylight, Hale has lost their trail. But at night, through the woods, with a baby — they’d never reach the cabin.', 3.6, shot='gardens'),
    {'go': 'c3_end'}]}

SC['c3_end'] = {'beats': [
    {'fx': 'fade_out', 'dur': 1.4},
    {'end': 3}]}

DATA = {
    'chapter': 3, 'start': 'c3_recap', 'ready': True,    # True — когда постановка главы готова (chapter3.gd)
    'title': {'ru': 'ГЛАВА 3 · ДЕРЕВНЯ', 'en': 'CHAPTER 3 · THE VILLAGE'},
    'names': {'agnes': {'ru': 'АГНЕС', 'en': 'AGNES'},
              'anna': {'ru': 'НОЙ — ЧУЖИМ ГОЛОСОМ', 'en': 'NOAH — NOT HIS OWN VOICE'}},
    'flags': {k: 0 for k in ['eva_found', 'eva_cold', 'walked', 'iris_alone', 'agnes_warm', 'agnes_knows', 'birth_time', 'light',
                             'went_noah', 'lapse_shop', 'hale_here', 'hard', 'eva_alive', 'eva_dead', 'baby', 'agnes_knows_hale',
                             'belief_doctor', 'belief_healer', 'belief_none', 'pills_burned', 'brew', 'pills3', 'pills_kept',
                             'night_lapse', 'danny_lost', 'heard_song', 'hale_brave', 'hale_knows', 'hale_lost']},
    'map': [
        {'key': 'eva', 'ru': 'Ева', 'en': 'Eva', 'opts': [
            {'id': 'alive', 'ru': 'жива', 'en': 'alive', 'if': {'eva_alive': '>0'}},
            {'id': 'dead', 'ru': 'умерла', 'en': 'died', 'if': {'eva_dead': '>0'}},
            {'id': 'none', 'ru': 'не с ними', 'en': 'not with them', 'if': {}}]},
        {'key': 'gas', 'ru': 'За бензином', 'en': 'The gas run', 'opts': [
            {'id': 'noah', 'ru': 'ходил Ной', 'en': 'Noah went', 'if': {'went_noah': '>0'}},
            {'id': 'zoe', 'ru': 'ходила Зоя', 'en': 'Zoe went', 'if': {'hale_here': '>0'}},
            {'id': 'none', 'ru': 'при свечах', 'en': 'by candlelight', 'if': {}}]},
        {'key': 'belief', 'ru': 'Во что верить', 'en': 'What to believe', 'opts': [
            {'id': 'doctor', 'ru': 'болезнь', 'en': 'an illness', 'if': {'belief_doctor': '>0'}},
            {'id': 'healer', 'ru': 'мама с ним', 'en': 'Mom is with him', 'if': {'belief_healer': '>0'}},
            {'id': 'none', 'ru': 'не решил', 'en': 'undecided', 'if': {}}]},
        {'key': 'pills3', 'ru': 'Таблетки №3', 'en': 'Pills #3', 'opts': [
            {'id': 'burned', 'ru': 'сожжены', 'en': 'burned', 'if': {'pills_burned': '>0'}},
            {'id': 'taken', 'ru': 'принял', 'en': 'took one', 'if': {'pills3': '>0'}},
            {'id': 'kept', 'ru': 'не тронуты', 'en': 'untouched', 'if': {}}]},
        {'key': 'night', 'ru': 'Ночь', 'en': 'The night', 'opts': [
            {'id': 'axe', 'ru': 'топор и чужой голос', 'en': 'the axe and the voice', 'if': {'night_lapse': '>0'}},
            {'id': 'song', 'ru': 'колыбельная в рации', 'en': 'the lullaby on the radio', 'if': {}}]},
        {'key': 'danny', 'ru': 'Дэнни', 'en': 'Danny', 'opts': [
            {'id': 'lost', 'ru': 'ушёл в буран', 'en': 'lost in the storm', 'if': {'danny_lost': '>0'}},
            {'id': 'with', 'ru': 'с ними', 'en': 'with them', 'if': {'danny': '>0'}},
            {'id': 'none', 'ru': 'остался на заправке', 'en': 'left at the station', 'if': {}}]},
        {'key': 'hale', 'ru': 'Свет фар', 'en': 'The headlights', 'opts': [
            {'id': 'axe', 'ru': 'вышел с топором', 'en': 'went out with the axe', 'if': {'hale_brave': '>0'}},
            {'id': 'cellar', 'ru': 'подпол', 'en': 'the cellar', 'if': {'hale_knows': '>0'}},
            {'id': 'neighbor', 'ru': 'ушли к соседке', 'en': 'slipped away', 'if': {}}]},
    ],
    'scenes': SC,
}

if __name__ == '__main__':
    json.dump(DATA, open(OUT, 'w'), ensure_ascii=False, indent=1)
    open(OUT, 'a').write('\n')
    says = sum(1 for s in SC.values() for b in s['beats'] if 'say' in b)
    print('ch3.json:', len(SC), 'сцен,', says, 'реплик')
