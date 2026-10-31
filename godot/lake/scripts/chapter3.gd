class_name Ch3
extends RefCounted
## Глава 3 «Деревня»: события сценария (fx) и планы, которые зависят от героев. Сценарий — data/ch3.json
## (генератор tools/lake/story/ch3.py), мир деревни — scripts/world/village.gd (модель tools/lake/village.py).
## Боковая дорога в буране, машина в сугробе, Ева в кювете, деревня, дом Агнес с печью, роды, ночь у печи,
## «второй Ной» с топором у последнего дома, чёрный седан в конце улицы на рассвете. Состояние — g.c3.

const AGX := -11.0
const AGZ := -45.0


static func ag(x: float, y: float, z: float) -> Vector3:
	return Village.ag(x, y, z)


static func actor(g, key: String, model := "") -> Actor:
	return Ch2.actor(g, key, model)


static func on(g, f: String) -> bool:
	return float(g.flags.get(f, 0)) > 0.0


static func has(g, k: String) -> bool:
	return g.actors.has(k) and is_instance_valid(g.actors[k])


## Поставить героя в точку мира: лицом по face (градусы; 0 — к +Z), клип.
static func put(g, who: String, p: Vector3, face: float, clip := "Idle") -> void:
	if not has(g, who):
		return
	(g.actors[who] as Actor).visible = true
	(g.actors[who] as Actor).reach = {}
	(g.actors[who] as Actor).wave = 0.0
	g._place({"who": who, "at": [p.x, p.y, p.z], "face": face, "clip": clip})


## Замереть в позе: клип на времени t и стоп (лежит, стоит на коленях…).
static func hold(a: Actor, clip: String, t := 0.0) -> void:
	a.play(clip, t)
	a.ap.pause()


static func face_to(from: Vector3, to: Vector3) -> float:
	return rad_to_deg(atan2(to.x - from.x, to.z - from.z))


static func vil(g) -> Village:
	var v = g.c3.get("village")
	return v if (v and is_instance_valid(v)) else null


## Деревня: модель, свет, дорога встаёт, ели вокруг расступаются; ночь.
static func village(g) -> Village:
	var v := vil(g)
	if v:
		return v
	var s: DriveScene = g.scene
	s.road.park(Village.LOT)
	v = Village.new()
	s.add_child(v)
	v.build(g.lang, s.pc)
	g.c3["village"] = v
	s.moon.light_energy = 0.12   # снег в деревне светится под луной (иначе ночью — чернота)
	for n in s.road_items.get_children():   # дорожное (машина Евы, огни вдали) — убрать
		n.queue_free()
	return v


## Кто есть в главе: Ной, Айрис; Дэнни, Зоя, Ева — по флагам; бабушка Агнес — в доме.
static func cast(g) -> Array:
	var who := ["noah", "iris"]
	if on(g, "danny") and not on(g, "danny_lost"):
		who.append("danny")
	if on(g, "zoe") and not on(g, "zoe_gone"):
		who.append("zoe")
	if (on(g, "eva") or on(g, "eva_found")) and not on(g, "eva_dead"):
		who.append("eva")
	for k in who:
		if not has(g, k):
			if k == "danny":
				g._attach_walkie(actor(g, "danny"))
			else:
				actor(g, k)
	return who


static func agnes(g) -> Actor:
	var fresh: bool = not has(g, "agnes")
	var a := actor(g, "agnes", "agnes" if ResourceLoader.exists("res://assets/chars/agnes.glb") else "eva")
	if fresh:
		a.visible = true
	return a


## Все в доме Агнес: у печи, у стола, на лавке.
static func in_house(g) -> void:
	var v := village(g)
	v.set_power(not g.c3.get("dark", false))
	cast(g)
	var a := agnes(g)
	a.visible = true
	put(g, "agnes", ag(-0.55, 0.0, -1.2), 60.0, "Idle")
	put(g, "noah", ag(1.0, 0.0, -0.4), -100.0, "Idle")
	put(g, "iris", ag(1.4, 0.0, 0.55), -120.0, "Idle_FoldArms")
	put(g, "danny", ag(2.0, 0.0, 0.1), -95.0, "Idle")
	put(g, "zoe", ag(0.6, 0.0, 1.5), -150.0, "Idle")
	if has(g, "eva"):
		put(g, "eva", ag(2.95, 0.0, 1.6), -90.0, "Rk_SitIdle")
		Ch2.sit(g, "eva", ag(2.9, 0.47, 1.6), -90.0)
	_car_away(g)
	for k in g.actors:
		(g.actors[k] as Actor).look_target = null


## Машина: доехали — стоит на улице у дома Агнес; шли пешком — осталась в сугробе (её не видно).
static func _car_away(g) -> void:
	var s: DriveScene = g.scene
	s.car.speed = 0.0
	s.car.ride = false
	if on(g, "walked"):
		s.car.visible = false
	else:
		s.car.visible = true
		s.car.position = Vector3(2.2, 0.0, -37.0)
		s.car.rotation = Vector3.ZERO
		s.car.lights(false)


static func restore(g, id: String) -> void:
	g.c3["on"] = true
	if id in ["c3_recap", "c3_s1"]:
		return   # дорогу ставит сценарий (fx c3_start)
	if id.begins_with("c3_s6"):
		g.c3["dark"] = false
	in_house(g)
	g.scene.set_daylight("night")


