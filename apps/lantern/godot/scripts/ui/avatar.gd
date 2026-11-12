class_name Avatar
extends Control
## Аватар игрока: круг с градиентом (свой у каждого ника; у владельцев косметики — цвета их пламени), первая буква ника,
## кольцо цвета связи и маленький светящийся значок уровня «Фонаря».

var nick := "?"
var cols: Array = []          # [цвет 1, цвет 2] — если пусто, из ника
var ring := Color(0, 0, 0, 0) # кольцо: связь (зелёное / жёлтое / красное) или «в сети»
var rim := Color(0, 0, 0, 0)  # тёмная кромка (в стопке аватаров — отделяет от соседа)
var level := 0                # уровень фонаря (0 — не показывать)

const QUALITY := {"great": Color("5fd38a"), "good": Color("ffc857"), "poor": Color("ff6b6b")}


static func make(nick_text: String, px := 44, quality := "", lvl := 0, equip: Variant = null) -> Avatar:
	var a := Avatar.new()
	a.nick = nick_text if nick_text != "" else "?"
	a.custom_minimum_size = Vector2(px, px)
	a.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	a.mouse_filter = Control.MOUSE_FILTER_IGNORE
	a.level = lvl
	if QUALITY.has(quality):
		a.ring = QUALITY[quality]
	var e: Dictionary = {}
	if equip is Dictionary:
		e = equip
	elif equip is String and equip != "":
		var p: Variant = JSON.parse_string(equip)
		if p is Dictionary:
			e = p
	if LanternIcon.FLAMES.has(String(e.get("flame", ""))):
		var f: Array = LanternIcon.FLAMES[e.flame]
		a.cols = [f[0], (f[0] as Color).lerp(f[1], 0.5).darkened(0.25)]
	return a


## Из записи пользователя relay: {nick, lantern:{level}, equip}
static func of(user: Variant, px := 44, quality := "") -> Avatar:
	var u: Dictionary = user if user is Dictionary else {}
	var lv: Variant = u.get("lantern", {})
	return make(String(u.get("nick", "?")), px, quality, int((lv as Dictionary).get("level", 0)) if lv is Dictionary else 0, u.get("equip", ""))


func _colors() -> Array:
	if cols.size() >= 2:
		return cols
	var h := float(absi(hash(nick)) % 360) / 360.0
	return [Color.from_hsv(h, 0.62, 0.98), Color.from_hsv(fmod(h + 0.11, 1.0), 0.78, 0.62)]


func _draw() -> void:
	var s := minf(size.x, size.y)
	var c := size / 2
	var has_ring := ring.a > 0.0
	var r := s / 2 - (maxf(2.0, s * 0.07) + 1.5 if has_ring else (1.0 if rim.a > 0.0 else 0.0))
	var cc := _colors()
	var pts := PackedVector2Array()
	var vc := PackedColorArray()
	for i in 40:
		var a := float(i) / 40.0 * TAU
		var d := Vector2(cos(a), sin(a))
		pts.append(c + d * r)
		vc.append((cc[0] as Color).lerp(cc[1], clampf(0.5 + (d.x * 0.45 + d.y * 0.75) * 0.5, 0.0, 1.0)))
	draw_polygon(pts, vc)
	draw_arc(c, r, 0, TAU, 48, (cc[0] as Color).lerp(cc[1], 0.5), 1.0, true)     # сглаживает край
	draw_arc(c + Vector2(0, 0.5), r - 1.0, PI * 1.1, PI * 1.9, 24, Color(1, 1, 1, 0.35), 1.0, true)   # блик сверху
	if rim.a > 0.0:
		draw_arc(c, r + 0.75, 0, TAU, 48, rim, 1.5, true)
	if has_ring:
		draw_arc(c, s / 2 - maxf(1.0, s * 0.035), 0, TAU, 56, ring, maxf(2.0, s * 0.07), true)
	var font: Font = UI.font_black
	var fs := int(s * 0.44)
	var ch := nick.substr(0, 1).to_upper()
	var ts := font.get_string_size(ch, HORIZONTAL_ALIGNMENT_LEFT, -1, fs)
	var pos := Vector2(c.x - ts.x / 2, c.y + fs * 0.36)      # базовая линия: половина высоты заглавной буквы ниже центра
	draw_string(font, pos + Vector2(0, 1), ch, HORIZONTAL_ALIGNMENT_LEFT, -1, fs, Color(0, 0, 0, 0.25))
	draw_string(font, pos, ch, HORIZONTAL_ALIGNMENT_LEFT, -1, fs, Color(1, 1, 1, 0.96))
	if level > 0 and s >= 36.0:
		# значок уровня «Фонаря»: маленький светящийся кружок справа внизу
		var br := maxf(8.0, s * 0.19)
		var bc := Vector2(size.x / 2 + s / 2 - br * 0.9, size.y / 2 + s / 2 - br * 0.9)
		var lc := Color(LanternIcon.LEVEL_FLAMES[clampi(level, 1, 9) - 1])
		draw_circle(bc, br + 3.0, Color(lc, 0.22), true, -1, true)
		draw_circle(bc, br + 1.5, Color(0.06, 0.07, 0.15), true, -1, true)
		draw_circle(bc, br, lc, true, -1, true)
		var ls := str(level)
		var lfs := int(br * 1.25)
		var lts := UI.font_black.get_string_size(ls, HORIZONTAL_ALIGNMENT_LEFT, -1, lfs)
		draw_string(UI.font_black, Vector2(bc.x - lts.x / 2, bc.y + lfs * 0.36), ls, HORIZONTAL_ALIGNMENT_LEFT, -1, lfs, UI.ON_ACCENT)
