package com.yompix.novograd.life;

import java.util.ArrayList;
import java.util.List;

/**
 * Каталог стиля: одежда двух магазинов («Шмот-Базар» — дёшево, бутик «Бархат» — дорого), услуги
 * цирюльни «Острая бритва», варианты лица и тела для редактора внешности. Цены одни и те же у сервера и клиента.
 *
 * <p>0.9.6: верх (26) и куртки поверх (13), низ (17), обувь (15), аксессуары (33: головные уборы, очки, шея, сумки,
 * часы), 23 причёски, 17 цветов волос, 10 тонов кожи, 8 цветов глаз. У вещи — пол (0 любой, 1 мужское, 2 женское)
 * и стили (биты {@link #ST} … {@link #WO}) — по ним генератор одевает жителей ({@link LookGen}).
 */
public final class StyleCatalog {
    private StyleCatalog() {}

    public static final String CHEAP_NAME = "Шмот-Базар", LUX_NAME = "Бутик «Бархат»", BARBER_NAME = "Цирюльня «Острая бритва»",
            GYM_NAME = "Зал «Железо»";

    /** Стили: улица, повседневное, нарядное, спорт, байкер, деловое, пляж, зима, рабочее. */
    public static final int ST = 1, CA = 2, FO = 4, SP = 8, BI = 16, BU = 32, BE = 64, WI = 128, WO = 256;

    public enum Slot {
        TOP("Верх"), OUTER("Куртки"), PANTS("Низ"), SHOES("Обувь"), HAT("Головные уборы"), GLASSES("Очки"), NECK("Шея"), BAG("Сумки"), WRIST("Часы");

        public final String title;

        Slot(String title) {
            this.title = title;
        }

        /** Можно ходить без этой вещи. */
        public boolean optional() {
            return this != TOP && this != PANTS && this != SHOES;
        }

        public boolean accessory() {
            return ordinal() >= HAT.ordinal();
        }
    }

    /**
     * Вещь: id — он же вид для отрисовки; tier 0 — «Шмот-Базар», 1 — «Бархат»; colors — варианты цвета (RGB);
     * gender 0 — любой, 1 — мужское, 2 — женское; tags — стили.
     */
    public record Wear(String id, Slot slot, String name, int tier, int price, int[] colors, String desc, int gender, int tags) {
        public int color(int i) {
            return colors[Math.floorMod(i, colors.length)];
        }

        public boolean fits(boolean female) {
            return gender == 0 || gender == (female ? 2 : 1);
        }
    }

    public static final List<Wear> WEAR = new ArrayList<>();
    private static int G, T;

    /** Пол и стили для следующих вещей. */
    private static void as(int gender, int tags) {
        G = gender;
        T = tags;
    }

    private static void w(String id, Slot slot, String name, int tier, int price, String desc, int... colors) {
        WEAR.add(new Wear(id, slot, name, tier, price, colors, desc, G, T));
    }

    // ---- палитры
    private static final int[] BASIC = {0xF2F2F2, 0x1E1E1E, 0x8A8A8A, 0x1A2A50, 0xC62828, 0x2E7D32, 0xF9A825, 0x5E2B8A};
    private static final int[] EARTH = {0x6B4A2E, 0x3A3A2A, 0xB09868, 0x55585E, 0x2A3A4A};
    private static final int[] PASTEL = {0xF4C2C2, 0xBFD8F2, 0xCDE8C4, 0xF2E3B3, 0xE0CDEB, 0xF2F2F2};

