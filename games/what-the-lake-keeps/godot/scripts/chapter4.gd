class_name Ch4
## Глава 4 «Чёрная машина» (data/ch4.json ← tools/story/ch4.py): двор Агнес солнечным утром — записка Хейла и новый
## пузырёк на сиденье, дедова буханка «с толкача»; лесная дорога (рассказ Зои или голос Хейла по рации); заброшенная остановка
## (рисунки Айрис, Дэнни про заправку); поваленная сосна — Хейл и санитар; погоня в лесу, охотничья избушка, младенец;
## на рассвете — озеро, дача, сарай со щелью. Режиссёр (game.gd) зовёт fx / cam / process / restore.

const WOODS := Vector3(160.0, 0.0, -60.0)      # лес и избушка — в стороне от дороги (её ельник кончается у x 65)
const LAKE := Vector3(-420.0, 0.0, -60.0)      # озеро (и вся глава 5)
const SANA := Vector3(480.0, 0.0, -60.0)       # санаторий «Тихие сосны» (необязательный заезд): далеко от леса и озера
const CAR_YARD := Vector3(2.2, 0.0, -37.0)     # их машина у забора (как в конце главы 3)
const VAN_YARD := Vector3(-10.5, 0.0, -55.0)   # буханка у дома Агнес, носом к улице
const GAP := Vector3(-9.555, 1.075, 10.88)     # щель в стене сарая (местные координаты озера): середина просвета между досками
const NOTE := {"ru": "Ной, тебе станет легче.\nТы же знаешь, что бывает\nбез них.", "en": "Noah, you’ll feel better.\nYou know what happens\nwithout them."}
const NOTE_SIGN := {"ru": "— С. Х.", "en": "— S. H."}


static func on(g, f: String) -> bool:
	return float(g.flags.get(f, 0)) > 0.0


static func has(g, who: String) -> bool:
	return g.actors.has(who) and is_instance_valid(g.actors[who])


static func actor(g, who: String, model := "") -> Actor:
	return Ch2.actor(g, who, model)


static func put(g, who: String, p: Vector3, face: float, clip := "Idle") -> void:
	Ch3.put(g, who, p, face, clip)


## Кто едет в главе 4: Ной, Айрис, бабушка; Дэнни, Зоя, Ева — по флагам (младенец — у Евы, без неё — у Айрис).
static func cast(g) -> Array:
	var who := ["noah", "iris", "agnes"]
	if on(g, "danny") and not on(g, "danny_lost") and not on(g, "danny_hale"):
		who.append("danny")
	if on(g, "zoe") and not on(g, "zoe_gone"):
		who.append("zoe")
	if (on(g, "eva") or on(g, "eva_found")) and not on(g, "eva_dead"):
		who.append("eva")
	for k in who:
		if not has(g, k):
			if k == "danny":
				g._attach_walkie(actor(g, "danny"))
			elif k == "agnes":
				Ch3.agnes(g)
			else:
				actor(g, k)
		(g.actors[k] as Actor).visible = true
	for k in ["danny", "zoe", "eva"]:
		if not k in who and has(g, k):
			(g.actors[k] as Actor).visible = false
	return who


static func baby_holder(g) -> String:
	return "eva" if has(g, "eva") and (g.actors.eva as Actor).visible and not on(g, "eva_dead") else "iris"


static func baby(g) -> void:
	if on(g, "baby"):
		Ch3.baby(g, baby_holder(g))


static func van(g) -> Van:
	var v = g.c4.get("van")
	if v and is_instance_valid(v):
		return v
	var vn := Van.new()
	g.scene.add_child(vn)
	g.c4["van"] = vn
	return vn


static func lake(g) -> Lake:
	var l = g.c4.get("lake")
	if l and is_instance_valid(l):
		return l
	var lk := Lake.new()
	g.scene.add_child(lk)
	lk.position = LAKE
	lk.build(g.scene.road, g.scene.pc)
	g.c4["lake"] = lk
	return lk


static func woods(g) -> Woods:
	var w = g.c4.get("woods")
	if w and is_instance_valid(w):
		return w
	var wd := Woods.new()
	g.scene.add_child(wd)
	wd.position = WOODS
	wd.build(g.scene.road, g.scene.pc)
	g.c4["woods"] = wd
	return wd


static func sana(g) -> Sana:
	var v = g.c4.get("sana")
	if v and is_instance_valid(v):
		return v
	var sn := Sana.new()
	g.scene.add_child(sn)
	sn.position = SANA
	sn.build(g.scene.road, g.scene.pc, g.lang)
	g.c4["sana"] = sn
	return sn


static func spos(x: float, y: float, z: float) -> Vector3:
	return SANA + Vector3(x, y, z)


static func wpos(x: float, y: float, z: float) -> Vector3:
	return WOODS + Vector3(x, y, z)


static func lpos(x: float, y: float, z: float) -> Vector3:
	return LAKE + Vector3(x, y, z)


static func day(g, mode: String) -> void:
	g.c4["day"] = mode
	g.scene.set_daylight(mode)


## Все в буханке по местам: Ной за рулём, бабушка рядом, сзади на лавках — Айрис, Ева с младенцем, Зоя, Дэнни.
static func seat_van(g) -> void:
	var vn := van(g)
	var who := cast(g)
	var slots := {"noah": "driver", "agnes": "front", "iris": "l1", "eva": "l2", "zoe": "r1", "danny": "r2"}
	for k in who:
		var a: Actor = g.actors[k]
		a.reach = {}
		a.look_target = null
		var st: Array = vn.seat(String(slots[k]))
		Ch2.sit(g, k, st[0], float(st[1]), "Driving" if k == "noah" else "Rk_SitIdle")
	baby(g)


## Встать через d метров (дорога и буханка вместе).
static func stop_in(g, d: float, tmax := 3.0) -> float:
	var s: DriveScene = g.scene
	var vn := van(g)
	var v0: float = s.road.speed
	var t := clampf(2.0 * d / maxf(v0, 0.1), 0.6, tmax)
	var old = g.c4.get("speed_tw")
	if old is Tween and (old as Tween).is_valid():
		(old as Tween).kill()
	var tw: Tween = g.create_tween().set_parallel()
	tw.tween_property(s.road, "speed", 0.0, t)
	tw.tween_property(vn, "speed", 0.0, t)
	g.c4["speed_tw"] = tw
	return t


static func drive(g, v: float, t: float) -> void:
	var tw: Tween = g.create_tween().set_parallel()
	tw.tween_property(g.scene.road, "speed", v, t).set_trans(Tween.TRANS_SINE)
	tw.tween_property(van(g), "speed", v, t).set_trans(Tween.TRANS_SINE)


## Продолжить с сохранения в главе 4: место и свет — по сцене.
static func restore(g, id: String) -> void:
	g.c4["on"] = true
	if id in ["c4_recap", "c4_s1"]:
		return   # двор ставит сценарий (fx c4_start)
	if id.begins_with("c4_s3") or id.begins_with("c4_s4") or id.begins_with("c4_s5") or id.begins_with("c4_s2") or id.begins_with("c4_sana"):
		_road(g)
		day(g, "sunny")
		seat_van(g)
		return
	if id.begins_with("c4_s6"):
		_woods_stage(g)
		return
	if id.begins_with("c4_s7"):
		_lake_stage(g)


