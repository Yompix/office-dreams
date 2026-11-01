#!/usr/bin/env python3
"""WHAT THE LAKE KEEPS — ритм по маршрутам симулятора: длительность глав, выборы, таймеры, действия, паузы без участия.
Длительность реплики — по озвучке (snd/vo/ru/timing.json), иначе по длине текста (как game.gd); ремарки/титры — их dur;
выбор — половина таймера; действие — его время. Это оценка экранного времени, без поиска и блужданий игрока.
  python3 tools/lake/story_rhythm.py [/tmp/lake_sim.json] [тест ...]"""
import json, os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import story_audit as A


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
                t_since += d
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
