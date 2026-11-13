extends VBoxContainer
## «Мой мир из Minecraft»: поиск мира, открытого в самой игре (own_page.gd). На телефоне — единственный способ
## создать игру. Мастер «Отдельный сервер» из 1.7 убран: сервер создаётся в «Создать игру», а все его настройки —
## на одной странице «Настройки игры».

var app
var arg := {}
var _own_page: Control


func _ready() -> void:
	add_theme_constant_override("separation", 20)
	app.set_title(L.t("Мой мир") if Cfg.mobile() or UI.compact() else L.t("Мой мир из Minecraft"))
	_own_page = preload("res://scripts/screens/own_page.gd").new()
	_own_page.set("app", app)
	_own_page.set("auto", arg is Dictionary and arg.get("auto", false))
	_own_page.set("launch", arg is Dictionary and arg.get("launch", false))
	_own_page.size_flags_vertical = Control.SIZE_EXPAND_FILL
	add_child(_own_page)
