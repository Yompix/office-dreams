#!/usr/bin/env python3
"""WHAT THE LAKE KEEPS — надписи в мире и интерфейс (RU + EN) из кода игры: вывески, щиты, объявления, записки,
телевизор, меню, настройки, подсказки. Реплики, ремарки и выборы — в данных (ch1–ch5.json), их собирает scenario_docx.py;
здесь — то, что написано прямо в скриптах godot/lake/scripts.
  python3 tools/lake/world_text.py                       → список в терминал
  python3 tools/lake/world_text.py --docx СЦЕНАРИЙ.docx  → приложение в конец документа (тот же файл)
Сбор — по шаблонам кода: «"RU" if ru else "EN"», {"ru": …, "en": …}, пары списков RU/EN. Отладочные строки (журнал,
print, ошибки) и комментарии не берутся. Что записано иначе, может не попасть — список проверочный, не исчерпывающий."""
import json
import os
import re
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '../..'))
SCR = os.path.join(ROOT, 'godot/lake/scripts')
CYR = re.compile('[А-Яа-яЁё]')
STR = r'"(?:[^"\\\n]|\\.)*"'
EXPR = r'(?:%s)(?:\s*\+\s*(?:%s))*' % (STR, STR)
COND = r'\)?\s*if\s+[\w\.\s!=()]*?(?:==\s*"ru")?[\w\.\s()\\]*?else\s*\(?\s*'
FMT = r'(?:\s*%\s*[\w\.\[\]]+)?'   # «"…%d" % flags.trust» — подстановка числа после строки
PAIR = re.compile(r'(%s)%s%s(%s)' % (EXPR, FMT, COND, EXPR))
DICT = re.compile(r'"ru"\s*:\s*(%s)\s*,\s*"en"\s*:\s*(%s)' % (EXPR, EXPR))
NEST = re.compile(r'\(\s*(%s)\s+if\s+[^"]+?\s+else\s+(%s)\s*\)%s\(?\s*(%s)\s+if\s+[^"]+?\s+else\s+(%s)\s*\)' % (EXPR, EXPR, COND, EXPR, EXPR))
ARR = re.compile(r'\[([^\[\]]*)\]\s*if\s+[\w\.\s!=()"\\]*?else\s*\[([^\[\]]*)\]')
DEBUG = ('_log(', 'print(', 'prints(', 'printerr(', 'push_error(', 'push_warning(', 'assert(')
GROUPS = [   # (заголовок, файлы)
    ('Надписи в мире: заправка «24», кафе, магазин, деревня, санаторий, дорога', ['world/drive_scene.gd', 'world/station_props.gd',
     'world/village.gd', 'world/sana.gd', 'world/car.gd', 'world/news_tv.gd', 'world/signs.gd']),
    ('Надписи и записки в сценах глав', ['chapter1.gd', 'chapter2.gd', 'chapter3.gd', 'chapter4.gd', 'chapter5.gd']),
    ('Интерфейс: меню, пауза, настройки, дерево выборов, подсказки', ['ui/hud.gd', 'ui/touch_ctl.gd', 'settings.gd', 'explore.gd',
     'game.gd']),
]


def _uncomment(src):
    """Комментарии (# вне строк) — прочь, строки и переносы — как были."""
    out, i, n, ins = [], 0, len(src), False
    while i < n:
        c = src[i]
        if ins:
            out.append(c)
            if c == '\\' and i + 1 < n:
                out.append(src[i + 1])
                i += 2
                continue
            if c == '"' or c == '\n':
                ins = False
        elif c == '"':
            ins = True
            out.append(c)
        elif c == '#':
            while i < n and src[i] != '\n':
                i += 1
            continue
        else:
            out.append(c)
        i += 1
    return ''.join(out)


def _logical(src):
    """Строки кода, склеенные по незакрытым скобкам (многострочные выражения GDScript)."""
    res, cur, depth = [], [], 0
    lines = src.split('\n')
    for i, ln in enumerate(lines):
        cur.append(ln.strip())
        nxt = lines[i + 1].strip() if i + 1 < len(lines) else ''
        if nxt.startswith(('else', 'if ', '+', 'or ', 'and ')) or ln.rstrip().endswith(('\\', '+')):
            body = re.sub(STR, '""', ln)
            depth += body.count('(') + body.count('[') + body.count('{') - body.count(')') - body.count(']') - body.count('}')
            continue
        body = re.sub(STR, '""', ln)
        depth += body.count('(') + body.count('[') + body.count('{') - body.count(')') - body.count(']') - body.count('}')
        if depth <= 0:
            res.append(' '.join(cur))
            cur, depth = [], 0
    if cur:
        res.append(' '.join(cur))
    return res


def _val(expr):
    return ''.join(json.loads(s) for s in re.findall(STR, expr))


