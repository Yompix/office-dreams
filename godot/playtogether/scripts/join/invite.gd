class_name Invite
extends RefCounted
## Приглашение в мир (QR и вставка). Формат v1 — docs/PROTOCOL.md. Общий код ПК и Android.
##   igraem://join?v=1&c=K7M2QX&e=both&n=<имя>&r=<wss-адрес relay, если не стандартный>

const SCHEME := "igraem://join"
const VERSION := 1


static func link(code: String, edition: String, name: String, relay := "") -> String:
	var q := "v=%d&c=%s&e=%s&n=%s" % [VERSION, code, edition, name.uri_encode()]
	if relay != "" and relay != Cfg.DEFAULT_RELAY:
		q += "&r=" + relay.uri_encode()
	return SCHEME + "?" + q


## Разобрать ввод: код комнаты, ссылку igraem://, ссылку minecraft:// или адрес host:port.
## → {ok, kind:"room"|"address", code, relay, edition, name, host, port, address} или {ok:false, error}
static func parse(text: String, default_edition := "java") -> Dictionary:
	var t := text.strip_edges()
	if t == "":
		return {"ok": false, "error": "Введите код комнаты"}
	if t.begins_with(SCHEME):
		var q := _query(t)
		if int(q.get("v", "1")) > VERSION:
			return {"ok": false, "error": "Приглашение из более новой версии — обновите «Играем вместе»"}
		var c := RoomCode.normalize(q.get("c", ""))
		if c == "":
			return {"ok": false, "error": "В приглашении нет кода комнаты"}
		return {"ok": true, "kind": "room", "code": c, "relay": q.get("r", ""), "edition": q.get("e", ""), "name": q.get("n", "")}
	var code := RoomCode.normalize(t)
	if code != "":
		return {"ok": true, "kind": "room", "code": code, "relay": "", "edition": "", "name": ""}
	if t.begins_with("minecraft://"):
		var i := t.find("addExternalServer=")
		if i < 0:
			return {"ok": false, "error": "Ссылка Minecraft без адреса сервера"}
		var v := t.substr(i + 18).uri_decode()
		var r := RoomCode.parse_address(v.get_slice("|", 1) if v.contains("|") else v, "bedrock")
		r["kind"] = "address"
		r["edition"] = "bedrock"
		r["name"] = v.get_slice("|", 0) if v.contains("|") else ""
		return r
	var r := RoomCode.parse_address(t, default_edition)
	r["kind"] = "address"
	r["edition"] = default_edition
	r["name"] = ""
	return r


static func _query(url: String) -> Dictionary:
	var d := {}
	var qi := url.find("?")
	if qi < 0:
		return d
	for kv in url.substr(qi + 1).split("&"):
		var p := kv.split("=", true, 1)
		if p.size() == 2:
			d[p[0]] = p[1].uri_decode()
	return d
