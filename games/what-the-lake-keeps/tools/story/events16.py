#!/usr/bin/env python3
"""WHAT THE LAKE KEEPS — 16 коротких событий-откликов на выборы игрока (план до выпуска: «13–15 ноя, 16 коротких
событий-откликов — постановка в сценах»; решение редактора 7 ноя, п. 6). Список и выбор — docs/DECISION_LOG.md
(D-41), таблица — docs/FLAG_RESPONSES_DRAFT.md, раздел «16 событий-откликов».

Каждое событие — 1–4 шага в уже существующей сцене, по условию на флаг выбора: реплики с постановкой (план камеры
этой же сцены, кто на кого смотрит, жест, выражение лица). Новых развилок нет: ни одного "go", "else", "choice",
сюжетные флаги и концовки не меняются. Если у выбора не было своего флага (варианты «только сразу» в
CHOICE_CONSEQUENCES.md), ставится журнальный флаг (объявлен в "flags" главы: старые сохранения грузятся с 0 —
событие просто не покажется).

Шаги помечены "ev": "<событие>"; шаги с текстом — ещё "resp": "ev_<событие>[:<флаг исхода>]" (game.gd пишет в лог «отклик …»,
по нему tools/story_tests.py сверяет с симулятором, где событие сработало). Повторный запуск сначала убирает старые
вставки — ничего не дублируется. Новые реплики — только текстом (решение владельца 8 ноя, голоса нет).
Порядок после пересборки данных: chN.py / chN_walk.py → gestures.py → flag_responses.py → script_edits.py →
script_v2.py → events16.py
    python3 tools/story/events16.py [--check]
"""
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
DATA = os.path.join(ROOT, 'godot/data')


def SAY(who, ru, en, shot=None, look=None, gesture=None, expr=None, thought=False):
    b = {'say': who, 'ru': ru, 'en': en}
    if thought:
        b['thought'] = True
    if shot:
        b['shot'] = shot
    if look:
        b['look'] = {'who': look[0], 'at': look[1]}
    if gesture:
        b['gesture'] = gesture
    if expr:
        b['expr'] = {'who': expr[0], 'set': expr[1]}
    return b


def G(who, do, dur=2.2, **kw):
    return dict({'who': who, 'do': do, 'dur': dur}, **kw)


def CLEAR(*who, expr=None):
    """Взгляд — снова свободный (по разговору), лицо — как было: чтобы вставка не тянулась в следующие шаги сцены."""
    b = {}
    if who:
        b['look'] = {'who': who[0], 'at': None}
    if expr:
        b['expr'] = {'who': expr, 'set': 'none'}
    return b


# Флаги, которые вводит этот файл (глава → флаги): журнальные отметки вариантов «только сразу».
NEW_FLAGS = {1: ['danny_refused', 'said_ok'], 2: ['kitchen_exit'],
             4: ['enough_both', 'book_left', 'hut_shut', 'hut_creak', 'hut_still', 'danny_unanswered'], 5: ['danny_try']}

# Где ставится флаг: (глава, сцена, якорь, флаг, условие). Якорь: '^' — начало сцены, 'qte' — сразу после действия
# (шаг выполняется, только если действие удалось: при провале игра уходит в "fail").
SETS = [
    (1, 's6_b', '^', 'danny_refused', None),               # выбор №6 «Мы не можем его взять» (6.2)
    (1, 's7_a', '^', 'said_ok', None),                     # выбор №9 «Всё будет хорошо» (9.1)
    (2, 'c2_s5_go_away', '^', 'kitchen_exit', None),       # выбор №20 «Уехать сейчас, через задний двор» (20.1)
    (4, 'c4_s2_enough', '^', 'enough_both', None),         # выбор №31 «Хватит. Обе» (31.3)
    (4, 'c4_s3_leave', '^', 'book_left', None),            # выбор №32 «Не трогать» / молчание (32.2, 32.м)
    (4, 'c4_s4_quiet', '^', 'danny_unanswered', None),     # выбор №34 молчание (34.м)
    (4, 'c4_s6_door', 'qte', 'hut_shut', None),            # выбор №38 «Закрыть дверь» — удалось (38.1)
    (4, 'c4_s6_creak', '^', 'hut_creak', None),            # выбор №38 «Закрыть дверь» — скрипнула (38.1, провал)
    (4, 'c4_s6_still', '^', 'hut_still', None),            # выбор №38 «Замереть» / молчание (38.2, 38.м)
    (5, 'c5_t_danny_try', '^', 'danny_try', None),         # выбор №43 «Не знаю. Но я постараюсь» (43.2)
]

