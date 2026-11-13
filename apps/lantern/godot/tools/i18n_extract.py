#!/usr/bin/env python3
"""LANTERN i18n: собрать русские строки интерфейса из GDScript и сверить с переводами.

Исходный язык — русский, ключ перевода = сама строка (i18n/<язык>.po, msgid — русский текст).
  python3 tools/i18n_extract.py            — обновить i18n/lantern.pot и показать, чего не хватает в каждом языке
  python3 tools/i18n_extract.py --check    — код выхода 1, если в каком-то .po нет ключа, перевод пуст
                                             или не совпадают подстановки (%s, %d) / число форм «a|b|c»
  python3 tools/i18n_extract.py --missing en   — вывести непереведённые строки языка (по одной на строку, JSON)
Не переводятся: журнал для разработчика (AppLog.add, _log, print), комментарии, служебные ключи.
"""
import json, os, re, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = [os.path.join(ROOT, 'scripts')]
CYR = re.compile('[А-Яа-яЁёІіЇїЄєҐґ]')
STR = re.compile(r'"((?:[^"\\]|\\.)*)"')
SKIP_CALL = re.compile(r'(AppLog\.add|_log|print|push_warning|push_error|\b_event|\b_fixed_log)\s*\($')
PH = re.compile(r'%(?:\d+\$)?[-+0 ]*\d*(?:\.\d+)?[sdfx%]')


def unescape(s):
    return s.replace('\\n', '\n').replace('\\t', '\t').replace('\\"', '"').replace('\\\\', '\\')


def strings_of(path):
    out = []
    for ln, line in enumerate(open(path, encoding='utf-8'), 1):
        code = line
        st = code.lstrip()
        if st.startswith('#'):
            continue
        # убрать хвостовой комментарий (вне строк)
        res, inq, i = '', False, 0
        while i < len(code):
            c = code[i]
            if c == '\\' and inq:
                res += code[i:i + 2]; i += 2; continue
            if c == '"':
                inq = not inq
            if c == '#' and not inq:
                break
            res += c; i += 1
        for m in STR.finditer(res):
            s = m.group(1)
            if not CYR.search(s):
                continue
            before = res[:m.start()]
            # строка — аргумент журнала разработчика? (ищем ближайший незакрытый вызов)
            depth, j, skip = 0, len(before) - 1, False
            while j >= 0:
                ch = before[j]
                if ch == ')':
                    depth += 1
                elif ch == '(':
                    if depth == 0:
                        if SKIP_CALL.search(before[:j + 1]):
                            skip = True
                        break
                    depth -= 1
                j -= 1
            if skip:
                continue
            out.append((unescape(s), '%s:%d' % (os.path.relpath(path, ROOT), ln)))
    return out


def collect():
    d = {}
    for base in SRC:
        for dp, _, fs in os.walk(base):
            for f in sorted(fs):
                if f.endswith('.gd'):
                    for s, where in strings_of(os.path.join(dp, f)):
                        d.setdefault(s, []).append(where)
    return d


def po_escape(s):
    return s.replace('\\', '\\\\').replace('"', '\\"').replace('\n', '\\n')


def po_read(path):
    d, key, val, mode = {}, None, None, None
    if not os.path.exists(path):
        return d
    def flush():
        if key is not None and key != '':
            d[key] = val or ''
    for line in open(path, encoding='utf-8'):
        line = line.rstrip('\n')
        if line.startswith('msgid '):
            flush(); key = json.loads(line[6:]); val = ''; mode = 'id'
        elif line.startswith('msgstr '):
            val = json.loads(line[7:]); mode = 'str'
        elif line.startswith('"') and mode == 'id':
            key += json.loads(line)
        elif line.startswith('"') and mode == 'str':
            val += json.loads(line)
    flush()
    return d


def po_write(path, lang, pairs):
    with open(path, 'w', encoding='utf-8') as f:
        f.write('msgid ""\nmsgstr ""\n"Language: %s\\n"\n"Content-Type: text/plain; charset=UTF-8\\n"\n\n' % lang)
        for k in sorted(pairs):
            f.write('msgid "%s"\nmsgstr "%s"\n\n' % (po_escape(k), po_escape(pairs[k])))


def problems(key, val):
    if not val.strip():
        return 'пустой перевод'
    if sorted(PH.findall(key.split('|')[0])) != sorted(PH.findall(val.split('|')[0])):
        return 'подстановки не совпадают: %s / %s' % (PH.findall(key), PH.findall(val))
    return ''


def main():
    src = collect()
    i18n = os.path.join(ROOT, 'i18n')
    os.makedirs(i18n, exist_ok=True)
    langs = sorted(f[:-3] for f in os.listdir(i18n) if f.endswith('.po'))
    if '--missing' in sys.argv:
        lang = sys.argv[sys.argv.index('--missing') + 1]
        have = po_read(os.path.join(i18n, lang + '.po'))
        for k in sorted(src):
            if not have.get(k, '').strip():
                print(json.dumps(k, ensure_ascii=False))
        return 0
    if '--check' not in sys.argv:
        po_write(os.path.join(i18n, 'lantern.pot'), '', {k: '' for k in src})
    bad = 0
    print('строк интерфейса: %d' % len(src))
    for lang in langs:
        have = po_read(os.path.join(i18n, lang + '.po'))
        miss = [k for k in src if k not in have]
        wrong = [(k, problems(k, have[k])) for k in src if k in have and problems(k, have[k])]
        extra = [k for k in have if k not in src]
        print('%s: переведено %d, нет %d, с ошибками %d, лишних %d' % (lang, len(src) - len(miss) - len(wrong), len(miss), len(wrong), len(extra)))
        for k, w in wrong[:10]:
            print('   ! %s — %s' % (json.dumps(k, ensure_ascii=False)[:70], w))
        bad += len(miss) + len(wrong)
    return 1 if bad and '--check' in sys.argv else 0


if __name__ == '__main__':
    sys.exit(main())
