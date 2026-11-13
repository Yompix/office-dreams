class_name Overlays
extends RefCounted
## Окна поверх приложения: знакомство и ник при первом запуске, нижние листы (на ПК — диалоги),
## лист покупки (как в Google Play), тестовая реклама.

const PRODUCTS := {
	"sparks_500": {"title": "500 искр", "desc": "Искры для магазина косметики", "uah": "79,99 ₴", "usd": "$1.99"},
	"sparks_1200": {"title": "1200 искр", "desc": "Выгоднее на 20%", "uah": "169,99 ₴", "usd": "$3.99"},
	"lantern_pass_s1": {"title": "Пропуск сезона «Первые огни»", "desc": "Премиум-путь сезона: 9 эксклюзивных предметов", "uah": "149,99 ₴", "usd": "$3.49"},
	"lantern_pro_month": {"title": "LANTERN Pro — месяц", "desc": "Без рекламы, свой код комнаты, облачные копии миров, особая косметика", "uah": "99,99 ₴/мес", "usd": "$2.49/mo"},
}


static func _dim(app: Control, alpha := 0.6) -> ColorRect:
	var ov := ColorRect.new()
	ov.color = Color(0, 0, 0, 0)
	ov.set_anchors_preset(Control.PRESET_FULL_RECT)
	ov.mouse_filter = Control.MOUSE_FILTER_STOP
	app.add_child(ov)
	if FX.on():
		ov.create_tween().tween_property(ov, "color:a", alpha, 0.22)
	else:
		ov.color.a = alpha
	return ov


static func _center(ov: Control, child: Control) -> void:
	var cc := CenterContainer.new()
	cc.set_anchors_preset(Control.PRESET_FULL_RECT)
	ov.add_child(cc)
	cc.add_child(child)
	if FX.on():
		child.modulate.a = 0
		child.scale = Vector2(0.94, 0.94)
		child.resized.connect(func(): child.pivot_offset = child.size / 2)
		var tw := child.create_tween().set_parallel().set_trans(Tween.TRANS_CUBIC).set_ease(Tween.EASE_OUT)
		tw.tween_property(child, "scale", Vector2.ONE, 0.22)
		tw.tween_property(child, "modulate:a", 1.0, 0.18)


static func _safe_bottom(app: Control) -> float:
	return float(app.get("_safe").y) if app.get("_safe") is Vector2 else 0.0


# ------------------------------------------------------------------ нижний лист (на ПК — диалог по центру)

