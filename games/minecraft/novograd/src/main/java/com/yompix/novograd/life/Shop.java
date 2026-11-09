package com.yompix.novograd.life;

import com.yompix.novograd.ModContent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.function.Supplier;

/** Магазин: еда, учёба, техника для дома, машины, мебель. Цены одинаковы у сервера и клиента. */
public final class Shop {
    private Shop() {}

    public record Entry(String category, String name, Supplier<Item> item, int count, int price, int minEdu) {}

    public static final List<Entry> CATALOG = List.of(
            new Entry("Еда", "Бургер", () -> ModContent.BURGER, 1, 8, 1),
            new Entry("Еда", "Кола", () -> ModContent.COLA, 1, 4, 1),
            new Entry("Оружие", "Бита", () -> ModContent.BAT, 1, 80, LifeData.SCHOOL_END + 1),
            new Entry("Оружие", "Пистолет", () -> ModContent.PISTOL, 1, 400, LifeData.SCHOOL_END + 1),
            new Entry("Оружие", "Автомат", () -> ModContent.SMG, 1, 1200, LifeData.SCHOOL_END + 1),
            new Entry("Оружие", "Нож", () -> com.yompix.novograd.gta.Arsenal.KNIFE, 1, 150, LifeData.SCHOOL_END + 1),
            new Entry("Тяжёлое", "Дробовик", () -> com.yompix.novograd.gta.Arsenal.SHOTGUN, 1, 1600, LifeData.SCHOOL_END + 1),
            new Entry("Тяжёлое", "Автомат «АК»", () -> com.yompix.novograd.gta.Arsenal.RIFLE, 1, 2600, LifeData.SCHOOL_END + 1),
            new Entry("Тяжёлое", "Снайперская винтовка", () -> com.yompix.novograd.gta.Arsenal.SNIPER, 1, 4200, LifeData.SCHOOL_END + 1),
            new Entry("Тяжёлое", "Граната", () -> com.yompix.novograd.gta.Arsenal.GRENADE, 1, 250, LifeData.SCHOOL_END + 1),
            new Entry("Тяжёлое", "Коктейль Молотова", () -> com.yompix.novograd.gta.Arsenal.MOLOTOV, 1, 140, LifeData.SCHOOL_END + 1),
            new Entry("Снаряжение", "Патроны ×30", () -> ModContent.AMMO, 30, 60, LifeData.SCHOOL_END + 1),
            new Entry("Снаряжение", "Патроны ×64 (выгодно)", () -> ModContent.AMMO, 64, 115, LifeData.SCHOOL_END + 1),
            new Entry("Снаряжение", "Бронежилет", () -> com.yompix.novograd.gta.Arsenal.ARMOR, 1, 650, LifeData.SCHOOL_END + 1),
            new Entry("Снаряжение", "Аптечка", () -> com.yompix.novograd.gta.Arsenal.MEDKIT, 1, 70, LifeData.SCHOOL_END + 1),
            new Entry("Еда", "Салат", () -> ModContent.SALAD, 1, 6, 1),
            new Entry("Еда", "Пицца", () -> ModContent.PIZZA, 1, 10, 1),
            new Entry("Еда", "Хлеб ×3", () -> Items.BREAD, 3, 5, 1),
            new Entry("Еда", "Торт", () -> Items.CAKE, 1, 25, 1),
            new Entry("Еда", "Яблоки ×5", () -> Items.APPLE, 5, 6, 1),
            new Entry("Одежда", "Деловой костюм", () -> ModContent.SUIT, 1, 60, 1),
            new Entry("Одежда", "Кожаная куртка", () -> Items.LEATHER_CHESTPLATE, 1, 45, 1),
            new Entry("Одежда", "Кепка", () -> Items.LEATHER_HELMET, 1, 15, 1),
            new Entry("Одежда", "Джинсы", () -> Items.LEATHER_LEGGINGS, 1, 30, 1),
            new Entry("Одежда", "Кроссовки", () -> Items.LEATHER_BOOTS, 1, 25, 1),
            new Entry("Дом", "Телевизор", () -> ModContent.TV.asItem(), 1, 150, 1),
            new Entry("Дом", "Компьютер", () -> ModContent.COMPUTER.asItem(), 1, 400, 1),
            new Entry("Дом", "Письменный стол", () -> ModContent.SCHOOL_DESK.asItem(), 1, 40, 1),
            new Entry("Дом", "Кровать", () -> Items.LIGHT_BLUE_BED, 1, 60, 1),
            new Entry("Дом", "Почтовый ящик", () -> ModContent.MAILBOX.asItem(), 1, 30, 1),
            new Entry("Дом", "Уличный фонарь", () -> ModContent.STREET_LAMP.asItem(), 1, 35, 1),
            new Entry("Дом", "Игровой автомат", () -> ModContent.SLOT_MACHINE.asItem(), 1, 900, 12),
            new Entry("Транспорт", "Электроскутер", () -> ModContent.SCOOTER_ITEM, 1, 120, 1),
            new Entry("Транспорт", "Седан (красный)", () -> ModContent.CARS[0], 1, 700, 12),
            new Entry("Транспорт", "Хэтчбек (синий)", () -> ModContent.CARS[1], 1, 650, 12),
            new Entry("Транспорт", "Фургон (белый)", () -> ModContent.CARS[4], 1, 900, 12),
            new Entry("Транспорт", "Пикап", () -> ModContent.CARS[8], 1, 1100, 12),
            new Entry("Транспорт", "Внедорожник", () -> ModContent.CARS[7], 1, 1300, 12),
            new Entry("Транспорт", "Спорткупе (жёлтое)", () -> ModContent.CARS[2], 1, 1400, 12),
            new Entry("Транспорт", "Маслкар", () -> ModContent.CARS[9], 1, 1600, 12),
            new Entry("Транспорт", "Люкс-седан (чёрный, премиум)", () -> ModContent.CARS[3], 1, 1500, 12),
            new Entry("Транспорт", "Лимузин", () -> ModContent.CARS[10], 1, 3000, 12),
            new Entry("Транспорт", "Такси", () -> ModContent.CARS[5], 1, 750, 12),
            // 0.4: новые машины (редкие — по ходу сюжета, см. buy)
            new Entry("Транспорт", "Малолитражка «Кроха»", () -> ModContent.CARS[16], 1, 480, 12),
            new Entry("Транспорт", "Багги (бездорожье, горы, Пустошь)", () -> ModContent.CARS[15], 1, 2400, 12),
            new Entry("Транспорт", "Лоурайдер (акт II)", () -> ModContent.CARS[14], 1, 3800, 12),
            new Entry("Транспорт", "Классика 70-х (акт II)", () -> ModContent.CARS[13], 1, 4500, 12),
            new Entry("Транспорт", "Суперкар «Молния» (акт IV)", () -> ModContent.CARS[12], 1, 12000, 12),
            new Entry("Питомцы", "Собака (сторожевая)", () -> Items.BONE, 1, 150, 1),
            new Entry("Питомцы", "Кошка", () -> Items.COD, 1, 120, 1),
            new Entry("Питомцы", "Попугай", () -> Items.WHEAT_SEEDS, 1, 90, 1),
            new Entry("Разное", "Банковская карта", () -> ModContent.BANK_CARD, 1, 20, 1),
            new Entry("Разное", "Телефон (запасной)", () -> ModContent.PHONE, 1, 120, 1),
            new Entry("Разное", "Цветы", () -> Items.POPPY, 3, 6, 1),
            new Entry("Разное", "Удочка", () -> Items.FISHING_ROD, 1, 30, 1),
            new Entry("Разное", "Аптечка", () -> com.yompix.novograd.gta.Arsenal.MEDKIT, 1, 90, 1),
            // новые стволы «Калибра» (в конце — номера старых товаров не сдвигаются); допуск по сюжету — Armory.locked
            new Entry("Оружие", "Мачете", () -> com.yompix.novograd.gta.Armory.MACHETE, 1, 220, LifeData.SCHOOL_END + 1),
            new Entry("Оружие", "Револьвер «Удав»", () -> com.yompix.novograd.gta.Armory.REVOLVER, 1, 750, LifeData.SCHOOL_END + 1),
            new Entry("Оружие", "«Микро» (ПП)", () -> com.yompix.novograd.gta.Armory.MICRO, 1, 1500, LifeData.SCHOOL_END + 1),
            new Entry("Оружие", "«Орёл» (тяжёлый пистолет)", () -> com.yompix.novograd.gta.Armory.EAGLE, 1, 1900, LifeData.SCHOOL_END + 1),
            new Entry("Тяжёлое", "«Сайга» (автодробовик)", () -> com.yompix.novograd.gta.Armory.SAIGA, 1, 3200, LifeData.SCHOOL_END + 1),
            new Entry("Тяжёлое", "«Карабин»", () -> com.yompix.novograd.gta.Armory.CARBINE, 1, 3600, LifeData.SCHOOL_END + 1),
            new Entry("Тяжёлое", "«Пулемёт»", () -> com.yompix.novograd.gta.Armory.LMG, 1, 7500, LifeData.SCHOOL_END + 1),
            new Entry("Тяжёлое", "«Гранатомёт»", () -> com.yompix.novograd.gta.Armory.RPG, 1, 15000, LifeData.SCHOOL_END + 1),
            new Entry("Снаряжение", "Выстрел ПГ ×2", () -> com.yompix.novograd.gta.Armory.ROCKET, 2, 1200, LifeData.SCHOOL_END + 1));

