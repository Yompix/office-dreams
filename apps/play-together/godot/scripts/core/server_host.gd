class_name ServerHost
extends Node
## Установка и запуск сервера Minecraft: Java (Paper + по желанию Geyser/Floodgate) или Bedrock Dedicated Server.

signal status(text: String)
signal progress(label: String, frac: float)   # frac < 0 — неизвестный размер
signal log_line(text: String)
signal state_changed(state: String)
signal players_changed(players: PackedStringArray)

const FILL := "https://fill.papermc.io/v3/projects/paper"
const GEYSER := "https://download.geysermc.org/v2/projects/%s/versions/latest/builds/latest"
const ADOPTIUM := "https://api.adoptium.net/v3/assets/latest/%d/hotspot?architecture=x64&image_type=jre&os=%s&vendor=eclipse"
const BEDROCK_LINKS := "https://net-secondary.web.minecraft-services.net/api/v1.0/download/links"
const JAVA_PORT := 25565
const BEDROCK_PORT := 19132

var edition := "java"
var opts := {}
var state := "idle"     # idle, installing, starting, running, stopping, stopped, error
var last_error := ""
var players: PackedStringArray = []
var proc: ManagedProcess
var started_msec := 0
var _stop_deadline := 0
var _installed := {}


func _process(_d: float) -> void:
	if proc:
		proc.poll()
	if state == "stopping" and _stop_deadline > 0 and Time.get_ticks_msec() > _stop_deadline:
		_stop_deadline = 0
		log_line.emit("[Играем вместе] Сервер не остановился вовремя — завершаем принудительно.")
		proc.kill()


func server_dir() -> String:
	return Cfg.sub("servers/" + edition)


func _set_state(s: String) -> void:
	state = s
	state_changed.emit(s)


func _fail(msg: String) -> bool:
	last_error = msg
	status.emit("Ошибка: " + msg)
	log_line.emit("[Играем вместе] Ошибка: " + msg)
	_set_state("error")
	return false


func _install_info() -> Dictionary:
	var p := server_dir().path_join("igraem-install.json")
	if FileAccess.file_exists(p):
		var d: Variant = JSON.parse_string(FileAccess.get_file_as_string(p))
		if d is Dictionary:
			return d
	return {}


func _save_install_info(d: Dictionary) -> void:
	var f := FileAccess.open(server_dir().path_join("igraem-install.json"), FileAccess.WRITE)
	f.store_string(JSON.stringify(d, "  "))


# ===================================================================== установка

## Установить/обновить и запустить. o: world, version, max_players, difficulty, gamemode, online_mode, crossplay, memory_mb
func host(ed: String, o: Dictionary) -> bool:
	if state in ["installing", "starting", "running", "stopping"]:
		return false
	edition = ed
	opts = o
	players = []
	players_changed.emit(players)
	_set_state("installing")
	var ok: bool = (await _install_java()) if edition == "java" else (await _install_bedrock())
	if not ok:
		return false
	return _launch()


func _dl(label: String, url: String, dest: String, checksum := {}) -> bool:
	status.emit(label)
	var res: Dictionary = await Net.download(url, dest, checksum, func(done: int, total: int):
		progress.emit(label, float(done) / total if total > 0 else -1.0))
	progress.emit(label, 1.0)
	if not res.ok:
		return _fail("%s — %s" % [label, res.error])
	return true


