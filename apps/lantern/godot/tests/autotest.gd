extends Node
## Автопроверки LANTERN: запуск с `-- --autotest <режим> [--key=value ...]`.
##   core     — QR, коды, приглашения, server.properties (без сети)
##   screens  — скриншоты всех экранов (--shots=папка)
##   java     — Paper(+Geyser) → relay → прокси друга; ждёт внешний клиент (файл ready/done)
##   bedrock  — Bedrock Dedicated Server → relay → прокси друга; то же
##   proc     — запуск процессов в папке с пробелами/скобками/&, чтение вывода, принудительная остановка (Windows: cmd/taskkill)
##   syscheck — экран «Проверка системы» (+скриншот)
##   phone    — экраны в портретном телефоне (--shots, окно 412×892), нужен relay и social_seed.js
##   ownworld — «Мой мир из Minecraft»: поддельный мир (tests/fake_world.js) → поиск → комната → друг получает данные (tests/ownworld.sh)
##   ownreal  — то же с настоящим BDS вместо «мира в игре» и настоящим клиентом (tests/e2e_client.js bedrock)
##   mini     — ПК: окошко поверх игры — настоящее окно системы (tests/mini_window.sh смотрит его через xwininfo/xprop)
##   tour     — обход ВСЕХ экранов и сценариев для дизайн-скриншотов (tests/design_shots.sh), нужен relay и social_seed.js
##   deadbuttons — нажимает КАЖДУЮ видимую кнопку/карточку на каждом экране и проверяет, что что-то произошло
##              (tests/deadbuttons.sh; список «мёртвых» — в <data>/dead.json)
##   bridge   — только мосты: хозяин → relay → друг поверх эхо-сервера (tests/latency.sh меряет задержку и скорость)
## Код выхода 0 — успех.

var app
var shots := ""
var data := ""


func _log(t: String) -> void:
	print("[autotest] ", t)


func run(a) -> void:
	app = a
	shots = Cfg.autotest_args.get("shots", "")
	data = Cfg.data_dir()
	OS.low_processor_usage_mode = false
	await get_tree().process_frame
	var code := 1
	match Cfg.autotest:
		"core": code = await _core()
		"screens": code = await _screens()
		"social": code = await _social()
		"proc": code = await _proc()
		"syscheck": code = await _syscheck()
		"phone": code = await _phone()
		"touch": code = await _touch()
		"tour": code = await _tour()
		"mini": code = await _mini()
		"bridge": code = await _bridge()
		"deadbuttons": code = await _deadbuttons()
		"ownworld": code = await _ownworld()
		"ownreal": code = await _ownreal()
		"java", "bedrock": code = await _host(Cfg.autotest)
		_: _log("неизвестный режим")
	_log("EXIT %d" % code)
	app.relay_join.stop()
	app.own.stop()
	app.relay_host.stop()
	get_tree().quit(code)


func _shot(name: String) -> void:
	if Cfg.phone_preview():
		_overflow(app._content, get_viewport().get_visible_rect().size.x, name)
	if shots == "" or DisplayServer.get_name() == "headless":
		return
	await get_tree().create_timer(0.8).timeout
	for i in 4:
		await get_tree().process_frame
	DirAccess.make_dir_recursive_absolute(shots)
	var p := shots.path_join(name + ".png")
	get_viewport().get_texture().get_image().save_png(p)
	_log("скриншот " + p)


# ------------------------------------------------------------------ core
## 1.5.1: Java для версии сервера, выбор ViaVersion, диапазон версий, разбор HTTP-входа Bedrock и подмена SDP.
func _core_versions() -> int:
	var fails := 0
	var chk := func(name: String, got: Variant, want: Variant) -> int:
		var ok: bool = got == want
		_log(("ок: " if ok else "ОШИБКА: ") + "%s = %s%s" % [name, str(got), "" if ok else " (ждали %s)" % str(want)])
		return 0 if ok else 1
	fails += chk.call("java 1.8.8", ServerHost.java_for("1.8.8", 8), 8)
	fails += chk.call("java 1.12.2", ServerHost.java_for("1.12.2", 8), 8)
	fails += chk.call("java 1.16.5", ServerHost.java_for("1.16.5", 8), 11)
	fails += chk.call("java 1.18.2", ServerHost.java_for("1.18.2", 17), 17)
	fails += chk.call("java 1.20.1", ServerHost.java_for("1.20.1", 17), 17)
	fails += chk.call("java 1.20.6", ServerHost.java_for("1.20.6", 21), 21)
	fails += chk.call("java 26.3", ServerHost.java_for("26.3", 25), 25)
	var list := [
		{"version_number": "5.13.0-SNAPSHOT+9", "version_type": "beta", "game_versions": ["1.8", "26.4"], "files": [{"primary": true, "url": "https://cdn.modrinth.com/b.jar", "hashes": {"sha1": "bb"}}]},
		{"version_number": "5.12.2", "version_type": "release", "game_versions": ["1.8", "26.1.2", "26.3", "26.3-rc-1"], "files": [{"primary": true, "url": "https://cdn.modrinth.com/a.jar", "hashes": {"sha1": "aa"}}]},
		{"version_number": "4.10.2", "version_type": "release", "game_versions": ["1.8", "1.20.6", "1.9.4"], "files": [{"primary": true, "url": "https://cdn.modrinth.com/c.jar", "hashes": {"sha1": "cc"}}]},
	]
	fails += chk.call("via релиз", ServerHost.pick_via(list, "viaversion", 25).get("version"), "5.12.2")
	fails += chk.call("via для Java 8", ServerHost.pick_via(list, "viaversion", 8).get("version"), "4.10.2")
	fails += chk.call("via только бета", ServerHost.pick_via([list[0]], "viaversion", 25).get("version"), "5.13.0-SNAPSHOT+9")
	var via := {"viaversion": ServerHost.pick_via(list, "viaversion", 25), "viabackwards": {}, "viarewind": {}}
	fails += chk.call("диапазон с Via", ServerHost.java_range("26.3", via), ["1.8", "26.3"])
	fails += chk.call("диапазон без Via", ServerHost.java_range("1.20.1", {}), ["1.20.1", "1.20.1"])
	fails += chk.call("сравнение версий", ServerHost.ver_cmp("1.21.11", "26.3"), -1)
	# HTTP-вход Bedrock: разбор запроса и ответа, подмена кандидатов в SDP на сокет моста
	var rq := NetherNet.http_parse("POST /v1/join/123 HTTP/1.1\r\nHost: x\r\nContent-Length: 5\r\n\r\nv=0\r\n".to_utf8_buffer())
	fails += chk.call("HTTP запрос", [rq.get("line"), rq.get("headers", {}).get("host"), (rq.get("body", PackedByteArray()) as PackedByteArray).get_string_from_utf8()], ["POST /v1/join/123 HTTP/1.1", "x", "v=0\r\n"])
	fails += chk.call("HTTP неполный", NetherNet.http_parse("POST / HTTP/1.1\r\nContent-Length: 9\r\n\r\nab".to_utf8_buffer()), {})
	var ch := NetherNet.http_parse("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n3\r\nabc\r\n2\r\nde\r\n0\r\n\r\n".to_utf8_buffer())
	fails += chk.call("HTTP chunked", (ch.get("body", PackedByteArray()) as PackedByteArray).get_string_from_utf8(), "abcde")
	var sess := NetherNet.new_session("123")
	var sdp := "v=0\r\na=ice-ufrag:x\r\na=candidate:1 1 udp 2130706431 192.168.1.20 19134 typ host\r\na=candidate:2 1 udp 1694498815 203.0.113.5 19134 typ srflx raddr 0.0.0.0 rport 0\r\n"
	var out := NetherNet.http_sdp_remote(sess, "CONNECTRESPONSE", sdp)
	fails += chk.call("SDP: чужие кандидаты заменены", [out.contains("192.168.1.20"), out.contains("203.0.113.5"), out.contains("127.0.0.1 %d typ host" % sess.u.get_local_port()), out.contains("ice-ufrag:x")], [false, false, true, true])
	NetherNet.http_sdp_local(sess, "CONNECTREQUEST", sdp)
	fails += chk.call("SDP: свои кандидаты запомнены", sess.cands, [["192.168.1.20", 19134]])
	NetherNet.close_session(sess)
	var jv := preload("res://scripts/screens/join.gd")
	fails += chk.call("версии Bedrock в подсказке", jv.version_text({"host_version": "1.21.120", "host_protocol": 859, "client_protocol": 844})[1].begins_with("У хозяина 1.21.120, у вас 1.21.110 — обновите Minecraft"), true)
	return fails


func _core() -> int:
	Cfg.set_value("host", "rules", {})      # прошлый прогон в этой же папке данных не влияет
	var fails := 0
	var out := Cfg.autotest_args.get("out", "/tmp/lantern-core")
	DirAccess.make_dir_recursive_absolute(out)
	var texts := ["HELLO", "minecraft://?addExternalServer=Мир Yompix|abc.example.net:12345",
		Invite.link("K7M2QX", "both", "Мир Yompix", "ws://127.0.0.1:1/ws"), "x".repeat(300)]
	for i in texts.size():
		for ecc in [QR.ECC_L, QR.ECC_M, QR.ECC_Q, QR.ECC_H]:
			var q := QR.encode(texts[i], ecc)
			q.to_image(4).save_png("%s/qr_%d_%d.png" % [out, i, ecc])
			var f := FileAccess.open("%s/qr_%d_%d.txt" % [out, i, ecc], FileAccess.WRITE)
			f.store_string(texts[i])
	var cases := [
		["k7m-2qx", {"kind": "room", "code": "K7M2QX"}],
		[" K7M 2QX ", {"kind": "room", "code": "K7M2QX"}],
		["K0M2QX", {"ok": false}],
		[Invite.link("ABC234", "bedrock", "Мир", ""), {"kind": "room", "code": "ABC234", "edition": "bedrock", "name": "Мир"}],
		["lantern://join?v=9&c=ABC234", {"ok": false}],
		["play.example.com", {"kind": "address", "address": "play.example.com:25565"}],
		["steve", {"kind": "room", "code": "STEVE"}],
		["lantern://join?v=1&c=YOMP2X&e=java", {"kind": "room", "code": "YOMP2X", "edition": "java"}],
		["ab", {"ok": false}],
		["2345", {"ok": false}],
		["minecraft://?addExternalServer=Мир|a.b.c:1234", {"kind": "address", "address": "a.b.c:1234", "edition": "bedrock"}],
	]
	fails += _core_versions()
	for c in cases:
		var r := Invite.parse(c[0], "java")
		var ok := true
		for k in c[1]:
			if r.get(k) != c[1][k]:
				ok = false
		_log("parse %s → %s %s" % [c[0], "OK" if ok else "FAIL", "" if ok else str(r)])
		if not ok:
			fails += 1
	for wl in [["[12:01:02 INFO]: Disconnecting Steve (/127.0.0.1:5555): You are not whitelisted on this server!", "Steve"],
			["[12:01:02 INFO]: Alex_2 (/127.0.0.1:5555) lost connection: You are not white-listed on this server!", "Alex_2"],
			["[12:01:02] [Server thread/INFO]: Disconnecting com.mojang.authlib.GameProfile@1[id=<null>,name=Masha,properties={}] (/1.2.3.4:5): You are not whitelisted on this server!", "Masha"],
			["[12:01:02 INFO]: Steve joined the game", ""]]:
		var got := ServerHost.whitelist_kicked(wl[0])
		_log("whitelist %s → %s %s" % [wl[1], got, "OK" if got == wl[1] else "FAIL"])
		if got != wl[1]:
			fails += 1
	# «доктор» сервера: строки журнала → правило
	var doc := [
		["[12:01:02 INFO]: Disconnecting Steve (/127.0.0.1:5555): You are not whitelisted on this server!", "whitelist", "Steve", ""],
		["[12:01:02 INFO]: Disconnecting com.mojang.authlib.GameProfile@1[id=<null>,name=Masha,properties={}] (/1.2.3.4:5): Failed to verify username!", "auth", "Masha", ""],
		["[12:01:02 INFO]: Alex (/1.2.3.4:5) lost connection: Outdated client! Please use 1.21.4", "outdated_client", "Alex", "1.21.4"],
		["[12:01:02 INFO]: Disconnecting Alex (/1.2.3.4:5): Outdated server! I'm still on 1.21.4", "outdated_server", "Alex", "1.21.4"],
		["[12:01:02 INFO]: Disconnecting Bob (/1.2.3.4:5): You are banned from this server.", "banned", "Bob", ""],
		["[12:01:02 INFO]: Disconnecting Kate (/1.2.3.4:5): The server is full!", "full", "Kate", ""],
		["[12:01:02 WARN]: **** FAILED TO BIND TO PORT!", "port", "", ""],
		["[2026-11-10 01:20:15:537 ERROR] Network port [19132] may be in use.", "port", "", ""],
		["[12:01:02] [Server thread/INFO]: Steve joined the game", "", "", ""],
	]
	for c in doc:
		var d := Doctor.diagnose(c[0])
		var ok: bool = String(d.get("rule", "")) == c[1] and String(d.get("nick", "")) == c[2] and String(d.get("version", "")) == c[3]
		_log("doctor %s → %s %s" % [c[1], JSON.stringify(d), "OK" if ok else "FAIL"])
		if not ok:
			fails += 1
	var sh := ServerHost.new()
	add_child(sh)
	sh.edition = "java"
	sh.opts = {"online_mode": true, "max_players": 10}
	for c in doc:
		sh._on_line(c[0])
	var ids: Array = sh.issues.map(func(i): return i.id)
	_log("доктор: issues=%s fixes=%s" % [JSON.stringify(ids), JSON.stringify(sh.fixes)])
	for want in ["auth", "ver:Alex", "ban:Bob", "full"]:
		if not want in ids:
			_log("FAIL: нет подсказки " + want)
			fails += 1
	if sh.fixes.is_empty() or not String(sh.fixes[-1]).contains("Steve") or not sh._port_conflict:
		_log("FAIL: белый список/порт не обработаны")
		fails += 1
	sh.opts["whitelist"] = true
	sh._on_line(doc[0][0])
	if not sh.issues.any(func(i): return i.id == "wl:Steve"):
		_log("FAIL: белый список включён хозяином — нужна кнопка «Впустить»")
		fails += 1
	sh.resolve("ban:Bob")
	if sh.issues.any(func(i): return i.id == "ban:Bob") or not String(sh.fixes[0]).contains("Bob"):
		fails += 1
	sh.queue_free()
	for c in [[true, 0, 0, 1000, "port"], [true, 3, 0, 1000, "fail"], [false, 0, 0, 120000, "restart"], [false, 0, 1, 120000, "fail"], [false, 0, 0, 5000, "fail"]]:
		var plan := Doctor.exit_plan(c[0], c[1], c[2], c[3])
		if plan != c[4]:
			_log("exit_plan FAIL %s → %s" % [str(c), plan])
			fails += 1
	if Doctor.memory_for(8192, 4096) != 2048 or Doctor.memory_for(2048, 16384) != 2048 or Doctor.memory_for(3072, 0) != 3072:
		_log("memory_for FAIL")
		fails += 1
	var busy := TCPServer.new()
	busy.listen(25611, "0.0.0.0")
	var fp := Doctor.free_port(25611, false)
	busy.stop()
	var ub := PacketPeerUDP.new()
	ub.bind(19611, "0.0.0.0")
	var fu := Doctor.free_port(19611, true, [19612])
	ub.close()
	_log("свободные порты: TCP %d, UDP %d" % [fp, fu])
	if fp != 25612 or fu != 19613:
		fails += 1
	ServerHost.write_properties(out + "/server.properties", {"motd": "Мир Yompix", "max-players": "5"})
	ServerHost.write_properties(out + "/server.properties", {"max-players": "7", "online-mode": "false"})
	var props := ServerHost.read_properties(out + "/server.properties")
	if props.get("max-players") != "7" or props.get("motd") != "\\u041c\\u0438\\u0440 Yompix":
		_log("properties FAIL " + str(props))
		fails += 1
	fails += _core_16(out)
	fails += _core_17()
	fails += _core_18()
	_log("FAILS=%d" % fails)
	return 1 if fails else 0


