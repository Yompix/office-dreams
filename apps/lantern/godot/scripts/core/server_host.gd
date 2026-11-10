class_name ServerHost
extends Node
## Установка и запуск сервера Minecraft: Java (Paper + по желанию Geyser/Floodgate) или Bedrock Dedicated Server.

signal status(text: String)
signal progress(label: String, frac: float)   # frac < 0 — неизвестный размер
signal log_line(text: String)
signal state_changed(state: String)
signal players_changed(players: PackedStringArray)
signal doctor_changed                            # «доктор»: новые подсказки (issues) или исправления (fixes)

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
var history: PackedStringArray = []
var last_status := ""
var last_progress := ["", 0.0]
var issues: Array = []        # нужно решение хозяина: {id, text, button, action, arg}
var fixes: Array = []         # «Что LANTERN исправил сам»: строки
var _port_conflict := false
var _port_retries := 0
var _auto_restarts := 0


func _log(t: String) -> void:
	history.append(t)
	if history.size() > 1500:
		history = history.slice(history.size() - 1200)
	log_line.emit(t)


func _status(t: String) -> void:
	last_status = t
	status.emit(t)


func _progress(label: String, f: float) -> void:
	last_progress = [label, f]
	progress.emit(label, f)


func _process(_d: float) -> void:
	if proc:
		proc.poll()
	if state == "stopping" and _stop_deadline > 0 and Time.get_ticks_msec() > _stop_deadline:
		_stop_deadline = 0
		_log("[LANTERN] Сервер не остановился вовремя — завершаем принудительно.")
		proc.kill()


func server_dir() -> String:
	return Cfg.server_sub("servers/" + edition)


func _set_state(s: String) -> void:
	state = s
	state_changed.emit(s)


func _fail(msg: String) -> bool:
	last_error = msg
	_status("Ошибка: " + msg)
	_log("[LANTERN] Ошибка: " + msg)
	_set_state("error")
	return false


func _install_info() -> Dictionary:
	var p := server_dir().path_join("lantern-install.json")
	if FileAccess.file_exists(p):
		var d: Variant = JSON.parse_string(FileAccess.get_file_as_string(p))
		if d is Dictionary:
			return d
	return {}


func _save_install_info(d: Dictionary) -> void:
	var f := FileAccess.open(server_dir().path_join("lantern-install.json"), FileAccess.WRITE)
	f.store_string(JSON.stringify(d, "  "))


# ===================================================================== установка

## Установить/обновить и запустить. o: world, version, max_players, difficulty, gamemode, online_mode, crossplay, memory_mb
func host(ed: String, o: Dictionary) -> bool:
	if state in ["installing", "starting", "running", "stopping"]:
		return false
	edition = ed
	if o != opts:
		_auto_restarts = 0
		_port_retries = 0
		issues.clear()
	opts = o
	_last_err = ""
	_port_conflict = false
	_check_memory()
	_check_ports()
	players = []
	players_changed.emit(players)
	_set_state("installing")
	var ok: bool = (await _install_java()) if edition == "java" else (await _install_bedrock())
	if not ok:
		return false
	return _launch()


func _dl(label: String, url: String, dest: String, checksum := {}) -> bool:
	_status(label)
	var res: Dictionary = await Net.download(url, dest, checksum, func(done: int, total: int):
		_progress(label, float(done) / total if total > 0 else -1.0))
	_progress(label, 1.0)
	if not res.ok:
		return _fail("%s — %s" % [label, res.error])
	return true