static func fx(g, k: String) -> bool:
	if not String(g._scene_id).begins_with("c3"):
		return false   # имена событий главы 3 бывают и в других главах (danny_stays)
	if k != "diary_agnes" and g.c3.has("diary"):   # дневник убран — руки Айрис опускаются
		var dn = g.c3.get("diary")
		if dn and is_instance_valid(dn):
			(dn as Node).queue_free()
		g.c3.erase("diary")
		if has(g, "iris"):
			(g.actors.iris as Actor).reach = {}
	var s: DriveScene = g.scene
	var c3: Dictionary = g.c3
	match k:
		"c3_start":   # ночь, буран, боковая дорога; машина ползёт; вдали — огоньки деревни
			c3["on"] = true
			s.set_daylight("night")
			Ch2.seat_all(g)
			s.car.lights(true)
			s.road.speed = 6.0
			s.car.speed = 6.0
			s.road.snow.amount = 5200 if s.pc else 2400
			s.env.fog_density = 0.034
			g._blizzard = true
			far_lights(g)
		"stuck":   # колёса буксуют, машина садится в сугроб
			Ch2.stop_in(g, 5.0, 2.0)
			g.sfx.play("skid", -10.0, 0.6)
			g.sfx.play("engine_start", -16.0, 0.7, 0.6)
			var tw: Tween = g.create_tween()
			tw.tween_interval(1.2)
			tw.tween_property(s.car, "position:y", -0.16, 0.8)
			snow_spray(g, true)
			g._shake = 0.4
		"eva_ditch":   # машина Евы в кювете, полузанесённая
			snow_spray(g, false)
			var e := BlackCar.new()
			e.paint_color = Color(0.2, 0.3, 0.26)
			e.tinted = false
			e.has_driver = false
			s.road_items.add_child(e)
			e.position = Vector3(DriveScene.ROAD_EDGE + 0.9, -0.45, -11.5)
			e.rotation_degrees = Vector3(8.0, 200.0, -12.0)
			c3["eva_car"] = e
			var cap := MeshInstance3D.new()   # шапка снега на крыше и капоте
			var bm := BoxMesh.new()
			bm.size = Vector3(1.6, 0.25, 3.6)
			cap.mesh = bm
			var sm := StandardMaterial3D.new()
			sm.albedo_color = Color(0.86, 0.88, 0.93)
			sm.roughness = 0.9
			cap.material_override = sm
			cap.position = Vector3(0, 1.45, 0.3)
			e.add_child(cap)
		"eva_in_ditch":   # Ева внутри: схватки, губы синие
			var ev := actor(g, "eva")
			var e: Node3D = c3.get("eva_car")
			if e:
				var p := e.to_global(Vector3(-0.36, 0.0, 0.3))
				put(g, "eva", p, e.global_rotation_degrees.y + 180.0, "Rk_SitIdle")
				Ch2.sit(g, "eva", e.to_global(Vector3(-0.36, 0.62, 0.42)), e.global_rotation_degrees.y + 180.0)
			g._expr({"who": "eva", "set": "fear"})
			ev.expr = {"mouthFrownLeft": 0.5, "mouthFrownRight": 0.5, "browInnerUp": 0.8}
			put(g, "noah", s.car.to_global(Vector3(1.6, 0.0, -2.0)), 160.0, "Idle")
		"carry_eva":   # Ной несёт Еву на руках по колено в снегу
			walkers(g, true)
			if has(g, "eva"):
				carry(g)
		"noah_run":   # Ной бежит к огоньку; Айрис остаётся с Евой
			put(g, "noah", s.car.to_global(Vector3(0.0, 0.0, -3.0)), 180.0, "Jog_Fwd")
			g._walk({"who": "noah", "to": [0.4, 0.0, -40.0], "clip": "Jog_Fwd", "speed": 3.0, "then": "Idle"})
			if has(g, "iris"):
				(g.actors.iris as Actor).visible = false
		"water_break":
			if has(g, "eva"):
				(g.actors.eva as Actor).expr = {"mouthFrownLeft": 0.6, "mouthFrownRight": 0.6, "browInnerUp": 0.9, "eyeSquintLeft": 0.5, "eyeSquintRight": 0.5}
		"noah_dig":   # Ной на коленях у заднего колеса
			put(g, "noah", s.car.to_global(Vector3(-1.25, 0.0, 1.15)), 90.0, "Fixing_Kneeling")
			g.sfx.play("car_door", -6.0)
		"unstuck":   # выползли
			snow_spray(g, true)
			g.sfx.play("engine_start", -10.0, 0.9)
			var tw: Tween = g.create_tween()
			tw.tween_property(s.car, "position:y", 0.0, 1.2)
			tw.tween_callback(func(): snow_spray(g, false))
			Ch2.seat_all(g)
			Ch2.drive(g, 4.0, 2.0)
		"walk_snow":   # пешком: все по колено в снегу к огоньку
			walkers(g, false)
		"village":   # деревня у леса; доехали — машина у дома Агнес
			snow_spray(g, false)
			var v := village(g)
			v.set_power(true)
			s.set_daylight("night")
			s.road.snow.amount = 3200 if s.pc else 1600
			s.env.fog_density = 0.02
			g._blizzard = true
			cast(g)
			_car_away(g)
			var spots := {"noah": Vector3(-3.4, 0, -40.0), "iris": Vector3(-2.7, 0, -38.9), "danny": Vector3(-2.4, 0, -39.7),
				"zoe": Vector3(-4.1, 0, -38.6), "eva": Vector3(-3.9, 0, -41.0)}
			for kk in spots:
				put(g, kk, spots[kk], face_to(spots[kk], Vector3(-7.7, 0, -45.0)), "Idle")
			if has(g, "eva"):
				(g.actors.eva as Actor).play("Walk", 0.0)
		"agnes_door":   # дверь открывает Агнес; Ной на ступенях
			var a := agnes(g)
			a.visible = true
			village(g).open_door()
			put(g, "agnes", Vector3(-7.95, 0.5, -45.0), 90.0, "Idle")
			put(g, "noah", Vector3(-6.75, 0.25, -45.05), -90.0, "Idle")
			put(g, "iris", Vector3(-5.9, 0.0, -44.1), -110.0, "Idle")
			put(g, "danny", Vector3(-5.6, 0.0, -45.6), -80.0, "Idle")
			put(g, "zoe", Vector3(-5.4, 0.0, -43.6), -120.0, "Idle")
			put(g, "eva", Vector3(-5.7, 0.0, -46.3), -70.0, "Idle")
			a.look_target = g._target("noah")
			(g.actors.noah as Actor).look_target = g._target("agnes")
			g.sfx.play("car_door", -12.0, 0.6)
		"house_in":
			in_house(g)
			g._blizzard = false
		"sleeves":
			(agnes(g)).play("Interact", 0.2)
		"agnes_nod":
			(agnes(g)).play("Yes", 0.2)
		"birth_bed":   # Еву — на кровать за печью
			in_house(g)
			lie(g, "eva", ag(-2.15, 0.6, 1.3), -90.0)
			put(g, "agnes", ag(-1.9, 0.0, 2.35), 180.0, "Idle")
			put(g, "iris", ag(-1.6, 0.0, 0.45), -30.0, "Crouch_Idle")
		"lights_out":   # свет мигает и гаснет
			var v := village(g)
			c3["dark"] = true
			var tw: Tween = g.create_tween()
			for i in 3:
				tw.tween_callback(func(): v.set_power(false))
				tw.tween_interval(0.12)
				tw.tween_callback(func(): v.set_power(true))
				tw.tween_interval(0.18)
			tw.tween_callback(func(): v.set_power(false))
			g.sfx.play("static_burst", -18.0, 0.5)
		"noah_storm":   # Ной один в буране
			put(g, "noah", Vector3(1.4, 0.0, -33.0), 160.0, "Walk")
			g._walk({"who": "noah", "to": [6.0, 0.0, -27.5], "speed": 1.0, "then": "Idle"})
			s.road.snow.amount = 5200 if s.pc else 2400
			s.env.fog_density = 0.035
			g.sfx.play("hum_ring", -14.0, 0.8)
		"shop":
			put(g, "noah", Vector3(7.4, 0.35, -26.0), 130.0, "Idle")
			canister(g, true)
		"shop_broken":   # провал: окно магазина разбито, в руке канистра
			village(g).break_shop_window()
			put(g, "noah", Vector3(6.6, 0.0, -27.2), -60.0, "Idle")
			canister(g, true)
			g.sfx.play("glass_shatter", -14.0, 0.9)
		"generator":   # генератор завёлся — свет
			canister(g, false)
			in_house(g)
			c3["dark"] = false
			village(g).set_power(true)
			village(g).set_candles(false)
			g.sfx.play("engine_start", -18.0, 0.6)
			restage_birth(g)
		"zoe_storm":
			if has(g, "zoe"):
				put(g, "zoe", ag(2.75, 0.0, -0.2), -90.0, "Idle")
				(g.actors.zoe as Actor).expr = {"browInnerUp": 0.7, "eyeWideLeft": 0.4, "eyeWideRight": 0.4}
			g.sfx.play("car_door", -10.0, 0.7)
		"candles":   # свечи и отсвет печи
			village(g).set_candles(true)
			restage_birth(g)
		"birth":
			restage_birth(g)
			if has(g, "danny"):
				put(g, "danny", ag(2.2, 0.0, 1.55), -90.0, "Crouch_Idle")
				(g.actors.danny as Actor).expr = {"eyeSquintLeft": 0.8, "eyeSquintRight": 0.8, "browInnerUp": 0.8}
			if has(g, "eva"):
				(g.actors.eva as Actor).expr = {"mouthOpen": 0.4, "jawOpen": 0.35, "eyeSquintLeft": 0.9, "eyeSquintRight": 0.9, "browInnerUp": 1.0}
			g.sfx.play("heartbeat", -8.0, 1.2)
		"baby_cry":
			baby(g, "agnes")
			g.sfx.play("baby_cry", -8.0)
		"bleed":   # кровь на простыне
			var m := MeshInstance3D.new()
			var pm := PlaneMesh.new()
			pm.size = Vector2(0.7, 0.55)
			m.mesh = pm
			var bl := StandardMaterial3D.new()
			bl.albedo_color = Color(0.32, 0.02, 0.02)
			bl.roughness = 0.25
			m.material_override = bl
			m.position = ag(-1.85, 0.575, 1.3)
			village(g).add_child(m)
			if has(g, "eva"):
				(g.actors.eva as Actor).expr = {"eyeSquintLeft": 0.5, "eyeSquintRight": 0.5, "mouthFrownLeft": 0.4, "mouthFrownRight": 0.4}
		"press":   # прижимать рану: Ной (или Айрис — если у Ноя был провал)
			var who := "iris" if on(g, "lapse_shop") else "noah"
			put(g, who, ag(-1.55, 0.0, 0.55), -40.0, "Fixing_Kneeling")
			(g.actors[who] as Actor).reach = {"_l": ag(-1.95, 0.62, 1.3), "_r": ag(-1.85, 0.62, 1.2)}
			if who == "iris":
				put(g, "noah", ag(0.7, 0.0, 2.6), -150.0, "Idle_FoldArms")
		"eva_baby":   # Ева держит девочку
			baby(g, "eva")
			if has(g, "eva"):
				(g.actors.eva as Actor).expr = {"mouthSmileLeft": 0.35, "mouthSmileRight": 0.35}
			for kk in ["noah", "iris"]:
				if has(g, kk):
					(g.actors[kk] as Actor).reach = {}
			put(g, "iris", ag(-1.5, 0.0, 0.5), -35.0, "Crouch_Idle")
		"eva_fading":
			if has(g, "eva"):
				(g.actors.eva as Actor).expr = {"eyeBlinkLeft": 0.6, "eyeBlinkRight": 0.6}
		"eva_dies":
			if has(g, "eva"):
				g._expr({"who": "eva", "set": "sleep"})
			baby(g, "iris")
			for kk in ["noah", "iris"]:
				if has(g, kk):
					(g.actors[kk] as Actor).reach = {}
		"night_stove":   # ночь; Агнес с трубкой у печи, Ной рядом
			in_house(g)
			var v := village(g)
			v.set_power(false)
			v.set_candles(false)
			Ch2.sit(g, "agnes", ag(-0.7, 0.42, -1.05), 75.0)
			(agnes(g)).play("Rk_SitIdle", 0.0)
			Ch2.sit(g, "noah", ag(0.25, 0.47, -1.55), -75.0)
			(g.actors.noah as Actor).play("Rk_SitIdle", 0.0)
			pipe(g, true)
			_house_sleepers(g)
		"pipe":
			pipe_puff(g)
		"diary_agnes":   # Айрис показывает бабушке мамин дневник
			put(g, "iris", ag(0.25, 0.0, -0.35), -120.0, "Idle")
			diary(g)
		"agnes_hand":
			(agnes(g)).reach = {"_l": g._target("noah") + Vector3(0, -0.55, 0)}
		"pills3":
			Ch2.pill_bottle(g, true)
		"burn_pills":
			Ch2.pill_bottle(g, false)
			var v := village(g)
			var tw: Tween = g.create_tween()
			tw.tween_property(v.fire, "light_energy", 4.0, 0.15)
			tw.tween_property(v.fire, "light_energy", 1.2, 1.4)
			g.sfx.play("blow", -16.0, 1.6)
		"drink_brew", "brew_cup":
			mug(g, true)
		"take_pill":
			g.sfx.play("pills", -10.0)
		"agnes_away":
			var a := agnes(g)
			a.rotation_degrees.y += 160.0
		"second_noah":   # ночь, снег, последний дом; Ной босиком, топор в руке, лицом к лесу
			var v := village(g)
			v.set_power(false)
			s.set_daylight("night")
			s.road.snow.amount = 1800 if s.pc else 900
			s.env.fog_density = 0.016
			g._blizzard = false
			mug(g, false)
			pipe(g, false)
			put(g, "noah", Vector3(6.0, 0.0, -94.2), 180.0, "Idle")
			axe(g, true)
			for kk in ["iris", "danny", "zoe", "eva", "agnes"]:
				if has(g, kk):
					(g.actors[kk] as Actor).visible = false
		"iris_behind":
			put(g, "iris", Vector3(6.3, 0.0, -88.4), 175.0, "Idle_FoldArms")
		"noah_turn":
			put(g, "noah", Vector3(6.0, 0.0, -94.2), 8.0, "Idle")
			axe(g, true)
			(g.actors.noah as Actor).look_target = g._target("iris")
		"axe_drop":
			axe(g, false)
			var ax := axe_mesh()
			ax.position = Vector3(6.1, 0.03, -94.25)
			ax.rotation_degrees = Vector3(90, 30, 0)
			village(g).add_child(ax)
			g.sfx.play("blow", -14.0, 0.7)
			var a := agnes(g)
			put(g, "agnes", Vector3(4.4, 0.0, -86.6), face_to(Vector3(4.4, 0, -86.6), Vector3(6.0, 0, -94.2)), "Idle_FoldArms")
			a.look_target = g._target("noah")
			(g.actors.noah as Actor).look_target = null
		"danny_at_window":   # Дэнни в доме у окна — всё видел
			if has(g, "danny"):
				put(g, "danny", ag(2.75, 0.0, 2.2), 90.0, "Idle")
				village(g).night_glow(true)
		"danny_door":   # Дэнни у двери — в валенках, с рацией
			in_house(g)
			put(g, "danny", ag(2.75, 0.0, 0.05), -90.0, "Idle")
			put(g, "noah", ag(0.4, 0.0, -0.8), 80.0, "Idle")
			(g.actors.danny as Actor).look_target = g._target("noah")
		"danny_stays":
			if has(g, "danny"):
				Ch2.sit(g, "danny", ag(2.9, 0.47, 1.1), -90.0)
		"block_door":
			put(g, "noah", ag(2.45, 0.0, 0.0), -90.0, "Idle")
			if has(g, "danny"):
				g._walk({"who": "danny", "to": [ag(2.6, 0, 2.4).x, ag(0, 0, 0).y, ag(2.6, 0, 2.4).z], "clip": "Jog_Fwd", "speed": 2.5, "then": "Idle"})
		"danny_tracks":   # следы в сторону трассы
			Ch2.drop(g, "danny")
			tracks(g)
			s.road.snow.amount = 5200 if s.pc else 2400
			g._blizzard = true
		"baby_night":   # ночь в доме: печь потрескивает, все спят
			pipe(g, false)
			in_house(g)
			village(g).set_power(false)
			_house_sleepers(g)
			Ch2.sit(g, "noah", ag(2.9, 0.47, 0.4), -90.0)
			(g.actors.noah as Actor).play("Rk_SitIdle", 0.0)
			if has(g, "danny"):
				Ch2.sit(g, "danny", ag(2.9, 0.47, 2.1), -90.0)
			if on(g, "baby"):
				g.sfx.play("baby_cry", -16.0, 1.05)
		"radio_song":   # сквозь помехи — та самая колыбельная
			village(g).night_glow(true)   # лицо у окна — в холодном свете снега
			village(g).radio_glow(true)
			g.sfx.play("static_burst", -16.0, 0.7)
			g.sfx.play("lullaby", -12.0, 1.0, 0.6)
		"dawn_street":   # под утро: буран стих; в конце улицы — фары седана, гаснут
			var v := village(g)
			v.set_power(false)
			v.night_glow(false)
			v.radio_glow(false)
			s.set_daylight("dawn")
			s.env.background_color = Color(0.16, 0.18, 0.22)
			s.env.ambient_light_energy = 0.25
			s.road.snow.amount = 600 if s.pc else 300
			s.env.fog_density = 0.012
			g._blizzard = false
			mug(g, false)
			pipe(g, false)
			if not is_instance_valid(g.blackcar):
				g.blackcar = BlackCar.new()
				v.add_child(g.blackcar)
			g.blackcar.position = Vector3(1.5, 0.0, -97.0)
			g.blackcar.rotation_degrees = Vector3(0, 180, 0)
			g.blackcar.speed = 0.0
			g.blackcar.lights(true)
			g.get_tree().create_timer(3.4).timeout.connect(func():   # горят, пока идёт кадр, — и гаснут
				if is_instance_valid(g.blackcar):
					g.blackcar.lights(false))
			in_house(g)
			put(g, "agnes", ag(2.75, 0.0, -1.7), 90.0, "Idle")
		"axe_walk":   # Ной идёт по пустой улице к седану
			for kk in ["iris", "danny", "zoe", "eva", "agnes"]:
				if has(g, kk):
					(g.actors[kk] as Actor).visible = false
			put(g, "noah", Vector3(-1.4, 0.0, -50.0), 175.0, "Walk")
			axe(g, true)
			g._walk({"who": "noah", "to": [0.4, 0.0, -78.0], "speed": 1.1, "then": "Idle"})
		"sedan_reverse":
			if is_instance_valid(g.blackcar):
				g.blackcar.lights(false)
				var tw: Tween = g.create_tween()
				tw.tween_property(g.blackcar, "speed", -4.0, 2.0)
		"cellar":   # подпол: темнота, полоски света между досок
			cellar(g)
		"knock":
			for i in 3:
				g.sfx.play("knock", -6.0, 1.0, 0.9 + float(i) * 0.55)
		"neighbor":   # задами, огородами — к соседке
			for kk in g.actors:
				(g.actors[kk] as Actor).visible = true
			var spots := [["agnes", Vector3(-17.0, 0, -50.0)], ["noah", Vector3(-17.6, 0, -48.6)], ["iris", Vector3(-18.2, 0, -47.6)],
				["eva", Vector3(-16.6, 0, -47.2)], ["danny", Vector3(-18.6, 0, -46.4)], ["zoe", Vector3(-17.2, 0, -46.0)]]
			for sp in spots:
				put(g, sp[0], sp[1], 175.0, "Walk")
				g._walk({"who": sp[0], "to": [(sp[1] as Vector3).x - 1.0, 0.0, (sp[1] as Vector3).z - 14.0], "speed": 0.9, "then": "Idle"})
			if on(g, "baby"):
				baby(g, "agnes" if not has(g, "eva") else "eva")
		_:
			return false
	return true


