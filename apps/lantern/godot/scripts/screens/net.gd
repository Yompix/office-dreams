extends VBoxContainer
## 2.0: «Любые игры» — виртуальная локальная сеть (как Hamachi / Radmin VPN). docs/VLAN.md
## Нет сети: создать / войти по коду (8 цифр или 6 знаков, QR и ссылка — те же, что у комнат), «Найти игроков»
## (открытые сети по игре), «Как играть по сети» (12 игр ПК + 7 Android).
## В сети: код и мой IP, «Подключить» (адаптер: UAC в Windows / запрос VPN на Android), участники с IP, пингом,
## P2P/relay и графиком связи, чат с реакциями, выйти / закрыть сеть.

var app: Control
var arg: Variant = null

var _vn: VNet
var _body: VBoxContainer
var _mode := ""
var _members: VBoxContainer
var _adapter_box: VBoxContainer
var _chat_list: VBoxContainer
var _chat_in: LineEdit
var _head: VBoxContainer
var _browse: VBoxContainer
var _browse_game := ""
var _last_chat := -1

const REACT := ["👍", "❤️", "😂", "🔥", "🎮", "👋"]


func _ready() -> void:
	add_theme_constant_override("separation", 14)
	_vn = app.vnet
	_vn.nick = str(Social.me.get("nick", ""))
	_vn.changed.connect(_refresh)
	_body = UI.vbox(14)
	add_child(_body)
	if arg is Dictionary and str(arg.get("code", "")) != "" and not _vn.active():
		_vn.join(str(arg.code))
	_refresh()


func _mode_of(s: Dictionary) -> String:
	match str(s.get("state", "idle")):
		"online", "reconnecting":
			return "online" if s.has("ip") else "wait"
		"connecting":
			return "wait"
	return "idle"


func _refresh() -> void:
	if not is_inside_tree():
		return
	var s := _vn.snapshot()
	var m := _mode_of(s)
	if m != _mode:
		_mode = m
		for c in _body.get_children():
			c.queue_free()
		match m:
			"idle": _build_idle(s)
			"wait": _build_wait()
			"online": _build_online(s)
		FX.stagger(_body, 0.04)
	if m == "online":
		_update_online(s)
	if app.has_method("set_title") and app.get("current") == "net" and not has_meta("leaving") and not (get_parent() and get_parent().has_meta("leaving")):     # 2.0.3: уходящий экран заголовок не трогает
		app.set_title(L.t("Любые игры"), str(s.get("name", "")) if m == "online" else L.t("Виртуальная локальная сеть"))


# ============================================================ нет сети

func _build_idle(s: Dictionary) -> void:
	var top := UI.hbox(14)
	var ch := Chibi.new()
	ch.custom_minimum_size = Vector2(110, 110)
	top.add_child(ch)
	var tv := UI.vbox(6)
	tv.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	tv.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	tv.add_child(UI.hero(L.t("Любые игры"), 30))
	tv.add_child(UI.para(L.t("Общая локальная сеть через интернет: игры думают, что вы в одной комнате. Как Hamachi, только проще."), 14))
	top.add_child(tv)
	_body.add_child(top)
	if str(s.get("state", "")) == "error":
		_body.add_child(_notice(_err_text(str(s.get("error", ""))), UI.RED))
	# три шага
	var steps := UI.vbox(8)
	for st in [["1", L.t("Создайте сеть и отправьте другу код или QR")], ["2", L.t("Оба нажмите «Подключить» — LANTERN включит сетевой адаптер")], ["3", L.t("В игре выберите «по локальной сети» или введите IP друга")]]:
		var r := UI.hbox(10)
		var n := UI.label(st[0], 12, UI.ON_ACCENT, UI.font_caps)
		n.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
		n.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
		n.custom_minimum_size = Vector2(28, 28)
		var np := PanelContainer.new()
		np.add_theme_stylebox_override("panel", UI.box(UI.TEXT, 0))
		np.add_child(n)
		np.size_flags_vertical = Control.SIZE_SHRINK_BEGIN
		r.add_child(np)
		var p := UI.para(st[1], 14, UI.TEXT)
		p.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		r.add_child(p)
		steps.add_child(r)
	_body.add_child(steps)
	var row := UI.cols(10)
	var cb := UI.button(L.t("Создать сеть"), "primary", _create_sheet, "plus")
	cb.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	row.add_child(cb)
	var jb := UI.button(L.t("Войти по коду"), "secondary", _join_sheet, "play")
	jb.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	row.add_child(jb)
	_body.add_child(row)
	# найти игроков
	_body.add_child(UI.section(L.t("Найти игроков"), L.t("Обновить"), func(): _load_browse()))
	var chips := UI.tabs([["", L.t("Все")]] + VGames.for_platform().slice(0, 8).map(func(g): return [g.id, g.name]), _browse_game, func(k):
		_browse_game = k
		_load_browse())
	_body.add_child(chips)
	_browse = UI.vbox(8)
	_body.add_child(_browse)
	_load_browse()
	_body.add_child(_guide())
	_body.add_child(_limits_note())


