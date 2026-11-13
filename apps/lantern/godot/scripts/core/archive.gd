class_name Archive
extends RefCounted
## Распаковка zip, tar.gz и .deb (ar) на чистом GDScript — без внешних программ.


## Распаковать zip. strip — сколько верхних папок отрезать. skip — пути, которые не перезаписывать, если уже есть.
static func unzip(zip_path: String, dest: String, strip := 0, keep_existing: PackedStringArray = []) -> String:
	var z := ZIPReader.new()
	if z.open(zip_path) != OK:
		return L.t("архив повреждён или не открылся: ") + zip_path.get_file()
	DirAccess.make_dir_recursive_absolute(dest)
	for name in z.get_files():
		var rel := _strip(name, strip)
		if rel == "" or rel.ends_with("/"):
			if rel != "":
				DirAccess.make_dir_recursive_absolute(dest.path_join(rel))
			continue
		if not _safe(rel):
			continue
		var out := dest.path_join(rel)
		if rel in keep_existing and FileAccess.file_exists(out):
			continue
		DirAccess.make_dir_recursive_absolute(out.get_base_dir())
		var data := z.read_file(name)
		var f := FileAccess.open(out, FileAccess.WRITE)
		if f == null:
			z.close()
			return L.t("не удалось записать ") + out
		f.store_buffer(data)
		f.close()
	z.close()
	return ""


## Распаковать .tar.gz. only — если задан, берём только эти пути (после strip).
static func untar_gz(path: String, dest: String, strip := 0, only: PackedStringArray = []) -> String:
	var raw := FileAccess.get_file_as_bytes(path)
	if raw.is_empty():
		return L.t("не удалось прочитать ") + path.get_file()
	var tar := raw.decompress_dynamic(-1, FileAccess.COMPRESSION_GZIP)
	if tar.is_empty():
		return L.t("архив повреждён: ") + path.get_file()
	return untar_bytes(tar, dest, strip, only)


static func untar_bytes(tar: PackedByteArray, dest: String, strip := 0, only: PackedStringArray = []) -> String:
	var pos := 0
	var long_name := ""
	var pax_path := ""
	while pos + 512 <= tar.size():
		var hdr := tar.slice(pos, pos + 512)
		if hdr.count(0) == 512:
			break
		var name := _cstr(hdr, 0, 100)
		var mode := _oct(hdr, 100, 8)
		var size := _oct(hdr, 124, 12)
		var typ := char(hdr[156]) if hdr[156] != 0 else "0"
		var prefix := _cstr(hdr, 345, 155) if _cstr(hdr, 257, 5) == "ustar" else ""
		if prefix != "":
			name = prefix + "/" + name
		var data_start := pos + 512
		pos = data_start + int(ceil(size / 512.0)) * 512
		if typ == "L":
			long_name = _cstr(tar, data_start, size)
			continue
		if typ == "x":
			var txt := tar.slice(data_start, data_start + size).get_string_from_utf8()
			for line in txt.split("\n"):
				var sp := line.find(" ")
				if sp > 0 and line.substr(sp + 1).begins_with("path="):
					pax_path = line.substr(sp + 6)
			continue
		if typ == "g":
			continue
		if long_name != "":
			name = long_name
			long_name = ""
		if pax_path != "":
			name = pax_path
			pax_path = ""
		var rel := _strip(name.trim_prefix("./"), strip)
		if rel == "" or not _safe(rel):
			continue
		if not only.is_empty() and not (rel in only):
			continue
		var out := dest.path_join(rel)
		if typ == "5":
			DirAccess.make_dir_recursive_absolute(out)
		elif typ == "0" or typ == "7":
			DirAccess.make_dir_recursive_absolute(out.get_base_dir())
			var f := FileAccess.open(out, FileAccess.WRITE)
			if f == null:
				return L.t("не удалось записать ") + out
			f.store_buffer(tar.slice(data_start, data_start + size))
			f.close()
			if not Cfg.is_windows() and mode & 73:
				FileAccess.set_unix_permissions(out, mode & 511)
		# ссылки (1, 2) пропускаем: в JRE это только файлы лицензий
	return ""


## Достать файл из ar-архива (.deb), например "data.tar.gz".
static func ar_member(path: String, prefix: String) -> PackedByteArray:
	var b := FileAccess.get_file_as_bytes(path)
	if b.size() < 8 or b.slice(0, 8).get_string_from_ascii() != "!<arch>\n":
		return PackedByteArray()
	var pos := 8
	while pos + 60 <= b.size():
		var name := b.slice(pos, pos + 16).get_string_from_ascii().strip_edges().trim_suffix("/")
		var size := int(b.slice(pos + 48, pos + 58).get_string_from_ascii().strip_edges())
		var start := pos + 60
		if name.begins_with(prefix):
			return b.slice(start, start + size)
		pos = start + size + (size % 2)
	return PackedByteArray()


static func _cstr(b: PackedByteArray, off: int, n: int) -> String:
	var s := b.slice(off, off + n)
	var z := s.find(0)
	if z >= 0:
		s = s.slice(0, z)
	return s.get_string_from_utf8()


static func _oct(b: PackedByteArray, off: int, n: int) -> int:
	var s := _cstr(b, off, n).strip_edges()
	var v := 0
	for c in s:
		if c < "0" or c > "7":
			break
		v = v * 8 + (c.unicode_at(0) - 48)
	return v


static func _strip(name: String, strip: int) -> String:
	if strip <= 0:
		return name
	var parts := name.split("/", false)
	if parts.size() <= strip:
		return ""
	var rest := "/".join(parts.slice(strip))
	return rest + ("/" if name.ends_with("/") else "")


static func _safe(rel: String) -> bool:
	return not (rel.begins_with("/") or rel.contains("..") or rel.contains(":"))


## Упаковать папки в zip (резервная копия). Возвращает "" или текст ошибки.
static func zip_dirs(base: String, dirs: PackedStringArray, out_zip: String) -> String:
	DirAccess.make_dir_recursive_absolute(out_zip.get_base_dir())
	var z := ZIPPacker.new()
	if z.open(out_zip) != OK:
		return L.t("не удалось создать ") + out_zip
	for d in dirs:
		_zip_walk(z, base, d)
	z.close()
	return ""


static func _zip_walk(z: ZIPPacker, base: String, rel: String) -> void:
	var full := base.path_join(rel)
	var da := DirAccess.open(full)
	if da == null:
		return
	for f in da.get_files():
		if f == "session.lock":
			continue
		z.start_file(rel.path_join(f))
		z.write_file(FileAccess.get_file_as_bytes(full.path_join(f)))
		z.close_file()
	for sub in da.get_directories():
		_zip_walk(z, base, rel.path_join(sub))
