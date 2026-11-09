class_name Sana
extends Node3D
## Санаторий «Тихие сосны» (глава 4, необязательный заезд по мастер-промпту редактора): закрыт на зиму. Ворота на цепи —
## одна створка провисла внутрь двора, можно протиснуться; двор с фонтаном, доской объявлений, скамейками и фонарями;
## белый корпус в два этажа, окна заколочены; справа — одноэтажная пристройка архива: окно во двор (одна доска на
## гвозде), внутри стеллажи с папками, картотека (один ящик выдвинут), стол с папкой. Всё кодом (коробки — MultiMesh,
## материалы Poly Haven), без новых моделей и платных ассетов.
## Местные координаты: ворота — z 10 (дорога приходит с юга, +z), фасад корпуса — z −6, архив — x 12…20, z −6…1,5,
## окно архива — в западной стене (x 12), z −1,5…−0,5, по высоте 1,05…2,0; пол архива — 0,15.

const AX0 := 12.0
const AX1 := 20.0
const AZ0 := -6.0
const AZ1 := 1.5
const AFLOOR := 0.15
const WIN_Z0 := -1.5
const WIN_Z1 := -0.5
const WIN_Y0 := 1.05
const WIN_Y1 := 2.0
var pc := true
var plank: MeshInstance3D    # доска на окне архива: висит на одном гвозде (отодрать — fx sana_in)
var _mm := {}                # материал → [Transform3D] единичных кубов (сливаются в MultiMesh)


func build(road, is_pc: bool, lang: String) -> void:
	pc = is_pc
	var ru := lang == "ru"
	var snow: Material = Lake.snow_mat(road)
	var ground := _box(Vector3(160.0, 1.0, 160.0), Vector3(0.0, -0.5, 0.0), snow)
	ground.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	var plaster := _tex("painted_plaster_wall", Color(0.9, 0.9, 0.86), 0.3)
	var trim := _tex("painted_plaster_wall", Color(0.74, 0.74, 0.7), 0.3)
	var board := _tex("weathered_plank_siding", Color(0.62, 0.56, 0.5), 0.9)
	var dark := _mat(Color(0.04, 0.04, 0.05), 0.95)
	var iron := _mat(Color(0.11, 0.11, 0.12), 0.5, 0.6)
	var roof := _mat(Color(0.3, 0.32, 0.34), 0.55, 0.3)
	var white := _mat(Color(0.9, 0.92, 0.96), 0.9)
	_gate(ru, trim, iron, white)
	_fence(iron)
	_yard(ru, iron, board, plaster, white)
	_main(ru, plaster, trim, board, dark, roof, white)
	_archive(ru, plaster, board, dark, roof, white)
	_flush()
	_trees(road)


# ---------------------------------------------------------------------------------------------------- снаружи

func _gate(ru: bool, brick: Material, iron: Material, white: Material) -> void:
	for sx in [-1.0, 1.0]:   # кирпичные столбы, снег шапкой
		_cube(Vector3(0.8, 2.6, 0.8), Vector3(sx * 2.9, 1.3, 10.0), brick)
		_cube(Vector3(0.92, 0.14, 0.92), Vector3(sx * 2.9, 2.67, 10.0), white)
		_cube(Vector3(0.08, 0.32, 0.08), Vector3(sx * 2.9, 2.9, 10.0), iron)
	_cube(Vector3(6.4, 0.56, 0.07), Vector3(0.0, 3.3, 10.0), iron)   # вывеска над воротами
	_label("САНАТОРИЙ «ТИХИЕ СОСНЫ»" if ru else "QUIET PINES SANATORIUM", Vector3(0.0, 3.3, 10.0365), 0.0046, Color(0.88, 0.82, 0.58), 0.0)
	for side in [-1.0, 1.0]:   # створки: прутья через 0,155 м и две перекладины; левая закрыта, правая провисла внутрь двора
		var hinge := Vector3(side * 2.5, 0.0, 10.0)
		var rot := Basis() if side < 0.0 else Basis(Vector3.UP, deg_to_rad(-30.0))
		var dir: float = -side   # левая тянется к +x, правая — к −x
		for k in 16:
			var u := 0.08 + k * 0.155
			_cube(Vector3(0.028, 1.95, 0.028), hinge + rot * Vector3(dir * u, 1.0, 0.0), iron, rot)
		for y in [0.28, 1.82]:
			_cube(Vector3(2.45, 0.05, 0.04), hinge + rot * Vector3(dir * 1.225, y, 0.0), iron, rot)
	for k in 7:   # цепь с замком висит на левой створке
		_cube(Vector3(0.05, 0.08, 0.03), Vector3(-0.12, 1.25 - k * 0.07, 10.05), iron, Basis(Vector3.FORWARD, 0.6 if k % 2 == 0 else -0.6))
	_cube(Vector3(0.09, 0.11, 0.05), Vector3(-0.12, 0.74, 10.06), iron)


