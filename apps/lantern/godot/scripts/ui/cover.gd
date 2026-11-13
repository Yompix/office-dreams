class_name Cover
extends ColorRect
## Обложка игры: процедурный пейзаж в стиле LANTERN (assets/shaders/cover.gdshader). У каждой комнаты свой (по её
## номеру/коду), палитра — по режиму игры. Слева в строке списка и большой шапкой на экране игры.
## Свой стиль: гладкие силуэты и градиенты — ничего блочного и похожего на чужую графику (docs/LEGAL.md).

## режим → [небо сверху, небо у горизонта, дальний слой, средний, ближний, солнце/луна]
const PALETTES := {
	"survival": ["0a2536", "2fa391", "1f7a78", "145458", "0a3036", "fff0b8"],     # зелёно-бирюзовый
	"creative": ["241257", "f07ab8", "9150d8", "5a2a9e", "2d145e", "ffe0f2"],     # фиолетово-розовый
	"pvp": ["36091a", "ff7d3c", "bd3a2c", "7c1e25", "3c0d17", "ffd98a"],          # красно-оранжевый
	"minigame": ["08335f", "38d3e2", "1ea0c4", "0f6f96", "083f60", "ffe45c"],     # жёлто-голубой
	"roleplay": ["0b1040", "5f80ff", "3c51c8", "252f8e", "131950", "e4eaff"],     # сине-индиго
	"other": ["151a44", "d07c40", "71507f", "3c2e60", "1c183a", "ffd596"],        # лагерь у фонаря
}
## «Другое» (режим не выбран) — чтобы игры всё равно не были на одно лицо: ещё три палитры, выбор по номеру комнаты
const OTHER_PALETTES := [
	["151a44", "d07c40", "71507f", "3c2e60", "1c183a", "ffd596"],     # закат у фонаря
	["0c1838", "4a8fd0", "3a5fa8", "25407c", "121f48", "eef4ff"],     # лунная ночь
	["1c1240", "c05a9a", "7a3f96", "4a2670", "24123f", "ffd0e6"],     # сиреневые сумерки
	["0a2230", "e0a060", "4f8a84", "2c5a5e", "123036", "fff0c0"],     # рассвет над водой
]
const MODE_COLORS := {"survival": Color("3fd0a6"), "creative": Color("d77bff"), "pvp": Color("ff7a4a"),
	"minigame": Color("ffd84a"), "roleplay": Color("7f9bff"), "other": Color("ffb648")}

static var _shader: Shader


static func mode_color(mode: String) -> Color:
	return MODE_COLORS.get(mode, MODE_COLORS["other"])


## seed_text — код или номер комнаты (или любое имя); radius — скругление в dp; shade — затемнение снизу под текст.
static func make(seed_text: String, mode := "other", radius := 14, shade := 0.0) -> Cover:
	var c := Cover.new()
	c.setup(seed_text, mode, radius, shade)
	return c


var seed_text := ""
var mode := "other"


func setup(p_seed: String, p_mode := "other", radius := 14, shade := 0.0) -> void:
	seed_text = p_seed
	mode = p_mode
	if _shader == null:
		_shader = load("res://assets/shaders/cover.gdshader")
	var m := ShaderMaterial.new()
	m.shader = _shader
	var h := absi(hash(seed_text))
	var pal: Array = PALETTES[mode] if PALETTES.has(mode) and mode != "other" else OTHER_PALETTES[(h / 59) % OTHER_PALETTES.size()]
	var names := ["sky_top", "sky_bot", "c_far", "c_mid", "c_near", "c_sun"]
	for i in 6:
		m.set_shader_parameter(names[i], Color(pal[i]))
	m.set_shader_parameter("seed", float(h % 9973) / 97.0)
	m.set_shader_parameter("kind", (h / 9973) % 6)
	m.set_shader_parameter("radius", float(radius))
	m.set_shader_parameter("shade", shade)
	m.set_shader_parameter("fade", 0.0)
	material = m
	color = Color.WHITE
	mouse_filter = Control.MOUSE_FILTER_IGNORE
	if not resized.is_connected(_fit):
		resized.connect(_fit)
	_fit()


func _fit() -> void:
	if material and size.x > 0.0 and size.y > 0.0:
		(material as ShaderMaterial).set_shader_parameter("px", size)


## Плитка-обложка для строки списка (px × px).
static func tile(seed_text: String, mode: String, px := 60) -> Cover:
	var c := make(seed_text, mode, int(px * 0.3))
	c.custom_minimum_size = Vector2(px, px)
	c.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	return c
