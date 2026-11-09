class_name StationProps
extends RefCounted
## Заправка «24», мелочи для прогулки (глава 1): автомат с кофе и шоколадом у угла витрины, таксофон и доска
## объявлений на боковой стене магазина (под жёлтой лампой), ведро со щёткой для стёкол у колонки.
## Всё кодом, простыми формами; ставится под station_root — уезжаем, и оно исчезает вместе с заправкой.

const F := DriveScene.FLOOR


static func _mat(c: Color, rough := 0.6, metal := 0.0, emit := Color(0, 0, 0), e := 0.0) -> StandardMaterial3D:
	var m := StandardMaterial3D.new()
	m.albedo_color = c
	m.roughness = rough
	m.metallic = metal
	if e > 0.0:
		m.emission_enabled = true
		m.emission = emit
		m.emission_energy_multiplier = e
	return m


static func _box(parent: Node3D, size: Vector3, pos: Vector3, m: Material, rot := Vector3.ZERO) -> MeshInstance3D:
	var mi := MeshInstance3D.new()
	var bm := BoxMesh.new()
	bm.size = size
	mi.mesh = bm
	mi.material_override = m
	mi.position = pos
	mi.rotation_degrees = rot
	parent.add_child(mi)
	return mi


static func _cyl(parent: Node3D, r: float, h: float, pos: Vector3, m: Material, r2 := -1.0) -> MeshInstance3D:
	var mi := MeshInstance3D.new()
	var cm := CylinderMesh.new()
	cm.top_radius = r
	cm.bottom_radius = r if r2 < 0.0 else r2
	cm.height = h
	cm.radial_segments = 20
	mi.mesh = cm
	mi.material_override = m
	mi.position = pos
	parent.add_child(mi)
	return mi


## Цилиндр от точки a (радиус ra) до точки b (радиус rb, по умолчанию тот же) — в осях родителя.
static func _rod(parent: Node3D, a: Vector3, b: Vector3, ra: float, m: Material, rb := -1.0) -> MeshInstance3D:
	var mi := _cyl(parent, ra if rb < 0.0 else rb, a.distance_to(b), (a + b) * 0.5, m, ra)
	mi.basis = _along(b - a)
	return mi


## Капсула от точки a до точки b (концы скруглены радиусом r).
static func _capsule(parent: Node3D, a: Vector3, b: Vector3, r: float, m: Material) -> MeshInstance3D:
	var mi := MeshInstance3D.new()
	var cm := CapsuleMesh.new()
	cm.radius = r
	cm.height = a.distance_to(b) + 2.0 * r
	cm.radial_segments = 20
	mi.mesh = cm
	mi.material_override = m
	parent.add_child(mi)
	mi.position = (a + b) * 0.5
	mi.basis = _along(b - a)
	return mi


## Поворот, который ставит ось Y по направлению d.
static func _along(d: Vector3) -> Basis:
	var n := d.normalized()
	if n.dot(Vector3.UP) < -0.9999:
		return Basis(Vector3.RIGHT, PI)
	return Basis(Quaternion(Vector3.UP, n))


## Пистолет заправочной колонки в правой руке Ноя — в осях кости hand_r: +Y — от запястья к пальцам, +Z — от мизинца
## к указательному, −X — сторона ладони (замер костей пальцев). Был коробкой с трубкой, и коробка шла вдоль пальцев
## сквозь ладонь — на крупном плане это было видно. Теперь: обрезиненная рукоять с рёбрами поперёк ладони (в кулаке),
## литой корпус, стальной носик с пружиной у основания, рычаг под пальцами, защитная скоба, муфта шланга с гайкой.
## Носик — на прежней линии: при заправке он входит в горловину там же, где раньше.
const NOZZLE_HOSE := Vector3(-0.026, 0.096, -0.094)   # куда входит шланг: муфта со стороны мизинца
const NOZZLE_SB := Vector3(0.0, 0.105, 0.0806)        # основание носика (носик — на прежней линии)
const NOZZLE_SD := Vector3(0.0, 0.259, 0.966)         # направление носика: вдоль рукояти, к указательному, чуть к пальцам
static func nozzle_parts(parent: Node3D) -> void:
	var rubber := _mat(Color(0.025, 0.025, 0.028), 0.8)
	# металл не зеркальный: ночью зеркальная сталь отражает тёмное небо и выглядит чёрной трубкой (проверено кадром)
	var alu := _mat(Color(0.62, 0.63, 0.64), 0.45, 0.5)
	var steel := _mat(Color(0.75, 0.76, 0.78), 0.32, 0.55)
	var wire := _mat(Color(0.05, 0.05, 0.055), 0.5, 0.4)
	var hp := Vector3(-0.026, 0.096, -0.058)   # рукоять: от мизинца…
	var hi := Vector3(-0.026, 0.096, 0.06)     # …к указательному
	_rod(parent, hp, hi, 0.0155, rubber)
	for k in 5:   # рёбра на резине
		var z := -0.042 + k * 0.021
		_rod(parent, Vector3(hp.x, hp.y, z - 0.002), Vector3(hp.x, hp.y, z + 0.002), 0.0168, rubber)
	_rod(parent, hp, NOZZLE_HOSE, 0.0175, alu)   # муфта шланга
	var nut := _rod(parent, Vector3(hp.x, hp.y, -0.068), Vector3(hp.x, hp.y, -0.08), 0.0205, alu)
	(nut.mesh as CylinderMesh).radial_segments = 6   # гайка — шестигранник
	var sb := NOZZLE_SB
	var sd := NOZZLE_SD
	_capsule(parent, Vector3(-0.026, 0.097, 0.036), sb + Vector3(0.0, 0.0, 0.006), 0.019, alu)   # литой корпус — от кулака к носику
	_rod(parent, sb - sd * 0.004, sb + sd * 0.01, 0.0145, alu)   # гайка носика
	_rod(parent, sb, sb + sd * 0.155, 0.0105, steel, 0.0094)   # носик
	for k in 6:   # пружина у основания носика
		var t := MeshInstance3D.new()
		var tm := TorusMesh.new()
		tm.inner_radius = 0.0104
		tm.outer_radius = 0.0128
		t.mesh = tm
		t.material_override = steel
		parent.add_child(t)
		t.position = sb + sd * (0.012 + k * 0.0065)
		t.basis = _along(sd)
	_rod(parent, sb + sd * 0.149, sb + sd * 0.156, 0.0101, wire)   # кромка на конце носика
	_box(parent, Vector3(0.007, 0.014, 0.085), Vector3(-0.06, 0.104, 0.008), rubber)   # рычаг — под пальцами (чёрный пластик)
	for pr in [[Vector3(-0.076, 0.1, -0.05), Vector3(-0.076, 0.1, 0.058)],   # защитная скоба вокруг рычага
			[Vector3(-0.076, 0.1, 0.058), Vector3(-0.03, 0.1, 0.07)],
			[Vector3(-0.076, 0.1, -0.05), Vector3(-0.03, 0.098, -0.062)]]:
		_rod(parent, pr[0], pr[1], 0.0034, wire)


static func _text(parent: Node3D, t: String, pos: Vector3, size: int, col: Color, px := 0.0016, rot := Vector3.ZERO, w := 0.0, kind := "notice") -> Label3D:
	var lb := Label3D.new()
	lb.text = t
	lb.font_size = size
	lb.pixel_size = px
	lb.modulate = col
	lb.outline_size = 0
	lb.shaded = true
	lb.double_sided = false
	lb.position = pos
	lb.rotation_degrees = rot
	if w > 0.0:
		lb.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
		lb.width = w / px
	Signs.style(lb, kind)
	parent.add_child(lb)
	Signs.flush.call_deferred(lb)   # к листу/табличке (висели в 4–6 мм перед ними)
	return lb


