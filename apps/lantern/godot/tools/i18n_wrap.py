#!/usr/bin/env python3
"""Обернуть русские строки интерфейса в L.t("…") (одноразовая правка кода; повторный запуск ничего не меняет).
Не трогаем: const (в константах нельзя вызывать функции — их текст Label/Button переводит сам Godot), сравнения и поиск
подстрок, ветки match, журнал разработчика. python3 tools/i18n_wrap.py [--dry]"""
import os, re, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import i18n_extract as x

SKIP_LINE = re.compile(r'^\s*(const |static var _re|##|#)|RegEx\.create|\bmatch\b')
BAD_BEFORE = re.compile(r'(==|!=|\bin|contains\(|begins_with\(|ends_with\(|find\(|rfind\(|has\(|L\.t\(|L\.n\(|trim_prefix\(|trim_suffix\(|replace\(|split\(|get_slice\()\s*$')
changed = 0
for dp, _, fs in os.walk(os.path.join(x.ROOT, 'scripts')):
    for f in sorted(fs):
        if not f.endswith('.gd') or f in ('l.gd',):
            continue
        p = os.path.join(dp, f)
        out = []
        for line in open(p, encoding='utf-8'):
            st = line.strip()
            if SKIP_LINE.search(line) or (st.startswith('"') and st.rstrip().endswith(':')) or not x.CYR.search(line):
                out.append(line); continue
            # вырезаем хвостовой комментарий так же, как сборщик
            res, pos, new = '', 0, ''
            code_end = len(line)
            inq = False; i = 0
            while i < len(line):
                c = line[i]
                if c == '\\' and inq: i += 2; continue
                if c == '"': inq = not inq
                if c == '#' and not inq: code_end = i; break
                i += 1
            code, tail = line[:code_end], line[code_end:]
            last = 0
            for m in x.STR.finditer(code):
                s = m.group(1)
                before = code[:m.start()]
                after = code[m.end():]
                skip = not x.CYR.search(s) or BAD_BEFORE.search(before) or after.lstrip().startswith(':') or before.rstrip().endswith('[') and after.lstrip().startswith(']')
                if not skip:
                    # аргумент журнала разработчика?
                    depth, j = 0, len(before) - 1
                    while j >= 0:
                        ch = before[j]
                        if ch == ')': depth += 1
                        elif ch == '(':
                            if depth == 0:
                                if x.SKIP_CALL.search(before[:j + 1]): skip = True
                                break
                            depth -= 1
                        j -= 1
                if skip:
                    continue
                new += code[last:m.start()] + 'L.t(' + m.group(0) + ')'
                last = m.end()
                changed += 1
            new += code[last:]
            out.append(new + tail)
        if '--dry' not in sys.argv:
            open(p, 'w', encoding='utf-8').write(''.join(out))
print('обёрнуто строк:', changed)
