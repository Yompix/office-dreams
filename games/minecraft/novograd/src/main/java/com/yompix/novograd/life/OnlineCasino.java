package com.yompix.novograd.life;

import com.yompix.novograd.ModContent;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Онлайн-казино в телефоне (игровые деньги, только взрослым): блэкджек, «Краш», монетка.
 * Карты, точка краша и монетка — на сервере; клиент только показывает.
 */
public final class OnlineCasino {
    private OnlineCasino() {}

    // ---------------------------------------------------------------- блэкджек
    static final class Hand {
        final List<Integer> deck = new ArrayList<>(), player = new ArrayList<>(), dealer = new ArrayList<>();
        int bet;
        boolean done;
    }

    private static final Map<UUID, Hand> HANDS = new HashMap<>();

    private static boolean adult(ServerPlayerEntity p) {
        if (Life.get(p).adult()) return true;
        p.sendMessage(Text.literal("Онлайн-казино только для взрослых").formatted(Formatting.RED), true);
        return false;
    }

    private static boolean betOk(int bet) {
        for (int b : Casino.BETS) if (b == bet) return true;
        return false;
    }

    /** Карта: 0–51, достоинство = id % 13 (0 — туз, 10–12 — картинки), масть = id / 13. */
    static int score(List<Integer> cards) {
        int s = 0, aces = 0;
        for (int c : cards) {
            int r = c % 13;
            if (r == 0) {
                aces++;
                s += 11;
            } else s += Math.min(10, r + 1);
        }
        while (s > 21 && aces-- > 0) s -= 10;
        return s;
    }

    static void blackjack(ServerPlayerEntity p, String action, int bet) {
        if (!adult(p)) return;
        Hand h = HANDS.get(p.getUuid());
        Random r = p.getRandom();
        switch (action) {
            case "deal" -> {
                if (h != null && !h.done) return;
                if (!betOk(bet) || !Life.pay(p, bet, "")) return;
                h = new Hand();
                for (int i = 0; i < 52; i++) h.deck.add(i);
                java.util.Collections.shuffle(h.deck, new java.util.Random(r.nextLong()));
                h.bet = bet;
                h.player.add(draw(h));
                h.dealer.add(draw(h));
                h.player.add(draw(h));
                h.dealer.add(draw(h));
                HANDS.put(p.getUuid(), h);
                if (score(h.player) == 21) finish(p, h);
            }
            case "hit" -> {
                if (h == null || h.done) return;
                h.player.add(draw(h));
                if (score(h.player) >= 21) finish(p, h);
            }
            case "double" -> {
                if (h == null || h.done || h.player.size() != 2 || !Life.pay(p, h.bet, "")) return;
                h.bet *= 2;
                h.player.add(draw(h));
                finish(p, h);
            }
            case "stand" -> {
                if (h == null || h.done) return;
                finish(p, h);
            }
            default -> {}
        }
        if (h != null) send(p, h);
    }

    private static int draw(Hand h) {
        return h.deck.remove(h.deck.size() - 1);
    }

    private static void finish(ServerPlayerEntity p, Hand h) {
        int ps = score(h.player);
        if (ps <= 21) while (score(h.dealer) < 17) h.dealer.add(draw(h));
        int ds = score(h.dealer);
        boolean bj = ps == 21 && h.player.size() == 2;
        int win;
        if (ps > 21) win = 0;
        else if (bj && !(ds == 21 && h.dealer.size() == 2)) win = h.bet * 5 / 2;
        else if (ds > 21 || ps > ds) win = h.bet * 2;
        else if (ps == ds) win = h.bet;
        else win = 0;
        h.done = true;
        payout(p, h.bet, win, "в блэкджек");
    }

    private static void send(ServerPlayerEntity p, Hand h) {
        NbtCompound n = new NbtCompound();
        n.putIntArray("player", h.player);
        n.putIntArray("dealer", h.done ? h.dealer : List.of(h.dealer.get(0)));
        n.putBoolean("done", h.done);
        n.putInt("ps", score(h.player));
        n.putInt("ds", h.done ? score(h.dealer) : -1);
        n.putInt("bet", h.bet);
        LifeNet.send(p, "bj_state", n);
    }

    // ---------------------------------------------------------------- «Краш»: множитель растёт, успей забрать
    /**
     * Раунд игрока. Время — счётчик тиков сервера (не время мира: его меняют /time и сон).
     * auto — автовывод ×100 (0 — нет). Раунд живёт, пока не выведен или не крашнулся; выплата ровно одна.
     */
    private static final class Crash {
        final int bet, auto;
        final long start;
        final double point;

        Crash(int bet, long start, double point, int auto) {
            this.bet = bet;
            this.start = start;
            this.point = point;
            this.auto = auto;
        }
    }

    private static final Map<UUID, Crash> CRASH = new HashMap<>();
    /** Последние 10 точек краша у каждого игрока (новые — в начале). */
    private static final Map<UUID, java.util.ArrayDeque<Integer>> CRASH_HISTORY = new HashMap<>();
    private static final java.util.Random CRASH_RNG = new java.util.Random();

