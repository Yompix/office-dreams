class_name VNet
extends Node
## LANTERN 2.0 «Любые игры» — виртуальная локальная сеть (как Hamachi / Radmin VPN / ZeroTier). docs/VLAN.md
##
##   игра ⇄ виртуальный адаптер (Windows: lantern-net.exe + Wintun · Android: LanternVpnService · Linux: lantern-net)
##        ⇄ UDP 127.0.0.1 ⇄ ЭТОТ модуль (свой поток) ⇄ relay (WebSocket, всегда) или напрямую P2P (UDP, если пробили NAT)
##
## Relay пересылает только пакеты между участниками одной сети (relay/vnet.js). P2P: адреса (STUN + локальные)
## обмениваются через relay, «пробой» NAT обоими сразу; пакеты P2P подписаны общим ключом сети (HMAC-SHA256, 8 байт).
## Широковещательные/групповые пакеты (поиск игр в LAN) идут через relay — он размножает их на всех.
## Связь с основным потоком — только через _cmd (команды) и _snap/_ev (состояние и события) под мьютексом.

signal changed
signal event(e: Dictionary)

const STUN := [["stun.l.google.com", 19302], ["stun.cloudflare.com", 3478]]
const P2P_DATA := 1
const P2P_PING := 2
const P2P_PONG := 3
const P2P_PUNCH := 4
const P2P_ACK := 5
const GRAPH := 60                 # точек на графике связи (по одной в 2 с)

var relay_url := ""
var nick := ""
var token_fn: Callable            # () -> String: токен профиля LANTERN (не обязателен)
var allow_p2p := true

var _mx := Mutex.new()
var _thread: Thread
var _run := false
var _cmd: Array = []
var _ev: Array = []
var _snap := {"state": "idle"}
var _dirty := false

# ---- только в потоке
var _ws: WebSocketPeer
var _ws_open := false
var _st := "idle"                 # idle · connecting · online · reconnecting · error
var _err := ""
var _info := {}                   # ответ relay {t:'vnet'} + что нужно для возврата
var _want := {}                   # что делаем: {"op":"create"|"join", ...}
var _members := {}                # peer -> {ip, name, owner, relay_ms, rtt, p2p, addr, last_rx, graph:[], cands:[], punch_until}
var _by_ip := {}                  # "10.88.x.y" -> peer
var _retry_at := 0
var _retry_n := 0
var _last_ping := 0
var _adapter: PacketPeerUDP
var _adapter_port := 0
var _adapter_token := ""
var _adapter_peer := ["", 0]
var _adapter_state := "none"      # none · starting · ok · error
var _adapter_err := ""
var _adapter_seen := 0
var _adapter_info := {}
var _adapter_t0 := 0               # когда начали ставить адаптер (25 с без ответа — ошибка «timeout»)
var _p2p: PacketPeerUDP
var _p2p_port := 0
var _pub := []                    # [ip, port] от STUN
var _stun_at := 0
var _stun_tx := PackedByteArray()
var _crypto := Crypto.new()
var _key := PackedByteArray()
var _stats := {"tx": 0, "rx": 0, "tx_p2p": 0, "rx_p2p": 0, "drop": 0}
var _chat: Array = []
var _proc_pid := 0


func _ready() -> void:
	set_process(true)


func _exit_tree() -> void:
	stop_thread()


# ============================================================ основной поток: API для экранов

func create(opts: Dictionary) -> void:
	_push({"op": "create", "opts": opts})


func join(code: String, password := "") -> void:
	_push({"op": "join", "code": code, "password": password})


func leave() -> void:
	_push({"op": "leave"})


func end_net() -> void:
	_push({"op": "end"})


func kick(peer: String, ban := false) -> void:
	_push({"op": "send", "m": {"t": "vnet_kick", "peer": peer, "ban": ban}})


func chat(text: String) -> void:
	_push({"op": "send", "m": {"t": "vnet_chat", "text": text}})


func react(id: int, e: String) -> void:
	_push({"op": "send", "m": {"t": "vnet_react", "id": id, "e": e}})


