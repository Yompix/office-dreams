extends BoxContainer
## Открытые миры (как в Multiplayer Master): фильтры, карточки, «Зайти». Справа — популярные миры недели.

var app
var arg

var _edition := "any"
var _tag := ""
var _q: LineEdit
var _list: VBoxContainer
var _top: VBoxContainer
var _seg := {}
var _tags_box: HFlowContainer
var _tick := 0.0


func _ready() -> void:
	vertical = UI.compact()   # телефон: колонки друг под другом
	add_theme_constant_override("separation", 20)
	var left := UI.vbox(14)
	left.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	left.size_flags_stretch_ratio = 1.8
	add_child(left)
	var head := UI.hbox(12)
	var hv := UI.vbox(2)
	hv.add_child(UI.h1("Открытые миры"))
	hv.add_child(UI.para("Зайдите в любой — мир появится у вас в игре. Свой мир можно сделать открытым при создании.", 15, UI.MUTED))
	hv.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	head.add_child(hv)
	left.add_child(head)
	var f := UI.panel(14)
	var fv := UI.vbox(10)
	f.add_child(fv)
	var r1 := UI.hbox(8)
	for it in [["any", "Все"], ["java", "Java"], ["bedrock", "Bedrock"]]:
		var b := UI.button(it[1], "secondary", func(): _edition = it[0]; _refresh_filters(); _load())
		b.custom_minimum_size = Vector2(96, 38)
		_seg[it[0]] = b
		r1.add_child(b)
	r1.add_child(UI.spacer())
	_q = UI.line_edit("", "Поиск по названию")
	_q.custom_minimum_size = Vector2(0 if UI.compact() else 260, 40)
	_q.text_submitted.connect(func(_t): _load())
	r1.add_child(_q)
	fv.add_child(r1)
	_tags_box = HFlowContainer.new()
	_tags_box.add_theme_constant_override("h_separation", 6)
	_tags_box.add_theme_constant_override("v_separation", 6)
	fv.add_child(_tags_box)
	left.add_child(f)
	var sc := ScrollContainer.new()
	sc.size_flags_vertical = Control.SIZE_EXPAND_FILL
	sc.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	_list = UI.vbox(10)
	_list.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	sc.add_child(_list)
	left.add_child(sc)

	var right := UI.panel(18)
	right.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	var rv := UI.vbox(10)
	right.add_child(rv)
	rv.add_child(UI.h3("Популярные миры недели"))
	rv.add_child(UI.para("По числу разных гостей за неделю.", 13, UI.DIM))
	_top = UI.vbox(8)
	rv.add_child(_top)
	add_child(right)
	Social.changed.connect(_on_changed)
	_refresh_filters()
	_load()
	Social.request("top")


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
	for k in _seg:
		var on: bool = k == _edition
		_seg[k].add_theme_stylebox_override("normal", UI.box(Color(UI.accent(), 0.18) if on else UI.CARD_HI, 10, 10, UI.accent() if on else Color(0, 0, 0, 0), 1 if on else 0))
	for c in _tags_box.get_children():
		c.queue_free()
	var tags: Array = Social.tags if not Social.tags.is_empty() else ["выживание", "творчество", "pvp", "мини-игры", "новоград"]
	for t in [""] + tags:
		var on: bool = t == _tag
		var b := UI.button("Любые" if t == "" else "#" + t, "ghost", func(): _tag = t; _refresh_filters(); _load())
		b.custom_minimum_size.y = 32
		b.add_theme_stylebox_override("normal", UI.box(Color(UI.accent(), 0.18) if on else Color(1, 1, 1, 0.04), 99, 8))
		b.add_theme_color_override("font_color", UI.TEXT if on else UI.MUTED)
		_tags_box.add_child(b)


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
		_list.add_child(UI.para("Нет связи с сервером LANTERN. Список появится, когда интернет вернётся.", 15, UI.YELLOW))
		return
	if Social.rooms.is_empty():
		var p := UI.panel(26)
		var v := UI.vbox(10)
		p.add_child(v)
		v.add_child(UI.h3("Сейчас нет открытых миров" + (" с такими фильтрами" if _tag != "" or _edition != "any" or _q.text != "" else "")))
		v.add_child(UI.para("Станьте первым: создайте мир и включите «Открытый мир».", 14, UI.MUTED))
		v.add_child(UI.button("Создать мир", "primary", func(): app.go("home")))
		_list.add_child(p)
		return
	for r in Social.rooms:
		_list.add_child(_card(r))


func _card(r: Dictionary) -> Control:
	var ed: String = r.edition
	var col := UI.JAVA if ed == "java" else (UI.BEDROCK if ed == "bedrock" else Color("c58bff"))
	var p := UI.panel(16, UI.CARD, 16)
	var row := UI.hbox(14)
	p.add_child(row)
	var host: Dictionary = r.get("host") if r.get("host") is Dictionary else {}
	row.add_child(LanternIcon.new().setup(int(host.get("lantern", {}).get("level", 1)), null, 54))
	var v := UI.vbox(4)
	v.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	var t := UI.label(r.title, 18, UI.TEXT, UI.font_bold)
	t.clip_text = true
	t.text_overrun_behavior = TextServer.OVERRUN_TRIM_ELLIPSIS
	v.add_child(t)
	var meta := UI.hbox(6)
	meta.add_child(UI.pill({"java": "Java", "bedrock": "Bedrock", "both": "Java + Bedrock"}.get(ed, ed), col))
	if r.get("version", "") != "":
		meta.add_child(UI.pill(r.version, UI.MUTED))
	for tg in r.get("tags", []):
		meta.add_child(UI.pill("#" + tg, UI.accent()))
	v.add_child(meta)
	v.add_child(UI.label("Хозяин: %s · ♥ %d · открыт %d мин назад" % [host.get("tag", "гость"), int(r.get("likes", 0)), int(r.get("age", 0))], 13, UI.MUTED))
	row.add_child(v)
	var rv := UI.vbox(6)
	var pl := UI.label("%d / %d" % [int(r.players), int(r.slots)], 20, UI.TEXT, UI.font_black)
	pl.horizontal_alignment = HORIZONTAL_ALIGNMENT_RIGHT
	rv.add_child(pl)
	var full: bool = int(r.players) >= int(r.slots)
	var b := UI.button("Полон" if full else "Зайти", "primary", func(): app.go("join", true, {"input": r.code, "edition": "both", "public": true}))
	b.disabled = full
	b.custom_minimum_size.x = 120
	rv.add_child(b)
	row.add_child(rv)
	UI.hover_scale(p, 1.01)
	return p


func _render_top() -> void:
	for c in _top.get_children():
		c.queue_free()
	if Social.top_hosts.is_empty():
		_top.add_child(UI.para("Пока пусто — эта неделя только началась.", 14, UI.DIM))
	var i := 1
	for h in Social.top_hosts:
		var row := UI.hbox(10)
		row.add_child(UI.label("%d" % i, 18, Color("ffb648") if i <= 3 else UI.MUTED, UI.font_black))
		row.add_child(LanternIcon.new().setup(int(h.get("lantern", {}).get("level", 1)), h.get("equip", ""), 36))
		var v := UI.vbox(0)
		v.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		v.add_child(UI.label(h.get("tag", ""), 15, UI.TEXT, UI.font_bold))
		v.add_child(UI.label("гостей: %d · ♥ %d" % [int(h.visits), int(h.get("likes", 0))], 12, UI.MUTED))
		row.add_child(v)
		_top.add_child(row)
		i += 1
