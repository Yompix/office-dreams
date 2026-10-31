class_name Ch2
extends RefCounted
## Глава 2 «Попутчица»: события сценария (fx) и планы, которые зависят от того, где сейчас герои.
## Сценарий — data/ch2.json; здесь — постановка: серое утро, отражение в зеркале («Тень»), Зоя на обочине,
## пост полиции, кафе «У трёх сосен» (drive_scene.park_at_cafe), туалет с зеркалом, крыльцо, чёрный седан,
## ночь у кафе, Ева, буран, сложившаяся фура, фары позади.
## Состояние главы — в g.c2 (словарь в game.gd).

const CF := DriveScene.CF


## Актёр по ключу: если ещё нет — создаём (модель героя или массовки).
static func actor(g, key: String, model := "") -> Actor:
	if g.actors.has(key):
		return g.actors[key]
	var a := Actor.make(key, model if model != "" else key)
	g.scene.add_child(a)
	g.actors[key] = a
	return a


static func drop(g, key: String) -> void:
	if g.actors.has(key):
		var a = g.actors[key]
		if is_instance_valid(a):
			(a as Actor).queue_free()
		g.actors.erase(key)


## Оставить героя на дороге: он уезжает назад вместе с ней (и пропадает далеко позади); режиссёр его больше не трогает.
static func leave_behind(g, key: String) -> void:
	var a = g.actors.get(key)
	if not is_instance_valid(a):
		g.actors.erase(key)
		return
	g._walks.erase(key)
	var t: Transform3D = (a as Actor).global_transform
	(a as Actor).reparent(g.scene.road_items, false)
	(a as Actor).global_transform = t
	g.actors.erase(key)


## Идёт глава 2 (сцены c2_*): планы героев в машине ставятся по месту, где кто сидит.
static func active(g) -> bool:
	return String(g._scene_id).begins_with("c2")


static func on(g, f: String) -> bool:
	return float(g.flags.get(f, 0)) > 0.0


## Кто где сидит в машине (глава 2): водитель Ной; спереди Айрис или Зоя; сзади — до троих.
static func seat_all(g) -> void:
	var back := []
	if on(g, "danny"):
		if not g.actors.has("danny"):
			g._attach_walkie(actor(g, "danny"))
		back.append("danny")
	var front := "iris"
	if on(g, "zoe") and not on(g, "zoe_gone"):
		actor(g, "zoe")
		if on(g, "zoe_front"):
			front = "zoe"
			back.append("iris")
		else:
			back.append("zoe")
	else:
		drop(g, "zoe")
	if on(g, "eva"):
		actor(g, "eva")
		back.append("eva")
	var slots: Array = {1: ["back"], 2: ["back", "back_l"], 3: ["back", "back_m", "back_l"]}.get(back.size(), [])
	g.scene.car.back_fill.visible = not back.is_empty()
	g.scene.car.notebook.visible = front == "iris"   # блокнот Айрис едет с ней
	g._place({"who": "noah", "car": "driver"})
	g._place({"who": front, "car": "passenger"})
	for i in back.size():
		g._place({"who": back[i], "car": slots[i]})
	for k in g.actors:
		(g.actors[k] as Actor).look_target = null
		(g.actors[k] as Actor).reach = {}


## Продолжить с сохранения в главе 2.
static func restore(g, id: String) -> void:
	if id == "c2_s4":
		return   # кафе ставит сценарий (fx "cafe")
	if id.begins_with("c2_s4_") or id.begins_with("c2_s5") or id in ["c2_s6", "c2_s6_eva", "c2_s6_alone"]:
		cafe(g)   # продолжили в кафе: стоянка, герои за столом (дальше сцена ставит сама)
		return
	g.scene.set_daylight("dawn")
	seat_all(g)