func _fence(iron: Material) -> void:
	var lines := [[Vector2(-26.0, 10.0), Vector2(-3.4, 10.0)], [Vector2(3.4, 10.0), Vector2(26.0, 10.0)],
		[Vector2(-26.0, -24.0), Vector2(-26.0, 10.0)], [Vector2(26.0, -24.0), Vector2(26.0, 10.0)],
		[Vector2(-26.0, -24.0), Vector2(26.0, -24.0)]]
	for ln in lines:
		var a: Vector2 = ln[0]
		var b: Vector2 = ln[1]
		var n := int(ceil(a.distance_to(b) / 2.0))
		for i in n + 1:
			var p := a.lerp(b, float(i) / n)
			_cube(Vector3(0.07, 1.6, 0.07), Vector3(p.x, 0.8, p.y), iron)
		var m := (a + b) * 0.5
		var along := b - a
		var rot := Basis(Vector3.UP, atan2(along.x, along.y))
		for y in [0.35, 1.4]:
			_cube(Vector3(0.04, 0.04, along.length()), Vector3(m.x, y, m.y), iron, rot)


func _yard(ru: bool, iron: Material, board: Material, plaster: Material, white: Material) -> void:
	# фонтан посреди двора: чаша, в ней снег вровень с краями
	_cyl(1.7, 1.75, 0.6, Vector3(0.0, 0.3, 2.5), plaster)
	_cyl(1.55, 1.55, 0.1, Vector3(0.0, 0.58, 2.5), white)
	_cyl(0.42, 0.16, 1.1, Vector3(0.0, 0.95, 2.5), plaster)
	_cube(Vector3(0.8, 0.12, 0.8), Vector3(0.0, 1.56, 2.5), white)
	_label("Я НЕ СУМАСШЕДШАЯ" if ru else "I AM NOT CRAZY", Vector3(0.35, 0.42, 4.235), 0.0013, Color(0.3, 0.3, 0.33), 10.0, false, "hand")
	# доска объявлений: два столба, щит, лист под стеклом
	_cube(Vector3(0.08, 1.9, 0.08), Vector3(-4.65, 0.95, 4.0), iron)
	_cube(Vector3(0.08, 1.9, 0.08), Vector3(-3.35, 0.95, 4.0), iron)
	_cube(Vector3(1.45, 0.95, 0.06), Vector3(-4.0, 1.5, 4.0), board)
	_cube(Vector3(1.5, 0.08, 0.2), Vector3(-4.0, 2.02, 4.02), white)
	var paper := _mat(Color(0.86, 0.84, 0.76), 0.9)
	_cube(Vector3(1.25, 0.78, 0.01), Vector3(-4.0, 1.5, 4.035), paper)
	_label("САНАТОРИЙ ЗАКРЫТ\nНА ЗИМНИЙ ПЕРИОД\nАдминистрация" if ru else "THE SANATORIUM IS CLOSED\nFOR THE WINTER\nThe Management",
		Vector3(-4.0, 1.7, 4.0415), 0.0014, Color(0.12, 0.12, 0.14), 0.0, false, "notice_b")
	_label("Главный врач — д-р С. Х" if ru else "Head physician — Dr. S. H", Vector3(-4.17, 1.33, 4.0415), 0.0011, Color(0.16, 0.16, 0.2), 0.0, false, "notice")
	var torn := _mat(Color(0.78, 0.76, 0.68), 0.9)   # оторванный край строки
	_cube(Vector3(0.34, 0.06, 0.012), Vector3(-3.52, 1.33, 4.04), torn, Basis(Vector3.BACK, 0.25))
	# скамейки по краям, занесённые снегом; фонари у дорожки
	for sx in [-1.0, 1.0]:
		var bx: float = sx * 6.5
		_cube(Vector3(0.45, 0.06, 1.6), Vector3(bx, 0.45, 1.0), board)
		_cube(Vector3(0.06, 0.45, 1.6), Vector3(bx - sx * 0.22, 0.72, 1.0), board)
		_cube(Vector3(0.47, 0.12, 1.55), Vector3(bx, 0.53, 1.0), white)
		for lz in [0.35, 1.65]:
			_cube(Vector3(0.4, 0.42, 0.05), Vector3(bx, 0.21, lz), iron)
		_cube(Vector3(0.1, 3.2, 0.1), Vector3(sx * 3.2, 1.6, 6.5), iron)
		_cube(Vector3(0.34, 0.26, 0.34), Vector3(sx * 3.2, 3.25, 6.5), iron)
		_cube(Vector3(0.38, 0.06, 0.38), Vector3(sx * 3.2, 3.41, 6.5), white)


