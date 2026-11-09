class_name St24
## Заправка «24», магазин (лист «24», эпизод 5 — docs/staging/ST24_STAGING_SHEET.md): постановка у прилавка. Роман на
## высоком табурете смотрит телевизор, узнаёт Ноя; руки Ноя на прилавке — в карманы; рука Романа под прилавком к
## телефону и трубка; ветки выбора: деньги и отход спиной, вырванный шнур (Роман отшатывается в стойку с сигаретами),
## «помогите сестре» (рука ложится на прилавок пустой). Реквизит — StationProps.counter_props.

const TOP := 1.445                              # верх столешницы (по ней кладутся руки и деньги)
const NOAH_AT := Vector3(26.4, 0.38, -7.62)     # Ной у прилавка, лицом к −Z
const TIP := 0.19                               # от запястья до кончика вытянутого указательного (зонд скелета Ноя, 5 ноя)
const TIP_L := Vector3(-0.005, 0.19, 0.045)     # кончик выпрямленного указательного в осях кисти (сустав сбоку от оси кисти)
const GLASS_X := 21.11                          # наружная сторона стекла витрины (tools/station.py: x 21,11–21,14)


## Шаги сценария магазина ("fx"). Только в сценах заправки (s5…, s6…).
static func fx(g, k: String) -> bool:
	var st = g.scene.station_root   # только на «24» (кафе главы 2 — cafe_root)
	if st == null or not is_instance_valid(st):
		return false
	match k:
		"roman_stool":
			_roman_stool(g)
		"roman_rise":
			_roman_rise(g)
		"noah_hands_counter":
			_hands_counter(g)
		"noah_pockets":
			var na: Actor = g.actors.noah
			na.clear_hands()
			na.do_gesture("pockets", 60.0)
		"roman_phone_reach":
			_phone_reach(g)
		"roman_pickup":
			_pickup(g)
		"noah_bills":
			_bills(g)
		"noah_backoff":
			_backoff(g)
		"roman_dial":
			_dial(g)
		"cord_rip":
			_cord_rip(g)
		"cord_drop":
			_cord_drop(g)
		"roman_recoil":
			_recoil(g)
		"roman_hand_counter":
			_hand_counter(g)
		"noah_step_out":   # встаёт с края сиденья и выходит из проёма наружу (Sitting_Exit — на месте, поэтому сдвиг)
			var na: Actor = g.actors.noah
			var tw: Tween = g.create_tween()
			tw.tween_property(na, "position", na.position + Vector3(-0.5, -0.06, 0.05), 1.0).set_trans(Tween.TRANS_SINE).set_ease(Tween.EASE_IN_OUT)
		"lapse_tv":   # провал, шаг 1: фокус соскальзывает с Романа на фото Ноя в телевизоре — гул в голове вверх
			g.hud.hum = 0.95
			g._shake = maxf(g._shake, 0.25)
		"wake_drop":   # пробуждение: по рукоятке стекает капля крови с разбитых костяшек
			_blood_drop(g)
			g.sfx.play("breath_ragged", -8.0)
		"bell_swing":   # колокольчик над треснувшей дверью ещё качается — беззвучно
			StationProps.door_bell(g.scene.station_root, true)
		"roman_groan":   # Роман жив: стонет на полу (глухо, сквозь стекло)
			g.sfx.play("groan", -14.0)
		"memory_dock":   # вспышка памяти: женская рука на мостках, мокрый рукав, холодный синий свет, плеск
			_memory_dock(g)
		"memory_back":
			_memory_back(g)
		"nozzle_drop":   # разжал пальцы: пистолет падает и повисает на шланге, качаясь
			_nozzle_drop(g)
		"kids_to_car":   # эпизод 7: Айрис ведёт Дэнни за руку к левой задней двери, Ной подходит от колонки
			_kids_to_car(g)
		"rear_door_open":   # Ной открывает заднюю дверь за ручку и держит её (левая: справа островок в 16 см)
			var na: Actor = g.actors.noah
			g.scene.car.door_open("rl", 0.8, 0.8)
			g.sfx.play("car_door", -10.0, 1.1)
			na.reach = {"_l": g.scene.car.to_global(Vector3(-0.9, 0.95, 0.62))}
		"danny_back_away":   # после провала: Дэнни в ужасе пятится, вырывает руку у Айрис, отступает в снег
			_danny_back_away(g)
		"iris_kneel":   # Айрис опускается перед ним, берёт за оба плеча
			_iris_kneel(g)
		"danny_climb_in":   # руки на дверной раме, колено на сиденье — влезает назад; Ной закрывает дверь, не хлопая
			_danny_climb_in(g)
		"iris_buckle":   # молчание: Айрис сама сажает мальчика и пристёгивает ремнём
			_iris_buckle(g)
		"iris_release":   # «Он остаётся»: Айрис медленно отпускает руку Дэнни
			_iris_release(g)
		"noah_to_car":   # Ной садится за руль: дверь, сел, дверь закрыта
			_noah_to_car(g)
		"engine_on":   # мотор заводится, фары и приборы загораются
			g.scene.car.dash_on(0.4)
			g.sfx.play("engine_start", -6.0, 1.0, 0.1)
		"roman_window":   # что видно в витрине при отъезде — по ветке у кассы
			_roman_window(g)
		"car_depart":   # машина трогается, проезжает мимо витрины и выезжает на трассу
			_car_depart(g)
		"danny_alone":   # «Он остаётся»: маленькая фигура под навесом, тихо шипит рация
			g.sfx.play("radio_hiss", -22.0)
		"iris_looks_away":   # ведро, шаг 7: Айрис смотрит на Ноя, Дэнни — на его руки; Айрис первой отводит глаза
			_iris_looks_away(g)
		"blanket_carry":   # багажник: Ной вытягивает одеяло из-под ящика, встряхивает и берёт под мышку
			_blanket_carry(g)
		"touch_rods":   # багажник, мысль Ноя о рыбалке: пальцы касаются чехла отцовских удочек
			_touch_rods(g)
		"vend_coin":   # автомат: монета из кармана в щель, кнопка — спираль на четверть, батончик повис
			_vend_coin(g)
		"vend_slap":   # автомат: ладонью по стеклу — батончик падает в лоток, Ной нагибается и достаёт
			_vend_slap(g)
		"vend_tap":   # автомат: постучал пальцем по стеклу и отошёл
			_vend_tap(g)
		"poster_corner":   # доска: Ной расправляет пальцем загнувшийся угол плаката; убрал палец — угол снова загнулся
			_poster_corner(g)
		"phone_cold":   # таксофон: трубка ледяная — Ной вздрагивает, прижимая её к уху; гудок
			g.sfx.play("dial_tone", -16.0, 1.0, 0.25, 1.8)
			(g.actors.noah as Actor).do_gesture("tremble", 0.5)
		"phone_lower":   # на седьмом гудке медленно опускает трубку
			(g.actors.noah as Actor).play("Idle", 0.0, 1.6)
		"board_look":   # доска: бумажки на кнопках шевелятся на ветру; Ной читает объявление про собаку
			g.sfx.play("page_turn", -24.0, 1.4, 0.3)
		"road_look":   # трасса: следы их машины, позёмок волнами заметает их прямо в кадре
			_road_look(g, false)
		"road_late":   # late: рядом — вторые следы, свежие, сворачивают к краю площадки, к чёрной машине
			_road_look(g, true)
		"iris_warn":   # чёрная машина: Айрис встаёт со ступеньки и делает шаг за Ноем
			_iris_warn(g)
		"phone_dial_home":   # крупно палец на металлических кнопках: набирает без заминки, каждая пищит
			_phone_dial_home(g)
		"phone_ringback":   # длинные гудки — пять, шесть, семь; лицо Ноя не меняется
			for ri in 3:
				g.sfx.play("ringback", -12.0, 1.0, 0.1 + ri * 1.55)
		"window_look":   # витрина: вплотную к стеклу, ладонь козырьком; пятно от дыхания на стекле; звуки гаснут
			_window_look(g)
		"blackcar_look":   # чёрная машина: потрескивает остывающий металл, ветер (сердце — под конец, на шаге назад)
			g.sfx.play("metal_tick", -10.0)
			g.sfx.play("metal_tick", -14.0, 0.9, 2.6)
		"step_back":   # отступил на шаг, не отводя глаз; сердцебиение Ноя в миксе
			var na: Actor = g.actors.noah
			g.sfx.play("heartbeat", -16.0, 1.0, 0.3)
			var back := -na.global_transform.basis.z.normalized() * 0.4
			na.play("Idle", 0.0, 0.2)
			g.create_tween().tween_property(na, "global_position", na.global_position + back, 0.6).set_trans(Tween.TRANS_SINE).set_ease(Tween.EASE_OUT)
			g.sfx.play("snow_steps", -16.0, 1.1, 0.05, 0.5)
		"iris_notebook":   # Айрис рисует в блокноте на коленях, не поднимая глаз
			_iris_notebook(g, true)
		"iris_notebook_hide":   # закрывает блокнот, прячет под полу худи и прижимает локтем
			_iris_notebook(g, false)
		"trunk_iron":   # багажник при deer = 2: рука Ноя останавливается над монтировкой и не касается
			_trunk_iron(g)
		"blanket_give":   # «Укройся»: Ной отдаёт одеяло Айрис, она встряхивает его и закутывает Дэнни по плечи
			_blanket_give(g)
		"choco_give":   # «Держи»: Дэнни берёт батончик обеими руками, прячет во внутренний карман, прижимает ладонью
			_choco_give(g)
		"walkie_hide", "walkie_show":   # рация Дэнни зажата коленями, пока он дышит в ладони (лист, шаг 3)
			var da: Actor = g.actors.get("danny")
			if da:
				for c in da.skel.get_children():
					if c is BoneAttachment3D and (c as BoneAttachment3D).bone_name == "hand_r":
						(c as Node3D).visible = k == "walkie_show"
		_:
			return false
	return true


## Каждый кадр: шнуры телефона у прилавка; левая рука Ноя на стволе пистолета (пробуждение у колонки).
static func tick(g) -> void:
	var root = g.scene.station_root
	if root == null or not is_instance_valid(root):
		return
	var cp := root.get_node_or_null("counter_props") as Node3D
	if cp != null:
		var via = null
		if cp.has_meta("fist"):   # витой шнур зажат в кулаке Ноя
			var fa = cp.get_meta("fist")
			if fa is Actor and is_instance_valid(fa):
				via = (_bone(fa, "hand_r") + _bone(fa, "middle_01_r")) * 0.5 + (fa as Actor).palm_normal("_r").normalized() * 0.02
		StationProps.cord_update(root, bool(g.flags.get("_cord_ripped", 0.0) > 0.0), via)
	if root.has_meta("torn_cord_by"):   # оборванный шнур в кулаке Романа (отъезд, ветка police_late)
		var rb = root.get_meta("torn_cord_by")
		var tc := root.get_node_or_null("torn_cord") as MeshInstance3D
		if rb is Actor and is_instance_valid(rb) and tc:
			var top := (_bone(rb, "hand_r") + _bone(rb, "middle_01_r")) * 0.5 + (rb as Actor).palm_normal("_r").normalized() * 0.025
			StationProps.cord_mesh(tc, top, top + Vector3(0.02, -0.42, 0.03), 0.0045, 0.0, 0.011)
	if root.has_meta("payphone_cord_by"):   # таксофон: стальной шнур за трубкой в руке Ноя
		StationProps.payphone_cord(root)
	StationProps.flutter(root, g._t)   # бумажки на доске шевелятся на ветру
	var na = g.actors.get("noah")
	if na is Actor and (na as Actor).has_meta("left_on_nozzle"):
		var hn := (na as Actor).skel.get_node_or_null("nozzle_in_hand") as Node3D
		if hn and (na as Actor).reach.has("_l"):
			(na as Actor).reach["_l"] = hn.global_transform * Vector3(0.0, 0.1, 0.12)   # ствол пистолета


## Пробуждение у колонки: пистолет в обеих руках, шланг от колонки (не обрывок), табло — литры заправки.
static func wake_at_pump(g) -> void:
	var na: Actor = g.actors.noah
	var root: Node3D = g.scene.station_root
	var hn := na.skel.get_node_or_null("nozzle_in_hand") as Node3D
	if hn:
		var stub := hn.get_node_or_null("hose_stub") as Node3D
		if stub:
			stub.visible = false
	StationProps.holster(root, false)
	if g._hose == null or not is_instance_valid(g._hose):
		g._hose = MeshInstance3D.new()
		var hm := StandardMaterial3D.new()
		hm.albedo_color = Color(0.03, 0.03, 0.035)
		hm.roughness = 0.45
		g._hose.material_override = hm
		root.add_child(g._hose)
	StationProps.pump_display(root, g.FUEL_L, g.lang)
	na.reach = {"_l": na.global_position + Vector3(0, 1.0, 0)}
	na.set_meta("left_on_nozzle", true)
	na.set_meta("auto_grip", false)
	na.grip = {"_r": 0.85, "_l": 0.7}


