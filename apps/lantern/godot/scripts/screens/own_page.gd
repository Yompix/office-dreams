extends VBoxContainer
## «Создать мир → Мой мир из Minecraft»: ищем мир, открытый в игре на этом устройстве, и открываем его друзьям.
## Нашли — карточка мира и кнопка «Открыть для друзей». Не нашли — пошаговая подсказка с картинками.

var app
var _status: Label
var _status_icon: Control
var _found: VBoxContainer
var _help: VBoxContainer
var _help_body: VBoxContainer
var _help_ed := "bedrock"
var _seg: Control
var _port: LineEdit
var _shown_keys := "?"
var auto := false            # одна кнопка: нашли мир — сразу открываем комнату
var launch := false          # 1.7: сразу открыть Minecraft сами («Создать» на телефоне) — без лишних экранов
var _launch := {"t": 3.0, "on": false}
var _launch_card: Control
var _launched := false
var _actions: VBoxContainer
var _auto_done := false
var _check: VBoxContainer        # «мир не находится» — чек-лист вместо пассивного ожидания
var _check_key := ""
var _since := 0
const CHECK_AFTER_MS := 8000


func _ready() -> void:
	add_theme_constant_override("separation", 16)
	var head := UI.vbox(4)
	var h := UI.hero(L.t("Мой мир из Minecraft"), 28)
	h.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	head.add_child(h)
	head.add_child(UI.para(L.t("Друзья зайдут прямо в мир, который вы открыли в игре. Ничего скачивать не нужно."), 15, UI.MUTED))
	add_child(head)
	# --- поиск
	var sp := UI.panel(16)
	var sr := UI.hbox(12)
	sp.add_child(sr)
	_status_icon = UI.icon_tile("search", UI.accent(), 40, 20)
	sr.add_child(_status_icon)
	FX.pulse(_status_icon, 1.06, 1.4)
	_status = UI.para("", 15, UI.TEXT)
	_status.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_status.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	sr.add_child(_status)
	add_child(sp)
	_check = UI.vbox(10)
	_check.visible = false
	add_child(_check)
	_since = Time.get_ticks_msec()
	# --- 1.7: «Создать» → LANTERN сам открывает Minecraft и ждёт мир (в фоне тоже)
	if auto:
		app.own.arm_auto()
	if auto and launch and Cfg.mobile() and (Cfg.autotest == "" or Cfg.autotest_args.has("autoopen")):
		_launch.t = Launcher.COUNTDOWN
		_launch.on = true
		_launch_card = Launcher.countdown(_launch, L.t("Открываем Minecraft…"), L.t("Зайдите в свой мир — комната откроется сама, код придёт уведомлением."), _fire_launch, func():
			_launch_card.queue_free()
			_launch_card = null
			_refresh())
		add_child(_launch_card)
	# --- не нашли: одна кнопка «Открыть Minecraft» (+ на ПК — свой сервер)
	_actions = UI.vbox(10)
	add_child(_actions)
	var om := UI.button(L.t("Открыть Minecraft"), "primary", func():
		if not OwnWorld.open_minecraft():
			app.toast(L.t("Minecraft (Bedrock) не найден. Откройте игру сами и зайдите в мир — LANTERN его увидит."), UI.YELLOW), "play")
	om.custom_minimum_size.y = 56
	om.add_theme_font_size_override("font_size", 17)
	_actions.add_child(om)
	_actions.add_child(UI.para(L.t("Зайдите в свой мир — LANTERN сам откроет комнату и пришлёт код уведомлением. В настройках мира должна быть включена «Видимость для игроков LAN»."), 14, UI.MUTED))
	if not Cfg.mobile():
		var row := UI.flow(8)
		var last_ed: String = Cfg.get_value("host", "last_edition", "")
		var saved: Dictionary = Cfg.get_value("host", last_ed, {}) if last_ed != "" else {}
		if saved.get("eula", false):
			row.add_child(UI.button(L.t("Запустить сервер «%s» снова") % saved.get("world", L.t("Мир")), "secondary", func():
				app.server.host(last_ed, saved)
				app.go("host", false, {"fresh": true}), "server"))
		row.add_child(UI.button(L.t("Создать сервер LANTERN"), "ghost", func(): app.go("create", true), "server"))
		_actions.add_child(row)
	_found = UI.vbox(10)
	add_child(_found)
	# --- как открыть мир
	_help = UI.vbox(12)
	add_child(_help)
	_help.add_child(UI.section(L.t("Как открыть мир для друзей")))
	if OwnWorld.java_supported():
		_help_ed = Cfg.get_value("own", "last_edition", "bedrock")
		_seg = UI.segmented([["bedrock", "Bedrock"], ["java", "Java"]], _help_ed, func(k):
			_help_ed = k
			UI.segmented_select(_seg, k)
			_build_help())
		_help.add_child(_seg)
	_help_body = UI.vbox(12)
	_help.add_child(_help_body)
	_build_help()
	app.own.changed.connect(_refresh)
	app.own.begin_scan()
	_refresh()


