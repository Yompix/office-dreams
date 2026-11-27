#!/usr/bin/env python3
"""Полный архив «Новоград» для официального лаунчера Minecraft: все моды уже внутри, интернет при установке не нужен.

  python3 tools/make_full.py 0.6.2

Кладёт download/Novograd-Full-<версия>.zip. В архиве INSTALL.bat (сам создаёт профиль «Новоград» с отдельной папкой игры),
моды с открытыми лицензиями (MIT/Apache/LGPL/GPL/Polyform Shield), шейдер MakeUp, профиль Fabric Loader.
Моды «все права защищены» и шейдеры с особой лицензией в архив не кладём.
"""
import json, os, sys, urllib.parse, urllib.request, zipfile

VER = sys.argv[1]
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JAR = os.path.join(ROOT, 'build', 'libs', f'novograd-{VER}.jar')
OUTDIR = os.environ.get('NOVOGRAD_OUT') or os.path.join(ROOT, 'download')
OUT = os.path.join(OUTDIR, f'Novograd-Full-{VER}.zip')
LOADER = '0.17.2'  # не ниже 0.17: GeckoLib 4.8.4 внутри Новограда требует fabricloader >=0.17
H = {'User-Agent': 'novograd-release'}

# Голос: Simple Voice Chat (все права защищены — в архив не кладём, INSTALL.bat качает его с modrinth.com по хэшу)
VOICE = 'dyhq1XyO'

# id версии на Modrinth (те же, что в make_release.py), куда класть
MODS = [
    ('Mys3P7lK', 'mods'),  # Fabric API
    ('SMxNOGZ6', 'mods'),  # Sodium
    ('bAo1Qhte', 'mods'),  # Iris
    ('Cg10zLnR', 'mods'),  # Freecam
    ('kSPJ4hQv', 'mods'),  # Continuity
    ('HpMb5wGb', 'mods'),  # Cloth Config (нужен More Culling)
    ('N08Z8wog', 'mods'),  # Lithium
    ('sOzRw3CG', 'mods'),  # FerriteCore
    ('aLnBqxFW', 'mods'),  # ImmediatelyFast
    ('NnNX8LBn', 'mods'),  # ModernFix
    ('zze1l7gB', 'mods'),  # More Culling
    ('BYhL3BDk', 'mods'),  # Sound Physics Remastered
    ('WuY7eJX9', 'mods'),  # Wakes
    ('GBivJmD5', 'mods'),  # Particle Rain
    ('dhKbgdIb', 'mods'),  # Visuality
    ('t4YRxQug', 'shaderpacks'),  # MakeUp Ultra Fast
]


def get(url):
    return urllib.request.urlopen(urllib.request.Request(url, headers=H), timeout=120).read()


def main():
    ids = json.dumps([m[0] for m in MODS] + [VOICE])
    vers = {v['id']: v for v in json.loads(get('https://api.modrinth.com/v2/versions?ids=' + urllib.parse.quote(ids)))}
    fabric = get(f'https://meta.fabricmc.net/v2/versions/loader/1.21.1/{LOADER}/profile/json')
    vid = json.loads(fabric)['id']
    cache = '/tmp/claude-0/full_cache'
    os.makedirs(cache, exist_ok=True)
    bat = open(os.path.join(ROOT, 'tools', 'install_full.bat'), encoding='utf-8').read().replace('\r\n', '\n').replace('\n', '\r\n')
    clean = open(os.path.join(ROOT, 'tools', 'clean_pc.bat'), encoding='utf-8').read().replace('\r\n', '\n').replace('\n', '\r\n')
    names = []
    with zipfile.ZipFile(OUT, 'w', zipfile.ZIP_DEFLATED) as z:
        z.writestr('INSTALL.bat', bat.replace('@@VID@@', vid).encode('utf-8'))
        z.writestr('CLEAN-PC.bat', clean.encode('utf-8'))
        z.writestr('files/' + vid + '.json', fabric)
        z.write(JAR, f'files/mods/novograd-{VER}.jar')
        for vid_, folder in MODS:
            v = vers[vid_]
            f = next((x for x in v['files'] if x.get('primary')), v['files'][0])
            path = os.path.join(cache, f['filename'])
            if not os.path.exists(path) or os.path.getsize(path) != f['size']:
                open(path, 'wb').write(get(f['url']))
            z.write(path, f'files/{folder}/{f["filename"]}')
            names.append(f['filename'])
        vf = next((x for x in vers[VOICE]['files'] if x.get('primary')), vers[VOICE]['files'][0])
        z.writestr('files/online.json', json.dumps([dict(file=vf['filename'], url=vf['url'], sha1=vf['hashes']['sha1'], sha512=vf['hashes']['sha512'],
                                                         size=vf['size'], prefix='voicechat-', folder='mods', group='voice')], indent=1))
        z.writestr('ПРОЧТИ.txt', README.replace('@@VER@@', VER).encode('utf-8'))
    print('ok', OUT, round(os.path.getsize(OUT) / 1e6, 1), 'MB', len(names), 'files')
    # то же без установщика: папки mods и shaderpacks — скопировать в .minecraft руками
    out2 = os.path.join(OUTDIR, f'Novograd-Mods-{VER}.zip')
    with zipfile.ZipFile(OUT) as src, zipfile.ZipFile(out2, 'w', zipfile.ZIP_DEFLATED) as z:
        for n in src.namelist():
            if n.startswith('files/mods/') or n.startswith('files/shaderpacks/'):
                z.writestr(n[len('files/'):], src.read(n))
        z.writestr('ПРОЧТИ.txt', MANUAL.replace('@@VER@@', VER).encode('utf-8'))
    print('ok', out2, round(os.path.getsize(out2) / 1e6, 1), 'MB')
    import subprocess
    if subprocess.run([sys.executable, os.path.join(ROOT, 'tools', 'check_deps.py'), OUT, out2, '--loader', LOADER]).returncode:
        raise SystemExit('check_deps: не хватает зависимостей')


