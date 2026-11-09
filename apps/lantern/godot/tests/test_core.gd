extends SceneTree
## Быстрые проверки без сети: QR, код комнаты, приглашения, server.properties.
## godot --headless --path . -s tests/test_core.gd -- --out=/tmp/x

func _init() -> void:
	var out := "/tmp/claude-0/qrtest"
	for a in OS.get_cmdline_user_args():
		if a.begins_with("--out="):
			out = a.substr(6)
	DirAccess.make_dir_recursive_absolute(out)
	var fails := 0
	var texts := ["HELLO", "minecraft://?addExternalServer=Мир Yompix|abc-def.gl.joinmc.link:12345",
		"igraem://join?v=1&n=%D0%9C%D0%B8%D1%80&j=147.185.221.20:41234", "x".repeat(300)]
	for i in texts.size():
		for ecc in [QR.ECC_L, QR.ECC_M, QR.ECC_Q, QR.ECC_H]:
			var q := QR.encode(texts[i], ecc)
			q.to_image(4).save_png("%s/qr_%d_%d.png" % [out, i, ecc])
			var f := FileAccess.open("%s/qr_%d_%d.txt" % [out, i, ecc], FileAccess.WRITE)
			f.store_string(texts[i])
	# код комнаты
	for c in [["147.185.221.20", 41234, "java"], ["1.2.3.4", 19132, "bedrock"], ["255.255.255.255", 65535, "bedrock"], ["0.0.0.1", 1, "java"]]:
		var code := RoomCode.encode(c[0], c[1], c[2])
		var d := RoomCode.decode(code.to_lower())
		var ok: bool = d.ok and d.host == c[0] and d.port == c[1] and d.edition == c[2]
		print("code %s -> %s %s" % [str(c), code, "OK" if ok else "FAIL " + str(d)])
		if not ok: fails += 1
		var bad := code.substr(0, 3) + ("Z" if code[3] != "Z" else "Y") + code.substr(4)
		if RoomCode.decode(bad).ok: print("parity miss (acceptable 1/2)")
	for t in ["play.example.com", "play.example.com:25570", "[::1]:19132", "minecraft://?addExternalServer=Мир|a.b.c:1234", "igraem://join?v=1&n=%D0%9C%D0%B8%D1%80&j=1.2.3.4:5&b=1.2.3.4:6", RoomCode.encode("10.0.0.1", 19132, "bedrock")]:
		print("parse %s -> %s" % [t, str(Invite.parse(t, "java"))])
	ServerHost.write_properties(out + "/server.properties", {"motd": "Мир Yompix", "max-players": "5"})
	ServerHost.write_properties(out + "/server.properties", {"max-players": "7", "online-mode": "false"})
	print(FileAccess.get_file_as_string(out + "/server.properties"))
	print("FAILS=%d" % fails)
	quit(fails)
