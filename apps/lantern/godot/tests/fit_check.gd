extends RefCounted
## 1.8.2: «всё ли помещается» — по НАСТОЯЩИМ прямоугольникам на экране, а не только по минимальным размерам.
## Владелец (1.8.1, телефон 1080×2400, украинский): экран «Завдання» оказался шире телефона — вкладка и кнопки обрезаны
## справа, нижнее меню уехало влево. Старая проверка смотрела только минимальную ширину содержимого и только на ПК-окне
## в телефонном виде, да ещё и по-русски. Теперь для каждого видимого элемента:
##   EDGE    — элемент вылезает за край окна или за край родителя, который его обрезает (clip_contents);
##             внутри рядов, листающихся вбок (ScrollContainer с горизонтальной прокруткой), — не ошибка;
##   ROOT    — оболочка (корень приложения) шире/уже окна: так «уезжает» нижнее меню;
##   CUT     — подпись обрезана многоточием (Label/Button с clip_text): в кнопках, вкладках и заголовке — ошибка;
##   LANG    — кириллица в английском/испанском/…; в украинском — буквы, которых в нём нет (ы, э, ъ, ё);
##   TOUCH   — (телефон) кнопка меньше 44×44 dp — предупреждение;
##   NAV     — подсветка нижнего меню не под кнопкой раздела.
## Пользовательские данные (ники, названия миров ботов, сообщения чата) не считаются непереведёнными.

const USER_CONTENT := ["Алекс", "Маша_Кубик", "Кирилл", "Нюта", "Ночной_Лис", "Выживание с нуля", "Новоград", "Мини-игры у Нюты",
	"выживание", "творчество", "мини-игры", "новоград", "Наш мир", "Привет! Заходи", "Жми «Присоединиться»", "Всем привет", "Уже иду",
	"Тест Yompix", "Мир %d", "Маша"]
const RU_ONLY := "ыэъёЫЭЪЁ"
const TEXT_CLASSES := ["Label", "Button", "LineEdit", "OptionButton", "CheckBox", "CheckButton", "LinkButton", "MenuButton"]

var issues: Array = []      # {kind, where, what}
var warnings: Array = []
var lang := "ru"
var phone := false


## Текст, который ВИДНО: Label/Button переводят свой text сами (ключ — русская строка), поэтому — atr().
func _txt(c: Control) -> String:
	if c is Label:
		return c.atr((c as Label).text)
	if c is Button:
		return c.atr((c as Button).text)
	if c is LineEdit:
		return c.atr((c as LineEdit).placeholder_text)
	if c is RichTextLabel:
		return (c as RichTextLabel).get_parsed_text()
	return ""


func _name(c: Control) -> String:
	var t := _txt(c).replace("\n", " ").substr(0, 48)
	var p := c.get_parent()
	var chain := c.get_class()
	for i in 3:
		if p == null or not (p is Control):
			break
		chain = p.get_class() + "/" + chain
		p = p.get_parent()
	return "%s «%s»" % [chain, t] if t != "" else chain


func _user_content(t: String) -> bool:
	for u in USER_CONTENT:
		if t.contains(u):
			return true
	return false


