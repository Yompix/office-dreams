class_name NewsTV
extends Node3D
## Телевизор за кассиром: ночные новости — фото Ноя (его же модель в маленькой «студии»: свой мир, ровный свет, снимок
## один раз), шапка канала, красная плашка «Разыскивается» и бегущая строка (русский / английский). Экран — плоскость
## поверх кинескопа модели Poly Haven Television_01, светится и чуть мерцает, синеватый отсвет на стене и кассире.

var photo: SubViewport
var news: SubViewport
var screen: MeshInstance3D
var glow: OmniLight3D
var ticker: Label
var _t := 0.0
var _frames := 0

const TEXT := {
	"ru": {"logo": "НОВОСТИ", "live": "В ЭФИРЕ", "head": "РАЗЫСКИВАЕТСЯ: НОЙ, 19 ЛЕТ",
		"sub": "Убийство в доме у озера. Может быть опасен. С ним сестра, 15 лет.",
		"tick": "Если вы видели этого человека — сообщите в полицию  •  Трасса на север: метель, видимость до 50 метров  •  "},
	"en": {"logo": "NEWS", "live": "LIVE", "head": "WANTED: NOAH, 19",
		"sub": "Lake house killing. May be dangerous. Travelling with his sister, 15.",
		"tick": "If you have seen this man, contact the police  •  Northbound highway: blizzard, visibility under 50 metres  •  "},
}


## tv — модель телевизора (экран ставится по её габаритам), lang — язык надписей.
func build(tv: Node3D, lang: String) -> void:
	var T: Dictionary = TEXT.get(lang, TEXT.en)
	# фото: Ной в «студии» (свой мир — не видит ни заправки, ни снега)
	photo = SubViewport.new()
	photo.size = Vector2i(256, 300)
	photo.own_world_3d = true
	photo.render_target_update_mode = SubViewport.UPDATE_ALWAYS
	add_child(photo)
	var env := Environment.new()
	env.background_mode = Environment.BG_COLOR
	env.background_color = Color(0.32, 0.36, 0.42)
	env.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
	env.ambient_light_color = Color(0.6, 0.62, 0.66)
	env.ambient_light_energy = 0.55
	env.tonemap_mode = Environment.TONE_MAPPER_ACES
	var we := WorldEnvironment.new()
	we.environment = env
	photo.add_child(we)
	var key := DirectionalLight3D.new()
	key.rotation_degrees = Vector3(-15, 20, 0)
	key.light_energy = 1.3
	photo.add_child(key)
	var noah := Actor.make("noah_photo", "noah")
	photo.add_child(noah)
	noah.play("Idle", 0.2)
	noah.blink_t = 99.0                               # на фото не моргает
	var cam := Camera3D.new()
	photo.add_child(cam)
	cam.fov = 22.0
	cam.make_current()
	_frame_cam.call_deferred(cam, noah)
	# кадр новостей: фон, фото в рамке, шапка, плашка, бегущая строка
	news = SubViewport.new()
	news.size = Vector2i(512, 384)
	news.render_target_update_mode = SubViewport.UPDATE_ALWAYS
	add_child(news)
	var bg := ColorRect.new()
	bg.color = Color(0.05, 0.12, 0.3)
	bg.size = Vector2(512, 384)
	news.add_child(bg)
	var stripe := ColorRect.new()
	stripe.color = Color(0.1, 0.22, 0.5)
	stripe.position = Vector2(0, 44)
	stripe.size = Vector2(512, 230)
	news.add_child(stripe)
	var frame := ColorRect.new()
	frame.color = Color(0.92, 0.92, 0.9)
	frame.position = Vector2(26, 58)
	frame.size = Vector2(168, 200)
	news.add_child(frame)
	var pic := TextureRect.new()
	pic.texture = photo.get_texture()
	pic.expand_mode = TextureRect.EXPAND_IGNORE_SIZE   # до размера: иначе картинка растягивает рамку до своих 256×300
	pic.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_COVERED
	pic.clip_contents = true
	pic.position = Vector2(30, 62)
	pic.size = Vector2(160, 192)
	news.add_child(pic)
	var bold: Font = load("res://assets/fonts/Inter-700.woff2")
	var mid: Font = load("res://assets/fonts/Inter-500.woff2")
	news.add_child(_label(T.logo, Vector2(18, 8), 26, bold, Color(1, 1, 1)))
	var live := _label("● " + T.live, Vector2(390, 12), 18, bold, Color(1.0, 0.3, 0.25))
	news.add_child(live)
	var band := ColorRect.new()
	band.color = Color(0.78, 0.06, 0.05)
	band.position = Vector2(0, 274)
	band.size = Vector2(512, 46)
	news.add_child(band)
	var head := _label(T.head, Vector2(16, 279), 26, bold, Color(1, 1, 1))
	head.clip_text = true
	head.size = Vector2(486, 40)
	news.add_child(head)
	var box := Control.new()                          # подзаголовок — в рамке своей ширины: перенос по словам
	box.position = Vector2(210, 66)
	box.size = Vector2(284, 196)
	box.clip_contents = true
	news.add_child(box)
	var sub := _label(T.sub, Vector2.ZERO, 21, mid, Color(0.95, 0.96, 1.0))
	sub.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	box.add_child(sub)
	sub.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	var tickbg := ColorRect.new()
	tickbg.color = Color(0.02, 0.03, 0.08)
	tickbg.position = Vector2(0, 320)
	tickbg.size = Vector2(512, 64)
	news.add_child(tickbg)
	ticker = _label(T.tick + T.tick, Vector2(0, 334), 22, mid, Color(1.0, 0.85, 0.3))
	news.add_child(ticker)
	# экран поверх кинескопа: по габаритам модели (кинескоп — левые ~62 % лицевой стороны)
	var ab := _aabb(tv)
	screen = MeshInstance3D.new()
	var qm := QuadMesh.new()
	qm.size = Vector2(ab.size.x * 0.6, ab.size.y * 0.62)
	screen.mesh = qm
	var m := StandardMaterial3D.new()
	m.albedo_texture = news.get_texture()
	m.albedo_color = Color(0.25, 0.25, 0.25)
	m.emission_enabled = true
	m.emission_texture = news.get_texture()
	m.emission_operator = BaseMaterial3D.EMISSION_OP_MULTIPLY
	m.emission = Color(1, 1, 1)
	m.emission_energy_multiplier = 1.3
	m.roughness = 0.15
	screen.material_override = m
	add_child(screen)
	screen.global_position = Vector3(ab.position.x + ab.size.x * 0.385, ab.position.y + ab.size.y * 0.56, ab.end.z + 0.004)
	glow = OmniLight3D.new()
	glow.light_color = Color(0.55, 0.65, 1.0)
	glow.light_energy = 0.35
	glow.omni_range = 3.0
	glow.light_volumetric_fog_energy = 0.0
	glow.light_specular = 0.0                         # без блика-точки на самом экране
	add_child(glow)
	glow.global_position = screen.global_position + Vector3(0, 0, 0.5)


