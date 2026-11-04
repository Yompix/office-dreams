class_name Explore
extends RefCounted
## Прогулка: игрок сам ведёт героя (WASD / стрелки / левый стик), камера за плечом (правый стик или правая кнопка
## мыши — повернуть; при ходьбе сама заходит за спину). У точки осмотра — кружок и подсказка «E — …»; нажал —
## герой подходит, встаёт как надо, точка играет свои шаги сценария (реплики, выбор без таймера, флаги).
## Прогулка кончается у выхода (дверь магазина и т. п.). Сценарий — data/chN.json → "explore": {имя: описание}:
##   who — кого ведём; start {at, face} — откуда (нет — с того места, где стоит); intro / setup — шаги в начале;
##   walk — прямоугольники [x0, z0, x1, z1], где можно ходить; block — где нельзя (постройки, машина, люди);
##   floors — [x0, z0, x1, z1, высота] (крыльцо); cam_block — [x0, z0, x1, z1, высота] куда не заходит камера (ниже камеры — не мешает, она смотрит поверх);
##   spots — точки: {id, at (кружок), where (где нажимать; нет — at), r, stand, face, cam [поз, цель, угол],
##     label {ru, en}, if, once (по умолчанию да), beats, ask (выбор тем, game._ask), after, exit};
##   zones — {rect, if, beats, to, manual}: один раз, когда герой зашёл; to [x, z, лицом] — потом встать туда (лёд: отступил);
##     manual — только для игрока (автопрогон зону не задевает); nudge {after, beats} — поторопить один раз;
##   hum {if, v} — гул в голове на время прогулки; watch — кто смотрит на героя, когда тот рядом.
##   doors — двери, которые герой открывает сам и заходит внутрь (владелец, 4 ноя: «не могу открыть дверь и зайти»):
##     {id, mesh [имена створки и её стекла в модели], hinge [x, z] — ось петли, open — угол (°), где/кольцо/подпись как
##     у точки, sfx; unblock / block — какие препятствия снять (весь дом) и какие поставить (стены без проёма, мебель),
##     cam_unblock / cam_block — то же для камеры, floors, nav — пол и точки пути внутри}. Точка с need — только когда
##     эта дверь открыта (выход «Заплатить» — у прилавка). В конце прогулки двери закрываются.
## Автопрогон (--autoplay) обходит точки по порядку сам; --explore exit — сразу к выходу; --skip a,b — эти точки обойти.

const SPEED := 1.35          # шаг Ноя, м/с (у клипа Rk_Walk при его росте естественно 1,38)
const RAD := 0.22            # «толщина» героя: на столько он не подходит к стенам
const DIST := 2.75           # камера: на сколько позади героя
const RISE := 0.42           # и на сколько выше головы
const MARK_NEAR := 7.5       # кружки над точками видны ближе этого (м)

var g                        # режиссёр (game.gd)
var d: Dictionary            # описание прогулки
var a: Actor                 # кого ведём
var yaw := 0.0               # камера: куда смотрит (рад, как rotation.y героя: вперёд — (sin, 0, cos))
var busy := false
var mouse_dx := 0.0          # поворот мышью (правая кнопка) — копит game._input; на телефоне — палец справа (touch_ctl)
var look_dy := 0.0           # взгляд вверх / вниз: палец справа по вертикали (телефон)
var pitch := 0.0             # наклон взгляда камеры: + вверх, − вниз (рад, −0,45…0,55)
var _orbit_t := 9.0          # сколько секунд назад игрок сам поворачивал камеру
var _prev := Vector3.ZERO    # последнее разрешённое место героя
var _near: Dictionary = {}   # точка, у которой стоим
var _used := {}              # id точки → сколько раз осматривали
var _marks := {}             # id точки → кружок (Sprite3D)
var _t := 0.0
var _moved := false
var _vel := Vector2.ZERO     # скорость героя на прогулке (м/с, по x и z)
var _nudged := false
var _zones := {}
var _exit := false
var _e_prev := true          # клавиша E была нажата в прошлом кадре (нажатие до начала прогулки не считаем)
var _looks := {}             # кто куда смотрел до прогулки — вернуть в конце
var _tight := false          # камере тесно (вплотную к стене): она над плечом — смотрит дальше вперёд, а не в землю
var _opened := {}            # id двери → узел-петля, на котором повёрнута створка
var _intro := false          # надписи вступления ещё идут (герой уже ходит): осмотр и зоны — после них
var _use_later := false      # «Осмотреть» нажали во время вступления — осмотреть, как только оно кончится
static var _ring: Texture2D


