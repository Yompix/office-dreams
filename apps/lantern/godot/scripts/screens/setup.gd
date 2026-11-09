extends HBoxContainer
## Настройка нового мира перед запуском.

var app
var arg := {}

var edition := "java"
var _name: LineEdit
var _version: OptionButton
var _versions: PackedStringArray = []
var _mode: OptionButton
var _diff: OptionButton
var _players: SpinBox
var _online := true
var _lic_btns := []
var _cross: CheckButton
var _internet: CheckButton
var _mem: HSlider
var _mem_lbl: Label
var _eula: CheckBox
var _start: Button
var _sum: Label

const MODES := [["survival", "Выживание"], ["creative", "Творческий"], ["adventure", "Приключение"]]
const DIFFS := [["peaceful", "Мирная"], ["easy", "Лёгкая"], ["normal", "Нормальная"], ["hard", "Сложная"]]


func _ready() -> void:
	edition = arg.get("edition", "java")
	var saved: Dictionary = Cfg.get_value("host", edition, {})
	add_theme_constant_override("separation", 20)
	var color := UI.JAVA if edition == "java" else UI.BEDROCK
	# ---------------- форма
	var scroll := ScrollContainer.new()
	scroll.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	scroll.size_flags_stretch_ratio = 1.6
	scroll.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	add_child(scroll)
	var form := UI.vbox(16)
	form.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	scroll.add_child(form)
	var top := UI.hbox(14)
	top.add_child(UI.texture("res://assets/img/mini_grass.png" if edition == "java" else "res://assets/img/mini_stone.png", 56, 56))
	var tt := UI.vbox(0)
	tt.add_child(UI.label("Новый мир", 15, color, UI.font_bold))
	tt.add_child(UI.h1("Java Edition" if edition == "java" else "Bedrock Edition"))
	top.add_child(tt)
	form.add_child(top)

	var p1 := UI.panel(22)
	var g := GridContainer.new()
	g.columns = 2
	g.add_theme_constant_override("h_separation", 18)
	g.add_theme_constant_override("v_separation", 16)
	p1.add_child(g)
	form.add_child(p1)
	_name = UI.line_edit(saved.get("world", "Наш мир"), "Как назвать мир")
	_name.max_length = 32
	_name.text_changed.connect(func(_t): _update())
	_name.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	g.add_child(_wide(UI.field("Название мира", _name)))
	if edition == "java":
		_version = UI.option(["Последняя версия"])
		_version.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		g.add_child(_wide(UI.field("Версия Minecraft", _version, "Должна совпадать с версией у друзей")))
		_load_versions(saved.get("version", "latest"))
	else:
		g.add_child(_wide(UI.field("Версия", UI.label("Последняя (обновляется сама)", 16, UI.TEXT, UI.font_bold), "Bedrock-сервер всегда свежий — как в магазине приложений")))
	_mode = UI.option(MODES.map(func(x): return x[1]), _idx(MODES, saved.get("gamemode", "survival")))
	_mode.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_diff = UI.option(DIFFS.map(func(x): return x[1]), _idx(DIFFS, saved.get("difficulty", "normal")))
	_diff.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	g.add_child(_wide(UI.field("Режим игры", _mode)))
	g.add_child(_wide(UI.field("Сложность", _diff)))
	_players = SpinBox.new()
	_players.min_value = 2
	_players.max_value = 50
	_players.value = saved.get("max_players", 10)
	_players.custom_minimum_size.y = 44
	_players.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	g.add_child(_wide(UI.field("Сколько игроков максимум", _players)))
	if edition == "java":
		var total := int(OS.get_memory_info().get("physical", 8 << 30) / (1 << 20))
		_mem = HSlider.new()
		_mem.min_value = 1024
		_mem.max_value = clampi(total / 2, 2048, 16384)
		_mem.step = 512
		_mem.value = clampi(int(saved.get("memory_mb", 2048 if total < 8000 else 3072)), 1024, int(_mem.max_value))
		_mem.custom_minimum_size.y = 28
		_mem_lbl = UI.label("", 15, UI.TEXT, UI.font_bold)
		_mem.value_changed.connect(func(v): _mem_lbl.text = "%.1f ГБ памяти для сервера" % (v / 1024.0))
		_mem.value_changed.emit(_mem.value)
		var mv := UI.vbox(6)
		mv.add_child(_mem_lbl)
		mv.add_child(_mem)
		g.add_child(_wide(UI.field("Память", mv, "Больше друзей и модов — больше памяти")))

	if edition == "java":
		var p2 := UI.panel(22)
		var v2 := UI.vbox(12)
		p2.add_child(v2)
		v2.add_child(UI.h3("Как друзья запускают Minecraft?"))
		var lr := UI.hbox(14)
		v2.add_child(lr)
		_online = saved.get("online_mode", true)
		lr.add_child(_choice(true, "Лицензия", "У всех купленный Minecraft. Ники защищены — чужой не зайдёт под вашим именем."))
		lr.add_child(_choice(false, "Пиратка / TLauncher", "Можно без лицензии. Но любой может взять любой ник — не давайте код посторонним."))
		_update_choice()
		form.add_child(p2)

	var p3 := UI.panel(22)
	var v3 := UI.vbox(10)
	p3.add_child(v3)
	if edition == "java":
		_cross = CheckButton.new()
		_cross.text = "Пустить друзей с телефона (Bedrock)"
		_cross.button_pressed = saved.get("crossplay", true)
		_cross.add_theme_font_override("font", UI.font_bold)
		_cross.toggled.connect(func(_b): _update())
		v3.add_child(_cross)
		v3.add_child(UI.para("Скачаем Geyser и Floodgate: друзья с телефона, планшета и консолей зайдут в этот же мир по тому же коду.", 14, UI.DIM))
		v3.add_child(UI.gap(4))
	_internet = CheckButton.new()
	_internet.text = "Друзья через интернет (код комнаты)"
	_internet.button_pressed = saved.get("internet", true)
	_internet.add_theme_font_override("font", UI.font_bold)
	_internet.toggled.connect(func(_b): _update())
	v3.add_child(_internet)
	v3.add_child(UI.para("Друзья вводят короткий код — и мир появляется у них в игре. Без VPN, Radmin и настройки роутера. Друзья в той же Wi-Fi сети увидят мир и без кода.", 14, UI.DIM))
	form.add_child(p3)

	# ---------------- правая колонка
	var side := UI.panel(24)
	side.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	side.size_flags_vertical = Control.SIZE_EXPAND_FILL
	add_child(side)
	var sv := UI.vbox(14)
	side.add_child(sv)
	sv.add_child(UI.h2("Всё готово к запуску"))
	_sum = UI.para("", 15, UI.MUTED)
	sv.add_child(_sum)
	sv.add_child(UI.spacer(false))
	var legal := UI.panel(16, Color("121620"), 14)
	var lv := UI.vbox(8)
	legal.add_child(lv)
	_eula = CheckBox.new()
	_eula.text = "Я принимаю Лицензионное соглашение Minecraft" + ("" if edition == "java" else " и Политику конфиденциальности")
	_eula.button_pressed = saved.get("eula", false)
	_eula.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	_eula.custom_minimum_size.x = 200
	_eula.toggled.connect(func(_b): _update())
	lv.add_child(_eula)
	var links := UI.hbox(6)
	links.add_child(UI.button("Прочитать EULA ↗", "ghost", func(): OS.shell_open("https://aka.ms/MinecraftEULA")))
	if edition == "bedrock":
		links.add_child(UI.button("Конфиденциальность ↗", "ghost", func(): OS.shell_open("https://go.microsoft.com/fwlink/?LinkId=521839")))
	lv.add_child(links)
	lv.add_child(UI.para("Без согласия Mojang не разрешает запускать сервер. Мы ничего не отправляем о вас — никакой телеметрии.", 13, UI.DIM))
	sv.add_child(legal)
	_start = UI.button("Запустить мир", "primary", _go)
	_start.custom_minimum_size.y = 58
	_start.add_theme_font_size_override("font_size", 19)
	sv.add_child(_start)
	sv.add_child(UI.button("← Назад", "ghost", func(): app.go("home")))
	_update()