static func fx(g, k: String) -> bool:
	if not String(g._scene_id).begins_with("c4"):
		return false
	var s: DriveScene = g.scene
	var c4: Dictionary = g.c4
	match k:
		"c4_start":   # солнечное утро во дворе Агнес; машина у забора, буханка у дома
			c4["on"] = true
			_yard(g)
		"car_door":
			g.sfx.play("car_door", -8.0)
			_note_props(g, true)
		"hale_note":
			_note_props(g, true)
		"pills_pocket":
			_note_props(g, false)
			g.sfx.play("pills", -14.0)
			(g.actors.noah as Actor).play("Interact", 0.0)
		"pills_throw":   # пузырёк летит через машину в сугроб
			_note_props(g, false)
			(g.actors.noah as Actor).play("OverhandThrow", 0.0)
			_fly_bottle(g)
		"pills_give":
			_note_props(g, false)
			var iris: Actor = g.actors.iris
			iris.reach = {"_r": g._target("noah") + Vector3(0, -0.45, 0)}
			g.get_tree().create_timer(1.4, false).timeout.connect(func():
				if is_instance_valid(iris):
					iris.reach = {})
			g.sfx.play("pills", -16.0)
		"shed_open":   # буханка под снегом у дома; бабушка рядом
			var vn := van(g)
			put(g, "agnes", VAN_YARD + Vector3(2.4, 0, -1.9), -150.0, "Idle_FoldArms")
			put(g, "noah", VAN_YARD + Vector3(4.4, 0, 1.2), -120.0, "Idle")
			put(g, "iris", VAN_YARD + Vector3(5.2, 0, 2.2), -110.0, "Idle_FoldArms")
			vn.snow(true)
		"van_hood":   # Ной у носа буханки — свечи, аккумулятор
			put(g, "noah", VAN_YARD + Vector3(2.75, 0, 0.2), -90.0, "Fixing_Kneeling")
			g.sfx.play("trunk", -14.0, 1.3)
		"van_push":   # все толкают сзади, Ной за рулём
			var vn := van(g)
			var st: Array = vn.seat("driver")
			Ch2.sit(g, "noah", st[0], float(st[1]), "Driving")
			var pushers := ["iris", "agnes", "zoe", "danny", "eva"]
			var i := 0
			for kk in pushers:
				if has(g, kk) and (g.actors[kk] as Actor).visible:
					if kk == "eva" and on(g, "baby"):
						put(g, "eva", VAN_YARD + Vector3(-4.2, 0, 2.6), 60.0, "Idle")   # Ева с младенцем — в стороне
						continue
					var dz: float = [-0.45, 0.45, 0.0, -0.9, 0.9][i]
					put(g, kk, vn.to_global(Vector3(dz, 0.0, 2.75)), vn.rotation_degrees.y + 180.0, "Push")
					i += 1
			vn.snow(false)
		"van_start":   # схватил — кашляет чёрным дымом; чуть проехал
			var vn := van(g)
			vn.engine(true, true)
			g.sfx.play("engine_start", -4.0, 0.8)
			var tw: Tween = g.create_tween()
			tw.tween_property(vn, "position", vn.position + vn.global_transform.basis.z * -2.2, 1.6).set_ease(Tween.EASE_OUT)
			c4["van_tw"] = tw   # дорога остановит, если сцена сменится раньше
			for kk in ["iris", "agnes", "zoe", "danny"]:
				if has(g, kk) and (g.actors[kk] as Actor).visible:
					(g.actors[kk] as Actor).play("Idle", 0.2)
		"zoe_clap":
			if has(g, "zoe"):
				(g.actors.zoe as Actor).play("Yes", 0.0)
		"van_drive":   # лесная дорога; буханку трясёт
			_road(g)
			day(g, "sunny")
			seat_van(g)
			van(g).engine(true)
			g.sfx.play("engine_start", -14.0, 0.8)
		"zoe_laugh":
			if has(g, "zoe"):
				(g.actors.zoe as Actor).expr = {"mouthSmileLeft": 0.45, "mouthSmileRight": 0.4, "cheekSquintLeft": 0.2}
		"zoe_window":
			if has(g, "zoe"):
				var z: Actor = g.actors.zoe
				z.expr = {}
				z.look_target = van(g).to_global(Vector3(0.4, 1.6, 3.2))   # в заднее окно — вполоборота (окно за спиной — шея не крутится на 180°)
		"radio_hale":
			g.sfx.play("static_burst", -12.0, 0.9)
		"phone_hale":
			g.sfx.play("phone_ring", -6.0)
			if has(g, "agnes"):
				var a: Actor = g.actors.agnes
				a.reach = {"_r": g._target("agnes") + Vector3(0.12, -0.08, -0.12)}
		"radio_off":
			g.sfx.play("static_burst", -16.0, 1.1)
			g._radio = false   # своя обработка гасит и общее радио (раньше песня главы 2 играла дальше)
			g.c2.erase("song")
			if has(g, "agnes"):
				(g.actors.agnes as Actor).reach = {}
		"bus_stop":   # буханка закипает у заброшенной остановки
			_bus_stop(g)
		"van_walk":   # двор: Ной сам готовит буханку (прогулка c4v); дедов ящик у сарая, остальные — по углам двора
			_van_walk_stage(g)
		"stop_walk":   # остановка: мотор кипит — ждём, пока остынет (прогулка c4b); все вышли размяться
			_stop_walk_stage(g)
		"sana_fork":   # снова в пути после остановки
			_back_on_road(g)
		"sana_stop":   # у поворота в лес Ной притормозил: справа — указатель на «Тихие сосны»
			_sana_turn(g)
		"sana_arrive":   # свернули: лесная дорога упирается в ворота санатория; все в буханке
			_sana_arrive(g)
		"sana_gate_out":   # Ной и Айрис вышли к воротам; створка провисла — Ной протискивается
			put(g, "iris", spos(-0.9, 0.0, 10.75), 180.0, "Idle_FoldArms")
			put(g, "noah", spos(0.35, 0.0, 10.6), 200.0, "Idle")
			g.sfx.play("car_door", -16.0, 0.9)
		"sana_board":   # окно архива: доска на одном гвозде
			put(g, "noah", spos(11.25, 0.0, -1.0), 90.0, "Interact")
		"sana_in":   # доска отодрана — Ной внутри, у окна
			var sn := sana(g)
			if sn.plank and is_instance_valid(sn.plank):
				sn.plank.position = Vector3(Sana.AX0 - 0.55, 0.04, -0.7)
				sn.plank.rotation_degrees = Vector3(0.0, 12.0, 90.0)
			g.sfx.play("blow", -16.0, 1.3)
			put(g, "noah", spos(12.8, Sana.AFLOOR, -1.0), 90.0, "Idle")
			_sana_dim(g)
		"sana_back":   # вылез, вернулся к воротам; Айрис ждёт — лицом друг к другу; снова день
			day(g, "sunny")
			put(g, "noah", spos(0.5, 0.0, 11.3), -105.0, "Idle")
			put(g, "iris", spos(-0.6, 0.0, 11.0), 75.0, "Idle_FoldArms")
			if has(g, "iris"):
				(g.actors.iris as Actor).look_target = g._target("noah")
		"sana_out":   # обратно на трассу (на чёрном): санаторий убрать, буханка — снова в начале координат, все по местам
			_sana_out(g)
		"hood_up":
			put(g, "noah", van(g).to_global(Vector3(0.25, 0.0, -2.85)), van(g).rotation_degrees.y, "Fixing_Kneeling")
			g.sfx.play("radio_hiss", -16.0, 0.6)
		"iris_bench":   # Айрис на лавке; Ной садится рядом, она протягивает блокнот
			var b: Node3D = c4.get("stop")
			# на лавку (лавка — z 0,25…0,65 по остановке; раньше z −0,2 — перед лавкой, в воздухе)
			Ch2.sit(g, "iris", b.to_global(Vector3(-0.35, 0.47, 0.42)), b.rotation_degrees.y + 180.0, "Rk_SitIdle")
			Ch2.sit(g, "noah", b.to_global(Vector3(0.35, 0.47, 0.42)), b.rotation_degrees.y + 180.0, "Rk_SitIdle")
			notebook(g, "iris")
		"page_1", "page_2", "page_3", "page_4", "page_5":
			var n := int(k.right(1))
			if not c4.has("book"):
				notebook(g, "noah")
			elif n == 1:
				notebook(g, "noah")
			_page(g, n)
		"notebook_close", "notebook_take":
			notebook(g, "iris")
			_page(g, 0)
		"iris_shake":
			if has(g, "iris"):
				(g.actors.iris as Actor).expr = {"browInnerUp": 0.5, "mouthFrownLeft": 0.2, "mouthFrownRight": 0.2}
		"hug":
			_hug(g, true)
		"iris_cry":
			if has(g, "iris"):
				(g.actors.iris as Actor).expr = {"browInnerUp": 0.9, "eyeSquintLeft": 0.5, "eyeSquintRight": 0.5, "mouthFrownLeft": 0.5, "mouthFrownRight": 0.5, "jawOpen": 0.12}
		"iris_sleep":   # Айрис спит в буханке, блокнот на коленях
			var vn := van(g)
			var st: Array = vn.seat("l1")
			Ch2.sit(g, "iris", st[0], float(st[1]))
			g._expr({"who": "iris", "set": "sleep"})
			put(g, "noah", vn.to_global(Vector3(1.3, 0.0, -0.4)), vn.rotation_degrees.y - 90.0, "Idle")
			notebook(g, "iris")
		"notebook_peek":
			notebook(g, "noah")
		"iris_wake":
			g._expr({"who": "iris", "set": "none"})
			if has(g, "iris"):
				(g.actors.iris as Actor).look_target = g._target("noah")
		"danny_sit":   # Дэнни садится на бампер рядом с Ноем
			Ch2.sit(g, "danny", van(g).to_global(Vector3(0.85, 0.6, -2.4)), van(g).rotation_degrees.y + 190.0)
		"pine":   # поперёк дороги — сосна; встаём
			_pine(g)
		"sedan_behind":
			_sedan(g)
		"orderly_out":
			put(g, "orderly", van(g).to_global(Vector3(-1.5, 0.0, 9.6)), 180.0 + van(g).rotation_degrees.y, "Idle")
			g.sfx.play("car_door", -8.0)
			_out_of_van(g)
		"hale_out":
			put(g, "hale", van(g).to_global(Vector3(1.1, 0.0, 9.0)), 180.0 + van(g).rotation_degrees.y, "Idle")
			g.sfx.play("car_door", -10.0, 1.1)
			_hale_glasses(g)
		"hale_look_van":
			(g.actors.hale as Actor).look_target = g._target("agnes")
		"hale_nod_iris":
			(g.actors.hale as Actor).look_target = g._target("iris")
		"hale_hand", "hale_elbow":
			var h: Actor = g.actors.hale
			h.look_target = g._target("noah")
			if k == "hale_elbow":
				put(g, "hale", van(g).to_global(Vector3(-0.3, 0.0, 4.4)), 180.0 + van(g).rotation_degrees.y, "Idle")
			h.reach = {"_r": g._target("noah") + Vector3(0.1, -0.75, -0.25)}
		"iris_pull":
			if has(g, "hale"):
				(g.actors.hale as Actor).reach = {}
			(g.actors.iris as Actor).reach = {"_r": g._target("noah") + Vector3(0.0, -0.7, 0.2)}
		"hale_cold":
			(g.actors.hale as Actor).expr = {"browDownLeft": 0.6, "browDownRight": 0.6, "mouthPressLeft": 0.5, "mouthPressRight": 0.5}
			if has(g, "orderly"):
				(g.actors.orderly as Actor).play("Walk", 0.0)
		"hale_nod", "orderly_step":
			if has(g, "orderly"):
				(g.actors.orderly as Actor).play("Walk", 0.0)
				g._walk({"who": "orderly", "to": _arr(van(g).to_global(Vector3(-1.0, 0.0, 6.0))), "speed": 1.0, "then": "Idle"})
		"diary_give", "diary_force", "hale_diary":
			if k == "diary_force":
				(g.actors.iris as Actor).expr = (g.EXPR["shock"] as Dictionary).duplicate()
			_diary(g, "hale" if k == "hale_diary" else "noah")
		"diary_hold":
			_diary(g, "iris")
		"flash_shore":   # вспышка: ночь, берег озера, фары — этот самый седан
			_flash(g, true)
		"flash_end":
			_flash(g, false)
		"crowbar":
			_crowbar(g, true)
			(g.actors.noah as Actor).play("Idle", 0.0)
		"orderly_grab":
			put(g, "orderly", van(g).to_global(Vector3(-0.8, 0.0, 4.4)), 180.0 + van(g).rotation_degrees.y, "Punch_Jab")
			(g.actors.noah as Actor).play("Hit_Chest", 0.0)
		"orderly_down":   # провал: санитар лежит и больше не встаёт
			var o: Actor = g.actors.orderly
			put(g, "orderly", van(g).to_global(Vector3(-1.3, 0.0, 5.4)), 120.0, "Death01")
			o.play("Death01", 0.0)
			g.get_tree().create_timer(0.1, false).timeout.connect(func():
				if is_instance_valid(o):
					Ch3.hold(o, "Death01", 3.0))
			(g.actors.noah as Actor).play("Idle", 0.0)
			(g.actors.noah as Actor).enable_blood()
			_crowbar(g, false)
		"hale_back":
			g._walk({"who": "hale", "to": _arr(van(g).to_global(Vector3(1.2, 0.0, 8.6))), "speed": 0.8, "then": "Idle"})
		"noah_down":   # таблетка в крови: санитар валит Ноя в снег
			var vn := van(g)
			put(g, "noah", vn.to_global(Vector3(-0.6, 0.0, 4.8)), 90.0, "LayToIdle")
			Ch3.hold(g.actors.noah, "LayToIdle", 0.0)
			put(g, "orderly", vn.to_global(Vector3(-0.2, 0.0, 5.3)), -60.0 + vn.rotation_degrees.y, "Crouch_Idle")
			_crowbar(g, false)
		"syringe":   # Хейл со шприцем шагает к Ною, лежащему в снегу
			if has(g, "hale"):
				put(g, "hale", van(g).to_global(Vector3(0.45, 0.0, 6.3)), -145.0 + van(g).rotation_degrees.y, "Idle")
			_syringe(g, true)
		"iris_hit":   # Айрис сбоку бьёт его по руке
			put(g, "iris", van(g).to_global(Vector3(1.05, 0.0, 6.7)), -124.0 + van(g).rotation_degrees.y, "Punch_Jab")
			_syringe(g, false)
		"run_woods":   # все бегут в лес
			_run_woods(g)
		"woods_dusk":   # сумерки, снег по колено, впереди избушка
			_woods_stage(g)
		"hut_in":
			_hut_in(g)
		"hut_door":   # Ной ползёт к приоткрытой двери избушки
			put(g, "noah", wpos(0.3, Woods.FLOOR, 1.35), 0.0, "Crouch_Idle")
			g.sfx.play("snow_steps", -18.0, 0.7)
		"beam_pass2":   # луч ещё раз проходит по щелям (шаги за стеной)
			g.sfx.play("snow_steps", -15.0, 0.8)
		"baby_whimper":
			g.sfx.play("baby_cry", -18.0, 1.15)
		"eva_rock", "iris_rock":
			c4["rock"] = "eva" if k == "eva_rock" else "iris"
		"beam_pass":
			c4["beam_mode"] = "pass"
			c4.erase("rock")
		"baby_cry":
			c4.erase("rock")
			g.sfx.play("baby_cry", -6.0)
		"beam_turn":
			c4["beam_mode"] = "hut"
		"hut_run":
			_run_woods(g, true)
		"zoe_grab":
			_zoe_grab(g)
		"zoe_rough":
			(g.actors.noah as Actor).enable_blood()
			var hunter := "orderly" if not on(g, "orderly_dead") else "hale"
			if has(g, hunter):
				put(g, hunter, wpos(9.5, 0.0, 4.6), 30.0, "LayToIdle")
				Ch3.hold(g.actors[hunter], "LayToIdle", 0.0)
			put(g, "zoe", wpos(8.2, 0.0, 3.4), -60.0, "Idle")
			put(g, "noah", wpos(8.8, 0.0, 2.6), -100.0, "Idle")
		"zoe_pull":
			var hunter := "orderly" if not on(g, "orderly_dead") else "hale"
			if has(g, hunter):
				put(g, hunter, wpos(10.2, 0.0, 4.0), 60.0, "Hit_Knockback")
			put(g, "noah", wpos(9.0, 0.0, 3.0), 70.0, "Punch_Jab")
			put(g, "zoe", wpos(7.6, 0.0, 2.6), -80.0, "Jog_Fwd")
		"zoe_dragged":
			var hunter := "orderly" if not on(g, "orderly_dead") else "hale"
			if has(g, hunter):
				g._walk({"who": hunter, "to": _arr(wpos(30.0, 0.0, 2.0)), "speed": 1.2, "then": "Idle"})
			if has(g, "zoe"):
				g._walk({"who": "zoe", "to": _arr(wpos(29.2, 0.0, 1.4)), "speed": 1.2, "then": "Idle", "clip": "Walk"})
		"agnes_sit":
			put(g, "agnes", wpos(5.4, 0.0, 14.0), 160.0, "Crouch_Idle")
			put(g, "noah", wpos(5.9, 0.0, 18.5), 170.0, "Idle")
			put(g, "iris", wpos(6.8, 0.0, 19.5), 175.0, "Idle")
			_flash_actor(g)
		"hale_dark", "hale_call":
			c4["beam_mode"] = "search"
		"carry_agnes":
			_carry_agnes(g)
		"agnes_left":
			put(g, "noah", wpos(6.0, 0.0, 24.0), 10.0, "Walk")
			put(g, "iris", wpos(6.8, 0.0, 25.0), 10.0, "Walk")
		"agnes_song":
			g.sfx.play("lullaby", -10.0, 0.92, 0.0)
		"song_stop":
			g.sfx.stop("lullaby", 1.6)
			if has(g, "agnes"):
				Ch3.lie(g, "agnes", wpos(5.4, 0.0, 14.0), 160.0)
		"danny_freeze":
			put(g, "danny", wpos(4.8, 0.0, 21.0), 120.0, "Rk_LookAround")
		"danny_to_iris":
			g._walk({"who": "danny", "to": _arr(wpos(6.4, 0.0, 22.6)), "speed": 1.0, "then": "Idle"})
		"danny_bite":
			put(g, "danny", wpos(3.0, 0.0, 19.0), 230.0, "Jog_Fwd")
			g.sfx.play("blow", -14.0, 1.4)
		"danny_gone":
			if has(g, "danny"):
				(g.actors.danny as Actor).visible = false
			c4["beam_mode"] = "off"
			g.sfx.play("car_door", -20.0, 0.9)
		"lake_dawn":   # рассвет: лес кончается — белое озеро, дача, сарай, мостки
			_lake_stage(g)
		"ice_walk":   # лёд вдоль берега к мосткам (прогулка c4i): тёмные пятна молодого льда, старая лунка; все — у кромки леса
			_ice_stage(g)
		"ice_crack":   # тёмный лёд под ногой трещит — назад
			g.sfx.play("ice_crack", -6.0, 1.1)
			g._shake = maxf(g._shake, 0.35)
		"ice_fall":   # провалился по пояс
			g.sfx.play("ice_crack", -2.0, 0.9)
			g.sfx.play("splash", -4.0, 0.8)
			g._shake = maxf(g._shake, 0.7)
			var n: Actor = g.actors.noah
			_ice_hole(g, n.global_position)
			n.position.y -= 0.7
		"ice_out":
			pass
		"ice_done":   # перешли: все у мостков, напротив лодочного сарая
			_ice_done(g)
		"boathouse_gap":
			_kid(g, false)
		"kid_eyes":
			_kid(g, true)
		"far_sedan":
			_far_sedan(g)
		_:
			return false
	return true


