#!/usr/bin/env python3
"""WHAT THE LAKE KEEPS — что поменялось в сценарии между двумя версиями данных (для docs/DIALOGUE_REVISIONS.md).
  python3 tools/lake/story_diff.py СТАРАЯ_ПАПКА_DATA [НОВАЯ_ПАПКА_DATA] [--md]
Сравнивает по сценам: реплики, ремарки, титры, варианты выбора, условия. Ключ реплики — как у озвучки (<сцена>_<номер>)."""
import json, os, sys, difflib

HERE = os.path.dirname(os.path.abspath(__file__))


def load(d):
    sc, names = {}, {}
    for n in range(1, 6):
        p = os.path.join(d, 'ch%d.json' % n)
        if os.path.exists(p):
            j = json.load(open(p))
            sc.update(j['scenes'])
            names.update(j.get('names', {}))
    return sc, names


VO = {}
_vp = os.path.join(HERE, '../../godot/lake/data/vo_lines.json')
if os.path.exists(_vp):
    for _k, _v in json.load(open(_vp)).items():
        VO.setdefault((_v['scene'], _v['who'], _v.get('ru', '')), _k)
        VO.setdefault(('', _v['who'], _v.get('ru', '')), _k)


def items(sid, beats):
    """Текстовые единицы сцены: (вид, кто, ru, en, условие, ID). ID реплики — ключ её озвучки (по тексту),
    у новой реплики — NEW:<сцена>.<номер реплики в сцене>."""
    out = []
    n = 0
    for i, b in enumerate(beats):
        cond = b.get('if')
        if 'say' in b:
            n += 1
            vid = VO.get((sid, b['say'], b['ru'])) or VO.get(('', b['say'], b['ru'])) or 'NEW:%s.%d' % (sid, n)
            out.append(('реплика', b['say'], b['ru'], b['en'], cond, vid))
        for k, kind in (('note', 'ремарка'), ('text', 'титр')):
            if isinstance(b.get(k), dict):
                out.append((kind, '', b[k]['ru'], b[k]['en'], cond, '%s#%d' % (sid, i)))
        if 'choice' in b:
            for j, o in enumerate(b['choice']['opts']):
                if not o.get('silent'):
                    out.append(('вариант', '', o['ru'], o['en'], o.get('if'), '%s#%d.%d' % (sid, i, j + 1)))
        if 'qte' in b:
            h = b.get('hint') or {}
            out.append(('действие', b['qte'], h.get('ru', ''), h.get('en', ''), cond, '%s#%d t=%s n=%s' % (sid, i, b.get('t'), b.get('n'))))
    return out


def cs(c):
    return ', '.join('%s %s' % (k, v) for k, v in (c or {}).items()) or '—'


def main():
    old, names = load(sys.argv[1])
    new, names2 = load(sys.argv[2] if len(sys.argv) > 2 and not sys.argv[2].startswith('--') else os.path.join(HERE, '../../godot/lake/data'))
    names.update(names2)
    rows = []
    for sid in list(old) + [s for s in new if s not in old]:
        a = items(sid, old.get(sid, {'beats': []})['beats'])
        b = items(sid, new.get(sid, {'beats': []})['beats'])
        ka = [(x[0], x[1], x[2], x[3], json.dumps(x[4], sort_keys=True)) for x in a]
        kb = [(x[0], x[1], x[2], x[3], json.dumps(x[4], sort_keys=True)) for x in b]
        sm = difflib.SequenceMatcher(a=ka, b=kb, autojunk=False)
        for op, i1, i2, j1, j2 in sm.get_opcodes():
            if op == 'equal':
                continue
            olds, news = a[i1:i2], b[j1:j2]
            for k in range(max(len(olds), len(news))):
                o = olds[k] if k < len(olds) else None
                nn = news[k] if k < len(news) else None
                rows.append((sid, o, nn))
    if sid not in new:
        pass
    for sid, o, nn in rows:
        x = nn or o
        who = x[1] if x[0] == 'реплика' else x[0]
        nm = names.get(who, who)
        nm = nm.get('ru', who) if isinstance(nm, dict) else nm
        print('### %s · %s · %s' % (sid, nm, (o or nn)[5]))
        if o:
            print('- было RU: %s' % o[2])
            print('- было EN: %s' % o[3])
            if o[4]:
                print('- было условие: %s' % cs(o[4]))
        else:
            print('- было: — (новое)')
        if nn:
            print('- стало RU: %s' % nn[2])
            print('- стало EN: %s' % nn[3])
            if nn[4] or (o and o[4]):
                print('- условие: %s' % cs(nn[4]))
        else:
            print('- стало: — (убрано)')
        print()


if __name__ == '__main__':
    main()
