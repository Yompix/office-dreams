class_name VAdapter
extends RefCounted
## 2.0: «Подключить» — поставить виртуальный адаптер LANTERN и связать его с VNet (UDP 127.0.0.1, одноразовый ключ).
##   Windows: lantern-net.exe + wintun.dll рядом с LANTERN.exe. Первый раз Windows спросит разрешение (UAC) — помощник
##            регистрирует задачу планировщика «LANTERN Net» с правами администратора; дальше подключение без вопросов
##            (приложение кладёт запрос в %LOCALAPPDATA%\LANTERN\net-request.json и запускает задачу).
##   Android: системный запрос «Разрешить VPN?» (один раз), служба LanternVpnService.
##   Linux:   lantern-net (root или pkexec). Автотесты: --vnet_helper=<путь> --vnet_netns=1 (свой namespace).
## Всё медленное (UAC, ожидание) — в отдельном потоке: окно не замирает.

const ANDROID_VPN := "com.godot.game.LanternVpnService"
const TASK := "LANTERN Net"

static var _th: Thread


static func supported() -> bool:
	return OS.get_name() in ["Windows", "Android", "Linux"]


static func helper_path() -> String:
	if Cfg.autotest_args.has("vnet_helper"):
		return String(Cfg.autotest_args["vnet_helper"])
	var dir := OS.get_executable_path().get_base_dir()
	return dir.path_join("lantern-net.exe" if OS.get_name() == "Windows" else "lantern-net")


## Запустить адаптер для текущей сети. vnet — узел VNet (есть адрес и порт), info — snapshot().
static func start(vnet: VNet) -> void:
	var s := vnet.snapshot()
	var ep := vnet.adapter_endpoint()
	if str(s.get("ip", "")) == "" or int(ep.port) == 0:
		vnet.set_adapter_state("error", "no_net")
		return
	vnet.set_adapter_state("starting")
	var req := {"port": int(ep.port), "token": str(ep.token), "ip": str(s.ip), "mask": 24, "mtu": int(s.get("mtu", 1380)), "metric": 1}
	AppLog.add("vnet", "адаптер — запуск (%s)" % OS.get_name())
	if OS.get_name() == "Android":
		_android(vnet, req)
		return
	if not FileAccess.file_exists(helper_path()):
		vnet.set_adapter_state("error", "no_helper")
		return
	if _th != null and _th.is_alive():
		return
	if _th != null:
		_th.wait_to_finish()
	_th = Thread.new()
	_th.start(func(): _desktop(vnet, req))


static func stop(vnet: VNet) -> void:
	if OS.get_name() == "Android" and Engine.has_singleton("AndroidRuntime"):
		var cls = JavaClassWrapper.wrap(ANDROID_VPN)
		if cls:
			cls.stop()
	vnet.set_adapter_state("none")    # помощник на ПК сам выйдет по «quit» / без «alive»


static func _android(vnet: VNet, req: Dictionary) -> void:
	var cls = JavaClassWrapper.wrap(ANDROID_VPN)
	if cls == null:
		vnet.set_adapter_state("error", "no_service")
		return
	# Java ждёт разрешения VPN сама (до 2 минут) и потом поднимает службу; ответ придёт «hello» от службы
	var r: String = str(cls.connectVpn(int(req.port), str(req.token), str(req.ip), 24, int(req.mtu)))
	AppLog.add("vnet", "android " + r)
	# служба с уведомлением держит LANTERN живым, пока вы в игре (как при входе к другу в Minecraft)
	var keep = JavaClassWrapper.wrap(OwnWorld.ANDROID_SERVICE)
	if keep and not r.begins_with("error"):
		keep.startJoin(L.t("LANTERN — сеть «Любые игры» включена"), L.t("Можно играть: друзья видят вас по локальной сети"))
	if r == "need_permission":
		vnet.set_adapter_state("starting", "permission")
	elif r.begins_with("error"):
		vnet.set_adapter_state("error", r)


