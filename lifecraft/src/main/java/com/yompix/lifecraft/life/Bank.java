package com.yompix.lifecraft.life;

import com.yompix.lifecraft.ModContent;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Банкомат: купюра = $10. Положить купюры на счёт или снять наличные. */
public final class Bank {
    private Bank() {}

    public static final int NOTE = 10;

    public static void useAtm(ServerPlayerEntity p, ItemStack stack) {
        if (stack.isOf(ModContent.BANKNOTE)) {
            int n = stack.getCount();
            stack.decrement(n);
            Life.earn(p, n * NOTE, "наличные на счёт");
            return;
        }
        LifeNet.send(p, "open_phone", Jobs.app("bank"));
    }

    static void atm(ServerPlayerEntity p, String action, int amount) {
        if (action.equals("withdraw")) {
            int notes = Math.max(1, Math.min(64, amount / NOTE));
            if (!Life.pay(p, notes * NOTE, "снято наличными")) return;
            School.give(p, new ItemStack(ModContent.BANKNOTE, notes));
        } else if (action.equals("deposit")) {
            int notes = p.getInventory().count(ModContent.BANKNOTE);
            if (notes == 0) {
                p.sendMessage(Text.literal("Нет купюр в инвентаре").formatted(Formatting.RED), true);
                return;
            }
            var inv = p.getInventory();
            for (int i = 0; i < inv.size(); i++) if (inv.getStack(i).isOf(ModContent.BANKNOTE)) inv.setStack(i, ItemStack.EMPTY);
            Life.earn(p, notes * NOTE, "наличные на счёт");
        }
    }
}
