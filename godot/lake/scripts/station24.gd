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
		"walkie_hide", "walkie_show":   # рация Дэнни зажата коленями, пока он дышит в ладони (лист, шаг 3)
			var da: Actor = g.actors.get("danny")
			if da:
				for c in da.skel.get_children():
					if c is BoneAttachment3D and (c as BoneAttachment3D).bone_name == "hand_r":
						(c as Node3D).visible = k == "walkie_show"
		_:
			return false
	return true


## Шнуры телефона — каждый кадр, пока реквизит прилавка на месте (game.gd _process).
static func tick(g) -> void:
	var root = g.scene.station_root
	if root == null or not is_instance_valid(root) or root.get_node_or_null("counter_props") == null:
		return
	StationProps.cord_update(root, bool(g.flags.get("_cord_ripped", 0.0) > 0.0))


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
