#!/usr/bin/env python3
"""WHAT THE LAKE KEEPS — аудит сценария по данным игры (godot/data/ch1..ch5.json), как их читает game.gd.

  python3 tools/story_audit.py dump [N] [--en]   сценарий главы N (или всех) коротко: реплики с ID, ремарки, выборы, условия
  python3 tools/story_audit.py graph             граф сцен: входы/выходы, недостижимые, тупики
  python3 tools/story_audit.py flags             флаги: где меняются, где читаются (данные + код постановки)
  python3 tools/story_audit.py explore           проход по ВСЕМ веткам с флагами: кто говорит/упоминается, когда его нет рядом
  python3 tools/story_audit.py check             то же, что explore + graph, код выхода 1 при ошибках (для тестов)
  python3 tools/story_audit.py md DIR            таблицы для docs/ (флаги, решения, граф, QTE, присутствие)

Семантика (как в game.gd): шаг с "if", условие ложно — шаг пропущен, переход в "else", если он есть; "set" прибавляет
к флагу; выбор — фильтр вариантов по "if", молчание — отдельный вариант (по таймеру); qte с "fail" — две ветки;
"end": N — карта главы и начало главы N+1. Сцена, у которой шаги кончились без перехода, — тупик (игра встанет)."""
import json, os, re, sys, collections

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
LAKE = os.path.join(ROOT, 'godot')
OPS = ['>=', '<=', '==', '!=', '>', '<']


def load():
    data = json.load(open(os.path.join(LAKE, 'data/ch1.json')))
    data['chapter'] = 1
    chapters = {1: {'start': data['start'], 'ready': True, 'map': data.get('map', [])}}
    owner = {sid: 1 for sid in data['scenes']}
    dup = []
    for n in range(2, 6):
        p = os.path.join(LAKE, 'data/ch%d.json' % n)
        if not os.path.exists(p):
            continue
        ch = json.load(open(p))
        chapters[n] = {'start': ch['start'], 'ready': bool(ch.get('ready')), 'map': ch.get('map', []), 'title': ch.get('title')}
        for k, v in ch['scenes'].items():
            if k in data['scenes']:
                dup.append(k)
            data['scenes'][k] = v
            owner[k] = n
        data['names'].update(ch.get('names', {}))
        data['flags'].update(ch.get('flags', {}))
        data.setdefault('explore', {}).update(ch.get('explore', {}))   # прогулки глав 2–5
    for sid, sc in data['scenes'].items():   # ключ озвучки, как в game.gd: <сцена>_<номер реплики>
        n = 0
        for b in sc['beats']:
            if 'say' in b:
                n += 1
                b['_vo'] = '%s_%d' % (sid, n)
    return data, chapters, owner, dup


def vo_keys(data):
    """Ключ голоса каждой реплики {(сцена, номер шага): ключ}. Озвученная (текст совпадает с data/vo_lines.json) —
    её ключ (файл snd/vo/<язык>/<ключ>.ogg); новая — <сцена>_n<номер реплики>, чтобы не затереть старые файлы."""
    p = os.path.join(LAKE, 'data/vo_lines.json')
    idx = json.load(open(p)) if os.path.exists(p) else {}
    by = {}
    for k, v in idx.items():
        by.setdefault((v['scene'], v['who'], v.get('ru')), k)
    keys = {}
    for sid, sc in data['scenes'].items():
        n = 0
        for i, b in enumerate(sc['beats']):
            if 'say' in b:
                n += 1
                keys[(sid, i)] = by.get((sid, b['say'], b['ru'])) or '%s_n%d' % (sid, n)
    return keys


def cond_ok(c, flags):
    for k, e in c.items():
        v = float(flags.get(k, 0))
        e = str(e).strip()
        op = ''
        for o in OPS:
            if e.startswith(o):
                op = o
                break
        n = float(e[len(op):])
        ok = {'>=': v >= n, '<=': v <= n, '!=': v != n, '>': v > n, '<': v < n}.get(op, v == n)
        if not ok:
            return False
    return True


def cond_s(c):
    return ', '.join('%s%s' % (k, v if str(v)[:1] in '<>=!' else '==' + str(v)) for k, v in c.items())


