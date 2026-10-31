class_name Settings
extends RefCounted
## Настройки игрока — user://lake_settings.cfg: качество графики (4 уровня), экран (окно / полный, вертикальная синхронизация),
## яркость, громкость (общая, музыка, звуки, голоса), крупные субтитры, больше времени на выбор, язык.
## Меняются сразу — в главном меню и в паузе (hud.show_settings), файл пишется при выходе из экрана настроек.

const PATH := "user://lake_settings.cfg"
const Q_RU := ["Низкое", "Среднее", "Высокое", "Ультра"]
const Q_EN := ["Low", "Medium", "High", "Ultra"]
var quality := -1                 # 0 низкое, 1 среднее, 2 высокое, 3 ультра; -1 — не выбрано: подберётся по видеокарте
var fullscreen := false
var vsync := true
var brightness := 1.0             # 0,6…1,5
var vol := {"master": 1.0, "music": 0.8, "sfx": 1.0, "voice": 1.0}
var subs_big := false
var choice_long := false          # больше времени на выбор (×1,6)
var lang := ""


func load_cfg() -> void:
	var c := ConfigFile.new()
	if c.load(PATH) != OK:
		return
	quality = int(c.get_value("video", "quality", quality))
	fullscreen = bool(c.get_value("video", "fullscreen", fullscreen))
	vsync = bool(c.get_value("video", "vsync", vsync))
	brightness = float(c.get_value("video", "brightness", brightness))
	for k in vol:
		vol[k] = float(c.get_value("audio", k, vol[k]))
	subs_big = bool(c.get_value("game", "subs_big", subs_big))
	choice_long = bool(c.get_value("game", "choice_long", choice_long))
	lang = String(c.get_value("game", "lang", lang))


func save() -> void:
	var c := ConfigFile.new()
	c.set_value("video", "quality", quality)
	c.set_value("video", "fullscreen", fullscreen)
	c.set_value("video", "vsync", vsync)
	c.set_value("video", "brightness", brightness)
	for k in vol:
		c.set_value("audio", k, vol[k])
	c.set_value("game", "subs_big", subs_big)
	c.set_value("game", "choice_long", choice_long)
	c.set_value("game", "lang", lang)
	c.save(PATH)


## Качество по видеокарте при первом запуске: встроенная графика — «Среднее», дискретная — «Высокое», без Forward+ — «Низкое».
static func auto_quality(pc: bool) -> int:
	if not pc:
		return 0
	var gpu := RenderingServer.get_video_adapter_name().to_lower()
	for weak in ["intel", "uhd", "iris", "llvmpipe", "swiftshader", "microsoft basic", "vega 3", "vega 8", "radeon(tm) graphics"]:
		if gpu.contains(weak):
			return 1
	return 2


static func quality_name(q: int, lang_code: String) -> String:
	return String((Q_RU if lang_code == "ru" else Q_EN)[clampi(q, 0, 3)])


## Экран: окно или полный экран, вертикальная синхронизация.
func apply_display() -> void:
	if DisplayServer.get_name() == "headless":
		return
	DisplayServer.window_set_mode(DisplayServer.WINDOW_MODE_FULLSCREEN if fullscreen else DisplayServer.WINDOW_MODE_WINDOWED)
	DisplayServer.window_set_vsync_mode(DisplayServer.VSYNC_ENABLED if vsync else DisplayServer.VSYNC_DISABLED)


## Качество графики: разрешение 3D (FSR), сглаживание, тени, объёмный туман, SSAO/SSIL, свечение, дальность детализации.
func apply_quality(vp: Viewport, env: Environment, pc: bool) -> void:
	var q := clampi(quality, 0, 3)
	vp.scaling_3d_mode = Viewport.SCALING_3D_MODE_FSR
	vp.scaling_3d_scale = [0.67, 0.85, 1.0, 1.0][q]
	vp.msaa_3d = [Viewport.MSAA_DISABLED, Viewport.MSAA_DISABLED, Viewport.MSAA_2X, Viewport.MSAA_4X][q]
	vp.screen_space_aa = Viewport.SCREEN_SPACE_AA_FXAA if q <= 1 else Viewport.SCREEN_SPACE_AA_DISABLED
	vp.mesh_lod_threshold = [4.0, 2.0, 1.0, 0.5][q]
	vp.positional_shadow_atlas_size = [0, 2048, 4096, 8192][q]   # «Низкое»: без теней от ламп и фар
	RenderingServer.directional_shadow_atlas_set_size([1024, 2048, 4096, 8192][q], true)
	var sq := [RenderingServer.SHADOW_QUALITY_HARD, RenderingServer.SHADOW_QUALITY_SOFT_LOW,
		RenderingServer.SHADOW_QUALITY_SOFT_MEDIUM, RenderingServer.SHADOW_QUALITY_SOFT_HIGH][q] as RenderingServer.ShadowQuality
	RenderingServer.directional_soft_shadow_filter_set_quality(sq)
	RenderingServer.positional_soft_shadow_filter_set_quality(sq)
	if env == null:
		return
	env.glow_enabled = q >= 1
	if pc:   # только у Forward+ (ПК): у упрощённого рендера этих эффектов нет
		env.volumetric_fog_enabled = q >= 2
		env.ssao_enabled = q >= 2
		env.ssil_enabled = q >= 3
		RenderingServer.environment_set_volumetric_fog_volume_size(128 if q >= 3 else 64, 96 if q >= 3 else 64)


## Яркость — поправка поверх света сцены (свет каждой сцены задаёт её «погода»).
func apply_brightness(env: Environment) -> void:
	if env == null:
		return
	env.adjustment_enabled = absf(brightness - 1.0) > 0.01
	env.adjustment_brightness = brightness
