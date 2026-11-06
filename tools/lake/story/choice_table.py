#!/usr/bin/env python3
"""WHAT THE LAKE KEEPS — таблица откликов на решения игрока: каждый вариант каждого выбора — что игрок видит сразу,
какие флаги ставит ветка, где они читаются дальше (сцены, прогулки, карта главы, «Ранее…», код постановки), чем
различаются ветки и как выбор влияет на концовку. Только факты из данных (godot/lake/data/ch1–ch5.json; разбор —
tools/lake/story_audit.py) и кода (godot/lake/scripts/**/*.gd); черновик откликов — docs/FLAG_RESPONSES_DRAFT.md.

  python3 tools/lake/story/choice_table.py             → godot/lake/docs/CHOICE_CONSEQUENCES.md
  python3 tools/lake/story/choice_table.py --out FILE  в другой файл
  python3 tools/lake/story/choice_table.py --check     только сводка в консоль, файл не пишется

Пересобирать после каждой правки данных (story/chN.py, chN_walk.py, gestures.py…): файл руками не править.
Лёгкий (секунды): граф сцен без перебора флагов. Полный перебор веток — story_audit.py check (тяжёлый).

Как считается:
- выбор — шаг "choice" (варианты, "if" — условие показа, "silent" — молчание по таймеру) и "peek" (взгляд в зеркало);
  время вышло → молчание, а без молчания — первый показанный вариант (scripts/ui/hud.gd);
- ветка варианта — от его сцены до следующего выбора или до сцены, куда можно попасть и из других вариантов;
  флаги ветки — "set" в её шагах (с условиями шагов), провал действия ("qte" + "fail") — своя ветка;
- отклик — флаг ветки читается дальше: условие шага/варианта/точки прогулки в сцене, до которой можно дойти
  после выбора (не в той же сцене), карта главы, «Ранее…», код постановки (scripts/*.gd, кроме dev/);
- концовка — экран «Конец» (game.gd::_the_end) по флагам end_*; «решает концовку» — флаг в условии, от которого
  зависит, какие end_* можно поставить дальше (по графу сцен; цепочки — через флаги, которые он ставит)."""
import collections, os, re, sys
sys.dont_write_bytecode = True
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))
import story_audit as A   # noqa: E402

LAKE = os.path.normpath(A.LAKE)
SCRIPTS = os.path.join(LAKE, 'scripts')
OUT = os.path.join(LAKE, 'docs/CHOICE_CONSEQUENCES.md')
DRAFT = os.path.join(LAKE, 'docs/FLAG_RESPONSES_DRAFT.md')
DRAFT_LINK = 'FLAG_RESPONSES_DRAFT.md#решения-игрока--предлагаемые-отклики'
COUNTERS = set(A.COUNTERS)   # числа с порогами: trust, shadow, memory, proof, ptotal, romance, hard, zoe_trust
GENERIC_FX = {'fade_in', 'fade_out', 'black'}
NEG = {'>': '<=', '<': '>=', '>=': '<', '<=': '>', '==': '!=', '!=': '=='}
# В каком файле искать описание эффекта ("fx") и к какой главе относится код постановки (0 — любая)
FX_FILES = {1: ['game.gd', 'station24.gd'], 2: ['chapter2.gd', 'game.gd'], 3: ['chapter3.gd', 'chapter2.gd', 'game.gd'],
            4: ['chapter4.gd', 'game.gd'], 5: ['chapter5.gd', 'chapter4.gd', 'game.gd']}
CODE_CH = {'station24.gd': 1, 'chapter2.gd': 2, 'chapter3.gd': 3, 'chapter4.gd': 4, 'chapter5.gd': 5}
# Что меняет код, читающий флаг: файл:функция[:ветка match] → коротко (сверено с кодом; нет в списке — имя функции)
CODE_DESC = {
    'game.gd:_resume_at': 'загрузка сохранения: что видно в кадре (руки, костяшки, фара, Дэнни в машине)',
    'game.gd:_station': 'заправка: розовая вода в ведре; при опоздании чёрная машина у края площадки и следы шин',
    'game.gd:_lapse_after': 'после провала у кассы: Роман на полу стонет (жив) или не двигается («Тень» ≥ 1)',
    'game.gd:_kids_by_car': 'Дэнни у машины: испуган (после провала) или грустный',
    'game.gd:_life_hints': 'манера Айрис: раскрывается при доверии',
    'station24.gd:dress_trunk': 'багажник: монтировка в бурых пятнах и шерсти (если добил оленя)',
    'station24.gd:_kids_to_car': 'дети идут к машине: Дэнни испуган или грустный; ладонь на кармане с батончиком',
    'station24.gd:_danny_climb_in': 'Дэнни садится в дальний угол (после провала)',
    'station24.gd:_roman_window': 'Роман в витрине при отъезде: звонит / с оборванным шнуром / смотрит вслед / на полу',
    'chapter2.gd:seat_all': 'кто где сидит в машине (гл. 2–3)',
    'chapter2.gd:fx:reflection': 'отражение Ноя в зеркале: «Тень» 1 — моргает невпопад, 2+ — не моргает и улыбается',
    'chapter2.gd:fx:reflection_wc': 'отражение Ноя в зеркале туалета ведёт себя не так, как он («Тень» ≥ 1)',
    'chapter2.gd:fx:sedan_arrive': 'у кафе: Зоя сидит с Ноем на крыльце',
    'chapter2.gd:fx:morning_cafe': 'утро у кафе: Зои нет, её браслет в снегу',
    'chapter2.gd:fx:leave_cafe': 'отъезд от кафе: Ева остаётся, если её не взяли',
    'chapter2.gd:fx:cafe_walk': 'кафе: Зоя курит на крыльце',
    'chapter2.gd:cafe': 'кафе: кто за столом у окна',
    'chapter3.gd:cast': 'кто есть в главе 3',
    'chapter3.gd:in_house': 'дом Агнес: кто в доме',
    'chapter3.gd:_car_away': 'машина у дома Агнес или осталась в сугробе (шли пешком)',
    'chapter3.gd:restore': 'утро в доме: свёрток с младенцем',
    'chapter3.gd:fx:press': 'роды: рану прижимает Ной или Айрис (после провала у магазина)',
    'chapter3.gd:fx:baby_night': 'ночь в доме: плач младенца',
    'chapter3.gd:fx:neighbor': 'уход к соседке: младенец на руках',
    'chapter3.gd:_house_sleepers': 'спящие в доме: Ева с младенцем, младенец у Айрис, Дэнни на печи',
    'chapter3.gd:cellar': 'подпол: младенец с ними',
    'chapter4.gd:cast': 'кто едет в главе 4',
    'chapter4.gd:baby_holder': 'у кого младенец (Ева или Айрис)',
    'chapter4.gd:baby': 'младенец в кадре',
    'chapter4.gd:fx:van_push': 'буханка с толкача: Ева с младенцем в стороне',
    'chapter4.gd:fx:zoe_rough': 'в лесу Зою хватает санитар или сам Хейл (если санитар убит)',
    'chapter4.gd:fx:zoe_pull': 'в лесу Зою тянет санитар или сам Хейл',
    'chapter4.gd:fx:zoe_dragged': 'в лесу Зою уводит санитар или сам Хейл',
    'chapter4.gd:_flash_actor': 'фонарь в лесу несёт санитар или сам Хейл',
    'chapter4.gd:_zoe_grab': 'в лесу Зою хватает санитар или сам Хейл',
    'chapter4.gd:_lake_stage': 'у озера: Агнес с ними или нет; хромает',
    'chapter4.gd:_ice_stage': 'переход по льду: Агнес с ними или нет',
    'chapter4.gd:_ice_done': 'у мостков: Агнес с ними или нет; хромает',
    'chapter5.gd:cast': 'кто на даче',
    'chapter5.gd:fx:stove5': 'печь на даче топит Агнес или Ной',
    'chapter5.gd:fx:agnes_out': 'Агнес выходит на берег',
    'chapter5.gd:fx:crawl': 'Агнес на берегу, пока Ной ползёт к полынье',
    'chapter5.gd:fx:in_water': 'Айрис в воде — или в сарае с малышкой',
    'chapter5.gd:fx:rub_hands': 'Зою растирает Ной или Айрис',
    'chapter5.gd:_sedan': 'седан на берегу: первым выходит санитар',
    'chapter5.gd:_hostages': 'заложники у машины или освобождены',
    'chapter5.gd:_room': 'свидание: Зоя сидит рядом с Айрис',
    'chapter5.gd:_season': 'эпилог: Агнес в кадре',
}
MAP_FUNCS = {'_map_pick', '_chapter_map', '_chapter_map_n'}   # карта главы (и дерево выборов в меню)


# ---------------------------------------------------------------- данные
DATA, CHAPTERS, OWNER, _ = A.load()
SC = DATA['scenes']
WALKS = DATA.get('explore', {})
JSON_IDX = {sid: i for i, sid in enumerate(SC)}


def short(s, n=90):
    s = ' '.join(str(s or '').replace('|', '/').split())
    return s if len(s) <= n else s[:n - 1].rstrip(' ,.;:—') + '…'


def cell(s):
    return str(s).replace('|', '/').replace('\n', ' ')


def who_name(b):
    nm = A.txt(b.get('name') or DATA['names'].get(b['say'], b['say']))
    return nm[:1] + nm[1:].lower() if nm.isupper() else nm


def parse_e(e):
    e = str(e).strip()
    for o in A.OPS:
        if e.startswith(o):
            return o, e[len(o):]
    return '==', e


def cond_txt(c):
    return A.cond_s(c) if c else ''


def neg_txt(c):
    if len(c) == 1:
        k, e = next(iter(c.items()))
        op, n = parse_e(e)
        if k not in COUNTERS and n in ('0', '0.0'):   # флаги-события не бывают меньше 0
            op2 = {'>': '==', '==': '>', '<=': '>', '!=': '=='}.get(op, NEG[op])
        else:
            op2 = NEG[op]
        return '%s%s%s' % (k, op2, n)
    return 'не (%s)' % cond_txt(c)


def delta(k, v):
    return '`%s`%s' % (k, ('+%d' % v) if v >= 0 else ('−%d' % -v))


def plural(n, one, few, many):
    n = abs(int(n))
    if n % 10 == 1 and n % 100 != 11:
        return one
    if 2 <= n % 10 <= 4 and not 12 <= n % 100 <= 14:
        return few
    return many


# ---------------------------------------------------------------- код постановки
def gd_files():
    out = []
    for dp, dns, fs in os.walk(SCRIPTS):
        dns[:] = sorted(d for d in dns if d != 'dev')
        for f in sorted(fs):
            if f.endswith('.gd'):
                out.append((f, open(os.path.join(dp, f), encoding='utf-8').read()))
    return out


GD = gd_files()
GAME = dict(GD).get('game.gd', '')
FUNC_RX = re.compile(r'^\s*(?:static\s+)?func\s+([A-Za-z0-9_]+)')
CASE_RX = re.compile(r'^\s*((?:"[^"\n]+"\s*,\s*)*"[^"\n]+")\s*:\s*(?:#\s*(.*))?$')
LABEL_RX = re.compile(r'^\t\t((?:"[^"\n]+"\s*,\s*)*"[^"\n]+")\s*:')
READ_RX = re.compile(r'(?:\bon\(\s*g\s*,\s*"|\b(?:flags|f)\.get\(\s*"|\bflags\[\s*"|\b_f\(\s*")([a-z0-9_]+)"')


