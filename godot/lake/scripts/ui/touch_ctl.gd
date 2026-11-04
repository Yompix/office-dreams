class_name TouchCtl
extends Control
## Управление касанием (телефон, планшет). На прогулке: слева ведёшь пальцем — герой идёт (где коснулся, там центр,
## кружок появляется под пальцем); справа ведёшь пальцем — камера смотрит по сторонам (влево-вправо и вверх-вниз);
## кнопка «Осмотреть» справа внизу. В действиях «руль» и «волна» — левый палец (влево / вправо); вне меню — кнопка
## паузы справа вверху. Касание в любом другом месте — как щелчок мышью (выбор, «жми в ритм», тормоз).
## Включается сама на телефоне (Android) или ключом --touch (проверка в облаке).

static var enabled := false
static var move := Vector2.ZERO      # джойстик: x — вправо, y — вперёд (−1…1)
static var _use := false             # нажали «Осмотреть» (забирает прогулка)
const R := 120.0                     # радиус джойстика, точки экрана (холст 1920×1080)
const LOOK := 0.6                    # взгляд: точки пальца → «точки мыши» прогулки (полэкрана ≈ пол-оборота)

var g                                # game.gd
var _stick_id := -1
var _origin := Vector2.ZERO
var _knob := Vector2.ZERO
var _shown := ""                     # что нарисовано (перерисовка — только при смене)
var _look_id := -1                   # палец, которым смотрят (правая половина)
var _walked := false                 # уже ходили пальцем — кружок в покое не показываем
var _looked := false                 # уже смотрели — подсказку «‹ смотреть ›» справа не показываем


static func take_use() -> bool:
	var u := _use
	_use = false
	return u


func _ready() -> void:
	mouse_filter = Control.MOUSE_FILTER_IGNORE
	set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	size = get_viewport_rect().size   # родитель — CanvasLayer: размер по окну задаём сами (иначе 0×0 и всё за краем)
	process_mode = Node.PROCESS_MODE_ALWAYS


func _stick_on() -> bool:
	if g == null or get_tree().paused:
		return false
	if String(g._qte) in ["steer", "tune"]:
		return true
	return g._ex != null and not g.hud.choosing() and not g._ex.busy


func _look_on() -> bool:   # смотреть пальцем справа — только на прогулке (в «руле» правая половина не нужна)
	return g != null and not get_tree().paused and g._ex != null and not g.hud.choosing() and not g._ex.busy \
		and String(g._qte) == ""


func _use_on() -> bool:
	return g != null and not get_tree().paused and g._ex != null and not g.hud.choosing() and not g._ex.busy \
		and String(g._qte) == ""


func _pause_on() -> bool:
	return g != null and not get_tree().paused and not g._menu_mode and not g._ending


## Отступы от выреза камеры и закруглений экрана (телефон), в точках холста: x — слева, y — сверху, z — справа, w — снизу.
func _inset() -> Vector4:
	if not OS.has_feature("mobile"):
		return Vector4.ZERO
	var sa := DisplayServer.get_display_safe_area()
	var ws := Vector2(DisplayServer.window_get_size())
	if sa.size.x <= 0 or ws.x <= 0.0:
		return Vector4.ZERO
	var k := size.x / ws.x
	return Vector4(sa.position.x * k, sa.position.y * k, (ws.x - sa.end.x) * k, (ws.y - sa.end.y) * k)


func _home() -> Vector2:   # где джойстик стоит, пока его не трогают
	var i := _inset()
	return Vector2(250.0 + i.x, size.y - 250.0 - i.w)


func _use_rect() -> Rect2:
	var i := _inset()
	return Rect2(size.x - 300.0 - i.z, size.y - 300.0 - i.w, 220.0, 220.0)


func _pause_rect() -> Rect2:
	var i := _inset()
	return Rect2(size.x - 150.0 - i.z, 24.0 + i.y, 120.0, 120.0)


