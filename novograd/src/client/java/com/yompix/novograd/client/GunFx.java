package com.yompix.novograd.client;

import com.yompix.novograd.Novograd;
import com.yompix.novograd.client.gun.GunAnim;
import com.yompix.novograd.client.gun.GunMeta;
import com.yompix.novograd.client.gun.GunWorldFx;
import com.yompix.novograd.gta.Arsenal;
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
import net.minecraft.client.render.model.json.Transformation;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * «Ощущение» стрельбы, как в шутер-модах (TaCZ, Point Blank): прицеливание по ПКМ (мушка в центре экрана, зум),
 * отдача — настоящий подброс камеры с возвратом + отдача модели (назад, вверх, крен), инерция и покачивание,
 * дыхание, спринт (ствол опущен), прыжок/приземление, доставание, осмотр (Y), перезарядка с анимацией костей,
 * вспышка со «светом», гильзы, дымок. Поза от третьего лица: от бедра и в прицеле.
 * Все пружины считаются по реальному времени кадра (плавно при любом FPS).
 */
public final class GunFx {
    private GunFx() {}

    /** Свойства ствола для анимации: отдача модели (блоки назад, градусы вверх), тряска, расстояние мушки от глаза в прицеле, зум. */
    public enum Kind {
        PISTOL(0.07f, 10f, 0.6f, true, -0.34f, 0.86f, 4f, 0.05f),
        SMG(0.035f, 2.6f, 0.25f, true, -0.24f, 0.82f, 6f, 0.035f),
        SHOTGUN(0.13f, 9f, 1.4f, true, -0.22f, 0.86f, 12f, 0.16f),
        RIFLE(0.05f, 3.4f, 0.35f, true, -0.22f, 0.76f, 5f, 0.04f),
        SNIPER(0.14f, 8f, 1.2f, true, -0.12f, 0.2f, 10f, 0.12f);

        final float kickZ, rise, shake, adsZ, zoom, crossBase, bloomAdd;
        final boolean twoHanded;

        /** Множитель поля зрения в прицеле. */
        public float zoomTarget() {
            return zoom;
        }