## Проверить всё, что сейчас на экране (оболочка, экран, листы). Возвращает число новых ошибок.
func check(app: Control, where: String, nav_pill: Control = null, nav: Dictionary = {}, title: Label = null) -> int:
	var before := issues.size()
	var vp := app.get_viewport().get_visible_rect()
	# ROOT: каждый корневой элемент оболочки ровно по окну
	for c in app.get_children():
		if c is Control and (c as Control).visible and (c as Control).get_anchor(SIDE_RIGHT) == 1.0 and (c as Control).get_anchor(SIDE_LEFT) == 0.0:
			var r := (c as Control).get_global_rect()
			if r.size.x > vp.size.x + 1.5 or r.position.x < -1.5:
				_add("ROOT", where, "%s шире окна: %d > %d dp" % [c.get_class(), int(r.size.x), int(vp.size.x)], c)
	for c in app.get_children():
		if c is Control and c.name != "Autotest":
			_walk(c, Rect2(vp.position.x, -1e6, vp.size.x, 2e6), false, where)
	# NAV: подсветка под кнопкой раздела
	if nav_pill and is_instance_valid(nav_pill) and nav_pill.is_visible_in_tree():
		var pc := nav_pill.get_global_rect().get_center()
		var ok := false
		for k in nav:
			var b: Control = nav[k]
			if b.is_visible_in_tree() and b.get_global_rect().grow(2).has_point(pc):
				ok = true
		if not ok or nav_pill.get_global_rect().end.x > vp.end.x + 1:
			_add("NAV", where, "подсветка меню не под кнопкой (x=%d)" % int(pc.x), nav_pill)
	# заголовок верхней панели целиком
	if title and is_instance_valid(title) and title.is_visible_in_tree() and title.text != "":
		if _cut(title) > 0.5:
			_add("CUT", where, "заголовок обрезан: «%s»" % title.text, title)
	return issues.size() - before


## Отдельный элемент поверх всего (плашка связи на CanvasLayer): не за краем окна, текст виден целиком, переведён.
func check_extra(c: Control, where: String) -> int:
	var before := issues.size()
	_walk(c, Rect2(c.get_viewport().get_visible_rect().position.x, -1e6, c.get_viewport().get_visible_rect().size.x, 2e6), false, where + "/плашка")
	return issues.size() - before


func _add(kind: String, where: String, what: String, _n: Node, warn := false) -> void:
	var it := {"kind": kind, "where": where, "what": what, "lang": lang}
	if warn:
		warnings.append(it)
	else:
		issues.append(it)


## Насколько подпись не помещается (dp), 0 — помещается.
func _cut(c: Control) -> float:
	if c is Label:
		var l := c as Label
		if l.autowrap_mode != TextServer.AUTOWRAP_OFF or l.text == "" or (not l.clip_text and l.text_overrun_behavior == TextServer.OVERRUN_NO_TRIMMING):
			return 0.0
		var font := l.get_theme_font("font")
		var fs := l.get_theme_font_size("font_size")
		var w := 0.0
		for line in _txt(l).split("\n"):
			w = maxf(w, font.get_string_size(line, HORIZONTAL_ALIGNMENT_LEFT, -1, fs).x)
		var sb := l.get_theme_stylebox("normal")
		var avail := l.size.x - (sb.get_minimum_size().x if sb else 0.0)
		return maxf(0.0, w - avail - 1.0)
	if c is Button:
		var b := c as Button
		if not b.clip_text or b.text == "":
			return 0.0
		var font := b.get_theme_font("font")
		var fs := b.get_theme_font_size("font_size")
		var w := font.get_string_size(_txt(b), HORIZONTAL_ALIGNMENT_LEFT, -1, fs).x
		var sb := b.get_theme_stylebox("normal")
		var extra := sb.get_minimum_size().x if sb else 0.0
		if b.icon:
			extra += b.get_theme_constant("h_separation") + minf(b.icon.get_width(), b.get_theme_constant("icon_max_width") if b.get_theme_constant("icon_max_width") > 0 else 1e6)
		return maxf(0.0, w + extra - b.size.x - 1.0)
	return 0.0


