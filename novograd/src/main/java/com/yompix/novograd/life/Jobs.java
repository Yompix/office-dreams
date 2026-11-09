package com.yompix.novograd.life;

import com.yompix.novograd.ModContent;
import com.yompix.novograd.entity.CarEntity;
import com.yompix.novograd.entity.CitizenEntity;
import com.yompix.novograd.world.TownState;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Работа: курьер, кассир, таксист, программист. Чем выше образование — тем дороже работа. */
public final class Jobs {
    private Jobs() {}

    public enum Job {
        NONE("none", "Без работы", 1, "Выбери работу в телефоне: приложение «Работа»."),
        COURIER("courier", "Курьер", 5, "Возьми посылку у доски вакансий и отнеси в почтовый ящик по адресу. $12–40 за доставку."),
        CASHIER("cashier", "Кассир", 7, "ПКМ по прилавку в магазине: считай покупки клиентов. $6 за верный чек."),
        TAXI("taxi", "Таксист", 12, "Нужна своя машина. Сядь за руль и нажми «Взять заказ» в телефоне. $25–90 за поездку."),
        PROGRAMMER("programmer", "Программист", 16, "Компьютер в офисе: печатай код без ошибок. $22 за строку."),
        PIZZA("pizza", "Курьер пиццы", 5, "Печь в пиццерии «Мама Мия»: возьми горячую пиццу и довези до ящика по адресу. Быстрее — больше чаевых!"),
        DJ("dj", "Диджей", 12, "Вечером в клубе «Неон»: встань за пульт и держи ритм. Чем лучше играешь — тем больше танцуют и платят.");

        public final String id, title, desc;
        public final int minEdu;

        Job(String id, String title, int minEdu, String desc) {
            this.id = id;
            this.title = title;
            this.minEdu = minEdu;
            this.desc = desc;
        }

        public static Job of(String id) {
            for (Job j : values()) if (j.id.equals(id)) return j;
            return NONE;
        }
    }

    record Work(String kind, int[] answers, List<String> lines, long started) {}

    private static final Map<UUID, Work> PENDING = new HashMap<>();
    private static final Map<UUID, UUID> TAXI_PASSENGER = new HashMap<>();
    public static final int SHIFT_COOLDOWN = 600;

    public static Job job(ServerPlayerEntity p) {
        return Job.of(Life.get(p).job);
    }

    static void select(ServerPlayerEntity p, String id) {
        Job j = Job.of(id);
        LifeData d = Life.get(p);
        if (false) {
            p.sendMessage(Text.literal("Для работы «" + j.title + "» нужно образование: " + eduName(j.minEdu)).formatted(Formatting.RED), true);
            return;
        }
        d.job = j.id;
        d.taskKind = "";
        d.taskPos = null;
        if (j == Job.NONE) Life.message(p, "Центр занятости", "Ты уволился. Найди новую работу в приложении «Работа».");
        else Life.message(p, "Центр занятости", "Ты принят: " + j.title + "! " + j.desc);
        Life.sync(p);
    }

    public static String eduName(int edu) {
        if (edu <= LifeData.SCHOOL_END) return edu + " класс";
        if (edu <= LifeData.UNI_END) return (edu - LifeData.SCHOOL_END) + " курс вуза";
        return "диплом вуза";
    }

