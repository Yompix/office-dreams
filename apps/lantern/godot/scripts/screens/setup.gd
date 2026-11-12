extends VBoxContainer
## «Создать мир»: сначала способ — «Мой мир из Minecraft» (мир, открытый в игре; на телефоне — только он)
## или «Отдельный сервер» (ПК: мастер из 3 шагов — издание → мир → запуск). Потом — экран мира с кодом (host.gd).

var app
var arg := {}

var edition := "java"
var kind := ""              # own — мир из Minecraft, server — отдельный сервер
var _own_page: Control
var _main_parts: Array[Control] = []
var step := 1
var _name: LineEdit
var _version: OptionButton
var _versions: PackedStringArray = []
var _ver_hint: Label
var _mode_i := 0
var _mode_seg: Control
var _diff: OptionButton
var _players: SpinBox
var _online := true
var _cross: CheckButton
var _internet: CheckButton
var _mem: HSlider
var _mem_lbl: Label
var _wl: CheckButton
var _eula: CheckBox
var _public: CheckButton
var _ptitle: LineEdit
var _ptags := {}
var _start: Button
var _sum: Label
var _next: Button
var _prev: Button
var _steps_box: VBoxContainer
var _pages: Array[VBoxContainer] = []
var _saved := {}

const MODES := [["survival", "Выживание"], ["creative", "Творческий"], ["adventure", "Приключение"]]
const DIFFS := [["peaceful", "Мирная"], ["easy", "Лёгкая"], ["normal", "Нормальная"], ["hard", "Сложная"]]
const STEP_NAMES := ["Способ", "Мир", "Запуск"]


func _ready() -> void:
	add_theme_constant_override("separation", 20)
	edition = arg.get("edition", Cfg.get_value("host", "last_edition", "java")) if arg is Dictionary else "java"
	step = int(arg.get("step", 2 if arg is Dictionary and arg.has("edition") else 1)) if arg is Dictionary else 1
	if arg is Dictionary and arg.has("edition"):
		kind = "server"
	if Cfg.mobile():
		step = 1
		kind = ""
	_steps_box = UI.vbox(0)
	add_child(_steps_box)
	var holder := UI.vbox(0)
	holder.size_flags_vertical = Control.SIZE_EXPAND_FILL
	add_child(holder)
	for i in 3:
		var pv := UI.vbox(16)
		pv.size_flags_vertical = Control.SIZE_EXPAND_FILL
		holder.add_child(pv)
		_pages.append(pv)
	var nav := UI.hbox(12)
	_prev = UI.button("Назад", "secondary", func(): _goto(step - 1))
	_prev.custom_minimum_size.x = 120
	nav.add_child(_prev)
	_next = UI.button("Дальше", "primary", func(): _goto(step + 1), "")
	_next.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_next.custom_minimum_size.y = 52
	nav.add_child(_next)
	_start = UI.button("Запустить мир", "primary", _go, "play")
	_start.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_start.custom_minimum_size.y = 56
	_start.add_theme_font_size_override("font_size", 17)
	nav.add_child(_start)
	add_child(nav)
	_main_parts = [_steps_box, holder, nav]
	_build_pages()
	_goto(step, false)
	if arg is Dictionary and arg.get("own", false):
		_show_own(true)


func _build_pages() -> void:
	_saved = Cfg.get_value("host", edition, {})
	for p in _pages:
		for c in p.get_children():
			p.remove_child(c)
			c.queue_free()
	_ptags.clear()
	_page_edition(_pages[0])
	_page_world(_pages[1])
	_page_launch(_pages[2])
	_update()


func _goto(n: int, animate := true) -> void:
	n = clampi(n, 1, 3)
	var prev := step
	step = n
	for c in _steps_box.get_children():
		c.queue_free()
	_steps_box.add_child(UI.steps(step, 3, STEP_NAMES, prev if animate else step))
	for i in 3:
		_pages[i].visible = i == step - 1
	if animate and prev != step:
		var dir := 1 if step > prev else -1
		FX.enter(_pages[step - 1], Vector2(48 * dir, 0), 0.0, 0.3)
	_prev.visible = step > 1
	_next.visible = step < 3 and (step > 1 or kind == "server")
	_start.visible = step == 3
	app.set_back((func(): _goto(step - 1)) if step > 1 else Callable())
	app.set_title("Создать мир")
	var sc := get_parent()
	if sc is ScrollContainer:
		(sc as ScrollContainer).scroll_vertical = 0


