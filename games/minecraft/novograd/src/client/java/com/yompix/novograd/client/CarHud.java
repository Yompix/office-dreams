package com.yompix.novograd.client;

import com.yompix.novograd.entity.CarEntity;
import com.yompix.novograd.vehicle.MotorVehicle;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/**
 * Машина на клиенте: ручник на Пробел, спидометр с передачей справа от радара, следы шин на асфальте при заносе.
 */
public final class CarHud {
    private CarHud() {}

    public static void init() {
        ClientTickEvents.START_CLIENT_TICK.register(c -> {
            boolean inCar = c.player != null && c.player.getVehicle() instanceof CarEntity car && !(car instanceof MotorVehicle)
                    && car.getControllingPassenger() == c.player && c.currentScreen == null;
            CarEntity.handbrakeInput = inCar && c.options.jumpKey.isPressed();
        });
        ClientTickEvents.END_CLIENT_TICK.register(CarHud::tickMarks);
        HudRenderCallback.EVENT.register((ctx, t) -> {
            float k = NovogradConfig.hud();
            int sh = ctx.getScaledWindowHeight();
            ctx.getMatrices().push();
            ctx.getMatrices().translate(0, sh, 0);
            ctx.getMatrices().scale(k, k, 1);
            ctx.getMatrices().translate(0, -sh, 0);
            speedo(ctx);
            ctx.getMatrices().pop();
        });
        WorldRenderEvents.AFTER_TRANSLUCENT.register(CarHud::renderMarks);
    }

    // ------------------------------------------------------------------ спидометр
    private static float shownKmh;