## Возвращает VBox для содержимого. Закрывается тапом по затемнению, «Назад» или Overlays.close_sheet().
static func sheet(app: Control, title := "", sub := "", light := false) -> VBoxContainer:
	var ov := _dim(app, 0.55)
	ov.set_meta("sheet", true)
	var phone := UI.compact()
	var bg := Color("f7f8fa") if light else Color("1b1f28")
	var p := PanelContainer.new()
	var st: StyleBox
	if light:
		var fl := UI.elevated(bg, UI.R_2XL, 0)
		fl.shadow_offset = Vector2(0, -2)
		if phone:
			fl.corner_radius_bottom_left = 0
			fl.corner_radius_bottom_right = 0
		st = fl
	else:
		# тёмное плотное стекло со светлой кромкой сверху; на телефоне низ листа уходит за край экрана
		var sx := UI.solid(UI.R_XL, 0)
		if phone:
			sx.expand_margin_bottom = UI.GLASS_M + 60
			sx.expand_margin_left = UI.GLASS_M + 2
			sx.expand_margin_right = UI.GLASS_M + 2
		st = sx
	st.content_margin_left = 20
	st.content_margin_right = 20
	st.content_margin_top = 10 if phone else 24
	st.content_margin_bottom = (20 + _safe_bottom(app)) if phone else 24
	p.add_theme_stylebox_override("panel", st)
	p.mouse_filter = Control.MOUSE_FILTER_STOP
	var v := UI.vbox(14)
	v.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	# высокий лист (телефон лёжа) прокручивается, а не уходит за экран
	var sc := ScrollContainer.new()
	sc.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	sc.add_child(v)
	p.add_child(sc)
	var fit := func():
		if not is_instance_valid(v):
			return
		var maxh: float = app.get_viewport_rect().size.y * 0.86 - 48 - _safe_bottom(app)
		sc.custom_minimum_size.y = minf(v.get_combined_minimum_size().y, maxh)
	v.minimum_size_changed.connect(fit)
	fit.call_deferred()
	if phone:
		var handle := Panel.new()
		handle.custom_minimum_size = Vector2(36, 4)
		handle.size_flags_horizontal = Control.SIZE_SHRINK_CENTER
		handle.add_theme_stylebox_override("panel", UI.box(Color(0.5, 0.5, 0.5, 0.45), 99))
		v.add_child(handle)
	if title != "":
		var hv := UI.vbox(4)
		var tl := UI.label(title, 24, Color("202124") if light else UI.TEXT, UI.font_title)
		tl.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
		tl.custom_minimum_size.x = 120
		hv.add_child(tl)
		if sub != "":
			hv.add_child(UI.para(sub, 14, Color("5f6368") if light else UI.MUTED))
		v.add_child(hv)
	var body := UI.vbox(12)
	v.add_child(body)
	ov.set_meta("panel", p)
	ov.gui_input.connect(func(e):
		if e is InputEventMouseButton and e.pressed and e.button_index == MOUSE_BUTTON_LEFT:
			if not p.get_global_rect().has_point(ov.get_global_transform() * e.position):
				close_sheet(app))
	if phone:
		var col := UI.vbox(0)
		col.set_anchors_preset(Control.PRESET_FULL_RECT)
		col.mouse_filter = Control.MOUSE_FILTER_IGNORE
		col.add_child(UI.spacer(false))
		col.add_child(p)
		ov.add_child(col)
		if FX.on():
			p.modulate.a = 0
			# через кадр (после раскладки) — выезд снизу с пружинкой; твин живёт вместе с листом
			p.create_tween().tween_callback(func():
				var y := p.position.y
				p.position.y = y + p.size.y * 0.6
				p.modulate.a = 1
				p.create_tween().set_trans(Tween.TRANS_CUBIC).set_ease(Tween.EASE_OUT).tween_property(p, "position:y", y, 0.24))
	else:
		p.custom_minimum_size.x = 480
		_center(ov, p)
	return body


## Закрыть верхний лист (если есть). true — что-то закрыли.
static func close_sheet(app: Control) -> bool:
	for i in range(app.get_child_count() - 1, -1, -1):
		var ov := app.get_child(i)
		if ov is ColorRect and ov.has_meta("sheet") and not ov.is_queued_for_deletion() and not ov.has_meta("closing"):
			_fade_out(ov)
			return true
	return false


static func _fade_out(ov: Control) -> void:
	ov.set_meta("closing", true)
	ov.mouse_filter = Control.MOUSE_FILTER_IGNORE
	if not FX.on():
		ov.queue_free()
		return
	var p: Control = ov.get_meta("panel") if ov.has_meta("panel") else null
	var tw := ov.create_tween().set_parallel().set_trans(Tween.TRANS_CUBIC).set_ease(Tween.EASE_IN)
	tw.tween_property(ov, "color:a", 0.0, 0.2)
	if p and UI.compact():
		tw.tween_property(p, "position:y", p.position.y + p.size.y, 0.2)
	elif p:
		tw.tween_property(p, "modulate:a", 0.0, 0.15)
	tw.chain().tween_callback(ov.queue_free)


