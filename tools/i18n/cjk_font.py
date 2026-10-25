#!/usr/bin/env python3
"""Шрифт для китайского, японского и корейского: только знаки, которые есть в переводе (Google Fonts, Noto Sans SC/JP/KR Bold, SIL OFL 1.1).
   python3 tools/i18n/cjk_font.py [zh ja ko] → public/games/nightshift/fonts/cjk/<язык>-N.woff2 и fonts/cjk/faces.js
   faces.js: window.NS_CJK = { names: [[файл, unicode-range]…], zh: […], ja: […], ko: […] } — i18n.js подключает их как шрифт NSCJK
   (names — названия языков в списке «Язык», нужны на любом языке игры). Перезапускать после каждой правки перевода zh/ja/ko."""
import json, os, re, sys, urllib.parse, urllib.request
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, 'public/games/nightshift/fonts/cjk')
FAM = {'zh': 'Noto+Sans+SC', 'ja': 'Noto+Sans+JP', 'ko': 'Noto+Sans+KR'}
NAMES = {'zh': '简体中文', 'ja': '日本語', 'ko': '한국어'}
UA = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36'   # иначе Google отдаёт ttf
# знаки, которых нет в шрифте интерфейса (Oswald): иероглифы, кана, хангыль, полноширинная пунктуация
WIDE = re.compile('[⺀-鿿가-힯ᄀ-ᇿ㄰-㆏豈-﫿︰-﹏＀-￯]')

def get(url):
    return urllib.request.urlopen(urllib.request.Request(url, headers={'User-Agent': UA}), timeout=60).read()

def faces(lang, text, tag):
    chars = sorted(set(WIDE.findall(text)))
    out, chunk = [], []
    def flush():
        if not chunk: return
        css = get(f'https://fonts.googleapis.com/css2?family={FAM[lang]}:wght@700&text=' + urllib.parse.quote(''.join(chunk))).decode()
        for i, (src, rng) in enumerate(re.findall(r'src: url\((https://[^)]+)\)[^;]*;\s*unicode-range: ([^;]+);', css)):
            f = f'{tag}-{len(out) + 1}.woff2'; open(os.path.join(OUT, f), 'wb').write(get(src)); out.append([f, rng.strip()])
        chunk.clear()
    for c in chars:   # адрес запроса не длиннее ~6 КБ
        chunk.append(c)
        if len(urllib.parse.quote(''.join(chunk))) > 5800: flush()
    flush()
    return out, len(chars)

os.makedirs(OUT, exist_ok=True)
langs = sys.argv[1:] or [l for l in FAM if os.path.exists(os.path.join(ROOT, f'tools/i18n/{l}.json'))]
fp = os.path.join(OUT, 'faces.js')
cur = {}
if os.path.exists(fp):
    try: cur = json.loads(re.search(r'window\.NS_CJK = (\{.*\});', open(fp).read(), re.S).group(1))
    except Exception: cur = {}
for f in os.listdir(OUT):   # старые куски пересобираемых языков — долой
    if f.endswith('.woff2') and (f.split('-')[0] in langs or f.startswith('names-')): os.remove(os.path.join(OUT, f))
cur['names'] = []
for l in FAM:
    fs, n = faces(l, NAMES[l], f'names-{l}'); cur['names'] += fs
for l in langs:
    T = json.load(open(os.path.join(ROOT, f'tools/i18n/{l}.json')))
    EX = json.load(open(os.path.join(ROOT, 'tools/i18n/extra.json'))) if os.path.exists(os.path.join(ROOT, 'tools/i18n/extra.json')) else {}
    text = ' '.join(T['lines'].values()) + ' '.join(T['ui'].values()) + ' '.join((EX.get(l) or {}).values()) + NAMES[l]   # + вывески из extra.json
    cur[l], n = faces(l, text, l)
    kb = sum(os.path.getsize(os.path.join(OUT, f)) for f, _ in cur[l]) / 1024
    print(f'{l}: знаков {n}, файлов {len(cur[l])}, {kb:.0f} КБ')
open(fp, 'w').write('/* шрифт NSCJK (Noto Sans SC/JP/KR Bold, OFL): только знаки перевода — tools/i18n/cjk_font.py */\nwindow.NS_CJK = ' + json.dumps(cur, ensure_ascii=False) + ';\n')
print('faces.js:', ', '.join(f'{k} {len(v)}' for k, v in cur.items()))
