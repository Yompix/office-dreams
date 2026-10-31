class_name Ch5
## Глава 5 «Озеро» и пять концовок (data/ch5.json ← tools/lake/story/ch5.py): дача (ключ под третьей ступенькой, мамин плащ,
## печь, разговоры), таблетки в последний раз, воспоминание в сарае — летняя ночь одиннадцать лет назад (мама, отец,
## молодой Хейл, первая таблетка), Хейл на льду у тёмного пятна, полынья, берег (Зоя в снегу), патруль; эпилоги:
## комната для свиданий, камера со стальным зеркалом, весна на мостках, лето на мостках, белая комната.
## Место — озеро главы 4 (Ch4.lake, местные координаты: x — к озеру, кромка — x −8); комнаты эпилогов — далеко в стороне.

const P := Vector3(7.0, Lake.ICE_Y, 10.0)        # середина тёмного пятна у мостков (ключи бьют — лёд не держит)
const HOLE := Vector3(7.6, Lake.ICE_Y, 11.0)     # полынья
const PW := Ch4.LAKE + P                          # то же в мировых координатах (для героев и камер)
const HOLEW := Ch4.LAKE + HOLE
const ROOMS := Vector3(0.0, 0.0, 700.0)          # комнаты эпилогов: свидания, камера, белая комната
const DX := -20.0                                 # дача: середина, пол — y 0.6, фасад к озеру (+x)
const DF := 0.6


static func L(x: float, y: float, z: float) -> Vector3:
	return Ch4.LAKE + Vector3(x, y, z)


static func D(x: float, y: float, z: float) -> Vector3:   # в даче: местные координаты дома
	return Ch4.LAKE + Vector3(DX + x, DF + y, z)


static func on(g, f: String) -> bool:
	return float(g.flags.get(f, 0)) > 0.0


static func has(g, who: String) -> bool:
	return g.actors.has(who) and is_instance_valid(g.actors[who]) and (g.actors[who] as Actor).visible


static func put(g, who: String, p: Vector3, face: float, clip := "Idle") -> void:
	if g.actors.has(who) and is_instance_valid(g.actors[who]):
		Ch3.put(g, who, p, face, clip)


static func face(g, who: String, ang := 22.0, dist := 1.0, dy := 0.02, fov := 38.0) -> bool:
	return Ch4.face(g, who, ang, dist, dy, fov)


static func lake(g) -> Lake:
	return Ch4.lake(g)


## Кто на даче: Ной, Айрис; бабушка, Ева, Дэнни, Зоя — кто дошёл. Заложники Хейла приедут с ним.
static func cast(g) -> Array:
	var who := Ch4.cast(g)
	if on(g, "agnes_dead"):
		who.erase("agnes")
		if g.actors.has("agnes") and is_instance_valid(g.actors.agnes):
			(g.actors.agnes as Actor).visible = false
	for k in ["orderly", "hale", "noah_kid", "mom", "victor", "hale_young"]:
		if g.actors.has(k) and is_instance_valid(g.actors[k]):
			(g.actors[k] as Actor).visible = false
	return who


static func looks_off(g) -> void:
	Ch4._looks_off(g)


static func restore(g, id: String) -> void:
	g.c5["on"] = true
	if id in ["c5_recap", "c5_s1"]:
		return
	_dacha_stage(g)
	if id.begins_with("c5_s3") or id.begins_with("c5_s4") or id.begins_with("c5_mama") or id.begins_with("c5_shore"):
		_ice_stage(g)