## Капля крови с костяшек стекает по рукоятке пистолета и падает в снег.
static func _blood_drop(g) -> void:
	var na: Actor = g.actors.noah
	var hn := na.skel.get_node_or_null("nozzle_in_hand") as Node3D
	if hn == null:
		return
	var d := MeshInstance3D.new()
	var sm := SphereMesh.new()
	sm.radius = 0.006
	sm.height = 0.014
	d.mesh = sm
	var m := StandardMaterial3D.new()
	m.albedo_color = Color(0.22, 0.01, 0.01)
	m.roughness = 0.1
	m.metallic_specular = 0.8
	d.material_override = m
	g.scene.station_root.add_child(d)
	# с разбитых костяшек (тыльная сторона кисти, у основания пальцев) — вниз по пальцам и рукояти, потом в снег.
	# Путь — в мировых осях: раньше капля шла вдоль кости кисти, и у опущенной руки ползла вверх.
	var p0: Vector3 = hn.global_transform * Vector3(0.017, 0.11, 0.024)
	d.global_position = p0
	var tw := d.create_tween()
	tw.tween_interval(0.6)
	tw.tween_property(d, "global_position", p0 + Vector3(0.0, -0.06, 0.0), 1.4).set_trans(Tween.TRANS_QUAD).set_ease(Tween.EASE_IN)
	tw.tween_property(d, "global_position", Vector3(p0.x, 0.02, p0.z), 0.35).set_trans(Tween.TRANS_QUAD).set_ease(Tween.EASE_IN)
	tw.tween_callback(d.queue_free)


## Разжал пальцы: пистолет падает, повисает на шланге у колонки и качается; Ной идёт к машине.
static func _nozzle_drop(g) -> void:
	var na: Actor = g.actors.noah
	var hn := na.skel.get_node_or_null("nozzle_in_hand") as Node3D
	na.remove_meta("left_on_nozzle")
	na.clear_hands()
	na.grip = {}
	na.set_meta("auto_grip", true)
	if hn == null:
		return
	var root: Node3D = g.scene.station_root
	var hold := Node3D.new()   # пистолет — отдельно от руки: висит на шланге
	hold.name = "nozzle_dropped"
	root.add_child(hold)
	hold.global_transform = hn.global_transform
	for c in hn.get_children():
		(c as Node3D).reparent(hold, true)
	hn.queue_free()
	g.set_meta("nozzle_node", hold)
	var hang := Vector3(12.55, 0.55, -4.45)   # повис у колонки, низко над снегом
	var tw := hold.create_tween()
	tw.tween_property(hold, "global_position", hang, 0.32).set_trans(Tween.TRANS_QUAD).set_ease(Tween.EASE_IN)
	tw.parallel().tween_property(hold, "rotation_degrees", Vector3(160, hold.rotation_degrees.y, 10), 0.32)
	for k in 4:   # качается и замирает
		var amp := 22.0 * pow(0.55, k)
		tw.tween_property(hold, "rotation_degrees:z", amp, 0.35).set_trans(Tween.TRANS_SINE)
		tw.tween_property(hold, "rotation_degrees:z", -amp * 0.7, 0.35).set_trans(Tween.TRANS_SINE)
	g.sfx.play("nozzle", -6.0, 0.8, 0.25)


## Вспышка памяти: мини-сцена далеко в стороне от «24» (в воздухе, чтобы ничего из мира не попало в кадр) — мокрые
## доски мостков, тёмная вода, женская рука в мокром рукаве (Анна), холодный синий свет сверху; камера — крупно на руку.
const DOCK := Vector3(400.0, 60.0, 400.0)
static func _memory_dock(g) -> void:
	var root: Node3D = g.scene.station_root
	var set_ := root.get_node_or_null("dock_memory") as Node3D
	if set_ == null:
		set_ = Node3D.new()
		set_.name = "dock_memory"
		root.add_child(set_)
		set_.global_position = DOCK
		var water := MeshInstance3D.new()
		var pm := PlaneMesh.new()
		pm.size = Vector2(12, 12)
		water.mesh = pm
		var wm := StandardMaterial3D.new()
		wm.albedo_color = Color(0.01, 0.02, 0.035)
		wm.roughness = 0.05
		wm.metallic_specular = 1.0
		water.material_override = wm
		water.position = Vector3(0, -0.35, 0)
		set_.add_child(water)
		var wood := StandardMaterial3D.new()
		wood.albedo_color = Color(0.16, 0.13, 0.1)
		wood.roughness = 0.2   # мокрые
		for i in 7:   # доски поперёк, с щелями
			var b := MeshInstance3D.new()
			var bm := BoxMesh.new()
			bm.size = Vector3(3.0, 0.04, 0.17)
			b.mesh = bm
			b.material_override = wood
			b.position = Vector3(0.6, 0, -0.6 + i * 0.19)
			set_.add_child(b)
		var sl := SpotLight3D.new()   # холодный синий свет
		sl.light_color = Color(0.45, 0.62, 1.0)
		sl.light_energy = 3.0
		sl.spot_range = 6.0
		sl.spot_angle = 40.0
		set_.add_child(sl)
		sl.position = Vector3(-0.3, 2.6, 0.2)
		sl.rotation_degrees = Vector3(-80, 0, 0)
		var an := Actor.make("anna", "anna")
		set_.add_child(an)
		an.name = "anna_dock"
		an.position = Vector3(1.05, 0.02, 0.0)
		an.rotation_degrees = Vector3(0, 90, 0)
		an.play("Death01", 0.0, 0.0)
		an.ap.seek(maxf(0.0, an.ap.current_animation_length - 0.05), true)
		an.ap.pause()
		an.blink_t = 1e9
		for mi in an.find_children("*", "MeshInstance3D", true, false):   # мокрая ткань: темнее и с бликом
			var m := (mi as MeshInstance3D).get_active_material(0)
			if m is StandardMaterial3D and String(mi.name).begins_with("cloth"):
				var wm2 := (m as StandardMaterial3D).duplicate() as StandardMaterial3D
				wm2.albedo_color = wm2.albedo_color.darkened(0.35)
				wm2.roughness = 0.18
				(mi as MeshInstance3D).set_surface_override_material(0, wm2)
		# Death01 кладёт кисти центром кости на «землю» — на мостках они уходили в доски на 3 см, и во вспышке был виден
		# только рукав. Ладони — на доски (верх досок + полкисти), локти при этом — вверх, а не в доски.
		var top := set_.global_position.y + 0.02 + 0.022
		var rh := {}
		for s in ["_r", "_l"]:
			var hp := _bone(an, "hand" + s)
			rh[s] = Vector3(hp.x, maxf(hp.y, top), hp.z)
		an.reach = rh
		an.reach_pole = {"_r": Vector3.UP, "_l": Vector3.UP}
	g.set_meta("dock_prev", [g._shot, g._cam_free])
	var hand := Vector3.ZERO
	var an2 := set_.get_node_or_null("anna_dock") as Actor
	if an2:
		hand = an2.reach.get("_r", an2.skel.global_transform * an2.skel.get_bone_global_pose(an2.skel.find_bone("hand_r")).origin)
	else:
		hand = DOCK
	g._cam_free = [[hand.x - 0.35, hand.y + 0.9, hand.z + 0.25], [hand.x, hand.y, hand.z], 34]
	g._cam_free_to = []
	g._cut("free")
	g.sfx.play("splash", -14.0, 0.7)
	g.sfx.play("splash", -18.0, 0.8, 0.9)


static func _memory_back(g) -> void:
	var root: Node3D = g.scene.station_root
	var set_ := root.get_node_or_null("dock_memory")
	if set_:
		set_.queue_free()
	g._shake = maxf(g._shake, 0.3)



static func _bone(a: Actor, b: String) -> Vector3:
	var sk := a.skel
	return sk.global_transform * sk.get_bone_global_pose(sk.find_bone(b)).origin


## Роман на табурете, повернулся к телевизору за спиной (табурет вертится), руки скрещены на животе.
static func _roman_stool(g) -> void:
	var root: Node3D = g.scene.station_root
	StationProps.counter_props(root)
	StationProps.tube_flicker(root)
	var ra: Actor = g.actors.roman
	ra.clear_hands()
	g._place({"who": "roman", "at": [StationProps.STOOL.x, 0.38, StationProps.STOOL.z - 0.06], "face": 168, "clip": "Sitting_Idle"})
	await g.get_tree().process_frame
	await g.get_tree().process_frame
	if not is_instance_valid(ra):
		return
	g._sit("roman", StationProps.STOOL + Vector3(0, 0.88, -0.05))
	ra.do_gesture("arms_crossed", 600.0)
	g._look({"who": "roman", "at": "tv"})


## Узнал: руки расходятся, табурет поворачивается к прилавку, взгляд — на Ноя.
static func _roman_rise(g) -> void:
	var ra: Actor = g.actors.roman
	ra.end_gesture()
	var c := StationProps.STOOL
	var p0 := ra.position
	var y0 := ra.rotation.y
	var y1 := deg_to_rad(4.0)
	var dy := wrapf(y1 - y0, -PI, PI)
	var tw: Tween = g.create_tween()
	tw.tween_method(func(f: float):
		var ang := dy * f
		var off := (p0 - Vector3(c.x, p0.y, c.z)).rotated(Vector3.UP, ang)
		ra.position = Vector3(c.x, p0.y, c.z) + off
		ra.rotation.y = y0 + ang, 0.0, 1.0, 0.9).set_trans(Tween.TRANS_SINE).set_ease(Tween.EASE_IN_OUT)
	g._look({"who": "roman", "at": "noah"})


## Руки Ноя на прилавке ладонями вниз (видно, что с ними: кровь или тёмное под ногтями).
static func _hands_counter(g) -> void:
	var na: Actor = g.actors.noah
	na.clear_hands()
	na.reach = {"_r": Vector3(NOAH_AT.x + 0.17, TOP, -8.14), "_l": Vector3(NOAH_AT.x - 0.17, TOP, -8.14)}
	na.reach_palm = {"_r": Vector3.DOWN, "_l": Vector3.DOWN}


## Правая рука Романа медленно уходит под прилавок — к полке с телефоном; пальцы ложатся на трубку.
static func _phone_reach(g) -> void:
	var ra: Actor = g.actors.roman
	var props := StationProps.counter_props(g.scene.station_root)
	var rc: Node3D = props.get("receiver")
	if rc == null:
		return
	var start := _bone(ra, "hand_r")
	var target := rc.global_position + Vector3(0.0, 0.035, 0.0)
	ra.reach = {"_r": start}
	ra.reach_palm = {"_r": Vector3.DOWN}
	ra.reach_lean = 22.0
	var tw: Tween = g.create_tween()
	tw.tween_method(func(p: Vector3):
		if ra.reach.has("_r"):
			ra.reach["_r"] = p, start, target, 1.9).set_trans(Tween.TRANS_SINE).set_ease(Tween.EASE_IN_OUT)


## Трубка — в руку Роману и вверх, к груди (витой шнур тянется от аппарата); гудок в трубке.
static func _pickup(g) -> void:
	var ra: Actor = g.actors.roman
	var props := StationProps.counter_props(g.scene.station_root)
	var rc: Node3D = props.get("receiver")
	if rc == null:
		return
	_hold(ra, rc)
	var from := _bone(ra, "hand_r")
	var to := Vector3(StationProps.PHONE_AT.x + 0.08, 1.62, -9.05)
	ra.reach = {"_r": from}
	var tw: Tween = g.create_tween()
	tw.tween_method(func(p: Vector3):
		if ra.reach.has("_r"):
			ra.reach["_r"] = p, from, to, 0.8).set_trans(Tween.TRANS_SINE)
	g.sfx.play("phone_pickup", -12.0)
	g.sfx.play("dial_tone", -24.0, 1.0, 0.5)