static func build(root: Node3D, lang: String) -> Dictionary:
	var out := {}
	var ru := lang == "ru"
	# ---------- автомат: на земле у угла витрины (z −11), лицом к колонкам ----------
	var vm := Node3D.new()
	vm.name = "vending"
	root.add_child(vm)
	vm.position = Vector3(20.66, 0.0, -11.0)
	var red := _mat(Color(0.42, 0.05, 0.04), 0.45, 0.2)
	var dark := _mat(Color(0.05, 0.05, 0.055), 0.5, 0.3)
	_box(vm, Vector3(0.62, 1.86, 0.86), Vector3(0, 0.93, 0), red)
	var glass := _mat(Color(0.75, 0.85, 0.9, 0.18), 0.05)
	glass.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
	var inner := _mat(Color(0.88, 0.93, 0.98), 0.8, 0.0, Color(0.84, 0.92, 1.0), 0.6)   # бело-голубое нутро (лист «24»)
	_box(vm, Vector3(0.02, 1.08, 0.56), Vector3(-0.3, 1.15, -0.1), inner)   # светлое нутро за стеклом
	var cols := [Color(0.8, 0.12, 0.08), Color(0.15, 0.3, 0.7), Color(0.85, 0.7, 0.1), Color(0.2, 0.5, 0.2), Color(0.5, 0.25, 0.08)]
	for row in 4:
		_box(vm, Vector3(0.05, 0.012, 0.54), Vector3(-0.31, 0.72 + row * 0.26, -0.1), dark)
		for i in 5:
			var c: Color = cols[(row * 2 + i) % cols.size()]
			_box(vm, Vector3(0.05, 0.15, 0.08), Vector3(-0.322, 0.81 + row * 0.26, -0.33 + i * 0.115), _mat(c, 0.5, 0.0, c, 0.15))
	_box(vm, Vector3(0.015, 1.12, 0.6), Vector3(-0.325, 1.15, -0.1), glass)
	_box(vm, Vector3(0.03, 0.62, 0.2), Vector3(-0.32, 1.2, 0.29), dark)    # панель: щель для монет, кнопки
	for k in 6:
		_box(vm, Vector3(0.012, 0.035, 0.05), Vector3(-0.34, 1.38 - k * 0.07, 0.26), _mat(Color(0.8, 0.8, 0.75), 0.4, 0.0, Color(0.9, 0.9, 0.8), 0.25))
	_box(vm, Vector3(0.03, 0.16, 0.5), Vector3(-0.32, 0.36, -0.1), dark)   # лоток
	var vt := _text(vm, "КОФЕ · ШОКОЛАД" if ru else "COFFEE · CANDY", Vector3(-0.316, 1.79, 0.0), 44, Color(1.0, 0.92, 0.8), 0.0016, Vector3(0, -90, 0), 0.0, "label")
	vt.shaded = false   # светящаяся надпись — видна и в темноте
	var vl := OmniLight3D.new()   # стекло светится на снег
	vl.position = Vector3(-0.6, 1.2, -0.1)
	vl.light_color = Color(0.82, 0.9, 1.0)   # бело-голубой свет ложится на лицо Ноя
	vl.light_energy = 0.5
	vl.omni_range = 2.2
	vm.add_child(vl)
	out["vending"] = vm
	# ---------- боковая стена магазина (z = 1, лицом к +Z): таксофон, доска объявлений, лампа ----------
	var side := Node3D.new()
	side.name = "side_wall"
	root.add_child(side)
	var blue := _mat(Color(0.12, 0.2, 0.32), 0.5, 0.4)
	var steel := _mat(Color(0.55, 0.56, 0.58), 0.35, 0.8)
	var ph := Node3D.new()
	ph.name = "payphone"
	side.add_child(ph)
	ph.position = Vector3(23.2, 0.0, 1.0)
	_box(ph, Vector3(0.62, 0.06, 0.34), Vector3(0, 2.06, 0.17), blue)      # козырёк
	_box(ph, Vector3(0.05, 0.55, 0.3), Vector3(-0.29, 1.8, 0.15), blue)
	_box(ph, Vector3(0.05, 0.55, 0.3), Vector3(0.29, 1.8, 0.15), blue)
	_box(ph, Vector3(0.3, 0.5, 0.14), Vector3(0, 1.45, 0.07), _mat(Color(0.62, 0.64, 0.66), 0.4, 0.7))   # корпус
	var key_m := _mat(Color(0.7, 0.71, 0.72), 0.42, 0.55)   # металлические кнопки (лист «24»: «палец на металлических кнопках»)
	var digits := ["1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#"]
	for k in 4:
		for j in 3:
			var kb := _box(ph, Vector3(0.045, 0.035, 0.012), Vector3(-0.06 + j * 0.06, 1.5 - k * 0.055, 0.145), key_m)
			_text(kb, digits[k * 3 + j], Vector3(0, 0, 0.0065), 40, Color(0.08, 0.08, 0.09), 0.0006, Vector3.ZERO, 0.0, "label")
	var rcv := _box(ph, Vector3(0.07, 0.24, 0.07), Vector3(-0.2, 1.42, 0.12), dark)   # трубка на рычаге
	rcv.name = "receiver"
	var pcs := _cyl(ph, 0.008, 0.45, Vector3(-0.2, 1.12, 0.13), dark)      # стальной шнур (трубка на рычаге)
	pcs.name = "payphone_cord"
	out["payphone"] = ph
	var bd := Node3D.new()
	side.add_child(bd)
	bd.position = Vector3(25.25, 0.0, 1.0)
	var cork := _mat(Color(0.48, 0.34, 0.2), 0.9)
	_box(bd, Vector3(1.34, 0.88, 0.03), Vector3(0, 1.5, 0.02), _mat(Color(0.25, 0.18, 0.1), 0.8))
	_box(bd, Vector3(1.26, 0.8, 0.02), Vector3(0, 1.5, 0.035), cork)
	var paper := _mat(Color(0.86, 0.84, 0.78), 0.95)
	var paper2 := _mat(Color(0.93, 0.9, 0.7), 0.95)
	var ink := Color(0.08, 0.08, 0.09)
	var n1 := _box(bd, Vector3(0.3, 0.22, 0.004), Vector3(-0.42, 1.68, 0.048), paper, Vector3(0, 0, 3))
	_text(n1, "ПРОДАМ\nДРОВА\nтел. 2-14" if ru else "FIREWOOD\nFOR SALE\ncall 2-14", Vector3(0, 0, 0.003), 46, ink, 0.0016, Vector3.ZERO, 0.0, "hand")
	var n2 := _box(bd, Vector3(0.27, 0.34, 0.004), Vector3(-0.42, 1.33, 0.048), paper2, Vector3(0, 0, -4))
	_text(n2, "ПРОПАЛА\nСОБАКА\n\nрыжая,\nзовут Лиса" if ru else "LOST\nDOG\n\nginger,\nanswers\nto Foxy", Vector3(0, 0, 0.003), 38, ink, 0.0016, Vector3.ZERO, 0.0, "hand")
	var flut: Array = []   # что треплет ветер: [узел, поворот в покое, размах, фаза] — StationProps.flutter
	for k in 7:   # бахрома отрывных номеров; два уже оторваны (лист «24»); каждая — на шарнире по верхнему краю
		if k == 2 or k == 5:
			continue
		var fp := Node3D.new()
		n2.add_child(fp)
		fp.position = Vector3(-0.105 + k * 0.035, -0.17, 0.001)
		fp.rotation_degrees = Vector3(0, 0, (k % 3 - 1) * 4.0)
		_box(fp, Vector3(0.028, 0.07, 0.002), Vector3(0, -0.035, 0.0), paper2)
		flut.append([fp, 0.0, -14.0, k * 1.37])
	# выцветший плакат санатория: голубое небо, ели, белый корпус
	var pst := Node3D.new()
	bd.add_child(pst)
	pst.position = Vector3(0.22, 1.5, 0.048)
	pst.rotation_degrees = Vector3(0, 0, 1.5)
	var pmat := _mat(Color(0.7, 0.78, 0.8), 0.95)
	_box(pst, Vector3(0.6, 0.6, 0.004), Vector3(0, 0.05, 0), pmat)            # плакат без правого нижнего угла:
	_box(pst, Vector3(0.5, 0.1, 0.004), Vector3(-0.05, -0.3, 0), pmat)        # угол — отдельный и загнут
	var flap := Node3D.new()   # загнувшийся угол: Ной расправляет его пальцем — и он снова загибается (лист «24»)
	flap.name = "poster_corner"
	pst.add_child(flap)
	flap.position = Vector3(0.25, -0.25, 0.0)
	var fl := _box(flap, Vector3(0.1, 0.1, 0.004), Vector3(0, -0.05, 0), pmat)
	pmat.cull_mode = BaseMaterial3D.CULL_DISABLED
	flap.rotation_degrees = Vector3(-58, 0, 0)
	fl.name = "flap"
	flut.append([flap, -52.0, -12.0, 0.4])
	root.set_meta("flutter", flut)
	_box(pst, Vector3(0.52, 0.26, 0.003), Vector3(0, 0.04, 0.003), _mat(Color(0.62, 0.75, 0.82), 0.95))   # небо — ниже названия (край не режет буквы)
	_box(pst, Vector3(0.24, 0.09, 0.003), Vector3(0.03, -0.02, 0.005), _mat(Color(0.93, 0.93, 0.9), 0.9))   # корпус
	for k in 4:
		_box(pst, Vector3(0.03, 0.022, 0.002), Vector3(-0.05 + k * 0.05, -0.01, 0.007), _mat(Color(0.35, 0.42, 0.48), 0.9))
	var pine := _mat(Color(0.16, 0.3, 0.23), 0.9)
	for i in 7:   # ели: три яруса треугольников (плоские призмы) на стволе
		var x := -0.255 + i * 0.085 + (0.012 if i % 2 == 0 else 0.0)
		if absf(x - 0.03) < 0.13:
			continue
		var h := 0.15 + 0.035 * float((i * 5) % 3)
		_box(pst, Vector3(0.012, h * 0.25, 0.002), Vector3(x, -0.08 + h * 0.06, 0.0055), _mat(Color(0.25, 0.18, 0.12), 0.9))
		for t in 3:
			var w := h * (0.55 - t * 0.13)
			var tri := MeshInstance3D.new()
			var pm := PrismMesh.new()
			pm.size = Vector3(w, h * 0.42, 0.002)
			tri.mesh = pm
			tri.material_override = pine
			tri.position = Vector3(x, -0.06 + h * (0.12 + t * 0.24), 0.006 + t * 0.0004)
			pst.add_child(tri)
	_box(pst, Vector3(0.52, 0.04, 0.003), Vector3(0, -0.1, 0.006), _mat(Color(0.18, 0.32, 0.25), 0.9))
	_text(pst, "САНАТОРИЙ\n«ТИХИЕ СОСНЫ»" if ru else "QUIET PINES\nSANATORIUM", Vector3(0, 0.26, 0.006), 44, Color(0.1, 0.22, 0.3), 0.0016, Vector3.ZERO, 0.0, "shop")
	_text(pst, "сон · тишина · сосновый воздух" if ru else "sleep · silence · pine air", Vector3(0, -0.2, 0.006), 26, Color(0.12, 0.2, 0.25))
	_text(pst, "Северная трасса" if ru else "Northern Highway", Vector3(0, -0.29, 0.006), 22, Color(0.25, 0.3, 0.32))
	out["board"] = bd
	var lamp := _box(side, Vector3(0.3, 0.12, 0.2), Vector3(24.25, 2.95, 1.1), _mat(Color(0.2, 0.2, 0.2), 0.6, 0.5))
	_box(lamp, Vector3(0.24, 0.02, 0.15), Vector3(0, -0.07, 0.01), _mat(Color(1.0, 0.8, 0.5), 0.5, 0.0, Color(1.0, 0.72, 0.38), 2.2))
	var sl := SpotLight3D.new()   # жёлтая лампа над таксофоном
	sl.position = Vector3(24.25, 2.85, 1.25)
	sl.rotation_degrees = Vector3(-70, 0, 0)
	sl.light_color = Color(1.0, 0.72, 0.42)
	sl.light_energy = 2.0
	sl.spot_range = 6.5
	sl.spot_angle = 58.0
	sl.spot_attenuation = 0.8
	sl.light_volumetric_fog_energy = 0.5
	side.add_child(sl)
	# ---------- ведро со щёткой для стёкол: на островке у колонки Ноя ----------
	var bk := Node3D.new()
	bk.name = "bucket"
	root.add_child(bk)
	bk.position = Vector3(12.45, 0.18, -8.55)   # у края островка: Ной моет руки с земли, присев
	# ведро открытое: стенки пластика видны и изнутри, сверху — ободок; вода и корка льда — на 3 см ниже края,
	# их видно сверху, руки уходят в воду (раньше ведро было сплошным цилиндром — вода и лёд прятались внутри)
	var plastic := _mat(Color(0.12, 0.3, 0.55), 0.5)
	plastic.cull_mode = BaseMaterial3D.CULL_DISABLED
	var shell := _cyl(bk, 0.17, 0.32, Vector3(0, 0.16, 0), plastic, 0.14)
	shell.name = "shell"
	(shell.mesh as CylinderMesh).cap_top = false
	var rim := MeshInstance3D.new()
	rim.name = "rim"
	var tm := TorusMesh.new()
	tm.inner_radius = 0.163
	tm.outer_radius = 0.181
	tm.rings = 28
	tm.ring_segments = 6
	rim.mesh = tm
	rim.material_override = plastic
	rim.position = Vector3(0, 0.32, 0)
	bk.add_child(rim)
	var water := _mat(Color(0.25, 0.45, 0.6, 0.72), 0.08)   # чуть прозрачная: руки под водой видны тенью
	water.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
	var wt := _cyl(bk, 0.16, 0.01, Vector3(0, 0.29, 0), water)
	wt.name = "water"
	var ice := _mat(Color(0.85, 0.92, 0.97, 0.55), 0.25)
	ice.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
	var ic := _cyl(bk, 0.16, 0.006, Vector3(0, 0.3, 0), ice)
	ic.name = "ice"
	var st := _cyl(bk, 0.012, 0.7, Vector3(0.06, 0.55, 0.0), _mat(Color(0.1, 0.1, 0.1), 0.5))
	st.rotation_degrees = Vector3(0, 0, -14)
	_box(st, Vector3(0.05, 0.04, 0.24), Vector3(0, 0.36, 0), _mat(Color(0.08, 0.08, 0.08), 0.7))
	out["bucket"] = bk
	snow_banks(root)
	return out