    /** Разделы оружейного «Калибра»: их продают только там и только лично. */
    public static boolean gunCategory(String category) {
        return category.equals("Оружие") || category.equals("Тяжёлое") || category.equals("Снаряжение");
    }

    /** delivery = покупка через телефон, +20% за доставку. */
    private static final java.util.Map<java.util.UUID, Long> AT_GUNSHOP = new java.util.HashMap<>();

    /** Игрок сейчас у Боба в «Калибре» (окно магазина открыто не больше 2 минут назад) — для Мастерской. */
    public static boolean atGunShop(ServerPlayerEntity p) {
        return p.getServerWorld().getTime() - AT_GUNSHOP.getOrDefault(p.getUuid(), -10000L) <= 2400;
    }

    /** Оружейный «Калибр»: только оружие, только взрослым и только в магазине (не доставкой). */
    public static void openGuns(ServerPlayerEntity p) {
        if (!Life.get(p).adult()) {
            p.sendMessage(Text.literal("Оружейник Боб: «Сейчас не продаю. Заходи позже.»").formatted(Formatting.GOLD), false);
            return;
        }
        AT_GUNSHOP.put(p.getUuid(), p.getServerWorld().getTime());
        NbtCompound n = new NbtCompound();
        n.putBoolean("delivery", false);
        n.putBoolean("guns", true);
        LifeNet.send(p, "open_shop", n);
    }

