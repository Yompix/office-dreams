#!/usr/bin/env python3
"""Собрать i18n/<язык>.po из таблиц переводчика i18n/src/<язык>_*.py (словарь T: номер ключа → перевод).
Номера — позиции в i18n/_keys.json (список русских строк, с которого делался перевод). Строки без перевода в таблице
(буквы транслита, имена файлов, названия языков) остаются как в исходнике. python3 tools/i18n_build.py"""
import glob, json, os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import i18n_extract as x

I18N = os.path.join(x.ROOT, 'i18n')
keys = json.load(open(os.path.join(I18N, '_keys.json'), encoding='utf-8'))
src = x.collect()
langs = sorted({os.path.basename(f).rsplit('_', 1)[0] for f in glob.glob(os.path.join(I18N, 'src', '*_*.py'))})
for lang in langs:
    pairs = {}
    for f in sorted(glob.glob(os.path.join(I18N, 'src', lang + '_*.py'))):
        ns = {}
        exec(open(f, encoding='utf-8').read(), ns)
        for i, v in ns['T'].items():
            pairs[keys[i]] = v
    extra = os.path.join(I18N, 'src', lang + '.extra.json')      # строки, появившиеся после нумерации: {русский: перевод}
    if os.path.exists(extra):
        pairs.update(json.load(open(extra, encoding='utf-8')))
    out = {k: pairs.get(k, k) for k in src}
    x.po_write(os.path.join(I18N, lang.replace('-', '_') + '.po'), lang, out)
    same = sum(1 for k in src if k not in pairs)
    print('%s: %d строк, без перевода (как в исходнике): %d' % (lang, len(out), same))
