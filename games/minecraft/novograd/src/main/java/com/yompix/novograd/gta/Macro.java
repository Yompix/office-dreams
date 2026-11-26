package com.yompix.novograd.gta;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;

import java.util.Locale;
import java.util.Random;

/**
 * 0.9.7: «рынок» — простая макроэкономика острова, шаг раз в игровой день (поверх индекса цен 0.9.6, {@link Money}).
 *
 * <ul>
 *   <li><b>Товары</b> ({@link #CATS}): еда, бензин, машины, одежда, оружие, жильё, бар, ремонт. У каждого — спрос
 *       (покупки игроков в этой категории за день против обычного), предложение (события: дефицит бензина, урожай),
 *       сезон (зимой бензин, летом бар и еда на курорте), войны банд (оружие дороже, жильё в районе дешевле). Цена
 *       категории — множитель 0,75…1,4 к обычной, тянется к 1 (без разгона).</li>
 *   <li><b>Центробанк</b>: цель инфляции 1,5 %/мес.; ставка 1…10 %/мес. по правилу Тейлора (выше инфляция — выше
 *       ставка), ставка тормозит индекс цен; от неё — проценты вклада и долга.</li>
 *   <li><b>Работа</b>: безработица 3…14 % — от оборота (сколько тратят игроки и приносят бизнесы) и событий;
 *       индекс зарплат 0,9…1,12 — к выплатам за работу и миссии.</li>
 *   <li><b>Новости</b>: случайные события (дефицит бензина, бум/обвал жилья, курортный сезон, отзыв машин, урожай,
 *       забастовка в порту) — в ленту «Новости» и на биржу (сектор компании растёт/падает).</li>
 *   <li><b>Бизнесы</b>: доход × состояние своего сектора; <b>биржа</b>: акции тянутся за своим сектором.</li>
 * </ul>
 * Всё ограничено сверху и снизу; 90 дней без игроков и с «бешеным» игроком — {@code ./gradlew macroTest}.
 */
public final class Macro {
    private Macro() {}

    public static final String[] CATS = {"food", "fuel", "cars", "clothes", "weapons", "property", "bar", "repair"};
    public static final String[] CAT_NAMES = {"Еда", "Бензин", "Машины", "Одежда", "Оружие", "Жильё", "Бар", "Ремонт"};
    /** Обычные траты всех игроков в категории за день (база спроса, $ до индекса). */
    static final double[] BASE_DEMAND = {120, 90, 400, 150, 150, 600, 60, 80};
    public static final double MUL_LO = 0.75, MUL_HI = 1.4, RATE_LO = 0.01, RATE_HI = 0.10, TARGET = 0.015;

    /** Событие: id, заголовок, категория (−1 нет), сдвиг предложения, дней, тикер биржи, шок акций. */
    record Ev(String id, String title, int cat, double supply, int days, String ticker, double shock) {}

    static final Ev[] EVENTS = {
            new Ev("fuel_shortage", "Дефицит бензина: танкер задержан штормом", 1, 0.78, 5, "TAXI", -0.08),
            new Ev("property_boom", "Бум жилья: на остров едут новые жители", 5, 0.85, 7, "CITY", 0.07),
            new Ev("property_crash", "Обвал цен на жильё: банк ужесточил ипотеку", 5, 1.25, 7, "CITY", -0.10),
            new Ev("tourism", "Курортный сезон на Ривьере: отели полны", 6, 0.88, 6, "RIVH", 0.12),
            new Ev("car_recall", "Отзыв машин: дилеры распродают склад", 2, 1.2, 5, "NITR", -0.06),
            new Ev("harvest", "Богатый урожай в Сосновке: еда дешевеет", 0, 1.2, 5, "PORT", 0.03),
            new Ev("port_strike", "Забастовка в порту: товары задерживаются", 3, 0.85, 4, "PORT", -0.09),
            new Ev("arms_raid", "Полиция накрыла склад оружия", 4, 0.8, 4, "KRON", -0.03),
    };

    /** Состояние (сохраняется в мире). */
    public static final class Model {
        public final double[] mul = new double[CATS.length], demand = new double[CATS.length], supply = new double[CATS.length];
        public double rate = 0.03, inflation = 0.01, unemployment = 0.07, wage = 1.0;
        public long day;
        public String event = "";
        public int eventLeft;
        /** История для графиков: день → [инфляция %/мес, ставка %/мес, безработица %, зарплаты, бензин, жильё, еда]. */
        public final java.util.ArrayDeque<double[]> hist = new java.util.ArrayDeque<>();
        /** Траты по категориям за текущий день. */
        public final double[] spentToday = new double[CATS.length];
        public double bizIncomeToday;
        public String gangWar = "";