    // ---------- курьер ----------
    public static void jobBoard(ServerPlayerEntity p) {
        LifeData d = Life.get(p);
        Job j = job(p);
        if (j != Job.COURIER) {
            LifeNet.send(p, "open_phone", app("jobs"));
            return;
        }
        if (d.taskKind.equals("courier") && d.taskPos != null) {
            p.sendMessage(Text.literal("Посылку ждут: " + TownState.get(p.getServerWorld()).address(d.taskPos) + ". Карта в телефоне покажет путь.").formatted(Formatting.YELLOW), false);
            return;
        }
        BlockPos target = TownState.get(p.getServerWorld()).randomMailbox(p.getRandom(), p.getBlockPos(), 20);
        if (target == null) {
            p.sendMessage(Text.literal("В городе нет почтовых ящиков дальше 20 блоков. Поставь ящики у домов!").formatted(Formatting.RED), false);
            return;
        }
        ItemStack parcel = new ItemStack(ModContent.PARCEL);
        parcel.set(ModContent.DELIVERY_TARGET, target);
        String addr = TownState.get(p.getServerWorld()).address(target);
        parcel.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal("Посылка: " + addr).formatted(Formatting.YELLOW));
        School.give(p, parcel);
        d.taskKind = "courier";
        d.taskPos = target;
        d.taskReward = 12 + (int) Math.min(28, Math.sqrt(target.getSquaredDistance(p.getBlockPos())) / 6);
        Life.message(p, "Курьерская служба", "Новая доставка: " + addr + ". Оплата $" + d.taskReward + ".");
    }

    /** Печь пиццерии: горячая пицца с адресом. Чем быстрее довёз — тем больше чаевых. */
    public static void pizzaOrder(ServerPlayerEntity p) {
        LifeData d = Life.get(p);
        if (job(p) != Job.PIZZA) {
            p.sendMessage(Text.literal("Дядя Марко: «Хочешь развозить пиццу? Выбери работу «Курьер пиццы» в телефоне»").formatted(Formatting.GOLD), false);
            return;
        }
        if (d.taskKind.equals("pizza") && d.taskPos != null) {
            p.sendMessage(Text.literal("Пицца стынет! Адрес: " + TownState.get(p.getServerWorld()).address(d.taskPos)).formatted(Formatting.YELLOW), false);
            return;
        }
        BlockPos target = TownState.get(p.getServerWorld()).randomMailbox(p.getRandom(), p.getBlockPos(), 25);
        if (target == null) return;
        ItemStack box = new ItemStack(ModContent.PARCEL);
        box.set(ModContent.DELIVERY_TARGET, target);
        String addr = TownState.get(p.getServerWorld()).address(target);
        box.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal("Горячая пицца: " + addr).formatted(Formatting.GOLD));
        School.give(p, box);
        d.taskKind = "pizza";
        d.taskPos = target;
        d.taskReward = 10 + (int) Math.min(25, Math.sqrt(target.getSquaredDistance(p.getBlockPos())) / 6);
        PIZZA_AT.put(p.getUuid(), p.getServerWorld().getTime());
        p.getServerWorld().playSound(null, p.getBlockPos(), net.minecraft.sound.SoundEvents.BLOCK_FIRE_EXTINGUISH, net.minecraft.sound.SoundCategory.BLOCKS, 0.5f, 1.4f);
        Life.message(p, "Мама Мия", "Заказ: " + addr + ". Базовая оплата $" + d.taskReward + " + чаевые за скорость.");
        Life.sync(p);
    }

    private static final Map<UUID, Long> PIZZA_AT = new HashMap<>();

    public static void mailbox(ServerPlayerEntity p, BlockPos pos, ItemStack stack) {
        LifeData d = Life.get(p);
        TownState town = TownState.get(p.getServerWorld());
        if (!stack.isOf(ModContent.PARCEL)) {
            p.sendMessage(Text.literal(town.address(pos)), true);
            return;
        }
        BlockPos target = stack.get(ModContent.DELIVERY_TARGET);
        if (target == null || !target.equals(pos)) {
            p.sendMessage(Text.literal("Не тот адрес! Посылка для: " + (target == null ? "?" : town.address(target))).formatted(Formatting.RED), true);
            return;
        }
        stack.decrement(1);
        if (d.taskKind.equals("quest")) {
            SideQuests.letterDelivered(p);
            return;
        }
        if (d.taskKind.equals("pizza")) {
            long sec = (p.getServerWorld().getTime() - PIZZA_AT.getOrDefault(p.getUuid(), p.getServerWorld().getTime())) / 20;
            int tip = sec < 40 ? 15 : sec < 70 ? 8 : sec < 110 ? 3 : 0;
            int pay = d.taskReward + tip;
            d.taskKind = "";
            d.taskPos = null;
            d.tasksDone++;
            Life.earn(p, pay, tip > 0 ? "пицца горячая! чаевые $" + tip : "пицца остыла — без чаевых");
            com.yompix.novograd.story.Story.onJobDone(p, "pizza");
            return;
        }
        int reward = d.taskKind.equals("courier") ? d.taskReward : 10;
        d.taskKind = "";
        d.taskPos = null;
        d.tasksDone++;
        Life.earn(p, reward, "доставка выполнена");
        com.yompix.novograd.story.Story.onJobDone(p, "courier");
        if (d.tasksDone >= 10) Achievements.give(p, "courier10");
    }

    // ---------- такси ----------
    static void takeTaxiOrder(ServerPlayerEntity p) {
        LifeData d = Life.get(p);
        if (job(p) != Job.TAXI) {
            p.sendMessage(Text.literal("Сначала устройся таксистом").formatted(Formatting.RED), true);
            return;
        }
        if (!(p.getVehicle() instanceof CarEntity car) || car.getControllingPassenger() != p) {
            p.sendMessage(Text.literal("Сядь за руль своей машины").formatted(Formatting.RED), true);
            return;
        }
        if (d.taskKind.equals("taxi")) {
            p.sendMessage(Text.literal("Клиент уже в машине: " + TownState.get(p.getServerWorld()).address(d.taskPos)), true);
            return;
        }
        BlockPos target = TownState.get(p.getServerWorld()).randomMailbox(p.getRandom(), p.getBlockPos(), 40);
        if (target == null) {
            p.sendMessage(Text.literal("Нет адресов дальше 40 блоков").formatted(Formatting.RED), true);
            return;
        }
        if (car.getPassengerList().size() < 4) {
            CitizenEntity c = new CitizenEntity(ModContent.CITIZEN, p.getServerWorld());
            c.refreshPositionAndAngles(car.getX(), car.getY(), car.getZ(), 0, 0);
            Random r = p.getRandom();
            c.setup(2 + r.nextInt(6), CitizenEntity.Role.CITIZEN, false, Names.random(r));
            p.getServerWorld().spawnEntity(c);
            c.startRiding(car, true);
            TAXI_PASSENGER.put(p.getUuid(), c.getUuid());
        }
        d.taskKind = "taxi";
        d.taskPos = target;
        d.taskReward = 25 + (int) Math.min(65, Math.sqrt(target.getSquaredDistance(p.getBlockPos())) / 3);
        Life.message(p, "Такси", "Клиент сел: «Мне на " + TownState.get(p.getServerWorld()).address(target) + "». Оплата $" + d.taskReward + ".");
    }

    /** Раз в секунду: довёз ли таксист клиента. */
    public static void tickTaxi(ServerPlayerEntity p) {
        LifeData d = Life.get(p);
        if (!d.taskKind.equals("taxi") || d.taskPos == null) return;
        if (p.getBlockPos().getSquaredDistance(d.taskPos) > 9 * 9) return;
        UUID cid = TAXI_PASSENGER.remove(p.getUuid());
        if (cid != null && p.getServerWorld().getEntity(cid) instanceof CitizenEntity c) {
            c.stopRiding();
            c.removeLater(200);
            p.sendMessage(Text.literal("«Спасибо! Сдачи не надо»").formatted(Formatting.ITALIC), false);
        }
        int reward = d.taskReward;
        d.taskKind = "";
        d.taskPos = null;
        d.tasksDone++;
        Life.earn(p, reward, "поездка оплачена");
        com.yompix.novograd.story.Story.onJobDone(p, "taxi");
        if (d.tasksDone >= 10) Achievements.give(p, "taxi10");
    }

    // ---------- кассир ----------
    private static final String[][] GOODS = {
            {"Хлеб", "2"}, {"Молоко", "3"}, {"Сыр", "7"}, {"Яблоки", "4"}, {"Кола", "4"}, {"Бургер", "8"},
            {"Шоколад", "3"}, {"Чипсы", "5"}, {"Сок", "4"}, {"Кофе", "6"}, {"Пицца", "12"}, {"Мороженое", "3"},
            {"Курица", "9"}, {"Рис", "4"}, {"Тетрадь", "2"}, {"Ручка", "1"}};

    public static void cashier(ServerPlayerEntity p) {
        if (job(p) != Job.CASHIER) {
            Shop.open(p, false);
            return;
        }
        if (!cooldownOk(p)) return;
        Random r = p.getRandom();
        NbtList customers = new NbtList();
        int[] correct = new int[5];
        for (int c = 0; c < 5; c++) {
            NbtCompound cn = new NbtCompound();
            NbtList items = new NbtList();
            int total = 0;
            int n = 2 + r.nextInt(3);
            for (int i = 0; i < n; i++) {
                String[] g = GOODS[r.nextInt(GOODS.length)];
                int qty = 1 + (r.nextInt(4) == 0 ? 1 : 0);
                total += Integer.parseInt(g[1]) * qty;
                items.add(NbtString.of(g[0] + (qty > 1 ? " ×" + qty : "") + "\t" + Integer.parseInt(g[1]) * qty));
            }
            cn.put("items", items);
            List<Integer> opts = new ArrayList<>(List.of(total));
            while (opts.size() < 4) {
                int w = total + (r.nextBoolean() ? 1 : -1) * (1 + r.nextInt(5));
                if (w > 0 && !opts.contains(w)) opts.add(w);
            }
            java.util.Collections.shuffle(opts, new java.util.Random(r.nextLong()));
            cn.putIntArray("options", opts);
            correct[c] = opts.indexOf(total);
            customers.add(cn);
        }
        PENDING.put(p.getUuid(), new Work("cashier", correct, List.of(), p.getServerWorld().getTime()));
        NbtCompound n = new NbtCompound();
        n.put("customers", customers);
        LifeNet.send(p, "open_cashier", n);
    }

    // ---------- программист ----------
    private static final String[] CODE = {
            "int money = 100;", "if (money > 50) buy();", "for (int i = 0; i < 10; i++)", "return a + b;",
            "print(\"Hello, world!\")", "let score = 0;", "while (alive) live();", "car.drive(speed);",
            "def grade(x): return x * 2", "SELECT * FROM users;", "phone.call(mom);", "class School {}",
            "player.earn(25);", "list.add(item);", "if (!ok) throw error;", "import novograd.*;"};

    public static void computer(ServerPlayerEntity p) {
        if (job(p) != Job.PROGRAMMER) {
            p.sendMessage(Text.literal("Компьютер. Работать здесь могут только программисты — устройся в приложении «Работа».").formatted(Formatting.GRAY), true);
            return;
        }
        if (!cooldownOk(p)) return;
        Random r = p.getRandom();
        List<String> lines = new ArrayList<>();
        while (lines.size() < 5) {
            String l = CODE[r.nextInt(CODE.length)];
            if (!lines.contains(l)) lines.add(l);
        }
        PENDING.put(p.getUuid(), new Work("programmer", new int[0], lines, p.getServerWorld().getTime()));
        NbtCompound n = new NbtCompound();
        NbtList ls = new NbtList();
        for (String l : lines) ls.add(NbtString.of(l));
        n.put("lines", ls);
        LifeNet.send(p, "open_typing", n);
    }

    private static boolean cooldownOk(ServerPlayerEntity p) {
        LifeData d = Life.get(p);
        long wait = d.lastWork + SHIFT_COOLDOWN - p.getServerWorld().getTime();
        if (wait > 0) {
            p.sendMessage(Text.literal("Перерыв: следующая смена через " + (wait / 20 + 1) + " с").formatted(Formatting.YELLOW), true);
            return false;
        }
        return true;
    }

    static void answer(ServerPlayerEntity p, NbtCompound d) {
        Work w = PENDING.remove(p.getUuid());
        if (w == null) return;
        long dt = p.getServerWorld().getTime() - w.started();
        LifeData data = Life.get(p);
        data.lastWork = p.getServerWorld().getTime();
        int right = 0, pay;
        if (w.kind().equals("cashier")) {
            if (dt < 60) return;
            int[] a = d.getIntArray("answers");
            for (int i = 0; i < w.answers().length && i < a.length; i++) if (a[i] == w.answers()[i]) right++;
            pay = right * 6 + (right == 5 ? 5 : 0);
        } else {
            if (dt < 100) return;
            NbtList typed = d.getList("typed", NbtElement.STRING_TYPE);
            for (int i = 0; i < w.lines().size() && i < typed.size(); i++)
                if (typed.getString(i).trim().equals(w.lines().get(i))) right++;
            pay = right * 22;
        }
        data.tasksDone++;
        if (right >= 3) com.yompix.novograd.story.Story.onJobDone(p, w.kind());
        if (pay > 0) Life.earn(p, pay, "смена: " + right + " из 5");
        else {
            p.sendMessage(Text.literal("Смена без оплаты: ни одного верного ответа").formatted(Formatting.RED), true);
            Life.sync(p);
        }
    }

    static NbtCompound app(String app) {
        NbtCompound n = new NbtCompound();
        n.putString("app", app);
        return n;
    }
}
