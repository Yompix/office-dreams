class_name QR
extends RefCounted
## Небольшой QR-кодировщик на GDScript (байтовый режим, версии 1–40, уровни L/M/Q/H).
## Написан для LANTERN по спецификации ISO/IEC 18004; алгоритм по мотивам
## открытой библиотеки Nayuki «QR Code generator» (MIT). Лицензия этого файла — MIT.

const ECC_L := 0
const ECC_M := 1
const ECC_Q := 2
const ECC_H := 3
const FORMAT_BITS := [1, 0, 3, 2]
const ECC_PER_BLOCK := [
	[-1, 7, 10, 15, 20, 26, 18, 20, 24, 30, 18, 20, 24, 26, 30, 22, 24, 28, 30, 28, 28, 28, 28, 30, 30, 26, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30],
	[-1, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26, 30, 22, 22, 24, 24, 28, 28, 26, 26, 26, 26, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28],
	[-1, 13, 22, 18, 26, 18, 24, 18, 22, 20, 24, 28, 26, 24, 20, 30, 24, 28, 28, 26, 30, 28, 30, 30, 30, 30, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30],
	[-1, 17, 28, 22, 16, 22, 28, 26, 26, 24, 28, 24, 28, 22, 24, 24, 30, 28, 28, 26, 28, 30, 24, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30],
]
const NUM_BLOCKS := [
	[-1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 4, 4, 4, 4, 4, 6, 6, 6, 6, 7, 8, 8, 9, 9, 10, 12, 12, 12, 13, 14, 15, 16, 17, 18, 19, 19, 20, 21, 22, 24, 25],
	[-1, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5, 5, 8, 9, 9, 10, 10, 11, 13, 14, 16, 17, 17, 18, 20, 21, 23, 25, 26, 28, 29, 31, 33, 35, 37, 38, 40, 43, 45, 47, 49],
	[-1, 1, 1, 2, 2, 4, 4, 6, 6, 8, 8, 8, 10, 12, 16, 12, 17, 16, 18, 21, 20, 23, 23, 25, 27, 29, 34, 34, 35, 38, 40, 43, 45, 48, 51, 53, 56, 59, 62, 65, 68],
	[-1, 1, 1, 2, 4, 4, 4, 5, 6, 8, 8, 11, 11, 16, 16, 18, 16, 19, 21, 25, 25, 25, 34, 30, 32, 35, 37, 40, 42, 45, 48, 51, 54, 57, 60, 63, 66, 70, 74, 77, 81],
]

var size := 0
var version := 0
var modules: Array = []   # [y][x] bool
var _func: Array = []


## Закодировать текст (UTF-8). Возвращает QR или null, если не влезло.
static func encode(text: String, ecc := ECC_M) -> QR:
	var data := text.to_utf8_buffer()
	for ver in range(1, 41):
		var cap := _data_codewords(ver, ecc) * 8
		var need := 4 + (8 if ver < 10 else 16) + data.size() * 8
		if need <= cap:
			var q := QR.new()
			q._build(ver, ecc, data)
			return q
	return null


func get_module(x: int, y: int) -> bool:
	return x >= 0 and y >= 0 and x < size and y < size and modules[y][x]


static func _raw_modules(ver: int) -> int:
	var r := (16 * ver + 128) * ver + 64
	if ver >= 2:
		var na := ver / 7 + 2
		r -= (25 * na - 10) * na - 55
		if ver >= 7:
			r -= 36
	return r


static func _data_codewords(ver: int, ecc: int) -> int:
	return _raw_modules(ver) / 8 - ECC_PER_BLOCK[ecc][ver] * NUM_BLOCKS[ecc][ver]