## Заправка: где запястье и как повёрнута кисть, чтобы носик пистолета вошёл в горловину вниз под 28° на 7 см.
## Хват — «как молоток» (рукоять поперёк ладони, носик продолжает её), поэтому крен вокруг носика подбирается так,
## чтобы пальцы смотрели по предплечью (от примерного локтя к запястью), — запястье не выламывается. [запястье, базис]
static func nozzle_grip(na: Node3D, sk: Skeleton3D, car: Node3D) -> Array:
	var f_at: Vector3 = car.to_global(FILLER)
	var inn := (car.global_transform.basis.orthonormalized() * Vector3(1, 0, 0)).normalized()   # внутрь кузова
	var a := deg_to_rad(28.0)
	var sd := (inn * cos(a) + Vector3.DOWN * sin(a)).normalized()
	var ph := NOZZLE_SB + NOZZLE_SD.normalized() * 0.085   # точка носика на срезе горловины: 7 см носика — внутри
	var sh := sk.global_transform * sk.get_bone_global_pose(sk.find_bone("upperarm_r")).origin
	var out := -na.global_transform.basis.x.normalized()   # правая сторона героя
	var w_at := f_at - inn * 0.12
	var b := Basis()
	for it in 4:
		var el := sh + (w_at - sh) * 0.5 + Vector3.DOWN * 0.12 + out * 0.08   # примерный локоть: ниже и снаружи
		var f := (w_at - el).normalized()
		var fp := f - sd * f.dot(sd)
		if fp.length() < 0.001:
			fp = Vector3.DOWN - sd * Vector3.DOWN.dot(sd)
		var w := -fp.normalized()
		b = Basis(sd.cross(w), sd * 0.259 - w * 0.966, sd * 0.966 + w * 0.259)
		w_at = f_at - b * ph
	return [w_at, b]


