extends VBoxContainer
## «Зайти к другу» — мастер: 1) код (6 клеток, вставить, QR) → 2) на чём играете → 3) мир в вашем Minecraft.
## 1.7 «нажал — и ты в игре»: как только мост готов, LANTERN сам открывает Minecraft (Bedrock: ссылка
## minecraft://connect на мост 127.0.0.1) — 3 секунды «Открываем Minecraft…» с «Отмена»; ручные шаги — под «Не открылось?».
## Java (ПК): мир сам появляется в «Сетевой игре», а адрес всегда один — localhost (добавить один раз).

var app
var arg := {}

var _input: LineEdit
var _err: Label
var _plays := "both"
var _result: VBoxContainer
var _recent_box: VBoxContainer
var _tick := 0.0
var _shown_state := ""
var _status_lbl: Label
var _traffic_lbl: Label
var _tiles: Array[PanelContainer] = []
var _tiles_row: HBoxContainer
var _pages: Array[VBoxContainer] = []
var _steps_box: VBoxContainer
var _step := 1
var _next1: Button
var _plays_box: VBoxContainer

const PLAYS := [["java", "java", "Java", "Minecraft из лаунчера на ПК (Java Edition)"], ["bedrock", "phone", "Bedrock", "Minecraft из Microsoft Store на Windows 10/11"], ["both", "globe", "Не знаю", "Подключим оба — мир появится в любой версии"]]
const STEP_NAMES := ["Код", "Ваша игра", "Играем"]


var _password := ""
var _launch := {"t": 3.0, "on": false}     # отсчёт «Открываем Minecraft…»
var _launch_code := ""                      # для какой комнаты уже открывали игру (один раз на вход)
var _launch_done := false
var _manual_open := false                   # раскрыто «Не открылось?»
var _beam: Control
var _mode := "other"


func _ready() -> void:
	add_theme_constant_override("separation", 20)
	if arg is Dictionary:
		_password = String(arg.get("password", ""))
		_mode = String(arg.get("mode", "other"))
	# 1.6: на ПК спрашиваем один раз «На чём вы играете?» и запоминаем навсегда
	_plays = String(Cfg.get_value("app", "play_on", ""))
	if _plays == "":
		_plays = "both"
	if Cfg.mobile():
		_plays = "bedrock"     # на телефоне только Bedrock — не спрашиваем
	_steps_box = UI.vbox(0)
	add_child(_steps_box)
	for i in 3:
		var pv := UI.vbox(16)
		pv.size_flags_vertical = Control.SIZE_EXPAND_FILL
		add_child(pv)
		_pages.append(pv)
	_page_code(_pages[0])
	_page_plays(_pages[1])
	_result = _pages[2]
	var st: Dictionary = app.relay_join.stats()
	if st.state in ["connecting", "online"]:
		_goto(3, false)
		_show_room()
	elif arg is Dictionary and arg.get("input", "") != "":
		_connect()
	else:
		_goto(1, false)


func _goto(n: int, animate := true) -> void:
	var prev := _step
	_step = n
	for c in _steps_box.get_children():
		c.queue_free()
	_steps_box.add_child(UI.steps(n, 3, STEP_NAMES, prev if animate else n))
	for i in 3:
		_pages[i].visible = i == n - 1
	if animate and prev != n:
		FX.enter(_pages[n - 1], Vector2(48 * (1 if n > prev else -1), 0), 0.0, 0.3)
	app.set_title("Зайти к другу")
	app.set_back((func(): _goto(n - 1)) if n == 2 else Callable())
	if n == 1 and is_inside_tree() and not UI.compact():
		_input.grab_focus.call_deferred()
	var sc := get_parent()
	if sc is ScrollContainer:
		(sc as ScrollContainer).scroll_vertical = 0


# ------------------------------------------------------------------ шаг 1: код
func _page_code(p: VBoxContainer) -> void:
	p.add_child(UI.hero("Код от друга", 32))
	p.add_child(UI.para("6 символов или номер комнаты. Ссылку от друга тоже можно вставить сюда.", 15, UI.MUTED))
	var holder := Control.new()
	holder.custom_minimum_size.y = 72
	p.add_child(holder)
	_tiles_row = UI.hbox(8)
	_tiles_row.set_anchors_preset(Control.PRESET_FULL_RECT)
	_tiles_row.alignment = BoxContainer.ALIGNMENT_CENTER
	_tiles_row.mouse_filter = Control.MOUSE_FILTER_IGNORE
	holder.add_child(_tiles_row)
	for i in 6:
		if i == 3:
			var dash := UI.label("–", 24, UI.DIM, UI.font_black)
			dash.size_flags_vertical = Control.SIZE_SHRINK_CENTER
			_tiles_row.add_child(dash)
		var t := PanelContainer.new()
		t.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		t.custom_minimum_size = Vector2(36, 64)
		t.mouse_filter = Control.MOUSE_FILTER_IGNORE
		var l := UI.label("", 28, UI.TEXT, UI.font_black)
		l.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
		l.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
		t.add_child(l)
		_tiles_row.add_child(t)
		_tiles.append(t)
	# настоящее поле ввода — прозрачное поверх клеток (вставка, клавиатура, ссылки)
	_input = UI.line_edit(arg.get("input", "") if arg is Dictionary else "", "")
	_input.set_anchors_preset(Control.PRESET_FULL_RECT)
	_input.max_length = 200
	_input.add_theme_font_override("font", UI.font_black)
	_input.add_theme_font_size_override("font_size", 20)
	_input.text_changed.connect(func(_t): _render_tiles())
	_input.text_submitted.connect(func(_t): _next_from_code())
	_input.focus_entered.connect(_render_tiles)
	_input.focus_exited.connect(_render_tiles)
	holder.add_child(_input)
	_err = UI.para("", 14, Color("ff9d9d"))
	_err.visible = false
	p.add_child(_err)
	var tools := UI.hbox(8)
	var paste := UI.button("Вставить", "secondary", func():
		_input.text = DisplayServer.clipboard_get().strip_edges()
		_render_tiles()
		if Invite.parse(_input.text, "java").ok:
			_next_from_code(), "paste")
	paste.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	tools.add_child(paste)
	var qr := UI.button("QR-код", "secondary", _qr_help, "qr")
	qr.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	tools.add_child(qr)
	p.add_child(tools)
	_next1 = UI.button("Зайти", "primary", _next_from_code, "play")
	_next1.custom_minimum_size.y = 56
	p.add_child(_next1)
	_render_tiles()
	# недавние миры
	p.add_child(UI.section("Недавние"))
	_recent_box = UI.vbox(8)
	p.add_child(_recent_box)
	_fill_recent()