func _process(_d: float) -> void:
	if not app or not is_instance_valid(_check):
		return
	var want: bool = app.own.worlds().is_empty() and Time.get_ticks_msec() - _since > CHECK_AFTER_MS
	var key := "%s|%d" % [want, app.own.probes / 5]
	if key != _check_key:
		_check_key = key
		_build_check(want)


## Чек-лист «мир не находится»: что проверить в игре + что делает LANTERN прямо сейчас.
func _build_check(on: bool) -> void:
	var was := _check.visible
	_check.visible = on
	for c in _check.get_children():
		c.queue_free()
	if not on:
		return
	var phone := Cfg.mobile()
	var p := UI.panel(16)
	var v := UI.vbox(12)
	p.add_child(v)
	var tl := UI.label(L.t("Мир пока не найден. Проверьте:"), 17, UI.TEXT, UI.font_bold)
	tl.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	v.add_child(tl)
	v.add_child(_check_row("?", L.t("Minecraft открыт"), L.t("Можно маленьким окном («картинка в картинке») — главное, не закрывать игру.") if phone else L.t("Окно игры можно свернуть — главное, не закрывать её.")))
	v.add_child(_check_row("?", L.t("Вы в самом мире, а не в меню"), L.t("Главное меню и список миров не считаются — зайдите в мир.")))
	v.add_child(_check_row("?", L.t("Включена «Видимость для игроков LAN»"), L.t("В мире: пауза → «Настройки» → «Мультиплеер». Там же должна быть включена «Многопользовательская игра».")))
	var lv: String = Cfg.get_value("own", "last_version", "")
	if lv != "":
		v.add_child(_check_row("ok", L.t("Ваша версия Minecraft: ") + lv, L.t("Так было в прошлый раз. Друзьям нужна та же версия игры.")))
	else:
		v.add_child(_check_row("ok", L.t("Подойдёт любая версия Minecraft"), L.t("LANTERN понимает и новый способ открытия мира (NetherNet, 2025+), и старый (RakNet). Друзьям нужна та же версия, что у вас.")))
	v.add_child(_check_row("search", L.t("LANTERN ищет мир на этом ") + (L.t("телефоне") if phone else L.t("компьютере")),
		L.t("Проверено %d раз: новый Minecraft (порт 7551) и старый (порт 19132).") % app.own.probes))
	if app.own.lan_error != "" and OwnWorld.java_supported():
		v.add_child(_check_row("no", L.t("Java: не слышим объявления миров"), app.own.lan_error))
	var b := UI.button(L.t("Проверить ещё раз"), "secondary", _recheck, "refresh")
	b.custom_minimum_size.y = 48
	v.add_child(b)
	_check.add_child(p)
	if not was:
		FX.enter(p)


func _check_row(state: String, title: String, sub: String) -> Control:
	var r := UI.hbox(12)
	var col: Color = {"ok": UI.JAVA, "no": UI.YELLOW, "search": UI.accent()}.get(state, UI.YELLOW)
	var ic: String = {"ok": "check", "no": "close", "search": "search"}.get(state, "info")
	var t := UI.icon_tile(ic, col, 32, 16)
	t.size_flags_vertical = Control.SIZE_SHRINK_BEGIN
	if state == "search":
		FX.pulse(t, 1.06, 1.4)
	r.add_child(t)
	var tv := UI.vbox(2)
	tv.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	var tl := UI.para(title, 15, UI.TEXT)
	tl.add_theme_font_override("font", UI.font_bold)
	tv.add_child(tl)
	if sub != "":
		tv.add_child(UI.para(sub, 13, UI.MUTED))
	r.add_child(tv)
	return r