def fx_comments():
    """Комментарий у ветки match эффекта в коде: "deer_look":   # встали в метре: олень смотрит в лобовое."""
    d = {}
    for f, src in GD:
        L = src.split('\n')
        for i, l in enumerate(L):
            m = CASE_RX.match(l)
            if not m:
                continue
            c = (m.group(2) or '').strip()
            if not c and i + 1 < len(L) and L[i + 1].strip().startswith('#'):
                c = L[i + 1].strip().lstrip('#').strip()
            if c:
                for nm in re.findall(r'"([^"]+)"', m.group(1)):
                    d.setdefault((f, nm), c)
    return d


FX_DESC = fx_comments()


def fx_txt(name, ch):
    for f in FX_FILES.get(ch, []) + [f for f, _ in GD]:
        if (f, name) in FX_DESC:
            return '`%s` — %s' % (name, short(FX_DESC[(f, name)], 75))
    return '`%s`' % name


def code_index():
    """Чтения и записи флагов в коде: {флаг: [(файл, строка, функция, ветка match)]}, {флаг: [(файл, функция)]}."""
    reads, writes = collections.defaultdict(list), collections.defaultdict(list)
    for f, src in GD:
        fn, fnl = '', 0
        L = src.split('\n')
        for i, l in enumerate(L):
            m = FUNC_RX.match(l)
            if m:
                fn, fnl = m.group(1), i
            for mm in READ_RX.finditer(l):
                k = mm.group(1)
                if k.startswith('_') or k.endswith('_'):
                    continue   # служебная отметка кода (_cord_ripped) или имя по частям ("end_" + k — ниже)
                if re.match(r'"\]\s*=(?!=)', l[mm.end() - 1:]):
                    writes[k].append((f, fn))
                    continue
                case = ''
                for j in range(i, fnl, -1):
                    lm = LABEL_RX.match(L[j])
                    if lm:
                        case = re.findall(r'"([^"]+)"', lm.group(1))[0]
                        break
                reads[k].append((f, i + 1, fn, case))
    return reads, writes


CODE_READS, CODE_WRITES = code_index()


def endings_from_code():
    """Концовки, как их выбирает game.gd::_the_end: имена, порядок проверки end_*, концовка по умолчанию."""
    m = re.search(r'func _the_end\(\)[^\n]*\n(.*?)(?=\nfunc |\Z)', GAME, re.S)
    body = m.group(1) if m else ''
    names = dict(re.findall(r'"([a-z]+)":\s*\["«([^»]+)»"', body))
    om = re.search(r'for k in \[([^\]]+)\]', body)
    order = re.findall(r'"([a-z]+)"', om.group(1)) if om else ['mama', 'shadow', 'ice', 'witness']
    dm = re.search(r'var cur := "([a-z]+)"', body)
    default = dm.group(1) if dm else 'oblivion'
    if not names:
        names = {k: k for k in order + [default]}
    return names, order, default


END_NAMES, END_ORDER, END_DEFAULT = endings_from_code()
END_FLAGS = {'end_' + k: k for k in END_NAMES}
for _k in END_NAMES:   # _the_end читает "end_" + k
    CODE_READS['end_' + _k].append(('game.gd', 0, '_the_end', ''))


def map1_from_code():
    rows = dict(re.findall(r'\{"key":\s*"(\w+)",\s*"ru":\s*"([^"]+)"', GAME))
    mf = re.search(r'const MAP1_FLAGS := \{(.*?)\}\n', GAME, re.S)
    fl = {}
    if mf:
        for key, lst in re.findall(r'"(\w+)":\s*\[([^\]]*)\]', mf.group(1)):
            for k in re.findall(r'"(\w+)"', lst):
                fl.setdefault(k, []).append(rows.get(key, key))
    return fl


MAP1 = map1_from_code()   # флаг → строки карты главы 1 (карта главы 1 — в коде, глав 2–5 — в данных)


def cp_titles():
    m = re.search(r'const CP_TITLES := \{(.*?)\}\n', GAME, re.S)
    return dict(re.findall(r'"(\w+)":\s*\["([^"]+)"', m.group(1))) if m else {}


CP = cp_titles()


# ---------------------------------------------------------------- граф сцен
def scene_edges(sid):
    """[(куда, как, условие, шаг, подпись)]: else, вариант/молчание, взгляд, провал действия, go, конец главы."""
    out = []
    for i, b in enumerate(SC[sid]['beats']):
        c = b.get('if')
        if b.get('else'):
            out.append((b['else'], 'else', c, i, ''))
        if 'choice' in b:
            for o in b['choice']['opts']:
                if o.get('go'):
                    out.append((o['go'], 'silent' if o.get('silent') else 'opt', o.get('if'), i, A.txt(o)))
        if 'peek' in b:
            out.append((b['yes'], 'peek_yes', c, i, ''))
            out.append((b['no'], 'peek_no', c, i, ''))
        if 'qte' in b and b.get('fail'):
            out.append((b['fail'], 'fail', c, i, A.txt(b.get('hint'))))
        if b.get('go'):
            out.append((b['go'], 'go', c, i, ''))
        if b.get('end'):
            nx = A.chapter_next(CHAPTERS, int(b['end']))
            if nx:
                out.append((nx, 'end', c, i, ''))
    return out


EDGES = {sid: scene_edges(sid) for sid in SC}
SUCC = {sid: [t for t, *_ in EDGES[sid] if t in SC] for sid in SC}
INC = collections.defaultdict(list)
for _s, _es in EDGES.items():
    for _t, _kind, _c, _i, _lab in _es:
        INC[_t].append((_s, _kind, _c, _i, _lab))


def bfs(s):
    seen, st = set(), list(SUCC[s])
    while st:
        x = st.pop()
        if x not in seen:
            seen.add(x)
            st.extend(SUCC[x])
    return seen


REACH = {sid: bfs(sid) for sid in SC}   # куда можно попасть из сцены (без учёта условий)


def post_dominators():
    """Сцены, через которые проходит ЛЮБОЙ путь из сцены до конца игры (точки, где ветки точно сходятся)."""
    nodes = list(SC)
    allset = set(nodes)
    pd = {n: ({n} if not SUCC[n] else set(allset)) for n in nodes}
    changed = True
    while changed:
        changed = False
        for n in reversed(nodes):
            if not SUCC[n]:
                continue
            new = set.intersection(*(pd[x] for x in SUCC[n])) | {n}
            if new != pd[n]:
                pd[n] = new
                changed = True
    return pd


PDOM = post_dominators()


def play_order():
    """Порядок прохождения: топологический по переходам внутри главы, при равенстве — порядок в данных; петли
    (хаб разговоров на даче) рвутся по порядку в данных."""
    order = []
    for n in sorted(CHAPTERS):
        nodes = [s for s in SC if OWNER[s] == n]
        indeg = {s: 0 for s in nodes}
        for s in nodes:
            for t in set(SUCC[s]):
                if t in indeg and t != s:
                    indeg[t] += 1
        done = set()
        while len(done) < len(nodes):
            cand = [s for s in nodes if s not in done and indeg[s] <= 0] or [s for s in nodes if s not in done]
            s = min(cand, key=JSON_IDX.get)
            done.add(s)
            order.append(s)
            for t in set(SUCC[s]):
                if t in indeg and t not in done:
                    indeg[t] -= 1
    return order


PLAY = play_order()
POS = {sid: i for i, sid in enumerate(PLAY)}


def sections():
    sec, cur = {}, ''
    for sid in PLAY:
        if sid.endswith('_recap') or sid == CHAPTERS.get(OWNER[sid], {}).get('start'):
            cur = ''
        if SC[sid].get('save') and sid in CP:
            cur = CP[sid]
        sec[sid] = cur
    return sec


SECTION = sections()


def where_scene(sid):
    s = SECTION.get(sid, '')
    return '%d · %s`%s`' % (OWNER[sid], (s + '<br>') if s else '', sid)


# ---------------------------------------------------------------- тексты шагов
def beat_line(b, n=85):
    if b.get('mute'):
        return None
    if 'say' in b:
        return '%s: «%s»' % (who_name(b), short(b.get('ru', ''), n))
    for k in ('text', 'note'):
        if isinstance(b.get(k), dict):
            return '*%s*' % short(b[k].get('ru', ''), n)
    return None


def beat_effect(b, ch):
    """Что делает шаг (для «где читается»): реплика, подпись, флаги, действие, переход."""
    parts = []
    t = beat_line(b, 70)
    if t:
        parts.append(t)
    if 'set' in b:
        parts.append('ставит ' + ', '.join(delta(k, v) for k, v in b['set'].items()))
    if 'qte' in b:
        parts.append('действие «%s» (%s нажатий, %s с)' % (short(A.txt(b.get('hint')), 40), b.get('n', '—'), b.get('t', '—')))
    if 'choice' in b:
        parts.append('выбор')
    if b.get('go'):
        parts.append('→ `%s`' % b['go'])
    if b.get('else'):
        parts.append('иначе → `%s`' % b['else'])
    if not parts and b.get('fx') and not str(b['fx']).startswith('snd:'):
        parts.append('постановка ' + fx_txt(b['fx'], ch))
    if not parts and (b.get('cam') or b.get('shot')):
        parts.append('отдельный план камеры (без текста)')
    if not parts and b.get('dur'):
        parts.append('пауза без текста')
    return '; '.join(parts) or 'шаг постановки без текста'


def fx_ok(b):
    return b.get('fx') and not b.get('if') and str(b['fx']) not in GENERIC_FX and not str(b['fx']).startswith('snd:')


def first_lines(sid, start=0, n=2):
    """1–2 первых реплики или подписи с шага start (без текста — эффект постановки из кода; без него — «не определено»).
    В следующую сцену — только если в этой нет ни текста, ни постановки (сквозной переход): «дальше `X`: …».
    Развилка по флагу в начале сцены: «(при `cond`) …строки…; иначе → `Y`»."""
    out, fx, cur, i0, hops, pre, tail = [], [], sid, start, 0, '', ''
    while cur in SC and hops < 3:
        hops += 1
        nxt = None
        ch = OWNER[cur]
        lead = '' if cur == sid else 'дальше `%s`: ' % cur
        for b in SC[cur]['beats'][i0:]:
            t = beat_line(b)
            if t:
                c = b.get('if')
                out.append((lead if not out else '') + pre + ('(если `%s`) ' % cond_txt(c) if c else '') + t)
                pre = ''
            elif fx_ok(b) and len(fx) < 2:
                fx.append(fx_txt(b['fx'], ch))
            if len(out) >= n:
                break
            if 'choice' in b or 'peek' in b:
                if not out:
                    out.append(lead + 'сразу следующий выбор')
                break
            if 'qte' in b and not out:
                out.append(lead + 'действие «%s»' % short(A.txt(b.get('hint')), 50))
                break
            if 'explore' in b and not out:
                g = A.txt(WALKS.get(b['explore'], {}).get('goal'))
                out.append(lead + 'прогулка `%s`%s' % (b['explore'], (' — «%s»' % g) if g else ''))
                break
            if b.get('go') and not b.get('if'):
                nxt = b['go']
                break
            if b.get('else') and not beat_line(b):
                if out or tail:
                    break
                pre = '(при `%s`) ' % cond_txt(b['if'])   # развилка: дальше строки — при условии, иначе — другая сцена
                tail = 'иначе → `%s`' % b['else']
                continue
            if b.get('end'):
                out.append(lead + 'конец главы %s' % b['end'])
                break
        if out or fx:
            break
        if nxt is None:
            break
        cur, i0 = nxt, 0
    if not out and fx:
        out = ['без реплик: постановка ' + '; '.join(fx[:2])]
    if not out:
        out = ['не определено по данным (нет реплик и подписей)']
    if tail:
        out.append(tail)
    return out


