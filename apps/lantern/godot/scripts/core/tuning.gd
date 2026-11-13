class_name Tuning
extends RefCounted
## 1.8 «Скорость игры»: настройки сервера подбираются под компьютер хозяина.
## Три набора: fast «Быстро» / balance «Баланс» / pretty «Красиво». Геймплей почти не меняется:
## режем дальность прорисовки, частоту лишних проверок и работу с далёкими от игроков существами.

const NAMES := [["fast", "Быстро"], ["balance", "Баланс"], ["pretty", "Красиво"]]
const HINTS := {
	"fast": "Мир видно ближе, зато без рывков. Для слабых компьютеров и большой компании.",
	"balance": "Золотая середина — подойдёт почти всем.",
	"pretty": "Мир видно далеко. Только для мощного компьютера.",
}

## Числа наборов. view/sim — дальность прорисовки и «живой» зоны (чанков); ear_* — с какого расстояния существа
## «засыпают»; merge_* — радиус склейки выпавших вещей и опыта; autosave — чанков за тик при автосохранении.
const PRESETS := {
	"fast": {"view": 6, "sim": 4, "ear_animals": 16, "ear_monsters": 24, "ear_misc": 8, "ear_water": 8, "ear_villagers": 16,
		"merge_item": 4.0, "merge_exp": 6.0, "mob_spawn_range": 4, "autosave": 8, "collisions": 2, "redstone": "ALTERNATE_CURRENT",
		"pathfind_on_block": "false", "hopper_check": 8, "chunk_send": 60.0, "chunk_load": 80.0, "chunk_gen": 20.0,
		"monsters": 40, "monster_ticks": 4, "broadcast_pct": 75, "geyser_zip": 3, "bds_view": 8, "bds_tick": 4},
	"balance": {"view": 8, "sim": 6, "ear_animals": 24, "ear_monsters": 28, "ear_misc": 12, "ear_water": 12, "ear_villagers": 24,
		"merge_item": 3.5, "merge_exp": 4.0, "mob_spawn_range": 6, "autosave": 12, "collisions": 4, "redstone": "ALTERNATE_CURRENT",
		"pathfind_on_block": "false", "hopper_check": 4, "chunk_send": 75.0, "chunk_load": 100.0, "chunk_gen": 40.0,
		"monsters": 56, "monster_ticks": 2, "broadcast_pct": 100, "geyser_zip": 5, "bds_view": 12, "bds_tick": 4},
	"pretty": {"view": 12, "sim": 8, "ear_animals": 32, "ear_monsters": 32, "ear_misc": 16, "ear_water": 16, "ear_villagers": 32,
		"merge_item": 2.5, "merge_exp": 3.0, "mob_spawn_range": 8, "autosave": 24, "collisions": 8, "redstone": "VANILLA",
		"pathfind_on_block": "true", "hopper_check": 1, "chunk_send": 75.0, "chunk_load": 100.0, "chunk_gen": -1.0,
		"monsters": 70, "monster_ticks": 1, "broadcast_pct": 100, "geyser_zip": 6, "bds_view": 20, "bds_tick": 6},
}


static func name_of(preset: String) -> String:
	for n in NAMES:
		if n[0] == preset:
			return n[1]
	return L.t("Баланс")


## Что знаем о компьютере: потоки процессора, вся и свободная память (МБ).
static func machine() -> Dictionary:
	var m := OS.get_memory_info()
	var total := int(int(m.get("physical", 0)) / (1 << 20))
	var free := int(int(m.get("available", -1)) / (1 << 20)) if int(m.get("available", -1)) > 0 else -1
	return {"threads": OS.get_processor_count(), "total_mb": total, "free_mb": free}


## Слабый компьютер: 4 потока и меньше или свободно меньше 6 ГБ.
static func weak(mc: Dictionary) -> bool:
	return int(mc.threads) <= 4 or (int(mc.free_mb) >= 0 and int(mc.free_mb) < 6144)


