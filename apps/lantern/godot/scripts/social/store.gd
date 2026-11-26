class_name Store
extends RefCounted
## Покупки Google Play и реклама AdMob.
## 2.0: покупки — свой плагин LanternBilling (Play Billing Library 8, addons/lantern_android/android/LanternBilling.java).
## Порядок: Play показывает оплату → токен покупки уходит на сервер LANTERN → сервер проверяет его у Google
## (relay/billing.js) и начисляет → для искр приложение «расходует» покупку (можно купить снова).
## Нет Google Play (APK со стороны, ПК, эмулятор без Play) — платное просто не показывается, остальное работает.
## Товары (создаёт владелец в Play Console, docs/PLAY_BILLING.md): lantern_pro_month — подписка «LANTERN Plus»,
## lantern_pass_s1 — сезонный пропуск, sparks_500 / sparks_1200 — искры (расходуемые).

const PRODUCTS := ["sparks_500", "sparks_1200", "lantern_pass_s1"]
const SUBS := ["lantern_pro_month"]
const ADS_CFG := "res://config/ads.cfg"
## Официальные ТЕСТОВЫЕ ID Google (безопасны, денег не приносят)
const TEST_APP_ID := "ca-app-pub-3940256099942544~3347511713"
const TEST_REWARDED := "ca-app-pub-3940256099942544/5224354917"
const TEST_INTERSTITIAL := "ca-app-pub-3940256099942544/1033173712"

static var _billing: Object = null
static var ready := false         # Play ответил «можно покупать»
static var prices := {}           # product -> цена из Play («99,00 ₴»)
static var _timer_on := false


static func billing_available() -> bool:
	return Engine.has_singleton("LanternBilling")


## Можно ли сейчас показывать настоящую оплату (Play есть и подключился, цены пришли).
static func can_buy(product: String) -> bool:
	return ready and prices.has(product)


static func init_billing() -> void:
	if not billing_available() or _billing != null:
		return
	_billing = Engine.get_singleton("LanternBilling")
	_billing.call("start")
	_poll()


## События плагина забираем опросом (из потока Play в GDScript — через очередь), раз в 0,4 с.
static func _poll() -> void:
	var tree := Engine.get_main_loop() as SceneTree
	if tree == null or _billing == null:
		return
	var raw: String = str(_billing.call("takeEvents"))
	var evs: Variant = JSON.parse_string(raw)
	if evs is Array:
		for e in evs:
			if e is Dictionary:
				_on_event(e)
	tree.create_timer(0.4).timeout.connect(_poll)


static func _on_event(e: Dictionary) -> void:
	match str(e.get("k", "")):
		"state":
			ready = bool(e.get("ok", false))
			AppLog.add("billing", "Play: %s (%d)" % [str(ready), int(e.get("code", 0))])
			if ready:
				_billing.call("query", ",".join(SUBS), ",".join(PRODUCTS))
				_billing.call("restore")      # подписка продлилась / покупка не дошла до сервера в прошлый раз
		"products":
			for d in e.get("list", []):
				prices[str(d.get("id", ""))] = str(d.get("price", ""))
			Social.changed.emit("shop")
		"purchase":
			# 1 = PURCHASED; 2 = PENDING (оплата наличными и т. п.) — ждём, Play пришлёт ещё раз
			if int(e.get("state", 0)) == 1:
				Social.request("purchase", {"product": str(e.get("id", "")), "token": str(e.get("token", ""))})
			elif int(e.get("state", 0)) == 2:
				Social.notice.emit({"kind": "info", "text": L.t("Оплата ожидает подтверждения — начислим, как только Google её примет.")})
		"buy":
			var code := int(e.get("code", 0))
			if code != 1:          # 1 = пользователь закрыл окно оплаты — это не ошибка
				Social.notice.emit({"kind": "info", "text": L.t("Покупка не прошла (код %d). Попробуйте ещё раз.") % code})


## Ответ сервера на {t:'purchase'}: начислено → для искр «расходуем» покупку в Play.
static func on_server(m: Dictionary) -> void:
	if m.get("ok", false):
		if m.get("consume", false) and _billing != null:
			_billing.call("consume", str(m.get("token", "")))
		if not m.get("already", false):
			Social.notice.emit({"kind": "purchased", "product": str(m.get("product", ""))})
	elif str(m.get("error", "")) not in ["", "not_active"]:
		AppLog.add("billing", "сервер: " + str(m.get("error", "")))
		if str(m.get("error", "")) == "billing_not_configured":
			Social.notice.emit({"kind": "info", "text": L.t("Покупка сохранена в Google Play — LANTERN начислит её, как только заработает проверка покупок на сервере.")})


static func buy(product: String) -> void:
	if not Cfg.paid():
		return
	if not can_buy(product):
		Social.notice.emit({"kind": "info", "text": L.t("Покупки доступны в версии из Google Play. Пока работает ДЕМО-режим (Настройки → Тест покупок и рекламы).")})
		return
	_billing.call("buy", product)


static func ads_config() -> Dictionary:
	var c := ConfigFile.new()
	var d := {"app_id": TEST_APP_ID, "rewarded": TEST_REWARDED, "interstitial": TEST_INTERSTITIAL, "enabled": false, "test": true}
	if c.load(ADS_CFG) == OK:
		for k in ["app_id", "rewarded", "interstitial"]:
			var v: String = c.get_value("admob", k, "")
			if v != "":
				d[k] = v
				d["test"] = false
		d["enabled"] = c.get_value("admob", "enabled", false)
	return d


## 2.0.4: есть ли что показать по «Смотреть рекламу»: ДЕМО (отладка) или настоящий AdMob. В выпуске AdMob не подключён —
## кнопки нет (иначе в Google Play был бы фальшивый экран «Тестовая реклама», а в анкете Play — «рекламы нет»).
static func ads_available() -> bool:
	return Social.demo or (Engine.has_singleton("PoingGodotAdMob") and bool(ads_config().enabled))


## Реклама за награду: ДЕМО — экран «Тестовая реклама»; при настроенном AdMob — настоящий ролик.
static func show_rewarded(app: Control) -> void:
	if not Cfg.paid() or not ads_available():
		return
	if Social.demo or not Engine.has_singleton("PoingGodotAdMob") or not ads_config().enabled:
		Overlays.ad(app, true)
		return
	# Настоящий AdMob подключается через плагин Poing Studios (см. docs/MONETIZATION.md); награда начисляется сервером.
	Overlays.ad(app, true)


## Межстраничная — только после выхода из мира и никогда у Pro.
static func show_interstitial(app: Control) -> void:
	if not Cfg.paid():
		return
	if Social.eco.get("pro", false):
		return
	if Social.demo:
		Overlays.ad(app, false)
