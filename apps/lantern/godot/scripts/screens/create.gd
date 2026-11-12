extends VBoxContainer
## «Создать игру» (1.6 — проще некуда): название → «Создать».
## ПК: сразу общий сервер для всех (Java 1.8+ и телефоны/консоли на актуальном Bedrock), новейшая версия, всё само.
## Телефон: мир из самого Minecraft (LANTERN находит его сам). Режим, места, пароль, «свой мир» на ПК,
## тонкая настройка сервера — под «Ещё настройки». Если игра уже идёт — настройки применяются сразу.

var app
var arg

var _title: LineEdit
var _kind := "own"
var _mode := "survival"
var _cap := 6
var _pw_on := false
var _pw: LineEdit
var _public := true
var _access_hint: Label
var _cap_lbl: Label
var _modes: HFlowContainer
var _eula: CheckBox


func _ready() -> void:
	add_theme_constant_override("separation", 16)
	var g := Cfg.game()
	_mode = g.mode
	_cap = clampi(g.cap, 2, 20)
	if Cfg.mobile():
		_cap = 10     # мир с телефона: как в самом Minecraft — до 10 игроков
	_pw_on = g.password != ""
	_public = g.public
	var live: bool = app.own.active or app.server.state in ["starting", "running"]
	app.set_title("Создать игру" if not live else "Настройки игры", "")
	var nick := String(Social.me.get("nick", "")) if Social.me is Dictionary else ""
	var def_title: String = g.title if g.title != "" else ("Мир " + nick if nick != "" else "Наш мир")
	_title = UI.line_edit(def_title, "Например: Выживание с друзьями")
	_title.max_length = 40
	add_child(UI.field("Название игры", _title))
	# --- доступ: открытая / закрытая игра и пароль — сразу на виду (12 ноя)
	_access_hint = UI.para("", 14, UI.MUTED)
	add_child(UI.field("Кто может зайти", UI.segmented([["all", "Открытая"], ["code", "Закрытая"]], "all" if _public else "code", func(k): _public = k == "all"; _access_text())))
	add_child(_access_hint)
	_access_text()
	var pw := UI.switch_row("Пароль", "Зайдут только те, кому вы его сказали", _pw_on, func(b): _pw_on = b; _pw.visible = b)
	add_child(pw.row)
	_pw = UI.line_edit(g.password, "Пароль (до 32 символов)")
	_pw.max_length = 32
	_pw.visible = _pw_on
	add_child(_pw)
	var simple := not live and not Cfg.mobile()
	if not live:
		add_child(compat_card(not Cfg.mobile()))
	if simple:
		_kind = "server"
		_eula = CheckBox.new()
		_eula.text = "Я принимаю Лицензионное соглашение Minecraft (EULA)"
		_eula.button_pressed = bool(Cfg.get_value("host", "java", {}).get("eula", false))
		_eula.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
		_eula.custom_minimum_size.x = 200
		var ev := UI.vbox(2)
		ev.add_child(_eula)
		ev.add_child(UI.button("Прочитать EULA", "link", func(): OS.shell_open("https://aka.ms/MinecraftEULA")))
		add_child(ev)
	# --- все настройки видны сразу, без «Ещё настройки» (12 ноя)
	var adv := UI.vbox(16)
	add_child(adv)
	if not live and not Cfg.mobile():
		adv.add_child(UI.field("Где идёт игра", UI.segmented([["server", "Сервер LANTERN (для всех)"], ["own", "Мой мир из Minecraft"]], _kind, func(k): _kind = k)))
	_modes = UI.flow(8)
	adv.add_child(UI.field("Режим игры", _modes))
	_render_modes()
	if Cfg.mobile():
		# мир с телефона: до 10 игроков — столько пускает сам Minecraft
		adv.add_child(UI.field("Мест", UI.para("До 10 игроков.", 15, UI.TEXT)))
	else:
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
		adv.add_child(UI.field("Мест (вместе с вами)", cr))
	_set_cap(_cap)
	if not live and not Cfg.mobile():
		adv.add_child(UI.button("Подробная настройка сервера (версия, память, издание…)", "secondary", func(): _save(); app.go("setup", true, {"edition": "java", "step": 2}), "settings"))
	var main_btn := UI.button("Создать" if not live else "Сохранить", "primary", _create, "play" if not live else "check")
	main_btn.custom_minimum_size.y = 56
	main_btn.add_theme_font_size_override("font_size", 17)
	add_child(main_btn)
	FX.stagger(self, 0.02)


