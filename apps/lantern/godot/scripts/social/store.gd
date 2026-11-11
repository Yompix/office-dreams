class_name Store
extends RefCounted
## Покупки Google Play (плагин GodotGooglePlayBilling) и реклама AdMob (плагин Poing Studios).
## Пока владелец не завёл товары в Play Console и ID рекламы в config/ads.cfg — работает только ДЕМО-режим.
## Товары: lantern_pro_month (подписка), lantern_pass_s1 (разово за сезон), sparks_500, sparks_1200 (расходуемые).

const PRODUCTS := ["sparks_500", "sparks_1200", "lantern_pass_s1"]
const SUBS := ["lantern_pro_month"]
const ADS_CFG := "res://config/ads.cfg"
## Официальные ТЕСТОВЫЕ ID Google (безопасны, денег не приносят)
const TEST_APP_ID := "ca-app-pub-3940256099942544~3347511713"
const TEST_REWARDED := "ca-app-pub-3940256099942544/5224354917"
const TEST_INTERSTITIAL := "ca-app-pub-3940256099942544/1033173712"

static var _billing: Object = null
static var prices := {}   # product -> строка цены из Play


static func billing_available() -> bool:
	return Engine.has_singleton("GodotGooglePlayBilling")


static func init_billing() -> void:
	if not Cfg.paid():
		return
	if not billing_available() or _billing != null:
		return
	_billing = Engine.get_singleton("GodotGooglePlayBilling")
	_billing.connect("connected", func():
		_billing.call("queryProductDetails", PackedStringArray(PRODUCTS), "inapp")
		_billing.call("queryProductDetails", PackedStringArray(SUBS), "subs"))
	_billing.connect("product_details_query_completed", func(details):
		for d in details:
			prices[d.get("product_id", d.get("productId", ""))] = d.get("one_time_purchase_offer_details", {}).get("formatted_price", ""))
	_billing.connect("purchases_updated", func(purchases):
		for p in purchases:
			Social.request("purchase", {"product": (p.get("product_ids", [""]))[0], "token": p.get("purchase_token", "")}))
	_billing.call("startConnection")


static func buy(product: String) -> void:
	if not Cfg.paid():
		return
	if _billing == null or not prices.has(product):
		Social.notice.emit({"kind": "info", "text": "Покупки скоро — пока работает ДЕМО-режим (Настройки → Тест покупок и рекламы)"})
		return
	_billing.call("purchase", product)


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


## Реклама за награду: ДЕМО — экран «Тестовая реклама»; при настроенном AdMob — настоящий ролик.
static func show_rewarded(app: Control) -> void:
	if not Cfg.paid():
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
