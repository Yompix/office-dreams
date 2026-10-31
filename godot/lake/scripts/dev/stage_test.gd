extends SceneTree
## Проба поз: Роман за прилавком с разными клипами — какой похож на «тянется под прилавок».
var scene: DriveScene
var cam: Camera3D
var r: Actor
var tests := [["Interact", 0.7], ["Interact", 1.2], ["PickUp_Table", 0.9], ["PickUp_Table", 1.4], ["Chest_Open", 0.8], ["Chest_Open", 1.5]]
var cams := [[Vector3(28.4, 1.5, -9.0), Vector3(26.4, 1.1, -9.2), 45.0], [Vector3(27.35, 1.3, -10.05), Vector3(26.3, 0.98, -8.85), 40.0]]
var si := 0
var ci := 0
var f := 0
func _initialize():
	scene = DriveScene.new()
	root.add_child(scene)
	scene.setup(true)
	scene.park_at_station("ru")
	cam = Camera3D.new()
	cam.near = 0.02
	root.add_child(cam)
	cam.make_current()
	r = Actor.make("roman", "roman")
	scene.add_child(r)
	r.position = Vector3(26.4, 0.38, -9.25)
func _process(d):
	f += 1
	if si >= tests.size():
		quit()
		return false
	var t: Array = tests[si]
	if f == 1:
		r.play(String(t[0]), float(t[1]))
		r.ap.pause()
	var c: Array = cams[ci]
	cam.fov = c[2]
	cam.look_at_from_position(c[0], c[1])
	if f == 6:
		root.get_viewport().get_texture().get_image().save_png("res://export/stage/%d_%s_%.1f_c%d.png" % [si, t[0], t[1], ci])
		f = 0
		ci += 1
		if ci >= cams.size():
			ci = 0
			si += 1
	return false
