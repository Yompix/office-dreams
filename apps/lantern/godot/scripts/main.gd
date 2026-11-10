extends Control
## LANTERN — by Yompix. Корень приложения: оболочка (верхняя панель + нижнее меню на телефоне,
## боковая панель на ПК), навигация с историей «Назад», переходы, всплывашки, общие службы.

var server: ServerHost
var relay_host := RelayHost.new()
var relay_join := RelayJoin.new()
var own: OwnWorld                  # «Мой мир из Minecraft» — мир, открытый в самой игре
var room: RoomControl              # хозяин мира: статус «друзья могут зайти», игроки, окошко поверх игры

var _content: MarginContainer      # сюда кладётся экран (автотесты ищут переполнение здесь)
var _screen: Control               # сам экран (без обёртки прокрутки)
var _screen_root: Control          # экран или его ScrollContainer
var _nav := {}                     # ключ → кнопка меню
var _toast: PanelContainer
var _closing := false
var current := ""
var _me_btn: Button
var _compact := false
var _arg: Variant = null
var _deeplink_seen := ""

var _bar_title: Label
var _bar_sub: Label
var _bar_back: Button
var _bar_brand: Control
var _bar_actions: HBoxContainer
var _sparks: Button
var _nav_pill: Panel
var _back_cb := Callable()
var _stack: Array = []             # история для «Назад»: [[экран, arg], …]
var _going_back := false
var _safe := Vector2.ZERO          # x — сверху, y — снизу (dp)
var _last_sparks := -1
var _nav_bar: Control
var _kb_gap: Control
var _kb_h := 0.0
var _has_actions := false

## На телефоне эти экраны прокручиваются целиком (у остальных — свои списки с прокруткой).
const SCROLL_IN_COMPACT := ["home", "join", "setup", "profile", "host", "quests", "shop", "create"]
const TITLES := {"home": "Игры", "create": "Создать игру", "setup": "Создать мир", "host": "Мой мир", "join": "Зайти к другу",
	"settings": "Настройки", "friends": "Друзья", "campfire": "Костёр",
	"quests": "Задания", "shop": "Магазин", "profile": "Профиль", "syscheck": "Проверка системы", "backups": "Копии миров"}
## Корневые разделы телефона (без стрелки «Назад»)
const ROOTS := ["home", "friends", "profile"]
## Узкая колонка на широком экране — для форм и списков настроек
const NARROW := ["settings", "backups", "syscheck", "profile", "join", "setup", "create"]


const TOUCH_DEADZONE := 14   # px: меньше — это ещё нажатие, больше — уже прокрутка


func _ready() -> void:
	get_tree().set_auto_accept_quit(false)
	if OS.has_feature("mobile") or Cfg.autotest_args.has("density"):
		_apply_mobile_scale()
	elif not Cfg.phone_preview():
		get_window().min_size = Vector2i(1024, 700)
	get_window().size_changed.connect(_on_resize)
	get_viewport().gui_focus_changed.connect(func(_c): if _kb_h > 0.0: _kb_reveal.call_deferred())
	get_window().title = "LANTERN — by Yompix"
	get_tree().node_added.connect(_on_node_added)
	server = ServerHost.new()
	server.name = "Server"
	add_child(server)
	server.state_changed.connect(_on_server_state)
	own = OwnWorld.new()
	own.name = "OwnWorld"
	own.app = self
	add_child(own)
	room = RoomControl.new()
	room.name = "RoomControl"
	room.app = self
	add_child(room)
	theme = UI.make_theme()
	Cfg.accent_changed.connect(func(_c): theme = UI.make_theme(); _rebuild())
	_build()
	Social.changed.connect(_on_social)
	Social.notice.connect(_on_notice)
	Social.failed.connect(func(what, text):
		# служебные коды (need_hello, bad_token…) людям не показываем
		if what != "register" and not RegEx.create_from_string("^[a-z_]+$").search(String(text)):
			toast(text, UI.YELLOW))
	relay_host.extra = {}
	if Cfg.autotest != "":
		var t: Node = load("res://tests/autotest.gd").new()
		t.name = "Autotest"
		add_child(t)
		t.run(self)
	elif not open_deeplink(DeepLink.from_launch()):
		go("home")
	if Cfg.autotest_args.has("startup"):
		_measure_startup()


## --startup=1: время до первого кадра и средний кадр на главной (для отчёта о скорости), затем выход
func _measure_startup() -> void:
	await RenderingServer.frame_post_draw
	var first := Time.get_ticks_msec()
	OS.low_processor_usage_mode = false   # на простое кадры не рисуются вовсе — для замера рисуем подряд
	var t0 := Time.get_ticks_usec()
	var n := 0
	var worst := 0.0
	var last := t0
	while n < 120:
		await RenderingServer.frame_post_draw
		var now := Time.get_ticks_usec()
		worst = maxf(worst, (now - last) / 1000.0)
		last = now
		n += 1
	print("[startup] first frame %d ms; avg frame %.1f ms, worst %.1f ms (%s)" % [first, (Time.get_ticks_usec() - t0) / 1000.0 / n, worst, RenderingServer.get_current_rendering_method()])
	get_tree().quit(0)


## Телефон: 1 логическая единица = 1 dp, чтобы шрифты и кнопки были привычного размера
## и в портрете, и лёжа (а не «ПК 1280×800, уменьшенный до ширины телефона»).
## На ПК для скриншотов: --density=2.75 (окно 1080×2400 = телефон 393×873 dp).
func _apply_mobile_scale() -> void:
	var w := get_window()
	var k := 2.0
	if Cfg.autotest_args.has("density"):
		k = float(Cfg.autotest_args.density)
	else:
		var dpi := float(DisplayServer.screen_get_dpi())
		k = clampf(dpi / 160.0, 1.0, 4.0) if dpi > 0 else 2.0
	w.content_scale_mode = Window.CONTENT_SCALE_MODE_CANVAS_ITEMS
	w.content_scale_aspect = Window.CONTENT_SCALE_ASPECT_EXPAND
	w.content_scale_size = Vector2i(Vector2(w.size) / k)


## Вырез камеры и полоска жестов: отступы безопасной зоны в dp.
func _safe_insets() -> Vector2:
	if Cfg.autotest_args.has("safe"):
		var p := String(Cfg.autotest_args.safe).split(",")
		return Vector2(float(p[0]), float(p[1]) if p.size() > 1 else 0.0)
	if not OS.has_feature("mobile"):
		return Vector2.ZERO
	var sa := DisplayServer.get_display_safe_area()
	var scr := DisplayServer.screen_get_size()
	var w := get_window()
	var k := float(w.size.x) / maxf(1.0, float(w.content_scale_size.x)) if w.content_scale_size.x > 0 else 1.0
	if sa.size.x <= 0 or scr.y <= 0:
		return Vector2(24, 0)
	return Vector2(maxf(0, sa.position.y) / k, maxf(0, scr.y - sa.end.y) / k)


