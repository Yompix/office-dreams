#!/usr/bin/env python3
"""Сборка установщиков «Новоград: Кино»: архив для TLauncher (УСТАНОВИТЬ.bat) и .mrpack для Modrinth/Prism.

  python3 tools/make_release.py 0.3.5

Берёт build/libs/novograd-<версия>.jar, точные версии модов с modrinth.com (по id версии), кладёт всё в download/
(или в папку из переменной NOVOGRAD_OUT). Обязательные моды (Fabric API, Cloth Config, Sodium…) лежат ВНУТРИ архивов —
установщик их не скачивает и не удаляет старые, пока новые не на месте. В конце — tools/check_deps.py по обоим архивам.
"""
import hashlib, json, os, sys, urllib.request, zipfile

VER = sys.argv[1]
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JAR = os.path.join(ROOT, 'build', 'libs', f'novograd-{VER}.jar')
OUT = os.environ.get('NOVOGRAD_OUT') or os.path.join(ROOT, 'download')
LOADER = '0.17.2'  # не ниже 0.17: GeckoLib 4.8.4 внутри Новограда требует fabricloader >=0.17

# (id версии на Modrinth, префикс для удаления старых версий, группа)
#  base — ставится всегда; big — по вопросу «Flashback и звуки»; studio — по вопросу «Киностудия»;
#  skins — по вопросу «скины»; replay — Replay Mod вместо Flashback.
MODS = [
    ('Mys3P7lK', 'fabric-api-', 'base'),
    ('SMxNOGZ6', 'sodium-', 'base'),
    ('bAo1Qhte', 'iris-', 'base'),
    ('ZUEjVQxa', 'camerautils-', 'base'),
    ('Cg10zLnR', 'freecam-', 'base'),
    ('BYhL3BDk', 'sound-physics-', 'fx'),
    ('WuY7eJX9', 'wakes-', 'fx'),
    ('GBivJmD5', 'particlerain-', 'fx'),
    ('kSPJ4hQv', 'continuity-', 'base'),
    ('dhKbgdIb', 'visuality-', 'fx'),
    ('HpMb5wGb', 'cloth-config-', 'base'),
    # производительность: сервер-логика, память, интерфейс, загрузка, отсечение невидимого
    ('N08Z8wog', 'lithium-', 'base'),
    ('sOzRw3CG', 'ferritecore-', 'base'),
    ('aLnBqxFW', 'ImmediatelyFast-', 'base'),
    ('NnNX8LBn', 'modernfix-', 'base'),
    ('zze1l7gB', 'moreculling-', 'base'),
    # шейдеры: быстрый MakeUp Ultra Fast — включён по умолчанию; Complementary — красивый, переключается в Iris
    ('t4YRxQug', None, 'shader'),
    ('Bqen1mJX', None, 'shader'),
    ('nPzvTtsX', 'Flashback-', 'big'),
    ('54u7eI2t', 'AmbientSounds_', 'big'),
    ('v7fZmKvM', 'CreativeCore_', 'big'),
    ('GKRCswnb', 'bbs-', 'studio'),
    ('yyYvYfZI', 'CustomSkinLoader_', 'skins'),
    ('lvRKajvV', 'skinrestorer-', 'skins'),
    ('DKvQ3lQq', 'replaymod-', 'replay'),
    # голос между игроками (0.9.10): все права защищены — только ссылкой, установщик качает и проверяет хэш
    ('dyhq1XyO', 'voicechat-', 'voice'),
]


# Моды с открытыми лицензиями, которые можно класть в архив (те же, что в make_full.py). Остальные — только ссылкой.
BUNDLE_OK = {'Mys3P7lK', 'SMxNOGZ6', 'bAo1Qhte', 'Cg10zLnR', 'kSPJ4hQv', 'HpMb5wGb', 'N08Z8wog', 'sOzRw3CG', 'aLnBqxFW',
             'NnNX8LBn', 'zze1l7gB', 'BYhL3BDk', 'WuY7eJX9', 'GBivJmD5', 'dhKbgdIb', 't4YRxQug'}
# Где искать уже скачанные файлы (по sha1), чтобы не качать заново; новые кладутся в первую папку.
CACHE = [d for d in os.environ.get('NOVOGRAD_CACHE', '/tmp/claude-0/full_cache:/tmp/claude-0/mods_dl').split(':') if d]


def sha1(path):
    return hashlib.sha1(open(path, 'rb').read()).hexdigest()


_idx = None


