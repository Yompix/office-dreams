class_name OwnWorld
extends Node
## «Мой мир из Minecraft»: друзья заходят в мир, который игрок сам открыл в Minecraft (без скачивания сервера).
## Bedrock (телефон, Windows): мир с «Видимостью для игроков LAN» отвечает на RakNet-пинг на UDP 19132.
## Java (ПК): «Открыть для сети» — игра объявляет мир в 224.0.2.60:4445 «[MOTD]…[/MOTD][AD]порт[/AD]».
## Найденный мир отдаём в RelayHost (тот же мост, что у запущенного сервера) — комната и код как обычно.
## Пока мир открыт, следим, что он ещё отвечает (и сколько в нём игроков — у Bedrock).
## На Android, пока игрок в Minecraft, LANTERN в фоне: включаем службу с уведомлением, чтобы система не усыпила мост.

signal changed

const MAGIC := RelayJoin.MAGIC
const LAN_GROUP := "224.0.2.60"
const LAN_PORT := 4445
const LANTERN_TAG := " — LANTERN"     # так подписывает свои объявления «Зайти к другу» — свои не находим
const FORGET_MS := 7000              # мир не отвечал столько — пропал из списка
const ANDROID_SERVICE := "com.godot.game.LanternRelayService"

var app
var active := false          # комната для своего мира открыта
var world := {}              # {key, edition, name, host, port, version, players, max, motd, seen}
var found := {}              # key -> мир (поиск)
var scanning := false
var lan_error := ""          # не удалось слушать объявления Java (порт 4445 занят)

var _udp: PacketPeerUDP
var _lan: PacketPeerUDP
var _guid := 0
var _t := 0.0
var _bport := 19132
var _nport := NetherNet.PORT
var _nn_id := 0
var probes := 0              # сколько раз уже искали (для подсказок на экране поиска)
var _was_alive := true
var _paused_at := 0
# 1.7 «Создать» на телефоне: LANTERN сразу открывает Minecraft, а мир ищет и в фоне (основной цикл Godot в фоне стоит —
# ищет отдельный поток, пока работает служба с уведомлением). Нашёл — открыл комнату и прислал «Комната открыта · код …».
var auto_pending := false
var bg_opened := false
var _bg: Thread
var _bg_stop := false


func _ready() -> void:
	var rng := RandomNumberGenerator.new()
	_guid = (rng.randi() << 31) | rng.randi()
	_bport = int(Cfg.autotest_args.get("own_bedrock_port", "19132"))
	_nport = int(Cfg.autotest_args.get("own_nn_port", str(NetherNet.PORT)))
	_nn_id = NetherNet.new_id()


## Java «Открыть для сети» есть только на ПК; на телефоне — только Bedrock.
static func java_supported() -> bool:
	return not Cfg.mobile()


# ------------------------------------------------------------------ поиск

func begin_scan() -> void:
	scanning = true
	_open_sockets()
	_t = 99.0


func end_scan() -> void:
	scanning = false
	if not active:
		_close_sockets()


## Список найденных миров (свежие сверху).
func worlds() -> Array:
	var a := found.values()
	a.sort_custom(func(x, y): return x.seen > y.seen)
	return a


## Java-мир по номеру порта из чата («Локальная игра запущена на порту 12345»), если объявление не дошло.
func add_manual_java(port: int) -> Dictionary:
	var w := {"key": "java:%d" % port, "edition": "java", "name": "Мир Java", "host": "127.0.0.1", "port": port,
		"version": "", "players": -1, "max": 0, "motd": "", "seen": Time.get_ticks_msec(), "manual": true}
	found[w.key] = w
	changed.emit()
	return w


