package com.yompix.novograd.life;

import com.yompix.novograd.ModContent;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Новые игры казино (телефон «Фортуна.онлайн» и столы в залах): видеопокер, кости, колесо фортуны,
 * тараканьи бега, плинко и мины. Всё решает сервер (математика — {@link CasinoMath}), состояние — у каждого
 * игрока своё (ключ — UUID), клиент только показывает. Пакеты: C2S «cg» {g, a, …} → S2C «cg» {g, …}.
 */
public final class CasinoGames {
    private CasinoGames() {}

    private static final Random RNG = new Random();

    private static Random rng(ServerPlayerEntity p) {
        return new Random(RNG.nextLong() ^ p.getRandom().nextLong());
    }

    private static boolean betOk(int bet) {
        for (int b : Casino.BETS) if (b == bet) return true;
        return false;
    }

    public static void handle(ServerPlayerEntity p, NbtCompound d) {
        String g = d.getString("g"), a = d.getString("a");
        int bet = d.getInt("bet");
        switch (g) {
            case "crash" -> OnlineCasino.crashState(p);
            case "poker" -> poker(p, a, bet, d.getInt("hold"));
            case "dice" -> dice(p, bet, d.getInt("pick"));
            case "wheel" -> wheel(p, bet, d.getInt("pick"));
            case "race" -> race(p, a, bet, d.getInt("pick"));
            case "plinko" -> plinko(p, bet, d.getInt("risk"));
            case "mines" -> mines(p, a, bet, d.getInt("mines"), d.getInt("cell"));
            default -> {}
        }
    }

    private static void send(ServerPlayerEntity p, String game, NbtCompound n) {
        n.putString("g", game);
        LifeNet.send(p, "cg", n);
    }

    /** Ставка: только из списка и только если хватает денег (списывается сразу). */
    private static boolean stake(ServerPlayerEntity p, int bet) {
        return betOk(bet) && Life.pay(p, bet, "");
    }

    /**
     * Расчёт любой игры казино: деньги, статистика, настроение, достижение и крупный выигрыш — в общий чат.
     * where — «на колесе», «в покере»…
     */
    public static void settle(ServerPlayerEntity p, int bet, long winL, String where) {
        int win = (int) Math.min(Integer.MAX_VALUE / 2, winL);
        LifeData d = Life.get(p);
        d.money += win;
        if (win > bet) {
            d.casinoWon += win - bet;
            Life.sound(p, win >= bet * 10 ? ModContent.LEVEL_UP : ModContent.CASH, 1f);
            d.mood = Math.min(100, d.mood + 3);
        } else if (win < bet) {
            d.casinoLost += bet - win;
            d.mood = Math.max(0, d.mood - 2);
        }
        if (win >= bet * 10) Achievements.give(p, "jackpot");
        if (win - bet >= 1000 && win >= bet * 5) {
            var server = p.getServer();
            if (server != null) server.getPlayerManager().broadcast(Text.literal("🎰 Игрок " + p.getName().getString()
                    + " выиграл $" + win + " " + where + "!").formatted(Formatting.GOLD), false);
        }
        Life.sync(p);
    }

    // ================================================================ видеопокер
    private static final class Poker {
        int[] deck;
        int[] hand = new int[5];
        int next, bet;
        boolean drawn = true;
    }

    private static final Map<UUID, Poker> POKER = new HashMap<>();