func _norm() -> String:
	return _input.text.strip_edges().to_upper().replace("-", "").replace(" ", "")


## Короткий код — клетки; ссылка или адрес — обычное поле.
func _raw_mode() -> bool:
	var t := _input.text.strip_edges()
	return t.contains(":") or t.contains("/") or t.contains(".") or _norm().length() > 6


func _render_tiles() -> void:
	var raw := _raw_mode()
	_tiles_row.visible = not raw
	var clear := Color(0, 0, 0, 0)
	for k in ["font_color", "caret_color", "selection_color"]:
		if raw:
			_input.remove_theme_color_override(k)
		else:
			_input.add_theme_color_override(k, clear)
	for st in ["normal", "focus", "read_only"]:
		if raw:
			_input.remove_theme_stylebox_override(st)
		else:
			_input.add_theme_stylebox_override(st, StyleBoxEmpty.new())
	_input.placeholder_text = "" if not raw else "Код, ссылка или адрес сервера"
	if raw:
		if _next1:
			_next1.disabled = false
		return
	var s := _norm()
	var focused := _input.has_focus()
	for i in 6:
		var t := _tiles[i]
		var l: Label = t.get_child(0)
		var ch := s[i] if i < s.length() else ""
		var was := l.text
		l.text = ch
		var active := focused and i == mini(s.length(), 5)
		var tst := UI.box(UI.FIELD if ch == "" else Color(UI.accent(), 0.14), UI.R_BTN, 0, UI.accent() if active else (Color(1, 1, 1, 0.14) if ch == "" else Color(UI.accent(), 0.5)), 2 if active else 1)
		if active or ch != "":
			tst.shadow_color = Color(UI.accent(), 0.30 if active else 0.14)
			tst.shadow_size = 10
		t.add_theme_stylebox_override("panel", tst)
		if ch != "" and was != ch and FX.on():
			l.pivot_offset = Vector2(t.size.x / 2, 32)
			l.scale = Vector2(0.6, 0.6)
			l.create_tween().set_trans(Tween.TRANS_BACK).set_ease(Tween.EASE_OUT).tween_property(l, "scale", Vector2.ONE, 0.22)
	if _next1:
		_next1.disabled = s.length() < 6


func _next_from_code() -> void:
	var r := Invite.parse(_input.text, "bedrock" if _plays == "bedrock" else "java")
	if not r.ok:
		_error(r.error)
		if FX.on():
			var tw := _tiles_row.create_tween()
			for x in [10, -8, 6, -4, 0]:
				tw.tween_property(_tiles_row, "position:x", float(x), 0.04)
		return
	_error("")
	if r.kind == "address":
		_connect()
		return
	if String(r.get("edition", "")) != "":
		_plays = "both" if r.edition == "both" else _plays
	# 1.7: ничего не спрашиваем. Телефон — Bedrock; ПК — открываем оба пути сразу (Java и Bedrock),
	# а «На чём вы играете?» осталось только по ссылке «Играю на другом Minecraft — сменить».
	if Cfg.mobile():
		_plays = "bedrock"
	_connect()


func _error(t: String) -> void:
	_err.text = t
	_err.visible = t != ""


func _qr_help() -> void:
	var v: VBoxContainer = Overlays.sheet(app, "Вход по QR-коду", "QR-код друга — это ссылка на его мир")
	v.add_child(app._num_row("1", "Откройте камеру телефона", "Обычную камеру или Google Lens"))
	v.add_child(app._num_row("2", "Наведите на QR друга", "Он на экране «Мой мир» у друга"))
	v.add_child(app._num_row("3", "Нажмите на ссылку", "LANTERN откроется сам и подключится к миру"))
	v.add_child(UI.button("Понятно", "primary", func(): Overlays.close_sheet(app)))


# ------------------------------------------------------------------ шаг 2: на чём играете
func _page_plays(p: VBoxContainer) -> void:
	p.add_child(UI.hero("На чём вы играете?", 30))
	p.add_child(UI.para("Спросим один раз и запомним. Телефоны и консоли — это Bedrock, LANTERN на телефоне выберет его сам.", 15, UI.MUTED))
	_plays_box = UI.vbox(10)
	p.add_child(_plays_box)
	_render_plays()
	var go := UI.button("Подключиться", "primary", func(): Cfg.set_value("app", "play_on", _plays); _connect(), "play")
	go.custom_minimum_size.y = 56
	go.add_theme_font_size_override("font_size", 17)
	p.add_child(go)
	var bk := UI.button("Изменить код", "ghost", func(): _goto(1))
	p.add_child(bk)


