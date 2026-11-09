package com.yompix.novograd.client;

import com.yompix.novograd.gta.Cheats;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import net.minecraft.nbt.NbtCompound;
import org.lwjgl.glfw.GLFW;

/** Как в San Andreas: чит-код набирается буквами прямо в игре, без чата. Сервер проверяет и применяет. */
public final class CheatInput {
    private CheatInput() {}

    private static final StringBuilder BUF = new StringBuilder();
    private static final boolean[] DOWN = new boolean[26];

    private static boolean graveDown;

    public static void tick(MinecraftClient mc) {
        if (mc.player == null || mc.currentScreen != null) return;
        long win = mc.getWindow().getHandle();
        // ` (Ё) — окошко чит-кода: надёжно, буквы не открывают инвентарь, карту и телефон
        boolean g = InputUtil.isKeyPressed(win, GLFW.GLFW_KEY_GRAVE_ACCENT);
        if (g && !graveDown) {
            graveDown = true;
            mc.setScreen(new com.yompix.novograd.client.screen.CheatScreen());
            return;
        }
        graveDown = g;
        for (int i = 0; i < 26; i++) {
            boolean now = InputUtil.isKeyPressed(win, GLFW.GLFW_KEY_A + i);
            if (now && !DOWN[i]) type((char) ('A' + i));
            DOWN[i] = now;
        }
    }

    private static void type(char c) {
        BUF.append(c);
        if (BUF.length() > 24) BUF.delete(0, BUF.length() - 24);
        String s = BUF.toString();
        for (String code : Cheats.LIST.keySet()) {
            if (s.endsWith(code)) {
                NbtCompound n = new NbtCompound();
                n.putString("code", code);
                NovogradClient.send("cheat_code", n);
                BUF.setLength(0);
                return;
            }
        }
    }
}
