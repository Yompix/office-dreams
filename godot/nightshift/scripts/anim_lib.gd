class_name AnimLib
extends RefCounted
## Клипы движений из anims.glb и anims2.glb. В этих файлах нет кожи, поэтому Godot импортирует кости
## как обычные узлы (Armature/root/pelvis/...). Здесь дорожки переводятся на кости Skeleton3D героев
## (путь «Skeleton3D:кость» от узла модели), как в старой игре: те же клипы на тех же 53 костях.

const FILES := ["res://assets/models/anims.glb", "res://assets/models/anims2.glb"]
static var _lib: AnimationLibrary


static func library() -> AnimationLibrary:
	if _lib:
		return _lib
	_lib = AnimationLibrary.new()
	for f in FILES:
		var sc: Node = (load(f) as PackedScene).instantiate()
		var ap: AnimationPlayer = sc.find_child("AnimationPlayer", true, false)
		for n in ap.get_animation_list():
			_lib.add_animation(n, _convert(ap.get_animation(n)))
		sc.free()
	return _lib


static func _convert(src: Animation) -> Animation:
	var a := Animation.new()
	a.length = src.length
	for i in src.get_track_count():
		var p := src.track_get_path(i)
		var bone := String(p.get_name(p.get_name_count() - 1))
		if bone == "root":
			bone = "Root"
		var t := a.add_track(src.track_get_type(i))
		a.track_set_path(t, NodePath("Skeleton3D:" + bone))
		a.track_set_interpolation_type(t, src.track_get_interpolation_type(i))
		for k in src.track_get_key_count(i):
			a.track_insert_key(t, src.track_get_key_time(i, k), src.track_get_key_value(i, k), src.track_get_key_transition(i, k))
	return a