func _open_sockets() -> void:
	if _udp == null:
		_udp = PacketPeerUDP.new()
		_udp.set_broadcast_enabled(true)
		if _udp.bind(0, "0.0.0.0") != OK:
			_udp = null
	if _lan == null and java_supported():
		_lan = PacketPeerUDP.new()
		if _lan.bind(LAN_PORT, "0.0.0.0") != OK:
			_lan = null
			lan_error = "Порт 4445 занят другой программой — мир Java не найдётся сам. Введите номер порта из чата Minecraft."
		else:
			lan_error = ""
			var joined := 0
			for i in IP.get_local_interfaces():
				if _lan.join_multicast_group(LAN_GROUP, i.name) == OK:
					joined += 1
			if joined == 0:
				lan_error = "Не удалось слушать объявления миров Java. Введите номер порта из чата Minecraft."


func _close_sockets() -> void:
	if _udp:
		_udp.close()
		_udp = null
	if _lan:
		_lan.close()
		_lan = null


func _process(d: float) -> void:
	if not scanning and not active:
		return
	_t += d
	if _t >= (1.0 if scanning else 2.5):
		_t = 0.0
		_send_pings()
	var dirty := _read()
	var now := Time.get_ticks_msec()
	for k in found.keys():
		if not found[k].get("manual", false) and now - int(found[k].seen) > FORGET_MS:
			found.erase(k)
			dirty = true
	if active:
		var a := alive()
		if a != _was_alive:
			_was_alive = a
			dirty = true
	if dirty:
		changed.emit()


func _send_pings() -> void:
	if _udp == null:
		_open_sockets()
		if _udp == null:
			return
	var pk := ping_packet(Time.get_ticks_msec(), _guid)
	_udp.set_dest_address("127.0.0.1", _bport)
	_udp.put_packet(pk)
	# если игра слушает только адрес Wi-Fi — найдём её по широковещанию (ответ должен прийти с этого же устройства)
	_udp.set_dest_address("255.255.255.255", _bport)
	_udp.put_packet(pk)
	# новые версии Bedrock (2025+) открывают мир по NetherNet: поиск на UDP 7551
	var rq := NetherNet.request(_nn_id)
	_udp.set_dest_address("127.0.0.1", _nport)
	_udp.put_packet(rq)
	_udp.set_dest_address("255.255.255.255", _nport)
	_udp.put_packet(rq)
	probes += 1


func _read() -> bool:
	var dirty := false
	var mine := _local_ips()
	var now := Time.get_ticks_msec()
	while _udp and _udp.get_available_packet_count() > 0:
		var pk := _udp.get_packet()
		var ip := _udp.get_packet_ip()
		var port := _udp.get_packet_port()
		if not mine.has(ip):
			continue    # мир на другом устройстве в той же сети — это не «мой мир»
		if pk.size() >= 48 and pk[0] != 0x1C:
			var nw := parse_nethernet(pk)
			if nw.is_empty() or int(nw.nn_id) == _joiner_nn_id():
				continue
			nw.host = ip
			nw.seen = now
			dirty = dirty or not found.has(nw.key) or found[nw.key].players != nw.players
			found[nw.key] = nw
			if active and world.key == nw.key:
				world.seen = now
				world.players = nw.players
				world.max = nw.max
			continue
		var w := parse_pong(pk)
		if w.is_empty() or int(w.guid) == _joiner_guid():
			continue
		if w.port <= 0:
			w.port = port
		w.host = ip
		w.key = "bedrock:%d" % w.port
		w.seen = now
		# 127.0.0.1 надёжнее адреса Wi-Fi (тот может смениться)
		if found.has(w.key) and found[w.key].host == "127.0.0.1" and ip != "127.0.0.1":
			w.host = "127.0.0.1"
		dirty = dirty or not found.has(w.key) or found[w.key].players != w.players
		found[w.key] = w
		if active and world.key == w.key:
			world.seen = now
			world.players = w.players
			world.max = w.max
	while _lan and _lan.get_available_packet_count() > 0:
		var pk := _lan.get_packet()
		var ip := _lan.get_packet_ip()
		if not mine.has(ip):
			continue
		var w := parse_announcement(pk.get_string_from_utf8())
		if w.is_empty() or String(w.motd).ends_with(LANTERN_TAG):
			continue
		w.host = "127.0.0.1"
		w.key = "java:%d" % w.port
		w.seen = now
		dirty = dirty or not found.has(w.key)
		found[w.key] = w
		if active and world.key == w.key:
			world.seen = now
	return dirty


