#!/usr/bin/env python3
"""WHAT THE LAKE KEEPS — маршруты из tools/story_sim.py прогоняются в Godot (без окна, автопилот, те же выборы
и провалы действий) и сравниваются с симулятором: путь по сценам, концовка, итоговые флаги.
  python3 tools/story_sim.py all && python3 tools/story_tests.py [ИМЯ ...]   → /tmp/lake_tests.json
Нужен Godot: source ../../tools/godot/env.sh (переменная GODOT)."""
import json, os, re, subprocess, sys, concurrent.futures as cf

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
LAKE = os.path.join(ROOT, 'godot')
GODOT = os.environ.get('GODOT', 'godot')


def run(name, t):
    args = [GODOT, '--headless', '--path', LAKE, '--', '--autoplay', '--fast', '--pick', ','.join(map(str, t['picks']))]
    if t.get('miss'):
        args += ['--miss', ','.join(t['miss'])]
    if t.get('missin'):
        args += ['--missin', ','.join(t['missin'])]
    if t.get('skip'):
        args += ['--skip', ','.join(t['skip'])]
    log = '/tmp/lake_test_%s.log' % name
    with open(log, 'w') as fo:
        subprocess.run(args, stdout=fo, stderr=subprocess.STDOUT, timeout=1500)
    txt = open(log, encoding='utf-8', errors='replace').read()
    path = re.findall(r'^сцена (\S+)', txt, re.M)
    end = re.findall(r'^конец игры: (\w+)', txt, re.M)
    fl = re.findall(r'^итог флаги (\{.*\})', txt, re.M)
    errs = re.findall(r'^SCRIPT ERROR: (.*)$', txt, re.M)
    flags = {k: v for k, v in json.loads(fl[-1]).items() if v} if fl else {}
    sim = {k: float(v) for k, v in t['flags'].items()}
    gd = {k: float(v) for k, v in flags.items()}
    diff = sorted(set(k for k in set(sim) | set(gd) if sim.get(k, 0) != gd.get(k, 0)))
    return name, {'title': t['title'], 'ending_sim': t['ending'], 'ending_godot': end[-1] if end else None,
                  'path_equal': path == t['path'], 'path_len': len(path), 'sim_len': len(t['path']),
                  'first_diff': next((i for i, (a, b) in enumerate(zip(path, t['path'])) if a != b), None),
                  'flag_diff': {k: [sim.get(k, 0), gd.get(k, 0)] for k in diff}, 'errors': errs[:5],
                  'picks': t['picks'], 'miss': t.get('miss', []), 'missin': t.get('missin', []),
                  'choices': t['choices'], 'flags': t['flags'], 'log': log}


def main():
    sim = json.load(open('/tmp/lake_sim.json'))
    names = sys.argv[1:] or list(sim)
    res = {}
    with cf.ThreadPoolExecutor(max_workers=int(os.environ.get('JOBS', '4'))) as ex:
        for name, r in ex.map(lambda n: run(n, sim[n]), names):
            res[name] = r
            ok = r['path_equal'] and r['ending_sim'] == r['ending_godot'] and not r['flag_diff'] and not r['errors']
            print('%s %-28s %-9s путь %s (%d), флаги %s, ошибок %d' % ('ОК ' if ok else 'НЕТ', name, r['ending_godot'],
                  'совпал' if r['path_equal'] else 'расходится с №%s' % r['first_diff'], r['path_len'],
                  'совпали' if not r['flag_diff'] else r['flag_diff'], len(r['errors'])), flush=True)
    old = json.load(open('/tmp/lake_tests.json')) if os.path.exists('/tmp/lake_tests.json') else {}
    old.update(res)
    json.dump(old, open('/tmp/lake_tests.json', 'w'), ensure_ascii=False, indent=1)
    return 0


if __name__ == '__main__':
    sys.exit(main())
