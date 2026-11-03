#!/usr/bin/env python3
"""WHAT THE LAKE KEEPS — схема сцен по данным игры (godot/lake/data/ch1..ch5.json): порядок сцен главы от начала,
куда ведёт каждая (переход, «иначе», варианты выбора), прогулки (точки осмотра и выход), разговоры с темами.
  python3 tools/lake/scene_flow.py     → godot/lake/docs/SCENE_FLOW.md"""
import json, os

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '../..')
DATA = os.path.join(ROOT, 'godot/lake/data')
OUT = os.path.join(ROOT, 'godot/lake/docs/SCENE_FLOW.md')


def cond(c):
    return ', '.join('%s %s' % (k, v if str(v)[:1] in '<>=!' else '== %s' % v) for k, v in (c or {}).items())


def exits(sc):
    out = []
    for b in sc['beats']:
        if b.get('else'):
            out.append(('иначе', b['else'], cond(b.get('if'))))
        if b.get('go'):
            out.append(('переход', b['go'], cond(b.get('if')) if b.get('if') else ''))
        if 'choice' in b:
            for o in b['choice']['opts']:
                if o.get('go'):
                    out.append(('выбор', o['go'], 'молчание' if o.get('silent') else o.get('ru', '')))
        if b.get('qte') and b.get('fail'):
            out.append(('провал действия', b['fail'], b.get('hint', {}).get('ru', b['qte']) if isinstance(b.get('hint'), dict) else b['qte']))
    return out


def walks(sc, data):
    res = []
    for b in sc['beats']:
        if 'explore' in b:
            d = data.get('explore', {}).get(b['explore'], {})
            spots = [s.get('label', {}).get('ru', s['id']) for s in d.get('spots', [])]
            ex = d.get('exit', {}).get('label', {}).get('ru', '—')
            res.append('прогулка `%s`: %s → выход «%s»' % (b['explore'], '; '.join(spots), ex))
        if 'ask' in b:
            q = b['ask']
            ts = [o.get('ru', '') for o in q.get('opts', []) if not o.get('leave')]
            res.append('разговор (тем %d, можно %s): %s' % (len(ts), q.get('max', 'все'), '; '.join(ts)))
    return res


def main():
    lines = ['# WHAT THE LAKE KEEPS — схема сцен', '',
             'Собирается из данных игры: `python3 tools/lake/scene_flow.py`. Порядок — обход от начала главы (сначала основная',
             'линия, ветки — по мере появления). Условия — как в данных (флаги). Прогулки и разговоры с темами — отдельной строкой.',
             '']
    total = 0
    for n in range(1, 6):
        p = os.path.join(DATA, 'ch%d.json' % n)
        if not os.path.exists(p):
            continue
        data = json.load(open(p, encoding='utf-8'))
        sc = data['scenes']
        start = data.get('start') or next(iter(sc))
        seen, order, queue = set(), [], [start]
        while queue:
            k = queue.pop(0)
            if k in seen or k not in sc:
                continue
            seen.add(k)
            order.append(k)
            for _, t, _ in exits(sc[k]):
                if t in sc and t not in seen:
                    queue.append(t)
        order += [k for k in sc if k not in seen]   # не достижимые отсюда (входы из других глав, контрольные точки)
        total += len(order)
        lines += ['## Глава %d — %s' % (n, data.get('title', {}).get('ru', '')), '',
                  'Сцен: %d, прогулок: %d.' % (len(order), len(data.get('explore', {}))), '']
        for k in order:
            s = sc[k]
            tag = []
            if s.get('save'):
                tag.append('контрольная точка')
            if s.get('time'):
                tag.append(s['time'])
            lines.append('- **%s**%s' % (k, (' (' + ', '.join(tag) + ')') if tag else ''))
            for w in walks(s, data):
                lines.append('  - %s' % w)
            for kind, t, c in exits(s):
                lines.append('  - %s → `%s`%s' % (kind, t, (' — ' + c) if c else ''))
        lines.append('')
    lines.insert(4, 'Всего сцен: %d.' % total)
    lines.insert(5, '')
    open(OUT, 'w', encoding='utf-8').write('\n'.join(lines) + '\n')
    print('SCENE_FLOW.md:', total, 'сцен')


if __name__ == '__main__':
    main()
