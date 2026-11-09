extends SceneTree
## Камера прогулки по кадрам: позиция камеры, героя, план, «внутри препятствия камеры?» (лог CAM).
var on := false
func _init() -> void:
	change_scene_to_file.call_deferred(String(ProjectSettings.get_setting("application/run/main_scene")))
func _process(d: float) -> bool:
	var g = current_scene
	if g == null or not ("_ex" in g):
		return false
	if g._ex == null:
		return false
	var ex = g._ex
	var c: Vector3 = g.cam.global_position
	var n: Vector3 = g.actors.noah.global_position
	var used: String = JSON.stringify(ex._used)
	var cin: bool = ex._cam_in(c) if ex.has_method("_cam_in") else false
	print("CAM f=%d shot=%s cam=(%.2f,%.2f,%.2f) noah=(%.2f,%.2f) yaw=%.0f in=%s used=%s" % [Engine.get_frames_drawn(), g._shot, c.x, c.y, c.z, n.x, n.z, rad_to_deg(ex.yaw), cin, used])
	return false
