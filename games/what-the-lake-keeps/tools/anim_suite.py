#!/usr/bin/env python3
"""WHAT THE LAKE KEEPS — замеры анимации героев на стенде godot/scenes/dev/anim_lab (без окна, кадр 1/60 с).
  source ../../tools/godot/env.sh && python3 tools/anim_suite.py [--perf] [--jobs N]   → /tmp/lake_anim_suite.json
Что меряется (подробно — godot/docs/CHARACTER_TESTS.md):
  idle   — 12 с стоит: «манекен» (доля секунд, когда поза почти не меняется), самый большой скачок кости за кадр
  walk   — идёт 6 м и встаёт: скольжение опорной стопы (за всю опору, м/с), скачок на старте и остановке
  trans  — цепочка клипов стоя/шаг/сидя/присел: самый большой скачок кости за кадр
  turn   — резкие повороты головы: скачок, волосы (отставание и успокоение)
  pause  — пауза игры на 1,5 с посреди простоя: поза должна замереть и продолжиться без рывка
  carry  — переноска на руках (гл. 3) и на спине (гл. 4): насколько кисти далеки от точек опоры
  perf   — 8 героев сразу: микросекунды обработки героев за кадр (CPU; только с --perf и без другой нагрузки)
"""
import json, os, re, subprocess, sys, concurrent.futures as cf

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
LAKE = os.path.join(ROOT, 'godot')
GODOT = os.environ.get('GODOT', 'godot')

CASES = [('idle', w, []) for w in ['noah', 'iris', 'zoe', 'eva', 'danny', 'agnes', 'hale']]
CASES += [('walk', w, []) for w in ['noah', 'iris', 'danny', 'hale']]
CASES += [('trans', 'iris', []), ('turn', 'iris', []), ('turn', 'zoe', []), ('turn', 'agnes', []), ('pause', 'iris', []),
          ('carry', 'noah+eva', []), ('carry', 'noah+agnes', ['--piggy'])]


def run(case):
    test, who, extra = case
    out = '/tmp/lake_anim_%s_%s.json' % (test, who.replace('+', '_'))
    scene = os.environ.get('LAB_SCENE', 'res://scenes/dev/anim_lab.tscn')   # замер «до»: копия стенда без переноски
    args = [GODOT, '--headless', '--fixed-fps', '60', '--path', LAKE, scene, '--',
            '--lab', test, '--out', out] + extra
    if test not in ('carry', 'perf'):
        args += ['--who', who]
    if test == 'perf':
        args += ['--sections']
    p = subprocess.run(args, capture_output=True, text=True, timeout=int(os.environ.get('LAB_TIMEOUT', '600')))
    txt = p.stdout + p.stderr
    res = json.load(open(out)) if os.path.exists(out) else {}
    m = re.search(r'ладони от точек опоры — медиана ([\d.]+) см, макс ([\d.]+) см', txt)
    pz = re.search(r'за паузу поза сдвинулась на ([\d.]+)°, после паузы самый большой скачок ([\d.]+)°', txt)
    sec = re.search(r'по частям \(мкс/кадр на всех\): (.*)', txt)
    errs = re.findall(r'^SCRIPT ERROR: (.*)$', txt, re.M)
    hair = res.get('hair', [])
    return {'test': test, 'who': who, 'jump_deg': res.get('max_bone_jump_deg'), 'jump_at': res.get('jump_at'),
            'still_seconds': res.get('still_seconds_ratio'), 'stance_slide_mps': res.get('stance_slide_mps'),
            'stances': res.get('stances'), 'us_per_frame': res.get('actor_us_per_frame'), 'actors': res.get('actors'),
            'hand_err_cm': [float(m.group(1)), float(m.group(2))] if m else None,
            'pause_deg': [float(pz.group(1)), float(pz.group(2))] if pz else None,
            'sections': sec.group(1) if sec else None, 'hair_samples': len(hair), 'errors': errs[:3]}


def main():
    cases = list(CASES)
    if '--perf' in sys.argv:
        cases = [('perf', 'cast8', [])]
    if '--nocarry' in sys.argv:   # старое ядро (замер «до»): у него нет API переноски
        cases = [c for c in cases if c[0] != 'carry']
    jobs = int(sys.argv[sys.argv.index('--jobs') + 1]) if '--jobs' in sys.argv else 3
    if cases[0][0] == 'perf':
        jobs = 1
    with cf.ThreadPoolExecutor(jobs) as ex:
        rows = list(ex.map(run, cases))
    json.dump(rows, open(os.environ.get('SUITE_OUT', '/tmp/lake_anim_suite.json'), 'w'), ensure_ascii=False, indent=1)
    for r in rows:
        parts = ['%-6s %-11s' % (r['test'], r['who'])]
        if r['jump_deg'] is not None:
            parts.append('скачок %5.1f° (%s)' % (r['jump_deg'], r['jump_at']))
        if r['test'] == 'idle':
            parts.append('манекен %3.0f%%' % (100 * (r['still_seconds'] or 0)))
        if r['test'] == 'walk':
            parts.append('опора скользит %.3f м/с (%s шагов)' % (r['stance_slide_mps'] or 0, r['stances']))
        if r.get('pause_deg'):
            parts.append('за паузу сдвиг %.3f°, после паузы скачок %.2f°' % tuple(r['pause_deg']))
        if r['hand_err_cm']:
            parts.append('кисти от опоры %.1f / макс %.1f см' % tuple(r['hand_err_cm']))
        if r['test'] == 'perf':
            parts.append('%.0f мкс/кадр на %s героев; %s' % (r['us_per_frame'] or 0, r['actors'], r['sections']))
        if r['errors']:
            parts.append('ОШИБКИ: %s' % r['errors'])
        print('  '.join(parts))


if __name__ == '__main__':
    main()