func _render_plays() -> void:
	for c in _plays_box.get_children():
		c.queue_free()
	for it in PLAYS:
		var col: Color = {"java": UI.JAVA, "bedrock": UI.BEDROCK, "both": UI.accent()}[it[0]]
		_plays_box.add_child(UI.choice(it[1], col, it[2], it[3], _plays == it[0], func():
			_plays = it[0]
			Cfg.set_value("app", "play_on", _plays)
			_render_plays()))


# ------------------------------------------------------------------ подключение
func _process(d: float) -> void:
	_tick += d
	if _tick < 0.3:
		return
	_tick = 0
	var st: Dictionary = app.relay_join.stats()
	var key := "%s|%s|%s|%s|%s|%s|%s|%s|%s" % [st.state, st.host, st.pong, st.bedrock_error, st.java_error, st.client_protocol, st.get("nethernet", false), st.get("nn_error", ""), st.get("in_game", false)]
	if _status_lbl and is_instance_valid(_status_lbl):
		_traffic_lbl.text = "Передано: ↓ %s  ↑ %s · соединений: %d" % [_kb(st.rx), _kb(st.tx), st.streams]
	if key != _shown_state and _shown_state != "":
		_shown_state = key
		if st.state == "online" and st.name != "":
			_remember(st)
		_show_room()


static func _kb(n: int) -> String:
	return "%.1f МБ" % (n / 1048576.0) if n > 1048576 else "%d КБ" % (n / 1024)


func _clear() -> void:
	for c in _result.get_children():
		c.queue_free()
	_status_lbl = null


func _connect() -> void:
	_error("")
	var r := Invite.parse(_input.text, "bedrock" if _plays == "bedrock" else "java")
	if not r.ok:
		_goto(1)
		_error(r.error)
		return
	_goto(3, _step != 3)
	if r.kind == "address":
		app.relay_join.stop()
		Cfg.add_recent({"kind": "address", "address": r.address, "edition": r.edition, "name": r.get("name", "")})
		_fill_recent()
		_show_address(r)
		return
	# свой же код на компьютере, где идёт этот мир: прокси друга подрался бы с сервером за порты — объясняем, как зайти самому
	# (автотесты java/bedrock/ownworld нарочно заходят к себе на других портах — им можно)
	var hosting: bool = app.own.active or app.server.state in ["installing", "starting", "running", "stopping"]
	if hosting and r.code == String(app.relay_host.stats().get("code", "")) and not Cfg.autotest_args.has("join_bedrock_port"):
		app.relay_join.stop()
		_show_self(app.own.active)
		return
	var relay: String = r.relay if r.relay != "" else Cfg.relay_url()
	var java := _plays in ["java", "both"]
	var bedrock := _plays in ["bedrock", "both"]
	app.relay_join.token = Social.token
	app.relay_join.password = _password
	app.relay_join.nn_port = int(Cfg.autotest_args.get("join_nn_port", NetherNet.PORT))
	# Java: всегда один и тот же порт — 25565 (адрес «localhost»); занят — тот, что был в прошлый раз
	app.relay_join.java_port_alt = int(Cfg.get_value("join", "java_port_last", 0))
	app.relay_join.start(relay, r.code, java, bedrock, int(Cfg.autotest_args.get("join_java_port", 25565)), int(Cfg.autotest_args.get("join_bedrock_port", 19132)))
	Launcher.auto_code = ""          # новое подключение — игру откроем сами (один раз)
	_launch_code = ""
	_launch_done = false
	_manual_open = false
	_launch.on = false
	_shown_state = "-"
	_show_room()


func _show_self(own: bool) -> void:
	_clear()
	_shown_state = ""
	_result.add_child(_hero(_ring("server", UI.accent()), "Это код вашего мира", "Вы хозяин — к себе заходите без кода." if not own else "Вы уже в этом мире.", UI.accent()))
	_result.add_child(preload("res://scripts/screens/host.gd").self_join_card(app, own))
	_result.add_child(UI.button("Открыть «Мой мир»", "secondary", func(): app.go("host"), "server"))
	FX.stagger(_result, 0.04)


func _remember(st: Dictionary) -> void:
	Cfg.add_recent({"kind": "room", "address": app.relay_join.code, "edition": st.edition, "name": st.name})
	# «Продолжить» на главной: последняя игра — одно нажатие
	Cfg.set_value("app", "last_game", {"kind": "join", "code": app.relay_join.code, "name": String(st.name), "edition": String(st.edition),
		"mode": _mode, "num": app.relay_join.code if RoomCode.is_number(app.relay_join.code) else "", "time": Time.get_unix_time_from_system()})
	if int(st.java_port) > 0 and int(st.java_port) != 25565:
		Cfg.set_value("join", "java_port_last", int(st.java_port))
	_fill_recent()


# ------------------------------------------------------------------ шаг 3: результат
func _hero(icon: Control, title: String, sub: String, color: Color) -> Control:
	var v := UI.vbox(10)
	v.alignment = BoxContainer.ALIGNMENT_CENTER
	icon.size_flags_horizontal = Control.SIZE_SHRINK_CENTER
	v.add_child(UI.gap(4))
	v.add_child(icon)
	var t := UI.label(title, 24, UI.TEXT, UI.font_title)
	t.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	t.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	t.custom_minimum_size.x = 120
	v.add_child(t)
	if sub != "":
		var s := UI.para(sub, 15, color)
		s.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
		v.add_child(s)
	return v


