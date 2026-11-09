#!/usr/bin/env python3
"""WHAT THE LAKE KEEPS — ритм по маршрутам симулятора: длительность глав, выборы, таймеры, действия, паузы без участия.
Длительность реплики — по озвучке (snd/vo/ru/timing.json), иначе по длине текста (как game.gd); ремарки/титры — их dur;
выбор — половина таймера; действие — его время. Это оценка экранного времени; прогулки — как если осмотреть всё (ходьба по прямой), без блужданий игрока.
  python3 tools/story_rhythm.py [/tmp/lake_sim.json] [тест ...]"""
import json, os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import story_audit as A


def _line(b):
    return max(1.4, min(9.0, 0.9 + len(b.get('ru', '')) / 15.0))


def nested_sec(b, data):
    """Прогулка и разговор с выбором тем — время, если игрок осмотрит всё (прогулка) и выберет столько тем,
    сколько успевают ("max"); ходьба — по прямой между точками со скоростью 1,35 м/с, на точку — 3 с подойти и
    встать; каждый выбор темы — 3 с. Возвращает (секунды, реплик, выборов)."""
    sec, lines, picks = 0.0, 0, 0

    def run(beats):
        nonlocal sec, lines
        for x in beats:
            if 'say' in x:
                sec += _line(x)
                lines += 1
            elif 'note' in x or 'text' in x or 'dur' in x:
                sec += float(x.get('dur', 2.0 if 'note' in x else 2.5))

    def ask(q):
        nonlocal sec, picks
        opts = [o for o in q.get('opts', []) if not o.get('leave')]
        for o in opts[:int(q['max'])] if 'max' in q else opts:
            sec += 3.0
            picks += 1
            run(o.get('beats', []))
        run(q.get('end', []))

    if 'explore' in b:
        d = data.get('explore', {}).get(b['explore'], {})
        run(d.get('setup', []) + d.get('intro', []))
        pos = None
        for s in d.get('spots', []) + ([d['exit']] if d.get('exit') else []):
            w = s.get('where', s['at'])
            w = (float(w[0]), float(w[-1]))
            if pos:
                sec += ((w[0] - pos[0]) ** 2 + (w[1] - pos[1]) ** 2) ** 0.5 / 1.35
            pos = w
            sec += 3.0
            picks += 1
            run(s.get('beats', []))
            if s.get('ask'):
                ask(s['ask'])
            run(s.get('after', []))
    if 'ask' in b:
        ask(b['ask'])
    return sec, lines, picks


def main():
    simp = sys.argv[1] if len(sys.argv) > 1 and sys.argv[1].endswith('.json') else '/tmp/lake_sim.json'
    names = [a for a in sys.argv[1:] if not a.endswith('.json')]
    sim = json.load(open(simp))
    data, chapters, owner, _ = A.load()
    keys = A.vo_keys(data)
    vo = json.load(open(os.path.join(A.LAKE, 'snd/vo/ru/timing.json')))
    out = {}
    for name in names or list(sim):
        r = sim[name]
        per = {}
        t_since = 0.0
        longest = (0.0, '')
        for sid in r['path']:
            ch = owner[sid]
            p = per.setdefault(ch, {'sec': 0.0, 'choices': 0, 'timed': 0, 'qte': 0, 'lines': 0})
            for i, b in enumerate(data['scenes'][sid]['beats']):
                d = 0.0
                if 'say' in b:
                    k = keys[(sid, i)]
                    d = float(vo[k]['dur']) + 0.35 if k in vo else max(1.4, min(9.0, 0.9 + len(b['ru']) / 15.0))
                    p['lines'] += 1
                elif 'note' in b or 'text' in b or 'dur' in b:
                    d = float(b.get('dur', 2.0 if 'note' in b else 2.5))
                own = False
                if 'explore' in b or 'ask' in b:   # игрок сам: прогулка, темы разговора
                    ns, nl, npk = nested_sec(b, data)
                    d += ns
                    p['lines'] += nl
                    p['choices'] += npk
                    own = True
                if 'choice' in b:
                    tt = float(b['choice'].get('t', 6))
                    d += tt / 2
                    p['choices'] += 1
                    p['timed'] += 1
                    t_since = 0.0
                if 'qte' in b:
                    d += float(b.get('t', 3.0)) * 0.7
                    p['qte'] += 1
                    t_since = 0.0
                if 'peek' in b:
                    d += 2.0
                    t_since = 0.0
                t_since = 0.0 if own else t_since + d
                if t_since > longest[0]:
                    longest = (t_since, sid)
                p['sec'] += d
        out[name] = {'chapters': per, 'longest_no_input': longest, 'total_min': sum(p['sec'] for p in per.values()) / 60}
        print('%s — всего %.0f мин; дольше всего без участия: %.0f с (до %s)' % (name, out[name]['total_min'], longest[0], longest[1]))
        for ch, p in sorted(per.items()):
            print('   гл.%d: %4.1f мин, выборов %2d, действий %d, реплик %3d, выбор раз в %.1f мин' % (
                ch, p['sec'] / 60, p['choices'], p['qte'], p['lines'], p['sec'] / 60 / max(1, p['choices'] + p['qte'])))
    json.dump(out, open('/tmp/lake_rhythm.json', 'w'), ensure_ascii=False, indent=1)
    return 0


if __name__ == '__main__':
    sys.exit(main())
