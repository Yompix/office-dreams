#!/usr/bin/env python3
"""0.9.6: архив выделенного сервера «Новоград» для игры по сети (много игроков).

  NOVOGRAD_OUT=папка python3 tools/make_server.py 0.9.6

Внутри: fabric-server-launch.jar (Fabric Loader для сервера; при первом запуске сам скачает сервер Minecraft 1.21.1),
mods/ (Новоград и Fabric API), server.properties (тип мира «Новоград: Город»), config/novograd-server.json,
START.bat / start.sh, ПРОЧИТАЙ.txt. eula.txt не кладём — соглашение Minecraft игрок принимает сам.
В конце — tools/check_deps.py.
"""
import json, os, sys, urllib.parse, urllib.request, zipfile, subprocess

VER = sys.argv[1]
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JAR = os.path.join(ROOT, 'build', 'libs', f'novograd-{VER}.jar')
OUTDIR = os.environ.get('NOVOGRAD_OUT') or os.path.join(ROOT, 'download')
OUT = os.path.join(OUTDIR, f'Novograd-Server-{VER}.zip')
LOADER, INSTALLER = '0.17.2', '1.0.1'
H = {'User-Agent': 'novograd-release'}
FABRIC_API = 'Mys3P7lK'
VOICE = 'dyhq1XyO'  # Simple Voice Chat (все права защищены — в архив не кладём, START качает с modrinth.com по sha512)


def get(url):
    return urllib.request.urlopen(urllib.request.Request(url, headers=H), timeout=120).read()


PROPS = """# Новоград — выделенный сервер
level-type=novograd\\:city
level-name=Novograd
motd=Новоград — Тёмные улицы
max-players=50
view-distance=8
simulation-distance=6
spawn-protection=0
online-mode=true
pvp=true
"""

CFG = {"earnMul": 1.0, "priceMul": 1.0, "pvp": True, "policeDifficulty": 1.0, "maxHeistPlayers": 4, "muggers": True, "packetsPerSecond": 60, "requireAdult": False, "fuelMode": "normal", "fuelRate": 1.0, "loanPeriodDays": 3, "voiceJailMute": True}

# Simple Voice Chat: порт UDP 24454, голос рядом — 48 блоков, группы (звонки и рации Новограда) включены
VOICE_CFG = """# Simple Voice Chat — настройки для Новограда
port=24454
bind_address=
max_voice_distance=48.0
crouch_distance_multiplier=0.5
whisper_distance_multiplier=0.5
codec=VOIP
mtu_size=1024
keep_alive=1000
enable_groups=true
voice_host=
allow_recording=true
spectator_interaction=false
spectator_player_possession=false
force_voice_chat=false
login_timeout=10000
broadcast_range=-1.0
allow_pings=true
"""

START_BAT = ("@echo off\r\nchcp 65001 >nul\r\ntitle Novograd server\r\n"
             "if not exist \"mods\\@@VFILE@@\" (\r\n"
             "  echo Скачиваю голос: Simple Voice Chat с modrinth.com...\r\n"
             "  powershell -NoProfile -Command \"$ProgressPreference='SilentlyContinue'; try { Invoke-WebRequest -UseBasicParsing -Uri '@@VURL@@' -OutFile 'mods\\@@VFILE@@.part'; "
             "if ((Get-FileHash -Algorithm SHA512 'mods\\@@VFILE@@.part').Hash.ToLower() -eq '@@VSHA@@') { Remove-Item 'mods\\voicechat-*.jar' -ErrorAction SilentlyContinue; Move-Item -Force 'mods\\@@VFILE@@.part' 'mods\\@@VFILE@@' } "
             "else { Remove-Item 'mods\\@@VFILE@@.part'; Write-Host 'хэш не совпал — сервер запустится без голоса' } } catch { Write-Host 'нет интернета — сервер запустится без голоса' }\"\r\n"
             ")\r\n"
             "java -Xms2G -Xmx6G -jar fabric-server-launch.jar nogui\r\npause\r\n")
START_SH = ("#!/bin/sh\ncd \"$(dirname \"$0\")\"\n"
            "if [ ! -f mods/@@VFILE@@ ]; then\n"
            "  echo 'Скачиваю голос: Simple Voice Chat с modrinth.com...'\n"
            "  if curl -fsSL -o mods/@@VFILE@@.part '@@VURL@@' && [ \"$(sha512sum mods/@@VFILE@@.part | cut -d' ' -f1)\" = '@@VSHA@@' ]; then\n"
            "    rm -f mods/voicechat-*.jar; mv mods/@@VFILE@@.part mods/@@VFILE@@\n"
            "  else rm -f mods/@@VFILE@@.part; echo 'голос не скачался — сервер запустится без него'; fi\n"
            "fi\n"
            "exec java -Xms2G -Xmx6G -jar fabric-server-launch.jar nogui\n")

