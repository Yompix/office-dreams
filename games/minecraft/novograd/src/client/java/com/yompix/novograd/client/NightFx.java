package com.yompix.novograd.client;

import com.yompix.novograd.entity.CarEntity;
import com.yompix.novograd.vehicle.HeliEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import org.joml.Matrix4f;

/**
 * Ночная погоня (розыск ★★★ и выше, ночью): над районом висят полицейские вертолёты и шарят прожекторами
 * (световые конусы), издалека воют сирены, в дворах лают собаки. У настоящих полицейских вертолётов рядом —
 * тоже луч прожектора (на тебя). Только клиент: звук и свет, без сущностей.
 */
public final class NightFx {
    private NightFx() {}

    private static final Random R = Random.create();
    private static long nextSiren, nextDog;
    private static float fade;

    static void tick(MinecraftClient c) {
        boolean on = active(c);
        fade = MathHelper.clamp(fade + (on ? 0.02f : -0.03f), 0, 1);
        if (!on) return;
        long now = c.world.getTime();
        Vec3d p = c.player.getPos();
        if (now >= nextSiren) {
            nextSiren = now + 80 + R.nextInt(140);
            double a = R.nextDouble() * Math.PI * 2, d = 45 + R.nextInt(30);
            c.world.playSound(p.x + Math.cos(a) * d, p.y + 4, p.z + Math.sin(a) * d, PulseClient.siren(), SoundCategory.AMBIENT,
                    5.5f, 0.85f + R.nextFloat() * 0.25f, false);
        }
        if (now >= nextDog) {
            nextDog = now + 160 + R.nextInt(240);
            double a = R.nextDouble() * Math.PI * 2, d = 20 + R.nextInt(25);
            var snd = R.nextInt(3) == 0 ? SoundEvents.ENTITY_WOLF_GROWL : SoundEvents.ENTITY_WOLF_AMBIENT;
            for (int i = 0; i < 1 + R.nextInt(3); i++)
                c.world.playSound(p.x + Math.cos(a) * d, p.y, p.z + Math.sin(a) * d, snd, SoundCategory.AMBIENT, 2.5f, 0.8f + R.nextFloat() * 0.3f, false);
        }
    }

    /** Ночь и большая погоня (или «Хаос» с розыском). */
    static boolean active(MinecraftClient c) {
        return c.world != null && c.player != null && PulseClient.night(c) && PulseClient.stars() >= 3;
    }

    static void render(MinecraftClient c, MatrixStack m, VertexConsumerProvider.Immediate imm, Vec3d cam, float delta) {
        VertexConsumer vc = imm.getBuffer(RenderLayer.getLightning());
        boolean night = PulseClient.night(c);
        // настоящие полицейские вертолёты: луч на игрока (в розыске)
        if (night && PulseClient.stars() > 0) {
            for (Entity e : c.world.getEntities()) {
                if (!(e instanceof HeliEntity h) || !h.isPolice() || h.wrecked() || h.squaredDistanceTo(c.player) > 160 * 160) continue;
                Vec3d from = h.getLerpedPos(delta).add(0, -0.3, 0);
                Vec3d to = c.player.getLerpedPos(delta);
                cone(m, vc, cam, from, to, 2.4f, 70);
            }
        }
        if (fade <= 0.01f) return;
        // «вертолёты над районом»: огни в небе и шарящие лучи вокруг игрока
        double t = (c.world.getTime() + delta) / 20.0;
        Vec3d p = c.player.getLerpedPos(delta);
        for (int i = 0; i < 2; i++) {
            double orbit = t * (0.07 + i * 0.03) + i * Math.PI;
            double R1 = 60 + i * 25;
            double hx = p.x + Math.cos(orbit) * R1, hz = p.z + Math.sin(orbit) * R1;
            int top = c.world.getTopY(Heightmap.Type.MOTION_BLOCKING, (int) Math.floor(hx), (int) Math.floor(hz));
            double hy = Math.max(p.y + 38, top + 20);
            Vec3d from = new Vec3d(hx, hy, hz);
            double sweep = t * (0.45 + i * 0.2);
            double sr = 10 + 14 * (0.5 + 0.5 * Math.sin(t * 0.37 + i));
            Vec3d aim = new Vec3d(p.x + Math.cos(sweep) * sr, p.y, p.z + Math.sin(sweep * 1.3) * sr);
            int gy = c.world.getTopY(Heightmap.Type.MOTION_BLOCKING, (int) Math.floor(aim.x), (int) Math.floor(aim.z));
            aim = new Vec3d(aim.x, gy, aim.z);
            cone(m, vc, cam, from, aim, 3.2f, (int) (60 * fade));
            light(m, vc, cam, from, 0.9f, 255, 250, 230, (int) (220 * fade));
            if ((c.world.getTime() / 6 + i) % 3 == 0) light(m, vc, cam, from.add(0, 0.6, 0), 0.5f, 255, 40, 40, (int) (230 * fade));
        }
    }