static func _arr(v: Vector3) -> Array:
	return [v.x, v.y, v.z]


# ---------- двор ----------
static func _yard(g) -> void:
	var s: DriveScene = g.scene
	var v := Ch3.village(g)
	v.visible = true
	v.set_power(false)
	v.night_glow(false)
	v.radio_glow(false)
	day(g, "sunny")
	g._blizzard = false
	s.road.snow.amount = Settings.pn(1 if not s.pc else 120)   # редкие искры в воздухе (0 частиц Godot не принимает)
	s.env.fog_density = 0.0035
	s.car.visible = true
	s.car.position = CAR_YARD
	s.car.rotation = Vector3.ZERO
	s.car.speed = 0.0
	s.car.ride = false
	s.car.lights(false)
	var vn := van(g)
	vn.position = VAN_YARD
	vn.rotation_degrees = Vector3(0, -90, 0)   # носом к улице (+X)
	vn.speed = 0.0
	vn.ride = false
	vn.snow(true)
	vn.engine(false)
	var who := cast(g)
	var spots := {"noah": [Vector3(1.05, 0, -36.75), 90.0], "iris": [Vector3(0.3, 0, -35.8), 70.0], "agnes": [Vector3(-3.6, 0, -40.6), 60.0],
		"zoe": [Vector3(0.4, 0, -38.9), 95.0], "danny": [Vector3(-0.5, 0, -37.9), 80.0], "eva": [Vector3(-1.4, 0, -39.4), 60.0]}
	for k in who:
		var sp: Array = spots[k]
		put(g, k, sp[0], float(sp[1]), "Idle_FoldArms" if k in ["iris", "agnes"] else "Idle")
	baby(g)
	for k in g.actors:
		(g.actors[k] as Actor).look_target = null


## Пузырёк и записка на водительском сиденье (и записка крупно).
static func _note_props(g, show: bool) -> void:
	var old = g.c4.get("note")
	if show and old and is_instance_valid(old):
		return
	if not show:
		if old and is_instance_valid(old):
			(old as Node).queue_free()
		g.c4.erase("note")
		return
	var car: Car = g.scene.car
	var root := Node3D.new()
	car.add_child(root)
	root.position = Vector3(-0.36, 0.53, 0.28)   # подушка водительского кресла
	var paper := MeshInstance3D.new()
	var pm := BoxMesh.new()
	pm.size = Vector3(0.16, 0.002, 0.115)
	paper.mesh = pm
	var wm := StandardMaterial3D.new()
	wm.albedo_color = Color(0.93, 0.92, 0.88)
	wm.roughness = 0.85
	paper.material_override = wm
	paper.rotation_degrees = Vector3(0, 14, 0)
	root.add_child(paper)
	var lb := Label3D.new()   # ровный почерк
	lb.text = String(NOTE.get(g.lang, NOTE.en))
	lb.font = load("res://assets/fonts/Pangolin-Regular.woff2")
	lb.font_size = 48
	lb.pixel_size = 0.00021
	lb.modulate = Color(0.1, 0.12, 0.25)
	lb.outline_size = 0
	lb.shaded = true
	lb.horizontal_alignment = HORIZONTAL_ALIGNMENT_LEFT   # у Label3D «влево» = текст идёт вправо от точки: точка — у левого поля
	lb.position = Vector3(-0.066, 0.0015, -0.01)
	lb.rotation_degrees = Vector3(-90, 0, 0)
	paper.add_child(lb)
	var sg := lb.duplicate() as Label3D   # подпись — у правого поля, ниже
	sg.text = String(NOTE_SIGN.get(g.lang, NOTE_SIGN.en))
	sg.horizontal_alignment = HORIZONTAL_ALIGNMENT_RIGHT
	sg.position = Vector3(0.064, 0.0015, 0.03)
	paper.add_child(sg)
	var b := MeshInstance3D.new()   # пузырёк — на записке
	var cm := CylinderMesh.new()
	cm.top_radius = 0.02
	cm.bottom_radius = 0.02
	cm.height = 0.075
	b.mesh = cm
	var bm := StandardMaterial3D.new()
	bm.albedo_color = Color(0.75, 0.42, 0.12, 0.88)
	bm.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
	bm.roughness = 0.2
	b.material_override = bm
	b.position = Vector3(0.115, 0.038, 0.02)
	root.add_child(b)
	var cap := MeshInstance3D.new()
	var cc := CylinderMesh.new()
	cc.top_radius = 0.021
	cc.bottom_radius = 0.021
	cc.height = 0.018
	cap.mesh = cc
	var capm := StandardMaterial3D.new()
	capm.albedo_color = Color(0.92, 0.92, 0.9)
	cap.material_override = capm
	cap.position = Vector3(0, 0.046, 0)
	b.add_child(cap)
	g.c4["note"] = root
	g.c4["note_paper"] = paper


static func _fly_bottle(g) -> void:
	var b := MeshInstance3D.new()
	var cm := CylinderMesh.new()
	cm.top_radius = 0.02
	cm.bottom_radius = 0.02
	cm.height = 0.075
	b.mesh = cm
	var bm := StandardMaterial3D.new()
	bm.albedo_color = Color(0.75, 0.42, 0.12)
	b.material_override = bm
	g.scene.add_child(b)
	var p0: Vector3 = g._target("noah") + Vector3(0.2, 0.15, 0.0)
	var p1 := Vector3(5.1, 0.35, -38.4)
	b.global_position = p0
	var tw: Tween = g.create_tween()
	tw.tween_method(func(t: float):
		if is_instance_valid(b):
			b.global_position = p0.lerp(p1, t) + Vector3(0, sin(t * PI) * 2.2, 0)
			b.rotation.x = t * 14.0, 0.0, 1.0, 0.9)
	tw.tween_property(b, "global_position", p1 + Vector3(0, -0.4, 0), 0.4)
	g.c4["thrown"] = b


# ---------- дорога, остановка ----------
static func _road(g) -> void:
	var s: DriveScene = g.scene
	var v := Ch3.vil(g)
	if v:
		v.visible = false
	s.road.unpark()
	for n in s.road_items.get_children():
		n.queue_free()
	s.car.visible = false
	s.car.speed = 0.0
	g._blizzard = false
	s.road.snow.amount = 1   # 0 частиц Godot не принимает
	var vn := van(g)
	var vt = g.c4.get("van_tw")
	if vt and (vt as Tween).is_valid():
		(vt as Tween).kill()
	vn.position = Vector3.ZERO
	vn.rotation = Vector3.ZERO
	vn.snow(false)
	vn.ride = true
	vn.fill.light_energy = 0.55
	vn.dome.light_energy = 0.0
	vn.lights(false)
	s.road.speed = 11.0
	vn.speed = 11.0
	if g.blackcar and is_instance_valid(g.blackcar):
		g.blackcar.visible = false
	_mirror(g, true)


## Боковое зеркало водителя — настоящее отражение (позади — дорога, седан).
static func _mirror(g, on_: bool) -> void:
	var m = g.c4.get("mirror")
	if on_ and not (m and is_instance_valid(m)):
		var vn := van(g)
		var pm := PlanarMirror.new()
		vn.add_child(pm)
		pm.build(vn.to_global(Vector3(-1.141, 1.6, -1.84)), vn.global_transform.basis * Vector3(0.37, 0.037, 1.0), Vector2(0.15, 0.21), g.cam)   # глаз водителя → зеркало → дорога позади (седан в кадре)
		g.c4["mirror"] = pm
	elif not on_ and m and is_instance_valid(m):
		(m as Node).queue_free()
		g.c4.erase("mirror")


static func _bus_stop(g) -> void:
	var s: DriveScene = g.scene
	var root := Node3D.new()   # бетонный павильон с лавкой, ржавый знак
	s.road_items.add_child(root)
	var d := 24.0
	root.position = Vector3(5.4, 0.15, -d - 1.0)
	root.rotation_degrees = Vector3(0, -90, 0)   # открыт к дороге (−X)
	var conc := StandardMaterial3D.new()   # старый серый бетон в потёках (Poly Haven concrete_floor_02, по трём осям)
	var tx := "res://assets/ph/concrete_floor_02/concrete_floor_02_"
	conc.albedo_texture = load(tx + "diff_1k.jpg")
	conc.albedo_color = Color(0.78, 0.78, 0.76)
	conc.normal_enabled = true
	conc.normal_texture = load(tx + "nor_gl_1k.jpg")
	conc.roughness = 0.95
	conc.uv1_triplanar = true
	conc.uv1_scale = Vector3(0.45, 0.45, 0.45)
	var dark := StandardMaterial3D.new()
	dark.albedo_color = Color(0.25, 0.2, 0.17)
	dark.roughness = 0.8
	var snow := StandardMaterial3D.new()
	snow.albedo_color = Color(0.88, 0.9, 0.95)
	snow.roughness = 0.9
	_mbox(root, Vector3(3.2, 2.3, 0.15), Vector3(0, 1.15, 0.75), conc)           # задняя стена
	_mbox(root, Vector3(0.15, 2.3, 1.5), Vector3(-1.55, 1.15, 0.0), conc)        # бока
	_mbox(root, Vector3(0.15, 2.3, 1.5), Vector3(1.55, 1.15, 0.0), conc)
	_mbox(root, Vector3(3.5, 0.14, 1.9), Vector3(0, 2.36, 0.0), conc)            # крыша
	_mbox(root, Vector3(3.4, 0.16, 1.8), Vector3(0, 2.5, 0.0), snow)
	_mbox(root, Vector3(2.6, 0.06, 0.4), Vector3(0, 0.45, 0.45), dark)           # лавка
	for x in [-1.0, 1.0]:
		_mbox(root, Vector3(0.08, 0.42, 0.3), Vector3(x, 0.21, 0.45), dark)
	var pole := MeshInstance3D.new()   # знак на столбе: жёлтый круг, выцвел
	var pc := CylinderMesh.new()
	pc.top_radius = 0.04
	pc.bottom_radius = 0.04
	pc.height = 2.6
	pole.mesh = pc
	pole.material_override = dark
	pole.position = Vector3(2.3, 1.3, -0.44)   # за кругом знака (лицо знака — к −z): раньше столб шёл перед буквой «А»
	root.add_child(pole)
	var sign := MeshInstance3D.new()
	var sc := CylinderMesh.new()
	sc.top_radius = 0.28
	sc.bottom_radius = 0.28
	sc.height = 0.02
	sign.mesh = sc
	var ym := StandardMaterial3D.new()
	ym.albedo_color = Color(0.72, 0.6, 0.2)
	ym.roughness = 0.7
	sign.material_override = ym
	sign.position = Vector3(2.3, 2.45, -0.5)
	sign.rotation_degrees = Vector3(90, 0, 0)   # лицом к −z, как буквы (было (90, 90, 0) — круг стоял ребром к надписи)
	root.add_child(sign)
	var lb := Label3D.new()
	lb.text = "А" if g.lang == "ru" else "BUS"
	lb.font_size = 160
	lb.modulate = Color(0.12, 0.14, 0.2)
	lb.outline_size = 0
	lb.shaded = true
	Signs.fit(Signs.style(lb, "road"), 0.4, 0.4, 0.86)   # в круге Ø 0,56 м
	lb.position = Vector3(2.3, 2.45, -0.5115)   # на лице круга (z −0,51)
	lb.rotation_degrees = Vector3(0, 180, 0)
	root.add_child(lb)
	_mbox(root, Vector3(4.6, 0.4, 2.6), Vector3(0.2, -0.05, -0.2), snow)   # площадка в снегу
	g.c4["stop"] = root
	var t := stop_in(g, d, 4.0)
	var vn := van(g)
	var steam := CPUParticles3D.new()   # пар из-под носа
	steam.amount = 90
	steam.lifetime = 2.4
	steam.direction = Vector3(0, 1, -0.4)
	steam.spread = 25.0
	steam.initial_velocity_min = 0.5
	steam.initial_velocity_max = 1.1
	steam.gravity = Vector3(0, 0.35, 0)
	steam.scale_amount_min = 0.8
	steam.scale_amount_max = 2.0
	steam.mesh = vn.exhaust.mesh
	steam.color = Color(0.95, 0.95, 0.97, 0.3)
	steam.position = Vector3(0.0, 0.85, -2.3)
	vn.add_child(steam)
	g.c4["steam"] = steam
	g.get_tree().create_timer(t, false).timeout.connect(func():
		if is_instance_valid(vn):
			vn.engine(false)
			if not g.c4.get("stop_walk", false):   # прогулка у остановки уже всех расставила (fx stop_walk)
				_out_at_stop(g))