## Огоньки деревни вдали (сцена 1): тёмные силуэты домов и один огонёк в окне.
static func far_lights(g) -> void:
	var s: DriveScene = g.scene
	var root := Node3D.new()
	s.road_items.add_child(root)
	root.position = Vector3(0, 0, -150.0)
	var dark := StandardMaterial3D.new()
	dark.albedo_color = Color(0.04, 0.04, 0.05)
	for hx in [[-14.0, -10.0], [12.0, -4.0], [-12.0, -28.0], [13.0, -36.0]]:
		var h := MeshInstance3D.new()
		var bm := BoxMesh.new()
		bm.size = Vector3(6.0, 3.6, 7.0)
		h.mesh = bm
		h.material_override = dark
		h.position = Vector3(hx[0], 1.8, hx[1])
		root.add_child(h)
	var w := MeshInstance3D.new()
	var wm := BoxMesh.new()
	wm.size = Vector3(0.05, 0.9, 0.8)
	w.mesh = wm
	var lit := StandardMaterial3D.new()
	lit.albedo_color = Color(1.0, 0.7, 0.4)
	lit.emission_enabled = true
	lit.emission = Color(1.0, 0.68, 0.35)
	lit.emission_energy_multiplier = 4.0
	w.material_override = lit
	w.position = Vector3(-10.9, 1.7, -10.0)
	root.add_child(w)
	var ol := OmniLight3D.new()
	ol.light_color = Color(1.0, 0.7, 0.4)
	ol.light_energy = 2.0
	ol.omni_range = 7.0
	ol.light_volumetric_fog_energy = 2.0
	ol.position = Vector3(-10.4, 1.7, -10.0)
	root.add_child(ol)


