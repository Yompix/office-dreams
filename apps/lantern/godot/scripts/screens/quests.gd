extends VBoxContainer
## Задания дня и недели, серия, реклама за награду, сезонный пропуск.

var app
var arg := {}

var _tab := "quests"
var _body: Control
var _tabs := {}


func _ready() -> void:
	add_theme_constant_override("separation", 14)
	var head := UI.hbox(12)
	head.add_child(UI.h1("Задания"))
	head.add_child(UI.spacer())
	for it in [["quests", "Задания и серия"], ["season", "Сезон"], ["achievements", "Достижения"]]:
		var b := UI.button(it[1], "secondary", func(): _tab = it[0]; _render())
		b.custom_minimum_size = Vector2(150, 40)
		_tabs[it[0]] = b
		head.add_child(b)
	add_child(head)
	if Social.demo:
		add_child(_demo_banner())
	_body = UI.vbox(0)
	_body.size_flags_vertical = Control.SIZE_EXPAND_FILL
	add_child(_body)
	if arg is Dictionary and arg.has("tab"):
		_tab = arg.tab
	Social.changed.connect(func(w): if w in ["eco", "season", "achievements", "conn"] and is_inside_tree(): _render())
	Social.request("eco")
	Social.request("season")
	Social.request("achievements")
	_render()


static func _demo_banner() -> Control:
	var p := PanelContainer.new()
	p.add_theme_stylebox_override("panel", UI.box(Color(UI.RED, 0.14), 10, 8, Color(UI.RED, 0.5), 1))
	var l := UI.label("ДЕМО: деньги не списываются, реклама тестовая. Выключить — Настройки → «Тест покупок и рекламы».", 13, Color("ffb3ba"), UI.font_bold)
	l.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	p.add_child(l)
	return p


func _render() -> void:
	for k in _tabs:
		var on: bool = k == _tab
		_tabs[k].add_theme_stylebox_override("normal", UI.box(Color(UI.accent(), 0.18) if on else UI.CARD_HI, 12, 12, UI.accent() if on else Color(0, 0, 0, 0), 1 if on else 0))
	for c in _body.get_children():
		c.queue_free()
	if not Social.online():
		_body.add_child(UI.para("Задания считаются на сервере LANTERN — подключитесь к интернету.", 15, UI.YELLOW))
		return
	match _tab:
		"quests": _quests()
		"season": _season()
		"achievements": _achievements()


func _quests() -> void:
	var cols := UI.hbox(20)
	cols.size_flags_vertical = Control.SIZE_EXPAND_FILL
	_body.add_child(cols)
	var left := UI.vbox(12)
	left.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	left.size_flags_stretch_ratio = 1.6
	cols.add_child(left)
	var e: Dictionary = Social.eco
	var q: Dictionary = Social.quests
	# серия
	var sp := UI.panel(18)
	var sr := UI.hbox(16)
	sp.add_child(sr)
	var big := UI.label("%d" % int(e.get("streak", 0)), 44, Color("ffb648"), UI.font_black)
	sr.add_child(big)
	var sv := UI.vbox(2)
	sv.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	sv.add_child(UI.h3("Дней подряд"))
	sv.add_child(UI.para("Заходите каждый день. Пропустили день? Раз в неделю серия не сгорает, а делится пополам. Рекорд: %d." % int(e.get("best_streak", 0)), 13, UI.MUTED))
	sr.add_child(sv)
	var claimed: bool = e.get("streak_claimed", false)
	var sb := UI.button("Получено" if claimed else "Забрать +%d ✦" % int(e.get("streak_reward", 5)), "primary", func(): Social.request("streak_claim"))
	sb.disabled = claimed
	sr.add_child(sb)
	left.add_child(sp)
	left.add_child(UI.label("ЗАДАНИЯ ДНЯ · обновятся через %s" % _hm(int(q.get("resets_in", 0))), 12, UI.MUTED, UI.font_bold))
	for it in q.get("daily", []):
		left.add_child(_quest_row(it))
	left.add_child(UI.label("ЗАДАНИЯ НЕДЕЛИ", 12, UI.MUTED, UI.font_bold))
	for it in q.get("weekly", []):
		left.add_child(_quest_row(it))
	# справа: фонарь и реклама
	var right := UI.vbox(12)
	right.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	cols.add_child(right)
	var lp := UI.panel(18)
	var lv := UI.vbox(8)
	lp.add_child(lv)
	var lan: Dictionary = e.get("lantern", {"level": 1, "minutes": 0, "next": 60})
	var lr := UI.hbox(12)
	lr.add_child(LanternIcon.new().setup(int(lan.level), e.get("equip", {}), 72))
	var lt := UI.vbox(2)
	lt.add_child(UI.h3("Ваш фонарь — уровень %d" % int(lan.level)))
	lt.add_child(UI.para("Разгорается от часов игры вместе с друзьями и наград за задания.", 13, UI.MUTED))
	lt.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	lr.add_child(lt)
	lv.add_child(lr)
	if lan.get("next") != null:
		var bar := ProgressBar.new()
		bar.show_percentage = false
		bar.custom_minimum_size.y = 10
		bar.max_value = float(lan.next)
		bar.value = float(lan.minutes)
		lv.add_child(bar)
		lv.add_child(UI.label("%d / %d очков света до следующего уровня" % [int(lan.minutes), int(lan.next)], 12, UI.DIM))
	right.add_child(lp)
	var ap := UI.panel(18)
	var av := UI.vbox(8)
	ap.add_child(av)
	av.add_child(UI.h3("Искры ×2 за рекламу"))
	var left_ads := int(e.get("ads_left", 3))
	av.add_child(UI.para("Короткий ролик — +20 искр. Осталось сегодня: %d из 3. В LANTERN Pro рекламы нет вообще." % left_ads, 13, UI.MUTED))
	var ab := UI.button("Смотреть рекламу", "secondary", func(): Store.show_rewarded(app))
	ab.disabled = left_ads <= 0
	av.add_child(ab)
	right.add_child(ap)
	var rp := UI.panel(18)
	var rv := UI.vbox(8)
	rp.add_child(rv)
	rv.add_child(UI.h3("Позовите друга"))
	rv.add_child(UI.para("Пусть друг впишет ваш тег %s в профиле. Когда он наиграет час — вы оба получите по 100 искр." % Social.me.get("tag", ""), 13, UI.MUTED))
	right.add_child(rp)


