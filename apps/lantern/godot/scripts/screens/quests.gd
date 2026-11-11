extends VBoxContainer
## Задания дня и недели, серия, реклама за награду, сезонный пропуск, достижения.

var app
var arg := {}

var _tab := "quests"
var _body: VBoxContainer
var _seg: Control
var _last_tab := ""


func _ready() -> void:
	add_theme_constant_override("separation", 14)
	if arg is Dictionary and arg.has("tab"):
		_tab = arg.tab
	_seg = UI.segmented([["quests", "Задания"], ["season", "Сезон"], ["achievements", "Достижения"]], _tab, func(k):
		_tab = k
		UI.segmented_select(_seg, k)
		_render())
	if not UI.compact():
		_seg.custom_minimum_size.x = 480
		_seg.size_flags_horizontal = Control.SIZE_SHRINK_BEGIN
	add_child(_seg)
	if Social.demo:
		add_child(_demo_banner())
	_body = UI.vbox(12)
	_body.size_flags_vertical = Control.SIZE_EXPAND_FILL
	add_child(_body)
	Social.changed.connect(func(w): if w in ["eco", "season", "achievements", "conn"] and is_inside_tree(): _render())
	Social.request("eco")
	Social.request("season")
	Social.request("achievements")
	_render()


static func _demo_banner() -> Control:
	var p := PanelContainer.new()
	p.add_theme_stylebox_override("panel", UI._hpad(UI.box(Color(UI.YELLOW, 0.10), UI.R_M), 14, 10))
	var r := UI.hbox(10)
	p.add_child(r)
	var ic := UI.icon("info", 18, UI.YELLOW)
	ic.size_flags_vertical = Control.SIZE_SHRINK_BEGIN
	r.add_child(ic)
	var l := UI.para("ДЕМО: деньги не списываются, реклама тестовая. Выключить — «Настройки».", 13, Color("ffdf9a"))
	l.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	r.add_child(l)
	return p


func _scroll_wrap(c: Control) -> Control:
	if UI.compact():
		return c
	var sc := ScrollContainer.new()
	sc.size_flags_vertical = Control.SIZE_EXPAND_FILL
	sc.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	c.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	sc.add_child(c)
	return sc


func _render() -> void:
	var changed := _tab != _last_tab
	_last_tab = _tab
	for c in _body.get_children():
		c.queue_free()
	if not Social.online():
		_body.add_child(UI.empty("quests", "Задания считаются на сервере LANTERN", "Повторить", func(): Social.reconnect(), "Подключитесь к интернету."))
		return
	match _tab:
		"quests": _quests()
		"season": _season()
		"achievements": _achievements()
	if changed and _body.get_child_count() > 0:
		FX.enter(_body.get_child(0), Vector2(0, 12))