static func fx(g, k: String) -> bool:
	if not String(g._scene_id).begins_with("c5"):
		return false
	var c5: Dictionary = g.c5
	match k:
		"c5_start":   # утро: дача по окна в снегу
			c5["on"] = true
			_dacha_stage(g)
			put(g, "noah", L(-12.6, 0.0, 1.6), -100.0, "Walk")
			put(g, "iris", L(-12.2, 0.0, 3.0), -110.0, "Idle_FoldArms")
		"key":
			put(g, "noah", L(-13.8, 0.0, 0.4), -90.0, "Crouch_Idle")
			_key(g)
			g.sfx.play("trunk", -18.0, 1.6)
		"boards":
			put(g, "noah", L(-16.4, DF, 2.0), -90.0, "Interact")
			_boards(g)
			g.sfx.play("blow", -10.0, 1.3)
			g.sfx.play("blow", -12.0, 1.1, 0.5)
		"dacha_in":
			_inside(g)
		"wall_drawing", "coat_touch":
			if k == "coat_touch":
				put(g, "iris", D(2.25, 0.0, -2.2), 40.0, "Idle")
				(g.actors.iris as Actor).reach = {"_r": D(2.7, 1.35, -2.55)}
		"stove5":
			var who := "agnes" if not on(g, "agnes_dead") else "noah"
			put(g, who, D(-1.2, 0.0, -1.4), -130.0, "Fixing_Kneeling")
			_stove_fire(g, true)
			g.sfx.play("blow", -16.0, 0.8)
		"iris_frost":
			put(g, "iris", D(2.55, 0.0, 2.0), 90.0, "Idle")
			(g.actors.iris as Actor).reach = {"_r": D(2.95, 1.5, 2.1)}
		"agnes_up":   # бабушка встаёт от печи и говорит в комнату (у устья камера стояла бы в печи)
			put(g, "agnes", D(-0.5, 0.0, -1.3), 75.0, "Idle")
		"hub_in":   # после разговора на крыльце (или у печи) — снова в комнате
			for who in ["noah", "zoe"]:
				if has(g, who) and (g.actors[who] as Actor).visible:
					var ap: Vector3 = (g.actors[who] as Actor).global_position
					var far := Vector2(ap.x - D(0, 0, 0).x, ap.z - D(0, 0, 0).z).length() > 3.2
					var at_stove: bool = who == "noah" and ap.distance_to(D(-1.2, 0.0, -1.4)) < 0.4
					if far or at_stove:
						put(g, who, D(1.6, 0, 0.4) if who == "noah" else D(2.2, 0, 1.2), -110.0 if who == "noah" else -120.0, "Idle")
		"zoe_porch":
			put(g, "zoe", L(-16.2, DF, -0.9), 70.0, "Idle")
			put(g, "noah", L(-15.6, DF, 0.6), -110.0, "Idle")
			_hook(g, "zoe")
		"zoe_hand":
			(g.actors.zoe as Actor).reach = {"_r": g._target("noah") + Vector3(0, -0.75, 0)}
		"danny_rope":
			put(g, "danny", D(1.6, 0.0, 0.8), -60.0, "Idle")
		"head_ring":
			put(g, "noah", D(1.0, 0.0, -0.2), 70.0, "Idle")
			g.hud.hum_to = 0.6
			g.get_tree().create_timer(3.5).timeout.connect(func(): g.hud.hum_to = 0.0)
		"bottle5":
			Ch2.pill_bottle(g, true)
		"iris_burns", "burn5":
			Ch2.pill_bottle(g, false)
			var tw: Tween = g.create_tween()
			var f = c5.get("fire")
			if f and is_instance_valid(f):
				tw.tween_property(f, "light_energy", 4.5, 0.2)
				tw.tween_property(f, "light_energy", 1.4, 1.2)
		"take5":
			Ch2.pill_bottle(g, false)
			g.sfx.play("pills", -10.0)
		"gap_ice", "kneel_gap":
			lake(g).shed_door(true)   # Ной вошёл — дверь сарая открыта (бабушка встанет в проёме)
			_shed_inside(g)
		"summer_night":   # воспоминание: та же дача летней ночью одиннадцать лет назад
			_summer_night(g, true)
		"mom_hum":
			g.sfx.play("lullaby", -8.0, 1.0)
		"road_lights":
			_memory_car(g, "victor")
		"hum_stop":
			g.sfx.stop("lullaby", 0.6)
		"victor_walk":
			put(g, "victor", L(-7.5, 0.42, 7.0), 90.0, "Walk")
			g._walk({"who": "victor", "to": _a(L(1.6, 0.42, 7.0)), "speed": 1.0, "then": "Idle"})
			(g.actors.mom as Actor).look_target = g._target("victor")
		"kick_bag":
			put(g, "victor", L(1.6, 0.42, 7.1), 90.0, "Idle")
			_kick_bag(g)
		"grab_hair":
			put(g, "mom", L(3.3, 0.42, 6.85), -90.0, "Crouch_Idle")
			put(g, "victor", L(2.7, 0.42, 7.0), 90.0, "Idle")
			(g.actors.victor as Actor).reach = {"_r": g._target("mom") + Vector3(0.0, 0.05, 0.0)}
			(g.actors.mom as Actor).expr = (g.EXPR["fear"] as Dictionary).duplicate()
		"mom_turns":
			(g.actors.mom as Actor).look_target = L(-9.6, 1.1, 10.9)
		"drown":
			_drown(g)
		"eyes_shut":
			g.hud.fade.color = Color(0, 0, 0, 1)
			g.sfx.play("splash", -4.0)
			g.sfx.play("splash", -8.0, 0.9, 1.1)
			g.get_tree().create_timer(3.4).timeout.connect(func(): g.create_tween().tween_property(g.hud.fade, "color:a", 0.0, 1.0))
			_drown(g)
		"after_drown":
			g.hud.fade.color.a = 0.0
			if g.actors.has("mom"):
				(g.actors.mom as Actor).visible = false
			put(g, "victor", L(3.0, 0.42, 6.6), 90.0, "Rk_SitIdle")
			Ch2.sit(g, "victor", L(3.0, 0.47, 6.6), 90.0)
			(g.actors.victor as Actor).reach = {}
		"young_hale":
			_memory_car(g, "hale")
			put(g, "victor", L(-14.0, 0.0, 11.0), -90.0, "Idle")
		"shed_door":   # Хейл открывает дверь сарая
			lake(g).shed_door(true)
			put(g, "hale_young", L(-7.9, 0.28, 13.0), -90.0, "Idle")
			_kid(g, L(-9.6, 0.28, 12.4), 90.0)
		"hale_crouch":
			put(g, "hale_young", L(-8.6, 0.28, 12.9), -90.0, "Crouch_Idle")
		"first_pill":
			var h: Actor = g.actors.hale_young
			h.reach = {"_r": g._target("noah_kid") + Vector3(0.12, -0.12, 0.0)}
		"winter_back":
			_summer_night(g, false)
			_shed_inside(g)
		"iris_lean":
			put(g, "iris", L(-9.3, 0.28, 11.8), 160.0, "Crouch_Idle")
			(g.actors.iris as Actor).reach = {"_l": g._target("noah") + Vector3(0, -0.2, 0)}
		"agnes_door5":
			put(g, "agnes", L(-8.1, 0.28, 13.0), -90.0, "Idle_FoldArms")
		"sedan_arrives":
			_ice_stage(g)
			_sedan(g)
		"noah_ice":
			put(g, "noah", PW + Vector3(2.4, 0.0, 1.2), -120.0, "Walk")
		"hale_ice":
			put(g, "hale", PW + Vector3(0.4, 0.0, 3.6), 150.0, "Walk_Formal")
			Ch4._hale_glasses(g)
			put(g, "noah", PW + Vector3(2.4, 0.0, 1.2), -60.0, "Idle")
			(g.actors.noah as Actor).look_target = g._target("hale")
		"tear_pages":
			Ch4._diary(g, "hale")
		"lighter", "burn_pages":
			_pages_fire(g, true)
		"diary_throw":
			_pages_fire(g, false)
			var dd = g.c4.get("diary")
			if dd and is_instance_valid(dd):
				(dd as Node).queue_free()
		"iris_ice":
			put(g, "iris", PW + Vector3(3.6, 0.0, 3.0), -120.0, "Idle_FoldArms")
		"agnes_out":   # бабушка выбежала на берег (без колыбельной её там не было — камера улетала)
			if has(g, "agnes"):
				put(g, "agnes", L(-10.5, 0.0, 4.5), 70.0, "Idle")
		"agnes_sings":
			put(g, "agnes", L(-10.5, 0.0, 4.5), 70.0, "Idle")
			g.sfx.play("lullaby", -10.0, 0.94)
		"hale_elbow5":
			put(g, "hale", PW + Vector3(2.0, 0.0, 1.9), 180.0, "Idle")
			(g.actors.hale as Actor).reach = {"_r": g._target("noah") + Vector3(0.1, -0.75, -0.1)}
		"hale_breaks":
			(g.actors.hale as Actor).expr = (g.EXPR["sad"] as Dictionary).duplicate()
		"hale_grabs":
			put(g, "hale", PW + Vector3(3.0, 0.0, 2.5), -90.0, "Idle")
			(g.actors.hale as Actor).reach = {"_r": g._target("iris") + Vector3(0.0, -0.5, 0.05)}
		"iris_back":
			put(g, "iris", HOLEW + Vector3(-0.3, 0.0, -0.2), 60.0, "Idle")
			put(g, "hale", HOLEW + Vector3(0.6, 0.0, 0.5), -120.0, "Walk")
			(g.actors.hale as Actor).reach = {}
		"smile":
			var n: Actor = g.actors.noah
			n.expr = {"mouthSmileLeft": 0.42, "mouthSmileRight": 0.38, "cheekSquintLeft": 0.1}
			n.blink_t = 1e9
			g.sfx.play("hum_ring", -14.0)
		"hale_backs":
			put(g, "hale", HOLEW + Vector3(0.7, 0.0, 0.6), 60.0, "Walk")
			put(g, "noah", PW + Vector3(2.0, 0.0, 2.2), -120.0, "Walk")
		"iris_sleeve":
			(g.actors.iris as Actor).reach = {"_r": g._target("noah") + Vector3(0, -0.6, 0)}
		"iris_falls":
			(g.actors.iris as Actor).reach = {}
			put(g, "iris", HOLEW + Vector3(-0.4, 0.0, -0.3), 60.0, "LayToIdle")
			Ch3.hold(g.actors.iris, "LayToIdle", 0.0)
		"eyes_close":
			(g.actors.noah as Actor).expr = {"eyeBlinkLeft": 1.0, "eyeBlinkRight": 1.0}
		"hale_pales":
			(g.actors.hale as Actor).expr = (g.EXPR["shock"] as Dictionary).duplicate()
			put(g, "hale", HOLEW + Vector3(0.5, 0.0, 0.3), 60.0, "Walk")
		"crack":
			_hole(g, true)
			g.sfx.play("ice_crack", -2.0)
			g._shake = 0.5
		"in_water", "ice_takes":
			_hole(g, true)
			_in_water(g, "hale", HOLEW + Vector3(0.45, 0.0, 0.35), -140.0)
			if k == "in_water" or on(g, "smiled"):
				_in_water(g, "iris", HOLEW + Vector3(-0.35, 0.0, -0.25), 40.0)
			g.sfx.play("splash", -2.0)
		"crawl":
			put(g, "noah", HOLEW + Vector3(1.9, 0.0, 1.5), -130.0, "Crouch_Idle")
			if not on(g, "agnes_dead"):
				put(g, "agnes", L(-6.0, 0.0, 6.0), 70.0, "Idle")
		"grab_iris", "grab_hale", "reach_hale", "hand_edge":
			var who := "iris" if k == "grab_iris" else "hale"
			put(g, "noah", HOLEW + Vector3(1.3, 0.0, 1.0), -130.0, "Crouch_Idle")
			if has(g, who):
				(g.actors.noah as Actor).reach = {"_r": g._target(who) + Vector3(0.0, -0.25, 0.0)}
				(g.actors[who] as Actor).reach = {"_r": g._target("noah") + Vector3(0.0, -0.5, 0.0), "_l": HOLEW + Vector3(0.9, 0.05, 0.7)}
		"iris_out", "both_out", "hale_out":
			var who := "hale" if k == "hale_out" else "iris"
			if has(g, who):
				put(g, who, HOLEW + Vector3(2.4, 0.0, 2.2) + (Vector3(0.8, 0, -0.4) if who == "hale" else Vector3.ZERO), -40.0, "LayToIdle")
				Ch3.hold(g.actors[who], "LayToIdle", 0.0)
			if k == "both_out" and has(g, "hale"):
				put(g, "hale", HOLEW + Vector3(3.2, 0.0, 1.6), 20.0, "LayToIdle")
				Ch3.hold(g.actors.hale, "LayToIdle", 0.0)
			(g.actors.noah as Actor).reach = {}
			g.sfx.play("splash", -10.0, 1.2)
		"hale_slips", "noah_under", "iris_gone", "iris_slips":
			var who: String = {"hale_slips": "hale", "noah_under": "noah", "iris_gone": "iris", "iris_slips": "iris"}[k]
			if k == "noah_under":
				_in_water(g, "noah", HOLEW + Vector3(0.1, 0.0, 0.1), 0.0)
				g.get_tree().create_timer(1.2).timeout.connect(func():
					if g.actors.has("noah"):
						(g.actors.noah as Actor).visible = false)
				if g.actors.has("hale"):
					g.get_tree().create_timer(1.2).timeout.connect(func():
						if g.actors.has("hale"):
							(g.actors.hale as Actor).visible = false)
			elif k != "iris_slips" and g.actors.has(who):
				var a: Actor = g.actors[who]
				g.create_tween().tween_property(a, "position:y", a.position.y - 1.5, 1.6)
				g.get_tree().create_timer(1.6).timeout.connect(func():
					if is_instance_valid(a):
						a.visible = false)
			g.sfx.play("splash", -8.0, 0.8)
		"edge_breaks":
			g.sfx.play("ice_crack", -6.0, 1.2)
			if g.actors.has("iris"):
				put(g, "iris", HOLEW + Vector3(2.2, 0.0, 2.0), -40.0, "LayToIdle")
				Ch3.hold(g.actors.iris, "LayToIdle", 0.0)
		"dive":
			_in_water(g, "noah", HOLEW, 0.0)
			put(g, "iris", HOLEW + Vector3(2.2, 0.0, 2.0), -40.0, "LayToIdle")
			Ch3.hold(g.actors.iris, "LayToIdle", 0.0)
		"boot":
			put(g, "noah", HOLEW + Vector3(1.0, 0.0, 0.8), -130.0, "Idle")
		"iris_sinking":
			if g.actors.has("iris"):
				var a: Actor = g.actors.iris
				var tw: Tween = g.create_tween().set_loops(3)
				tw.tween_property(a, "position:y", Lake.ICE_Y - 1.7, 0.6)
				tw.tween_property(a, "position:y", Lake.ICE_Y - 1.3, 0.6)
		"wake_up":
			(g.actors.noah as Actor).expr = {}
			(g.actors.noah as Actor).blink_t = 1.0
			put(g, "noah", HOLEW + Vector3(1.4, 0.0, 1.1), -130.0, "Crouch_Idle")
			put(g, "iris", HOLEW + Vector3(2.4, 0.0, 2.2), -40.0, "LayToIdle")
			Ch3.hold(g.actors.iris, "LayToIdle", 0.0)
		"eyes_clear":
			(g.actors.noah as Actor).expr = {}
		"shore":
			_hole(g, true)
			if g.actors.has("noah") and (g.actors.noah as Actor).visible:
				put(g, "noah", L(-9.0, 0.0, 16.5), -60.0, "Idle")
			for kk in ["iris", "agnes", "eva", "danny", "zoe"]:
				if has(g, kk):
					put(g, kk, L(-12.0 - float(["iris", "agnes", "eva", "danny", "zoe"].find(kk)) * 0.8, 0.0, 18.5), -40.0, "Idle")
		"unlock":
			put(g, "noah", L(-13.2, 0.0, 19.2), 30.0, "Interact")
			_hostages(g, true)
		"orderly_flees":
			_hostages(g, true)
			if g.blackcar and is_instance_valid(g.blackcar):
				var bc: BlackCar = g.blackcar
				bc.lights(true)
				g.create_tween().tween_property(bc, "position", bc.position + Vector3(-40.0, 0.0, 30.0), 4.0).set_ease(Tween.EASE_IN)
			if g.actors.has("orderly"):
				(g.actors.orderly as Actor).visible = false
			g.sfx.play("engine_start", -8.0, 1.0)
		"zoe_falls":
			put(g, "zoe", L(-11.4, 0.0, 19.6), 0.0, "LayToIdle")
			Ch3.hold(g.actors.zoe, "LayToIdle", 0.0)
			g._expr({"who": "zoe", "set": "sleep"})
		"rub_hands":
			var who := "noah" if not on(g, "noah_drowned") else "iris"
			put(g, who, L(-11.0, 0.0, 20.6), 200.0, "Crouch_Idle")
			(g.actors[who] as Actor).reach = {"_r": g._target("zoe") + Vector3(0.25, -0.3, 0.0), "_l": g._target("zoe") + Vector3(0.35, -0.25, 0.2)}
		"zoe_eyes":
			g._expr({"who": "zoe", "set": "none"})
		"zoe_still", "zoe_sing":
			pass
		"patrol":
			_patrol(g)
		"hands_out":
			if g.actors.has("noah") and (g.actors.noah as Actor).visible:
				put(g, "noah", L(-17.0, 0.0, 25.0), 30.0, "Idle")
				(g.actors.noah as Actor).reach = {"_l": L(-17.1, 1.0, 25.5), "_r": L(-16.9, 1.0, 25.5)}
		"hale_stove":
			_dacha_stage(g, false)
			_stove_fire(g, true)
			put(g, "hale", D(-1.0, 0.0, -1.0), -130.0, "Rk_SitIdle")
			Ch2.sit(g, "hale", D(-1.0, 0.45, -1.0), -130.0)
			put(g, "iris", D(-0.4, 0.0, 0.6), -150.0, "Rk_SitIdle")
			Ch2.sit(g, "iris", D(-0.4, 0.45, 0.6), -150.0)
		"visit_room":
			_room(g, "visit")
		"drawing_dock", "drawing_car":
			_table_drawing(g, k)
		"cell":
			_room(g, "cell")
		"steel_smile":
			(g.actors.noah as Actor).expr = {"mouthSmileLeft": 0.4, "mouthSmileRight": 0.36}
		"iris_crawls", "iris_screams":
			put(g, "iris", HOLEW + Vector3(2.6, 0.0, 2.4), -40.0, "Crouch_Idle")
			(g.actors.iris as Actor).expr = (g.EXPR["fear" if k == "iris_crawls" else "pain"] as Dictionary).duplicate()
		"notebook_ice":
			_ice_notebook(g)
		"water_smile":
			_water_mirror(g, true)
		"spring":
			_season(g, "spring")
		"summer_day":
			_season(g, "summer")
		"own_smile":
			(g.actors.noah as Actor).expr = {"mouthSmileLeft": 0.25, "mouthSmileRight": 0.25}
		"walk_car":
			if has(g, "noah"):
				g._walk({"who": "noah", "to": _a(L(-14.5, 0.0, 17.5)), "speed": 1.0, "then": "Idle"})
		"sign", "iris_dock", "empty_dock":
			_mirror_view(g, k)
		"pocket_note":
			(g.actors.iris as Actor).reach = {"_r": g._target("noah") + Vector3(0.1, -0.85, 0.05)}
		"injection":
			_car_inside(g)
		"white_room":
			_room(g, "white")
		"hale_takes":
			(g.actors.hale as Actor).reach = {"_r": ROOMS + Vector3(80.0, 0.78, 0.2)}
		_:
			return false
	return true


