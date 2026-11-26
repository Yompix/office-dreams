package com.yompix.novograd.world;

import com.yompix.novograd.entity.CarEntity;
import com.yompix.novograd.entity.CitizenEntity;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

/**
 * 0.9.7: прохожие замечают, что вокруг. Раз в 5 тиков у каждого игрока:
 * <ul>
 *   <li>быстрая машина (своя или чужая) рядом — головы поворачиваются за ней (на 2 с), спорткар — дольше;</li>
 *   <li>машина игрока летит прямо на человека — тот отскакивает в сторону (с полосы движения на шаг-два вбок);</li>
 *   <li>игрок бежит мимо на спринте — кто рядом, провожает взглядом.</li>
 * </ul>
 * Дёшево: только люди в 16 блоках от игрока, никаких новых сущностей.
 */
public final class Attention {
    private Attention() {}

    /** Для автотеста: сколько раз отскочили и засмотрелись. */
    public static int dodges, gazes;

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(Attention::tick);
    }

    private static boolean free(CitizenEntity c) {
        return c.isAlive() && !c.hasVehicle() && c.getStoryId().isEmpty() && c.knockPhase() == CitizenEntity.KO_NONE && !c.asleep();
    }

    private static void tick(MinecraftServer s) {
        if (s.getTicks() % 5 != 2) return;
        ServerWorld w = s.getOverworld();
        for (ServerPlayerEntity p : w.getPlayers()) {
            if (p.isSpectator()) continue;
            // машины рядом с игроком (и его собственная)
            for (CarEntity car : w.getEntitiesByClass(CarEntity.class, p.getBoundingBox().expand(24, 8, 24), x -> Math.abs(x.speed) > 0.35f)) {
                Vec3d v = car.getVelocity().lengthSquared() > 1e-4 ? car.getVelocity() : Vec3d.fromPolar(0, car.getYaw()).multiply(car.speed);
                Vec3d dir = new Vec3d(v.x, 0, v.z);
                if (dir.lengthSquared() < 1e-4) continue;
                dir = dir.normalize();
                Vec3d perp = new Vec3d(-dir.z, 0, dir.x);
                boolean player = car.getFirstPassenger() instanceof ServerPlayerEntity;
                for (CitizenEntity c : w.getEntitiesByClass(CitizenEntity.class, car.getBoundingBox().expand(16, 4, 16), Attention::free)) {
                    Vec3d rel = c.getPos().subtract(car.getPos());
                    double along = rel.dotProduct(dir), lat = rel.dotProduct(perp);
                    if (player && along > 1 && along < 14 && Math.abs(lat) < 2.3 && !c.isStationary()) {
                        double side = lat >= 0 ? 1 : -1;
                        Vec3d to = c.getPos().add(perp.multiply(side * (3.4 - Math.abs(lat))));
                        c.getNavigation().startMovingTo(to.x, c.getY(), to.z, 1.45);
                        c.gaze(car, 30);
                        dodges++;
                    } else if (!c.gazing() && rel.lengthSquared() < 12 * 12 && (along > -6 || Math.abs(car.speed) > 0.6f)) {
                        c.gaze(car, Math.abs(car.speed) > 0.6f ? 60 : 40);
                        gazes++;
                    }
                }
            }
            // спринт мимо прохожих
            if (p.isSprinting() && !p.hasVehicle())
                for (CitizenEntity c : w.getEntitiesByClass(CitizenEntity.class, p.getBoundingBox().expand(6, 3, 6), Attention::free))
                    if (!c.gazing() && w.random.nextInt(3) == 0) {
                        c.gaze(p, 30);
                        gazes++;
                    }
        }
    }
}