func _ring(icon: String, col: Color) -> Control:
	var ring := PanelContainer.new()
	ring.custom_minimum_size = Vector2(84, 84)
	var rst := UI.box(Color(col, 0.16), 99, 0, Color(col, 0.75), 2)
	rst.shadow_color = Color(col, 0.35)
	rst.shadow_size = 18
	ring.add_theme_stylebox_override("panel", rst)
	var ic := UIcon.make(icon, 40, col.lightened(0.15))
	ic.stroke = 2.6
	ic.size_flags_horizontal = Control.SIZE_SHRINK_CENTER
	ic.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	ring.add_child(ic)
	return ring


func _show_room() -> void:
	_clear()
	var st: Dictionary = app.relay_join.stats()
	_shown_state = "%s|%s|%s|%s|%s|%s|%s|%s|%s" % [st.state, st.host, st.pong, st.bedrock_error, st.java_error, st.client_protocol, st.get("nethernet", false), st.get("nn_error", ""), st.get("in_game", false)]
	var code := RoomCode.pretty(app.relay_join.code)
	match st.state:
		"connecting":
			_render_connecting(code)
			return
		"error":
			if String(st.get("error_code", "")) in ["need_password", "bad_password"]:
				# игра с паролем: спросить прямо здесь и зайти снова
				var locked: bool = st.error_code == "need_password"
				_result.add_child(_hero(_ring("lock", UI.accent()), "Игра с паролем" if locked else "Неверный пароль", "Спросите пароль у хозяина игры", UI.MUTED))
				var pw := UI.line_edit("", "Пароль")
				pw.secret = true
				pw.max_length = 32
				_result.add_child(pw)
				var go := func(): _password = pw.text; _connect()
				pw.text_submitted.connect(func(_t): go.call())
				_result.add_child(UI.button("Войти", "primary", go, "play"))
				_result.add_child(UI.button("Назад", "secondary", func(): app.relay_join.stop(); app.go("home", true)))
				pw.grab_focus.call_deferred()
				return
			_result.add_child(_hero(_ring("close", UI.RED), "Не получилось подключиться", _human_error(st), Color("ff9d9d")))
			_result.add_child(UI.button("Попробовать ещё раз", "primary", _connect, "refresh"))
			_result.add_child(UI.button("Изменить код", "secondary", func(): app.relay_join.stop(); _goto(1)))
			if not String(st.get("error_code", "")) in ["not_found", "full", "kicked", "banned_by_host"]:
				_result.add_child(Report.button(app, "join_error"))
			return
		"online":
			pass
		_:
			if st.state == "reconnecting":
				pass
			else:
				_result.add_child(_hero(_ring("wifi", UI.MUTED), "Вы отключились", "Можно зайти снова по тому же коду", UI.MUTED))
				_result.add_child(UI.button("Подключиться снова", "primary", _connect, "refresh"))
				_result.add_child(UI.button("Другой код", "secondary", func(): _goto(1)))
				return
	# --- в мире (1.7: обложка игры, статус и ОДНО действие; LANTERN сам открывает Minecraft)
	var ok: bool = st.host
	var recon: bool = st.state == "reconnecting"
	var in_game: bool = st.get("in_game", false)
	var ed: String = st.edition
	var java_on: bool = st.java_port > 0
	var bed_on: bool = st.bedrock_port > 0 or st.get("nethernet", false)
	var bport: int = st.bedrock_port if st.bedrock_port > 0 else 0
	var status_col := UI.JAVA if ok and in_game and not recon else (UI.YELLOW if recon or not ok else UI.accent())
	var top := PanelContainer.new()
	top.add_theme_stylebox_override("panel", UI.glass(2, UI.R_XL, 0, status_col))
	var tv := UI.vbox(0)
	top.add_child(tv)
	# большая шапка-обложка: пейзаж этой комнаты, название поверх
	var head := Control.new()
	head.custom_minimum_size.y = 150 if UI.compact() else 190
	head.mouse_filter = Control.MOUSE_FILTER_IGNORE
	var cov := Cover.make(app.relay_join.code, _mode, 23, 1.0)
	cov.set_anchors_preset(Control.PRESET_FULL_RECT)
	cov.offset_left = 1
	cov.offset_top = 1
	cov.offset_right = -1
	head.add_child(cov)
	var hv := UI.vbox(2)
	hv.set_anchors_preset(Control.PRESET_BOTTOM_WIDE)
	hv.offset_left = 18
	hv.offset_right = -18
	hv.offset_top = -78
	hv.offset_bottom = -12
	hv.alignment = BoxContainer.ALIGNMENT_END
	hv.mouse_filter = Control.MOUSE_FILTER_IGNORE
	var status_text := "Связь прервалась — восстанавливаем…" if recon else ("Хозяин переподключается…" if not ok else ("Вы в игре" if in_game else "Мост готов"))
	# статус — тёмная «таблетка» в углу обложки: читается на любом пейзаже
	var sp := PanelContainer.new()
	sp.add_theme_stylebox_override("panel", UI._hpad(UI.box(Color(0.03, 0.04, 0.10, 0.62), 99, 0, Color(status_col, 0.5), 1), 10, 4))
	sp.add_child(UI.dot_label(status_text, status_col, 12))
	sp.position = Vector2(14, 14)
	sp.mouse_filter = Control.MOUSE_FILTER_IGNORE
	head.add_child(sp)
	var nm := UI.label(String(st.name) if String(st.name) != "" else "Мир друга", 26, UI.TEXT, UI.font_title)
	nm.clip_text = true
	nm.text_overrun_behavior = TextServer.OVERRUN_TRIM_ELLIPSIS
	hv.add_child(nm)
	head.add_child(hv)
	tv.add_child(head)
	var body := UI.vbox(12)
	var bm := MarginContainer.new()
	for sd in ["left", "right", "bottom"]:
		bm.add_theme_constant_override("margin_" + sd, 16)
	bm.add_theme_constant_override("margin_top", 12)
	bm.add_child(body)
	tv.add_child(bm)
	if recon:
		body.add_child(UI.para("Не выходите из игры: если связь вернётся за полминуты, Minecraft даже не заметит.", 14, UI.MUTED))
	elif not ok:
		body.add_child(UI.para("Сервер друга перезапускается или пропал интернет. Мир вернётся сам — ничего делать не нужно.", 14, UI.MUTED))
	elif in_game:
		body.add_child(UI.para("Комната %s. Не закрывайте LANTERN, пока играете." % code, 14, UI.MUTED))
	# одно главное действие: открыть Minecraft (телефон и Windows-Bedrock — сами, с отсчётом)
	var auto_bedrock: bool = bed_on and (Cfg.mobile() or _plays == "bedrock")
	var primary_used := false
	if ok and not in_game and not recon:
		if auto_bedrock and not _launch_done and Launcher.auto_code != app.relay_join.code and not _launch.on and _can_auto():
			_launch_code = app.relay_join.code
			Launcher.auto_code = app.relay_join.code
			_launch.t = Launcher.COUNTDOWN
			_launch.on = true
		if auto_bedrock and _launch.on:
			body.add_child(Launcher.countdown(_launch, "Открываем Minecraft…", "Игра запустится и сама зайдёт в мир «%s»" % st.name, func(): _fire_launch(bport, String(st.name)), func(): _launch_done = true; _shown_state = "-"; _show_room()))
			primary_used = true
		elif bed_on and not (java_on and _plays == "java"):
			var pc_unknown: bool = java_on and not Cfg.mobile()
			var hint := "Нажмите — Minecraft откроется и сам зайдёт в мир."
			if _launch_done and Launcher.opened > 0:
				hint = "Minecraft открыт — заходим в мир «%s»." % st.name
			elif pc_unknown:
				hint = "Minecraft из Microsoft Store (Bedrock) откроется и сам зайдёт в мир. Играете в Java — смотрите ниже."
			body.add_child(UI.para(hint, 14, UI.MUTED))
			body.add_child(UI.button("Открыть Minecraft" + (" (Bedrock)" if pc_unknown else ""), "primary", func(): _fire_launch(bport, String(st.name)), "play"))
			primary_used = true
	var acts := UI.hbox(8)
	if Social.online():
		var like := UI.button("Нравится", "tonal", func(): app.relay_join.like(); app.toast("Спасибо! Хозяин мира получит ♥", UI.accent()), "heart")
		like.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		acts.add_child(like)
	var dc := _disconnect_btn("Выйти")
	dc.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	acts.add_child(dc)
	body.add_child(acts)
	_result.add_child(top)
	if ok and in_game and FX.on():
		top.create_tween().tween_callback(func(): FX.burst(app, top.get_global_rect().get_center() - Vector2(0, top.size.y * 0.2) - app.global_position, 28))
	if not in_game and ok:
		if java_on:
			_result.add_child(_java_card(String(st.name), int(st.java_port), not primary_used))
		if bed_on:
			# ручные шаги — только если игра не открылась сама
			var mb := UI.button("Не открылось?", "ghost", func(): _manual_open = not _manual_open; _shown_state = "-"; _show_room(), "chevron_down")
			mb.size_flags_horizontal = Control.SIZE_SHRINK_BEGIN
			_result.add_child(mb)
			if _manual_open:
				var b2: int = bport if bport > 0 else 19132
				var addr := "127.0.0.1:%d" % b2
				if ed == "both":
					_result.add_child(_steps_card("Вручную: Minecraft на телефоне, Windows или консоли", UI.BEDROCK, [
						["Играть → «Серверы»", "В самом низу — «%s». Нажмите «Играть»" % st.name, ""],
						["Сервера нет в списке?", "«Добавить сервер»: адрес 127.0.0.1, порт %d" % b2, addr],
					], "bedrock_add", "minecraft://?addExternalServer=%s|%s" % [String(st.name).replace("|", " "), addr]))
				else:
					_result.add_child(_steps_card("Вручную: Minecraft на телефоне, Windows или консоли", UI.BEDROCK, [
						["Играть → «Друзья»", "Внизу, в «Играх по сети рядом», — «%s». Нажмите на него" % st.name, ""],
						["Не видно мира?", "«Серверы» → «Добавить сервер»: адрес 127.0.0.1, порт %d" % b2, addr],
					], ""))
	if bed_on and String(st.host_version) != "":
		_result.add_child(_version_hint(st))
	for e in [st.get("nn_error", ""), st.bedrock_error, st.java_error]:
		if String(e) != "":
			_result.add_child(UI.para(String(e), 14, UI.YELLOW))
	if not java_on and not bed_on:
		var need := "Java (лаунчер на ПК)" if ed == "java" else "Bedrock (Minecraft из Microsoft Store, телефон, консоль)"
		_result.add_child(UI.para("Этот мир для Minecraft %s. Выберите его и подключитесь снова." % need, 15, UI.YELLOW))
		_result.add_child(UI.button("Выбрать игру", "secondary", func(): _goto(2)))
	if Social.online():
		if Social.room_code != app.relay_join.code:
			Social.enter_room(app.relay_join.code)
		_result.add_child(UI.section("Чат комнаты"))
		var cp := UI.panel(12)
		var chat := ChatBox.new().setup("room")
		chat.custom_minimum_size.y = 320 if UI.compact() else 260
		cp.add_child(chat)
		_result.add_child(cp)
	var foot := UI.vbox(2)
	_status_lbl = UI.label("Игра идёт через LANTERN — не закрывайте его, пока играете.", 12, UI.DIM)
	_status_lbl.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	_status_lbl.custom_minimum_size.x = 120
	foot.add_child(_status_lbl)
	_traffic_lbl = UI.label("", 12, UI.DIM)
	foot.add_child(_traffic_lbl)
	_result.add_child(foot)
	if not Cfg.mobile():
		_result.add_child(UI.button("Играю на другом Minecraft — сменить", "ghost", func(): Cfg.set_value("app", "play_on", ""); app.relay_join.stop(); _goto(2)))
	var rb := Report.button(app, "join")
	rb.size_flags_horizontal = Control.SIZE_SHRINK_BEGIN
	_result.add_child(rb)