func _main(ru: bool, plaster: Material, trim: Material, board: Material, dark: Material, roof: Material, white: Material) -> void:
	_box(Vector3(26.0, 7.4, 12.0), Vector3(0.0, 3.7, -12.0), plaster)
	_cube(Vector3(26.2, 0.18, 12.2), Vector3(0.0, 3.55, -12.0), trim)     # пояс между этажами
	_cube(Vector3(26.2, 0.4, 12.2), Vector3(0.0, 0.2, -12.0), trim)       # цоколь
	_cube(Vector3(26.8, 0.35, 12.8), Vector3(0.0, 7.575, -12.0), roof)
	_cube(Vector3(26.6, 0.25, 12.6), Vector3(0.0, 7.87, -12.0), white)
	for fl in [1.9, 5.3]:   # окна: тёмное стекло, подоконник, три доски наискосок
		for k in 9:
			var wx := -11.0 + k * 2.75
			if fl < 3.0 and absf(wx) < 1.0:
				continue   # дверь
			_cube(Vector3(1.3, 1.7, 0.04), Vector3(wx, fl, -5.98), dark)
			_cube(Vector3(1.5, 0.1, 0.18), Vector3(wx, fl - 0.9, -5.92), trim)
			var ward: bool = fl < 3.0 and absf(wx + 8.25) < 0.1   # одно окно: доски сорваны, внутри коридор
			for b in 3:
				if ward and b < 2:
					continue
				_cube(Vector3(1.55, 0.2, 0.04), Vector3(wx, fl - 0.55 + b * 0.55, -5.93), board, Basis(Vector3.BACK, deg_to_rad((b - 1) * 4.0)))
			if ward:   # сорванные доски — в снегу под окном
				_cube(Vector3(1.55, 0.04, 0.2), Vector3(wx - 0.2, 0.03, -5.4), board, Basis(Vector3.UP, 0.3))
				_cube(Vector3(1.55, 0.04, 0.2), Vector3(wx + 0.1, 0.06, -5.2), board, Basis(Vector3.UP, -0.2))
	# дверь — заколочена крест-накрест; над ней — «Лечебный корпус»
	_cube(Vector3(1.5, 2.4, 0.04), Vector3(0.0, 1.5, -5.97), dark)
	for sgn in [-1.0, 1.0]:
		_cube(Vector3(2.2, 0.2, 0.05), Vector3(0.0, 1.5, -5.9), board, Basis(Vector3.BACK, sgn * deg_to_rad(42.0)))
	_label("ЛЕЧЕБНЫЙ КОРПУС" if ru else "TREATMENT BUILDING", Vector3(0.0, 3.05, -5.95), 0.0034, Color(0.2, 0.2, 0.22), 0.0, false)
	# крыльцо с колоннами
	_cube(Vector3(5.2, 0.3, 2.6), Vector3(0.0, 0.15, -4.7), trim)
	_cube(Vector3(5.2, 0.15, 0.4), Vector3(0.0, 0.075, -3.2), trim)
	for x in [-2.1, -0.75, 0.75, 2.1]:
		_cyl(0.15, 0.17, 3.2, Vector3(x, 1.9, -3.65), plaster)
	_cube(Vector3(5.6, 0.25, 2.9), Vector3(0.0, 3.6, -4.65), trim)
	_cube(Vector3(5.5, 0.2, 2.8), Vector3(0.0, 3.82, -4.65), white)