        public Model() {
            java.util.Arrays.fill(mul, 1);
            java.util.Arrays.fill(demand, 1);
            java.util.Arrays.fill(supply, 1);
        }

        public double sector(int cat) {
            return mul[cat] * 0.5 + demand[cat] * 0.5;
        }

        /**
         * Шаг дня. baseInflation — темп индекса цен из баланса «заработали/потратили» (Money.dailyRate, в день);
         * season 0 весна, 1 лето, 2 осень, 3 зима; r — случайность. Возвращает новое событие или null.
         */
        public Ev step(double baseInflation, int season, Random r) {
            day++;
            Ev started = null;
            // события
            if (eventLeft > 0 && --eventLeft == 0) event = "";
            if (event.isEmpty() && r.nextInt(100) < 9) {
                started = EVENTS[r.nextInt(EVENTS.length)];
                event = started.id;
                eventLeft = started.days;
            }
            Ev cur = null;
            for (Ev e : EVENTS) if (e.id.equals(event)) cur = e;
            double turnover = 0;
            for (int c = 0; c < CATS.length; c++) {
                // спрос: траты за день против обычных (сглажено, 0,5…2)
                double d = Math.max(0.5, Math.min(2.0, spentToday[c] <= 0 ? 1.0 : spentToday[c] / BASE_DEMAND[c]));
                demand[c] = demand[c] * 0.7 + d * 0.3;
                turnover += Math.min(2, spentToday[c] / BASE_DEMAND[c]);
                // предложение: событие, иначе к 1
                double s = cur != null && cur.cat == c ? cur.supply : 1.0;
                supply[c] = supply[c] * 0.6 + s * 0.4;
                double seasonal = switch (CATS[c]) {
                    case "fuel" -> season == 3 ? 1.04 : 1;
                    case "bar", "food" -> season == 1 ? 1.03 : 1;
                    case "clothes" -> season == 2 ? 1.02 : 1;
                    default -> 1;
                };
                double war = gangWar.isEmpty() ? 1 : CATS[c].equals("weapons") ? 1.08 : CATS[c].equals("property") ? 0.96 : 1;
                // цель цены: спрос / предложение × сезон × война; цена идёт к цели и тянется к 1
                double target = Math.pow(demand[c], 0.35) / Math.pow(supply[c], 0.8) * seasonal * war;
                mul[c] += (target - mul[c]) * 0.25 + (1 - mul[c]) * 0.04;
                mul[c] = Math.max(MUL_LO, Math.min(MUL_HI, mul[c]));
                spentToday[c] = 0;
            }
            // инфляция: темп от денег, ставка тормозит, средняя цена категорий добавляет
            double avgMul = 0;
            for (double m : mul) avgMul += m;
            avgMul /= mul.length;
            double perMonth = baseInflation * 30 - (rate - 0.03) * 0.5 + (avgMul - 1) * 0.04;
            perMonth = Math.max(-0.005, Math.min(0.05, perMonth));
            inflation = inflation * 0.7 + perMonth * 0.3;
            // центробанк (Тейлор): ставка = нейтральная + 1,5 × (инфляция − цель) + 0,5 × (5 % − безработица)
            double want = 0.03 + 1.5 * (inflation - TARGET) + 0.5 * (0.05 - unemployment) * 0.2;
            rate += (Math.max(RATE_LO, Math.min(RATE_HI, want)) - rate) * 0.3;
            // занятость: оборот выше обычного и доход бизнесов — меньше безработица; высокая ставка — больше
            double activity = turnover / CATS.length + Math.min(1, bizIncomeToday / 5000.0) * 0.3;
            double uWant = 0.09 - (activity - 1) * 0.04 + (rate - 0.03) * 0.6 + (cur != null && cur.id.equals("port_strike") ? 0.02 : 0)
                    + (cur != null && cur.id.equals("tourism") ? -0.01 : 0) + r.nextGaussian() * 0.003;
            unemployment += (Math.max(0.03, Math.min(0.14, uWant)) - unemployment) * 0.2;
            wage = Math.max(0.9, Math.min(1.12, 1 + (0.07 - unemployment) * 1.2 + (inflation - TARGET) * 2));
            bizIncomeToday = 0;
            hist.addLast(new double[]{day, inflation * 100, rate * 100, unemployment * 100, wage, mul[1], mul[5], mul[0]});
            while (hist.size() > 60) hist.pollFirst();
            return started;
        }

        /** Ставки на день для вклада и долга (вклад ≈ 0,6 ставки, долг — ставка + 6 %/мес.). */
        public double saveDaily() {
            return rate * 0.6 / 30;
        }