## «Заходим в игру…»: луч света идёт от «вас» к «хозяину»; когда дошли — вспышка искр (экран «в игре»).
func _render_connecting(code: String) -> void:
	_beam = preload("res://scripts/ui/beam.gd").new()
	_beam.custom_minimum_size.y = 150
	_beam.me = String(Social.me.get("nick", "Вы")) if Social.me is Dictionary and Social.online() else "Вы"
	_result.add_child(UI.gap(8))
	_result.add_child(_beam)
	_result.add_child(_hero(UI.gap(0), "Заходим в игру…", "Комната %s" % code, UI.MUTED))
	_result.add_child(_disconnect_btn("Отмена"))


## Для дизайн-скриншотов: показать экран подключения / отсчёт «Открываем Minecraft…» независимо от сети.
func preview(what: String) -> void:
	if what == "connecting":
		_goto(3, false)
		_clear()
		_shown_state = ""
		_render_connecting(RoomCode.pretty(app.relay_join.code) if app.relay_join.code != "" else "K7M-2QX")
	elif what == "launch":
		_launch_code = app.relay_join.code
		Launcher.auto_code = app.relay_join.code
		_launch_done = false
		_launch.t = 2.4
		_launch.on = true
		_plays = "bedrock"
		_show_room()


## Номера протокола Bedrock → версия игры (для подсказки «у тебя 1.21.110»). Неизвестный номер — без названия.
const BEDROCK_PROTOCOLS := {766: "1.21.50", 776: "1.21.60", 786: "1.21.70", 800: "1.21.80", 818: "1.21.90", 819: "1.21.93",
	827: "1.21.100", 844: "1.21.110", 859: "1.21.120", 860: "1.21.124"}


