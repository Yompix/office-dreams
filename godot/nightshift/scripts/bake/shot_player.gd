class_name ShotPlayer
extends Node3D
## Проигрывает записи кадров старой игры в Godot: место, герои (кости, лицо), предметы, подвижные части места, свет, камера.

## ключ героя в старой игре → модель (как castV3 в chars2.js)
const CAST := {
	"temych": "ethan", "ethanThing": "ethan", "smenshik": "dayguard", "mihalych": "frank",
	"cleaner": "gus", "victim": "gus", "katya": "lily", "katyaSmile": "lily", "katyaThing": "lily",
	"martha": "lily", "monster": "tenant",
}
const BONE := {"root": "Root", "Head": "head"}   # имена костей старой игры → Godot
## кто говорит → чей рот двигается (NAME в scenes.js): первый из списка, кто сейчас в кадре
const NAME := {"ИТАН": ["temych"], "СМЕНЩИК": ["smenshik"], "ЛИЛИ": ["katya", "katyaSmile", "katyaThing"], "???": ["monster"],
	"ФРЭНК": ["mihalych"], "ГАС": ["cleaner"]}
const LIP_LAG := {"katya": 0.12, "katyaSmile": 0.22}   # у «Лили» за стеклом рот чуть опаздывает за голосом
const FROZEN := ["monster", "katya", "katyaSmile", "katyaThing", "ethanThing"]   # лицо застывшее (noBlink / Жилец)

var locations := {}        # имя места → Location
var actors := {}           # ключ героя → { node, skel, bones: {имя → индекс}, morph: {форма → [[сетка, индекс]…]} }
var props := {}            # имя объекта → Node3D (предметы, созданные по записи)
var nodes := {}            # место → { имя → Node3D }
var camera: Camera3D
var fill: OmniLight3D
const FILL_E := 0.6
var world_env: WorldEnvironment
var shot: BakedShot
var base_pos := Vector3.ZERO      # камера кадра из записи (как sh.pos / sh.look / sh.fov в старой игре)
var base_look := Vector3.FORWARD
var base_fov := 50.0
var base_roll := 0.0
var aim_cx := 0.5                 # план кадра сценария: точка кадра, зум, доля «настоящего проезда»
var aim_cy := 0.5
var aim_z := 1.0
var aim_dk := 0.5
var talk := {}                    # кто говорит сейчас: { who, v (громкость), vis (форма рта) } — ставит движок перед apply
var norms := {}                   # «нормы» холла из хода игры (propsNow): часы, куртка Фрэнка, пропуск, журнал, телефон Фрэнка
var _plan: Array = []      # подготовленные каналы текущего кадра
var feeds: Feeds                  # живые камеры на мониторах будки
var _touched := {}                # узел места → [transform, visible] до первой записи, которая его двигала
static var SHOTS := {}            # статические поля кадров старой игры (data/shots.json): место, камера, feeds…
var _last_t := -1.0


func _ready() -> void:
	camera = Camera3D.new()
	camera.near = 0.03
	add_child(camera)
	camera.make_current()
	# заполняющий свет от камеры («глазной» свет кино): в старой игре героев со всех сторон освещала карта окружения комнаты
	fill = OmniLight3D.new()
	fill.light_energy = float(OS.get_environment("FILL")) if OS.get_environment("FILL") != "" else FILL_E
	fill.omni_range = 6.0
	fill.omni_attenuation = 1.0
	fill.light_specular = 0.15
	fill.light_color = Color(0.86, 0.9, 1.0)
	fill.shadow_enabled = false
	fill.position = Vector3(0, 0.25, 0.1)
	camera.add_child(fill)
	world_env = WorldEnvironment.new()
	add_child(world_env)