# ------------------------------------------------------------------ шаг 1: способ (и издание для сервера)
func _page_edition(p: VBoxContainer) -> void:
	var mobile := Cfg.mobile()
	p.add_child(UI.h2("Как открыть мир друзьям?"))
	p.add_child(UI.choice("play", UI.accent(), "Мой мир из Minecraft",
		"Быстро и без скачивания: откройте мир в игре — LANTERN даст код." + (" На телефоне — единственный вариант." if mobile else ""),
		kind == "own", _pick_kind.bind("own")))
	var srv := UI.choice("server", UI.MUTED if mobile else UI.JAVA, "Отдельный сервер",
		"Только на компьютере. Мир работает, даже когда вы не в игре." if not mobile else "Только на компьютере: установите LANTERN на ПК. Мир работает, даже когда вы не в игре.",
		kind == "server", _pick_kind.bind("server"))
	if mobile:
		srv.modulate.a = 0.55
	p.add_child(srv)
	if kind != "server" or mobile:
		return
	var sub := UI.vbox(12)
	sub.add_child(UI.gap(4))
	sub.add_child(UI.h3("На чём играют друзья?"))
	sub.add_child(UI.choice("java", UI.JAVA, "Java Edition", "Minecraft на компьютере. Друзья с телефона тоже смогут зайти.", edition == "java", _pick.bind("java")))
	sub.add_child(UI.choice("phone", UI.BEDROCK, "Bedrock Edition", "Телефон, планшет, Windows 10/11, Xbox, PlayStation, Switch.", edition == "bedrock", _pick.bind("bedrock")))
	var tip := UI.panel(16, Color(1, 1, 1, 0.03))
	var tr := UI.hbox(12)
	tip.add_child(tr)
	var ic := UI.icon("info", 22, UI.MUTED)
	ic.size_flags_vertical = Control.SIZE_SHRINK_BEGIN
	tr.add_child(ic)
	var tl := UI.para("Не знаете, что у друзей? Если в меню игры есть «Сетевая игра» — это Java. Если «Играть → Друзья» — Bedrock.", 14, UI.MUTED)
	tl.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	tr.add_child(tl)
	sub.add_child(tip)
	p.add_child(sub)
	FX.stagger(sub, 0.04)


func _pick_kind(k: String) -> void:
	if k == "server" and Cfg.mobile():
		app.toast("Отдельный сервер запускается только на компьютере — установите LANTERN на ПК", UI.YELLOW)
		return
	kind = k
	if k == "own":
		_show_own(true)
		return
	for c in _pages[0].get_children():
		_pages[0].remove_child(c)
		c.queue_free()
	_page_edition(_pages[0])
	_goto(1, false)


## «Мой мир из Minecraft»: вместо шагов мастера — поиск мира в игре (own_page.gd).
func _show_own(on: bool) -> void:
	for c in _main_parts:
		c.visible = not on
	if is_instance_valid(_own_page):
		_own_page.queue_free()
		_own_page = null
	if on:
		kind = "own"
		_own_page = preload("res://scripts/screens/own_page.gd").new()
		_own_page.set("app", app)
		_own_page.set("auto", arg is Dictionary and arg.get("auto", false))
		_own_page.set("launch", arg is Dictionary and arg.get("launch", false))
		_own_page.size_flags_vertical = Control.SIZE_EXPAND_FILL
		add_child(_own_page)
		FX.enter(_own_page, Vector2(48, 0), 0.0, 0.3)
		app.set_back(func(): _show_own(false))
		app.set_title("Мой мир" if Cfg.mobile() or UI.compact() else "Мой мир из Minecraft")
	else:
		kind = ""
		_pick_kind_refresh()
		_goto(1, false)


func _pick_kind_refresh() -> void:
	for c in _pages[0].get_children():
		_pages[0].remove_child(c)
		c.queue_free()
	_page_edition(_pages[0])


func _pick(ed: String) -> void:
	if ed == edition:
		_goto(2)
		return
	edition = ed
	Cfg.set_value("host", "last_edition", ed)
	_build_pages()
	_goto(2)