func _joiner_nn_id() -> int:
	return int(app.relay_join.nn_id) if app and app.relay_join.stats().state == "online" else -1


func _joiner_guid() -> int:
	return int(app.relay_join._guid) if app and app.relay_join.stats().state == "online" else -1


static func _local_ips() -> Dictionary:
	var d := {"127.0.0.1": true}
	for a in IP.get_local_addresses():
		d[a] = true
	return d


# ------------------------------------------------------------------ комната

## Мир отвечает? (Java без объявлений — считаем, что да.)
func alive() -> bool:
	if not active:
		return false
	if world.get("manual", false) and _lan == null:
		return true
	# мост сам пингует мир Bedrock (в фоне на Android основной цикл стоит)
	var seen := maxi(int(world.get("seen", 0)), app.relay_host.world_seen() if app else 0)
	return Time.get_ticks_msec() - seen < FORGET_MS


## Ждать мир и открыть комнату самим (в том числе пока LANTERN в фоне, а игрок — в Minecraft).
func arm_auto() -> void:
	auto_pending = true
	bg_opened = false
	AppLog.add("own", "ждём мир в Minecraft: комната откроется сама (в фоне тоже)")
	if OS.get_name() != "Android":
		return
	if not OS.get_granted_permissions().has("android.permission.POST_NOTIFICATIONS"):
		OS.request_permission("android.permission.POST_NOTIFICATIONS")
	var cls = JavaClassWrapper.wrap(ANDROID_SERVICE)
	if cls == null:
		return
	cls.start("Ждём ваш мир в Minecraft", "Зайдите в свой мир — комната для друзей откроется сама.")
	cls.update(JSON.stringify({"code": "······", "ok": false, "why": "Зайдите в свой мир в Minecraft — комната откроется сама.", "world": "", "peers": [], "public": RoomControl.own_public_default(), "share": ""}))


func disarm_auto() -> void:
	if not auto_pending:
		return
	auto_pending = false
	_bg_join()
	if not active:
		_keepalive(false)


func _bg_join() -> void:
	if _bg != null:
		_bg_stop = true
		_bg.wait_to_finish()
		_bg = null
	_bg_stop = false


## Поток поиска мира, пока приложение в фоне: те же запросы, что и на экране поиска; нашли — открываем комнату.
func _bg_loop() -> void:
	AppLog.add("own", "фон: ищем мир в Minecraft")
	var t0 := Time.get_ticks_msec()
	var n := 0
	while not _bg_stop and Time.get_ticks_msec() - t0 < 30 * 60 * 1000:
		_send_pings()
		OS.delay_msec(300)
		_read()
		n += 1
		var list := worlds().filter(func(w): return w.edition == "bedrock" or java_supported())
		if not list.is_empty():
			var w: Dictionary = list[0]
			AppLog.add("own", "фон: мир найден после %d попыток — «%s», открываем комнату" % [n, w.get("name", "")])
			start(w, true)
			bg_opened = true
			# ждём код комнаты и сообщаем игроку (он сейчас в Minecraft)
			var t1 := Time.get_ticks_msec()
			while not _bg_stop and Time.get_ticks_msec() - t1 < 25000:
				var code := String(app.relay_host.stats().get("code", ""))
				if code != "":
					var cls = JavaClassWrapper.wrap(ANDROID_SERVICE) if OS.get_name() == "Android" else null
					if cls != null:
						cls.event("Комната открыта · код %s — отправьте его друзьям" % RoomCode.pretty(code), false)
					AppLog.add("own", "фон: комната открыта, код получен")
					break
				OS.delay_msec(300)
			return
		for i in 7:
			if _bg_stop:
				return
			OS.delay_msec(100)
	AppLog.add("own", "фон: поиск мира остановлен")