# События: (id, глава, сцена, якорь, 'after'|'before', шаги). Якорь — начало русского текста шага или ('fx', имя).
# У каждого шага с текстом своё "if" (как у соседних реплик этого героя: кто жив и рядом).
EVENTS = [
    # 1. Глава 2, буран: Дэнни помнит «Мы не можем его взять» (выбор №6)
    ('danny_refused', 2, 'c2_storm_wait', 'Мотор не глушу. Иначе замёрзнем.', 'after', [
        SAY('danny', 'Ной. А на заправке ты не хотел меня брать.', 'Noah. Back at the gas station you didn’t want to take me.',
            shot='back', look=('danny', 'noah')),
        SAY('noah', 'Не хотел, чтобы с нами тебе стало хуже.', 'I didn’t want things to get worse for you. With us.',
            shot='noah', look=('iris', 'danny')),
        SAY('danny', 'С вами не хуже. С вами тепло.', 'It’s not worse with you. It’s warm with you.',
            shot='back', gesture=G('danny', 'nod')),
        CLEAR('iris'),
    ], {'danny_refused': '>0', 'danny': '>0'}),
    # 2. Глава 2, кафе: дальнобойщик про пост — Ной объехал его лесом (`detour`, выбор №14 «Развернуться до поста»)
    ('detour', 2, 'c2_s4', 'Нина, мне добавки.', 'after', [
        SAY('trucker', 'С трассы, молодёжь? Там на посту с утра всех трясут.',
            'Off the highway, kids? That checkpoint’s been shaking everybody down since morning.', shot='cafe_truckers'),
        SAY('noah', 'Мы лесом.', 'We took the woods road.', shot='noah_cafe'),
        SAY('trucker', 'Лесом? В такую погоду? Отчаянные.', 'Through the woods? In this weather? You’re nuts.', shot='cafe_truckers'),
        SAY('iris', '«Отчаянные». Это он про тебя.', '“Nuts.” He means you.', shot='cafe_iris', look=('iris', 'noah')),
        CLEAR('iris'),
    ], {'detour': '>0'}),
    # 3. Глава 2, конец бурана: фары позади — ушли через кухню, а он всё равно нашёл (выбор №20)
    ('kitchen_exit', 2, 'c2_storm_end', 'В зеркале, далеко позади', 'after', [
        SAY('iris', 'Мы же через кухню ушли. Он всё равно нас нашёл.', 'We went out through the kitchen. He found us anyway.',
            shot='iris', look=('iris', 'noah'), expr=('iris', 'fear')),
        SAY('noah', 'Не оглядывайся.', 'Don’t look back.', shot='noah'),
        SAY('iris', 'Я не оглядываюсь. Я в зеркало.', 'I’m not. I’m looking in the mirror.', shot='iris', look=('iris', 'window')),
        CLEAR('iris', expr='iris'),
    ], {'kitchen_exit': '>0'}),
    # 4. Глава 3, после родов: «Всё будет хорошо» из главы 1 сбылось (выбор №9)
    ('said_ok', 3, 'c3_s3_alive', 'Живы. Обе.', 'after', [
        SAY('iris', 'Ты в машине сказал: «всё будет хорошо». Помнишь?', 'In the car you said, “It’ll be okay.” Remember?',
            shot='iris_bed', look=('iris', 'noah')),
        SAY('noah', 'Я тогда сам в это не верил.', 'I didn’t believe it myself.', shot='noah_house'),
        SAY('iris', 'Знаю. А сегодня — сбылось.', 'I know. But tonight it came true.', shot='iris_bed', expr=('iris', 'smile')),
        CLEAR('iris', expr='iris'),
    ], {'said_ok': '>0'}),
    # 5. Глава 3, дом Агнес: Зоя помнит, как заговорила полицейского на посту (`zoe_talked`, выбор №14 «Дать говорить Зое»)
    ('zoe_talked', 3, 'c3_s2', 'По радио нас ищут. Машину надо спрятать.', 'after', [
        SAY('zoe', 'Если полиция заедет — говорю я. На посту же получилось.', 'If the cops come by, I’ll do the talking. Worked at the checkpoint.',
            shot='zoe_house', look=('zoe', 'noah')),
        SAY('iris', '«Подрался за меня». Получилось у неё.', '“He got in a fight over me.” Sure. Worked.', shot='iris_out', look=('iris', 'zoe'),
            gesture=G('iris', 'shake', 1.2)),
        CLEAR('zoe'),
        CLEAR('iris'),
    ], {'zoe_talked': '>0', 'storm_radio': '>0', 'police': '>0', 'zoe': '>0', 'zoe_gone': '==0'}),
    # 6. Глава 4, остановка: Зоя и Айрис помнят «Хватит. Обе» (выбор №31)
    ('enough_both', 4, 'c4_s3', 'Всё, закипела. Стоим.', 'after', [
        SAY('zoe', 'Капитан, а ругаться уже можно? Или всё ещё «хватит»?', 'Captain, are we allowed to argue now? Or is it still “enough”?',
            shot='zoe_van', look=('zoe', 'noah')),
        SAY('noah', 'Ругайтесь. Только с мотором.', 'Go ahead. Argue with the engine.', shot='noah_van'),
        SAY('iris', 'Я за. Он первый начал.', 'Fine by me. It started it.', shot='iris_van', look=('iris', 'zoe'), expr=('iris', 'smile')),
        CLEAR('zoe', expr='iris'),
        CLEAR('iris'),
    ], {'enough_both': '>0', 'zoe': '>0', 'zoe_gone': '==0'}),
    # 7. Глава 4, снова в дороге: Ной не открыл блокнот спящей Айрис (выбор №32)
    ('book_left', 4, 'c4_s4_road', 'Шланг держит. Пока держит.', 'after', [
        SAY('iris', 'Ты не открыл блокнот. Я на обложку волосок клала.', 'You didn’t open my notebook. I left a hair on the cover.',
            shot='iris_van', look=('iris', 'noah')),
        SAY('noah', 'Шпионка.', 'Spy.', shot='noah_van'),
        SAY('iris', 'Спасибо, Ной.', 'Thanks, Noah.', shot='iris_van', expr=('iris', 'smile')),
        CLEAR('iris', expr='iris'),
    ], {'book_left': '>0'}),
    # 8. Глава 4, сосна: Хейл стучал в подпол Агнес три раза (`hale_knows`, выбор №29 «Спрятать всех в подпол»)
    ('hale_knows', 4, 'c4_s5', 'Как голова? Ты плохо спишь', 'after', [
        SAY('hale', 'Утром я к вам заходил. Постучал три раза. Вы не открыли — я не в обиде.',
            'I stopped by this morning. Knocked three times. You didn’t open — no hard feelings.',
            shot='hale', gesture=G('hale', 'nod'), look=('iris', 'hale')),
        SAY('noah', 'Три стука. Вежливо. Подпол, темнота — а он за дверью.',
            'Three knocks. Polite. The cellar, the dark — and him outside the door.', thought=True, shot='noah_pine'),
    ], {'hale_knows': '>0'}),
    # 9. Глава 4, рассвет у озера: дверь в избушке (выбор №38 — все исходы)
    ('hut_door', 4, 'c4_s7', ('fx', 'ice_done'), 'after', [
        dict(SAY('iris', 'Там, в избушке, ты дверь закрыл — ни звука. Я думала, петли заорут.',
                 'Back in the hut you shut that door without a sound. I thought the hinges would scream.',
                 shot='iris_lake', look=('iris', 'noah')), **{'if': {'hut_shut': '>0'}}),
        dict(SAY('noah', 'Её надо приподнять за ручку. Тогда петли молчат.', 'You lift it by the handle. Then the hinges keep quiet.',
                 shot='noah_woods'), **{'if': {'hut_shut': '>0'}}),
        dict(SAY('iris', 'Механик.', 'Mechanic.', shot='iris_lake', expr=('iris', 'smile')), **{'if': {'hut_shut': '>0'}}),
        dict(CLEAR('iris', expr='iris'), **{'if': {'hut_shut': '>0'}}),
        dict(SAY('iris', 'Когда в избушке скрипнула дверь, я решила — всё. Конец.',
                 'When that door creaked in the hut, I thought that was it. The end.',
                 shot='iris_lake', look=('iris', 'noah'), expr=('iris', 'sad')), **{'if': {'hut_creak': '>0'}}),
        dict(SAY('noah', 'Я тоже.', 'So did I.', shot='noah_woods'), **{'if': {'hut_creak': '>0'}}),
        dict(CLEAR('iris', expr='iris'), **{'if': {'hut_creak': '>0'}}),
        dict(SAY('iris', 'В избушке свет был у самых твоих ботинок. А ты даже не шелохнулся.',
                 'In the hut the light was right at your boots. And you didn’t even flinch.',
                 shot='iris_lake', look=('iris', 'noah')), **{'if': {'hut_still': '>0'}}),
        dict(SAY('noah', 'Не мог. Ноги не слушались.', 'I couldn’t. My legs wouldn’t move.', shot='noah_woods'),
             **{'if': {'hut_still': '>0'}}),
        dict(CLEAR('iris'), **{'if': {'hut_still': '>0'}}),
    ]),
    # 10. Глава 5, дача: Айрис вспоминает сосну — остальные три исхода выбора №36 (дневник и «замер» — в откликах)
    ('pine', 5, 'c5_s1', 'Скажешь, когда всё кончится. С процентами.', 'after', [
        dict(SAY('iris', 'У сосны ты схватил монтировку раньше, чем я успела крикнуть.',
                 'At the pine you grabbed the crowbar before I could even yell.',
                 shot='iris_coat', look=('iris', 'noah')), **{'if': {'pine_attack': '>0', 'iris_fought': '==0'}}),
        dict(SAY('noah', 'Руки быстрее меня. Это и пугает.', 'My hands are faster than me. That’s what scares me.',
                 shot='noah_room', gesture=G('noah', 'look_hands', 2.6)), **{'if': {'pine_attack': '>0', 'iris_fought': '==0'}}),
        dict(CLEAR('iris'), **{'if': {'pine_attack': '>0', 'iris_fought': '==0'}}),
        dict(SAY('iris', 'В лесу ты меня так за руку тащил — чуть не оторвал.',
                 'In the woods you dragged me by the hand so hard you nearly pulled it off.',
                 shot='iris_coat', look=('iris', 'noah')), **{'if': {'pine_run': '>0'}}),
        dict(SAY('noah', 'Прости.', 'Sorry.', shot='noah_room'), **{'if': {'pine_run': '>0'}}),
        dict(SAY('iris', 'Не извиняйся. Если что — тащи и дальше.', 'Don’t apologize. If it comes to that, keep dragging me.',
                 shot='iris_coat', expr=('iris', 'smile')), **{'if': {'pine_run': '>0'}}),
        dict(CLEAR('iris', expr='iris'), **{'if': {'pine_run': '>0'}}),
        dict(SAY('iris', 'Ты спросил его про маму. Прямо в лицо. Я бы не смогла.',
                 'You asked him about Mom. Right to his face. I couldn’t have.',
                 shot='iris_coat', look=('iris', 'noah')), **{'if': {'pine_talk': '>0'}}),
        dict(SAY('noah', 'Он соврал.', 'He lied.', shot='noah_room'), **{'if': {'pine_talk': '>0'}}),
        dict(SAY('iris', 'Соврал. Но ты спросил.', 'He lied. But you asked.', shot='iris_coat'), **{'if': {'pine_talk': '>0'}}),
        dict(CLEAR('iris'), **{'if': {'pine_talk': '>0'}}),
    ]),
    # 11. Глава 5, дача: Дэнни помнит, что Ной промолчал на остановке (выбор №34, молчание)
    ('danny_unanswered', 5, 'c5_t_danny', 'Дэнни вцепился в моток верёвки.', 'after', [
        SAY('danny', 'На остановке я тебе всё рассказал. А ты промолчал.', 'At the bus stop I told you everything. And you didn’t say a thing.',
            shot='danny_room', look=('danny', 'noah')),
        SAY('noah', 'Я не знал, что на такое отвечают.', 'I didn’t know what you say to something like that.', shot='noah_room'),
        SAY('danny', 'Можно просто «спасибо».', 'You can just say “thanks.”', shot='danny_room'),
        SAY('noah', 'Спасибо, Дэнни.', 'Thanks, Danny.', shot='danny_room', gesture=G('danny', 'nod')),
        CLEAR('danny'),
    ], {'danny_unanswered': '>0'}),
    # 12. Глава 5, крыльцо: синяк Зои — Ной отбил её в лесу (`zoe_saved`, выбор №39)
    ('zoe_saved', 5, 'c5_t_zoe', 'Будешь? А, ты же не куришь.', 'after', [
        SAY('zoe', 'Глянь, синяк. Это с леса — когда ты меня у них отбил.', 'Look, a bruise. From the woods — when you got me away from them.',
            shot='zoe_porch', gesture=G('zoe', 'look_hands', 2.4)),
        SAY('noah', 'Прости.', 'Sorry.', shot='noah_porch'),
        SAY('zoe', 'Дурак. Это мой любимый синяк.', 'Idiot. It’s my favorite bruise.', shot='zoe_porch',
            look=('zoe', 'noah'), expr=('zoe', 'smile')),
        CLEAR('zoe', expr='zoe'),
    ], {'zoe_saved': '>0'}),
    # 13. Глава 5, дача: малышка заплакала в избушке — здесь можно (`baby_cried`, действие «Укачай» в гл. 4)
    ('baby_cried', 5, 'c5_s1', 'Тише, Анечка, тише.', 'after', [
        dict(SAY('eva', 'Хотя нет — плачь, маленькая. Тут можно. Тут никто не услышит.',
                 'Actually, no — go ahead and cry, little one. You can here. No one will hear.',
                 shot='dacha_room', expr=('eva', 'smile')), **{'if': {'eva_alive': '>0'}}),
        dict(CLEAR(expr='eva'), **{'if': {'eva_alive': '>0'}}),
    ], {'baby_cried': '>0', 'baby': '>0'}),
    # 14. Глава 5, дача: пузырёк остался в сугробе у Агнес (`pills_snow`, выбор №30 «Забросить в сугроб»)
    ('pills_snow', 5, 'c5_s1_pills2', '^', 'before', [
        SAY('noah', 'Карман пустой. Пузырёк так и лежит в сугробе у Агнес. Пусть лежит до весны.',
            'Empty pocket. The bottle’s still in the snowdrift at Agnes’s. Let it stay there till spring.',
            thought=True, shot='noah_room', gesture=G('noah', 'pockets', 2.8)),
    ], {'pills_snow': '>0'}),
    # 15. Глава 5, сарай: таблеток нет — Айрис сожгла последние (`iris_refused`, выбор №46)
    ('iris_refused', 5, 'c5_s2', 'Я тут, за спиной.', 'after', [
        SAY('noah', 'Таблеток нет — Айрис сожгла. Ничего не глушит. Только я и эта щель.',
            'No pills — Iris burned them. Nothing to dull it. Just me and this gap.', thought=True, shot='noah_gap',
            look=('iris', 'noah')),
        CLEAR('iris'),
    ], {'iris_refused': '>0'}),
    # 16. Глава 5, полынья: Дэнни с верёвкой — «постараюсь» (выбор №43)
    ('danny_try', 5, 'c5_s4_iris', 'Лови верёвку!', 'after', [
        SAY('danny', 'Ты сказал — постараешься! Вот и старайся!', 'You said you’d try! So try!', shot='hole',
            look=('danny', 'noah')),
    ], {'danny_try': '>0', 'danny_rope': '>0', 'sitter_danny': '==0'}),
]