func _install_java() -> bool:
	var dir := server_dir()
	var info := _install_info()
	# --- Paper
	status.emit("Узнаём последнюю версию Paper…")
	var version: String = opts.get("version", "latest")
	if version == "" or version == "latest":
		var pr: Dictionary = await Net.get_json(FILL)
		if not pr.ok:
			return _offline_or_fail(info, "не удалось получить список версий Paper: " + pr.error)
		version = _latest_paper_version(pr.data)
	var vr: Dictionary = await Net.get_json(FILL + "/versions/" + version)
	if not vr.ok:
		return _offline_or_fail(info, "версия %s не найдена у Paper: %s" % [version, vr.error])
	var java_min: int = int(vr.data.get("version", {}).get("java", {}).get("version", {}).get("minimum", 21))
	var br: Dictionary = await Net.get_json(FILL + "/versions/%s/builds" % version)
	if not br.ok or not (br.data is Array) or br.data.is_empty():
		return _offline_or_fail(info, "нет сборок Paper для %s" % version)
	var build: Dictionary = br.data[0]
	for b in br.data:
		if b.get("channel", "") == "STABLE":
			build = b
			break
	var dl: Dictionary = build.get("downloads", {}).get("server:default", {})
	if dl.is_empty():
		return _fail("у сборки Paper нет файла сервера")
	var jar := dir.path_join(dl.name)
	if not FileAccess.file_exists(jar) or info.get("paper_sha256", "") != dl.checksums.sha256:
		if not await _dl("Скачиваем сервер Paper %s (сборка %d)" % [version, build.id], dl.url, jar, {"sha256": dl.checksums.sha256}):
			return false
		if info.get("paper_jar", "") != "" and info.paper_jar != dl.name:
			DirAccess.remove_absolute(dir.path_join(info.paper_jar))
	info.paper_jar = dl.name
	info.paper_sha256 = dl.checksums.sha256
	info.mc_version = version
	info.paper_build = build.id
	info.java_min = java_min
	# --- Java
	var java := await ensure_java(java_min)
	if java == "":
		return false
	info.java = java
	# --- Кроссплей
	var plugins := dir.path_join("plugins")
	DirAccess.make_dir_recursive_absolute(plugins)
	if opts.get("crossplay", false):
		for p in [["geyser", "Geyser-Spigot.jar"], ["floodgate", "floodgate-spigot.jar"]]:
			status.emit("Проверяем %s…" % p[1])
			var gr: Dictionary = await Net.get_json(GEYSER % p[0])
			var dest := plugins.path_join(p[1])
			if not gr.ok:
				if FileAccess.file_exists(dest):
					continue
				return _fail("не удалось узнать версию %s: %s" % [p[1], gr.error])
			var d: Dictionary = gr.data.downloads.spigot
			var url := "https://download.geysermc.org/v2/projects/%s/versions/%s/builds/%d/downloads/spigot" % [p[0], gr.data.version, int(gr.data.build)]
			if info.get(p[0] + "_sha256", "") != d.sha256 or not FileAccess.file_exists(dest):
				if not await _dl("Скачиваем %s %s" % [p[1].get_basename(), gr.data.version], url, dest, {"sha256": d.sha256}):
					return false
			info[p[0] + "_sha256"] = d.sha256
			info[p[0] + "_version"] = "%s-%d" % [gr.data.version, int(gr.data.build)]
	else:
		for f in ["Geyser-Spigot.jar", "floodgate-spigot.jar"]:
			if FileAccess.file_exists(plugins.path_join(f)):
				DirAccess.rename_absolute(plugins.path_join(f), plugins.path_join(f + ".off"))
	if opts.get("crossplay", false):
		for f in ["Geyser-Spigot.jar", "floodgate-spigot.jar"]:
			if FileAccess.file_exists(plugins.path_join(f + ".off")):
				DirAccess.remove_absolute(plugins.path_join(f + ".off"))
	# --- EULA и настройки
	if not opts.get("eula", false):
		return _fail("нужно принять лицензионное соглашение Minecraft (EULA)")
	var f := FileAccess.open(dir.path_join("eula.txt"), FileAccess.WRITE)
	f.store_string("# Принято пользователем в приложении «Играем вместе»: https://aka.ms/MinecraftEULA\neula=true\n")
	f.close()
	write_properties(dir.path_join("server.properties"), {
		"motd": opts.get("world", "Мир") + " — Играем вместе",
		"level-name": _safe_name(opts.get("world", "world")),
		"max-players": str(opts.get("max_players", 10)),
		"difficulty": opts.get("difficulty", "normal"),
		"gamemode": opts.get("gamemode", "survival"),
		"online-mode": "true" if opts.get("online_mode", true) else "false",
		"server-port": str(JAVA_PORT),
		"enable-status": "true",
		"spawn-protection": "0",
	})
	_save_install_info(info)
	_installed = info
	return true