## Лючок бака на левом заднем крыле машины (машина у колонки: нос к −Z, левый бок — к колонке Ноя). Замер кузова
## sedan.glb: x ±0,894, y 0,21…1,60, z −2,86…2,21; горловина — над задним колесом, перед крышкой багажника.
const FILLER := Vector3(-0.9, 0.9, 1.68)   # горловина в осях машины
static func fuel_flap(car: Node3D, open: bool) -> void:
	var ff: Node3D = car.get_node_or_null("fuel_flap")
	if ff == null:
		ff = Node3D.new()
		ff.name = "fuel_flap"
		car.add_child(ff)
		ff.position = FILLER + Vector3(0.012, 0, 0)
		var dark := _mat(Color(0.02, 0.02, 0.022), 0.6)
		_box(ff, Vector3(0.02, 0.15, 0.15), Vector3(0.0, 0, 0), dark)                 # ниша лючка
		var cap := _cyl(ff, 0.034, 0.03, Vector3(-0.012, 0, 0), _mat(Color(0.06, 0.06, 0.06), 0.5))   # крышка горловины
		cap.rotation_degrees = Vector3(0, 0, 90)
		cap.name = "cap"
		var hinge := Node3D.new()   # петля лючка — у его переднего края (к носу машины)
		hinge.name = "hinge"
		hinge.position = Vector3(-0.004, 0, -0.08)
		ff.add_child(hinge)
		var paint: Material = _mat(Color(0.09, 0.12, 0.2), 0.35, 0.3)
		for n in car.find_children("body*", "MeshInstance3D", true, false):
			var m: Material = (n as MeshInstance3D).get_active_material(0)
			if m:
				paint = m
				break
		var lid := _box(hinge, Vector3(0.008, 0.155, 0.155), Vector3(-0.006, 0, 0.0775), paint)
		lid.name = "lid"
	var hg: Node3D = ff.get_node("hinge")
	var tw := ff.create_tween()
	tw.tween_property(hg, "rotation_degrees:y", -78.0 if open else 0.0, 0.35).set_trans(Tween.TRANS_SINE)
	var cap_n: Node3D = ff.get_node("cap")
	cap_n.visible = not open   # крышка снята — лежит в руке/на лючке (кадр её не держит)


## Пистолет и шланг в держателе колонки Ноя (модель станции): спрятать, пока пистолет у Ноя в руке, и вернуть.
static func holster(root: Node3D, show: bool) -> void:
	for pair in [["nozzle", Vector3(12.34, 1.16, -4.7)], ["hose", Vector3(12.5, 0.8, -4.55)]]:
		var best: Node3D = null
		var bd := 0.6
		for n in root.find_children(String(pair[0]) + "*", "MeshInstance3D", true, false):
			var c: Vector3 = (n as MeshInstance3D).global_transform * (n as MeshInstance3D).get_aabb().get_center()
			var dd: float = c.distance_to(pair[1])
			if dd < bd:
				bd = dd
				best = n
		if best:
			best.visible = show


## Шланг от колонки к пистолету в руке: трубка по кривой с провисом (перестраивается каждый кадр, пока пистолет в руке).
static func hose_mesh(mi: MeshInstance3D, a: Vector3, b: Vector3) -> void:
	var st := SurfaceTool.new()
	st.begin(Mesh.PRIMITIVE_TRIANGLES)
	var sag := Vector3(0, -minf(1.0, 0.25 + a.distance_to(b) * 0.45), 0)
	var c1 := a + Vector3(0.25, -0.5, 0.0) + sag * 0.6
	var c2 := b + Vector3(-0.15, -0.35, 0.05) + sag * 0.5
	var N := 18
	var R := 0.016
	var pts: Array[Vector3] = []
	for i in N + 1:
		var t := float(i) / N
		var u := 1.0 - t
		pts.append(a * u * u * u + c1 * 3.0 * u * u * t + c2 * 3.0 * u * t * t + b * t * t * t)
	for i in N:
		var p0: Vector3 = pts[i]
		var p1: Vector3 = pts[i + 1]
		var dir := (p1 - p0).normalized()
		var side := dir.cross(Vector3.UP).normalized() if absf(dir.dot(Vector3.UP)) < 0.95 else dir.cross(Vector3.RIGHT).normalized()
		var up := side.cross(dir).normalized()
		for k in 6:
			var a0 := TAU * k / 6.0
			var a1 := TAU * (k + 1) / 6.0
			var o0 := (side * cos(a0) + up * sin(a0)) * R
			var o1 := (side * cos(a1) + up * sin(a1)) * R
			st.set_normal(o0.normalized()); st.add_vertex(p0 + o0)
			st.set_normal(o1.normalized()); st.add_vertex(p0 + o1)
			st.set_normal(o1.normalized()); st.add_vertex(p1 + o1)
			st.set_normal(o0.normalized()); st.add_vertex(p0 + o0)
			st.set_normal(o1.normalized()); st.add_vertex(p1 + o1)
			st.set_normal(o0.normalized()); st.add_vertex(p1 + o0)
	mi.mesh = st.commit()


## Табло колонки Ноя (островок x = 12, сторона к машине): литры и сумма бегут, пока идёт бензин (заправка удержанием).
const PRICE := 55.4   # цена литра на табло
static func pump_display(root: Node3D, liters: float, lang := "ru") -> void:
	var lb := root.get_node_or_null("pump_digits") as Label3D
	if lb == null:
		lb = Label3D.new()
		lb.name = "pump_digits"
		lb.position = Vector3(12.298, 1.43, -5.0)   # на стекле табло (pump_display: x 12,285 + 0,01)
		lb.rotation_degrees = Vector3(0, 90, 0)     # лицом к машине (+x)
		lb.pixel_size = 0.0011
		lb.font_size = 60
		lb.line_spacing = -6.0
		lb.modulate = Color(1.0, 0.68, 0.24)
		lb.outline_size = 0
		lb.shaded = false
		lb.double_sided = false
		lb.horizontal_alignment = HORIZONTAL_ALIGNMENT_RIGHT
		lb.texture_filter = BaseMaterial3D.TEXTURE_FILTER_LINEAR_WITH_MIPMAPS
		root.add_child(lb)
	lb.text = "%.2f %s\n%.2f" % [liters, "Л" if lang == "ru" else "L", liters * PRICE]


## Лёд в ведре (действие «лёд», два нажатия): 1 — по корке бегут белые трещины; 2 — корка разломана на пластины,
## между ними тёмная вода; пластины покачиваются.
static func ice_stage(root: Node3D, n: int) -> void:
	var bk := root.get_node_or_null("bucket")
	if bk == null:
		return
	var ic := bk.get_node_or_null("ice") as MeshInstance3D
	if n == 1:
		if bk.get_node_or_null("cracks"):
			return
		var q := MeshInstance3D.new()
		q.name = "cracks"
		var pm := PlaneMesh.new()
		pm.size = Vector2(0.32, 0.32)
		q.mesh = pm
		var m := StandardMaterial3D.new()
		m.albedo_texture = _crack_tex()
		m.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
		m.roughness = 0.2
		m.shading_mode = BaseMaterial3D.SHADING_MODE_PER_PIXEL
		q.material_override = m
		q.position = Vector3(0, 0.3045, 0)
		bk.add_child(q)
		return
	for nm in ["cracks", "ice"]:
		var nd := bk.get_node_or_null(nm)
		if nd:
			(nd as Node3D).visible = false
	if bk.get_node_or_null("plates"):
		return
	var pl := Node3D.new()
	pl.name = "plates"
	bk.add_child(pl)
	var im := _mat(Color(0.86, 0.93, 0.98, 0.62), 0.18)
	im.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
	var rng := RandomNumberGenerator.new()
	rng.seed = 24
	for k in 6:   # обломки корки: тонкие неровные пластины по кругу, одна-две ушли под воду краем
		var a := TAU * float(k) / 6.0 + rng.randf_range(-0.3, 0.3)
		var r := rng.randf_range(0.04, 0.1)
		var b := _box(pl, Vector3(rng.randf_range(0.07, 0.11), 0.006, rng.randf_range(0.05, 0.08)),
			Vector3(cos(a) * r, 0.296 + rng.randf_range(-0.004, 0.003), sin(a) * r), im,
			Vector3(rng.randf_range(-14, 14), rad_to_deg(a) + rng.randf_range(-30, 30), rng.randf_range(-14, 14)))
		var tw := b.create_tween().set_loops()   # покачиваются на воде
		var y0 := b.position.y
		tw.tween_property(b, "position:y", y0 - 0.002, 0.9 + 0.1 * k).set_trans(Tween.TRANS_SINE)
		tw.tween_property(b, "position:y", y0 + 0.001, 0.9 + 0.1 * k).set_trans(Tween.TRANS_SINE)


