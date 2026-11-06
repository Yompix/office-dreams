package com.yompix.novograd.gta;

import com.yompix.novograd.life.Life;
import com.yompix.novograd.life.LifeNet;
import com.yompix.novograd.story.Story;
import com.yompix.novograd.vehicle.Vehicles;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Авиарейсы как в San Andreas: на аэродроме Новограда, гидроаэродроме Ривьеры и полосе в Пустоши стоят авиакассы.
 * Подошёл к кассе — выбрал рейс и заплатил — короткий перелёт (затемнение, самолёт в небе, гул моторов) — ты на месте.
 * С розыском в самолёт не пускают; во время миссии касса закрыта.
 */
public final class Airport {
    private Airport() {}

    /** id, где касса, название, цена из любой кассы сюда. */
    static final String[][] PORTS = {
            {"nov", "airfield_hangar+14,18", "Аэропорт Новограда", "150"},
            {"riv", "riv_beach+40,0", "Гидроаэродром Ривьеры", "150"},
            {"des", "desert_road+18,6", "Полоса «Пустошь»", "220"},
    };

    private record Flight(String to, long landAt) {}

    private static final Map<UUID, Flight> FLYING = new HashMap<>();

    static int port(String id) {
        for (int i = 0; i < PORTS.length; i++) if (PORTS[i][0].equals(id)) return i;
        return -1;
    }

    static void tickPlayer(ServerPlayerEntity p) {
        ServerWorld w = p.getServerWorld();
        for (int i = 0; i < PORTS.length; i++) {
            Vec3d at = Story.place(w, PORTS[i][1]);
            if (at == null || p.squaredDistanceTo(at) > 48 * 48) continue;
            Vec3d m = at;
            if (p.age % 40 < 10)
                FunUtil.marker(w, m, "nv_air_" + PORTS[i][0], new ItemStack(Items.ELYTRA), Text.literal("✈ Авиакасса · " + PORTS[i][2]).formatted(Formatting.AQUA), true);
            final int here = i;
            DriveHooks.walkIn(p, "air" + i, m, () -> {
                NbtCompound n = DriveHooks.state(p);
                n.putString("open", "air");
                n.putString("here", PORTS[here][0]);
                LifeNet.send(p, "drv_open", n);
            });
        }
    }

    /** Купить билет из ближайшей кассы в to. */
    public static void fly(ServerPlayerEntity p, String to) {
        ServerWorld w = p.getServerWorld();
        int dst = port(to);
        if (dst < 0 || FLYING.containsKey(p.getUuid())) return;
        int from = -1;
        for (int i = 0; i < PORTS.length; i++) {
            Vec3d at = Story.place(w, PORTS[i][1]);
            if (at != null && p.squaredDistanceTo(at) < 10 * 10) from = i;
        }
        if (from < 0) {
            p.sendMessage(Text.literal("Билеты продают только в авиакассе").formatted(Formatting.RED), true);
            return;
        }
        if (from == dst) return;
        if (Wanted.stars(p) > 0) {
            p.sendMessage(Text.literal("Кассир: «С розыском на борт нельзя. Сначала разберитесь с полицией.»").formatted(Formatting.RED), false);
            return;
        }
        if (!FunUtil.freeRoam(p) || FunUtil.busy(p)) {
            p.sendMessage(Text.literal("Сейчас не до полётов — закончи начатое").formatted(Formatting.RED), true);
            return;
        }
        if (p.hasVehicle()) p.stopRiding();
        int price = Integer.parseInt(PORTS[dst][3]);
        if (!Life.pay(p, price, "билет: " + PORTS[from][2] + " → " + PORTS[dst][2])) return;
        NbtCompound n = new NbtCompound();
        n.putString("from", PORTS[from][2]);
        n.putString("to", PORTS[dst][2]);
        n.putString("code", "NV-" + (100 + from * 10 + dst + w.getRandom().nextInt(9) * 100));
        LifeNet.send(p, "drv_flight", n);
        p.playSoundToPlayer(Vehicles.PLANE_ENGINE, SoundCategory.MASTER, 1f, 0.9f);
        FLYING.put(p.getUuid(), new Flight(PORTS[dst][0], w.getTime() + 60));
        p.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(net.minecraft.entity.effect.StatusEffects.RESISTANCE, 200, 4, true, false));
        if (w.getRandom().nextInt(3) == 0) Economy.shock(p.getServer(), dst == 1 ? "RIVH" : "PORT", 0.004, "");
    }

    static void tick(MinecraftServer s) {
        if (FLYING.isEmpty()) return;
        long now = s.getOverworld().getTime();
        for (var it = FLYING.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            ServerPlayerEntity p = s.getPlayerManager().getPlayer(e.getKey());
            if (p == null) {
                it.remove();
                continue;
            }
            if (now < e.getValue().landAt()) {
                if ((e.getValue().landAt() - now) % 20 == 0) p.playSoundToPlayer(Vehicles.PLANE_ENGINE, SoundCategory.MASTER, 0.9f, 1f);
                continue;
            }
            it.remove();
            int dst = port(e.getValue().to());
            ServerWorld w = p.getServerWorld();
            Vec3d at = Story.place(w, PORTS[dst][1]);
            if (at == null) continue;
            Vec3d out = at.add(2.5, 0, 2.5);
            w.getChunk((int) Math.floor(out.x) >> 4, (int) Math.floor(out.z) >> 4);
            int y = Story.topY(w, (int) Math.floor(out.x), (int) Math.floor(out.z));
            p.teleport(w, out.x, y + 0.1, out.z, p.getYaw(), 0);
            DriveHooks.arm(p, "air" + dst); // касса не открывается сразу после посадки
            Activities.title(p, PORTS[dst][2].toUpperCase(), "Добро пожаловать! Спасибо, что летаете «Новоград-Авиа»");
        }
    }

    public static boolean flying(ServerPlayerEntity p) {
        return FLYING.containsKey(p.getUuid());
    }
}