func _load_browse() -> void:
	if not is_instance_valid(_browse):
		return
	for c in _browse.get_children():
		c.queue_free()
	_browse.add_child(FX.skeleton(56, 0))
	var r: Dictionary = await Net.get_json(Social.http_base() + "/vnets?game=" + _browse_game.uri_encode())
	if not is_instance_valid(_browse):
		return
	FX.calm(_show_browse.bind(r))


func _show_browse(r: Dictionary) -> void:
	if not is_instance_valid(_browse):
		return
	for c in _browse.get_children():
		c.queue_free()
	var nets: Array = (r.get("data", {}) as Dictionary).get("nets", []) if r.get("data") is Dictionary else []
	if nets.is_empty():
		_browse.add_child(UI.para(L.t("Сейчас открытых сетей нет. Создайте свою и отметьте «Видна всем» — к вам зайдут."), 13, UI.DIM))
		return
	for n in nets:
		var sub := "%s · %d/%d" % [VGames.title(str(n.get("game", ""))), int(n.get("n", 0)), int(n.get("cap", 0))]
		var lock := UI.icon("lock", 18, UI.DIM) if n.get("locked", false) else UI.icon("chevron", 20, UI.DIM)
		var num := str(n.get("num", ""))
		var locked: bool = n.get("locked", false)
		var br := UI.list_row(Avatar.make(str(n.get("owner", "?")), 40), str(n.get("name", "")), sub, lock, func():
			if locked:
				_join_sheet(num)
			else:
				_vn.join(num))
		FlameFX.aura(br, str(n.get("flame", "")))     # 2.0.3: «Глитч-пламя» хозяина — аура у всех
		_browse.add_child(br)


func _create_sheet() -> void:
	var v: VBoxContainer = Overlays.sheet(app, L.t("Новая сеть"), L.t("Друзья войдут по коду, ссылке или QR."))
	var name_e := UI.line_edit(L.t("Сеть %s") % str(Social.me.get("nick", "LANTERN")), L.t("Название"))
	v.add_child(UI.field(L.t("Название"), name_e))
	var game := ["any"]
	var opts := [L.t("Любая игра")] + VGames.for_platform().map(func(g): return g.name)
	var ids := ["any"] + VGames.for_platform().map(func(g): return g.id)
	var ob := UI.option(opts, 0)
	ob.fit_to_longest_item = false      # длинные названия игр не распирают лист на узком телефоне
	ob.clip_text = true
	ob.custom_minimum_size.x = 120
	ob.item_selected.connect(func(i): game[0] = ids[i])
	v.add_child(UI.field(L.t("Во что играем"), ob))
	var pub := [false]
	var sw := UI.switch_row(L.t("Видна всем"), L.t("Сеть появится в «Найти игроков» — зайти сможет любой"), false, func(on): pub[0] = on)
	v.add_child(sw.row)
	var pw := UI.line_edit("", L.t("Без пароля"))
	pw.secret = true
	v.add_child(UI.field(L.t("Пароль (не обязательно)"), pw))
	var b := UI.button(L.t("Создать сеть"), "primary", func():
		Overlays.close_sheet(app)
		_vn.create({"name": name_e.text.strip_edges(), "game": game[0], "public": pub[0], "password": pw.text}), "plus")
	b.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	v.add_child(b)


