extends Container
## 1.8.2: телефон — экран всегда ровно по ширине окна. Минимальная ширина содержимого наверх НЕ передаётся
## (раньше широкая строка на «Завданнях» растягивала всю оболочку: вкладки и кнопки уезжали за край,
## нижнее меню сдвигалось влево). Если что-то всё-таки шире — оно обрезается, а не двигает экран;
## tests/fit.sh ловит такие места как ошибку.


func _get_minimum_size() -> Vector2:
	var h := 0.0
	for c in get_children():
		if c is Control and (c as Control).visible:
			h = maxf(h, (c as Control).get_combined_minimum_size().y)
	return Vector2(0, h)


func _notification(what: int) -> void:
	if what == NOTIFICATION_SORT_CHILDREN:
		for c in get_children():
			if c is Control:
				fit_child_in_rect(c, Rect2(Vector2.ZERO, size))