func _init(game, def: Dictionary) -> void:
	g = game
	d = def.duplicate(true)   # двери меняют препятствия, пол и путь — на копии (данные главы остаются как были)


func run() -> void:
	a = g.actors[String(d.get("who", "noah"))]
	if a.get_parent() != g.scene:
		var gt := a.global_transform
		a.reparent(g.scene, false)
		a.global_transform = gt
		a.rotation = Vector3(0, a.rotation.y, 0)
	var st: Dictionary = d.get("start", {})
	if st.has("at"):
		g._place({"who": String(d.get("who", "noah")), "at": st.at, "face": st.get("face", 0.0), "clip": "Idle"})
	a.stop_walk()
	for k in d.get("watch", []):
		var w = g.actors.get(String(k))
		if w is Actor and is_instance_valid(w):
			_looks[k] = [(w as Actor).look_actor, (w as Actor).look_target]
	for b in d.get("setup", []):
		await g._beat(b)
	yaw = a.rotation.y
	_prev = a.position
	var hum: Dictionary = d.get("hum", {})
	var hum0: float = g.hud.hum_to
	if not hum.is_empty() and (not hum.has("if") or g._cond(hum["if"])):
		g.hud.hum_to = float(hum.get("v", 0.3))
	_make_marks()
	g._ex = self
	g._cut("explore")
	var intro: Array = d.get("intro", [])
	if g.autoplay or "--walkauto" in OS.get_cmdline_user_args():   # --walkauto: проверки управления (--qtetest) проходят прогулку сами
		for b in intro:
			await g._beat(b)
		g._cut("explore")
		await _auto()
	else:
		# Игрок ведёт героя сразу (владелец, 4 ноя: «не во всех локациях на телефоне получается ходить»): раньше всё
		# вступление (надписи, реплики — 3–9 с) шло до управления, а камера и джойстик уже были прогулочные — палец
		# и клавиши не слушались. Теперь до управления — только общие планы вступления (шаги со склейкой «shot»),
		# надписи и реплики идут уже на ходу; осмотр и зоны — после них.
		var k := 0
		while k < intro.size() and (intro[k] as Dictionary).has("shot"):
			await g._beat(intro[k])
			k += 1
		g._cut("explore")
		if k < intro.size():
			_intro_walk(intro.slice(k))
		await _loop()
		while _intro:   # выход не жмётся во время вступления, но на всякий случай — не оставлять надпись в следующей сцене
			g._skip = true
			await g.get_tree().process_frame
	g.hud.prompt("")
	for m in _marks.values():
		if is_instance_valid(m):
			(m as Node).queue_free()
	_marks.clear()
	for k in _looks:   # кто смотрел на героя — снова туда, куда смотрел до прогулки
		var w = g.actors.get(String(k))
		if w is Actor and is_instance_valid(w):
			(w as Actor).look_actor = _looks[k][0]
			(w as Actor).look_target = _looks[k][1]
	if not hum.is_empty():
		g.hud.hum_to = hum0
	for k in _opened:   # двери закрываются за героем (на склейке; узел-петля остаётся, створка — на месте)
		var pv: Node3D = _opened[k]
		if is_instance_valid(pv):
			pv.rotation.y = 0.0
	g._ex = null
	g._log("прогулка: осмотрено %s" % JSON.stringify(_used))


# ---------------------------------------------------------------------------------------------------- игрок ведёт

## Надписи и реплики вступления — пока игрок уже ведёт героя (склейки из них убраны: камера — прогулочная).
func _intro_walk(beats: Array) -> void:
	_intro = true
	for b in beats:
		if _exit:
			break
		var bb: Dictionary = (b as Dictionary).duplicate()
		bb.erase("shot")
		await g._beat(bb)
	_intro = false


