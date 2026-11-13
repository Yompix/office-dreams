extends HBoxContainer
## Друзья: мой тег (копировать, QR), добавить по тегу, заявки, список со статусами и личный чат.
## Телефон: список → чат открывается «поверх» (стрелка «Назад» в верхней панели). ПК: список слева, чат справа.

var app
var arg := {}

var _tag_in: LineEdit
var _list: VBoxContainer
var _chat_holder: VBoxContainer
var _peer := 0
var _compact := false
var _left: Control
var _right: PanelContainer


func _ready() -> void:
	add_theme_constant_override("separation", 20)
	_compact = UI.compact()
	var sc := ScrollContainer.new()
	sc.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	sc.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	sc.custom_minimum_size.x = 0 if _compact else 400
	sc.size_flags_stretch_ratio = 0.8
	_left = sc
	add_child(sc)
	var left := UI.vbox(12)
	left.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	sc.add_child(left)
	# --- мой тег
	var tag: String = Social.me.get("tag", "—")
	var mp := PanelContainer.new()
	var mst := UI.glass(2, UI.R_XL, 0, UI.accent())
	UI._hpad(mst, 16, 14)
	mp.add_theme_stylebox_override("panel", mst)
	var mr := UI.hbox(12)
	mp.add_child(mr)
	var mv := UI.vbox(2)
	mv.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	mv.add_child(UI.caption(L.t("Ваш тег"), UI.accent()))
	var tl := UI.label(tag, 22, UI.TEXT, UI.font_black)
	tl.clip_text = true
	tl.text_overrun_behavior = TextServer.OVERRUN_TRIM_ELLIPSIS
	mv.add_child(tl)
	mv.add_child(UI.para(L.t("Отправьте его другу — он добавит вас"), 12, UI.MUTED))
	mr.add_child(mv)
	mr.add_child(UI.icon_button("copy", func(): UI.copy(tag); app.toast(L.t("Тег скопирован: ") + tag), L.t("Скопировать тег"), UI.accent().lightened(0.2), Color(UI.accent(), 0.15)))
	if Social.online():
		mr.add_child(UI.icon_button("qr", func(): _qr_sheet(tag), L.t("QR-код для друга"), UI.accent().lightened(0.2), Color(UI.accent(), 0.15)))
	left.add_child(mp)
	# --- добавить
	var arow := UI.hbox(8)
	_tag_in = UI.line_edit("", L.t("Тег друга: Алекс#4821"))
	_tag_in.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_tag_in.text_submitted.connect(func(_t): _add())
	arow.add_child(_tag_in)
	var ab := UI.button(L.t("Добавить"), "tonal", _add, "plus")
	arow.add_child(ab)
	left.add_child(arow)
	_list = UI.vbox(8)
	_list.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	left.add_child(_list)
	# --- чат
	var right := PanelContainer.new()
	right.add_theme_stylebox_override("panel", UI.glass(1, UI.R_XL, 16) if not _compact else UI.box(Color(0, 0, 0, 0), UI.R_L, 0))
	right.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_chat_holder = UI.vbox(10)
	right.add_child(_chat_holder)
	add_child(right)
	_right = right
	if _compact:
		right.visible = false
	Social.changed.connect(_on_changed)
	_render()
	FX.stagger(_list, 0.035)
	_peer = int(arg.get("chat", 0)) if arg is Dictionary else 0
	if _peer == 0 and not Social.friends.is_empty() and not _compact:
		_peer = int(Social.friends[0].id)
	if not _compact:
		_open_chat(_peer)
	elif _peer != 0:
		_open_chat(_peer)
	if not Social.online():
		Social.reconnect()
	# ссылка lantern://friend?tag=… (QR друга или ссылка из мессенджера)
	if arg is Dictionary and String(arg.get("add", "")) != "":
		_tag_in.text = String(arg.add)
		if Social.online():
			_add()
		else:
			app.toast(L.t("Заявка уйдёт, когда появится связь — нажмите «Добавить»"), UI.YELLOW)


func _qr_sheet(tag: String) -> void:
	var v: VBoxContainer = Overlays.sheet(app, L.t("Ваш QR-код"), L.t("Друг наводит камеру телефона и нажимает «Открыть в LANTERN» — заявка отправится сама"))
	var t := TextureRect.new()
	QR.encode(DeepLink.friend_web_link(tag), QR.ECC_M).show_in(t, 200)
	t.size_flags_horizontal = Control.SIZE_SHRINK_CENTER
	v.add_child(t)
	var l := UI.label(tag, 22, UI.accent(), UI.font_black)
	l.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	v.add_child(l)


