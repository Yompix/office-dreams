extends Node
## Социальная часть LANTERN: профиль без пароля, друзья, чаты, открытые комнаты, задания, магазин, сезон.
## Одно WebSocket-соединение с relay (протокол — docs/PROTOCOL.md, раздел 5). Общий код ПК и Android.

signal changed(what: String)        # "me", "friends", "eco", "shop", "season", "achievements", "rooms", "top", "dm", "room_chat", "conn"
signal notice(d: Dictionary)        # заявки, приглашения, достижения
signal failed(what: String, text: String)

const TOKEN_FILE := "user://device.cfg"

var token := ""
var conn := "off"            # off, connecting, need_register, online
var me := {}
var friends: Array = []
var incoming: Array = []
var outgoing: Array = []
var blocked: Array = []
var eco := {}                # profile
var quests := {}
var shop := {}
var season := {}
var achievements := {}
var rooms: Array = []
var top_hosts: Array = []
var tags: Array = []
var dms := {}                # friend id -> Array сообщений
var unread := {}             # friend id -> int
var room_msgs: Array = []
var room_code := ""
var demo := true             # ДЕМО-режим покупок и рекламы (настройка)
var cloud := {}              # LANTERN Pro: {enabled, pro, list:[{id,name,size,ts,share,share_until}], max_mb, slots}

var _ws := WebSocketPeer.new()
var _active := false
var _next_try := 0
var _last_ping := 0
var _presence := {"activity": "idle"}

# ---- связь (1.8.2): почему раньше было «Подключитесь к интернету», хотя интернет есть —
# 1) пауза между попытками росла до 30 с (выкладка relay на Railway ≈1–2 мин → ещё до 30 с «офлайн» после неё);
# 2) «зависший» сокет (телефон сменил сеть, Android усыпил приложение, NAT забыл соединение) не замечался ~3 мин:
#    ping уходил, а ответ никто не ждал; 3) подключение без ответа висело без ограничения времени;
# 4) экраны показывали пустую страницу «нет интернета» на каждом коротком обрыве.
const CONNECT_MS := 8000     # не открылся за 8 с — новая попытка
const HELLO_MS := 8000       # открылся, но relay не ответил на hello
const PING_MS := 5000        # ping раз в 5 с (relay отвечает pong); ~60 байт
const DEAD_MS := 12000       # 12 с ни одного сообщения от relay — сокет мёртв, переподключаемся
const RESUME_CHECK_MS := 3500  # после возврата в приложение / смены сети: ответ на ping за 3.5 с, иначе переподключаемся
const BACKOFF := [500, 1000, 2000, 4000, 5000]   # в фоне (окно свёрнуто) — до 30 с
const PROBE_EVERY_MS := 15000
const OUTBOX_MS := 30000     # команды, нажатые во время переподключения, уходят после него (если не старше 30 с)
const CACHE_FILE := "user://social_cache.json"
const PROBE_URLS := ["https://connectivitycheck.gstatic.com/generate_204", "https://www.google.com/generate_204"]

var _fails := 0              # неудачных попыток подряд
var _t_open := 0             # когда начали подключаться
var _t_opened := 0           # когда сокет открылся (ждём welcome/need_register)
var _dead_at := 0            # к этому времени должно прийти хоть что-то от relay
var _bg := false             # приложение свёрнуто (Android) / окно без фокуса
var _net := "connecting"     # online, connecting, server (интернет есть, LANTERN недоступен — обновляется), offline
var _probing := false
var _probe_at := 0
var _down_since := 0         # с какого момента нет связи (0 — есть)
var _was_online := false
var _outbox: Array = []      # [ms, Dictionary]
var _ifaces := ""
var _ifaces_t := 0
var _vis_key := ""
var _cache_dirty := false
var _cache_t := 0
var _t_live := 0             # когда связь последний раз заработала
var _restart_hint := false   # relay прислал {t:"restart"} перед закрытием (выкладка)
var reconnects := 0          # сколько раз переподключались (для журнала и автотестов)
var _pill: Button
var _pill_layer: CanvasLayer


func _ready() -> void:
	var c := ConfigFile.new()
	c.load(TOKEN_FILE)
	token = c.get_value("device", "token", "")
	if token == "":
		token = Crypto.new().generate_random_bytes(32).hex_encode()
		c.set_value("device", "token", token)
		c.save(TOKEN_FILE)
	demo = Cfg.paid() and Cfg.get_value("app", "demo", true)
	if Cfg.autotest in ["core", "java", "bedrock", "proc"]:
		return   # e2e сервера не трогает социальную часть
	_load_cache()
	_next_try = 0


