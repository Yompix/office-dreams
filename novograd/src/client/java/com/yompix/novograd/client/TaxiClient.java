package com.yompix.novograd.client;

import com.yompix.novograd.client.ui.Ui;
import com.yompix.novograd.entity.CarEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;

/** Поездка на такси как в GTA: «Пробел — пропустить поездку», затемнение — и ты на месте. */
public final class TaxiClient {
    private TaxiClient() {}

    private static long fadeAt = -1;

    public static boolean riding(MinecraftClient mc) {
        return mc.player != null && mc.player.getVehicle() instanceof CarEntity car && car.getVariant() == 5
                && car.getFirstPassenger() != mc.player && "taxi_ride".equals(NovogradClient.data.taskKind);
    }

    public static void tick(MinecraftClient mc) {
        if (!riding(mc) || mc.currentScreen != null) return;
        while (mc.options.jumpKey.wasPressed()) NovogradClient.send("taxi_skip");
    }

    public static void fade() {
        fadeAt = System.currentTimeMillis();
    }

    public static void render(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        int w = ctx.getScaledWindowWidth(), h = ctx.getScaledWindowHeight();
        if (riding(mc) && !mc.options.hudHidden)
            Ui.small(ctx, mc.textRenderer, "Пробел — пропустить поездку", w / 2f, h - 62, 0.9f, 0xFFFFC428, 1);
        if (fadeAt < 0) return;
        float t = (System.currentTimeMillis() - fadeAt) / 1800f;
        if (t >= 1) {
            fadeAt = -1;
            return;
        }
        float a = MathHelper.clamp(t < 0.15f ? t / 0.15f : t < 0.6f ? 1 : 1 - (t - 0.6f) / 0.4f, 0, 1);
        ctx.fill(0, 0, w, h, Ui.alpha(0xFF05060A, a));
        if (a > 0.4f) Ui.big(ctx, mc.textRenderer, "ТАКСИ", w / 2f, h / 2f - 8, 2f, Ui.alpha(0xFFF7C600, (a - 0.4f) / 0.6f), 1);
    }
}