func _join_sheet(code := "") -> void:
	var v: VBoxContainer = Overlays.sheet(app, L.t("Войти в сеть"), L.t("Номер из 8 цифр или код из 6 знаков от друга."))
	var ce := UI.line_edit(code, "12345678")
	v.add_child(UI.field(L.t("Код сети"), ce))
	var pw := UI.line_edit("", L.t("Если сеть с паролем"))
	pw.secret = true
	v.add_child(UI.field(L.t("Пароль"), pw))
	var b := UI.button(L.t("Войти"), "primary", func():
		var c := ce.text.strip_edges()
		var web := Invite.web_code(c)
		if web != "":
			c = web
		if c == "":
			return
		Overlays.close_sheet(app)
		_vn.join(c, pw.text), "play")
	b.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	v.add_child(b)


# ============================================================ подключаемся

func _build_wait() -> void:
	var c := UI.vbox(12)
	c.alignment = BoxContainer.ALIGNMENT_CENTER
	var ch := Chibi.new()
	ch.custom_minimum_size = Vector2(160, 160)
	ch.size_flags_horizontal = Control.SIZE_SHRINK_CENTER
	c.add_child(ch)
	var l := UI.label(L.t("Подключаемся к сети…"), 16, UI.TEXT, UI.font_bold)
	l.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	c.add_child(l)
	var x := UI.button(L.t("Отмена"), "ghost", func(): _vn.leave())
	x.size_flags_horizontal = Control.SIZE_SHRINK_CENTER
	c.add_child(x)
	_body.add_child(c)


# ============================================================ в сети

func _build_online(s: Dictionary) -> void:
	_head = UI.vbox(10)
	_body.add_child(_head)
	_adapter_box = UI.vbox(8)
	_body.add_child(_adapter_box)
	_body.add_child(UI.section(L.t("Кто в сети")))
	_members = UI.vbox(8)
	_body.add_child(_members)
	_body.add_child(UI.section(L.t("Чат сети")))
	_chat_list = UI.vbox(6)
	_body.add_child(_chat_list)
	var cr := UI.hbox(8)
	_chat_in = UI.line_edit("", L.t("Написать всем…"))
	_chat_in.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_chat_in.max_length = 300
	_chat_in.text_submitted.connect(func(_t): _send_chat())
	cr.add_child(_chat_in)
	cr.add_child(UI.icon_button("send", _send_chat, L.t("Отправить")))
	_body.add_child(cr)
	_body.add_child(_guide())
	var lv := UI.button(L.t("Закрыть сеть для всех") if s.get("is_owner", false) else L.t("Выйти из сети"), "danger", _leave, "close")
	lv.size_flags_horizontal = Control.SIZE_SHRINK_BEGIN
	_body.add_child(lv)
	_last_chat = -1