func _process(_d: float) -> void:
	var now := Time.get_ticks_msec()
	if Cfg.autotest in ["core", "java", "bedrock", "proc"]:
		return
	_watch_network(now)
	_flush_cache(now)
	if not _active:
		if now >= _next_try and not (_bg and OS.has_feature("mobile") and _fails > 3):
			_connect()
		_maybe_probe(now)
		_update_pill()
		return
	_ws.poll()
	var st := _ws.get_ready_state()
	if st == WebSocketPeer.STATE_CONNECTING:
		if now - _t_open > CONNECT_MS:
			_drop("connect timeout %d ms" % (now - _t_open))
	elif st == WebSocketPeer.STATE_OPEN:
		if _t_opened == 0:
			_t_opened = now
			_dead_at = now + HELLO_MS
			_send_now({"t": "hello", "v": 1, "token": token})
		while _ws.get_available_packet_count() > 0:
			var pk := _ws.get_packet()
			_dead_at = now + DEAD_MS
			if _ws.was_string_packet():
				var m: Variant = JSON.parse_string(pk.get_string_from_utf8())
				if m is Dictionary:
					_on_msg(m)
			if not _active:
				return
		if conn in ["online", "need_register"] and now - _last_ping > PING_MS:
			_last_ping = now
			_send_now({"t": "ping", "ts": now})
		if now > _dead_at:
			_drop("relay silent %d ms" % (now - _dead_at + (DEAD_MS if conn in ["online", "need_register"] else HELLO_MS)))
	elif st == WebSocketPeer.STATE_CLOSED:
		var code := _ws.get_close_code()
		_active = false
		_after_close(code, "closed, code %d %s" % [code, _ws.get_close_reason()])
	_maybe_probe(now)
	_update_pill()


func _connect() -> void:
	_ws = WebSocketPeer.new()
	_ws.inbound_buffer_size = 1 << 20
	var tls: TLSOptions = null
	var ca := OS.get_environment("SSL_CERT_FILE")
	if Cfg.relay_url().begins_with("wss://") and ca != "" and FileAccess.file_exists(ca):
		var cert := X509Certificate.new()
		if cert.load(ca) == OK:
			tls = TLSOptions.client(cert)
	_t_open = Time.get_ticks_msec()
	_t_opened = 0
	if _ws.connect_to_url(Cfg.relay_url(), tls) != OK:
		_after_close(-1, "connect_to_url failed")
		return
	_active = true
	if conn != "connecting":
		_set_conn("connecting")


## Оборвать текущий сокет (завис, не открылся, не ответил) и переподключиться по расписанию
func _drop(why: String) -> void:
	_ws.close()
	_active = false
	_after_close(-1, why)


func _after_close(code: int, why: String) -> void:
	var now := Time.get_ticks_msec()
	var restart := code == 4012 or code == 1012 or _restart_hint
	_restart_hint = false
	var was := conn in ["online", "need_register"]
	if _down_since == 0:
		_down_since = now
	var delay := 0
	if restart:
		delay = 300 + randi() % 700      # relay перезапускается (выкладка): пробуем почти сразу, дальше — как обычно
		_fails = 0
	elif was and now - _t_live > 5000:
		delay = 150 + randi() % 350      # только что работало — сразу пробуем ещё раз (но не по кругу, если рвётся сразу)
		_fails = 0
	else:
		var cap: int = 30000 if _bg else int(BACKOFF[BACKOFF.size() - 1])
		delay = mini(int(BACKOFF[mini(_fails, BACKOFF.size() - 1)]), cap)
		if _bg and _fails >= BACKOFF.size():
			delay = mini(delay * (1 << mini(_fails - BACKOFF.size() + 1, 3)), cap)
		delay = int(delay * randf_range(0.75, 1.25))
		_fails += 1
	_next_try = now + delay
	if was or _fails == 1:
		AppLog.add("social", "связь с relay: %s; новая попытка через %d мс" % [why, delay])
	if _fails >= 2 and _net in ["online", "connecting"] and not _probing:
		_probe_at = 0                     # вторая неудача подряд — выясняем: нет интернета или LANTERN обновляется
	if conn != "off":
		_set_conn("off")
	else:
		_emit_vis()


