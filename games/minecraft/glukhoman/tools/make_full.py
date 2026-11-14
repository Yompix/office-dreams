#!/usr/bin/env python3
"""Архивы «Глухомани» для игроков: всё внутри, интернет при установке не нужен.

  python3 tools/make_full.py 0.1.0 [папка-вывода]

Glukhoman-Full-<версия>.zip — INSTALL.bat (сам создаёт профиль «Глухомань» в официальном лаунчере и, по желанию,
кладёт моды в .minecraft\\mods для TLauncher), ПОЧИНИТЬ_ЗАПУСК.bat, профиль Fabric Loader, мод и библиотеки, «Прочитай.txt».
Glukhoman-Mods-<версия>.zip — только папка mods и инструкция для ручной установки (и для сервера).
Моды берутся с modrinth.com (точные версии по id), только с открытыми лицензиями.
"""
import json, os, sys, urllib.parse, urllib.request, zipfile

VER = sys.argv[1]
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUTDIR = sys.argv[2] if len(sys.argv) > 2 else os.path.join(ROOT, 'download')
JAR = os.path.join(ROOT, 'build', 'libs', f'glukhoman-{VER}.jar')
LOADER = '0.16.14'
H = {'User-Agent': 'glukhoman-release'}
CACHE = '/tmp/claude-0/full_cache'

# (id версии на Modrinth, нужен ли на сервере). Fabric API обязателен; остальное — FPS на слабых ноутбуках.
MODS = [
    ('Mys3P7lK', True),   # Fabric API (Apache-2.0)
    ('SMxNOGZ6', False),  # Sodium (Polyform Shield) — быстрее рисует лес и туман
    ('N08Z8wog', True),   # Lithium (LGPL-3.0)
    ('sOzRw3CG', True),   # FerriteCore (MIT)
    ('aLnBqxFW', False),  # ImmediatelyFast (LGPL-3.0)
    ('NnNX8LBn', True),   # ModernFix (LGPL-3.0)
]


def get(url):
    return urllib.request.urlopen(urllib.request.Request(url, headers=H), timeout=120).read()


def crlf(path):
    return open(path, encoding='utf-8').read().replace('\r\n', '\n').replace('\n', '\r\n').encode('utf-8')


def main():
    os.makedirs(OUTDIR, exist_ok=True)
    os.makedirs(CACHE, exist_ok=True)
    vers = {v['id']: v for v in json.loads(get('https://api.modrinth.com/v2/versions?ids=' + urllib.parse.quote(json.dumps([m[0] for m in MODS]))))}
    fabric = get(f'https://meta.fabricmc.net/v2/versions/loader/1.21.1/{LOADER}/profile/json')
    vid = json.loads(fabric)['id']
    jars = []
    for mid, server in MODS:
        v = vers[mid]
        f = next((x for x in v['files'] if x.get('primary')), v['files'][0])
        path = os.path.join(CACHE, f['filename'])
        if not os.path.exists(path) or os.path.getsize(path) != f['size']:
            open(path, 'wb').write(get(f['url']))
        jars.append((path, f['filename'], server))
    out = os.path.join(OUTDIR, f'Glukhoman-Full-{VER}.zip')
    with zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED) as z:
        z.writestr('INSTALL.bat', crlf(os.path.join(ROOT, 'tools', 'install_full.bat')).replace(b'@@VID@@', vid.encode()))
        z.writestr('ПОЧИНИТЬ_ЗАПУСК.bat', crlf(os.path.join(ROOT, 'tools', 'fix_launch.bat')))
        z.writestr('files/' + vid + '.json', fabric)
        z.write(JAR, f'files/mods/glukhoman-{VER}.jar')
        for path, name, _ in jars:
            z.write(path, f'files/mods/{name}')
        z.writestr('Прочитай.txt', README.replace('@@VER@@', VER).replace('@@VID@@', vid).replace('\n', '\r\n').encode('utf-8'))
    print('ok', out, round(os.path.getsize(out) / 1e6, 2), 'MB')
    out2 = os.path.join(OUTDIR, f'Glukhoman-Mods-{VER}.zip')
    with zipfile.ZipFile(out2, 'w', zipfile.ZIP_DEFLATED) as z:
        z.write(JAR, f'mods/glukhoman-{VER}.jar')
        for path, name, server in jars:
            z.write(path, f'mods/{name}')
        server = ', '.join([f'glukhoman-{VER}.jar'] + [n for _, n, sv in jars if sv])
        z.writestr('Прочитай.txt', MANUAL.replace('@@VER@@', VER).replace('@@SERVER@@', server).replace('\n', '\r\n').encode('utf-8'))
    print('ok', out2, round(os.path.getsize(out2) / 1e6, 2), 'MB')


