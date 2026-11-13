class_name RelayJoin
extends RefCounted
## Вход к другу по коду комнаты: мир появляется в списке игры сам.
## Java: объявление по LAN (224.0.2.60:4445) + локальный TCP-вход → relay.
## Bedrock: UDP 19132, ответ на LAN-пинги RakNet (MOTD друга) + пересылка датаграмм → relay.
## Работает в своём потоке, без узлов сцены — общий код ПК и Android.

const MAGIC := [0x00, 0xff, 0xff, 0x00, 0xfe, 0xfe, 0xfe, 0xfe, 0xfd, 0xfd, 0xfd, 0xfd, 0x12, 0x34, 0x56, 0x78]
const LAN_GROUP := "224.0.2.60"
const LAN_PORT := 4445
const PING_SID := 1
const NN_SID := 1000000      # поток карточки мира NetherNet (новые версии Bedrock)
const UDP_IDLE_MS := 60000
const BACKLOG_MAX := 2 << 20

var relay_url := ""
var code := ""
var want_java := true
var want_bedrock := true
var java_port := 25565       # предпочитаемый локальный порт Java (если занят — прошлый, потом любой свободный)
var java_port_alt := 0       # 1.7: порт с прошлого раза — адрес «localhost:порт» у игрока не меняется
var bedrock_port := 19132    # Bedrock ищет LAN-миры только на 19132
var nn_port := NetherNet.PORT  # Bedrock 1.21.1xx+ ищет LAN-миры по NetherNet на 7551
var nn_id := 0               # наш id NetherNet — чтобы «Мой мир» не нашёл сам себя
var password := ""           # пароль игры (1.5), если хозяин его задал
var token := ""              # профиль LANTERN (для заданий и «Фонаря»), можно пусто
var _want_like := false
var lan_direct := true       # 1.8: «Прямое соединение по Wi-Fi» (LanDirect)

var _thread: Thread
var _run := false
var _mutex := Mutex.new()
var _stats := {"state": "off", "error": "", "name": "", "edition": "", "java_port": 0, "bedrock_port": 0,
	"bedrock_error": "", "java_error": "", "pong": false, "motd": "", "streams": 0, "rx": 0, "tx": 0, "host": true,
	"host_version": "", "host_protocol": 0, "client_protocol": 0, "nethernet": false, "nn_error": "", "nn_clients": 0,
	"http_joins": 0, "connects": 0, "in_game": false, "resumed": 0, "direct": false, "direct_used": false, "rtt": 0}
var _guid := 0


func start(url: String, room_code: String, java := true, bedrock := true, jport := 25565, bport := 19132) -> void:
	stop()
	relay_url = url
	code = room_code
	want_java = java
	want_bedrock = bedrock
	java_port = jport
	bedrock_port = bport
	var rng := RandomNumberGenerator.new()
	_guid = (rng.randi() << 31) | rng.randi()
	nn_id = NetherNet.new_id()
	_run = true
	_st("state", "connecting")
	_thread = Thread.new()
	_thread.start(_loop)


func like() -> void:
	_want_like = true


func stop() -> void:
	_run = false
	if _thread and _thread.is_started():
		_thread.wait_to_finish()
	_thread = null
	_st("state", "off")


func stats() -> Dictionary:
	_mutex.lock()
	var d := _stats.duplicate()
	_mutex.unlock()
	return d


func _st(k: String, v: Variant) -> void:
	_mutex.lock()
	_stats[k] = v
	_mutex.unlock()


func _add(k: String, v: int) -> void:
	_mutex.lock()
	_stats[k] += v
	_mutex.unlock()


static func announcement(text: String, port: int) -> String:
	return "[MOTD]%s[/MOTD][AD]%d[/AD]" % [text.replace("[", "(").replace("]", ")"), port]


