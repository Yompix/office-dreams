#!/usr/bin/env python3
"""WHAT THE LAKE KEEPS — сценарий v2 (8 ноя, владелец: «общаются странно, всё слишком быстро, начало такое себе —
посмотри, как это делают в интерактивных фильмах»). Библия стиля — godot/docs/SCRIPT_V2_BIBLE.md.
Правки по главам — tools/story/script_v2/chN.json: список операций, применяются к godot/data/chN.json.

Операции (все тексты — ru и en вместе):
  {"op": "replace", "sid": СЦЕНА, "find": "русский текст шага", "ru": "...", "en": "...",
       ["say": "кто"], ["thought": true], ["note": true], ["if": {...}]}
      — первый шаг сцены (в т.ч. внутри «ask»/«if»-веток), у которого реплика / ремарка / титр / вариант выбора
        ровно = find. say — сделать репликой этого героя (ремарка → реплика), thought — мысли Ноя (say: noah),
        note — сделать ремаркой. Ключи постановки (shot, fx, dur, look, set, go, if …) не трогаются.
  {"op": "insert", "sid": СЦЕНА, "after": "русский текст шага" | "^" (в начало сцены), "beats": [шаги…]}
  {"op": "before", "sid": СЦЕНА, "find": "русский текст шага", "beats": [шаги…]}
      — новые шаги: {"say": кто, "ru", "en", ["thought": true], ["if": {...}], ["shot": ...]},
        {"note": {"ru","en"}, ["dur"]}, {"ask": {"id", "max", "opts": [{"ru","en",["if"],"beats":[…]}, {"leave":true,…}]}}
  {"op": "delete", "sid": СЦЕНА, "find": "русский текст шага"}
У изменённых и новых шагов снимается ключ "vo" (старая запись голоса к новому тексту не подходит; озвучка выключена).
Повторный запуск безопасен: уже применённое пропускается. Порядок после пересборки данных:
  chN.py / chN_walk.py → gestures.py → flag_responses.py → script_edits.py → script_v2.py
    python3 tools/story/script_v2.py [ch1 …] [--check]
"""
import json, os, sys

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '../..')
DATA = os.path.join(ROOT, 'godot/data')
OPS = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'script_v2')


def ru_of(b):
    """Русский текст шага: реплика, ремарка, титр, вариант выбора/темы."""
    if not isinstance(b, dict):
        return None
    if isinstance(b.get('ru'), str):
        return b['ru']
    for k in ('note', 'text'):
        if isinstance(b.get(k), dict) and isinstance(b[k].get('ru'), str):
            return b[k]['ru']
    return None


def lists(v):
    """Все списки шагов внутри сцены (сама сцена, beats вариантов выбора/тем и т.п.)."""
    if isinstance(v, list):
        yield v
        for x in v:
            yield from lists(x)
    elif isinstance(v, dict):
        for x in v.values():
            yield from lists(x)


def find(scene, text):
    for L in lists(scene):
        for i, b in enumerate(L):
            if ru_of(b) == text:
                return L, i
    return None, -1


def texts(scene):
    out = set()
    for L in lists(scene):
        for b in L:
            t = ru_of(b)
            if t:
                out.add(t)
    return out


def set_text(b, op):
    ru, en = op['ru'], op.get('en', '')
    b.pop('vo', None)
    if op.get('note') or (not op.get('say') and not op.get('thought') and (isinstance(b.get('note'), dict) or isinstance(b.get('text'), dict))):
        key = 'text' if isinstance(b.get('text'), dict) and not op.get('note') else 'note'
        for k in ('say', 'ru', 'en', 'thought', 'lips'):
            b.pop(k, None)
        b[key] = {'ru': ru, 'en': en}
        if key == 'note' and 'dur' not in b:
            b['dur'] = 2.6
        return
    who = op.get('say') or ('noah' if op.get('thought') else b.get('say'))
    if who:   # реплика (или вариант выбора, если who нет — просто текст)
        b.pop('note', None)
        b.pop('text', None)
        b['say'] = who
        if op.get('thought'):
            b['thought'] = True
        else:
            b.pop('thought', None)
    b['ru'], b['en'] = ru, en
    if 'if' in op:
        b['if'] = op['if']


def strip_vo(v):
    if isinstance(v, dict):
        v.pop('vo', None)
        for x in v.values():
            strip_vo(x)
    elif isinstance(v, list):
        for x in v:
            strip_vo(x)


def apply(ch, check=False):
    path_ops = os.path.join(OPS, ch + '.json')
    if not os.path.exists(path_ops):
        return (0, 0, 0, [])
    ops = json.load(open(path_ops, encoding='utf-8'))
    path = os.path.join(DATA, ch + '.json')
    d = json.load(open(path, encoding='utf-8'))
    sc = d.get('scenes', d)
    done = skip = 0
    miss = []
    for n, op in enumerate(ops):
        s = sc.get(op.get('sid'))
        if s is None:
            miss.append((n, op.get('sid'), 'нет сцены'))
            continue
        k = op['op']
        if k == 'replace':
            L, i = find(s, op['find'])
            if L is None:
                if op['ru'] in texts(s):
                    skip += 1
                else:
                    miss.append((n, op['sid'], 'нет текста: ' + op['find'][:50]))
                continue
            set_text(L[i], op)
            done += 1
        elif k == 'delete':
            L, i = find(s, op['find'])
            if L is None:
                skip += 1
                continue
            L.pop(i)
            done += 1
        elif k in ('insert', 'before'):
            new = json.loads(json.dumps(op['beats']))
            strip_vo(new)
            first = next((ru_of(b) for b in new if ru_of(b)), None)
            if first and first in texts(s):
                skip += 1
                continue
            anchor = op.get('after') if k == 'insert' else op.get('find')
            if anchor == '^':
                L = s['beats'] if isinstance(s, dict) else s
                i = 0
            else:
                L, i = find(s, anchor)
                if L is None:
                    miss.append((n, op['sid'], 'нет якоря: ' + str(anchor)[:50]))
                    continue
                if k == 'insert':
                    i += 1
            L[i:i] = new
            done += 1
        else:
            miss.append((n, op.get('sid'), 'неизвестная операция ' + k))
    if not check and done:
        with open(path, 'w', encoding='utf-8') as f:
            json.dump(d, f, ensure_ascii=False, indent=1)
            f.write('\n')
    return (len(ops), done, skip, miss)


def main():
    check = '--check' in sys.argv
    chs = [a for a in sys.argv[1:] if a.startswith('ch')] or ['ch%d' % i for i in range(1, 6)]
    bad = 0
    for ch in chs:
        tot, done, skip, miss = apply(ch, check)
        if tot:
            print(f'{ch}: операций {tot}, применено {done}, уже было {skip}, не найдено {len(miss)}' + (' (проверка)' if check else ''))
        for m in miss:
            print('   НЕТ', *m)
        bad += len(miss)
    sys.exit(1 if bad else 0)


if __name__ == '__main__':
    main()
