package com.yompix.lifecraft.life;

import net.minecraft.nbt.NbtCompound;

import com.yompix.lifecraft.ModContent;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Банкомат: купюра = $10. Положить купюры на счёт или снять наличные. */
public final class Bank {
    private Bank() {}

    public static final int NOTE = 10;

    /** Купюры: $100, $50, $10. */
    private static final net.minecraft.item.Item[] NOTES = {ModContent.BANKNOTE_100, ModContent.BANKNOTE_50, ModContent.BANKNOTE};
    private static final int[] VALUES = {100, 50, 10};

    public static int value(ItemStack s) {
        for (int i = 0; i < NOTES.length; i++) if (s.isOf(NOTES[i])) return VALUES[i] * s.getCount();
        return 0;
    }

    public static void useAtm(ServerPlayerEntity p, ItemStack stack) {
        int v = value(stack);
        if (v > 0) {
            stack.decrement(stack.getCount());
            Life.earn(p, v, "наличные на счёт");
            return;
        }
        // экран банкомата: баланс и наличные в кармане
        NbtCompound n = new NbtCompound();
        n.putInt("balance", Life.get(p).money);
        n.putInt("cash", cash(p));
        LifeNet.send(p, "open_atm", n);
    }

    static int cash(ServerPlayerEntity p) {
        int c = 0;
        var inv = p.getInventory();
        for (int i = 0; i < inv.size(); i++) c += value(inv.getStack(i));
        return c;
    }

    private static void reply(ServerPlayerEntity p, String action, boolean ok, int sum, String msg) {
        NbtCompound n = new NbtCompound();
        n.putString("action", action);
        n.putBoolean("ok", ok);
        n.putInt("sum", sum);
        n.putString("msg", msg);
        n.putInt("balance", Life.get(p).money);
        n.putInt("cash", cash(p));
        LifeNet.send(p, "atm_result", n);
    }

    static void atm(ServerPlayerEntity p, String action, int amount) {
        if (action.equals("withdraw")) {
            int sum = Math.max(10, Math.min(5000, amount / 10 * 10));
            if (!Life.pay(p, sum, "снято наличными")) {
                reply(p, "withdraw", false, sum, "Недостаточно средств на счёте");
                return;
            }
            // крупными купюрами: $250 = 2×$100 + 1×$50
            int left = sum;
            for (int i = 0; i < NOTES.length; i++) {
                int n = left / VALUES[i];
                left -= n * VALUES[i];
                while (n > 0) {
                    int k = Math.min(64, n);
                    School.give(p, new ItemStack(NOTES[i], k));
                    n -= k;
                }
            }
            reply(p, "withdraw", true, sum, "");
        } else if (action.equals("deposit")) {
            var inv = p.getInventory();
            int total = 0;
            for (int i = 0; i < inv.size(); i++) {
                int v = value(inv.getStack(i));
                if (v > 0) {
                    total += v;
                    inv.setStack(i, ItemStack.EMPTY);
                }
            }
            if (total == 0) {
                p.sendMessage(Text.literal("Нет купюр в инвентаре").formatted(Formatting.RED), true);
                reply(p, "deposit", false, 0, "Нет купюр в инвентаре");
                return;
            }
            Life.earn(p, total, "наличные на счёт");
            reply(p, "deposit", true, total, "");
        }
    }
}
