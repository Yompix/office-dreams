package com.yompix.novograd.gta;

import com.yompix.novograd.life.Life;
import com.yompix.novograd.life.LifeData;
import com.yompix.novograd.life.LifeNet;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.PersistentState;

import java.util.*;

/**
 * Экономика Новограда — одна связанная система:
 *  • чистые деньги на карте (работа, миссии, бизнесы) и ГРЯЗНЫЕ наличные (ограбления, налёты, инкассаторы);
 *  • грязными платят у оружейника (чёрный рынок), а чтобы тратить везде — отмывают: бизнесы отмывают понемногу каждый день,
 *    Рэй на пристани — сразу всё, но берёт 30%;
 *  • риск: погиб — теряешь половину грязных наличных, задержан — полиция забирает все;
 *  • биржа: 6 компаний острова, их акции меняются сами и от твоих дел (ограбил банк — акции банка падают).
 */
public final class Economy {
    private Economy() {}

    /** Насколько далеко игрок по сюжету: 0 — акт I … 4 — акт V, 5 — сюжет пройден. */
    public static int act(LifeData d) {
        return d.storyDone ? 5 : Math.max(0, d.chapter);
    }

    // ------------------------------------------------------------------ грязные деньги
    public static void dirty(ServerPlayerEntity p, int amount, String why) {
        LifeData d = Life.get(p);
        d.dirty += amount;
        p.sendMessage(Text.literal("+$" + amount + " грязными  " + why + "  (отмыть: свой бизнес или Рэй на пристани)").formatted(Formatting.RED), true);
        Life.sync(p);
    }

    /** Платёж «сначала грязными» (чёрный рынок): true — хватило денег. */
    public static boolean payBlack(ServerPlayerEntity p, int amount, String why) {
        LifeData d = Life.get(p);
        int fromDirty = Math.min(d.dirty, amount);
        if (fromDirty + d.money < amount) {
            p.sendMessage(Text.literal("Не хватает денег: нужно $" + amount).formatted(Formatting.RED), true);
            return false;
        }
        d.dirty -= fromDirty;
        if (amount - fromDirty > 0) return Life.pay(p, amount - fromDirty, why);
        Life.sync(p);
        return true;
    }

    /** Погиб или задержан: грязные наличные теряются. */
    public static void onDownfall(ServerPlayerEntity p, boolean busted) {
        LifeData d = Life.get(p);
        if (d.dirty <= 0) return;
        int lost = busted ? d.dirty : d.dirty / 2;
        d.dirty -= lost;
        p.sendMessage(Text.literal((busted ? "🚔 Полиция изъяла" : "💀 Потеряно") + " $" + lost + " грязных денег. Деньги на карте целы.").formatted(Formatting.RED), false);
        Life.sync(p);
    }

    /** Отмыть через Рэя: всё сразу, 70% возвращается на карту. */
    public static void launderRay(ServerPlayerEntity p) {
        LifeData d = Life.get(p);
        if (d.dirty <= 0) {
            p.sendMessage(Text.literal("Рэй: «Нечего отмывать, друг. Приходи с наличкой.»").formatted(Formatting.GRAY), true);
            return;
        }
        int clean = d.dirty * 70 / 100;
        int fee = d.dirty - clean;
        d.dirty = 0;
        d.money += clean;
        Activities.title(p, "ДЕНЬГИ ОТМЫТЫ", "+$" + clean + " на карту · Рэй взял $" + fee);
        Life.sync(p);
    }

    /** Раз в игровой день: каждый бизнес отмывает до своего лимита (85% на карту). */
    public static int launderDaily(ServerPlayerEntity p, int capacity) {
        LifeData d = Life.get(p);
        int take = Math.min(d.dirty, capacity);
        if (take <= 0) return 0;
        d.dirty -= take;
        int clean = take * 85 / 100;
        d.money += clean;
        return clean;
    }

    // ------------------------------------------------------------------ биржа
    /** Тикер, название, начальная цена. */
    public static final String[][] STOCKS = {
            {"CITY", "Сити-Банк", "120"}, {"KRON", "Казино «Корона»", "85"}, {"NITR", "Нитро-Гараж", "40"},
            {"PORT", "Портовая компания", "60"}, {"RIVH", "Ривьера-Отели", "150"}, {"TAXI", "Такси «Жёлтый»", "25"},
    };

    public static final class Market extends PersistentState {
        final Map<String, Double> price = new LinkedHashMap<>();
        final Map<String, Deque<Double>> history = new HashMap<>();

        static final Type<Market> TYPE = new Type<>(Market::new, (n, reg) -> load(n), null);

