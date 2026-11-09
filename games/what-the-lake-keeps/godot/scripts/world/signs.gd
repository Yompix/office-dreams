class_name Signs
extends RefCounted
## Надписи на предметах: вывески, щиты, объявления, этикетки. Владелец, 4 ноя: «названия выступают, и шрифт текста на
## предметах недоделан». Было: Label3D со шрифтом по умолчанию, в 5–60 мм перед доской (вывеска кафе — в 3 см, «МАГАЗИН» —
## в 6 см: сбоку видно, что буквы висят в воздухе), текст шире щита, без мипмапов — вдали рябил и «крошился».
## Теперь: свой шрифт под вид надписи (MSDF: края чёткие на любом расстоянии, с мипмапами), размер — по доске, текст
## прижат к поверхности за ним (1,5 мм), непрозрачная часть пишет глубину (не путается с туманом и стёклами).
## Проверка: scripts/dev/sign_audit.gd.

const FONTS := {
	"cafe": "res://assets/fonts/Lobster-Regular.ttf",       # вывеска придорожного кафе — рукописная, как у таких кафе
	"shop": "res://assets/fonts/Oswald-SemiBold.ttf",        # магазин, заправка: узкие прописные
	"label": "res://assets/fonts/Oswald-Medium.ttf",         # автомат, таблички
	"road": "res://assets/fonts/PTSansNarrow-Bold.ttf",      # дорожные щиты и указатели (узкий, как дорожный шрифт)
	"notice": "res://assets/fonts/PTSans-Regular.ttf",       # объявления на столбах, листовки, этикетки
	"notice_b": "res://assets/fonts/PTSans-Bold.ttf",
	"hand": "res://assets/fonts/Caveat-SemiBold.ttf",       # написано от руки маркером: «ПРОДАМ ДРОВА», «ПРОПАЛА СОБАКА»
}
static var _fonts := {}
static var _faces := {}


static func font(kind: String) -> Font:
	if not _fonts.has(kind):
		var path := String(FONTS.get(kind, FONTS.notice))
		var f: FontFile = load(path) if ResourceLoader.exists(path) else null
		if f and not f.multichannel_signed_distance_field:   # настройки импорта (.import) в git не идут — MSDF и мипмапы здесь
			f.multichannel_signed_distance_field = true
			f.generate_mipmaps = true
		_fonts[kind] = f
	return _fonts[kind]


## Шрифт и отрисовка надписи этого вида. Возвращает её же (для цепочки).
static func style(lb: Label3D, kind: String) -> Label3D:
	var f := font(kind)
	if f:
		lb.font = f
	lb.alpha_cut = Label3D.ALPHA_CUT_OPAQUE_PREPASS
	lb.texture_filter = BaseMaterial3D.TEXTURE_FILTER_LINEAR_WITH_MIPMAPS_ANISOTROPIC
	return lb


## Размер букв — чтобы текст занял не больше w × h метров (доля fill от доски, по умолчанию 86 %).
static func fit(lb: Label3D, w: float, h: float, fill := 0.86) -> Label3D:
	var f: Font = lb.font if lb.font else ThemeDB.fallback_font
	var sz := f.get_multiline_string_size(lb.text, HORIZONTAL_ALIGNMENT_CENTER, -1, lb.font_size)
	if sz.x > 0.0 and sz.y > 0.0:
		lb.pixel_size = minf(w * fill / sz.x, h * fill / sz.y)
	return lb


## Прижать надпись к поверхности за ней (доска, стена, бок щита): зазор gap. Надпись и то, на чём она, должны быть в
## дереве сцены — поэтому зовётся отложенно (Signs.flush.call_deferred(lb)). Ничего за ней в 15 см нет (надпись на
## стекле, висит) или больше половины точек мимо — не трогаем.
static func flush(lb: Label3D, gap := 0.0015) -> void:
	if not is_instance_valid(lb) or not lb.is_inside_tree():
		return
	var root: Node = lb.get_tree().current_scene
	if root == null:
		return
	var xf := lb.global_transform
	var box := lb.get_aabb()
	var nrm := xf.basis.z.normalized()
	var best := INF
	var hits := 0
	for u in [0.5, 0.2, 0.8]:
		for v in [0.5, 0.25, 0.75]:
			var p: Vector3 = xf * (box.position + Vector3(box.size.x * u, box.size.y * v, 0.0))
			var d := _ray(root, p, -nrm, 0.15)
			if d < INF:
				hits += 1
				best = minf(best, d)
	if hits >= 5 and best > gap:
		lb.global_position -= nrm * (best - gap)


static func _ray(root: Node, from: Vector3, dir: Vector3, far: float) -> float:
	var best := INF
	var seg := AABB(from, Vector3.ZERO).expand(from + dir * far).grow(0.01)
	for n in root.find_children("*", "MeshInstance3D", true, false):
		var mi := n as MeshInstance3D
		if mi == null or mi.mesh == null or not mi.is_visible_in_tree() or Seats._in_actor(mi):
			continue
		if not (mi.global_transform * mi.get_aabb()).intersects(seg):
			continue
		var k := mi.mesh.get_instance_id()
		if not _faces.has(k):
			_faces[k] = mi.mesh.get_faces()
		var f: PackedVector3Array = _faces[k]
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