static func _mbox(parent: Node3D, size: Vector3, pos: Vector3, m: Material) -> MeshInstance3D:
	var mi := MeshInstance3D.new()
	var bm := BoxMesh.new()
	bm.size = size
	mi.mesh = bm
	mi.material_override = m
	mi.position = pos
	parent.add_child(mi)
	return mi


## Двор Агнес перед прогулкой «буханка»: дедов ящик у двери сарая; Агнес у буханки, остальные — по углам двора.
static func _van_walk_stage(g) -> void:
	var v := Ch3.village(g)
	if not g.c4.has("toolbox"):
		var box := Node3D.new()
		v.add_child(box)
		box.position = Vector3(-16.55, 0.0, -51.2)
		var wood := StandardMaterial3D.new()
		wood.albedo_color = Color(0.3, 0.2, 0.12)
		wood.roughness = 0.85
		_mbox(box, Vector3(0.5, 0.28, 0.32), Vector3(0, 0.14, 0), wood)
		var steel := StandardMaterial3D.new()
		steel.albedo_color = Color(0.35, 0.36, 0.38)
		steel.metallic = 0.6
		steel.roughness = 0.45
		_mbox(box, Vector3(0.42, 0.03, 0.05), Vector3(0, 0.34, 0), steel)   # ручка
		g.c4["toolbox"] = box
	put(g, "agnes", Vector3(-7.6, 0.0, -57.8), -120.0, "Idle_FoldArms")
	put(g, "iris", Vector3(-14.8, 0.0, -57.6), 60.0, "Idle_FoldArms")
	put(g, "zoe", Vector3(-14.6, 0.0, -50.2), 120.0, "Idle")
	put(g, "danny", Vector3(-12.0, 0.0, -57.8), 20.0, "Idle")
	put(g, "eva", Vector3(-15.0, 0.0, -54.5), 90.0, "Idle")
	baby(g)
	for k in g.actors:
		(g.actors[k] as Actor).look_target = null


## Остановка перед прогулкой «пока остывает»: дорога встала намертво, павильон — на своём месте; все вышли размяться;
## у павильона — указатель на «Тихие сосны», Дэнни лепит снеговика.
static func _stop_walk_stage(g) -> void:
	var s: DriveScene = g.scene
	var vn := van(g)
	var tw = g.c4.get("speed_tw")
	if tw is Tween and (tw as Tween).is_valid():
		(tw as Tween).kill()
	s.road.speed = 0.0
	vn.speed = 0.0
	vn.engine(false)
	var b: Node3D = g.c4.get("stop")
	if b and is_instance_valid(b):
		b.position = Vector3(5.4, 0.15, -1.0)
	g.c4["stop_walk"] = true
	cast(g)
	put(g, "agnes", Vector3(-1.6, 0.0, 1.2), 90.0, "Idle_FoldArms")
	put(g, "eva", Vector3(-1.7, 0.0, -1.2), 90.0, "Idle")
	put(g, "iris", Vector3(2.8, 0.0, -4.2), 160.0, "Idle_FoldArms")
	put(g, "zoe", Vector3(6.8, 0.0, 2.2), 120.0, "Idle")
	put(g, "danny", Vector3(3.0, 0.0, 2.6), 60.0, "Crouch_Idle")
	baby(g)
	for k in g.actors:
		(g.actors[k] as Actor).look_target = null
	if b and is_instance_valid(b) and not g.c4.has("sana_sign"):
		var post := StandardMaterial3D.new()
		post.albedo_color = Color(0.3, 0.27, 0.24)
		post.roughness = 0.8
		var board := StandardMaterial3D.new()   # синий щит, краска облезла
		board.albedo_color = Color(0.16, 0.26, 0.42)
		board.roughness = 0.7
		# мир (7,6; −5,5) → местные оси павильона (повёрнут на −90°: x мира = 5,4 − z, z мира = −1 + x)
		_mbox(b, Vector3(0.08, 2.1, 0.08), Vector3(-4.5, 0.9, -2.2), post)
		_mbox(b, Vector3(1.25, 0.46, 0.04), Vector3(-4.5, 1.72, -2.16), board)
		var lb := Label3D.new()
		lb.text = "САНАТОРИЙ\n«ТИХИЕ СОСНЫ» 6 км →" if g.lang == "ru" else "QUIET PINES\nSANATORIUM 6 km →"
		lb.font_size = 64
		lb.modulate = Color(0.92, 0.93, 0.9)
		lb.outline_size = 0
		lb.shaded = true
		Signs.fit(Signs.style(lb, "road"), 1.25, 0.46, 0.84)   # текст был шире щита (1,25 м) — свисал по краям
		lb.position = Vector3(-4.5, 1.72, -2.1385)   # на лице щита (z −2,14)
		b.add_child(lb)
		g.c4["sana_sign"] = lb
	if has(g, "danny") and not g.c4.has("snowman"):   # снеговик Дэнни: два кома и ветка
		var sn := Node3D.new()
		g.scene.road_items.add_child(sn)
		sn.position = Vector3(3.6, 0.0, 2.9)
		var white := StandardMaterial3D.new()
		white.albedo_color = Color(0.9, 0.92, 0.96)
		white.roughness = 0.9
		for ball in [[0.28, 0.26], [0.18, 0.64]]:
			var mi := MeshInstance3D.new()
			var sm := SphereMesh.new()
			sm.radius = ball[0]
			sm.height = ball[0] * 2.0
			mi.mesh = sm
			mi.material_override = white
			mi.position = Vector3(0, ball[1], 0)
			sn.add_child(mi)
		g.c4["snowman"] = sn


## Вышли у остановки: Айрис — на лавку, Ной — к носу буханки, остальные рядом.
static func _out_at_stop(g) -> void:
	var vn := van(g)
	var b: Node3D = g.c4.get("stop")
	cast(g)
	put(g, "noah", vn.to_global(Vector3(0.25, 0.0, -2.85)), vn.rotation_degrees.y, "Fixing_Kneeling")
	if b:
		Ch2.sit(g, "iris", b.to_global(Vector3(-0.35, 0.47, 0.42)), b.rotation_degrees.y + 180.0, "Rk_SitIdle")
	put(g, "agnes", vn.to_global(Vector3(1.9, 0.0, 0.4)), vn.rotation_degrees.y - 70.0, "Idle_FoldArms")
	put(g, "zoe", vn.to_global(Vector3(2.4, 0.0, -1.4)), vn.rotation_degrees.y - 120.0, "Idle")
	put(g, "danny", vn.to_global(Vector3(1.4, 0.0, -2.2)), vn.rotation_degrees.y + 200.0, "Idle")
	put(g, "eva", vn.to_global(Vector3(2.9, 0.0, 1.6)), vn.rotation_degrees.y - 80.0, "Idle")
	baby(g)


## Блокнот Айрис (рисунки): раскрытый — в руках у who; страница — текстура рисунка.
static func notebook(g, who: String) -> void:
	var bk = g.c4.get("book")
	if not (bk and is_instance_valid(bk)):
		var book := Node3D.new()
		g.scene.add_child(book)
		var cover := StandardMaterial3D.new()
		cover.albedo_color = Color(0.12, 0.16, 0.24)
		cover.roughness = 0.7
		var cv := MeshInstance3D.new()
		var cb := BoxMesh.new()
		cb.size = Vector3(0.3, 0.006, 0.21)
		cv.mesh = cb
		cv.material_override = cover
		book.add_child(cv)
		var page := MeshInstance3D.new()
		var pq := QuadMesh.new()
		pq.size = Vector2(0.28, 0.2)
		page.mesh = pq
		var pm := StandardMaterial3D.new()
		pm.albedo_color = Color(0.92, 0.9, 0.84)
		pm.roughness = 0.9
		page.material_override = pm
		page.position = Vector3(0, 0.0045, 0)
		page.rotation_degrees = Vector3(-90, 0, 0)
		book.add_child(page)
		g.c4["book"] = book
		g.c4["book_page"] = page
	g.c4["book_who"] = who
	if has(g, who):
		var a: Actor = g.actors[who]
		var fwd := a.global_transform.basis.z
		fwd = Vector3(fwd.x, 0.0, fwd.z).normalized()
		var left := Vector3.UP.cross(fwd).normalized()
		var chest := a.global_position + Vector3(0, 0.95, 0) + fwd * 0.32
		if a.ap and a.ap.current_animation.begins_with("Rk_Sit"):
			chest = a.global_position + Vector3(0, 0.62, 0) + fwd * 0.38
		a.reach = {"_l": chest + left * 0.12, "_r": chest - left * 0.12}
		a._reach_w = 1.0   # руки — сразу с блокнотом (план может начаться в тот же миг)


static func _page(g, n: int) -> void:
	g.c4["page_n"] = n   # лист поверх кадра (game._cut → hud.page_view)
	var page = g.c4.get("book_page")
	if not (page and is_instance_valid(page)):
		return
	var pm := (page as MeshInstance3D).material_override as StandardMaterial3D
	pm.albedo_texture = load("res://assets/textures/drawing_%d.png" % n) if n > 0 else null
	if n > 0:
		g.sfx.play("page_turn", -10.0)


static func _hug(g, on_: bool) -> void:
	var n: Actor = g.actors.noah
	var i: Actor = g.actors.iris
	if not on_:
		n.reach = {}
		i.reach = {}
		return
	var bk = g.c4.get("book")
	if bk and is_instance_valid(bk):
		(bk as Node3D).visible = false
	var ih: Vector3 = g._target("iris")
	var nh: Vector3 = g._target("noah")
	var side := Vector3(ih.x - nh.x, 0.0, ih.z - nh.z).normalized()   # от Ноя к Айрис
	var nl: Vector3 = n.global_transform.basis.x                       # левая рука героя — к его +X
	var near := "_l" if side.dot(nl) > 0.0 else "_r"
	var far := "_r" if near == "_l" else "_l"
	n.reach = {near: ih + side * 0.16 + Vector3(0, -0.24, 0), far: ih - side * 0.05 + Vector3(0, -0.42, 0)}
	var il: Vector3 = i.global_transform.basis.x
	i.reach = {("_l" if (-side).dot(il) > 0.0 else "_r"): nh + Vector3(0, -0.38, 0) + side * 0.05}
	n.look_target = ih + Vector3(0, -0.05, 0)
	i.look_target = nh + Vector3(0, -0.3, 0)


# ---------- сосна, Хейл и санитар ----------
## Снова в пути: все в буханке, пар кончился, остановка (и снеговик Дэнни) остались позади.
static func _back_on_road(g) -> void:
	var s: DriveScene = g.scene
	var vn := van(g)
	var st = g.c4.get("steam")
	if st and is_instance_valid(st):
		(st as CPUParticles3D).emitting = false
	var bk = g.c4.get("book")
	if bk and is_instance_valid(bk):
		(bk as Node).queue_free()
		g.c4.erase("book")
	_hug(g, false)
	for key in ["stop", "snowman", "turn_sign"]:
		var n = g.c4.get(key)
		if n and is_instance_valid(n):
			(n as Node).queue_free()
		g.c4.erase(key)
	var tw = g.c4.get("speed_tw")
	if tw is Tween and (tw as Tween).is_valid():
		(tw as Tween).kill()
	seat_van(g)
	vn.engine(true)
	vn.ride = true
	s.road.speed = 11.0
	vn.speed = 11.0


