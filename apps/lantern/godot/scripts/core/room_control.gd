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
		return {"ok": false, "text": L.t("Мир закрыт"), "why": ""}
	var st: Dictionary = app.relay_host.stats()
	if st.state == "connecting":
		return {"ok": false, "text": L.t("Открываем мир для друзей…"), "why": "", "wait": true}
	if st.state != "online":
		return {"ok": false, "text": L.t("Мир не виден друзьям"), "why": L.t("Нет связи с LANTERN. Проверьте интернет — комната вернётся с тем же кодом.")}
	if app.own.active and not app.own.alive():
		return {"ok": false, "text": L.t("Мир не виден друзьям"), "why": L.t("Minecraft не отвечает. Вернитесь в мир") + (L.t(" и проверьте «Видимость для игроков LAN».") if app.own.world.get("edition", "") == "bedrock" else L.t(" («Открыть для сети»)."))}
	if not app.own.active and not app.server.is_alive():
		return {"ok": false, "text": L.t("Мир не виден друзьям"), "why": L.t("Сервер не запущен.")}
	return {"ok": true, "text": L.t("Друзья могут зайти прямо сейчас ✓"), "why": ""}


func code() -> String:
	var c: String = app.relay_host.stats().get("code", "")
	return RoomCode.pretty(c) if c != "" else ""


func world_name() -> String:
	return String(app.own.world.get("name", L.t("Мир"))) if app.own.active else String(app.server.opts.get("world", L.t("Мир")))


func edition() -> String:
	if app.own.active:
		return String(app.own.world.get("edition", "bedrock"))
	return "both" if app.server.edition == "java" and app.server.opts.get("crossplay", false) else app.server.edition


func invite_link() -> String:
	return Invite.web_link(app.relay_host.stats().get("code", ""), edition(), world_name(), Cfg.relay_url())


## Ссылка lantern:// (QR-код: камера открывает приложение сразу)
func app_link() -> String:
	return Invite.link(app.relay_host.stats().get("code", ""), edition(), world_name(), Cfg.relay_url())


func share_text() -> String:
	if code() == "":
		return ""
	return L.t("Заходи в мой мир «%s» через LANTERN — нажми на ссылку:\n%s\nКод: %s") % [world_name(), invite_link(), code()]


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
	app.toast(L.t("Приглашение отправлено"), UI.JAVA)


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
var _cheat_ops: Array = []      # кому уже выданы права при включённых «читах»


func kick(peer: String) -> void:
	app.relay_host.kick(peer, false)
	_server_cmd("kick", peer)
	app.toast(L.t("Игрок выгнан из мира"), UI.YELLOW)


func ban(peer: String) -> void:
	for p in app.relay_host.peers():
		if p.peer == peer:
			_remember_ban(p.user, String(_mc_names.get(peer, "")))
	app.relay_host.kick(peer, true)
	_server_cmd("ban", peer)
	app.toast(L.t("Игроку закрыт вход в ваш мир"), UI.RED)


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


func _remember_ban(u: Variant, mc := "") -> void:
	if not u is Dictionary or int(u.get("id", 0)) <= 0:
		return
	var a := bans()
	for b in a:
		if int(b.id) == int(u.id):
			return
	a.append({"id": int(u.id), "nick": String(u.get("nick", "")), "tag": String(u.get("tag", "")), "mc": mc})
	Cfg.set_value("host", "bans", a)
	app.relay_host.bans = ban_ids()


