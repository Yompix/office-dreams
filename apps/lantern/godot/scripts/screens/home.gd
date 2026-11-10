extends VBoxContainer
## «Игры» — главная (как в Multiplayer Master, но в стиле LANTERN): список открытых игр.
## Строка: аватар хозяина (рамка — цвет связи), номер комнаты, связь «отлично/хорошо/так себе», название, режим,
## издание (Java/Bedrock/Обе), версия, размер мира, «2/6». Нажали строку — заходим (пароль спросит экран входа).
## Фильтры «Все режимы ▸», «Версия игры ▸», поиск; обновление каждые 10 с и потянуть вниз. Создать — кнопка «+» внизу.

var app
var arg
var _mode := "any"
var _version := "any"
var _q := ""
var _list: VBoxContainer
var _filters: HBoxContainer
var _search: LineEdit
var _tick := 0.0
var _first := true
var _pull := 0.0
var _pull_lbl: Label

const QUALITY := {"great": ["отлично", Color("5ccb7a")], "good": ["хорошо", Color("ffc857")], "poor": ["так себе", Color("ff6b6b")]}


func _ready() -> void:
	add_theme_constant_override("separation", 12)
	_version = String(Cfg.get_value("game", "filter_version", "any"))
	if UI.compact():
		var hr := UI.hbox(8)
		var h := UI.label("Игры", 26, UI.TEXT, UI.font_black)
		h.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		hr.add_child(h)
		hr.add_child(UI.icon_button("search", _toggle_search, "Поиск", UI.MUTED))
		add_child(hr)
	else:
		app.set_title("Игры", "Открытые игры — зайдите в любую одним нажатием")
		app.set_actions([UI.icon_button("search", _toggle_search, "Поиск", UI.MUTED), UI.icon_button("refresh", _load, "Обновить", UI.MUTED)])
	# --- мир уже идёт
	if app.own.active:
		add_child(_live_card_own())
	elif app.server.state in ["installing", "starting", "running", "stopping", "error"]:
		add_child(_live_card_host())
	if app.relay_join.stats().state in ["connecting", "online"]:
		add_child(_live_card_join())
	_search = UI.line_edit("", "Название или номер комнаты")
	_search.right_icon = UI.icon_tex("search", 20, UI.DIM)
	_search.visible = false
	_search.text_submitted.connect(func(t): _q = t.strip_edges(); _first = true; _load())
	add_child(_search)
	_filters = UI.hbox(8)
	add_child(_filters)
	_render_filters()
	_pull_lbl = UI.label("", 12, UI.DIM)
	_pull_lbl.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	_pull_lbl.visible = false
	add_child(_pull_lbl)
	_list = UI.vbox(8)
	add_child(_list)
	for i in 3:
		_list.add_child(FX.skeleton(84, UI.R_L))
	Social.changed.connect(_on_social)
	_load()


func _toggle_search() -> void:
	_search.visible = not _search.visible
	if _search.visible:
		_search.grab_focus()
	elif _q != "":
		_q = ""
		_search.text = ""
		_load()


func _render_filters() -> void:
	for c in _filters.get_children():
		c.queue_free()
	var modes := [["any", "Все режимы"]] + Cfg.MODES
	_filters.add_child(_drop(("Все режимы" if _mode == "any" else Cfg.mode_name(_mode)) + " ▸", _mode != "any", modes, func(k): _mode = k))
	var vers := [["any", "Любая версия"]]
	for v in _known_versions():
		vers.append([v, v])
	_filters.add_child(_drop(("Версия игры" if _version == "any" else _version) + " ▸", _version != "any", vers, func(k):
		_version = k
		Cfg.set_value("game", "filter_version", k)))


## Чип с выпадающим списком
func _drop(text: String, on: bool, items: Array, cb: Callable) -> Button:
	var b := UI.chip(text, on)
	b.pressed.connect(func():
		var pm := PopupMenu.new()
		for i in items.size():
			pm.add_item(String(items[i][1]), i)
		pm.id_pressed.connect(func(id):
			cb.call(String(items[id][0]))
			_first = true
			_render_filters()
			_load())
		pm.popup_hide.connect(pm.queue_free)
		add_child(pm)
		pm.popup(Rect2i(Vector2i(b.get_screen_position() + Vector2(0, b.size.y)), Vector2i(220, 0))))
	return b


