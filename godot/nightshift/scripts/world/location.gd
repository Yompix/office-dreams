class_name Location
extends Node3D
## Место действия, выгруженное из старой игры (tools/godot/export_world.js): геометрия world/<место>.glb,
## свет и туман world/<место>.json. Свет пересчитывается из единиц three.js (legacy) в Godot.

var loc := ""
var meta: Dictionary = {}
var lights: Dictionary = {}   # ключ места (l1, lamp, boothLight…) → Light3D
## фоновый свет three.js (AmbientLight, HemisphereLight): в Godot это свет окружения — имя → { t (тип), c, g (цвет неба и земли,
## линейные), i (сила), v (виден) }; запись кадра меняет его, как и лампы (в серверной k8 фон гаснет до 0.008)
var ambs: Dictionary = {}
var _amb0: Dictionary = {}
var env: Environment
var pc := true                # Forward+ (ПК): тени, объёмный туман; иначе — как на сайте
var layer := 2                # слой отрисовки места: все места стоят в одних координатах, камера видит только слой своего места
var screens: Array[MeshInstance3D] = []   # мониторы будки (холл): на них — живые камеры (Feeds)
var probe: ReflectionProbe
var beams: Beams              # лучи ламп и пыль в них (как «Кино» старой игры)
## слои мест (биты 2…6); 20 — мониторы будки (камеры мониторов их не снимают — экран не снимает сам себя)
const LAYERS := {"lobby": 2, "corridor": 3, "server": 4, "stairs": 5, "basement": 6}
const SCREEN_LAYER := 20
## свет места, которое видно только на мониторе будки (камера 2 — седьмой этаж): все семь ламп коридора горят (в кадрах коридора 2,4; на мониторе тусклее — экран маленький и пересвечен)
const FEED_LIGHTS := {"corridor": {"casters_0": 1.4, "casters_1": 1.4, "casters_2": 1.4, "casters_3": 1.4, "casters_4": 1.4,
	"casters_5": 1.4, "casters_6": 1.4}}
## Свет three.js (legacy) гаснет как (1 − d/r)^decay, без обратного квадрата; в Godot — (1 − (d/r)^4)^2 / d^att: затухание att
## и поправку силы подбираем на каждую лампу (falloff_fit), LIGHT_K — общий множитель
const LIGHT_K := 1.0
const AMB_K := 1.0
## экспозиция кадра по месту — как uExp в обработке кадра старой игры (cineRender): коридор темнее, серверная чуть светлее
const EXPOSURE := {"corridor": 0.68, "server": 1.05}
const TONE_WHITE := 1.6
## карта окружения старой игры (снимок места со всеми лампами, tools/godot/export_envmap.js): рассеянный свет от комнаты на всё —
## в Godot это небо (не видно — фон цветом), из которого берётся свет окружения. ENV_K — сила для стен и пола (envMapIntensity 0.7
## в world.js), героям — своя через AO (CharLook). Фоновый свет AmbientLight прибавляется: Godot смешивает цвет и небо (mix),
## поэтому оба удваиваем при доле неба 0.5 — выходит сумма, как в three.js.
const ENV_K := 0.7
const SKY_MIX := 0.5
const ENV_SKY := """
shader_type sky;
uniform sampler2D pano : filter_linear, repeat_enable;
uniform float k = 1.4;
void sky() {
	vec3 d = normalize(EYEDIR);
	vec2 uv = vec2(atan(d.x, -d.z) / TAU + 0.5, acos(clamp(d.y, -1.0, 1.0)) / PI);
	COLOR = texture(pano, uv).rgb * k;
}
"""
static var _sky_sh: Shader
var sky_mix := 0.0
## объёмный туман ПК: доля плотности тумана места (старая игра — экспонента FogExp2 + конусы лучей; туман Godot светится от всех ламп
## и поднимает тени — при 0.5 кадры ПК были вдвое светлее старых в темноте)
const VFOG_K := 0.15


static func light_k() -> float:
	return float(OS.get_environment("LK")) if OS.get_environment("LK") != "" else LIGHT_K


static var _fits := {}


