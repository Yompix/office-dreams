class_name FlameFX
extends ColorRect
## 2.0.3: яркое живое пламя — единственный цвет в монохромном интерфейсе Yompix (assets/shaders/flame.gdshader).
## У каждого пламени свой вид и своя «фишка», пока оно надето (перки). Видимое другим — только по данным
## сервера: relay сам проверяет, что пламя куплено (economy.js: owns/wear), и раздаёт его вместе с профилем.
## Экономия: время двигает скрипт (не TIME), не чаще 24 раз в секунду, только пока пламя на экране и после
## касания/появления (8 с); «Меньше анимаций» и лёгкий режим — неподвижный кадр.

const INFO := {
	"flame_amber": {"name": "Белое пламя", "style": 0, "c": ["ffffff", "fff1cf", "ffad45"], "rank": 0,
		"perk": "Классика: чистый белый жар. Пламя по умолчанию."},
	"flame_orange": {"name": "Пиксельное пламя", "style": 1, "c": ["fff9c4", "ffd23c", "ff4a1a"], "rank": 1,
		"perk": "Ещё 4 реакции в чате «Любых игр»: 👾 🕹️ 💾 ⭐ — видят все."},
	"flame_rose": {"name": "Неоновое пламя", "style": 2, "c": ["7af7ff", "ff3df0", "ff2bd6"], "rank": 2,
		"perk": "Ваш ник светится неоном в чатах и списках — у всех."},
	"flame_violet": {"name": "Глитч-пламя", "style": 5, "c": ["5ff6ff", "ffffff", "ff2d55"], "rank": 3,
		"perk": "Аура вокруг вашей игры и сети в «Играх» и «Найти игроков»."},
	"flame_ice": {"name": "Призрачное пламя", "style": 3, "c": ["f0fbff", "8fe0ff", "3d7bff"], "rank": 4,
		"perk": "Призрачная QR-карточка, когда вы делитесь кодом игры или сети."},
	"flame_emerald": {"name": "Золотая корона", "style": 4, "c": ["fffbe0", "ffd84a", "ff9a1a"], "rank": 5,
		"perk": "+10% искр за задания и золотые искры короны у «Костра» — для всех у огня."},
	"flame_rainbow": {"name": "Радужное пламя", "style": 6, "c": ["ffffff", "ffffff", "ff5ad8"], "rank": 6,
		"perk": "Живой фон профиля и радужная аура в списке друзей у всех."},
	"flame_s1": {"name": "Пламя первых огней", "style": 7, "c": ["fff2c2", "ffb648", "ff5a14"], "rank": 2,
		"perk": "Сезонное: искры поднимаются над фонарём."},
	"flame_s1p": {"name": "Пламя сезона (премиум)", "style": 8, "c": ["ffe6e6", "ff7a5a", "ff2448"], "rank": 3,
		"perk": "Сезонное: алый жар и искры."},
	"flame_s1legend": {"name": "Легендарное пламя", "style": 9, "c": ["fffbe8", "ffe08a", "ffb648"], "rank": 7,
		"perk": "Легенда сезона: неоновый ник и радужная аура."},
}
## Фишка → какие пламена её дают
const PERKS := {
	"pixel_react": ["flame_orange"],
	"neon_nick": ["flame_rose", "flame_s1legend"],
	"card_aura": ["flame_violet"],
	"qr_skin": ["flame_ice"],
	"crown": ["flame_emerald"],
	"rainbow": ["flame_rainbow", "flame_s1legend"],
}
const PIXEL_REACTIONS := ["👾", "🕹️", "💾", "⭐"]
## Коллекция: сколько платных/сезонных пламён куплено (белое не считается)
const COLLECT := [[3, "Вспышка входа: друзья видят огненную вспышку, когда вы заходите к ним в игру"],
	[6, "Титул «Хранитель пламени» и +80 искр"]]

static var _shader: Shader
static var awake_until := 0     # мс: после касания пламя оживает на 8 с

var flame := "flame_amber"
var _t := 0.0
var _acc := 0.0
var _mat: ShaderMaterial
var _live := true


static func info(id: String) -> Dictionary:
	return INFO.get(id, INFO["flame_amber"])


static func has(id: String, perk: String) -> bool:
	return id in PERKS.get(perk, [])


static func name_of(id: String) -> String:
	return L.t(String(info(id).name))


static func perk_of(id: String) -> String:
	return L.t(String(info(id).perk))


## Моё надетое пламя (с сервера: eco/shop → equip)
static func mine() -> String:
	var e: Variant = Social.eco.get("equip", {}) if Social.eco is Dictionary else {}
	if e is Dictionary and String(e.get("flame", "")) != "":
		return String(e.flame)
	return flame_of(Social.me.get("equip", "") if Social.me is Dictionary else "")


## Пламя из записи пользователя relay (equip — словарь или JSON-строка)
static func flame_of(equip: Variant) -> String:
	var e: Variant = equip
	if equip is String and String(equip) != "":
		e = JSON.parse_string(String(equip))
	if e is Dictionary:
		return String(e.get("flame", ""))
	return ""


static func make(id: String, live := true) -> FlameFX:
	var f := FlameFX.new()
	f.setup(id, live)
	return f


