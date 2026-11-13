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
var world_name := L.t("Мир")
var java_port := 25565
var bedrock_port := 19132
var local_host := "127.0.0.1"   # куда пересылать: свой сервер или мир, открытый в Minecraft
var bedrock_nn_port := 0         # порт поиска NetherNet (0 — стандартный 7551; другой — только для автотестов)
var extra := {}          # token владельца, публичная карточка комнаты

var _thread: Thread
var _run := false
var _mutex := Mutex.new()
var _stats := {"state": "off", "code": "", "streams": 0, "peers": 0, "rx": 0, "tx": 0, "error": ""}
var _events: Array = []
var _last_room := ["", "", ""]    # код и токен последней комнаты — чтобы вернуть тот же код после перезапуска моста
var _resume := false
var _graceful := true
var _peers := {}          # peer -> {peer, user:{id,nick,tag}|null, since} — кто сейчас в комнате (relay 1.4+)
var _cmds: Array = []     # команды для relay из основного потока (выгнать, список запрета)
var cmd_total := 0        # сколько команд поставлено в очередь (автотест «нерабочих кнопок»)
var _peer_events: Array = []  # {peer, on, user} — зашёл/вышел (для уведомлений)
var _peer_events_a: Array = []
var bans: Array = []      # id аккаунтов LANTERN, кому хозяин закрыл вход (хранит приложение)
## Мир в самом Minecraft (Bedrock): мост сам пингует его — основной цикл Godot на Android в фоне стоит.
var probe_world := false
## Android: окошко поверх Minecraft и уведомление обновляются прямо из потока моста (JavaClass службы).
var android: Variant = null
var share_text := ""
var invite_friends: Array = []   # друзья в сети для «Пригласить» в окошке поверх игры: [{id, nick}]
var public_on := false
var public_card := {}
var _ui_actions: Array = []   # что нажали в окошке, пока LANTERN был в фоне: {a:"ban", user} {a:"public", on} {a:"close"}
var _probe: PacketPeerUDP
## 1.8: «Прямое соединение по Wi-Fi» (LanDirect): друзья из той же сети играют мимо relay
var lan_direct := true
var _lan_on := {}         # peer -> true: друг сообщил, что играет напрямую (для счётчика «друзей в мире»)
var _world_seen := 0
var _ok_since := 0
var _bad_since := 0
var _alerted := false


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
	_mutex.lock()
	_peers.clear()
	_cmds.clear()
	_peer_events.clear()
	_mutex.unlock()
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


## Кто сейчас в комнате: [{peer, user}], user = {id, nick, tag} или null (гость без профиля).
func peers() -> Array:
	_mutex.lock()
	var a := _peers.values().duplicate(true)
	_mutex.unlock()
	a.sort_custom(func(x, y): return int(x.since) < int(y.since))
	return a


func take_peer_events_android() -> Array:
	_mutex.lock()
	var e := _peer_events_a
	_peer_events_a = []
	_mutex.unlock()
	return e


## Когда мир Minecraft последний раз ответил на пинг моста (мс, 0 — не пинговали).
func world_seen() -> int:
	_mutex.lock()
	var t := _world_seen
	_mutex.unlock()
	return t


## Действия из окошка Android для основного потока (забрать и очистить).
func take_ui_actions() -> Array:
	_mutex.lock()
	var a := _ui_actions
	_ui_actions = []
	_mutex.unlock()
	return a


## Зашёл/вышел (забрать и очистить): [{peer, on, user}].
func take_peer_events() -> Array:
	_mutex.lock()
	var e := _peer_events
	_peer_events = []
	_mutex.unlock()
	return e


## Выгнать игрока из комнаты (ban — и больше не пускать).
func kick(peer: String, ban := false) -> void:
	_mutex.lock()
	cmd_total += 1
	_cmds.append({"t": "kick", "peer": peer, "ban": ban})
	_mutex.unlock()


