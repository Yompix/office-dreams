extends SceneTree
## Снимки телефонного интерфейса в облаке (ключ --touch): главное меню, настройки, выбор, субтитры, пауза.
##   xvfb-run -a -s "-screen 0 1600x720x24" $GODOT --rendering-method gl_compatibility --resolution 1600x720 --path godot/lake \
##     --script res://scripts/dev/phone_shots.gd -- --touch --snapdir папка [--scene s1]
var t := 0.0
var step := 0
var dir := "user://"
var play := false


func _init() -> void:
	var args := OS.get_cmdline_user_args()
	if "--snapdir" in args:
		dir = args[args.find("--snapdir") + 1]
	play = "--scene" in args
	change_scene_to_file.call_deferred(String(ProjectSettings.get_setting("application/run/main_scene")))


func _shot(n: String) -> void:
	root.get_texture().get_image().save_png("%s/%s.png" % [dir, n])
	print("SHOT ", n, " окно ", DisplayServer.window_get_size(), " холст ", root.get_visible_rect().size, " масштаб ", root.content_scale_factor)


func _process(d: float) -> bool:
	var g = current_scene
	if g == null or not ("hud" in g) or g.hud == null:
		return false
	t += d
	if t > 240.0:
		print("PHONE SHOTS: время вышло на шаге ", step)
		quit()
		return true
	if not play:
		match step:
			0:
				if g.hud.menu_open() and t > 3.0:
					_shot("menu")
					g.hud.show_settings(g.settings)
					step = 1
					t = 0.0
			1:
				if t > 1.0:
					_shot("settings")
					g.hud.close_settings()
					quit()
					return true
		return false
	match step:
		0:   # реплика с субтитрами
			if String(g.hud.sub_text.text) != "" and t > 2.0:
				_shot("subs")
				step = 1
		1:   # выбор
			if g.hud.choosing():
				t = 0.0
				step = 2
			else:
				g._skip = true
		2:
			if t > 0.8:
				_shot("choice")
				g._pause()
				step = 3
				t = 0.0
		3:
			if t > 0.8:
				_shot("pause")
				quit()
				return true
	return false
