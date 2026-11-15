package com.yompix.glukhoman.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Интерактивные моменты в игре (0.5.1): короткий рывок камеры (схватили за плечо), резкий разворот к событию,
 * медленный подъём камеры к глазу. Приходят с сервера (S2C «tug», «turn») — у всех, кого касается, одинаково.
 * «Без тряски» гасит рывок; «Направленный взгляд» выключает разворот.
 */
public final class Moments {
    private Moments() {}

    public static float dYaw, dPitch;
    static double ty, tp, tyv, tpv, last = -1;
    static float turnFrom, turnTo, turnPFrom, turnPTo;
    static double turnT0 = -1;
    static final double TURN = 0.38;

    static double now() {
        return System.nanoTime() / 1e9;
    }

    /** Рывок камеры от точки (кто схватил): вбок к нему и чуть вниз, пружина с перелётом, ~0.6 с. */
    public static void tug(Vec3d from) {
        var p = MinecraftClient.getInstance().player;
        if (p == null || GConfig.get().noShake) return;
        Vec3d d = from.subtract(p.getEyePos());
        double rel = MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(-d.x, d.z)) - p.getYaw());
        tyv += Math.signum(rel) * 70;
        tpv += 40;
    }

    /** Резко обернуться к точке (звук за спиной, звонок) за ~0.4 с — управление остаётся у игрока. */
    public static void turn(Vec3d at) {
        var p = MinecraftClient.getInstance().player;
        if (p == null || !GConfig.get().camGlance) return;
        Vec3d d = at.subtract(p.getEyePos());
        turnFrom = p.getYaw();
        turnTo = turnFrom + MathHelper.wrapDegrees((float) Math.toDegrees(Math.atan2(-d.x, d.z)) - turnFrom);
        turnPFrom = p.getPitch();
        turnPTo = (float) MathHelper.clamp(-Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z))), -40, 40);
        turnT0 = now();
    }

    /** Каждый кадр (CameraMixin), вне кат-сцен. */
    public static void frame() {
        double t = now();
        double dt = last < 0 ? 0 : Math.min(0.05, t - last);
        last = t;
        for (int i = 0; i < 4; i++) {
            double h = dt / 4, w = 11;
            tyv += (-w * w * ty - 2 * 0.45 * w * tyv) * h;
            ty += tyv * h;
            tpv += (-w * w * tp - 2 * 0.45 * w * tpv) * h;
            tp += tpv * h;
        }
        dYaw = (float) ty;
        dPitch = (float) tp;
        var p = MinecraftClient.getInstance().player;
        if (turnT0 > 0 && p != null) {
            double u = MathHelper.clamp((t - turnT0) / TURN, 0, 1);
            double e = 1 - Math.pow(1 - u, 3);   // резко начинается, мягко доходит
            p.setYaw((float) MathHelper.lerp(e, turnFrom, turnTo));
            p.setPitch((float) MathHelper.lerp(e, turnPFrom, turnPTo));
            if (u >= 1) turnT0 = -1;
        }
    }
}