## Версии для фильтра: «мажор.минор» из открытых игр и своей последней версии
func _known_versions() -> Array:
	var out := []
	var last := String(Cfg.get_value("own", "last_version", "")).get_slice(" ", 1)
	for v in [last] + Social.rooms.map(func(r): return String(r.get("version", ""))):
		var p := String(v).split(".")
		if p.size() >= 2 and p[0].is_valid_int():
			var mm := p[0] + "." + p[1]
			if not mm in out:
				out.append(mm)
	if _version != "any" and not _version in out:
		out.append(_version)
	out.sort()
	out.reverse()
	return out


func _load() -> void:
	_tick = 0.0
	Social.request("rooms", {"edition": "any", "mode": _mode, "version": _version, "q": _q})
	if not Social.online():
		_render()


func _process(d: float) -> void:
	_tick += d
	if _tick > 10.0 and is_visible_in_tree():
		_load()


## Потянуть вниз, когда список в самом верху, — обновить
func _input(e: InputEvent) -> void:
	if not is_visible_in_tree():
		return
	var sc := get_parent() as ScrollContainer
	var at_top := sc == null or sc.scroll_vertical <= 0
	if (e is InputEventScreenDrag or (e is InputEventMouseMotion and e.button_mask & MOUSE_BUTTON_MASK_LEFT)) and at_top:
		_pull = maxf(0.0, _pull + e.relative.y) if e.relative.y > 0 or _pull > 0 else 0.0
		_pull_lbl.visible = _pull > 24
		_pull_lbl.text = "Отпустите, чтобы обновить" if _pull > 90 else "Потяните, чтобы обновить"
	elif (e is InputEventScreenTouch or e is InputEventMouseButton) and not e.pressed:
		if _pull > 90:
			_pull_lbl.text = "Обновляем…"
			_first = true
			_load()
			get_tree().create_timer(0.6).timeout.connect(func(): if is_instance_valid(_pull_lbl): _pull_lbl.visible = false)
		else:
			_pull_lbl.visible = false
		_pull = 0.0


func _on_social(what: String) -> void:
	if not is_inside_tree():
		return
	if what == "rooms":
		_render()
		if _first:
			_render_filters()
	elif what == "conn" and Social.online():
		_load()


func _render() -> void:
	for c in _list.get_children():
		c.queue_free()
	if not Social.online():
		var reg := Social.conn == "need_register"
		_list.add_child(UI.empty("wifi" if not reg else "user", "Нет связи с сервером LANTERN" if not reg else "Придумайте ник", "Повторить" if not reg else "Создать профиль",
			func(): Overlays.register(app) if reg else Social.reconnect(), "Список игр появится, когда вернётся интернет." if not reg else "Без профиля список игр не виден."))
		return
	var mine: String = app.relay_host.stats().get("code", "")
	var rooms: Array = Social.rooms.filter(func(r): return r.code != mine)
	if rooms.is_empty():
		var filt := _mode != "any" or _version != "any" or _q != ""
		_list.add_child(UI.empty("globe", "Сейчас нет открытых игр" + (" с такими фильтрами" if filt else ""), "Создай первую игру", func(): app.go("create"),
			"Откройте свой мир — его увидят все в LANTERN." if not filt else "Попробуйте другой режим или версию."))
		return
	for r in rooms:
		_list.add_child(_row(r))
	if _first:
		_first = false
		FX.stagger(_list, 0.025)
	_coach()


