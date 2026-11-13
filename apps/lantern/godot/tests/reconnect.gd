extends RefCounted
## Автотест «reconnect»: приложение держит связь с relay, а tests/reconnect.js перезапускает/«вешает» relay
## и меряет, через сколько приложение снова «в сети». Здесь — только журнал состояний связи:
## <data>/conn.log — строки «мс состояние подробность» (время по часам системы, мс с 1970), конец — файл <data>/stop.


func run(t) -> int:
	var data: String = t.data
	var log_path := data.path_join("conn.log")
	var f := FileAccess.open(log_path, FileAccess.WRITE)
	var last := ""
	var t0 := Time.get_ticks_msec()
	while not FileAccess.file_exists(data.path_join("stop")):
		if Social.conn == "need_register":
			Social.register("Тест_Связи")
		var s := "%s %s" % [Social.conn, Social.net_state() if Social.has_method("net_state") else "-"]
		if s != last:
			last = s
			f.store_line("%d %s" % [int(Time.get_unix_time_from_system() * 1000.0), s])
			f.flush()
			t._log("связь: " + s)
			if t.shots != "" and DisplayServer.get_name() != "headless":
				await t.get_tree().create_timer(2.0).timeout   # плашка появляется через 1.5 с
				DirAccess.make_dir_recursive_absolute(t.shots)
				t.get_viewport().get_texture().get_image().save_png(t.shots.path_join("conn_%s.png" % s.replace(" ", "_")))
		if Time.get_ticks_msec() - t0 > 600000:
			break
		await t.get_tree().create_timer(0.05).timeout
	f.close()
	return 0
