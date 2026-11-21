extends VBoxContainer
## 2.0.2: «Поддержать» (только ПК) — добровольный донат автору. Ничего не открывает и не даёт преимуществ.
## Звёзды Telegram — через бота студии (@officedream1_bot, «?start=lantern» → счёт в звёздах, как спонсоры игр:
## отдельная таблица game_sponsors, не фонд Office Dreams). SOL — на публичный кошелёк владельца (из HANDOFF.md).
## На Android экрана нет: Google Play не разрешает оплату мимо Play в приложении.
## Пустое значение — кнопка скрыта (адреса здесь не придумываются).

const STARS_BOT := "officedream1_bot"
const STARS_START := "lantern"
const SOL_ADDRESS := "8oqrJrimPonh7F83C6pU3gxQjhV9qo5sVScbrLsWZVw1"
const SOL_AMOUNTS := [0.05, 0.1, 0.25, 0.5]

var app: Control
var arg: Variant = null
var _amount := 0.1
var _qr_rect: TextureRect
var _amount_l: Label
var _chips: Control


static func available() -> bool:
	return not Cfg.mobile() and (stars_link() != "" or SOL_ADDRESS != "")


static func stars_link() -> String:
	return "https://t.me/%s?start=%s" % [STARS_BOT, STARS_START] if STARS_BOT != "" else ""


func sol_uri() -> String:
	return "solana:%s?amount=%s&label=%s&message=%s" % [SOL_ADDRESS, _fmt(_amount), "LANTERN".uri_encode(), L.t("Спасибо за LANTERN").uri_encode()]


func _fmt(a: float) -> String:
	return String.num(a, 2).rstrip("0").rstrip(".")


func _ready() -> void:
	add_theme_constant_override("separation", 16)
	# шапка: чиби бренда и короткий честный текст
	var top := UI.hbox(18)
	var ch := Chibi.new()
	ch.custom_minimum_size = Vector2(120, 120)
	top.add_child(ch)
	var tv := UI.vbox(8)
	tv.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	tv.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	tv.add_child(UI.hero(L.t("Поддержать LANTERN"), 30))
	tv.add_child(UI.para(L.t("LANTERN бесплатный и без рекламы на ПК. Если он вам помог — можно поддержать автора. Это просто спасибо: донат ничего не открывает и не даёт преимуществ."), 14))
	top.add_child(tv)
	add_child(top)
	var cols := UI.cols(16)
	add_child(cols)
	# --- звёзды Telegram
	if stars_link() != "":
		var c := UI.card(UI.S5)
		c.add_theme_stylebox_override("panel", UI.pix(Color(0.04, 0.04, 0.04, 0.92), Color(1, 1, 1, 0.55), 4, UI.S5))
		c.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		var v := UI.vbox(10)
		c.add_child(v)
		v.add_child(UI.caption(L.t("Звёзды Telegram")))
		v.add_child(UI.label(L.t("Через бота Yompix"), 20, UI.TEXT, UI.font_title))
		v.add_child(UI.para(L.t("Откроется Telegram: выберите сумму в звёздах. Ваше имя может попасть в список спонсоров игр Yompix — или скройте его командой /sponsor -."), 13, UI.MUTED))
		var b := UI.button(L.t("Открыть в Telegram"), "primary", func(): UI.open(stars_link()), "star")
		v.add_child(b)
		var lr := UI.hbox(8)
		var ll := UI.label(stars_link().replace("https://", ""), 12, UI.DIM, UI.font_mono)
		ll.set_meta("user_text", true)
		ll.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		ll.clip_text = true
		lr.add_child(ll)
		lr.add_child(UI.icon_button("copy", func(): UI.copy(stars_link()); app.toast(L.t("Ссылка скопирована")), L.t("Скопировать ссылку")))
		v.add_child(lr)
		cols.add_child(c)
	# --- Solana
	if SOL_ADDRESS != "":
		var c := UI.card(UI.S5)
		c.add_theme_stylebox_override("panel", UI.pix(Color(0.04, 0.04, 0.04, 0.92), Color(1, 1, 1, 0.55), 4, UI.S5))
		c.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		var v := UI.vbox(10)
		c.add_child(v)
		v.add_child(UI.caption(L.t("Solana (SOL)")))
		var row := UI.hbox(16)
		var qv := UI.vbox(6)
		var qp := PanelContainer.new()
		qp.add_theme_stylebox_override("panel", UI.pix(Color.WHITE, Color.WHITE, 4, 8))
		_qr_rect = TextureRect.new()
		qp.add_child(_qr_rect)
		qv.add_child(qp)
		_amount_l = UI.label("", 13, UI.TEXT, UI.font_caps)
		_amount_l.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
		qv.add_child(_amount_l)
		row.add_child(qv)
		var rv := UI.vbox(10)
		rv.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		rv.add_child(UI.para(L.t("Наведите камеру кошелька (Phantom, Solflare) на QR или скопируйте адрес. Сеть — только Solana."), 13, UI.MUTED))
		_chips = UI.flow(8)
		rv.add_child(_chips)
		var ar := UI.hbox(8)
		var al := UI.label(SOL_ADDRESS, 12, UI.TEXT, UI.font_mono)
		al.set_meta("user_text", true)
		al.autowrap_mode = TextServer.AUTOWRAP_ARBITRARY
		al.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		ar.add_child(al)
		ar.add_child(UI.icon_button("copy", func(): UI.copy(SOL_ADDRESS); app.toast(L.t("Адрес скопирован")), L.t("Скопировать адрес")))
		rv.add_child(ar)
		var ob := UI.button(L.t("Открыть в кошельке"), "secondary", func():
			if UI.open(sol_uri()) != OK:
				UI.copy(SOL_ADDRESS)
				app.toast(L.t("Кошелёк не найден — адрес скопирован"), UI.YELLOW), "link")
		rv.add_child(ob)
		row.add_child(rv)
		v.add_child(row)
		cols.add_child(c)
		_render_sol()
	add_child(UI.para(L.t("Платёж уходит напрямую автору. LANTERN не видит ваш кошелёк и не хранит данные оплаты. Сид-фразу и ключи никому не сообщайте — их никогда не спрашивают."), 12, UI.DIM))


func _render_sol() -> void:
	for c in _chips.get_children():
		c.queue_free()
	for a in SOL_AMOUNTS:
		var amt: float = a
		var b := UI.button("%s SOL" % _fmt(amt), "primary" if is_equal_approx(amt, _amount) else "ghost", func():
			_amount = amt
			_render_sol())
		_chips.add_child(b)
	QR.encode(sol_uri()).show_in(_qr_rect, 168)
	_amount_l.text = "%s SOL" % _fmt(_amount)