## Предмет — в кисть героя (BoneAttachment), трубкой вдоль ладони. Левая кисть — зеркально правому хвату
## (оси костей левой руки отражены, поэтому хват переводится через позу покоя обеих кистей).
static func _hold(a: Actor, item: Node3D, side := "_r") -> void:
	var ba := a.skel.get_node_or_null("prop_hand" + side) as BoneAttachment3D
	if ba == null:
		ba = BoneAttachment3D.new()
		ba.name = "prop_hand" + side
		ba.bone_name = "hand" + side
		a.skel.add_child(ba)
	item.reparent(ba, false)
	var xr := Transform3D(Basis.from_euler(Vector3(0, 0, deg_to_rad(90.0))), Vector3(0.0, 0.09, 0.03))
	if side == "_r":
		item.transform = xr
	else:
		var sk := a.skel
		var rr := sk.get_bone_global_rest(sk.find_bone("hand_r"))
		var rl := sk.get_bone_global_rest(sk.find_bone("hand_l"))
		var m := Transform3D(Basis(Vector3(-1, 0, 0), Vector3(0, 1, 0), Vector3(0, 0, 1)), Vector3.ZERO)
		item.transform = rl.affine_inverse() * (m * (rr * xr) * m)
	a.set_meta("auto_grip", true)


## Деньги: двумя пальцами из внутреннего кармана — смятые купюры на прилавок, разгладить ладонью.
static func _bills(g) -> void:
	var na: Actor = g.actors.noah
	na.clear_hands()
	var tw: Tween = g.create_tween()
	var pocket := _bone(na, "spine_03") + na.global_transform.basis.z * 0.13 + na.global_transform.basis.x * 0.07
	na.reach = {"_r": pocket}
	na.reach_palm = {"_r": -na.global_transform.basis.z}
	var bills := Node3D.new()
	bills.name = "bills"
	var paper := StandardMaterial3D.new()
	paper.albedo_color = Color(0.58, 0.64, 0.52)
	paper.roughness = 0.85
	for i in 3:
		var b := MeshInstance3D.new()
		var bm := BoxMesh.new()
		bm.size = Vector3(0.068, 0.004, 0.15)
		b.mesh = bm
		b.material_override = paper
		b.position = Vector3(i * 0.006, i * 0.004, i * 0.01)
		b.rotation_degrees = Vector3(randf_range(-12, 12), i * 11.0, randf_range(-14, 14))   # смятые
		bills.add_child(b)
	var spot := Vector3(NOAH_AT.x + 0.08, TOP + 0.002, -8.2)
	tw.tween_interval(0.65)
	tw.tween_callback(func():
		g.scene.station_root.add_child(bills)
		_hold(na, bills)
		na.reach = {"_r": spot + Vector3(0, 0.05, 0)}
		na.reach_palm = {"_r": Vector3.DOWN})
	tw.tween_interval(0.6)
	tw.tween_callback(func():   # положил: купюры на прилавке, расправляются
		bills.reparent(g.scene.station_root, true)
		bills.global_position = spot
		bills.rotation = Vector3(0, deg_to_rad(18.0), 0)
		for c in bills.get_children():
			(c as Node3D).create_tween().tween_property(c, "rotation_degrees", Vector3(0, (c as Node3D).rotation_degrees.y, 0), 0.5)
		g.sfx.play("bills", -6.0))
	tw.tween_method(func(f: float):   # ладонь разглаживает купюры: два раза туда-обратно
		if na.reach.has("_r"):
			na.reach["_r"] = spot + Vector3(sin(f * TAU * 2.0) * 0.07, 0.02, 0.0), 0.0, 1.0, 0.9)
	tw.tween_callback(func(): na.clear_hands())


## Пятится к двери, не поворачиваясь спиной (1,6 м); у двери — колокольчик.
static func _backoff(g) -> void:
	var na: Actor = g.actors.noah
	na.clear_hands()
	na.play("Walk", 0.0, 0.25)
	na.ap.speed_scale = -0.8   # шаги назад
	var p0 := na.position
	var p1 := p0 - na.global_transform.basis.z * 1.6   # назад = против взгляда (лицом к прилавку, −Z → идёт к +Z, к выходу)
	var tw: Tween = g.create_tween()
	tw.tween_property(na, "position", p1, 2.4).set_trans(Tween.TRANS_SINE).set_ease(Tween.EASE_IN_OUT)
	tw.tween_callback(func():
		na.play("Idle", 0.0, 0.3)
		g.sfx.play("door_chime", -10.0, 1.0, 0.8))


## С улицы сквозь витрину: Роман подносит трубку к уху, левой рукой набирает номер — полицию вызвали.
static func _dial(g) -> void:
	var ra: Actor = g.actors.roman
	var props := StationProps.counter_props(g.scene.station_root)
	var rc: Node3D = props.get("receiver")
	if rc:   # трубка — в левую руку (к левому уху), правая свободна для кнопок: телефон под правой рукой
		_hold(ra, rc, "_l")
	var ear := ra.head_pos() + ra.global_transform.basis.x * 0.09 + Vector3(0, -0.03, 0.02)
	var key := StationProps.PHONE_AT + Vector3(0.02, 0.06, -0.02)
	ra.reach = {"_l": ear, "_r": key}
	ra.reach_palm = {"_r": Vector3.DOWN}
	ra.reach_lean = 22.0
	var tw: Tween = g.create_tween()
	tw.tween_interval(0.6)
	tw.tween_method(func(f: float):   # набирает: кисть постукивает по кнопкам
		if ra.reach.has("_r"):
			ra.reach["_r"] = key + Vector3(0.02 * sin(f * 9.0), 0.02 * absf(sin(f * 18.0)), 0.015 * cos(f * 7.0)), 0.0, 1.0, 1.8)
	g.sfx.play("phone_dial", -16.0, 1.0, 0.6)


## Вырвать провод: Ной перегибается через прилавок, хватает витой шнур кулаком и дёргает на себя — аппарат слетает
## с полки, шнур вылетает из розетки с треском и брызгами пластика; трубка вырывается из руки Романа.
static func _cord_rip(g) -> void:
	var na: Actor = g.actors.noah
	var ra: Actor = g.actors.roman
	var root: Node3D = g.scene.station_root
	var props := StationProps.counter_props(root)
	var ph: Node3D = props.get("phone")
	var rc: Node3D = props.get("receiver")
	na.clear_hands()
	var cp := root.get_node_or_null("counter_props") as Node3D
	var p0 := na.position
	na.create_tween().tween_property(na, "position", Vector3(p0.x, p0.y, -7.84), 0.3).set_trans(Tween.TRANS_SINE)   # вплотную к прилавку
	var grab := Vector3(StationProps.PHONE_AT.x + 0.1, TOP + 0.06, -8.86)   # витой шнур у заднего края прилавка
	na.reach = {"_r": grab}
	na.reach_lean = 55.0
	na.reach_palm = {"_r": Vector3.DOWN}
	na.set_meta("auto_grip", false)
	na.grip = {"_r": 0.95}
	var tw: Tween = g.create_tween()
	tw.tween_interval(0.4)
	tw.tween_callback(func():   # пальцы сомкнулись на шнуре — дальше шнур идёт через кулак
		if cp:
			cp.set_meta("fist", na))
	tw.tween_interval(0.15)
	tw.tween_callback(func():
		g.sfx.play("phone_rip", -2.0)
		g._shake = 0.4
		g.flags["_cord_ripped"] = 1.0
		_sparks(root, StationProps.SOCKET)
		if rc and rc.get_parent() is BoneAttachment3D:   # трубка — из руки Романа на прилавок
			rc.reparent(root, true)
			var tr := rc.create_tween()
			tr.tween_property(rc, "global_position", Vector3(StationProps.PHONE_AT.x + 0.3, TOP + 0.03, -8.45), 0.35).set_trans(Tween.TRANS_QUAD).set_ease(Tween.EASE_IN)
		ra.clear_hands()
		if ph:   # аппарат рывком вверх с полки, через прилавок — на пол сбоку от Ноя (не сквозь столешницу)
			ph.reparent(root, true)
			var tp := ph.create_tween()
			tp.tween_property(ph, "global_position", Vector3(StationProps.PHONE_AT.x + 0.1, TOP + 0.22, -8.75), 0.14).set_trans(Tween.TRANS_QUAD).set_ease(Tween.EASE_OUT)
			tp.tween_property(ph, "global_position", Vector3(StationProps.PHONE_AT.x + 0.05, TOP + 0.12, -8.0), 0.2)
			tp.tween_property(ph, "global_position", Vector3(25.72, 0.42, -7.7), 0.32).set_trans(Tween.TRANS_QUAD).set_ease(Tween.EASE_IN)
			var tq := ph.create_tween()
			tq.tween_property(ph, "rotation_degrees", Vector3(70, 120, 30), 0.66)
		na.reach = {"_r": Vector3(NOAH_AT.x + 0.25, 1.35, -7.75)}   # рывок на себя
		na.reach_lean = 0.0
		na.create_tween().tween_property(na, "position", p0, 0.35).set_trans(Tween.TRANS_SINE))


## Шнур — на пол, руки свободны.
static func _cord_drop(g) -> void:
	var na: Actor = g.actors.noah
	na.clear_hands()
	var cp := (g.scene.station_root as Node3D).get_node_or_null("counter_props") as Node3D
	if cp and cp.has_meta("fist"):
		cp.remove_meta("fist")


## Искры и обломки пластика из розетки.
static func _sparks(root: Node3D, at: Vector3) -> void:
	var p := CPUParticles3D.new()
	p.one_shot = true
	p.amount = 24
	p.lifetime = 0.6
	p.explosiveness = 0.95
	p.direction = Vector3(0, 0.6, 1)
	p.spread = 55.0
	p.initial_velocity_min = 0.8
	p.initial_velocity_max = 2.2
	p.gravity = Vector3(0, -9.8, 0)
	p.scale_amount_min = 0.4
	p.scale_amount_max = 1.0
	var bm := BoxMesh.new()
	bm.size = Vector3(0.008, 0.008, 0.008)
	p.mesh = bm
	var m := StandardMaterial3D.new()
	m.albedo_color = Color(0.9, 0.88, 0.8)
	m.emission_enabled = true
	m.emission = Color(1.0, 0.75, 0.4)
	m.emission_energy_multiplier = 1.5
	p.material_override = m
	root.add_child(p)
	p.global_position = at + Vector3(0, 0, 0.03)
	p.emitting = true
	p.get_tree().create_timer(1.5).timeout.connect(p.queue_free)


## Роман отшатывается: встаёт с табурета, ладони вперёд, шаг назад — в стойку с сигаретами, пачки сыплются на пол.
static func _recoil(g) -> void:
	var ra: Actor = g.actors.roman
	var root: Node3D = g.scene.station_root
	ra.clear_hands()
	ra.play("Idle", 0.0, 0.25)
	ra.do_gesture("palms_out", 1.6)
	g._look({"who": "roman", "at": "noah"})
	var p0 := ra.position
	var p1 := Vector3(p0.x + 0.25, 0.38, StationProps.CIG_RACK.z + 0.42)
	var tw: Tween = g.create_tween()
	tw.tween_property(ra, "position", p1, 0.55).set_trans(Tween.TRANS_SINE).set_ease(Tween.EASE_OUT)
	tw.tween_callback(func():   # спиной в стойку: пачки падают
		g.sfx.play("bills", -4.0, 0.6)
		var rk := root.find_child("cigs", true, false) as Node3D
		if rk == null:
			return
		var rng := RandomNumberGenerator.new()
		rng.seed = 7
		for pk in rk.find_children("pack_*", "MeshInstance3D", false, false):
			if rng.randf() > 0.45:
				continue
			var n := pk as Node3D
			var to := n.global_position + Vector3(rng.randf_range(-0.3, 0.3), 0.0, rng.randf_range(0.15, 0.6))
			to.y = 0.4
			var tp := n.create_tween().set_parallel()
			tp.tween_property(n, "global_position", to, rng.randf_range(0.35, 0.6)).set_trans(Tween.TRANS_QUAD).set_ease(Tween.EASE_IN).set_delay(rng.randf_range(0.0, 0.25))
			tp.tween_property(n, "rotation_degrees", Vector3(rng.randf_range(-90, 90), rng.randf_range(0, 360), rng.randf_range(-90, 90)), 0.5))
	tw.tween_callback(func(): ra.play("Idle_No", 0.0, 0.3))
	var st := root.find_child("stool", true, false) as Node3D   # табурет отъезжает
	if st:
		st.create_tween().tween_property(st, "position", st.position + Vector3(-0.2, 0, 0.25), 0.4)