## Снег из-под задних колёс.
static func snow_spray(g, enable: bool) -> void:
	var p = g.c3.get("spray")
	if enable and not (p and is_instance_valid(p)):
		var car: Node3D = g.scene.car
		var root := Node3D.new()
		car.add_child(root)
		for x in [-0.72, 0.72]:
			var sp := CPUParticles3D.new()
			sp.amount = 160
			sp.lifetime = 0.9
			sp.direction = Vector3(0, 0.6, 1)
			sp.spread = 30.0
			sp.initial_velocity_min = 2.0
			sp.initial_velocity_max = 4.5
			sp.gravity = Vector3(0, -6.0, 0)
			sp.scale_amount_min = 1.0
			sp.scale_amount_max = 2.6
			sp.mesh = g.scene.road.snow.mesh   # снежная пыль: те же мягкие хлопья, что летят в фары
			sp.position = Vector3(x, 0.15, 1.25)
			root.add_child(sp)
		g.c3["spray"] = root
	elif not enable and p and is_instance_valid(p):
		(p as Node).queue_free()
		g.c3.erase("spray")


## Идут пешком по колено в снегу: из машины — вперёд по дороге.
static func walkers(g, carry_eva: bool) -> void:
	var s: DriveScene = g.scene
	var who := cast(g)
	var i := 0
	for k in who:
		if k == "eva" and carry_eva:
			continue
		var p := s.car.to_global(Vector3(-0.9 + 0.6 * float(i % 3), 0.0, -2.6 - 0.9 * float(i / 3)))
		put(g, k, p, 180.0, "Walk_Carry" if (k == "noah" and carry_eva) else "Walk")
		g._walk({"who": k, "to": [p.x, 0.0, p.z - 26.0], "speed": 0.8 if k != "noah" else 0.75, "then": "Idle",
			"clip": "Walk_Carry" if (k == "noah" and carry_eva) else "Walk"})
		i += 1