## Bedrock: телефон, Windows и консоли играют вместе, только если версия Minecraft у всех одинаковая.
## Сервер с Geyser («both») пускает целый диапазон версий — там разница не страшна.
static func version_text(st: Dictionary) -> Array:
	var hv := String(st.get("host_version", ""))
	var hp := int(st.get("host_protocol", 0))
	var cp := int(st.get("client_protocol", 0))
	var mine := String(BEDROCK_PROTOCOLS.get(cp, ""))
	if String(st.get("edition", "")) == "both":
		if cp > 0 and hp > 0 and cp > hp:
			return ["bad", "У вас Minecraft %sновее, чем знает сервер друга (до %s). Пусть хозяин перезапустит сервер в LANTERN — Geyser обновится сам." % [mine + " " if mine != "" else "", hv]]
		return ["ok", "Сервер друга пускает телефоны и консоли с актуальной версией Minecraft (до %s)." % hv]
	if cp > 0 and hp > 0 and cp != hp:
		var yours := ("у вас " + mine) if mine != "" else "у вас другая версия"
		if cp < hp:
			return ["bad", "У хозяина %s, %s — обновите Minecraft. Играть вместе можно только на одной версии." % [hv, yours]]
		return ["bad", "У хозяина %s, %s — пусть хозяин обновит Minecraft. Играть вместе можно только на одной версии." % [hv, yours]]
	return ["ok", "Мир на версии %s — у всех должна быть такая же (телефон, Windows и консоли играют вместе)." % hv]


func _version_hint(st: Dictionary) -> Control:
	var r := version_text(st)
	var bad: bool = r[0] == "bad"
	var p := UI.panel(14, Color(UI.YELLOW, 0.12) if bad else Color(1, 1, 1, 0.03))
	var row := UI.hbox(10)
	p.add_child(row)
	var ic := UI.icon("info", 20, UI.YELLOW if bad else UI.MUTED)
	ic.size_flags_vertical = Control.SIZE_SHRINK_BEGIN
	row.add_child(ic)
	var l := UI.para(r[1], 14, UI.YELLOW if bad else UI.MUTED)
	l.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	row.add_child(l)
	return p


