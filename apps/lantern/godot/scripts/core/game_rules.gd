class_name GameRules
extends RefCounted
## 1.8 «Настройки игры»: правила мира на сервере LANTERN (Paper или Bedrock Dedicated Server).
## Хранятся для каждого мира отдельно (settings.cfg: host/rules), применяются командами сервера сразу
## и заново при каждом запуске. Мир, открытый из самого Minecraft, снаружи не настроить — там правил нет.

## Правила «да/нет»: [ключ, название, пояснение, по умолчанию]
const SWITCHES := [
	["pvp", "Драки между игроками (PvP)", "Игроки могут ранить друг друга.", true],
	["explosions", "Взрывы ломают блоки", "Динамит и монстры разрушают постройки.", true],
	["fire", "Огонь распространяется", "Пожар может перекинуться на дом и лес.", true],
	["keep", "Вещи остаются после смерти", "После гибели рюкзак не выпадает.", false],
	["monsters", "Монстры", "Зомби, скелеты и другие враги появляются в мире.", true],
	["daylight", "Смена дня и ночи", "Выключите — время в мире остановится.", true],
	["weather", "Погода", "Дождь, гроза и снег. Выключите — всегда ясно.", true],
	["cheats", "Команды для всех игроков (читы)", "Каждый сможет менять режим, выдавать вещи и телепортироваться.", false],
]
const DIFFS := [["peaceful", "Мирная"], ["easy", "Лёгкая"], ["normal", "Обычная"], ["hard", "Сложная"]]
const GAMEMODES := [["survival", "Выживание"], ["creative", "Творчество"], ["adventure", "Приключение"]]
const GAMEMODE_HINTS := {
	"survival": "Добываем ресурсы, строим, сражаемся и можем погибнуть.",
	"creative": "Все блоки сразу, можно летать, никто не погибает.",
	"adventure": "Для готовых карт: блоки ломать и ставить нельзя.",
}
const AUTOBACKUP := [[0, "Выключена"], [30, "Раз в 30 минут"], [60, "Раз в час"], [180, "Раз в 3 часа"]]


static func defaults() -> Dictionary:
	var d := {"whitelist": false, "wl": [], "admins": [], "autobackup": 0}
	for s in SWITCHES:
		d[s[0]] = s[3]
	return d


static func key(edition: String, world: String) -> String:
	return "%s:%s" % [edition, world]


## Правила мира (с умолчаниями). Старые «Защита мира» из 1.7 (host/protect: «Без PvP» и т. п.) переносятся сами.
static func load_for(edition: String, world: String) -> Dictionary:
	var d := defaults()
	var all: Variant = Cfg.get_value("host", "rules", {})
	var saved: Variant = (all as Dictionary).get(key(edition, world)) if all is Dictionary else null
	if saved is Dictionary:
		d.merge(saved, true)
		return d
	var old_all: Variant = Cfg.get_value("host", "protect", {})
	var old: Variant = (old_all as Dictionary).get(key(edition, world)) if old_all is Dictionary else null
	if old is Dictionary:
		d.merge(from_protect(old), true)
	return d


static func from_protect(old: Dictionary) -> Dictionary:
	var d := {}
	if old.has("pvp"):
		d["pvp"] = not bool(old.pvp)
	if old.has("boom"):
		d["explosions"] = not bool(old.boom)
	if old.has("fire"):
		d["fire"] = not bool(old.fire)
	if old.has("keep"):
		d["keep"] = bool(old.keep)
	return d


static func save_for(edition: String, world: String, d: Dictionary) -> void:
	var all: Variant = Cfg.get_value("host", "rules", {})
	var a: Dictionary = (all as Dictionary).duplicate(true) if all is Dictionary else {}
	a[key(edition, world)] = d
	Cfg.set_value("host", "rules", a)


## Новые имена правил (snake_case, правило pvp, огонь — радиусом) — с Java 1.21.11.
static func new_names(mc_version: String) -> bool:
	return mc_version == "" or ServerHost.ver_cmp(mc_version, "1.21.11") >= 0


static func clean_nick(n: String) -> String:
	var out := ""
	for c in n.strip_edges():
		if (c >= "a" and c <= "z") or (c >= "A" and c <= "Z") or (c >= "0" and c <= "9") or c == "_" or c == "." or c == " " or c == "-":
			out += c
	return out.substr(0, 32).strip_edges()