func set_meta_net(d: Dictionary) -> void:
	var m := d.duplicate()
	m["t"] = "vnet_meta"
	_push({"op": "send", "m": m})


func active() -> bool:
	var s: String = snapshot().get("state", "idle")
	return s != "idle" and s != "error"


## Копия состояния для экрана (безопасно вызывать в любой момент).
func snapshot() -> Dictionary:
	_mx.lock()
	var s := _snap.duplicate(true)
	_mx.unlock()
	return s


func _push(c: Dictionary) -> void:
	start_thread()
	_mx.lock()
	_cmd.append(c)
	_mx.unlock()


func start_thread() -> void:
	if _thread != null:
		return
	_run = true
	_thread = Thread.new()
	_thread.start(_loop)


func stop_thread() -> void:
	if _thread == null:
		return
	_mx.lock()
	_cmd.append({"op": "leave"})
	_mx.unlock()
	OS.delay_msec(60)
	_run = false
	_thread.wait_to_finish()
	_thread = null


func _process(_d: float) -> void:
	_mx.lock()
	var evs := _ev
	_ev = []
	var dirty := _dirty
	_dirty = false
	_mx.unlock()
	for e in evs:
		event.emit(e)
	if dirty:
		changed.emit()


# ============================================================ адаптер (основной поток запускает помощника)

## Порт и одноразовый ключ, по которым помощник/служба VPN найдёт нас на 127.0.0.1.
func adapter_endpoint() -> Dictionary:
	_mx.lock()
	var d := {"port": _adapter_port, "token": _adapter_token}
	_mx.unlock()
	return d


func set_adapter_state(st: String, err := "") -> void:
	_mx.lock()
	_snap["adapter"] = st
	_snap["adapter_err"] = err
	_cmd.append({"op": "adapter_state", "st": st, "err": err})
	_dirty = true
	_mx.unlock()


# ============================================================ поток

func _loop() -> void:
	_open_local()
	var idle := 0
	while _run:
		var busy := 0
		_mx.lock()
		var cmds := _cmd
		_cmd = []
		_mx.unlock()
		for c in cmds:
			_do(c)
		busy += _poll_ws()
		busy += _poll_adapter()
		busy += _poll_p2p()
		_tick()
		if busy == 0:
			idle = mini(idle + 1, 20)
			OS.delay_usec(500 if idle < 5 else 2000)   # пакеты есть — без сна; тишина — спим чуть дольше
		else:
			idle = 0
	_close_all()


func _open_local() -> void:
	var rng := RandomNumberGenerator.new()
	rng.randomize()
	_adapter = PacketPeerUDP.new()
	for i in 20:
		var p := rng.randi_range(42000, 61000)
		if _adapter.bind(p, "127.0.0.1", 4 << 20) == OK:
			_adapter_port = p
			break
	var tk := PackedByteArray()
	for i in 16:
		tk.append(rng.randi_range(0, 255))
	_adapter_token = tk.hex_encode()
	_p2p = PacketPeerUDP.new()
	if _p2p.bind(0, "*", 4 << 20) == OK:
		_p2p_port = _p2p.get_local_port()
	_mx.lock()
	_snap["adapter"] = "none"
	_mx.unlock()


func _close_all() -> void:
	_send_adapter_ctl("quit")
	if _ws != null:
		_ws.close(1000, "bye")
		for i in 10:
			_ws.poll()
			OS.delay_msec(5)
	if _adapter:
		_adapter.close()
	if _p2p:
		_p2p.close()


func _do(c: Dictionary) -> void:
	match c.op:
		"create", "join":
			_want = c
			_info = {}
			_members = {}
			_by_ip = {}
			_chat = []
			_retry_n = 0
			_connect()
		"leave", "end":
			if _ws != null and _ws_open:
				_ws.send_text(JSON.stringify({"t": "vnet_end" if c.op == "end" else "vnet_leave"}))
				_ws.poll()
			_want = {}
			_info = {}
			_members = {}
			_by_ip = {}
			_set_state("idle")
			if _ws != null:
				_ws.close(1000, "bye")
			_send_adapter_ctl("quit")
			_adapter_peer = ["", 0]
			_adapter_state = "none"
		"send":
			if _ws != null and _ws_open:
				_ws.send_text(JSON.stringify(c.m))
		"adapter_state":
			_adapter_state = c.st
			_adapter_err = c.err
			_adapter_t0 = Time.get_ticks_msec() if c.st == "starting" and c.err == "" else _adapter_t0
			_publish()


