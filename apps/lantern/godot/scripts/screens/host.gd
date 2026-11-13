extends BoxContainer
## «Мой мир»: большой код комнаты (копировать, поделиться, QR), живой статус, игроки, журнал, кнопки.
## Два вида: свой сервер (ServerHost) или мир, открытый в самом Minecraft (OwnWorld) — код и друзья одинаковые.

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
var _own := false
var _tips: VBoxContainer
var _own_alive := -1
var _doc_box: VBoxContainer
var _fix_panel: PanelContainer
var _fix_list: VBoxContainer
var _live: PanelContainer        # «Друзья могут зайти прямо сейчас ✓» / «Мир не виден друзьям»
var _live_lbl: Label
var _live_why: Label
var _live_dot: Panel
var _admin: VBoxContainer        # кто в мире: выгнать / забанить, «Кто может зайти», закрытый вход
var _admin_key := ""
var _perf: PanelContainer
var _perf_lbl: Label
var _perf_sub: Label
var _perf_btn: Button
var _net_lbl: Label

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
	_own = app.own.active
	if _own:
		_ready_own()
		return
	var s: ServerHost = app.server
	var compact := UI.compact()
	vertical = compact
	add_theme_constant_override("separation", 16 if compact else 24)
	app.set_title(s.opts.get("world", L.t("Мой мир")), "Java Edition" + (L.t(" + телефоны") if s.opts.get("crossplay", false) else "") if s.edition == "java" else "Bedrock Edition")
	var left := UI.vbox(16)
	left.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	add_child(left)
	var right := left if compact else UI.vbox(16)
	if not compact:
		right.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		add_child(right)

	left.add_child(_live_card())
	# --- состояние сервера
	var head := UI.panel(18)
	var hv := UI.vbox(10)
	head.add_child(hv)
	var hr := UI.hbox(12)
	hv.add_child(hr)
	hr.add_child(Cover.tile(String(s.opts.get("world", L.t("Мир"))), Cfg.game().mode, 52))
	var tv := UI.vbox(0)
	tv.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	var wl := UI.label(s.opts.get("world", L.t("Мир")), 19, UI.TEXT, UI.font_black)
	wl.clip_text = true
	wl.text_overrun_behavior = TextServer.OVERRUN_TRIM_ELLIPSIS
	tv.add_child(wl)
	var sub := "Java Edition" + (L.t(" + телефоны") if s.opts.get("crossplay", false) and s.edition == "java" else "") if s.edition == "java" else "Bedrock Edition"
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
	_doc_box = UI.vbox(8)
	hv.add_child(_doc_box)
	left.add_child(head)

	left.add_child(_make_code_card(compact))
	left.add_child(_perf_card())
	left.add_child(_self_join_card())

	# --- игроки
	var pp := UI.panel(18)
	var pv := UI.vbox(12)
	pp.add_child(pv)
	_players_title = UI.h3(L.t("В мире: 0"))
	pv.add_child(_players_title)
	_players_box = UI.flow(8)
	pv.add_child(_players_box)
	left.add_child(pp)
	left.add_child(_admin_card())
	_fix_panel = UI.panel(18)
	var fv := UI.vbox(10)
	_fix_panel.add_child(fv)
	var fh := UI.hbox(10)
	fh.add_child(UI.icon("check", 20, UI.JAVA))
	fh.add_child(UI.label(L.t("Что LANTERN исправил сам"), 15, UI.TEXT, UI.font_bold))
	fv.add_child(fh)
	_fix_list = UI.vbox(6)
	fv.add_child(_fix_list)
	left.add_child(_fix_panel)

	# --- кнопки управления
	var br := UI.flow(8)
	_btns["stop"] = UI.button(L.t("Остановить"), "danger", _stop, "stop")
	_btns["restart"] = UI.button(L.t("Перезапуск"), "secondary", func(): app.server.restart(), "refresh")
	_btns["start"] = UI.button(L.t("Запустить снова"), "primary", func(): app.server.host(app.server.edition, app.server.opts), "play")
	_btns["copies"] = UI.button(L.t("Копии миров"), "ghost", func(): app.go("backups", true), "cloud")
	_btns["check"] = UI.button(L.t("Проверка системы"), "secondary", func(): app.go("syscheck", true), "info")
	for k in ["stop", "restart", "start", "copies", "check"]:
		br.add_child(_btns[k])
	right.add_child(br)

	# --- та же Wi-Fi сеть
	var lp := UI.panel(18)
	var lv := UI.vbox(8)
	lp.add_child(lv)
	var lh := UI.hbox(10)
	lh.add_child(UI.icon("wifi", 20, UI.MUTED))
	lh.add_child(UI.label(L.t("В той же Wi-Fi сети"), 15, UI.TEXT, UI.font_bold))
	lv.add_child(lh)
	lv.add_child(UI.para(L.t("Друзья рядом могут зайти и без кода — по адресу:"), 13, UI.MUTED))
	_lan_box = UI.vbox(4)
	lv.add_child(_lan_box)
	right.add_child(lp)

	# --- журнал сервера
	var logp := PanelContainer.new()
	logp.add_theme_stylebox_override("panel", UI.box(Color(0.02, 0.03, 0.07, 0.6), UI.R_L, 14, Color(1, 1, 1, 0.08), 1))
	logp.size_flags_vertical = Control.SIZE_EXPAND_FILL
	var lgv := UI.vbox(10)
	logp.add_child(lgv)
	var lgh := UI.button(L.t("Журнал сервера и команды"), "ghost", Callable(), "chevron_down")
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
	_cmd = UI.line_edit("", L.t("Команда серверу, например: op ВашНик"))
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
	s.doctor_changed.connect(_refresh_doctor)
	s.perf_changed.connect(_refresh_perf)
	_refresh_doctor()
	for l in s.history:
		_log.add_text(l + "\n")
	_status.text = s.last_status
	_on_progress(s.last_progress[0], s.last_progress[1])
	_on_state(s.state)
	_on_players(s.players)
	_refresh_relay()
	app.room.changed.connect(_refresh_admin)
	right.add_child(Report.button(app, "host"))
	FX.stagger(left, 0.05)