        public double loanDaily() {
            return (rate + 0.06) / 30;
        }

        public NbtCompound write() {
            NbtCompound n = new NbtCompound();
            for (int c = 0; c < CATS.length; c++) {
                n.putDouble("m_" + CATS[c], mul[c]);
                n.putDouble("d_" + CATS[c], demand[c]);
                n.putDouble("s_" + CATS[c], supply[c]);
            }
            n.putDouble("rate", rate);
            n.putDouble("infl", inflation);
            n.putDouble("unemp", unemployment);
            n.putDouble("wage", wage);
            n.putLong("day", day);
            n.putString("event", event);
            n.putInt("eventLeft", eventLeft);
            NbtList h = new NbtList();
            for (double[] x : hist) {
                NbtList row = new NbtList();
                for (double v : x) row.add(net.minecraft.nbt.NbtDouble.of(v));
                h.add(row);
            }
            n.put("hist", h);
            return n;
        }

        public static Model read(NbtCompound n) {
            Model m = new Model();
            for (int c = 0; c < CATS.length; c++) {
                if (n.contains("m_" + CATS[c])) m.mul[c] = n.getDouble("m_" + CATS[c]);
                if (n.contains("d_" + CATS[c])) m.demand[c] = n.getDouble("d_" + CATS[c]);
                if (n.contains("s_" + CATS[c])) m.supply[c] = n.getDouble("s_" + CATS[c]);
            }
            if (n.contains("rate")) m.rate = n.getDouble("rate");
            if (n.contains("infl")) m.inflation = n.getDouble("infl");
            if (n.contains("unemp")) m.unemployment = n.getDouble("unemp");
            if (n.contains("wage")) m.wage = n.getDouble("wage");
            m.day = n.getLong("day");
            m.event = n.getString("event");
            m.eventLeft = n.getInt("eventLeft");
            for (var e : n.getList("hist", 9)) {
                NbtList row = (NbtList) e;
                double[] x = new double[row.size()];
                for (int i = 0; i < x.length; i++) x[i] = row.getDouble(i);
                m.hist.add(x);
            }
            return m;
        }
    }

    // ================================================================== в мире
    static final class State extends PersistentState {
        Model m = new Model();
        static final Type<State> TYPE = new Type<>(State::new, (n, reg) -> {
            State s = new State();
            s.m = Model.read(n);
            return s;
        }, null);

        @Override
        public NbtCompound writeNbt(NbtCompound n, net.minecraft.registry.RegistryWrapper.WrapperLookup reg) {
            return n.copyFrom(m.write());
        }
    }

    private static MinecraftServer server;
    private static Model offline; // без сервера (симуляция, тесты)

    public static void bind(MinecraftServer s) {
        server = s;
    }

    public static Model model() {
        MinecraftServer s = server;
        if (s == null || s.getOverworld() == null) return offline == null ? (offline = new Model()) : offline;
        return s.getOverworld().getPersistentStateManager().getOrCreate(State.TYPE, "novograd_macro").m;
    }

    private static void dirty() {
        MinecraftServer s = server;
        if (s != null && s.getOverworld() != null) s.getOverworld().getPersistentStateManager().getOrCreate(State.TYPE, "novograd_macro").markDirty();
    }

    /** Категория траты по описанию платежа («бензин», «ремонт», «квартира»…); −1 — общая. */
    public static int category(String why) {
        if (why == null) return -1;
        String w = why.toLowerCase(Locale.ROOT);
        if (w.contains("бензин") || w.contains("заправ") || w.contains("топлив")) return 1;
        if (w.contains("ремонт") || w.contains("покраск") || w.contains("тюнинг") || w.contains("нитро")) return 7;
        if (w.contains("машин") || w.contains("авто") || w.contains("мотоц") || w.contains("скутер") || w.contains("вертол") || w.contains("лодк")) return 2;
        if (w.contains("оруж") || w.contains("патрон") || w.contains("пистол") || w.contains("бронеж") || w.contains("ствол")) return 4;
        if (w.contains("квартир") || w.contains("жиль") || w.contains("дом") || w.contains("пентхаус") || w.contains("вилл") || w.contains("аренд")) return 5;
        if (w.contains("одежд") || w.contains("образ") || w.contains("внешн") || w.contains("стриж")) return 3;
        if (w.contains("бар") || w.contains("коктейл") || w.contains("клуб")) return 6;
        if (w.contains("еда") || w.contains("кафе") || w.contains("бургер") || w.contains("пицц") || w.contains("обед") || w.contains("кофе") || w.contains("магазин")) return 0;
        return -1;
    }

