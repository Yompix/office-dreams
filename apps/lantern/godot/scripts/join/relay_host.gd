class_name RelayHost
extends RefCounted
## Мост хозяина мира: relay ↔ локальный сервер.
## Поток «tcp» → 127.0.0.1:java_port (Paper или мир Java «Открыть для сети»), поток «udp» → 127.0.0.1:bedrock_port
## (BDS, Geyser или мир Bedrock с «Видимостью для игроков LAN»), каждый кадр = одна датаграмма.
## Работает в своём потоке, без узлов сцены — общий код ПК и Android.

const UDP_IDLE_MS := 60000
const BACKLOG_MAX := 2 << 20

var relay_url := ""
var edition := "java"     # java / bedrock / both
var world_name := "Мир"
var java_port := 25565
var bedrock_port := 19132
var local_host := "127.0.0.1"   # куда пересылать: свой сервер или мир, открытый в Minecraft
var extra := {}          # token владельца, публичная карточка комнаты

var _thread: Thread
var _run := false
var _mutex := Mutex.new()
var _stats := {"state": "off", "code": "", "streams": 0, "peers": 0, "rx": 0, "tx": 0, "error": ""}
var _events: Array = []
var _last_room := ["", ""]    # код и токен последней комнаты — чтобы вернуть тот же код после перезапуска моста
var _resume := false
var _graceful := true


## resume — попросить у relay прежний код (relay держит комнату 5 минут после обрыва).
func start(url: String, ed: String, name: String, jport := 25565, bport := 19132, hello_extra := {}, resume := false) -> void:
	stop()
	_resume = resume and _last_room[0] != ""
	_graceful = true
	extra = hello_extra
	relay_url = url
	edition = ed
	world_name = name
	java_port = jport
	bedrock_port = bport
	_run = true
	_st("state", "connecting")
	_thread = Thread.new()
	_thread.start(_loop)


## graceful=false — оборвать связь без «end» (как если бы систему усыпила приложение): комната ждёт возврата 5 минут.
func stop(graceful := true) -> void:
	_graceful = graceful
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


## Журнал событий для UI (забрать и очистить).
func take_events() -> Array:
	_mutex.lock()
	var e := _events
	_events = []
	_mutex.unlock()
	return e


func _st(k: String, v: Variant) -> void:
	_mutex.lock()
	_stats[k] = v
	_mutex.unlock()


func _add(k: String, v: int) -> void:
	_mutex.lock()
	_stats[k] += v
	_mutex.unlock()


func _event(text: String) -> void:
	_mutex.lock()
	_events.append(text)
	_mutex.unlock()


