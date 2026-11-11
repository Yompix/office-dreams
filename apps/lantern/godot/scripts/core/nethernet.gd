class_name NetherNet
extends RefCounted
## NetherNet — как новые версии Minecraft Bedrock (1.21.1xx+, 2025–2026) открывают мир по LAN.
## Поиск: UDP 7551, пакеты Request/Response/Message, AES-256-ECB (ключ = SHA-256 от uint64 LE 0xdeadbeef)
## + HMAC-SHA256. Сама игра — WebRTC (ICE/DTLS/SCTP) по UDP; согласование (CONNECTREQUEST/CONNECTRESPONSE/
## CANDIDATEADD) ходит в Message-пакетах. Старые версии и Windows-серверы — RakNet на 19132 (own_world.gd).
##
## Мост LANTERN не расшифровывает WebRTC: пересылает сигналы и UDP-датаграммы как есть, подменяя только
## адреса ICE-кандидатов на свой локальный UDP-сокет (ufrag/pwd и отпечатки DTLS идут из конца в конец).
## Кадры в потоке relay: [0xFF][тип][данные]; тип S — сигнал (текст), D — датаграмма, I — карточка мира.
## RakNet никогда не начинает датаграмму с 0xFF, так что старый хост LANTERN просто не ответит.

const PORT := 7551
const FRAME := 0xFF
const F_SIGNAL := 0x53   # S
const F_DATA := 0x44     # D
const F_INFO := 0x49     # I
const F_HTTP := 0x48     # H — HTTP-вход на сервер (Bedrock 26.x: «Серверы» → TCP <порт>, /v1/join), JSON {m,p,h,b} / {s,r,h,b}
const ID_REQUEST := 0
const ID_RESPONSE := 1
const ID_MESSAGE := 2

static var _key := PackedByteArray()


static func key() -> PackedByteArray:
	if _key.is_empty():
		var h := HashingContext.new()
		h.start(HashingContext.HASH_SHA256)
		h.update(_le(0xdeadbeef, 8))
		_key = h.finish()
	return _key


static func new_id() -> int:
	var rng := RandomNumberGenerator.new()
	rng.randomize()
	return ((rng.randi() & 0x3fffffff) << 32) | rng.randi() | 1


# ------------------------------------------------------------------ пакеты поиска

static func encode(id: int, sender: int, body := PackedByteArray()) -> PackedByteArray:
	var inner := _le(id, 2)
	inner.append_array(_le(sender, 8))
	inner.append_array(PackedByteArray([0, 0, 0, 0, 0, 0, 0, 0]))
	inner.append_array(body)
	var payload := _le(inner.size() + 2, 2)
	payload.append_array(inner)
	var out := Crypto.new().hmac_digest(HashingContext.HASH_SHA256, key(), payload)
	out.append_array(_aes(payload, true))
	return out


static func request(sender: int) -> PackedByteArray:
	return encode(ID_REQUEST, sender)


static func response(sender: int, appdata: PackedByteArray) -> PackedByteArray:
	var hx := appdata.hex_encode().to_utf8_buffer()
	var b := _le(hx.size(), 4)
	b.append_array(hx)
	return encode(ID_RESPONSE, sender, b)


static func message(sender: int, recipient: int, text: String) -> PackedByteArray:
	var t := text.to_utf8_buffer()
	var b := _le(recipient, 8)
	b.append_array(_le(t.size(), 4))
	b.append_array(t)
	return encode(ID_MESSAGE, sender, b)