## 1.6: журнал и отчёт без личного, Geyser с Floodgate, Via не пускает 1.7, поток с возобновлением.
func _core_16(out: String) -> int:
	var fails := 0
	var dirty := "Authorization: XBL3.0 x=123;eyJabc password=кот token=\"abc\" a=ice-pwd:SECRET друг 95.31.2.7 лан 192.168.1.5 версия 1.21.130.1"
	var clean := AppLog.scrub(dirty)
	_log("scrub: " + clean)
	for bad in ["x=123", "кот", "SECRET", "95.31.2.7", "\"abc\""]:
		if clean.contains(bad):
			_log("scrub FAIL: осталось " + bad)
			fails += 1
	if not clean.contains("192.168.1.5") or not clean.contains("1.21.130.1"):
		_log("scrub FAIL: вырезано лишнее")
		fails += 1
	AppLog.add("test", "проверка отчёта token=zzz")
	var rep := Report.collect(app, "test", "заметка")
	if not String(rep.app_log).contains("проверка отчёта") or String(rep.app_log).contains("zzz") or String(rep.app).find(Cfg.APP_VERSION) < 0:
		_log("report FAIL")
		fails += 1
	_log("отчёт: %d байт журнала, устройство %s" % [String(rep.app_log).length(), rep.device])
	# Geyser: короткий файл до первого запуска и полный с auth-type: online → floodgate
	var gc := out + "/geyser/config.yml"
	DirAccess.remove_absolute(gc)
	ServerHost._geyser_config(gc, true)
	var g1 := FileAccess.get_file_as_string(gc)
	FileAccess.open(gc, FileAccess.WRITE).store_string("bedrock:\n  port: 19132\n  transport: nethernet\njava:\n  # c\n  auth-type: online\nmotd:\n  x: 1\n")
	ServerHost._geyser_config(gc, true)
	var g2 := FileAccess.get_file_as_string(gc)
	if not g1.contains("auth-type: floodgate") or not g2.contains("auth-type: floodgate") or g2.contains("auth-type: online") or not g2.contains("transport: both"):
		_log("geyser config FAIL:\n" + g1 + "\n---\n" + g2)
		fails += 1
	DirAccess.make_dir_recursive_absolute(out + "/via")
	DirAccess.remove_absolute(out + "/via/ViaVersion/config.yml")
	ServerHost._via_config(out + "/via")
	var v1 := FileAccess.get_file_as_string(out + "/via/ViaVersion/config.yml")
	FileAccess.open(out + "/via/ViaVersion/config.yml", FileAccess.WRITE).store_string("a: 1\nblock-versions: []\nblock-disconnect-msg: You are using an unsupported Minecraft version!\nb: 2\n")
	ServerHost._via_config(out + "/via")
	var v2 := FileAccess.get_file_as_string(out + "/via/ViaVersion/config.yml")
	if not v1.contains("\"<1.8\"") or not v2.contains("block-versions: [\"<1.8\"]") or not v2.contains("1.8.9") or not v2.contains("b: 2"):
		_log("via config FAIL:\n" + v1 + "\n---\n" + v2)
		fails += 1
	# поток: отправили 10 байт, подтверждено 4, на той стороне 6 → досылаем 4 последних
	var s := {}
	RelStream.init(s, true)
	RelStream.on_sent(s, "0123456789".to_utf8_buffer())
	RelStream.on_ack(s, 4)
	if s.buf.get_string_from_utf8() != "456789":
		_log("relstream ack FAIL " + s.buf.get_string_from_utf8())
		fails += 1
	var fake := RelayLink.new()
	if not RelStream.replay(fake, 1, s, 6) or s.buf.get_string_from_utf8() != "6789" or RelStream.replay(fake, 1, s, 3):
		_log("relstream replay FAIL")
		fails += 1
	var opts: Dictionary = load("res://scripts/screens/create.gd").universal_opts("Мир теста", "creative", 5, true)
	if not opts.crossplay or opts.version != "latest" or opts.gamemode != "creative" or not opts.any_version:
		_log("universal opts FAIL " + str(opts))
		fails += 1
	_log("1.6 core: FAILS=%d" % fails)
	return fails


## 1.7: одна ссылка https://…/lantern/j/<код>, ссылка «открыть Minecraft и зайти», адрес Java, защита мира, шаги для Xiaomi и др.
func _core_17() -> int:
	var fails := 0
	var checks := [
		[Invite.web_link("K7M2QX", "both", "Мир", Cfg.DEFAULT_RELAY), Cfg.SITE + "/j/K7M2QX"],
		[Invite.web_code("https://office-dreams-production.up.railway.app/lantern/j/k7m-2qx"), "K7M2QX"],
		[Invite.web_code("https://office-dreams-production.up.railway.app/lantern/j/35821626?x=1"), "35821626"],
		[Invite.web_code("https://example.com/other/j/K7M2QX"), ""],
		[Invite.web_code("https://office-dreams-production.up.railway.app/lantern/j/<script>"), ""],
		[String(Invite.parse("https://office-dreams-production.up.railway.app/lantern/j/K7M2QX").get("code", "")), "K7M2QX"],
		[String(Invite.parse("lantern://join?code=K7M2QX").get("code", "")), "K7M2QX"],
		[String(Invite.parse("lantern://join?v=1&c=K7M2QX&e=both&n=%D0%9C%D0%B8%D1%80").get("code", "")), "K7M2QX"],
		[String(DeepLink.parse("lantern://join?code=35821626").get("code", "")), "35821626"],
		[String(DeepLink.parse("https://office-dreams-production.up.railway.app/lantern/j/K7M2QX").get("kind", "")), "join"],
		[DeepLink.friend_web_link("Алекс#4821"), "https://office-dreams-production.up.railway.app/lantern/f/%D0%90%D0%BB%D0%B5%D0%BA%D1%81%234821"],
		[String(DeepLink.parse(DeepLink.friend_web_link("Алекс#4821")).get("tag", "")), "Алекс#4821"],
		[String(DeepLink.parse("https://office-dreams-production.up.railway.app/lantern/f/Alex%231234?x=1").get("kind", "")), "friend"],
		[String(DeepLink.parse("https://example.com/lantern/f/bad").get("kind", "")), ""],
		[Launcher.connect_url(19132), "minecraft://connect/?serverUrl=127.0.0.1&serverPort=19132"],
		[Launcher.java_address(25565), "localhost"],
		[Launcher.java_address(25570), "localhost:25570"],
		[RoomControl.vendor_kind("Xiaomi Redmi Note 12"), "xiaomi"],
		[RoomControl.vendor_kind("POCO X5"), "xiaomi"],
		[RoomControl.vendor_kind("HUAWEI P30"), "huawei"],
		[RoomControl.vendor_kind("samsung SM-A515F"), "samsung"],
		[RoomControl.vendor_kind("Google Pixel 8"), ""],
	]
	for c in checks:
		if String(c[0]) != String(c[1]):
			_log("1.7 FAIL: «%s» вместо «%s»" % [c[0], c[1]])
			fails += 1
	# открытие Minecraft в автотесте ничего не запускает, но запоминает ссылку
	Launcher.open_bedrock(19142, "тест")
	if Launcher.last_url != "minecraft://connect/?serverUrl=127.0.0.1&serverPort=19142":
		_log("1.7 FAIL: ссылка запуска " + Launcher.last_url)
		fails += 1
	# поделиться игрой — ссылкой https (её видно в мессенджерах), а не lantern://
	var g := Cfg.game()
	if int(g.cap) < 2:
		fails += 1
	_log("1.7 core: FAILS=%d" % fails)
	return fails


