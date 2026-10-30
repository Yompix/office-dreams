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
const KEYS := ["kind", "kinds", "who"]


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