## 1.8: «Сервер успевает ✓ / не успевает» (замер раз в минуту) и связь с сервером LANTERN.
func _perf_card() -> Control:
	_perf = UI.panel(16)
	var v := UI.vbox(8)
	_perf.add_child(v)
	_perf_lbl = UI.label("", 15, UI.TEXT, UI.font_bold)
	_perf_lbl.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	v.add_child(_perf_lbl)
	_perf_sub = UI.para("", 13, UI.MUTED)
	v.add_child(_perf_sub)
	_perf_btn = UI.button(L.t("Сделать быстрее"), "tonal", _faster, "bolt")
	v.add_child(_perf_btn)
	_net_lbl = UI.label("", 13, UI.MUTED)
	_net_lbl.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	v.add_child(_net_lbl)
	_refresh_perf()
	return _perf


func _refresh_perf() -> void:
	if not is_instance_valid(_perf_lbl):
		return
	var s: ServerHost = app.server
	var ps: Dictionary = s.perf_status()
	var need: Array = s.restart_needed()
	_perf_btn.visible = false
	if "perf" in need:
		_perf_lbl.text = L.t("Режим «Быстро» включится после перезапуска")
		_perf_lbl.add_theme_color_override("font_color", UI.YELLOW)
		_perf_sub.text = L.t("Игроки переподключатся примерно через 30 секунд.")
		_perf_btn.text = L.t("Перезапустить сейчас")
		_perf_btn.visible = true
	elif s.state != "running" or not ps.known:
		_perf_lbl.text = L.t("Скорость игры: «%s»") % L.t(Tuning.name_of(s.perf))
		_perf_lbl.add_theme_color_override("font_color", UI.TEXT)
		_perf_sub.text = L.t("Первый замер — через минуту после запуска.") if s.edition == "java" else ""
	elif ps.ok:
		_perf_lbl.text = L.t("Сервер успевает ✓")
		_perf_lbl.add_theme_color_override("font_color", UI.JAVA.lightened(0.2))
		_perf_sub.text = L.t("Скорость игры: «%s». Игра идёт без задержек.") % L.t(Tuning.name_of(s.perf))
	else:
		_perf_lbl.text = L.t("Сервер не успевает — игра может лагать")
		_perf_lbl.add_theme_color_override("font_color", UI.YELLOW)
		_perf_sub.text = L.t("Закройте лишние программы на этом компьютере.") if s.perf == "fast" else L.t("Включите режим «Быстро»: мир будет видно чуть ближе, зато без рывков.")
		_perf_btn.text = L.t("Сделать быстрее")
		_perf_btn.visible = s.perf != "fast"
	_perf_sub.visible = _perf_sub.text != ""


func _faster() -> void:
	var s: ServerHost = app.server
	if "perf" in s.restart_needed():
		app.toast(L.t("Перезапускаем сервер — мир сохранится"), UI.YELLOW)
		s.restart()
		return
	s.opts["perf"] = "fast"
	s.opts["perf_manual"] = true
	s._save_opts()
	AppLog.add("host", "«Сделать быстрее»: режим «Быстро», нужен перезапуск")
	_refresh_perf()