## Ева на руках у Ноя (лежит поперёк рук).
static func carry(g) -> void:
	var noah: Actor = g.actors.noah
	var ev: Actor = g.actors.eva
	var ba := BoneAttachment3D.new()
	ba.bone_name = "spine_03"
	noah.skel.add_child(ba)
	ev.reparent(ba, false)
	ev.position = Vector3(0.0, -0.35, 0.42)
	ev.rotation_degrees = Vector3(0, 90, 90)
	hold(ev, "LayToIdle", 0.0)
	g.c3["carry"] = ba


## Лежит на спине (кровать): поза из начала «LayToIdle», замерла.
static func lie(g, who: String, p: Vector3, face: float) -> void:
	if not has(g, who):
		return
	var a: Actor = g.actors[who]
	var ba = g.c3.get("carry")
	if ba and is_instance_valid(ba) and a.get_parent() == ba:
		a.reparent(g.scene, false)
	put(g, who, p, face, "LayToIdle")
	a.rotation = Vector3(0, deg_to_rad(face), 0)
	hold(a, "LayToIdle", 0.0)


## Роды: Ева на кровати, Агнес у изножья, Айрис на коленях у изголовья.
static func restage_birth(g) -> void:
	if not has(g, "eva"):
		return
	lie(g, "eva", ag(-2.15, 0.6, 1.3), -90.0)
	put(g, "agnes", ag(-1.9, 0.0, 2.35), 180.0, "Idle")
	put(g, "iris", ag(-1.6, 0.0, 0.45), -30.0, "Crouch_Idle")
	(g.actors.iris as Actor).reach = {"_r": g._target("eva") + Vector3(0.25, -0.15, -0.1)}
	put(g, "noah", ag(0.6, 0.0, 2.5), -140.0, "Idle_FoldArms")
	put(g, "zoe", ag(0.0, 0.0, -0.3), -110.0, "Idle")


## Спящие в доме: Агнес на печи, Ева с младенцем на кровати (если жива).
static func _house_sleepers(g) -> void:
	var a := agnes(g)
	if g._scene_id.begins_with("c3_s5"):
		lie(g, "agnes", ag(-1.95, 1.95, -2.35), 0.0)
		g._expr({"who": "agnes", "set": "sleep"})
	if has(g, "eva"):
		lie(g, "eva", ag(-2.15, 0.6, 1.3), -90.0)
		g._expr({"who": "eva", "set": "sleep"})
		if on(g, "baby"):
			baby(g, "eva")
	for k in ["iris", "zoe"]:
		if has(g, k):
			Ch2.sit(g, k, ag(2.9 if k == "iris" else 1.9, 0.47, 1.6 if k == "iris" else 2.9), -90.0 if k == "iris" else 180.0)
	a.visible = true


## Младенец — свёрток на руках (у груди героя).
static func baby(g, who: String) -> void:
	var b = g.c3.get("baby")
	if b and is_instance_valid(b):
		(b as Node).queue_free()
	if not has(g, who):
		return
	var a: Actor = g.actors[who]
	var ba := BoneAttachment3D.new()
	ba.bone_name = "spine_03"
	a.skel.add_child(ba)
	var m := MeshInstance3D.new()
	var cm := CapsuleMesh.new()
	cm.radius = 0.09
	cm.height = 0.42
	m.mesh = cm
	var mat := StandardMaterial3D.new()
	mat.albedo_color = Color(0.86, 0.84, 0.78)
	mat.roughness = 0.95
	m.material_override = mat
	m.position = Vector3(0.0, 0.0, 0.2)
	m.rotation_degrees = Vector3(0, 0, 80)
	ba.add_child(m)
	var face := MeshInstance3D.new()
	var sm := SphereMesh.new()
	sm.radius = 0.055
	sm.height = 0.1
	face.mesh = sm
	var fm := StandardMaterial3D.new()
	fm.albedo_color = Color(0.86, 0.62, 0.55)
	face.material_override = fm
	face.position = Vector3(0.14, 0.03, 0.27)
	ba.add_child(face)
	g.c3["baby"] = ba


