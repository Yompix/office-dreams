extends VBoxContainer
## Главная: два главных действия — «Создать мир» и «Зайти к другу», потом друзья в игре и недавние миры.

var app
var arg


func _ready() -> void:
	add_theme_constant_override("separation", 16)
	var compact := UI.compact()
	# --- приветствие
	var nick: String = Social.me.get("nick", "") if Social.online() else ""
	var greet := "Привет, %s!" % nick if nick != "" else "Привет!"
	if compact:
		var hello := UI.vbox(2)
		hello.add_child(UI.label(greet, 26, UI.TEXT, UI.font_black))
		hello.add_child(UI.label("Во что играем сегодня?", 15, UI.MUTED))
		add_child(hello)
	else:
		app.set_title(greet, "Во что играем сегодня?")
	# --- мир уже идёт
	if app.own.active:
		add_child(_live_card_own())
	elif app.server.state in ["installing", "starting", "running", "stopping", "error"]:
		add_child(_live_card_host())
	if app.relay_join.stats().state in ["connecting", "online"]:
		add_child(_live_card_join())
	# --- друзья играют прямо сейчас — наверху, «Зайти» в одно нажатие
	var hosting_now := Social.online() and Social.friends.any(func(f):
		var a: Variant = f.get("activity")
		return f.get("online", false) and a is Dictionary and String(a.get("code", "")) != "")
	if hosting_now:
		var top := UI.vbox(8)
		_friends_playing(top)
		add_child(top)
	# --- два главных действия
	var acts := UI.cols(16)
	var h: Control = app.play_option("host", true)
	var j: Control = app.play_option("join", true)
	h.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	j.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	acts.add_child(h)
	acts.add_child(j)
	add_child(acts)
	# --- друзья в игре и недавние
	var lower := UI.cols(24)
	add_child(lower)
	var left := UI.vbox(8)
	left.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	lower.add_child(left)
	var right := UI.vbox(8)
	right.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	lower.add_child(right)
	if not hosting_now:
		_friends_playing(left)
	_recent(right if not hosting_now else left)
	if not compact:
		var how := UI.vbox(8)
		how.add_child(UI.section("Как это работает"))
		var hp := UI.panel(20)
		var hv := UI.vbox(16)
		hp.add_child(hv)
		hv.add_child(app._num_row("1", "Один создаёт мир", "Свой мир из Minecraft — или отдельный сервер на ПК, LANTERN всё скачает сам"))
		hv.add_child(app._num_row("2", "Отправляет код друзьям", "6 символов, например K7M-2QX, или ссылку / QR"))
		hv.add_child(app._num_row("3", "Друзья вводят код", "Мир сам появляется у них в Minecraft — как будто вы в одной Wi-Fi сети"))
		how.add_child(hp)
		add_child(how)
	FX.stagger(self, 0.04)


func _live_card_host() -> Control:
	var s: ServerHost = app.server
	var code: String = app.relay_host.stats().get("code", "")
	var st: String = {"installing": "готовится…", "starting": "запускается…", "running": "работает", "stopping": "останавливается…", "error": "— ошибка"}.get(s.state, "")
	var sub := "Код %s · игроков: %d" % [RoomCode.pretty(code), s.players.size()] if code != "" else "Нажмите, чтобы открыть"
	return UI.list_row(_dot_tile(UI.JAVA if s.state == "running" else UI.YELLOW, "server"), "Ваш мир «%s» %s" % [s.opts.get("world", "Мир"), st], sub, UI.icon("chevron", 20, UI.MUTED), func(): app.go("host"), Color("1b2a22"))


func _live_card_own() -> Control:
	var w: Dictionary = app.own.world
	var code: String = app.relay_host.stats().get("code", "")
	var sub := "Код %s · друзей: %d" % [RoomCode.pretty(code), int(app.relay_host.stats().get("peers", 0))] if code != "" else "Открываем комнату…"
	var ok: bool = app.own.alive()
	return UI.list_row(_dot_tile(UI.JAVA if ok else UI.YELLOW, "server"), "Ваш мир «%s» %s" % [w.get("name", "Мир"), "открыт" if ok else "— откройте Minecraft"], sub, UI.icon("chevron", 20, UI.MUTED), func(): app.go("host"), Color("1b2a22"))