## Корка льда с трещинами: полупрозрачный круг, от удара в центре — ломаные белые линии к краю.
static func _crack_tex() -> ImageTexture:
	var S := 160
	var img := Image.create(S, S, false, Image.FORMAT_RGBA8)
	var c := Vector2(S, S) * 0.5
	for y in S:
		for x in S:
			var d := Vector2(x, y).distance_to(c) / (S * 0.5)
			img.set_pixel(x, y, Color(0.85, 0.92, 0.97, 0.0 if d > 0.98 else 0.42))
	var rng := RandomNumberGenerator.new()
	rng.seed = 7
	for k in 9:   # лучи от места удара, с изломами и веточками
		var p := c + Vector2(rng.randf_range(-6, 6), rng.randf_range(-6, 6))
		var ang := TAU * float(k) / 9.0 + rng.randf_range(-0.2, 0.2)
		for st in 70:
			ang += rng.randf_range(-0.28, 0.28)
			p += Vector2(cos(ang), sin(ang)) * 1.1
			if p.distance_to(c) > S * 0.48:
				break
			for o: Vector2 in [Vector2.ZERO, Vector2(1, 0), Vector2(0, 1)]:
				var q: Vector2 = (p + o).floor()
				if q.x >= 0 and q.y >= 0 and q.x < S and q.y < S:
					img.set_pixelv(Vector2i(q), Color(1, 1, 1, 0.95))
	img.generate_mipmaps()
	return ImageTexture.create_from_image(img)


## Круги по воде в ведре: руки вошли в воду (pts — точки в мире). Тонкие светлые кольца расходятся и гаснут.
static func ripples(root: Node3D, pts: Array) -> void:
	var bk := root.get_node_or_null("bucket") as Node3D
	if bk == null:
		return
	for p in pts:
		var lp := bk.to_local(p as Vector3)
		for k in 2:
			var mi := MeshInstance3D.new()
			var tm := TorusMesh.new()
			tm.inner_radius = 0.017
			tm.outer_radius = 0.022
			tm.rings = 24
			tm.ring_segments = 4
			mi.mesh = tm
			var m := _mat(Color(0.82, 0.9, 0.96, 0.55), 0.1)
			m.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
			mi.material_override = m
			bk.add_child(mi)
			mi.position = Vector3(lp.x, 0.297, lp.z)
			mi.scale = Vector3(1.0, 0.25, 1.0)
			var tw := mi.create_tween().set_parallel()
			tw.tween_property(mi, "scale", Vector3(4.2, 0.25, 4.2), 1.2).set_delay(k * 0.3).set_ease(Tween.EASE_OUT)
			tw.tween_property(m, "albedo_color:a", 0.0, 1.2).set_delay(k * 0.3).set_ease(Tween.EASE_IN)
			tw.chain().tween_callback(mi.queue_free)


## Отмыл руки: вода розовеет, лёд разбит.
static func pink_water(root: Node3D) -> void:
	var bk := root.get_node_or_null("bucket")
	if bk == null:
		return
	var w := bk.get_node_or_null("water") as MeshInstance3D
	if w:
		var m := (w.material_override as StandardMaterial3D).duplicate() as StandardMaterial3D
		m.albedo_color = Color(0.55, 0.32, 0.38, 0.88)
		w.material_override = m
	for nm in ["ice", "cracks"]:   # лёд уже разбит (действие «лёд» или старое сохранение)
		var nd := bk.get_node_or_null(nm)
		if nd:
			(nd as Node3D).visible = false


## Багажник машины (прогулка главы 1): кузов в модели одной деталью, поэтому «открытый» багажник — накладка поверх
## крышки: тёмная ниша вдоль наклона крышки, в ней отцовские удочки, красный ящик с инструментами, свёрнутое одеяло,
## тусклая лампочка; крышка цвета кузова поднимается на петлях у заднего стекла. Замер по sedan.glb: крышка — z 1,76…2,3
## (оси машины), высота 1,04 у стекла → 0,93 у края. open=false — опустить и убрать.
static func trunk(car: Node3D, open: bool) -> void:
	var tr: Node3D = car.get_node_or_null("trunk_open")
	if not open:
		if tr:
			var lid0: Node3D = tr.get_node("hinge")
			var tw0 := tr.create_tween()
			tw0.tween_property(lid0, "rotation_degrees:x", 14.0, 0.45).set_ease(Tween.EASE_IN)
			tw0.tween_callback(tr.queue_free)
		return
	if tr:
		return
	tr = Node3D.new()
	tr.name = "trunk_open"
	car.add_child(tr)
	var paint: Material = null
	for mi in car.find_children("body*", "MeshInstance3D", true, false):
		paint = (mi as MeshInstance3D).get_active_material(0)
		break
	if paint == null:
		paint = _mat(Color(0.06, 0.08, 0.13), 0.35, 0.4)
	var niche := Node3D.new()   # ниша — по наклону крышки
	tr.add_child(niche)
	niche.position = Vector3(0, 0.99, 2.03)
	niche.rotation_degrees = Vector3(14, 0, 0)
	_box(niche, Vector3(1.42, 0.012, 0.56), Vector3(0, 0.006, 0), _mat(Color(0.02, 0.02, 0.022), 0.95))
	for sx in [-1, 1]:   # борта ниши
		_box(niche, Vector3(0.03, 0.05, 0.56), Vector3(sx * 0.7, 0.025, 0), _mat(Color(0.05, 0.05, 0.05), 0.9))
	var rods := _mat(Color(0.12, 0.1, 0.07), 0.5)
	for k in 2:   # удочки — вдоль багажника, от борта к борту
		var r := _cyl(niche, 0.008, 1.3, Vector3(0, 0.03 + k * 0.012, -0.18 + k * 0.05), rods)
		r.rotation_degrees = Vector3(0, 0, 90)
	_box(niche, Vector3(0.42, 0.13, 0.22), Vector3(-0.38, 0.065, 0.08), _mat(Color(0.5, 0.06, 0.04), 0.45, 0.3))   # ящик
	_box(niche, Vector3(0.3, 0.02, 0.05), Vector3(-0.38, 0.14, 0.08), _mat(Color(0.2, 0.2, 0.2), 0.4, 0.7))        # ручка ящика
	var blanket := _box(niche, Vector3(0.46, 0.09, 0.3), Vector3(0.3, 0.045, 0.06), _mat(Color(0.32, 0.14, 0.1), 0.95))
	blanket.name = "blanket"
	_box(blanket, Vector3(0.462, 0.012, 0.302), Vector3(0, 0.0, 0), _mat(Color(0.55, 0.42, 0.22), 0.95))          # полоса пледа
	var hinge := Node3D.new()   # крышка на петлях у заднего стекла
	hinge.name = "hinge"
	tr.add_child(hinge)
	hinge.position = Vector3(0, 1.05, 1.76)
	hinge.rotation_degrees = Vector3(14, 0, 0)
	_box(hinge, Vector3(1.46, 0.035, 0.57), Vector3(0, 0.0, 0.285), paint)
	_box(hinge, Vector3(1.4, 0.02, 0.53), Vector3(0, -0.026, 0.285), _mat(Color(0.08, 0.08, 0.085), 0.8))          # изнанка
	var bulb := OmniLight3D.new()
	bulb.position = Vector3(0, 1.12, 1.95)
	bulb.light_color = Color(1.0, 0.85, 0.6)
	bulb.light_energy = 0.5
	bulb.omni_range = 1.3
	tr.add_child(bulb)
	var tw := tr.create_tween()
	tw.tween_property(hinge, "rotation_degrees:x", -62.0, 0.6).set_ease(Tween.EASE_OUT).set_trans(Tween.TRANS_BACK)


## Одеяло взяли — из багажника пропадает.
static func trunk_take_blanket(car: Node3D) -> void:
	var tr: Node3D = car.get_node_or_null("trunk_open")
	if tr:
		var b := tr.find_child("blanket", true, false)
		if b:
			(b as Node3D).visible = false


