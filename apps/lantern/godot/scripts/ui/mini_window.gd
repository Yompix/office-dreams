extends Window
## ПК: маленькое полупрозрачное окошко LANTERN поверх всех окон (и поверх Minecraft в оконном режиме / без рамки).
## 1.7: это НАСТОЯЩЕЕ окно системы (force_native), а не окно внутри LANTERN: оно видно, когда LANTERN свёрнут
## или за игрой; не связано с главным окном (transient = false — не сворачивается вместе с ним); когда всплывает
## само — фокус у игры не забирает (unfocusable), фокус берёт только после клика по нему.
## Всегда видно: можно ли зайти и сколько друзей в мире. Нажали Esc в Minecraft — кликните по окошку:
## код, «Копировать», игроки с «Выгнать» / «Забанить», «Защита мира», «Закрыть комнату».

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
var _more_peer := ""
var _k := 1.0               # масштаб под плотность экрана (Windows 125–200 %)
var _log_t := 0.0
const W := 312


func _init() -> void:
	# до входа в дерево: иначе Godot встроит окно в главное (display/window/subwindows/embed_subwindows)
	visible = false
	force_native = true
	transient = false
	exclusive = false
	borderless = true
	always_on_top = true
	unfocusable = true
	transparent = true
	transparent_bg = true
	unresizable = true
	wrap_controls = true
	initial_position = Window.WINDOW_INITIAL_POSITION_ABSOLUTE
	title = "LANTERN — мир"
	visible = true


func _ready() -> void:
	theme = ctl.app.theme
	var scr_i := DisplayServer.window_get_current_screen()
	var dpi := DisplayServer.screen_get_dpi(scr_i)
	_k = clampf(snappedf(dpi / 96.0, 0.25), 1.0, 2.5) if OS.get_name() == "Windows" and dpi > 0 else 1.0
	content_scale_factor = _k
	min_size = Vector2i(int(W * _k), 10)
	size = Vector2i(int(W * _k), int(64 * _k))
	var saved: Variant = Cfg.get_value("host", "mini_pos", Vector2i(-99999, -99999))
	var scr := DisplayServer.screen_get_usable_rect(scr_i)
	position = Vector2i(saved) if saved is Vector2i and scr.has_point(Vector2i(saved) + Vector2i(20, 20)) else Vector2i(scr.end.x - int(W * _k) - 16, scr.position.y + 16)
	_root = PanelContainer.new()
	_root.add_theme_stylebox_override("panel", _panel_style(Color(1, 1, 1, 0.12)))
	_root.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_root.custom_minimum_size.x = W
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
	_count = UI.label("", 13, UI.MUTED, UI.font_bold)
	_count.mouse_filter = Control.MOUSE_FILTER_IGNORE
	head.add_child(_count)
	v.add_child(head)
	_body = UI.vbox(8)
	_body.visible = false
	v.add_child(_body)
	ctl.changed.connect(_refresh)
	_refresh()
	_fit()
	AppLog.add("mini", "окошко поверх игры: настоящее окно=%s, поверх всех=%s, без фокуса=%s, экран %s, позиция %s, масштаб %.2f" % [
		not is_embedded(), always_on_top, unfocusable, DisplayServer.screen_get_size(scr_i), position, _k])


static func _panel_style(border: Color, bw := 1) -> StyleBoxFlat:
	return UI.box(Color(0.075, 0.085, 0.13, 0.94), 18, 12, border, bw)


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
			position += Vector2i(e.relative * _k)


## Друг зашёл / мир не виден — раскрыть на 5 секунд и подсветить (фокус у игры не забираем).
func flash(text: String, alert: bool) -> void:
	_flash_until = Time.get_ticks_msec() + 5000
	_set_expanded(true)
	_root.add_theme_stylebox_override("panel", _panel_style(UI.RED if alert else UI.AMBER, 2))
	_status.text = text
	if not visible:
		show()


func _process(d: float) -> void:
	_t += d
	_log_t += d
	if _t < 0.5:
		return
	_t = 0.0
	if _flash_until > 0 and Time.get_ticks_msec() > _flash_until:
		_flash_until = 0
		_root.add_theme_stylebox_override("panel", _panel_style(Color(1, 1, 1, 0.12)))
		if not _pinned:
			_set_expanded(false)
		_key = ""
		_refresh()
	# главное окно свернули — окошко остаётся; если система всё же спрятала его вместе с главным, показываем снова
	if not visible:
		show()
	if mode == Window.MODE_MINIMIZED:
		mode = Window.MODE_WINDOWED
	if _log_t > 60.0:
		_log_t = 0.0
		AppLog.add("mini", "окошко: видно=%s, главное окно свёрнуто=%s, позиция %s" % [visible, get_tree().root.mode == Window.MODE_MINIMIZED, position])


