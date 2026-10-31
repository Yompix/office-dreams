class_name Village
extends Node3D
## Деревня у леса (глава 3): модель tools/lake/village.py → assets/models/village.glb, свет (окна, фонари на столбах, лампа
## и печь в доме Агнес, свечи, генератор), вывеска магазина на языке игры, предметы (топор на колоде, канистра, кружка).
## Координаты деревни — мировые: улица вдоль Z по x = 0 (там же дорога), дом Агнес — x −11, z −45, фасадом к улице.
## Планы камеры — SHOTS: [позиция, куда смотрит, угол обзора].

const AG := Vector3(-11.0, 0.0, -45.0)   # середина дома Агнес; пол в доме — y 0.5
const FLOOR := 0.5
const LOT := Rect2(-26.0, -104.0, 52.0, 130.0)   # где на земле нет леса (дорога встаёт, ели расступаются)

## точка в доме Агнес (местные координаты дома: фасад к +X, пол — y 0)
static func ag(x: float, y: float, z: float) -> Vector3:
	return AG + Vector3(x, FLOOR + y, z)

static var SHOTS := {
	"village_street": [Vector3(2.4, 1.8, 6.0), Vector3(-3.0, 1.6, -40.0), 46.0],
	"agnes_door": [Vector3(-5.0, 1.65, -43.9), Vector3(-7.65, 1.75, -45.0), 36.0],
	"house_wide": [ag(2.6, 1.55, -3.2), ag(-1.8, 0.6, 0.6), 64.0],
	"photos": [ag(-2.2, 1.65, 1.15), ag(-3.06, 1.72, 0.55), 30.0],
	"bed": [ag(0.1, 1.55, 0.55), ag(-2.2, 0.45, 1.45), 52.0],
	"danny_table": [ag(0.8, 0.45, 0.6), ag(2.2, 0.35, 1.6), 54.0],
	"press": [ag(-0.9, 1.15, 0.5), ag(-2.0, 0.55, 1.3), 38.0],
	"stove_two": [ag(1.3, 1.1, -0.4), ag(-1.0, 0.65, -1.7), 48.0],
	"stove_fire": [ag(-0.15, 0.55, -2.2), ag(-1.08, 0.95, -2.35), 34.0],
	"house_night": [ag(2.6, 1.4, -3.2), ag(-1.5, 0.5, 0.6), 62.0],
	"house_door": [ag(-1.2, 1.1, 1.4), ag(3.2, 1.0, 0.0), 52.0],
	"shop": [Vector3(4.0, 1.7, -20.0), Vector3(8.6, 1.9, -28.6), 44.0],
	"street_end": [Vector3(-4.8, 2.0, -41.0), Vector3(1.5, 1.0, -97.0), 32.0],
	"gardens": [Vector3(-21.5, 1.9, -60.0), Vector3(-17.5, 1.1, -47.0), 46.0],
	"tracks": [Vector3(2.0, 1.6, -16.0), Vector3(0.2, 0.0, -6.0), 46.0],
	"axe_back": [Vector3(6.9, 1.55, -90.6), Vector3(4.6, 1.25, -112.0), 44.0],
	"axe_iris": [Vector3(4.7, 1.55, -95.6), Vector3(6.3, 1.35, -88.4), 40.0],
	"axe_snow": [Vector3(6.9, 0.7, -95.0), Vector3(6.1, 0.05, -94.25), 36.0],
}

var pc := true
var model: Node3D
var bulb: OmniLight3D            # лампа под потолком в доме Агнес
var fire: OmniLight3D            # отсвет из устья печи
var fire_quad: MeshInstance3D
var candles: Array[OmniLight3D] = []
var street: Array[OmniLight3D] = []
var windows: Array[MeshInstance3D] = []
var power := true
var _t := 0.0