## Связь с сервером LANTERN (пинг) и прямое соединение по Wi-Fi.
func _refresh_net() -> void:
	if not is_instance_valid(_net_lbl):
		return
	var r: Dictionary = app.relay_host.stats()
	var parts := PackedStringArray()
	var ms := int(r.get("rtt", 0))
	if r.state == "online" and ms > 0:
		parts.append(L.t("Связь с сервером LANTERN: %d мс") % ms)
	if int(r.get("lan_peers", 0)) > 0:
		parts.append(L.t("Прямое соединение по Wi-Fi ✓"))
	_net_lbl.text = " · ".join(parts)
	_net_lbl.visible = not parts.is_empty()


## Большая живая строка: могут ли друзья зайти прямо сейчас.
func _live_card() -> PanelContainer:
	_live = UI.panel(16)
	var r := UI.hbox(12)
	_live.add_child(r)
	_live_dot = Panel.new()
	_live_dot.custom_minimum_size = Vector2(16, 16)
	_live_dot.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	r.add_child(_live_dot)
	var v := UI.vbox(2)
	v.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_live_lbl = UI.label("…", 19, UI.TEXT, UI.font_black)
	_live_lbl.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	v.add_child(_live_lbl)
	_live_why = UI.para("", 13, UI.MUTED)
	v.add_child(_live_why)
	r.add_child(v)
	return _live


func _refresh_live() -> void:
	if _live == null:
		return
	var st: Dictionary = app.room.status()
	var col: Color = UI.JAVA if st.ok else (UI.YELLOW if st.get("wait", false) else UI.RED)
	_live_lbl.text = String(st.text)
	_live_lbl.add_theme_color_override("font_color", col.lightened(0.25))
	_live_dot.add_theme_stylebox_override("panel", UI.box(col, 99))
	_live.add_theme_stylebox_override("panel", UI.glass(2, UI.R_XL, 16, col))
	var why := String(st.why)
	if st.ok:
		why = L.t("Даже пока вы в Minecraft. Не закрывайте игру и LANTERN.") if Cfg.mobile() else L.t("В игре нажмите Esc — и кликните по окошку LANTERN, чтобы увидеть, кто зашёл.")
	_live_why.text = why


## Кто в мире (аккаунт LANTERN и ник в Minecraft), «Выгнать» / «Забанить», «Кто может зайти», закрытый вход.
func _admin_card() -> PanelContainer:
	var p := UI.panel(18)
	_admin = UI.vbox(10)
	p.add_child(_admin)
	_refresh_admin()
	return p


func _refresh_admin() -> void:
	if _admin == null or not is_inside_tree():
		return
	var peers: Array = app.room.peers()
	var key := "%s|%s|%s" % [JSON.stringify(peers), Cfg.get_value("host", "mini", true), Cfg.get_value("host", "notify", true)]
	if key == _admin_key:
		return
	_admin_key = key
	for c in _admin.get_children():
		c.queue_free()
	_admin.add_child(UI.label(L.t("Игроки из LANTERN: %d") % peers.size(), 15, UI.TEXT, UI.font_bold))
	if peers.is_empty():
		_admin.add_child(UI.label(L.t("Пока никто не зашёл"), 14, UI.DIM))
	for pe in peers:
		var r := UI.hbox(8)
		var nm := String(pe.name) + (L.t(" · в Minecraft: ") + String(pe.mc) if String(pe.mc) != "" else "")
		var l := UI.label(nm, 15, UI.TEXT)
		l.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		l.clip_text = true
		l.text_overrun_behavior = TextServer.OVERRUN_TRIM_ELLIPSIS
		r.add_child(l)
		var pid := String(pe.peer)
		r.add_child(UI.button(L.t("Выгнать"), "ghost", func(): app.room.kick(pid)))
		r.add_child(UI.button(L.t("Забанить"), "danger", func(): app.room.ban(pid)))
		_admin.add_child(r)
	_admin.add_child(UI.divider())
	var gs := UI.button(L.t("Настройки игры"), "tonal", func(): app.go("gamesettings", true), "settings")
	gs.custom_minimum_size.y = 52
	_admin.add_child(gs)
	_admin.add_child(UI.para(L.t("Режим, сложность, пароль, правила мира, копии — всё на одной странице.") if not _own else L.t("Название, пароль, кто может зайти и список забаненных."), 12, UI.DIM))
	if not Cfg.mobile() and OS.get_name() != "Android":
		_admin.add_child(UI.divider())
		_admin.add_child(UI.switch_row(L.t("Окошко поверх Minecraft"), L.t("Маленькое окно LANTERN поверх игры: кто зашёл, выгнать, код."), bool(Cfg.get_value("host", "mini", true)), func(b):
			app.room.set_mini(b)).row)
		_admin.add_child(UI.switch_row(L.t("Уведомление, когда друг зашёл"), L.t("Покажем и тогда, когда игра открыта во весь экран."), bool(Cfg.get_value("host", "notify", true)), func(b):
			Cfg.set_value("host", "notify", b)).row)
		_admin.add_child(UI.para(L.t("Окошко не видно поверх игры? Включите в Minecraft оконный режим без рамки (Java: F11 — выключить полный экран)."), 12, UI.DIM))
	elif OS.get_name() == "Android" or Cfg.autotest_args.has("vendor"):
		_admin.add_child(UI.button(L.t("Кнопка не видна в игре?"), "ghost", func(): RoomControl.vendor_sheet(app, true, String(Cfg.autotest_args.get("vendor", ""))), "info"))


