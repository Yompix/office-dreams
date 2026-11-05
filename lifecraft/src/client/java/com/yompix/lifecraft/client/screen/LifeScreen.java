package com.yompix.lifecraft.client.screen;

import com.yompix.lifecraft.ModContent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Общая основа экранов мода: кнопки рисуются вручную (скруглённые, с подсветкой и нажатием),
 * плавное появление окна и удобные функции для текста.
 */
public abstract class LifeScreen extends Screen {
    protected record Hit(int x, int y, int w, int h, Runnable action) {}

    protected final List<Hit> hits = new ArrayList<>();
    protected final long openedAt = System.currentTimeMillis();
    protected int mouseX, mouseY;

    protected LifeScreen(String title) {
        super(Text.literal(title));
    }

    /** 0→1 за ~250 мс с мягким «пружинным» замедлением. */
    protected float openAnim() {
        float t = MathHelper.clamp((System.currentTimeMillis() - openedAt) / 260f, 0, 1);
        return 1 - (1 - t) * (1 - t) * (1 - t);
    }

    protected float seconds() {
        return (System.currentTimeMillis() - openedAt) / 1000f;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        hits.clear();
        background(ctx);
        draw(ctx, delta);
        super.render(ctx, mouseX, mouseY, delta);
    }

    /** Свой фон вместо ванильного размытия (иначе оно ложится поверх нашего окна). */
    protected void background(DrawContext ctx) {
        ctx.fillGradient(0, 0, width, height, 0x60000000, 0xA0000000);
    }

    @Override
    public final void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
    }

    protected abstract void draw(DrawContext ctx, float delta);

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (button != 0) return false;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (mx >= h.x() && mx < h.x() + h.w() && my >= h.y() && my < h.y() + h.h()) {
                click();
                h.action().run();
                return true;
            }
        }
        return false;
    }

    protected void click() {
        if (client != null && client.player != null) client.player.playSound(ModContent.PHONE_CLICK, 0.5f, 1f);
    }

    protected boolean hover(int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    protected void area(int x, int y, int w, int h, Runnable action) {
        hits.add(new Hit(x, y, w, h, action));
    }

    /** Кнопка: скруглённые углы, светлее при наведении, «утопленная» при отключении. */
    protected void button(DrawContext ctx, int x, int y, int w, int h, String label, int color, boolean enabled, Runnable action) {
        boolean hov = enabled && hover(x, y, w, h);
        int c = enabled ? (hov ? lighten(color, 0.18f) : color) : 0xFF555A66;
        roundRect(ctx, x, y + 1, w, h, darken(c, 0.35f));
        roundRect(ctx, x, y - (hov ? 1 : 0), w, h, c);
        ctx.fill(x + 2, y - (hov ? 1 : 0) + 1, x + w - 2, y - (hov ? 1 : 0) + 2, lighten(c, 0.25f));
        int tw = textRenderer.getWidth(label);
        ctx.drawText(textRenderer, label, x + (w - tw) / 2, y + (h - 8) / 2 - (hov ? 1 : 0), enabled ? 0xFFFFFFFF : 0xFFAAAAAA, true);
        if (enabled) area(x, y, w, h, action);
    }

    protected static void roundRect(DrawContext ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x + 1, y, x + w - 1, y + h, color);
        ctx.fill(x, y + 1, x + w, y + h - 1, color);
    }

    protected static void panel(DrawContext ctx, int x, int y, int w, int h, int top, int bottom) {
        ctx.fill(x + 2, y + 3, x + w + 2, y + h + 3, 0x66000000);
        ctx.fillGradient(x + 1, y, x + w - 1, y + h, top, bottom);
        ctx.fillGradient(x, y + 1, x + w, y + h - 1, top, bottom);
    }

    protected void center(DrawContext ctx, String s, int cx, int y, int color) {
        ctx.drawText(textRenderer, s, cx - textRenderer.getWidth(s) / 2, y, color, true);
    }

    /** Текст с переносом по словам; возвращает высоту. */
    protected int wrap(DrawContext ctx, String s, int x, int y, int w, int color) {
        var lines = textRenderer.wrapLines(Text.literal(s), w);
        for (int i = 0; i < lines.size(); i++) ctx.drawText(textRenderer, lines.get(i), x, y + i * 10, color, false);
        return lines.size() * 10;
    }

    protected void bigText(DrawContext ctx, String s, int cx, int y, float scale, int color) {
        ctx.getMatrices().push();
        ctx.getMatrices().translate(cx, y, 0);
        ctx.getMatrices().scale(scale, scale, 1);
        ctx.drawText(textRenderer, s, -textRenderer.getWidth(s) / 2, 0, color, true);
        ctx.getMatrices().pop();
    }

    public static int lighten(int c, float k) {
        int a = c >>> 24, r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
        r += (int) ((255 - r) * k);
        g += (int) ((255 - g) * k);
        b += (int) ((255 - b) * k);
        return a << 24 | r << 16 | g << 8 | b;
    }

    public static int darken(int c, float k) {
        int a = c >>> 24, r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
        return a << 24 | (int) (r * (1 - k)) << 16 | (int) (g * (1 - k)) << 8 | (int) (b * (1 - k));
    }

    protected static MinecraftClient mc() {
        return MinecraftClient.getInstance();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