func _add() -> void:
	var t := _tag_in.text.strip_edges()
	if t.begins_with("lantern://friend") or DeepLink.web_tag(t) != "":
		t = String(DeepLink.parse(t).get("tag", ""))
	if not t.contains("#"):
		app.toast(L.t("Нужен тег целиком: Ник#1234"), UI.YELLOW)
		return
	Social.request("friend_add", {"tag": t})
	_tag_in.text = ""


func _on_changed(what: String) -> void:
	if what in ["friends", "conn", "dm"]:
		_render()
		if _compact and _peer != 0 and what == "friends" and _right.visible:
			_update_chat_bar()


static func status_text(f: Dictionary) -> String:
	if not f.get("online", false):
		return L.t("не в сети")
	var a: Variant = f.get("activity")
	if not (a is Dictionary):
		return L.t("в сети")
	var ed: String = {"java": "Java", "bedrock": "Bedrock", "both": "Java + Bedrock"}.get(a.get("edition", ""), "")
	match a.get("activity", "idle"):
		"hosting": return L.t("в своём мире «%s» · %s %s") % [a.get("world", ""), ed, a.get("version", "")]
		"playing": return L.t("играет в мир «%s»") % a.get("world", "")
	return L.t("в сети")


func _render() -> void:
	for c in _list.get_children():
		c.queue_free()
	if not Social.online():
		if Social.conn == "need_register":
			_list.add_child(UI.empty("user", L.t("Сначала придумайте ник"), L.t("Придумать ник"), func(): Overlays.register(app), L.t("По нику и тегу вас найдут друзья")))
		else:
			_list.add_child(UI.empty("wifi", L.t("Нет связи с сервером LANTERN"), L.t("Повторить"), func(): Social.reconnect(), (Social.net_text() if Social.net_text() != "" else L.t("Друзья появятся, когда вернётся интернет"))))
		return
	if not Social.incoming.is_empty():
		_list.add_child(UI.section(L.t("Заявки в друзья · %d") % Social.incoming.size()))
		for u in Social.incoming:
			var tr := UI.hbox(4)
			tr.add_child(UI.icon_button("close", func(): Social.request("friend_decline", {"id": u.id}), L.t("Отклонить"), UI.MUTED))
			tr.add_child(UI.button(L.t("Принять"), "tonal", func(): Social.request("friend_accept", {"id": u.id})))
			_list.add_child(_row(u, L.t("хочет дружить"), tr))
	if not Social.outgoing.is_empty():
		_list.add_child(UI.section(L.t("Отправленные заявки")))
		for u in Social.outgoing:
			_list.add_child(_row(u, L.t("ждём ответа"), null))
	_list.add_child(UI.section(L.t("Друзья · %d") % Social.friends.size()))
	if Social.friends.is_empty():
		_list.add_child(UI.empty("friends", L.t("Пока никого"), "", Callable(), L.t("Отправьте свой тег другу или добавьте его по тегу выше.")))
		return
	var sorted := Social.friends.duplicate()
	sorted.sort_custom(func(a, b): return int(a.online) > int(b.online))
	var my_code: String = app.relay_host.stats().get("code", "")
	for f in sorted:
		var tr := UI.hbox(6)
		var a: Variant = f.get("activity")
		var unread := int(Social.unread.get(int(f.id), 0))
		if unread > 0:
			tr.add_child(UI.badge(unread))
		if a is Dictionary and a.get("code", "") != "":
			tr.add_child(UI.button(L.t("Зайти"), "tonal", func(): app.go("join", true, {"input": a.code, "edition": "both"})))
		elif my_code != "" and f.online:
			tr.add_child(UI.button(L.t("Позвать"), "tonal", func(): app.room.invite(int(f.id))))   # имя мира — и у сервера, и у мира из Minecraft
		var row := _row(f, status_text(f), tr, func(): _open_chat(int(f.id)))
		if not _compact and int(f.id) == _peer:
			UI.halo(row, UI.accent(), UI.R_XL, 0.7)
		_list.add_child(row)


func _avatar(u: Dictionary, px := 46) -> Control:
	var holder := Control.new()
	holder.custom_minimum_size = Vector2(px, px)
	holder.mouse_filter = Control.MOUSE_FILTER_IGNORE
	# аватар-градиент с первой буквой ника; кольцо — «в сети»; значок — уровень фонаря
	var av := Avatar.of(u, px)
	av.size = Vector2(px, px)
	if u.get("online", false):
		av.ring = UI.JAVA
	av.modulate.a = 1.0 if u.get("online", true) else 0.5
	holder.add_child(av)
	return holder