## Карточка «Код для друзей»: плитки кода, QR, «Скопировать» и «Поделиться», живой счётчик друзей.
func _make_code_card(compact: bool) -> PanelContainer:
	_code_card = PanelContainer.new()
	var cst := UI.glass(3, UI.R_XL, 0, UI.accent())
	UI._hpad(cst, 20, 20)
	_code_card.add_theme_stylebox_override("panel", cst)
	UI.halo(_code_card, UI.accent(), UI.R_XL, 0.6, true)
	var cv := UI.vbox(14)
	_code_card.add_child(cv)
	var ctop := UI.hbox(8)
	var cap := UI.caption(L.t("Код для друзей"), UI.accent())
	cap.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	ctop.add_child(cap)
	_relay_lbl = UI.label("", 13, UI.DIM, UI.font_bold)
	if compact:
		var qb := UI.icon_button("qr", _qr_sheet, L.t("QR-код"), UI.accent().lightened(0.2), Color(UI.accent(), 0.15))
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
		var tst := UI.box(Color(0.02, 0.03, 0.08, 0.55), UI.R_BTN, 0, Color(UI.accent(), 0.45), 1)
		tst.shadow_color = Color(UI.accent(), 0.18)
		tst.shadow_size = 8
		tile.add_theme_stylebox_override("panel", tst)
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
	var cb := UI.button(L.t("Скопировать код"), "primary", _copy_code, "copy")
	cb.clip_text = true
	cb.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	acts.add_child(cb)
	_btns["copy"] = cb
	var sh := UI.button(L.t("Скопировать приглашение"), "tonal", _share, "share")
	sh.clip_text = true
	sh.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	acts.add_child(sh)
	_btns["share"] = sh
	cv.add_child(acts)
	if compact:
		cv.add_child(_relay_lbl)
	_code_hint = UI.para(L.t("Друг открывает LANTERN → «Зайти к другу» → вводит код. Мир сам появится у него в Minecraft."), 13, UI.MUTED)
	cv.add_child(_code_hint)
	return _code_card


## «Как зайти самому»: хозяин сервера заходит в свой же мир с этого компьютера (без кода).
func _self_join_card() -> Control:
	return self_join_card(app, _own)


## Общая карточка: экран «Мой мир» и «Зайти к другу», если хозяин ввёл код своей же комнаты.
static func self_join_card(app, own: bool) -> Control:
	var p := UI.panel(18)
	var v := UI.vbox(12)
	p.add_child(v)
	var hr := UI.hbox(10)
	hr.add_child(UI.icon("play", 20, UI.accent()))
	hr.add_child(UI.label(L.t("Как зайти самому"), 15, UI.TEXT, UI.font_bold))
	v.add_child(hr)
	if own:
		v.add_child(UI.para(L.t("Вы уже в мире — он открыт у вас в Minecraft. Друзья зайдут по коду."), 14, UI.MUTED))
		return p
	var s: ServerHost = app.server
	if s.edition == "java":
		var vt := UI.para(s.versions_text(), 13, UI.GREEN)
		vt.visible = vt.text != ""
		v.add_child(vt)
		var upd := func(): vt.text = s.versions_text(); vt.visible = vt.text != ""
		s.versions_changed.connect(upd)
		vt.tree_exiting.connect(func(): if s.versions_changed.is_connected(upd): s.versions_changed.disconnect(upd))
		var jp: int = s.java_port()
		var addr := "localhost" if jp == 25565 else "localhost:%d" % jp
		v.add_child(_self_row(app, "java", UI.JAVA, L.t("Minecraft Java на этом компьютере"), L.t("«Сетевая игра» → «Прямое подключение» → адрес:"), addr))
		if s.opts.get("crossplay", false):
			v.add_child(_self_row(app, "phone", UI.BEDROCK, L.t("Bedrock на этом компьютере"), L.t("«Играть» → «Серверы» → «Добавить сервер»: адрес 127.0.0.1, порт %d") % s.bedrock_port(), "127.0.0.1:%d" % s.bedrock_port()))
	else:
		v.add_child(_self_row(app, "phone", UI.BEDROCK, L.t("Minecraft Bedrock на этом компьютере"), L.t("«Играть» → «Друзья» → внизу «LAN-игры» — ваш мир. Нет его там: «Играть» → «Серверы» → «Добавить сервер»: адрес 127.0.0.1, порт %d") % s.bedrock_port(), "127.0.0.1:%d" % s.bedrock_port()))
	return p


