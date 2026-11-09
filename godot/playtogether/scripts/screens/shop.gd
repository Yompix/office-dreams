extends VBoxContainer
## Магазин косметики за искры + LANTERN Pro и наборы искр (покупки — Google Play; сейчас ДЕМО).

var app
var arg := {}

const KINDS := [["flame", "Пламя"], ["shape", "Фонарь"], ["nick", "Цвет ника"], ["frame", "Рамки"], ["sticker", "Стикеры"], ["campfire", "Костёр"], ["pro", "Pro и искры"]]
const NICK_COLORS := {"nick_white": "eef1f7", "nick_gold": "ffcf5a", "nick_sky": "7cc4ff", "nick_mint": "6ff0c0", "nick_flame": "ff7a3d", "nick_s1": "ffa8c8"}

var _kind := "flame"
var _tabs := {}
var _body: Control
var _sparks: Label


func _ready() -> void:
	add_theme_constant_override("separation", 14)
	var head := UI.hbox(10)
	head.add_child(UI.h1("Магазин"))
	head.add_child(UI.spacer())
	_sparks = UI.label("", 22, Color("ffb648"), UI.font_black)
	head.add_child(_sparks)
	add_child(head)
	if Social.demo:
		add_child(preload("res://scripts/screens/quests.gd")._demo_banner())
	var tabs := UI.hbox(6)
	for k in KINDS:
		var b := UI.button(k[1], "secondary", func(): _kind = k[0]; _render())
		b.custom_minimum_size = Vector2(0, 38)
		_tabs[k[0]] = b
		tabs.add_child(b)
	add_child(tabs)
	_body = UI.vbox(0)
	_body.size_flags_vertical = Control.SIZE_EXPAND_FILL
	add_child(_body)
	add_child(UI.label("Только внешний вид — никаких преимуществ в игре и случайных наград.", 12, UI.DIM))
	if arg is Dictionary and arg.has("kind"):
		_kind = arg.kind
	Social.changed.connect(func(w): if w in ["shop", "eco", "conn"] and is_inside_tree(): _render())
	Social.request("shop")
	Social.request("eco")
	_render()


func _render() -> void:
	_sparks.text = "✦ %d искр" % int(Social.eco.get("sparks", 0))
	for k in _tabs:
		var on: bool = k == _kind
		_tabs[k].add_theme_stylebox_override("normal", UI.box(Color(UI.accent(), 0.18) if on else UI.CARD_HI, 10, 12, UI.accent() if on else Color(0, 0, 0, 0), 1 if on else 0))
	for c in _body.get_children():
		c.queue_free()
	if not Social.online():
		_body.add_child(UI.para("Магазин работает через сервер LANTERN — подключитесь к интернету.", 15, UI.YELLOW))
		return
	if _kind == "pro":
		_pro()
		return
	var sc := ScrollContainer.new()
	sc.size_flags_vertical = Control.SIZE_EXPAND_FILL
	sc.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	var g := GridContainer.new()
	g.columns = 4
	g.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	g.add_theme_constant_override("h_separation", 12)
	g.add_theme_constant_override("v_separation", 12)
	sc.add_child(g)
	var equip: Dictionary = Social.shop.get("equip", {})
	for it in Social.shop.get("items", []):
		if it.kind != _kind:
			continue
		g.add_child(_card(it, equip))
	_body.add_child(sc)


func _preview(it: Dictionary) -> Control:
	var box := CenterContainer.new()
	box.custom_minimum_size.y = 100
	match it.kind:
		"flame":
			box.add_child(LanternIcon.new().setup(6, {"flame": it.id}, 88))
		"shape":
			box.add_child(LanternIcon.new().setup(5, {"shape": it.id, "flame": Social.eco.get("equip", {}).get("flame", "")}, 88))
		"nick":
			box.add_child(UI.label(Social.me.get("nick", "Ник"), 24, Color(NICK_COLORS.get(it.id, "eef1f7")), UI.font_black))
		"frame":
			var p := PanelContainer.new()
			var colors := {"frame_wood": "8a5a32", "frame_stone": "8a8f9c", "frame_gold": "ffcf5a", "frame_night": "5a6cff", "frame_s1": "ff8a3d", "frame_none": "2c3345"}
			p.add_theme_stylebox_override("panel", UI.box(UI.CARD, 10, 10, Color(colors.get(it.id, "2c3345")), 4))
			p.add_child(UI.label("Мой мир", 14, UI.TEXT, UI.font_bold))
			p.custom_minimum_size = Vector2(130, 60)
			box.add_child(p)
		"sticker":
			box.add_child(UI.label({"sticker_wave": "Привет! ♥", "sticker_creeper": "БУМ! ✦", "sticker_heart": "♥ ♥ ♥", "sticker_s1": "★ Сезон ★"}.get(it.id, "★"), 22, Color("ffb648"), UI.font_black))
		"campfire":
			box.add_child(UI.label({"camp_logs": "▲ костёр", "camp_tent": "▲ палатка", "camp_stars": "✦ ✦ ✦", "camp_snow": "❄ ❄ ❄", "camp_fireflies": "• ✦ •", "camp_s1": "❦ осень", "camp_s1b": "≈ сияние"}.get(it.id, "▲"), 22, Color("ffb648"), UI.font_black))
	return box