## Совместимость — в одном месте, простыми словами (и с картинкой из значков).
static func compat_card(pc: bool) -> Control:
	var p := UI.panel(16, Color(1, 1, 1, 0.03))
	var v := UI.vbox(10)
	p.add_child(v)
	var row := UI.flow(14)
	for it in [["desktop", "ПК (Java)"], ["phone", "Телефоны"], ["play", "Консоли"], ["desktop", "Windows (Bedrock)"]]:
		var c := UI.hbox(6)
		c.add_child(UI.icon(it[0], 20, UI.JAVA if pc else UI.BEDROCK))
		c.add_child(UI.label(it[1] + " ✓", 15, UI.TEXT, UI.font_bold))
		row.add_child(c)
	v.add_child(row)
	if pc:
		v.add_child(UI.para("Все в одном мире: Java от 1.8.9 до новейшей, телефоны, Windows и консоли — с актуальной версией Minecraft. Ничего настраивать не нужно.", 14, UI.MUTED))
	else:
		v.add_child(UI.para("Вы создаёте мир в Minecraft на телефоне — зайдут телефоны, Windows (Bedrock) и консоли с той же версией Minecraft. Чтобы зашли и друзья с Java (ПК), создайте игру в LANTERN на компьютере.", 14, UI.MUTED))
	return p


func _access_text() -> void:
	if is_instance_valid(_access_hint):
		_access_hint.text = "Игра появится в списке «Игры» у всех, зайти сможет любой." if _public else "В списке её нет. Зайдут только те, кому вы отправите код или ссылку."


func _render_modes() -> void:
	for c in _modes.get_children():
		c.queue_free()
	for m in Cfg.MODES:
		_modes.add_child(UI.chip(m[1], m[0] == _mode, func(): _mode = m[0]; _render_modes()))


func _set_cap(n: int) -> void:
	_cap = clampi(n, 2, 20)
	if is_instance_valid(_cap_lbl):
		_cap_lbl.text = str(_cap)


func _save() -> bool:
	var pw := _pw.text.strip_edges() if _pw_on else ""
	if _pw_on and pw == "":
		app.toast("Введите пароль или выключите его", UI.YELLOW)
		return false
	Cfg.set_value("game", "title", _title.text.strip_edges())
	Cfg.set_value("game", "mode", _mode)
	Cfg.set_value("game", "cap", _cap)
	Cfg.set_value("game", "password", pw)
	Cfg.set_value("game", "public", _public)
	Cfg.set_value("own", "public", _public)
	return true


func _create() -> void:
	if not _save():
		return
	var pw := _pw.text.strip_edges() if _pw_on else ""
	if app.own.active or app.server.state in ["starting", "running"]:
		app.relay_host.set_settings({"mode": _mode, "cap": _cap, "password": pw})
		app.room.set_public(_public)
		app.go("host", true)
		return
	if _kind == "server" and not Cfg.mobile():
		if _eula and not _eula.button_pressed:
			app.toast("Отметьте согласие с соглашением Minecraft — без него Mojang не разрешает запускать сервер", UI.YELLOW)
			return
		app.server.host("java", universal_opts(_title.text.strip_edges(), _mode, _cap, _public))
		app.own.stop()
		app.go("host", false, {"fresh": true})
	else:
		app.go("setup", true, {"own": true, "auto": true, "launch": true})



## Универсальный сервер «для всех»: новейший Paper + Via* (Java 1.8+) + Geyser/Floodgate (телефоны, консоли).
## Прошлые ручные настройки (память, проверка аккаунтов, белый список) сохраняются.
static func universal_opts(title: String, mode: String, cap: int, public: bool) -> Dictionary:
	var o: Dictionary = (Cfg.get_value("host", "java", {}) as Dictionary).duplicate()
	o.merge({"world": title if title != "" else "Наш мир", "difficulty": "normal", "max_players": 10, "online_mode": true, "memory_mb": 2048}, false)
	o["world"] = title if title != "" else String(o.world)
	o["gamemode"] = "creative" if mode == "creative" else "survival"
	o["max_players"] = maxi(cap, 2)
	o["internet"] = true
	o["eula"] = true
	o["public"] = public
	o["public_title"] = title
	o["version"] = "latest"
	o["crossplay"] = true
	o["any_version"] = true
	Cfg.set_value("host", "java", o)
	Cfg.set_value("host", "last_edition", "java")
	return o
