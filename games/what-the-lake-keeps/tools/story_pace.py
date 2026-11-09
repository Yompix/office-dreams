#!/usr/bin/env python3
"""WHAT THE LAKE KEEPS — темп сюжета для аудита (ничего в игре не меняет): сцены с крупным событием (по флагам —
смерть, пропажа, роды, погоня, лёд…), которые проходят быстрее 45 с, и выборы с таймером.
  python3 tools/story_pace.py"""
import json, os, sys, re
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import story_audit as A
data, chapters, owner, _ = A.load()
keys = A.vo_keys(data)
vo = json.load(open(os.path.join(A.LAKE, 'snd/vo/ru/timing.json')))
MAJOR = re.compile(r'dead|dies|lost|taken|baby|birth|police|crash|ice|hurt|shot|kill|drown|chase|attack|ditch|deer|fire|storm', re.I)
rows = []
timers = []
for sid, sc in data['scenes'].items():
    sec = 0.0; flags = set(); first = ''
    for i, b in enumerate(sc['beats']):
        if 'say' in b:
            k = keys.get((sid, i)); sec += float(vo[k]['dur']) + 0.35 if k in vo else max(1.4, min(9.0, 0.9 + len(b.get('ru', '')) / 15.0))
        elif 'note' in b or 'text' in b or 'dur' in b:
            sec += float(b.get('dur', 2.0 if 'note' in b else 2.5))
            if not first and isinstance(b.get('note'), dict): first = b['note'].get('ru', '')
        if 'choice' in b:
            t = float(b['choice'].get('t', 6)); sec += t / 2; timers.append((t, sid))
        if 'qte' in b: sec += float(b.get('t', 3)) * 0.7
        for k in (b.get('set') or {}): flags.add(k)
        for o in (b.get('choice') or {}).get('opts', []):
            for k in (o.get('set') or {}): flags.add(k)
    maj = sorted(f for f in flags if MAJOR.search(f))
    if maj:
        rows.append((owner[sid], sid, sec, maj, first[:70]))
print('== сцены с крупными событиями (по флагам), короче 45 с')
for ch, sid, sec, maj, first in sorted(rows, key=lambda r: r[2]):
    if sec < 45: print('гл.%d %-22s %5.1f с  %s  «%s»' % (ch, sid, sec, ','.join(maj), first))
print('== таймеры выборов: распределение')
from collections import Counter
c = Counter(int(t) for t, _ in timers); print(sorted(c.items()))
print('короткие (<=4 с):', [(t, s) for t, s in timers if t <= 4][:20])