func _connect() -> void:
	_ws = WebSocketPeer.new()
	_ws.inbound_buffer_size = 4 << 20
	_ws.outbound_buffer_size = 4 << 20
	_ws.max_queued_packets = 8192
	_ws_open = false
	var tls: TLSOptions = null
	var ca := OS.get_environment("SSL_CERT_FILE")
	if relay_url.begins_with("wss://") and ca != "" and FileAccess.file_exists(ca):
		var cert := X509Certificate.new()
		if cert.load(ca) == OK:
			tls = TLSOptions.client(cert)
	_set_state("connecting" if _info.is_empty() else "reconnecting")
	if _ws.connect_to_url(relay_url, tls) != OK:
		_fail_retry("connect")


func _hello() -> Dictionary:
	var tk: String = token_fn.call() if token_fn.is_valid() else ""
	if _want.op == "create":
		var o: Dictionary = _want.opts
		return {"t": "vnet_create", "v": 1, "token": tk, "nick": nick, "name": o.get("name", ""), "game": o.get("game", "any"),
			"public": o.get("public", false), "password": o.get("password", ""), "cap": o.get("cap", 0)}
	var m := {"t": "vnet_join", "v": 1, "token": tk, "nick": nick, "code": _want.get("code", ""), "password": _want.get("password", "")}
	if not _info.is_empty():
		# возвращаемся в свою сеть (обрыв связи, перезапуск relay): прежний адрес и подпись
		for k in ["num", "octet", "owner", "owner_uid", "name", "game", "public", "pass_hash", "ip"]:
			m[k] = _info.get(k)
		m["code"] = _info.code
		m["resume"] = _info.token
		m["peer"] = _info.peer
	return m


func _poll_ws() -> int:
	if _ws == null:
		return 0
	_ws.poll()
	var st := _ws.get_ready_state()
	var n := 0
	if st == WebSocketPeer.STATE_OPEN and not _ws_open:
		_ws_open = true
		_ws.set_no_delay(true)
		if not _want.is_empty():
			_ws.send_text(JSON.stringify(_hello()))
	if st == WebSocketPeer.STATE_OPEN or st == WebSocketPeer.STATE_CLOSING or st == WebSocketPeer.STATE_CLOSED:
		while _ws.get_available_packet_count() > 0 and n < 400:
			var pk := _ws.get_packet()
			n += 1
			if _ws.was_string_packet():
				var m: Variant = JSON.parse_string(pk.get_string_from_utf8())
				if m is Dictionary:
					_on_msg(m)
			else:
				_stats.rx += 1
				_to_adapter(pk)
	if st == WebSocketPeer.STATE_CLOSED:
		var code := _ws.get_close_code()
		_ws = null
		_ws_open = false
		if not _want.is_empty():
			if code == 4000 or code == 4004:
				if _st != "error":
					_set_state("idle")
				_want = {}
			else:
				_fail_retry("closed")
	return n


func _fail_retry(_why: String) -> void:
	_retry_n += 1
	if _info.is_empty() and _retry_n > 3:
		_err = "offline"
		_want = {}
		_set_state("error")
		_emit({"k": "error", "error": "offline"})
		return
	_retry_at = Time.get_ticks_msec() + mini(5000, 400 * _retry_n)
	_set_state("reconnecting" if not _info.is_empty() else "connecting")