func _on_resize() -> void:
	if OS.has_feature("mobile") or Cfg.autotest_args.has("density"):
		_apply_mobile_scale()
	await get_tree().process_frame
	if UI.compact() != _compact and _content:
		_rebuild()


## Повернули телефон / сузили окно / сменили акцент — пересобираем оболочку и текущий экран.
func _rebuild() -> void:
	for c in get_children():
		if c.name != "Autotest" and c != server:
			c.queue_free()
	_screen = null
	_screen_root = null
	_nav.clear()
	await get_tree().process_frame
	theme = UI.make_theme()
	_build()
	go(current if current != "" else "home", true, _arg)


## На телефоне полосы прокрутки не показываем — листают пальцем (с инерцией).
func _on_node_added(n: Node) -> void:
	# Телефон: пальцем листаем откуда угодно — и с кнопок, и с карточек. Кнопки пропускают касание к прокрутке
	# (PASS); начали листать (дальше «мёртвой зоны») — нажатие отменяется, кнопка не срабатывает.
	if Cfg.mobile():
		if n is Control and not (n is ScrollContainer or n is ScrollBar or n is TextEdit or n is RichTextLabel or n is Slider):
			_touch_pass.call_deferred(n)
		if n is ScrollContainer:
			(n as ScrollContainer).scroll_deadzone = TOUCH_DEADZONE
			(n as ScrollContainer).scroll_started.connect(UI.mark_scroll)
	if _compact and n is ScrollContainer:
		var sc := n as ScrollContainer
		(func():
			if not is_instance_valid(sc):
				return
			if sc.vertical_scroll_mode == ScrollContainer.SCROLL_MODE_AUTO:
				sc.vertical_scroll_mode = ScrollContainer.SCROLL_MODE_SHOW_NEVER
			if sc.horizontal_scroll_mode == ScrollContainer.SCROLL_MODE_AUTO:
				sc.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_SHOW_NEVER).call_deferred()


## Элемент внутри прокрутки не «глотает» касание: палец, начавший на кнопке или карточке, листает список.
func _touch_pass(o: Variant) -> void:
	if not is_instance_valid(o):   # успел исчезнуть до отложенного вызова
		return
	var c := o as Control
	if c == null or c.mouse_filter != Control.MOUSE_FILTER_STOP:
		return
	var p := c.get_parent()
	while p:
		if p is ScrollContainer:
			c.mouse_filter = Control.MOUSE_FILTER_PASS
			return
		if p is Window:
			return
		p = p.get_parent()


## lantern://join?…&c=КОД — зайти к другу; lantern://friend?tag=Ник%231234 — добавить в друзья.
func open_deeplink(url: String) -> bool:
	if url == "" or url == _deeplink_seen:
		return false
	_deeplink_seen = url
	var d := DeepLink.parse(url)
	match d.get("kind", ""):
		"join":
			go("join", true, {"input": url, "edition": d.get("edition", "") if d.get("edition", "") != "" else Cfg.get_value("join", "plays", "both")})
			return true
		"friend":
			go("friends", true, {"add": d.tag})
			return true
	return false


# ======================================================================== оболочка

func _build() -> void:
	_compact = UI.compact()
	_safe = _safe_insets() if _compact else Vector2.ZERO
	var bg := ColorRect.new()
	bg.color = UI.BG
	bg.set_anchors_preset(Control.PRESET_FULL_RECT)
	bg.mouse_filter = Control.MOUSE_FILTER_IGNORE
	add_child(bg)
	var deco := preload("res://scripts/ui/backdrop.gd").new()
	deco.set_anchors_preset(Control.PRESET_FULL_RECT)
	add_child(deco)
	_content = MarginContainer.new()
	_content.size_flags_vertical = Control.SIZE_EXPAND_FILL
	_content.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_content.clip_contents = true
	if _compact:
		_build_phone()
	else:
		_build_desktop()
	_toast = PanelContainer.new()
	_toast.add_theme_stylebox_override("panel", UI._hpad(UI.elevated(Color("2a303c"), UI.R_M), 16, 12))
	var tl := UI.label("", 14, UI.TEXT, UI.font_bold)
	tl.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	_toast.add_child(tl)
	_toast.modulate.a = 0
	_toast.visible = false
	_toast.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_toast.z_index = 20   # поверх листов, но не последним ребёнком (автотесты ищут кнопки листа в последнем)
	add_child(_toast)
	_refresh_me()


func _build_phone() -> void:
	var root := UI.vbox(0)
	root.set_anchors_preset(Control.PRESET_FULL_RECT)
	add_child(root)
	root.add_child(UI.gap(int(_safe.x)))
	# --- верхняя панель (как в Android-приложениях): назад · заголовок · действия · искры · аватар
	var bar := MarginContainer.new()
	bar.add_theme_constant_override("margin_left", 4)
	bar.add_theme_constant_override("margin_right", 8)
	bar.custom_minimum_size.y = 60
	root.add_child(bar)
	var row := UI.hbox(4)
	bar.add_child(row)
	_bar_back = UI.icon_button("back", func(): back(), "Назад")
	row.add_child(_bar_back)
	var lead := UI.gap(12)
	row.add_child(lead)
	_bar_back.visibility_changed.connect(func(): lead.visible = not _bar_back.visible)
	_bar_brand = UI.hbox(10)
	_bar_brand.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	var li := LanternIcon.new().setup(6, {"flame": "flame_amber"}, 30)
	li.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	_bar_brand.add_child(li)
	var bt := UI.label("LANTERN", 20, UI.TEXT, UI.font_black)
	bt.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	_bar_brand.add_child(bt)
	row.add_child(_bar_brand)
	var tv := UI.vbox(-2)
	tv.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	tv.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	_bar_title = UI.label("", 20, UI.TEXT, UI.font_black)
	_bar_title.clip_text = true
	_bar_title.text_overrun_behavior = TextServer.OVERRUN_TRIM_ELLIPSIS
	tv.add_child(_bar_title)
	_bar_sub = UI.label("", 12, UI.MUTED)
	_bar_sub.clip_text = true
	_bar_sub.text_overrun_behavior = TextServer.OVERRUN_TRIM_ELLIPSIS
	tv.add_child(_bar_sub)
	row.add_child(tv)
	_bar_actions = UI.hbox(0)
	row.add_child(_bar_actions)
	_sparks = UI.sparks_chip(0, func(): go("shop"))
	row.add_child(_sparks)
	_me_btn = _avatar_button(40)
	row.add_child(_me_btn)
	# --- содержимое
	_content.add_theme_constant_override("margin_left", 16)
	_content.add_theme_constant_override("margin_right", 16)
	_content.add_theme_constant_override("margin_top", 4)
	_content.add_theme_constant_override("margin_bottom", 12)
	root.add_child(_content)
	_nav_bar = _bottom_nav()
	root.add_child(_nav_bar)
	# экранная клавиатура: поднимаем содержимое (поле чата остаётся над клавиатурой), меню прячем
	_kb_gap = UI.gap(0)
	_kb_gap.custom_minimum_size = Vector2.ZERO
	root.add_child(_kb_gap)


