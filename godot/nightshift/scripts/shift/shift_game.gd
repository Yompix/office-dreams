class_name ShiftGame
extends Node
## Смена — перенос shiftGame / shiftTick / shiftEvent и окон смены (engine.js 948–1241): часы ночи между сюжетными сценами.
## Камеры (запись по слоям, tools/godot/bake_shift.js), странности по минутам часа, «Записать» (7 видов), пульт (вход, лифт,
## свет 13 этажей), журнал, звонки, СМС, задания к сроку, «держи», обучение с диспетчером Верой. Пропустил три странности —
## оно приходит к будке; засмотрелся на помехи, открыл ей, ответил на звонок — смерть.

signal ended(next_scene: String)
signal died(over_scene: String)

const SEC_MIN := 7.0   # секунд на игровую минуту по умолчанию
const KINDS := ["Свет", "Дверь", "Лифт", "Помехи", "Кто-то", "Звук", "Другое"]
const CAM_KEY := {"01": "cam01", "02": "cam02", "03": "cam03", "04": "cam04", "07": "cam07", "10": "cam10", "m2": "cam_m2"}
const STARE_T := [0.0, 1.3, 2.6, 3.8]
const TUT_DO := {"t02": "Нажми камеру 03 — лифты", "t03": "Смотри на табло лифта: «13»", "t04": "Нажми «Записать» → «Лифт»",
	"t06": "MIC слева вверху: столбики прыгают — звук", "t07": "Открой «Пульт»", "t08": "Открой «Журнал»"}
## одна причина смерти — разные финалы по очереди; «ладонь на объективе» снята на CAM 07, когда Гаса там нет
const DEATH_ALT := {"over_stare": ["over_stare", "over_stare_b"]}

var game                      # главный узел (звук, голос, субтитры, флаги)
var player: ShotPlayer
var ui: ShiftUI
var S: Dictionary             # story.shift
var index := {}               # слои камер: bake/shift/index.json
static var shk := {}          # переходит из часа в час: пульт, записи, кто где, кнопка 13
static var alt_n := {}        # какой по счёту финал «засмотрелся»
static var tut_done := false  # обучение пройдено (в следующий раз не начинается само)
var shf := {}                 # этот час
var tut = null                # обучение идёт: { seen }
var spd := 1.0                # темп (обучение — время стоит)
var T := 0.0
var rng := RandomNumberGenerator.new()


static func shk_new() -> Dictionary:
	return {"door": 0, "lift": 0, "lights": {}, "log": [], "st": {}, "c13": false, "read": false}


func start(th: Dictionary, p_game, p_player: ShotPlayer, p_ui: ShiftUI) -> void:
	game = p_game
	player = p_player
	ui = p_ui
	S = StoryDB.story.shift
	if index.is_empty() and FileAccess.file_exists("res://bake/shift/index.json"):
		index = JSON.parse_string(FileAccess.get_file_as_string("res://bake/shift/index.json"))
	var seg: Dictionary = S.segs[String(th.shift)]
	if th.get("fresh", false) or shk.is_empty():
		shk = shk_new()
	if seg.has("st"):
		shk.st.merge(seg.st, true)   # что на камерах с начала часа (после 03:00 Гаса на седьмом нет)
	shf = {"id": String(th.shift), "seg": seg, "m0": _to_min(seg.from), "m1": _to_min(seg.to), "M": float(_to_min(seg.from)), "gm": 0.0,
		"gz": 0.0, "cam": String(seg.get("cam", "02")), "camT": T, "act": [], "miss": 0, "vq": [], "pop": false, "warn13": false,
		"ended": false, "mic": 0.0, "task": null}
	shf.ev = []
	for e in seg.events:
		var x: Dictionary = (e as Dictionary).duplicate(true)
		x.m = float(_to_min(e.at))
		shf.ev.append(x)
	if seg.has("rnd"):   # случайные странности часа
		var pool: Array = seg.rnd.pool.duplicate()
		for i in int(seg.rnd.n):
			if pool.is_empty():
				break
			var id: String = pool.pop_at(rng.randi() % pool.size())
			shf.ev.append({"m": shf.m0 + 6 + rng.randf() * maxf(1.0, shf.m1 - shf.m0 - 14), "an": id})
	shf.ev.sort_custom(func(a, b): return a.m < b.m)
	ui.build(_cams(), shf.cam, game.dlg.theme)
	ui.cam_pressed.connect(cam)
	ui.log_pressed.connect(log_open)
	ui.panel_pressed.connect(panel_open)
	ui.book_pressed.connect(book_open)
	ui.help_pressed.connect(help_open)
	ui.tut_skip.connect(func(): tut_end(true))
	game._music(String(seg.get("music", "shift")))
	var will_tut: bool = shf.id == "S1" and not tut_done and not game.autoplay
	if not will_tut:
		ui.toast(String(seg.get("hint", "")))
	else:
		get_tree().create_timer(0.9).timeout.connect(tutorial)
	_task()


