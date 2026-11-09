extends BoxContainer
## «Мой мир»: большой код комнаты (копировать, поделиться, QR), живой статус, игроки, журнал, кнопки.

var app
var arg := {}

var _state_pill: PanelContainer
var _status: Label
var _bar: ProgressBar
var _bar_lbl: Label
var _code_row: HBoxContainer
var _code_tiles: Array[Label] = []
var _code_hint: Label
var _qr: TextureRect
var _relay_lbl: Label
var _lan_box: VBoxContainer
var _players_box: HFlowContainer
var _players_title: Label
var _log: RichTextLabel
var _cmd: LineEdit
var _btns := {}
var _last_code := "?"
var _shown_code := ""
var _tick := 0.0
var _last_peers := -1
var _code_card: PanelContainer
var _log_body: Control

const STATES := {
	"installing": ["Готовим сервер", UI.YELLOW],
	"starting": ["Запускается", UI.YELLOW],
	"running": ["Работает", UI.JAVA],
	"stopping": ["Останавливается", UI.YELLOW],
	"stopped": ["Остановлен", UI.MUTED],
	"error": ["Ошибка", UI.RED],
	"idle": ["Не запущен", UI.MUTED],
}


func _ready() -> void:
	var s: ServerHost = app.server
	var compact := UI.compact()
	vertical = compact
	add_theme_constant_override("separation", 16 if compact else 24)
	app.set_title(s.opts.get("world", "Мой мир"), "Java Edition" + (" + телефоны" if s.opts.get("crossplay", false) else "") if s.edition == "java" else "Bedrock Edition")
	var left := UI.vbox(16)
	left.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	add_child(left)
	var right := left if compact else UI.vbox(16)
	if not compact:
		right.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		add_child(right)

	# --- состояние сервера
	var head := UI.panel(18)
	var hv := UI.vbox(10)
	head.add_child(hv)
	var hr := UI.hbox(12)
	hv.add_child(hr)
	hr.add_child(UI.texture("res://assets/img/mini_grass.png" if s.edition == "java" else "res://assets/img/mini_stone.png", 44, 44))
	var tv := UI.vbox(0)
	tv.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	var wl := UI.label(s.opts.get("world", "Мир"), 19, UI.TEXT, UI.font_black)
	wl.clip_text = true
	wl.text_overrun_behavior = TextServer.OVERRUN_TRIM_ELLIPSIS
	tv.add_child(wl)
	var sub := "Java Edition" + (" + телефоны" if s.opts.get("crossplay", false) and s.edition == "java" else "") if s.edition == "java" else "Bedrock Edition"
	tv.add_child(UI.label(sub, 13, UI.MUTED))
	hr.add_child(tv)
	_state_pill = UI.pill("…", UI.MUTED)
	hr.add_child(_state_pill)
	_status = UI.para("", 14, UI.MUTED)
	hv.add_child(_status)
	_bar = ProgressBar.new()
	_bar.custom_minimum_size.y = 6
	_bar.show_percentage = false
	_bar.max_value = 1.0
	hv.add_child(_bar)
	_bar_lbl = UI.label("", 12, UI.DIM)
	hv.add_child(_bar_lbl)
	left.add_child(head)

	# --- код для друзей
	_code_card = PanelContainer.new()
	var cst := UI.box(Color("201a10"), UI.R_XL, 0, Color(UI.accent(), 0.35), 1)
	UI._hpad(cst, 20, 20)
	_code_card.add_theme_stylebox_override("panel", cst)
	var cv := UI.vbox(14)
	_code_card.add_child(cv)
	var ctop := UI.hbox(8)
	var cap := UI.caption("Код для друзей", UI.accent())
	cap.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	ctop.add_child(cap)
	_relay_lbl = UI.label("", 13, UI.DIM, UI.font_bold)
	if compact:
		var qb := UI.icon_button("qr", _qr_sheet, "QR-код", UI.accent().lightened(0.2), Color(UI.accent(), 0.15))
		ctop.add_child(qb)
		_btns["qr"] = qb
	else:
		ctop.add_child(_relay_lbl)
	cv.add_child(ctop)
	var cmid := UI.hbox(16)
	cv.add_child(cmid)
	_code_row = UI.hbox(6 if compact else 8)
	_code_row.alignment = BoxContainer.ALIGNMENT_CENTER
	_code_row.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_code_row.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	for i in 6:
		if i == 3:
			_code_row.add_child(UI.label("–", 26, UI.DIM, UI.font_black))
		var tile := PanelContainer.new()
		tile.add_theme_stylebox_override("panel", UI.box(Color(0, 0, 0, 0.35), UI.R_M))
		tile.custom_minimum_size = Vector2(34 if compact else 52, 58 if compact else 68)
		if compact:
			tile.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		var l := UI.label("·", 30 if compact else 36, UI.accent().lightened(0.15), UI.font_black)
		l.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
		l.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
		tile.add_child(l)
		_code_row.add_child(tile)
		_code_tiles.append(l)
	cmid.add_child(_code_row)
	_qr = TextureRect.new()
	_qr.custom_minimum_size = Vector2(132, 132)
	_qr.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	_qr.texture_filter = CanvasItem.TEXTURE_FILTER_NEAREST
	_qr.visible = not compact
	if not compact:
		cmid.add_child(_qr)
	var acts: BoxContainer = UI.vbox(8) if compact else UI.hbox(8)
	var cb := UI.button("Скопировать код", "primary", _copy_code, "copy")
	cb.clip_text = true
	cb.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	acts.add_child(cb)
	_btns["copy"] = cb
	var sh := UI.button("Поделиться ссылкой", "tonal", _share, "share")
	sh.clip_text = true
	sh.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	acts.add_child(sh)
	_btns["share"] = sh
	cv.add_child(acts)
	if compact:
		cv.add_child(_relay_lbl)
	_code_hint = UI.para("Друг открывает LANTERN → «Зайти к другу» → вводит код. Мир сам появится у него в Minecraft.", 13, UI.MUTED)
	cv.add_child(_code_hint)
	left.add_child(_code_card)

	# --- игроки
	var pp := UI.panel(18)
	var pv := UI.vbox(12)
	pp.add_child(pv)
	_players_title = UI.h3("В мире: 0")
	pv.add_child(_players_title)
	_players_box = UI.flow(8)
	pv.add_child(_players_box)
	left.add_child(pp)

	# --- кнопки управления
	var br := UI.flow(8)
	_btns["stop"] = UI.button("Остановить", "danger", _stop, "stop")
	_btns["restart"] = UI.button("Перезапуск", "secondary", func(): app.server.restart(), "refresh")
	_btns["start"] = UI.button("Запустить снова", "primary", func(): app.server.host(app.server.edition, app.server.opts), "play")
	_btns["backup"] = UI.button("Резервная копия", "secondary", _backup, "download")
	_btns["folder"] = UI.button("Папка мира", "secondary", func(): OS.shell_open(ProjectSettings.globalize_path(app.server.world_path())), "folder")
	_btns["copies"] = UI.button("Копии миров", "ghost", func(): app.go("backups", true), "cloud")
	_btns["check"] = UI.button("Проверка системы", "secondary", func(): app.go("syscheck", true), "info")
	for k in ["stop", "restart", "start", "backup", "folder", "copies", "check"]:
		br.add_child(_btns[k])
	right.add_child(br)

	# --- та же Wi-Fi сеть
	var lp := UI.panel(18)
	var lv := UI.vbox(8)
	lp.add_child(lv)
	var lh := UI.hbox(10)
	lh.add_child(UI.icon("wifi", 20, UI.MUTED))
	lh.add_child(UI.label("В той же Wi-Fi сети", 15, UI.TEXT, UI.font_bold))
	lv.add_child(lh)
	lv.add_child(UI.para("Друзья рядом могут зайти и без кода — по адресу:", 13, UI.MUTED))
	_lan_box = UI.vbox(4)
	lv.add_child(_lan_box)
	right.add_child(lp)

	# --- журнал сервера
	var logp := UI.panel(14, Color("0b0d12"))
	logp.size_flags_vertical = Control.SIZE_EXPAND_FILL
	var lgv := UI.vbox(10)
	logp.add_child(lgv)
	var lgh := UI.button("Журнал сервера и команды", "ghost", Callable(), "chevron_down")
	lgh.alignment = HORIZONTAL_ALIGNMENT_LEFT
	lgh.icon_alignment = HORIZONTAL_ALIGNMENT_RIGHT
	lgh.add_theme_color_override("font_color", UI.MUTED)
	lgv.add_child(lgh)
	var body := UI.vbox(10)
	body.size_flags_vertical = Control.SIZE_EXPAND_FILL
	lgv.add_child(body)
	_log_body = body
	body.visible = not compact
	lgh.pressed.connect(func(): body.visible = not body.visible)
	_log = RichTextLabel.new()
	_log.scroll_following = true
	_log.selection_enabled = true
	_log.size_flags_vertical = Control.SIZE_EXPAND_FILL
	_log.custom_minimum_size.y = 220 if compact else 160
	_log.bbcode_enabled = false
	body.add_child(_log)
	_cmd = UI.line_edit("", "Команда серверу, например: op ВашНик")
	_cmd.add_theme_font_override("font", UI.font_mono)
	_cmd.add_theme_font_size_override("font_size", 14)
	_cmd.text_submitted.connect(func(t):
		if t.strip_edges() != "":
			app.server.command(t.strip_edges().trim_prefix("/"))
			_cmd.text = "")
	body.add_child(_cmd)
	right.add_child(logp)

	s.state_changed.connect(_on_state)
	s.status.connect(func(t): _status.text = t)
	s.progress.connect(_on_progress)
	s.log_line.connect(_add_log)
	s.players_changed.connect(_on_players)
	for l in s.history:
		_log.add_text(l + "\n")
	_status.text = s.last_status
	_on_progress(s.last_progress[0], s.last_progress[1])
	_on_state(s.state)
	_on_players(s.players)
	_refresh_relay()
	FX.stagger(left, 0.05)


