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
    static float yawS, pitchS, yawV, pitchV;     // пружина поворота (критически задемпфированная — без звона)
    static float lastYawIn, lastPitchIn;
    static double joltY, joltV;                  // толчок приземления: провал и мягкий возврат (~250 мс)
    public static double gait, runB, crouchB, airB;     // сглаженные множители: шаг, бег, присед, в воздухе
    static float breath;                         // одышка 0..1
    static boolean wasGround = true;
    static double lastVy;
    /** Для проверки плавности: значения последнего кадра (автотест пишет их в лог). */
    public static double lastPhase;

    static boolean holding(ClientPlayerEntity p) {
        return p.getMainHandStack().isOf(GContent.CAMCORDER) || p.getOffHandStack().isOf(GContent.CAMCORDER);
    }

    /** Экспоненциальное сглаживание к цели с постоянной времени tau (сек), по реальному шагу кадра. */
    static double ease(double cur, double target, double tau, double dt) {
        return cur + (target - cur) * (1 - Math.exp(-dt / tau));
    }

    /**
     * Каждый кадр, из CameraMixin (вне кат-сцен). Всё считается по времени кадра (тики + tickDelta), поза игрока —
     * интерполированная, все множители сглажены — поэтому нет «ступенек» по тикам. Амплитуды заданы для 100 %;
     * при 70 % по умолчанию: шаг ≤ 0.035 блока по вертикали, ≤ 0.02 вбок, крен ≤ 0.6°, кивок ≤ 0.4°; бег ×1.6;
     * дыхание ≤ 0.008 блока и ≤ 0.15°.
     */
    public static void frame(float tickDelta) {
        MinecraftClient c = MinecraftClient.getInstance();
        ClientPlayerEntity p = c.player;
        float k = GConfig.get().noShake ? 0f : GConfig.get().handSway / 100f;
        active = p != null && c.world != null && !Cine.active && GlukhomanClient.inWorld && holding(p) && k > 0.001f && c.options.getPerspective().isFirstPerson();
        if (!active) {
            ox = oy = 0;
            dYaw = dPitch = roll = hudX = hudY = 0;
            lastT = -1;
            if (p != null) {
                yawS = p.getYaw(tickDelta);
                pitchS = p.getPitch(tickDelta);
                yawV = pitchV = 0;
            }
            return;
        }
        double t = (c.world.getTime() + tickDelta) / 20.0;
        double dt = lastT < 0 ? 0 : MathHelper.clamp(t - lastT, 0, 0.1);
        lastT = t;
        float yaw = p.getYaw(tickDelta), pitch = p.getPitch(tickDelta);
        if (lastT == t && dt <= 0 && yawV == 0 && pitchV == 0) {
            yawS = yaw;
            pitchS = pitch;
            lastYawIn = yaw;
            lastPitchIn = pitch;
        }
        if (dt > 0) {
            // --- инерция поворота: критически задемпфированная пружина (ζ = 1) — отстаёт и догоняет, без перелёта и звона.
            // Шаг интегрирования дробим, чтобы при редких кадрах не было неустойчивости.
            double w = 30;
            int n = Math.max(1, (int) Math.ceil(dt / 0.004));
            double h = dt / n;
            for (int i = 0; i < n; i++) {
                // цель внутри шага кадра — линейно от прошлого взгляда к текущему: ровно при любом рисунке шагов кадров
                float u = (i + 1f) / n;
                float yT = lastYawIn + MathHelper.wrapDegrees(yaw - lastYawIn) * u, pT = lastPitchIn + (pitch - lastPitchIn) * u;
                float ey = MathHelper.wrapDegrees(yT - yawS), ep = pT - pitchS;
                yawV += (float) ((w * w * ey - 2 * w * yawV) * h);
                pitchV += (float) ((w * w * ep - 2 * w * pitchV) * h);
                yawS += (float) (yawV * h);
                pitchS += (float) (pitchV * h);
            }
        }
        lastYawIn = yaw;
        lastPitchIn = pitch;
        float lagYraw = MathHelper.wrapDegrees(yawS - yaw), lagPraw = pitchS - pitch;
        if (Math.abs(lagYraw) > 30) {   // резкий рывок мышью: не тащим камеру за 30° — пружину подтягиваем
            yawS = yaw + Math.signum(lagYraw) * 30;
            lagYraw = Math.signum(lagYraw) * 30;
        }
        if (Math.abs(lagPraw) > 20) {
            pitchS = pitch + Math.signum(lagPraw) * 20;
            lagPraw = Math.signum(lagPraw) * 20;
        }
        // камера смотрит ровно туда, где пружина: любой скачок взгляда игрока (рывок мыши, поправка сервера) сглажен ею
        float lagY = lagYraw, lagP = lagPraw;

        // --- шаг: фаза — интерполированный пройденный путь (как у звуков шагов), сила — сглаженная скорость
        double phase = MathHelper.lerp(tickDelta, p.prevHorizontalSpeed, p.horizontalSpeed);
        lastPhase = phase;
        double hv = p.getVelocity().horizontalLength();
        gait = ease(gait, Math.min(1, hv / 0.115), 0.2, dt);                  // плавно входит в шаг и выходит (~200 мс)
        runB = ease(runB, p.isSprinting() ? 1 : 0, 0.25, dt);
        crouchB = ease(crouchB, p.isInSneakingPose() ? 1 : 0, 0.2, dt);
        airB = ease(airB, p.isOnGround() ? 0 : 1, 0.12, dt);
        double amp = gait * (1 + 0.6 * runB) * (1 - 0.5 * crouchB) * (1 - 0.7 * airB);
        double s = Math.sin(Math.PI * phase);
        double dip = 0.5 + 0.5 * Math.cos(2 * Math.PI * phase);          // 1 на ударе пятки, плавно (без излома)
        double vert = -0.05 * amp * dip;
        double side = 0.028 * amp * s;
        float stepRoll = (float) (0.6 * amp * s);
        float stepPitch = (float) (0.55 * amp * dip);
        // --- одышка копится на бегу и спадает
        breath = (float) MathHelper.clamp(breath + (p.isSprinting() ? 0.15 : -0.05) * dt, 0, 1);
        double still = 1 - gait;
        double brRate = (0.9 + 0.8 * breath) * 2 * Math.PI / 3.6;
        double br = Math.sin(t * brRate);
        double breathY = br * 0.008 * (0.55 + 0.45 * breath) * (0.5 + 0.5 * still);
        float breathP = (float) (br * 0.17 * (0.55 + 0.45 * breath) * (0.5 + 0.5 * still));
        // медленный «увод» руки (очень мало), на бегу — мелкая дрожь, но плавная (низкие частоты)
        float drift = (float) (Math.sin(t * 0.37) * 0.12 + Math.sin(t * 0.61 + 1) * 0.08);
        float run = (float) (runB * gait);
        float jitterY = (float) ((Math.sin(t * 7.3) + Math.sin(t * 11.1 + 2)) * 0.06 * run);
        float jitterP = (float) ((Math.sin(t * 6.1 + 1) + Math.sin(t * 9.7)) * 0.05 * run);
        // --- приземление: провал и плавный возврат (критически задемпфировано, ~250 мс)
        boolean ground = p.isOnGround();
        if (ground && !wasGround && lastVy < -0.18) joltV -= Math.min(0.35, -lastVy * 0.6);
        wasGround = ground;
        lastVy = p.getVelocity().y;
        if (dt > 0) {
            double jw = 16;
            int n = Math.max(1, (int) Math.ceil(dt / 0.004));
            double h = dt / n;
            for (int i = 0; i < n; i++) {
                joltV += (-jw * jw * joltY - 2 * jw * joltV) * h;
                joltY += joltV * h;
            }
        }

        ox = side * k;
        oy = (vert + breathY + joltY * 0.1) * k;
        dYaw = (lagY + drift * 0.5f + jitterY) * k;
        dPitch = (lagP + stepPitch + breathP + drift * 0.3f + jitterP + (float) (joltY * 2)) * k;
        roll = (stepRoll - lagY * 0.04f) * k;
        hudX = (float) (side * 40 + lagY * 0.5) * k;
        hudY = (float) (-vert * 40 + lagP * 0.4) * k;
    }

    static java.io.PrintWriter log;

    /** Автотест: строка на каждый записанный кадр — время, смещения, крен, поправки углов, итоговый yaw камеры. */
    public static void logFrame() {
        if (System.getProperty("glukhoman.camlog") == null) return;
        try {
            if (log == null) {
                log = new java.io.PrintWriter(new java.io.FileWriter(System.getProperty("glukhoman.camlog")));
                log.println("t,oy,ox,roll,dyaw,dpitch,camyaw,campitch,gait,run");
            }
            MinecraftClient c = MinecraftClient.getInstance();
            var cam = c.gameRenderer.getCamera();
            log.printf(java.util.Locale.ROOT, "%.4f,%.5f,%.5f,%.4f,%.4f,%.4f,%.4f,%.4f,%.3f,%.3f%n", lastT, oy, ox, roll, dYaw, dPitch, cam.getYaw(), cam.getPitch(), gait, runB);
            log.flush();
        } catch (Exception ignored) {
        }
    }

    /** Смещение позиции камеры в мировых координатах (вбок — поперёк взгляда). */
    public static Vec3d offset(float yaw) {
        double r = Math.toRadians(yaw);
        return new Vec3d(-Math.cos(r) * ox, oy, -Math.sin(r) * ox);
    }
}