func _cams() -> Array:
	var c: Array = shf.seg.cams.duplicate()
	if shk.c13:
		c.append("13")
	return c


static func _to_min(s: String) -> int:
	var p := s.split(":")
	var h := int(p[0])
	return ((h + 24) if h < 12 else h) * 60 + int(p[1])


static func min_str(v: float) -> String:
	return "%02d:%02d" % [int(floor(v / 60.0)) % 24, int(floor(v)) % 60]


## Что сейчас видно на камерах: пульт + кто где + активные странности (shiftProps).
func props() -> Dictionary:
	var p: Dictionary = shk.st.duplicate()
	p.clk = shf.M
	p.l7 = 1 if shk.lights.get(7, 0) else 0
	p.l10 = 1 if shk.lights.get(10, 0) else 0
	if shk.st.get("blackout"):
		p.merge({"hallDark": 1, "stDark": 1, "mOff": 1, "l7": 0, "l10": 0}, true)
	for a in shf.act:
		p.merge(a.def.get("props", {}), true)
	if shf.id == "S6" or shf.id == "A1":
		p.noEthan = 1   # Итан не за столом — на камере будки его нет
	return p


static func stare_n(k: float) -> int:
	return 0 if k < STARE_T[1] else (1 if k < STARE_T[2] else (2 if k < STARE_T[3] else 3))


## Основа камеры и слои по состоянию смены.
func view_ids(c: String) -> Array:
	var key: String = CAM_KEY.get(c, "")
	if key == "" or not index.has(key):
		return []
	var st := props()
	var ids := ["shift/" + String(index[key].base)]
	for L in index[key].layers:
		var name := String(L.id).split("@")[1]
		if name.begins_with("st_"):
			continue
		var ok := true
		for k in L.when:
			if not st.has(k) or str(st[k]) != str(L.when[k]):
				ok = false
				break
		if ok:
			ids.append("shift/" + String(L.id))
	for a in shf.act:
		if a.def.get("stare", false) and String(a.def.cam) == c:
			var id := "shift/%s@st_%s_%d" % [key, a.id, stare_n(float(a.get("look", 0.0)))]
			if ResourceLoader.exists("res://bake/" + id + ".json") or FileAccess.file_exists("res://bake/" + id + ".json"):
				ids.append(id)
	return ids


# ---------- реплики во время смены ----------
## Реплика: голос + слова по таймингу; нет озвучки — печатается текст. Встают в очередь друг за другом.
func say_v(k: String, delay := 0.0) -> Dictionary:
	var at := T
	for v in shf.vq:
		at = maxf(at, v.t0 + v.show + 0.25)
	var has_t := not StoryDB.line(k).is_empty()
	var d := StoryDB.dur(k) if has_t else maxf(2.4, String(S.get("lines", {}).get(k, "")).length() / 14.0 + 1.0)
	var it := {"k": k, "t0": at + delay, "d": d, "show": d, "started": false}
	shf.vq.append(it)
	return it


func cur_line() -> Dictionary:
	for v in shf.vq:
		if T >= v.t0 and (T < v.t0 + v.show + 0.4 or v.get("hold", false)):
			return v
	return {}


