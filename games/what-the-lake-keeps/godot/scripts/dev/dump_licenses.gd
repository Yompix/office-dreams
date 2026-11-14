extends SceneTree
## Лицензии движка Godot (MIT) и его сторонних компонентов — текстом из самого движка (docs/LICENSES.md §3).
## Зовёт tools/licenses_txt.py; в сборку не входит (scripts/dev/*).
##   godot --headless --path godot --script res://scripts/dev/dump_licenses.gd -- ФАЙЛ


func _init() -> void:
	var s := "This game uses Godot Engine, available under the following license:\n\n" + Engine.get_license_text()
	s += "\n\nThird-party components of Godot Engine:\n"
	for c in Engine.get_copyright_info():
		for p in c.parts:
			s += "\n%s\n  Copyright: %s\n  License: %s\n" % [c.name, "; ".join(PackedStringArray(p.copyright)), p.license]
	var lic := Engine.get_license_info()
	for k in lic:
		s += "\n\n===== %s =====\n\n%s\n" % [k, lic[k]]
	var f := FileAccess.open(OS.get_cmdline_user_args()[0], FileAccess.WRITE)
	f.store_string(s)
	f.close()
	quit()