func play(id: String) -> void:
	if SHOTS.is_empty():
		SHOTS = JSON.parse_string(FileAccess.get_file_as_string("res://data/shots.json")).shots
	shot = BakedShot.load_shot(id)
	_use_location(shot.loc)
	_plan.clear()
	for n in _touched:   # всё, что двигали прошлые кадры, — на место (запись не хранит то, что совпадает с выгрузкой)
		if is_instance_valid(n):
			n.transform = _touched[n][0]
			n.visible = _touched[n][1]
	_last_t = -1.0
	for k in actors:
		actors[k].lips.reset()
	for ch in shot.chans.keys() + shot.consts.keys():
		var c := String(ch)
		var p := c.split(":")
		match p[0]:
			"a":
				var a := _actor(p[1])
				if p[2] == "q" or p[2] == "p" or p[2] == "s":
					var bi: int = a.skel.find_bone(BONE.get(p[3], p[3]))
					if bi >= 0:
						_plan.append([c, "b" + p[2], a, bi])
				elif p[2] == "f":
					if a.morph.has(p[3]):
						_plan.append([c, "f", a, p[3]])
				elif p[2] == "inner":
					_plan.append([c, "inner", a])
			"o":
				var n := _node(p[1])
				if n:
					if not _touched.has(n):
						_touched[n] = [n.transform, n.visible]
					_plan.append([c, "o" + p[2], n])
			"l":
				var l: Light3D = locations[shot.loc].lights.get(p[1])
				if l:
					_plan.append([c, "l" + p[2], l])


func apply(t: float, wait := 0.0) -> void:
	var f := shot.frame_at(t, wait)
	var now := t + wait
	var dt := clampf(now - _last_t, 0.0, 0.05) if _last_t >= 0.0 else 0.0
	_last_t = now
	for k in actors:
		actors[k].W.clear()
	_props()
	var cam := shot.value("cam", f)
	if cam.size() >= 7 and not is_nan(cam[0]):
		base_pos = Vector3(cam[0], cam[1], cam[2])
		base_look = Vector3(cam[3], cam[4], cam[5])
		base_fov = cam[6]
		base_roll = cam[7] if cam.size() > 7 else 0.0
	aim(aim_cx, aim_cy, aim_z, aim_dk)
	for k in actors:
		var a: Dictionary = actors[k]
		var r := shot.value("a:%s:root" % k, f, 3)
		a.node.visible = r.size() == 7
		if a.node.visible:
			a.node.position = Vector3(r[0], r[1], r[2])
			a.node.quaternion = Quaternion(r[3], r[4], r[5], r[6]).normalized()
	for e in _plan:
		var kind: String = e[1]
		var v := shot.value(e[0], f, 0 if (kind == "bq" or kind == "or") else -1)
		if v.is_empty():
			continue
		match kind:
			"bq":
				e[2].skel.set_bone_pose_rotation(e[3], Quaternion(v[0], v[1], v[2], v[3]).normalized())
			"bp":
				e[2].skel.set_bone_pose_position(e[3], Vector3(v[0], v[1], v[2]))
			"bs":
				e[2].skel.set_bone_pose_scale(e[3], Vector3(v[0], v[1], v[2]))
			"f":
				e[2].W[e[3]] = v[0]
			"inner":
				e[2].inner.scale = Vector3(v[0], v[1], v[2])
			"ot":
				e[2].position = Vector3(v[0], v[1], v[2])
			"or":
				e[2].quaternion = Quaternion(v[0], v[1], v[2], v[3]).normalized()
			"os":
				e[2].scale = Vector3(v[0], v[1], v[2])
			"ov":
				e[2].visible = v[0] > 0.5
			"li":
				e[2].light_energy = v[0] * Location.light_k()
			"lv":
				e[2].visible = v[0] > 0.5
			"lc":
				e[2].light_color = Color(v[0], v[1], v[2])
			"lt":
				e[2].global_position = Vector3(v[0], v[1], v[2])
			"ld":
				var tgt := Vector3(v[0], v[1], v[2])
				if not (e[2] is OmniLight3D) and e[2].global_position.distance_to(tgt) > 0.001:
					e[2].look_at(tgt)
			"lr":
				if v[0] > 0.0:
					if e[2] is OmniLight3D:
						e[2].omni_range = v[0]
					elif e[2] is SpotLight3D:
						e[2].spot_range = v[0]

	_faces(dt, now)
	if feeds and shot.loc == "lobby":
		feeds.all_often = bool(SHOTS.get(shot.id, {}).get("feeds", false))
		feeds.tick()