def nested(b, data):
    """Шаги внутри прогулки ("explore": точки, разговоры, выход) и разговора с выбором тем ("ask") — все подряд.
    Условие точки или темы (`if`) переносится в её шаги (копии): тема «Зоя, откуда ты?» есть только при Зое — её реплики
    не «говорит тот, кого нет рядом» (раньше условие темы терялось — ложные находки аудита)."""
    out = []

    def add(beats, cond):
        for nb in beats:
            if cond:
                c = dict(cond)
                c.update(nb.get('if') or {})
                nb = dict(nb)
                nb['if'] = c
            out.append(nb)

    def ask(q, cond):
        for o in q.get('opts', []):
            c = dict(cond or {})
            c.update(o.get('if') or {})
            add(o.get('beats', []), c)
        add(q.get('end', []), cond)

    if 'explore' in b:
        d = data.get('explore', {}).get(b['explore'], {})
        add(d.get('setup', []) + d.get('intro', []), None)
        for s in d.get('spots', []) + ([d['exit']] if d.get('exit') else []):
            c = s.get('if') or {}
            add(s.get('beats', []), c)
            if s.get('ask'):
                ask(s['ask'], c)
            add(s.get('after', []), c)
        for z in d.get('zones', []):
            add(z.get('beats', []), z.get('if') or {})
        add(d.get('nudge', {}).get('beats', []), None)
    if 'ask' in b:
        ask(b['ask'], None)
    return out


def txt(d, lang='ru'):
    if isinstance(d, dict):
        return d.get(lang, d.get('ru', ''))
    return str(d) if d is not None else ''


def edges(sc):
    """(куда, как) из сцены — без учёта условий."""
    out = []
    for i, b in enumerate(sc['beats']):
        if 'else' in b and b['else']:
            out.append((b['else'], 'else#%d' % i))
        if 'choice' in b:
            for j, o in enumerate(b['choice']['opts']):
                if o.get('go'):
                    out.append((o['go'], ('молчание' if o.get('silent') else 'выбор') + '#%d.%d' % (i, j)))
        if 'peek' in b:
            out.append((b['yes'], 'peek-да#%d' % i))
            out.append((b['no'], 'peek-нет#%d' % i))
        if 'qte' in b and b.get('fail'):
            out.append((b['fail'], 'qte-провал#%d' % i))
        if b.get('go'):
            out.append((b['go'], 'go#%d' % i))
    return out


def chapter_next(chapters, n):
    nx = chapters.get(n + 1)
    return nx['start'] if nx and nx['ready'] else None


# ---------- dump ----------

def dump(data, owner, chap=None, en=False):
    out = []
    for sid, sc in data['scenes'].items():
        if chap and owner[sid] != chap:
            continue
        tags = []
        if sc.get('save'):
            tags.append('сохр.')
        if sc.get('time'):
            tags.append(sc['time'])
        out.append('\n## %s %s' % (sid, ' '.join('[%s]' % t for t in tags)))
        for i, b in enumerate(sc['beats']):
            cnd = ' {ЕСЛИ %s}' % cond_s(b['if']) if 'if' in b else ''
            els = ' иначе→%s' % b['else'] if b.get('else') else ''
            fx = ' [fx:%s]' % b['fx'] if 'fx' in b else ''
            parts = []
            if 'set' in b:
                parts.append('ФЛАГИ ' + ', '.join('%s%+d' % (k, v) for k, v in b['set'].items()))
            if 'toast' in b:
                parts.append('«Айрис это запомнит»')
            if 'say' in b:
                nm = b.get('name') or data['names'].get(b['say'], b['say'])
                nm = txt(nm)
                line = '%s [%s]: %s' % (nm, b['_vo'], b['ru'])
                if en:
                    line += '  /EN: ' + b['en']
                parts.append(line)
            for k in ('note', 'text'):
                if isinstance(b.get(k), dict):
                    line = ('РЕМАРКА: ' if k == 'note' else 'ТИТР: ') + b[k]['ru']
                    if en:
                        line += '  /EN: ' + b[k]['en']
                    parts.append(line)
            if 'choice' in b:
                c = b['choice']
                ol = []
                for j, o in enumerate(c['opts']):
                    oc = ' {ЕСЛИ %s}' % cond_s(o['if']) if 'if' in o else ''
                    if o.get('silent'):
                        ol.append('    [молчание]%s → %s' % (oc, o.get('go')))
                    else:
                        s = '    [%d] %s%s%s → %s' % (j + 1, o['ru'], ('  /EN: ' + o['en']) if en else '', oc, o.get('go'))
                        ol.append(s)
                parts.append('ВЫБОР (%s с):\n%s' % (c.get('t', 6), '\n'.join(ol)))
            if 'peek' in b:
                parts.append('ВЗГЛЯД (%s с) да→%s нет→%s' % (b.get('t', 5), b['yes'], b['no']))
            if 'qte' in b:
                parts.append('ДЕЙСТВИЕ %s t=%s n=%s%s «%s»%s' % (b['qte'], b.get('t'), b.get('n', '-'),
                             (' gap=%s' % b['gap']) if 'gap' in b else '', txt(b.get('hint')),
                             (' провал→' + b['fail']) if b.get('fail') else ''))
            if b.get('go'):
                parts.append('→ ' + b['go'])
            if b.get('end'):
                parts.append('КОНЕЦ ГЛАВЫ %s' % b['end'])
            if not parts:
                if cnd or els:
                    parts.append('(шаг постановки)')
                else:
                    continue
            out.append('%3d %s%s%s%s' % (i, ' | '.join(parts), cnd, els, fx))
    return '\n'.join(out)