func _update_online(s: Dictionary) -> void:
	# --- шапка: название, код, мой IP
	for c in _head.get_children():
		c.queue_free()
	var card := PanelContainer.new()
	card.add_theme_stylebox_override("panel", UI._hpad(UI.pix(Color(0.05, 0.05, 0.05, 0.92), Color(1, 1, 1, 0.85), 5), 16, 14))
	var cv := UI.vbox(8)
	card.add_child(cv)
	var tr := UI.hbox(8)
	var nm := UI.label(str(s.get("name", "")), 20, UI.TEXT, UI.font_title)
	nm.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	nm.clip_text = true
	nm.text_overrun_behavior = TextServer.OVERRUN_TRIM_ELLIPSIS
	tr.add_child(nm)
	var st := str(s.get("state", ""))
	tr.add_child(UI.pill(L.t("в сети") if st == "online" else L.t("переподключаемся…"), UI.GREEN if st == "online" else UI.YELLOW))
	cv.add_child(tr)
	var gcap := UI.caption(VGames.title(str(s.get("game", "any"))) + ("  ·  " + L.t("видна всем") if s.get("public", false) else ""))
	gcap.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	gcap.custom_minimum_size.x = 80
	cv.add_child(gcap)
	var code := str(s.get("num", ""))
	var cl := UI.label(code.substr(0, 4) + " " + code.substr(4), 26, UI.TEXT, UI.font_caps)
	cv.add_child(cl)
	var ipr := UI.hbox(8)
	ipr.add_child(UI.label(L.t("Мой IP:"), 14, UI.MUTED))
	var ipl := UI.label(str(s.get("ip", "")), 16, UI.TEXT, UI.font_caps)
	ipl.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	ipr.add_child(ipl)
	cv.add_child(ipr)
	var br := UI.vbox(8)
	var inv := UI.button(L.t("Скопировать приглашение"), "outline", func():
		UI.copy(L.t("Заходи в мою сеть LANTERN «%s»: %s (номер %s)") % [str(s.get("name", "")), _link(s), code])
		app.toast(L.t("Приглашение скопировано")), "copy")
	inv.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	inv.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART     # во всю ширину; на 320 dp — в две строки
	inv.custom_minimum_size.x = 0
	br.add_child(inv)
	var qb := UI.button(L.t("QR-код"), "outline", func(): _qr(s), "qr")
	qb.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	br.add_child(qb)
	cv.add_child(br)
	_head.add_child(card)
	# --- адаптер
	for c in _adapter_box.get_children():
		c.queue_free()
	_adapter_box.add_child(_adapter_card(s))
	# --- участники
	for c in _members.get_children():
		c.queue_free()
	var me_row := _member_row({"name": str(Social.me.get("nick", L.t("Вы"))), "ip": str(s.get("ip", "")), "me": true, "flame": FlameFX.mine(), "rtt": int(s.get("my_relay_ms", 0)), "p2p": false, "graph": []}, s)
	_members.add_child(me_row)
	var ms: Array = s.get("members", [])
	if ms.is_empty():
		_members.add_child(UI.para(L.t("Пока вы одни. Отправьте другу код — он появится здесь."), 13, UI.DIM))
	for mb in ms:
		_members.add_child(_member_row(mb, s))
	# --- чат: только новые сообщения
	var chat: Array = s.get("chat", [])
	var sig: int = chat.size() * 1000 + chat.reduce(func(a, c): return a + (c.re as Dictionary).size(), 0)
	if sig != _last_chat:
		_last_chat = sig
		for c in _chat_list.get_children():
			c.queue_free()
		if chat.is_empty():
			_chat_list.add_child(UI.para(L.t("Договоритесь здесь, во что и как играть."), 13, UI.DIM))
		for c in chat.slice(maxi(0, chat.size() - 12)):
			_chat_list.add_child(_chat_row(c))