## Нет связи с relay: простой HTTPS-запрос к /healthz, потом к проверке интернета Google
func _maybe_probe(now: int) -> void:
	if _probing or conn in ["online", "need_register"] or _fails < 2 or now < _probe_at:
		return
	_probing = true
	_probe_at = now + PROBE_EVERY_MS
	var hz := await Net.probe(http_base() + "/healthz", 5.0)
	var st := "offline"
	if hz:
		st = "server"      # сайт отвечает, а WebSocket нет — тоже «LANTERN обновляется», но не «нет интернета»
	else:
		var urls: Array = [String(Cfg.autotest_args["probe"])] if Cfg.autotest_args.has("probe") else PROBE_URLS
		for u in urls:
			if await Net.probe(u, 5.0):
				st = "server"
				break
	_probing = false
	if conn in ["online", "need_register"]:
		return
	if st != _net:
		AppLog.add("social", "проверка сети: /healthz %s → %s" % ["ок" if hz else "нет", st])
		_net = st
		_emit_vis()


func reconnect() -> void:
	if _active:
		_ws.close()
	_active = false
	_fails = 0
	_next_try = 0


## Проверить связь прямо сейчас (вернулись в приложение, сменилась сеть): живой сокет ответит на ping за 3.5 с
func check_now(why := "") -> void:
	var now := Time.get_ticks_msec()
	if _active and conn in ["online", "need_register"]:
		_last_ping = now
		_send_now({"t": "ping", "ts": now})
		_dead_at = mini(_dead_at, now + RESUME_CHECK_MS) if now < _dead_at else now + RESUME_CHECK_MS
	elif not _active:
		_fails = mini(_fails, 1)
		_next_try = now
		_probe_at = 0
	if why != "":
		AppLog.add("social", "проверка связи: " + why)


func _notification(what: int) -> void:
	match what:
		NOTIFICATION_APPLICATION_PAUSED, NOTIFICATION_APPLICATION_FOCUS_OUT:
			if what == NOTIFICATION_APPLICATION_PAUSED or OS.has_feature("mobile"):
				_bg = true
		NOTIFICATION_APPLICATION_RESUMED, NOTIFICATION_APPLICATION_FOCUS_IN:
			var was_bg := _bg
			_bg = false
			if was_bg or what == NOTIFICATION_APPLICATION_RESUMED:
				check_now("app resumed")


## Смена сети (Wi-Fi ↔ мобильные данные, другой роутер): у устройства поменялись адреса — проверяем связь сразу
func _watch_network(now: int) -> void:
	if now - _ifaces_t < 3000:
		return
	_ifaces_t = now
	var a := Array(IP.get_local_addresses()).filter(func(x): return not (x.begins_with("127.") or x == "::1" or x.begins_with("fe80")))
	a.sort()
	var k := ",".join(a)
	if _ifaces != "" and k != _ifaces:
		check_now("network changed")
	_ifaces = k


func _set_conn(s: String) -> void:
	var now := Time.get_ticks_msec()
	if s == "online" or s == "need_register":
		if conn != "online" and conn != "need_register":
			_t_live = now
		if _down_since != 0 and _was_online:
			reconnects += 1
			AppLog.add("social", "связь с relay вернулась через %.1f с" % ((now - _down_since) / 1000.0))
		_down_since = 0
		_fails = 0
		_net = "online"
		_probe_at = 0
	conn = s
	if s == "online":
		_was_online = true
		_flush_outbox()
	_emit_vis()


## Экраны перерисовываются по changed("conn") — только когда меняется то, что они показывают
## (а не на каждую попытку переподключения: иначе список дёргается и сбивается прокрутка).
func _emit_vis() -> void:
	var k := "%s|%s|%s" % [online(), conn if conn in ["need_register", "online"] else "-", net_state()]
	if k != _vis_key:
		_vis_key = k
		changed.emit("conn")


## Состояние связи для текста: online, connecting (подключаемся / переподключаемся), server (интернет есть,
## LANTERN недоступен — обычно выкладка, вернёмся сами), offline (нет интернета)
func net_state() -> String:
	if conn in ["online", "need_register"]:
		return "online"
	return _net if _net != "online" else "connecting"


## Короткий текст о связи (пустой — всё хорошо)
func net_text() -> String:
	match net_state():
		"server":
			return L.t("Сервер LANTERN обновляется — подключимся сами через несколько секунд")
		"offline":
			return L.t("Нет интернета — проверьте Wi-Fi или мобильные данные")
		"connecting":
			return L.t("Переподключаемся к LANTERN…") if _was_online or not me.is_empty() else L.t("Подключаемся…")
	return ""