## Поворот на «Тихие сосны»: буханка стоит, ржавый указатель — справа у обочины, в паре метров перед носом.
static func _sana_turn(g) -> void:
	var s: DriveScene = g.scene
	var vn := van(g)
	var tw = g.c4.get("speed_tw")
	if tw is Tween and (tw as Tween).is_valid():
		(tw as Tween).kill()
	s.road.speed = 0.0
	vn.speed = 0.0
	var old = g.c4.get("turn_sign")
	if old and is_instance_valid(old):
		(old as Node).queue_free()
	var post := StandardMaterial3D.new()
	post.albedo_color = Color(0.3, 0.27, 0.24)
	post.roughness = 0.8
	var board := StandardMaterial3D.new()
	board.albedo_color = Color(0.16, 0.26, 0.42)
	board.roughness = 0.7
	var root := Node3D.new()
	s.road_items.add_child(root)
	root.position = Vector3(3.9, 0.0, -8.2)
	_mbox(root, Vector3(0.08, 2.1, 0.08), Vector3(0.0, 1.05, 0.0), post)
	_mbox(root, Vector3(1.3, 0.5, 0.04), Vector3(0.0, 1.8, 0.04), board)
	var lb := Label3D.new()   # щит повёрнут к дороге (+z): его видно из кабины
	lb.text = "САНАТОРИЙ\n«ТИХИЕ СОСНЫ» 2 км →" if g.lang == "ru" else "QUIET PINES\nSANATORIUM 2 km →"
	lb.font_size = 64
	lb.modulate = Color(0.92, 0.93, 0.9)
	lb.outline_size = 0
	lb.shaded = true
	lb.double_sided = false
	Signs.fit(Signs.style(lb, "road"), 1.3, 0.5, 0.84)   # щит 1,3 × 0,5 м
	lb.position = Vector3(0.0, 1.8, 0.0615)   # на лице щита (z 0,06)
	root.add_child(lb)
	g.c4["turn_sign"] = root


## Заезд в санаторий: дорогу остановить, буханку — к воротам (носом к ним), все сидят по местам.
static func _sana_arrive(g) -> void:
	var s: DriveScene = g.scene
	var vn := van(g)
	var tw = g.c4.get("speed_tw")
	if tw is Tween and (tw as Tween).is_valid():
		(tw as Tween).kill()
	s.road.speed = 0.0
	vn.speed = 0.0
	vn.ride = false
	vn.engine(false)
	_mirror(g, false)
	var tsn = g.c4.get("turn_sign")
	if tsn and is_instance_valid(tsn):
		(tsn as Node).queue_free()
	g.c4.erase("turn_sign")
	sana(g)
	vn.position = SANA + Vector3(0.0, 0.0, 15.6)
	vn.rotation = Vector3.ZERO
	seat_van(g)
	for k in g.actors:
		(g.actors[k] as Actor).look_target = null


## Внутри архива: солнце сквозь крышу не светит (тени от солнца у построек выключены — ради скорости), поэтому
## день на время архива приглушён — свет даёт окно (прожектор в пристройке) и слабая заливка. Наружу — fx sana_back.
static func _sana_dim(g) -> void:
	var s: DriveScene = g.scene
	var tw: Tween = g.create_tween().set_parallel()
	tw.tween_property(s.moon, "light_energy", 0.1, 0.5)
	tw.tween_property(s.env, "ambient_light_energy", 0.2, 0.5)


static func _sana_out(g) -> void:
	var sn = g.c4.get("sana")
	if sn and is_instance_valid(sn):
		(sn as Node).queue_free()
	g.c4.erase("sana")
	_road(g)
	day(g, "sunny")
	seat_van(g)
	van(g).engine(true)


static func _pine(g) -> void:
	var s: DriveScene = g.scene
	_back_on_road(g)
	_mirror(g, true)
	var root := Node3D.new()
	s.road_items.add_child(root)
	var d := 30.0
	root.position = Vector3(0.0, 0.0, -d - 2.4 - 3.0)   # встанем в трёх метрах до ствола
	var tree := MeshInstance3D.new()   # ель лежит поперёк дороги, комель — у пня на обочине
	tree.mesh = s.road._fir_mesh2(41)
	tree.rotation_degrees = Vector3(0, 0, 90)
	tree.scale = Vector3(1.15, 1.15, 1.15)
	tree.position = Vector3(5.8, 0.42, 0.0)
	root.add_child(tree)
	var wood := StandardMaterial3D.new()
	wood.albedo_texture = load("res://assets/ph/dead_tree_trunk/textures/dead_tree_trunk_diff_1k.jpg")
	wood.albedo_color = Color(0.7, 0.62, 0.55)
	wood.roughness = 0.9
	var cut := StandardMaterial3D.new()   # свежий спил — светлое дерево с кольцами
	var gt := GradientTexture2D.new()
	var gr := Gradient.new()
	gr.set_color(0, Color(0.55, 0.38, 0.2))
	gr.set_color(1, Color(0.86, 0.72, 0.5))
	for k in 7:
		gr.add_point(0.12 + k * 0.12, Color(0.72, 0.56, 0.34) if k % 2 == 0 else Color(0.85, 0.7, 0.48))
	gt.gradient = gr
	gt.fill = GradientTexture2D.FILL_RADIAL
	gt.fill_from = Vector2(0.5, 0.5)
	gt.fill_to = Vector2(0.5, 0.0)
	cut.albedo_texture = gt
	cut.roughness = 0.8
	var stump := MeshInstance3D.new()
	var sc := CylinderMesh.new()
	sc.top_radius = 0.24
	sc.bottom_radius = 0.3
	sc.height = 0.55
	stump.mesh = sc
	stump.material_override = wood
	stump.position = Vector3(6.1, 0.3, 0.6)
	root.add_child(stump)
	var face := MeshInstance3D.new()
	var fc := CylinderMesh.new()
	fc.top_radius = 0.235
	fc.bottom_radius = 0.235
	fc.height = 0.01
	face.mesh = fc
	face.material_override = cut
	face.position = Vector3(6.1, 0.58, 0.6)
	root.add_child(face)
	var butt := MeshInstance3D.new()   # торец ствола у пня
	butt.mesh = fc
	butt.material_override = cut
	butt.position = Vector3(5.3, 0.45, 0.0)
	butt.rotation_degrees = Vector3(0, 0, 90)
	root.add_child(butt)
	var log := MeshInstance3D.new()
	var lc := CylinderMesh.new()
	lc.top_radius = 0.22
	lc.bottom_radius = 0.23
	lc.height = 2.4
	log.mesh = lc
	log.material_override = wood
	log.position = Vector3(4.1, 0.42, 0.0)
	log.rotation_degrees = Vector3(0, 0, 90)
	root.add_child(log)
	g.c4["pine"] = root
	stop_in(g, d, 4.0)


static func _sedan(g) -> void:
	var vn := van(g)
	if not (g.blackcar and is_instance_valid(g.blackcar)):
		g.blackcar = BlackCar.new()
		g.scene.add_child(g.blackcar)
	var bc: BlackCar = g.blackcar
	if bc.get_parent() != g.scene:
		bc.reparent(g.scene, false)
	bc.visible = true
	bc.position = vn.to_global(Vector3(-2.2, 0.0, 34.0))   # по встречной полосе — так его видно в левом зеркале (прямо позади его закрывает кузов)
	bc.rotation = vn.rotation
	bc.speed = 0.0
	bc.lights(true)
	var tw: Tween = g.create_tween()
	tw.tween_property(bc, "position", vn.to_global(Vector3(-2.2, 0.0, 16.0)), 1.2).set_trans(Tween.TRANS_SINE)
	tw.tween_property(bc, "position", vn.to_global(Vector3(0.0, 0.0, 9.5)), 1.1).set_ease(Tween.EASE_OUT).set_trans(Tween.TRANS_QUAD)
	tw.tween_callback(func():
		if is_instance_valid(bc):
			bc.lights(false))
	g.sfx.play("engine_start", -16.0, 0.92)
	actor(g, "hale", "hale" if ResourceLoader.exists("res://assets/chars/hale.glb") else "frank").visible = false
	actor(g, "orderly", "orderly" if ResourceLoader.exists("res://assets/chars/orderly.glb") else "gus").visible = false


## Все выходят из буханки: стоят между ней и седаном.
static func _looks_off(g) -> void:
	for k in g.actors:
		var a = g.actors[k]
		if is_instance_valid(a):
			(a as Actor).look_target = null
			(a as Actor).reach = {}


static func _out_of_van(g) -> void:
	_looks_off(g)
	var vn := van(g)
	_mirror(g, false)
	var who := cast(g)
	var spots := {"noah": [Vector3(-0.6, 0, 3.4), 0.0], "iris": [Vector3(0.35, 0, 3.0), -10.0], "agnes": [Vector3(1.5, 0, 2.7), -20.0],
		"zoe": [Vector3(-1.8, 0, 2.6), 15.0], "danny": [Vector3(0.9, 0, 2.2), -15.0], "eva": [Vector3(2.4, 0, 2.0), -25.0]}
	for k in who:
		var sp: Array = spots[k]
		put(g, k, vn.to_global(sp[0]), float(sp[1]) + vn.rotation_degrees.y, "Idle")
	baby(g)
	(g.actors.hale as Actor).visible = true
	(g.actors.orderly as Actor).visible = true


static func _hale_glasses(g) -> void:
	if not has(g, "hale"):
		return
	var h: Actor = g.actors.hale
	h.look_target = g._target("noah")
	h.expr = {"mouthSmileLeft": 0.12, "mouthSmileRight": 0.1}


static func _diary(g, who: String) -> void:
	var old = g.c4.get("diary")
	if old and is_instance_valid(old):
		(old as Node).queue_free()
	if not has(g, who):
		return
	var a: Actor = g.actors[who]
	var ba := BoneAttachment3D.new()
	ba.bone_name = "hand_r"
	a.skel.add_child(ba)
	var bm := MeshInstance3D.new()
	var bx := BoxMesh.new()
	bx.size = Vector3(0.15, 0.21, 0.025)
	bm.mesh = bx
	var m := StandardMaterial3D.new()
	m.albedo_color = Color(0.3, 0.15, 0.1)
	m.roughness = 0.6
	bm.material_override = m
	bm.position = Vector3(0.0, 0.1, 0.05)
	ba.add_child(bm)
	g.c4["diary"] = ba
	if who == "hale":
		g.sfx.play("page_turn", -10.0)


static func _crowbar(g, show: bool) -> void:
	var old = g.c4.get("crowbar")
	if old and is_instance_valid(old):
		(old as Node).queue_free()
		g.c4.erase("crowbar")
	if not show or not has(g, "noah"):
		return
	var ba := BoneAttachment3D.new()
	ba.bone_name = "hand_r"
	(g.actors.noah as Actor).skel.add_child(ba)
	var m := MeshInstance3D.new()
	var cm := CylinderMesh.new()
	cm.top_radius = 0.014
	cm.bottom_radius = 0.014
	cm.height = 0.62
	m.mesh = cm
	var mat := StandardMaterial3D.new()
	mat.albedo_color = Color(0.12, 0.12, 0.13)
	mat.metallic = 0.7
	mat.roughness = 0.4
	m.material_override = mat
	m.position = Vector3(0.0, 0.2, 0.04)
	ba.add_child(m)
	g.c4["crowbar"] = ba


static func _syringe(g, show: bool) -> void:
	var old = g.c4.get("syringe")
	if old and is_instance_valid(old):
		if show:
			return
		var p: Vector3 = (old as Node3D).global_position
		(old as Node).queue_free()
		g.c4.erase("syringe")
		var sy := MeshInstance3D.new()   # шприц — в снегу
		var cm := CylinderMesh.new()
		cm.top_radius = 0.008
		cm.bottom_radius = 0.008
		cm.height = 0.12
		sy.mesh = cm
		g.scene.add_child(sy)
		sy.global_position = Vector3(p.x + 0.4, 0.03, p.z + 0.3)
		sy.rotation_degrees = Vector3(0, 30, 90)
		return
	if not show or not has(g, "hale"):
		return
	var ba := BoneAttachment3D.new()
	ba.bone_name = "hand_r"
	(g.actors.hale as Actor).skel.add_child(ba)
	var m := MeshInstance3D.new()
	var cm := CylinderMesh.new()
	cm.top_radius = 0.008
	cm.bottom_radius = 0.008
	cm.height = 0.12
	m.mesh = cm
	var mat := StandardMaterial3D.new()
	mat.albedo_color = Color(0.85, 0.9, 0.95, 0.7)
	mat.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
	m.material_override = mat
	m.position = Vector3(0.0, 0.08, 0.03)
	ba.add_child(m)
	(g.actors.hale as Actor).reach = {"_r": g._target("noah") + Vector3(0.0, -0.3, 0.0)}
	g.c4["syringe"] = ba


## Вспышка памяти: ночь, берег озера, фары седана — из щели сарая.
static func _flash(g, on_: bool) -> void:
	var s: DriveScene = g.scene
	if on_:
		var lk := lake(g)
		lk.summer(true)
		g.c4["day_before"] = String(g.c4.get("day", "sunny"))
		s.set_daylight("night")
		var fb := BlackCar.new()
		lk.add_child(fb)
		fb.position = Vector3(-15.5, 0.0, -2.0)   # на дороге у дачи, фарами к сараю и мосткам
		fb.rotation_degrees = Vector3(0, 200, 0)
		fb.lights(true)
		g.c4["flash_car"] = fb
		g.hud.fade.color = Color(1, 1, 1, 0.9)
		g.create_tween().tween_property(g.hud.fade, "color", Color(1, 1, 1, 0.0), 0.35)
		g.sfx.play("boom", -12.0)
	else:
		var fb = g.c4.get("flash_car")
		if fb and is_instance_valid(fb):
			(fb as Node).queue_free()
		g.c4.erase("flash_car")
		lake(g).summer(false)
		s.set_daylight(String(g.c4.get("day_before", "sunny")))
		g.hud.fade.color = Color(1, 1, 1, 0.8)
		g.create_tween().tween_property(g.hud.fade, "color", Color(1, 1, 1, 0.0), 0.3)


