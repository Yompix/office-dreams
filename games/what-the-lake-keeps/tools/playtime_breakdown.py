#!/usr/bin/env python3
"""WHAT THE LAKE KEEPS — из чего складывается время прохождения (для аудита, ничего в игре не меняет).
Те же правила оценки, что у story_rhythm.py (реплика — по голосу, иначе по длине текста; ремарка — её dur; выбор —
половина таймера; действие — 0,7 таймера; прогулка — все точки, ходьба по прямой 1,35 м/с, 3 с на подход), но время
разложено по видам: обязательный сюжет / выборы / действия (QTE) / прогулки (ходьба и что в точках) / разговоры с темами.
  python3 tools/story_sim.py all && python3 tools/playtime_breakdown.py [/tmp/lake_sim.json] [--md]"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import story_audit as A   # noqa: E402

KINDS = ['story', 'choice', 'qte', 'walk', 'spot', 'talk']
NAMES = {'story': 'сюжет (реплики, ремарки, паузы)', 'choice': 'выборы (половина таймера)', 'qte': 'действия на время',
         'walk': 'прогулки: ходьба и подход', 'spot': 'прогулки: что в точках', 'talk': 'разговоры с темами'}


NOVO = os.environ.get('VO') == '1'   # 8 ноя: озвучка выключена по умолчанию — время реплики по тексту (как game.gd), VO=1 — по голосу


def _txt_line(b):   # game.gd 8 ноя: 1,4 + знаки/11,5 (2,2…12 с) + вдох 0,3 с
    return max(2.2, min(12.0, 1.4 + len(b.get('ru', '')) / 11.5)) + 0.3


def _note(b):   # ремарка/титр: не короче, чем прочесть (game.gd _read_t)
    t = b.get('note') or b.get('text')
    n = len(t.get('ru', '')) if isinstance(t, dict) else 0
    d = float(b.get('dur', 2.0 if 'note' in b else 2.5))
    return max(d * 1.3, 1.6 + n / 12.0) if isinstance(t, dict) else d


def _line(b, vo, key):
    return float(vo[key]['dur']) + 0.35 if (key in vo and NOVO) else _txt_line(b)


def _beats(beats, acc, kind):
    for x in beats:
        if 'say' in x:
            acc[kind] += _txt_line(x)
        elif 'note' in x or 'text' in x or 'dur' in x:
            acc[kind] += _note(x)


def _ask(q, acc):
    opts = [o for o in q.get('opts', []) if not o.get('leave')]
    for o in opts[:int(q['max'])] if 'max' in q else opts:
        acc['talk'] += 3.0
        _beats(o.get('beats', []), acc, 'talk')
    _beats(q.get('end', []), acc, 'talk')


def _explore(d, acc):
    _beats(d.get('setup', []) + d.get('intro', []), acc, 'spot')
    pos = None
    for s in d.get('spots', []) + ([d['exit']] if d.get('exit') else []):
        w = s.get('where', s['at'])
        w = (float(w[0]), float(w[-1]))
        if pos:
            acc['walk'] += ((w[0] - pos[0]) ** 2 + (w[1] - pos[1]) ** 2) ** 0.5 / 1.35
        pos = w
        acc['walk'] += 3.0
        _beats(s.get('beats', []), acc, 'spot')
        if s.get('ask'):
            _ask(s['ask'], acc)
        _beats(s.get('after', []), acc, 'spot')


def main():
    simp = next((a for a in sys.argv[1:] if a.endswith('.json')), '/tmp/lake_sim.json')
    sim = json.load(open(simp))
    data, chapters, owner, _ = A.load()
    keys = A.vo_keys(data)
    vo = json.load(open(os.path.join(A.LAKE, 'snd/vo/ru/timing.json')))
    tot = {}   # маршрут → глава → вид → секунды
    for name, r in sim.items():
        per = tot.setdefault(name, {})
        for sid in r['path']:
            acc = per.setdefault(owner[sid], {k: 0.0 for k in KINDS})
            for i, b in enumerate(data['scenes'][sid]['beats']):
                if 'say' in b:
                    acc['story'] += _line(b, vo, keys.get((sid, i)))
                elif 'note' in b or 'text' in b or 'dur' in b:
                    acc['story'] += _note(b)
                if 'explore' in b:
                    _explore(data.get('explore', {}).get(b['explore'], {}), acc)
                if 'ask' in b:
                    _ask(b['ask'], acc)
                if 'choice' in b:
                    acc['choice'] += float(b['choice'].get('t', 6)) / 2
                if 'qte' in b:
                    acc['qte'] += float(b.get('t', 3.0)) * 0.7
                if 'peek' in b:
                    acc['qte'] += 2.0
    # среднее по маршрутам: глава × вид, минуты
    n = len(tot)
    avg = {}
    for per in tot.values():
        for ch, acc in per.items():
            a = avg.setdefault(ch, {k: 0.0 for k in KINDS})
            for k in KINDS:
                a[k] += acc[k] / 60.0 / n
    whole = {name: sum(sum(a.values()) for a in per.values()) / 60.0 for name, per in tot.items()}
    md = '--md' in sys.argv
    if md:
        print('| Глава | ' + ' | '.join(NAMES[k] for k in KINDS) + ' | всего |')
        print('|---|' + '---|' * (len(KINDS) + 1))
    for ch in sorted(avg):
        a = avg[ch]
        cells = ['%.1f' % a[k] for k in KINDS] + ['%.1f' % sum(a.values())]
        print(('| %d | ' % ch + ' | '.join(cells) + ' |') if md else ('гл.%d  ' % ch + '  '.join('%s %s' % (k, c) for k, c in zip(KINDS + ['всего'], cells))))
    s = {k: sum(avg[ch][k] for ch in avg) for k in KINDS}
    cells = ['%.1f' % s[k] for k in KINDS] + ['%.1f' % sum(s.values())]
    print(('| **все** | ' + ' | '.join(cells) + ' |') if md else ('все   ' + '  '.join('%s %s' % (k, c) for k, c in zip(KINDS + ['всего'], cells))))
    lo, hi = min(whole.values()), max(whole.values())
    print('маршрутов %d; вся игра %.0f–%.0f мин (среднее %.0f); короче всех: %s, дольше всех: %s' % (
        n, lo, hi, sum(whole.values()) / n, min(whole, key=whole.get), max(whole, key=whole.get)))
    return 0


if __name__ == '__main__':
    sys.exit(main())