def cached(m):
    """Локальный путь к файлу мода (проверен по sha1); нет в кэше — скачать."""
    global _idx
    if _idx is None:
        _idx = {}
        for d in CACHE:
            if os.path.isdir(d):
                for f in os.listdir(d):
                    p = os.path.join(d, f)
                    if os.path.isfile(p) and os.path.getsize(p) < 400_000_000:
                        _idx[sha1(p)] = p
    if m['sha1'] in _idx:
        return _idx[m['sha1']]
    os.makedirs(CACHE[0], exist_ok=True)
    dst = os.path.join(CACHE[0], m['file'])
    req = urllib.request.Request(m['url'], headers={'User-Agent': 'novograd-release'})
    open(dst, 'wb').write(urllib.request.urlopen(req, timeout=300).read())
    if sha1(dst) != m['sha1']:
        os.remove(dst)
        raise SystemExit(f'хэш не совпал: {m["file"]}')
    _idx[m['sha1']] = dst
    return dst


def versions():
    ids = json.dumps([m[0] for m in MODS])
    url = 'https://api.modrinth.com/v2/versions?ids=' + urllib.request.quote(ids)
    req = urllib.request.Request(url, headers={'User-Agent': 'novograd-release'})
    data = {v['id']: v for v in json.load(urllib.request.urlopen(req, timeout=60))}
    out = []
    for vid, prefix, group in MODS:
        v = data[vid]
        f = next((x for x in v['files'] if x.get('primary')), v['files'][0])
        out.append(dict(vid=vid, file=f['filename'], url=f['url'], sha1=f['hashes']['sha1'], sha512=f['hashes']['sha512'],
                        size=f['size'], prefix=prefix, group=group, bundle=vid in BUNDLE_OK,
                        folder='shaderpacks' if group == 'shader' else 'mods'))
    return [m for m in out if m['group'] not in ('studio', 'replay')]  # BBS и Replay Mod установщик не ставил никогда


README = r"""Новоград: Кино @@VER@@ — для TLauncher и лаунчеров на основе Prism (Prism Launcher, ElyPrism и др.)

Главное уже внутри архива: Новоград, Fabric API, Sodium, Iris, моды ускорения, шейдер MakeUp.
Интернет нужен для голоса (Simple Voice Chat — его нельзя класть в архив, установщик скачает и проверит) и для
необязательного (Flashback, звуки природы, скины, шейдер Complementary, Camera Utils); если не скачается — игра всё равно запустится.
Голос: зажми V — говорить; телефон → «Рация» — звонки, рация службы и банды, настройки микрофона.

Нужен Fabric Loader 0.17 или новее (на 0.16 игра скажет «GeckoLib требует fabricloader 0.17»).

TLauncher:
1. Распакуй этот архив в обычную папку и запусти УСТАНОВИТЬ.bat. Выбери «TLauncher / .minecraft».
   Установщик сам добавит версию fabric-loader-@@LOADER@@-1.21.1.
2. В TLauncher выбери версию fabric-loader-@@LOADER@@-1.21.1 (если её нет в списке — «Fabric 1.21.1» с самым
   новым загрузчиком) и запусти. Создай новый мир: Ещё — Тип мира «Новоград: Город».

Prism Launcher / ElyPrism / другие на основе Prism:
- Проще всего: «Добавить сборку» — «Импорт» — файл Novograd-Kino-@@VER@@.mrpack (Fabric поставится сам).
- Или: создай сборку Minecraft 1.21.1 + Fabric (загрузчик @@LOADER@@ или новее), закрой лаунчер, запусти УСТАНОВИТЬ.bat
  и выбери свою сборку из списка (он сам находит папки instances в %APPDATA%).
- Вручную: сборка — «Папка» (Open folder) — minecraft\mods; скопируй туда всё из files\mods этого архива,
  а files\shaderpacks — в minecraft\shaderpacks. Старые novograd-*.jar и fabric-api-*.jar из mods удали.

Установщик сначала кладёт новые моды во временную папку и только потом убирает старые версии тех же модов —
Fabric API никогда не пропадёт. В конце он проверяет, что все обязательные моды на месте.
Другие твои моды не трогаются.
Вылетает или тормозит? ПОЧИНИТЬ_ЗАПУСК.bat — включит файл подкачки, выставит память для TLauncher,
облегчит настройки, выключит шейдеры и уберёт тяжёлые моды в mods_backup.
Тормозит в игре — Настройки — Графика — «Производительность: низкая».
Свой скин с CustomSkinLoader: положи картинку в .minecraft/CustomSkinLoader/LocalSkin/skins/<ник>.png
"""


