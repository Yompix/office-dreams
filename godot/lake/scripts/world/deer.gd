class_name Deer
extends Node3D
## Олень (сцена 3): модель — Figma Weave (Hunyuan 3D) + кости tools/lake/deer.py. Движения кодом, поверх позы покоя:
## «stand» — стоит, дышит, хвост подрагивает; «walk» — шаг четырьмя ногами по очереди (задняя левая → передняя левая →
## задняя правая → передняя правая), копыта поднимаются, корпус покачивается, идёт вперёд (+Z) со скоростью speed;
## «lie» — сбит: лежит на боку, ноги неестественно, дышит часто и пытается поднять голову; «dead» — затих.
## look_target — точка (мир): голова и шея поворачиваются к ней. die(сек) — затихает постепенно (дыхание реже, слабее).
## Глаза светятся в фарах (eyeshine): только когда камера почти на линии света (shine_from — откуда светят фары).

var model: Node3D
var skel: Skeleton3D
var mode := "stand"
var look_target = null
var speed := 1.3
var t := 0.0
var fall := 0.0                 # 0 — стоит, 1 — лежит (переход — падение)
var _b := {}
var _rest := {}
var _rg := {}
var _look := Vector2.ZERO       # поворот головы (рыскание, кивок), сглаженный
var life := 1.0                 # лежит: 1 — бьётся и часто дышит, 0 — затих
var _die := 0.0                 # скорость угасания (1/с)
var _bt := 0.0                  # фаза дыхания (частота меняется плавно)
var shine_from = null           # точка (мир) — фары: глаза светятся, если камера рядом с линией света
var _eyes: Array[MeshInstance3D] = []
var _body0 := Vector3.ZERO      # середина спины в осях узла (модель сдвинута вбок) — по ней ставим оленя
const LEGS := {"bl": 0.0, "fl": 0.25, "br": 0.5, "fr": 0.75}   # фаза шага каждой ноги
const PERIOD := 1.15


func _ready() -> void:
	model = (load("res://assets/models/deer.glb") as PackedScene).instantiate() as Node3D
	add_child(model)
	skel = model.find_children("*", "Skeleton3D", true, false)[0] as Skeleton3D
	for i in skel.get_bone_count():
		_b[skel.get_bone_name(i)] = i
		_rest[i] = skel.get_bone_rest(i).basis.get_rotation_quaternion()
		_rg[i] = skel.get_bone_global_rest(i).basis.orthonormalized()
	for mi in model.find_children("*", "MeshInstance3D", true, false):
		(mi as MeshInstance3D).cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_ON
	if _b.has("spine"):
		_body0 = to_local(skel.global_transform * skel.get_bone_global_rest(_b["spine"]).origin)
		_body0.y = 0.0
	_make_eyes()


## Поставить оленя так, чтобы середина спины встала в точку p (мир, по земле), мордой по углу face (градусы, 0 — к +Z).
func place_body(p: Vector3, face: float) -> void:
	rotation = Vector3(0, deg_to_rad(face), 0)
	position = p - basis * _body0
	position.y = p.y


func body_center() -> Vector3:
	return global_transform * _body0


func head_pos() -> Vector3:
	if not _b.has("head"):
		return global_position + Vector3(0, 1.1, 0)
	return skel.global_transform * skel.get_bone_global_pose(_b["head"]).origin


## Затихнуть за t секунд (лежит — дыхание реже и слабее, потом «dead»).
func die(t: float) -> void:
	_die = 1.0 / maxf(t, 0.05)