static func _self_row(app, icon_name: String, col: Color, title: String, how: String, addr: String) -> Control:
	var r := UI.hbox(12)
	var t := UI.icon_tile(icon_name, col, 36, 18)
	t.size_flags_vertical = Control.SIZE_SHRINK_BEGIN
	r.add_child(t)
	var v := UI.vbox(4)
	v.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	var tl := UI.para(title, 14, UI.TEXT)
	tl.add_theme_font_override("font", UI.font_bold)
	v.add_child(tl)
	v.add_child(UI.para(how, 13, UI.MUTED))
	var ar := HFlowContainer.new()
	ar.add_theme_constant_override("h_separation", 12)
	ar.add_theme_constant_override("v_separation", 6)
	var al := UI.label(addr, 16, UI.TEXT, UI.font_mono)
	al.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	ar.add_child(al)
	var b := UI.button(L.t("Скопировать адрес"), "tonal", func(): UI.copy(addr); app.toast(L.t("Адрес %s скопирован") % addr), "copy")
	ar.add_child(b)
	v.add_child(ar)
	r.add_child(v)
	return r

# ------------------------------------------------------------------ мир из Minecraft (без своего сервера)
func _ready_own() -> void:
	var w: Dictionary = app.own.world
	var bed: bool = w.edition == "bedrock"
	var compact := UI.compact()
	vertical = compact
	add_theme_constant_override("separation", 16 if compact else 24)
	app.set_title(String(w.name), L.t("Мир из Minecraft · ") + ("Bedrock" if bed else "Java"))
	var left := UI.vbox(16)
	left.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	add_child(left)
	var right := left if compact else UI.vbox(16)
	if not compact:
		right.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		add_child(right)
	# --- мир
	var head := UI.panel(18)
	var hv := UI.vbox(10)
	head.add_child(hv)
	var hr := UI.hbox(12)
	hv.add_child(hr)
	hr.add_child(Cover.tile(String(w.name), Cfg.game().mode, 52))
	var tv := UI.vbox(0)
	tv.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	var wl := UI.label(String(w.name), 19, UI.TEXT, UI.font_black)
	wl.clip_text = true
	wl.text_overrun_behavior = TextServer.OVERRUN_TRIM_ELLIPSIS
	tv.add_child(wl)
	var sl := UI.label(("Bedrock" if bed else "Java") + (" " + String(w.version) if String(w.get("version", "")) != "" else "") + L.t(" · мир из Minecraft"), 13, UI.MUTED)
	sl.clip_text = true
	sl.text_overrun_behavior = TextServer.OVERRUN_TRIM_ELLIPSIS
	tv.add_child(sl)
	hr.add_child(tv)
	_state_pill = UI.pill("…", UI.MUTED)
	hr.add_child(_state_pill)
	_status = UI.para("", 14, UI.MUTED)
	hv.add_child(_status)
	left.add_child(_live_card())
	left.add_child(head)
	left.add_child(_make_code_card(compact))
	_net_lbl = UI.label("", 13, UI.MUTED)
	_net_lbl.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	_net_lbl.visible = false
	left.add_child(_net_lbl)
	left.add_child(_self_join_card())
	# --- друзья
	var pp := UI.panel(18)
	var pv := UI.vbox(10)
	pp.add_child(pv)
	_players_title = UI.h3(L.t("Друзей в мире: 0"))
	pv.add_child(_players_title)
	_players_box = UI.flow(8)
	pv.add_child(_players_box)
	left.add_child(pp)
	left.add_child(_admin_card())
	# --- важно знать
	var tp := UI.panel(18)
	_tips = UI.vbox(12)
	tp.add_child(_tips)
	_tips.add_child(UI.label(L.t("Важно знать"), 15, UI.TEXT, UI.font_bold))
	if OS.has_feature("mobile") or Cfg.mobile():
		_tips.add_child(_tip("phone", UI.YELLOW, L.t("Minecraft — на экране, LANTERN не закрывайте"), L.t("Свернуть LANTERN можно, закрывать — нет. Если телефон всё же остановит LANTERN, откройте его снова: комната вернётся с тем же кодом.")))
	if bed:
		_tips.add_child(_tip("phone", UI.BEDROCK, L.t("Зайдут только друзья с Bedrock"), L.t("Телефон, планшет, Windows, консоли. Друзья с Java в этот мир зайти не смогут.")))
	else:
		_tips.add_child(_tip("java", UI.JAVA, L.t("Зайдут друзья с Java"), L.t("Компьютер или ноутбук с Minecraft: Java Edition той же версии.")))
	if not Cfg.mobile():
		_tips.add_child(_tip("server", UI.MUTED, L.t("Мир открыт, пока открыт Minecraft"), L.t("Чтобы друзья играли без вас — запустите «Отдельный сервер».")))
	right.add_child(tp)
	# --- кнопки
	var br := UI.flow(8)
	_btns["close_room"] = UI.button(L.t("Закрыть комнату"), "danger", func():
		app.own.stop()
		app.go("setup", true), "stop")
	_btns["other"] = UI.button(L.t("Другой мир"), "secondary", func():
		app.own.stop()
		app.go("setup", true, {"own": true}), "refresh")
	br.add_child(_btns.close_room)
	br.add_child(_btns.other)
	right.add_child(br)
	app.own.changed.connect(_refresh_own)
	app.room.changed.connect(_refresh_admin)
	_refresh_own()
	_refresh_relay()
	right.add_child(Report.button(app, "own_world"))
	FX.stagger(left, 0.05)


