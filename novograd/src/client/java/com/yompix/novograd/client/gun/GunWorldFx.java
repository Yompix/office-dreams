package com.yompix.novograd.client.gun;

import com.yompix.novograd.Novograd;
import com.yompix.novograd.client.GunFx;
import com.yompix.novograd.gta.Arsenal;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Эффекты выстрела в мире: гильзы и выпавшие магазины (прыгают по земле, звенят), вспышки у чужих стволов,
 * трассеры, искры и следы от пуль на стенах, дымок из ствола. Всё только на клиенте; данные — пакет Arsenal.ShotFx.
 */
public final class GunWorldFx {
    private GunWorldFx() {}

    private static final Identifier BITS = Novograd.id("textures/misc/gun_bits.png");
    private static final Identifier FLASH = Novograd.id("textures/misc/muzzle_flash.png");
    private static final Random RND = Random.create();

    /** Гильза / магазин: позиция, скорость, вращение. type: 0 латунь пистолет, 1 латунь винтовка, 2 красная гильза дроби, 3 магазин, 4 рожок «АК». */
    private static final class Bit {
        double x, y, z, px, py, pz, vx, vy, vz;
        float yaw, pitch, roll, pYaw, pPitch, pRoll, vyaw, vpitch;
        int type, age, bounces, life;
        boolean rest;
    }

    private record Tracer(Vec3d from, Vec3d to, long born, float width) {}

    private record Flash(Vec3d pos, Vec3d dir, long born, float size, int variant) {}

    private record Hole(Vec3d pos, Direction side, long born, float size) {}