    private static void poker(ServerPlayerEntity p, String a, int bet, int hold) {
        Poker s = POKER.get(p.getUuid());
        NbtCompound n = new NbtCompound();
        if (a.equals("deal")) {
            if (s == null || s.drawn) {
                if (!stake(p, bet)) return;
                s = new Poker();
                s.deck = CasinoMath.deck(rng(p));
                System.arraycopy(s.deck, 0, s.hand, 0, 5);
                s.next = 5;
                s.bet = bet;
                s.drawn = false;
                POKER.put(p.getUuid(), s);
            } // иначе раздача уже идёт (экран открыли заново) — просто повторяем её
            n.putInt("bet", s.bet);
            n.putIntArray("hand", s.hand);
            n.putBoolean("drawn", false);
            n.putInt("now", CasinoMath.pokerHand(s.hand));
            send(p, "poker", n);
        } else if (a.equals("draw")) {
            if (s == null || s.drawn) return;
            for (int i = 0; i < 5; i++) if ((hold & (1 << i)) == 0) s.hand[i] = s.deck[s.next++];
            s.drawn = true;
            int res = CasinoMath.pokerHand(s.hand);
            long win = (long) s.bet * CasinoMath.POKER_PAY[res];
            settle(p, s.bet, win, "в покере");
            n.putIntArray("hand", s.hand);
            n.putBoolean("drawn", true);
            n.putInt("res", res);
            n.putInt("win", (int) win);
            n.putInt("hold", hold);
            n.putInt("bet", s.bet);
            send(p, "poker", n);
        }
    }

    // ================================================================ кости
    private static void dice(ServerPlayerEntity p, int bet, int pick) {
        if (pick < 0 || pick >= CasinoMath.DICE_BETS.length || !stake(p, bet)) return;
        Random r = rng(p);
        int a = 1 + r.nextInt(6), b = 1 + r.nextInt(6);
        long win = CasinoMath.diceWins(pick, a, b) ? (long) bet * CasinoMath.DICE_PAY[pick] / 100 : 0;
        settle(p, bet, win, "в кости");
        NbtCompound n = new NbtCompound();
        n.putInt("a", a);
        n.putInt("b", b);
        n.putInt("pick", pick);
        n.putInt("win", (int) win);
        n.putInt("bet", bet);
        send(p, "dice", n);
    }

    // ================================================================ колесо фортуны
    private static void wheel(ServerPlayerEntity p, int bet, int pick) {
        boolean ok = false;
        for (int v : CasinoMath.WHEEL_BETS) ok |= v == pick;
        if (!ok || !stake(p, bet)) return;
        int[] seq = CasinoMath.wheelSpin(rng(p));
        long win = CasinoMath.wheelWin(bet, pick, seq);
        settle(p, bet, win, "на колесе фортуны");
        NbtCompound n = new NbtCompound();
        n.putIntArray("seq", seq);
        n.putInt("pick", pick);
        n.putInt("win", (int) win);
        n.putInt("bet", bet);
        send(p, "wheel", n);
    }

    // ================================================================ тараканьи бега
    private record Card(int[] names, double[] probs) {}

    private static final Map<UUID, Card> RACE = new HashMap<>();

    private static Card card(ServerPlayerEntity p) {
        return RACE.computeIfAbsent(p.getUuid(), u -> {
            Random r = rng(p);
            int[] names = new int[5];
            java.util.List<Integer> all = new java.util.ArrayList<>();
            for (int i = 0; i < CasinoMath.RUNNERS.length; i++) all.add(i);
            java.util.Collections.shuffle(all, r);
            for (int i = 0; i < 5; i++) names[i] = all.get(i);
            return new Card(names, CasinoMath.raceProbs(r));
        });
    }

    private static void putCard(NbtCompound n, Card c) {
        n.putIntArray("names", c.names());
        int[] odds = new int[5];
        for (int i = 0; i < 5; i++) odds[i] = CasinoMath.raceOdds10(c.probs()[i]);
        n.putIntArray("odds", odds);
    }

