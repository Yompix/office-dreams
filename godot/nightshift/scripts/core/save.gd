class_name Save
extends RefCounted
## Сохранение — как localStorage старой игры (ключи ns1_*): где остановился (сцена, часы ночи), флаги, смена (пульт, записи,
## кто где), найденные концовки и смерти, обучение пройдено, прохождение (журнал дежурств) и настройки. Файл user://save.json.

const PATH := "user://save.json"
static var d := {}


static func load_all() -> void:
	if not d.is_empty():
		return
	if FileAccess.file_exists(PATH):
		var j = JSON.parse_string(FileAccess.get_file_as_string(PATH))
		if j is Dictionary:
			d = j
	if not d.has("set"):
		d.set = {}
	var s: Dictionary = d.set
	var def := {"lang": "", "voice": true, "calm": false, "auto": false, "gfx": "high", "full": true,
			"vol": {"all": 1.0, "mus": 0.8, "voice": 1.0, "sfx": 0.9, "amb": 0.8}}
	for k in def:
		if not s.has(k):
			s[k] = def[k]


static func get_v(k: String, def = null):
	load_all()
	return d.get(k, def)


static func set_v(k: String, v) -> void:
	load_all()
	d[k] = v
	write()


static func setting(k: String, def = null):
	load_all()
	return d.set.get(k, def)


static func set_setting(k: String, v) -> void:
	load_all()
	d.set[k] = v
	write()


static func write() -> void:
	var f := FileAccess.open(PATH, FileAccess.WRITE)
	if f:
		f.store_string(JSON.stringify(d))


## Громкости по шинам (Музыка, Голоса, Звуки, Фон) и общая.
static func apply_volumes() -> void:
	load_all()
	var v: Dictionary = d.set.vol
	for b in [["Master", "all"], ["Music", "mus"], ["Voice", "voice"], ["SFX", "sfx"], ["Amb", "amb"]]:
		var i := AudioServer.get_bus_index(b[0])
		if i >= 0:
			AudioServer.set_bus_volume_db(i, linear_to_db(maxf(0.0001, float(v.get(b[1], 1.0)))))


## Шины звука (как в старой игре: музыка, голос, звуки, фон — каждая со своей громкостью).
static func ensure_buses() -> void:
	for n in ["Music", "Voice", "SFX", "Amb"]:
		if AudioServer.get_bus_index(n) < 0:
			AudioServer.add_bus()
			var i := AudioServer.bus_count - 1
			AudioServer.set_bus_name(i, n)
			AudioServer.set_bus_send(i, "Master")
	apply_volumes()