    /** Множитель цены категории (−1 — 1). */
    public static double mul(int cat) {
        return cat < 0 ? 1 : model().mul[cat];
    }

    /** Покупка игрока: учесть спрос; вернуть цену с рынком. */
    public static int price(int indexed, String why) {
        int c = category(why);
        if (c < 0) return indexed;
        Model m = model();
        m.spentToday[c] += indexed;
        return (int) Math.round(indexed * m.mul[c]);
    }

    public static double wage() {
        return model().wage;
    }

    public static double saveDaily() {
        return model().saveDaily();
    }

    public static double loanDaily() {
        return model().loanDaily();
    }

    /** Бизнес i (Heists.BUSINESS): сектор — бар/клуб — «бар», стоянка — «машины», отель/пристань — курорт, ферма — еда. */
    public static double bizMul(int i) {
        Model m = model();
        int c = switch (i) {
            case 0, 1, 6 -> 6;
            case 2 -> 2;
            case 3, 4 -> 6;
            case 5 -> 0;
            default -> -1;
        };
        double base = c < 0 ? 1 : 0.85 + 0.15 * m.sector(c);
        if ((i == 3 || i == 4) && m.event.equals("tourism")) base *= 1.25;
        if (!m.gangWar.isEmpty()) base *= 0.97;
        return Math.max(0.7, Math.min(1.35, base));
    }

    public static void bizIncome(int amount) {
        model().bizIncomeToday += amount;
    }

    /** Биржа: дрейф акции от её сектора (в час). */
    public static double stockDrift(String ticker) {
        Model m = model();
        int c = switch (ticker) {
            case "CITY" -> 5;
            case "KRON" -> 6;
            case "NITR" -> 2;
            case "PORT" -> 3;
            case "RIVH" -> 6;
            case "TAXI" -> 1;
            default -> -1;
        };
        double s = c < 0 ? 1 : m.sector(c);
        if (ticker.equals("TAXI")) s = 2 - s; // дорогой бензин — плохо для такси
        return Math.max(-0.004, Math.min(0.004, (s - 1) * 0.01 - (m.rate - 0.03) * 0.02));
    }

    /** Время года по дню (сезон — 30 игровых дней). */
    public static int season(long day) {
        return (int) ((day / 30) % 4);
    }

    /** Шаг дня в мире (Money.tick на смене дня): события — в новости и на биржу. */
    public static void day(MinecraftServer s, double baseInflationDaily, long day) {
        Model m = model();
        m.gangWar = Territories.warActive(s) ? "war" : "";
        Ev e = m.step(baseInflationDaily, season(day), new Random(day * 977 + 13));
        if (e != null) {
            Economy.shock(s, e.ticker, e.shock, e.title);
            com.yompix.novograd.Novograd.LOG.info("[рынок] день {}: {}", day, e.title);
        }
        com.yompix.novograd.Novograd.LOG.info("[рынок] день {}: инфляция {}%/мес, ставка {}%/мес, безработица {}%, зарплаты ×{}, бензин ×{}, жильё ×{}",
                day, String.format(Locale.ROOT, "%.2f", m.inflation * 100), String.format(Locale.ROOT, "%.2f", m.rate * 100),
                String.format(Locale.ROOT, "%.1f", m.unemployment * 100), String.format(Locale.ROOT, "%.3f", m.wage),
                String.format(Locale.ROOT, "%.2f", m.mul[1]), String.format(Locale.ROOT, "%.2f", m.mul[5]));
        dirty();
    }

    /** Для «Финансов»: показатели и история. */
    public static void fill(NbtCompound n) {
        Model m = model();
        NbtCompound o = new NbtCompound();
        o.putDouble("infl", m.inflation * 100);
        o.putDouble("rate", m.rate * 100);
        o.putDouble("unemp", m.unemployment * 100);
        o.putDouble("wage", m.wage);
        o.putString("event", m.event);
        String title = "";
        for (Ev e : EVENTS) if (e.id.equals(m.event)) title = e.title;
        o.putString("eventTitle", title);
        NbtList cats = new NbtList();
        for (int c = 0; c < CATS.length; c++) {
            NbtCompound x = new NbtCompound();
            x.putString("name", CAT_NAMES[c]);
            x.putDouble("mul", m.mul[c]);
            cats.add(x);
        }
        o.put("cats", cats);
        NbtList h = new NbtList();
        for (double[] row : m.hist) {
            NbtCompound x = new NbtCompound();
            x.putDouble("infl", row[1]);
            x.putDouble("rate", row[2]);
            x.putDouble("unemp", row[3]);
            h.add(x);
        }
        o.put("hist", h);
        n.put("macro", o);
    }
}