static func _a(v: Vector3) -> Array:
	return [v.x, v.y, v.z]


# ---------- дача ----------
static func _dacha_stage(g, outside := true) -> void:
	var lk := lake(g)
	lk.summer(false)
	Ch4.day(g, "lake_day")
	g._blizzard = false
	g.scene.env.fog_density = 0.006
	looks_off(g)
	var who := cast(g)
	if outside:
		var spots := {"noah": [L(-12.6, 0, 1.6), -100.0], "iris": [L(-12.2, 0, 3.0), -110.0], "agnes": [L(-11.2, 0, 2.2), -100.0],
			"eva": [L(-11.0, 0, 3.6), -110.0], "danny": [L(-11.8, 0, 4.2), -110.0], "zoe": [L(-12.0, 0, -0.4), -80.0]}
		for k in who:
			var sp: Array = spots[k]
			put(g, k, sp[0], float(sp[1]), "Idle")
	Ch4.baby(g)
	var bk = g.c4.get("book")
	if bk and is_instance_valid(bk):
		(bk as Node3D).visible = false


static func _key(g) -> void:
	var k := MeshInstance3D.new()
	var bm := BoxMesh.new()
	bm.size = Vector3(0.07, 0.006, 0.02)
	k.mesh = bm
	var m := StandardMaterial3D.new()
	m.albedo_color = Color(0.35, 0.18, 0.08)
	m.metallic = 0.6
	m.roughness = 0.7
	k.material_override = m
	var ba := BoneAttachment3D.new()
	ba.bone_name = "hand_r"
	(g.actors.noah as Actor).skel.add_child(ba)
	ba.add_child(k)
	k.position = Vector3(0, 0.08, 0.03)
	g.c5["key"] = ba


## Доски с одного окна фасада — долой (в дом входит свет).
static func _boards(g) -> void:
	var lk := lake(g)
	for n in lk.model.find_children("dacha_board*", "MeshInstance3D", true, false):
		var mi := n as MeshInstance3D
		var gp := mi.global_position - Ch4.LAKE
		if gp.x > DX + 2.9 and gp.z > 1.0:
			mi.visible = false
	var kb = g.c5.get("key")
	if kb and is_instance_valid(kb):
		(kb as Node).queue_free()


static func _inside(g) -> void:
	var who := cast(g)
	var spots := {"noah": [D(1.6, 0, 0.4), -110.0], "iris": [D(1.8, 0, -1.6), -150.0], "agnes": [D(0.4, 0, -1.0), -120.0],
		"eva": [D(-0.9, 0, 1.4), -60.0], "danny": [D(0.9, 0, 1.6), -100.0], "zoe": [D(2.2, 0, 1.2), -120.0]}
	for k in who:
		var sp: Array = spots[k]
		put(g, k, sp[0], float(sp[1]), "Idle")
	if "eva" in who:
		Ch2.sit(g, "eva", D(-0.9, 0.45, 1.4), -60.0)
	Ch4.baby(g)
	var lk := lake(g)
	if not lk.has_node("in_light"):   # серый свет из окна и тёплый — от печи
		var wl := OmniLight3D.new()
		wl.name = "in_light"
		wl.light_color = Color(0.8, 0.86, 1.0)
		wl.light_energy = 0.9
		wl.omni_range = 6.0
		wl.position = Vector3(DX + 2.2, DF + 1.8, 1.5)
		lk.add_child(wl)
	if not lk.has_node("family_drawing"):   # детский рисунок фломастером на стене
		var q := MeshInstance3D.new()
		q.name = "family_drawing"
		var qm := QuadMesh.new()
		qm.size = Vector2(0.42, 0.32)
		q.mesh = qm
		var m := StandardMaterial3D.new()
		m.albedo_texture = load("res://assets/textures/drawing_family.png")
		m.roughness = 0.9
		q.material_override = m
		q.position = Vector3(DX - 2.78, DF + 1.25, 0.6)   # под полкой (полка проходила по рисунку)
		q.rotation_degrees = Vector3(0, 90, 0)
		lk.add_child(q)


