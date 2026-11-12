class_name ChatBox
extends VBoxContainer
## Чат как в мессенджере: личный (mode="dm", peer=id друга) или чат комнаты (mode="room").
## Свои сообщения справа, чужие слева, время в пузыре; быстрые ответы — строкой чипов над полем.
## Ссылки не кликабельны (сервер их вырезает), у чужого сообщения — «Пожаловаться/Заблокировать».

var mode := "room"
var peer := 0
var _list: VBoxContainer
var _scroll: ScrollContainer
var _input: LineEdit
var _count := -1
var _bubble_w := 0.0

const QUICK := ["Привет!", "Го!", "Иду!", "GG", "♥", "Сейчас буду", "Где ты?"]


func setup(m: String, p := 0) -> ChatBox:
	mode = m
	peer = p
	return self


func _ready() -> void:
	add_theme_constant_override("separation", 8)
	_scroll = ScrollContainer.new()
	_scroll.size_flags_vertical = Control.SIZE_EXPAND_FILL
	_scroll.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	add_child(_scroll)
	_list = UI.vbox(4)
	_list.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_scroll.add_child(_list)
	# ширина пузырей зависит от ширины чата — пересобираем, когда она заметно меняется
	_scroll.resized.connect(func():
		if absf(_scroll.size.x - _bubble_w) > 30:
			_count = -2
			_render())
	# быстрые ответы — одна строка, листается вбок
	var qs := ScrollContainer.new()
	qs.vertical_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	qs.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_SHOW_NEVER
	qs.custom_minimum_size.y = 40
	var qr := UI.hbox(8)
	qs.add_child(qr)
	for st in QUICK:
		var b := UI.chip(st, false, func(): _send(st))
		b.custom_minimum_size.y = 36
		b.tooltip_text = "Быстрый ответ"
		qr.add_child(b)
	add_child(qs)
	# поле ввода + круглая кнопка «отправить»
	var row := UI.hbox(8)
	_input = UI.line_edit("", "Сообщение…" if mode == "dm" else "Написать всем в комнате…")
	_input.max_length = 300
	_input.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_input.custom_minimum_size.y = 48
	for st in ["normal", "focus"]:
		var sb := UI._hpad(UI.box(UI.FIELD, 24, 0, UI.accent() if st == "focus" else UI.LINE, 1 if st == "normal" else 2), 18, 10)
		_input.add_theme_stylebox_override(st, sb)
	_input.text_submitted.connect(func(t): _send(t))
	row.add_child(_input)
	var sb := UI.icon_button("send", func(): _send(_input.text), "Отправить", UI.ON_ACCENT, UI.accent())
	sb.custom_minimum_size = Vector2(48, 48)
	row.add_child(sb)
	add_child(row)
	Social.changed.connect(_on_changed)
	_render()


func _on_changed(what: String) -> void:
	if (mode == "dm" and what == "dm") or (mode == "room" and what == "room_chat"):
		_render()


func _messages() -> Array:
	return Social.dms.get(peer, []) if mode == "dm" else Social.room_msgs


func _send(t: String) -> void:
	t = t.strip_edges()
	if t == "":
		return
	if not Social.online():
		Social.notice.emit({"kind": "info", "text": "Нет связи с сервером LANTERN"})
		return
	if mode == "dm":
		Social.send_dm(peer, t)
	else:
		Social.room_say(t)
	_input.text = ""


static func _hm(ts: float) -> String:
	if ts <= 0:
		return ""
	var unix := int(ts / 1000.0) if ts > 1e11 else int(ts)
	var d := Time.get_datetime_dict_from_unix_time(unix + int(Time.get_time_zone_from_system().get("bias", 0)) * 60)
	return "%02d:%02d" % [d.hour, d.minute]