## Нижнее меню: Главная · Друзья · [Играть] · Магазин · Ещё. Активный раздел — подложка-«таблетка», она плавно переезжает.
func _bottom_nav() -> Control:
	var p := PanelContainer.new()
	var st := UI.box(UI.PANEL, 0, 0)
	st.border_width_top = 1
	st.border_color = Color(1, 1, 1, 0.05)
	st.content_margin_top = 6
	st.content_margin_bottom = 8 + int(_safe.y)
	p.add_theme_stylebox_override("panel", st)
	var holder := Control.new()
	holder.custom_minimum_size.y = 60
	p.add_child(holder)
	_nav_pill = Panel.new()
	_nav_pill.add_theme_stylebox_override("panel", UI.box(Color(UI.accent(), 0.16), 99))
	_nav_pill.size = Vector2(60, 32)
	_nav_pill.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_nav_pill.visible = false
	holder.add_child(_nav_pill)
	var row := UI.hbox(0)
	row.set_anchors_preset(Control.PRESET_FULL_RECT)
	holder.add_child(row)
	for item in [["home", "globe", "Игры"], ["friends", "friends", "Друзья"], ["play", "plus", "Создать"], ["profile", "user", "Профиль"], ["more", "more", "Ещё"]]:
		var b := Button.new()
		b.flat = true
		b.focus_mode = Control.FOCUS_NONE
		b.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		b.custom_minimum_size = Vector2(56, 60)
		b.tooltip_text = item[2]
		for s in ["normal", "hover", "pressed", "hover_pressed", "focus"]:
			b.add_theme_stylebox_override(s, StyleBoxEmpty.new())
		var v := UI.vbox(4)
		v.mouse_filter = Control.MOUSE_FILTER_IGNORE
		v.set_anchors_preset(Control.PRESET_FULL_RECT)
		v.alignment = BoxContainer.ALIGNMENT_CENTER
		var ic_holder := CenterContainer.new()
		ic_holder.custom_minimum_size = Vector2(60, 32)
		ic_holder.mouse_filter = Control.MOUSE_FILTER_IGNORE
		var ic: UIcon
		if item[0] == "play":
			# центральная кнопка «Играть» — круг акцента
			var circ := PanelContainer.new()
			circ.add_theme_stylebox_override("panel", UI.elevated(UI.accent(), 99))
			circ.custom_minimum_size = Vector2(44, 32)
			circ.mouse_filter = Control.MOUSE_FILTER_IGNORE
			ic = UIcon.make("plus", 22, UI.ON_ACCENT)
			ic.stroke = 2.6
			ic.size_flags_horizontal = Control.SIZE_SHRINK_CENTER
			ic.size_flags_vertical = Control.SIZE_SHRINK_CENTER
			circ.add_child(ic)
			ic_holder.add_child(circ)
			b.set_meta("fixed", true)
		else:
			ic = UIcon.make(item[1], 24, UI.MUTED)
			ic_holder.add_child(ic)
		v.add_child(ic_holder)
		var lb := UI.label(item[2], 12, UI.MUTED, UI.font_bold)
		lb.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
		v.add_child(lb)
		b.add_child(v)
		b.set_meta("icon", ic)
		b.set_meta("label", lb)
		b.set_meta("ic_holder", ic_holder)
		match item[0]:
			"more": b.pressed.connect(_more_menu.bind(b))
			"play": b.pressed.connect(func(): go("create"))
			_: b.pressed.connect(func(): go(item[0]))
		FX.press(b, 0.92)
		row.add_child(b)
		_nav[item[0]] = b
	return p