func _live_card_join() -> Control:
	var st: Dictionary = app.relay_join.stats()
	var t := "Вы в мире «%s»" % st.name if st.state == "online" and st.name != "" else "Подключаемся к миру…"
	return UI.list_row(_dot_tile(UI.BEDROCK, "play"), t, "Код " + RoomCode.pretty(app.relay_join.code), UI.icon("chevron", 20, UI.MUTED), func(): app.go("join"), Color("18243a"))


func _dot_tile(c: Color, icon: String) -> Control:
	var t := UI.icon_tile(icon, c, 44, 22)
	FX.pulse(t, 1.06, 1.6)
	return t


func _friends_playing(box: VBoxContainer) -> void:
	box.add_child(UI.section("Друзья в игре", "Все друзья", func(): app.go("friends")))
	if not Social.online():
		var reg := Social.conn == "need_register"
		var msg := "Придумайте ник — и добавляйте друзей" if reg else "Нет связи с сервером LANTERN — друзья появятся, когда вернётся интернет"
		box.add_child(UI.list_row(UI.icon_tile("wifi" if not reg else "user", UI.YELLOW, 44, 22), "Друзья пока не видны", msg, null, func():
			if reg:
				Overlays.register(app)
			else:
				Social.reconnect()))
		return
	var playing := Social.friends.filter(func(f):
		var a: Variant = f.get("activity")
		return f.get("online", false) and a is Dictionary and String(a.get("code", "")) != "")
	if playing.is_empty():
		var online := Social.friends.filter(func(f): return f.get("online", false)).size()
		var t := "Сейчас никто не играет" if not Social.friends.is_empty() else "Добавьте друзей по тегу"
		var sub := ("В сети: %d. Позовите их в свой мир!" % online) if not Social.friends.is_empty() else "Ваш тег: %s" % Social.me.get("tag", "")
		box.add_child(UI.list_row(UI.icon_tile("friends", UI.accent(), 44, 22), t, sub, UI.icon("chevron", 20, UI.DIM), func(): app.go("friends")))
		return
	for f in playing.slice(0, 3):
		var a: Dictionary = f.activity
		var ed: String = {"java": "Java", "bedrock": "Bedrock", "both": "Java + Bedrock"}.get(a.get("edition", ""), "")
		var b := UI.button("Зайти", "primary", func(): app.go("join", true, {"input": a.code, "edition": "both"}))
		b.custom_minimum_size.x = 88
		var li := LanternIcon.new().setup(int(f.get("lantern", {}).get("level", 1)), f.get("equip", ""), 44)
		box.add_child(UI.list_row(li, "%s · «%s»" % [f.nick, a.get("world", "")], ("хостит " if a.get("activity") == "hosting" else "играет · ") + ed, b))


func _recent(box: VBoxContainer) -> void:
	box.add_child(UI.section("Недавние миры", "Открытые миры", func(): app.go("rooms")))
	var list: Array = Cfg.recent()
	if list.is_empty():
		box.add_child(UI.list_row(UI.icon_tile("globe", UI.BEDROCK, 44, 22), "Открытые миры", "Зайдите в мир, открытый для всех", UI.icon("chevron", 20, UI.DIM), func(): app.go("rooms")))
		return
	for e in list.slice(0, 3):
		var nm: String = e.get("name", "")
		var room: bool = e.get("kind") == "room"
		var code: String = RoomCode.pretty(e.address) if room else e.address
		var ed: String = {"java": "Java", "bedrock": "Bedrock", "both": "Java + Bedrock"}.get(e.get("edition", ""), "")
		var b := UI.button("Зайти", "secondary", func(): app.go("join", true, {"input": code}))
		b.custom_minimum_size.x = 88
		var col := UI.BEDROCK if e.get("edition", "") == "bedrock" else UI.JAVA
		box.add_child(UI.list_row(UI.icon_tile("phone" if e.get("edition", "") == "bedrock" else "java", col, 44, 22), nm if nm != "" else code, ("код " + code if room else code) + (" · " + ed if ed != "" else ""), b))
