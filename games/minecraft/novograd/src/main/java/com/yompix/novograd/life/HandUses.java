package com.yompix.novograd.life;

import com.yompix.novograd.ModContent;
import com.yompix.novograd.entity.CarEntity;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * 0.9.8 «Руки»: что дают действия — автомат, ларёк, касса, колонка (бензин и ремонт), тайник, машина. Анимацию рисует
 * клиент по {@link HandKind}; здесь — места, деньги и результат (в нужный тик, на сервере).
 */
public final class HandUses {
    private HandUses() {}

    static Direction facing(BlockState st, ServerPlayerEntity p) {
        return st.contains(Properties.HORIZONTAL_FACING) ? st.get(Properties.HORIZONTAL_FACING) : Direction.fromRotation(p.getYaw()).getOpposite();
    }

    // ------------------------------------------------------------------ автомат с напитками/чипсами
    private static final String[] JAM = {"Автомат зажевал деньги. Пни его!", "Банка застряла… Пни автомат!"};

    /** Монетка (платёж), кнопка, банка падает (иногда застревает), нагнуться, достать, открыть, выпить. Shift — чипсы. */
    public static void vending(ServerWorld w, BlockPos pos, ServerPlayerEntity p, java.util.Map<Long, Long> jammed) {
        boolean snack = p.isSneaking();
        Direction f = facing(w.getBlockState(pos), p);
        Hands.Session s = Hands.start(p, HandKind.VEND, snack ? "snack" : "drink", Hands.front(pos, f, 0.95), Vec3d.ofCenter(pos).add(0, 0.1, 0));
        if (s == null) return;
        boolean[] paid = {false}, jam = {false};
        s.at(14, () -> {
            paid[0] = Life.pay(p, snack ? 4 : 3, snack ? "чипсы «Хруст»" : "газировка «Новоград-Кола»");
            if (!paid[0]) Hands.cancel(p);
        });
        s.at(36, () -> {
            if (w.random.nextInt(14) == 0) {
                jam[0] = true;
                jammed.put(pos.asLong(), w.getTime());
                p.sendMessage(Text.literal(JAM[w.random.nextInt(JAM.length)]).formatted(Formatting.YELLOW), true);
                Hands.cancel(p);
            }
        });
        s.onDone(() -> {
            if (snack) {
                p.getHungerManager().add(5, 0.5f);
                p.heal(1);
                p.sendMessage(Text.literal("Чипсы «Хруст»: +сытость").formatted(Formatting.GREEN), true);
            } else {
                p.getHungerManager().add(2, 0.3f);
                p.heal(4);
                refresh(p);
                p.sendMessage(Text.literal("«Новоград-Кола»: +здоровье · Shift — чипсы").formatted(Formatting.GREEN), true);
            }
            Life.get(p).mood = Math.min(100, Life.get(p).mood + 1);
        });
        s.onCancel(() -> {
            // взял банку, но ушёл — банка с собой
            if (paid[0] && !jam[0] && s.stoppedAt >= 56 && !snack) give(p, new ItemStack(ModContent.COLA));
            else if (paid[0] && !jam[0] && s.stoppedAt >= 56) p.getHungerManager().add(2, 0.2f);
            else if (paid[0] && !jam[0] && s.stoppedAt >= 36) com.yompix.novograd.world.Destruction.dropCan(w, Vec3d.ofCenter(pos).add(Vec3d.of(f.getVector()).multiply(0.7)));
        });
    }

