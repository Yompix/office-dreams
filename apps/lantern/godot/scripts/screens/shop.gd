extends VBoxContainer
## Магазин косметики за искры + LANTERN Pro и наборы искр (покупки — Google Play; сейчас ДЕМО).
## Вкладки — строка чипов с прокруткой вбок, товары — сетка карточек одинаковой высоты.

var app
var arg := {}

const KINDS := [["flame", "Пламя"], ["shape", "Фонарь"], ["nick", "Цвет ника"], ["frame", "Рамки"], ["sticker", "Стикеры"], ["campfire", "Костёр"], ["pro", "Pro и искры"]]
const NICK_COLORS := {"nick_white": "eef1f7", "nick_gold": "ffcf5a", "nick_sky": "7cc4ff", "nick_mint": "6ff0c0", "nick_flame": "ff7a3d", "nick_s1": "ffa8c8"}

var _kind := "flame"
var _tabs: ScrollContainer
var _body: VBoxContainer
var _last_kind := ""


func _ready() -> void:
	add_theme_constant_override("separation", 14)
	if arg is Dictionary and arg.has("kind"):
		_kind = arg.kind
	if Social.demo:
		add_child(preload("res://scripts/screens/quests.gd")._demo_banner())
	var kinds: Array = KINDS if Cfg.paid() else KINDS.filter(func(k): return k[0] != "pro")
	if _kind == "pro" and not Cfg.paid():
		_kind = "flame"
	_tabs = UI.tabs(kinds, _kind, func(k): _kind = k; _render())
	add_child(_tabs)
	_body = UI.vbox(14)
	_body.size_flags_vertical = Control.SIZE_EXPAND_FILL
	add_child(_body)
	var foot := UI.para(L.t("Только внешний вид — никаких преимуществ в игре и случайных наград."), 12, UI.DIM)
	foot.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	add_child(foot)
	Social.changed.connect(func(w): if w in ["shop", "eco", "conn"] and is_inside_tree() and not _typing(): _render())
	Social.request("shop")
	Social.request("eco")
	_render()


func _scroll_wrap(c: Control) -> Control:
	# на телефоне прокручивается весь экран — вложенная прокрутка не нужна
	if UI.compact():
		return c
	var sc := ScrollContainer.new()
	sc.size_flags_vertical = Control.SIZE_EXPAND_FILL
	sc.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	c.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	sc.add_child(c)
	return sc


func _render() -> void:
	UI.tabs_select(_tabs, _kind)
	var changed := _kind != _last_kind
	_last_kind = _kind
	for c in _body.get_children():
		c.queue_free()
	if not Social.online():
		_body.add_child(UI.empty("wifi", L.t("Магазин работает через интернет"), L.t("Повторить"), func(): Social.reconnect(), L.t("Подключитесь к сети — и всё появится.")))
		return
	if _kind == "pro":
		_pro()
		return
	var g := GridContainer.new()
	g.columns = 2 if UI.compact() else 4
	g.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	g.add_theme_constant_override("h_separation", 12)
	g.add_theme_constant_override("v_separation", 12)
	var items: Array = Social.shop.get("items", [])
	if items.is_empty():
		for i in 4:
			g.add_child(FX.skeleton(210, UI.R_L))
	var equip: Dictionary = Social.shop.get("equip", {})
	for it in items:
		if it.kind != _kind:
			continue
		g.add_child(_card(it, equip))
	_body.add_child(_scroll_wrap(g))
	if changed:
		FX.stagger(g, 0.03, Vector2(0, 16))