## «Помогите сестре»: трубка ложится обратно, рука поднимается из-под прилавка пустой и ложится на прилавок.
static func _hand_counter(g) -> void:
	var ra: Actor = g.actors.roman
	var props := StationProps.counter_props(g.scene.station_root)
	var rc: Node3D = props.get("receiver")
	var ph: Node3D = props.get("phone")
	if rc and ph and rc.get_parent() is BoneAttachment3D:
		rc.reparent(ph, false)
		rc.position = Vector3(-0.035, 0.045, 0.0)
		rc.rotation = Vector3.ZERO
		g.sfx.play("phone_pickup", -16.0, 0.8)
	var from := _bone(ra, "hand_r")
	var to := Vector3(StationProps.PHONE_AT.x + 0.25, TOP + 0.01, -8.62)
	ra.reach = {"_r": from}
	ra.reach_palm = {"_r": Vector3.DOWN}
	var tw: Tween = g.create_tween()
	tw.tween_interval(0.5)
	tw.tween_method(func(p: Vector3):
		if ra.reach.has("_r"):
			ra.reach["_r"] = p, from, to, 1.1).set_trans(Tween.TRANS_SINE).set_ease(Tween.EASE_IN_OUT)


# ------------------------------------------------------------------ эпизод 4: одеяло и батончик (вместо подписей)
## Клетчатый плед (как в багажнике: кирпично-бурый с зелёной и горчичной клеткой), шерсть — матовая, с ворсом по краю.
const PLAID := """
shader_type spatial;
render_mode cull_disabled, diffuse_burley;
uniform float scale = 6.0;
float band(float x, float c, float w) { float d = abs(fract(x - c + 0.5) - 0.5); return 1.0 - smoothstep(w * 0.5, w * 0.5 + 0.02, d); }
float h2(vec2 p) { return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453); }
void fragment() {
	vec2 p = UV * scale;
	vec3 base = vec3(0.13, 0.03, 0.016);   // светлее, чем в багажнике под лампочкой: ночью под навесом плед читается
	vec3 green = vec3(0.02, 0.055, 0.035);
	vec3 mustard = vec3(0.4, 0.24, 0.07);
	float gu = band(p.x, 0.0, 0.34);
	float gv = band(p.y, 0.0, 0.34);
	float mu = band(p.x, 0.5, 0.05);
	float mv = band(p.y, 0.5, 0.05);
	vec3 c = mix(base, green, max(gu, gv) * 0.85);
	c = mix(c, green * 0.55, gu * gv * 0.6);   // пересечение полос — темнее
	c = mix(c, mustard, max(mu, mv) * 0.9);
	float twill = 0.9 + 0.1 * sin((UV.x + UV.y) * scale * 80.0);   // саржевая диагональ
	ALBEDO = c * twill * (0.88 + 0.24 * h2(floor(UV * 700.0)));
	ROUGHNESS = 1.0;
	SPECULAR = 0.2;
	RIM = 0.4;
	RIM_TINT = 0.85;
}
"""
static var _plaid: ShaderMaterial = null


static func plaid() -> ShaderMaterial:
	if _plaid == null:
		var sh := Shader.new()
		sh.code = PLAID
		_plaid = ShaderMaterial.new()
		_plaid.shader = sh
	return _plaid


## Сложенное одеяло (несёт Ной, лежит в багажнике): стопка из трёх слоёв с мягким краем.
static func _folded() -> Node3D:
	var n := Node3D.new()
	n.name = "blanket_folded"
	for i in 3:
		var b := MeshInstance3D.new()
		var bm := BoxMesh.new()
		bm.size = Vector3(0.4 - i * 0.006, 0.026, 0.27 - i * 0.004)
		b.mesh = bm
		b.material_override = plaid()
		b.position = Vector3(i * 0.003, i * 0.027, -i * 0.002)
		n.add_child(b)
	return n


## Ведро, шаг 7 (лист «24»): Айрис со ступеньки смотрит на Ноя, Дэнни рядом — на его руки. Айрис первой отводит глаза
## и лёгким касанием плеча разворачивает мальчика к себе. Ничего не говорит (подпись убрана — всё в кадре).
static func _iris_looks_away(g) -> void:
	var ia: Actor = g.actors.iris
	var da: Actor = g.actors.danny
	g._look({"who": "iris", "at": "noah"})
	g._look({"who": "danny", "at": "noah_hands"})
	var tw: Tween = g.create_tween()
	tw.tween_interval(1.5)
	tw.tween_callback(func():   # отвела глаза — на мальчика; левой рукой — к его плечу (он слева от неё)
		g._look({"who": "iris", "at": "danny"})
		ia.reach = {"_l": _bone(da, "upperarm_r") + Vector3(0, 0.04, 0)})
	tw.tween_interval(0.45)
	tw.tween_callback(func(): g._look({"who": "danny", "at": "iris"}))
	tw.tween_interval(0.9)
	tw.tween_callback(func(): ia.clear_hands())


## Прикрепить предмет к кости так, чтобы он встал в мировое положение xf (кость — в текущей позе героя).
static func _attach_world(a: Actor, bone: String, item: Node3D, xf: Transform3D, ba_name := "") -> void:
	var ba := BoneAttachment3D.new()
	ba.bone_name = bone
	if ba_name != "":
		ba.name = ba_name
	a.skel.add_child(ba)
	var bi := a.skel.find_bone(bone)
	var bone_w := a.skel.global_transform * a.skel.get_bone_global_pose(bi)
	ba.add_child(item)
	item.transform = bone_w.affine_inverse() * xf


## Одеяло из багажника: правая рука под ящик, левая приподнимает ящик (лязг), одеяло встряхнуто — и под мышку.
## Багажник по листу «24»: сверху над плечом Ноя — отцовские удочки в потёртом брезентовом чехле, металлический ящик
## с инструментами, под ним — свёрнутое клетчатое одеяло. Было: две палочки вместо удочек, одеяло рядом с ящиком,
## а что внутри — перечисляла подпись (убрана: всё в кадре). При deer = 2 сверху брошена монтировка в бурых пятнах
## с прилипшей шерстью. Вызывается сразу, как багажник открылся (тот же кадр — без подмены на глазах).
static func dress_trunk(g) -> void:
	var car: Node3D = g.scene.car
	var tr: Node3D = car.get_node_or_null("trunk_open")
	if tr == null or tr.has_meta("dressed"):
		return
	tr.set_meta("dressed", true)
	var old := tr.find_child("blanket", true, false) as Node3D
	if old == null:
		return
	var niche := old.get_parent() as Node3D
	var box: MeshInstance3D = null
	var handle: MeshInstance3D = null
	for c in niche.get_children():
		if not c is MeshInstance3D:
			continue
		var mesh = (c as MeshInstance3D).mesh
		if mesh is CylinderMesh:
			c.queue_free()   # две палочки-удочки — заменяет чехол
		elif mesh is BoxMesh:
			var sz: Vector3 = (mesh as BoxMesh).size
			if absf(sz.x - 0.42) < 0.01:
				box = c
			elif absf(sz.x - 0.3) < 0.01 and absf(sz.y - 0.02) < 0.005:
				handle = c
	old.name = "blanket_old"
	old.queue_free()
	var f := _folded()   # тот же клетчатый плед, что потом под мышкой у Ноя и на плечах у Дэнни
	f.name = "blanket"
	niche.add_child(f)
	f.position = Vector3(-0.36, 0.013, 0.07)
	if box:   # ящик — поверх одеяла
		box.position.y += 0.08
	if handle:
		handle.position.y += 0.08
	var canvas := StationProps._mat(Color(0.2, 0.22, 0.13), 0.92)   # выгоревший брезент
	var rc := StationProps._rod(niche, Vector3(-0.64, 0.035, -0.17), Vector3(0.58, 0.035, -0.17), 0.032, canvas)
	rc.name = "rod_case"
	StationProps._rod(niche, Vector3(-0.6, 0.066, -0.17), Vector3(0.52, 0.066, -0.17), 0.0035, StationProps._mat(Color(0.05, 0.05, 0.05), 0.5))   # молния
	StationProps._rod(niche, Vector3(-0.2, 0.035, -0.17), Vector3(-0.17, 0.035, -0.17), 0.034, StationProps._mat(Color(0.13, 0.11, 0.08), 0.8))   # ремешок
	for k in 2:   # из открытого конца чехла — пробковые рукояти удилищ
		var y := 0.03 + k * 0.017
		var z := -0.18 + k * 0.02
		StationProps._rod(niche, Vector3(0.56, y, z), Vector3(0.72, y, z), 0.011, StationProps._mat(Color(0.62, 0.47, 0.3), 0.85))
		StationProps._rod(niche, Vector3(0.72, y, z), Vector3(0.735, y, z), 0.012, StationProps._mat(Color(0.08, 0.08, 0.08), 0.5))
	if float(g.flags.get("deer", 0)) >= 2.0:   # монтировка: бурые пятна, прилипшая шерсть
		var iron := Node3D.new()
		iron.name = "tire_iron"
		niche.add_child(iron)
		var steel := StationProps._mat(Color(0.18, 0.18, 0.19), 0.45, 0.7)
		var a := Vector3(-0.05, 0.08, -0.08)
		var b := Vector3(0.36, 0.08, 0.06)
		StationProps._rod(iron, a, b, 0.009, steel)
		StationProps._rod(iron, b, b + Vector3(0.03, 0.0, 0.09), 0.011, steel)   # изогнутый конец с ключом
		var blood := StationProps._mat(Color(0.2, 0.03, 0.02), 0.35)
		var fur := StationProps._mat(Color(0.42, 0.3, 0.2), 0.95)
		for k in 5:
			var p := a.lerp(b, 0.55 + k * 0.08)
			var sp := MeshInstance3D.new()
			var sm := SphereMesh.new()
			sm.radius = 0.012
			sm.height = 0.012
			sp.mesh = sm
			sp.material_override = blood
			iron.add_child(sp)
			sp.position = p + Vector3(0, 0.004, 0)
			sp.scale = Vector3(1.4, 0.5, 1.0)
			if k % 2 == 0:
				StationProps._box(iron, Vector3(0.022, 0.004, 0.006), p + Vector3(0.004, 0.011, 0.004), fur, Vector3(0, 30.0 * k, 12))


## Мысль Ноя о рыбалке: он наклоняется в багажник и касается пальцами чехла удочек, потом убирает руку.
static func _touch_rods(g) -> void:
	var na: Actor = g.actors.noah
	var car: Node3D = g.scene.car
	na.clear_hands()
	na.reach = {"_r": car.to_global(Vector3(0.14, 1.1, 1.87))}
	na.reach_palm = {"_r": Vector3.DOWN}
	na.reach_lean = 40.0   # наклонился в багажник: чехол — у дальнего борта
	var tw: Tween = g.create_tween()
	tw.tween_interval(2.2)
	tw.tween_callback(na.clear_hands)


## deer = 2: рука Ноя тянется к одеялу и останавливается над монтировкой — не касается.
static func _trunk_iron(g) -> void:
	var na: Actor = g.actors.noah
	var car: Node3D = g.scene.car
	na.clear_hands()
	na.reach = {"_r": car.to_global(Vector3(0.12, 1.22, 2.0))}
	na.reach_palm = {"_r": Vector3.DOWN}
	na.reach_lean = 32.0
	var tw: Tween = g.create_tween()
	tw.tween_interval(2.0)
	tw.tween_callback(na.clear_hands)


## Автомат по листу «24» — подписи-описания убраны, всё действием. Батончик, который «повиснет», — третий ряд снизу,
## середина (у машины ряды и столбцы — бруски за стеклом, см. StationProps.build).
const VEND_BAR := Vector3(-0.322, 1.33, -0.1)
static func _vend_bar(vm: Node3D) -> MeshInstance3D:
	for c in vm.get_children():
		if c is MeshInstance3D and (c as Node3D).position.distance_to(VEND_BAR) < 0.01:
			return c
	return null


static func _vend_glass(vm: Node3D) -> MeshInstance3D:
	for c in vm.get_children():
		if c is MeshInstance3D and (c as MeshInstance3D).mesh is BoxMesh and absf(((c as MeshInstance3D).mesh as BoxMesh).size.y - 1.12) < 0.01 \
				and absf(((c as MeshInstance3D).mesh as BoxMesh).size.x - 0.015) < 0.003:
			return c
	return null


