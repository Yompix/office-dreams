extends HBoxContainer
## Зайти к другу: по коду комнаты мир появляется в списке игры сам; по адресу — ссылка или инструкция.

var app
var arg := {}

var _input: LineEdit
var _err: Label
var _plays := "both"
var _seg := {}
var _result: VBoxContainer
var _recent_box: VBoxContainer
var _tick := 0.0
var _shown_state := ""
var _status_lbl: Label
var _traffic_lbl: Label

const PLAYS := [["java", "Java"], ["bedrock", "Bedrock"], ["both", "Оба"]]


func _ready() -> void:
	add_theme_constant_override("separation", 20)
	_plays = arg.get("edition", Cfg.get_value("join", "plays", "both"))
	var left := UI.vbox(16)
	left.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	left.size_flags_stretch_ratio = 1.7
	add_child(left)
	var head := UI.vbox(2)
	head.add_child(UI.h1("Зайти к другу"))
	head.add_child(UI.para("Введите код, который прислал друг. Мир сам появится в Minecraft — как будто вы в одной Wi-Fi сети.", 16, UI.MUTED))
	left.add_child(head)
	var p := UI.panel(22)
	var pv := UI.vbox(14)
	p.add_child(pv)
	var row := UI.hbox(12)
	_input = UI.line_edit(arg.get("input", ""), "Код, например K7M-2QX")
	_input.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_input.custom_minimum_size.y = 60
	_input.add_theme_font_override("font", UI.font_black)
	_input.add_theme_font_size_override("font_size", 24)
	_input.text_submitted.connect(func(_t): _connect())
	row.add_child(_input)
	var paste := UI.button("Вставить", "secondary", func(): _input.text = DisplayServer.clipboard_get().strip_edges())
	paste.custom_minimum_size.y = 60
	row.add_child(paste)
	var go := UI.button("Подключиться", "primary", _connect)
	go.custom_minimum_size = Vector2(190, 60)
	row.add_child(go)
	pv.add_child(row)
	_err = UI.label("", 14, Color("ffb3ba"))
	_err.visible = false
	pv.add_child(_err)
	var sr := UI.hbox(8)
	sr.add_child(UI.label("Я играю на:", 15, UI.MUTED))
	for it in PLAYS:
		var b := UI.button(it[1], "secondary", func(): _plays = it[0]; Cfg.set_value("join", "plays", _plays); _update_seg())
		b.custom_minimum_size = Vector2(110, 38)
		_seg[it[0]] = b
		sr.add_child(b)
	pv.add_child(sr)
	_update_seg()
	left.add_child(p)
	_result = UI.vbox(14)
	_result.size_flags_vertical = Control.SIZE_EXPAND_FILL
	left.add_child(_result)

	var right := UI.panel(20)
	right.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	add_child(right)
	var rv := UI.vbox(12)
	right.add_child(rv)
	rv.add_child(UI.h3("Недавние"))
	var sc := ScrollContainer.new()
	sc.size_flags_vertical = Control.SIZE_EXPAND_FILL
	sc.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	rv.add_child(sc)
	_recent_box = UI.vbox(8)
	_recent_box.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	sc.add_child(_recent_box)
	_fill_recent()
	var st: Dictionary = app.relay_join.stats()
	if st.state in ["connecting", "online"]:
		_show_room()
	elif arg.get("input", "") != "":
		_connect()
	else:
		_show_help()


func _process(d: float) -> void:
	_tick += d
	if _tick < 0.3:
		return
	_tick = 0
	var st: Dictionary = app.relay_join.stats()
	var key := "%s|%s|%s|%s|%s" % [st.state, st.host, st.pong, st.bedrock_error, st.java_error]
	if _status_lbl and is_instance_valid(_status_lbl):
		_traffic_lbl.text = "Передано: ↓ %s  ↑ %s  · соединений: %d" % [_kb(st.rx), _kb(st.tx), st.streams]
	if key != _shown_state and _shown_state != "":
		_shown_state = key
		if st.state == "online" and st.name != "":
			_remember(st)
		_show_room()


