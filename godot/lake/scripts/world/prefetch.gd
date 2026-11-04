class_name Prefetch
extends RefCounted
## Плавная игра (владелец, 4 ноя: «видно, как по ходу прогружаются главы»): модели мест и героев главы грузятся
## в фоне заранее — следующая глава, пока идёт текущая. Раньше кафе, деревня, озеро, буханка и новые герои
## (на телефоне — все) грузились прямо посреди сцены: кадр замирал на 0,3–2 с. На телефоне заранее — только места.
## Место берёт модель через Prefetch.scene(путь) — готовую из фона или (если фон не успел) обычной загрузкой.

const CHAPTERS := {
	1: {"world": ["station", "sedan", "deer"], "chars": ["noah", "iris", "danny", "roman"]},
	2: {"world": ["cafe", "truck"], "chars": ["zoe", "eva"]},
	3: {"world": ["village"], "chars": ["agnes"]},
	4: {"world": ["van", "lake"], "chars": ["hale", "orderly"]},
	5: {"world": ["lake"], "chars": ["noah_kid", "anna", "victor", "hale_young", "nina"]},
}
static var _asked := {}


## Модели мест и героев главы n — в фоновую загрузку (повторный вызов ничего не делает).
static func chapter(n: int) -> void:
	if _asked.has(n) or not CHAPTERS.has(n):
		return
	_asked[n] = true
	for m in CHAPTERS[n].world:
		request("res://assets/models/%s.glb" % m)
	if not OS.has_feature("mobile"):   # на телефоне герои (по 20–40 МБ видеопамяти) — когда нужны: памяти меньше
		Actor.prefetch(CHAPTERS[n].chars)


static func request(path: String) -> void:
	if not ResourceLoader.exists(path) or ResourceLoader.has_cached(path):
		return
	if ResourceLoader.load_threaded_get_status(path) == ResourceLoader.THREAD_LOAD_INVALID_RESOURCE:
		ResourceLoader.load_threaded_request(path, "", true)


## Модель места: из фона (если запрашивали — дождётся конца загрузки), иначе — обычной загрузкой.
static func scene(path: String) -> PackedScene:
	if ResourceLoader.load_threaded_get_status(path) != ResourceLoader.THREAD_LOAD_INVALID_RESOURCE:
		var r := ResourceLoader.load_threaded_get(path)
		if r is PackedScene:
			return r
	return load(path) as PackedScene