## Монета из кармана джинсов замёрзшими пальцами — в щель (звон, перекат внутри); кнопка — спираль поворачивается
## на четверть и встаёт: батончик повис на краю. Автомат проглотил монету — и ничего.
static func _vend_coin(g) -> void:
	var na: Actor = g.actors.noah
	var vm := g.scene.station_root.get_node_or_null("vending") as Node3D
	if vm == null:
		return
	var bar := _vend_bar(vm)
	if bar:
		vm.set_meta("hang_bar", bar)   # повиснет и сдвинется — дальше (удар по стеклу) берём по ссылке, а не по месту
	na.clear_hands()
	var fwd := na.global_transform.basis.z.normalized()
	var right := -na.global_transform.basis.x.normalized()
	na.reach = {"_r": _bone(na, "pelvis") + fwd * 0.1 + right * 0.16 - Vector3(0, 0.03, 0)}   # передний карман джинсов
	var coin := MeshInstance3D.new()
	var cm := CylinderMesh.new()
	cm.top_radius = 0.012
	cm.bottom_radius = 0.012
	cm.height = 0.0025
	coin.mesh = cm
	coin.material_override = StationProps._mat(Color(0.76, 0.72, 0.58), 0.3, 0.9)
	var tw: Tween = g.create_tween()
	tw.tween_interval(0.65)
	tw.tween_callback(func():   # монета между большим и указательным — к щели на панели
		g.scene.station_root.add_child(coin)
		_hold(na, coin)
		coin.position = Vector3(-0.015, 0.11, 0.035)
		coin.rotation_degrees = Vector3(0, 0, 90)
		na.reach = {"_r": vm.to_global(Vector3(-0.43, 1.45, 0.29))})
	tw.tween_interval(0.85)
	tw.tween_callback(func():
		coin.queue_free()
		g.sfx.play("coin", -8.0))
	tw.tween_interval(0.55)
	tw.tween_callback(func(): na.reach = {"_r": vm.to_global(Vector3(-0.42, 1.24, 0.26))})   # к кнопке
	tw.tween_interval(0.4)
	tw.tween_callback(func():   # нажал: спираль на четверть — батончик сполз и повис на краю
		na.reach = {"_r": vm.to_global(Vector3(-0.375, 1.24, 0.26))}
		g.sfx.play("ui_click", -12.0)
		g.sfx.play("vend_motor", -8.0, 1.0, 0.12)
		if bar:
			var bt := bar.create_tween().set_parallel()
			bt.tween_property(bar, "position", VEND_BAR + Vector3(0.0, -0.035, 0.0), 0.55).set_delay(0.12)
			bt.tween_property(bar, "rotation_degrees", Vector3(0, 0, -9.0), 0.55).set_delay(0.12))
	tw.tween_interval(0.5)
	tw.tween_callback(func():
		na.clear_hands()
		if bar:
			g._look({"who": "noah", "at": [bar.global_position.x, bar.global_position.y, bar.global_position.z]}))


## Ладонью — не кулаком — по стеклу рядом с батончиком: стекло дрожит, батончик срывается и гулко падает в лоток;
## Ной нагибается, откидывает клапан лотка, достаёт батончик и убирает во внутренний карман.
static func _vend_slap(g) -> void:
	var na: Actor = g.actors.noah
	var vm := g.scene.station_root.get_node_or_null("vending") as Node3D
	if vm == null:
		return
	var bar: MeshInstance3D = vm.get_meta("hang_bar") if vm.has_meta("hang_bar") else _vend_bar(vm)
	if bar and not is_instance_valid(bar):
		bar = null
	var glass := _vend_glass(vm)
	na.clear_hands()
	na.reach = {"_r": vm.to_global(Vector3(-0.355, 1.36, -0.08))}
	na.reach_palm = {"_r": vm.global_transform.basis.x.normalized()}   # ладонь — к стеклу
	var tw: Tween = g.create_tween()
	tw.tween_interval(0.22)
	tw.tween_callback(func():
		g.sfx.play("slap", -4.0)
		g._shake = maxf(g._shake, 0.08)
		if glass:   # стекло дрожит в раме
			var x0 := glass.position.x
			var gt := glass.create_tween()
			for k in 4:
				gt.tween_property(glass, "position:x", x0 + (0.004 if k % 2 == 0 else -0.003) * (1.0 - k * 0.22), 0.04)
			gt.tween_property(glass, "position:x", x0, 0.05)
		if bar:   # сорвался — вниз, в лоток
			var bt := bar.create_tween()
			bt.tween_interval(0.08)
			bt.tween_property(bar, "position", Vector3(VEND_BAR.x + 0.01, 0.4, VEND_BAR.z), 0.32).set_trans(Tween.TRANS_QUAD).set_ease(Tween.EASE_IN)
			bt.parallel().tween_property(bar, "rotation_degrees", Vector3(70, 0, -20), 0.32)
			bt.tween_callback(func(): g.sfx.play("candy_drop", -6.0)))
	tw.tween_interval(0.3)
	tw.tween_callback(na.clear_hands)
	tw.tween_interval(0.35)
	tw.tween_callback(func():   # нагнулся к лотку
		na.reach = {"_r": vm.to_global(Vector3(-0.4, 0.42, -0.08))}
		na.reach_lean = 38.0)
	tw.tween_interval(0.75)
	tw.tween_callback(func():   # клапан — и батончик в руке
		g.sfx.play("knock", -24.0, 1.8)
		if bar:
			_hold(na, bar)
			bar.position = Vector3(0.0, 0.09, 0.03)
			bar.rotation_degrees = Vector3(0, 0, 90))
	tw.tween_interval(0.35)
	tw.tween_callback(func():   # выпрямился, глянул — и во внутренний карман
		na.reach_lean = 0.0
		var b := na.global_transform.basis
		na.reach = {"_r": na.global_position + Vector3(0, 1.2, 0) + b.z.normalized() * 0.32})
	tw.tween_interval(0.6)
	tw.tween_callback(func():
		var b := na.global_transform.basis
		na.reach = {"_r": _bone(na, "spine_03") + b.z.normalized() * 0.12 - b.x.normalized() * 0.06})
	tw.tween_interval(0.45)
	tw.tween_callback(func():
		g.sfx.play("wrapper", -18.0)
		if bar:
			bar.visible = false
		na.clear_hands())


## «Отойти»: постучал пальцем по стеклу — дважды — и отошёл.
static func _vend_tap(g) -> void:
	var na: Actor = g.actors.noah
	var vm := g.scene.station_root.get_node_or_null("vending") as Node3D
	if vm == null:
		return
	na.clear_hands()
	var near := vm.to_global(Vector3(-0.39, 1.34, -0.08))
	var touch := vm.to_global(Vector3(-0.345, 1.34, -0.08))
	na.reach = {"_r": near}
	var tw: Tween = g.create_tween()
	for k in 2:
		tw.tween_interval(0.3 if k == 0 else 0.16)
		tw.tween_callback(func():
			na.reach["_r"] = touch
			g.sfx.play("knock", -22.0, 1.9))
		tw.tween_interval(0.12)
		tw.tween_callback(func(): na.reach["_r"] = near)
	tw.tween_interval(0.25)
	tw.tween_callback(na.clear_hands)


## Айрис с блокнотом (лист «24», разговор с Айрис): рисует на коленях и не поднимает глаз; на «Нормально.» закрывает
## блокнот, прячет под полу худи и прижимает локтем (подпись «Она прячет блокнот…» убрана — всё в кадре).
static func _iris_notebook(g, show: bool) -> void:
	var ia: Actor = g.actors.get("iris")
	if ia == null:
		return
	var nb := ia.skel.find_child("iris_notebook", true, false) as Node3D
	if show:
		if nb == null:
			nb = Node3D.new()
			nb.name = "iris_notebook"
			StationProps._box(nb, Vector3(0.15, 0.012, 0.2), Vector3.ZERO, StationProps._mat(Color(0.14, 0.17, 0.26), 0.75))   # обложка
			StationProps._box(nb, Vector3(0.14, 0.004, 0.19), Vector3(0.002, 0.008, 0.0), StationProps._mat(Color(0.9, 0.88, 0.82), 0.9))   # лист
			for k in 3:   # штрихи карандаша на листе
				StationProps._box(nb, Vector3(0.06 - k * 0.012, 0.0015, 0.003), Vector3(-0.01 + k * 0.01, 0.0105, -0.04 + k * 0.03), StationProps._mat(Color(0.25, 0.25, 0.28), 0.9), Vector3(0, 20 - k * 15, 0))
			g.scene.station_root.add_child(nb)
			_hold(ia, nb)
			nb.position = Vector3(0.0, 0.1, 0.06)
			nb.rotation_degrees = Vector3(0, 0, 90)
		nb.visible = true
		var b := ia.global_transform.basis
		var lap := (_bone(ia, "thigh_l") + _bone(ia, "thigh_r")) * 0.5 + Vector3(0, 0.16, 0) + b.z.normalized() * 0.2
		var rt := -b.x.normalized()
		ia.reach = {"_r": lap + rt * 0.05, "_l": lap - rt * 0.09}
		ia.reach_palm = {"_r": Vector3.UP, "_l": Vector3.DOWN}
		ia.look_target = lap
		return
	ia.clear_hands()   # после жеста руки — как в позе сидя, а не снова на коленях с пустым блокнотом
	ia.look_target = null
	ia.do_gesture("chest", 1.8)   # к груди — под полу худи, локтем прижать
	if nb:
		var tw: Tween = g.create_tween()
		tw.tween_interval(0.7)
		tw.tween_callback(func():
			g.sfx.play("cloth", -16.0)
			nb.visible = false)


## Доска объявлений: Ной задевает пальцем загнувшийся угол старого плаката санатория и расправляет его; камера
## наезжает на название; палец убран — угол снова загибается. Шелест бумаги.
static func _poster_corner(g) -> void:
	var na: Actor = g.actors.noah
	var flap := g.scene.station_root.find_child("poster_corner", true, false) as Node3D
	if flap == null:
		return
	var pst := flap.get_parent() as Node3D
	flap.set_meta("held", true)   # ветер его больше не треплет (StationProps.flutter)
	var tip_up: Vector3 = flap.global_transform * Vector3(0.0, -0.085, 0.0)                  # загнутый край угла
	var tip_flat: Vector3 = pst.global_transform * (flap.position + Vector3(0.0, -0.085, 0.006))   # тот же край — расправленный
	na.clear_hands()
	na.point = {"_l": 1.0}
	na.set_meta("auto_grip", false)
	var aim := func(p: Vector3, back: float) -> void:   # кончик пальца — в точку p (back — не доходя), ладонь вниз
		_aim_finger(na, "_l", p, back)
	aim.call(tip_up, 0.06)
	var tw: Tween = g.create_tween()
	tw.tween_interval(0.55)
	tw.tween_callback(func(): aim.call(tip_up, 0.0))
	tw.tween_interval(0.15)
	tw.tween_callback(func():   # задел пальцем и расправил
		g.sfx.play("page_turn", -18.0, 1.3)
		flap.create_tween().tween_property(flap, "rotation_degrees:x", 0.0, 0.35).set_trans(Tween.TRANS_SINE)
		na.create_tween().tween_method(func(k: float): aim.call(tip_up.lerp(tip_flat, k), 0.0), 0.0, 1.0, 0.35))
	tw.tween_interval(3.25)
	tw.tween_callback(func():   # убрал палец — угол снова загнулся
		aim.call(tip_flat, 0.09)
		g.sfx.play("page_turn", -22.0, 1.5)
		flap.create_tween().tween_property(flap, "rotation_degrees:x", -58.0, 0.5).set_trans(Tween.TRANS_BACK).set_ease(Tween.EASE_OUT))
	tw.tween_interval(0.5)
	tw.tween_callback(func():
		flap.remove_meta("held")
		na.reach = {}
		na.point = {}
		na.set_meta("auto_grip", true))


## Кончик вытянутого указательного — в точку p (back — не доходя по ходу пальца), ладонью к palm: запястье ставится так,
## чтобы в точку пришёл именно кончик (он сбоку от оси кисти), а не продолжение предплечья.
static func _aim_finger(na: Actor, side: String, p: Vector3, back: float, palm := Vector3.DOWN) -> void:
	var fd: Vector3 = (p - _bone(na, "upperarm" + side)).normalized()
	var b := _point_basis(side, fd, palm)
	var tl := TIP_L if side == "_l" else Vector3(-TIP_L.x, TIP_L.y, TIP_L.z)
	na.reach[side] = p - fd * back - b * tl
	na.reach_basis[side] = b