def context(sid, i, depth=0):
    """Ситуация перед выбором: последняя реплика/подпись в сцене (без условия), иначе — постановка, иначе —
    конец предыдущей сцены (первой по ходу игры)."""
    beats = SC[sid]['beats']
    cond_line, fx = None, []
    for b in reversed(beats[:i]):
        if 'explore' in b or 'choice' in b:
            break
        t = beat_line(b, 80)
        if t and not b.get('if'):
            return t
        if t and cond_line is None:
            cond_line = '(если `%s`) %s' % (cond_txt(b['if']), t)
        if fx_ok(b) and len(fx) < 2:
            fx.append(fx_txt(b['fx'], OWNER[sid]))
    if cond_line:
        return cond_line
    if fx:
        return 'постановка ' + '; '.join(reversed(fx))
    if depth < 2:
        preds = sorted({s for s, kind, *_ in INC.get(sid, []) if kind in ('go', 'else')}, key=lambda x: POS.get(x, 0))
        for p in preds:
            t = context(p, len(SC[p]['beats']), depth + 1)
            if t != 'не определено по данным':
                return t
    return 'не определено по данным'


def entry_cond(sid):
    """Условие входа в сцену, если в неё ведёт ровно одна развилка по флагам."""
    inc = INC.get(sid, [])
    if len(inc) == 1:
        s, kind, c, i, _ = inc[0]
        if kind == 'go' and c:
            return '`%s`' % cond_txt(c)
        if kind == 'else' and c:
            return '`%s`' % neg_txt(c)
    return ''


# ---------------------------------------------------------------- где флаги ставятся и читаются
Read = collections.namedtuple('Read', 'kind sid ch where cond text order')


def walk_spot_label(wid, spot_id):
    W = WALKS.get(wid, {})
    for s in W.get('spots', []) + ([W['exit']] if W.get('exit') else []):
        if s.get('id') == spot_id:
            return A.txt(s.get('label')) or spot_id
    return spot_id


WALK_KEYS = {'hum': 'гул в голове во время прогулки', 'intro': 'вступление', 'setup': 'подготовка', 'nudge': 'подсказка',
             'zones': 'зона', 'block': 'преграда', 'doors': 'дверь', 'exit': 'выход'}


def walk_read_text(where, x, ch):
    wid, _, part = where.partition('.')
    part = part.replace(' [преграда]', '')
    if 'label' in x:
        return 'прогулка `%s`: точка «%s» есть' % (wid, A.txt(x['label']))
    if 'beats' in x and 'ru' in x:
        return 'прогулка `%s` («%s»): тема «%s» есть' % (wid, walk_spot_label(wid, part), short(A.txt(x).strip('«»'), 40))
    if 'rect' in x or '[преграда]' in where:
        t = next((beat_line(b, 50) for b in x.get('beats', []) if beat_line(b)), '')
        return 'прогулка `%s`: %s%s' % (wid, 'зона' if 'rect' in x else 'преграда на пути (предмет или человек)',
                                        (' — ' + t) if t else '')
    if part in WALK_KEYS:
        t = beat_line(x, 60)
        return 'прогулка `%s`: %s%s' % (wid, WALK_KEYS[part], (' — ' + t) if t else '')
    return 'прогулка `%s` («%s»): %s' % (wid, walk_spot_label(wid, part), beat_effect(x, ch))


def build_index():
    """SETS: флаг → [(сцена, подпись места)]; READS: флаг → [Read]."""
    sets, reads = collections.defaultdict(list), collections.defaultdict(list)
    for sid in PLAY:
        ch = OWNER[sid]
        for i, b in enumerate(SC[sid]['beats']):
            o0 = POS[sid] * 1000 + i
            for k in (b.get('set') or {}):
                sets[k].append((sid, ''))
            if b.get('if'):
                if sid.endswith('_recap') and 'text' in b:
                    kind = 'recap'
                elif sid.endswith('_cards'):
                    kind = 'cards'
                else:
                    kind = 'scene'
                eff = beat_effect(b, ch)
                for k in b['if']:
                    reads[k].append(Read(kind, sid, ch, '', b['if'], eff, o0))
            if 'choice' in b:
                for o in b['choice']['opts']:
                    if o.get('if'):
                        for k in o['if']:
                            reads[k].append(Read('option', sid, ch, '', o['if'],
                                                 'вариант «%s» есть' % short(A.txt(o) or 'молчание', 45), o0))
            for where, x in A.walk_parts(b, DATA):
                lab = 'прогулка `%s`' % where.split('.')[0] if 'explore' in b else 'разговор'
                for k in (x.get('set') or {}):
                    sets[k].append((sid, lab))
                if isinstance(x.get('if'), dict):
                    if 'explore' in b:
                        t = walk_read_text(where, x, ch)
                    elif 'beats' in x and 'ru' in x:
                        t = 'разговор: тема «%s» есть' % short(A.txt(x).strip('«»'), 40)
                    else:
                        t = 'разговор: ' + beat_effect(x, ch)
                    for k in x['if']:
                        reads[k].append(Read('walk', sid, ch, where, x['if'], t, o0))
    for n, chd in CHAPTERS.items():
        for r in chd.get('map', []):
            for o in r.get('opts', []):
                for k in (o.get('if') or {}):
                    reads[k].append(Read('map', None, n, '', o['if'], 'карта главы %d: «%s — %s»' % (n, r.get('ru', r.get('key')), o.get('ru', o.get('id'))), 10 ** 7 + n))
    for k, rows in MAP1.items():
        reads[k].append(Read('map', None, 1, '', {}, 'карта главы 1: «%s»' % '», «'.join(rows), 10 ** 7 + 1))
    for k, lst in CODE_READS.items():
        seen = set()
        for f, line, fn, case in lst:
            if fn in MAP_FUNCS:
                if fn != '_map_pick':
                    reads[k].append(Read('map', None, 0, '', {}, 'карта главы: доверие Айрис', 10 ** 7 + 9))
                continue
            key = '%s:%s' % (f, fn)
            key2 = '%s:%s:%s' % (f, fn, case) if case else key
            if key2 in seen:
                continue
            seen.add(key2)
            if fn == '_the_end':
                reads[k].append(Read('end', None, 5, '', {}, 'экран «Конец»: название концовки (`game.gd`)', 10 ** 7 + 8))
                continue
            desc = CODE_DESC.get(key2) or CODE_DESC.get(key) or 'функция `%s`%s' % (fn, (' (`%s`)' % case) if case else '')
            reads[k].append(Read('code', None, CODE_CH.get(f, 0), '', {}, 'код `%s`: %s' % (f, desc), 10 ** 7 + 5))
    for k, lst in CODE_WRITES.items():
        for f, fn in lst:
            sets[k].append((None, 'код `%s` %s' % (f, fn)))
    return sets, reads


SETS, READS = build_index()
# служебные отметки сцены: читаются только там же, где ставятся (тема уже была, подсказка показана, шаг ремонта)
LOCAL = {k for k in SETS if READS.get(k) and all(r.sid is not None and r.sid in {s for s, _ in SETS[k]} for r in READS[k])}
STRONG = {'scene', 'option', 'walk', 'cards', 'code', 'end'}   # сюжет и постановка; слабые — карта главы, «Ранее…»


def deferred(flag, set_sid, exclude=()):
    """Где флаг, поставленный в сцене set_sid, читается дальше: сцены, до которых можно дойти (не та же сцена),
    карта главы этой главы и дальше, код этой главы и дальше, экран «Конец»."""
    ch = OWNER.get(set_sid, 1)
    out = []
    for r in READS.get(flag, []):
        if r.sid is not None:
            if r.sid == set_sid or r.sid in exclude or r.sid not in REACH.get(set_sid, ()):
                continue
        elif r.kind in ('map', 'code') and r.ch and r.ch < ch:
            continue
        out.append(r)
    return sorted(out, key=lambda r: (r.kind not in STRONG, r.order))   # сначала сюжет и постановка, потом карта/«Ранее…»


def read_txt(r, flag):
    c = r.cond or {}
    part = ' (`%s`%s)' % (A.cond_s({flag: c[flag]}), ', …' if len(c) > 1 else '') if flag in c else ''
    if r.kind in ('scene', 'option', 'walk'):
        return 'гл. %d · `%s`: %s%s' % (r.ch, r.sid, r.text, part)
    if r.kind == 'recap':
        return '«Ранее…» гл. %d (только после «Продолжить»): %s%s' % (r.ch, r.text, part)
    if r.kind == 'cards':
        return 'эпилог «Что стало с каждым»: %s%s' % (r.text, part)
    return r.text + part


# ---------------------------------------------------------------- черновик откликов
def load_draft():
    res = {}
    if not os.path.exists(DRAFT):
        return res
    for line in open(DRAFT, encoding='utf-8'):
        if not line.startswith('| `'):
            continue
        cells = [c.strip() for c in line.strip().strip('|').split('|')]
        flags = re.findall(r'`([a-z0-9_]+)`', cells[0])
        for k in flags:
            if len(cells) >= 5:
                how = re.split(r' / [“"]', cells[4])[0]
                res[k] = ('proposal', '%s — %s' % (cells[3], how))
            elif len(cells) == 3:
                res[k] = ('service', cells[2])
    return res


DRAFT_MAP = load_draft()


def draft_note(flag):
    d = DRAFT_MAP.get(flag)
    if not d:
        return ''
    if d[0] == 'service':
        return ' — служебный, отклик не нужен ([черновик](%s))' % DRAFT_LINK.split('#')[0]
    return ' — [черновик отклика](%s): %s' % (DRAFT_LINK, short(d[1], 120))


# ---------------------------------------------------------------- концовки
def own_set_tokens(st, G):
    out = set()
    for k in (st or {}):
        if k in END_FLAGS:
            out.add(('E', END_FLAGS[k]))
        elif k in G:
            out.add(('F', k))
    return out


def beat_tokens(b, G):
    tok = own_set_tokens(b.get('set'), G)
    for _, x in A.walk_parts(b, DATA):
        tok |= own_set_tokens(x.get('set'), G)
    return tok