func _fire_launch() -> void:
	_launch.on = false
	_launched = true
	if is_instance_valid(_launch_card):
		_launch_card.queue_free()
		_launch_card = null
	if not app.own.worlds().is_empty() or app.own.active:
		_refresh()
		return     # мир уже нашёлся, пока шёл отсчёт — открывать игру не нужно
	Launcher.open_bedrock(0, L.t("создать игру: зайдите в свой мир"))
	_refresh()


func _recheck() -> void:
	app.own.begin_scan()
	_since = Time.get_ticks_msec()
	_check_key = ""
	app.toast(L.t("Ищем мир ещё раз…"))


func _exit_tree() -> void:
	if app and app.own:
		if app.own.changed.is_connected(_refresh):
			app.own.changed.disconnect(_refresh)
		app.own.end_scan()
		# ушли с экрана, не дождавшись мира, и не в Minecraft — больше не ждём
		if app.own.auto_pending and not app.own.active:
			app.own.disarm_auto()


func _build_help() -> void:
	for c in _help_body.get_children():
		c.queue_free()
	var phone := Cfg.mobile()
	if _help_ed == "bedrock":
		_help_body.add_child(_step("1", L.t("Откройте Minecraft и зайдите в свой мир"), L.t("Или создайте новый — подойдёт любой."), MenuHint.make("world")))
		_help_body.add_child(_step("2", L.t("Пауза → «Настройки» → «Мультиплеер»"), L.t("Кнопка паузы — вверху экрана") + ("." if phone else L.t(" (или Esc).")), MenuHint.make("settings")))
		_help_body.add_child(_step("3", L.t("Включите «Видимость для игроков LAN»"), L.t("И «Многопользовательская игра», если она выключена."), MenuHint.make("toggle")))
		_help_body.add_child(_step("4", L.t("Вернитесь в LANTERN, не закрывая Minecraft"), L.t("Мир найдётся сам за пару секунд."), null))
		_help_body.add_child(_note("friends", L.t("Зайдут друзья с Bedrock: телефон, планшет, Windows, консоли. Друзья с Java (ПК) в такой мир зайти не смогут — для них нужен «Отдельный сервер» на компьютере.")))
	else:
		_help_body.add_child(_step("1", L.t("Зайдите в свой мир в Minecraft Java"), L.t("Одиночная игра → ваш мир."), MenuHint.make("world")))
		_help_body.add_child(_step("2", L.t("Нажмите Esc → «Открыть для сети»"), "", MenuHint.make("pause")))
		_help_body.add_child(_step("3", L.t("Нажмите «Открыть мир для сети»"), L.t("В чате появится «Локальный сервер запущен на порту …»."), MenuHint.make("lan")))
		_help_body.add_child(_step("4", L.t("Вернитесь в LANTERN"), L.t("Мир найдётся сам за пару секунд. Не нашёлся — введите номер порта из чата:"), null))
		var pr := UI.hbox(8)
		_port = UI.line_edit("", L.t("Порт, например 54321"))
		_port.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		_port.max_length = 5
		_port.virtual_keyboard_type = LineEdit.KEYBOARD_TYPE_NUMBER
		pr.add_child(_port)
		pr.add_child(UI.button(L.t("Подключить"), "secondary", _manual_port))
		_help_body.add_child(pr)
		_help_body.add_child(_note("lock", L.t("Друзьям нужна та же версия Minecraft: Java Edition и свой аккаунт Microsoft.")))
	FX.stagger(_help_body, 0.03)


func _step(n: String, title: String, sub: String, pic: Control) -> Control:
	var v := UI.vbox(8)
	var row := UI.hbox(14)
	var num := PanelContainer.new()
	num.custom_minimum_size = Vector2(36, 36)
	num.add_theme_stylebox_override("panel", UI.box(Color(UI.accent(), 0.16), 99))
	num.size_flags_vertical = Control.SIZE_SHRINK_BEGIN
	var nl := UI.label(n, 16, UI.accent(), UI.font_black)
	nl.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	nl.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
	num.add_child(nl)
	row.add_child(num)
	var tv := UI.vbox(2)
	tv.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	var tl := UI.para(title, 16, UI.TEXT)
	tl.add_theme_font_override("font", UI.font_bold)
	tv.add_child(tl)
	if sub != "":
		tv.add_child(UI.para(sub, 14, UI.MUTED))
	row.add_child(tv)
	v.add_child(row)
	if pic:
		var m := MarginContainer.new()
		m.add_theme_constant_override("margin_left", 50)
		m.add_child(pic)
		v.add_child(m)
	return v


