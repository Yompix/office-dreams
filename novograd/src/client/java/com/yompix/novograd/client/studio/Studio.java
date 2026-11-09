package com.yompix.novograd.client.studio;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import com.yompix.novograd.Novograd;
import com.yompix.novograd.client.mixin.GameRendererAccessor;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * «Киностудия Новограда» (F7) — снимай своё кино прямо в игре, без внешних модов:
 *  • свободная камера (WASD, пробел/Shift — выше/ниже, Ctrl — быстрее);
 *  • ключевые кадры камеры (Enter) — плавный пролёт по ним (P), облёт вокруг точки (O);
 *  • наклон камеры (, .), зум (↑ ↓), скорость пролёта ([ ]);
 *  • ДУБЛИ (U): записываешь, как бегаешь/стреляешь/прыгаешь, — и до 4 твоих «актёров» повторяют это при съёмке,
 *    как в Blockbuster: можно сыграть все роли сцены самому;
 *  • кинофильтры (I): нуар, закат, неон, «блокбастер»; кинополосы и виньетка.
 * Всё хранится в config/novograd_studio.json. Видео записывай через OBS, Flashback или Replay Mod.
 */
public final class Studio {
    private Studio() {}

    // ------------------------------------------------------------------ данные
    public static final class Key {
        double x, y, z;
        float yaw, pitch, roll, fov;
        float secs = 3f; // время перехода к следующему кадру
    }

    /** Один кадр дубля: позиция и поза актёра. */
    public static final class Frame {
        double x, y, z;
        float yaw, head, pitch, body;
        boolean sneak, swing;
        String item = "";
    }

    public static final class Take {
        List<Frame> frames = new ArrayList<>();
    }

    static final class Save {
        List<Key> keys = new ArrayList<>();
        List<Take> takes = new ArrayList<>();
        int filter;
    }

    private static Save data = new Save();
    private static final Gson GSON = new GsonBuilder().create();

    // ------------------------------------------------------------------ состояние
    private static boolean on, playing, recording;
    private static int countdown, playTick, recTick;
    private static ArmorStandEntity cam;
    private static Input savedInput;
    private static double cx, cy, cz;
    private static float fov = 70, roll, prevFov = 70, prevRoll;
    private static float speedMul = 1;
    private static final List<OtherClientPlayerEntity> ghosts = new ArrayList<>();
    private static final boolean[] down = new boolean[512];
    private static boolean hudWas, hints = true;
    private static float bars;
    private static Take current;

    public static final String[] FILTERS = {"без фильтра", "НУАР", "ЗАКАТ", "НЕОН", "БЛОКБАСТЕР", "ВЫЦВЕТШАЯ ПЛЁНКА"};
    private static final String[] FILTER_FILES = {"", "film_noir", "film_sunset", "film_neon", "film_blockbuster", "film_faded"};
    private static int appliedFilter = -1;

    public static boolean active() {
        return on;
    }

    public static boolean playing() {
        return on && playing;
    }

