extends Node
## Проверка управления и экранов без человека: клавиши и кнопки геймпада подаются в игру как настоящие события
## (Input.parse_input_event), результат — по журналу игры и её состоянию. В сборку не входит (scripts/dev/*).
##   управление: godot --path godot/lake -- --inputtest --scene c5_s1_hub --flags danny=1,zoe=1,eva=1,eva_alive=1
##   экраны:     godot --path godot/lake -- --uitest --shotlog папка        (меню → настройки → дерево выборов)
var g
var step := 0
var t := 0.0
var mark := 0
var res: Array[String] = []
var ui := false
var qte := false
var presses := 0
var txt0 := ""
var shots := ""
var want := -1
var save_mode := ""   # --savetest A|B|C: сохранение и загрузка без двойного начисления флагов (три запуска подряд)
var s0 := {}


func _ready() -> void:
	g = get_parent()
	process_mode = Node.PROCESS_MODE_ALWAYS
	var args := OS.get_cmdline_user_args()
	ui = "--uitest" in args
	qte = "--qtetest" in args   # godot --path godot/lake -- --inputtest --qtetest --scene c4_s3: A листает и жмёт в действии
	if "--shotlog" in args:
		shots = args[args.find("--shotlog") + 1]
	if "--savetest" in args:
		save_mode = args[args.find("--savetest") + 1]
	if "--inputlog" in args:   # отладка: какие события пришли и у кого фокус
		get_viewport().gui_focus_changed.connect(func(c: Control): print("  фокус → ", c.get_class(), " ", c.get("text")))


func _input(e: InputEvent) -> void:
	if "--inputlog" in OS.get_cmdline_user_args() and not (e is InputEventMouseMotion):
		print("  событие: ", e.as_text(), " pressed=", e.is_pressed(), " paused=", get_tree().paused, " фокус=", get_viewport().gui_get_focus_owner())


func key(k: Key) -> void:
	for pressed in [true, false]:
		var e := InputEventKey.new()
		e.keycode = k
		e.physical_keycode = k
		e.pressed = pressed
		Input.parse_input_event(e)


func pad(b: JoyButton) -> void:
	for pressed in [true, false]:
		var e := InputEventJoypadButton.new()
		e.button_index = b
		e.pressed = pressed
		e.device = 0
		Input.parse_input_event(e)


func nth_visible(n: int) -> int:   # номер варианта в сценарии для n-й кнопки (без «молчания»)
	var k := 0
	for i in g.hud._opts.size():
		if not g.hud._opts[i].get("silent", false):
			k += 1
			if k == n:
				return i
	return -1


func logged(s: String) -> bool:
	for i in range(mark, g.log_lines.size()):
		if String(g.log_lines[i]).contains(s):
			return true
	return false


func check(name: String, ok: bool) -> void:
	res.append(("ОК   " if ok else "НЕТ  ") + name)
	print(("ОК   " if ok else "НЕТ  ") + name)


func snap(name: String) -> void:
	if shots != "":
		get_viewport().get_texture().get_image().save_png("%s/%s.png" % [shots, name])


func next(s: int) -> void:
	step = s
	t = 0.0


func _process(d: float) -> void:
	t += d
	if save_mode != "":
		_save_test()
	elif ui:
		_ui()
	elif qte:
		_qte()
	else:
		_controls()