def _news(src):
    """Телевизор (news_tv.gd): const T := {"ru": {ключ: текст}, "en": {…}} — пары по ключу."""
    m = re.search(r'\{\s*"ru"\s*:\s*\{(.*?)\}\s*,\s*"en"\s*:\s*\{(.*?)\}\s*,?\s*\}', src, re.S)
    if not m:
        return []
    kv = lambda s: {k: _val(v) for k, v in re.findall(r'"(\w+)"\s*:\s*(%s)' % EXPR, s)}
    ru, en = kv(m.group(1)), kv(m.group(2))
    return [(ru[k], en.get(k, '—')) for k in ru]


def collect():
    rows = []   # (группа, файл, RU, EN)
    for title, files in GROUPS:
        seen = set()
        for f in files:
            p = os.path.join(SCR, f)
            if not os.path.exists(p):
                continue
            src = _uncomment(open(p, encoding='utf-8').read())
            pairs = _news(src) if f.endswith('news_tv.gd') else []
            for m in re.finditer(r'const (\w+)_RU\s*:=\s*\[(.*?)\]', src, re.S):   # const X_RU / X_EN — по порядку
                en = re.search(r'const %s_EN\s*:=\s*\[(.*?)\]' % m.group(1), src, re.S)
                ra, ea = [json.loads(s) for s in re.findall(STR, m.group(2))], ([json.loads(s) for s in re.findall(STR, en.group(1))] if en else [])
                pairs += [(r, ea[i] if i < len(ea) else '—') for i, r in enumerate(ra)]
            for ln in _logical(src):
                if any(d in ln for d in DEBUG) or not CYR.search(ln):
                    continue
                got = False
                for m in NEST.finditer(ln):
                    pairs += [(_val(m.group(1)), _val(m.group(3))), (_val(m.group(2)), _val(m.group(4)))]
                    got = True
                ln = NEST.sub('', ln)
                for m in ARR.finditer(ln):
                    ra = [json.loads(s) for s in re.findall(STR, m.group(1))]
                    ea = [json.loads(s) for s in re.findall(STR, m.group(2))]
                    if any(CYR.search(r) for r in ra):
                        pairs += [(r, ea[i] if i < len(ea) else '—') for i, r in enumerate(ra)]
                        got = True
                for m in DICT.finditer(ln):
                    pairs.append((_val(m.group(1)), _val(m.group(2))))
                    got = True
                for m in PAIR.finditer(ln):
                    r = _val(m.group(1))
                    if CYR.search(r):
                        pairs.append((r, _val(m.group(2))))
                        got = True
                if not got and ' := {' not in ln and 'const ' not in ln:
                    for s in re.findall(STR, ln):   # русский текст без пары — тоже показать (английского рядом нет)
                        v = json.loads(s)
                        if CYR.search(v) and len(v) > 1:
                            pairs.append((v, '—'))
            for r, e in pairs:
                key = (r.strip(), e.strip())
                if key in seen or not r.strip():
                    continue
                seen.add(key)
                rows.append((title, f, r.strip(), e.strip()))
    return rows


def to_docx(path, rows):
    from docx import Document
    from docx.shared import Pt, Cm
    doc = Document(path)
    doc.add_page_break()
    doc.add_heading('Приложение. Надписи в мире и интерфейс', 1)
    doc.add_paragraph('Всё, что написано не в репликах, а прямо в мире и на экране: вывески, щиты, объявления, записки, '
                      'телевизор, меню, настройки, подсказки. Собрано из кода игры (godot/lake/scripts) по шаблонам; '
                      'перенос строки на вывеске — « / ». «%d», «%s» — сюда игра подставляет число или имя. '
                      'Английский «—» — пары в коде рядом нет (проверить, есть ли английский вариант).')
    cur = None
    for title, f, r, e in rows:
        if title != cur:
            cur = title
            doc.add_heading(title, 2)
            t = doc.add_table(rows=1, cols=3)
            t.style = 'Light Grid Accent 1'
            for j, h in enumerate(['RU', 'EN', 'Где (файл)']):
                t.cell(0, j).text = h
        c = t.add_row().cells
        for j, v in enumerate([r.replace('\n', ' / '), e.replace('\n', ' / '), f]):
            c[j].text = v
            for p in c[j].paragraphs:
                for run in p.runs:
                    run.font.size = Pt(9)
        c[0].width, c[1].width, c[2].width = Cm(7.2), Cm(7.2), Cm(2.8)
    doc.save(path)


def main():
    rows = collect()
    if '--docx' in sys.argv:
        path = sys.argv[sys.argv.index('--docx') + 1]
        to_docx(path, rows)
        print('приложение: %d надписей → %s' % (len(rows), path))
    else:
        for title, f, r, e in rows:
            print('%-22s | %s | %s' % (f, r.replace('\n', ' / '), e.replace('\n', ' / ')))
        print('всего: %d' % len(rows))


if __name__ == '__main__':
    main()