## Набор, который реально применится: выбранный хозяином; «Баланс» на слабом компьютере сам становится «Быстро»
## (если хозяин не выбрал набор сам — opts.perf_manual).
static func effective(opts: Dictionary, mc: Dictionary) -> String:
	var p := String(opts.get("perf", "balance"))
	if not PRESETS.has(p):
		p = "balance"
	if p == "balance" and not opts.get("perf_manual", false) and weak(mc):
		return "fast"
	return p


## Память для сервера «авто» (МБ): по всей и свободной памяти; не больше половины всей.
static func auto_memory(mc: Dictionary) -> int:
	var total := int(mc.total_mb)
	var mem := 2048
	if total <= 0:
		return mem
	if total < 6000:
		mem = 1536
	elif total < 8500:
		mem = 2048
	elif total < 12500:
		mem = 3072
	elif total < 17000:
		mem = 4096
	else:
		mem = 6144
	var free := int(mc.free_mb)
	if free > 0:
		mem = mini(mem, maxi(1024, int((free - 1536) / 512) * 512))
	return clampi(mem, 1024, maxi(1024, int(total / 2)))


static func memory(opts: Dictionary, mc: Dictionary) -> int:
	var m := int(opts.get("memory_mb", 0))
	return m if m >= 1024 and not opts.get("memory_auto", true) else auto_memory(mc)


## Флаги Java: сборщик G1 с настройками «Aikar's flags» (короткие паузы — меньше рывков в игре).
## Java 8 — те же флаги (G1 там уже есть). Память не «прогреваем» заранее (AlwaysPreTouch): на этом же
## компьютере обычно работает и сам Minecraft хозяина.
static func jvm_flags(_java_major: int, mem_mb: int) -> PackedStringArray:
	var big := mem_mb >= 12288
	var f := PackedStringArray(["-Xms%dM" % mini(mem_mb, maxi(1024, int(mem_mb / 2))), "-Xmx%dM" % mem_mb,
		"-XX:+UseG1GC", "-XX:+ParallelRefProcEnabled", "-XX:MaxGCPauseMillis=200", "-XX:+UnlockExperimentalVMOptions",
		"-XX:+DisableExplicitGC", "-XX:G1NewSizePercent=%d" % (40 if big else 30), "-XX:G1MaxNewSizePercent=%d" % (50 if big else 40),
		"-XX:G1HeapRegionSize=%dM" % (16 if big else 8), "-XX:G1ReservePercent=%d" % (15 if big else 20), "-XX:G1HeapWastePercent=5",
		"-XX:G1MixedGCCountTarget=4", "-XX:InitiatingHeapOccupancyPercent=%d" % (20 if big else 15),
		"-XX:G1MixedGCLiveThresholdPercent=90", "-XX:G1RSetUpdatingPauseTimePercent=5", "-XX:SurvivorRatio=32",
		"-XX:+PerfDisableSharedMem", "-XX:MaxTenuringThreshold=1"])
	return f


## Ключи server.properties (Paper).
static func java_properties(preset: String) -> Dictionary:
	var p: Dictionary = PRESETS[preset]
	return {"view-distance": str(p.view), "simulation-distance": str(p.sim), "network-compression-threshold": "256",
		"sync-chunk-writes": "false", "entity-broadcast-range-percentage": str(p.broadcast_pct), "enable-jmx-monitoring": "false"}


## Ключи server.properties (Bedrock Dedicated Server). Движение игроков (server-authoritative-movement) не трогаем.
static func bedrock_properties(preset: String, threads: int) -> Dictionary:
	var p: Dictionary = PRESETS[preset]
	return {"view-distance": str(p.bds_view), "tick-distance": str(p.bds_tick), "compression-threshold": "256",
		"max-threads": str(clampi(threads, 2, 8))}


## Поменять значение ключа в YAML по пути (["world-settings", "default", "merge-radius", "item"]). Файл не разбираем:
## ищем ключи по порядку вложенности. Нет такого ключа — текст не меняется (сервер старой версии).
static func yaml_set(txt: String, path: Array, value: String) -> String:
	var pos := 0
	for i in path.size():
		var re := RegEx.create_from_string("(?m)^([ \\t]*)" + String(path[i]).replace(".", "\\.") + ":[ \\t]*(.*)$")
		var m := re.search(txt, pos)
		if m == null:
			return txt
		if i == path.size() - 1:
			return txt.substr(0, m.get_start(2)) + value + txt.substr(m.get_end(2))
		pos = m.get_end()
	return txt


