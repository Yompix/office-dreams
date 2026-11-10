extends BoxContainer
## Открытые миры (как в Multiplayer Master): поиск, фильтры, карточки, «Зайти». Плюс популярные миры недели.

var app
var arg

var _edition := "any"
var _tag := ""
var _q: LineEdit
var _list: VBoxContainer
var _top: VBoxContainer
var _seg: Control
var _tags_box: ScrollContainer
var _tick := 0.0
var _first := true


func _ready() -> void:
	vertical = UI.compact()
	add_theme_constant_override("separation", 16 if UI.compact() else 24)
	app.set_title("Открытые миры", "Зайдите в любой — мир появится у вас в игре" if UI.compact() else "Зайдите в любой — мир появится у вас в игре. Свой мир можно открыть при создании.")
	var left := UI.vbox(12)
	left.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	left.size_flags_stretch_ratio = 1.8
	add_child(left)
	var sr := UI.hbox(10)
	_q = UI.line_edit("", "Поиск по названию")
	_q.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_q.right_icon = UI.icon_tex("search", 20, UI.DIM)
	_q.text_submitted.connect(func(_t): _load())
	sr.add_child(_q)
	if not UI.compact():
		_seg = UI.segmented([["any", "Все"], ["java", "Java"], ["bedrock", "Bedrock"]], _edition, _pick_ed)
		_seg.custom_minimum_size.x = 300
		sr.add_child(_seg)
	left.add_child(sr)
	if UI.compact():
		_seg = UI.segmented([["any", "Все"], ["java", "Java"], ["bedrock", "Bedrock"]], _edition, _pick_ed)
		left.add_child(_seg)
	_tags_box = ScrollContainer.new()
	_tags_box.vertical_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	_tags_box.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_SHOW_NEVER
	_tags_box.custom_minimum_size.y = 40
	left.add_child(_tags_box)
	if UI.compact():
		_list = UI.vbox(10)
		left.add_child(_list)
	else:
		var sc := ScrollContainer.new()
		sc.size_flags_vertical = Control.SIZE_EXPAND_FILL
		sc.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
		_list = UI.vbox(10)
		_list.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		sc.add_child(_list)
		left.add_child(sc)
	var right := UI.vbox(10)
	right.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	right.add_child(UI.section("Популярные миры недели"))
	var tp := UI.panel(14)
	_top = UI.vbox(10)
	tp.add_child(_top)
	right.add_child(tp)
	right.add_child(UI.para("По числу разных гостей за неделю.", 12, UI.DIM))
	add_child(right)
	Social.changed.connect(_on_changed)
	_refresh_filters()
	for i in 3:
		_list.add_child(FX.skeleton(96, UI.R_L))
	_load()
	Social.request("top")


func _pick_ed(k: String) -> void:
	_edition = k
	UI.segmented_select(_seg, k)
	_first = true
	_load()


func _process(d: float) -> void:
	_tick += d
	if _tick > 10.0:
		_tick = 0
		_load()


func _load() -> void:
	Social.request("rooms", {"edition": _edition, "tag": _tag, "q": _q.text.strip_edges()})
	if not Social.online():
		_render()


func _refresh_filters() -> void:
	for c in _tags_box.get_children():
		c.queue_free()
	var row := UI.hbox(8)
	_tags_box.add_child(row)
	var tags: Array = Social.tags if not Social.tags.is_empty() else ["выживание", "творчество", "pvp", "мини-игры", "новоград"]
	for t in [""] + tags:
		row.add_child(UI.chip("Любые" if t == "" else "#" + t, t == _tag, func(): _tag = t; _first = true; _refresh_filters(); _load()))


func _on_changed(what: String) -> void:
	if what == "rooms":
		_render()
	elif what == "top":
		_render_top()
	elif what == "conn" and Social.online():
		_load()


func _render() -> void:
	for c in _list.get_children():
		c.queue_free()
	if not Social.online():
		_list.add_child(UI.empty("wifi", "Нет связи с сервером LANTERN", "Повторить", func(): Social.reconnect(), "Список появится, когда интернет вернётся."))
		return
	if Social.rooms.is_empty():
		var filt := _tag != "" or _edition != "any" or _q.text != ""
		_list.add_child(UI.empty("globe", "Сейчас нет открытых миров" + (" с такими фильтрами" if filt else ""), "Создать мир" if not Cfg.mobile() else "", func(): app.go("setup"), "Станьте первым: создайте мир и включите «Открытый мир»." if not Cfg.mobile() else "Загляните чуть позже или зайдите к другу по коду."))
		return
	for r in Social.rooms:
		_list.add_child(_card(r))
	if _first:
		_first = false
		FX.stagger(_list, 0.04)