func _input(e: InputEvent) -> void:
	if not enabled or g == null:
		return
	if e is InputEventScreenTouch:
		var t := e as InputEventScreenTouch
		var p := t.position
		if t.pressed:
			if _pause_on() and _pause_rect().has_point(p):
				get_viewport().set_input_as_handled()
				g._pause()
				return
			if _use_on() and _use_rect().has_point(p):
				_use = true
				get_viewport().set_input_as_handled()
				return
			if _stick_on() and _stick_id < 0 and p.x < size.x * 0.5:
				_stick_id = t.index
				_origin = p
				_knob = p
				move = Vector2.ZERO
				queue_redraw()
			elif _look_on() and _look_id < 0 and p.x >= size.x * 0.5:
				_look_id = t.index
				queue_redraw()
		elif t.index == _stick_id:
			_stick_id = -1
			move = Vector2.ZERO
			queue_redraw()
		elif t.index == _look_id:
			_look_id = -1
			queue_redraw()
	elif e is InputEventScreenDrag and (e as InputEventScreenDrag).index == _stick_id:
		_knob = (e as InputEventScreenDrag).position
		var d := _knob - _origin
		if d.length() > R:
			d = d.normalized() * R
		move = Vector2(d.x / R, -d.y / R)
		if d.length() > R * 0.3:
			_walked = true
		queue_redraw()
	elif e is InputEventScreenDrag and (e as InputEventScreenDrag).index == _look_id and _look_on():
		var rel := (e as InputEventScreenDrag).relative
		g._ex.mouse_dx += rel.x * LOOK
		g._ex.look_dy -= rel.y * LOOK
		if rel.length() > 2.0 and not _looked:
			_looked = true
			queue_redraw()


func _process(_d: float) -> void:
	if not enabled:
		return
	var vs := get_viewport_rect().size
	if size != vs:   # поворот экрана, смена окна
		size = vs
		queue_redraw()
	if not _stick_on() and _stick_id >= 0:
		_stick_id = -1
		move = Vector2.ZERO
	if not _look_on() and _look_id >= 0:
		_look_id = -1
	var s := "%s%s%s%d%d%s%s" % [_stick_on(), _use_on(), _pause_on(), _stick_id, _look_id, _walked, _looked]
	if s != _shown:
		_shown = s
		queue_redraw()


func _draw() -> void:
	if not enabled:
		return
	var f: Font = ThemeDB.fallback_font
	var ru: bool = g != null and g.lang == "ru"
	if _stick_on() and (_stick_id >= 0 or not _walked or String(g._qte) != ""):   # кружок — под пальцем; в покое — подсказка, пока не ходили
		var c := _origin if _stick_id >= 0 else _home()
		var dim := 1.0 if _stick_id >= 0 else 0.55
		draw_circle(c, R, Color(1, 1, 1, 0.10 * dim))
		draw_arc(c, R, 0.0, TAU, 48, Color(1, 1, 1, 0.35 * dim), 3.0)
		var k := (c + (_knob - _origin).limit_length(R)) if _stick_id >= 0 else c
		draw_circle(k, 46.0, Color(1, 1, 1, 0.45 * dim))
	if _look_on() and not _looked:   # подсказка справа: «‹ смотреть ›», пока ни разу не смотрели
		var lc := Vector2(size.x * 0.75, size.y * 0.42)
		var lt := "‹   смотреть   ›" if ru else "‹   look around   ›"
		var lw := f.get_string_size(lt, HORIZONTAL_ALIGNMENT_LEFT, -1, 30).x
		draw_string(f, lc + Vector2(-lw * 0.5, 10), lt, HORIZONTAL_ALIGNMENT_LEFT, -1, 30, Color(1, 1, 1, 0.35))
	if _use_on():
		var r := _use_rect()
		var cc := r.get_center()
		var near: bool = g._ex != null and not (g._ex._near as Dictionary).is_empty()
		draw_circle(cc, r.size.x * 0.5, Color(1, 1, 1, 0.22 if near else 0.08))
		draw_arc(cc, r.size.x * 0.5, 0.0, TAU, 48, Color(1, 1, 1, 0.6 if near else 0.25), 3.0)
		var txt := "Осмотреть" if ru else "Look"
		var fs := 34
		var w := f.get_string_size(txt, HORIZONTAL_ALIGNMENT_LEFT, -1, fs).x
		draw_string(f, cc + Vector2(-w * 0.5, fs * 0.35), txt, HORIZONTAL_ALIGNMENT_LEFT, -1, fs, Color(1, 1, 1, 0.9 if near else 0.4))
	if _pause_on():
		var pr := _pause_rect()
		draw_circle(pr.get_center(), pr.size.x * 0.42, Color(0, 0, 0, 0.30))
		var cx := pr.get_center()
		draw_rect(Rect2(cx + Vector2(-20, -24), Vector2(13, 48)), Color(1, 1, 1, 0.75))
		draw_rect(Rect2(cx + Vector2(7, -24), Vector2(13, 48)), Color(1, 1, 1, 0.75))
