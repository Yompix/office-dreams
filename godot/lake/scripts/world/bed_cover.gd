extends RefCounted
## Одеяло на кровати за печью в доме Агнес (координаты дома, как Ch3.ag). Лежит на постели — или укрывает лежащую
## (Еву) от ног до бёдер: ткань ложится на тело по её позе (капсулы ног, таза и рук по костям, «натяг» ткани между
## ними), спадает к матрасу и свисает с длинных краёв кровати; у стены (изножье) заправлено.
## Раньше на кровати был сложенный брусок одеяла, а Ева лежала поверх — на родах её пальто читалось тёмным комом
## (заметил владелец, 2 ноя). Модельное одеяло (bed_blanket в village.glb) прячется, его материал — у нового.

const X_WALL := -3.12          # изножье у стены: здесь ткань заправлена
const Z_NEAR := 0.84           # длинные края матраса
const Z_FAR := 1.76
const TOP := 0.568             # верх матраса (0,56 от пола дома) + полтолщины ткани
const X_EMPTY := -1.85         # край одеяла на пустой кровати (как у сложенного)
const DROP := 0.24             # насколько свисает с боков
const R_EDGE := 0.035          # скругление на ребре матраса (снаружи — рама кровати шире на 1,5 см)
const SLOPE := 0.85            # «натяг»: ткань спадает от тела не круче этого (м на м)