# ---------- граф ----------

def graph(data, chapters, owner):
    sc = data['scenes']
    inc = collections.defaultdict(list)
    bad = []
    for sid, s in sc.items():
        for to, how in edges(s):
            if to not in sc:
                bad.append('%s: переход в несуществующую сцену %s (%s)' % (sid, to, how))
            inc[to].append((sid, how))
        for b in s['beats']:
            if b.get('end'):
                nx = chapter_next(chapters, int(b['end']))
                if nx:
                    inc[nx].append((sid, 'конец главы'))
    seen = set()
    stack = [data['start']]
    while stack:
        x = stack.pop()
        if x in seen or x not in sc:
            continue
        seen.add(x)
        for to, _ in edges(sc[x]):
            stack.append(to)
        for b in sc[x]['beats']:
            if b.get('end'):
                nx = chapter_next(chapters, int(b['end']))
                if nx:
                    stack.append(nx)
    unreach = [s for s in sc if s not in seen]
    return inc, unreach, bad


# ---------- флаги ----------

FLAG_RE = re.compile(r'(?:on\(g,\s*"|flags\.get\("|flags\["|_f\(")([a-z0-9_]+)"')


def code_reads():
    res = collections.defaultdict(set)
    for dp, _, fs in os.walk(os.path.join(LAKE, 'scripts')):
        for f in fs:
            if f.endswith('.gd') and 'dev' not in dp:
                src = open(os.path.join(dp, f)).read()
                for m in FLAG_RE.finditer(src):
                    res[m.group(1)].add(f)
                if 'end_' in src and f == 'game.gd':
                    for k in ['end_witness', 'end_shadow', 'end_ice', 'end_mama', 'end_oblivion']:
                        res[k].add(f)
    return res


def flag_index(data, chapters):
    sets = collections.defaultdict(list)
    reads = collections.defaultdict(list)
    for sid, s in data['scenes'].items():
        for i, b in enumerate(s['beats']):
            if 'set' in b:
                for k, v in b['set'].items():
                    sets[k].append('%s#%d(%+d)' % (sid, i, v))
            if 'if' in b:
                for k in b['if']:
                    reads[k].append('%s#%d' % (sid, i))
            if 'choice' in b:
                for o in b['choice']['opts']:
                    for k in o.get('if', {}):
                        reads[k].append('%s#%d(вариант)' % (sid, i))
        for i, b in enumerate(s['beats']):   # прогулки и разговоры с темами: точки, темы, зоны, преграды
            for where, x in walk_parts(b, data):
                for k, v in (x.get('set') or {}).items():
                    sets[k].append('%s#%d(%s)' % (sid, i, where))
                for k in (x.get('if') or {}):
                    reads[k].append('%s#%d(%s)' % (sid, i, where))
    for n, ch in chapters.items():
        for r in ch.get('map', []):
            for o in r.get('opts', []):
                for k in (o.get('if') or {}):
                    reads[k].append('карта гл.%d' % n)
    return sets, reads


