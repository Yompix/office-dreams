extends ScrollContainer
## Копии миров: резервные копии на этом устройстве, облако LANTERN Pro, получение копии по коду от друга
## или со второго устройства. Восстановление — в папку сервера (старый мир не удаляется, а переименовывается).

var app
var arg

var _local: VBoxContainer
var _cloud: VBoxContainer
var _code_in: LineEdit
var _busy := false
var last_result := {}      # для автотестов: последнее действие {what, ok, error, ...}


func _ready() -> void:
	horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	var v := UI.vbox(10)
	v.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	add_child(v)
	app.set_title(L.t("Копии миров"), (L.t("Восстановить, отправить в облако или другу") if UI.compact() else L.t("Резервная копия — zip с папкой мира. Её можно восстановить, отправить в облако (Pro) или передать другу по коду.")) if Cfg.paid() else L.t("Резервные копии миров на этом устройстве"))
	# --- получить по коду (самое частое действие — сверху)
	v.add_child(UI.section(L.t("Получить копию по коду")))
	var gp := UI.panel(16)
	var gv := UI.vbox(10)
	gp.add_child(gv)
	var gr := UI.hbox(8)
	_code_in = UI.line_edit("", L.t("Код, например 7KQ-M2X"))
	_code_in.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_code_in.max_length = 12
	_code_in.add_theme_font_override("font", UI.font_bold)
	gr.add_child(_code_in)
	gr.add_child(UI.button(L.t("Скачать"), "tonal", func(): fetch_shared(_code_in.text), "download"))
	gv.add_child(gr)
	gv.add_child(UI.para(L.t("Код действует сутки. Так можно перенести мир на второй компьютер или получить его от друга."), 12, UI.DIM))
	v.add_child(gp)
	# --- на этом устройстве
	var lh := UI.hbox(8)
	var ls := UI.section(L.t("На этом устройстве"))
	ls.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	lh.add_child(ls)
	if not Cfg.mobile():
		lh.add_child(UI.button(L.t("Папка"), "link", func(): UI.open(ProjectSettings.globalize_path(Cfg.sub("backups"))), "folder"))
	v.add_child(lh)
	_local = UI.vbox(8)
	v.add_child(_local)
	# --- облако
	var ch := UI.hbox(8)
	ch.add_child(UI.section(L.t("Облако LANTERN")))
	var pp := UI.pill("Pro", UI.AMBER)
	ch.add_child(pp)
	v.add_child(ch)
	_cloud = UI.vbox(8)
	v.add_child(_cloud)
	ch.visible = Cfg.paid()
	_cloud.visible = Cfg.paid()
	v.add_child(UI.gap(16))
	Social.changed.connect(func(w): if w in ["cloud", "eco", "conn"] and is_inside_tree(): _render_cloud())
	_render_local()
	_render_cloud()
	if Social.online():
		Social.request("cloud_list")


static func _mb(n: int) -> String:
	return L.t("%.1f МБ") % (n / 1048576.0) if n >= 104858 else L.t("%d КБ") % maxi(1, n / 1024)


static func _date(unix: int) -> String:
	var d := Time.get_datetime_dict_from_unix_time(unix + int(Time.get_time_zone_from_system().get("bias", 0)) * 60)
	return "%02d.%02d.%d %02d:%02d" % [d.day, d.month, d.year, d.hour, d.minute]


func _render_local() -> void:
	for c in _local.get_children():
		c.queue_free()
	var list: Array = app.server.list_backups()
	if list.is_empty():
		_local.add_child(UI.list_row(UI.icon_tile("download", UI.MUTED, 44, 22), L.t("Копий пока нет"), L.t("«Резервная копия» на экране мира — или скачайте по коду"), null))
		return
	for b in list:
		var info := ServerHost.inspect_backup(b.path)
		var ed: String = {"java": "Java", "bedrock": "Bedrock"}.get(info.get("edition", ""), "?")
		var r := UI.list_row(UI.icon_tile("java" if info.get("edition", "") == "java" else "phone", UI.JAVA if info.get("edition", "") == "java" else UI.BEDROCK, 44, 22), L.t("Мир «%s»") % info.get("world", "?"), "%s · %s · %s" % [ed, _mb(b.size), _date(b.time)], null)
		var v: VBoxContainer = r.get_meta("text")
		var row := UI.flow(6)
		var rb := UI.button(L.t("Восстановить"), "tonal", func(): restore(b.path), "refresh")
		rb.disabled = not info.ok or Cfg.mobile()
		rb.tooltip_text = "" if info.ok else info.get("error", "")
		row.add_child(rb)
		if Cfg.paid():
			var ub := UI.button(L.t("В облако"), "secondary", func(): upload(b.path), "upload")
			ub.disabled = not Social.live()     # загрузка в облако — только при живом соединении (не по сохранённому профилю)
			row.add_child(ub)
		row.add_child(UI.icon_button("trash", func():
			DirAccess.remove_absolute(b.path)
			_render_local(), L.t("Удалить"), UI.MUTED))
		for btn in row.get_children():
			btn.custom_minimum_size.y = 40
		v.add_child(UI.gap(4))
		v.add_child(row)
		_local.add_child(r)