static func _stove_fire(g, on_: bool) -> void:
	var f = g.c5.get("fire")
	if on_ and not (f and is_instance_valid(f)):
		var ol := OmniLight3D.new()
		ol.light_color = Color(1.0, 0.55, 0.22)
		ol.light_energy = 1.4
		ol.omni_range = 4.5
		ol.position = Vector3(DX - 1.2, DF + 0.6, -2.2)
		lake(g).add_child(ol)
		g.c5["fire"] = ol
	elif not on_ and f and is_instance_valid(f):
		(f as Node).queue_free()
		g.c5.erase("fire")


static func _hook(g, who: String) -> void:
	if g.c5.has("hook") or not has(g, who):
		return
	var ba := BoneAttachment3D.new()
	ba.bone_name = "hand_r"
	(g.actors[who] as Actor).skel.add_child(ba)
	var m := MeshInstance3D.new()
	var cm := CylinderMesh.new()
	cm.top_radius = 0.016
	cm.bottom_radius = 0.016
	cm.height = 1.9
	m.mesh = cm
	var mat := StandardMaterial3D.new()
	mat.albedo_color = Color(0.38, 0.27, 0.16)
	m.material_override = mat
	m.position = Vector3(0, 0.3, 0.03)
	ba.add_child(m)
	g.c5["hook"] = ba


# ---------- сарай и воспоминание ----------
static func _shed_inside(g) -> void:
	var lk := lake(g)
	if not lk.has_node("shed_light"):   # в сарае — полосы света сквозь доски
		var sl := OmniLight3D.new()
		sl.name = "shed_light"
		sl.light_color = Color(0.85, 0.88, 1.0)
		sl.light_energy = 0.7
		sl.omni_range = 3.2
		sl.position = Vector3(-9.6, 1.8, 12.6)
		lk.add_child(sl)
	looks_off(g)
	put(g, "noah", L(-9.6, 0.28, 11.45), 180.0, "Fixing_Kneeling")
	if has(g, "iris"):
		put(g, "iris", L(-9.0, 0.28, 12.4), -150.0, "Idle")


static func _summer_night(g, on_: bool) -> void:
	var s: DriveScene = g.scene
	var lk := lake(g)
	lk.summer(on_)
	var c5: Dictionary = g.c5
	if on_:
		lk.shed_door(false)   # мальчик прячется в сарае — дверь закрыта, пока её не откроет Хейл
		s.set_daylight("night")
		s.moon.light_energy = 0.6   # летняя ночь светлее зимней: видно мостки, маму, воду
		s.env.ambient_light_energy = s.env.ambient_light_energy * 1.8
		s.env.fog_density = 0.004
		if not c5.has("ice_mat"):
			c5["ice_mat"] = lk.ice.material_override
		var w := StandardMaterial3D.new()   # тёплая ночь: вода, а не лёд
		w.albedo_color = Color(0.02, 0.04, 0.05)
		w.roughness = 0.06
		w.metallic_specular = 1.0
		lk.ice.material_override = w
		Road._twig_m.set_shader_parameter("snow", 0.0)
		var lamp := OmniLight3D.new()   # фонарь на мостках
		lamp.light_color = Color(1.0, 0.72, 0.38)
		lamp.light_energy = 3.6
		lamp.omni_range = 12.0
		lamp.shadow_enabled = s.pc
		lamp.position = Vector3(4.35, 1.45, 7.45)
		lk.add_child(lamp)
		c5["lamp"] = lamp
		var lm := MeshInstance3D.new()
		var lb := BoxMesh.new()
		lb.size = Vector3(0.12, 0.2, 0.12)
		lm.mesh = lb
		var lmat := StandardMaterial3D.new()
		lmat.albedo_color = Color(1.0, 0.8, 0.5)
		lmat.emission_enabled = true
		lmat.emission = Color(1.0, 0.7, 0.35)
		lmat.emission_energy_multiplier = 3.0
		lm.material_override = lmat
		lm.position = Vector3(4.35, 1.45, 7.58)
		lk.add_child(lm)
		c5["lamp_mesh"] = lm
		for i in 2:   # сумки у её ног
			var bag := MeshInstance3D.new()
			var bb := BoxMesh.new()
			bb.size = Vector3(0.55, 0.32, 0.26) if i == 0 else Vector3(0.45, 0.28, 0.3)
			bag.mesh = bb
			var bmat := StandardMaterial3D.new()
			bmat.albedo_color = Color(0.32, 0.22, 0.14) if i == 0 else Color(0.2, 0.26, 0.34)
			bag.material_override = bmat
			bag.position = Vector3(2.4 + i * 0.6, 0.6, 6.8 - i * 0.2)
			bag.name = "bag%d" % i
			lk.add_child(bag)
			c5["bag%d" % i] = bag
		var mom := Ch2.actor(g, "mom", "anna" if ResourceLoader.exists("res://assets/chars/anna.glb") else "eva")
		mom.visible = true
		Ch2.actor(g, "victor", "victor" if ResourceLoader.exists("res://assets/chars/victor.glb") else "roman").visible = false
		put(g, "mom", L(2.0, 0.42, 6.75), 120.0, "Rk_SitIdle")
		Ch2.sit(g, "mom", L(2.0, 0.47, 6.75), 120.0)
		_kid(g, L(-9.6, 0.28, 11.3), 180.0)
		for kk in ["noah", "iris", "agnes", "eva", "danny", "zoe", "hale", "orderly"]:
			if g.actors.has(kk) and is_instance_valid(g.actors[kk]):
				(g.actors[kk] as Actor).visible = false
		g.sfx.bed("frogs", -10.0)
		c5["frogs"] = true
	else:
		lk.shed_door(true)   # снова зима: Ной и Айрис в сарае, дверь открыта
		if c5.has("ice_mat"):
			lk.ice.material_override = c5["ice_mat"]
		Road._twig_m.set_shader_parameter("snow", 1.0)
		for kk in ["lamp", "lamp_mesh", "bag0", "bag1", "memory_car"]:
			var n = c5.get(kk)
			if n and is_instance_valid(n):
				(n as Node).queue_free()
			c5.erase(kk)
		for kk in ["mom", "victor", "hale_young", "noah_kid"]:
			if g.actors.has(kk) and is_instance_valid(g.actors[kk]):
				(g.actors[kk] as Actor).visible = false
		for kk in ["noah", "iris"]:
			if g.actors.has(kk) and is_instance_valid(g.actors[kk]):
				(g.actors[kk] as Actor).visible = true
		g.sfx.bed("frogs", Sfx.OFF)
		c5.erase("frogs")
		Ch4.day(g, "lake_day")


## Машина на дороге у дачи: чья — отец приехал; позже — молодой Хейл.
static func _memory_car(g, who: String) -> void:
	var lk := lake(g)
	var old = g.c5.get("memory_car")
	if old and is_instance_valid(old):
		(old as Node).queue_free()
	var bc := BlackCar.new()
	if who == "victor":
		bc.paint_color = Color(0.3, 0.32, 0.3)
		bc.tinted = false
		bc.has_driver = false
	lk.add_child(bc)
	bc.position = Vector3(-27.0, 0.0, 9.0)
	bc.rotation_degrees = Vector3(0, -120, 0)
	bc.lights(true)
	g.c5["memory_car"] = bc
	if who == "hale":
		var hy := Ch2.actor(g, "hale_young", "hale_young" if ResourceLoader.exists("res://assets/chars/hale_young.glb") else "hale")
		hy.visible = true
		put(g, "hale_young", L(-24.0, 0.0, 10.0), 70.0, "Idle")
	g.sfx.play("engine_start", -14.0, 0.9)


static func _kick_bag(g) -> void:
	var b = g.c5.get("bag0")
	if b and is_instance_valid(b):
		var tw: Tween = g.create_tween()
		tw.tween_property(b, "position", (b as Node3D).position + Vector3(1.6, -0.9, 0.9), 0.5)
		g.sfx.play("splash", -10.0, 1.3, 0.4)