static func fx(g, k: String) -> bool:
	var s: DriveScene = g.scene
	var c2: Dictionary = g.c2
	match k:
		"ch2_start":   # утро: серо, снег выше машины; Айрис и Дэнни спят
			s.set_daylight("dawn")
			s.road.speed = 22.0
			s.car.speed = 22.0
			seat_all(g)
			g._expr({"who": "iris", "set": "sleep"})
			(g.actors.iris as Actor).look_target = g._target("window")
			if g.actors.has("danny"):
				g._expr({"who": "danny", "set": "sleep"})
		"reflection":   # отражение Ноя в зеркале: при «Тени» оно ведёт себя не так, как он
			reflection(g, float(g.flags.get("shadow", 0)))
		"reflection_off":
			reflection(g, 0.0)
		"zoe_road":   # Зоя на правой обочине впереди: лицом к машине, машет обеими руками; Ной сбрасывает газ
			g._expr({"who": "iris", "set": "none"})
			var z := actor(g, "zoe")
			z.reparent(s.road_items, false)
			z.position = Vector3(DriveScene.ROAD_EDGE + 0.6, 0.0, -42.0)
			z.rotation = Vector3.ZERO
			z.play("Idle", 0.0)
			z.wave = 1.0
			z.breeze = 1.0
			c2["zoe_bracelet"] = false
			# к ~16 м от неё — шагом (пока Ной решает, она остаётся впереди)
			var tw: Tween = g.create_tween().set_parallel()
			tw.tween_property(s.road, "speed", 1.5, 2.2)
			tw.tween_property(s.car, "speed", 1.5, 2.2)
		"zoe_pass":   # газ: она остаётся позади и показывает вслед средний палец
			var z: Actor = g.actors.get("zoe")
			if z:
				z.wave = 0.0
				z.play("Idle_No", 0.2)
				leave_behind(g, "zoe")
			drive(g, 22.0, 3.0)
		"car_stop":   # Ной тормозит метрах в шести от неё; как машина встала — она бежит к окну
			var z: Actor = g.actors.get("zoe")
			var d := 20.0 if z == null else maxf(0.0, -6.0 - z.global_position.z)
			var t := stop_in(g, d)
			g.sfx.play("brake_squeal", -12.0, 1.1, maxf(0.0, t - 1.4))
			if z:
				z.wave = 0.0
				z.play("Yes", 0.2)
				g.get_tree().create_timer(t * 0.9).timeout.connect(func():
					if g.actors.get("zoe") == z and is_instance_valid(z) and not g._walks.has("zoe") and z.get_parent() == s.road_items:
						_zoe_run(g))
		"zoe_window":   # подбежала к окну пассажира
			actor(g, "zoe")
			if not g._walks.has("zoe") and g.actors.zoe.get_parent() == s.road_items:
				_zoe_run(g)
			(g.actors.iris as Actor).look_target = g._target("zoe")
		"zoe_back", "zoe_front":
			g.sfx.play("car_door", -6.0)
			seat_all(g)
		"radio_music":   # Зоя переключает радио на музыку и подпевает
			g._radio = true
			c2["song"] = true
			g.sfx.play("radio_on", -8.0)
			s.car.radio_display("FM 99.1")
		"danny_smile":
			g._expr({"who": "danny", "set": "smile"})
		"bracelet":   # больничный браслет на запястье Зои
			bracelet(g)
		"post":   # пост полиции впереди: две патрульные машины с мигалками, очередь из трёх машин
			post(g)
		"radio_cop":
			g.sfx.play("static_burst", -18.0, 0.8)
		"cop_window":   # очередь прошла: полицейский у окна водителя
			cop_window(g)
		"post_pass":
			if g.actors.has("cop"):
				(g.actors.cop as Actor).look_target = null
				(g.actors.cop as Actor).play("Idle", 0.3)
				leave_behind(g, "cop")
			(g.actors.noah as Actor).look_target = null
			drive(g, 22.0, 5.0)
		"flee":   # бегство: газ в пол, занос, снег из-под колёс
			g._shake = 1.1
			g._jolt(0.6)
			g.sfx.play("skid", -4.0)
			g.sfx.play("engine_start", -6.0, 1.3)
			if g.actors.has("cop"):
				(g.actors.cop as Actor).play("Idle_No", 0.0)
				(g.actors.cop as Actor).look_target = null
				leave_behind(g, "cop")
			(g.actors.noah as Actor).look_target = null
			var tw: Tween = g.create_tween()
			tw.tween_property(s.car, "steer", 0.7, 0.3)
			tw.tween_property(s.car, "steer", -0.5, 0.35)
			tw.tween_property(s.car, "steer", 0.0, 0.5)
			drive(g, 28.0, 3.0)
		"siren":
			g.sfx.play("siren", -6.0)
		"cafe":
			cafe(g)
		"zoe_cafe":   # Зоя за стойкой: её подвёз дальнобойщик
			var z := actor(g, "zoe")
			sit(g, "zoe", Vector3(30.12, CF + 0.72, -5.4), -90.0, "Rk_SitIdle")
		"eva_corner":
			g._expr({"who": "eva", "set": "sad"})
		"eva_car":
			pass
		"iris_draw":
			(g.actors.iris as Actor).look_target = g._target("eva")
		"iris_eva":   # Айрис пересаживается к Еве
			sit(g, "iris", Vector3(23.5, CF + 0.42, -14.18), 0.0, "Rk_SitIdle")
			(g.actors.iris as Actor).look_target = g._target("eva")
			(g.actors.eva as Actor).look_target = g._target("iris")
		"danny_fries":
			(g.actors.danny as Actor).look_target = g._target("noah")
		"wc":   # туалет: Ной у раковины, вода розовая
			wc(g)
		"reflection_wc":
			wc_mirror(g, true)
			if float(g.flags.get("shadow", 0)) >= 1.0:
				reflection(g, float(g.flags.get("shadow", 0)))
		"pills2":
			pill_bottle(g, true)
		"wc_boy":   # в зеркале — мокрый восьмилетний мальчик за плечом Ноя
			boy(g, true)
			g.sfx.play("hum_ring", -14.0, 0.8)
		"wc_boy_off":
			boy(g, false)
		"flush":
			pill_bottle(g, false)
			g.sfx.play("splash", -12.0, 1.4)
		"zoe_porch":   # Зоя курит на крыльце, облокотившись на перила
			pill_bottle(g, false)
			reflection(g, 0.0)
			wc_mirror(g, false)
			g._place({"who": "zoe", "at": [20.2, CF, -9.15], "face": -60, "clip": "Idle_Rail"})
			cigarette(g, true)
			sit(g, "noah", Vector3(22.9, CF + 0.42, -3.38), 0.0, "Rk_SitIdle")
		"noah_porch":
			g._place({"who": "noah", "at": [20.85, CF, -8.25], "face": rad_to_deg(atan2(20.2 - 20.85, -9.15 + 8.25)), "clip": "Idle"})
			(g.actors.noah as Actor).look_target = g._target("zoe")
			(g.actors.zoe as Actor).look_target = g._target("noah")
		"hold_hand":   # он берёт её за руку: её левая и его правая встречаются между ними
			var z: Actor = g.actors.zoe
			var n: Actor = g.actors.noah
			z.play("Idle", 0.3)
			var mid := (z.global_position + n.global_position) * 0.5 + Vector3(0, 0.95, 0)
			n.reach = {"_r": mid}
			z.reach = {"_l": mid}
		"sedan_arrive":   # чёрный седан в конце парковки: фары, мотор работает
			cigarette(g, false)
			if g.actors.has("zoe") and not on(g, "zoe_gone"):
				sit(g, "zoe", Vector3(23.42, CF + 0.42, -3.38), 0.0, "Rk_SitIdle")
			sit(g, "noah", Vector3(22.9, CF + 0.42, -3.38), 0.0, "Rk_SitIdle")
			sit(g, "iris", Vector3(22.75, CF + 0.42, -1.82), 180.0, "Rk_SitIdle")
			g.blackcar = BlackCar.new()
			g.blackcar.gap_side = -1.0   # нос к кафе; к Ною (со стороны кафе) — левое заднее окно
			s.cafe_root.add_child(g.blackcar)
			g.blackcar.position = Vector3(17.5, 0.0, -40.0)   # в конце стоянки, мимо фур — его видно из окна кафе
			g.blackcar.rotation_degrees = Vector3(0, 160, 0)
			g.blackcar.lights(true)
			g.sfx.play("engine_start", -20.0, 0.9)
		"cup_drop":
			g.sfx.play("glass_shatter", -16.0, 1.7)
		"pill_label":
			pill_bottle(g, true)
		"back_door":   # через задний двор — к машине, в буран
			pill_bottle(g, false)
			g.sfx.play("car_door", -8.0, 1.0, 1.4)
		"noah_to_sedan":   # Ной идёт по снегу к седану, к заднему правому окну
			pill_bottle(g, false)
			var w: Vector3 = g.blackcar.to_global(Vector3(-1.55, 0.0, 0.55))
			g._place({"who": "noah", "at": [21.4, CF, -7.8], "face": -120, "clip": "Idle"})
			g._walk({"who": "noah", "to": [w.x, 0.0, w.z], "then": "Idle",
				"face": rad_to_deg(atan2(g.blackcar.global_position.x - w.x, g.blackcar.global_position.z - w.z))})
		"sedan_window":   # заднее стекло опускается на два пальца
			g.blackcar.window_gap(true)
			g.sfx.play("window_down", -16.0, 0.8)
		"sedan_leave":
			g.blackcar.window_gap(false)
			g.blackcar.speed = 0.0
			var tw: Tween = g.create_tween()
			tw.tween_property(g.blackcar, "speed", -3.5, 3.0)   # задним ходом — в снег, прочь от кафе
		"night_cafe":   # вечер у кафе: седан всё ещё на парковке; в зале горят все лампы
			s.set_daylight("evening")
			if is_instance_valid(s.cafe_root) and not c2.get("night_lit", false):
				c2["night_lit"] = true
				for n in s.cafe_root.get_children():
					if n is OmniLight3D:
						(n as OmniLight3D).light_energy *= 1.8
			if not is_instance_valid(g.blackcar):
				fx(g, "sedan_arrive")
		"handle":
			g.sfx.play("glovebox", -12.0, 0.7, 0.8)
		"morning_cafe":
			s.set_daylight("day")
			if is_instance_valid(g.blackcar):
				g.blackcar.queue_free()
			if on(g, "zoe_taken"):
				drop(g, "zoe")
				bracelet_snow(g)
		"eva_porch":   # Ева на крыльце с сумкой
			g._place({"who": "eva", "at": [21.05, CF, -8.0], "face": -110, "clip": "Idle"})
			for k2 in ["iris", "danny", "zoe"]:
				if g.actors.has(k2) and (g.actors[k2] as Actor).get_parent() == s:
					(g.actors[k2] as Actor).visible = false
			g._place({"who": "noah", "at": [18.6, 0.0, -11.0], "face": 50, "clip": "Idle"})
			(g.actors.noah as Actor).look_target = g._target("eva")
			(g.actors.eva as Actor).look_target = g._target("noah")
		"eva_in_car":   # Ева садится назад, к Айрис; все — в машине на стоянке
			g.sfx.play("car_door", -7.0)
			for k2 in g.actors:
				(g.actors[k2] as Actor).visible = true
			seat_all(g)
		"leave_cafe":   # дальше в буран
			for k2 in g.actors:
				(g.actors[k2] as Actor).visible = true
			for k2 in ["trucker1", "trucker2", "trucker3", "waitress"]:
				drop(g, k2)
			if not on(g, "eva"):
				drop(g, "eva")
			s.leave_cafe()
			s.set_daylight("day")
			seat_all(g)
			s.road.snow.amount = 5200 if s.pc else 2400
			s.env.fog_density = 0.03
			g._blizzard = true
			drive(g, 12.0, 4.0)
		"truck_block":   # поперёк дороги сложилась фура, аварийка мигает; встаём метрах в двенадцати
			var tr := Truck.new().setup(Color(0.55, 0.55, 0.53))
			s.road_items.add_child(tr)
			tr.position = Vector3(1.0, 0.0, -40.0)
			tr.rotation_degrees.y = 72.0
			tr.fold(-78.0)
			tr.lights(true)
			tr.hazard = true
			c2["truck"] = tr
			stop_in(g, 28.0, 4.0)
			g.sfx.play("brake_squeal", -14.0, 0.9, 1.0)
		"side_road":   # направо, на боковую: указатель засыпан снегом наполовину
			var tw: Tween = g.create_tween()
			tw.tween_property(s.car, "steer", 0.8, 0.8)
			tw.tween_property(s.car, "steer", 0.0, 1.2)
			for n in s.road_items.get_children():
				if n is Truck:
					n.queue_free()
			var sp: Node3D = s.signpost(-55.0, "")
			sp.position.y = -1.1
			drive(g, 8.0, 3.0)
		"far_lights":   # в зеркале, далеко позади, — две фары: держатся на одном расстоянии
			var bc := BlackCar.new()
			s.road_items.add_child(bc)
			bc.position = Vector3(0.0, 0.0, 24.0)
			bc.lights(true)
			for x in [-0.6, 0.6]:   # ореол фар в снегу — его видно в зеркале сквозь мглу
				var ol := OmniLight3D.new()
				ol.light_color = Color(1.0, 0.95, 0.85)
				ol.light_energy = 2.0
				ol.omni_range = 4.0
				ol.light_volumetric_fog_energy = 3.0
				ol.position = Vector3(x, 0.75, -2.4)
				bc.add_child(ol)
			bc.speed = s.road.speed
			c2["follower"] = bc
		_:
			return false
	return true


