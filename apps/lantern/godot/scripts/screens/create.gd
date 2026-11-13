class_name Create
extends VBoxContainer
## «Создать игру» (1.8): название → «Создать». Всё остальное — на одной странице «Настройки игры» (в одно нажатие).
## ПК: общий сервер для всех (Java 1.8+ и телефоны/консоли), всё ставится само. Телефон: мир из самого Minecraft.

var app
var arg

var _title: LineEdit
var _eula: CheckBox


func _ready() -> void:
	add_theme_constant_override("separation", 16)
	app.set_title(L.t("Создать игру"), "")
	_title = UI.line_edit(default_title(), L.t("Например: Выживание с друзьями"))
	_title.max_length = 40
	_title.focus_exited.connect(_save)
	add_child(UI.field(L.t("Название игры"), _title))
	add_child(compat_card(not Cfg.mobile()))
	var ed := "java"
	if not Cfg.mobile():
		ed = String(Cfg.get_value("host", "last_edition", "java"))
		_eula = eula_box(ed if ed in ["java", "bedrock"] else "java")
		add_child(_eula)
	var main_btn := UI.button(L.t("Создать"), "primary", _create, "play")
	main_btn.custom_minimum_size.y = 56
	main_btn.add_theme_font_size_override("font_size", 17)
	add_child(main_btn)
	add_child(UI.button(L.t("Настройки игры"), "secondary", func(): _save(); app.go("gamesettings", true), "settings"))
	add_child(UI.para(L.t("Режим, сложность, пароль, правила мира — всё на одной странице. Менять можно и после создания."), 13, UI.DIM))
	if not Cfg.mobile():
		add_child(UI.divider())
		add_child(UI.button(L.t("Открыть друзьям мир из моего Minecraft"), "ghost", func(): _save(); app.go("setup", true, {"own": true, "auto": true}), "globe"))
	FX.stagger(self, 0.02)


static func default_title() -> String:
	var t := String(Cfg.get_value("game", "title", ""))
	if t != "":
		return t
	var nick := String(Social.me.get("nick", "")) if Social.me is Dictionary else ""
	return L.t("Мир %s") % nick if nick != "" else L.t("Наш мир")


## Галочка согласия с EULA (без неё Mojang не разрешает запускать сервер) со ссылкой «Прочитать».
static func eula_box(edition: String) -> CheckBox:
	var c := CheckBox.new()
	c.text = L.t("Я принимаю Лицензионное соглашение Minecraft (EULA)") if edition == "java" else L.t("Я принимаю Лицензионное соглашение Minecraft и Политику конфиденциальности")
	c.button_pressed = bool((Cfg.get_value("host", edition, {}) as Dictionary).get("eula", false))
	c.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	c.custom_minimum_size.x = 200
	c.tooltip_text = "https://aka.ms/MinecraftEULA"
	return c


## Совместимость — в одном месте, простыми словами (и с картинкой из значков).
static func compat_card(pc: bool) -> Control:
	var p := UI.panel(16, Color(1, 1, 1, 0.03))
	var v := UI.vbox(10)
	p.add_child(v)
	var row := UI.flow(14)
	for it in [["desktop", L.t("ПК (Java)")], ["phone", L.t("Телефоны")], ["play", L.t("Консоли")], ["desktop", "Windows (Bedrock)"]]:
		var c := UI.hbox(6)
		c.add_child(UI.icon(it[0], 20, UI.JAVA if pc else UI.BEDROCK))
		c.add_child(UI.label(L.t(it[1]) + " ✓", 15, UI.TEXT, UI.font_bold))
		row.add_child(c)
	v.add_child(row)
	if pc:
		v.add_child(UI.para(L.t("Все в одном мире: Java от 1.8.9 до новейшей, телефоны, Windows и консоли — с актуальной версией Minecraft. Ничего настраивать не нужно."), 14, UI.MUTED))
		v.add_child(UI.button(L.t("Прочитать EULA"), "link", func(): UI.open("https://aka.ms/MinecraftEULA")))
	else:
		v.add_child(UI.para(L.t("Вы создаёте мир в Minecraft на телефоне — зайдут телефоны, Windows (Bedrock) и консоли с той же версией Minecraft. Чтобы зашли и друзья с Java (ПК), создайте игру в LANTERN на компьютере."), 14, UI.MUTED))
	return p


func _save() -> void:
	Cfg.set_value("game", "title", _title.text.strip_edges())


func _create() -> void:
	_save()
	if Cfg.mobile():
		app.go("setup", true, {"own": true, "auto": true, "launch": true})
		return
	var ed := String(Cfg.get_value("host", "last_edition", "java"))
	launch_server(app, ed if ed in ["java", "bedrock"] else "java", _eula)


## Запустить сервер LANTERN с сохранёнными настройками («Создать» здесь и на странице «Настройки игры»).
static func launch_server(app, edition: String, eula: CheckBox) -> void:
	if eula == null or not eula.button_pressed:
		app.toast(L.t("Отметьте согласие с соглашением Minecraft — без него Mojang не разрешает запускать сервер"), UI.YELLOW)
		return
	var o := server_opts(edition)
	o["eula"] = true
	Cfg.set_value("host", edition, o)
	Cfg.set_value("host", "last_edition", edition)
	app.own.stop()
	app.server.host(edition, o)
	app.go("host", false, {"fresh": true})


## Настройки сервера с умолчаниями. «Для всех»: новейший Paper + Via* (Java 1.8+) + Geyser/Floodgate (телефоны, консоли).
## Имя мира (папка) задаётся один раз — при первом создании; потом меняется только название игры.
static func server_opts(edition: String) -> Dictionary:
	var o: Dictionary = (Cfg.get_value("host", edition, {}) as Dictionary).duplicate()
	var g := Cfg.game()
	var title := default_title()
	o.merge({"world": title, "difficulty": "normal", "gamemode": "creative" if g.mode == "creative" else "survival", "online_mode": true,
		"perf": "balance", "memory_auto": not o.has("memory_mb")}, false)
	o["max_players"] = clampi(g.cap, 2, 20)
	o["internet"] = true
	o["public"] = g.public
	o["public_title"] = title
	if edition == "java":
		o.merge({"version": "latest", "crossplay": true, "any_version": true}, false)
	return o


## Совместимость с 1.7 (автотесты): настройки универсального сервера из формы.
static func universal_opts(title: String, mode: String, cap: int, public: bool) -> Dictionary:
	Cfg.set_value("game", "title", title)
	Cfg.set_value("game", "mode", mode)
	Cfg.set_value("game", "cap", cap)
	Cfg.set_value("game", "public", public)
	var o := server_opts("java")
	o["gamemode"] = "creative" if mode == "creative" else "survival"
	o["eula"] = true
	o["version"] = "latest"
	o["crossplay"] = true
	o["any_version"] = true
	Cfg.set_value("host", "java", o)
	Cfg.set_value("host", "last_edition", "java")
	return o


## Свободное имя для нового мира: «Название», «Название 2», …
static func free_world_name(server: ServerHost, title: String) -> String:
	var base := title if title != "" else L.t("Мир")
	var name := base
	var i := 2
	var dir := server.server_dir()
	while DirAccess.dir_exists_absolute(dir.path_join(ServerHost._safe_name(name))) or DirAccess.dir_exists_absolute(dir.path_join("worlds/" + ServerHost._safe_name(name))):
		name = "%s %d" % [base, i]
		i += 1
	return name