    /** Световой конус: от вершины from к кругу радиуса r у to; на земле — пятно. */
    private static void cone(MatrixStack m, VertexConsumer vc, Vec3d cam, Vec3d from, Vec3d to, float r, int alpha) {
        Vec3d axis = to.subtract(from);
        double len = axis.length();
        if (len < 1) return;
        Vec3d n = axis.normalize();
        Vec3d u = Math.abs(n.y) < 0.95 ? n.crossProduct(new Vec3d(0, 1, 0)).normalize() : n.crossProduct(new Vec3d(1, 0, 0)).normalize();
        Vec3d v = n.crossProduct(u).normalize();
        m.push();
        m.translate(from.x - cam.x, from.y - cam.y, from.z - cam.z);
        Matrix4f mat = m.peek().getPositionMatrix();
        int seg = 14;
        for (int i = 0; i < seg; i++) {
            double a0 = i * Math.PI * 2 / seg, a1 = (i + 1) * Math.PI * 2 / seg;
            Vec3d b0 = axis.add(u.multiply(Math.cos(a0) * r)).add(v.multiply(Math.sin(a0) * r));
            Vec3d b1 = axis.add(u.multiply(Math.cos(a1) * r)).add(v.multiply(Math.sin(a1) * r));
            Vec3d s0 = u.multiply(Math.cos(a0) * 0.25).add(v.multiply(Math.sin(a0) * 0.25));
            Vec3d s1 = u.multiply(Math.cos(a1) * 0.25).add(v.multiply(Math.sin(a1) * 0.25));
            float[][] q = {{(float) s0.x, (float) s0.y, (float) s0.z, alpha}, {(float) s1.x, (float) s1.y, (float) s1.z, alpha},
                    {(float) b1.x, (float) b1.y, (float) b1.z, alpha / 5f}, {(float) b0.x, (float) b0.y, (float) b0.z, alpha / 5f}};
            both(vc, mat, q, 255, 250, 225);
        }
        // пятно на земле
        for (int i = 0; i < seg; i++) {
            double a0 = i * Math.PI * 2 / seg, a1 = (i + 1) * Math.PI * 2 / seg;
            float y = (float) axis.y + 0.08f;
            float[][] q = {{(float) axis.x, y, (float) axis.z, Math.min(255, alpha * 2)}, {(float) axis.x, y, (float) axis.z, Math.min(255, alpha * 2)},
                    {(float) (axis.x + Math.cos(a1) * r * 1.2), y, (float) (axis.z + Math.sin(a1) * r * 1.2), 0},
                    {(float) (axis.x + Math.cos(a0) * r * 1.2), y, (float) (axis.z + Math.sin(a0) * r * 1.2), 0}};
            both(vc, mat, q, 255, 250, 225);
        }
        m.pop();
    }

    /** Огонёк в небе (квадрат, повёрнутый к камере). */
    private static void light(MatrixStack m, VertexConsumer vc, Vec3d cam, Vec3d at, float s, int r, int g, int b, int a) {
        MinecraftClient mc = MinecraftClient.getInstance();
        m.push();
        m.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
        m.multiply(mc.gameRenderer.getCamera().getRotation());
        Matrix4f mat = m.peek().getPositionMatrix();
        both(vc, mat, new float[][]{{-s, -s, 0, a}, {s, -s, 0, a}, {s, s, 0, a}, {-s, s, 0, a}}, r, g, b);
        m.pop();
    }

    /** Четырёхугольник с обеих сторон (слой молний отсекает обратные грани). */
    private static void both(VertexConsumer vc, Matrix4f mat, float[][] q, int r, int g, int b) {
        for (int i = 0; i < 4; i++) vc.vertex(mat, q[i][0], q[i][1], q[i][2]).color(r, g, b, (int) q[i][3]);
        for (int i = 3; i >= 0; i--) vc.vertex(mat, q[i][0], q[i][1], q[i][2]).color(r, g, b, (int) q[i][3]);
    }

    static boolean policeCar(Entity e) {
        return e instanceof CarEntity car && car.isPolice();
    }
}
