"""WHAT THE LAKE KEEPS — ключи голоса для реплик ВНУТРИ прогулок (точки, разговоры, выход, зоны, «поторопить») и
разговоров с темами (`ask`) в сценах. Озвучка сцен (tools/voice.py) раньше видела только реплики верхнего уровня
сцены; вложенные шли без голоса. Теперь у каждой такой реплики свой ключ `vo` (game.gd::_vo_key берёт его первым):
  <прогулка>_<точка>_<6 знаков от текста>   — прогулки;   <сцена>_a<6 знаков от текста> — разговоры в сценах.
Ключ зависит от героя и русского текста: вставили или убрали реплику — ключи остальных не съезжают; поменяли текст —
у реплики новый ключ, старый голос не играет, пока её не переозвучат (как у реплик сцен в data/vo_lines.json)."""
import hashlib


def _h(b):
    return hashlib.sha1(('%s|%s' % (b.get('say', ''), b.get('ru', ''))).encode('utf-8')).hexdigest()[:6]


def _ask_beats(q):
    out = []
    for o in (q or {}).get('opts', []):
        out.extend(o.get('beats', []))
    out.extend((q or {}).get('end', []))
    return out


def assign(j, explores=None):
    """Проставить `vo` всем вложенным репликам главы j (словарь chN.json). explores — какие прогулки (None — все)."""
    for ex_id, d in j.get('explore', {}).items():
        if explores is not None and ex_id not in explores:
            continue
        groups = [('intro', d.get('setup', []) + d.get('intro', []))]
        for s in d.get('spots', []) + ([d['exit']] if d.get('exit') else []):
            groups.append((s['id'], s.get('beats', []) + _ask_beats(s.get('ask')) + s.get('after', [])))
        for i, z in enumerate(d.get('zones', [])):
            groups.append(('z%d' % i, z.get('beats', [])))
        groups.append(('nudge', d.get('nudge', {}).get('beats', [])))
        for gid, beats in groups:
            for b in beats:
                if 'say' in b:
                    b['vo'] = '%s_%s_%s' % (ex_id, gid, _h(b))
    for sid, sc in j['scenes'].items():
        for b in sc['beats']:
            if 'ask' in b:
                for bb in _ask_beats(b['ask']):
                    if 'say' in bb:
                        bb['vo'] = '%s_a%s' % (sid, _h(bb))
    return j
