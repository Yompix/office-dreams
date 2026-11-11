class_name RoomControl
extends Node
## Хозяин мира, пока комната открыта (свой мир из Minecraft или свой сервер):
## «Друзья могут зайти прямо сейчас ✓» / «Мир не виден друзьям», кто в мире, выгнать/забанить,
## «Кто может зайти: Только друзья / Все в LANTERN», уведомления «Алекс зашёл в ваш мир».
## Android: окошко поверх Minecraft и уведомление (LanternRelayService) обновляет поток моста (RelayHost._sync_side) —
## здесь только подключаем его и забираем то, что нажали, пока LANTERN был в фоне.
## ПК: маленькое окошко поверх всех окон (MiniWindow) + звук/мигание в панели задач.

signal changed

var app
var _t := 0.0
var _was_ok := -1
var _bad_since := 0
var _alerted := false
var _overlay_asked := false
var mini: Window


## Комната открыта?
func hosting() -> bool:
	return app.own.active or (app.relay_host.stats().state != "off" and app.server.state in ["starting", "running"])


## Друзья могут зайти прямо сейчас: связь с relay есть и мир отвечает.
func ok() -> bool:
	if not hosting():
		return false
	if app.relay_host.stats().state != "online":
		return false
	return app.own.alive() if app.own.active else app.server.is_alive()


## Короткая строка статуса и подсказка, что делать.
func status() -> Dictionary:
	if not hosting():
		return {"ok": false, "text": "Мир закрыт", "why": ""}
	var st: Dictionary = app.relay_host.stats()
	if st.state == "connecting":
		return {"ok": false, "text": "Открываем мир для друзей…", "why": "", "wait": true}
	if st.state != "online":
		return {"ok": false, "text": "Мир не виден друзьям", "why": "Нет связи с LANTERN. Проверьте интернет — комната вернётся с тем же кодом."}
	if app.own.active and not app.own.alive():
		return {"ok": false, "text": "Мир не виден друзьям", "why": "Minecraft не отвечает. Вернитесь в мир" + (" и проверьте «Видимость для игроков LAN»." if app.own.world.get("edition", "") == "bedrock" else " («Открыть для сети»).")}
	if not app.own.active and not app.server.is_alive():
		return {"ok": false, "text": "Мир не виден друзьям", "why": "Сервер не запущен."}
	return {"ok": true, "text": "Друзья могут зайти прямо сейчас ✓", "why": ""}


func code() -> String:
	var c: String = app.relay_host.stats().get("code", "")
	return RoomCode.pretty(c) if c != "" else ""


func world_name() -> String:
	return String(app.own.world.get("name", "Мир")) if app.own.active else String(app.server.opts.get("world", "Мир"))


func edition() -> String:
	if app.own.active:
		return String(app.own.world.get("edition", "bedrock"))
	return "both" if app.server.edition == "java" and app.server.opts.get("crossplay", false) else app.server.edition


func invite_link() -> String:
	return Invite.link(app.relay_host.stats().get("code", ""), edition(), world_name(), Cfg.relay_url())


func share_text() -> String:
	if code() == "":
		return ""
	return "Заходи в мой мир «%s» в Minecraft через LANTERN!\nКод: %s\n%s" % [world_name(), code(), invite_link()]


# ------------------------------------------------------------------ игроки

## [{peer, user, name, mc}] — кто в комнате. mc — ник в Minecraft, если он известен (свой сервер, один новый игрок).
## Номер комнаты (8 цифр, как в списке «Игры») и связь хозяина с LANTERN: great / good / poor
func num() -> String:
	return String(app.relay_host.stats().get("num", ""))


func quality() -> String:
	return String(app.relay_host.stats().get("quality", ""))


const QUALITY_TEXT := {"great": "связь отличная", "good": "связь хорошая", "poor": "связь так себе"}


## Пригласить друга из LANTERN (придёт приглашение с кнопкой «Зайти»)
func invite(friend_id: int) -> void:
	if friend_id <= 0 or code() == "":
		return
	Social.request("invite", {"to": friend_id, "code": app.relay_host.stats().get("code", ""), "world": world_name(), "edition": app.relay_host.edition})
	app.toast("Приглашение отправлено", UI.JAVA)


func peers() -> Array:
	var out := []
	for p in app.relay_host.peers():
		var d: Dictionary = p.duplicate()
		d["name"] = RelayHost.peer_name(p.user)
		d["mc"] = String(_mc_names.get(p.peer, ""))
		out.append(d)
	return out


var _mc_names := {}   # peer -> ник в Minecraft (угадываем на своём сервере: кто зашёл в игру вслед за другом)
var _await_mc: Array = []
var _opped := false


