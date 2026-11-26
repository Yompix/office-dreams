package com.yompix.novograd.life;

/**
 * 0.9.8 «Руки»: виды взаимодействий. Общие для сервера и клиента: длина (тики), «держит ли» игрока на месте,
 * метки звуков (тик → звук). Сервер по длине решает, когда наступает результат; клиент по тем же тикам рисует руки и
 * играет звуки — у всех игроков рядом одинаково (время — тики мира от сервера).
 */
public enum HandKind {
    /** Карта в прорезь, PIN (4 клавиши + ввод). Потом — экран суммы. */
    ATM_IN(46, true, 7),
    /** Банкомат считает, купюры в лотке: взять, пересчитать, в карман; забрать карту. */
    ATM_CASH(60, true, 7),
    /** Наличные из кармана в приёмник, банкомат считает, карту — обратно. */
    ATM_DEPOSIT(54, true, 7),
    /** Просто забрать карту (вышел из меню). */
    ATM_CARD(20, true, 7),
    /** Автомат: монетка, кнопка, банка падает, нагнуться, достать, открыть, выпить (или пачка чипсов). */
    VEND(150, true, 7),
    /** Ларёк/кафе: заказать, заплатить, получить, съесть или выпить. */
    STALL(124, true, 7),
    /** Касса: выложить покупки, оплатить картой (касание терминала) или наличными, забрать пакет. */
    CHECKOUT(60, true, 5),
    /** Дверь: рука на ручку, толкнуть. Не держит. */
    DOOR(12, false, 0),
    /** Заправка пешком: пистолет из колонки → в бак → льётся → обратно. */
    FUEL(210, true, 8),
    /** Ремонт у колонки: присел у машины, ключ-трещотка. */
    REPAIR(170, true, 8),
    /** Тайник/сейф: присесть, наборный диск, открыть дверцу. */
    SAFE(64, true, 6),
    /** Сел в машину: дверь, сесть, закрыть. Пристегнулся. Вышел. */
    CAR_IN(18, false, 0),
    BELT(16, false, 0),
    CAR_OUT(14, false, 0),
    /** Закурить (только 18+): сигарета, зажигалка, три затяжки, выбросить. Можно идти. */
    SMOKE(330, false, 0),
    /** Выпить на ходу: бутылка/стакан, три глотка. */
    DRINK(130, false, 0);

    /** Длина в тиках; держит ли на месте (сдвинулся — прервано); сколько тиков подходит к месту. */
    public final int len, align;
    public final boolean blocking;

    HandKind(int len, boolean blocking, int align) {
        this.len = len;
        this.blocking = blocking;
        this.align = align;
    }