static func _hm(ms: int) -> String:
	var m := ms / 60000
	return "%d ч %02d мин" % [m / 60, m % 60]


func _quest_row(it: Dictionary) -> Control:
	var p := UI.panel(14, UI.CARD, 14)
	var row := UI.hbox(14)
	p.add_child(row)
	var v := UI.vbox(6)
	v.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	v.add_child(UI.label(it.title, 16, UI.TEXT, UI.font_bold))
	var bar := ProgressBar.new()
	bar.show_percentage = false
	bar.custom_minimum_size.y = 8
	bar.max_value = float(it.target)
	bar.value = float(it.progress)
	v.add_child(bar)
	v.add_child(UI.label("%d / %d   ·   +%d ✦  +%d света" % [int(it.progress), int(it.target), int(it.sparks), int(it.xp)], 12, UI.MUTED))
	row.add_child(v)
	var done: bool = int(it.progress) >= int(it.target)
	var b := UI.button("Получено" if it.claimed else ("Забрать" if done else "В процессе"), "primary" if done and not it.claimed else "secondary", func(): Social.request("quest_claim", {"id": it.id, "period": it.period}))
	b.disabled = it.claimed or not done
	b.custom_minimum_size.x = 130
	row.add_child(b)
	return p


func _season() -> void:
	var s: Dictionary = Social.season
	if s.is_empty():
		return
	var head := UI.panel(18)
	var hr := UI.hbox(16)
	head.add_child(hr)
	var hv := UI.vbox(4)
	hv.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	hv.add_child(UI.h2(s.title))
	var days := int((float(s.end) - Time.get_unix_time_from_system() * 1000.0) / 86400000.0)
	hv.add_child(UI.label("Ступень %d из %d · %d очков · до конца сезона %d дн." % [int(s.tier), int(s.tiers), int(s.points), max(days, 0)], 14, UI.MUTED))
	var bar := ProgressBar.new()
	bar.show_percentage = false
	bar.custom_minimum_size.y = 10
	bar.max_value = float(s.tierPoints)
	bar.value = float(int(s.points) % int(s.tierPoints))
	hv.add_child(bar)
	hr.add_child(hv)
	if s.premium:
		hr.add_child(UI.pill("Премиум-путь открыт", Color("ffb648")))
	else:
		var b := UI.button("Открыть премиум-путь · 149,99 ₴", "primary", func():
			if not s.get("premium_available", false):
				app.toast("Скоро! Покупки появятся после запуска в Google Play.")
				return
			Overlays.purchase(app, "lantern_pass_s1", func(): Social.request("season")))
		hr.add_child(b)
	_body.add_child(head)
	_body.add_child(UI.gap(10))
	var sc := ScrollContainer.new()
	sc.vertical_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	sc.size_flags_vertical = Control.SIZE_EXPAND_FILL
	var row := UI.hbox(10)
	sc.add_child(row)
	var labels := UI.vbox(10)
	labels.add_child(UI.gap(30))
	var fl := UI.label("Бесплатно", 14, UI.MUTED, UI.font_bold)
	fl.custom_minimum_size.y = 130
	fl.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
	labels.add_child(fl)
	var pl := UI.label("Премиум", 14, Color("ffb648"), UI.font_bold)
	pl.custom_minimum_size.y = 130
	pl.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
	labels.add_child(pl)
	row.add_child(labels)
	var claimed: Array = s.get("claimed", [])
	for tier in range(1, int(s.tiers) + 1):
		var col := UI.vbox(10)
		var tl := UI.label(str(tier), 15, Color("ffb648") if tier <= int(s.tier) else UI.DIM, UI.font_black)
		tl.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
		tl.custom_minimum_size.y = 20
		col.add_child(tl)
		col.add_child(_reward_cell(s, tier, "free", s.free.get(str(tier)), claimed))
		col.add_child(_reward_cell(s, tier, "premium", s.premiumTrack.get(str(tier)), claimed))
		row.add_child(col)
	_body.add_child(sc)