    /** Множитель через t тиков (см. {@link CasinoMath#crashMult}). */
    public static double mult(long ticks) {
        return CasinoMath.crashMult(ticks);
    }

    private static long now(ServerPlayerEntity p) {
        return p.getServer() == null ? 0 : p.getServer().getTicks();
    }

    static void crashStart(ServerPlayerEntity p, int bet, int auto) {
        if (!adult(p) || CRASH.containsKey(p.getUuid())) return;
        boolean autoOk = false;
        for (int v : CasinoMath.CRASH_AUTO) autoOk |= v == auto;
        if (!autoOk || !betOk(bet) || !Life.pay(p, bet, "")) return;
        double point = CasinoMath.crashPoint(new java.util.Random(CRASH_RNG.nextLong() ^ p.getRandom().nextLong()));
        CRASH.put(p.getUuid(), new Crash(bet, now(p), point, auto));
        NbtCompound n = new NbtCompound();
        n.putInt("bet", bet);
        n.putInt("auto", auto);
        n.putLong("e", 0);
        LifeNet.send(p, "crash_start", n);
        tick(p); // мгновенный краш на ×1.00 — сразу
    }

    static void crashCashout(ServerPlayerEntity p) {
        Crash c = CRASH.get(p.getUuid());
        if (c == null) return;
        double m = mult(now(p) - c.start);
        if (m >= c.point) return; // уже поздно — краш обработает tick
        cashout(p, c, m);
    }

    /** Вывод: раунд снимается до выплаты, поэтому второй раз заплатить нельзя. Точку краша сообщаем сразу. */
    private static void cashout(ServerPlayerEntity p, Crash c, double m) {
        if (CRASH.remove(p.getUuid()) != c) return;
        int win = (int) Math.floor(c.bet * m + 1e-9);
        payout(p, c.bet, win, "в краше");
        remember(p, c.point);
        NbtCompound n = new NbtCompound();
        n.putDouble("at", m);
        n.putInt("win", win);
        n.putDouble("point", c.point);
        n.putLong("e", now(p) - c.start);
        n.putIntArray("hist", history(p));
        LifeNet.send(p, "crash_cashout", n);
    }

    private static void remember(ServerPlayerEntity p, double point) {
        var h = CRASH_HISTORY.computeIfAbsent(p.getUuid(), u -> new java.util.ArrayDeque<>());
        h.addFirst((int) Math.round(point * 100));
        while (h.size() > 10) h.removeLast();
    }

    private static int[] history(ServerPlayerEntity p) {
        var h = CRASH_HISTORY.get(p.getUuid());
        return h == null ? new int[0] : h.stream().mapToInt(Integer::intValue).toArray();
    }

    /** Клиент открыл «Краш»: идёт ли раунд и история. */
    static void crashState(ServerPlayerEntity p) {
        NbtCompound n = new NbtCompound();
        Crash c = CRASH.get(p.getUuid());
        n.putBoolean("running", c != null);
        if (c != null) {
            n.putInt("bet", c.bet);
            n.putInt("auto", c.auto);
            n.putLong("e", now(p) - c.start);
        }
        n.putIntArray("hist", history(p));
        LifeNet.send(p, "crash_state", n);
    }

    /** Каждый тик: автовывод, краш, а раз в 4 тика — сверка времени с клиентом. */
    public static void tick(ServerPlayerEntity p) {
        Crash c = CRASH.get(p.getUuid());
        if (c == null) return;
        long e = now(p) - c.start;
        double m = mult(e);
        double auto = c.auto / 100.0;
        if (c.auto > 0 && m >= auto && auto < c.point) {
            cashout(p, c, auto); // ровно на заданном множителе
            return;
        }
        if (m >= c.point) {
            CRASH.remove(p.getUuid());
            payout(p, c.bet, 0, "в краше");
            remember(p, c.point);
            NbtCompound n = new NbtCompound();
            n.putDouble("point", c.point);
            n.putIntArray("hist", history(p));
            LifeNet.send(p, "crash_end", n);
            return;
        }
        if (e % 4 == 0) {
            NbtCompound n = new NbtCompound();
            n.putLong("e", e);
            LifeNet.send(p, "crash_sync", n);
        }
    }

    // ---------------------------------------------------------------- монетка
    static void coin(ServerPlayerEntity p, int bet, boolean heads) {
        if (!adult(p) || !betOk(bet) || !Life.pay(p, bet, "")) return;
        boolean won = p.getRandom().nextInt(100) < 49; // 2% — заведению
        boolean landedHeads = won == heads;
        int win = won ? bet * 2 : 0;
        payout(p, bet, win, "в монетку");
        NbtCompound n = new NbtCompound();
        n.putBoolean("heads", landedHeads);
        n.putInt("win", win);
        LifeNet.send(p, "coin_result", n);
    }

    private static void payout(ServerPlayerEntity p, int bet, int win, String where) {
        CasinoGames.settle(p, bet, win, where);
    }
}