func kick(peer: String) -> void:
	app.relay_host.kick(peer, false)
	_server_cmd("kick", peer)
	app.toast("Игрок выгнан из мира", UI.YELLOW)


func ban(peer: String) -> void:
	for p in app.relay_host.peers():
		if p.peer == peer:
			_remember_ban(p.user)
	app.relay_host.kick(peer, true)
	_server_cmd("ban", peer)
	app.toast("Игроку закрыт вход в ваш мир", UI.RED)


func _server_cmd(what: String, peer: String) -> void:
	var mc := String(_mc_names.get(peer, ""))
	if mc == "" or app.own.active or not app.server.is_alive():
		return
	if app.server.edition == "java":
		app.server.command("%s %s" % [what, mc])
	else:
		app.server.command("kick %s" % mc)   # у BDS нет ban — достаточно запрета на relay


## Список запрета хозяина: [{id, nick, tag}] (хранится на устройстве, отправляется relay при открытии комнаты).
static func bans() -> Array:
	var a: Variant = Cfg.get_value("host", "bans", [])
	return a if a is Array else []


func _remember_ban(u: Variant) -> void:
	if not u is Dictionary or int(u.get("id", 0)) <= 0:
		return
	var a := bans()
	for b in a:
		if int(b.id) == int(u.id):
			return
	a.append({"id": int(u.id), "nick": String(u.get("nick", "")), "tag": String(u.get("tag", ""))})
	Cfg.set_value("host", "bans", a)
	app.relay_host.bans = ban_ids()


func unban(id: int) -> void:
	var a := bans().filter(func(b): return int(b.id) != id)
	Cfg.set_value("host", "bans", a)
	app.relay_host.send_bans(ban_ids())
	changed.emit()


static func ban_ids() -> Array:
	return bans().map(func(b): return int(b.id))


# ------------------------------------------------------------------ «Кто может зайти»

## Свой мир из Minecraft: «Все в LANTERN» (в «Открытых мирах» на главной) — по умолчанию включено.
static func own_public_default() -> bool:
	return bool(Cfg.get_value("own", "public", true))


func is_public() -> bool:
	return app.relay_host.public_on


func set_public(on: bool) -> void:
	if app.own.active:
		Cfg.set_value("own", "public", on)
	app.relay_host.set_public(on, public_card())
	changed.emit()
	app.toast("Теперь зайти могут все в LANTERN" if on else "Теперь зайти можно только по коду", UI.JAVA)


func public_card() -> Dictionary:
	var g := Cfg.game()
	var c := _public_card()
	if g.title != "":
		c["title"] = g.title
	c["slots"] = g.cap
	return c


func _public_card() -> Dictionary:
	if app.own.active:
		var w: Dictionary = app.own.world
		var mx := int(w.get("max", 0))
		return {"title": String(w.get("name", "Мир")), "tags": [], "version": String(w.get("version", "")), "slots": mx if mx > 0 else 10}
	var o: Dictionary = app.server.opts
	return {"title": String(o.get("public_title", o.get("world", "Мир"))), "tags": o.get("tags", []),
		"version": String(o.get("mc_version", "")) if app.server.edition == "java" else "", "slots": int(o.get("max_players", 10))}


## «Закрыть комнату» из окошка/уведомления.
func close_room() -> void:
	if app.own.active:
		app.own.stop()
	elif app.server.is_alive():
		app.server.stop()
	app.relay_host.stop()
	room_closed()
	if app.current == "host":
		app.go("home")


# ------------------------------------------------------------------ цикл

func _ready() -> void:
	app.server.players_changed.connect(_on_mc_players)


## Комнату только что открыли (OwnWorld.start / сервер).
func room_opened() -> void:
	_was_ok = -1
	_alerted = false
	_bad_since = 0
	_mc_names.clear()
	_opped = false
	app.relay_host.bans = ban_ids()
	app.relay_host.share_text = ""
	if OS.get_name() == "Android":
		app.relay_host.android = JavaClassWrapper.wrap(OwnWorld.ANDROID_SERVICE)
		_ask_overlay()
	elif Cfg.autotest == "" and bool(Cfg.get_value("host", "mini", true)):
		_open_mini()


func room_closed() -> void:
	app.relay_host.android = null
	if mini:
		mini.queue_free()
		mini = null
	changed.emit()


