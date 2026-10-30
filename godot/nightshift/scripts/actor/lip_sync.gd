class_name LipSync
extends RefCounted
## Губы под голос — перенос faceV3 (chars2.js) и visemeAt (engine.js) старой игры. В слове, которое звучит сейчас
## (время слов — в timing.json), берём букву под этот момент → форма рта (визема); громкость голоса открывает челюсть.
## Нет текста (крик, плач, реплика без озвучки) — формы по громкости. Запись кадра (bake) лицо пишет без речи — губы живые.

const VOPEN := ["viseme_aa", "viseme_O", "viseme_E", "viseme_aa", "viseme_I", "viseme_U", "viseme_E"]
const VSHUT := ["viseme_PP", "viseme_FF", "viseme_SS", "viseme_DD", "viseme_nn", "viseme_kk", "viseme_TH", "viseme_CH", "viseme_RR"]
## насколько форма открывает челюсть
const JAW := {"viseme_aa": 0.26, "viseme_O": 0.2, "viseme_E": 0.16, "viseme_I": 0.1, "viseme_U": 0.1, "viseme_PP": 0.0,
	"viseme_FF": 0.03, "viseme_TH": 0.06, "viseme_DD": 0.08, "viseme_kk": 0.11, "viseme_CH": 0.07, "viseme_SS": 0.04,
	"viseme_nn": 0.06, "viseme_RR": 0.09}
## сила формы (остальные — 1)
const VW := {"viseme_SS": 0.65, "viseme_TH": 0.55, "viseme_DD": 0.5, "viseme_kk": 0.45, "viseme_nn": 0.45, "viseme_RR": 0.6,
	"viseme_I": 0.85, "viseme_E": 0.9}
const VIS_SETS := [["аяa", "viseme_aa"], ["оёo", "viseme_O"], ["уюuw", "viseme_U"], ["ыийiy", "viseme_I"], ["еэe", "viseme_E"],
	["пбмpbm", "viseme_PP"], ["фвfv", "viseme_FF"], ["сзцsz", "viseme_SS"], ["шжщчjc", "viseme_CH"], ["тдtd", "viseme_DD"],
	["нлnl", "viseme_nn"], ["кгхkgqxh", "viseme_kk"], ["рr", "viseme_RR"],
	["áàâãäå", "viseme_aa"], ["óòôõö", "viseme_O"], ["úùûü", "viseme_U"], ["íìîïіїý", "viseme_I"], ["éèêëє", "viseme_E"],
	["ñ", "viseme_nn"], ["çß", "viseme_SS"], ["ґ", "viseme_kk"]]   # испанский, португальский, немецкий, украинский
static var _vis_ch := {}
static var _letter: RegEx

var v_prev := ""
var v_cur := ""
var v_k := 0.0
var v_t := 0.0
var talk := 0.0           # открытие рта (uTalk старой игры): быстро растёт, медленнее спадает
var lag := 0.0            # у «Лили» за стеклом рот чуть опаздывает за голосом (как плохой дубляж)
var live := true          # у Жильца и подделок лицо застывшее: без бровей на ударениях и без смыкания губ после фразы
var _hist: Array = []
var _q_t := 0.0
var _spk := false
var _brow := 0.0
var _rng := RandomNumberGenerator.new()


func _init(seed_key := "") -> void:
	_rng.seed = hash(seed_key)


## Форма рта для буквы, звучащей в момент tl реплики k: "" — не знаем (рот по громкости), "sil" — пауза, рот закрыт.
static func viseme_at(k: String, tl: float) -> String:
	if _vis_ch.is_empty():
		for s in VIS_SETS:
			for c in String(s[0]):
				_vis_ch[c] = s[1]
		_letter = RegEx.create_from_string("\\p{L}")
	var tm := StoryDB.line(k)
	var w: Array = tm.get("ew", tm.get("w", []))   # ew — слова английской записи: язык субтитрами звучит английскими голосами
	if w.is_empty():
		return ""
	var i := -1
	for j in w.size():
		if float(w[j][0]) > tl:
			break
		i = j
	if i < 0:
		return "sil"
	var word := String(w[i][1]).to_lower()
	var L: Array[String] = []
	for c in word:
		if _vis_ch.has(c):
			L.append(c)
	if L.is_empty():
		return "" if _letter.search(word) else "sil"   # иероглифы, хангыль — рот по громкости, а не закрыт
	var st := float(w[i][0])
	var nx := float(w[i + 1][0]) if i + 1 < w.size() else st + 9.0
	var en := minf(nx - 0.02, st + 0.075 * L.size() + 0.1)   # слово кончилось — пауза, рот закрыт
	if tl > en:
		return "sil"
	return _vis_ch[L[mini(L.size() - 1, int(floor((tl - st) / maxf(0.05, en - st) * L.size())))]]