func _card(it: Dictionary, equip: Dictionary) -> Control:
	var worn: bool = equip.get(it.kind, "") == it.id
	var p := UI.panel(14, UI.CARD if not worn else Color(UI.accent(), 0.10), 16)
	p.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	var v := UI.vbox(8)
	p.add_child(v)
	v.add_child(_preview(it))
	var nl := UI.label(it.name, 15, UI.TEXT, UI.font_bold)
	nl.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	v.add_child(nl)
	var b: Button
	if it.get("season", null) != null and not it.owned:
		b = UI.button("Награда сезона", "ghost", func(): app.go("quests", true, {"tab": "season"}))
	elif not it.owned:
		var can: bool = int(Social.eco.get("sparks", 0)) >= int(it.price)
		b = UI.button("✦ %d" % int(it.price), "primary" if can else "secondary", func(): Social.request("buy", {"item": it.id}))
		b.tooltip_text = "" if can else "Не хватает искр — выполняйте задания"
	elif it.kind == "sticker":
		b = UI.button("Есть", "ghost")
		b.disabled = true
	elif worn:
		b = UI.button("Надето", "ghost")
		b.disabled = true
	else:
		b = UI.button("Надеть", "secondary", func(): Social.request("wear", {"item": it.id}))
	v.add_child(b)
	return p


func _pro() -> void:
	var cols := UI.hbox(20)
	cols.size_flags_vertical = Control.SIZE_EXPAND_FILL
	_body.add_child(cols)
	var pro := UI.panel(24, Color("1d1a14"), 20)
	pro.add_theme_stylebox_override("panel", UI.box(Color("1f1a12"), 20, 24, Color("ffb648"), 2))
	pro.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	var v := UI.vbox(10)
	pro.add_child(v)
	var hr := UI.hbox(12)
	hr.add_child(LanternIcon.new().setup(9, {"flame": "flame_s1legend", "shape": "shape_star"}, 76))
	var hv := UI.vbox(0)
	hv.add_child(UI.label("LANTERN Pro", 30, Color("ffb648"), UI.font_black))
	var active: bool = Social.eco.get("pro", false)
	var until := int(Social.eco.get("pro_until", 0))
	hv.add_child(UI.label(("Активна до " + Time.get_date_string_from_unix_time(until / 1000) + (" (ДЕМО)" if Social.demo else "")) if active else "99,99 ₴ в месяц · $2.49", 15, UI.JAVA if active else UI.MUTED, UI.font_bold))
	hr.add_child(hv)
	v.add_child(hr)
	for line in ["Без рекламы совсем", "Свой код комнаты (например YOMPIX)", "Облачные резервные копии миров", "Особая косметика каждый месяц", "Поддержка автора LANTERN — Yompix"]:
		var r := UI.hbox(8)
		r.add_child(UI.label("✓", 16, Color("ffb648"), UI.font_black))
		r.add_child(UI.label(line, 16, UI.TEXT))
		v.add_child(r)
	v.add_child(UI.spacer(false))
	if active:
		v.add_child(UI.pill("✓ Pro активна", UI.JAVA))
	else:
		v.add_child(UI.button("Оформить Pro", "primary", func(): _buy("lantern_pro_month")))
	cols.add_child(pro)
	var right := UI.vbox(12)
	right.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	for pk in [["sparks_500", "500 искр", "79,99 ₴"], ["sparks_1200", "1200 искр", "169,99 ₴  · выгоднее"]]:
		var p := UI.panel(18)
		var r := UI.hbox(12)
		p.add_child(r)
		r.add_child(UI.label("✦", 34, Color("ffb648"), UI.font_black))
		var t := UI.vbox(0)
		t.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		t.add_child(UI.label(pk[1], 20, UI.TEXT, UI.font_black))
		t.add_child(UI.label(pk[2], 14, UI.MUTED))
		r.add_child(t)
		r.add_child(UI.button("Купить", "secondary", func(): _buy(pk[0])))
		right.add_child(p)
	right.add_child(UI.para("Искры можно и не покупать: задания дня, серия и сезон дают их каждый день. Покупки — только через Google Play, родители могут ограничить их в настройках Play.", 13, UI.DIM))
	cols.add_child(right)


func _buy(product: String) -> void:
	if not Social.demo:
		Store.buy(product)
		return
	Overlays.purchase(app, product, func(): Social.request("shop"))
