class_name Overlays
extends RefCounted
## Окна поверх приложения: регистрация ника, лист покупки (как в Google Play), тестовая реклама.

const PRODUCTS := {
	"sparks_500": {"title": "500 искр", "desc": "Искры для магазина косметики", "uah": "79,99 ₴", "usd": "$1.99"},
	"sparks_1200": {"title": "1200 искр", "desc": "Выгоднее на 20%", "uah": "169,99 ₴", "usd": "$3.99"},
	"lantern_pass_s1": {"title": "Пропуск сезона «Первые огни»", "desc": "Премиум-путь сезона: 9 эксклюзивных предметов", "uah": "149,99 ₴", "usd": "$3.49"},
	"lantern_pro_month": {"title": "LANTERN Pro — месяц", "desc": "Без рекламы, свой код комнаты, облачные копии миров, особая косметика", "uah": "99,99 ₴/мес", "usd": "$2.49/mo"},
}


static func _dim(app: Control) -> ColorRect:
	var ov := ColorRect.new()
	ov.color = Color(0, 0, 0, 0)
	ov.set_anchors_preset(Control.PRESET_FULL_RECT)
	ov.mouse_filter = Control.MOUSE_FILTER_STOP
	app.add_child(ov)
	ov.create_tween().tween_property(ov, "color:a", 0.62, 0.2)
	return ov


static func _center(ov: Control, child: Control) -> void:
	var cc := CenterContainer.new()
	cc.set_anchors_preset(Control.PRESET_FULL_RECT)
	ov.add_child(cc)
	cc.add_child(child)
	child.pivot_offset = child.size / 2
	child.scale = Vector2(0.94, 0.94)
	child.modulate.a = 0
	var tw := child.create_tween().set_parallel().set_trans(Tween.TRANS_BACK).set_ease(Tween.EASE_OUT)
	tw.tween_property(child, "scale", Vector2.ONE, 0.25)
	tw.tween_property(child, "modulate:a", 1.0, 0.18)


# ------------------------------------------------------------------ ник при первом запуске
static func register(app: Control) -> void:
	if app.has_node("RegisterOverlay"):
		return
	var ov := _dim(app)
	ov.name = "RegisterOverlay"
	var p := UI.panel(30, UI.PANEL, 22)
	p.custom_minimum_size = Vector2(520, 0)
	var v := UI.vbox(14)
	p.add_child(v)
	var icon := LanternIcon.new().setup(3, null, 96)
	icon.size_flags_horizontal = Control.SIZE_SHRINK_CENTER
	v.add_child(icon)
	var t := UI.h2("Как вас зовут в игре?")
	t.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	v.add_child(t)
	var d := UI.para("Ник видят друзья. Пароль не нужен — профиль привязан к этому устройству. Получите тег вида Ник#4821, по нему вас добавят в друзья.", 14, UI.MUTED)
	d.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	v.add_child(d)
	var e := UI.line_edit("", "Ник: 3–16 букв, цифр или _")
	e.max_length = 16
	e.add_theme_font_override("font", UI.font_bold)
	e.add_theme_font_size_override("font_size", 20)
	v.add_child(e)
	var err := UI.label("", 14, Color("ffb3ba"))
	v.add_child(err)
	var row := UI.hbox(10)
	var later := UI.button("Позже", "ghost", func(): ov.queue_free())
	var ok := UI.button("Готово", "primary", func():
		err.text = ""
		Social.register(e.text))
	ok.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	row.add_child(later)
	row.add_child(ok)
	v.add_child(row)
	e.text_submitted.connect(func(_t): ok.pressed.emit())
	var on_fail := func(what, text):
		if what == "register":
			err.text = text
	var on_change := func(what):
		if what == "me" and Social.online():
			ov.queue_free()
	Social.failed.connect(on_fail)
	Social.changed.connect(on_change)
	ov.tree_exiting.connect(func():
		Social.failed.disconnect(on_fail)
		Social.changed.disconnect(on_change))
	_center(ov, p)
	e.grab_focus.call_deferred()