## Едем: скорость дороги и колёс плавно к v за t секунд.
static func drive(g, v: float, t: float) -> void:
	var tw: Tween = g.create_tween().set_parallel()
	tw.tween_property(g.scene.road, "speed", v, t).set_trans(Tween.TRANS_SINE)
	tw.tween_property(g.scene.car, "speed", v, t).set_trans(Tween.TRANS_SINE)


## Встать через d метров: едем быстро — ровно тормозим; ползём — чуть подкатываемся и тормозим (не дольше tmax).
static func stop_in(g, d: float, tmax := 3.0) -> float:
	var s: DriveScene = g.scene
	var v0: float = s.road.speed
	var tw: Tween = g.create_tween().set_parallel()
	if d <= 0.05:
		tw.tween_property(s.road, "speed", 0.0, 0.6)
		tw.tween_property(s.car, "speed", 0.0, 0.6)
		return 0.6
	var t := 2.0 * d / maxf(v0, 0.1)
	if t <= tmax:
		tw.tween_property(s.road, "speed", 0.0, t)
		tw.tween_property(s.car, "speed", 0.0, t)
		return t
	var vp := maxf(0.0, (4.0 * d / tmax - v0) / 2.0)   # путь (v0 + 2·vp)·tmax/4 = d
	tw.tween_property(s.road, "speed", vp, tmax * 0.5)
	tw.tween_property(s.car, "speed", vp, tmax * 0.5)
	tw.chain().tween_property(s.road, "speed", 0.0, tmax * 0.5)
	tw.parallel().tween_property(s.car, "speed", 0.0, tmax * 0.5)
	return tmax