func _walk(n: Node, clip: Rect2, hscroll: bool, where: String) -> void:
	if not (n is Control) or not (n as Control).visible:
		return
	var c := n as Control
	if c.has_meta("fit_ignore") or c.modulate.a <= 0.01:
		return
	var r := c.get_global_rect()
	var cls := c.get_class()
	var checked := c is Container or cls in TEXT_CLASSES or c is RichTextLabel or c is ProgressBar or c is TextureRect
	if checked and not hscroll and r.size.x > 0.5 and r.size.y > 0.5 and r.end.y > clip.position.y and r.position.y < clip.end.y:
		if r.position.x < clip.position.x - 1.5 or r.end.x > clip.end.x + 1.5:
			_add("EDGE", where, "%s вылезает за край: x %d…%d, видно %d…%d%s" % [_name(c), int(r.position.x), int(r.end.x), int(clip.position.x), int(clip.end.x), _culprit(c, clip)], c)
			return     # самый внешний виновник; внутрь не идём, чтобы не было лавины
	# CUT
	var cut := _cut(c)
	if cut > 0.5 and not _user_content(_txt(c)):
		var strict := c is Button
		_add("CUT", where, "%s обрезано на %d dp" % [_name(c), int(cut)], c, not strict)
	# LANG
	var t := _txt(c)
	if t != "" and not _user_content(t) and not c.has_meta("user_text"):
		if lang != "ru" and lang != "uk" and RegEx.create_from_string("[А-Яа-яЁёІіЇїЄєҐґ]").search(t):
			_add("LANG", where, "не переведено: %s" % _name(c), c)
		elif lang == "uk":
			for ch in RU_ONLY:
				if t.contains(ch):
					_add("LANG", where, "по-русски в украинском: %s" % _name(c), c)
					break
	# TOUCH (телефон): маленькие кнопки
	if phone and c is BaseButton and not (c as BaseButton).disabled and c.mouse_filter != Control.MOUSE_FILTER_IGNORE and r.size.x > 1:
		if minf(r.size.x, r.size.y) < 43.5 and not c.has_meta("touch_ok"):
			_add("TOUCH", where, "%s %dx%d dp" % [_name(c), int(r.size.x), int(r.size.y)], c, true)
	var nclip := clip
	var nh := hscroll
	if c is ScrollContainer:
		if (c as ScrollContainer).horizontal_scroll_mode != ScrollContainer.SCROLL_MODE_DISABLED:
			nh = true
		nclip = Rect2(maxf(clip.position.x, r.position.x), maxf(clip.position.y, r.position.y), 0, 0)
		nclip.end = Vector2(minf(clip.end.x, r.end.x), minf(clip.end.y, r.end.y))
	elif c.clip_contents:
		nclip = Rect2(maxf(clip.position.x, r.position.x), clip.position.y, 0, clip.size.y)
		nclip.end.x = minf(clip.end.x, r.end.x)
	for ch in c.get_children():
		_walk(ch, nclip, nh, where)


## Подсказка: самая глубокая ветка, которая уходит дальше всех вправо (что распирает).
func _culprit(c: Control, clip: Rect2) -> String:
	var out := ""
	var cur := c
	for i in 14:
		var best: Control = null
		for ch in cur.get_children():
			if ch is Control and (ch as Control).visible and (ch as Control).get_global_rect().end.x > clip.end.x + 1.5:
				if best == null or (ch as Control).get_combined_minimum_size().x > best.get_combined_minimum_size().x:
					best = ch
		if best == null:
			break
		cur = best
	if cur != c:
		out = " ← %s (мин. ширина %d)" % [_name(cur), int(cur.get_combined_minimum_size().x)]
	# из чего складывается минимальная ширина: дети самого широкого по пути вниз
	var parts := PackedStringArray()
	var n := c
	for i in 9:
		var kids := PackedStringArray()
		var widest: Control = null
		for ch in n.get_children():
			if ch is Control and (ch as Control).visible:
				var w := int((ch as Control).get_combined_minimum_size().x)
				kids.append("%s%s=%d" % [ch.get_class(), ("«%s»" % _txt(ch).substr(0, 14)) if _txt(ch) != "" else "", w])
				if widest == null or w > widest.get_combined_minimum_size().x:
					widest = ch
		if kids.is_empty():
			break
		parts.append("[" + " ".join(kids) + "]")
		n = widest
	return out + " " + " > ".join(parts)


func report_lines() -> PackedStringArray:
	var out := PackedStringArray()
	var seen := {}
	for it in issues:
		var k: String = "%s %s: %s" % [it.kind, it.lang, it.what]
		if seen.has(k):
			seen[k].append(it.where)
			continue
		seen[k] = [it.where]
	for k in seen:
		out.append("%s  [%s]" % [k, ", ".join(seen[k].slice(0, 6))])
	return out