# ------------------------------------------------------------------ лист покупки
static func purchase(app: Control, product: String, on_success := Callable()) -> void:
	var info: Dictionary = PRODUCTS.get(product, {})
	var ov := _dim(app)
	var sheet := PanelContainer.new()
	var st := UI.box(Color("f7f8fa"), 18, 26)
	sheet.add_theme_stylebox_override("panel", st)
	sheet.custom_minimum_size = Vector2(520, 0)
	var v := UI.vbox(12)
	sheet.add_child(v)
	var top := UI.hbox(10)
	var gp := UI.label("▶ Google Play", 15, Color("3c4043"), UI.font_bold)
	top.add_child(gp)
	top.add_child(UI.spacer())
	if Social.demo:
		top.add_child(UI.pill("ДЕМО", Color("d93025")))
	v.add_child(top)
	var row := UI.hbox(14)
	var ic := UI.texture("res://assets/img/icon.png", 56, 56)
	row.add_child(ic)
	var tv := UI.vbox(2)
	tv.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	tv.add_child(UI.label(info.get("title", product), 18, Color("202124"), UI.font_bold))
	tv.add_child(UI.label("LANTERN (by Yompix)", 13, Color("5f6368")))
	row.add_child(tv)
	var pv := UI.vbox(0)
	pv.add_child(UI.label(info.get("uah", ""), 18, Color("202124"), UI.font_bold))
	pv.add_child(UI.label(info.get("usd", ""), 13, Color("5f6368")))
	row.add_child(pv)
	v.add_child(row)
	v.add_child(UI.para(info.get("desc", ""), 14, Color("5f6368")))
	var line := ColorRect.new()
	line.color = Color("e0e0e0")
	line.custom_minimum_size.y = 1
	v.add_child(line)
	v.add_child(UI.label("Способ оплаты: " + ("тестовая карта (ДЕМО — деньги не списываются)" if Social.demo else "Google Play"), 13, Color("3c4043")))
	if product == "lantern_pro_month":
		v.add_child(UI.label("Подписку можно отменить в любой момент в Google Play.", 12, Color("5f6368")))
	var buy := UI.button("Купить", "primary")
	buy.add_theme_stylebox_override("normal", UI.box(Color("1a73e8"), 10, 14))
	buy.add_theme_stylebox_override("hover", UI.box(Color("3b87ee"), 10, 14))
	buy.add_theme_stylebox_override("pressed", UI.box(Color("1557b0"), 10, 14))
	buy.add_theme_color_override("font_color", Color.WHITE)
	buy.add_theme_color_override("font_hover_color", Color.WHITE)
	v.add_child(buy)
	var cancel := UI.button("Отмена", "ghost", func(): ov.queue_free())
	cancel.add_theme_color_override("font_color", Color("1a73e8"))
	v.add_child(cancel)
	buy.pressed.connect(func():
		if not Social.demo:
			Store.buy(product)
			ov.queue_free()
			return
		buy.disabled = true
		buy.text = "Обработка…"
		Social.request("demo_purchase", {"product": product})
		await app.get_tree().create_timer(0.9).timeout
		for c in v.get_children():
			c.queue_free()
		var ok := UI.vbox(10)
		var check := UI.label("✓", 64, Color("1e8e3e"), UI.font_black)
		check.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
		ok.add_child(check)
		var tl := UI.label("Покупка выполнена", 20, Color("202124"), UI.font_bold)
		tl.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
		ok.add_child(tl)
		var sub := UI.label(info.get("title", "") + " — уже у вас" + ("  (ДЕМО)" if Social.demo else ""), 14, Color("5f6368"))
		sub.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
		ok.add_child(sub)
		v.add_child(ok)
		check.pivot_offset = Vector2(40, 40)
		check.scale = Vector2(0.2, 0.2)
		check.create_tween().set_trans(Tween.TRANS_BACK).set_ease(Tween.EASE_OUT).tween_property(check, "scale", Vector2.ONE, 0.4)
		if on_success.is_valid():
			on_success.call()
		if Cfg.autotest == "":
			await app.get_tree().create_timer(1.8).timeout
			if is_instance_valid(ov):
				ov.queue_free())
	_center(ov, sheet)


# ------------------------------------------------------------------ тестовая реклама
static func ad(app: Control, rewarded: bool, on_done := Callable()) -> void:
	var ov := _dim(app)
	ov.color = Color(0, 0, 0, 0.92)
	var p := UI.panel(30, Color("1b2333"), 18)
	p.custom_minimum_size = Vector2(620, 380)
	var v := UI.vbox(14)
	p.add_child(v)
	var top := UI.hbox(8)
	top.add_child(UI.pill("Тестовая реклама", UI.YELLOW))
	top.add_child(UI.spacer())
	var cnt := UI.label("5", 18, UI.TEXT, UI.font_black)
	top.add_child(cnt)
	v.add_child(top)
	var big := UI.label("Здесь будет реклама", 30, UI.TEXT, UI.font_black)
	big.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	big.size_flags_vertical = Control.SIZE_EXPAND_FILL
	big.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
	v.add_child(big)
	var sub := UI.para(("Досмотрите до конца — получите +20 искр." if rewarded else "Реклама показывается только после выхода из мира. В Pro её нет.") + " Это ДЕМО: настоящей рекламы и трекинга нет.", 14, UI.MUTED)
	sub.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	v.add_child(sub)
	var bar := ProgressBar.new()
	bar.show_percentage = false
	bar.max_value = 5.0
	bar.custom_minimum_size.y = 8
	v.add_child(bar)
	var close := UI.button("Закрыть", "secondary", func(): ov.queue_free())
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
	close.text = "Забрать +20 искр" if rewarded else "Закрыть"
	if rewarded:
		Social.request("ad_reward")
	if on_done.is_valid():
		on_done.call()