def walk_parts(b, data):
    """(где, словарь) — все шаги и условия внутри прогулки или разговора с темами шага b, с подписью места."""
    out = []

    def deep(where, x):
        if isinstance(x, dict):
            if isinstance(x.get('set'), dict) or isinstance(x.get('if'), dict):
                out.append((where, x))
            for v in x.values():
                deep(where, v)
        elif isinstance(x, list):
            if len(x) >= 3 and isinstance(x[-1], dict) and all(isinstance(v, (int, float)) for v in x[:-1]):
                out.append((where + ' [преграда]', {'if': x[-1]}))   # прямоугольник с условием (block, зона)
            for v in x:
                deep(where, v)

    if 'explore' in b:
        d = data.get('explore', {}).get(b['explore'], {})
        for k, v in d.items():
            if k == 'spots':
                for sp in v:
                    deep('%s.%s' % (b['explore'], sp.get('id', '?')), sp)
            elif k == 'exit':
                deep('%s.выход' % b['explore'], v)
            else:
                deep('%s.%s' % (b['explore'], k), v)
    if 'ask' in b:
        deep('разговор', b['ask'])
    return out


def walk_flags(data):
    """Флаги, которые ставят только прогулки и разговоры: точку и тему можно пропустить — в проходе такой флаг «любой»
    (условие по нему — обе ветки). Раньше он в проходе всегда был 0, и ветки «осмотрел точку» не проверялись."""
    inwalk, inscene = set(), set()
    for s in data['scenes'].values():
        for b in s['beats']:
            inscene |= set(b.get('set') or {})
            for _, x in walk_parts(b, data):
                inwalk |= set(x.get('set') or {})
    return inwalk - inscene


# ---------- проход по всем веткам ----------
# Кто рядом с Ноем (может говорить/быть упомянут как присутствующий) — по флагам. Модель — docs/BRANCH_CONTINUITY.md.

def present(who, f, sid):
    g = lambda k: float(f.get(k, 0)) > 0
    ch = int(sid[1]) if sid.startswith('c') and sid[1].isdigit() else 1
    if who == 'zoe':
        if sid.startswith('c5_visit'):   # эпилог через месяцы: заложниц уже отпустили, рядом — если жива и не ушла
            return g('zoe') and not g('zoe_gone') and not g('zoe_died')
        return g('zoe') and not g('zoe_gone') and not g('zoe_taken') and not g('zoe_taken4') and not g('zoe_died')
    if who == 'danny':
        if g('danny_hale') and sid.startswith(('c5_shore', 'c5_oblivion')):   # заложник Хейла на берегу (chapter5.gd ставит его у седана)
            return True
        return g('danny') and not g('danny_lost') and not g('danny_hale')   # danny_stayed — остался с ними (гл. 4)
    if who == 'eva':
        if ch == 2:
            return g('eva')
        return (g('eva') or g('eva_found')) and not g('eva_dead')
    if who == 'agnes':   # с её дома (c3_s2); до встречи в гл. 3 её рядом нет
        met = ch >= 4 or (ch == 3 and sid != 'c3_recap' and not sid.startswith('c3_s1'))
        return met and not g('agnes_dead')
    return True


PRESENCE_KEYS = {'eva_alone', 'danny_left', 'zoe_passed', 'eva_alive', 'baby', 'zoe', 'zoe_gone', 'zoe_taken', 'zoe_taken4', 'zoe_died', 'danny', 'danny_lost', 'danny_hale', 'danny_stayed',
                 'eva', 'eva_found', 'eva_dead', 'agnes_dead', 'end_witness', 'end_shadow', 'end_ice', 'end_mama', 'end_oblivion'}
COUNTERS = {'trust', 'shadow', 'memory', 'proof', 'ptotal', 'romance', 'hard', 'zoe_trust'}   # числа с порогами — в проходе «любое»
# Журнальные флаги событий-откликов (tools/story/events16.py): читаются только условиями реплик, сюжет не ветвят —
# в проходе «любые» (обе ветки реплик проверяются на присутствие героев), иначе состояний вдвое-втрое больше (память).
COUNTERS |= {'danny_refused', 'said_ok', 'kitchen_exit', 'enough_both', 'book_left', 'hut_shut', 'hut_creak', 'hut_still',
             'danny_unanswered', 'danny_try'}

