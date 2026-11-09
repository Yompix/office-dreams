extends Control
## «Играем вместе» — by Yompix. Корень приложения: шапка, навигация, общие службы.

var server: ServerHost
var relay_host := RelayHost.new()
var relay_join := RelayJoin.new()

var _content: Control
var _screen: Control
var _nav := {}
var _toast: PanelContainer
var _closing := false
var current := ""


func _ready() -> void:
	get_tree().set_auto_accept_quit(false)
	get_window().min_size = Vector2i(1120, 740)
	get_window().title = "Играем вместе — by Yompix"
	server = ServerHost.new()
	server.name = "Server"
	add_child(server)
	theme = UI.make_theme()
	Cfg.accent_changed.connect(func(_c): theme = UI.make_theme(); go(current, true))
	_build()
	if Cfg.autotest != "":
		var t := preload("res://tests/autotest.gd").new()
		t.name = "Autotest"
		add_child(t)
		t.run(self)
	else:
		go("home")


func _build() -> void:
	var bg := ColorRect.new()
	bg.color = UI.BG
	bg.set_anchors_preset(Control.PRESET_FULL_RECT)
	add_child(bg)
	var deco := preload("res://scripts/ui/backdrop.gd").new()
	deco.set_anchors_preset(Control.PRESET_FULL_RECT)
	add_child(deco)
	var root := UI.vbox(0)
	root.set_anchors_preset(Control.PRESET_FULL_RECT)
	add_child(root)
	# --- шапка
	var bar := MarginContainer.new()
	for side in ["left", "right"]:
		bar.add_theme_constant_override("margin_" + side, 28)
	bar.add_theme_constant_override("margin_top", 16)
	bar.add_theme_constant_override("margin_bottom", 8)
	root.add_child(bar)
	var row := UI.hbox(14)
	bar.add_child(row)
	var logo := UI.texture("res://assets/img/icon.png", 44, 44)
	row.add_child(logo)
	var titles := UI.vbox(-4)
	titles.add_child(UI.label("Играем вместе", 22, UI.TEXT, UI.font_black))
	titles.add_child(UI.label("by Yompix", 13, UI.accent(), UI.font_bold))
	row.add_child(titles)
	row.add_child(UI.spacer())
	for item in [["home", "Главная"], ["join", "Зайти к другу"], ["host", "Мой мир"], ["settings", "Настройки"]]:
		var b := UI.button(item[1], "ghost", go.bind(item[0]))
		b.custom_minimum_size = Vector2(0, 42)
		row.add_child(b)
		_nav[item[0]] = b
	# --- содержимое
	_content = MarginContainer.new()
	_content.size_flags_vertical = Control.SIZE_EXPAND_FILL
	for side in ["left", "right"]:
		_content.add_theme_constant_override("margin_" + side, 28)
	_content.add_theme_constant_override("margin_top", 8)
	_content.add_theme_constant_override("margin_bottom", 22)
	root.add_child(_content)
	# --- всплывашка
	_toast = UI.panel(14, UI.CARD_HI, 14)
	_toast.add_child(UI.label("", 15))
	_toast.modulate.a = 0
	_toast.mouse_filter = Control.MOUSE_FILTER_IGNORE
	add_child(_toast)


func go(name: String, force := false, arg: Variant = null) -> void:
	if name == current and not force and arg == null:
		return
	if name == "host" and arg == null and not server.state in ["installing", "starting", "running", "stopping", "error"]:
		name = "home"
		if current == "home" and not force:
			toast("Сначала выберите Java или Bedrock и нажмите «Создать мир»")
			return
	current = name
	for k in _nav:
		var on: bool = k == name
		var b: Button = _nav[k]
		b.add_theme_color_override("font_color", UI.accent() if on else UI.MUTED)
		b.add_theme_color_override("font_hover_color", UI.accent() if on else UI.TEXT)
	if _screen:
		_screen.queue_free()
	var scr: Control
	match name:
		"home": scr = preload("res://scripts/screens/home.gd").new()
		"setup": scr = preload("res://scripts/screens/setup.gd").new()
		"host": scr = preload("res://scripts/screens/host.gd").new()
		"join": scr = preload("res://scripts/screens/join.gd").new()
		"settings": scr = preload("res://scripts/screens/settings.gd").new()
	scr.set("app", self)
	if arg != null:
		scr.set("arg", arg)
	_screen = scr
	_content.add_child(scr)
	scr.modulate.a = 0
	scr.position.y = 14
	var tw := scr.create_tween().set_parallel().set_trans(Tween.TRANS_CUBIC).set_ease(Tween.EASE_OUT)
	tw.tween_property(scr, "modulate:a", 1.0, 0.25)
	tw.tween_property(scr, "position:y", 0.0, 0.3)


func toast(text: String, color := UI.TEXT) -> void:
	var l: Label = _toast.get_child(0)
	l.text = text
	l.add_theme_color_override("font_color", color)
	_toast.reset_size()
	await get_tree().process_frame
	_toast.position = Vector2((size.x - _toast.size.x) / 2, size.y - _toast.size.y - 28)
	var tw := create_tween()
	tw.tween_property(_toast, "modulate:a", 1.0, 0.2)
	tw.tween_interval(2.6)
	tw.tween_property(_toast, "modulate:a", 0.0, 0.4)


func _notification(what: int) -> void:
	if what == NOTIFICATION_WM_CLOSE_REQUEST:
		_quit()


func _quit() -> void:
	if _closing:
		return
	_closing = true
	relay_join.stop()
	relay_host.stop()
	if server.is_alive():
		var ov := ColorRect.new()
		ov.color = Color(0, 0, 0, 0.75)
		ov.set_anchors_preset(Control.PRESET_FULL_RECT)
		var l := UI.h2("Сохраняем мир и выключаем сервер…")
		l.set_anchors_preset(Control.PRESET_CENTER)
		l.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
		ov.add_child(l)
		add_child(ov)
		server.stop()
		var t0 := Time.get_ticks_msec()
		while server.is_alive() and Time.get_ticks_msec() - t0 < 60000:
			await get_tree().process_frame
	get_tree().quit()


func _exit_tree() -> void:
	relay_join.stop()
	relay_host.stop()
