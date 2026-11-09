extends Node
## Настройки LANTERN: папка данных, акцент, недавние серверы, последние параметры мира.
## Никаких паролей и ключей здесь нет.

const APP_NAME := "LANTERN"
const APP_VERSION := "1.0.0"
const AUTHOR := "Yompix"
const SETTINGS_PATH := "user://settings.cfg"
## Адрес relay по умолчанию: ретранслятор работает на нашем сервере Railway вместе с сайтом
## (apps/lantern/relay подключён в server/index.js, путь /lantern/ws). Свой отдельный сервис — см. relay/README.md.
const DEFAULT_RELAY := "wss://office-dreams-production.up.railway.app/lantern/ws"

const ACCENTS := {
	"amber": Color("ffb648"),
	"green": Color("4cc96e"),
	"sky": Color("4aa8ff"),
	"violet": Color("9d7bff"),
	"orange": Color("ff9a3d"),
	"pink": Color("ff6fae"),
}

signal accent_changed(color: Color)

var cfg := ConfigFile.new()
var autotest := ""          # --autotest <режим>
var autotest_args := {}     # --key=value из командной строки


func _ready() -> void:
	cfg.load(SETTINGS_PATH)
	_parse_cmdline()
	if autotest_args.has("data"):
		set_value("app", "data_dir", String(autotest_args["data"]))
	DirAccess.make_dir_recursive_absolute(data_dir())


func _parse_cmdline() -> void:
	var args := OS.get_cmdline_user_args()
	if args.is_empty():
		args = OS.get_cmdline_args()
	var i := 0
	while i < args.size():
		var a: String = args[i]
		if a == "--autotest" and i + 1 < args.size():
			autotest = args[i + 1]
			i += 1
		elif a.begins_with("--") and a.contains("="):
			var kv := a.substr(2).split("=", true, 1)
			autotest_args[kv[0]] = kv[1]
		i += 1


func get_value(section: String, key: String, def: Variant = null) -> Variant:
	return cfg.get_value(section, key, def)


func set_value(section: String, key: String, v: Variant) -> void:
	cfg.set_value(section, key, v)
	cfg.save(SETTINGS_PATH)


func default_data_dir() -> String:
	return OS.get_user_data_dir().path_join("data")


func data_dir() -> String:
	var d: String = get_value("app", "data_dir", "")
	return d if d != "" else default_data_dir()


func sub(path: String) -> String:
	var p := data_dir().path_join(path)
	DirAccess.make_dir_recursive_absolute(p)
	return p


func relay_url() -> String:
	if autotest_args.has("relay"):
		return String(autotest_args["relay"])
	var r: String = get_value("app", "relay", "")
	return r if r != "" else DEFAULT_RELAY


func accent() -> Color:
	return ACCENTS.get(get_value("app", "accent", "amber"), ACCENTS["amber"])


func set_accent(key: String) -> void:
	set_value("app", "accent", key)
	accent_changed.emit(accent())


# ---- недавние серверы (зайти к другу)
func recent() -> Array:
	return get_value("join", "recent", [])


func add_recent(entry: Dictionary) -> void:
	var list: Array = recent()
	for i in range(list.size() - 1, -1, -1):
		if list[i].get("address", "") == entry.get("address", "") and list[i].get("edition", "") == entry.get("edition", ""):
			list.remove_at(i)
	entry["time"] = Time.get_unix_time_from_system()
	list.push_front(entry)
	while list.size() > 12:
		list.pop_back()
	set_value("join", "recent", list)


func remove_recent(idx: int) -> void:
	var list: Array = recent()
	if idx >= 0 and idx < list.size():
		list.remove_at(idx)
		set_value("join", "recent", list)


static func is_windows() -> bool:
	return OS.get_name() == "Windows"