    public static void open(ServerPlayerEntity p, boolean delivery) {
        NbtCompound n = new NbtCompound();
        n.putBoolean("delivery", delivery);
        LifeNet.send(p, "open_shop", n);
    }

    public static int price(Entry e, boolean delivery) {
        return price(e, delivery, "");
    }

    /** Цена с доставкой (+20%) и ярмаркой (−30%). */
    public static int price(Entry e, boolean delivery, String today) {
        int p = delivery ? e.price() + (e.price() + 4) / 5 : e.price();
        return today.equals("fair") ? Math.max(1, p * 7 / 10) : p;
    }

    /** Питомец появляется рядом уже ручным и с именем. */
    private static void pet(ServerPlayerEntity p, String name) {
        var w = p.getServerWorld();
        net.minecraft.entity.passive.TameableEntity pet = name.startsWith("Собака") ? net.minecraft.entity.EntityType.WOLF.create(w)
                : name.startsWith("Кошка") ? net.minecraft.entity.EntityType.CAT.create(w) : net.minecraft.entity.EntityType.PARROT.create(w);
        if (pet == null) return;
        pet.refreshPositionAndAngles(p.getX() + 1, p.getY(), p.getZ() + 1, p.getYaw(), 0);
        pet.setOwner(p);
        pet.setTamed(true, true);
        String[] names = {"Бобик", "Шарик", "Мурка", "Барсик", "Кеша", "Рекс", "Симба", "Лаки"};
        pet.setCustomName(net.minecraft.text.Text.literal(names[p.getRandom().nextInt(names.length)]));
        pet.setPersistent();
        w.spawnEntity(pet);
        Achievements.give(p, "pet");
        Life.message(p, "Зоомагазин", "Питомец: " + pet.getCustomName().getString() + ". Присядь и нажми на него, чтобы сидел или шёл за тобой.");
    }

