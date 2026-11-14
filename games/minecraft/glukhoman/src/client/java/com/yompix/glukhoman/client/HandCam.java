package com.yompix.glukhoman.client;

import com.yompix.glukhoman.GContent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * «Живая» камера в руках, от первого лица: шаг в шаг с ногами (провал на ударе пятки — там же звук шага), боковое качание
 * и лёгкий крен на каждом шаге, инерция поворота (камера отстаёт от мыши, перелетает и успокаивается — пружина),
 * дыхание на месте (сильнее после бега), толчок при приземлении, мягче вприсядку, тряско и прыгуче на бегу.
 * Время — игровое (тики + доля кадра), а не настенные часы: так одинаково и в игре, и при покадровой записи.
 * Сила — настройка «Покачивание камеры» (0–100 %).
 */
public final class HandCam {
    private HandCam() {}

    public static boolean active;
    /** Смещение камеры (блоки, в системе взгляда) и поправки углов (градусы). */
    public static double ox, oy;
    public static float dYaw, dPitch, roll;
    /** Сдвиг рамки видоискателя и REC (пиксели GUI). */
    public static float hudX, hudY;

    static double lastT = -1;
    static float yawS, pitchS, yawV, pitchV;     // пружина поворота
    static double joltY, joltV;                  // пружина толчка
    static float breath;                         // одышка 0..1
    static boolean wasGround = true;
    static double lastVy;

    static boolean holding(ClientPlayerEntity p) {
        return p.getMainHandStack().isOf(GContent.CAMCORDER) || p.getOffHandStack().isOf(GContent.CAMCORDER);
    }

    /** Каждый кадр, из CameraMixin (вне кат-сцен). */
    public static void frame(float tickDelta) {
        MinecraftClient c = MinecraftClient.getInstance();
        ClientPlayerEntity p = c.player;
        float k = GConfig.get().handSway / 100f;
        active = p != null && c.world != null && !Cine.active && GlukhomanClient.inWorld && holding(p) && k > 0.001f && c.options.getPerspective().isFirstPerson();
        if (!active) {
            ox = oy = 0;
            dYaw = dPitch = roll = hudX = hudY = 0;
            lastT = -1;
            if (p != null) {
                yawS = p.getYaw(tickDelta);
                pitchS = p.getPitch(tickDelta);
            }
            return;
        }
        double t = (c.world.getTime() + tickDelta) / 20.0;
        double dt = lastT < 0 ? 0 : MathHelper.clamp(t - lastT, 0, 0.1);
        lastT = t;
        float yaw = p.getYaw(tickDelta), pitch = p.getPitch(tickDelta);
        if (dt == 0) {
            yawS = yaw;
            pitchS = pitch;
        }
        // --- инерция поворота: недодемпфированная пружина (перелёт и успокоение), отставание не больше 7°
        double w = 26, z = 0.55;
        float ey = MathHelper.wrapDegrees(yaw - yawS), ep = pitch - pitchS;
        yawV += (float) ((w * w * ey - 2 * z * w * yawV) * dt);
        pitchV += (float) ((w * w * ep - 2 * z * w * pitchV) * dt);
        yawS += yawV * dt;
        pitchS += pitchV * dt;
        float lagY = MathHelper.clamp(MathHelper.wrapDegrees(yawS - yaw), -7, 7), lagP = MathHelper.clamp(pitchS - pitch, -5, 5);
        yawS = yaw + lagY;
        pitchS = pitch + lagP;

        // --- шаг: фаза — пройденный путь (как у звуков шагов), сила — скорость
        double phase = MathHelper.lerp(tickDelta, p.prevHorizontalSpeed, p.horizontalSpeed);
        double speed = Math.min(1.6, p.getVelocity().horizontalLength() / 0.13);
        boolean run = p.isSprinting(), crouch = p.isInSneakingPose();
        if (!p.isOnGround()) speed *= 0.3;
        double amp = speed * (run ? 1.6 : crouch ? 0.45 : 1.0);
        double s = Math.sin(Math.PI * phase);
        double strike = Math.pow(1 - Math.abs(s), crouch ? 1.4 : 3.0);       // резкий провал на ударе пятки (вприсядку — мягко)
        double vert = -0.045 * amp * strike + 0.012 * amp * Math.abs(s);
        double side = 0.022 * amp * s;
        float stepRoll = (float) (0.75 * amp * s);
        float stepPitch = (float) (0.45 * amp * strike);
        // --- бег: прыгучесть и мелкая дрожь; одышка копится на бегу и спадает
        breath = (float) MathHelper.clamp(breath + (run ? 0.18 : -0.06) * dt, 0, 1);
        double shake = run ? 0.25 : 0;
        float jitterY = (float) ((Math.sin(t * 23.1) + Math.sin(t * 31.7)) * 0.12 * shake);
        float jitterP = (float) ((Math.sin(t * 19.3) + Math.sin(t * 27.9)) * 0.1 * shake);
        // --- дыхание на месте (и сверху на ходу), сильнее после бега
        double still = 1 - Math.min(1, speed);
        float br = 0.35f + 1.1f * breath;
        double brRate = 0.9 + 1.4 * breath;
        float breathP = (float) (Math.sin(t * brRate * 2 * Math.PI / 3.2) * 0.35 * br * (0.4 + 0.6 * still));
        double breathY = Math.sin(t * brRate * 2 * Math.PI / 3.2) * 0.006 * br;
        float drift = (float) (Math.sin(t * 0.37) * 0.25 + Math.sin(t * 0.61 + 1) * 0.18);   // рука не держит идеально
        // --- приземление и шаг вниз: толчок по пружине
        boolean ground = p.isOnGround();
        if (ground && !wasGround && lastVy < -0.18) joltV -= Math.min(0.9, -lastVy * 1.6);
        wasGround = ground;
        lastVy = p.getVelocity().y;
        double jw = 18, jz = 0.45;
        joltV += (-jw * jw * joltY - 2 * jz * jw * joltV) * dt;
        joltY += joltV * dt;

        ox = side * k;
        oy = (vert + breathY + joltY * 0.12) * k;
        dYaw = (lagY + drift * 0.6f + jitterY) * k;
        dPitch = (lagP + stepPitch + breathP + drift * 0.4f + jitterP + (float) (joltY * 6)) * k;
        roll = (stepRoll + lagY * -0.25f + (float) Math.sin(t * 0.5) * 0.3f) * k;
        hudX = (float) (side * 40 + lagY * 0.6) * k;
        hudY = (float) (-vert * 40 + lagP * 0.5) * k;
    }

    /** Смещение позиции камеры в мировых координатах (вбок — поперёк взгляда). */
    public static Vec3d offset(float yaw) {
        double r = Math.toRadians(yaw);
        return new Vec3d(-Math.cos(r) * ox, oy, -Math.sin(r) * ox);
    }
}
