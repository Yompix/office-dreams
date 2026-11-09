extends Control
## LANTERN — by Yompix. Корень приложения: шапка, навигация, общие службы.

var server: ServerHost
var relay_host := RelayHost.new()
var relay_join := RelayJoin.new()

var _content: Control
var _screen: Control
var _nav := {}
var _toast: PanelContainer
var _closing := false
var current := ""
var _brand: Control
var _me_btn: Button


func _ready() -> void:
	get_tree().set_auto_accept_quit(false)
	get_window().min_size = Vector2i(1120, 740)
	get_window().title = "LANTERN — by Yompix"
	server = ServerHost.new()
	server.name = "Server"
	add_child(server)
	server.state_changed.connect(_on_server_state)
	theme = UI.make_theme()
	Cfg.accent_changed.connect(func(_c): theme = UI.make_theme(); go(current, true))
	_build()
	Social.changed.connect(_on_social)
	Social.notice.connect(_on_notice)
	Social.failed.connect(func(what, text):
		if what != "register":
			toast(text, UI.YELLOW))
	relay_host.extra = {}
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
	var logo := UI.texture("res://assets/img/icon.png", 40, 40)
	row.add_child(logo)
	var titles := UI.vbox(-4)
	titles.add_child(UI.label("LANTERN", 20, UI.TEXT, UI.font_black))
	titles.add_child(UI.label("by Yompix", 12, Color("ffb648"), UI.font_bold))
	row.add_child(titles)
	_brand = UI.hbox(0)
	logo.reparent(_brand)
	titles.reparent(_brand)
	_brand.add_theme_constant_override("separation", 12)
	row.add_child(_brand)
	row.add_child(UI.spacer())
	for item in [["home", "Главная"], ["rooms", "Комнаты"], ["friends", "Друзья"], ["campfire", "Костёр"], ["quests", "Задания"], ["shop", "Магазин"], ["settings", "Настройки"]]:
		var b := UI.button(item[1], "ghost", go.bind(item[0]))
		b.custom_minimum_size = Vector2(0, 40)
		b.add_theme_font_size_override("font_size", 15)
		row.add_child(b)
		_nav[item[0]] = b
	_me_btn = Button.new()
	_me_btn.flat = true
	_me_btn.custom_minimum_size = Vector2(150, 44)
	_me_btn.mouse_default_cursor_shape = Control.CURSOR_POINTING_HAND
	_me_btn.pressed.connect(func(): go("profile"))
	row.add_child(_me_btn)
	_refresh_me()
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


## Сервер заработал → открываем комнату на relay; остановился → закрываем.
func _on_server_state(s: String) -> void:
	# При перезапуске сервера комната (и её код) остаются прежними.
	if s == "running" and server.opts.get("internet", true):
		var ed := server.edition
		if ed == "java" and server.opts.get("crossplay", false):
			ed = "both"
		var st: Dictionary = relay_host.stats()
		if st.state in ["off", "error"] or relay_host.edition != ed or relay_host.relay_url != Cfg.relay_url():
			relay_host.start(Cfg.relay_url(), ed, server.opts.get("world", "Мир"), 25565, 19132, host_extra())
	elif s == "error":
		relay_host.stop()
	update_presence()


func host_extra() -> Dictionary:
	var d := {"token": Social.token}
	if server.opts.get("public", false):
		d["public"] = {"title": server.opts.get("public_title", server.opts.get("world", "Мир")), "tags": server.opts.get("tags", []),
			"version": server.opts.get("mc_version", "") if server.edition == "java" else "", "slots": server.opts.get("max_players", 10)}
	return d


## Что видят друзья: «хостит Java 1.21», «играет в мир X».
func update_presence() -> void:
	if server.state in ["running", "starting"]:
		var ed := server.edition
		if ed == "java" and server.opts.get("crossplay", false):
			ed = "both"
		var v: String = server._install_info().get("mc_version", "") if server.edition == "java" else ""
		Social.set_presence("hosting", server.opts.get("world", "Мир"), ed, v, relay_host.stats().get("code", ""))
	elif relay_join.stats().state == "online":
		var st: Dictionary = relay_join.stats()
		Social.set_presence("playing", st.name, st.edition, "", relay_join.code)
	else:
		Social.set_presence("idle")


var _presence_tick := 0.0
var _last_presence := ""