    static void buy(ServerPlayerEntity p, int index, boolean delivery) {
        if (index < 0 || index >= CATALOG.size()) return;
        Entry e = CATALOG.get(index);
        if (false) { // ограничений по «образованию» больше нет
            p.sendMessage(Text.literal("Пока недоступно").formatted(Formatting.RED), true);
            return;
        }
        if (gunCategory(e.category()) && (delivery || p.getServerWorld().getTime() - AT_GUNSHOP.getOrDefault(p.getUuid(), -10000L) > 2400)) {
            p.sendMessage(Text.literal("Оружие продают только в «Калибре», лично").formatted(Formatting.RED), true);
            return;
        }
        // тяжёлое оружие — по ходу сюжета: «АК» с акта II, снайперка с акта IV
        var it = e.item().get();
        int act = com.yompix.novograd.gta.Economy.act(Life.get(p));
        if (it == com.yompix.novograd.gta.Arsenal.RIFLE && act < 1 || it == com.yompix.novograd.gta.Arsenal.SNIPER && act < 3) {
            p.sendMessage(Text.literal("Оружейник Боб: «Такой ствол — только проверенным людям. Приходи, когда тебя узнают в городе.»").formatted(Formatting.GOLD), false);
            return;
        }
        String no = com.yompix.novograd.gta.Armory.locked(it, act);
        if (no != null) {
            p.sendMessage(Text.literal(no).formatted(Formatting.GOLD), false);
            return;
        }
        // редкие машины — когда тебя узнают в городе: лоурайдер и классика с акта II, суперкар с акта IV
        if ((it == ModContent.CARS[13] || it == ModContent.CARS[14]) && act < 1 || it == ModContent.CARS[12] && act < 3) {
            p.sendMessage(Text.literal("Автодилер Фрэнк: «Такую машину держу для особых клиентов. Сделай себе имя в городе — и приходи.»").formatted(Formatting.GOLD), false);
            return;
        }
        int cost = price(e, delivery, CityLife.event(p.getServerWorld()));
        // у оружейника берут и грязные наличные (чёрный рынок), в остальных магазинах — только чистые с карты
        if (gunCategory(e.category()) ? !com.yompix.novograd.gta.Economy.payBlack(p, cost, e.name()) : !Life.pay(p, cost, e.name())) return;
        if (e.category().equals("Питомцы")) {
            pet(p, e.name());
            return;
        }
        if (e.category().equals("Транспорт")) Achievements.give(p, "car");
        School.give(p, new ItemStack(e.item().get(), e.count()));
        Life.sound(p, ModContent.CASH, 1.2f);
    }
}
