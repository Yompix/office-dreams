package com.yompix.novograd.client;

import com.yompix.novograd.entity.CarEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Фишка Новограда — «Кино-камера» (B за рулём): поездка превращается в кино.
 * Ракурсы сменяются сами: низко сбоку, с обочины, высоко сзади, с капота; сверху и снизу — кинополосы.
 * Управление машиной остаётся у тебя.
 */
public final class CinemaCam {
    private CinemaCam() {}

    private static boolean on;
    private static ArmorStandEntity cam;
    private static int shot, shotTicks;
    private static Vec3d roadside;
    private static double cx, cy, cz;
    private static float bars;

    public static void toggle(MinecraftClient mc) {
        if (!on && !(mc.player != null && mc.player.getVehicle() instanceof CarEntity)) {
            if (mc.player != null) mc.player.sendMessage(net.minecraft.text.Text.literal("🎬 Кино-камера работает в машине"), true);
            return;
        }
        if (on) off(mc);
        else {
            on = true;
            cam = new ArmorStandEntity(EntityType.ARMOR_STAND, mc.world);
            cam.setInvisible(true);
            shot = 0;
            shotTicks = 0;
            cx = Double.NaN;
            mc.player.sendMessage(net.minecraft.text.Text.literal("🎬 Кино-камера: B — выключить"), true);
        }
    }

    private static void off(MinecraftClient mc) {
        on = false;
        if (mc.player != null && mc.getCameraEntity() == cam) mc.setCameraEntity(mc.player);
        cam = null;
    }

    public static boolean active() {
        return on;
    }

    public static void tick(MinecraftClient mc) {
        bars += ((on ? 1 : 0) - bars) * 0.15f;
        if (!on) return;
        if (mc.player == null || !(mc.player.getVehicle() instanceof CarEntity car) || mc.currentScreen != null && !(mc.currentScreen instanceof net.minecraft.client.gui.screen.ChatScreen)) {
            if (mc.player == null || !(mc.player.getVehicle() instanceof CarEntity)) off(mc);
            return;
        }
        if (mc.getCameraEntity() != cam) mc.setCameraEntity(cam);
        if (++shotTicks > 20 * 5 || shot == 1 && roadside != null && roadside.squaredDistanceTo(car.getPos()) > 34 * 34 && shotTicks > 30) {
            shot = (shot + 1 + mc.world.random.nextInt(2)) % 4;
            shotTicks = 0;
            roadside = null;
            cx = Double.NaN;
        }
        Vec3d at = car.getPos();
        double rad = Math.toRadians(car.getYaw());
        Vec3d fwd = new Vec3d(-Math.sin(rad), 0, Math.cos(rad)), right = new Vec3d(-fwd.z, 0, fwd.x);
        Vec3d want;
        switch (shot) {
            case 0 -> want = at.add(right.multiply(4.2)).add(fwd.multiply(1.5)).add(0, 0.7, 0); // низко сбоку
            case 1 -> {
                if (roadside == null) {
                    double sp = Math.max(0.3, car.getVelocity().horizontalLength()) * 20;
                    roadside = at.add(fwd.multiply(Math.min(40, 10 + sp * 1.2))).add(right.multiply(mc.world.random.nextBoolean() ? 6 : -6)).add(0, 1.2, 0);
                }
                want = roadside; // с обочины: машина проносится мимо
            }
            case 2 -> want = at.add(fwd.multiply(-11)).add(0, 5, 0); // высоко сзади
            default -> want = at.add(fwd.multiply(2.6)).add(0, 1.15, 0).add(right.multiply(0.6)); // с капота назад
        }
        if (Double.isNaN(cx) || shot == 1) {
            cx = want.x;
            cy = want.y;
            cz = want.z;
        } else {
            cx += (want.x - cx) * 0.35;
            cy += (want.y - cy) * 0.35;
            cz += (want.z - cz) * 0.35;
        }
        cam.prevX = cam.getX();
        cam.prevY = cam.getY();
        cam.prevZ = cam.getZ();
        cam.lastRenderX = cam.getX();
        cam.lastRenderY = cam.getY();
        cam.lastRenderZ = cam.getZ();
        cam.setPosition(cx, cy - cam.getStandingEyeHeight(), cz);
        Vec3d look = shot == 3 ? at.add(fwd.multiply(-8)).add(0, 1, 0) : at.add(0, 0.9, 0);
        double dx = look.x - cx, dy = look.y - cy, dz = look.z - cz;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        cam.prevYaw = cam.getYaw();
        cam.prevPitch = cam.getPitch();
        cam.setYaw(yaw);
        cam.setPitch(pitch);
        cam.setHeadYaw(yaw);
        cam.prevHeadYaw = cam.prevYaw;
    }

    public static void render(DrawContext ctx) {
        if (bars < 0.01f) return;
        int h = (int) (ctx.getScaledWindowHeight() * 0.11f * bars);
        ctx.fill(0, 0, ctx.getScaledWindowWidth(), h, 0xFF000000);
        ctx.fill(0, ctx.getScaledWindowHeight() - h, ctx.getScaledWindowWidth(), ctx.getScaledWindowHeight(), 0xFF000000);
        if (on) {
            var tr = MinecraftClient.getInstance().textRenderer;
            String s = "🎬 КИНО · B — выход";
            ctx.drawText(tr, s, ctx.getScaledWindowWidth() - tr.getWidth(s) - 8, h / 2 - 4, 0x99FFFFFF, false);
        }
    }
}
