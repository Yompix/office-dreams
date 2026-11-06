extends SceneTree
## Седан снаружи и из салона при разном свете (проверка стекла).
## --lights keep,night,day,dusk,sunny,lake_day — свет через DriveScene.set_daylight («keep» — свет самой сцены);
## --views rear_top,top,rear,side,front,inside — камеры в осях машины (вперёд — −Z, водитель слева);
## --oldglass — прежнее стекло (StandardMaterial3D, 0,16 с обеих сторон) для кадров «до»; --frost 0…1 — иней (гл. 2);
## --wait N — кадров после появления машины. Снимки: snapdir/car_<свет>_<вид>.png; надписи интерфейса скрыты.
var frames := 0
var done := false

const VIEWS := {
	"rear_top": [Vector3(0.0, 3.6, 6.4), Vector3(0.0, 0.9, 0.4), 48.0],     # сзади-сверху: заднее стекло и крыша
	"top": [Vector3(0.3, 4.1, 2.7), Vector3(0.0, 0.6, -0.2), 62.0],         # сверху-сзади, ниже навеса заправки
	"rear": [Vector3(1.2, 1.55, 6.2), Vector3(0.0, 1.0, 0.0), 42.0],        # сзади на уровне глаз
	"side": [Vector3(5.2, 1.7, 0.6), Vector3(0.0, 1.0, 0.1), 42.0],         # сбоку: боковые стёкла
	"front": [Vector3(-2.6, 2.1, -6.0), Vector3(0.0, 1.0, 0.0), 42.0],      # спереди три четверти: лобовое
	"inside": [Vector3(-0.37, 1.16, 0.18), Vector3(-0.25, 1.0, -4.0), 68.0],  # глаза водителя: салон и лобовое изнутри
	"inside_side": [Vector3(-0.3, 1.16, 0.25), Vector3(3.0, 1.0, 0.6), 68.0], # изнутри в правое окно
}

func _init() -> void:
	change_scene_to_file.call_deferred(String(ProjectSettings.get_setting("application/run/main_scene")))

func _arg(k: String, def: String) -> String:
	var a := OS.get_cmdline_user_args()
	return a[a.find(k) + 1] if k in a else def

func _process(_d: float) -> bool:
	var g = current_scene
	if g == null or done or not ("scene" in g) or g.scene == null or g.scene.car == null:
		return false
	frames += 1
	if frames < int(_arg("--wait", "40")):
		return false
	done = true
	_shoot(g)
	return false

func _shoot(g) -> void:
	for n in root.find_children("*", "CanvasLayer", true, false):
		(n as CanvasLayer).visible = false
	var car: Node3D = g.scene.car
	if "--oldglass" in OS.get_cmdline_user_args():
		var old := StandardMaterial3D.new()
		old.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
		old.albedo_color = Color(0.45, 0.52, 0.6, 0.16)
		old.roughness = 0.05
		old.metallic_specular = 0.9
		old.cull_mode = BaseMaterial3D.CULL_DISABLED
		for mi in car.find_children("glass*", "MeshInstance3D", true, false):
			(mi as MeshInstance3D).material_override = old
	elif car.has_method("set_frost"):
		car.set_frost(float(_arg("--frost", "0")))
	var cam := Camera3D.new()
	g.add_child(cam)
	cam.far = 400.0
	var dir := _arg("--snapdir", "/tmp")
	var n := 0
	for mode in _arg("--lights", "keep").split(","):
		if mode != "keep":
			g.scene.set_daylight(mode)
		for v in _arg("--views", "rear_top,top,side,inside").split(","):
			if not VIEWS.has(v):
				continue
			var p: Array = VIEWS[v]
			for k in 6:
				cam.fov = float(p[2])
				cam.look_at_from_position(car.to_global(p[0]), car.to_global(p[1]))
				cam.current = true
				await process_frame
			root.get_texture().get_image().save_png("%s/car_%s_%s.png" % [dir, mode, v])
			n += 1
	print("CAR done %d" % n)
	quit()