## → {id, sender, appdata?|recipient+text?}; {} — не NetherNet или подпись не сошлась.
static func decode(b: PackedByteArray) -> Dictionary:
	if b.size() < 48 or (b.size() - 32) % 16 != 0:
		return {}
	var payload := _aes(b.slice(32), false)
	if payload.is_empty():
		return {}
	var n := payload[payload.size() - 1]
	if n < 1 or n > 16 or n > payload.size():
		return {}
	payload = payload.slice(0, payload.size() - n)
	if Crypto.new().hmac_digest(HashingContext.HASH_SHA256, key(), payload) != b.slice(0, 32):
		return {}
	if payload.size() < 20:
		return {}
	var d := {"id": payload.decode_u16(2), "sender": payload.decode_s64(4)}
	var p := 20
	match int(d.id):
		ID_RESPONSE:
			if payload.size() < p + 4:
				return {}
			var ln := payload.decode_u32(p)
			var hx := payload.slice(p + 4, p + 4 + ln).get_string_from_utf8()
			d.appdata = hx.hex_decode()
		ID_MESSAGE:
			if payload.size() < p + 12:
				return {}
			d.recipient = payload.decode_s64(p)
			d.text = payload.slice(p + 12).get_string_from_utf8()
	return d


## Карточка мира (ServerData, версия 7): название, мир, версия, игроки. {} — другой формат (передаём как есть).
static func parse_server_data(b: PackedByteArray) -> Dictionary:
	if b.size() < 2 or b[0] != 7:
		return {}
	var r := [1]
	var d := {}
	d.server = _rstr(b, r)
	d.protocol = _rvarint(b, r)
	d.version = _rstr(b, r)
	d.level = _rstr(b, r)
	d.players = _rvarint(b, r)
	d.max = _rvarint(b, r)
	d.gametype = _rvarint(b, r)
	if r[0] > b.size():
		return {}
	return d


static func _rvaru(b: PackedByteArray, r: Array) -> int:
	var v := 0
	var sh := 0
	while r[0] < b.size() and sh < 35:
		var x := b[r[0]]
		r[0] += 1
		v |= (x & 0x7f) << sh
		if x & 0x80 == 0:
			return v
		sh += 7
	r[0] = b.size() + 1
	return 0


static func _rvarint(b: PackedByteArray, r: Array) -> int:
	var u := _rvaru(b, r)
	return (u >> 1) ^ -(u & 1)


static func _rstr(b: PackedByteArray, r: Array) -> String:
	var n := _rvaru(b, r)
	if r[0] + n > b.size():
		r[0] = b.size() + 1
		return ""
	var s := b.slice(r[0], r[0] + n).get_string_from_utf8()
	r[0] += n
	return s


# ------------------------------------------------------------------ сигналы и ICE-кандидаты

## «CONNECTREQUEST 123 v=0…» → ["CONNECTREQUEST", "123", "v=0…"]
static func split_signal(text: String) -> PackedStringArray:
	var a := text.find(" ")
	var b := text.find(" ", a + 1) if a >= 0 else -1
	if a < 0 or b < 0:
		return PackedStringArray()
	return PackedStringArray([text.substr(0, a), text.substr(a + 1, b - a - 1), text.substr(b + 1)])


## Сессия моста (одно WebRTC-соединение): свой UDP-сокет, адрес «своего» Minecraft, его кандидаты.
static func new_session(cid: String) -> Dictionary:
	var u := PacketPeerUDP.new()
	u.bind(0, "0.0.0.0")
	return {"cid": cid, "u": u, "addr": [], "cands": [], "sent_cand": false, "last": Time.get_ticks_msec()}


static func close_session(s: Dictionary) -> void:
	if s.has("u"):
		s.u.close()


## Сигнал от своего Minecraft → запомнить его кандидатов (куда слать датаграммы), отправить в relay как есть.
static func note_local_signal(s: Dictionary, text: String) -> void:
	var p := split_signal(text)
	if p.size() < 3:
		return
	if p[0] == "CANDIDATEADD":
		_note_cand(s, p[2])
	elif p[0] == "CONNECTREQUEST" or p[0] == "CONNECTRESPONSE":
		for line in p[2].split("\n"):
			line = line.strip_edges()
			if line.begins_with("a=candidate:"):
				_note_cand(s, line.substr(2))


static func _note_cand(s: Dictionary, c: String) -> void:
	var f := c.strip_edges().split(" ")
	if f.size() < 8 or f[2].to_lower() != "udp" or f[7] != "host" or not f[4].is_valid_ip_address() or f[4].contains(":") or not f[5].is_valid_int():
		return    # mDNS-имена (*.local), IPv6, TCP — адрес узнаем из первой датаграммы
	s.cands.append([f[4], int(f[5])])