## Спад света: для дальности r и decay лампы three.js — затухание Godot att (0…2) и множитель силы k, при которых
## k·(1 − x⁴)²/(x·r)^att ближе всего к (1 − x)^decay на 24 точках x = d/r от 5 % до 100 % (ошибка относительная — важны и
## пятно у лампы, и край). Без подбора маленькие лампы у лица (faceL: 0.85 м, decay 2) светили в Godot в 3–5 раз ярче.
static func falloff_fit(r: float, decay: float) -> Vector2:
	var key := "%.3f|%.2f" % [r, decay]
	if _fits.has(key):
		return _fits[key]
	var best := Vector2(1.0, 1.0)
	var best_e := INF
	var xs: Array[float] = []
	for i in 24:
		xs.append(0.05 + 0.95 * i / 23.0)
	for ai in 21:
		var att := ai * 0.1
		var fs: Array[float] = []
		var gs: Array[float] = []
		var num := 0.0
		var den := 0.0
		for x in xs:
			var f := pow(1.0 - x, decay)
			var g := pow(maxf(1.0 - pow(x, 4.0), 0.0), 2.0) / pow(maxf(x * r, 0.0001), att)
			var w := 1.0 / pow(f + 0.05, 2.0)
			num += w * f * g
			den += w * g * g
			fs.append(f)
			gs.append(g)
		var k := num / maxf(den, 1e-9)
		var e := 0.0
		for i in xs.size():
			e += pow((k * gs[i] - fs[i]) / (fs[i] + 0.05), 2.0)
		if e < best_e:
			best_e = e
			best = Vector2(k, att)
	_fits[key] = best
	return best


## Дальность лампы (из выгрузки или записи кадра; 0 — свет three.js без спада) → дальность и затухание Godot, поправка силы (meta fk).
static func set_range(l: Light3D, r: float) -> void:
	var fit := falloff_fit(r, float(l.get_meta("decay", 2.0))) if r > 0.0 else Vector2(1.0, 0.0)
	var rr := r if r > 0.0 else 20.0
	var old_k := float(l.get_meta("fk", 1.0))
	if l is OmniLight3D:
		(l as OmniLight3D).omni_range = rr
		(l as OmniLight3D).omni_attenuation = fit.y
	elif l is SpotLight3D:
		(l as SpotLight3D).spot_range = rr
		(l as SpotLight3D).spot_attenuation = fit.y
	l.set_meta("fk", fit.x)
	l.light_energy = l.light_energy / old_k * fit.x


## Сила лампы three.js ↔ энергия Godot (с поправкой спада этой лампы).
static func set_intensity(l: Light3D, i: float) -> void:
	l.light_energy = i * light_k() * float(l.get_meta("fk", 1.0))


static func intensity(l: Light3D) -> float:
	return l.light_energy / (light_k() * float(l.get_meta("fk", 1.0)))


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
			li.set_meta("fk0", li.get_meta("fk", 1.0))
		li.visible = on and bool(li.get_meta("vis0"))
		li.shadow_enabled = shadows and bool(li.get_meta("sh0"))
		li.light_color = li.get_meta("c0")
		li.transform = li.get_meta("x0")
		li.set_meta("fk", li.get_meta("fk0"))
		li.light_energy = li.get_meta("e0")
		if li.has_meta("dist0"):
			set_range(li, float(li.get_meta("dist0")))
	ambs = _amb0.duplicate(true)
	amb_update()
	if not on:
		return
	# место видно только на мониторе: обычная ночь (лампы седьмого этажа горят — их включают кадры коридора, lampSet в scenes.js)
	if not shadows and FEED_LIGHTS.has(loc):
		for k in FEED_LIGHTS[loc]:
			if lights.has(k):
				set_intensity(lights[k], FEED_LIGHTS[loc][k])
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
	env.tonemap_mode = {"linear": Environment.TONE_MAPPER_LINEAR, "filmic": Environment.TONE_MAPPER_FILMIC, "agx": Environment.TONE_MAPPER_AGX}.get(OS.get_environment("TMAP"), Environment.TONE_MAPPER_ACES)
	env.tonemap_exposure = EXPOSURE.get(loc, 0.92)
	# белая точка ACES: у старой игры кривая (Narkowicz) мягче в светлом — бумага и лампы не выгорали в белое; 1.6 ближе всего
	env.tonemap_white = float(OS.get_environment("TWHITE")) if OS.get_environment("TWHITE") != "" else TONE_WHITE
	env.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
	_env_sky()
	for d in meta.lights:
		match d.type:
			"AmbientLight", "HemisphereLight":
				_amb0[String(d.key)] = {"t": d.type, "c": _col(d.color).srgb_to_linear(), "g": _col(d.get("ground", 0)).srgb_to_linear(),
						"i": float(d.intensity), "v": bool(d.get("visible", true))}
			"PointLight", "SpotLight", "DirectionalLight":
				_add_light(d)
	ambs = _amb0.duplicate(true)
	amb_update()
	beams = Beams.new()
	add_child(beams)
	beams.setup(lights, meta.get("beams", []), float(meta.fog.density) if meta.get("fog") else 0.05, pc)
	var fog = meta.get("fog")
	if fog:
		env.fog_enabled = true
		env.fog_light_color = _col(fog.color)
		env.fog_density = pow(float(fog.density), 2.0) * 8.0   # FogExp2 three.js ≈ экспонента Godot на расстоянии ~8 м
		if pc:
			env.volumetric_fog_enabled = true
			env.volumetric_fog_density = float(fog.density) * (float(OS.get_environment("VFOG")) if OS.get_environment("VFOG") != "" else VFOG_K)
			env.volumetric_fog_albedo = _col(fog.color).lightened(0.5)
	# свечение — только у ламп (в старой игре: всё, что ярче 0.6 в размытом кадре, плюс лёгкая тёплая кайма); bloom по всему кадру
	# (0.15) поднимал тени — тёмные кадры ПК были вдвое светлее старых
	env.glow_bloom = float(OS.get_environment("BLOOM")) if OS.get_environment("BLOOM") != "" else 0.0
	env.glow_hdr_threshold = 0.8
	env.glow_intensity = 0.8
	env.glow_blend_mode = Environment.GLOW_BLEND_MODE_ADDITIVE
	Graphics.env(env)
	if OS.get_environment("GLOW") == "0":   # проверка: без свечения
		env.glow_enabled = false


