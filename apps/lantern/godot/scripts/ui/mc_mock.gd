class_name McMock
extends RefCounted
## Маленькие «картинки» экранов Minecraft для пошаговых подсказок (рисуются кодом, без скриншотов игры).
## Нужное место подсвечено жёлтой рамкой.

const BG := Color("313233")
const BTN := Color("48494a")
const EDGE := Color("1e1e1f")
const GREEN := Color("3c8527")
const HI := Color("ffc857")
const TXT := Color("f2f2f2")


static func make(kind: String) -> Control:
	var p := PanelContainer.new()
	p.add_theme_stylebox_override("panel", UI.box(BG, 6, 10, EDGE, 2))
	p.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	p.mouse_filter = Control.MOUSE_FILTER_IGNORE
	var v := UI.vbox(6)
	p.add_child(v)
	match kind:
		"world":
			v.add_child(_title("Играть"))
			var row := UI.hbox(8)
			row.add_child(UI.texture("res://assets/img/mini_grass.png", 30, 30))
			var nv := UI.vbox(0)
			nv.size_flags_horizontal = Control.SIZE_EXPAND_FILL
			nv.add_child(UI.label("Мой мир", 13, TXT, UI.font_bold))
			nv.add_child(UI.label("Выживание", 11, Color(TXT, 0.6)))
			row.add_child(nv)
			row.add_child(_btn("Играть", GREEN, true))
			v.add_child(row)
		"settings":
			v.add_child(_title("Настройки"))
			var row := UI.hbox(8)
			var left := UI.vbox(4)
			left.add_child(_btn("Игра", BTN, false))
			left.add_child(_btn("Мультиплеер", BTN, true))
			left.add_child(_btn("Управление", BTN, false))
			row.add_child(left)
			var right := UI.vbox(4)
			right.size_flags_horizontal = Control.SIZE_EXPAND_FILL
			right.add_child(_toggle("Многопользовательская игра", true, false))
			right.add_child(_toggle("Видимость для игроков LAN", true, false))
			row.add_child(right)
			v.add_child(row)
		"toggle":
			v.add_child(_title("Мультиплеер"))
			v.add_child(_toggle("Многопользовательская игра", true, true))
			v.add_child(_toggle("Видимость для игроков LAN", true, true))
		"pause":
			v.add_child(_title("Меню игры"))
			v.add_child(_btn("Вернуться к игре", BTN, false))
			v.add_child(_btn("Достижения", BTN, false))
			v.add_child(_btn("Открыть для сети", BTN, true))
		"lan":
			v.add_child(_title("Открыть для сети"))
			v.add_child(_btn("Режим игры: Выживание", BTN, false))
			v.add_child(_btn("Разрешить читы: Нет", BTN, false))
			v.add_child(_btn("Открыть мир для сети", BTN, true))
	return p


static func _title(t: String) -> Label:
	var l := UI.label(t, 12, Color(TXT, 0.7), UI.font_bold)
	l.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	return l


static func _btn(t: String, c: Color, hi: bool) -> PanelContainer:
	var b := PanelContainer.new()
	b.add_theme_stylebox_override("panel", UI._hpad(UI.box(c, 3, 0, HI if hi else EDGE, 2 if hi else 1), 10, 4))
	var l := UI.label(t, 12, TXT, UI.font_bold)
	l.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	b.add_child(l)
	b.mouse_filter = Control.MOUSE_FILTER_IGNORE
	return b


static func _toggle(t: String, on: bool, hi: bool) -> PanelContainer:
	var b := PanelContainer.new()
	b.add_theme_stylebox_override("panel", UI._hpad(UI.box(Color(0, 0, 0, 0.2), 3, 0, HI if hi else Color(0, 0, 0, 0), 2 if hi else 0), 8, 4))
	var row := UI.hbox(8)
	b.add_child(row)
	var l := UI.label(t, 12, TXT)
	l.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	l.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	l.custom_minimum_size.x = 60
	row.add_child(l)
	var sw := PanelContainer.new()
	sw.custom_minimum_size = Vector2(34, 16)
	sw.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	sw.add_theme_stylebox_override("panel", UI.box(GREEN if on else BTN, 2, 2, EDGE, 1))
	var knob := ColorRect.new()
	knob.color = TXT
	knob.custom_minimum_size = Vector2(12, 12)
	knob.size_flags_horizontal = Control.SIZE_SHRINK_END if on else Control.SIZE_SHRINK_BEGIN
	knob.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	sw.add_child(knob)
	row.add_child(sw)
	b.mouse_filter = Control.MOUSE_FILTER_IGNORE
	return b