# ---------- ход часа ----------
func _process(delta: float) -> void:
	if shf.is_empty() or shf.ended:
		return
	var dt := minf(0.1, delta)
	T += dt
	shf.gz += dt
	shf.gm += dt * spd / float(shf.seg.get("spm", SEC_MIN))
	shf.M = shf.m0 + shf.gm
	game.last_time = min_str(shf.M)
	game.sec_base = int(fmod(shf.M, 1.0) * 60.0)
	game.clock_t0 = game.T
	for v in shf.vq:   # голос по очереди
		if not v.started and T >= v.t0:
			v.started = true
			game._say(v.k)
	for e in shf.ev:
		if not e.get("done", false) and shf.M >= e.m:
			e.done = true
			event(e)
			if shf.ended:
				return
	var K := SEC_MIN / float(shf.seg.get("spm", SEC_MIN))   # окна странностей — в настоящих секундах
	for a in shf.act.duplicate():
		var d: Dictionary = a.def
		if shf.cam == String(d.cam) and d.has("snd") and T - shf.camT > 0.3 and T >= float(a.get("sndT", 0.0)):   # камера слышит
			var sn: Array = d.snd
			a.sndT = T + (float(sn[2]) if sn.size() > 2 else 6.0) * (0.8 + rng.randf() * 0.4)
			game._play(String(sn[0]), (sn[1] if sn.size() > 1 and sn[1] is Dictionary else {}).merged({"gain": 0.7}))
			shf.mic = 1.0
		if shf.cam == String(d.cam) and not shf.pop and T - shf.camT > 0.4:
			if not a.has("seen"):
				a.seen = T
				if d.has("say"):
					say_v(String(d.say))
			if d.get("stare", false):
				a.look = float(a.get("look", 0.0)) + dt
				if a.look > 5.0:
					die(String(d.get("death", "over_stare")), String(d.cam))
					return
				if a.look > 2.6 and not a.get("warned", false):
					a.warned = true
					game._play("breath_close", {"gain": 0.6})
		else:
			a.look = 0.0
		if d.get("trap") == "door" and not shk.door and shf.M > a.m + 4 * K:
			die(String(d.get("die", "over_door")))
			return
		if shf.M > a.m + float(d.get("win", 15)) * K:
			miss(a)
		if shf.ended:
			return
	var tk = shf.task
	if tk != null and tk.get("sayAt") != null and shf.M >= float(tk.sayAt):
		tk.sayAt = null
		say_v(String(tk.say))
	if tk != null and shf.M > _to_min(tk.by) and not tk.get("done", false):
		tk.done = true
		var all_ok := true
		for n in tk.get("need", []):
			if not shk.get(n, 0):
				all_ok = false
		if not all_ok:
			shf.miss += 1
			ui.toast("Не успел к сроку.")
			if shf.miss >= 3:
				die("over_come")
				return
		_task()
	shf.mic = float(shf.mic) * 0.94
	if game.autoplay:
		_bot()
	if shf.M >= shf.m1:
		end()


## Автопрогон (проверка без человека): идёт к странности, улаживает её как надо, к сроку запирает вход и останавливает лифт,
## на помехи не засматривается, трубку не берёт.
func _bot() -> void:
	if shf.pop or tut != null:
		return
	for n in ["door", "lift"]:
		if shf.task != null and n in shf.task.get("need", []) and not shk[n]:
			shk[n] = 1
			check()
	for a in shf.act:
		var d: Dictionary = a.def
		if String(d.get("fix", "log")) == "none":   # Гас на седьмом, тень пальмы — не странность, улаживать нечего
			continue
		if d.get("stare", false) and float(a.get("look", 0.0)) > 1.5:
			for c in shf.seg.cams:
				if c != d.cam:
					cam(c)
					break
			continue
		if shf.cam != String(d.cam):
			cam(String(d.cam))
			return
		if T - shf.camT < 0.6:
			return
		var f := String(d.get("fix", "log"))
		if f.begins_with("light:"):
			shk.lights[int(f.substr(6))] = 0
			check()
		elif f == "lift":
			shk.lift = 1
			check()
		elif f == "door":
			shk.door = 1
			check()
		elif f == "log":
			shk.log.append({"m": min_str(shf.M), "cam": shf.cam, "k": String(d.get("kinds", [d.get("kind", "")])[0]), "ok": true})
			resolve(a, "Записано.")
		return


