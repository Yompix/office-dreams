extends SceneTree
## Время создания героя (загрузка модели, переодевание скелета, материалы): первый раз и повторно.
var _f := 0
func _process(_d: float) -> bool:
	_f += 1
	if _f < 2:
		return false
	for k in ["noah", "iris", "zoe", "eva", "danny", "agnes", "hale", "orderly", "roman"]:
		var t0 := Time.get_ticks_usec()
		var a := Actor.make(k, k)
		var t1 := Time.get_ticks_usec()
		get_root().add_child(a)
		var b := Actor.make(k, k)
		var t2 := Time.get_ticks_usec()
		print("%-8s первый %6.1f мс, повторно %6.1f мс" % [k, (t1 - t0) / 1000.0, (t2 - t1) / 1000.0])
		a.free()
		b.free()
	quit()
	return true