func _process(d: float) -> void:
	_tick += d
	if _tick > 0.4:
		_tick = 0
		_refresh_relay()


func _add_log(t: String) -> void:
	_log.add_text(t + "\n")
	if _log.get_line_count() > 1500:
		_log.remove_paragraph(0)


func _on_progress(label: String, f: float) -> void:
	_bar_lbl.text = label
	if f < 0:
		_bar.value = fmod(Time.get_ticks_msec() / 1500.0, 1.0)
	else:
		_bar.value = f
		if f < 1.0:
			_bar_lbl.text = "%s — %d%%" % [label, int(f * 100)]


func _on_state(st: String) -> void:
	var info: Array = STATES.get(st, ["", UI.MUTED])
	var l: Label = _state_pill.get_meta("label")
	l.text = info[0]
	l.add_theme_color_override("font_color", (info[1] as Color).lightened(0.3))
	_state_pill.add_theme_stylebox_override("panel", UI._hpad(UI.box(Color(info[1], 0.15), 99), 10, 3))
	var busy := st in ["installing", "starting"]
	_bar.visible = busy
	_bar_lbl.visible = busy and _bar_lbl.text != ""
	var alive: bool = app.server.is_alive()
	_btns.stop.visible = alive
	_btns.restart.visible = st == "running"
	_btns.start.visible = st in ["stopped", "error"]
	_btns.backup.disabled = busy
	_btns.check.visible = st == "error"
	if st == "error":
		_status.text = "Что-то пошло не так: " + app.server.last_error
		_status.add_theme_color_override("font_color", Color("ff9d9d"))
	else:
		_status.add_theme_color_override("font_color", UI.MUTED)
	if st == "running":
		_status.text = "Сервер работает. Зовите друзей!"
	_refresh_lan()