static func _drown(g) -> void:
	if not g.actors.has("mom"):
		return
	var m: Actor = g.actors.mom
	put(g, "mom", L(4.1, Lake.ICE_Y - 0.15, 6.9), -90.0, "LayToIdle")
	Ch3.hold(m, "LayToIdle", 0.0)
	put(g, "victor", L(3.6, 0.42, 6.9), 90.0, "Crouch_Idle")
	(g.actors.victor as Actor).reach = {"_r": g._target("mom") + Vector3(0, 0.1, 0), "_l": g._target("mom") + Vector3(0.1, 0.05, 0.15)}
	g.sfx.play("splash", -4.0)
	var sp := CPUParticles3D.new()   # брызги
	sp.one_shot = true
	sp.amount = 60
	sp.lifetime = 1.0
	sp.explosiveness = 0.9
	sp.direction = Vector3(0, 1, 0)
	sp.spread = 40.0
	sp.initial_velocity_min = 1.5
	sp.initial_velocity_max = 3.0
	var q := QuadMesh.new()
	q.size = Vector2(0.04, 0.04)
	var qm := StandardMaterial3D.new()
	qm.albedo_color = Color(0.7, 0.75, 0.8, 0.6)
	qm.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
	qm.billboard_mode = BaseMaterial3D.BILLBOARD_PARTICLES
	q.material = qm
	sp.mesh = q
	sp.position = Vector3(4.1, Lake.ICE_Y + 0.05, 6.9)
	lake(g).add_child(sp)
	sp.emitting = true


static func _kid(g, p: Vector3, face_deg: float) -> void:
	var kid := Ch2.actor(g, "noah_kid", "noah_kid" if ResourceLoader.exists("res://assets/chars/noah_kid.glb") else "danny")
	kid.visible = true
	put(g, "noah_kid", p, face_deg, "Idle")
	kid.expr = {"eyeWideLeft": 0.5, "eyeWideRight": 0.5}


# ---------- лёд ----------
static func _ice_stage(g) -> void:
	var lk := lake(g)
	lk.summer(false)
	Ch4.day(g, "lake_day")
	if not lk.has_node("dark_patch"):   # тёмное пятно у мостков — тонкий лёд над ключами
		var dp := MeshInstance3D.new()
		dp.name = "dark_patch"
		var cm := CylinderMesh.new()
		cm.top_radius = 2.6
		cm.bottom_radius = 2.6
		cm.height = 0.01
		dp.mesh = cm
		var m := StandardMaterial3D.new()
		var gt := GradientTexture2D.new()
		var gr := Gradient.new()
		gr.set_color(0, Color(0.12, 0.16, 0.2, 0.95))
		gr.set_color(1, Color(0.3, 0.36, 0.42, 0.0))
		gt.gradient = gr
		gt.fill = GradientTexture2D.FILL_RADIAL
		gt.fill_from = Vector2(0.5, 0.5)
		gt.fill_to = Vector2(0.5, 0.0)
		m.albedo_texture = gt
		m.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
		m.roughness = 0.1
		dp.material_override = m
		dp.position = Vector3(P.x, P.y + 0.004, P.z)
		lk.add_child(dp)


static func _sedan(g) -> void:
	var lk := lake(g)
	if not (g.blackcar and is_instance_valid(g.blackcar)):
		g.blackcar = BlackCar.new()
		g.scene.add_child(g.blackcar)
	var bc: BlackCar = g.blackcar
	if bc.get_parent() != lk:
		bc.reparent(lk, false)
	bc.visible = true
	bc.speed = 0.0
	bc.position = Vector3(-30.0, 0.0, 34.0)
	bc.rotation_degrees = Vector3(0, -130, 0)
	bc.lights(true)
	var tw: Tween = g.create_tween()
	tw.tween_property(bc, "position", Vector3(-14.5, 0.0, 18.5), 3.0).set_ease(Tween.EASE_OUT)
	var h := Ch2.actor(g, "hale", "hale" if ResourceLoader.exists("res://assets/chars/hale.glb") else "frank")
	h.visible = true
	put(g, "hale", L(-12.4, 0.0, 17.2), 110.0, "Idle")
	if not on(g, "orderly_dead"):
		var o := Ch2.actor(g, "orderly", "orderly" if ResourceLoader.exists("res://assets/chars/orderly.glb") else "gus")
		o.visible = true
		put(g, "orderly", L(-13.6, 0.0, 19.6), 120.0, "Idle")
	_hostages(g, false)
	var arrivals: Array = []   # из седана выходят, когда он встал (а не стоят у сарая, пока он едет)
	for k in ["hale", "orderly", "zoe", "danny"]:
		if g.actors.has(k) and is_instance_valid(g.actors[k]) and (g.actors[k] as Actor).visible \
				and (g.actors[k] as Actor).global_position.distance_to(L(-14.0, 0.0, 18.5)) < 6.0:
			(g.actors[k] as Actor).visible = false
			arrivals.append(k)
	tw.tween_callback(func():
		for k in arrivals:
			if g.actors.has(k) and is_instance_valid(g.actors[k]):
				(g.actors[k] as Actor).visible = true)
	g.sfx.play("engine_start", -12.0, 0.9)


## Заложники Хейла: у машины (санитар держит Зою под локоть) или — освобождены (у сарая, Дэнни бежит к Айрис).
static func _hostages(g, free: bool) -> void:
	if on(g, "zoe_taken"):
		var z := Ch2.actor(g, "zoe")
		z.visible = true
		if not free:
			put(g, "zoe", L(-13.2, 0.0, 20.4), 110.0, "Idle")
			g._expr({"who": "zoe", "set": "sleep"})
		else:
			put(g, "zoe", L(-12.0, 0.0, 19.8), 60.0, "Idle")
	if on(g, "danny_hale"):
		if not g.actors.has("danny"):
			g._attach_walkie(Ch2.actor(g, "danny"))
		(g.actors.danny as Actor).visible = true
		if not free:
			put(g, "danny", L(-13.8, 0.0, 20.9), 120.0, "Idle")
		else:
			put(g, "danny", L(-9.5, 0.0, 17.0), 40.0, "Jog_Fwd")


static func _pages_fire(g, on_: bool) -> void:
	var f = g.c5.get("pages_fire")
	if on_ and not (f and is_instance_valid(f)) and has(g, "hale"):
		var ba := BoneAttachment3D.new()
		ba.bone_name = "hand_l"
		(g.actors.hale as Actor).skel.add_child(ba)
		var fl := CPUParticles3D.new()
		fl.amount = 40
		fl.lifetime = 0.5
		fl.direction = Vector3(0, 1, 0)
		fl.spread = 20.0
		fl.initial_velocity_min = 0.2
		fl.initial_velocity_max = 0.5
		var q := QuadMesh.new()
		q.size = Vector2(0.06, 0.1)
		var m := StandardMaterial3D.new()
		m.billboard_mode = BaseMaterial3D.BILLBOARD_PARTICLES
		m.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
		m.blend_mode = BaseMaterial3D.BLEND_MODE_ADD
		m.albedo_color = Color(1.0, 0.55, 0.15)
		q.material = m
		fl.mesh = q
		fl.position = Vector3(0, 0.1, 0.05)
		ba.add_child(fl)
		var ol := OmniLight3D.new()
		ol.light_color = Color(1.0, 0.6, 0.25)
		ol.light_energy = 1.5
		ol.omni_range = 2.5
		ba.add_child(ol)
		g.c5["pages_fire"] = ba
		(g.actors.hale as Actor).reach = {"_l": g._target("hale") + Vector3(0.3, -0.35, 0.3)}
		g.sfx.play("blow", -14.0, 1.8)
	elif not on_ and f and is_instance_valid(f):
		(f as Node).queue_free()
		g.c5.erase("pages_fire")
		if has(g, "hale"):
			(g.actors.hale as Actor).reach = {}


## Полынья: чёрная вода и обломки льда по краю.
static func _hole(g, on_: bool) -> void:
	var lk := lake(g)
	var h = lk.get_node_or_null("hole")
	if on_ and h == null:
		var root := Node3D.new()
		root.name = "hole"
		lk.add_child(root)
		root.position = Vector3(HOLE.x, HOLE.y, HOLE.z)
		var w := MeshInstance3D.new()
		var cm := CylinderMesh.new()
		cm.top_radius = 1.5
		cm.bottom_radius = 1.5
		cm.height = 0.01
		w.mesh = cm
		var wm := StandardMaterial3D.new()
		wm.albedo_color = Color(0.01, 0.02, 0.03)
		wm.roughness = 0.03
		wm.metallic_specular = 1.0
		w.material_override = wm
		w.position = Vector3(0, 0.008, 0)
		root.add_child(w)
		var im := StandardMaterial3D.new()
		im.albedo_color = Color(0.72, 0.8, 0.88)
		im.roughness = 0.2
		var rng := RandomNumberGenerator.new()
		rng.seed = 9
		for i in 14:   # обломки льда по кромке
			var a := TAU * float(i) / 14.0 + rng.randf() * 0.3
			var sh := MeshInstance3D.new()
			var bm := BoxMesh.new()
			bm.size = Vector3(rng.randf_range(0.3, 0.7), 0.08, rng.randf_range(0.2, 0.45))
			sh.mesh = bm
			sh.material_override = im
			sh.position = Vector3(cos(a) * 1.45, 0.02, sin(a) * 1.45)
			sh.rotation = Vector3(rng.randf_range(-0.3, 0.3), -a, rng.randf_range(-0.25, 0.25))
			root.add_child(sh)
	elif not on_ and h:
		h.queue_free()