func _quests() -> void:
	var cols := UI.cols(20)
	cols.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_body.add_child(_scroll_wrap(cols))
	var left := UI.vbox(10)
	left.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	left.size_flags_stretch_ratio = 1.5
	cols.add_child(left)
	var e: Dictionary = Social.eco
	var q: Dictionary = Social.quests
	# серия
	var sp := PanelContainer.new()
	var sst := UI.box(Color("221b10"), UI.R_L, 0, Color(UI.AMBER, 0.3), 1)
	UI._hpad(sst, 16, 14)
	sp.add_theme_stylebox_override("panel", sst)
	var sr := UI.hbox(14)
	sp.add_child(sr)
	var fire := UI.icon_tile("fire", UI.AMBER, 52, 28)
	fire.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	sr.add_child(fire)
	var sv := UI.vbox(0)
	sv.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	var big := UI.label("0", 28, UI.AMBER, UI.font_black)
	FX.count(big, 0, int(e.get("streak", 0)), "%d", 0.5)
	var bl := UI.hbox(8)
	bl.add_child(big)
	var dl := UI.label(_days(int(e.get("streak", 0))) + " подряд", 14, UI.TEXT, UI.font_bold)
	dl.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	bl.add_child(dl)
	sv.add_child(bl)
	sv.add_child(UI.para("Рекорд: %d · пропуск раз в неделю не сжигает серию" % int(e.get("best_streak", 0)), 12, UI.MUTED))
	sr.add_child(sv)
	var claimed: bool = e.get("streak_claimed", false)
	var sb := UI.button("Получено" if claimed else "+%d" % int(e.get("streak_reward", 5)), "primary", func(): Social.request("streak_claim"), "check" if claimed else "spark")
	sb.disabled = claimed
	sb.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	sr.add_child(sb)
	left.add_child(sp)
	left.add_child(UI.section("Задания дня · ещё %s" % _hm(int(q.get("resets_in", 0)))))
	for it in q.get("daily", []):
		left.add_child(_quest_row(it))
	left.add_child(UI.section("Задания недели"))
	for it in q.get("weekly", []):
		left.add_child(_quest_row(it))
	# фонарь, реклама, друг
	var right := UI.vbox(10)
	right.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	cols.add_child(right)
	if UI.compact():
		right.add_child(UI.section("Ещё искры"))
	var lp := UI.panel(18)
	var lv := UI.vbox(10)
	lp.add_child(lv)
	var lan: Dictionary = e.get("lantern", {"level": 1, "minutes": 0, "next": 60})
	var lr := UI.hbox(14)
	lr.add_child(LanternIcon.new().setup(int(lan.level), e.get("equip", {}), 64))
	var lt := UI.vbox(2)
	lt.add_child(UI.h3("Фонарь — уровень %d" % int(lan.level)))
	lt.add_child(UI.para("Разгорается от игры с друзьями и наград за задания.", 13, UI.MUTED))
	lt.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	lr.add_child(lt)
	lv.add_child(lr)
	if lan.get("next") != null:
		var bar := ProgressBar.new()
		bar.show_percentage = false
		bar.custom_minimum_size.y = 8
		bar.max_value = float(lan.next)
		_grow(bar, float(lan.minutes))
		lv.add_child(bar)
		lv.add_child(UI.label("%d / %d очков света до следующего уровня" % [int(lan.minutes), int(lan.next)], 12, UI.DIM))
	right.add_child(lp)
	if Cfg.paid():
		var left_ads := int(e.get("ads_left", 3))
		var ab := UI.button("Смотреть рекламу", "tonal", func(): Store.show_rewarded(app), "play")
		ab.disabled = left_ads <= 0
		right.add_child(UI.list_row(UI.icon_tile("spark", UI.AMBER, 44, 22), "+20 искр за ролик", "Осталось сегодня: %d из 3 · в Pro рекламы нет" % left_ads, null))
		right.add_child(ab)
	right.add_child(UI.list_row(UI.icon_tile("friends", UI.BEDROCK, 44, 22), "Позовите друга — +100 искр", "Пусть впишет ваш тег %s в профиле и наиграет час" % Social.me.get("tag", ""), null))
	FX.stagger(left, 0.035)


func _grow(bar: ProgressBar, to: float) -> void:
	if not FX.on():
		bar.value = to
		return
	bar.value = 0
	bar.create_tween().set_trans(Tween.TRANS_CUBIC).set_ease(Tween.EASE_OUT).tween_property(bar, "value", to, 0.6).set_delay(0.15)


## 1 день, 2 дня, 5 дней
static func _days(n: int) -> String:
	var m10 := n % 10
	var m100 := n % 100
	if m10 == 1 and m100 != 11:
		return "день"
	if m10 >= 2 and m10 <= 4 and (m100 < 12 or m100 > 14):
		return "дня"
	return "дней"


static func _hm(ms: int) -> String:
	var m := ms / 60000
	return "%d ч %02d мин" % [m / 60, m % 60]


func _quest_row(it: Dictionary) -> Control:
	var p := PanelContainer.new()
	p.add_theme_stylebox_override("panel", UI._hpad(UI.box(UI.CARD, UI.R_L), 16, 14))
	var row := UI.hbox(14)
	p.add_child(row)
	var v := UI.vbox(8)
	v.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	var tl := UI.para(it.title, 15, UI.TEXT)
	tl.add_theme_font_override("font", UI.font_bold)
	v.add_child(tl)
	var bar := ProgressBar.new()
	bar.show_percentage = false
	bar.custom_minimum_size.y = 6
	bar.max_value = float(it.target)
	_grow(bar, float(it.progress))
	v.add_child(bar)
	v.add_child(UI.label("%d / %d  ·  +%d искр  ·  +%d света" % [int(it.progress), int(it.target), int(it.sparks), int(it.xp)], 12, UI.MUTED))
	row.add_child(v)
	var done: bool = int(it.progress) >= int(it.target)
	var b: Button
	if it.claimed:
		b = UI.button("", "ghost", Callable(), "check")
		b.tooltip_text = "Получено"
		b.disabled = true
	else:
		b = UI.button("Забрать" if done else "В пути", "primary" if done else "secondary", func(): Social.request("quest_claim", {"id": it.id, "period": it.period}))
		b.disabled = not done
	b.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	b.custom_minimum_size.x = 48 if it.claimed else 104
	row.add_child(b)
	if it.claimed:
		p.modulate.a = 0.6
	return p