def gates():
    """Флаги, от которых зависит концовка. Точка решения — условие, при котором дальше можно поставить разные end_*
    (развилка шага, вариант с условием, сложность действия с провалом в другую концовку) или флаг, уже решающий
    концовку (цепочка). G[флаг] = {ends, via, where, kind}."""
    G = {}

    def ends_of(tokens):
        e = {v for t, v in tokens if t == 'E'}
        if e:
            return e
        for t, v in tokens:
            e |= G[v]['ends']
        return e

    def add(k, ends, via, where, kind):
        if k in END_FLAGS or not ends or k == via:
            return False
        depth = 0 if via is None else G[via]['depth'] + 1
        r = dict(kind=kind, where=where, ends=set(ends), via=via, depth=depth)
        if k not in G:
            G[k] = dict(ends=set(ends), reasons=[r], depth=depth, kind=kind, via=via, where=where)
            return True
        new = set(ends) - G[k]['ends']
        if not new:
            return False
        r['ends'] = new
        G[k]['reasons'].append(r)
        G[k]['ends'] |= new
        if depth < G[k]['depth']:
            G[k].update(depth=depth, kind=kind, via=via, where=where)
        return True


    while True:
        own = {sid: set().union(*[beat_tokens(b, G) for b in SC[sid]['beats']]) if SC[sid]['beats'] else set() for sid in SC}
        eff = {sid: own[sid].union(*[own[x] for x in REACH[sid]]) for sid in SC}

        def E(s):
            return eff.get(s, set())

        def tail(sid, j):
            tok = set()
            beats = SC[sid]['beats']
            for k in range(j, len(beats)):
                b = beats[k]
                tok |= beat_tokens(b, G)
                if b.get('else'):
                    tok |= E(b['else'])
                if 'qte' in b and b.get('fail'):
                    tok |= E(b['fail'])
                if 'choice' in b:
                    return tok.union(*[E(o.get('go')) for o in b['choice']['opts']])
                if 'peek' in b:
                    return tok | E(b['yes']) | E(b['no'])
                if b.get('go'):
                    tok |= E(b['go'])
                    if not b.get('if') or b.get('else'):
                        return tok
                if b.get('end'):
                    return tok | E(A.chapter_next(CHAPTERS, int(b['end'])))
            return tok

        def branch_true(sid, j):
            b = SC[sid]['beats'][j]
            tok = beat_tokens(b, G)
            if 'qte' in b and b.get('fail'):
                tok |= E(b['fail'])
            if b.get('go'):
                return tok | E(b['go'])
            if 'choice' in b:
                return tok.union(*[E(o.get('go')) for o in b['choice']['opts']])
            if 'peek' in b:
                return tok | E(b['yes']) | E(b['no'])
            if b.get('end'):
                return tok | E(A.chapter_next(CHAPTERS, int(b['end'])))
            return tok | tail(sid, j + 1)

        changed = False

        def add_tokens(k, tokens, where, kind):
            """Причина по каждому решающему флагу отдельно; если ветки различаются самими end_*, цепочки не нужны."""
            ch = False
            es = {v for t, v in tokens if t == 'E'}
            if es:
                return add(k, es, None, where, kind)
            for t, v in sorted(tokens, key=lambda x: (G[x[1]]['depth'], x[1])):
                ch |= add(k, G[v]['ends'], v, where, 'через')
            return ch

        for sid in PLAY:
            beats = SC[sid]['beats']
            j = 0
            while j < len(beats):
                b = beats[j]
                c = b.get('if')
                if c:
                    tk = own_set_tokens(b.get('set'), G)
                    if tk:   # условие, при котором ставится флаг, решающий концовку
                        for k in c:
                            changed |= add_tokens(k, tk, 'условие, при котором `%s` ставит %s' % (
                                sid, ', '.join('`%s`' % v if t == 'F' else '`end_%s`' % v for t, v in sorted(tk))), 'решает')
                    if 'qte' in b and b.get('fail'):   # варианты сложности одного действия: решает, если провал — в другую концовку
                        k2 = j
                        conds = []
                        while k2 < len(beats) and 'qte' in beats[k2] and beats[k2].get('fail') == b['fail']:
                            conds.append(beats[k2].get('if') or {})
                            k2 += 1
                        diff = E(b['fail']) ^ tail(sid, k2)
                        if diff:
                            for cc in conds:
                                for k in cc:
                                    changed |= add_tokens(k, diff, 'сложность действия «%s» в `%s`' % (
                                        short(A.txt(b.get('hint')), 30), sid), 'сложность')
                        j = k2
                        continue
                    T = branch_true(sid, j)
                    F = E(b['else']) if b.get('else') else tail(sid, j + 1)
                    diff = T ^ F
                    if diff:
                        for k in c:
                            changed |= add_tokens(k, diff, 'развилка в `%s` (`%s`)' % (sid, cond_txt(c)), 'решает')
                if 'choice' in b:
                    opts = b['choice']['opts']
                    for o in opts:
                        if not o.get('if'):
                            continue
                        U = set().union(*[E(p.get('go')) for p in opts if p is not o])
                        extra = E(o.get('go')) - U
                        if extra:
                            for k in o['if']:
                                changed |= add_tokens(k, extra, 'вариант «%s» в `%s`' % (short(A.txt(o), 30), sid), 'решает')
                for where, x in A.walk_parts(b, DATA):   # прогулки и разговоры: точка/тема с условием ставит решающий флаг
                    tk = own_set_tokens(x.get('set'), G)
                    if tk and isinstance(x.get('if'), dict):
                        for k in x['if']:
                            changed |= add_tokens(k, tk, 'прогулка `%s`' % where.split('.')[0], 'решает')
                j += 1
        if not changed:
            return G, eff


GATES, EFF = gates()


def ends_reach(sid):
    return {v for t, v in EFF.get(sid, set()) if t == 'E'}


def ends_txt(es):
    order = [k for k in END_ORDER + [END_DEFAULT] + sorted(END_NAMES) if k in es]
    seen = []
    for k in order:
        if k not in seen:
            seen.append(k)
    return ', '.join('«%s»' % END_NAMES.get(k, k) for k in seen)


def gate_chain(k):
    """Цепочка до флага, который решает концовку сам: [via1, via2, …], и место решения последнего."""
    chain, cur, seen = [], k, {k}
    while GATES.get(cur, {}).get('via') and GATES[cur]['via'] not in seen:
        cur = GATES[cur]['via']
        seen.add(cur)
        chain.append(cur)
    return chain, GATES[cur]['where'] if cur in GATES else ''


def chain_for(v, ends):
    """Цепочка от флага v к флагу, который решает именно эти концовки сам (по причинам с этими концовками)."""
    path, cur, seen = [v], v, {v}
    while True:
        rs = [r for r in GATES[cur]['reasons'] if r['ends'] & ends]
        if not rs:
            break
        r = min(rs, key=lambda r: r['depth'])
        if not r['via'] or r['via'] in seen:
            break
        seen.add(r['via'])
        path.append(r['via'])
        cur = r['via']
    return path


def reasons_list(k, only=None):
    """[(концовки, как)] — причины флага, одинаковые объяснения слиты."""
    out = collections.OrderedDict()
    for r in sorted(GATES[k]['reasons'], key=lambda r: r['depth']):
        how = ('через ' + ' → '.join('`%s`' % x for x in chain_for(r['via'], r['ends']))) if r['via'] else r['where']
        out.setdefault(how, set()).update(r['ends'])
    return [(e, how) for how, e in out.items()]


def reason_txt(r):
    return '%s: %s' % (ends_txt(r[0]), r[1])


def gate_txt(k, nmax=2):
    rs = reasons_list(k)
    return '`%s` → %s' % (k, '; '.join(reason_txt(r) for r in rs[:nmax]) + (' …' if len(rs) > nmax else ''))


# ---------------------------------------------------------------- ветка варианта
def segment(start, stop, origin, start_beat=0, max_scenes=12, shared=None):
    """Ветка от сцены start до следующего выбора, до сцены из stop (туда ведут все остальные варианты — ветки
    сошлись), контрольной точки (новый эпизод), возврата к выбору origin, конца главы. Флаги — с условиями шагов;
    провал действия — своя ветка. shared: сцена → варианты, которые тоже туда ведут (но не все)."""
    res = dict(sets=[], toast=False, scenes=[], stops=[], walks=[])
    visited = set()

    def walk(sid, conds, i0=0):
        if sid not in SC:
            res['stops'].append(('missing', sid))
            return
        if sid == origin and i0 == 0:
            res['stops'].append(('loop', sid))
            return
        if sid != start and sid in stop:
            res['stops'].append(('merge', sid))
            return
        if sid != start and SC[sid].get('save'):   # контрольная точка — новый эпизод: дальше уже не эта ветка
            res['stops'].append(('save', sid))
            return
        if (sid, i0) in visited:
            return
        visited.add((sid, i0))
        if len(visited) > max_scenes:
            res['stops'].append(('far', sid))
            return
        if sid not in res['scenes']:
            res['scenes'].append(sid)
            if shared and sid != start and sid in shared:
                res['stops'].append(('shared', (sid, shared[sid])))
        beats = SC[sid]['beats']
        cur = list(conds)
        i = i0
        while i < len(beats):
            b = beats[i]
            c = b.get('if')
            if c and b.get('else'):
                walk(b['else'], cur + [neg_txt(c)])
                cur = cur + [cond_txt(c)]
                bc = cur
            else:
                bc = cur + ([cond_txt(c)] if c else [])
            for k, v in (b.get('set') or {}).items():
                res['sets'].append((k, int(v), bc, sid))
            if 'explore' in b and b['explore'] not in res['walks']:
                res['walks'].append(b['explore'])
            if b.get('toast'):
                res['toast'] = True
            if 'choice' in b or 'peek' in b:
                res['stops'].append(('choice', (sid, i)))
                return
            if 'qte' in b and b.get('fail'):
                lab = 'провал «%s»' % short(A.txt(b.get('hint')), 28)
                if b['qte'] == 'brake' and 'lamp' in CODE_WRITES:
                    res['sets'].append(('lamp', 1, cur + [lab, 'ставит код `_deer_hit`'], sid))
                walk(b['fail'], cur + [lab])
                if 'если действие удалось' not in cur:
                    cur = cur + ['если действие удалось']
            if b.get('go'):
                walk(b['go'], bc)
                if not c or b.get('else'):
                    return
                cur = cur + [neg_txt(c)]
            if b.get('end'):
                res['stops'].append(('end', int(b['end'])))
                return
            i += 1
        res['stops'].append(('deadend', sid))

    walk(start, [], start_beat)
    return res


def sets_txt(sets):
    out, seen = [], set()
    for k, v, conds, sid in sets:
        key = (k, v, tuple(conds[-2:]))
        if key in seen:
            continue
        seen.add(key)
        c = [x for x in conds[-2:] if x]
        out.append(delta(k, v) + ((' (%s)' % '; '.join(c)) if c else ''))
    return out


def flag_reads_txt(flag, set_sid, nmax=3, exclude=()):
    """Отложенно для одного флага: текст и виды чтений ('strong'/'weak'/None). exclude — сцены, где чтение не в счёт
    (сцена самого выбора: возврат к хабу разговоров только прячет уже выбранную тему)."""
    rs = deferred(flag, set_sid, exclude)
    if not rs:
        note = draft_note(flag)
        same = [r for r in READS.get(flag, []) if r.sid == set_sid]
        if same:
            return '`%s` — читается только в той же сцене (`%s`: %s)%s' % (flag, set_sid, short(same[0].text, 60), note), None
        return '`%s` — дальше нигде не читается%s' % (flag, note), None
    kinds = {r.kind for r in rs}
    strength = 'strong' if kinds & STRONG else 'weak'
    uniq, seen = [], set()   # одно место на сцену (первое по ходу игры)
    for r in rs:
        key = (r.kind, r.sid) if r.sid else (r.kind, r.text)
        if key not in seen:
            seen.add(key)
            uniq.append(r)
    if flag in COUNTERS:
        thr = sorted({A.cond_s({flag: r.cond[flag]})[len(flag):] for r in rs if r.cond and flag in r.cond})
        ex = [read_txt(r, flag) for r in uniq if r.kind in STRONG][:2] or [read_txt(r, flag) for r in uniq][:2]
        return '`%s` (счётчик): дальше в %d %s%s — %s%s' % (
            flag, len(uniq), plural(len(uniq), 'месте', 'местах', 'местах'), (', пороги ' + ' '.join(thr)) if thr else '',
            '; '.join(ex), ' …' if len(uniq) > len(ex) else ''), strength
    txts = [read_txt(r, flag) for r in uniq[:nmax]]
    more = ' (+ ещё %d)' % (len(uniq) - nmax) if len(uniq) > nmax else ''
    return '`%s` → %s%s' % (flag, '; '.join(txts), more), strength


