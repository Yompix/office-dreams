class_name VGames
extends RefCounted
## 2.0: «Как играть по сети» — популярные игры с игрой по локальной сети (LAN) или «вход по IP».
## Все отобраны вручную: у каждой есть официальный режим LAN / прямого подключения. Названия пунктов меню — как в
## самих играх (на английском, у игр свой язык интерфейса), пояснения переводятся.
##   way: "list" — друзья видят игру в списке LAN сами; "ip" — друзья вводят IP хозяина (10.88.x.y); "both" — оба способа.
##   port — что открыть, если брандмауэр Windows спросит (подсказка; LANTERN сам разрешает входящие из своей сети).

const PC := [
	{"id": "minecraft", "name": "Minecraft: Java Edition", "way": "list", "host": "Esc → Open to LAN", "join": "Multiplayer (LAN World)", "port": "TCP 25565"},
	{"id": "terraria", "name": "Terraria", "way": "ip", "host": "Multiplayer → Host & Play", "join": "Multiplayer → Join via IP", "port": "TCP 7777"},
	{"id": "stardew", "name": "Stardew Valley", "way": "both", "host": "Co-op → Host (farmhand cabins)", "join": "Co-op → Join LAN Game", "port": "UDP 24642"},
	{"id": "factorio", "name": "Factorio", "way": "both", "host": "Multiplayer → Host new game", "join": "Multiplayer → Browse LAN games / Connect to address", "port": "UDP 34197"},
	{"id": "valheim", "name": "Valheim", "way": "ip", "host": "Start game (community server off)", "join": "Join game → Join IP", "port": "UDP 2456–2458"},
	{"id": "dst", "name": "Don't Starve Together", "way": "list", "host": "Host Game → Server type: LAN", "join": "Browse Games → filter LAN", "port": "UDP 10999"},
	{"id": "l4d2", "name": "Left 4 Dead 2", "way": "both", "host": "Console: sv_lan 1 → map …", "join": "Console: connect <IP>", "port": "UDP 27015"},
	{"id": "cs16", "name": "Counter-Strike 1.6", "way": "both", "host": "New Game (Internet off)", "join": "Find Servers → LAN", "port": "UDP 27015"},
	{"id": "wc3", "name": "Warcraft III", "way": "list", "host": "Local Area Network → Create Game", "join": "Local Area Network", "port": "UDP/TCP 6112"},
	{"id": "sc", "name": "StarCraft: Remastered", "way": "list", "host": "Multiplayer → Local Area Network (UDP)", "join": "Local Area Network (UDP)", "port": "UDP 6111–6112"},
	{"id": "bl2", "name": "Borderlands 2", "way": "list", "host": "Network: LAN", "join": "Find Game → LAN", "port": "UDP 7777"},
	{"id": "pz", "name": "Project Zomboid", "way": "ip", "host": "Host (server)", "join": "Join → Server IP", "port": "UDP 16261–16262"},
]

const ANDROID := [
	{"id": "mcpe", "name": "Minecraft: Bedrock Edition", "way": "both", "host": "Settings → Multiplayer → Visible to LAN Players", "join": "Play → Friends (LAN Games) / Servers → Add Server", "port": "UDP 19132"},
	{"id": "terraria_m", "name": "Terraria (mobile)", "way": "ip", "host": "Multiplayer → Host & Play", "join": "Multiplayer → Join via IP", "port": "TCP 7777"},
	{"id": "mindustry", "name": "Mindustry", "way": "both", "host": "Host Multiplayer Game", "join": "Join Game → Local / Add Server", "port": "TCP/UDP 6567"},
	{"id": "bombsquad", "name": "BombSquad", "way": "list", "host": "Gather → Host", "join": "Gather → Local Network", "port": "UDP 43210"},
	{"id": "soulknight", "name": "Soul Knight", "way": "list", "host": "Multiplayer → Create (LAN)", "join": "Multiplayer → Join (LAN)", "port": "UDP"},
	{"id": "amongus", "name": "Among Us", "way": "list", "host": "Local → Create Game", "join": "Local (Find Game)", "port": "UDP 22023"},
	{"id": "minimilitia", "name": "Mini Militia – Doodle Army 2", "way": "list", "host": "Multiplayer → Local → Create", "join": "Multiplayer → Local → Join", "port": "UDP"},
]


static func all() -> Array:
	return PC + ANDROID


static func for_platform() -> Array:
	return (ANDROID + PC) if Cfg.mobile() else (PC + ANDROID)


static func by_id(id: String) -> Dictionary:
	for g in all():
		if g.id == id:
			return g
	return {}


static func title(id: String) -> String:
	if id == "" or id == "any":
		return L.t("Любая игра")
	var g := by_id(id)
	return String(g.get("name", id))


static func way_text(g: Dictionary) -> String:
	match g.get("way", ""):
		"list": return L.t("Игра сама появится в списке «по локальной сети»")
		"ip": return L.t("Друзья вводят IP хозяина")
		_: return L.t("Видно в списке LAN или по IP хозяина")