func _offline_or_fail(info: Dictionary, msg: String) -> bool:
	# Без интернета запускаем то, что уже скачано.
	if info.get("paper_jar", "") != "" and FileAccess.file_exists(server_dir().path_join(info.paper_jar)) and info.get("java", "") != "" and FileAccess.file_exists(info.java):
		log_line.emit("[Играем вместе] Нет связи с сайтом обновлений, запускаем уже скачанную версию.")
		_installed = info
		if opts.get("eula", false):
			return true
	return _fail(msg)


static func _latest_paper_version(data: Dictionary) -> String:
	var groups: Dictionary = data.get("versions", {})
	for g in groups.keys():   # группы идут от новых к старым
		for v in groups[g]:
			var s := String(v)
			if not (s.contains("-") or s.contains("pre") or s.contains("rc") or s.contains("snapshot")):
				return s
	return "1.21.11"


static func paper_versions(data: Dictionary) -> PackedStringArray:
	var out := PackedStringArray()
	for g in data.get("versions", {}).keys():
		for v in data.versions[g]:
			var s := String(v)
			if not (s.contains("-") or s.contains("pre") or s.contains("rc")):
				out.append(s)
	return out


static func _safe_name(s: String) -> String:
	var r := ""
	for c in s.strip_edges():
		r += c if not ("\\/:*?\"<>|".contains(c)) else "_"
	return r if r != "" else "world"


## Найти Java нужной версии (своя → системная) или скачать Temurin JRE.
func ensure_java(major: int) -> String:
	major = max(major, 21)
	var rt := Cfg.sub("runtime").path_join("jre-%d" % major)
	var exe := rt.path_join("bin/java.exe" if Cfg.is_windows() else "bin/java")
	if FileAccess.file_exists(exe):
		return exe
	var sys_java := _system_java(major)
	if sys_java != "":
		log_line.emit("[Играем вместе] Используем установленную Java: " + sys_java)
		return sys_java
	status.emit("Ищем Java %d (Eclipse Temurin)…" % major)
	var os_name := "windows" if Cfg.is_windows() else ("mac" if OS.get_name() == "macOS" else "linux")
	var r: Dictionary = await Net.get_json(ADOPTIUM % [major, os_name])
	if not r.ok or not (r.data is Array) or r.data.is_empty():
		_fail("не удалось найти Java %d на adoptium.net: %s" % [major, r.get("error", "")])
		return ""
	var pkg: Dictionary = r.data[0].binary.package
	var arc := Cfg.sub("downloads").path_join(pkg.name)
	if not await _dl("Скачиваем Java %d (%s)" % [major, r.data[0].version.semver], pkg.link, arc, {"sha256": pkg.checksum}):
		return ""
	status.emit("Распаковываем Java…")
	await get_tree().process_frame
	var err := Archive.unzip(arc, rt, 1) if pkg.name.ends_with(".zip") else Archive.untar_gz(arc, rt, 1)
	DirAccess.remove_absolute(arc)
	if err != "" or not FileAccess.file_exists(exe):
		_fail("не удалось распаковать Java: " + err)
		return ""
	return exe


func _system_java(major: int) -> String:
	var out := []
	var home := OS.get_environment("JAVA_HOME")
	var cands := []
	if home != "":
		cands.append(home.path_join("bin/java.exe" if Cfg.is_windows() else "bin/java"))
	cands.append("java")
	for c in cands:
		out.clear()
		if OS.execute(c, ["-version"], out, true) != 0 or out.is_empty():
			continue
		var txt := String(out[0])
		var re := RegEx.create_from_string("version \"(\\d+)")
		var m := re.search(txt)
		if m and int(m.get_string(1)) >= major:
			return c
	return ""