func _build_desktop() -> void:
	var root := UI.hbox(0)
	root.set_anchors_preset(Control.PRESET_FULL_RECT)
	add_child(root)
	# --- боковая панель
	var side := PanelContainer.new()
	var sst := UI.box(UI.PANEL, 0, 0)
	sst.border_width_right = 1
	sst.border_color = Color(1, 1, 1, 0.05)
	side.add_theme_stylebox_override("panel", sst)
	side.custom_minimum_size.x = 256
	root.add_child(side)
	var sm := MarginContainer.new()
	for s in ["left", "right"]:
		sm.add_theme_constant_override("margin_" + s, 14)
	sm.add_theme_constant_override("margin_top", 20)
	sm.add_theme_constant_override("margin_bottom", 14)
	side.add_child(sm)
	var sv := UI.vbox(2)
	sm.add_child(sv)
	var brand := UI.hbox(12)
	var bm := MarginContainer.new()
	bm.add_theme_constant_override("margin_left", 8)
	bm.add_theme_constant_override("margin_bottom", 18)
	bm.add_child(brand)
	brand.add_child(LanternIcon.new().setup(6, {"flame": "flame_amber"}, 40))
	var bv := UI.vbox(-4)
	bv.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	bv.add_child(UI.label("LANTERN", 21, UI.TEXT, UI.font_black))
	bv.add_child(UI.label("by Yompix", 12, UI.AMBER, UI.font_bold))
	brand.add_child(bv)
	sv.add_child(bm)
	var groups := [
		[["home", "globe", "Игры"], ["create", "plus", "Создать игру"], ["join", "play", "Зайти по коду"]],
		[["friends", "friends", "Друзья"], ["campfire", "campfire", "Костёр"]],
		[["quests", "quests", "Задания и сезон"], ["shop", "shop", "Магазин"], ["backups", "cloud", "Копии миров"], ["settings", "settings", "Настройки"]],
	]
	for gi in groups.size():
		if gi > 0:
			var dm := MarginContainer.new()
			dm.add_theme_constant_override("margin_top", 8)
			dm.add_theme_constant_override("margin_bottom", 8)
			dm.add_theme_constant_override("margin_left", 12)
			dm.add_theme_constant_override("margin_right", 12)
			dm.add_child(UI.divider())
			sv.add_child(dm)
		for item in groups[gi]:
			var b := Button.new()
			b.focus_mode = Control.FOCUS_NONE
			b.custom_minimum_size.y = 44
			b.mouse_default_cursor_shape = Control.CURSOR_POINTING_HAND
			b.tooltip_text = ""
			var h := UI.hbox(14)
			h.mouse_filter = Control.MOUSE_FILTER_IGNORE
			h.set_anchors_preset(Control.PRESET_FULL_RECT)
			h.offset_left = 14
			var ic := UIcon.make(item[1], 22, UI.MUTED)
			ic.size_flags_vertical = Control.SIZE_SHRINK_CENTER
			h.add_child(ic)
			var lb := UI.label(item[2], 15, UI.MUTED, UI.font_bold)
			lb.size_flags_vertical = Control.SIZE_SHRINK_CENTER
			lb.size_flags_horizontal = Control.SIZE_EXPAND_FILL
			h.add_child(lb)
			b.add_child(h)
			b.set_meta("icon", ic)
			b.set_meta("label", lb)
			b.set_meta("row", h)
			b.pressed.connect(func(): go(item[0]))
			sv.add_child(b)
			_nav[item[0]] = b
	sv.add_child(UI.spacer(false))
	_me_btn = Button.new()
	_me_btn.custom_minimum_size.y = 64
	_me_btn.focus_mode = Control.FOCUS_NONE
	_me_btn.mouse_default_cursor_shape = Control.CURSOR_POINTING_HAND
	_me_btn.add_theme_stylebox_override("normal", UI.box(UI.CARD, UI.R_M))
	_me_btn.add_theme_stylebox_override("hover", UI.box(UI.CARD_HI, UI.R_M))
	_me_btn.add_theme_stylebox_override("pressed", UI.box(UI.CARD_HI, UI.R_M))
	_me_btn.pressed.connect(func(): go("profile"))
	sv.add_child(_me_btn)
	# --- основная часть: заголовок + содержимое по центру
	var main := UI.vbox(0)
	main.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	root.add_child(main)
	var head := MarginContainer.new()
	head.custom_minimum_size.y = 76
	main.add_child(head)
	var row := UI.hbox(8)
	head.add_child(row)
	_bar_back = UI.icon_button("back", func(): back(), "Назад")
	_bar_back.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	row.add_child(_bar_back)
	var tv := UI.vbox(0)
	tv.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	tv.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	_bar_title = UI.label("", 26, UI.TEXT, UI.font_black)
	_bar_title.clip_text = true
	_bar_title.text_overrun_behavior = TextServer.OVERRUN_TRIM_ELLIPSIS
	tv.add_child(_bar_title)
	_bar_sub = UI.label("", 14, UI.MUTED)
	_bar_sub.clip_text = true
	_bar_sub.text_overrun_behavior = TextServer.OVERRUN_TRIM_ELLIPSIS
	tv.add_child(_bar_sub)
	row.add_child(tv)
	_bar_actions = UI.hbox(4)
	_bar_actions.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	row.add_child(_bar_actions)
	_sparks = UI.sparks_chip(0, func(): go("shop"))
	row.add_child(_sparks)
	_bar_brand = Control.new()
	_content.add_theme_constant_override("margin_bottom", 24)
	main.add_child(_content)
	# ширина контента: не шире 1100 (формы — 760), по центру
	resized.connect(_center_content)   # от ширины окна, а не колонки — иначе широкий экран раскачивает раскладку
	set_meta("head", head)
	set_meta("main", main)


func _center_content() -> void:
	if _compact or not is_instance_valid(_content):
		return
	var w: float = get_viewport_rect().size.x - 256.0
	var maxw := 760.0 if current in NARROW else 1120.0
	var m := int(maxf(32.0, (w - maxw) / 2.0))
	if _content.get_theme_constant("margin_left") == m and _content.has_theme_constant_override("margin_left"):
		return
	for s in ["left", "right"]:
		_content.add_theme_constant_override("margin_" + s, m)
	var head: MarginContainer = get_meta("head")
	for s in ["left", "right"]:
		head.add_theme_constant_override("margin_" + s, m)
	head.add_theme_constant_override("margin_top", 18)


func _avatar_button(px: int) -> Button:
	var b := Button.new()
	b.flat = true
	b.focus_mode = Control.FOCUS_NONE
	b.custom_minimum_size = Vector2(48, 48)
	b.tooltip_text = "Профиль"
	for s in ["normal", "hover", "pressed", "hover_pressed", "focus"]:
		b.add_theme_stylebox_override(s, UI.box(Color(1, 1, 1, 0.07 if s in ["hover", "pressed"] else 0.0), 99))
	b.pressed.connect(func(): go("profile"))
	b.set_meta("px", px)
	FX.press(b, 0.9)
	return b


# ======================================================================== листы «Играть» и «Ещё»

## Главный выбор: создать мир или зайти к другу.
func play_sheet() -> void:
	var v: VBoxContainer = Overlays.sheet(self, "Во что играем?", "Выберите, что хотите сделать")
	v.add_child(play_option("host"))
	v.add_child(play_option("join"))
	var rr := UI.list_row(UI.icon_tile("globe", UI.BEDROCK, 40, 20), "Игры", "Зайти в открытую игру", UI.icon("chevron", 20, UI.DIM), func(): Overlays.close_sheet(self); go("home"), Color(0, 0, 0, 0))
	v.add_child(rr)


## Большая карточка-действие («Создать мир» / «Зайти к другу») — на главной и в листе «Играть».
func play_option(kind: String, big := false) -> Control:
	var host := kind == "host"
	var col := UI.accent() if host else UI.BEDROCK
	var mobile := Cfg.mobile()
	var p := PanelContainer.new()
	var st := UI.box(Color(col, 0.12), UI.R_XL if big else UI.R_L, 0, Color(col, 0.35), 1)
	UI._hpad(st, 24 if big and not _compact else 18, 26 if big and not _compact else (18 if big else 16))
	p.add_theme_stylebox_override("panel", st)
	var row := UI.hbox(16)
	p.add_child(row)
	var tile := PanelContainer.new()
	tile.custom_minimum_size = Vector2(56, 56) if big else Vector2(48, 48)
	tile.add_theme_stylebox_override("panel", UI.box(col, UI.R_L))
	tile.mouse_filter = Control.MOUSE_FILTER_IGNORE
	var ic := UIcon.make("plus" if host else "play", 28 if big else 24, UI.ON_ACCENT)
	ic.stroke = 2.6
	ic.size_flags_horizontal = Control.SIZE_SHRINK_CENTER
	ic.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	tile.add_child(ic)
	tile.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	row.add_child(tile)
	var v := UI.vbox(3)
	v.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	v.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	v.add_child(UI.label("Играть с друзьями" if host else "Зайти к другу", 20 if big else 18, UI.TEXT, UI.font_black))
	var desc := "Откройте мир в Minecraft — код для друзей появится сразу" if host else "Введите код, который прислал друг"
	if host and not mobile:
		desc = "Ваш мир из Minecraft или отдельный сервер — друзья зайдут по коду"
	v.add_child(UI.para(desc, 14, UI.MUTED))
	row.add_child(v)
	var ch := UI.icon("chevron", 22, col.lightened(0.2))
	ch.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	row.add_child(ch)
	UI.tappable(p, func():
		Overlays.close_sheet(self)
		if host:
			quick_host()
		else:
			go("join"), st)
	UI.hover_scale(p, 1.01)
	return p