## Действительно подключены прямо сейчас (а не показываем сохранённое)
func live() -> bool:
	return conn == "online"


var sent_count := 0      # сколько команд ушло на сервер (автотест «нерабочих кнопок» смотрит, был ли эффект)


const _NO_QUEUE := ["ping", "hello", "register", "delete_account", "demo_wipe"]


func _send(d: Dictionary) -> void:
	sent_count += 1
	var t := String(d.get("t", ""))
	if _active and _ws.get_ready_state() == WebSocketPeer.STATE_OPEN and (conn == "online" or t in ["register", "ping"]):
		_ws.send_text(JSON.stringify(d))
	elif not t in _NO_QUEUE:
		# переподключаемся: команда уйдёт сразу после возврата связи (обновления списков — по одной)
		if d.size() == 1:
			_outbox = _outbox.filter(func(e): return not (e[1].size() == 1 and e[1].t == t))
		_outbox.append([Time.get_ticks_msec(), d])
		if _outbox.size() > 50:
			_outbox.pop_front()
	elif t == "delete_account":
		failed.emit("delete_account", "offline")


func _send_now(d: Dictionary) -> void:
	if _ws.get_ready_state() == WebSocketPeer.STATE_OPEN:
		_ws.send_text(JSON.stringify(d))


func _flush_outbox() -> void:
	var now := Time.get_ticks_msec()
	var q := _outbox
	_outbox = []
	for e in q:
		if now - int(e[0]) <= OUTBOX_MS:
			_send_now(e[1])


## Можно показывать данные профиля: связь есть, или она на минуту пропала, а сохранённое уже есть
## (во время выкладки relay экраны не превращаются в пустое «Подключитесь к интернету» — сверху плашка
## «Переподключаемся…»). Нет интернета совсем — false, экраны показывают свою страницу «нет связи».
func online() -> bool:
	if conn == "online":
		return true
	return conn != "need_register" and me.has("id") and net_state() != "offline"


func _on_msg(m: Dictionary) -> void:
	var t: String = m.get("t", "")
	if t in ["welcome", "state", "friend", "eco", "demo_purchase", "ad_reward", "shop", "season", "achievements", "vanity", "cloud"]:
		_cache_dirty = true
	if t.ends_with("_err"):
		failed.emit(t.trim_suffix("_err"), str(m.get("error", "")))
		return
	match t:
		"restart":
			_restart_hint = true
		"need_register":
			_set_conn("need_register")
		"account_deleted":
			_forget_profile()
		"welcome":
			me = m.me
			tags = m.get("tags", [])
			_set_conn("online")
			changed.emit("me")
			_send(_presence.merged({"t": "presence"}))
			request("eco")
		"state":
			me = m.me
			friends = m.friends
			incoming = m.incoming
			outgoing = m.outgoing
			blocked = m.blocked
			changed.emit("friends")
			changed.emit("me")
		"friend":
			var f: Dictionary = m.friend
			var found := false
			for i in friends.size():
				if friends[i].id == f.id:
					friends[i] = f
					found = true
			if found:
				changed.emit("friends")
		"friend_add_ok":
			notice.emit({"kind": "info", "text": L.t("Заявка отправлена: ") + m.user.tag})
		"notice", "invite":
			var d := m.duplicate()
			if t == "invite":
				d["kind"] = "invite"
			notice.emit(d)
			if m.get("kind", "") == "achievement":
				request("eco")
		"dm":
			var other: int = m.to if int(m.from) == int(me.get("id", 0)) else m.from
			if not dms.has(other):
				dms[other] = []
			dms[other].append({"id": m.id, "from": m.from, "text": m.text, "ts": m.ts})
			if int(m.from) != int(me.get("id", 0)):
				unread[other] = int(unread.get(other, 0)) + 1
			changed.emit("dm")
		"history":
			dms[int(m.with)] = m.messages
			changed.emit("dm")
		"room_history":
			room_code = m.code
			room_msgs = m.messages
			changed.emit("room_chat")
		"room_chat":
			room_msgs.append(m.msg)
			if room_msgs.size() > 100:
				room_msgs.pop_front()
			changed.emit("room_chat")
		"rooms":
			rooms = m.rooms
			changed.emit("rooms")
		"top":
			top_hosts = m.hosts
			changed.emit("top")
		"eco", "demo_purchase", "ad_reward":
			if m.has("profile"):
				eco = m.profile
			if m.has("quests"):
				quests = m.quests
			changed.emit("eco")
			if str(m.get("error", "")) != "":
				failed.emit(t, m.error)
			elif t == "demo_purchase":
				notice.emit({"kind": "purchased", "product": m.product})
			elif t == "ad_reward":
				notice.emit({"kind": "ad_reward"})
		"shop":
			shop = m
			eco["sparks"] = m.get("sparks", eco.get("sparks", 0))
			if m.has("equip"):
				eco["equip"] = m.equip
			changed.emit("shop")
			changed.emit("eco")
			if str(m.get("error", "")) != "":
				failed.emit("shop", m.error)
		"season":
			season = m
			changed.emit("season")
			if str(m.get("error", "")) != "":
				failed.emit("season", m.error)
		"vanity":
			eco["vanity"] = m.get("code", "")
			changed.emit("eco")
			if str(m.get("error", "")) != "":
				failed.emit("vanity", m.error)
			else:
				notice.emit({"kind": "info", "text": (L.t("Ваш код комнаты: ") + m.code) if m.get("code", "") != "" else L.t("Свой код отключён")})
		"cloud":
			cloud = m
			changed.emit("cloud")
			if str(m.get("error", "")) != "":
				failed.emit("cloud", m.error)
		"achievements":
			achievements = m
			changed.emit("achievements")
		"set_title", "referral", "like", "report_ok", "invite_ok":
			if str(m.get("error", "")) != "":
				failed.emit(t, m.error)
			else:
				notice.emit({"kind": "info", "text": {"set_title": L.t("Титул обновлён"), "referral": L.t("Код друга принят!"), "like": L.t("Спасибо за лайк!"), "report_ok": L.t("Жалоба отправлена. Спасибо!"), "invite_ok": L.t("Приглашение отправлено")}.get(t, L.t("Готово"))})
				if t == "set_title":
					request("state")
		"error":
			failed.emit("error", str(m.get("error", "")))