func _process(d: float) -> void:
	_t += d
	if _t < 0.5:
		return
	_t = 0.0
	for a in app.relay_host.take_ui_actions():
		match String(a.get("a", "")):
			"ban":
				_remember_ban(a.get("user"))
			"public":
				if app.own.active:
					Cfg.set_value("own", "public", bool(a.on))
			"close":
				close_room()
			"invite":
				invite(int(a.get("id", 0)))
	if not hosting():
		if mini:
			room_closed()
		return
	app.relay_host.share_text = share_text()
	var fl := []
	for f in Social.friends:
		if f.get("online", false):
			fl.append({"id": int(f.id), "nick": String(f.nick)})
	app.relay_host.invite_friends = fl.slice(0, 8)
	for e in app.relay_host.take_peer_events():
		var nm := RelayHost.peer_name(e.user)
		if e.on:
			_await_mc.append([e.peer, Time.get_ticks_msec()])
		else:
			_mc_names.erase(e.peer)
		var text := "%s %s ваш мир" % [nm, "зашёл в" if e.on else "вышел из"]
		app.toast(text, UI.JAVA if e.on else UI.MUTED)
		_attention(text, false)
		if app.server.is_alive() and app.server.edition == "java" and not app.own.active:
			app.server.command('tellraw @a {"text":"[LANTERN] %s","color":"%s"}' % [text.json_escape(), "green" if e.on else "gray"])
	var s := status()
	var k := 1 if s.ok else 0
	if s.ok:
		_bad_since = 0
		_alerted = false
	elif not s.get("wait", false):
		if _bad_since == 0:
			_bad_since = Time.get_ticks_msec()
		if not _alerted and _was_ok == 1 and Time.get_ticks_msec() - _bad_since > 8000:
			_alerted = true
			_attention("Мир не виден друзьям: вернитесь в мир / откройте LANTERN", true)
	if k != _was_ok and (s.ok or not s.get("wait", false)):
		_was_ok = k
	changed.emit()


## Свой сервер: кто вошёл в Minecraft вскоре после друга из LANTERN — это он.
func _on_mc_players(list: PackedStringArray) -> void:
	# хозяин зашёл в свой сервер Java — сразу оператор: /kick и /ban прямо в игре
	var me := String(Cfg.get_value("host", "mc_nick", ""))
	if me != "" and me in list and not _opped and app.server.is_alive() and app.server.edition == "java":
		_opped = true
		app.server.command("op " + me)
	var known := _mc_names.values()
	known.append(me)
	for n in list:
		if n in known:
			continue
		var now := Time.get_ticks_msec()
		_await_mc = _await_mc.filter(func(x): return now - int(x[1]) < 120000)
		if not _await_mc.is_empty():
			var p: Array = _await_mc.pop_front()
			_mc_names[p[0]] = n
	if app.server.is_alive() and app.server.edition == "java" and code() != "" and list.size() > 0:
		app.server.command('tellraw @a {"text":"[LANTERN] Код для друзей: %s","color":"aqua"}' % code())


func _attention(text: String, alert: bool) -> void:
	if OS.get_name() == "Android":
		return   # уведомление и окошко шлёт поток моста
	if mini:
		mini.flash(text, alert)
	if DisplayServer.get_name() != "headless":
		DisplayServer.window_request_attention()


# ------------------------------------------------------------------ Android: разрешение «поверх других приложений»

func _ask_overlay() -> void:
	if _overlay_asked or Cfg.autotest != "":
		return
	var cls = app.relay_host.android
	if cls == null or bool(cls.canDrawOverlays()):
		return
	_overlay_asked = true
	if bool(Cfg.get_value("own", "overlay_asked", false)):
		return
	Cfg.set_value("own", "overlay_asked", true)
	var v: VBoxContainer = Overlays.sheet(app, "Кнопка поверх Minecraft", "Маленький кружок LANTERN прямо в игре: видно, могут ли друзья зайти, кто зашёл, и можно выгнать игрока, не выходя из Minecraft.")
	v.add_child(UI.para("Android спросит разрешение «Поверх других приложений» — включите его для LANTERN и вернитесь назад. Без него всё будет в уведомлении.", 14, UI.MUTED))
	v.add_child(UI.button("Разрешить", "primary", func():
		Overlays.close_sheet(app)
		cls.requestOverlay(), "check"))
	v.add_child(UI.button("Не сейчас", "ghost", func(): Overlays.close_sheet(app)))


# ------------------------------------------------------------------ ПК: окошко поверх всех окон

func _open_mini() -> void:
	if mini:
		return
	mini = preload("res://scripts/ui/mini_window.gd").new()
	mini.ctl = self
	app.add_child(mini)


func set_mini(on: bool) -> void:
	Cfg.set_value("host", "mini", on)
	if on and hosting() and OS.get_name() != "Android":
		_open_mini()
	elif not on and mini:
		mini.queue_free()
		mini = null