func _on_players(list: PackedStringArray) -> void:
	for c in _players_box.get_children():
		c.queue_free()
	_players_title.text = "В мире: %d" % list.size()
	if list.is_empty():
		_players_box.add_child(UI.label("Пока никого. Отправьте друзьям код!", 14, UI.DIM))
	for n in list:
		var chip := UI.pill("●  " + n, UI.JAVA)
		_players_box.add_child(chip)
		FX.enter(chip, Vector2(0, 8))


func _refresh_lan() -> void:
	for c in _lan_box.get_children():
		c.queue_free()
	var ips := RoomCode.lan_addresses()
	if ips.is_empty():
		_lan_box.add_child(UI.label("Нет подключения к домашней сети", 14, UI.DIM))
		return
	var s: ServerHost = app.server
	var ports := []
	if s.edition == "java":
		ports.append(["Java", ServerHost.JAVA_PORT])
		if s.opts.get("crossplay", false):
			ports.append(["Телефон", ServerHost.BEDROCK_PORT])
	else:
		ports.append(["Bedrock", ServerHost.BEDROCK_PORT])
	for p in ports:
		var row := UI.hbox(8)
		var nl := UI.label(p[0], 14, UI.MUTED)
		nl.custom_minimum_size.x = 72
		row.add_child(nl)
		var addr := "%s:%d" % [ips[0], p[1]]
		var al := UI.label(addr, 14, UI.TEXT, UI.font_mono)
		al.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		al.clip_text = true
		row.add_child(al)
		row.add_child(UI.icon_button("copy", func(): UI.copy(addr); app.toast("Адрес скопирован"), "Копировать", UI.MUTED))
		_lan_box.add_child(row)


func _set_code(c: String) -> void:
	if c == _shown_code:
		return
	var reveal := _shown_code == "" or _shown_code.contains("·")
	_shown_code = c
	var chars := c.replace("-", "")
	for i in 6:
		_code_tiles[i].text = chars[i] if i < chars.length() else "·"
	if reveal and not c.contains("·"):
		FX.flip_in(_code_tiles, 0.08)
		var st: StyleBoxFlat = _code_card.get_theme_stylebox("panel")
		if FX.on():
			var tw := _code_card.create_tween()
			tw.tween_property(st, "border_color", Color(UI.accent(), 0.9), 0.25)
			tw.tween_property(st, "border_color", Color(UI.accent(), 0.35), 0.8)