# ------------------------------------------------------------------ команды
func request(t: String, extra := {}) -> void:
	var d := extra.duplicate()
	d["t"] = t
	_send(d)


func register(nick: String) -> void:
	_send({"t": "register", "token": token, "nick": nick.strip_edges()})


func set_presence(activity: String, world := "", edition := "", version := "", code := "") -> void:
	_presence = {"activity": activity, "world": world, "edition": edition, "version": version, "code": code}
	_send(_presence.merged({"t": "presence"}))


func send_dm(fid: int, text: String) -> void:
	_send({"t": "dm", "to": fid, "text": text})


func open_dm(fid: int) -> void:
	unread.erase(fid)
	_send({"t": "history", "with": fid})


func enter_room(code: String) -> void:
	room_msgs = []
	room_code = code
	_send({"t": "room_enter", "code": code})


func room_say(text: String) -> void:
	_send({"t": "room_chat", "text": text})


func friend_by_id(id: int) -> Dictionary:
	for f in friends:
		if int(f.id) == id:
			return f
	return {}


## Удалить профиль на сервере (друзья, сообщения, облако — всё). Ответ — account_deleted / delete_account_err.
func delete_account() -> void:
	_send({"t": "delete_account", "confirm": "DELETE"})


## После удаления: новый ключ устройства, локальные данные профиля стёрты, дальше — регистрация заново
func _forget_profile() -> void:
	token = Crypto.new().generate_random_bytes(32).hex_encode()
	var c := ConfigFile.new()
	c.set_value("device", "token", token)
	c.save(TOKEN_FILE)
	me = {}
	friends = []
	incoming = []
	outgoing = []
	blocked = []
	eco = {}
	dms = {}
	unread = {}
	room_msgs = []
	cloud = {}
	quests = {}
	DirAccess.remove_absolute(ProjectSettings.globalize_path(CACHE_FILE))
	notice.emit({"kind": "account_deleted"})
	changed.emit("me")
	reconnect()


func set_demo(on: bool) -> void:
	on = on and Cfg.paid()
	demo = on
	Cfg.set_value("app", "demo", on)
	if not on:
		_send({"t": "demo_wipe"})


## HTTP-адрес relay (для облачных копий): wss://host/ws → https://host
func http_base() -> String:
	var u := Cfg.relay_url()
	u = u.replace("wss://", "https://").replace("ws://", "http://")
	if u.ends_with("/ws"):
		u = u.substr(0, u.length() - 3)
	return u.trim_suffix("/")


