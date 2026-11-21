extends Control
## 2.0.1: место для экрана. Не контейнер: раскладка не сбрасывает позицию экрана посреди перехода
## (раньше MarginContainer пересобирался и экран «дёргался» на 10–40 dp). Экраны растянуты на всё место;
## минимальный размер — как у самого большого экрана, чтобы раскладка вокруг вела себя как прежде.

func _init() -> void:
	mouse_filter = Control.MOUSE_FILTER_IGNORE
	child_entered_tree.connect(func(c):
		if c is Control:
			var cc: Control = c
			cc.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
			if not cc.minimum_size_changed.is_connected(update_minimum_size):
				cc.minimum_size_changed.connect(update_minimum_size)
			update_minimum_size())
	child_exiting_tree.connect(func(_c): update_minimum_size.call_deferred())


func _get_minimum_size() -> Vector2:
	var m := Vector2.ZERO
	for c in get_children():
		if c is Control and (c as Control).visible and not c.is_queued_for_deletion() and not c.has_meta("leaving"):
			m = m.max((c as Control).get_combined_minimum_size())
	return m


## Экран встаёт на место (после анимации или если раскладка изменилась) — ровно по месту, без сдвига.
func settle(c: Control) -> void:
	if is_instance_valid(c) and c.get_parent() == self:
		c.position = Vector2.ZERO
		c.size = size