func _row(u: Dictionary, status: String, trail: Control, cb := Callable()) -> PanelContainer:
	var r := UI.list_row(_avatar(u), String(u.get("nick", "")), status, trail, cb)
	var tv: VBoxContainer = r.get_meta("text")
	var title: Label = r.get_meta("title")
	var line := UI.hbox(6)
	tv.remove_child(title)
	title.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	line.add_child(title)
	if not _compact:
		var tg := UI.label("#" + String(u.get("tag", "#")).get_slice("#", 1), 13, UI.DIM)
		line.add_child(tg)
	if not _compact:
		line.add_child(UI.pill(L.t("ур. %d") % int(u.get("lantern", {}).get("level", 1)), UI.AMBER))
	tv.add_child(line)
	tv.move_child(line, 0)
	if tv.get_child_count() > 1:
		var sl: Label = tv.get_child(1)
		var a: Variant = u.get("activity")
		sl.add_theme_color_override("font_color", UI.JAVA if a is Dictionary and a.get("code", "") != "" else (UI.MUTED if u.get("online", false) else UI.DIM))
	if String(u.get("title", "")) != "":
		var tt := UI.label(L.t(String(u.title)), 12, UI.accent(), UI.font_bold)
		tt.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART      # 1.8.2: титул («Constructor de puentes») не распирает строку друга
		tv.add_child(tt)
	return r


func _update_chat_bar() -> void:
	var f := Social.friend_by_id(_peer)
	if f.is_empty():
		return
	app.set_title(String(f.get("nick", "")), status_text(f))


func _friend_menu(fid: int) -> MenuButton:
	var mb := MenuButton.new()
	mb.icon = UI.icon_tex("dots_v", 22, UI.TEXT)
	mb.flat = true
	mb.tooltip_text = L.t("Ещё")
	mb.custom_minimum_size = Vector2(48, 48)
	var pm := mb.get_popup()
	pm.add_item(L.t("Удалить из друзей"), 0)
	pm.add_item(L.t("Заблокировать"), 1)
	pm.add_item(L.t("Пожаловаться"), 2)
	pm.id_pressed.connect(func(id):
		match id:
			0: Social.request("friend_remove", {"id": fid})
			1: Social.request("block", {"id": fid})
			2:
				Social.request("report", {"id": fid, "reason": L.t("профиль")})
				app.toast(L.t("Жалоба отправлена"))
		if id < 2:
			_open_chat(0))
	return mb


func _open_chat(fid: int) -> void:
	_peer = fid
	for c in _chat_holder.get_children():
		c.queue_free()
	if _compact:
		var was := _right.visible
		_left.visible = fid == 0
		_right.visible = fid != 0
		app.set_nav_visible(fid == 0)
		if fid == 0:
			app.set_title(L.t("Друзья"))
			app.set_back(Callable())
			app.set_actions([])
			if was:
				FX.enter(_left, Vector2(-40, 0))
			_render()
			return
		FX.enter(_right, Vector2(40, 0))
		_update_chat_bar()
		app.set_back(func(): _open_chat(0))
		var acts := []
		var f0 := Social.friend_by_id(fid)
		var a0: Variant = f0.get("activity")
		if a0 is Dictionary and a0.get("code", "") != "":
			acts.append(UI.icon_button("play", func(): app.go("join", true, {"input": a0.code, "edition": "both"}), L.t("Зайти в мир"), UI.JAVA))
		acts.append(_friend_menu(fid))
		app.set_actions(acts)
	if fid == 0 or Social.friend_by_id(fid).is_empty():
		var e := UI.empty("chat", L.t("Личные сообщения"), "", Callable(), L.t("Выберите друга слева, чтобы написать ему. Писать можно только друзьям."))
		e.size_flags_vertical = Control.SIZE_EXPAND_FILL
		_chat_holder.add_child(e)
		return
	var f := Social.friend_by_id(fid)
	if not _compact:
		var head := UI.hbox(12)
		head.add_child(_avatar(f, 44))
		var hv := UI.vbox(0)
		hv.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		hv.add_child(UI.label(f.tag, 17, UI.TEXT, UI.font_bold))
		var hs := UI.label(status_text(f), 13, UI.MUTED)
		hs.clip_text = true
		hs.text_overrun_behavior = TextServer.OVERRUN_TRIM_ELLIPSIS
		hv.add_child(hs)
		head.add_child(hv)
		var a: Variant = f.get("activity")
		if a is Dictionary and a.get("code", "") != "":
			head.add_child(UI.button(L.t("Зайти в мир"), "primary", func(): app.go("join", true, {"input": a.code, "edition": "both"}), "play"))
		head.add_child(_friend_menu(fid))
		_chat_holder.add_child(head)
		_chat_holder.add_child(UI.divider())
	var chat := ChatBox.new().setup("dm", fid)
	chat.size_flags_vertical = Control.SIZE_EXPAND_FILL
	_chat_holder.add_child(chat)
	_chat_holder.size_flags_vertical = Control.SIZE_EXPAND_FILL
	Social.open_dm(fid)
	if not _compact:
		_render()
