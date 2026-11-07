package com.yompix.novograd.client;

import com.yompix.novograd.client.screen.DarkJobsScreen;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;

import java.util.ArrayList;
import java.util.List;

/**
 * Клиент «Тёмных дел» (gta/Underworld): данные для телефона → «Дела» и подсказки слева во время дела
 * (этап ограбления, камеры, подозрение охраны, мешки, таймер; заказ; контрабанда).
 */
public final class UnderworldClient {
    private UnderworldClient() {}

    public static NbtCompound state = new NbtCompound();
    private static final List<String> HUD = new ArrayList<>();

    public static void init() {
        HudRenderCallback.EVENT.register((ctx, t) -> render(ctx));
        HeistAutotest.init();
    }

    public static boolean receive(MinecraftClient client, String type, NbtCompound n) {
        switch (type) {
            case "uw_state" -> state = n;
            case "uw_hud" -> {
                HUD.clear();
                NbtList l = n.getList("lines", NbtElement.STRING_TYPE);
                for (int i = 0; i < l.size(); i++) HUD.add(l.getString(i));
            }
            case "uw_open" -> client.setScreen(new DarkJobsScreen(n.getString("tab")));
            default -> { return false; }
        }
        return true;
    }

    public static void send(String type, NbtCompound n) {
        NovogradClient.send(type, n);
    }

    private static void render(DrawContext ctx) {
        MinecraftClient c = MinecraftClient.getInstance();
        if (HUD.isEmpty() || c.options.hudHidden || c.player == null || c.currentScreen != null) return;
        var tr = c.textRenderer;
        int w = 0;
        for (String s : HUD) w = Math.max(w, tr.getWidth(s));
        int x = 6, y = ctx.getScaledWindowHeight() / 2 - 10, h = HUD.size() * 10 + 6;
        ctx.fill(x - 3, y - 3, x + w + 5, y + h - 3, 0x90080510);
        ctx.fill(x - 3, y - 3, x - 1, y + h - 3, 0xFFFF2E88);
        for (int i = 0; i < HUD.size(); i++) ctx.drawText(tr, HUD.get(i), x + 1, y + i * 10, 0xFFFFFFFF, true);
    }
}