## Таксофон: Ной снял трубку (она у него в правой руке, на рычаге пусто) или повесил обратно.
static func receiver(root: Node3D, a: Actor, in_hand: bool) -> void:
	var ph := root.find_child("payphone", true, false) as Node3D   # своя трубка: у прилавка в магазине тоже есть «receiver»
	if ph == null:
		return
	var r := ph.get_node_or_null("receiver") as Node3D
	if r:
		r.visible = not in_hand
	var pcs := ph.get_node_or_null("payphone_cord") as Node3D
	if pcs:
		pcs.visible = not in_hand
	var old := a.skel.get_node_or_null("receiver_hand")
	if old:
		old.queue_free()
	var live := root.get_node_or_null("payphone_cord_live") as MeshInstance3D
	if not in_hand:
		if live:
			live.queue_free()
		root.remove_meta("payphone_cord_by")
		return
	var ba := BoneAttachment3D.new()
	ba.name = "receiver_hand"
	ba.bone_name = "hand_r"
	a.skel.add_child(ba)
	var m := MeshInstance3D.new()
	var bm := BoxMesh.new()
	bm.size = Vector3(0.05, 0.21, 0.055)
	m.mesh = bm
	m.material_override = _mat(Color(0.05, 0.05, 0.055), 0.5, 0.3)
	m.position = Vector3(0.0, 0.08, 0.035)
	m.name = "rcv"
	ba.add_child(m)
	if live == null:   # стальной шнур от аппарата к трубке в руке — расправляется (каждый кадр: St24.tick → payphone_cord)
		live = MeshInstance3D.new()
		live.name = "payphone_cord_live"
		live.material_override = _mat(Color(0.5, 0.52, 0.55), 0.35, 0.85)
		live.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
		root.add_child(live)
	root.set_meta("payphone_cord_by", a)
	payphone_cord(root)


## Сугробы по краям площадки (tools/station.py — гладкие «капли» одного цвета: под холодными лампами навеса
## выглядели синим пластиком, кадры трассы 5 ноя). Наст: мелкая рябь — карта нормалей из шума по трём осям (без шва на
## шаре), неровная белизна пятнами, почти без блеска. Один материал на все сугробы.
static func snow_banks(root: Node3D) -> void:
	var m := StandardMaterial3D.new()
	m.albedo_color = Color(0.93, 0.94, 0.97)
	m.roughness = 0.94
	m.metallic_specular = 0.22
	var nz := FastNoiseLite.new()
	nz.frequency = 0.07
	nz.fractal_octaves = 4
	var nt := NoiseTexture2D.new()
	nt.noise = nz
	nt.width = 256
	nt.height = 256
	nt.seamless = true
	nt.as_normal_map = true
	nt.bump_strength = 5.0
	m.normal_enabled = true
	m.normal_texture = nt
	m.normal_scale = 0.9
	var an := FastNoiseLite.new()
	an.frequency = 0.025
	var at := NoiseTexture2D.new()
	at.noise = an
	at.width = 256
	at.height = 256
	at.seamless = true
	var gr := Gradient.new()
	gr.set_color(0, Color(0.74, 0.77, 0.82))
	gr.set_color(1, Color(1.0, 1.0, 1.0))
	at.color_ramp = gr
	m.albedo_texture = at
	m.uv1_triplanar = true
	m.uv1_scale = Vector3(0.7, 0.7, 0.7)
	for mi in root.find_children("snowbank*", "MeshInstance3D", true, false):
		(mi as MeshInstance3D).material_override = m


## Стальной шнур таксофона: от низа корпуса к нижнему концу трубки в руке Ноя, с лёгким провисом.
static func payphone_cord(root: Node3D) -> void:
	var live := root.get_node_or_null("payphone_cord_live") as MeshInstance3D
	var a = root.get_meta("payphone_cord_by", null)
	var ph := root.find_child("payphone", true, false) as Node3D
	if live == null or not (a is Actor) or not is_instance_valid(a) or ph == null:
		return
	var ba := (a as Actor).skel.get_node_or_null("receiver_hand") as Node3D
	var rc := ba.get_node_or_null("rcv") as Node3D if ba else null
	if rc == null:
		return
	var e0: Vector3 = rc.global_transform * Vector3(0, -0.1, 0)
	var e1: Vector3 = rc.global_transform * Vector3(0, 0.1, 0)
	var b: Vector3 = e0 if e0.y < e1.y else e1   # нижний конец трубки (микрофон)
	cord_mesh(live, ph.to_global(Vector3(-0.2, 1.25, 0.13)), b, 0.007, 0.04, 0.0)


## Бумажки на кнопках шевелятся на ветру: бахрома объявления про собаку, загнутый угол плаката (пока его не держат).
static func flutter(root: Node3D, t: float) -> void:
	for e in root.get_meta("flutter", []):
		var n := e[0] as Node3D
		if n == null or not is_instance_valid(n) or n.has_meta("held"):
			continue
		var ph := float(e[3])
		var w := 0.5 + 0.5 * (0.6 * sin(t * 2.3 + ph) + 0.4 * sin(t * 5.1 + ph * 1.7))   # 0…1, порывами
		n.rotation_degrees.x = float(e[1]) + float(e[2]) * w


## Следы шин на снегу площадки: их машина — от трассы к колонке (под колёса); при late — вторые, свежие, рядом с их
## следами от трассы и к краю площадки, к чёрной машине. Две колеи по 0,2 м, колея машины 1,5 м.
static func tire_tracks(root: Node3D, late: bool) -> void:
	if root.get_node_or_null("tracks_ours") == null:
		# по кривой подъезда (game._car_arrive), с того места, где колёса сошли с трассы, до задних колёс у колонки
		_tracks(root, "tracks_ours", [Vector3(2.66, 0, 22.84), Vector3(6.54, 0, 13.56), Vector3(14.3, 0, 8.5), Vector3(14.3, 0, -3.7)], 0.62)
	if late and root.get_node_or_null("tracks_black") == null:
		# от трассы рядом с их следами, вдоль края площадки — к дальнему краю, под передние колёса чёрной машины
		_tracks(root, "tracks_black", [Vector3(2.2, 0, 21.5), Vector3(5.4, 0, 13.0), Vector3(5.6, 0, 2.0), Vector3(5.2, 0, -8.0),
			Vector3(4.8, 0, -18.0), Vector3(7.0, 0, -21.8), Vector3(9.5, 0, -22.6)], 0.8)


static func _tracks(root: Node3D, nm: String, c: Array, alpha: float) -> void:
	var st := SurfaceTool.new()
	st.begin(Mesh.PRIMITIVE_TRIANGLES)
	var segs := (c.size() - 1) / 3   # кубические кривые Безье: точки 0-1-2-3, 3-4-5-6…
	var per := 60
	var pts: Array[Vector3] = []
	for sg in segs:
		var q0: Vector3 = c[sg * 3]
		var q1: Vector3 = c[sg * 3 + 1]
		var q2: Vector3 = c[sg * 3 + 2]
		var q3: Vector3 = c[sg * 3 + 3]
		for i in range(0 if sg == 0 else 1, per + 1):
			var u := float(i) / per
			var v := 1.0 - u
			pts.append(q0 * v * v * v + q1 * 3.0 * v * v * u + q2 * 3.0 * v * u * u + q3 * u * u * u)
	var N := pts.size() - 1
	for sv in [-0.75, 0.75]:
		var side := float(sv)
		for i in N:
			var p0: Vector3 = pts[i]
			var p1: Vector3 = pts[i + 1]
			var d := (p1 - p0).normalized()
			var nrm := Vector3(-d.z, 0, d.x)
			var w0 := 0.1 * (1.0 + 0.12 * sin(i * 1.7))
			var w1 := 0.1 * (1.0 + 0.12 * sin((i + 1) * 1.7))
			var a0 := p0 + nrm * (side - w0)
			var b0 := p0 + nrm * (side + w0)
			var a1 := p1 + nrm * (side - w1)
			var b1 := p1 + nrm * (side + w1)
			var y := Vector3(0, 0.012, 0)
			var fa := clampf(float(i) / 8.0, 0.0, 1.0) * clampf(float(N - i) / 4.0, 0.0, 1.0)   # у трассы колея сходит на нет
			var fb := clampf(float(i + 1) / 8.0, 0.0, 1.0) * clampf(float(N - i - 1) / 4.0, 0.0, 1.0)
			for q in [[a0, fa, 0.0], [a1, fb, 0.0], [b0, fa, 1.0], [b0, fa, 1.0], [a1, fb, 0.0], [b1, fb, 1.0]]:   # лицом вверх (по часовой)
				st.set_color(Color(1, 1, 1, float(q[1])))
				st.set_uv(Vector2(float(q[2]), float(i) * 0.25))
				st.set_normal(Vector3.UP)
				st.add_vertex((q[0] as Vector3) + y)
	var mi := MeshInstance3D.new()
	mi.name = nm
	mi.mesh = st.commit()
	var m := StandardMaterial3D.new()   # колея в свежем снегу ночью: продавленная, в тени — тёмная, без блеска
	m.albedo_color = Color(0.13, 0.15, 0.19, alpha)
	m.vertex_color_use_as_albedo = true
	m.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
	m.roughness = 1.0
	m.render_priority = 1
	mi.material_override = m
	mi.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	root.add_child(mi)


