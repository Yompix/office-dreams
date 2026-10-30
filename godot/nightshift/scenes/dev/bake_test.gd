extends Node3D
## Проба записи кадра: $SHOT_ID в момент $AT (секунды), снимок в $SHOT и выход.
var frames := 0
var player: ShotPlayer

func _ready():
	player = ShotPlayer.new()
	add_child(player)
	player.play(OS.get_environment("SHOT_ID") if OS.get_environment("SHOT_ID") != "" else "pro_two")
	player.apply(float(OS.get_environment("AT")) if OS.get_environment("AT") != "" else 2.0)

func _process(_d):
	frames += 1
	if frames == 6:
		get_viewport().get_texture().get_image().save_png(OS.get_environment("SHOT"))
		print("saved ", RenderingServer.get_current_rendering_method())
		get_tree().quit()
