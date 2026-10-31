extends SceneTree
var n := 0
var scene: DriveScene
var acts := []
func _initialize():
	scene = DriveScene.new()
	root.add_child(scene)
	scene.setup(false)
	for k in ["noah", "iris"]:
		var a := Actor.make(k, k)
		scene.car.add_child(a)
		a.position = (Car.DRIVER if k == "noah" else Car.PASSENGER) + Vector3(0, 0.15, 0)
		a.rotation_degrees = Vector3(0, 180, 0)
		a.play("Driving" if k == "noah" else "Rk_SitIdle", 0.5)
		acts.append(a)
func _process(_d):
	n += 1
	if n == 5:
		for a in acts:
			var sk: Skeleton3D = a.skel
			var top := -9.0
			for mi in a.find_children("*", "MeshInstance3D", true, false):
				var ab: AABB = (mi as MeshInstance3D).get_aabb()
				top = maxf(top, ((mi as MeshInstance3D).global_transform * ab).end.y)
			var h := scene.car.to_local(sk.global_transform * sk.get_bone_global_pose(sk.find_bone("head")).origin)
			var p := scene.car.to_local(sk.global_transform * sk.get_bone_global_pose(sk.find_bone("pelvis")).origin)
			print(a.key, " head bone y=%.3f z=%.3f  pelvis y=%.3f z=%.3f  mesh top(rest aabb)=%.3f" % [h.y, h.z, p.y, p.z, scene.car.to_local(Vector3(0, top, 0)).y])
		quit()
	return false
