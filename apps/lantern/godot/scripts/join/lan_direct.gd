class_name LanDirect
extends RefCounted
## 1.8: «Прямое соединение по Wi-Fi». Хозяин и друг в одной домашней сети играют напрямую, минуя relay
## (relay остаётся для кода комнаты, списка игроков, «выгнать» и чата).
## Как находим друг друга, не раскрывая адресов: relay выдаёт обоим общий одноразовый ключ k и номер порта «пробы»
## (адреса устройств через relay не передаются вовсе). Друг шлёт широковещательную пробу в свою сеть:
##   «LNT1» + nonce(16 hex) + HMAC(k, "q|" + nonce)[0..16]
## Ответить может только хозяин, знающий k:
##   «LNT2» + nonce + HMAC(k, "a|" + nonce)[0..16]
## Адрес хозяина друг берёт из самого ответа. Проба из чужой сети до хозяина не доходит; неверную пробу хозяин молча
## отбрасывает. Любой сбой (нет ответа, порт игры закрыт брандмауэром) — тихо играем через relay, как раньше.

const Q := "LNT1"
const A := "LNT2"
const KEYS_MAX := 64


static func _mac(k: String, what: String, nonce: String) -> String:
	return Crypto.new().hmac_digest(HashingContext.HASH_SHA256, k.to_utf8_buffer(), (what + "|" + nonce).to_utf8_buffer()).hex_encode().substr(0, 16)


static func new_nonce(rng: RandomNumberGenerator) -> String:
	return "%08x%08x" % [rng.randi(), rng.randi()]


static func probe(k: String, nonce: String) -> PackedByteArray:
	return (Q + nonce + _mac(k, "q", nonce)).to_utf8_buffer()


## Хозяин: ответ на пробу или пусто (ключ не наш / мусор). keys: k -> что угодно.
static func answer(pk: PackedByteArray, keys: Dictionary) -> PackedByteArray:
	if pk.size() != 36:
		return PackedByteArray()
	var t := pk.get_string_from_ascii()
	if t.length() != 36 or not t.begins_with(Q):
		return PackedByteArray()
	var nonce := t.substr(4, 16)
	var mac := t.substr(20, 16)
	for k in keys:
		if _mac(String(k), "q", nonce) == mac:
			return (A + nonce + _mac(String(k), "a", nonce)).to_utf8_buffer()
	return PackedByteArray()


## Друг: это ответ хозяина на нашу пробу?
static func check(pk: PackedByteArray, k: String, nonce: String) -> bool:
	if pk.size() != 36:
		return false
	var t := pk.get_string_from_ascii()
	return t == A + nonce + _mac(k, "a", nonce)


## Куда слать пробу: общий широковещательный адрес и «x.y.z.255» каждой домашней сети устройства.
static func broadcast_targets() -> PackedStringArray:
	var out := PackedStringArray(["255.255.255.255"])
	for a in RoomCode.lan_addresses():
		var p := a.split(".")
		var b := "%s.%s.%s.255" % [p[0], p[1], p[2]]
		if not b in out:
			out.append(b)
	var extra := String(Cfg.autotest_args.get("lan_probe_addr", ""))   # только автотест (в песочнице нет домашней сети)
	if extra != "":
		out.append(extra)
	return out


static func remember(keys: Dictionary, k: String, now: int) -> void:
	if k.length() < 16 or k.length() > 64:
		return
	keys[k] = now
	while keys.size() > KEYS_MAX:
		var oldest: Variant = null
		for x in keys:
			if oldest == null or int(keys[x]) < int(keys[oldest]):
				oldest = x
		keys.erase(oldest)