## Одна кнопка «Играть с друзьями»: мир уже открыт — экран с кодом; нет — ищем мир в Minecraft
## и открываем комнату сами, как только он появится (на ПК там же — «Отдельный сервер»).
func quick_host() -> void:
	if own.active or server.state in ["installing", "starting", "running", "stopping"]:
		go("host")
	else:
		go("create")


func _num_row(n: String, t: String, sub: String) -> Control:
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
	var v := UI.vbox(2)
	v.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	v.add_child(UI.label(t, 16, UI.TEXT, UI.font_bold))
	v.add_child(UI.para(sub, 14, UI.MUTED))
	row.add_child(v)
	return row


func _more_menu(_b: Button = null) -> void:
	var v: VBoxContainer = Overlays.sheet(self, "Ещё")
	var items := [["join", "play", "Зайти по коду", "Код или номер комнаты от друга"], ["shop", "shop", "Магазин", "Пламя и рамки за искры"], ["campfire", "campfire", "Костёр", "Друзья в сети — у огня"], ["quests", "quests", "Задания и сезон", "Искры за игру с друзьями"],
		["backups", "cloud", "Копии миров", "Резервные копии и облако"], ["settings", "settings", "Настройки", "Цвет, анимации, сервер"]]
	if own.active or server.state in ["installing", "starting", "running", "stopping", "error"]:
		items.push_front(["host", "server", "Мой мир", "Код комнаты и игроки"])
	var list := UI.vbox(2)
	for it in items:
		var r := UI.list_row(UI.icon_tile(it[1], UI.accent(), 40, 20), it[2], it[3], UI.icon("chevron", 20, UI.DIM), func(): Overlays.close_sheet(self); go(it[0]), Color(0, 0, 0, 0))
		r.custom_minimum_size.y = 60
		list.add_child(r)
	v.add_child(list)
	FX.stagger(list, 0.03)


# ======================================================================== сервер, присутствие

## Сервер заработал → открываем комнату на relay; остановился → закрываем.
func _on_server_state(s: String) -> void:
	# При перезапуске сервера комната (и её код) остаются прежними.
	if s == "running" and server.opts.get("internet", true):
		var ed := server.edition
		if ed == "java" and server.opts.get("crossplay", false):
			ed = "both"
		var st: Dictionary = relay_host.stats()
		if st.state in ["off", "error"] or relay_host.edition != ed or relay_host.relay_url != Cfg.relay_url() or relay_host.java_port != server.java_port() or relay_host.bedrock_port != server.bedrock_port():
			relay_host.probe_world = false
			relay_host.public_card = room.public_card()
			relay_host.public_on = server.opts.get("public", false)
			relay_host.start(Cfg.relay_url(), ed, server.opts.get("world", "Мир"), server.java_port(), server.bedrock_port(), host_extra())
			room.room_opened()
	elif s == "error" and not own.active:
		relay_host.stop()
		room.room_closed()
	update_presence()


## own_opts — мир из Minecraft (без «Открытого мира»: его видно, только пока игрок в игре).
func host_extra(own_opts := {}) -> Dictionary:
	var d := {"token": Social.token}
	# LANTERN Pro: relay выдаст свой код комнаты (если он задан и Pro активна)
	if Social.eco.get("pro", false) and String(Social.eco.get("vanity", "")) != "":
		d["vanity"] = true
	if own_opts.is_empty() and server.opts.get("public", false):
		d["public"] = {"title": server.opts.get("public_title", server.opts.get("world", "Мир")), "tags": server.opts.get("tags", []),
			"version": server.opts.get("mc_version", "") if server.edition == "java" else "", "slots": server.opts.get("max_players", 10)}
	elif not own_opts.is_empty() and relay_host.public_on and not relay_host.public_card.is_empty():
		d["public"] = relay_host.public_card
	# 1.5: режим, места и пароль из «Создать игру» (relay проверяет пароль и места сам)
	var g := Cfg.game()
	d["settings"] = {"mode": g.mode, "cap": g.cap, "password": g.password}
	if d.has("public"):
		d.public = (d.public as Dictionary).duplicate()
		if g.title != "":
			d.public["title"] = g.title
		d.public["slots"] = g.cap
	return d


## Что видят друзья: «хостит Java 1.21», «играет в мир X».
func update_presence() -> void:
	if own and own.active:
		Social.set_presence("hosting", String(own.world.get("name", "Мир")), String(own.world.get("edition", "bedrock")), String(own.world.get("version", "")), relay_host.stats().get("code", ""))
	elif server.state in ["running", "starting"]:
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
	if _compact and (OS.has_feature("mobile") or Cfg.autotest_args.has("kb")) and is_instance_valid(_kb_gap):
		var w := get_window()
		var k := float(w.size.y) / maxf(1.0, float(w.content_scale_size.y)) if w.content_scale_size.y > 0 else 1.0
		var kb := float(DisplayServer.virtual_keyboard_get_height()) / k
		if Cfg.autotest_args.has("kb"):   # автотест: «клавиатура» высотой kb dp, пока фокус в поле ввода
			kb = float(Cfg.autotest_args.kb) if get_viewport().gui_get_focus_owner() is LineEdit else 0.0
		# Клавиатура открывается/закрывается в несколько шагов, а при переходе между полями на миг «пропадает».
		# Открытие применяем сразу, закрытие — после короткой паузы: без прыжков содержимого.
		if absf(kb - _kb_h) > 1.0:
			if kb > _kb_h:
				_kb_pending = -1.0
				_kb_apply(kb)
			elif _kb_pending < 0.0 or absf(_kb_pending_h - kb) > 1.0:
				_kb_pending = 0.15
				_kb_pending_h = kb
		if _kb_pending >= 0.0:
			_kb_pending -= d
			if _kb_pending < 0.0:
				_kb_apply(_kb_pending_h)
	_presence_tick += d
	if _presence_tick > 2.0:
		_presence_tick = 0
		var key := "%s|%s|%s" % [server.state, relay_host.stats().get("code", ""), relay_join.stats().state]
		if key != _last_presence:
			_last_presence = key
			update_presence()


var _kb_pending := -1.0
var _kb_pending_h := 0.0
var _kb_tw: Tween


