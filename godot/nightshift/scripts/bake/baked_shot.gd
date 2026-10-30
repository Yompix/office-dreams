class_name BakedShot
extends RefCounted
## Запись кадра старой игры (tools/godot/bake_shots.js): каналы со значениями по кадрам (30 к/с).
## Первые main_frames кадров — сам кадр (время 0…dur), дальше — «ожидание»: время кадра стоит, герои живут (дышат, смотрят).

var id := ""
var loc := ""
var fps := 30.0
var dur := 0.0
var frames := 0
var main_frames := 0
var chans := {}                      # имя канала → [смещение, ширина]
var consts := {}                     # имя → PackedFloat32Array (не меняется весь кадр)
var data := PackedFloat32Array()


static func load_shot(shot_id: String) -> BakedShot:
	var j: Dictionary = JSON.parse_string(FileAccess.get_file_as_string("res://bake/%s.json" % shot_id))
	var s := BakedShot.new()
	s.id = shot_id
	s.loc = j.loc
	s.fps = float(j.fps)
	s.dur = float(j.dur)
	s.frames = int(j.frames)
	s.main_frames = int(round(s.dur * s.fps)) + 1
	var off := 0
	for c in j.chans:
		s.chans[String(c[0])] = [off, int(c[1])]
		off += int(c[1]) * s.frames
	for k in j.get("const", {}):
		s.consts[k] = PackedFloat32Array(j.const[k])
	var b := FileAccess.get_file_as_bytes("res://bake/%s.bin" % shot_id)
	s.data = b.to_float32_array()
	return s


## Дробный номер кадра записи для времени кадра t; wait > 0 — сколько секунд кадр ждёт выбора игрока.
func frame_at(t: float, wait := 0.0) -> float:
	if wait > 0.0 and frames > main_frames + 1:
		return main_frames + fposmod(wait * fps, float(frames - main_frames - 1))
	return clampf(t * fps, 0.0, float(main_frames - 1))


func has(ch: String) -> bool:
	return chans.has(ch) or consts.has(ch)


## Значение канала в кадре f (между кадрами — линейно; кватернион с позиции q0 — по кратчайшему пути).
## Пусто — канала в этом кадре нет (герой не в кадре).
func value(ch: String, f: float, q0 := -1) -> PackedFloat32Array:
	if consts.has(ch):
		return consts[ch]
	if not chans.has(ch):
		return PackedFloat32Array()
	var o: int = chans[ch][0]
	var n: int = chans[ch][1]
	var i0 := clampi(int(floor(f)), 0, frames - 1)
	var i1 := mini(i0 + 1, frames - 1)
	var u := f - i0
	var out := PackedFloat32Array()
	out.resize(n)
	var a := o + i0 * n
	var b := o + i1 * n
	if is_nan(data[a]):
		return PackedFloat32Array()
	if is_nan(data[b]):
		b = a
	var sgn := 1.0
	if q0 >= 0:
		var d := 0.0
		for k in 4:
			d += data[a + q0 + k] * data[b + q0 + k]
		if d < 0.0:
			sgn = -1.0
	for k in n:
		var vb := data[b + k]
		if q0 >= 0 and k >= q0 and k < q0 + 4:
			vb *= sgn
		out[k] = lerpf(data[a + k], vb, u)
	return out


## Список героев, которые есть в записи (по каналу корня).
func actor_keys() -> PackedStringArray:
	var out := PackedStringArray()
	for c in chans.keys() + consts.keys():
		var s := String(c)
		if s.begins_with("a:") and s.ends_with(":root"):
			out.append(s.split(":")[1])
	return out