func _tip(icon_name: String, col: Color, title: String, sub: String) -> Control:
	var r := UI.hbox(12)
	var t := UI.icon_tile(icon_name, col, 36, 18)
	t.size_flags_vertical = Control.SIZE_SHRINK_BEGIN
	r.add_child(t)
	var v := UI.vbox(2)
	v.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	var tl := UI.para(title, 15, UI.TEXT)
	tl.add_theme_font_override("font", UI.font_bold)
	v.add_child(tl)
	v.add_child(UI.para(sub, 13, UI.MUTED))
	r.add_child(v)
	return r


func _refresh_own() -> void:
	if not is_inside_tree() or not app.own.active:
		return
	var w: Dictionary = app.own.world
	var ok: bool = app.own.alive()
	var l: Label = _state_pill.get_meta("label")
	var col := UI.JAVA if ok else UI.YELLOW
	l.text = L.t("Открыт") if ok else L.t("Не отвечает")
	l.add_theme_color_override("font_color", col.lightened(0.3))
	_state_pill.add_theme_stylebox_override("panel", UI._hpad(UI.box(Color(col, 0.15), 99), 10, 3))
	if ok:
		_status.text = L.t("Мир открыт. Отправьте друзьям код — они зайдут прямо к вам в игру.")
		_status.add_theme_color_override("font_color", UI.MUTED)
	else:
		_status.text = L.t("Minecraft не отвечает. Вернитесь в игру и проверьте, что мир открыт") + (L.t(" («Видимость для игроков LAN» включена).") if w.edition == "bedrock" else L.t(" («Открыть для сети»)."))
		_status.add_theme_color_override("font_color", UI.YELLOW)
	if ok != (_own_alive == 1):
		_own_alive = 1 if ok else 0
		FX.enter(_status, Vector2(0, 6))
	_update_own_players()


func _update_own_players() -> void:
	var peers := int(app.relay_host.stats().get("peers", 0))
	_players_title.text = L.t("Друзей в мире: %d") % peers
	var n: int = app.own.players()
	var key := "%d|%d" % [peers, n]
	if _players_box.get_meta("key", "") == key:
		return
	_players_box.set_meta("key", key)
	for c in _players_box.get_children():
		c.queue_free()
	if n >= 0:
		_players_box.add_child(UI.pill(L.t("●  в игре сейчас: %d из %d") % [n, int(app.own.world.get("max", 0))], UI.JAVA))
	if peers == 0:
		_players_box.add_child(UI.label(L.t("Пока никого. Отправьте друзьям код!"), 14, UI.DIM))

func _process(d: float) -> void:
	_tick += d
	if _tick > 0.4:
		_tick = 0
		_refresh_relay()
		_refresh_live()
		_refresh_net()
		if _own:
			_refresh_own()


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