## bg — вызов из фонового потока: только то, что не трогает экран (остальное основной поток доделает в on_resume).
func start(w: Dictionary, bg := false) -> void:
	auto_pending = false
	if not bg and app.server.is_alive():
		app.server.stop()
	world = w.duplicate()
	world.seen = Time.get_ticks_msec()
	active = true
	_was_alive = true
	_open_sockets()
	app.relay_host.local_host = String(w.get("host", "127.0.0.1"))
	var jp := int(w.port) if w.edition == "java" else 25565
	var bp := int(w.port) if w.edition == "bedrock" and w.get("transport", "") != "nethernet" else 19132
	app.relay_host.bedrock_nn_port = _nport
	app.relay_host.probe_world = w.edition == "bedrock"
	var mx := int(w.get("max", 0))
	app.relay_host.public_card = {"title": String(w.name), "tags": [], "version": String(w.get("version", "")), "slots": mx if mx > 0 else 10}
	app.relay_host.public_on = RoomControl.own_public_default()
	app.relay_host.start(Cfg.relay_url(), w.edition, String(w.name), jp, bp, app.host_extra({"world": w.name}))
	Cfg.set_value("own", "last_edition", w.edition)
	if String(w.get("version", "")) != "":
		Cfg.set_value("own", "last_version", "%s %s" % ["Bedrock" if w.edition == "bedrock" else "Java", w.version])
	if bg:
		app.relay_host.bans = RoomControl.ban_ids()
		if OS.get_name() == "Android":
			var cls = JavaClassWrapper.wrap(ANDROID_SERVICE)
			if cls != null:
				app.relay_host.android = cls
				cls.start("Мир «%s» открыт для друзей" % world.get("name", "Мир"), "Друзья могут зайти, пока Minecraft открыт. LANTERN держит связь.")
		return
	_keepalive(true)
	app.room.room_opened()
	app.update_presence()
	changed.emit()


func stop() -> void:
	disarm_auto()
	if not active:
		return
	active = false
	app.relay_host.stop()
	app.relay_host.local_host = "127.0.0.1"
	_keepalive(false)
	app.room.room_closed()
	if not scanning:
		_close_sockets()
	app.update_presence()
	changed.emit()


## Сколько людей в мире по данным самой игры (Bedrock), -1 — неизвестно.
func players() -> int:
	return int(world.get("players", -1))


## Android: приложение вернулось из фона. Если связь с relay оборвалась — мост сам переподключится
## (тот же код комнаты, relay держит его 5 минут). Здесь — сразу проверить мир и поторопить переподключение.
func on_resume() -> void:
	_bg_join()
	if bg_opened:
		# комнату открыл фоновый поток, пока игрок был в Minecraft — доделываем то, что связано с экраном
		bg_opened = false
		app.room.room_opened()
		app.update_presence()
		changed.emit()
		app.go("host", true, {"fresh": true})
	if not active:
		return
	_t = 99.0
	var st: Dictionary = app.relay_host.stats()
	if st.state in ["off", "error"]:
		app.relay_host.start(Cfg.relay_url(), world.edition, String(world.name), int(world.port) if world.edition == "java" else 25565,
			int(world.port) if world.edition == "bedrock" and world.get("transport", "") != "nethernet" else 19132, app.host_extra({"world": world.name}), true)
	_keepalive(true)


func on_pause() -> void:
	_paused_at = Time.get_ticks_msec()
	if auto_pending and not active and _bg == null:
		_bg_stop = false
		_bg = Thread.new()
		_bg.start(_bg_loop)


# ------------------------------------------------------------------ Android: служба «мир открыт» (уведомление)

func _keepalive(on: bool) -> void:
	if OS.get_name() != "Android":
		return
	if on and not OS.get_granted_permissions().has("android.permission.POST_NOTIFICATIONS"):
		OS.request_permission("android.permission.POST_NOTIFICATIONS")
	var cls = JavaClassWrapper.wrap(ANDROID_SERVICE)
	if cls == null:
		push_warning("LANTERN: нет службы " + ANDROID_SERVICE)
		return
	if on:
		cls.start("Мир «%s» открыт для друзей" % world.get("name", "Мир"), "Друзья могут зайти, пока Minecraft открыт. LANTERN держит связь.")
	else:
		cls.stop()


