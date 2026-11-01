extends SceneTree
## Подол на пружинах: где суставы цепочек относительно коленей в позе шага.
## godot --headless --fixed-fps 60 --path godot/lake --script res://scripts/dev/skirt_probe.gd -- файл [клип] [время]
var a: Actor
var n := 0
var clip := "Walk"
var at := 0.35

func _init() -> void:
	var args := OS.get_cmdline_user_args()
	var f: String = args[0] if args.size() > 0 else "test/eva_s1"
	if args.size() > 1:
		clip = args[1]
	if args.size() > 2:
		at = float(args[2])
	a = Actor.make(f.get_file().get_basename().split("_")[0], f)
	root.add_child(a)
	a.play(clip, at, 0.0)
	a.ap.pause()
	a.placed()
	print("SKIRT sim: ", a.skirt_sim != null, " цепочек ", a.skirt_sim.setting_count if a.skirt_sim else 0,
		" столкновений ", a.skirt_sim.get_child_count() if a.skirt_sim else 0)

func _process(_d: float) -> bool:
	n += 1
	if n == 40:
		var sk := a.skel
		var gp := func(b: String) -> Vector3:
			var i := sk.find_bone(b)
			return sk.global_transform * sk.get_bone_global_pose(i).origin if i >= 0 else Vector3.INF
		for side in ["l", "r"]:
			print("SKIRT колено %s: %s, бедро %s" % [side, gp.call("calf_" + side), gp.call("thigh_" + side)])
		for si in 8:
			var row := []
			for j in 3:
				var p: Vector3 = gp.call("skirt_%d_%d" % [si, j])
				if p != Vector3.INF:
					row.append("(%.2f %.2f %.2f)" % [p.x, p.y, p.z])
			if row.size() > 0:
				print("SKIRT цепь %d: %s" % [si, " ".join(row)])
		for c in a.skirt_sim.get_children():
			var cc := c as SpringBoneCollision3D
			print("SKIRT столкн. ", cc.get_class(), " ", cc.bone_name, " мир ", cc.global_position)
		return true
	return false