## В воде: виден по плечи (ниже — под льдом).
static func _in_water(g, who: String, p: Vector3, face_deg: float) -> void:
	if not g.actors.has(who) or not is_instance_valid(g.actors[who]):
		return
	(g.actors[who] as Actor).visible = true
	put(g, who, p + Vector3(0, -1.28, 0), face_deg, "Idle")
	(g.actors[who] as Actor).expr = (g.EXPR["pain"] as Dictionary).duplicate()


static func _patrol(g) -> void:
	var lk := lake(g)
	var pc := Ch2.police_car(g)
	lk.add_child(pc)
	g.c5["patrol_car"] = pc
	pc.position = Vector3(-34.0, 0.0, 40.0)
	pc.rotation_degrees = Vector3(0, -140, 0)
	g.create_tween().tween_property(pc, "position", Vector3(-19.0, 0.0, 26.0), 3.0).set_ease(Tween.EASE_OUT)
	var cop := Ch2.actor(g, "cop", "dayguard")
	if cop.get_parent() != g.scene:
		cop.reparent(g.scene, false)
	cop.visible = true
	put(g, "cop", L(-17.6, 0.0, 26.4), 140.0, "Idle_Talking")
	g.sfx.play("siren", -14.0)


# ---------- эпилоги: комнаты ----------
static func _room(g, kind: String) -> void:
	var s: DriveScene = g.scene
	var x0: float = {"visit": 0.0, "cell": 40.0, "white": 80.0}[kind]
	var o := ROOMS + Vector3(x0, 0, 0)
	var root = g.c5.get("room_" + kind)
	if not (root and is_instance_valid(root)):
		root = Node3D.new()
		s.add_child(root)
		(root as Node3D).position = o
		g.c5["room_" + kind] = root
		var wall := StandardMaterial3D.new()
		wall.albedo_color = {"visit": Color(0.55, 0.6, 0.56), "cell": Color(0.5, 0.5, 0.52), "white": Color(0.93, 0.94, 0.95)}[kind]
		wall.roughness = 0.9
		var w := 4.0
		var dz := 3.6
		_rb(root, Vector3(w, 0.1, dz), Vector3(0, -0.05, 0), wall)              # пол
		_rb(root, Vector3(w, 0.1, dz), Vector3(0, 2.8, 0), wall)                # потолок
		for sx in [-1.0, 1.0]:
			_rb(root, Vector3(0.1, 2.8, dz), Vector3(sx * w / 2, 1.4, 0), wall)
		_rb(root, Vector3(w, 2.8, 0.1), Vector3(0, 1.4, -dz / 2), wall)
		_rb(root, Vector3(w, 2.8, 0.1), Vector3(0, 1.4, dz / 2), wall)
		var lamp := OmniLight3D.new()
		lamp.light_color = Color(1.0, 0.96, 0.9) if kind != "cell" else Color(0.85, 0.9, 1.0)
		lamp.light_energy = 1.2 if kind != "white" else 1.6
		lamp.omni_range = 6.0
		lamp.position = Vector3(0, 2.5, 0)
		(root as Node3D).add_child(lamp)
		var dark := StandardMaterial3D.new()
		dark.albedo_color = Color(0.3, 0.26, 0.22)
		match kind:
			"visit", "white":
				_rb(root, Vector3(1.4, 0.06, 0.8), Vector3(0, 0.76, 0), dark)        # стол
				for sz in [-1.0, 1.0]:
					_rb(root, Vector3(0.45, 0.05, 0.45), Vector3(0, 0.45, sz * 0.75), dark)
			"cell":
				var steel := StandardMaterial3D.new()
				steel.albedo_color = Color(0.55, 0.57, 0.6)
				steel.metallic = 0.9
				steel.roughness = 0.35
				_rb(root, Vector3(0.6, 0.45, 0.04), Vector3(0, 1.6, -dz / 2 + 0.07), steel)   # «зеркало» — сталь
				_rb(root, Vector3(0.5, 0.2, 0.35), Vector3(0, 0.85, -dz / 2 + 0.25), steel)   # раковина
	looks_off(g)
	match kind:
		"visit":
			put(g, "noah", o + Vector3(0, 0, -0.75), 0.0, "Rk_SitIdle")
			Ch2.sit(g, "noah", o + Vector3(0, 0.47, -0.75), 0.0)
			if g.actors.has("iris"):
				(g.actors.iris as Actor).visible = true
				put(g, "iris", o + Vector3(0, 0, 0.75), 180.0, "Rk_SitIdle")
				Ch2.sit(g, "iris", o + Vector3(0, 0.47, 0.75), 180.0)
			if on(g, "romance") and not on(g, "zoe_died") and g.actors.has("zoe"):   # Зоя сидит рядом с Айрис
				(g.actors.zoe as Actor).visible = true
				if not (root as Node3D).has_node("zoe_stool"):
					var st := StandardMaterial3D.new()
					st.albedo_color = Color(0.3, 0.26, 0.22)
					var zs := _rb(root, Vector3(0.45, 0.05, 0.45), Vector3(0.62, 0.45, 0.75), st)
					zs.name = "zoe_stool"
				put(g, "zoe", o + Vector3(0.62, 0, 0.75), 180.0, "Rk_SitIdle")
				Ch2.sit(g, "zoe", o + Vector3(0.62, 0.47, 0.75), 180.0)
		"cell":
			put(g, "noah", o + Vector3(0, 0, -1.0), 180.0, "Interact")
		"white":
			put(g, "noah", o + Vector3(0, 0, -0.75), 0.0, "Rk_SitIdle")
			Ch2.sit(g, "noah", o + Vector3(0, 0.47, -0.75), 0.0)
			_table_drawing(g, "drawing_lantern", o)
			if g.actors.has("hale"):
				(g.actors.hale as Actor).visible = true
				put(g, "hale", o + Vector3(0.7, 0, 0.6), -140.0, "Idle")
	for k in ["agnes", "eva", "danny", "orderly", "cop"]:
		if g.actors.has(k) and is_instance_valid(g.actors[k]):
			(g.actors[k] as Actor).visible = false
	if kind != "white" and g.actors.has("hale"):
		(g.actors.hale as Actor).visible = false
	if g.actors.has("noah"):
		(g.actors.noah as Actor).visible = true


static func _rb(parent: Node3D, size: Vector3, pos: Vector3, m: Material) -> MeshInstance3D:
	var mi := MeshInstance3D.new()
	var bm := BoxMesh.new()
	bm.size = size
	mi.mesh = bm
	mi.material_override = m
	mi.position = pos
	parent.add_child(mi)
	return mi


static func _table_drawing(g, tex: String, o := ROOMS) -> void:
	var old = g.c5.get("table_drawing")
	if old and is_instance_valid(old):
		(old as Node).queue_free()
	var q := MeshInstance3D.new()
	var qm := QuadMesh.new()
	qm.size = Vector2(0.4, 0.3)
	q.mesh = qm
	var m := StandardMaterial3D.new()
	m.albedo_texture = load("res://assets/textures/%s.png" % tex)
	m.roughness = 0.9
	q.material_override = m
	g.scene.add_child(q)
	q.global_position = o + Vector3(0.0, 0.795, 0.0)
	q.rotation_degrees = Vector3(-90, 180 if tex != "drawing_lantern" else 0, 0)
	g.c5["table_drawing"] = q


## Блокнот Айрис на льду — ветер листает (самый тёмный вариант «Тени»).
static func _ice_notebook(g) -> void:
	var q := MeshInstance3D.new()
	var qm := QuadMesh.new()
	qm.size = Vector2(0.3, 0.22)
	q.mesh = qm
	var m := StandardMaterial3D.new()
	m.albedo_texture = load("res://assets/textures/drawing_dock.png")
	q.material_override = m
	lake(g).add_child(q)
	q.position = Vector3(HOLE.x + 2.0, HOLE.y + 0.02, HOLE.z + 1.6)
	q.rotation_degrees = Vector3(-90, 30, 0)
	g.c5["ice_notebook"] = q
	g.sfx.play("page_turn", -8.0)
	g.sfx.play("page_turn", -10.0, 1.1, 0.8)