## Мамин дневник — раскрытая тетрадь в руках Айрис (строчки — тёмные полоски).
static func diary(g) -> void:
	var old = g.c3.get("diary")
	if old and is_instance_valid(old):
		(old as Node).queue_free()
	if not has(g, "iris"):
		return
	var iris: Actor = g.actors.iris
	var book := Node3D.new()   # место — каждый кадр между ладонями (process)
	village(g).add_child(book)
	var fwd := iris.global_transform.basis.z.normalized()
	var left := Vector3.UP.cross(fwd).normalized()   # левая рука героя — к +X при взгляде к +Z
	var chest := iris.global_position + Vector3(0, 1.02, 0) + fwd * 0.3
	iris.reach = {"_l": chest + left * 0.11, "_r": chest - left * 0.11}
	var paper := StandardMaterial3D.new()
	paper.albedo_color = Color(0.9, 0.87, 0.78)
	paper.roughness = 0.9
	var cover := StandardMaterial3D.new()
	cover.albedo_color = Color(0.28, 0.14, 0.09)
	cover.roughness = 0.6
	var cv := MeshInstance3D.new()   # обложка под страницами
	var cb := BoxMesh.new()
	cb.size = Vector3(0.32, 0.004, 0.225)
	cv.mesh = cb
	cv.material_override = cover
	cv.position = Vector3(0, -0.006, 0)
	book.add_child(cv)
	var hand: Font = load("res://assets/fonts/Pangolin-Regular.woff2")
	var pages := ["Была у мамы. Она говорит —\nиди к Саймону, он поможет.\nОн же врач. Он друг.", "Саймон обещал. В субботу\nночью он нас увезёт.\nНой уже собрал рюкзак."] if g.lang == "ru" \
		else ["Went to see Mom. She says —\ngo to Simon, he’ll help.\nHe’s a doctor. A friend.", "Simon promised. Saturday\nnight he’ll take us away.\nNoah already packed\nhis backpack."]
	for k in 2:
		var side := -1.0 if k == 0 else 1.0
		var pg := MeshInstance3D.new()
		var bm := BoxMesh.new()
		bm.size = Vector3(0.152, 0.005, 0.21)
		pg.mesh = bm
		pg.material_override = paper
		pg.position = Vector3(side * 0.078, 0.0, 0.0)
		pg.rotation_degrees = Vector3(0, 0, -side * 6.0)
		book.add_child(pg)
		var lb := Label3D.new()   # строки почерком — лицом вверх (к лицу Айрис), сверху страницы
		lb.text = pages[k]
		lb.font = hand
		lb.font_size = 64
		lb.pixel_size = 0.00015   # строка «Была у мамы. Она говорит —» ≈ 11 см — в ширину страницы
		lb.modulate = Color(0.13, 0.16, 0.36)
		lb.outline_size = 0
		lb.shaded = true
		lb.double_sided = false
		lb.horizontal_alignment = HORIZONTAL_ALIGNMENT_LEFT
		lb.vertical_alignment = VERTICAL_ALIGNMENT_TOP
		lb.position = Vector3(0.0, 0.0029, -0.085)
		lb.rotation_degrees = Vector3(-90, 0, 0)
		pg.add_child(lb)
	g.c3["diary"] = book


## Топор в правой руке Ноя.
static func axe(g, show: bool) -> void:
	var b = g.c3.get("axe")
	if show and not (b and is_instance_valid(b)):
		var noah: Actor = g.actors.noah
		var ba := BoneAttachment3D.new()
		ba.bone_name = "hand_r"
		noah.skel.add_child(ba)
		var ax := axe_mesh()
		ax.position = Vector3(0.0, 0.06, 0.02)
		ax.rotation_degrees = Vector3(0, 0, 180)
		ba.add_child(ax)
		g.c3["axe"] = ba
	elif not show and b and is_instance_valid(b):
		(b as Node).queue_free()
		g.c3.erase("axe")


static func axe_mesh() -> Node3D:
	var n := Node3D.new()
	var h := MeshInstance3D.new()
	var hm := CylinderMesh.new()
	hm.top_radius = 0.016
	hm.bottom_radius = 0.019
	hm.height = 0.72
	h.mesh = hm
	var wood := StandardMaterial3D.new()
	wood.albedo_color = Color(0.45, 0.32, 0.18)
	wood.roughness = 0.7
	h.material_override = wood
	h.position = Vector3(0, 0.3, 0)
	n.add_child(h)
	var head := MeshInstance3D.new()
	var bm := BoxMesh.new()
	bm.size = Vector3(0.03, 0.09, 0.18)
	head.mesh = bm
	var steel := StandardMaterial3D.new()
	steel.albedo_color = Color(0.32, 0.33, 0.34)
	steel.metallic = 0.8
	steel.roughness = 0.35
	head.material_override = steel
	head.position = Vector3(0, 0.63, 0.06)
	n.add_child(head)
	return n


## Канистра в левой руке Ноя.
static func canister(g, show: bool) -> void:
	var b = g.c3.get("can")
	if show and not (b and is_instance_valid(b)):
		var noah: Actor = g.actors.noah
		var ba := BoneAttachment3D.new()
		ba.bone_name = "hand_l"
		noah.skel.add_child(ba)
		var m := MeshInstance3D.new()
		var bm := BoxMesh.new()
		bm.size = Vector3(0.14, 0.34, 0.26)
		m.mesh = bm
		var mat := StandardMaterial3D.new()
		mat.albedo_color = Color(0.55, 0.08, 0.05)
		mat.roughness = 0.45
		m.material_override = mat
		m.position = Vector3(0.0, 0.2, 0.0)
		ba.add_child(m)
		g.c3["can"] = ba
	elif not show and b and is_instance_valid(b):
		(b as Node).queue_free()
		g.c3.erase("can")


## Кружка отвара в руке Ноя.
static func mug(g, show: bool) -> void:
	var b = g.c3.get("mug")
	if show and not (b and is_instance_valid(b)):
		var noah: Actor = g.actors.noah
		var ba := BoneAttachment3D.new()
		ba.bone_name = "hand_r"
		noah.skel.add_child(ba)
		var m := MeshInstance3D.new()
		var cm := CylinderMesh.new()
		cm.top_radius = 0.042
		cm.bottom_radius = 0.038
		cm.height = 0.1
		m.mesh = cm
		var mat := StandardMaterial3D.new()
		mat.albedo_color = Color(0.3, 0.45, 0.62)
		mat.roughness = 0.3
		m.material_override = mat
		m.position = Vector3(0.0, 0.08, 0.04)
		ba.add_child(m)
		g.c3["mug"] = ba
	elif not show and b and is_instance_valid(b):
		(b as Node).queue_free()
		g.c3.erase("mug")