        Kind(float kickZ, float rise, float shake, boolean twoHanded, float adsZ, float zoom, float crossBase, float bloomAdd) {
            this.kickZ = kickZ;
            this.rise = rise;
            this.shake = shake;
            this.twoHanded = twoHanded;
            this.adsZ = adsZ;
            this.zoom = zoom;
            this.crossBase = crossBase;
            this.bloomAdd = bloomAdd;
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

    public static String id(Item item) {
        return Registries.ITEM.getId(item).getPath();
    }

    private static final Identifier FLASH = Novograd.id("textures/misc/muzzle_flash.png");
    private static final Identifier FLASH_SIDE = Novograd.id("textures/misc/muzzle_side.png");
    private static final java.util.Random RNG = new java.util.Random();

    // пружины модели: отдача назад (z), подброс (p), крен, тряска камеры
    private static float kz, kzV, kp, kpV, roll, rollV, shake, shakeV, side, sideV;
    private static float ads, sprint, lagYaw, lagPitch, swayX, swayY, land, landV, air;
    /** Прицел зажат (ставит ArsenalClient). */
    public static boolean adsTarget;
    private static float lastYaw = Float.NaN, lastPitch;
    private static boolean wasOnGround = true;
    private static long lastNanos, flashUntil, shotAt;
    private static Item shotItem;
    private static float flashSpin, flashScale;
    private static int flashVariant;
    private static Item drawItem;
    private static int drawSlot = -1;
    private static long drawStart;
    // камера: сколько ещё поднять (градусы) и сколько вернуть потом
    private static float camPitch, camYaw, camBackPitch, camBackYaw;
    private static float bloom;
    private static int burst;
    // перезарядка
    private static long reloadStart, reloadLen;
    private static Item reloadItem;
    private static boolean reloadEmpty;
    private static int reloadShells;
    private static GunAnim.Cue[] cues = new GunAnim.Cue[0];
    private static float cueP;
    // осмотр
    private static long inspectStart;
    private static Item inspectItem;
    private static float inspectCueP;
    // отложенный выброс гильзы (помпа, затвор снайперки)
    private static long ejectAt;
    private static int ejectType = -1;
    /** Где на самом деле нарисовано дуло / окно выброса / магазин (мир), обновляется каждый кадр от первого лица. */
    public static Vec3d muzzleWorld, ejectWorld, magWorld;
    private static Vec3d ejectDirWorld = Vec3d.ZERO;
    /** Патронов в магазине по мнению клиента (с учётом ещё не подтверждённых выстрелов). */
    public static int predictedMag = 1;
    /** Только для автотеста: держать вспышку/позу выстрела дольше, чтобы попала на скриншот. */
    public static long testFlashNs;
    /** Только для автотеста: заморозить анимации (перезарядка/осмотр) на этой доле. */
    public static float testFreezeP = -1;
    private static final GunAnim.In IN = new GunAnim.In();

    public static void init() {}

    // ------------------------------------------------------------------ события
    /** Свой выстрел (ArsenalClient, сразу при нажатии — без ожидания сервера). */
    public static void onShot(GunItem g) {
        Kind k = kind(g);
        if (testFlashNs > 0) Novograd.LOG.info("[guns] shot {}", id(g));
        MinecraftClient c = MinecraftClient.getInstance();
        float aim = 1f - ads * 0.4f;
        kzV += 9f * k.kickZ / 0.05f * aim;
        kpV += 9f * k.rise * aim * (0.85f + RNG.nextFloat() * 0.3f);
        rollV += (RNG.nextFloat() - 0.35f) * 60f * k.kickZ / 0.05f * aim;
        sideV += (RNG.nextFloat() - 0.5f) * 1.2f * aim;
        shakeV += 14f * k.shake;
        // подброс камеры: вверх и чуть вбок, потом часть возвращается
        float r = g.spec().recoil() * (ads > 0.5f ? 0.7f : 1f) * (0.9f + RNG.nextFloat() * 0.2f);
        camPitch += r;
        camYaw += (RNG.nextFloat() - 0.5f) * r * 0.55f;
        bloom = Math.min(1f, bloom + k.bloomAdd * (ads > 0.5f ? 0.5f : 1f) * 3);
        burst++;
        long now = System.nanoTime();
        shotAt = now;
        shotItem = g;
        inspectItem = null;
        flashUntil = now + (k == Kind.SMG || k == Kind.RIFLE ? 40_000_000L : 60_000_000L) + testFlashNs;
        flashSpin = RNG.nextFloat() * 360f;
        flashScale = 0.8f + RNG.nextFloat() * 0.45f;
        flashVariant = RNG.nextInt(4);
        // гильза: сразу (пистолет, ПП, «АК») или когда передёрнут помпу / затвор
        if (k == Kind.SHOTGUN) schedule(now + (long) ((GunAnim.PUMP_AT + 0.06f) * 1e9), 2);
        else if (k == Kind.SNIPER) schedule(now + (long) ((GunAnim.SNIPER_BOLT_AT + 0.62f * 0.28f) * 1e9), 1);
        else ejectCasing(k == Kind.RIFLE ? 1 : 0);
        if (c.world != null && muzzleWorld != null) GunWorldFx.smoke(muzzleWorld, 1f);
        if (testFlashNs > 0) {
            kz = 0.06f * kzV;
            kp = 0.06f * kpV;
            shake = 0.05f * shakeV;
        }
    }

    private static void schedule(long at, int type) {
        ejectAt = at;
        ejectType = type;
    }

    private static void ejectCasing(int type) {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.player == null) return;
        if (c.options.getPerspective().isFirstPerson() && ejectWorld != null) {
            Vec3d look = c.player.getRotationVec(1f);
            Vec3d right = look.crossProduct(new Vec3d(0, 1, 0)).normalize();
            Vec3d v = right.multiply(0.11 + RNG.nextFloat() * 0.05).add(0, 0.09 + RNG.nextFloat() * 0.05, 0).add(look.multiply(-0.015)).add(c.player.getVelocity());
            GunWorldFx.spawnBit(ejectWorld, v, type);
        } else {
            Vec3d eye = c.player.getEyePos();
            Vec3d look = c.player.getRotationVec(1f);
            Vec3d right = look.crossProduct(new Vec3d(0, 1, 0)).normalize();
            Vec3d port = GunWorldFx.thirdPersonMuzzle(c.player, eye).subtract(look.multiply(0.4));
            GunWorldFx.spawnBit(port, right.multiply(0.12).add(0, 0.1, 0), type);
        }
    }

    /** Из ItemCooldownManager.set: длинный «кулдаун» у ствола = перезарядка (длину назначил сервер). */
    public static void onCooldown(ItemCooldownManager mgr, Item item, int duration) {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.player == null || mgr != c.player.getItemCooldownManager() || !(item instanceof GunItem g)) return;
        if (duration <= g.spec().cooldown() + 4) return;
        reloadItem = item;
        reloadStart = System.nanoTime();
        reloadLen = duration * 50_000_000L;
        inspectItem = null;
        // по длине понимаем, какая перезарядка: с пустого или нет, сколько патронов дробовику (см. GunItem.reloadTicks)
        if (g.spec().pellets() > 1) {
            reloadEmpty = (duration - 8) % 7 != 0;
            reloadShells = Math.max(0, (duration - 8 - (reloadEmpty ? 10 : 0)) / 7);
        } else {
            reloadEmpty = duration >= g.spec().reload() - 1;
            reloadShells = 0;
        }
        cues = GunAnim.reloadCues(id(item), reloadEmpty, reloadShells, duration / 20f);
        cueP = 0;
    }