func _loop() -> void:
	while not _exit:
		await g.get_tree().process_frame
		var dt: float = g._dt()
		if dt <= 0.0 or busy:
			continue
		if not _intro:
			_t += dt   # время прогулки — после надписей вступления (подсказка «как ходить», «поторопить»)
		_tick(dt)
		var e: bool = Input.is_physical_key_pressed(KEY_E)
		var lmb: bool = Input.is_mouse_button_pressed(MOUSE_BUTTON_LEFT) and not TouchCtl.enabled   # телефон: касание джойстика — не «осмотреть»
		var press: bool = (e and not _e_prev) or Input.is_action_just_pressed("ui_accept") \
			or lmb and not _e_prev or TouchCtl.take_use()
		_e_prev = e or lmb
		var later := _use_later and not _intro   # «Осмотреть» нажали во время надписей вступления — сразу после них
		if later:
			_use_later = false
		if press and _intro and not _near.is_empty():
			_use_later = true
		if (press or later) and not _near.is_empty() and not g.hud.choosing() and not _intro:
			await _use(_near)
			continue
		if not _intro:
			await _zones_check()
		var nd: Dictionary = d.get("nudge", {})
		if not _nudged and not nd.is_empty() and _t > float(nd.get("after", 180.0)):
			_nudged = true
			busy = true
			g.hud.prompt("")
			for b in nd.get("beats", []):
				await g._beat(b)
			busy = false


func _tick(dt: float) -> void:
	# камера: правый стик / мышь; при ходьбе сама плавно заходит герою за спину
	var rx := Input.get_joy_axis(0, JOY_AXIS_RIGHT_X)
	if absf(rx) > 0.2:
		yaw -= rx * dt * 2.4
		_orbit_t = 0.0
	if absf(mouse_dx) > 0.0:
		yaw -= mouse_dx * 0.006
		mouse_dx = 0.0
		_orbit_t = 0.0
	if absf(look_dy) > 0.0:
		pitch = clampf(pitch + look_dy * 0.004, -0.45, 0.55)
		look_dy = 0.0
		_orbit_t = 0.0
	_orbit_t += dt
	if a.speed() > 0.3 and _orbit_t > 1.5:
		yaw = lerp_angle(yaw, a.rotation.y, 1.0 - exp(-dt * 1.25))
		pitch = lerpf(pitch, 0.0, 1.0 - exp(-dt * 1.5))   # пошёл — взгляд сам возвращается к дороге
	# ход: куда жмут — относительно камеры; скорость с разгоном и торможением, стены — по осям отдельно
	# (упёрся в стену наискось — скользит вдоль неё; внутрь препятствия шаг не делается никогда)
	var v := _input_vec()
	var f := Vector3(sin(yaw), 0.0, cos(yaw))
	var r := f.cross(Vector3.UP)
	var dir := r * v.x + f * v.y
	var want := Vector2.ZERO
	if dir.length() > 0.12:
		_moved = true
		want = Vector2(dir.x, dir.z).normalized() * SPEED * clampf(dir.length(), 0.35, 1.0)
	_vel = _vel.move_toward(want, dt * (4.0 if want.length() > _vel.length() else 6.0))
	var p := a.position
	var inside := not _free(p.x, p.z)   # поставила сцена внутрь препятствия — выпускаем любым шагом
	var nx := p.x + _vel.x * dt
	if inside or _free(nx, p.z):
		p.x = nx
	else:
		_vel.x = 0.0
	var nz := p.z + _vel.y * dt
	if inside or _free(p.x, nz):
		p.z = nz
	else:
		_vel.y = 0.0
	p.y = move_toward(p.y, _floor(p.x, p.z), dt * 2.4)   # на крыльцо — шагом, не всплывая
	a.position = p
	_prev = p
	a.drive(Vector3(_vel.x, 0.0, _vel.y), dt, Vector3(want.x, 0.0, want.y))
	if _vel.length() > 0.1:
		g._walks[String(d.get("who", "noah"))] = true   # шаги по снегу (режиссёр гасит их, когда герой стоит)
	_find_near()
	_update_marks(dt)
	for k in d.get("watch", []):   # Дэнни и Айрис поднимают глаза, когда Ной подходит близко
		var w = g.actors.get(String(k))
		if not (w is Actor) or not is_instance_valid(w) or not _looks.has(k):
			continue
		var wa := w as Actor
		var close := wa.global_position.distance_to(a.global_position) < 3.2
		if close and wa.look_actor != a:
			wa.look_actor = a
		elif not close and wa.look_actor == a:
			wa.look_actor = _looks[k][0]
			wa.look_target = _looks[k][1]


