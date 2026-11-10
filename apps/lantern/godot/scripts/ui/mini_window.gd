extends Window
## ПК: маленькое полупрозрачное окошко LANTERN поверх всех окон (и поверх Minecraft в оконном режиме / без рамки).
## Всегда видно: можно ли зайти и сколько друзей в мире. Нажали Esc в Minecraft — кликните по окошку:
## код, «Копировать», игроки с «Выгнать» / «Забанить», «Закрыть комнату». На событие — раскрывается на 5 секунд.

var ctl   # RoomControl
var _root: PanelContainer
var _body: VBoxContainer
var _dot: Panel
var _status: Label
var _count: Label
var _expanded := false
var _pinned := false        # раскрыли кликом — не сворачиваем сами
var _flash_until := 0
var _key := ""
var _drag := false
var _moved := false
var _t := 0.0
const W := 300


func _ready() -> void:
	title = "LANTERN"
	borderless = true
	always_on_top = true
	unfocusable = false
	transparent = true
	transparent_bg = true
	wrap_controls = true
	theme = ctl.app.theme
	min_size = Vector2i(W, 10)
	size = Vector2i(W, 64)
	var saved: Variant = Cfg.get_value("host", "mini_pos", null)
	var scr := DisplayServer.screen_get_usable_rect(DisplayServer.window_get_current_screen())
	position = Vector2i(saved) if saved is Vector2i and scr.has_point(Vector2i(saved) + Vector2i(20, 20)) else Vector2i(scr.end.x - W - 16, scr.position.y + 16)
	_root = PanelContainer.new()
	_root.add_theme_stylebox_override("panel", UI.box(Color(UI.CARD, 0.9), 14, 12, Color(1, 1, 1, 0.08), 1))
	_root.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	add_child(_root)
	var v := UI.vbox(8)
	_root.add_child(v)
	var head := UI.hbox(8)
	head.mouse_filter = Control.MOUSE_FILTER_STOP
	head.gui_input.connect(_on_head_input)
	head.tooltip_text = "Нажмите — открыть, потяните — передвинуть"
	_dot = Panel.new()
	_dot.custom_minimum_size = Vector2(12, 12)
	_dot.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	_dot.mouse_filter = Control.MOUSE_FILTER_IGNORE
	head.add_child(_dot)
	_status = UI.label("…", 14, UI.TEXT, UI.font_bold)
	_status.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_status.clip_text = true
	_status.text_overrun_behavior = TextServer.OVERRUN_TRIM_ELLIPSIS
	_status.mouse_filter = Control.MOUSE_FILTER_IGNORE
	head.add_child(_status)
	_count = UI.label("", 14, UI.MUTED, UI.font_bold)
	_count.mouse_filter = Control.MOUSE_FILTER_IGNORE
	head.add_child(_count)
	v.add_child(head)
	_body = UI.vbox(8)
	_body.visible = false
	v.add_child(_body)
	ctl.changed.connect(_refresh)
	_refresh()


func _on_head_input(e: InputEvent) -> void:
	if e is InputEventMouseButton and e.button_index == MOUSE_BUTTON_LEFT:
		if e.pressed:
			_drag = true
			_moved = false
		else:
			_drag = false
			if _moved:
				Cfg.set_value("host", "mini_pos", position)
			else:
				_pinned = not _expanded
				_set_expanded(not _expanded)
	elif e is InputEventMouseMotion and _drag:
		if e.relative.length() > 0.5:
			_moved = true
			position += Vector2i(e.relative)


## Друг зашёл / мир не виден — раскрыть на 5 секунд и подсветить.
func flash(text: String, alert: bool) -> void:
	_flash_until = Time.get_ticks_msec() + 5000
	_set_expanded(true)
	var st := UI.box(Color(UI.CARD, 0.95), 14, 12, (UI.RED if alert else UI.JAVA), 2)
	_root.add_theme_stylebox_override("panel", st)
	_status.text = text


func _process(d: float) -> void:
	_t += d
	if _t < 0.5:
		return
	_t = 0.0
	if _flash_until > 0 and Time.get_ticks_msec() > _flash_until:
		_flash_until = 0
		_root.add_theme_stylebox_override("panel", UI.box(Color(UI.CARD, 0.9), 14, 12, Color(1, 1, 1, 0.08), 1))
		if not _pinned:
			_set_expanded(false)
		_key = ""
		_refresh()


func _set_expanded(on: bool) -> void:
	_expanded = on
	_body.visible = on
	_key = ""
	_refresh()
	size = Vector2i(W, 10)   # wrap_controls подгонит высоту под содержимое
	reset_size()