## Глаза в фарах: два светящихся пятна у головы (кость head: от затылка к носу — ось Y).
func _make_eyes() -> void:
	if not _b.has("head"):
		return
	var g := Gradient.new()
	g.set_color(0, Color(1, 1, 1, 1))
	g.set_color(1, Color(1, 1, 1, 0))
	var gt := GradientTexture2D.new()
	gt.gradient = g
	gt.fill = GradientTexture2D.FILL_RADIAL
	gt.fill_from = Vector2(0.5, 0.5)
	gt.fill_to = Vector2(1.0, 0.5)
	gt.width = 64
	gt.height = 64
	var m := StandardMaterial3D.new()
	m.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
	m.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
	m.blend_mode = BaseMaterial3D.BLEND_MODE_ADD
	m.billboard_mode = BaseMaterial3D.BILLBOARD_ENABLED
	m.billboard_keep_scale = true
	m.albedo_texture = gt
	m.albedo_color = Color(0.8, 1.0, 0.75)
	m.disable_receive_shadows = true
	var hr := skel.get_bone_global_rest(_b["head"])
	var ba := BoneAttachment3D.new()
	ba.bone_name = "head"
	skel.add_child(ba)
	for ep in EYES:
		var e := MeshInstance3D.new()
		var q := QuadMesh.new()
		q.size = Vector2.ONE
		e.mesh = q
		e.material_override = m
		e.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
		e.position = hr.affine_inverse() * (ep as Vector3)
		e.set_meta("p0", e.position)
		e.visible = false
		ba.add_child(e)
		_eyes.append(e)


## морда в покое повёрнута влево (модель по картинке смотрела в камеру): от середины глаз к носу, в осях модели
const FACE_DIR := Vector3(0.750, -0.419, 0.511)
const FACE_YAW := 0.972          # её поворот от оси тела, рад (≈56°): взгляд считаем от него
## глаза в осях модели: первый — лучом из камеры через зрачок на снимке, второй — тёмное пятно на том же расстоянии от носа
## (голова модели повёрнута влево примерно на 45°, поэтому глаза не симметричны относительно оси тела)
const EYES := [Vector3(0.096, 1.202, 0.6715), Vector3(0.2058, 1.2031, 0.5444)]


func _update_eyes() -> void:
	var cam := get_viewport().get_camera_3d()
	var nose := Vector3.ZERO           # куда смотрит морда: глаз светится, только если обращён к фарам
	if _b.has("head"):
		var hr := skel.get_bone_global_rest(_b["head"]).basis
		nose = (skel.global_transform.basis * skel.get_bone_global_pose(_b["head"]).basis * (hr.inverse() * FACE_DIR)).normalized()
	for e in _eyes:
		var k := 0.0
		if shine_from != null and cam and mode != "dead":
			var p := e.global_position
			var to_cam := (cam.global_position - p).normalized()
			var to_light := ((shine_from as Vector3) - p).normalized()
			k = smoothstep(0.93, 0.995, to_cam.dot(to_light)) * smoothstep(0.05, 0.5, nose.dot(to_light))
		e.visible = k > 0.01
		if e.visible:   # блик чуть к камере, чтобы не тонул в глазу
			var par := e.get_parent() as Node3D
			e.position = (e.get_meta("p0") as Vector3) + par.global_transform.basis.inverse() * ((cam.global_position - par.global_transform * (e.get_meta("p0") as Vector3)).normalized() * 0.025)
			var d := cam.global_position.distance_to(e.global_position)
			e.scale = Vector3.ONE * maxf(0.035, d * 0.0045) * k
			(e.material_override as StandardMaterial3D).albedo_color = Color(0.8, 1.0, 0.75) * (0.6 + 0.4 * k)


## Повороты костей за кадр: имя → [ось в осях модели, угол] (складываются), потом одним разом на скелет.
var _acc := {}
func _turn(name: String, axis: Vector3, ang: float) -> void:
	if not _b.has(name):
		return
	var i: int = _b[name]
	var local: Vector3 = ((_rg[i] as Basis).inverse() * axis).normalized()
	_acc[i] = (_acc.get(i, Quaternion.IDENTITY) as Quaternion) * Quaternion(local, ang)