    static {
        // ================================================================ верх
        as(0, CA | BE | SP | ST);
        w("tank", Slot.TOP, "Майка", 0, 10, "", 0xEDEDED, 0x222222, 0x9E9E9E, 0x1A2A50);
        as(0, CA | ST);
        w("tee", Slot.TOP, "Футболка", 0, 20, "", BASIC);
        w("tee_print", Slot.TOP, "Футболка с принтом", 0, 35, "", 0x161616, 0xF2F2F2, 0xE0407A, 0x1C6E8A);
        as(0, CA | ST | WI);
        w("longsleeve", Slot.TOP, "Лонгслив", 0, 30, "", 0xF2F2F2, 0x1E1E1E, 0x7A1F2B, 0x2A4A3A, 0xB0A080);
        as(0, CA | BU);
        w("polo", Slot.TOP, "Поло", 0, 45, "", 0xF5F5F5, 0x1A2A50, 0x6FCFB0, 0xC62838);
        as(0, FO | BU | CA);
        w("shirt", Slot.TOP, "Рубашка", 0, 60, "", 0xF6F6F6, 0xBFD8F2, 0xF4C2C2, 0x1E1E1E, 0xE8DCC0);
        as(0, CA | ST | WO);
        w("flannel", Slot.TOP, "Клетчатая рубашка", 0, 55, "", 0xB71C1C, 0x1E4A8C, 0x2E6B34, 0x5A4A3A);
        as(1, ST | CA);
        w("bowling", Slot.TOP, "Рубашка-боулинг", 0, 70, "", 0x1E1E1E, 0xC62838, 0x16A3A8, 0xE8DCC0);
        as(0, BE | CA);
        w("hawaii", Slot.TOP, "Гавайская рубашка", 1, 350, "", 0x16A3A8, 0xC62838, 0xF2C230, 0x1E1E1E);
        as(0, ST | SP | CA);
        w("hoodie", Slot.TOP, "Худи", 0, 60, "", 0x8A8A8A, 0x1B1B1B, 0x1C2850, 0x5E2B8A, 0xC62828, 0xF2E3B3);
        w("sweatshirt", Slot.TOP, "Свитшот", 0, 50, "", 0x8A8A8A, 0x1E1E1E, 0x2E5E3A, 0xF4C2C2, 0xBFD8F2);
        as(0, CA | WI);
        w("sweater", Slot.TOP, "Свитер", 0, 70, "", 0x7A1F2B, 0x2A4A3A, 0xE8DCC0, 0x3A3A44, 0x1A2A50);
        as(0, FO | WI | BU);
        w("turtleneck", Slot.TOP, "Водолазка", 1, 260, "", 0x161616, 0xE8DCC0, 0x5A3A2A, 0x3A3A44);
        as(0, SP | ST);
        w("tracksuit", Slot.TOP, "Олимпийка", 0, 80, "", 0x1E50B4, 0xB0201E, 0x151515, 0x1C6E3A);
        as(0, SP);
        w("jersey", Slot.TOP, "Спортивная майка", 0, 40, "", 0xF2C230, 0x1E50B4, 0xC62828, 0x1E1E1E);
        as(2, BU | FO | CA);
        w("blouse", Slot.TOP, "Блузка", 1, 240, "", 0xF6F6F6, 0xF4C2C2, 0xBFD8F2, 0x1E1E1E, 0xE0CDEB);
        as(2, CA | WI);
        w("cardigan", Slot.TOP, "Кардиган", 0, 75, "", 0xE8DCC0, 0x7A1F2B, 0x8A8A8A, 0xF4C2C2);
        as(2, CA | BE);
        w("tunic", Slot.TOP, "Туника", 0, 65, "", 0xF2F2F2, 0x16A3A8, 0xF2C230, 0xE0407A);
        as(2, BE | CA);
        w("sundress", Slot.TOP, "Сарафан", 0, 90, "", 0xF2C230, 0x16A3A8, 0xF4C2C2, 0xF2F2F2, 0x2E7D32);
        as(2, FO | CA);
        w("dress", Slot.TOP, "Платье", 1, 420, "", 0x7A1F2B, 0x161616, 0x1A2A50, 0x2E5E3A, 0xE0407A);
        as(2, BU | FO);
        w("office_dress", Slot.TOP, "Платье-футляр", 1, 520, "", 0x161616, 0x3A3A44, 0x1A2A50, 0xE8DCC0);
        as(2, BE);
        w("swimsuit", Slot.TOP, "Купальник", 0, 60, "Закрытый, спортивный.", 0x1A2A50, 0xC62828, 0x16A3A8, 0x161616);
        as(0, FO | BU);
        w("suit", Slot.TOP, "Костюм-тройка", 1, 1400, "", 0x161618, 0x3A3C42, 0x18244A, 0xEDEDED);
        w("vest_knit", Slot.TOP, "Жилет", 1, 280, "", 0x3A3A44, 0x5A3A2A, 0x1A2A50);
        as(0, WO);
        w("work_vest", Slot.TOP, "Сигнальный жилет", 0, 30, "", 0xF57C00, 0xC6E01E);
        w("coverall", Slot.TOP, "Спецовка", 0, 45, "", 0x1E3A6A, 0x5A5A40, 0xC05A1E);
        // ================================================================ куртки (слой поверх)
        as(0, CA | ST);
        w("denim", Slot.OUTER, "Джинсовка", 0, 110, "", 0x3A5F94, 0x6E8FB8, 0x1E1E22);
        as(0, BI | ST);
        w("leather", Slot.OUTER, "Косуха", 1, 480, "", 0x181412, 0x5A3420, 0x5C1420);
        as(0, ST | CA);
        w("bomber", Slot.OUTER, "Бомбер", 1, 560, "", 0x4A5530, 0x161616, 0xE88AB4, 0x1A2A50);
        as(0, BU | FO | CA);
        w("blazer", Slot.OUTER, "Пиджак", 1, 750, "", 0x1E1E22, 0xF2F0EA, 0xE8D8B8, 0x3A3C42, 0x8EC8EC, 0xF2A6C0);
        as(0, FO | WI | BU);
        w("coat", Slot.OUTER, "Пальто", 1, 1100, "", 0xB08A5A, 0x161616, 0x55585E, 0x7A1F2B);
        as(0, BU | CA);
        w("trench", Slot.OUTER, "Тренч", 1, 900, "", 0xC8B08A, 0x3A3A2A, 0x161616);
        as(0, WI);
        w("puffer", Slot.OUTER, "Пуховик", 0, 220, "", 0x161616, 0x1A2A50, 0xC62828, 0xE8E4D8, 0x2E5E3A);
        as(0, WI | ST);
        w("parka", Slot.OUTER, "Парка", 0, 180, "", 0x4A5530, 0x1A2A50, 0x161616);
        as(0, SP | CA);
        w("windbreaker", Slot.OUTER, "Ветровка", 0, 90, "", 0x1E50B4, 0xC62828, 0x1C6E3A, 0xF2C230);
        as(0, ST | SP);
        w("varsity", Slot.OUTER, "Бейсбольная куртка", 0, 150, "", 0x7A1F2B, 0x1A2A50, 0x1E1E1E, 0x2E5E3A);
        as(0, BI);
        w("biker_vest", Slot.OUTER, "Байкерский жилет", 0, 130, "", 0x161412, 0x3A2A1C);
        as(0, CA | WI | WO);
        w("fleece", Slot.OUTER, "Флиска", 0, 70, "", 0x3A3A44, 0x2E5E3A, 0x7A1F2B, 0xE8DCC0);
        as(0, WO);
        w("work_jacket", Slot.OUTER, "Рабочая куртка", 0, 85, "", 0x5A4A30, 0x1E3A6A, 0x3A3A2A);
        // ================================================================ низ
        as(0, CA | ST);
        w("jeans", Slot.PANTS, "Джинсы", 0, 30, "", 0x2F4A7A, 0x1C1C22, 0x7A98C0, 0x4A5A6A);
        w("skinny", Slot.PANTS, "Узкие джинсы", 0, 40, "", 0x1C1C22, 0x2F4A7A, 0xB0B0B0);
        as(0, ST);
        w("ripped", Slot.PANTS, "Рваные джинсы", 0, 50, "", 0x7A98C0, 0x2F4A7A, 0x1C1C22);
        as(0, SP | ST);
        w("sweats", Slot.PANTS, "Спортивные штаны", 0, 25, "", 0x707070, 0x161616, 0x1C2850, 0x7A1F2B);
        w("joggers", Slot.PANTS, "Джоггеры", 0, 35, "", 0x3A3A44, 0x161616, 0x4A5530, 0xE8DCC0);
        as(0, CA | BE | SP);
        w("shorts", Slot.PANTS, "Шорты", 0, 15, "", 0xB09868, 0x3A5F94, 0x222222, 0x2E5E3A);
        as(0, BE | CA | ST);
        w("denim_shorts", Slot.PANTS, "Джинсовые шорты", 0, 25, "", 0x5A7AA8, 0x2F4A7A, 0xE8E4D8);
        as(0, ST | WO);
        w("cargo", Slot.PANTS, "Карго", 0, 45, "", 0x4E5A34, 0xB8A57A, 0x5E6066, 0x161616);
        as(0, FO | BU);
        w("slacks", Slot.PANTS, "Брюки", 1, 300, "", 0x161618, 0x3A3C42, 0xF0EEE8, 0xE0D0B0, 0x18244A);
        as(0, CA | BU);
        w("chinos", Slot.PANTS, "Чиносы", 0, 60, "", 0xC8B08A, 0x1A2A50, 0x4A5530, 0x8A8A8A);
        as(0, BI | ST);
        w("leather_pants", Slot.PANTS, "Кожаные брюки", 1, 520, "", 0x141212, 0x4A2C1C);
        as(0, WO);
        w("work_pants", Slot.PANTS, "Рабочие брюки", 0, 40, "", 0x1E3A6A, 0x3A3A2A, 0x5A5A40);
        as(2, SP | CA);
        w("leggings", Slot.PANTS, "Леггинсы", 0, 30, "", 0x161616, 0x3A3A44, 0x5E2B8A, 0x1A2A50);
        as(2, BU | FO);
        w("skirt", Slot.PANTS, "Юбка-карандаш", 1, 220, "", 0x161616, 0x3A3A44, 0x7A1F2B, 0x1A2A50, 0xE8DCC0);
        as(2, CA);
        w("skirt_pleat", Slot.PANTS, "Юбка плиссе", 0, 70, "", 0x1A2A50, 0x7A1F2B, 0x8A8A8A, 0xF4C2C2, 0x2E5E3A);
        as(2, CA | FO | BE);
        w("skirt_long", Slot.PANTS, "Длинная юбка", 0, 80, "", 0x6B4A2E, 0x161616, 0x16A3A8, 0xF2C230, 0xE0CDEB);
        as(1, BE);
        w("swim_trunks", Slot.PANTS, "Пляжные шорты", 0, 20, "", 0x16A3A8, 0xC62838, 0xF2C230, 0x1A2A50);
        // ================================================================ обувь
        as(0, CA | ST);
        w("canvas", Slot.SHOES, "Кеды", 0, 20, "", 0xF2F2F2, 0x1A1A1A, 0xC62828, 0x1E50B4);
        w("sneakers", Slot.SHOES, "Кроссовки", 0, 40, "", 0xF0F0F0, 0x1A1A1A, 0x1E50B4, 0xC62828);
        as(0, ST);
        w("high_tops", Slot.SHOES, "Высокие кеды", 0, 45, "", 0x1A1A1A, 0xF2F2F2, 0xC62828);
        as(0, SP);
        w("runners", Slot.SHOES, "Беговые кроссовки", 0, 60, "", 0x2A2A2A, 0xF57C00, 0x16A3A8, 0xE0407A);
        as(0, BE | CA);
        w("slides", Slot.SHOES, "Шлёпанцы", 0, 8, "", 0x161616, 0x1E50B4, 0xF2F2F2);
        w("sandals", Slot.SHOES, "Сандалии", 0, 25, "", 0x6B4A2E, 0x161616, 0xE8DCC0);
        as(0, FO | BU);
        w("loafers", Slot.SHOES, "Лаковые туфли", 1, 350, "", 0x111111, 0x5A3420, 0xF2F2F2);
        w("oxfords", Slot.SHOES, "Оксфорды", 1, 300, "", 0x1A1410, 0x5A3420);
        as(0, CA | FO);
        w("boots", Slot.SHOES, "Ботинки «Челси»", 1, 280, "", 0x1A1614, 0x9A6A3A);
        as(0, WO);
        w("work_boots", Slot.SHOES, "Рабочие ботинки", 0, 50, "", 0x6B4A2E, 0x1A1614);
        as(0, BI);
        w("biker_boots", Slot.SHOES, "Байкерские сапоги", 0, 120, "", 0x141210, 0x3A2A1C);
        as(0, WI);
        w("winter_boots", Slot.SHOES, "Зимние ботинки", 0, 70, "", 0x3A2A1C, 0x161616, 0xE8E4D8);
        as(2, FO | BU);
        w("heels", Slot.SHOES, "Туфли на каблуке", 1, 320, "", 0x161616, 0x7A1F2B, 0xE8DCC0, 0xC62828);
        as(2, CA | BU);
        w("flats", Slot.SHOES, "Балетки", 0, 35, "", 0x161616, 0xF4C2C2, 0xE8DCC0, 0x1A2A50);
        as(0, ST | FO);
        w("lux_sneakers", Slot.SHOES, "Кроссовки «Люкс»", 1, 420, "", 0xF4F4F4, 0x141414);
        // ================================================================ головные уборы
        as(0, ST | CA | SP);
        w("cap", Slot.HAT, "Кепка", 0, 15, "", 0xC62828, 0x161616, 0xF2F2F2, 0x1E50B4, 0x2E7D32);
        as(0, ST);
        w("cap_back", Slot.HAT, "Кепка козырьком назад", 0, 15, "", 0x161616, 0xC62828, 0xF2F2F2);
        as(0, CA | WI | ST);
        w("beanie", Slot.HAT, "Шапка", 0, 18, "", 0x1B1B1B, 0x8A8A8A, 0xF2C230, 0xC62828, 0x1A2A50);
        as(0, ST | BI);
        w("bandana", Slot.HAT, "Бандана", 0, 12, "", 0xC62828, 0x1E50B4, 0x161616, 0x2E7D32);
        as(0, ST);
        w("gold_cap", Slot.HAT, "Кепка «Золото»", 1, 220, "", 0xD4AF37, 0x161616);
        as(0, FO);
        w("fedora", Slot.HAT, "Шляпа-федора", 1, 380, "", 0x161616, 0xEDEDED, 0x6A4A30);
        as(0, BE);
        w("sunhat", Slot.HAT, "Шляпа от солнца", 0, 40, "", 0xE8DCC0, 0xF2F2F2, 0x161616);
        as(0, BE | ST | CA);
        w("bucket", Slot.HAT, "Панама", 0, 25, "", 0xE8DCC0, 0x161616, 0x4A5530, 0x16A3A8);
        as(0, SP);
        w("headband", Slot.HAT, "Повязка", 0, 10, "", 0xF2F2F2, 0xC62828, 0x161616, 0xE0407A);
        as(0, WI);
        w("ushanka", Slot.HAT, "Ушанка", 0, 45, "", 0x3A2A1C, 0x161616, 0x8A8A8A);
        as(0, WO);
        w("hardhat", Slot.HAT, "Каска", 0, 20, "", 0xF2C032, 0xF2F2F2, 0xF57C00);
        as(0, FO | CA);
        w("beret", Slot.HAT, "Берет", 1, 150, "", 0x161616, 0x7A1F2B, 0x1A2A50);
        // ================================================================ очки
        as(0, CA | BU);
        w("frame", Slot.GLASSES, "Очки в оправе", 0, 25, "", 0x1A1A1A, 0x6B4A2E);
        as(0, ST | BE | BI);
        w("shades", Slot.GLASSES, "Тёмные очки", 0, 35, "", 0x111111);
        as(0, FO | BU);
        w("gold", Slot.GLASSES, "Золотая оправа", 1, 260, "", 0xD4AF37);
        as(0, BI | ST | BE);
        w("aviator", Slot.GLASSES, "Авиаторы", 1, 320, "", 0xD4AF37, 0xC0C0C0);
        as(0, CA | FO);
        w("round", Slot.GLASSES, "Круглые очки", 0, 40, "", 0x1A1A1A, 0xC0C0C0, 0x6B4A2E);
        as(2, FO | BE | CA);
        w("cateye", Slot.GLASSES, "Очки «кошачий глаз»", 1, 240, "", 0x161616, 0x7A1F2B, 0xF2F2F2);
        as(0, SP);
        w("sport", Slot.GLASSES, "Спортивные очки", 0, 55, "", 0x161616, 0x1E50B4, 0xF57C00);
        // ================================================================ шея
        as(0, ST | BI);
        w("chain_gold", Slot.NECK, "Золотая цепь", 1, 600, "", 0xD4AF37);
        w("chain_silver", Slot.NECK, "Серебряная цепь", 0, 120, "", 0xC0C0C0);
        as(0, FO | CA);
        w("pendant", Slot.NECK, "Кулон", 1, 180, "", 0xD4AF37, 0xC0C0C0);
        as(0, WI | CA);
        w("scarf", Slot.NECK, "Шарф", 0, 30, "", 0xC62828, 0x8A8A8A, 0xF2C230, 0x1A2A50, 0xE8DCC0);
        as(0, ST | BI);
        w("neck_bandana", Slot.NECK, "Платок на шею", 0, 15, "", 0xC62828, 0x1A2A50, 0x161616);
        // ================================================================ сумки
        as(0, CA | ST | SP);
        w("backpack", Slot.BAG, "Рюкзак", 0, 40, "", 0x161616, 0x1A2A50, 0xC62828, 0x4A5530, 0xF2C230);
        as(0, CA | BU);
        w("messenger", Slot.BAG, "Сумка через плечо", 0, 55, "", 0x6B4A2E, 0x161616, 0x4A5530);
        as(2, FO | BU | CA);
        w("handbag", Slot.BAG, "Сумочка", 1, 380, "", 0x161616, 0x7A1F2B, 0xE8DCC0, 0xC62828);
        as(0, ST | SP);
        w("fanny", Slot.BAG, "Поясная сумка", 0, 30, "", 0x161616, 0xC62828, 0x16A3A8);
        as(2, CA | BE);
        w("tote", Slot.BAG, "Шоппер", 0, 20, "", 0xE8DCC0, 0x161616, 0x2E5E3A);
        // ================================================================ часы и браслеты
        as(0, CA | BU);
        w("watch", Slot.WRIST, "Часы", 0, 60, "", 0x161616, 0x6B4A2E);
        as(0, FO | BU);
        w("gold_watch", Slot.WRIST, "Золотые часы", 1, 900, "", 0xD4AF37);
        as(0, CA | BE | ST);
        w("bracelet", Slot.WRIST, "Браслеты", 0, 25, "", 0xC62828, 0xD4AF37, 0x161616);
        as(0, SP);
        w("smartwatch", Slot.WRIST, "Смарт-часы", 0, 150, "", 0x161616, 0xF2F2F2, 0xE0407A);
        as(0, 0);
    }

