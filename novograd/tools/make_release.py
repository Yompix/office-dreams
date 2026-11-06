#!/usr/bin/env python3
"""Сборка установщиков «Новоград: Кино»: архив для TLauncher (УСТАНОВИТЬ.bat) и .mrpack для Modrinth/Prism.

  python3 tools/make_release.py 0.3.5

Берёт build/libs/novograd-<версия>.jar, точные версии модов с modrinth.com (по id версии), кладёт всё в download/.
"""
import json, os, sys, urllib.request, zipfile

VER = sys.argv[1]
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JAR = os.path.join(ROOT, 'build', 'libs', f'novograd-{VER}.jar')
OUT = os.path.join(ROOT, 'download')

# (id версии на Modrinth, префикс для удаления старых версий, группа)
#  base — ставится всегда; big — по вопросу «Flashback и звуки»; studio — по вопросу «Киностудия»;
#  skins — по вопросу «скины»; replay — Replay Mod вместо Flashback.
MODS = [
    ('Mys3P7lK', 'fabric-api-', 'base'),
    ('SMxNOGZ6', 'sodium-', 'base'),
    ('bAo1Qhte', 'iris-', 'base'),
    ('ZUEjVQxa', 'camerautils-', 'base'),
    ('Cg10zLnR', 'freecam-', 'base'),
    ('BYhL3BDk', 'sound-physics-', 'base'),
    ('WuY7eJX9', 'wakes-', 'base'),
    ('GBivJmD5', 'particlerain-', 'base'),
    ('kSPJ4hQv', 'continuity-', 'base'),
    ('dhKbgdIb', 'visuality-', 'base'),
    ('HpMb5wGb', 'cloth-config-', 'base'),
    ('Bqen1mJX', None, 'shader'),
    ('nPzvTtsX', 'Flashback-', 'big'),
    ('54u7eI2t', 'AmbientSounds_', 'big'),
    ('v7fZmKvM', 'CreativeCore_', 'big'),
    ('GKRCswnb', 'bbs-', 'studio'),
    ('yyYvYfZI', 'CustomSkinLoader_', 'skins'),
    ('lvRKajvV', 'skinrestorer-', 'skins'),
    ('DKvQ3lQq', 'replaymod-', 'replay'),
]


def versions():
    ids = json.dumps([m[0] for m in MODS])
    url = 'https://api.modrinth.com/v2/versions?ids=' + urllib.request.quote(ids)
    req = urllib.request.Request(url, headers={'User-Agent': 'novograd-release'})
    data = {v['id']: v for v in json.load(urllib.request.urlopen(req, timeout=60))}
    out = []
    for vid, prefix, group in MODS:
        v = data[vid]
        f = next((x for x in v['files'] if x.get('primary')), v['files'][0])
        out.append(dict(file=f['filename'], url=f['url'], sha1=f['hashes']['sha1'], sha512=f['hashes']['sha512'],
                        size=f['size'], prefix=prefix, group=group))
    return out


def ps(url, dst):
    url = url.replace('%', '%%')  # в .bat «%2B» иначе съедается как параметр
    return ("powershell -NoProfile -Command \"$ProgressPreference='SilentlyContinue'; [Net.ServicePointManager]::SecurityProtocol='Tls12'; "
            f"Invoke-WebRequest -UseBasicParsing -Uri '{url}' -OutFile '{dst}'\"")


def bat(mods):
    jar = f'novograd-{VER}.jar'
    L = ['@echo off', 'chcp 65001 >nul', 'title Новоград: Кино — установка',
         'echo ============================================',
         'echo   НОВОГРАД: КИНО — установка модов и шейдеров',
         'echo ============================================',
         'echo Перед установкой: в TLauncher выбери версию Fabric 1.21.1, запусти её один раз и закрой игру.',
         'echo.',
         f'if not exist "%~dp0{jar}" (',
         '  echo Сначала распакуй архив в обычную папку, потом запусти УСТАНОВИТЬ.bat оттуда.',
         '  pause', '  exit /b', ')',
         'set "MC=%APPDATA%\\.minecraft"',
         'if not exist "%MC%" (', '  echo Не нашёл папку %MC%', '  set /p MC="Вставь путь к папке игры (где лежат mods): "', ')',
         'if not exist "%MC%\\mods" mkdir "%MC%\\mods"',
         'if not exist "%MC%\\shaderpacks" mkdir "%MC%\\shaderpacks"',
         'if not exist "%MC%\\config" mkdir "%MC%\\config"',
         'echo.',
         'echo Что ещё поставить (Y — да, N — нет):',
         'set /p BIG="  Flashback (запись и монтаж видео, 200 МБ) и AmbientSounds (звуки природы)? [Y/N]: "',
         'set /p STUDIO="  BBS — киностудия как Blockbuster+Aperture (актёры, камера, анимации)? [Y/N]: "',
         'set /p SKINS="  Свои скины: CustomSkinLoader + SkinRestorer (скины видно и в онлайне)? [Y/N]: "',
         'set REPLAY=N',
         'if /i not "%BIG%"=="Y" set /p REPLAY="  Replay Mod (запись повторов и пути камеры) вместо Flashback? [Y/N]: "',
         'echo.',
         'echo Удаляю старые версии этих модов (чтобы не было дубликатов)...',
         'del /q "%MC%\\mods\\novograd-*.jar" >nul 2>&1',
         'del /q "%MC%\\mods_old\\novograd-*.jar" >nul 2>&1',
         'del /q "%MC%\\mods\\entityculling-*.jar" >nul 2>&1']
    for m in mods:
        if m['prefix']:
            L.append(f'del /q "%MC%\\mods\\{m["prefix"]}*.jar" >nul 2>&1')
            L.append(f'del /q "%MC%\\mods_old\\{m["prefix"]}*.jar" >nul 2>&1')
    L += [f'copy /y "%~dp0{jar}" "%MC%\\mods\\" >nul', 'echo Скачиваю моды с modrinth.com ...']
    cond = {'big': 'BIG', 'studio': 'STUDIO', 'skins': 'SKINS', 'replay': 'REPLAY'}
    for m in mods:
        folder = 'shaderpacks' if m['group'] == 'shader' else 'mods'
        dst = f'%MC%\\{folder}\\{m["file"]}'
        line = ps(m['url'], dst)
        if m['group'] in cond:
            L.append(f'if /i "%{cond[m["group"]]}%"=="Y" (')
            L.append(f'  echo   {m["file"]}')
            L.append('  ' + line)
            L.append(')')
        else:
            L.append(f'echo   {m["file"]}')
            L.append(f'if not exist "{dst}" {line}')
    shader = next(m for m in mods if m['group'] == 'shader')['file']
    L += ['echo enableShaders=true> "%MC%\\config\\iris.properties"',
          f'echo shaderPack={shader}>> "%MC%\\config\\iris.properties"',
          'if not exist "%MC%\\mods\\fabric-api-*.jar" (',
          '  echo ОШИБКА: Fabric API не скачался. Проверь интернет и запусти УСТАНОВИТЬ.bat ещё раз.',
          '  pause', '  exit /b', ')',
          'rmdir "%MC%\\mods_old" >nul 2>&1',
          'echo.',
          'echo Готово! Запускай в TLauncher версию Fabric 1.21.1.',
          'echo В игре: Новый мир - Ещё - Тип мира «Новоград: Город».',
          'echo Своё кино: F7 — Киностудия Новограда. Если ставил BBS — его клавиши в Настройки - Управление.',
          'echo Слабый ноутбук? Настройки - Графика - Шейдеры - выключить.',
          'pause']
    return '\r\n'.join(L) + '\r\n'


