extends VBoxContainer
## 1.8 «Настройки игры»: ОДНА страница со всеми настройками — без «ещё настроек» и вложенных разделов.
## До создания игры (из «Создать игру») и для уже идущей (из «Мой мир»). Всё сохраняется сразу; если сервер работает —
## сразу и применяется (командами сервера). Что требует перезапуска — помечено, внизу появляется «Перезапустить сейчас».
## Мир, открытый из самого Minecraft: только то, чем LANTERN действительно управляет.

var app
var arg := {}

var _own := false          # мир из Minecraft (не сервер LANTERN)
var _live := false         # игра уже идёт
var _ed := "java"
var _o := {}               # настройки сервера (app.server.opts)
var _bar: PanelContainer   # липкая полоса внизу: «Создать» или «Перезапустить сейчас»
var _bar_box: BoxContainer
var _eula: CheckBox
var _notes := {}           # ключ настройки -> метка «Применится после перезапуска»
var _cap := 6
var _scroll: ScrollContainer
var _restart_key := ""

const WORLD_TYPES := [["normal", "Обычный"], ["flat", "Плоский"], ["large", "Большие биомы"]]
const RESTART_KEYS := {"version": "version", "crossplay": "crossplay", "online_mode": "online", "perf": "perf", "memory": "mem", "cap": "max"}


func _ready() -> void:
	add_theme_constant_override("separation", 0)
	_own = app.own.active or (arg is Dictionary and arg.get("own", false)) or Cfg.mobile()
	_live = app.own.active or app.server.state in ["installing", "starting", "running"]
	app.set_title(L.t("Настройки игры"), L.t("Мир из Minecraft") if _own else "")
	if not _own:
		if not _live:
			_ed = String(Cfg.get_value("host", "last_edition", "java"))
			if not _ed in ["java", "bedrock"]:
				_ed = "java"
			app.server.edition = _ed
			app.server.opts = Create.server_opts(_ed)
		_ed = app.server.edition
		_o = app.server.opts
	_cap = clampi(Cfg.game().cap, 2, 20)
	_scroll = ScrollContainer.new()
	_scroll.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	_scroll.vertical_scroll_mode = ScrollContainer.SCROLL_MODE_SHOW_NEVER if UI.compact() else ScrollContainer.SCROLL_MODE_AUTO
	_scroll.size_flags_vertical = Control.SIZE_EXPAND_FILL
	add_child(_scroll)
	var wide := not UI.compact() and get_viewport_rect().size.x >= 1000
	var page: BoxContainer = UI.hbox(20) if wide else UI.vbox(16)
	page.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	var pad := MarginContainer.new()
	pad.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	pad.add_theme_constant_override("margin_bottom", 16)
	pad.add_theme_constant_override("margin_right", 0 if UI.compact() else 10)
	pad.add_child(page)
	_scroll.add_child(pad)
	var left := UI.vbox(16)
	left.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	left.size_flags_stretch_ratio = 1.0
	page.add_child(left)
	var right := left
	if wide:
		right = UI.vbox(16)
		right.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		page.add_child(right)
	left.add_child(_sec_game())
	left.add_child(_sec_access())
	if _own:
		var hp := UI.panel(18, Color(1, 1, 1, 0.03))
		hp.add_child(UI.para(RoomControl.RULES_OWN_HINT, 14, UI.MUTED))
		left.add_child(hp)
	else:
		right.add_child(_sec_rules())
		right.add_child(_sec_world())
		(left if wide else right).add_child(_sec_speed())
	# --- липкая полоса: только там, где нужно подтвердить действие
	_bar = PanelContainer.new()
	_bar.add_theme_stylebox_override("panel", UI._hpad(UI.box(Color(UI.PANEL, 0.96), UI.R_XL, 0, UI.LINE, 1), 16, 12))
	_bar_box = UI.vbox(8) if UI.compact() else UI.hbox(14)
	_bar.add_child(_bar_box)
	add_child(_bar)
	_refresh_bar()
	if not _own:
		app.server.state_changed.connect(_on_state)
	FX.stagger(left, 0.03)


