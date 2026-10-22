#!/usr/bin/env python3
"""Сборка Night Shift для itch.io.
   python3 tools/itch/build.py  →  dist/itch/
     nightshift-web.zip      — «играть в браузере» (HTML5): index.html в корне архива, всё относительными путями
     nightshift-offline.zip  — «скачать»: распаковал, открыл index.html двойным щелчком, интернет не нужен.
                               Браузер не читает файлы с диска через XHR, поэтому звуки/модели/тайминги
                               упакованы в pack-*.js (base64), а offline.js подставляет их вместо сетевых ответов."""
import base64, os, re, shutil, zipfile
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SRC = os.path.join(ROOT, 'public/games/nightshift')
OUT = os.path.join(ROOT, 'dist/itch')
SKIP = re.compile(r'(^|/)(_[^/]*|\.DS_Store)$')
PACKED = re.compile(r'^(snd/.*\.mp3|models/.*\.glb|timing(_en)?\.json)$')
CHUNK = 6 << 20  # ~6 МБ base64 на файл pack-N.js

def files():
    for d, _, fs in os.walk(SRC):
        for f in sorted(fs):
            rel = os.path.relpath(os.path.join(d, f), SRC).replace(os.sep, '/')
            if not SKIP.search(rel): yield rel

def zipdir(path, zpath, prefix=''):
    with zipfile.ZipFile(zpath, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for d, _, fs in os.walk(path):
            for f in sorted(fs):
                full = os.path.join(d, f); rel = os.path.relpath(full, path).replace(os.sep, '/')
                # mp3/glb/webp уже сжаты — кладём как есть, так архив собирается быстрее
                z.write(full, prefix + rel, zipfile.ZIP_STORED if re.search(r'\.(mp3|webp|jpg|png|glb)$', f) else zipfile.ZIP_DEFLATED)

shutil.rmtree(OUT, ignore_errors=True); os.makedirs(OUT)
all_files = list(files())

# ---------- веб-версия ----------
web = os.path.join(OUT, 'web')
for rel in all_files:
    os.makedirs(os.path.dirname(os.path.join(web, rel)), exist_ok=True); shutil.copy2(os.path.join(SRC, rel), os.path.join(web, rel))
zipdir(web, os.path.join(OUT, 'nightshift-web.zip'))

# ---------- офлайн-версия ----------
off = os.path.join(OUT, 'offline', 'NightShift')
os.makedirs(off)
packs, cur, size = [], [], 0
for rel in all_files:
    if PACKED.match(rel):
        b64 = base64.b64encode(open(os.path.join(SRC, rel), 'rb').read()).decode()
        cur.append(f'__PACK[{rel!r}]="{b64}";\n'); size += len(b64)
        if size > CHUNK: packs.append(cur); cur, size = [], 0
    elif rel != 'index.html' and rel != 'manifest.json':
        os.makedirs(os.path.dirname(os.path.join(off, rel)), exist_ok=True); shutil.copy2(os.path.join(SRC, rel), os.path.join(off, rel))
if cur: packs.append(cur)
names = []
for i, p in enumerate(packs, 1):
    n = f'pack-{i}.js'; names.append(n)
    open(os.path.join(off, n), 'w').write('window.__PACK=window.__PACK||{};\n' + ''.join(p))
shutil.copy2(os.path.join(ROOT, 'tools/itch/offline.js'), os.path.join(off, 'offline.js'))
html = open(os.path.join(SRC, 'index.html')).read()
html = re.sub(r'<link rel="manifest"[^>]*>\n?', '', html)
# шрифты прямо в CSS: Firefox не грузит @font-face с file://
for f in ('ns-logo', 'ns-ui'):
    b64 = base64.b64encode(open(os.path.join(SRC, f'fonts/{f}.woff'), 'rb').read()).decode()
    html = html.replace(f'url(fonts/{f}.woff)', f'url(data:font/woff;base64,{b64})')
tags = ''.join(f'<script src="{n}"></script>\n' for n in ['offline.js'] + names)
html = html.replace('<script src="i18n.js', tags + '<script src="i18n.js', 1)
open(os.path.join(off, 'index.html'), 'w').write(html)
open(os.path.join(off, 'README.txt'), 'w').write(
    'NIGHT SHIFT\n\nOpen index.html in Chrome, Edge or Firefox. No internet needed.\n'
    'Headphones recommended. Language: English / Russian (Settings).\n\n'
    'Откройте index.html в Chrome, Edge или Firefox. Интернет не нужен. Лучше в наушниках.\n')
zipdir(os.path.join(OUT, 'offline'), os.path.join(OUT, 'nightshift-offline.zip'))

for f in ('nightshift-web.zip', 'nightshift-offline.zip'):
    p = os.path.join(OUT, f); print(f'{f}: {os.path.getsize(p) / 1e6:.1f} МБ')
print(f'файлов в веб-версии: {len(all_files)} (лимит itch.io — 1000)')