func _wide(c: Control) -> Control:
	c.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	return c


static func _idx(list: Array, key: String) -> int:
	for i in list.size():
		if list[i][0] == key:
			return i
	return 0


func _choice(val: bool, title: String, desc: String) -> Control:
	var b := Button.new()
	b.toggle_mode = true
	b.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	b.custom_minimum_size.y = 108
	b.mouse_default_cursor_shape = Control.CURSOR_POINTING_HAND
	var v := UI.vbox(4)
	v.mouse_filter = Control.MOUSE_FILTER_IGNORE
	v.set_anchors_preset(Control.PRESET_FULL_RECT)
	v.offset_left = 16
	v.offset_top = 14
	v.offset_right = -16
	v.add_child(UI.label(title, 17, UI.TEXT, UI.font_bold))
	var d := UI.para(desc, 13, UI.MUTED)
	d.mouse_filter = Control.MOUSE_FILTER_IGNORE
	v.add_child(d)
	for c in v.get_children():
		c.mouse_filter = Control.MOUSE_FILTER_IGNORE
	b.add_child(v)
	b.set_meta("val", val)
	b.pressed.connect(func(): _online = val; _update_choice(); _update())
	_lic_btns.append(b)
	return b


func _update_choice() -> void:
	for b in _lic_btns:
		var on: bool = b.get_meta("val") == _online
		var c := UI.accent()
		for st in ["normal", "hover", "pressed", "hover_pressed", "focus"]:
			b.add_theme_stylebox_override(st, UI.box(Color(c, 0.12) if on else Color("121620"), 14, 0, c if on else UI.LINE, 2 if on else 1))