## Базис кисти «палец в точку»: пальцы (+Y кости) — по fdir, ладонь — к palm. У левой кисти ладонь — +X кости, у правой —
## −X (зонд скелета: probe_geom, 5 ноя); +Z — от мизинца к указательному.
static func _point_basis(side: String, fdir: Vector3, palm: Vector3) -> Basis:
	var y := fdir.normalized()
	var pn := palm - y * palm.dot(y)
	if pn.length() < 0.01:
		pn = Vector3.FORWARD - y * Vector3.FORWARD.dot(y)
	pn = pn.normalized()
	var x := pn if side == "_l" else -pn
	return Basis(x, y, x.cross(y).normalized())


## Таксофон: трубка у уха в правой руке (клип), указательный левой набирает номер на металлических кнопках —
## без заминки, каждая кнопка пищит. Кисть — базисом: палец смотрит в кнопку (раньше рука шла по предплечью мимо).
static func _phone_dial_home(g) -> void:
	var na: Actor = g.actors.noah
	var ph := g.scene.station_root.find_child("payphone", true, false) as Node3D
	if ph == null:
		return
	var seq := [[1, 0], [0, 2], [2, 1], [1, 1], [3, 1], [0, 0], [2, 2]]   # (ряд, столбец) — домашний номер
	na.reach_lean = 0.0
	na.point = {"_l": 1.0}
	na.set_meta("auto_grip", false)
	var aim := func(key: Vector3, back: float) -> void:
		_aim_finger(na, "_l", key, back)
	var k0: Vector3 = ph.to_global(Vector3(-0.06 + 0.0 * 0.06, 1.5 - 1.0 * 0.055, 0.152))
	aim.call(k0, 0.05)
	var tw: Tween = g.create_tween()
	tw.tween_interval(0.45)   # рука поднимается к кнопкам
	for i in seq.size():
		var rk: int = seq[i][0]
		var cj: int = seq[i][1]
		var key: Vector3 = ph.to_global(Vector3(-0.06 + cj * 0.06, 1.5 - rk * 0.055, 0.152))   # лицо кнопки
		tw.tween_callback(func(): aim.call(key, 0.03))
		tw.tween_interval(0.13)
		tw.tween_callback(func():
			aim.call(key, -0.004)   # нажал
			g.sfx.play("phone_dial", -14.0, 1.0 + 0.04 * float((rk * 3 + cj) % 5)))
		tw.tween_interval(0.12)
	tw.tween_callback(func(): aim.call(k0 + Vector3(-0.06, -0.12, 0.05), 0.12))   # рука уходит от кнопок
	tw.tween_interval(0.3)
	tw.tween_callback(func():
		na.reach.erase("_l")
		na.reach_basis.erase("_l")
		na.point = {}
		na.set_meta("auto_grip", true))


## Витрина: Ной вплотную к стеклу, прикрывает глаза ладонью от света навеса; отражение Ноя лежит поверх магазина
## (плоское отражение PlanarMirror — только на этот план: вторая отрисовка сцены, держать его всё время дорого); от дыхания
## на стекле пятно — и тает; звуки улицы гаснут, остаётся гул неона.
static func _window_look(g) -> void:
	var na: Actor = g.actors.noah
	var root: Node3D = g.scene.station_root
	na.do_gesture("shade_eyes_l", 4.4)   # левой: камера над правым плечом видит отражение лица справа от головы
	var hd := _bone(na, "head")
	var f := Vector3(sin(na.rotation.y), 0.0, cos(na.rotation.y))   # куда смотрит Ной (лицом по +Z модели)
	var mouth := hd + f * 0.1 + Vector3(0, -0.1, 0)
	var fog := MeshInstance3D.new()
	var qm := QuadMesh.new()
	qm.size = Vector2(0.24, 0.15)
	fog.mesh = qm
	var m := StandardMaterial3D.new()
	m.albedo_color = Color(0.86, 0.89, 0.92, 0.0)
	m.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
	m.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
	m.cull_mode = BaseMaterial3D.CULL_DISABLED
	var gr := Gradient.new()   # мягкое пятно, без краёв
	gr.set_color(0, Color(1, 1, 1, 1))
	gr.set_color(1, Color(1, 1, 1, 0))
	gr.add_point(0.5, Color(1, 1, 1, 0.55))
	var gt := GradientTexture2D.new()
	gt.gradient = gr
	gt.fill = GradientTexture2D.FILL_RADIAL
	gt.fill_from = Vector2(0.5, 0.5)
	gt.fill_to = Vector2(1.0, 0.5)
	gt.width = 64
	gt.height = 64
	m.albedo_texture = gt
	fog.material_override = m
	root.add_child(fog)
	fog.global_position = Vector3(GLASS_X - 0.003, mouth.y, mouth.z)   # на наружной стороне стекла, напротив рта
	fog.rotation_degrees = Vector3(0, 90, 0)
	var tw: Tween = g.create_tween()
	tw.tween_interval(1.0)
	tw.tween_property(m, "albedo_color:a", 0.42, 0.45)
	tw.tween_property(m, "albedo_color:a", 0.0, 1.9)
	tw.tween_callback(fog.queue_free)
	var pm := PlanarMirror.new()   # стекло окна между стойками z −7,2 и −4,8 (station.py)
	root.add_child(pm)
	pm.build(Vector3(GLASS_X - 0.004, 2.105, -6.0), Vector3(-1, 0, 0), Vector2(2.28, 2.05), g.cam)
	var fq := clampi(Settings.fx, 0, 3)   # отражение — на 4 с: на «Высоком» и выше вдвое чётче (лицо — главное в кадре)
	if fq >= 2:
		pm._res = fq
		pm.vp.size = Vector2i(1138, 1024)
	var bits := 0   # в стекле — только сам Ной (его слой): улица, навес и колонки не рябят поверх магазина
	for mi in na.find_children("*", "MeshInstance3D", true, false):
		bits |= (mi as MeshInstance3D).layers
	pm.cam.cull_mask = Car.DRIVER_LAYER if (bits & Car.DRIVER_LAYER) != 0 else bits
	var lit: Array = []   # маска камеры отсекает и источники света (они тоже на слоях) — без этого Ной в стекле чёрный
	for l in root.find_children("*", "Light3D", true, false):
		if ((l as Light3D).layers & Car.DRIVER_LAYER) == 0:
			(l as Light3D).layers |= Car.DRIVER_LAYER
			lit.append(l)
	var mm := pm.quad.material_override as StandardMaterial3D
	mm.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
	mm.blend_mode = BaseMaterial3D.BLEND_MODE_ADD   # ночью витрина отражает только светлое: лицо в свете из магазина
	mm.albedo_color = Color(0.0, 0.0, 0.0, 1.0)
	var tr: Tween = g.create_tween()
	tr.tween_property(mm, "albedo_color", Color(0.62, 0.62, 0.66, 1.0), 0.6)
	tr.tween_interval(3.6)
	tr.tween_property(mm, "albedo_color", Color(0.0, 0.0, 0.0, 1.0), 0.5)
	tr.tween_callback(func():
		for l in lit:
			if is_instance_valid(l):
				(l as Light3D).layers &= ~Car.DRIVER_LAYER
		pm.queue_free())
	g.set_meta("hush_t", g._t + 4.4)   # звуки улицы гаснут — остаётся гул неона (фоны — game.gd)


## Трасса (лист «24»): следы их машины (StationProps.tire_tracks) на глазах заносит позёмком; при late — вторые, свежие.
## Позёмок — низкие волны снега вдоль земли, только на этот план.
static func _road_look(g, late: bool) -> void:
	var root: Node3D = g.scene.station_root
	var tr := root.get_node_or_null("tracks_ours") as MeshInstance3D
	if tr and not late:
		var tm := tr.material_override as StandardMaterial3D
		var tw: Tween = g.create_tween()
		tw.tween_interval(0.8)
		tw.tween_property(tm, "albedo_color:a", tm.albedo_color.a * 0.45, 3.6).set_trans(Tween.TRANS_SINE)
	if root.get_node_or_null("drift") == null:
		var d := CPUParticles3D.new()
		d.name = "drift"
		d.amount = Settings.pn(1400)
		d.lifetime = 2.6
		d.preprocess = 2.0
		d.emission_shape = CPUParticles3D.EMISSION_SHAPE_BOX
		d.emission_box_extents = Vector3(3.8, 0.04, 13.0)   # полоса вдоль трассы: от Ноя до въезда (их следы)
		d.position = Vector3(4.2, 0.08, 7.0)
		d.direction = Vector3(0.12, 0.06, -1.0)
		d.spread = 8.0
		d.gravity = Vector3(0, -0.15, 0)
		d.initial_velocity_min = 2.2
		d.initial_velocity_max = 4.2
		d.scale_amount_min = 0.5
		d.scale_amount_max = 1.6
		var qm := QuadMesh.new()
		qm.size = Vector2(0.09, 0.03)
		d.mesh = qm
		var sm := StandardMaterial3D.new()
		sm.billboard_mode = BaseMaterial3D.BILLBOARD_PARTICLES
		sm.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
		sm.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
		sm.vertex_color_use_as_albedo = true
		sm.albedo_color = Color(0.86, 0.9, 0.98, 0.5)
		var gr := Gradient.new()
		gr.set_color(0, Color(1, 1, 1, 1))
		gr.set_color(1, Color(1, 1, 1, 0))
		var gt := GradientTexture2D.new()
		gt.gradient = gr
		gt.fill = GradientTexture2D.FILL_RADIAL
		gt.fill_from = Vector2(0.5, 0.5)
		gt.fill_to = Vector2(1.0, 0.5)
		gt.width = 32
		gt.height = 32
		sm.albedo_texture = gt
		d.material_override = sm
		var cr := Gradient.new()   # волнами: частица проявляется и гаснет
		cr.set_color(0, Color(1, 1, 1, 0))
		cr.set_color(1, Color(1, 1, 1, 0))
		cr.add_point(0.3, Color(1, 1, 1, 0.85))
		cr.add_point(0.7, Color(1, 1, 1, 0.6))
		d.color_ramp = cr
		root.add_child(d)
		g.sfx.play("snow_gust", -10.0, 1.0, 0.3)   # порыв: шорох позёмка
		var tw2: Tween = g.create_tween()
		tw2.tween_interval(9.0 if late else 6.5)
		tw2.tween_callback(func(): d.emitting = false)
		tw2.tween_interval(2.8)
		tw2.tween_callback(d.queue_free)


## Чёрная машина (лист «24»): Ной зашёл в зону у края площадки — Айрис встаёт со ступеньки и делает шаг за ним (реплика —
## следующим шагом). Через несколько секунд возвращается и садится обратно (план прогулки её не видит — она за спиной).
static func _iris_warn(g) -> void:
	var ir: Actor = g.actors.get("iris")
	var na: Actor = g.actors.noah
	if ir == null or not is_instance_valid(ir):
		return
	var up := Vector3(19.55, 0.0, -4.1)   # встала: на земле перед ступенькой
	var to := na.global_position - up
	var yaw := atan2(to.x, to.z)
	var step := up + Vector3(to.x, 0.0, to.z).normalized() * 0.6
	ir.stop_walk()
	ir.play("Idle", 0.0, 0.45)
	var tw: Tween = g.create_tween()
	tw.tween_property(ir, "global_position", up, 0.45).set_trans(Tween.TRANS_SINE).set_ease(Tween.EASE_OUT)
	tw.parallel().tween_property(ir, "rotation:y", yaw, 0.45)
	tw.tween_callback(func():
		ir.placed()
		ir.walk_to(step, 1.3, "Rk_Walk", "Idle", rad_to_deg(yaw)))
	tw.tween_interval(5.0)
	tw.tween_callback(func():   # постояла, глядя ему вслед, — и назад, на ступеньку к Дэнни
		if is_instance_valid(ir):
			ir.walk_to(up, 1.0, "Rk_Walk", "Idle", -90.0))
	tw.tween_interval(1.4)
	tw.tween_callback(func():
		if is_instance_valid(ir):
			g._place({"who": "iris", "at": [20.08, 0.38, -4.05], "face": -90, "clip": "Rk_SitIdle"}))
	tw.tween_interval(0.06)
	tw.tween_callback(func():
		if is_instance_valid(ir):
			g._sit("iris", Vector3(19.98, 0.48, -4.05))
			g._look({"who": "iris", "at": "danny"}))