func _adapter_card(s: Dictionary) -> Control:
	var p := PanelContainer.new()
	var a := str(s.get("adapter", "none"))
	p.add_theme_stylebox_override("panel", UI._hpad(UI.pix(Color(0.04, 0.04, 0.04, 0.9), Color(1, 1, 1, 0.5 if a != "ok" else 0.25), 4), 14, 12))
	# текст сверху, кнопка снизу на всю ширину: длинные переводы не распирают карточку на узком телефоне
	var r := UI.vbox(10)
	p.add_child(r)
	var tv := UI.vbox(2)
	tv.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	match a:
		"ok":
			tv.add_child(UI.dot_label(L.t("Адаптер LANTERN подключён"), UI.GREEN, 15))
			tv.add_child(UI.para(L.t("Запускайте игру и выбирайте игру по локальной сети."), 13, UI.MUTED))
		"starting":
			tv.add_child(_title(L.t("Подключаем адаптер…"), UI.TEXT))
			var hint := L.t("Windows спросит разрешение — нажмите «Да».") if OS.get_name() == "Windows" else (L.t("Разрешите VPN в окне Android.") if OS.get_name() == "Android" else "")
			if str(s.get("adapter_err", "")) == "permission":
				hint = VAdapter.error_text("permission")
			if hint != "":
				tv.add_child(UI.para(hint, 13, UI.MUTED))
		"error":
			tv.add_child(_title(L.t("Адаптер не подключён"), UI.RED))
			tv.add_child(UI.para(VAdapter.error_text(str(s.get("adapter_err", ""))), 13, UI.MUTED))
		_:
			tv.add_child(_title(L.t("Шаг 2 — подключите адаптер"), UI.TEXT))
			tv.add_child(UI.para(L.t("Без него игры не увидят друзей. Один раз Windows попросит разрешение.") if OS.get_name() == "Windows" else L.t("Без него игры не увидят друзей."), 13, UI.MUTED))
	r.add_child(tv)
	if a == "ok":
		r.add_child(UI.button(L.t("Отключить"), "ghost", func(): VAdapter.stop(_vn)))
	elif a != "starting":
		var b := UI.button(L.t("Подключить"), "primary", func(): FX.haptic(16); VAdapter.start(_vn), "bolt")
		b.disabled = not VAdapter.supported()
		b.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		r.add_child(b)
	else:
		var ch := Chibi.new()
		ch.orbit = false
		ch.custom_minimum_size = Vector2(44, 44)
		r.add_child(ch)
	return p


func _title(t: String, c: Color) -> Label:
	var l := UI.label(t, 15, c, UI.font_bold)
	l.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	l.custom_minimum_size.x = 80
	return l


func _member_row(mb: Dictionary, s: Dictionary) -> Control:
	var p := PanelContainer.new()
	p.add_theme_stylebox_override("panel", UI._hpad(UI.pix(Color(0.03, 0.03, 0.03, 0.8), Color(1, 1, 1, 0.22), 3), 12, 10))
	var r := UI.hbox(10)
	p.add_child(r)
	r.add_child(Avatar.make(str(mb.get("name", "?")), 40))
	var tv := UI.vbox(2)
	tv.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	var nm := str(mb.get("name", ""))
	if mb.get("me", false):
		nm += " · " + L.t("вы")
	elif mb.get("owner", false):
		nm += " · " + L.t("создатель")
	var nl := UI.label(nm, 15, UI.TEXT, UI.font_bold)
	nl.clip_text = true
	nl.text_overrun_behavior = TextServer.OVERRUN_TRIM_ELLIPSIS
	FlameFX.style_nick(nl, str(mb.get("flame", "")))     # 2.0.3: «Неоновое пламя» — ник светится у всех
	tv.add_child(nl)
	var ipb := UI.button(str(mb.get("ip", "")), "link", func():
		UI.copy(str(mb.get("ip", "")))
		app.toast(L.t("IP скопирован — вставьте его в игре")))
	ipb.custom_minimum_size.y = 32
	ipb.size_flags_horizontal = Control.SIZE_SHRINK_BEGIN
	ipb.add_theme_font_override("font", UI.font_caps)
	ipb.add_theme_font_size_override("font_size", 11)
	tv.add_child(ipb)
	r.add_child(tv)
	var rv := UI.vbox(4)
	rv.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	var rtt := int(mb.get("rtt", 0))
	if mb.get("me", false):
		rv.add_child(UI.num("%d ms" % rtt if rtt > 0 else "", 13, UI.DIM))
	else:
		rv.add_child(UI.pill("P2P" if mb.get("p2p", false) else "RELAY", UI.GREEN if mb.get("p2p", false) else UI.MUTED))
		rv.add_child(UI.num("%d ms" % rtt if rtt > 0 else "…", 13, UI.TEXT))
		var g := PingGraph.new()
		g.points = mb.get("graph", [])
		g.custom_minimum_size = Vector2(72, 22)
		rv.add_child(g)
	r.add_child(rv)
	if s.get("is_owner", false) and not mb.get("me", false):
		var mbtn := UI.icon_button("dots_v", func(): _member_menu(mb), L.t("Ещё"), UI.MUTED)
		r.add_child(mbtn)
	return p


