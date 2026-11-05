package com.yompix.novograd.gta;

import com.yompix.novograd.entity.CarEntity;
import com.yompix.novograd.life.Life;
import com.yompix.novograd.life.LifeNet;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** «Нитро-Гараж»: покраска, нитро, двигатель, броня, ремонт и «перекраска от полиции» (сброс розыска). */
public final class Garage {
    private Garage() {}

    public static final int[] PAINTS = {0xD7262C, 0x2C6ECB, 0xF2C032, 0x2A2C31, 0xE8E8EA, 0x2EAD4B, 0x8E44AD, 0xFF7A1A};
    public static final int PAINT = 60, NITRO = 350, ENGINE1 = 300, ENGINE2 = 600, ARMOR1 = 250, ARMOR2 = 500, REPAIR = 50, HIDE = 120;

    /** Машина, которую тюнингуем: та, в которой сидишь, или ближайшая у гаража. */
    private static CarEntity car(ServerPlayerEntity p) {
        if (p.getVehicle() instanceof CarEntity c && !c.isScooter()) return c;
        return p.getServerWorld().getEntitiesByClass(CarEntity.class, p.getBoundingBox().expand(10), c -> !c.isScooter() && !c.isPolice()).stream()
                .min(java.util.Comparator.comparingDouble(c -> c.squaredDistanceTo(p))).orElse(null);
    }

    public static void open(ServerPlayerEntity p) {
        CarEntity c = car(p);
        if (c == null) {
            p.sendMessage(Text.literal("Механик Стас: «Загоняй машину в гараж — тогда поговорим»").formatted(Formatting.YELLOW), false);
            return;
        }
        LifeNet.send(p, "open_garage", info(p, c));
    }

    private static NbtCompound info(ServerPlayerEntity p, CarEntity c) {
        NbtCompound n = new NbtCompound();
        n.putInt("car", c.getId());
        n.putInt("color", c.color());
        n.putBoolean("nitro", c.hasNitro());
        n.putInt("engine", c.engine());
        n.putInt("armor", c.armor());
        n.putInt("health", c.health());
        n.putInt("stars", Wanted.stars(p));
        n.putInt("money", Life.get(p).money);
        return n;
    }

    public static void action(ServerPlayerEntity p, String act, int value) {
        CarEntity c = car(p);
        if (c == null) return;
        boolean ok = switch (act) {
            case "paint" -> value >= 0 && value < PAINTS.length && Life.pay(p, PAINT, "покраска") && run(() -> c.paint(PAINTS[value]));
            case "nitro" -> !c.hasNitro() && Life.pay(p, NITRO, "нитро") && run(() -> c.setTune(true, c.engine(), c.armor()));
            case "engine" -> c.engine() < 2 && Life.pay(p, c.engine() == 0 ? ENGINE1 : ENGINE2, "двигатель") && run(() -> c.setTune(c.hasNitro(), c.engine() + 1, c.armor()));
            case "armor" -> c.armor() < 2 && Life.pay(p, c.armor() == 0 ? ARMOR1 : ARMOR2, "броня") && run(() -> c.setTune(c.hasNitro(), c.engine(), c.armor() + 1));
            case "repair" -> c.health() < 100 && Life.pay(p, REPAIR, "ремонт") && run(c::repair);
            case "hide" -> Wanted.stars(p) > 0 && Life.pay(p, HIDE, "перекраска от полиции") && run(() -> {
                c.paint(PAINTS[p.getRandom().nextInt(PAINTS.length)]);
                c.repair();
                Wanted.clear(p);
                p.sendMessage(Text.literal("🎨 Новый цвет — полиция ищет другую машину. Розыск снят!").formatted(Formatting.GREEN), false);
            });
            default -> false;
        };
        if (ok) p.getServerWorld().playSound(null, c.getBlockPos(), SoundEvents.BLOCK_ANVIL_USE, SoundCategory.BLOCKS, 0.6f, 1.3f);
        LifeNet.send(p, "garage_info", info(p, c));
    }

    private static boolean run(Runnable r) {
        r.run();
        return true;
    }
}