    public static Wear wear(String id) {
        for (Wear w : WEAR) if (w.id().equals(id)) return w;
        return null;
    }

    public static List<Wear> of(Slot slot, int tier) {
        List<Wear> l = new ArrayList<>();
        for (Wear w : WEAR) if (w.slot() == slot && (tier < 0 || w.tier() == tier)) l.add(w);
        return l;
    }

    /** Вещи слота для пола (и «унисекс»). */
    public static List<Wear> of(Slot slot, int tier, boolean female) {
        List<Wear> l = new ArrayList<>();
        for (Wear w : of(slot, tier)) if (w.fits(female)) l.add(w);
        return l;
    }

    public static int count(Slot... slots) {
        int n = 0;
        for (Wear w : WEAR) for (Slot s : slots) if (w.slot() == s) n++;
        return n;
    }

    /** Вид верха: платье (своя юбка поверх ног). */
    public static boolean dress(String id) {
        return id.equals("sundress") || id.equals("dress") || id.equals("office_dress");
    }

    /** Юбка (низ) — ноги открыты, юбка — объёмная. */
    public static boolean skirt(String id) {
        return id.startsWith("skirt");
    }

    /** Вещи, которые есть у каждого с самого начала. */
    public static final List<String> STARTER = List.of("tee#0", "tee#1", "jeans#0", "sneakers#0");

