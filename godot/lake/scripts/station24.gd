class_name St24
## Заправка «24», магазин (лист «24», эпизод 5 — docs/staging/ST24_STAGING_SHEET.md): постановка у прилавка. Роман на
## высоком табурете смотрит телевизор, узнаёт Ноя; руки Ноя на прилавке — в карманы; рука Романа под прилавком к
## телефону и трубка; ветки выбора: деньги и отход спиной, вырванный шнур (Роман отшатывается в стойку с сигаретами),
## «помогите сестре» (рука ложится на прилавок пустой). Реквизит — StationProps.counter_props.

const TOP := 1.445                              # верх столешницы (по ней кладутся руки и деньги)
const NOAH_AT := Vector3(26.4, 0.38, -7.62)     # Ной у прилавка, лицом к −Z


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
	if root.get_node_or_null("counter_props") != null:
		StationProps.cord_update(root, bool(g.flags.get("_cord_ripped", 0.0) > 0.0))
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
	hn.add_child(d)
	d.position = Vector3(0.022, 0.12, 0.03)
	var tw := d.create_tween()
	tw.tween_interval(0.6)
	tw.tween_property(d, "position", Vector3(0.022, 0.02, 0.03), 1.4).set_trans(Tween.TRANS_QUAD).set_ease(Tween.EASE_IN)
	tw.tween_callback(func():
		var gp := d.global_position
		d.reparent(g.scene.station_root, true)
		var tf := d.create_tween()
		tf.tween_property(d, "global_position", Vector3(gp.x, 0.02, gp.z), 0.35).set_trans(Tween.TRANS_QUAD).set_ease(Tween.EASE_IN)
		tf.tween_callback(d.queue_free))


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
	g.set_meta("dock_prev", [g._shot, g._cam_free])
	var hand := Vector3.ZERO
	var an2 := set_.get_node_or_null("anna_dock") as Actor
	if an2:
		hand = an2.skel.global_transform * an2.skel.get_bone_global_pose(an2.skel.find_bone("hand_r")).origin
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


## Предмет — в правую кисть героя (BoneAttachment), трубкой вдоль ладони.
static func _hold(a: Actor, item: Node3D) -> void:
	var ba := a.skel.get_node_or_null("prop_hand_r") as BoneAttachment3D
	if ba == null:
		ba = BoneAttachment3D.new()
		ba.name = "prop_hand_r"
		ba.bone_name = "hand_r"
		a.skel.add_child(ba)
	item.reparent(ba, false)
	item.position = Vector3(0.0, 0.09, 0.03)
	item.rotation_degrees = Vector3(0, 0, 90)
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
	var p1 := p0 + na.global_transform.basis.z * -1.0 * -1.6   # назад = против взгляда (лицом к −Z → идём к +Z)
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
	if rc and not (rc.get_parent() is BoneAttachment3D):
		_hold(ra, rc)
	var ear := ra.head_pos() - ra.global_transform.basis.x * 0.09 + Vector3(0, -0.03, 0.02)
	var key := StationProps.PHONE_AT + Vector3(0.03, 0.05, -0.02)
	ra.reach = {"_r": ear, "_l": key}
	ra.reach_lean = 25.0
	var tw: Tween = g.create_tween()
	tw.tween_interval(0.6)
	tw.tween_method(func(f: float):   # набирает: кисть постукивает по кнопкам
		if ra.reach.has("_l"):
			ra.reach["_l"] = key + Vector3(0.02 * sin(f * 9.0), 0.02 * absf(sin(f * 18.0)), 0.015 * cos(f * 7.0)), 0.0, 1.0, 1.8)
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
	var grab := Vector3(StationProps.PHONE_AT.x + 0.15, TOP + 0.1, -8.72)   # шнур над задним краем прилавка
	na.reach = {"_r": grab}
	na.reach_lean = 55.0
	na.reach_palm = {"_r": Vector3.DOWN}
	na.set_meta("auto_grip", false)
	na.grip = {"_r": 0.95}
	var tw: Tween = g.create_tween()
	tw.tween_interval(0.55)
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
		if ph:   # аппарат слетает с полки и падает на пол перед прилавком
			ph.reparent(root, true)
			var tp := ph.create_tween()
			tp.tween_property(ph, "global_position", Vector3(StationProps.PHONE_AT.x + 0.35, TOP + 0.25, -8.3), 0.2).set_trans(Tween.TRANS_QUAD).set_ease(Tween.EASE_OUT)
			tp.tween_property(ph, "global_position", Vector3(StationProps.PHONE_AT.x + 0.45, 0.42, -7.8), 0.35).set_trans(Tween.TRANS_QUAD).set_ease(Tween.EASE_IN)
			tp.parallel().tween_property(ph, "rotation_degrees", Vector3(70, 120, 30), 0.35)
		na.reach = {"_r": Vector3(NOAH_AT.x + 0.25, 1.35, -7.75)})   # рывок на себя


## Шнур — на пол, руки свободны.
static func _cord_drop(g) -> void:
	var na: Actor = g.actors.noah
	na.clear_hands()


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
	g._place({"who": "iris", "at": [13.75, 0.0, -1.2], "face": -150, "clip": "Idle"})
	g._place({"who": "noah", "at": [12.95, 0.0, -5.7], "face": 0, "clip": "Idle"})
	g._walk({"who": "danny", "to": [RL_DOOR.x, 0.0, RL_DOOR.z + 0.2], "then": "Idle", "face": 180, "speed": 0.9})
	g._walk({"who": "iris", "to": [RL_DOOR.x - 0.05, 0.0, RL_DOOR.z + 0.85], "then": "Idle", "face": 180, "speed": 0.9})
	g._walk({"who": "noah", "to": [12.95, 0.0, -5.1], "then": "Idle", "face": 0, "speed": 0.7})
	g._expr({"who": "danny", "set": "fear" if lapse else "sad"})
	da.look_target = g._target("noah")
	ia.look_target = g._target("noah")
	na.look_target = g._target("iris")
	var tw: Tween = g.create_tween()   # рука в руке: левая Айрис держит правую Дэнни
	tw.tween_interval(0.2)
	tw.tween_method(func(_f: float):
		if is_instance_valid(da) and is_instance_valid(ia) and da.walking:
			ia.reach = {"_l": _bone(da, "hand_r") + Vector3(0, 0.02, 0)}, 0.0, 1.0, 3.2)
	tw.tween_callback(func(): ia.reach = {"_l": _bone(da, "hand_r") + Vector3(0, 0.02, 0)})


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
	var hand := _bone(da, "hand_r")
	ia.reach = {"_l": hand}
	var tw: Tween = g.create_tween()
	tw.tween_interval(0.8)
	tw.tween_method(func(f: float):
		if ia.reach.has("_l"):
			ia.reach["_l"] = hand + Vector3(-0.02, 0.03, 0.08) * f, 0.0, 1.0, 1.0)
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
	g._place({"who": "roman", "at": [26.35, 0.38, -9.2], "face": 0, "clip": "Idle"})
	if float(f.get("police_late", 0)) > 0.0:   # с оборванным шнуром в руке
		var cord := MeshInstance3D.new()
		var cm := CylinderMesh.new()
		cm.top_radius = 0.004
		cm.bottom_radius = 0.004
		cm.height = 0.7
		cord.mesh = cm
		var m := StandardMaterial3D.new()
		m.albedo_color = Color(0.1, 0.1, 0.1)
		cord.material_override = m
		_hold(ra, cord)
		cord.position = Vector3(0.0, -0.25, 0.03)
		cord.rotation_degrees = Vector3.ZERO
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

