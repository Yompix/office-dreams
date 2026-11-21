class_name PixelArt
extends RefCounted
## 2.0: белые пиксельные значки бренда Yompix Games (сердце, куб, шестерёнка, геймпад, хлопушка, плюс, звезда-искра)
## — те же рисунки, что на заставках канала (stream-kit/brand.js). Рисуются прямоугольниками: ни текстур, ни шрифтов.

const ICONS := {
	"heart": [".XX...XX.", "XXXX.XXXX", "XXXXXXXXX", "XXXXXXXXX", ".XXXXXXX.", "..XXXXX..", "...XXX...", "....X...."],
	"plus": ["..X..", "..X..", "XXXXX", "..X..", "..X.."],
	"pad": [".XXXXXXXXXXX.", "X...........X", "X.X......X..X", "XXXX....X.X.X", "X.X......X..X", "X...XXXXX...X", ".XXX.....XXX."],
	"cube": ["...XXXX...", ".XX....XX.", "X..XXXX..X", "X.X....X.X", "XX......XX", "X.XX..XX.X", "X...XX...X", ".XX.X..XX.", "...XXXX..."],
	"gear": ["...XX...", ".X.XX.X.", "..XXXX..", "XXX..XXX", "XXX..XXX", "..XXXX..", ".X.XX.X.", "...XX..."],
	"clap": ["XXXXXXXXXX", "X.XX.XX.XX", "XXXXXXXXXX", "XXXXXXXXXX", "X........X", "X........X", "XXXXXXXXXX"],
	"star": ["...X...", "...X...", "..XXX..", "XXXXXXX", "..XXX..", "...X...", "...X..."],
	"crown": ["X..X..X", "XX.X.XX", "XXXXXXX", "XXXXXXX"],
	"dot": ["X"],
}


static func size_of(name: String) -> Vector2i:
	var rows: Array = ICONS.get(name, ICONS["dot"])
	return Vector2i(String(rows[0]).length(), rows.size())


## Нарисовать значок на CanvasItem: левый верхний угол pos, сторона «пикселя» cell.
static func draw(ci: CanvasItem, name: String, pos: Vector2, cell: float, color: Color) -> void:
	var rows: Array = ICONS.get(name, ICONS["dot"])
	for y in rows.size():
		var r: String = rows[y]
		var x := 0
		while x < r.length():
			if r[x] == "X":
				var x0 := x
				while x < r.length() and r[x] == "X":
					x += 1
				ci.draw_rect(Rect2(pos + Vector2(x0, y) * cell, Vector2(x - x0, 1) * cell), color)
			else:
				x += 1