## Кадр губ: tk — громкость голоса (0…1) этого героя, vis — форма из viseme_at (или "" — не говорит по тексту).
## Добавляет веса к лицу W (имя формы → вес), поверх выражения из записи кадра.
func update(dt: float, tk: float, vis: String, now: float, W: Dictionary) -> void:
	if lag > 0.0:
		_hist.append([now, tk])
		while _hist.size() > 2 and _hist[1][0] < now - lag:
			_hist.pop_front()
		tk = _hist[0][1]
	var tg := clampf(tk * 1.15, 0.0, 0.9)
	talk = lerpf(talk, tg, minf(1.0, dt * (22.0 if tg > talk else 9.0)))
	var m := talk
	if vis != "":
		var v := "" if vis == "sil" else vis
		if v != v_cur:
			v_prev = v_cur
			v_cur = v
			v_k = 0.0
		v_k = minf(1.0, v_k + dt / 0.06)   # соседние звуки перетекают (коартикуляция), а не щёлкают каждую букву
		var g := clampf(0.4 + m * 0.9, 0.0, 0.85)   # даже шёпот — губы работают; громче — шире
		if v_prev != "":
			_add(W, v_prev, (1.0 - v_k) * g * float(VW.get(v_prev, 1.0)))
		if v_cur != "":
			_add(W, v_cur, v_k * g * float(VW.get(v_cur, 1.0)))
		_add(W, "jawOpen", lerpf(JAW.get(v_prev, 0.0), JAW.get(v_cur, 0.0), v_k) * (0.6 + 0.5 * minf(1.0, m * 1.5)))
	elif m > 0.02:   # нет текста (крик, плач) — по громкости
		v_t -= dt
		if v_t <= 0.0:
			v_t = 0.065 + _rng.randf() * 0.085
			v_prev = v_cur
			v_cur = VOPEN[_rng.randi() % VOPEN.size()] if m > 0.3 else VSHUT[_rng.randi() % VSHUT.size()]
			v_k = 0.0
		v_k = minf(1.0, v_k + dt / 0.055)
		var g2 := minf(1.0, m * 1.5)
		if v_prev != "":
			_add(W, v_prev, (1.0 - v_k) * g2)
		if v_cur != "":
			_add(W, v_cur, v_k * g2)
		_add(W, "jawOpen", m * 0.4)
	else:
		v_prev = ""
		v_cur = ""
	if not live:
		return
	# брови поднимаются на ударных слогах
	_brow += (clampf((tk - 0.45) * 1.5, 0.0, 0.6) - _brow) * minf(1.0, dt * 9.0)
	if _brow > 0.001:
		_add(W, "browInnerUp", _brow * 0.55)
		_add(W, "browOuterUpLeft", _brow * 0.7)
		_add(W, "browOuterUpRight", _brow * 0.7)
	# договорил фразу — на миг сжал губы (рот закрывается живым смыканием, а не «отпусканием» челюсти)
	if m > 0.05:
		_q_t = 0.0
		_spk = true
	else:
		_q_t += dt
		if _spk and _q_t > 0.12 and _q_t < 0.45:
			var p := 0.28 * sin(PI * (_q_t - 0.12) / 0.33)
			_add(W, "mouthPressLeft", p)
			_add(W, "mouthPressRight", p)
		if _q_t >= 0.45:
			_spk = false


## Сразу закрыть рот (склейка на другой кадр).
func reset() -> void:
	v_prev = ""
	v_cur = ""
	talk = 0.0
	_hist.clear()
	_spk = false
	_brow = 0.0


static func _add(W: Dictionary, k: String, v: float) -> void:
	W[k] = float(W.get(k, 0.0)) + v
