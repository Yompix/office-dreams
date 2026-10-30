class_name StoryDB
extends RefCounted
## Сценарий, языки и тайминги реплик старой игры (data/story.json, data/i18n.json, assets/timing*.json).
## Перевод — как в старой игре: ключ — русская строка; нет перевода — английский; kind/kinds/who не переводим (это ключи).

static var story: Dictionary = {}
static var i18n: Dictionary = {}
static var lang := "ru"
static var voice := "ru"        # чьими записями озвучен язык (ru/en/es/pt/de; остальные — английские с субтитрами)
static var tim: Dictionary = {} # реплика → { t, dur, w: [[сек, слово]…], who, g, fx, ew }
static var _dict: Dictionary = {}
static var _scenes: Dictionary = {}
static var _loud: Dictionary = {}     # реплика → громкость голоса по времени (base64, 60 значений в секунду; tools/godot/export_loudness.py)
static var _loud_raw: Dictionary = {}
static var _loud_hz := 60.0
const KEYS := ["kind", "kinds", "who"]
## чья реплика, если в тайминге не указано (как VOICE_WHO в engine.js)
const VOICE_WHO := {"ch_katya": "ЛИЛИ", "ch_whisper": "ЛИЛИ", "ch_monster": "???"}
## знаков в секунду, которые успевают прочесть (японский ~5, китайский ~8, корейский ~11, остальные 14)
const CPS := {"ja": 5.0, "zh": 8.0, "ko": 11.0}


static func setup(l := "ru") -> void:
	if story.is_empty():
		story = JSON.parse_string(FileAccess.get_file_as_string("res://data/story.json"))
		i18n = JSON.parse_string(FileAccess.get_file_as_string("res://data/i18n.json"))
	lang = l if l in i18n.langs else "en"
	voice = i18n.voice.get(lang, "en")
	_dict = i18n.en if lang == "en" else i18n.dict.get(lang, {})
	var f := "res://assets/timing.json" if lang == "ru" else "res://assets/timing_%s.json" % lang
	tim = JSON.parse_string(FileAccess.get_file_as_string(f))
	_scenes.clear()
	_chunks.clear()
	_loud_raw.clear()
	var lf := "res://assets/loud_%s.json" % voice
	if FileAccess.file_exists(lf):
		var j: Dictionary = JSON.parse_string(FileAccess.get_file_as_string(lf))
		_loud = j.lines
		_loud_hz = float(j.hz)


static func t(s: String) -> String:
	if lang == "ru":
		return s
	if _dict.has(s):
		return _dict[s]
	if i18n.en.has(s):
		return i18n.en[s]
	return s


## Сцена с переведёнными строками (кроме ключей kind/kinds/who).
static func scene(id: String) -> Dictionary:
	if _scenes.has(id):
		return _scenes[id]
	var sc = story.scenes.get(id)
	if sc == null:
		return {}
	var d: Dictionary = _translate(sc.duplicate(true))
	_scenes[id] = d
	return d


static func _translate(v):
	if lang == "ru":
		return v
	if v is String:
		return t(v)
	if v is Array:
		for i in v.size():
			v[i] = _translate(v[i])
	elif v is Dictionary:
		for k in v.keys():
			if not (k in KEYS):
				v[k] = _translate(v[k])
	return v


static func line(k: String) -> Dictionary:
	return tim.get(k, {})


static func dur(k: String) -> float:
	return float(line(k).get("dur", 2.0))


static func voice_path(k: String) -> String:
	return "res://assets/snd/%s.mp3" % k if voice == "ru" else "res://assets/snd/%s/%s.mp3" % [voice, k]


## Кто говорит реплику k (ключ для цвета имени и чей рот двигается).
static func who_of(k: String) -> String:
	if VOICE_WHO.has(k):
		return VOICE_WHO[k]
	return String(line(k).get("who", "ИТАН")) if line(k).get("who") != null else "ИТАН"


## Вслух или про себя: рассказ Итана (без тире) — мысли, губы не двигаются; реплики с тире и все остальные — вслух.
static func aloud_of(k: String) -> bool:
	return who_of(k) != "ИТАН" or String(line(k).get("t", "")).strip_edges().begins_with("—")


## Громкость голоса реплики k на секунде tl (0…1), как анализатор старой игры (RMS последних 512 отсчётов × 6).
static func loud(k: String, tl: float) -> float:
	if tl < 0.0:
		return 0.0
	if not _loud_raw.has(k):
		_loud_raw[k] = Marshalls.base64_to_raw(_loud[k]) if _loud.has(k) else PackedByteArray()
	var b: PackedByteArray = _loud_raw[k]
	var f := tl * _loud_hz
	var i := int(f)
	if i >= b.size():
		return 0.0
	var u := f - i
	return lerpf(b[i], b[mini(i + 1, b.size() - 1)], u) / 255.0


## Сколько секунд печатается текст без озвучки (как typed в engine.js).
static func type_dur(s: String) -> float:
	return maxf(2.2, s.length() / float(CPS.get(lang, 14.0)) + 1.3)


static var _chunks := {}
static var _punct: RegEx


## Куски субтитров по словам тайминга (chunks в engine.js): слова полного текста реплики — со знаками препинания — делятся
## по кускам тайминга, чтобы подсвечивать слово ровно тогда, когда оно звучит. Китайский и японский — куски тайминга как есть.
static func chunks(k: String) -> PackedStringArray:
	if _chunks.has(k):
		return _chunks[k]
	var w: Array = line(k).get("w", [])
	var raw := PackedStringArray()
	for x in w:
		raw.append(String(x[1]))
	if lang == "zh" or lang == "ja":
		_chunks[k] = raw
		return raw
	if _punct == null:
		_punct = RegEx.create_from_string("[.,:;—?!…«»\"()\\-¿¡„“”]")
	var toks := []
	for s in String(line(k).get("t", "")).split(" ", false):
		var parts := _punct.sub(s, " ", true).split(" ", false)
		toks.append([s, parts.size()])
	var j := 0
	var debt := 0
	var out := PackedStringArray()
	for x in raw:
		var n: int = x.split(" ", false).size() - debt
		debt = 0
		var part := PackedStringArray()
		while j < toks.size() and (n > 0 or toks[j][1] == 0):
			part.append(toks[j][0])
			n -= toks[j][1]
			j += 1
		if n < 0:
			debt = -n
		out.append(" ".join(part))
	var ok := j == toks.size()
	var any := false
	for o in out:
		if o != "":
			any = true
	_chunks[k] = out if ok and any else raw
	return _chunks[k]