## Отражение в чёрной воде: в зеркале — двойник (улыбается), сам Ной в зеркало не попадает.
static func _water_mirror(g, on_: bool) -> void:
	if not on_:
		return
	var n: Actor = g.actors.noah
	n.visible = true
	put(g, "noah", HOLEW + Vector3(0.0, 0.0, 1.55), 180.0, "Crouch_Idle")
	n.expr = {}
	var pm := PlanarMirror.new()
	lake(g).add_child(pm)
	pm.build(HOLEW + Vector3(0, 0.012, 0.5), Vector3.UP, Vector2(1.6, 1.4), g.cam)
	var dbl := Actor.make("noah_double", "noah")
	g.scene.add_child(dbl)
	dbl.global_transform = n.global_transform
	dbl.play("Crouch_Idle", 0.0)
	dbl.expr = {"mouthSmileLeft": 0.45, "mouthSmileRight": 0.4}
	dbl.blink_t = 1e9
	Ch2._layers(n, Car.DRIVER_LAYER)
	Ch2._layers(dbl, 2)
	g.cam.cull_mask = 0xFFFFF & ~2
	pm.cam.cull_mask = 0xFFFFF & ~Car.DRIVER_LAYER
	g.c5["water_mirror"] = pm
	g.c5["double"] = dbl


static func _season(g, kind: String) -> void:
	var s: DriveScene = g.scene
	var lk := lake(g)
	lk.summer(true)
	if not g.c5.has("ice_mat"):
		g.c5["ice_mat"] = lk.ice.material_override
	var w := StandardMaterial3D.new()
	w.albedo_color = Color(0.08, 0.12, 0.14) if kind == "spring" else Color(0.1, 0.18, 0.22)
	w.roughness = 0.08
	w.metallic_specular = 1.0
	lk.ice.material_override = w
	Road._twig_m.set_shader_parameter("snow", 0.0)
	_hole(g, false)
	var dp = lk.get_node_or_null("dark_patch")
	if dp:
		dp.queue_free()
	Ch4.day(g, "lake_day" if kind == "spring" else "sunny")   # весна — серый, но светлый день (у «day» солнце еле светит)
	g.scene.env.fog_density = 0.003
	for kk in ["patrol_car"]:   # месяцы спустя: патруля и седана у сарая уже нет
		var n = g.c5.get(kk)
		if n and is_instance_valid(n):
			(n as Node).queue_free()
		g.c5.erase(kk)
	if g.blackcar and is_instance_valid(g.blackcar):
		g.blackcar.visible = false
	looks_off(g)
	for k in g.actors:
		if is_instance_valid(g.actors[k]):
			(g.actors[k] as Actor).visible = false
	if kind == "spring":
		(g.actors.iris as Actor).visible = true
		put(g, "iris", L(3.2, 0.42, 7.0), 90.0, "Crouch_Idle")
		if not on(g, "agnes_dead") and g.actors.has("agnes"):
			(g.actors.agnes as Actor).visible = true
			put(g, "agnes", L(0.6, 0.42, 7.2), 90.0, "Idle_FoldArms")
		var q := MeshInstance3D.new()   # рисунок — на воде
		var qm := QuadMesh.new()
		qm.size = Vector2(0.3, 0.22)
		q.mesh = qm
		var m := StandardMaterial3D.new()
		m.albedo_texture = load("res://assets/textures/drawing_under.png")
		q.material_override = m
		lk.add_child(q)
		q.position = Vector3(4.4, Lake.ICE_Y + 0.02, 7.2)
		q.rotation_degrees = Vector3(-90, 80, 0)
	else:
		(g.actors.iris as Actor).visible = true
		(g.actors.noah as Actor).visible = true
		put(g, "noah", L(3.4, 0.42, 6.75), 90.0, "Rk_SitIdle")
		Ch2.sit(g, "noah", L(3.4, 0.47, 6.75), 90.0)
		put(g, "iris", L(3.4, 0.42, 7.3), 90.0, "Rk_SitIdle")
		Ch2.sit(g, "iris", L(3.4, 0.47, 7.3), 90.0)
		Ch4.notebook(g, "iris")


## Из заднего окна седана: мостки уходят назад (Айрис с листом «Я ПОМНЮ ЗА ТЕБЯ» — или пусто).
static func _mirror_view(g, k: String) -> void:
	var mb: BlackCar = g.blackcar
	if mb and is_instance_valid(mb):   # седан развернулся уезжать: задним стеклом — к мосткам
		var to_dock := L(-3.6, 0.0, 7.0) - mb.global_position
		mb.rotation.y = atan2(to_dock.x, to_dock.z)
	for kk in ["agnes", "eva", "danny", "zoe"]:
		if g.actors.has(kk) and is_instance_valid(g.actors[kk]):
			(g.actors[kk] as Actor).visible = false
	if k == "empty_dock":
		(g.actors.iris as Actor).visible = false
		return
	(g.actors.iris as Actor).visible = true
	put(g, "iris", L(-4.0, 0.42, 7.0), -100.0, "Idle" if k == "iris_dock" else "Jog_Fwd")
	if k == "sign":
		var q := MeshInstance3D.new()
		var qm := QuadMesh.new()
		qm.size = Vector2(0.6, 0.45)
		q.mesh = qm
		var m := StandardMaterial3D.new()
		m.albedo_texture = load("res://assets/textures/drawing_sign_%s.png" % ("ru" if g.lang == "ru" else "en"))
		m.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
		q.material_override = m
		var ba := BoneAttachment3D.new()
		ba.bone_name = "spine_03"
		(g.actors.iris as Actor).skel.add_child(ba)
		ba.add_child(q)
		q.position = Vector3(0, 0.15, 0.4)
		(g.actors.iris as Actor).reach = {"_l": g._target("iris") + Vector3(0.3, -0.4, -0.5), "_r": g._target("iris") + Vector3(-0.3, -0.4, -0.5)}


static func _car_inside(g) -> void:
	looks_off(g)
	var bc: BlackCar = g.blackcar
	if not (bc and is_instance_valid(bc)):
		return
	bc.visible = true
	(g.actors.hale as Actor).visible = true
	Ch2.sit(g, "noah", bc.to_global(Vector3(0.38, 0.53, 1.2)), bc.rotation_degrees.y + 180.0)
	Ch2.sit(g, "hale", bc.to_global(Vector3(-0.38, 0.53, 1.2)), bc.rotation_degrees.y + 180.0)
	(g.actors.hale as Actor).reach = {"_r": g._target("noah") + Vector3(0.1, -0.3, 0.0)}
	(g.actors.noah as Actor).expr = {"eyeBlinkLeft": 0.6, "eyeBlinkRight": 0.6}


# ---------- каждый кадр ----------
static func process(g, _delta: float) -> void:
	var c5: Dictionary = g.c5
	var lamp = c5.get("lamp")
	if lamp and is_instance_valid(lamp):   # фонарь на мостках дрожит
		(lamp as OmniLight3D).light_energy = 2.2 + 0.25 * sin(float(g._t) * 7.0) + randf_range(-0.1, 0.1)
	var dbl = c5.get("double")
	if dbl and is_instance_valid(dbl) and g.actors.has("noah"):
		(dbl as Actor).global_transform = (g.actors.noah as Actor).global_transform