func _process(d: float) -> void:
	_presence_tick += d
	if _presence_tick > 2.0:
		_presence_tick = 0
		var key := "%s|%s|%s" % [server.state, relay_host.stats().get("code", ""), relay_join.stats().state]
		if key != _last_presence:
			_last_presence = key
			update_presence()


func _on_social(what: String) -> void:
	if what in ["me", "eco", "conn"]:
		_refresh_me()
	if what == "conn" and Social.conn == "need_register" and Cfg.autotest == "":
		Overlays.register(self)


func _refresh_me() -> void:
	if not _me_btn:
		return
	for c in _me_btn.get_children():
		c.queue_free()
	var row := UI.hbox(8)
	row.mouse_filter = Control.MOUSE_FILTER_IGNORE
	row.set_anchors_preset(Control.PRESET_FULL_RECT)
	var lvl: int = int(Social.eco.get("lantern", {}).get("level", Social.me.get("lantern", {}).get("level", 1)))
	row.add_child(LanternIcon.new().setup(lvl, Social.eco.get("equip", Social.me.get("equip", "")), 40))
	var v := UI.vbox(-3)
	v.mouse_filter = Control.MOUSE_FILTER_IGNORE
	v.alignment = BoxContainer.ALIGNMENT_CENTER
	var nick := UI.label(Social.me.get("nick", "Профиль") if Social.online() else ("Профиль" if Social.conn != "connecting" else "…"), 14, UI.TEXT, UI.font_bold)
	nick.mouse_filter = Control.MOUSE_FILTER_IGNORE
	v.add_child(nick)
	var sp := UI.label("✦ %d искр" % int(Social.eco.get("sparks", 0)) if Social.online() else ("не в сети" if Social.conn == "off" else ""), 12, Color("ffb648"), UI.font_bold)
	sp.mouse_filter = Control.MOUSE_FILTER_IGNORE
	v.add_child(sp)
	row.add_child(v)
	_me_btn.add_child(row)


func _on_notice(d: Dictionary) -> void:
	match d.get("kind", ""):
		"request": toast("%s хочет дружить — откройте «Друзья»" % d.from.tag)
		"accepted": toast("%s принял(а) вашу заявку!" % d.from.tag, UI.JAVA)
		"invite": invite_popup(d)
		"achievement": toast("Достижение: «%s» · +%d искр · титул «%s»" % [d.name, d.sparks, d.title], Color("ffb648"))
		"referral": toast("Ваш друг %s наиграл час — вам +%d искр!" % [d.from.nick, d.sparks], Color("ffb648"))
		"purchased": toast("Готово! Покупка получена" + (" (ДЕМО)" if Social.demo else ""), UI.JAVA)
		"ad_reward": toast("+20 искр за просмотр", Color("ffb648"))
		"info": toast(d.text)


func invite_popup(d: Dictionary) -> void:
	var p := UI.panel(18, UI.CARD_HI, 16)
	var v := UI.vbox(10)
	p.add_child(v)
	v.add_child(UI.label("%s зовёт вас в мир «%s»" % [d.from.nick, d.get("world", "")], 16, UI.TEXT, UI.font_bold))
	var row := UI.hbox(8)
	row.add_child(UI.button("Зайти", "primary", func(): p.queue_free(); go("join", true, {"input": d.code})))
	row.add_child(UI.button("Не сейчас", "ghost", func(): p.queue_free()))
	v.add_child(row)
	add_child(p)
	p.position = Vector2(size.x - 420, 80)
	p.custom_minimum_size.x = 390
	get_tree().create_timer(20).timeout.connect(func(): if is_instance_valid(p): p.queue_free())


func go(name: String, force := false, arg: Variant = null) -> void:
	if name == current and not force and arg == null:
		return
	if name == "host" and arg == null and not server.state in ["installing", "starting", "running", "stopping", "error"]:
		name = "home"
		if current == "home" and not force:
			toast("Сначала выберите Java или Bedrock и нажмите «Создать мир»")
			return
	current = name
	_brand.modulate.a = 0.0 if name == "home" else 1.0
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
		"rooms": scr = preload("res://scripts/screens/rooms.gd").new()
		"friends": scr = preload("res://scripts/screens/friends.gd").new()
		"campfire": scr = preload("res://scripts/screens/campfire.gd").new()
		"quests": scr = preload("res://scripts/screens/quests.gd").new()
		"shop": scr = preload("res://scripts/screens/shop.gd").new()
		"profile": scr = preload("res://scripts/screens/profile.gd").new()
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