func _install_java() -> bool:
	var dir := server_dir()
	var info := _install_info()
	# --- Paper
	_status("Узнаём последнюю версию Paper…")
	var version: String = opts.get("version", "latest")
	if opts.get("crossplay", false) and version != "latest":
		# Geyser поддерживает только самую новую версию Java Edition
		_log("[LANTERN] Кроссплей с телефонами работает только на последней версии Minecraft — ставим её.")
		version = "latest"
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
			_status("Проверяем %s…" % p[1])
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
		_geyser_config(plugins.path_join("Geyser-Spigot/config.yml"))
		for f in ["Geyser-Spigot.jar", "floodgate-spigot.jar"]:
			if FileAccess.file_exists(plugins.path_join(f + ".off")):
				DirAccess.remove_absolute(plugins.path_join(f + ".off"))
	# --- EULA и настройки
	if not opts.get("eula", false):
		return _fail("нужно принять лицензионное соглашение Minecraft (EULA)")
	var f := FileAccess.open(dir.path_join("eula.txt"), FileAccess.WRITE)
	f.store_string("# Принято пользователем в приложении LANTERN: https://aka.ms/MinecraftEULA\neula=true\n")
	f.close()
	_geyser_port(dir.path_join("plugins/Geyser-Spigot/config.yml"), bedrock_port())
	write_properties(dir.path_join("server.properties"), {
		"motd": opts.get("world", "Мир") + " — LANTERN",
		"level-name": _safe_name(opts.get("world", "world")),
		"max-players": str(opts.get("max_players", 10)),
		"difficulty": opts.get("difficulty", "normal"),
		"gamemode": opts.get("gamemode", "survival"),
		"online-mode": "true" if opts.get("online_mode", true) else "false",
		"server-port": str(java_port()),
		"enable-status": "true",
		"spawn-protection": "0",
		# белый список — только если хозяин сам включил «Только для своих» (иначе друзей выкидывает «not whitelisted»)
		"white-list": "true" if opts.get("whitelist", false) else "false",
		"enforce-whitelist": "true" if opts.get("whitelist", false) else "false",
	})
	_save_install_info(info)
	_installed = info
	return true


## Geyser 2.12+ по умолчанию слушает только NetherNet (WebRTC). LANTERN передаёт RakNet по UDP 19132,
## поэтому включаем «both»: RakNet на 19132, NetherNet на 19134. Остальные настройки Geyser дополнит сам.
static func _geyser_config(path: String) -> void:
	DirAccess.make_dir_recursive_absolute(path.get_base_dir())
	if not FileAccess.file_exists(path):
		var f := FileAccess.open(path, FileAccess.WRITE)
		f.store_string("bedrock:\n  port: %d\n  webrtc-port: %d\n  transport: both\n" % [BEDROCK_PORT, BEDROCK_PORT + 2])
		return
	var txt := FileAccess.get_file_as_string(path)
	var re_t := RegEx.create_from_string("(?m)^(  transport:)\\s*\\S+")
	var re_w := RegEx.create_from_string("(?m)^(  webrtc-port:)\\s*0\\s*$")
	var out := re_t.sub(txt, "$1 both")
	out = re_w.sub(out, "$1 %d" % (BEDROCK_PORT + 2))
	if out != txt:
		var f := FileAccess.open(path, FileAccess.WRITE)
		f.store_string(out)


func _offline_or_fail(info: Dictionary, msg: String) -> bool:
	# Без интернета запускаем то, что уже скачано.
	if info.get("paper_jar", "") != "" and FileAccess.file_exists(server_dir().path_join(info.paper_jar)) and info.get("java", "") != "" and FileAccess.file_exists(info.java):
		_log("[LANTERN] Нет связи с сайтом обновлений, запускаем уже скачанную версию.")
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


## Имя папки мира: латиница, цифры и «_» (кириллица — транслитом), чтобы сервер не споткнулся о кодировку.
static func _safe_name(s: String) -> String:
	const TR := {"а":"a","б":"b","в":"v","г":"g","д":"d","е":"e","ё":"e","ж":"zh","з":"z","и":"i","й":"y","к":"k","л":"l","м":"m","н":"n","о":"o","п":"p","р":"r","с":"s","т":"t","у":"u","ф":"f","х":"h","ц":"ts","ч":"ch","ш":"sh","щ":"sch","ъ":"","ы":"y","ь":"","э":"e","ю":"yu","я":"ya","і":"i","ї":"yi","є":"ye","ґ":"g"}
	var r := ""
	for c in s.strip_edges():
		var lc := c.to_lower()
		if TR.has(lc):
			var t: String = TR[lc]
			r += t.capitalize() if c != lc and t != "" else t
		elif (lc >= "a" and lc <= "z") or (c >= "0" and c <= "9") or c == "-" or c == "_":
			r += c
		elif c == " ":
			r += "_"
	return r.substr(0, 40) if r != "" else "world"


