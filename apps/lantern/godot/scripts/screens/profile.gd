extends BoxContainer
## Профиль: фонарь, ник и тег, титул, уровень, искры, серия, код друга, смена ника.

var app
var arg
var _nick_wait := ""


func _ready() -> void:
	vertical = UI.compact()
	add_theme_constant_override("separation", 16 if UI.compact() else 24)
	Social.changed.connect(func(w):
		if w == "me" and _nick_wait != "" and is_inside_tree() and Social.me is Dictionary and String(Social.me.get("nick", "")) == _nick_wait:
			_nick_wait = ""
			app.toast(L.t("Ник изменён"), UI.JAVA)
		if w in ["me", "eco", "conn"] and is_inside_tree() and not _typing(): _render())
	Social.failed.connect(func(what, text):
		if what == "register" and _nick_wait != "" and is_inside_tree():
			_nick_wait = ""
			app.toast(L.t(String(text)) if not RegEx.create_from_string("^[a-z_]+$").search(String(text)) else "Такой ник не подходит или занят", UI.YELLOW))
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
		var e := UI.empty("user", L.t("Придумайте ник") if reg else L.t("Профиль появится, когда будет связь"), L.t("Придумать ник") if reg else L.t("Повторить"), func():
			if reg:
				Overlays.register(app)
			else:
				Social.reconnect(), L.t("Пароль не нужен — профиль живёт на этом устройстве.") if reg else L.t("Нет связи с сервером LANTERN."))
		e.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		add_child(e)
		return
	var e: Dictionary = Social.eco
	var me: Dictionary = Social.me
	# --- карточка героя
	var hero := PanelContainer.new()
	hero.add_theme_stylebox_override("panel", UI._hpad(UI.glass(2, UI.R_XL, 0, UI.AMBER), 20, 24))
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
		pills.add_child(UI.pill("«%s»" % L.t(String(e.title)), UI.AMBER))
	if e.get("pro", false) and Cfg.paid():
		pills.add_child(UI.pill("LANTERN Pro" + (L.t(" · ДЕМО") if Social.demo else ""), UI.JAVA))
	if pills.get_child_count() > 0:
		lv.add_child(pills)
	lv.add_child(UI.gap(6))
	var row: BoxContainer = UI.vbox(8) if UI.compact() else UI.hbox(8)
	var cb := UI.button(L.t("Скопировать тег"), "tonal", func(): UI.copy(me.tag); app.toast(L.t("Тег скопирован")), "copy")
	cb.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	row.add_child(cb)
	var sb := UI.button(L.t("Сменить вид"), "secondary", func(): app.go("shop"), "shop")
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
	for s in [[L.t("Уровень фонаря"), int(e.get("lantern", {}).get("level", 1)), "lantern"], [L.t("Искры"), int(e.get("sparks", 0)), "spark"], [L.t("Дней подряд"), int(e.get("streak", 0)), "fire"], [L.t("Друзей"), Social.friends.size(), "friends"]]:
		var p := PanelContainer.new()
		p.add_theme_stylebox_override("panel", UI._hpad(UI.glass(1, UI.R_XL, 0), 14, 12))
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
	for l in [["quests", L.t("Задания и серия"), L.t("Искры каждый день"), {}], ["star", L.t("Достижения и титулы"), L.t("Носите титул под ником"), {"tab": "achievements"}], ["gift", L.t("Сезон «Первые огни»"), L.t("Награды за игру"), {"tab": "season"}]]:
		links.add_child(UI.list_row(UI.icon_tile(l[0], UI.accent(), 40, 20), l[1], l[2], UI.icon("chevron", 20, UI.DIM), func(): app.go("quests", true, l[3] if not l[3].is_empty() else null)))
	right.add_child(links)
	if not e.get("referrer", false):
		var rp := UI.panel(18)
		var rv := UI.vbox(10)
		rp.add_child(rv)
		rv.add_child(UI.h3(L.t("Вас позвал друг?")))
		rv.add_child(UI.para(L.t("Впишите его тег в первую неделю — когда наиграете вместе час, вы оба получите по 100 искр."), 13, UI.MUTED))
		var rr := UI.hbox(8)
		var re := UI.line_edit("", L.t("Тег друга: Стив#1234"))
		re.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		rr.add_child(re)
		rr.add_child(UI.button(L.t("Готово"), "secondary", func(): Social.request("referral", {"tag": re.text.strip_edges()})))
		rv.add_child(rr)
		right.add_child(rp)
	var np := UI.panel(18)
	var nv := UI.vbox(10)
	np.add_child(nv)
	nv.add_child(UI.h3(L.t("Сменить ник")))
	var nr := UI.hbox(8)
	var ne := UI.line_edit(me.get("nick", ""), L.t("Новый ник"))
	ne.max_length = 16
	ne.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	nr.add_child(ne)
	nr.add_child(UI.button(L.t("Сохранить"), "secondary", func():
		if not Social.online():
			app.toast(L.t("Нет связи с сервером — ник не сохранён"), UI.YELLOW)
			return
		ne.release_focus()      # иначе экран не перерисуется, пока курсор в поле
		_nick_wait = ne.text.strip_edges()
		Social.register(ne.text)))
	nv.add_child(nr)
	nv.add_child(UI.para(L.t("Профиль хранится на этом устройстве (без пароля). Не удаляйте папку LANTERN — иначе придётся начать заново."), 12, UI.DIM))
	right.add_child(np)
	add_child(right)
	FX.stagger(right, 0.04)
