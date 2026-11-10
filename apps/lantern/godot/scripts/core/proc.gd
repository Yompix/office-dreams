class_name ManagedProcess
extends RefCounted
## Дочерний процесс с живым выводом: читает stdout/stderr в фоне, отдаёт строки через poll().
## Запуск в нужной папке: Linux — через sh -c 'cd …', Windows — через cmd /c "cd /d …".
## Ничего не запускается с правами администратора.

signal line(text: String)
signal exited(code: int)

var pid := -1
var running := false
var exit_code := -1
var _stdio: FileAccess
var _stderr: FileAccess
var _threads: Array[Thread] = []
var _mutex := Mutex.new()
var _queue: PackedStringArray = []
var _via_shell := false
static var _ansi := RegEx.create_from_string("\\x1b\\[[0-9;?]*[ -/]*[@-~]")


## exe — полный путь к программе; cwd — папка запуска ("" — не важно).
func start(exe: String, args: PackedStringArray, cwd := "") -> String:
	var path := exe
	var argv := args
	_via_shell = false
	if cwd != "":
		_via_shell = true
		if Cfg.is_windows():
			# cmd.exe: обратные слэши в путях, кавычки вокруг путей с пробелами (C:\Users\Иван Петров\…)
			var cmdline := "cd /d %s && %s" % [_wq(cwd.replace("/", "\\")), _wq(exe.replace("/", "\\") if exe.contains("/") else exe)]
			for a in args:
				cmdline += " " + _wq(a)
			path = OS.get_environment("ComSpec") if OS.get_environment("ComSpec") != "" else "cmd.exe"
			argv = PackedStringArray(["/d", "/s", "/c", cmdline])
		else:
			path = "/bin/sh"
			argv = PackedStringArray(["-c", "cd \"$0\" && exec \"$@\"", cwd, exe])
			argv.append_array(args)
	var info := OS.execute_with_pipe(path, argv, true)
	if info.is_empty():
		return "не удалось запустить " + exe.get_file()
	pid = info["pid"]
	_stdio = info["stdio"]
	_stderr = info["stderr"]
	running = true
	for f in [_stdio, _stderr]:
		var t := Thread.new()
		t.start(_reader.bind(f))
		_threads.append(t)
	return ""


static func _wq(s: String) -> String:
	for ch in [" ", "&", "(", ")", "^", "|", "<", ">", ",", ";", "=", "!", "'", "+", "[", "]", "{", "}", "`", "~"]:
		if s.contains(ch):
			return "\"" + s + "\""
	return s


func _reader(f: FileAccess) -> void:
	while true:
		var l := f.get_line()
		if f.get_error() != OK and l == "":
			break
		if l == "" and f.eof_reached():
			break
		l = _ansi.sub(l, "", true).strip_edges(false, true)
		_mutex.lock()
		_queue.append(l)
		_mutex.unlock()


## Вызывать каждый кадр: раздаёт накопленные строки и сообщает о выходе.
func poll() -> void:
	_mutex.lock()
	var lines := _queue
	_queue = PackedStringArray()
	_mutex.unlock()
	for l in lines:
		line.emit(l)
	if running and not OS.is_process_running(pid):
		running = false
		exit_code = OS.get_process_exit_code(pid)
		_cleanup()
		_mutex.lock()
		var rest := _queue
		_queue = PackedStringArray()
		_mutex.unlock()
		for l in rest:
			line.emit(l)
		exited.emit(exit_code)


func write_line(text: String) -> void:
	if running and _stdio != null:
		_stdio.store_string(text + "\n")
		_stdio.flush()


func kill() -> void:
	if not running:
		return
	if Cfg.is_windows() and _via_shell:
		OS.execute("taskkill", ["/T", "/F", "/PID", str(pid)])
	else:
		OS.kill(pid)


func _cleanup() -> void:
	for t in _threads:
		if t.is_started():
			t.wait_to_finish()
	_threads.clear()
	if _stdio:
		_stdio.close()
	if _stderr:
		_stderr.close()