func _refresh() -> void:
	if not is_inside_tree() or not ctl.hosting():
		return
	var s: Dictionary = ctl.status()
	var peers: Array = ctl.peers()
	var col: Color = UI.JAVA if s.ok else (UI.YELLOW if s.get("wait", false) else UI.RED)
	_dot.add_theme_stylebox_override("panel", UI.box(col, 99))
	if _flash_until == 0:
		_status.text = ("Друзья могут зайти ✓" if s.ok else String(s.text))
	_count.text = "👥 %d" % peers.size()
	if not _expanded:
		return
	var key := "%s|%s|%s|%s|%s|%s|%s|%d" % [s.text, s.why, ctl.code(), ctl.is_public(), JSON.stringify(peers), ctl.num(), ctl.quality(), Social.friends.size()]
	if key == _key:
		return
	_key = key
	for c in _body.get_children():
		c.queue_free()
	if String(s.why) != "":
		_body.add_child(UI.para(String(s.why), 13, UI.YELLOW))
	var cr := UI.hbox(8)
	var cl := UI.label(("№ " + ctl.num()) if ctl.num() != "" else (ctl.code() if ctl.code() != "" else "······"), 22, UI.accent(), UI.font_black)
	cl.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	cr.add_child(cl)
	cr.add_child(UI.icon_button("copy", func():
		if ctl.share_text() != "":
			UI.copy(ctl.share_text())
			_status.text = "Приглашение скопировано", "Копировать приглашение", UI.MUTED))
	_body.add_child(cr)
	var q: String = ctl.quality()
	_body.add_child(UI.label("● " + String(RoomControl.QUALITY_TEXT.get(q, "проверяем связь…")) + ("  · код " + ctl.code() if ctl.num() != "" else ""), 12,
		{"great": UI.JAVA, "good": UI.YELLOW, "poor": UI.RED}.get(q, UI.DIM), UI.font_bold))
	_body.add_child(UI.label("Игроки" + (" · %d" % peers.size() if peers.size() > 0 else ""), 14, UI.TEXT, UI.font_bold))
	_body.add_child(UI.button("Кто может зайти: " + ("все в LANTERN" if ctl.is_public() else "только друзья по коду"), "ghost", func(): ctl.set_public(not ctl.is_public()), "globe"))
	if peers.is_empty():
		_body.add_child(UI.label("Пока никто не зашёл", 13, UI.DIM))
	for p in peers:
		var r := UI.hbox(6)
		var n := UI.label(String(p.name) + (" (%s)" % p.mc if String(p.mc) != "" else ""), 14, UI.TEXT)
		n.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		n.clip_text = true
		n.text_overrun_behavior = TextServer.OVERRUN_TRIM_ELLIPSIS
		r.add_child(n)
		var pid := String(p.peer)
		r.add_child(UI.button("Выгнать", "ghost", func(): ctl.kick(pid)))
		var more := UI.icon_button("dots_v", Callable(), "Ещё", UI.MUTED)
		more.pressed.connect(func():
			var pm := PopupMenu.new()
			pm.add_item("Забанить", 0)
			pm.id_pressed.connect(func(_i): ctl.ban(pid))
			pm.popup_hide.connect(pm.queue_free)
			add_child(pm)
			pm.popup(Rect2i(Vector2i(more.get_screen_position() + Vector2(0, more.size.y)), Vector2i(160, 0))))
		r.add_child(more)
		_body.add_child(r)
	_body.add_child(UI.label("Пригласить друзей", 14, UI.TEXT, UI.font_bold))
	var ir := UI.hbox(6)
	ir.add_child(UI.button("Скопировать ссылку", "secondary", func():
		if ctl.share_text() != "":
			UI.copy(ctl.share_text())
			_status.text = "Приглашение скопировано", "share"))
	ir.add_child(UI.button("Номер", "ghost", func():
		UI.copy(ctl.num() if ctl.num() != "" else ctl.code())
		_status.text = "Номер скопирован", "copy"))
	_body.add_child(ir)
	for f in Social.friends.filter(func(x): return x.get("online", false)).slice(0, 4):
		var fr := UI.hbox(6)
		var fl := UI.label(String(f.nick), 14, UI.TEXT)
		fl.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		fr.add_child(fl)
		var fid := int(f.id)
		fr.add_child(UI.button("Пригласить", "tonal", func(): ctl.invite(fid)))
		_body.add_child(fr)
	var br := UI.hbox(8)
	br.add_child(UI.button("Закрыть комнату", "danger", func():
		ctl.close_room(), "stop"))
	br.add_child(UI.button("Свернуть", "ghost", func():
		_pinned = false
		_set_expanded(false)))
	_body.add_child(br)
	_body.add_child(UI.para("В игре нажмите Esc — и кликните по окошку LANTERN. Не видно окошка — включите в Minecraft оконный режим (Java: F11 выкл.).", 12, UI.DIM))
