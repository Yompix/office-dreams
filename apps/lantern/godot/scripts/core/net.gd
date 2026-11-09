extends Node
## Загрузки по HTTPS: JSON, файлы с прогрессом и проверкой контрольных сумм.
## Прокси берётся из HTTPS_PROXY (если задан), корневые сертификаты — из SSL_CERT_FILE (если задан).

const UA := "LANTERN/1.0 (by Yompix; +minecraft server helper)"

var _proxy_host := ""
var _proxy_port := 0
var _tls: TLSOptions = null


func _ready() -> void:
	var p := OS.get_environment("HTTPS_PROXY")
	if p == "":
		p = OS.get_environment("https_proxy")
	if p != "":
		p = p.trim_prefix("http://").trim_prefix("https://")
		if p.contains("@"):
			p = p.get_slice("@", 1)
		p = p.trim_suffix("/")
		var idx := p.rfind(":")
		if idx > 0:
			_proxy_host = p.substr(0, idx)
			_proxy_port = int(p.substr(idx + 1))
	var ca := OS.get_environment("SSL_CERT_FILE")
	if ca != "" and FileAccess.file_exists(ca):
		var cert := X509Certificate.new()
		if cert.load(ca) == OK:
			_tls = TLSOptions.client(cert)


func _make() -> HTTPRequest:
	var r := HTTPRequest.new()
	r.use_threads = true
	r.max_redirects = 10
	r.timeout = 0
	add_child(r)
	if _proxy_host != "":
		r.set_https_proxy(_proxy_host, _proxy_port)
		r.set_http_proxy(_proxy_host, _proxy_port)
	if _tls != null:
		r.set_tls_options(_tls)
	return r


static func _err_text(result: int, code: int) -> String:
	match result:
		HTTPRequest.RESULT_SUCCESS:
			return "сервер ответил кодом %d" % code
		HTTPRequest.RESULT_CANT_CONNECT, HTTPRequest.RESULT_CONNECTION_ERROR:
			return "нет соединения с интернетом или сайт недоступен"
		HTTPRequest.RESULT_CANT_RESOLVE:
			return "не удалось найти адрес сайта (проверьте интернет)"
		HTTPRequest.RESULT_TLS_HANDSHAKE_ERROR:
			return "ошибка защищённого соединения (TLS)"
		HTTPRequest.RESULT_TIMEOUT:
			return "сайт слишком долго не отвечает"
		HTTPRequest.RESULT_DOWNLOAD_FILE_CANT_OPEN, HTTPRequest.RESULT_DOWNLOAD_FILE_WRITE_ERROR:
			return "не удалось записать файл на диск (место? права?)"
		HTTPRequest.RESULT_REDIRECT_LIMIT_REACHED:
			return "слишком много перенаправлений"
	return "ошибка сети (%d)" % result


## Запрос, возвращает {ok, code, text, data(JSON или null), error}
func request(url: String, method := HTTPClient.METHOD_GET, body := "", headers: PackedStringArray = []) -> Dictionary:
	var r := _make()
	r.timeout = 60
	var h := PackedStringArray(["User-Agent: " + UA, "Accept: application/json"])
	h.append_array(headers)
	if body != "":
		h.append("Content-Type: application/json")
	var err := r.request(url, h, method, body)
	if err != OK:
		r.queue_free()
		return {"ok": false, "code": 0, "error": "не удалось начать загрузку (%d)" % err, "data": null, "text": ""}
	var res: Array = await r.request_completed
	r.queue_free()
	var result: int = res[0]
	var code: int = res[1]
	var text: String = (res[3] as PackedByteArray).get_string_from_utf8()
	var data: Variant = null
	if text != "":
		data = JSON.parse_string(text)
	var ok := result == HTTPRequest.RESULT_SUCCESS and code >= 200 and code < 300
	return {"ok": ok, "code": code, "text": text, "data": data, "error": "" if ok else _err_text(result, code)}


func get_json(url: String, headers: PackedStringArray = []) -> Dictionary:
	return await request(url, HTTPClient.METHOD_GET, "", headers)


func post_json(url: String, payload: Variant, headers: PackedStringArray = []) -> Dictionary:
	return await request(url, HTTPClient.METHOD_POST, JSON.stringify(payload), headers)


## Скачивание в файл. checksum: {"sha256": "..."} или {"sha1": "..."} или {}.
## progress(done_bytes, total_bytes) вызывается на каждом кадре.
func download(url: String, dest: String, checksum: Dictionary = {}, progress := Callable()) -> Dictionary:
	DirAccess.make_dir_recursive_absolute(dest.get_base_dir())
	var tmp := dest + ".part"
	if FileAccess.file_exists(tmp):
		DirAccess.remove_absolute(tmp)
	var r := _make()
	r.download_file = tmp
	r.download_chunk_size = 262144
	var done := [false, 0, 0]
	r.request_completed.connect(func(result, code, _h, _b): done[0] = true; done[1] = result; done[2] = code)
	var err := r.request(url, PackedStringArray(["User-Agent: " + UA]))
	if err != OK:
		r.queue_free()
		return {"ok": false, "error": "не удалось начать загрузку (%d)" % err}
	while not done[0]:
		if progress.is_valid():
			progress.call(r.get_downloaded_bytes(), r.get_body_size())
		await get_tree().process_frame
	r.queue_free()
	if done[1] != HTTPRequest.RESULT_SUCCESS or done[2] < 200 or done[2] >= 300:
		DirAccess.remove_absolute(tmp)
		return {"ok": false, "error": _err_text(done[1], done[2])}
	if not checksum.is_empty():
		var algo: String = checksum.keys()[0]
		var want: String = String(checksum[algo]).to_lower()
		var got := await file_hash(tmp, algo)
		if got != want:
			DirAccess.remove_absolute(tmp)
			return {"ok": false, "error": "файл повреждён при загрузке (не совпала контрольная сумма %s). Попробуйте ещё раз." % algo.to_upper()}
	if FileAccess.file_exists(dest):
		DirAccess.remove_absolute(dest)
	DirAccess.rename_absolute(tmp, dest)
	return {"ok": true, "error": "", "path": dest}


func file_hash(path: String, algo := "sha256") -> String:
	var f := FileAccess.open(path, FileAccess.READ)
	if f == null:
		return ""
	var ctx := HashingContext.new()
	ctx.start(HashingContext.HASH_SHA1 if algo == "sha1" else (HashingContext.HASH_MD5 if algo == "md5" else HashingContext.HASH_SHA256))
	var n := 0
	while f.get_position() < f.get_length():
		ctx.update(f.get_buffer(1 << 20))
		n += 1
		if n % 16 == 0:
			await get_tree().process_frame
	return ctx.finish().hex_encode()
