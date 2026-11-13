extends VBoxContainer
## «Игры» — главная. 1.7: сверху «Продолжить» (последняя игра — одно нажатие), затем игры друзей, затем открытые игры.
## Строка игры: обложка-пейзаж (у каждой комнаты своя, палитра — по режиму), название, режим · версия · издание,
## игроки (стопка аватаров + «3/10»), связь тремя столбиками, номер комнаты. Нажали строку — заходим
## (пароль спросит экран входа), LANTERN сам откроет Minecraft. Один поиск: название, номер комнаты или режим.
## Обновление каждые 10 с и «потянуть вниз» (фонарь качается). Создать — светящаяся кнопка внизу.

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
var _pull_box: Control
var _pull_icon: LanternIcon
var _friends_box: VBoxContainer
var _friends_key := "?"

const QUALITY := {"great": ["отлично", Color("5fd38a")], "good": ["хорошо", Color("ffc857")], "poor": ["так себе", Color("ff6b6b")]}
const EDITION := {"java": ["Java", Color("5fd38a")], "bedrock": ["Телефоны", Color("6aaeff")], "both": ["Для всех", Color("c58bff")]}


func _ready() -> void:
	add_theme_constant_override("separation", 12)
	_version = String(Cfg.get_value("game", "filter_version", "any"))
	if UI.compact():
		var hr := UI.hbox(8)
		var h := UI.hero(L.t("Игры"))
		h.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		hr.add_child(h)
		var sb := UI.icon_button("search", _toggle_search, L.t("Поиск"), UI.TEXT, Color(1, 1, 1, 0.08))
		sb.size_flags_vertical = Control.SIZE_SHRINK_CENTER
		hr.add_child(sb)
		add_child(hr)
	else:
		app.set_title(L.t("Игры"), L.t("Зайдите в любую одним нажатием — LANTERN сам откроет Minecraft"))
		app.set_actions([UI.icon_button("search", _toggle_search, L.t("Поиск"), UI.MUTED), UI.icon_button("refresh", _load, L.t("Обновить"), UI.MUTED)])
	# --- игра уже идёт / «Продолжить»
	var live := false
	if app.own.active:
		add_child(_live_card_own())
		live = true
	elif app.server.state in ["installing", "starting", "running", "stopping", "error"]:
		add_child(_live_card_host())
		live = true
	if app.relay_join.stats().state in ["connecting", "online", "reconnecting"]:
		add_child(_live_card_join())
		live = true
	if not live:
		var cc := _continue_card()
		if cc:
			add_child(cc)
	_search = UI.line_edit("", L.t("Название, номер комнаты или режим"))
	_search.right_icon = UI.icon_tex("search", 20, UI.DIM)
	_search.visible = false
	_search.text_submitted.connect(func(t): _q = t.strip_edges(); _first = true; _load())
	add_child(_search)
	_filters = UI.hbox(8)
	add_child(_filters)
	_render_filters()
	# потянуть вниз — обновить: фонарь качается
	_pull_box = UI.vbox(2)
	_pull_box.visible = false
	var ph := Control.new()
	ph.custom_minimum_size = Vector2(0, 44)
	ph.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_pull_icon = LanternIcon.new().setup(6, {"flame": "flame_amber"}, 40)
	_pull_icon.animate = false
	_pull_icon.set_anchors_preset(Control.PRESET_CENTER_TOP)
	_pull_icon.offset_left = -20
	_pull_icon.offset_right = 20
	_pull_icon.offset_bottom = 40
	_pull_icon.pivot_offset = Vector2(20, 2)
	ph.add_child(_pull_icon)
	_pull_box.add_child(ph)
	_pull_lbl = UI.label("", 12, UI.DIM)
	_pull_lbl.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	_pull_box.add_child(_pull_lbl)
	add_child(_pull_box)
	_friends_box = UI.vbox(8)
	_friends_box.visible = false
	add_child(_friends_box)
	_list = UI.vbox(10)
	add_child(_list)
	for i in 3:
		_list.add_child(FX.skeleton(88, UI.R_XL))
	Social.changed.connect(_on_social)
	_render_friends()
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
	var modes := [["any", L.t("Все режимы")]] + Cfg.MODES
	_filters.add_child(_drop((L.t("Все режимы") if _mode == "any" else Cfg.mode_name(_mode)) + "  ▾", _mode != "any", modes, func(k): _mode = k))
	var vers := [["any", L.t("Любая версия")]]
	for v in _known_versions():
		vers.append([v, v])
	_filters.add_child(_drop((L.t("Версия") if _version == "any" else _version) + "  ▾", _version != "any", vers, func(k):
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
		_pull_box.visible = _pull > 24
		_pull_lbl.text = L.t("Отпустите, чтобы обновить") if _pull > 90 else L.t("Потяните, чтобы обновить")
		_pull_icon.rotation = sin(_pull * 0.07) * minf(_pull / 90.0, 1.0) * 0.45     # фонарь качается за пальцем
	elif (e is InputEventScreenTouch or e is InputEventMouseButton) and not e.pressed:
		if _pull > 90:
			_pull_lbl.text = L.t("Обновляем…")
			_first = true
			_load()
			if FX.on():
				var tw := _pull_icon.create_tween().set_trans(Tween.TRANS_SINE).set_ease(Tween.EASE_IN_OUT)
				for a in [0.5, -0.36, 0.22, -0.1, 0.0]:
					tw.tween_property(_pull_icon, "rotation", a, 0.16)
			get_tree().create_timer(0.85).timeout.connect(func(): if is_instance_valid(_pull_box): _pull_box.visible = false)
		else:
			_pull_box.visible = false
		_pull = 0.0


func _on_social(what: String) -> void:
	if not is_inside_tree():
		return
	if what == "rooms":
		_render()
		if _first:
			_render_filters()
	elif what == "friends":
		_render_friends()
	elif what == "conn" and Social.online():
		_load()
		_render_friends()


# ------------------------------------------------------------------ «Продолжить»

## Последняя игра, в которую заходили или которую создавали: одно нажатие — и снова в ней.
func _continue_card() -> Control:
	var g: Variant = Cfg.get_value("app", "last_game", {})
	if not g is Dictionary or (g as Dictionary).is_empty():
		return null
	var d: Dictionary = g
	if Time.get_unix_time_from_system() - float(d.get("time", 0)) > 30 * 86400:
		return null
	var host: bool = d.get("kind", "") == "host"
	var mode := String(d.get("mode", "other"))
	var p := PanelContainer.new()
	var st := UI.glass(2, UI.R_XL, 0, UI.accent())
	p.add_theme_stylebox_override("panel", st)
	UI.halo(p, UI.accent(), UI.R_XL, 0.55, true)
	var row := UI.hbox(14)
	row.mouse_filter = Control.MOUSE_FILTER_IGNORE
	var m := MarginContainer.new()
	m.mouse_filter = Control.MOUSE_FILTER_IGNORE
	for s in ["left", "top", "bottom"]:
		m.add_theme_constant_override("margin_" + s, 10)
	m.add_theme_constant_override("margin_right", 14)
	m.add_child(row)
	p.add_child(m)
	row.add_child(Cover.tile(String(d.get("code", d.get("name", ""))), mode, 76))
	var v := UI.vbox(2)
	v.mouse_filter = Control.MOUSE_FILTER_IGNORE
	v.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	v.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	v.add_child(UI.caption(L.t("Продолжить"), UI.accent().lightened(0.3)))
	var t := UI.label(String(d.get("name", L.t("Мир"))), 19, UI.TEXT, UI.font_title)
	t.clip_text = true
	t.text_overrun_behavior = TextServer.OVERRUN_TRIM_ELLIPSIS
	v.add_child(t)
	var sub := L.t("Ваша игра — открыть снова") if host else (L.t("Вы заходили · ") + (("№ " + String(d.num)) if String(d.get("num", "")) != "" else L.t("код ") + RoomCode.pretty(String(d.get("code", "")))))
	var sl := UI.label(sub, 13, UI.MUTED)
	sl.clip_text = true
	sl.text_overrun_behavior = TextServer.OVERRUN_TRIM_ELLIPSIS
	v.add_child(sl)
	row.add_child(v)
	var go := PanelContainer.new()
	go.custom_minimum_size = Vector2(44, 44)
	go.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	go.mouse_filter = Control.MOUSE_FILTER_IGNORE
	var gst := UI.box(UI.accent(), 99)
	gst.shadow_color = Color(UI.accent(), 0.45)
	gst.shadow_size = 10
	go.add_theme_stylebox_override("panel", gst)
	var ic := UIcon.make("play", 22, UI.ON_ACCENT)
	ic.stroke = 2.6
	ic.size_flags_horizontal = Control.SIZE_SHRINK_CENTER
	ic.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	go.add_child(ic)
	row.add_child(go)
	UI.tappable(p, func(): app.continue_last(), st)
	return p


# ------------------------------------------------------------------ игры друзей

func _render_friends() -> void:
	if not is_instance_valid(_friends_box):
		return
	var playing: Array = []
	if Social.online():
		var mine: String = app.relay_host.stats().get("code", "")
		for f in Social.friends:
			var a: Variant = f.get("activity")
			if f.get("online", false) and a is Dictionary and String(a.get("code", "")) != "" and String(a.code) != mine:
				playing.append(f)
	var key := JSON.stringify(playing.map(func(f): return [f.id, f.activity.get("code", ""), f.activity.get("world", "")]))
	if key == _friends_key:
		return
	_friends_key = key
	for c in _friends_box.get_children():
		c.queue_free()
	_friends_box.visible = not playing.is_empty()
	if playing.is_empty():
		return
	_friends_box.add_child(UI.section(L.t("Друзья играют")))
	for f in playing.slice(0, 4):
		_friends_box.add_child(_friend_row(f))


func _friend_row(f: Dictionary) -> Control:
	var a: Dictionary = f.activity
	var code := String(a.code)
	var hosting: bool = a.get("activity", "") == "hosting"
	var ed := String(a.get("edition", "both"))
	var p := PanelContainer.new()
	var st := UI._hpad(UI.glass(1, UI.R_XL, 0), 10, 10)
	p.add_theme_stylebox_override("panel", st)
	var row := UI.hbox(12)
	row.mouse_filter = Control.MOUSE_FILTER_IGNORE
	p.add_child(row)
	row.add_child(Cover.tile(code, "other", 64))
	var tv := UI.vbox(3)
	tv.mouse_filter = Control.MOUSE_FILTER_IGNORE
	tv.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	tv.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	var t := UI.label(String(a.get("world", L.t("Мир"))), 16, UI.TEXT, UI.font_bold)
	t.clip_text = true
	t.text_overrun_behavior = TextServer.OVERRUN_TRIM_ELLIPSIS
	tv.add_child(t)
	var who := UI.hbox(6)
	who.mouse_filter = Control.MOUSE_FILTER_IGNORE
	who.add_child(Avatar.of(f, 20))
	var wl := UI.label("%s %s" % [f.nick, L.t("создал(а) игру") if hosting else L.t("играет")], 13, UI.MUTED)
	wl.clip_text = true
	wl.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	wl.text_overrun_behavior = TextServer.OVERRUN_TRIM_ELLIPSIS
	who.add_child(wl)
	tv.add_child(who)
	row.add_child(tv)
	var e: Array = EDITION.get(ed, EDITION["both"])
	var ep := UI.pill(e[0], e[1])
	ep.mouse_filter = Control.MOUSE_FILTER_IGNORE
	row.add_child(ep)
	UI.tappable(p, func(): app.go("join", true, {"input": code, "edition": "both"}), st)
	return p


# ------------------------------------------------------------------ открытые игры

func _render() -> void:
	for c in _list.get_children():
		c.queue_free()
	if not Social.online():
		var reg := Social.conn == "need_register"
		_list.add_child(UI.empty("wifi" if not reg else "user", L.t("Нет связи с сервером LANTERN") if not reg else L.t("Придумайте ник"), L.t("Повторить") if not reg else L.t("Создать профиль"),
			func(): Overlays.register(app) if reg else Social.reconnect(), L.t("Список игр появится, когда вернётся интернет.") if not reg else L.t("Без профиля список игр не виден.")))
		return
	var mine: String = app.relay_host.stats().get("code", "")
	var rooms: Array = Social.rooms.filter(func(r): return r.code != mine)
	if rooms.is_empty():
		var filt := _mode != "any" or _version != "any" or _q != ""
		# в поиске — код или номер закрытой игры: зайти можно всё равно
		var code := RoomCode.normalize(_q) if _q != "" else ""
		if code != "" and (RoomCode.is_random(code) or RoomCode.is_number(code)):
			_list.add_child(UI.list_row(UI.icon_tile("play", UI.accent(), 48, 24), L.t("Зайти по коду ") + (code if RoomCode.is_number(code) else RoomCode.pretty(code)), L.t("В открытых такой игры нет — попробуем по коду"),
				UI.icon("chevron", 20, UI.DIM), func(): app.go("join", true, {"input": code, "edition": "both"})))
			return
		_list.add_child(UI.empty("globe", L.t("Сейчас нет открытых игр") + (L.t(" с такими фильтрами") if filt else ""), L.t("Создай первую игру"), func(): app.go("create"),
			L.t("Откройте свой мир — его увидят все в LANTERN.") if not filt else L.t("Попробуйте другое название, номер или режим.")))
		return
	_list.add_child(UI.section(L.t("Открытые игры · %d") % rooms.size()))
	for r in rooms.slice(0, 60):     # список не растёт бесконечно: 60 строк хватает, остальное — поиском
		_list.add_child(_row(r))
	if _first:
		_first = false
		FX.stagger(_list, 0.025)
	_coach()


func _row(r: Dictionary) -> Control:
	var host: Dictionary = r.get("host") if r.get("host") is Dictionary else {}
	var quality := String(r.get("quality", ""))
	var cap := int(r.get("cap", 0))
	var slots := cap if cap > 0 else int(r.get("slots", 10))
	var players := int(r.players) + 1   # с хозяином
	var full := players >= slots
	var mode := String(r.get("mode", "other"))
	var ed: String = r.edition
	var num := String(r.get("num", ""))
	var p := PanelContainer.new()
	var st := UI._hpad(UI.glass(1, UI.R_XL, 0), 10, 10)
	p.add_theme_stylebox_override("panel", st)
	var row := UI.hbox(12)
	row.mouse_filter = Control.MOUSE_FILTER_IGNORE
	p.add_child(row)
	# обложка: у каждой комнаты свой пейзаж, палитра — по режиму
	row.add_child(Cover.tile(num if num != "" else String(r.code), mode, 68))
	var tv := UI.vbox(4)
	tv.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	tv.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	tv.mouse_filter = Control.MOUSE_FILTER_IGNORE
	var t := UI.label(String(r.title), 16, UI.TEXT, UI.font_bold)
	t.clip_text = true
	t.text_overrun_behavior = TextServer.OVERRUN_TRIM_ELLIPSIS
	tv.add_child(t)
	# режим · версия · издание — точка и маленькие «таблетки»
	var meta := UI.flow(6)       # на узком экране (360 dp) метки переносятся, а не распирают строку
	meta.add_theme_constant_override("v_separation", 4)
	meta.mouse_filter = Control.MOUSE_FILTER_IGNORE
	meta.add_child(UI.dot_label(Cfg.mode_name(mode), Cover.mode_color(mode)))
	if String(r.get("version", "")) != "":
		var vp := UI.pill(String(r.version), UI.MUTED)
		vp.mouse_filter = Control.MOUSE_FILTER_IGNORE
		meta.add_child(vp)
	var e: Array = EDITION.get(ed, [ed, UI.MUTED])
	var ep := UI.pill(e[0], e[1])
	ep.mouse_filter = Control.MOUSE_FILTER_IGNORE
	meta.add_child(ep)
	tv.add_child(meta)
	# игроки: хозяин и ещё до двух кружков + «3/10»; номер комнаты табличными цифрами
	var bot := UI.hbox(8)
	bot.mouse_filter = Control.MOUSE_FILTER_IGNORE
	var names := [String(host.get("nick", "?"))]
	for i in mini(players - 1, 2):
		names.append("%s#%d" % [r.code, i])
	bot.add_child(UI.avatar_stack(names, players, slots, full))
	if num != "":
		var nl := UI.num("№ " + num, 11, UI.DIM)
		nl.size_flags_vertical = Control.SIZE_SHRINK_CENTER
		bot.add_child(nl)
	tv.add_child(bot)
	row.add_child(tv)
	var rv := UI.vbox(8)
	rv.mouse_filter = Control.MOUSE_FILTER_IGNORE
	rv.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	var bars := UI.signal_bars(quality)
	bars.size_flags_horizontal = Control.SIZE_SHRINK_END
	rv.add_child(bars)
	if r.get("locked", false):
		var lk := UI.icon("lock", 16, UI.MUTED)
		lk.size_flags_horizontal = Control.SIZE_SHRINK_END
		rv.add_child(lk)
	row.add_child(rv)
	UI.tappable(p, func():
		if full:
			app.toast(L.t("Все места заняты"), UI.YELLOW)
			return
		if String(r.get("version", "")) != "":
			Cfg.set_value("own", "last_version", "%s %s" % [ed, r.version])
		app.go("join", true, {"input": num if num != "" else String(r.code), "edition": "both", "public": true, "mode": mode}), st)
	return p


## Подсказка при первом запуске: «Нажми — и ты в игре» над первой игрой
func _coach() -> void:
	if Cfg.get_value("app", "coach_rooms", false) or Cfg.autotest != "" and not Cfg.autotest_args.has("coach") or _list.get_child_count() < 2:
		return
	Cfg.set_value("app", "coach_rooms", true)
	var first: Control = _list.get_child(1)
	await get_tree().process_frame
	await get_tree().process_frame
	if not is_instance_valid(first):
		return
	var ov := Control.new()
	ov.set_anchors_preset(Control.PRESET_FULL_RECT)
	ov.mouse_filter = Control.MOUSE_FILTER_STOP
	app.add_child(ov)
	var dim := ColorRect.new()
	dim.color = Color(0, 0, 0, 0.6)
	dim.set_anchors_preset(Control.PRESET_FULL_RECT)
	dim.mouse_filter = Control.MOUSE_FILTER_IGNORE
	ov.add_child(dim)
	var r := first.get_global_rect()
	var ring := Panel.new()
	var rst := UI.box(Color(0, 0, 0, 0), UI.R_XL + 2, 0, UI.accent(), 2)
	rst.shadow_color = Color(UI.accent(), 0.4)
	rst.shadow_size = 14
	ring.add_theme_stylebox_override("panel", rst)
	ring.position = r.position - Vector2(4, 4)
	ring.size = r.size + Vector2(8, 8)
	ring.mouse_filter = Control.MOUSE_FILTER_IGNORE
	ov.add_child(ring)
	var tip := PanelContainer.new()
	tip.add_theme_stylebox_override("panel", UI._hpad(UI.box(UI.accent(), UI.R_BTN), 16, 10))
	tip.add_child(UI.label(L.t("Нажми — и ты в игре  ↑"), 15, UI.ON_ACCENT, UI.font_bold))
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


# ------------------------------------------------------------------ игра идёт прямо сейчас

func _live_card(seed_text: String, mode: String, cap: String, title: String, sub: String, col: Color, target: String) -> Control:
	var p := PanelContainer.new()
	var st := UI._hpad(UI.glass(2, UI.R_XL, 0, col), 10, 10)
	p.add_theme_stylebox_override("panel", st)
	UI.halo(p, col, UI.R_XL, 0.5, true)
	var row := UI.hbox(12)
	row.mouse_filter = Control.MOUSE_FILTER_IGNORE
	p.add_child(row)
	row.add_child(Cover.tile(seed_text, mode, 64))
	var v := UI.vbox(2)
	v.mouse_filter = Control.MOUSE_FILTER_IGNORE
	v.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	v.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	v.add_child(UI.dot_label(cap, col))
	var t := UI.label(title, 17, UI.TEXT, UI.font_bold)
	t.clip_text = true
	t.text_overrun_behavior = TextServer.OVERRUN_TRIM_ELLIPSIS
	v.add_child(t)
	var sl := UI.label(sub, 13, UI.MUTED)
	sl.clip_text = true
	sl.text_overrun_behavior = TextServer.OVERRUN_TRIM_ELLIPSIS
	v.add_child(sl)
	row.add_child(v)
	var ch := UI.icon("chevron", 20, UI.MUTED)
	ch.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	row.add_child(ch)
	UI.tappable(p, func(): app.go(target), st)
	return p


func _live_card_host() -> Control:
	var s: ServerHost = app.server
	var code: String = app.relay_host.stats().get("code", "")
	var st: String = {"installing": L.t("Готовится…"), "starting": L.t("Запускается…"), "running": L.t("Идёт игра"), "stopping": L.t("Останавливается…"), "error": L.t("Ошибка")}.get(s.state, "")
	var sub := L.t("Код %s · игроков: %d") % [RoomCode.pretty(code), s.players.size()] if code != "" else L.t("Нажмите, чтобы открыть")
	return _live_card(code if code != "" else String(s.opts.get("world", "")), Cfg.game().mode, st, L.t("Ваш мир «%s»") % s.opts.get("world", L.t("Мир")), sub, UI.JAVA if s.state == "running" else UI.YELLOW, "host")


func _live_card_own() -> Control:
	var w: Dictionary = app.own.world
	var code: String = app.relay_host.stats().get("code", "")
	var sub := L.t("Код %s · друзей: %d") % [RoomCode.pretty(code), int(app.relay_host.stats().get("peers", 0))] if code != "" else L.t("Открываем комнату…")
	var ok: bool = app.own.alive()
	return _live_card(code if code != "" else String(w.get("name", "")), Cfg.game().mode, L.t("Игра открыта") if ok else L.t("Откройте Minecraft"), L.t("Ваш мир «%s»") % w.get("name", L.t("Мир")), sub, UI.JAVA if ok else UI.YELLOW, "host")


func _live_card_join() -> Control:
	var st: Dictionary = app.relay_join.stats()
	var t := L.t("Мир «%s»") % st.name if st.state == "online" and st.name != "" else L.t("Подключаемся…")
	return _live_card(app.relay_join.code, "other", L.t("Вы в игре") if st.state == "online" else L.t("Заходим"), t, L.t("Код ") + RoomCode.pretty(app.relay_join.code), UI.BEDROCK, "join")