## Куда жмут: клавиатура (WASD, стрелки) или стик / крестовина — что сильнее.
func _input_vec() -> Vector2:
	var k := Vector2.ZERO
	if Input.is_physical_key_pressed(KEY_W) or Input.is_key_pressed(KEY_UP):
		k.y += 1.0
	if Input.is_physical_key_pressed(KEY_S) or Input.is_key_pressed(KEY_DOWN):
		k.y -= 1.0
	if Input.is_physical_key_pressed(KEY_A) or Input.is_key_pressed(KEY_LEFT):
		k.x -= 1.0
	if Input.is_physical_key_pressed(KEY_D) or Input.is_key_pressed(KEY_RIGHT):
		k.x += 1.0
	var p := Vector2(Input.get_joy_axis(0, JOY_AXIS_LEFT_X), -Input.get_joy_axis(0, JOY_AXIS_LEFT_Y))
	if p.length() < 0.22:
		p = Vector2.ZERO
	if Input.is_joy_button_pressed(0, JOY_BUTTON_DPAD_UP):
		p.y = 1.0
	if Input.is_joy_button_pressed(0, JOY_BUTTON_DPAD_DOWN):
		p.y = -1.0
	if Input.is_joy_button_pressed(0, JOY_BUTTON_DPAD_LEFT):
		p.x = -1.0
	if Input.is_joy_button_pressed(0, JOY_BUTTON_DPAD_RIGHT):
		p.x = 1.0
	var v := p if p.length() > k.length() else k.normalized()
	if TouchCtl.enabled and TouchCtl.move.length() > maxf(v.length(), 0.12):   # телефон: джойстик на экране
		v = TouchCtl.move
	return v.limit_length(1.0)


func _free(x: float, z: float) -> bool:
	var inside := false
	for w in d.get("walk", []):
		if x >= float(w[0]) + RAD and x <= float(w[2]) - RAD and z >= float(w[1]) + RAD and z <= float(w[3]) - RAD:
			inside = true
			break
	if not inside:
		return false
	for b in d.get("block", []):
		if b.size() > 4 and not g._cond(b[4]):   # [x0, z0, x1, z1, условие] — препятствие только при условии
			continue
		if x > float(b[0]) - RAD and x < float(b[2]) + RAD and z > float(b[1]) - RAD and z < float(b[3]) + RAD:
			return false
	return true


func _floor(x: float, z: float) -> float:
	for f in d.get("floors", []):
		if x >= float(f[0]) and x <= float(f[2]) and z >= float(f[1]) and z <= float(f[3]):
			return float(f[4])
	return 0.0


# ---------------------------------------------------------------------------------------------------- точки

func _avail(s: Dictionary) -> bool:
	if s.has("if") and not g._cond(s["if"]):
		return false
	if s.has("need") and not _opened.has(String(s.need)):   # за этой дверью — ещё закрыта
		return false
	if s.get("once", true) and _used.has(String(s.id)):
		return false
	if s.has("ask") and not g._ask_left(s.ask, "ex_" + String(s.id)):   # все темы разговора уже спрошены
		return false
	return true


func _where(s: Dictionary) -> Vector2:
	var w: Array = s.get("where", s.at)
	return Vector2(float(w[0]), float(w[w.size() - 1]))