func _on_msg(m: Dictionary) -> void:
	match str(m.get("t", "")):
		"vnet":
			_info = m
			_retry_n = 0
			_key = String(m.key).hex_decode()
			_members = {}
			_by_ip = {}
			for p in m.get("members", []):
				_add_member(p)
			_set_state("online")
			_emit({"k": "online", "ip": m.ip})
			_send_cands_all()
		"vnet_peer":
			if m.get("on", false):
				_add_member(m)
				_emit({"k": "peer_on", "name": m.get("name", ""), "ip": m.get("ip", "")})
				_send_cands(str(m.peer))
			else:
				var pe := str(m.get("peer", ""))
				if _members.has(pe):
					_by_ip.erase(_members[pe].ip)
					_members.erase(pe)
				_emit({"k": "peer_off", "name": m.get("name", ""), "lost": m.get("lost", false)})
			_publish()
		"vnet_pings":
			var p: Dictionary = m.get("p", {})
			for pe in p:
				if _members.has(pe):
					_members[pe].relay_ms = int(p[pe])
			_publish()
		"vnet_sig":
			_on_sig(str(m.get("from", "")), m.get("d", {}))
		"vnet_ping":
			if _ws_open:
				_ws.send_text(JSON.stringify({"t": "vnet_pong", "to": m.get("from", ""), "ts": m.get("ts", 0)}))
		"vnet_pong":
			var pe := str(m.get("from", ""))
			if _members.has(pe) and not _members[pe].p2p:
				_sample(pe, Time.get_ticks_msec() - int(m.get("ts", 0)))
		"vnet_chat":
			var c := {"id": int(m.get("id", 0)), "from": str(m.get("from", "")), "name": str(m.get("name", "")), "text": str(m.get("text", "")), "re": {}}
			_chat.append(c)
			if _chat.size() > 80:
				_chat.pop_front()
			_emit({"k": "chat", "name": c.name, "text": c.text, "mine": c.from == str(_info.get("peer", ""))})
			_publish()
		"vnet_react":
			for c in _chat:
				if c.id == int(m.get("id", 0)):
					var e := str(m.get("e", ""))
					c.re[e] = int(c.re.get(e, 0)) + 1
			_publish()
		"vnet_meta":
			for k in ["name", "public", "game", "locked", "pass_hash"]:
				if m.has(k):
					_info[k] = m[k]
			_publish()
		"net":
			_info["my_relay_ms"] = int(m.get("ping", 0))
			_publish()
		"error":
			var e := str(m.get("error", ""))
			_err = e
			if e in ["not_found", "need_password", "bad_password", "full", "kicked", "banned_by_host", "host_ended", "net_closed", "busy", "rate", "expired", "empty"]:
				_want = {}
				_info = {}
				_members = {}
				_set_state("error")
			_emit({"k": "error", "error": e})
		"restart":
			pass


func _add_member(p: Dictionary) -> void:
	var pe := str(p.get("peer", ""))
	if pe == "" or pe == str(_info.get("peer", "")):
		return
	var old: Dictionary = _members.get(pe, {})
	_members[pe] = {"peer": pe, "ip": str(p.get("ip", "")), "name": str(p.get("name", "")), "owner": bool(p.get("owner", false)),
		"plus": bool(p.get("plus", false)), "relay_ms": int(p.get("ms", 0)), "rtt": int(old.get("rtt", 0)), "p2p": false, "addr": [],
		"last_rx": 0, "graph": old.get("graph", []), "cands": [], "punch_until": 0, "punch_at": 0}
	_by_ip[_members[pe].ip] = pe


