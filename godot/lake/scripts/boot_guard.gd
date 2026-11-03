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
const PATH := "user://lake_boot.cfg"
const OVERRIDE := "user://lake_renderer.cfg"


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
## "compat" (включён совместимый режим — нужен перезапуск).
static func start(st: Settings, pc: bool) -> String:
	if not active():
		return ""
	var c := ConfigFile.new()
	c.load(PATH)
	var crashed := bool(c.get_value("boot", "running", false))
	var n := int(c.get_value("boot", "crashes", 0))
	var act := ""
	if crashed:
		n += 1
		if st.quality > 0:
			st.quality = 0
			st.save()
			act = "low"
		elif pc:
			set_compat(true)
			act = "compat"
	c.set_value("boot", "running", act != "compat")   # перезапуск в совместимом режиме — не вылет
	c.set_value("boot", "crashes", n)
	c.set_value("boot", "gpu", RenderingServer.get_video_adapter_name())
	c.set_value("boot", "method", RenderingServer.get_current_rendering_method())
	c.save(PATH)
	return act


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