## Тихие перемены в холле (o.props в scenes.js): куртка пропала после открытой двери, журнал перевязан до 02:50, телефон Фрэнка
## на зарядке, часы идут до трёх и встают. Запись кадра (bake) поверх — если кадр сам меняет эти предметы, прав кадр.
func _props() -> void:
	if shot.loc != "lobby" or not nodes.has("lobby"):
		return
	var m: Dictionary = nodes["lobby"]
	var P := norms
	var book := bool(P.get("book", 0))
	for e in [["jacket", bool(P.get("jacket", 1))], ["badge", bool(P.get("badge", 1))], ["book", book], ["note", not book],
			["noteCv", not book], ["fphone", bool(P.get("fphone", 0))]]:
		if m.has(e[0]):
			(m[e[0]] as Node3D).visible = e[1]
	var hm := String(P.get("clock", "02:57") if P.get("clock") != null else "02:57").split(":")
	var h := int(hm[0])
	var mi := int(hm[1]) if hm.size() > 1 else 0
	if m.has("clockM"):
		m.clockM.rotation.z = -mi / 60.0 * TAU
	if m.has("clockH"):
		m.clockH.rotation.z = -((h % 12) + mi / 60.0) / 12.0 * TAU


## Лицо: выражение из записи кадра + живые губы того, кто говорит; язык — своими весами (как faceV3).
func _faces(dt: float, now: float) -> void:
	var sp := ""
	if talk.has("who"):
		for k in NAME.get(talk.who, []):
			if actors.has(k) and actors[k].node.visible:
				sp = k
				break
	for k in actors:
		var a: Dictionary = actors[k]
		if not a.node.visible:
			continue
		var W: Dictionary = a.W
		a.lips.update(dt, float(talk.get("v", 0.0)) if k == sp else 0.0, String(talk.get("vis", "")) if k == sp else "", now, W)
		# язык лежит на дне рта: опускается вместе с челюстью и губами (своих форм звуков почти не берёт — иначе торчит вперёд)
		var t_open := minf(1.0, W.get("jawOpen", 0.0) + 0.6 * maxf(maxf(W.get("viseme_aa", 0.0), 0.85 * W.get("viseme_O", 0.0)),
			maxf(0.7 * W.get("viseme_E", 0.0), maxf(0.5 * W.get("viseme_U", 0.0), 0.45 * W.get("viseme_I", 0.0)))))
		for m in a.meshes:
			var mi: MeshInstance3D = m[0]
			var d: Dictionary = m[1]
			for bn in d:
				var w := 0.0
				if m[2]:
					w = t_open if bn == "jawOpen" else (0.3 * W.get(bn, 0.0) if bn == "viseme_TH" or bn == "viseme_DD" else 0.0)
				else:
					w = W.get(bn, 0.0)
				mi.set_blend_shape_value(d[bn], w)


## Камера на план кадра — как aim() в старой игре (scenes.js): точка (cx, cy) кадра становится центром, зум сужает угол
## вокруг неё; наезд наполовину настоящий проезд камеры (не дальше 30 см), наполовину объективом.
func aim(cx: float, cy: float, z: float, dk := 0.5) -> void:
	camera.fov = base_fov
	camera.look_at_from_position(base_pos, base_look)
	if base_roll != 0.0:
		camera.rotate_object_local(Vector3.FORWARD, base_roll)
	var vp := get_viewport().get_visible_rect().size
	var dir := camera.project_ray_normal(Vector2(cx * vp.x, cy * vp.y))
	camera.look_at(base_pos + dir)
	if dk > 0.0 and z > 1.001:
		var dd := maxf(0.6, (base_look - base_pos).dot(dir))
		var m := minf(0.3, dk * dd * (1.0 - 1.0 / minf(z, 1.35)))
		camera.position += dir * m
		z *= (dd - m) / dd
	camera.fov = rad_to_deg(2.0 * atan(tan(deg_to_rad(base_fov) / 2.0) / maxf(0.3, z)))