def is_neg(e):
    op, n = parse_e(e)
    try:
        n = float(n)
    except ValueError:
        return False
    return (op == '==' and n == 0) or (op == '<=' and n == 0) or (op == '<' and n == 1)


def absent_txt(k, origin, labs, nmax=2, exclude=()):
    """Флаг ставят другие варианты, а этот — нет: где дальше читается «флага нет» (условие == 0)."""
    rs = [r for r in deferred(k, origin, exclude) if r.cond and k in r.cond and is_neg(r.cond[k])]
    if not rs:
        return None, None
    uniq, seen = [], set()
    for r in rs:
        key = (r.kind, r.sid) if r.sid else (r.kind, r.text)
        if key not in seen:
            seen.add(key)
            uniq.append(r)
    strength = 'strong' if any(r.kind in STRONG for r in rs) else 'weak'
    more = ' (+ ещё %d)' % (len(uniq) - nmax) if len(uniq) > nmax else ''
    return 'без `%s` (его ставит %s) → %s%s' % (k, labs, '; '.join(read_txt(r, k) for r in uniq[:nmax]), more), strength


# ---------------------------------------------------------------- выборы
def collect_choices():
    out = []
    for sid in PLAY:
        for i, b in enumerate(SC[sid]['beats']):
            if 'choice' in b:
                c = b['choice']
                opts, n = [], 0
                for o in c['opts']:
                    if o.get('silent'):
                        lab = 'м'
                    else:
                        n += 1
                        lab = str(n)
                    opts.append(dict(lab=lab, text=A.txt(o), go=o.get('go'), cond=o.get('if'), silent=bool(o.get('silent')),
                                     shadow=bool(o.get('shadow'))))
                out.append(dict(kind='choice', sid=sid, i=i, t=float(c.get('t', 6)), opts=opts))
            elif 'peek' in b:
                hint = A.txt(b.get('hint')) or 'Посмотреть'
                out.append(dict(kind='peek', sid=sid, i=i, t=float(b.get('t', 5)), opts=[
                    dict(lab='1', text='%s (успеть за %g с)' % (hint, float(b.get('t', 5))), go=b['yes'], cond=None, silent=False, shadow=False),
                    dict(lab='м', text='не посмотреть (время вышло)', go=b['no'], cond=None, silent=True, shadow=False)]))
    for n, c in enumerate(out, 1):
        c['num'] = n
    return out


CHOICES = collect_choices()
NUM_AT = {(c['sid'], c['i']): c['num'] for c in CHOICES}


def first_choice(sid):
    """Номер выбора, к которому сцена приходит без развилок (иначе None)."""
    for i, b in enumerate(SC.get(sid, {}).get('beats', [])):
        if 'choice' in b or 'peek' in b:
            return NUM_AT.get((sid, i))
        if b.get('go') or b.get('else') or ('qte' in b and b.get('fail')) or b.get('end'):
            return None
    return None


def stops_txt(stops, origin_num, sib=None):
    out = []
    for kind, info in stops:
        if kind == 'merge':
            nn = first_choice(info)
            if sib and info in sib:
                s = 'дальше — как вариант %s (`%s`)' % (sib[info], info)
            elif nn:
                s = 'дальше общий выбор №%d (`%s`)' % (nn, info)
            else:
                s = 'дальше общая сцена `%s`' % info
        elif kind == 'choice':
            nn = NUM_AT.get(info)
            s = ('дальше выбор №%d' % nn) if nn != origin_num else 'возврат к выбору'
        elif kind == 'loop':
            s = 'возврат к выбору №%d' % origin_num
        elif kind == 'end':
            s = 'конец игры' if info >= max(CHAPTERS) else 'конец главы %d' % info
        elif kind == 'shared':
            sid_, labs = info
            s = 'вместе с вариантом %s: `%s`' % (', '.join(sorted(set(labs))), sid_)
        elif kind == 'save':
            s = 'дальше эпизод `%s`%s' % (info, (' («%s»)' % CP[info]) if info in CP else '')
        elif kind == 'far':
            s = 'длинная своя ветка (дальше `%s`)' % info
        elif kind == 'deadend':
            s = 'шаги сцены `%s` кончаются без перехода' % info
        else:
            s = 'переход в несуществующую сцену `%s`' % info
        if s not in out:
            out.append(s)
    return out


def analyse_option(c, o, seg, siblings, segs=None, absent=()):
    """Ячейки и статус одного варианта. Если ветка втекает в сцену другого варианта того же выбора (молчание →
    «замер» → как «Подчиниться»), последствия того варианта — тоже его."""
    sib = {}
    for p in siblings:
        if p['go'] != o['go']:
            sib.setdefault(p['go'], p['lab'])
    sets = list(seg['sets'])
    inherit = []
    for kind, info in seg['stops']:
        if kind == 'merge' and info in sib and segs and info in segs and not first_choice(info):
            inherit.append(info)
            sets += [x for x in segs[info]['sets'] if x not in sets]
    flags = []
    for k, v, conds, sid in sets:
        if k not in [f for f, _ in flags]:
            flags.append((k, sid))
    deferred_txt, strengths, counter_only = [], set(), True
    for k, sid in flags:
        t, s = flag_reads_txt(k, sid, exclude={c['sid']})
        deferred_txt.append(t)
        if s:
            strengths.add(s if (k not in COUNTERS or s == 'weak') else 'counter')
    for t, s in absent:   # чего нет в этой ветке, но есть в других
        deferred_txt.append(t)
        strengths.add(s)
    other_walks = {w for t2, sg in (segs or {}).items() if t2 != o['go'] for w in sg.get('walks', [])}
    own_walks = [w for w in seg.get('walks', []) if w not in other_walks and w in WALK_INFO]
    walk_strong = False
    for w in own_walks:
        info = WALK_INFO[w]
        nums = info['nums']
        deferred_txt.append('своя прогулка `%s`%s' % (w, (': действия %s' % ', '.join(nums)) if nums else ' (без действий с флагами)'))
        walk_strong |= info['strong']
    has_text = any(beat_line(b) for s in seg['scenes'] for b in SC[s]['beats'])
    same = [p['lab'] for p in siblings if p is not o and p['go'] == o['go']]
    targets = {p['go'] for p in siblings}
    # концовка: что ещё возможно после варианта
    all_e = set().union(*[ends_reach(t) for t in targets])
    mine = ends_reach(o['go'])
    others = set().union(*[ends_reach(t) for t in targets if t != o['go']]) if len(targets) > 1 else set()
    diff = []
    if same:
        diff.append('= вариант %s (та же сцена)' % ', '.join(same))
    diff += stops_txt(seg['stops'], c['num'], sib)
    end_direct = False
    if all_e and mine != all_e:
        end_direct = True
        only = mine - others
        if only:
            diff.append('**только этот вариант ведёт к %s**' % ends_txt(only))
        diff.append('дальше возможны: %s' % (ends_txt(mine) or 'никакие end_* (уже решено)'))
    end_set = sorted({END_FLAGS[k] for k, *_ in sets if k in END_FLAGS})
    if end_set:
        end_direct = True
        diff.append('**концовка %s**' % ends_txt(set(end_set)))
    gate_flags = [k for k, _ in flags if k in GATES]
    for k in gate_flags[:3]:
        diff.append('на концовку: ' + gate_txt(k))
    if len(gate_flags) > 3:
        diff.append('и ещё %d флага на концовку' % (len(gate_flags) - 3))
    # статус
    if len(targets) == 1:
        status = 'нет отклика'
        why = 'все варианты ведут в одну сцену'
    elif 'strong' in strengths or end_direct:
        status, why = 'есть отклик', ''
    elif walk_strong:
        status, why = 'есть отклик', 'через действия на своей прогулке'
    elif 'counter' in strengths:
        status, why = 'есть отклик', 'только через счётчик'
    elif 'weak' in strengths:
        status, why = 'есть отклик', 'только карта главы / «Ранее…»'
    elif has_text or sets or any(k == 'choice' for k, _ in seg['stops']) or any(
            b.get('fx') for s in seg['scenes'] for b in SC[s]['beats'] if s not in [t for t in targets if t != o['go']]):
        status, why = 'только сразу', ''
    else:
        status, why = 'нет отклика', 'ветка ничего не показывает и не ставит'
    if status in ('только сразу', 'нет отклика'):
        notes = [draft_note(k) for k, _ in flags if draft_note(k)]
        if notes and not any('черновик' in t for t in deferred_txt):
            deferred_txt += ['`%s`%s' % (k, draft_note(k)) for k, _ in flags if draft_note(k)]
    own = sets_txt(seg['sets'])
    inh = [x for x in sets if x not in seg['sets']]
    ftxt = ', '.join(own)
    if inh:
        ftxt += ('; ' if ftxt else '') + 'дальше как вариант %s: %s' % (', '.join(sib[i] for i in inherit), ', '.join(sets_txt(inh)))
    return dict(flags=ftxt, deferred=deferred_txt, diff=diff, status=status, why=why)


def choice_rows(c):
    rows = []
    targets = {}
    for o in c['opts']:
        targets.setdefault(o['go'], []).append(o)
    segs = {}
    lab_of = {}
    for o in c['opts']:
        lab_of.setdefault(o['go'], o['lab'])
    for t in targets:
        others = [t2 for t2 in targets if t2 != t]
        reach = {t2: {t2} | REACH.get(t2, set()) for t2 in others}
        # ветки сошлись: через эту сцену проходит любой путь каждого другого варианта
        stop = set.intersection(*[PDOM.get(t2, {t2}) for t2 in others]) if others else set()
        shared = collections.defaultdict(list)
        for t2, rs in reach.items():
            for x in rs - stop:
                shared[x].append(lab_of[t2])
        segs[t] = segment(t, stop, c['sid'], shared=shared)
    has_silent = any(o['silent'] for o in c['opts'])
    timer = '%g с' % c['t']
    if c['kind'] == 'peek':
        tnote = '%s: нажать вовремя — посмотреть, иначе ветка «не посмотрел»' % timer
    elif has_silent:
        tnote = '%s; молчание (не выбирать) — свой вариант' % timer
    else:
        tnote = '%s; время вышло → первый показанный вариант' % timer
    ctx = context(c['sid'], c['i'])
    ent = entry_cond(c['sid'])
    for k, o in enumerate(c['opts']):
        seg = segs[o['go']]
        own = {k for k, *_ in seg['sets']}
        absent = []
        for k in sorted({k for t2 in targets if t2 != o['go'] for k, *_ in segs[t2]['sets']} - own - COUNTERS - set(END_FLAGS) - LOCAL):
            labs = sorted({p['lab'] for p in c['opts'] if p['go'] != o['go'] and k in {x for x, *_ in segs[p['go']]['sets']}})
            t, st = absent_txt(k, o['go'], ('вариант ' + ', '.join(labs)) if labs else 'другой вариант', exclude={c['sid']})
            if t:
                absent.append((t, st))
        a = analyse_option(c, o, seg, c['opts'], segs, absent)
        if o['silent'] and c['kind'] == 'choice':
            text = '[молчание — не выбирать %s]' % timer
        else:
            text = o['text']   # как на экране: реплика — в «», действие — без кавычек
        if o['cond']:
            text += '<br>только если `%s`' % cond_txt(o['cond'])
        if o['shadow']:
            text += '<br>*(вариант «Тени»: красный, дрожит)*'
        now = first_lines(o['go'])
        if seg['toast']:
            now.append('плашка «Айрис это запомнит»')
        rows.append(dict(
            num='%d.%s' % (c['num'], o['lab']),
            where=where_scene(c['sid']) + (('<br>сцена только при %s' % ent) if ent else '') if k == 0 else '″',
            q=('%s<br>*%s*' % (ctx, tnote)) if k == 0 else '″',
            opt=text,
            now='→ `%s`: %s' % (o['go'], ' · '.join(now)),
            flags=a['flags'] or '—',
            deferred='<br>'.join(a['deferred']) or '—',
            diff='<br>'.join(a['diff']) or '—',
            status=a['status'] + (('<br>(%s)' % a['why']) if a['why'] else ''),
            st=a['status'], why=a['why'], kind='choice', ch=OWNER[c['sid']], text=o['text'], silent=o['silent'],
            same=[p['lab'] for p in c['opts'] if p is not o and p['go'] == o['go']]))
    return rows