## Кнопка «Назад» на Android: закрыть верхнее окно.
static func close_top(app: Control) -> bool:
	if close_sheet(app):
		return true
	for i in range(app.get_child_count() - 1, -1, -1):
		var ov := app.get_child(i)
		if ov is ColorRect and ov.mouse_filter == Control.MOUSE_FILTER_STOP and not ov.is_queued_for_deletion():
			if ov.has_meta("page_back") and (ov.get_meta("page_back") as Callable).call():
				return true
			ov.queue_free()
			return true
	return false


# ------------------------------------------------------------------ первый запуск: знакомство → ник → «как играть»
static func register(app: Control) -> void:
	if app.has_node("RegisterOverlay"):
		return
	var ov := ColorRect.new()
	ov.name = "RegisterOverlay"
	ov.color = UI.BG
	ov.set_anchors_preset(Control.PRESET_FULL_RECT)
	ov.mouse_filter = Control.MOUSE_FILTER_STOP
	app.add_child(ov)
	var glow := preload("res://scripts/ui/backdrop.gd").new()
	glow.set_anchors_preset(Control.PRESET_FULL_RECT)
	glow.warm = 1.4
	ov.add_child(glow)
	var safe: Vector2 = app.get("_safe") if app.get("_safe") is Vector2 else Vector2.ZERO
	var m := MarginContainer.new()
	m.set_anchors_preset(Control.PRESET_FULL_RECT)
	var side := 24 if UI.compact() else 0
	m.add_theme_constant_override("margin_left", side)
	m.add_theme_constant_override("margin_right", side)
	m.add_theme_constant_override("margin_top", int(safe.x) + 8)
	m.add_theme_constant_override("margin_bottom", int(safe.y) + 24)
	ov.add_child(m)
	var center := CenterContainer.new()
	m.add_child(center)
	var stage := Control.new()   # страницы сменяются сдвигом по горизонтали
	stage.clip_contents = false
	center.add_child(stage)
	var vp := app.get_viewport_rect().size
	var w := minf(440.0, vp.x - side * 2)
	var h := vp.y - safe.x - safe.y - 32
	if not UI.compact():
		h = minf(h, 720)
	stage.custom_minimum_size = Vector2(w, h)
	var state := {"page": 0, "nick": null, "err": null, "waiting": false}
	var pages: Array[Control] = []

	var show_page := func(i: int, dir: int) -> void:
		var old: Control = pages[state.page]
		var nw: Control = pages[i]
		state.page = i
		nw.visible = true
		if FX.on() and old != nw:
			nw.position.x = 60 * dir
			nw.modulate.a = 0
			var tw := nw.create_tween().set_parallel().set_trans(Tween.TRANS_CUBIC).set_ease(Tween.EASE_OUT)
			tw.tween_property(nw, "position:x", 0.0, 0.22)
			tw.tween_property(nw, "modulate:a", 1.0, 0.22)
			var to := old.create_tween().set_parallel().set_trans(Tween.TRANS_CUBIC).set_ease(Tween.EASE_IN)
			to.tween_property(old, "position:x", -60.0 * dir, 0.2)
			to.tween_property(old, "modulate:a", 0.0, 0.16)
			to.chain().tween_callback(func(): old.visible = false; old.position.x = 0)
		elif old != nw:
			old.visible = false
		if i == 1 and state.nick:
			(state.nick as LineEdit).grab_focus.call_deferred()

	var mk_page := func() -> VBoxContainer:
		var pv := UI.vbox(16)
		pv.set_anchors_preset(Control.PRESET_FULL_RECT)
		pv.visible = pages.is_empty()
		stage.add_child(pv)
		pages.append(pv)
		return pv

	# иллюстрация страницы: пейзаж в стиле LANTERN во всю ширину, снизу растворяется в ночном небе
	var art := func(pv: VBoxContainer, seed_text: String, mode: String, frac: float) -> Control:
		var a := Control.new()
		a.custom_minimum_size.y = maxf(120.0, h * frac)
		a.mouse_filter = Control.MOUSE_FILTER_IGNORE
		var cov := Cover.make(seed_text, mode, 28 if not UI.compact() else 0, 0.35)
		(cov.material as ShaderMaterial).set_shader_parameter("fade", 1.0)
		cov.set_anchors_preset(Control.PRESET_FULL_RECT)
		if UI.compact():
			cov.offset_left = -side
			cov.offset_right = side
			cov.offset_top = -(safe.x + 8)
		a.add_child(cov)
		pv.add_child(a)
		return a

	var dots := func(n: int) -> Control:
		var r := UI.hbox(6)
		r.alignment = BoxContainer.ALIGNMENT_CENTER
		for i in 3:
			var d := Panel.new()
			d.custom_minimum_size = Vector2(22 if i == n else 8, 8)
			d.add_theme_stylebox_override("panel", UI.box(UI.accent() if i == n else Color(1, 1, 1, 0.18), 99))
			r.add_child(d)
		return r

	# --- 1. знакомство
	var p1: VBoxContainer = mk_page.call()
	var a1: Control = art.call(p1, "lantern-camp-7", "other", 0.46)
	var top1 := UI.hbox(0)
	top1.set_anchors_preset(Control.PRESET_TOP_WIDE)
	top1.add_child(UI.spacer())
	var later := UI.button(L.t("Позже"), "ghost", func(): ov.queue_free())
	later.add_theme_color_override("font_color", Color(1, 1, 1, 0.85))
	top1.add_child(later)
	a1.add_child(top1)
	var li := LanternIcon.new().setup(7, {"flame": "flame_amber"}, 124)
	li.set_anchors_preset(Control.PRESET_CENTER_BOTTOM)
	li.offset_left = -62
	li.offset_right = 62
	li.offset_top = -112
	li.offset_bottom = 12
	a1.add_child(li)
	var t1 := UI.label("LANTERN", 40, UI.TEXT, UI.font_title)
	t1.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	p1.add_child(t1)
	var s1 := UI.para(L.t("Нажал — и ты в игре с друзьями. Java и Bedrock, телефон и ПК. Без VPN и настроек."), 16, UI.MUTED)
	s1.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	p1.add_child(s1)
	p1.add_child(UI.spacer(false))
	p1.add_child(dots.call(0))
	var b1 := UI.button(L.t("Начать"), "primary", func(): show_page.call(1, 1))
	b1.custom_minimum_size.y = 56
	b1.add_theme_font_size_override("font_size", 17)
	p1.add_child(b1)
	var disc := UI.para(Cfg.DISCLAIMER, 10, UI.DIM)
	disc.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	p1.add_child(disc)

	# --- 2. ник
	var p2: VBoxContainer = mk_page.call()
	var a2: Control = art.call(p2, "lantern-nick-3", "roleplay", 0.27)
	var top2 := UI.hbox(0)
	top2.set_anchors_preset(Control.PRESET_TOP_WIDE)
	top2.add_child(UI.icon_button("back", func(): show_page.call(0, -1), L.t("Назад")))
	a2.add_child(top2)
	p2.add_child(UI.h1(L.t("Как вас зовут в игре?")))
	p2.add_child(UI.para(L.t("Ник видят друзья. Пароль не нужен. Вы получите тег вида Ник#4821 — по нему вас добавят в друзья."), 15, UI.MUTED))
	p2.add_child(UI.gap(4))
	var e := UI.line_edit("", L.t("Ник: 3–16 букв, цифр или _"))
	e.max_length = 16
	e.custom_minimum_size.y = 56
	e.add_theme_font_override("font", UI.font_bold)
	e.add_theme_font_size_override("font_size", 20)
	p2.add_child(e)
	state.nick = e
	var err := UI.para("", 14, Color("ff9d9d"))
	err.visible = false
	p2.add_child(err)
	p2.add_child(UI.spacer(false))
	p2.add_child(dots.call(1))
	var ok := UI.button(L.t("Готово"), "primary")
	ok.custom_minimum_size.y = 56
	ok.add_theme_font_size_override("font_size", 17)
	ok.pressed.connect(func():
		err.visible = false
		state.waiting = true
		ok.text = L.t("Создаём профиль…")
		Social.register(e.text))
	p2.add_child(ok)
	e.text_submitted.connect(func(_t): ok.pressed.emit())

	# --- 3. как играть
	var p3: VBoxContainer = mk_page.call()
	art.call(p3, "lantern-ready-5", "survival", 0.24)
	var hi := UI.h1(L.t("Готово!"))
	p3.add_child(hi)
	var tagl := UI.para("", 15, UI.MUTED)
	p3.add_child(tagl)
	p3.add_child(UI.gap(4))
	p3.add_child(UI.caption(L.t("Как играть вместе")))
	var go_host := func():
		ov.queue_free()
		if app.has_method("play_option"):
			app.go("setup")
	p3.add_child(_how_card("1", "plus", UI.accent(), L.t("Создайте мир"), L.t("Откройте свой мир в Minecraft или запустите сервер на ПК — получите код. Друзья зайдут по нему.") if not Cfg.mobile() else L.t("Откройте свой мир в Minecraft — LANTERN даст код, по нему зайдут друзья."), go_host))
	p3.add_child(_how_card("2", "play", UI.BEDROCK, L.t("Или зайдите к другу"), L.t("Введите код из 6 символов, который прислал друг, — мир появится прямо в Minecraft."), func(): ov.queue_free(); app.go("join")))
	p3.add_child(UI.spacer(false))
	p3.add_child(dots.call(2))
	var b3 := UI.button(L.t("Осмотрюсь сам"), "secondary", func(): ov.queue_free())
	b3.custom_minimum_size.y = 52
	p3.add_child(b3)

	ov.set_meta("page_back", func() -> bool:
		if state.page == 1:
			show_page.call(0, -1)
			return true
		return false)
	var on_fail := func(what, text):
		if what == "register":
			err.text = text
			err.visible = true
			ok.text = L.t("Готово")
			state.waiting = false
	var on_change := func(what):
		if what == "me" and Social.online() and state.waiting:
			state.waiting = false
			tagl.text = L.t("Ваш тег — %s. Отправьте его друзьям, чтобы они добавили вас.") % Social.me.get("tag", "")
			show_page.call(2, 1)
			FX.burst(ov, Vector2(ov.size.x / 2, ov.size.y * 0.25), 30)
	Social.failed.connect(on_fail)
	Social.changed.connect(on_change)
	ov.tree_exiting.connect(func():
		Social.failed.disconnect(on_fail)
		Social.changed.disconnect(on_change))
	if FX.on():
		ov.modulate.a = 0
		ov.create_tween().tween_property(ov, "modulate:a", 1.0, 0.25)
		FX.stagger(p1, 0.05)


