class_name CharLook
extends RefCounted
## Вид героев — как castV3 / humMat в chars2.js: перекраска частей (tint: яркость текстуры × цвет × сила, смешанная с цветом),
## серость кожи (grey), мокрость (wet), приглушённая одежда (dim), чёрные или свои глаза (deadEyes / eyeC), цвет зубов,
## шероховатость по виду части. Волосы, брови, ресницы — вырезка по альфе с мягким краем. На ПК — рассеяние света в коже.

## один код — два шейдера: непрозрачный (кожа, одежда, зубы) и с вырезкой по альфе (волосы, брови, ресницы, глаза):
## запись ALPHA делает материал «вырезанным» целиком — у кожи от этого шла зернистость
const SHADER := """
shader_type spatial;
render_mode depth_draw_opaque%s;
uniform sampler2D albedo_tex : source_color, filter_linear_mipmap_anisotropic, repeat_enable;
uniform vec4 albedo : source_color = vec4(1.0);
uniform bool has_tex = false;
uniform sampler2D normal_tex : hint_normal, filter_linear_mipmap_anisotropic, repeat_enable;
uniform bool has_normal = false;
uniform float normal_k = 1.0;
uniform float rough = 0.86;
uniform vec3 tint : source_color = vec3(1.0);
uniform float tint_gain = 1.0;
uniform float tint_mix = 0.0;
uniform bool use_tint = false;
uniform float grey = 0.0;
uniform float wet = 0.0;
uniform float dim = 1.0;
uniform float cut = 0.0;
uniform float sss = 0.0;
uniform float spec = 0.5;
uniform float env_k = 0.31;   // доля света окружения (envMapIntensity части / 0.7 у стен): кожа и одежда 0.22, волосы 0.1, глаза 0.9
void fragment() {
	vec4 t = has_tex ? texture(albedo_tex, UV) : vec4(1.0);
	vec3 c = t.rgb * albedo.rgb;
	if (use_tint) {
		float l = dot(c, vec3(0.299, 0.587, 0.114));
		c = mix(vec3(l) * tint * tint_gain, tint, tint_mix);
	}
	c *= dim;
	float l2 = dot(c, vec3(0.299, 0.587, 0.114));
	c = mix(c, vec3(l2) * vec3(0.8, 0.85, 0.9), grey);
	c *= 1.0 - 0.22 * wet;
	ALBEDO = c;
	ROUGHNESS = mix(rough, rough * 0.45, wet);
	SPECULAR = spec;
	if (has_normal) {
		NORMAL_MAP = texture(normal_tex, UV).rgb;
		NORMAL_MAP_DEPTH = normal_k;
	}
	%s
	SSS_STRENGTH = sss;
	AO = env_k;
	AO_LIGHT_AFFECT = 0.0;
}
"""
const CUT_CODE := "ALPHA = t.a * albedo.a;\n\tALPHA_SCISSOR_THRESHOLD = cut;"
## кожа с точкой покоя каждой вершины (цвет вершины = vB × 0,2 + 0,5 — export_actors.js): тёмные глазницы (socket) и
## у Жильца — саван прямо на коже (SHROUD_FS в chars2.js): грязно-белая ткань от шеи ниже колен, складки, пятна, мокрый рваный
## подол; на голых руках и ногах — вены обрывками
const SKIN_X := """
shader_type spatial;
render_mode depth_draw_opaque;
uniform sampler2D albedo_tex : source_color, filter_linear_mipmap_anisotropic, repeat_enable;
uniform vec4 albedo : source_color = vec4(1.0);
uniform bool has_tex = false;
uniform float rough = 0.6;
uniform float grey = 0.0;
uniform float wet = 0.0;
uniform float sss = 0.0;
uniform float socket = 0.0;
uniform vec3 eye_l = vec3(0.0, -9.0, 0.0);
uniform vec3 eye_r = vec3(0.0, -9.0, 0.0);
uniform bool shroud = false;
uniform float env_k = 0.31;
varying vec3 vB;
float hh3(vec3 p) { return fract(sin(dot(p, vec3(12.9898, 78.233, 37.719))) * 43758.5453); }
float vn3(vec3 p) { vec3 i = floor(p), f = fract(p); f = f * f * (3.0 - 2.0 * f);
	return mix(mix(mix(hh3(i), hh3(i + vec3(1.0, 0.0, 0.0)), f.x), mix(hh3(i + vec3(0.0, 1.0, 0.0)), hh3(i + vec3(1.0, 1.0, 0.0)), f.x), f.y),
		mix(mix(hh3(i + vec3(0.0, 0.0, 1.0)), hh3(i + vec3(1.0, 0.0, 1.0)), f.x), mix(hh3(i + vec3(0.0, 1.0, 1.0)), hh3(i + vec3(1.0, 1.0, 1.0)), f.x), f.y), f.z); }
void vertex() { vB = (COLOR.rgb - 0.5) / 0.2; }
void fragment() {
	vec4 t = has_tex ? texture(albedo_tex, UV) : vec4(1.0);
	vec3 c = t.rgb * albedo.rgb;
	float gCloth = 0.0, gWet = 0.0;
	vec3 gClothC = vec3(0.0);
	if (shroud) {
		float body = mix(smoothstep(0.27, 0.23, abs(vB.x)), smoothstep(0.4, 0.34, abs(vB.x)), smoothstep(1.32, 1.22, vB.y));
		float hemY = 0.56 + 0.07 * (vn3(vec3(vB.x * 26.0, 0.0, vB.z * 26.0)) - 0.5) + 0.03 * (hh3(vec3(floor(vB.x * 60.0), 3.0, floor(vB.z * 60.0))) - 0.5);
		gCloth = body * smoothstep(1.98, 1.95, vB.y + 0.03 * cos(vB.x * 16.0)) * smoothstep(hemY - 0.012, hemY + 0.012, vB.y);
		float fph = atan(vB.x, vB.z + 0.3 + 1e-5) * 14.0 + vn3(vB * 6.0) * 5.0;
		float fold = sin(fph) * (0.08 + 0.14 * smoothstep(1.5, 0.6, vB.y)) * (1.0 - smoothstep(0.6, 1.6, fwidth(fph)));
		float dirt = smoothstep(0.55, 0.78, vn3(vB * 8.5)) * 0.55 + smoothstep(1.1, 0.55, vB.y) * 0.35;
		gWet = smoothstep(hemY + 0.2, hemY + 0.02, vB.y) * gCloth;
		vec3 cloth = vec3(0.36, 0.34, 0.3) * (1.0 + fold) * (1.0 - 0.4 * dirt) * mix(vec3(1.0), vec3(0.74, 0.6, 0.42), dirt * 0.85);
		cloth *= 1.0 - 0.55 * gWet;
		gClothC = mix(cloth, vec3(0.06, 0.055, 0.05), 0.4 * gWet * smoothstep(0.4, 0.7, vn3(vB * 30.0)));
		float arm = smoothstep(0.34, 0.44, abs(vB.x)) * smoothstep(1.78, 1.86, vB.y);
		float vnv = vn3(mix(vec3(vB.x * 38.0, vB.y * 9.0, vB.z * 38.0), vec3(vB.x * 9.0, vB.y * 38.0, vB.z * 38.0), arm));
		float vw = fwidth(vnv);
		float vein = (1.0 - smoothstep(0.0, 0.03 + vw, abs(vnv - 0.5))) * smoothstep(0.09, 0.03, vw) * smoothstep(0.42, 0.62, vn3(vB * 4.0 + 9.0)) * (1.0 - gCloth);
		c *= 1.0 - 0.34 * vein * vec3(1.0, 0.8, 0.7);
	}
	float s = smoothstep(0.04, 0.012, length(vB - eye_l)) + smoothstep(0.04, 0.012, length(vB - eye_r));
	c *= 1.0 - 0.9 * socket * clamp(s, 0.0, 1.0);
	float l2 = dot(c, vec3(0.299, 0.587, 0.114));
	c = mix(c, vec3(l2) * vec3(0.8, 0.85, 0.9), grey);
	c *= 1.0 - 0.22 * wet;
	c = mix(c, gClothC, gCloth);
	ALBEDO = c;
	ROUGHNESS = mix(mix(rough, rough * 0.45, wet), mix(0.92, 0.45, gWet), gCloth);
	SSS_STRENGTH = sss * (1.0 - gCloth);
	AO = env_k;
	AO_LIGHT_AFFECT = 0.0;
}
"""
## рубаха-саван Жильца (gownMat в chars2.js): складки вокруг тела, грязь пятнами, низ мокрый и тёмный, рваный подол, дыры у подола.
## UV.x — угол вокруг тела, UV.y — доля длины от ворота, цвет вершины — точка покоя (× 0,2 + 0,5) — так их кладёт export_actors.js
const GOWN := """
shader_type spatial;
render_mode cull_disabled;
uniform float wet = 0.0;
float hh3(vec3 p) { return fract(sin(dot(p, vec3(12.9898, 78.233, 37.719))) * 43758.5453); }
float vn3(vec3 p) { vec3 i = floor(p), f = fract(p); f = f * f * (3.0 - 2.0 * f);
	return mix(mix(mix(hh3(i), hh3(i + vec3(1.0, 0.0, 0.0)), f.x), mix(hh3(i + vec3(0.0, 1.0, 0.0)), hh3(i + vec3(1.0, 1.0, 0.0)), f.x), f.y),
		mix(mix(hh3(i + vec3(0.0, 0.0, 1.0)), hh3(i + vec3(1.0, 0.0, 1.0)), f.x), mix(hh3(i + vec3(0.0, 1.0, 1.0)), hh3(i + vec3(1.0, 1.0, 1.0)), f.x), f.y), f.z); }
void fragment() {
	vec3 g = (COLOR.rgb - 0.5) / 0.2;
	float vF = UV.y;
	float gA = (UV.x - 0.5) * 6.2831853;
	float gN = vn3(g * 7.0);
	if (vF > 0.93 - 0.08 * vn3(vec3(g.x * 22.0, 0.0, g.z * 22.0)) - 0.04 * hh3(vec3(floor(gA * 9.0), 1.0, 2.0))) discard;   // рваный подол
	if (vF > 0.62 && vn3(g * 13.0) > 0.8) discard;   // дыры у подола
	float fold = sin(gA * 11.0 + gN * 4.0) * (0.1 + 0.16 * vF);
	float dirt = smoothstep(0.55, 0.78, vn3(g * 8.5)) * 0.55 + vF * vF * 0.5;
	float gWet = smoothstep(0.62, 0.92, vF);
	vec3 cloth = vec3(0.44, 0.42, 0.37) * (1.0 + fold) * (1.0 - 0.4 * dirt) * mix(vec3(1.0), vec3(0.76, 0.63, 0.46), dirt * 0.8);
	cloth *= 1.0 - 0.55 * gWet;
	cloth = mix(cloth, vec3(0.05, 0.045, 0.04), 0.45 * gWet * smoothstep(0.4, 0.7, vn3(g * 30.0)));
	ALBEDO = cloth * (1.0 - 0.15 * wet);
	ROUGHNESS = mix(0.92, 0.45, gWet);
	AO = 0.71;   // рубаха — litMat (0.5 / 0.7)
	AO_LIGHT_AFFECT = 0.0;
}
"""