def main():
    mods = versions()
    os.makedirs(OUT, exist_ok=True)
    jarname = f'novograd-{VER}.jar'
    nov = dict(file=jarname, sha1=sha1(JAR), folder='mods', group='base', prefix='novograd-', required=True)
    shader = next(m for m in mods if m['group'] == 'shader' and m['bundle'])['file']
    bundled = [nov] + [dict(file=m['file'], sha1=m['sha1'], folder=m['folder'], group=m['group'], prefix=m['prefix'],
                            required=m['group'] == 'base' and m['folder'] == 'mods') for m in mods if m['bundle']]
    optional = [dict(file=m['file'], sha1=m['sha1'], url=m['url'], folder=m['folder'], group=m['group'], prefix=m['prefix'])
                for m in mods if not m['bundle']]
    fabric = urllib.request.urlopen(urllib.request.Request(f'https://meta.fabricmc.net/v2/versions/loader/1.21.1/{LOADER}/profile/json',
                                                          headers={'User-Agent': 'novograd-release'}), timeout=60).read()
    vid = json.loads(fabric)['id']
    manifest = dict(version=VER, shader=shader, loader=LOADER, profile=vid, bundled=bundled, optional=optional)
    with zipfile.ZipFile(os.path.join(OUT, f'Novograd-Kino-TLauncher-{VER}.zip'), 'w', zipfile.ZIP_DEFLATED) as z:
        inst = open(os.path.join(ROOT, 'tools', 'install_kino.bat'), encoding='utf-8').read().replace('\r\n', '\n').replace('\n', '\r\n')
        z.writestr('УСТАНОВИТЬ.bat', inst.encode('utf-8'))
        z.writestr('manifest.json', json.dumps(manifest, ensure_ascii=False, indent=1))
        z.writestr('optional.json', json.dumps(optional, ensure_ascii=False, indent=1))  # для tools/check_deps.py
        z.write(JAR, 'files/mods/' + jarname)
        z.writestr(f'files/{vid}.json', fabric)  # профиль Fabric Loader для TLauncher (versions/)
        for m in mods:
            if m['bundle']:
                z.write(cached(m), f'files/{m["folder"]}/{m["file"]}')
            else:
                cached(m)  # необязательные — в кэш, чтобы check_deps проверил и их зависимости
        z.writestr('ПРОЧТИ.txt', README.replace('@@VER@@', VER).replace('@@LOADER@@', LOADER).replace('\n', '\r\n').encode('utf-8'))
        fix = open(os.path.join(ROOT, 'tools', 'fix_launch.bat'), encoding='utf-8').read().replace('\r\n', '\n').replace('\n', '\r\n')
        z.writestr('ПОЧИНИТЬ_ЗАПУСК.bat', fix.encode('utf-8'))  # вылетает/тормозит — один запуск всё настраивает
        clean = open(os.path.join(ROOT, 'tools', 'clean_pc.bat'), encoding='utf-8').read().replace('\r\n', '\n').replace('\n', '\r\n')
        z.writestr('CLEAN-PC.bat', clean.encode('utf-8'))  # находит мусор, показывает и удаляет только то, что выберешь
    # .mrpack (Modrinth App, Prism, ElyPrism): обязательные моды лежат прямо в overrides/mods — импорт не зависит
    # от скачивания; по ссылкам (с sha1/sha512) — только необязательное и то, что нельзя класть в архив по лицензии
    files = []
    for m in mods:
        if m['bundle'] and m['group'] in ('base', 'shader'):
            continue
        server = 'optional' if m['prefix'] == 'skinrestorer-' else 'required' if m['group'] == 'voice' else 'unsupported'
        files.append({'path': m['folder'] + '/' + m['file'], 'hashes': {'sha1': m['sha1'], 'sha512': m['sha512']},
                      'env': {'client': 'required' if m['group'] == 'voice' else 'optional', 'server': server}, 'downloads': [m['url']], 'fileSize': m['size']})
    index = {'formatVersion': 1, 'game': 'minecraft', 'versionId': VER, 'name': 'Новоград: Кино',
             'summary': 'Новоград + Fabric API, быстрые шейдеры MakeUp, Sodium, Lithium, ImmediatelyFast; по желанию Complementary, Flashback, свои скины, живые звуки',
             'files': files, 'dependencies': {'minecraft': '1.21.1', 'fabric-loader': LOADER}}
    with zipfile.ZipFile(os.path.join(OUT, f'Novograd-Kino-{VER}.mrpack'), 'w', zipfile.ZIP_DEFLATED) as z:
        z.writestr('modrinth.index.json', json.dumps(index, ensure_ascii=False, indent=1))
        z.write(JAR, 'overrides/mods/' + jarname)
        for m in mods:
            if m['bundle'] and m['group'] in ('base', 'shader'):
                z.write(cached(m), f'overrides/{m["folder"]}/{m["file"]}')
        z.writestr('overrides/config/iris.properties', f'enableShaders=true\nshaderPack={shader}\n')
    print('ok', VER, len(mods), 'mods,', sum(1 for m in mods if m['bundle']), 'inside the archives')
    import subprocess
    r = subprocess.run([sys.executable, os.path.join(ROOT, 'tools', 'check_deps.py'),
                        os.path.join(OUT, f'Novograd-Kino-TLauncher-{VER}.zip'), os.path.join(OUT, f'Novograd-Kino-{VER}.mrpack')]
                       + sum((['--cache', d] for d in CACHE), []))
    if r.returncode:
        raise SystemExit('check_deps: не хватает зависимостей')


if __name__ == '__main__':
    main()