## Найти Java нужной версии (своя → системная) или скачать Temurin JRE.
func ensure_java(major: int) -> String:
	major = max(major, 21)
	var rt := Cfg.server_sub("runtime").path_join("jre-%d" % major)
	var exe := rt.path_join("bin/java.exe" if Cfg.is_windows() else "bin/java")
	if FileAccess.file_exists(exe):
		return exe
	var sys_java := _system_java(major)
	if sys_java != "":
		_log("[LANTERN] Используем установленную Java: " + sys_java)
		return sys_java
	_status("Ищем Java %d (Eclipse Temurin)…" % major)
	var os_name := "windows" if Cfg.is_windows() else ("mac" if OS.get_name() == "macOS" else "linux")
	var r: Dictionary = await Net.get_json(ADOPTIUM % [major, os_name])
	if not r.ok or not (r.data is Array) or r.data.is_empty():
		_fail("не удалось найти Java %d на adoptium.net: %s" % [major, r.get("error", "")])
		return ""
	var pkg: Dictionary = r.data[0].binary.package
	var arc := Cfg.sub("downloads").path_join(pkg.name)
	if not await _dl("Скачиваем Java %d (%s)" % [major, r.data[0].version.semver], pkg.link, arc, {"sha256": pkg.checksum}):
		return ""
	_status("Распаковываем Java…")
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
	if home != "" and FileAccess.file_exists(home.path_join("bin/java.exe" if Cfg.is_windows() else "bin/java")):
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
	_status("Узнаём последнюю версию Bedrock Dedicated Server…")
	var r: Dictionary = await Net.get_json(BEDROCK_LINKS)
	var url := ""
	if r.ok:
		for l in r.data.get("result", {}).get("links", []):
			if l.downloadType == ("serverBedrockWindows" if Cfg.is_windows() else "serverBedrockLinux"):
				url = l.downloadUrl
	if url == "":
		if FileAccess.file_exists(exe):
			_log("[LANTERN] Нет связи с minecraft.net, запускаем уже скачанную версию.")
		else:
			return _fail("не удалось получить ссылку на Bedrock Dedicated Server: " + r.get("error", "ссылка не найдена"))
	elif info.get("bedrock_url", "") != url or not FileAccess.file_exists(exe):
		if not opts.get("eula", false):
			return _fail("нужно принять условия Minecraft (EULA и политику конфиденциальности)")
		var zip := Cfg.sub("downloads").path_join(url.get_file())
		if not await _dl("Скачиваем Bedrock Dedicated Server " + url.get_file().get_basename().trim_prefix("bedrock-server-"), url, zip):
			return false
		_status("Распаковываем сервер…")
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
		"server-name": opts.get("world", "Мир") + " — LANTERN",
		"level-name": _safe_name(opts.get("world", "world")),
		"max-players": str(opts.get("max_players", 10)),
		"difficulty": opts.get("difficulty", "normal"),
		"gamemode": opts.get("gamemode", "survival"),
		"server-port": str(bedrock_port()),
		"server-portv6": str(bedrock_port() + 1),
		"online-mode": "true" if opts.get("online_mode", true) else "false",
		"allow-cheats": "false",
		"enable-lan-visibility": "true",
		"allow-list": "false",
		# BDS 1.26+ по умолчанию слушает NetherNet (WebRTC); LANTERN передаёт RakNet по UDP
		"transport": "raknet",
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
		_status("Запускаем сервер Java…")
		err = proc.start(_installed.java, args, dir)
	else:
		if not Cfg.is_windows():
			OS.set_environment("LD_LIBRARY_PATH", dir)
		_status("Запускаем сервер Bedrock…")
		# Только для автотестов в песочнице без IPv6: заглушка сокетов для BDS (см. tests/e2e.sh)
		var pre := OS.get_environment("LANTERN_TEST_BDS_PRELOAD")
		if pre != "":
			OS.set_environment("LD_PRELOAD", pre)
		err = proc.start(dir.path_join("bedrock_server.exe" if Cfg.is_windows() else "bedrock_server"), PackedStringArray(), dir)
		if pre != "":
			OS.unset_environment("LD_PRELOAD")
	if err != "":
		return _fail(err)
	started_msec = Time.get_ticks_msec()
	_log("[LANTERN] Сервер запущен (процесс %d). Папка: %s" % [proc.pid, dir])
	return true


