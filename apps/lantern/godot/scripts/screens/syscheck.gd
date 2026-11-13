extends ScrollContainer
## «Проверка системы»: можно ли на этом компьютере запустить сервер — Java, папки, порты, интернет, брандмауэр.
## Ничего не меняет и ничего не запускает от администратора (только читает).

var app
var arg

var _list: VBoxContainer
var _running := false
var results := []     # [{key, ok: "ok"|"warn"|"bad"|"info", title, text}] — для автотестов


func _ready() -> void:
	horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	var v := UI.vbox(10)
	v.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	add_child(v)
	app.set_title(L.t("Проверка системы"), L.t("Можно ли создать мир на этом компьютере"))
	app.set_actions([UI.icon_button("refresh", run, L.t("Проверить ещё раз"))])
	v.add_child(UI.para(L.t("Проверим, всё ли готово, чтобы создать мир на этом компьютере. Ничего не меняем и не устанавливаем."), 14, UI.MUTED))
	_list = UI.vbox(8)
	v.add_child(_list)
	run()


func run() -> void:
	if _running:
		return
	_running = true
	results = []
	for c in _list.get_children():
		c.queue_free()
	var win := Cfg.is_windows()
	# --- система и память
	var mem := OS.get_memory_info()
	var phys_gb: float = float(mem.get("physical", 0)) / 1073741824.0
	var free_gb: float = float(mem.get("available", mem.get("free", 0))) / 1073741824.0
	_add("os", "info", "%s %s" % [OS.get_name(), OS.get_version()], L.t("Оперативная память: %.1f ГБ, свободно %.1f ГБ. Серверу Java нужно 1,5–4 ГБ, Bedrock — около 1 ГБ.") % [phys_gb, free_gb] if phys_gb > 0 else L.t("Объём памяти узнать не удалось."))
	if phys_gb > 0 and phys_gb < 4:
		_add("mem", "warn", L.t("Мало памяти"), L.t("Для Java-сервера закройте браузер и лишние программы или выберите Bedrock."))
	# --- папки
	var data := Cfg.data_dir()
	var root := Cfg.server_root()
	var dw := Cfg._writable(Cfg.sub(""))
	_add("data", "ok" if dw else "bad", L.t("Папка данных"), data + ("" if dw else L.t("\nНет прав на запись — выберите другую папку в «Настройках».")))
	if win and not Cfg.is_ascii_path(data):
		if root != data:
			_add("ascii", "ok", L.t("Путь с русскими буквами — обошли"), L.t("Java на Windows не всегда понимает такие пути, поэтому серверы и Java лежат в ") + root)
		else:
			_add("ascii", "warn", L.t("Путь с русскими буквами"), L.t("Java на английской Windows может не запуститься из такой папки. Если сервер не стартует — выберите в «Настройках» папку вроде C:\\LANTERN."))
	var d := DirAccess.open(root)
	if d:
		var left_gb: float = d.get_space_left() / 1073741824.0
		if left_gb > 0:
			_add("disk", "ok" if left_gb >= 3 else ("warn" if left_gb >= 1 else "bad"), L.t("Свободно на диске: %.1f ГБ") % left_gb, L.t("Нужно 1–3 ГБ: сервер, Java и миры.") if left_gb >= 1 else L.t("Освободите место или выберите другой диск в «Настройках»."))
	# --- Java
	var java := _local_java()
	if java != "":
		_add("java", "ok", L.t("Java установлена"), java)
	else:
		var sys := _system_java_version()
		if sys >= 21:
			_add("java", "ok", L.t("Найдена Java %d на компьютере") % sys, L.t("LANTERN всё равно скачает свою, если миру нужна версия новее."))
		else:
			_add("java", "info", L.t("Java скачается сама"), L.t("При первом запуске мира LANTERN скачает Eclipse Temurin (~50 МБ) в свою папку. Устанавливать ничего не нужно."))
	# --- порты
	_add_port("tcp", 25565, "Java")
	_add_port("udp", 19132, "Bedrock")
	# --- интернет (параллельно)
	var checks := [
		["paper", L.t("Сайт PaperMC (серверы Java)"), "https://fill.papermc.io/v3/projects/paper"],
		["adoptium", L.t("Сайт Adoptium (Java)"), "https://api.adoptium.net/v3/info/available_releases"],
		["relay", L.t("Сервер LANTERN (комнаты по коду)"), Social.http_base() + "/healthz"],
	]
	for c in checks:
		var row := _add(c[0], "info", c[1], L.t("проверяем…"))
		_net_check(row, c[0], c[2])
	# --- брандмауэр
	if win:
		_add("firewall", "info", L.t("Брандмауэр Windows"), L.t("При первом запуске мира Windows может спросить разрешение для «OpenJDK Platform binary» (Java) или «bedrock_server». Друзьям по коду через LANTERN это не нужно — всё идёт через сам LANTERN. «Разрешить» нужно, только если друзья заходят из той же Wi-Fi сети по адресу компьютера. Права администратора LANTERN не просит."))
		var fb := UI.button(L.t("Открыть настройки брандмауэра"), "ghost", func(): OS.create_process("control", ["firewall.cpl"]))
		fb.size_flags_horizontal = Control.SIZE_SHRINK_BEGIN
		_list.add_child(fb)
	elif OS.get_name() == "Linux":
		_add("firewall", "info", L.t("Брандмауэр"), L.t("Для игры по коду ничего открывать не нужно. Для друзей в той же сети: TCP 25565 (Java) и UDP 19132 (Bedrock)."))
	_running = false