func build(lang: String, is_pc: bool) -> void:
	pc = is_pc
	model = (load("res://assets/models/village.glb") as PackedScene).instantiate() as Node3D
	add_child(model)
	for n in model.find_children("*", "MeshInstance3D", true, false):
		var mi := n as MeshInstance3D
		var nm := String(mi.name)
		if nm.contains("window") or nm.contains("glass") or nm.begins_with("lamp") or nm.begins_with("herb"):
			mi.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
		if nm.contains("window_lit"):
			windows.append(mi)
	var lb := Label3D.new()                                  # вывеска магазина
	lb.text = "МАГАЗИН" if lang == "ru" else "STORE"
	lb.font_size = 120
	lb.pixel_size = 0.0045
	lb.modulate = Color(0.95, 0.92, 0.8)
	lb.outline_size = 0
	lb.shaded = true
	lb.position = Vector3(8.25, 3.0, -27.4)
	lb.rotation_degrees = Vector3(0, -90, 0)
	add_child(lb)
	for zp in [-15.0, -55.0, -92.0]:                         # фонари на столбах
		var sl := OmniLight3D.new()
		sl.position = Vector3(3.55, 5.9, zp)
		sl.light_color = Color(1.0, 0.82, 0.55)
		sl.light_energy = 1.6
		sl.omni_range = 14.0
		sl.light_volumetric_fog_energy = 1.2
		add_child(sl)
		street.append(sl)
	for w in [[Vector3(-7.4, 1.6, -8.2), 1.0], [Vector3(-7.4, 1.6, -3.8), 0.8]]:   # тёплые окна дома h1 — свет на снег
		var ol := OmniLight3D.new()
		ol.position = w[0]
		ol.light_color = Color(1.0, 0.7, 0.4)
		ol.light_energy = w[1]
		ol.omni_range = 5.0
		add_child(ol)
	bulb = OmniLight3D.new()                                 # дом Агнес: лампа под потолком
	bulb.position = ag(0.2, 2.05, 0.4)
	bulb.light_color = Color(1.0, 0.8, 0.55)
	bulb.light_energy = 1.4
	bulb.omni_range = 7.5
	bulb.shadow_enabled = pc
	add_child(bulb)
	fire = OmniLight3D.new()                                 # печь: живой отсвет из устья
	fire.position = ag(-0.85, 0.95, -2.35)
	fire.light_color = Color(1.0, 0.5, 0.18)
	fire.light_energy = 1.2
	fire.omni_range = 4.5
	add_child(fire)
	var hole := MeshInstance3D.new()   # устье: тёмный проём в закопчённой дверце
	var hq := QuadMesh.new()
	hq.size = Vector2(0.44, 0.32)
	hole.mesh = hq
	var hm := StandardMaterial3D.new()
	hm.albedo_color = Color(0.035, 0.018, 0.01)
	hm.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
	hole.material_override = hm
	hole.position = ag(-1.03, 0.93, -2.35)
	hole.rotation_degrees = Vector3(0, 90, 0)
	add_child(hole)
	fire_quad = MeshInstance3D.new()   # угли на поду
	var q := QuadMesh.new()
	q.size = Vector2(0.38, 0.09)
	fire_quad.mesh = q
	var fm := StandardMaterial3D.new()
	fm.albedo_color = Color(1.0, 0.45, 0.1)
	fm.emission_enabled = true
	fm.emission = Color(1.0, 0.42, 0.08)
	fm.emission_energy_multiplier = 3.0
	fm.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
	fire_quad.material_override = fm
	fire_quad.position = ag(-1.027, 0.815, -2.35)
	fire_quad.rotation_degrees = Vector3(0, 90, 0)
	add_child(fire_quad)
	var fl := CPUParticles3D.new()   # язычки пламени в устье
	fl.amount = 48
	fl.lifetime = 0.55
	fl.emission_shape = CPUParticles3D.EMISSION_SHAPE_BOX
	fl.emission_box_extents = Vector3(0.004, 0.01, 0.16)
	fl.direction = Vector3(0, 1, 0)
	fl.spread = 10.0
	fl.initial_velocity_min = 0.12
	fl.initial_velocity_max = 0.3
	fl.gravity = Vector3(0, 0.2, 0)
	fl.scale_amount_min = 0.6
	fl.scale_amount_max = 1.2
	var cr := Curve.new()
	cr.add_point(Vector2(0, 1))
	cr.add_point(Vector2(1, 0))
	fl.scale_amount_curve = cr
	var fq := QuadMesh.new()
	fq.size = Vector2(0.07, 0.12)
	var flm := StandardMaterial3D.new()
	flm.billboard_mode = BaseMaterial3D.BILLBOARD_PARTICLES
	flm.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
	flm.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
	flm.blend_mode = BaseMaterial3D.BLEND_MODE_ADD
	flm.albedo_color = Color(1.0, 0.55, 0.15, 0.8)
	var gr := Gradient.new()
	gr.set_color(0, Color(1, 1, 1, 1))
	gr.set_color(1, Color(1, 1, 1, 0))
	var gt := GradientTexture2D.new()
	gt.gradient = gr
	gt.fill = GradientTexture2D.FILL_RADIAL
	gt.fill_from = Vector2(0.5, 0.6)
	gt.fill_to = Vector2(0.5, 0.0)
	flm.albedo_texture = gt
	fq.material = flm
	fl.mesh = fq
	fl.position = ag(-1.022, 0.83, -2.35)
	add_child(fl)
	var pics := ["photo_anna1", "photo_anna2", "photo_house"]
	var spots := [Vector2(0.1, 1.75), Vector2(0.55, 1.85), Vector2(1.0, 1.7)]
	for k in 3:   # фотографии над кроватью: рамки — в модели, снимки — свои (сепия: Анна девочкой, дом)
		var pq := MeshInstance3D.new()
		var pm := QuadMesh.new()
		pm.size = Vector2(0.19, 0.27)
		pq.mesh = pm
		var pmat := StandardMaterial3D.new()
		pmat.albedo_texture = load("res://assets/textures/%s.png" % pics[k])
		pmat.roughness = 0.55
		pq.material_override = pmat
		pq.position = ag(-3.052, spots[k].y, spots[k].x)
		pq.rotation_degrees = Vector3(0, 90, 0)
		add_child(pq)
	for c in [ag(2.2, 0.86, 1.3), ag(-1.0, 0.68, -1.6), ag(-1.3, 0.62, 0.9)]:   # свечи (горят, когда нет света)
		var ol := OmniLight3D.new()
		ol.position = c + Vector3(0, 0.12, 0)
		ol.light_color = Color(1.0, 0.62, 0.3)
		ol.light_energy = 0.0
		ol.omni_range = 3.2
		add_child(ol)
		candles.append(ol)
		var cm := MeshInstance3D.new()
		var cy := CylinderMesh.new()
		cy.top_radius = 0.018
		cy.bottom_radius = 0.02
		cy.height = 0.16
		cm.mesh = cy
		var wm := StandardMaterial3D.new()
		wm.albedo_color = Color(0.92, 0.88, 0.75)
		cm.material_override = wm
		cm.position = c + Vector3(0, 0.08, 0)
		cm.name = "candle"
		cm.visible = false
		add_child(cm)