## Сигнал из relay (от Minecraft на другом конце) → тексты для своего Minecraft: чужие кандидаты заменены
## на наш сокет (по одному на каждый наш IPv4), srflx/relay/IPv6/TCP выброшены.
static func remote_signal(s: Dictionary, text: String) -> PackedStringArray:
	var p := split_signal(text)
	var out := PackedStringArray()
	if p.size() < 3:
		return out
	if p[0] == "CANDIDATEADD":
		for c in _rewrite_cand(s, p[2]):
			out.append("CANDIDATEADD %s %s" % [p[1], c])
		return out
	if p[0] == "CONNECTREQUEST" or p[0] == "CONNECTRESPONSE":
		var nl := "\r\n" if p[2].contains("\r\n") else "\n"
		var lines := PackedStringArray()
		for line in p[2].split(nl):
			if line.begins_with("a=candidate:"):
				for c in _rewrite_cand(s, line.substr(2)):
					lines.append("a=" + c)
			else:
				lines.append(line)
		out.append("%s %s %s" % [p[0], p[1], nl.join(lines)])
		return out
	out.append(text)
	return out


static func _rewrite_cand(s: Dictionary, c: String) -> PackedStringArray:
	var out := PackedStringArray()
	var f := c.strip_edges().split(" ")
	if f.size() < 8 or f[2].to_lower() != "udp" or f[7] != "host" or f[4].contains(":"):
		return out
	if s.sent_cand:
		return out     # одного набора наших адресов достаточно
	s.sent_cand = true
	var port: int = s.u.get_local_port()
	var i := 0
	for ip in local_ipv4():
		var g := f.duplicate()
		g[0] = "%s%d" % [f[0], i]
		g[3] = str(maxi(1, int(f[3]) - i * 256))
		g[4] = ip
		g[5] = str(port)
		out.append(" ".join(g))
		i += 1
	return out


## Адреса этого устройства: сначала Wi-Fi/LAN, в конце 127.0.0.1.
static func local_ipv4() -> PackedStringArray:
	var out := PackedStringArray()
	for a in IP.get_local_addresses():
		if a.contains(":") or a.begins_with("127.") or a.begins_with("169.254.") or a == "0.0.0.0":
			continue
		if out.size() < 3:
			out.append(a)
	out.append("127.0.0.1")
	return out


## Датаграммы от своего Minecraft (запоминаем его адрес).
static func pump(s: Dictionary) -> Array:
	var out := []
	var u: PacketPeerUDP = s.u
	while u.get_available_packet_count() > 0:
		var pk := u.get_packet()
		s.addr = [u.get_packet_ip(), u.get_packet_port()]
		s.last = Time.get_ticks_msec()
		out.append(pk)
	return out


## Датаграмма из relay → своему Minecraft (по последнему адресу, иначе — по его кандидату).
static func deliver(s: Dictionary, data: PackedByteArray) -> void:
	var to: Array = s.addr
	if to.is_empty():
		if s.cands.is_empty():
			return
		to = s.cands[0]
	s.last = Time.get_ticks_msec()
	s.u.set_dest_address(to[0], to[1])
	s.u.put_packet(data)


static func frame(kind: int, data: PackedByteArray) -> PackedByteArray:
	var b := PackedByteArray([FRAME, kind])
	b.append_array(data)
	return b


# ------------------------------------------------------------------ байты

static func _le(v: int, n: int) -> PackedByteArray:
	var b := PackedByteArray()
	b.resize(n)
	for i in n:
		b[i] = (v >> (8 * i)) & 255
	return b


static func _aes(data: PackedByteArray, enc: bool) -> PackedByteArray:
	var a := AESContext.new()
	var d := data
	if enc:
		var pad := 16 - d.size() % 16
		for i in pad:
			d.append(pad)
	elif d.size() % 16 != 0:
		return PackedByteArray()
	if a.start(AESContext.MODE_ECB_ENCRYPT if enc else AESContext.MODE_ECB_DECRYPT, key()) != OK:
		return PackedByteArray()
	var out := a.update(d)
	a.finish()
	return out


