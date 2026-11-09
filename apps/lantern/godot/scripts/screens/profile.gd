extends BoxContainer
## Профиль: фонарь, ник и тег, титул, уровень, искры, серия, код друга, смена ника.

var app
var arg


func _ready() -> void:
	vertical = UI.compact()
	add_theme_constant_override("separation", 16 if UI.compact() else 24)
	Social.changed.connect(func(w): if w in ["me", "eco", "conn"] and is_inside_tree() and not _typing(): _render())
	Social.request("eco")
	_render()


func _typing() -> bool:
	var f := get_viewport().gui_get_focus_owner()
	return f is LineEdit and is_ancestor_of(f)


func _render() -> void:
	for c in get_children():
		c.queue_free()
	if not Social.online():
		var reg := Social.conn == "need_register"
		var e := UI.empty("user", "Придумайте ник" if reg else "Профиль появится, когда будет связь", "Придумать ник" if reg else "Повторить", func():
			if reg:
				Overlays.register(app)
			else:
				Social.reconnect(), "Пароль не нужен — профиль живёт на этом устройстве." if reg else "Нет связи с сервером LANTERN.")
		e.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		add_child(e)
		return
	var e: Dictionary = Social.eco
	var me: Dictionary = Social.me
	# --- карточка героя
	var hero := PanelContainer.new()
	hero.add_theme_stylebox_override("panel", UI._hpad(UI.box(Color("1d1912"), UI.R_XL, 0, Color(UI.AMBER, 0.22), 1), 20, 24))
	hero.custom_minimum_size.x = 0 if UI.compact() else 360
	hero.size_flags_vertical = Control.SIZE_SHRINK_BEGIN
	var lv := UI.vbox(8)
	hero.add_child(lv)
	var li := LanternIcon.new().setup(int(e.get("lantern", {}).get("level", 1)), e.get("equip", {}), 132)
	li.size_flags_horizontal = Control.SIZE_SHRINK_CENTER
	lv.add_child(li)
	var nick_col := Color(preload("res://scripts/screens/shop.gd").NICK_COLORS.get(e.get("equip", {}).get("nick", ""), "eef1f7"))
	var n := UI.label(me.get("nick", ""), 30, nick_col, UI.font_black)
	n.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	lv.add_child(n)
	var t := UI.label(me.get("tag", ""), 15, UI.MUTED, UI.font_bold)
	t.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	lv.add_child(t)
	var pills := UI.hbox(6)
	pills.alignment = BoxContainer.ALIGNMENT_CENTER
	if String(e.get("title", "")) != "":
		pills.add_child(UI.pill("«%s»" % e.title, UI.AMBER))
	if e.get("pro", false):
		pills.add_child(UI.pill("LANTERN Pro" + (" · ДЕМО" if Social.demo else ""), UI.JAVA))
	if pills.get_child_count() > 0:
		lv.add_child(pills)
	lv.add_child(UI.gap(6))
	var row: BoxContainer = UI.vbox(8) if UI.compact() else UI.hbox(8)
	var cb := UI.button("Скопировать тег", "tonal", func(): UI.copy(me.tag); app.toast("Тег скопирован"), "copy")
	cb.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	row.add_child(cb)
	var sb := UI.button("Сменить вид", "secondary", func(): app.go("shop"), "shop")
	sb.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	row.add_child(sb)
	lv.add_child(row)
	add_child(hero)
	# --- справа: статистика, ссылки, друг, ник
	var right := UI.vbox(12)
	right.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	var stats := GridContainer.new()
	stats.columns = 2 if UI.compact() else 4
	stats.add_theme_constant_override("h_separation", 10)
	stats.add_theme_constant_override("v_separation", 10)
	for s in [["Уровень фонаря", int(e.get("lantern", {}).get("level", 1)), "lantern"], ["Искры", int(e.get("sparks", 0)), "spark"], ["Дней подряд", int(e.get("streak", 0)), "fire"], ["Друзей", Social.friends.size(), "friends"]]:
		var p := PanelContainer.new()
		p.add_theme_stylebox_override("panel", UI._hpad(UI.box(UI.CARD, UI.R_L), 14, 12))
		p.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		var v := UI.vbox(2)
		p.add_child(v)
		var si := UI.icon(s[2], 20, UI.AMBER)
		si.size_flags_horizontal = Control.SIZE_SHRINK_BEGIN
		v.add_child(si)
		var num := UI.label("0", 26, UI.TEXT, UI.font_black)
		FX.count(num, 0, s[1], "%d", 0.6)
		v.add_child(num)
		v.add_child(UI.label(s[0], 12, UI.MUTED))
		stats.add_child(p)
	right.add_child(stats)
	var links := UI.vbox(6)
	for l in [["quests", "Задания и серия", "Искры каждый день", {}], ["star", "Достижения и титулы", "Носите титул под ником", {"tab": "achievements"}], ["gift", "Сезон «Первые огни»", "Награды за игру", {"tab": "season"}]]:
		links.add_child(UI.list_row(UI.icon_tile(l[0], UI.accent(), 40, 20), l[1], l[2], UI.icon("chevron", 20, UI.DIM), func(): app.go("quests", true, l[3] if not l[3].is_empty() else null)))
	right.add_child(links)
	if not e.get("referrer", false):
		var rp := UI.panel(18)
		var rv := UI.vbox(10)
		rp.add_child(rv)
		rv.add_child(UI.h3("Вас позвал друг?"))
		rv.add_child(UI.para("Впишите его тег в первую неделю — когда наиграете вместе час, вы оба получите по 100 искр.", 13, UI.MUTED))
		var rr := UI.hbox(8)
		var re := UI.line_edit("", "Тег друга: Стив#1234")
		re.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		rr.add_child(re)
		rr.add_child(UI.button("Готово", "secondary", func(): Social.request("referral", {"tag": re.text.strip_edges()})))
		rv.add_child(rr)
		right.add_child(rp)
	var np := UI.panel(18)
	var nv := UI.vbox(10)
	np.add_child(nv)
	nv.add_child(UI.h3("Сменить ник"))
	var nr := UI.hbox(8)
	var ne := UI.line_edit(me.get("nick", ""), "Новый ник")
	ne.max_length = 16
	ne.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	nr.add_child(ne)
	nr.add_child(UI.button("Сохранить", "secondary", func(): Social.register(ne.text)))
	nv.add_child(nr)
	nv.add_child(UI.para("Профиль хранится на этом устройстве (без пароля). Не удаляйте папку LANTERN — иначе придётся начать заново.", 12, UI.DIM))
	right.add_child(np)
	add_child(right)
	FX.stagger(right, 0.04)