func unban(id: int) -> void:
	for b in bans():
		if int(b.id) == id and String(b.get("mc", "")) != "" and _live():
			for c in GameRules.commands(app.server.edition, "pardon", String(b.mc)):
				app.server.command(c)
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
	app.toast(L.t("Теперь зайти могут все в LANTERN") if on else L.t("Теперь зайти можно только по коду"), UI.JAVA)


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
		return {"title": String(w.get("name", L.t("Мир"))), "tags": [], "version": String(w.get("version", "")), "slots": mx if mx > 0 else 10}
	var o: Dictionary = app.server.opts
	return {"title": String(o.get("public_title", o.get("world", L.t("Мир")))), "tags": o.get("tags", []),
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
	app.server.state_changed.connect(func(st):
		if st == "running":
			rules_apply())


## Комнату только что открыли (OwnWorld.start / сервер).
func room_opened() -> void:
	_was_ok = -1
	_alerted = false
	_bad_since = 0
	_mc_names.clear()
	_cheat_ops.clear()
	_opped = false
	app.relay_host.bans = ban_ids()
	app.relay_host.share_text = ""
	app.remember_hosted()
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
	# Android: что с кнопкой поверх игры — в журнал (для «Сообщить о проблеме»), когда состояние меняется
	if OS.get_name() == "Android" and app.relay_host.android != null:
		_ov_t += 0.5
		if _ov_t >= 5.0:
			_ov_t = 0.0
			var ovs := L.t("%s; разрешение=%s") % [String(app.relay_host.android.overlayState()), bool(app.relay_host.android.canDrawOverlays())]
			if ovs != _ov_last:
				_ov_last = ovs
				AppLog.add("overlay", ovs)
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
		var text := L.t("%s %s ваш мир") % [nm, L.t("зашёл в") if e.on else L.t("вышел из")]
		app.toast(text, UI.JAVA if e.on else UI.MUTED)
		_attention(text, false, e.on)
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
			_attention(L.t("Мир не виден друзьям: вернитесь в мир / откройте LANTERN"), true)
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
	if app.server.is_alive() and app.server.edition == "java" and not app.own.active and rules_get().get("cheats", false):
		for n in list:
			if not n in _cheat_ops:
				_cheat_ops.append(n)
				app.server.command("op " + n)
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
		app.server.command('tellraw @a {"text":L.t("[LANTERN] Код для друзей: %s"),"color":"aqua"}' % code())


func _attention(text: String, alert: bool, notify := true) -> void:
	if OS.get_name() == "Android":
		return   # уведомление и окошко шлёт поток моста
	if mini:
		mini.flash(text, alert)
	if DisplayServer.get_name() != "headless":
		DisplayServer.window_request_attention()
	if notify:      # системное уведомление — когда друг зашёл или мир пропал (не на каждый выход)
		os_notify("LANTERN", text)


## Уведомление системы (Windows — всплывающее в углу и в центре уведомлений; Linux — notify-send).
## Запасной путь, когда игра во весь экран закрывает окошко. Текст передаём через переменные окружения — не в команду.
static func os_notify(title: String, text: String) -> bool:
	if Cfg.autotest != "" and not Cfg.autotest_args.has("notify"):
		return false
	if not bool(Cfg.get_value("host", "notify", true)):
		return false
	OS.set_environment("LANTERN_NOTE_TITLE", title)
	OS.set_environment("LANTERN_NOTE_TEXT", text)
	var pid := -1
	match OS.get_name():
		"Windows":
			var ps := "Add-Type -AssemblyName System.Windows.Forms; Add-Type -AssemblyName System.Drawing; $n = New-Object System.Windows.Forms.NotifyIcon; " + \
				"$n.Icon = [System.Drawing.Icon]::ExtractAssociatedIcon($env:LANTERN_NOTE_EXE); $n.Visible = $true; " + \
				"$n.ShowBalloonTip(6000, $env:LANTERN_NOTE_TITLE, $env:LANTERN_NOTE_TEXT, [System.Windows.Forms.ToolTipIcon]::None); Start-Sleep -Seconds 7; $n.Dispose()"
			OS.set_environment("LANTERN_NOTE_EXE", OS.get_executable_path().replace("/", "\\"))
			pid = OS.create_process("powershell.exe", ["-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden", "-Command", ps])
		"Linux", "FreeBSD":
			pid = OS.create_process("notify-send", ["-a", "LANTERN", "-t", "6000", title, text])
	AppLog.add("mini", "уведомление системы: %s (pid %d)" % [text, pid])
	return pid > 0


# ------------------------------------------------------------------ Android: разрешение «поверх других приложений»

func _ask_overlay() -> void:
	if _overlay_asked or Cfg.autotest != "":
		return
	var cls = app.relay_host.android
	if cls == null:
		AppLog.add("overlay", "служба Android недоступна — кнопки поверх игры не будет")
		return
	var can := bool(cls.canDrawOverlays())
	var maker := String(cls.maker())
	AppLog.add("overlay", "разрешение «поверх других приложений»: %s; устройство: %s; %s" % ["есть" if can else "НЕТ", maker, String(cls.overlayState())])
	_overlay_asked = true
	if can:
		vendor_sheet(app, false)
		return
	if bool(Cfg.get_value("own", "overlay_asked", false)):
		return
	Cfg.set_value("own", "overlay_asked", true)
	var v: VBoxContainer = Overlays.sheet(app, L.t("Кнопка поверх Minecraft"), L.t("Маленький кружок LANTERN прямо в игре: видно, могут ли друзья зайти, кто зашёл, и можно выгнать игрока, не выходя из Minecraft."))
	v.add_child(UI.para(L.t("Android спросит разрешение «Поверх других приложений» — включите его для LANTERN и вернитесь назад. Без него всё будет в уведомлении."), 14, UI.MUTED))
	v.add_child(UI.button(L.t("Разрешить"), "primary", func():
		Overlays.close_sheet(app)
		AppLog.add("overlay", "открываем настройку «Поверх других приложений»")
		cls.requestOverlay()
		_vendor_after_overlay = true, "check"))
	v.add_child(UI.button(L.t("Не сейчас"), "ghost", func(): Overlays.close_sheet(app)))


var _vendor_after_overlay := false
var _ov_t := 0.0
var _ov_last := ""


## Вернулись из настроек: записать в журнал, что вышло с разрешением, и один раз показать шаги для Xiaomi и др.
func on_resume() -> void:
	if OS.get_name() != "Android":
		return
	var cls = app.relay_host.android if app.relay_host.android != null else JavaClassWrapper.wrap(OwnWorld.ANDROID_SERVICE)
	if cls == null:
		return
	AppLog.add("overlay", "вернулись в LANTERN: поверх других приложений=%s; %s" % [bool(cls.canDrawOverlays()), String(cls.overlayState())])
	if _vendor_after_overlay and bool(cls.canDrawOverlays()):
		_vendor_after_overlay = false
		vendor_sheet(app, false)


## Xiaomi / Redmi / POCO (MIUI, HyperOS): одного разрешения мало — нужны ещё «всплывающие окна в фоне», автозапуск
## и батарея «без ограничений». Huawei / Honor / Oppo / Realme / Vivo / Samsung: батарея. Показываем один раз
## (force — из «Мой мир» по кнопке «Кнопка не видна в игре?»). Ничего не запрашиваем сами — только открываем экран настроек.
static func vendor_sheet(app: Control, force: bool, maker_override := "") -> bool:
	var cls = JavaClassWrapper.wrap(OwnWorld.ANDROID_SERVICE) if OS.get_name() == "Android" else null
	var maker := maker_override.to_lower() if maker_override != "" else (String(cls.maker()).to_lower() if cls != null else "")
	var kind := vendor_kind(maker)
	if kind == "" and not force:
		return false
	if not force and bool(Cfg.get_value("own", "vendor_sheet", false)):
		return false
	Cfg.set_value("own", "vendor_sheet", true)
	AppLog.add("overlay", "показываем шаги для «%s» (%s)" % [maker, kind if kind != "" else "общие"])
	var steps: Array = VENDOR_STEPS.get(kind, VENDOR_STEPS["other"])
	var v: VBoxContainer = Overlays.sheet(app, L.t("Чтобы кнопка была видна в игре"), String(steps[0]))
	for i in range(1, steps.size()):
		v.add_child(app._num_row(str(i), String(steps[i][0]), String(steps[i][1])))
	v.add_child(UI.button(L.t("Открыть настройки"), "primary", func():
		if cls != null:
			AppLog.add("overlay", "открыли экран настроек: " + String(cls.openVendorSettings()))
		Overlays.close_sheet(app), "settings"))
	v.add_child(UI.button(L.t("Уже сделано"), "ghost", func(): Overlays.close_sheet(app)))
	return true


static func vendor_kind(maker: String) -> String:
	var m := maker.to_lower()
	for k in ["xiaomi", "redmi", "poco"]:
		if m.contains(k):
			return "xiaomi"
	for k in ["huawei", "honor"]:
		if m.contains(k):
			return "huawei"
	for k in ["oppo", "realme", "oneplus", "vivo", "iqoo"]:
		if m.contains(k):
			return "oppo"
	if m.contains("samsung"):
		return "samsung"
	return ""


const VENDOR_STEPS := {
	"xiaomi": ["На Xiaomi, Redmi и POCO кнопку поверх игры нужно разрешить отдельно — три переключателя:",
		["«Другие разрешения»", "Включите «Отображать всплывающие окна при работе в фоновом режиме» и «Всплывающие окна»"],
		["«Автозапуск»", "Включите — иначе система выгрузит LANTERN, пока вы играете"],
		["«Контроль активности» (батарея)", "Выберите «Нет ограничений»"]],
	"huawei": ["На Huawei и Honor система закрывает приложения в фоне. Два шага:",
		["«Запуск приложений» → LANTERN", "Выключите «Автоматическое управление» и оставьте все три переключателя включёнными"],
		["Батарея", "Разрешите LANTERN работать в фоне"]],
	"oppo": ["На Oppo, Realme, OnePlus и Vivo система закрывает приложения в фоне. Два шага:",
		["«Расход батареи» → LANTERN", "Включите «Разрешить работу в фоне»"],
		["«Плавающие окна»", "Разрешите LANTERN показывать окна поверх других приложений"]],
	"samsung": ["На Samsung система может «усыпить» LANTERN, пока вы в игре. Один шаг:",
		["«Батарея» → LANTERN", "Выберите «Не ограничено»"]],
	"other": ["Если кнопка пропадает в игре, проверьте два пункта:",
		["«Поверх других приложений»", "Разрешение должно быть включено для LANTERN"],
		["Батарея", "Выберите «Без ограничений» — иначе система остановит LANTERN в фоне"]],
}


# ------------------------------------------------------------------ правила мира (сервер LANTERN: командами сервера)

const RULES_OWN_HINT := "Остальные правила — в настройках мира в самом Minecraft."


## Правила работают только на сервере LANTERN (Paper / BDS). Мир из самого Minecraft снаружи не настроить.
func rules_available() -> bool:
	return not app.own.active


func _live() -> bool:
	return not app.own.active and app.server.is_alive() and app.server.state == "running"


func _ctx() -> Dictionary:
	var r := rules_get()
	return {"mc": String(app.server._installed.get("mc_version", "")), "players": Array(app.server.players),
		"host": String(Cfg.get_value("host", "mc_nick", "")), "admins": r.get("admins", [])}


func rules_get() -> Dictionary:
	return app.server.rules()


func _rules_save(d: Dictionary) -> void:
	GameRules.save_for(app.server.edition, String(app.server.opts.get("world", L.t("Мир"))), d)


## Переключатель правила: запоминаем для этого мира и, если сервер работает, сразу шлём команду.
func rule_set(key: String, value: Variant) -> void:
	var d := rules_get()
	d[key] = value
	_rules_save(d)
	if _live():
		for c in GameRules.commands(app.server.edition, key, value, _ctx()):
			app.server.command(c)
	AppLog.add("host", "правило мира: %s = %s" % [key, str(value)])
	changed.emit()


## Режим и сложность — настройки сервера (server.properties) + команда на ходу.
func opt_live(key: String, value: String) -> void:
	app.server.opts[key] = value
	app.server._save_opts()
	if _live():
		for c in GameRules.commands(app.server.edition, key, value, _ctx()):
			app.server.command(c)
	changed.emit()


## Списки: «Только свои» (wl) и админы (admins).
func list_add(list: String, nick: String) -> bool:
	var n := GameRules.clean_nick(nick)
	var d := rules_get()
	var a: Array = d.get(list, [])
	if n == "" or n in a:
		return false
	a.append(n)
	d[list] = a
	_rules_save(d)
	if _live():
		for c in GameRules.commands(app.server.edition, "wl_add" if list == "wl" else "admin_add", n):
			app.server.command(c)
	changed.emit()
	return true


func list_remove(list: String, nick: String) -> void:
	var d := rules_get()
	var a: Array = d.get(list, [])
	a.erase(nick)
	d[list] = a
	_rules_save(d)
	if _live():
		for c in GameRules.commands(app.server.edition, "wl_remove" if list == "wl" else "admin_remove", nick):
			app.server.command(c)
	changed.emit()


## Сервер заработал — применяем сохранённые правила этого мира заново.
func rules_apply() -> void:
	if app.own.active or not app.server.is_alive():
		return
	var d := rules_get()
	var cmds := GameRules.start_commands(app.server.edition, d, _ctx())
	var host := String(Cfg.get_value("host", "mc_nick", ""))
	if host != "":
		cmds.append_array(GameRules.commands(app.server.edition, "admin_add", host))
	for c in cmds:
		app.server.command(c)
	AppLog.add("host", "правила мира применены (%d команд)" % cmds.size())


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