    // ---------------------------------------------------------------- цирюльня и лицо
    /** Вариант: id, название, цена, цвет (для образцов) и пол (0 любой, 1 мужской, 2 женский — для «случайно»). */
    public record Option(String id, String name, int price, int color, int gender) {
        public Option(String id, String name, int price, int color) {
            this(id, name, price, color, 0);
        }
    }

    public static final List<Option> HAIR = List.of(
            new Option("bald", "Налысо", 20, 0, 1), new Option("buzz", "Под машинку", 25, 0, 0), new Option("crew", "Короткая", 30, 0, 1),
            new Option("short", "Классика", 40, 0, 1), new Option("side_part", "Пробор", 50, 0, 0), new Option("slick", "Зачёс назад", 70, 0, 1),
            new Option("undercut", "Андеркат", 80, 0, 0), new Option("quiff", "Кок", 70, 0, 1), new Option("curly_short", "Короткие кудри", 60, 0, 0),
            new Option("curly", "Кудри", 80, 0, 0), new Option("afro", "Афро", 120, 0, 0), new Option("mohawk", "Ирокез", 120, 0, 1),
            new Option("dreads", "Дреды", 150, 0, 0), new Option("cornrows", "Косички-колоски", 140, 0, 0), new Option("man_bun", "Пучок на макушке", 70, 0, 1),
            new Option("pixie", "Пикси", 70, 0, 2), new Option("bob", "Каре", 70, 0, 2), new Option("long", "Длинные", 90, 0, 2),
            new Option("wavy", "Волнистые", 110, 0, 2), new Option("ponytail", "Хвост", 80, 0, 2), new Option("high_pony", "Высокий хвост", 90, 0, 2),
            new Option("braids", "Две косы", 130, 0, 2), new Option("bun", "Пучок", 80, 0, 2));