## Обновить список запрета на relay (после «Разрешить снова»).
func send_bans(list: Array) -> void:
	bans = list.duplicate()
	_mutex.lock()
	cmd_total += 1
	_cmds.append({"t": "bans", "bans": bans})
	_mutex.unlock()


## Режим, места, пароль на ходу (без переподключения)
func set_settings(s: Dictionary) -> void:
	extra["settings"] = s
	_mutex.lock()
	cmd_total += 1
	_cmds.append({"t": "room_meta", "settings": s})
	_mutex.unlock()


## Открыть/закрыть комнату для всех («Открытые миры»). card — карточка мира (название, версия, места).
func set_public(on: bool, card := {}) -> void:
	if not card.is_empty():
		public_card = card
	public_on = on and not public_card.is_empty()
	_apply_public()
	_mutex.lock()
	cmd_total += 1
	_cmds.append({"t": "room_meta", "public": public_card if public_on else null})
	_mutex.unlock()


func _apply_public() -> void:
	if public_on:
		extra["public"] = public_card
	else:
		extra.erase("public")


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
	var streams := {}    # sid -> {kind, tcp|udp, pending, last, peer, key?, detached?}
	var code := ""
	var token := ""
	var num := ""
	if _resume:
		_mutex.lock()
		code = _last_room[0]
		token = _last_room[1]
		num = _last_room[2] if _last_room.size() > 2 else ""
		_mutex.unlock()
	var backoff := 1000
	var next_try := 0
	var connected := false
	var last_ping := 0
	var last_sync := 0
	var last_rx := 0
	var link_t0 := 0
	var active := false
	var last_busy := 0
	var lanp: PacketPeerUDP = null      # порт «пробы» прямого соединения
	var lan_keys := {}
	_lan_on.clear()
	if lan_direct and String(Cfg.autotest_args.get("lan", "1")) != "0" and (local_host == "127.0.0.1" or local_host in IP.get_local_addresses()):
		lanp = PacketPeerUDP.new()
		if lanp.bind(0, "0.0.0.0") != OK:
			lanp = null
	var nn := {"disc": null, "id": NetherNet.new_id(), "srv": 0, "ip": "", "port": 0, "app": PackedByteArray(), "cids": {}, "req": 0}
	AppLog.add("host", "мост хозяина: %s, java %d, bedrock %d, relay %s" % [edition, java_port, bedrock_port, relay_url])
	while _run:
		var now := Time.get_ticks_msec()
		if not active and now >= next_try:
			link = RelayLink.new()
			if link.open(relay_url) != OK:
				_st("state", "error")
				_st("error", L.t("неверный адрес сервера-посредника: ") + relay_url)
				AppLog.add("host", "не удалось начать соединение с relay")
				next_try = now + 5000
			else:
				active = true
				link_t0 = now
		var busy := false
		var evs: Array = link.poll() if active else []
		# связь «зависла» (телефон сменил сеть, а сокет не закрылся): нет ничего от relay 40 с — переподключаемся
		if active and connected and now - last_rx > 40000:
			AppLog.add("host", "relay молчит %d с — переподключаемся" % ((now - last_rx) / 1000))
			link.ws.close()
			evs.append({"k": "closed", "code": -1, "reason": "timeout", "was_open": true})
		elif active and not connected and now - link_t0 > 20000:
			AppLog.add("host", "relay не отвечает 20 с при подключении")
			link.ws.close()
			evs.append({"k": "closed", "code": -1, "reason": "connect timeout", "was_open": false})
		for ev in evs:
			if not active:
				break
			if ev.k != "closed":
				last_rx = now
			match ev.k:
				"open":
					connected = true
					backoff = 1000
					var hello := {"t": "host", "v": RelayLink.PROTO, "edition": edition, "name": world_name, "bans": bans}
					hello.merge(extra)
					if lanp:
						hello["lan"] = {"tcp": java_port if edition != "bedrock" else 0, "udp": bedrock_port if edition != "java" else 0, "probe": lanp.get_local_port()}
					_mutex.lock()
					_peers.clear()
					_mutex.unlock()
					if token != "":
						hello["code"] = code
						hello["resume"] = token
						hello["num"] = num
					link.send_json(hello)
					AppLog.add("host", "relay: соединение открыто за %d мс%s" % [now - link_t0, ", возвращаем прежний код" if token != "" else ""])
				"closed":
					active = false
					connected = false
					_mutex.lock()
					_peers.clear()
					_mutex.unlock()
					var kept := 0
					for sid in streams.keys():
						if _detach(streams[sid], now):
							kept += 1
						else:
							_close_local(streams[sid])
							streams.erase(sid)
					_update_counts(streams)
					AppLog.add("host", "relay: связь закрыта (код %s, %s, была открыта %s, %d с); потоков ждут возврата: %d" % [str(ev.code), String(ev.get("reason", "")), str(ev.was_open), (now - link_t0) / 1000, kept])
					if _run:
						_st("state", "reconnecting")
						_st("error", L.t("Связь с LANTERN прервалась — восстанавливаем, игроки не вылетят, если она вернётся за полминуты.") if ev.was_open else L.t("Нет связи с LANTERN — проверьте интернет. Пробуем снова…"))
					next_try = Time.get_ticks_msec() + (300 if int(ev.code) == 1012 else backoff)
					backoff = min(backoff * 2, 8000)
				"msg":
					var m: Dictionary = ev.m
					match String(m.get("t", "")):
						"net":
							_st("ping", int(m.get("ping", 0)))
							_st("quality", String(m.get("quality", "")))
						"room":
							if code != "" and code != m.code:
								_event("Старая комната пропала, выдан новый код: " + m.code)
								AppLog.add("host", "прежний код не вернулся — новый код; ждущие потоки закрыты")
								for sid in streams.keys():
									if streams[sid].get("detached", false):
										_close_local(streams[sid])
										streams.erase(sid)
								_update_counts(streams)
							elif code != "":
								AppLog.add("host", "комната возвращена с прежним кодом")
							code = m.code
							token = m.token
							num = String(m.get("num", ""))
							_mutex.lock()
							_last_room = [code, token, num]
							_mutex.unlock()
							_st("code", code)
							_st("num", num)
							_st("state", "online")
							_st("error", "")
						"open":
							var sid := int(m.sid)
							var key := String(m.get("key", ""))
							if key != "" and m.get("resume", false):
								_resume_stream(link, streams, sid, m, now)
								continue
							var s := {"kind": m.kind, "pending": PackedByteArray(), "last": now, "peer": m.get("peer", ""), "t0": now}
							if key != "":
								s.key = key
							if m.kind == "tcp":
								var t := StreamPeerTCP.new()
								t.connect_to_host(local_host, java_port)
								t.set_no_delay(true)
								s.tcp = t
								RelStream.init(s, key != "")
								AppLog.add("host", "поток %d (tcp) от друга %s → игра :%d" % [sid, s.peer, java_port])
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
								var s: Dictionary = streams[sid]
								if m.get("lost", false) and _detach(s, now):
									AppLog.add("host", "поток %d: друг потерял связь — ждём его возврата (%s)" % [sid, RelStream.info(s) if s.kind == "tcp" else s.kind])
								else:
									_log_close(sid, s, L.t("друг закрыл"))
									_close_local(s)
									streams.erase(sid)
								_update_counts(streams)
						"ack":
							var s: Variant = streams.get(int(m.sid))
							if s != null and s.kind == "tcp":
								RelStream.on_ack(s, int(m.get("n", 0)))
						"error":
							var e := String(m.get("error", ""))
							AppLog.add("host", "relay: ошибка " + e)
							if e == "bad_version" or e == "busy":
								_st("state", "error")
								_st("error", RelayLink.error_text(e))
								_run = false
						"peer":
							var pid := String(m.get("peer", ""))
							var u: Variant = m.get("user")
							AppLog.add("host", "друг %s %s" % [pid, "в комнате" if m.get("on", false) else "вышел"])
							_mutex.lock()
							if m.get("on", false):
								var known := _peers.has(pid)
								_peers[pid] = {"peer": pid, "user": u, "since": _peers.get(pid, {}).get("since", now)}
								if not known:
									_peer_events.append({"peer": pid, "on": true, "user": u})
									_peer_events_a.append({"peer": pid, "on": true, "user": u})
							elif _peers.has(pid):
								_lan_on.erase(pid)
								_peer_events.append({"peer": pid, "on": false, "user": _peers[pid].user})
								_peer_events_a.append({"peer": pid, "on": false, "user": _peers[pid].user})
								_peers.erase(pid)
							_mutex.unlock()
						"lan":
							if m.has("k"):
								LanDirect.remember(lan_keys, String(m.k), now)
							if m.has("on"):
								var lp := String(m.get("peer", ""))
								if m.on:
									if not _lan_on.has(lp):
										AppLog.add("host", "друг %s играет напрямую по Wi-Fi (мимо relay)" % lp)
									_lan_on[lp] = true
								else:
									_lan_on.erase(lp)
								_update_counts(streams)
						"pong":
							_st("rtt", maxi(1, Time.get_ticks_msec() - int(m.get("ts", now))))
				"bin":
					busy = true
					var s: Variant = streams.get(ev.sid)
					if s == null:
						continue
					s.last = now
					_add("rx", ev.data.size())
					if s.kind == "udp" and ev.data.size() >= 2 and ev.data[0] == NetherNet.FRAME:
						_nn_frame(nn, link, ev.sid, s, ev.data)
						continue
					if s.kind == "tcp":
						s.pending.append_array(ev.data)
						RelStream.on_recv(s, ev.data.size())
					else:
						s.udp.put_packet(ev.data)
		if now - last_sync >= 1000:
			last_sync = now
			_sync_side(link, connected, now)
		if connected:
			_mutex.lock()
			var cmds := _cmds
			_cmds = []
			_mutex.unlock()
			for c in cmds:
				link.send_json(c)
		if connected and now - last_ping > 10000:
			last_ping = now
			link.send_json({"t": "ping", "ts": now})
		# локальные соединения → relay
		for sid in streams.keys():
			var s: Dictionary = streams[sid]
			if s.get("detached", false) and now - int(s.det_t) > RelStream.DETACH_MS:
				_log_close(sid, s, L.t("друг не вернулся за %d с") % (RelStream.DETACH_MS / 1000))
				_close_local(s)
				streams.erase(sid)
				_update_counts(streams)
				continue
			if s.kind == "tcp":
				var t: StreamPeerTCP = s.tcp
				t.poll()
				var st := t.get_status()
				if st == StreamPeerTCP.STATUS_CONNECTED:
					if not RelStream.flush(s, t):
						_log_close(sid, s, L.t("игра не принимает данные"))
						if connected and not s.get("detached", false):
							link.send_json({"t": "close", "sid": sid})
						_close_local(s)
						streams.erase(sid)
						_update_counts(streams)
						continue
					if connected and link.backlog() < BACKLOG_MAX and RelStream.can_read(s):
						var sent := RelStream.pump_out(link, sid, s, t)
						if sent > 0:
							busy = true
							_add("tx", sent)
							s.last = now
					if connected and not s.get("detached", false):
						RelStream.maybe_ack(link, sid, s, now)
				elif st == StreamPeerTCP.STATUS_ERROR or st == StreamPeerTCP.STATUS_NONE:
					_log_close(sid, s, L.t("сервер закрыл соединение") if st == StreamPeerTCP.STATUS_NONE else L.t("ошибка соединения с сервером (порт %d)") % java_port)
					if connected and not s.get("detached", false):
						link.send_json({"t": "close", "sid": sid})
					_close_local(s)
					streams.erase(sid)
					_update_counts(streams)
			elif s.get("detached", false):
				continue
			elif s.has("nn"):
				if s.has("http"):
					busy = _http_poll(link, sid, s, now) or busy
				if s.has("sess"):
					for pk in NetherNet.pump(s.sess):
						busy = true
						link.send_bin(sid, NetherNet.frame(NetherNet.F_DATA, pk))
						_add("tx", pk.size())
						s.last = now
				if now - s.last > UDP_IDLE_MS:
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
		if nn.disc:
			busy = _nn_poll(nn, link, streams, now) or busy
		while lanp and lanp.get_available_packet_count() > 0:
			var ans := LanDirect.answer(lanp.get_packet(), lan_keys)
			if not ans.is_empty():
				lanp.set_dest_address(lanp.get_packet_ip(), lanp.get_packet_port())
				lanp.put_packet(ans)
		if busy:
			last_busy = Time.get_ticks_usec()
			if active:
				link.flush()
		else:
			RelStream.idle_wait(Time.get_ticks_usec(), last_busy, not streams.is_empty())
	if nn.disc:
		nn.disc.close()
	if lanp:
		lanp.close()
	if _probe:
		_probe.close()
		_probe = null
	if link.is_open() and _graceful:
		link.send_json({"t": "end"})
	for sid in streams:
		_close_local(streams[sid])
	link.close()
	AppLog.add("host", "мост хозяина остановлен")