func _controls() -> void:
	match step:
		0:   # ждём первый выбор (комната на даче: до шести вариантов) — клавиша 5
			if g.hud.choosing() and t > 1.0:
				mark = g.log_lines.size()
				want = nth_visible(5)
				key(KEY_5)
				next(1)
			elif t > 60.0:
				check("выбор не появился", false)
				next(99)
		1:
			if t > 0.5:
				check("клавиша 5 выбирает пятый вариант (раньше работали только 1–3)", want >= 0 and logged("выбор %d:" % want))
				next(2)
		2:   # следующий выбор — геймпадом: крестовина вниз (выделить первый), ещё вниз (второй), A — выбрать
			if g.hud.choosing() and t > 1.0:
				mark = g.log_lines.size()
				want = nth_visible(2)
				pad(JOY_BUTTON_DPAD_DOWN)
				next(3)
			elif t > 90.0:
				check("второй выбор не появился", false)
				next(99)
		3:
			if t > 0.4:
				pad(JOY_BUTTON_DPAD_DOWN)
				next(4)
		4:
			if t > 0.4:
				pad(JOY_BUTTON_A)
				next(5)
		5:
			if t > 0.6:
				check("геймпад: крестовина выделяет, A выбирает (второй вариант)", want >= 0 and logged("выбор %d:" % want))
				next(6)
		6:   # пауза с геймпада (Start) и выход из неё (B)
			if not g.hud.choosing() and t > 1.5:
				pad(JOY_BUTTON_START)
				next(7)
		7:
			if t > 0.4:
				check("Start на геймпаде ставит паузу", get_tree().paused and g.hud.menu_open())
				pad(JOY_BUTTON_B)
				next(8)
		8:
			if t > 0.4:
				check("B на геймпаде продолжает игру", not get_tree().paused and not g.hud.menu_open())
				key(KEY_ESCAPE)
				next(9)
		9:
			if t > 0.4:
				check("Esc ставит паузу", get_tree().paused)
				key(KEY_ESCAPE)
				next(10)
		10:
			if t > 0.4:
				check("Esc в паузе — продолжить", not get_tree().paused)
				next(11)
		11:   # пауза → Настройки → назад: игра всё ещё стоит, меню паузы на месте
			if not g.hud.choosing() and t > 0.5:
				key(KEY_P)
				next(12)
		12:
			if t > 0.4:
				check("P ставит паузу", get_tree().paused)
				g.hud.menu_pick.emit("settings")
				next(13)
		13:
			if t > 0.5:
				check("Настройки открываются из паузы", g.hud.settings_open() and get_tree().paused)
				pad(JOY_BUTTON_B)
				next(14)
		14:
			if t > 0.5:
				check("B закрывает настройки, снова меню паузы", not g.hud.settings_open() and g.hud.menu_open() and get_tree().paused)
				pad(JOY_BUTTON_B)
				next(15)
		15:
			if t > 0.5:
				check("после настроек игра продолжается", not get_tree().paused)
				next(99)
		99:
			print("ИТОГ ПРОВЕРКИ УПРАВЛЕНИЯ: %d из %d" % [res.filter(func(r): return r.begins_with("ОК")).size(), res.size()])
			get_tree().quit()


func pad_hold(b: JoyButton, on: bool) -> void:
	var e := InputEventJoypadButton.new()
	e.button_index = b
	e.pressed = on
	e.device = 0
	Input.parse_input_event(e)


func _qte() -> void:
	match step:
		0:   # первая надпись рассказчика — A пропускает её
			if g.hud.note.text != "" and t > 0.8:
				txt0 = g.hud.note.text
				pad(JOY_BUTTON_A)
				next(1)
		1:
			if t > 0.3:
				check("A на геймпаде листает (надпись ушла сразу)", g.hud.note.text != txt0)
				next(2)
		2:   # «мотай изоленту»: жать в ритм — A держим 0,06 с каждые 0,25 с
			if String(g._qte) == "press":
				check("подсказка под геймпад: «%s»" % g.hud.hint.text.get_slice("   ", 0), g.hud.hint.text.contains("A") and not g.hud.hint.text.contains("E /"))
				mark = g.log_lines.size()
				presses = 0
				next(3)
			elif t > 30.0:
				check("действие не началось", false)
				next(99)
		3:
			if t > 0.25:
				pad_hold(JOY_BUTTON_A, true)
				presses += 1
				next(4)
		4:
			if t > 0.06:
				pad_hold(JOY_BUTTON_A, false)
				next(3 if presses < 14 and String(g._qte) == "press" else 5)
		5:
			if t > 1.0:
				check("A на геймпаде жмёт в действии (%d нажатий)" % presses, logged("действие press: удалось"))
				next(99)
		99:
			print("ИТОГ ПРОВЕРКИ ДЕЙСТВИЙ: %d из %d" % [res.filter(func(r): return r.begins_with("ОК")).size(), res.size()])
			get_tree().quit()


