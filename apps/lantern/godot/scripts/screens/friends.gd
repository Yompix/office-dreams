extends HBoxContainer
## Друзья: мой тег и QR, добавление по тегу, заявки, список со статусами, «Присоединиться»/«Пригласить», личный чат.

var app
var arg := {}

var _tag_in: LineEdit
var _list: VBoxContainer
var _chat_holder: VBoxContainer
var _peer := 0
var _compact := false
var _left: VBoxContainer
var _right: PanelContainer


func _ready() -> void:
	add_theme_constant_override("separation", 20)
	_compact = UI.compact()
	var left := UI.vbox(10 if _compact else 14)
	left.custom_minimum_size.x = 0 if _compact else 560
	left.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_left = left
	add_child(left)
	left.add_child(UI.h1("Друзья"))
	# мой тег
	var mp := UI.panel(16)
	var mr := UI.hbox(14)
	mp.add_child(mr)
	var mv := UI.vbox(6)
	mv.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	mv.add_child(UI.label("ВАШ ТЕГ", 12, UI.MUTED, UI.font_bold))
	var tag: String = Social.me.get("tag", "—")
	mv.add_child(UI.label(tag, 24, Color("ffb648"), UI.font_black))
	var ar := UI.hbox(8)
	ar.add_child(UI.button("Скопировать тег", "secondary", func(): UI.copy(tag); app.toast("Тег скопирован: " + tag)))
	mv.add_child(ar)
	mr.add_child(mv)
	if Social.online():
		var q := QR.encode(DeepLink.friend_link(tag), QR.ECC_M)
		var qt := TextureRect.new()
		qt.texture = ImageTexture.create_from_image(q.to_image(4, Color("0b0e14"), Color("eef1f7")))
		qt.custom_minimum_size = Vector2(84, 84) if _compact else Vector2(104, 104)
		qt.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
		qt.texture_filter = CanvasItem.TEXTURE_FILTER_NEAREST
		qt.tooltip_text = "Друг сканирует этот QR в LANTERN на телефоне"
		mr.add_child(qt)
	left.add_child(mp)
	# добавить
	var ap := UI.panel(14)
	var arow := UI.hbox(8)
	ap.add_child(arow)
	_tag_in = UI.line_edit("", "Тег: Алекс#4821")
	_tag_in.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_tag_in.text_submitted.connect(func(_t): _add())
	arow.add_child(_tag_in)
	arow.add_child(UI.button("Добавить", "primary", _add))
	left.add_child(ap)
	var sc := ScrollContainer.new()
	sc.size_flags_vertical = Control.SIZE_EXPAND_FILL
	sc.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	_list = UI.vbox(8)
	_list.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	sc.add_child(_list)
	left.add_child(sc)

	var right := UI.panel(12 if _compact else 18)
	right.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_chat_holder = UI.vbox(10)
	right.add_child(_chat_holder)
	add_child(right)
	_right = right
	if _compact:
		right.visible = false   # телефон: список друзей и чат — по очереди
	Social.changed.connect(_on_changed)
	_render()
	_peer = int(arg.get("chat", 0)) if arg is Dictionary else 0
	if _peer == 0 and not Social.friends.is_empty():
		_peer = int(Social.friends[0].id)
	if not _compact:
		_open_chat(_peer)
	elif int(arg.get("chat", 0)) if arg is Dictionary else false:
		_open_chat(int(arg.chat))
	if not Social.online():
		Social.reconnect()
	# ссылка lantern://friend?tag=… (QR друга или ссылка из мессенджера)
	if arg is Dictionary and String(arg.get("add", "")) != "":
		_tag_in.text = String(arg.add)
		if Social.online():
			_add()
		else:
			app.toast("Заявка уйдёт, когда появится связь — нажмите «Добавить»", UI.YELLOW)


func _add() -> void:
	var t := _tag_in.text.strip_edges()
	if t.begins_with("lantern://friend"):
		t = Invite._query(t).get("tag", "")
	if not t.contains("#"):
		app.toast("Нужен тег целиком: Ник#1234", UI.YELLOW)
		return
	Social.request("friend_add", {"tag": t})
	_tag_in.text = ""


func _on_changed(what: String) -> void:
	if what in ["friends", "conn"]:
		_render()
	if what == "dm":
		_render()


static func status_text(f: Dictionary) -> String:
	if not f.get("online", false):
		return "не в сети"
	var a: Variant = f.get("activity")
	if not (a is Dictionary):
		return "в сети"
	var ed := {"java": "Java", "bedrock": "Bedrock", "both": "Java + Bedrock"}.get(a.get("edition", ""), "")
	match a.get("activity", "idle"):
		"hosting": return "хостит %s %s · «%s»" % [ed, a.get("version", ""), a.get("world", "")]
		"playing": return "играет в мир «%s»" % a.get("world", "")
	return "в сети"


