class_name AnimLib
extends RefCounted
## Клипы движений из anims.glb (Quaternius) и anims2.glb (мокап). В этих файлах нет кожи, поэтому Godot импортирует кости
## как обычные узлы (Armature/root/pelvis/...). Как в старой игре (chars2.js: загрузка клипов), берём только повороты костей
## и высоту таза — так одна анимация подходит и мужчине, и женщине, и вытянутому Жильцу. Дорожки ставим на кости Skeleton3D
## героя (путь «Skeleton3D:кость» от узла модели). Высота таза масштабируется под рост героя (clipsFor в старой игре).

const FILES := ["res://assets/models/anims.glb", "res://assets/models/anims2.glb"]
const RENAME := {"root": "Root", "Head": "head"}   # имена костей клипов → имена костей героев MakeHuman
static var _raw: Dictionary = {}       # имя клипа → Animation (повороты + таз, без масштаба)
static var _qw: Dictionary = {}        # кость → поворот в покое в пространстве модели (скелет клипов) — QW старой игры
static var _pelvis_z := 0.0
static var _scaled: Dictionary = {}    # масштаб таза → AnimationLibrary


static func _load() -> void:
	if not _raw.is_empty():
		return
	for f in FILES:
		var sc: Node3D = (load(f) as PackedScene).instantiate()
		var ap: AnimationPlayer = sc.find_child("AnimationPlayer", true, false)
		for n in ap.get_animation_list():
			_raw[n] = _convert(ap.get_animation(n))
		if f == FILES[0]:
			_collect_qw(sc, Transform3D.IDENTITY)
			var pel: Node3D = sc.find_child("pelvis", true, false)
			_pelvis_z = pel.position.z
		sc.free()


static func _collect_qw(n: Node, parent: Transform3D) -> void:
	var g := parent
	if n is Node3D:
		g = parent * (n as Node3D).transform
		_qw[String(n.name)] = g.basis.orthonormalized().get_rotation_quaternion()
	for c in n.get_children():
		_collect_qw(c, g)


static func _convert(src: Animation) -> Animation:
	var a := Animation.new()
	a.length = src.length
	for i in src.get_track_count():
		var p := src.track_get_path(i)
		var bone := String(p.get_name(p.get_name_count() - 1))
		var ty := src.track_get_type(i)
		if not (ty == Animation.TYPE_ROTATION_3D or (ty == Animation.TYPE_POSITION_3D and bone == "pelvis")):
			continue
		bone = RENAME.get(bone, bone)
		var t := a.add_track(ty)
		a.track_set_path(t, NodePath("Skeleton3D:" + bone))
		a.track_set_interpolation_type(t, src.track_get_interpolation_type(i))
		for k in src.track_get_key_count(i):
			a.track_insert_key(t, src.track_get_key_time(i, k), src.track_get_key_value(i, k), src.track_get_key_transition(i, k))
	return a


## Поворот покоя кости скелета клипов в пространстве модели (по имени кости клипов: root, pelvis, Head…).
static func rest_world(bone: String) -> Variant:
	_load()
	return _qw.get(bone)


static func pelvis_z() -> float:
	_load()
	return _pelvis_z


## Библиотека клипов с высотой таза под героя (k = таз героя / таз скелета клипов).
static func library(k := 1.0) -> AnimationLibrary:
	_load()
	var key := snappedf(k, 0.001)
	if _scaled.has(key):
		return _scaled[key]
	var lib := AnimationLibrary.new()
	for n in _raw:
		var a: Animation = _raw[n]
		if not is_equal_approx(key, 1.0):
			a = a.duplicate(true)
			for t in a.get_track_count():
				if a.track_get_type(t) == Animation.TYPE_POSITION_3D:
					for kk in a.track_get_key_count(t):
						a.track_set_key_value(t, kk, (a.track_get_key_value(t, kk) as Vector3) * key)
		lib.add_animation(n, a)
	_scaled[key] = lib
	return lib
