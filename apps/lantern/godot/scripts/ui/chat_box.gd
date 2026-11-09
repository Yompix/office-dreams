class_name ChatBox
extends VBoxContainer
## Чат: личный (mode="dm", peer=id друга) или чат комнаты (mode="room").
## Ссылки не кликабельны (сервер их вырезает), у каждого сообщения — «Пожаловаться/Заблокировать».

var mode := "room"
var peer := 0
var _list: VBoxContainer
var _scroll: ScrollContainer
var _input: LineEdit
var _count := -1


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
	_list = UI.vbox(6)
	_list.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_scroll.add_child(_list)
	var row := UI.hbox(6)
	for st in ["Привет!", "GG", "Го!", "♥"]:
		var b := UI.button(st, "ghost", func(): _send(st))
		b.custom_minimum_size = Vector2(0, 38)
		b.tooltip_text = "Стикер"
		row.add_child(b)
	_input = UI.line_edit("", "Сообщение…" if mode == "dm" else "Написать всем в комнате…")
	_input.max_length = 300
	_input.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_input.custom_minimum_size.y = 40
	_input.text_submitted.connect(func(t): _send(t))
	row.add_child(_input)
	var sb := UI.button("➤", "primary", func(): _send(_input.text))
	sb.custom_minimum_size = Vector2(46, 40)
	row.add_child(sb)
	add_child(row)
	add_child(UI.label("Будьте вежливы. Сообщения хранятся сутки. Не сообщайте личные данные.", 11, UI.DIM))
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


func _render() -> void:
	var msgs := _messages()
	if msgs.size() == _count and _list.get_child_count() > 0:
		return
	_count = msgs.size()
	for c in _list.get_children():
		c.queue_free()
	if msgs.is_empty():
		_list.add_child(UI.label("Пока тихо. Напишите первым!", 13, UI.DIM))
	var my := int(Social.me.get("id", 0))
	for m in msgs:
		var mine := int(m.get("from", 0)) == my
		var row := UI.hbox(6)
		var nick: String = m.get("nick", "")
		if nick == "":
			nick = "Вы" if mine else Social.friend_by_id(int(m.get("from", 0))).get("nick", "Друг")
		var bubble := PanelContainer.new()
		bubble.add_theme_stylebox_override("panel", UI.box(Color(UI.accent(), 0.16) if mine else UI.CARD_HI, 12, 10))
		bubble.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		var v := UI.vbox(2)
		v.add_child(UI.label(nick, 12, UI.accent() if mine else UI.MUTED, UI.font_bold))
		var tl := UI.para(String(m.get("text", "")), 14, UI.TEXT)
		v.add_child(tl)
		bubble.add_child(v)
		if mine:
			row.add_child(UI.gap(30))
		row.add_child(bubble)
		if not mine and int(m.get("from", 0)) > 0:
			var mb := MenuButton.new()
			mb.text = "•••"
			mb.flat = true
			mb.custom_minimum_size = Vector2(26, 30)
			var pm := mb.get_popup()
			pm.add_item("Пожаловаться", 0)
			pm.add_item("Пожаловаться и заблокировать", 1)
			pm.add_item("Заблокировать", 2)
			var fid := int(m.from)
			var mid := int(m.get("id", 0))
			pm.id_pressed.connect(func(id):
				if id == 2:
					Social.request("block", {"id": fid})
					Social.notice.emit({"kind": "info", "text": "Игрок заблокирован"})
				else:
					Social.request("report", {"id": fid, "msg_id": mid if mode == "dm" else 0, "reason": "чат", "block": id == 1}))
			row.add_child(mb)
		_list.add_child(row)
	await get_tree().process_frame
	_scroll.scroll_vertical = int(_scroll.get_v_scroll_bar().max_value)