func _on_state(_s: String) -> void:
	var live: bool = app.server.state in ["installing", "starting", "running"]
	if live != _live and is_inside_tree():
		app.go("gamesettings", true, arg)
	else:
		_refresh_bar()


# ------------------------------------------------------------------ кирпичики

func _section(icon_name: String, title: String, sub := "") -> Array:
	var p := UI.panel(18)
	var v := UI.vbox(14)
	p.add_child(v)
	var h := UI.hbox(10)
	h.add_child(UI.icon_tile(icon_name, UI.accent(), 34, 18))
	var tv := UI.vbox(0)
	tv.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	tv.add_child(UI.label(title, 18, UI.TEXT, UI.font_black))
	if sub != "":
		tv.add_child(UI.label(sub, 12, UI.DIM))
	h.add_child(tv)
	v.add_child(h)
	return [p, v]


## Строка: название + одна строка пояснения, под ними — сам переключатель/поле.
func _row(title: String, desc: String, ctrl: Control = null, key := "") -> VBoxContainer:
	var v := UI.vbox(6)
	v.add_child(UI.label(title, UI.T_BODY, UI.TEXT, UI.font_bold))
	if desc != "":
		var d := UI.para(desc, UI.T_LABEL, UI.MUTED)
		d.name = "Desc"
		v.add_child(d)
	if ctrl:
		v.add_child(ctrl)
	if key != "":
		v.add_child(_note(key))
	return v


func _note(key: String) -> Label:
	var n := UI.label(L.t("Применится после перезапуска"), 12, UI.YELLOW, UI.font_bold)
	n.visible = false
	_notes[key] = n
	return n


func _switch(title: String, desc: String, on: bool, cb: Callable, key := "") -> Control:
	var r: Dictionary = UI.switch_row(title, desc, on, cb)
	(r.switch as CheckButton).custom_minimum_size.y = UI.touch_h()
	if key == "":
		return r.row
	var v := UI.vbox(4)
	v.add_child(r.row)
	v.add_child(_note(key))
	return v


## Список ников с «Добавить» и «Убрать» (свои / админы).
func _nick_list(list: String, placeholder: String, empty_text: String) -> VBoxContainer:
	var v := UI.vbox(8)
	var items := UI.vbox(4)
	v.add_child(items)
	var draw := func(self_ref: Callable):
		for c in items.get_children():
			c.queue_free()
		var names: Array = app.room.rules_get().get(list, [])
		if names.is_empty():
			items.add_child(UI.label(empty_text, 13, UI.DIM))
		for n in names:
			var r := UI.hbox(8)
			var l := UI.label(String(n), 15, UI.TEXT, UI.font_mono)
			l.size_flags_horizontal = Control.SIZE_EXPAND_FILL
			l.clip_text = true
			r.add_child(l)
			var nick := String(n)
			r.add_child(UI.button(L.t("Убрать"), "ghost", func(): app.room.list_remove(list, nick); self_ref.call(self_ref)))
			items.add_child(r)
	draw.call(draw)
	var add := UI.hbox(8)
	var e := UI.line_edit("", placeholder)
	e.max_length = 32
	e.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	add.add_child(e)
	var go := func():
		if e.text.strip_edges() == "":
			app.toast(L.t("Введите ник игрока в Minecraft"), UI.YELLOW)
			return
		if app.room.list_add(list, e.text):
			e.text = ""
			draw.call(draw)
		else:
			app.toast(L.t("Такой ник уже есть в списке"), UI.YELLOW)
	add.add_child(UI.button(L.t("Добавить"), "tonal", go, "plus"))
	e.text_submitted.connect(func(_t): go.call())
	v.add_child(add)
	return v


func _set_opt(key: String, value: Variant) -> void:
	_o[key] = value
	Cfg.set_value("host", _ed, _o)
	_refresh_bar()


# ------------------------------------------------------------------ Игра