func _member_menu(mb: Dictionary) -> void:
	var v: VBoxContainer = Overlays.sheet(app, str(mb.get("name", "")), str(mb.get("ip", "")))
	v.add_child(UI.button(L.t("Выгнать из сети"), "secondary", func(): Overlays.close_sheet(app); _vn.kick(str(mb.peer)), "close"))
	v.add_child(UI.button(L.t("Выгнать и закрыть вход"), "danger", func(): Overlays.close_sheet(app); _vn.kick(str(mb.peer), true), "lock"))


func _chat_row(c: Dictionary) -> Control:
	var v := UI.vbox(2)
	var fl := str(c.get("flame", ""))
	if FlameFX.has(fl, "neon_nick"):
		var hr := UI.hbox(0)
		var nl := UI.label(str(c.name) + ": ", 14, UI.TEXT, UI.font_bold)
		FlameFX.style_nick(nl, fl)
		hr.add_child(nl)
		var tl := UI.para(str(c.text), 14, UI.TEXT)
		tl.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		hr.add_child(tl)
		v.add_child(hr)
	else:
		var t := UI.para("%s: %s" % [c.name, c.text], 14, UI.TEXT)
		v.add_child(t)
	var rr := UI.flow(4)
	# 2.0.3: «Пиксельное пламя» — ещё 4 реакции (сервер проверяет пламя); чужие пиксельные реакции видны всем
	var set_ := REACT.duplicate()
	for e in FlameFX.PIXEL_REACTIONS:
		if FlameFX.has(FlameFX.mine(), "pixel_react") or int((c.re as Dictionary).get(e, 0)) > 0:
			set_.append(e)
	for e in set_:
		var n := int((c.re as Dictionary).get(e, 0))
		var b := Button.new()
		b.text = e + (" %d" % n if n > 0 else "")
		b.flat = n == 0
		b.focus_mode = Control.FOCUS_NONE
		b.custom_minimum_size = Vector2(44, 36)
		b.add_theme_font_size_override("font_size", 13)
		var id := int(c.id)
		b.pressed.connect(func(): FX.haptic(8); _vn.react(id, e))
		FX.press(b, 0.9)
		rr.add_child(b)
	v.add_child(rr)
	return v


func _send_chat() -> void:
	var t := _chat_in.text.strip_edges()
	if t == "":
		return
	_vn.chat(t)
	_chat_in.text = ""


func _leave() -> void:
	var s := _vn.snapshot()
	VAdapter.stop(_vn)
	if s.get("is_owner", false):
		_vn.end_net()
	else:
		_vn.leave()


func _link(s: Dictionary) -> String:
	return Social.http_base() + "/j/" + str(s.get("num", ""))


func _qr(s: Dictionary) -> void:
	var v: VBoxContainer = Overlays.sheet(app, L.t("QR-код сети"), L.t("Друг наводит камеру — откроется LANTERN и вход в сеть."))
	var tr := TextureRect.new()
	tr.size_flags_horizontal = Control.SIZE_SHRINK_CENTER
	v.add_child(tr)
	QR.encode(_link(s)).show_in(tr, 220)
	FlameFX.qr_skin(tr)     # 2.0.3: «Призрачное пламя» — призрачная карточка


# ============================================================ общее

## Как играть по сети: игры ПК и Android, по нажатию — как зайти.
func _guide() -> Control:
	var v := UI.vbox(8)
	v.add_child(UI.section(L.t("Как играть по сети")))
	_fill_guide(v)
	return v


