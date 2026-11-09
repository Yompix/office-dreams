extends SceneTree
## Надписи на предметах по всей игре (владелец, 4 ноя: «названия выступают»): автопилот маршрута (ключи как у
## story_tests.py); каждую видимую Label3D один раз меряем — сколько от плоскости текста до поверхности за ней (доска,
## стена, бок машины). Больше 4 мм — «торчит», поверхность перед текстом ближе 5 см — «утоплена», за ней ничего
## в полуметре — «в воздухе» (надпись на стекле, висит). Плюс высота букв и шрифт.
##   $GODOT --headless --path godot --script res://scripts/dev/sign_audit.gd -- --autoplay --fast --pick 0,1,…
##   одна сцена: … -- --autoplay --scene c2_s4 --audit-secs 40
var t := 0.0
var total := 0.0
var limit := -1.0
var seen := {}   # путь узла → строка отчёта
var faces := {}


func _init() -> void:
	change_scene_to_file.call_deferred(String(ProjectSettings.get_setting("application/run/main_scene")))


func _process(d: float) -> bool:
	if limit < 0.0:
		var args := OS.get_cmdline_user_args()
		limit = float(args[args.find("--audit-secs") + 1]) if "--audit-secs" in args else 1e9
	total += d
	if total > limit:
		quit()
		return true
	var g = current_scene
	if g == null or not ("actors" in g):
		return false
	t += d
	if t < 0.5:
		return false
	t = 0.0
	var sid := String(g.get("_scene_id")) if g.get("_scene_id") != null else "?"
	for n in g.find_children("*", "Label3D", true, false):
		var lb := n as Label3D
		if lb == null or not lb.is_visible_in_tree() or lb.text.strip_edges() == "":
			continue
		var key := "%s «%s»" % [String(lb.get_parent().name), lb.text.substr(0, 24).replace("\n", " ")]
		if seen.has(key):
			continue
		seen[key] = _measure(g, lb, sid)
	return false


func _measure(root: Node, lb: Label3D, sid: String) -> String:
	var xf := lb.global_transform
	var box := lb.get_aabb()
	var nrm := xf.basis.z.normalized()
	if lb.billboard != BaseMaterial3D.BILLBOARD_DISABLED:
		return "BILLBOARD %s" % sid
	var pts: Array[Vector3] = []
	for u in [0.5, 0.15, 0.85]:
		for v in [0.5, 0.2, 0.8]:
			pts.append(xf * (box.position + Vector3(box.size.x * u, box.size.y * v, 0.0)))
	var back := INF
	var front := INF
	var hits := 0
	for p in pts:
		var b := _ray(root, p, -nrm, 0.5)
		if b < INF:
			hits += 1
			back = minf(back, b)
		front = minf(front, _ray(root, p, nrm, 0.05))
	var letter_h := float(lb.font_size) * lb.pixel_size
	var fnt := String(lb.font.resource_path.get_file()) if lb.font else "по умолчанию"
	var flag := "ок"
	if front < INF:
		flag = "УТОПЛЕНА"
	elif back == INF:
		flag = "В ВОЗДУХЕ"
	elif back > 0.004:
		flag = "ТОРЧИТ"
	return "%-9s сцена %s, зазор %s, перед ней %s, попаданий %d/9, буквы %.1f см, шрифт %s, shaded %s, outline %d, %s" % [
		flag, sid, ("%.1f мм" % (back * 1000.0)) if back < INF else "—", ("%.1f мм" % (front * 1000.0)) if front < INF else "—",
		hits, letter_h * 100.0, fnt, lb.shaded, lb.outline_size, str(lb.global_position.snapped(Vector3.ONE * 0.01))]


func _ray(root: Node, from: Vector3, dir: Vector3, far: float) -> float:
	var best := INF
	var seg := AABB(from, Vector3.ZERO).expand(from + dir * far).grow(0.01)
	for n in root.find_children("*", "MeshInstance3D", true, false):
		var mi := n as MeshInstance3D
		if mi == null or mi.mesh == null or not mi.is_visible_in_tree() or Seats._in_actor(mi):
			continue
		var wb: AABB = mi.global_transform * mi.get_aabb()
		if not wb.intersects(seg):
			continue
		var k := mi.mesh.get_instance_id()
		if not faces.has(k):
			faces[k] = mi.mesh.get_faces()
		var f: PackedVector3Array = faces[k]
		var x := mi.global_transform
		var i := 0
		while i + 2 < f.size():
			var hit = Geometry3D.ray_intersects_triangle(from, dir, x * f[i], x * f[i + 1], x * f[i + 2])
			i += 3
			if hit != null:
				var dd: float = (hit as Vector3).distance_to(from)
				if dd <= far and dd < best:
					best = dd
	return best


func _finalize() -> void:
	var keys := seen.keys()
	keys.sort()
	for k in keys:
		print("SIGN %s  %s" % [seen[k], k])