func _sec_game() -> Control:
	var s := _section("play", L.t("Игра"))
	var v: VBoxContainer = s[1]
	var g := Cfg.game()
	var title := UI.line_edit(Create.default_title(), L.t("Например: Выживание с друзьями"))
	title.max_length = 40
	var save_title := func():
		var t := title.text.strip_edges()
		if t == String(Cfg.get_value("game", "title", "")):
			return
		Cfg.set_value("game", "title", t)
		if not _own:
			_set_opt("public_title", t)
		if _live:
			app.relay_host.set_public(app.relay_host.public_on, app.room.public_card())
			app.toast(L.t("Название обновлено"), UI.JAVA)
	title.text_submitted.connect(func(_t): save_title.call(); title.release_focus())
	title.focus_exited.connect(save_title)
	v.add_child(_row(L.t("Название"), L.t("Так игру увидят друзья."), title))
	if not _own:
		var mode := String(_o.get("gamemode", "survival"))
		var hint := UI.para(String(GameRules.GAMEMODE_HINTS.get(mode, "")), UI.T_LABEL, UI.MUTED)
		var seg := UI.segmented(GameRules.GAMEMODES, mode, func(k):
			hint.text = String(GameRules.GAMEMODE_HINTS.get(k, ""))
			Cfg.set_value("game", "mode", k if k != "adventure" else "other")
			app.room.opt_live("gamemode", k)
			if _live:
				app.relay_host.set_settings({"mode": Cfg.game().mode, "cap": _cap, "password": Cfg.game().password}))
		var mv := UI.vbox(6)
		mv.add_child(seg)
		mv.add_child(hint)
		v.add_child(UI.divider())
		v.add_child(_row(L.t("Режим"), "", mv))
		v.add_child(UI.divider())
		v.add_child(_row(L.t("Сложность"), L.t("Чем сложнее, тем сильнее монстры. «Мирная» — совсем без них."),
			UI.segmented(GameRules.DIFFS, String(_o.get("difficulty", "normal")), func(k): app.room.opt_live("difficulty", k))))
	v.add_child(UI.divider())
	if _own:
		v.add_child(_row(L.t("Сколько игроков"), L.t("До 10 — столько пускает сам Minecraft.")))
	else:
		var cr := UI.hbox(12)
		var lbl := UI.label(str(_cap), 20, UI.TEXT, UI.font_black)
		lbl.custom_minimum_size.x = 48
		lbl.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
		var set_cap := func(n: int):
			_cap = clampi(n, 2, 20)
			lbl.text = str(_cap)
			Cfg.set_value("game", "cap", _cap)
			_set_opt("max_players", _cap)
			if _live:
				app.relay_host.set_settings({"mode": Cfg.game().mode, "cap": _cap, "password": Cfg.game().password})
		var minus := UI.button("−", "secondary", func(): set_cap.call(_cap - 1))
		var plus := UI.button("+", "secondary", func(): set_cap.call(_cap + 1))
		for b in [minus, plus]:
			b.custom_minimum_size = Vector2(52, 48)
			b.add_theme_font_size_override("font_size", 22)
		cr.add_child(minus)
		cr.add_child(lbl)
		cr.add_child(plus)
		v.add_child(_row(L.t("Сколько игроков"), L.t("Вместе с вами."), cr, "cap"))
	if not _own and _ed == "java":
		v.add_child(UI.divider())
		v.add_child(_version_row())
	return s[0]


func _version_row() -> Control:
	var holder := UI.vbox(0)
	var draw := func():
		for c in holder.get_children():
			c.queue_free()
		_notes.erase("version")
		if _o.get("crossplay", true):
			holder.add_child(_row(L.t("Версия"), L.t("Новейшая — заходят с любой версии игры. С телефонами и консолями сервер всегда новейший."), null, "version"))
			return
		var vers := PackedStringArray(["latest"])
		vers.append_array(ServerHost.POPULAR)
		var cur := String(_o.get("version", "latest"))
		if not cur in vers:
			vers.append(cur)
		var names := []
		for x in vers:
			names.append(L.t("Новейшая — заходят с любой") if x == "latest" else x)
		var ob := UI.option(names, maxi(0, vers.find(cur)))
		ob.item_selected.connect(func(i): _set_opt("version", vers[i]))
		holder.add_child(_row(L.t("Версия"), L.t("Друзья зайдут с любой версии от 1.8. Мир, созданный в новой версии, в старой не откроется."), ob, "version"))
	draw.call()
	holder.set_meta("redraw", draw)
	set_meta("version_row", holder)
	return holder


