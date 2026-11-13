class_name RelStream
extends RefCounted
## 1.6: TCP-поток, переживающий обрыв связи с relay (перезапуск relay при выкладке, смена сети на телефоне).
## Каждая сторона помнит отправленное, пока другая не подтвердит (`{"t":"ack","sid","n"}` — получено n байт).
## После переподключения друг открывает поток с тем же ключом и `rx` (сколько получил), хозяин досылает
## недошедшее и отвечает `ack` с `r:1` (сколько получил он) — друг досылает своё. Игра Minecraft обрыва не замечает,
## если связь вернулась быстрее ~25 с (Minecraft отключает через 30 с тишины).

const BUF_MAX := 3 << 20       # не больше 3 МБ неподтверждённого (дальше ждём подтверждений — как «окно» TCP)
const ACK_BYTES := 65536
const ACK_MS := 400
const PENDING_MAX := 32 << 20  # игра на этом устройстве не забирает данные — дальше закрываем поток
const DETACH_MS := 60000       # сколько ждать возврата связи, не закрывая соединение игры


## 1.8: пауза цикла моста. Сразу после данных не спим совсем (ответ игры приходит через доли миллисекунды),
## пока идёт игра — короткая пауза, в тишине — длиннее (бережём батарею). Никаких таймеров накопления:
## всё, что пришло за один опрос, уходит сразу.
const SPIN_US := 300
const ACTIVE_US := 2000000
const READ_MAX := 262144       # сколько читаем из одного соединения игры за один проход (несколько кадров по 60000)


static func idle_wait(now_us: int, last_busy_us: int, has_streams: bool) -> void:
	var dt := now_us - last_busy_us
	if dt < SPIN_US:
		return
	if dt < ACTIVE_US:
		OS.delay_usec(_active_sleep)
	else:
		OS.delay_usec(1000 if has_streams else 4000)


static var _active_sleep := 500 if OS.has_feature("mobile") else 250


## Прочитать всё, что игра успела прислать (до READ_MAX), и отправить кадрами. Возвращает число байт.
static func pump_out(link: RelayLink, sid: int, s: Dictionary, t: StreamPeerTCP) -> int:
	var total := 0
	while total < READ_MAX:
		var n := t.get_available_bytes()
		if n <= 0:
			break
		var r := t.get_partial_data(min(n, 60000))
		var data: PackedByteArray = r[1]
		if r[0] != OK or data.is_empty():
			break
		link.send_bin(sid, data)
		on_sent(s, data)
		total += data.size()
	return total


static func init(s: Dictionary, reliable: bool) -> void:
	s.sent = 0
	s.acked = 0
	s.buf = PackedByteArray()
	s.recv = 0
	s.ack_sent = 0
	s.ack_t = 0
	s.rel = reliable        # другая сторона точно подтверждает (хозяин: у друга есть ключ; друг: пришло первое ack)
	s.norel = false         # старая версия на той стороне — возобновление невозможно
	if not s.has("pending"):
		s.pending = PackedByteArray()


## Можно ли читать из игры (окно неподтверждённого не переполнено, поток не ждёт возобновления).
static func can_read(s: Dictionary) -> bool:
	if s.get("detached", false) or s.get("wait_resume", false):
		return false
	return s.norel or not s.rel or s.buf.size() < BUF_MAX


static func on_sent(s: Dictionary, data: PackedByteArray) -> void:
	s.sent += data.size()
	if s.norel:
		return
	s.buf.append_array(data)
	if not s.rel and s.buf.size() > BUF_MAX:
		s.norel = true          # подтверждений нет — на той стороне старая версия LANTERN
		s.buf = PackedByteArray()


static func on_ack(s: Dictionary, n: int) -> void:
	s.rel = true
	if s.norel or n <= s.acked or n > s.sent:
		return
	s.buf = s.buf.slice(n - s.acked)
	s.acked = n


static func on_recv(s: Dictionary, n: int) -> void:
	s.recv += n


static func maybe_ack(link: RelayLink, sid: int, s: Dictionary, now: int) -> void:
	if s.recv > s.ack_sent and (s.recv - s.ack_sent >= ACK_BYTES or now - int(s.ack_t) >= ACK_MS):
		link.send_json({"t": "ack", "sid": sid, "n": s.recv})
		s.ack_sent = s.recv
		s.ack_t = now


## Дослать всё, что другая сторона не получила (она получила from_n байт). false — невозможно.
static func replay(link: RelayLink, sid: int, s: Dictionary, from_n: int) -> bool:
	if s.norel or from_n < s.acked or from_n > s.sent:
		return false
	s.buf = s.buf.slice(from_n - s.acked)
	s.acked = from_n
	var i := 0
	while i < s.buf.size():
		link.send_bin(sid, s.buf.slice(i, i + 60000))
		i += 60000
	return true


## Записать в соединение игры без блокировки (зависшая игра одного друга не тормозит остальных).
## false — соединение сломано или переполнено.
static func flush(s: Dictionary, t: StreamPeerTCP) -> bool:
	if s.pending.is_empty():
		return true
	var r: Array = t.put_partial_data(s.pending)
	if r[0] != OK:
		return false
	var n: int = r[1]
	if n > 0:
		s.pending = s.pending.slice(n)
	return s.pending.size() < PENDING_MAX


static func info(s: Dictionary) -> String:
	return L.t("отпр %d (подтв %d), получ %d%s") % [int(s.get("sent", 0)), int(s.get("acked", 0)), int(s.get("recv", 0)), L.t(" без возобновления") if s.get("norel", false) else ""]


## Перед закрытием: дописать пришедшее в соединение игры (не дольше ms — зависшая игра не держит мост).
static func drain(s: Dictionary, t: StreamPeerTCP, ms := 300) -> void:
	if not s.has("pending") or s.pending.is_empty() or t.get_status() != StreamPeerTCP.STATUS_CONNECTED:
		return
	var until := Time.get_ticks_msec() + ms
	while not s.pending.is_empty() and Time.get_ticks_msec() < until:
		var r: Array = t.put_partial_data(s.pending)
		if r[0] != OK:
			break
		s.pending = s.pending.slice(int(r[1]))
		if not s.pending.is_empty():
			OS.delay_usec(2000)
	s.pending = PackedByteArray()
