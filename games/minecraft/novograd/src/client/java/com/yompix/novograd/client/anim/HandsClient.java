package com.yompix.novograd.client.anim;

import com.yompix.novograd.Novograd;
import com.yompix.novograd.client.GunFx;
import com.yompix.novograd.client.NovogradClient;
import com.yompix.novograd.client.NovogradConfig;
import com.yompix.novograd.life.HandKind;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 0.9.8 «Руки» на клиенте: кто что делает (пакеты «hand_start»/«hand_end» от сервера), подход к месту и разворот,
 * звуки по меткам, дым/вспышки; руки от третьего лица (IK: рука тянется к точке раскадровки {@link HandsPose}) и от
 * первого (свои кисти и предметы в кадре вместо ванильной руки). Сдвинулся с места — «hand_cancel».
 */
public final class HandsClient {
    private HandsClient() {}

    static final class Active {
        HandKind k;
        String v;
        long t0;
        Vec3d anchor, target;
        float yaw;
        int lastCue = -1;
        long endTick = Long.MAX_VALUE;
        boolean cancelSent;
    }

    private static final Map<UUID, Active> A = new HashMap<>();
    private static final HandsPose.Frame F = new HandsPose.Frame(), F3 = new HandsPose.Frame();

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(HandsClient::tick);
        net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.AFTER_ENTITIES.register(HandsClient::screens);
    }

    // ------------------------------------------------------------------ экран банкомата: состояние по ходу действия
    private static final net.minecraft.util.Identifier STATES = Novograd.id("textures/misc/atm_states.png");
    /** Свой банкомат, пока открыт экран суммы (после карты и PIN). */
    private static Vec3d menuAt;

    /** Кадр экрана: 0 карта, 1–5 PIN, 6 сумма, 7 обработка, 8 деньги, 9 карта назад; −1 — заставка блока. */
    static int screenState(Active a, float t) {
        return switch (a.k) {
            case ATM_IN -> t < 17 ? 0 : t >= 42 ? 5 : 1 + (int) MathHelper.clamp((t - 25) / 4f + 0.5f, 0, 4);
            case ATM_CASH -> t < 20 ? 7 : t < 44 ? 8 : 9;
            case ATM_DEPOSIT -> t < 38 ? 7 : 9;
            case ATM_CARD -> 9;
            default -> -1;
        };
    }

    private static void screens(net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext ctx) {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.world == null || ctx.consumers() == null) return;
        float td = ctx.tickCounter().getTickDelta(false);
        if (c.currentScreen instanceof com.yompix.novograd.client.screen.AtmScreen && menuAt != null) screen(ctx, c, menuAt, 6);
        else menuAt = null;
        for (Active a : A.values()) {
            float t = time(a, td);
            int st = screenState(a, t);
            if (st < 0 || a.endTick != Long.MAX_VALUE) continue;
            screen(ctx, c, a.target, st);
            if (a.k == HandKind.ATM_IN && c.player != null && A.get(c.player.getUuid()) == a) menuAt = a.target;
        }
    }

    private static void screen(net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext ctx, MinecraftClient c, Vec3d target, int frame) {
        net.minecraft.util.math.BlockPos bp = net.minecraft.util.math.BlockPos.ofFloored(target.x, target.y - 0.85, target.z);
        var bs = c.world.getBlockState(bp);
        if (!(bs.isOf(com.yompix.novograd.ModContent.ATM) || bs.isOf(com.yompix.novograd.ModContent.ATM_WALL))) return;
        var f = bs.get(net.minecraft.state.property.Properties.HORIZONTAL_FACING);
        Vec3d cam = ctx.camera().getPos();
        Vec3d ctr = Vec3d.ofBottomCenter(bp).add(f.getOffsetX() * 0.2585, 23.5 / 16.0, f.getOffsetZ() * 0.2585);
        if (ctr.squaredDistanceTo(cam) > 48 * 48) return;
        Vec3d right = new Vec3d(f.getOffsetZ(), 0, -f.getOffsetX());
        float hw = 4.5f / 16f, hh = 3f / 16f;
        MatrixStack m = ctx.matrixStack();
        m.push();
        m.translate(ctr.x - cam.x, ctr.y - cam.y, ctr.z - cam.z);
        var entry = m.peek();
        org.joml.Matrix4f mat = entry.getPositionMatrix();
        var vc = ctx.consumers().getBuffer(net.minecraft.client.render.RenderLayer.getEntityCutout(STATES));
        float v0 = frame * 12 / 120f, v1 = (frame + 1) * 12 / 120f;
        int light = 0xF000F0;
        float rx = (float) right.x, rz = (float) right.z, nx = f.getOffsetX(), nz = f.getOffsetZ();
        // лево зрителя = −right
        vc.vertex(mat, -rx * hw, -hh, -rz * hw).color(255, 255, 255, 255).texture(0, v1).overlay(net.minecraft.client.render.OverlayTexture.DEFAULT_UV).light(light).normal(entry, nx, 0, nz);
        vc.vertex(mat, rx * hw, -hh, rz * hw).color(255, 255, 255, 255).texture(1, v1).overlay(net.minecraft.client.render.OverlayTexture.DEFAULT_UV).light(light).normal(entry, nx, 0, nz);
        vc.vertex(mat, rx * hw, hh, rz * hw).color(255, 255, 255, 255).texture(1, v0).overlay(net.minecraft.client.render.OverlayTexture.DEFAULT_UV).light(light).normal(entry, nx, 0, nz);
        vc.vertex(mat, -rx * hw, hh, -rz * hw).color(255, 255, 255, 255).texture(0, v0).overlay(net.minecraft.client.render.OverlayTexture.DEFAULT_UV).light(light).normal(entry, nx, 0, nz);
        m.pop();
    }

    // ------------------------------------------------------------------ сеть
    public static boolean receive(MinecraftClient c, String type, NbtCompound n) {
        switch (type) {
            case "hand_start" -> {
                HandKind k = HandKind.of(n.getString("k"));
                if (k == null || !n.containsUuid("who")) return true;
                Active a = new Active();
                a.k = k;
                a.v = n.getString("v");
                a.t0 = n.getLong("t0");
                a.anchor = new Vec3d(n.getDouble("ax"), n.getDouble("ay"), n.getDouble("az"));
                a.target = new Vec3d(n.getDouble("tx"), n.getDouble("ty"), n.getDouble("tz"));
                a.yaw = n.getFloat("yaw");
                A.put(n.getUuid("who"), a);
            }
            case "hand_end" -> {
                if (!n.containsUuid("who")) return true;
                Active a = A.get(n.getUuid("who"));
                if (a != null && a.k.id().equals(n.getString("k")) && c.world != null) {
                    // прервали — руки плавно возвращаются (5 тиков), предметы исчезают
                    if (n.getBoolean("x")) a.endTick = c.world.getTime();
                    else A.remove(n.getUuid("who"));
                    if (c.player != null && n.getUuid("who").equals(c.player.getUuid()) && a.k == HandKind.CAR_IN && !n.getBoolean("x")
                            && NovogradConfig.get().seatBelt) NovogradClient.send("hand_belt");
                }
            }
            case "hand_gunrl" -> GunThird.receive(c, n);
            case "stash_screen" -> c.setScreen(new com.yompix.novograd.client.city.StashScreen());
            default -> {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ время
    static float time(Active a, float tickDelta) {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.world == null) return 0;
        return c.world.getTime() - a.t0 + tickDelta;
    }

    /** Действие игрока (для других систем: оружие прячется, жесты не мешают). */
    public static boolean active(PlayerEntity p) {
        return A.containsKey(p.getUuid());
    }

    public static boolean blocking(PlayerEntity p) {
        Active a = A.get(p.getUuid());
        return a != null && a.k.blocking;
    }

    /** Кадр игрока p сейчас (null — ничего не делает). Вес плавно гаснет после прерывания. */
    static HandsPose.Frame frame(PlayerEntity p, float tickDelta, HandsPose.Frame f) {
        Active a = A.get(p.getUuid());
        if (a == null) return null;
        float t = time(a, tickDelta);
        if (t < 0) return null;
        HandsPose.sample(a.k, a.v, Math.min(t, a.k.len), f);
        MinecraftClient c = MinecraftClient.getInstance();
        if (a.endTick != Long.MAX_VALUE && c.world != null) {
            float fade = 1 - MathHelper.clamp((c.world.getTime() - a.endTick + tickDelta) / 5f, 0, 1);
            f.wr *= fade;
            f.wl *= fade;
            f.crouch *= fade;
            f.lookW *= fade;
            f.propR = "";
            f.propL = "";
        }
        return f;
    }

    // ------------------------------------------------------------------ такт: подход, отмена, звуки, частицы
    private static void tick(MinecraftClient c) {
        if (c.world == null || c.player == null) {
            A.clear();
            return;
        }
        long now = c.world.getTime();
        var it = A.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            Active a = e.getValue();
            int t = (int) (now - a.t0);
            if (t > a.k.len + 6 || now - a.endTick > 6) {
                it.remove();
                continue;
            }
            PlayerEntity p = c.world.getPlayerByUuid(e.getKey());
            if (p == null) continue;
            // звуки по меткам — у всех рядом (позиционно)
            if (a.endTick == Long.MAX_VALUE) for (HandKind.Cue cue : a.k.cues(a.v))
                if (cue.at() > a.lastCue && cue.at() <= t) {
                    SoundEvent ev = SoundEvent.of(cue.sound().contains(":") ? net.minecraft.util.Identifier.of(cue.sound()) : Novograd.id("hands." + cue.sound()));
                    c.world.playSound(p.getX(), p.getY() + 1, p.getZ(), ev, SoundCategory.PLAYERS, cue.vol(), cue.pitch() * (0.96f + p.getRandom().nextFloat() * 0.08f), false);
                }
            a.lastCue = Math.max(a.lastCue, t);
            particles(c, p, a, t);
            if (p == c.player) self(c, c.player, a, t);
        }
    }

    private static void particles(MinecraftClient c, PlayerEntity p, Active a, int t) {
        if (a.endTick != Long.MAX_VALUE) return;
        HandsPose.sample(a.k, a.v, t, F);
        if (a.k == HandKind.SMOKE && "cigarette".equals(F.propR) && t % 5 == 0) {
            Vec3d tip = bodyToWorld(p, F.r, 1f);
            c.world.addParticle(ParticleTypes.SMOKE, tip.x, tip.y + 0.05, tip.z, 0, 0.012, 0);
        }
        if (a.k == HandKind.SMOKE && (t == 18 || t == 19)) {
            Vec3d l = bodyToWorld(p, F.l, 1f);
            c.world.addParticle(ParticleTypes.SMALL_FLAME, l.x, l.y + 0.06, l.z, 0, 0.005, 0);
        }
        if (a.k == HandKind.SMOKE) for (int at : new int[]{80, 170, 260})
            if (t >= at && t < at + 10 && t % 2 == 0) {
                Vec3d m = bodyToWorld(p, HandsPose.add(HandsPose.MOUTH, 0, 0, 0.12f), 1f);
                Vec3d look = Vec3d.fromPolar(0, p.getBodyYaw()).multiply(0.03);
                c.world.addParticle(ParticleTypes.POOF, m.x, m.y, m.z, look.x, 0.008, look.z);
            }
        if (a.k == HandKind.REPAIR && t > 14 && t < 150 && t % 11 == 0) {
            Vec3d r = bodyToWorld(p, F.r, 1f);
            c.world.addParticle(ParticleTypes.CRIT, r.x, r.y, r.z, 0, 0.05, 0);
        }
        if (a.k == HandKind.VEND && "drink".equals(a.v) && t == 72) {
            Vec3d r = bodyToWorld(p, F.r, 1f);
            for (int i = 0; i < 3; i++) c.world.addParticle(ParticleTypes.SPLASH, r.x, r.y + 0.1, r.z, 0, 0.05, 0);
        }
        if (a.k == HandKind.FUEL && t > 40 && t < 180 && t % 7 == 0) {
            Vec3d r = bodyToWorld(p, F.r, 1f);
            c.world.addParticle(ParticleTypes.FALLING_DRIPSTONE_WATER, r.x, r.y - 0.05, r.z, 0, 0, 0);
        }
    }

    /** Свой игрок: подойти к точке и развернуться; сдвинулся сам — отменить. */
    private static void self(MinecraftClient c, ClientPlayerEntity p, Active a, int t) {
        if (!a.k.blocking || a.endTick != Long.MAX_VALUE) return;
        if (t <= a.k.align + 1) {
            double k = Math.min(1, 1.0 / Math.max(1, a.k.align + 1 - t));
            double nx = MathHelper.lerp(k * 0.85, p.getX(), a.anchor.x), nz = MathHelper.lerp(k * 0.85, p.getZ(), a.anchor.z);
            p.setVelocity(0, p.getVelocity().y, 0);
            p.setPosition(nx, p.getY(), nz);
            float yaw = p.getYaw() + MathHelper.wrapDegrees(a.yaw - p.getYaw()) * (float) Math.max(k, 0.35);
            p.setYaw(yaw);
            p.setHeadYaw(yaw);
            p.setBodyYaw(yaw);
            // взгляд на предмет (не насильно: только пока подходит)
            Vec3d eye = p.getEyePos();
            Vec3d d = a.target.subtract(eye);
            float wantPitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z)));
            p.setPitch(MathHelper.lerp(0.35f, p.getPitch(), MathHelper.clamp(wantPitch, -30, 60)));
            return;
        }
        if (a.cancelSent || c.currentScreen != null) return;
        var o = c.options;
        boolean moves = o.forwardKey.isPressed() || o.backKey.isPressed() || o.leftKey.isPressed() || o.rightKey.isPressed() || o.jumpKey.isPressed();
        if (moves) {
            a.cancelSent = true;
            NovogradClient.send("hand_cancel");
        }
        p.bodyYaw = MathHelper.lerpAngleDegrees(0.3f, p.bodyYaw, a.yaw); // корпус к предмету, голова свободна
    }

    // ------------------------------------------------------------------ геометрия
    /** Точка тела (x вправо, y вверх, z вперёд) → мир. */
    static Vec3d bodyToWorld(PlayerEntity p, float[] b, float tickDelta) {
        float yaw = MathHelper.lerpAngleDegrees(tickDelta, p.prevBodyYaw, p.bodyYaw);
        double r = Math.toRadians(yaw);
        Vec3d fwd = new Vec3d(-Math.sin(r), 0, Math.cos(r)), right = new Vec3d(-Math.cos(r), 0, -Math.sin(r));
        Vec3d pos = p.getLerpedPos(tickDelta);
        return pos.add(right.multiply(b[0])).add(0, b[1], 0).add(fwd.multiply(b[2]));
    }

    // ------------------------------------------------------------------ третье лицо
    /** Вызывает PlayerAnim в конце позы (после ходьбы и жестов): присед, руки к точкам, голова. */
    public static void applyThird(PlayerEntityModel<?> m, PlayerEntity p, float age) {
        float td = MinecraftClient.getInstance().getRenderTickCounter().getTickDelta(false);
        HandsPose.Frame f = frame(p, td, F3);
        if (f == null) return;
        if (f.crouch > 0.01f) {
            float dy = 6.5f * f.crouch;
            for (ModelPart part : new ModelPart[]{m.head, m.body, m.rightArm, m.leftArm}) part.pivotY += dy;
            m.rightLeg.pivotY += dy;
            m.leftLeg.pivotY += dy;
            m.rightLeg.pitch = MathHelper.lerp(f.crouch, m.rightLeg.pitch, -1.25f);
            m.leftLeg.pitch = MathHelper.lerp(f.crouch, m.leftLeg.pitch, -0.55f);
            m.leftLeg.pivotZ -= 2.5f * f.crouch;
            m.body.pitch += 0.32f * f.crouch;
            m.rightLeg.pivotZ += 3.5f * f.crouch * 0; // колено вперёд — нога «на колено»
        }
        if (f.wr > 0.01f) reach(m.rightArm, f.r, f.wr);
        if (f.wl > 0.01f) reach(m.leftArm, f.l, f.wl);
        if (f.press > 0) m.rightArm.roll += 0.03f * f.press;
        if (f.lookW > 0.01f) {
            // голова — к точке взгляда (в пределах шеи)
            float[] t = f.look;
            float hx = -t[0] * 16 - m.head.pivotX, hy = (1.5f - t[1]) * 16 - m.head.pivotY + 4, hz = -t[2] * 16 - m.head.pivotZ;
            float pitch = (float) Math.atan2(hy, Math.hypot(hx, hz));
            float yaw = (float) Math.atan2(-hx, -hz);
            m.head.pitch = MathHelper.lerp(f.lookW, m.head.pitch, MathHelper.clamp(pitch, -0.6f, 1.0f));
            m.head.yaw = MathHelper.lerp(f.lookW, m.head.yaw, MathHelper.clamp(yaw, -0.9f, 0.9f));
        }
    }

    /** Рука смотрит из плеча на точку тела b (IK одной кости: у ванильной руки нет локтя). */
    static void reach(ModelPart arm, float[] b, float w) {
        float tx = -b[0] * 16, ty = (1.5f - b[1]) * 16, tz = -b[2] * 16;
        float dx = tx - arm.pivotX, dy = ty - arm.pivotY, dz = tz - arm.pivotZ;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-3f) return;
        dx /= len;
        dy /= len;
        dz /= len;
        float h = (float) Math.sqrt(dx * dx + dz * dz);
        float pitch = -(float) Math.atan2(h, dy);
        float yaw = h < 1e-4f ? 0 : (float) Math.atan2(-dx, -dz);
        arm.pitch = lerpAngle(w, arm.pitch, pitch);
        arm.yaw = lerpAngle(w, arm.yaw, yaw);
        arm.roll = MathHelper.lerp(w, arm.roll, 0);
    }

    static float lerpAngle(float k, float a, float b) {
        return a + MathHelper.wrapDegrees((float) Math.toDegrees(b - a)) * MathHelper.RADIANS_PER_DEGREE * k;
    }

    /** Предметы в руках от третьего лица (PlayerLookFeature, пространство модели). */
    public static void renderThird(MatrixStack m, VertexConsumerProvider buffers, int light, PlayerEntity p, PlayerEntityModel<?> model) {
        float td = MinecraftClient.getInstance().getRenderTickCounter().getTickDelta(false);
        HandsPose.Frame f = frame(p, td, F3);
        if (f == null) return;
        if (!f.propR.isEmpty()) prop3(m, buffers, light, model.rightArm, -1, f.propR, f.tiltR, f.rollR);
        if (!f.propL.isEmpty()) prop3(m, buffers, light, model.leftArm, 1, f.propL, f.tiltL, 0);
    }

    private static void prop3(MatrixStack m, VertexConsumerProvider buffers, int light, ModelPart arm, float side, String id, float tilt, float roll) {
        // кисть: 10 пикс. вдоль руки от плеча (и на 1 пикс. к центру ладони)
        Vector3f hand = new Vector3f(side, 10.5f, 0);
        new Quaternionf().rotationZYX(arm.roll, arm.yaw, arm.pitch).transform(hand);
        m.push();
        m.translate((arm.pivotX + hand.x) / 16f, (arm.pivotY + hand.y) / 16f, (arm.pivotZ + hand.z) / 16f);
        m.scale(-1, -1, -1); // модель → тело (x вправо, y вверх, z вперёд)
        HandsProps.draw(m, buffers, light, id, tilt, roll, 1 / 16f);
        m.pop();
    }

    // ------------------------------------------------------------------ первое лицо
    /** Свои руки и предметы в кадре. true — нарисовали (ванильную руку не рисовать). */
    public static boolean renderFirst(MatrixStack m, VertexConsumerProvider buffers, ClientPlayerEntity p, float tickDelta, int light) {
        Active a = A.get(p.getUuid());
        if (a == null || !NovogradConfig.get().liveAnims) return false;
        HandsPose.Frame f = frame(p, tickDelta, F);
        if (f == null) return false;
        boolean any = f.wr > 0.02f || f.wl > 0.02f || !f.propR.isEmpty() || !f.propL.isEmpty();
        if (!any) return a.k.blocking; // у банкомата ванильная рука не мелькает
        MinecraftClient c = MinecraftClient.getInstance();
        if (!(c.getEntityRenderDispatcher().getRenderer(p) instanceof PlayerEntityRenderer pr)) return false;
        var cam = c.gameRenderer.getCamera();
        Quaternionf inv = new Quaternionf(cam.getRotation()).conjugate();
        Vec3d camPos = cam.getPos();
        // в кадре: кисть в точке раскадровки (или «у пояса», если вес мал), плечо — где у тела
        float crouchDy = -0.42f * f.crouch;
        float[] rr = mixRest(f.r, HandsPose.R_REST, f.wr), ll = mixRest(f.l, HandsPose.L_REST, f.wl);
        rr[1] += crouchDy;
        ll[1] += crouchDy;
        Vector3f fistR = toCam(p, rr, tickDelta, inv, camPos), fistL = toCam(p, ll, tickDelta, inv, camPos);
        Vector3f shR = toCam(p, new float[]{0.30f, 1.32f + crouchDy, -0.08f}, tickDelta, inv, camPos);
        Vector3f shL = toCam(p, new float[]{-0.30f, 1.32f + crouchDy, -0.08f}, tickDelta, inv, camPos);
        Matrix3f bodyToCam = bodyRot(p, tickDelta, inv);
        GunFx.fpArm = true;
        try {
            if (f.wr > 0.02f || !f.propR.isEmpty()) {
                armAt(pr, p, m, fistR, shR, true, buffers, light);
                if (!f.propR.isEmpty()) propFp(m, buffers, light, fistR, bodyToCam, f.propR, f.tiltR, f.rollR);
            }
            if (f.wl > 0.02f || !f.propL.isEmpty()) {
                armAt(pr, p, m, fistL, shL, false, buffers, light);
                if (!f.propL.isEmpty()) propFp(m, buffers, light, fistL, bodyToCam, f.propL, f.tiltL, 0);
            }
        } finally {
            GunFx.fpArm = false;
        }
        return true;
    }

    private static float[] mixRest(float[] target, float[] rest, float w) {
        // вне кадра «отдыхающая» рука опущена ниже, чтобы не висеть у нижнего края экрана
        float[] lowRest = {rest[0], rest[1] - 0.25f, rest[2]};
        return new float[]{MathHelper.lerp(w, lowRest[0], target[0]), MathHelper.lerp(w, lowRest[1], target[1]), MathHelper.lerp(w, lowRest[2], target[2])};
    }

    private static Vector3f toCam(PlayerEntity p, float[] b, float td, Quaternionf inv, Vec3d camPos) {
        Vec3d w = bodyToWorld(p, b, td).subtract(camPos);
        return inv.transform(new Vector3f((float) w.x, (float) w.y, (float) w.z));
    }

    /** Поворот «тело → камера» (для предметов). */
    private static Matrix3f bodyRot(PlayerEntity p, float td, Quaternionf inv) {
        double r = Math.toRadians(MathHelper.lerpAngleDegrees(td, p.prevBodyYaw, p.bodyYaw));
        Vector3f right = inv.transform(new Vector3f((float) -Math.cos(r), 0, (float) -Math.sin(r)));
        Vector3f up = inv.transform(new Vector3f(0, 1, 0));
        Vector3f fwd = inv.transform(new Vector3f((float) -Math.sin(r), 0, (float) Math.cos(r)));
        return new Matrix3f(right, up, fwd); // столбцы
    }

    private static void propFp(MatrixStack m, VertexConsumerProvider buffers, int light, Vector3f fist, Matrix3f bodyToCam, String id, float tilt, float roll) {
        m.push();
        m.translate(fist.x, fist.y, fist.z);
        org.joml.Matrix4f pm = m.peek().getPositionMatrix();
        pm.mul(new org.joml.Matrix4f(bodyToCam));
        m.peek().getNormalMatrix().mul(bodyToCam);
        HandsProps.draw(m, buffers, light, id, tilt, roll, 1 / 16f);
        m.pop();
    }

    /** Рука от первого лица: кулак в точке, рука уходит к плечу (тянется по длине, если нужно). */
    public static void armAt(PlayerEntityRenderer pr, AbstractClientPlayerEntity player, MatrixStack m, Vector3f fist, Vector3f shoulder, boolean rightArm,
                             VertexConsumerProvider buffers, int light) {
        m.push();
        m.translate(fist.x, fist.y, fist.z);
        Vector3f d = new Vector3f(shoulder).sub(fist);
        float len = d.length();
        if (len < 1e-4f) {
            m.pop();
            return;
        }
        d.div(len);
        m.multiply(new Quaternionf().rotationTo(0, -1, 0, d.x, d.y, d.z));
        float sw = 0.62f, sl = Math.max(sw, Math.min(1.6f, len / 0.656f));
        m.scale(sw, sl, sw);
        m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(rightArm ? -70 : 70));
        m.translate(rightArm ? 6f / 16f : -6f / 16f, -10.5f / 16f, 0);
        if (rightArm) pr.renderRightArm(m, buffers, light, player);
        else pr.renderLeftArm(m, buffers, light, player);
        m.pop();
    }
}
