#!/usr/bin/env python3
"""Проверка сюжета без игры: места, поля шагов, герои, предметы, озвучка.
Запуск из папки novograd: python3 tools/check_story.py"""
import json, os, re, sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, '..')
SRC = os.path.join(ROOT, 'src', 'main')
story = json.load(open(os.path.join(SRC, 'resources', 'novograd_story.json'), encoding='utf-8'))
island = open(os.path.join(SRC, 'java/com/yompix/novograd/world/Island.java'), encoding='utf-8').read()
town = open(os.path.join(SRC, 'java/com/yompix/novograd/world/TownBuilder.java'), encoding='utf-8').read()
ISL = set(re.findall(r'(?:put|cam)\("([a-z_0-9]+)"', island))
TOWN = set(re.findall(r'b\.(?:place|cam)\("([a-z_0-9]+)"', town))
PLACES = ISL | TOWN
CHARS = set(story['characters'])
VOICE = os.path.join(SRC, 'resources/assets/novograd/sounds/voice')
items = set()
for f in ('java/com/yompix/novograd/ModContent.java', 'java/com/yompix/novograd/gta/Arsenal.java'):
    p = os.path.join(SRC, f)
    if os.path.exists(p):
        items |= set(re.findall(r'item\("([a-z_0-9]+)"', open(p, encoding='utf-8').read()))

STEP_TYPES = {'dialog', 'talk', 'goto', 'lesson', 'job', 'money', 'buy', 'sleep', 'collect', 'race', 'drivetest', 'party', 'cutscene', 'title',
              'credits', 'phone', 'ball', 'carrace', 'tune', 'escape', 'steal', 'clear', 'chase'}
REQ = {'goto': ['place'], 'talk': ['npc'], 'collect': ['clues'], 'carrace': ['start', 'points'], 'steal': ['place', 'to'], 'clear': ['place'],
       'chase': ['points'], 'escape': ['stars'], 'buy': ['item'], 'cutscene': ['shots']}
problems, warnings = [], []
overlap = ISL & TOWN
if overlap:
    warnings.append(f'места есть и на острове, и в старом центре (какое берётся?): {sorted(overlap)}')


def place_ok(name):
    if name.startswith('@'):
        return True
    base = name.split('+', 1)[0] if name.find('+', 1) > 0 else name
    return base in PLACES


def check_lines(where, lines):
    for ln in lines or []:
        if ln.get('who') not in CHARS:
            problems.append(f'{where}: реплика от неизвестного героя «{ln.get("who")}»')
        v = ln.get('voice')
        if v and not os.path.exists(os.path.join(VOICE, v + '.ogg')):
            warnings.append(f'{where}: нет озвучки {v}')


def check_steps(where, steps, spawned):
    for i, s in enumerate(steps):
        w = f'{where} шаг {i} ({s.get("type")})'
        t = s.get('type')
        if t not in STEP_TYPES:
            problems.append(f'{w}: неизвестный тип шага')
            continue
        for k in REQ.get(t, []):
            if k not in s or s[k] in ('', [], None):
                problems.append(f'{w}: нет поля «{k}»')
        for a in s.get('do', []):
            if a[0] in ('spawn', 'tp_npc'):
                spawned.add(a[1])
                if not place_ok(a[2]): problems.append(f'{w}: do {a[0]} — нет места «{a[2]}»')
            if a[0] in ('tp', 'car') and not place_ok(a[1]):
                problems.append(f'{w}: do {a[0]} — нет места «{a[1]}»')
            if a[0] == 'despawn':
                spawned.discard(a[1])
            if a[0] in ('give', 'take') and a[1].split(':')[-1] not in items and not a[1].startswith('minecraft:'):
                problems.append(f'{w}: нет предмета {a[1]}')
        for k in ('place', 'to', 'start'):
            if k in s and not place_ok(s[k]):
                problems.append(f'{w}: нет места «{s[k]}» ({k})')
        for p in s.get('points', []):
            if not place_ok(p): problems.append(f'{w}: нет точки маршрута «{p}»')
        for c in s.get('clues', []):
            if not place_ok(c['place']): problems.append(f'{w}: нет места улики «{c["place"]}»')
        if t == 'buy' and s.get('item', '').split(':')[-1] not in items:
            problems.append(f'{w}: нет предмета {s.get("item")}')
        if t == 'talk' and s.get('npc') not in spawned:
            problems.append(f'{w}: разговор с «{s.get("npc")}», но он ещё не появлялся в мире (нет spawn раньше)')
        if t == 'cutscene':
            for sh in s.get('shots', []):
                for k in ('from', 'to'):
                    if k in sh and not place_ok(sh[k]): problems.append(f'{w}: кадр — нет места «{sh[k]}»')
                if not any(k in sh for k in ('from', 'follow', 'track')): problems.append(f'{w}: кадр без позиции')
            actors = set()
            for cue in s.get('actors', []):
                kind = cue[1]
                if kind in ('spawn', 'car'):
                    actors.add(cue[2])
                    if not place_ok(cue[3]): problems.append(f'{w}: актёр {cue[2]} — нет места «{cue[3]}»')
                    if kind == 'spawn' and cue[2] in CHARS: spawned.add(cue[2])
                elif kind in ('walk', 'run'):
                    if not place_ok(cue[3]): problems.append(f'{w}: {kind} {cue[2]} — нет места «{cue[3]}»')
                elif kind == 'drive':
                    for pt in cue[3]:
                        if not place_ok(pt): problems.append(f'{w}: drive — нет точки «{pt}»')
                elif kind in ('boom', 'smoke') and not place_ok(cue[2]):
                    problems.append(f'{w}: {kind} — нет места «{cue[2]}»')
                for ref in cue[2:4]:
                    if kind in ('act', 'face', 'ride', 'shoot', 'die', 'fight', 'despawn', 'drive', 'exit', 'walk', 'run') and isinstance(ref, str) \
                            and ref not in actors and ref not in CHARS and ref != 'player' and not place_ok(ref) and not ref.isupper() and ref != 'none':
                        problems.append(f'{w}: «{kind}» — неизвестный актёр «{ref}»')
            for sh in s.get('shots', []):
                for k in ('follow', 'track'):
                    if k in sh and sh[k] not in actors and sh[k] not in CHARS:
                        problems.append(f'{w}: камера следит за «{sh[k]}», а такого актёра нет')
        check_lines(w, s.get('lines'))
        for c in s.get('choices', []):
            check_lines(w + ' выбор', c.get('reply'))


spawned = set()
n = 0
for ch in story['chapters']:
    for m in ch['missions']:
        n += 1
        check_steps(f'{ch["id"]}/{m["id"]} «{m["title"]}»', m['steps'], spawned)
for sid, sm in story['side'].items():
    check_steps(f'побочная {sid}', sm['steps'], set(CHARS))
print(f'миссий: {n}, побочных: {len(story["side"])}')
for w in warnings[:40]:
    print('  ⚠', w)
for p in problems:
    print('  ✗', p)
print('ошибок:', len(problems), 'предупреждений:', len(warnings))
sys.exit(1 if problems else 0)