func _disconnect_btn(t: String) -> Button:
	return UI.button(t, "danger" if t == "Выйти" else "secondary", func():
		app.relay_join.stop()
		_shown_state = "-"
		_show_room()
		Store.show_interstitial(app))


## Шаги «где найти мир» с маленькой «кнопкой из игры» вместо скриншота.
func _edition_block(title: String, color: Color, steps: Array, manual: String, addr: String, deeplink: String) -> Control:
	var p := UI.panel(18)
	var v := UI.vbox(14)
	p.add_child(v)
	var hr := UI.hbox(10)
	hr.add_child(UI.icon_tile("java" if color == UI.JAVA else "phone", color, 36, 18))
	var tl := UI.label(title, 16, UI.TEXT, UI.font_bold)
	tl.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	tl.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	tl.custom_minimum_size.x = 120
	tl.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	hr.add_child(tl)
	v.add_child(hr)
	for i in steps.size():
		var s: Array = steps[i]
		var r := UI.hbox(12)
		var num := PanelContainer.new()
		num.custom_minimum_size = Vector2(28, 28)
		num.size_flags_vertical = Control.SIZE_SHRINK_BEGIN
		num.add_theme_stylebox_override("panel", UI.box(Color(color, 0.18), 99))
		var nl := UI.label(str(i + 1), 14, color.lightened(0.2), UI.font_black)
		nl.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
		nl.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
		num.add_child(nl)
		r.add_child(num)
		var sv := UI.vbox(4)
		sv.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		var t1 := UI.para(s[0], 15, UI.TEXT)
		t1.add_theme_font_override("font", UI.font_bold)
		sv.add_child(t1)
		if s[1] != "":
			sv.add_child(UI.para(s[1], 13, UI.MUTED))
		if s[2] != "":
			sv.add_child(_game_button(s[2]))
		r.add_child(sv)
		v.add_child(r)
	v.add_child(UI.divider())
	var mr := UI.hbox(8)
	var mv := UI.vbox(0)
	mv.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	mv.add_child(UI.para(manual, 12, UI.MUTED))
	mr.add_child(mv)
	if addr != "":
		mv.add_child(UI.label(addr, 14, UI.TEXT, UI.font_mono))
		mr.add_child(UI.icon_button("copy", func(): UI.copy(addr); app.toast("Скопировано: " + addr), "Копировать", UI.MUTED))
	v.add_child(mr)
	if deeplink != "":
		v.add_child(UI.button("Открыть в Minecraft", "tonal", func(): OS.shell_open(deeplink), "play"))
	return p


## Подсказка «какую кнопку искать в игре»: плашка в стиле LANTERN с названием кнопки (не копия интерфейса игры).
func _game_button(text: String) -> Control:
	var b := PanelContainer.new()
	b.add_theme_stylebox_override("panel", UI._hpad(UI.box(Color(1, 1, 1, 0.07), 10, 0, Color(UI.accent(), 0.6), 1), 14, 6))
	b.size_flags_horizontal = Control.SIZE_SHRINK_BEGIN
	b.add_child(UI.label(text, 14, UI.TEXT, UI.font_bold))
	return b


func _show_address(r: Dictionary) -> void:
	_clear()
	_result.add_child(_hero(_ring("server", UI.accent()), "Сервер %s" % r.address, "", UI.MUTED))
	if r.edition == "bedrock":
		_result.add_child(UI.para("Нажмите — Minecraft откроется и сразу зайдёт на сервер.", 15, UI.MUTED))
		_result.add_child(UI.button("Открыть в Minecraft", "primary", func():
			var url := "minecraft://connect/?serverUrl=%s&serverPort=%d" % [String(r.host), int(r.port)]
			AppLog.add("launch", "сервер по адресу: " + url)
			if Cfg.autotest == "" and OS.shell_open(url) != OK:
				OwnWorld.open_minecraft(), "play"))
	else:
		_result.add_child(_edition_block("Java Edition", UI.JAVA, [
			["Откройте Minecraft → «Сетевая игра»", "", "Добавить сервер"],
			["Вставьте адрес в поле «Адрес сервера»", "Он уже скопирован", ""],
			["Выберите сервер в списке", "и нажмите «Подключиться»", ""],
		], "Адрес", r.address, ""))
		UI.copy(r.address)
		app.toast("Адрес скопирован — вставьте его в Minecraft")
	_result.add_child(UI.button("Другой код", "secondary", func(): _goto(1)))


func _fill_recent() -> void:
	for c in _recent_box.get_children():
		c.queue_free()
	var list: Array = Cfg.recent()
	if list.is_empty():
		_recent_box.add_child(UI.para("Здесь будут миры, в которые вы заходили.", 14, UI.DIM))
		return
	for i in list.size():
		if i >= 5:
			break
		var e: Dictionary = list[i]
		var nm: String = e.get("name", "")
		var room: bool = e.get("kind") == "room"
		var code: String = RoomCode.pretty(e.address) if room else e.address
		var ed: String = {"java": "Java", "bedrock": "Bedrock", "both": "Java + Bedrock"}.get(e.get("edition", ""), "")
		var trail := UI.hbox(0)
		var b := UI.button("Зайти", "secondary", func(): _input.text = code; _render_tiles(); _connect())
		trail.add_child(b)
		trail.add_child(UI.icon_button("close", func(): Cfg.remove_recent(i); _fill_recent(), "Убрать из списка", UI.DIM))
		var col := UI.BEDROCK if e.get("edition", "") == "bedrock" else UI.JAVA
		_recent_box.add_child(UI.list_row(UI.icon_tile("phone" if e.get("edition", "") == "bedrock" else "java", col, 40, 20), nm if nm != "" else code, ("код " + code if room else code) + (" · " + ed if ed != "" else ""), trail))