func _frame_cam(cam: Camera3D, a: Actor) -> void:
	await get_tree().process_frame
	await get_tree().process_frame
	var h := a.skel.global_transform * a.skel.get_bone_global_pose(a.skel.find_bone("head"))
	var face := h.origin + h.basis.y.normalized() * 0.06
	cam.look_at_from_position(face + Vector3(0, 0.0, 1.7), face + Vector3(0, -0.07, 0))   # голова и плечи, как на фото


func _label(t: String, p: Vector2, size: int, f: Font, c: Color) -> Label:
	var l := Label.new()
	l.text = t
	l.position = p
	l.add_theme_font_override("font", f)
	l.add_theme_font_size_override("font_size", size)
	l.add_theme_color_override("font_color", c)
	return l


func _aabb(n: Node3D) -> AABB:
	var out := AABB()
	var first := true
	for c in n.find_children("*", "MeshInstance3D", true, false):
		var mi := c as MeshInstance3D
		var b := mi.global_transform * mi.get_aabb()
		out = b if first else out.merge(b)
		first = false
	return out


func _process(delta: float) -> void:
	_t += delta
	_frames += 1
	if _frames == 30:                                 # снимок готов — студия больше не рисуется
		photo.render_target_update_mode = SubViewport.UPDATE_DISABLED
	if ticker:
		ticker.position.x = -fmod(_t * 60.0, ticker.size.x * 0.5)
	if glow:                                          # кинескоп мерцает: отсвет на стене дышит
		glow.light_energy = 0.32 + 0.05 * sin(_t * 31.0) + 0.04 * sin(_t * 7.3)
