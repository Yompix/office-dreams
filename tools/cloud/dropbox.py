#!/usr/bin/env python3
"""Сборки в Dropbox владельца: загрузить файл любого размера и получить ссылку на скачивание.

Доступ — приложение Dropbox с доступом только к своей папке (Apps/<имя приложения>), права files.content.write и
sharing.write. Ключи владелец вписывает сам в настройки среды (не в чат, не в git):
    DROPBOX_APP_KEY, DROPBOX_APP_SECRET, DROPBOX_REFRESH_TOKEN
Короткий токен доступа получается из них при каждом запуске (живёт 4 часа), значения нигде не печатаются.

    python3 tools/cloud/dropbox.py upload out/WhatTheLakeKeeps_win.zip [имя_в_облаке] [--keep 1]
        загрузить (по частям по 64 МБ, каждая с повтором), старые файлы с тем же началом имени удалить,
        оставив --keep последних; напечатать ссылку на скачивание (dl=1)
    python3 tools/cloud/dropbox.py list      что лежит в папке приложения
    python3 tools/cloud/dropbox.py check     ключи на месте и работают
"""
import json, os, sys, time, urllib.error, urllib.parse, urllib.request

API = "https://api.dropboxapi.com/2/"
CONTENT = "https://content.dropboxapi.com/2/"
CHUNK = 64 * 1024 * 1024


def die(msg):
    print("ОШИБКА: " + msg, file=sys.stderr)
    sys.exit(1)


def token():
    need = ["DROPBOX_APP_KEY", "DROPBOX_APP_SECRET", "DROPBOX_REFRESH_TOKEN"]
    miss = [k for k in need if not os.environ.get(k)]
    if miss:
        die("нет переменных среды: " + ", ".join(miss) + " — владелец вписывает их в настройках среды, новая сессия их видит")
    body = urllib.parse.urlencode({"grant_type": "refresh_token", "refresh_token": os.environ["DROPBOX_REFRESH_TOKEN"],
                                   "client_id": os.environ["DROPBOX_APP_KEY"],
                                   "client_secret": os.environ["DROPBOX_APP_SECRET"]}).encode()
    try:
        with urllib.request.urlopen(urllib.request.Request("https://api.dropbox.com/oauth2/token", data=body), timeout=60) as r:
            return json.load(r)["access_token"]
    except urllib.error.HTTPError as e:
        die("Dropbox не выдал токен (%d): %s" % (e.code, e.read().decode(errors="replace")[:300]))


def call(tok, url, arg=None, data=None, tries=4):
    """RPC (arg — JSON в теле) или загрузка (arg — в заголовке Dropbox-API-Arg, data — байты)."""
    for i in range(tries):
        h = {"Authorization": "Bearer " + tok}
        if data is None:
            h["Content-Type"] = "application/json"
            body = json.dumps(arg).encode() if arg is not None else b"null"
        else:
            h["Content-Type"] = "application/octet-stream"
            h["Dropbox-API-Arg"] = json.dumps(arg, ensure_ascii=True)
            body = data
        try:
            with urllib.request.urlopen(urllib.request.Request(url, data=body, headers=h), timeout=600) as r:
                raw = r.read()
                return json.loads(raw) if raw else {}
        except urllib.error.HTTPError as e:
            txt = e.read().decode(errors="replace")
            if e.code == 409:   # ошибка по сути (нет файла, ссылка уже есть) — разбирает вызывающий
                return {"error": json.loads(txt).get("error", {}), "error_summary": json.loads(txt).get("error_summary", "")}
            if e.code in (429, 500, 502, 503, 504) and i < tries - 1:
                time.sleep(2 ** (i + 1))
                continue
            die("%s → %d: %s" % (url.rsplit("/", 2)[-2] + "/" + url.rsplit("/", 1)[-1], e.code, txt[:300]))
        except (urllib.error.URLError, TimeoutError, ConnectionError) as e:
            if i < tries - 1:
                time.sleep(2 ** (i + 1))
                continue
            die("сеть: %s" % e)


def listing(tok):
    r = call(tok, API + "files/list_folder", {"path": ""})
    items = list(r.get("entries", []))
    while r.get("has_more"):
        r = call(tok, API + "files/list_folder/continue", {"cursor": r["cursor"]})
        items += r.get("entries", [])
    return [e for e in items if e.get(".tag") == "file"]


def upload(tok, path, name):
    size = os.path.getsize(path)
    dest = "/" + name
    with open(path, "rb") as f:
        if size <= CHUNK:
            return call(tok, CONTENT + "files/upload", {"path": dest, "mode": "overwrite", "mute": True}, f.read())
        sid = call(tok, CONTENT + "files/upload_session/start", {"close": False}, f.read(CHUNK))["session_id"]
        off = CHUNK
        while size - off > CHUNK:
            call(tok, CONTENT + "files/upload_session/append_v2", {"cursor": {"session_id": sid, "offset": off}, "close": False}, f.read(CHUNK))
            off += CHUNK
            print("  загружено %d из %d МБ" % (off >> 20, size >> 20), flush=True)
        return call(tok, CONTENT + "files/upload_session/finish",
                    {"cursor": {"session_id": sid, "offset": off}, "commit": {"path": dest, "mode": "overwrite", "mute": True}}, f.read())


def link(tok, name):
    r = call(tok, API + "sharing/create_shared_link_with_settings", {"path": "/" + name})
    if "error" in r:
        r2 = call(tok, API + "sharing/list_shared_links", {"path": "/" + name, "direct_only": True})
        if not r2.get("links"):
            die("не вышло сделать ссылку: " + r.get("error_summary", ""))
        r = r2["links"][0]
    url = r["url"]
    return url.replace("dl=0", "dl=1") if "dl=0" in url else url + ("&" if "?" in url else "?") + "dl=1"


def main():
    if len(sys.argv) < 2 or sys.argv[1] not in ("upload", "list", "check"):
        print(__doc__)
        sys.exit(2)
    tok = token()
    if sys.argv[1] == "check":
        print("ключи работают; в папке файлов: %d" % len(listing(tok)))
        return
    if sys.argv[1] == "list":
        for e in sorted(listing(tok), key=lambda e: e["server_modified"]):
            print("%s  %6d МБ  %s" % (e["server_modified"], e["size"] >> 20, e["name"]))
        return
    rest = sys.argv[2:]
    keep = 1   # сколько сборок оставить вместе с новой
    if "--keep" in rest:
        i = rest.index("--keep")
        keep = int(rest[i + 1])
        del rest[i:i + 2]
    if not rest or not os.path.isfile(rest[0]):
        die("нет файла для загрузки")
    path = rest[0]
    name = rest[1] if len(rest) > 1 else os.path.basename(path)
    stem = os.path.splitext(name)[0].split("_")[0]   # «WhatTheLakeKeeps_win_1nov.zip» → старые «WhatTheLakeKeeps*»
    old = sorted([e for e in listing(tok) if e["name"].startswith(stem) and e["name"] != name], key=lambda e: e["server_modified"])
    for e in old[:max(0, len(old) - max(0, keep - 1))]:   # место под новую сборку (бесплатно у Dropbox — 2 ГБ)
        call(tok, API + "files/delete_v2", {"path": e["path_lower"]})
        print("  удалена старая: " + e["name"])
    print("загрузка %s (%d МБ) → Dropbox/Apps/…/%s" % (path, os.path.getsize(path) >> 20, name), flush=True)
    r = upload(tok, path, name)
    if "error" in r:
        die("загрузка не завершилась: " + r.get("error_summary", ""))
    print(link(tok, name))


if __name__ == "__main__":
    main()
