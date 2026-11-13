extends Node
## Настройки LANTERN: папка данных, акцент, недавние серверы, последние параметры мира.
## Никаких паролей и ключей здесь нет.

const APP_NAME := "LANTERN"
const APP_VERSION := "1.8.1"
const AUTHOR := "Yompix"
const SETTINGS_PATH := "user://settings.cfg"
## Платные функции (LANTERN Pro, наборы искр, премиум-путь сезона, реклама) и их ДЕМО.
## В Google Play 1.5.0 выходит бесплатной: покупки и AdMob не подключены — всё это скрыто.
## Включить для проверки: --paid=1 в командной строке (автотесты) или PAID := true в сборке.
const PAID := false
## Обязательная оговорка (Minecraft Usage Guidelines): на «О программе», первом экране, в магазине и на сайте.
const DISCLAIMER := "НЕ ЯВЛЯЕТСЯ ОФИЦИАЛЬНЫМ ПРОДУКТОМ MINECRAFT. НЕ ОДОБРЕНО И НЕ СВЯЗАНО С MOJANG ИЛИ MICROSOFT."
const DISCLAIMER_EN := "NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT."
const SITE := "https://office-dreams-production.up.railway.app/lantern"
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


var _path := SETTINGS_PATH


func _ready() -> void:
	_parse_cmdline()
	# 1.8: автотест со своей папкой данных хранит там и настройки — прогоны не портят друг другу (и приложению) settings.cfg
	if autotest != "" and autotest_args.has("data"):
		DirAccess.make_dir_recursive_absolute(String(autotest_args["data"]))
		_path = String(autotest_args["data"]).path_join("settings.cfg")
	cfg.load(_path)
	# язык: выбранный в настройках, иначе язык системы (нет такого перевода — английский). Автотесты — по-русски (--lang=en меняет).
	L.apply(String(autotest_args.get("lang", "ru")) if autotest != "" else String(get_value("app", "lang", L.detect())))
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
	cfg.save(_path)


func default_data_dir() -> String:
	return OS.get_user_data_dir().path_join("data")


func data_dir() -> String:
	var d: String = get_value("app", "data_dir", "")
	return d if d != "" else default_data_dir()


func sub(path: String) -> String:
	var p := data_dir().path_join(path)
	DirAccess.make_dir_recursive_absolute(p)
	return p


## Папка для серверов и Java. На Windows Java (лаунчер java.exe и сам Paper) работает с путями через
## кодовую страницу системы: «C:\\Users\\Іван Петренко\\…» на английской Windows превращается в «????»,
## и сервер не стартует («could not find java.dll»). Поэтому, если в пути данных есть не-ASCII символы,
## серверы и Java кладём в %ProgramData%\\LANTERN (или C:\\LANTERN). Уже установленные серверы не переносим.
func server_root() -> String:
	var d := data_dir()
	if not is_windows() or is_ascii_path(d) or DirAccess.dir_exists_absolute(d.path_join("servers/java")) or DirAccess.dir_exists_absolute(d.path_join("servers/bedrock")):
		return d
	var cands := PackedStringArray()
	var pd := OS.get_environment("ProgramData")
	if pd != "":
		cands.append(pd.replace("\\", "/").path_join("LANTERN"))
	var sd := OS.get_environment("SystemDrive")
	cands.append((sd if sd != "" else "C:") + "/LANTERN")
	for c in cands:
		if is_ascii_path(c) and DirAccess.make_dir_recursive_absolute(c) == OK and _writable(c):
			return c
	return d


func server_sub(path: String) -> String:
	var p := server_root().path_join(path)
	DirAccess.make_dir_recursive_absolute(p)
	return p


static func is_ascii_path(p: String) -> bool:
	for c in p:
		if c.unicode_at(0) > 127:
			return false
	return true


static func _writable(dir: String) -> bool:
	var probe := dir.path_join(".lantern-write-test")
	var f := FileAccess.open(probe, FileAccess.WRITE)
	if f == null:
		return false
	f.close()
	DirAccess.remove_absolute(probe)
	return true


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


## Телефонный вид на ПК для скриншотов/проверок (--phone=1): без минимального размера окна, как на Android.
func phone_preview() -> bool:
	return autotest_args.has("phone")


## Телефон или телефонный вид: создавать мир нельзя (сервер — только на ПК).
## Настройки игры из формы «Создать игру»: название, режим, мест (всего, с хозяином), пароль, видна всем
const MODES := [["survival", "Выживание"], ["creative", "Творчество"], ["minigame", "Мини-игра"], ["pvp", "PvP"], ["roleplay", "Ролевая"], ["other", "Другое"]]


func game() -> Dictionary:
	return {"title": String(get_value("game", "title", "")), "mode": String(get_value("game", "mode", "survival")),
		"cap": int(get_value("game", "cap", 10)), "password": String(get_value("game", "password", "")),
		"public": bool(get_value("game", "public", true))}


static func mode_name(m: String) -> String:
	for x in MODES:
		if x[0] == m:
			return x[1]
	return L.t("Другое")


## Показывать ли платные функции (см. PAID)
func paid() -> bool:
	return PAID or autotest_args.has("paid")


func mobile() -> bool:
	return OS.has_feature("mobile") or phone_preview()


static func is_windows() -> bool:
	return OS.get_name() == "Windows"