# ------------------------------------------------------------------ шаг 2: мир
func _page_world(p: VBoxContainer) -> void:
	var color := UI.JAVA if edition == "java" else UI.BEDROCK
	var head := UI.hbox(12)
	head.add_child(UI.icon_tile("java" if edition == "java" else "phone", color, 44, 22))
	var hv := UI.vbox(0)
	hv.add_child(UI.h2("Ваш мир"))
	hv.add_child(UI.label("Java Edition" if edition == "java" else "Bedrock Edition", 13, color.lightened(0.2), UI.font_bold))
	head.add_child(hv)
	p.add_child(head)
	var card := UI.panel(20)
	var v := UI.vbox(18)
	card.add_child(v)
	p.add_child(card)
	_name = UI.line_edit(_saved.get("world", "Наш мир"), "Как назвать мир")
	_name.max_length = 32
	_name.add_theme_font_override("font", UI.font_bold)
	_name.text_changed.connect(func(_t): _update())
	v.add_child(UI.field("Название мира", _name))
	_mode_i = _idx(MODES, _saved.get("gamemode", "survival"))
	_mode_seg = UI.segmented(MODES.map(func(x): return [x[0], x[1]]), MODES[_mode_i][0], func(k):
		_mode_i = _idx(MODES, k)
		UI.segmented_select(_mode_seg, k)
		_update())
	v.add_child(UI.field("Режим игры", _mode_seg))
	_diff = UI.option(DIFFS.map(func(x): return x[1]), _idx(DIFFS, _saved.get("difficulty", "normal")))
	_diff.item_selected.connect(func(_i): _update())
	v.add_child(UI.field("Сложность", _diff))
	if edition == "java":
		var lic := UI.vbox(10)
		lic.add_child(UI.label("Проверка аккаунтов", 13, UI.MUTED, UI.font_bold))
		_online = _saved.get("online_mode", true)
		var lic_box := UI.vbox(10)
		lic.add_child(lic_box)
		var draw_lic := func(self_ref: Callable):
			for c in lic_box.get_children():
				c.queue_free()
			lic_box.add_child(UI.choice("lock", UI.accent(), "Включена (рекомендуется)", "Вход только с аккаунтом Microsoft — никто не зайдёт под чужим ником.", _online, func(): _online = true; self_ref.call(self_ref); _update()))
			lic_box.add_child(UI.choice("user", UI.YELLOW, "Выключена", "Сервер не проверяет аккаунты (online-mode=false): любой может взять любой ник. Только для тех, кому доверяете.", not _online, func(): _online = false; self_ref.call(self_ref); _update()))
		draw_lic.call(draw_lic)
		v.add_child(lic)
		var cr := UI.switch_row("Пустить друзей с телефона", "Друзья с Bedrock (телефон, консоль) зайдут в этот же мир по тому же коду.", _saved.get("crossplay", true), func(_b): _update())
		_cross = cr.switch
		v.add_child(UI.divider())
		v.add_child(cr.row)
		# версия сервера: друзьям она не важна — ViaVersion пускает Java любых версий (1.8 – новейшая)
		v.add_child(UI.divider())
		_version = UI.option(["Новейшая (рекомендуется)"])
		_ver_hint = UI.para("", 12, UI.DIM)
		var vf := UI.field("Версия сервера", _version)
		vf.add_child(_ver_hint)
		v.add_child(vf)
		_version.item_selected.connect(func(_i): _update())
		_load_versions(_saved.get("version", "latest"))