func _find_near() -> void:
	var best: Dictionary = {}
	var bs := 1e9
	var fw := Vector2(sin(a.rotation.y), cos(a.rotation.y))
	var me := Vector2(a.position.x, a.position.z)
	for s in _spots():
		if not _avail(s):
			continue
		var to := _where(s) - me
		var dd := to.length()
		if dd > float(s.get("r", 1.2)):
			continue
		var sc := dd - (to.normalized().dot(fw) * 0.6 if dd > 0.05 else 0.6)   # ближе и перед лицом — выше
		if sc < bs:
			bs = sc
			best = s
	if best != _near:
		_near = best
	if _intro:   # надписи вступления — там же, внизу кадра: подсказки — сразу после них, а не одна на другой
		g.hud.prompt("")
		return
	if not _near.is_empty():
		var key := "A" if g._pad else ("" if TouchCtl.enabled else "E")
		g.hud.prompt(("%s   %s" % [key, g.hud.T(_near.get("label", {}))]).strip_edges())
	elif not _moved and _t < 14.0 and TouchCtl.enabled and not g._pad:
		g.hud.prompt("Слева веди пальцем — идти   ·   справа — смотреть   ·   «Осмотреть» — кнопка" if g.lang == "ru"
			else "Drag on the left — walk   ·   on the right — look around   ·   «Look» — button")
	elif not _moved and _t < 14.0:
		g.hud.prompt(("Стик — идти   ·   A — осмотреть" if g._pad else "WASD — идти   ·   E — осмотреть") if g.lang == "ru"
			else ("Stick — walk   ·   A — interact" if g._pad else "WASD — walk   ·   E — interact"))
	else:
		g.hud.prompt("")


func _spots() -> Array:
	var all: Array = (d.get("spots", []) as Array).duplicate()
	for dr in d.get("doors", []):
		if not _opened.has(String(dr.id)):
			var e: Dictionary = (dr as Dictionary).duplicate()
			e["door"] = true
			all.append(e)
	if d.has("exit"):
		var e: Dictionary = (d.exit as Dictionary).duplicate()
		e["exit"] = true
		e["once"] = false
		if not e.has("id"):
			e["id"] = "exit"
		all.append(e)
	return all


## Осмотреть точку: подойти, встать лицом, свой план, шаги сценария; потом — снова игрок.
func _use(s: Dictionary) -> void:
	busy = true
	g.hud.prompt("")
	_hide_marks()
	var id := String(s.id)
	if s.has("stand"):
		var sp: Array = s.stand
		var to := Vector3(float(sp[0]), 0.0, float(sp[sp.size() - 1]))
		to.y = _floor(to.x, to.z)
		if Vector2(to.x - a.position.x, to.z - a.position.z).length() > 0.1:
			a.walk_to(to, 1.1, "Rk_Walk", "Idle", s.get("face"))
			g._walks[String(d.get("who", "noah"))] = true
			var left := 4.0
			while a.walking and left > 0.0:
				await g.get_tree().process_frame
				left -= g._dt()
				a.position.y = move_toward(a.position.y, _floor(a.position.x, a.position.z), g._dt() * 2.4)
			if a.walking:   # не дошёл (что-то мешало) — встать на место
				a.stop_walk()
				a.position = to
				a.play("Idle", 0.0)
		if s.get("face") != null:
			a.face_to(deg_to_rad(float(s.face)))
	elif s.get("face") != null:
		a.face_to(deg_to_rad(float(s.face)))
	if s.get("door", false):
		await _open_door(s)
	_used[id] = int(_used.get(id, 0)) + 1
	if s.has("cam"):
		g._cam_free = s.cam
		g._cut("free")
	for b in s.get("beats", []):
		await g._beat(b)
	if s.has("ask"):
		await g._ask(s.ask, "ex_" + id)
	for b in s.get("after", []):
		await g._beat(b)
	if s.get("exit", false):
		_exit = true
		busy = false
		return
	yaw = a.rotation.y + PI if s.get("turn_back", false) else _yaw_after(s)
	g._cut("explore")
	_prev = a.position
	_e_prev = true
	busy = false