    /** Напиток — коротко бодрит (жажды в игре нет: глоток даёт немного скорости, как «освежился»). */
    static void refresh(ServerPlayerEntity p) {
        p.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(net.minecraft.entity.effect.StatusEffects.SPEED, 20 * 20, 0, false, false, true));
    }

    static void give(ServerPlayerEntity p, ItemStack it) {
        if (!p.getInventory().insertStack(it)) p.dropItem(it, false);
    }

    // ------------------------------------------------------------------ ларёк (хот-доги, мороженое, кофе)
    /** Заказать, заплатить (тик 26), получить, съесть/выпить; результат — в конце. Ушёл с едой — доест на ходу (половина). */
    public static void stall(ServerWorld w, BlockPos pos, ServerPlayerEntity p, String variant, int price, int hunger, float heal, String what, String line) {
        Direction f = facing(w.getBlockState(pos), p);
        Hands.Session s = Hands.start(p, HandKind.STALL, variant, Hands.front(pos, f, 1.1), Vec3d.ofCenter(pos).add(0, 0.2, 0));
        if (s == null) return;
        boolean[] paid = {false};
        s.at(26, () -> {
            paid[0] = Life.pay(p, price, what);
            if (!paid[0]) Hands.cancel(p);
            else p.sendMessage(Text.literal(what + " — продавец: ").formatted(Formatting.YELLOW).append(Text.literal(line).formatted(Formatting.WHITE)), false);
        });
        s.onDone(() -> {
            p.getHungerManager().add(hunger, 0.6f);
            p.heal(heal);
            if (variant.equals("drink") || variant.equals("coffee")) refresh(p);
            Life.get(p).mood = Math.min(100, Life.get(p).mood + 1);
        });
        s.onCancel(() -> {
            if (paid[0] && s.stoppedAt >= 52) p.getHungerManager().add(Math.max(1, hunger / 2), 0.4f);
        });
    }

    // ------------------------------------------------------------------ касса: магазин, кафе, супермаркет
    /** Покупка уже прошла — видно, как выложил, приложил карту (или отдал наличные) и взял пакет. */
    public static void checkout(ServerPlayerEntity p, boolean cash, @Nullable Vec3d counter) {
        Hands.start(p, HandKind.CHECKOUT, cash ? "cash" : "card", null, counter);
    }

    /** Вариант оплаты по кошельку: хватает наличных — наличными. */
    public static boolean paysCash(ServerPlayerEntity p, int price) {
        return Life.get(p).cash >= price;
    }

    // ------------------------------------------------------------------ тайник
    /** Телефон → «Тайник»: дома — присел к сейфу, набрал код, открыл; потом экран. */
    public static void stashOpen(ServerPlayerEntity p) {
        boolean home = com.yompix.novograd.world.Housing.atAnyHome(p);
        int tier = com.yompix.novograd.gta.Money.state(p.getServer()).stash(p.getUuid()).tier;
        if (home && tier > 0 && !Hands.busy(p)) {
            Hands.Session s = Hands.start(p, HandKind.SAFE, String.valueOf(tier), null, null);
            if (s != null) {
                s.onDone(() -> LifeNet.send(p, "stash_screen", new NbtCompound()));
                return;
            }
        }
        LifeNet.send(p, "stash_screen", new NbtCompound());
    }

    // ------------------------------------------------------------------ колонка: бензин пешком и ремонт
    /** Своя машина рядом с колонкой (8 блоков). */
    @Nullable
    static CarEntity myCar(ServerPlayerEntity p, double r) {
        CarEntity best = null;
        for (CarEntity c : p.getServerWorld().getEntitiesByClass(CarEntity.class, p.getBoundingBox().expand(r, 3, r),
                c -> c.isAlive() && !c.hasPassengers() && (p.getUuid().equals(c.getOwner()) || c.getOwner() == null && c.usesFuel())))
            if (best == null || c.squaredDistanceTo(p) < best.squaredDistanceTo(p)) best = c;
        return best;
    }

    /** Точка у бака (задняя четверть машины со стороны игрока) и куда стоять. */
    static Vec3d[] tank(CarEntity car, ServerPlayerEntity p) {
        double yaw = Math.toRadians(car.getYaw());
        Vec3d fwd = new Vec3d(-Math.sin(yaw), 0, Math.cos(yaw)), side = new Vec3d(Math.cos(yaw), 0, Math.sin(yaw));
        Vec3d c = car.getPos();
        double sg = Math.signum(p.getPos().subtract(c).dotProduct(side));
        if (sg == 0) sg = 1;
        Vec3d door = c.add(fwd.multiply(-1.3)).add(side.multiply(sg * 0.95)).add(0, 0.75, 0);
        Vec3d stand = c.add(fwd.multiply(-1.3)).add(side.multiply(sg * 1.75));
        return new Vec3d[]{stand, door};
    }

    /** ПКМ по колонке пешком: своя машина рядом — заправить (Shift — починить). true — заняли нажатие. */
    public static boolean pump(ServerPlayerEntity p, BlockPos clicked) {
        int i = com.yompix.novograd.gta.Fuel.nearest(p.getPos());
        Vec3d pumps = com.yompix.novograd.gta.Fuel.pumps(i);
        if (p.hasVehicle() || pumps.squaredDistanceTo(clicked.getX() + 0.5, pumps.y, clicked.getZ() + 0.5) > 9 * 9 || Math.abs(clicked.getY() - pumps.y) > 4) return false;
        CarEntity car = myCar(p, 9);
        if (car == null) {
            p.sendMessage(Text.literal("⛽ Подгони машину к колонке").formatted(Formatting.GRAY), true);
            return true;
        }
        Vec3d[] t = tank(car, p);
        if (p.isSneaking()) return repair(p, car, t);
        int max = CarEntity.FUEL_MAX, need = max - car.fuel();
        if (!car.usesFuel() || need < 10) {
            p.sendMessage(Text.literal("⛽ Бак полный").formatted(Formatting.GRAY), true);
            return true;
        }
        LifeData d = Life.get(p);
        if (d.cash + Math.max(0, d.money) < 3) {
            p.sendMessage(Text.literal("⛽ Нечем платить").formatted(Formatting.RED), true);
            return true;
        }
        Hands.Session s = Hands.start(p, HandKind.FUEL, "", t[0], t[1]);
        if (s == null) return true;
        int[] added = {0};
        final int from = 40, to = 182;
        for (int k = from; k <= to; k += 6) {
            final int tick = k;
            s.at(tick, () -> {
                int want = (int) Math.ceil(need * (double) (tick - from + 6) / (to - from + 6));
                int add = Math.min(max - car.fuel(), want - added[0]);
                if (add <= 0 || !car.isAlive()) return;
                car.setFuel(car.fuel() + add);
                added[0] += add;
                if (tick % 30 == 4) p.sendMessage(Text.literal("⛽ " + car.fuel() * 100 / max + "%").formatted(Formatting.AQUA), true);
            });
        }
        Runnable bill = () -> {
            if (added[0] <= 0) return;
            int units = added[0];
            int cost = Math.max(1, com.yompix.novograd.gta.Macro.price(com.yompix.novograd.gta.Money.price((units + com.yompix.novograd.gta.Fuel.UNITS_PER_DOLLAR - 1)
                    / com.yompix.novograd.gta.Fuel.UNITS_PER_DOLLAR), "бензин"));
            if (!Life.payRaw(p, cost, "бензин")) { // не хватило — часть списали в долг на карте
                d.money -= cost;
                Life.sync(p);
            }
            p.sendMessage(Text.literal("⛽ Заправлено " + car.fuel() * 100 / max + "% · $" + cost).formatted(Formatting.GREEN), true);
        };
        s.onDone(bill);
        s.onCancel(bill);
        return true;
    }

    private static boolean repair(ServerPlayerEntity p, CarEntity car, Vec3d[] t) {
        if (car.health() >= 100 || car.wrecked()) {
            p.sendMessage(Text.literal(car.wrecked() ? "🔧 Разбита — только в «Нитро-Гараже»" : "🔧 Машина целая").formatted(Formatting.GRAY), true);
            return true;
        }
        int price = 35 + (100 - car.health()) / 2;
        if (Life.get(p).cash + Math.max(0, Life.get(p).money) < price) {
            p.sendMessage(Text.literal("🔧 Ремонт — $" + price).formatted(Formatting.RED), true);
            return true;
        }
        double yaw = Math.toRadians(car.getYaw());
        Vec3d fwd = new Vec3d(-Math.sin(yaw), 0, Math.cos(yaw));
        Vec3d front = car.getPos().add(fwd.multiply(2.6));
        Hands.Session s = Hands.start(p, HandKind.REPAIR, "", front, car.getPos().add(fwd.multiply(1.6)).add(0, 0.5, 0));
        if (s == null) return true;
        s.onDone(() -> {
            if (!car.isAlive() || !Life.pay(p, price, "ремонт у колонки")) return;
            car.repair();
            p.sendMessage(Text.literal("🔧 Починил").formatted(Formatting.GREEN), true);
        });
        return true;
    }

    // ------------------------------------------------------------------ машина: сел, ремень, вышел
    public static void carIn(ServerPlayerEntity p) {
        Hands.gesture(p, HandKind.CAR_IN, "");
    }

    public static void carOut(ServerPlayerEntity p) {
        Hands.gesture(p, HandKind.CAR_OUT, "");
    }
}