var _error_offered := false


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
	_btns.check.visible = st == "error"
	if st == "error":
		_status.text = L.t("Что-то пошло не так: ") + app.server.last_error + L.t("\nНажмите «Запустить» ещё раз. Не помогло — «Сообщить о проблеме» ниже, мы разберёмся.")
		_status.add_theme_color_override("font_color", Color("ff9d9d"))
		if not _error_offered:
			_error_offered = true
			if Cfg.autotest == "":
				(func(): Report.dialog(app, "server_error", L.t("Сервер остановился с ошибкой. Отправьте отчёт — это поможет нам быстро всё починить."))).call_deferred()
	else:
		_status.add_theme_color_override("font_color", UI.MUTED)
	if st == "running":
		_status.text = L.t("Сервер работает. Зовите друзей!")
	_refresh_lan()


## «Доктор»: что требует решения (кнопка в одно нажатие) и что LANTERN уже исправил сам.
func _refresh_doctor() -> void:
	if not is_instance_valid(_doc_box):
		return
	var s: ServerHost = app.server
	for c in _doc_box.get_children():
		c.queue_free()
	for it in s.issues:
		var p := PanelContainer.new()
		p.add_theme_stylebox_override("panel", UI._hpad(UI.box(Color(UI.YELLOW, 0.10), UI.R_M, 0, Color(UI.YELLOW, 0.35), 1), 12, 10))
		var v := UI.vbox(8)
		p.add_child(v)
		v.add_child(UI.para(String(it.text), 14, UI.TEXT))
		var br := UI.flow(8)
		var id := String(it.id)
		if String(it.action) != "":
			br.add_child(UI.button(String(it.button), "tonal", func(): s.resolve(id), "check"))
			br.add_child(UI.button(L.t("Не сейчас"), "ghost", func(): s.dismiss(id)))
		else:
			br.add_child(UI.button(String(it.button), "secondary", func(): s.dismiss(id)))
		v.add_child(br)
		_doc_box.add_child(p)
		FX.enter(p)
	if is_instance_valid(_fix_list):
		for c in _fix_list.get_children():
			c.queue_free()
		for f in s.fixes:
			var r := UI.hbox(8)
			var dot := UI.icon("check", 16, UI.JAVA)
			dot.size_flags_vertical = Control.SIZE_SHRINK_BEGIN
			r.add_child(dot)
			var l := UI.para(String(f), 13, UI.MUTED)
			l.size_flags_horizontal = Control.SIZE_EXPAND_FILL
			r.add_child(l)
			_fix_list.add_child(r)
		_fix_panel.visible = not s.fixes.is_empty()


func _on_players(list: PackedStringArray) -> void:
	for c in _players_box.get_children():
		c.queue_free()
	_players_title.text = L.t("В мире: %d") % list.size()
	if list.is_empty():
		_players_box.add_child(UI.label(L.t("Пока никого. Отправьте друзьям код!"), 14, UI.DIM))
	for n in list:
		var chip := UI.pill("●  " + n, UI.JAVA)
		_players_box.add_child(chip)
		FX.enter(chip, Vector2(0, 8))


func _refresh_lan() -> void:
	for c in _lan_box.get_children():
		c.queue_free()
	var ips := RoomCode.lan_addresses()
	if ips.is_empty():
		_lan_box.add_child(UI.label(L.t("Нет подключения к домашней сети"), 14, UI.DIM))
		return
	var s: ServerHost = app.server
	var ports := []
	if s.edition == "java":
		ports.append(["Java", s.java_port()])
		if s.opts.get("crossplay", false):
			ports.append([L.t("Телефон"), s.bedrock_port()])
	else:
		ports.append(["Bedrock", s.bedrock_port()])
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
		row.add_child(UI.icon_button("copy", func(): UI.copy(addr); app.toast(L.t("Адрес скопирован")), L.t("Копировать"), UI.MUTED))
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
		# код раскрылся: карточка вспыхивает тёплым светом и гаснет до обычного
		var st := _code_card.get_theme_stylebox("panel") as StyleBoxTexture
		if FX.on() and st:
			var base := st.modulate_color
			var tw := _code_card.create_tween()
			tw.tween_property(st, "modulate_color", Color(base.r * 1.7, base.g * 1.6, base.b * 1.4, 1.0), 0.25)
			tw.tween_property(st, "modulate_color", base, 0.8)