func _preview(it: Dictionary) -> Control:
	var box := CenterContainer.new()
	box.custom_minimum_size.y = 96
	box.mouse_filter = Control.MOUSE_FILTER_IGNORE
	match it.kind:
		"flame":
			box.add_child(LanternIcon.new().setup(6, {"flame": it.id}, 84))
		"shape":
			box.add_child(LanternIcon.new().setup(5, {"shape": it.id, "flame": Social.eco.get("equip", {}).get("flame", "")}, 84))
		"nick":
			box.add_child(UI.label(Social.me.get("nick", L.t("Ник")), 22, Color(NICK_COLORS.get(it.id, "eef1f7")), UI.font_black))
		"frame":
			var p := PanelContainer.new()
			var colors := {"frame_wood": "8a5a32", "frame_stone": "8a8f9c", "frame_gold": "ffcf5a", "frame_night": "5a6cff", "frame_s1": "ff8a3d", "frame_none": "2c3345"}
			p.add_theme_stylebox_override("panel", UI.box(UI.CARD_HI, 10, 10, Color(colors.get(it.id, "2c3345")), 4))
			p.add_child(UI.label(L.t("Мой мир"), 14, UI.TEXT, UI.font_bold))
			box.add_child(p)
		"sticker":
			box.add_child(UI.label({"sticker_wave": L.t("Привет! ♥"), "sticker_creeper": L.t("БУМ! ✦"), "sticker_heart": "♥ ♥ ♥", "sticker_s1": L.t("★ Сезон ★")}.get(it.id, "★"), 20, UI.AMBER, UI.font_black))
		"campfire":
			box.add_child(UI.label({"camp_logs": L.t("▲ костёр"), "camp_tent": L.t("▲ палатка"), "camp_stars": "✦ ✦ ✦", "camp_snow": "❄ ❄ ❄", "camp_fireflies": "• ✦ •", "camp_s1": L.t("❦ осень"), "camp_s1b": L.t("≈ сияние")}.get(it.id, "▲"), 20, UI.AMBER, UI.font_black))
	return box


func _card(it: Dictionary, equip: Dictionary) -> Control:
	var worn: bool = equip.get(it.kind, "") == it.id
	var p := PanelContainer.new()
	var st := UI.glass(2 if worn else 1, UI.R_XL, 0, UI.accent() if worn else Color(0, 0, 0, 0))
	UI._hpad(st, 12, 12)
	p.add_theme_stylebox_override("panel", st)
	if worn:
		UI.halo(p, UI.accent(), UI.R_XL, 0.75)
	p.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	var v := UI.vbox(8)
	p.add_child(v)
	var stage := PanelContainer.new()
	stage.add_theme_stylebox_override("panel", UI.box(Color(0.02, 0.03, 0.08, 0.45), UI.R_L, 0, Color(1, 1, 1, 0.06), 1))
	stage.add_child(_preview(it))
	v.add_child(stage)
	var nl := UI.label(it.name, 14, UI.TEXT, UI.font_bold)
	nl.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	nl.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
	nl.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	nl.custom_minimum_size = Vector2(60, 40)
	nl.max_lines_visible = 2
	v.add_child(nl)
	var b: Button
	if it.get("season", null) != null and not it.owned:
		b = UI.button(L.t("Сезон"), "ghost", func(): app.go("quests", true, {"tab": "season"}), "star")
	elif not it.owned:
		var can: bool = int(Social.eco.get("sparks", 0)) >= int(it.price)
		b = UI.button("%d" % int(it.price), "tonal" if can else "secondary", func():
			if not can:
				app.toast(L.t("Не хватает искр — выполняйте задания"), UI.YELLOW)
				return
			Social.request("buy", {"item": it.id})
			FX.burst(app, p.get_global_rect().get_center() - app.global_position, 18), "spark")
		b.tooltip_text = "" if can else L.t("Не хватает искр — выполняйте задания")
	elif it.kind == "sticker":
		b = UI.button(L.t("Есть"), "ghost", Callable(), "check")
		b.disabled = true
	elif worn:
		b = UI.button(L.t("Надето"), "tonal", Callable(), "check")
		b.disabled = true
	else:
		b = UI.button(L.t("Надеть"), "secondary", func(): Social.request("wear", {"item": it.id}))
	b.clip_text = true
	b.add_theme_font_size_override("font_size", 14)
	b.custom_minimum_size.y = 40
	v.add_child(b)
	return p


