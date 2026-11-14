"""WHAT THE LAKE KEEPS — титры и LICENSES.txt из свода лицензий (godot/docs/LICENSES.md).
Пишет:
  godot/credits/credits.json — текст экрана «Авторы» (RU и EN): §4.2 и §4.1 свода, по строкам;
  godot/credits/LICENSES.txt — §4.1 (EN) + полный текст лицензий: движок Godot и его компоненты (из самого движка,
      scripts/dev/dump_licenses.gd), SIL OFL 1.1 (шрифты), CC0 1.0, условия Rokoko и CMU (../hush-till-six/web/licenses).
Обе папки идут в сборку (export_presets.cfg, include_filter "credits/*"): экран «Авторы» в меню и после концовки
показывает их на всех платформах (на Android рядом с apk файлов нет). Для Windows и архивов сайта LICENSES.txt ещё
кладётся рядом с игрой — tools/export_licenses.sh.
Запуск: python3 tools/licenses_txt.py   (после правки свода — снова, результат в git)
Ничего не придумывать: всё — из свода. Правообладатель — OWNER ниже (в своде пока «[правообладатель]»)."""
import json, os, re, subprocess, sys, tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
GAME = os.path.normpath(os.path.join(HERE, '..'))
GD = os.path.join(GAME, 'godot')
HTS = os.path.normpath(os.path.join(GAME, '../hush-till-six/web/licenses'))
GODOT = os.environ.get('GODOT', '/home/user/tools/godot/godot')
# как игра названа в проекте: пакет com.yompix.whatthelakekeeps (export_presets.cfg), репозиторий и сайт Yompix.
# Юридическое имя правообладателя в своде не записано — подтвердить у владельца (docs/LICENSES.md §4)
OWNER = 'Yompix'


def blocks(md):
    """код-блоки ``` под заголовками «### 4.1» и «### 4.2»"""
    out = {}
    for key in ('4.1', '4.2'):
        m = re.search(r'^### ' + re.escape(key) + r'.*?\n```\n(.*?)\n```', md, re.S | re.M)
        if not m:
            sys.exit('нет блока §%s в LICENSES.md' % key)
        out[key] = m.group(1)
    return out


def screen_lines(t):
    """строки для экрана: перенос строки внутри фразы в своде (строка кончается запятой или тире) — склеить"""
    out = []
    for l in t.split('\n'):
        if out and out[-1].rstrip().endswith((',', '—')):
            out[-1] = out[-1].rstrip() + ' ' + l.strip()
        else:
            out.append(l)
    return out


def main():
    md = open(os.path.join(GD, 'docs/LICENSES.md'), encoding='utf-8').read()
    b = blocks(md)
    en = b['4.1'].replace('[правообладатель]', OWNER)
    ru = b['4.2'].replace('[правообладатель]', OWNER)
    # экран «Авторы»: без строки-заглушки «полный текст MIT» — сам текст в LICENSES.txt (кнопка «Лицензии»)
    scr_en = '\n'.join(l for l in en.split('\n') if not l.startswith('[full MIT'))
    scr_en = scr_en.replace('This game uses Godot Engine, available under the following license:\n',
                            'Godot Engine is available under the MIT license (full text in LICENSES.txt):\n')
    os.makedirs(os.path.join(GD, 'credits'), exist_ok=True)
    json.dump({'_': 'генерирует tools/licenses_txt.py из docs/LICENSES.md §4 — руками не править',
               'ru': screen_lines(ru), 'en': screen_lines(scr_en)},
              open(os.path.join(GD, 'credits/credits.json'), 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
    # текст лицензий движка — из самого движка
    tmp = os.path.join(tempfile.mkdtemp(), 'engine.txt')
    subprocess.run([GODOT, '--headless', '--path', GD, '--script', 'res://scripts/dev/dump_licenses.gd', '--', tmp],
                   check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    engine = open(tmp, encoding='utf-8').read()
    os.remove(tmp)
    ofl = open(os.path.join(HTS, 'OFL-Oswald.txt'), encoding='utf-8').read()
    ofl = ofl[ofl.index('This Font Software is licensed'):]
    cc0 = open(os.path.join(HTS, 'CC0-1.0.txt'), encoding='utf-8').read()
    roko = open(os.path.join(HTS, 'Rokoko-free-mocap.txt'), encoding='utf-8').read()
    cmu = open(os.path.join(HTS, 'CMU-mocap.txt'), encoding='utf-8').read()
    bar = '=' * 100
    full = en.replace('[full MIT license text from Engine.get_license_text()]', '(full text: section 1 below)')
    parts = [
        'WHAT THE LAKE KEEPS — credits and third-party licenses',
        bar, full, '',
        bar, '1. Godot Engine and its third-party components', bar, engine.strip(), '',
        bar, '2. SIL Open Font License 1.1 (fonts: Inter, Pangolin, IBM Plex Mono, Lobster, Oswald, PT Sans, PT Sans Narrow, Caveat)', bar,
        ofl.strip(), '',
        bar, '3. Creative Commons CC0 1.0 Universal (MakeHuman / MPFB2 and MakeHuman community assets, Poly Haven, Quaternius)', bar,
        cc0.strip(), '',
        bar, '4. Motion capture: terms of use', bar, roko.strip(), '', cmu.strip(), '',
    ]
    txt = '\n'.join(parts) + '\n'
    open(os.path.join(GD, 'credits/LICENSES.txt'), 'w', encoding='utf-8', newline='\r\n').write(txt)   # CRLF — читается и в Блокноте
    print('credits.json: %d + %d строк; LICENSES.txt: %d КБ' % (len(ru.split('\n')), len(scr_en.split('\n')), len(txt.encode()) // 1024))


main()