    private static void race(ServerPlayerEntity p, String a, int bet, int pick) {
        NbtCompound n = new NbtCompound();
        if (a.equals("run")) {
            Card c = RACE.get(p.getUuid());
            if (c == null || pick < 0 || pick >= 5 || !stake(p, bet)) return;
            RACE.remove(p.getUuid());
            int[] order = CasinoMath.raceOrder(rng(p), c.probs());
            long win = order[0] == pick ? (long) bet * CasinoMath.raceOdds10(c.probs()[pick]) / 10 : 0;
            settle(p, bet, win, "на тараканьих бегах");
            putCard(n, c);
            n.putIntArray("order", order);
            n.putInt("pick", pick);
            n.putInt("win", (int) win);
            n.putInt("bet", bet);
            NbtCompound next = new NbtCompound();
            putCard(next, card(p));
            n.put("next", next);
        } else putCard(n, card(p));
        send(p, "race", n);
    }

    // ================================================================ плинко
    private static void plinko(ServerPlayerEntity p, int bet, int risk) {
        if (risk < 0 || risk > 2 || !stake(p, bet)) return;
        int path = CasinoMath.plinkoPath(rng(p));
        int slot = CasinoMath.plinkoSlot(path);
        long win = (long) bet * CasinoMath.PLINKO[risk][slot] / 10;
        settle(p, bet, win, "в плинко");
        NbtCompound n = new NbtCompound();
        n.putInt("path", path);
        n.putInt("slot", slot);
        n.putInt("risk", risk);
        n.putInt("win", (int) win);
        n.putInt("bet", bet);
        send(p, "plinko", n);
    }

    // ================================================================ мины
    private static final class Mines {
        boolean[] grid;
        boolean[] open = new boolean[25];
        int bet, mines, opened;
    }

    private static final Map<UUID, Mines> MINES = new HashMap<>();

    private static void mines(ServerPlayerEntity p, String a, int bet, int mines, int cell) {
        Mines s = MINES.get(p.getUuid());
        NbtCompound n = new NbtCompound();
        switch (a) {
            case "start" -> {
                boolean ok = false;
                for (int m : CasinoMath.MINES_COUNTS) ok |= m == mines;
                if (s != null || !ok || !stake(p, bet)) return;
                s = new Mines();
                s.grid = CasinoMath.minesGrid(rng(p), mines);
                s.bet = bet;
                s.mines = mines;
                MINES.put(p.getUuid(), s);
                n.putString("state", "play");
            }
            case "open" -> {
                if (s == null || cell < 0 || cell >= 25 || s.open[cell]) return;
                s.open[cell] = true;
                if (s.grid[cell]) {
                    MINES.remove(p.getUuid());
                    settle(p, s.bet, 0, "в минах");
                    n.putString("state", "boom");
                    n.putInt("cell", cell);
                } else {
                    s.opened++;
                    n.putString("state", "play");
                    if (s.opened == 25 - s.mines) { // открыл всё — забираем сами
                        cashout(p, s, n);
                    }
                }
            }
            case "cash" -> {
                if (s == null || s.opened == 0) return;
                cashout(p, s, n);
            }
            case "state" -> {
                if (s == null) n.putString("state", "idle");
                else n.putString("state", "play");
            }
            default -> {
                return;
            }
        }
        if (s != null) {
            byte[] open = new byte[25];
            for (int i = 0; i < 25; i++) open[i] = (byte) (s.open[i] ? 1 : 0);
            n.putByteArray("open", open);
            n.putInt("mines", s.mines);
            n.putInt("bet", s.bet);
            n.putInt("opened", s.opened);
            if (!n.getString("state").equals("play")) {
                byte[] grid = new byte[25];
                for (int i = 0; i < 25; i++) grid[i] = (byte) (s.grid[i] ? 1 : 0);
                n.putByteArray("grid", grid);
            }
        }
        send(p, "mines", n);
    }

    private static void cashout(ServerPlayerEntity p, Mines s, NbtCompound n) {
        MINES.remove(p.getUuid());
        long win = CasinoMath.minesPay(s.bet, CasinoMath.minesMult(s.mines, s.opened));
        settle(p, s.bet, win, "в минах");
        n.putString("state", "cashed");
        n.putInt("win", (int) win);
    }
}
