class_name PhoneScreen
extends Control
## Экран телефона на весь кадр — перенос phone() / phoneIn() старой игры (engine.js): заставка с детским рисунком Лили, часами
## и уведомлениями (lock), входящий (call) и исходящий (out) звонок, разговор с таймером (talk, call2), сообщения (msg).
## Рисуется в координатах вертикального холста 1080×1920, вписанного по высоте. Строки из сценария уже переведены (StoryDB.scene).

const X := 240.0
const Y := 230.0
const PW := 600.0
const PH := 1060.0
## подписи на рисунке Лили: слово, точка на рисунке, наклон (как word() в engine.js drawing())
const WORDS := [["ИТАН", 150.0, 382.0, -0.05], ["Я", 300.0, 542.0, 0.08], ["БУБЛИК", 470.0, 692.0, -0.07]]

var spec := {}
var t := 0.0
static var _fonts := {}
static var _drawing: Texture2D


func _init() -> void:
	set_anchors_preset(Control.PRESET_FULL_RECT)
	mouse_filter = Control.MOUSE_FILTER_IGNORE
	visible = false


func show_phone(p) -> void:
	spec = p if p is Dictionary else {"kind": String(p)}
	t = 0.0
	visible = true
	queue_redraw()


func tick(time: float) -> void:
	t = time
	queue_redraw()


## Шрифт экрана (Inter нужной толщины; для китайского, японского, корейского — запасной шрифт игры).
static func font(w: int) -> Font:
	var key := str(w)
	if not _fonts.has(key):
		var f: FontFile = load("res://ui/fonts/Inter-%d.woff2" % w) if w > 0 else load("res://ui/fonts/Pangolin-Regular.woff2")
		f = f.duplicate()
		f.fallbacks = cjk_fallbacks()
		_fonts[key] = f
	return _fonts[key]


static func cjk_fallbacks() -> Array[Font]:
	var out: Array[Font] = []
	var d := DirAccess.open("res://assets/fonts/cjk")
	if d:
		for f in d.get_files():
			if f.ends_with(".woff2") and ResourceLoader.exists("res://assets/fonts/cjk/" + f):
				out.append(load("res://assets/fonts/cjk/" + f))
	return out


func _draw() -> void:
	draw_rect(Rect2(Vector2.ZERO, size), Color("#030405"))
	var us := size.y / 1920.0
	draw_set_transform(Vector2(size.x / 2.0 - 540.0 * us, 0.0), 0.0, Vector2(us, us))
	var kind := String(spec.get("kind", "lock"))
	_rrect(Rect2(X - 20, Y - 20, PW + 40, PH + 40), 70, Color("#111111"))
	_rrect(Rect2(X, Y, PW, PH), 54, Color("#0b0d12"))
	match kind:
		"lock":
			_lock()
		"msg":
			_msg()
		_:
			_call(kind)
	draw_set_transform(Vector2.ZERO)


func _lock() -> void:
	if _drawing == null:
		_drawing = load("res://ui/lily_drawing.png")
	var pts := _rpoly(Rect2(X, Y, PW, PH), 54)
	var uvs := PackedVector2Array()
	for p in pts:
		uvs.append(Vector2((p.x - X) / PW, (p.y - Y) / PH))
	draw_colored_polygon(pts, Color.WHITE, uvs, _drawing)
	# подписи детским почерком — на языке игры
	var us := size.y / 1920.0
	var phone_xf := Transform2D(0.0, Vector2(us, us), 0.0, Vector2(size.x / 2.0 - 540.0 * us, 0.0))
	var kid := font(0)
	for w in WORDS:
		var at := Vector2(X + 42.0 + 0.86 * float(w[1]), Y + 158.0 + 0.86 * float(w[2]))
		draw_set_transform_matrix(phone_xf * Transform2D(float(w[3]), at))
		draw_string(kid, Vector2(-300, 0), StoryDB.t(w[0]), HORIZONTAL_ALIGNMENT_CENTER, 600, 40, Color(0.169, 0.169, 0.541, 0.85))
	draw_set_transform_matrix(phone_xf)
	# затемнение под часами
	var top := PackedVector2Array()
	for p in pts:
		top.append(Vector2(p.x, minf(p.y, Y + 330)))
	draw_colored_polygon(top, Color(0, 0, 0, 0.18))
	_text(String(spec.get("time", "21:00")), 540, Y + 230, 300, 150, Color.WHITE)
	_text(StoryDB.t(String(spec.get("date", "среда, 28 октября"))), 540, Y + 285, 500, 34, Color.WHITE)
	var notes: Array = spec.get("notes", [])
	for i in notes.size():
		var n: Dictionary = notes[i]
		var at := float(n.get("at", 0.6))
		if t < at:
			continue
		var k := clampf((t - at) / 0.25, 0.0, 1.0)
		var ny := Y + 312 + i * 160 - (1.0 - k) * 40
		_rrect(Rect2(X + 24, ny, PW - 48, 146), 30, Color(28 / 255.0, 30 / 255.0, 36 / 255.0, 0.93 * k))
		var grey := Color("#99aabb", k)
		draw_string(font(600), Vector2(X + 52, ny + 42), StoryDB.t("СООБЩЕНИЯ") + " · " + String(n.get("from", StoryDB.t("Лили"))),
			HORIZONTAL_ALIGNMENT_LEFT, -1, 26, grey)
		draw_string(font(600), Vector2(X + 52, ny + 42), String(n.get("ago", StoryDB.t("сейчас"))), HORIZONTAL_ALIGNMENT_RIGHT, PW - 104, 26, grey)
		_wrap(String(n.get("text", "")), X + 52, ny + 86, PW - 104, 38, 2, font(500), 32, Color(1, 1, 1, k))