func _refresh_relay() -> void:
	var s: ServerHost = app.server
	var r: Dictionary = app.relay_host.stats()
	var code: String = r.get("code", "")
	var starting: bool = not _own and s.state in ["installing", "starting"]
	if not _own and not s.opts.get("internet", true):
		_set_code("······")
		_code_hint.text = L.t("Интернет-комната выключена: зайти смогут только друзья из вашей Wi-Fi сети.")
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
		var num := String(r.get("num", ""))
		var q: String = RoomControl.QUALITY_TEXT.get(String(r.get("quality", "")), "")
		_code_hint.text = (L.t("Номер в списке «Игры»: № %s%s. ") % [num, (" · " + q) if q != "" else ""] if num != "" else "") + L.t("Друг вводит код или номер в «Зайти по коду» — мир сам появится у него в Minecraft.")
	elif starting:
		_set_code("······")
	if code != _last_code:
		_last_code = code
		if code != "":
			QR.encode(_qr_link(), QR.ECC_M).show_in(_qr, 132)
		else:
			_qr.texture = null
	match r.state:
		"online":
			var peers := int(r.peers)
			_relay_lbl.text = L.t("● друзей в мире: %d") % peers
			_relay_lbl.add_theme_color_override("font_color", UI.JAVA)
			if _last_peers >= 0 and peers > _last_peers:
				FX.burst(app, _code_card.get_global_rect().get_center() - app.global_position, 34)
				app.toast(L.t("Друг зашёл в ваш мир!"), UI.JAVA)
			_last_peers = peers
		"connecting":
			_relay_lbl.text = L.t("открываем комнату…")
			_relay_lbl.add_theme_color_override("font_color", UI.DIM)
		"reconnecting", "error":
			_relay_lbl.text = "⚠ " + ((L.t("Мир в Minecraft найден (%s), но связь для друзей не открылась: ") % (L.t("новый Minecraft, NetherNet") if app.own.world.get("transport", "") == "nethernet" else "RakNet")) if _own and app.own.world.get("edition", "") == "bedrock" else "") + str(r.error)
			_relay_lbl.add_theme_color_override("font_color", UI.YELLOW)
		_:
			_relay_lbl.text = L.t("код появится после запуска") if starting else ""
			_relay_lbl.add_theme_color_override("font_color", UI.DIM)


func _world_name() -> String:
	return String(app.own.world.get("name", L.t("Мир"))) if _own else String(app.server.opts.get("world", L.t("Мир")))


## 1.8.1: в QR — ссылка https://…/lantern/j/<код>: камера телефона открывает страницу с кнопкой «Открыть в LANTERN»
## (lantern:// камеры и Google Lens не открывают). Свой relay — как раньше, lantern://.
func _qr_link() -> String:
	var l := _invite_link()
	var p := Invite.parse(l)
	return Invite.web_link(String(p.get("code", "")), String(p.get("edition", "")), String(p.get("name", "")), Cfg.relay_url()) if p.get("ok", false) else l


func _invite_link() -> String:
	if _own:
		return Invite.link(app.relay_host.stats().get("code", ""), String(app.own.world.edition), _world_name(), Cfg.relay_url())
	var s: ServerHost = app.server
	var ed := s.edition if not (s.edition == "java" and s.opts.get("crossplay", false)) else "both"
	return Invite.link(app.relay_host.stats().get("code", ""), ed, s.opts.get("world", L.t("Мир")), Cfg.relay_url())


func _copy_code() -> void:
	var r: Dictionary = app.relay_host.stats()
	if r.code != "":
		UI.copy(RoomCode.pretty(r.code))
		app.toast(L.t("Код %s скопирован — отправьте его друзьям") % RoomCode.pretty(r.code), UI.JAVA)


## Поделиться: готовое сообщение с кодом и ссылкой (вставьте в любой мессенджер).
func _share() -> void:
	var r: Dictionary = app.relay_host.stats()
	if r.code == "":
		return
	UI.copy(app.room.share_text())
	app.toast(L.t("Приглашение со ссылкой скопировано — вставьте его в чат"), UI.JAVA)


func _qr_sheet() -> void:
	var v: VBoxContainer = Overlays.sheet(app, L.t("QR-код комнаты"), L.t("Друг наводит камеру телефона и нажимает «Открыть в LANTERN» — мир появится у него в Minecraft"))
	var t := TextureRect.new()
	QR.encode(_qr_link(), QR.ECC_M).show_in(t, 220)
	t.size_flags_horizontal = Control.SIZE_SHRINK_CENTER
	v.add_child(t)
	var cl := UI.label(_shown_code, 30, UI.accent(), UI.font_black)
	cl.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	v.add_child(cl)


func _stop() -> void:
	app.relay_host.stop()
	app.server.stop()
