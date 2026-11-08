#!/usr/bin/env python3
"""WHAT THE LAKE KEEPS — редактура текста поверх данных глав (8 ноя, владелец: «чтоб слова были нормальные, не придуманные,
логичные… подойди серьёзно»). Правки лежат в tools/lake/story/script_edits.json: [{ch, sid, old_ru, new_ru, old_en, new_en, why}].
Применяются к godot/lake/data/chN.json: в сцене sid ищется любой текстовый узел {ru, en} с ru == old_ru (и en == old_en, если
задан) и получает новый текст. Повторный запуск безопасен (уже исправленное пропускается).
  python3 tools/lake/story/script_edits.py           → применить, напечатать сводку
  python3 tools/lake/story/script_edits.py --check   → только проверить, всё ли находится
ВАЖНО: если chN.py / chN_walk.py / gestures.py пересобрали данные — после них снова запустить этот скрипт."""
import json, os, sys

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '../..')
DATA = os.path.join(ROOT, 'godot/lake/data')
EDITS = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'script_edits.json')


def nodes(v):
    if isinstance(v, dict):
        if isinstance(v.get('ru'), str):
            yield v
        for k, x in v.items():
            if k not in ('ru', 'en'):
                yield from nodes(x)
    elif isinstance(v, list):
        for x in v:
            yield from nodes(x)


def main():
    check = '--check' in sys.argv
    edits = json.load(open(EDITS, encoding='utf-8'))
    by_ch = {}
    for e in edits:
        by_ch.setdefault(e['ch'], []).append(e)
    total = done = already = 0
    missing = []
    for ch, es in sorted(by_ch.items()):
        path = os.path.join(DATA, ch + '.json')
        d = json.load(open(path, encoding='utf-8'))
        sc = d.get('scenes', d)
        changed = False
        for e in es:
            total += 1
            scene = sc.get(e['sid'])
            if scene is None:
                missing.append((ch, e['sid'], e['old_ru'][:50], 'нет сцены'))
                continue
            hit = [n for n in nodes(scene) if n['ru'] == e['old_ru'] and (not e.get('old_en') or n.get('en', '') == e['old_en'])]
            if not hit:
                if any(n['ru'] == e['new_ru'] for n in nodes(scene)):
                    already += 1
                    continue
                missing.append((ch, e['sid'], e['old_ru'][:50], 'не найден текст'))
                continue
            for n in hit:
                n['ru'] = e['new_ru']
                if e.get('new_en'):
                    n['en'] = e['new_en']
            done += 1
            changed = True
        if changed and not check:
            with open(path, 'w', encoding='utf-8') as f:
                json.dump(d, f, ensure_ascii=False, indent=1)
                f.write('\n')
    print(f'правок {total}: применено {done}, уже было {already}, не найдено {len(missing)}' + (' (только проверка)' if check else ''))
    for m in missing:
        print('  НЕТ', *m)


if __name__ == '__main__':
    main()
