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
const UDP_IDLE_MS := 60000
const BACKLOG_MAX := 2 << 20

var relay_url := ""
var code := ""
var want_java := true
var want_bedrock := true
var java_port := 25565       # предпочитаемый локальный порт Java (если занят — любой свободный)
var bedrock_port := 19132    # Bedrock ищет LAN-миры только на 19132
var token := ""              # профиль LANTERN (для заданий и «Фонаря»), можно пусто
var _want_like := false

var _thread: Thread
var _run := false
var _mutex := Mutex.new()
var _stats := {"state": "off", "error": "", "name": "", "edition": "", "java_port": 0, "bedrock_port": 0,
	"bedrock_error": "", "java_error": "", "pong": false, "motd": "", "streams": 0, "rx": 0, "tx": 0, "host": true}
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
	if link.open(relay_url) != OK:
		_st("state", "error")
		_st("error", "неверный адрес сервера-посредника: " + relay_url)
		return
	var joined := false
	var edition := ""
	var name := ""
	var tcp_server: TCPServer = null
	var lan: PacketPeerUDP = null
	var udp: PacketPeerUDP = null
	var streams := {}         # sid -> {kind, tcp|ip+port, pending, last, open}
	var by_client := {}       # "ip:port" -> sid (bedrock)
	var next_sid := 2
	var pong_body := PackedByteArray()
	var last_ad := 0
	var last_ping := 0
	var last_rping := 0
	var host_here := true
	while _run:
		var now := Time.get_ticks_msec()
		var busy := false
		for ev in link.poll():
			match ev.k:
				"open":
					link.send_json({"t": "join", "v": RelayLink.PROTO, "code": code, "token": token})
				"closed":
					_st("state", "error")
					_st("error", "связь с сервером-посредником потеряна" if ev.was_open else "сервер-посредник недоступен — проверьте интернет")
					_run = false
				"msg":
					var m: Dictionary = ev.m
					match String(m.get("t", "")):
						"joined":
							joined = true
							edition = m.edition
							name = m.name
							host_here = m.get("host", true)
							_st("name", name)
							_st("edition", edition)
							_st("host", host_here)
							if want_java and edition in ["java", "both"]:
								tcp_server = TCPServer.new()
								if tcp_server.listen(java_port, "*") != OK and tcp_server.listen(0, "*") != OK:
									_st("java_error", "не удалось открыть локальный порт")
									tcp_server = null
								else:
									_st("java_port", tcp_server.get_local_port())
									lan = PacketPeerUDP.new()
									lan.set_dest_address(LAN_GROUP, LAN_PORT)
							if want_bedrock and edition in ["bedrock", "both"]:
								udp = PacketPeerUDP.new()
								udp.set_broadcast_enabled(true)
								if udp.bind(bedrock_port, "*") != OK:
									_st("bedrock_error", "порт %d занят — закройте открытый по сети мир или сервер Bedrock на этом устройстве" % bedrock_port)
									udp = null
								else:
									_st("bedrock_port", bedrock_port)
									link.send_json({"t": "open", "sid": PING_SID, "kind": "udp"})
							_st("state", "online")
						"error":
							_st("state", "error")
							_st("error", RelayLink.error_text(String(m.get("error", ""))))
							_run = false
						"close":
							var sid := int(m.sid)
							if sid == PING_SID:
								if udp and host_here:
									link.send_json({"t": "open", "sid": PING_SID, "kind": "udp"})
							elif streams.has(sid):
								_close_stream(streams[sid], by_client)
								streams.erase(sid)
						"host_gone":
							host_here = false
							_st("host", false)
							pong_body = PackedByteArray()
							_st("pong", false)
							for sid in streams.keys():
								_close_stream(streams[sid], by_client)
							streams.clear()
						"host_back":
							host_here = true
							_st("host", true)
							if udp:
								link.send_json({"t": "open", "sid": PING_SID, "kind": "udp"})
				"bin":
					busy = true
					_add("rx", ev.data.size())
					if ev.sid == PING_SID:
						var pk: PackedByteArray = ev.data
						if pk.size() > 35 and pk[0] == 0x1C:
							var motd := pk.slice(35).get_string_from_utf8()
							pong_body = _rewrite_motd(motd).to_utf8_buffer()
							_st("pong", true)
							_st("motd", motd)
						continue
					var s: Variant = streams.get(ev.sid)
					if s == null:
						continue
					s.last = now
					if s.kind == "tcp":
						s.tcp.put_data(ev.data)
					elif udp:
						udp.set_dest_address(s.ip, s.port)
						udp.put_packet(ev.data)
		if not joined:
			if not _run:
				break
			OS.delay_usec(2000)
			continue
		if _want_like:
			_want_like = false
			link.send_json({"t": "like"})
		if now - last_ping > 20000:
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
				streams[sid] = {"kind": "tcp", "tcp": c, "last": now}
				link.send_json({"t": "open", "sid": sid, "kind": "tcp"})
				busy = true
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
					streams[sid] = {"kind": "udp", "ip": cip, "port": cport, "key": key, "last": now}
					link.send_json({"t": "open", "sid": sid, "kind": "udp"})
				streams[sid].last = now
				link.send_bin(sid, pk)
				_add("tx", pk.size())
		# --- TCP → relay, уборка
		for sid in streams.keys():
			var s: Dictionary = streams[sid]
			if s.kind == "tcp":
				var t: StreamPeerTCP = s.tcp
				t.poll()
				if t.get_status() != StreamPeerTCP.STATUS_CONNECTED:
					link.send_json({"t": "close", "sid": sid})
					_close_stream(s, by_client)
					streams.erase(sid)
					continue
				var n := t.get_available_bytes()
				if n > 0 and link.backlog() < BACKLOG_MAX:
					busy = true
					var r := t.get_partial_data(min(n, 60000))
					link.send_bin(sid, r[1])
					_add("tx", r[1].size())
			elif now - s.last > UDP_IDLE_MS:
				link.send_json({"t": "close", "sid": sid})
				_close_stream(s, by_client)
				streams.erase(sid)
		_st("streams", streams.size())
		if not busy:
			OS.delay_usec(1000)
	for sid in streams:
		_close_stream(streams[sid], by_client)
	if tcp_server:
		tcp_server.stop()
	if udp:
		udp.close()
	if lan:
		lan.close()
	link.close()
	if stats().state != "error":
		_st("state", "off")


static func _close_stream(s: Dictionary, by_client: Dictionary) -> void:
	if s.kind == "tcp":
		s.tcp.disconnect_from_host()
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
