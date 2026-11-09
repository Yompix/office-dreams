extends SceneTree
## Куда поворачивается голова: цель слева (+X) / справа (−X) / ниже. Герой смотрит по +Z, его правая рука — на −X.
##   godot --headless --path godot --script res://scripts/dev/gaze_probe.gd
var _a: Actor
var _f := 0
var _phase := 0
const TARGETS := [["слева", Vector3(2.0, 1.5, 0.6)], ["справа", Vector3(-2.0, 1.5, 0.6)], ["ниже, впереди", Vector3(0.0, 0.6, 1.2)]]


func _init() -> void:
	_a = Actor.make("iris", "iris")
	get_root().add_child(_a)


func _process(_d: float) -> bool:
	_f += 1
	if _f == 3:
		_a.play("Idle", 0.0, 0.0)
	var i := (_f - 3) / 90
	if _f >= 3 and i < TARGETS.size():
		_a.look_target = TARGETS[i][1]
		if (_f - 3) % 90 == 89:
			var sk := _a.skel
			var hb := (sk.global_transform * sk.get_bone_global_pose(sk.find_bone("head"))).basis
			var hf := hb.z.normalized()
			var to: Vector3 = (TARGETS[i][1] - _a.head_pos()).normalized()
			print("цель %-14s лицо смотрит (%.2f, %.2f, %.2f), на цель (%.2f, %.2f, %.2f): угол %.0f°" % [TARGETS[i][0], hf.x, hf.y, hf.z, to.x, to.y, to.z, rad_to_deg(hf.angle_to(to))])
	if _f > 3 + 90 * TARGETS.size():
		quit()
		return true
	return false