## 1.8: правила мира (команды Paper старых/новых версий и BDS), скорость игры, прямое соединение по Wi-Fi, множественное число.
func _core_18() -> int:
	var fails := 0
	var J := func(a: PackedStringArray) -> String: return " | ".join(a)
	var checks := [
		[J.call(GameRules.commands("java", "pvp", false, {"mc": "26.3"})), "gamerule pvp false"],
		[J.call(GameRules.commands("java", "keep", true, {"mc": "26.3"})), "gamerule keep_inventory true"],
		[J.call(GameRules.commands("java", "keep", true, {"mc": "1.20.1"})), "gamerule keepInventory true"],
		[J.call(GameRules.commands("java", "fire", false, {"mc": "1.21.11"})), "gamerule fire_spread_radius_around_player 0"],
		[J.call(GameRules.commands("java", "fire", false, {"mc": "1.21.10"})), "gamerule doFireTick false"],
		[J.call(GameRules.commands("java", "explosions", false, {"mc": "26.3"})), "gamerule tnt_explodes false | gamerule mob_griefing false"],
		[J.call(GameRules.commands("java", "monsters", false, {"mc": "26.3"})), "gamerule spawn_monsters false"],
		[J.call(GameRules.commands("java", "monsters", false, {"mc": "1.16.5"})), "gamerule doMobSpawning false"],
		[J.call(GameRules.commands("java", "daylight", false, {"mc": "26.3"})), "gamerule advance_time false"],
		[J.call(GameRules.commands("java", "weather", false, {"mc": "1.12.2"})), "gamerule doWeatherCycle false | weather clear"],
		[J.call(GameRules.commands("java", "difficulty", "hard")), "difficulty hard"],
		[J.call(GameRules.commands("java", "gamemode", "creative")), "defaultgamemode creative | gamemode creative @a"],
		[J.call(GameRules.commands("java", "whitelist", true)), "whitelist on"],
		[J.call(GameRules.commands("java", "wl_add", "Luna_7")), "whitelist add Luna_7"],
		[J.call(GameRules.commands("bedrock", "wl_add", "Luna 7")), 'allowlist add "Luna 7"'],
		[J.call(GameRules.commands("bedrock", "whitelist", false)), "allowlist off"],
		[J.call(GameRules.commands("bedrock", "keep", true)), "gamerule keepinventory true"],
		[J.call(GameRules.commands("bedrock", "fire", false)), "gamerule dofiretick false"],
		[J.call(GameRules.commands("bedrock", "cheats", true)), "changesetting allow-cheats true"],
		[J.call(GameRules.commands("java", "cheats", true, {"players": ["A", "B"]})), "op A | op B"],
		[J.call(GameRules.commands("java", "cheats", false, {"players": ["A", "B"], "admins": ["B"], "host": "C"})), "deop A"],
		[J.call(GameRules.commands("java", "admin_add", "Luna_7")), "op Luna_7"],
		[J.call(GameRules.commands("java", "pardon", "Luna_7")), "pardon Luna_7"],
		[GameRules.clean_nick("  Lu;na/7 && stop "), "Luna7  stop"],
		[str(GameRules.from_protect({"pvp": true, "boom": true, "keep": true})), str({"pvp": false, "explosions": false, "keep": true})],
		[Tuning.effective({"perf": "balance"}, {"threads": 4, "total_mb": 16000, "free_mb": 12000}), "fast"],
		[Tuning.effective({"perf": "balance"}, {"threads": 8, "total_mb": 16000, "free_mb": 5000}), "fast"],
		[Tuning.effective({"perf": "balance"}, {"threads": 8, "total_mb": 16000, "free_mb": 9000}), "balance"],
		[Tuning.effective({"perf": "pretty", "perf_manual": true}, {"threads": 2, "total_mb": 4000, "free_mb": 1000}), "pretty"],
		[str(Tuning.auto_memory({"threads": 8, "total_mb": 16000, "free_mb": 12000})), "4096"],
		[str(Tuning.auto_memory({"threads": 4, "total_mb": 8000, "free_mb": 3000})), "1024"],
		[str(Tuning.auto_memory({"threads": 4, "total_mb": 4000, "free_mb": 3500})), "1536"],
		[str(Tuning.parse_tps("[00:48:04 INFO]: TPS from last 1m, 5m, 15m: 20.1, 19.9, 20.0")), "20.1"],
		[str(Tuning.parse_tps("[00:48:04 INFO]: TPS from last 1m, 5m, 15m: *12.5, 19.9, 20.0")), "12.5"],
		[str(Tuning.parse_tps("[00:48:04 INFO]: Done (21.9s)!")), "-1.0"],
		[str(Tuning.parse_mspt("[00:48:04 INFO]: ? 2.8/0.8/47.5, 2.8/0.8/47.5, 2.8/0.8/47.5")), "2.8"],
		[Tuning.yaml_set("a:\n  b:\n    item: 0.5\n    exp: -1.0\nitem: 9\n", ["a", "b", "item"], "4.0"), "a:\n  b:\n    item: 4.0\n    exp: -1.0\nitem: 9\n"],
		[Tuning.yaml_set("a:\n  x: 1\n", ["a", "nope"], "4"), "a:\n  x: 1\n"],
		[str(L.plural_index("ru", 1)) + str(L.plural_index("ru", 3)) + str(L.plural_index("ru", 11)) + str(L.plural_index("ru", 21)), "0120"],
		[str(L.plural_index("pl", 1)) + str(L.plural_index("pl", 22)) + str(L.plural_index("pl", 5)), "012"],
		[str(L.plural_index("en", 1)) + str(L.plural_index("en", 0)) + str(L.plural_index("fr", 0)) + str(L.plural_index("tr", 5)), "0100"],
	]
	for c in checks:
		if String(c[0]) != String(c[1]):
			_log("1.8 FAIL: «%s» вместо «%s»" % [c[0], c[1]])
			fails += 1
	var fl := Tuning.jvm_flags(21, 4096)
	if not "-Xmx4096M" in fl or not "-XX:+UseG1GC" in fl or not "-XX:G1HeapRegionSize=8M" in fl or not "-XX:G1HeapRegionSize=16M" in Tuning.jvm_flags(21, 12288):
		_log("1.8 FAIL: флаги Java " + str(fl))
		fails += 1
	for p in Tuning.PRESETS:
		if Tuning.PRESETS[p].size() != Tuning.PRESETS.fast.size() or int(Tuning.PRESETS[p].sim) > int(Tuning.PRESETS[p].view):
			_log("1.8 FAIL: набор " + p)
			fails += 1
	# прямое соединение: ответ только на пробу с нашим ключом, чужой ответ не принимается
	var rng := RandomNumberGenerator.new()
	var nonce := LanDirect.new_nonce(rng)
	var keys := {}
	LanDirect.remember(keys, "0123456789abcdef0123456789abcdef", 1)
	var ans := LanDirect.answer(LanDirect.probe("0123456789abcdef0123456789abcdef", nonce), keys)
	if ans.is_empty() or not LanDirect.check(ans, "0123456789abcdef0123456789abcdef", nonce) or LanDirect.check(ans, "0123456789abcdef0123456789abcdee", nonce) \
			or not LanDirect.answer(LanDirect.probe("ffffffffffffffffffffffffffffffff", nonce), keys).is_empty() or not LanDirect.answer("мусор".to_utf8_buffer(), keys).is_empty():
		_log("1.8 FAIL: проба прямого соединения")
		fails += 1
	for i in 80:
		LanDirect.remember(keys, "%032x" % i, i + 2)
	if keys.size() != LanDirect.KEYS_MAX or keys.has("0123456789abcdef0123456789abcdef"):
		_log("1.8 FAIL: ключей прямого соединения %d" % keys.size())
		fails += 1
	fails += _core_i18n()
	_log("1.8 core: FAILS=%d" % fails)
	return fails


## Переводы: в каждом языке есть каждая строка из i18n/lantern.pot (её обновляет tools/i18n_extract.py),
## перевод не пуст, подстановки (%s, %d) те же, у форм множественного числа «a|b|c» подстановки есть в каждой форме.
func _core_i18n() -> int:
	var fails := 0
	var pot := FileAccess.get_file_as_string("res://i18n/lantern.pot")
	var keys := []
	for m in RegEx.create_from_string("(?m)^msgid (\".*\")$").search_all(pot):
		var k: Variant = JSON.parse_string(m.get_string(1))
		if k is String and k != "":
			keys.append(k)
	var ph := RegEx.create_from_string("%[-+0 ]*\\d*(?:\\.\\d+)?[sdfx]")
	var count := func(t: String) -> String:
		var a := []
		for x in ph.search_all(t):
			a.append(x.get_string().right(1))
		a.sort()
		return "".join(a)
	var langs := 0
	for l in L.LANGS:
		if l[0] == "ru":
			continue
		var path := "res://i18n/%s.po" % l[0]
		if not ResourceLoader.exists(path):
			continue          # язык ещё не переведён — в списке «Язык» его не будет (L.available)
		langs += 1
		var t: Translation = load(path)
		var bad := 0
		for k in keys:
			var v := String(t.get_message(k))
			if v == "" or count.call(String(k).get_slice("|", 0)) != count.call(v.get_slice("|", 0)):
				bad += 1
				if bad <= 5:
					_log("i18n FAIL [%s]: «%s» → «%s»" % [l[0], String(k).substr(0, 60), v.substr(0, 60)])
		if bad > 0:
			fails += 1
		_log("i18n %s: строк %d, с ошибками %d" % [l[0], keys.size(), bad])
	if keys.size() < 1000 or langs < 1:
		_log("i18n FAIL: строк %d, языков %d" % [keys.size(), langs])
		fails += 1
	return fails


# ------------------------------------------------------------------ скриншоты
func _screens() -> int:
	app.go("home", true)
	await _shot("01_home")
	app.go("gamesettings", true)
	await _shot("02_game_settings")
	app.go("join", true, {"edition": "both"})
	await _shot("04_join")
	app.go("settings", true)
	await _shot("05_settings")
	app.go("create", true)
	await _shot("06_create")
	Report.dialog(app, "test")
	await _shot("07_report")
	Overlays.close_sheet(app)
	return 0


# ------------------------------------------------------------------ соцсеть, магазин, сезон (нужен relay и tests/social_seed.js)
func _press(text: String, in_overlay := false) -> bool:
	var root: Node = get_tree().root
	if in_overlay:
		root = app.get_child(app.get_child_count() - 1)
	for b in root.find_children("*", "Button", true, false):
		if b.is_visible_in_tree() and ((b as Button).text == text or (b as Button).text == L.t(text)) and not (b as Button).disabled:
			(b as Button).pressed.emit()
			return true
	_log("кнопка не найдена: " + text)
	return false


func _close_overlays() -> void:
	for c in app.get_children():
		if c is ColorRect and c.mouse_filter == Control.MOUSE_FILTER_STOP:
			c.queue_free()
	await get_tree().process_frame


func _social() -> int:
	if not await _wait(func(): return Social.conn in ["need_register", "online"], 20, "связь с relay"):
		return 1
	if Social.conn == "need_register":
		Overlays.register(app)
		await get_tree().create_timer(0.5).timeout
		await _shot("20_register")
		Social.register("Ночной_Лис")
		await _wait(func(): return Social.online(), 10, "профиль создан")
	await _close_overlays()
	var tag_file := data.path_join("autotest-tag.txt")
	var f := FileAccess.open(tag_file, FileAccess.WRITE)
	f.store_string(Social.me.tag)
	f.close()
	_log("мой тег: " + Social.me.tag)
	await _wait(func(): return Social.incoming.size() >= 3, 30, "пришли 3 заявки в друзья")
	for u in Social.incoming.duplicate():
		Social.request("friend_accept", {"id": u.id})
	await _wait(func(): return Social.friends.size() >= 3, 10, "друзья приняты")
	await _wait(func(): return FileAccess.file_exists(tag_file + ".ok"), 30, "боты готовы")
	await get_tree().create_timer(1.0).timeout
	Social.request("eco")
	app.go("friends", true, {"chat": int(Social.friends[0].id)})
	await get_tree().create_timer(1.0).timeout
	await _shot("21_friends_chat")
	app.go("campfire", true)
	await get_tree().create_timer(1.0).timeout
	await _shot("22_campfire")
	app.go("rooms", true)
	await get_tree().create_timer(1.5).timeout
	await _shot("23_rooms")
	app.go("quests", true)
	await get_tree().create_timer(1.0).timeout
	await _shot("24_quests")
	# реклама за награду
	_press("Смотреть рекламу")
	await get_tree().create_timer(0.6).timeout
	await _shot("25_ad_playing")
	await get_tree().create_timer(2.0).timeout
	await _shot("26_ad_reward")
	await _close_overlays()
	# магазин и покупки (ДЕМО)
	app.go("shop", true)
	await get_tree().create_timer(1.0).timeout
	await _shot("27_shop")
	Overlays.purchase(app, "sparks_1200", func(): Social.request("shop"))
	await get_tree().create_timer(0.5).timeout
	await _shot("28_purchase_sheet")
	_press("Купить", true)
	await get_tree().create_timer(1.6).timeout
	await _shot("29_purchase_success")
	await _close_overlays()
	Social.request("buy", {"item": "flame_emerald"})
	await get_tree().create_timer(0.5).timeout
	Social.request("wear", {"item": "flame_emerald"})
	app.go("shop", true, {"kind": "pro"})
	await get_tree().create_timer(0.6).timeout
	_press("Оформить Pro")
	await get_tree().create_timer(0.4).timeout
	_press("Купить", true)
	await get_tree().create_timer(1.6).timeout
	await _close_overlays()
	app.go("shop", true, {"kind": "pro"})
	await get_tree().create_timer(0.8).timeout
	await _shot("30_pro_active")
	Social.request("demo_purchase", {"product": "lantern_pass_s1"})
	Social.request("eco")
	await get_tree().create_timer(0.5).timeout
	app.go("quests", true, {"tab": "season"})
	Social.request("season")
	await get_tree().create_timer(1.0).timeout
	await _shot("31_season_premium")
	app.go("quests", true, {"tab": "achievements"})
	await get_tree().create_timer(1.0).timeout
	await _shot("32_achievements")
	app.go("profile", true)
	await get_tree().create_timer(1.0).timeout
	await _shot("33_profile")
	app.go("home", true)
	await get_tree().create_timer(0.8).timeout
	await _shot("34_home_online")
	return 0


# ------------------------------------------------------------------ полный цикл
func _wait(cond: Callable, sec: float, what: String) -> bool:
	var t0 := Time.get_ticks_msec()
	while not cond.call():
		if Time.get_ticks_msec() - t0 > sec * 1000:
			_log("ТАЙМАУТ: " + what)
			return false
		await get_tree().create_timer(0.25).timeout
	_log("ок: %s (%.1f с)" % [what, (Time.get_ticks_msec() - t0) / 1000.0])
	return true