func _loop() -> void:
	var link := RelayLink.new()
	AppLog.add("join", "вход по коду: java %s, bedrock %s, relay %s" % [str(want_java), str(want_bedrock), relay_url])
	if link.open(relay_url) != OK:
		_st("state", "error")
		_st("error", L.t("неверный адрес сервера-посредника: ") + relay_url)
		AppLog.add("join", "неверный адрес relay")
		return
	var joined := false
	var ever_joined := false
	var my_peer := ""
	var edition := ""
	var name := ""
	var tcp_server: TCPServer = null
	var lan: PacketPeerUDP = null
	var udp: PacketPeerUDP = null
	var streams := {}         # sid -> {kind, tcp|ip+port, pending, last, key, detached?, wait_resume?}
	var by_client := {}       # "ip:port" -> sid (bedrock)
	var next_sid := 2
	var pong_body := PackedByteArray()
	var last_ad := 0
	var last_ping := 0
	var last_rping := 0
	var host_here := true
	var nn_disc: PacketPeerUDP = null
	var nn_app := PackedByteArray()
	var nn_cids := {}         # id соединения WebRTC -> sid
	var last_ninfo := 0
	var http_srv: TCPServer = null   # HTTP-вход новых Bedrock на TCP <порт Bedrock> (Geyser: «Серверы» → адрес)
	var http_conns: Array = []
	var link_on := true       # есть ли сейчас соединение с relay (или попытка)
	var lost_t := 0           # когда пропала связь (0 — есть)
	var next_try := 0
	var backoff := 500
	var link_t0 := Time.get_ticks_msec()
	var first_t := link_t0
	var restart_hint := false
	var last_rx := link_t0
	var rng := RandomNumberGenerator.new()
	var last_busy := 0
	var n_streams := -1
	# прямое соединение по Wi-Fi: ключ и порты от relay, адрес хозяина — из ответа на пробу
	var ld := {"on": lan_direct and String(Cfg.autotest_args.get("lan", "1")) != "0", "k": "", "tcp": 0, "udp": 0, "probe": 0, "addr": "",
		"nonce": "", "tries": 0, "next": 0, "sock": null, "fail_tcp": 0, "fail_udp": 0, "told": false}
	while _run:
		var now := Time.get_ticks_msec()
		var busy := false
		# --- переподключение к relay (1.6): игра на этом устройстве не отключается, если связь вернулась быстро
		if not link_on:
			if now - lost_t > RECONNECT_MS:
				AppLog.add("join", "связь не вернулась за %d с — выходим" % (RECONNECT_MS / 1000))
				_st("state", "error")
				_st("error_code", "lost")
				_st("error", L.t("Связь с LANTERN пропала и не вернулась. Проверьте интернет и зайдите снова."))
				break
			if now >= next_try:
				link = RelayLink.new()
				link.open(relay_url)
				link_on = true
				link_t0 = now
				last_rx = now
			else:
				_pump_direct(ld, streams, by_client, udp, null, rng, now)
				OS.delay_usec(1000)
				continue
		var evs: Array = link.poll()
		if joined and now - last_rx > 25000:     # ping каждые 10 с; было 40 с
			AppLog.add("join", "relay молчит %d с — переподключаемся" % ((now - last_rx) / 1000))
			link.ws.close()
			evs.append({"k": "closed", "code": -1, "reason": "timeout", "was_open": true})
		elif not joined and now - link_t0 > 10000:
			AppLog.add("join", "relay не отвечает 10 с")
			link.ws.close()
			evs.append({"k": "closed", "code": -1, "reason": "connect timeout", "was_open": link.is_open()})
		for ev in evs:
			if not link_on:
				break
			if ev.k != "closed":
				last_rx = now
			match ev.k:
				"open":
					var jm := {"t": "join", "v": RelayLink.PROTO, "code": code, "token": token, "password": password}
					if ld.on:
						jm["lan"] = 1
					ld.told = false
					if my_peer != "":
						jm["peer"] = my_peer
					link.send_json(jm)
					AppLog.add("join", "relay: соединение открыто за %d мс%s" % [now - link_t0, " (возвращаемся)" if ever_joined else ""])
				"closed":
					var reason := String(ev.get("reason", ""))
					AppLog.add("join", "relay: связь закрыта (код %s, %s, была открыта %s, %d с)" % [str(ev.code), reason, str(ev.was_open), (now - link_t0) / 1000])
					if not _run:
						break
					if reason in ["kicked", "banned_by_host"] or reason == "room closed":
						_st("state", "error")
						_st("error", RelayLink.error_text(reason if reason != "room closed" else "room_closed"))
						_run = false
						break
					# первый вход во время выкладки relay (перезапуск, «зависшее» подключение): пробуем ещё ~25 с, а не сразу ошибка
					if not ever_joined and (int(ev.code) in [4012, 1012] or restart_hint or reason == "connect timeout" or not ev.was_open) \
							and now - first_t < 25000:
						link_on = false
						lost_t = now   # для проверки RECONNECT_MS (первый вход ограничен 25 с выше)
						next_try = now + (300 if int(ev.code) in [4012, 1012] or restart_hint else backoff) + rng.randi_range(0, 300)
						backoff = min(backoff * 2, 4000)
						restart_hint = false
						continue
					if not ever_joined:
						_st("state", "error")
						_st("error_code", "unreachable" if not ev.was_open else "lost")
						_st("error", L.t("связь с сервером-посредником потеряна") if ev.was_open else L.t("сервер-посредник недоступен — проверьте интернет"))
						_run = false
						break
					# была игра — держим соединения игры и переподключаемся
					joined = false
					link_on = false
					if lost_t == 0:
						lost_t = now
					next_try = now + (200 if int(ev.code) in [4012, 1012] or restart_hint else backoff) + rng.randi_range(0, 300)
					restart_hint = false
					backoff = min(backoff * 2, 4000)
					_detach_all(streams, by_client, now)
					_st("state", "reconnecting")
					_st("error", L.t("Связь прервалась — восстанавливаем…"))
				"msg":
					var m: Dictionary = ev.m
					match String(m.get("t", "")):
						"restart":
							restart_hint = true
						"joined":
							joined = true
							edition = m.edition
							name = m.name
							host_here = m.get("host", true)
							my_peer = String(m.get("peer", my_peer))
							_lan_offer(ld, m.get("lan"), rng, now)
							_st("name", name)
							_st("edition", edition)
							_st("host", host_here)
							if ever_joined:
								AppLog.add("join", "снова в комнате через %d мс после обрыва; хозяин %s" % [now - lost_t, "на месте" if host_here else "ещё не вернулся"])
								lost_t = 0
								backoff = 500
								_st("state", "online")
								_st("error", "")
								if host_here:
									_reopen(link, streams, udp != null, now)
								continue
							ever_joined = true
							lost_t = 0
							backoff = 500
							AppLog.add("join", "в комнате: %s, издание %s, хозяин %s" % [name, edition, "на месте" if host_here else "отошёл"])
							if want_java and edition in ["java", "both"]:
								tcp_server = TCPServer.new()
								if tcp_server.listen(java_port, "*") != OK and (java_port_alt <= 0 or java_port_alt == java_port or tcp_server.listen(java_port_alt, "*") != OK) and tcp_server.listen(0, "*") != OK:
									_st("java_error", L.t("не удалось открыть локальный порт"))
									AppLog.add("join", "не удалось открыть локальный порт Java")
									tcp_server = null
								else:
									_st("java_port", tcp_server.get_local_port())
									lan = PacketPeerUDP.new()
									lan.set_dest_address(LAN_GROUP, LAN_PORT)
									AppLog.add("join", "Java: мир объявлен в «Сетевой игре», порт %d" % tcp_server.get_local_port())
							if want_bedrock and edition in ["bedrock", "both"]:
								udp = PacketPeerUDP.new()
								udp.set_broadcast_enabled(true)
								if udp.bind(bedrock_port, "*") != OK:
									_st("bedrock_error", L.t("порт %d занят — закройте открытый по сети мир или сервер Bedrock на этом устройстве") % bedrock_port)
									AppLog.add("join", "Bedrock: порт %d занят" % bedrock_port)
									udp = null
								else:
									_st("bedrock_port", bedrock_port)
									link.send_json({"t": "open", "sid": PING_SID, "kind": "udp"})
									http_srv = TCPServer.new()
									if http_srv.listen(bedrock_port, "*") != OK:
										http_srv = null
										AppLog.add("join", "Bedrock: TCP %d (вход «Серверы») занят" % bedrock_port)
									AppLog.add("join", "Bedrock: слушаем UDP/TCP %d" % bedrock_port)
								link.send_json({"t": "open", "sid": NN_SID, "kind": "udp"})
							_st("state", "online")
						"error":
							var e := String(m.get("error", ""))
							if ever_joined and e == "not_found":
								# relay перезапустился, а хозяин ещё не вернулся — пробуем снова
								AppLog.add("join", "комната пока не найдена (relay перезапустился?) — ждём хозяина")
								link.ws.close()
								link_on = false
								next_try = now + 1500
								continue
							AppLog.add("join", "relay: ошибка " + e)
							_st("state", "error")
							_st("error_code", e)
							_st("error", RelayLink.error_text(e))
							_run = false
						"pong":
							_st("rtt", maxi(1, Time.get_ticks_msec() - int(m.get("ts", now))))
						"ack":
							var s: Variant = streams.get(int(m.sid))
							if s == null or s.kind != "tcp":
								continue
							RelStream.on_ack(s, int(m.get("n", 0)))
							if m.get("r", 0) and s.get("wait_resume", false):
								if RelStream.replay(link, int(m.sid), s, int(m.n)):
									s.wait_resume = false
									AppLog.add("join", "поток %d возобновлён без разрыва игры (%s)" % [int(m.sid), RelStream.info(s)])
									_add("resumed", 1)
								else:
									AppLog.add("join", "поток %d: возобновить не вышло (%s)" % [int(m.sid), RelStream.info(s)])
									link.send_json({"t": "close", "sid": int(m.sid)})
									_close_stream(s, by_client)
									streams.erase(int(m.sid))
						"close":
							var sid := int(m.sid)
							if sid == PING_SID:
								if udp and host_here:
									link.send_json({"t": "open", "sid": PING_SID, "kind": "udp"})
							elif sid == NN_SID:
								if want_bedrock and host_here:
									link.send_json({"t": "open", "sid": NN_SID, "kind": "udp"})
							elif streams.has(sid):
								var s: Dictionary = streams[sid]
								if m.get("lost", false) and _detach(s, now):
									AppLog.add("join", "поток %d: хозяин потерял связь — ждём" % sid)
								else:
									_log_close(sid, s, L.t("хозяин закрыл (%s)") % (L.t("ждал и не дождался") if s.get("wait_resume", false) else L.t("сервер/игра закрыли соединение")))
									_close_stream(s, by_client)
									streams.erase(sid)
						"host_gone":
							host_here = false
							_st("host", false)
							pong_body = PackedByteArray()
							_st("pong", false)
							AppLog.add("join", "хозяин потерял связь с relay — ждём его")
							_detach_all(streams, by_client, now)
						"host_back":
							host_here = true
							_st("host", true)
							AppLog.add("join", "хозяин вернулся")
							_lan_offer(ld, m.get("lan"), rng, now)
							if udp:
								link.send_json({"t": "open", "sid": PING_SID, "kind": "udp"})
							if want_bedrock and edition in ["bedrock", "both"]:
								link.send_json({"t": "open", "sid": NN_SID, "kind": "udp"})
							_reopen(link, streams, udp != null, now)
				"bin":
					busy = true
					_add("rx", ev.data.size())
					if ev.sid == PING_SID:
						var pk: PackedByteArray = ev.data
						if pk.size() > 35 and pk[0] == 0x1C:
							var motd := pk.slice(35).get_string_from_utf8()
							pong_body = _rewrite_motd(motd).to_utf8_buffer()
							if not stats().pong:
								AppLog.add("join", "Bedrock: сервер друга ответил на пинг: " + motd.substr(0, 120))
							_st("pong", true)
							_st("motd", motd)
							var mf := motd.split(";")
							if mf.size() > 3:
								_st("host_protocol", int(mf[2]))
								_st("host_version", mf[3])
						continue
					if ev.sid == NN_SID:
						var pk: PackedByteArray = ev.data
						if pk.size() > 2 and pk[0] == NetherNet.FRAME and pk[1] == NetherNet.F_INFO:
							nn_app = pk.slice(2)
							if not stats().nethernet:
								_st("nethernet", true)
								var sd := NetherNet.parse_server_data(nn_app)
								if not sd.is_empty():
									_st("host_version", sd.version)
									_st("host_protocol", sd.protocol)
									_st("motd", "%s;%s" % [sd.server, sd.level])
								AppLog.add("join", "NetherNet: карточка мира хозяина получена (%s)" % str(sd.get("version", "?")))
							if nn_disc == null:
								nn_disc = PacketPeerUDP.new()
								nn_disc.set_broadcast_enabled(true)
								if nn_disc.bind(nn_port, "*") != OK:
									nn_disc = null
									_st("nn_error", L.t("порт %d занят — закройте открытый по сети мир Minecraft на этом устройстве") % nn_port)
									AppLog.add("join", "NetherNet: порт %d занят" % nn_port)
								else:
									_st("nn_error", "")
						continue
					var s: Variant = streams.get(ev.sid)
					if s == null:
						continue
					s.last = now
					if (s.kind == "nn" or s.kind == "http") and ev.data.size() >= 2 and ev.data[0] == NetherNet.FRAME and ev.data[1] == NetherNet.F_HTTP:
						if _http_reply(s, ev.data.slice(2)) and s.kind == "http":
							link.send_json({"t": "close", "sid": ev.sid})
							streams.erase(ev.sid)
						continue
					if s.kind == "http":
						continue
					if s.kind == "nn":
						var pk: PackedByteArray = ev.data
						if pk.size() < 2 or pk[0] != NetherNet.FRAME:
							continue
						if pk[1] == NetherNet.F_DATA:
							if not s.get("data_seen", false):
								s.data_seen = true
								AppLog.add("join", "NetherNet: поток %d — пошли данные игры" % ev.sid)
							s.rb = int(s.get("rb", 0)) + pk.size()
							if s.rb > 100000 and not s.get("big", false):
								s.big = true
								AppLog.add("join", "NetherNet: игрок в мире (поток %d)" % ev.sid)
								_st("in_game", true)
							NetherNet.deliver(s.sess, pk.slice(2))
						elif pk[1] == NetherNet.F_SIGNAL and nn_disc:
							for t in NetherNet.remote_signal(s.sess, pk.slice(2).get_string_from_utf8()):
								nn_disc.set_dest_address(s.ip, s.port)
								nn_disc.put_packet(NetherNet.message(nn_id, s.peer, t))
						continue
					if s.kind == "tcp":
						s.pending.append_array(ev.data)
						RelStream.on_recv(s, ev.data.size())
						if s.recv > 50000 and not s.get("big", false):
							s.big = true
							AppLog.add("join", "Java: игрок в мире (поток %d)" % ev.sid)
							_st("in_game", true)   # пошёл мир (чанки) — игрок в игре
					elif udp:
						s.rb = int(s.get("rb", 0)) + ev.data.size()
						if s.rb > 100000 and not s.get("big", false):
							s.big = true
							AppLog.add("join", "Bedrock: игрок в мире (поток %d)" % ev.sid)
							_st("in_game", true)
						udp.set_dest_address(s.ip, s.port)
						udp.put_packet(ev.data)
		if not joined:
			if not _run:
				break
			_pump_direct(ld, streams, by_client, udp, null, rng, now)
			OS.delay_usec(1000 if ever_joined else 2000)
			continue
		busy = _lan_probe(ld, now) or busy
		if ld.addr != "" and not ld.told and stats().direct_used:
			ld.told = true
			link.send_json({"t": "lan_on", "on": true})
		if _want_like:
			_want_like = false
			link.send_json({"t": "like"})
		if now - last_ping > 10000:
			last_ping = now
			link.send_json({"t": "ping", "ts": now})
		# --- Java: объявление и новые подключения
		if lan and now - last_ad > 1500:
			last_ad = now
			lan.put_packet(announcement(name + " — LANTERN", tcp_server.get_local_port()).to_utf8_buffer())
		if tcp_server:
			while tcp_server.is_connection_available():
				var c := tcp_server.take_connection()
				c.set_no_delay(true)
				var sid := next_sid
				next_sid += 1
				_add("connects", 1)
				busy = true
				if ld.addr != "" and int(ld.tcp) > 0 and int(ld.fail_tcp) < 2:
					# хозяин в этой же сети: игра идёт к его серверу напрямую, relay не участвует
					var d := StreamPeerTCP.new()
					d.connect_to_host(String(ld.addr), int(ld.tcp))
					streams[sid] = {"kind": "dtcp", "tcp": c, "d": d, "last": now, "t0": now, "up": false, "to_d": PackedByteArray(), "to_c": PackedByteArray(), "rb": 0}
					AppLog.add("join", "Java: игра подключилась (поток %d) — пробуем напрямую по Wi-Fi" % sid)
					continue
				var s := {"kind": "tcp", "tcp": c, "last": now, "t0": now, "key": _key(rng)}
				RelStream.init(s, false)
				streams[sid] = s
				AppLog.add("join", "Java: игра подключилась (поток %d)" % sid)
				link.send_json({"t": "open", "sid": sid, "kind": "tcp", "key": s.key, "rx": 0})
		# --- Bedrock: пинги и датаграммы
		if udp:
			if host_here and now - last_rping > 2000:
				last_rping = now
				link.send_bin(PING_SID, _ping_packet(now))
			while udp.get_available_packet_count() > 0:
				busy = true
				var pk := udp.get_packet()
				var cip := udp.get_packet_ip()
				var cport := udp.get_packet_port()
				if pk.is_empty():
					continue
				if (pk[0] == 0x01 or pk[0] == 0x02) and pk.size() >= 25:
					if pong_body.size() > 0:
						udp.set_dest_address(cip, cport)
						udp.put_packet(_pong_packet(pk.slice(1, 9), pong_body))
					continue
				if not host_here:
					continue
				var key := "%s:%d" % [cip, cport]
				var sid: int = by_client.get(key, 0)
				if sid == 0:
					sid = next_sid
					next_sid += 1
					by_client[key] = sid
					_add("connects", 1)
					var du: PacketPeerUDP = null
					if ld.addr != "" and int(ld.udp) > 0 and int(ld.fail_udp) < 2:
						du = PacketPeerUDP.new()
						if du.bind(0, "0.0.0.0") != OK or du.set_dest_address(String(ld.addr), int(ld.udp)) != OK:
							du = null
					if du:
						streams[sid] = {"kind": "dudp", "ip": cip, "port": cport, "key": key, "du": du, "last": now, "t0": now, "got": false, "rb": 0, "held": []}
						AppLog.add("join", "Bedrock: игра подключается по RakNet (поток %d) — пробуем напрямую по Wi-Fi" % sid)
					else:
						streams[sid] = {"kind": "udp", "ip": cip, "port": cport, "key": key, "rkey": _key(rng), "last": now, "t0": now}
						link.send_json({"t": "open", "sid": sid, "kind": "udp", "key": streams[sid].rkey})
						AppLog.add("join", "Bedrock: игра подключается по RakNet (поток %d)" % sid)
				var us: Dictionary = streams[sid]
				us.last = now
				if us.kind == "dudp":
					if (pk[0] & 0x80) and pk.size() > 12:
						var dcp := client_protocol(pk)
						if dcp > 0:
							_st("client_protocol", dcp)
					us.du.put_packet(pk)
					if not us.got and us.held.size() < 8:
						us.held.append(pk)       # если напрямую не ответят — последний пакет уйдёт через relay
					_add("tx", pk.size())
					continue
				if us.get("detached", false):
					continue
				if (pk[0] & 0x80) and pk.size() > 12:
					var cp := client_protocol(pk)
					if cp > 0:
						if int(stats().client_protocol) != cp:
							AppLog.add("join", "Bedrock: версия протокола игры %d" % cp)
						_st("client_protocol", cp)
				link.send_bin(sid, pk)
				_add("tx", pk.size())
		# --- HTTP-вход новых Bedrock: принять запрос целиком → хозяину кадром H
		if http_srv:
			while http_srv.is_connection_available():
				var c := http_srv.take_connection()
				c.set_no_delay(true)
				http_conns.append({"tcp": c, "buf": PackedByteArray(), "t0": now, "cont": false})
		for hc in http_conns.duplicate():
			var t: StreamPeerTCP = hc.tcp
			t.poll()
			if t.get_status() != StreamPeerTCP.STATUS_CONNECTED or now - int(hc.t0) > 15000:
				if hc.buf.size() > 0 or now - int(hc.t0) > 15000:
					AppLog.add("join", "HTTP-вход: соединение игры закрыто до запроса (%d байт)" % hc.buf.size())
				t.disconnect_from_host()
				http_conns.erase(hc)
				continue
			var n := t.get_available_bytes()
			if n <= 0:
				continue
			busy = true
			hc.buf.append_array(t.get_partial_data(n)[1])
			if hc.buf.size() > 0 and hc.buf[0] == 0x16:
				AppLog.add("join", "HTTP-вход: игра пробует https:// — отказываем, она повторит по http://")
				t.disconnect_from_host()   # TLS (https://) — у сервера LANTERN его нет, игра попробует http://
				http_conns.erase(hc)
				continue
			var rq := NetherNet.http_parse(hc.buf)
			if rq.is_empty():
				if not hc.cont and String(NetherNet.http_parse(hc.buf + PackedByteArray([13, 10, 13, 10])).get("headers", {}).get("expect", "")).to_lower() == "100-continue":
					hc.cont = true
					t.put_data("HTTP/1.1 100 Continue\r\n\r\n".to_utf8_buffer())
				continue
			http_conns.erase(hc)
			var rl := String(rq.line).split(" ")
			var path := rl[1] if rl.size() > 1 else "/"
			AppLog.add("join", "HTTP-вход: %s %s (%d байт)" % [rl[0], path.get_base_dir() if path.begins_with("/v1/join/") else path, String(rq.body.get_string_from_utf8()).length()])
			if not host_here:
				t.put_data(NetherNet.http_response(503, "Service Unavailable", "text/plain", "host is away".to_utf8_buffer()))
				t.disconnect_from_host()
				continue
			var rb: PackedByteArray = rq.body
			var body := rb.get_string_from_utf8()
			var sid := next_sid
			next_sid += 1
			var st := {"kind": "http", "http": t, "last": now, "post": false, "path": path.get_base_dir() if path.begins_with("/v1/join/") else path}
			if rl[0] == "POST" and path.begins_with("/v1/join/"):
				st.kind = "nn"
				st.post = true
				st.sess = NetherNet.new_session(path.get_file())
				st.rkey = _key(rng)
				NetherNet.http_sdp_local(st.sess, "CONNECTREQUEST", body)
				_add("http_joins", 1)
				_add("connects", 1)
			streams[sid] = st
			var om := {"t": "open", "sid": sid, "kind": "udp"}
			if st.has("rkey"):
				om["key"] = st.rkey
			link.send_json(om)
			link.send_bin(sid, NetherNet.frame(NetherNet.F_HTTP, JSON.stringify({"m": rl[0], "p": path, "h": rq.headers, "b": body}).to_utf8_buffer()))
		# --- NetherNet: карточка мира у хозяина, поиск и сигналы от Minecraft этого устройства
		if want_bedrock and host_here and edition in ["bedrock", "both"] and now - last_ninfo > 2000:
			last_ninfo = now
			link.send_bin(NN_SID, NetherNet.frame(NetherNet.F_INFO, PackedByteArray()))
		while nn_disc and nn_disc.get_available_packet_count() > 0:
			busy = true
			var pk := nn_disc.get_packet()
			var cip := nn_disc.get_packet_ip()
			var cport := nn_disc.get_packet_port()
			var d := NetherNet.decode(pk)
			if d.is_empty() or int(d.sender) == nn_id:
				continue
			if d.id == NetherNet.ID_REQUEST:
				if host_here and not nn_app.is_empty():
					nn_disc.set_dest_address(cip, cport)
					nn_disc.put_packet(NetherNet.response(nn_id, nn_app))
					if not stats().get("nn_seen", false):
						_st("nn_seen", true)
						AppLog.add("join", "NetherNet: Minecraft на этом устройстве ищет миры — показали мир друга")
			elif d.id == NetherNet.ID_MESSAGE and int(d.recipient) == nn_id and host_here:
				var text := String(d.text)
				var p := NetherNet.split_signal(text)
				if p.size() < 3:
					continue
				var sid: int = nn_cids.get(p[1], 0)
				if sid == 0 or not streams.has(sid):
					if p[0] != "CONNECTREQUEST":
						continue
					sid = next_sid
					next_sid += 1
					nn_cids[p[1]] = sid
					streams[sid] = {"kind": "nn", "sess": NetherNet.new_session(p[1]), "ip": cip, "port": cport, "peer": int(d.sender), "last": now, "t0": now, "rkey": _key(rng)}
					link.send_json({"t": "open", "sid": sid, "kind": "udp", "key": streams[sid].rkey})
					_add("connects", 1)
					AppLog.add("join", "NetherNet: игра подключается через «Друзья» (поток %d)" % sid)
				var s: Dictionary = streams[sid]
				s.ip = cip
				s.port = cport
				s.last = now
				NetherNet.note_local_signal(s.sess, text)
				AppLog.add("join", "NetherNet: сигнал %s" % p[0])
				link.send_bin(sid, NetherNet.frame(NetherNet.F_SIGNAL, text.to_utf8_buffer()))
		# --- прямые соединения (мимо relay), потом TCP → relay и уборка
		busy = _pump_direct(ld, streams, by_client, udp, link, rng, now) or busy
		for sid in streams.keys():
			var s: Dictionary = streams[sid]
			if s.kind == "dtcp" or s.kind == "dudp":
				continue
			if s.get("detached", false) and now - int(s.det_t) > RelStream.DETACH_MS:
				_log_close(sid, s, L.t("связь не вернулась"))
				_close_stream(s, by_client)
				streams.erase(sid)
				continue
			if s.kind == "tcp":
				var t: StreamPeerTCP = s.tcp
				t.poll()
				if t.get_status() != StreamPeerTCP.STATUS_CONNECTED or not RelStream.flush(s, t):
					_log_close(sid, s, L.t("игра закрыла соединение") if t.get_status() != StreamPeerTCP.STATUS_CONNECTED else L.t("игра не принимает данные"))
					if not s.get("detached", false):
						link.send_json({"t": "close", "sid": sid})
					_close_stream(s, by_client)
					streams.erase(sid)
					continue
				if link.backlog() < BACKLOG_MAX and RelStream.can_read(s):
					var sent := RelStream.pump_out(link, sid, s, t)
					if sent > 0:
						busy = true
						_add("tx", sent)
				if not s.get("detached", false):
					RelStream.maybe_ack(link, sid, s, now)
			elif s.get("detached", false):
				continue
			elif s.kind == "nn":
				for pk in NetherNet.pump(s.sess):
					busy = true
					s.last = now
					link.send_bin(sid, NetherNet.frame(NetherNet.F_DATA, pk))
					_add("tx", pk.size())
				if now - s.last > UDP_IDLE_MS:
					AppLog.add("join", "NetherNet: поток %d закрыт — тишина %d с" % [sid, UDP_IDLE_MS / 1000])
					link.send_json({"t": "close", "sid": sid})
					_close_stream(s, by_client)
					streams.erase(sid)
			elif now - s.last > UDP_IDLE_MS:
				if s.kind == "udp":
					AppLog.add("join", "Bedrock: поток %d закрыт — тишина %d с, был %d с" % [sid, UDP_IDLE_MS / 1000, (now - int(s.get("t0", now))) / 1000])
				link.send_json({"t": "close", "sid": sid})
				_close_stream(s, by_client)
				streams.erase(sid)
		if streams.size() != n_streams:
			n_streams = streams.size()
			_st("streams", n_streams)
		if busy:
			last_busy = Time.get_ticks_usec()
			link.flush()
		else:
			RelStream.idle_wait(Time.get_ticks_usec(), last_busy, not streams.is_empty())
	for sid in streams:
		_close_stream(streams[sid], by_client)
	if tcp_server:
		tcp_server.stop()
	if udp:
		udp.close()
	if lan:
		lan.close()
	if nn_disc:
		nn_disc.close()
	if ld.sock:
		ld.sock.close()
	if http_srv:
		http_srv.stop()
	for hc in http_conns:
		hc.tcp.disconnect_from_host()
	link.close()
	AppLog.add("join", "вход завершён: " + String(stats().get("error", "")))
	if stats().state != "error":
		_st("state", "off")


