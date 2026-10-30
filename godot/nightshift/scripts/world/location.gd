class_name Location
extends Node3D
## Место действия, выгруженное из старой игры (tools/godot/export_world.js): геометрия world/<место>.glb,
## свет и туман world/<место>.json. Свет пересчитывается из единиц three.js (legacy) в Godot.

var loc := ""
var meta: Dictionary = {}
var lights: Dictionary = {}   # ключ места (l1, lamp, boothLight…) → Light3D
var env: Environment
var pc := true                # Forward+ (ПК): тени, объёмный туман; иначе — как на сайте
var layer := 2                # слой отрисовки места: все места стоят в одних координатах, камера видит только слой своего места
var screens: Array[MeshInstance3D] = []   # мониторы будки (холл): на них — живые камеры (Feeds)
var probe: ReflectionProbe
## слои мест (биты 2…6); 20 — мониторы будки (камеры мониторов их не снимают — экран не снимает сам себя)
const LAYERS := {"lobby": 2, "corridor": 3, "server": 4, "stairs": 5, "basement": 6}
const SCREEN_LAYER := 20
## свет места, которое видно только на мониторе будки (камера 2 — седьмой этаж): все семь ламп коридора горят (в кадрах коридора 2,4; на мониторе тусклее — экран маленький и пересвечен)
const FEED_LIGHTS := {"corridor": {"casters_0": 1.4, "casters_1": 1.4, "casters_2": 1.4, "casters_3": 1.4, "casters_4": 1.4,
	"casters_5": 1.4, "casters_6": 1.4}}
## Свет three.js (legacy) гаснет как (1 − d/r)^decay, без обратного квадрата; в Godot подбираем затухание и яркость под тот же вид
const LIGHT_ATT := 0.5
const LIGHT_K := 1.0
const AMB_K := 1.0


static func light_k() -> float:
	return float(OS.get_environment("LK")) if OS.get_environment("LK") != "" else LIGHT_K


static func create(name: String) -> Location:
	var l := Location.new()
	l.loc = name
	l.name = name
	l.pc = RenderingServer.get_current_rendering_method() == "forward_plus"
	l.layer = LAYERS.get(name, 7)
	l._build()
	l.set_layer(l, 1 << (l.layer - 1))
	return l


## Всё видимое и весь свет места — на его слой (свет освещает только своё место).
func set_layer(n: Node, bit: int) -> void:
	if n is MeshInstance3D and (n as MeshInstance3D) in screens:
		(n as VisualInstance3D).layers = 1 << (SCREEN_LAYER - 1)
	elif n is VisualInstance3D and not (n is Light3D) and not (n is ReflectionProbe):
		(n as VisualInstance3D).layers = bit
	if n is Light3D:   # свет тоже на слое места: камера видит только свет своего места
		(n as Light3D).layers = bit
		(n as Light3D).light_cull_mask = bit | (1 << (SCREEN_LAYER - 1))
	if n is ReflectionProbe:
		(n as ReflectionProbe).cull_mask = bit
		(n as ReflectionProbe).reflection_mask = bit
	for c in n.get_children():
		set_layer(c, bit)


## Свет места: текущее место — со всеми тенями; место, которое видно только на мониторе, — без теней; остальные погашены.
## Каждый раз свет возвращается к выгруженному состоянию — запись кадра ставит поверх своё (что не меняет — так и было).
func set_lit(on: bool, shadows: bool) -> void:
	for l in find_children("*", "Light3D", true, false):
		var li := l as Light3D
		if not li.has_meta("vis0"):
			li.set_meta("vis0", li.visible)
			li.set_meta("sh0", li.shadow_enabled)
			li.set_meta("e0", li.light_energy)
			li.set_meta("c0", li.light_color)
			li.set_meta("x0", li.transform)
			li.set_meta("r0", (li as OmniLight3D).omni_range if li is OmniLight3D else ((li as SpotLight3D).spot_range if li is SpotLight3D else 0.0))
		li.visible = on and bool(li.get_meta("vis0"))
		li.shadow_enabled = shadows and bool(li.get_meta("sh0"))
		li.light_energy = li.get_meta("e0")
		li.light_color = li.get_meta("c0")
		li.transform = li.get_meta("x0")
		if li is OmniLight3D:
			(li as OmniLight3D).omni_range = li.get_meta("r0")
		elif li is SpotLight3D:
			(li as SpotLight3D).spot_range = li.get_meta("r0")
	if not on:
		return
	# место видно только на мониторе: обычная ночь (лампы седьмого этажа горят — их включают кадры коридора, lampSet в scenes.js)
	if not shadows and FEED_LIGHTS.has(loc):
		for k in FEED_LIGHTS[loc]:
			if lights.has(k):
				lights[k].light_energy = FEED_LIGHTS[loc][k] * light_k()
				lights[k].visible = true


