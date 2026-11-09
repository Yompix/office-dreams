package com.yompix.lifecraft.life;

import com.yompix.lifecraft.ModContent;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.random.Random;

/**
 * Казино на игровые деньги мода: игровой автомат и рулетка. Только после школы.
 * Исход всегда решает сервер; клиент лишь красиво крутит барабаны и колесо.
 * Отдача автомата ≈95%, рулетки ≈97% — как в настоящем казино, в среднем выигрывает заведение.
 */
public final class Casino {
    private Casino() {}

    public static final String[] SYMBOLS = {"cherry", "lemon", "bell", "star", "seven", "diamond"};
    private static final int[] WEIGHTS = {30, 25, 20, 12, 8, 5};
    private static final int[] THREE = {4, 8, 12, 20, 40, 100};
    public static final int[] BETS = {5, 10, 25, 50, 100, 250};
    private static final int[] REDS = {1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36};

    private static boolean allowed(ServerPlayerEntity p, int bet) {
        if (!Life.get(p).adult()) {
            p.sendMessage(Text.literal("🔞 Казино только для взрослых. Сначала закончи школу!").formatted(Formatting.RED), true);
            return false;
        }
        for (int b : BETS) if (b == bet) return true;
        return false;
    }

    private static int roll(Random r) {
        int x = r.nextInt(100);
        for (int i = 0; i < WEIGHTS.length; i++) {
            if (x < WEIGHTS[i]) return i;
            x -= WEIGHTS[i];
        }
        return 0;
    }

    static void spin(ServerPlayerEntity p, int bet) {
        if (!allowed(p, bet) || !Life.pay(p, bet, "")) return;
        Random r = p.getRandom();
        int a = roll(r), b = roll(r), c = roll(r);
        int win;
        if (a == b && b == c) win = bet * THREE[a];
        else {
            int cherries = (a == 0 ? 1 : 0) + (b == 0 ? 1 : 0) + (c == 0 ? 1 : 0);
            win = cherries == 2 ? bet * 2 : cherries == 1 ? bet * 2 / 5 : 0;
        }
        settle(p, bet, win);
        NbtCompound n = new NbtCompound();
        n.putIntArray("reels", new int[]{a, b, c});
        n.putInt("win", win);
        n.putBoolean("jackpot", a == b && b == c && a >= 4);
        LifeNet.send(p, "slots_result", n);
    }

    /** kind: red, black, even, odd, low (1–18), high (19–36), dozen1..3, number. */
    static void roulette(ServerPlayerEntity p, int bet, String kind, int number) {
        if (!allowed(p, bet) || !Life.pay(p, bet, "")) return;
        int n = p.getRandom().nextInt(37);
        boolean red = isRed(n);
        int mult = switch (kind) {
            case "red" -> n != 0 && red ? 2 : 0;
            case "black" -> n != 0 && !red ? 2 : 0;
            case "even" -> n != 0 && n % 2 == 0 ? 2 : 0;
            case "odd" -> n % 2 == 1 ? 2 : 0;
            case "low" -> n >= 1 && n <= 18 ? 2 : 0;
            case "high" -> n >= 19 ? 2 : 0;
            case "dozen1" -> n >= 1 && n <= 12 ? 3 : 0;
            case "dozen2" -> n >= 13 && n <= 24 ? 3 : 0;
            case "dozen3" -> n >= 25 ? 3 : 0;
            case "number" -> n == number ? 36 : 0;
            default -> 0;
        };
        int win = bet * mult;
        settle(p, bet, win);
        NbtCompound out = new NbtCompound();
        out.putInt("number", n);
        out.putInt("win", win);
        LifeNet.send(p, "roulette_result", out);
    }

    public static boolean isRed(int n) {
        for (int r : REDS) if (r == n) return true;
        return false;
    }

    private static void settle(ServerPlayerEntity p, int bet, int win) {
        LifeData d = Life.get(p);
        if (win > 0) {
            d.money += win;
            if (win > bet) d.casinoWon += win - bet;
            else d.casinoLost += bet - win;
            Life.sound(p, win >= bet * 10 ? ModContent.LEVEL_UP : ModContent.CASH, 1f);
        } else {
            d.casinoLost += bet;
        }
        if (win >= bet * 20)
            p.getServer().getPlayerManager().broadcast(Text.literal("🎰 " + p.getName().getString() + " сорвал куш $" + win + " в казино!").formatted(Formatting.GOLD), false);
        Life.sync(p);
    }
}