# ------------------------------------------------------------------ сохранённое (показываем, пока переподключаемся)
## Профиль, друзья (все «не в сети»), задания, магазин, сезон — на диске, привязаны к ключу устройства.
func _load_cache() -> void:
	if not FileAccess.file_exists(CACHE_FILE):
		return
	var d: Variant = JSON.parse_string(FileAccess.get_file_as_string(CACHE_FILE))
	if not d is Dictionary or String(d.get("k", "")) != token.sha256_text().substr(0, 16) or not d.get("me") is Dictionary:
		return
	me = d.me
	tags = d.get("tags", [])
	eco = d.get("eco", {})
	quests = d.get("quests", {})
	shop = d.get("shop", {})
	season = d.get("season", {})
	achievements = d.get("achievements", {})
	friends = d.get("friends", [])
	for f in friends:
		if f is Dictionary:
			f["online"] = false
	_was_online = true


func _flush_cache(now: int) -> void:
	if not _cache_dirty or now - _cache_t < 5000 or not me.has("id"):
		return
	_cache_dirty = false
	_cache_t = now
	var d := {"k": token.sha256_text().substr(0, 16), "me": me, "tags": tags, "eco": eco, "quests": quests, "shop": shop,
		"season": season, "achievements": achievements, "friends": friends}
	var f := FileAccess.open(CACHE_FILE, FileAccess.WRITE)
	if f:
		f.store_string(JSON.stringify(d))


# ------------------------------------------------------------------ плашка «Переподключаемся…»
## Небольшая плашка сверху вместо пустой страницы: появляется, если связи нет дольше 1.5 с; нажать — попробовать сейчас.
func _update_pill() -> void:
	if Cfg.autotest != "" and not Cfg.autotest_args.has("pill"):
		return
	var show := conn != "online" and conn != "need_register" and _down_since != 0 \
		and Time.get_ticks_msec() - _down_since > 1500 and (_was_online or _fails >= 2)
	if not show:
		if _pill and _pill.visible:
			_pill.visible = false
		return
	if _pill == null:
		_pill_layer = CanvasLayer.new()
		_pill_layer.layer = 90
		add_child(_pill_layer)
		_pill = Button.new()
		_pill.focus_mode = Control.FOCUS_NONE
		_pill.add_theme_font_override("font", UI.font_bold)
		_pill.add_theme_font_size_override("font_size", 13)
		_pill.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
		for st in ["normal", "hover", "pressed", "focus"]:
			_pill.add_theme_stylebox_override(st, UI.box(Color(UI.PANEL, 0.94), 18, 9, Color(UI.YELLOW, 0.55), 1))
		_pill.pressed.connect(func(): reconnect())
		_pill_layer.add_child(_pill)
	var text := net_text()
	var col: Color = UI.RED if net_state() == "offline" else (UI.YELLOW if net_state() == "server" else UI.MUTED)
	if _pill.text != text or not _pill.visible:
		_pill.text = text
		_pill.add_theme_color_override("font_color", col)
		_pill.add_theme_color_override("font_hover_color", col)
		_pill.add_theme_color_override("font_pressed_color", col)
		_pill.visible = true
		var vs := get_viewport().get_visible_rect().size
		var w := minf(vs.x - 32, 460)
		_pill.custom_minimum_size = Vector2(0, 0)
		_pill.size = Vector2.ZERO
		_pill.custom_minimum_size.x = minf(w, UI.font_bold.get_string_size(text, HORIZONTAL_ALIGNMENT_LEFT, -1, 13).x + 36)
		_pill.custom_minimum_size.y = 44 if UI.compact() else 0     # 1.8.2: нажимается пальцем (44 dp)
		_pill.reset_size()
		var ph := maxf(_pill.size.y, _pill.get_combined_minimum_size().y)   # при первом показе размер ещё не посчитан
		var top := 10.0
		if OS.has_feature("mobile"):
			var sa := DisplayServer.get_display_safe_area()
			var k := float(get_window().size.y) / maxf(1.0, vs.y)
			top += maxf(0.0, sa.position.y) / maxf(k, 0.01)
		# 1.8.2: телефон — над нижним меню, а не поверх заголовка и «Назад» в верхней панели (там плашка закрывала их)
		var app := get_tree().current_scene
		if not UI.compact():
			top = vs.y - ph - 20      # ПК: внизу по центру — сверху заголовок экрана
		elif app and "_nav_bar" in app and is_instance_valid(app._nav_bar) and (app._nav_bar as Control).is_visible_in_tree():
			top = (app._nav_bar as Control).get_global_rect().position.y - ph - 8
		_pill.position = Vector2((vs.x - _pill.size.x) / 2, top)