func _tick() -> void:
	var now := Time.get_ticks_msec()
	if _adapter_state == "starting" and _adapter_err == "" and _adapter_t0 > 0 and now - _adapter_t0 > 25000:
		_adapter_state = "error"      # помощник так и не ответил (Windows не запустил задачу, служба VPN не поднялась)
		_adapter_err = "timeout"
		_emit({"k": "adapter", "ok": false, "err": "timeout"})
		_publish()
	if _ws == null and not _want.is_empty() and now >= _retry_at:
		_connect()
	if _st != "online":
		return
	# STUN раз в 30 с (адрес у NAT может смениться)
	if allow_p2p and now >= _stun_at:
		_stun_at = now + 30000
		_stun_send()
	# пинг участников раз в 2 с: по P2P — прямо, иначе через relay
	if now - _last_ping >= 2000:
		_last_ping = now
		for pe in _members:
			var mb: Dictionary = _members[pe]
			if mb.p2p:
				if now - int(mb.last_rx) > 7000:
					mb.p2p = false        # прямая связь пропала — обратно через relay
					mb.addr = []
					_emit({"k": "p2p", "name": mb.name, "on": false})
					_publish()
				else:
					_p2p_send(mb.addr, P2P_PING, _u64(now))
			elif _ws_open:
				_ws.send_text(JSON.stringify({"t": "vnet_ping", "to": pe, "ts": now}))
	# пробой NAT: шлём «стук» на все адреса кандидатов, пока не ответят
	for pe in _members:
		var mb: Dictionary = _members[pe]
		if not mb.p2p and now < int(mb.punch_until) and now >= int(mb.punch_at):
			mb.punch_at = now + 250
			for c in mb.cands:
				_p2p_send(c, P2P_PUNCH, PackedByteArray())


# ---------------------------------------------------------------- адаптер ⇄ сеть

func _poll_adapter() -> int:
	if _adapter == null:
		return 0
	var n := 0
	while _adapter.get_available_packet_count() > 0 and n < 400:
		var pk := _adapter.get_packet()
		var ip := _adapter.get_packet_ip()
		var port := _adapter.get_packet_port()
		n += 1
		if pk.size() == 0:
			continue
		if pk[0] == 0:
			var s := pk.slice(1).get_string_from_utf8()
			if s.begins_with("hello "):
				var parts := s.split(" ", false, 2)
				if parts.size() >= 2 and parts[1] == _adapter_token:
					_adapter_peer = [ip, port]
					_adapter_seen = Time.get_ticks_msec()
					var inf: Variant = JSON.parse_string(parts[2]) if parts.size() > 2 else {}
					_adapter_info = inf if inf is Dictionary else {}
					_adapter_state = "ok" if _adapter_info.get("ok", false) else "error"
					_adapter_err = str(_adapter_info.get("err", ""))
					_emit({"k": "adapter", "ok": _adapter_state == "ok", "err": _adapter_err})
					_publish()
			elif s.begins_with("alive") and ip == _adapter_peer[0] and port == _adapter_peer[1]:
				_adapter_seen = Time.get_ticks_msec()
			continue
		if ip != _adapter_peer[0] or port != _adapter_peer[1]:
			continue          # чужой процесс на этом компьютере — не наш адаптер
		_from_adapter(pk)
	# раз в секунду — «жив» помощнику; 15 с тишины — адаптер пропал
	var now := Time.get_ticks_msec()
	if _adapter_peer[1] != 0 and now - int(_adapter_info.get("_ka", 0)) > 1000:
		_adapter_info["_ka"] = now
		_send_adapter_ctl("alive")
		if now - _adapter_seen > 15000 and _adapter_state == "ok":
			_adapter_state = "error"
			_adapter_err = "lost"
			_adapter_peer = ["", 0]
			_emit({"k": "adapter", "ok": false, "err": "lost"})
			_publish()
	return n


func _send_adapter_ctl(s: String) -> void:
	if _adapter == null or _adapter_peer[1] == 0:
		return
	_adapter.set_dest_address(_adapter_peer[0], _adapter_peer[1])
	var b := PackedByteArray([0])
	b.append_array(s.to_utf8_buffer())
	_adapter.put_packet(b)


func _to_adapter(pk: PackedByteArray) -> void:
	if _adapter_peer[1] == 0:
		_stats.drop += 1
		return
	_adapter.set_dest_address(_adapter_peer[0], _adapter_peer[1])
	_adapter.put_packet(pk)


func _from_adapter(pk: PackedByteArray) -> void:
	if pk.size() < 20 or (pk[0] >> 4) != 4 or _st != "online":
		return
	var dst := "%d.%d.%d.%d" % [pk[16], pk[17], pk[18], pk[19]]
	var pe: String = _by_ip.get(dst, "")
	if pe != "":
		var mb: Dictionary = _members[pe]
		if mb.p2p:
			_p2p_send(mb.addr, P2P_DATA, pk)
			_stats.tx_p2p += 1
			return
	if _ws_open:
		_ws.send(pk, WebSocketPeer.WRITE_MODE_BINARY)
		_stats.tx += 1


