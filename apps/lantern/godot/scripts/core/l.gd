class_name L
extends RefCounted
## 1.8: перевод интерфейса. Исходный язык — русский: ключ перевода = сама русская строка (i18n/<язык>.po).
## Простой текст в Label/Button Godot переводит сам; строки с подстановками — через L.t("… %d") % x,
## числа с существительным — L.n(число, "%d игрок|%d игрока|%d игроков").

const LANGS := [["ru", "Русский"], ["en", "English"], ["uk", "Українська"], ["es", "Español"], ["pt_BR", "Português (Brasil)"],
	["pl", "Polski"], ["fr", "Français"], ["de", "Deutsch"], ["tr", "Türkçe"], ["it", "Italiano"], ["id", "Bahasa Indonesia"]]


## Языки, для которых перевод есть в сборке (русский — исходный).
static func available() -> Array:
	return LANGS.filter(func(l): return l[0] == "ru" or ResourceLoader.exists("res://i18n/%s.po" % l[0]))


static func t(s: String) -> String:
	return TranslationServer.translate(s)


## Язык по умолчанию: язык системы, если он у нас есть; иначе английский.
static func detect() -> String:
	var loc := OS.get_locale()
	var lang := OS.get_locale_language()
	if lang == "pt":
		return "pt_BR"
	for l in available():
		if l[0] == loc or l[0] == lang:
			return l[0]
	return "en"


static func current() -> String:
	var c := TranslationServer.get_locale()
	for l in LANGS:
		if l[0] == c:
			return c
	var lang := c.get_slice("_", 0)
	return "pt_BR" if lang == "pt" else lang


static func apply(lang: String) -> void:
	TranslationServer.set_locale(lang)


## Номер формы множественного числа для языка.
static func plural_index(lang: String, n: int) -> int:
	n = absi(n)
	match lang.get_slice("_", 0):
		"ru", "uk":
			if n % 10 == 1 and n % 100 != 11:
				return 0
			if n % 10 >= 2 and n % 10 <= 4 and (n % 100 < 10 or n % 100 >= 20):
				return 1
			return 2
		"pl":
			if n == 1:
				return 0
			if n % 10 >= 2 and n % 10 <= 4 and (n % 100 < 10 or n % 100 >= 20):
				return 1
			return 2
		"fr", "pt":
			return 0 if n <= 1 else 1
		"tr", "id", "ja", "ko", "zh":
			return 0
	return 0 if n == 1 else 1


## «3 игрока»: forms — русские формы через «|» (один|два-четыре|много); в переводе — столько форм, сколько нужно языку.
static func n(count: int, forms: String) -> String:
	var tr_forms := t(forms).split("|")
	var i := mini(plural_index(current(), count), tr_forms.size() - 1)
	var f := tr_forms[i]
	return f % count if f.contains("%d") else f