static func _blanket_carry(g) -> void:
	var na: Actor = g.actors.noah
	var car: Node3D = g.scene.car
	StationProps.trunk_take_blanket(car)
	var tr: Node3D = car.get_node_or_null("trunk_open")
	var box: Node3D = null
	if tr:   # ящик с инструментами — самый широкий красный брусок в нише
		for c in tr.find_children("*", "MeshInstance3D", true, false):
			var bm = (c as MeshInstance3D).mesh
			if bm is BoxMesh and absf((bm as BoxMesh).size.x - 0.42) < 0.01:
				box = c
	na.clear_hands()
	var src: Vector3 = car.to_global(Vector3(-0.14, 1.07, 2.06))   # край одеяла из-под ящика
	na.reach = {"_r": src, "_l": car.to_global(Vector3(-0.4, 1.2, 2.06))}   # левая — приподнимает ящик за ручку
	na.reach_lean = 40.0
	var f := _folded()
	g.scene.station_root.add_child(f)
	f.visible = false
	var tw: Tween = g.create_tween()
	tw.tween_interval(0.45)
	tw.tween_callback(func():
		if box:
			box.create_tween().tween_property(box, "position:y", box.position.y + 0.04, 0.2)
		g.sfx.play("trunk", -18.0, 1.4)
		f.visible = true
		_hold(na, f)
		f.position = Vector3(0.0, 0.1, 0.06))
	tw.tween_interval(0.35)
	tw.tween_callback(func():   # встряхнул: короткий рывок вверх, шорох
		if box:   # одеяла под ящиком больше нет — ящик ложится на дно
			box.create_tween().tween_property(box, "position:y", box.position.y - 0.12, 0.15)
			for hc in box.get_parent().get_children():
				if hc is MeshInstance3D and (hc as MeshInstance3D).mesh is BoxMesh and absf(((hc as MeshInstance3D).mesh as BoxMesh).size.x - 0.3) < 0.01:
					hc.create_tween().tween_property(hc, "position:y", (hc as Node3D).position.y - 0.08, 0.15)
		na.reach_lean = 0.0
		na.reach = {"_r": na.global_position + Vector3(0, 1.15, 0) + na.global_transform.basis.z * 0.35}
		g.sfx.play("cloth", -12.0))
	tw.tween_interval(0.5)
	tw.tween_callback(func():   # под левую руку: между плечом и рёбрами, длинной стороной вперёд
		na.clear_hands()
		f.get_parent().remove_child(f)
		var up := Vector3.UP
		var fwd := na.global_transform.basis.z.normalized()
		var left := up.cross(fwd).normalized()   # у героя, смотрящего к +Z, левая сторона — +X
		var sh := _bone(na, "upperarm_l")
		var p := sh - up * 0.2 + fwd * 0.17   # зажато под мышкой: задний край у плеча, остальное торчит вперёд (не внутри груди)
		var xf := Transform3D(Basis(fwd, left, up).orthonormalized(), p)   # длинной стороной вперёд, стопка — поперёк
		_attach_world(na, "upperarm_l", f, xf, "blanket_carry")
		na.set_meta("blanket_carry", true))


## Обёртка на плечах: оболочка вокруг груди и плеч (по плечи, край — под подбородком), запахнута спереди внахлёст,
## мягкие складки книзу. Строится по текущей позе мальчика и крепится к груди (spine_03).
static func _wrap(da: Actor) -> MeshInstance3D:
	var fwd := da.global_transform.basis.z.normalized()
	var up := (_bone(da, "neck_01") - _bone(da, "spine_01")).normalized()
	var side := fwd.cross(up).normalized()   # поперёк плеч (сторона не важна — оболочка симметрична, кроме запаха)
	var neck := _bone(da, "neck_01") + up * 0.05
	var chest := _bone(da, "spine_03")
	var waist := _bone(da, "spine_01") - up * 0.06
	var half := (_bone(da, "upperarm_l") - _bone(da, "upperarm_r")).length() * 0.5
	var rx := half + 0.15   # поверх пуховой куртки с рукавами (меньше — оболочка пряталась внутри куртки, торчал только ворот)
	var rz := 0.2
	var st := SurfaceTool.new()
	st.begin(Mesh.PRIMITIVE_TRIANGLES)
	var NT := 14
	var NA := 44
	var span := TAU + 0.5
	var a0 := PI - 0.25   # запах — посередине спереди: руки выходят между краями
	var rows := []
	for i in NT + 1:
		var t := float(i) / NT
		var c: Vector3 = neck.lerp(chest, smoothstep(0.0, 0.45, t)) if t < 0.45 else chest.lerp(waist, (t - 0.45) / 0.55)
		c += fwd * 0.04 * smoothstep(0.1, 0.5, t)
		var k := smoothstep(0.0, 0.3, t)   # у шеи — узко (подоткнуто под подбородок), к плечам — во всю ширину
		var ax := lerpf(0.11, rx, k) * (1.0 + 0.05 * smoothstep(0.7, 1.0, t))
		var az := lerpf(0.105, rz, k)
		var row := []
		for j in NA + 1:
			var u := float(j) / NA
			var th := a0 + u * span
			var fold := 1.0 + 0.045 * sin(th * 8.0 + t * 3.0) * smoothstep(0.25, 1.0, t)
			var grow := 1.0 + 0.04 * u   # запах: внешний край поверх внутреннего
			var p := c + (-fwd * cos(th) * az + side * sin(th) * ax) * fold * grow
			row.append(p)
		rows.append(row)
	var ba_w := da.skel.global_transform * da.skel.get_bone_global_pose(da.skel.find_bone("spine_03"))
	var inv := ba_w.affine_inverse()
	for i in NT:
		for j in NA:
			var q := [[i, j], [i + 1, j], [i + 1, j + 1], [i, j], [i + 1, j + 1], [i, j + 1]]
			for v in q:
				st.set_uv(Vector2(float(v[1]) / NA * 2.2, float(v[0]) / NT * 0.9))
				st.add_vertex(inv * (rows[v[0]][v[1]] as Vector3))
	st.generate_normals()
	var mi := MeshInstance3D.new()
	mi.name = "blanket_wrap"
	mi.mesh = st.commit()
	mi.material_override = plaid()
	var ba := BoneAttachment3D.new()
	ba.name = "blanket_wrap_ba"
	ba.bone_name = "spine_03"
	da.skel.add_child(ba)
	ba.add_child(mi)
	return mi


## «Укройся»: одеяло из-под руки Ноя — в руки Айрис; встряхнула и накинула мальчику на плечи, подоткнула под подбородок.
static func _blanket_give(g) -> void:
	var na: Actor = g.actors.noah
	var ia: Actor = g.actors.iris
	var da: Actor = g.actors.danny
	var f: Node3D = na.skel.find_child("blanket_folded", true, false) as Node3D
	if f == null:
		f = _folded()
		g.scene.station_root.add_child(f)
	var mid := (_bone(da, "spine_03") + _bone(ia, "spine_03")) * 0.5 + Vector3(-0.25, -0.1, 0.0)
	_hold(na, f)
	f.position = Vector3(0.0, 0.1, 0.06)
	na.set_meta("blanket_carry", null)
	na.reach = {"_r": mid}
	var tw: Tween = g.create_tween()
	tw.tween_interval(0.6)
	tw.tween_callback(func():
		ia.reach = {"_r": mid + Vector3(0, 0.02, -0.06), "_l": mid + Vector3(0, 0.02, 0.06)})
	tw.tween_interval(0.45)
	tw.tween_callback(func():   # в руках у Айрис; Ной отпускает
		_hold(ia, f)
		f.position = Vector3(0.0, 0.1, 0.06)
		na.clear_hands())
	tw.tween_interval(0.15)
	tw.tween_callback(func():   # встряхнула: руки вверх и в стороны, плед раскрывается
		var c := _bone(ia, "spine_03") + ia.global_transform.basis.z * 0.35 + Vector3(0, 0.15, 0)
		ia.reach = {"_r": c + ia.global_transform.basis.x * -0.22, "_l": c + ia.global_transform.basis.x * 0.22}
		f.create_tween().tween_property(f, "scale", Vector3(2.6, 0.4, 2.2), 0.3)
		g.sfx.play("cloth", -10.0))
	tw.tween_interval(0.45)
	tw.tween_callback(func():   # на плечи мальчику
		f.queue_free()
		var w := _wrap(da)
		w.scale = Vector3(1.18, 1.1, 1.18)
		w.create_tween().tween_property(w, "scale", Vector3.ONE, 0.55).set_trans(Tween.TRANS_SINE).set_ease(Tween.EASE_OUT)
		ia.reach = {"_r": _bone(da, "upperarm_r") + Vector3(0, 0.06, 0), "_l": _bone(da, "upperarm_l") + Vector3(0, 0.06, 0)}
		ia.reach_lean = 20.0)
	tw.tween_interval(0.6)
	tw.tween_callback(func():   # подоткнула край под подбородок
		ia.reach = {"_r": _bone(da, "neck_01") + da.global_transform.basis.z * 0.1 + Vector3(0, -0.03, 0)}
		g.sfx.play("cloth", -18.0, 1.2))
	tw.tween_interval(0.5)
	tw.tween_callback(func():   # плечи опускаются — он перестаёт дрожать
		ia.clear_hands()
		ia.reach_lean = 0.0
		g._expr({"who": "danny", "set": "sad"})
		da.set_meta("blanket", true))


## «Держи»: батончик из кармана Ноя — мальчику; он берёт его обеими руками, разглядывает, не ест — прячет
## во внутренний карман, прижимает ладонью и кивает: на потом.
static func _choco_give(g) -> void:
	var na: Actor = g.actors.noah
	var da: Actor = g.actors.danny
	var bar := Node3D.new()
	bar.name = "choco"
	var w := MeshInstance3D.new()
	var bm := BoxMesh.new()
	bm.size = Vector3(0.035, 0.013, 0.12)
	w.mesh = bm
	var m := StandardMaterial3D.new()
	m.albedo_color = Color(0.45, 0.06, 0.05)   # красная обёртка
	m.roughness = 0.35
	m.metallic = 0.3
	w.material_override = m
	bar.add_child(w)
	var lab := MeshInstance3D.new()   # светлая полоса надписи поперёк
	var lm := BoxMesh.new()
	lm.size = Vector3(0.036, 0.014, 0.03)
	lab.mesh = lm
	var l2 := StandardMaterial3D.new()
	l2.albedo_color = Color(0.85, 0.75, 0.45)
	l2.roughness = 0.4
	lab.material_override = l2
	bar.add_child(lab)
	g.scene.station_root.add_child(bar)
	_hold(na, bar)
	var mid := (_bone(na, "hand_r") + _bone(da, "spine_02")) * 0.5 + Vector3(0, 0.05, 0)
	var dmid := _bone(da, "spine_02") + da.global_transform.basis.z * 0.28 + Vector3(0, 0.05, 0)
	na.reach = {"_r": dmid - da.global_transform.basis.z * -0.05}
	g.sfx.play("wrapper", -16.0)
	var tw: Tween = g.create_tween()
	tw.tween_interval(0.65)
	tw.tween_callback(func():   # обеими руками
		var side := da.global_transform.basis.x * 0.045
		da.reach = {"_r": dmid - side, "_l": dmid + side}
		da.look_target = dmid)
	tw.tween_interval(0.5)
	tw.tween_callback(func():
		_hold(da, bar)
		na.clear_hands()
		da.reach = {"_r": dmid + Vector3(0, 0.1, 0) + da.global_transform.basis.z * -0.06}   # ближе к глазам — разглядывает
		g.sfx.play("wrapper", -20.0, 1.1))
	tw.tween_interval(0.85)
	tw.tween_callback(func():   # во внутренний карман: под полу куртки слева на груди
		var pocket := _bone(da, "spine_03") + da.global_transform.basis.x * 0.07 + da.global_transform.basis.z * 0.06 - Vector3(0, 0.04, 0)
		da.reach = {"_r": pocket}
		da.look_target = null)
	tw.tween_interval(0.45)
	tw.tween_callback(func():
		bar.visible = false
		da.clear_hands()
		da.do_gesture("chest", 1.6)   # ладонь прижата к карману
		g.sfx.play("cloth", -22.0, 1.4))
	tw.tween_interval(1.0)
	tw.tween_callback(func():
		da.do_gesture("nod", 1.0)
		da.set_meta("choco", true)
		bar.queue_free())


# ------------------------------------------------------------------ эпизод 7: дети у машины и отъезд
const RL_DOOR := Vector3(12.98, 0.0, -3.95)    # у левой задней двери, в проходе между машиной и левым островком


