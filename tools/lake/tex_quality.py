#!/usr/bin/env python3
"""WHAT THE LAKE KEEPS — сжатие текстур высокого качества (владелец, 4 ноя: «текстуры как будто на них сжали»).
Godot по умолчанию жмёт текстуры в видеопамять «быстро»: на ПК — DXT1/DXT5 (4 бита на точку: кубики 4×4 и полосы
на коже, на градиентах неба и снега), на телефоне — ETC2. С high_quality: на ПК — BPTC (BC7), на телефоне — ASTC 4×4:
на глаз как исходник, вдвое больше места у текстур без прозрачности.
Правит настройки импорта (*.import лежат в godot/lake/assets — в git не идут, поэтому скрипт): кожа, лица, глаза, волосы
и одежда героев (assets/chars и модели героев в assets/models); свои пути — аргументами.
Потом переимпорт: $GODOT --headless --path godot/lake --import
    python3 tools/lake/tex_quality.py [glob ...]
"""
import glob
import os
import re
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '../..'))
LAKE = os.path.join(ROOT, 'godot/lake')
# по умолчанию — герои (лица и одежда на крупных планах: там «кубики» DXT заметнее всего; замер 4 ноя — кожа 36 → 52 дБ)
# и модели героев из Hush Till Six (дальнобойщики, полицейский). Места (assets/models/<место>_*, assets/ph) — как были:
# издалека разницы почти нет, а APK для телефона вырос бы ещё на ~250 МБ
DEFAULT = ['assets/chars/*', 'assets/models/frank_*', 'assets/models/gus_*', 'assets/models/ethan_*', 'assets/models/dayguard_*']


def main():
    pats = sys.argv[1:] or DEFAULT
    n = 0
    for pat in pats:
        for imp in glob.glob(os.path.join(LAKE, pat + '.import'), recursive=True):
            s = open(imp, encoding='utf-8').read()
            if 'importer="texture"' not in s or 'compress/mode=2' not in s:   # только сжатые в видеопамять
                continue
            t = re.sub(r'^compress/high_quality=false$', 'compress/high_quality=true', s, flags=re.M)
            if t != s:
                open(imp, 'w', encoding='utf-8').write(t)
                n += 1
    print('текстур переключено на высокое качество сжатия: %d' % n)


if __name__ == '__main__':
    main()