func _process(delta: float) -> void:
	t += delta
	_acc.clear()
	var X := Vector3.RIGHT          # поперёк оленя: ноги вперёд/назад, кивок
	var Y := Vector3.UP             # поворот головы
	var Z := Vector3.BACK           # вдоль: крен
	fall = move_toward(fall, 1.0 if mode in ["lie", "dead"] else 0.0, delta * 1.8)
	if _die > 0.0 and mode == "lie":
		life = maxf(0.0, life - _die * delta)
		if life <= 0.0:
			mode = "dead"
	_update_eyes()
	if mode == "walk":
		var ph := fmod(t / PERIOD, 1.0)
		for leg in LEGS:
			var p := fmod(ph + float(LEGS[leg]), 1.0)
			var u: float
			if p < 0.35:
				var k := p / 0.35
				u = lerpf(-1.0, 1.0, k * k * (3.0 - 2.0 * k))           # нога в воздухе — вперёд
			else:
				u = lerpf(1.0, -1.0, (p - 0.35) / 0.65)                   # нога на земле — назад
			var lift: float = sin(PI * p / 0.35) if p < 0.35 else 0.0
			var front: bool = String(leg).begins_with("f")
			_turn(leg + "_up", X, -0.32 * u)
			_turn(leg + "_low", X, (0.55 if front else -0.45) * lift)
			_turn(leg + "_foot", X, (0.4 if front else 0.55) * lift)
		var bob := sin(ph * TAU * 2.0)
		model.position.y = bob * 0.012
		_turn("spine", Z, sin(ph * TAU) * 0.025)
		_turn("neck", X, bob * 0.03)
		position += global_transform.basis.z.normalized() * speed * delta
	elif mode == "stand":
		model.position.y = 0.0
		_turn("chest", X, sin(t * 1.7) * 0.012)                          # дышит
		_turn("tail", X, maxf(0.0, sin(t * 0.9) * sin(t * 7.0)) * 0.6)   # хвост подрагивает
	# голова к цели
	var want := Vector2.ZERO
	if look_target != null and fall < 0.5 and _b.has("head"):
		var hp := skel.global_transform * skel.get_bone_global_pose(_b["head"]).origin
		var d := global_transform.affine_inverse().basis * ((look_target as Vector3) - hp)
		want = Vector2(clampf(atan2(d.x, d.z) - FACE_YAW, -2.0, 0.8), clampf(atan2(d.y, Vector2(d.x, d.z).length()), -0.5, 0.6))
	_look = _look.lerp(want, minf(1.0, delta * 3.0))
	_turn("neck2", Y, _look.x * 0.45)
	_turn("head", Y, _look.x * 0.55)
	_turn("neck2", X, -_look.y * 0.4)
	_turn("head", X, -_look.y * 0.6)
	# сбит: на боку, ноги в разные стороны, часто дышит, пытается поднять голову
	if fall > 0.0:
		var f := fall * fall * (3.0 - 2.0 * fall)
		model.rotation.z = -deg_to_rad(84.0) * f
		model.position.y = 0.17 * f
		model.position.x = -0.42 * f
		var alive := life if mode == "lie" else 0.0
		_bt += delta * lerpf(1.4, 5.2, alive)
		var br := sin(_bt) * lerpf(0.012, 0.035, alive) * (1.0 if mode == "lie" else 0.0)
		_turn("chest", X, br * f)
		_turn("spine", X, -br * 0.6 * f)
		var lift: float = maxf(0.0, sin(t * 0.7)) * alive * alive
		_turn("neck", Z, (0.55 - lift * 0.35) * f)
		_turn("neck2", Z, 0.35 * f)
		_turn("head", X, 0.25 * f)
		_turn("fl_up", X, -0.9 * f)
		_turn("fl_low", X, 1.2 * f)             # сломанная передняя
		_turn("fr_up", X, 0.35 * f)
		_turn("bl_up", X, -0.5 * f)
		_turn("br_up", X, 0.6 * f)
		_turn("br_low", X, -0.7 * f)
		_turn("bl_low", X, sin(t * 3.1) * 0.12 * alive * f)   # нога дёргается
	for i in _b.values():
		skel.set_bone_pose_rotation(i, (_rest[i] as Quaternion) * (_acc.get(i, Quaternion.IDENTITY) as Quaternion))