    public String id() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    public static HandKind of(String id) {
        try {
            return valueOf(id.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Метка звука: тик, звук (sounds.json «hands.*» или «minecraft:…»), громкость, высота. */
    public record Cue(int at, String sound, float vol, float pitch) {}

    private static Cue c(int at, String s) {
        return new Cue(at, s, 0.8f, 1f);
    }

    private static Cue c(int at, String s, float vol, float pitch) {
        return new Cue(at, s, vol, pitch);
    }

    /** Звуки по ходу действия; variant — «snack», «drink», «food», «card», «cash», «beer», «soda», «coffee»… */
    public Cue[] cues(String variant) {
        return switch (this) {
            case ATM_IN -> new Cue[]{c(16, "card_in"), c(27, "key", 0.6f, 1.0f), c(31, "key", 0.6f, 1.12f), c(35, "key", 0.6f, 0.94f), c(39, "key", 0.6f, 1.06f),
                    c(43, "key", 0.7f, 1.3f)};
            case ATM_CASH -> new Cue[]{c(2, "atm_whirr", 0.7f, 1f), c(22, "cash"), c(34, "cash", 0.6f, 1.2f), c(48, "card_out")};
            case ATM_DEPOSIT -> new Cue[]{c(12, "cash"), c(20, "atm_whirr", 0.7f, 1.05f), c(42, "card_out")};
            case ATM_CARD -> new Cue[]{c(6, "card_out")};
            case VEND -> "snack".equals(variant)
                    ? new Cue[]{c(15, "coin"), c(26, "button"), c(34, "can_drop", 0.6f, 1.5f), c(70, "bag_tear"), c(86, "crunch"), c(104, "crunch", 0.8f, 1.1f), c(122, "crunch", 0.8f, 0.9f)}
                    : new Cue[]{c(15, "coin"), c(26, "button"), c(36, "can_drop"), c(72, "can_open"), c(90, "gulp"), c(108, "gulp", 0.8f, 0.92f), c(126, "gulp", 0.8f, 1.08f)};
            case STALL -> "drink".equals(variant) || "coffee".equals(variant)
                    ? new Cue[]{c(28, "cash", 0.6f, 1.1f), c(50, "wrapper", 0.4f, 1.3f), c(76, "gulp", 0.6f, 1.1f), c(94, "gulp", 0.6f, 1f), c(110, "gulp", 0.6f, 1.15f)}
                    : new Cue[]{c(28, "cash", 0.6f, 1.1f), c(50, "wrapper"), c(72, "bite"), c(90, "bite", 0.8f, 0.9f), c(108, "bite", 0.8f, 1.1f)};
            case CHECKOUT -> "cash".equals(variant)
                    ? new Cue[]{c(12, "scan"), c(16, "scan", 0.8f, 1.02f), c(30, "cash"), c(40, "receipt", 0.5f, 1f), c(50, "wrapper", 0.6f, 0.9f)}
                    : new Cue[]{c(12, "scan"), c(16, "scan", 0.8f, 1.02f), c(32, "tap"), c(40, "receipt", 0.5f, 1f), c(50, "wrapper", 0.6f, 0.9f)};
            case DOOR -> new Cue[]{c(3, "door_handle", 0.5f, 1f)};
            case FUEL -> new Cue[]{c(14, "nozzle"), c(34, "nozzle", 0.7f, 1.2f), c(40, "fuel_flow", 0.6f, 1f), c(88, "fuel_flow", 0.6f, 1f), c(136, "fuel_flow", 0.6f, 1f),
                    c(186, "nozzle", 0.7f, 1.1f), c(200, "nozzle", 0.8f, 0.9f)};
            case REPAIR -> new Cue[]{c(18, "ratchet"), c(40, "ratchet", 0.8f, 1.1f), c(62, "ratchet"), c(84, "ratchet", 0.8f, 0.95f), c(106, "ratchet"),
                    c(128, "ratchet", 0.8f, 1.05f), c(150, "nozzle", 0.5f, 0.6f)};
            case SAFE -> new Cue[]{c(14, "dial"), c(18, "dial", 0.8f, 1.1f), c(22, "dial"), c(28, "dial", 0.8f, 0.9f), c(33, "dial"), c(40, "dial", 1f, 1.3f),
                    c(48, "safe_open")};
            case CAR_IN -> new Cue[]{c(0, "car_door_open"), c(14, "car_door_close")};
            case BELT -> new Cue[]{c(2, "belt", 0.7f, 1f)};
            case CAR_OUT -> new Cue[]{c(0, "car_door_open", 0.7f, 1.05f), c(10, "car_door_close", 0.9f, 1.05f)};
            case SMOKE -> new Cue[]{c(18, "lighter"), c(80, "exhale", 0.4f, 1f), c(170, "exhale", 0.4f, 0.95f), c(260, "exhale", 0.4f, 1.05f)};
            case DRINK -> "coffee".equals(variant) ? new Cue[]{c(34, "gulp", 0.6f, 1.1f), c(74, "gulp", 0.6f, 1f), c(108, "gulp", 0.6f, 1.15f)}
                    : new Cue[]{c(10, "bottle", 0.5f, 1f), c(34, "gulp"), c(74, "gulp", 0.8f, 0.92f), c(108, "gulp", 0.8f, 1.08f)};
        };
    }
}