## Экранная клавиатура: плавно (180 мс) поднять содержимое, спрятать нижнее меню и прокрутить к полю ввода.
func _kb_apply(kb: float) -> void:
	_kb_h = kb
	var gap := maxf(0.0, kb - _safe.y)
	_nav_bar.visible = kb <= 0.0
	if _kb_tw:
		_kb_tw.kill()
	if FX.on():
		_kb_tw = create_tween().set_trans(Tween.TRANS_CUBIC).set_ease(Tween.EASE_OUT)
		_kb_tw.tween_property(_kb_gap, "custom_minimum_size:y", gap, 0.18)
		_kb_tw.tween_callback(_kb_reveal)
	else:
		_kb_gap.custom_minimum_size.y = gap
		_kb_reveal.call_deferred()


## Поле с фокусом — над клавиатурой: прокрутить свой ScrollContainer, а окно поверх экрана (лист, регистрация) — сдвинуть вверх.
func _kb_reveal() -> void:
	var f := get_viewport().gui_get_focus_owner()
	var main_root := _kb_gap.get_parent()
	var panned: Array = get_meta("kb_panned", [])
	if f == null or _kb_h <= 0.0:
		for n in panned:
			if is_instance_valid(n):
				_kb_pan(n, 0.0)
		set_meta("kb_panned", [])
		return
	var p := f.get_parent()
	while p != null and p != self:
		if p is ScrollContainer:
			(p as ScrollContainer).ensure_control_visible(f)
			break
		p = p.get_parent()
	var top: Node = f
	while top.get_parent() != null and top.get_parent() != self:
		top = top.get_parent()
	if top == main_root or not (top is Control):
		return
	var bottom := get_viewport_rect().size.y - _kb_h - 12.0
	var over := f.get_global_rect().end.y - (top as Control).position.y - bottom
	if over > 0.0:
		if not top in panned:
			panned.append(top)
		set_meta("kb_panned", panned)
		_kb_pan(top, -over)


func _kb_pan(n: Control, y: float) -> void:
	if FX.on():
		n.create_tween().set_trans(Tween.TRANS_CUBIC).set_ease(Tween.EASE_OUT).tween_property(n, "position:y", y, 0.18)
	else:
		n.position.y = y


func _on_social(what: String) -> void:
	if what in ["me", "eco", "conn", "friends", "dm"]:
		_refresh_me()
	if what == "conn" and Social.conn == "need_register" and Cfg.autotest == "":
		Overlays.register(self)


func _refresh_me() -> void:
	if not is_instance_valid(_me_btn):
		return
	for c in _me_btn.get_children():
		c.queue_free()
	var lvl: int = int(Social.eco.get("lantern", {}).get("level", Social.me.get("lantern", {}).get("level", 1)))
	var eq: Variant = Social.eco.get("equip", Social.me.get("equip", ""))
	if _compact:
		var li := LanternIcon.new().setup(lvl, eq, 34)
		li.position = Vector2(7, 7)
		_me_btn.add_child(li)
	else:
		var row := UI.hbox(10)
		row.mouse_filter = Control.MOUSE_FILTER_IGNORE
		row.set_anchors_preset(Control.PRESET_FULL_RECT)
		row.offset_left = 10
		row.offset_right = -10
		row.add_child(LanternIcon.new().setup(lvl, eq, 40))
		var v := UI.vbox(-2)
		v.mouse_filter = Control.MOUSE_FILTER_IGNORE
		v.alignment = BoxContainer.ALIGNMENT_CENTER
		v.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		var nick := UI.label(Social.me.get("nick", "Профиль") if Social.online() else ("Профиль" if Social.conn != "connecting" else "Подключаемся…"), 15, UI.TEXT, UI.font_bold)
		nick.clip_text = true
		nick.text_overrun_behavior = TextServer.OVERRUN_TRIM_ELLIPSIS
		v.add_child(nick)
		var sub := "#" + String(Social.me.get("tag", "#")).get_slice("#", 1) if Social.online() else ("не в сети" if Social.conn == "off" else "")
		v.add_child(UI.label(sub, 12, UI.MUTED))
		row.add_child(v)
		row.add_child(UI.icon("chevron", 18, UI.DIM))
		_me_btn.add_child(row)
	if is_instance_valid(_sparks):
		_sparks.visible = Social.online() and not (_compact and _has_actions)
		var n := int(Social.eco.get("sparks", 0))
		if _last_sparks >= 0 and n != _last_sparks and FX.on():
			var from := _last_sparks
			_sparks.create_tween().tween_method(func(x): _sparks.text = "%d" % int(round(x)), float(from), float(n), 0.6).set_trans(Tween.TRANS_CUBIC).set_ease(Tween.EASE_OUT)
		else:
			_sparks.text = "%d" % n
		_last_sparks = n
	_update_badges()


func _update_badges() -> void:
	var b: Button = _nav.get("friends")
	if not is_instance_valid(b):
		return
	var n := Social.incoming.size()
	for k in Social.unread:
		n += int(Social.unread[k])
	var old: Node = b.get_meta("badge") if b.has_meta("badge") else null
	if is_instance_valid(old):
		old.queue_free()
	b.remove_meta("badge")
	if n <= 0:
		return
	var bd := UI.badge(n)
	if _compact:
		bd.position = Vector2(13, -7)   # на иконке, справа сверху
		(b.get_meta("icon") as Control).add_child(bd)
	else:
		(b.get_meta("row") as HBoxContainer).add_child(bd)
	b.set_meta("badge", bd)


func _on_notice(d: Dictionary) -> void:
	match d.get("kind", ""):
		"request": toast("%s хочет дружить — откройте «Друзья»" % d.from.tag)
		"accepted": toast("%s принял(а) вашу заявку!" % d.from.tag, UI.JAVA)
		"invite": invite_popup(d)
		"achievement": toast("Достижение «%s» · +%d искр · титул «%s»" % [d.name, d.sparks, d.title], UI.AMBER)
		"referral": toast("Ваш друг %s наиграл час — вам +%d искр!" % [d.from.nick, d.sparks], UI.AMBER)
		"purchased": toast("Готово! Покупка получена" + (" (ДЕМО)" if Social.demo else ""), UI.JAVA)
		"ad_reward": toast("+20 искр за просмотр", UI.AMBER)
		"account_deleted":
			toast("Профиль удалён с сервера", UI.JAVA)
			go("home", true)
		"info": toast(d.text)


