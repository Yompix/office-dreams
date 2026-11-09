extends VBoxContainer
## Главная: выбор издания и быстрый вход по коду.

var app
var arg


func _ready() -> void:
	add_theme_constant_override("separation", 18)
	var head := UI.hbox(18)
	head.add_child(UI.texture("res://assets/img/icon_512.png", 92, 92))
	var hv := UI.vbox(0)
	hv.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	var logo := UI.hbox(14)
	var lt := UI.label("LANTERN", 46, UI.TEXT, UI.font_black)
	logo.add_child(lt)
	var by := UI.label("by Yompix", 16, Color("ffb648"), UI.font_bold)
	by.size_flags_vertical = Control.SIZE_SHRINK_END
	logo.add_child(by)
	hv.add_child(logo)
	hv.add_child(UI.label("Играй с друзьями — Java и Bedrock", 19, UI.MUTED, UI.font_bold))
	head.add_child(hv)
	add_child(head)
	var cards := UI.hbox(20)
	cards.size_flags_vertical = Control.SIZE_EXPAND_FILL
	add_child(cards)
	cards.add_child(_card("java", "Java Edition", "Для компьютера: лицензия или TLauncher", "Сервер Paper — быстрый и надёжный. Можно пустить друзей с телефона (кроссплей).", "res://assets/img/card_java.png", UI.JAVA))
	cards.add_child(_card("bedrock", "Bedrock Edition", "Телефон, планшет, Windows, консоли", "Официальный сервер Bedrock от Mojang. Мир сам появится у друга во вкладке «Друзья».", "res://assets/img/card_bedrock.png", UI.BEDROCK))
	# --- быстрый вход
	var p := UI.panel(18)
	var row := UI.hbox(14)
	p.add_child(row)
	var t := UI.vbox(2)
	t.add_child(UI.h3("Есть код от друга?"))
	t.add_child(UI.label("6 символов, например K7M-2QX", 14, UI.MUTED))
	row.add_child(t)
	row.add_child(UI.spacer())
	var e := UI.line_edit("", "Код комнаты")
	e.custom_minimum_size.x = 260
	e.add_theme_font_override("font", UI.font_bold)
	e.add_theme_font_size_override("font_size", 20)
	e.max_length = 64
	row.add_child(e)
	var go_btn := UI.button("Зайти", "primary", func(): app.go("join", false, {"input": e.text}))
	go_btn.custom_minimum_size.x = 140
	e.text_submitted.connect(func(_t): go_btn.pressed.emit())
	row.add_child(go_btn)
	add_child(p)
	if app.server.state in ["installing", "starting", "running", "stopping"]:
		var b := UI.button("Мой мир запущен — открыть панель сервера", "secondary", func(): app.go("host"))
		add_child(b)


func _card(ed: String, title: String, tag: String, desc: String, img: String, color: Color) -> Control:
	var card := PanelContainer.new()
	card.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	card.size_flags_vertical = Control.SIZE_EXPAND_FILL
	var st := UI.box(UI.CARD, 22, 0, UI.LINE, 1)
	card.add_theme_stylebox_override("panel", st)
	card.mouse_filter = Control.MOUSE_FILTER_PASS
	var v := UI.vbox(0)
	card.add_child(v)
	# картинка на цветной подложке
	var art := PanelContainer.new()
	var ast := UI.box(Color(color, 0.12), 22, 0)
	ast.corner_radius_bottom_left = 0
	ast.corner_radius_bottom_right = 0
	art.add_theme_stylebox_override("panel", ast)
	art.custom_minimum_size.y = 230
	art.size_flags_vertical = Control.SIZE_EXPAND_FILL
	var pic := UI.texture(img, 0, 220)
	art.add_child(pic)
	v.add_child(art)
	var m := MarginContainer.new()
	for s in ["left", "right", "top", "bottom"]:
		m.add_theme_constant_override("margin_" + s, 24)
	v.add_child(m)
	var body := UI.vbox(10)
	m.add_child(body)
	body.add_child(UI.pill(tag, color))
	body.add_child(UI.label(title, 30, UI.TEXT, UI.font_black))
	body.add_child(UI.para(desc, 15, UI.MUTED))
	body.add_child(UI.gap(4))
	var row := UI.hbox(12)
	body.add_child(row)
	var mobile := OS.has_feature("mobile")
	var b1 := UI.button("Создать мир" if not mobile else "Создать — на ПК", "primary", func(): app.go("setup", false, {"edition": ed}))
	b1.disabled = mobile
	b1.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	b1.add_theme_stylebox_override("normal", UI.box(color, 12, 14))
	b1.add_theme_stylebox_override("hover", UI.box(color.lightened(0.12), 12, 14))
	b1.add_theme_stylebox_override("pressed", UI.box(color.darkened(0.15), 12, 14))
	row.add_child(b1)
	var b2 := UI.button("Зайти к другу", "secondary", func(): app.go("join", false, {"edition": ed}))
	b2.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	row.add_child(b2)
	card.mouse_entered.connect(func(): st.border_color = Color(color, 0.7); card.queue_redraw())
	card.mouse_exited.connect(func(): st.border_color = UI.LINE; card.queue_redraw())
	UI.hover_scale(card, 1.012)
	return card