func event(e: Dictionary) -> void:
	if not game._flag_ok(e.get("if")):
		return
	game._set_flags(e.get("set"))
	if e.has("an"):
		var def: Dictionary = S.an.get(e.an, {})
		if def.is_empty() or shf.act.any(func(a): return a.id == e.an):
			return
		var busy: bool = shf.act.any(func(a): return a.def.cam == def.cam) or (def.get("need") == "locked" and not shk.door) \
			or (def.get("need") == "nogus" and shk.st.get("gus")) or (def.get("need") == "gus" and not shk.st.get("gus"))
		if busy:   # камера занята или не к месту — позже
			var K := SEC_MIN / float(shf.seg.get("spm", SEC_MIN))
			if shf.M + 4 * K < shf.m1 - 6:
				e.done = false
				e.m = shf.M + (3 + rng.randf() * 3) * K
			return
		shf.act.append({"id": String(e.an), "def": def, "m": shf.M})
		if def.has("light"):
			shk.lights[int(def.light)] = 1   # свет включился сам — на пульте этаж загорелся
		if def.get("unlock"):
			shk.door = 0   # замок входа отщёлкнулся сам
		if def.get("trap") == "door" and not shk.door:
			ui.toast("Вход не заперт!")
		if def.has("sfx"):
			game._play(String(def.sfx[0]), def.sfx[1] if def.sfx.size() > 1 else {})
		if def.has("notice"):
			ui.toast(String(def.notice))
	if e.has("say"):
		say_v(String(e.say))
	if e.has("sfx"):
		game._play(String(e.sfx[0]), e.sfx[1] if e.sfx.size() > 1 else {})
	if e.has("radio"):   # по рации: щелчок и голоса (Гас на камере 07 сперва отставляет швабру — 2 с)
		var gus: bool = shk.st.get("gus", false) and StoryDB.who_of(String(e.radio[0])) == "ГАС"
		var dl := 2.0 if gus else 0.0
		get_tree().create_timer(dl).timeout.connect(func(): game._play("click", {"gain": 0.4}))
		for i in e.radio.size():
			say_v(String(e.radio[i]), dl if i == 0 else 0.0)
	if e.has("sms"):
		_sms(e.sms)
	if e.has("state"):
		shk.st.merge(e.state, true)
	if e.has("c13"):
		shk.c13 = true
		ui.build(_cams(), shf.cam, game.dlg.theme)
		_task()
	if e.has("hint"):
		ui.toast(String(e.hint))
	if e.has("task"):
		shf.task = (e.task as Dictionary).duplicate(true)
		_task()
	if e.has("call"):
		call_open((e.call as Dictionary).duplicate(true))
	if e.has("hold"):
		hold_open(e.hold)


func resolve(a: Dictionary, how: String) -> void:
	shf.act.erase(a)
	game._play("click", {"gain": 0.5})
	if a.def.has("ok"):
		say_v(String(a.def.ok))
	elif how != "":
		ui.toast(how)
	_task()


## После записи или пульта: что из странностей уже улажено.
func check() -> void:
	for a in shf.act.duplicate():
		var f := String(a.def.get("fix", "log"))
		if f.begins_with("light:") and not shk.lights.get(int(f.substr(6)), 0):
			resolve(a, "Свет выключен.")
		elif f == "lift" and shk.lift:
			resolve(a, "Лифт остановлен.")
		elif f == "door" and shk.door:
			resolve(a, "Вход заперт.")
	_task()