func _msg() -> void:
	_text(StoryDB.t("Лили"), 540, Y + 120, 600, 34, Color("#99aaaa"))
	_rrect(Rect2(X + 50, Y + 420, PW - 120, 190), 36, Color("#2b2f3a"))
	var f := font(600)
	draw_string(f, Vector2(X + 90, Y + 500), StoryDB.t("Не открывай им."), HORIZONTAL_ALIGNMENT_LEFT, -1, 40, Color.WHITE)
	if t > 1.6:
		draw_string(f, Vector2(X + 90, Y + 560), StoryDB.t("Это не я."), HORIZONTAL_ALIGNMENT_LEFT, -1, 40, Color.WHITE)
	if t > 2.4:
		_rrect(Rect2(X + 50, Y + 650, PW - 220, 120), 36, Color("#2b2f3a"))
		draw_string(f, Vector2(X + 90, Y + 725), StoryDB.t("Я тебя вижу"), HORIZONTAL_ALIGNMENT_LEFT, -1, 40, Color("#ff6666"))


## call — входящий, out — исходящий (идут гудки), call2 / talk — разговор идёт (таймер)
func _call(kind: String) -> void:
	var pulse := 1.0 + 0.06 * sin(t * 8.0)
	var talking := kind == "call2" or kind == "talk"
	_text(String(spec.get("name", StoryDB.t("Лили"))), 540, Y + 330, 700, 70, Color.WHITE)
	var sub := ""
	if talking:
		var s := int(floor(t)) + int(spec.get("t0", 8))
		sub = "%02d:%02d" % [s / 60, s % 60]
	elif kind == "out":
		sub = StoryDB.t("вызов") + "…"
	else:
		sub = StoryDB.t("мобильный · входящий")
	_text(sub, 540, Y + 400, 500, 36, Color("#99aaaa"))
	if kind == "call":
		draw_circle(Vector2(X + PW - 150, Y + PH - 200), 70 * pulse, Color("#22aa33"))
	draw_circle(Vector2(X + 150 if kind == "call" else 540.0, Y + PH - 200), 70, Color("#dd3333"))


func _text(s: String, cx: float, base: float, w: int, sz: int, c: Color) -> void:
	draw_string(font(w), Vector2(cx - 540, base), s, HORIZONTAL_ALIGNMENT_CENTER, 1080, sz, c)


## Перенос по словам (китайский и японский — по знакам), не больше max_l строк.
func _wrap(s: String, x0: float, y0: float, max_w: float, lh: float, max_l: int, f: Font, sz: int, c: Color) -> void:
	var cj := StoryDB.lang == "zh" or StoryDB.lang == "ja"
	var ws: PackedStringArray = []
	if cj:
		for ch in s:
			ws.append(ch)
	else:
		ws = s.split(" ")
	var line := ""
	var n := 0
	for wd in ws:
		if cj and line == "" and wd == " ":
			continue
		var tst: String = (line + ("" if cj else " ") + wd) if line != "" else wd
		if f.get_string_size(tst, HORIZONTAL_ALIGNMENT_LEFT, -1, sz).x > max_w and line != "":
			draw_string(f, Vector2(x0, y0 + n * lh), line, HORIZONTAL_ALIGNMENT_LEFT, -1, sz, c)
			line = wd
			n += 1
			if n >= max_l:
				return
		else:
			line = tst
	if line != "":
		draw_string(f, Vector2(x0, y0 + n * lh), line, HORIZONTAL_ALIGNMENT_LEFT, -1, sz, c)


func _rrect(r: Rect2, rad: float, c: Color) -> void:
	draw_colored_polygon(_rpoly(r, rad), c)


## Скруглённый прямоугольник многоугольником (для заливки и обрезки картинки по углам экрана).
static func _rpoly(r: Rect2, rad: float) -> PackedVector2Array:
	var pts := PackedVector2Array()
	var corners := [[r.position + Vector2(r.size.x - rad, rad), -PI / 2], [r.end - Vector2(rad, rad), 0.0],
		[Vector2(r.position.x + rad, r.end.y - rad), PI / 2], [r.position + Vector2(rad, rad), PI]]
	for c in corners:
		for i in 9:
			var a: float = c[1] + i / 8.0 * PI / 2
			pts.append(c[0] + Vector2(cos(a), sin(a)) * rad)
	return pts
