extends ScrollContainer
## Настройки: язык, цвет, папка данных, сервер-посредник, обновления, о программе.

var app
var arg

var _upd: VBoxContainer
var _dir: LineEdit
var _relay: LineEdit
var _relay_st: Label


func _ready() -> void:
	horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	var cols := UI.cols(20)
	cols.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	add_child(cols)
	var l := UI.vbox(16)
	l.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	cols.add_child(l)
	var r := UI.vbox(16)
	r.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	cols.add_child(r)
	l.add_child(UI.h1("Настройки"))

	# --- внешний вид
	var p := UI.panel(22)
	var v := UI.vbox(14)
	p.add_child(v)
	v.add_child(UI.h3("Внешний вид"))
	var lr := UI.hbox(10)
	lr.add_child(UI.label("Язык", 15, UI.MUTED))
	lr.add_child(UI.spacer())
	var lang := UI.option(["Русский"])
	lang.disabled = true
	lang.custom_minimum_size.x = 200
	lr.add_child(lang)
	v.add_child(lr)
	var ar := UI.hbox(10)
	ar.add_child(UI.label("Цвет акцента", 15, UI.MUTED))
	ar.add_child(UI.spacer())
	for key in Cfg.ACCENTS:
		var c: Color = Cfg.ACCENTS[key]
		var b := Button.new()
		b.custom_minimum_size = Vector2(36, 36)
		b.mouse_default_cursor_shape = Control.CURSOR_POINTING_HAND
		var on: bool = Cfg.get_value("app", "accent", "amber") == key
		b.add_theme_stylebox_override("normal", UI.box(c, 99, 0, Color.WHITE if on else Color(0, 0, 0, 0), 3 if on else 0))
		b.add_theme_stylebox_override("hover", UI.box(c.lightened(0.15), 99, 0, Color.WHITE, 2))
		b.add_theme_stylebox_override("pressed", UI.box(c, 99, 0, Color.WHITE, 3))
		b.pressed.connect(func(): Cfg.set_accent(key))
		ar.add_child(b)
	v.add_child(ar)
	l.add_child(p)

	# --- папка данных
	var pd := UI.panel(22)
	var dv := UI.vbox(10)
	pd.add_child(dv)
	dv.add_child(UI.h3("Где хранить миры и серверы"))
	dv.add_child(UI.para("Здесь лежат серверы, миры, Java и резервные копии. Нужно 1–3 ГБ свободного места.", 14, UI.MUTED))
	var dr := UI.hbox(8)
	_dir = UI.line_edit(Cfg.data_dir())
	_dir.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_dir.editable = false
	dr.add_child(_dir)
	dr.add_child(UI.button("Изменить…", "secondary", _pick_dir))
	dr.add_child(UI.button("Открыть", "ghost", func(): OS.shell_open(ProjectSettings.globalize_path(Cfg.data_dir()))))
	dv.add_child(dr)
	if Cfg.server_root() != Cfg.data_dir():
		dv.add_child(UI.para("Серверы и Java — в %s (Java на Windows не понимает русские буквы в пути)." % Cfg.server_root(), 13, UI.DIM))
	var tools := UI.flow(8)
	tools.add_child(UI.button("Вернуть папку по умолчанию", "ghost", func():
		Cfg.set_value("app", "data_dir", "")
		_dir.text = Cfg.data_dir()))
	tools.add_child(UI.button("Копии миров", "secondary", func(): app.go("backups", true)))
	if not Cfg.mobile():
		tools.add_child(UI.button("Проверка системы", "secondary", func(): app.go("syscheck", true)))
	dv.add_child(tools)
	l.add_child(pd)

	# --- relay
	var pr := UI.panel(22)
	var rv := UI.vbox(10)
	pr.add_child(rv)
	rv.add_child(UI.h3("Сервер-посредник (комнаты по коду)"))
	rv.add_child(UI.para("Через него друзья заходят по коду. Он только передаёт данные игры между вами и ничего не хранит.", 14, UI.MUTED))
	var rr := UI.hbox(8)
	_relay = UI.line_edit(Cfg.relay_url(), Cfg.DEFAULT_RELAY)
	_relay.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_relay.add_theme_font_override("font", UI.font_mono)
	_relay.add_theme_font_size_override("font_size", 14)
	rr.add_child(_relay)
	rr.add_child(UI.button("Сохранить", "secondary", _save_relay))
	rr.add_child(UI.button("Проверить", "ghost", _check_relay))
	rv.add_child(rr)
	_relay_st = UI.label("", 14, UI.DIM)
	rv.add_child(_relay_st)
	l.add_child(pr)

	# --- обновления
	if not UI.compact():
		r.add_child(UI.gap(46))
	var pm := UI.panel(22)
	var mv := UI.vbox(10)
	pm.add_child(mv)
	mv.add_child(UI.h3("Тест покупок и рекламы"))
	var demo := CheckButton.new()
	demo.text = "ДЕМО-режим (деньги не списываются)"
	demo.button_pressed = Social.demo
	demo.add_theme_font_override("font", UI.font_bold)
	demo.toggled.connect(func(b):
		Social.set_demo(b)
		app.toast("ДЕМО включён" if b else "ДЕМО выключен: демо-покупки и демо-искры стёрты"))
	mv.add_child(demo)
	mv.add_child(UI.para("Покупки показывают лист «как в Google Play», но ничего не списывают; реклама — тестовый экран на 5 секунд. Настоящие покупки и AdMob включатся, когда автор настроит Google Play.", 13, UI.DIM))
	r.add_child(pm)
	var pu := UI.panel(22)
	var uv := UI.vbox(10)
	pu.add_child(uv)
	uv.add_child(UI.h3("Обновления серверов"))
	uv.add_child(UI.para("Серверы обновляются сами при каждом запуске мира. Здесь можно просто посмотреть, что нового.", 14, UI.MUTED))
	uv.add_child(UI.button("Проверить обновления", "secondary", _check))
	_upd = UI.vbox(6)
	uv.add_child(_upd)
	r.add_child(pu)

	# --- о программе
	var pa := UI.panel(22)
	var av := UI.vbox(8)
	pa.add_child(av)
	var ah := UI.hbox(12)
	ah.add_child(UI.texture("res://assets/img/icon.png", 52, 52))
	var at := UI.vbox(0)
	at.add_child(UI.label("LANTERN " + Cfg.APP_VERSION, 18, UI.TEXT, UI.font_bold))
	at.add_child(UI.label("Создатель — Yompix", 14, UI.accent(), UI.font_bold))
	ah.add_child(at)
	av.add_child(ah)
	av.add_child(UI.para("Без телеметрии и рекламы. Не требует прав администратора. Пароли не спрашивает.", 14, UI.MUTED))
	av.add_child(UI.para("Сервер Paper (PaperMC), Geyser и Floodgate (GeyserMC), Java (Eclipse Temurin), Bedrock Dedicated Server (Mojang) скачиваются с официальных сайтов. Шрифты Montserrat и JetBrains Mono — лицензия SIL OFL 1.1. Движок Godot — MIT.", 13, UI.DIM))
	av.add_child(UI.para("Не является официальным продуктом Minecraft. Не одобрено и не связано с Mojang или Microsoft.", 13, UI.DIM))
	r.add_child(pa)