func _row(r: Dictionary) -> Control:
	var host: Dictionary = r.get("host") if r.get("host") is Dictionary else {}
	var q: Array = QUALITY.get(String(r.get("quality", "")), ["", UI.DIM])
	var cap := int(r.get("cap", 0))
	var slots := cap if cap > 0 else int(r.get("slots", 10))
	var players := int(r.players) + 1   # с хозяином
	var full := players >= slots
	var p := PanelContainer.new()
	var st := UI._hpad(UI.box(UI.CARD, UI.R_L, 0, Color(1, 1, 1, 0.04), 1), 12, 12)
	p.add_theme_stylebox_override("panel", st)
	var row := UI.hbox(12)
	row.mouse_filter = Control.MOUSE_FILTER_IGNORE
	p.add_child(row)
	# аватар в скруглённой рамке цвета связи
	var av := PanelContainer.new()
	av.add_theme_stylebox_override("panel", UI.box(Color(q[1], 0.12), 14, 2, q[1], 2))
	av.mouse_filter = Control.MOUSE_FILTER_IGNORE
	av.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	av.add_child(LanternIcon.new().setup(int(host.get("lantern", {}).get("level", 1)), host.get("equip", ""), 44))
	row.add_child(av)
	var tv := UI.vbox(2)
	tv.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	tv.mouse_filter = Control.MOUSE_FILTER_IGNORE
	var top := UI.hbox(6)
	top.add_child(UI.label(String(r.get("num", "")) if String(r.get("num", "")) != "" else RoomCode.pretty(r.code), 12, UI.DIM, UI.font_mono))
	if q[0] != "":
		top.add_child(UI.label("● " + String(q[0]), 12, q[1], UI.font_bold))
	tv.add_child(top)
	var t := UI.label(String(r.title), 16, UI.TEXT, UI.font_bold)
	t.clip_text = true
	t.text_overrun_behavior = TextServer.OVERRUN_TRIM_ELLIPSIS
	tv.add_child(t)
	var bits := [Cfg.mode_name(String(r.get("mode", "other")))]
	if String(r.get("version", "")) != "":
		bits.append(String(r.version))
	if int(r.get("size_mb", 0)) > 0:
		bits.append("%d МБ" % int(r.size_mb))
	var ml := UI.label(" · ".join(bits), 12, UI.MUTED)
	ml.clip_text = true
	ml.text_overrun_behavior = TextServer.OVERRUN_TRIM_ELLIPSIS
	tv.add_child(ml)
	row.add_child(tv)
	var rv := UI.vbox(6)
	rv.mouse_filter = Control.MOUSE_FILTER_IGNORE
	rv.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	var ed: String = r.edition
	var pr := UI.hbox(4)
	pr.alignment = BoxContainer.ALIGNMENT_END
	if r.get("locked", false):
		pr.add_child(UI.icon("lock", 14, UI.MUTED))
	pr.add_child(UI.pill("%d/%d" % [players, slots], UI.RED if full else UI.JAVA))
	rv.add_child(pr)
	var ep := UI.pill({"java": "Java", "bedrock": "Телефоны", "both": "Для всех"}.get(ed, ed), UI.JAVA if ed == "java" else (UI.BEDROCK if ed == "bedrock" else Color("c58bff")))
	ep.size_flags_horizontal = Control.SIZE_SHRINK_END
	rv.add_child(ep)
	row.add_child(rv)
	UI.tappable(p, func():
		if full:
			app.toast("Все места заняты", UI.YELLOW)
			return
		if String(r.get("version", "")) != "":
			Cfg.set_value("own", "last_version", "%s %s" % [ed, r.version])
		app.go("join", true, {"input": String(r.get("num", "")) if String(r.get("num", "")) != "" else String(r.code), "edition": "both", "public": true}), st)
	return p


## Подсказка при первом запуске: «Нажми, чтобы зайти в игру» над первой игрой
func _coach() -> void:
	if Cfg.get_value("app", "coach_rooms", false) or Cfg.autotest != "" and not Cfg.autotest_args.has("coach") or _list.get_child_count() == 0:
		return
	Cfg.set_value("app", "coach_rooms", true)
	var first: Control = _list.get_child(0)
	await get_tree().process_frame
	await get_tree().process_frame
	if not is_instance_valid(first):
		return
	var ov := Control.new()
	ov.set_anchors_preset(Control.PRESET_FULL_RECT)
	ov.mouse_filter = Control.MOUSE_FILTER_STOP
	app.add_child(ov)
	var dim := ColorRect.new()
	dim.color = Color(0, 0, 0, 0.55)
	dim.set_anchors_preset(Control.PRESET_FULL_RECT)
	dim.mouse_filter = Control.MOUSE_FILTER_IGNORE
	ov.add_child(dim)
	var r := first.get_global_rect()
	var ring := Panel.new()
	ring.add_theme_stylebox_override("panel", UI.box(Color(0, 0, 0, 0), UI.R_L + 4, 0, UI.accent(), 2))
	ring.position = r.position - Vector2(4, 4)
	ring.size = r.size + Vector2(8, 8)
	ring.mouse_filter = Control.MOUSE_FILTER_IGNORE
	ov.add_child(ring)
	var tip := PanelContainer.new()
	tip.add_theme_stylebox_override("panel", UI._hpad(UI.box(UI.accent(), UI.R_M), 16, 10))
	tip.add_child(UI.label("Нажми, чтобы зайти в игру  ↑", 15, UI.ON_ACCENT, UI.font_bold))
	tip.position = Vector2(r.position.x + 8, r.end.y + 14)
	tip.mouse_filter = Control.MOUSE_FILTER_IGNORE
	ov.add_child(tip)
	ov.modulate.a = 0.0
	if FX.on():
		ov.create_tween().tween_property(ov, "modulate:a", 1.0, 0.2)
	else:
		ov.modulate.a = 1.0
	ov.gui_input.connect(func(e):
		if (e is InputEventMouseButton or e is InputEventScreenTouch) and e.pressed:
			ov.queue_free())