func _season() -> void:
	var s: Dictionary = Social.season
	if s.is_empty():
		_body.add_child(FX.skeleton(120))
		_body.add_child(FX.skeleton(300))
		return
	var head := PanelContainer.new()
	head.add_theme_stylebox_override("panel", UI._hpad(UI.box(Color("221b10"), UI.R_XL, 0, Color(UI.AMBER, 0.35), 1), 18, 18))
	var hv := UI.vbox(8)
	head.add_child(hv)
	hv.add_child(UI.caption("Сезон", UI.AMBER))
	hv.add_child(UI.h2(s.title))
	var days := int((float(s.end) - Time.get_unix_time_from_system() * 1000.0) / 86400000.0)
	hv.add_child(UI.label("Ступень %d из %d · %d очков · ещё %d дн." % [int(s.tier), int(s.tiers), int(s.points), max(days, 0)], 13, UI.MUTED))
	var bar := ProgressBar.new()
	bar.show_percentage = false
	bar.custom_minimum_size.y = 8
	bar.max_value = float(s.tierPoints)
	_grow(bar, float(int(s.points) % int(s.tierPoints)))
	hv.add_child(bar)
	if s.premium:
		hv.add_child(UI.pill("✓ Премиум-путь открыт", UI.AMBER))
	elif Cfg.paid():
		var b := UI.button("Премиум-путь · 149,99 ₴", "primary", func():
			if not s.get("premium_available", false):
				app.toast("Скоро! Покупки появятся после запуска в Google Play.")
				return
			Overlays.purchase(app, "lantern_pass_s1", func(): Social.request("season")), "star")
		hv.add_child(b)
	_body.add_child(head)
	_body.add_child(UI.section("Награды — листайте вбок"))
	var sc := ScrollContainer.new()
	sc.vertical_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	sc.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_SHOW_NEVER if UI.compact() else ScrollContainer.SCROLL_MODE_AUTO
	var row := UI.hbox(10)
	sc.add_child(row)
	var labels := UI.vbox(10)
	labels.add_child(UI.gap(22))
	var prem_on := Cfg.paid() or bool(s.premium)
	for tr in ([["Бесплатно", UI.MUTED], ["Премиум", UI.AMBER]] if prem_on else []):
		var fl := UI.label(tr[0], 12, tr[1], UI.font_bold)
		fl.custom_minimum_size = Vector2(0, 132)
		fl.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
		labels.add_child(fl)
	if not UI.compact() and prem_on:
		row.add_child(labels)
	var claimed: Array = s.get("claimed", [])
	for tier in range(1, int(s.tiers) + 1):
		var col := UI.vbox(10)
		var tl := UI.label(str(tier), 14, UI.AMBER if tier <= int(s.tier) else UI.DIM, UI.font_black)
		tl.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
		tl.custom_minimum_size.y = 22
		col.add_child(tl)
		col.add_child(_reward_cell(s, tier, "free", s.free.get(str(tier)), claimed))
		if prem_on:
			col.add_child(_reward_cell(s, tier, "premium", s.premiumTrack.get(str(tier)), claimed))
		row.add_child(col)
	_body.add_child(sc)
	if UI.compact() and prem_on:
		_body.add_child(UI.para("Верхний ряд — бесплатные награды, нижний — премиум.", 12, UI.DIM))
	FX.stagger(row, 0.03, Vector2(16, 0))


