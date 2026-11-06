package com.yompix.novograd.life;

import java.util.ArrayList;
import java.util.List;

/**
 * Каталог стиля: одежда двух магазинов («Шмот-Базар» — дёшево, бутик «Бархат» — дорого), услуги
 * цирюльни «Острая бритва». Цены одни и те же у сервера и клиента.
 */
public final class StyleCatalog {
    private StyleCatalog() {}

    public static final String CHEAP_NAME = "Шмот-Базар", LUX_NAME = "Бутик «Бархат»", BARBER_NAME = "Цирюльня «Острая бритва»",
            GYM_NAME = "Зал «Железо»";

    public enum Slot {
        TOP("Верх"), PANTS("Низ"), SHOES("Обувь"), HAT("Головные уборы"), GLASSES("Очки");

        public final String title;

        Slot(String title) {
            this.title = title;
        }
    }

    /** Вещь: id — он же вид для отрисовки; tier 0 — «Шмот-Базар», 1 — «Бархат»; colors — варианты цвета (RGB). */
    public record Wear(String id, Slot slot, String name, int tier, int price, int[] colors, String desc) {
        public int color(int i) {
            return colors[Math.floorMod(i, colors.length)];
        }
    }

    public static final List<Wear> WEAR = new ArrayList<>();

    private static void w(String id, Slot slot, String name, int tier, int price, String desc, int... colors) {
        WEAR.add(new Wear(id, slot, name, tier, price, colors, desc));
    }