static var _re_join := RegEx.create_from_string("^\\[[^\\]]*\\]:? ?(?:\\[[^\\]]*\\]:? )?([\\.\\w]{1,32}) joined the game")
static var _re_left := RegEx.create_from_string("^\\[[^\\]]*\\]:? ?(?:\\[[^\\]]*\\]:? )?([\\.\\w]{1,32}) (?:left the game|lost connection)")
static var _re_bconn := RegEx.create_from_string("Player connected: ([^,]+),")
static var _re_bdisc := RegEx.create_from_string("Player disconnected: ([^,]+),")


var _last_err := ""
## Порты сервера: обычно 25565 / 19132; если заняты — «доктор» берёт свободные (relay пересылает на настоящие).
func java_port() -> int:
	return int(opts.get("java_port", JAVA_PORT))


func bedrock_port() -> int:
	return int(opts.get("port", BEDROCK_PORT))


## «Ник не в белом списке» (совместимость со старыми проверками).
static func whitelist_kicked(t: String) -> String:
	return Doctor.whitelist_nick(t)


func _fixed(text: String) -> void:
	_log("[LANTERN] Исправлено: " + text)
	fixes.push_front(text)
	if fixes.size() > 8:
		fixes.resize(8)
	doctor_changed.emit()


func _issue(id: String, text: String, button := "", action := "", arg: Variant = null) -> void:
	for i in issues:
		if i.id == id:
			return
	issues.append({"id": id, "text": text, "button": button, "action": action, "arg": arg})
	doctor_changed.emit()


func dismiss(id: String) -> void:
	issues = issues.filter(func(i): return i.id != id)
	doctor_changed.emit()


## Кнопка «доктора» на «Мой мир».
func resolve(id: String) -> void:
	var it: Dictionary = {}
	for i in issues:
		if i.id == id:
			it = i
	if it.is_empty():
		return
	dismiss(id)
	match String(it.action):
		"whitelist_add":
			command("whitelist add " + String(it.arg))
			_fixed("%s добавлен в белый список — пусть зайдёт ещё раз" % it.arg)
		"pardon":
			command("pardon " + String(it.arg))
			_fixed("%s разбанен — пусть зайдёт ещё раз" % it.arg)
		"allow_offline":
			opts["online_mode"] = false
			_save_opts()
			_fixed("Теперь пускаем игроков с TLauncher (мир тот же, сервер перезапущен)")
			restart()
		"more_slots":
			opts["max_players"] = int(opts.get("max_players", 10)) + 5
			_save_opts()
			_fixed("Мест на сервере теперь %d (сервер перезапущен)" % int(opts.max_players))
			restart()


func _save_opts() -> void:
	if Cfg.autotest == "":
		Cfg.set_value("host", edition, opts)