static func _edit(path: String, sets: Array) -> int:
	if not FileAccess.file_exists(path):
		return 0
	var txt := FileAccess.get_file_as_string(path)
	var out := txt
	for s in sets:
		out = yaml_set(out, s[0], str(s[1]))
	if out != txt:
		FileAccess.open(path, FileAccess.WRITE).store_string(out)
		return 1
	return 0


## Файлы настроек Paper/Spigot/Bukkit/Geyser в папке сервера. Их создаёт сам сервер при первом запуске,
## поэтому тонкая настройка действует со второго запуска (дальность и флаги Java — с первого). Возвращает число изменённых файлов.
static func apply_java(dir: String, preset: String) -> int:
	var p: Dictionary = PRESETS[preset]
	var n := 0
	n += _edit(dir.path_join("config/paper-world-defaults.yml"), [
		[["chunks", "max-auto-save-chunks-per-tick"], p.autosave],
		[["chunks", "prevent-moving-into-unloaded-chunks"], "true"],
		[["collisions", "max-entity-collisions"], p.collisions],
		[["environment", "optimize-explosions"], "true"],
		[["misc", "redstone-implementation"], p.redstone],
		[["misc", "update-pathfinding-on-block-update"], p.pathfind_on_block]])
	n += _edit(dir.path_join("config/paper-global.yml"), [
		[["chunk-loading-basic", "player-max-chunk-generate-rate"], p.chunk_gen],
		[["chunk-loading-basic", "player-max-chunk-load-rate"], p.chunk_load],
		[["chunk-loading-basic", "player-max-chunk-send-rate"], p.chunk_send]])
	n += _edit(dir.path_join("spigot.yml"), [
		[["world-settings", "default", "entity-activation-range", "animals"], p.ear_animals],
		[["world-settings", "default", "entity-activation-range", "monsters"], p.ear_monsters],
		[["world-settings", "default", "entity-activation-range", "misc"], p.ear_misc],
		[["world-settings", "default", "entity-activation-range", "water"], p.ear_water],
		[["world-settings", "default", "entity-activation-range", "villagers"], p.ear_villagers],
		[["world-settings", "default", "merge-radius", "item"], p.merge_item],
		[["world-settings", "default", "merge-radius", "exp"], p.merge_exp],
		[["world-settings", "default", "mob-spawn-range"], p.mob_spawn_range],
		[["world-settings", "default", "ticks-per", "hopper-check"], p.hopper_check]])
	n += _edit(dir.path_join("bukkit.yml"), [
		[["spawn-limits", "monsters"], p.monsters],
		[["ticks-per", "monster-spawns"], p.monster_ticks],
		[["settings", "query-plugins"], "false"]])
	# статистика bStats и проверки обновлений плагинов — лишняя работа при запуске
	n += _edit(dir.path_join("plugins/bStats/config.yml"), [[["enabled"], "false"]])
	n += _edit(dir.path_join("plugins/ViaVersion/config.yml"), [[["checkforupdates"], "false"]])
	n += _edit(dir.path_join("plugins/Geyser-Spigot/config.yml"), [[["compression-level"], p.geyser_zip], [["mtu"], 1400]])
	return n


## Строка «TPS from last 1m, 5m, 15m: 20.0, 19.8, 20.0» (Paper) → TPS за минуту; -1 — это не она.
static func parse_tps(line: String) -> float:
	var i := line.find("TPS from last")
	if i < 0:
		return -1.0
	var m := RegEx.create_from_string(":\\s*\\*?([0-9]+(?:\\.[0-9]+)?)").search(line, i)
	return float(m.get_string(1)) if m else -1.0


## Строка «◴ 2.8/0.8/47.5, …» после «Server tick times» → среднее время тика (мс) за 5 с; -1 — не она.
static func parse_mspt(line: String) -> float:
	var m := RegEx.create_from_string("([0-9]+(?:\\.[0-9]+)?)/[0-9.]+/[0-9.]+,").search(line)
	return float(m.get_string(1)) if m else -1.0