# ------------------------------------------------------------------ шаг 3: запуск
func _page_launch(p: VBoxContainer) -> void:
	p.add_child(UI.h2("Всё готово к запуску"))
	var sp := UI.panel(18)
	_sum = UI.para("", 15, UI.TEXT)
	sp.add_child(_sum)
	p.add_child(sp)
	var opts := UI.panel(18)
	var ov := UI.vbox(14)
	opts.add_child(ov)
	var ir := UI.switch_row("Друзья через интернет", "Друзья вводят короткий код — и мир появляется у них в игре. Без VPN и настройки роутера.", _saved.get("internet", true), func(_b): _update())
	_internet = ir.switch
	ov.add_child(ir.row)
	ov.add_child(UI.divider())
	var pr := UI.switch_row("Открытый мир", "Виден всем в «Открытых мирах». Без этого зайти можно только по коду.", _saved.get("public", false))
	_public = pr.switch
	ov.add_child(pr.row)
	var pub_box := UI.vbox(10)
	_ptitle = UI.line_edit(_saved.get("public_title", ""), "Название для списка, например «Выживание с нуля»")
	_ptitle.max_length = 40
	pub_box.add_child(_ptitle)
	var tg := UI.flow(8)
	for t in ["выживание", "творчество", "pvp", "мини-игры", "новоград", "приключения"]:
		var on: bool = t in _saved.get("tags", [])
		var cb := UI.chip("#" + t, on)
		cb.toggle_mode = true
		cb.button_pressed = on
		cb.toggled.connect(func(b): UI.chip_set(cb, b))
		_ptags[t] = cb
		tg.add_child(cb)
	pub_box.add_child(tg)
	pub_box.add_child(UI.para("Не пишите в названии личные данные.", 12, UI.DIM))
	pub_box.visible = _public.button_pressed
	_public.toggled.connect(func(b): pub_box.visible = b; _update())
	ov.add_child(pub_box)
	p.add_child(opts)
	# дополнительно (свёрнуто)
	var adv := UI.panel(18)
	var av := UI.vbox(14)
	adv.add_child(av)
	var ah := UI.button("Дополнительно", "ghost", Callable(), "chevron_down")
	ah.alignment = HORIZONTAL_ALIGNMENT_LEFT
	ah.icon_alignment = HORIZONTAL_ALIGNMENT_RIGHT
	ah.add_theme_color_override("font_color", UI.TEXT)
	av.add_child(ah)
	var body := UI.vbox(16)
	body.visible = false
	av.add_child(body)
	ah.pressed.connect(func(): body.visible = not body.visible; if body.visible: FX.stagger(body, 0.03))
	_players = SpinBox.new()
	_players.min_value = 2
	_players.max_value = 50
	_players.value = _saved.get("max_players", 10)
	_players.custom_minimum_size.y = UI.touch_h()
	body.add_child(UI.field("Сколько игроков максимум", _players))
	if edition == "java":
		var total := int(OS.get_memory_info().get("physical", 8 << 30) / (1 << 20))
		_mem = HSlider.new()
		_mem.min_value = 1024
		_mem.max_value = clampi(total / 2, 2048, 16384)
		_mem.step = 512
		_mem.value = clampi(int(_saved.get("memory_mb", 2048 if total < 8000 else 3072)), 1024, int(_mem.max_value))
		_mem.custom_minimum_size.y = 32
		_mem_lbl = UI.label("", 15, UI.TEXT, UI.font_bold)
		_mem.value_changed.connect(func(val): _mem_lbl.text = "%.1f ГБ памяти для сервера" % (val / 1024.0))
		_mem.value_changed.emit(_mem.value)
		var mv := UI.vbox(6)
		mv.add_child(_mem_lbl)
		mv.add_child(_mem)
		body.add_child(UI.field("Память", mv, "Больше друзей и модов — больше памяти"))
		var wr := UI.switch_row("Только для своих (белый список)", "Зайдут только те, кого вы впустите. Когда друг попробует зайти, на экране мира появится кнопка «Впустить». Или команда: whitelist add Ник.", _saved.get("whitelist", false), func(_b): _update())
		_wl = wr.switch
		body.add_child(wr.row)
	else:
		body.add_child(UI.field("Версия", UI.label("Последняя (обновляется сама)", 15, UI.TEXT, UI.font_bold)))
	p.add_child(adv)
	# соглашение
	var legal := UI.panel(18, Color(1, 1, 1, 0.03))
	var lv := UI.vbox(8)
	legal.add_child(lv)
	_eula = CheckBox.new()
	_eula.text = "Я принимаю Лицензионное соглашение Minecraft" + ("" if edition == "java" else " и Политику конфиденциальности")
	_eula.button_pressed = _saved.get("eula", false)
	_eula.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	_eula.custom_minimum_size.x = 200
	_eula.toggled.connect(func(_b): _update())
	lv.add_child(_eula)
	var links := UI.flow(4)
	links.add_child(UI.button("Прочитать EULA", "link", func(): OS.shell_open("https://aka.ms/MinecraftEULA")))
	if edition == "bedrock":
		links.add_child(UI.button("Конфиденциальность", "link", func(): OS.shell_open("https://go.microsoft.com/fwlink/?LinkId=521839")))
	lv.add_child(links)
	lv.add_child(UI.para("Без согласия Mojang не разрешает запускать сервер. Мы ничего не отправляем о вас.", 12, UI.DIM))
	p.add_child(legal)


