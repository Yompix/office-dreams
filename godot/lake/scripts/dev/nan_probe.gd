extends SceneTree
var pmax := 0.0
## Где впервые появляется «нечисло» (NaN/inf) в положении машины, дороги, героев: игра идёт сама, каждый кадр проверка.
##   godot --headless --path godot/lake --script res://scripts/dev/nan_probe.gd -- --autoplay --fast --scene s3 --stop s5
var n := 0
var seen := {}


func _init() -> void:
	change_scene_to_file.call_deferred(String(ProjectSettings.get_setting("application/run/main_scene")))


static func _ok(t: Transform3D) -> bool:
	return t.origin.is_finite() and t.basis.x.is_finite() and t.basis.y.is_finite() and t.basis.z.is_finite()


func _report(what: String, extra: String) -> void:
	if seen.has(what):
		return
	seen[what] = true
	var g = current_scene
	print("NAN кадр %d сцена %s план %s: %s %s" % [n, g.get("_scene_id"), g.get("_shot"), what, extra])


func _process(_d: float) -> bool:
	n += 1
	var g = current_scene
	if g == null or not ("scene" in g) or g.get("scene") == null:
		return false
	var s = g.get("scene")
	if "car" in s and s.car != null:
		var c: Node3D = s.car
		if not _ok(c.global_transform):
			_report("car", "pos %s rot %s speed %s steer %s t %s y0 %s parent %s parent_ok %s" % [c.position, c.rotation, c.get("speed"), c.get("steer"), c.get("t"), c.get("_y0"), c.get_parent().name, _ok((c.get_parent() as Node3D).global_transform) if c.get_parent() is Node3D else "-"])
	if "road" in s and s.road != null and s.road is Node3D and not _ok((s.road as Node3D).global_transform):
		_report("road", str((s.road as Node3D).position))
	var acts: Dictionary = g.get("actors")
	for k in acts:
		var a = acts[k]
		if a is Actor and is_instance_valid(a):
			if not _ok((a as Actor).global_transform):
				_report("actor " + String(k), "pos %s parent %s" % [(a as Actor).position, (a as Actor).get_parent().name])
			else:
				var sk: Skeleton3D = (a as Actor).skel
				for b in [0, sk.find_bone("head"), sk.find_bone("hand_r")]:
					if b >= 0 and not _ok(sk.get_bone_global_pose(b)):
						_report("bones " + String(k), "кость %s клип %s" % [sk.get_bone_name(b), (a as Actor).ap.current_animation])
						break
	var pt = g.get("_pitch")
	if pt != null and absf(float(pt)) > 0.2:
		_report("pitch", "клевок кузова %s рад" % pt)
	var cam = g.get("cam")
	if cam is Camera3D and not _ok((cam as Camera3D).global_transform):
		_report("cam", "")
	return false


func _finalize() -> void:
	print("NAN итог: кадров ", n, ", найдено ", seen.keys())