## Зоя бежит к окну пассажира (рюкзак лупит по спине).
static func _zoe_run(g) -> void:
	g._walk({"who": "zoe", "to": [1.35, 0.0, 0.35], "face": -90, "then": "Idle_Talking", "clip": "Jog_Fwd", "speed": 3.2})


## Посадить героя: клип сидя, таз — в точку p, лицом по face (градусы; 0 — к +Z).
static func sit(g, who: String, p: Vector3, face: float, clip := "Rk_SitIdle") -> void:
	var a := actor(g, who)
	a.reach = {}
	g._place({"who": who, "at": [p.x, p.y - 0.42, p.z], "face": face, "clip": clip})
	await g.get_tree().process_frame
	await g.get_tree().process_frame
	if is_instance_valid(a):
		g._sit(who, p)


## Кафе: стоянка, герои за столом у окна, Ева в углу, дальнобойщики у стойки.
static func cafe(g) -> void:
	var s: DriveScene = g.scene
	s.park_at_cafe(g.lang)
	s.set_daylight("day")
	g._blizzard = false
	sit(g, "noah", Vector3(22.9, CF + 0.42, -3.38), 0.0)
	sit(g, "iris", Vector3(22.75, CF + 0.42, -1.82), 180.0)
	if on(g, "danny"):
		sit(g, "danny", Vector3(23.42, CF + 0.42, -1.82), 180.0)
	if on(g, "zoe") and not on(g, "zoe_gone"):
		sit(g, "zoe", Vector3(23.42, CF + 0.42, -3.38), 0.0)
	actor(g, "eva")
	sit(g, "eva", Vector3(23.0, CF + 0.42, -14.18), 0.0)
	var tk := [["trucker1", "gus", Vector3(30.12, CF + 0.72, -11.4), 90.0], ["trucker2", "frank", Vector3(30.12, CF + 0.72, -9.9), 90.0],
		["trucker3", "ethan", Vector3(30.12, CF + 0.72, -8.4), 110.0]]
	for t in tk:
		actor(g, t[0], t[1])
		sit(g, t[0], t[2], t[3])
	if ResourceLoader.exists("res://assets/chars/nina.glb"):   # официантка Нина за стойкой
		actor(g, "waitress", "nina")
		g._place({"who": "waitress", "at": [31.75, CF, -6.6], "face": -90, "clip": "Idle"})
	for k2 in g.actors:
		(g.actors[k2] as Actor).look_target = null
	if g.actors.has("waitress"):
		(g.actors.waitress as Actor).look_target = g._target("trucker3")