## перекраска и прочее по героям (ключи — как в записи кадров)
const HAIR_E := {"hair": [0x6b4a30, 2.4, 0.0], "brows": [0x4a3220, 2.0, 0.0]}
const GUS_HAIR := {"hair": [0x9c9893, 1.5, 0.5], "brows": [0x8a8680, 1.5, 0.45]}
const LILY := {"wet": 1.0, "tint": {"sweater": [0xf0c020, 3.4, 0.3], "hair": [0x3a2618, 1.7, 0.0]}}
static var LOOKS := {
	"temych": {"tint": HAIR_E},
	"mihalych": {"tint": {"polo": [0x2b3036, 2.1, 0.0], "pants": [0x1b1d22, 1.7, 0.0], "hair": [0xb8b6b0, 1.6, 0.55], "brows": [0xa8a6a0, 1.6, 0.5]}},
	"katya": LILY,
	"katyaSmile": LILY.merged({"grey": 0.45, "eyeC": 0x707070, "socket": 0.55}),
	"katyaThing": LILY.merged({"grey": 0.85, "deadEyes": 0x000000, "socket": 1.0}),
	"monster": {"grey": 1.0, "wet": 0.25, "deadEyes": 0x000000, "monster": true, "socket": 1.0, "shroud": true, "tint": {"skin": 0xa9b0a6, "hair": [0x0a0a0a, 1.0, 0.6]}},
	"ethanThing": {"grey": 0.55, "deadEyes": 0x000000, "socket": 0.8, "tint": HAIR_E},
	"cleaner": {"dim": {"worksuit": 0.74}, "tint": GUS_HAIR},
	"victim": {"grey": 0.55, "dim": {"worksuit": 0.74}, "tint": GUS_HAIR},
	"martha": {"tint": {"sweater": [0x9a1c1c, 2.4, 0.35], "pants": [0x2a2224, 1.2, 0.2], "hair": [0x8c8680, 1.5, 0.45]}},
}
const ROUGH := {"skin": 0.6, "eyes": 0.06, "brows": 0.85, "lashes": 0.85, "teeth": 0.3, "tongue": 0.4, "hair": 0.68}
static var _shader: Shader
static var _shader_cut: Shader
static var _gown: Shader
static var _skin_x: Shader