func _install_bedrock() -> bool:
	var dir := server_dir()
	var info := _install_info()
	var exe := dir.path_join("bedrock_server.exe" if Cfg.is_windows() else "bedrock_server")
	status.emit("Узнаём последнюю версию Bedrock Dedicated Server…")
	var r: Dictionary = await Net.get_json(BEDROCK_LINKS)
	var url := ""
	if r.ok:
		for l in r.data.get("result", {}).get("links", []):
			if l.downloadType == ("serverBedrockWindows" if Cfg.is_windows() else "serverBedrockLinux"):
				url = l.downloadUrl
	if url == "":
		if FileAccess.file_exists(exe):
			log_line.emit("[Играем вместе] Нет связи с minecraft.net, запускаем уже скачанную версию.")
		else:
			return _fail("не удалось получить ссылку на Bedrock Dedicated Server: " + r.get("error", "ссылка не найдена"))
	elif info.get("bedrock_url", "") != url or not FileAccess.file_exists(exe):
		if not opts.get("eula", false):
			return _fail("нужно принять условия Minecraft (EULA и политику конфиденциальности)")
		var zip := Cfg.sub("downloads").path_join(url.get_file())
		if not await _dl("Скачиваем Bedrock Dedicated Server " + url.get_file().get_basename().trim_prefix("bedrock-server-"), url, zip):
			return false
		status.emit("Распаковываем сервер…")
		await get_tree().process_frame
		var err := Archive.unzip(zip, dir, 0, PackedStringArray(["server.properties", "allowlist.json", "permissions.json"]))
		DirAccess.remove_absolute(zip)
		if err != "":
			return _fail(err)
		if not Cfg.is_windows():
			FileAccess.set_unix_permissions(exe, 493)
		info.bedrock_url = url
		info.bedrock_version = url.get_file().get_basename().trim_prefix("bedrock-server-")
	write_properties(dir.path_join("server.properties"), {
		"server-name": opts.get("world", "Мир") + " — Играем вместе",
		"level-name": _safe_name(opts.get("world", "world")),
		"max-players": str(opts.get("max_players", 10)),
		"difficulty": opts.get("difficulty", "normal"),
		"gamemode": opts.get("gamemode", "survival"),
		"server-port": str(opts.get("port", BEDROCK_PORT)),
		"server-portv6": str(int(opts.get("port", BEDROCK_PORT)) + 1),
		"online-mode": "true",
		"allow-cheats": "false",
		"enable-lan-visibility": "true",
	}, false)
	_save_install_info(info)
	_installed = info
	return true


## Обновить ключи в server.properties, сохраняя остальные строки.
static func write_properties(path: String, values: Dictionary, java_escape := true) -> void:
	var lines := PackedStringArray()
	var left := values.duplicate()
	if FileAccess.file_exists(path):
		for l in FileAccess.get_file_as_string(path).split("\n"):
			var s := l.strip_edges(false, true)
			var eq := s.find("=")
			if eq > 0 and not s.begins_with("#"):
				var k := s.substr(0, eq)
				if left.has(k):
					s = "%s=%s" % [k, _esc(left[k]) if java_escape else str(left[k])]
					left.erase(k)
			lines.append(s)
	while lines.size() > 0 and lines[lines.size() - 1] == "":
		lines.remove_at(lines.size() - 1)
	for k in left:
		lines.append("%s=%s" % [k, _esc(left[k]) if java_escape else str(left[k])])
	var f := FileAccess.open(path, FileAccess.WRITE)
	f.store_string("\n".join(lines) + "\n")
	f.close()


static func _esc(v: Variant) -> String:
	# server.properties — формат Java Properties: не-ASCII как \uXXXX
	var s := str(v)
	var out := ""
	for c in s:
		var u := c.unicode_at(0)
		out += c if u < 128 else "\\u%04x" % u
	return out


static func read_properties(path: String) -> Dictionary:
	var d := {}
	if FileAccess.file_exists(path):
		for l in FileAccess.get_file_as_string(path).split("\n"):
			var eq := l.find("=")
			if eq > 0 and not l.begins_with("#"):
				d[l.substr(0, eq).strip_edges()] = l.substr(eq + 1).strip_edges()
	return d