## Приглашение от друга — карточка сверху.
func invite_popup(d: Dictionary) -> void:
	var p := PanelContainer.new()
	p.add_theme_stylebox_override("panel", UI.elevated(Color("262b37"), UI.R_L, 16))
	var v := UI.vbox(12)
	p.add_child(v)
	var top := UI.hbox(12)
	top.add_child(LanternIcon.new().setup(int(d.from.get("lantern", {}).get("level", 3)) if d.from is Dictionary else 3, null, 40))
	var tl := UI.para("%s зовёт вас в мир «%s»" % [d.from.nick, d.get("world", "")], 15, UI.TEXT)
	tl.add_theme_font_override("font", UI.font_bold)
	tl.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	top.add_child(tl)
	v.add_child(top)
	var row := UI.hbox(8)
	var nb := UI.button("Не сейчас", "ghost", func(): p.queue_free())
	row.add_child(nb)
	var gb := UI.button("Зайти", "primary", func(): p.queue_free(); go("join", true, {"input": d.code}))
	gb.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	row.add_child(gb)
	v.add_child(row)
	add_child(p)
	var w := UI.dialog_width(400)
	p.custom_minimum_size.x = w
	p.position = Vector2(size.x - w - 16 if not _compact else (size.x - w) / 2, _safe.x + 12)
	if FX.on():
		var y := p.position.y
		p.position.y = y - 40
		p.modulate.a = 0
		var tw := p.create_tween().set_parallel().set_trans(Tween.TRANS_CUBIC).set_ease(Tween.EASE_OUT)
		tw.tween_property(p, "position:y", y, 0.22)
		tw.tween_property(p, "modulate:a", 1.0, 0.2)
	get_tree().create_timer(20).timeout.connect(func(): if is_instance_valid(p): p.queue_free())


# ======================================================================== навигация

## Экран-заголовок задаёт сам экран (подзаголовок, своя кнопка «Назад», действия справа).
func set_title(t: String, sub := "") -> void:
	if not is_instance_valid(_bar_title):
		return
	_bar_title.text = t
	_bar_sub.text = sub
	_bar_sub.visible = sub != ""
	if _compact:
		_bar_brand.visible = false
		_bar_title.get_parent().visible = true


func set_back(cb: Callable) -> void:
	_back_cb = cb
	if is_instance_valid(_bar_back):
		_bar_back.visible = cb.is_valid() or (_compact and not current in ROOTS) or (not _compact and not _stack.is_empty() and current in ["syscheck", "host"])


## Телефон: спрятать нижнее меню (открыт чат — как в мессенджерах). При переходе на другой экран меню возвращается.
func set_nav_visible(on: bool) -> void:
	if is_instance_valid(_nav_bar) and _kb_h <= 0.0:
		_nav_bar.visible = on


func set_actions(list: Array) -> void:
	if not is_instance_valid(_bar_actions):
		return
	for c in _bar_actions.get_children():
		c.queue_free()
	for c in list:
		_bar_actions.add_child(c)
	_has_actions = not list.is_empty()
	if _compact:
		# у экрана свои кнопки (чат) — искры и аватар убираем, чтобы заголовку хватило места
		_sparks.visible = list.is_empty() and Social.online()
		_me_btn.visible = list.is_empty()


func back() -> void:
	if _back_cb.is_valid():
		var cb := _back_cb
		cb.call()
		return
	if not _stack.is_empty():
		var prev: Array = _stack.pop_back()
		_going_back = true
		go(prev[0], true, prev[1])
		_going_back = false
	elif current != "home":
		_going_back = true
		go("home")
		_going_back = false


func go(name: String, force := false, arg: Variant = null) -> void:
	if name == "rooms":   # 1.5: «Открытые миры» стали главной — списком «Игры»
		name = "home"
	if name == current and not force and arg == null:
		return
	if name == "host" and arg == null and not own.active and not server.state in ["installing", "starting", "running", "stopping", "error"]:
		name = "setup"
	# история «Назад»
	var dir := 1
	if _going_back:
		dir = -1
	elif name in ROOTS or (not _compact and name in ["home", "create", "join", "friends", "campfire", "quests", "shop", "backups", "settings"]):
		_stack.clear()
		dir = 0
	elif current != "" and current != name:
		_stack.append([current, _arg])
		if _stack.size() > 12:
			_stack.pop_front()
	var prev_name := current
	while Overlays.close_sheet(self):
		pass   # переходим на другой экран — открытые листы закрываем
	current = name
	_arg = arg
	_back_cb = Callable()
	_select_nav(name)
	# верхняя панель по умолчанию
	if is_instance_valid(_bar_title):
		_bar_title.text = TITLES.get(name, "")
		_bar_sub.text = ""
		_bar_sub.visible = false
		set_actions([])
		if _compact:
			var brand := name == "home"
			_bar_brand.visible = brand
			_bar_title.get_parent().visible = not brand
	set_back(Callable())
	set_nav_visible(true)
	_center_content()
	# старый экран уходит, новый приходит (общая ось: вперёд — справа, назад — слева, разделы — проявление)
	var old := _screen_root
	if is_instance_valid(old):
		old.mouse_filter = Control.MOUSE_FILTER_IGNORE
		_set_tree_ignore(old)
		if FX.on() and prev_name != name:
			var tw := old.create_tween().set_parallel().set_trans(Tween.TRANS_CUBIC).set_ease(Tween.EASE_IN)
			tw.tween_property(old, "modulate:a", 0.0, 0.14)
			if dir != 0:
				tw.tween_property(old, "position:x", old.position.x - 36 * dir, 0.16)
			tw.chain().tween_callback(old.queue_free)
		else:
			old.queue_free()
	# экраны грузятся при первом открытии (быстрее старт), дальше — из кэша ресурсов
	var scr: Control = load("res://scripts/screens/%s.gd" % name).new()
	scr.set("app", self)
	if arg != null:
		scr.set("arg", arg)
	_screen = scr
	var root: Control = scr
	if _compact:
		# телефон: длинные экраны прокручиваются целиком. Обёртка ещё и не даёт широкому содержимому
		# растянуть оболочку (нижнее меню) за край экрана.
		var sc := ScrollContainer.new()
		# по горизонтали не листается: ничего не «выступает» за край экрана, как на сайте
		sc.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
		sc.clip_contents = true
		sc.vertical_scroll_mode = ScrollContainer.SCROLL_MODE_SHOW_NEVER if name in SCROLL_IN_COMPACT and not (scr is ScrollContainer) else ScrollContainer.SCROLL_MODE_DISABLED
		scr.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		scr.size_flags_vertical = Control.SIZE_EXPAND_FILL
		sc.add_child(scr)
		root = sc
	elif name in SCROLL_IN_COMPACT and not (scr is ScrollContainer):
		# ПК: длинные экраны (мастера, профиль) тоже прокручиваются
		var sc := ScrollContainer.new()
		sc.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
		scr.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		scr.size_flags_vertical = Control.SIZE_EXPAND_FILL
		sc.add_child(scr)
		root = sc
	_screen_root = root
	_content.add_child(root)
	if FX.on():
		root.modulate.a = 0
		var from := Vector2(40 * dir, 0) if dir != 0 else Vector2(0, 10)
		var start := func():
			if not is_instance_valid(root):
				return
			var p := root.position
			root.position = p + from
			var tw := root.create_tween().set_parallel().set_trans(Tween.TRANS_CUBIC).set_ease(Tween.EASE_OUT)
			tw.tween_property(root, "modulate:a", 1.0, 0.18).set_delay(0.03)
			tw.tween_property(root, "position", p, 0.22).set_delay(0.02)
			tw.chain().tween_callback(func(): if is_instance_valid(_content): _content.queue_sort())
		root.create_tween().tween_callback(start)