func _reward_cell(s: Dictionary, tier: int, track: String, reward: Variant, claimed: Array) -> Control:
	var p := PanelContainer.new()
	p.custom_minimum_size = Vector2(112, 132)
	var open := tier <= int(s.tier)
	var prem := track == "premium"
	var col := UI.AMBER if prem else UI.accent()
	p.add_theme_stylebox_override("panel", UI.box(Color(col, 0.10) if reward != null else Color(1, 1, 1, 0.015), UI.R_M, 8, Color(col, 0.55) if open and reward != null else Color(1, 1, 1, 0.05 if reward != null else 0.0), 1))
	var v := UI.vbox(4)
	v.alignment = BoxContainer.ALIGNMENT_CENTER
	p.add_child(v)
	if reward == null:
		var dash := UI.label("—", 14, Color(1, 1, 1, 0.12))
		dash.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
		v.add_child(dash)
		return p
	var text := ""
	if reward is Array:
		text = "+%d искр" % int(reward[1]) if reward[0] == "sparks" else str(s.items.get(reward[1], reward[1]))
		if reward[0] == "sparks":
			var ic := UI.icon("spark", 34, UI.AMBER)
			ic.size_flags_horizontal = Control.SIZE_SHRINK_CENTER
			v.add_child(ic)
		elif reward[0] == "item" and String(reward[1]).begins_with("flame"):
			var li := LanternIcon.new().setup(5, {"flame": reward[1]}, 44)
			li.size_flags_horizontal = Control.SIZE_SHRINK_CENTER
			v.add_child(li)
	else:
		text = str(s.items.get(reward, reward))
		var li := LanternIcon.new().setup(6, {"flame": reward if String(reward).begins_with("flame") else "flame_s1p", "shape": reward if String(reward).begins_with("shape") else "shape_cube"}, 44)
		li.size_flags_horizontal = Control.SIZE_SHRINK_CENTER
		v.add_child(li)
	var l := UI.para(text, 12, UI.TEXT)
	l.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	l.custom_minimum_size.x = 92
	l.max_lines_visible = 2
	v.add_child(l)
	var key := ("p" if prem else "f") + str(tier)
	if key in claimed:
		v.add_child(UI.pill("✓ есть", UI.JAVA))
		(v.get_child(v.get_child_count() - 1) as Control).size_flags_horizontal = Control.SIZE_SHRINK_CENTER
	elif open and (not prem or s.premium):
		var b := UI.button("Забрать", "primary", func(): Social.request("season_claim", {"tier": tier, "track": track}))
		b.custom_minimum_size = Vector2(0, 32)
		b.add_theme_font_size_override("font_size", 13)
		v.add_child(b)
	elif prem and not s.premium:
		var lk := UI.icon("lock", 16, UI.DIM)
		lk.size_flags_horizontal = Control.SIZE_SHRINK_CENTER
		v.add_child(lk)
	return p


func _achievements() -> void:
	var a: Dictionary = Social.achievements
	var g := GridContainer.new()
	g.columns = 1 if UI.compact() else 3
	g.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	g.add_theme_constant_override("h_separation", 12)
	g.add_theme_constant_override("v_separation", 10)
	_body.add_child(_scroll_wrap(g))
	var cur: String = a.get("title", "")
	for it in a.get("list", []):
		var p := PanelContainer.new()
		p.add_theme_stylebox_override("panel", UI._hpad(UI.box(Color(UI.AMBER, 0.08) if it.done else UI.CARD, UI.R_L, 0, Color(UI.AMBER, 0.3) if it.done else Color(1, 1, 1, 0.04), 1), 16, 14))
		p.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		var row := UI.hbox(14)
		p.add_child(row)
		var tile := UI.icon_tile("star", UI.AMBER if it.done else UI.DIM, 44, 22)
		tile.size_flags_vertical = Control.SIZE_SHRINK_BEGIN
		row.add_child(tile)
		var v := UI.vbox(6)
		v.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		row.add_child(v)
		v.add_child(UI.label(it.name, 15, UI.TEXT if it.done else UI.MUTED, UI.font_bold))
		v.add_child(UI.label("Титул «%s» · +%d искр" % [it.title, int(it.sparks)], 12, UI.MUTED))
		var bar := ProgressBar.new()
		bar.show_percentage = false
		bar.custom_minimum_size.y = 6
		bar.max_value = float(it.need)
		_grow(bar, float(it.progress))
		v.add_child(bar)
		if it.done:
			var tb := UI.button("Носится" if cur == it.title else "Носить титул", "tonal" if cur != it.title else "ghost", func(): Social.request("set_title", {"title": it.title}); Social.request("achievements"))
			tb.custom_minimum_size.y = 36
			tb.size_flags_horizontal = Control.SIZE_SHRINK_BEGIN
			tb.disabled = cur == it.title
			v.add_child(tb)
		g.add_child(p)
	FX.stagger(g, 0.03)
