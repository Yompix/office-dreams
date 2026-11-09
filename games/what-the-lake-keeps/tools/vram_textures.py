#!/usr/bin/env python3
"""Текстуры 3D — сжатыми для видеокарты (VRAM Compressed: S3TC/BPTC на ПК), карты нормалей — RGTC.

Раньше все 540 текстур героев, моделей и окружения импортировались «Lossy» (WebP): на диске мало, но в видеопамяти
каждая 2K — 21 МБ без сжатия (в кафе ~1,8 ГБ видеопамяти) — встроенная графика (Ryzen 3 + Radeon Vega) не выдерживала.
Сжатые — в 4–8 раз меньше и быстрее для слабых видеокарт. Рисунки и картинки интерфейса (assets/textures) — без сжатия
для видеокарты (артефакты на линиях карандаша заметны).

    python3 tools/vram_textures.py        # правит .import в godot/assets/{chars,models,ph}
    $GODOT --headless --path godot --import   # переимпорт
Запускать после tools/fetch_assets.py и пересборки героев (build_chars.sh) — .import появляются при первом импорте.
"""
import os, re, sys

LAKE = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'godot')
VRAM_DIRS = ['assets/chars', 'assets/models', 'assets/ph']
FLAT_DIRS = ['assets/textures']           # рисунки, интерфейс: как было (Lossy, без сжатия для видеокарты)
NORMAL = re.compile(r'(_nor_|_nor\.|-norm|_normal|normal_|_nrm)', re.I)
SKIP_EXT = ('.hdr', '.exr')                # небо HDR — как было
# Предел размера (по большей стороне) там, где на экране мелко: зубы и язык — 256, глаза и обувь — 512; модели (окружение,
# дальнобойщики из прошлой игры) и Poly Haven — 1024, кроме земли и снега; рельеф одежды и шероховатость окружения — 512.
# Лица и одежда героев (цвет) — как есть (2K). Сборка Windows после этого — 748 МБ, архив — 492 МБ.
LIMITS = [
    (r'^assets/chars/.*(teeth|tongue)', 256),
    (r'^assets/chars/.*(eye|boot|shoe)', 512),
    (r'^assets/chars/.*(nor_gl|norm|normal)', 512),      # рельеф ткани одежды — мелкий на экране
    (r'^assets/ph/.*_arm_', 512),                        # шероховатость/металл/затенение окружения — плавные
    (r'^assets/models/', 1024),
    (r'^assets/ph/(?!asphalt_snow|snow_02)', 1024),
]


def patch(path, mode, normal, limit=0):
    s = open(path, encoding='utf-8').read()
    if 'importer="texture"' not in s:
        return False
    t = re.sub(r'^compress/mode=\d+$', 'compress/mode=%d' % mode, s, flags=re.M)
    t = re.sub(r'^process/size_limit=\d+$', 'process/size_limit=%d' % limit, t, flags=re.M)
    if normal is not None:
        t = re.sub(r'^compress/normal_map=\d+$', 'compress/normal_map=%d' % normal, t, flags=re.M)
    if t != s:
        open(path, 'w', encoding='utf-8').write(t)
        return True
    return False


def main():
    changed = 0
    for d, mode in [(x, 2) for x in VRAM_DIRS] + [(x, 1) for x in FLAT_DIRS]:
        root = os.path.join(LAKE, d)
        for dp, _, fs in os.walk(root):
            for f in fs:
                if not f.endswith('.import') or f[:-7].lower().endswith(SKIP_EXT):
                    continue
                src = f[:-7]
                normal = (1 if NORMAL.search(src) else 0) if mode == 2 else None
                rel = os.path.relpath(os.path.join(dp, src), LAKE).replace(os.sep, '/')
                limit = next((n for rx, n in LIMITS if re.search(rx, rel, re.I)), 0) if mode == 2 else 0
                if patch(os.path.join(dp, f), mode, normal, limit):
                    changed += 1
    print('изменено .import:', changed)
    return 0


if __name__ == '__main__':
    sys.exit(main())