func _build(ver: int, ecc: int, data: PackedByteArray) -> void:
	version = ver
	size = ver * 4 + 17
	modules = []
	_func = []
	for y in size:
		var row := []
		row.resize(size)
		row.fill(false)
		modules.append(row)
		var fr := []
		fr.resize(size)
		fr.fill(false)
		_func.append(fr)
	# --- битовый поток
	var bits: Array[int] = []
	_append(bits, 4, 4)
	_append(bits, data.size(), 8 if ver < 10 else 16)
	for b in data:
		_append(bits, b, 8)
	var cap := _data_codewords(ver, ecc) * 8
	_append(bits, 0, min(4, cap - bits.size()))
	_append(bits, 0, (8 - bits.size() % 8) % 8)
	var pad := 0xEC
	while bits.size() < cap:
		_append(bits, pad, 8)
		pad = 0x11 if pad == 0xEC else 0xEC
	var cw := PackedByteArray()
	cw.resize(bits.size() / 8)
	for i in bits.size():
		cw[i >> 3] |= bits[i] << (7 - (i & 7))
	var all := _add_ecc(cw, ver, ecc)
	# --- рисуем
	_draw_function(ecc)
	_draw_codewords(all)
	var best := 0
	var best_pen := -1
	for m in 8:
		_apply_mask(m)
		_draw_format(ecc, m)
		var p := _penalty()
		if best_pen < 0 or p < best_pen:
			best = m
			best_pen = p
		_apply_mask(m)
	_apply_mask(best)
	_draw_format(ecc, best)


static func _append(bits: Array[int], v: int, n: int) -> void:
	for i in range(n - 1, -1, -1):
		bits.append((v >> i) & 1)


func _set_f(x: int, y: int, dark: bool) -> void:
	modules[y][x] = dark
	_func[y][x] = true


func _draw_function(ecc: int) -> void:
	for i in size:
		_set_f(6, i, i % 2 == 0)
		_set_f(i, 6, i % 2 == 0)
	_finder(3, 3)
	_finder(size - 4, 3)
	_finder(3, size - 4)
	var pos := _align_pos()
	var n := pos.size()
	for i in n:
		for j in n:
			if (i == 0 and j == 0) or (i == 0 and j == n - 1) or (i == n - 1 and j == 0):
				continue
			for dy in range(-2, 3):
				for dx in range(-2, 3):
					_set_f(pos[i] + dx, pos[j] + dy, max(abs(dx), abs(dy)) != 1)
	_draw_format(ecc, 0)
	if version >= 7:
		var rem := version
		for i in 12:
			rem = (rem << 1) ^ ((rem >> 11) * 0x1F25)
		var vb := (version << 12) | rem
		for i in 18:
			var bit := ((vb >> i) & 1) != 0
			var a := size - 11 + i % 3
			var b := i / 3
			_set_f(a, b, bit)
			_set_f(b, a, bit)


func _finder(cx: int, cy: int) -> void:
	for dy in range(-4, 5):
		for dx in range(-4, 5):
			var d := maxi(absi(dx), absi(dy))
			var x := cx + dx
			var y := cy + dy
			if x >= 0 and x < size and y >= 0 and y < size:
				_set_f(x, y, d != 2 and d != 4)


func _align_pos() -> Array[int]:
	var out: Array[int] = []
	if version == 1:
		return out
	var na := version / 7 + 2
	var step := (version * 8 + na * 3 + 5) / (na * 4 - 4) * 2
	var p := size - 7
	for i in na - 1:
		out.insert(0, p)
		p -= step
	out.insert(0, 6)
	return out


func _draw_format(ecc: int, mask: int) -> void:
	var data: int = int(FORMAT_BITS[ecc]) << 3 | mask
	var rem: int = data
	for i in 10:
		rem = (rem << 1) ^ ((rem >> 9) * 0x537)
	var bits: int = (data << 10 | rem) ^ 0x5412
	for i in 6:
		_set_f(8, i, _bit(bits, i))
	_set_f(8, 7, _bit(bits, 6))
	_set_f(8, 8, _bit(bits, 7))
	_set_f(7, 8, _bit(bits, 8))
	for i in range(9, 15):
		_set_f(14 - i, 8, _bit(bits, i))
	for i in 8:
		_set_f(size - 1 - i, 8, _bit(bits, i))
	for i in range(8, 15):
		_set_f(8, size - 15 + i, _bit(bits, i))
	_set_f(8, size - 8, true)


static func _bit(v: int, i: int) -> bool:
	return ((v >> i) & 1) != 0


