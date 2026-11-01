class_name Manner
extends RefCounted
## Актёрская манера героев: темп, дыхание, перенос веса, осанка, моргание, взгляд, лицо в покое.
## Значения малые — это микродвижения: заметны как «живой», а не как «качается». Углы — в градусах.
##   breath     — вдохов в секунду (спокойный взрослый ≈ 0.25); amp — глубина (1 — обычная)
##   sway       — перенос веса (1 — обычный, 0 — стоит как вкопанный); sway_t — период, с
##   head       — микродвижения головы (1 — обычные); tempo — скорость поворотов головы и взгляда (1 — обычная)
##   posture    — постоянные наклоны: spine (грудь вперёд +), neck (+ вперёд), head (+ вниз), shoulders (+ вверх)
##   blink      — пауза между морганиями [от, до], с; sacc — между мелкими движениями глаз [от, до], с
##   avert      — как часто отводит взгляд от собеседника (0…1); down — куда отводит: вниз (1) или в сторону (0)
##   face       — лицо в покое (формы ARKit); brows — брови на ударных слогах речи; smile — мелькающая улыбка (0…1)
##   listen     — смотрит на того, кто говорит рядом (если сцена не задала взгляд сама)

const P := {
	# Ной, 19: устал, растерян, сдерживается; плечи чуть вперёд, голова опущена, взгляд часто уходит вниз
	"noah": {"breath": 0.30, "amp": 1.05, "sway": 0.8, "sway_t": 7.5, "head": 0.8, "tempo": 0.95,
		"posture": {"spine": 2.5, "neck": 3.0, "head": 3.5, "shoulders": 1.5},
		"blink": [2.0, 4.6], "sacc": [0.5, 1.9], "avert": 0.38, "down": 0.75,
		"face": {"browInnerUp": 0.14, "mouthPressLeft": 0.10, "mouthPressRight": 0.10}, "brows": 0.6, "smile": 0.0, "listen": true},
	# Айрис, 15: закрыта и наблюдательна; почти неподвижна, голова чуть вниз, губы поджаты, взгляд — исподлобья и в блокнот
	"iris": {"breath": 0.27, "amp": 0.85, "sway": 0.35, "sway_t": 9.5, "head": 0.5, "tempo": 0.9,
		"posture": {"spine": 1.5, "neck": 2.0, "head": 4.0, "shoulders": 2.0},
		"blink": [2.8, 6.2], "sacc": [0.7, 2.4], "avert": 0.45, "down": 0.85,
		"face": {"browDownLeft": 0.08, "browDownRight": 0.08, "mouthPressLeft": 0.14, "mouthPressRight": 0.14}, "brows": 0.3, "smile": 0.0, "listen": true},
	# Зоя: свободнее и подвижнее всех; голова живая, улыбка мелькает — под ней тревога
	"zoe": {"breath": 0.31, "amp": 1.0, "sway": 1.25, "sway_t": 5.5, "head": 1.35, "tempo": 1.25,
		"posture": {"spine": -1.0, "neck": 0.5, "head": -1.0, "shoulders": 0.0},
		"blink": [1.8, 3.8], "sacc": [0.35, 1.3], "avert": 0.32, "down": 0.3,
		"face": {"mouthSmileLeft": 0.05, "browOuterUpLeft": 0.04}, "brows": 1.2, "smile": 0.35, "listen": true},
	# Ева: беременна, устала; дышит тяжелее, двигается бережно, спина прогнута назад
	"eva": {"breath": 0.38, "amp": 1.45, "sway": 0.45, "sway_t": 10.0, "head": 0.6, "tempo": 0.8,
		"posture": {"spine": -3.0, "neck": 1.0, "head": 1.5, "shoulders": 0.5},
		"blink": [2.3, 5.0], "sacc": [0.6, 2.2], "avert": 0.3, "down": 0.6,
		"face": {"browInnerUp": 0.10}, "brows": 0.7, "smile": 0.0, "listen": true},
	# Дэнни, ~10: зажат, плечи вверх, голова вниз; мелкие беспокойные движения
	"danny": {"breath": 0.42, "amp": 0.9, "sway": 0.6, "sway_t": 4.5, "head": 1.0, "tempo": 1.15,
		"posture": {"spine": 3.5, "neck": 4.5, "head": 6.0, "shoulders": 3.0},
		"blink": [1.8, 4.0], "sacc": [0.35, 1.4], "avert": 0.6, "down": 0.9,
		"face": {"browInnerUp": 0.16}, "brows": 0.5, "smile": 0.0, "listen": true},
	# Агнес: спокойна и экономна; лёгкая старческая сутулость, взгляд прямой и долгий
	"agnes": {"breath": 0.22, "amp": 0.8, "sway": 0.22, "sway_t": 12.0, "head": 0.35, "tempo": 0.7,
		"posture": {"spine": 4.5, "neck": -2.5, "head": 1.0, "shoulders": 0.0},
		"blink": [3.0, 6.5], "sacc": [1.0, 3.0], "avert": 0.12, "down": 0.5,
		"face": {"browDownLeft": 0.05, "browDownRight": 0.05}, "brows": 0.3, "smile": 0.0, "listen": true},
	# Хейл, ~56: собран и спокоен; почти неподвижен, редко моргает, может долго смотреть не мигая
	"hale": {"breath": 0.19, "amp": 0.6, "sway": 0.1, "sway_t": 14.0, "head": 0.2, "tempo": 0.65,
		"posture": {"spine": -1.0, "neck": -1.0, "head": 0.5, "shoulders": -0.5},
		"blink": [4.5, 9.0], "sacc": [1.5, 4.0], "avert": 0.04, "down": 0.2,
		"face": {}, "brows": 0.15, "smile": 0.0, "listen": true},
	"orderly": {"breath": 0.24, "amp": 1.1, "sway": 0.2, "sway_t": 11.0, "head": 0.25, "tempo": 0.7,
		"posture": {"spine": 1.0, "neck": 1.0, "head": 0.0, "shoulders": 0.0},
		"blink": [3.5, 7.0], "sacc": [1.2, 3.5], "avert": 0.05, "down": 0.3,
		"face": {"browDownLeft": 0.1, "browDownRight": 0.1}, "brows": 0.1, "smile": 0.0, "listen": false},
	"roman": {"breath": 0.26, "amp": 1.0, "sway": 0.5, "sway_t": 8.0, "head": 0.7, "tempo": 0.9,
		"posture": {"spine": 2.0, "neck": 1.5, "head": 1.0, "shoulders": 0.5},
		"blink": [2.4, 5.0], "sacc": [0.6, 2.0], "avert": 0.3, "down": 0.5,
		"face": {}, "brows": 0.5, "smile": 0.0, "listen": true},
}
const DEFAULT := {"breath": 0.26, "amp": 1.0, "sway": 0.5, "sway_t": 8.0, "head": 0.6, "tempo": 0.9,
	"posture": {"spine": 0.0, "neck": 0.0, "head": 0.0, "shoulders": 0.0},
	"blink": [2.5, 5.5], "sacc": [0.6, 2.2], "avert": 0.25, "down": 0.5, "face": {}, "brows": 0.5, "smile": 0.0, "listen": true}