# ---------- лес ----------
static func _run_woods(g, from_hut := false) -> void:
	woods(g)
	var who := cast(g)
	var base := wpos(30.0, 0.0, 1.0) if not from_hut else wpos(3.0, 0.0, 6.0)
	var dest := wpos(14.0, 0.0, 1.0) if not from_hut else wpos(6.0, 0.0, 22.0)
	var i := 0
	for k in who:
		var off := Vector3(float(i % 3) * 0.9 - 0.9, 0.0, float(i / 3) * 1.1)
		var p := base + off
		var dv := dest - base
		put(g, k, p, rad_to_deg(atan2(dv.x, dv.z)), "Sprint" if k != "agnes" else "Jog_Fwd")
		i += 1
	baby(g)
	_flash_actor(g)


static func _woods_stage(g) -> void:
	_looks_off(g)
	var w := woods(g)
	day(g, "dusk")
	g._blizzard = false
	g.scene.env.fog_density = 0.024
	var who := cast(g)
	var i := 0
	for k in who:
		put(g, k, wpos(4.5 + float(i) * 0.8, 0.0, 2.0 + float(i % 2) * 0.7), -100.0, "Walk")
		i += 1
	baby(g)
	_flash_actor(g)
	w.visible = true


## Фонарь в лесу: санитар (или сам Хейл, если санитар убит) — луч бродит между елей.
static func _flash_actor(g) -> void:
	var hunter := "orderly" if not on(g, "orderly_dead") else "hale"
	var a := actor(g, hunter, hunter if ResourceLoader.exists("res://assets/chars/%s.glb" % hunter) else "gus")
	a.visible = true
	if not g.c4.has("torch"):
		var ba := BoneAttachment3D.new()
		ba.bone_name = "hand_r"
		a.skel.add_child(ba)
		var sl := SpotLight3D.new()
		sl.light_color = Color(0.92, 0.95, 1.0)
		sl.light_energy = 12.0
		sl.spot_range = 28.0
		sl.spot_angle = 16.0
		sl.spot_attenuation = 0.8
		sl.light_volumetric_fog_energy = 5.0   # в тумане избушки луч сквозь щели виден полосами
		sl.shadow_enabled = g.scene.pc
		ba.add_child(sl)
		sl.rotation_degrees = Vector3(-90, 0, 0)
		var body := MeshInstance3D.new()
		var cm := CylinderMesh.new()
		cm.top_radius = 0.025
		cm.bottom_radius = 0.02
		cm.height = 0.2
		body.mesh = cm
		var bm := StandardMaterial3D.new()
		bm.albedo_color = Color(0.1, 0.1, 0.11)
		body.material_override = bm
		ba.add_child(body)
		g.c4["torch"] = sl
		g.c4["hunter"] = hunter
	put(g, hunter, wpos(24.0, 0.0, -3.0), -80.0, "Idle_Torch")
	g.c4["beam_mode"] = "search"


static func _hut_in(g) -> void:
	var w := woods(g)
	var who := cast(g)
	var spots := {"noah": [Vector3(0.9, 0, 0.9), -120.0], "iris": [Vector3(-0.9, 0, -0.4), 70.0], "agnes": [Vector3(-1.1, 0, 0.8), 80.0],
		"zoe": [Vector3(0.3, 0, -1.2), 10.0], "danny": [Vector3(-0.4, 0, 0.3), 40.0], "eva": [Vector3(-1.2, 0, -1.2), 60.0]}
	for k in who:
		var sp: Array = spots[k]
		put(g, k, wpos(0, Woods.FLOOR, 0) + (sp[0] as Vector3), float(sp[1]), "Crouch_Idle" if k != "eva" else "Idle")
	if has(g, "eva") and (g.actors.eva as Actor).visible:
		Ch2.sit(g, "eva", wpos(-Woods.HW + 0.3, Woods.FLOOR + 0.48, -0.5), 90.0, "Rk_SitIdle")
	baby(g)
	var hunter := String(g.c4.get("hunter", "orderly"))
	if has(g, hunter):
		put(g, hunter, wpos(6.0, 0.0, -1.0), -90.0, "Idle_Torch")
	g.c4["beam_mode"] = "hut_sweep"
	w.visible = true


static func _zoe_grab(g) -> void:
	var hunter := "orderly" if not on(g, "orderly_dead") else "hale"
	put(g, "zoe", wpos(8.4, 0.0, 3.3), -100.0, "Idle")
	if has(g, hunter):
		put(g, hunter, wpos(9.0, 0.0, 3.5), -100.0, "Punch_Jab" if hunter == "orderly" else "Idle")
		(g.actors[hunter] as Actor).reach = {"_r": g._target("zoe") + Vector3(0.0, -0.35, 0.0), "_l": g._target("zoe") + Vector3(0.1, -0.15, 0.1)}
	put(g, "noah", wpos(6.2, 0.0, 2.2), 70.0, "Idle")
	(g.actors.zoe as Actor).expr = (g.EXPR["fear"] as Dictionary).duplicate()
	if hunter == "hale":
		_syringe(g, true)


static func _carry_agnes(g) -> void:
	if not (has(g, "noah") and has(g, "agnes")):
		return
	var n: Actor = g.actors.noah
	var a: Actor = g.actors.agnes
	# идёт через кадр (а не шагает на месте): из глубины леса к камере плана woods_carry
	put(g, "noah", wpos(5.3, 0.0, 12.4), 10.0, "Walk")
	g.c4["carry"] = piggyback(n, a)
	g._walk({"who": "noah", "to": _arr(wpos(5.95, 0.0, 16.4)), "speed": 0.65, "then": "Idle", "clip": "Walk"})


## Агнес на спине у Ноя (система скелета Ноя в покое: +X — его левая рука, +Z — вперёд). Привязана к его тазу — вес на
## пояснице, и его наклон вперёд её не опрокидывает. Середина её тазобедренных суставов — за его поясницей, чуть правее
## (голова — у его правого плеча, а не в затылке); бёдра горизонтально и разведены — обхватывают его пояс; корпус наклонён
## вперёд, к его спине. Числа подобраны на стенде (anim_lab --lab carry --piggy). Сильнее разводить бёдра нельзя: юбка Агнес
## привязана к бёдрам без своих костей и ткани — растягивается шатром (см. docs/CHARACTER_AUDIT.md).
const PIGGY_HIP := Vector3(-0.06, 1.08, -0.24)
const PIGGY_LEAN := 22.0
const PIGGY_SPREAD := 24.0


static func piggyback(n: Actor, a: Actor) -> BoneAttachment3D:
	var ba := BoneAttachment3D.new()
	ba.bone_name = "pelvis"
	n.skel.add_child(ba)
	a.reparent(ba, false)
	a.reach = {}
	Ch3.hold(a, "Sitting_Idle", 0.0)
	var ask := a.skel
	ask.force_update_all_bone_transforms()
	var sk_in := a.global_transform.affine_inverse() * ask.global_transform
	var bp := func(k: String) -> Vector3:
		return sk_in * ask.get_bone_global_pose(ask.find_bone(k)).origin
	var hip: Vector3 = ((bp.call("thigh_l") as Vector3) + bp.call("thigh_r")) * 0.5
	var knee: Vector3 = ((bp.call("calf_l") as Vector3) + bp.call("calf_r")) * 0.5
	var thigh0 := atan2(knee.y - hip.y, knee.z - hip.z)   # бёдра в позе клипа: + — колени выше таза
	var b := Basis(Vector3.RIGHT, deg_to_rad(PIGGY_LEAN))   # корпус вперёд — к его спине
	var nsk := n.skel
	a.transform = nsk.get_bone_global_rest(nsk.find_bone("pelvis")).affine_inverse() * Transform3D(b, PIGGY_HIP - b * hip)
	var gb := a.global_transform.basis.orthonormalized()
	# бёдра: поднять до горизонтали (наклон корпуса и поза клипа их опустили), потом развести — обхватить его пояс
	var lift := deg_to_rad(PIGGY_LEAN) - thigh0
	for side in ["_l", "_r"]:
		var bt := a._bi("thigh" + side)
		a._rot_now(bt, gb.x, -lift)
		a._rot_now(bt, gb.y, deg_to_rad(PIGGY_SPREAD) * (1.0 if side == "_l" else -1.0))
	ClothFit.ride(a, true)   # середина юбки — от таза, а не между разведённых колен
	n.set_meta("carrying", "back")
	a.placed()   # руки и взгляд — сразу, на склейке
	return ba


## Каждый кадр, пока Агнес на спине: кисти Ноя — под её бёдрами у колен (локти назад), её руки — через его плечи на грудь,
## голова — чуть в сторону, мимо его затылка.
static func piggy_update(n: Actor, a: Actor) -> void:
	var ask := a.skel
	var bp := func(k: String) -> Vector3:
		return ask.global_transform * ask.get_bone_global_pose(ask.find_bone(k)).origin
	var ab := a.global_transform.basis.orthonormalized()
	var nb := n.global_transform.basis.orthonormalized()
	var nr := {}
	var palm := {}
	for side in ["_l", "_r"]:
		var hp: Vector3 = bp.call("thigh" + side)
		var kn: Vector3 = bp.call("calf" + side)
		var th := (kn - hp).normalized()
		var under := (-ab.y - th * (-ab.y).dot(th)).normalized()
		nr[side] = hp.lerp(kn, 0.7) + under * 0.065
		palm[side] = -under
	n.reach = nr
	n.reach_palm = palm
	n.reach_pole = {"_l": -nb.z + nb.x * 0.6, "_r": -nb.z - nb.x * 0.6}
	var nsk := n.skel
	var chest := nsk.global_transform * nsk.get_bone_global_pose(nsk.find_bone("spine_03")).origin
	a.reach = {"_l": chest + nb.z * 0.15 + nb.x * 0.07 + nb.y * 0.1, "_r": chest + nb.z * 0.15 - nb.x * 0.07 + nb.y * 0.1}
	a.reach_pole = {"_l": nb.y + nb.x * 0.7, "_r": nb.y - nb.x * 0.7}
	a.reach_palm = {"_l": -nb.z, "_r": -nb.z}
	a.look_target = n.head_pos() + nb.z * 2.0 - nb.x * 1.2   # смотрит вперёд-вправо, мимо его головы
	n.set_meta("carrying", "back")


# ---------- озеро ----------
static func _lake_stage(g) -> void:
	Ch5.yard_props(g)   # качели и поленница у дачи — те же, что в главе 5
	_looks_off(g)
	var lk := lake(g)
	lk.summer(false)
	day(g, "lake_dawn")
	g._blizzard = false
	g.scene.env.fog_density = 0.005
	var ba = g.c4.get("carry")
	if ba and is_instance_valid(ba) and has(g, "agnes") and (g.actors.agnes as Actor).get_parent() == ba:
		(g.actors.agnes as Actor).reparent(g.scene, false)
	var who := cast(g)
	if on(g, "agnes_dead") and has(g, "agnes"):
		(g.actors.agnes as Actor).visible = false
		who.erase("agnes")
	var hunter := String(g.c4.get("hunter", ""))
	if hunter != "" and has(g, hunter):
		(g.actors[hunter] as Actor).visible = false
	var torch = g.c4.get("torch")
	if torch and is_instance_valid(torch):
		(torch as Node3D).visible = false
	var spots := {"noah": [Vector3(-13.6, 0, 37.0), 150.0], "iris": [Vector3(-12.6, 0, 37.6), 145.0], "agnes": [Vector3(-15.0, 0, 38.8), 150.0],
		"zoe": [Vector3(-11.6, 0, 38.8), 140.0], "danny": [Vector3(-12.2, 0, 39.2), 150.0], "eva": [Vector3(-14.6, 0, 40.0), 150.0]}
	for k in who:
		var sp: Array = spots[k]
		put(g, k, lpos(0, 0, 0) + (sp[0] as Vector3), float(sp[1]), "Idle" if k != "agnes" or not on(g, "agnes_limp") else "Idle_FoldArms")
	baby(g)


