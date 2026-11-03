#!/usr/bin/env python3
"""WHAT THE LAKE KEEPS — правки по мастер-промпту редактора (3 ноя, §38 «проблемные места»), для глав без генератора
(ch1.json, ch2.json правятся на месте; ch3–5 — в своих tools/lake/story/chN.py). Повторный запуск ничего не дублирует.
  python3 tools/lake/story/fixes_mp.py
Что и почему — godot/lake/docs/BRANCH_AUDIT.md, раздел «§38».
  * s1_ditch → s1_talk: флаг ditch ставился, но нигде не читался — после кювета Айрис всё ещё держится за ручку двери;
  * s5_lapse: заправочный пистолет — Ной очнулся у колонки «будто собрался заправляться», бак давно полный
    (кровь на костяшках — от кулаков; пистолет на шланге в магазин не дотянуть);
  * c2_s4_wc: если в главе 1 пузырёк не выброшен (threw == 0), он у Ноя в кармане вместе со вторым, запасным —
    «Смыть всё» смывает оба (дальше игра так и считает)."""
import json, os

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
CH1 = os.path.join(ROOT, 'godot/lake/data/ch1.json')
CH2 = os.path.join(ROOT, 'godot/lake/data/ch2.json')


def note(ru, en):
    return {'ru': ru, 'en': en}


def ch1():
    j = json.load(open(CH1, encoding='utf-8'))
    sc = j['scenes']
    # кювет: реакция в первом разговоре
    b = sc['s1_talk']['beats']
    if not any(x.get('if', {}).get('ditch') for x in b):
        i = next(i for i, x in enumerate(b) if x == {'shot': 'iris', 'dur': 2.2})
        b[i] = {'shot': 'iris', 'dur': 2.2, 'if': {'ditch': '==0'}}
        b.insert(i + 1, {'shot': 'iris', 'dur': 2.8, 'if': {'ditch': '>0'},
                         'note': note('Айрис так и не отпустила ручку двери. Пальцы у неё белые.',
                                      'Iris still hasn’t let go of the door handle. Her knuckles are white.')})
    # заправочный пистолет
    for x in sc['s5_lapse']['beats']:
        nt = x.get('note')
        if isinstance(nt, dict) and nt.get('ru', '').startswith('Ной у колонки. В руке — заправочный пистолет'):
            x['note'] = note('Ной стоит у колонки и сжимает заправочный пистолет — будто собрался заправляться. Бак полный. Костяшки в крови.',
                             'Noah is standing at the pump, gripping the fuel nozzle — as if he were about to fill up. The tank is full. His knuckles are bloody.')
            x['dur'] = 3.6
    open(CH1, 'w', encoding='utf-8').write(json.dumps(j, ensure_ascii=False, indent=1) + '\n')


def ch2():
    j = json.load(open(CH2, encoding='utf-8'))
    b = j['scenes']['c2_s4_wc']['beats']
    if not any(x.get('if', {}).get('threw') for x in b):
        i = next(i for i, x in enumerate(b) if x.get('fx') == 'pills2')
        b[i]['if'] = {'threw': '>0'}
        alt = dict(b[i])
        alt['if'] = {'threw': '==0'}
        alt['note'] = note('В кармане куртки — пузырёк из машины, а под ним второй, запасной. Та же этикетка: «Д-р С. Хейл».',
                           'In his jacket pocket — the bottle from the car, and under it a second one, a spare. The same label: “Dr. S. Hale.”')
        alt['dur'] = 3.4
        b.insert(i + 1, alt)
    open(CH2, 'w', encoding='utf-8').write(json.dumps(j, ensure_ascii=False, indent=1) + '\n')


if __name__ == '__main__':
    ch1()
    ch2()
    print('ch1.json: кювет → реакция, заправочный пистолет; ch2.json: два пузырька при threw == 0')