func _build() -> void:
	var geo: Node3D = (load("res://world/%s.glb" % loc) as PackedScene).instantiate()
	add_child(geo)
	_fix_materials(geo)
	_probe(geo)
	for i in 4:
		var sc := geo.find_child("screens_%d" % i, true, false)
		if sc:
			var mi := (sc if sc is MeshInstance3D else sc.find_children("*", "MeshInstance3D", true, false).front()) as MeshInstance3D
			if mi:
				screens.append(mi)
	meta = JSON.parse_string(FileAccess.get_file_as_string("res://world/%s.json" % loc))
	env = Environment.new()
	env.background_mode = Environment.BG_COLOR
	env.background_color = _col(meta.get("bg") if meta.get("bg") != null else 0x0b0f14)
	env.tonemap_mode = Environment.TONE_MAPPER_ACES
	env.tonemap_exposure = 0.92
	env.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
	var amb := Color(0, 0, 0)
	for d in meta.lights:
		match d.type:
			"AmbientLight":
				amb += _col(d.color) * float(d.intensity)
			"HemisphereLight":
				amb += (_col(d.color) + _col(d.get("ground", 0))) * 0.5 * float(d.intensity)
			"PointLight", "SpotLight", "DirectionalLight":
				_add_light(d)
	env.ambient_light_color = Color(amb.r, amb.g, amb.b).clamp()
	env.ambient_light_energy = float(OS.get_environment("AMB")) if OS.get_environment("AMB") != "" else AMB_K
	var fog = meta.get("fog")
	if fog:
		env.fog_enabled = true
		env.fog_light_color = _col(fog.color)
		env.fog_density = pow(float(fog.density), 2.0) * 8.0   # FogExp2 three.js ≈ экспонента Godot на расстоянии ~8 м
		if pc:
			env.volumetric_fog_enabled = true
			env.volumetric_fog_density = float(fog.density) * 0.5
			env.volumetric_fog_albedo = _col(fog.color).lightened(0.5)
	env.glow_bloom = 0.15
	Graphics.env(env)


func _add_light(d: Dictionary) -> void:
	var l: Light3D
	var p := _v3(d.pos)
	if d.type == "SpotLight":
		var s := SpotLight3D.new()
		s.spot_range = float(d.get("distance", 0)) if float(d.get("distance", 0)) > 0 else 20.0
		s.spot_angle = rad_to_deg(float(d.angle))
		s.spot_angle_attenuation = 1.0 + float(d.get("penumbra", 0)) * 2.0
		s.spot_attenuation = float(OS.get_environment("ATT")) if OS.get_environment("ATT") != "" else LIGHT_ATT
		l = s
	elif d.type == "DirectionalLight":
		l = DirectionalLight3D.new()
	else:
		var o := OmniLight3D.new()
		o.omni_range = float(d.get("distance", 0)) if float(d.get("distance", 0)) > 0 else 20.0
		o.omni_attenuation = float(OS.get_environment("ATT")) if OS.get_environment("ATT") != "" else LIGHT_ATT
		l = o
	l.light_color = _col(d.color)
	l.light_energy = float(d.intensity) * light_k()
	l.shadow_enabled = pc and bool(d.get("shadow", false))
	l.visible = bool(d.get("visible", true))
	add_child(l)
	if d.has("target") and d.type != "PointLight":
		l.look_at_from_position(p, _v3(d.target))
	else:
		l.position = p
	if String(d.get("key", "")) != "":
		lights[d.key] = l


