class_name AppLog
extends RefCounted
## Журнал приложения для «Сообщить о проблеме» (1.6): последние ~2000 строк в памяти и в user://lantern.log.
## Пишут все части (мосты relay в своих потоках, сервер, экраны) — потокобезопасно. Личного не пишем:
## ни токенов, ни паролей, ни чужих IP (всё равно вычищается ещё раз при отправке).

const MAX := 2000
static var _lines: PackedStringArray = []
static var _mutex := Mutex.new()
static var _file: FileAccess = null
static var _written := 0
static var _echo := -1     # автотесты: строки журнала ещё и в stdout


static func add(tag: String, text: String) -> void:
	var t := Time.get_datetime_dict_from_system()
	var line := "%02d:%02d:%02d.%03d [%s] %s" % [t.hour, t.minute, t.second, Time.get_ticks_msec() % 1000, tag, text]
	_mutex.lock()
	if _echo < 0:
		_echo = 1 if "--autotest" in OS.get_cmdline_user_args() else 0
	if _echo == 1:
		print("[log] ", line)
	_lines.append(line)
	if _lines.size() > MAX + 200:
		_lines = _lines.slice(_lines.size() - MAX)
	if _file == null and _written == 0:
		_written = 1
		# прошлый запуск сохраняем рядом: после вылета он и есть самый нужный
		if FileAccess.file_exists("user://lantern.log"):
			DirAccess.rename_absolute(ProjectSettings.globalize_path("user://lantern.log"), ProjectSettings.globalize_path("user://lantern.prev.log"))
		_file = FileAccess.open("user://lantern.log", FileAccess.WRITE)
	if _file:
		_file.store_line(line)
		_written += 1
		if _written % 20 == 0:
			_file.flush()
		if _written > 20000:   # не растём бесконечно
			_file.close()
			_file = FileAccess.open("user://lantern.log", FileAccess.WRITE)
			_written = 1
	_mutex.unlock()


static func text() -> String:
	_mutex.lock()
	var s := "\n".join(_lines.slice(max(0, _lines.size() - MAX)))
	if _file:
		_file.flush()
	_mutex.unlock()
	return s


## Хвост прошлого запуска (если приложение вылетело) — последние n строк.
static func previous(n := 300) -> String:
	if not FileAccess.file_exists("user://lantern.prev.log"):
		return ""
	var lines := FileAccess.get_file_as_string("user://lantern.prev.log").split("\n")
	return "\n".join(lines.slice(max(0, lines.size() - n)))


## Вычистить личное: токены, ключи, пароли, ICE-пароли WebRTC, чужие IP (свои локальные оставляем).
static func scrub(s: String) -> String:
	var out := s
	for p in [
		["XBL3\\.0 x=[^\\s\"]+", "XBL3.0 ***"],
		["\\b(Bearer|Basic)\\s+[A-Za-z0-9._~+/=-]+", "$1 ***"],
		["(?i)(authorization|token|password|passwd|secret|resume|x-upload-key|cookie)(\"?\\s*[:=]\\s*)(\"[^\"]*\"|[^\\s,;}]+)", "$1$2***"],
		["eyJ[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{5,}", "<jwt>"],
		["a=ice-pwd:\\S+", "a=ice-pwd:***"],
		["a=ice-ufrag:\\S+", "a=ice-ufrag:***"],
		["a=fingerprint:\\S+ \\S+", "a=fingerprint:***"],
	]:
		out = RegEx.create_from_string(p[0]).sub(out, p[1], true)
	var re := RegEx.create_from_string("\\b(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\b")
	var parts := []
	var last := 0
	for m in re.search_all(out):
		var a := int(m.get_string(1))
		var b := int(m.get_string(2))
		var keep := a < 30 or a == 127 or a >= 224 or (a == 192 and b == 168) or (a == 172 and b >= 16 and b <= 31) \
			or int(m.get_string(3)) > 255 or int(m.get_string(4)) > 255 or b > 255
		parts.append(out.substr(last, m.get_start() - last))
		parts.append(m.get_string() if keep else "%d.x.x.%s" % [a, m.get_string(4)])
		last = m.get_end()
	parts.append(out.substr(last))
	return "".join(parts)
