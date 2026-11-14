package com.yompix.glukhoman.entity;

import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

/** Кто смотрит на существо: угол обзора + прямая видимость. */
public final class Watch {
    private Watch() {}

    public static ServerPlayerEntity observer(ServerWorld w, Entity e, double range, double cos) {
        Vec3d c = e.getPos().add(0, e.getHeight() * 0.6, 0);
        for (ServerPlayerEntity p : w.getPlayers()) {
            if (p.isSpectator() || !p.isAlive()) continue;
            Vec3d to = c.subtract(p.getEyePos());
            double d = to.length();
            if (d > range) continue;
            if (d > 1.5 && to.normalize().dotProduct(p.getRotationVec(1f)) < cos) continue;
            if (!p.canSee(e)) continue;
            return p;
        }
        return null;
    }

    public static boolean playable(ServerPlayerEntity p) {
        return p.isAlive() && !p.isSpectator() && !p.isCreative();
    }
}
