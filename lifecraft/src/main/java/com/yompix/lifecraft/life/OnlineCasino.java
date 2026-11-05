package com.yompix.lifecraft.life;

import com.yompix.lifecraft.ModContent;
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
        payout(p, h.bet, win);
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
    record Crash(int bet, long start, double point) {}

    private static final Map<UUID, Crash> CRASH = new HashMap<>();

    /** Множитель через t тиков: 1.00 → ×2 примерно за 11 секунд. */
    public static double mult(long ticks) {
        return Math.floor(Math.exp(ticks * 0.0032) * 100) / 100;
    }

    static void crashStart(ServerPlayerEntity p, int bet) {
        if (!adult(p) || CRASH.containsKey(p.getUuid()) || !betOk(bet) || !Life.pay(p, bet, "")) return;
        double u = p.getRandom().nextDouble();
        double point = Math.max(1.0, Math.floor(0.97 / (1 - u) * 100) / 100);
        CRASH.put(p.getUuid(), new Crash(bet, p.getServerWorld().getTime(), point));
        NbtCompound n = new NbtCompound();
        n.putLong("start", p.getServerWorld().getTime());
        LifeNet.send(p, "crash_start", n);
    }

    static void crashCashout(ServerPlayerEntity p) {
        Crash c = CRASH.get(p.getUuid());
        if (c == null) return;
        double m = mult(p.getServerWorld().getTime() - c.start());
        if (m >= c.point()) return; // уже поздно — краш обработает tick
        CRASH.remove(p.getUuid());
        int win = (int) Math.floor(c.bet() * m);
        payout(p, c.bet(), win);
        NbtCompound n = new NbtCompound();
        n.putDouble("at", m);
        n.putInt("win", win);
        LifeNet.send(p, "crash_cashout", n);
    }

    /** Каждый тик: у кого множитель дошёл до точки краша — ставка сгорает. */
    public static void tick(ServerPlayerEntity p) {
        Crash c = CRASH.get(p.getUuid());
        if (c == null) return;
        double m = mult(p.getServerWorld().getTime() - c.start());
        if (m >= c.point()) {
            CRASH.remove(p.getUuid());
            payout(p, c.bet(), 0);
            NbtCompound n = new NbtCompound();
            n.putDouble("point", c.point());
            LifeNet.send(p, "crash_end", n);
        }
    }

    // ---------------------------------------------------------------- монетка
    static void coin(ServerPlayerEntity p, int bet, boolean heads) {
        if (!adult(p) || !betOk(bet) || !Life.pay(p, bet, "")) return;
        boolean won = p.getRandom().nextInt(100) < 49; // 2% — заведению
        boolean landedHeads = won == heads;
        int win = won ? bet * 2 : 0;
        payout(p, bet, win);
        NbtCompound n = new NbtCompound();
        n.putBoolean("heads", landedHeads);
        n.putInt("win", win);
        LifeNet.send(p, "coin_result", n);
    }

    private static void payout(ServerPlayerEntity p, int bet, int win) {
        LifeData d = Life.get(p);
        d.money += win;
        if (win > bet) {
            d.casinoWon += win - bet;
            Life.sound(p, ModContent.CASH, 1f);
            d.mood = Math.min(100, d.mood + 3);
        } else if (win < bet) {
            d.casinoLost += bet - win;
            d.mood = Math.max(0, d.mood - 2);
        }
        if (win >= bet * 10) Achievements.give(p, "jackpot");
        Life.sync(p);
    }
}