    /** 0..1 — ход перезарядки, или -1. */
    public static float reloadProgress(Item item) {
        if (reloadItem != item || reloadLen <= 0) return -1;
        float p = (System.nanoTime() - reloadStart) / (float) reloadLen;
        if (p >= 1) return -1;
        return testFreezeP >= 0 ? testFreezeP : p;
    }

    /** Для автотеста: состояние пружин. */
    public static String debug() {
        return String.format("ads=%.2f target=%s sway=%.2f/%.2f kz=%.3f kp=%.2f side=%.2f roll=%.2f land=%.2f air=%.2f sprint=%.2f pose=%s",
                ads, adsTarget, swayX, swayY, kz, kp, side, roll, land, air, sprint, GunAnim.POSE.gun);
    }

    /** Осмотр (клавиша Y). */
    public static void inspect() {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.player == null || !(c.player.getMainHandStack().getItem() instanceof GunItem g) || reloadProgress(g) >= 0 || ads > 0.3f) return;
        inspectItem = g;
        inspectStart = System.nanoTime();
        inspectCueP = 0;
    }

    public static float inspectProgress(Item item) {
        if (inspectItem != item) return -1;
        float p = (System.nanoTime() - inspectStart) / 1e9f / GunAnim.INSPECT_SEC;
        if (p >= 1) {
            inspectItem = null;
            return -1;
        }
        return testFreezeP >= 0 ? testFreezeP : p;
    }

    public static float adsAmount() {
        return ads;
    }

    /** Множитель скорости ходьбы (в прицеле медленнее). */
    public static float adsMoveFactor() {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.player == null || !(c.player.getMainHandStack().getItem() instanceof GunItem)) return 1f;
        return 1f - 0.45f * ads;
    }

    /** Разлёт перекрестия (пиксели GUI). */
    public static float crosshairGap() {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.player == null) return 6;
        Kind k = kind(c.player.getMainHandStack().getItem());
        double v = c.player.getVelocity().horizontalLength();
        float move = (float) Math.min(1, v / 0.2) * 7 + (c.player.isOnGround() ? 0 : 6) + sprint * 6;
        return k.crossBase + move + bloom * 14 - (c.player.isSneaking() ? 1.5f : 0);
    }

    // ------------------------------------------------------------------ каждый кадр
    private static float springStep(float x, float[] v, float k, float damp, float dt) {
        v[0] += (-k * x - damp * v[0]) * dt;
        return x + v[0] * dt;
    }

    private static final float[] TMP = new float[1];

    /** Раз за кадр (из GameRenderer.renderWorld): пружины, камера, метки звука, анимация костей. */
    public static void frame(float tickDelta) {
        MinecraftClient c = MinecraftClient.getInstance();
        long now = System.nanoTime();
        float dt = lastNanos == 0 ? 0.016f : Math.min(0.05f, (now - lastNanos) / 1e9f);
        if (dt <= 0) return;
        lastNanos = now;
        var p = c.player;
        boolean hold = testFlashNs > 0 && now < flashUntil;
        if (!hold) {
            int n = Math.max(1, (int) Math.ceil(dt / 0.004f));
            float h = dt / n;
            for (int i = 0; i < n; i++) {
                TMP[0] = kzV; kz = springStep(kz, TMP, 300f, 24f, h); kzV = TMP[0];
                TMP[0] = kpV; kp = springStep(kp, TMP, 170f, 19f, h); kpV = TMP[0];
                TMP[0] = rollV; roll = springStep(roll, TMP, 200f, 18f, h); rollV = TMP[0];
                TMP[0] = sideV; side = springStep(side, TMP, 220f, 20f, h); sideV = TMP[0];
                TMP[0] = shakeV; shake = springStep(shake, TMP, 900f, 30f, h); shakeV = TMP[0];
                TMP[0] = landV; land = springStep(land, TMP, 140f, 13f, h); landV = TMP[0];
            }
        }
        kz = MathHelper.clamp(kz, -0.1f, 0.35f);
        kp = MathHelper.clamp(kp, -6f, 30f);
        bloom = Math.max(0, bloom - dt * 1.6f);
        if (now - shotAt > 350_000_000L) burst = 0;
        if (p == null) return;
        ItemStack stack = p.getMainHandStack();
        boolean gun = stack.getItem() instanceof GunItem;
        boolean reloading = gun && reloadProgress(stack.getItem()) >= 0;
        boolean aiming = gun && adsTarget && !reloading && c.currentScreen == null;
        if (aiming) inspectItem = null;
        ads += ((aiming ? 1f : 0f) - ads) * (1f - (float) Math.exp(-dt * (aiming ? 13f : 16f)));
        boolean run = gun && p.isSprinting() && !aiming && now - shotAt > 400_000_000L && !reloading;
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
        // прыжок и приземление
        boolean ground = p.isOnGround();
        if (ground && !wasOnGround) landV -= (float) Math.min(1.2, Math.abs(p.getVelocity().y) * 6 + 0.3);
        wasOnGround = ground;
        air += ((ground || p.hasVehicle() ? 0f : MathHelper.clamp((float) p.getVelocity().y * 3f, -1f, 1f)) - air) * (1f - (float) Math.exp(-dt * 10f));
        // камера: подброс отдачи (быстро) и возврат (медленнее, когда перестал стрелять)
        if (!c.isPaused()) cameraRecoil(p, dt, now);
        // отложенная гильза
        if (ejectType >= 0 && now >= ejectAt) {
            if (gun && stack.getItem() == shotItem) ejectCasing(ejectType);
            ejectType = -1;
        }
        // дымок после очереди
        float sinceShot = (now - shotAt) / 1e9f;
        if (gun && sinceShot > 0.08f && sinceShot < 1.6f && muzzleWorld != null && c.options.getPerspective().isFirstPerson() && !c.isPaused())
            GunWorldFx.smoke(muzzleWorld, dt * 22 * (1.6f - sinceShot) * Math.min(1f, 0.25f + bloom));
        if (!gun) {
            GunAnim.POSE.gun = null;
            return;
        }
        Item item = stack.getItem();
        GunItem g = (GunItem) item;
        // метки звука перезарядки и осмотра
        float rp = reloadProgress(item);
        if (rp >= 0 && testFreezeP < 0) {
            for (GunAnim.Cue cue : cues)
                if (cue.at() > cueP && cue.at() <= rp) cue(cue.sound(), g);
            cueP = rp;
        }
        float ip = reloadProgress(item) < 0 ? inspectProgress(item) : -1;
        if (ip >= 0 && testFreezeP < 0) {
            for (GunAnim.Cue cue : GunAnim.inspectCues(id(item)))
                if (cue.at() > inspectCueP && cue.at() <= ip) cue(cue.sound(), g);
            inspectCueP = ip;
        }
        // поза костей
        GunMeta meta = GunMeta.get(id(item));
        IN.gun = id(item);
        IN.shotT = shotItem == item ? (hold ? 0.025f : sinceShot) : 99;
        IN.locked = id(item).equals("pistol") && predictedMag <= 0 && !p.isCreative();
        IN.reloadP = rp;
        IN.reloadSec = reloadLen / 1e9f;
        IN.reloadEmpty = reloadEmpty;
        IN.shells = reloadShells;
        IN.inspectP = ip;
        IN.trigger = c.options.attackKey.isPressed() && c.currentScreen == null ? 1 : 0;
        GunAnim.evaluate(meta, IN);
    }

    private static void cameraRecoil(net.minecraft.client.network.ClientPlayerEntity p, float dt, long now) {
        if (Math.abs(camPitch) > 0.001f || Math.abs(camYaw) > 0.001f) {
            float k = 1f - (float) Math.exp(-dt * 38f);
            float dP = camPitch * k, dY = camYaw * k;
            camPitch -= dP;
            camYaw -= dY;
            turn(p, -dP, dY);
            camBackPitch += dP * 0.6f;
            camBackYaw += dY * 0.6f;
        }
        if (now - shotAt > 110_000_000L && (Math.abs(camBackPitch) > 0.001f || Math.abs(camBackYaw) > 0.001f)) {
            float k = 1f - (float) Math.exp(-dt * 6.5f);
            float bP = camBackPitch * k, bY = camBackYaw * k;
            camBackPitch -= bP;
            camBackYaw -= bY;
            turn(p, bP, -bY);
        }
    }

    private static void turn(net.minecraft.client.network.ClientPlayerEntity p, float dPitch, float dYaw) {
        float np = MathHelper.clamp(p.getPitch() + dPitch, -90, 90);
        float realDp = np - p.getPitch();
        p.setPitch(np);
        p.prevPitch += realDp;
        p.setYaw(p.getYaw() + dYaw);
        p.prevYaw += dYaw;
    }

    private static void cue(String s, GunItem g) {
        MinecraftClient c = MinecraftClient.getInstance();
        if (s.equals("drop")) {
            // пустой магазин падает на землю
            if (c.options.getPerspective().isFirstPerson() && magWorld != null) {
                Vec3d look = c.player.getRotationVec(1f);
                GunWorldFx.spawnBit(magWorld, new Vec3d(0, -0.05, 0).add(look.multiply(0.02)).add(c.player.getVelocity()), id(g).equals("rifle") ? 4 : 3);
            }
            return;
        }
        SoundEvent ev = switch (s) {
            case "mag_out" -> Arsenal.MAG_OUT;
            case "mag_in" -> Arsenal.MAG_IN;
            case "slide_back" -> Arsenal.SLIDE_BACK;
            case "slide_release" -> Arsenal.SLIDE_RELEASE;
            case "bolt_back" -> Arsenal.BOLT_BACK;
            case "bolt_fwd" -> Arsenal.BOLT_FWD;
            case "shell_in" -> Arsenal.SHELL_IN;
            case "pump" -> Arsenal.PUMP;
            default -> Arsenal.HANDLING;
        };
        c.getSoundManager().play(PositionedSoundInstance.master(ev, 0.95f + RNG.nextFloat() * 0.1f, 0.8f));
    }

    private static float smooth(float a, float b, float x) {
        float t = MathHelper.clamp((x - a) / (b - a), 0, 1);
        return t * t * (3 - 2 * t);
    }

    // ------------------------------------------------------------------ от первого лица
    /** Вместо ванильного renderFirstPersonItem для стволов. */
    public static void renderFirstPerson(HeldItemRenderer r, AbstractClientPlayerEntity player, float tickDelta, Hand hand, ItemStack item,
                                         float equipProgress, MatrixStack m, VertexConsumerProvider buffers, int light) {
        MinecraftClient c = MinecraftClient.getInstance();
        boolean main = hand == Hand.MAIN_HAND;
        Arm arm = main ? player.getMainArm() : player.getMainArm().getOpposite();
        boolean right = arm == Arm.RIGHT;
        float s = right ? 1 : -1;
        // в оптике снайперки ружьё не мешает
        if (main && ArsenalClient.fovScale(tickDelta) < 0.6) return;
        Kind k = kind(item.getItem());
        GunMeta meta = GunMeta.get(id(item.getItem()));
        boolean posed = main && GunAnim.POSE.gun != null && GunAnim.POSE.gun.equals(id(item.getItem()));
        GunAnim.Pose pose = GunAnim.POSE;
        float a = main ? ads : 0, sp = main ? sprint : 0;

        // «достать оружие»: своя анимация при смене ствола (ванильный equipProgress дёргается на каждом выстреле)
        float draw = 0;
        if (main) {
            int slot = player.getInventory().selectedSlot;
            if (item.getItem() != drawItem || slot != drawSlot) {
                drawItem = item.getItem();
                drawSlot = slot;
                drawStart = System.nanoTime();
                if (c.player != null) c.getSoundManager().play(PositionedSoundInstance.master(Arsenal.HANDLING, 1.1f, 0.5f));
            }
            float dp = MathHelper.clamp((System.nanoTime() - drawStart) / 3.6e8f, 0, 1);
            draw = (1 - dp) * (1 - dp) * (1 - dp);
            equipProgress = 0;
        }
        float sc = meta.fpScale;
        // прицел: мушка/целик — в центр экрана
        float dX = sc * (meta.sight[0] - meta.grip[0]) / 16f, dY = sc * (meta.sight[1] - meta.grip[1]) / 16f, dZ = sc * (meta.sight[2] - meta.grip[2]) / 16f;
        float hipX = 0.34f * s, hipY = -0.34f, hipZ = -0.58f;
        float x = MathHelper.lerp(a, hipX, -dX * s), y = MathHelper.lerp(a, hipY, -dY), z = MathHelper.lerp(a, hipZ, k.adsZ - dZ);

        m.push();
        m.translate(x, y - 0.5f * draw + equipProgress * -0.6f, z);
        // ходьба: «восьмёрка» (почти нет в прицеле)
        float stride = MathHelper.lerp(tickDelta, player.prevStrideDistance, player.strideDistance);
        float ph = -(player.horizontalSpeed + (player.horizontalSpeed - player.prevHorizontalSpeed) * tickDelta) * (float) Math.PI;
        float bobK = (1f - a * 0.85f) * (1f + sp * 1.2f);
        m.translate(MathHelper.sin(ph) * stride * 0.05f * bobK * s, -Math.abs(MathHelper.cos(ph)) * stride * 0.06f * bobK, 0);
        // дыхание
        float t = (player.age + tickDelta) * 0.055f;
        float br = 1 - a * 0.75f;
        m.translate(MathHelper.sin(t) * 0.0045f * br, MathHelper.sin(t * 1.7f) * 0.0055f * br, 0);
        // прыжок / приземление
        if (main) m.translate(0, -air * 0.03f + land * 0.05f, 0);
        if (main) {
            // инерция поворота: ствол отстаёт
            float sw = 1 - a * 0.7f;
            m.translate(swayX * 0.003f * sw, -swayY * 0.0028f * sw, 0);
            m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(swayX * 0.3f * sw));
            m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(swayY * 0.28f * sw + land * 6 - air * 4));
            m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-swayX * 0.3f * sw));
        }
        // спринт: оружие опущено и повёрнуто к центру
        if (sp > 0.01f) {
            float run = MathHelper.sin(ph) * 3f * sp;
            m.translate(-0.06f * sp * s, -0.07f * sp, 0.03f * sp);
            m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((34f + run) * sp * s));
            m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-18f * sp));
            m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(14f * sp * s));
        }
        // доставание: снизу, с поворотом
        if (draw > 0) {
            m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-45f * draw));
            m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-25f * draw * s));
        }
        // от бедра ствол чуть смотрит к перекрестию
        float hipYaw = (1 - a) * 4.5f * s, hipPitch = (1 - a) * 1.5f;
        m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(hipYaw));
        m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(hipPitch));
        // анимация всего оружия (перезарядка, осмотр, затвор)
        if (posed) {
            float pa = 1 - a * 0.6f;
            m.translate(pose.tx * s, pose.ty, pose.tz);
            m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(pose.ry * s * pa));
            m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(pose.rz * s * pa));
            m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pose.rx * pa));
        }
        // отдача: назад, ствол вверх, крен и чуть вбок (пружины)
        if (main) {
            float ka = 1 - a * 0.35f;
            m.translate(side * 0.01f * s, kp * 0.0015f, kz * ka);
            m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(kp * (1 - a * 0.55f)));
            m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(roll * 0.1f * s));
            m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * 1.5f * s));
        }

        boolean flashing = main && System.nanoTime() < flashUntil && shotItem == item.getItem();
        int lit = flashing ? 0xF000F0 : light; // «свет» от вспышки на руках и оружии
        ModelTransformationMode mode = right ? ModelTransformationMode.FIRST_PERSON_RIGHT_HAND : ModelTransformationMode.FIRST_PERSON_LEFT_HAND;
        Transformation tf = c.getItemRenderer().getModel(item, player.getWorld(), player, 0).getTransformation().getTransformation(mode);
        if (main) trackPoints(c, player, m, tf, !right, meta, posed ? pose : null);
        if (main && !player.isInvisible()) arms(c, player, meta, posed ? pose : null, k, tf, !right, a, m, buffers, lit);
        r.renderItem(player, item, mode, !right, m, buffers, lit);
        if (flashing) flash(m, buffers, tf, !right, meta, k);
        m.pop();
    }

    /** Точки модели (дуло, окно выброса, магазин) → мир: для гильз, дыма, трассеров. */
    private static void trackPoints(MinecraftClient c, AbstractClientPlayerEntity player, MatrixStack m, Transformation tf, boolean left, GunMeta meta,
                                    GunAnim.Pose pose) {
        MatrixStack t = new MatrixStack();
        t.peek().getPositionMatrix().set(m.peek().getPositionMatrix());
        tf.apply(left, t);
        t.translate(-0.5f, -0.5f, -0.5f);
        Matrix4f mat = t.peek().getPositionMatrix();
        muzzleWorld = toWorld(c, mat.transformPosition(px(meta.muzzle, left)));
        float slide = pose != null && pose.bone("slide") != null ? pose.bone("slide")[2] : 0;
        ejectWorld = toWorld(c, mat.transformPosition(px(new float[]{meta.eject[0], meta.eject[1], meta.eject[2] + slide * 0.5f}, left)));
        magWorld = toWorld(c, mat.transformPosition(px(pose != null ? pose.magPos : meta.magHand, left)));
    }

    private static Vector3f px(float[] p, boolean left) {
        return new Vector3f((left ? 16 - p[0] : p[0]) / 16f, p[1] / 16f, p[2] / 16f);
    }

    /** Из пространства камеры (x вправо, y вверх, -z вперёд) в мир. */
    private static Vec3d toWorld(MinecraftClient c, Vector3f v) {
        var cam = c.gameRenderer.getCamera();
        Vec3d fwd = Vec3d.fromPolar(cam.getPitch(), cam.getYaw());
        Vec3d right = fwd.crossProduct(new Vec3d(0, 1, 0));
        if (right.lengthSquared() < 1e-6) right = new Vec3d(1, 0, 0);
        right = right.normalize();
        Vec3d up = right.crossProduct(fwd).normalize();
        return cam.getPos().add(right.multiply(v.x)).add(up.multiply(v.y)).add(fwd.multiply(-v.z));
    }

    /** Руки тоньше ванильных: оружие в них не выглядит игрушечным. */
    private static final float ARM_SCALE = 0.56f;

    /** Сейчас рисуем руку от первого лица — поза «целится» не нужна. */
    public static boolean fpArm;

    /**
     * Руки держат оружие: кулак — на рукояти и на цевье (или на магазине, затворе, помпе — по анимации),
     * плечо — за краем экрана и немного следует за оружием (в прицеле плечи ближе к центру).
     */
    private static void arms(MinecraftClient c, AbstractClientPlayerEntity player, GunMeta meta, GunAnim.Pose pose, Kind k, Transformation tf, boolean left,
                             float a, MatrixStack m, VertexConsumerProvider buffers, int light) {
        if (!(c.getEntityRenderDispatcher().getRenderer(player) instanceof net.minecraft.client.render.entity.PlayerEntityRenderer pr)) return;
        float s = left ? -1 : 1;
        Vector3f mainShoulder = new Vector3f(MathHelper.lerp(a, 0.6f, 0.36f) * s, -0.95f, MathHelper.lerp(a, 0.15f, 0.2f));
        Vector3f offShoulder = new Vector3f(MathHelper.lerp(a, -0.4f, -0.45f) * s, -0.95f, 0.05f);
        float[] grip = pose != null ? pose.main : meta.grip;
        float[] sup = pose != null ? pose.support : meta.support;
        fpArm = true;
        try {
            arm(pr, player, m, tf, left, grip, mainShoulder, true, buffers, light);
            arm(pr, player, m, tf, left, sup, offShoulder, false, buffers, light);
        } finally {
            fpArm = false;
        }
    }

    private static void arm(net.minecraft.client.render.entity.PlayerEntityRenderer pr, AbstractClientPlayerEntity player, MatrixStack m,
                            Transformation tf, boolean left, float[] at, Vector3f shoulder, boolean mainArm, VertexConsumerProvider buffers, int light) {
        m.push();
        tf.apply(left, m);
        m.translate(-0.5f, -0.5f, -0.5f);
        float gx = at[0] / 16f, gy = at[1] / 16f, gz = at[2] / 16f;
        if (left) gx = 1 - gx; // зеркальная модель для левой руки
        m.translate(gx, gy, gz);
        // где кулак на экране и куда смотреть, чтобы рука уходила к плечу
        Matrix4f pos = m.peek().getPositionMatrix();
        Vector3f fist = pos.transformPosition(new Vector3f());
        Vector3f toShoulder = new Vector3f(shoulder).sub(fist);
        Matrix4f inv = new Matrix4f(pos).invert();
        Vector3f local = inv.transformDirection(toShoulder).normalize();
        // ось руки модели: от кулака к плечу — это -Y
        org.joml.Quaternionf q = new org.joml.Quaternionf().rotationTo(0, -1, 0, local.x, local.y, local.z);
        m.multiply(q);
        float sc = ARM_SCALE / Math.max(0.05f, tf.scale.x());
        m.scale(sc, sc, sc);
        boolean rightArm = mainArm != left;
        m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(rightArm ? -70 : 70));
        m.translate(rightArm ? 6f / 16f : -6f / 16f, -10.5f / 16f, 0);
        if (rightArm) pr.renderRightArm(m, buffers, light, player);
        else pr.renderLeftArm(m, buffers, light, player);
        m.pop();
    }

    /** Вспышка: случайная звезда навстречу камере + «языки» пламени вдоль ствола. */
    private static void flash(MatrixStack m, VertexConsumerProvider buffers, Transformation tf, boolean left, GunMeta meta, Kind k) {
        m.push();
        tf.apply(left, m);
        float mx = left ? 16 - meta.muzzle[0] : meta.muzzle[0];
        m.translate(mx / 16f - 0.5f, meta.muzzle[1] / 16f - 0.5f, meta.muzzle[2] / 16f - 0.5f);
        float size = (k == Kind.SHOTGUN ? 5.5f : k == Kind.SNIPER ? 6f : k == Kind.PISTOL ? 3.6f : 4.2f) * 1.3f / 16f * flashScale;
        if (ads > 0.5f) size *= 0.75f;
        VertexConsumer vc = buffers.getBuffer(RenderLayer.getEyes(FLASH));
        float u0 = (flashVariant % 2) * 0.5f, v0 = (flashVariant / 2) * 0.5f;
        m.push();
        m.translate(0, 0, -size * 0.25f);
        m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(flashSpin));
        quad(vc, m, size, size, u0, v0, u0 + 0.5f, v0 + 0.5f);
        m.pop();
        VertexConsumer vs = buffers.getBuffer(RenderLayer.getEyes(FLASH_SIDE));
        m.push();
        m.translate(0, 0, -size * 1.0f);
        m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90));
        for (int i = 0; i < 2; i++) {
            m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(90));
            quad(vs, m, size * 0.42f, size * 1.05f, 0, 0, 1, 1);
        }
        m.pop();
        m.pop();
    }

    private static void quad(VertexConsumer vc, MatrixStack m, float rx, float ry, float u0, float v0, float u1, float v1) {
        MatrixStack.Entry e = m.peek();
        Matrix4f mat = e.getPositionMatrix();
        int light = 0xF000F0;
        for (int side = 0; side < 2; side++) {
            float[][] v = side == 0 ? new float[][]{{-rx, -ry, u0, v1}, {rx, -ry, u1, v1}, {rx, ry, u1, v0}, {-rx, ry, u0, v0}}
                    : new float[][]{{-rx, ry, u0, v0}, {rx, ry, u1, v0}, {rx, -ry, u1, v1}, {-rx, -ry, u0, v1}};
            for (float[] p : v)
                vc.vertex(mat, p[0], p[1], 0).color(255, 255, 255, 255).texture(p[2], p[3]).overlay(OverlayTexture.DEFAULT_UV).light(light)
                        .normal(e, 0, 0, side == 0 ? 1 : -1);
        }
    }

    /** Тряска камеры от выстрела (из GameRenderer.tiltViewWhenHurt). */
    public static void shake(MatrixStack m, float tickDelta) {
        MinecraftClient c = MinecraftClient.getInstance();
        if (Math.abs(shake) < 0.002f || c.options.getPerspective() != Perspective.FIRST_PERSON) return;
        float k = 1 - ads * 0.5f;
        m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(shake * 0.8f * k));
        m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-Math.abs(shake) * 0.45f * k));
    }

    // ------------------------------------------------------------------ от третьего лица
    /** Когда кто-то из других стрелял (по пакету) — поднимает оружие в прицел. */
    public static final java.util.Map<Integer, Long> OTHERS_SHOT = new java.util.HashMap<>();

    /** Поза после ванильной setAngles: от бедра (ствол вперёд-вниз), в прицеле/при стрельбе — у плеча, на бегу — опущено. */
    public static void pose(BipedEntityModel<?> model, LivingEntity e) {
        ItemStack st = e.getMainHandStack();
        if (fpArm || !(st.getItem() instanceof GunItem)) return;
        Kind k = kind(st.getItem());
        boolean right = e.getMainArm() == Arm.RIGHT;
        ModelPart main = right ? model.rightArm : model.leftArm, off = right ? model.leftArm : model.rightArm;
        float s = right ? 1 : -1;
        float hp = model.head.pitch, hy = model.head.yaw;
        MinecraftClient c = MinecraftClient.getInstance();
        boolean me = e == c.player;
        long now = System.nanoTime();
        long shot = me ? shotAt : OTHERS_SHOT.getOrDefault(e.getId(), 0L);
        boolean firing = now - shot < 600_000_000L;
        float aim = me ? Math.max(ads, firing ? 1 : 0) : GunWorldFx.AIMING.contains(e.getId()) || firing ? 1 : 0;
        if (!(e instanceof net.minecraft.entity.player.PlayerEntity)) aim = 1; // NPC с оружием всегда «в прицеле»
        float kick = me ? kp * 0.02f : 0;
        boolean low = e.isSprinting() && !firing && aim < 0.5f;
        float aimPitch = -MathHelper.HALF_PI + MathHelper.clamp(hp, -1.2f, 1.2f);
        float hipPitch = -0.95f + MathHelper.clamp(hp, -1.0f, 0.8f) * 0.6f;
        float pitch = low ? -0.7f : MathHelper.lerp(aim, hipPitch, aimPitch);
        float rp = me ? reloadProgress(st.getItem()) : -1;
        if (rp >= 0) pitch = MathHelper.lerp(smooth(0, 0.18f, rp) * (1 - smooth(0.82f, 1, rp)), pitch, -0.7f);
        main.pitch = pitch - kick;
        main.yaw = hy + MathHelper.lerp(aim, -0.25f, -0.06f) * s + (low ? -0.3f * s : 0);
        main.roll = 0;
        off.pitch = pitch - kick + (low ? 0.15f : 0) + (aim < 0.5f ? 0.1f : 0);
        off.yaw = hy + MathHelper.lerp(aim, 0.75f, 0.62f) * s + (low ? -0.1f * s : 0);
        off.roll = 0;
        if (k == Kind.PISTOL && aim < 0.5f && !firing) off.yaw = hy + 0.55f * s;
        // в прицеле голова чуть к прикладу
        if (aim > 0.5f && k != Kind.PISTOL) {
            model.head.roll = 0.12f * s * aim;
            model.hat.roll = model.head.roll;
        }
    }
}