## Новое одеяло (узел — ребёнок деревни). lying — кто лежит на кровати (или null).
static func build(v: Node3D, lying: Actor) -> MeshInstance3D:
	var mat: Material = null
	var model_blanket := v.find_child("bed_blanket", true, false) as MeshInstance3D
	if model_blanket:
		mat = model_blanket.get_active_material(0)
		model_blanket.visible = false
	var o := Village.ag(0, 0, 0)
	var caps := []    # [a, b, r] в координатах дома
	var x_top := X_EMPTY
	if lying != null:
		var sk := lying.skel
		var bp := func(n: String) -> Vector3:
			var i := sk.find_bone(n)
			return (sk.global_transform * sk.get_bone_global_pose(i).origin - o) if i >= 0 else Vector3.INF
		for s in ["_l", "_r"]:
			caps.append([bp.call("thigh" + s), bp.call("calf" + s), 0.12])    # бедро (с полой пальто)
			caps.append([bp.call("calf" + s), bp.call("foot" + s), 0.07])
			caps.append([bp.call("foot" + s), bp.call("ball" + s), 0.06])     # стопа носком вверх
			caps.append([bp.call("lowerarm" + s), bp.call("hand" + s), 0.05])
			caps.append([bp.call("hand" + s), bp.call("middle_01" + s), 0.04])
		caps.append([bp.call("pelvis"), bp.call("spine_01"), 0.18])
		# полы пальто или юбки (кости подола SkirtRig: skirt_<сектор>_<сустав>) — ткань ложится и на них
		for si in 8:
			var prev := Vector3.INF
			for j in 5:
				var cur: Vector3 = bp.call("skirt_%d_%d" % [si, j])
				if cur == Vector3.INF:
					break
				if prev != Vector3.INF:
					caps.append([prev, cur, 0.045])
				prev = cur
		x_top = (bp.call("pelvis") as Vector3).x + 0.1    # до низа живота
	# сетка «развёртки»: px — вдоль (от стены к краю у пояса), pz — поперёк (с запасом на свисание с обоих боков)
	var nx := 40
	var nz := 36
	var z0 := Z_NEAR - DROP
	var z1 := Z_FAR + DROP
	var hx := func(i: int) -> float:
		return X_WALL + (x_top - X_WALL) * float(i) / nx
	var hz := func(j: int) -> float:
		return z0 + (z1 - z0) * float(j) / nz
	# высота ткани над точкой матраса: тело (верх капсул) + зазор, затем «натяг» и сглаживание
	var raw := PackedFloat32Array()
	raw.resize((nx + 1) * (nz + 1))
	for i in nx + 1:
		for j in nz + 1:
			var x: float = hx.call(i)
			var z: float = clampf(hz.call(j), Z_NEAR, Z_FAR)
			var h := TOP
			for c in caps:
				var a: Vector3 = c[0]
				var b: Vector3 = c[1]
				if a == Vector3.INF or b == Vector3.INF:
					continue
				var r: float = c[2]
				var ab := Vector2(b.x - a.x, b.z - a.z)
				var t := 0.0
				if ab.length_squared() > 1e-6:
					t = clampf(Vector2(x - a.x, z - a.z).dot(ab) / ab.length_squared(), 0.0, 1.0)
				var cp := a.lerp(b, t)
				var d := Vector2(x - cp.x, z - cp.z).length()
				if d < r:
					h = maxf(h, cp.y + sqrt(r * r - d * d) + 0.012)
			raw[i * (nz + 1) + j] = h
	var hgt := raw.duplicate()
	if lying != null:
		var dx := (x_top - X_WALL) / nx
		var dz := (z1 - z0) / nz
		var rad := 5
		for i in nx + 1:
			for j in nz + 1:
				var best := raw[i * (nz + 1) + j]
				for di in range(-rad, rad + 1):
					var ii := i + di
					if ii < 0 or ii > nx:
						continue
					for dj in range(-rad, rad + 1):
						var jj := j + dj
						if jj < 0 or jj > nz:
							continue
						var q := raw[ii * (nz + 1) + jj] - SLOPE * sqrt(pow(di * dx, 2) + pow(dj * dz, 2))
						best = maxf(best, q)
				hgt[i * (nz + 1) + j] = best
		for _k in 2:   # мягче: два прохода усреднения 3×3
			var sm := hgt.duplicate()
			for i in nx + 1:
				for j in nz + 1:
					var sum := 0.0
					var n := 0
					for di in [-1, 0, 1]:
						for dj in [-1, 0, 1]:
							var ii: int = i + di
							var jj: int = j + dj
							if ii >= 0 and ii <= nx and jj >= 0 and jj <= nz:
								sum += hgt[ii * (nz + 1) + jj]
								n += 1
					sm[i * (nz + 1) + j] = maxf(raw[i * (nz + 1) + j], sum / n)
			hgt = sm
	# вершины: на матрасе — высота сетки; за длинным краем — скругление по ребру и вниз
	var st := SurfaceTool.new()
	st.begin(Mesh.PRIMITIVE_TRIANGLES)
	for i in nx + 1:
		for j in nz + 1:
			var x: float = hx.call(i)
			var pz: float = hz.call(j)
			var h: float = hgt[i * (nz + 1) + j]
			var over := 0.0
			var side := 0.0
			if pz < Z_NEAR:
				over = Z_NEAR - pz
				side = -1.0
			elif pz > Z_FAR:
				over = pz - Z_FAR
				side = 1.0
			var z := clampf(pz, Z_NEAR, Z_FAR)
			var y := h
			if over > 0.0:
				# ткань с высоты края (у тела — выше матраса) огибает ребро и висит
				var arc := PI * 0.5 * R_EDGE
				if over < arc:
					var th := over / R_EDGE
					z += side * R_EDGE * sin(th)
					y -= R_EDGE * (1.0 - cos(th))
				else:
					z += side * R_EDGE
					y -= R_EDGE + (over - arc)
			st.set_uv(Vector2(x * 2.0, pz * 2.0))
			st.add_vertex(Vector3(x, y, z))
	for i in nx:
		for j in nz:
			var k0 := i * (nz + 1) + j
			var k1 := k0 + nz + 1
			st.add_index(k0)
			st.add_index(k0 + 1)
			st.add_index(k1)
			st.add_index(k0 + 1)
			st.add_index(k1 + 1)
			st.add_index(k1)
	st.generate_normals()
	st.generate_tangents()
	var mi := MeshInstance3D.new()
	mi.name = "BedCover"
	mi.mesh = st.commit()
	if mat == null:
		var m := StandardMaterial3D.new()
		m.albedo_color = Color(0.42, 0.12, 0.1)
		m.roughness = 0.9
		mat = m
	var m2 := mat.duplicate() as Material
	if m2 is BaseMaterial3D:
		(m2 as BaseMaterial3D).cull_mode = BaseMaterial3D.CULL_DISABLED   # видна и изнанка свисающего края
	mi.material_override = m2
	mi.position = o
	v.add_child(mi)
	return mi