func miss(a: Dictionary) -> void:
	shf.act.erase(a)
	if String(a.def.get("fix", "log")) == "none":
		return
	shf.miss += 1
	if game.autoplay:
		print("  пропуск ", a.id, " (", a.def.cam, ", ", a.def.get("fix", "log"), ") ", min_str(shf.M), " камера ", shf.cam)
	game._play("heart", {"gain": 0.9})
	get_tree().create_timer(0.7).timeout.connect(func(): game._play("heart", {"gain": 0.7}))
	game._play("steps_barefoot", {"gain": 0.35, "cut": 1.6})
	ui.toast("" if shf.miss >= 3 else ["Что-то упустил…", "Где-то в здании хлопнула дверь.", "Шаги. Далеко. Или нет."][mini(2, shf.miss - 1)])
	if shf.miss >= 3:
		die("over_come")


func die(id: String, c := "") -> void:
	if shf.ended:
		return
	shf.ended = true
	ui.pop_close()
	if DEATH_ALT.has(id):
		var alt: Array = DEATH_ALT[id].filter(func(x): return x != "over_stare_b" or (c == "07" and not shk.st.get("gus")))
		if not alt.is_empty():
			var n: int = alt_n.get(id, 0)
			alt_n[id] = n + 1
			id = alt[n % alt.size()]
	id = String(S.get("hourDeath", {}).get(shf.id, {}).get(id, id))   # в серверной и под стойкой — та же смерть в другой постановке
	_close()
	died.emit(id)


func end() -> void:
	if shf.ended:
		return
	shf.ended = true
	ui.pop_close()
	var n := String(shf.seg.next)
	_close()
	ended.emit(n)


func _close() -> void:
	tut = null
	spd = 1.0
	ui.visible = false
	for s in ["cam_pressed", "log_pressed", "panel_pressed", "book_pressed", "help_pressed", "tut_skip"]:
		for c in ui.get_signal_connection_list(s):
			ui.disconnect(s, c.callable)


# ---------- игрок ----------
func cam(c: String) -> void:
	if c == "13":
		if not shf.warn13:
			shf.warn13 = true
			game._play("static", {"gain": 0.5, "cut": 0.4})
			ui.toast("«НЕ СМОТРИ В НЕЁ» — журнал, 18.10")
			return
		die("over_k1")
		return
	if c == shf.cam:
		return
	shf.cam = c
	shf.camT = T
	game._play("click", {"gain": 0.45})
	game._play("static", {"gain": 0.25, "cut": 0.25})
	ui.set_cam(c)


func _task() -> void:
	var tk = shf.task
	if tk == null:
		ui.task("")
		return
	var all_ok := true
	for n in tk.get("need", []):
		if not shk.get(n, 0):
			all_ok = false
	if all_ok:
		if not tk.get("ok", false):
			tk.ok = true
			if tk.has("say"):
				tk.sayAt = maxf(shf.M, _to_min(tk.by) - 3)
		ui.task("")
		return
	ui.task(String(tk.text))


func log_open() -> void:
	var c := String(shf.cam)
	shf.pop = true
	ui.pop_open("Записать в журнал", ShiftUI.cam_label(c) + " · " + min_str(shf.M))
	var items := []
	for k in KINDS + ["Всё спокойно"]:
		items.append([StoryDB.t(k), _log_pick.bind(c, k)])
	ui.pop_grid(items, 4)


func _log_pick(c: String, k: String) -> void:
	var hit = null
	for a in shf.act:
		if String(a.def.cam) == c and String(a.def.get("fix", "log")) == "log" and (k in a.def.get("kinds", [a.def.get("kind", "")])):
			hit = a
			break
	shk.log.append({"m": min_str(shf.M), "cam": c, "k": k, "ok": hit != null})
	game._play("pen", {"gain": 0.5, "cut": 1.3})
	ui.pop_open("Записываю…", ShiftUI.cam_label(c) + " · " + min_str(shf.M) + " — " + StoryDB.t(k), true)
	var my: Dictionary = shf
	await get_tree().create_timer(1.4).timeout
	if shf != my or shf.ended:
		return
	ui.pop_close()
	shf.pop = false
	if hit != null and shf.act.has(hit):
		resolve(hit, "Записано.")
	else:
		ui.toast("Записано: всё спокойно." if k == "Всё спокойно" else "Записано. Хотя, может, показалось.")
	check()


