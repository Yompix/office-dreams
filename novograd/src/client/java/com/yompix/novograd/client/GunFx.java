package com.yompix.novograd.client;

import com.yompix.novograd.Novograd;
import com.yompix.novograd.item.GunItem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;

/**
 * «Ощущение» стрельбы: оружие от первого лица (прицеливание, отдача с пружиной, перезарядка со сменой магазина,
 * покачивание при ходьбе и инерция при повороте, спринт — ствол вниз), вспышка у дула, тряска камеры,
 * поза «целится» от третьего лица (двумя руками, пистолет — одной).
 * Все пружины считаются по реальному времени кадра, поэтому плавно при любом FPS.
 */
public final class GunFx {
    private GunFx() {}

    /** Свойства ствола для анимации. muzzle — дуло в пикселях модели (см. tools/gen_guns.py). */
    public enum Kind {
        PISTOL(1.0f, 0.55f, false, 8, 9.1f, 1.6f, 3.2f, new float[]{8, 5.6f, 10.2f}, new float[]{8.4f, 4.6f, 9.4f}),
        SMG(0.42f, 0.22f, true, 8, 9f, -0.5f, 3.0f, new float[]{8, 5.6f, 11.4f}, new float[]{8, 6.8f, 5f}),
        SHOTGUN(1.7f, 1.5f, true, 8, 9.6f, -7f, 5.0f, new float[]{8, 6.4f, 14.6f}, new float[]{8, 7.6f, 0f}),
        RIFLE(0.55f, 0.32f, true, 8, 9.4f, -9.5f, 3.8f, new float[]{8, 6f, 13.6f}, new float[]{8, 7.8f, 2.5f}),
        SNIPER(2.0f, 1.25f, true, 8, 9.4f, -14.5f, 4.6f, new float[]{8, 6.2f, 12.8f}, new float[]{8, 7.4f, 0.5f});

        final float kick, shake, flash;
        final boolean twoHanded;
        final float mx, my, mz;
        /** Где кулак правой руки (рукоять) и левой (цевьё/магазин; у пистолета — поддерживает снизу), пиксели модели. */
        final float[] grip, support;

        Kind(float kick, float shake, boolean twoHanded, float mx, float my, float mz, float flash, float[] grip, float[] support) {
            this.kick = kick;
            this.shake = shake;
            this.twoHanded = twoHanded;
            this.mx = mx;
            this.my = my;
            this.mz = mz;
            this.flash = flash;
            this.grip = grip;
            this.support = support;
        }
    }

    public static Kind kind(Item item) {
        return switch (Registries.ITEM.getId(item).getPath()) {
            case "smg" -> Kind.SMG;
            case "shotgun" -> Kind.SHOTGUN;
            case "rifle" -> Kind.RIFLE;
            case "sniper" -> Kind.SNIPER;
            default -> Kind.PISTOL;
        };
    }

    private static final Identifier FLASH = Novograd.id("textures/misc/muzzle_flash.png");

    // пружины: значение и скорость
    private static float recoil, recoilV, roll, rollV, shake, shakeV;
    private static float ads, sprint, lagYaw, lagPitch, swayX, swayY;
    private static float lastYaw = Float.NaN, lastPitch;
    private static long lastNanos, flashUntil, shotAt;
    private static float flashSpin, flashScale;
    private static Item drawItem;
    private static int drawSlot = -1;
    private static long drawStart;
    private static Kind lastKind = Kind.PISTOL;
    // перезарядка: когда началась и сколько длится (мс)
    private static long reloadStart;
    private static long reloadLen;
    private static Item reloadItem;
    private static final java.util.Random RNG = new java.util.Random();
    /** Только для автотеста: держать вспышку дольше, чтобы попала на скриншот. */
    public static long testFlashNs;


    public static void init() {
        GunItem.clientShot = GunFx::onShot;
    }

    /** Вызывается из GunItem на клиенте при каждом выстреле (и одиночном, и очередью). */
    public static void onShot(GunItem g) {
        Kind k = kind(g);
        lastKind = k;
        float aim = 1f - ads * 0.35f; // в прицеле отдача ниже
        recoilV += 9f * k.kick * aim;
        rollV += (RNG.nextFloat() - 0.5f) * 40f * k.kick;
        shakeV += 14f * k.shake;
        long now = System.nanoTime();
        shotAt = now;
        flashUntil = now + (k == Kind.SMG || k == Kind.RIFLE ? 38_000_000L : 55_000_000L) + testFlashNs;
        flashSpin = RNG.nextFloat() * 360f;
        flashScale = 0.8f + RNG.nextFloat() * 0.45f;
        if (testFlashNs > 0) {
            recoil = 0.05f * recoilV;
            shake = 0.05f * shakeV;
        }
    }

