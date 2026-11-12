extends ScrollContainer
## Настройки: внешний вид и анимации, (ДЕМО покупок — только при Cfg.paid()), папка данных, сервер LANTERN, обновления,
## профиль и данные (конфиденциальность, удаление профиля — требование Google Play), о программе.

var app
var arg

var _upd: VBoxContainer
var _dir: LineEdit
var _relay: LineEdit
var _relay_st: Label


func _group(v: VBoxContainer, title: String) -> VBoxContainer:
	v.add_child(UI.section(title))
	var p := UI.panel(18)
	var inner := UI.vbox(16)
	p.add_child(inner)
	v.add_child(p)
	return inner


func _ready() -> void:
	horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	var v := UI.vbox(10)
	v.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	add_child(v)

	# --- внешний вид
	var g := _group(v, "Внешний вид")
	var lr := UI.hbox(10)
	var ll := UI.label("Язык", 15, UI.TEXT, UI.font_bold)
	ll.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	lr.add_child(ll)
	lr.add_child(UI.label("Русский", 15, UI.MUTED))
	g.add_child(lr)
	g.add_child(UI.divider())
	var av := UI.vbox(10)
	av.add_child(UI.label("Цвет акцента", 15, UI.TEXT, UI.font_bold))
	var ar := UI.hbox(10)
	for key in Cfg.ACCENTS:
		var c: Color = Cfg.ACCENTS[key]
		var b := Button.new()
		b.custom_minimum_size = Vector2(40, 40)
		b.focus_mode = Control.FOCUS_NONE
		b.mouse_default_cursor_shape = Control.CURSOR_POINTING_HAND
		var on: bool = Cfg.get_value("app", "accent", "amber") == key
		b.add_theme_stylebox_override("normal", UI.box(c, 99, 0, Color.WHITE if on else Color(0, 0, 0, 0), 3 if on else 0))
		b.add_theme_stylebox_override("hover", UI.box(c.lightened(0.15), 99, 0, Color(1, 1, 1, 0.6), 2))
		b.add_theme_stylebox_override("pressed", UI.box(c, 99, 0, Color.WHITE, 3))
		if on:
			var ck := UI.icon("check", 20, UI.ON_ACCENT)
			ck.stroke = 3.0
			ck.position = Vector2(10, 10)
			ck.size = Vector2(20, 20)
			b.add_child(ck)
		b.pressed.connect(func(): Cfg.set_accent(key))
		FX.press(b, 0.88)
		ar.add_child(b)
	av.add_child(ar)
	g.add_child(av)
	g.add_child(UI.divider())
	var rm := UI.switch_row("Меньше анимаций", "Без сдвигов и вспышек — для слабых телефонов и тех, кого укачивает.", Cfg.get_value("app", "reduce_motion", false), func(b): Cfg.set_value("app", "reduce_motion", b))
	g.add_child(rm.row)

	# --- покупки (в версии для Google Play скрыто: покупки и реклама не подключены)
	if Cfg.paid():
		var gm := _group(v, "Покупки и реклама")
		var demo := UI.switch_row("ДЕМО-режим", "Покупки показывают лист «как в Google Play», но ничего не списывают; реклама — тестовый экран на 5 секунд.", Social.demo, func(b):
			Social.set_demo(b)
			app.toast("ДЕМО включён" if b else "ДЕМО выключен: демо-покупки и демо-искры стёрты"))
		gm.add_child(demo.row)

	# --- миры
	var gd := _group(v, "Миры и копии")
	gd.add_child(UI.list_row(UI.icon_tile("cloud", UI.accent(), 40, 20), "Копии миров", "Резервные копии, облако, перенос по коду", UI.icon("chevron", 20, UI.DIM), func(): app.go("backups", true), Color(0, 0, 0, 0)))
	if not Cfg.mobile():
		gd.add_child(UI.list_row(UI.icon_tile("info", UI.BEDROCK, 40, 20), "Проверка системы", "Можно ли создать мир на этом компьютере", UI.icon("chevron", 20, UI.DIM), func(): app.go("syscheck", true), Color(0, 0, 0, 0)))
		gd.add_child(UI.divider())
		var dv := UI.vbox(8)
		dv.add_child(UI.label("Где хранить миры и серверы", 15, UI.TEXT, UI.font_bold))
		dv.add_child(UI.para("Серверы, миры, Java и копии. Нужно 1–3 ГБ свободного места.", 13, UI.MUTED))
		_dir = UI.line_edit(Cfg.data_dir())
		_dir.editable = false
		_dir.add_theme_font_size_override("font_size", 13)
		dv.add_child(_dir)
		if Cfg.server_root() != Cfg.data_dir():
			dv.add_child(UI.para("Серверы и Java — в %s (Java на Windows не понимает русские буквы в пути)." % Cfg.server_root(), 12, UI.DIM))
		var tools := UI.flow(8)
		tools.add_child(UI.button("Изменить…", "secondary", _pick_dir, "folder"))
		tools.add_child(UI.button("Открыть", "ghost", func(): OS.shell_open(ProjectSettings.globalize_path(Cfg.data_dir()))))
		tools.add_child(UI.button("По умолчанию", "ghost", func():
			Cfg.set_value("app", "data_dir", "")
			_dir.text = Cfg.data_dir()))
		dv.add_child(tools)
		gd.add_child(dv)

	# --- сервер LANTERN
	var gr := _group(v, "Сервер LANTERN")
	gr.add_child(UI.para("Через него друзья заходят по коду: он только передаёт данные игры между вами и ничего не хранит. Менять адрес нужно, только если у вас свой сервер.", 13, UI.MUTED))
	_relay = UI.line_edit(Cfg.relay_url(), Cfg.DEFAULT_RELAY)
	_relay.add_theme_font_override("font", UI.font_mono)
	_relay.add_theme_font_size_override("font_size", 13)
	gr.add_child(_relay)
	var rr := UI.flow(8)
	rr.add_child(UI.button("Сохранить", "secondary", _save_relay))
	rr.add_child(UI.button("Проверить связь", "ghost", _check_relay, "wifi"))
	gr.add_child(rr)
	_relay_st = UI.label("", 13, UI.DIM)
	gr.add_child(_relay_st)

	# --- обновления (только ПК: серверы живут там)
	if not Cfg.mobile():
		var gu := _group(v, "Обновления серверов")
		gu.add_child(UI.para("Серверы обновляются сами при каждом запуске мира. Здесь можно посмотреть, что нового.", 13, UI.MUTED))
		var ub := UI.button("Проверить обновления", "secondary", _check, "refresh")
		ub.size_flags_horizontal = Control.SIZE_SHRINK_BEGIN
		gu.add_child(ub)
		_upd = UI.vbox(6)
		gu.add_child(_upd)

	# --- профиль и данные
	var gp := _group(v, "Профиль и данные")
	gp.add_child(UI.list_row(UI.icon_tile("lock", UI.MUTED, 40, 20), "Конфиденциальность", "Что хранится и сколько", UI.icon("chevron", 20, UI.DIM), func(): OS.shell_open(Cfg.SITE + "/privacy"), Color(0, 0, 0, 0)))
	gp.add_child(UI.list_row(UI.icon_tile("info", UI.MUTED, 40, 20), "Условия использования", "", UI.icon("chevron", 20, UI.DIM), func(): OS.shell_open(Cfg.SITE + "/terms"), Color(0, 0, 0, 0)))
	gp.add_child(UI.divider())
	var db := UI.button("Удалить профиль", "danger", _ask_delete, "trash")
	db.size_flags_horizontal = Control.SIZE_SHRINK_BEGIN
	gp.add_child(db)
	gp.add_child(UI.para("Навсегда удалит ник, друзей, сообщения и облачные копии с сервера. Миры на устройстве останутся.", 12, UI.DIM))

	# --- о программе
	var ga := _group(v, "О программе")
	var rep := Report.button(app, "settings")
	rep.size_flags_horizontal = Control.SIZE_SHRINK_BEGIN
	ga.add_child(rep)
	var ah := UI.hbox(14)
	ah.add_child(UI.texture("res://assets/img/icon.png", 52, 52))
	var at := UI.vbox(0)
	at.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	at.add_child(UI.label("LANTERN " + Cfg.APP_VERSION, 17, UI.TEXT, UI.font_bold))
	at.add_child(UI.label("Создатель — Yompix", 13, UI.accent(), UI.font_bold))
	ah.add_child(at)
	ga.add_child(ah)
	ga.add_child(UI.para("Без телеметрии. Не требует прав администратора. Пароли не спрашивает.", 13, UI.MUTED))
	ga.add_child(UI.para("Сервер Paper (PaperMC), Geyser и Floodgate (GeyserMC), Java (Eclipse Temurin), Bedrock Dedicated Server (Mojang) скачиваются с официальных сайтов. Шрифты Montserrat и JetBrains Mono — лицензия SIL OFL 1.1. Движок Godot — MIT.", 12, UI.DIM))
	ga.add_child(UI.para(Cfg.DISCLAIMER + "\n" + Cfg.DISCLAIMER_EN, 12, UI.DIM))
	ga.add_child(UI.para("Minecraft — товарный знак Mojang Synergies AB. LANTERN не содержит файлов игры: нужна своя копия Minecraft.", 12, UI.DIM))
	v.add_child(UI.gap(16))


func _ask_delete() -> void:
	var s: VBoxContainer = Overlays.sheet(app, "Удалить профиль?", "Ник, тег, друзья, сообщения, искры и облачные копии будут стёрты с сервера навсегда. Отменить нельзя.")
	var row := UI.hbox(8)
	var no := UI.button("Отмена", "secondary", func(): Overlays.close_sheet(app))
	no.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	var yes := UI.button("Удалить навсегда", "danger", func():
		if not Social.online():
			app.toast("Нужен интернет — подключитесь и повторите", UI.YELLOW)
			return
		Social.delete_account()
		Overlays.close_sheet(app), "trash")
	yes.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	row.add_child(no)
	row.add_child(yes)
	s.add_child(row)


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