## Состояния поверх манеры (страх, боль, «второй он», сон): множители дыхания, моргания, переноса веса и добавки осанки.
const MOOD := {
	"calm": {},
	"tense": {"breath": 1.2, "blink": 0.8, "sway": 0.8, "posture": {"spine": 1.0, "shoulders": 1.5}},
	"afraid": {"breath": 1.7, "amp": 1.3, "blink": 0.6, "sway": 0.5, "head": 1.3, "posture": {"spine": 2.5, "neck": 1.5, "head": 2.0, "shoulders": 3.5}},
	"pain": {"breath": 1.6, "amp": 1.4, "blink": 0.7, "sway": 0.3, "posture": {"spine": 3.0, "shoulders": 2.0}},
	# «Второй он»: выпрямлен, грудь открыта, почти не моргает, взгляд не отводит, не качается
	"other": {"breath": 0.7, "amp": 0.6, "blink": 3.0, "sway": 0.05, "head": 0.1, "tempo": 0.6, "avert": 0.0,
		"posture": {"spine": -4.0, "neck": -2.5, "head": -3.0, "shoulders": -1.5}},
	"sleep": {"breath": 0.7, "amp": 1.2, "sway": 0.0, "head": 0.0},
	"dead": {"breath": 0.0, "amp": 0.0, "sway": 0.0, "head": 0.0},
}


static func of(key: String) -> Dictionary:
	var base: Dictionary = P.get(key.trim_suffix("_double").trim_suffix("_young").trim_suffix("_kid"), DEFAULT)
	return base