## Строка журнала → исправить само или спросить хозяина.
func _doctor(t: String) -> bool:
	var d := Doctor.diagnose(t)
	if d.is_empty():
		return false
	var n := String(d.nick)
	match String(d.rule):
		"port":
			_port_conflict = true
		"whitelist":
			if n == "":
				return true
			if opts.get("whitelist", false):
				_issue("wl:" + n, Doctor.text(d), "Впустить", "whitelist_add", n)
			else:
				command("whitelist off")
				command("whitelist add " + n)
				_fixed("%s не пускал белый список — LANTERN выключил его. Пусть %s зайдёт ещё раз" % [n, n])
		"auth":
			if opts.get("online_mode", true):
				_issue("auth", Doctor.text(d), "Пускать игроков TLauncher", "allow_offline")
		"outdated_client", "outdated_server":
			_issue("ver:" + n, Doctor.text(d, String(_installed.get("mc_version", ""))), "Понятно", "")
		"banned":
			if n != "":
				_issue("ban:" + n, Doctor.text(d), "Разбанить", "pardon", n)
		"full":
			_issue("full", Doctor.text(d), "Больше мест (+5)", "more_slots")
	return true


## Мало памяти: не даём Java больше половины ОЗУ, предупреждаем, если свободной мало.
func _check_memory() -> void:
	if edition != "java":
		return
	var info := OS.get_memory_info()
	var total := int(info.get("physical", 0) / (1 << 20))
	var want := int(opts.get("memory_mb", 2048))
	var mem := Doctor.memory_for(want, total)
	if mem != want:
		opts["memory_mb"] = mem
		_fixed("Памяти на компьютере немного — серверу выделено %.1f ГБ" % (mem / 1024.0))
	var free := int(info.get("available", -1) / (1 << 20)) if int(info.get("available", -1)) > 0 else -1
	if free >= 0 and free < mem:
		_issue("ram", "Свободной памяти мало (%.1f ГБ). Закройте браузер и лишние программы, иначе сервер может тормозить." % (free / 1024.0), "Понятно", "")


## Заняты 25565 / 19132 (другой сервер, открытый в игре мир) — берём свободные, комната работает как обычно.
func _check_ports() -> void:
	if Cfg.autotest_args.has("no_port_check"):
		return
	if edition == "java":
		var jp := Doctor.free_port(java_port(), false)
		if jp != java_port():
			_fixed("Порт %d был занят — сервер на порту %d, друзья заходят как обычно" % [java_port(), jp])
			opts["java_port"] = jp
	if edition == "bedrock" or opts.get("crossplay", false):
		var bp := Doctor.free_port(bedrock_port(), true, [BEDROCK_PORT + 1, BEDROCK_PORT + 2])
		if bp != bedrock_port():
			_fixed("Порт %d был занят — сервер на порту %d, друзья заходят как обычно" % [bedrock_port(), bp])
			opts["port"] = bp


## Geyser: порт Bedrock в первой секции «bedrock:» (файл может ещё не существовать — тогда его создаст _geyser_config).
static func _geyser_port(path: String, port: int) -> void:
	if not FileAccess.file_exists(path):
		return
	var txt := FileAccess.get_file_as_string(path)
	var out := RegEx.create_from_string("(?m)^(  port:)\\s*\\d+").sub(txt, "$1 %d" % port)
	if out != txt:
		var f := FileAccess.open(path, FileAccess.WRITE)
		f.store_string(out)


func _on_line(t: String) -> void:
	_log(t)
	if (t.contains("ERROR") or t.contains("Exception")) and _last_err == "":
		_last_err = t.substr(t.find("]") + 1).strip_edges() if t.begins_with("[") else t
	if state == "starting":
		if (edition == "java" and t.contains("Done (") and t.contains("help")) or (edition == "bedrock" and t.contains("Server started.")):
			_set_state("running")
			_status("Сервер работает")
			if edition == "java" and opts.get("whitelist", false) and String(opts.get("whitelist_nick", "")) != "":
				command("whitelist add " + String(opts.whitelist_nick))
	if _doctor(t):
		return
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
	_log("[LANTERN] Сервер завершился (код %d)." % code)
	if was == "stopping":
		_set_state("stopped")
		_status("Сервер остановлен, мир сохранён")
	else:
		var plan := Doctor.exit_plan(_port_conflict, _port_retries, _auto_restarts, Time.get_ticks_msec() - started_msec)
		if plan == "port":
			_port_retries += 1
			var old := java_port() if edition == "java" else bedrock_port()
			if edition == "java":
				opts["java_port"] = Doctor.free_port(java_port() + 1, false)
			else:
				opts["port"] = Doctor.free_port(bedrock_port() + 3, true)
			var now_p := java_port() if edition == "java" else bedrock_port()
			_fixed("Порт %d был занят — перезапустили сервер на порту %d" % [old, now_p])
			_set_state("stopped")
			host(edition, opts)
			return
		if plan == "restart":
			_auto_restarts += 1
			_fixed("Сервер упал — LANTERN перезапустил его" + (" (ошибка: %s)" % _last_err.substr(0, 80) if _last_err != "" else ""))
			_set_state("stopped")
			host(edition, opts)
			return
		var hint := ""
		if edition == "java" and Time.get_ticks_msec() - started_msec < 15000:
			hint = " Возможно, не хватает памяти — закройте лишние программы."
		var why := (" Последняя ошибка: " + _last_err.substr(0, 160) + ".") if _last_err != "" else ""
		_fail("сервер неожиданно остановился (код %d).%s%s Подробности — в журнале." % [code, why, hint])