func panel_open() -> void:
	if tut != null:
		tut.seen.panel = true
	shf.pop = true
	ui.pop_open("Пульт")
	var row := HBoxContainer.new()
	row.add_theme_constant_override("separation", int(2 * ui.u))
	for b in [["Вход", "door", "ЗАПЕРТ", "ОТКРЫТ"], ["Лифт", "lift", "СТОП", "РАБОТАЕТ"]]:
		var on: bool = shk[b[1]] != 0
		var bt := ui.btn("%s: %s" % [StoryDB.t(b[0]), StoryDB.t(b[2] if on else b[3])], 3.2, ShiftUI.GOLD if on else Color(0.92, 0.94, 0.92, 0.28))
		bt.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		bt.custom_minimum_size = Vector2(0, 10 * ui.u)
		bt.pressed.connect(_panel_toggle.bind(b[1]))
		row.add_child(bt)
	ui.pop_box.add_child(row)
	var l := Label.new()
	l.text = StoryDB.t("Свет на этажах")
	l.add_theme_font_size_override("font_size", int(3.2 * ui.u))
	ui.pop_box.add_child(l)
	var items := []
	for f in [12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1, -2]:
		items.append(["−2" if f == -2 else str(f), _light_toggle.bind(f), shk.lights.get(f, 0) != 0])
	ui.pop_grid(items, 7, 3.6)


func _panel_toggle(k: String) -> void:
	shk[k] = 1 - int(shk[k])
	game._play("click")
	if k == "door":
		if shk.door:
			game._play("bolt", {"gain": 0.4, "cut": 0.8})
		else:
			for a in shf.act:   # открыл ей
				if a.def.get("trap") == "door":
					die(String(a.def.get("die", "over_door")))
					return
	panel_open()
	check()


func _light_toggle(f: int) -> void:
	shk.lights[f] = 0 if shk.lights.get(f, 0) else 1
	game._play("click", {"gain": 0.6})
	panel_open()
	check()


func book_open(tab := 0) -> void:
	if tut != null:
		tut.seen.book = true
	shf.pop = true
	var fr: Array = S.get("frank", []) if game.flags.has("jread") else S.get("frankPre", S.get("frank", []))   # личные записи Фрэнка — до 02:50 не прочитаны
	var mine := []
	for r in shk.log:
		mine.append("%s · %s — %s" % [r.m, ShiftUI.cam_label(r.cam), StoryDB.t(r.k)])
	if mine.is_empty():
		mine = [StoryDB.t("Пока ни одной записи.")]
	var tabs := [["Памятка", S.get("memo", []).map(func(x): return StoryDB.t(x))], ["Фрэнк", fr.map(func(x): return StoryDB.t(x))], ["Моя смена", mine]]
	ui.pop_open("Журнал")
	var row := HBoxContainer.new()
	for j in tabs.size():
		var b := ui.btn(StoryDB.t(tabs[j][0]).to_upper(), 3.0, ShiftUI.GOLD if j == tab else Color(0.92, 0.94, 0.92, 0.28))
		b.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		b.pressed.connect(book_open.bind(j))
		row.add_child(b)
	ui.pop_box.add_child(row)
	ui.pop_list(tabs[tab][1])
	if tab == 1:
		shk.read = true


func help_open() -> void:
	shf.pop = true
	ui.pop_open("Как играть")
	ui.pop_list(["Камеры — кнопки внизу. Переключай и запоминай, как там обычно.", "Заметил, что не так, — «Записать» и выбери, что это: свет, дверь, лифт, помехи, кто-то, звук, другое.",
		"MIC слева вверху: прыгают столбики — на этой камере звук.", "«Пульт»: свет на этажах, вход, лифт. Лишний свет — выключи. К 22:00 вход запри, лифт останови.",
		"Три пропущенные странности за час — оно придёт к будке.", "Помехи на камере — не засматривайся: записал и переключил.", "Не отзывайся, если зовут по имени. Не открывай дверь."].map(func(x): return StoryDB.t(x)))
	ui.pop_grid([[StoryDB.t("Пройти обучение снова"), func():
		ui.pop_close()
		shf.pop = false
		if tut == null:
			tutorial()]], 1, 3.0)


