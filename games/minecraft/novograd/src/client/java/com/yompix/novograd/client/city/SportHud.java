package com.yompix.novograd.client.city;

import com.yompix.novograd.client.ui.Ui;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.nbt.NbtCompound;

/** 0.9.9: табло заезда — время, точка, место; в дрифте — очки и серия (полоска). */
public final class SportHud {
    private SportHud() {}

    public static NbtCompound state = new NbtCompound();
    private static long at;

    public static void set(NbtCompound n) {
        state = n;
        at = System.currentTimeMillis();
    }

    public static void init() {
        net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register((ctx, counter) -> render(ctx));
    }

    static void render(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (state.isEmpty() || mc.options.hudHidden || System.currentTimeMillis() - at > 4000) return;
        int w = mc.getWindow().getScaledWidth();
        int x = w / 2 - 90, y = 6, bw = 180;
        Ui.slant(ctx, x, y, bw, 34, 4, 0xC0101418);
        long t = state.getLong("t") + (System.currentTimeMillis() - at) / 50;
        long ms = t * 50;
        String time = String.format("%d:%02d.%d", ms / 60000, ms / 1000 % 60, ms / 100 % 10);
        var tr = mc.textRenderer;
        Ui.small(ctx, tr, state.getString("name").toUpperCase() + " · " + state.getInt("place") + "/" + state.getInt("of"), x + 8, y + 4, 0.75f, 0xFFFFCC80, 0);
        if (state.getBoolean("drift")) {
            Ui.big(ctx, tr, String.valueOf(state.getInt("score")), x + 8, y + 14, 1.4f, Ui.WHITE, 0);
            float chain = state.getFloat("chain");
            Ui.small(ctx, tr, "×" + String.format("%.1f", 1 + chain), x + bw - 8, y + 16, 0.9f, chain > 2 ? Ui.GOLD : Ui.CYAN, 2);
            long left = Math.max(0, state.getInt("limit") * 20L - t) / 20;
            Ui.small(ctx, tr, left + " с", x + bw - 8, y + 4, 0.75f, 0xFFB0BEC5, 2);
            Ui.bar(ctx, x + 8, y + 29, bw - 16, 2, Math.min(1, chain / 5f), 0xFFFF7043);
        } else {
            Ui.big(ctx, tr, time, x + 8, y + 14, 1.4f, Ui.WHITE, 0);
            Ui.small(ctx, tr, "точка " + state.getInt("cp") + "/" + state.getInt("cps"), x + bw - 8, y + 18, 0.85f, Ui.CYAN, 2);
        }
    }
}
