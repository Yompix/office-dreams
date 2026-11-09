class_name DeepLink
extends RefCounted
## Ссылки lantern:// — открыть приложение сразу на нужном экране.
##   lantern://join?v=1&c=K7M2QX&e=both&n=<мир>   — зайти к другу (то же, что QR/приглашение, см. Invite)
##   lantern://friend?v=1&tag=Алекс%234821         — добавить в друзья
## Android: ссылка приходит в Intent (манифест — addons/lantern_android). ПК: аргументом командной строки.


## Ссылка, с которой запустили (или вернули на передний план) приложение, или "".
static func from_launch() -> String:
	if OS.get_name() == "Android":
		if not Engine.has_singleton("AndroidRuntime"):
			return ""
		var rt: Object = Engine.get_singleton("AndroidRuntime")
		var act: Variant = rt.call("getActivity")
		if act == null:
			return ""
		var intent: Variant = act.call("getIntent")
		if intent == null:
			return ""
		var data: Variant = intent.call("getDataString")
		return String(data) if data != null else ""
	for a in OS.get_cmdline_args() + OS.get_cmdline_user_args():
		if String(a).begins_with("lantern://"):
			return String(a)
	return ""


## → {kind:"join", code, edition, name, relay} | {kind:"friend", tag} | {kind:""}
static func parse(url: String) -> Dictionary:
	var u := url.strip_edges()
	if u.begins_with(Invite.SCHEME):
		var r := Invite.parse(u)
		if r.get("ok", false):
			return {"kind": "join", "code": r.code, "edition": r.get("edition", ""), "name": r.get("name", ""), "relay": r.get("relay", "")}
		return {"kind": ""}
	if u.begins_with("lantern://friend"):
		var q := Invite._query(u)
		var tag := String(q.get("tag", "")).strip_edges()
		if RegEx.create_from_string("^.{3,16}#\\d{4}$").search(tag):
			return {"kind": "friend", "tag": tag}
	return {"kind": ""}


static func friend_link(tag: String) -> String:
	return "lantern://friend?v=1&tag=" + tag.uri_encode()
