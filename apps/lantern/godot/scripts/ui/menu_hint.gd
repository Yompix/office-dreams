class_name MenuHint
extends RefCounted
## Схемы-подсказки «какое меню игры → какая кнопка» для пошаговых инструкций.
## Нарочно в стиле LANTERN (наши цвета, шрифты, скругления), а не копия интерфейса игры:
## названия меню и кнопок — просто текст, нужная кнопка подсвечена акцентной рамкой и стрелкой.


static func make(kind: String) -> Control:
	var p := PanelContainer.new()
	p.add_theme_stylebox_override("panel", UI.box(UI.FIELD, UI.R_L, 12, UI.LINE, 1))
	p.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	p.mouse_filter = Control.MOUSE_FILTER_IGNORE
	var v := UI.vbox(6)
	p.add_child(v)
	match kind:
		"world":
			v.add_child(_title(L.t("Меню игры · Играть")))
			v.add_child(_row(L.t("Мой мир"), true, "play"))
		"settings":
			v.add_child(_title(L.t("Пауза → Настройки")))
			v.add_child(_row(L.t("Мультиплеер"), true))
			v.add_child(_row(L.t("Другие разделы…"), false))
		"toggle":
			v.add_child(_title(L.t("Настройки → Мультиплеер")))
			v.add_child(_toggle(L.t("Многопользовательская игра"), true))
			v.add_child(_toggle(L.t("Видимость для игроков LAN"), true))
		"pause":
			v.add_child(_title(L.t("Меню паузы (Esc)")))
			v.add_child(_row(L.t("Вернуться к игре"), false))
			v.add_child(_row(L.t("Открыть для сети"), true))
		"lan":
			v.add_child(_title(L.t("Открыть для сети")))
			v.add_child(_row(L.t("Открыть мир для сети"), true))
	return p


static func _title(t: String) -> Label:
	return UI.label(t, 12, UI.DIM, UI.font_bold)


## Строка меню: подсвеченная — акцентная рамка и стрелка «сюда»
static func _row(t: String, hi: bool, icon := "") -> PanelContainer:
	var b := PanelContainer.new()
	var a := UI.accent()
	b.add_theme_stylebox_override("panel", UI._hpad(UI.box(Color(a, 0.14) if hi else UI.CARD, 10, 0, a if hi else Color(0, 0, 0, 0), 2 if hi else 0), 12, 8))
	b.mouse_filter = Control.MOUSE_FILTER_IGNORE
	var r := UI.hbox(8)
	b.add_child(r)
	if icon != "":
		r.add_child(UI.icon(icon, 16, a if hi else UI.MUTED))
	var l := UI.label(t, 13, UI.TEXT if hi else UI.MUTED, UI.font_bold if hi else UI.font_regular)
	l.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	l.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART     # длинное название переносится, а не распирает экран
	l.custom_minimum_size.x = 90
	r.add_child(l)
	if hi:
		r.add_child(_arrow(a))
	return b


static func _arrow(c: Color) -> Control:
	var ic := UI.icon("chevron", 16, c)
	ic.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	return ic


static func _toggle(t: String, hi: bool) -> PanelContainer:
	var b := _row(t, hi)
	var r: HBoxContainer = b.get_child(0)
	var sw := PanelContainer.new()
	sw.custom_minimum_size = Vector2(34, 18)
	sw.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	sw.add_theme_stylebox_override("panel", UI.box(UI.accent(), 99, 2))
	var knob := PanelContainer.new()
	knob.custom_minimum_size = Vector2(14, 14)
	knob.add_theme_stylebox_override("panel", UI.box(UI.ON_ACCENT, 99))
	knob.size_flags_horizontal = Control.SIZE_SHRINK_END
	knob.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	sw.add_child(knob)
	r.add_child(sw)
	r.move_child(sw, r.get_child_count() - 2)
	return b