# Где герой есть в кадре, даже если ещё не едет с Ноем (встреча, его дом, засада) — сцены по началу ID.
ENCOUNTER = {
    'danny': ['s5', 's6'],
    'zoe': ['c2_s2_stop', 'c2_s2_back', 'c2_s2_front', 'c2_s4_zoe', 'c5_shore', 'c5_zoe'],   # гл. 5: заложницу выпускают на берегу
    'eva': ['c2_s4', 'c2_s6', 'c3_s1_evacar'],   # в кафе Ева сидит в углу с самого входа (chapter2.gd, cafe())
    'roman': ['s5'],
    'trucker': ['c2_s4'],
    'cop': ['c2_s3'],
}
NAMES = {'danny': r'Дэнни', 'zoe': r'Зо[яиюе]|Зоей', 'eva': r'Ев[аыеуо]|Евой', 'agnes': r'Агнес', 'hale': r'Хейл'}


# На границе глав проход помнит точно только присутствие героев и эти факты; прочие флаги прошлых глав — «любые».
KEEP = set()   # через границу главы — только присутствие героев (PRESENCE_KEYS); иначе состояний слишком много
SET_CH = {}   # флаг → главы, где он меняется (заполняет explore)
CUR_CH = [1]


WALKF = set()   # флаги только из прогулок и разговоров (walk_flags): «может быть», если точка, которая их ставит, доступна


def unknown_flag(k):
    if k in COUNTERS:
        return True
    return k not in KEEP and k not in PRESENCE_KEYS and any(c < CUR_CH[0] for c in SET_CH.get(k, ()))


def cond3(c, f):
    """True / False / None (зависит от «любого» флага: проход идёт по обеим веткам)."""
    unknown = False
    for k, e in c.items():
        if unknown_flag(k) or (k in WALKF and f.get('~' + k)):
            unknown = True
            continue
        if not cond_ok({k: e}, f):
            return False
    return None if unknown else True