func command(cmd: String) -> void:
	if proc and proc.running:
		proc.write_line(cmd)


func stop() -> void:
	if not proc or not proc.running:
		_set_state("stopped")
		return
	_set_state("stopping")
	_status("Сохраняем мир и останавливаем сервер…")
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


## Что внутри копии: {ok, edition, world, error}. Java: <мир>/level.dat (+ _nether, _the_end), Bedrock: worlds/<мир>/level.dat.
static func inspect_backup(zip: String) -> Dictionary:
	var z := ZIPReader.new()
	if z.open(zip) != OK:
		return {"ok": false, "error": "файл не открылся — это не zip-копия мира"}
	var files := z.get_files()
	z.close()
	for f in files:
		var parts := String(f).split("/", false)
		if parts.size() == 3 and parts[0] == "worlds" and parts[2] == "level.dat":
			return {"ok": true, "edition": "bedrock", "world": parts[1]}
		if parts.size() == 2 and parts[1] == "level.dat" and not (parts[0].ends_with("_nether") or parts[0].ends_with("_the_end")):
			return {"ok": true, "edition": "java", "world": parts[0]}
	return {"ok": false, "error": "в архиве нет мира Minecraft (level.dat)"}


## Восстановить мир из копии (своей, из облака или от друга). Текущая папка мира с тем же именем
## не удаляется, а переименовывается в «<мир>.before-<время>». Сервер должен быть остановлен.
func restore_backup(zip: String) -> Dictionary:
	if is_alive():
		return {"ok": false, "error": "Сначала остановите сервер"}
	var info := inspect_backup(zip)
	if not info.ok:
		return info
	var saved := edition
	edition = info.edition
	var dir := server_dir()
	edition = saved
	var stamp := Time.get_datetime_string_from_system().replace(":", "-").replace("T", "_")
	var tops: Array = ["worlds/" + info.world] if info.edition == "bedrock" else [info.world, info.world + "_nether", info.world + "_the_end"]
	for t in tops:
		if DirAccess.dir_exists_absolute(dir.path_join(t)):
			DirAccess.rename_absolute(dir.path_join(t), dir.path_join(t + ".before-" + stamp))
	var err := Archive.unzip(zip, dir)
	if err != "":
		return {"ok": false, "error": err}
	# следующий запуск этого издания откроет восстановленный мир
	var o: Dictionary = Cfg.get_value("host", info.edition, {})
	o["world"] = info.world
	Cfg.set_value("host", info.edition, o)
	return {"ok": true, "edition": info.edition, "world": info.world}


func list_backups() -> Array:
	var out := []
	var dir := Cfg.sub("backups")
	var d := DirAccess.open(dir)
	if d == null:
		return out
	for f in d.get_files():
		if f.ends_with(".zip"):
			var p := dir.path_join(f)
			out.append({"path": p, "name": f, "size": FileAccess.get_size(p), "time": FileAccess.get_modified_time(p)})
	out.sort_custom(func(a, b): return a.time > b.time)
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