func _ui() -> void:
	match step:
		0:
			if t > 3.0:   # главное меню
				snap("01_menu")
				check("в главном меню есть «Дерево выборов» и «Настройки»", g.hud.menu_open())
				g.hud.menu_pick.emit("settings")
				next(1)
		1:
			if t > 1.0:
				step = 50   # дальше — с ожиданиями: пока идут, _process сюда не заходит
				snap("02_settings")
				var q0: int = g.settings.quality
				pad(JOY_BUTTON_A)   # фокус — на «Качество графики»: A листает значение
				await get_tree().create_timer(0.3).timeout
				check("A на «Качество графики» меняет качество (%d → %d)" % [q0, g.settings.quality], g.settings.quality != q0)
				pad(JOY_BUTTON_DPAD_DOWN)
				pad(JOY_BUTTON_DPAD_DOWN)
				pad(JOY_BUTTON_DPAD_DOWN)
				pad(JOY_BUTTON_DPAD_DOWN)   # «Громкость: общая»
				var v0: float = float(g.settings.vol.master)
				pad(JOY_BUTTON_DPAD_LEFT)
				await get_tree().create_timer(0.3).timeout
				check("крестовина влево на ползунке убавляет громкость (%.2f → %.2f)" % [v0, float(g.settings.vol.master)], float(g.settings.vol.master) < v0)
				snap("03_settings_changed")
				pad(JOY_BUTTON_B)
				next(2)
		2:
			if t > 0.8:
				check("B закрывает настройки, файл сохранён", not g.hud.settings_open() and FileAccess.file_exists(Settings.PATH))
				var cps := {}   # для снимка: будто пройдены главы 1–3 и начало четвёртой
				for sid in g.data.scenes:
					if g.data.scenes[sid].get("save", false) and (not String(sid).begins_with("c") or String(sid).begins_with("c2") \
							or String(sid).begins_with("c3") or sid in ["c4_recap", "c4_s1", "c4_s3"]):
						cps[sid] = {"flags": g.flags.duplicate(), "lang": g.lang}
				var f := FileAccess.open("user://lake_checkpoints.json", FileAccess.WRITE)
				f.store_string(JSON.stringify(cps))
				f = null
				g.hud.menu_pick.emit("tree")
				next(3)
		3:
			if t > 1.2:
				snap("04_tree")
				check("дерево выборов открывается", g.hud.tree_open())
				pad(JOY_BUTTON_DPAD_DOWN)   # с вкладки главы — на первую пройденную контрольную точку
				next(4)
		4:
			if t > 0.8:
				snap("05_tree_focus")
				pad(JOY_BUTTON_DPAD_LEFT)   # на вкладку соседней главы
				next(5)
		5:
			if t > 0.8:
				var tabs_focus: Control = get_viewport().gui_get_focus_owner()
				snap("06_tree_ch")
				check("фокус в дереве ходит крестовиной (%s)" % (tabs_focus.get_class() if tabs_focus else "нет"), tabs_focus != null)
				g.hud._tree_chapter(4, false)
				next(6)
		6:
			if t > 0.8:
				snap("07_tree_ch5")
				pad(JOY_BUTTON_B)
				next(7)
		7:
			if t > 0.8:
				check("B закрывает дерево, снова главное меню", not g.hud.tree_open() and g.hud.menu_open())
				next(99)
		99:
			print("ИТОГ ПРОВЕРКИ ЭКРАНОВ: %d из %d" % [res.filter(func(r): return r.begins_with("ОК")).size(), res.size()])
			get_tree().quit()


