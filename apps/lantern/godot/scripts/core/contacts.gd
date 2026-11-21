class_name Contacts
extends RefCounted
## 2.0: «Связь с автором» — Discord владельца для предложений и проблем. ОДНО место для адреса.
## Пока пусто — кнопка Discord скрыта. Владелец пришлёт ссылку — вписать её в DISCORD (одна строка),
## например "https://discord.gg/…" или "https://discord.com/users/…". Адрес здесь не придумывается.

const DISCORD := ""


static func has_any() -> bool:
	return discord() != ""


static func discord() -> String:
	var d := DISCORD.strip_edges()
	return d if d.begins_with("https://discord.") else ""


## Блок «Написать автору в Discord»: открыть / скопировать ссылку. Ссылки нет — null.
static func block(app: Control, _about := "") -> Control:
	if not has_any():
		return null
	var v := UI.vbox(8)
	v.add_child(UI.caption(L.t("Предложения и проблемы — автору")))
	var r := UI.hbox(8)
	var d := UI.button(L.t("Открыть Discord"), "secondary", func(): UI.open(discord()), "friends")
	d.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	d.clip_text = true
	r.add_child(d)
	r.add_child(UI.icon_button("copy", func():
		UI.copy(discord())
		if app.has_method("toast"):
			app.toast(L.t("Ссылка скопирована")), L.t("Скопировать ссылку")))
	v.add_child(r)
	return v


## Лист «Связь с автором» (Настройки): отчёт с журналом + Discord, если ссылка задана.
static func sheet(app: Control) -> void:
	var v: VBoxContainer = Overlays.sheet(app, L.t("Связь с автором"), L.t("Предложения, идеи и проблемы — всё читаем."))
	var rep := UI.button(L.t("Сообщить о проблеме"), "primary", func(): Overlays.close_sheet(app); Report.dialog(app), "info")
	rep.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	rep.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	v.add_child(rep)
	v.add_child(Promo.other_row())
	var b := block(app)
	if b:
		v.add_child(b)
	else:
		v.add_child(UI.para(L.t("Отчёт вместе с журналом получит автор LANTERN."), 13, UI.DIM))
