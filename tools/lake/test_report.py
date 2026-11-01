#!/usr/bin/env python3
"""WHAT THE LAKE KEEPS — таблица прохождений для docs/TEST_RESULTS.md из /tmp/lake_sim.json (симулятор) и
/tmp/lake_tests.json (Godot, tools/lake/story_tests.py).
  python3 tools/lake/test_report.py > /tmp/lake_report.md"""
import json, os, re

KEY = ['trust', 'shadow', 'remembered', 'proof', 'exposed', 'pills', 'ptotal', 'pills5', 'ditch', 'late', 'police', 'cop_saw',
       'fled', 'zoe', 'zoe_passed', 'zoe_taken', 'zoe_died', 'danny', 'danny_lost', 'danny_hale', 'eva_alive', 'eva_dead',
       'agnes_dead', 'baby_along', 'sitter_zoe', 'sitter_danny', 'help', 'hale_saved', 'hale_drowned', 'hale_let_go',
       'hale_killed', 'iris_saved', 'iris_lost', 'noah_drowned', 'roman_dead', 'orderly_dead']
END = {'witness': '«Свидетель»', 'shadow': '«Тень»', 'ice': '«Лёд»', 'mama': '«Мама»', 'oblivion': '«Забытьё»'}


def main():
    sim = json.load(open('/tmp/lake_sim.json'))
    gd = json.load(open('/tmp/lake_tests.json')) if os.path.exists('/tmp/lake_tests.json') else {}
    rows = []
    ok_n = 0
    for name, t in sim.items():
        g = gd.get(name)
        if g:
            ok = g['path_equal'] and g['ending_sim'] == g['ending_godot'] and not g['flag_diff'] and not g['errors']
            ok_n += ok
            txt = open(g['log'], encoding='utf-8', errors='replace').read() if os.path.exists(g['log']) else ''
            tm = re.findall(r'^время игры: ([\d.]+) мин', txt, re.M)
            res = ('совпало: путь %d сцен, концовка, флаги; ошибок 0' % g['path_len']) if ok else \
                  ('РАСХОДИТСЯ: путь %s, концовка %s, флаги %s, ошибки %s' % (g['path_equal'], g['ending_godot'], g['flag_diff'], g['errors'][:2]))
            tm = (tm[-1] + ' мин') if tm else '—'
        else:
            res, tm = 'в Godot не запускался', '—'
        fl = t['flags']
        key = ', '.join('%s=%g' % (k, fl[k]) for k in KEY if fl.get(k))
        ch = '; '.join('%s: %s' % (s, c) for s, c in t['choices'])
        opt = ''
        if t.get('miss'):
            opt += ' --miss ' + ','.join(t['miss'])
        if t.get('missin'):
            opt += ' --missin ' + ','.join(t['missin'])
        rows.append((name, t, res, tm, key, ch, opt))
    print('| Тест | Что проверяет | Концовка | Godot | Время сценария |')
    print('|---|---|---|---|---|')
    for name, t, res, tm, key, ch, opt in rows:
        print('| %s | %s | %s | %s | %s |' % (name, t['title'], END.get(t['ending'], t['ending']), res, tm))
    print()
    print('Итого в Godot: %d из %d совпали с симуляцией.' % (ok_n, len(rows)))
    print()
    for name, t, res, tm, key, ch, opt in rows:
        print('### %s — %s' % (name, t['title']))
        print('- Концовка: %s. Godot: %s.' % (END.get(t['ending'], t['ending']), res))
        print('- Запуск: `godot --headless --path godot/lake -- --autoplay --fast --pick %s%s`' % (','.join(map(str, t['picks'])), opt))
        print('- Ключевые итоговые флаги: %s' % key)
        print('- Решения (%d): %s' % (len(t['choices']), ch))
        print()


if __name__ == '__main__':
    main()