# ------------------------------------------------------------------ Кто может зайти

func _sec_access() -> Control:
	var s := _section("lock", L.t("Кто может зайти"))
	var v: VBoxContainer = s[1]
	var g := Cfg.game()
	var public: bool = app.room.is_public() if _live else g.public
	var hint := UI.para("", UI.T_LABEL, UI.MUTED)
	var set_hint := func(p: bool):
		hint.text = L.t("Игра видна в списке «Игры» у всех — зайти может любой.") if p else L.t("В списке её нет. Зайдут только те, кому вы отправите код или ссылку.")
	set_hint.call(public)
	var seg := UI.segmented([["all", L.t("Все в LANTERN")], ["code", L.t("По коду")]], "all" if public else "code", func(k):
		var p: bool = k == "all"
		set_hint.call(p)
		Cfg.set_value("game", "public", p)
		Cfg.set_value("own", "public", p)
		if not _own:
			_set_opt("public", p)
		if _live:
			app.room.set_public(p))
	var av := UI.vbox(6)
	av.add_child(seg)
	av.add_child(hint)
	v.add_child(av)
	v.add_child(UI.divider())
	# пароль
	var pw := UI.line_edit(g.password, L.t("Пароль (до 32 символов)"))
	pw.max_length = 32
	pw.visible = g.password != ""
	var send_pw := func(text: String):
		Cfg.set_value("game", "password", text)
		if _live:
			app.relay_host.set_settings({"mode": Cfg.game().mode, "cap": _cap, "password": text})
	var apply_pw := func():
		var t := pw.text.strip_edges()
		if t != Cfg.game().password:
			send_pw.call(t)
			if t != "":
				app.toast(L.t("Пароль сохранён"), UI.JAVA)
	pw.text_submitted.connect(func(_t): apply_pw.call(); pw.release_focus())
	pw.focus_exited.connect(apply_pw)
	v.add_child(_switch(L.t("Пароль"), L.t("Зайдут только те, кому вы его сказали."), g.password != "", func(b):
		pw.visible = b
		if b:
			pw.grab_focus()
		else:
			pw.text = ""
			send_pw.call("")))
	v.add_child(pw)
	if not _own:
		v.add_child(UI.divider())
		var wl_box := _nick_list("wl", L.t("Ник друга в Minecraft"), L.t("Список пуст — добавьте ники друзей."))
		wl_box.visible = bool(app.room.rules_get().get("whitelist", false))
		v.add_child(_switch(L.t("Только свои"), L.t("В мир попадут только игроки из вашего списка."), wl_box.visible, func(b):
			wl_box.visible = b
			app.room.rule_set("whitelist", b)))
		v.add_child(wl_box)
		if _ed == "java":
			v.add_child(UI.divider())
			v.add_child(_switch(L.t("С телефонов и консолей тоже"), L.t("Друзья с Bedrock (телефон, планшет, консоль) зайдут в этот же мир."), bool(_o.get("crossplay", true)), func(b):
				_set_opt("crossplay", b)
				var vr: Variant = get_meta("version_row", null)
				if vr:
					(vr.get_meta("redraw") as Callable).call()
					_refresh_bar(), "crossplay"))
		v.add_child(UI.divider())
		var acc := UI.para("", UI.T_LABEL, UI.MUTED)
		var acc_text := func(on: bool):
			acc.text = L.t("Включена — каждый входит под своим аккаунтом Minecraft, чужой ник занять нельзя.") if on else L.t("Выключена — сервер не проверяет аккаунты, ник может взять кто угодно. Только для тех, кому доверяете.")
		acc_text.call(bool(_o.get("online_mode", true)))
		var ar: Dictionary = UI.switch_row(L.t("Проверка аккаунтов Minecraft"), "", bool(_o.get("online_mode", true)), func(b):
			acc_text.call(b)
			_set_opt("online_mode", b))
		var accv := UI.vbox(4)
		accv.add_child(ar.row)
		accv.add_child(acc)
		accv.add_child(_note("online_mode"))
		v.add_child(accv)
	v.add_child(UI.divider())
	v.add_child(_bans())
	return s[0]