## Отражение Ноя в зеркале. «Тень» 0 — обычное; 1 — двойник моргает невпопад; 2+ — не моргает и чуть улыбается.
## Двойник виден только камерам зеркал (слой 2), сам Ной для них скрыт (слой 3).
static func reflection(g, shadow: float) -> void:
	var noah: Actor = g.actors.noah
	var dbl: Actor = g.c2.get("double")
	if shadow < 1.0:
		if is_instance_valid(dbl):
			dbl.queue_free()
		g.c2.erase("double")
		_layers(noah, Car.DRIVER_LAYER)
		g.cam.cull_mask = 0xFFFFF
		g.scene.car.mirror_hide_driver = false
		_mirror_mask(g, 0xFFFFF)
		return
	if not is_instance_valid(dbl):
		dbl = Actor.make("noah_double", "noah")
		noah.get_parent().add_child(dbl)
		g.c2["double"] = dbl
	dbl.global_transform = noah.global_transform
	dbl.play(noah.clip, 0.0)
	_layers(noah, Car.DRIVER_LAYER)
	_layers(dbl, 2)
	g.cam.cull_mask = 0xFFFFF & ~2
	g.scene.car.mirror_hide_driver = true
	_mirror_mask(g, 0xFFFFF & ~Car.DRIVER_LAYER)
	dbl.set_meta("shadow", shadow)
	if shadow >= 2.0:
		dbl.blink_t = 1e9
		dbl.expr = {"mouthSmileLeft": 0.3, "mouthSmileRight": 0.25}
	else:
		dbl.blink_t = 1.2


static func _layers(a: Actor, layer_bits: int) -> void:
	for mi in a.find_children("*", "MeshInstance3D", true, false):
		(mi as MeshInstance3D).layers = layer_bits


static func _mirror_mask(g, mask: int) -> void:
	g.scene.car.mirror_mask()   # зеркало машины само решает по тому, куда смотрит
	var wm = g.c2.get("wc_mirror")
	if wm and is_instance_valid(wm):
		(wm as PlanarMirror).cam.cull_mask = mask


## Каждый кадр (из game._process): двойник повторяет позу Ноя.
static func process(g) -> void:
	var dbl: Actor = g.c2.get("double")
	if not is_instance_valid(dbl):
		return
	var noah: Actor = g.actors.noah
	dbl.global_transform = noah.global_transform
	for i in noah.skel.get_bone_count():
		dbl.skel.set_bone_pose_rotation(i, noah.skel.get_bone_pose_rotation(i))
		dbl.skel.set_bone_pose_position(i, noah.skel.get_bone_pose_position(i))
	if float(dbl.get_meta("shadow", 0.0)) < 2.0 and noah.blink_t < 0.16 and dbl.blink_t > 0.5:
		dbl.blink_t = 0.45   # моргает на долю секунды позже него


## Больничный браслет на левом запястье Зои.
static func bracelet(g) -> void:
	if g.c2.get("zoe_bracelet", false) or not g.actors.has("zoe"):
		return
	g.c2["zoe_bracelet"] = true
	var z: Actor = g.actors.zoe
	var ba := BoneAttachment3D.new()
	ba.bone_name = "hand_l"
	z.skel.add_child(ba)
	var m := MeshInstance3D.new()
	var tm := TorusMesh.new()
	tm.inner_radius = 0.031
	tm.outer_radius = 0.045
	m.mesh = tm
	var mat := StandardMaterial3D.new()
	mat.albedo_color = Color(0.93, 0.95, 0.98)
	mat.roughness = 0.4
	m.material_override = mat
	m.position = Vector3(0.0, 0.012, 0.0)   # у самого запястья, из-под манжеты
	ba.add_child(m)


## Браслет Зои на снегу у машины (утро после ночи у кафе).
static func bracelet_snow(g) -> void:
	var m := MeshInstance3D.new()
	var tm := TorusMesh.new()
	tm.inner_radius = 0.034
	tm.outer_radius = 0.044
	m.mesh = tm
	var mat := StandardMaterial3D.new()
	mat.albedo_color = Color(0.9, 0.92, 0.95)
	mat.roughness = 0.35
	m.material_override = mat
	m.position = Vector3(19.05, 0.015, -11.35)   # у пассажирской двери, на снегу (не под машиной)
	m.rotation_degrees = Vector3(8, 30, 0)
	g.scene.cafe_root.add_child(m)


## Пост полиции: патрульные машины с мигалками поперёк обочины, очередь из трёх машин; машина встаёт за последней.
static func post(g) -> void:
	var s: DriveScene = g.scene
	var root := Node3D.new()
	s.road_items.add_child(root)
	g.c2["post"] = root
	var cols := [Color(0.35, 0.05, 0.05), Color(0.6, 0.6, 0.58), Color(0.1, 0.16, 0.3)]
	for i in 3:   # очередь
		var q := BlackCar.new()
		q.paint_color = cols[i]
		q.tinted = false
		root.add_child(q)
		q.position = Vector3(0.0, 0.0, -82.0 - i * 6.5)
		q.lights(true)
	for p in [[Vector3(2.6, 0.0, -106.0), 25.0], [Vector3(-3.3, 0.0, -110.0), -160.0]]:   # патрульные
		var pc := police_car(g)
		root.add_child(pc)
		pc.position = p[0]
		pc.rotation_degrees.y = p[1]
	var cop := actor(g, "cop", "dayguard")
	cop.reparent(root, false)
	cop.position = Vector3(1.6, 0.0, -100.0)
	cop.rotation_degrees.y = 180.0
	cop.play("Idle_Talking", 0.0)
	var d := 75.0
	var t := 2.0 * d / maxf(s.road.speed, 0.1)
	var tw: Tween = g.create_tween().set_parallel()
	tw.tween_property(s.road, "speed", 0.0, t)
	tw.tween_property(s.car, "speed", 0.0, t)


