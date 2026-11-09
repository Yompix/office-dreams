class_name RelayLink
extends RefCounted
## Соединение с relay «Играем вместе» по WebSocket (протокол v1, docs/PROTOCOL.md).
## Кадры: текст — JSON-команды, бинарные — [u32 sid big-endian][данные].
## Используется только из одного потока (хост-мост или прокси друга). Общий код ПК и Android.

const PROTO := 1

var ws := WebSocketPeer.new()
var url := ""
var _was_open := false


func open(relay_url: String) -> Error:
	url = relay_url
	ws = WebSocketPeer.new()
	ws.inbound_buffer_size = 4 << 20
	ws.outbound_buffer_size = 4 << 20
	ws.max_queued_packets = 8192
	_was_open = false
	var tls: TLSOptions = null
	var ca := OS.get_environment("SSL_CERT_FILE")
	if relay_url.begins_with("wss://") and ca != "" and FileAccess.file_exists(ca):
		var cert := X509Certificate.new()
		if cert.load(ca) == OK:
			tls = TLSOptions.client(cert)
	return ws.connect_to_url(relay_url, tls)


func state() -> int:
	return ws.get_ready_state()


func is_open() -> bool:
	return ws.get_ready_state() == WebSocketPeer.STATE_OPEN


## Опросить; вернуть список событий:
## {"k":"open"} / {"k":"msg","m":Dictionary} / {"k":"bin","sid":int,"data":PackedByteArray} / {"k":"closed","code":int,"reason":String}
func poll() -> Array:
	var ev := []
	ws.poll()
	var st := ws.get_ready_state()
	if st == WebSocketPeer.STATE_OPEN:
		if not _was_open:
			_was_open = true
			ev.append({"k": "open"})
		while ws.get_available_packet_count() > 0:
			var pk := ws.get_packet()
			if ws.was_string_packet():
				var m: Variant = JSON.parse_string(pk.get_string_from_utf8())
				if m is Dictionary:
					ev.append({"k": "msg", "m": m})
			elif pk.size() >= 4:
				var sid := (pk[0] << 24) | (pk[1] << 16) | (pk[2] << 8) | pk[3]
				ev.append({"k": "bin", "sid": sid, "data": pk.slice(4)})
	elif st == WebSocketPeer.STATE_CLOSED:
		ev.append({"k": "closed", "code": ws.get_close_code(), "reason": ws.get_close_reason(), "was_open": _was_open})
	return ev


func send_json(d: Dictionary) -> void:
	if is_open():
		ws.send_text(JSON.stringify(d))


func send_bin(sid: int, data: PackedByteArray) -> bool:
	if not is_open():
		return false
	var b := PackedByteArray([(sid >> 24) & 255, (sid >> 16) & 255, (sid >> 8) & 255, sid & 255])
	b.append_array(data)
	return ws.send(b, WebSocketPeer.WRITE_MODE_BINARY) == OK


## Сколько ещё не ушло в сеть — для притормаживания TCP.
func backlog() -> int:
	return ws.get_current_outbound_buffered_amount()


func close() -> void:
	if ws.get_ready_state() == WebSocketPeer.STATE_OPEN or ws.get_ready_state() == WebSocketPeer.STATE_CONNECTING:
		ws.close(1000, "bye")
		for i in 20:
			ws.poll()
			if ws.get_ready_state() == WebSocketPeer.STATE_CLOSED:
				break
			OS.delay_msec(5)


static func error_text(code: String) -> String:
	match code:
		"not_found": return "Комната с таким кодом не найдена. Проверьте код или попросите друга запустить мир заново."
		"full": return "В комнате уже максимум друзей."
		"rate": return "Слишком много попыток. Подождите минуту."
		"busy": return "Сервер-посредник перегружен. Попробуйте позже."
		"bad_version": return "Нужна новая версия «Играем вместе»."
		"host_left", "host_ended", "room_closed": return "Хозяин закрыл мир."
		"server_restart": return "Сервер-посредник перезапускается. Попробуйте через минуту."
		"expired": return "Комната устарела. Попросите друга запустить мир заново."
	return "Ошибка relay: " + code