func _bans() -> Control:
	var v := UI.vbox(6)
	v.add_child(UI.label(L.t("Забаненные"), UI.T_BODY, UI.TEXT, UI.font_bold))
	var items := UI.vbox(4)
	v.add_child(items)
	var draw := func(self_ref: Callable):
		for c in items.get_children():
			c.queue_free()
		var bans: Array = RoomControl.bans()
		if bans.is_empty():
			items.add_child(UI.para(L.t("Никого. Забанить игрока можно на экране «Мой мир»."), UI.T_LABEL, UI.MUTED))
		for b in bans:
			var r := UI.hbox(8)
			var l := UI.label(String(b.get("tag", b.get("nick", L.t("Игрок")))), 15, UI.TEXT)
			l.size_flags_horizontal = Control.SIZE_EXPAND_FILL
			l.clip_text = true
			r.add_child(l)
			var bid := int(b.id)
			r.add_child(UI.button(L.t("Пустить снова"), "ghost", func(): app.room.unban(bid); app.toast(L.t("Игрок снова может зайти"), UI.JAVA); self_ref.call(self_ref)))
			items.add_child(r)
	draw.call(draw)
	return v


# ------------------------------------------------------------------ Правила мира

func _sec_rules() -> Control:
	var s := _section("shield", L.t("Правила мира"), L.t("Меняются сразу, даже во время игры") if _live else "")
	var v: VBoxContainer = s[1]
	var r: Dictionary = app.room.rules_get()
	var first := true
	for it in GameRules.SWITCHES:
		if not first:
			v.add_child(UI.divider())
		first = false
		var k: String = it[0]
		v.add_child(_switch(String(it[1]), String(it[2]), bool(r.get(k, it[3])), func(b): app.room.rule_set(k, b)))
	v.add_child(UI.divider())
	var nick := UI.line_edit(String(Cfg.get_value("host", "mc_nick", "")), L.t("Ваш ник в Minecraft"))
	nick.max_length = 32
	var save_nick := func():
		var n := GameRules.clean_nick(nick.text)
		if n == String(Cfg.get_value("host", "mc_nick", "")):
			return
		Cfg.set_value("host", "mc_nick", n)
		if n != "" and app.server.state == "running":
			for c in GameRules.commands(_ed, "admin_add", n):
				app.server.command(c)
		app.toast(L.t("Вы — админ мира") if n != "" else L.t("Ник убран"), UI.JAVA)
	nick.text_submitted.connect(func(_t): save_nick.call(); nick.release_focus())
	nick.focus_exited.connect(save_nick)
	v.add_child(_row(L.t("Кто админ"), L.t("Админ может выгонять игроков и менять правила прямо в игре. Впишите свой ник — станете админом."), nick))
	v.add_child(_row(L.t("Ещё админы"), "", _nick_list("admins", L.t("Ник игрока в Minecraft"), L.t("Только вы."))))
	return s[0]


# ------------------------------------------------------------------ Мир