func _render() -> void:
	var msgs := _messages()
	if msgs.size() == _count and _list.get_child_count() > 0:
		return
	var added := msgs.size() - maxi(_count, 0)
	var first := _count < 0
	if _count == -2:
		added = 0
	_count = msgs.size()
	for c in _list.get_children():
		c.queue_free()
	var rules := UI.label("Будьте вежливы · сообщения хранятся сутки · не пишите личные данные", 11, UI.DIM)
	rules.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	rules.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	rules.custom_minimum_size.x = 120
	_list.add_child(rules)
	_list.add_child(UI.gap(4))
	if msgs.is_empty():
		var e := UI.para("Пока тихо. Напишите первым!", 14, UI.MUTED)
		e.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
		_list.add_child(UI.gap(24))
		_list.add_child(e)
	var my := int(Social.me.get("id", 0))
	var prev_from := -1
	var w := maxf(_scroll.size.x, 280.0)
	_bubble_w = _scroll.size.x
	for m in msgs:
		var from := int(m.get("from", 0))
		var mine := from == my
		var row := UI.hbox(6)
		row.alignment = BoxContainer.ALIGNMENT_END if mine else BoxContainer.ALIGNMENT_BEGIN
		var nick: String = m.get("nick", "")
		if nick == "":
			nick = "Вы" if mine else Social.friend_by_id(from).get("nick", "Друг")
		var grouped := from == prev_from
		prev_from = from
		if not grouped:
			_list.add_child(UI.gap(4))
		var bubble := PanelContainer.new()
		var st := UI.box(Color(UI.accent(), 0.24) if mine else UI.CARD_HI, 18, 0, Color(UI.accent(), 0.45) if mine else Color(1, 1, 1, 0.10), 1)
		UI._hpad(st, 14, 8)
		if mine:
			st.corner_radius_bottom_right = 6
		else:
			st.corner_radius_bottom_left = 6
		bubble.add_theme_stylebox_override("panel", st)
		var v := UI.vbox(2)
		if mode == "room" and not mine and not grouped:
			v.add_child(UI.label(nick, 12, UI.accent().lightened(0.2), UI.font_bold))
		var text := String(m.get("text", ""))
		var tl := UI.label(text, 15, UI.TEXT)
		tl.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
		var font: Font = UI.font_regular
		var tw := font.get_string_size(text, HORIZONTAL_ALIGNMENT_LEFT, -1, 15).x
		tl.custom_minimum_size.x = clampf(tw + 2, 24, w * 0.72 - 28)
		v.add_child(tl)
		var tm := _hm(float(m.get("ts", 0)))
		if tm != "":
			var tml := UI.label(tm, 10, UI.MUTED if not mine else Color(UI.TEXT, 0.6))
			tml.horizontal_alignment = HORIZONTAL_ALIGNMENT_RIGHT
			v.add_child(tml)
		bubble.add_child(v)
		row.add_child(bubble)
		if not mine and from > 0:
			var mb := MenuButton.new()
			mb.icon = UI.icon_tex("dots_v", 16, UI.DIM)
			mb.flat = true
			mb.tooltip_text = "Пожаловаться или заблокировать"
			mb.custom_minimum_size = Vector2(32, 32)
			mb.size_flags_vertical = Control.SIZE_SHRINK_CENTER
			var pm := mb.get_popup()
			pm.add_item("Пожаловаться", 0)
			pm.add_item("Пожаловаться и заблокировать", 1)
			pm.add_item("Заблокировать", 2)
			var fid := from
			var mid := int(m.get("id", 0))
			pm.id_pressed.connect(func(id):
				if id == 2:
					Social.request("block", {"id": fid})
					Social.notice.emit({"kind": "info", "text": "Игрок заблокирован"})
				else:
					Social.request("report", {"id": fid, "msg_id": mid if mode == "dm" else 0, "reason": "чат", "block": id == 1}))
			row.add_child(mb)
		_list.add_child(row)
	# новое сообщение «всплывает»
	if not first and added > 0 and _list.get_child_count() > 0:
		var last: Control = _list.get_child(_list.get_child_count() - 1)
		FX.enter(last, Vector2(0, 16), 0.0, 0.24)
	await get_tree().process_frame
	await get_tree().process_frame
	if is_instance_valid(_scroll):
		_scroll.scroll_vertical = int(_scroll.get_v_scroll_bar().max_value)