## Связь пропала: поток с ключом ждёт возврата друга (соединение с сервером не закрываем). false — закрыть.
func _detach(s: Dictionary, now: int) -> bool:
	if not s.has("key") or s.get("norel", false):
		return false
	if not s.get("detached", false):
		s.detached = true
		s.det_t = now
	return true


## Друг вернулся (или relay перезапустился): поток с тем же ключом продолжает прежнее соединение с сервером.
func _resume_stream(link: RelayLink, streams: Dictionary, sid: int, m: Dictionary, now: int) -> void:
	var key := String(m.key)
	var old := -1
	for k in streams:
		if String(streams[k].get("key", "")) == key:
			old = k
			break
	if old < 0:
		AppLog.add("host", "возобновление потока: соединение уже закрыто — друг переподключится заново")
		link.send_json({"t": "close", "sid": sid})
		return
	var s: Dictionary = streams[old]
	streams.erase(old)
	if s.kind == "tcp":
		if not RelStream.replay(link, sid, s, int(m.get("rx", 0))):
			AppLog.add("host", "возобновление потока %d невозможно (%s)" % [sid, RelStream.info(s)])
			link.send_json({"t": "close", "sid": sid})
			_close_local(s)
			_update_counts(streams)
			return
		link.send_json({"t": "ack", "sid": sid, "n": s.recv, "r": 1})
		s.ack_sent = s.recv
	s.detached = false
	s.erase("det_t")
	s.last = now
	s.peer = m.get("peer", s.peer)
	streams[sid] = s
	_update_counts(streams)
	AppLog.add("host", "поток %d → %d возобновлён без разрыва игры (%s)" % [old, sid, RelStream.info(s) if s.kind == "tcp" else s.kind])
	_event("Связь с другом восстановлена")