static func _kb(n: int) -> String:
	return "%.1f МБ" % (n / 1048576.0) if n > 1048576 else "%d КБ" % (n / 1024)


func _update_seg() -> void:
	for k in _seg:
		var on: bool = k == _plays
		var b: Button = _seg[k]
		b.add_theme_stylebox_override("normal", UI.box(Color(UI.accent(), 0.18) if on else UI.CARD_HI, 10, 10, UI.accent() if on else Color(0, 0, 0, 0), 1 if on else 0))
		b.add_theme_color_override("font_color", UI.TEXT if on else UI.MUTED)


func _clear() -> void:
	for c in _result.get_children():
		c.queue_free()
	_status_lbl = null


func _error(t: String) -> void:
	_err.text = t
	_err.visible = t != ""


func _connect() -> void:
	_error("")
	var r := Invite.parse(_input.text, "bedrock" if _plays == "bedrock" else "java")
	if not r.ok:
		_error(r.error)
		return
	if r.kind == "address":
		app.relay_join.stop()
		Cfg.add_recent({"kind": "address", "address": r.address, "edition": r.edition, "name": r.get("name", "")})
		_fill_recent()
		_show_address(r)
		return
	var relay: String = r.relay if r.relay != "" else Cfg.relay_url()
	var java := _plays in ["java", "both"]
	var bedrock := _plays in ["bedrock", "both"]
	app.relay_join.token = Social.token
	app.relay_join.start(relay, r.code, java, bedrock, int(Cfg.autotest_args.get("join_java_port", 25565)), int(Cfg.autotest_args.get("join_bedrock_port", 19132)))
	_shown_state = "-"
	_show_room()


func _remember(st: Dictionary) -> void:
	Cfg.add_recent({"kind": "room", "address": app.relay_join.code, "edition": st.edition, "name": st.name})
	_fill_recent()


# ------------------------------------------------------------------ экраны результата

func _show_help() -> void:
	_clear()
	var p := UI.panel(22)
	var v := UI.vbox(12)
	p.add_child(v)
	v.add_child(UI.h3("Как это работает"))
	v.add_child(_step(1, "Друг создаёт мир в LANTERN", "и присылает вам код из 6 символов"))
	v.add_child(_step(2, "Вы вводите код здесь", "приложение подключится к миру друга"))
	v.add_child(_step(3, "Открываете Minecraft", "Java: «Сетевая игра» · Bedrock: «Играть» → «Друзья». Мир уже в списке!"))
	_result.add_child(p)


func _step(n: int, title: String, text: String) -> Control:
	var row := UI.hbox(14)
	var num := PanelContainer.new()
	num.add_theme_stylebox_override("panel", UI.box(Color(UI.accent(), 0.18), 99, 0))
	num.custom_minimum_size = Vector2(38, 38)
	var nl := UI.label(str(n), 17, UI.accent(), UI.font_black)
	nl.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	nl.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
	num.add_child(nl)
	row.add_child(num)
	var v := UI.vbox(0)
	v.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	v.add_child(UI.label(title, 16, UI.TEXT, UI.font_bold))
	v.add_child(UI.para(text, 14, UI.MUTED))
	row.add_child(v)
	return row