## 2.0.1: справочник ниже края экрана — строки добавляются по 4 за кадр, подробности строятся при раскрытии
## (раньше 19 строк с подробностями строились разом — 30+ мс в кадре перехода на этот экран).
func _fill_guide(v: VBoxContainer) -> void:
	var plats := [[L.t("Телефоны (Android)"), VGames.ANDROID], [L.t("Компьютер"), VGames.PC]]
	if not Cfg.mobile():
		plats.reverse()
	var n := 0
	await FX.settled(v)
	for pl in plats:
		for g in pl[1]:
			if n % 4 == 0:
				await get_tree().process_frame
				if not is_instance_valid(v) or not v.is_inside_tree():
					return
			if n == 0 or g == pl[1][0]:
				v.add_child(UI.caption(pl[0], UI.MUTED))
			n += 1
			var holder := UI.vbox(4)
			var det := UI.vbox(4)
			det.visible = false
			var row := UI.list_row(UI.icon_tile("play", UI.MUTED, 36, 18), str(g.name), VGames.way_text(g), UI.icon("chevron_down", 20, UI.DIM), func():
				if det.get_child_count() == 0:
					det.add_child(UI.para(L.t("Хозяин: ") + str(g.host), 13, UI.TEXT))
					det.add_child(UI.para(L.t("Друзья: ") + str(g.join) + ("" if g.way == "list" else L.t(" — IP хозяина из списка «Кто в сети»")), 13, UI.TEXT))
					det.add_child(UI.para(L.t("Порт: ") + str(g.port), 12, UI.DIM))
				det.visible = not det.visible
				if det.visible:
					FX.enter(det, Vector2(0, -6)), Color(0, 0, 0, 0))
			holder.add_child(row)
			holder.add_child(det)
			v.add_child(holder)


func _limits_note() -> Control:
	var t := L.t("Честно о границах: игры с античитом (Vanguard, EAC, BattlEye) и игры только с онлайн-серверами так не работают. На Android поиск игр «по Wi‑Fi» срабатывает не во всех играх — тогда вводите IP друга.")
	return _notice(t, UI.DIM)


func _notice(text: String, col: Color) -> Control:
	var p := PanelContainer.new()
	p.add_theme_stylebox_override("panel", UI._hpad(UI.box(Color(1, 1, 1, 0.04), 0, 0, Color(col, 0.5), 2), 12, 10))
	p.add_child(UI.para(text, 13, col if col != UI.DIM else UI.MUTED))
	return p


func _err_text(e: String) -> String:
	match e:
		"offline": return L.t("Нет связи с сервером LANTERN. Проверьте интернет.")
		"not_found", "net_closed", "empty", "expired": return L.t("Сеть с таким кодом не найдена или уже закрыта.")
		"need_password": return L.t("Эта сеть с паролем.")
		"bad_password": return L.t("Неверный пароль.")
		"full": return L.t("В сети нет свободных мест.")
		"kicked", "banned_by_host": return L.t("Создатель сети закрыл вам вход.")
		"host_ended": return L.t("Создатель закрыл сеть.")
		"rate": return L.t("Слишком много попыток. Подождите минуту.")
		"busy": return L.t("Серверы LANTERN сейчас перегружены. Попробуйте через пару минут.")
	return e


## Маленький график пинга (последние ~2 минуты): белая ступенчатая линия.
class PingGraph extends Control:
	var points: Array = []

	func _draw() -> void:
		draw_rect(Rect2(Vector2.ZERO, size), Color(1, 1, 1, 0.05))
		if points.size() < 2:
			return
		var mx := 50.0
		for p in points:
			mx = maxf(mx, float(p))
		var n := points.size()
		var w := size.x / float(maxi(1, n - 1))
		for i in n:
			var h := clampf(float(points[i]) / mx, 0.0, 1.0) * (size.y - 2.0)
			draw_rect(Rect2(Vector2(floor(i * w), size.y - h - 1.0), Vector2(maxf(1.0, ceil(w)), 2.0)), Color(1, 1, 1, 0.85))