README = """Новоград: Кино для TLauncher

1. В TLauncher выбери версию Fabric 1.21.1, запусти один раз и закрой.
2. Распакуй этот архив куда угодно и запусти УСТАНОВИТЬ.bat (нужен интернет).
   Он спросит, что ещё поставить: Flashback (запись видео), BBS (киностудия как Blockbuster/Aperture),
   свои скины (CustomSkinLoader + SkinRestorer), Replay Mod (если не Flashback).
3. Запусти Fabric 1.21.1 и создай новый мир: Ещё - Тип мира «Новоград: Город».

Своё кино без лишних модов: F7 в игре — «Киностудия Новограда»: свободная камера, ключевые кадры,
плавный пролёт, облёт, зум и наклон, дубли-актёры (сыграй все роли сам), кинофильтры.

Моды скачиваются с официального modrinth.com. Старые версии этих модов (и прошлые версии Новограда)
удаляются, новые ставятся на их место. Другие твои моды не трогаются.
Свой скин с CustomSkinLoader: положи картинку в .minecraft/CustomSkinLoader/LocalSkin/skins/<ник>.png
"""


def main():
    mods = versions()
    os.makedirs(OUT, exist_ok=True)
    jarname = f'novograd-{VER}.jar'
    with zipfile.ZipFile(os.path.join(OUT, f'Novograd-Kino-TLauncher-{VER}.zip'), 'w', zipfile.ZIP_DEFLATED) as z:
        z.writestr('УСТАНОВИТЬ.bat', bat(mods).encode('utf-8'))
        z.write(JAR, jarname)
        z.writestr('ПРОЧТИ.txt', README.encode('utf-8'))
    files = []
    for m in mods:
        if m['group'] == 'replay':
            continue  # Replay Mod и Flashback вместе не ставим: в Modrinth-сборке остаётся Flashback
        optional = m['group'] in ('big', 'studio', 'skins')
        server = 'required' if m['prefix'] == 'fabric-api-' else ('optional' if m['prefix'] == 'skinrestorer-' else 'unsupported')
        files.append({'path': ('shaderpacks/' if m['group'] == 'shader' else 'mods/') + m['file'],
                      'hashes': {'sha1': m['sha1'], 'sha512': m['sha512']},
                      'env': {'client': 'optional' if optional else 'required', 'server': server},
                      'downloads': [m['url']], 'fileSize': m['size']})
    index = {'formatVersion': 1, 'game': 'minecraft', 'versionId': VER, 'name': 'Новоград: Кино',
             'summary': 'Новоград + шейдеры Complementary, Sodium, съёмка кино (Flashback, BBS, Camera Utils, Freecam), свои скины, живые звуки',
             'files': files, 'dependencies': {'minecraft': '1.21.1', 'fabric-loader': '0.16.14'}}
    shader = next(m for m in mods if m['group'] == 'shader')['file']
    with zipfile.ZipFile(os.path.join(OUT, f'Novograd-Kino-{VER}.mrpack'), 'w', zipfile.ZIP_DEFLATED) as z:
        z.writestr('modrinth.index.json', json.dumps(index, ensure_ascii=False, indent=1))
        z.write(JAR, 'overrides/mods/' + jarname)
        z.writestr('overrides/config/iris.properties', f'enableShaders=true\nshaderPack={shader}\n')
    print('ok', VER, len(mods), 'mods')


if __name__ == '__main__':
    main()
