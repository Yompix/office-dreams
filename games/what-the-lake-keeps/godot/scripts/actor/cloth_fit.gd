class_name ClothFit
extends RefCounted
## Тело и нижние слои одежды не должны проходить сквозь верхние (грудь сквозь свитер, футболка из-под куртки).
## Заранее (scripts/dev/fit_bake.gd) для каждой модели найдены вершины, прикрытые верхним слоем вплотную, и на сколько
## их убрать внутрь (по нормали). Здесь при создании героя эти сдвиги применяются к сетке и ко всем её формам лица/тела —
## одинаково, чтобы формы не «вытаскивали» вершину обратно. Данные — data/fit/<модель>.json.

static var _cache := {}   # модель → {сетка → [[индекс, dx, dy, dz], …]}


static func apply(a: Actor, file: String) -> void:
	var path := "res://data/fit/%s.json" % file
	if not _cache.has(file):
		_cache[file] = {}
		if FileAccess.file_exists(path):
			var j: Variant = JSON.parse_string(FileAccess.get_file_as_string(path))
			if j is Dictionary:
				_cache[file] = j
	var fit: Dictionary = _cache[file]
	if fit.is_empty():
		return
	for mi in a.model.find_children("*", "MeshInstance3D", true, false):
		var m := mi as MeshInstance3D
		var key := String(m.name)
		if not fit.has(key):
			continue
		var moves: Array = fit[key]
		var src := m.mesh as ArrayMesh
		if src == null or src.get_surface_count() != 1:
			continue
		var arr := src.surface_get_arrays(0)
		var vs: PackedVector3Array = arr[Mesh.ARRAY_VERTEX]
		var bsa := src.surface_get_blend_shape_arrays(0)
		for mv in moves:
			var i := int(mv[0])
			if i >= vs.size():
				continue
			var d := Vector3(float(mv[1]), float(mv[2]), float(mv[3]))
			vs[i] += d
			for s in bsa.size():
				var sv: PackedVector3Array = (bsa[s] as Array)[Mesh.ARRAY_VERTEX]
				sv[i] += d
				(bsa[s] as Array)[Mesh.ARRAY_VERTEX] = sv
		arr[Mesh.ARRAY_VERTEX] = vs
		var nm := ArrayMesh.new()
		nm.blend_shape_mode = src.blend_shape_mode
		for i in src.get_blend_shape_count():
			nm.add_blend_shape(src.get_blend_shape_name(i))
		var per := (arr[Mesh.ARRAY_BONES] as PackedInt32Array).size() / maxi(vs.size(), 1) if arr[Mesh.ARRAY_BONES] != null else 4
		nm.add_surface_from_arrays(Mesh.PRIMITIVE_TRIANGLES, arr, bsa, {}, Mesh.ARRAY_FLAG_USE_8_BONE_WEIGHTS if per == 8 else 0)
		nm.surface_set_material(0, src.surface_get_material(0))
		var ov := m.get_surface_override_material(0)
		m.mesh = nm
		if ov:
			m.set_surface_override_material(0, ov)


## Длинная юбка «верхом» (Агнес на спине у Ноя): вершины, привязанные к обоим бёдрам сразу (середина юбки спереди и сзади),
## при разведённых бёдрах уезжают между колен — вперёд того, на ком сидят (линейный скиннинг), и юбка обёртывает его шатром.
## Своих костей и ткани у юбки нет, поэтому на время сцены смешанную часть веса таких вершин переводим на таз: середина
## висит от таза, бока — по бёдрам. on = false возвращает исходную сетку.
static func ride(a: Actor, on: bool) -> void:
	for mi in a.model.find_children("*", "MeshInstance3D", true, false):
		var m := mi as MeshInstance3D
		if not String(m.name).to_lower().contains("skirt") or m.skin == null:
			continue
		if m.has_meta("skirt_rig"):   # у юбки свои кости подола (SkirtRig): висит на пружинах, перевешивать не нужно
			continue
		if not on:
			if m.has_meta("ride_orig"):
				m.mesh = m.get_meta("ride_orig")
			continue
		if not m.has_meta("ride_orig"):
			m.set_meta("ride_orig", m.mesh)
		if not m.has_meta("ride_mesh"):
			var rm := _ride_mesh(a, m, m.get_meta("ride_orig") as ArrayMesh)
			if rm == null:
				continue
			m.set_meta("ride_mesh", rm)
		m.mesh = m.get_meta("ride_mesh")


static func _ride_mesh(a: Actor, m: MeshInstance3D, src: ArrayMesh) -> ArrayMesh:
	if src == null or src.get_surface_count() != 1:
		return null
	var arr := src.surface_get_arrays(0)
	if arr[Mesh.ARRAY_BONES] == null or arr[Mesh.ARRAY_WEIGHTS] == null:
		return null
	var bones: PackedInt32Array = arr[Mesh.ARRAY_BONES]
	var w: PackedFloat32Array = arr[Mesh.ARRAY_WEIGHTS]
	var nv := (arr[Mesh.ARRAY_VERTEX] as PackedVector3Array).size()
	var per := w.size() / maxi(nv, 1)
	var pel := -1
	var left := {}
	var right := {}
	for bi in m.skin.get_bind_count():
		var sb := m.skin.get_bind_bone(bi)
		var n := a.skel.get_bone_name(sb) if sb >= 0 else String(m.skin.get_bind_name(bi))
		if n == "pelvis":
			pel = bi
		elif n in ["thigh_l", "calf_l"]:
			left[bi] = true
		elif n in ["thigh_r", "calf_r"]:
			right[bi] = true
	if pel < 0:
		return null
	var changed := 0
	for v in nv:
		var o := v * per
		var wl := 0.0
		var wr := 0.0
		for j in per:
			if left.has(bones[o + j]):
				wl += w[o + j]
			elif right.has(bones[o + j]):
				wr += w[o + j]
		if wl <= 0.0 or wr <= 0.0:
			continue
		var f := smoothstep(0.08, 0.45, minf(wl, wr) / maxf(wl, wr))   # насколько вершина «между ног»
		if f <= 0.0:
			continue
		var moved := 0.0
		var slot := -1
		var low := 1e9
		var low_j := 0
		for j in per:
			var b := bones[o + j]
			if left.has(b) or right.has(b):
				moved += w[o + j] * f
				w[o + j] *= 1.0 - f
			if b == pel:
				slot = j
			elif not left.has(b) and not right.has(b) and w[o + j] < low:
				low = w[o + j]
				low_j = j
		if slot < 0:   # таза среди влияний нет: занимаем самое слабое не-ножное место
			slot = low_j
			moved += w[o + slot]
			bones[o + slot] = pel
			w[o + slot] = 0.0
		w[o + slot] += moved
		changed += 1
	arr[Mesh.ARRAY_BONES] = bones
	arr[Mesh.ARRAY_WEIGHTS] = w
	var nm := ArrayMesh.new()
	nm.add_surface_from_arrays(Mesh.PRIMITIVE_TRIANGLES, arr, [], {}, Mesh.ARRAY_FLAG_USE_8_BONE_WEIGHTS if per == 8 else 0)
	nm.surface_set_material(0, src.surface_get_material(0))
	return nm