# ------------------------------------------------------------------ HTTP-вход (NetherNet endpoint)
## Новые Bedrock заходят на сервер (Geyser, «Серверы» → адрес:порт) так: GET /v1/join — карточка сервера,
## POST /v1/join/<id> с SDP-предложением → SDP-ответ; дальше WebRTC по UDP на кандидатов из SDP.
## Мост: друг-LANTERN принимает HTTP на TCP <порт Bedrock>, пересылает запрос хозяину кадром H, хозяин спрашивает
## свой сервер; кандидаты в SDP подменяются на сокеты моста (как в поиске NetherNet), датаграммы — кадрами D.

static func _bfind(buf: PackedByteArray, pat: PackedByteArray, from := 0) -> int:
	var n := pat.size()
	for i in range(from, buf.size() - n + 1):
		if buf[i] == pat[0]:
			var ok := true
			for j in range(1, n):
				if buf[i + j] != pat[j]:
					ok = false
					break
			if ok:
				return i
	return -1


## Разобрать HTTP-запрос или ответ. {} — пришёл ещё не весь. eof — соединение закрыто (тело до конца).
## → {line: «GET /v1/join HTTP/1.1», headers: {имя в нижнем регистре: значение}, body: PackedByteArray, size: байт}
static func http_parse(buf: PackedByteArray, eof := false) -> Dictionary:
	var crlf := PackedByteArray([13, 10])
	var end := _bfind(buf, PackedByteArray([13, 10, 13, 10]))
	if end < 0:
		return {}
	var lines := buf.slice(0, end).get_string_from_utf8().split("\r\n")
	var h := {}
	for i in range(1, lines.size()):
		var c := lines[i].find(":")
		if c > 0:
			h[lines[i].substr(0, c).strip_edges().to_lower()] = lines[i].substr(c + 1).strip_edges()
	var p := end + 4
	var body := PackedByteArray()
	if String(h.get("transfer-encoding", "")).to_lower().contains("chunked"):
		while true:
			var le := _bfind(buf, crlf, p)
			if le < 0:
				return {}
			var n := buf.slice(p, le).get_string_from_ascii().get_slice(";", 0).strip_edges().hex_to_int()
			if n <= 0:
				var fin := _bfind(buf, crlf, le + 2)
				if fin < 0:
					return {}
				p = fin + 2
				break
			if buf.size() < le + 2 + n + 2:
				return {}
			body.append_array(buf.slice(le + 2, le + 2 + n))
			p = le + 2 + n + 2
	elif h.has("content-length"):
		var n := int(h["content-length"])
		if buf.size() < p + n:
			return {}
		body = buf.slice(p, p + n)
		p += n
	elif lines[0].begins_with("HTTP/") and not eof:
		return {}   # ответ без длины — тело до закрытия соединения
	else:
		body = buf.slice(p)
		p = buf.size()
	return {"line": lines[0], "headers": h, "body": body, "size": p}


## Подменить кандидатов в SDP (offer — «CONNECTREQUEST», answer — «CONNECTRESPONSE») на сокет моста.
static func http_sdp_remote(s: Dictionary, kind: String, sdp: String) -> String:
	var r := remote_signal(s, "%s %s %s" % [kind, s.cid, sdp])
	if r.is_empty():
		return sdp
	var p := split_signal(r[0])
	return p[2] if p.size() >= 3 else sdp


## Запомнить кандидатов «своего» Minecraft/сервера из SDP (куда слать датаграммы).
static func http_sdp_local(s: Dictionary, kind: String, sdp: String) -> void:
	note_local_signal(s, "%s %s %s" % [kind, s.cid, sdp])


static func http_response(status: int, reason: String, ctype: String, body: PackedByteArray) -> PackedByteArray:
	var head := "HTTP/1.1 %d %s\r\n" % [status, reason if reason != "" else "OK"]
	if ctype != "":
		head += "Content-Type: %s\r\n" % ctype
	head += "Content-Length: %d\r\nConnection: close\r\n\r\n" % body.size()
	var out := head.to_utf8_buffer()
	out.append_array(body)
	return out