        Market() {
            for (String[] s : STOCKS) {
                price.put(s[0], Double.parseDouble(s[2]));
                history.put(s[0], new ArrayDeque<>());
            }
        }

        static Market load(NbtCompound n) {
            Market m = new Market();
            for (String[] s : STOCKS) {
                if (n.contains(s[0])) m.price.put(s[0], n.getDouble(s[0]));
                NbtList h = n.getList("h_" + s[0], 6);
                for (int i = 0; i < h.size(); i++) m.history.get(s[0]).add(h.getDouble(i));
            }
            return m;
        }

        @Override
        public NbtCompound writeNbt(NbtCompound n, net.minecraft.registry.RegistryWrapper.WrapperLookup reg) {
            price.forEach(n::putDouble);
            history.forEach((k, v) -> {
                NbtList l = new NbtList();
                for (double x : v) l.add(net.minecraft.nbt.NbtDouble.of(x));
                n.put("h_" + k, l);
            });
            return n;
        }
    }

    public static Market market(MinecraftServer s) {
        return s.getOverworld().getPersistentStateManager().getOrCreate(Market.TYPE, "novograd_market");
    }

    /** Новость на бирже: цена компании резко меняется (pct = −0.3 → −30%). */
    public static void shock(MinecraftServer s, String ticker, double pct, String news) {
        Market m = market(s);
        Double p = m.price.get(ticker);
        if (p == null) return;
        m.price.put(ticker, Math.max(1, p * (1 + pct)));
        m.markDirty();
        String name = Arrays.stream(STOCKS).filter(x -> x[0].equals(ticker)).findFirst().map(x -> x[1]).orElse(ticker);
        for (ServerPlayerEntity pl : s.getPlayerManager().getPlayerList())
            pl.sendMessage(Text.literal("📈 Биржа: " + name + " " + (pct >= 0 ? "+" : "") + Math.round(pct * 100) + "% — " + news).formatted(pct >= 0 ? Formatting.GREEN : Formatting.RED), false);
    }

    private static long lastHour = -1;

    /** Раз в игровой час: цены гуляют (небольшой рост в среднем), история для графика. */
    public static void tick(MinecraftServer s) {
        ServerWorld w = s.getOverworld();
        long hour = w.getTimeOfDay() / 1000;
        if (hour == lastHour) return;
        lastHour = hour;
        Market m = market(s);
        Random r = new Random(hour * 31 + 7);
        for (String[] st : STOCKS) {
            double p = m.price.get(st[0]);
            double base = Double.parseDouble(st[2]);
            double drift = (base - p) / base * 0.02 + 0.002;              // тянет к «справедливой» цене и чуть вверх
            p = Math.max(1, p * (1 + drift + r.nextGaussian() * 0.025));
            m.price.put(st[0], p);
            Deque<Double> h = m.history.get(st[0]);
            h.addLast(p);
            while (h.size() > 48) h.removeFirst();
        }
        m.markDirty();
    }

    public static void trade(ServerPlayerEntity p, String ticker, int qty) {
        Market m = market(p.getServer());
        Double price = m.price.get(ticker);
        if (price == null || qty == 0) return;
        LifeData d = Life.get(p);
        int have = d.shares.getOrDefault(ticker, 0);
        if (qty > 0) {
            int cost = (int) Math.ceil(price * qty * 1.01); // комиссия 1%
            if (!Life.pay(p, cost, "акции " + ticker)) return;
            d.shares.put(ticker, have + qty);
        } else {
            int sell = Math.min(have, -qty);
            if (sell <= 0) return;
            int got = (int) Math.floor(price * sell * 0.99);
            d.shares.put(ticker, have - sell);
            d.money += got;
        }
        Life.sync(p);
        sendState(p);
    }

    /** Состояние для приложения «Финансы». */
    public static void sendState(ServerPlayerEntity p) {
        LifeData d = Life.get(p);
        Market m = market(p.getServer());
        NbtCompound n = new NbtCompound();
        n.putInt("money", d.money);
        n.putInt("dirty", d.dirty);
        NbtList st = new NbtList();
        for (String[] s : STOCKS) {
            NbtCompound c = new NbtCompound();
            c.putString("t", s[0]);
            c.putString("name", s[1]);
            c.putDouble("price", m.price.get(s[0]));
            c.putInt("own", d.shares.getOrDefault(s[0], 0));
            NbtList h = new NbtList();
            for (double x : m.history.get(s[0])) h.add(net.minecraft.nbt.NbtDouble.of(x));
            c.put("h", h);
            st.add(c);
        }
        n.put("stocks", st);
        n.put("biz", Heists.businessState(p));
        LifeNet.send(p, "eco_state", n);
    }
}