func _set_expanded(on: bool) -> void:
	_expanded = on
	_body.visible = on
	# раскрыли кликом — окно берёт фокус (кнопки, прокрутка); свернули или всплыло само — фокус остаётся у игры
	var nofocus := not (on and _pinned)
	if unfocusable != nofocus:
		unfocusable = nofocus
	_key = ""
	_refresh()
	_fit()


func _fit() -> void:
	size = Vector2i(int(W * _k), 10)   # wrap_controls подгонит высоту под содержимое
	reset_size()


func _refresh() -> void:
	if not is_inside_tree():
		return
	var s: Dictionary = ctl.status()
	var peers: Array = ctl.peers()
	var col: Color = UI.JAVA if s.ok else (UI.YELLOW if s.get("wait", false) else UI.RED)
	_dot.add_theme_stylebox_override("panel", UI.box(col, 99))
	if _flash_until == 0:
		_status.text = ("Друзья могут зайти ✓" if s.ok else String(s.text))
	_count.text = "%d в мире" % peers.size() if peers.size() > 0 else ""
	if not _expanded:
		return
	var key := "%s|%s|%s|%s|%s|%s|%s|%d|%s|%s|%s" % [s.text, s.why, ctl.code(), ctl.is_public(), JSON.stringify(peers), ctl.num(), ctl.quality(), Social.friends.size(), _more_peer, JSON.stringify(ctl.protect_get()), ctl.protect_available()]
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
	_body.add_child(UI.caption("Игроки" + (" · %d" % peers.size() if peers.size() > 0 else "")))
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
		r.add_child(_small("Выгнать", "secondary", func(): ctl.kick(pid)))
		if _more_peer == pid:
			r.add_child(_small("Забанить", "danger", func(): _more_peer = ""; ctl.ban(pid)))
		else:
			r.add_child(_small("⋯", "ghost", func(): _more_peer = pid; _key = ""; _refresh(); _fit()))
		_body.add_child(r)
	# --- защита мира (только сервер LANTERN: правила меняем командами сервера)
	_body.add_child(UI.caption("Защита мира"))
	if ctl.protect_available():
		var pr: Dictionary = ctl.protect_get()
		var fl := UI.flow(6)
		for it in RoomControl.PROTECT:
			var k: String = it[0]
			var ch := UI.chip(String(it[1]), bool(pr.get(k, false)), func(): ctl.protect_set(k, not bool(ctl.protect_get().get(k, false))))
			ch.custom_minimum_size.y = 32
			ch.add_theme_font_size_override("font_size", 12)
			fl.add_child(ch)
		_body.add_child(fl)
	else:
		_body.add_child(UI.para(RoomControl.PROTECT_OWN_HINT, 12, UI.MUTED))
	_body.add_child(UI.caption("Кто может зайти"))
	_body.add_child(UI.segmented([["friends", "По коду"], ["all", "Все в LANTERN"]], "all" if ctl.is_public() else "friends", func(k): ctl.set_public(k == "all")))
	var ir := UI.hbox(6)
	var sb := _small("Скопировать ссылку", "tonal", func():
		if ctl.share_text() != "":
			UI.copy(ctl.share_text())
			_status.text = "Приглашение скопировано")
	sb.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	ir.add_child(sb)
	ir.add_child(_small("Номер", "secondary", func():
		UI.copy(ctl.num() if ctl.num() != "" else ctl.code())
		_status.text = "Номер скопирован"))
	_body.add_child(ir)
	for f in Social.friends.filter(func(x): return x.get("online", false)).slice(0, 3):
		var fr := UI.hbox(6)
		var fl2 := UI.label(String(f.nick), 14, UI.TEXT)
		fl2.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		fr.add_child(fl2)
		var fid := int(f.id)
		fr.add_child(_small("Позвать", "secondary", func(): ctl.invite(fid)))
		_body.add_child(fr)
	var br := UI.hbox(6)
	var cb := _small("Закрыть комнату", "danger", func(): ctl.close_room())
	cb.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	br.add_child(cb)
	br.add_child(_small("Свернуть", "ghost", func():
		_pinned = false
		_set_expanded(false)))
	_body.add_child(br)
	_body.add_child(UI.para("В игре нажмите Esc и кликните по окошку. Не видно поверх игры — включите в Minecraft оконный режим (Java: F11).", 11, UI.DIM))
	_fit.call_deferred()


func _small(text: String, kind: String, cb: Callable) -> Button:
	var b := UI.button(text, kind, cb)
	b.custom_minimum_size.y = 36
	b.add_theme_font_size_override("font_size", 13)
	b.focus_mode = Control.FOCUS_NONE
	return b