const RECONNECT_MS := 180000   # выкладка relay на Railway идёт 1–2 мин; комната на relay ждёт хозяина 5 мин


static func _key(rng: RandomNumberGenerator) -> String:
	return "%08x%08x" % [rng.randi(), rng.randi()]


## Связь пропала: потоки с ключом ждут (соединение игры на этом устройстве не закрываем). false — закрыть.
static func _detach(s: Dictionary, now: int) -> bool:
	if s.kind == "http" or (s.kind == "tcp" and s.get("norel", false)) or (s.kind != "tcp" and not s.has("rkey")):
		return false
	if not s.get("detached", false):
		s.detached = true
		s.det_t = now
	return true


func _detach_all(streams: Dictionary, by_client: Dictionary, now: int) -> void:
	var kept := 0
	for sid in streams.keys():
		var s: Dictionary = streams[sid]
		if s.kind == "dtcp" or s.kind == "dudp":
			continue             # прямое соединение от relay не зависит — просто продолжает работать
		if _detach(s, now):
			kept += 1
		else:
			_log_close(sid, s, L.t("связь пропала"))
			_close_stream(s, by_client)
			streams.erase(sid)
	if kept > 0:
		AppLog.add("join", "ждут возврата связи потоков: %d" % kept)


## Снова есть связь и хозяин: открыть потоки заново с прежними ключами — хозяин продолжит прежние соединения.
func _reopen(link: RelayLink, streams: Dictionary, _bedrock: bool, now: int) -> void:
	for sid in streams.keys():
		var s: Dictionary = streams[sid]
		if not s.get("detached", false) or s.kind == "dtcp" or s.kind == "dudp":
			continue
		s.detached = false
		s.last = now
		if s.kind == "tcp":
			s.wait_resume = true
			link.send_json({"t": "open", "sid": sid, "kind": "tcp", "key": s.key, "rx": s.recv, "resume": true})
		else:
			link.send_json({"t": "open", "sid": sid, "kind": "udp", "key": s.rkey, "rx": 0, "resume": true})
		AppLog.add("join", "поток %d: просим хозяина продолжить (%s)" % [sid, RelStream.info(s) if s.kind == "tcp" else s.kind])