## Лёд перед прогулкой: пятна молодого (чёрного) льда — по зонам «провалился» из data/ch4.json (прогулка c4i), старая лунка
## с удочкой; Ной впереди, остальные — у кромки леса, за ним.
static func _ice_stage(g) -> void:
	var lk := lake(g)
	if not g.c4.has("black_ice"):
		var root := Node3D.new()
		lk.add_child(root)
		var gt := GradientTexture2D.new()   # мягкий край пятна
		gt.fill = GradientTexture2D.FILL_RADIAL
		gt.fill_from = Vector2(0.5, 0.5)
		gt.fill_to = Vector2(1.0, 0.5)
		var gr := Gradient.new()
		gr.set_color(0, Color(1, 1, 1, 1))
		gr.set_color(1, Color(1, 1, 1, 0))
		gr.add_point(0.7, Color(1, 1, 1, 0.92))
		gt.gradient = gr
		var m := StandardMaterial3D.new()
		m.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
		m.albedo_color = Color(0.05, 0.08, 0.11)
		m.albedo_texture = gt
		m.roughness = 0.06
		m.metallic_specular = 0.8
		var ex: Dictionary = g.data.explore.get("c4i", {})
		for z in ex.get("zones", []):
			var bs: Array = z.get("beats", [])
			if bs.is_empty() or String((bs[0] as Dictionary).get("fx", "")) != "ice_fall":
				continue
			var rc: Array = z.rect   # мир → местные координаты озера; пятно — на 0,6 м шире зоны провала
			var x0 := float(rc[0]) - LAKE.x - 0.6
			var z0 := float(rc[1]) - LAKE.z - 0.6
			var x1 := float(rc[2]) - LAKE.x + 0.6
			var z1 := float(rc[3]) - LAKE.z + 0.6
			var mi := MeshInstance3D.new()
			var pm := PlaneMesh.new()
			pm.size = Vector2(x1 - x0 + 1.2, z1 - z0 + 1.2)
			mi.mesh = pm
			mi.material_override = m
			mi.position = Vector3((x0 + x1) / 2.0, Lake.ICE_Y + 0.008, (z0 + z1) / 2.0)
			root.add_child(mi)
		var dark := StandardMaterial3D.new()   # лунка: тёмный кружок, удочка вмёрзла рядом
		dark.albedo_color = Color(0.08, 0.1, 0.12)
		dark.roughness = 0.2
		var hole := MeshInstance3D.new()
		var cm := CylinderMesh.new()
		cm.top_radius = 0.16
		cm.bottom_radius = 0.16
		cm.height = 0.01
		hole.mesh = cm
		hole.material_override = dark
		hole.position = Vector3(-5.2, Lake.ICE_Y + 0.01, 66.5)
		root.add_child(hole)
		var rod := MeshInstance3D.new()
		var rm := CylinderMesh.new()
		rm.top_radius = 0.008
		rm.bottom_radius = 0.012
		rm.height = 0.9
		rod.mesh = rm
		var rmat := StandardMaterial3D.new()
		rmat.albedo_color = Color(0.25, 0.18, 0.1)
		rod.material_override = rmat
		rod.position = Vector3(-4.9, Lake.ICE_Y + 0.05, 66.2)
		rod.rotation_degrees = Vector3(0, 30, 84)
		root.add_child(rod)
		g.c4["black_ice"] = root
	var who := cast(g)
	if on(g, "agnes_dead"):
		who.erase("agnes")
	var spots := {"iris": [-5.4, 76.4], "agnes": [-7.0, 77.6], "zoe": [-4.4, 77.2], "danny": [-6.2, 77.0], "eva": [-7.3, 78.4]}
	for k in who:
		if k == "noah":
			continue
		var sp: Array = spots.get(k, [-6.0, 77.0])
		put(g, k, lpos(float(sp[0]), Lake.ICE_Y, float(sp[1])), 180.0, "Idle_FoldArms" if k == "agnes" else "Idle")
	put(g, "noah", lpos(-6.5, Lake.ICE_Y, 74.0), 180.0, "Idle")
	baby(g)


## Провалился: тёмная вода в проломе льда — там, где стоял.
static func _ice_hole(g, at: Vector3) -> void:
	var lk := lake(g)
	var w := MeshInstance3D.new()
	var cm := CylinderMesh.new()
	cm.top_radius = 0.75
	cm.bottom_radius = 0.75
	cm.height = 0.02
	w.mesh = cm
	var m := StandardMaterial3D.new()
	m.albedo_color = Color(0.03, 0.05, 0.07)
	m.roughness = 0.02
	m.metallic_specular = 1.0
	w.material_override = m
	lk.add_child(w)
	w.global_position = Vector3(at.x, Lake.ICE_Y + 0.012, at.z)


## Перешли по льду: все у мостков, напротив лодочного сарая (щель в его стене — к мосткам).
static func _ice_done(g) -> void:
	var who := cast(g)
	if on(g, "agnes_dead"):
		who.erase("agnes")
	var spots := {"noah": [-9.5, 8.6, 0.0], "iris": [-8.6, 8.2, -20.0], "agnes": [-11.0, 8.0, 20.0], "zoe": [-8.3, 9.6, -40.0],
		"danny": [-9.9, 7.8, 10.0], "eva": [-11.4, 9.2, 30.0]}
	for k in who:
		var sp: Array = spots.get(k, [-10.0, 8.4, 0.0])
		put(g, k, lpos(float(sp[0]), 0.0, float(sp[1])), float(sp[2]), "Idle" if k != "agnes" or not on(g, "agnes_limp") else "Idle_FoldArms")
	baby(g)


static func _kid(g, show: bool) -> void:
	var kid := actor(g, "noah_kid", "noah_kid" if ResourceLoader.exists("res://assets/chars/noah_kid.glb") else "danny")
	kid.visible = show
	var lk := lake(g)
	var fill := lk.get_node_or_null("kid_fill") as OmniLight3D
	if fill == null:   # дневной свет сквозь щель — на лицо, без теней: при чётких тенях («Ультра») доски клали на рот чёрную полосу
		fill = OmniLight3D.new()
		fill.name = "kid_fill"
		fill.light_color = Color(0.86, 0.9, 1.0)
		fill.light_energy = 0.55
		fill.omni_range = 1.3
		fill.shadow_enabled = false
		lk.add_child(fill)
		fill.global_position = lpos(GAP.x, GAP.y + 0.05, GAP.z - 0.2)
	fill.visible = show
	if show:
		put(g, "noah_kid", lpos(GAP.x, 0.28, GAP.z + 0.45), 180.0, "Idle")
		g.c4["kid_align"] = 6   # несколько кадров: глаза — точно в щель
		kid.look_target = lpos(GAP.x, GAP.y, GAP.z - 3.0)


static func _far_sedan(g) -> void:
	var fb := BlackCar.new()
	lake(g).add_child(fb)
	fb.position = Vector3(Lake.FAR + 1.6, 0.3, 30.0)   # по самому берегу, перед елями (иначе машину прячут деревья)
	fb.rotation_degrees = Vector3(0, 0, 0)   # едет вдоль дальнего берега (носом к −z)
	fb.speed = 2.2
	fb.lights(true)
	for h in fb.halos:   # издалека — два огня: ярче неба (HDR) и сквозь утренний туман, иначе тонут в белом
		h.scale = Vector3(4.0, 4.0, 4.0)
		var hm := (h.material_override as StandardMaterial3D).duplicate() as StandardMaterial3D
		hm.albedo_color = Color(5.0, 4.4, 3.4, 0.9)
		hm.disable_fog = true
		h.material_override = hm
	g.c4["far_car"] = fb


# ---------- каждый кадр ----------
static func process(g, delta: float) -> void:
	var c4: Dictionary = g.c4
	var cb = c4.get("carry")   # Агнес на спине у Ноя: руки и взгляд — каждый кадр (они идут)
	if cb and is_instance_valid(cb) and has(g, "noah") and has(g, "agnes") and (g.actors.agnes as Actor).get_parent() == cb:
		piggy_update(g.actors.noah, g.actors.agnes)
	elif has(g, "noah") and String((g.actors.noah as Actor).get_meta("carrying", "")) == "back":
		(g.actors.noah as Actor).remove_meta("carrying")
		(g.actors.noah as Actor).reach = {}
		if has(g, "agnes"):
			(g.actors.agnes as Actor).reach = {}
			(g.actors.agnes as Actor).look_target = null
			ClothFit.ride(g.actors.agnes, false)
	var st = c4.get("steam")   # пар из-под буханки — слышно рядом
	g.sfx.bed("steam", -10.0 if (st and is_instance_valid(st) and (st as CPUParticles3D).emitting) else Sfx.OFF)
	var bk = c4.get("book")
	if bk and is_instance_valid(bk):   # блокнот — между ладонями того, кто держит
		var who := String(c4.get("book_who", "iris"))
		if has(g, who):
			var a: Actor = g.actors[who]
			var sk := a.skel
			var hl := (sk.global_transform * sk.get_bone_global_pose(sk.find_bone("hand_l"))).origin
			var hr := (sk.global_transform * sk.get_bone_global_pose(sk.find_bone("hand_r"))).origin
			var fwd := a.global_transform.basis.z
			fwd = Vector3(fwd.x, 0.0, fwd.z).normalized()
			var right := fwd.cross(Vector3.UP).normalized()
			var n := (Vector3.UP * 0.8 - fwd * 0.6).normalized()
			(bk as Node3D).global_transform = Transform3D(Basis(right, n, right.cross(n)), (hl + hr) * 0.5 + fwd * 0.06 + n * 0.05)
	var rk = c4.get("rock")
	if rk != null and has(g, String(rk)):   # укачивают: корпус в такт нажатиям
		var a: Actor = g.actors[String(rk)]
		var ph := float(g._qte_got) * PI + float(g._t) * 1.2
		a.rotation.z = sin(ph) * 0.035
	var torch = c4.get("torch")
	if torch and is_instance_valid(torch):   # луч фонаря: ищет, проходит мимо, упирается в избушку
		var sl := torch as SpotLight3D
		var t := float(g._t)
		var mode := String(c4.get("beam_mode", "search"))
		sl.visible = mode != "off"
		var hunter := String(c4.get("hunter", "orderly"))
		if has(g, hunter):
			var h: Actor = g.actors[hunter]
			var target := wpos(0, 1.0, 0)
			match mode:
				"search":
					target = h.global_position + Vector3(sin(t * 0.6) * 9.0 - 6.0, 0.4, cos(t * 0.45) * 8.0)
				"hut_sweep":
					target = wpos(1.7, 1.2 + sin(t * 1.3) * 0.3, sin(t * 0.7) * 1.6)
				"pass":
					target = wpos(6.0 + t * 0.0, 1.0, -8.0 + fmod(t * 2.0, 6.0))
				"hut":
					target = wpos(1.7, 1.1, 0.2)
			var p: Vector3 = sl.global_position
			if (target - p).length() > 0.1:
				sl.global_transform = Transform3D(Basis.looking_at(target - p, Vector3.UP), p)
			var wd = c4.get("woods")
			if wd and is_instance_valid(wd):   # полосы света сквозь щели — там, где луч упёрся в стену
				var tz := 99.0
				if mode in ["hut_sweep", "hut"] and sl.visible:
					tz = target.z - WOODS.z
				(wd as Woods).beam_at(tz, t)
	var ka = c4.get("kid_align")
	if ka != null and int(ka) > 0 and has(g, "noah_kid"):   # глаза мальчика — точно в щель
		var kid: Actor = g.actors.noah_kid
		var hp: Vector3 = g._target("noah_kid") - Vector3(0, 0.03, 0)
		var want := lpos(GAP.x, GAP.y, GAP.z + 0.12)
		kid.global_position += Vector3(want.x - hp.x, want.y - hp.y, 0.0)
		c4["kid_align"] = int(ka) - 1


