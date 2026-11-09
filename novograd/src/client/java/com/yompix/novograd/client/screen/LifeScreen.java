package com.yompix.novograd.client.screen;

import com.yompix.novograd.ModContent;
import com.yompix.novograd.client.ui.Ui;
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

    /** Свой фон вместо ванильного размытия: тёмная ночная подкраска, виньетка и зерно плёнки. */
    protected void background(DrawContext ctx) {
        float a = openAnim();
        ctx.fillGradient(0, 0, width, height, (int) (a * 120) << 24 | 0x0A0418, (int) (a * 200) << 24 | 0x05020C);
        Ui.vignette(ctx, width, height, a * 0.7f);
        Ui.grain(ctx, width, height, 0.18f * a);
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

    /**
     * Кнопка «Тёмных улиц»: тёмная косая плашка с цветной кромкой; при наведении цвет заливает её слева направо,
     * текст чуть сдвигается. Отключённая — серая и приглушённая.
     */
    protected void button(DrawContext ctx, int x, int y, int w, int h, String label, int color, boolean enabled, Runnable action) {
        boolean hov = enabled && hover(x, y, w, h);
        int key = x * 31 + y * 17 + w;
        float k = Ui.approach(hoverAnim.getOrDefault(key, 0f), hov ? 1 : 0, 0.4f);
        hoverAnim.put(key, k);
        int c = enabled ? (color | 0xFF000000) : 0xFF4A4947;
        int skew = Math.min(4, h / 3);
        ctx.fill(x + 2, y + 2, x + w + 2, y + h + 2, 0x50000000);
        Ui.slant(ctx, x, y, w - skew, h, skew, enabled ? 0xF010100F : 0xC010100F);
        if (k > 0.02f) Ui.slant(ctx, x, y, (int) ((w - skew) * Ui.easeOut(k)), h, skew, c);
        Ui.slant(ctx, x, y, 2, h, skew, enabled ? lighten(c, 0.2f) : c);
        ctx.fill(x + skew / 2, y + h - 1, x + w - skew / 2, y + h, enabled ? c : 0xFF343432);
        int tw = textRenderer.getWidth(label);
        float fs = tw > w - 6 ? Math.max(0.6f, (w - 6) / (float) tw) : 1f;
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x + w / 2f + k * 1.5f, y + h / 2f - 4 * fs + 0.5f, 0);
        ctx.getMatrices().scale(fs, fs, 1);
        ctx.drawText(textRenderer, label, -tw / 2, 0, enabled ? 0xFFFFFFFF : 0xFF8A8985, enabled);
        ctx.getMatrices().pop();
        if (enabled) area(x, y, w, h, action);
    }

    private final java.util.Map<Integer, Float> hoverAnim = new java.util.HashMap<>();

    protected static void roundRect(DrawContext ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x + 1, y, x + w - 1, y + h, color);
        ctx.fill(x, y + 1, x + w, y + h - 1, color);
    }

    private static float luma(int c) {
        return (((c >> 16) & 255) * 0.3f + ((c >> 8) & 255) * 0.59f + (c & 255) * 0.11f) / 255f;
    }

    /**
     * Окно. Тёмные цвета превращаются в «стекло» почти чёрного цвета с неоновой кромкой оттенка окна,
     * светлые (бумага: магазин, касса) остаются светлыми, но тоже получают кромку и тень.
     */
    protected static void panel(DrawContext ctx, int x, int y, int w, int h, int top, int bottom) {
        ctx.fill(x + 4, y + 5, x + w + 4, y + h + 5, 0x70000000);
        int accent = accentOf(top);
        if (luma(top) > 0.6f) {
            ctx.fillGradient(x, y, x + w, y + h, top, bottom);
        } else {
            int glass = mix(0xF00A0A0A, top, 0.22f), glass2 = mix(0xF4050505, bottom, 0.12f);
            ctx.fillGradient(x, y, x + w, y + h, glass, glass2);
            ctx.fill(x, y + 3, x + 1, y + h, 0x30FFFFFF);
            ctx.fill(x + w - 1, y + 3, x + w, y + h, 0x18FFFFFF);
        }
        // кромка сверху и косой «ярлык» в углу
        ctx.fill(x, y, x + w, y + 3, accent);
        Ui.slant(ctx, x + w - 46, y - 5, 40, 5, 5, accent);
        Ui.slant(ctx, x + w - 58, y - 5, 8, 5, 5, Ui.alpha(accent, 0.55f));
        ctx.fill(x, y + h - 1, x + w, y + h, Ui.alpha(accent, 0.5f));
    }

    /** Яркий неоновый вариант цвета окна (для кромок). */
    protected static int accentOf(int c) {
        int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
        int max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        if (max - min < 24) return Ui.PINK; // серые окна — фирменный розовый
        float k = 255f / Math.max(1, max);
        return 0xFF000000 | Math.min(255, (int) (r * k)) << 16 | Math.min(255, (int) (g * k)) << 8 | Math.min(255, (int) (b * k));
    }

    protected static int mix(int a, int b, float k) {
        int aa = a >>> 24;
        int r = (int) (((a >> 16) & 255) * (1 - k) + ((b >> 16) & 255) * k), g = (int) (((a >> 8) & 255) * (1 - k) + ((b >> 8) & 255) * k),
                bl = (int) ((a & 255) * (1 - k) + (b & 255) * k);
        return aa << 24 | r << 16 | g << 8 | bl;
    }

    /** Заголовок окна в стиле игры: косая плашка, крупные буквы. */
    protected void title(DrawContext ctx, String title, int x, int y, int accent) {
        Ui.header(ctx, textRenderer, title, x, y, accent);
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

    /** Крупный текст по центру; на тёмном — с чёрной обводкой, тёмный текст — без неё. */
    protected void bigText(DrawContext ctx, String s, int cx, int y, float scale, int color) {
        if (luma(color) < 0.35f) {
            ctx.getMatrices().push();
            ctx.getMatrices().translate(cx, y, 0);
            ctx.getMatrices().scale(scale, scale, 1);
            ctx.drawText(textRenderer, s, -textRenderer.getWidth(s) / 2, 0, color, false);
            ctx.getMatrices().pop();
        } else Ui.big(ctx, textRenderer, s, cx, y, scale, color, 1);
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