    /** Причёски, у которых есть длина (короче/длиннее). */
    public static boolean hasLength(String style) {
        return switch (style) {
            case "long", "wavy", "bob", "ponytail", "high_pony", "braids", "dreads" -> true;
            default -> false;
        };
    }

    /** Причёски с чёлкой (её можно убрать). */
    public static boolean hasFringe(String style) {
        return switch (style) {
            case "short", "side_part", "pixie", "bob", "long", "wavy", "ponytail", "braids", "bun", "curly_short" -> true;
            default -> false;
        };
    }

    public static final List<Option> HAIR_COLORS = List.of(
            new Option("black", "Чёрный", 30, 0x1A1A1A), new Option("dark", "Тёмно-каштановый", 30, 0x3E2723),
            new Option("brown", "Каштановый", 30, 0x6B3D1F), new Option("ginger", "Рыжий", 30, 0xD2691E),
            new Option("blond", "Блонд", 30, 0xF2D16B), new Option("platinum", "Платина", 40, 0xE8E4D8),
            new Option("gray", "Седой", 30, 0x9E9E9E), new Option("pink", "Неон: розовый", 120, 0xE91E63),
            new Option("blue", "Неон: синий", 120, 0x1E88E5), new Option("green", "Неон: зелёный", 120, 0x43A047),
            new Option("purple", "Неон: фиолетовый", 120, 0x8E24AA),
            // 0.9.6
            new Option("auburn", "Медный", 40, 0x8A3A1E), new Option("dark_blond", "Русый", 30, 0xA07A4A),
            new Option("honey", "Медовый", 40, 0xC8913A), new Option("white", "Белый", 50, 0xF4F2EE),
            new Option("cherry", "Вишнёвый", 90, 0x7A1424), new Option("teal", "Бирюзовый", 120, 0x1C9A9A));
    /** Натуральные цвета (для жителей и «случайно»). */
    public static final int[] NATURAL_HAIR = {0, 1, 2, 3, 4, 5, 6, 11, 12, 13};