func _render_cloud() -> void:
	for c in _cloud.get_children():
		c.queue_free()
	if not Social.online():
		_cloud.add_child(UI.list_row(UI.icon_tile("wifi", UI.YELLOW, 44, 22), L.t("Нет связи с сервером LANTERN"), (Social.net_text() if Social.net_text() != "" else L.t("Облако появится, когда вернётся интернет")), null))
		return
	var c: Dictionary = Social.cloud
	if c.is_empty():
		_cloud.add_child(FX.skeleton(72))
		return
	if not c.get("enabled", false):
		_cloud.add_child(UI.list_row(UI.icon_tile("cloud", UI.MUTED, 44, 22), L.t("Облако выключено на этом сервере"), L.t("Копии можно хранить на устройстве"), null))
		return
	if not c.get("pro", false):
		var pp := UI.panel(16)
		var pv := UI.vbox(10)
		pp.add_child(pv)
		var ph := UI.hbox(12)
		ph.add_child(UI.icon_tile("cloud", UI.AMBER, 44, 22))
		var pt := UI.para(L.t("До %d копий по %d МБ в облаке — с LANTERN Pro. Не пропадут, даже если сломается компьютер.") % [int(c.get("slots", 3)), int(c.get("max_mb", 100))], 14, UI.TEXT)
		pt.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		ph.add_child(pt)
		pv.add_child(ph)
		var pb := UI.button(L.t("Узнать о Pro"), "primary", func(): app.go("shop", true, {"kind": "pro"}))
		pv.add_child(pb)
		_cloud.add_child(pp)
	else:
		_cloud.add_child(UI.para(L.t("До %d копий по %d МБ. Самая старая заменяется новой.") % [int(c.get("slots", 3)), int(c.get("max_mb", 100))], 12, UI.DIM))
	for it in c.get("list", []):
		var r := UI.list_row(UI.icon_tile("cloud", UI.BEDROCK, 44, 22), String(it.name).get_basename(), "%s · %s" % [_mb(int(it.size)), _date(int(it.ts) / 1000)], null)
		var v: VBoxContainer = r.get_meta("text")
		if String(it.get("share", "")) != "":
			var sr := UI.hbox(8)
			sr.add_child(UI.label(L.t("Код для друга:"), 13, UI.MUTED))
			var cl := UI.label(RoomCode.pretty(it.share), 18, UI.AMBER, UI.font_black)
			sr.add_child(cl)
			sr.add_child(UI.icon_button("copy", func(): UI.copy(RoomCode.pretty(it.share)); app.toast(L.t("Код скопирован")), L.t("Копировать"), UI.MUTED))
			v.add_child(sr)
		var row := UI.flow(6)
		row.add_child(UI.button(L.t("Скачать"), "tonal", func(): download_cloud(it), "download"))
		row.add_child(UI.button(L.t("Код для друга"), "secondary", func(): Social.request("cloud_share", {"id": it.id}), "share"))
		row.add_child(UI.icon_button("trash", func(): Social.request("cloud_delete", {"id": it.id}), L.t("Удалить"), UI.MUTED))
		for btn in row.get_children():
			btn.custom_minimum_size.y = 40
		v.add_child(UI.gap(4))
		v.add_child(row)
		_cloud.add_child(r)


func _auth() -> PackedStringArray:
	return PackedStringArray(["Authorization: Bearer " + Social.token])


func restore(path: String) -> void:
	var r: Dictionary = await app.server.restore_backup(path)
	last_result = r.merged({"what": "restore"})
	if r.ok:
		app.toast(L.t("Мир «%s» восстановлен — он запустится при следующем «Создать мир» (%s)") % [r.world, "Java" if r.edition == "java" else "Bedrock"], UI.JAVA)
	else:
		app.toast(L.t("Не получилось: ") + L.t(String(r.error)), UI.RED)


func upload(path: String) -> void:
	if _busy:
		return
	if not Social.cloud.get("pro", false):
		app.toast(L.t("Облачные копии — функция LANTERN Pro"), UI.YELLOW)
		app.go("shop", true, {"kind": "pro"})
		return
	_busy = true
	app.toast(L.t("Отправляем копию в облако…"))
	var r: Dictionary = await Net.put_file(Social.http_base() + "/cloud/upload?name=" + path.get_file().uri_encode(), path, _auth())
	_busy = false
	last_result = r.merged({"what": "upload"})
	if r.ok:
		app.toast(L.t("Копия в облаке ✓"), UI.JAVA)
		Social.request("cloud_list")
	else:
		app.toast(L.t("Не получилось: ") + L.t(String(r.error)), UI.RED)


func download_cloud(it: Dictionary) -> void:
	await _download(Social.http_base() + "/cloud/file/" + String(it.id), String(it.name))


func fetch_shared(code: String) -> void:
	var c := code.strip_edges().to_upper().replace("-", "").replace(" ", "")
	if c.length() != 6:
		app.toast(L.t("Код — 6 символов"), UI.YELLOW)
		return
	await _download(Social.http_base() + "/cloud/share/" + c, "")


func _download(url: String, name: String) -> void:
	if _busy:
		return
	if Social.token == "":
		return
	_busy = true
	var stamp := Time.get_datetime_string_from_system().replace(":", "-").replace("T", "_")
	var fname := name if name.ends_with(".zip") else L.t("из-облака-%s.zip") % stamp
	var dest := Cfg.sub("backups").path_join(fname.get_file().replace("/", "_"))
	if FileAccess.file_exists(dest):
		dest = dest.get_basename() + "-" + stamp + ".zip"
	app.toast(L.t("Скачиваем копию…"))
	var r: Dictionary = await Net.download(url, dest, {}, Callable(), _auth())
	_busy = false
	last_result = r.merged({"what": "download", "path": dest})
	if r.ok:
		var info := ServerHost.inspect_backup(dest)
		if not info.ok:
			DirAccess.remove_absolute(dest)
			app.toast(L.t("Это не копия мира: ") + L.t(String(info.error)), UI.RED)
			last_result.ok = false
		else:
			app.toast(L.t("Копия мира «%s» скачана — нажмите «Восстановить»") % info.world, UI.JAVA)
	else:
		app.toast(L.t("Не получилось: ") + L.t(String(r.error)), UI.RED)
	if is_inside_tree():
		_render_local()
