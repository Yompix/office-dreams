class_name Invite
extends RefCounted
## Приглашение в мир (QR и вставка). Формат v1 — docs/PROTOCOL.md. Общий код ПК и Android.
##   lantern://join?v=1&c=K7M2QX&e=both&n=<имя>&r=<wss-адрес relay, если не стандартный>

const SCHEME := "lantern://join"
const VERSION := 1


static func link(code: String, edition: String, name: String, relay := "") -> String:
	var q := "v=%d&c=%s&e=%s&n=%s" % [VERSION, code, edition, name.uri_encode()]
	if relay != "" and relay != Cfg.DEFAULT_RELAY:
		q += "&r=" + relay.uri_encode()
	return SCHEME + "?" + q


## Ссылка для мессенджеров (1.7): https://…/lantern/j/<код> — её видно как ссылку в любом чате; страница открывает
## LANTERN (lantern://join) или предлагает скачать. Свой relay — обычная ссылка lantern:// (страницы там может не быть).
static func web_link(code: String, edition: String, name: String, relay := "") -> String:
	if relay != "" and relay != Cfg.DEFAULT_RELAY and not Cfg.autotest_args.has("weblink"):
		return link(code, edition, name, relay)
	return Cfg.SITE + "/j/" + code


## Код комнаты из ссылки https://…/lantern/j/<код> (или "").
static func web_code(url: String) -> String:
	var m := RegEx.create_from_string("^https?://[^/]+/lantern/j/([A-Za-z0-9-]{6,9})(?:[/?#].*)?$").search(url.strip_edges())
	return RoomCode.normalize(m.get_string(1)) if m else ""


## Разобрать ввод: код комнаты, ссылку lantern://, ссылку minecraft:// или адрес host:port.
## → {ok, kind:"room"|"address", code, relay, edition, name, host, port, address} или {ok:false, error}
static func parse(text: String, default_edition := "java") -> Dictionary:
	var t := text.strip_edges()
	if t == "":
		return {"ok": false, "error": L.t("Введите код комнаты")}
	if t.begins_with(SCHEME):
		var q := _query(t)
		if int(q.get("v", "1")) > VERSION:
			return {"ok": false, "error": L.t("Приглашение из более новой версии — обновите LANTERN")}
		# c — код в приглашении приложения; code — в ссылке со страницы /lantern/j/<код> (1.7)
		var c := RoomCode.normalize(q.get("c", q.get("code", "")))
		if c == "":
			return {"ok": false, "error": L.t("В приглашении нет кода комнаты")}
		return {"ok": true, "kind": "room", "code": c, "relay": q.get("r", ""), "edition": q.get("e", ""), "name": q.get("n", "")}
	var code := RoomCode.normalize(t)
	if code == "":
		code = web_code(t)
	if code != "":
		return {"ok": true, "kind": "room", "code": code, "relay": "", "edition": "", "name": ""}
	if t.begins_with("minecraft://"):
		var i := t.find("addExternalServer=")
		if i < 0:
			return {"ok": false, "error": L.t("Ссылка Minecraft без адреса сервера")}
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