    // ------------------------------------------------------------------ вкл/выкл
    public static void toggle(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) return;
        if (on) {
            off(mc);
            return;
        }
        if (mc.player.hasVehicle()) {
            mc.player.sendMessage(Text.literal("Киностудия: выйди из транспорта (в машине — кино-камера на B)").formatted(Formatting.GRAY), true);
            return;
        }
        load();
        on = true;
        cam = new ArmorStandEntity(EntityType.ARMOR_STAND, mc.world);
        cam.setInvisible(true);
        cam.setNoGravity(true);
        Vec3d e = mc.player.getEyePos();
        cx = e.x;
        cy = e.y;
        cz = e.z;
        fov = prevFov = mc.options.getFov().getValue();
        roll = prevRoll = 0;
        savedInput = mc.player.input;
        mc.player.input = new Input();
        placeCam(mc.player.getYaw(), mc.player.getPitch(), true);
        mc.setCameraEntity(cam);
        hudWas = mc.options.hudHidden;
        appliedFilter = -1;
        spawnGhosts(mc);
        mc.player.sendMessage(Text.literal("Киностудия открыта. H — подсказки, F7 — выйти.").formatted(Formatting.LIGHT_PURPLE), false);
    }

    private static void off(MinecraftClient mc) {
        stopRecording(mc, false);
        playing = false;
        on = false;
        if (mc.player != null) {
            if (savedInput != null) mc.player.input = savedInput instanceof KeyboardInput ? savedInput : new KeyboardInput(mc.options);
            if (mc.getCameraEntity() != mc.player) mc.setCameraEntity(mc.player);
        }
        mc.options.hudHidden = hudWas;
        clearGhosts(mc);
        ((GameRendererAccessor) mc.gameRenderer).novograd$disablePost();
        appliedFilter = -1;
        cam = null;
        save();
    }

    // ------------------------------------------------------------------ тик
    public static void tick(MinecraftClient mc) {
        bars += ((on && (playing || recording) ? 1 : on ? 0.35f : 0) - bars) * 0.12f;
        if (!on) return;
        if (mc.player == null || mc.world == null || mc.player.hasVehicle() || mc.player.isDead()) {
            off(mc);
            return;
        }
        prevFov = fov;
        prevRoll = roll;
        boolean free = mc.currentScreen == null;
        if (free) keys(mc);
        if (recording) {
            tickRecording(mc);
        } else if (playing) {
            tickPlayback(mc);
        } else if (free) {
            fly(mc);
        } else placeCam(cam.getYaw(), cam.getPitch(), false);
        if (!recording && mc.getCameraEntity() != cam) mc.setCameraEntity(cam);
        mc.options.hudHidden = !recording || countdown > 0;
        applyFilter(mc);
    }

    private static boolean pressed(MinecraftClient mc, int key) {
        boolean now = InputUtil.isKeyPressed(mc.getWindow().getHandle(), key);
        boolean was = down[key];
        down[key] = now;
        return now && !was;
    }

    private static boolean held(MinecraftClient mc, int key) {
        return InputUtil.isKeyPressed(mc.getWindow().getHandle(), key);
    }

    private static void keys(MinecraftClient mc) {
        if (pressed(mc, GLFW.GLFW_KEY_H)) hints = !hints;
        if (pressed(mc, GLFW.GLFW_KEY_I)) {
            data.filter = (data.filter + 1) % FILTERS.length;
            toast(mc, "Фильтр: " + FILTERS[data.filter]);
        }
        if (pressed(mc, GLFW.GLFW_KEY_U)) {
            if (recording || countdown > 0) stopRecording(mc, true);
            else if (data.takes.size() >= 4) toast(mc, "Уже 4 дубля — End удалит их");
            else {
                playing = false;
                countdown = 60;
                recording = true;
                current = new Take();
                recTick = 0;
                mc.setCameraEntity(mc.player);
                mc.player.input = savedInput instanceof KeyboardInput ? savedInput : new KeyboardInput(mc.options);
                restartGhosts(mc);
            }
        }
        if (recording) return; // во время дубля управляешь героем — остальные клавиши студии молчат
        if (pressed(mc, GLFW.GLFW_KEY_END)) {
            data.takes.clear();
            clearGhosts(mc);
            toast(mc, "Дубли удалены");
        }
        if (pressed(mc, GLFW.GLFW_KEY_P)) {
            if (playing) {
                playing = false;
                mc.options.hudHidden = hudWas;
            } else if (data.keys.isEmpty() && data.takes.isEmpty()) toast(mc, "Сначала поставь кадры (Enter) или запиши дубль (U)");
            else {
                playing = true;
                playTick = 0;
                mc.options.hudHidden = true;
                restartGhosts(mc);
            }
        }
        if (playing) return;
        if (pressed(mc, GLFW.GLFW_KEY_ENTER) || pressed(mc, GLFW.GLFW_KEY_KP_ENTER)) {
            Key k = new Key();
            k.x = cx;
            k.y = cy;
            k.z = cz;
            k.yaw = cam.getYaw();
            k.pitch = cam.getPitch();
            k.roll = roll;
            k.fov = fov;
            if (!data.keys.isEmpty()) k.secs = data.keys.get(data.keys.size() - 1).secs;
            data.keys.add(k);
            toast(mc, "Кадр " + data.keys.size() + " поставлен");
            save();
        }
        if (pressed(mc, GLFW.GLFW_KEY_BACKSPACE) && !data.keys.isEmpty()) {
            data.keys.remove(data.keys.size() - 1);
            toast(mc, data.keys.isEmpty() ? "Кадров нет" : "Кадр удалён, осталось " + data.keys.size());
        }
        if (pressed(mc, GLFW.GLFW_KEY_DELETE)) {
            data.keys.clear();
            toast(mc, "Все кадры удалены");
        }
        if (pressed(mc, GLFW.GLFW_KEY_O)) orbit(mc);
        if (pressed(mc, GLFW.GLFW_KEY_LEFT_BRACKET)) speed(mc, -0.5f);
        if (pressed(mc, GLFW.GLFW_KEY_RIGHT_BRACKET)) speed(mc, 0.5f);
        if (held(mc, GLFW.GLFW_KEY_COMMA)) roll -= 1.2f;
        if (held(mc, GLFW.GLFW_KEY_PERIOD)) roll += 1.2f;
        if (pressed(mc, GLFW.GLFW_KEY_SLASH)) roll = 0;
        if (held(mc, GLFW.GLFW_KEY_UP)) fov = Math.max(10, fov - 1.2f);
        if (held(mc, GLFW.GLFW_KEY_DOWN)) fov = Math.min(110, fov + 1.2f);
        if (held(mc, GLFW.GLFW_KEY_RIGHT)) speedMul = Math.min(6, speedMul + 0.05f);
        if (held(mc, GLFW.GLFW_KEY_LEFT)) speedMul = Math.max(0.1f, speedMul - 0.05f);
    }

    private static void speed(MinecraftClient mc, float d) {
        if (data.keys.isEmpty()) return;
        float s = MathHelper.clamp(data.keys.get(data.keys.size() - 1).secs + d, 0.5f, 30f);
        for (Key k : data.keys) k.secs = s;
        toast(mc, "Переход между кадрами: " + s + " с · весь пролёт " + Math.round(total() / 20f) + " с");
    }

    /** Облёт: 9 кадров по кругу вокруг точки, куда смотрит камера. */
    private static void orbit(MinecraftClient mc) {
        Vec3d look = Vec3d.fromPolar(cam.getPitch(), cam.getYaw());
        var hit = mc.world.raycast(new net.minecraft.world.RaycastContext(new Vec3d(cx, cy, cz), new Vec3d(cx, cy, cz).add(look.multiply(60)),
                net.minecraft.world.RaycastContext.ShapeType.COLLIDER, net.minecraft.world.RaycastContext.FluidHandling.NONE, cam));
        Vec3d c = hit.getType() == net.minecraft.util.hit.HitResult.Type.MISS ? new Vec3d(cx, cy, cz).add(look.multiply(14)) : hit.getPos();
        double r = Math.max(5, Math.min(30, Math.sqrt(Math.pow(c.x - cx, 2) + Math.pow(c.z - cz, 2))));
        double h = cy - c.y;
        double a0 = Math.atan2(cz - c.z, cx - c.x);
        data.keys.clear();
        for (int i = 0; i <= 8; i++) {
            double a = a0 + i * Math.PI * 2 / 8;
            Key k = new Key();
            k.x = c.x + Math.cos(a) * r;
            k.z = c.z + Math.sin(a) * r;
            k.y = c.y + h;
            double dx = c.x - k.x, dy = c.y + 1 - k.y, dz = c.z - k.z;
            k.yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            k.pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
            k.roll = roll;
            k.fov = fov;
            k.secs = 2.5f;
            data.keys.add(k);
        }
        toast(mc, "Облёт готов: 9 кадров — P, чтобы снять");
    }

    // ------------------------------------------------------------------ свободная камера
    private static void fly(MinecraftClient mc) {
        var o = mc.options;
        float yaw = mc.player.getYaw(), pitch = mc.player.getPitch();
        double sp = 0.45 * speedMul * (held(mc, GLFW.GLFW_KEY_LEFT_CONTROL) ? 3 : 1);
        double f = (o.forwardKey.isPressed() ? 1 : 0) - (o.backKey.isPressed() ? 1 : 0);
        double s = (o.leftKey.isPressed() ? 1 : 0) - (o.rightKey.isPressed() ? 1 : 0);
        double u = (o.jumpKey.isPressed() ? 1 : 0) - (held(mc, GLFW.GLFW_KEY_LEFT_SHIFT) ? 1 : 0);
        Vec3d fw = Vec3d.fromPolar(pitch, yaw), rt = Vec3d.fromPolar(0, yaw - 90);
        Vec3d mv = fw.multiply(f).add(rt.multiply(s)).add(0, u, 0);
        if (mv.lengthSquared() > 1e-4) {
            mv = mv.normalize().multiply(sp);
            cx += mv.x;
            cy += mv.y;
            cz += mv.z;
        }
        placeCam(yaw, pitch, false);
    }

    private static void placeCam(float yaw, float pitch, boolean snap) {
        cam.prevX = snap ? cx : cam.getX();
        cam.prevY = snap ? cy - cam.getStandingEyeHeight() : cam.getY();
        cam.prevZ = snap ? cz : cam.getZ();
        cam.lastRenderX = cam.prevX;
        cam.lastRenderY = cam.prevY;
        cam.lastRenderZ = cam.prevZ;
        cam.setPosition(cx, cy - cam.getStandingEyeHeight(), cz);
        cam.prevYaw = snap ? yaw : cam.getYaw();
        cam.prevPitch = snap ? pitch : cam.getPitch();
        cam.setYaw(yaw);
        cam.setPitch(pitch);
        cam.prevHeadYaw = cam.prevYaw;
        cam.setHeadYaw(yaw);
    }

    // ------------------------------------------------------------------ пролёт
    private static int total() {
        int t = 0;
        for (int i = 0; i < data.keys.size() - 1; i++) t += Math.round(data.keys.get(i).secs * 20);
        return t;
    }

    private static int takesLength() {
        int t = 0;
        for (Take tk : data.takes) t = Math.max(t, tk.frames.size());
        return t;
    }

    private static void tickPlayback(MinecraftClient mc) {
        int len = Math.max(total(), takesLength());
        if (playTick > len + 10) {
            playing = false;
            mc.options.hudHidden = hudWas;
            toast(mc, "Снято! P — ещё раз");
            return;
        }
        if (data.keys.size() >= 2) {
            int t = Math.min(playTick, total());
            int seg = 0;
            while (seg < data.keys.size() - 2 && t >= Math.round(data.keys.get(seg).secs * 20)) {
                t -= Math.round(data.keys.get(seg).secs * 20);
                seg++;
            }
            float segLen = Math.max(1, Math.round(data.keys.get(seg).secs * 20));
            float u = MathHelper.clamp(t / segLen, 0, 1);
            // мягкий старт и финиш всего пролёта, в середине — постоянная скорость
            if (data.keys.size() == 2) u = ease(u);
            else if (seg == 0) u = easeIn(u);
            else if (seg == data.keys.size() - 2) u = easeOut(u);
            Key p0 = data.keys.get(Math.max(0, seg - 1)), p1 = data.keys.get(seg), p2 = data.keys.get(seg + 1),
                    p3 = data.keys.get(Math.min(data.keys.size() - 1, seg + 2));
            cx = cat(p0.x, p1.x, p2.x, p3.x, u);
            cy = cat(p0.y, p1.y, p2.y, p3.y, u);
            cz = cat(p0.z, p1.z, p2.z, p3.z, u);
            float y1 = p1.yaw, y0 = y1 + MathHelper.wrapDegrees(p0.yaw - y1), y2 = y1 + MathHelper.wrapDegrees(p2.yaw - y1), y3 = y2 + MathHelper.wrapDegrees(p3.yaw - y2);
            float yaw = (float) cat(y0, y1, y2, y3, u);
            float pitch = (float) cat(p0.pitch, p1.pitch, p2.pitch, p3.pitch, u);
            roll = (float) cat(p0.roll, p1.roll, p2.roll, p3.roll, u);
            fov = (float) cat(p0.fov, p1.fov, p2.fov, p3.fov, u);
            placeCam(yaw, pitch, playTick == 0);
        } else if (data.keys.size() == 1) {
            Key k = data.keys.get(0);
            cx = k.x;
            cy = k.y;
            cz = k.z;
            roll = k.roll;
            fov = k.fov;
            placeCam(k.yaw, k.pitch, playTick == 0);
        } else placeCam(cam.getYaw(), cam.getPitch(), false);
        tickGhosts(playTick);
        playTick++;
    }

    private static float ease(float u) {
        return u * u * (3 - 2 * u);
    }

    private static float easeIn(float u) {
        return u * u * (2 - u); // с нуля разгоняется до постоянной скорости
    }

    private static float easeOut(float u) {
        return u * (1 + u - u * u);
    }

    private static double cat(double p0, double p1, double p2, double p3, double t) {
        double t2 = t * t, t3 = t2 * t;
        return 0.5 * (2 * p1 + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3);
    }

    // ------------------------------------------------------------------ дубли (актёры)
    private static void tickRecording(MinecraftClient mc) {
        if (countdown > 0) {
            if (countdown % 20 == 0) toast(mc, "Дубль " + (data.takes.size() + 1) + " через " + countdown / 20 + "…");
            if (--countdown == 0) {
                toast(mc, "● ЗАПИСЬ — играй! U — стоп");
                restartGhosts(mc);
            }
            return;
        }
        var p = mc.player;
        Frame f = new Frame();
        f.x = p.getX();
        f.y = p.getY();
        f.z = p.getZ();
        f.yaw = p.getYaw();
        f.head = p.getHeadYaw();
        f.pitch = p.getPitch();
        f.body = p.bodyYaw;
        f.sneak = p.isInSneakingPose();
        f.swing = p.handSwinging && p.handSwingTicks == 0;
        ItemStack st = p.getMainHandStack();
        f.item = st.isEmpty() ? "" : Registries.ITEM.getId(st.getItem()).toString();
        current.frames.add(f);
        tickGhosts(recTick++);
        if (current.frames.size() >= 20 * 120) stopRecording(mc, true);
    }

    private static void stopRecording(MinecraftClient mc, boolean keep) {
        if (!recording) return;
        recording = false;
        boolean wasCounting = countdown > 0;
        countdown = 0;
        if (keep && !wasCounting && current != null && current.frames.size() > 10) {
            data.takes.add(current);
            if (mc.player != null) toast(mc, "Дубль " + data.takes.size() + " записан (" + current.frames.size() / 20 + " с). P — снять сцену с актёрами");
            save();
        }
        current = null;
        if (mc.player != null && on) {
            savedInput = mc.player.input;
            mc.player.input = new Input();
            Vec3d e = mc.player.getEyePos();
            if (data.keys.isEmpty()) {
                cx = e.x;
                cy = e.y + 1.5;
                cz = e.z;
            }
            placeCam(cam.getYaw(), cam.getPitch(), true);
            mc.setCameraEntity(cam);
        }
        spawnGhosts(mc);
    }

    private static final class Ghost extends OtherClientPlayerEntity {
        Ghost(MinecraftClient mc, int i) {
            super(mc.world, new GameProfile(UUID.nameUUIDFromBytes(("novograd_studio_" + i).getBytes()), mc.player.getGameProfile().getName()));
        }

        @Override
        public net.minecraft.client.util.SkinTextures getSkinTextures() {
            var p = MinecraftClient.getInstance().player;
            return p != null ? p.getSkinTextures() : super.getSkinTextures();
        }

        @Override
        public boolean shouldRenderName() {
            return false;
        }
    }

    private static void spawnGhosts(MinecraftClient mc) {
        clearGhosts(mc);
        for (int i = 0; i < data.takes.size(); i++) {
            Take t = data.takes.get(i);
            if (t.frames.isEmpty()) continue;
            Ghost g = new Ghost(mc, i);
            g.setId(-7700 - i);
            Frame f = t.frames.get(0);
            g.refreshPositionAndAngles(f.x, f.y, f.z, f.yaw, f.pitch);
            g.setHeadYaw(f.head);
            g.bodyYaw = f.body;
            mc.world.addEntity(g);
            ghosts.add(g);
        }
    }

    private static void restartGhosts(MinecraftClient mc) {
        spawnGhosts(mc);
    }

    private static void clearGhosts(MinecraftClient mc) {
        for (Entity g : ghosts) if (mc.world != null) mc.world.removeEntity(g.getId(), Entity.RemovalReason.DISCARDED);
        ghosts.clear();
    }

    private static void tickGhosts(int tick) {
        for (int i = 0; i < ghosts.size() && i < data.takes.size(); i++) {
            List<Frame> fr = data.takes.get(i).frames;
            Frame f = fr.get(Math.min(tick, fr.size() - 1));
            OtherClientPlayerEntity g = ghosts.get(i);
            g.updateTrackedPositionAndAngles(f.x, f.y, f.z, f.yaw, f.pitch, 2);
            g.updateTrackedHeadRotation(f.head, 2);
            g.setSneaking(f.sneak);
            g.setPose(f.sneak ? EntityPose.CROUCHING : EntityPose.STANDING);
            if (f.swing && tick < fr.size()) g.swingHand(Hand.MAIN_HAND);
            Identifier id = Identifier.tryParse(f.item);
            ItemStack want = id == null || f.item.isEmpty() ? ItemStack.EMPTY : new ItemStack(Registries.ITEM.get(id));
            if (!ItemStack.areItemsEqual(want, g.getMainHandStack())) g.setStackInHand(Hand.MAIN_HAND, want);
        }
    }

    // ------------------------------------------------------------------ фильтры
    private static void applyFilter(MinecraftClient mc) {
        var acc = (GameRendererAccessor) mc.gameRenderer;
        boolean lost = data.filter != 0 && mc.gameRenderer.getPostProcessor() == null;
        if (data.filter == appliedFilter && !lost) return;
        appliedFilter = data.filter;
        if (data.filter == 0) acc.novograd$disablePost();
        else acc.novograd$loadPost(Novograd.id("shaders/post/" + FILTER_FILES[data.filter] + ".json"));
    }

    // ------------------------------------------------------------------ камера: наклон и зум (из миксина)
    public static float roll(float delta) {
        return on && !recording ? MathHelper.lerp(delta, prevRoll, roll) : 0;
    }

    public static double fov(double vanilla, float delta) {
        if (!on || recording) return vanilla;
        return MathHelper.lerp(delta, prevFov, fov);
    }

    // ------------------------------------------------------------------ экран
    private static String toast = "";
    private static long toastAt;

    private static void toast(MinecraftClient mc, String s) {
        toast = s;
        toastAt = System.currentTimeMillis();
    }

    public static void render(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        int W = ctx.getScaledWindowWidth(), H = ctx.getScaledWindowHeight();
        if (bars > 0.01f) {
            int h = (int) (H * 0.12f * bars);
            ctx.fill(0, 0, W, h, 0xFF000000);
            ctx.fill(0, H - h, W, H, 0xFF000000);
        }
        if (!on) return;
        if (playing || recording) {
            // виньетка: затемнение к краям кадра
            for (int i = 0; i < 12; i++) {
                int a = (int) (Math.pow((12 - i) / 12.0, 2.2) * 70) << 24;
                int m = i * Math.max(W, H) / 60;
                ctx.fill(m, 0, m + Math.max(1, W / 60), H, a);
                ctx.fill(W - m - Math.max(1, W / 60), 0, W - m, H, a);
            }
        }
        TextRenderer tr = mc.textRenderer;
        int barH = (int) (H * 0.12f * bars);
        if (recording && countdown == 0 && (System.currentTimeMillis() / 500) % 2 == 0)
            ctx.drawText(tr, "● REC  дубль " + (data.takes.size() + 1), 10, Math.max(6, barH / 2 - 4), 0xFFFF3B3B, false);
        if (playing) {
            int len = Math.max(1, Math.max(total(), takesLength()));
            ctx.fill(0, H - 2, W * Math.min(playTick, len) / len, H, 0x80FF4FD8);
        }
        long age = System.currentTimeMillis() - toastAt;
        if (age < 2600 && !toast.isEmpty()) {
            int a = (int) (255 * Math.min(1, (2600 - age) / 400f)) << 24;
            int w = tr.getWidth(toast);
            ctx.fill(W / 2 - w / 2 - 6, H - barH - 22, W / 2 + w / 2 + 6, H - barH - 8, a / 2 & 0xFF000000);
            ctx.drawText(tr, toast, W / 2 - w / 2, H - barH - 19, a | 0xFFFFFF, false);
        }
        if (playing || recording) return;
        String head = "КИНОСТУДИЯ  ·  кадров " + data.keys.size() + "  ·  дублей " + data.takes.size() + "/4  ·  " + FILTERS[data.filter]
                + "  ·  зум " + Math.round(fov) + "°" + (Math.abs(roll) > 0.5 ? "  ·  наклон " + Math.round(roll) + "°" : "");
        ctx.drawText(tr, head, 8, Math.max(4, barH / 2 - 4), 0xFFFF4FD8, true);
        if (!hints) return;
        String[] lines = {
                "WASD — лететь · пробел/Shift — выше/ниже · Ctrl — быстрее · ← → — скорость полёта",
                "Enter — кадр · Backspace — убрать кадр · Del — все кадры · O — облёт точки",
                "P — снять (пролёт по кадрам) · [ ] — время перехода · ↑ ↓ — зум · , . — наклон · / — ровно",
                "U — записать дубль-актёра (сыграй роль сам, до 4) · End — удалить дубли",
                "I — кинофильтр · H — спрятать подсказки · F7 — выйти. Видео пиши OBS/Flashback/Replay Mod",
        };
        int y = H - barH - 12 - lines.length * 11;
        for (String l : lines) {
            ctx.drawText(tr, l, 8, y, 0xFFE0E0E0, true);
            y += 11;
        }
    }

    // ------------------------------------------------------------------ сохранение
    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("novograd_studio.json");
    }

    private static void load() {
        try {
            Path f = file();
            if (Files.exists(f)) {
                Save s = GSON.fromJson(Files.readString(f), Save.class);
                if (s != null) data = s;
            }
        } catch (Exception ignored) {
        }
        if (data.keys == null) data.keys = new ArrayList<>();
        if (data.takes == null) data.takes = new ArrayList<>();
        if (data.filter < 0 || data.filter >= FILTERS.length) data.filter = 0;
    }

    private static void save() {
        try {
            Files.writeString(file(), GSON.toJson(data));
        } catch (Exception ignored) {
        }
    }

    /** Автотест: два дубля-актёра, облёт вокруг них, фильтр — и съёмка. */
    public static void devDemo(MinecraftClient mc, int step) {
        if (step == 0) {
            if (!on) toggle(mc);
            data.keys.clear();
            data.takes.clear();
            var p = mc.player;
            for (int a = 0; a < 2; a++) {
                Take t = new Take();
                for (int i = 0; i < 140; i++) {
                    Frame f = new Frame();
                    double ang = Math.toRadians(p.getYaw() + (a == 0 ? 0 : 35));
                    double d = i * 0.12;
                    f.x = p.getX() - Math.sin(ang) * d + (a == 0 ? 0 : 2.5);
                    f.z = p.getZ() + Math.cos(ang) * d;
                    f.y = mc.world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(f.x), (int) Math.floor(f.z));
                    f.yaw = f.head = f.body = p.getYaw() + (a == 0 ? 0 : 35);
                    f.swing = i % 25 == 0;
                    f.item = a == 0 ? "novograd:pistol" : "";
                    t.frames.add(f);
                }
                data.takes.add(t);
            }
            spawnGhosts(mc);
            com.yompix.novograd.Novograd.LOG.info("[studio] ghosts {}", ghosts.size());
            cx = p.getX() + 6;
            cy = p.getY() + 3;
            cz = p.getZ() + 6;
            double dx = p.getX() - cx, dy = p.getY() + 1 - cy, dz = p.getZ() - cz;
            placeCam((float) Math.toDegrees(Math.atan2(-dx, dz)), (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))), true);
            p.setYaw(cam.getYaw());
            p.setPitch(cam.getPitch());
        } else if (step == 1) {
            orbit(mc);
            roll = 6;
            data.filter = 4;
        } else if (step == 2) {
            playing = true;
            playTick = 0;
            mc.options.hudHidden = true;
            restartGhosts(mc);
        } else if (step == 3) {
            data.filter = 1;
        }
    }

    /** Сменили мир — кадры чужого места не нужны, дубли тоже. */
    public static void onDisconnect() {
        on = false;
        playing = recording = false;
        ghosts.clear();
        cam = null;
    }
}