README = """ГЛУХОМАНЬ @@VER@@ — хоррор с сюжетом (Minecraft Java Edition 1.21.1)
Yompix Games. Создатель — Yompix.

Тайга, октябрь 1996-го. Посёлок Еловый, закрытая метеостанция «Полынь-17» и старая шахта.
Анна Ветрова пропала, а в эфире звучит её голос: «Не отвечайте, если позовут по имени…»
5 глав, 2 концовки. Можно одному или с друзьями (2–4).

КАК УСТАНОВИТЬ (один раз)
1. Распакуй архив в обычную папку (например, на рабочий стол).
2. Закрой лаунчер Minecraft / TLauncher и запусти INSTALL.bat.
   Он спросит, играешь ли ты через TLauncher, — ответь Y или N.
3. Официальный лаунчер: внизу слева выбери профиль «Глухомань» и нажми «Играть».
   TLauncher: в списке версий выбери @@VID@@.
   Первый запуск дольше — лаунчер сам докачает Minecraft 1.21.1.
4. В игре: Одиночная игра — Создать мир — Ещё — Тип мира «Глухомань» — Создать.
   Хозяин мира выбирает сложность: «Жутко», «Страшно» или «Кошмар».

С ДРУЗЬЯМИ
- Через LANTERN: хозяин открывает мир «Глухомань», друзья заходят по коду комнаты.
- Или: пауза — «Открыть для друзей».
- У всех должен стоять этот же архив (та же версия мода).
- Сюжет общий: любой двигает историю, остальные видят то же. Рядом с друзьями рассудок держится лучше.
  Жердь не двигается, пока на неё смотрит хоть кто-то — один смотрит, другой работает.

УПРАВЛЕНИЕ
R — фонарик / керосинка (тратит батарейки/масло). H — подсказка, куда идти.
Shift — красться (Слухач слепой и идёт на звук). ПКМ с камешком — бросить, чтобы отвлечь.
ПКМ по рации, записке, ящику, щиту — взаимодействовать. Записки остаются в инвентаре.
Настройки (меньше скримеров, без вспышек, субтитры, яркость) — кнопка «Глухомань» в меню паузы.

СОВЕТЫ
Играй в наушниках. Не бегай без нужды. Береги батарейки. В домах с печью безопасно.
Если слишком темно — пауза — «Глухомань» — Яркость.

НЕ ЗАПУСКАЕТСЯ ИЛИ ТОРМОЗИТ
Запусти ПОЧИНИТЬ_ЗАПУСК.bat (попросит права администратора): включит файл подкачки, выставит память и лёгкие настройки.

Свой сервер: Fabric-сервер 1.21.1 — см. «Прочитай.txt» в архиве Glukhoman-Mods,
в server.properties: level-type=glukhoman\\:taiga

Моды-библиотеки с modrinth.com, открытые лицензии (Fabric API, Sodium, Lithium, FerriteCore, ImmediatelyFast, ModernFix).
Звуки и музыка сгенерированы кодом. Голоса — нейросеть XTTS-v2 (Coqui, некоммерческая лицензия).
Не связано с Mojang и Microsoft.
"""

MANUAL = """Глухомань @@VER@@ — моды одним архивом, ставится вручную

ИГРОКУ
1. Fabric для Minecraft 1.21.1:
   - TLauncher: выбери версию «Fabric 1.21.1», запусти один раз и закрой.
   - Официальный лаунчер: установщик https://fabricmc.net/use/installer/ — 1.21.1 — Install.
2. Win+R — %appdata%\\.minecraft — Enter.
3. Скопируй файлы из папки mods архива в папку mods (создай, если нет). Старые glukhoman-*.jar удали.
4. Запусти Fabric 1.21.1. Создать мир — Ещё — Тип мира «Глухомань».

СЕРВЕРУ (Fabric-сервер 1.21.1)
В папку mods сервера — только эти файлы из mods: @@SERVER@@
(Sodium и ImmediatelyFast — только для игроков). В server.properties: level-type=glukhoman\\:taiga
(мир нужно создать заново). Игрокам нужен тот же мод.

Не связано с Mojang и Microsoft.
"""

if __name__ == '__main__':
    main()