func _log_close(sid: int, s: Dictionary, why: String) -> void:
	if s.kind == "tcp":
		AppLog.add("host", "поток %d закрыт: %s; %d с, %s" % [sid, why, (Time.get_ticks_msec() - int(s.get("t0", 0))) / 1000, RelStream.info(s)])


## Раз в секунду: пинг мира (Bedrock), окошко/уведомление Android, «мир не виден» и действия из окошка.
func _sync_side(link: RelayLink, connected: bool, now: int) -> void:
	if probe_world:
		if _probe == null:
			_probe = PacketPeerUDP.new()
			_probe.set_broadcast_enabled(true)
			if _probe.bind(0, "0.0.0.0") != OK:
				_probe = null
		if _probe:
			while _probe.get_available_packet_count() > 0:
				_probe.get_packet()
				_mutex.lock()
				_world_seen = now
				_mutex.unlock()
			_probe.set_dest_address(local_host, bedrock_port)
			_probe.put_packet(OwnWorld.ping_packet(now, 0x4C414E54))
			_probe.set_dest_address(local_host, bedrock_nn_port if bedrock_nn_port > 0 else NetherNet.PORT)
			_probe.put_packet(NetherNet.request(0x4C414E54))
	var alive := not probe_world or now - world_seen() < 7000
	var ok: bool = connected and _stats.state == "online" and alive
	if ok:
		_ok_since = now
		_bad_since = 0
		_alerted = false
	elif _bad_since == 0:
		_bad_since = now
	var why := ""
	if not connected or _stats.state != "online":
		why = L.t("Нет интернета или связи с LANTERN. Откройте LANTERN.")
	elif not alive:
		why = L.t("Minecraft не отвечает. Вернитесь в мир.")
	if android == null:
		_mutex.lock()
		_peer_events_a.clear()
		_mutex.unlock()
		return
	var plist := []
	for p in peers():
		plist.append({"peer": p.peer, "name": peer_name(p.user)})
	android.update(JSON.stringify({"code": RoomCode.pretty(_stats.code) if _stats.code != "" else "······", "ok": ok, "why": why,
		"world": world_name, "peers": plist, "public": public_on, "share": share_text,
		"num": String(_stats.get("num", "")), "quality": String(_stats.get("quality", "")), "friends": invite_friends}))
	for e in take_peer_events_android():
		android.event(L.t("%s %s ваш мир") % [peer_name(e.user), L.t("зашёл в") if e.on else L.t("вышел из")], false)
	if not ok and not _alerted and _ok_since > 0 and now - _bad_since > 8000:
		_alerted = true
		android.event(L.t("Мир не виден друзьям: вернитесь в мир / откройте LANTERN"), true)
	var acts: Variant = JSON.parse_string(String(android.takeActions()))
	if acts is Array:
		for a in acts:
			if not a is Dictionary:
				continue
			match String(a.get("a", "")):
				"kick", "ban":
					var pid := String(a.get("peer", ""))
					var ban: bool = a.a == "ban"
					if ban:
						var u: Variant = _peers.get(pid, {}).get("user")
						_mutex.lock()
						_ui_actions.append({"a": "ban", "user": u})
						_mutex.unlock()
					if connected:
						link.send_json({"t": "kick", "peer": pid, "ban": ban})
				"public":
					public_on = bool(a.get("on", false)) and not public_card.is_empty()
					_apply_public()
					if connected:
						link.send_json({"t": "room_meta", "public": public_card if public_on else null})
					_mutex.lock()
					_ui_actions.append({"a": "public", "on": public_on})
					_mutex.unlock()
				"invite":
					_mutex.lock()
					_ui_actions.append({"a": "invite", "id": int(a.get("id", 0))})
					_mutex.unlock()
				"close":
					_mutex.lock()
					_ui_actions.append({"a": "close"})
					_mutex.unlock()
					_run = false