func _render() -> void:
	for c in _list.get_children():
		c.queue_free()
	if not Social.online():
		_list.add_child(UI.para("Нет связи с сервером LANTERN — друзья появятся, когда интернет вернётся." if Social.conn != "need_register" else "Сначала придумайте ник.", 15, UI.YELLOW))
		if Social.conn == "need_register":
			_list.add_child(UI.button("Придумать ник", "primary", func(): Overlays.register(app)))
		return
	if not Social.incoming.is_empty():
		_list.add_child(UI.label("ЗАЯВКИ В ДРУЗЬЯ", 12, UI.MUTED, UI.font_bold))
		for u in Social.incoming:
			var row := _row(u, "хочет дружить")
			row.add_child(UI.button("Принять", "primary", func(): Social.request("friend_accept", {"id": u.id})))
			row.add_child(UI.button("Нет", "ghost", func(): Social.request("friend_decline", {"id": u.id})))
			_list.add_child(row.get_parent().get_parent())
	if not Social.outgoing.is_empty():
		_list.add_child(UI.label("ОТПРАВЛЕННЫЕ ЗАЯВКИ", 12, UI.MUTED, UI.font_bold))
		for u in Social.outgoing:
			_list.add_child(_row(u, "ждём ответа").get_parent().get_parent())
	_list.add_child(UI.label("ДРУЗЬЯ · %d" % Social.friends.size(), 12, UI.MUTED, UI.font_bold))
	if Social.friends.is_empty():
		_list.add_child(UI.para("Пока никого. Отправьте свой тег другу или добавьте его по тегу.", 14, UI.DIM))
	var sorted := Social.friends.duplicate()
	sorted.sort_custom(func(a, b): return int(a.online) > int(b.online))
	var my_code: String = app.relay_host.stats().get("code", "")
	for f in sorted:
		var row := _row(f, status_text(f))
		var a: Variant = f.get("activity")
		if a is Dictionary and a.get("code", "") != "":
			row.add_child(UI.button("Зайти" if _compact else "Присоединиться", "primary", func(): app.go("join", true, {"input": a.code, "edition": "both"})))
		elif my_code != "" and f.online:
			row.add_child(UI.button("Позвать" if _compact else "Пригласить", "secondary", func():
				Social.request("invite", {"to": f.id, "code": my_code, "world": app.server.opts.get("world", ""), "edition": app.relay_host.edition})))
		var unread := int(Social.unread.get(int(f.id), 0))
		var cb := UI.button("Чат" + (" (%d)" % unread if unread > 0 else ""), "ghost", func(): _open_chat(int(f.id)))
		row.add_child(cb)
		_list.add_child(row.get_parent().get_parent())


func _row(u: Dictionary, status: String) -> HBoxContainer:
	var p := UI.panel(12, UI.CARD, 14)
	var m := MarginContainer.new()
	p.add_child(m)
	var row := UI.hbox(10)
	m.add_child(row)
	var li := LanternIcon.new().setup(int(u.get("lantern", {}).get("level", 1)), u.get("equip", ""), 46)
	li.modulate.a = 1.0 if u.get("online", true) else 0.45
	row.add_child(li)
	var v := UI.vbox(0)
	v.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	var nl := UI.hbox(6)
	nl.add_child(UI.label(u.get("nick", ""), 16, UI.TEXT, UI.font_bold))
	if not _compact:
		nl.add_child(UI.label("#" + String(u.get("tag", "#")).get_slice("#", 1), 13, UI.DIM))
	nl.add_child(UI.label("ур. %d" % int(u.get("lantern", {}).get("level", 1)), 12, Color("ffb648"), UI.font_bold))
	v.add_child(nl)
	if u.get("title", "") != "":
		v.add_child(UI.label(u.title, 12, UI.accent(), UI.font_bold))
	var sl := UI.label(status, 13, UI.JAVA if u.get("online", false) else UI.DIM)
	sl.clip_text = true
	sl.text_overrun_behavior = TextServer.OVERRUN_TRIM_ELLIPSIS
	v.add_child(sl)
	row.add_child(v)
	return row


func _open_chat(fid: int) -> void:
	_peer = fid
	for c in _chat_holder.get_children():
		c.queue_free()
	if _compact:
		_left.visible = fid == 0
		_right.visible = fid != 0
		if fid != 0:
			var back := UI.button("←  Все друзья", "ghost", func(): _open_chat(0))
			back.size_flags_horizontal = Control.SIZE_SHRINK_BEGIN
			_chat_holder.add_child(back)
		else:
			return
	if fid == 0 or Social.friend_by_id(fid).is_empty():
		_chat_holder.add_child(UI.h3("Личные сообщения"))
		_chat_holder.add_child(UI.para("Выберите друга слева, чтобы написать ему. Писать можно только друзьям.", 14, UI.MUTED))
		return
	var f := Social.friend_by_id(fid)
	var head := UI.hbox(10)
	head.add_child(LanternIcon.new().setup(int(f.get("lantern", {}).get("level", 1)), f.get("equip", ""), 40))
	var hv := UI.vbox(0)
	hv.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	hv.add_child(UI.label(f.tag, 17, UI.TEXT, UI.font_bold))
	var hs := UI.label(status_text(f), 13, UI.MUTED)
	hs.clip_text = true
	hs.text_overrun_behavior = TextServer.OVERRUN_TRIM_ELLIPSIS
	hv.add_child(hs)
	head.add_child(hv)
	var mb := MenuButton.new()
	mb.text = "•••"
	mb.flat = true
	var pm := mb.get_popup()
	pm.add_item("Удалить из друзей", 0)
	pm.add_item("Заблокировать", 1)
	pm.add_item("Пожаловаться", 2)
	pm.id_pressed.connect(func(id):
		match id:
			0: Social.request("friend_remove", {"id": fid})
			1: Social.request("block", {"id": fid})
			2: Social.request("report", {"id": fid, "reason": "профиль"})
		if id < 2:
			_open_chat(0))
	head.add_child(mb)
	_chat_holder.add_child(head)
	var chat := ChatBox.new().setup("dm", fid)
	chat.size_flags_vertical = Control.SIZE_EXPAND_FILL
	_chat_holder.add_child(chat)
	_chat_holder.size_flags_vertical = Control.SIZE_EXPAND_FILL
	Social.open_dm(fid)
