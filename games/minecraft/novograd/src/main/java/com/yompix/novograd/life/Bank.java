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

    /** 0.9.8: у какого банкомата стоит игрок (после карты и PIN) — снять/внести можно только здесь. */
    private static final java.util.Map<java.util.UUID, net.minecraft.util.math.BlockPos> AT = new java.util.HashMap<>();

    /**
     * 0.9.8: банкомат «руками» — подошёл, встал лицом, достал карту, вставил, набрал PIN (видно всем рядом), потом экран
     * суммы. Купюры в руке — по-старому кладутся на счёт сразу (но тоже с анимацией).
     */
    public static void useAtm(ServerPlayerEntity p, ItemStack stack, net.minecraft.util.math.BlockPos pos, net.minecraft.block.BlockState state) {
        var face = state.contains(net.minecraft.state.property.Properties.HORIZONTAL_FACING) ? state.get(net.minecraft.state.property.Properties.HORIZONTAL_FACING)
                : net.minecraft.util.math.Direction.fromRotation(p.getYaw()).getOpposite();
        net.minecraft.util.math.Vec3d anchor = Hands.front(pos, face, 0.9);
        net.minecraft.util.math.Vec3d target = new net.minecraft.util.math.Vec3d(pos.getX() + 0.5, pos.getY() + 0.85, pos.getZ() + 0.5);
        int v = value(stack);
        if (v > 0) {
            Hands.Session s = Hands.start(p, HandKind.ATM_DEPOSIT, "notes", anchor, target);
            if (s == null) return;
            s.at(20, () -> {
                int got = value(stack);
                if (got <= 0) return;
                stack.decrement(stack.getCount());
                Life.get(p).money += got;
                Life.sync(p);
                p.sendMessage(Text.literal("+$" + got + " на карту").formatted(Formatting.GREEN), true);
            });
            return;
        }
        Hands.Session s = Hands.start(p, HandKind.ATM_IN, "", anchor, target);
        if (s == null) return;
        AT.put(p.getUuid(), pos.toImmutable());
        // экран банкомата: баланс и наличные в кармане — когда карта вставлена и PIN набран
        s.onDone(() -> {
            NbtCompound n = new NbtCompound();
            n.putInt("balance", Life.get(p).money);
            n.putInt("cash", cash(p));
            n.putBoolean("physical", true);
            LifeNet.send(p, "open_atm", n);
        });
        s.onCancel(() -> AT.remove(p.getUuid()));
    }

    /** Стоит у «своего» банкомата (вставил карту) или в «Сити-Банке». */
    public static boolean atAtm(ServerPlayerEntity p) {
        var pos = AT.get(p.getUuid());
        if (pos != null && pos.toCenterPos().squaredDistanceTo(p.getPos()) < 3.2 * 3.2 && (p.getServerWorld().getBlockState(pos).isOf(ModContent.ATM) || p.getServerWorld().getBlockState(pos).isOf(ModContent.ATM_WALL))) return true;
        var bank = com.yompix.novograd.story.Story.place(p.getServerWorld(), "bank_tower");
        return bank != null && bank.squaredDistanceTo(p.getPos()) < 12 * 12;
    }

    private static Hands.Session atmHands(ServerPlayerEntity p, HandKind k) {
        var pos = AT.get(p.getUuid());
        if (pos == null) return null;
        var st = p.getServerWorld().getBlockState(pos);
        var face = st.contains(net.minecraft.state.property.Properties.HORIZONTAL_FACING) ? st.get(net.minecraft.state.property.Properties.HORIZONTAL_FACING)
                : net.minecraft.util.math.Direction.NORTH;
        return Hands.start(p, k, "", Hands.front(pos, face, 0.9), new net.minecraft.util.math.Vec3d(pos.getX() + 0.5, pos.getY() + 0.85, pos.getZ() + 0.5));
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
        if (action.equals("leave")) { // забрал карту, ничего не делая
            if (AT.containsKey(p.getUuid())) atmHands(p, HandKind.ATM_CARD);
            AT.remove(p.getUuid());
            return;
        }
        if (!atAtm(p)) { // 0.9.8: наличные — только у банкомата (телефон — переводы и кредит)
            reply(p, action, false, 0, "Наличные — у банкомата ($ на карте)");
            p.sendMessage(Text.literal("Наличные — у банкомата: значок $ на карте").formatted(Formatting.GRAY), true);
            return;
        }
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
            // 0.9.8: деньги — когда рука взяла купюры из лотка (тик 22); ушёл раньше — банкомат забрал их обратно
            Hands.Session s = AT.containsKey(p.getUuid()) ? atmHands(p, HandKind.ATM_CASH) : null;
            final int fFee = fee;
            Runnable give = () -> {
                if (d.money < sum + fFee) return;
                d.money -= sum + fFee;
                d.cash += sum;
                Life.sync(p);
            };
            if (s != null) {
                s.at(22, give);
                s.onDone(() -> AT.remove(p.getUuid()));
                s.onCancel(() -> AT.remove(p.getUuid()));
            } else give.run();
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
            if (total > 0 && AT.containsKey(p.getUuid())) { // 0.9.8: руками — купюры в приёмник
                atmHands(p, HandKind.ATM_DEPOSIT);
                AT.remove(p.getUuid());
            }
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