## Проба отражений по всему месту: как карта окружения (PMREM) в старой игре — свет комнаты падает на героев со всех сторон.
func _probe(geo: Node3D) -> void:
	var box := AABB()
	var first := true
	for mi in geo.find_children("*", "MeshInstance3D", true, false):
		var m := mi as MeshInstance3D
		if not m.visible:
			continue
		var bb := _local_xform(m) * m.get_aabb()
		if bb.size.length() > 60.0:
			continue   # небо и далёкие фасады улицы — не комната
		box = bb if first else box.merge(bb)
		first = false
	var p := ReflectionProbe.new()
	p.position = box.get_center()
	p.size = box.size + Vector3(0.5, 0.5, 0.5)
	p.interior = true
	p.box_projection = true
	p.ambient_mode = ReflectionProbe.AMBIENT_ENVIRONMENT
	p.intensity = float(OS.get_environment("PROBE")) if OS.get_environment("PROBE") != "" else 1.0
	p.update_mode = ReflectionProbe.UPDATE_ONCE
	add_child(p)
	probe = p


func _local_xform(n: Node) -> Transform3D:
	var t := Transform3D.IDENTITY
	while n != null and n != self:
		if n is Node3D:
			t = (n as Node3D).transform * t
		n = n.get_parent()
	return t


## Особые материалы старой игры приходят с меткой в имени материала (tools/godot/export_world.js): подставляем свои.
func _fix_materials(n: Node) -> void:
	if n is MeshInstance3D:
		var mi := n as MeshInstance3D
		for i in mi.mesh.get_surface_count():
			var m := mi.mesh.surface_get_material(i)
			var nm := m.resource_name if m else ""
			if nm.begins_with("__beam") or nm.begins_with("__fx"):
				mi.visible = false   # лучи света: на ПК их даёт объёмный туман
			elif nm.begins_with("__glass"):
				mi.set_surface_override_material(i, _glass())
			elif nm.begins_with("__crt"):
				mi.set_surface_override_material(i, _crt())
			elif nm.begins_with("__add") and m is BaseMaterial3D:
				var a := (m as BaseMaterial3D).duplicate() as BaseMaterial3D
				a.blend_mode = BaseMaterial3D.BLEND_MODE_ADD
				a.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
				a.no_depth_test = false
				mi.set_surface_override_material(i, a)
	for c in n.get_children():
		_fix_materials(c)


func _glass() -> StandardMaterial3D:
	var g := StandardMaterial3D.new()
	g.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
	g.albedo_color = Color(0.32, 0.38, 0.44, 0.16)
	g.roughness = 0.04
	g.metallic = 0.0
	g.metallic_specular = 0.9
	g.cull_mode = BaseMaterial3D.CULL_DISABLED
	return g


func _crt() -> StandardMaterial3D:
	var c := StandardMaterial3D.new()
	c.albedo_color = Color(0.02, 0.05, 0.04)
	c.emission_enabled = true
	c.emission = Color(0.2, 0.55, 0.45)
	c.emission_energy_multiplier = 0.6
	c.roughness = 0.15
	return c


## Камера кадра старой игры (shots.json): позиция, куда смотрит, вертикальный угол обзора.
func frame_camera(shot: Dictionary) -> Camera3D:
	var cam := Camera3D.new()
	cam.fov = float(shot.get("fov", 50))
	cam.near = 0.05
	add_child(cam)
	cam.look_at_from_position(_v3(shot.pos), _v3(shot.look))
	return cam


static func _col(hex) -> Color:
	return Color.hex((int(hex) << 8) | 0xff)


static func _v3(a) -> Vector3:
	return Vector3(float(a[0]), float(a[1]), float(a[2]))
