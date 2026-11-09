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
	var v := UI.vbox(14)
	v.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	add_child(v)
	var head := UI.hbox(12)
	var hv := UI.vbox(2)
	hv.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	hv.add_child(UI.h1("Проверка системы"))
	hv.add_child(UI.para("Проверим, всё ли готово, чтобы создать мир на этом компьютере. Ничего не меняем и не устанавливаем.", 15, UI.MUTED))
	head.add_child(hv)
	var again := UI.button("Проверить ещё раз", "secondary", run)
	again.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	head.add_child(again)
	v.add_child(head)
	_list = UI.vbox(10)
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
	_add("os", "info", "%s %s" % [OS.get_name(), OS.get_version()], "Оперативная память: %.1f ГБ, свободно %.1f ГБ. Серверу Java нужно 1,5–4 ГБ, Bedrock — около 1 ГБ." % [phys_gb, free_gb] if phys_gb > 0 else "Объём памяти узнать не удалось.")
	if phys_gb > 0 and phys_gb < 4:
		_add("mem", "warn", "Мало памяти", "Для Java-сервера закройте браузер и лишние программы или выберите Bedrock.")
	# --- папки
	var data := Cfg.data_dir()
	var root := Cfg.server_root()
	var dw := Cfg._writable(Cfg.sub(""))
	_add("data", "ok" if dw else "bad", "Папка данных", data + ("" if dw else "\nНет прав на запись — выберите другую папку в «Настройках»."))
	if win and not Cfg.is_ascii_path(data):
		if root != data:
			_add("ascii", "ok", "Путь с русскими буквами — обошли", "Java на Windows не всегда понимает такие пути, поэтому серверы и Java лежат в " + root)
		else:
			_add("ascii", "warn", "Путь с русскими буквами", "Java на английской Windows может не запуститься из такой папки. Если сервер не стартует — выберите в «Настройках» папку вроде C:\\LANTERN.")
	var d := DirAccess.open(root)
	if d:
		var left_gb: float = d.get_space_left() / 1073741824.0
		if left_gb > 0:
			_add("disk", "ok" if left_gb >= 3 else ("warn" if left_gb >= 1 else "bad"), "Свободно на диске: %.1f ГБ" % left_gb, "Нужно 1–3 ГБ: сервер, Java и миры." if left_gb >= 1 else "Освободите место или выберите другой диск в «Настройках».")
	# --- Java
	var java := _local_java()
	if java != "":
		_add("java", "ok", "Java установлена", java)
	else:
		var sys := _system_java_version()
		if sys >= 21:
			_add("java", "ok", "Найдена Java %d на компьютере" % sys, "LANTERN всё равно скачает свою, если миру нужна версия новее.")
		else:
			_add("java", "info", "Java скачается сама", "При первом запуске мира LANTERN скачает Eclipse Temurin (~50 МБ) в свою папку. Устанавливать ничего не нужно.")
	# --- порты
	_add_port("tcp", 25565, "Java")
	_add_port("udp", 19132, "Bedrock")
	# --- интернет (параллельно)
	var checks := [
		["paper", "Сайт PaperMC (серверы Java)", "https://fill.papermc.io/v3/projects/paper"],
		["adoptium", "Сайт Adoptium (Java)", "https://api.adoptium.net/v3/info/available_releases"],
		["relay", "Сервер LANTERN (комнаты по коду)", Social.http_base() + "/healthz"],
	]
	for c in checks:
		var row := _add(c[0], "info", c[1], "проверяем…")
		_net_check(row, c[0], c[2])
	# --- брандмауэр
	if win:
		_add("firewall", "info", "Брандмауэр Windows", "При первом запуске мира Windows может спросить разрешение для «OpenJDK Platform binary» (Java) или «bedrock_server». Друзьям по коду через LANTERN это не нужно — всё идёт через сам LANTERN. «Разрешить» нужно, только если друзья заходят из той же Wi-Fi сети по адресу компьютера. Права администратора LANTERN не просит.")
		var fb := UI.button("Открыть настройки брандмауэра", "ghost", func(): OS.create_process("control", ["firewall.cpl"]))
		fb.size_flags_horizontal = Control.SIZE_SHRINK_BEGIN
		_list.add_child(fb)
	elif OS.get_name() == "Linux":
		_add("firewall", "info", "Брандмауэр", "Для игры по коду ничего открывать не нужно. Для друзей в той же сети: TCP 25565 (Java) и UDP 19132 (Bedrock).")
	_running = false


func _add(key: String, ok: String, title: String, text: String) -> Control:
	results.append({"key": key, "ok": ok, "title": title, "text": text})
	var p := UI.panel(14, UI.CARD, 14)
	var row := UI.hbox(12)
	p.add_child(row)
	var col: Color = {"ok": UI.JAVA, "warn": UI.YELLOW, "bad": UI.RED}.get(ok, UI.BEDROCK)
	var icon := UI.label({"ok": "✓", "warn": "!", "bad": "✗"}.get(ok, "i"), 20, col, UI.font_black)
	icon.custom_minimum_size.x = 26
	icon.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	row.add_child(icon)
	var v := UI.vbox(2)
	v.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	v.add_child(UI.label(title, 16, UI.TEXT, UI.font_bold))
	var t := UI.para(text, 14, UI.MUTED)
	v.add_child(t)
	row.add_child(v)
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
	icon.add_theme_color_override("font_color", {"ok": UI.JAVA, "warn": UI.YELLOW, "bad": UI.RED}.get(ok, UI.BEDROCK))
	(row.get_meta("text") as Label).text = text
	var i: int = row.get_meta("idx")
	if i < results.size():
		results[i].ok = ok
		results[i].text = text


func _net_check(row: Control, key: String, url: String) -> void:
	var r: Dictionary = await Net.get_json(url)
	if r.ok:
		_set_row(row, "ok", "доступен")
	else:
		var why: String = r.error
		_set_row(row, "bad" if key != "relay" else "warn", "недоступен: %s%s" % [why, " — проверьте интернет или антивирус" if key != "relay" else " — по коду играть не получится, по адресу в своей сети можно"])


func _add_port(kind: String, port: int, what: String) -> void:
	var ours: bool = app != null and app.server.is_alive() and ((kind == "tcp" and app.server.edition == "java") or (kind == "udp" and (app.server.edition == "bedrock" or app.server.opts.get("crossplay", false))))
	if ours:
		_add("port_%d" % port, "ok", "Порт %d (%s)" % [port, what], "Занят вашим запущенным сервером — так и должно быть.")
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
		_add("port_%d" % port, "ok", "Порт %d свободен (%s)" % [port, what], "Сервер сможет его занять.")
	else:
		_add("port_%d" % port, "warn", "Порт %d занят (%s)" % [port, what], "Закройте другой сервер Minecraft или открытый «по сети» мир, иначе сервер не запустится.")


static func _local_java() -> String:
	var rt := Cfg.server_root().path_join("runtime")
	var d := DirAccess.open(rt)
	if d == null:
		return ""
	for sub in d.get_directories():
		var exe := rt.path_join(sub).path_join("bin/java.exe" if Cfg.is_windows() else "bin/java")
		if FileAccess.file_exists(exe):
			return "%s (своя копия LANTERN)" % sub.replace("jre-", "Java ")
	return ""


static func _system_java_version() -> int:
	var out := []
	if OS.execute("java", ["-version"], out, true) != 0 or out.is_empty():
		return 0
	var m := RegEx.create_from_string("version \"(\\d+)").search(String(out[0]))
	return int(m.get_string(1)) if m else 0
