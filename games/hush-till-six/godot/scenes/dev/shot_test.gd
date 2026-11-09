extends Node3D
## Проба кадра: место и камера кадра старой игры ($SHOT_ID, по умолчанию guard), снимок в $SHOT и выход.
var frames := 0

func _ready():
	var shots: Dictionary = JSON.parse_string(FileAccess.get_file_as_string("res://data/shots.json")).shots
	var id: String = OS.get_environment("SHOT_ID") if OS.get_environment("SHOT_ID") != "" else "guard"
	var sh: Dictionary = shots[id]
	var loc := Location.create(sh.loc)
	add_child(loc)
	var we := WorldEnvironment.new(); we.environment = loc.env; add_child(we)
	loc.frame_camera(sh).make_current()

func _process(_d):
	frames += 1
	if frames == 6:
		get_viewport().get_texture().get_image().save_png(OS.get_environment("SHOT"))
		print("saved ", RenderingServer.get_current_rendering_method())
		get_tree().quit()
