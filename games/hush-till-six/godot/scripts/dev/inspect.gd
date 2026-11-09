# Печатает устройство GLB-моделей: узлы, кости, лицевые формы, клипы. Запуск: godot --headless --path . --script res://scripts/dev/inspect.gd
extends SceneTree
func _init():
	for f in ["ethan", "anims", "tenant", "lily"]:
		var ps: PackedScene = load("res://assets/models/%s.glb" % f)
		if ps == null:
			print("!! не загрузилась ", f)
			continue
		var n := ps.instantiate()
		print("== ", f)
		_walk(n, 0)
		n.free()
	quit()
func _walk(n: Node, d: int):
	var line := "  ".repeat(d) + n.name + " <" + n.get_class() + ">"
	if n is Skeleton3D:
		var sk := n as Skeleton3D
		var names := []
		for i in range(min(sk.get_bone_count(), 70)): names.append(sk.get_bone_name(i))
		line += " bones=%d %s" % [sk.get_bone_count(), str(names)]
	if n is MeshInstance3D and (n as MeshInstance3D).mesh:
		var m: Mesh = (n as MeshInstance3D).mesh
		var bs := []
		if m is ArrayMesh:
			for i in range((m as ArrayMesh).get_blend_shape_count()): bs.append((m as ArrayMesh).get_blend_shape_name(i))
		line += " surfaces=%d blend=%d %s" % [m.get_surface_count(), bs.size(), str(bs.slice(0, 60))]
	if n is AnimationPlayer:
		var ap := n as AnimationPlayer
		var al := []
		for a in ap.get_animation_list(): al.append("%s(%.1fs)" % [a, ap.get_animation(a).length])
		line += " anims=%d %s" % [al.size(), str(al)]
	print(line)
	if d < 4:
		for c in n.get_children(): _walk(c, d + 1)