func _log_close(sid: int, s: Dictionary, why: String) -> void:
	var dur := (Time.get_ticks_msec() - int(s.get("t0", Time.get_ticks_msec()))) / 1000
	if s.kind == "tcp":
		AppLog.add("join", "поток %d (Java) закрыт: %s; %d с, %s" % [sid, why, dur, RelStream.info(s)])
	else:
		AppLog.add("join", "поток %d (%s) закрыт: %s; %d с" % [sid, s.kind, why, dur])


## Версия протокола Minecraft друга: первый игровой пакет RequestNetworkSettings (0xfe, длина, id 193, int32 протокол)
## идёт без сжатия и шифрования — ищем его в датаграмме RakNet. 0 — не нашли.
static func client_protocol(pk: PackedByteArray) -> int:
	for i in range(4, pk.size() - 7):
		if pk[i] == 0xfe and pk[i + 2] == 0xc1 and pk[i + 3] == 0x01 and pk[i + 1] >= 6 and pk[i + 1] < 0x80:
			var v := (pk[i + 4] << 24) | (pk[i + 5] << 16) | (pk[i + 6] << 8) | pk[i + 7]
			if v > 300 and v < 5000:
				return v
	return 0


## Ответ сервера хозяина на HTTP-вход → игре на этом устройстве (кандидаты WebRTC — наш сокет моста).
func _http_reply(s: Dictionary, data: PackedByteArray) -> bool:
	var r: Variant = JSON.parse_string(data.get_string_from_utf8())
	if not (r is Dictionary) or not s.has("http"):
		return false
	var code := int(r.get("s", 502))
	var body := String(r.get("b", ""))
	if s.post and code == 200 and s.has("sess"):
		body = NetherNet.http_sdp_remote(s.sess, "CONNECTRESPONSE", body)
	var ct := String(r.get("h", {}).get("content-type", "")) if r.get("h", {}) is Dictionary else ""
	var t: StreamPeerTCP = s.http
	t.put_data(NetherNet.http_response(code, String(r.get("r", "")), ct, body.to_utf8_buffer()))
	t.disconnect_from_host()
	s.erase("http")
	return true


