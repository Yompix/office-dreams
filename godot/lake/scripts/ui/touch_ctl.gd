class_name TouchCtl
extends Control
## Управление касанием (телефон, планшет). На прогулке — джойстик (левая половина экрана: где коснулся, там центр) и
## кнопка «Осмотреть» справа внизу; в действиях «руль» и «волна» — тот же джойстик (влево / вправо); вне меню — кнопка
## паузы справа вверху. Касание в любом другом месте — как щелчок мышью (выбор, «жми в ритм», тормоз).
## Включается сама на телефоне (Android) или ключом --touch (проверка в облаке).

static var enabled := false
static var move := Vector2.ZERO      # джойстик: x — вправо, y — вперёд (−1…1)
static var _use := false             # нажали «Осмотреть» (забирает прогулка)
const R := 120.0                     # радиус джойстика, точки экрана (холст 1920×1080)

var g                                # game.gd
var _stick_id := -1
var _origin := Vector2.ZERO
var _knob := Vector2.ZERO
var _shown := ""                     # что нарисовано (перерисовка — только при смене)


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


func _use_on() -> bool:
	return g != null and not get_tree().paused and g._ex != null and not g.hud.choosing() and not g._ex.busy \
		and String(g._qte) == ""


func _pause_on() -> bool:
	return g != null and not get_tree().paused and not g._menu_mode and not g._ending


func _home() -> Vector2:   # где джойстик стоит, пока его не трогают
	return Vector2(250.0, size.y - 250.0)


func _use_rect() -> Rect2:
	return Rect2(size.x - 300.0, size.y - 300.0, 220.0, 220.0)


func _pause_rect() -> Rect2:
	return Rect2(size.x - 150.0, 24.0, 120.0, 120.0)


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
		elif t.index == _stick_id:
			_stick_id = -1
			move = Vector2.ZERO
			queue_redraw()
	elif e is InputEventScreenDrag and (e as InputEventScreenDrag).index == _stick_id:
		_knob = (e as InputEventScreenDrag).position
		var d := _knob - _origin
		if d.length() > R:
			d = d.normalized() * R
		move = Vector2(d.x / R, -d.y / R)
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
	var s := "%s%s%s%d" % [_stick_on(), _use_on(), _pause_on(), _stick_id]
	if s != _shown:
		_shown = s
		queue_redraw()


func _draw() -> void:
	if not enabled:
		return
	var f: Font = ThemeDB.fallback_font
	var ru: bool = g != null and g.lang == "ru"
	if _stick_on():
		var c := _origin if _stick_id >= 0 else _home()
		draw_circle(c, R, Color(1, 1, 1, 0.10))
		draw_arc(c, R, 0.0, TAU, 48, Color(1, 1, 1, 0.35), 3.0)
		var k := (c + (_knob - _origin).limit_length(R)) if _stick_id >= 0 else c
		draw_circle(k, 46.0, Color(1, 1, 1, 0.45))
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
