extends SceneTree
## Снимки экрана «Авторы»: меню → «Авторы» (RU) → «Лицензии» → назад в меню → «Авторы» (EN) → титры-самоход.
##   xvfb-run -a $GODOT --path godot --script res://scripts/dev/credits_shots.gd -- --snapdir папка   (в сборку не входит)
var t := 0.0
var step := 0
var dir := "user://"


func _init() -> void:
	var args := OS.get_cmdline_user_args()
	if "--snapdir" in args:
		dir = args[args.find("--snapdir") + 1]
	change_scene_to_file.call_deferred(String(ProjectSettings.get_setting("application/run/main_scene")))


func _shot(n: String) -> void:
	root.get_texture().get_image().save_png("%s/%s.png" % [dir, n])
	print("SHOT ", n)


func _process(d: float) -> bool:
	var g = current_scene
	if g == null or not ("hud" in g) or g.hud == null:
		return false
	t += d
	if t > 120.0:
		print("CREDITS SHOTS: время вышло на шаге ", step)
		quit()
		return true
	var h = g.hud
	match step:
		0:
			if h.menu_open() and t > 3.0:
				_shot("1_menu")
				h.menu_pick.emit("credits")
				step = 1
				t = 0.0
		1:
			if t > 1.0:
				print("CREDITS open=", h.credits_open(), " menu=", h.menu_open())
				_shot("2_credits_ru")
				h._credits_fill(true)
				step = 2
				t = 0.0
		2:
			if t > 1.0:
				_shot("3_licenses")
				h._credits_sc.scroll_vertical = 30000
				step = 3
				t = 0.0
		3:
			if t > 0.5:
				_shot("4_licenses_far")
				h.close_credits()
				step = 4
				t = 0.0
		4:
			if t > 1.0:
				print("BACK menu=", h.menu_open(), " credits=", h.credits_open())
				h.menu_pick.emit("lang")
				step = 5
				t = 0.0
		5:
			if t > 1.0:
				h.menu_pick.emit("credits")
				step = 6
				t = 0.0
		6:
			if t > 1.0:
				_shot("5_credits_en")
				h.close_credits()
				h.hide_menu()
				h.show_credits(true)
				step = 7
				t = 0.0
		7:
			if t > 6.0:
				_shot("6_roll")
				print("ROLL y=", h._credits_y)
				quit()
				return true
	return false