func _set_tree_ignore(n: Node) -> void:
	if n is Control:
		(n as Control).mouse_filter = Control.MOUSE_FILTER_IGNORE
	for c in n.get_children():
		_set_tree_ignore(c)


func _select_nav(name: String) -> void:
	var key := name
	if _compact:
		if name in ["join", "setup", "host"]:
			key = "play"
		elif not _nav.has(name):
			key = "more"
	else:
		if name == "host":
			key = "setup"
		elif name == "syscheck":
			key = "settings"
	for k in _nav:
		var b: Button = _nav[k]
		var on: bool = k == key
		var ic: UIcon = b.get_meta("icon")
		var lb: Label = b.get_meta("label")
		if b.has_meta("fixed"):
			lb.add_theme_color_override("font_color", UI.TEXT if on else UI.MUTED)
			continue
		ic.set_color(UI.accent().lightened(0.2) if on else UI.MUTED)
		lb.add_theme_color_override("font_color", UI.TEXT if on else UI.MUTED)
		if not _compact:
			for s in ["normal", "hover", "pressed", "hover_pressed", "focus"]:
				b.add_theme_stylebox_override(s, UI.box(Color(UI.accent(), 0.14) if on else Color(1, 1, 1, 0.05 if s in ["hover", "pressed"] else 0.0), UI.R_M))
	if _compact and is_instance_valid(_nav_pill):
		var b: Button = _nav.get(key)
		if b == null or b.has_meta("fixed"):
			_nav_pill.visible = false
			return
		var place := func():
			if not is_instance_valid(b) or not is_instance_valid(_nav_pill):
				return
			var h: Control = b.get_meta("ic_holder")
			var target: Vector2 = b.position + h.position + h.get_parent().position + Vector2((h.size.x - 60) / 2, 0)
			var was := _nav_pill.visible
			_nav_pill.visible = true
			if was and FX.on():
				_nav_pill.create_tween().set_trans(Tween.TRANS_CUBIC).set_ease(Tween.EASE_OUT).tween_property(_nav_pill, "position", target, 0.22)
			else:
				_nav_pill.position = target
		if b.size.x > 0:
			place.call()
		else:
			b.create_tween().tween_callback(place).set_delay(0.05)


## Короткое сообщение снизу (над нижним меню).
func toast(text: String, color := UI.TEXT) -> void:
	if not is_instance_valid(_toast):
		return
	var l: Label = _toast.get_child(0)
	l.text = text
	l.add_theme_color_override("font_color", color if color != UI.TEXT else UI.TEXT)
	_toast.visible = true
	var w := minf(UI.dialog_width(520), l.get_theme_font("font").get_string_size(text, HORIZONTAL_ALIGNMENT_LEFT, -1, 14).x + 36)
	l.custom_minimum_size.x = w - 32
	_toast.size = Vector2.ZERO
	_toast.reset_size()
	await get_tree().process_frame
	var bottom := 96.0 + _safe.y if _compact else 28.0
	var x := (size.x - _toast.size.x) / 2 if _compact else (size.x + 256 - _toast.size.x) / 2
	var y := size.y - _toast.size.y - bottom
	for c in get_children():
		if c is ColorRect and c.mouse_filter == Control.MOUSE_FILTER_STOP and not c.is_queued_for_deletion():
			y = _safe.x + 12   # открыт лист — всплывашка сверху, чтобы не закрывать его
			x = (size.x - _toast.size.x) / 2
	if _toast.has_meta("tw"):
		var old: Tween = _toast.get_meta("tw")
		if old and old.is_valid():
			old.kill()
	_toast.position = Vector2(x, y + (16 if FX.on() else 0))
	var tw := create_tween()
	_toast.set_meta("tw", tw)
	tw.set_parallel().set_trans(Tween.TRANS_CUBIC).set_ease(Tween.EASE_OUT)
	tw.tween_property(_toast, "modulate:a", 1.0, FX.d(0.2))
	tw.tween_property(_toast, "position:y", y, FX.d(0.28))
	tw.chain().tween_interval(2.8)
	tw.chain().tween_property(_toast, "modulate:a", 0.0, 0.3)
	tw.chain().tween_callback(func(): _toast.visible = false)


func _notification(what: int) -> void:
	if what == NOTIFICATION_WM_CLOSE_REQUEST:
		_quit()
	elif what == NOTIFICATION_APPLICATION_RESUMED and _content and Cfg.autotest == "":
		# Android: ссылка lantern:// пришла, когда приложение уже было открыто (onNewIntent → setIntent)
		own.on_resume()
		open_deeplink(DeepLink.from_launch())
	elif what == NOTIFICATION_APPLICATION_PAUSED and own:
		own.on_pause()
	elif what == NOTIFICATION_WM_GO_BACK_REQUEST:
		# кнопка «Назад» на Android: закрыть лист → шаг назад в экране → предыдущий экран
		if Overlays.close_top(self):
			return
		if _back_cb.is_valid() or not _stack.is_empty() or current != "home":
			back()


func _quit() -> void:
	if _closing:
		return
	_closing = true
	relay_join.stop()
	own.stop()
	relay_host.stop()
	if server.is_alive():
		var ov := ColorRect.new()
		ov.color = Color(UI.BG, 0.92)
		ov.set_anchors_preset(Control.PRESET_FULL_RECT)
		var cc := CenterContainer.new()
		cc.set_anchors_preset(Control.PRESET_FULL_RECT)
		ov.add_child(cc)
		var v := UI.vbox(16)
		var li := LanternIcon.new().setup(6, null, 72)
		li.size_flags_horizontal = Control.SIZE_SHRINK_CENTER
		v.add_child(li)
		FX.pulse(li)
		var l := UI.h2("Сохраняем мир и выключаем сервер…")
		l.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
		v.add_child(l)
		cc.add_child(v)
		add_child(ov)
		server.stop()
		var t0 := Time.get_ticks_msec()
		while server.is_alive() and Time.get_ticks_msec() - t0 < 60000:
			await get_tree().process_frame
	get_tree().quit()


func _exit_tree() -> void:
	if own:
		own.stop()
	relay_join.stop()
	relay_host.stop()
