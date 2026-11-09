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
var _backoff := 1000
var _last_ping := 0
var _presence := {"activity": "idle"}


func _ready() -> void:
	var c := ConfigFile.new()
	c.load(TOKEN_FILE)
	token = c.get_value("device", "token", "")
	if token == "":
		token = Crypto.new().generate_random_bytes(32).hex_encode()
		c.set_value("device", "token", token)
		c.save(TOKEN_FILE)
	demo = Cfg.get_value("app", "demo", true)
	if Cfg.autotest in ["core", "java", "bedrock", "proc"]:
		return   # e2e сервера не трогает социальную часть
	_next_try = 0


func _process(_d: float) -> void:
	var now := Time.get_ticks_msec()
	if Cfg.autotest in ["core", "java", "bedrock", "proc"]:
		return
	if not _active:
		if now >= _next_try:
			_connect()
		return
	_ws.poll()
	var st := _ws.get_ready_state()
	if st == WebSocketPeer.STATE_OPEN:
		while _ws.get_available_packet_count() > 0:
			var pk := _ws.get_packet()
			if _ws.was_string_packet():
				var m: Variant = JSON.parse_string(pk.get_string_from_utf8())
				if m is Dictionary:
					_on_msg(m)
		if now - _last_ping > 25000:
			_last_ping = now
			_send({"t": "ping", "ts": now})
	elif st == WebSocketPeer.STATE_CLOSED:
		_active = false
		_set_conn("off")
		_next_try = now + _backoff
		_backoff = mini(_backoff * 2, 30000)


func _connect() -> void:
	_ws = WebSocketPeer.new()
	_ws.inbound_buffer_size = 1 << 20
	var tls: TLSOptions = null
	var ca := OS.get_environment("SSL_CERT_FILE")
	if Cfg.relay_url().begins_with("wss://") and ca != "" and FileAccess.file_exists(ca):
		var cert := X509Certificate.new()
		if cert.load(ca) == OK:
			tls = TLSOptions.client(cert)
	if _ws.connect_to_url(Cfg.relay_url(), tls) != OK:
		_next_try = Time.get_ticks_msec() + 10000
		return
	_active = true
	_set_conn("connecting")
	await get_tree().create_timer(0.1).timeout
	_wait_open()


func _wait_open() -> void:
	for i in 100:
		if not _active:
			return
		if _ws.get_ready_state() == WebSocketPeer.STATE_OPEN:
			_backoff = 1000
			_send({"t": "hello", "v": 1, "token": token})
			return
		await get_tree().create_timer(0.1).timeout


func reconnect() -> void:
	if _active:
		_ws.close()
	_active = false
	_next_try = 0


func _set_conn(s: String) -> void:
	conn = s
	changed.emit("conn")


func _send(d: Dictionary) -> void:
	if _active and _ws.get_ready_state() == WebSocketPeer.STATE_OPEN:
		_ws.send_text(JSON.stringify(d))


func online() -> bool:
	return conn == "online"


func _on_msg(m: Dictionary) -> void:
	var t: String = m.get("t", "")
	if t.ends_with("_err"):
		failed.emit(t.trim_suffix("_err"), str(m.get("error", "")))
		return
	match t:
		"need_register":
			_set_conn("need_register")
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
			notice.emit({"kind": "info", "text": "Заявка отправлена: " + m.user.tag})
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
				notice.emit({"kind": "info", "text": ("Ваш код комнаты: " + m.code) if m.get("code", "") != "" else "Свой код отключён"})
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
				notice.emit({"kind": "info", "text": {"set_title": "Титул обновлён", "referral": "Код друга принят!", "like": "Спасибо за лайк!", "report_ok": "Жалоба отправлена. Спасибо!", "invite_ok": "Приглашение отправлено"}.get(t, "Готово")})
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


func set_demo(on: bool) -> void:
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
