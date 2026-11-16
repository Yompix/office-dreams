#!/usr/bin/env python3
"""Скачивает Minecraft 1.21.1 + Fabric Loader в отдельную папку для тестов под xvfb.
Без звуков и лишних ассетов (только индекс), только linux-библиотеки. Использование:
  python3 tools/setup_client.py <папка_кэша>
"""
import json, os, sys, urllib.request, hashlib
MC = '1.21.1'; LOADER = '0.16.14'
root = os.path.abspath(sys.argv[1]); os.makedirs(root, exist_ok=True)
UA = {'User-Agent': 'shaderlite-build/1.0'}
def fetch(url, path, sha1=None):
    if os.path.exists(path): return path
    os.makedirs(os.path.dirname(path), exist_ok=True)
    data = urllib.request.urlopen(urllib.request.Request(url, headers=UA)).read()
    if sha1: assert hashlib.sha1(data).hexdigest() == sha1, url
    open(path + '.part', 'wb').write(data); os.replace(path + '.part', path)
    return path
def getj(url): return json.load(urllib.request.urlopen(urllib.request.Request(url, headers=UA)))
man = getj('https://piston-meta.mojang.com/mc/game/version_manifest_v2.json')
vurl = [v['url'] for v in man['versions'] if v['id'] == MC][0]
vj = getj(vurl)
json.dump(vj, open(os.path.join(root, 'version.json'), 'w'))
cp = []
cj = fetch(vj['downloads']['client']['url'], f'{root}/versions/{MC}/{MC}.jar', vj['downloads']['client']['sha1'])
def allowed(lib):
    rules = lib.get('rules')
    if not rules: return True
    ok = False
    for r in rules:
        osn = r.get('os', {}).get('name')
        if osn is None or osn == 'linux': ok = r['action'] == 'allow'
    return ok
for lib in vj['libraries']:
    if not allowed(lib): continue
    a = lib.get('downloads', {}).get('artifact')
    if not a: continue
    if 'natives' in lib['name'] and not lib['name'].endswith('natives-linux'): continue
    cp.append(fetch(a['url'], f"{root}/libraries/{a['path']}", a['sha1']))
fab = getj(f'https://meta.fabricmc.net/v2/versions/loader/{MC}/{LOADER}/profile/json')
for lib in fab['libraries']:
    g, n, v = lib['name'].split(':')
    p = f"{g.replace('.', '/')}/{n}/{v}/{n}-{v}.jar"
    cp.append(fetch(lib['url'].rstrip('/') + '/' + p, f'{root}/libraries/{p}', lib.get('sha1')))
ai = vj['assetIndex']
idxp = fetch(ai['url'], f"{root}/assets/indexes/{ai['id']}.json", ai['sha1'])
# только мелкие нужные ассеты: иконки, шрифт, русский язык, встроенные паки (без звуков, музыки, Realms)
for k, v in json.load(open(idxp))['objects'].items():
    if k.startswith(('icons/', 'minecraft/font/', 'minecraft/resourcepacks/')) or k in ('minecraft/lang/ru_ru.json', 'pack.mcmeta', 'realms/lang/en_us.json'):
        h = v['hash']; fetch(f'https://resources.download.minecraft.net/{h[:2]}/{h}', f'{root}/assets/objects/{h[:2]}/{h}', h)
open(os.path.join(root, 'classpath.txt'), 'w').write(':'.join(cp + [cj]))
open(os.path.join(root, 'launch.txt'), 'w').write(json.dumps({'main': fab['mainClass'], 'assetIndex': ai['id']}))
print('ok', len(cp), 'libs', fab['mainClass'])