static func _close_stream(s: Dictionary, by_client: Dictionary) -> void:
	if s.has("http"):
		s.http.disconnect_from_host()
	if s.kind == "dtcp":
		if not s.to_c.is_empty() and s.tcp.get_status() == StreamPeerTCP.STATUS_CONNECTED:
			s.pending = s.to_c
			RelStream.drain(s, s.tcp)
		s.tcp.disconnect_from_host()
		s.d.disconnect_from_host()
	elif s.kind == "dudp":
		s.du.close()
		by_client.erase(s.get("key", ""))
	elif s.kind == "tcp":
		# сначала дописать то, что уже пришло (сообщение «вас выгнали» / «старая версия» — иначе игрок увидит «соединение разорвано»)
		RelStream.drain(s, s.tcp)
		s.tcp.disconnect_from_host()
	elif s.kind == "nn":
		NetherNet.close_session(s.sess)
	else:
		by_client.erase(s.get("key", ""))


func _ping_packet(now: int) -> PackedByteArray:
	var b := PackedByteArray([0x01])
	b.append_array(_be64(now))
	b.append_array(PackedByteArray(MAGIC))
	b.append_array(_be64(_guid))
	return b


func _pong_packet(time8: PackedByteArray, body: PackedByteArray) -> PackedByteArray:
	var b := PackedByteArray([0x1C])
	b.append_array(time8)
	b.append_array(_be64(_guid))
	b.append_array(PackedByteArray(MAGIC))
	b.append((body.size() >> 8) & 255)
	b.append(body.size() & 255)
	b.append_array(body)
	return b