# ---------------------------------------------------------------- P2P: STUN, сигналы, пробой, данные

func _stun_send() -> void:
	if _p2p == null:
		return
	_stun_tx = PackedByteArray([0, 1, 0, 0, 0x21, 0x12, 0xA4, 0x42])
	for i in 12:
		_stun_tx.append(randi() % 256)
	for s in STUN:
		var ip := IP.resolve_hostname(s[0], IP.TYPE_IPV4)
		if ip != "":
			_p2p.set_dest_address(ip, s[1])
			_p2p.put_packet(_stun_tx)


func _stun_parse(pk: PackedByteArray) -> void:
	if pk.size() < 20 or pk[0] != 1 or pk[1] != 1 or pk.slice(8, 20) != _stun_tx.slice(8, 20):
		return
	var i := 20
	while i + 4 <= pk.size():
		var typ := (pk[i] << 8) | pk[i + 1]
		var ln := (pk[i + 2] << 8) | pk[i + 3]
		if (typ == 0x20 or typ == 0x01) and ln >= 8 and pk[i + 5] == 1:
			var port := (pk[i + 6] << 8) | pk[i + 7]
			var a := [pk[i + 8], pk[i + 9], pk[i + 10], pk[i + 11]]
			if typ == 0x20:
				port ^= 0x2112
				a = [a[0] ^ 0x21, a[1] ^ 0x12, a[2] ^ 0xA4, a[3] ^ 0x42]
			var np := ["%d.%d.%d.%d" % a, port]
			if np != _pub:
				_pub = np
				_send_cands_all()
			return
		i += 4 + ((ln + 3) & ~3)


func _cands() -> Array:
	var out := []
	if not _pub.is_empty():
		out.append(_pub)
	for a in IP.get_local_addresses():
		if a.contains(".") and not a.begins_with("127.") and not a.begins_with("10.88.") and not a.begins_with("169.254."):
			out.append([a, _p2p_port])
		if out.size() >= 6:
			break
	return out


func _send_cands_all() -> void:
	for pe in _members:
		_send_cands(pe)


func _send_cands(pe: String) -> void:
	if not allow_p2p or not _ws_open or _p2p_port == 0:
		return
	_ws.send_text(JSON.stringify({"t": "vnet_sig", "to": pe, "d": {"c": _cands()}}))


func _on_sig(pe: String, d: Variant) -> void:
	if not allow_p2p or not _members.has(pe) or not d is Dictionary:
		return
	var cs: Array = []
	for c in d.get("c", []):
		if c is Array and c.size() == 2 and str(c[0]).is_valid_ip_address() and int(c[1]) > 0 and int(c[1]) < 65536:
			cs.append([str(c[0]), int(c[1])])
	var mb: Dictionary = _members[pe]
	mb.cands = cs.slice(0, 6)
	mb.punch_until = Time.get_ticks_msec() + 12000
	mb.punch_at = 0


func _u64(v: int) -> PackedByteArray:
	var b := PackedByteArray()
	b.resize(8)
	b.encode_s64(0, v)
	return b


## Кадр P2P: "LV" · тип · peer отправителя (6) · подпись (8) · данные. Подпись = HMAC-SHA256(ключ сети, тип·peer·данные)[0:8].
func _p2p_send(addr: Array, typ: int, payload: PackedByteArray) -> void:
	if _p2p == null or addr.size() != 2 or _key.is_empty():
		return
	var me := str(_info.get("peer", "000000")).to_ascii_buffer()
	var head := PackedByteArray([typ])
	head.append_array(me)
	var msg := head.duplicate()
	msg.append_array(payload)
	var tag := _crypto.hmac_digest(HashingContext.HASH_SHA256, _key, msg).slice(0, 8)
	var b := PackedByteArray([0x4C, 0x56])
	b.append_array(head)
	b.append_array(tag)
	b.append_array(payload)
	_p2p.set_dest_address(addr[0], addr[1])
	_p2p.put_packet(b)