## Трубка Агнес: огонёк и дымок.
static func pipe(g, show: bool) -> void:
	var b = g.c3.get("pipe")
	if show and not (b and is_instance_valid(b)) and has(g, "agnes"):
		var a: Actor = g.actors.agnes
		var ba := BoneAttachment3D.new()
		ba.bone_name = "head"
		a.skel.add_child(ba)
		var mat := StandardMaterial3D.new()   # тёмное дерево (кость головы: лицо — к +Z, макушка — к +Y)
		mat.albedo_color = Color(0.3, 0.17, 0.08)
		mat.roughness = 0.45
		var dir := Vector3(0.18, -0.42, 0.89).normalized()   # из угла рта — вперёд и вниз
		var start := Vector3(0.024, 0.012, 0.1)
		var stem := MeshInstance3D.new()
		var cm := CylinderMesh.new()
		cm.top_radius = 0.0045
		cm.bottom_radius = 0.0055
		cm.height = 0.085
		stem.mesh = cm
		stem.material_override = mat
		stem.transform = Transform3D(_basis_y(dir), start + dir * 0.0425)
		ba.add_child(stem)
		var bowl := MeshInstance3D.new()   # чашечка — стоит торчком на конце мундштука
		var bm := CylinderMesh.new()
		bm.top_radius = 0.015
		bm.bottom_radius = 0.012
		bm.height = 0.04
		bowl.mesh = bm
		bowl.material_override = mat
		var tip := start + dir * 0.085
		bowl.position = tip + Vector3(0, 0.014, 0.006)
		ba.add_child(bowl)
		var ash := MeshInstance3D.new()   # тлеющий табак
		var am := CylinderMesh.new()
		am.top_radius = 0.012
		am.bottom_radius = 0.012
		am.height = 0.004
		ash.mesh = am
		var amat := StandardMaterial3D.new()
		amat.albedo_color = Color(0.25, 0.08, 0.03)
		amat.emission_enabled = true
		amat.emission = Color(1.0, 0.35, 0.08)
		amat.emission_energy_multiplier = 0.6
		ash.material_override = amat
		ash.position = bowl.position + Vector3(0, 0.0185, 0)
		ba.add_child(ash)
		var ember := OmniLight3D.new()
		ember.light_color = Color(1.0, 0.4, 0.1)
		ember.light_energy = 0.05
		ember.omni_range = 0.22
		ember.position = ash.position + Vector3(0, 0.02, 0)
		ba.add_child(ember)
		g.c3["pipe"] = ba
		g.c3["ember"] = ember
	elif not show and b and is_instance_valid(b):
		(b as Node).queue_free()
		g.c3.erase("pipe")
		g.c3.erase("ember")


## Базис, у которого ось Y смотрит по dir (цилиндр вдоль направления).
static func _basis_y(dir: Vector3) -> Basis:
	var y := dir.normalized()
	var x := y.cross(Vector3.FORWARD if absf(y.dot(Vector3.FORWARD)) < 0.95 else Vector3.RIGHT).normalized()
	return Basis(x, y, x.cross(y).normalized())


static func pipe_puff(g) -> void:
	var e = g.c3.get("ember")
	if e and is_instance_valid(e):
		var tw: Tween = g.create_tween()
		tw.tween_property(e, "light_energy", 0.22, 0.4)
		tw.tween_property(e, "light_energy", 0.05, 1.0)


## Следы на снегу от крыльца — в сторону трассы.
static func tracks(g) -> void:
	var v := village(g)
	var mat := StandardMaterial3D.new()
	mat.albedo_color = Color(0.55, 0.6, 0.7)
	mat.roughness = 1.0
	for i in 26:
		var m := MeshInstance3D.new()
		var pm := PlaneMesh.new()
		pm.size = Vector2(0.11, 0.24)
		m.mesh = pm
		m.material_override = mat
		var z := -42.0 + float(i) * 1.4
		m.position = Vector3(-4.5 + float(i) * 0.15 + (0.12 if i % 2 else -0.12), 0.012, z)
		v.add_child(m)


## Подпол: тёмная яма под домом, лестница, полоски света между досок; все — на корточках.
static func cellar(g) -> void:
	var v := village(g)
	var root := Node3D.new()
	v.add_child(root)
	g.c3["cellar"] = root
	var base := ag(0.0, -2.6, -1.0)
	var dark := StandardMaterial3D.new()
	dark.albedo_color = Color(0.09, 0.07, 0.05)
	dark.roughness = 0.95
	for w in [[Vector3(3.0, 0.1, 3.0), Vector3(0, 0, 0)], [Vector3(3.0, 2.3, 0.1), Vector3(0, 1.15, -1.5)], [Vector3(3.0, 2.3, 0.1), Vector3(0, 1.15, 1.5)],
			[Vector3(0.1, 2.3, 3.0), Vector3(-1.5, 1.15, 0)], [Vector3(0.1, 2.3, 3.0), Vector3(1.5, 1.15, 0)], [Vector3(3.0, 0.08, 3.0), Vector3(0, 2.3, 0)]]:
		var m := MeshInstance3D.new()
		var bm := BoxMesh.new()
		bm.size = w[0]
		m.mesh = bm
		m.material_override = dark
		m.position = base + (w[1] as Vector3)
		root.add_child(m)
	var lit := StandardMaterial3D.new()
	lit.albedo_color = Color(1.0, 0.8, 0.55)
	lit.emission_enabled = true
	lit.emission = Color(1.0, 0.75, 0.45)
	lit.emission_energy_multiplier = 2.0
	for k in 4:
		var s := MeshInstance3D.new()
		var bm := BoxMesh.new()
		bm.size = Vector3(2.6, 0.01, 0.012)
		s.mesh = bm
		s.material_override = lit
		s.position = base + Vector3(0, 2.25, -0.9 + float(k) * 0.6)
		root.add_child(s)
	var sl := SpotLight3D.new()
	sl.position = base + Vector3(0, 2.2, 0)
	sl.rotation_degrees = Vector3(-90, 0, 0)
	sl.light_color = Color(1.0, 0.8, 0.55)
	sl.light_energy = 0.6
	sl.spot_angle = 50.0
	sl.spot_range = 3.0
	root.add_child(sl)
	var i := 0
	for k in ["noah", "iris", "danny", "zoe", "eva", "agnes"]:
		if has(g, k):
			var p := base + Vector3(-0.9 + 0.6 * float(i % 4), 0.05, -0.6 + 0.8 * float(i / 4))
			put(g, k, p, 0.0, "Crouch_Idle")
			i += 1
	if on(g, "baby"):
		baby(g, "eva" if has(g, "eva") else "agnes")


static func process(g, _delta: float) -> void:
	var dn = g.c3.get("diary")
	if dn and is_instance_valid(dn) and has(g, "iris"):   # раскрытая тетрадь — между ладонями, страницы к лицу
		var iris: Actor = g.actors.iris
		var sk := iris.skel
		var hl := (sk.global_transform * sk.get_bone_global_pose(sk.find_bone("hand_l"))).origin
		var hr := (sk.global_transform * sk.get_bone_global_pose(sk.find_bone("hand_r"))).origin
		var fwd := iris.global_transform.basis.z
		fwd = Vector3(fwd.x, 0.0, fwd.z).normalized()
		var right := fwd.cross(Vector3.UP).normalized()
		var n := (Vector3.UP * 0.75 - fwd * 0.66).normalized()
		(dn as Node3D).global_transform = Transform3D(Basis(right, n, right.cross(n)), (hl + hr) * 0.5 + fwd * 0.07 + n * 0.065)