## Дверь дома Агнес открыта (створку — внутрь, к стене).
func open_door() -> void:
	for n in model.find_children("agnes_door*", "MeshInstance3D", true, false):
		var d := n as MeshInstance3D
		if not d.has_meta("open"):
			d.set_meta("open", true)
			d.visible = false
	var leaf := MeshInstance3D.new()   # открытая створка у стены, внутри
	var bm := BoxMesh.new()
	bm.size = Vector3(0.96, 2.05, 0.06)
	leaf.mesh = bm
	var m := StandardMaterial3D.new()
	m.albedo_color = Color(0.25, 0.15, 0.08)
	m.roughness = 0.65
	leaf.material_override = m
	leaf.position = AG + Vector3(2.6, FLOOR + 1.03, 0.52)
	add_child(leaf)


## Ночью из окон дома — холодный свет снега (видно лица у окна, когда в доме нет света).
func night_glow(on: bool) -> void:
	var gl: OmniLight3D = get_node_or_null("night_glow")
	if gl == null:
		gl = OmniLight3D.new()
		gl.name = "night_glow"
		gl.position = ag(2.6, 1.5, 2.1)
		gl.light_color = Color(0.6, 0.7, 0.95)
		gl.light_energy = 0.7
		gl.omni_range = 3.2
		add_child(gl)
	gl.visible = on


## Приёмник на полке ночью: тёплый огонёк шкалы.
func radio_glow(on: bool) -> void:
	var gl: OmniLight3D = get_node_or_null("radio_glow")
	if gl == null:
		gl = OmniLight3D.new()
		gl.name = "radio_glow"
		gl.position = ag(-2.7, 1.8, 0.1)
		gl.light_color = Color(1.0, 0.72, 0.4)
		gl.light_energy = 0.6
		gl.omni_range = 1.8
		add_child(gl)
	gl.visible = on


## Свет в деревне: true — сеть есть (лампа, фонари, окна), false — оборвало провода (горят печь и свечи).
func set_power(on: bool) -> void:
	power = on
	bulb.visible = on
	for sl in street:
		sl.visible = on
	for w in windows:
		w.visible = on


func set_candles(on: bool) -> void:
	for ol in candles:
		ol.light_energy = 0.55 if on else 0.0
	for n in get_children():
		if n.name.begins_with("candle"):
			(n as Node3D).visible = on


## Разбитое окно магазина: стекло пропадает, по краям — острые осколки.
func break_shop_window() -> void:
	for n in model.find_children("shop_window*", "MeshInstance3D", true, false):
		(n as MeshInstance3D).visible = false
	var m := StandardMaterial3D.new()
	m.albedo_color = Color(0.2, 0.24, 0.26, 0.5)
	m.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
	m.roughness = 0.05
	for k in 7:
		var sh := MeshInstance3D.new()
		var pm := PrismMesh.new()
		pm.size = Vector3(randf_range(0.2, 0.5), randf_range(0.3, 0.7), 0.01)
		sh.mesh = pm
		sh.material_override = m
		var top := k % 2 == 0
		sh.position = Vector3(8.47, 1.95 + (0.55 if top else -0.55), -28.0 + 0.9 - 2.4 * float(k) / 6.0 - 0.0)
		sh.rotation_degrees = Vector3(0, 90, 180.0 if top else 0.0)
		add_child(sh)


func _process(delta: float) -> void:
	_t += delta
	if fire:   # огонь дышит
		var f := 1.0 + 0.18 * sin(_t * 7.3) + 0.1 * sin(_t * 13.1 + 1.7) + randf_range(-0.06, 0.06)
		fire.light_energy = 1.2 * f
		(fire_quad.material_override as StandardMaterial3D).emission_energy_multiplier = 2.2 * f
	for ol in candles:
		if ol.light_energy > 0.0:
			ol.light_energy = 0.55 + 0.08 * sin(_t * 9.0 + ol.position.x * 3.0) + randf_range(-0.03, 0.03)