## Сохранения (три запуска, один за другим; общий user://):
##  A: godot … -- --inputtest --savetest A --scene c2_s3 --flags zoe=1   сохранение на входе в пост → «Дать говорить Зое» → сохранение c2_s4
##  B: godot … -- --inputtest --savetest B                              «Продолжить»: флаги c2_s4 те же, ничего не начислено второй раз
##  C: godot … -- --inputtest --savetest C                              раннее сохранение (пост) → другой выбор: следов первого нет
const SAVE_RES := "/tmp/lake_savetest.json"


func _save_flags() -> Dictionary:
	var j = JSON.parse_string(FileAccess.get_file_as_string("user://lake_save.json")) if FileAccess.file_exists("user://lake_save.json") else null
	return j if j is Dictionary else {}


func _same(a: Dictionary, b: Dictionary) -> bool:
	for k in a.keys() + b.keys():
		if float(a.get(k, 0)) != float(b.get(k, 0)):
			return false
	return true


var sres := {}   # итоги трёх запусков (файл SAVE_RES): S0 — флаги на входе в пост, F1 — после выбора


func _save_test() -> void:
	if sres.is_empty() and FileAccess.file_exists(SAVE_RES):
		var j = JSON.parse_string(FileAccess.get_file_as_string(SAVE_RES))
		sres = j if j is Dictionary else {"_": 1}
	if sres.is_empty():
		sres = {"_": 1}
	var res: Dictionary = sres
	match step:
		0:
			if save_mode == "D":   # испорченный файл + запасная копия у озера (после приезда седана)
				var fb := FileAccess.open("user://lake_save.json.bak", FileAccess.WRITE)
				fb.store_string(JSON.stringify({"v": 2, "scene": "c5_s4", "lang": "ru", "clock": "05:40",
					"flags": {"danny": 1, "zoe": 1, "eva": 1, "eva_alive": 1, "baby": 1, "trust": 2}}))
				fb = null
				var fm := FileAccess.open("user://lake_save.json", FileAccess.WRITE)
				fm.store_string("{\"scene\": \"c5_s4\", \"flags\": {")   # оборван посреди записи
				fm = null
				g.hud.menu_pick.emit("cont")
				next(30)
			elif save_mode == "E":   # дом Агнес утром, Дэнни потерялся в буран
				var fe := FileAccess.open("user://lake_save.json", FileAccess.WRITE)
				fe.store_string(JSON.stringify({"v": 2, "scene": "c3_s6", "lang": "ru",
					"flags": {"danny": 1, "danny_lost": 1, "zoe": 1, "eva": 1, "eva_alive": 1, "baby": 1}}))
				fe = null
				g.hud.menu_pick.emit("cont")
				next(40)
			elif save_mode == "B":
				g.hud.menu_pick.emit("cont")
				next(10)
			elif save_mode == "C":
				var f := FileAccess.open("user://lake_save.json", FileAccess.WRITE)
				f.store_string(JSON.stringify({"scene": "c2_s3", "flags": res.get("S0", {}), "lang": "ru"}))
				f = null
				g.hud.menu_pick.emit("cont")
				next(20)
			elif g.hud.choosing() and t > 1.0:   # A: на посту, выбор на экране
				s0 = (g.flags as Dictionary).duplicate()
				var sv := _save_flags()
				check("A: сохранение на входе в c2_s3 = флаги на входе", String(sv.get("scene", "")) == "c2_s3" and _same(sv.get("flags", {}), s0))
				res["S0"] = s0
				want = nth_visible(2)
				key(KEY_2)   # «Дать говорить Зое»
				next(1)
		1:
			if String(g._scene_id) == "c2_s4" and t > 0.5:
				var sv := _save_flags()
				res["F1"] = (g.flags as Dictionary).duplicate()
				check("A: после выбора сохранение c2_s4, флаги с выбором (zoe_talked=%s)" % sv.get("flags", {}).get("zoe_talked", 0),
					String(sv.get("scene", "")) == "c2_s4" and float(sv.get("flags", {}).get("zoe_talked", 0)) == 1.0)
				next(99)
		10:
			if String(g._scene_id) == "c2_s4" and t > 1.0:
				var dif := []
				var f1: Dictionary = res.get("F1", {})
				for k in g.flags.keys() + f1.keys():
					if float(g.flags.get(k, 0)) != float(f1.get(k, 0)) and not (k in dif):
						dif.append("%s %s→%s" % [k, f1.get(k, 0), g.flags.get(k, 0)])
				check("B: «Продолжить» — флаги как при сохранении, без повторного начисления %s" % [dif], dif.is_empty())
				next(99)
			elif t > 30.0:
				check("B: игра не продолжилась с сохранения", false)
				next(99)
		20:
			if g.hud.choosing() and String(g._scene_id) == "c2_s3" and t > 1.0:
				check("C: загружено раннее сохранение — флаги как до выбора", _same(g.flags, res.get("S0", {})))
				key(KEY_1)   # «Говорить буду я»
				next(21)
			elif t > 40.0:
				check("C: выбор на посту не появился", false)
				next(99)
		21:
			if String(g._scene_id) == "c2_s4" and t > 0.5:
				var a: Dictionary = res.get("S0", {})
				check("C: другой выбор — следов первого нет (zoe_talked %s, jealous %s → %s)" % [g.flags.get("zoe_talked", 0), a.get("jealous", 0), g.flags.get("jealous", 0)],
					float(g.flags.get("zoe_talked", 0)) == 0.0 and float(g.flags.get("jealous", 0)) == float(a.get("jealous", 0)) \
					and float(g.flags.get("cop_saw", 0)) == float(a.get("cop_saw", 0)) + 1.0)
				next(99)
		30:
			if String(g._scene_id) == "c5_s4" and t > 2.0:
				var hv: bool = g.actors.has("hale") and is_instance_valid(g.actors.hale)
				var ov: bool = g.actors.has("orderly") and is_instance_valid(g.actors.orderly)
				var bc: bool = g.blackcar != null and is_instance_valid(g.blackcar) and (g.blackcar as Node3D).visible
				check("D: испорченный файл — загружена запасная копия (c5_s4)", true)
				check("D: Хейл, санитар и седан у берега после загрузки (Хейл %s, санитар %s, седан %s)" % [hv, ov, bc], hv and ov and bc)
				check("D: часы в машине из сохранения (%s)" % g.scene.car.clock.text, String(g.scene.car.clock.text) == "05:40")
				check("D: лёд у мостков построен (тёмное пятно)", Ch4.lake(g).has_node("dark_patch"))
				next(99)
			elif t > 40.0:
				check("D: запасная копия не загрузилась (сцена %s)" % g._scene_id, false)
				next(99)
		40:
			if String(g._scene_id) == "c3_s6" and t > 2.0:
				var dv: bool = g.actors.has("danny") and is_instance_valid(g.actors.danny) and (g.actors.danny as Node3D).is_visible_in_tree()
				check("E: потерявшийся Дэнни не появляется в доме после загрузки (виден: %s)" % dv, not dv)
				var hasbaby: bool = g.c3.get("baby") != null and is_instance_valid(g.c3.get("baby"))
				check("E: младенец на руках после загрузки (%s)" % hasbaby, hasbaby)
				next(99)
			elif t > 40.0:
				check("E: сохранение c3_s6 не загрузилось (сцена %s)" % g._scene_id, false)
				next(99)
		99:
			res["checks_" + save_mode] = self.res
			var fo := FileAccess.open(SAVE_RES, FileAccess.WRITE)
			fo.store_string(JSON.stringify(res))
			fo = null
			print("ИТОГ ПРОВЕРКИ СОХРАНЕНИЙ %s: %d из %d" % [save_mode, self.res.filter(func(r): return r.begins_with("ОК")).size(), self.res.size()])
			get_tree().quit()
