class_name Launcher
extends RefCounted
## 1.7 «нажал — и ты в игре»: LANTERN сам запускает Minecraft (Bedrock) и подключает к миру.
##   minecraft://connect/?serverUrl=127.0.0.1&serverPort=<порт>  — открыть игру и сразу зайти на сервер (мост LANTERN)
##   minecraft://                                                  — просто открыть игру (мир друга виден во «Друзьях»)
## Android: Intent VIEW (OS.shell_open), Windows: ссылка minecraft:// (Minecraft из Microsoft Store).
## Java-издание ссылок не понимает — там мир появляется в «Сетевой игре» сам, а адрес всегда один: localhost.

const STORE := "https://play.google.com/store/apps/details?id=com.mojang.minecraftpe"
const COUNTDOWN := 3.0

static var last_url := ""       # что открывали последним (автотесты проверяют, не запуская игру)
static var opened := 0
static var auto_code := ""      # для какой комнаты игру уже открывали сами (один раз на подключение)


static func connect_url(port: int, host := "127.0.0.1") -> String:
	return "minecraft://connect/?serverUrl=%s&serverPort=%d" % [host, port]


## Открыть Minecraft. port > 0 — сразу подключить к мосту LANTERN на этом устройстве. → true, если система приняла ссылку.
static func open_bedrock(port := 0, why := "") -> bool:
	var url := connect_url(port) if port > 0 else "minecraft://"
	last_url = url
	AppLog.add("launch", "открываем Minecraft: %s (%s)" % [url, why])
	if Cfg.autotest != "":
		opened += 1
		return true
	var err := UI.open(url)
	if err != OK and port > 0:
		err = UI.open("minecraft://")
	if err != OK:
		AppLog.add("launch", "Minecraft не открылся (ошибка %d)" % err)
		if OS.get_name() == "Android":
			UI.open(STORE)
		return false
	opened += 1     # считаем только удачные: иначе экран входа писал «Minecraft открыт» после неудачи
	return true


## Java (ПК): адрес, который игрок добавляет один раз. Порт 25565 — стандартный, его не пишут.
static func java_address(port: int) -> String:
	return "localhost" if port == 25565 or port <= 0 else "localhost:%d" % port


## Карточка «Открываем Minecraft… 3» с кнопкой «Отмена». Сама считает секунды; по нулю вызывает fire.
## state: {"t": секунд осталось, "on": bool} — хранит экран (карточка может пересобираться при обновлении статуса).
class Countdown extends PanelContainer:
	var state: Dictionary
	var fire: Callable
	var cancel: Callable
	var title := L.t("Открываем Minecraft…")
	var sub := ""
	var _num: Label
	var _bar: Panel
	var _track: Control

	func _ready() -> void:
		add_theme_stylebox_override("panel", UI.glass(2, UI.R_XL, 18, UI.accent()))
		var v := UI.vbox(12)
		add_child(v)
		var row := UI.hbox(14)
		v.add_child(row)
		var ring := PanelContainer.new()
		ring.custom_minimum_size = Vector2(52, 52)
		ring.size_flags_vertical = Control.SIZE_SHRINK_CENTER
		ring.add_theme_stylebox_override("panel", UI.box(Color(UI.accent(), 0.18), 99, 0, UI.accent(), 2))
		_num = UI.label("3", 26, UI.accent().lightened(0.3), UI.font_black)
		_num.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
		_num.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
		ring.add_child(_num)
		row.add_child(ring)
		var tv := UI.vbox(2)
		tv.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		tv.size_flags_vertical = Control.SIZE_SHRINK_CENTER
		var tl := UI.para(title, 17, UI.TEXT)
		tl.add_theme_font_override("font", UI.font_black)
		tv.add_child(tl)
		if sub != "":
			tv.add_child(UI.para(sub, 14, UI.MUTED))
		row.add_child(tv)
		_track = Control.new()
		_track.custom_minimum_size.y = 4
		var bg := Panel.new()
		bg.add_theme_stylebox_override("panel", UI.box(Color(1, 1, 1, 0.1), 99))
		bg.set_anchors_preset(Control.PRESET_FULL_RECT)
		_track.add_child(bg)
		_bar = Panel.new()
		_bar.add_theme_stylebox_override("panel", UI.box(UI.accent(), 99))
		_bar.set_anchors_preset(Control.PRESET_FULL_RECT)
		_track.add_child(_bar)
		v.add_child(_track)
		v.add_child(UI.button(L.t("Отмена"), "secondary", func():
			state.on = false
			if cancel.is_valid():
				cancel.call()))
		_tick(0.0)

	func _process(d: float) -> void:
		if not state.get("on", false):
			return
		_tick(d)

	func _tick(d: float) -> void:
		state.t = float(state.get("t", COUNTDOWN)) - d
		_num.text = str(maxi(1, int(ceil(float(state.t)))))
		_bar.anchor_right = clampf(1.0 - float(state.t) / COUNTDOWN, 0.0, 1.0)
		if float(state.t) <= 0.0 and state.get("on", false):
			state.on = false
			set_process(false)
			if fire.is_valid():
				fire.call()


static func countdown(state: Dictionary, title: String, sub: String, fire: Callable, cancel: Callable) -> Countdown:
	var c := Countdown.new()
	c.state = state
	c.title = title
	c.sub = sub
	c.fire = fire
	c.cancel = cancel
	return c