# ===================================================================== запуск

func _launch() -> bool:
	_set_state("starting")
	var dir := server_dir()
	proc = ManagedProcess.new()
	proc.line.connect(_on_line)
	proc.exited.connect(_on_exit)
	var err := ""
	if edition == "java":
		var mem: int = int(opts.get("memory_mb", 2048))
		var args := PackedStringArray(["-Xms%dM" % min(mem, 1024), "-Xmx%dM" % mem,
			"-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8",
			"-Dterminal.jline=false", "-Dterminal.ansi=false", "-DPaper.IgnoreJavaVersion=false",
			"-jar", _installed.paper_jar, "--nogui"])
		status.emit("Запускаем сервер Java…")
		err = proc.start(_installed.java, args, dir)
	else:
		if not Cfg.is_windows():
			OS.set_environment("LD_LIBRARY_PATH", dir)
		status.emit("Запускаем сервер Bedrock…")
		err = proc.start(dir.path_join("bedrock_server.exe" if Cfg.is_windows() else "bedrock_server"), PackedStringArray(), dir)
	if err != "":
		return _fail(err)
	started_msec = Time.get_ticks_msec()
	log_line.emit("[Играем вместе] Сервер запущен (процесс %d). Папка: %s" % [proc.pid, dir])
	return true


static var _re_join := RegEx.create_from_string("^\\[[^\\]]*\\]:? ?(?:\\[[^\\]]*\\]:? )?([\\.\\w]{1,32}) joined the game")
static var _re_left := RegEx.create_from_string("^\\[[^\\]]*\\]:? ?(?:\\[[^\\]]*\\]:? )?([\\.\\w]{1,32}) left the game")
static var _re_bconn := RegEx.create_from_string("Player connected: ([^,]+),")
static var _re_bdisc := RegEx.create_from_string("Player disconnected: ([^,]+),")


func _on_line(t: String) -> void:
	log_line.emit(t)
	if state == "starting":
		if (edition == "java" and t.contains("Done (")) or (edition == "bedrock" and t.contains("Server started.")):
			_set_state("running")
			status.emit("Сервер работает")
	var m := _re_join.search(t) if edition == "java" else _re_bconn.search(t)
	if m:
		var n := m.get_string(1).strip_edges()
		if not n in players:
			players.append(n)
			players_changed.emit(players)
		return
	m = _re_left.search(t) if edition == "java" else _re_bdisc.search(t)
	if m:
		var n := m.get_string(1).strip_edges()
		var i := players.find(n)
		if i >= 0:
			players.remove_at(i)
			players_changed.emit(players)
	if edition == "java" and t.contains("You need to agree to the EULA"):
		last_error = "сервер требует принять EULA"


func _on_exit(code: int) -> void:
	players = []
	players_changed.emit(players)
	var was := state
	_stop_deadline = 0
	log_line.emit("[Играем вместе] Сервер завершился (код %d)." % code)
	if was == "stopping":
		_set_state("stopped")
		status.emit("Сервер остановлен, мир сохранён")
	else:
		var hint := ""
		if edition == "java" and Time.get_ticks_msec() - started_msec < 15000:
			hint = " Возможно, порт 25565 уже занят другим сервером или не хватает памяти."
		_fail("сервер неожиданно остановился (код %d).%s Подробности — в журнале." % [code, hint])


func command(cmd: String) -> void:
	if proc and proc.running:
		proc.write_line(cmd)


func stop() -> void:
	if not proc or not proc.running:
		_set_state("stopped")
		return
	_set_state("stopping")
	status.emit("Сохраняем мир и останавливаем сервер…")
	proc.write_line("stop")
	_stop_deadline = Time.get_ticks_msec() + 45000


func restart() -> void:
	if proc and proc.running:
		stop()
		while proc.running:
			await get_tree().process_frame
	await host(edition, opts)


