extends SceneTree
## Аудит героев: модель, скелет, сетки, формы лица (blend shapes), волосы, одежда, материалы, клипы.
##   godot --headless --path godot/lake --script res://scripts/dev/char_audit.gd [-- --json /tmp/chars.json]
## В сборку не входит (scripts/dev/*).

const CAST := ["noah", "iris", "zoe", "eva", "danny", "agnes", "hale", "roman", "orderly", "victor", "anna", "noah_kid", "hale_young", "nina"]


func _init() -> void:
	var out := {}
	for k in CAST:
		var path := "res://assets/chars/%s.glb" % k
		if not ResourceLoader.exists(path):
			continue
		var raw: Node3D = (load(path) as PackedScene).instantiate()
		var sk0: Skeleton3D = raw.find_child("Skeleton3D", true, false)
		var bones := []
		for i in sk0.get_bone_count():
			bones.append(sk0.get_bone_name(i))
		raw.free()
		var a := Actor.make(k, k)
		var info := {"bones": bones.size(), "bone_names": bones, "meshes": [], "hair": [], "clips": a.ap.get_animation_list().size()}
		for mi in a.model.find_children("*", "MeshInstance3D", true, false):
			var m := mi as MeshInstance3D
			var am := m.mesh as ArrayMesh
			var verts := 0
			for s in m.mesh.get_surface_count():
				var arr := m.mesh.surface_get_arrays(s)
				verts += (arr[Mesh.ARRAY_VERTEX] as PackedVector3Array).size()
			var shapes := []
			if am:
				for i in am.get_blend_shape_count():
					shapes.append(String(am.get_blend_shape_name(i)))
			# какие кости реально двигают эту сетку (по весам)
			var used := {}
			if m.skin:
				for s in m.mesh.get_surface_count():
					var arr := m.mesh.surface_get_arrays(s)
					var bi: Variant = arr[Mesh.ARRAY_BONES]
					var bw: Variant = arr[Mesh.ARRAY_WEIGHTS]
					if bi == null or bw == null:
						continue
					for j in (bi as PackedInt32Array).size():
						if float(bw[j]) > 0.01:
							var bix: int = int(bi[j])
							var b: int = m.skin.get_bind_bone(bix) if bix < m.skin.get_bind_count() else -1
							var nm: String = a.skel.get_bone_name(b) if b >= 0 else String(m.skin.get_bind_name(bix))
							used[nm] = true
			var mats := []
			for s in m.mesh.get_surface_count():
				var mt := m.get_active_material(s)
				mats.append(mt.get_class() if mt else "—")
			var e := {"name": String(m.name), "verts": verts, "surfaces": m.mesh.get_surface_count(), "shapes": shapes.size(),
				"shape_names": shapes, "bones_used": used.keys(), "mats": mats}
			info.meshes.append(e)
			if String(m.name).to_lower().contains("hair"):
				info.hair.append(e)
		out[k] = info
		a.free()
	# клипы
	var clips := {}
	var probe := Actor.make("noah", "noah")
	for n in probe.ap.get_animation_list():
		var an := probe.ap.get_animation(n)
		clips[n] = {"len": snappedf(an.length, 0.01), "tracks": an.get_track_count(), "loop": an.loop_mode}
	probe.free()
	out["_clips"] = clips
	var args := OS.get_cmdline_user_args()
	var jp := "/tmp/lake_chars.json"
	if "--json" in args:
		jp = args[args.find("--json") + 1]
	var f := FileAccess.open(jp, FileAccess.WRITE)
	f.store_string(JSON.stringify(out, " "))
	f.close()
	print("аудит героев → ", jp, " (", out.size() - 1, " героев, ", clips.size(), " клипов)")
	quit()