def _ru(b):
    for k in ('note', 'text'):
        if isinstance(b.get(k), dict):
            return str(b[k].get('ru', ''))
    return str(b.get('ru', ''))


def strip(x):
    """Убрать прошлые вставки этого файла (шаги с "ev") — во всём дереве главы."""
    if isinstance(x, list):
        x[:] = [v for v in x if not (isinstance(v, dict) and 'ev' in v)]
        for v in x:
            strip(v)
    elif isinstance(x, dict):
        for v in x.values():
            strip(v)


def find(beats, anchor):
    if isinstance(anchor, tuple):
        hits = [k for k, b in enumerate(beats) if 'ev' not in b and b.get(anchor[0]) == anchor[1]]
    else:
        hits = [k for k, b in enumerate(beats) if 'ev' not in b and _ru(b).startswith(anchor)]
    return hits


def apply(j, ch):
    n = 0
    for k in NEW_FLAGS.get(ch, []):
        j['flags'].setdefault(k, 0)
    for c, sid, anchor, flag, cond in SETS:
        if c != ch:
            continue
        beats = j['scenes'][sid]['beats']
        b = {'set': {flag: 1}, 'ev': flag}
        if cond:
            b['if'] = cond
        if anchor == '^':
            i = 0
        elif anchor == 'qte':
            q = [k for k, x in enumerate(beats) if 'qte' in x]
            if len(q) != 1:
                raise SystemExit('ch%d %s: действий %d, нужно одно' % (ch, sid, len(q)))
            i = q[0] + 1
        beats[i:i] = [b]
        n += 1
    for ev in EVENTS:
        eid, c, sid, anchor, pos, steps = ev[:6]
        cond = ev[6] if len(ev) > 6 else None
        if c != ch:
            continue
        beats = j['scenes'][sid]['beats']
        new = []
        for s in steps:
            s = dict(s, ev=eid)
            if cond:
                s['if'] = dict(cond, **s.get('if', {}))
            if 'say' in s:   # у событий с вариантами (дверь в избушке, сосна) — свой отклик на каждый исход
                s['resp'] = 'ev_' + eid + ('' if cond or 'if' not in s else ':' + next(iter(s['if'])))
            new.append(s)
        if anchor == '^':
            i = 0
        else:
            hits = find(beats, anchor)
            if not hits:
                raise SystemExit('ch%d %s: нет шага «%s»' % (ch, sid, anchor))
            i = hits[0] + (1 if pos == 'after' else 0)
        beats[i:i] = new
        n += len(new)
    return n


def main():
    check = '--check' in sys.argv
    total = 0
    for ch in range(1, 6):
        path = os.path.join(DATA, 'ch%d.json' % ch)
        raw = open(path, encoding='utf-8').read()
        j = json.loads(raw)
        strip(j)
        total += apply(j, ch)
        s = json.dumps(j, ensure_ascii=False, indent=1) + '\n'
        if check:
            if s != raw:
                print('ch%d: данные не совпадают с events16.py (запустить без --check)' % ch)
                return 1
        else:
            open(path, 'w', encoding='utf-8').write(s)
    print('событий: %d, шагов вставлено: %d (из них флагов: %d)' % (len(EVENTS), total, len(SETS)))
    return 0


if __name__ == '__main__':
    sys.exit(main())
