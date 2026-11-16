#!/usr/bin/env python3
"""Собирает выпуск «Лёгкие шейдеры (ShaderLite)»:
  ShaderLite-<ver>.mrpack        — для Modrinth App / Prism / ATLauncher (моды качаются с cdn.modrinth.com)
  ShaderLite-mods-<ver>.zip      — простой ZIP: mods/ + shaderpacks/ + config/ (без модов, которые нельзя распространять)
  ShaderLite_v1.0.zip            — только шейдерпак
  shaderlite-adaptive-<ver>.jar  — свой мод «Адаптивное качество»
  README_RU.txt
Использование: python3 tools/build_release.py <папка_выпуска> <собранный_мод.jar> <папка_со_скачанными_модами>
"""
import hashlib, json, os, shutil, subprocess, sys, zipfile
VER = '1.0.0'; MC = '1.21.1'; LOADER = '0.16.14'
D = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
out, modjar, moddir = (os.path.abspath(a) for a in sys.argv[1:4])
os.makedirs(out, exist_ok=True)
mods = json.load(open(os.path.join(D, 'pack', 'mods.json')))
shader = os.path.join(out, 'ShaderLite_v1.0.zip')
subprocess.check_call([os.path.join(D, 'tools', 'pack_shader.sh'), shader])
myjar = os.path.join(out, f'shaderlite-adaptive-{VER}.jar')
shutil.copy(modjar, myjar)
shutil.copy(os.path.join(D, 'README_RU.txt'), os.path.join(out, 'README_RU.txt'))

def zadd(z, path, arc):
    z.write(path, arc, compress_type=zipfile.ZIP_DEFLATED)

def add_overrides(z, prefix):
    base = os.path.join(D, 'pack', 'overrides')
    for root, _, files in os.walk(base):
        for f in files:
            p = os.path.join(root, f)
            zadd(z, p, prefix + os.path.relpath(p, base))
    zadd(z, shader, prefix + 'shaderpacks/ShaderLite_v1.0.zip')

# 1) mrpack
index = {'formatVersion': 1, 'game': 'minecraft', 'versionId': VER,
         'name': 'Лёгкие шейдеры (ShaderLite)',
         'summary': 'Шейдеры для слабых ПК: Sodium + Iris + оптимизации + свой лёгкий шейдерпак и адаптивное качество',
         'files': [], 'dependencies': {'minecraft': MC, 'fabric-loader': LOADER}}
for m in mods:
    index['files'].append({'path': 'mods/' + m['file'], 'hashes': {'sha1': m['sha1'], 'sha512': m['sha512']},
                           'env': {'client': 'required', 'server': 'unsupported'},
                           'downloads': [m['url']], 'fileSize': m['size']})
mr = os.path.join(out, f'ShaderLite-{VER}.mrpack')
with zipfile.ZipFile(mr, 'w') as z:
    z.writestr('modrinth.index.json', json.dumps(index, ensure_ascii=False, indent=1), compress_type=zipfile.ZIP_DEFLATED)
    add_overrides(z, 'overrides/')
    zadd(z, myjar, f'overrides/mods/shaderlite-adaptive-{VER}.jar')

# 2) простой ZIP модов
lic = ['Моды в этом архиве и их лицензии', '=' * 40, '']
skipped = []
zp = os.path.join(out, f'ShaderLite-mods-{VER}.zip')
with zipfile.ZipFile(zp, 'w') as z:
    for m in mods:
        p = os.path.join(moddir, m['file'])
        assert hashlib.sha512(open(p, 'rb').read()).hexdigest() == m['sha512'], p
        if m['redistribute']:
            zadd(z, p, 'mods/' + m['file'])
            # текст лицензии из самого jar, если есть
            with zipfile.ZipFile(p) as j:
                for n in j.namelist():
                    b = n.rsplit('/', 1)[-1].upper()
                    if '/' not in n and (b.startswith('LICENSE') or b.startswith('COPYING')):
                        z.writestr(f'LICENSES/{m["slug"]}-{n}', j.read(n))
            lic.append(f'{m["title"]} {m["version"]} — {m["license"]}\n  {m["license_note"]}\n  исходный код: {m["source"]}\n  страница: https://modrinth.com/mod/{m["slug"]}\n')
        else:
            skipped.append(m)
    zadd(z, myjar, f'mods/shaderlite-adaptive-{VER}.jar')
    lic.append(f'ShaderLite: Adaptive Quality {VER} и шейдерпак ShaderLite — MIT (свои)\n')
    add_overrides(z, '')
    note = ['Эти моды нельзя класть в архив (лицензия запрещает распространение) — скачайте сами и положите в папку mods:', '']
    for m in skipped:
        note.append(f'{m["title"]} {m["version"]} ({m["license"]})\n  {m["url"]}\n  страница: https://modrinth.com/mod/{m["slug"]}\n  {m["license_note"]}\n')
    z.writestr('СКАЧАТЬ_ОТДЕЛЬНО.txt', '\n'.join(note))
    z.writestr('LICENSES/СПИСОК.txt', '\n'.join(lic))
    ld = os.path.join(D, 'pack', 'licenses')          # тексты лицензий, которых нет внутри jar
    for f in sorted(os.listdir(ld)):
        zadd(z, os.path.join(ld, f), 'LICENSES/' + f)
    zadd(z, os.path.join(D, 'shaders', 'ShaderLite', 'LICENSE.txt'), 'LICENSES/shaderlite-LICENSE.txt')
    zadd(z, os.path.join(D, 'README_RU.txt'), 'README_RU.txt')
for f in sorted(os.listdir(out)):
    print(f'{f:42s} {os.path.getsize(os.path.join(out, f)) / 1024:8.0f} КБ')