## Неон «24» на столбе у трассы: цифра «4» гаснет на полсекунды и загорается с треском (лист «24», шаг 1). Цифры в модели —
## одна сетка на сторону, поэтому «4» вырезается из неё по середине (с трассы «4» справа); вырезается один раз.
static func neon_four(root: Node3D) -> Array:
	var out: Array = []
	for n in root.find_children("sign_24*", "MeshInstance3D", true, false):
		var mi := n as MeshInstance3D
		var four := mi.get_node_or_null("four") as MeshInstance3D
		if four == null:
			var face := 1.0 if mi.global_position.x < 5.6 else -1.0   # сторона к трассе (−X): «4» к +Z; обратная — к −Z
			var keep := ArrayMesh.new()
			var cut := ArrayMesh.new()
			for si in mi.mesh.get_surface_count():
				var arr := mi.mesh.surface_get_arrays(si)
				var vs: PackedVector3Array = arr[Mesh.ARRAY_VERTEX]
				var idx: PackedInt32Array = arr[Mesh.ARRAY_INDEX] if arr[Mesh.ARRAY_INDEX] != null else PackedInt32Array()
				if idx.is_empty():
					idx.resize(vs.size())
					for i in vs.size():
						idx[i] = i
				var ik := PackedInt32Array()
				var ic := PackedInt32Array()
				for t in range(0, idx.size() - 2, 3):
					var c := mi.global_transform * ((vs[idx[t]] + vs[idx[t + 1]] + vs[idx[t + 2]]) / 3.0)
					var dst := ic if (c.z - 6.0) * face > 0.0 else ik
					dst.append_array([idx[t], idx[t + 1], idx[t + 2]])
				var mat := mi.get_active_material(si)
				for pair in [[keep, ik], [cut, ic]]:
					if (pair[1] as PackedInt32Array).is_empty():
						continue
					var a2 := arr.duplicate()
					a2[Mesh.ARRAY_INDEX] = pair[1]
					(pair[0] as ArrayMesh).add_surface_from_arrays(Mesh.PRIMITIVE_TRIANGLES, a2)
					if mat:
						(pair[0] as ArrayMesh).surface_set_material((pair[0] as ArrayMesh).get_surface_count() - 1, mat)
			if cut.get_surface_count() == 0:
				continue
			for si in mi.mesh.get_surface_count():
				mi.set_surface_override_material(si, null)
			mi.mesh = keep
			four = MeshInstance3D.new()
			four.name = "four"
			four.mesh = cut
			mi.add_child(four)
		out.append(four)
	for f in out:   # мигнуть: гаснет, дрожит, загорается
		var tw := (f as Node3D).create_tween()
		tw.tween_property(f, "visible", false, 0.0)
		tw.tween_interval(0.35)
		tw.tween_property(f, "visible", true, 0.0)
		tw.tween_interval(0.06)
		tw.tween_property(f, "visible", false, 0.0)
		tw.tween_interval(0.1)
		tw.tween_property(f, "visible", true, 0.0)
	return out


# ------------------------------------------------------------------ магазин: прилавок Романа (лист «24», эпизод 5)
const FLOOR_S := 0.38                               # пол магазина
const STOOL := Vector3(26.45, FLOOR_S, -9.5)        # высокий табурет за прилавком
const PHONE_AT := Vector3(26.1, 1.32, -8.93)        # телефон на полке у задней стенки прилавка — под правой рукой Романа (на высоте колен — не дотягивался)
const SOCKET := Vector3(25.7, 0.62, -8.83)          # розетка на задней стенке прилавка (сторона Романа) — шнур в стену
const CIG_RACK := Vector3(27.45, FLOOR_S, -10.35)   # стойка с сигаретами за спиной Романа

## Прилавок изнутри: высокий табурет Романа, полка под столешницей с телефоном (трубка лежит на рычагах, витой шнур
## к трубке, шнур в розетку), стойка с сигаретами за спиной у Романа. Всё один раз (повторный вызов — те же узлы).
static func counter_props(root: Node3D) -> Dictionary:
	var out := {}
	var cp := root.get_node_or_null("counter_props") as Node3D
	if cp:   # ссылки, а не поиск по имени: трубка бывает в руке у Романа, аппарат — на полу (уже не внутри counter_props)
		var refs: Dictionary = cp.get_meta("refs", {})
		for k in ["stool", "phone", "receiver", "cigs"]:
			var n = refs.get(k)
			out[k] = n if (n != null and is_instance_valid(n)) else cp.find_child(k, true, false)
		return out
	cp = Node3D.new()
	cp.name = "counter_props"
	root.add_child(cp)
	var metal := _mat(Color(0.32, 0.33, 0.35), 0.4, 0.7)
	var vinyl := _mat(Color(0.16, 0.03, 0.03), 0.55)
	var st := Node3D.new()   # табурет: сиденье в 0,76 м над полом, три ноги, кольцо для ног
	st.name = "stool"
	cp.add_child(st)
	st.position = STOOL
	_cyl(st, 0.19, 0.06, Vector3(0, 0.76, 0), vinyl)
	for k in 3:
		var a := TAU * k / 3.0
		var leg := _cyl(st, 0.014, 0.78, Vector3(cos(a) * 0.12, 0.38, sin(a) * 0.12), metal)
		leg.rotation = Vector3(sin(a) * 0.12, 0, -cos(a) * 0.12)
	var ring := MeshInstance3D.new()
	var tm := TorusMesh.new()
	tm.inner_radius = 0.15
	tm.outer_radius = 0.17
	ring.mesh = tm
	ring.material_override = metal
	ring.position = Vector3(0, 0.3, 0)
	st.add_child(ring)
	out["stool"] = st
	# полка под столешницей (со стороны Романа) и телефон на ней
	var wood := _mat(Color(0.22, 0.17, 0.12), 0.8)
	_box(cp, Vector3(0.7, 0.025, 0.3), Vector3(PHONE_AT.x, PHONE_AT.y - 0.06, -8.93), wood)
	var ph := Node3D.new()
	ph.name = "phone"
	cp.add_child(ph)
	ph.position = PHONE_AT
	ph.rotation_degrees.y = -12.0   # кнопками к Роману
	var plast := _mat(Color(0.52, 0.48, 0.4), 0.45)   # бежевый пластик 90-х
	_box(ph, Vector3(0.2, 0.07, 0.17), Vector3(0, -0.012, 0), plast)
	_box(ph, Vector3(0.09, 0.012, 0.07), Vector3(0.02, 0.028, -0.02), _mat(Color(0.12, 0.12, 0.12), 0.5))   # кнопки
	var rc := Node3D.new()
	rc.name = "receiver"
	ph.add_child(rc)
	rc.position = Vector3(-0.035, 0.045, 0.0)
	_box(rc, Vector3(0.05, 0.035, 0.21), Vector3(0, 0, 0), plast)
	for zz in [-0.09, 0.09]:
		_box(rc, Vector3(0.06, 0.045, 0.055), Vector3(0, -0.012, zz), plast)
	out["phone"] = ph
	out["receiver"] = rc
	for nm in ["cord_rx", "cord_rx2", "cord_wall"]:   # витой шнур трубки (две половины, если зажат в кулаке) и шнур в розетку
		var cm := MeshInstance3D.new()
		cm.name = nm
		cm.material_override = _mat(Color(0.45, 0.41, 0.34) if nm != "cord_wall" else Color(0.1, 0.1, 0.1), 0.6)
		cp.add_child(cm)
	_box(cp, Vector3(0.07, 0.11, 0.015), SOCKET + Vector3(0, 0, 0.008), _mat(Color(0.85, 0.83, 0.78), 0.5))   # розетка: на грани прилавка
	cord_update(root)
	# стойка с сигаретами: открытый шкафчик, пачки рядами (часть упадёт, когда Роман отшатнётся)
	var rk := Node3D.new()
	rk.name = "cigs"
	cp.add_child(rk)
	rk.position = CIG_RACK
	var dark := _mat(Color(0.12, 0.1, 0.08), 0.75)
	cp.set_meta("refs", {"stool": st, "phone": ph, "receiver": rc, "cigs": rk})
	_box(rk, Vector3(0.6, 1.3, 0.04), Vector3(0, 0.95, -0.17), dark)
	for sx in [-1, 1]:
		_box(rk, Vector3(0.03, 1.3, 0.36), Vector3(sx * 0.3, 0.95, 0), dark)
	for row in 4:
		_box(rk, Vector3(0.6, 0.02, 0.36), Vector3(0, 0.55 + row * 0.3, 0), dark)
		for i in 6:   # пачки: красные, белые, синие — с белой полосой
			var col: Color = [Color(0.72, 0.08, 0.06), Color(0.9, 0.88, 0.84), Color(0.1, 0.2, 0.5)][(i + row) % 3]
			var pk := _box(rk, Vector3(0.055, 0.09, 0.025), Vector3(-0.22 + i * 0.088, 0.61 + row * 0.3, 0.12), _mat(col, 0.5))
			pk.name = "pack_%d_%d" % [row, i]
			_box(pk, Vector3(0.056, 0.02, 0.026), Vector3(0, 0.02, 0), _mat(Color(0.95, 0.93, 0.9), 0.5))
	return out