func _refresh_relay() -> void:
	var s: ServerHost = app.server
	var r: Dictionary = app.relay_host.stats()
	var code: String = r.get("code", "")
	if not s.opts.get("internet", true):
		_set_code("······")
		_code_hint.text = "Интернет-комната выключена: зайти смогут только друзья из вашей Wi-Fi сети."
		_relay_lbl.text = ""
		_qr.texture = null
		_btns.copy.disabled = true
		_btns.share.disabled = true
		return
	var shown: bool = code != "" and r.state in ["online", "reconnecting"]
	_btns.copy.disabled = not shown
	_btns.share.disabled = not shown
	if _btns.has("qr"):
		_btns.qr.disabled = not shown
	if shown:
		_set_code(RoomCode.pretty(code))
	elif s.state in ["installing", "starting"]:
		_set_code("······")
	if code != _last_code:
		_last_code = code
		if code != "":
			_qr.texture = ImageTexture.create_from_image(QR.encode(_invite_link(), QR.ECC_M).to_image(6, Color("0b0e14"), Color("eef1f7")))
		else:
			_qr.texture = null
	match r.state:
		"online":
			var peers := int(r.peers)
			_relay_lbl.text = "● друзей в мире: %d" % peers
			_relay_lbl.add_theme_color_override("font_color", UI.JAVA)
			if _last_peers >= 0 and peers > _last_peers:
				FX.burst(app, _code_card.get_global_rect().get_center() - app.global_position, 34)
				app.toast("Друг зашёл в ваш мир!", UI.JAVA)
			_last_peers = peers
		"connecting":
			_relay_lbl.text = "открываем комнату…"
			_relay_lbl.add_theme_color_override("font_color", UI.DIM)
		"reconnecting", "error":
			_relay_lbl.text = "⚠ " + str(r.error)
			_relay_lbl.add_theme_color_override("font_color", UI.YELLOW)
		_:
			_relay_lbl.text = "код появится после запуска" if s.state in ["installing", "starting"] else ""
			_relay_lbl.add_theme_color_override("font_color", UI.DIM)


func _invite_link() -> String:
	var s: ServerHost = app.server
	var ed := s.edition if not (s.edition == "java" and s.opts.get("crossplay", false)) else "both"
	return Invite.link(app.relay_host.stats().get("code", ""), ed, s.opts.get("world", "Мир"), Cfg.relay_url())


func _copy_code() -> void:
	var r: Dictionary = app.relay_host.stats()
	if r.code != "":
		UI.copy(RoomCode.pretty(r.code))
		app.toast("Код %s скопирован — отправьте его друзьям" % RoomCode.pretty(r.code), UI.JAVA)


## Поделиться: готовое сообщение с кодом и ссылкой (вставьте в любой мессенджер).
func _share() -> void:
	var r: Dictionary = app.relay_host.stats()
	if r.code == "":
		return
	UI.copy("Заходи в мой мир «%s» в Minecraft через LANTERN!\nКод: %s\n%s" % [app.server.opts.get("world", "Мир"), RoomCode.pretty(r.code), _invite_link()])
	app.toast("Приглашение со ссылкой скопировано — вставьте его в чат", UI.JAVA)


func _qr_sheet() -> void:
	var v: VBoxContainer = Overlays.sheet(app, "QR-код комнаты", "Друг наводит камеру телефона — LANTERN откроется сам и подключится")
	var t := TextureRect.new()
	t.texture = _qr.texture
	t.custom_minimum_size = Vector2(240, 240)
	t.size_flags_horizontal = Control.SIZE_SHRINK_CENTER
	t.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	t.texture_filter = CanvasItem.TEXTURE_FILTER_NEAREST
	v.add_child(t)
	var cl := UI.label(_shown_code, 30, UI.accent(), UI.font_black)
	cl.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	v.add_child(cl)


func _stop() -> void:
	app.relay_host.stop()
	app.server.stop()


func _backup() -> void:
	_btns.backup.disabled = true
	_btns.backup.text = "Сохраняем…"
	var p: String = await app.server.backup()
	_btns.backup.text = "Резервная копия"
	_btns.backup.disabled = false
	if p == "":
		app.toast("Не получилось: " + app.server.last_error, UI.RED)
	else:
		app.toast("Копия сохранена: %s — она в «Копиях миров»" % p.get_file())