func _card(r: Dictionary) -> Control:
	var ed: String = r.edition
	var col := UI.JAVA if ed == "java" else (UI.BEDROCK if ed == "bedrock" else Color("c58bff"))
	var p := PanelContainer.new()
	var st := UI._hpad(UI.box(UI.CARD, UI.R_L, 0, Color(1, 1, 1, 0.045), 1), 16, 14)
	p.add_theme_stylebox_override("panel", st)
	var v := UI.vbox(10)
	p.add_child(v)
	var row := UI.hbox(12)
	v.add_child(row)
	var host: Dictionary = r.get("host") if r.get("host") is Dictionary else {}
	row.add_child(LanternIcon.new().setup(int(host.get("lantern", {}).get("level", 1)), host.get("equip", ""), 48))
	var tv := UI.vbox(2)
	tv.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	var t := UI.label(r.title, 17, UI.TEXT, UI.font_bold)
	t.clip_text = true
	t.text_overrun_behavior = TextServer.OVERRUN_TRIM_ELLIPSIS
	tv.add_child(t)
	tv.add_child(UI.label("%s · ♥ %d · %d мин" % [host.get("nick", host.get("tag", "гость")), int(r.get("likes", 0)), int(r.get("age", 0))], 12, UI.MUTED))
	row.add_child(tv)
	var full: bool = int(r.players) >= int(r.slots)
	var pl := UI.vbox(0)
	var pn := UI.label("%d/%d" % [int(r.players), int(r.slots)], 17, UI.TEXT if not full else UI.RED, UI.font_black)
	pn.horizontal_alignment = HORIZONTAL_ALIGNMENT_RIGHT
	pl.add_child(pn)
	var pc := UI.label("игроков", 11, UI.DIM)
	pc.horizontal_alignment = HORIZONTAL_ALIGNMENT_RIGHT
	pl.add_child(pc)
	row.add_child(pl)
	var meta := UI.flow(6)
	meta.add_child(UI.pill({"java": "Java", "bedrock": "Bedrock", "both": "Java + Bedrock"}.get(ed, ed), col))
	if r.get("version", "") != "":
		meta.add_child(UI.pill(r.version, UI.MUTED))
	for tg in r.get("tags", []).slice(0, 2):
		meta.add_child(UI.pill("#" + tg, UI.accent()))
	meta.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	meta.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	var br := UI.hbox(8)
	br.add_child(meta)
	var b := UI.button("Полон" if full else "Зайти", "primary", func(): app.go("join", true, {"input": r.code, "edition": "both", "public": true}))
	b.disabled = full
	b.custom_minimum_size.x = 100
	br.add_child(b)
	v.add_child(br)
	UI.hover_scale(p, 1.01)
	return p


func _render_top() -> void:
	for c in _top.get_children():
		c.queue_free()
	if Social.top_hosts.is_empty():
		_top.add_child(UI.para("Пока пусто — эта неделя только началась.", 14, UI.DIM))
	var i := 1
	for h in Social.top_hosts:
		var row := UI.hbox(12)
		var nl := UI.label("%d" % i, 18, UI.AMBER if i <= 3 else UI.DIM, UI.font_black)
		nl.custom_minimum_size.x = 18
		nl.size_flags_vertical = Control.SIZE_SHRINK_CENTER
		row.add_child(nl)
		row.add_child(LanternIcon.new().setup(int(h.get("lantern", {}).get("level", 1)), h.get("equip", ""), 36))
		var v := UI.vbox(0)
		v.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		v.add_child(UI.label(h.get("tag", ""), 15, UI.TEXT, UI.font_bold))
		v.add_child(UI.label("гостей: %d · ♥ %d" % [int(h.visits), int(h.get("likes", 0))], 12, UI.MUTED))
		row.add_child(v)
		_top.add_child(row)
		i += 1
