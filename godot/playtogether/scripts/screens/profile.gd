extends HBoxContainer
## Профиль: фонарь, ник и тег, титул, уровень, искры, серия, код друга.

var app
var arg


func _ready() -> void:
	add_theme_constant_override("separation", 20)
	Social.changed.connect(func(w): if w in ["me", "eco", "conn"] and is_inside_tree(): _render())
	Social.request("eco")
	_render()


func _render() -> void:
	for c in get_children():
		c.queue_free()
	if not Social.online():
		var p := UI.panel(24)
		var v := UI.vbox(10)
		p.add_child(v)
		v.add_child(UI.h2("Профиль"))
		v.add_child(UI.para("Профиль появится, когда будет связь с сервером LANTERN." if Social.conn != "need_register" else "Придумайте ник — пароль не нужен.", 15, UI.MUTED))
		if Social.conn == "need_register":
			v.add_child(UI.button("Придумать ник", "primary", func(): Overlays.register(app)))
		add_child(p)
		return
	var e: Dictionary = Social.eco
	var me: Dictionary = Social.me
	var left := UI.panel(28)
	left.custom_minimum_size.x = 420
	var lv := UI.vbox(12)
	left.add_child(lv)
	var li := LanternIcon.new().setup(int(e.get("lantern", {}).get("level", 1)), e.get("equip", {}), 180)
	li.size_flags_horizontal = Control.SIZE_SHRINK_CENTER
	lv.add_child(li)
	var nick_col := Color(preload("res://scripts/screens/shop.gd").NICK_COLORS.get(e.get("equip", {}).get("nick", ""), "eef1f7"))
	var n := UI.label(me.get("nick", ""), 34, nick_col, UI.font_black)
	n.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	lv.add_child(n)
	var t := UI.label(me.get("tag", ""), 16, UI.MUTED, UI.font_bold)
	t.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	lv.add_child(t)
	if String(e.get("title", "")) != "":
		var ti := UI.label("«%s»" % e.title, 16, Color("ffb648"), UI.font_bold)
		ti.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
		lv.add_child(ti)
	var row := UI.hbox(10)
	row.alignment = BoxContainer.ALIGNMENT_CENTER
	row.add_child(UI.button("Скопировать тег", "secondary", func(): UI.copy(me.tag); app.toast("Тег скопирован")))
	row.add_child(UI.button("Сменить вид", "ghost", func(): app.go("shop")))
	lv.add_child(row)
	if e.get("pro", false):
		var pp := UI.pill("LANTERN Pro" + (" · ДЕМО" if Social.demo else ""), Color("ffb648"))
		pp.size_flags_horizontal = Control.SIZE_SHRINK_CENTER
		lv.add_child(pp)
	add_child(left)
	var right := UI.vbox(14)
	right.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	var stats := UI.hbox(12)
	for s in [["Уровень фонаря", str(int(e.get("lantern", {}).get("level", 1)))], ["Искры", "✦ %d" % int(e.get("sparks", 0))], ["Дней подряд", str(int(e.get("streak", 0)))], ["Друзей", str(Social.friends.size())]]:
		var p := UI.panel(16)
		p.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		var v := UI.vbox(2)
		p.add_child(v)
		v.add_child(UI.label(s[1], 30, UI.TEXT, UI.font_black))
		v.add_child(UI.label(s[0], 13, UI.MUTED))
		stats.add_child(p)
	right.add_child(stats)
	var links := UI.hbox(12)
	links.add_child(UI.button("Задания и серия", "primary", func(): app.go("quests")))
	links.add_child(UI.button("Достижения и титулы", "secondary", func(): app.go("quests", true, {"tab": "achievements"})))
	links.add_child(UI.button("Сезон", "secondary", func(): app.go("quests", true, {"tab": "season"})))
	right.add_child(links)
	if not e.get("referrer", false):
		var rp := UI.panel(18)
		var rv := UI.vbox(8)
		rp.add_child(rv)
		rv.add_child(UI.h3("Вас позвал друг?"))
		rv.add_child(UI.para("Впишите его тег в первую неделю — когда наиграете вместе час, вы оба получите по 100 искр.", 13, UI.MUTED))
		var rr := UI.hbox(8)
		var re := UI.line_edit("", "Тег друга, например Стив#1234")
		re.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		rr.add_child(re)
		rr.add_child(UI.button("Готово", "secondary", func(): Social.request("referral", {"tag": re.text.strip_edges()})))
		rv.add_child(rr)
		right.add_child(rp)
	var np := UI.panel(18)
	var nv := UI.vbox(8)
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