MANUAL = """Новоград @@VER@@ — все моды одним архивом, ставится вручную (без установщиков)

1. Fabric для Minecraft 1.21.1, загрузчик (Fabric Loader) 0.17 или новее — на 0.16 мод не запустится:
   - TLauncher: выбери версию «Fabric 1.21.1» с самым новым загрузчиком, запусти один раз и закрой.
   - Официальный лаунчер: скачай установщик https://fabricmc.net/use/installer/ ,
     выбери 1.21.1 — Install, в лаунчере запусти «fabric-loader-1.21.1» один раз и закрой.
2. Win+R — впиши %appdata%\\.minecraft — Enter.
3. Скопируй из архива папки mods и shaderpacks в эту папку (согласись объединить).
   Старый novograd-*.jar в mods удали, если он там есть.
4. Запусти Fabric 1.21.1. Новый мир — Ещё — Тип мира «Новоград: Город».

Prism Launcher / ElyPrism / другие на основе Prism: создай сборку Minecraft 1.21.1 + Fabric (загрузчик 0.17.2 или новее),
нажми «Папка» (Open folder) и скопируй папки mods и shaderpacks из этого архива в папку minecraft сборки.
Или просто импортируй Novograd-Kino-@@VER@@.mrpack («Добавить сборку» — «Импорт»).

Fabric API (fabric-api-*.jar) обязателен — он лежит в mods этого архива, не удаляй его.

Голос (по желанию): скачай Simple Voice Chat для Fabric 1.21.1 с modrinth.com/mod/simple-voice-chat и положи в mods.
Говорить — зажми V; телефон → «Рация».

Шейдеры (по желанию): Настройки — Графика — Шейдеры — MakeUp. На слабом ноутбуке лучше без них.
Вылетает — выдели игре 3–4 ГБ памяти (TLauncher: ползунок памяти; официальный лаунчер: профиль — Ещё — Аргументы JVM: -Xmx4G).

Моды с modrinth.com, открытые лицензии. Музыка радио: Kevin MacLeod (incompetech.com), CC BY 4.0.
Создатель и сценарист — Yompix.
"""


README = """Новоград @@VER@@ — полный архив для официального лаунчера Minecraft (Java Edition 1.21.1)

Всё уже внутри: мод Новоград, Fabric, Sodium, Iris, моды ускорения, быстрые шейдеры MakeUp.
Интернет нужен лаунчеру (он сам скачает Minecraft 1.21.1) и для голоса: INSTALL.bat скачает мод Simple Voice Chat
с modrinth.com (его нельзя класть в архив). Говорить — зажми V; телефон → «Рация» — звонки и настройки микрофона.

1. Распакуй архив в обычную папку (например, на рабочий стол).
2. Закрой лаунчер Minecraft и запусти INSTALL.bat.
   Он спросит, слабый ли компьютер (ноутбук, встроенная видеокарта) — тогда шейдеры будут выключены.
3. Открой лаунчер Minecraft: внизу слева у кнопки «Играть» выбери профиль «Новоград» и нажми «Играть».
4. В игре: Новый мир — Ещё — Тип мира «Новоград: Город».

У Новограда своя папка: %APPDATA%\\.minecraft\\novograd — там его моды, миры и настройки.
Другие твои моды и миры не трогаются.
Обновить: скачай новый архив и снова запусти INSTALL.bat — миры сохранятся.
Тормозит или мусор на диске — CLEAN-PC.bat чистит компьютер (спрашивает, что удалять).
Тормозит в игре — Настройки — Графика — «Производительность: низкая».
Prism Launcher / ElyPrism: возьми Novograd-Kino-@@VER@@.mrpack («Добавить сборку» — «Импорт») или архив
Novograd-Kino-TLauncher (его установщик сам находит сборки Prism).

Моды взяты с modrinth.com, у каждого своя открытая лицензия (MIT, Apache, LGPL, GPL, Polyform Shield).
Музыка радио: Kevin MacLeod (incompetech.com), CC BY 4.0.
Создатель и сценарист — Yompix.
"""

if __name__ == '__main__':
    main()