static func _how_card(n: String, icon: String, col: Color, title: String, desc: String, cb: Callable) -> Control:
	var p := PanelContainer.new()
	var st := UI.glass(2, UI.R_XL, 0, col)
	UI._hpad(st, 16, 16)
	p.add_theme_stylebox_override("panel", st)
	var row := UI.hbox(14)
	p.add_child(row)
	var tile := PanelContainer.new()
	tile.custom_minimum_size = Vector2(48, 48)
	tile.size_flags_vertical = Control.SIZE_SHRINK_BEGIN
	tile.add_theme_stylebox_override("panel", UI.box(col, UI.R_M))
	var ic := UIcon.make(icon, 24, UI.ON_ACCENT)
	ic.stroke = 2.6
	ic.size_flags_horizontal = Control.SIZE_SHRINK_CENTER
	ic.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	tile.add_child(ic)
	row.add_child(tile)
	var v := UI.vbox(4)
	v.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	v.add_child(UI.label(title, 17, UI.TEXT, UI.font_black))
	v.add_child(UI.para(desc, 14, UI.MUTED))
	row.add_child(v)
	UI.tappable(p, cb, st)
	return p


# ------------------------------------------------------------------ лист покупки (как в Google Play)
static func purchase(app: Control, product: String, on_success := Callable()) -> void:
	var info: Dictionary = PRODUCTS.get(product, {})
	var v := sheet(app, "", "", true)
	var ov: Control = v.get_parent().get_parent()
	while not (ov is ColorRect):
		ov = ov.get_parent()
	var top := UI.hbox(10)
	top.add_child(UI.label("▶ Google Play", 15, Color("3c4043"), UI.font_bold))
	top.add_child(UI.spacer())
	if Social.demo:
		top.add_child(UI.pill(L.t("ДЕМО"), Color("d93025")))
	v.add_child(top)
	var row := UI.hbox(14)
	row.add_child(UI.texture("res://assets/img/icon.png", 48 if UI.compact() else 56, 48 if UI.compact() else 56))
	var tv := UI.vbox(2)
	tv.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	var tt := UI.label(info.get("title", product), 17, Color("202124"), UI.font_bold)
	tt.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	tt.custom_minimum_size.x = 72 if UI.compact() else 120
	tv.add_child(tt)
	tv.add_child(UI.para("LANTERN (by Yompix)", 13, Color("5f6368")))     # 1.8.2: перенос на 320 dp
	row.add_child(tv)
	var pv := UI.vbox(0)
	var pr := UI.label(info.get("uah", ""), 17, Color("202124"), UI.font_bold)
	pr.horizontal_alignment = HORIZONTAL_ALIGNMENT_RIGHT
	pv.add_child(pr)
	var pu := UI.label(info.get("usd", ""), 13, Color("5f6368"))
	pu.horizontal_alignment = HORIZONTAL_ALIGNMENT_RIGHT
	pv.add_child(pu)
	row.add_child(pv)
	v.add_child(row)
	v.add_child(UI.para(info.get("desc", ""), 14, Color("5f6368")))
	var line := ColorRect.new()
	line.color = Color("e0e0e0")
	line.custom_minimum_size.y = 1
	v.add_child(line)
	v.add_child(UI.para(L.t("Способ оплаты: ") + (L.t("тестовая карта (ДЕМО — деньги не списываются)") if Social.demo else "Google Play"), 13, Color("3c4043")))
	if product == "lantern_pro_month":
		v.add_child(UI.para(L.t("Подписку можно отменить в любой момент в Google Play."), 12, Color("5f6368")))
	var buy := UI.button(L.t("Купить"), "primary")
	for st in [["normal", Color("1a73e8")], ["hover", Color("3b87ee")], ["pressed", Color("1557b0")], ["hover_pressed", Color("1557b0")], ["disabled", Color("8ab4f8")]]:
		buy.add_theme_stylebox_override(st[0], UI.box(st[1], 99, 12))
	for k in ["font_color", "font_hover_color", "font_pressed_color", "font_hover_pressed_color", "font_disabled_color"]:
		buy.add_theme_color_override(k, Color.WHITE)
	v.add_child(buy)
	var cancel := UI.button(L.t("Отмена"), "ghost", func(): close_sheet(app))
	cancel.add_theme_color_override("font_color", Color("1a73e8"))
	v.add_child(cancel)
	buy.pressed.connect(func():
		if not Social.demo:
			Store.buy(product)
			close_sheet(app)
			return
		buy.disabled = true
		buy.text = L.t("Обработка…")
		Social.request("demo_purchase", {"product": product})
		await app.get_tree().create_timer(0.9).timeout
		if not is_instance_valid(v):
			return
		for c in v.get_children():
			c.queue_free()
		var okb := UI.vbox(10)
		var ring := PanelContainer.new()
		ring.custom_minimum_size = Vector2(80, 80)
		ring.size_flags_horizontal = Control.SIZE_SHRINK_CENTER
		ring.add_theme_stylebox_override("panel", UI.box(Color("1e8e3e"), 99))
		var ck := UIcon.make("check", 40, Color.WHITE)
		ck.stroke = 3.0
		ck.size_flags_horizontal = Control.SIZE_SHRINK_CENTER
		ck.size_flags_vertical = Control.SIZE_SHRINK_CENTER
		ring.add_child(ck)
		okb.add_child(ring)
		var tl := UI.label(L.t("Покупка выполнена"), 20, Color("202124"), UI.font_bold)
		tl.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
		okb.add_child(tl)
		var sub := UI.para(info.get("title", "") + L.t(" — уже у вас") + (L.t("  (ДЕМО)") if Social.demo else ""), 14, Color("5f6368"))
		sub.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
		okb.add_child(sub)
		okb.add_child(UI.gap(8))
		v.add_child(okb)
		if FX.on():
			ring.pivot_offset = Vector2(40, 40)
			ring.scale = Vector2(0.2, 0.2)
			ring.create_tween().set_trans(Tween.TRANS_BACK).set_ease(Tween.EASE_OUT).tween_property(ring, "scale", Vector2.ONE, 0.4)
			ring.create_tween().tween_callback(func(): FX.burst(ov, ring.get_global_rect().get_center() - ov.global_position, 30))
		if on_success.is_valid():
			on_success.call()
		if Cfg.autotest == "":
			await app.get_tree().create_timer(1.8).timeout
			if is_instance_valid(ov):
				_fade_out(ov))