    private static final List<Bit> BITS_LIST = new ArrayList<>();
    private static final List<Tracer> TRACERS = new ArrayList<>();
    private static final List<Flash> FLASHES = new ArrayList<>();
    private static final List<Hole> HOLES = new ArrayList<>();
    /** Кто из других игроков сейчас целится (ПКМ) — поза от третьего лица. */
    public static final Set<Integer> AIMING = new HashSet<>();

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(Arsenal.ShotFx.ID, (fx, ctx) -> ctx.client().execute(() -> onShot(fx)));
        ClientPlayNetworking.registerGlobalReceiver(Arsenal.AimS2C.ID, (a, ctx) -> ctx.client().execute(() -> {
            if (a.aiming()) AIMING.add(a.entity());
            else AIMING.remove(a.entity());
        }));
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(c -> tick(c));
        WorldRenderEvents.AFTER_ENTITIES.register(GunWorldFx::render);
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> {
            AIMING.clear();
            BITS_LIST.clear();
            HOLES.clear();
        });
    }

    // ------------------------------------------------------------------ пакет выстрела
    private static void onShot(Arsenal.ShotFx fx) {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.world == null || c.player == null) return;
        Entity shooter = c.world.getEntityById(fx.shooter());
        boolean me = shooter == c.player;
        boolean fp = me && c.options.getPerspective().isFirstPerson();
        if (!me) GunFx.OTHERS_SHOT.put(fx.shooter(), System.nanoTime());
        Vec3d eye = new Vec3d(fx.fx(), fx.fy(), fx.fz());
        // дуло: у себя от первого лица — где реально нарисовано оружие, иначе — в руках у модели
        Vec3d muzzle;
        if (fp && GunFx.muzzleWorld != null) muzzle = GunFx.muzzleWorld;
        else if (shooter instanceof LivingEntity le) muzzle = thirdPersonMuzzle(le, eye);
        else muzzle = eye;
        int n = fx.what().length;
        for (int i = 0; i < n; i++) {
            Vec3d end = new Vec3d(fx.ends()[i * 3], fx.ends()[i * 3 + 1], fx.ends()[i * 3 + 2]);
            int w = fx.what()[i] & 3;
            if (end.squaredDistanceTo(muzzle) > 4 && (n == 1 || i % 2 == 0))
                TRACERS.add(new Tracer(muzzle, end, System.nanoTime(), n > 1 ? 0.012f : fx.kind() == 4 ? 0.03f : 0.02f));
            if (w == 1) impact(c, end, Direction.byId((fx.what()[i] >> 2) & 7), n > 1);
        }
        if (!fp) {
            // чужая вспышка и гильза
            if (shooter instanceof LivingEntity le) {
                Vec3d dir = le.getRotationVec(1f);
                FLASHES.add(new Flash(muzzle, dir, System.nanoTime(), fx.kind() == 2 || fx.kind() == 4 ? 0.55f : 0.38f, RND.nextInt(4)));
                if (fx.kind() != 2 && fx.kind() != 4) {
                    Vec3d right = dir.crossProduct(new Vec3d(0, 1, 0)).normalize();
                    Vec3d port = muzzle.subtract(dir.multiply(0.35)).add(0, 0.05, 0);
                    spawnBit(port, right.multiply(0.12).add(0, 0.12, 0).add(dir.multiply(-0.02)), fx.kind() == 3 ? 1 : 0);
                }
                c.world.addParticle(ParticleTypes.SMOKE, muzzle.x, muzzle.y, muzzle.z, dir.x * 0.02, 0.02, dir.z * 0.02);
            }
        }
        if (me && fx.hit() > 0) GunHud.hit(fx.hit() == 2);
    }

    /** Примерно где дуло у модели с поднятыми руками. */
    public static Vec3d thirdPersonMuzzle(LivingEntity e, Vec3d eye) {
        Vec3d dir = e.getRotationVec(1f);
        Vec3d right = dir.crossProduct(new Vec3d(0, 1, 0)).normalize();
        return eye.add(dir.multiply(1.0)).add(right.multiply(0.3)).add(0, -0.32, 0);
    }

    private static void impact(MinecraftClient c, Vec3d p, Direction side, boolean pellet) {
        Vec3d nrm = Vec3d.of(side.getVector());
        for (int k = 0; k < (pellet ? 2 : 5); k++)
            c.world.addParticle(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, nrm.x * 0.15 + RND.nextGaussian() * 0.08, nrm.y * 0.15 + RND.nextGaussian() * 0.08 + 0.05,
                    nrm.z * 0.15 + RND.nextGaussian() * 0.08);
        c.world.addParticle(ParticleTypes.SMOKE, p.x + nrm.x * 0.05, p.y + nrm.y * 0.05, p.z + nrm.z * 0.05, nrm.x * 0.02, 0.01, nrm.z * 0.02);
        if (!pellet) c.world.addParticle(ParticleTypes.POOF, p.x + nrm.x * 0.08, p.y + nrm.y * 0.08, p.z + nrm.z * 0.08, nrm.x * 0.03, 0.01, nrm.z * 0.03);
        HOLES.add(new Hole(p.add(nrm.multiply(0.004 + RND.nextFloat() * 0.002)), side, System.nanoTime(), pellet ? 0.05f : 0.075f));
        if (HOLES.size() > 120) HOLES.remove(0);
    }

    // ------------------------------------------------------------------ гильзы и магазины
    public static void spawnBit(Vec3d pos, Vec3d vel, int type) {
        Bit b = new Bit();
        b.x = b.px = pos.x;
        b.y = b.py = pos.y;
        b.z = b.pz = pos.z;
        b.vx = vel.x + RND.nextGaussian() * 0.015;
        b.vy = vel.y + RND.nextGaussian() * 0.015;
        b.vz = vel.z + RND.nextGaussian() * 0.015;
        b.yaw = RND.nextFloat() * 360;
        b.vyaw = (RND.nextFloat() - 0.5f) * 60;
        b.vpitch = 25 + RND.nextFloat() * 30;
        b.type = type;
        b.life = type >= 3 ? 240 : 160;
        BITS_LIST.add(b);
        if (BITS_LIST.size() > 160) BITS_LIST.remove(0);
    }

    private static void tick(MinecraftClient c) {
        if (c.world == null || c.isPaused()) return;
        for (Iterator<Bit> it = BITS_LIST.iterator(); it.hasNext(); ) {
            Bit b = it.next();
            b.px = b.x;
            b.py = b.y;
            b.pz = b.z;
            b.pYaw = b.yaw;
            b.pPitch = b.pitch;
            b.pRoll = b.roll;
            if (++b.age > b.life) {
                it.remove();
                continue;
            }
            if (b.rest) continue;
            b.vy -= b.type >= 3 ? 0.06 : 0.045;
            b.vx *= 0.98;
            b.vz *= 0.98;
            double nx = b.x + b.vx, ny = b.y + b.vy, nz = b.z + b.vz;
            double floor = floorAt(c, nx, ny, nz);
            if (ny <= floor) {
                ny = floor;
                if (Math.abs(b.vy) > 0.06 && b.bounces < 4) {
                    if (b.bounces < 3) {
                        float pitch = b.type == 2 ? 0.9f : b.type >= 3 ? 0.55f : 1f + RND.nextFloat() * 0.25f;
                        var snd = b.type == 2 || b.type >= 3 ? Arsenal.CASING_SHELL : Arsenal.CASING;
                        c.getSoundManager().play(new PositionedSoundInstance(snd, SoundCategory.PLAYERS, (b.type >= 3 ? 0.5f : 0.3f) / (1 + b.bounces),
                                pitch, RND, nx, ny, nz));
                    }
                    b.vy = -b.vy * (b.type >= 3 ? 0.2 : 0.38);
                    b.vx *= 0.55;
                    b.vz *= 0.55;
                    b.vpitch *= 0.5f;
                    b.vyaw *= 0.6f;
                    b.bounces++;
                } else {
                    b.vy = 0;
                    b.vx *= 0.6;
                    b.vz *= 0.6;
                    if (b.vx * b.vx + b.vz * b.vz < 1e-4) {
                        b.rest = true;
                        b.pitch = b.type >= 3 ? 90 : 90; // лежит на боку
                        b.roll = 0;
                    }
                }
            }
            // в стену — отскок
            if (!c.world.getBlockState(BlockPos.ofFloored(nx, ny + 0.02, nz)).getCollisionShape(c.world, BlockPos.ofFloored(nx, ny + 0.02, nz)).isEmpty()
                    && ny > floor + 0.05) {
                b.vx = -b.vx * 0.3;
                b.vz = -b.vz * 0.3;
                nx = b.x;
                nz = b.z;
            }
            b.x = nx;
            b.y = ny;
            b.z = nz;
            b.yaw += b.vyaw;
            b.pitch += b.vpitch;
        }
        long now = System.nanoTime();
        TRACERS.removeIf(t -> now - t.born > 400_000_000L);
        FLASHES.removeIf(f -> now - f.born > 60_000_000L);
        HOLES.removeIf(h -> now - h.born > 20_000_000_000L);
    }

    /** Высота «пола» под точкой (верх коллизии блока). */
    private static double floorAt(MinecraftClient c, double x, double y, double z) {
        BlockPos p = BlockPos.ofFloored(x, y, z);
        for (int i = 0; i < 2; i++) {
            BlockPos q = p.down(i);
            var shape = c.world.getBlockState(q).getCollisionShape(c.world, q);
            if (!shape.isEmpty()) {
                double top = q.getY() + shape.getMax(Direction.Axis.Y);
                if (top <= y + 0.6) return top + 0.008;
            }
        }
        return -1e9;
    }

    // ------------------------------------------------------------------ отрисовка
    private static void render(WorldRenderContext ctx) {
        if (ctx.consumers() == null || ctx.world() == null) return;
        if (BITS_LIST.isEmpty() && TRACERS.isEmpty() && FLASHES.isEmpty() && HOLES.isEmpty()) return;
        Vec3d cam = ctx.camera().getPos();
        MatrixStack m = ctx.matrixStack() != null ? ctx.matrixStack() : new MatrixStack();
        float td = ctx.tickCounter().getTickDelta(false);
        long now = System.nanoTime();
        // гильзы и магазины
        if (!BITS_LIST.isEmpty()) {
            VertexConsumer vc = ctx.consumers().getBuffer(RenderLayer.getEntityCutoutNoCull(BITS));
            for (Bit b : BITS_LIST) {
                double x = MathHelper.lerp(td, b.px, b.x), y = MathHelper.lerp(td, b.py, b.y), z = MathHelper.lerp(td, b.pz, b.z);
                if ((x - cam.x) * (x - cam.x) + (y - cam.y) * (y - cam.y) + (z - cam.z) * (z - cam.z) > 48 * 48) continue;
                int light = WorldRenderer.getLightmapCoordinates(ctx.world(), BlockPos.ofFloored(x, y + 0.05, z));
                m.push();
                m.translate(x - cam.x, y - cam.y, z - cam.z);
                m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(MathHelper.lerp(td, b.pYaw, b.yaw)));
                m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(MathHelper.lerp(td, b.pPitch, b.pitch)));
                switch (b.type) {
                    case 0 -> cuboid(vc, m, 0.011f, 0.011f, 0.024f, 0, 0, light);
                    case 1 -> cuboid(vc, m, 0.011f, 0.011f, 0.036f, 0, 0, light);
                    case 2 -> {
                        cuboid(vc, m, 0.018f, 0.018f, 0.04f, 16, 0, light);
                        m.translate(0, 0, 0.044f);
                        cuboid(vc, m, 0.0195f, 0.0195f, 0.008f, 0, 0, light);
                    }
                    case 3 -> cuboid(vc, m, 0.035f, 0.11f, 0.06f, 0, 8, light);
                    default -> cuboid(vc, m, 0.035f, 0.13f, 0.07f, 16, 8, light);
                }
                m.pop();
            }
        }
        // следы от пуль
        if (!HOLES.isEmpty()) {
            VertexConsumer vc = ctx.consumers().getBuffer(RenderLayer.getEntityTranslucent(BITS));
            for (Hole h : HOLES) {
                float age = (now - h.born) / 1e9f;
                int a = (int) (230 * MathHelper.clamp(1 - (age - 16) / 4f, 0, 1));
                m.push();
                m.translate(h.pos.x - cam.x, h.pos.y - cam.y, h.pos.z - cam.z);
                m.multiply(h.side.getRotationQuaternion());
                m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90));
                Matrix4f mat = m.peek().getPositionMatrix();
                float s = h.size;
                int light = WorldRenderer.getLightmapCoordinates(ctx.world(), BlockPos.ofFloored(h.pos.add(Vec3d.of(h.side.getVector()).multiply(0.1))));
                float u0 = 0, u1 = 1, v0 = 0.5f, v1 = 1;
                vc.vertex(mat, -s, -s, 0).color(14, 12, 10, a).texture(u0, v1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(m.peek(), 0, 0, 1);
                vc.vertex(mat, s, -s, 0).color(14, 12, 10, a).texture(u1, v1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(m.peek(), 0, 0, 1);
                vc.vertex(mat, s, s, 0).color(14, 12, 10, a).texture(u1, v0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(m.peek(), 0, 0, 1);
                vc.vertex(mat, -s, s, 0).color(14, 12, 10, a).texture(u0, v0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(m.peek(), 0, 0, 1);
                m.pop();
            }
        }
        // трассеры: яркий отрезок летит от ствола к цели
        if (!TRACERS.isEmpty()) {
            VertexConsumer vc = ctx.consumers().getBuffer(RenderLayer.getLightning());
            for (Tracer t : TRACERS) {
                float age = (now - t.born) / 1e9f;
                double len = t.to.distanceTo(t.from);
                double head = Math.min(len, 6 + age * 320), tail = Math.max(0, head - 5.5);
                if (tail >= len) continue;
                Vec3d dir = t.to.subtract(t.from).normalize();
                Vec3d a = t.from.add(dir.multiply(tail)).subtract(cam), b = t.from.add(dir.multiply(head)).subtract(cam);
                Vec3d side = dir.crossProduct(a.add(b).multiply(0.5)).normalize().multiply(t.width);
                Matrix4f mat = m.peek().getPositionMatrix();
                int alpha = (int) (200 * MathHelper.clamp(1 - age / 0.35f, 0, 1));
                vc.vertex(mat, (float) (a.x - side.x), (float) (a.y - side.y), (float) (a.z - side.z)).color(255, 210, 120, 0);
                vc.vertex(mat, (float) (a.x + side.x), (float) (a.y + side.y), (float) (a.z + side.z)).color(255, 210, 120, 0);
                vc.vertex(mat, (float) (b.x + side.x), (float) (b.y + side.y), (float) (b.z + side.z)).color(255, 240, 200, alpha);
                vc.vertex(mat, (float) (b.x - side.x), (float) (b.y - side.y), (float) (b.z - side.z)).color(255, 240, 200, alpha);
            }
        }
        // вспышки у чужих стволов (звезда к камере)
        if (!FLASHES.isEmpty()) {
            VertexConsumer vc = ctx.consumers().getBuffer(RenderLayer.getEyes(FLASH));
            for (Flash f : FLASHES) {
                m.push();
                m.translate(f.pos.x - cam.x, f.pos.y - cam.y, f.pos.z - cam.z);
                m.multiply(ctx.camera().getRotation());
                m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(f.variant * 77));
                Matrix4f mat = m.peek().getPositionMatrix();
                float s = f.size, u0 = (f.variant % 2) * 0.5f, v0 = (f.variant / 2) * 0.5f;
                vc.vertex(mat, -s, -s, 0).color(255, 255, 255, 255).texture(u0, v0 + 0.5f).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0, 0, 1);
                vc.vertex(mat, s, -s, 0).color(255, 255, 255, 255).texture(u0 + 0.5f, v0 + 0.5f).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0, 0, 1);
                vc.vertex(mat, s, s, 0).color(255, 255, 255, 255).texture(u0 + 0.5f, v0).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0, 0, 1);
                vc.vertex(mat, -s, s, 0).color(255, 255, 255, 255).texture(u0, v0).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0, 0, 1);
                m.pop();
            }
        }
    }

    /** Коробочка с центром в 0 (полуразмеры hx, hy, hz), текстура — клетка 16×8 из gun_bits (u0, v0 в пикселях). */
    private static void cuboid(VertexConsumer vc, MatrixStack m, float hx, float hy, float hz, int u0, int v0, int light) {
        MatrixStack.Entry e = m.peek();
        Matrix4f mat = e.getPositionMatrix();
        float a0 = u0 / 32f, a1 = (u0 + 16) / 32f, b0 = v0 / 32f, b1 = (v0 + 8) / 32f;
        float[][] f = {
                {-hx, -hy, hz, hx, -hy, hz, hx, hy, hz, -hx, hy, hz, 0, 0, 1},
                {hx, -hy, -hz, -hx, -hy, -hz, -hx, hy, -hz, hx, hy, -hz, 0, 0, -1},
                {hx, -hy, hz, hx, -hy, -hz, hx, hy, -hz, hx, hy, hz, 1, 0, 0},
                {-hx, -hy, -hz, -hx, -hy, hz, -hx, hy, hz, -hx, hy, -hz, -1, 0, 0},
                {-hx, hy, hz, hx, hy, hz, hx, hy, -hz, -hx, hy, -hz, 0, 1, 0},
                {-hx, -hy, -hz, hx, -hy, -hz, hx, -hy, hz, -hx, -hy, hz, 0, -1, 0}};
        float[][] uv = {{a0, b1}, {a1, b1}, {a1, b0}, {a0, b0}};
        for (float[] q : f)
            for (int i = 0; i < 4; i++)
                vc.vertex(mat, q[i * 3], q[i * 3 + 1], q[i * 3 + 2]).color(255, 255, 255, 255).texture(uv[i][0], uv[i][1]).overlay(OverlayTexture.DEFAULT_UV)
                        .light(light).normal(e, q[12], q[13], q[14]);
    }

    /** Дымок из ствола после стрельбы. */
    public static void smoke(Vec3d p, float amount) {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.world == null || p == null) return;
        if (RND.nextFloat() < amount)
            c.world.addParticle(ParticleTypes.SMOKE, p.x + RND.nextGaussian() * 0.01, p.y, p.z + RND.nextGaussian() * 0.01, 0, 0.012 + RND.nextFloat() * 0.01, 0);
    }
}
