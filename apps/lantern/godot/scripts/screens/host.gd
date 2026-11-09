extends HBoxContainer
## Панель запущенного мира: код для друзей, QR, состояние, игроки, журнал, кнопки.

var app
var arg := {}

var _state_pill: PanelContainer
var _status: Label
var _bar: ProgressBar
var _bar_lbl: Label
var _code_lbl: Label
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
var _tick := 0.0

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
	add_theme_constant_override("separation", 20)
	# ================= левая колонка
	var left := UI.vbox(16)
	left.custom_minimum_size.x = 430
	add_child(left)
	var head := UI.panel(20)
	var hv := UI.vbox(10)
	head.add_child(hv)
	var hr := UI.hbox(12)
	hv.add_child(hr)
	hr.add_child(UI.texture("res://assets/img/mini_grass.png" if s.edition == "java" else "res://assets/img/mini_stone.png", 44, 44))
	var tv := UI.vbox(0)
	tv.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	var wl := UI.label(s.opts.get("world", "Мир"), 22, UI.TEXT, UI.font_black)
	wl.clip_text = true
	wl.text_overrun_behavior = TextServer.OVERRUN_TRIM_ELLIPSIS
	tv.add_child(wl)
	var sub := "Java Edition" + (" + телефоны" if s.opts.get("crossplay", false) and s.edition == "java" else "") if s.edition == "java" else "Bedrock Edition"
	tv.add_child(UI.label(sub, 14, UI.MUTED))
	hr.add_child(tv)
	_state_pill = UI.pill("…", UI.MUTED)
	_state_pill.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	hr.add_child(_state_pill)
	_status = UI.para("", 14, UI.MUTED)
	hv.add_child(_status)
	_bar = ProgressBar.new()
	_bar.custom_minimum_size.y = 10
	_bar.show_percentage = false
	_bar.max_value = 1.0
	hv.add_child(_bar)
	_bar_lbl = UI.label("", 12, UI.DIM)
	hv.add_child(_bar_lbl)
	left.add_child(head)

	var inv := UI.panel(20)
	inv.size_flags_vertical = Control.SIZE_EXPAND_FILL
	var iv := UI.vbox(10)
	inv.add_child(iv)
	iv.add_child(UI.label("КОД ДЛЯ ДРУЗЕЙ", 13, UI.MUTED, UI.font_bold))
	var cr := UI.hbox(14)
	iv.add_child(cr)
	var cv := UI.vbox(8)
	cv.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	cr.add_child(cv)
	_code_lbl = UI.label("······", 46, UI.accent(), UI.font_black)
	cv.add_child(_code_lbl)
	var cb := UI.button("Скопировать код", "secondary", _copy_code)
	cv.add_child(cb)
	_btns["copy"] = cb
	_qr = TextureRect.new()
	_qr.custom_minimum_size = Vector2(150, 150)
	_qr.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	_qr.texture_filter = CanvasItem.TEXTURE_FILTER_NEAREST
	cr.add_child(_qr)
	_code_hint = UI.para("Друг открывает LANTERN → «Зайти к другу» → вводит код. Мир появится у него в игре сам.", 14, UI.MUTED)
	iv.add_child(_code_hint)
	_relay_lbl = UI.label("", 13, UI.DIM)
	iv.add_child(_relay_lbl)
	iv.add_child(UI.spacer(false))
	iv.add_child(UI.label("В ТОЙ ЖЕ WI-FI СЕТИ", 13, UI.MUTED, UI.font_bold))
	_lan_box = UI.vbox(4)
	iv.add_child(_lan_box)
	left.add_child(inv)

	# ================= правая колонка
	var right := UI.vbox(16)
	right.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	add_child(right)
	var pp := UI.panel(18)
	var pv := UI.vbox(10)
	pp.add_child(pv)
	_players_title = UI.h3("Игроки")
	pv.add_child(_players_title)
	_players_box = HFlowContainer.new()
	_players_box.add_theme_constant_override("h_separation", 8)
	_players_box.add_theme_constant_override("v_separation", 8)
	pv.add_child(_players_box)
	right.add_child(pp)

	var lp := UI.panel(14, Color("0c0f15"))
	lp.size_flags_vertical = Control.SIZE_EXPAND_FILL
	var lv := UI.vbox(10)
	lp.add_child(lv)
	var lh := UI.hbox(10)
	lh.add_child(UI.label("ЖУРНАЛ СЕРВЕРА", 13, UI.MUTED, UI.font_bold))
	lv.add_child(lh)
	_log = RichTextLabel.new()
	_log.scroll_following = true
	_log.selection_enabled = true
	_log.size_flags_vertical = Control.SIZE_EXPAND_FILL
	_log.bbcode_enabled = false
	lv.add_child(_log)
	_cmd = UI.line_edit("", "Команда серверу, например: op ВашНик   (Enter — отправить)")
	_cmd.add_theme_font_override("font", UI.font_mono)
	_cmd.add_theme_font_size_override("font_size", 14)
	_cmd.text_submitted.connect(func(t):
		if t.strip_edges() != "":
			app.server.command(t.strip_edges().trim_prefix("/"))
			_cmd.text = "")
	lv.add_child(_cmd)
	right.add_child(lp)

	var br := UI.hbox(10)
	right.add_child(br)
	_btns["stop"] = UI.button("■  Стоп", "danger", _stop)
	_btns["restart"] = UI.button("Перезапуск", "secondary", func(): app.server.restart())
	_btns["start"] = UI.button("▶  Запустить снова", "primary", func(): app.server.host(app.server.edition, app.server.opts))
	_btns["folder"] = UI.button("Папка мира", "secondary", func(): OS.shell_open(ProjectSettings.globalize_path(app.server.world_path())))
	_btns["backup"] = UI.button("Резервная копия", "secondary", _backup)
	_btns["copies"] = UI.button("Копии миров", "ghost", func(): app.go("backups", true))
	_btns["check"] = UI.button("Проверка системы", "secondary", func(): app.go("syscheck", true))
	for k in ["stop", "restart", "start", "folder", "backup", "copies", "check"]:
		_btns[k].size_flags_horizontal = Control.SIZE_EXPAND_FILL
		br.add_child(_btns[k])

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
	l.add_theme_color_override("font_color", (info[1] as Color).lightened(0.25))
	_state_pill.add_theme_stylebox_override("panel", _pill_box(info[1]))
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
		_status.add_theme_color_override("font_color", Color("ffb3ba"))
	else:
		_status.remove_theme_color_override("font_color")
	if st == "running":
		_status.text = "Сервер работает. Зовите друзей!"
	_refresh_lan()


