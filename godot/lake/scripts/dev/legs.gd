extends SceneTree
## Длина ног героев (мир, м): шаг клипа подстраивается под неё.
var _f := 0
func _process(_d: float) -> bool:
	_f += 1
	if _f < 2:
		return false
	for k in ["noah", "iris", "zoe", "eva", "danny", "agnes", "hale", "orderly", "roman", "victor", "noah_kid"]:
		var a := Actor.make(k, k)
		get_root().add_child(a)
		var sk := a.skel
		sk.force_update_all_bone_transforms()
		var g := func(n: String) -> Vector3: return sk.global_transform * sk.get_bone_global_rest(sk.find_bone(n)).origin
		var leg: float = (g.call("thigh_l") as Vector3).distance_to(g.call("calf_l")) + (g.call("calf_l") as Vector3).distance_to(g.call("foot_l"))
		print("%-9s нога %.3f м, рост ~%.2f м" % [k, leg, (g.call("head") as Vector3).y + 0.12])
		a.free()
	quit()
	return true
