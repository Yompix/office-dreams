extends SceneTree
## Материалы и сетки героев: тип материала, текстуры (размер), шероховатость, блик, нормали, масштаб UV, вершины, диапазон UV.
##   godot --headless --path godot/lake --script res://scripts/dev/mat_audit.gd -- [герой …]
const CAST := ["noah", "iris", "zoe", "eva", "danny", "agnes", "hale", "orderly"]
var _f := 0


func _process(_d: float) -> bool:
	_f += 1
	if _f < 2:
		return false
	var only := Array(OS.get_cmdline_user_args())
	for k in CAST:
		if not only.is_empty() and not (k in only):
			continue
		var m: Node3D = (load("res://assets/chars/%s.glb" % k) as PackedScene).instantiate()
		print("== ", k)
		for mi in m.find_children("*", "MeshInstance3D", true, false):
			var mm := mi as MeshInstance3D
			var mesh := mm.mesh
			if mesh == null:
				continue
			var arr := mesh.surface_get_arrays(0)
			var uv: PackedVector2Array = arr[Mesh.ARRAY_TEX_UV] if arr[Mesh.ARRAY_TEX_UV] != null else PackedVector2Array()
			var umin := Vector2(1e9, 1e9)
			var umax := Vector2(-1e9, -1e9)
			for q in uv:
				umin = umin.min(q)
				umax = umax.max(q)
			var mat := mesh.surface_get_material(0)
			var info := ""
			if mat is StandardMaterial3D:
				var s := mat as StandardMaterial3D
				var tex := func(t: Texture2D) -> String:
					return "%dx%d" % [t.get_width(), t.get_height()] if t else "-"
				info = "Std alb=%s nrm=%s(%.2f) rgh=%s r=%.2f met=%.2f spec=%.2f uv1=%s cull=%d tr=%d cc=%s/%.2f rim=%s" % [tex.call(s.albedo_texture), tex.call(s.normal_texture) if s.normal_enabled else "off", s.normal_scale,
					tex.call(s.roughness_texture), s.roughness, s.metallic, s.metallic_specular, str(s.uv1_scale), s.cull_mode, s.transparency, str(s.clearcoat_enabled), s.clearcoat, str(s.rim_enabled)]
			elif mat:
				info = mat.get_class()
			print("  %-34s v=%-6d uv=%s..%s %s" % [mm.name, (arr[Mesh.ARRAY_VERTEX] as PackedVector3Array).size(), str(umin.snapped(Vector2.ONE * 0.01)), str(umax.snapped(Vector2.ONE * 0.01)), info])
		m.free()
	quit()
	return true