static func peer_name(u: Variant) -> String:
	return String(u.nick) if u is Dictionary and u.has("nick") else L.t("Гость")


func _update_counts(streams: Dictionary) -> void:
	var peers := {}
	for sid in streams:
		peers[streams[sid].peer] = true
	for p in _lan_on:
		peers[p] = true
	_st("streams", streams.size())
	_st("peers", peers.size())
	_st("lan_peers", _lan_on.size())


# ------------------------------------------------------------------ NetherNet (новые версии Bedrock)
## Друг прислал кадр NetherNet: I — карточка мира, S — сигнал WebRTC (→ Minecraft через 7551), D — датаграмма.
func _nn_frame(nn: Dictionary, link: RelayLink, sid: int, s: Dictionary, data: PackedByteArray) -> void:
	if not s.has("nn"):
		s.nn = true
		if s.has("udp"):
			s.udp.close()
			s.erase("udp")
	if data[1] == NetherNet.F_HTTP:
		_http_frame(s, data.slice(2))
		return
	if data[1] == NetherNet.F_DATA and s.has("http_cid"):
		if s.has("sess"):
			NetherNet.deliver(s.sess, data.slice(2))
		return
	if nn.disc == null:
		var u := PacketPeerUDP.new()
		u.set_broadcast_enabled(true)
		if u.bind(0, "0.0.0.0") != OK:
			return
		nn.disc = u
		nn.req = 0
		_st("nethernet", true)
	var body := data.slice(2)
	match data[1]:
		NetherNet.F_INFO:
			s.info = true
			if not nn.app.is_empty():
				link.send_bin(sid, NetherNet.frame(NetherNet.F_INFO, nn.app))
		NetherNet.F_SIGNAL:
			var text := body.get_string_from_utf8()
			var p := NetherNet.split_signal(text)
			if p.size() < 3 or nn.srv == 0:
				return
			if not s.has("sess"):
				s.sess = NetherNet.new_session(p[1])
				nn.cids[p[1]] = sid
				_event("Друг подключается к миру (NetherNet)")
			for t in NetherNet.remote_signal(s.sess, text):
				nn.disc.set_dest_address(nn.ip, nn.port)
				nn.disc.put_packet(NetherNet.message(nn.id, nn.srv, t))
		NetherNet.F_DATA:
			if s.has("sess"):
				NetherNet.deliver(s.sess, body)


