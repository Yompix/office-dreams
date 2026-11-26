package com.yompix.novograd.life;

import net.minecraft.nbt.NbtCompound;

import com.yompix.novograd.ModContent;
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
            Life.get(p).money += v;
            Life.sync(p);
            return;
        }
        // экран банкомата: баланс и наличные в кармане
        NbtCompound n = new NbtCompound();
        n.putInt("balance", Life.get(p).money);
        n.putInt("cash", cash(p));
        LifeNet.send(p, "open_atm", n);
    }

    static int cash(ServerPlayerEntity p) {
        int c = Life.get(p).cash;
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
            // 0.9.6: наличные — в кошелёк (видно на экране); у банкомата не в «Сити-Банке» — комиссия 2 %
            int sum = Math.max(10, Math.min(5000, amount / 10 * 10));
            var bank = com.yompix.novograd.story.Story.place(p.getServerWorld(), "bank_tower");
            int fee = bank != null && bank.squaredDistanceTo(p.getPos()) < 40 * 40 ? 0 : Math.max(2, sum * com.yompix.novograd.gta.Money.ATM_FEE_PCT / 100);
            LifeData d = Life.get(p);
            if (d.money < sum + fee) {
                reply(p, "withdraw", false, sum, "Недостаточно средств на счёте");
                return;
            }
            d.money -= sum + fee;
            d.cash += sum;
            Life.sync(p);
            reply(p, "withdraw", true, sum, fee > 0 ? "комиссия $" + fee : "");
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
            LifeData dd = Life.get(p);
            total += dd.cash; // 0.9.6: и наличные из кошелька
            dd.cash = 0;
            if (total == 0) {
                p.sendMessage(Text.literal("Нет купюр в инвентаре").formatted(Formatting.RED), true);
                reply(p, "deposit", false, 0, "Нет купюр в инвентаре");
                return;
            }
            Life.get(p).money += total;
            Life.sync(p);
            reply(p, "deposit", true, total, "");
        }
    }
}