func _archive(ru: bool, plaster: Material, board: Material, dark: Material, roof: Material, white: Material) -> void:
	var cx := (AX0 + AX1) * 0.5
	var cz := (AZ0 + AZ1) * 0.5
	var sx := AX1 - AX0
	var sz := AZ1 - AZ0
	var floor_m := _tex("old_wood_floor", Color(0.7, 0.66, 0.6), 0.6)
	_cube(Vector3(sx, AFLOOR, sz), Vector3(cx, AFLOOR * 0.5, cz), floor_m)
	_cube(Vector3(sx + 0.4, 0.25, sz + 0.4), Vector3(cx, 3.575, cz), roof)
	_cube(Vector3(sx + 0.2, 0.2, sz + 0.2), Vector3(cx, 3.8, cz), white)
	var wh := 3.3
	var wy := AFLOOR + wh * 0.5
	_box(Vector3(0.2, wh, sz), Vector3(AX1 - 0.1, wy, cz), plaster)                 # восточная стена
	_box(Vector3(sx, wh, 0.2), Vector3(cx, wy, AZ0 + 0.1), plaster)                 # северная
	_box(Vector3(sx, wh, 0.2), Vector3(cx, wy, AZ1 - 0.1), plaster)                 # южная
	_box(Vector3(0.2, wh, WIN_Z0 - AZ0), Vector3(AX0 + 0.1, wy, (AZ0 + WIN_Z0) * 0.5), plaster)   # западная — с окном
	_box(Vector3(0.2, wh, AZ1 - WIN_Z1), Vector3(AX0 + 0.1, wy, (AZ1 + WIN_Z1) * 0.5), plaster)
	_box(Vector3(0.2, WIN_Y0 - AFLOOR, WIN_Z1 - WIN_Z0), Vector3(AX0 + 0.1, (AFLOOR + WIN_Y0) * 0.5, (WIN_Z0 + WIN_Z1) * 0.5), plaster)
	_box(Vector3(0.2, AFLOOR + wh - WIN_Y1, WIN_Z1 - WIN_Z0), Vector3(AX0 + 0.1, (WIN_Y1 + AFLOOR + wh) * 0.5, (WIN_Z0 + WIN_Z1) * 0.5), plaster)
	_cube(Vector3(0.32, 0.06, 1.15), Vector3(AX0 + 0.05, WIN_Y0 + 0.03, -1.0), white)   # снег на подоконнике
	_label("АРХИВ" if ru else "ARCHIVE", Vector3(AX0 - 0.015, 2.4, -2.3), 0.0034, Color(0.18, 0.18, 0.2), -90.0, false, "label")
	plank = MeshInstance3D.new()   # доска на одном гвозде — наискосок через окно
	var pb := BoxMesh.new()
	pb.size = Vector3(0.04, 0.2, 1.35)
	plank.mesh = pb
	plank.material_override = board
	plank.position = Vector3(AX0 - 0.04, 1.55, -1.0)
	plank.rotation_degrees = Vector3(28.0, 0.0, 0.0)
	add_child(plank)
	# внутри: стеллажи вдоль северной и южной стен, папки разных цветов
	var steel := _mat(Color(0.36, 0.38, 0.38), 0.5, 0.5)
	var folders: Array = []
	var rng := RandomNumberGenerator.new()
	rng.seed = 11
	var tones := [Color(0.55, 0.45, 0.3), Color(0.62, 0.56, 0.42), Color(0.3, 0.36, 0.46), Color(0.48, 0.32, 0.26), Color(0.7, 0.68, 0.6)]
	for zw in [AZ0 + 0.55, AZ1 - 0.55]:
		var face: float = 1.0 if zw < cz else -1.0   # куда смотрят корешки
		for k in 3:
			var ux := 14.6 + k * 1.9
			for e in [-0.83, 0.83]:
				_cube(Vector3(0.04, 2.25, 0.45), Vector3(ux + e, AFLOOR + 1.125, zw), steel)
			for lv in 5:
				_cube(Vector3(1.7, 0.03, 0.45), Vector3(ux, AFLOOR + 0.06 + lv * 0.5, zw), steel)
			for lv in 4:
				var x := ux - 0.78
				while x < ux + 0.74:
					var w := rng.randf_range(0.05, 0.1)
					var h := rng.randf_range(0.28, 0.34)
					var tone: Color = tones[rng.randi() % tones.size()]
					var t := Transform3D(Basis(Vector3.BACK, rng.randf_range(-0.05, 0.05)) * Basis.from_scale(Vector3(w, h, 0.36)),
						Vector3(x + w * 0.5, AFLOOR + 0.075 + lv * 0.5 + h * 0.5, zw + face * 0.02))
					folders.append([t, tone * rng.randf_range(0.8, 1.1)])
					x += w + rng.randf_range(0.0, 0.015)
	_multi(folders)
	# картотека у восточной стены: ящички, один выдвинут, сверху в нём — карточка
	var wood := _tex("wood_table_worn", Color(0.55, 0.42, 0.3), 0.8)
	_cube(Vector3(1.0, 1.3, 0.6), Vector3(19.3, AFLOOR + 0.65, -4.5), wood)
	var card := _mat(Color(0.92, 0.9, 0.82), 0.9)
	for r in 4:
		for c in 4:
			var open := r == 2 and c == 1
			var dz := 0.26 if open else 0.0
			var dx := 18.95 + c * 0.235
			_cube(Vector3(0.21, 0.25, 0.02), Vector3(dx, AFLOOR + 0.2 + r * 0.3, -4.19 + dz), steel)
			_cube(Vector3(0.06, 0.02, 0.025), Vector3(dx, AFLOOR + 0.27 + r * 0.3, -4.17 + dz), dark)
			if open:
				_cube(Vector3(0.2, 0.2, 0.26), Vector3(dx, AFLOOR + 0.2 + r * 0.3, -4.32 + dz * 0.5), wood)
				_cube(Vector3(0.15, 0.003, 0.2), Vector3(dx + 0.01, AFLOOR + 0.31 + r * 0.3, -4.1), card, Basis(Vector3.UP, 0.2))
	# стол, стул, лампа; на столе — одна папка и листы
	_cube(Vector3(1.4, 0.05, 0.75), Vector3(14.6, AFLOOR + 0.76, -3.4), wood)
	for lx in [-0.64, 0.64]:
		for lz in [-0.32, 0.32]:
			_cube(Vector3(0.05, 0.74, 0.05), Vector3(14.6 + lx, AFLOOR + 0.37, -3.4 + lz), wood)
	_cube(Vector3(0.45, 0.05, 0.45), Vector3(14.6, AFLOOR + 0.46, -2.72), wood)
	_cube(Vector3(0.45, 0.5, 0.04), Vector3(14.6, AFLOOR + 0.72, -2.5), wood)
	var folder := _mat(Color(0.66, 0.58, 0.42), 0.85)
	_cube(Vector3(0.25, 0.025, 0.33), Vector3(15.0, AFLOOR + 0.8, -3.42), folder, Basis(Vector3.UP, 0.12))
	var sheet := _mat(Color(0.93, 0.92, 0.86), 0.9)
	_cube(Vector3(0.21, 0.003, 0.3), Vector3(14.55, AFLOOR + 0.79, -3.3), sheet, Basis(Vector3.UP, -0.35))
	_cube(Vector3(0.21, 0.003, 0.3), Vector3(14.3, AFLOOR + 0.791, -3.5), sheet, Basis(Vector3.UP, 0.5))
	_cyl(0.05, 0.13, 0.14, Vector3(14.05, AFLOOR + 1.05, -3.62), steel)
	_cube(Vector3(0.02, 0.26, 0.02), Vector3(14.05, AFLOOR + 0.91, -3.62), steel)
	# свет: из окна — полосой по полу; полумрак — слабой заливкой
	var sun := SpotLight3D.new()
	sun.light_color = Color(0.86, 0.9, 1.0)
	sun.light_energy = 4.0
	sun.spot_range = 7.5
	sun.spot_angle = 34.0
	sun.shadow_enabled = pc
	sun.position = Vector3(AX0 + 0.3, 2.05, -1.0)   # прямо за окном, внутри: наружная стена не светится кругом
	sun.rotation_degrees = Vector3(-38.0, -90.0, 0.0)
	add_child(sun)
	var fill := OmniLight3D.new()
	fill.light_color = Color(0.72, 0.76, 0.86)
	fill.light_energy = 0.45
	fill.omni_range = 7.5
	fill.position = Vector3(16.2, 2.7, -2.2)
	add_child(fill)