    /** Из ItemCooldownManager.set: длинный «кулдаун» у ствола = перезарядка. */
    public static void onCooldown(ItemCooldownManager mgr, Item item, int duration) {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.player == null || mgr != c.player.getItemCooldownManager() || !(item instanceof GunItem g)) return;
        if (duration > g.spec().cooldown() + 4 && duration >= g.spec().reload() - 2) {
            reloadItem = item;
            reloadStart = System.nanoTime();
            reloadLen = duration * 50_000_000L;
        }
    }

    /** 0..1 — ход перезарядки, или -1. */
    public static float reloadProgress(Item item) {
        if (reloadItem != item || reloadLen <= 0) return -1;
        float p = (System.nanoTime() - reloadStart) / (float) reloadLen;
        return p >= 1 ? -1 : p;
    }

    private static float springStep(float x, float[] v, float k, float damp, float dt) {
        v[0] += (-k * x - damp * v[0]) * dt;
        return x + v[0] * dt;
    }

    private static final float[] TMP = new float[1];

    /** Обновить пружины (раз за кадр, из первого лица и из камеры). */
    private static void update(MinecraftClient c, float tickDelta) {
        long now = System.nanoTime();
        float dt = lastNanos == 0 ? 0.016f : Math.min(0.05f, (now - lastNanos) / 1e9f);
        if (dt <= 0) return;
        lastNanos = now;
        if (testFlashNs > 0 && now < flashUntil) return; // автотест: держим позу выстрела до скриншота
        // мелкими шагами — пружины жёсткие
        int n = Math.max(1, (int) Math.ceil(dt / 0.004f));
        float h = dt / n;
        for (int i = 0; i < n; i++) {
            TMP[0] = recoilV; recoil = springStep(recoil, TMP, 320f, 26f, h); recoilV = TMP[0];
            TMP[0] = rollV; roll = springStep(roll, TMP, 260f, 22f, h); rollV = TMP[0];
            TMP[0] = shakeV; shake = springStep(shake, TMP, 900f, 30f, h); shakeV = TMP[0];
        }
        recoil = MathHelper.clamp(recoil, -0.6f, 2.2f);
        var p = c.player;
        if (p == null) return;
        boolean gun = p.getMainHandStack().getItem() instanceof GunItem;
        boolean aiming = gun && c.currentScreen == null && (c.options.useKey.isPressed() || p.isSneaking()) && reloadProgress(p.getMainHandStack().getItem()) < 0;
        ads += ((aiming ? 1f : 0f) - ads) * (1f - (float) Math.exp(-dt * 14f));
        boolean run = gun && p.isSprinting() && !aiming && System.nanoTime() - shotAt > 400_000_000L;
        sprint += ((run ? 1f : 0f) - sprint) * (1f - (float) Math.exp(-dt * 8f));
        // инерция при повороте: оружие отстаёт от взгляда и догоняет
        float yaw = p.getYaw(tickDelta), pitch = p.getPitch(tickDelta);
        if (Float.isNaN(lastYaw)) {
            lastYaw = yaw;
            lastPitch = pitch;
        }
        float dy = MathHelper.wrapDegrees(yaw - lastYaw), dp = pitch - lastPitch;
        lastYaw = yaw;
        lastPitch = pitch;
        lagYaw = MathHelper.clamp(lagYaw + dy, -18f, 18f);
        lagPitch = MathHelper.clamp(lagPitch + dp, -14f, 14f);
        float back = 1f - (float) Math.exp(-dt * 9f);
        lagYaw -= lagYaw * back;
        lagPitch -= lagPitch * back;
        swayX += (lagYaw - swayX) * (1f - (float) Math.exp(-dt * 16f));
        swayY += (lagPitch - swayY) * (1f - (float) Math.exp(-dt * 16f));
    }

    private static float smooth(float a, float b, float x) {
        float t = MathHelper.clamp((x - a) / (b - a), 0, 1);
        return t * t * (3 - 2 * t);
    }

    /** Вместо ванильного renderFirstPersonItem для стволов. */
    public static void renderFirstPerson(HeldItemRenderer r, AbstractClientPlayerEntity player, float tickDelta, Hand hand, ItemStack item,
                                         float equipProgress, MatrixStack m, VertexConsumerProvider buffers, int light) {
        MinecraftClient c = MinecraftClient.getInstance();
        boolean main = hand == Hand.MAIN_HAND;
        Arm arm = main ? player.getMainArm() : player.getMainArm().getOpposite();
        boolean right = arm == Arm.RIGHT;
        float s = right ? 1 : -1;
        if (main) update(c, tickDelta);
        // в оптике снайперки ружьё не мешает
        if (main && ArsenalClient.fovScale(tickDelta) < 0.6) return;
        Kind k = kind(item.getItem());
        float a = main ? ads : 0, sp = main ? sprint : 0, rc = main ? recoil : 0;

        // «достать оружие»: своя анимация при смене ствола (ванильный equipProgress дёргается на каждом выстреле)
        float draw = 0;
        if (main) {
            int slot = player.getInventory().selectedSlot;
            if (item.getItem() != drawItem || slot != drawSlot) {
                drawItem = item.getItem();
                drawSlot = slot;
                drawStart = System.nanoTime();
            }
            float dp = MathHelper.clamp((System.nanoTime() - drawStart) / 3.2e8f, 0, 1);
            draw = (1 - dp) * (1 - dp);
            equipProgress = 0;
        }

        m.push();
        // база (как у ванили) → к центру при прицеливании
        float x = MathHelper.lerp(a, 0.56f, k == Kind.PISTOL ? 0.16f : 0.2f) * s;
        float y = MathHelper.lerp(a, -0.52f, -0.43f) + equipProgress * -0.6f;
        float z = MathHelper.lerp(a, -0.72f, -0.62f);
        m.translate(x, y - 0.45f * draw, z);
        if (draw > 0) {
            m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-35f * draw));
            m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-20f * draw * s));
        }

        // ходьба: «восьмёрка» (мягче в прицеле)
        float stride = MathHelper.lerp(tickDelta, player.prevStrideDistance, player.strideDistance);
        float ph = -(player.horizontalSpeed + (player.horizontalSpeed - player.prevHorizontalSpeed) * tickDelta) * (float) Math.PI;
        float bobK = (1f - a * 0.75f) * (1f + sp * 0.8f);
        m.translate(MathHelper.sin(ph) * stride * 0.06f * bobK * s, -Math.abs(MathHelper.cos(ph)) * stride * 0.07f * bobK, 0);
        // дыхание стоя
        float t = (player.age + tickDelta) * 0.06f;
        m.translate(MathHelper.sin(t) * 0.004f * (1 - a * 0.7f), MathHelper.sin(t * 1.7f) * 0.005f * (1 - a * 0.7f), 0);
        if (main) {
            // инерция поворота: ствол отстаёт
            m.translate(swayX * 0.0035f, -swayY * 0.003f, 0);
            m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(swayX * 0.35f));
            m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(swayY * 0.3f));
            m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-swayX * 0.25f));
        }
        // спринт: оружие опущено и повёрнуто
        if (sp > 0.01f) {
            m.translate(-0.08f * sp * s, -0.06f * sp, 0.05f * sp);
            m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(38f * sp * s));
            m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-16f * sp));
            m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(12f * sp * s));
        }
        // перезарядка: наклон вниз-вбок, «вынули магазин — вставили» и рывок затвора
        float rp = main ? reloadProgress(item.getItem()) : -1;
        if (rp >= 0) {
            float in = smooth(0f, 0.18f, rp) * (1 - smooth(0.82f, 1f, rp));
            m.translate(0.02f * in * s, -0.06f * in, 0.03f * in);
            m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-22f * in));
            m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(28f * in * s));
            m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-12f * in * s));
            if (k == Kind.SHOTGUN) {
                // патроны по одному: 4 толчка
                float pulse = MathHelper.clamp((rp - 0.2f) / 0.55f, 0, 1);
                float bump = pulse > 0 && pulse < 1 ? (float) Math.pow(Math.abs(MathHelper.sin(pulse * 4 * (float) Math.PI)), 3) : 0;
                m.translate(0, 0.02f * bump, -0.015f * bump);
                m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(4f * bump));
            } else {
                // магазин вниз (оружие чуть вверх), потом удар ладонью снизу
                float out = smooth(0.22f, 0.36f, rp) * (1 - smooth(0.5f, 0.62f, rp));
                float slap = rp > 0.62f && rp < 0.74f ? MathHelper.sin((rp - 0.62f) / 0.12f * (float) Math.PI) : 0;
                m.translate(0, 0.03f * out + 0.05f * slap, 0);
                m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(6f * out + 9f * slap));
            }
            // затвор/цевьё в конце
            float rack = rp > 0.78f && rp < 0.9f ? MathHelper.sin((rp - 0.78f) / 0.12f * (float) Math.PI) : 0;
            m.translate(0, 0, 0.05f * rack);
        }
        // отдача: назад и стволом вверх, с лёгким креном; от пружины — быстрый возврат
        if (rc != 0) {
            m.translate(0, 0.02f * rc, 0.11f * rc * (k == Kind.PISTOL ? 0.8f : 1f));
            m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(rc * (k == Kind.PISTOL ? 11f : 7f)));
            m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(roll * 0.08f * s));
        }

        ModelTransformationMode mode = right ? ModelTransformationMode.FIRST_PERSON_RIGHT_HAND : ModelTransformationMode.FIRST_PERSON_LEFT_HAND;
        if (main && !player.isInvisible()) arms(c, player, item, k, mode, !right, rp, m, buffers, light);
        r.renderItem(player, item, mode, !right, m, buffers, light);
        // вспышка у дула — 1–2 кадра после выстрела
        if (main && System.nanoTime() < flashUntil && kind(item.getItem()) == lastKind)
            flash(c, player, item, mode, !right, m, buffers, k);
        m.pop();
    }

    /** Руки тоньше ванильных: оружие в них не выглядит игрушечным. */
    private static final float ARM_SCALE = 0.68f;

    /** Сейчас рисуем руку от первого лица — поза «целится» не нужна. */
    public static boolean fpArm;

    /**
     * Руки держат оружие: кулак — на рукояти (и на цевье у длинных стволов), плечо — за краем экрана.
     * Считаем в пространстве модели оружия, поэтому руки двигаются вместе с отдачей и перезарядкой.
     */
    private static void arms(MinecraftClient c, AbstractClientPlayerEntity player, ItemStack item, Kind k, ModelTransformationMode mode, boolean left,
                             float reload, MatrixStack m, VertexConsumerProvider buffers, int light) {
        if (!(c.getEntityRenderDispatcher().getRenderer(player) instanceof net.minecraft.client.render.entity.PlayerEntityRenderer pr)) return;
        var model = c.getItemRenderer().getModel(item, player.getWorld(), player, 0);
        var tf = model.getTransformation().getTransformation(mode);
        float s = left ? -1 : 1;
        // плечи в пространстве руки-камеры
        org.joml.Vector3f mainShoulder = new org.joml.Vector3f(0.62f * s, -0.95f, 0.15f), offShoulder = new org.joml.Vector3f(-0.42f * s, -0.95f, 0.1f);
        // левая рука при перезарядке уходит вниз, к «магазину»
        float[] sup = k.support.clone();
        if (reload >= 0 && k != Kind.SHOTGUN) {
            float away = smooth(0.18f, 0.32f, reload) * (1 - smooth(0.5f, 0.64f, reload));
            sup[1] -= 5f * away;
            sup[2] += 2f * away;
        }
        fpArm = true;
        try {
            arm(pr, player, m, tf, left, k.grip, mainShoulder, true, buffers, light);
            if (k.twoHanded || k == Kind.PISTOL) arm(pr, player, m, tf, left, sup, offShoulder, false, buffers, light);
        } finally {
            fpArm = false;
        }
    }

    private static void arm(net.minecraft.client.render.entity.PlayerEntityRenderer pr, AbstractClientPlayerEntity player, MatrixStack m,
                            net.minecraft.client.render.model.json.Transformation tf, boolean left, float[] at, org.joml.Vector3f shoulder, boolean mainArm,
                            VertexConsumerProvider buffers, int light) {
        m.push();
        tf.apply(left, m);
        m.translate(-0.5f, -0.5f, -0.5f);
        float gx = at[0] / 16f, gy = at[1] / 16f, gz = at[2] / 16f;
        if (left) gx = 1 - gx; // зеркальная модель для левой руки
        m.translate(gx, gy, gz);
        // где кулак на экране и куда смотреть, чтобы рука уходила к плечу
        Matrix4f pos = m.peek().getPositionMatrix();
        org.joml.Vector3f fist = pos.transformPosition(new org.joml.Vector3f());
        org.joml.Vector3f toShoulder = new org.joml.Vector3f(shoulder).sub(fist);
        Matrix4f inv = new Matrix4f(pos).invert();
        org.joml.Vector3f local = inv.transformDirection(toShoulder).normalize();
        // ось руки модели: от кулака к плечу — это -Y
        org.joml.Quaternionf q = new org.joml.Quaternionf().rotationTo(0, -1, 0, local.x, local.y, local.z);
        m.multiply(q);
        float sc = ARM_SCALE / Math.max(0.05f, tf.scale.x());
        m.scale(sc, sc, sc);
        boolean rightArm = mainArm != left; // основная рука правая, если не левша
        // кулак в модели руки: низ кубоида (y≈10.5), центр по x
        m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(rightArm ? -70 : 70));
        m.translate(rightArm ? 6f / 16f : -6f / 16f, -10.5f / 16f, 0);
        if (rightArm) pr.renderRightArm(m, buffers, light, player);
        else pr.renderLeftArm(m, buffers, light, player);
        m.pop();
    }

    private static void flash(MinecraftClient c, LivingEntity player, ItemStack item, ModelTransformationMode mode, boolean left, MatrixStack m,
                              VertexConsumerProvider buffers, Kind k) {
        var model = c.getItemRenderer().getModel(item, player.getWorld(), player, 0);
        m.push();
        model.getTransformation().getTransformation(mode).apply(left, m);
        m.translate(k.mx / 16f - 0.5f, k.my / 16f - 0.5f, k.mz / 16f - 0.5f);
        float size = k.flash * 1.35f / 16f * flashScale;
        VertexConsumer vc = buffers.getBuffer(RenderLayer.getEyes(FLASH));
        // звезда навстречу камере
        m.push();
        m.translate(0, 0, -size * 0.3f);
        m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(flashSpin));
        quad(vc, m, size, 0);
        m.pop();
        // и два «языка» вдоль ствола
        m.push();
        m.translate(0, 0, -size * 0.9f);
        m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90));
        quad(vc, m, size * 0.75f, 1);
        m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(90));
        quad(vc, m, size * 0.75f, 1);
        m.pop();
        m.pop();
    }

    private static void quad(VertexConsumer vc, MatrixStack m, float r, int stretch) {
        MatrixStack.Entry e = m.peek();
        Matrix4f mat = e.getPositionMatrix();
        float ry = stretch == 1 ? r * 1.6f : r;
        int light = 0xF000F0;
        // двусторонний: обе стороны
        for (int side = 0; side < 2; side++) {
            float[][] v = side == 0 ? new float[][]{{-r, -ry, 0, 0, 1}, {r, -ry, 0, 1, 1}, {r, ry, 0, 1, 0}, {-r, ry, 0, 0, 0}}
                    : new float[][]{{-r, ry, 0, 0, 0}, {r, ry, 0, 1, 0}, {r, -ry, 0, 1, 1}, {-r, -ry, 0, 0, 1}};
            for (float[] p : v)
                vc.vertex(mat, p[0], p[1], p[2]).color(255, 255, 255, 255).texture(p[3], p[4]).overlay(OverlayTexture.DEFAULT_UV).light(light)
                        .normal(e, 0, 0, side == 0 ? 1 : -1);
        }
    }

    /** Тряска камеры от выстрела (из GameRenderer.tiltViewWhenHurt). */
    public static void shake(MatrixStack m, float tickDelta) {
        MinecraftClient c = MinecraftClient.getInstance();
        update(c, tickDelta);
        if (Math.abs(shake) < 0.002f || c.options.getPerspective() != Perspective.FIRST_PERSON) return;
        m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(shake * 0.9f));
        m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-Math.abs(shake) * 0.5f));
    }

    /** Поза «целится» от третьего лица (после ванильной setAngles). */
    public static void pose(BipedEntityModel<?> model, LivingEntity e) {
        ItemStack st = e.getMainHandStack();
        if (fpArm || !(st.getItem() instanceof GunItem)) return;
        Kind k = kind(st.getItem());
        boolean right = e.getMainArm() == Arm.RIGHT;
        ModelPart main = right ? model.rightArm : model.leftArm, off = right ? model.leftArm : model.rightArm;
        float s = right ? 1 : -1;
        float hp = model.head.pitch, hy = model.head.yaw;
        MinecraftClient c = MinecraftClient.getInstance();
        float kick = e == c.player ? recoil * 0.25f : 0;
        boolean low = e.isSprinting() && !(e == c.player && System.nanoTime() - shotAt < 400_000_000L);
        float pitch = low ? -0.75f : -MathHelper.HALF_PI + MathHelper.clamp(hp, -1.2f, 1.2f);
        float rp = e == c.player ? reloadProgress(st.getItem()) : -1;
        if (rp >= 0) pitch = MathHelper.lerp(smooth(0, 0.18f, rp) * (1 - smooth(0.82f, 1, rp)), pitch, -0.6f);
        main.pitch = pitch - kick;
        main.yaw = hy - 0.1f * s + (low ? -0.3f * s : 0);
        main.roll = 0;
        if (k.twoHanded) {
            off.pitch = pitch - kick + (low ? 0.15f : 0);
            off.yaw = hy + 0.5f * s + (low ? -0.1f * s : 0);
            off.roll = 0;
        }
    }
}