func _pro() -> void:
	var cols := UI.cols(20)
	cols.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_body.add_child(_scroll_wrap(cols))
	var pro := PanelContainer.new()
	var pst := UI.glass(3, UI.R_XL, 0, UI.AMBER)
	UI._hpad(pst, 20, 20)
	pro.add_theme_stylebox_override("panel", pst)
	pro.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	var v := UI.vbox(12)
	pro.add_child(v)
	var hr := UI.hbox(14)
	var pli := LanternIcon.new().setup(9, {"flame": "flame_s1legend", "shape": "shape_star"}, 72)
	hr.add_child(pli)
	var hv := UI.vbox(2)
	hv.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	hv.add_child(UI.label("LANTERN Pro", 26, UI.AMBER, UI.font_black))
	var active: bool = Social.eco.get("pro", false)
	var until := int(Social.eco.get("pro_until", 0))
	hv.add_child(UI.label((L.t("Активна до ") + Time.get_date_string_from_unix_time(until / 1000) + (L.t(" (ДЕМО)") if Social.demo else "")) if active else L.t("99,99 ₴ в месяц · $2.49"), 14, UI.JAVA if active else UI.MUTED, UI.font_bold))
	hr.add_child(hv)
	v.add_child(hr)
	for line in [["close", L.t("Без рекламы совсем")], ["lock", L.t("Свой код комнаты, например LUNA7")], ["cloud", L.t("Облачные копии миров")], ["star", L.t("Особая косметика каждый месяц")], ["heart", L.t("Поддержка автора LANTERN — Yompix")]]:
		var r := UI.hbox(12)
		var ic := UI.icon("check", 18, UI.AMBER)
		ic.size_flags_vertical = Control.SIZE_SHRINK_CENTER
		r.add_child(ic)
		var ll := UI.para(line[1], 15, UI.TEXT)
		ll.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		r.add_child(ll)
		v.add_child(r)
	v.add_child(UI.gap(4))
	if active:
		v.add_child(UI.pill(L.t("✓ Pro активна"), UI.JAVA))
		v.add_child(UI.caption(L.t("Свой код комнаты")))
		var vr := UI.hbox(8)
		var ve := UI.line_edit(String(Social.eco.get("vanity", "")), L.t("Например LUNA7"))
		ve.max_length = 8
		ve.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		ve.add_theme_font_override("font", UI.font_black)
		vr.add_child(ve)
		vr.add_child(UI.button(L.t("Сохранить"), "tonal", func(): Social.request("vanity_set", {"code": ve.text})))
		if String(Social.eco.get("vanity", "")) != "":
			vr.add_child(UI.icon_button("close", func(): Social.request("vanity_clear"), L.t("Убрать свой код"), UI.MUTED))
		v.add_child(vr)
		v.add_child(UI.para(L.t("3–8 символов: латиница и цифры 2–9. Друзья вводят его вместо случайного кода. Работает, пока активна Pro; один мир за раз."), 12, UI.DIM))
		v.add_child(UI.button(L.t("Облачные копии миров"), "secondary", func(): app.go("backups", true), "cloud"))
	else:
		var buy := UI.button(L.t("Оформить Pro"), "primary", func(): _buy("lantern_pro_month"))
		buy.custom_minimum_size.y = 52
		v.add_child(buy)
	cols.add_child(pro)
	var right := UI.vbox(10)
	right.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	right.add_child(UI.section(L.t("Наборы искр")))
	for pk in [["sparks_500", L.t("500 искр"), "79,99 ₴"], ["sparks_1200", L.t("1200 искр"), L.t("169,99 ₴ · выгоднее на 20%")]]:
		var tile := UI.icon_tile("spark", UI.AMBER, 48, 26)
		right.add_child(UI.list_row(tile, pk[1], pk[2], UI.button(L.t("Купить"), "secondary", func(): _buy(pk[0]))))
	right.add_child(UI.para(L.t("Искры можно и не покупать: задания дня, серия и сезон дают их каждый день. Покупки — только через Google Play, родители могут ограничить их в настройках Play."), 13, UI.DIM))
	cols.add_child(right)
	FX.stagger(cols, 0.05)


func _buy(product: String) -> void:
	if not Social.demo:
		Store.buy(product)
		return
	Overlays.purchase(app, product, func(): Social.request("shop"))


## Не перерисовывать, пока игрок печатает свой код (иначе поле сбросится)
func _typing() -> bool:
	var f := get_viewport().gui_get_focus_owner()
	return f is LineEdit and is_ancestor_of(f)