## MCPE;motd;protocol;version;players;max;guid;sub;gamemode;gm_num;port4;port6;
func _rewrite_motd(s: String) -> String:
	var f := s.split(";")
	if f.size() < 6:
		return s
	if f.size() > 6:
		f[6] = str(_guid)
	if f.size() > 10:
		f[10] = str(bedrock_port)
	if f.size() > 11:
		f[11] = str(bedrock_port + 1)
	return ";".join(f)


static func _be64(v: int) -> PackedByteArray:
	var b := PackedByteArray()
	b.resize(8)
	for i in 8:
		b[7 - i] = (v >> (8 * i)) & 255
	return b


# ------------------------------------------------------------------ 1.8: прямое соединение по Wi-Fi (LanDirect)

## relay прислал ключ и порты (хозяин, похоже, в этой же сети) — начинаем искать его пробой.
func _lan_offer(lan: Dictionary, o: Variant, rng: RandomNumberGenerator, now: int) -> void:
	if not lan.on or not (o is Dictionary) or String(o.get("k", "")) == "" or int(o.get("probe", 0)) <= 0:
		return
	lan.k = String(o.k)
	lan.tcp = int(o.get("tcp", 0))
	lan.udp = int(o.get("udp", 0))
	lan.probe = int(o.probe)
	lan.nonce = LanDirect.new_nonce(rng)
	lan.tries = 0
	lan.next = now
	if lan.sock == null:
		var u := PacketPeerUDP.new()
		u.set_broadcast_enabled(true)
		if u.bind(0, "0.0.0.0") == OK:
			lan.sock = u
	AppLog.add("join", "хозяин, похоже, в этой же сети — ищем его напрямую")