static func _b(v: bool) -> String:
	return "true" if v else "false"


static func _q(edition: String, nick: String) -> String:
	return '"%s"' % nick if edition == "bedrock" else nick.replace(" ", "")


## Команды сервера для одного правила. ctx: mc (версия Java-сервера), players (кто сейчас в игре), host (ник хозяина),
## admins (список админов) — нужны для «читов».
static func commands(edition: String, rule: String, value: Variant, ctx := {}) -> PackedStringArray:
	var out := PackedStringArray()
	var bed := edition == "bedrock"
	var nn := new_names(String(ctx.get("mc", "")))
	match rule:
		"difficulty":
			out.append("difficulty " + String(value))
		"gamemode":
			out.append("defaultgamemode " + String(value))
			out.append("gamemode %s @a" % String(value))
		"pvp":
			out.append("gamerule pvp " + _b(value))
		"explosions":
			if bed:
				out.append("gamerule tntexplodes " + _b(value))
				out.append("gamerule mobgriefing " + _b(value))
			elif nn:
				out.append("gamerule tnt_explodes " + _b(value))
				out.append("gamerule mob_griefing " + _b(value))
			else:
				out.append("gamerule tntExplodes " + _b(value))
				out.append("gamerule mobGriefing " + _b(value))
		"fire":
			if bed:
				out.append("gamerule dofiretick " + _b(value))
			elif nn:
				out.append("gamerule fire_spread_radius_around_player " + ("128" if value else "0"))
			else:
				out.append("gamerule doFireTick " + _b(value))
		"keep":
			out.append("gamerule %s %s" % ["keepinventory" if bed else ("keep_inventory" if nn else "keepInventory"), _b(value)])
		"monsters":
			if bed:
				out.append("gamerule domobspawning " + _b(value))
			elif nn:
				out.append("gamerule spawn_monsters " + _b(value))
			else:
				out.append("gamerule doMobSpawning " + _b(value))
		"daylight":
			out.append("gamerule %s %s" % ["dodaylightcycle" if bed else ("advance_time" if nn else "doDaylightCycle"), _b(value)])
		"weather":
			out.append("gamerule %s %s" % ["doweathercycle" if bed else ("advance_weather" if nn else "doWeatherCycle"), _b(value)])
			if not value:
				out.append("weather clear")
		"cheats":
			if bed:
				out.append("changesetting allow-cheats " + _b(value))
			else:
				var keep: Array = (ctx.get("admins", []) as Array).duplicate()
				keep.append(String(ctx.get("host", "")))
				for p in ctx.get("players", []):
					if value:
						out.append("op " + String(p))
					elif not String(p) in keep:
						out.append("deop " + String(p))
		"whitelist":
			out.append(("allowlist " if bed else "whitelist ") + ("on" if value else "off"))
		"wl_add":
			out.append(("allowlist add " if bed else "whitelist add ") + _q(edition, String(value)))
		"wl_remove":
			out.append(("allowlist remove " if bed else "whitelist remove ") + _q(edition, String(value)))
		"admin_add":
			out.append("op " + _q(edition, String(value)))
		"admin_remove":
			out.append("deop " + _q(edition, String(value)))
		"pardon":
			if not bed:
				out.append("pardon " + _q(edition, String(value)))
		"save":
			out.append("save hold" if bed else "save-all")
	return out


## Все команды при запуске сервера: правила, белый список, админы (режим и сложность — в server.properties).
static func start_commands(edition: String, d: Dictionary, ctx := {}) -> PackedStringArray:
	var out := PackedStringArray()
	for s in SWITCHES:
		var k: String = s[0]
		if k == "cheats" and edition != "bedrock":
			continue          # «читы» на Java — выдаём права входящим игрокам (RoomControl)
		if k == "pvp" and edition != "bedrock" and not new_names(String(ctx.get("mc", ""))):
			continue          # старые версии: pvp — настройка server.properties
		out.append_array(commands(edition, k, bool(d.get(k, s[3])), ctx))
	for n in d.get("wl", []):
		out.append_array(commands(edition, "wl_add", n))
	out.append_array(commands(edition, "whitelist", bool(d.get("whitelist", false))))
	for n in d.get("admins", []):
		out.append_array(commands(edition, "admin_add", n))
	return out