README = """Новоград @@VER@@ — сервер для игры по сети (18+)

Нужно: Java 21, 6–8 ГБ памяти для 20–50 игроков, открытые порты 25565 (TCP, игра) и 24454 (UDP, голос).

Голос (Simple Voice Chat): START.bat / start.sh при первом запуске сами скачают мод с modrinth.com и проверят хэш
(его лицензия не разрешает класть мод в архив). Без него сервер работает, только без голоса. Настройки голоса —
config/voicechat/voicechat-server.properties (port=24454 — этот порт UDP открой в роутере/фаерволе; на хостинге
укажи voice_host, если порт снаружи другой). Игроки говорят клавишей V; звонки и рации — телефон → «Рация».
В тюрьме микрофон выключен (voiceJailMute в config/novograd-server.json).

1. Распакуй архив в отдельную папку.
2. Запусти START.bat (Windows) или start.sh (Linux). Первый запуск скачает сервер Minecraft 1.21.1 и остановится:
   открой eula.txt, поставь eula=true (это соглашение Minecraft — https://aka.ms/MinecraftEULA) и запусти снова.
3. Остров строится сам: тип мира уже задан в server.properties (level-type=novograd\\:city).
4. Игрокам нужен клиент Новограда той же версии (Novograd-Full / Novograd-Mods / .mrpack).

Настройки Новограда — config/novograd-server.json (после правки: /novograd reload):
  earnMul — множитель заработка, priceMul — множитель цен, pvp — игроки ранят друг друга,
  policeDifficulty — строгость полиции, maxHeistPlayers — игроков в одном ограблении,
  muggers — грабители ночью, packetsPerSecond — лимит пакетов мода от одного игрока,
  requireAdult — только 18+: игрок, чей клиент сказал «не 18+» (проверка возраста при первом запуске мода),
    или не ответил за 15 с, вежливо отключается. Это честное слово клиента (подделать можно).
  fuelMode — бензин: "normal", "slow" (тратится вдвое медленнее) или "off" (не тратится); fuelRate — множитель расхода.
  loanPeriodDays — платёж по кредиту раз в столько игровых дней.
  voiceJailMute — в тюрьме микрофон выключен (true/false).

Команды оператора (op <ник>):
  /novograd money <игрок> give|take|set <сумма> [card|cash|dirty]
  /novograd reset <игрок>      — начать игроку заново
  /novograd tp <игрок> <x> <z>
  /novograd kick <игрок>  ·  /novograd ban <игрок>
  /novograd stats              — игроки, MSPT, жители, машины, индекс цен
  /novograd reload             — перечитать настройки

Всё о каждом игроке (деньги, машины, дома, тайник, внешность, розыск, тюрьма, сюжет) хранится отдельно, по его UUID,
и считается на сервере. Создатель — Yompix.
"""


def main():
    os.makedirs(OUTDIR, exist_ok=True)
    cache = '/tmp/claude-0/full_cache'
    os.makedirs(cache, exist_ok=True)
    launch = os.path.join(cache, f'fabric-server-launch-{LOADER}.jar')
    if not os.path.exists(launch):
        open(launch, 'wb').write(get(f'https://meta.fabricmc.net/v2/versions/loader/1.21.1/{LOADER}/{INSTALLER}/server/jar'))
    v = json.loads(get('https://api.modrinth.com/v2/version/' + FABRIC_API))
    f = next((x for x in v['files'] if x.get('primary')), v['files'][0])
    api = os.path.join(cache, f['filename'])
    if not os.path.exists(api) or os.path.getsize(api) != f['size']:
        open(api, 'wb').write(get(f['url']))
    vv = json.loads(get('https://api.modrinth.com/v2/version/' + VOICE))
    vf = next((x for x in vv['files'] if x.get('primary')), vv['files'][0])
    sub = lambda t: t.replace('@@VFILE@@', vf['filename']).replace('@@VURL@@', vf['url']).replace('@@VSHA@@', vf['hashes']['sha512'])
    with zipfile.ZipFile(OUT, 'w', zipfile.ZIP_DEFLATED) as z:
        z.write(launch, 'fabric-server-launch.jar')
        z.write(JAR, f'mods/novograd-{VER}.jar')
        z.write(api, 'mods/' + f['filename'])
        z.writestr('server.properties', PROPS)
        z.writestr('config/novograd-server.json', json.dumps(CFG, indent=2))
        z.writestr('config/voicechat/voicechat-server.properties', VOICE_CFG)
        z.writestr('START.bat', sub(START_BAT).encode('utf-8'))
        zi = zipfile.ZipInfo('start.sh')
        zi.external_attr = 0o755 << 16
        z.writestr(zi, sub(START_SH))
        z.writestr('ПРОЧИТАЙ.txt', README.replace('@@VER@@', VER).encode('utf-8'))
    print('ok', OUT, round(os.path.getsize(OUT) / 1e6, 1), 'MB')
    if subprocess.run([sys.executable, os.path.join(ROOT, 'tools', 'check_deps.py'), OUT, '--loader', LOADER]).returncode:
        raise SystemExit('check_deps: не хватает зависимостей')


if __name__ == '__main__':
    main()