## Планы главы 3. true — план поставлен.
static func cam(g, shot: String) -> bool:
	if not String(g._scene_id).begins_with("c3"):
		return false
	var s: DriveScene = g.scene
	var c: Camera3D = g.cam
	if Village.SHOTS.has(shot) and vil(g):
		var w: Array = Village.SHOTS[shot]
		c.fov = w[2]
		c.look_at_from_position(w[0], w[1])
		return true
	match shot:
		"village_far":   # глазами Ноя — сквозь буран, огонёк вдали
			c.fov = 30.0
			c.look_at_from_position(s.car.to_global(Vector3(-0.36, 1.2, 0.2)), s.car.to_global(Vector3(-4.0, 1.6, -150.0)))
		"wheel":   # снаружи, низко: заднее колесо в сугробе
			c.fov = 44.0
			c.look_at_from_position(s.car.to_global(Vector3(-2.3, 0.55, 3.0)), s.car.to_global(Vector3(-0.8, 0.35, 1.2)))
		"wheel_out":   # Ной на коленях у колеса
			c.fov = 46.0
			c.look_at_from_position(s.car.to_global(Vector3(-2.6, 1.3, -0.4)), s.car.to_global(Vector3(-1.1, 0.5, 1.15)))
		"eva_ditch":   # из-за нашей машины, в свете фар — машина Евы в кювете
			var e = g.c3.get("eva_car")
			if not (e and is_instance_valid(e)):
				return false
			c.fov = 40.0
			c.look_at_from_position(s.car.to_global(Vector3(-1.7, 1.75, 3.6)), (e as Node3D).global_position + Vector3(0, 0.6, 0))
		"eva_ditch_in":   # сквозь боковое стекло — лицо Евы
			return face(g, "eva", 30.0, 0.9, 0.05, 34.0)
		"noah_out", "iris_out", "storm_noah", "axe_face", "agnes", "agnes_window", "agnes_candle":
			var who: String = {"noah_out": "noah", "iris_out": "iris", "storm_noah": "noah", "axe_face": "noah", "agnes": "agnes",
				"agnes_window": "agnes", "agnes_candle": "agnes"}[shot]
			return face(g, who, 25.0, 1.15, 0.0, 36.0)
		"walk_snow":   # сзади, сквозь снег — идут к огоньку
			if not has(g, "noah"):
				return false
			var n: Actor = g.actors.noah
			c.fov = 50.0
			c.look_at_from_position(n.global_position + Vector3(1.6, 1.9, 4.5), n.global_position + Vector3(0, 1.0, -6.0))
		"agnes_sleep":   # спит на печи: с уровня лежанки, из комнаты
			if not has(g, "agnes"):
				return false
			var ah: Vector3 = g._target("agnes")
			c.fov = 32.0
			c.look_at_from_position(ah + Vector3(0.62, 0.06, 0.22), ah)
		"eva_bed":   # лежит: камера сверху, чуть сбоку от лица
			var who := "eva" if shot == "eva_bed" else "agnes"
			if not has(g, who):
				return false
			var hp: Vector3 = g._target(who)
			var bed: Vector3 = ag(-2.2, 0.6, 1.3)
			var to := Vector3(bed.x - hp.x, 0.0, bed.z - hp.z)
			to = to.normalized() if to.length() > 0.05 else Vector3(1, 0, 0)
			c.fov = 38.0
			c.look_at_from_position(hp + to * 0.42 + Vector3(0, 0.58, 0), hp + Vector3(0.0, -0.04, 0.0))
		"iris_bed":   # через кровать — лицо Айрис, Ева на подушке на переднем плане
			if not has(g, "iris"):
				return false
			var hp: Vector3 = g._target("iris")
			var fz: Vector3 = (g.actors.iris as Actor).global_transform.basis.z
			var fw := Vector3(fz.x, 0.0, fz.z).normalized()
			var ev: Vector3 = g._target("eva") if has(g, "eva") else hp + fw   # прямо перед ней — Ева: камера сбоку, подальше от неё
			var p1 := hp + fw.rotated(Vector3.UP, deg_to_rad(70.0)) * 0.7
			var p2 := hp + fw.rotated(Vector3.UP, deg_to_rad(-70.0)) * 0.7
			var cp := p1 if p1.distance_to(ev) > p2.distance_to(ev) else p2
			c.fov = 40.0
			c.look_at_from_position(cp + Vector3(0, 0.06, 0), hp + Vector3(0, -0.04, 0))
		"agnes_stove":
			return face(g, "agnes", 32.0, 1.0, 0.02, 36.0)
		"diary_page":   # страница дневника — крупно, сверху
			var dn = g.c3.get("diary")
			if not (dn and is_instance_valid(dn)):
				return false
			var dt: Transform3D = (dn as Node3D).global_transform
			c.fov = 36.0
			c.look_at_from_position(dt.origin + dt.basis.y * 0.42 - dt.basis.z * 0.02, dt.origin)
		"noah_house", "iris_house", "zoe_house", "noah_stove", "agnes_bed", "danny_window", "danny_door":
			var who := shot.get_slice("_", 0)
			var ang := -28.0 if shot.ends_with("stove") else 22.0
			return face(g, who, ang, 1.05, 0.02, 36.0)
		"street_walk":   # за спиной Ноя — улица, в конце седан
			if not has(g, "noah"):
				return false
			var n: Actor = g.actors.noah
			c.fov = 40.0
			c.look_at_from_position(n.global_position + Vector3(0.9, 1.75, 3.4), Vector3(1.5, 1.0, -97.0))
		"cellar":
			var r = g.c3.get("cellar")
			if not (r and is_instance_valid(r)):
				return false
			var b := ag(0.0, -2.6, -1.0)
			c.fov = 54.0
			c.look_at_from_position(b + Vector3(1.2, 1.3, 1.2), b + Vector3(-0.4, 0.5, -0.2))
		_:
			return false
	return true


## Лицо героя: камера перед ним под углом ang (градусы, вокруг вертикали), на расстоянии dist.
static func face(g, who: String, ang: float, dist: float, dy: float, fov: float) -> bool:
	if not has(g, who):
		return false
	var a: Actor = g.actors[who]
	var hp: Vector3 = g._target(who)
	var fwd := a.global_transform.basis.z
	fwd.y = 0.0
	fwd = fwd.normalized().rotated(Vector3.UP, deg_to_rad(ang))
	g.cam.fov = fov
	g.cam.look_at_from_position(hp + fwd * dist + Vector3(0, dy, 0), hp + Vector3(0, -0.04, 0))
	return true