# ------------------------------------------------------------------ тестовая реклама
static func ad(app: Control, rewarded: bool, on_done := Callable()) -> void:
	var ov := _dim(app, 0.92)
	var p := PanelContainer.new()
	p.add_theme_stylebox_override("panel", UI.solid(UI.R_XL, 24))
	p.custom_minimum_size = Vector2(UI.dialog_width(560), 380)
	var v := UI.vbox(14)
	p.add_child(v)
	var top := UI.hbox(8)
	top.add_child(UI.pill(L.t("Тестовая реклама"), UI.YELLOW))
	top.add_child(UI.spacer())
	var cnt := UI.label("5", 18, UI.TEXT, UI.font_black)
	top.add_child(cnt)
	v.add_child(top)
	var big := UI.label(L.t("Здесь будет реклама"), 28, UI.TEXT, UI.font_black)
	big.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	big.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	big.custom_minimum_size.x = 120
	big.size_flags_vertical = Control.SIZE_EXPAND_FILL
	big.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
	v.add_child(big)
	var sub := UI.para((L.t("Досмотрите до конца — получите +20 искр.") if rewarded else L.t("Реклама показывается только после выхода из мира. В Pro её нет.")) + L.t(" Это ДЕМО: настоящей рекламы и трекинга нет."), 14, UI.MUTED)
	sub.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	v.add_child(sub)
	var bar := ProgressBar.new()
	bar.show_percentage = false
	bar.max_value = 5.0
	bar.custom_minimum_size.y = 6
	v.add_child(bar)
	var close := UI.button(L.t("Закрыть"), "secondary", func(): ov.queue_free())
	close.disabled = true
	v.add_child(close)
	_center(ov, p)
	var tw := bar.create_tween()
	tw.tween_property(bar, "value", 5.0, 5.0 if Cfg.autotest == "" else 2.0)
	for i in 5:
		cnt.text = str(5 - i)
		await app.get_tree().create_timer(1.0 if Cfg.autotest == "" else 0.4).timeout
		if not is_instance_valid(ov):
			return
	cnt.text = "✓"
	close.disabled = false
	close.text = L.t("Забрать +20 искр") if rewarded else L.t("Закрыть")
	if rewarded:
		close.remove_theme_stylebox_override("normal")
		Social.request("ad_reward")
	if on_done.is_valid():
		on_done.call()