func _host(ed: String) -> int:
	var s: ServerHost = app.server
	s.log_line.connect(func(t): print("[server] ", t))
	s.status.connect(func(t): _log("статус: " + t))
	var last_pct := [-10]
	s.progress.connect(func(l, f):
		var pct := int(f * 100)
		if pct >= last_pct[0] + 25 or pct == 100:
			last_pct[0] = pct if pct < 100 else -10
			_log("загрузка: %s %d%%" % [l, pct]))
	var o := {"world": "Тест Yompix", "gamemode": "creative", "difficulty": "easy", "max_players": 8,
		"internet": true, "eula": true}
	if ed == "bedrock":
		o["online_mode"] = false
	if ed == "java":
		o.merge({"version": Cfg.autotest_args.get("mc", "latest"), "online_mode": false, "crossplay": Cfg.autotest_args.get("crossplay", "1") == "1", "memory_mb": 1536})
	s.host(ed, o)
	app.go("host", true, {})
	if not await _wait(func(): return s.state == "running" or s.state == "error", 1500, "сервер запущен (Done)"):
		return 1
	if s.state == "error":
		_log("ошибка сервера: " + s.last_error)
		return 1
	if not await _wait(func(): return app.relay_host.stats().code != "", 30, "комната на relay открыта"):
		return 1
	var code: String = app.relay_host.stats().code
	_log("КОД КОМНАТЫ: " + RoomCode.pretty(code))
	await _shot("10_host_%s" % ed)
	# 1.8: «Настройки игры» на настоящем сервере — команды действительно применяются (ответы сервера в журнале)
	if ed == "java":
		var want := ["Game rule pvp is now set to false", "Game rule keep_inventory is now set to true", "difficulty has been set to Hard",
			"Added Tester1 to the whitelist", "default game mode is now Creative", "Game rule advance_weather is now set to false", "Made Tester1 a server operator"]
		if not GameRules.new_names(String(s._installed.get("mc_version", ""))):
			want = ["keepInventory", "difficulty", "Added Tester1 to the white", "game mode"]
		app.room.rule_set("pvp", false)
		app.room.rule_set("keep", true)
		app.room.rule_set("weather", false)
		app.room.opt_live("difficulty", "hard")
		app.room.opt_live("gamemode", "creative")
		app.room.list_add("wl", "Tester1")
		app.room.list_add("admins", "Tester1")
		var seen := func(): return want.all(func(w): return Array(s.history).any(func(l): return String(l).contains(w)))
		if not await _wait(seen, 15, "сервер подтвердил правила из «Настроек игры»"):
			for w in want:
				_log("  %s: %s" % ["да" if Array(s.history).any(func(l): return String(l).contains(w)) else "НЕТ", w])
			return 1
		app.room.rule_set("pvp", true)
		app.room.rule_set("keep", false)
		app.room.rule_set("weather", true)
		app.room.opt_live("difficulty", "easy")
		app.room.list_remove("wl", "Tester1")
		app.room.list_remove("admins", "Tester1")
		_log("скорость игры: набор %s, память %d МБ, флаги Java: %s" % [s.perf, s.mem_mb, " ".join(Tuning.jvm_flags(21, s.mem_mb).slice(0, 4))])
	# --- друг: вход по коду (тот же процесс, другие локальные порты)
	app.go("join", true, {"input": RoomCode.pretty(code), "edition": "both"})
	if not await _wait(func(): return app.relay_join.stats().state == "online", 20, "друг вошёл в комнату"):
		return 1
	var js: Dictionary = app.relay_join.stats()
	if js.bedrock_port > 0:
		await _wait(func(): return app.relay_join.stats().pong, 20, "сервер ответил на RakNet-пинг через relay")
	await get_tree().create_timer(1.0).timeout
	js = app.relay_join.stats()
	await _shot("11_join_%s" % ed)
	var ready := {"code": code, "edition": js.edition, "java_port": js.java_port, "bedrock_port": js.bedrock_port, "nethernet": js.nethernet, "nn_port": app.relay_join.nn_port, "motd": js.motd, "name": js.name}
	var rf := FileAccess.open(data.path_join("autotest-ready.json"), FileAccess.WRITE)
	rf.store_string(JSON.stringify(ready))
	rf.close()
	_log("READY " + JSON.stringify(ready))
	var done_path := data.path_join("autotest-done.json")
	if FileAccess.file_exists(done_path):
		DirAccess.remove_absolute(done_path)
	var ok := await _wait(func(): return FileAccess.file_exists(done_path), float(Cfg.autotest_args.get("client_timeout", "600")), "внешний клиент закончил")
	var result := {}
	if ok:
		await get_tree().create_timer(0.3).timeout
		result = JSON.parse_string(FileAccess.get_file_as_string(done_path))
		_log("клиент: " + JSON.stringify(result))
	_log("relay host: " + JSON.stringify(app.relay_host.stats()))
	_log("relay join: " + JSON.stringify(app.relay_join.stats()))
	_log("игроки на сервере: " + str(s.players))
	if ed == "java":
		s._tps_t = -100000       # замер TPS сейчас (обычно раз в минуту)
		await _wait(func(): return s.tps >= 0.0, 10, "TPS сервера измерен")
		_log("TPS %.1f, тик %.1f мс, сервер %s" % [s.tps, s.mspt, "успевает" if not s.lagging else "НЕ успевает"])
	await _shot("12_host_after_%s" % ed)
	# --- резервная копия и остановка
	var bk: String = await s.backup()
	_log("резервная копия: %s (%d байт)" % [bk, FileAccess.get_file_as_bytes(bk).size() if bk != "" else 0])
	app.relay_join.stop()
	app.relay_host.stop()
	s.stop()
	if not await _wait(func(): return s.state == "stopped" or s.state == "error", 90, "сервер остановлен"):
		return 1
	if s.state != "stopped":
		return 1
	return 0 if ok and result.get("ok", false) and bk != "" else 1


# ------------------------------------------------------------------ процессы (ПК)
func _proc() -> int:
	var fails := 0
	var cwd := Cfg.sub("LANTERN test (1) & more")
	var p := ManagedProcess.new()
	var lines: PackedStringArray = []
	p.line.connect(func(t): lines.append(t))
	var err := p.start("cmd" if Cfg.is_windows() else "/bin/pwd", PackedStringArray(["/c", "cd"]) if Cfg.is_windows() else PackedStringArray(), cwd)
	if err != "":
		_log("start FAIL " + err)
		return 1
	await _wait(func(): p.poll(); return not p.running, 20, "процесс вывел папку и завершился")
	var joined := "\n".join(lines)
	_log("вывод: " + joined + " код " + str(p.exit_code))
	if not joined.contains("(1) & more"):
		_log("FAIL: неверная папка запуска")
		fails += 1
	var q := ManagedProcess.new()
	err = q.start("ping" if Cfg.is_windows() else "sleep", PackedStringArray(["-n", "120", "127.0.0.1"]) if Cfg.is_windows() else PackedStringArray(["120"]), cwd)
	if err != "":
		_log("start2 FAIL " + err)
		return 1
	await get_tree().create_timer(1.5).timeout
	q.poll()
	if not q.running:
		_log("FAIL: долгий процесс сразу завершился")
		fails += 1
	q.kill()
	if not await _wait(func(): q.poll(); return not q.running, 15, "принудительная остановка (kill/taskkill)"):
		fails += 1
	_log("FAILS=%d" % fails)
	return 1 if fails else 0


# ------------------------------------------------------------------ проверка системы
func _syscheck() -> int:
	app.go("syscheck", true)
	await get_tree().create_timer(6.0).timeout
	var scr = app._screen
	for r in scr.results:
		_log("%s [%s] %s — %s" % [r.key, r.ok, r.title, r.text.replace("\n", " ")])
	await _shot("40_syscheck")
	var bad := 0
	for r in scr.results:
		if r.ok == "bad" and r.key in ["data", "paper", "adoptium"]:
			bad += 1
	return 1 if bad else 0


# ------------------------------------------------------------------ телефон (портрет 412×892): вход, друзья, чат, магазин, Pro, копии, ссылки
func _phone() -> int:
	var fails := 0
	if not await _wait(func(): return Social.conn in ["need_register", "online"], 20, "связь с relay"):
		return 1
	if Social.conn == "need_register":
		Overlays.register(app)
		await get_tree().create_timer(0.6).timeout
		await _shot("50_phone_register")
		Social.register("Ночной_Лис")
		await _wait(func(): return Social.online(), 10, "профиль создан")
	await _close_overlays()
	var tag_file := data.path_join("autotest-tag.txt")
	var f := FileAccess.open(tag_file, FileAccess.WRITE)
	f.store_string(Social.me.tag)
	f.close()
	await _wait(func(): return Social.incoming.size() >= 3, 30, "заявки в друзья")
	for u in Social.incoming.duplicate():
		Social.request("friend_accept", {"id": u.id})
	await _wait(func(): return Social.friends.size() >= 3, 10, "друзья приняты")
	await _wait(func(): return FileAccess.file_exists(tag_file + ".ok"), 30, "боты готовы")
	await get_tree().create_timer(1.0).timeout
	app.go("home", true)
	await _shot("51_phone_home")
	# --- зайти по коду друга (бот хостит «Новоград», Java + Bedrock)
	var code := ""
	for fr in Social.friends:
		var a: Variant = fr.get("activity")
		if a is Dictionary and a.get("activity", "") == "hosting":
			code = a.code
	_log("код друга: " + code)
	app.go("join", true, {"input": RoomCode.pretty(code), "edition": "both"})
	if not await _wait(func(): return app.relay_join.stats().state == "online", 15, "вошли в комнату друга"):
		fails += 1
	await _wait(func(): return Social.room_code == app.relay_join.code, 5, "чат комнаты открыт")
	Social.room_say("Всем привет! Я с телефона")
	await get_tree().create_timer(1.2).timeout
	await _shot("52_phone_join_room")
	app.relay_join.stop()
	# --- друзья и личный чат
	app.go("friends", true)
	await get_tree().create_timer(1.0).timeout
	await _shot("53_phone_friends")
	app.go("friends", true, {"chat": int(Social.friends[0].id)})
	await get_tree().create_timer(1.2).timeout
	Social.send_dm(int(Social.friends[0].id), "Уже иду! :)")
	await get_tree().create_timer(0.8).timeout
	await _shot("54_phone_chat")
	# --- магазин, Pro, свой код
	app.go("shop", true)
	await get_tree().create_timer(1.0).timeout
	await _shot("55_phone_shop")
	Social.request("demo_purchase", {"product": "lantern_pro_month"})
	await _wait(func(): return Social.eco.get("pro", false), 5, "Pro (ДЕМО) активна")
	Social.request("vanity_set", {"code": "yompix"})
	await get_tree().create_timer(0.5).timeout
	Social.request("vanity_set", {"code": "lu-na7"})
	if not await _wait(func(): return Social.eco.get("vanity", "") == "LUNA7", 5, "свой код LUNA7"):
		fails += 1
	app.go("shop", true, {"kind": "pro"})
	await get_tree().create_timer(1.0).timeout
	await _shot("56_phone_pro")
	# --- копии миров: своя копия → облако → код → скачать по коду → восстановить
	var bdir := Cfg.sub("backups")
	var zp := bdir.path_join("java-Mir_Stiva-2026-11-09_12-00-00.zip")
	var z := ZIPPacker.new()
	z.open(zp)
	for fn in ["Mir_Stiva/level.dat", "Mir_Stiva/region/r.0.0.mca", "Mir_Stiva_nether/level.dat"]:
		z.start_file(fn)
		z.write_file(("LANTERN test " + fn).to_utf8_buffer())
		z.close_file()
	z.close()
	app.go("backups", true)
	Social.request("cloud_list")
	await _wait(func(): return Social.cloud.get("pro", false), 5, "облако: Pro видна")
	var scr = app._screen
	await scr.upload(zp)
	if not scr.last_result.get("ok", false):
		_log("FAIL облако: " + str(scr.last_result))
		fails += 1
	await _wait(func(): return Social.cloud.get("list", []).size() > 0, 5, "копия в списке облака")
	Social.request("cloud_share", {"id": Social.cloud.list[0].id})
	await _wait(func(): return String(Social.cloud.get("shared", "")) != "", 5, "код для друга")
	var share: String = Social.cloud.get("shared", "")
	_log("код копии: " + share)
	await get_tree().create_timer(0.8).timeout
	await _shot("57_phone_backups")
	await scr.fetch_shared(RoomCode.pretty(share))
	if not scr.last_result.get("ok", false):
		_log("FAIL скачивание по коду: " + str(scr.last_result))
		fails += 1
	else:
		var rr: Dictionary = await app.server.restore_backup(scr.last_result.path)
		_log("восстановление: " + str(rr))
		var wd := Cfg.server_sub("servers/java").path_join("Mir_Stiva/level.dat")
		if not rr.get("ok", false) or not FileAccess.file_exists(wd):
			fails += 1
	# --- ссылки lantern://
	Social.request("rooms")
	await _wait(func(): return Social.rooms.size() >= 3, 5, "открытые миры")
	var nyuta := ""
	for r in Social.rooms:
		if r.get("host") is Dictionary and String(r.host.nick) == "Нюта":
			nyuta = r.host.tag
	_log("ссылка: " + DeepLink.friend_link(nyuta))
	app.open_deeplink(DeepLink.friend_link(nyuta))
	if not await _wait(func(): return Social.outgoing.any(func(u): return u.tag == nyuta), 5, "заявка Нюте по ссылке lantern://friend"):
		fails += 1
	await get_tree().create_timer(0.6).timeout
	await _shot("58_phone_friend_link")
	var link := Invite.link(code, "both", "Новоград", "")
	app.open_deeplink(link)
	if not await _wait(func(): return app.relay_join.stats().state == "online" and app.relay_join.code == code, 15, "вход по ссылке lantern://join"):
		fails += 1
	app.relay_join.stop()
	if DeepLink.parse("lantern://friend?tag=x").kind != "" or DeepLink.parse("lantern://evil?c=ABC234").kind != "":
		_log("FAIL: неверные ссылки приняты")
		fails += 1
	# --- «Создать игру» (кнопка «+»)
	app._nav["play"].pressed.emit()
	await get_tree().create_timer(0.8).timeout
	if app.current != "create":
		_log("FAIL: «+» не открыл «Создать игру»")
		fails += 1
	await _shot("62_phone_create")
	# --- экранная клавиатура (имитация --kb=300): поле ввода видно над ней, меню спрятано
	if Cfg.autotest_args.has("kb"):
		app.go("friends", true)
		await get_tree().create_timer(0.8).timeout
		var fe: LineEdit = app._screen.get("_tag_in")
		if fe:
			fe.grab_focus()
			await get_tree().create_timer(0.8).timeout
			var vis := get_viewport().get_visible_rect().size.y - float(Cfg.autotest_args.kb)
			_log("клавиатура: низ поля %.0f, верх клавиатуры %.0f, меню видно: %s" % [fe.get_global_rect().end.y, vis, app._nav_bar.visible])
			if fe.get_global_rect().end.y > vis + 1.0 or app._nav_bar.visible:
				_log("FAIL: поле под клавиатурой")
				fails += 1
			await _shot("61_phone_keyboard")
			fe.release_focus()
			await get_tree().create_timer(0.6).timeout
			if not app._nav_bar.visible:
				_log("FAIL: меню не вернулось после клавиатуры")
				fails += 1
	# --- меню «Ещё» и поворот
	app.go("home", true)
	await get_tree().create_timer(0.5).timeout
	app._nav["more"].pressed.emit()
	await get_tree().create_timer(0.6).timeout
	await _shot("59_phone_more")
	for c in app.get_children():
		if c is PopupMenu:
			c.hide()
	get_window().size = Vector2i(892, 412)
	await get_tree().create_timer(1.0).timeout
	app.go("join", true, {"edition": "both"})
	await get_tree().create_timer(0.8).timeout
	await _shot("60_phone_landscape_join")
	_log("FAILS=%d" % fails)
	return 1 if fails else 0