    private static void speedo(DrawContext ctx) {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.player == null || c.options.hudHidden || !(c.player.getVehicle() instanceof CarEntity car) || car instanceof MotorVehicle) return;
        if (LifeHud.cinematic(c) || CinemaCam.active()) return;
        float kmh = Math.abs(car.clientSpeed) * 20 * 3.6f;
        shownKmh = MathHelper.lerp(0.25f, shownKmh, kmh);
        int w = ctx.getScaledWindowWidth(), h = ctx.getScaledWindowHeight();
        int pw = 78, ph = 44;
        // справа от радара (радар: x 12..144, низ — полоски здоровья на h-13); не залезаем на хотбар
        int x = NovogradConfig.get().minimap ? 152 : 12, y = h - 13 - ph;
        if (x + pw > w / 2 - 95) y = h - 26 - ph;
        ctx.fill(x - 1, y - 1, x + pw + 1, y + ph + 1, 0xC0000000);
        ctx.fill(x, y, x + pw, y + ph, 0xE0101317);
        var tr = c.textRenderer;
        // крупные цифры скорости
        String num = String.valueOf(Math.round(shownKmh));
        var mat = ctx.getMatrices();
        mat.push();
        mat.translate(x + 6, y + 6, 0);
        mat.scale(2.2f, 2.2f, 1);
        boolean red = car.rpm > 0.93f && car.gear > 0;
        ctx.drawText(tr, num, 0, 0, car.nitroOn() ? 0xFF7FD8FF : 0xFFFFFFFF, true);
        mat.pop();
        ctx.drawText(tr, "км/ч", x + 6 + (int) (tr.getWidth(num) * 2.2f) + 3, y + 15, 0xFF9AA4AE, false);
        // передача
        String g = car.gear < 0 ? "R" : car.gear == 0 ? "N" : String.valueOf(car.gear);
        int gx = x + pw - 18;
        ctx.fill(gx - 2, y + 4, gx + 14, y + 22, 0xFF1E242B);
        mat.push();
        mat.translate(gx + 2.5f, y + 6, 0);
        mat.scale(1.6f, 1.6f, 1);
        ctx.drawText(tr, g, 0, 0, car.gear < 0 ? 0xFFFF8A65 : red ? 0xFFFF5252 : 0xFFFFC107, true);
        mat.pop();
        // тахометр: полоска оборотов с красной зоной
        int bx = x + 6, by = y + 28, bw = pw - 12;
        ctx.fill(bx, by, bx + bw, by + 4, 0xFF2A2F36);
        ctx.fill(bx + (int) (bw * 0.85f), by, bx + bw, by + 4, 0xFF4A1E1E);
        int fillW = (int) (bw * MathHelper.clamp(car.rpm, 0, 1));
        for (int i = 0; i < fillW; i += 3) {
            float f = i / (float) bw;
            int col = f > 0.85f ? 0xFFFF4040 : f > 0.65f ? 0xFFFFC107 : 0xFF5BC236;
            ctx.fill(bx + i, by, bx + Math.min(fillW, i + 2), by + 4, col);
        }
        // здоровье машины, нитро, ручник
        int hp = car.health();
        ctx.fill(bx, by + 8, bx + bw, by + 10, 0xFF2A2F36);
        ctx.fill(bx, by + 8, bx + (int) (bw * MathHelper.clamp(hp / 100f, 0, 1)), by + 10, hp > 60 ? 0xFF4CAF50 : hp > 30 ? 0xFFFFC107 : 0xFFF44336);
        int ix = x + pw - 4;
        if (car.handbrake) {
            ctx.drawText(tr, "(P)", ix - tr.getWidth("(P)"), y + ph + 3, 0xFFFF5252, true);
        } else if (car.hasNitro()) {
            String n = car.nitroOn() ? "NOS!" : "NOS";
            ctx.drawText(tr, n, ix - tr.getWidth(n), y + ph + 3 > h - 10 ? y - 10 : y + ph + 3, car.nitroOn() ? 0xFF7FD8FF : 0xFF4A6A80, true);
        }
        DriveClient.fuelGauge(ctx, car, x, y, pw, ph); // бензин справа от спидометра
    }

    // ------------------------------------------------------------------ следы шин
    private record Mark(Vec3d a, Vec3d b, long born) {}

    private static final ArrayDeque<Mark> MARKS = new ArrayDeque<>();
    private static final Map<Integer, Vec3d[]> LAST = new HashMap<>();
    private static final int LIFE = 20 * 25, MAX = 900;

    private static void tickMarks(MinecraftClient c) {
        if (c.world == null || c.player == null) {
            MARKS.clear();
            LAST.clear();
            return;
        }
        long now = c.world.getTime();
        while (!MARKS.isEmpty() && (now - MARKS.peekFirst().born > LIFE || MARKS.size() > MAX)) MARKS.pollFirst();
        if (c.isPaused()) return;
        for (CarEntity car : c.world.getEntitiesByClass(CarEntity.class, c.player.getBoundingBox().expand(64), e -> !(e instanceof MotorVehicle) && !e.isBike() && !e.isScooter())) {
            if (!car.skidding || !car.isOnGround() && car.getVelocity().y < -0.1) {
                LAST.remove(car.getId());
                continue;
            }
            double rad = Math.toRadians(car.getYaw());
            double fx = -Math.sin(rad), fz = Math.cos(rad), rx = -Math.cos(rad), rz = -Math.sin(rad);
            Vec3d[] now2 = new Vec3d[2];
            for (int i = 0; i < 2; i++) {
                double side = i == 0 ? 0.8 : -0.8, back = -1.0;
                now2[i] = new Vec3d(car.getX() + fx * back + rx * side, car.getY() + 0.02, car.getZ() + fz * back + rz * side);
            }
            Vec3d[] prev = LAST.put(car.getId(), now2);
            if (prev != null && prev[0].squaredDistanceTo(now2[0]) < 4 && prev[0].squaredDistanceTo(now2[0]) > 0.0004)
                for (int i = 0; i < 2; i++) MARKS.addLast(new Mark(prev[i], now2[i], now));
        }
        LAST.keySet().removeIf(id -> c.world.getEntityById(id) == null);
    }

    private static void renderMarks(WorldRenderContext ctx) {
        MinecraftClient c = MinecraftClient.getInstance();
        if (MARKS.isEmpty() || c.world == null || ctx.consumers() == null) return;
        Vec3d cam = ctx.camera().getPos();
        MatrixStack m = ctx.matrixStack() != null ? ctx.matrixStack() : new MatrixStack();
        Matrix4f mat = m.peek().getPositionMatrix();
        // прозрачные цветные четырёхугольники без текстуры; координаты — от камеры (без потери точности)
        VertexConsumer vc = ctx.consumers().getBuffer(RenderLayer.getDebugQuads());
        long now = c.world.getTime();
        float hw = 0.13f;
        for (Mark k : MARKS) {
            if (k.a.squaredDistanceTo(cam) > 70 * 70) continue;
            float age = (now - k.born) / (float) LIFE;
            int alpha = (int) (170 * (1 - age * age));
            if (alpha <= 4) continue;
            Vec3d d = k.b.subtract(k.a);
            double len = Math.sqrt(d.x * d.x + d.z * d.z);
            if (len < 1e-4) continue;
            float px = (float) (-d.z / len * hw), pz = (float) (d.x / len * hw);
            float ax = (float) (k.a.x - cam.x), ay = (float) (k.a.y - cam.y), az = (float) (k.a.z - cam.z);
            float bx = (float) (k.b.x - cam.x), by = (float) (k.b.y - cam.y), bz = (float) (k.b.z - cam.z);
            int r = 16, g = 16, bl = 18;
            vc.vertex(mat, ax - px, ay, az - pz).color(r, g, bl, alpha);
            vc.vertex(mat, ax + px, ay, az + pz).color(r, g, bl, alpha);
            vc.vertex(mat, bx + px, by, bz + pz).color(r, g, bl, alpha);
            vc.vertex(mat, bx - px, by, bz - pz).color(r, g, bl, alpha);
        }
    }
}