## HTTP-вход нового Bedrock (Geyser: «Серверы» → адрес): запрос друга → наш сервер на TCP <порт Bedrock>.
const HTTP_DROP := ["host", "connection", "content-length", "transfer-encoding", "keep-alive", "expect", "upgrade"]

func _http_frame(s: Dictionary, body: PackedByteArray) -> void:
	var rq: Variant = JSON.parse_string(body.get_string_from_utf8())
	if not (rq is Dictionary) or s.has("http"):
		return
	var m := String(rq.get("m", "GET"))
	var path := String(rq.get("p", "/"))
	var payload := String(rq.get("b", ""))
	var post := m == "POST" and path.begins_with("/v1/join/")
	if post:
		var cid := path.get_file()
		if not s.has("sess"):
			s.sess = NetherNet.new_session(cid)
			_event("Друг подключается к серверу (NetherNet)")
		s.http_cid = cid
		payload = NetherNet.http_sdp_remote(s.sess, "CONNECTREQUEST", payload)
	else:
		s.http_cid = ""
	var req := "%s %s HTTP/1.1\r\nHost: 127.0.0.1:%d\r\n" % [m, path, bedrock_port]
	var hd: Dictionary = rq.get("h", {}) if rq.get("h", {}) is Dictionary else {}
	for k in hd:
		if not String(k).to_lower() in HTTP_DROP:
			req += "%s: %s\r\n" % [k, String(hd[k]).replace("\r", "").replace("\n", "")]
	var pb := payload.to_utf8_buffer()
	req += "Content-Length: %d\r\nConnection: close\r\n\r\n" % pb.size()
	var out := req.to_utf8_buffer()
	out.append_array(pb)
	var t := StreamPeerTCP.new()
	t.connect_to_host(local_host, bedrock_port)
	t.set_no_delay(true)
	s.http = {"tcp": t, "out": out, "buf": PackedByteArray(), "t0": Time.get_ticks_msec(), "post": post}