## Проба: 4 попытки за секунду, потом раз в минуту (вдруг телефон только что подключился к Wi-Fi). true — нашли.
func _lan_probe(lan: Dictionary, now: int) -> bool:
	var u: PacketPeerUDP = lan.sock
	if u == null or String(lan.k) == "":
		return false
	var found := false
	while u.get_available_packet_count() > 0:
		var pk := u.get_packet()
		var ip := u.get_packet_ip()
		if LanDirect.check(pk, String(lan.k), String(lan.nonce)) and String(lan.addr) != ip:
			lan.addr = ip
			lan.fail_tcp = 0
			lan.fail_udp = 0
			found = true
			_st("direct", true)
			AppLog.add("join", "хозяин найден в домашней сети — новые подключения игры пойдут напрямую")
	if String(lan.addr) == "" and now >= int(lan.next):
		lan.tries = int(lan.tries) + 1
		lan.next = now + (250 if int(lan.tries) < 4 else 60000)
		var pr := LanDirect.probe(String(lan.k), String(lan.nonce))
		for t in LanDirect.broadcast_targets():
			if u.set_dest_address(t, int(lan.probe)) == OK:
				u.put_packet(pr)
		if int(lan.tries) == 4:
			AppLog.add("join", "хозяин в домашней сети не ответил — играем через сервер LANTERN")
	return found


