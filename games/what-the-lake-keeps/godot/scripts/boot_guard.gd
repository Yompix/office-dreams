class_name BootGuard
extends RefCounted
## Защита от вылетов на слабых и капризных видеокартах (встроенная Radeon у Ryzen, старые драйверы).
## Игра при старте ставит метку «запущена» (user://lake_boot.cfg) и снимает её при нормальном выходе (и при возврате
## в меню). Метка осталась — прошлый запуск закрылся аварийно (вылет драйвера, нехватка видеопамяти):
##   1-й раз — графика «Низкое»;
##   снова, уже на «Низком», — совместимый видеорежим (OpenGL через ANGLE / Direct3D 11) со следующего запуска:
##   файл user://lake_renderer.cfg (project.godot: application/config/project_settings_override), игра перезапускается сама.
## Вернуть обычный режим — «Настройки → Видеорежим». Работает только в собранной игре (не в редакторе и не в автопрогонах
## облака) или с ключом --bootguard (проверка).
## С 4 ноя: прошлый запуск проработал дольше STABLE (пульс beat() раз в 30 с) — это не вылет при старте, а снятая задача,
## выключение ПК или сбой посреди игры: графика только на ступень ниже, без совместимого режима; о любом таком
## изменении игрок узнаёт плашкой в меню (раньше — молча); счётчик вылетов сбрасывается после STABLE устойчивой работы.
const PATH := "user://lake_boot.cfg"
const OVERRIDE := "user://lake_renderer.cfg"
const STABLE := 120.0   # секунд: дольше — запуск считается устойчивым (вылеты на встроенной Radeon были при загрузке)


static func active() -> bool:
	return not OS.is_debug_build() or "--bootguard" in OS.get_cmdline_user_args()


## Совместимый режим включён (файл переопределения есть)?
static func compat_on() -> bool:
	return FileAccess.file_exists(OVERRIDE)


## Включить или выключить совместимый режим (действует со следующего запуска).
static func set_compat(on: bool) -> void:
	if on:
		var f := FileAccess.open(OVERRIDE, FileAccess.WRITE)
		if f == null:
			return
		f.store_string("; What the Lake Keeps: совместимый видеорежим (после вылетов или из настроек). Удалить файл — обычный режим.\n"
			+ "[rendering]\n\nrenderer/rendering_method=\"gl_compatibility\"\n"
			+ "gl_compatibility/driver.windows=\"opengl3_angle\"\n")
		f.close()
	elif compat_on():
		DirAccess.remove_absolute(ProjectSettings.globalize_path(OVERRIDE))


## В начале игры (после загрузки настроек). Возвращает, что сделано: "" (ничего), "low" (графика снижена до «Низкого»),
## "lower" (на ступень ниже — прошлый запуск закрылся без выхода, проработав дольше STABLE), "compat" (включён
## совместимый режим — нужен перезапуск; плашка о нём — после перезапуска, take_note()).
static func start(st: Settings, pc: bool) -> String:
	if not active():
		return ""
	var c := ConfigFile.new()
	c.load(PATH)
	var crashed := bool(c.get_value("boot", "running", false))
	var n := int(c.get_value("boot", "crashes", 0))
	var alive := float(c.get_value("boot", "alive", 0.0))   # сколько секунд проработал прошлый запуск (пульс beat)
	var act := ""
	if crashed and alive >= STABLE:   # не вылет при загрузке: графика на ступень ниже, совместимый режим не трогаем
		if st.quality > 0:
			st.quality -= 1
			st.save()
			act = "lower"
	elif crashed:   # вылет при старте или загрузке
		n += 1
		if st.quality > 0:
			st.quality = 0
			st.save()
			act = "low"
		elif pc:
			set_compat(true)
			act = "compat"
			c.set_value("boot", "note", "compat")   # сказать игроку уже в совместимом режиме, после перезапуска
	c.set_value("boot", "running", act != "compat")   # перезапуск в совместимом режиме — не вылет
	c.set_value("boot", "crashes", n)
	c.set_value("boot", "alive", 0.0)
	c.set_value("boot", "gpu", RenderingServer.get_video_adapter_name())
	c.set_value("boot", "method", RenderingServer.get_current_rendering_method())
	c.save(PATH)
	return act


## Пульс (game.gd, раз в 30 с): сколько секунд работает этот запуск. После STABLE — счётчик вылетов при старте в ноль.
static func beat(sec: float) -> void:
	if not active():
		return
	var c := ConfigFile.new()
	c.load(PATH)
	c.set_value("boot", "alive", sec)
	if sec >= STABLE:
		c.set_value("boot", "crashes", 0)
	c.save(PATH)


## Отложенная плашка (совместимый режим включён перед перезапуском): вернуть один раз и забыть.
static func take_note() -> String:
	if not active():
		return ""
	var c := ConfigFile.new()
	if c.load(PATH) != OK:
		return ""
	var note := String(c.get_value("boot", "note", ""))
	if note != "":
		c.set_value("boot", "note", "")
		c.save(PATH)
	return note


## Нормальный выход (и возврат в меню): метку снять.
static func clean_exit() -> void:
	if not active():
		return
	var c := ConfigFile.new()
	c.load(PATH)
	c.set_value("boot", "running", false)
	c.save(PATH)


## Перезапуск игры (смена видеорежима): с теми же ключами запуска.
static func restart(tree: SceneTree) -> void:
	clean_exit()
	OS.set_restart_on_exit(true, OS.get_cmdline_args())
	tree.quit()