## Телефонный вид: найти элементы, которые шире экрана (самые глубокие), и записать в журнал.
func _overflow(n: Node, limit: float, where: String) -> bool:
	if not (n is Control) or not (n as Control).is_visible_in_tree():
		return false
	if n is ScrollContainer and (n as ScrollContainer).horizontal_scroll_mode != ScrollContainer.SCROLL_MODE_DISABLED and n != app._screen_root:
		return false   # строка, которая листается вбок (вкладки, награды сезона), — так и задумано
	var deeper := false
	for c in n.get_children():
		if _overflow(c, limit, where):
			deeper = true
	var w := (n as Control).get_combined_minimum_size().x
	if w > limit and not deeper:
		var t := ""
		if n is Label or n is Button:
			t = String(n.text).substr(0, 50)
		_log("ШИРЕ ЭКРАНА [%s] %s %s (%d px) «%s»" % [where, n.get_class(), n.name, int(w), t])
		_widest(n, 0)
		return true
	return w > limit or deeper


## Подсказка к «ШИРЕ ЭКРАНА»: самая широкая ветка (что именно распирает экран)
func _widest(n: Node, depth: int) -> void:
	var best: Control = null
	for c in n.get_children():
		if c is Control and (c as Control).visible and (best == null or (c as Control).get_combined_minimum_size().x > best.get_combined_minimum_size().x):
			best = c
	if best == null or depth > 12:
		return
	var t := String(best.get("text")).substr(0, 30) if best is Label or best is Button else ""
	_log("%s└ %s (%d px) %s" % ["  ".repeat(depth + 1), best.get_class(), int(best.get_combined_minimum_size().x), t])
	_widest(best, depth + 1)


# ------------------------------------------------------------------ дизайн-обход: все экраны и сценарии (tests/design_shots.sh)
func _find_button(root: Node, text: String) -> Button:
	for b in root.find_children("*", "Button", true, false):
		if b.is_visible_in_tree() and ((b as Button).text == text or (b as Button).text == L.t(text)) and not (b as Button).disabled:
			return b
	return null


func _scroll_down(px := 100000) -> void:
	var r = app._screen_root
	if r is ScrollContainer:
		(r as ScrollContainer).scroll_vertical = px
	await get_tree().create_timer(0.3).timeout


func _tour() -> int:
	var fails := 0
	var phone := UI.compact()
	if not await _wait(func(): return Social.conn in ["need_register", "online"], 20, "связь с relay"):
		return 1
	app.go("home", true)
	await _shot("00_home_offline")
	# --- первый запуск: знакомство → ник → «как играть»
	if Social.conn == "need_register":
		Overlays.register(app)
		await get_tree().create_timer(0.5).timeout
		await _shot("01_onboard_welcome")
		var ov: Node = app.get_node("RegisterOverlay")
		_find_button(ov, "Начать").pressed.emit()
		await get_tree().create_timer(0.5).timeout
		var e: LineEdit = ov.find_children("*", "LineEdit", true, false)[0]
		e.text = "Ночной_Лис"     # ник для скриншотов: без имён персонажей игры (docs/LEGAL.md)
		await _shot("02_onboard_nick")
		_find_button(ov, "Готово").pressed.emit()
		await _wait(func(): return Social.online(), 10, "профиль создан")
		await get_tree().create_timer(0.6).timeout
		await _shot("03_onboard_howto")
	await _close_overlays()
	var tag_file := data.path_join("autotest-tag.txt")
	var f := FileAccess.open(tag_file, FileAccess.WRITE)
	f.store_string(Social.me.tag)
	f.close()
	await _wait(func(): return Social.incoming.size() >= 3, 30, "заявки в друзья")
	await get_tree().create_timer(0.3).timeout
	app.go("friends", true)
	await get_tree().create_timer(0.8).timeout
	await _shot("30_friends_requests")
	for u in Social.incoming.duplicate():
		Social.request("friend_accept", {"id": u.id})
	await _wait(func(): return Social.friends.size() >= 3, 10, "друзья приняты")
	await _wait(func(): return FileAccess.file_exists(tag_file + ".ok"), 30, "боты готовы")
	await get_tree().create_timer(1.0).timeout
	Social.request("eco")
	if Cfg.autotest_args.has("rec"):
		return await _rec_all()
	app.go("home", true)
	await get_tree().create_timer(0.6).timeout
	await _shot("10_home")
	await _scroll_down()
	await _shot("10b_home_bottom")
	app.play_sheet()
	await _shot("11_play_sheet")
	await _close_overlays()
	app.go("create", true)
	await get_tree().create_timer(0.4).timeout
	await _shot("12c_create")
	app.go("gamesettings", true)
	await get_tree().create_timer(0.6).timeout
	await _shot("13_game_settings")
	var gs_sc: ScrollContainer = app._screen._scroll
	for i in 4:
		gs_sc.scroll_vertical = int((i + 1) * gs_sc.size.y * 0.85)
		await get_tree().create_timer(0.3).timeout
		await _shot("13%s_game_settings" % "bcde"[i])
	if Cfg.mobile():
		app.go("setup", true, {"own": true})
		await get_tree().create_timer(1.0).timeout
		await _shot("12b_host_own_phone")
		app.go("home", true)
	if Cfg.mobile() and Cfg.autotest_args.has("weblink"):
		# 1.8.1: QR комнаты на телефоне (tests/check_qr.py читает его со скриншота)
		app.relay_host.start(Cfg.relay_url(), "bedrock", "Наш мир", 0, 19132, app.host_extra())
		await _wait(func(): return app.relay_host.stats().code != "", 10, "комната открыта")
		app.own.world = {"key": "t", "edition": "bedrock", "name": "Наш мир", "host": "127.0.0.1", "port": 19132, "version": "1.21", "players": 1, "max": 5, "transport": "raknet"}
		app.own.active = true
		app.go("host", true)
		await get_tree().create_timer(6.0).timeout      # тост о достижении успеет уйти
		app._screen._qr_sheet()
		await _shot("16c_host_qr")
		await _close_overlays()
		app.own.active = false
		app.own.world = {}
		app.relay_host.stop()
		app.go("home", true)
	# --- мастер «Создать мир» (ПК)
	if not Cfg.mobile() and not Cfg.autotest_args.has("nohost"):
		# комната без настоящего сервера: открываем её на relay и показываем экран мира
		var s: ServerHost = app.server
		s.edition = "java"
		s.opts = Create.server_opts("java")
		s.opts["world"] = "Наш мир"
		s.state = "running"
		s._started = s.restart_state()
		s.tps = 20.0
		s.last_status = "Сервер работает. Зовите друзей!"
		s.players = PackedStringArray(["Алекс", "Маша_Кубик"])
		app.relay_host.start(Cfg.relay_url(), "both", "Наш мир", 25565, 19132, app.host_extra())
		await _wait(func(): return app.relay_host.stats().code != "", 10, "комната открыта")
		app.go("host", true)
		await _shot("16_host_room")
		app._screen._qr_sheet()
		await _shot("16c_host_qr")
		await _close_overlays()
		await _scroll_down(1150)
		await _shot("16b_host_bottom")
		app.go("gamesettings", true)
		await get_tree().create_timer(0.6).timeout
		await _shot("17_game_settings_live")
		app._screen._set_opt("online_mode", false)
		await get_tree().create_timer(0.5).timeout
		await _shot("17b_game_settings_restart")
		app._screen._set_opt("online_mode", true)
		if Cfg.autotest_args.has("hostonly"):
			await get_tree().create_timer(float(Cfg.autotest_args.get("hostwait", "3"))).timeout
			_log("host: живы")
			app.relay_host.stop()
			s.state = "idle"
			app.go(String(Cfg.autotest_args.hostonly), true)
			await get_tree().create_timer(2.0).timeout
			_log("host→ ок")
			return 0
		app.relay_host.stop()
		s.state = "idle"
		s.players = PackedStringArray()
	# --- мастер «Зайти к другу»
	var code := ""
	for fr in Social.friends:
		var a: Variant = fr.get("activity")
		if a is Dictionary and a.get("activity", "") == "hosting":
			code = a.code
	app.go("join", true)
	await _shot("20_join_step1_code")
	var js = app._screen
	js._input.text = code.substr(0, 4)
	js._input.grab_focus()
	js._render_tiles()
	await _shot("21_join_typing")
	js._input.text = RoomCode.pretty(code)
	js._render_tiles()
	js._next_from_code()
	await _shot("22_join_step2_game")
	js._plays = "both"
	js.preview("connecting")
	await _shot("23_join_connecting")
	js._connect()
	if not await _wait(func(): return app.relay_join.stats().state == "online", 15, "вошли в комнату друга"):
		fails += 1
	await _wait(func(): return Social.room_code == app.relay_join.code, 5, "чат комнаты открыт")
	Social.room_say("Всем привет! Я с телефона")
	await get_tree().create_timer(1.5).timeout
	await _shot("24_join_step3_in_world")
	js.preview("launch")
	await get_tree().create_timer(0.1).timeout
	await _shot("24b_join_auto_open")
	js._launch.on = false
	js._launch_done = true
	js._plays = "both"
	js._shown_state = "-"
	js._show_room()
	await _scroll_down(int(900 if phone else 500))
	await _shot("25_join_where_to_find")
	await _scroll_down()
	await _shot("26_join_room_chat")
	app.relay_join.stop()
	# --- друзья и чат
	app.go("friends", true)
	await get_tree().create_timer(0.8).timeout
	await _shot("31_friends")
	app.go("friends", true, {"chat": int(Social.friends[0].id)})
	await get_tree().create_timer(1.0).timeout
	Social.send_dm(int(Social.friends[0].id), "Уже иду! :)")
	await get_tree().create_timer(1.0).timeout
	await _shot("32_chat")
	app.go("friends", true)
	await get_tree().create_timer(0.4).timeout
	app._screen._qr_sheet(Social.me.tag)
	await _shot("33_friend_qr")
	await _close_overlays()
	app.go("campfire", true)
	await get_tree().create_timer(1.0).timeout
	await _shot("34_campfire")
	app.go("rooms", true)
	await get_tree().create_timer(1.5).timeout
	await _shot("35_rooms")
	app.go("quests", true)
	await get_tree().create_timer(1.0).timeout
	await _shot("40_quests")
	await _scroll_down()
	await _shot("40b_quests_bottom")
	app.go("quests", true, {"tab": "season"})
	await get_tree().create_timer(1.0).timeout
	await _shot("41_season")
	app.go("quests", true, {"tab": "achievements"})
	await get_tree().create_timer(1.0).timeout
	await _shot("42_achievements")
	app.go("shop", true)
	await get_tree().create_timer(1.0).timeout
	await _shot("43_shop")
	app.go("shop", true, {"kind": "pro"})
	await get_tree().create_timer(0.8).timeout
	await _shot("44_shop_pro")
	Overlays.purchase(app, "sparks_1200", func(): Social.request("shop"))
	await _shot("45_purchase_sheet")
	_press("Купить", true)
	await get_tree().create_timer(1.6).timeout
	await _shot("46_purchase_done")
	await _close_overlays()
	Overlays.ad(app, true)
	await get_tree().create_timer(0.3).timeout
	await _shot("47_ad")
	await _close_overlays()
	app.go("profile", true)
	await get_tree().create_timer(1.0).timeout
	await _shot("50_profile")
	app.go("backups", true)
	await get_tree().create_timer(1.0).timeout
	await _shot("51_backups")
	app.go("settings", true)
	await get_tree().create_timer(0.6).timeout
	await _shot("52_settings")
	if not Cfg.mobile():
		app.go("syscheck", true)
		await get_tree().create_timer(4.0).timeout
		await _shot("53_syscheck")
	app.go("home", true)
	await get_tree().create_timer(0.4).timeout
	if phone:
		app._nav["more"].pressed.emit()
		await _shot("54_more_sheet")
		await _close_overlays()
	app.toast("Код K7M-2QX скопирован — отправьте его друзьям", UI.JAVA)
	await _shot("55_toast")
	await get_tree().create_timer(3.4).timeout
	Report.dialog(app, "test")
	await _shot("56_report")
	await _close_overlays()
	if phone:
		RoomControl.vendor_sheet(app, true, "Xiaomi")
		await _shot("57_vendor_xiaomi")
		await _close_overlays()
	_log("FAILS=%d" % fails)
	return 1 if fails else 0


# ------------------------------------------------------------------ запись анимаций кадрами (tour --anim=1 --rec=1)
func _rec(name: String, sec: float, action: Callable) -> void:
	var dir := shots.path_join("anim_" + name)
	DirAccess.make_dir_recursive_absolute(dir)
	Engine.time_scale = 0.3
	var frames := []
	var times := []
	await get_tree().process_frame
	action.call()
	var t0 := Time.get_ticks_usec()
	var last := t0
	var game := 0.0
	while game < sec:
		await get_tree().process_frame
		var now := Time.get_ticks_usec()
		game += (now - last) / 1000000.0 * Engine.time_scale
		last = now
		var img := get_viewport().get_texture().get_image()
		img.resize(img.get_width() / 2, img.get_height() / 2, Image.INTERPOLATE_BILINEAR)
		frames.append(img)
		times.append(game)
	Engine.time_scale = 1.0
	var f := FileAccess.open(dir.path_join("times.txt"), FileAccess.WRITE)
	for i in frames.size():
		frames[i].save_png(dir.path_join("%03d.png" % i))
		f.store_line(str(times[i]))
	f.close()
	_log("анимация %s: %d кадров" % [name, frames.size()])


