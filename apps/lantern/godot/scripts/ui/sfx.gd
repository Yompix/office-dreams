class_name Sfx
extends RefCounted
## 1.8: мягкие звуки интерфейса (tools/make_sounds.py): tap, success, join, error. Выключатель — «Звуки» в настройках.

static var _players := {}
static var _last := 0


static func on() -> bool:
	return Cfg.autotest == "" and bool(Cfg.get_value("app", "sounds", true))


static func play(name: String) -> void:
	if not on():
		return
	var now := Time.get_ticks_msec()
	if name == "tap" and now - _last < 60:
		return
	_last = now
	var p: AudioStreamPlayer = _players.get(name)
	if p == null or not is_instance_valid(p):
		var st: AudioStream = load("res://assets/sfx/%s.wav" % name)
		var tree := Engine.get_main_loop() as SceneTree
		if st == null or tree == null:
			return
		p = AudioStreamPlayer.new()
		p.stream = st
		p.volume_db = -8.0
		tree.root.add_child(p)
		_players[name] = p
	p.play()
