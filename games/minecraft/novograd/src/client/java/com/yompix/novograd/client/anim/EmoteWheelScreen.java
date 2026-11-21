package com.yompix.novograd.client.anim;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

/** 0.8: колесо жестов (V): мышью по кругу или цифрой 1–7, клик — показать; видят все рядом. */
public class EmoteWheelScreen extends Screen {
    private int sel = -1;

    public EmoteWheelScreen() {
        super(Text.literal("Жесты"));
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private int at(double mx, double my) {
        double dx = mx - width / 2.0, dy = my - height / 2.0;
        if (dx * dx + dy * dy < 18 * 18) return -1;
        double a = Math.atan2(dx, -dy); // 0 — вверх, по часовой
        int n = PlayerAnim.EMOTES.length;
        return Math.floorMod((int) Math.round(a / (Math.PI * 2) * n), n);
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        sel = at(mx, my);
        int cx = width / 2, cy = height / 2, n = PlayerAnim.EMOTES.length;
        int r = Math.min(78, Math.min(width, height) / 3);
        ctx.fill(0, 0, width, height, 0x55000000);
        for (int i = 0; i < n; i++) {
            double a = i / (double) n * Math.PI * 2;
            int x = cx + (int) Math.round(Math.sin(a) * r), y = cy - (int) Math.round(Math.cos(a) * r);
            String s = (i + 1) + " " + PlayerAnim.EMOTE_NAMES[i];
            int w = textRenderer.getWidth(s) + 10;
            boolean on = i == sel;
            ctx.fill(x - w / 2, y - 8, x + w / 2, y + 8, on ? 0xE0F0B030 : 0xC0101418);
            ctx.drawText(textRenderer, s, x - w / 2 + 5, y - 4, on ? 0xFF101010 : 0xFFE8E8E8, false);
        }
        String c = "Жесты";
        ctx.drawText(textRenderer, c, cx - textRenderer.getWidth(c) / 2, cy - 4, 0xFFFFD54F, true);
    }

    private void pick(int i) {
        if (i >= 0 && i < PlayerAnim.EMOTES.length) PlayerAnim.play(PlayerAnim.EMOTES[i]);
        close();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        pick(at(mx, my));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_7) {
            pick(key - GLFW.GLFW_KEY_1);
            return true;
        }
        if (PlayerAnim.emoteKey.matchesKey(key, scan)) {
            close();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void renderBackground(DrawContext ctx, int mx, int my, float delta) {
    }

    @SuppressWarnings("unused")
    private static float clamp(float v) {
        return MathHelper.clamp(v, 0, 1);
    }
}
