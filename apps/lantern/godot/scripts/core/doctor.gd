class_name Doctor
extends RefCounted
## «Доктор» сервера: по строкам журнала Paper/BDS узнаёт типичные беды и говорит, что делать — простыми словами.
## Что можно — ServerHost чинит сам (белый список, занятый порт, падение, память) и пишет в «Что LANTERN исправил сам».
## Что требует решения хозяина — кнопка в одно нажатие на «Мой мир» (разбанить, больше мест).
## Здесь только распознавание (чистые функции — их проверяет autotest core).

static var _re_name := RegEx.create_from_string("name=([\\.\\w]{1,32})")
static var _re_who := RegEx.create_from_string("\\]: (?:Disconnecting )?([\\.\\w]{1,32})[ ,(\\[]")
static var _re_ver := RegEx.create_from_string("(?i)(?:please use|still on|i'm on|version)\\s*:?\\s*([0-9][0-9a-z\\.\\-]*[0-9a-z])")


## Строка журнала → {rule, nick, version} или {} (ничего важного).
## rule: whitelist, auth, outdated_client, outdated_server, banned, full, port
static func diagnose(t: String) -> Dictionary:
	var low := t.to_lower()
	var rule := ""
	if low.contains("not whitelisted") or low.contains("not white-listed"):
		rule = "whitelist"
	elif low.contains("failed to verify username") or low.contains("invalid session") or low.contains("verify username"):
		rule = "auth"
	elif low.contains("outdated client") or low.contains("incompatible client"):
		rule = "outdated_client"
	elif low.contains("outdated server"):
		rule = "outdated_server"
	elif low.contains("you are banned") or low.contains("are banned from this server"):
		rule = "banned"
	elif low.contains("server is full") or low.contains("the server is full"):
		rule = "full"
	elif low.contains("failed to bind to port") or low.contains("address already in use") or (low.contains("port") and low.contains("may be in use")):
		rule = "port"
	if rule == "":
		return {}
	var d := {"rule": rule, "nick": "", "version": ""}
	if rule != "port":
		var m := _re_name.search(t)
		if m == null:
			m = _re_who.search(t)
		if m:
			d.nick = m.get_string(1)
		var v := _re_ver.search(t)
		if v:
			d.version = v.get_string(1)
	return d


## Устаревшая строка whitelist (совместимость): ник или "".
static func whitelist_nick(t: String) -> String:
	var d := diagnose(t)
	return String(d.nick) if d.get("rule", "") == "whitelist" else ""


## Сервер завершился сам. Что делаем: "port" — занят порт, берём свободный и запускаем снова;
## "restart" — упал после работы, перезапускаем один раз; "fail" — показываем ошибку.
static func exit_plan(port_conflict: bool, port_retries: int, auto_restarts: int, ran_ms: int) -> String:
	if port_conflict and port_retries < 3:
		return "port"
	if auto_restarts < 1 and ran_ms > 60000:
		return "restart"
	return "fail"


## Сколько памяти дать Java-серверу: не больше ~половины ОЗУ компьютера, минимум 1 ГБ.
static func memory_for(want_mb: int, total_mb: int) -> int:
	if total_mb <= 0:
		return want_mb
	var cap := maxi(1024, int(total_mb * 0.5) / 512 * 512)
	return clampi(want_mb, 1024, cap)


## Свободный порт, начиная с want (TCP для Java, UDP для Bedrock).
static func free_port(want: int, udp: bool, skip := []) -> int:
	for p in range(want, want + 40):
		if p in skip:
			continue
		if udp:
			var u := PacketPeerUDP.new()
			var ok := u.bind(p, "0.0.0.0") == OK
			u.close()
			if ok:
				return p
		else:
			var s := TCPServer.new()
			var ok := s.listen(p, "0.0.0.0") == OK
			s.stop()
			if ok:
				return p
	return want


## Понятный текст для хозяина по правилу (без жаргона).
static func text(d: Dictionary, server_version := "") -> String:
	var n := String(d.get("nick", ""))
	var who := ("Игрок " + n) if n != "" else "Игрок"
	var v := String(d.get("version", ""))
	if v == "":
		v = server_version
	match String(d.get("rule", "")):
		"auth":
			return "%s не прошёл проверку аккаунта Microsoft: на сервер заходят только игроки со своим аккаунтом Minecraft." % who
		"outdated_client":
			return "%s пришёл со старой версией Minecraft. Сервер на версии %s — пусть выберет в лаунчере версию %s." % [who, v, v] if v != "" else "%s пришёл со старой версией Minecraft. Пусть выберет в лаунчере ту же версию, что у сервера." % who
		"outdated_server":
			return "У %s версия Minecraft новее сервера (%s). Пусть выберет в лаунчере версию %s." % [n if n != "" else "игрока", v, v] if v != "" else "У игрока версия Minecraft новее сервера. Пусть выберет в лаунчере версию сервера."
		"banned":
			return "%s забанен на этом сервере." % who
		"full":
			return "Сервер полон — %s не поместился." % ("игрок " + n if n != "" else "новый игрок")
		"whitelist":
			return "%s не в белом списке." % who
	return ""
