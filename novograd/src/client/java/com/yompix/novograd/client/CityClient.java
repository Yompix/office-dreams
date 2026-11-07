package com.yompix.novograd.client;

import com.yompix.novograd.Novograd;
import com.yompix.novograd.client.ui.Ui;
import com.yompix.novograd.entity.CarEntity;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

/**
 * Клиент «города, который живёт сам»: строка полицейской рации (с треском и «печатью» текста),
 * подсказка в автобусе (маршрут, следующая остановка, Пробел — пропустить) и затемнение при пропуске.
 */
public final class CityClient {
    private CityClient() {}

    private static String radio = "";
    private static long radioAt = -1;
    @Nullable private static NbtCompound bus;
    private static long fadeAt = -1;
    private static String fadeLabel = "";
    private static final long RADIO_MS = 7500;

    public static void init() {
        HudRenderCallback.EVENT.register((ctx, t) -> render(ctx));
        ClientTickEvents.END_CLIENT_TICK.register(CityClient::tick);
        CityAutotest.init();
    }

    public static boolean receive(MinecraftClient c, String type, NbtCompound n) {
        switch (type) {
            case "city_radio" -> {
                radio = n.getString("text");
                radioAt = System.currentTimeMillis();
                if (c.player != null) {
                    play(c, "radio_chirp", 0.55f);
                    play(c, "radio_static", 0.45f);
                }
            }
            case "city_bus" -> bus = n.isEmpty() ? null : n;
            case "city_fade" -> {
                fadeAt = System.currentTimeMillis();
                fadeLabel = n.getString("label");
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private static void play(MinecraftClient c, String id, float vol) {
        c.getSoundManager().play(PositionedSoundInstance.master(SoundEvent.of(Novograd.id(id)), 1f, vol));
    }

    private static boolean ridingBus(MinecraftClient mc) {
        return bus != null && mc.player != null && mc.player.getVehicle() instanceof CarEntity car && car.getVariant() == CarEntity.BUS && car.getControllingPassenger() != mc.player;
    }

    private static void tick(MinecraftClient mc) {
        if (mc.player == null) {
            bus = null;
            return;
        }
        if (!ridingBus(mc) || mc.currentScreen != null) return;
        while (mc.options.jumpKey.wasPressed()) NovogradClient.send("city_bus_skip");
    }

    /** Последняя строка рации (для автотеста). */
    public static String lastRadio() {
        return radio;
    }

    private static void render(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        int w = ctx.getScaledWindowWidth(), h = ctx.getScaledWindowHeight();
        var tr = mc.textRenderer;
        long now = System.currentTimeMillis();
        if (!mc.options.hudHidden && radioAt > 0 && now - radioAt < RADIO_MS && !LifeHud.cinematic(mc)) {
            float age = (now - radioAt) / 1000f, total = RADIO_MS / 1000f;
            float a = MathHelper.clamp(Math.min(age / 0.25f, (total - age) / 0.6f), 0, 1);
            int chars = Math.min(radio.length(), (int) (age * 42)); // «печатает» диспетчер
            String shown = radio.substring(0, chars);
            int maxW = Math.min(w / 3, 230); // компактно, в углу — середина экрана свободна
            var lines = tr.wrapLines(net.minecraft.text.Text.literal(radio), maxW);
            int lh = 10, boxH = lines.size() * lh + 16, boxW = 0;
            for (var l : lines) boxW = Math.max(boxW, tr.getWidth(l));
            boxW += 50;
            int x0 = 6, y0 = 6; // левый верхний угол, как рация в GTA
            ctx.fill(x0, y0, x0 + boxW, y0 + boxH, Ui.alpha(0xD0101820, a));
            ctx.fill(x0, y0, x0 + 3, y0 + boxH, Ui.alpha((now / 300) % 2 == 0 ? 0xFF2A6AFF : 0xFFFF2A3A, a)); // мигалка
            ctx.drawText(tr, "РАЦИЯ", x0 + 8, y0 + 4, Ui.alpha(0xFF7FB2FF, a), false);
            // текст печатается по строкам
            int left = chars, ty = y0 + 4;
            for (var l : tr.wrapLines(net.minecraft.text.Text.literal(shown.isEmpty() ? " " : shown), maxW)) {
                ctx.drawText(tr, l, x0 + 42, ty, Ui.alpha(0xFFE8F0FF, a), false);
                ty += lh;
            }
            // «волна» эфира
            for (int i = 0; i < 6; i++) {
                float k = (float) Math.abs(Math.sin(now / 90.0 + i * 1.3));
                int bh = (int) (2 + k * 6);
                ctx.fill(x0 + 8 + i * 4, y0 + boxH - 4 - bh, x0 + 10 + i * 4, y0 + boxH - 4, Ui.alpha(0xFF7FB2FF, a * (chars < radio.length() ? 1 : 0.4f)));
            }
        }
        if (ridingBus(mc) && !mc.options.hudHidden && bus != null) {
            int col = 0xFF000000 | bus.getInt("color");
            String head = "АВТОБУС №" + bus.getInt("num") + " · " + bus.getString("title");
            int eta = bus.getInt("eta");
            String next = bus.getBoolean("at") ? "Остановка «" + bus.getString("stop") + "» — посадка" : "Следующая: «" + bus.getString("stop") + "»" + (eta > 0 ? " · ~" + eta + " с" : "");
            int bw = Math.max(tr.getWidth(head), tr.getWidth(next)) + 24, x0 = w / 2 - bw / 2, y0 = h - 96;
            ctx.fill(x0, y0, x0 + bw, y0 + 26, 0xC0101418);
            ctx.fill(x0, y0, x0 + bw, y0 + 2, col);
            ctx.drawCenteredTextWithShadow(tr, head, w / 2, y0 + 5, 0xFFFFFFFF);
            ctx.drawCenteredTextWithShadow(tr, next, w / 2, y0 + 15, 0xFFFFC428);
            Ui.small(ctx, tr, "Пробел — к следующей остановке · Shift — выйти", w / 2f, y0 + 30, 0.85f, 0xFFB8C4D0, 1);
        }
        if (fadeAt > 0) {
            float t = (now - fadeAt) / 1800f;
            if (t >= 1) {
                fadeAt = -1;
                return;
            }
            float a = MathHelper.clamp(t < 0.15f ? t / 0.15f : t < 0.6f ? 1 : 1 - (t - 0.6f) / 0.4f, 0, 1);
            ctx.fill(0, 0, w, h, Ui.alpha(0xFF05060A, a));
            if (a > 0.4f) Ui.big(ctx, tr, fadeLabel, w / 2f, h / 2f - 8, 2f, Ui.alpha(0xFFFFC428, (a - 0.4f) / 0.6f), 1);
        }
    }
}
