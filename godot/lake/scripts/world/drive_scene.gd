class_name DriveScene
extends Node3D
## Ночная трасса с машиной: окружение (небо Poly Haven едва светит, туман, на ПК — объёмный туман для лучей фар), луна,
## дорога, машина. Планы камеры — в координатах машины (SHOTS): «как в кино» — руки на руле, лицо Айрис, дорога глазами Ноя.

var road: Road
var car: Car
var env: Environment
var moon: DirectionalLight3D
var pc := true

## план → [позиция камеры, куда смотрит, угол обзора] (машина: вперёд −Z, водитель слева)
const SHOTS := {
	"pov": [Vector3(-0.36, 1.2, 0.26), Vector3(-0.2, 0.75, -25.0), 64.0],
	"hands": [Vector3(0.06, 1.06, -0.33), Vector3(-0.36, 0.93, -0.16), 38.0],
	"noah": [Vector3(0.28, 1.12, -0.18), Vector3(-0.36, 1.18, 0.3), 40.0],
	"iris": [Vector3(-0.26, 1.14, -0.12), Vector3(0.36, 1.1, 0.32), 40.0],
	"two": [Vector3(0.0, 1.22, -0.6), Vector3(0.0, 1.05, 0.45), 58.0],
	"notebook": [Vector3(0.22, 1.18, -0.02), Vector3(0.38, 0.62, 0.2), 42.0],
	"radio": [Vector3(-0.08, 0.98, -0.25), Vector3(0.0, 0.8, -0.45), 34.0],
	"glovebox": [Vector3(0.05, 1.05, 0.05), Vector3(0.36, 0.72, -0.42), 44.0],
	"front": [Vector3(0.6, 1.45, -5.5), Vector3(0.0, 1.0, 0.0), 40.0],
	"road": [Vector3(-0.36, 1.17, 0.24), Vector3(-0.36, 0.6, -40.0), 50.0],
}


func setup(is_pc: bool) -> void:
	pc = is_pc
	env = Environment.new()
	env.background_mode = Environment.BG_COLOR
	env.background_color = Color(0.008, 0.01, 0.016)
	var sky := Sky.new()
	var psm := PanoramaSkyMaterial.new()
	psm.panorama = load("res://assets/ph/narrow_moonlit_road_1k.hdr")
	sky.sky_material = psm
	sky.radiance_size = Sky.RADIANCE_SIZE_64
	env.sky = sky
	env.ambient_light_source = Environment.AMBIENT_SOURCE_SKY
	env.ambient_light_energy = 0.018
	env.reflected_light_source = Environment.REFLECTION_SOURCE_SKY
	env.tonemap_mode = Environment.TONE_MAPPER_ACES
	env.tonemap_exposure = 1.0
	env.tonemap_white = 1.6
	env.fog_enabled = true
	env.fog_light_color = Color(0.03, 0.035, 0.05)
	env.fog_density = 0.012
	env.glow_enabled = true
	env.glow_bloom = 0.0
	env.glow_hdr_threshold = 0.9
	env.glow_intensity = 0.7
	env.glow_blend_mode = Environment.GLOW_BLEND_MODE_ADDITIVE
	if pc:
		env.volumetric_fog_enabled = true
		env.volumetric_fog_density = 0.007
		env.volumetric_fog_albedo = Color(0.85, 0.88, 0.95)
		env.volumetric_fog_length = 80.0
		env.ssao_enabled = true
	var we := WorldEnvironment.new()
	we.environment = env
	add_child(we)
	moon = DirectionalLight3D.new()
	moon.light_color = Color(0.55, 0.65, 0.9)
	moon.light_energy = 0.025
	moon.rotation_degrees = Vector3(-35, 140, 0)
	moon.shadow_enabled = false
	add_child(moon)
	road = Road.new()
	add_child(road)
	road.setup(pc)
	car = Car.new()
	add_child(car)
	car.setup(pc)


## Камера на план (в координатах машины).
func put_camera(cam: Camera3D, shot: String) -> void:
	var s: Array = SHOTS.get(shot, SHOTS.pov)
	cam.fov = s[2]
	cam.look_at_from_position(car.to_global(s[0]), car.to_global(s[1]))
