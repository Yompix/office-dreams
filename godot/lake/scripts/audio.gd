class_name Sfx
extends Node
## Звук игры. Фоны и петли — «грядки» (bed): игра каждый кадр говорит, какой громкости должна быть каждая петля
## (салон, лес, заправка, магазин, буран, гул, дыхание оленя, шаги, радио), а громкость сама плавно идёт к ней —
## склейка снаружи/внутри машины звучит как смена места, без щелчков. Разовые звуки — play().
## Файлы — snd/<имя>.ogg: ElevenLabs Sound Effects на платном тарифе владельца (описания — tools/lake/sfx.json),
## подводный гул и щелчок — кодом (tools/lake/synth.py). Громкость выровнена: snd/levels.json
## (tools/lake/snd_levels.py) — 0 дБ в игре = нормальная громкость любого звука.

const OFF := -60.0
const ALIAS := {"deer_steps": "snow_steps"}   # вторая петля из того же файла (шаги оленя)
var _beds := {}                                # имя → [плеер, нужная громкость, нужная высота, текущая громкость]
var _pool: Array[AudioStreamPlayer] = []
var _cache := {}
var _gain := {}
var master := 1.0                              # общая громкость (0…1)
var _duck := 0.0                               # пауза: всё тише
var _vo: AudioStreamPlayer                     # голос реплики (одна за раз)
var _vo_duck := 0.0                            # пока звучит голос, фоны тише на 5 дБ


func _ready() -> void:
	process_mode = Node.PROCESS_MODE_ALWAYS
	if FileAccess.file_exists("res://snd/levels.json"):
		var j = JSON.parse_string(FileAccess.get_file_as_string("res://snd/levels.json"))
		if j is Dictionary:
			_gain = j
	for i in 12:
		var p := AudioStreamPlayer.new()
		add_child(p)
		_pool.append(p)
	_vo = AudioStreamPlayer.new()
	add_child(_vo)


func _file(name: String) -> String:
	return String(ALIAS.get(name, name))


func gain(name: String) -> float:
	return float(_gain.get(_file(name), 0.0))


func _stream(name: String, loop: bool) -> AudioStream:
	var f := _file(name)
	var key := f + ("@l" if loop else "")
	if _cache.has(key):
		return _cache[key]
	var path := "res://snd/%s.ogg" % f
	if not ResourceLoader.exists(path):
		return null
	var s := (load(path) as AudioStreamOggVorbis).duplicate() as AudioStreamOggVorbis
	s.loop = loop
	_cache[key] = s
	return s


## Петля: нужная громкость (дБ, OFF — выключить) и высота. Звать можно каждый кадр.
func bed(name: String, db: float, pitch := 1.0) -> void:
	var b: Array = _beds.get(name, [])
	if b.is_empty():
		if db <= OFF:
			return
		var s := _stream(name, true)
		if s == null:
			return
		var p := AudioStreamPlayer.new()
		p.stream = s
		p.volume_db = OFF
		add_child(p)
		b = [p, db, pitch, OFF]
		_beds[name] = b
	b[1] = db
	b[2] = pitch


func beds_off() -> void:
	for k in _beds:
		_beds[k][1] = OFF


func _process(delta: float) -> void:
	_vo_duck = move_toward(_vo_duck, -5.0 if _vo.playing else 0.0, delta * 20.0)
	for k in _beds:
		var b: Array = _beds[k]
		var p: AudioStreamPlayer = b[0]
		var want: float = b[1]
		var cur: float = b[3]
		if want > OFF and not p.playing:
			cur = OFF
			p.play(randf() * maxf(0.0, p.stream.get_length() - 1.0))
		cur = move_toward(cur, want, delta * (70.0 if want > cur else 45.0))
		b[3] = cur
		p.volume_db = cur + gain(k) + _vo_duck
		p.pitch_scale = move_toward(p.pitch_scale, float(b[2]), delta * 0.6)
		if cur <= OFF and want <= OFF and p.playing:
			p.stop()


## Разовый звук: громкость (дБ), высота, через сколько секунд.
func play(name: String, db := 0.0, pitch := 1.0, delay := 0.0) -> void:
	if delay > 0.0:
		get_tree().create_timer(delay, true).timeout.connect(play.bind(name, db, pitch, 0.0))
		return
	var s := _stream(name, false)
	if s == null:
		return
	var p: AudioStreamPlayer = null
	for q in _pool:
		if not q.playing:
			p = q
			break
	if p == null:
		p = _pool[0]
	p.stream = s
	p.volume_db = db + gain(name)
	p.pitch_scale = pitch
	p.play()


## Остановить разовый звук (колыбельная): плавно за fade секунд.
func stop(name: String, fade := 0.5) -> void:
	var s := _stream(name, false)
	for p in _pool:
		if p.playing and p.stream == s:
			var tw := create_tween()
			tw.tween_property(p, "volume_db", -60.0, fade)
			tw.tween_callback(p.stop)


## Голос реплики: файл snd/vo/<язык>/<ключ>.ogg, g — множитель громкости из timing.json. Новая реплика гасит прошлую.
func voice(path: String, g := 1.0) -> bool:
	if not ResourceLoader.exists(path):
		return false
	_vo.stop()
	_vo.stream = load(path)
	_vo.volume_db = linear_to_db(maxf(g, 0.01))
	_vo.play()
	return true


func voice_stop() -> void:
	_vo.stop()


func set_master(v: float) -> void:
	master = clampf(v, 0.0, 1.0)
	_apply_bus()


## Пауза: всё на 14 дБ тише.
func duck(on: bool) -> void:
	_duck = -14.0 if on else 0.0
	_apply_bus()


func _apply_bus() -> void:
	AudioServer.set_bus_volume_db(0, linear_to_db(maxf(master, 0.0001)) + _duck)