func _rec_all() -> int:
	app.go("home", true)
	await get_tree().create_timer(0.8).timeout
	await _rec("00_splash", 1.5, func(): app._splash(true))
	await _rec("01_home_live", 1.6, func(): app.go("home", true))
	await _rec("tabs", 0.9, func(): app.go("friends"))
	await get_tree().create_timer(0.4).timeout
	await _rec("sheet", 0.9, func(): app.play_sheet())
	await _close_overlays()
	await _rec("push", 0.9, func(): app.go("join"))
	var code := ""
	for fr in Social.friends:
		var a: Variant = fr.get("activity")
		if a is Dictionary and a.get("activity", "") == "hosting":
			code = a.code
	var js = app._screen
	await get_tree().create_timer(0.4).timeout
	await _rec("code_typing", 1.0, func():
		for i in 6:
			js._input.text = code.substr(0, i + 1)
			js._render_tiles()
			await get_tree().create_timer(0.12 * Engine.time_scale).timeout)
	await _rec("wizard_step", 0.9, func(): js._next_from_code())
	js._plays = "both"
	await _rec("connect_beam", 1.6, func(): js.preview("connecting"))
	await _rec("connect_success", 2.2, func(): js._connect())
	await _rec("launch_countdown", 1.6, func(): js.preview("launch"))
	js._launch.on = false
	app.relay_join.stop()
	# экран мира: «переворот» цифр кода и искры (сервер не запускаем — только комната на relay)
	var s: ServerHost = app.server
	s.edition = "java"
	s.opts = {"world": "Наш мир", "crossplay": true, "internet": true}
	s.state = "running"
	s.players = PackedStringArray(["Алекс"])
	app.relay_host.start(Cfg.relay_url(), "both", "Наш мир", 25565, 19132, app.host_extra())
	await _wait(func(): return app.relay_host.stats().code != "", 10, "комната открыта")
	await _rec("code_reveal", 2.0, func():
		app.go("host", true)
		await get_tree().create_timer(0.9 * Engine.time_scale).timeout
		var scr = app._screen
		if scr and scr.get("_code_card"):
			FX.burst(app, scr._code_card.get_global_rect().get_center() - app.global_position, 34)
			app.toast("Друг зашёл в ваш мир!", UI.JAVA))
	app.relay_host.stop()
	s.state = "idle"
	app.go("quests", true)
	await get_tree().create_timer(0.8).timeout
	await _rec("segmented", 0.8, func(): app._screen._seg.get_meta("tabs")["season"].pressed.emit())
	app.go("campfire", true)
	await get_tree().create_timer(0.5).timeout
	await _rec("campfire", 1.6, func(): pass)
	return 0


# ------------------------------------------------------------------ «Мой мир из Minecraft» (tests/ownworld.sh + tests/fake_world.js)
func _ownworld() -> int:
	var fails := 0
	var own: OwnWorld = app.own
	var pc := not Cfg.mobile()
	# разбор пакетов
	var mb := "MCPE;Стив;712;1.21.50;2;8;7;Мой мир;Survival;1;19132;19133;".to_utf8_buffer()
	var pong := OwnWorld.parse_pong(PackedByteArray([0x1C, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 7]) + PackedByteArray(RelayJoin.MAGIC) + PackedByteArray([0, mb.size()]) + mb)
	if pong.get("name") != "Мой мир" or pong.get("port") != 19132 or pong.get("players") != 2 or pong.get("version") != "1.21.50":
		_log("parse_pong FAIL " + str(pong))
		fails += 1
	var ad := OwnWorld.parse_announcement("[MOTD]Стив - Мой мир[/MOTD][AD]54321[/AD]")
	if ad.get("name") != "Мой мир" or ad.get("port") != 54321:
		_log("parse_announcement FAIL " + str(ad))
		fails += 1
	var frame := PackedByteArray([0x84, 0, 0, 0, 0x60, 0, 64, 0, 0, 0, 0, 0, 0, 0, 0xfe, 6, 0xc1, 0x01, 0, 0, 0x02, 0xbc])
	if RelayJoin.client_protocol(frame) != 700:
		_log("client_protocol FAIL %d" % RelayJoin.client_protocol(frame))
		fails += 1
	# NetherNet: шифрование туда-обратно, карточка мира, подмена ICE-кандидатов
	var sdx := PackedByteArray([7, 3]) + "S1A".to_utf8_buffer() + PackedByteArray([0x80, 0x22, 6]) + "1.26.6".to_utf8_buffer() + PackedByteArray([3]) + "Wld".to_utf8_buffer() + PackedByteArray([2, 16, 2, 0, 0, 1, 1, 0, 8])
	var nr := OwnWorld.parse_nethernet(NetherNet.response(42, sdx))
	var nd := NetherNet.decode(NetherNet.message(1, 2, "CONNECTREQUEST 5 x"))
	if nr.get("transport") != "nethernet" or nr.get("nn_id") != 42 or nr.get("version") != "1.26.6" or nr.get("name") != "Wld" or nr.get("players") != 1 or nd.get("text") != "CONNECTREQUEST 5 x" or nd.get("recipient") != 2:
		_log("nethernet FAIL %s %s" % [nr, nd])
		fails += 1
	var ss := NetherNet.new_session("5")
	var rw := NetherNet.remote_signal(ss, "CANDIDATEADD 5 candidate:1 1 udp 2130706431 10.1.2.3 19132 typ host generation 0 ufrag ab network-id 1 network-cost 0")
	var rs := NetherNet.remote_signal(ss, "CANDIDATEADD 5 candidate:2 1 udp 1694498815 5.6.7.8 4444 typ srflx raddr 0.0.0.0 rport 0 generation 0")
	if rw.is_empty() or not rw[0].contains(" %d typ host" % ss.u.get_local_port()) or rw[0].contains("10.1.2.3") or not rs.is_empty():
		_log("nethernet ICE FAIL %s %s" % [rw, rs])
		fails += 1
	NetherNet.close_session(ss)
	# --- 1. «Создать мир» → два способа
	app.go("setup", true, {"own": true})
	var scr = app._screen
	await get_tree().create_timer(2.5).timeout
	if not own.worlds().is_empty():
		_log("FAIL: мир найден до запуска")
		fails += 1
	await _shot("61_own_help_bedrock")
	if shots != "":
		await get_tree().create_timer(6.5).timeout
		await _shot("61c_own_check")
		if scr._own_page._check.visible != true:
			_log("FAIL: чек-лист «мир не найден» не показан")
			fails += 1
	if pc:
		var pg = scr._own_page
		pg._seg.get_meta("tabs")["java"].pressed.emit()
		await get_tree().create_timer(0.4).timeout
		await _shot("61b_own_help_java")
	# --- 2. «игрок открыл мир» — запускаем поддельный мир
	var f := FileAccess.open(data.path_join("own-start"), FileAccess.WRITE)
	f.store_string("1")
	f.close()
	if not await _wait(func(): return own.worlds().any(func(w): return w.edition == "bedrock"), 15, "мир Bedrock найден (RakNet-понг)"):
		return 1
	if pc and not await _wait(func(): return own.worlds().any(func(w): return w.edition == "java"), 15, "мир Java найден (объявление LAN)"):
		fails += 1
	await get_tree().create_timer(0.6).timeout
	_log("найдено: " + JSON.stringify(own.worlds()))
	await _shot("62_own_found")
	# --- 3. Bedrock: комната → друг → данные
	var bw: Dictionary = own.worlds().filter(func(w): return w.edition == "bedrock")[0]
	if bw.name != "Тестовый мир" or bw.version != "1.21.50":
		_log("FAIL: карточка мира " + str(bw))
		fails += 1
	scr._own_page._open(bw)
	if not await _wait(func(): return app.relay_host.stats().code != "", 20, "комната для своего мира открыта"):
		return 1
	var code: String = app.relay_host.stats().code
	await _shot("63_own_host_bedrock")
	app.go("join", true, {"input": RoomCode.pretty(code), "edition": "both"})
	if not await _wait(func(): return app.relay_join.stats().state == "online", 20, "друг вошёл в комнату"):
		return 1
	if not await _wait(func(): return app.relay_join.stats().pong, 20, "мир ответил на пинг через relay (виден в «Друзья/LAN»)"):
		fails += 1
	var js: Dictionary = app.relay_join.stats()
	_log("друг видит: %s, версия %s" % [js.motd, js.host_version])
	if not String(js.motd).contains("Тестовый мир") or js.host_version != "1.21.50":
		fails += 1
	var u := PacketPeerUDP.new()
	u.bind(0, "127.0.0.1")
	u.set_dest_address("127.0.0.1", int(js.bedrock_port))
	# первый пакет «клиента» с протоколом 700 (мир — 712): друг увидит подсказку про версию
	u.put_packet(frame)
	u.put_packet("hello-bedrock".to_utf8_buffer())
	var got := [""]
	var ok := await _wait(func():
		while u.get_available_packet_count() > 0:
			got[0] += u.get_packet().get_string_from_utf8() + "|"
		return got[0].contains("ECHO:hello-bedrock"), 15, "датаграмма друга дошла до мира и вернулась")
	_log("ответ мира: " + got[0].replace("\n", " ").substr(0, 120))
	if not ok:
		fails += 1
	if not await _wait(func(): return app.relay_join.stats().client_protocol == 700, 5, "версия Minecraft друга распознана"):
		fails += 1
	var vt: Array = preload("res://scripts/screens/join.gd").version_text(app.relay_join.stats())
	_log("подсказка другу: " + vt[1])
	if vt[0] != "bad":
		fails += 1
	await get_tree().create_timer(1.0).timeout
	await _shot("64_own_join_version")
	if not await _wait(func(): return int(app.relay_host.stats().peers) >= 1, 10, "хозяин видит друга"):
		fails += 1
	# 1.4: relay сообщает, кто зашёл; статус «друзья могут зайти»; «Все в LANTERN» включено по умолчанию
	if not await _wait(func(): return app.room.peers().size() >= 1, 10, "хозяин видит друга в списке игроков"):
		fails += 1
	if not await _wait(func(): return app.room.status().ok, 10, "статус «Друзья могут зайти прямо сейчас»"):
		fails += 1
	if not app.relay_host.public_on:
		_log("FAIL: мир не открыт для всех в LANTERN по умолчанию")
		fails += 1
	app.go("host", true)
	await get_tree().create_timer(1.0).timeout
	await _shot("65_own_host_friend")
	# мир «замолк» (Minecraft свернули) → предупреждение, потом снова живой
	f = FileAccess.open(data.path_join("own-mute"), FileAccess.WRITE)
	f.store_string("1")
	f.close()
	if not await _wait(func(): return not own.alive(), 15, "хозяин видит «Не отвечает»"):
		fails += 1
	await get_tree().create_timer(0.6).timeout
	await _shot("66_own_not_responding")
	DirAccess.remove_absolute(data.path_join("own-mute"))
	if not await _wait(func(): return own.alive(), 10, "мир снова отвечает"):
		fails += 1
	# связь с relay оборвалась (телефон усыпил LANTERN) → тот же код после переподключения
	app.relay_host.stop(false)
	own.on_resume()
	if not await _wait(func(): return app.relay_host.stats().state == "online", 15, "после возврата комната снова на связи"):
		fails += 1
	_log("код после переподключения: %s (был %s)" % [app.relay_host.stats().code, code])
	if app.relay_host.stats().code != code:
		_log("FAIL: код комнаты сменился после переподключения")
		fails += 1
	# 1.4: хозяин выгоняет друга → у друга «Хозяин мира закрыл вам вход»
	if await _wait(func(): return app.room.peers().size() >= 1, 15, "друг снова в списке после переподключения"):
		app.room.kick(String(app.room.peers()[0].peer))
		if not await _wait(func(): return String(app.relay_join.stats().error).contains("закрыл вам вход"), 10, "друг видит «Хозяин мира закрыл вам вход»"):
			_log("друг: " + str(app.relay_join.stats()))
			fails += 1
		if not await _wait(func(): return app.room.peers().is_empty(), 10, "после «Выгнать» список игроков пуст"):
			fails += 1
	else:
		fails += 1
	app.room.set_public(false)
	if not await _wait(func(): return app.room.is_public() == false, 3, "«Только друзья» переключилось"):
		fails += 1
	app.room.set_public(true)
	u.close()
	app.relay_join.stop()
	own.stop()
	# --- 4. Java «Открыть для сети» (только ПК)
	if pc:
		app.go("setup", true, {"own": true})
		if not await _wait(func(): return own.worlds().any(func(w): return w.edition == "java"), 10, "мир Java снова найден"):
			return 1
		var jw: Dictionary = own.worlds().filter(func(w): return w.edition == "java")[0]
		app._screen._own_page._open(jw)
		if not await _wait(func(): return app.relay_host.stats().code != "", 20, "комната для мира Java открыта"):
			return 1
		code = app.relay_host.stats().code
		app.go("join", true, {"input": RoomCode.pretty(code), "edition": "both"})
		if not await _wait(func(): return app.relay_join.stats().state == "online" and app.relay_join.stats().java_port > 0, 20, "друг (Java) вошёл"):
			return 1
		await get_tree().create_timer(2.0).timeout
		if own.worlds().any(func(w): return String(w.motd).ends_with(OwnWorld.LANTERN_TAG)):
			_log("FAIL: своё объявление «Зайти к другу» принято за мир")
			fails += 1
		var t := StreamPeerTCP.new()
		t.connect_to_host("127.0.0.1", int(app.relay_join.stats().java_port))
		var sent := [false]
		var buf := [""]
		ok = await _wait(func():
			t.poll()
			if t.get_status() == StreamPeerTCP.STATUS_CONNECTED:
				if not sent[0]:
					sent[0] = true
					t.put_data("hello-java".to_utf8_buffer())
				var n := t.get_available_bytes()
				if n > 0:
					buf[0] += t.get_utf8_string(n)
			return buf[0].contains("ECHO:hello-java"), 15, "TCP друга дошёл до мира Java и вернулся")
		if not ok:
			fails += 1
		app.go("host", true)
		await get_tree().create_timer(1.0).timeout
		await _shot("67_own_host_java")
		t.disconnect_from_host()
		app.relay_join.stop()
		own.stop()
	# «Как зайти самому» на экране своего сервера (без настоящего сервера — только комната)
	for ed in ["bedrock", "java"]:
		var sv: ServerHost = app.server
		sv.edition = ed
		sv.opts = {"world": "Наш мир", "internet": true, "crossplay": true}
		sv.state = "running"
		if ed == "java":
			sv.opts["online_mode"] = true
			sv.issues = []
			sv.fixes = []
			sv._doctor("[12:01:02 INFO]: Disconnecting Steve (/127.0.0.1:5555): You are not whitelisted on this server!")
			sv._doctor("[12:01:03 INFO]: Disconnecting com.mojang.authlib.GameProfile@1[id=<null>,name=Masha,properties={}] (/1.2.3.4:5): Failed to verify username!")
			sv._fixed("Порт 25565 был занят — сервер на порту 25566, друзья заходят как обычно")
		app.relay_host.start(Cfg.relay_url(), ed, "Наш мир", 25565, 19132, app.host_extra())
		await _wait(func(): return app.relay_host.stats().code != "", 10, "комната сервера открыта")
		app.go("host", true)
		await get_tree().create_timer(0.8).timeout
		if ed == "java":
			await _shot("68a_host_whitelist")
		await _scroll_down(520)
		await _shot("68_host_server_selfjoin_" + ed)
		# хозяин вводит свой же код в «Зайти к другу» → подсказка «Как зайти самому», без прокси
		var jb = Cfg.autotest_args.get("join_bedrock_port")
		Cfg.autotest_args.erase("join_bedrock_port")
		app.go("join", true, {"input": RoomCode.pretty(app.relay_host.stats().code), "edition": "both"})
		await get_tree().create_timer(1.0).timeout
		if app.relay_join.stats().state != "off":
			_log("FAIL: свой код запустил прокси друга")
			fails += 1
		else:
			_log("ок: свой код → «Как зайти самому» (%s)" % ed)
		await _shot("69_join_own_code_" + ed)
		Cfg.autotest_args["join_bedrock_port"] = jb
		app.relay_host.stop()
		sv.state = "idle"
		sv.issues = []
		sv.fixes = []
	# одна кнопка «Играть с друзьями»: мир открыт в игре → комната открывается сама, без нажатий
	Cfg.set_value("own", "last_edition", "bedrock")
	app.go("home", true)
	await _shot("70_home_one_tap")
	app.quick_host()   # 1.5: «+» → «Создать игру» → «Создать» → дальше всё само
	await get_tree().create_timer(0.5).timeout
	await _shot("70b_create_game")
	if app.current == "create":
		# 1.8: на ПК «Создать» запускает сервер для всех; свой мир — кнопкой «Открыть друзьям мир из моего Minecraft»
		if Cfg.mobile():
			app._screen._create()
		else:
			_press("Открыть друзьям мир из моего Minecraft")
	if not await _wait(func(): return own.active and app.relay_host.stats().code != "", 15, "одна кнопка: комната открылась сама"):
		fails += 1
	await get_tree().create_timer(0.8).timeout
	await _shot("71_one_tap_room")
	own.stop()
	app.go("home", true)
	await get_tree().create_timer(0.5).timeout
	_log("FAILS=%d" % fails)
	return 1 if fails else 0