func _show_room() -> void:
	_clear()
	var st: Dictionary = app.relay_join.stats()
	_shown_state = "%s|%s|%s|%s|%s" % [st.state, st.host, st.pong, st.bedrock_error, st.java_error]
	var p := UI.panel(22)
	p.size_flags_vertical = Control.SIZE_EXPAND_FILL
	var v := UI.vbox(12)
	p.add_child(v)
	_result.add_child(p)
	var top := UI.hbox(12)
	v.add_child(top)
	var title := ""
	var color := UI.YELLOW
	match st.state:
		"connecting":
			title = "Подключаемся к комнате %s…" % RoomCode.pretty(app.relay_join.code)
		"online":
			title = "Мир «%s» уже в вашей игре!" % st.name if st.host else "Друг временно отключился…"
			color = UI.JAVA if st.host else UI.YELLOW
		"error":
			title = "Не получилось подключиться"
			color = UI.RED
		_:
			title = "Отключено"
			color = UI.MUTED
	var tl := UI.h2(title)
	tl.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	tl.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	top.add_child(tl)
	if st.state == "online" and Social.online():
		top.add_child(UI.button("♥ Нравится", "ghost", func(): app.relay_join.like()))
	if st.state in ["connecting", "online"]:
		top.add_child(UI.button("Отключиться", "danger", func():
			app.relay_join.stop()
			_shown_state = "-"
			_show_room()
			Store.show_interstitial(app)))
	if st.state == "error":
		v.add_child(UI.para(st.error, 15, Color("ffb3ba")))
		v.add_child(UI.button("Попробовать ещё раз", "primary", _connect))
		return
	if st.state != "online":
		if st.state == "connecting":
			var bar := ProgressBar.new()
			bar.custom_minimum_size.y = 8
			bar.show_percentage = false
			bar.indeterminate = true
			v.add_child(bar)
		return
	if not st.host:
		v.add_child(UI.para("Сервер друга перезапускается или пропал интернет. Как только он вернётся, мир снова появится — ничего делать не нужно.", 15, UI.MUTED))
	var ed: String = st.edition
	var java_on: bool = st.java_port > 0
	var bed_on: bool = st.bedrock_port > 0
	if java_on:
		v.add_child(_edition_block("Java Edition", UI.JAVA, [
			"Откройте Minecraft Java → «Сетевая игра»",
			"Внизу списка появится «%s — LANTERN»" % st.name,
			"Выберите его и нажмите «Подключиться»",
		], "Если не видно: «Добавить сервер» → адрес", "127.0.0.1:%d" % st.java_port, ""))
	if bed_on:
		var bed_hint := "сервер друга отвечает" if st.pong else "ждём ответ сервера друга…"
		v.add_child(_edition_block("Bedrock Edition — " + bed_hint, UI.BEDROCK, [
			"Откройте Minecraft → «Играть» → вкладка «Друзья»",
			"В разделе «Игры по локальной сети» появится мир друга",
			"Нажмите на него — и вы в игре",
		], "Если не видно: «Серверы» → «Добавить сервер»", "127.0.0.1:%d" % st.bedrock_port, "minecraft://?addExternalServer=%s|127.0.0.1:%d" % [String(st.name).replace("|", " "), st.bedrock_port]))
	if st.bedrock_error != "":
		v.add_child(UI.para("Bedrock: " + st.bedrock_error, 14, UI.YELLOW))
	if st.java_error != "":
		v.add_child(UI.para("Java: " + st.java_error, 14, UI.YELLOW))
	if not java_on and not bed_on:
		var need := "Java" if ed == "java" else "Bedrock"
		v.add_child(UI.para("Этот мир для %s. Переключите «Я играю на» и подключитесь снова." % need, 15, UI.YELLOW))
	if Social.online():
		if Social.room_code != app.relay_join.code:
			Social.enter_room(app.relay_join.code)
		var chat := ChatBox.new().setup("room")
		chat.custom_minimum_size.y = 150
		chat.size_flags_vertical = Control.SIZE_EXPAND_FILL
		v.add_child(UI.label("ЧАТ КОМНАТЫ", 12, UI.MUTED, UI.font_bold))
		v.add_child(chat)
	else:
		v.add_child(UI.spacer(false))
	_status_lbl = UI.label("Не закрывайте приложение, пока играете.", 13, UI.DIM)
	v.add_child(_status_lbl)
	_traffic_lbl = UI.label("", 13, UI.DIM)
	v.add_child(_traffic_lbl)


