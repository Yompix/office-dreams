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
	var inner := _mat(Color(0.9, 0.93, 0.85), 0.8, 0.0, Color(0.95, 0.97, 0.88), 0.55)
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
	vl.light_color = Color(0.95, 0.97, 0.88)
	vl.light_energy = 0.35
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
	for k in 4:
		for j in 3:
			_box(ph, Vector3(0.045, 0.035, 0.012), Vector3(-0.06 + j * 0.06, 1.5 - k * 0.055, 0.145), dark)
	var rcv := _box(ph, Vector3(0.07, 0.24, 0.07), Vector3(-0.2, 1.42, 0.12), dark)   # трубка на рычаге
	rcv.name = "receiver"
	_cyl(ph, 0.008, 0.45, Vector3(-0.2, 1.12, 0.13), dark)                 # витой шнур
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
	# выцветший плакат санатория: голубое небо, ели, белый корпус
	var pst := Node3D.new()
	bd.add_child(pst)
	pst.position = Vector3(0.22, 1.5, 0.048)
	pst.rotation_degrees = Vector3(0, 0, 1.5)
	_box(pst, Vector3(0.6, 0.7, 0.004), Vector3.ZERO, _mat(Color(0.7, 0.78, 0.8), 0.95))
	_box(pst, Vector3(0.52, 0.3, 0.003), Vector3(0, 0.06, 0.003), _mat(Color(0.62, 0.75, 0.82), 0.95))
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
	_cyl(bk, 0.17, 0.32, Vector3(0, 0.16, 0), _mat(Color(0.12, 0.3, 0.55), 0.5), 0.14)
	var water := _mat(Color(0.25, 0.45, 0.6, 0.85), 0.08)
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
	return out


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
	var r := root.find_child("receiver", true, false) as Node3D
	if r:
		r.visible = not in_hand
	var old := a.skel.get_node_or_null("receiver_hand")
	if old:
		old.queue_free()
	if not in_hand:
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
	ba.add_child(m)