func _add(key: String, ok: String, title: String, text: String) -> Control:
	results.append({"key": key, "ok": ok, "title": title, "text": text})
	var p := PanelContainer.new()
	p.add_theme_stylebox_override("panel", UI._hpad(UI.glass(1, UI.R_XL, 0), 14, 12))
	var row := UI.hbox(14)
	p.add_child(row)
	var col: Color = {"ok": UI.JAVA, "warn": UI.YELLOW, "bad": UI.RED}.get(ok, UI.BEDROCK)
	var tile := PanelContainer.new()
	tile.custom_minimum_size = Vector2(36, 36)
	tile.size_flags_vertical = Control.SIZE_SHRINK_BEGIN
	tile.add_theme_stylebox_override("panel", UI.box(Color(col, 0.16), 99))
	var icon := UI.label({"ok": "✓", "warn": "!", "bad": "✗"}.get(ok, "i"), 18, col, UI.font_black)
	icon.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	icon.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
	tile.add_child(icon)
	row.add_child(tile)
	var v := UI.vbox(2)
	v.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	var tl := UI.label(title, 15, UI.TEXT, UI.font_bold)
	tl.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	tl.custom_minimum_size.x = 120
	v.add_child(tl)
	var t := UI.para(text, 13, UI.MUTED)
	v.add_child(t)
	row.add_child(v)
	p.set_meta("tile", tile)
	p.set_meta("icon", icon)
	p.set_meta("text", t)
	p.set_meta("idx", results.size() - 1)
	_list.add_child(p)
	return p


func _set_row(row: Control, ok: String, text: String) -> void:
	if not is_instance_valid(row):
		return
	var icon: Label = row.get_meta("icon")
	icon.text = {"ok": "✓", "warn": "!", "bad": "✗"}.get(ok, "i")
	var col: Color = {"ok": UI.JAVA, "warn": UI.YELLOW, "bad": UI.RED}.get(ok, UI.BEDROCK)
	icon.add_theme_color_override("font_color", col)
	(row.get_meta("tile") as PanelContainer).add_theme_stylebox_override("panel", UI.box(Color(col, 0.16), 99))
	(row.get_meta("text") as Label).text = text
	var i: int = row.get_meta("idx")
	if i < results.size():
		results[i].ok = ok
		results[i].text = text


func _net_check(row: Control, key: String, url: String) -> void:
	var r: Dictionary = await Net.get_json(url)
	if r.ok:
		_set_row(row, "ok", L.t("доступен"))
	else:
		var why: String = r.error
		_set_row(row, "bad" if key != "relay" else "warn", L.t("недоступен: %s%s") % [why, L.t(" — проверьте интернет или антивирус") if key != "relay" else L.t(" — по коду играть не получится, по адресу в своей сети можно")])


func _add_port(kind: String, port: int, what: String) -> void:
	var ours: bool = app != null and app.server.is_alive() and ((kind == "tcp" and app.server.edition == "java") or (kind == "udp" and (app.server.edition == "bedrock" or app.server.opts.get("crossplay", false))))
	if ours:
		_add("port_%d" % port, "ok", L.t("Порт %d (%s)") % [port, what], L.t("Занят вашим запущенным сервером — так и должно быть."))
		return
	var free := false
	if kind == "tcp":
		var s := TCPServer.new()
		free = s.listen(port, "*") == OK
		s.stop()
	else:
		var u := PacketPeerUDP.new()
		free = u.bind(port, "*") == OK
		u.close()
	if free:
		_add("port_%d" % port, "ok", L.t("Порт %d свободен (%s)") % [port, what], L.t("Сервер сможет его занять."))
	else:
		_add("port_%d" % port, "warn", L.t("Порт %d занят (%s)") % [port, what], L.t("Закройте другой сервер Minecraft или открытый «по сети» мир, иначе сервер не запустится."))


static func _local_java() -> String:
	var rt := Cfg.server_root().path_join("runtime")
	var d := DirAccess.open(rt)
	if d == null:
		return ""
	for sub in d.get_directories():
		var exe := rt.path_join(sub).path_join("bin/java.exe" if Cfg.is_windows() else "bin/java")
		if FileAccess.file_exists(exe):
			return L.t("%s (своя копия LANTERN)") % sub.replace("jre-", "Java ")
	return ""


static func _system_java_version() -> int:
	var out := []
	if OS.execute("java", ["-version"], out, true) != 0 or out.is_empty():
		return 0
	var m := RegEx.create_from_string("version \"(\\d+)").search(String(out[0]))
	return int(m.get_string(1)) if m else 0