func _pick_dir() -> void:
	var fd := FileDialog.new()
	fd.file_mode = FileDialog.FILE_MODE_OPEN_DIR
	fd.access = FileDialog.ACCESS_FILESYSTEM
	fd.use_native_dialog = true
	fd.title = "Папка для миров"
	fd.dir_selected.connect(func(d):
		if app.server.is_alive():
			app.toast("Сначала остановите сервер", UI.YELLOW)
			return
		Cfg.set_value("app", "data_dir", d.path_join("LANTERN"))
		DirAccess.make_dir_recursive_absolute(Cfg.data_dir())
		_dir.text = Cfg.data_dir()
		if Cfg.is_windows() and not Cfg.is_ascii_path(Cfg.data_dir()):
			app.toast("В пути есть русские буквы — серверы и Java всё равно будут в " + Cfg.server_root(), UI.YELLOW)
		else:
			app.toast("Новые миры будут в этой папке"))
	add_child(fd)
	fd.popup_centered(Vector2i(800, 500))


func _save_relay() -> void:
	var u := _relay.text.strip_edges()
	if u != "" and not (u.begins_with("wss://") or u.begins_with("ws://")):
		_relay_st.text = "Адрес должен начинаться с wss://"
		_relay_st.add_theme_color_override("font_color", UI.YELLOW)
		return
	Cfg.set_value("app", "relay", "" if u == Cfg.DEFAULT_RELAY else u)
	_relay.text = Cfg.relay_url()
	app.toast("Сохранено")


func _check_relay() -> void:
	var u := _relay.text.strip_edges()
	var h := u.replace("wss://", "https://").replace("ws://", "http://").trim_suffix("/ws") + "/healthz"
	_relay_st.text = "Проверяем…"
	var r: Dictionary = await Net.get_json(h)
	if not is_inside_tree():
		return
	if r.ok and r.data is Dictionary and r.data.get("app", "") == "lantern-relay":
		_relay_st.text = "✓ Работает. Комнат сейчас: %d" % int(r.data.rooms)
		_relay_st.add_theme_color_override("font_color", UI.JAVA)
	else:
		_relay_st.text = "✗ Не отвечает: " + str(r.get("error", "это не сервер LANTERN"))
		_relay_st.add_theme_color_override("font_color", UI.RED)


func _check() -> void:
	for c in _upd.get_children():
		c.queue_free()
	_upd.add_child(UI.label("Проверяем…", 14, UI.DIM))
	var lines: PackedStringArray = await app.server.check_updates()
	if not is_inside_tree():
		return
	for c in _upd.get_children():
		c.queue_free()
	for t in lines:
		_upd.add_child(UI.para("• " + t, 14, UI.TEXT))