static func _idx(list: Array, key: String) -> int:
	for i in list.size():
		if list[i][0] == key:
			return i
	return 0


func _load_versions(want: String) -> void:
	var r: Dictionary = await Net.get_json(ServerHost.FILL)
	if not is_inside_tree() or not r.ok or not is_instance_valid(_version):
		return
	var all := ServerHost.paper_versions(r.data)
	# новейшая, потом популярные (под них моды и сборки), потом остальные
	_versions = PackedStringArray(["latest"])
	_version.clear()
	_version.add_item("Новейшая — %s (рекомендуется)" % (all[0] if all.size() > 0 else "?"))
	for v in ServerHost.POPULAR:
		if v in all and v != all[0]:
			_versions.append(v)
			_version.add_item(v)
	_version.add_separator("Другие версии")
	_versions.append("")
	for v in all.slice(1, 60):
		if not v in ServerHost.POPULAR:
			_versions.append(v)
			_version.add_item(v)
	var i := _versions.find(want)
	_version.select(i if i > 0 else 0)
	_update()


func _opts() -> Dictionary:
	var o := {
		"world": _name.text.strip_edges() if _name.text.strip_edges() != "" else "Наш мир",
		"gamemode": MODES[_mode_i][0],
		"difficulty": DIFFS[_diff.selected][0],
		"max_players": int(_players.value),
		"internet": _internet.button_pressed,
		"eula": _eula.button_pressed,
		"public": _public.button_pressed and _internet.button_pressed,
		"public_title": _ptitle.text.strip_edges(),
		"tags": _ptags.keys().filter(func(k): return _ptags[k].button_pressed),
	}
	if edition == "java":
		var vi := _version.selected
		o["version"] = _versions[vi] if vi > 0 and vi < _versions.size() and _versions[vi] != "" else "latest"
		o["online_mode"] = _online
		o["crossplay"] = _cross.button_pressed
		o["memory_mb"] = int(_mem.value)
		o["whitelist"] = _wl.button_pressed
		o["whitelist_nick"] = String(Social.me.get("nick", "")) if Social.online() else ""
	return o


func _update() -> void:
	if _sum == null or _eula == null:
		return
	var o := _opts()
	if edition == "java" and _ver_hint:
		if o.version == "latest":
			_ver_hint.text = "Друзья зайдут с любой версии Java — от 1.8 до новейшей." + (" Телефоны — с актуальной версии Minecraft." if o.crossplay else "")
		elif o.crossplay:
			_ver_hint.text = "Для телефонов нужен новый сервер — поставим новейший. Старые версии Java (и %s) всё равно смогут зайти." % o.version
		else:
			_ver_hint.text = "Сервер %s (Java %d скачаем сами). Зайти можно и с других версий Java — LANTERN поставит ViaVersion." % [o.version, ServerHost.java_for(o.version)]
	var lines := PackedStringArray()
	lines.append("•  Мир «%s» — %s, сложность %s" % [o.world, MODES[_mode_i][1].to_lower(), DIFFS[_diff.selected][1].to_lower()])
	if edition == "java":
		lines.append("•  Скачаем сервер Paper %s и, если нужно, Java — один раз" % ("новейший" if o.version == "latest" or o.crossplay else o.version))
		lines.append("•  Друзья зайдут с любой версии Java (ViaVersion)")
		lines.append("•  Вход: " + ("с проверкой аккаунта Microsoft" if o.online_mode else "без проверки аккаунтов"))
		if o.crossplay:
			lines.append("•  Друзья с телефона тоже зайдут (последняя версия)")
		if o.whitelist:
			lines.append("•  Белый список: зайдут только те, кого вы впустите")
	else:
		lines.append("•  Скачаем официальный сервер Bedrock — один раз")
	lines.append("•  " + ("Получите код комнаты для друзей" if o.internet else "Только для друзей в вашей Wi-Fi сети"))
	_sum.text = "\n".join(lines)
	if _start:
		_start.disabled = not o.eula
		_start.tooltip_text = "" if o.eula else "Сначала примите соглашение Minecraft"


func _go() -> void:
	var o := _opts()
	if not o.eula:
		app.toast("Отметьте согласие с соглашением Minecraft", UI.YELLOW)
		return
	Cfg.set_value("host", edition, o)
	app.own.stop()
	app.server.host(edition, o)
	app.go("host", false, {"fresh": true})
