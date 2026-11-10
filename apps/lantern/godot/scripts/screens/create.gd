extends VBoxContainer
## «Создать игру» (кнопка «+»): название, мир, режим, мест, пароль, видимость → «Создать».
## Дальше — привычные шаги: на телефоне LANTERN сам находит мир, открытый в Minecraft; на ПК можно и «Отдельный сервер».
## Если игра уже идёт — настройки применяются сразу (relay: room_meta.settings).

var app
var arg

var _title: LineEdit
var _kind := "own"
var _mode := "survival"
var _cap := 6
var _pw_on := false
var _pw: LineEdit
var _public := true
var _cap_lbl: Label
var _modes: HFlowContainer


func _ready() -> void:
	add_theme_constant_override("separation", 16)
	var g := Cfg.game()
	_mode = g.mode
	_cap = clampi(g.cap, 2, 20)
	_pw_on = g.password != ""
	_public = g.public
	var live: bool = app.own.active or app.server.state in ["starting", "running"]
	app.set_title("Создать игру" if not live else "Настройки игры", "Её увидят в списке «Игры»")
	_title = UI.line_edit(g.title, "Например: Выживание с друзьями")
	_title.max_length = 40
	add_child(UI.field("Название", _title))
	if not live:
		if Cfg.mobile():
			add_child(UI.field("Мир", UI.list_row(UI.icon_tile("phone", UI.BEDROCK, 40, 20), "Мой мир из Minecraft", "Откройте мир в игре — LANTERN найдёт его сам", null)))
		else:
			add_child(UI.field("Мир", UI.segmented([["own", "Мой мир из Minecraft"], ["server", "Отдельный сервер"]], _kind, func(k): _kind = k)))
	_modes = UI.flow(8)
	add_child(UI.field("Режим игры", _modes))
	_render_modes()
	var cr := UI.hbox(12)
	var minus := UI.button("−", "secondary", func(): _set_cap(_cap - 1))
	minus.custom_minimum_size = Vector2(48, 48)
	minus.add_theme_font_size_override("font_size", 22)
	cr.add_child(minus)
	_cap_lbl = UI.label("", 20, UI.TEXT, UI.font_black)
	_cap_lbl.custom_minimum_size.x = 48
	_cap_lbl.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	cr.add_child(_cap_lbl)
	var plus := UI.button("+", "secondary", func(): _set_cap(_cap + 1))
	plus.custom_minimum_size = Vector2(48, 48)
	plus.add_theme_font_size_override("font_size", 22)
	cr.add_child(plus)
	add_child(UI.field("Мест (вместе с вами)", cr))
	_set_cap(_cap)
	var pw := UI.switch_row("Пароль", "Зайдут только те, кому вы его сказали", _pw_on, func(b): _pw_on = b; _pw.visible = b)
	add_child(pw.row)
	_pw = UI.line_edit(g.password, "Пароль (до 32 символов)")
	_pw.max_length = 32
	_pw.visible = _pw_on
	add_child(_pw)
	add_child(UI.field("Кто видит", UI.segmented([["all", "Видна всем"], ["code", "Только по коду"]], "all" if _public else "code", func(k): _public = k == "all")))
	var b := UI.button("Создать" if not live else "Сохранить", "primary", _create, "play" if not live else "check")
	b.custom_minimum_size.y = 56
	b.add_theme_font_size_override("font_size", 17)
	add_child(b)
	FX.stagger(self, 0.02)


func _render_modes() -> void:
	for c in _modes.get_children():
		c.queue_free()
	for m in Cfg.MODES:
		_modes.add_child(UI.chip(m[1], m[0] == _mode, func(): _mode = m[0]; _render_modes()))


func _set_cap(n: int) -> void:
	_cap = clampi(n, 2, 20)
	_cap_lbl.text = str(_cap)


func _create() -> void:
	var pw := _pw.text.strip_edges() if _pw_on else ""
	if _pw_on and pw == "":
		app.toast("Введите пароль или выключите его", UI.YELLOW)
		return
	Cfg.set_value("game", "title", _title.text.strip_edges())
	Cfg.set_value("game", "mode", _mode)
	Cfg.set_value("game", "cap", _cap)
	Cfg.set_value("game", "password", pw)
	Cfg.set_value("game", "public", _public)
	Cfg.set_value("own", "public", _public)
	if app.own.active or app.server.state in ["starting", "running"]:
		app.relay_host.set_settings({"mode": _mode, "cap": _cap, "password": pw})
		app.room.set_public(_public)
		app.go("host", true)
		return
	if _kind == "server" and not Cfg.mobile():
		app.go("setup", true, {"own": false})
	else:
		app.go("setup", true, {"own": true, "auto": true})
