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
##   tour     — обход ВСЕХ экранов и сценариев для дизайн-скриншотов (tests/design_shots.sh), нужен relay и social_seed.js
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
func _core() -> int:
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
	_log("FAILS=%d" % fails)
	return 1 if fails else 0


# ------------------------------------------------------------------ скриншоты
func _screens() -> int:
	app.go("home", true)
	await _shot("01_home")
	app.go("setup", true, {"edition": "java"})
	await get_tree().create_timer(2.5).timeout
	await _shot("02_setup_java")
	app.go("setup", true, {"edition": "bedrock"})
	await _shot("03_setup_bedrock")
	app.go("join", true, {"edition": "both"})
	await _shot("04_join")
	app.go("settings", true)
	await _shot("05_settings")
	return 0


# ------------------------------------------------------------------ соцсеть, магазин, сезон (нужен relay и tests/social_seed.js)
func _press(text: String, in_overlay := false) -> bool:
	var root: Node = get_tree().root
	if in_overlay:
		root = app.get_child(app.get_child_count() - 1)
	for b in root.find_children("*", "Button", true, false):
		if b.is_visible_in_tree() and (b as Button).text == text and not (b as Button).disabled:
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
		Social.register("Стив")
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
		Social.register("Стив")
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
	Social.request("vanity_set", {"code": "ste-ve"})
	if not await _wait(func(): return Social.eco.get("vanity", "") == "STEVE", 5, "свой код STEVE"):
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
		return true
	return w > limit or deeper


# ------------------------------------------------------------------ дизайн-обход: все экраны и сценарии (tests/design_shots.sh)
func _find_button(root: Node, text: String) -> Button:
	for b in root.find_children("*", "Button", true, false):
		if b.is_visible_in_tree() and (b as Button).text == text and not (b as Button).disabled:
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
		e.text = "Стив"
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
	if Cfg.mobile():
		app.go("setup", true)
		await _shot("12_host_step1_phone")
		app._screen._pick_kind("own")
		await get_tree().create_timer(1.0).timeout
		await _shot("12b_host_own_phone")
		app.go("home", true)
	# --- мастер «Создать мир» (ПК)
	if not Cfg.mobile() and not Cfg.autotest_args.has("nohost"):
		app.go("setup", true)
		await _shot("13_host_step1_kind")
		var scr = app._screen
		scr._pick_kind("server")
		await _shot("13b_host_step1_edition")
		scr._pick("java")
		await _shot("14_host_step2_world")
		scr._goto(3)
		scr._eula.button_pressed = true
		await _shot("15_host_step3_launch")
		# комната без настоящего сервера: открываем её на relay и показываем экран мира
		var s: ServerHost = app.server
		s.edition = "java"
		s.opts = {"world": "Наш мир", "crossplay": true, "internet": true, "gamemode": "survival"}
		s.state = "running"
		s.last_status = "Сервер работает. Зовите друзей!"
		s.players = PackedStringArray(["Алекс", "Маша_Кубик"])
		app.relay_host.start(Cfg.relay_url(), "both", "Наш мир", 25565, 19132, app.host_extra())
		await _wait(func(): return app.relay_host.stats().code != "", 10, "комната открыта")
		app.go("host", true)
		await _shot("16_host_room")
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
	js._connect()
	await _shot("23_join_connecting")
	if not await _wait(func(): return app.relay_join.stats().state == "online", 15, "вошли в комнату друга"):
		fails += 1
	await _wait(func(): return Social.room_code == app.relay_join.code, 5, "чат комнаты открыт")
	Social.room_say("Всем привет! Я с телефона")
	await get_tree().create_timer(1.5).timeout
	await _shot("24_join_step3_in_world")
	await _scroll_down(int(900 if phone else 500))
	await _shot("25_join_where_to_find")
	await _scroll_down()
	await _shot("26_join_room_chat")
	js._qr_help()
	await _shot("27_join_qr_help")
	await _close_overlays()
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
	await _rec("connect_success", 2.2, func(): js._connect())
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
	app.go("setup", true)
	await _shot("60_own_step1")
	var scr = app._screen
	scr._pick_kind("own")
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
	app.quick_host()
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