## Настоящий Bedrock Dedicated Server, запущенный отдельно на 19132, играет роль «мира, открытого в игре».
func _ownreal() -> int:
	var own: OwnWorld = app.own
	app.go("setup", true, {"own": true})
	if not await _wait(func(): return own.worlds().any(func(w): return w.edition == "bedrock"), 30, "настоящий мир Bedrock найден"):
		return 1
	var w: Dictionary = own.worlds().filter(func(x): return x.edition == "bedrock")[0]
	_log("мир: " + JSON.stringify(w))
	app._screen._own_page._open(w)
	if not await _wait(func(): return app.relay_host.stats().code != "", 20, "комната открыта"):
		return 1
	app.go("join", true, {"input": RoomCode.pretty(app.relay_host.stats().code), "edition": "both"})
	if not await _wait(func(): return app.relay_join.stats().pong or app.relay_join.stats().nethernet, 20, "мир виден у друга (RakNet-понг или карточка NetherNet через relay)"):
		return 1
	var js: Dictionary = app.relay_join.stats()
	_log("друг: " + JSON.stringify(js))
	await _shot("70_ownreal_join")
	var rf := FileAccess.open(data.path_join("autotest-ready.json"), FileAccess.WRITE)
	rf.store_string(JSON.stringify({"code": app.relay_host.stats().code, "edition": "bedrock", "java_port": 0, "bedrock_port": js.bedrock_port, "motd": js.motd, "name": js.name}))
	rf.close()
	var done := data.path_join("autotest-done.json")
	var ok := await _wait(func(): return FileAccess.file_exists(done), 300, "настоящий клиент Bedrock закончил")
	var res: Variant = JSON.parse_string(FileAccess.get_file_as_string(done)) if ok else {}
	_log("клиент: " + JSON.stringify(res))
	_log("relay host: " + JSON.stringify(app.relay_host.stats()))
	app.relay_join.stop()
	own.stop()
	return 0 if res is Dictionary and res.get("ok", false) else 1


# ------------------------------------------------------------------ 1.4: касания на телефоне (--phone=1)
## Палец ведёт вверх, начав на кнопке: экран листается, кнопка НЕ срабатывает. Короткое касание — срабатывает.
func _touch() -> int:
	var fails := 0
	Input.emulate_touch_from_mouse = true      # чтобы ScrollContainer считал устройство сенсорным (как телефон)
	if get_window().size.y < 600:
		get_window().size = Vector2i(412, 892)
	await get_tree().create_timer(0.3).timeout
	app.go("home")
	await get_tree().create_timer(1.0).timeout
	var sc = app._screen_root
	# длинный список кнопок, как «Открытые миры» с десятками миров
	var many := UI.vbox(8)
	for i in 30:
		many.add_child(UI.list_row(UI.icon_tile("globe", UI.JAVA, 44, 22), "Мир %d" % i, "Bedrock · 1/10", UI.button("Зайти", "primary", func(): pass)))
	app._screen.add_child(many)
	app._screen.move_child(many, 0)
	await get_tree().create_timer(0.5).timeout
	if not sc is ScrollContainer:
		_log("FAIL: экран не прокручивается")
		return 1
	if (sc as ScrollContainer).horizontal_scroll_mode != ScrollContainer.SCROLL_MODE_DISABLED:
		_log("FAIL: экран листается вбок")
		fails += 1
	var btn: Button = null
	for b in app._screen.find_children("*", "Button", true, false):
		var r: Rect2 = (b as Control).get_global_rect()
		if b.is_visible_in_tree() and not b.disabled and r.size.y > 30 and r.get_center().y > 200 and r.get_center().y < get_viewport().get_visible_rect().size.y - 200:
			btn = b
			break
	if btn == null:
		_log("FAIL: нет кнопки для проверки (экран %s, кнопок %d, высота прокрутки %d)" % [get_viewport().get_visible_rect().size, app._screen.find_children("*", "Button", true, false).size(), (sc as ScrollContainer).get_v_scroll_bar().max_value])
		for b in app._screen.find_children("*", "Button", true, false):
			_log("  %s %s %s" % [b.text, b.get_global_rect(), b.is_visible_in_tree()])
		return 1
	var fired := [0]
	btn.pressed.connect(func(): fired[0] += 1)
	var p := btn.get_global_rect().get_center()
	var y0: int = sc.scroll_vertical
	var t := InputEventScreenTouch.new()
	t.index = 0
	t.position = p
	t.pressed = true
	Input.parse_input_event(t)
	await get_tree().process_frame
	for i in 12:
		var d := InputEventScreenDrag.new()
		d.index = 0
		d.position = p - Vector2(0, 20 * (i + 1))
		d.relative = Vector2(0, -20)
		Input.parse_input_event(d)
		await get_tree().process_frame
	t = t.duplicate()
	t.position = p - Vector2(0, 240)
	t.pressed = false
	Input.parse_input_event(t)
	await get_tree().create_timer(0.5).timeout
	_log("прокрутка пальцем: %d → %d, нажатий кнопки «%s»: %d (сенсор %s, фильтр %d, макс %d)" % [y0, sc.scroll_vertical, btn.text, fired[0], DisplayServer.is_touchscreen_available(), btn.mouse_filter, sc.get_v_scroll_bar().max_value])
	if sc.scroll_vertical <= y0:
		_log("FAIL: палец, начавший на кнопке, не листает экран")
		fails += 1
	if fired[0] != 0:
		_log("FAIL: кнопка сработала при прокрутке")
		fails += 1
	# короткое касание — нажатие
	fired[0] = 0
	await get_tree().create_timer(2.5).timeout     # инерция прокрутки затихла
	var vh := get_viewport().get_visible_rect().size.y
	for b in app._screen.find_children("*", "Button", true, false):
		var r: Rect2 = (b as Control).get_global_rect()
		if b.is_visible_in_tree() and (b as Button).text == "Зайти" and r.get_center().y > 150 and r.get_center().y < vh - 150:
			btn = b
			break
	btn.pressed.connect(func(): fired[0] += 1)
	p = btn.get_global_rect().get_center()
	var t2 := InputEventScreenTouch.new()
	t2.index = 0
	t2.position = p
	t2.pressed = true
	Input.parse_input_event(t2)
	await get_tree().process_frame
	var t3 := t2.duplicate()
	t3.pressed = false
	Input.parse_input_event(t3)
	await get_tree().create_timer(0.3).timeout
	if fired[0] != 1:
		_log("FAIL: короткое касание не нажало кнопку (%d)" % fired[0])
		fails += 1
	else:
		_log("ок: касание нажимает, прокрутка — нет")
	_log("FAILS=%d" % fails)
	return 0 if fails == 0 else 1


# ------------------------------------------------------------------ 1.7: окошко поверх игры — настоящее окно системы (tests/mini_window.sh)
func _mini() -> int:
	var s: ServerHost = app.server
	s.edition = "java"
	s.opts = {"world": "Наш мир", "crossplay": true, "internet": true}
	s.state = "running"
	app.relay_host.start(Cfg.relay_url(), "both", "Наш мир", 25565, 19132, app.host_extra())
	await _wait(func(): return app.relay_host.stats().code != "", 10, "комната открыта")
	app.room._open_mini()
	await get_tree().create_timer(1.0).timeout
	var m: Window = app.room.mini
	var info := {"embedded": m.is_embedded(), "id": m.get_window_id(), "main_id": get_window().get_window_id(),
		"native": DisplayServer.window_get_native_handle(DisplayServer.WINDOW_HANDLE, m.get_window_id()) if not m.is_embedded() else 0,
		"main_native": DisplayServer.window_get_native_handle(DisplayServer.WINDOW_HANDLE, 0),
		"pos": [m.position.x, m.position.y], "size": [m.size.x, m.size.y], "on_top": m.always_on_top, "no_focus": m.unfocusable,
		"main_pos": [get_window().position.x, get_window().position.y], "main_size": [get_window().size.x, get_window().size.y]}
	_log("mini: " + JSON.stringify(info))
	var fails := 0
	if m.is_embedded() or m.get_window_id() == get_window().get_window_id():
		_log("ОШИБКА: окошко встроено в главное окно")
		fails += 1
	FileAccess.open(data.path_join("mini-1.json"), FileAccess.WRITE).store_string(JSON.stringify(info))
	await _wait(func(): return FileAccess.file_exists(data.path_join("mini-1.ok")), 30, "скрипт посмотрел окна")
	# главное окно сворачиваем: окошко должно остаться
	get_window().mode = Window.MODE_MINIMIZED
	await get_tree().create_timer(1.5).timeout
	m.flash("Алекс зашёл в ваш мир", false)
	await get_tree().create_timer(1.0).timeout
	info["main_minimized"] = get_window().mode == Window.MODE_MINIMIZED
	info["visible_after"] = m.visible and m.mode != Window.MODE_MINIMIZED
	info["size"] = [m.size.x, m.size.y]
	info["focused_after_flash"] = m.has_focus()
	_log("mini после сворачивания: " + JSON.stringify(info))
	if not info.visible_after:
		fails += 1
	if shots != "":
		DirAccess.make_dir_recursive_absolute(shots)
		m.get_texture().get_image().save_png(shots.path_join("mini_window.png"))
	FileAccess.open(data.path_join("mini-2.json"), FileAccess.WRITE).store_string(JSON.stringify(info))
	await _wait(func(): return FileAccess.file_exists(data.path_join("mini-2.ok")), 30, "скрипт посмотрел окна ещё раз")
	app.relay_host.stop()
	s.state = "idle"
	return fails


