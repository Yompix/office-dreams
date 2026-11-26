package com.yompix.novograd.gta;

import com.yompix.novograd.Novograd;
import com.yompix.novograd.life.Life;
import com.yompix.novograd.life.LifeData;
import com.yompix.novograd.life.LifeNet;
import com.yompix.novograd.world.Island;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.PersistentState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 0.9.6: деньги «как в жизни».
 * <ul>
 *   <li><b>Наличные и карта.</b> {@link LifeData#cash} — кошелёк (на экране), {@link LifeData#money} — карта/счёт, грязные
 *       ({@link LifeData#dirty}) — только наличными. Платёж — сначала наличными, потом картой. Банкомат: снять/положить,
 *       у чужого банкомата (не в «Сити-Банке») комиссия 2 %. Ночью в плохих районах грабят (убежал или в машине — нет);
 *       смерть — половина наличных; арест — все грязные и пятая часть наличных. Старые сохранения: всё на карте.</li>
 *   <li><b>Тайник дома</b> (купленное жильё): за картиной → под полом → сейф в стене; деньги, грязные и вещи. Без охраны
 *       и с простым тайником бывают кражи; сигнализация, собака, камеры — реже. На 4★+ полиция может найти простой тайник.</li>
 *   <li><b>Счета каждое утро</b> (с карты): налог на жильё, коммуналка (растёт с числом домов), страховка машин,
 *       связь, содержание бизнесов; долг на карте — под процент, остаток — с небольшим процентом.</li>
 *   <li><b>Цены и инфляция</b>: индекс цен растёт на 1–3 % в игровой месяц (30 дней) — быстрее, когда игроки
 *       зарабатывают много больше, чем тратят; заработок (миссии, работа) индексируется так же, никто не разоряется.
 *       События города временно дорожают: шторм — бензин и ремонт, «Ночь музыки» — еда и бар.</li>
 * </ul>
 * Все числа — на игрока (UUID) и на мир; расчёт — на сервере.
 */
public final class Money {
    private Money() {}

    public static final int ATM_FEE_PCT = 2;
    public static final int[] STASH_PRICE = {0, 500, 2000, 6000}, STASH_SLOTS = {0, 9, 18, 27};
    public static final String[] STASH_NAMES = {"Нет", "За картиной", "Под полом", "Сейф в стене"};
    public static final int ALARM = 800, DOG = 1200, CAMS = 1500;
    public static final double SAVE_RATE = 0.0002, LOAN_RATE = 0.004; // в игровой день

    // ================================================================== состояние мира
    public static final class Stash {
        public int tier, cash, dirty;
        public boolean alarm, dog, cams;
        public NbtList items = new NbtList();

        NbtCompound write() {
            NbtCompound n = new NbtCompound();
            n.putInt("tier", tier);
            n.putInt("cash", cash);
            n.putInt("dirty", dirty);
            n.putBoolean("alarm", alarm);
            n.putBoolean("dog", dog);
            n.putBoolean("cams", cams);
            n.put("items", items);
            return n;
        }

        static Stash read(NbtCompound n) {
            Stash s = new Stash();
            s.tier = n.getInt("tier");
            s.cash = n.getInt("cash");
            s.dirty = n.getInt("dirty");
            s.alarm = n.getBoolean("alarm");
            s.dog = n.getBoolean("dog");
            s.cams = n.getBoolean("cams");
            s.items = n.getList("items", 10);
            return s;
        }
    }

    public static final class State extends PersistentState {
        public double cpi = 1.0;
        public long day = -1;
        /** Сколько игроки заработали и потратили за текущий день (для темпа инфляции). */
        public long earnedToday, spentToday;
        public double lastRate = 0.0006;
        public final java.util.ArrayDeque<Double> history = new java.util.ArrayDeque<>();
        public final Map<UUID, Stash> stash = new HashMap<>();

        public Stash stash(UUID u) {
            return stash.computeIfAbsent(u, k -> new Stash());
        }

        @Override
        public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup reg) {
            nbt.putDouble("cpi", cpi);
            nbt.putLong("day", day);
            nbt.putLong("earned", earnedToday);
            nbt.putLong("spent", spentToday);
            nbt.putDouble("rate", lastRate);
            NbtList h = new NbtList();
            for (double x : history) h.add(net.minecraft.nbt.NbtDouble.of(x));
            nbt.put("history", h);
            NbtList s = new NbtList();
            for (var e : stash.entrySet()) {
                NbtCompound n = e.getValue().write();
                n.putUuid("who", e.getKey());
                s.add(n);
            }
            nbt.put("stash", s);
            return nbt;
        }

        static State read(NbtCompound nbt, RegistryWrapper.WrapperLookup reg) {
            State st = new State();
            st.cpi = nbt.contains("cpi") ? nbt.getDouble("cpi") : 1.0;
            st.day = nbt.getLong("day");
            st.earnedToday = nbt.getLong("earned");
            st.spentToday = nbt.getLong("spent");
            if (nbt.contains("rate")) st.lastRate = nbt.getDouble("rate");
            for (var e : nbt.getList("history", 6)) st.history.add(((net.minecraft.nbt.NbtDouble) e).doubleValue());
            for (var e : nbt.getList("stash", 10)) {
                NbtCompound n = (NbtCompound) e;
                if (n.containsUuid("who")) st.stash.put(n.getUuid("who"), Stash.read(n));
            }
            return st;
        }
    }

    private static final PersistentState.Type<State> TYPE = new PersistentState.Type<>(State::new, State::read, null);
    private static MinecraftServer server;

    public static State state(MinecraftServer s) {
        return s.getOverworld().getPersistentStateManager().getOrCreate(TYPE, "novograd_money");
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(Money::tick);
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(s -> {
            server = s;
            Macro.bind(s); // 0.9.7: рынок
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(s -> server = null);
    }

    // ================================================================== цены
    /** Индекс цен (1.0 — цены 0.9.5). */
    public static double cpi() {
        MinecraftServer s = server;
        return s == null || s.getOverworld() == null ? 1.0 : state(s).cpi;
    }

    /** Цена с инфляцией (и, для категории, с событием города). */
    public static int price(int base) {
        return (int) Math.round(base * cpi() * com.yompix.novograd.life.Admin.cfg.priceMul);
    }

    /** Заработок с индексацией (без множителя цен сервера). */
    public static int indexed(int base) {
        return (int) Math.round(base * cpi());
    }

    /** Множитель события города: шторм — бензин и ремонт +20 %, «Ночь музыки» — еда и бар +15 %. */
    public static double eventMul(String category) {
        String ev = com.yompix.novograd.world.CityEvents.active();
        if (ev == null) return 1;
        return switch (category) {
            case "fuel", "repair" -> ev.equals("storm") ? 1.2 : 1;
            case "food", "bar" -> ev.equals("festival") ? 1.15 : 1;
            default -> 1;
        };
    }

    public static int price(int base, String category) {
        return (int) Math.round(base * cpi() * eventMul(category));
    }

    /** Учёт для темпа инфляции (вызывают Life.earn и Life.pay). */
    public static void earned(int amount) {
        MinecraftServer s = server;
        if (s != null && amount > 0) state(s).earnedToday += amount;
    }

    public static void spent(int amount) {
        MinecraftServer s = server;
        if (s != null && amount > 0) state(s).spentToday += amount;
    }

    /**
     * Темп инфляции на день: 1 %/мес. базово + до 2 %/мес., если за день заработано больше, чем потрачено
     * (денежная масса растёт); при «стоках» больше дохода — до 0,5 %/мес. Ровно, без скачков (сглаживание 0.8).
     */
    public static double dailyRate(double prev, long earned, long spent) {
        double perMonth = 0.01;
        if (earned + spent > 0) {
            double surplus = (earned - spent) / (double) Math.max(1, earned + spent); // −1…1
            perMonth += Math.max(-0.005, Math.min(0.02, surplus * 0.02));
        }
        double target = perMonth / 30.0;
        return prev * 0.8 + target * 0.2;
    }

    // ================================================================== счета
    /** Счёт за день (база, до индекса): налог 0,1 % стоимости купленного жилья, коммуналка, связь, страховка, бизнесы. */
    public static int[] bills(double ownedValue, int homes, int insured, int businesses) {
        int tax = (int) Math.round(ownedValue * 0.001);
        int util = homes > 0 ? 12 + homes * 6 : 0;
        int phone = 5;
        int ins = insured * MyCars.INSURE_DAY;
        int biz = businesses * 25;
        return new int[]{tax, util, phone, ins, biz};
    }

    public static final String[] BILL_NAMES = {"налог на жильё", "коммуналка", "связь", "страховка", "бизнесы"};

    private static void daily(ServerPlayerEntity p, State st, long day) {
        LifeData d = Life.get(p);
        if (d.billDay == day) return;
        boolean first = d.billDay < 0;
        d.billDay = day;
        if (first) return; // первый день после обновления — без счетов
        var hs = com.yompix.novograd.world.Housing.state(p.getServer()).owner(p.getUuid());
        double owned = 0;
        int homes = 0;
        for (var l : com.yompix.novograd.world.Housing.homes(p)) {
            homes++;
            var h = hs.homes.get(l.id);
            if (h != null && !h.rent) owned += l.price;
        }
        int biz = 0;
        for (String f : d.flags) if (f.startsWith("biz:")) biz++;
        int[] b = bills(owned, homes, MyCars.insured(p.getServer(), p.getUuid()), biz);
        int total = 0;
        for (int x : b) total += x;
        total = (int) Math.round(total * st.cpi);
        d.money -= total; // счёт приходит всегда: не хватило — минус на карте (долг под процент)
        d.spent += total;
        st.spentToday += total;
        // проценты: остаток — немного растёт, долг — дорожает
        // 0.9.7: проценты — от ставки центробанка (рынок)
        int interest = d.money >= 0 ? (int) Math.min(500, Math.floor(d.money * Macro.saveDaily())) : (int) Math.floor(d.money * Macro.loanDaily());
        d.money += interest;
        d.lastBills = total;
        d.lastInterest = interest;
        if (total > 0 || interest != 0)
            Life.message(p, "Банк", "Счета за день: −$" + total + (interest != 0 ? (interest > 0 ? ", проценты +$" : ", проценты по долгу −$") + Math.abs(interest) : "") + ".");
        // тайник: кража, если охраны мало
        Stash s = st.stash.get(p.getUuid());
        if (s != null && (s.cash > 0 || s.dirty > 0 || !s.items.isEmpty())) {
            int risk = burglaryRisk(s);
            if (p.getRandom().nextInt(100) < risk) {
                int lc = s.cash * 35 / 100, ld = s.dirty * 35 / 100;
                s.cash -= lc;
                s.dirty -= ld;
                int gone = 0;
                for (int i = 0; i < 2 && !s.items.isEmpty(); i++) {
                    s.items.remove(p.getRandom().nextInt(s.items.size()));
                    gone++;
                }
                Life.message(p, "Сигнализация", "Квартиру обокрали: −$" + (lc + ld) + (gone > 0 ? ", вещей: " + gone : "") + ". Поставь охрану.");
                st.markDirty();
            }
        }
        if (d.raidRisk && s != null && s.tier < 2 && (s.dirty > 0 || !s.items.isEmpty()) && p.getRandom().nextInt(3) == 0) {
            int ld = s.dirty;
            s.dirty = 0;
            s.items.clear();
            Life.message(p, "Полиция", "Обыск дома: тайник найден, изъято $" + ld + " и вещи.");
            st.markDirty();
        }
        d.raidRisk = false;
        Life.sync(p);
    }

    public static int burglaryRisk(Stash s) {
        int r = 14 - s.tier * 3 - (s.alarm ? 4 : 0) - (s.dog ? 3 : 0) - (s.cams ? 3 : 0);
        return Math.max(1, r);
    }

    // ================================================================== тик
    private static final Map<UUID, long[]> MUGGER = new HashMap<>(); // игрок → {когда, x, z}

    private static void tick(MinecraftServer s) {
        ServerWorld w = s.getOverworld();
        if (s.getTicks() % 100 != 61) return;
        State st = state(s);
        long day = w.getTimeOfDay() / 24000L + 1;
        if (st.day != day) {
            if (st.day >= 0) {
                st.lastRate = dailyRate(st.lastRate, st.earnedToday, st.spentToday);
                // 0.9.7: рынок — шаг дня; ставка центробанка тормозит рост цен
                Macro.day(s, st.lastRate, day);
                st.cpi *= 1 + Math.max(-0.0002, st.lastRate - (Macro.model().rate - 0.03) * 0.5 / 30);
                st.history.addLast(st.cpi);
                while (st.history.size() > 60) st.history.pollFirst();
                Novograd.LOG.info("[деньги] день {}: индекс цен {} (+{}%/мес.), заработано {}, потрачено {}", day, String.format("%.4f", st.cpi),
                        String.format("%.2f", st.lastRate * 30 * 100), st.earnedToday, st.spentToday);
            }
            st.day = day;
            st.earnedToday = 0;
            st.spentToday = 0;
            st.markDirty();
        }
        long tod = w.getTimeOfDay() % 24000;
        boolean night = tod > 13500 && tod < 22500;
        for (ServerPlayerEntity p : s.getPlayerManager().getPlayerList()) {
            if (p.isSpectator() || p.isCreative()) continue;
            daily(p, st, day);
            LifeData d = Life.get(p);
            if (Wanted.stars(p) >= 4) d.raidRisk = true;
            // грабитель
            long[] m = MUGGER.get(p.getUuid());
            if (m != null) {
                if (w.getTime() >= m[0]) {
                    MUGGER.remove(p.getUuid());
                    double dx = p.getX() - m[1], dz = p.getZ() - m[2];
                    if (!p.hasVehicle() && dx * dx + dz * dz < 12 * 12 && p.isAlive()) {
                        int lc = d.cash * 30 / 100, ld = d.dirty / 2;
                        d.cash -= lc;
                        d.dirty -= ld;
                        Activities.title(p, "ОГРАБИЛИ", "−$" + (lc + ld));
                        Life.sync(p);
                    } else Activities.title(p, "", "Ушёл от грабителя");
                }
            } else if (night && !p.hasVehicle() && d.cash + d.dirty >= 150 && p.getServerWorld() == w && !Prison.inside(p)) {
                Island.Zone z = Island.zone(p.getBlockX(), p.getBlockZ());
                boolean bad = z == Island.Zone.CORE || z == Island.Zone.INDUSTRIAL || z == Island.Zone.PORT;
                if (bad && p.getRandom().nextInt(60) == 0 && !Boolean.getBoolean("novograd.nomuggers")) {
                    MUGGER.put(p.getUuid(), new long[]{w.getTime() + 60, (long) p.getX(), (long) p.getZ()});
                    Activities.title(p, "ГРАБИТЕЛЬ!", "беги или садись в машину");
                }
            }
        }
    }

    // ================================================================== кошелёк
    /** Платёж: сначала наличными, потом картой. true — хватило. */
    public static boolean pay(LifeData d, int amount) {
        if (d.cash + Math.max(0, d.money) < amount) return false;
        int c = Math.min(d.cash, amount);
        d.cash -= c;
        d.money -= amount - c;
        return true;
    }

    /** Погиб: половина наличных; арест: пятая часть (грязные — в Economy.onDownfall). */
    public static void onDownfall(ServerPlayerEntity p, boolean busted) {
        LifeData d = Life.get(p);
        int lost = busted ? d.cash / 5 : d.cash / 2;
        if (lost <= 0) return;
        d.cash -= lost;
        Life.message(p, busted ? "Полиция" : "Больница", (busted ? "Изъято наличных: $" : "Потеряно наличных: $") + lost + ". На карте всё цело.");
        Life.sync(p);
    }

    /** Банкомат: снять наличные / положить наличные на карту. */
    public static void atm(ServerPlayerEntity p, String action, int amount) {
        LifeData d = Life.get(p);
        boolean own = Story$near(p, "bank_tower", 40);
        int fee = own ? 0 : Math.max(2, amount * ATM_FEE_PCT / 100);
        amount = Math.max(0, Math.min(50_000, amount));
        if (action.equals("cash_out")) {
            if (d.money < amount + fee) return;
            d.money -= amount + fee;
            d.cash += amount;
        } else if (action.equals("cash_in")) {
            amount = Math.min(amount, d.cash);
            d.cash -= amount;
            d.money += Math.max(0, amount - fee);
        }
        Life.sync(p);
    }

    private static boolean Story$near(ServerPlayerEntity p, String place, double r) {
        Vec3d v = com.yompix.novograd.story.Story.place(p.getServerWorld(), place);
        return v != null && v.squaredDistanceTo(p.getPos()) < r * r;
    }

    // ================================================================== тайник
    public static void stashAction(ServerPlayerEntity p, String act, int amount) {
        State st = state(p.getServer());
        Stash s = st.stash(p.getUuid());
        LifeData d = Life.get(p);
        boolean home = com.yompix.novograd.world.Housing.atAnyHome(p);
        switch (act) {
            case "upgrade" -> {
                if (!home || s.tier >= 3) return;
                if (!Life.pay(p, price(STASH_PRICE[s.tier + 1]), "тайник: " + STASH_NAMES[s.tier + 1].toLowerCase())) return;
                s.tier++;
            }
            case "alarm", "dog", "cams" -> {
                boolean have = act.equals("alarm") ? s.alarm : act.equals("dog") ? s.dog : s.cams;
                if (have) return;
                int pr = price(act.equals("alarm") ? ALARM : act.equals("dog") ? DOG : CAMS);
                if (!Life.pay(p, pr, act.equals("alarm") ? "сигнализация" : act.equals("dog") ? "собака" : "камеры")) return;
                if (act.equals("alarm")) s.alarm = true;
                else if (act.equals("dog")) s.dog = true;
                else s.cams = true;
            }
            case "cash_in", "dirty_in" -> {
                if (!home || s.tier == 0) return;
                boolean dirty = act.startsWith("dirty");
                int a = Math.min(amount, dirty ? d.dirty : d.cash);
                if (a <= 0) return;
                if (dirty) {
                    d.dirty -= a;
                    s.dirty += a;
                } else {
                    d.cash -= a;
                    s.cash += a;
                }
            }
            case "cash_out", "dirty_out" -> {
                if (!home) return;
                boolean dirty = act.startsWith("dirty");
                int a = Math.min(amount, dirty ? s.dirty : s.cash);
                if (a <= 0) return;
                if (dirty) {
                    s.dirty -= a;
                    d.dirty += a;
                } else {
                    s.cash -= a;
                    d.cash += a;
                }
            }
            case "items" -> {
                if (!home || s.tier == 0) return;
                openItems(p, s, st);
                return;
            }
            default -> {}
        }
        st.markDirty();
        Life.sync(p);
        sendStash(p);
    }

    private static void openItems(ServerPlayerEntity p, Stash s, State st) {
        int slots = STASH_SLOTS[s.tier];
        SimpleInventory inv = new SimpleInventory(27);
        for (int i = 0; i < s.items.size() && i < 27; i++) {
            NbtCompound n = s.items.getCompound(i);
            int slot = n.getInt("slot");
            if (slot < 27) inv.setStack(slot, ItemStack.fromNbtOrEmpty(p.getRegistryManager(), n.getCompound("stack")));
        }
        inv.addListener(x -> {
            NbtList l = new NbtList();
            for (int i = 0; i < 27; i++) {
                ItemStack st2 = inv.getStack(i);
                if (st2.isEmpty()) continue;
                NbtCompound n = new NbtCompound();
                n.putInt("slot", i);
                n.put("stack", st2.encode(p.getRegistryManager()));
                l.add(n);
            }
            s.items = l;
            st.markDirty();
        });
        int rows = Math.max(1, slots / 9);
        p.openHandledScreen(new SimpleNamedScreenHandlerFactory((syncId, pinv, pl) -> switch (rows) {
            case 1 -> new GenericContainerScreenHandler(net.minecraft.screen.ScreenHandlerType.GENERIC_9X1, syncId, pinv, inv, 1);
            case 2 -> new GenericContainerScreenHandler(net.minecraft.screen.ScreenHandlerType.GENERIC_9X2, syncId, pinv, inv, 2);
            default -> GenericContainerScreenHandler.createGeneric9x3(syncId, pinv, inv);
        }, Text.literal("Тайник")));
    }

    public static void sendStash(ServerPlayerEntity p) {
        State st = state(p.getServer());
        Stash s = st.stash(p.getUuid());
        NbtCompound n = s.write();
        n.remove("items");
        n.putInt("itemCount", s.items.size());
        n.putBoolean("home", com.yompix.novograd.world.Housing.atAnyHome(p));
        n.putInt("risk", burglaryRisk(s));
        n.putIntArray("prices", new int[]{price(STASH_PRICE[1]), price(STASH_PRICE[2]), price(STASH_PRICE[3]), price(ALARM), price(DOG), price(CAMS)});
        LifeNet.send(p, "stash", n);
    }

    /** Для «Финансов»: индекс цен, темп, ставки, последние счета. */
    public static void fill(NbtCompound n, ServerPlayerEntity p) {
        State st = state(p.getServer());
        LifeData d = Life.get(p);
        n.putDouble("cpi", st.cpi);
        n.putDouble("inflMonth", st.lastRate * 30 * 100);
        n.putDouble("saveRate", Macro.saveDaily() * 30 * 100);
        n.putDouble("loanRate", Macro.loanDaily() * 30 * 100);
        Macro.fill(n); // 0.9.7: рынок для «Финансов»
        n.putInt("bills", d.lastBills);
        n.putInt("interest", d.lastInterest);
        n.putInt("cash", d.cash);
        NbtList h = new NbtList();
        for (double x : st.history) h.add(net.minecraft.nbt.NbtDouble.of(x));
        n.put("cpiHistory", h);
    }
}
