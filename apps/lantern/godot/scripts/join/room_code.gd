class_name RoomCode
extends RefCounted
## Код комнаты relay: 6 символов из алфавита без похожих знаков (нет 0/O, 1/I/L).
## Показываем как «K7M-2QX». Формат — docs/PROTOCOL.md. Общий код ПК и Android.

const ALPHABET := "23456789ABCDEFGHJKMNPQRSTUVWXYZ"
const LENGTH := 6


## Свой код LANTERN Pro: 3–8 символов, латиница и цифры 2–9 (без 0 и 1), хотя бы одна буква.
const VANITY_ALPHABET := "23456789ABCDEFGHIJKLMNOPQRSTUVWXYZ"


## Привести ввод к виду кода или "" (если это не код).
static func normalize(text: String) -> String:
	var c := text.strip_edges().to_upper().replace("-", "").replace(" ", "")
	if is_random(c) or is_number(c):
		return c
	if c.length() < 3 or c.length() > 8:
		return ""
	var letter := false
	for ch in c:
		if not VANITY_ALPHABET.contains(ch):
			return ""
		letter = letter or (ch >= "A" and ch <= "Z")
	return c if letter else ""


## Номер комнаты из списка игр (1.5): 8 цифр, например 35821626
static func is_number(c: String) -> bool:
	return c.length() == 8 and c.is_valid_int() and not c.begins_with("0")


static func is_random(c: String) -> bool:
	if c.length() != LENGTH:
		return false
	for ch in c:
		if not ALPHABET.contains(ch):
			return false
	return true


static func pretty(code: String) -> String:
	return code.substr(0, 3) + "-" + code.substr(3) if is_random(code) else code


## Адрес host[:port] (для игры в одной сети без relay).
static func parse_address(text: String, edition := "java") -> Dictionary:
	var t := text.strip_edges()
	var host := t
	var port := 0
	if t.count(":") == 1:
		host = t.get_slice(":", 0)
		var ps := t.get_slice(":", 1)
		if not ps.is_valid_int():
			return {"ok": false, "error": L.t("Порт должен быть числом")}
		port = int(ps)
	if host.contains(" ") or host.length() < 3 or not host.contains("."):
		return {"ok": false, "error": L.t("Это не похоже ни на код комнаты, ни на адрес")}
	if port == 0:
		port = 19132 if edition == "bedrock" else 25565
	if port < 1 or port > 65535:
		return {"ok": false, "error": L.t("Порт должен быть от 1 до 65535")}
	return {"ok": true, "host": host, "port": port, "address": "%s:%d" % [host, port]}


## Адреса в домашней сети (для друзей на том же Wi-Fi).
static func lan_addresses() -> PackedStringArray:
	var out := PackedStringArray()
	for a in IP.get_local_addresses():
		if a.contains(":"):
			continue
		if a.begins_with("192.168.") or a.begins_with("10.") or (a.begins_with("172.") and int(a.get_slice(".", 1)) >= 16 and int(a.get_slice(".", 1)) <= 31):
			out.append(a)
	return out