static func _desktop(vnet: VNet, req: Dictionary) -> void:
	var h := helper_path()
	var args := ["--port", str(req.port), "--token", str(req.token), "--ip", str(req.ip), "--mask", "24", "--mtu", str(req.mtu)]
	match OS.get_name():
		"Windows":
			var err := _windows(h, req, args)
			if err != "":
				vnet.set_adapter_state("error", err)
		_:
			if Cfg.autotest_args.get("vnet_netns", "") == "1":
				args.append("--netns")
			var run := h
			if OS.get_environment("USER") != "root" and OS.get_environment("EUID") != "0" and FileAccess.file_exists("/usr/bin/pkexec") and not Cfg.autotest_args.has("vnet_helper"):
				args.push_front(h)
				run = "/usr/bin/pkexec"
			var pid := OS.create_process(run, args)
			if pid <= 0:
				vnet.set_adapter_state("error", "spawn")


## Windows: задача планировщика (UAC — только первый раз), иначе — запуск с правами администратора (UAC каждый раз).
static func _windows(h: String, req: Dictionary, args: Array) -> String:
	if Cfg.get_value("net", "task_broken", false):
		return _runas(h, args)
	if not _task_exists():
		var out := []
		var ps := "$p = Start-Process -FilePath '%s' -ArgumentList '--install' -Verb RunAs -WindowStyle Hidden -Wait -PassThru; exit $p.ExitCode" % h.replace("'", "''")
		var code := OS.execute("powershell", ["-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", ps], out, true)
		AppLog.add("vnet", "установка задачи → %d" % code)
		if code != 0 and not _task_exists():
			return "uac"           # отказались в окне Windows или нет прав
	var dir := OS.get_environment("LOCALAPPDATA").path_join("LANTERN")
	DirAccess.make_dir_recursive_absolute(dir)
	var f := FileAccess.open(dir.path_join("net-request.json"), FileAccess.WRITE)
	if f == null:
		return "request_file"
	f.store_string(JSON.stringify(req))
	f.close()
	var out2 := []
	var rc := OS.execute("schtasks", ["/run", "/tn", TASK], out2, true)
	if rc == 0:
		return ""
	AppLog.add("vnet", "schtasks /run → %d, запуск напрямую" % rc)
	return _runas(h, args)


## Запасной путь — сразу с правами администратора (Windows спросит разрешение каждый раз).
static func _runas(h: String, args: Array) -> String:
	var argl := " ".join(args.map(func(a): return "'%s'" % str(a)))
	var ps2 := "Start-Process -FilePath '%s' -ArgumentList %s -Verb RunAs -WindowStyle Hidden" % [h.replace("'", "''"), argl.replace(" ", ",")]
	var c2 := OS.execute("powershell", ["-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", ps2], [], true)
	return "" if c2 == 0 else "uac"


static func _task_exists() -> bool:
	var out := []
	return OS.execute("schtasks", ["/query", "/tn", TASK], out, true) == 0


static func error_text(code: String) -> String:
	match code:
		"uac": return L.t("Windows не дал разрешение. Нажмите «Подключить» ещё раз и ответьте «Да» в окне Windows.")
		"no_helper": return L.t("Не найден файл lantern-net.exe рядом с LANTERN — распакуйте архив LANTERN целиком.")
		"no_service", "no_net": return L.t("Сначала создайте сеть или войдите в неё.")
		"permission": return L.t("Разрешите LANTERN создать VPN — так игры на телефоне увидят друзей.")
		"lost": return L.t("Адаптер отключился. Нажмите «Подключить» ещё раз.")
		"timeout":
			if OS.get_name() == "Windows":
				Cfg.set_value("net", "task_broken", true)   # задача планировщика не сработала — дальше запуск напрямую (с UAC)
			return L.t("Адаптер отключился. Нажмите «Подключить» ещё раз.")
		"": return L.t("Не получилось запустить помощника сети.")
		"spawn": return L.t("Не получилось запустить помощника сети.")
		"request_file": return L.t("Нет доступа к папке LANTERN.")
	if code.contains("wintun") or code.contains("administrator") or code.contains("TUNSETIFF"):
		return L.t("Не получилось создать сетевой адаптер (нужны права администратора).") + " " + code
	return code