# ---------------------------------------------------------------- действия с провалом
def qte_groups():
    """Действия с провалом в отдельную сцену. Варианты сложности одного действия (условные шаги с тем же провалом в
    одной сцене, например «с монтировкой — 10 нажатий, без — 16») — одной группой."""
    out = []
    for sid in PLAY:
        beats = SC[sid]['beats']
        j = 0
        while j < len(beats):
            b = beats[j]
            if 'qte' in b and b.get('fail'):
                grp, k, last = [b], j + 1, j
                while k < len(beats):
                    bk = beats[k]
                    if 'qte' in bk and bk.get('fail') == b['fail'] and (bk.get('if') or b.get('if')):
                        grp.append(bk)
                        last = k
                    elif 'qte' in bk or 'choice' in bk or 'peek' in bk or (bk.get('go') and not bk.get('if')) or bk.get('end'):
                        break
                    k += 1
                if not all(x.get('if') for x in grp) and len(grp) > 1:   # вариантами считаем только условные шаги
                    grp, last = [b], j
                out.append(dict(sid=sid, i=j, after=last + 1, beats=grp, fail=b['fail']))
                j = last + 1
            else:
                j += 1
    return out


def exits_from(sid, j):
    ex = set()
    beats = SC[sid]['beats']
    for k in range(j, len(beats)):
        b = beats[k]
        for key in ('else', 'go'):
            if b.get(key):
                ex.add(b[key])
        if 'qte' in b and b.get('fail'):
            ex.add(b['fail'])
        if 'choice' in b:
            ex |= {o.get('go') for o in b['choice']['opts']}
            break
        if 'peek' in b:
            ex |= {b['yes'], b['no']}
            break
        if b.get('go') and not b.get('if'):
            break
        if b.get('end'):
            nx = A.chapter_next(CHAPTERS, int(b['end']))
            if nx:
                ex.add(nx)
            break
    return {x for x in ex if x in SC}


def qte_rows(q, num):
    sid = q['sid']
    b0 = q['beats'][0]
    hint = A.txt(b0.get('hint')) or b0['qte']
    var = []
    for b in q['beats']:
        var.append('%s нажатий, %s с%s' % (b.get('n', '—'), b.get('t', '—'), (' при `%s`' % cond_txt(b['if'])) if b.get('if') else ''))
    ok_exits = exits_from(sid, q['after'])
    stop_fail = set.intersection(*[PDOM.get(x, {x}) for x in ok_exits]) if ok_exits else set()
    stop_ok = PDOM.get(q['fail'], {q['fail']})
    seg_ok = segment(sid, stop_ok, None, start_beat=q['after'])
    seg_fail = segment(q['fail'], stop_fail, sid)
    if b0['qte'] == 'brake' and 'lamp' in CODE_WRITES:
        seg_fail['sets'].insert(0, ('lamp', 1, ['ставит код `_deer_hit`'], q['fail']))
    e_ok = set()
    for x in ok_exits:
        e_ok |= ends_reach(x)
    e_ok |= {END_FLAGS[k] for k, *_ in seg_ok['sets'] if k in END_FLAGS}
    e_fail = ends_reach(q['fail'])
    rows = []
    fail_flags = []
    for k, *_ in seg_fail['sets']:
        if k not in fail_flags:
            fail_flags.append(k)
    for lab, seg, tgt, es, other in (('удалось', seg_ok, None, e_ok, e_fail), ('провал', seg_fail, q['fail'], e_fail, e_ok)):
        flags = []
        for k, v, conds, s in seg['sets']:
            if k not in [f for f, _ in flags]:
                flags.append((k, s))
        dtxt, strengths = [], set()
        for k, s in flags:
            t, st = flag_reads_txt(k, s)
            dtxt.append(t)
            if st:
                strengths.add(st if k not in COUNTERS else 'counter')
        diff = stops_txt(seg['stops'], None)
        if es != other and (es or other):
            diff.append('дальше возможны: %s' % (ends_txt(es) or 'никакие end_* (уже решено)'))
        ends_here = sorted({END_FLAGS[k] for k, *_ in seg['sets'] if k in END_FLAGS})
        if ends_here:
            diff.append('**концовка %s**' % ends_txt(set(ends_here)))
        for k in [k for k, _ in flags if k in GATES][:2]:
            diff.append('на концовку: ' + gate_txt(k))
        if 'strong' in strengths or ends_here or (es != other and (es or other)):
            st, why = 'есть отклик', ''
        elif 'counter' in strengths:
            st, why = 'есть отклик', 'только через счётчик'
        elif 'weak' in strengths:
            st, why = 'есть отклик', 'только карта главы / «Ранее…»'
        elif lab == 'удалось' or flags or any(beat_line(bb) for s in seg['scenes'] for bb in SC[s]['beats']):
            st, why = 'только сразу', ''   # удалось — сцена идёт дальше как обычно
        else:
            st, why = 'нет отклика', 'ветка ничего не показывает и не ставит'
        if lab == 'удалось':
            for k in [x for x in fail_flags if x not in COUNTERS and x not in END_FLAGS and x not in [f for f, _ in flags]]:
                t, stt = absent_txt(k, sid, 'провал')
                if t:
                    dtxt.append(t)
                    if stt == 'strong' and st != 'есть отклик':
                        st, why = 'есть отклик', ''
        if lab == 'удалось' and not flags and fail_flags:
            diff.insert(0, 'отличие от провала: не ставятся %s' % ', '.join('`%s`' % k for k in fail_flags))
        now = first_lines(sid, q['after']) if tgt is None else first_lines(tgt)
        rows.append(dict(
            num='Д%d.%s' % (num, '1' if lab == 'удалось' else '2'),
            where=where_scene(sid) if lab == 'удалось' else '″',
            q=('действие «%s»<br>*%s*' % (short(hint, 60), '; '.join(var))) if lab == 'удалось' else '″',
            opt=lab, now=(('→ `%s`: ' % tgt) if tgt else ('' if now[0].startswith('дальше `') else 'дальше в `%s`: ' % sid)) + ' · '.join(now),
            flags=', '.join(sets_txt(seg['sets'])) or '—', deferred='<br>'.join(dtxt) or '—', diff='<br>'.join(diff) or '—',
            status=st + (('<br>(%s)' % why) if why else ''), st=st, why=why, kind='qte', ch=OWNER[sid], text=lab, silent=False, same=[]))
    return rows


# ---------------------------------------------------------------- прогулки и разговоры
def nested_sets(beats):
    out = []

    def deep(x):
        if isinstance(x, dict):
            if isinstance(x.get('set'), dict):
                for k, v in x['set'].items():
                    out.append((k, int(v), [cond_txt(x['if'])] if isinstance(x.get('if'), dict) else []))
            for kk, v in x.items():
                if kk != 'ask':
                    deep(v)
        elif isinstance(x, list):
            for v in x:
                deep(v)
    deep(beats)
    return out


def lines_of(beats, ch, n=2):
    out, fx = [], []
    for b in beats:
        t = beat_line(b)
        if t:
            out.append(('(если `%s`) ' % cond_txt(b['if']) if b.get('if') else '') + t)
            if len(out) >= n:
                break
        elif fx_ok(b) and len(fx) < 2:
            fx.append(fx_txt(b['fx'], ch))
    if not out and fx:
        out = ['без реплик: постановка ' + '; '.join(fx)]
    return out


def action_row(num, sid, where_lbl, situation, label, beats, cond, walk_id):
    sets = nested_sets(beats)
    sets_full = [(k, v, c, sid) for k, v, c in sets]
    flags = []
    for k, v, c in sets:
        if k not in flags:
            flags.append(k)
    dtxt, strengths = [], set()
    unlock = []
    for k in flags:
        t, st = flag_reads_txt(k, sid)
        dtxt.append(t)
        if st:
            strengths.add(st if k not in COUNTERS else 'counter')
        for r in READS.get(k, []):   # та же прогулка/разговор: что сразу открывает (условие «> 0») или убирает («== 0»)
            if r.sid == sid and r.kind in ('walk', 'option') and (not walk_id or r.where.startswith(walk_id + '.')):
                op, nn = parse_e(r.cond.get(k, ''))
                pos = (op in ('>', '>=') and float(nn) >= (0 if op == '>' else 1)) or (op == '==' and float(nn) > 0)
                u = r.text.split(': ', 1)[-1].split('; ставит ')[0]
                if u.startswith('ставит '):
                    continue
                u = ('открывает: ' if pos else 'убирает: ') + u
                if u not in unlock:
                    unlock.append(u)
    diff = ['на концовку: ' + gate_txt(k) for k in flags if k in GATES][:3]
    if 'strong' in strengths:
        st, why = 'есть отклик', ''
    elif 'counter' in strengths:
        st, why = 'есть отклик', 'только через счётчик'
    elif 'weak' in strengths:
        st, why = 'есть отклик', 'только карта главы / «Ранее…»'
    else:
        st, why = 'только сразу', ''
    now = lines_of(beats, OWNER[sid]) or ['не определено по данным (нет реплик и подписей)']
    if unlock:
        now.append('сразу ' + '; '.join(unlock[:2]) + (' …' if len(unlock) > 2 else ''))
    return dict(num=num, where=where_lbl, q=situation, opt=label + (('<br>только если `%s`' % cond_txt(cond)) if cond else ''),
                now=' · '.join(now), flags=', '.join(sets_txt(sets_full)) or '—', deferred='<br>'.join(dtxt) or '—',
                diff='<br>'.join(diff) or '—', status=st + (('<br>(%s)' % why) if why else ''), st=st, why=why, kind='walk',
                ch=OWNER[sid], text=label, silent=False, same=[])


def self_only(beats, wid, spot_id):
    """Флаги, которые точка ставит только для себя (реплика не повторяется при втором подходе) — не отдельное действие."""
    ks = {k for k, *_ in nested_sets(beats)}
    here = '%s.%s' % (wid, spot_id)
    return bool(ks) and all(READS.get(k) and all(r.kind == 'walk' and r.where.split(' ')[0] == here for r in READS[k]) for k in ks)