func _sec_world() -> Control:
	var s := _section("globe", L.t("Мир"))
	var v: VBoxContainer = s[1]
	var exists: bool = app.server.world_exists()
	if exists or _live:
		var props := ServerHost.read_properties(app.server.server_dir().path_join("server.properties"))
		var lt := String(props.get("level-type", "")).replace("\\", "").to_lower()
		var tname := L.t("Плоский") if lt.contains("flat") else (L.t("Большие биомы") if lt.contains("large") else L.t("Обычный"))
		var seed := String(props.get("level-seed", ""))
		v.add_child(_row(L.t("Тип мира и зерно"), L.t("%s · зерно: %s. Мир уже создан — тип и зерно меняются только у нового мира.") % [L.t(tname), seed if seed != "" else L.t("случайное")]))
		if not _live:
			v.add_child(UI.button(L.t("Начать новый мир"), "secondary", _new_world, "plus"))
	else:
		v.add_child(_row(L.t("Тип мира"), L.t("Обычный — горы, леса и пещеры. Плоский — ровная площадка для строек."),
			UI.segmented(WORLD_TYPES, String(_o.get("world_type", "normal")), func(k): _set_opt("world_type", k))))
		v.add_child(UI.divider())
		var seed_e := UI.line_edit(String(_o.get("seed", "")), L.t("Пусто — случайный мир"))
		seed_e.max_length = 32
		seed_e.text_changed.connect(func(t): _set_opt("seed", t.strip_edges()))
		v.add_child(_row(L.t("Зерно (seed)"), L.t("Одинаковое зерно — одинаковый мир. Выбирается один раз, при создании."), seed_e))
		v.add_child(UI.divider())
		v.add_child(_row(L.t("Для кого сервер"), L.t("«Для всех» — ПК, телефоны и консоли в одном мире. «Только Bedrock» — официальный сервер для телефонов и консолей."),
			UI.segmented([["java", L.t("Для всех")], ["bedrock", L.t("Только Bedrock")]], _ed, func(k):
				Cfg.set_value("host", "last_edition", k)
				app.go("gamesettings", true, arg))))
	v.add_child(UI.divider())
	if exists:
		var bb := UI.button(L.t("Сделать копию"), "tonal", Callable(), "download")
		bb.pressed.connect(func():
			bb.disabled = true
			bb.text = L.t("Сохраняем…")
			var p: String = await app.server.backup()
			if not is_instance_valid(bb):
				return
			bb.text = L.t("Сделать копию")
			bb.disabled = false
			app.toast(L.t("Копия сохранена: %s — она в «Копиях миров»") % p.get_file() if p != "" else L.t("Не получилось: %s") % app.server.last_error, UI.JAVA if p != "" else UI.RED))
		v.add_child(_row(L.t("Резервная копия сейчас"), L.t("Сохранит мир в файл. Вернуть его можно в «Копиях миров»."), bb))
		v.add_child(UI.divider())
	var ab := 0
	var cur := int(app.room.rules_get().get("autobackup", 0))
	var names := []
	for i in GameRules.AUTOBACKUP.size():
		names.append(GameRules.AUTOBACKUP[i][1])
		if int(GameRules.AUTOBACKUP[i][0]) == cur:
			ab = i
	var ob := UI.option(names, ab)
	ob.item_selected.connect(func(i): app.room.rule_set("autobackup", int(GameRules.AUTOBACKUP[i][0])))
	v.add_child(_row(L.t("Автокопия"), L.t("Пока игра идёт, LANTERN сам сохраняет копию мира. Хранятся 5 последних."), ob))
	if exists and not Cfg.mobile():
		v.add_child(UI.divider())
		v.add_child(_row(L.t("Папка мира"), L.t("Файлы мира на этом компьютере."), UI.button(L.t("Открыть папку мира"), "secondary", func(): UI.open(ProjectSettings.globalize_path(app.server.world_path())), "folder")))
	return s[0]


func _new_world() -> void:
	var v: VBoxContainer = Overlays.sheet(app, L.t("Начать новый мир?"), L.t("Нынешний мир никуда не денется: он останется в папке сервера и в «Копиях миров». Игра начнётся в новом, пустом мире."))
	v.add_child(UI.button(L.t("Да, новый мир"), "primary", func():
		Overlays.close_sheet(app)
		_o["world"] = Create.free_world_name(app.server, Create.default_title())
		_o.erase("seed")
		_o.erase("world_type")
		Cfg.set_value("host", _ed, _o)
		app.go("gamesettings", true, arg), "plus"))
	v.add_child(UI.button(L.t("Оставить как есть"), "ghost", func(): Overlays.close_sheet(app)))