## Открыть дверь: створка (и её стекло) поворачивается на петле, звук; проём свободен — снять с дома «сплошное»
## препятствие, поставить стены без проёма и мебель внутри, пол и точки пути внутри, камере — стены вместо дома.
func _open_door(s: Dictionary) -> void:
	var id := String(s.id)
	var hinge: Array = s.get("hinge", [])
	var leaves: Array = []
	for nm in s.get("mesh", []):
		for n in g.scene.find_children(String(nm), "Node3D", true, false):
			var n3 := n as Node3D
			if hinge.size() >= 2 and Vector2(n3.global_position.x - float(hinge[0]), n3.global_position.z - float(hinge[1])).length() > 3.0:
				continue   # одноимённая дверь в другом месте (shop_door — и на заправке, и в деревне)
			leaves.append(n3)
	if not leaves.is_empty():
		var first: Node3D = leaves[0]
		var piv := Node3D.new()
		piv.name = "hinge_" + id
		first.get_parent().add_child(piv)
		piv.global_position = Vector3(float(hinge[0]), first.global_position.y, float(hinge[1])) if hinge.size() >= 2 else first.global_position
		for l in leaves:
			(l as Node3D).reparent(piv, true)
		_opened[id] = piv
		var tw: Tween = g.create_tween()
		tw.tween_property(piv, "rotation:y", deg_to_rad(float(s.get("open", 90.0))), 0.9).set_trans(Tween.TRANS_SINE).set_ease(Tween.EASE_OUT)
	else:
		_opened[id] = null
	if s.has("sfx"):
		g.sfx.play(String(s.sfx), -8.0)
	_swap("block", s.get("unblock", []), s.get("block", []))
	_swap("cam_block", s.get("cam_unblock", []), s.get("cam_block", []))
	for key in ["floors", "nav", "walk"]:
		if s.has(key):
			if not d.has(key):
				d[key] = []
			(d[key] as Array).append_array(s[key])
	await g._wait(0.5)   # створка приоткрылась — дальше игрок идёт сам


## В списке препятствий: убрать прямоугольники из out (по первым четырём числам), добавить add.
func _swap(key: String, out: Array, add: Array) -> void:
	var cur: Array = d.get(key, [])
	for o in out:
		for i in range(cur.size() - 1, -1, -1):
			var b: Array = cur[i]
			if absf(float(b[0]) - float(o[0])) < 0.02 and absf(float(b[1]) - float(o[1])) < 0.02 \
					and absf(float(b[2]) - float(o[2])) < 0.02 and absf(float(b[3]) - float(o[3])) < 0.02:
				cur.remove_at(i)
	cur.append_array(add)
	d[key] = cur


## Камера после точки: за спину героя, если он стоит лицом к стене / человеку — иначе туда же, куда была.
func _yaw_after(_s: Dictionary) -> float:
	return a.rotation.y


func _zones_check() -> void:
	for z in d.get("zones", []):
		var key := JSON.stringify(z.rect)
		if _zones.has(key) or (z.has("if") and not g._cond(z["if"])):
			continue
		if z.get("manual", false) and g.autoplay:   # «только для игрока» (тонкий лёд): автопрогон идёт по белому, как задумано
			continue
		var rc: Array = z.rect
		var p := a.position
		if p.x >= float(rc[0]) and p.x <= float(rc[2]) and p.z >= float(rc[1]) and p.z <= float(rc[3]):
			_zones[key] = true
			busy = true
			g.hud.prompt("")
			for b in z.get("beats", []):
				await g._beat(b)
			if z.has("to"):   # после шагов — на безопасное место [x, z, лицом]: отступил от тонкого льда, вытащили из полыньи
				var to: Array = z.to
				a.stop_walk()
				a.position = Vector3(float(to[0]), _floor(float(to[0]), float(to[1])), float(to[1]))
				if to.size() > 2:
					a.rotation.y = deg_to_rad(float(to[2]))
				a.play("Idle", 0.0)
				_prev = a.position
				_vel = Vector2.ZERO
			busy = false


# ---------------------------------------------------------------------------------------------------- камера

func place_cam(cam: Camera3D) -> void:
	# за правым плечом: герой чуть левее середины кадра, впереди видно, куда идёт
	var f := Vector3(sin(yaw), 0.0, cos(yaw))
	var r := f.cross(Vector3.UP)
	var head := a.global_position + Vector3(0, 1.5, 0)
	var eye := head - f * DIST + r * 0.38 + Vector3(0, RISE - pitch * 0.55, 0)   # смотрит вверх — камера чуть ниже
	eye.y = maxf(eye.y, a.global_position.y + 0.45)
	eye = _cam_clear(head + r * 0.2, eye)
	cam.fov = 56.0
	var up := Vector3(0, pitch * 2.6, 0)   # взгляд вверх / вниз (палец справа на телефоне)
	if _tight:   # над плечом: цель дальше и выше — в кадре дорога впереди и макушка героя, а не одна земля
		cam.look_at_from_position(eye, head + f * 3.6 + r * 0.22 + Vector3(0, -0.3, 0) + up)
	else:
		cam.look_at_from_position(eye, head + f * 1.4 + r * 0.22 + Vector3(0, -0.38, 0) + up)