func _load_versions(want: String) -> void:
	var r: Dictionary = await Net.get_json(ServerHost.FILL)
	if not is_inside_tree() or not r.ok:
		return
	_versions = ServerHost.paper_versions(r.data)
	_version.clear()
	_version.add_item("Последняя (%s)" % (_versions[0] if _versions.size() > 0 else "?"))
	for v in _versions.slice(0, 40):
		_version.add_item(v)
	var i := _versions.find(want)
	_version.select(i + 1 if i >= 0 else 0)


func _opts() -> Dictionary:
	var o := {
		"world": _name.text.strip_edges() if _name.text.strip_edges() != "" else "Наш мир",
		"gamemode": MODES[_mode.selected][0],
		"difficulty": DIFFS[_diff.selected][0],
		"max_players": int(_players.value),
		"internet": _internet.button_pressed,
		"eula": _eula.button_pressed,
	}
	if edition == "java":
		o["version"] = "latest" if _version.selected <= 0 else _versions[_version.selected - 1]
		o["online_mode"] = _online
		o["crossplay"] = _cross.button_pressed
		o["memory_mb"] = int(_mem.value)
	return o


func _update() -> void:
	if edition == "java" and _version:
		_version.disabled = _cross.button_pressed
		if _cross.button_pressed:
			_version.select(0)
		_version.tooltip_text = "С кроссплеем — только последняя версия (так требует Geyser)" if _cross.button_pressed else ""
	var o := _opts()
	var lines := PackedStringArray()
	lines.append("• Мир «%s», %s, сложность — %s" % [o.world, MODES[_mode.selected][1].to_lower(), DIFFS[_diff.selected][1].to_lower()])
	if edition == "java":
		lines.append("• Скачаем сервер Paper и, если нужно, Java — один раз")
		lines.append("• Вход: " + ("только лицензия" if o.online_mode else "лицензия и TLauncher"))
		if o.crossplay:
			lines.append("• Кроссплей: зайдут и с телефонов (Geyser, последняя версия)")
	else:
		lines.append("• Скачаем официальный Bedrock Dedicated Server — один раз")
	lines.append("• " + ("Получите код комнаты для друзей" if o.internet else "Только для друзей в вашей Wi-Fi сети"))
	_sum.text = "\n".join(lines)
	_start.disabled = not o.eula
	_start.tooltip_text = "" if o.eula else "Сначала примите EULA"


func _go() -> void:
	var o := _opts()
	if not o.eula:
		app.toast("Нужно принять EULA", UI.YELLOW)
		return
	Cfg.set_value("host", edition, o)
	app.server.host(edition, o)
	app.go("host", false, {})
