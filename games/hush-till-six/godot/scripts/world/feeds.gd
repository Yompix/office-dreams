class_name Feeds
extends Node
## Мониторы в будке — живые камеры, как renderFeeds в scenes.js: 0 — холл, 1 — вход, 2 — седьмой этаж, 3 — помехи (камера 13).
## Маленькие камеры (96×72, ближние пиксели, как в старой игре) снимают свои места по слоям; экраны — ЭЛТ: серое с зеленцой,
## строки развёртки, темнее к краям. Кадр с мониторами крупно (shots.json feeds) — обновляются все каждый третий кадр,
## иначе — по одному раз в 20 кадров.

const VIEWS := [["lobby", Vector3(7.45, 3.85, 5.45), Vector3(-2.2, 0.9, -5)], ["lobby", Vector3(-0.9, 3.25, 8.1), Vector3(0.15, 0.8, 6.2)],
	["corridor", Vector3(1.0, 2.75, -0.5), Vector3(-0.1, 0.9, -25)]]
const SHADER := """
shader_type spatial;
render_mode unshaded;
uniform sampler2D feed : source_color, filter_nearest;
uniform float on = 1.0;
void fragment() {
	vec3 c = texture(feed, UV).rgb;
	float l = dot(c, vec3(.299, .587, .114));
	vec2 q = UV - .5;
	ALBEDO = mix(vec3(l), c, .3) * vec3(.8, 1., .88) * (1. - dot(q, q) * 1.5) * (.84 + .16 * sin(UV.y * 452.4)) * 1.45 * on;
}
"""

var vps: Array[SubViewport] = []
var mats: Array[ShaderMaterial] = []
var noise_img: Image
var noise_tex: ImageTexture
var all_often := false     # кадр смотрит на мониторы — обновлять все и часто
var _n := 0
var _rr := 0
var _rng := RandomNumberGenerator.new()


func setup(lobby: Location) -> void:
	var sh := Shader.new()
	sh.code = SHADER
	noise_img = Image.create(64, 48, false, Image.FORMAT_L8)
	noise_tex = ImageTexture.create_from_image(noise_img)
	for i in 4:
		var m := ShaderMaterial.new()
		m.shader = sh
		mats.append(m)
		if i < lobby.screens.size():
			lobby.screens[i].material_override = m
	for v in VIEWS:
		var vp := SubViewport.new()
		vp.size = Vector2i(96, 72)
		vp.render_target_update_mode = SubViewport.UPDATE_ONCE
		vp.msaa_3d = Viewport.MSAA_DISABLED
		vp.positional_shadow_atlas_size = 0
		var cam := Camera3D.new()
		cam.fov = 60.0   # угол three.js по вертикали — у Godot тоже по вертикали
		cam.near = 0.05
		cam.far = 80.0
		cam.cull_mask = 1 << (Location.LAYERS[v[0]] - 1)
		vp.add_child(cam)
		add_child(vp)
		cam.look_at_from_position(v[1], v[2])
		vps.append(vp)
	for i in 3:
		mats[i].set_shader_parameter("feed", vps[i].get_texture())
	mats[3].set_shader_parameter("feed", noise_tex)
	_rng.seed = 13


## Каждый кадр, пока место — холл. map: что показывает экран i ("noise" | "black" | "" — как обычно).
func tick(map := []) -> void:
	_n += 1
	if all_often:
		if _n % 3 == 0:
			for vp in vps:
				vp.render_target_update_mode = SubViewport.UPDATE_ONCE
	elif _n % 20 == 0:
		vps[_rr % 3].render_target_update_mode = SubViewport.UPDATE_ONCE
		_rr += 1
	if _n % 3 == 0:   # помехи
		var d := PackedByteArray()
		d.resize(64 * 48)
		for i in d.size():
			d[i] = _rng.randi() % 105
		noise_img.set_data(64, 48, false, Image.FORMAT_L8, d)
		noise_tex.update(noise_img)
	for i in 4:
		var m := String(map[i]) if i < map.size() else ""
		mats[i].set_shader_parameter("feed", noise_tex if i == 3 or m == "noise" else vps[i].get_texture())
		mats[i].set_shader_parameter("on", 0.0 if m == "black" else 1.0)