func _loop() -> void:
	var link := RelayLink.new()
	var streams := {}    # sid -> {kind, tcp|udp, pending, last, peer}
	var code := ""
	var token := ""
	if _resume:
		_mutex.lock()
		code = _last_room[0]
		token = _last_room[1]
		_mutex.unlock()
	var backoff := 1000
	var next_try := 0
	var connected := false
	var last_ping := 0
	var active := false
	while _run:
		var now := Time.get_ticks_msec()
		if not active and now >= next_try:
			link = RelayLink.new()
			if link.open(relay_url) != OK:
				_st("state", "error")
				_st("error", "неверный адрес сервера-посредника: " + relay_url)
				next_try = now + 5000
			else:
				active = true
		var busy := false
		for ev in (link.poll() if active else []):
			match ev.k:
				"open":
					connected = true
					backoff = 1000
					var hello := {"t": "host", "v": RelayLink.PROTO, "edition": edition, "name": world_name}
					hello.merge(extra)
					if token != "":
						hello["code"] = code
						hello["resume"] = token
					link.send_json(hello)
				"closed":
					if true:
						active = false
						connected = false
						for sid in streams.keys():
							_close_local(streams[sid])
						streams.clear()
						_st("streams", 0)
						_st("peers", 0)
						if _run:
							_st("state", "reconnecting")
							_st("error", "нет связи с сервером-посредником, переподключаемся…" if ev.was_open else "сервер-посредник недоступен (%s)" % relay_url)
						next_try = Time.get_ticks_msec() + backoff
						backoff = min(backoff * 2, 15000)
				"msg":
					var m: Dictionary = ev.m
					match String(m.get("t", "")):
						"room":
							if code != "" and code != m.code:
								_event("Старая комната пропала, выдан новый код: " + m.code)
							code = m.code
							token = m.token
							_mutex.lock()
							_last_room = [code, token]
							_mutex.unlock()
							_st("code", code)
							_st("state", "online")
							_st("error", "")
						"open":
							var sid := int(m.sid)
							var s := {"kind": m.kind, "pending": PackedByteArray(), "last": now, "peer": m.get("peer", "")}
							if m.kind == "tcp":
								var t := StreamPeerTCP.new()
								t.connect_to_host(local_host, java_port)
								t.set_no_delay(true)
								s.tcp = t
							else:
								var u := PacketPeerUDP.new()
								u.bind(0, "127.0.0.1" if local_host == "127.0.0.1" else "0.0.0.0")
								u.set_dest_address(local_host, bedrock_port)
								s.udp = u
							streams[sid] = s
							_update_counts(streams)
						"close":
							var sid := int(m.sid)
							if streams.has(sid):
								_close_local(streams[sid])
								streams.erase(sid)
								_update_counts(streams)
						"error":
							var e := String(m.get("error", ""))
							if e == "bad_version" or e == "busy":
								_st("state", "error")
								_st("error", RelayLink.error_text(e))
								_run = false
						"pong":
							pass
				"bin":
					busy = true
					var s: Variant = streams.get(ev.sid)
					if s == null:
						continue
					s.last = now
					_add("rx", ev.data.size())
					if s.kind == "tcp":
						s.pending.append_array(ev.data)
					else:
						s.udp.put_packet(ev.data)
		if connected and now - last_ping > 20000:
			last_ping = now
			link.send_json({"t": "ping", "ts": now})
		# локальные соединения → relay
		for sid in streams.keys():
			var s: Dictionary = streams[sid]
			if s.kind == "tcp":
				var t: StreamPeerTCP = s.tcp
				t.poll()
				var st := t.get_status()
				if st == StreamPeerTCP.STATUS_CONNECTED:
					if s.pending.size() > 0:
						t.put_data(s.pending)
						s.pending = PackedByteArray()
					var n := t.get_available_bytes()
					if n > 0 and link.backlog() < BACKLOG_MAX:
						busy = true
						var r := t.get_partial_data(min(n, 60000))
						link.send_bin(sid, r[1])
						_add("tx", r[1].size())
						s.last = now
				elif st == StreamPeerTCP.STATUS_ERROR or st == StreamPeerTCP.STATUS_NONE:
					link.send_json({"t": "close", "sid": sid})
					_close_local(s)
					streams.erase(sid)
					_update_counts(streams)
			else:
				var u: PacketPeerUDP = s.udp
				while u.get_available_packet_count() > 0:
					busy = true
					var pk := u.get_packet()
					link.send_bin(sid, pk)
					_add("tx", pk.size())
					s.last = now
				if now - s.last > UDP_IDLE_MS:
					link.send_json({"t": "close", "sid": sid})
					_close_local(s)
					streams.erase(sid)
					_update_counts(streams)
		if not busy:
			OS.delay_usec(1000)
	if link.is_open() and _graceful:
		link.send_json({"t": "end"})
	for sid in streams:
		_close_local(streams[sid])
	link.close()


func _update_counts(streams: Dictionary) -> void:
	var peers := {}
	for sid in streams:
		peers[streams[sid].peer] = true
	_st("streams", streams.size())
	_st("peers", peers.size())


static func _close_local(s: Dictionary) -> void:
	if s.has("tcp"):
		s.tcp.disconnect_from_host()
	if s.has("udp"):
		s.udp.close()