## После сглаживания (game._smooth_cam тянет камеру с упреждением) — если всё же въехала в постройку, вытолкнуть к герою.
func clamp_cam(cam: Camera3D) -> void:
	var head := a.global_position + Vector3(0, 1.5, 0)
	var p := cam.global_position
	if not _cam_in(p):
		return
	for i in range(1, 21):
		var q := p.lerp(head, i / 20.0)
		if not _cam_in(q):
			cam.global_position = q
			return


## over — не считать преграды ниже этой высоты (камера и так над ними).
func _cam_in(q: Vector3, over := -1.0) -> bool:
	for b in d.get("cam_block", []):
		var top := float(b[4]) if b.size() > 4 else 99.0
		if top < over:
			continue
		if q.x > float(b[0]) - 0.12 and q.x < float(b[2]) + 0.12 and q.z > float(b[1]) - 0.12 and q.z < float(b[3]) + 0.12 \
				and q.y < top:
			return true
	return false


## Камера не заходит в постройки: идём от головы героя к камере, пока не упрёмся; совсем тесно — выше, над плечом.
## Низкое (машина ниже камеры) не мешает: камера и так над ним и смотрит поверх — раньше у бока машины
## она прыгала над головой Ноя и смотрела в снег (видео главы 1, 3 ноя).
func _cam_clear(head: Vector3, eye: Vector3) -> Vector3:
	_tight = false
	var last := head
	for i in range(1, 25):
		var q := head.lerp(eye, i / 24.0)
		if _cam_in(q, eye.y - 0.1):
			if head.distance_to(last) > 1.0:
				return last
			_tight = true
			return head.lerp(eye, 0.3) + Vector3(0, 0.5, 0)
		last = q
	return eye


# ---------------------------------------------------------------------------------------------------- кружки над точками

static func ring_tex() -> Texture2D:
	if _ring != null:
		return _ring
	var n := 96
	var img := Image.create(n, n, false, Image.FORMAT_RGBA8)
	for y in n:
		for x in n:
			var r := Vector2(x - (n - 1) * 0.5, y - (n - 1) * 0.5).length()
			var ring := clampf(1.0 - absf(r - 34.0) / 5.0, 0.0, 1.0)
			var dot := clampf(1.0 - (r - 9.0) / 3.0, 0.0, 1.0)
			img.set_pixel(x, y, Color(1, 1, 1, maxf(ring * 0.9, dot)))
	_ring = ImageTexture.create_from_image(img)
	return _ring


func _make_marks() -> void:
	for s in _spots():
		var sp := Sprite3D.new()
		sp.texture = ring_tex()
		sp.billboard = BaseMaterial3D.BILLBOARD_ENABLED
		sp.shaded = false
		sp.pixel_size = 0.0022
		sp.alpha_cut = SpriteBase3D.ALPHA_CUT_DISABLED
		sp.modulate = Color(1, 1, 1, 0)
		sp.render_priority = 10
		sp.layers = 1
		var at: Array = s.at
		g.scene.add_child(sp)
		sp.global_position = Vector3(at[0], at[1], at[2])
		_marks[String(s.id)] = sp


func _update_marks(dt: float) -> void:
	var me := a.global_position
	for s in _spots():
		var m: Sprite3D = _marks.get(String(s.id))
		if m == null or not is_instance_valid(m):
			continue
		var want := 0.0
		if _avail(s):
			var dd := me.distance_to(m.global_position)
			want = clampf((MARK_NEAR - dd) / 2.5, 0.0, 0.55)
			if not _near.is_empty() and String(_near.id) == String(s.id):
				want = 1.0
		m.modulate.a = move_toward(m.modulate.a, want, dt * 3.0)
		var sc := 1.25 if want >= 1.0 else 1.0
		m.scale = m.scale.lerp(Vector3.ONE * sc, 1.0 - exp(-dt * 8.0))


func _hide_marks() -> void:
	for m in _marks.values():
		if is_instance_valid(m):
			(m as Sprite3D).modulate.a = 0.0


