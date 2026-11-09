class_name Sfx
extends Node
## Звук игры. Фоны и петли — «грядки» (bed): игра каждый кадр говорит, какой громкости должна быть каждая петля
## (салон, лес, заправка, магазин, буран, гул, дыхание оленя, шаги, радио), а громкость сама плавно идёт к ней —
## склейка снаружи/внутри машины звучит как смена места, без щелчков. Разовые звуки — play().
## Файлы — snd/<имя>.ogg: ElevenLabs Sound Effects на платном тарифе владельца (описания — tools/sfx.json),
## подводный гул и щелчок — кодом (tools/synth.py). Громкость выровнена: snd/levels.json
## (tools/snd_levels.py) — 0 дБ в игре = нормальная громкость любого звука.

const OFF := -60.0
const ALIAS := {"deer_steps": "snow_steps"}   # вторая петля из того же файла (шаги оленя)
const MUSIC := ["lake_theme", "lake_end"]   # музыка (шина Music), остальное — шина SFX, голоса — Voice
var _beds := {}                                # имя → [плеер, нужная громкость, нужная высота, текущая громкость]
var _pool: Array[AudioStreamPlayer] = []
var _cache := {}
var _gain := {}
var master := 1.0                              # общая громкость (0…1)
var _duck := 0.0                               # пауза: всё тише
var _vo: AudioStreamPlayer                     # голос реплики (одна за раз)
var _vo_duck := 0.0                            # пока звучит голос, фоны тише на 5 дБ
var _vo_tw: Tween = null                       # мягкий обрыв реплики (пропуск)


func _ready() -> void:
	process_mode = Node.PROCESS_MODE_ALWAYS
	if FileAccess.file_exists("res://snd/levels.json"):
		var j = JSON.parse_string(FileAccess.get_file_as_string("res://snd/levels.json"))
		if j is Dictionary:
			_gain = j
	for bn in ["Music", "SFX", "Voice"]:   # свои шины: громкость музыки, звуков и голосов — отдельно (настройки)
		if AudioServer.get_bus_index(bn) < 0:
			AudioServer.add_bus()
			var i := AudioServer.bus_count - 1
			AudioServer.set_bus_name(i, bn)
			AudioServer.set_bus_send(i, "Master")
	for i in 12:
		var p := AudioStreamPlayer.new()
		p.bus = "SFX"
		add_child(p)
		_pool.append(p)
	_vo = AudioStreamPlayer.new()
	_vo.bus = "Voice"
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
		p.bus = "Music" if name in MUSIC else "SFX"
		add_child(p)
		b = [p, db, pitch, OFF]
		_beds[name] = b
	b[1] = db
	b[2] = pitch


func beds_off() -> void:
	for k in _beds:
		_beds[k][1] = OFF


func _process(delta: float) -> void:
	# приглушение фонов под голосом: быстро вниз, медленно вверх — в паузе 0,35 с между репликами фоны не «качает»
	var dk := -5.0 if voice_playing() else 0.0
	_vo_duck = move_toward(_vo_duck, dk, delta * (20.0 if dk < _vo_duck else 4.0))
	for k in _beds:
		var b: Array = _beds[k]
		var p: AudioStreamPlayer = b[0]
		var want: float = b[1]
		var cur: float = b[3]
		if want > OFF and not p.playing:
			cur = OFF
			# фоны — с любого места (не слышно повтора); музыка — с начала фразы (тема меню начиналась с середины)
			p.play(0.0 if k in MUSIC else randf() * maxf(0.0, p.stream.get_length() - 1.0))
		cur = move_toward(cur, want, delta * (70.0 if want > cur else 45.0))
		b[3] = cur
		p.volume_db = cur + gain(k) + _vo_duck
		p.pitch_scale = move_toward(p.pitch_scale, float(b[2]), delta * 0.6)
		if cur <= OFF and want <= OFF and p.playing:
			p.stop()


## Разовый звук: громкость (дБ), высота, через сколько секунд; dur > 0 — только первые dur секунд (кусочек длинного
## файла: шаг из петли шагов), с коротким затуханием.
func play(name: String, db := 0.0, pitch := 1.0, delay := 0.0, dur := 0.0) -> void:
	if delay > 0.0:   # таймер стоит, пока игра на паузе (раньше отложенные звуки звучали под меню паузы)
		get_tree().create_timer(delay, false).timeout.connect(play.bind(name, db, pitch, 0.0, dur))
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
	p.bus = "Music" if name in MUSIC else "SFX"
	p.volume_db = db + gain(name)
	p.pitch_scale = pitch
	p.play()
	if dur > 0.0:
		var tm := get_tree().create_timer(dur, false)
		tm.timeout.connect(func():
			if p.playing and p.stream == s:
				var tw := create_tween()
				tw.tween_property(p, "volume_db", p.volume_db - 30.0, 0.08)
				tw.tween_callback(p.stop))


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
	if _vo_tw:
		_vo_tw.kill()
		_vo_tw = null
	_vo.stop()
	_vo.stream = load(path)
	_vo.volume_db = linear_to_db(maxf(g, 0.01))
	_vo.stream_paused = false
	_vo.play()
	return true


## Конец реплики или пропуск: голос гаснет за 60 мс (без щелчка обрыва).
func voice_stop(fade := 0.06) -> void:
	if not _vo.playing:
		return
	if _vo_tw:
		_vo_tw.kill()
	_vo_tw = create_tween()
	_vo_tw.tween_property(_vo, "volume_db", _vo.volume_db - 30.0, fade)
	_vo_tw.tween_callback(_vo.stop)


func voice_playing() -> bool:
	return _vo.playing and not _vo.stream_paused


func set_master(v: float) -> void:
	master = clampf(v, 0.0, 1.0)
	_apply_bus()


## Громкость из настроек: master (общая), music, sfx, voice — 0…1.
func set_volumes(vol: Dictionary) -> void:
	for k in ["music", "sfx", "voice"]:
		var i := AudioServer.get_bus_index({"music": "Music", "sfx": "SFX", "voice": "Voice"}[k])
		if i >= 0:
			AudioServer.set_bus_volume_db(i, linear_to_db(maxf(float(vol.get(k, 1.0)), 0.0001)))
	set_master(float(vol.get("master", 1.0)))


## Пауза: фоны на 14 дБ тише, голос реплики и разовые звуки замирают и продолжаются с того же места (раньше реплика
## доигрывала под меню, а после паузы субтитр и губы шли молча).
func duck(on: bool) -> void:
	_duck = -14.0 if on else 0.0
	_apply_bus()
	_vo.stream_paused = on and _vo.playing
	for p in _pool:
		if p.playing or p.stream_paused:
			p.stream_paused = on


func _apply_bus() -> void:
	AudioServer.set_bus_volume_db(0, linear_to_db(maxf(master, 0.0001)) + _duck)