func _note(icon_name: String, text: String) -> Control:
	var p := UI.panel(14, Color(1, 1, 1, 0.03))
	var r := UI.hbox(10)
	p.add_child(r)
	var ic := UI.icon(icon_name, 20, UI.MUTED)
	ic.size_flags_vertical = Control.SIZE_SHRINK_BEGIN
	r.add_child(ic)
	var l := UI.para(text, 14, UI.MUTED)
	l.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	r.add_child(l)
	return p


func _manual_port() -> void:
	var t := _port.text.strip_edges()
	if not t.is_valid_int() or int(t) < 1 or int(t) > 65535:
		app.toast(L.t("Введите номер порта — только цифры из чата Minecraft"), UI.YELLOW)
		return
	app.own.add_manual_java(int(t))


func _refresh() -> void:
	if not is_inside_tree():
		return
	var list: Array = app.own.worlds()
	var phone := Cfg.mobile()
	if list.is_empty():
		_status.text = L.t("Ищем открытый мир на этом ") + (L.t("телефоне") if phone else L.t("компьютере")) + "…"
		if app.own.lan_error != "" and OwnWorld.java_supported() and _help_ed == "java":
			_status.text += "\n" + app.own.lan_error
	else:
		_status.text = L.t("Нашли! Нажмите «Открыть для друзей».") if list.size() == 1 else L.t("Нашли несколько миров — выберите нужный.")
	_help.visible = list.is_empty()
	_actions.visible = list.is_empty() and not _launch.on    # идёт отсчёт — главная кнопка одна («Отмена» у отсчёта)
	if auto and not _auto_done and not list.is_empty() and not app.own.active:
		var pick: Variant = null
		var last: String = Cfg.get_value("own", "last_edition", "bedrock")
		for w in list:
			if w.edition == last:
				pick = w
				break
		if pick == null and list.size() == 1:
			pick = list[0]
		if pick != null:
			_auto_done = true
			_open.call_deferred(pick)
			return
	var keys := ",".join(list.map(func(w): return "%s|%s|%s" % [w.key, w.players, w.name]))
	if keys == _shown_keys:
		return
	var first := _shown_keys == "?" or _shown_keys == ""
	_shown_keys = keys
	for c in _found.get_children():
		c.queue_free()
	for w in list:
		var card := _world_card(w)
		_found.add_child(card)
		if first or not keys.contains(w.key):
			FX.enter(card)


func _world_card(w: Dictionary) -> Control:
	var bed: bool = w.edition == "bedrock"
	var col := UI.BEDROCK if bed else UI.JAVA
	var p := PanelContainer.new()
	p.add_theme_stylebox_override("panel", UI._hpad(UI.glass(2, UI.R_XL, 0, col), 16, 16))
	UI.halo(p, col, UI.R_XL, 0.6, true)
	var v := UI.vbox(12)
	p.add_child(v)
	var row := UI.hbox(12)
	row.add_child(UI.icon_tile("phone" if bed else "java", UI.BEDROCK if bed else UI.JAVA, 44, 22))
	var tv := UI.vbox(2)
	tv.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	var nl := UI.label(String(w.name), 18, UI.TEXT, UI.font_black)
	nl.clip_text = true
	nl.text_overrun_behavior = TextServer.OVERRUN_TRIM_ELLIPSIS
	tv.add_child(nl)
	var bits := PackedStringArray(["Bedrock" if bed else "Java"])
	if String(w.get("version", "")) != "":
		bits.append(String(w.version))
	if bed:
		if w.get("transport", "") == "nethernet":
			bits.append(L.t("новый Minecraft"))
	if int(w.get("players", -1)) >= 0:
		bits.append(L.t("в мире %d из %d") % [int(w.players), int(w.max)])
	elif not bed:
		bits.append(L.t("порт %d") % int(w.port))
	tv.add_child(UI.label(" · ".join(bits), 13, col.lightened(0.25), UI.font_bold))
	row.add_child(tv)
	v.add_child(row)
	v.add_child(UI.para(L.t("Зайдут друзья с Bedrock: телефон, планшет, Windows, консоли. С Java — не получится.") if bed else
		L.t("Зайдут друзья с Java той же версии."), 13, UI.MUTED))
	var b := UI.button(L.t("Открыть для друзей"), "primary", func(): _open(w), "play")
	b.custom_minimum_size.y = 52
	v.add_child(b)
	return p


func _open(w: Dictionary) -> void:
	_launch.on = false
	app.own.start(w)
	app.go("host", false, {"fresh": true})