## «Открыть Minecraft» (Bedrock: телефон, Windows). Нет игры — страница в магазине.
static func open_minecraft() -> bool:
	if OS.shell_open("minecraft://") == OK:
		return true
	if OS.get_name() == "Android":
		OS.shell_open("https://play.google.com/store/apps/details?id=com.mojang.minecraftpe")
	return false


# ------------------------------------------------------------------ протоколы

static func ping_packet(now: int, guid: int) -> PackedByteArray:
	var b := PackedByteArray([0x01])
	b.append_array(_be64(now))
	b.append_array(PackedByteArray(MAGIC))
	b.append_array(_be64(guid))
	return b


## RakNet unconnected pong → мир. MCPE;строка1;протокол;версия;игроков;макс;guid;строка2(название мира);режим;…;порт4;порт6;
static func parse_pong(pk: PackedByteArray) -> Dictionary:
	if pk.size() < 35 or pk[0] != 0x1C or pk.slice(17, 33) != PackedByteArray(MAGIC):
		return {}
	var guid := 0
	for i in 8:
		guid = (guid << 8) | pk[9 + i]
	var n := (pk[33] << 8) | pk[34]
	var s := pk.slice(35, 35 + n).get_string_from_utf8()
	var f := s.split(";")
	if f.size() < 6:
		return {}
	var name := f[7].strip_edges() if f.size() > 7 else ""
	if name == "":
		name = f[1].strip_edges()
	return {"edition": "bedrock", "name": name if name != "" else "Мир Bedrock", "host_name": f[1].strip_edges(),
		"version": f[3], "players": int(f[4]), "max": int(f[5]), "mode": f[8] if f.size() > 8 else "",
		"port": int(f[10]) if f.size() > 10 and f[10].is_valid_int() else 0, "motd": s, "guid": guid}


## Ответ NetherNet на 7551 → мир (новые версии Bedrock). {} — не ответ на поиск.
static func parse_nethernet(pk: PackedByteArray) -> Dictionary:
	var d := NetherNet.decode(pk)
	if d.is_empty() or d.id != NetherNet.ID_RESPONSE:
		return {}
	var sd := NetherNet.parse_server_data(d.appdata)
	var name: String = sd.get("level", "").strip_edges()
	if name == "":
		name = String(sd.get("server", "")).strip_edges()
	return {"edition": "bedrock", "transport": "nethernet", "key": "nethernet:%d" % int(d.sender), "nn_id": int(d.sender),
		"name": name if name != "" else "Мир Bedrock", "host_name": String(sd.get("server", "")), "version": String(sd.get("version", "")),
		"protocol": int(sd.get("protocol", 0)), "players": int(sd.get("players", -1)), "max": int(sd.get("max", 0)), "port": NetherNet.PORT, "motd": ""}


## «[MOTD]Стив - Мой мир[/MOTD][AD]54321[/AD]» → мир Java.
static func parse_announcement(s: String) -> Dictionary:
	var m := RegEx.create_from_string("\\[MOTD\\](.*)\\[/MOTD\\]\\[AD\\](?:.*:)?(\\d+)\\[/AD\\]").search(s)
	if m == null:
		return {}
	var port := int(m.get_string(2))
	if port <= 0 or port > 65535:
		return {}
	var motd := m.get_string(1)
	var name := motd
	var dash := motd.find(" - ")
	if dash > 0:
		name = motd.substr(dash + 3)
	return {"edition": "java", "name": name.strip_edges() if name.strip_edges() != "" else "Мир Java", "host_name": motd.substr(0, dash) if dash > 0 else "",
		"version": "", "players": -1, "max": 0, "port": port, "motd": motd}


static func _be64(v: int) -> PackedByteArray:
	var b := PackedByteArray()
	b.resize(8)
	for i in 8:
		b[7 - i] = (v >> (8 * i)) & 255
	return b
