class_name Report
extends RefCounted
## «Сообщить о проблеме» (1.6): собираем журнал приложения, хвост журнала сервера Minecraft и сведения об устройстве,
## вычищаем личное (токены, пароли, чужие IP) и отправляем на relay: POST <сайт>/report → номер отчёта.

const SERVER_LINES := 500


## Что отправим (без отправки) — для автотестов и «Посмотреть, что отправится».
static func collect(app: Node, kind := "manual", note := "") -> Dictionary:
	var d := {
		"app": "%s %s" % [Cfg.APP_NAME, Cfg.APP_VERSION],
		"os": "%s %s" % [OS.get_name(), OS.get_version()],
		"device": L.t("%s · %s · %d ядер · Godot %s") % [OS.get_model_name(), OS.get_locale(), OS.get_processor_count(), Engine.get_version_info().string],
		"kind": kind,
		"note": note.substr(0, 1000),
		"nick": String(Social.me.get("nick", "")) if Social.get("me") is Dictionary else "",
	}
	var st := {}
	if app:
		st["screen"] = String(app.get("current"))
		var rh: Dictionary = app.relay_host.stats()
		rh.erase("code")
		st["relay_host"] = rh
		st["relay_host_code"] = L.t("есть") if app.relay_host.stats().get("code", "") != "" else L.t("нет")
		var rj: Dictionary = app.relay_join.stats()
		st["relay_join"] = rj
		if app.server:
			var s: ServerHost = app.server
			var info := s._install_info()
			st["server"] = {"state": s.state, "edition": s.edition, "error": s.last_error, "players": s.players.size(),
				"version": info.get("mc_version", ""), "paper_build": info.get("paper_build", ""), "java": info.get("java_major", ""),
				"geyser": info.get("geyser_version", ""), "floodgate": info.get("floodgate_version", ""),
				"java_range": info.get("java_range", []), "bedrock_range": s.bedrock_range,
				"crossplay": s.opts.get("crossplay", false), "online_mode": s.opts.get("online_mode", true), "memory_mb": s.opts.get("memory_mb", 0),
				"issues": s.issues.map(func(i): return String(i.get("id", "")))}
		if app.own:
			st["own_world"] = {"active": app.own.active, "world": app.own.world.duplicate() if app.own.world is Dictionary else {}}
		var g := Cfg.game()
		st["room"] = {"mode": g.get("mode", ""), "cap": g.get("cap", 0), "password": g.get("password", "") != ""}
		d["minecraft"] = {"host_version": rj.get("host_version", ""), "host_protocol": rj.get("host_protocol", 0),
			"client_protocol": rj.get("client_protocol", 0), "server": st.get("server", {}).get("version", ""),
			"plays_on": Cfg.get_value("app", "play_on", "")}
	d["state"] = st
	var prev := AppLog.previous()
	d["app_log"] = AppLog.scrub(AppLog.text() + (L.t("\n----- прошлый запуск -----\n") + prev if prev != "" else ""))
	d["server_log"] = AppLog.scrub(server_log(app))
	return d


## Хвост logs/latest.log сервера (Paper, Geyser, Via*) или журнал из памяти (BDS).
static func server_log(app: Node) -> String:
	if app == null or app.server == null:
		return ""
	var s: ServerHost = app.server
	var out := ""
	var latest := s.server_dir().path_join("logs/latest.log")
	if FileAccess.file_exists(latest):
		var lines := FileAccess.get_file_as_string(latest).split("\n")
		out = "\n".join(lines.slice(max(0, lines.size() - SERVER_LINES)))
		# падения Paper: последний hs_err / crash-report
		var cr := s.server_dir().path_join("crash-reports")
		if DirAccess.dir_exists_absolute(cr):
			var files := DirAccess.get_files_at(cr)
			if files.size() > 0:
				files.sort()
				var txt := FileAccess.get_file_as_string(cr.path_join(files[files.size() - 1]))
				out += "\n----- %s -----\n%s" % [files[files.size() - 1], txt.substr(0, 20000)]
	else:
		out = "\n".join(s.history.slice(max(0, s.history.size() - SERVER_LINES)))
	return out


## Отправить. Вернёт {ok, id, error}.
static func send(app: Node, kind := "manual", note := "") -> Dictionary:
	var d := collect(app, kind, note)
	AppLog.add("report", "отправляем отчёт (%s), журнал %d байт, сервер %d байт" % [kind, String(d.app_log).length(), String(d.server_log).length()])
	var r: Dictionary = await Net.post_json(Cfg.SITE + "/report", d)
	if r.ok and r.data is Dictionary and r.data.has("id"):
		AppLog.add("report", "отчёт №%d отправлен" % int(r.data.id))
		return {"ok": true, "id": int(r.data.id)}
	var e := L.t("нет интернета или сайт LANTERN недоступен") if int(r.code) == 0 else (L.t("слишком часто — попробуйте через час") if int(r.code) == 429 else L.t("ошибка %d") % int(r.code))
	return {"ok": false, "error": e}


## Окно «Сообщить о проблеме». why — что случилось (для автоматического предложения после ошибки).
static func dialog(app: Control, kind := "manual", why := "") -> void:
	var v: VBoxContainer = Overlays.sheet(app, L.t("Сообщить о проблеме"),
		why if why != "" else L.t("Мы получим журнал приложения и сервера — без паролей и чужих адресов. Это поможет быстро найти ошибку."))
	var note := UI.line_edit("", L.t("Что случилось? (можно не писать)"))
	v.add_child(note)
	v.add_child(UI.para(L.t("Отправится: версия LANTERN, модель устройства, журнал работы и ваш ник. Пароли, ключи и IP других людей вырезаются."), 13, UI.DIM))
	var status := UI.para("", 15, UI.MUTED)
	status.visible = false
	v.add_child(status)
	var btn := UI.button(L.t("Отправить отчёт"), "primary")
	btn.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	v.add_child(btn)
	btn.pressed.connect(func():
		btn.disabled = true
		btn.text = L.t("Отправляем…")
		var r: Dictionary = await send(app, kind, note.text)
		if not is_instance_valid(btn):
			return
		status.visible = true
		if r.ok:
			status.text = L.t("Отчёт №%d отправлен — пришлите этот номер разработчику.") % r.id
			status.add_theme_color_override("font_color", UI.TEXT)
			btn.disabled = false
			btn.text = L.t("Скопировать номер")
			for c in btn.pressed.get_connections():
				btn.pressed.disconnect(c.callable)
			btn.pressed.connect(func():
				UI.copy(L.t("Отчёт LANTERN №%d") % r.id)
				if app.has_method("toast"):
					app.toast(L.t("Номер скопирован")))
		else:
			status.text = L.t("Не отправилось: %s.") % r.error
			btn.disabled = false
			btn.text = L.t("Попробовать ещё раз"))


## Маленькая кнопка «Сообщить о проблеме» для экранов.
static func button(app: Control, kind := "manual", why := "") -> Button:
	var b := UI.button(L.t("Сообщить о проблеме"), "ghost", func(): dialog(app, kind, why), "info")
	b.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART     # 1.8.2: «Повідомити про проблему» на 320 dp — в две строки
	return b