static func _col(v) -> Color:
	return Color.hex((int(v) << 8) | 0xff)


## Часть тела по имени сетки: skin, hair, brows, lashes, eyes, teeth, tongue, gown или одежда (cloth_…).
static func kind_of(n: String) -> String:
	var s := n.to_lower()
	for k in ["tongue", "teeth", "lashes", "brows", "eyes", "hair", "gown", "skin"]:
		if s.contains(k):
			return k
	return "cloth_" + s


static func apply(model: Node, key: String, pc: bool) -> void:
	if _shader == null:
		_shader = Shader.new()
		_shader.code = SHADER % ["", ""]
		_shader_cut = Shader.new()
		_shader_cut.code = SHADER % [", cull_disabled", CUT_CODE]
		_gown = Shader.new()
		_gown.code = GOWN
		_skin_x = Shader.new()
		_skin_x.code = SKIN_X
	var o: Dictionary = LOOKS.get(key, {})
	var xm := {}   # точки покоя кожи и глаза (actors/<ключ>.json)
	if FileAccess.file_exists("res://actors/%s.json" % key):
		xm = JSON.parse_string(FileAccess.get_file_as_string("res://actors/%s.json" % key))
	for mi in model.find_children("*", "MeshInstance3D", true, false):
		var m := mi as MeshInstance3D
		if m.mesh == null:
			continue
		var kind := kind_of(String(m.name))
		if kind == "gown":
			var gm := ShaderMaterial.new()
			gm.shader = _gown
			gm.set_shader_parameter("wet", float(o.get("wet", 0.0)))
			for i in m.mesh.get_surface_count():
				m.set_surface_override_material(i, gm)
			continue
		var cut_k: bool = kind in ["hair", "brows", "lashes"] or (kind == "eyes" and not o.has("deadEyes") and not o.has("eyeC"))
		for i in m.mesh.get_surface_count():
			var src := m.get_active_material(i) as BaseMaterial3D
			var sm := ShaderMaterial.new()
			sm.shader = _shader_cut if cut_k else _shader
			if kind == "skin" and xm.get("vb", false):   # кожа с точкой покоя: глазницы, у Жильца — саван на коже
				sm.shader = _skin_x
				sm.set_shader_parameter("socket", float(o.get("socket", 0.0)))
				sm.set_shader_parameter("shroud", bool(o.get("shroud", false)))
				if xm.has("uEyeL3"):
					sm.set_shader_parameter("eye_l", Vector3(xm.uEyeL3[0], xm.uEyeL3[1], xm.uEyeL3[2]))
					sm.set_shader_parameter("eye_r", Vector3(xm.uEyeR3[0], xm.uEyeR3[1], xm.uEyeR3[2]))
			if src:
				if src.albedo_texture:
					sm.set_shader_parameter("albedo_tex", src.albedo_texture)
					sm.set_shader_parameter("has_tex", true)
				sm.set_shader_parameter("albedo", src.albedo_color)
				if src.normal_enabled and src.normal_texture and (kind == "hair" or kind.begins_with("cloth")):
					sm.set_shader_parameter("normal_tex", src.normal_texture)
					sm.set_shader_parameter("has_normal", true)
					sm.set_shader_parameter("normal_k", 0.45 if kind == "hair" else 1.0)
			sm.set_shader_parameter("rough", ROUGH.get(kind, 0.86))
			var tint = null
			for k in o.get("tint", {}):
				if kind == k or kind.contains(k):
					tint = o.tint[k]
			if tint is Array:
				sm.set_shader_parameter("use_tint", true)
				sm.set_shader_parameter("tint", _col(tint[0]))
				sm.set_shader_parameter("tint_gain", float(tint[1]))
				sm.set_shader_parameter("tint_mix", float(tint[2]) if tint.size() > 2 else 0.0)
			elif tint != null:
				sm.set_shader_parameter("albedo", _col(tint))
			for k in o.get("dim", {}):
				if kind.contains(k):
					sm.set_shader_parameter("dim", float(o.dim[k]))
			if kind == "eyes":
				if o.has("deadEyes"):
					sm.set_shader_parameter("has_tex", false)
					sm.set_shader_parameter("albedo", _col(o.deadEyes))
				elif o.has("eyeC"):
					sm.set_shader_parameter("albedo", _col(o.eyeC))
				else:
					sm.set_shader_parameter("cut", 0.3)
			elif kind == "teeth":
				sm.set_shader_parameter("albedo", _col(0x8a8070 if o.get("monster", false) else 0xd8d0c2))
			elif kind in ["hair", "brows", "lashes"]:
				sm.set_shader_parameter("cut", 0.18 if kind == "hair" else 0.25)
			if kind == "skin" or kind == "hair" or kind.begins_with("cloth") or kind == "gown":
				sm.set_shader_parameter("grey", float(o.get("grey", 0.0)) if kind == "skin" else 0.0)
				sm.set_shader_parameter("wet", float(o.get("wet", 0.0)) if kind == "hair" or kind == "skin" else 0.0)
			if kind == "skin" and pc:
				sm.set_shader_parameter("sss", 0.0 if o.get("grey", 0.0) > 0.3 else 0.35)   # живая кожа просвечивает; у мёртвой — нет
			if kind in ["teeth", "tongue"]:
				sm.set_shader_parameter("spec", 0.2)
			# свет окружения по частям, как envMapIntensity в humMat (chars2.js): у Жильца кожа 0.15 — комната на нём почти не светится
			var ek: float = {"eyes": 0.9, "hair": 0.1, "teeth": 0.06, "tongue": 0.06}.get(kind, 0.15 if (kind == "skin" and o.get("monster", false)) else 0.22)
			sm.set_shader_parameter("env_k", minf(1.0, ek / Location.ENV_K))
			m.set_surface_override_material(i, sm)
		if kind in ["hair", "brows", "lashes"]:
			m.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_ON
