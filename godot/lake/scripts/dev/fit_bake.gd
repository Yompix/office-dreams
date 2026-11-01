extends SceneTree
## Тело и нижние слои одежды под верхними: найти вершины, к которым верхний слой прилегает вплотную (до 2,5 см снаружи
## по нормали; для нижней одежды — до 4,5 см: у пояса и подола в сидячей позе), и сдвинуть их внутрь — кожу на 8 мм,
## нижнюю одежду на 9 мм. Результат — data/fit/<модель>.json (в git и в сборке),
## его применяет ClothFit при создании героя (сетка и все формы сдвигаются одинаково).
##   godot --headless --path godot/lake --script res://scripts/dev/fit_bake.gd [-- noah iris …]
## В сборку не входит (scripts/dev/*).

const CAST := ["noah", "iris", "zoe", "eva", "danny", "agnes", "hale", "roman", "orderly", "victor", "anna", "noah_kid", "hale_young", "nina"]
const OUTER := ["jacket", "hoodie", "parka", "coat", "puffer", "robe", "raincoat", "vest", "sweater", "hood", "scarf"]
const INNER := ["casualsuit", "worksuit", "shift_dress", "suit", "skirt"]
const FEET := ["boots", "shoes", "flats"]
var _f := 0


func _process(_d: float) -> bool:
	_f += 1
	if _f < 2:
		return false
	var only: Array = Array(OS.get_cmdline_user_args())
	for k in CAST:
		if not only.is_empty() and not (k in only):
			continue
		var path := "res://assets/chars/%s.glb" % k
		if not ResourceLoader.exists(path):
			continue
		var model: Node3D = (load(path) as PackedScene).instantiate()
		get_root().add_child(model)
		var sk: Skeleton3D = model.find_child("Skeleton3D", true, false)
		var meshes := {}
		for mi in model.find_children("*", "MeshInstance3D", true, false):
			var m := mi as MeshInstance3D
			if m.skin == null or m.mesh.get_surface_count() != 1:
				continue
			var j := 0
			var b := m.skin.get_bind_bone(j)
			if b < 0:
				b = sk.find_bone(m.skin.get_bind_name(j))
			var to_sk := sk.get_bone_global_rest(b) * m.skin.get_bind_pose(j)
			var arr := m.mesh.surface_get_arrays(0)
			var vs: PackedVector3Array = arr[Mesh.ARRAY_VERTEX]
			var ns: PackedVector3Array = arr[Mesh.ARRAY_NORMAL]
			var pts := PackedVector3Array()
			var nrm := PackedVector3Array()
			pts.resize(vs.size())
			nrm.resize(vs.size())
			var nb := to_sk.basis.inverse().transposed()
			for i in vs.size():
				pts[i] = to_sk * vs[i]
				nrm[i] = (nb * ns[i]).normalized()
			meshes[String(m.name)] = {"pts": pts, "nrm": nrm, "to_mesh": to_sk.basis.inverse()}
		var out := {}
		var total := 0
		if OS.get_environment("FITDBG") != "":
			for mn in meshes:
				var pp: PackedVector3Array = meshes[mn].pts
				var mn_y := 1e9
				var mx_y := -1e9
				for q in pp:
					mn_y = minf(mn_y, q.y)
					mx_y = maxf(mx_y, q.y)
				print("  ", mn, " роль=", _role(mn), " вершин ", pp.size(), " y ", snappedf(mn_y, 0.01), "…", snappedf(mx_y, 0.01))
		for inner_name in meshes:
			var role := _role(inner_name)
			if role == "" or role == "outer" and not inner_name.contains("hoodie"):
				continue
			var covers: Array = []
			for cn in meshes:
				if cn == inner_name:
					continue
				var cr := _role(cn)
				if role == "skin" and cr in ["outer", "inner", "feet"]:
					covers.append(cn)
				elif role == "inner" and cr == "outer":
					covers.append(cn)
				elif inner_name.contains("hoodie") and cn.contains("hood") and not cn.contains("hoodie"):
					covers.append(cn)
			if covers.is_empty():
				continue
			var grid := {}
			for cn in covers:
				var cp: PackedVector3Array = meshes[cn].pts
				for i in cp.size():
					var c := Vector3i((cp[i] / 0.025).floor())
					if not grid.has(c):
						grid[c] = []                    # Array — по ссылке (PackedVector3Array копируется при append)
					(grid[c] as Array).append(cp[i])
			var ip: PackedVector3Array = meshes[inner_name].pts
			var inn: PackedVector3Array = meshes[inner_name].nrm
			var to_mesh: Basis = meshes[inner_name].to_mesh
			var push := 0.008 if role == "skin" else 0.009
			var moves: Array = []
			for i in ip.size():
				var p := ip[i]
				var n := inn[i]
				var c0 := Vector3i((p / 0.025).floor())
				var best := 1e9
				for dx in [-1, 0, 1]:
					for dy in [-1, 0, 1]:
						for dz in [-1, 0, 1]:
							var cell: Variant = grid.get(c0 + Vector3i(dx, dy, dz))
							if cell == null:
								continue
							for q in (cell as Array):
								var d: Vector3 = q - p
								var along := d.dot(n)
								if along < -0.002:          # верхний слой изнутри — не наш случай
									continue
								var side := (d - n * along).length()
								var lim_side := 0.02 if role == "skin" else 0.035
								var lim_along := 0.025 if role == "skin" else 0.045
								if side < lim_side and along < lim_along:
									best = minf(best, along)
				if OS.get_environment("FITDBG") != "" and i % 1500 == 0:
					var nearest := 1e9
					var nal := 0.0
					for dx2 in [-1, 0, 1]:
						for dy2 in [-1, 0, 1]:
							for dz2 in [-1, 0, 1]:
								var cell2: Variant = grid.get(c0 + Vector3i(dx2, dy2, dz2))
								if cell2 == null:
									continue
								for q2 in (cell2 as Array):
									if (q2 - p).length() < nearest:
										nearest = (q2 - p).length()
										nal = (q2 - p).dot(n)
					print("    ", inner_name, " #", i, " p=", p, " n=", n, " ближайшая ", snappedf(nearest, 0.001), " по нормали ", snappedf(nal, 0.001))
				if best < 1e8:
					var amt := push + maxf(0.0, 0.004 - best)
					var dm := to_mesh * (-n * amt)
					moves.append([i, snappedf(dm.x, 0.0001), snappedf(dm.y, 0.0001), snappedf(dm.z, 0.0001)])
			if not moves.is_empty():
				out[inner_name] = moves
				total += moves.size()
		var f := FileAccess.open("res://data/fit/%s.json" % k, FileAccess.WRITE)
		f.store_string(JSON.stringify(out))
		f.close()
		var parts := []
		for n in out:
			parts.append("%s %d" % [n, (out[n] as Array).size()])
		print("%-11s сдвинуто вершин %5d: %s" % [k, total, ", ".join(parts)])
		model.free()
	quit()
	return true


func _role(n: String) -> String:
	var l := n.to_lower()
	if l == "skin":
		return "skin"
	for w in FEET:
		if l.contains(w):
			return "feet"
	for w in OUTER:
		if l.contains(w):
			return "outer"
	for w in INNER:
		if l.contains(w):
			return "inner"
	return ""