## Патрульная машина: белая, синяя полоса, мигалка на крыше (синий/красный по очереди).
static func police_car(g) -> BlackCar:
	var pc := BlackCar.new()
	pc.paint_color = Color(0.85, 0.86, 0.88)
	pc.tinted = false
	pc.has_driver = false
	var bar := Node3D.new()
	bar.name = "lightbar"
	bar.position = Vector3(0.0, 1.5, 0.1)
	pc.add_child(bar)
	for x in [-0.3, 0.3]:
		var m := MeshInstance3D.new()
		var bm := BoxMesh.new()
		bm.size = Vector3(0.5, 0.1, 0.22)
		m.mesh = bm
		var mat := StandardMaterial3D.new()
		mat.albedo_color = Color(0.1, 0.25, 1.0) if x < 0 else Color(1.0, 0.08, 0.06)
		mat.emission_enabled = true
		mat.emission = mat.albedo_color
		mat.emission_energy_multiplier = 3.0
		m.material_override = mat
		m.position = Vector3(x, 0.0, 0.0)
		bar.add_child(m)
		var ol := OmniLight3D.new()
		ol.light_color = mat.albedo_color
		ol.light_energy = 2.5
		ol.omni_range = 9.0
		ol.position = Vector3(x, 0.15, 0.0)
		m.add_child(ol)
	pc.set_meta("police", true)
	g.c2.get_or_add("police_cars", []).append(pc)
	return pc


## Каждый кадр: мигалки патрульных — синий и красный по очереди.
static func flash(g, t: float) -> void:
	for pc in g.c2.get("police_cars", []):
		if not is_instance_valid(pc):
			continue
		var bar: Node3D = (pc as Node3D).get_node_or_null("lightbar")
		if bar == null or bar.get_child_count() < 2:
			continue
		var a := fmod(t * 2.2, 1.0) < 0.5
		(bar.get_child(0) as Node3D).visible = a
		(bar.get_child(1) as Node3D).visible = not a


## Очередь прошла (склейка): машины впереди уехали, полицейский у окна водителя.
static func cop_window(g) -> void:
	var root: Node3D = g.c2.get("post")
	if root == null:
		return
	for i in 3:
		(root.get_child(i) as Node3D).visible = false
	# пост — прямо перед машиной: патрульные в 9–13 м впереди (корень — у машины, иначе дорога уберёт его как «далеко позади»)
	root.position.z = 0.0
	for ch in root.get_children():
		(ch as Node3D).position.z += 97.0
	var cop: Actor = g.actors.get("cop")
	if cop:
		cop.reparent(g.scene, false)
		cop.position = Vector3(-1.45, 0.0, 0.05)
		cop.rotation_degrees.y = 90.0
		cop.play("Idle_Talking", 0.0)
		cop.look_target = g._target("noah")
		(g.actors.noah as Actor).look_target = g._target("cop")


## Туалет кафе: Ной у раковины, розовая вода.
static func wc(g) -> void:
	for k2 in g.actors:
		(g.actors[k2] as Actor).look_target = null
	g._place({"who": "noah", "at": [33.12, CF, -13.8], "face": 90, "clip": "Interact"})
	var w := MeshInstance3D.new()
	var pm := PlaneMesh.new()
	pm.size = Vector2(0.3, 0.36)
	w.mesh = pm
	var m := StandardMaterial3D.new()
	m.albedo_color = Color(0.75, 0.32, 0.34, 0.6)
	m.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
	m.roughness = 0.05
	w.material_override = m
	w.position = Vector3(33.5, CF + 0.965, -13.8)
	g.scene.cafe_root.add_child(w)
	g.c2["pink"] = w


## Зеркало над раковиной — настоящее отражение (PlanarMirror).
static func wc_mirror(g, enable: bool) -> void:
	var wm = g.c2.get("wc_mirror")
	if enable and not (wm and is_instance_valid(wm)):
		var pm := PlanarMirror.new()
		g.scene.cafe_root.add_child(pm)
		pm.build(Vector3(33.727, CF + 1.6, -13.8), Vector3(-1, 0, 0), Vector2(0.6, 0.75), g.cam)   # лицо стекла модели — x 33.73
		g.c2["wc_mirror"] = pm
	elif not enable and wm and is_instance_valid(wm):
		(wm as PlanarMirror).queue_free()
		g.c2.erase("wc_mirror")