## Шнуры телефона по положению трубки и аппарата: витой — от аппарата к трубке (где бы она ни была: на рычагах, у уха
## Романа, в кулаке Ноя), прямой — от аппарата в розетку (вырван — свисает с аппарата).
static func cord_update(root: Node3D, ripped := false, via = null) -> void:
	var cp := root.get_node_or_null("counter_props") as Node3D
	if cp == null:
		return
	var refs := counter_props(root)
	var ph := refs.get("phone") as Node3D
	var rc := refs.get("receiver") as Node3D
	var crx := cp.get_node("cord_rx") as MeshInstance3D
	var crx2 := cp.get_node_or_null("cord_rx2") as MeshInstance3D
	var cw := cp.get_node("cord_wall") as MeshInstance3D
	if ph == null or rc == null:
		return
	var a := ph.global_transform * Vector3(-0.09, -0.01, 0.05)
	var b := rc.global_transform * Vector3(0, -0.02, 0.1)
	if via is Vector3 and crx2:   # шнур зажат в кулаке: от аппарата к кулаку и от кулака к трубке, почти без провиса
		cord_mesh(crx, a, via, 0.006, 0.02, 0.012)
		cord_mesh(crx2, via, b, 0.006, 0.03, 0.012)
		crx2.visible = true
	else:
		cord_mesh(crx, a, b, 0.006, 0.12, 0.012)
		if crx2:
			crx2.visible = false
	var c := ph.global_transform * Vector3(0.09, -0.03, -0.06)
	var w := Vector3(c.x + 0.3, maxf(c.y - 0.45, FLOOR_S + 0.01), c.z + 0.12) if ripped else SOCKET   # вырван — конец на полу
	cord_mesh(cw, c, w, 0.004, 0.05, 0.0)


## Шнур: провисающая кривая от a до b, радиус r; coil — витки (размах), 0 — гладкий.
static func cord_mesh(mi: MeshInstance3D, a: Vector3, b: Vector3, r: float, sag: float, coil: float) -> void:
	var st := SurfaceTool.new()
	st.begin(Mesh.PRIMITIVE_TRIANGLES)
	var mid := (a + b) * 0.5 + Vector3(0, -sag - a.distance_to(b) * 0.25, 0)
	var N := 40 if coil > 0.0 else 14
	var pts: Array[Vector3] = []
	for i in N + 1:
		var t := float(i) / N
		var u := 1.0 - t
		pts.append(a * u * u + mid * 2.0 * u * t + b * t * t)
	var turns := a.distance_to(b) / 0.012 * 0.5 if coil > 0.0 else 0.0
	for i in N:
		var p0: Vector3 = pts[i]
		var p1: Vector3 = pts[i + 1]
		var dir := (p1 - p0).normalized()
		var side := dir.cross(Vector3.UP).normalized() if absf(dir.dot(Vector3.UP)) < 0.95 else dir.cross(Vector3.RIGHT).normalized()
		var up := side.cross(dir).normalized()
		if coil > 0.0:   # витки: смещение по кругу вокруг оси шнура
			var ph0 := TAU * turns * float(i) / N
			var ph1 := TAU * turns * float(i + 1) / N
			p0 += (side * cos(ph0) + up * sin(ph0)) * coil
			p1 += (side * cos(ph1) + up * sin(ph1)) * coil
		for k in 5:
			var a0 := TAU * k / 5.0
			var a1 := TAU * (k + 1) / 5.0
			var o0 := (side * cos(a0) + up * sin(a0)) * r
			var o1 := (side * cos(a1) + up * sin(a1)) * r
			st.set_normal(o0.normalized()); st.add_vertex(p0 + o0)
			st.set_normal(o1.normalized()); st.add_vertex(p0 + o1)
			st.set_normal(o1.normalized()); st.add_vertex(p1 + o1)
			st.set_normal(o0.normalized()); st.add_vertex(p0 + o0)
			st.set_normal(o1.normalized()); st.add_vertex(p1 + o1)
			st.set_normal(o0.normalized()); st.add_vertex(p1 + o0)
	mi.mesh = st.commit()


## Лампа дневного света над рядом мигает (лист: «над одним рядом мигает лампа»): трубка гаснет и вспыхивает неровно.
static func tube_flicker(root: Node3D) -> void:
	for n in root.find_children("tube_lamp_004", "MeshInstance3D", true, false):
		var mi := n as MeshInstance3D
		if mi.has_meta("flicker"):
			return
		mi.set_meta("flicker", true)
		var tw := mi.create_tween().set_loops()
		for sd in [[true, 1.8], [false, 0.07], [true, 0.05], [false, 0.09], [true, 2.6], [false, 0.06], [true, 0.35], [false, 0.05], [true, 1.2]]:
			var on: bool = sd[0]
			tw.tween_callback(func(): mi.visible = on)
			tw.tween_interval(float(sd[1]))


## Колокольчик над дверью магазина (кронштейн у притолоки): swing — качается и затихает (после провала — беззвучно).
static func door_bell(root: Node3D, swing := false) -> Node3D:
	var b := root.get_node_or_null("door_bell") as Node3D
	if b == null:
		b = Node3D.new()
		b.name = "door_bell"
		root.add_child(b)
		b.position = Vector3(21.0, FLOOR_S + 2.12, -2.25)   # изнутри, над створкой
		var brass := _mat(Color(0.62, 0.48, 0.2), 0.3, 0.9)
		_box(b, Vector3(0.012, 0.012, 0.14), Vector3(0, 0, -0.07), brass)   # кронштейн
		var cup := MeshInstance3D.new()
		var cm := CylinderMesh.new()
		cm.top_radius = 0.012
		cm.bottom_radius = 0.035
		cm.height = 0.05
		cup.mesh = cm
		cup.material_override = brass
		cup.position = Vector3(0, -0.04, 0)
		cup.name = "cup"
		b.add_child(cup)
	if swing:
		var tw := b.create_tween()
		for k in 6:
			var amp := 28.0 * pow(0.7, k)
			tw.tween_property(b, "rotation_degrees:z", amp, 0.32).set_trans(Tween.TRANS_SINE)
			tw.tween_property(b, "rotation_degrees:z", -amp * 0.8, 0.32).set_trans(Tween.TRANS_SINE)
		tw.tween_property(b, "rotation_degrees:z", 0.0, 0.3)
	return b


## После провала: пачки сигарет рассыпаны по полу вокруг Романа (стойка у него за спиной опрокинута в драке).
static func scatter_packs(root: Node3D, around: Vector3) -> void:
	counter_props(root)
	var rk := root.find_child("cigs", true, false) as Node3D
	if rk == null:
		return
	rk.rotation_degrees = Vector3(0, 0, 0)
	var rng := RandomNumberGenerator.new()
	rng.seed = 24
	var i := 0
	for pk in rk.find_children("pack_*", "MeshInstance3D", false, false):
		if rng.randf() > 0.6:
			continue
		var n := pk as Node3D
		var a := rng.randf() * TAU
		var r := rng.randf_range(0.25, 1.1)
		n.global_position = around + Vector3(cos(a) * r, 0.0, sin(a) * r * 0.7)
		n.global_position.y = FLOOR_S + 0.015
		n.rotation_degrees = Vector3(90 if rng.randf() > 0.5 else 0, rng.randf_range(0, 360), 0)
		i += 1