func is_alive() -> bool:
	return proc != null and proc.running


func world_dirs() -> PackedStringArray:
	var name := _safe_name(opts.get("world", "world"))
	if edition == "java":
		var out := PackedStringArray()
		for s in ["", "_nether", "_the_end"]:
			if DirAccess.dir_exists_absolute(server_dir().path_join(name + s)):
				out.append(name + s)
		return out
	return PackedStringArray(["worlds/" + name]) if DirAccess.dir_exists_absolute(server_dir().path_join("worlds/" + name)) else PackedStringArray()


func world_path() -> String:
	var name := _safe_name(opts.get("world", "world"))
	var p := server_dir().path_join(name if edition == "java" else "worlds/" + name)
	return p if DirAccess.dir_exists_absolute(p) else server_dir()


## Резервная копия мира в zip. Возвращает путь или "" (ошибка — в last_error).
func backup() -> String:
	var dirs := world_dirs()
	if dirs.is_empty():
		last_error = "мир ещё не создан — запустите сервер хотя бы раз"
		return ""
	if is_alive():
		if edition == "java":
			command("save-off")
			command("save-all flush")
		else:
			command("save hold")
		await get_tree().create_timer(3.0).timeout
	var stamp := Time.get_datetime_string_from_system().replace(":", "-").replace("T", "_")
	var out := Cfg.sub("backups").path_join("%s-%s-%s.zip" % [edition, _safe_name(opts.get("world", "world")), stamp])
	var err := Archive.zip_dirs(server_dir(), dirs, out)
	if is_alive():
		command("save-on" if edition == "java" else "save resume")
	if err != "":
		last_error = err
		return ""
	return out


## Проверка обновлений серверов (для экрана настроек).
func check_updates() -> PackedStringArray:
	var out := PackedStringArray()
	var saved := edition
	edition = "java"
	var ji := _install_info()
	var pr: Dictionary = await Net.get_json(FILL)
	if pr.ok:
		var v := _latest_paper_version(pr.data)
		var br: Dictionary = await Net.get_json(FILL + "/versions/%s/builds/latest" % v)
		var latest := "Paper %s сборка %s" % [v, str(br.data.get("id", "?")) if br.ok else "?"]
		if ji.has("paper_build"):
			var same: bool = br.ok and ji.get("mc_version", "") == v and int(ji.paper_build) == int(br.data.get("id", -1))
			out.append("Java: установлен Paper %s сборка %d — %s" % [ji.mc_version, int(ji.paper_build), "это последняя версия" if same else "доступна " + latest + " (обновится при следующем запуске)"])
		else:
			out.append("Java: ещё не установлен. Последняя версия — " + latest)
	else:
		out.append("Java: не удалось проверить (" + pr.error + ")")
	var gr: Dictionary = await Net.get_json(GEYSER % "geyser")
	if gr.ok:
		var gv := "%s-%d" % [gr.data.version, int(gr.data.build)]
		out.append("Кроссплей (Geyser): " + ("установлен %s, последняя %s" % [ji.geyser_version, gv] if ji.has("geyser_version") else "последняя версия " + gv))
	edition = "bedrock"
	var bi := _install_info()
	var r: Dictionary = await Net.get_json(BEDROCK_LINKS)
	if r.ok:
		var lv := ""
		for l in r.data.get("result", {}).get("links", []):
			if l.downloadType == ("serverBedrockWindows" if Cfg.is_windows() else "serverBedrockLinux"):
				lv = String(l.downloadUrl).get_file().get_basename().trim_prefix("bedrock-server-")
		if bi.has("bedrock_version"):
			out.append("Bedrock: установлен %s — %s" % [bi.bedrock_version, "это последняя версия" if bi.bedrock_version == lv else "доступна " + lv + " (обновится при следующем запуске)"])
		else:
			out.append("Bedrock: ещё не установлен. Последняя версия — " + lv)
	else:
		out.append("Bedrock: не удалось проверить (" + r.error + ")")
	edition = saved
	return out