def explore(data, chapters, owner, limit=8_000_000, check_who=None):
    """Все ветки: флаги-события — точно, счётчики (доверие, «Тень», память…) — любые (условие по ним — обе ветки).
    Состояние на входе в сцену объединяется по живым флагам. Возвращает нарушения присутствия, упоминания отсутствующих,
    тупики, концовки и покрытие сцен."""
    sc = data['scenes']
    _, reads = flag_index(data, chapters)
    always = set()   # флаги, которые читает только код постановки (кто виден в кадре), — отдельная ручная проверка
    succ = {sid: set(t for t, _ in edges(s)) for sid, s in sc.items()}
    for sid, s in sc.items():
        for b in s['beats']:
            if b.get('end'):
                nx = chapter_next(chapters, int(b['end']))
                if nx:
                    succ[sid].add(nx)
    local = {}
    for sid, s in sc.items():
        r = set()
        for b in s['beats']:
            r |= set(b.get('if', {}))
            for o in b.get('choice', {}).get('opts', []):
                r |= set(o.get('if', {}))
        local[sid] = r
    for n, ch in chapters.items():
        mr = set()
        for r in ch.get('map', []):
            for o in r.get('opts', []):
                mr |= set((o.get('if') or {}).keys())
        for sid, s in sc.items():
            if any(b.get('end') == n for b in s['beats']):
                local[sid] |= mr
    live = {sid: set(local[sid]) for sid in sc}   # живые флаги внутри главы (через границу главы идут только KEEP)
    changed = True
    while changed:
        changed = False
        for sid in sc:
            nl = set(live[sid])
            for t in succ[sid]:
                if t in live and owner.get(t) == owner[sid]:
                    nl |= live[t]
            if nl != live[sid]:
                live[sid] = nl
                changed = True
    SET_CH.clear()
    for sid, s in sc.items():
        for b in s['beats']:
            for k in b.get('set', {}):
                SET_CH.setdefault(k, set()).add(owner[sid])
            for _, x in walk_parts(b, data):
                for k in (x.get('set') or {}):
                    SET_CH.setdefault(k, set()).add(owner[sid])
    starts = {ch['start']: n for n, ch in chapters.items()}

    keep_cache = {}

    def key(sid, f):
        CUR_CH[0] = owner[sid]
        keep = keep_cache.get(sid)
        if keep is None:
            keep = keep_cache[sid] = frozenset(k for k in (live[sid] | PRESENCE_KEYS | KEEP) if not unknown_flag(k) or k in PRESENCE_KEYS) | \
                frozenset('~' + k for k in live[sid] if k in WALKF and not unknown_flag(k))
        return sid, tuple(sorted((k, v) for k, v in f.items() if k in keep and v))

    def here(who, f, sid):
        if any(sid == p or sid.startswith(p + '_') for p in ENCOUNTER.get(who, [])):
            return True
        return present(who, f, sid)

    seen = set()
    viol = collections.OrderedDict()
    ment = collections.OrderedDict()
    deadends = collections.OrderedDict()
    endings = collections.Counter()
    end_states = collections.defaultdict(list)
    visits = collections.Counter()
    presence_at = {}   # сцена → герой → {True/False}: бывает ли он рядом на входе в сцену
    states = 0
    WALKF.clear()
    WALKF.update(walk_flags(data))
    stack = collections.deque([(data['start'], {k: v for k, v in data['flags'].items() if k not in COUNTERS}, ())])
    while stack:
        sid, f, path = stack.popleft()   # в ширину: главы по порядку, путь-пример — самый короткий
        if sid not in sc:
            continue
        if sid in starts:   # новая глава: прошлое — только присутствие и KEEP
            CUR_CH[0] = starts[sid]
            f = {kk: v for kk, v in f.items() if not unknown_flag(kk) and not kk.startswith('~')}
        k = key(sid, f)
        if k in seen:
            continue
        seen.add(k)
        states += 1
        if states > limit:
            print('!! предел состояний', limit, file=sys.stderr)
            break
        visits[sid] += 1
        pres = presence_at.setdefault(sid, {})
        for w in ('zoe', 'danny', 'eva', 'agnes'):
            pres.setdefault(w, set()).add(here(w, f, sid))
        path = path + (sid,)
        todo = [(0, f)]
        while todo:
            i, f = todo.pop()
            beats = sc[sid]['beats']
            fell = True
            while i < len(beats):
                b = beats[i]
                if 'if' in b:
                    r = cond3(b['if'], f)
                    if r is None:   # обе ветки: шаг выполнен — дальше здесь; шаг пропущен — отдельно
                        if b.get('else'):
                            stack.append((b['else'], f, path))
                        else:
                            todo.append((i + 1, f))
                    elif r is False:
                        if b.get('else'):
                            stack.append((b['else'], f, path))
                            fell = False
                            break
                        i += 1
                        continue
                if 'set' in b:
                    f = dict(f)
                    for kk, v in b['set'].items():
                        if kk not in COUNTERS:
                            f[kk] = f.get(kk, 0) + int(v)
                if 'say' in b:
                    who = b['say']
                    if (not check_who or who in check_who) and not here(who, f, sid):
                        viol.setdefault((sid, i, who), path)
                for fld in ('ru', 'note', 'text'):
                    t = b.get(fld)
                    t = t.get('ru', '') if isinstance(t, dict) else (t if isinstance(t, str) else '')
                    for w, rx in NAMES.items():
                        if t and re.search(rx, t) and not here(w, f, sid):
                            ment.setdefault((sid, i, w), path)
                inner = nested(b, data)
                if inner:   # прогулка или разговор: флаг точки «может быть», если её условие не ложно (до неподвижной точки)
                    grew = True
                    while grew:
                        grew = False
                        for nb in inner:
                            for kk in (nb.get('set') or {}):
                                if kk in WALKF and not f.get('~' + kk) and cond3(nb.get('if') or {}, f) is not False:
                                    f = dict(f)
                                    f['~' + kk] = 1
                                    grew = True
                for nb in inner:   # прогулка и разговор: кто говорит — должен быть рядом (условия шага — как есть)
                    if 'if' in nb and cond3(nb['if'], f) is False:
                        continue
                    if 'say' in nb and (not check_who or nb['say'] in check_who) and not here(nb['say'], f, sid):
                        viol.setdefault((sid, i, nb['say']), path)
                if 'choice' in b:
                    for o in b['choice']['opts']:
                        if 'if' in o and cond3(o['if'], f) is False:
                            continue
                        for w, rx in NAMES.items():
                            if re.search(rx, o.get('ru', '')) and not here(w, f, sid):
                                ment.setdefault((sid, i, w), path)
                        stack.append((o.get('go', ''), f, path))
                    fell = False
                    break
                if 'peek' in b:
                    stack.append((b['yes'], f, path))
                    stack.append((b['no'], f, path))
                    fell = False
                    break
                if 'qte' in b and b.get('fail'):
                    stack.append((b['fail'], f, path))
                if b.get('go'):
                    stack.append((b['go'], f, path))
                    fell = False
                    break
                if b.get('end'):
                    nx = chapter_next(chapters, int(b['end']))
                    if nx:
                        stack.append((nx, f, path))
                    else:
                        cur = 'oblivion'
                        for e in ['mama', 'shadow', 'ice', 'witness']:
                            if float(f.get('end_' + e, 0)) > 0:
                                cur = e
                                break
                        endings[cur] += 1
                        if len(end_states[cur]) < 3:
                            end_states[cur].append(path)
                    fell = False
                    break
                i += 1
            if fell:
                deadends.setdefault(sid, path)
    return dict(seen=seen, viol=viol, ment=ment, deadends=deadends, endings=endings, end_states=end_states,
                visits=visits, states=states, live=live, presence_at=presence_at)