func _http_poll(link: RelayLink, sid: int, s: Dictionary, now: int) -> bool:
	var h: Dictionary = s.http
	var t: StreamPeerTCP = h.tcp
	t.poll()
	var st := t.get_status()
	var busy := false
	if st == StreamPeerTCP.STATUS_CONNECTED:
		if h.out.size() > 0:
			t.put_data(h.out)
			h.out = PackedByteArray()
		var n := t.get_available_bytes()
		if n > 0:
			h.buf.append_array(t.get_partial_data(n)[1])
			busy = true
	var eof := st == StreamPeerTCP.STATUS_NONE or st == StreamPeerTCP.STATUS_ERROR
	var r := NetherNet.http_parse(h.buf, eof)
	var resp := {}
	if not r.is_empty():
		var sl := String(r.line).split(" ", false, 2)
		var rb: PackedByteArray = r.body
		var body := rb.get_string_from_utf8()
		var code := int(sl[1]) if sl.size() > 1 else 502
		if h.post and code == 200 and s.has("sess"):
			NetherNet.http_sdp_local(s.sess, "CONNECTRESPONSE", body)
		resp = {"s": code, "r": sl[2] if sl.size() > 2 else "", "h": {"content-type": String(r.headers.get("content-type", ""))}, "b": body}
	elif eof or now - int(h.t0) > 15000:
		resp = {"s": 502, "r": "Bad Gateway", "h": {"content-type": "text/plain"}, "b": "server is not answering"}
	if resp.is_empty():
		return busy
	t.disconnect_from_host()
	s.erase("http")
	s.last = now
	link.send_bin(sid, NetherNet.frame(NetherNet.F_HTTP, JSON.stringify(resp).to_utf8_buffer()))
	return true