## Ошибка входа простыми словами: что случилось + что сделать.
static func _human_error(st: Dictionary) -> String:
	match String(st.get("error_code", "")):
		"unreachable":
			return "Нет связи с LANTERN. Проверьте интернет (Wi-Fi или мобильные данные) и нажмите «Попробовать ещё раз»."
		"lost":
			return "Интернет пропал надолго. Когда он вернётся — нажмите «Попробовать ещё раз», мир друга на месте."
	var e := String(st.get("error", ""))
	if e.begins_with("связь с сервером-посредником"):
		return "Связь с LANTERN оборвалась. Проверьте интернет и нажмите «Попробовать ещё раз»."
	if e.begins_with("сервер-посредник недоступен"):
		return "Нет связи с LANTERN. Проверьте интернет и нажмите «Попробовать ещё раз»."
	return e


## Карточка «как зайти»: 2–3 больших пронумерованных шага и большая кнопка «Открыть Minecraft».
func _steps_card(title: String, color: Color, steps: Array, open_kind: String, deeplink := "") -> Control:
	var p := UI.panel(18)
	var v := UI.vbox(14)
	p.add_child(v)
	var stl := UI.para(title, 15, color.lightened(0.3))
	stl.add_theme_font_override("font", UI.font_bold)
	v.add_child(stl)
	var i := 0
	for s in steps:
		i += 1
		var row: Control = app._num_row(str(i), s[0], s[1])
		v.add_child(row)
		if String(s[2]) != "":
			var ar := UI.hbox(8)
			var al := UI.label(String(s[2]), 18, UI.TEXT, UI.font_bold)
			al.size_flags_horizontal = Control.SIZE_EXPAND_FILL
			ar.add_child(UI.gap(36))
			ar.add_child(al)
			var addr := String(s[2])
			ar.add_child(UI.button("Копировать", "tonal", func(): UI.copy(addr); app.toast("Скопировано: " + addr), "copy"))
			v.add_child(ar)
	if open_kind == "bedrock_add":
		v.add_child(UI.button("Добавить сервер в Minecraft", "secondary", func():
			AppLog.add("join", "кнопка «Добавить сервер в Minecraft»")
			if Cfg.autotest == "" and OS.shell_open(deeplink) != OK:
				OwnWorld.open_minecraft(), "plus"))
	return p


## Можно ли открывать игру самим: не в автотестах (кроме --autoopen=1) и только когда LANTERN на экране.
func _can_auto() -> bool:
	if Cfg.autotest != "" and not Cfg.autotest_args.has("autoopen"):
		return false
	return is_visible_in_tree()


func _fire_launch(bport: int, world_name: String) -> void:
	_launch.on = false
	_launch_done = true
	app._join_keepalive()     # Android: служба с уведомлением — до ухода в Minecraft, чтобы мост не заморозили
	Launcher.open_bedrock(bport, "вход в мир «%s»" % world_name)
	FX.haptic(18)
	_shown_state = "-"
	_show_room.call_deferred()


## Java (ПК): одна строка с «картинкой» пути и адресом, который не меняется.
func _java_card(world_name: String, port: int, lead: bool) -> Control:
	var addr := Launcher.java_address(port)
	var p := UI.glass_panel(1, 16, UI.JAVA if lead else Color(0, 0, 0, 0))
	var v := UI.vbox(12)
	p.add_child(v)
	v.add_child(UI.dot_label("Minecraft Java (ПК)", UI.JAVA, 13))
	# «картинка»: путь в меню игры — три плашки со стрелками
	var path := UI.flow(6)
	var steps := ["Minecraft", "Сетевая игра", world_name]
	for i in steps.size():
		var chip := PanelContainer.new()
		var last := i == steps.size() - 1
		chip.add_theme_stylebox_override("panel", UI._hpad(UI.box(Color(UI.JAVA, 0.18) if last else Color(1, 1, 1, 0.07), 10, 0, Color(UI.JAVA, 0.6) if last else Color(1, 1, 1, 0.14), 1), 12, 7))
		var txt := String(steps[i])
		var cl := UI.label(txt if txt.length() <= 22 else txt.substr(0, 21) + "…", 14, UI.TEXT if last else UI.MUTED, UI.font_bold)
		chip.add_child(cl)
		path.add_child(chip)
		if not last:
			var ar := UI.icon("chevron", 16, UI.DIM)
			ar.size_flags_vertical = Control.SIZE_SHRINK_CENTER
			path.add_child(ar)
	v.add_child(path)
	v.add_child(UI.para("Мир уже в списке «Сетевая игра» — выберите его и нажмите «Подключиться».", 14, UI.MUTED))
	var ar2 := UI.flow(10)
	ar2.add_child(UI.button("Скопировать адрес", "tonal", func(): UI.copy(addr); app.toast("Скопировано: " + addr), "copy"))
	var al := UI.label(addr, 17, UI.TEXT, UI.font_mono)
	al.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	ar2.add_child(al)
	v.add_child(ar2)
	v.add_child(UI.para("Добавьте сервер %s один раз — дальше он всегда ведёт в игру, к которой вы подключились в LANTERN." % addr, 13, UI.DIM))
	return p