def walk_rows(counter):
    """Строки для действий на прогулках и тем разговоров, которые ставят флаги; остальное — списком."""
    rows, plain = collections.defaultdict(list), collections.defaultdict(list)
    for sid in PLAY:
        ch = OWNER[sid]
        for i, b in enumerate(SC[sid]['beats']):
            if 'explore' in b:
                wid = b['explore']
                W = WALKS.get(wid, {})
                where_lbl = '%s<br>прогулка `%s`' % (where_scene(sid), wid)
                goal = A.txt(W.get('goal'))
                for s in W.get('spots', []) + ([W['exit']] if W.get('exit') else []):
                    lab = A.txt(s.get('label')) or s.get('id')
                    own = s.get('beats', []) + s.get('after', [])
                    if nested_sets(own) and not self_only(own, wid, s.get('id')):
                        counter[0] += 1
                        rows[ch].append(action_row('П%d' % counter[0], sid, where_lbl, 'точка осмотра%s' % ((' (цель: «%s»)' % goal) if goal else ''),
                                                   '«%s»' % lab, own, s.get('if'), wid))
                    elif not s.get('ask') or nested_sets(own):
                        plain[ch].append('`%s`: «%s»%s' % (wid, lab, (' (если `%s`)' % cond_txt(s['if'])) if s.get('if') else ''))
                    for o in (s.get('ask') or {}).get('opts', []):
                        ob = o.get('beats', [])
                        if nested_sets(ob) and not self_only(ob, wid, s.get('id')):
                            counter[0] += 1
                            c = dict(s.get('if') or {})
                            c.update(o.get('if') or {})
                            rows[ch].append(action_row('П%d' % counter[0], sid, where_lbl, 'точка «%s» — выбор без таймера' % lab,
                                                       '«%s»' % A.txt(o).strip('«»'), ob, c or None, wid))
                        else:
                            plain[ch].append('`%s`: «%s» → «%s»' % (wid, lab, A.txt(o).strip('«»')))
                zone_rows = {}
                for z in W.get('zones', []):
                    if nested_sets(z.get('beats', [])):
                        r = action_row('П?', sid, where_lbl, 'зона прогулки', 'зайти в зону', z.get('beats', []), z.get('if'), wid)
                        key = (r['now'], r['flags'], r['opt'])
                        if key in zone_rows:   # одинаковые зоны (полыньи на льду) — одной строкой
                            zone_rows[key]['n'] += 1
                            continue
                        counter[0] += 1
                        r['num'] = 'П%d' % counter[0]
                        r['n'] = 1
                        zone_rows[key] = r
                        rows[ch].append(r)
                for r in zone_rows.values():
                    if r['n'] > 1:
                        r['opt'] = r['opt'].replace('зайти в зону', 'зайти в зону (%d одинаковых места)' % r['n'], 1)
            if 'ask' in b:
                q = b['ask']
                where_lbl = '%s<br>разговор (до %s тем)' % (where_scene(sid), q.get('max', 'всех'))
                for o in q.get('opts', []):
                    ob = o.get('beats', [])
                    if nested_sets(ob):
                        counter[0] += 1
                        rows[ch].append(action_row('П%d' % counter[0], sid, where_lbl, 'тема разговора (без таймера)',
                                                   '«%s»' % A.txt(o).strip('«»'), ob, o.get('if'), None))
                    else:
                        plain[ch].append('`%s` (разговор): «%s»%s%s' % (sid, A.txt(o).strip('«»'), ' — уйти' if o.get('leave') else '',
                                                                       (' (если `%s`)' % cond_txt(o['if'])) if o.get('if') else ''))
    for ch_rows in rows.values():   # какие действия у каждой прогулки (для строки варианта, который ведёт на неё)
        for r in ch_rows:
            m = re.search(r'прогулка `([^`]+)`', r['where'])
            if m:
                info = WALK_INFO.setdefault(m.group(1), dict(nums=[], strong=False))
                info['nums'].append(r['num'])
                info['strong'] |= (r['st'] == 'есть отклик' and not r['why'])
    for wid in WALKS:
        WALK_INFO.setdefault(wid, dict(nums=[], strong=False))
    return rows, plain


WALK_INFO = {}   # прогулка → номера её действий с флагами, есть ли у них отклик (заполняет walk_rows)


# ---------------------------------------------------------------- концовки: как дойти
COPIES = {}   # номер выбора → номер такой же развилки раньше (заполняет main)


def how_reached(sid, depth=0, seen=None):
    seen = seen or set()
    if sid in seen or depth > 3:
        return ['…']
    seen = seen | {sid}
    out = []
    done = set()
    for src, kind, c, i, lab in INC.get(sid, []):
        if kind in ('opt', 'silent', 'peek_yes', 'peek_no'):
            n = NUM_AT.get((src, i))
            if n in COPIES:
                continue   # копия развилки — та же строка, что у первой
            t = 'молчание' if kind == 'silent' else ((lab if lab.startswith(('«', '[')) else '«%s»' % lab) if lab else kind)
            cp = [a for a, b in COPIES.items() if b == n]
            s = 'выбор №%s%s %s%s' % (n, ('/' + '/'.join('№%d' % x for x in cp)) if cp else '', short(t, 45),
                                    (' (если `%s`)' % cond_txt(c)) if c else '')
        elif kind == 'fail':
            if (src, kind) in done:
                continue
            done.add((src, kind))
            up = how_reached(src, depth + 1, seen)
            s = 'провал действия «%s» в `%s` ← %s' % (short(lab, 30), src, ' / '.join(up[:3]))
        elif kind in ('go', 'else', 'end'):
            up = how_reached(src, depth + 1, seen)
            cnd = '' if not c else ('при `%s`' % (cond_txt(c) if kind == 'go' else neg_txt(c)))
            s = '`%s`%s ← %s' % (src, (' ' + cnd) if cnd else '', ' / '.join(up[:3]))
        else:
            continue
        if s not in out:
            out.append(s)
    return out or ['начало']


# ---------------------------------------------------------------- сборка документа
def table(rows):
    head = ['| № | Глава · сцена | Вопрос/ситуация | Вариант (RU) | Сразу: куда ведёт и что видит игрок | Флаги | '
            'Отложенно: где флаг читается и что меняется | Различия веток / концовка | Статус |',
            '|---|---|---|---|---|---|---|---|---|']
    for r in rows:
        head.append('| %s |' % ' | '.join(cell(r[k]) for k in ('num', 'where', 'q', 'opt', 'now', 'flags', 'deferred', 'diff', 'status')))
    return head


