extends SceneTree
## Замер производительности по сценам: игра идёт сама (автопилот), каждый кадр пишется время кадра (реальное), время
## обработки (скрипты, анимация — Performance.TIME_PROCESS), вызовы отрисовки, примитивы, объекты в кадре; по сценам —
## среднее, 95-й процентиль, максимум и рывки (кадр дольше 2,5 медианы сцены). Память — в конце.
##   xvfb-run -a -s "-screen 0 1280x720x24" $GODOT --rendering-driver vulkan --resolution 1280x720 --path godot \
##     --script res://scripts/dev/perf_run.gd -- --autoplay --scene c2_recap --stop c3_recap --perfout файл.json [--maxsec 900]
## В облаке видеокарта программная (lavapipe): время кадра там — время программной отрисовки; честно сравнимы
## «до/после» на одной машине, вызовы отрисовки, примитивы, объекты и время обработки.
var out := "/tmp/lake_perf.json"
var max_sec := 900.0
var t0 := 0
var last := 0
var rows := {}
var order: Array = []
var dumped := false


func _init() -> void:
	var args := OS.get_cmdline_user_args()
	if "--perfout" in args:
		out = args[args.find("--perfout") + 1]
	if "--maxsec" in args:
		max_sec = float(args[args.find("--maxsec") + 1])
	change_scene_to_file.call_deferred(String(ProjectSettings.get_setting("application/run/main_scene")))
	t0 = Time.get_ticks_usec()
	last = t0


func _process(_d: float) -> bool:
	var now := Time.get_ticks_usec()
	var ft := float(now - last) / 1000.0
	last = now
	var g := current_scene
	var sid := ""
	if g != null and "_scene_id" in g:
		sid = String(g.get("_scene_id"))
	if sid != "":
		if not rows.has(sid):
			rows[sid] = {"ft": [], "pt": [], "dc": [], "prim": [], "obj": []}
			order.append(sid)
		var r: Dictionary = rows[sid]
		(r.ft as Array).append(ft)
		(r.pt as Array).append(Performance.get_monitor(Performance.TIME_PROCESS) * 1000.0)
		(r.dc as Array).append(Performance.get_monitor(Performance.RENDER_TOTAL_DRAW_CALLS_IN_FRAME))
		(r.prim as Array).append(Performance.get_monitor(Performance.RENDER_TOTAL_PRIMITIVES_IN_FRAME))
		(r.obj as Array).append(Performance.get_monitor(Performance.RENDER_TOTAL_OBJECTS_IN_FRAME))
	if float(now - t0) / 1e6 > max_sec:
		_dump()
		return true
	return false


func _finalize() -> void:
	_dump()


func _stat(a: Array) -> Dictionary:
	if a.is_empty():
		return {}
	var s := a.duplicate()
	s.sort()
	var tot := 0.0
	for x in s:
		tot += float(x)
	return {"mean": snappedf(tot / s.size(), 0.01), "p95": snappedf(float(s[int(s.size() * 0.95)]), 0.01),
		"max": snappedf(float(s[s.size() - 1]), 0.01), "med": snappedf(float(s[s.size() / 2]), 0.01)}


func _dump() -> void:
	if dumped:
		return
	dumped = true
	var res := {"scenes": [], "memory_static_mb": snappedf(Performance.get_monitor(Performance.MEMORY_STATIC) / 1048576.0, 0.1),
		"video_mem_mb": snappedf(Performance.get_monitor(Performance.RENDER_VIDEO_MEM_USED) / 1048576.0, 0.1),
		"nodes": Performance.get_monitor(Performance.OBJECT_NODE_COUNT), "seconds": snappedf(float(Time.get_ticks_usec() - t0) / 1e6, 0.1)}
	for sid in order:
		var r: Dictionary = rows[sid]
		var ft := _stat(r.ft)
		var hitch := 0
		for x in (r.ft as Array):
			if float(x) > 2.5 * float(ft.med):
				hitch += 1
		(res.scenes as Array).append({"id": sid, "frames": (r.ft as Array).size(), "frame_ms": ft, "process_ms": _stat(r.pt),
			"draw_calls": _stat(r.dc), "primitives": _stat(r.prim), "objects": _stat(r.obj), "hitches": hitch})
	var f := FileAccess.open(out, FileAccess.WRITE)
	if f:
		f.store_string(JSON.stringify(res, " "))
	print("PERF записано ", out, " сцен ", order.size())
