package com.yompix.lifecraft.life;

import com.yompix.lifecraft.ModContent;
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
            new Entry("Еда", "Салат (−вес)", () -> ModContent.SALAD, 1, 6, 1),
            new Entry("Еда", "Пицца", () -> ModContent.PIZZA, 1, 10, 1),
            new Entry("Еда", "Хлеб ×3", () -> Items.BREAD, 3, 5, 1),
            new Entry("Еда", "Торт", () -> Items.CAKE, 1, 25, 1),
            new Entry("Еда", "Яблоки ×5", () -> Items.APPLE, 5, 6, 1),
            new Entry("Учёба", "Учебник (подсказка на уроке)", () -> ModContent.TEXTBOOK, 1, 15, 1),
            new Entry("Учёба", "Тетради ×4", () -> Items.BOOK, 4, 6, 1),
            new Entry("Одежда", "Костюм для выпускного", () -> ModContent.SUIT, 1, 60, 1),
            new Entry("Одежда", "Кожаная куртка", () -> Items.LEATHER_CHESTPLATE, 1, 45, 1),
            new Entry("Одежда", "Кепка", () -> Items.LEATHER_HELMET, 1, 15, 1),
            new Entry("Одежда", "Джинсы", () -> Items.LEATHER_LEGGINGS, 1, 30, 1),
            new Entry("Одежда", "Кроссовки", () -> Items.LEATHER_BOOTS, 1, 25, 1),
            new Entry("Дом", "Телевизор", () -> ModContent.TV.asItem(), 1, 150, 1),
            new Entry("Дом", "Компьютер", () -> ModContent.COMPUTER.asItem(), 1, 400, 1),
            new Entry("Дом", "Парта", () -> ModContent.SCHOOL_DESK.asItem(), 1, 40, 1),
            new Entry("Дом", "Кровать", () -> Items.LIGHT_BLUE_BED, 1, 60, 1),
            new Entry("Дом", "Почтовый ящик", () -> ModContent.MAILBOX.asItem(), 1, 30, 1),
            new Entry("Дом", "Уличный фонарь", () -> ModContent.STREET_LAMP.asItem(), 1, 35, 1),
            new Entry("Дом", "Игровой автомат", () -> ModContent.SLOT_MACHINE.asItem(), 1, 900, 12),
            new Entry("Транспорт", "Электроскутер (с любого возраста)", () -> ModContent.SCOOTER_ITEM, 1, 120, 1),
            new Entry("Транспорт", "Машина: красная", () -> ModContent.CARS[0], 1, 700, 12),
            new Entry("Транспорт", "Машина: синяя", () -> ModContent.CARS[1], 1, 700, 12),
            new Entry("Транспорт", "Машина: жёлтая", () -> ModContent.CARS[2], 1, 800, 12),
            new Entry("Транспорт", "Машина: белая", () -> ModContent.CARS[4], 1, 900, 12),
            new Entry("Транспорт", "Машина: чёрная (премиум)", () -> ModContent.CARS[3], 1, 1500, 12),
            new Entry("Транспорт", "Такси", () -> ModContent.CARS[5], 1, 750, 12),
            new Entry("Питомцы", "Собака (верный друг)", () -> Items.BONE, 1, 150, 1),
            new Entry("Питомцы", "Кошка", () -> Items.COD, 1, 120, 1),
            new Entry("Питомцы", "Попугай", () -> Items.WHEAT_SEEDS, 1, 90, 1),
            new Entry("Разное", "Банковская карта", () -> ModContent.BANK_CARD, 1, 20, 1),
            new Entry("Разное", "Телефон (запасной)", () -> ModContent.PHONE, 1, 120, 1),
            new Entry("Разное", "Цветы", () -> Items.POPPY, 3, 6, 1),
            new Entry("Разное", "Удочка", () -> Items.FISHING_ROD, 1, 30, 1));

    /** delivery = покупка через телефон, +20% за доставку. */
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
        Life.message(p, "Зоомагазин", "Знакомься: " + pet.getCustomName().getString() + "! Присядь и нажми на питомца, чтобы он сидел или шёл за тобой.");
    }

    static void buy(ServerPlayerEntity p, int index, boolean delivery) {
        if (index < 0 || index >= CATALOG.size()) return;
        Entry e = CATALOG.get(index);
        if (Life.get(p).edu < e.minEdu()) {
            p.sendMessage(Text.literal("Это можно купить только взрослым (после школы)").formatted(Formatting.RED), true);
            return;
        }
        if (!Life.pay(p, price(e, delivery, CityLife.event(p.getServerWorld())), e.name())) return;
        if (e.category().equals("Питомцы")) {
            pet(p, e.name());
            return;
        }
        if (e.category().equals("Транспорт")) Achievements.give(p, "car");
        School.give(p, new ItemStack(e.item().get(), e.count()));
        Life.sound(p, ModContent.CASH, 1.2f);
    }
}