func setup(id: String, live := true) -> FlameFX:
	flame = id if INFO.has(id) else "flame_amber"
	_live = live
	mouse_filter = Control.MOUSE_FILTER_IGNORE
	color = Color.WHITE
	if _shader == null:
		_shader = load("res://assets/shaders/flame.gdshader")
	_mat = ShaderMaterial.new()
	_mat.shader = _shader
	var inf := info(flame)
	_mat.set_shader_parameter("style", int(inf.style))
	var c: Array = inf.c
	_mat.set_shader_parameter("c_core", Color(c[0]))
	_mat.set_shader_parameter("c_mid", Color(c[1]))
	_mat.set_shader_parameter("c_edge", Color(c[2]))
	_t = randf() * 10.0
	_mat.set_shader_parameter("t", _t)
	material = _mat
	resized.connect(func(): _mat.set_shader_parameter("px", size))
	return self


func set_intensity(v: float) -> void:
	_mat.set_shader_parameter("intensity", v)


func set_glow(v: float) -> void:
	_mat.set_shader_parameter("glow", v)


func _ready() -> void:
	awake_until = maxi(awake_until, Time.get_ticks_msec() + 8000)
	set_process(_live)
	set_process_input(_live)


func _input(e: InputEvent) -> void:
	if e is InputEventMouseButton or e is InputEventScreenTouch or e is InputEventMouseMotion:
		awake_until = Time.get_ticks_msec() + 8000


func _process(d: float) -> void:
	if not FX.on() or FX.lite:
		return
	if Time.get_ticks_msec() > awake_until or not is_visible_in_tree():
		return
	# вне экрана — не двигаем (и не перерисовываем)
	if not get_viewport_rect().intersects(get_global_rect().grow(16)):
		return
	_acc += d
	if _acc < 1.0 / 24.0:
		return
	_t += _acc
	_acc = 0.0
	_mat.set_shader_parameter("t", _t)


# ------------------------------------------------------------------ фишки (видимые другим — по данным сервера)

## Неоновый ник: подпись светится цветом пламени (если у владельца ника есть эта фишка)
static func style_nick(l: Label, id: String) -> void:
	if not has(id, "neon_nick"):
		return
	var c: Array = info(id).c
	l.add_theme_color_override("font_color", Color(c[0]).lerp(Color.WHITE, 0.25))
	l.add_theme_color_override("font_outline_color", Color(Color(c[2]), 0.55))
	l.add_theme_constant_override("outline_size", 5)
	l.set_meta("user_text", true)


## Аура вокруг карточки (игра/сеть с «Глитч-пламенем», радужное — в списке друзей). Рамка-пламя по краю.
static func aura(c: Control, id: String) -> void:
	if not (has(id, "card_aura") or has(id, "rainbow")):
		return
	var f := make(id)
	f._mat.set_shader_parameter("style", 100)
	if has(id, "rainbow"):
		f._mat.set_shader_parameter("c_mid", Color(1, 1, 1, 0))     # радужная рамка
	f.set_glow(0.0)
	_attach(c, f, 5.0)


## Рамка-пламя вокруг c (через Node2D: контейнер не раскладывает её как содержимое), m — на сколько шире
static func _attach(c: Control, f: FlameFX, m: float) -> void:
	var n2 := Node2D.new()
	c.add_child(n2)
	n2.add_child(f)
	f.position = Vector2(-m, -m)
	f.size = c.size + Vector2(m, m) * 2.0
	c.resized.connect(func(): if is_instance_valid(f): f.size = c.size + Vector2(m, m) * 2.0)


## Призрачная карточка QR: холодное свечение вокруг QR (если на мне «Призрачное пламя»)
static func qr_skin(panel: Control) -> void:
	var id := mine()
	if not has(id, "qr_skin"):
		return
	var f := make(id)
	f._mat.set_shader_parameter("style", 100)
	f.set_glow(0.0)
	_attach(panel, f, 14.0)


## «Вспышка входа» (коллекция из 3+ пламени): у хозяина над экраном вспыхивает пламя гостя и подпись
static func flare(app: Control, id: String, text: String) -> void:
	if app.has_method("toast"):
		app.toast(text, UI.TEXT)
	if not FX.on():
		return
	var f := make(id if INFO.has(id) else "flame_amber")
	f.set_glow(1.2)
	f.z_index = 60
	var vs := app.get_viewport_rect().size
	var w := minf(vs.x * 0.35, 260.0)
	f.size = Vector2(w, w * 1.3)
	f.position = Vector2(vs.x / 2 - w / 2, vs.y * 0.62 - w * 1.3)
	f.modulate.a = 0.0
	app.add_child(f)
	FX.burst(app, Vector2(vs.x / 2, vs.y * 0.5), 26)
	var tw := FX.drive(f.create_tween())
	tw.tween_property(f, "modulate:a", 1.0, 0.18).set_trans(Tween.TRANS_CUBIC).set_ease(Tween.EASE_OUT)
	tw.parallel().tween_property(f, "scale", Vector2(1.08, 1.08), 0.5).from(Vector2(0.6, 0.6)).set_trans(Tween.TRANS_QUINT).set_ease(Tween.EASE_OUT)
	tw.tween_interval(0.9)
	tw.tween_property(f, "modulate:a", 0.0, 0.5).set_trans(Tween.TRANS_CUBIC).set_ease(Tween.EASE_IN)
	tw.tween_callback(f.queue_free)
	f.pivot_offset = f.size / 2