func _nn_poll(nn: Dictionary, link: RelayLink, streams: Dictionary, now: int) -> bool:
	var busy := false
	var u: PacketPeerUDP = nn.disc
	if now - int(nn.req) > 2000:
		nn.req = now
		var rq := NetherNet.request(nn.id)
		for h in ["127.0.0.1", local_host] if local_host != "127.0.0.1" else ["127.0.0.1"]:
			u.set_dest_address(h, NetherNet.PORT if bedrock_nn_port <= 0 else bedrock_nn_port)
			u.put_packet(rq)
	while u.get_available_packet_count() > 0:
		busy = true
		var pk := u.get_packet()
		var ip := u.get_packet_ip()
		var port := u.get_packet_port()
		var d := NetherNet.decode(pk)
		if d.is_empty() or int(d.sender) == int(nn.id):
			continue
		if d.id == NetherNet.ID_RESPONSE:
			var first: bool = nn.app.is_empty()
			nn.srv = int(d.sender)
			nn.ip = ip
			nn.port = port
			nn.app = d.appdata
			for sid in streams:
				if streams[sid].get("info", false) and (first or now - int(streams[sid].get("info_t", 0)) > 1500):
					streams[sid].info_t = now
					link.send_bin(sid, NetherNet.frame(NetherNet.F_INFO, nn.app))
		elif d.id == NetherNet.ID_MESSAGE and int(d.recipient) == int(nn.id):
			var p := NetherNet.split_signal(String(d.text))
			if p.size() < 3:
				continue
			var sid: int = nn.cids.get(p[1], 0)
			if sid == 0 or not streams.has(sid):
				continue
			NetherNet.note_local_signal(streams[sid].sess, d.text)
			link.send_bin(sid, NetherNet.frame(NetherNet.F_SIGNAL, String(d.text).to_utf8_buffer()))
	return busy


static func _close_local(s: Dictionary) -> void:
	if s.has("http"):
		s.http.tcp.disconnect_from_host()
	if s.has("sess"):
		NetherNet.close_session(s.sess)
	if s.has("tcp"):
		RelStream.drain(s, s.tcp)
		s.tcp.disconnect_from_host()
	if s.has("udp"):
		s.udp.close()