func _trees(road) -> void:
	var rng := RandomNumberGenerator.new()
	rng.seed = 23
	var pts: Array = []
	for i in 420:
		var x := rng.randf_range(-78.0, 78.0)
		var z := rng.randf_range(-78.0, 78.0)
		if x > -29.0 and x < 29.0 and z > -27.0 and z < 13.0:
			continue   # двор, корпус и ограда
		if absf(x) < 8.0 and z > 9.0:
			continue   # подъезд с юга (и место для камеры у ворот)
		pts.append([x, z, rng.randf_range(0.9, 1.6)])
	Lake.firs(road, self, pts, 71, pc)


# ---------------------------------------------------------------------------------------------------- помощники

func _box(size: Vector3, pos: Vector3, m: Material) -> MeshInstance3D:
	var mi := MeshInstance3D.new()
	var bm := BoxMesh.new()
	bm.size = size
	mi.mesh = bm
	mi.material_override = m
	mi.position = pos
	add_child(mi)
	return mi


func _cyl(top: float, bottom: float, h: float, pos: Vector3, m: Material) -> MeshInstance3D:
	var mi := MeshInstance3D.new()
	var cm := CylinderMesh.new()
	cm.top_radius = top
	cm.bottom_radius = bottom
	cm.height = h
	mi.mesh = cm
	mi.material_override = m
	mi.position = pos
	add_child(mi)
	return mi


