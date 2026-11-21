class_name Promo
extends RefCounted
## 2.0: «Есть версия для ПК» (на телефоне) / «Есть версия для Android» (на ПК) — ведёт на страницу студии владельца,
## где лежат все сборки LANTERN. На главной — маленькая карточка, её можно закрыть (вернётся в Настройки → «О программе»).
## И карточка «Любые игры» — вход в виртуальную сеть (экран net).

const STUDIO := "https://yompix.github.io/studio"


static func other_title() -> String:
	return L.t("Есть версия для ПК") if Cfg.mobile() else L.t("Есть версия для Android")


static func other_sub() -> String:
	return L.t("LANTERN для Windows — играйте с друзьями и с компьютера") if Cfg.mobile() else L.t("LANTERN на телефоне — друзья зайдут и с Android")


## Строка для «О программе» / «Связь с автором» (не закрывается).
static func other_row() -> Control:
	return UI.list_row(UI.icon_tile("desktop" if Cfg.mobile() else "phone", UI.MUTED, 40, 20), other_title(), other_sub(),
		UI.icon("chevron", 20, UI.DIM), func(): UI.open(STUDIO), Color(0, 0, 0, 0))


## Карточка на главной; null — если игрок её закрыл.
static func other_card() -> Control:
	if Cfg.get_value("app", "promo_other_hidden", false):
		return null
	var p := PanelContainer.new()
	p.add_theme_stylebox_override("panel", UI._hpad(UI.pix(Color(0.04, 0.04, 0.04, 0.88), Color(1, 1, 1, 0.45), 4), 14, 12))
	var r := UI.hbox(12)
	p.add_child(r)
	var ic := Control.new()
	ic.custom_minimum_size = Vector2(36, 36)
	ic.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	ic.draw.connect(func(): PixelArt.draw(ic, "pad", Vector2(0, 8), 2.6, UI.TEXT))
	r.add_child(ic)
	var tv := UI.vbox(2)
	tv.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	var tl := UI.label(other_title(), 15, UI.TEXT, UI.font_bold)
	tl.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	tl.custom_minimum_size.x = 80
	tv.add_child(tl)
	var s := UI.para(other_sub() + "  →", 12, UI.MUTED)
	tv.add_child(s)
	r.add_child(tv)
	var x := UI.icon_button("close", func():
		Cfg.set_value("app", "promo_other_hidden", true)
		if is_instance_valid(p):
			var tw := p.create_tween()
			tw.tween_property(p, "modulate:a", 0.0, 0.18)
			tw.tween_callback(p.queue_free), L.t("Скрыть"), UI.DIM)
	x.size_flags_vertical = Control.SIZE_SHRINK_BEGIN
	r.add_child(x)
	UI.tappable(p, func(): UI.open(STUDIO))     # нажимается вся карточка (на узком телефоне кнопка не помещалась)
	return p


## Большая карточка «Любые игры» на главной: вход в виртуальную локальную сеть.
static func net_card(app: Control) -> Control:
	var p := PanelContainer.new()
	p.add_theme_stylebox_override("panel", UI._hpad(UI.pix(Color(0.06, 0.06, 0.06, 0.92), Color(1, 1, 1, 0.85), 5), 16, 14))
	var r := UI.hbox(14)
	p.add_child(r)
	var ch := Chibi.new()
	ch.custom_minimum_size = Vector2(64, 64)
	ch.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	r.add_child(ch)
	var tv := UI.vbox(4)
	tv.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	var cap := UI.caption(L.t("Новое · виртуальная сеть"), UI.MUTED)
	cap.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	cap.custom_minimum_size.x = 80
	tv.add_child(cap)
	var t := UI.label(L.t("Любые игры по сети"), 19, UI.TEXT, UI.font_title)
	t.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	t.custom_minimum_size.x = 100
	tv.add_child(t)
	tv.add_child(UI.para(L.t("Не только Minecraft: любые игры с игрой по локальной сети — как будто вы в одной комнате."), 13, UI.MUTED))
	r.add_child(tv)
	UI.tappable(p, func(): FX.haptic(12); app.go("net"))
	return p
