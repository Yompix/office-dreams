package com.yompix.lifecraft.life;

import com.yompix.lifecraft.entity.CarEntity;
import com.yompix.lifecraft.world.TownState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Камеры ГИБДД в городе: больше 80 км/ч — сначала предупреждение, потом штраф. */
public final class Police {
    private Police() {}

    public static final int LIMIT_KMH = 80;
    private static final Map<UUID, Vec3d> LAST = new HashMap<>();
    private static final Map<UUID, Integer> WARNINGS = new HashMap<>();
    private static final Map<UUID, Long> LAST_FINE = new HashMap<>();

    public static void tick(MinecraftServer server) {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (!(p.getVehicle() instanceof CarEntity car) || car.getControllingPassenger() != p) {
                LAST.remove(p.getUuid());
                continue;
            }
            Vec3d now = car.getPos(), was = LAST.put(p.getUuid(), now);
            TownState t = TownState.get(p.getServerWorld());
            if (was == null || t.origin == null || Math.abs(now.x - t.origin.getX()) > 80 || Math.abs(now.z - t.origin.getZ()) > 80) continue;
            double kmh = Math.sqrt((now.x - was.x) * (now.x - was.x) + (now.z - was.z) * (now.z - was.z)) * 3.6;
            if (kmh <= LIMIT_KMH || kmh > 400) continue;
            long time = p.getServerWorld().getTime();
            if (time - LAST_FINE.getOrDefault(p.getUuid(), -10000L) < 400) continue;
            int warn = WARNINGS.merge(p.getUuid(), 1, Integer::sum);
            p.playSoundToPlayer(SoundEvents.BLOCK_NOTE_BLOCK_BIT.value(), SoundCategory.MASTER, 1f, 0.5f);
            if (warn < 3) {
                p.sendMessage(Text.literal("📷 Камера: " + (int) kmh + " км/ч при лимите " + LIMIT_KMH + "! Сбавь скорость").formatted(Formatting.RED), true);
                LAST_FINE.put(p.getUuid(), time - 300);
            } else {
                WARNINGS.put(p.getUuid(), 0);
                LAST_FINE.put(p.getUuid(), time);
                LifeData d = Life.get(p);
                int fine = 25 + d.fines * 10;
                d.money = Math.max(0, d.money - fine);
                d.fines++;
                Life.message(p, "ГИБДД", "Штраф $" + fine + " за превышение скорости (" + (int) kmh + " км/ч). Ограничение в городе — " + LIMIT_KMH + " км/ч.");
                Achievements.give(p, "speeding");
                com.yompix.lifecraft.life.LifeNet.send(p, "siren", new net.minecraft.nbt.NbtCompound());
            }
        }
    }
}