func _sms(m: Dictionary) -> void:
	game._play("buzz", {"gain": 0.7})
	ui.toast(StoryDB.t(String(m.get("from", "Лили"))) + ": " + StoryDB.t(String(m.text)))
	if m.has("reply"):
		await get_tree().create_timer(2.6).timeout
		if shf.ended:
			return
		shf.pop = true
		ui.pop_open(String(m.get("from", "Лили")), StoryDB.t(String(m.text)))
		var items := []
		for r in m.reply:
			items.append([StoryDB.t(r), func():
				ui.pop_close()
				shf.pop = false
				game._play("click")
				if m.has("back"):
					await get_tree().create_timer(4.0).timeout
					if not shf.ended:
						ui.toast(StoryDB.t(String(m.get("from", "Лили"))) + ": " + StoryDB.t(String(m.back)))
						game._play("buzz", {"gain": 0.6})])
		ui.pop_grid(items, 2)


## Внутренний телефон будки: снять или нет; сняли — голос по имени; ответить — смерть.
func call_open(c: Dictionary) -> void:
	var my: Dictionary = shf
	shf.pop = true
	ui.pop_open(String(c.get("title", "Звонит внутренний телефон")), StoryDB.t(String(c.get("sub", "В будке. Номер не определён."))))
	ui.pop_grid([[StoryDB.t(String(c.get("take", "Снять трубку"))), func(): _call_take(c, my)], [StoryDB.t(String(c.get("no", "Не брать"))), func():
		c.taken = "no"
		ui.pop_close()
		shf.pop = false
		game._play("click")
		if c.has("noSay"):
			say_v(String(c.noSay))]], 2)
	var rings := 0
	while shf == my and not shf.ended and not c.has("taken") and rings < int(c.get("rings", 6)):
		rings += 1
		game._play("static" if c.get("radio") else "ring", {"gain": 0.5, "cut": 0.9} if c.get("radio") else {"gain": 0.8})
		await get_tree().create_timer(2.2).timeout
	if shf == my and not shf.ended and not c.has("taken"):
		ui.pop_close()
		shf.pop = false


func _call_take(c: Dictionary, my: Dictionary) -> void:
	c.taken = "yes"
	ui.pop_close()
	game._play("click")
	if c.get("takeDie"):
		die(String(c.die))
		return
	var it := say_v(String(c.say))
	await get_tree().create_timer(maxf(0.6, it.t0 + it.d - T)).timeout
	if shf != my or shf.ended:
		return
	ui.pop_open("В эфире" if c.get("radio") else "В трубке", StoryDB.t(String(c.ask)), true)
	ui.pop_grid([[StoryDB.t(String(c.yes)), func(): die(String(c.die))], [StoryDB.t(String(c.hang)), func():
		ui.pop_close()
		shf.pop = false
		game._play("click")
		if c.has("after"):
			say_v(String(c.after))]], 2)