func _live_card_host() -> Control:
	var s: ServerHost = app.server
	var code: String = app.relay_host.stats().get("code", "")
	var st: String = {"installing": "готовится…", "starting": "запускается…", "running": "работает", "stopping": "останавливается…", "error": "— ошибка"}.get(s.state, "")
	var sub := "Код %s · игроков: %d" % [RoomCode.pretty(code), s.players.size()] if code != "" else "Нажмите, чтобы открыть"
	return UI.list_row(_dot_tile(UI.JAVA if s.state == "running" else UI.YELLOW, "server"), "Ваш мир «%s» %s" % [s.opts.get("world", "Мир"), st], sub, UI.icon("chevron", 20, UI.MUTED), func(): app.go("host"), Color("1b2a22"))


func _live_card_own() -> Control:
	var w: Dictionary = app.own.world
	var code: String = app.relay_host.stats().get("code", "")
	var sub := "Код %s · друзей: %d" % [RoomCode.pretty(code), int(app.relay_host.stats().get("peers", 0))] if code != "" else "Открываем комнату…"
	var ok: bool = app.own.alive()
	return UI.list_row(_dot_tile(UI.JAVA if ok else UI.YELLOW, "server"), "Ваш мир «%s» %s" % [w.get("name", "Мир"), "открыт" if ok else "— откройте Minecraft"], sub, UI.icon("chevron", 20, UI.MUTED), func(): app.go("host"), Color("1b2a22"))


func _live_card_join() -> Control:
	var st: Dictionary = app.relay_join.stats()
	var t := "Вы в мире «%s»" % st.name if st.state == "online" and st.name != "" else "Подключаемся к миру…"
	return UI.list_row(_dot_tile(UI.BEDROCK, "play"), t, "Код " + RoomCode.pretty(app.relay_join.code), UI.icon("chevron", 20, UI.MUTED), func(): app.go("join"), Color("18243a"))


func _dot_tile(c: Color, icon: String) -> Control:
	var t := UI.icon_tile(icon, c, 44, 22)
	FX.pulse(t, 1.06, 1.6)
	return t


func _friends_playing(box: VBoxContainer) -> void:
	box.add_child(UI.section("Друзья в игре", "Все друзья", func(): app.go("friends")))
	if not Social.online():
		var reg := Social.conn == "need_register"
		var msg := "Придумайте ник — и добавляйте друзей" if reg else "Нет связи с сервером LANTERN — друзья появятся, когда вернётся интернет"
		box.add_child(UI.list_row(UI.icon_tile("wifi" if not reg else "user", UI.YELLOW, 44, 22), "Друзья пока не видны", msg, null, func():
			if reg:
				Overlays.register(app)
			else:
				Social.reconnect()))
		return
	var playing := Social.friends.filter(func(f):
		var a: Variant = f.get("activity")
		return f.get("online", false) and a is Dictionary and String(a.get("code", "")) != "")
	if playing.is_empty():
		var online := Social.friends.filter(func(f): return f.get("online", false)).size()
		var t := "Сейчас никто не играет" if not Social.friends.is_empty() else "Добавьте друзей по тегу"
		var sub := ("В сети: %d. Позовите их в свой мир!" % online) if not Social.friends.is_empty() else "Ваш тег: %s" % Social.me.get("tag", "")
		box.add_child(UI.list_row(UI.icon_tile("friends", UI.accent(), 44, 22), t, sub, UI.icon("chevron", 20, UI.DIM), func(): app.go("friends")))
		return
	for f in playing.slice(0, 3):
		var a: Dictionary = f.activity
		var ed: String = {"java": "Java", "bedrock": "Bedrock", "both": "Java + Bedrock"}.get(a.get("edition", ""), "")
		var b := UI.button("Зайти", "primary", func(): app.go("join", true, {"input": a.code, "edition": "both"}))
		b.custom_minimum_size.x = 88
		var li := LanternIcon.new().setup(int(f.get("lantern", {}).get("level", 1)), f.get("equip", ""), 44)
		box.add_child(UI.list_row(li, "%s · «%s»" % [f.nick, a.get("world", "")], ("хостит " if a.get("activity") == "hosting" else "играет · ") + ed, b))


