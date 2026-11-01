class_name Fabric
extends RefCounted
## Материал одежды по ткани. MakeHuman и glTF отдают всем тканям одно и то же (шероховатость 0,7, блик 0,5) —
## кожаная куртка, шерстяное пальто и хлопковое худи блестят одинаково, как пластик. Здесь — по имени сетки:
## шероховатость (или множитель к карте шероховатости), сила блика, мягкий отсвет по краю (у ворсистых тканей —
## шерсть, трикотаж — свет «стелется» по контуру), лак (дождевик). Цвет и рисунок ткани не трогаются.

## [подстрока имени сетки, ткань] — первая подходящая
const MATCH := [
	["raincoat", "pvc"], ["puffer", "nylon"], ["parka", "poplin"], ["jacket", "leather"],
	["hoodie", "jersey"], ["hood", "jersey"], ["beanie", "knit"], ["scarf", "wool"], ["coat", "wool"],
	["sweater", "knit"], ["suit_3", "wool"], ["worksuit", "cotton"], ["robe", "cotton"], ["vest", "wool"],
	["casualsuit", "denim"], ["dress", "cotton"], ["skirt", "cotton"],
	["boots", "boot_leather"], ["shoes", "boot_leather"], ["flats", "boot_leather"], ["glasses", "frame"],
]
## rough — шероховатость (с картой шероховатости — множитель к ней), spec — блик (0,5 — пластик), rim/rim_tint — отсвет
## по краю (rim_tint 1 — цветом ткани), coat — лак, drop_rough_map — карта в слоте шероховатости на самом деле карта
## блеска (у ботинок и костюма Toigo: белое — блестит), её не используем
const KIND := {
	"leather": {"rough": 0.62, "spec": 0.45, "rim": 0.0, "rim_tint": 0.0, "coat": 0.08},
	"boot_leather": {"rough": 0.5, "spec": 0.45, "rim": 0.0, "rim_tint": 0.0, "coat": 0.12, "drop_rough_map": true},
	"pvc": {"rough": 0.32, "spec": 0.5, "rim": 0.0, "rim_tint": 0.0, "coat": 0.35},
	"nylon": {"rough": 0.45, "spec": 0.45, "rim": 0.08, "rim_tint": 0.5, "coat": 0.0},
	"poplin": {"rough": 0.68, "spec": 0.38, "rim": 0.1, "rim_tint": 0.6, "coat": 0.0},
	"jersey": {"rough": 0.97, "spec": 0.28, "rim": 0.28, "rim_tint": 0.75, "coat": 0.0},
	"knit": {"rough": 1.0, "spec": 0.22, "rim": 0.35, "rim_tint": 0.8, "coat": 0.0},
	"wool": {"rough": 1.0, "spec": 0.25, "rim": 0.32, "rim_tint": 0.8, "coat": 0.0, "drop_rough_map": true},
	"cotton": {"rough": 0.9, "spec": 0.3, "rim": 0.18, "rim_tint": 0.7, "coat": 0.0},
	"denim": {"rough": 0.88, "spec": 0.32, "rim": 0.12, "rim_tint": 0.6, "coat": 0.0},
	"frame": {"rough": 0.25, "spec": 0.55, "rim": 0.0, "rim_tint": 0.0, "coat": 0.0},
}


static func kind_of(mesh_name: String) -> String:
	var nm := mesh_name.to_lower()
	if not nm.begins_with("cloth_"):
		return ""
	if nm.contains("_zip") or nm.contains("_pull") or nm.contains("_button") or nm.contains("_patch"):
		return ""   # молния, пуговицы, нашивки — свои материалы из сборки
	for pair in MATCH:
		if nm.contains(String(pair[0])):
			return String(pair[1])
	return ""


## Настроить копию материала под ткань (вызывается для каждой поверхности одежды при создании героя).
static func tune(mm: BaseMaterial3D, mesh_name: String) -> void:
	var k := kind_of(mesh_name)
	if k == "":
		return
	var p: Dictionary = KIND[k]
	if bool(p.get("drop_rough_map", false)):
		mm.roughness_texture = null
	mm.roughness = float(p.rough)
	mm.metallic = 0.0
	mm.metallic_specular = float(p.spec)
	if float(p.rim) > 0.0:
		mm.rim_enabled = true
		mm.rim = float(p.rim)
		mm.rim_tint = float(p.rim_tint)
	if float(p.coat) > 0.0:
		mm.clearcoat_enabled = true
		mm.clearcoat = float(p.coat)
		mm.clearcoat_roughness = 0.5