# ---------- планы ----------
static func cam(g, shot: String) -> bool:
	if not String(g._scene_id).begins_with("c5"):
		return false
	var c: Camera3D = g.cam
	match shot:
		"dacha_front":
			_look(c, L(-5.5, 1.7, -5.0), L(-18.0, 1.4, 0.0), 44.0)
		"porch_step":
			_look(c, L(-12.9, 0.95, 1.4), L(-14.6, 0.2, 0.0), 36.0)
		"dacha_window":
			_look(c, L(-13.6, 1.6, 3.4), L(-17.0, 1.9, 2.0), 40.0)
		"dacha_room":
			_look(c, D(2.4, 1.75, 2.9), D(-2.0, 0.8, -1.6), 60.0)
		"wall_drawing":
			_look(c, D(-1.9, 1.3, 0.6), D(-2.78, 1.25, 0.6), 34.0)   # рисунок висит под полкой
		"stove5":
			_look(c, D(0.6, 1.2, 0.2), D(-1.6, 0.6, -2.0), 46.0)
		"coat":
			_look(c, D(1.4, 1.5, -1.4), D(2.7, 1.35, -2.6), 36.0)
		"dark_ice":
			_look(c, L(-13.8, 1.6, 0.8), PW, 30.0)
		"bottle5":
			var b = g.c2.get("bottle")
			if not (b and is_instance_valid(b)):
				return false
			var bp: Vector3 = (b as Node3D).global_position
			_look(c, bp + Vector3(0.22, 0.12, 0.2), bp, 30.0)
		"gap_view":   # от щели наружу (доски по краям кадра — маска hud.peek_on): мостки, лёд
			_look(c, L(-9.555, 1.075, 10.85), L(-2.0, 0.6, 6.5), 40.0)
		"gap_close":   # конец мостков, где мама
			_look(c, L(-9.555, 1.075, 10.85), L(2.0, 0.85, 6.9), 24.0)
		"noah_gap", "shed_two":
			_look(c, L(-8.75, 1.35, 12.3), L(-9.6, 0.85, 11.3), 50.0)
		"memory_dock":   # с берега, ближе: мостки, фонарь, мама и отец читаются (издалека была одна точка фонаря)
			_look(c, L(-3.0, 1.6, 11.5), L(2.6, 0.9, 7.0), 46.0)
		"memory_road":
			_look(c, L(-6.0, 1.4, 6.0), L(-26.0, 1.0, 9.5), 42.0)
		"mom":
			return face(g, "mom", 28.0, 0.95, 0.02, 36.0)
		"mom_face":
			return face(g, "mom", 10.0, 0.6, 0.0, 30.0)
		"victor":
			return face(g, "victor", 20.0, 1.0, 0.02, 36.0)
		"victor_face":
			return face(g, "victor", 15.0, 0.7, 0.0, 30.0)
		"young_hale":
			return face(g, "hale_young", 18.0, 1.0, 0.02, 34.0)
		"hale_door":
			_look(c, L(-10.6, 0.95, 12.2), L(-8.0, 1.5, 13.0), 50.0)
		"young_hale_low":
			if not has(g, "hale_young"):
				return false
			_look(c, L(-9.4, 1.0, 12.5), g._target("hale_young"), 44.0)
		"sedan_shore":
			_look(c, L(-3.5, 1.8, 24.0), L(-14.0, 1.0, 18.5), 44.0)
		"ice_wide":
			_look(c, L(16.0, 2.6, 22.0), L(3.0, 0.0, 9.0), 46.0)
		"ice_two":
			if not has(g, "noah") or not has(g, "hale"):
				return false
			var a: Vector3 = g._target("noah")
			var b: Vector3 = g._target("hale")
			var mid := (a + b) * 0.5
			var side := (b - a).cross(Vector3.UP).normalized()
			_look(c, mid + side * 3.2 + Vector3(0, 0.3, 0), mid + Vector3(0, -0.2, 0), 40.0)
		"pages_fire":
			var f = g.c5.get("pages_fire")
			if not (f and is_instance_valid(f)):
				return false
			var fp: Vector3 = (f as Node3D).global_position
			_look(c, fp + Vector3(0.5, 0.2, 0.5), fp, 34.0)
		"agnes_shore":
			if not face(g, "agnes", 20.0, 1.1, 0.02, 36.0):
				_look(c, L(16.0, 2.6, 22.0), L(3.0, 0.0, 9.0), 46.0)
		"noah_smile":
			return face(g, "noah", 8.0, 0.65, 0.0, 30.0)
		"hole":
			_look(c, HOLEW + Vector3(3.2, 1.6, 3.4), HOLEW + Vector3(0, 0.2, 0), 44.0)
		"crawl":
			_look(c, HOLEW + Vector3(4.2, 1.2, 0.4), HOLEW + Vector3(1.0, 0.3, 0.8), 46.0)
		"hole_hands":
			_look(c, HOLEW + Vector3(1.6, 0.9, -0.6), HOLEW + Vector3(0.6, 0.1, 0.5), 40.0)
		"noah_crawl":
			return face(g, "noah", 30.0, 0.9, -0.05, 38.0)
		"boot":
			_look(c, HOLEW + Vector3(1.6, 0.5, 1.6), HOLEW + Vector3(0.8, 0.0, 0.7), 34.0)
		"hale_water":
			return face(g, "hale", 12.0, 0.9, 0.15, 36.0)
		"patrol":
			_look(c, L(-6.0, 1.8, 31.0), L(-18.0, 1.0, 25.0), 44.0)
		"zoe_car", "zoe_snow":
			_look(c, L(-9.6, 1.4, 22.4), L(-11.6, 0.4, 19.8), 44.0)
		"visit":
			_look(c, ROOMS + Vector3(1.6, 1.6, 0.0), ROOMS + Vector3(0, 0.9, 0), 50.0)
		"visit_table":
			var tp := ROOMS + Vector3(0, 0.8, 0)
			_look(c, tp + Vector3(0.0, 0.55, -0.3), tp, 40.0)
		"cell":
			_look(c, ROOMS + Vector3(41.4, 1.7, 1.2), ROOMS + Vector3(40.0, 1.2, -1.2), 50.0)
		"steel":
			return face(g, "noah", 0.0, 0.6, 0.02, 30.0)
		"hole_sit":
			_look(c, HOLEW + Vector3(3.0, 1.2, 3.6), HOLEW + Vector3(0.0, 0.4, 1.3), 42.0)
		"notebook_ice":
			var nb = g.c5.get("ice_notebook")
			if not (nb and is_instance_valid(nb)):
				return false
			var np: Vector3 = (nb as Node3D).global_position
			_look(c, np + Vector3(0.0, 0.42, 0.06), np, 36.0)
		"water_face":
			var pm = g.c5.get("water_mirror")
			if pm and is_instance_valid(pm):
				var hp: Vector3 = g._target("noah")
				_look(c, hp + Vector3(0.0, 0.25, 0.32), HOLEW + Vector3(0, 0, 0.6), 38.0)
			else:
				return face(g, "noah", 0.0, 0.7, -0.3, 34.0)
		"spring_dock", "summer_dock":
			_look(c, L(8.5, 1.6, 3.0), L(3.0, 0.7, 7.0), 42.0)
		"mirror_sign", "mirror_dock":   # из заднего окна седана (он развернулся уезжать): мостки уходят назад
			var mb: BlackCar = g.blackcar
			if mb and is_instance_valid(mb):
				_look(c, mb.to_global(Vector3(0.0, 1.22, 0.85)), L(-3.6, 1.0, 7.0), 30.0)
			else:
				_look(c, L(-12.6, 1.6, 16.6), L(-3.6, 1.0, 7.0), 30.0)
		"car_hale":
			var bc: BlackCar = g.blackcar
			if not (bc and is_instance_valid(bc)):
				return false
			_look(c, bc.to_global(Vector3(0.0, 1.25, 0.4)), bc.to_global(Vector3(0.0, 1.0, 1.3)), 56.0)
		"white_room":   # Ной за столом рисует: спереди-сбоку и чуть сверху — лицо и листок перед ним
			if not has(g, "noah"):
				_look(c, ROOMS + Vector3(81.4, 1.7, 1.3), ROOMS + Vector3(80.0, 0.9, -0.4), 50.0)
				return true
			var wa: Actor = g.actors.noah
			var wh: Vector3 = g._target("noah")
			var wf := wa.global_transform.basis.z
			wf.y = 0.0
			wf = wf.normalized()
			_look(c, wh + wf.rotated(Vector3.UP, deg_to_rad(35.0)) * 1.0 + Vector3(0, 0.3, 0), wh + wf * 0.3 + Vector3(0, -0.4, 0), 46.0)
		"hale_white":
			return face(g, "hale", 20.0, 1.0, 0.02, 36.0)
		"zoe_face":   # Зоя лежит в снегу: сверху над лицом (план «стоя» попадал в куртку)
			if not has(g, "zoe"):
				return false
			var zh: Vector3 = g._target("zoe")
			_look(c, zh + Vector3(0.3, 0.75, 0.35), zh, 40.0)
		"iris_crawl":   # Айрис на льду низко, смотрит вниз — камера ниже, лицо под волосами видно
			if not face(g, "iris", 26.0, 0.9, -0.3, 40.0):
				_look(c, L(16.0, 2.6, 22.0), L(3.0, 0.0, 9.0), 46.0)
		"noah_shed":   # Ной на коленях у щели: сбоку от головы, на её высоте — профиль (спереди уже стена), плечо не в кадре
			if not has(g, "noah"):
				return false
			var nh: Vector3 = g._target("noah")
			var cp := nh + Vector3(0.75, 0.12, -0.05)
			cp.z = maxf(cp.z, L(0, 0, 10.97).z)
			cp.x = minf(cp.x, L(-8.4, 0, 0).x)
			_look(c, cp, nh, 40.0)
		"iris_porch", "noah_porch", "iris_room", "noah_room", "danny_room", "eva_room", "iris_coat", "iris_window", "zoe_porch", \
				"agnes_window", "agnes_stove5", "agnes_shed5", "iris_shed", "hale_ice", "noah_ice", "iris_ice", \
				"noah_snow", "iris_snow", "iris_visit", "noah_visit", "agnes_spring", "iris_spring", \
				"iris_summer", "noah_summer":
			var who := shot.get_slice("_", 0)
			var ang := 22.0
			if shot.ends_with("_visit") or shot.ends_with("_summer"):
				ang = 30.0
			if not face(g, who, ang, 1.0, 0.02, 38.0) and (shot.ends_with("_ice") or shot.ends_with("_shore") or shot.ends_with("_snow")):
				_look(c, L(16.0, 2.6, 22.0), L(3.0, 0.0, 9.0), 46.0)   # героя нет на месте — общий план озера, а не салон машины
			return true
		_:
			return false
	return true


static func _look(c: Camera3D, from: Vector3, to: Vector3, fov: float) -> void:
	c.fov = fov
	c.look_at_from_position(from, to)