# ------------------------------------------------------------------ Скорость игры

func _sec_speed() -> Control:
	var s := _section("bolt", L.t("Скорость игры"))
	var v: VBoxContainer = s[1]
	var mc := Tuning.machine()
	var cur := String(_o.get("perf", "balance"))
	var hint := UI.para("", UI.T_LABEL, UI.MUTED)
	var auto := UI.para("", UI.T_LABEL, UI.YELLOW)
	var upd := func(k: String):
		hint.text = String(Tuning.HINTS.get(k, ""))
		auto.visible = Tuning.effective(_o, mc) != k
		auto.text = L.t("Этот компьютер не самый мощный — LANTERN сам включит «Быстро», чтобы игра не лагала.")
	var seg := UI.segmented(Tuning.NAMES, cur, func(k):
		_o["perf_manual"] = k != "balance"
		_set_opt("perf", k)
		upd.call(k))
	upd.call(cur)
	var pv := UI.vbox(6)
	pv.add_child(seg)
	pv.add_child(hint)
	pv.add_child(auto)
	pv.add_child(_note("perf"))
	v.add_child(pv)
	if _ed == "java":
		v.add_child(UI.divider())
		var total := int(mc.total_mb)
		var vals := [0]
		var names := [L.t("Авто — %s ГБ") % _gb(Tuning.auto_memory(mc))]
		for m in [2048, 3072, 4096, 6144, 8192, 12288]:
			if total <= 0 or m <= total / 2:
				vals.append(m)
				names.append(L.t("%s ГБ") % _gb(m))
		var sel := 0 if _o.get("memory_auto", true) else maxi(0, vals.find(int(_o.get("memory_mb", 0))))
		var ob := UI.option(names, sel)
		ob.item_selected.connect(func(i):
			_o["memory_auto"] = vals[i] == 0
			_set_opt("memory_mb", int(vals[i])))
		v.add_child(_row(L.t("Память для сервера"), L.t("«Авто» — LANTERN сам подберёт под ваш компьютер."), ob, "memory"))
	return s[0]


static func _gb(mb: int) -> String:
	return ("%.1f" % (mb / 1024.0)).trim_suffix(".0")


# ------------------------------------------------------------------ липкая полоса

func _refresh_bar() -> void:
	if not is_instance_valid(_bar):
		return
	var need: Array = [] if _own else app.server.restart_needed()
	for k in _notes:
		if is_instance_valid(_notes[k]):
			(_notes[k] as Label).visible = String(RESTART_KEYS.get(k, "")) in need
	var key := "%s|%s" % [_live, not need.is_empty()]
	if key == _restart_key:
		return
	_restart_key = key
	for c in _bar_box.get_children():
		c.queue_free()
	_eula = null
	if not _live and not _own:
		_eula = Create.eula_box(_ed)
		_eula.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		_bar_box.add_child(_eula)
		var b := UI.button(L.t("Создать"), "primary", func(): Create.launch_server(app, _ed, _eula), "play")
		b.custom_minimum_size = Vector2(180, 52)
		_bar_box.add_child(b)
		_bar.visible = true
	elif not _live and _own:
		var b := UI.button(L.t("Готово"), "primary", func(): app.back(), "check")
		b.custom_minimum_size.y = 52
		b.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		_bar_box.add_child(b)
		_bar.visible = true
	elif not need.is_empty():
		var l := UI.para(L.t("Часть настроек применится после перезапуска. Игроки переподключатся примерно через 30 секунд."), 13, UI.YELLOW)
		l.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		_bar_box.add_child(l)
		var b := UI.button(L.t("Перезапустить сейчас"), "primary", func():
			app.toast(L.t("Перезапускаем сервер — мир сохранится"), UI.YELLOW)
			app.server.restart()
			app.go("host", true), "refresh")
		b.custom_minimum_size.y = 48
		_bar_box.add_child(b)
		_bar.visible = true
		FX.enter(_bar, Vector2(0, 16))
	else:
		_bar.visible = false