func _edition_block(title: String, color: Color, steps: Array, manual: String, addr: String, deeplink: String) -> Control:
	var p := UI.panel(16, Color(color, 0.07), 14)
	var v := UI.vbox(8)
	p.add_child(v)
	v.add_child(UI.label(title, 16, color.lightened(0.2), UI.font_bold))
	for i in steps.size():
		var r := UI.hbox(10)
		r.add_child(UI.label("%d." % (i + 1), 15, color, UI.font_black))
		var l := UI.para(steps[i], 15, UI.TEXT)
		l.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		r.add_child(l)
		v.add_child(r)
	var mr := UI.hbox(8)
	mr.add_child(UI.label(manual + ":", 13, UI.MUTED))
	mr.add_child(UI.label(addr, 14, UI.TEXT, UI.font_mono))
	mr.add_child(UI.spacer())
	var cb := UI.button("Копировать", "ghost", func(): UI.copy(addr); app.toast("Скопировано: " + addr))
	cb.custom_minimum_size.y = 32
	mr.add_child(cb)
	if deeplink != "":
		var ob := UI.button("Открыть в Minecraft", "secondary", func(): OS.shell_open(deeplink))
		ob.custom_minimum_size.y = 34
		mr.add_child(ob)
	v.add_child(mr)
	return p


func _show_address(r: Dictionary) -> void:
	_clear()
	var p := UI.panel(22)
	var v := UI.vbox(12)
	p.add_child(v)
	_result.add_child(p)
	v.add_child(UI.h2("Сервер %s" % r.address))
	if r.edition == "bedrock":
		v.add_child(UI.para("Нажмите кнопку — Minecraft откроется и сам добавит сервер в список «Серверы».", 15, UI.MUTED))
		var name: String = r.name if r.name != "" else "Сервер друга"
		v.add_child(UI.button("Открыть в Minecraft", "primary", func(): OS.shell_open("minecraft://?addExternalServer=%s|%s" % [name.replace("|", " "), r.address])))
	else:
		v.add_child(_edition_block("Java Edition", UI.JAVA, [
			"Откройте Minecraft → «Сетевая игра» → «Добавить сервер»",
			"Вставьте адрес в поле «Адрес сервера» и нажмите «Готово»",
			"Выберите сервер в списке и нажмите «Подключиться»",
		], "Адрес", r.address, ""))
		UI.copy(r.address)
		app.toast("Адрес скопирован — вставьте его в Minecraft")


func _fill_recent() -> void:
	for c in _recent_box.get_children():
		c.queue_free()
	var list: Array = Cfg.recent()
	if list.is_empty():
		_recent_box.add_child(UI.para("Здесь будут миры, в которые вы заходили.", 14, UI.DIM))
		return
	for i in list.size():
		var e: Dictionary = list[i]
		var card := UI.panel(12, UI.CARD, 12)
		var row := UI.hbox(10)
		card.add_child(row)
		var v := UI.vbox(0)
		v.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		var nm: String = e.get("name", "")
		var t := UI.label(nm if nm != "" else (RoomCode.pretty(e.address) if e.get("kind") == "room" else e.address), 15, UI.TEXT, UI.font_bold)
		t.clip_text = true
		t.text_overrun_behavior = TextServer.OVERRUN_TRIM_ELLIPSIS
		v.add_child(t)
		var ed := {"java": "Java", "bedrock": "Bedrock", "both": "Java + Bedrock"}.get(e.get("edition", ""), "")
		var sub: String = ("код " + RoomCode.pretty(e.address)) if e.get("kind") == "room" else e.address
		v.add_child(UI.label("%s · %s" % [sub, ed], 12, UI.MUTED))
		row.add_child(v)
		var b := UI.button("Зайти", "secondary", func(): _input.text = RoomCode.pretty(e.address) if e.get("kind") == "room" else e.address; _connect())
		b.custom_minimum_size = Vector2(80, 36)
		row.add_child(b)
		var x := UI.button("×", "ghost", func(): Cfg.remove_recent(i); _fill_recent())
		x.custom_minimum_size = Vector2(32, 36)
		x.tooltip_text = "Убрать из списка"
		row.add_child(x)
		_recent_box.add_child(card)