## Пузырёк таблеток в правой руке Ноя.
static func pill_bottle(g, show: bool) -> void:
	var b = g.c2.get("bottle")
	if show and not (b and is_instance_valid(b)):
		var noah: Actor = g.actors.noah
		var ba := BoneAttachment3D.new()
		ba.bone_name = "hand_r"
		noah.skel.add_child(ba)
		var m := MeshInstance3D.new()
		var cm := CylinderMesh.new()
		cm.top_radius = 0.022
		cm.bottom_radius = 0.022
		cm.height = 0.08
		m.mesh = cm
		var mat := StandardMaterial3D.new()
		mat.albedo_color = Color(0.75, 0.42, 0.12, 0.85)
		mat.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
		mat.roughness = 0.2
		m.material_override = mat
		m.position = Vector3(0.0, 0.07, 0.03)
		ba.add_child(m)
		var paper := MeshInstance3D.new()   # бумажная этикетка вокруг пузырька
		var pc := CylinderMesh.new()
		pc.top_radius = 0.0232
		pc.bottom_radius = 0.0232
		pc.height = 0.045
		paper.mesh = pc
		var pm := StandardMaterial3D.new()
		pm.albedo_color = Color(0.93, 0.92, 0.86)
		pm.roughness = 0.8
		paper.material_override = pm
		m.add_child(paper)
		var lb := Label3D.new()
		lb.text = "Д-р С. Хейл\nпо 1 таб. 2 р/д" if g.lang == "ru" else "Dr. S. Hale\n1 tab twice daily"
		lb.font_size = 40
		lb.pixel_size = 0.00028
		lb.modulate = Color(0.08, 0.08, 0.1)
		lb.outline_size = 0
		lb.shaded = false
		lb.double_sided = false
		lb.position = Vector3(0.0, 0.0, 0.0236)
		m.add_child(lb)
		g.c2["bottle"] = ba
		g.c2["bottle_label"] = lb
	elif not show and b and is_instance_valid(b):
		(b as Node).queue_free()
		g.c2.erase("bottle")


## Мальчик в зеркале туалета (виден только в отражении — слой 2): мокрый Ной в восемь лет за его плечом.
static func boy(g, show: bool) -> void:
	var b = g.c2.get("boy")
	if show and not (b and is_instance_valid(b)):
		var a := Actor.make("noah_kid", "noah_kid" if ResourceLoader.exists("res://assets/chars/noah_kid.glb") else "danny")
		g.scene.cafe_root.add_child(a)
		a.position = Vector3(32.78, CF, -14.5)   # сбоку за левым плечом Ноя — в поле зеркала из плана wc_mirror, не за его спиной
		a.rotation_degrees.y = 26.0
		a.play("Idle", 0.0)
		a.look_target = g.actors.noah.global_position + Vector3(0, 1.6, 0)
		_wet(a)
		_layers(a, 2)
		g.cam.cull_mask = 0xFFFFF & ~2
		g.c2["boy"] = a
	elif not show and b and is_instance_valid(b):
		(b as Node).queue_free()
		g.c2.erase("boy")


## Мокрый (из озера): ткань и кожа темнее и блестят, волосы липнут (тёмные и глянцевые).
static func _wet(a: Actor) -> void:
	for mi in a.find_children("*", "MeshInstance3D", true, false):
		var m := mi as MeshInstance3D
		for i in m.mesh.get_surface_count():
			var mat := m.get_active_material(i)
			if mat is StandardMaterial3D:
				var w := (mat as StandardMaterial3D).duplicate() as StandardMaterial3D
				w.albedo_color = w.albedo_color * Color(0.72, 0.74, 0.78, 1.0)
				w.roughness = 0.18
				w.metallic_specular = 0.75
				m.set_surface_override_material(i, w)
			elif mat is ShaderMaterial:
				(mat as ShaderMaterial).set_shader_parameter("albedo", Color(0.45, 0.45, 0.5))


## Сигарета у Зои: огонёк и дым.
static func cigarette(g, show: bool) -> void:
	var c = g.c2.get("cig")
	if show and not (c and is_instance_valid(c)) and g.actors.has("zoe"):
		var z: Actor = g.actors.zoe
		var ba := BoneAttachment3D.new()
		ba.bone_name = "hand_r"
		z.skel.add_child(ba)
		var tip := OmniLight3D.new()
		tip.light_color = Color(1.0, 0.45, 0.15)
		tip.light_energy = 0.35
		tip.omni_range = 0.35
		tip.position = Vector3(0.0, 0.1, 0.06)
		ba.add_child(tip)
		var smoke := CPUParticles3D.new()
		smoke.amount = 40
		smoke.lifetime = 3.0
		smoke.direction = Vector3(0, 1, 0)
		smoke.spread = 18.0
		smoke.initial_velocity_min = 0.08
		smoke.initial_velocity_max = 0.18
		smoke.gravity = Vector3(0.05, 0.08, 0)
		smoke.scale_amount_min = 0.03
		smoke.scale_amount_max = 0.09
		var sm := SphereMesh.new()
		sm.radius = 0.5
		sm.height = 1.0
		var smat := StandardMaterial3D.new()
		smat.albedo_color = Color(0.8, 0.8, 0.82, 0.12)
		smat.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
		smat.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
		sm.material = smat
		smoke.mesh = sm
		smoke.position = Vector3(0.0, 0.1, 0.06)
		ba.add_child(smoke)
		g.c2["cig"] = ba
	elif not show and c and is_instance_valid(c):
		(c as Node).queue_free()
		g.c2.erase("cig")


## Герой в машине — лицом. Спереди пассажир — с места водителя (план «iris»); сзади — из просвета между передними
## креслами, чуть со стороны, противоположной герою. false — героя нет в машине.
static func car_face(g, who: String) -> bool:
	var a = g.actors.get(who)
	if not is_instance_valid(a) or (a as Actor).get_parent() != g.scene.car:
		return false
	var car: Car = g.scene.car
	var head: Vector3 = car.to_local(g._target(who))
	if head.z < 0.75:   # спереди: план по голове (поза за рулём бывает выше или ниже — лицо всегда в кадре)
		var off := Vector3(0.62, -0.2, -0.48) if head.x < 0.0 else Vector3(-0.62, -0.12, -0.5)   # ниже козырьков
		g.cam.fov = 40.0
		g.cam.look_at_from_position(car.to_global(head + off), car.to_global(head + Vector3(0, -0.05, 0)))
		return true
	var cp := Vector3(-0.05 if head.x > 0.1 else (0.05 if head.x < -0.1 else 0.0), 1.1, 0.6)
	g.cam.fov = 44.0
	g.cam.look_at_from_position(car.to_global(cp), car.to_global(head + Vector3(0, -0.05, 0)))
	return true