## Коробка в общий MultiMesh своего материала (один вызов отрисовки на материал).
func _cube(size: Vector3, pos: Vector3, m: Material, rot := Basis()) -> void:
	if not _mm.has(m):
		_mm[m] = []
	(_mm[m] as Array).append(Transform3D(rot * Basis.from_scale(size), pos))


func _flush() -> void:
	var unit := BoxMesh.new()
	for m in _mm:
		var list: Array = _mm[m]
		var mm := MultiMesh.new()
		mm.transform_format = MultiMesh.TRANSFORM_3D
		mm.mesh = unit
		mm.instance_count = list.size()
		for i in list.size():
			mm.set_instance_transform(i, list[i])
		var mi := MultiMeshInstance3D.new()
		mi.multimesh = mm
		mi.material_override = m
		mi.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_ON if pc else GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
		add_child(mi)
	_mm.clear()


## Папки на полках: один MultiMesh с цветом на каждую.
func _multi(items: Array) -> void:
	var mm := MultiMesh.new()
	mm.transform_format = MultiMesh.TRANSFORM_3D
	mm.use_colors = true
	mm.mesh = BoxMesh.new()
	mm.instance_count = items.size()
	for i in items.size():
		mm.set_instance_transform(i, items[i][0])
		mm.set_instance_color(i, items[i][1])
	var m := StandardMaterial3D.new()
	m.vertex_color_use_as_albedo = true
	m.roughness = 0.9
	var mi := MultiMeshInstance3D.new()
	mi.multimesh = mm
	mi.material_override = m
	mi.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	add_child(mi)


func _label(text: String, pos: Vector3, px: float, c: Color, yaw: float, outline := true, kind := "shop") -> Label3D:
	var lb := Label3D.new()
	lb.text = text
	lb.font_size = 64
	lb.pixel_size = px
	lb.modulate = c
	lb.outline_size = 6 if outline else 0
	lb.outline_modulate = Color(0.0, 0.0, 0.0, 0.5)
	lb.double_sided = false
	lb.shaded = true
	lb.position = pos
	lb.rotation_degrees = Vector3(0.0, yaw, 0.0)
	Signs.style(lb, kind)
	add_child(lb)
	Signs.flush.call_deferred(lb)
	return lb


static func _mat(c: Color, rough: float, metal := 0.0) -> StandardMaterial3D:
	var m := StandardMaterial3D.new()
	m.albedo_color = c
	m.roughness = rough
	m.metallic = metal
	return m


static func _tex(name: String, tint: Color, tile: float) -> StandardMaterial3D:
	var m := StandardMaterial3D.new()
	var base := "res://assets/ph/%s/%s_" % [name, name]
	if ResourceLoader.exists(base + "diff_1k.jpg"):
		m.albedo_texture = load(base + "diff_1k.jpg")
		if ResourceLoader.exists(base + "nor_gl_1k.jpg"):
			m.normal_enabled = true
			m.normal_texture = load(base + "nor_gl_1k.jpg")
	m.albedo_color = tint
	m.roughness = 0.9
	m.uv1_triplanar = true
	m.uv1_world_triplanar = true
	m.uv1_scale = Vector3(tile, tile, tile)
	return m