func _pill_box(c: Color) -> StyleBoxFlat:
	var s := UI.box(Color(c, 0.16), 99, 0, Color(c, 0.45), 1)
	s.content_margin_left = 12
	s.content_margin_right = 12
	s.content_margin_top = 4
	s.content_margin_bottom = 4
	return s


func _on_players(list: PackedStringArray) -> void:
	for c in _players_box.get_children():
		c.queue_free()
	_players_title.text = "Игроки онлайн: %d" % list.size()
	if list.is_empty():
		_players_box.add_child(UI.label("Пока никого. Отправьте друзьям код!", 14, UI.DIM))
	for n in list:
		_players_box.add_child(UI.pill("●  " + n, UI.accent()))


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
		row.add_child(UI.label(p[0], 14, UI.MUTED))
		var addr := "%s:%d" % [ips[0], p[1]]
		var al := UI.label(addr, 15, UI.TEXT, UI.font_mono)
		al.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		al.horizontal_alignment = HORIZONTAL_ALIGNMENT_RIGHT
		row.add_child(al)
		var b := UI.button("Копировать", "ghost", func(): UI.copy(addr); app.toast("Адрес скопирован"))
		b.custom_minimum_size.y = 32
		row.add_child(b)
		_lan_box.add_child(row)


func _refresh_relay() -> void:
	var s: ServerHost = app.server
	var r: Dictionary = app.relay_host.stats()
	var code: String = r.get("code", "")
	if not s.opts.get("internet", true):
		_code_lbl.text = "—"
		_code_hint.text = "Интернет-комната выключена: зайти смогут только друзья из вашей Wi-Fi сети."
		_relay_lbl.text = ""
		_qr.texture = null
		_btns.copy.disabled = true
		return
	var shown: bool = code != "" and r.state in ["online", "reconnecting"]
	_btns.copy.disabled = not shown
	if shown:
		_code_lbl.text = RoomCode.pretty(code)
	elif s.state in ["installing", "starting"]:
		_code_lbl.text = "······"
	if code != _last_code:
		_last_code = code
		if code != "":
			var ed := s.edition if not (s.edition == "java" and s.opts.get("crossplay", false)) else "both"
			var q := QR.encode(Invite.link(code, ed, s.opts.get("world", "Мир"), Cfg.relay_url()), QR.ECC_M)
			_qr.texture = ImageTexture.create_from_image(q.to_image(6, Color("0b0e14"), Color("eef1f7")))
		else:
			_qr.texture = null
	match r.state:
		"online":
			var who := "друзей пока нет" if r.peers == 0 else "друзей подключено: %d" % r.peers
			_relay_lbl.text = "● Комната открыта — %s" % who
			_relay_lbl.add_theme_color_override("font_color", UI.JAVA)
		"connecting":
			_relay_lbl.text = "Открываем комнату…"
			_relay_lbl.add_theme_color_override("font_color", UI.DIM)
		"reconnecting", "error":
			_relay_lbl.text = "⚠ " + str(r.error)
			_relay_lbl.add_theme_color_override("font_color", UI.YELLOW)
		_:
			_relay_lbl.text = "Код появится, когда сервер запустится" if s.state in ["installing", "starting"] else ""
			_relay_lbl.add_theme_color_override("font_color", UI.DIM)


func _copy_code() -> void:
	var r: Dictionary = app.relay_host.stats()
	if r.code != "":
		UI.copy(RoomCode.pretty(r.code))
		app.toast("Код %s скопирован — отправьте его друзьям" % RoomCode.pretty(r.code))


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