func _add_ecc(data: PackedByteArray, ver: int, ecc: int) -> PackedByteArray:
	var nb: int = NUM_BLOCKS[ecc][ver]
	var el: int = ECC_PER_BLOCK[ecc][ver]
	var raw := _raw_modules(ver) / 8
	var n_short := nb - raw % nb
	var short_len := raw / nb
	var div := _rs_divisor(el)
	var blocks: Array = []
	var k := 0
	for i in nb:
		var dl := short_len - el + (0 if i < n_short else 1)
		var dat := data.slice(k, k + dl)
		k += dl
		var e := _rs_rem(dat, div)
		var blk := dat.duplicate()
		if i < n_short:
			blk.append(0)
		blk.append_array(e)
		blocks.append(blk)
	var out := PackedByteArray()
	for i in (blocks[0] as PackedByteArray).size():
		for j in nb:
			if i != short_len - el or j >= n_short:
				out.append(blocks[j][i])
	return out


static func _gf_mul(x: int, y: int) -> int:
	var z := 0
	for i in range(7, -1, -1):
		z = (z << 1) ^ ((z >> 7) * 0x11D)
		z ^= ((y >> i) & 1) * x
	return z & 255


static func _rs_divisor(deg: int) -> PackedByteArray:
	var r := PackedByteArray()
	r.resize(deg)
	r[deg - 1] = 1
	var root := 1
	for i in deg:
		for j in deg:
			r[j] = _gf_mul(r[j], root)
			if j + 1 < deg:
				r[j] ^= r[j + 1]
		root = _gf_mul(root, 0x02)
	return r


static func _rs_rem(data: PackedByteArray, div: PackedByteArray) -> PackedByteArray:
	var r := PackedByteArray()
	r.resize(div.size())
	for b in data:
		var f := b ^ r[0]
		r = r.slice(1)
		r.append(0)
		for i in div.size():
			r[i] ^= _gf_mul(div[i], f)
	return r


func _draw_codewords(data: PackedByteArray) -> void:
	var i := 0
	var right := size - 1
	while right >= 1:
		if right == 6:
			right = 5
		for vert in size:
			for j in 2:
				var x := right - j
				var up := ((right + 1) & 2) == 0
				var y := size - 1 - vert if up else vert
				if not _func[y][x] and i < data.size() * 8:
					modules[y][x] = _bit(data[i >> 3], 7 - (i & 7))
					i += 1
		right -= 2


func _apply_mask(m: int) -> void:
	for y in size:
		for x in size:
			var inv := false
			match m:
				0: inv = (x + y) % 2 == 0
				1: inv = y % 2 == 0
				2: inv = x % 3 == 0
				3: inv = (x + y) % 3 == 0
				4: inv = (x / 3 + y / 2) % 2 == 0
				5: inv = x * y % 2 + x * y % 3 == 0
				6: inv = (x * y % 2 + x * y % 3) % 2 == 0
				7: inv = ((x + y) % 2 + x * y % 3) % 2 == 0
			if inv and not _func[y][x]:
				modules[y][x] = not modules[y][x]


## Упрощённый штраф: серии одного цвета + блоки 2×2 + баланс тёмных.
func _penalty() -> int:
	var p := 0
	var dark := 0
	for y in size:
		var run := 1
		for x in size:
			if modules[y][x]:
				dark += 1
			if x > 0:
				if modules[y][x] == modules[y][x - 1]:
					run += 1
					if run == 5:
						p += 3
					elif run > 5:
						p += 1
				else:
					run = 1
			if x > 0 and y > 0:
				var c: bool = modules[y][x]
				if c == modules[y][x - 1] and c == modules[y - 1][x] and c == modules[y - 1][x - 1]:
					p += 3
	for x in size:
		var run := 1
		for y in range(1, size):
			if modules[y][x] == modules[y - 1][x]:
				run += 1
				if run == 5:
					p += 3
				elif run > 5:
					p += 1
			else:
				run = 1
	var total := size * size
	var k := int(ceil(absf(dark * 20.0 - total * 10.0) / total)) - 1
	p += max(k, 0) * 10
	return p


## Картинка QR (белые поля 4 модуля).
func to_image(scale := 8, dark := Color.BLACK, light := Color.WHITE) -> Image:
	var b := 4
	var n := (size + b * 2) * scale
	var img := Image.create(n, n, false, Image.FORMAT_RGBA8)
	img.fill(light)
	for y in size:
		for x in size:
			if modules[y][x]:
				img.fill_rect(Rect2i((x + b) * scale, (y + b) * scale, scale, scale), dark)
	return img