def A_txt(d):
    return d.get('ru', '') if isinstance(d, dict) else ''


def main():
    data, chapters, owner, dup = load()
    cmd = sys.argv[1] if len(sys.argv) > 1 else 'check'
    if cmd == 'dump':
        n = int(sys.argv[2]) if len(sys.argv) > 2 and sys.argv[2].isdigit() else None
        print(dump(data, owner, n, '--en' in sys.argv))
        return 0
    if cmd == 'graph' or cmd == 'check':
        inc, unreach, bad = graph(data, chapters, owner)
        print('сцен: %d, повторов ID между главами: %d %s' % (len(data['scenes']), len(dup), dup))
        print('недостижимые (без учёта условий):', unreach)
        for x in bad:
            print('ОШИБКА', x)
        noin = [s for s in data['scenes'] if s not in inc and s != data['start']]
        print('без входа:', noin)
    if cmd == 'flags':
        sets, reads = flag_index(data, chapters)
        creads = code_reads()
        allf = sorted(set(data['flags']) | set(sets) | set(reads) | set(creads))
        for k in allf:
            print('%-18s нач=%s  меняют: %s  читают: %s  код: %s' % (k, data['flags'].get(k, '—'), ' '.join(sets.get(k, [])) or '—',
                  ' '.join(reads.get(k, [])) or '—', ','.join(sorted(creads.get(k, []))) or '—'))
        print('\nменяются, но нигде не читаются:', [k for k in allf if k in sets and k not in reads and k not in creads])
        print('читаются, но нигде не меняются:', [k for k in allf if k in reads and k not in sets and not data['flags'].get(k)])
        print('не объявлены в "flags" глав:', [k for k in allf if k not in data['flags']])
        return 0
    if cmd in ('explore', 'check'):
        r = explore(data, chapters, owner)
        import pickle
        pickle.dump({'presence_at': r['presence_at'], 'endings': dict(r['endings']), 'states': r['states'],
                     'viol': list(r['viol'].items()), 'ment': list(r['ment'].items()), 'deadends': list(r['deadends']),
                     'visits': dict(r['visits']), 'end_states': dict(r['end_states'])},
                    open(os.environ.get('LAKE_EXPLORE_OUT', '/tmp/lake_explore.pkl'), 'wb'))
        print('состояний: %d, сцен пройдено: %d из %d' % (r['states'], len(r['visits']), len(data['scenes'])))
        print('не пройдены ни одной веткой:', [s for s in data['scenes'] if s not in r['visits']])
        print('концовки (число разных путей до конца):', dict(r['endings']))
        print('тупики (шаги кончились без перехода):', list(r['deadends']))
        print('говорит тот, кого нет рядом: %d' % len(r['viol']))
        for (sid, i, who), path in r['viol'].items():
            b = data['scenes'][sid]['beats'][i]
            print('  %s#%d %s: «%s»  путь: …%s' % (sid, i, who, b.get('ru', '')[:70], ' → '.join(path[-6:])))
        print('упомянут (в ремарке/реплике/варианте), хотя его нет рядом — проверить вручную: %d' % len(r['ment']))
        for (sid, i, who), path in r['ment'].items():
            b = data['scenes'][sid]['beats'][i]
            t = b.get('ru') or A_txt(b.get('note') or b.get('text')) or '(выбор)'
            print('  %s#%d %s: «%s»  путь: …%s' % (sid, i, who, t[:80], ' → '.join(path[-5:])))
        if cmd == 'check':
            return 1 if (r['viol'] or r['deadends']) else 0
    if cmd == 'md':
        print('см. docs/*.md — таблицы собираются tools/story_tables.py')
    return 0


if __name__ == '__main__':
    sys.exit(main())
