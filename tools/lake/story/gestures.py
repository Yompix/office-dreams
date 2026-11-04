"""WHAT THE LAKE KEEPS — жесты по подписям. Владелец (4 ноя): «когда Ною говорят, что руки красные, он никак не реагирует,
и не только — тоже странно». В подписях сцен («Ной прячет руки в карманы», «Дэнни кивает», «Зоя отворачивается к окну»)
герои делали это только текстом. Здесь каждой такой подписи (и паре реплик) добавляется жест — godot/lake/scripts/actor/
actor.gd, do_gesture(): pockets, look_hands, behind, chest, give, palm_to, take_hand, sleeve, look_away, nod, shake,
tremble, wave.

Правит godot/lake/data/ch1–5.json на месте; повторный запуск ничего не дублирует (жест в шаге заменяется).
Если генераторы глав (chN.py, chN_walk.py) пересобрали шаги — запустить снова:
    python3 tools/lake/story/gestures.py
"""
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))


def G(who, do, dur=2.2, **kw):
    g = {'who': who, 'do': do, 'dur': dur}
    g.update(kw)
    return g


# (глава, начало русского текста подписи или реплики, кто говорит или None для подписи) → жесты
RULES = [
    # глава 1
    (1, 'У тебя руки грязные.', 'danny', [G('noah', 'look_hands', 1.9)]),
    (1, 'Ной прячет руки в карманы.', None, [G('noah', 'pockets', 3.4)]),
    (1, 'На его руки.', None, [G('noah', 'pockets', 2.6)]),   # Роман смотрит на руки — Ной прячет (не отмыл)
    (1, 'Айрис молча протягивает Дэнни свои варежки.', None, [G('iris', 'give', 2.2)]),
    (1, 'Она сжимает блокнот так, что белеют пальцы.', None, [G('iris', 'chest', 2.6)]),
    (1, 'Она прячет блокнот под куртку.', None, [G('iris', 'chest', 1.8)]),
    (1, 'Дэнни берёт батончик обеими руками.', None, [G('danny', 'chest', 1.2), G('danny', 'nod')]),
    # глава 2
    (2, 'Идёт спиной вперёд против ветра и машет обеими руками.', None, [G('zoe', 'wave', 2.6)]),
    (2, 'Полицейский долго смотрит Ною в лицо.', None, [G('cop', 'give', 1.6)]),
    (2, 'Ной кладёт обе руки на руль. Луч фонарика ползёт по рукаву', None, [G('noah', 'sleeve', 2.6)]),
    (2, 'Что у вас на рукаве?', 'cop', [G('noah', 'sleeve', 1.6)]),
    (2, 'Она отворачивается к окну. В отражении видно', None, [G('iris', 'look_away', 3.0, bone='r')]),
    (2, 'Зоя отворачивается к окну. Больше они её не увидят.', None, [G('zoe', 'look_away', 2.6, bone='r')]),
    (2, 'Она кивает на фуры и смеётся.', None, [G('zoe', 'nod')]),
    (2, 'Ева отворачивается к окну и долго дышит', None, [G('eva', 'look_away', 3.2, bone='l')]),
    (2, 'Дэнни кивает, будто это договор.', None, [G('danny', 'nod')]),
    (2, 'На ней бурое и шерсть. Ной старается не смотреть.', None, [G('noah', 'look_away', 2.4, bone='l')]),
    # глава 3
    (3, 'Ева кивает.', None, [G('eva', 'nod')]),
    (3, 'Ной тянет тетрадь — Айрис во сне сжимает её крепче.', None, [G('noah', 'give', 1.6), G('iris', 'chest', 2.4)]),
    # глава 4
    (4, 'Дэнни кивает.', None, [G('danny', 'nod')]),
    (4, 'Дэнни кивает. Айрис у буханки всё слышала.', None, [G('danny', 'nod')]),
    (4, 'Дэнни трясётся. Ной кладёт ладонь ему на затылок', None,
     [G('danny', 'tremble', 3.2), G('noah', 'palm_to', 3.2, to='danny', bone='head')]),
    (4, 'Зоя видит указатель — и отворачивается.', None, [G('zoe', 'look_away', 2.4, bone='l')]),
    (4, 'Ной прячет папку под куртку.', None, [G('noah', 'chest', 2.0)]),
    (4, 'Ной кладёт папку на место.', None, [G('noah', 'give', 1.8)]),
    # глава 5
    (5, 'Дэнни кивает. Он это запомнит.', None, [G('danny', 'nod')]),
    (5, 'Айрис молча отдаёт пузырёк и отворачивается.', None, [G('iris', 'give', 1.6)]),
    (5, 'Её пальцы сжимают его руку.', None, [G('zoe', 'take_hand', 3.0, to='noah', bone='hand_l')]),
    (5, 'Айрис прячет письмо во внутренний карман.', None, [G('iris', 'chest', 1.8)]),
    (5, 'Ной берётся за верёвку.', None, [G('noah', 'give', 1.8)]),
]


def main():
    total = 0
    for ch in range(1, 6):
        path = os.path.join(ROOT, 'godot/lake/data/ch%d.json' % ch)
        j = json.load(open(path, encoding='utf-8'))
        rules = [r for r in RULES if r[0] == ch]
        hit = {i: 0 for i in range(len(rules))}

        def scan(x):
            nonlocal total
            if isinstance(x, list):
                for v in x:
                    scan(v)
            elif isinstance(x, dict):
                for i, (_, start, who, gs) in enumerate(rules):
                    if who is None:
                        n = x.get('note')
                        ok = isinstance(n, dict) and str(n.get('ru', '')).startswith(start)
                    else:
                        ok = x.get('say') == who and str(x.get('ru', '')).startswith(start)
                    if ok:
                        x['gesture'] = gs if len(gs) > 1 else gs[0]
                        hit[i] += 1
                        total += 1
                        break
                for k, v in x.items():
                    if k != 'gesture':
                        scan(v)

        scan(j.get('scenes', {}))
        scan(j.get('explore', {}))
        miss = [rules[i][1] for i in hit if hit[i] == 0]
        if miss:
            print('ch%d: не найдено: %s' % (ch, miss))
        s = json.dumps(j, ensure_ascii=False, indent=1) + '\n'
        open(path, 'w', encoding='utf-8').write(s)
    print('жестов поставлено: %d' % total)


if __name__ == '__main__':
    main()