static func amb_k() -> float:
	return float(OS.get_environment("AMB")) if OS.get_environment("AMB") != "" else AMB_K


## Свет окружения = сумма фонового света места (в линейных цветах, как в three.js); полусфера — среднее неба и земли.
func amb_update() -> void:
	var sum := Color(0, 0, 0)
	for k in ambs:
		var a: Dictionary = ambs[k]
		if a.v:
			sum += ((a.c as Color) if a.t == "AmbientLight" else ((a.c as Color) + (a.g as Color)) * 0.5) * float(a.i)
	sum *= amb_k() / (1.0 - sky_mix)
	env.ambient_light_color = Color(sum.r, sum.g, sum.b).linear_to_srgb()
	env.ambient_light_energy = 1.0   # сила — в цвете: у сайта энергия умножает и небо


## Карта окружения места → небо Godot (свет окружения); без карты — только фоновый свет.
func _env_sky() -> void:
	var em = meta.get("env")
	var path := "res://world/%s_env.bin" % loc
	if em == null or not FileAccess.file_exists(path) or OS.get_environment("NOENV") != "":
		return
	if _sky_sh == null:
		_sky_sh = Shader.new()
		_sky_sh.code = ENV_SKY
	var img := Image.create_from_data(int(em.w), int(em.h), false, Image.FORMAT_RGBF, FileAccess.get_file_as_bytes(path))
	img.convert(Image.FORMAT_RGBH)   # половинная точность: на сайте (WebGL2) фильтруется без расширений
	var sm := ShaderMaterial.new()
	sm.shader = _sky_sh
	sm.set_shader_parameter("pano", ImageTexture.create_from_image(img))
	sky_mix = SKY_MIX
	sm.set_shader_parameter("k", (float(OS.get_environment("ENVK")) if OS.get_environment("ENVK") != "" else ENV_K) / sky_mix)
	var sky := Sky.new()
	sky.sky_material = sm
	sky.radiance_size = Sky.RADIANCE_SIZE_64
	env.sky = sky
	env.ambient_light_source = Environment.AMBIENT_SOURCE_SKY
	env.ambient_light_sky_contribution = sky_mix
	env.reflected_light_source = Environment.REFLECTION_SOURCE_DISABLED   # блики — от пробы отражений места


## Запись кадра меняет фоновый свет: i — сила, v — виден, c — цвет (линейный, как в three.js).
func amb_set(k: String, kind: String, v: PackedFloat32Array) -> void:
	var a: Dictionary = ambs.get(k, {})
	if a.is_empty():
		return
	match kind:
		"i":
			a.i = v[0]
		"v":
			a.v = v[0] > 0.5
		"c":
			a.c = Color(v[0], v[1], v[2])


func _add_light(d: Dictionary) -> void:
	var l: Light3D
	var p := _v3(d.pos)
	if d.type == "SpotLight":
		var s := SpotLight3D.new()
		s.spot_angle = rad_to_deg(float(d.angle))
		s.spot_angle_attenuation = 1.0 + float(d.get("penumbra", 0)) * 2.0
		l = s
	elif d.type == "DirectionalLight":
		l = DirectionalLight3D.new()
	else:
		l = OmniLight3D.new()
	l.light_color = _col(d.color)
	l.light_energy = float(d.intensity) * light_k()
	if not (l is DirectionalLight3D):
		l.set_meta("decay", float(d.get("decay", 2.0)))
		l.set_meta("dist0", float(d.get("distance", 0)))
		set_range(l, float(d.get("distance", 0)))
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