## Держать кнопку, пока голос не стихнет; отпустил больше чем на 0,9 с — смерть h.die.
func hold_open(h: Dictionary) -> void:
	var my: Dictionary = shf
	var it := say_v(String(h.say), 0.4)
	var need := float(h.get("dur", 7))
	shf.pop = true
	ui.pop_open(String(h.get("title", "ДЕРЖИ ФРЭНКА")), StoryDB.t(String(h.get("text", "Фрэнк поднимается на голос. Не отпускай, пока голос не стихнет."))), true)
	var bar := ProgressBar.new()
	bar.show_percentage = false
	bar.max_value = need
	bar.custom_minimum_size = Vector2(0, 2.4 * ui.u)
	ui.pop_box.add_child(bar)
	var b := ui.btn(StoryDB.t(String(h.get("btn", "ДЕРЖАТЬ"))), 5.0, ShiftUI.GOLD)
	b.custom_minimum_size = Vector2(0, 14 * ui.u)
	b.keep_pressed_outside = false
	ui.pop_box.add_child(b)
	game._play("heart", {"gain": 0.9})
	if h.has("sfx"):
		game._play(String(h.sfx[0]), h.sfx[1] if h.sfx.size() > 1 else {})
	var held := 0.0
	var lost := 0.0
	var st := {"down": false}
	b.button_down.connect(func(): st.down = true)
	b.button_up.connect(func(): st.down = false)
	while shf == my and not shf.ended:
		await get_tree().process_frame
		var dt := minf(0.1, get_process_delta_time())
		var down: bool = st.down or Input.is_action_pressed("ui_accept") or game.autoplay
		b.text = StoryDB.t(String(h.get("btnOn", "ДЕРЖУ") if down else h.get("btn", "ДЕРЖАТЬ")))
		if T > it.t0:
			if down:
				held += dt
			elif T > it.t0 + 1.2:
				lost += dt
		bar.value = held
		if lost > 0.9:
			ui.pop_close()
			die(String(h.get("die", "over_k8")))
			return
		if held >= need:
			ui.pop_close()
			shf.pop = false
			game._play("click")
			ui.toast(StoryDB.t(String(h.get("done", "Голос стих. Фрэнк сел на пол и закрыл лицо руками."))))
			return


# ---------- обучение: диспетчер охранного пульта «Щит» Вера по рации показывает, что где (время смены стоит) ----------
func tutorial() -> void:
	if shf.is_empty() or shf.ended or tut != null:
		return
	var me := {"seen": {}}
	tut = me
	spd = 0.0
	ui.pop_close()
	shf.pop = false
	var steps := [["t01", null, null], ["t02", "cam:03", func(): return shf.cam == "03"], ["t03", null, null],
		["t04", "shLog", func(): return not shf.act.any(func(a): return a.id == "tut13")], ["t05", null, null],
		["t06", [76, 340, 290, 44], null], ["t07", "shPanel", func(): return me.seen.has("panel")], ["t08", "shBook", func(): return me.seen.has("book")],
		["t09", null, null]]
	var ok := true
	for s in steps:
		if tut != me:
			return
		if s[0] == "t03":   # учебная странность: табло лифта показывает 13-й этаж
			shf.act.append({"id": "tut13", "def": {"cam": "03", "kind": "Лифт", "props": {"lift13": 1}, "win": 9999}, "m": shf.M})
		ui.ring_at(s[1])
		ui.tut_show(true, TUT_DO.get(s[0], ""), false)
		var it := say_v(String(s[0]))
		it.hold = true
		while tut == me and T < it.t0 + it.d + 0.3:   # голос договорит
			await get_tree().process_frame
		if tut != me:
			return
		if s[2] != null:
			var cond: Callable = s[2]
			while tut == me and not cond.call():
				await get_tree().process_frame
		else:
			ui.tut_show(true, TUT_DO.get(s[0], ""), true)
			if s[1] == null:
				ui.ring_at("go")
			var go := [false]
			var f := func(): go[0] = true
			ui.tut_go.connect(f)
			while tut == me and not go[0] and not game.autoplay:
				await get_tree().process_frame
			if ui.tut_go.is_connected(f):
				ui.tut_go.disconnect(f)
		it.hold = false
		if tut != me:
			return
		if s[0] == "t07" or s[0] == "t08":   # посмотрел и закрыл
			ui.ring_at(null)
			while tut == me and ui.pop.visible:
				await get_tree().process_frame
	tut_end(ok)


func tut_end(done: bool) -> void:
	if tut == null:
		return
	tut = null
	spd = 1.0
	if done:
		tut_done = true
	ui.ring_at(null)
	ui.tut_show(false)
	shf.act = shf.act.filter(func(a): return a.id != "tut13")
	shf.vq = shf.vq.filter(func(v): return not String(v.k).begins_with("t0"))
	if done and not shf.ended:
		ui.toast("Смена началась. Удачи.")
