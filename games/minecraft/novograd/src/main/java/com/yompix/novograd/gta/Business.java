package com.yompix.novograd.gta;

import com.yompix.novograd.entity.CarEntity;
import com.yompix.novograd.life.Achievements;
import com.yompix.novograd.life.Life;
import com.yompix.novograd.life.LifeNet;
import com.yompix.novograd.story.Story;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.PersistentState;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 0.9.10: свой бизнес с нуля (телефон → «Бизнес»): кафе, автосалон или клуб — на свободном месте города.
 * <ul>
 *   <li><b>Открыть</b>: место (у каждого места один хозяин), название, взнос. Каждое утро — день работы: гости
 *       приходят по спросу района (рынок gta/Macro), репутации, ценам, персоналу; продажи ограничены запасом.</li>
 *   <li><b>Запас</b>: закупка по телефону (оптом) — или свой рейс на фуре от портового склада (вдвое дешевле).</li>
 *   <li><b>Персонал</b>: по 3 должности на тип (кафе: повар, официант, бариста; салон: продавец, механик, менеджер;
 *       клуб: бармен, охрана, диджей) — каждый поднимает поток гостей и репутацию, зарплата каждый день.</li>
 *   <li><b>Цены</b>: дёшево / обычно / премиум — больше гостей или больше с гостя.</li>
 *   <li><b>Конкуренты</b>: бизнесы одного типа у разных игроков делят гостей (по репутации и цене); рейтинг всех
 *       бизнесов сервера — в приложении. Касса бизнеса — забрать на карту (налог 5 %).</li>
 * </ul>
 * Числа модели — {@link #simulate}; проверка — {@code ./gradlew bizTest} (BusinessSim).
 */
public final class Business {
    private Business() {}

    public static final String[] TYPES = {"cafe", "dealer", "club"};
    public static final String[] TYPE_NAMES = {"Кафе", "Автосалон", "Клуб"};
    public static final String[][] STAFF = {{"повар", "официант", "бариста"}, {"продавец", "механик", "менеджер"}, {"бармен", "охрана", "диджей"}};
    public static final int[] OPEN = {5000, 15000, 12000}, RENT = {60, 160, 120}, WAGE = {45, 70, 55};
    /** Гостей в день при репутации 50 (без персонала), цена с гостя, себестоимость единицы запаса. */
    public static final int[] BASE_GUESTS = {60, 4, 80};
    public static final int[] TICKET = {12, 1150, 25};
    public static final int[] UNIT_COST = {4, 900, 8};
    public static final String[][] PLACES = {{"cafe_dt", "cafe_suburb", "cafe_riviera", "cafe_mirage"}, {"industry_street", "suburb_street", "desert_road"},
            {"club_neon", "beach_bar", "riv_strip"}};
    public static final String[] PRICE_NAMES = {"дёшево", "обычно", "премиум"};
    public static final float[] PRICE_MUL = {0.75f, 1f, 1.45f};

    public static final class Biz {
        public String place, name;
        public int type, price = 1, stock, cash, rep = 50;
        public UUID owner;
        public String ownerName = "";
        public int[] staff = new int[3];
        public int lastGuests, lastRevenue, lastCosts;
        public long day;

        NbtCompound write() {
            NbtCompound n = new NbtCompound();
            n.putString("place", place);
            n.putString("name", name);
            n.putInt("type", type);
            n.putInt("price", price);
            n.putInt("stock", stock);
            n.putInt("cash", cash);
            n.putInt("rep", rep);
            n.putUuid("owner", owner);
            n.putString("ownerName", ownerName);
            n.putIntArray("staff", staff);
            n.putInt("g", lastGuests);
            n.putInt("rv", lastRevenue);
            n.putInt("cs", lastCosts);
            n.putLong("day", day);
            return n;
        }

        static Biz read(NbtCompound n) {
            Biz b = new Biz();
            b.place = n.getString("place");
            b.name = n.getString("name");
            b.type = n.getInt("type");
            b.price = n.getInt("price");
            b.stock = n.getInt("stock");
            b.cash = n.getInt("cash");
            b.rep = n.getInt("rep");
            b.owner = n.getUuid("owner");
            b.ownerName = n.getString("ownerName");
            int[] s = n.getIntArray("staff");
            if (s.length == 3) b.staff = s;
            b.lastGuests = n.getInt("g");
            b.lastRevenue = n.getInt("rv");
            b.lastCosts = n.getInt("cs");
            b.day = n.getLong("day");
            return b;
        }
    }

    static final class State extends PersistentState {
        final Map<String, Biz> biz = new LinkedHashMap<>();
        long day = -1;

        @Override
        public NbtCompound writeNbt(NbtCompound n, RegistryWrapper.WrapperLookup reg) {
            NbtList l = new NbtList();
            for (Biz b : biz.values()) l.add(b.write());
            n.put("biz", l);
            n.putLong("day", day);
            return n;
        }

        static State read(NbtCompound n, RegistryWrapper.WrapperLookup reg) {
            State s = new State();
            for (var e : n.getList("biz", 10)) {
                Biz b = Biz.read((NbtCompound) e);
                s.biz.put(b.place, b);
            }
            s.day = n.contains("day") ? n.getLong("day") : -1;
            return s;
        }
    }

    private static final PersistentState.Type<State> TYPE = new PersistentState.Type<>(State::new, State::read, null);

    static State state(MinecraftServer s) {
        return s.getOverworld().getPersistentStateManager().getOrCreate(TYPE, "novograd_business");
    }

    // ================================================================== модель дня (чистая математика)
    /** Поток гостей без конкурентов: база × репутация × цена × персонал × спрос района. */
    public static double draw(Biz b, double demand) {
        int staff = b.staff[0] + b.staff[1] + b.staff[2];
        double priceK = switch (b.price) {
            case 0 -> 1.35;
            case 2 -> 0.65;
            default -> 1.0;
        };
        return BASE_GUESTS[b.type] * (0.4 + b.rep / 83.0) * priceK * (1 + Math.min(6, staff) * 0.12) * demand;
    }

    /** Один день всех бизнесов: делим гостей между конкурентами одного типа (по «притяжению»). */
    public static void simulate(java.util.Collection<Biz> all, double[] demand, java.util.Random r) {
        for (int t = 0; t < TYPES.length; t++) {
            final int tt = t;
            var same = all.stream().filter(b -> b.type == tt).toList();
            if (same.isEmpty()) continue;
            double sum = 0;
            for (Biz b : same) sum += draw(b, demand[t]);
            // рынок типа не растёт бесконечно от числа точек: каждая новая делит его с остальными
            double market = BASE_GUESTS[t] * demand[t] * (1 + Math.sqrt(same.size() - 1) * 0.6) * 1.4;
            for (Biz b : same) {
                double share = sum <= 0 ? 0 : draw(b, demand[t]) / sum;
                int guests = (int) Math.round(Math.min(draw(b, demand[t]), market * share) * (0.9 + r.nextDouble() * 0.2));
                int sold = Math.min(guests, b.stock);
                int revenue = (int) (sold * TICKET[t] * PRICE_MUL[b.price]);
                int staff = b.staff[0] + b.staff[1] + b.staff[2];
                int costs = RENT[t] + staff * WAGE[t];
                b.stock -= sold;
                b.cash += revenue - costs;
                b.lastGuests = guests;
                b.lastRevenue = revenue;
                b.lastCosts = costs;
                // репутация: хватило запаса и людей — растёт; не хватило, дорого и пусто — падает
                int need = Math.max(1, guests / Math.max(1, staff == 0 ? 1 : staff * 25));
                int dr = (sold < guests ? -6 : 2) + (staff >= 2 ? 2 : -1) + (b.price == 2 ? -1 : b.price == 0 ? 1 : 0) - (need > 3 ? 2 : 0);
                b.rep = Math.max(5, Math.min(100, b.rep + dr));
            }
        }
    }

    // ================================================================== сервер
    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(Business::tick);
        Achievements.ALL.putIfAbsent("biz_open", new String[]{"Своё дело", "Открыть бизнес с нуля"});
        Achievements.ALL.putIfAbsent("biz_top", new String[]{"Номер один", "Твой бизнес — первый в рейтинге"});
    }

    private static Biz mine(MinecraftServer s, UUID u) {
        for (Biz b : state(s).biz.values()) if (b.owner.equals(u)) return b;
        return null;
    }

    private static void tick(MinecraftServer s) {
        if (s.getTicks() % 100 != 77) return;
        ServerWorld w = s.getOverworld();
        State st = state(s);
        long day = w.getTimeOfDay() / 24000L + 1;
        if (st.day != day && !st.biz.isEmpty()) {
            if (st.day >= 0) {
                double[] demand = {Macro.mul(0) * Macro.wage(), Macro.mul(2) * Macro.wage(), Macro.mul(6) * Macro.wage()};
                simulate(st.biz.values(), demand, new java.util.Random(day * 31));
                Biz top = st.biz.values().stream().max((a, b) -> a.lastRevenue - b.lastRevenue).orElse(null);
                for (Biz b : st.biz.values()) {
                    ServerPlayerEntity p = s.getPlayerManager().getPlayer(b.owner);
                    if (p == null) continue;
                    Life.message(p, "«" + b.name + "»", "Вчера: гостей " + b.lastGuests + ", выручка $" + b.lastRevenue + ", расходы $" + b.lastCosts + ", запас " + b.stock
                            + ", репутация " + b.rep + (b.stock < BASE_GUESTS[b.type] ? " · нужен запас!" : ""));
                    if (b == top && st.biz.size() >= 2) Achievements.give(p, "biz_top");
                }
            }
            st.day = day;
            st.markDirty();
        }
        // свой рейс за запасом: фура у бизнеса — запас вдвое дешевле
        for (ServerPlayerEntity p : s.getPlayerManager().getPlayerList()) {
            Biz b = mine(s, p.getUuid());
            if (b == null || !Life.get(p).city.contains("bizRun")) continue;
            Vec3d at = Story.place(w, b.place);
            if (at != null && p.getVehicle() instanceof CarEntity car && car.getVariant() == 11 && car.squaredDistanceTo(at) < 25 * 25 && car.slow()) {
                int units = Life.get(p).city.getInt("bizRun");
                Life.get(p).city.remove("bizRun");
                b.stock += units;
                st.markDirty();
                p.stopRiding();
                car.discard();
                FunUtil.target(p, null, "");
                Activities.title(p, "ЗАПАС ПРИВЕЗЁН", "«" + b.name + "» +" + units);
            }
            labels(w, b);
        }
    }

    /** Вывеска с названием над местом, когда хозяин рядом. */
    private static void labels(ServerWorld w, Biz b) {
        Vec3d at = Story.place(w, b.place);
        if (at == null || w.getClosestPlayer(at.x, at.y, at.z, 64, false) == null) return;
        String tag = "nv_biz:" + b.place;
        Box box = new Box(at, at).expand(6);
        var have = w.getEntitiesByClass(DisplayEntity.TextDisplayEntity.class, box, e -> e.getCommandTags().contains(tag));
        String text = "«" + b.name + "» · " + TYPE_NAMES[b.type].toLowerCase() + " · " + b.ownerName;
        if (!have.isEmpty()) return;
        DisplayEntity.TextDisplayEntity d = EntityType.TEXT_DISPLAY.create(w);
        if (d == null) return;
        NbtCompound n = new NbtCompound();
        n.putString("billboard", "center");
        n.putInt("background", 0xAA101010);
        d.readNbt(n);
        d.setText(Text.literal(text).formatted(Formatting.GOLD));
        d.refreshPositionAndAngles(at.x, at.y + 3.2, at.z, 0, 0);
        d.addCommandTag(tag);
        w.spawnEntity(d);
    }

    public static boolean handle(ServerPlayerEntity p, String type, NbtCompound n) {
        State st = state(p.getServer());
        Biz b = mine(p.getServer(), p.getUuid());
        switch (type) {
            case "biz_state" -> {}
            case "biz_open" -> {
                int t = n.getInt("type");
                String place = n.getString("place"), name = n.getString("name").replaceAll("[^\\p{L}\\p{N} \\-«»]", "").trim();
                if (b != null || t < 0 || t >= TYPES.length || st.biz.containsKey(place) || !java.util.Arrays.asList(PLACES[t]).contains(place)) break;
                if (name.length() < 2 || name.length() > 20) name = TYPE_NAMES[t] + " " + p.getName().getString();
                if (!Life.pay(p, OPEN[t], "открытие бизнеса")) break;
                Biz nb = new Biz();
                nb.place = place;
                nb.type = t;
                nb.name = name;
                nb.owner = p.getUuid();
                nb.ownerName = p.getName().getString();
                st.biz.put(place, nb);
                st.markDirty();
                Achievements.give(p, "biz_open");
                News.post(p.getServer(), "Открылось новое заведение: «" + name + "» (" + TYPE_NAMES[t].toLowerCase() + "). Владелец — " + nb.ownerName, "БИЗНЕС");
            }
            case "biz_price" -> {
                if (b != null) b.price = Math.max(0, Math.min(2, n.getInt("v")));
            }
            case "biz_staff" -> {
                int i = n.getInt("i"), d = n.getInt("d");
                if (b != null && i >= 0 && i < 3) b.staff[i] = Math.max(0, Math.min(3, b.staff[i] + d));
            }
            case "biz_buy" -> {
                int units = Math.max(1, Math.min(500, n.getInt("units")));
                if (b != null && Life.pay(p, units * UNIT_COST[b.type], "запас: «" + b.name + "»")) b.stock += units;
            }
            case "biz_run" -> { // свой рейс: фура у склада, запас — за полцены
                int units = b == null ? 0 : (b.type == 1 ? 4 : 200);
                if (b == null || Life.get(p).city.contains("bizRun") || !Life.pay(p, units * UNIT_COST[b.type] / 2, "свой рейс за запасом")) break;
                Vec3d wh = Story.place(p.getServerWorld(), "port_warehouse");
                CarEntity truck = FunUtil.car(p.getServerWorld(), wh, 0, 11);
                Life.get(p).city.putInt("bizRun", units);
                FunUtil.target(p, truck.getPos(), "ФУРА С ЗАПАСОМ");
                Life.message(p, "Склад", "Фура загружена: " + units + " ед. Довези до «" + b.name + "».");
            }
            case "biz_cash" -> {
                if (b != null && b.cash > 0) {
                    int tax = b.cash * 5 / 100;
                    Life.earn(p, b.cash - tax, "касса «" + b.name + "» (налог $" + tax + ")");
                    b.cash = 0;
                }
            }
            case "biz_close" -> {
                if (b != null) {
                    int back = Math.max(0, b.cash) + OPEN[b.type] / 3;
                    Life.earn(p, back, "закрытие «" + b.name + "»");
                    st.biz.remove(b.place);
                }
            }
            default -> {
                return false;
            }
        }
        st.markDirty();
        send(p);
        return true;
    }

    public static void send(ServerPlayerEntity p) {
        State st = state(p.getServer());
        NbtCompound out = new NbtCompound();
        Biz b = mine(p.getServer(), p.getUuid());
        if (b != null) out.put("mine", b.write());
        NbtList all = new NbtList();
        st.biz.values().stream().sorted((x, y) -> y.lastRevenue - x.lastRevenue).forEach(x -> all.add(x.write()));
        out.put("all", all);
        NbtList free = new NbtList();
        for (int t = 0; t < TYPES.length; t++)
            for (String pl : PLACES[t])
                if (!st.biz.containsKey(pl)) {
                    NbtCompound c = new NbtCompound();
                    c.putInt("type", t);
                    c.putString("place", pl);
                    free.add(c);
                }
        out.put("free", free);
        out.putIntArray("open", OPEN);
        out.putIntArray("unit", UNIT_COST);
        LifeNet.send(p, "biz", out);
    }
}