# ---------- планы ----------
static func cam(g, shot: String) -> bool:
	if not String(g._scene_id).begins_with("c4"):
		return false
	var c: Camera3D = g.cam
	var vn = g.c4.get("van")
	match shot:
		"yard":
			c.fov = 42.0
			c.look_at_from_position(Vector3(5.0, 1.9, -31.2), Vector3(-1.5, 1.0, -39.5))
		"car_seat":
			var car: Car = g.scene.car
			c.fov = 34.0
			c.look_at_from_position(car.to_global(Vector3(0.4, 1.12, 0.12)), car.to_global(Vector3(-0.36, 0.55, 0.28)))
		"hale_note":
			var p = g.c4.get("note_paper")
			if not (p and is_instance_valid(p)):
				return false
			var t: Transform3D = (p as Node3D).global_transform
			c.fov = 30.0
			c.look_at_from_position(t.origin + Vector3(0.0, 0.3, 0.01), t.origin, -t.basis.z)
		"yard_drift":
			c.fov = 40.0
			c.look_at_from_position(Vector3(2.4, 1.3, -42.0), Vector3(5.1, 0.3, -38.4))
		"shed":
			c.fov = 44.0
			c.look_at_from_position(VAN_YARD + Vector3(8.0, 2.0, -5.5), VAN_YARD + Vector3(1.0, 1.0, 0.0))
		"van_engine":
			c.fov = 40.0
			c.look_at_from_position(VAN_YARD + Vector3(4.6, 1.3, 1.8), VAN_YARD + Vector3(2.4, 0.8, 0.1))
		"van_push":
			if not vn:
				return false
			c.fov = 46.0
			c.look_at_from_position((vn as Van).to_global(Vector3(-2.2, 1.6, 7.2)), (vn as Van).to_global(Vector3(0.0, 1.0, 1.0)))
		"van_smoke":
			if not vn:
				return false
			c.fov = 44.0
			c.look_at_from_position((vn as Van).to_global(Vector3(-3.2, 1.0, 5.6)), (vn as Van).to_global(Vector3(-0.5, 0.7, 2.4)))
		"van_road":
			if not vn:
				return false
			c.fov = 40.0
			c.look_at_from_position((vn as Van).to_global(Vector3(-3.4, 1.5, -10.5)), (vn as Van).to_global(Vector3(0.0, 1.0, -0.5)))
		"van_inside":
			if not vn:
				return false
			c.fov = 62.0
			c.look_at_from_position((vn as Van).to_global(Vector3(0.0, 1.6, 2.0)), (vn as Van).to_global(Vector3(0.0, 1.2, -1.0)))
		"van_mirror":   # глазами Ноя — в левое зеркало: позади седан
			if not vn or not has(g, "noah"):
				return false
			c.fov = 18.0
			c.look_at_from_position((vn as Van).to_global(Vector3(-0.74, 1.66, -1.42)), (vn as Van).to_global(Vector3(-1.141, 1.6, -1.84)))
		"noah_van":
			return face(g, "noah", 28.0, 0.62, 0.03, 44.0)
		"zoe_van":   # Зоя — справа, лицом к проходу: камера из прохода (прежний план «в лицо» вставал в голову Евы на лавке напротив)
			if not vn or not has(g, "zoe") or not (g.actors.zoe as Actor).visible:
				return false
			c.fov = 42.0
			c.look_at_from_position((vn as Van).to_global(Vector3(-0.05, 1.42, 0.05)), g._target("zoe") + Vector3(0, -0.04, 0))
		"iris_van", "danny_van", "agnes_van", "iris_awake":
			return face(g, shot.get_slice("_", 0), 22.0, 0.95, 0.02, 40.0)
		"iris_asleep":
			if not vn or not has(g, "iris"):
				return false
			c.fov = 40.0
			c.look_at_from_position((vn as Van).to_global(Vector3(0.25, 1.5, -0.05)), g._target("iris") + Vector3(0, -0.12, 0))
		"noah_door":
			if not has(g, "noah") or not vn:
				return false
			c.fov = 40.0
			c.look_at_from_position((vn as Van).to_global(Vector3(-0.35, 1.45, -0.55)), g._target("noah"))
		"bus_stop":
			var b = g.c4.get("stop")
			if not (b and is_instance_valid(b)) or not vn:
				return false
			c.fov = 42.0   # спереди-слева: буханка подъезжает и встаёт, павильон выплывает справа и встаёт рядом
			c.look_at_from_position((vn as Van).to_global(Vector3(-5.5, 1.7, -9.5)), (vn as Van).to_global(Vector3(2.0, 1.0, -0.8)))
		"sana_turn":   # из кабины через лобовое: указатель у поворота в лес
			var ts = g.c4.get("turn_sign")
			if not (ts and is_instance_valid(ts)) or not vn:
				return false
			c.fov = 38.0
			c.look_at_from_position((vn as Van).to_global(Vector3(-0.15, 1.72, -0.75)), (ts as Node3D).global_position + Vector3(0.0, 1.6, 0.0))
		"sana_gate":   # слева-сзади от буханки: ворота, вывеска, белый корпус за ними
			c.fov = 46.0
			c.look_at_from_position(spos(-5.6, 1.7, 18.6), spos(0.6, 2.4, -1.5))
		"sana_gap":   # со двора: Ной боком в щель между створками
			c.fov = 42.0
			c.look_at_from_position(spos(2.6, 1.55, 6.4), spos(0.3, 1.15, 10.0))
		"sana_window":   # окно пристройки: доска на одном гвозде
			c.fov = 40.0
			c.look_at_from_position(spos(9.2, 1.75, 1.6), spos(12.0, 1.35, -1.0))
		"sana_inside":   # архив изнутри: полоса света из окна, стеллажи
			c.fov = 52.0
			c.look_at_from_position(spos(18.9, 2.25, -0.2), spos(12.8, 1.0, -1.9))
		"sana_back":   # у ворот: Ной и Айрис друг против друга; слева — створка, за ними — двор
			c.fov = 40.0
			c.look_at_from_position(spos(-2.4, 1.6, 12.9), spos(0.3, 1.35, 11.0))
		"hose":
			if not vn:
				return false
			c.fov = 40.0
			c.look_at_from_position((vn as Van).to_global(Vector3(1.0, 1.05, -3.7)), (vn as Van).to_global(Vector3(0.15, 0.8, -2.4)))
		"iris_stop", "noah_stop", "noah_engine", "danny_stop", "noah_yard", "iris_yard", "zoe_yard", "agnes_shed":
			return face(g, shot.get_slice("_", 0), 24.0, 1.0, 0.02, 38.0)
		"drawing":   # страница блокнота — во весь кадр, глазами того, кто держит (камера не в голове и не в куртке)
			var bk = g.c4.get("book")
			if not (bk and is_instance_valid(bk)):
				return false
			var t: Transform3D = (bk as Node3D).global_transform
			var p := t.origin + t.basis.y * 0.42
			var who := String(g.c4.get("book_who", "iris"))
			if has(g, who):
				var v: Vector3 = g._target(who) - t.origin
				if v.dot(t.basis.y) > 0.05:   # лицо над страницей: камера между глазами и блокнотом
					p = t.origin + v * 0.7
			var dist := maxf((p - t.origin).length(), 0.05)
			c.fov = clampf(rad_to_deg(2.0 * atan(0.125 / dist)), 18.0, 60.0)
			c.look_at_from_position(p, t.origin, -t.basis.z)
		"hit_two":   # Айрис и Хейл сбоку: удар по руке со шприцем
			if not has(g, "iris") or not has(g, "hale") or not vn:
				return false
			var ih: Vector3 = g._target("iris")
			var hh: Vector3 = g._target("hale")
			var mid := (ih + hh) * 0.5 + Vector3(0, -0.2, 0)
			c.fov = 42.0
			c.look_at_from_position((vn as Van).to_global(Vector3(2.9, 1.5, 4.4)), mid)
		"hug":
			if not has(g, "iris"):
				return false
			var ih: Vector3 = g._target("iris")
			var nh: Vector3 = g._target("noah")
			var mid := (ih + nh) * 0.5
			var side := (nh - ih).cross(Vector3.UP).normalized()
			if (g.cam.global_position - mid).dot(side) < 0.0:
				side = -side
			c.fov = 40.0
			c.look_at_from_position(mid + side * 1.3 + Vector3(0, 0.05, 0), mid + Vector3(0, -0.08, 0))
		"pine_road":
			var p = g.c4.get("pine")
			if not (p and is_instance_valid(p)):
				return false
			c.fov = 44.0
			c.look_at_from_position((p as Node3D).global_position + Vector3(7.5, 1.8, -5.0), (p as Node3D).global_position + Vector3(0.0, 0.8, 8.0))
		"pine_cut":
			var p = g.c4.get("pine")
			if not (p and is_instance_valid(p)):
				return false
			var sp: Vector3 = (p as Node3D).global_position + Vector3(6.1, 0.58, 0.6)
			c.fov = 36.0
			c.look_at_from_position(sp + Vector3(0.8, 0.7, 0.5), sp)
		"orderly":   # снизу: огромный
			if not has(g, "orderly"):
				return false
			var oh: Vector3 = g._target("orderly")
			var of := (g.actors.orderly as Actor).global_transform.basis.z
			c.fov = 40.0
			c.look_at_from_position(oh + Vector3(of.x, 0.0, of.z).normalized().rotated(Vector3.UP, 0.4) * 2.1 + Vector3(0, -0.45, 0), oh + Vector3(0, -0.12, 0))
		"hale":
			return face(g, "hale", 18.0, 0.95, 0.02, 34.0)
		"hale_wide":
			if not vn or not has(g, "hale"):
				return false
			c.fov = 46.0
			c.look_at_from_position((vn as Van).to_global(Vector3(-4.6, 1.7, 3.8)), (vn as Van).to_global(Vector3(0.4, 1.2, 6.8)))
		"agnes_pine", "iris_pine", "noah_pine":
			return face(g, shot.get_slice("_", 0), 26.0, 1.0, 0.02, 38.0)
		"flash_shore":
			c.fov = 40.0
			c.look_at_from_position(lpos(GAP.x, GAP.y, GAP.z - 0.03), lpos(-15.5, 0.8, -2.0))   # от щели наружу; доски по краям — маска hud.peek_on
		"fight", "fight_after":
			if not vn:
				return false
			c.fov = 44.0
			c.look_at_from_position((vn as Van).to_global(Vector3(2.4, 1.4, 3.6)), (vn as Van).to_global(Vector3(-1.0, 0.8 if shot == "fight" else 0.4, 5.0)))
		"knuckles4":
			var n: Actor = g.actors.noah
			var hp: Vector3 = (n.skel.global_transform * n.skel.get_bone_global_pose(n.skel.find_bone("hand_r"))).origin
			c.fov = 34.0
			c.look_at_from_position(hp + Vector3(0.35, 0.25, 0.35), hp)
		"woods_run":
			c.fov = 50.0
			c.look_at_from_position(wpos(36.0, 1.5, 2.4), wpos(16.0, 1.0, 0.5))
		"woods_back":
			c.fov = 46.0
			c.look_at_from_position(wpos(8.0, 1.6, 2.0), wpos(26.0, 1.2, -2.0))
		"woods":
			c.fov = 46.0
			c.look_at_from_position(wpos(13.0, 1.9, 7.0), wpos(1.0, 1.0, 0.5))
		"hut":
			c.fov = 66.0
			c.look_at_from_position(wpos(1.25, Woods.FLOOR + 1.45, -1.55), wpos(-0.6, Woods.FLOOR + 0.55, 0.6))
		"hut_gap":
			c.fov = 46.0
			c.look_at_from_position(wpos(-0.4, Woods.FLOOR + 1.1, -0.2), wpos(Woods.HW, Woods.FLOOR + 1.15, 0.3))
		"baby_hut":   # снизу-спереди: лицо того, кто укачивает (он смотрит вниз), и свёрток в кадре
			var who := baby_holder(g)
			if not has(g, who) or not (g.actors[who] as Actor).visible:
				return false
			var ba: Actor = g.actors[who]
			var bh: Vector3 = g._target(who)
			var bf := ba.global_transform.basis.z
			bf.y = 0.0
			bf = bf.normalized().rotated(Vector3.UP, deg_to_rad(16.0))
			c.fov = 42.0
			c.look_at_from_position(bh + bf * 0.8 + Vector3(0, -0.34, 0), bh + Vector3(0, -0.2, 0))
		"zoe_trail", "zoe_after":
			c.fov = 42.0
			c.look_at_from_position(wpos(6.0, 1.5, 6.4), wpos(8.8, 1.0, 3.2))
		"noah_woods", "iris_woods", "danny_woods", "agnes_snow":
			return face(g, shot.get_slice("_", 0), 20.0, 1.0, 0.04, 38.0)
		"woods_dark":
			c.fov = 46.0
			c.look_at_from_position(wpos(6.0, 1.4, 20.0), wpos(12.0, 1.2, 4.0))
		"woods_carry":
			c.fov = 44.0
			c.look_at_from_position(wpos(8.4, 1.5, 19.5), wpos(5.6, 1.1, 15.0))
		"lake_wide":
			c.fov = 50.0
			c.look_at_from_position(lpos(-17.0, 1.8, 46.0), lpos(12.0, 0.0, 2.0))
		"dacha":
			c.fov = 42.0
			c.look_at_from_position(lpos(-1.5, 1.8, -7.5), lpos(-20.0, 1.6, 0.0))
		"iris_lake":
			return face(g, "iris", 22.0, 1.0, 0.03, 38.0)
		"boathouse":
			c.fov = 40.0
			c.look_at_from_position(lpos(-5.5, 1.35, 4.0), lpos(-10.0, 1.2, 10.9))
		"gap_eyes":
			c.fov = 30.0
			c.look_at_from_position(lpos(GAP.x + 0.05, GAP.y + 0.02, GAP.z - 0.45), lpos(GAP.x, GAP.y, GAP.z))
		"lake_far":
			c.fov = 14.0
			c.look_at_from_position(lpos(-11.0, 1.7, 35.0), lpos(Lake.FAR + 1.6, 0.9, 27.0))
		_:
			return false
	return true


static func face(g, who: String, ang: float, dist: float, dy: float, fov: float) -> bool:
	if not has(g, who) or not (g.actors[who] as Actor).visible:
		return false
	return Ch3.face(g, who, ang, dist, dy, fov)