## Место кадра. Все места стоят в одних координатах, поэтому каждое — на своём слое: камера кадра видит слой своего места,
## камеры мониторов будки — свои места (холл и седьмой этаж). Свет: у места кадра — с тенями, у места на мониторе — без теней.
func _use_location(loc: String) -> void:
	_load_location(loc)
	if loc == "lobby":
		_load_location("corridor")
		if feeds == null:
			feeds = Feeds.new()
			add_child(feeds)
			feeds.setup(locations.lobby)
	var bit: int = 1 << (locations[loc].layer - 1)
	for k in locations:
		locations[k].set_lit(k == loc or (loc == "lobby" and k == "corridor"), k == loc)
	camera.cull_mask = bit | (1 << (Location.SCREEN_LAYER - 1))
	fill.layers = bit
	fill.light_cull_mask = bit
	for k in actors:
		_actor_layer(actors[k].node, bit)
	world_env.environment = locations[loc].env


func _load_location(loc: String) -> void:
	if locations.has(loc):
		return
	var l := Location.create(loc)
	add_child(l)
	locations[loc] = l
	var map := {}
	_index(l, map)
	nodes[loc] = map


func _actor_layer(n: Node, bit: int) -> void:
	if n is VisualInstance3D:
		(n as VisualInstance3D).layers = bit
	for c in n.get_children():
		_actor_layer(c, bit)


func _index(n: Node, map: Dictionary) -> void:
	var s := String(n.name)
	if not map.has(s):
		map[s] = n
	for c in n.get_children():
		_index(c, map)


## Узел места по имени; предметы (prop_*) создаются из world/props при первом появлении.
func _node(name: String) -> Node3D:
	var map: Dictionary = nodes[shot.loc]
	if map.has(name):
		return map[name]
	if name.begins_with("prop_") and ResourceLoader.exists("res://world/props/%s.glb" % name):
		var p: Node3D = (load("res://world/props/%s.glb" % name) as PackedScene).instantiate()
		p.name = name
		locations[shot.loc].add_child(p)
		locations[shot.loc].set_layer(p, 1 << (locations[shot.loc].layer - 1))
		map[name] = p
		return p
	return null


func _actor(key: String) -> Dictionary:
	if actors.has(key):
		return actors[key]
	var node := Node3D.new()
	node.name = "actor_" + key
	var inner := Node3D.new()
	node.add_child(inner)
	var model: Node3D = (load("res://assets/models/%s.glb" % CAST.get(key, "ethan")) as PackedScene).instantiate()
	inner.add_child(model)
	var rig := HumanRig.rebase(model)
	var morph := {}    # имена форм, которые есть у героя
	var meshes := []   # [сетка, {форма → индекс}, это язык]
	for mi in model.find_children("*", "MeshInstance3D", true, false):
		var m := mi as MeshInstance3D
		if m.mesh is ArrayMesh and (m.mesh as ArrayMesh).get_blend_shape_count() > 0:
			var d := {}
			for i in (m.mesh as ArrayMesh).get_blend_shape_count():
				var bn := String((m.mesh as ArrayMesh).get_blend_shape_name(i))
				d[bn] = i
				morph[bn] = true
			meshes.append([m, d, String(m.name).to_lower().contains("tongue")])
	add_child(node)
	if shot:
		_actor_layer(node, 1 << (locations[shot.loc].layer - 1))
	var lips := LipSync.new(key)
	lips.lag = LIP_LAG.get(key, 0.0)
	lips.live = not (key in FROZEN)
	var a := {"node": node, "inner": inner, "skel": rig.skel, "morph": morph, "meshes": meshes, "W": {}, "lips": lips}
	actors[key] = a
	return a
