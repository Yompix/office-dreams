package com.yompix.novograd.client.anim;

import com.yompix.novograd.entity.CitizenEntity;
import net.minecraft.util.math.MathHelper;
import software.bernie.geckolib.cache.object.GeoBone;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * 0.9.8: «живой» слой поверх клипов жителей (GeckoLib) — дёшево, на клиенте, каждый кадр:
 * <ul>
 *   <li>характер по зерну (бодрый, уверенный, усталый, пожилой, обычный): осанка, размах рук, наклон головы —
 *       толпа не двигается одинаково;</li>
 *   <li>шаг: наклон в поворот (по скорости поворота и шага), наклон при разгоне/торможении, голова держит горизонт;</li>
 *   <li>ступенька: нога выше на подъёме (колено), без «скольжения» через угол;</li>
 *   <li>стоит: дыхание (грудь и плечи), перенос веса с ноги на ногу, мелкие движения головы; повернул голову далеко —
 *       корпус доворачивается (глаза → голова → корпус).</li>
 * </ul>
 * Стоимость — {@link #nanos} (средняя на житель за вызов, для автотеста crowd).
 */
public final class CitizenLife {
    private CitizenLife() {}

    private static final class S {
        float lastYaw = Float.NaN, lastSpeed, turn, accel;
        double lastY = Double.NaN;
        float stepUntil = -1, lastT = -1;
    }

    public static long nanos, calls;

    /** Характер: осанка (рад, + вперёд), размах рук, наклон головы (рад), темп дыхания. */
    private static final float[][] KIND = {
            {-0.02f, 1.2f, -0.03f, 1.15f},  // бодрый
            {-0.07f, 1.05f, -0.06f, 1.0f},  // уверенный — грудь вперёд, голова выше
            {0.09f, 0.72f, 0.12f, 0.9f},    // усталый
            {0.15f, 0.6f, 0.05f, 0.85f},    // пожилой
            {0f, 1f, 0f, 1f}};              // обычный

    public static void apply(CitizenEntity e, Function<String, GeoBone> bone, float pt) {
        if (CitizenEntity.OLD_GAIT || e.knockPhase() != CitizenEntity.KO_NONE || e.hasVehicle() || e.asleep()) return;
        long t0 = System.nanoTime();
        S st = e.animState instanceof S s0 ? s0 : (S) (e.animState = new S());
        float t = e.age + pt;
        float dt = st.lastT < 0 ? 0 : MathHelper.clamp(t - st.lastT, 0, 3);
        st.lastT = t;
        int seed = e.getUuid().hashCode();
        float[] kind = KIND[Math.floorMod(seed >>> 7, KIND.length)];
        float ph = (seed & 1023) / 1023f * MathHelper.TAU;
        double dx = e.getX() - e.prevX, dz = e.getZ() - e.prevZ;
        float speed = (float) Math.sqrt(dx * dx + dz * dz);
        boolean moving = speed > 0.016f;
        CitizenEntity.Activity act = e.getActivity();
        boolean plain = act == CitizenEntity.Activity.IDLE || act.walking() && !act.pose();
        GeoBone waist = bone.apply("waist"), chest = bone.apply("chest"), neck = bone.apply("neck");
        GeoBone ra = bone.apply("right_arm"), la = bone.apply("left_arm");
        GeoBone rl = bone.apply("right_leg"), ll = bone.apply("left_leg"), rs = bone.apply("right_shin"), ls = bone.apply("left_shin");
        if (waist == null || chest == null) return;
        // ---------------- поворот и разгон (сглаженные)
        float yaw = MathHelper.lerpAngleDegrees(pt, e.prevBodyYaw, e.bodyYaw);
        if (dt > 0) {
            float rate = Float.isNaN(st.lastYaw) ? 0 : MathHelper.wrapDegrees(yaw - st.lastYaw) / dt;
            float turnWant = MathHelper.clamp(rate * speed * 0.9f, -0.14f, 0.14f);
            st.turn += (turnWant - st.turn) * Math.min(1, dt * 0.25f);
            float acc = (speed - st.lastSpeed) / dt;
            st.accel += (MathHelper.clamp(acc * 2.2f, -0.1f, 0.1f) - st.accel) * Math.min(1, dt * 0.2f);
            st.lastSpeed = speed;
            st.lastYaw = yaw;
            // ступенька: поднялся на шаге — колено выше на 6 тиков
            if (!Double.isNaN(st.lastY) && e.isOnGround() && e.getY() - st.lastY > 0.3 && moving) st.stepUntil = t + 6;
            st.lastY = e.getY();
        }
        float walkW = MathHelper.clamp((speed - 0.01f) / 0.04f, 0, 1);
        // ---------------- осанка (на ходу сильнее)
        float post = kind[0] * (0.6f + 0.4f * walkW);
        waist.setRotX(waist.getRotX() + post * 0.5f + st.accel);
        chest.setRotX(chest.getRotX() + post * 0.5f);
        if (neck != null) neck.setRotX(neck.getRotX() + kind[2] - post * 0.6f);
        // ---------------- на ходу: руки по характеру, наклон в поворот, голова держит горизонт
        if (moving && plain) {
            if (ra != null) ra.setRotX(ra.getRotX() * kind[1]);
            if (la != null) la.setRotX(la.getRotX() * kind[1]);
            waist.setRotZ(waist.getRotZ() + st.turn);
            if (neck != null) {
                neck.setRotZ(neck.getRotZ() - st.turn * 0.6f - chest.getRotZ() * 0.5f);
                neck.setRotY(neck.getRotY() - chest.getRotY() * 0.4f);
            }
        }
        if (t < st.stepUntil && rl != null && ll != null) {
            float k = MathHelper.sin((1 - (st.stepUntil - t) / 6f) * MathHelper.PI);
            boolean right = rl.getRotX() < ll.getRotX();
            GeoBone leg = right ? rl : ll, shin = right ? rs : ls;
            leg.setRotX(leg.getRotX() - 0.45f * k);
            if (shin != null) shin.setRotX(shin.getRotX() + 0.7f * k);
        }
        // ---------------- стоит: дыхание, вес с ноги на ногу, мелкие движения головы, корпус за взглядом
        if (!moving) {
            float br = MathHelper.sin(t * MathHelper.TAU / (70f / kind[3]) + ph);
            chest.setRotX(chest.getRotX() - 0.022f * br);
            if (ra != null) ra.setRotZ(ra.getRotZ() + 0.015f * br);
            if (la != null) la.setRotZ(la.getRotZ() - 0.015f * br);
            if (act == CitizenEntity.Activity.IDLE) {
                float sh = MathHelper.sin(t * MathHelper.TAU / (230f + (seed & 63)) + ph);
                waist.setRotZ(waist.getRotZ() + 0.03f * sh);
                if (rl != null) rl.setRotZ(rl.getRotZ() - 0.03f * sh);
                if (ll != null) ll.setRotZ(ll.getRotZ() - 0.03f * sh);
                if (neck != null) {
                    neck.setRotY(neck.getRotY() + 0.035f * MathHelper.sin(t * 0.031f + ph) + 0.02f * MathHelper.sin(t * 0.077f + ph * 2));
                    neck.setRotX(neck.getRotX() + 0.02f * MathHelper.sin(t * 0.043f + ph));
                }
            }
            if (neck != null) {
                float hy = neck.getRotY();
                float over = Math.abs(hy) - 0.6f;
                if (over > 0) {
                    float turn = Math.signum(hy) * over * 0.45f;
                    chest.setRotY(chest.getRotY() + turn);
                    neck.setRotY(hy - turn);
                }
            }
        }
        nanos += System.nanoTime() - t0;
        calls++;
    }
}