func _poll_p2p() -> int:
	if _p2p == null:
		return 0
	var n := 0
	while _p2p.get_available_packet_count() > 0 and n < 400:
		var pk := _p2p.get_packet()
		var ip := _p2p.get_packet_ip()
		var port := _p2p.get_packet_port()
		n += 1
		if pk.size() >= 20 and pk[0] == 1 and pk[1] == 1:
			_stun_parse(pk)
			continue
		if pk.size() < 17 or pk[0] != 0x4C or pk[1] != 0x56 or _key.is_empty():
			continue
		var typ := pk[2]
		var pe := pk.slice(3, 9).get_string_from_ascii()
		if not _members.has(pe):
			continue
		var msg := pk.slice(2, 9)
		var payload := pk.slice(17)
		msg.append_array(payload)
		if _crypto.hmac_digest(HashingContext.HASH_SHA256, _key, msg).slice(0, 8) != pk.slice(9, 17):
			continue           # не участник этой сети
		var mb: Dictionary = _members[pe]
		var now := Time.get_ticks_msec()
		match typ:
			P2P_PUNCH, P2P_ACK:
				if typ == P2P_PUNCH:
					_p2p_send([ip, port], P2P_ACK, PackedByteArray())
				if not mb.p2p:
					mb.p2p = true
					mb.addr = [ip, port]
					_emit({"k": "p2p", "name": mb.name, "on": true})
					_publish()
				mb.last_rx = now
			P2P_PING:
				mb.last_rx = now
				_p2p_send([ip, port], P2P_PONG, payload)
			P2P_PONG:
				mb.last_rx = now
				if payload.size() == 8:
					_sample(pe, now - payload.decode_s64(0))
			P2P_DATA:
				if not mb.p2p:
					continue
				mb.last_rx = now
				# защита: внутри — пакет именно от этого участника (его виртуальный адрес)
				if payload.size() >= 20 and "%d.%d.%d.%d" % [payload[12], payload[13], payload[14], payload[15]] == mb.ip:
					_stats.rx_p2p += 1
					_to_adapter(payload)
	return n


func _sample(pe: String, ms: int) -> void:
	if not _members.has(pe) or ms < 0 or ms > 60000:
		return
	var mb: Dictionary = _members[pe]
	mb.rtt = ms if int(mb.rtt) == 0 else int(round(mb.rtt * 0.6 + ms * 0.4))
	var g: Array = mb.graph
	g.append(ms)
	while g.size() > GRAPH:
		g.pop_front()
	_publish()


# ---------------------------------------------------------------- состояние для экранов

func _set_state(s: String) -> void:
	_st = s
	_publish()


func _emit(e: Dictionary) -> void:
	_mx.lock()
	_ev.append(e)
	_mx.unlock()


func _publish() -> void:
	var ms := []
	for pe in _members:
		var mb: Dictionary = _members[pe]
		ms.append({"peer": pe, "ip": mb.ip, "name": mb.name, "owner": mb.owner, "plus": mb.plus, "rtt": mb.rtt,
			"relay_ms": mb.relay_ms, "p2p": mb.p2p, "graph": mb.graph.duplicate()})
	ms.sort_custom(func(a, b): return a.ip.naturalnocasecmp_to(b.ip) < 0)
	var s := {"state": _st, "error": _err, "members": ms, "adapter": _adapter_state, "adapter_err": _adapter_err,
		"adapter_if": str(_adapter_info.get("ifname", "")), "adapter_ns": str(_adapter_info.get("ns", "")), "stats": _stats.duplicate(), "chat": _chat.duplicate(true)}
	for k in ["code", "num", "ip", "name", "game", "public", "locked", "cap", "peer", "owner", "bcast", "mtu", "my_relay_ms"]:
		if _info.has(k):
			s[k] = _info[k]
	s["is_owner"] = str(_info.get("owner", "")) == str(_info.get("peer", "-"))
	_mx.lock()
	_snap = s
	_dirty = true
	_mx.unlock()
