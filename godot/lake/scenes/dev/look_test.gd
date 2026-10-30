extends Node3D
## Проба вида: ночная трасса и машина, план $CAM, снимок в $SHOT на кадре $FRAMES (по умолчанию 40) и выход.
var frames := 0
var scene: DriveScene
var cam: Camera3D

func _ready():
	scene = DriveScene.new()
	add_child(scene)
	scene.setup(RenderingServer.get_current_rendering_method() == "forward_plus")
	cam = Camera3D.new()
	cam.near = 0.02
	add_child(cam)
	cam.make_current()
	# Ной за рулём, Айрис рядом (модели — пока Итан и Лили из Hush Till Six)
	var noah := Actor.make("noah", "ethan")
	scene.car.add_child(noah)
	noah.position = Car.DRIVER + Vector3(0, float(OS.get_environment("NY")) if OS.get_environment("NY") != "" else 0.15, float(OS.get_environment("NZ")) if OS.get_environment("NZ") != "" else 0.0)
	noah.rotation_degrees = Vector3(0, 180, 0)
	noah.play(OS.get_environment("NCLIP") if OS.get_environment("NCLIP") != "" else "Driving", 0.5)
	var iris := Actor.make("iris", "lily")
	scene.car.add_child(iris)
	iris.position = Car.PASSENGER + Vector3(0, float(OS.get_environment("IY")) if OS.get_environment("IY") != "" else 0.15, float(OS.get_environment("IZ")) if OS.get_environment("IZ") != "" else 0.0)
	iris.rotation_degrees = Vector3(0, 180, 0)
	iris.play(OS.get_environment("ICLIP") if OS.get_environment("ICLIP") != "" else "Rk_SitIdle", 1.0)
	scene.put_camera(cam, OS.get_environment("CAM") if OS.get_environment("CAM") != "" else "pov")

func _process(_d):
	frames += 1
	if frames == (int(OS.get_environment("FRAMES")) if OS.get_environment("FRAMES") != "" else 40):
		get_viewport().get_texture().get_image().save_png(OS.get_environment("SHOT"))
		print("saved ", RenderingServer.get_current_rendering_method())
		get_tree().quit()