## Айрис ведёт Дэнни за руку из-за машины к левой задней двери; Ной идёт навстречу от колонки.
static func _kids_to_car(g) -> void:
	var lapse := float(g.flags.get("lapse1", 0)) > 0.0
	var da: Actor = g.actors.danny
	var ia: Actor = g.actors.iris
	var na: Actor = g.actors.noah
	da.clear_hands()
	ia.clear_hands()
	g._place({"who": "danny", "at": [13.35, 0.0, -1.35], "face": -150, "clip": "Idle"})
	g._place({"who": "iris", "at": [12.98, 0.0, -1.2], "face": -150, "clip": "Idle"})   # слева от него: в правой у Дэнни рация
	g._place({"who": "noah", "at": [12.95, 0.0, -5.7], "face": 0, "clip": "Idle"})
	g._walk({"who": "danny", "to": [RL_DOOR.x, 0.0, RL_DOOR.z + 0.2], "then": "Idle", "face": 180, "speed": 0.9})
	g._walk({"who": "iris", "to": [RL_DOOR.x - 0.2, 0.0, RL_DOOR.z + 0.75], "then": "Idle", "face": 180, "speed": 0.9})
	g._walk({"who": "noah", "to": [12.95, 0.0, -5.1], "then": "Idle", "face": 0, "speed": 0.7})
	g._expr({"who": "danny", "set": "fear" if lapse else "sad"})
	da.look_target = g._target("noah")
	ia.look_target = g._target("noah")
	na.look_target = g._target("iris")
	if float(g.flags.get("choco_given", 0)) > 0.0:   # лист, эпизод 7: ладонь (с рацией) прижата к карману с батончиком
		da.do_gesture("chest", 30.0)
	var tw: Tween = g.create_tween()   # рука в руке: правая Айрис держит левую Дэнни (в правой у него рация)
	tw.tween_interval(0.2)
	tw.tween_method(func(_f: float):
		if is_instance_valid(da) and is_instance_valid(ia) and da.walking:
			ia.reach = {"_r": _bone(da, "hand_l") + Vector3(0, 0.02, 0)}, 0.0, 1.0, 3.2)
	tw.tween_callback(func(): ia.reach = {"_r": _bone(da, "hand_l") + Vector3(0, 0.02, 0)})


## Дэнни пятится в снег, вырывая руку у Айрис.
static func _danny_back_away(g) -> void:
	var da: Actor = g.actors.danny
	var ia: Actor = g.actors.iris
	ia.clear_hands()
	g._expr({"who": "danny", "set": "fear"})
	da.play("Walk", 0.0, 0.2)
	da.ap.speed_scale = -0.9
	var tw: Tween = g.create_tween()
	tw.tween_property(da, "position", da.position + Vector3(-0.15, 0.0, 0.85), 1.2).set_trans(Tween.TRANS_SINE).set_ease(Tween.EASE_OUT)
	tw.tween_callback(func(): da.play("Idle", 0.0, 0.25))
	da.do_gesture("chest", 6.0)


## Айрис опускается перед мальчиком на корточки и берёт его за оба плеча.
static func _iris_kneel(g) -> void:
	var da: Actor = g.actors.danny
	var ia: Actor = g.actors.iris
	var d0 := da.global_position
	g._place({"who": "iris", "at": [d0.x + 0.05, 0.0, d0.z - 0.55], "face": 10, "clip": "Crouch_Idle"})
	ia.reach_lean = 20.0
	var tw: Tween = g.create_tween()
	tw.tween_interval(0.35)
	tw.tween_callback(func():
		ia.reach = {"_r": _bone(da, "upperarm_l") + Vector3(0, 0.02, 0), "_l": _bone(da, "upperarm_r") + Vector3(0, 0.02, 0)}
		ia.look_target = g._target("danny"))


## Дэнни садится назад через левую заднюю дверь: руки на раме, колено на сиденье — и внутрь; дверь закрывается тихо.
static func _danny_climb_in(g) -> void:
	var da: Actor = g.actors.danny
	var ia: Actor = g.actors.iris
	var na: Actor = g.actors.noah
	ia.clear_hands()
	da.clear_hands()
	g.scene.car.door_open("rl", 0.8, 0.5)
	g._place({"who": "danny", "at": [13.05, 0.0, -3.85], "face": 90, "clip": "Idle"})
	da.reach = {"_r": g.scene.car.to_global(Vector3(-0.88, 1.18, 0.6)), "_l": g.scene.car.to_global(Vector3(-0.88, 1.18, 1.2))}
	var tw: Tween = g.create_tween()
	tw.tween_interval(0.45)
	tw.tween_callback(func(): da.play("Sitting_Enter", 0.0, 0.3))
	tw.tween_property(da, "position", Vector3(13.6, 0.18, -3.85), 0.9).set_trans(Tween.TRANS_SINE).set_ease(Tween.EASE_IN_OUT)
	tw.tween_callback(func():
		da.clear_hands()
		var lapse := float(g.flags.get("lapse1", 0)) > 0.0
		g._place({"who": "danny", "car": "back" if lapse else "back_l"})   # после провала — в дальний угол, подальше от Ноя
		g._expr({"who": "danny", "set": "fear" if lapse else "sad"})
		g.scene.car.door_open("rl", 0.0, 0.7)   # не хлопая — медленно
		g.sfx.play("car_door", -16.0, 1.05, 0.6)
		na.clear_hands())


## Айрис сама сажает мальчика и пристёгивает: дверь, Дэнни внутрь, она тянется через него к пряжке.
static func _iris_buckle(g) -> void:
	_danny_climb_in(g)
	var ia: Actor = g.actors.iris
	var tw: Tween = g.create_tween()
	tw.tween_interval(1.6)
	tw.tween_callback(func():
		ia.reach_lean = 40.0
		ia.reach = {"_r": g.scene.car.to_global(Vector3(-0.2, 0.62, 1.15))}
		g.sfx.play("belt_click", -10.0, 1.0, 0.7))
	tw.tween_interval(1.2)
	tw.tween_callback(func(): ia.clear_hands())


## «Нет. Он остаётся»: пальцы Айрис медленно разжимаются, рука мальчика выскальзывает; она идёт к машине, не глядя на Ноя.
static func _iris_release(g) -> void:
	var ia: Actor = g.actors.iris
	var da: Actor = g.actors.danny
	var hand := _bone(da, "hand_l")   # правая Айрис держит левую руку мальчика (в правой — рация)
	ia.reach = {"_r": hand}
	var tw: Tween = g.create_tween()
	tw.tween_interval(0.8)
	tw.tween_method(func(f: float):
		if ia.reach.has("_r"):
			ia.reach["_r"] = hand + Vector3(-0.02, 0.03, 0.08) * f, 0.0, 1.0, 1.0)
	tw.tween_callback(func():
		ia.clear_hands()
		ia.look_target = null)


## Ной садится за руль: открыл свою дверь, сел (край сиденья — внутрь), дверь закрыта.
static func _noah_to_car(g) -> void:
	var na: Actor = g.actors.noah
	na.clear_hands()
	var ia: Actor = g.actors.iris
	if ia.get_parent() != g.scene.car:   # Айрис уже села (вне кадра: справа дверь не открыть — островок в 16 см)
		ia.clear_hands()
		g._place({"who": "iris", "car": "passenger"})
		ia.look_target = null
	g._place({"who": "noah", "at": [12.98, 0.0, -4.45], "face": 90, "clip": "Idle"})
	g.scene.car.door_open("l", 0.72, 0.6)
	g.sfx.play("car_door", -9.0, 1.05)
	var tw: Tween = g.create_tween()
	tw.tween_interval(0.55)
	tw.tween_callback(func(): na.play("Sitting_Enter", 0.0, 0.3))
	tw.tween_property(na, "position", Vector3(13.5, 0.06, -4.6), 0.9).set_trans(Tween.TRANS_SINE).set_ease(Tween.EASE_IN_OUT)
	tw.tween_callback(func():
		g._place({"who": "noah", "car": "driver"})
		g.scene.car.door_open("l", 0.0, 0.45)
		g.sfx.play("car_door", -5.0, 1.0, 0.4))


## Витрина при отъезде — последствия кассы (лист «24», эпизод 7, шаг 3): police — Роман у телефона, трубка у уха;
## police_late — Роман с оборванным шнуром в руке смотрит на дверь; roman_ally — Роман смотрит им вслед, трубка
## лежит на прилавке; roman_hurt — поднимается с пола; roman_dead — не поднимается.
static func _roman_window(g) -> void:
	var ra: Actor = g.actors.roman
	var root: Node3D = g.scene.station_root
	var f: Dictionary = g.flags
	var props := StationProps.counter_props(root)
	var rc: Node3D = props.get("receiver")
	ra.end_gesture()
	if float(f.get("roman_dead", 0)) > 0.0:
		return   # лежит, как лежал
	if float(f.get("roman_hurt", 0)) > 0.0:
		ra.ap.play("LayToIdle", 0.3)   # поднимается с пола, держась за голову
		ra.clear_hands()
		return
	g._place({"who": "roman", "at": [26.35, 0.38, -9.2], "face": -90, "clip": "Idle"})   # лицом к витрине: с улицы видно лицо и трубку
	if float(f.get("police_late", 0)) > 0.0:   # с оборванным шнуром в руке (лист: «шнур оборван, звонка не показывать»)
		# обрывок витого бежевого шнура свисает из кулака перед собой — на фоне тёмного свитера его видно и с улицы
		# (был чёрный прут 4 мм у бедра — сквозь витрину на дальнем плане не читался). Шнур — каждый кадр за кистью (tick)
		var cord := root.get_node_or_null("torn_cord") as MeshInstance3D
		if cord == null:
			cord = MeshInstance3D.new()
			cord.name = "torn_cord"
			var m := StandardMaterial3D.new()
			m.albedo_color = Color(0.45, 0.41, 0.34)   # как витой шнур трубки
			m.roughness = 0.6
			cord.material_override = m
			root.add_child(cord)
		root.set_meta("torn_cord_by", ra)
		var b := ra.global_transform.basis
		ra.reach = {"_r": ra.global_position + Vector3(0, 1.12, 0) + b.z.normalized() * 0.3 - b.x.normalized() * 0.1}
		ra.set_meta("auto_grip", false)
		ra.grip = {"_r": 0.95}
		g._look({"who": "roman", "at": [21.0, 1.6, -2.0]})   # на дверь
		return
	if float(f.get("police", 0)) > 0.0:   # у телефона: трубка у уха
		if rc:
			_hold(ra, rc)
		ra.reach = {"_r": ra.head_pos() - ra.global_transform.basis.x * 0.09 + Vector3(0, -0.03, 0.02)}
		ra.play("Idle_TalkingPhone", 0.0, 0.3)
		return
	if float(f.get("roman_ally", 0)) > 0.0:   # смотрит им вслед; трубка лежит на прилавке
		if rc:
			rc.reparent(root, true)
			rc.global_position = Vector3(26.0, TOP + 0.02, -8.6)
			rc.rotation = Vector3(0, 0.4, 1.5708)
		g._look({"who": "roman", "at": [14.3, 1.2, -9.0]})


## Отъезд: машина трогается от колонки вперёд, проезжает вдоль навеса мимо витрины и дугой выезжает на трассу.
static func _car_depart(g, dur := 7.0) -> void:
	var car: Car = g.scene.car
	var p0 := car.position
	var p1 := Vector3(p0.x, 0.0, -16.0)
	var p2 := Vector3(0.5, 0.0, -19.0)
	var p3 := Vector3(0.5, 0.0, -36.0)
	var bez := func(t: float) -> Vector3:
		var u := 1.0 - t
		return p0 * (u * u * u) + p1 * (3.0 * u * u * t) + p2 * (3.0 * u * t * t) + p3 * (t * t * t)
	var st := {"yaw": car.rotation.y}
	car.ride = true
	var tw: Tween = g.create_tween()
	tw.tween_method(func(k: float):
		var t := k * k * (1.6 - 0.6 * k)   # трогается плавно, разгоняется
		var p: Vector3 = bez.call(t)
		var d: Vector3 = (bez.call(minf(t + 0.004, 1.0)) as Vector3) - (bez.call(maxf(t - 0.004, 0.0)) as Vector3)
		var yaw := atan2(-d.x, -d.z) if d.length() > 1e-5 else float(st.yaw)
		car.position = p
		car.rotation.y = yaw
		car.speed = 2.0 + 10.0 * k
		car.steer = clampf(-wrapf(yaw - float(st.yaw), -PI, PI) * 55.0, -0.9, 0.9)
		st.yaw = yaw, 0.0, 1.0, dur)

