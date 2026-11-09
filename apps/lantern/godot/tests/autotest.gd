extends Node
## Автопроверки LANTERN: запуск с `-- --autotest <режим> [--key=value ...]`.
##   core     — QR, коды, приглашения, server.properties (без сети)
##   screens  — скриншоты всех экранов (--shots=папка)
##   java     — Paper(+Geyser) → relay → прокси друга; ждёт внешний клиент (файл ready/done)
##   bedrock  — Bedrock Dedicated Server → relay → прокси друга; то же
##   proc     — запуск процессов в папке с пробелами/скобками/&, чтение вывода, принудительная остановка (Windows: cmd/taskkill)
##   syscheck — экран «Проверка системы» (+скриншот)
##   phone    — экраны в портретном телефоне (--shots, окно 412×892), нужен relay и social_seed.js
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
		"java", "bedrock": code = await _host(Cfg.autotest)
		_: _log("неизвестный режим")
	_log("EXIT %d" % code)
	app.relay_join.stop()
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
	var ready := {"code": code, "edition": js.edition, "java_port": js.java_port, "bedrock_port": js.bedrock_port, "motd": js.motd, "name": js.name}
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