# ------------------------------------------------------------------ мосты поверх эхо-сервера (tests/latency.sh)
func _bridge() -> int:
	var jt := int(Cfg.autotest_args.get("echo_tcp", "25599"))
	var ju := int(Cfg.autotest_args.get("echo_udp", "19199"))
	app.relay_host.probe_world = false
	app.relay_host.start(Cfg.relay_url(), "both", "bench", jt, ju, {})
	if not await _wait(func(): return app.relay_host.stats().code != "", 15, "комната открыта"):
		return 1
	var code: String = app.relay_host.stats().code
	app.relay_join.start(Cfg.relay_url(), code, true, true, int(Cfg.autotest_args.get("join_java_port", "25570")), int(Cfg.autotest_args.get("join_bedrock_port", "19142")))
	if not await _wait(func(): return app.relay_join.stats().state == "online" and int(app.relay_join.stats().java_port) > 0, 15, "друг в комнате"):
		return 1
	await get_tree().create_timer(0.5).timeout
	FileAccess.open(data.path_join("autotest-ready.json"), FileAccess.WRITE).store_string(JSON.stringify({"code": code}))
	var done := data.path_join("autotest-done.json")
	await _wait(func(): return FileAccess.file_exists(done), 300, "замер закончен")
	_log("мост хозяина: " + JSON.stringify(app.relay_host.stats()))
	_log("мост друга: " + JSON.stringify(app.relay_join.stats()))
	return 0


# ------------------------------------------------------------------ 1.8: «нерабочих кнопок нет» (tests/deadbuttons.sh)

## Всё, что можно нажать: кнопки, переключатели, списки выбора и карточки (UI.tappable).
func _controls(root: Node) -> Array:
	var out := []
	for n in root.find_children("*", "Control", true, false):
		var c := n as Control
		if not c.is_visible_in_tree() or c.get_global_rect().size.x < 2:
			continue
		if c is BaseButton:
			if c.get_parent() is ScrollBar or c.get_parent() is SpinBox:
				continue
			out.append(c)
		elif c.has_meta("tap"):
			out.append(c)
	return out


func _ctl_name(c: Control) -> String:
	var t := ""
	if c is Button:
		t = String((c as Button).text)
	if t == "":
		t = String(c.tooltip_text)
	if t == "" and c.has_meta("title"):
		t = String((c.get_meta("title") as Label).text)
	if t == "":
		for l in c.find_children("*", "Label", true, false):
			if String((l as Label).text) != "":
				t = String((l as Label).text)
				break
	if t == "":
		# подпись рядом (строка с переключателем)
		var par := c.get_parent()
		if par:
			for l in par.find_children("*", "Label", true, false):
				t = String((l as Label).text)
				break
	return "%s «%s»" % [c.get_class(), t.substr(0, 40)]


func _tree_hash(n: Node, acc: Array) -> void:
	if n is Control:
		var c := n as Control
		if not c.visible:
			return
		acc.append(c.get_class())
		if c is Label:
			acc.append((c as Label).text)
		elif c is Button:
			acc.append((c as Button).text)
			acc.append((c as Button).button_pressed)
			acc.append((c as Button).disabled)
		elif c is LineEdit:
			acc.append((c as LineEdit).text)
			acc.append((c as LineEdit).secret)
		elif c is RichTextLabel:
			acc.append((c as RichTextLabel).get_parsed_text().length())
		elif c is ScrollContainer:
			acc.append((c as ScrollContainer).scroll_vertical)
			acc.append((c as ScrollContainer).scroll_horizontal)
		elif c is TextureRect:
			acc.append((c as TextureRect).texture != null)
		if c.has_meta("key"):
			acc.append(str(c.get_meta("key")))
	for ch in n.get_children():
		_tree_hash(ch, acc)


## «Отпечаток» всего, что кнопка может изменить: экран, листы, тост, буфер обмена, открытия в системе,
## настройки, команды серверу Minecraft / relay / соцсети, состояние сервера и комнаты, вид экрана, фокус ввода.
func _fingerprint() -> Dictionary:
	var acc := []
	_tree_hash(app, acc)
	var fo := get_viewport().gui_get_focus_owner()
	return {"screen": "%s|%s|%d" % [app.current, str(app._arg), app._stack.size()], "nodes": app.get_child_count(),
		"toast": app.toast_count, "popup": get_tree().root.find_children("*", "PopupMenu", true, false).filter(func(p): return (p as PopupMenu).visible).size() + get_tree().root.find_children("*", "FileDialog", true, false).size(), "copy": UI.copy_count, "open": UI.open_log.size(),
		"cfg": Cfg.cfg.encode_to_text().hash(), "srv_cmd": app.server.cmd_log.size(), "srv": "%s|%s" % [app.server.state, str(app.server.opts).hash()],
		"relay": "%d|%s|%s" % [app.relay_host.cmd_total, app.relay_host.stats().state, app.relay_join.stats().state], "social": Social.sent_count,
		"own": "%s|%s" % [app.own.active, app.own.auto_pending], "ui": str(acc).hash(), "focus": str(fo.get_path()) if fo else "", "launch": Launcher.last_url,
		"win": get_tree().root.get_child_count(), "locale": TranslationServer.get_locale(), "fx": str(Cfg.get_value("app", "accent", ""))}


var _toasts := 0
var _dead_setup := {}


func _dead_changed(a: Dictionary, b: Dictionary) -> String:
	for k in a:
		if str(a[k]) != str(b[k]):
			return String(k)
	return ""


## Поддельная идущая игра на сервере LANTERN (без настоящего Paper): комната на relay открыта, сервер «работает».
func _fake_server(on: bool) -> void:
	var s: ServerHost = app.server
	if on:
		s.edition = "java"
		s.opts = Create.server_opts("java")
		s.opts["world"] = "Наш мир"
		s.state = "running"
		s._started = s.restart_state()
		s.perf = "balance"
		s.players = PackedStringArray(["Алекс"])
		if app.relay_host.stats().state == "off":
			app.relay_host.start(Cfg.relay_url(), "both", "Наш мир", 25565, 19132, app.host_extra())
			await _wait(func(): return app.relay_host.stats().code != "", 10, "комната открыта")
	else:
		if app.relay_host.stats().state != "off":
			app.relay_host.stop()
		s.state = "idle"
		s._started = {}
		s.players = PackedStringArray()


func _dead_goto(what: String) -> void:
	await _close_overlays()
	for c in app.get_children():
		if c is Control and (c.name.begins_with("Sheet") or c.name == "RegisterOverlay"):
			c.queue_free()
	if app.relay_join.stats().state != "off":
		app.relay_join.stop()
	if app.own.active:
		app.own.stop()
	var fake := what in ["host", "gamesettings_live", "more_hosting"]
	if fake != (app.server.state == "running"):
		await _fake_server(fake)
	app._stack.clear()
	match what:
		"gamesettings_live": app.go("gamesettings", true)
		"gamesettings_own": app.go("gamesettings", true, {"own": true})
		"setup_own": app.go("setup", true, {"own": true})
		"more", "more_hosting":
			app.go("home", true)
			app._more_menu()
		"play_sheet":
			app.go("home", true)
			app.play_sheet()
		"report":
			app.go("home", true)
			Report.dialog(app, "test")
		"friends_chat": app.go("friends", true, {"chat": int(Social.friends[0].id)} if not Social.friends.is_empty() else null)
		"join_room":
			var code := ""
			for fr in Social.friends:
				var a: Variant = fr.get("activity")
				if a is Dictionary and a.get("activity", "") == "hosting":
					code = a.code
			app.go("join", true, {"input": code, "edition": "both"} if code != "" else null)
			if code != "":
				await _wait(func(): return app.relay_join.stats().state == "online", 8, "в комнате друга")
		_: app.go(what, true)
	await get_tree().create_timer(0.45).timeout


func _dead_root(what: String) -> Node:
	if what in ["more", "more_hosting", "play_sheet", "report"]:
		return app.get_child(app.get_child_count() - 1)
	return app if what == "home" else app._content


func _dead_press(c: Control) -> void:
	if c is OptionButton:
		var ob := c as OptionButton
		var j := (ob.selected + 1) % maxi(1, ob.item_count)
		while ob.is_item_separator(j) or ob.is_item_disabled(j):
			j = (j + 1) % ob.item_count
		ob.select(j)
		ob.item_selected.emit(j)
	elif c is BaseButton:
		var b := c as BaseButton
		if b.toggle_mode:
			b.button_pressed = not b.button_pressed
		else:
			b.pressed.emit()
	elif c.has_meta("tap"):
		(c.get_meta("tap") as Callable).call()


func _deadbuttons() -> int:
	if not await _wait(func(): return Social.conn in ["need_register", "online"], 20, "связь с relay"):
		return 1
	if Social.conn == "need_register":
		Social.register("Ночной_Лис")
		await _wait(func(): return Social.online(), 10, "профиль создан")
	await _close_overlays()
	var tag_file := data.path_join("autotest-tag.txt")
	FileAccess.open(tag_file, FileAccess.WRITE).store_string(Social.me.tag)
	await _wait(func(): return Social.incoming.size() >= 3, 30, "заявки в друзья")
	for u in Social.incoming.duplicate():
		Social.request("friend_accept", {"id": u.id})
	await _wait(func(): return Social.friends.size() >= 3, 10, "друзья приняты")
	await _wait(func(): return FileAccess.file_exists(tag_file + ".ok"), 30, "боты готовы")
	Social.request("eco")
	await get_tree().create_timer(1.0).timeout
	var screens := ["home", "more", "play_sheet", "create", "gamesettings", "setup_own", "join", "join_room", "friends", "friends_chat", "campfire", "quests", "shop", "profile", "settings", "report"]
	if Cfg.mobile():
		screens.append("gamesettings_own")
	else:
		screens.append_array(["backups", "syscheck", "host", "gamesettings_live", "more_hosting"])
	if Cfg.autotest_args.has("only"):
		screens = Array(String(Cfg.autotest_args.only).split(","))
	# не нажимаем то, что по-настоящему необратимо или закрывает приложение (проверено отдельно руками/другими тестами)
	var skip := ["Удалить профиль навсегда", "Выйти из LANTERN"]
	var dead := []
	var disabled := []
	var total := 0
	for scr in screens:
		await _dead_goto(scr)
		var n := _controls(_dead_root(scr)).size()
		_log("экран %s: элементов %d" % [scr, n])
		var names_seen := {}
		for i in n:
			await _dead_goto(scr)
			var list := _controls(_dead_root(scr))
			if i >= list.size():
				break
			var c: Control = list[i]
			var nm := _ctl_name(c)
			if c is BaseButton and (c as BaseButton).disabled:
				disabled.append("%s: %s" % [scr, nm])
				continue
			var txt: String = String((c as Button).text) if c is Button else ""
			if txt in skip:
				continue
			# одинаковые строки списков (десятый «Зайти») — достаточно трёх
			names_seen[nm] = int(names_seen.get(nm, 0)) + 1
			if int(names_seen[nm]) > 3:
				continue
			total += 1
			var before := _fingerprint()
			_dead_press(c)
			var why := ""
			for step in 6:
				await get_tree().create_timer(0.12).timeout
				why = _dead_changed(before, _fingerprint())
				if why != "":
					break
			if why == "":
				# вторая попытка: выбранная вкладка/цвет (нажатие на уже выбранное ничего не меняет) — сначала нажимаем соседа;
				# кнопка рядом с пустым полем («Отправить», «Добавить») — сначала вводим текст
				await _dead_goto(scr)
				list = _controls(_dead_root(scr))
				if i < list.size():
					c = list[i]
					var sib: Control = null
					for o in c.get_parent().get_children():
						if o != c and o is BaseButton and (o as Control).is_visible_in_tree() and not (o as BaseButton).disabled:
							sib = o
							break
					if sib:
						_dead_press(sib)
						await get_tree().create_timer(0.3).timeout
					if is_instance_valid(c) and c.is_inside_tree():
						for e in _dead_root(scr).find_children("*", "LineEdit", true, false):
							if (e as LineEdit).is_visible_in_tree() and (e as LineEdit).editable and (e as LineEdit).text == "":
								(e as LineEdit).text = "LUNA7"
								(e as LineEdit).text_changed.emit("LUNA7")
						await get_tree().process_frame
						before = _fingerprint()
						_dead_press(c)
						for step in 6:
							await get_tree().create_timer(0.12).timeout
							why = _dead_changed(before, _fingerprint())
							if why != "":
								break
					else:
						why = "sibling"
			if why == "":
				dead.append("%s: %s" % [scr, nm])
				_log("МЁРТВАЯ КНОПКА [%s] %s" % [scr, nm])
			elif Cfg.autotest_args.has("verbose"):
				_log("  ок [%s] %s → %s" % [scr, nm, why])
	await _fake_server(false)
	FileAccess.open(data.path_join("dead.json"), FileAccess.WRITE).store_string(JSON.stringify({"total": total, "dead": dead, "disabled": disabled}, "  "))
	_log("нажато элементов: %d, без эффекта: %d, выключенных: %d" % [total, dead.size(), disabled.size()])
	for d in disabled:
		_log("выключена: " + d)
	return 0 if dead.is_empty() else 1