# ---------------------------------------------------------------------------------------------------- автопрогон

## Проверки: обойти доступные точки по порядку (выборы — по очереди, без --pick), потом выход.
func _auto() -> void:
	var mode := "all"
	var args := OS.get_cmdline_user_args()
	if "--explore" in args:
		mode = args[args.find("--explore") + 1]
	var skip: PackedStringArray = args[args.find("--skip") + 1].split(",") if "--skip" in args else PackedStringArray()   # маршрут обходит эти точки (необязательная шкатулка и т. п.)
	if mode != "exit":
		for s in d.get("spots", []):
			if String(s.id) in skip or not _avail(s):
				continue
			if mode != "all" and not (String(s.id) in mode.split(",")):
				continue
			await _auto_go(s)
			await _use(s)
			while s.has("ask") and _avail(s):   # разговор: ещё раз, пока не кончатся темы
				await _use(s)
	if d.has("exit"):
		var e: Dictionary = (d.exit as Dictionary).duplicate()
		e["exit"] = true
		if not e.has("id"):
			e["id"] = "exit"
		if e.has("need"):   # выход за дверью: сначала открыть её
			for dr in d.get("doors", []):
				if String(dr.id) == String(e.need) and not _opened.has(String(dr.id)):
					var ds: Dictionary = (dr as Dictionary).duplicate()
					ds["door"] = true
					await _auto_go(ds)
					await _use(ds)
		await _auto_go(e)
		await _use(e)


func _auto_go(s: Dictionary) -> void:
	var w := _where(s)
	if g._fast:
		a.position = Vector3(w.x, _floor(w.x, w.y), w.y)
		a.placed()
		return
	for p in _path(Vector2(a.position.x, a.position.z), w):   # в обход машины, колонок и крыльца — по точкам "nav"
		var pt: Vector2 = p
		a.walk_to(Vector3(pt.x, _floor(pt.x, pt.y), pt.y), SPEED, "Rk_Walk", "Idle")
		g._walks[String(d.get("who", "noah"))] = true
		var left := 20.0
		while a.walking and left > 0.0:
			await g.get_tree().process_frame
			var dt: float = g._dt()
			left -= dt
			a.position.y = move_toward(a.position.y, _floor(a.position.x, a.position.z), dt * 2.4)
			yaw = lerp_angle(yaw, a.rotation.y, 1.0 - exp(-dt * 2.0))
		if a.walking:
			a.stop_walk()
			a.position = Vector3(pt.x, _floor(pt.x, pt.y), pt.y)


## Свободен ли отрезок (концы не проверяем: точка осмотра бывает вплотную к препятствию).
func _seg_free(p: Vector2, q: Vector2) -> bool:
	var n := maxi(2, int(ceil(p.distance_to(q) / 0.2)))
	for i in range(1, n):
		var t := p.lerp(q, float(i) / n)
		if not _free(t.x, t.y):
			return false
	return true


## Путь для автопрогона: напрямую, если свободно, иначе — кратчайший по точкам "nav" (Дейкстра по видимости).
func _path(p: Vector2, q: Vector2) -> Array:
	if _seg_free(p, q):
		return [q]
	var nodes: Array = [p]
	for w in d.get("nav", []):
		nodes.append(Vector2(float(w[0]), float(w[1])))
	nodes.append(q)
	var n := nodes.size()
	var dist: Array = []
	var prev: Array = []
	var done: Array = []
	for i in n:
		dist.append(1e9)
		prev.append(-1)
		done.append(false)
	dist[0] = 0.0
	for _k in n:
		var u := -1
		var best := 1e9
		for i in n:
			if not done[i] and float(dist[i]) < best:
				best = float(dist[i])
				u = i
		if u < 0 or u == n - 1:
			break
		done[u] = true
		for v in n:
			if done[v] or v == u or not _seg_free(nodes[u], nodes[v]):
				continue
			var alt: float = float(dist[u]) + (nodes[u] as Vector2).distance_to(nodes[v])
			if alt < float(dist[v]):
				dist[v] = alt
				prev[v] = u
	if int(prev[n - 1]) < 0:
		return [q]
	var out: Array = []
	var c := n - 1
	while c > 0:
		out.push_front(nodes[c])
		c = int(prev[c])
	return out