## Планы, которые зависят от того, где герои. true — план поставлен.
static func cam(g, shot: String) -> bool:
	var s: DriveScene = g.scene
	var c: Camera3D = g.cam
	match shot:
		"iris", "zoe", "danny", "eva", "noah":   # герой в машине — лицом, где бы он ни сидел
			return active(g) and car_face(g, shot)
		"back":   # заднее сиденье целиком — из просвета между передними креслами
			if not active(g):
				return false
			c.fov = 58.0
			c.look_at_from_position(s.car.to_global(Vector3(0.0, 1.15, 0.55)), s.car.to_global(Vector3(0.0, 1.0, 1.3)))
		"truck":   # снаружи, у нашего капота: сложившаяся фура поперёк дороги, аварийка в снегу
			var tr = g.c2.get("truck")
			if not is_instance_valid(tr):
				return false
			c.fov = 46.0
			c.look_at_from_position(s.car.to_global(Vector3(-1.7, 1.35, -3.2)), (tr as Node3D).global_position + Vector3(0, 1.6, 0))
		"wc_pills":   # пузырёк в руке Ноя, крупно
			var b = g.c2.get("bottle")
			if not (b and is_instance_valid(b)):
				return false
			var bp: Vector3 = (b as Node3D).global_position
			c.fov = 34.0
			c.look_at_from_position(bp + Vector3(-0.42, 0.28, 0.3), bp)
		"zoe_out":   # Зоя на обочине — снаружи, перед ней: машет обеими руками
			var z: Actor = g.actors.get("zoe")
			if z == null:
				return false
			c.fov = 38.0
			c.look_at_from_position(z.global_position + Vector3(-1.5, 1.4, 3.1), z.global_position + Vector3(0, 1.45, 0))
		"zoe_run":   # из-за спины Зои: машина подкатывает и встаёт, Зоя бежит к окну
			var z: Actor = g.actors.get("zoe")
			if z == null:
				return false
			var zp := z.global_position
			c.fov = 40.0
			c.look_at_from_position(zp + Vector3(0.9, 1.5, -3.2), (s.car.global_position + Vector3(0.7, 0.9, 0.0)).lerp(zp + Vector3(0, 1.0, 0), 0.35))
		"zoe_wrist":   # Зоя спереди: её левое запястье с браслетом — глазами Ноя, крупно
			var z: Actor = g.actors.get("zoe")
			if z == null:
				return false
			var hp: Vector3 = z.skel.global_transform * z.skel.get_bone_global_pose(z.skel.find_bone("hand_l")).origin
			c.fov = 32.0
			c.look_at_from_position(hp + s.car.global_transform.basis * Vector3(-0.16, 0.3, -0.3), hp)
		"zoe_window":   # из машины — на Зою у окна пассажира
			var z: Actor = g.actors.get("zoe")
			if z == null:
				return false
			c.fov = 42.0
			c.look_at_from_position(s.car.to_global(Vector3(-0.25, 1.22, 0.15)), z.global_position + Vector3(0, 1.45, 0))
		"lamp_drive":   # одна фара светит — машина идёт на камеру
			c.fov = 40.0
			c.look_at_from_position(Vector3(-0.6, 0.95, -9.0), s.car.to_global(Vector3(-0.2, 0.7, -2.6)))
		"post":   # пост впереди: мигалки, очередь
			c.fov = 40.0
			c.look_at_from_position(Vector3(2.6, 2.3, 5.5), Vector3(0.0, 1.0, -60.0))
		"cop":   # из машины — полицейский у окна водителя
			var cop: Actor = g.actors.get("cop")
			if cop == null:
				return false
			c.fov = 44.0
			c.look_at_from_position(s.car.to_global(Vector3(0.38, 1.2, 0.05)), g._target("cop"))
		"sedan_window":   # из-за правого плеча Ноя — заднее стекло седана, опущенное на два пальца
			if not is_instance_valid(g.blackcar):
				return false
			var sd: float = g.blackcar.gap_side
			c.fov = 34.0
			c.look_at_from_position(g.blackcar.to_global(Vector3(2.4 * sd, 2.0, 1.6)), g.blackcar.to_global(Vector3(0.79 * sd, 1.22, 0.55)))
		"noah_sedan":   # лицо Ноя у седана — из-за стекла
			if not is_instance_valid(g.blackcar):
				return false
			c.fov = 36.0
			c.look_at_from_position(g.blackcar.to_global(Vector3(0.95 * float(g.blackcar.gap_side), 1.45, 0.4)), g._target("noah"))
		"pill_label":   # этикетка на пузырьке: «Д-р С. Хейл» — камера прямо на надпись
			var lb = g.c2.get("bottle_label")
			if not (lb and is_instance_valid(lb)):
				return false
			var lt: Transform3D = (lb as Node3D).global_transform
			c.fov = 22.0
			c.look_at_from_position(lt.origin + lt.basis.z.normalized() * 0.2, lt.origin, lt.basis.y.normalized())   # надпись — ровно
		_:
			return false
	return true

