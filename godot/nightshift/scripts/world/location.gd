class_name Location
extends Node3D
## Место действия, выгруженное из старой игры (tools/godot/export_world.js): геометрия world/<место>.glb,
## свет и туман world/<место>.json. Свет пересчитывается из единиц three.js (legacy) в Godot.

var loc := ""
var meta: Dictionary = {}
var lights: Dictionary = {}   # ключ места (l1, lamp, boothLight…) → Light3D
var env: Environment
var pc := true                # Forward+ (ПК): тени, объёмный туман; иначе — как на сайте


static func create(name: String) -> Location:
	var l := Location.new()
	l.loc = name
	l.name = name
	l.pc = RenderingServer.get_current_rendering_method() == "forward_plus"
	l._build()
	return l


func _build() -> void:
	add_child((load("res://world/%s.glb" % loc) as PackedScene).instantiate())
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
	env.ambient_light_energy = 1.0
	var fog = meta.get("fog")
	if fog:
		env.fog_enabled = true
		env.fog_light_color = _col(fog.color)
		env.fog_density = pow(float(fog.density), 2.0) * 8.0   # FogExp2 three.js ≈ экспонента Godot на расстоянии ~8 м
		if pc:
			env.volumetric_fog_enabled = true
			env.volumetric_fog_density = float(fog.density) * 0.5
			env.volumetric_fog_albedo = _col(fog.color).lightened(0.5)
	if pc:
		env.ssao_enabled = true
		env.glow_enabled = true
		env.glow_bloom = 0.15


func _add_light(d: Dictionary) -> void:
	var l: Light3D
	var p := _v3(d.pos)
	if d.type == "SpotLight":
		var s := SpotLight3D.new()
		s.spot_range = float(d.get("distance", 0)) if float(d.get("distance", 0)) > 0 else 20.0
		s.spot_angle = rad_to_deg(float(d.angle))
		s.spot_angle_attenuation = 1.0 + float(d.get("penumbra", 0)) * 2.0
		s.spot_attenuation = float(d.get("decay", 1))
		l = s
	elif d.type == "DirectionalLight":
		l = DirectionalLight3D.new()
	else:
		var o := OmniLight3D.new()
		o.omni_range = float(d.get("distance", 0)) if float(d.get("distance", 0)) > 0 else 20.0
		o.omni_attenuation = float(d.get("decay", 1))
		l = o
	l.light_color = _col(d.color)
	l.light_energy = float(d.intensity)
	l.shadow_enabled = pc and bool(d.get("shadow", false))
	l.visible = bool(d.get("visible", true))
	add_child(l)
	if d.has("target") and d.type != "PointLight":
		l.look_at_from_position(p, _v3(d.target))
	else:
		l.position = p
	if String(d.get("key", "")) != "":
		lights[d.key] = l


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