func _reward_cell(s: Dictionary, tier: int, track: String, reward: Variant, claimed: Array) -> Control:
	var p := PanelContainer.new()
	p.custom_minimum_size = Vector2(118, 130)
	var open := tier <= int(s.tier)
	var prem := track == "premium"
	var col := Color("ffb648") if prem else UI.accent()
	p.add_theme_stylebox_override("panel", UI.box(Color(col, 0.10) if reward != null else Color(1, 1, 1, 0.02), 12, 8, Color(col, 0.5) if open and reward != null else UI.LINE, 1))
	var v := UI.vbox(4)
	v.alignment = BoxContainer.ALIGNMENT_CENTER
	p.add_child(v)
	if reward == null:
		return p
	var text := ""
	var kind := ""
	if reward is Array:
		kind = reward[0]
		text = "+%d ✦" % int(reward[1]) if reward[0] == "sparks" else str(s.items.get(reward[1], reward[1]))
		if reward[0] == "item":
			kind = "item"
			var item: String = reward[1]
			if item.begins_with("flame"):
				var li := LanternIcon.new().setup(5, {"flame": item}, 46)
				li.size_flags_horizontal = Control.SIZE_SHRINK_CENTER
				v.add_child(li)
	else:
		text = str(s.items.get(reward, reward))
		var li := LanternIcon.new().setup(6, {"flame": reward if String(reward).begins_with("flame") else "flame_s1p", "shape": reward if String(reward).begins_with("shape") else "shape_cube"}, 46)
		li.size_flags_horizontal = Control.SIZE_SHRINK_CENTER
		v.add_child(li)
	var l := UI.para(text, 12, UI.TEXT)
	l.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	l.custom_minimum_size.x = 100
	v.add_child(l)
	var key := ("p" if prem else "f") + str(tier)
	if key in claimed:
		v.add_child(UI.label("✓ получено", 11, UI.JAVA, UI.font_bold))
	elif open and (not prem or s.premium):
		var b := UI.button("Забрать", "primary", func(): Social.request("season_claim", {"tier": tier, "track": track}))
		b.custom_minimum_size = Vector2(0, 30)
		b.add_theme_font_size_override("font_size", 13)
		v.add_child(b)
	elif prem and not s.premium:
		v.add_child(UI.label("премиум", 11, UI.DIM))
	return p


func _achievements() -> void:
	var a: Dictionary = Social.achievements
	var sc := ScrollContainer.new()
	sc.size_flags_vertical = Control.SIZE_EXPAND_FILL
	sc.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	var g := GridContainer.new()
	g.columns = 3
	g.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	g.add_theme_constant_override("h_separation", 12)
	g.add_theme_constant_override("v_separation", 12)
	sc.add_child(g)
	var cur: String = a.get("title", "")
	for it in a.get("list", []):
		var p := UI.panel(14, UI.CARD if not it.done else Color(Color("ffb648"), 0.08), 14)
		p.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		var v := UI.vbox(4)
		p.add_child(v)
		var r := UI.hbox(8)
		r.add_child(UI.label("★" if it.done else "☆", 20, Color("ffb648") if it.done else UI.DIM))
		var nm := UI.label(it.name, 15, UI.TEXT if it.done else UI.MUTED, UI.font_bold)
		nm.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		r.add_child(nm)
		v.add_child(r)
		v.add_child(UI.label("Титул «%s» · +%d ✦" % [it.title, int(it.sparks)], 12, UI.MUTED))
		var bar := ProgressBar.new()
		bar.show_percentage = false
		bar.custom_minimum_size.y = 6
		bar.max_value = float(it.need)
		bar.value = float(it.progress)
		v.add_child(bar)
		if it.done:
			var tb := UI.button("Носится" if cur == it.title else "Носить титул", "ghost", func(): Social.request("set_title", {"title": it.title}); Social.request("achievements"))
			tb.custom_minimum_size.y = 30
			tb.disabled = cur == it.title
			v.add_child(tb)
		g.add_child(p)
	_body.add_child(sc)