    static {
        // ---- верх
        w("tank", Slot.TOP, "Майка-алкоголичка", 0, 10, "Классика двора. Дёшево и сердито.", 0xEDEDED, 0x222222, 0x9E9E9E);
        w("tee", Slot.TOP, "Футболка", 0, 20, "Простая хлопковая футболка.", 0xF2F2F2, 0x1E1E1E, 0xC62828, 0x1A237E, 0x2E7D32, 0xF9A825);
        w("tee_print", Slot.TOP, "Футболка «Новоград»", 0, 35, "С неоновым принтом родного города.", 0x161616, 0xF2F2F2, 0xE0407A);
        w("polo", Slot.TOP, "Поло", 0, 45, "Воротник, пуговицы — почти прилично.", 0xF5F5F5, 0x1A2A50, 0x6FCFB0);
        w("flannel", Slot.TOP, "Клетчатая рубашка", 0, 55, "Фланель в клетку — для района в самый раз.", 0xB71C1C, 0x1E4A8C, 0x2E6B34);
        w("hoodie", Slot.TOP, "Худи", 0, 60, "Капюшон, карман-кенгуру. Лицо в тени.", 0x8A8A8A, 0x1B1B1B, 0x1C2850, 0x5E2B8A);
        w("tracksuit", Slot.TOP, "Олимпийка с лампасами", 0, 80, "Спортивный костюм девяностых: молния и белые полосы.", 0x1E50B4, 0xB0201E, 0x151515, 0x1C6E3A);
        w("denim", Slot.TOP, "Джинсовка", 0, 110, "Джинсовая куртка поверх белой футболки.", 0x3A5F94, 0x6E8FB8);
        w("hawaii", Slot.TOP, "Гавайская рубашка", 1, 350, "Шёлк, цветы и пальмы — как у курортного босса.", 0x16A3A8, 0xC62838, 0xF2C230);
        w("leather", Slot.TOP, "Кожаная куртка", 1, 480, "Настоящая кожа. С ней тебя запомнят.", 0x181412, 0x5A3420, 0x5C1420);
        w("bomber", Slot.TOP, "Бомбер", 1, 560, "Атласный бомбер с трикотажными манжетами.", 0x4A5530, 0x161616, 0xE88AB4);
        w("blazer", Slot.TOP, "Пиджак", 1, 750, "Пиджак нараспашку на футболку — стиль ночного побережья.", 0xF2F0EA, 0xE8D8B8, 0x8EC8EC, 0xF2A6C0);
        w("suit", Slot.TOP, "Костюм-тройка", 1, 1400, "Белая рубашка, галстук, жилет. Для серьёзных людей.", 0x161618, 0x3A3C42, 0x18244A, 0xEDEDED);
        w("coat", Slot.TOP, "Пальто", 1, 1100, "Длинное пальто: дождь, неон и тайны.", 0xB08A5A, 0x161616, 0x55585E);
        // ---- низ
        w("jeans", Slot.PANTS, "Джинсы", 0, 30, "Синие, крепкие, с ремнём.", 0x2F4A7A, 0x1C1C22, 0x7A98C0);
        w("sweats", Slot.PANTS, "Спортивные штаны", 0, 25, "С белым лампасом — к олимпийке.", 0x707070, 0x161616, 0x1C2850);
        w("shorts", Slot.PANTS, "Шорты", 0, 15, "Жарко? Шорты.", 0xB09868, 0x3A5F94, 0x222222);
        w("cargo", Slot.PANTS, "Камуфляжные карго", 0, 45, "Камуфляж и большие карманы.", 0x4E5A34, 0xB8A57A, 0x5E6066);
        w("slacks", Slot.PANTS, "Брюки со стрелками", 1, 300, "Идеальная стрелка и тонкий ремень.", 0x161618, 0x3A3C42, 0xF0EEE8, 0xE0D0B0);
        w("leather_pants", Slot.PANTS, "Кожаные брюки", 1, 520, "Блестят под неоном.", 0x141212, 0x4A2C1C);
        // ---- обувь
        w("canvas", Slot.SHOES, "Кеды", 0, 20, "Белый носок, резиновая подошва.", 0xF2F2F2, 0x1A1A1A, 0xC62828);
        w("sneakers", Slot.SHOES, "Кроссовки", 0, 40, "Беговые, с белой подошвой.", 0xF0F0F0, 0x1A1A1A, 0x1E50B4, 0xC62828);
        w("slides", Slot.SHOES, "Шлёпанцы", 0, 8, "Пляж, двор, магазин у дома.", 0x161616, 0x1E50B4);
        w("loafers", Slot.SHOES, "Лаковые туфли", 1, 350, "Блестят так, что видно отражение.", 0x111111, 0x5A3420, 0xF2F2F2);
        w("boots", Slot.SHOES, "Ботинки «Челси»", 1, 280, "Кожаные, с резинкой сбоку.", 0x1A1614, 0x9A6A3A);
        w("lux_sneakers", Slot.SHOES, "Кроссовки «Люкс»", 1, 420, "Белые с золотом. Не наступай в лужу.", 0xF4F4F4, 0x141414);
        // ---- головные уборы
        w("cap", Slot.HAT, "Кепка", 0, 15, "Козырёк вперёд.", 0xC62828, 0x161616, 0xF2F2F2, 0x1E50B4, 0x2E7D32);
        w("beanie", Slot.HAT, "Шапка", 0, 18, "Вязаная шапка с помпоном.", 0x1B1B1B, 0x8A8A8A, 0xF2C230, 0xC62828);
        w("bandana", Slot.HAT, "Бандана", 0, 12, "Повязать на голову — и ты с района.", 0xC62828, 0x1E50B4, 0x161616, 0x2E7D32);
        w("gold_cap", Slot.HAT, "Кепка «Золото»", 1, 220, "Кепка с золотым козырьком.", 0xD4AF37, 0x161616);
        w("fedora", Slot.HAT, "Шляпа-федора", 1, 380, "Шляпа гангстера старой школы.", 0x161616, 0xEDEDED, 0x6A4A30);
        // ---- очки
        w("frame", Slot.GLASSES, "Очки в оправе", 0, 25, "Умный вид за $25.", 0x1A1A1A);
        w("shades", Slot.GLASSES, "Тёмные очки", 0, 35, "Чтобы не узнали.", 0x111111);
        w("gold", Slot.GLASSES, "Золотая оправа", 1, 260, "Тонкое золото, прозрачные стёкла.", 0xD4AF37);
        w("aviator", Slot.GLASSES, "Авиаторы", 1, 320, "Золото и зеркальные стёкла.", 0xD4AF37);
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

    /** Вещи, которые есть у каждого с самого начала. */
    public static final List<String> STARTER = List.of("tee#0", "tee#1", "jeans#0", "sneakers#0");

    // ---------------------------------------------------------------- цирюльня
    public record Option(String id, String name, int price, int color) {}

    public static final List<Option> HAIR = List.of(
            new Option("buzz", "Под машинку", 25, 0), new Option("short", "Классика", 40, 0), new Option("slick", "Зачёс назад", 70, 0),
            new Option("curly", "Кудри", 80, 0), new Option("mohawk", "Ирокез", 120, 0), new Option("afro", "Афро", 120, 0),
            new Option("long", "Длинные", 90, 0), new Option("bob", "Каре", 70, 0), new Option("ponytail", "Хвост", 80, 0),
            new Option("bald", "Налысо", 20, 0));

    public static final List<Option> HAIR_COLORS = List.of(
            new Option("black", "Чёрный", 30, 0x1A1A1A), new Option("dark", "Тёмно-каштановый", 30, 0x3E2723),
            new Option("brown", "Каштановый", 30, 0x6B3D1F), new Option("ginger", "Рыжий", 30, 0xD2691E),
            new Option("blond", "Блонд", 30, 0xF2D16B), new Option("platinum", "Платина", 40, 0xE8E4D8),
            new Option("gray", "Седой", 30, 0x9E9E9E), new Option("pink", "Неон: розовый", 120, 0xE91E63),
            new Option("blue", "Неон: синий", 120, 0x1E88E5), new Option("green", "Неон: зелёный", 120, 0x43A047),
            new Option("purple", "Неон: фиолетовый", 120, 0x8E24AA));

    public static final List<Option> BEARDS = List.of(
            new Option("none", "Гладко выбрит", 10, 0), new Option("stubble", "Щетина", 15, 0), new Option("mustache", "Усы", 30, 0),
            new Option("goatee", "Эспаньолка", 40, 0), new Option("beard", "Борода", 50, 0), new Option("full", "Борода с усами", 60, 0));

    public static final int[] SKIN_TONES = {0xF6D2B8, 0xEFC6A4, 0xD8A982, 0xC99067, 0xA8714A, 0x6B4429};
    /** Сменить кожу и телосложение: первый раз — бесплатно. */
    public static final int LOOK_PRICE = 300;

    public static int hairColor(int i) {
        return HAIR_COLORS.get(Math.floorMod(i, HAIR_COLORS.size())).color();
    }

    public static Option find(List<Option> l, String id) {
        for (Option o : l) if (o.id().equals(id)) return o;
        return null;
    }

    // ---------------------------------------------------------------- спортзал
    /** Максимум за игровой день по каждому навыку и предел навыка. */
    public static final int GYM_DAILY = 6, STAT_MAX = 100;
}
