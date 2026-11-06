package com.yompix.novograd.client;

import com.yompix.novograd.client.screen.DriveScreen;
import com.yompix.novograd.client.story.ClientStory;
import com.yompix.novograd.client.ui.Ui;
import com.yompix.novograd.entity.CarEntity;
import com.yompix.novograd.vehicle.MotorVehicle;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.MathHelper;

/**
 * Клиент «дорог и транспорта 0.4»: датчик бензина у спидометра, вспышка камеры скорости, перелёт (затемнение, самолёт
 * в небе), туман на рассвете и в дождь (фары помогают), значки заправок/касс/клуба на радаре, экран «Авто».
 */
public final class DriveClient {
    private DriveClient() {}

    private static long flashAt = -1, flightAt = -1;
    private static int flashSpeed, flashLimit;
    private static NbtCompound flight = new NbtCompound();
    private static NbtList places = new NbtList();
    /** Сила тумана 0..1 (плавно) — её читает DriveFogMixin. */
    public static float fog;

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(DriveClient::tick);
        HudRenderCallback.EVENT.register((ctx, t) -> hud(ctx));
        DriveAutotest.init();
    }

    /** Пакеты сервера; true — наш. */
    public static boolean receive(MinecraftClient mc, String type, NbtCompound n) {
        switch (type) {
            case "drv_places" -> {
                places = n.getList("list", NbtElement.COMPOUND_TYPE);
                inject();
            }
            case "drv_flash" -> {
                flashAt = System.currentTimeMillis();
                flashSpeed = n.getInt("speed");
                flashLimit = n.getInt("limit");
                if (mc.player != null) mc.player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_SNARE.value(), 1f, 1.9f);
            }
            case "drv_flight" -> {
                flight = n;
                flightAt = System.currentTimeMillis();
                if (mc.currentScreen != null) mc.setScreen(null);
            }
            case "drv_open" -> NovogradClient.show(mc, newScreen(n));
            case "drv_state" -> {
                NbtCompound old = DriveScreen.state;
                if (n.getString("open").isEmpty()) n.putString("open", old.getString("open"));
                if (!n.contains("here") && old.contains("here")) n.putString("here", old.getString("here"));
                DriveScreen.state = n;
            }
            case "drv_auction" -> DriveScreen.auction(n);
            default -> {
                return false;
            }
        }
        return true;
    }

    private static DriveScreen newScreen(NbtCompound n) {
        DriveScreen.state = n;
        return new DriveScreen(n.getString("open"), false);
    }

    /** Заправки, камеры, кассы — в общий список мест: их рисуют радар и карта. */
    private static void inject() {
        NbtCompound pl = ClientStory.places;
        for (NbtElement e : places) {
            NbtCompound c = (NbtCompound) e;
            String id = c.getString("id");
            if (pl.contains(id)) continue;
            NbtCompound v = new NbtCompound();
            v.putInt("x", c.getInt("x"));
            v.putInt("z", c.getInt("z"));
            v.putString("name", c.getString("name"));
            pl.put(id, v);
        }
    }

    private static void tick(MinecraftClient mc) {
        if (mc.world == null || mc.player == null) {
            fog = 0;
            return;
        }
        if (mc.world.getTime() % 20 == 5 && !places.isEmpty()) inject();
        fog = MathHelper.lerp(0.02f, fog, fogTarget(mc));
    }

    /** Туман: на рассвете в «туманные» дни (каждый третий), в дождь, высоко в горах в непогоду. */
    private static float fogTarget(MinecraftClient mc) {
        var w = mc.world;
        long tod = w.getTimeOfDay() % 24000, day = w.getTimeOfDay() / 24000;
        float f = 0;
        boolean foggyDay = Math.floorMod(day * 2654435761L, 3) == 0;
        if (foggyDay) {
            // 4:30–9:00: густой к 6:00, рассеивается к 9
            float h = ((tod + 6000) % 24000) / 1000f;
            if (h >= 4.5f && h <= 9f) f = h < 6 ? (h - 4.5f) / 1.5f : 1 - (h - 6) / 3f;
        }
        if (w.isRaining()) f = Math.max(f, w.isThundering() ? 0.7f : 0.45f);
        if (mc.player.getZ() < -1150 && w.isRaining()) f = Math.max(f, 0.9f);
        if (CinemaCam.active()) f *= 0.5f;
        return MathHelper.clamp(f, 0, 1);
    }

    /** Дальность тумана для DriveFogMixin: null — не трогать. В машине с фарами — видно дальше. */
    public static float[] fogRange(float viewDistance) {
        if (fog < 0.02f) return null;
        MinecraftClient mc = MinecraftClient.getInstance();
        float near = 40, far = 30;
        if (mc.player != null && mc.player.getVehicle() instanceof CarEntity car && car.light(CarEntity.HEADLIGHTS)) far += 22;
        float end = MathHelper.lerp(fog, viewDistance, far);
        float start = MathHelper.lerp(fog, viewDistance * 0.9f, Math.min(near, end) * 0.05f);
        return new float[]{Math.min(start, end - 1), end};
    }

    // ------------------------------------------------------------------ датчик бензина (вызывает CarHud.speedo)
    public static void fuelGauge(DrawContext ctx, CarEntity car, int x, int y, int pw, int ph) {
        if (!car.usesFuel() || car instanceof MotorVehicle) return;
        var tr = MinecraftClient.getInstance().textRenderer;
        float k = car.fuel() / (float) CarEntity.FUEL_MAX;
        int gx = x + pw + 3, gy = y, gh = ph;
        ctx.fill(gx - 1, gy - 1, gx + 7, gy + gh + 1, 0xC0000000);
        ctx.fill(gx, gy, gx + 6, gy + gh, 0xE0101317);
        int fh = (int) ((gh - 12) * k);
        boolean low = k < 0.2f;
        boolean blink = low && (System.currentTimeMillis() / 400) % 2 == 0;
        int col = k > 0.5f ? 0xFF5BC236 : k > 0.2f ? 0xFFFFC107 : blink ? 0xFFFF2020 : 0xFF8A1A1A;
        ctx.fill(gx + 1, gy + gh - 2 - fh, gx + 5, gy + gh - 2, col);
        for (int i = 1; i < 4; i++) ctx.fill(gx, gy + 10 + (gh - 12) * i / 4, gx + 2, gy + 11 + (gh - 12) * i / 4, 0xFF5A6068);
        ctx.drawText(tr, "⛽", gx - 1, gy + 1, low ? col : 0xFF9AA4AE, false);
    }

    // ------------------------------------------------------------------ HUD: вспышка камеры и перелёт
    private static void hud(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        int w = ctx.getScaledWindowWidth(), h = ctx.getScaledWindowHeight();
        if (flashAt >= 0) {
            float t = (System.currentTimeMillis() - flashAt) / 1000f;
            if (t > 3.2f) flashAt = -1;
            else {
                float a = t < 0.08f ? t / 0.08f : Math.max(0, 1 - (t - 0.08f) / 0.35f);
                if (a > 0) ctx.fill(0, 0, w, h, Ui.alpha(0xFFFFFFFF, a * 0.85f));
                if (t > 0.3f) {
                    float b = Math.min(1, (t - 0.3f) * 4) * Math.min(1, (3.2f - t) * 2);
                    Ui.big(ctx, mc.textRenderer, "◉ КАМЕРА: " + flashSpeed + " км/ч", w / 2f, h * 0.2f, 1.6f, Ui.alpha(0xFFFF5252, b), 1);
                    Ui.small(ctx, mc.textRenderer, "ограничение " + flashLimit + " · штраф придёт СМС", w / 2f, h * 0.2f + 18, 0.9f, Ui.alpha(0xFFFFFFFF, b), 1);
                }
            }
        }
        if (flightAt >= 0) flight(ctx, mc, w, h);
    }

    /** Перелёт ~6 с: затемнение, небо, самолёт пересекает экран, табло рейса, посадка (телепорт — на сервере на 3-й секунде). */
    private static void flight(DrawContext ctx, MinecraftClient mc, int w, int h) {
        float t = (System.currentTimeMillis() - flightAt) / 1000f;
        if (t > 6.5f) {
            flightAt = -1;
            return;
        }
        float a = t < 0.5f ? t / 0.5f : t > 5.6f ? 1 - (t - 5.6f) / 0.9f : 1;
        a = MathHelper.clamp(a, 0, 1);
        // небо: сверху тёмно-синее, к горизонту светлее
        ctx.fillGradient(0, 0, w, h, Ui.alpha(0xFF0D2A4A, a), Ui.alpha(0xFF6FA8D8, a));
        // облака
        for (int i = 0; i < 9; i++) {
            float cx = ((i * 137 + t * (40 + i * 9)) % (w + 160)) - 80;
            float cy = h * (0.55f + (i % 4) * 0.1f);
            int cw = 50 + (i * 31) % 70;
            ctx.fill((int) cx, (int) cy, (int) cx + cw, (int) cy + 8 + i % 3 * 3, Ui.alpha(0xFFF2F6FA, a * 0.75f));
            ctx.fill((int) cx + 10, (int) cy - 5, (int) cx + cw - 12, (int) cy, Ui.alpha(0xFFF2F6FA, a * 0.65f));
        }
        // самолёт: летит слева направо и чуть вверх
        float k = MathHelper.clamp((t - 0.6f) / 4.6f, 0, 1);
        int px = (int) (-120 + (w + 240) * k), py = (int) (h * 0.42f - k * h * 0.12f);
        int body = Ui.alpha(0xFFE8ECF0, a), dark = Ui.alpha(0xFF2A3A55, a), red = Ui.alpha(0xFFD7262C, a);
        ctx.fill(px - 50, py - 5, px + 46, py + 5, body);              // фюзеляж
        ctx.fill(px + 46, py - 3, px + 56, py + 4, body);              // нос
        ctx.fill(px + 44, py - 3, px + 50, py, dark);                  // кабина
        ctx.fill(px - 10, py + 2, px + 14, py + 7, Ui.alpha(0xFFB0BCC8, a)); // крыло
        ctx.fill(px - 20, py - 2, px + 22, py, Ui.alpha(0xFFB0BCC8, a));
        ctx.fill(px - 50, py - 18, px - 38, py - 4, red);              // киль
        ctx.fill(px - 54, py - 2, px - 40, py + 2, body);              // стабилизатор
        for (int i = 0; i < 9; i++) ctx.fill(px - 34 + i * 8, py - 2, px - 31 + i * 8, py, dark); // иллюминаторы
        ctx.fill(px - 50, py + 1, px + 46, py + 2, red);              // полоса
        // табло
        Ui.letterbox(ctx, w, h, a);
        String route = flight.getString("from") + "  →  " + flight.getString("to");
        Ui.big(ctx, mc.textRenderer, "РЕЙС " + flight.getString("code"), w / 2f, h * 0.12f, 1.4f, Ui.alpha(0xFFFFE082, a), 1);
        Ui.small(ctx, mc.textRenderer, route, w / 2f, h * 0.12f + 18, 1f, Ui.alpha(0xFFFFFFFF, a), 1);
        String stage = t < 2 ? "Взлёт…" : t < 4.2f ? "В полёте" : "Заходим на посадку";
        Ui.small(ctx, mc.textRenderer, stage, w / 2f, h * 0.86f, 1f, Ui.alpha(0xFFB3E5FC, a), 1);
    }

    public static boolean flying() {
        return flightAt >= 0;
    }
}