def main():
    out_path = OUT
    if '--out' in sys.argv:
        out_path = sys.argv[sys.argv.index('--out') + 1]
    check = '--check' in sys.argv
    rows_w, plain_w = walk_rows([0])   # раньше выборов: строкам вариантов нужны номера действий на прогулках
    by_ch = collections.defaultdict(list)
    for c in CHOICES:
        by_ch[OWNER[c['sid']]].append(c)
    rows_c = {ch: [r for c in cs for r in choice_rows(c)] for ch, cs in by_ch.items()}
    q_by = collections.defaultdict(list)
    for n, q in enumerate(qte_groups(), 1):
        q_by[OWNER[q['sid']]].extend(qte_rows(q, n))
    chs = sorted(CHAPTERS)

    # копии развилок (те же варианты и переходы в другой сцене)
    sig = {}
    copies = {}
    for c in CHOICES:
        key = tuple((o['text'], o['go'], cond_txt(o['cond']), o['silent']) for o in c['opts'])
        if key in sig:
            copies[c['num']] = sig[key]
        else:
            sig[key] = c['num']
    COPIES.clear()
    COPIES.update(copies)

    # ---- сводка
    L = ['# WHAT THE LAKE KEEPS — отклики на решения игрока', '',
         'Собрано `tools/lake/story/choice_table.py` из данных игры (`godot/lake/data/ch1–ch5.json`, разбор — '
         '`tools/lake/story_audit.py`) и кода постановки (`godot/lake/scripts/**/*.gd`). Руками не править: после правок '
         'данных — `python3 tools/lake/story/choice_table.py`. Черновик новых откликов (на утверждение) — '
         '[`FLAG_RESPONSES_DRAFT.md`](FLAG_RESPONSES_DRAFT.md).', '',
         '## Как читать', '',
         '- Одна строка — один вариант выбора, в порядке прохождения. «м» — молчание: игрок ничего не нажал, пока шла '
         'полоска времени. Где молчания нет, по истечении времени игра сама берёт первый показанный вариант (`hud.gd`). '
         'Настройка «долгий выбор» даёт ×1,6 времени.',
         '- «только если …» — вариант показывается лишь при этом условии. `″` — то же, что строкой выше.',
         '- **Сразу** — первые реплики или подписи сцены, куда ведёт вариант. Если там нет текста — эффект постановки '
         '(`fx`) с пояснением из кода; если и его нет — «не определено по данным».',
         '- **Флаги** — что ставит ветка варианта до следующего выбора или до сцены, где ветки сходятся (в скобках — '
         'условие шага; «провал …» — ветка проваленного действия).',
         '- **Отложенно** — где эти флаги читаются дальше: сцены и варианты (условие `if`), прогулки, эпилог, карта главы '
         '(экран в конце главы и «дерево выборов» в меню), «Ранее…» (виден только после «Продолжить»), код постановки. '
         'Только места, куда можно попасть после этого выбора, и не та же сцена.',
         '- **Статус:** «есть отклик» — флаг ветки где-то дальше меняет игру (или ветка сама решает концовку); с пометкой '
         '«только через счётчик» — меняется лишь число (`trust`, `shadow`, `memory`…), которое действует по порогам; '
         '«только карта главы / «Ранее…»» — в сюжете не видно; «только сразу» — своя сцена есть, дальше ничего не '
         'меняется; «нет отклика» — вариант ничем не отличается (все варианты ведут в одну сцену или ветка пустая). '
         'Для флагов без отклика — ссылка на предложенный отклик из черновика, если он там есть.',
         '- **Концовка** — по графу сцен (без перебора флагов): какие `end_*` ещё можно поставить после варианта и какие '
         'флаги ветки влияют на выбор концовки (цепочки — через флаги, которые от них зависят).', '']

    def stat(rows):
        cnt = collections.Counter()
        for r in rows:
            if r['st'] == 'есть отклик':
                cnt['yes_counter' if r['why'] == 'только через счётчик' else ('yes_weak' if r['why'] else 'yes')] += 1
            else:
                cnt[r['st']] += 1
        return cnt

    L += ['## Сводка', '',
          '| Глава | Выборов | Вариантов (из них молчание) | Есть отклик | — только через счётчик | — только карта/«Ранее…» | '
          'Только сразу | Нет отклика | Действий с провалом | Действий на прогулках и в разговорах с флагом |',
          '|---|---|---|---|---|---|---|---|---|---|']
    tot = collections.Counter()
    console = []
    for ch in chs:
        cs = by_ch.get(ch, [])
        rs = rows_c.get(ch, [])
        st = stat(rs)
        nq = len([r for r in q_by.get(ch, []) if r['opt'] == 'удалось'])
        nw = len(rows_w.get(ch, []))
        ncopy = len([c for c in cs if c['num'] in copies])
        nsil = len([r for r in rs if r['silent']])
        L.append('| %d | %d%s | %d (%d) | %d | %d | %d | %d | %d | %d | %d |' % (
            ch, len(cs), (' (%d — копии развилки)' % ncopy) if ncopy else '', len(rs), nsil,
            st['yes'] + st['yes_counter'] + st['yes_weak'], st['yes_counter'], st['yes_weak'], st['только сразу'],
            st['нет отклика'], nq, nw))
        tot.update(dict(c=len(cs), o=len(rs), s=nsil, y=st['yes'] + st['yes_counter'] + st['yes_weak'], yc=st['yes_counter'],
                        yw=st['yes_weak'], im=st['только сразу'], no=st['нет отклика'], q=nq, w=nw, cp=ncopy))
        console.append('гл.%d: выборов %d, вариантов %d (молчание %d): отклик %d (счётчик %d, карта %d), только сразу %d, нет %d; '
                       'действий с провалом %d, прогулки/разговоры с флагом %d' % (
                           ch, len(cs), len(rs), nsil, st['yes'] + st['yes_counter'] + st['yes_weak'], st['yes_counter'],
                           st['yes_weak'], st['только сразу'], st['нет отклика'], nq, nw))
    L.append('| **Всего** | **%d**%s | **%d (%d)** | **%d** | %d | %d | **%d** | **%d** | %d | %d |' % (
        tot['c'], (' (%d — копии)' % tot['cp']) if tot['cp'] else '', tot['o'], tot['s'], tot['y'], tot['yc'], tot['yw'],
        tot['im'], tot['no'], tot['q'], tot['w']))
    L += ['', 'Выбор — шаг с вариантами (`choice`) или взгляд в зеркало (`peek`). «Копии развилки» — тот же набор вариантов '
          'в другой сцене (показывается одна из них, по флагам). Действия с провалом и действия на прогулках — в своих '
          'таблицах в конце каждой главы.', '']

    # ---- концовки
    setters = collections.defaultdict(list)
    for sid in PLAY:
        for b in SC[sid]['beats']:
            for k in (b.get('set') or {}):
                if k in END_FLAGS and sid not in setters[END_FLAGS[k]]:
                    setters[END_FLAGS[k]].append(sid)
    L += ['## Концовки', '',
          'Экран «Конец» (`game.gd`, `_the_end`) проверяет флаги по порядку %s; если ни один не стоит — %s. '
          'Ниже — где ставится каждый флаг и какие выборы и условия ведут туда (по данным).' % (
              ' → '.join('`end_%s`' % k for k in END_ORDER), '«%s»' % END_NAMES.get(END_DEFAULT, END_DEFAULT)), '',
          '| Концовка | Флаг | Где ставится ← как туда попасть |', '|---|---|---|']
    for k in END_ORDER + [x for x in END_NAMES if x not in END_ORDER]:
        ways = []
        for s in setters.get(k, []):
            ways.append('`%s` ← %s' % (s, ' / '.join(how_reached(s))))
        note = '' if k in END_ORDER else ' (код не проверяет: это концовка по умолчанию)'
        L.append('| «%s» | `end_%s`%s | %s |' % (END_NAMES[k], k, note, cell('<br>'.join(ways) or 'нигде не ставится')))
    pat = []
    for sid in PLAY:
        for b in SC[sid]['beats']:
            c = b.get('if') or {}
            if len(c) == 1 and next(iter(c)) in END_FLAGS and b.get('else') and not b.get('set'):
                pat.append(END_FLAGS[next(iter(c))])
    if pat:
        L += ['', 'Эпилог после берега выбирается развилками в данных в порядке %s (иначе — «%s»), экран «Конец» — в порядке '
              '%s. Разница не важна, пока за прохождение ставится ровно один `end_*` (так проверяет `flag_audit.py`).' % (
                  ' → '.join('`end_%s`' % x for x in pat), END_NAMES.get('witness', 'witness'),
                  ' → '.join('`end_%s`' % x for x in END_ORDER))]
    def kind_of(g):
        rs = [r for r in g['reasons'] if r['depth'] == 0]
        if any(r['kind'] == 'решает' for r in rs):
            return 'решает'
        return 'сложность' if any(r['kind'] == 'сложность' for r in rs) else 'через'

    direct = sorted(k for k, g in GATES.items() if kind_of(g) == 'решает')
    diffic = sorted(k for k, g in GATES.items() if kind_of(g) == 'сложность')
    chain = sorted(k for k, g in GATES.items() if kind_of(g) == 'через')

    def reasons_line(k):
        return '; '.join(reason_txt(r) for r in reasons_list(k))

    L += ['', '**Флаги, которые решают концовку сами** (от них зависит, какие `end_*` можно поставить дальше):', '']
    for k in direct:
        L.append('- `%s` → %s' % (k, reasons_line(k)))
    L += ['', '**Сложность действий, от которых зависит концовка** (провал ведёт в другую концовку):', '']
    for k in diffic:
        L.append('- `%s` → %s' % (k, reasons_line(k)))
    L += ['', '**Косвенно** — флаг решает, поставится ли флаг из списков выше (цепочка до него; по графу сцен, без '
          'перебора всех сочетаний флагов):', '']
    for k in chain:
        L.append('- `%s` → %s' % (k, reasons_line(k)))
    decisive = []
    for c in CHOICES:
        targets = {o['go'] for o in c['opts']}
        es = [ends_reach(t) for t in targets]
        if len(targets) > 1 and any(e != es[0] for e in es):
            decisive.append('№%d (`%s`)' % (c['num'], c['sid']))
    L += ['', '**Выборы, от варианта которых прямо зависит, какие концовки ещё возможны:** %s.' % (', '.join(decisive) or 'нет'), '']

    # ---- находки
    all_rows = [r for ch in chs for r in rows_c.get(ch, [])]
    L += ['## Находки (собираются автоматически)', '']
    same_all = [c for c in CHOICES if len({o['go'] for o in c['opts']}) == 1]
    L.append('1. **Выборы, где все варианты ведут в одну сцену** (%d): %s.' % (
        len(same_all), ', '.join('№%d `%s` (%s)' % (c['num'], c['sid'], ' / '.join(short(o['text'] or 'молчание', 25) for o in c['opts'])) for c in same_all) or 'нет'))
    no = [r for r in all_rows if r['st'] == 'нет отклика' and r['why'] != 'все варианты ведут в одну сцену']
    L.append('2. **Варианты «нет отклика»** (ветка пустая, %d): %s.' % (len(no), ', '.join('%s «%s»' % (r['num'], short(r['text'], 40)) for r in no) or 'нет'))
    only_now = [r for r in all_rows if r['st'] == 'только сразу']
    L.append('3. **Варианты «только сразу»** (своя сцена есть, дальше ничего не меняется): %d — %s.' % (
        len(only_now), ', '.join(r['num'] for r in only_now) or 'нет'))
    sil_same = [r for r in all_rows if r['silent'] and r['same']]
    sil_own = [r for r in all_rows if r['silent'] and not r['same']]
    L.append('4. **Молчание**: %d раз — то же, что один из вариантов (%s); %d раз — своя ветка (%s).' % (
        len(sil_same), ', '.join(r['num'] for r in sil_same), len(sil_own), ', '.join(r['num'] for r in sil_own)))
    def places(k, n=3):
        ps = []
        for s, lab in SETS[k]:
            p = ('`%s`%s' % (s, (' (%s)' % lab) if lab else '')) if s else lab
            if p not in ps:
                ps.append(p)
        return ', '.join(ps[:n]) + (' …' if len(ps) > n else '')

    def draft_mark(k):
        if k not in DRAFT_MAP:
            return ''
        return ' — отклик в черновике' if DRAFT_MAP[k][0] == 'proposal' else ' — в черновике служебный'

    flags_set = sorted(k for k in SETS if k not in END_FLAGS and not k.startswith('_'))
    unread = [k for k in flags_set if not READS.get(k)]
    L.append('5. **Флаги, которые ставятся, но нигде не читаются** (%d): %s.' % (
        len(unread), ', '.join('`%s` (%s)%s' % (k, places(k), draft_mark(k)) for k in unread)))

    def later(k):
        return [r for s, _ in SETS[k] if s for r in deferred(k, s)]

    local = [k for k in flags_set if READS.get(k) and not later(k) and all(s for s, _ in SETS[k])]
    L.append('6. **Флаги, которые читаются только в той же сцене** (прогулка, разговор, развилка внутри сцены; дальше по '
             'игре не влияют, %d): %s.' % (len(local), ', '.join('`%s` (%s)%s' % (k, places(k, 2), draft_mark(k)) for k in local) or 'нет'))
    weak = [k for k in flags_set if later(k) and not any(r.kind in STRONG for r in later(k))]
    L.append('7. **Флаги, которые дальше видно только в карте главы или в «Ранее…»** (в сюжете не меняют ничего, %d): %s.' % (
        len(weak), ', '.join('`%s`' % k for k in weak) or 'нет'))
    init = {k for k, v in DATA['flags'].items() if v}
    noset = sorted(k for k in READS if k not in SETS and k not in init and k not in END_FLAGS)
    L.append('8. **Флаги, которые читаются, но нигде не ставятся** (%d): %s.' % (len(noset), ', '.join(
        '`%s` (читается: %s)' % (k, ', '.join(sorted({('`%s`' % r.sid) if r.sid else short(r.text, 40) for r in READS[k]}))) for k in noset) or 'нет'))
    never = []
    for c in CHOICES:
        for o in c['opts']:
            for k, e in (o['cond'] or {}).items():
                op, n = parse_e(e)
                need_pos = (op in ('>', '>=') and float(n) >= 0 and not (op == '>=' and float(n) == 0)) or (op == '==' and float(n) > 0)
                if need_pos and k not in SETS and not DATA['flags'].get(k):
                    never.append('%d.%s «%s» (`%s`)' % (c['num'], o['lab'], short(o['text'], 30), cond_txt(o['cond'])))
    L.append('9. **Варианты, которые по данным не могут показаться** (условие на флаг, который нигде не ставится): %s.' % (', '.join(never) or 'нет'))
    if copies:
        L.append('10. **Копии развилки** (тот же набор вариантов в другой сцене): %s.' % ', '.join('№%d = №%d' % (a, b) for a, b in sorted(copies.items())))
    L.append('')

    # ---- главы
    for ch in chs:
        title = A.txt(CHAPTERS[ch].get('title')) if ch > 1 else A.txt(DATA.get('title'))
        title = re.sub(r'^\s*глава\s*\d+\s*[.·:]?\s*', '', title or '', flags=re.I).strip()
        if title.isupper():
            title = title.capitalize()
        L += ['## Глава %d. %s' % (ch, title), '']
        L += ['### Выборы', '']
        L += table(rows_c.get(ch, []))
        if q_by.get(ch):
            L += ['', '### Действия с провалом (`qte` + `fail`)', '',
                  'Не выбор, а ловкость: «удалось» — продолжение сцены, «провал» — отдельная ветка.', '']
            L += table(q_by[ch])
        if rows_w.get(ch):
            L += ['', '### Прогулки и разговоры: действия, которые ставят флаги', '',
                  'Необязательные: точку можно не осматривать, тему — не выбирать. Без таймера.', '']
            L += table(rows_w[ch])
        if plain_w.get(ch):
            L += ['', '**Без флагов (ничего не ставят, только сцена на месте):** ' + '; '.join(plain_w[ch]) + '.']
        L.append('')
    text = '\n'.join(L).rstrip() + '\n'
    print('\n'.join(console))
    print('всего: выборов %d, вариантов %d: с откликом %d (только счётчик %d, только карта/«Ранее…» %d), только сразу %d, нет %d' % (
        tot['c'], tot['o'], tot['y'], tot['yc'], tot['yw'], tot['im'], tot['no']))
    print('концовку решают напрямую:', ', '.join(direct), '| сложность:', ', '.join(diffic), '| косвенно: %d' % len(chain))
    if check:
        return 0
    open(out_path, 'w', encoding='utf-8').write(text)
    print('→', os.path.relpath(out_path, os.getcwd()), '(%d КБ)' % (len(text.encode('utf-8')) // 1024))
    return 0


if __name__ == '__main__':
    sys.exit(main())
