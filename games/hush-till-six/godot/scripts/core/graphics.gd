class_name Graphics
extends RefCounted
## Качество графики: высокая / средняя / низкая. ПК (Forward+): объёмный туман, тени, SSAO, свечение ламп; масштаб 3D-кадра.
## Сайт (Compatibility): без объёмного тумана и SSAO, масштаб кадра ниже на слабых.

static func level() -> String:
	return String(Save.setting("gfx", "high"))


static func pc() -> bool:
	return RenderingServer.get_current_rendering_method() == "forward_plus"


static func apply(vp: Viewport) -> void:
	var lv := level()
	vp.scaling_3d_scale = {"high": 1.0, "mid": 0.85, "low": 0.67}.get(lv, 1.0)
	vp.scaling_3d_mode = Viewport.SCALING_3D_MODE_FSR2 if pc() and lv != "high" else Viewport.SCALING_3D_MODE_BILINEAR


## Эффекты окружения места по качеству (Location вызывает при сборке, смена качества — сразу для текущего места).
static func env(e: Environment) -> void:
	var lv := level()
	var p := pc()
	e.ssao_enabled = p and lv == "high"
	e.volumetric_fog_enabled = p and lv != "low" and e.fog_enabled
	e.glow_enabled = lv != "low"