    public static final List<Option> BEARDS = List.of(
            new Option("none", "Гладко выбрит", 10, 0), new Option("stubble", "Щетина", 15, 0), new Option("mustache", "Усы", 30, 0),
            new Option("goatee", "Эспаньолка", 40, 0), new Option("chin", "Шкиперская", 45, 0), new Option("beard", "Борода", 50, 0),
            new Option("full", "Борода с усами", 60, 0));

    /** 10 тонов кожи, от светлого к тёмному (0.9.5 и раньше — 6, см. {@link Appearance#fromNbt}). */
    public static final int[] SKIN_TONES = {0xF9DCC4, 0xF6D2B8, 0xEFC6A4, 0xD8A982, 0xC99067, 0xB47A52, 0xA8714A, 0x8A5636, 0x6B4429, 0x4A2E1C};
    public static final int[] OLD_TONE = {1, 2, 3, 4, 6, 8};

    public static final List<Option> EYE_COLORS = List.of(
            new Option("dark", "Тёмно-карие", 0, 0x3B2314), new Option("brown", "Карие", 0, 0x6B4423), new Option("hazel", "Ореховые", 0, 0x8E7A3A),
            new Option("amber", "Янтарные", 0, 0xB07A2A), new Option("green", "Зелёные", 0, 0x4E8A4A), new Option("blue", "Голубые", 0, 0x3E6FB0),
            new Option("gray", "Серые", 0, 0x7A8A99), new Option("black", "Чёрные", 0, 0x161616));
    public static final List<Option> EYE_SHAPES = List.of(new Option("round", "Круглые", 0, 0), new Option("almond", "Миндаль", 0, 0),
            new Option("narrow", "Узкие", 0, 0), new Option("wide", "Большие", 0, 0));
    public static final List<Option> BROWS = List.of(new Option("thin", "Тонкие", 0, 0), new Option("normal", "Обычные", 0, 0),
            new Option("thick", "Густые", 0, 0), new Option("arched", "Дугой", 0, 0), new Option("bushy", "Широкие", 0, 0));
    public static final List<Option> MOUTHS = List.of(new Option("neutral", "Обычные", 0, 0), new Option("full", "Пухлые", 0, 0),
            new Option("thin", "Тонкие", 0, 0), new Option("smile", "Улыбка", 0, 0));
    /** Помада и лак: 0 — нет. */
    public static final List<Option> LIP_COLORS = List.of(new Option("none", "Нет", 0, 0), new Option("nude", "Нюд", 0, 0xC0806A),
            new Option("rose", "Розовая", 0, 0xD46A82), new Option("red", "Красная", 0, 0xC0182A), new Option("berry", "Ягодная", 0, 0x8A1A4A),
            new Option("coral", "Коралл", 0, 0xF0705A), new Option("plum", "Слива", 0, 0x5A1A3A));
    public static final List<Option> NAIL_COLORS = List.of(new Option("none", "Нет", 0, 0), new Option("red", "Красный", 0, 0xC0182A),
            new Option("pink", "Розовый", 0, 0xF080A8), new Option("black", "Чёрный", 0, 0x161616), new Option("white", "Белый", 0, 0xF4F4F4),
            new Option("nude", "Нюд", 0, 0xE0B0A0), new Option("blue", "Синий", 0, 0x2A5AC0), new Option("gold", "Золото", 0, 0xD4AF37));
    public static final List<Option> EARRINGS = List.of(new Option("none", "Нет", 0, 0), new Option("studs", "Гвоздики", 0, 0xE0E0E0),
            new Option("hoops", "Кольца", 0, 0xD4AF37), new Option("drops", "Подвески", 0, 0xD4AF37));
    public static final List<Option> NOSE = List.of(new Option("none", "Нет", 0, 0), new Option("stud", "Гвоздик", 0, 0xE0E0E0),
            new Option("ring", "Колечко", 0, 0xC0C0C0));
    public static final List<Option> SCARS = List.of(new Option("none", "Нет", 0, 0), new Option("brow", "Бровь", 0, 0),
            new Option("cheek", "Щека", 0, 0), new Option("lip", "Губа", 0, 0));
    public static final List<Option> MOLES = List.of(new Option("none", "Нет", 0, 0), new Option("cheek", "На щеке", 0, 0),
            new Option("lip", "Над губой", 0, 0));
    public static final List<Option> FACE_TATS = List.of(new Option("none", "Нет", 0, 0), new Option("star", "Звёздочка", 0, 0),
            new Option("heart", "Сердечко", 0, 0), new Option("dots", "Точки", 0, 0));
    public static final List<Option> TATS = List.of(new Option("none", "Нет", 0, 0), new Option("tribal", "Узор", 0, 0),
            new Option("rose", "Роза", 0, 0), new Option("anchor", "Якорь", 0, 0), new Option("sleeve", "Рукав", 0, 0),
            new Option("script", "Надпись", 0, 0), new Option("stars", "Звёзды", 0, 0));
    /** Места тату: левая рука, правая рука, шея, кисть. */
    public static final String[] TAT_SLOTS = {"Левая рука", "Правая рука", "Шея", "Кисть"};
    public static final List<Option> PATTERNS = List.of(new Option("none", "Однотон", 0, 0), new Option("stripes", "Полоска", 0, 0),
            new Option("check", "Клетка", 0, 0), new Option("camo", "Камуфляж", 0, 0), new Option("print", "Принт", 0, 0));
    public static final String[] GRIME = {"Чистая", "Ношеная", "Грязная", "Очень грязная"};
    public static final String[] BUILDS = {"Худое", "Среднее", "Спортивное", "Плотное"};

    /** Сменить кожу и телосложение: первый раз — бесплатно. */
    public static final int LOOK_PRICE = 300;

    public static int hairColor(int i) {
        return HAIR_COLORS.get(Math.floorMod(i, HAIR_COLORS.size())).color();
    }

    public static int tone(int i) {
        return SKIN_TONES[Math.floorMod(i, SKIN_TONES.length)];
    }

    public static Option find(List<Option> l, String id) {
        for (Option o : l) if (o.id().equals(id)) return o;
        return null;
    }

    public static int indexOf(List<Option> l, String id) {
        for (int i = 0; i < l.size(); i++) if (l.get(i).id().equals(id)) return i;
        return 0;
    }

    // ---------------------------------------------------------------- спортзал
    /** Максимум за игровой день по каждому навыку и предел навыка. */
    public static final int GYM_DAILY = 6, STAT_MAX = 100;
}