func _direct_used() -> void:
	if not stats().direct_used:
		_st("direct_used", true)
		AppLog.add("join", "прямое соединение по Wi-Fi работает")


## Прямые потоки: игра ↔ сервер хозяина без relay. Не получилось соединиться — тихо переводим поток на relay
## (link == null — связи с relay сейчас нет: тогда поток просто закрываем, игра переподключится).
func _pump_direct(lan: Dictionary, streams: Dictionary, by_client: Dictionary, udp: PacketPeerUDP, link: RelayLink, rng: RandomNumberGenerator, now: int) -> bool:
	var busy := false
	for sid in streams.keys():
		var s: Dictionary = streams[sid]
		if s.kind == "dtcp":
			var c: StreamPeerTCP = s.tcp
			var d: StreamPeerTCP = s.d
			c.poll()
			d.poll()
			var cs := c.get_status()
			var ds := d.get_status()
			if cs != StreamPeerTCP.STATUS_CONNECTED:
				_close_stream(s, by_client)
				streams.erase(sid)
				continue
			if not s.up:
				if ds == StreamPeerTCP.STATUS_CONNECTED:
					s.up = true
					d.set_no_delay(true)
					_direct_used()
				elif ds != StreamPeerTCP.STATUS_CONNECTING or now - int(s.t0) > 800:
					# порт игры хозяина напрямую недоступен (брандмауэр?) — этот поток пойдёт через relay
					lan.fail_tcp = int(lan.fail_tcp) + 1
					d.disconnect_from_host()
					AppLog.add("join", "поток %d: напрямую не вышло — через сервер LANTERN" % sid)
					if link == null:
						c.disconnect_from_host()
						streams.erase(sid)
						continue
					var ns := {"kind": "tcp", "tcp": c, "last": now, "t0": now, "key": _key(rng)}
					RelStream.init(ns, false)
					streams[sid] = ns
					link.send_json({"t": "open", "sid": sid, "kind": "tcp", "key": ns.key, "rx": 0})
					var held: PackedByteArray = s.to_d
					var n0 := c.get_available_bytes()
					if n0 > 0:
						held.append_array(c.get_partial_data(n0)[1])
					var i := 0
					while i < held.size():
						var part := held.slice(i, i + 60000)
						link.send_bin(sid, part)
						RelStream.on_sent(ns, part)
						i += 60000
					busy = true
					continue
			# игра → хозяин
			var n := c.get_available_bytes()
			if n > 0 and s.to_d.size() < (1 << 20):
				s.to_d.append_array(c.get_partial_data(min(n, RelStream.READ_MAX))[1])
				busy = true
			if s.up and not s.to_d.is_empty():
				var r: Array = d.put_partial_data(s.to_d)
				if r[0] == OK and int(r[1]) > 0:
					_add("tx", int(r[1]))
					s.to_d = s.to_d.slice(int(r[1]))
			# хозяин → игра
			if s.up:
				if ds == StreamPeerTCP.STATUS_CONNECTED:
					var m := d.get_available_bytes()
					if m > 0 and s.to_c.size() < (1 << 20):
						var got: PackedByteArray = d.get_partial_data(min(m, RelStream.READ_MAX))[1]
						s.to_c.append_array(got)
						_add("rx", got.size())
						s.rb = int(s.rb) + got.size()
						if int(s.rb) > 50000 and not s.get("big", false):
							s.big = true
							AppLog.add("join", "Java: игрок в мире (поток %d, напрямую)" % sid)
							_st("in_game", true)
						busy = true
				if not s.to_c.is_empty():
					var r2: Array = c.put_partial_data(s.to_c)
					if r2[0] == OK and int(r2[1]) > 0:
						s.to_c = s.to_c.slice(int(r2[1]))
				if ds != StreamPeerTCP.STATUS_CONNECTED and d.get_available_bytes() <= 0:
					AppLog.add("join", "поток %d (Java, напрямую) закрыт сервером; %d с" % [sid, (now - int(s.t0)) / 1000])
					_close_stream(s, by_client)
					streams.erase(sid)
		elif s.kind == "dudp":
			var du: PacketPeerUDP = s.du
			while du.get_available_packet_count() > 0:
				var pk := du.get_packet()
				if not s.got:
					s.got = true
					s.held = []
					_direct_used()
				s.last = now
				s.rb = int(s.rb) + pk.size()
				_add("rx", pk.size())
				if int(s.rb) > 100000 and not s.get("big", false):
					s.big = true
					AppLog.add("join", "Bedrock: игрок в мире (поток %d, напрямую)" % sid)
					_st("in_game", true)
				if udp:
					udp.set_dest_address(s.ip, s.port)
					udp.put_packet(pk)
				busy = true
			if not s.got and now - int(s.t0) > 1500:
				# сервер хозяина по UDP напрямую не отвечает — переводим игрока на relay (RakNet сам повторит запрос)
				lan.fail_udp = int(lan.fail_udp) + 1
				du.close()
				AppLog.add("join", "поток %d: Bedrock напрямую не ответил — через сервер LANTERN" % sid)
				if link == null:
					by_client.erase(s.key)
					streams.erase(sid)
					continue
				var held: Array = s.held
				streams[sid] = {"kind": "udp", "ip": s.ip, "port": s.port, "key": s.key, "rkey": _key(rng), "last": now, "t0": now}
				link.send_json({"t": "open", "sid": sid, "kind": "udp", "key": streams[sid].rkey})
				if not held.is_empty():
					link.send_bin(sid, held[held.size() - 1])
			elif now - int(s.last) > UDP_IDLE_MS:
				_close_stream(s, by_client)
				streams.erase(sid)
	return busy
